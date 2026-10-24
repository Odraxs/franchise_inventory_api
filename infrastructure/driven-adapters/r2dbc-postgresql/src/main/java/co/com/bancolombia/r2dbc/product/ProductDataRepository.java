package co.com.bancolombia.r2dbc.product;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProductDataRepository extends ReactiveCrudRepository<ProductData, UUID> {

    /** Sets deleted_at on the active Product and returns the number of rows changed (0 or 1). */
    @Modifying
    @Query("UPDATE product SET deleted_at = now() WHERE id = :id AND branch_id = :branchId AND deleted_at IS NULL")
    Mono<Integer> softDelete(@Param("id") UUID id, @Param("branchId") UUID branchId);

    /** Updates the stock of an active product of the branch; emits nothing when no row matches. */
    @Query("UPDATE product SET stock = :stock WHERE id = :id AND branch_id = :branchId AND deleted_at IS NULL "
            + "RETURNING id, branch_id, name, stock")
    Mono<ProductData> updateStock(@Param("id") UUID id, @Param("branchId") UUID branchId,
                                  @Param("stock") Integer stock);

    /**
     * Sets the name in one statement and returns the updated row, or empty when no active product has this id in
     * the branch.
     */
    @Query("UPDATE product SET name = :name WHERE id = :id AND branch_id = :branchId AND deleted_at IS NULL "
            + "RETURNING id, branch_id, name, stock")
    Mono<ProductData> rename(@Param("id") UUID id, @Param("branchId") UUID branchId, @Param("name") String name);

    /**
     * One page of the branches of the franchise, sorted by lower(name) then id, each with its active product of
     * highest stock, ties going to the oldest one. The page is cut before the join, so the lateral subquery runs only
     * for the branches of the page; the cut follows uq_branch_franchise_name and the lateral subquery follows
     * ix_product_top_stock. A branch without active products keeps null product columns.
     */
    @Query("""
            SELECT b.id AS branch_id, b.name AS branch_name,
                   p.id AS product_id, p.name AS product_name, p.stock AS product_stock
            FROM (
                SELECT id, name FROM branch
                WHERE franchise_id = :franchiseId
                ORDER BY lower(name), id
                LIMIT :size OFFSET :offset
            ) b
            LEFT JOIN LATERAL (
                SELECT id, name, stock FROM product
                WHERE branch_id = b.id AND deleted_at IS NULL
                ORDER BY stock DESC, created_at, id
                LIMIT 1
            ) p ON TRUE
            ORDER BY lower(b.name), b.id
            """)
    Flux<TopStockRow> findTopStockByFranchiseId(@Param("franchiseId") UUID franchiseId, @Param("size") int size,
                                                @Param("offset") long offset);
}
