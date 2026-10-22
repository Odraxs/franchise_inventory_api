package co.com.bancolombia.r2dbc.product;

import java.util.UUID;

/** Row of the Top-Stock query. The product columns are null when the branch has no active product. */
public record TopStockRow(UUID branchId, String branchName, UUID productId, String productName, Integer productStock) {
}
