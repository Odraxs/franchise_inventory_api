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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FranchiseRepositoryAdapterTest {

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
    void existsByIdEmitsTheRepositoryAnswer() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(Mono.just(true));

        StepVerifier.create(adapter.existsById(id))
                .expectNext(true)
                .verifyComplete();
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
