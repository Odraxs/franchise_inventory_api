package co.com.bancolombia.r2dbc.franchise;

import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

public interface FranchiseDataRepository extends ReactiveCrudRepository<FranchiseData, UUID>,
        ReactiveQueryByExampleExecutor<FranchiseData> {
}
