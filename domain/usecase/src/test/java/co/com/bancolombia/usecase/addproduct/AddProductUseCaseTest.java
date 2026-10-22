package co.com.bancolombia.usecase.addproduct;

import co.com.bancolombia.model.branch.gateways.BranchGateway;
import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.franchise.gateways.FranchiseGateway;
import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.model.product.gateways.ProductGateway;
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
class AddProductUseCaseTest {

    private static final UUID FRANCHISE_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();

    @Mock
    private FranchiseGateway franchiseGateway;

    @Mock
    private BranchGateway branchGateway;

    @Mock
    private ProductGateway productGateway;

    private AddProductUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AddProductUseCase(franchiseGateway, branchGateway, productGateway);
    }

    @Test
    void savesProductInTheBranchAndEmitsItWithItsGeneratedId() {
        UUID id = UUID.randomUUID();
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(branchGateway.existsByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(productGateway.save(any(Product.class)))
                .thenAnswer(invocation -> Mono.just(((Product) invocation.getArgument(0)).toBuilder().id(id).build()));

        StepVerifier.create(useCase.execute(FRANCHISE_ID, product()))
                .expectNextMatches(saved -> id.equals(saved.id()) && BRANCH_ID.equals(saved.branchId())
                        && "Cola".equals(saved.name()) && saved.stock() == 0L)
                .verifyComplete();
    }

    @Test
    void failsWithFranchiseNotFoundWithoutSavingWhenTheFranchiseDoesNotExist() {
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.execute(FRANCHISE_ID, product()))
                .expectErrorMatches(error -> hasCode(error, ErrorCode.FRANCHISE_NOT_FOUND))
                .verify();
        verifyNoInteractions(branchGateway, productGateway);
    }

    @Test
    void failsWithBranchNotFoundWithoutSavingWhenTheBranchIsNotInTheFranchise() {
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(branchGateway.existsByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.execute(FRANCHISE_ID, product()))
                .expectErrorMatches(error -> hasCode(error, ErrorCode.BRANCH_NOT_FOUND))
                .verify();
        verifyNoInteractions(productGateway);
    }

    @Test
    void propagatesDuplicateNameFromTheGateway() {
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(branchGateway.existsByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(productGateway.save(any(Product.class)))
                .thenReturn(Mono.error(new BusinessException(ErrorCode.DUPLICATE_PRODUCT_NAME)));

        StepVerifier.create(useCase.execute(FRANCHISE_ID, product()))
                .expectErrorMatches(error -> hasCode(error, ErrorCode.DUPLICATE_PRODUCT_NAME))
                .verify();
        verify(productGateway).save(any(Product.class));
    }

    private static Product product() {
        return Product.builder().branchId(BRANCH_ID).name("Cola").stock(0L).build();
    }

    private static boolean hasCode(Throwable error, ErrorCode code) {
        return error instanceof BusinessException exception && exception.getErrorCode() == code;
    }
}
