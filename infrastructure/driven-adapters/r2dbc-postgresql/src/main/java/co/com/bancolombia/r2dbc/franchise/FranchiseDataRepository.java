package co.com.bancolombia.r2dbc.franchise;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FranchiseDataRepository extends ReactiveCrudRepository<FranchiseData, UUID> {

    /** Sets the name in one statement and returns the updated row, or empty when no row has this id. */
    @Query("UPDATE franchise SET name = :name WHERE id = :id RETURNING id, name")
    Mono<FranchiseData> rename(@Param("id") UUID id, @Param("name") String name);
}
