package co.com.bancolombia.r2dbc.branch;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface BranchDataRepository extends ReactiveCrudRepository<BranchData, UUID> {

    Mono<Boolean> existsByIdAndFranchiseId(UUID id, UUID franchiseId);
}
