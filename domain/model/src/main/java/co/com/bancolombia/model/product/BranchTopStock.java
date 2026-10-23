package co.com.bancolombia.model.product;

import lombok.Builder;

import java.util.UUID;

/** A Branch with its Top-Stock Product, or a null topProduct when the Branch has no active Product. */
@Builder(toBuilder = true)
public record BranchTopStock(UUID branchId, String branchName, Product topProduct) {
}
