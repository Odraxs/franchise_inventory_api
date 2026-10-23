package co.com.bancolombia.api.dto;

import co.com.bancolombia.model.product.BranchTopStock;
import lombok.Builder;

import java.util.Optional;
import java.util.UUID;

@Builder
public record BranchTopStockResponse(UUID branchId, String branchName, ProductResponse topProduct) {

    /** A Branch without Products has no top Product. */
    public static BranchTopStockResponse from(BranchTopStock branch) {
        return BranchTopStockResponse.builder()
                .branchId(branch.branchId())
                .branchName(branch.branchName())
                .topProduct(Optional.ofNullable(branch.topProduct()).map(ProductResponse::from).orElse(null))
                .build();
    }
}
