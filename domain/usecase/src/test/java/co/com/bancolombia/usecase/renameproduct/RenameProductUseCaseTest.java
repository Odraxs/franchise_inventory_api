package co.com.bancolombia.usecase.renameproduct;

import co.com.bancolombia.model.branch.gateways.BranchGateway;
import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.franchise.gateways.FranchiseGateway;
import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.model.product.RenameProductCommand;
import co.com.bancolombia.model.product.gateways.ProductGateway;
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
class RenameProductUseCaseTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID FRANCHISE_ID = UUID.randomUUID();

    @Mock
    private FranchiseGateway franchiseGateway;

    @Mock
    private BranchGateway branchGateway;

    @Mock
    private ProductGateway productGateway;

    private RenameProductUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RenameProductUseCase(franchiseGateway, branchGateway, productGateway);
    }

    @Test
    void renamesWithTheStrippedNameAndEmitsTheUpdatedProduct() {
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(branchGateway.existsByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(productGateway.rename(ID, BRANCH_ID, "Cola"))
                .thenReturn(Mono.just(Product.builder().id(ID).branchId(BRANCH_ID).name("Cola").stock(7).build()));

        StepVerifier.create(useCase.execute(command("  Cola  ")))
                .expectNextMatches(renamed -> ID.equals(renamed.id()) && BRANCH_ID.equals(renamed.branchId())
                        && "Cola".equals(renamed.name()) && renamed.stock() == 7)
                .verifyComplete();
    }

    @Test
    void failsWithFranchiseNotFoundWithoutCallingTheBranchOrProductGateway() {
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.execute(command("Cola")))
                .expectErrorMatches(error -> hasCode(error, ErrorCode.FRANCHISE_NOT_FOUND))
                .verify();
        verifyNoInteractions(branchGateway, productGateway);
    }

    @Test
    void failsWithBranchNotFoundWithoutCallingTheProductGateway() {
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(branchGateway.existsByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.execute(command("Cola")))
                .expectErrorMatches(error -> hasCode(error, ErrorCode.BRANCH_NOT_FOUND))
                .verify();
        verifyNoInteractions(productGateway);
    }

    @Test
    void failsWithProductNotFoundWhenNoActiveProductOfTheBranchMatches() {
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(branchGateway.existsByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(productGateway.rename(ID, BRANCH_ID, "Cola")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(command("Cola")))
                .expectErrorMatches(error -> hasCode(error, ErrorCode.PRODUCT_NOT_FOUND))
                .verify();
    }

    @Test
    void propagatesDuplicateNameFromTheGateway() {
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(branchGateway.existsByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(productGateway.rename(ID, BRANCH_ID, "Cola"))
                .thenReturn(Mono.error(new BusinessException(ErrorCode.DUPLICATE_PRODUCT_NAME)));

        StepVerifier.create(useCase.execute(command("Cola")))
                .expectErrorMatches(error -> hasCode(error, ErrorCode.DUPLICATE_PRODUCT_NAME))
                .verify();
    }

    private static RenameProductCommand command(String name) {
        return RenameProductCommand.builder().franchiseId(FRANCHISE_ID).branchId(BRANCH_ID).productId(ID).name(name)
                .build();
    }

    private static boolean hasCode(Throwable error, ErrorCode code) {
        return error instanceof BusinessException exception && exception.getErrorCode() == code;
    }
}
