package co.com.bancolombia.r2dbc.branch;

import co.com.bancolombia.model.branch.Branch;
import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.exception.TechnicalException;
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
class BranchRepositoryAdapterTest {

    /** Longer than every backoff of the fast test config. */
    private static final Duration ALL_BACKOFFS = Duration.ofSeconds(1);
    private static final UUID FRANCHISE_ID = UUID.randomUUID();

    @Mock
    private BranchDataRepository repository;

    private BranchRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new BranchRepositoryAdapter(repository, DatabaseResilienceFixture.fastDatabaseResilience());
    }

    @Test
    void insertsWithoutIdAndEmitsBranchWithGeneratedId() {
        UUID id = UUID.randomUUID();
        when(repository.save(new BranchData(null, FRANCHISE_ID, "Centro")))
                .thenReturn(Mono.just(new BranchData(id, FRANCHISE_ID, "Centro")));

        StepVerifier.create(adapter.save(Branch.builder().franchiseId(FRANCHISE_ID).name("Centro").build()))
                .expectNextMatches(branch -> id.equals(branch.id()) && FRANCHISE_ID.equals(branch.franchiseId())
                        && "Centro".equals(branch.name()))
                .verifyComplete();
    }

    @Test
    void mapsDuplicateKeyToDuplicateBranchName() {
        when(repository.save(new BranchData(null, FRANCHISE_ID, "Centro")))
                .thenReturn(Mono.error(new DuplicateKeyException("uq_branch_franchise_name")));

        StepVerifier.create(adapter.save(Branch.builder().franchiseId(FRANCHISE_ID).name("Centro").build()))
                .expectErrorMatches(error -> error instanceof BusinessException exception
                        && exception.getErrorCode() == ErrorCode.DUPLICATE_BRANCH_NAME)
                .verify();
    }

    @Test
    void renameEmitsTheUpdatedBranch() {
        UUID id = UUID.randomUUID();
        when(repository.rename(id, FRANCHISE_ID, "Centro")).thenReturn(Mono.just(new BranchData(id, FRANCHISE_ID, "Centro")));

        StepVerifier.create(adapter.rename(id, FRANCHISE_ID, "Centro"))
                .expectNextMatches(branch -> id.equals(branch.id()) && FRANCHISE_ID.equals(branch.franchiseId())
                        && "Centro".equals(branch.name()))
                .verifyComplete();
    }

    @Test
    void renameEmitsEmptyWhenNoRowMatches() {
        UUID id = UUID.randomUUID();
        when(repository.rename(id, FRANCHISE_ID, "Centro")).thenReturn(Mono.empty());

        StepVerifier.create(adapter.rename(id, FRANCHISE_ID, "Centro")).verifyComplete();
    }

    @Test
    void renameMapsDuplicateKeyToDuplicateBranchName() {
        UUID id = UUID.randomUUID();
        when(repository.rename(id, FRANCHISE_ID, "Centro"))
                .thenReturn(Mono.error(new DuplicateKeyException("uq_branch_franchise_name")));

        StepVerifier.create(adapter.rename(id, FRANCHISE_ID, "Centro"))
                .expectErrorMatches(error -> error instanceof BusinessException exception
                        && exception.getErrorCode() == ErrorCode.DUPLICATE_BRANCH_NAME)
                .verify();
    }

    @Test
    void stalledRenameFailsWithDatabaseUnavailableWithoutRetrying() {
        UUID id = UUID.randomUUID();
        PublisherProbe<BranchData> stalled = PublisherProbe.of(Mono.never());
        when(repository.rename(id, FRANCHISE_ID, "Centro")).thenReturn(stalled.mono());

        StepVerifier.withVirtualTime(() -> adapter.rename(id, FRANCHISE_ID, "Centro"))
                .expectSubscription()
                .thenAwait(DatabaseResilienceFixture.TIMEOUT.plus(ALL_BACKOFFS))
                .expectErrorMatches(error -> error instanceof TechnicalException exception
                        && exception.getErrorCode() == TechnicalException.ErrorCode.DATABASE_UNAVAILABLE)
                .verify();
        assertThat(stalled.subscribeCount()).isEqualTo(1);
    }

    @Test
    void renameDuplicateNameIsTriedOnceAndMapsToDuplicateBranchName() {
        UUID id = UUID.randomUUID();
        PublisherProbe<BranchData> duplicate = PublisherProbe.of(Mono.error(new DuplicateKeyException("uq_branch_franchise_name")));
        when(repository.rename(id, FRANCHISE_ID, "Centro")).thenReturn(duplicate.mono());

        StepVerifier.create(adapter.rename(id, FRANCHISE_ID, "Centro"))
                .expectErrorMatches(error -> error instanceof BusinessException exception
                        && exception.getErrorCode() == ErrorCode.DUPLICATE_BRANCH_NAME)
                .verify();
        assertThat(duplicate.subscribeCount()).isEqualTo(1);
    }

    @Test
    void existsByIdAndFranchiseIdDelegatesToTheRepository() {
        UUID id = UUID.randomUUID();
        when(repository.existsByIdAndFranchiseId(id, FRANCHISE_ID)).thenReturn(Mono.just(true));

        StepVerifier.create(adapter.existsByIdAndFranchiseId(id, FRANCHISE_ID))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void existsByIdAndFranchiseIdIsRetriedAfterATimeoutAndRecovers() {
        UUID id = UUID.randomUUID();
        AtomicInteger subscriptions = new AtomicInteger();
        Iterator<Mono<Boolean>> answers = List.of(Mono.<Boolean>never(), Mono.just(true)).iterator();
        when(repository.existsByIdAndFranchiseId(id, FRANCHISE_ID)).thenReturn(Mono.defer(answers::next)
                .doOnSubscribe(subscription -> subscriptions.incrementAndGet()));

        StepVerifier.withVirtualTime(() -> adapter.existsByIdAndFranchiseId(id, FRANCHISE_ID))
                .expectSubscription()
                .thenAwait(DatabaseResilienceFixture.TIMEOUT)
                .thenAwait(ALL_BACKOFFS)
                .expectNext(true)
                .verifyComplete();
        assertThat(subscriptions).hasValue(2);
    }

    @Test
    void stalledExistsByIdAndFranchiseIdFailsWithDatabaseUnavailable() {
        UUID id = UUID.randomUUID();
        PublisherProbe<Boolean> stalled = PublisherProbe.of(Mono.never());
        when(repository.existsByIdAndFranchiseId(id, FRANCHISE_ID)).thenReturn(stalled.mono());

        StepVerifier.withVirtualTime(() -> adapter.existsByIdAndFranchiseId(id, FRANCHISE_ID))
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
    void countByFranchiseIdDelegatesToTheRepository() {
        when(repository.countByFranchiseId(FRANCHISE_ID)).thenReturn(Mono.just(3L));

        StepVerifier.create(adapter.countByFranchiseId(FRANCHISE_ID))
                .expectNext(3L)
                .verifyComplete();
    }

    @Test
    void stalledCountByFranchiseIdFailsWithDatabaseUnavailable() {
        PublisherProbe<Long> stalled = PublisherProbe.of(Mono.never());
        when(repository.countByFranchiseId(FRANCHISE_ID)).thenReturn(stalled.mono());

        StepVerifier.withVirtualTime(() -> adapter.countByFranchiseId(FRANCHISE_ID))
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
    void propagatesOtherErrorsUnchanged() {
        when(repository.save(new BranchData(null, FRANCHISE_ID, "Centro")))
                .thenReturn(Mono.error(new DataIntegrityViolationException("ck_branch_name_not_blank")));

        StepVerifier.create(adapter.save(Branch.builder().franchiseId(FRANCHISE_ID).name("Centro").build()))
                .expectError(DataIntegrityViolationException.class)
                .verify();
    }

    @Test
    void stalledSaveFailsWithDatabaseUnavailable() {
        when(repository.save(new BranchData(null, FRANCHISE_ID, "Centro"))).thenReturn(Mono.never());

        StepVerifier.withVirtualTime(() -> adapter.save(Branch.builder().franchiseId(FRANCHISE_ID).name("Centro").build()))
                .expectSubscription()
                .thenAwait(DatabaseResilienceFixture.TIMEOUT)
                .expectErrorMatches(error -> error instanceof TechnicalException exception
                        && exception.getErrorCode() == TechnicalException.ErrorCode.DATABASE_UNAVAILABLE)
                .verify();
    }

    @Test
    void duplicateNameIsTriedOnceAndMapsToDuplicateBranchName() {
        PublisherProbe<BranchData> duplicate = PublisherProbe.of(Mono.error(new DuplicateKeyException("uq_branch_franchise_name")));
        when(repository.save(new BranchData(null, FRANCHISE_ID, "Centro"))).thenReturn(duplicate.mono());

        StepVerifier.create(adapter.save(Branch.builder().franchiseId(FRANCHISE_ID).name("Centro").build()))
                .expectErrorMatches(error -> error instanceof BusinessException exception
                        && exception.getErrorCode() == ErrorCode.DUPLICATE_BRANCH_NAME)
                .verify();
        assertThat(duplicate.subscribeCount()).isEqualTo(1);
    }
}
