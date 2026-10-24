package co.com.bancolombia.model.product;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

/**
 * One page of the Branches of a Franchise, each with its Top-Stock Product, and where that page sits:
 * its number and size, and how many Branches and pages the Franchise has.
 */
@Builder(toBuilder = true)
public record TopStockReport(UUID franchiseId, List<BranchTopStock> branches, int page, int size,
                             long totalElements, int totalPages) {
}
