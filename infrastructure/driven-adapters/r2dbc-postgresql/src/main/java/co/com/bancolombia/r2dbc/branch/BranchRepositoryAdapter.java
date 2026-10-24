package co.com.bancolombia.r2dbc.branch;

import co.com.bancolombia.model.branch.Branch;
import co.com.bancolombia.model.branch.gateways.BranchGateway;
import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.r2dbc.resilience.DatabaseResilience;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class BranchRepositoryAdapter implements BranchGateway {

    private final BranchDataRepository repository;
    private final DatabaseResilience resilience;

    /** uq_branch_franchise_name rejects a name already taken in the franchise (ignoring case). */
    @Override
    public Mono<Branch> save(Branch branch) {
        return resilience.nonIdempotent(repository.save(toData(branch)))
                .map(BranchRepositoryAdapter::toEntity)
                .onErrorMap(DuplicateKeyException.class,
                        error -> new BusinessException(ErrorCode.DUPLICATE_BRANCH_NAME, error));
    }

    /**
     * uq_branch_franchise_name rejects a name another Branch of the franchise has (ignoring case); a row never
     * conflicts with itself. A Timeout leaves the commit unknown, and repeating the UPDATE could overwrite a newer
     * rename, so only connection-level errors are retried.
     */
    @Override
    public Mono<Branch> rename(UUID id, UUID franchiseId, String name) {
        return resilience.nonIdempotent(repository.rename(id, franchiseId, name))
                .map(BranchRepositoryAdapter::toEntity)
                .onErrorMap(DuplicateKeyException.class,
                        error -> new BusinessException(ErrorCode.DUPLICATE_BRANCH_NAME, error));
    }

    @Override
    public Mono<Boolean> existsByIdAndFranchiseId(UUID id, UUID franchiseId) {
        return resilience.idempotent(repository.existsByIdAndFranchiseId(id, franchiseId));
    }

    @Override
    public Mono<Long> countByFranchiseId(UUID franchiseId) {
        return resilience.idempotent(repository.countByFranchiseId(franchiseId));
    }

    private static BranchData toData(Branch branch) {
        return new BranchData(branch.id(), branch.franchiseId(), branch.name());
    }

    private static Branch toEntity(BranchData data) {
        return Branch.builder().id(data.id()).franchiseId(data.franchiseId()).name(data.name()).build();
    }
}
