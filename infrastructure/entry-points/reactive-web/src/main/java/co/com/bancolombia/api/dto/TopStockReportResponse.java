package co.com.bancolombia.api.dto;

import java.util.List;
import java.util.UUID;

public record TopStockReportResponse(UUID franchiseId, List<BranchTopStockResponse> branches) {
}
