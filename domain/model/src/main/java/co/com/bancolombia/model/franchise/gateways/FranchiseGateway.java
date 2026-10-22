package co.com.bancolombia.model.franchise.gateways;

import co.com.bancolombia.model.franchise.Franchise;
import reactor.core.publisher.Mono;

public interface FranchiseGateway {

    /**
     * Saves a new Franchise and emits it with its generated id.
     * Fails with a Business Exception coded DUPLICATE_FRANCHISE_NAME when the name is already taken (ignoring case).
     */
    Mono<Franchise> save(Franchise franchise);
}
