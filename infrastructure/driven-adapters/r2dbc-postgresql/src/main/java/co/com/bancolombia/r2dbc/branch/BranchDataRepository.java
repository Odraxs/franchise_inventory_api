package co.com.bancolombia.r2dbc.branch;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface BranchDataRepository extends ReactiveCrudRepository<BranchData, UUID> {

    Mono<Boolean> existsByIdAndFranchiseId(UUID id, UUID franchiseId);

    Mono<Long> countByFranchiseId(UUID franchiseId);

    /** Sets the name in one statement and returns the updated row, or empty when no row has this id in the franchise. */
    @Query("UPDATE branch SET name = :name WHERE id = :id AND franchise_id = :franchiseId "
            + "RETURNING id, franchise_id, name")
    Mono<BranchData> rename(@Param("id") UUID id, @Param("franchiseId") UUID franchiseId, @Param("name") String name);
}
