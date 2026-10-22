package co.com.bancolombia.usecase.addbranch;

import co.com.bancolombia.model.branch.Branch;
import co.com.bancolombia.model.branch.gateways.BranchGateway;
import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.franchise.gateways.FranchiseGateway;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class AddBranchUseCase {

    private final FranchiseGateway franchiseGateway;
    private final BranchGateway branchGateway;

    public Mono<Branch> execute(Branch branch) {
        return franchiseGateway.existsById(branch.franchiseId())
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.FRANCHISE_NOT_FOUND)))
                .then(Mono.defer(() -> branchGateway.save(branch)));
    }
}
