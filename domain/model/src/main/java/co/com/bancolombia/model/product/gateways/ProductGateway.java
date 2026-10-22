package co.com.bancolombia.model.product.gateways;

import co.com.bancolombia.model.product.Product;
import reactor.core.publisher.Mono;

public interface ProductGateway {

    /**
     * Saves a new Product in its Branch and emits it with its generated id.
     * Fails with a Business Exception coded DUPLICATE_PRODUCT_NAME when the Branch already has an active Product
     * with that name (ignoring case).
     */
    Mono<Product> save(Product product);
}
