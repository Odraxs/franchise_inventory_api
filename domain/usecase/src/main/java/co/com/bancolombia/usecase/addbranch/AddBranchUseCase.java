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
    static final int MAX_NAME_LENGTH = 100;

    private final FranchiseGateway franchiseGateway;
    private final BranchGateway branchGateway;

    public Mono<Branch> execute(Branch branch) {
        return Mono.justOrEmpty(branch.name())
                .map(String::strip)
                .filter(name -> !name.isEmpty() && name.length() <= MAX_NAME_LENGTH)
                .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.INVALID_BRANCH_NAME)))
                .map(name -> branch.toBuilder().name(name).build())
                .flatMap(valid -> franchiseGateway.existsById(valid.franchiseId())
                        .filter(Boolean::booleanValue)
                        .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.FRANCHISE_NOT_FOUND)))
                        .thenReturn(valid))
                .flatMap(branchGateway::save);
    }
}
