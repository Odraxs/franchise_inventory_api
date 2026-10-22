package co.com.bancolombia.model.branch.gateways;

import co.com.bancolombia.model.branch.Branch;
import reactor.core.publisher.Mono;

public interface BranchGateway {

    /**
     * Saves a new Branch in its Franchise and emits it with its generated id.
     * Fails with a Business Exception coded DUPLICATE_BRANCH_NAME when the Franchise already has a Branch
     * with that name (ignoring case).
     */
    Mono<Branch> save(Branch branch);
}
