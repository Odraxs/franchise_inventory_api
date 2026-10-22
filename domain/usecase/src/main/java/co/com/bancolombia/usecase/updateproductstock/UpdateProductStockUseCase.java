package co.com.bancolombia.usecase.updateproductstock;

import co.com.bancolombia.model.branch.gateways.BranchGateway;
import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.franchise.gateways.FranchiseGateway;
import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.model.product.gateways.ProductGateway;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class UpdateProductStockUseCase {

    private final FranchiseGateway franchiseGateway;
    private final BranchGateway branchGateway;
    private final ProductGateway productGateway;

    public Mono<Product> execute(UUID franchiseId, UUID branchId, UUID productId, Integer stock) {
        return Mono.fromRunnable(() -> Product.validateStock(stock))
                .then(Mono.defer(() -> franchiseGateway.existsById(franchiseId)))
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.FRANCHISE_NOT_FOUND)))
                .then(Mono.defer(() -> branchGateway.existsByIdAndFranchiseId(branchId, franchiseId)))
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND)))
                .then(Mono.defer(() -> productGateway.updateStock(productId, branchId, stock)))
                .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND)));
    }
}
