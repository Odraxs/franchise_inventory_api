package co.com.bancolombia.usecase.renamebranch;

import co.com.bancolombia.model.branch.Branch;
import co.com.bancolombia.model.branch.gateways.BranchGateway;
import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.franchise.gateways.FranchiseGateway;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class RenameBranchUseCase {

    private final FranchiseGateway franchiseGateway;
    private final BranchGateway branchGateway;

    /** The Branch carries the id to rename, its Franchise and its new name. */
    public Mono<Branch> execute(Branch branch) {
        return franchiseGateway.existsById(branch.franchiseId())
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.FRANCHISE_NOT_FOUND)))
                .then(Mono.defer(() -> branchGateway.rename(branch.id(), branch.franchiseId(), branch.name())))
                .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND)));
    }
}
