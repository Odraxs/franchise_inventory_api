package co.com.bancolombia.usecase.addproduct;

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
public class AddProductUseCase {

    private final FranchiseGateway franchiseGateway;
    private final BranchGateway branchGateway;
    private final ProductGateway productGateway;

    public Mono<Product> execute(UUID franchiseId, Product product) {
        return franchiseGateway.existsById(franchiseId)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.FRANCHISE_NOT_FOUND)))
                .then(Mono.defer(() -> branchGateway.existsByIdAndFranchiseId(product.branchId(), franchiseId)))
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND)))
                .then(Mono.defer(() -> productGateway.save(product)));
    }
}
