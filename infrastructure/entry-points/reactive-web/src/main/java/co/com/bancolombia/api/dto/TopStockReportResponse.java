package co.com.bancolombia.api.dto;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record TopStockReportResponse(UUID franchiseId, List<BranchTopStockResponse> branches) {
}
