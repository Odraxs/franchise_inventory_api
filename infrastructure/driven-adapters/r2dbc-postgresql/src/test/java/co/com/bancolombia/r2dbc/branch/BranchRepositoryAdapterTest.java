package co.com.bancolombia.r2dbc.branch;

import co.com.bancolombia.model.branch.Branch;
import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchRepositoryAdapterTest {

    private static final UUID FRANCHISE_ID = UUID.randomUUID();

    @Mock
    private BranchDataRepository repository;

    private BranchRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new BranchRepositoryAdapter(repository);
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
    void existsByIdAndFranchiseIdDelegatesToTheRepository() {
        UUID id = UUID.randomUUID();
        when(repository.existsByIdAndFranchiseId(id, FRANCHISE_ID)).thenReturn(Mono.just(true));

        StepVerifier.create(adapter.existsByIdAndFranchiseId(id, FRANCHISE_ID))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void propagatesOtherErrorsUnchanged() {
        when(repository.save(new BranchData(null, FRANCHISE_ID, "Centro")))
                .thenReturn(Mono.error(new DataIntegrityViolationException("ck_branch_name_not_blank")));

        StepVerifier.create(adapter.save(Branch.builder().franchiseId(FRANCHISE_ID).name("Centro").build()))
                .expectError(DataIntegrityViolationException.class)
                .verify();
    }
}
