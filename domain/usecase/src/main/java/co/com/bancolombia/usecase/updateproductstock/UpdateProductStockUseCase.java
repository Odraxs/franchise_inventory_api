package co.com.bancolombia.usecase.updateproductstock;

import co.com.bancolombia.model.branch.gateways.BranchGateway;
import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.franchise.gateways.FranchiseGateway;
import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.model.product.UpdateProductStockCommand;
import co.com.bancolombia.model.product.gateways.ProductGateway;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class UpdateProductStockUseCase {

    private final FranchiseGateway franchiseGateway;
    private final BranchGateway branchGateway;
    private final ProductGateway productGateway;

    /** The command already holds a valid stock. */
    public Mono<Product> execute(UpdateProductStockCommand command) {
        return franchiseGateway.existsById(command.franchiseId())
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.FRANCHISE_NOT_FOUND)))
                .then(Mono.defer(() -> branchGateway.existsByIdAndFranchiseId(command.branchId(),
                        command.franchiseId())))
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND)))
                .then(Mono.defer(() -> productGateway.updateStock(command.productId(), command.branchId(),
                        command.stock())))
                .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND)));
    }
}
