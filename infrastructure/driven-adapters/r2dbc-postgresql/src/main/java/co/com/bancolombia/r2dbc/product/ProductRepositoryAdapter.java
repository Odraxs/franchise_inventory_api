package co.com.bancolombia.r2dbc.product;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.model.product.gateways.ProductGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryAdapter implements ProductGateway {

    private final ProductDataRepository repository;

    /** uq_product_branch_name rejects an active name already taken in the branch (ignoring case). */
    @Override
    public Mono<Product> save(Product product) {
        return repository.save(toData(product))
                .map(ProductRepositoryAdapter::toEntity)
                .onErrorMap(DuplicateKeyException.class,
                        error -> new BusinessException(ErrorCode.DUPLICATE_PRODUCT_NAME, error));
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
