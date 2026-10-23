package co.com.bancolombia.usecase.renamebranch;

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

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenameBranchUseCaseTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID FRANCHISE_ID = UUID.randomUUID();

    @Mock
    private FranchiseGateway franchiseGateway;

    @Mock
    private BranchGateway branchGateway;

    private RenameBranchUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RenameBranchUseCase(franchiseGateway, branchGateway);
    }

    @Test
    void renamesWithTheStrippedNameAndEmitsTheUpdatedBranch() {
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(branchGateway.rename(ID, FRANCHISE_ID, "Centro"))
                .thenReturn(Mono.just(Branch.builder().id(ID).franchiseId(FRANCHISE_ID).name("Centro").build()));

        StepVerifier.create(useCase.execute(branch("  Centro  ")))
                .expectNextMatches(renamed -> ID.equals(renamed.id()) && FRANCHISE_ID.equals(renamed.franchiseId())
                        && "Centro".equals(renamed.name()))
                .verifyComplete();
    }

    @Test
    void failsWithFranchiseNotFoundWithoutCallingTheBranchGatewayWhenTheFranchiseDoesNotExist() {
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.execute(branch("Centro")))
                .expectErrorMatches(error -> hasCode(error, ErrorCode.FRANCHISE_NOT_FOUND))
                .verify();
        verifyNoInteractions(branchGateway);
    }

    @Test
    void failsWithBranchNotFoundWhenNoBranchOfTheFranchiseMatches() {
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(branchGateway.rename(ID, FRANCHISE_ID, "Centro")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(branch("Centro")))
                .expectErrorMatches(error -> hasCode(error, ErrorCode.BRANCH_NOT_FOUND))
                .verify();
    }

    @Test
    void propagatesDuplicateNameFromTheGateway() {
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(branchGateway.rename(ID, FRANCHISE_ID, "Centro"))
                .thenReturn(Mono.error(new BusinessException(ErrorCode.DUPLICATE_BRANCH_NAME)));

        StepVerifier.create(useCase.execute(branch("Centro")))
                .expectErrorMatches(error -> hasCode(error, ErrorCode.DUPLICATE_BRANCH_NAME))
                .verify();
    }

    private static Branch branch(String name) {
        return Branch.builder().id(ID).franchiseId(FRANCHISE_ID).name(name).build();
    }

    private static boolean hasCode(Throwable error, ErrorCode code) {
        return error instanceof BusinessException exception && exception.getErrorCode() == code;
    }
}
