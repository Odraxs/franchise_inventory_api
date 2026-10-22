package co.com.bancolombia.r2dbc.branch;

import co.com.bancolombia.model.branch.Branch;
import co.com.bancolombia.model.branch.gateways.BranchGateway;
import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class BranchRepositoryAdapter implements BranchGateway {

    private final BranchDataRepository repository;

    /** uq_branch_franchise_name rejects a name already taken in the franchise (ignoring case). */
    @Override
    public Mono<Branch> save(Branch branch) {
        return repository.save(toData(branch))
                .map(BranchRepositoryAdapter::toEntity)
                .onErrorMap(DuplicateKeyException.class,
                        error -> new BusinessException(ErrorCode.DUPLICATE_BRANCH_NAME, error));
    }

    @Override
    public Mono<Boolean> existsByIdAndFranchiseId(UUID id, UUID franchiseId) {
        return repository.existsByIdAndFranchiseId(id, franchiseId);
    }

    private static BranchData toData(Branch branch) {
        return new BranchData(branch.id(), branch.franchiseId(), branch.name());
    }

    private static Branch toEntity(BranchData data) {
        return Branch.builder().id(data.id()).franchiseId(data.franchiseId()).name(data.name()).build();
    }
}
