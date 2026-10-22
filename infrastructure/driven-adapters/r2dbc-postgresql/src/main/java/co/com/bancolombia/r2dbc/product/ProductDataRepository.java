package co.com.bancolombia.r2dbc.product;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProductDataRepository extends ReactiveCrudRepository<ProductData, UUID> {

    /** Sets deleted_at on the active Product and returns the number of rows changed (0 or 1). */
    @Modifying
    @Query("UPDATE product SET deleted_at = now() WHERE id = :id AND branch_id = :branchId AND deleted_at IS NULL")
    Mono<Integer> softDelete(@Param("id") UUID id, @Param("branchId") UUID branchId);
}
