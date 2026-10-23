package co.com.bancolombia.api.dto;

import co.com.bancolombia.model.product.BranchTopStock;

import java.util.Optional;
import java.util.UUID;

public record BranchTopStockResponse(UUID branchId, String branchName, ProductResponse topProduct) {

    /** A Branch without Products has no top Product. */
    public static BranchTopStockResponse from(BranchTopStock branch) {
        return new BranchTopStockResponse(branch.branchId(), branch.branchName(),
                Optional.ofNullable(branch.topProduct()).map(ProductResponse::from).orElse(null));
    }
}
