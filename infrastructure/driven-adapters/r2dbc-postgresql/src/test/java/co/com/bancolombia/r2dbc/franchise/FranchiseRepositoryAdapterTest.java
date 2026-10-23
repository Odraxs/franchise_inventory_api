package co.com.bancolombia.r2dbc.franchise;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.exception.TechnicalException;
import co.com.bancolombia.model.franchise.Franchise;
import co.com.bancolombia.r2dbc.resilience.DatabaseResilienceFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.publisher.PublisherProbe;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FranchiseRepositoryAdapterTest {

    /** Longer than every backoff of the fast test config. */
    private static final Duration ALL_BACKOFFS = Duration.ofSeconds(1);

    @Mock
    private FranchiseDataRepository repository;

    private FranchiseRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new FranchiseRepositoryAdapter(repository, DatabaseResilienceFixture.fastDatabaseResilience());
    }

    @Test
    void insertsWithoutIdAndEmitsFranchiseWithGeneratedId() {
        UUID id = UUID.randomUUID();
        when(repository.save(new FranchiseData(null, "Nequi"))).thenReturn(Mono.just(new FranchiseData(id, "Nequi")));

        StepVerifier.create(adapter.save(Franchise.builder().name("Nequi").build()))
                .expectNextMatches(franchise -> id.equals(franchise.id()) && "Nequi".equals(franchise.name()))
                .verifyComplete();
    }

    @Test
    void mapsDuplicateKeyToDuplicateFranchiseName() {
        when(repository.save(new FranchiseData(null, "Nequi")))
                .thenReturn(Mono.error(new DuplicateKeyException("uq_franchise_name")));

        StepVerifier.create(adapter.save(Franchise.builder().name("Nequi").build()))
                .expectErrorMatches(error -> error instanceof BusinessException exception
                        && exception.getErrorCode() == ErrorCode.DUPLICATE_FRANCHISE_NAME)
                .verify();
    }

    @Test
    void propagatesOtherErrorsUnchanged() {
        when(repository.save(new FranchiseData(null, "Nequi")))
                .thenReturn(Mono.error(new DataIntegrityViolationException("ck_franchise_name_not_blank")));

        StepVerifier.create(adapter.save(Franchise.builder().name("Nequi").build()))
                .expectError(DataIntegrityViolationException.class)
                .verify();
    }

    @Test
    void renameEmitsTheUpdatedFranchise() {
        UUID id = UUID.randomUUID();
        when(repository.rename(id, "Nequi")).thenReturn(Mono.just(new FranchiseData(id, "Nequi")));

        StepVerifier.create(adapter.rename(id, "Nequi"))
                .expectNextMatches(franchise -> id.equals(franchise.id()) && "Nequi".equals(franchise.name()))
                .verifyComplete();
    }

    @Test
    void renameEmitsEmptyWhenNoRowMatches() {
        UUID id = UUID.randomUUID();
        when(repository.rename(id, "Nequi")).thenReturn(Mono.empty());

        StepVerifier.create(adapter.rename(id, "Nequi")).verifyComplete();
    }

    @Test
    void renameMapsDuplicateKeyToDuplicateFranchiseName() {
        UUID id = UUID.randomUUID();
        when(repository.rename(id, "Nequi")).thenReturn(Mono.error(new DuplicateKeyException("uq_franchise_name")));

        StepVerifier.create(adapter.rename(id, "Nequi"))
                .expectErrorMatches(error -> error instanceof BusinessException exception
                        && exception.getErrorCode() == ErrorCode.DUPLICATE_FRANCHISE_NAME)
                .verify();
    }

    @Test
    void existsByIdEmitsTheRepositoryAnswer() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(Mono.just(true));

        StepVerifier.create(adapter.existsById(id))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void existsByIdIsRetriedAfterATimeoutAndRecovers() {
        UUID id = UUID.randomUUID();
        AtomicInteger subscriptions = new AtomicInteger();
        Iterator<Mono<Boolean>> answers = List.of(Mono.<Boolean>never(), Mono.just(true)).iterator();
        when(repository.existsById(id)).thenReturn(Mono.defer(answers::next)
                .doOnSubscribe(subscription -> subscriptions.incrementAndGet()));

        StepVerifier.withVirtualTime(() -> adapter.existsById(id))
                .expectSubscription()
                .thenAwait(DatabaseResilienceFixture.TIMEOUT)
                .thenAwait(ALL_BACKOFFS)
                .expectNext(true)
                .verifyComplete();
        assertThat(subscriptions).hasValue(2);
    }

    @Test
    void stalledExistsByIdFailsWithDatabaseUnavailable() {
        UUID id = UUID.randomUUID();
        PublisherProbe<Boolean> stalled = PublisherProbe.of(Mono.never());
        when(repository.existsById(id)).thenReturn(stalled.mono());

        StepVerifier.withVirtualTime(() -> adapter.existsById(id))
                .expectSubscription()
                .thenAwait(DatabaseResilienceFixture.TIMEOUT.multipliedBy(DatabaseResilienceFixture.MAX_ATTEMPTS)
                        .plus(ALL_BACKOFFS))
                .expectErrorMatches(error -> error instanceof TechnicalException exception
                        && exception.getErrorCode() == TechnicalException.ErrorCode.DATABASE_UNAVAILABLE)
                .verify();
        // The retries count in the breaker, so it can open before the last attempt
        assertThat(stalled.subscribeCount()).isBetween(2L, (long) DatabaseResilienceFixture.MAX_ATTEMPTS);
    }

    @Test
    void stalledSaveFailsWithDatabaseUnavailable() {
        when(repository.save(new FranchiseData(null, "Nequi"))).thenReturn(Mono.never());

        StepVerifier.withVirtualTime(() -> adapter.save(Franchise.builder().name("Nequi").build()))
                .expectSubscription()
                .thenAwait(DatabaseResilienceFixture.TIMEOUT)
                .expectErrorMatches(error -> error instanceof TechnicalException exception
                        && exception.getErrorCode() == TechnicalException.ErrorCode.DATABASE_UNAVAILABLE)
                .verify();
    }

    @Test
    void renameIsRetriedAfterATimeoutAndRecovers() {
        UUID id = UUID.randomUUID();
        AtomicInteger subscriptions = new AtomicInteger();
        Iterator<Mono<FranchiseData>> answers =
                List.of(Mono.<FranchiseData>never(), Mono.just(new FranchiseData(id, "Nequi"))).iterator();
        when(repository.rename(id, "Nequi")).thenReturn(Mono.defer(answers::next)
                .doOnSubscribe(subscription -> subscriptions.incrementAndGet()));

        StepVerifier.withVirtualTime(() -> adapter.rename(id, "Nequi"))
                .expectSubscription()
                .thenAwait(DatabaseResilienceFixture.TIMEOUT)
                .thenAwait(ALL_BACKOFFS)
                .expectNextMatches(franchise -> id.equals(franchise.id()) && "Nequi".equals(franchise.name()))
                .verifyComplete();
        assertThat(subscriptions).hasValue(2);
    }

    @Test
    void stalledRenameFailsWithDatabaseUnavailable() {
        UUID id = UUID.randomUUID();
        PublisherProbe<FranchiseData> stalled = PublisherProbe.of(Mono.never());
        when(repository.rename(id, "Nequi")).thenReturn(stalled.mono());

        StepVerifier.withVirtualTime(() -> adapter.rename(id, "Nequi"))
                .expectSubscription()
                .thenAwait(DatabaseResilienceFixture.TIMEOUT.multipliedBy(DatabaseResilienceFixture.MAX_ATTEMPTS)
                        .plus(ALL_BACKOFFS))
                .expectErrorMatches(error -> error instanceof TechnicalException exception
                        && exception.getErrorCode() == TechnicalException.ErrorCode.DATABASE_UNAVAILABLE)
                .verify();
        // The retries count in the breaker, so it can open before the last attempt
        assertThat(stalled.subscribeCount()).isBetween(2L, (long) DatabaseResilienceFixture.MAX_ATTEMPTS);
    }

    @Test
    void duplicateNameOnRenameIsTriedOnceAndMapsToDuplicateFranchiseName() {
        UUID id = UUID.randomUUID();
        PublisherProbe<FranchiseData> duplicate = PublisherProbe.of(Mono.error(new DuplicateKeyException("uq_franchise_name")));
        when(repository.rename(id, "Nequi")).thenReturn(duplicate.mono());

        StepVerifier.create(adapter.rename(id, "Nequi"))
                .expectErrorMatches(error -> error instanceof BusinessException exception
                        && exception.getErrorCode() == ErrorCode.DUPLICATE_FRANCHISE_NAME)
                .verify();
        assertThat(duplicate.subscribeCount()).isEqualTo(1);
    }

    @Test
    void duplicateNameIsTriedOnceAndMapsToDuplicateFranchiseName() {
        PublisherProbe<FranchiseData> duplicate = PublisherProbe.of(Mono.error(new DuplicateKeyException("uq_franchise_name")));
        when(repository.save(new FranchiseData(null, "Nequi"))).thenReturn(duplicate.mono());

        StepVerifier.create(adapter.save(Franchise.builder().name("Nequi").build()))
                .expectErrorMatches(error -> error instanceof BusinessException exception
                        && exception.getErrorCode() == ErrorCode.DUPLICATE_FRANCHISE_NAME)
                .verify();
        assertThat(duplicate.subscribeCount()).isEqualTo(1);
    }
}
