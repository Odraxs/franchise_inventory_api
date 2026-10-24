package co.com.bancolombia.model.product;

import lombok.Builder;

import java.util.UUID;

/**
 * Sets the stock of the Product {@code productId} of the Branch {@code branchId} in the Franchise {@code franchiseId}.
 * It always holds a valid stock; creating one with another stock fails with INVALID_STOCK.
 */
@Builder(toBuilder = true)
public record UpdateProductStockCommand(UUID franchiseId, UUID branchId, UUID productId, Integer stock) {

    public UpdateProductStockCommand {
        Product.validateStock(stock);
    }
}
