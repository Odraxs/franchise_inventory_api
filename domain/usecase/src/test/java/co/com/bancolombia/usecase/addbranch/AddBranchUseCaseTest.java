package co.com.bancolombia.usecase.addbranch;

import co.com.bancolombia.model.branch.Branch;
import co.com.bancolombia.model.branch.gateways.BranchGateway;
import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.franchise.gateways.FranchiseGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddBranchUseCaseTest {

    private static final UUID FRANCHISE_ID = UUID.randomUUID();

    @Mock
    private FranchiseGateway franchiseGateway;

    @Mock
    private BranchGateway branchGateway;

    private AddBranchUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AddBranchUseCase(franchiseGateway, branchGateway);
    }

    @Test
    void savesBranchInTheFranchiseAndEmitsItWithItsGeneratedId() {
        UUID id = UUID.randomUUID();
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(branchGateway.save(any(Branch.class)))
                .thenAnswer(invocation -> Mono.just(((Branch) invocation.getArgument(0)).toBuilder().id(id).build()));

        StepVerifier.create(useCase.execute(Branch.builder().franchiseId(FRANCHISE_ID).name("Centro").build()))
                .expectNextMatches(saved -> id.equals(saved.id()) && "Centro".equals(saved.name())
                        && FRANCHISE_ID.equals(saved.franchiseId()))
                .verifyComplete();
    }

    @Test
    void failsWithFranchiseNotFoundWithoutSavingWhenTheFranchiseDoesNotExist() {
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.execute(Branch.builder().franchiseId(FRANCHISE_ID).name("Centro").build()))
                .expectErrorMatches(error -> error instanceof BusinessException exception
                        && exception.getErrorCode() == ErrorCode.FRANCHISE_NOT_FOUND)
                .verify();
        verifyNoInteractions(branchGateway);
    }

    @Test
    void propagatesDuplicateNameFromTheGateway() {
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(branchGateway.save(any(Branch.class)))
                .thenReturn(Mono.error(new BusinessException(ErrorCode.DUPLICATE_BRANCH_NAME)));

        StepVerifier.create(useCase.execute(Branch.builder().franchiseId(FRANCHISE_ID).name("Centro").build()))
                .expectErrorMatches(error -> error instanceof BusinessException exception
                        && exception.getErrorCode() == ErrorCode.DUPLICATE_BRANCH_NAME)
                .verify();
        verify(branchGateway).save(any(Branch.class));
    }
}
