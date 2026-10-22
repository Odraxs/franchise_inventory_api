package co.com.bancolombia.model.product.gateways;

import co.com.bancolombia.model.product.Product;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProductGateway {

    /**
     * Saves a new Product in its Branch and emits it with its generated id.
     * Fails with a Business Exception coded DUPLICATE_PRODUCT_NAME when the Branch already has an active Product
     * with that name (ignoring case).
     */
    Mono<Product> save(Product product);

    /**
     * Soft-deletes the active Product with this id in the Branch and emits true.
     * Emits false when the Branch has no active Product with that id, so a second delete changes nothing.
     */
    Mono<Boolean> delete(UUID id, UUID branchId);

    /**
     * Sets the stock of the active Product with this id in the Branch and emits the updated Product.
     * Emits nothing when the Branch has no active Product with that id.
     */
    Mono<Product> updateStock(UUID id, UUID branchId, Integer stock);
}
