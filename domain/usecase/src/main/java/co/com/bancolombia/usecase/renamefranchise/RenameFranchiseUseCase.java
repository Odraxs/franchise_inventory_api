package co.com.bancolombia.usecase.renamefranchise;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.franchise.Franchise;
import co.com.bancolombia.model.franchise.gateways.FranchiseGateway;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class RenameFranchiseUseCase {

    private final FranchiseGateway franchiseGateway;

    /** The Franchise carries the id to rename and its new name. */
    public Mono<Franchise> execute(Franchise franchise) {
        return franchiseGateway.rename(franchise.id(), franchise.name())
                .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.FRANCHISE_NOT_FOUND)));
    }
}
