package co.com.bancolombia.model.product;

import lombok.Builder;

import java.util.UUID;

/**
 * Renames the Product {@code productId} of the Branch {@code branchId} in the Franchise {@code franchiseId}.
 * It always holds a valid stripped name; creating one with another name fails with INVALID_PRODUCT_NAME.
 */
@Builder(toBuilder = true)
public record RenameProductCommand(UUID franchiseId, UUID branchId, UUID productId, String name) {

    public RenameProductCommand {
        name = Product.validName(name);
    }
}
