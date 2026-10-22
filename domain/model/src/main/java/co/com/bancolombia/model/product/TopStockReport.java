package co.com.bancolombia.model.product;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

/** The Top-Stock Product of every Branch of a Franchise. */
@Builder(toBuilder = true)
public record TopStockReport(UUID franchiseId, List<BranchTopStock> branches) {
}
