package co.com.bancolombia.usecase.renameproduct;

import co.com.bancolombia.model.branch.gateways.BranchGateway;
import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.franchise.gateways.FranchiseGateway;
import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.model.product.RenameProductCommand;
import co.com.bancolombia.model.product.gateways.ProductGateway;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class RenameProductUseCase {

    private final FranchiseGateway franchiseGateway;
    private final BranchGateway branchGateway;
    private final ProductGateway productGateway;

    /** The command already holds a valid name; the stock is not touched. */
    public Mono<Product> execute(RenameProductCommand command) {
        return franchiseGateway.existsById(command.franchiseId())
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.FRANCHISE_NOT_FOUND)))
                .then(Mono.defer(() -> branchGateway.existsByIdAndFranchiseId(command.branchId(),
                        command.franchiseId())))
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND)))
                .then(Mono.defer(() -> productGateway.rename(command.productId(), command.branchId(), command.name())))
                .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND)));
    }
}
