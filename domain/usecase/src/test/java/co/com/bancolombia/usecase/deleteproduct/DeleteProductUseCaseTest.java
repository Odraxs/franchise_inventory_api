package co.com.bancolombia.usecase.deleteproduct;

import co.com.bancolombia.model.branch.gateways.BranchGateway;
import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.franchise.gateways.FranchiseGateway;
import co.com.bancolombia.model.product.gateways.ProductGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteProductUseCaseTest {

    private static final UUID FRANCHISE_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Mock
    private FranchiseGateway franchiseGateway;

    @Mock
    private BranchGateway branchGateway;

    @Mock
    private ProductGateway productGateway;

    private DeleteProductUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DeleteProductUseCase(franchiseGateway, branchGateway, productGateway);
    }

    @Test
    void deletesTheProductOfTheBranchAndCompletesEmpty() {
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(branchGateway.existsByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(productGateway.delete(PRODUCT_ID, BRANCH_ID)).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute(FRANCHISE_ID, BRANCH_ID, PRODUCT_ID)).verifyComplete();
        verify(productGateway).delete(PRODUCT_ID, BRANCH_ID);
    }

    @Test
    void failsWithFranchiseNotFoundWithoutDeletingWhenTheFranchiseDoesNotExist() {
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.execute(FRANCHISE_ID, BRANCH_ID, PRODUCT_ID))
                .expectErrorMatches(error -> hasCode(error, ErrorCode.FRANCHISE_NOT_FOUND))
                .verify();
        verifyNoInteractions(branchGateway, productGateway);
    }

    @Test
    void failsWithBranchNotFoundWithoutDeletingWhenTheBranchIsNotInTheFranchise() {
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(branchGateway.existsByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.execute(FRANCHISE_ID, BRANCH_ID, PRODUCT_ID))
                .expectErrorMatches(error -> hasCode(error, ErrorCode.BRANCH_NOT_FOUND))
                .verify();
        verifyNoInteractions(productGateway);
    }

    @Test
    void failsWithProductNotFoundWhenNoActiveProductMatches() {
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(branchGateway.existsByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(productGateway.delete(PRODUCT_ID, BRANCH_ID)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.execute(FRANCHISE_ID, BRANCH_ID, PRODUCT_ID))
                .expectErrorMatches(error -> hasCode(error, ErrorCode.PRODUCT_NOT_FOUND))
                .verify();
    }

    private static boolean hasCode(Throwable error, ErrorCode code) {
        return error instanceof BusinessException exception && exception.getErrorCode() == code;
    }
}
