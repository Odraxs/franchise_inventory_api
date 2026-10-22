package co.com.bancolombia.r2dbc.product;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.model.product.gateways.ProductGateway;
import co.com.bancolombia.r2dbc.resilience.DatabaseResilience;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryAdapter implements ProductGateway {

    private final ProductDataRepository repository;
    private final DatabaseResilience resilience;

    /** uq_product_branch_name rejects an active name already taken in the branch (ignoring case). */
    @Override
    public Mono<Product> save(Product product) {
        return resilience.nonIdempotent(repository.save(toData(product)))
                .map(ProductRepositoryAdapter::toEntity)
                .onErrorMap(DuplicateKeyException.class,
                        error -> new BusinessException(ErrorCode.DUPLICATE_PRODUCT_NAME, error));
    }

    /** Only an active Product of the branch is marked, so a repeated delete changes no row. */
    @Override
    public Mono<Boolean> delete(UUID id, UUID branchId) {
        return repository.softDelete(id, branchId).map(rows -> rows > 0);
    }

    private static ProductData toData(Product product) {
        return ProductData.builder().id(product.id()).branchId(product.branchId()).name(product.name())
                .stock(product.stock()).build();
    }

    private static Product toEntity(ProductData data) {
        return Product.builder().id(data.id()).branchId(data.branchId()).name(data.name())
                .stock(data.stock()).build();
    }
}
