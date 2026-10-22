package co.com.bancolombia.api.dto;

import java.util.UUID;

public record BranchTopStockResponse(UUID branchId, String branchName, ProductResponse topProduct) {
}
