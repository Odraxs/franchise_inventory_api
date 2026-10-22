package co.com.bancolombia.usecase.createfranchise;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.franchise.Franchise;
import co.com.bancolombia.model.franchise.gateways.FranchiseGateway;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class CreateFranchiseUseCase {
    static final int MAX_NAME_LENGTH = 100;

    private final FranchiseGateway franchiseGateway;

    public Mono<Franchise> execute(Franchise franchise) {
        return Mono.justOrEmpty(franchise.name())
                .map(String::strip)
                .filter(name -> !name.isEmpty() && name.length() <= MAX_NAME_LENGTH)
                .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.INVALID_FRANCHISE_NAME)))
                .map(name -> franchise.toBuilder().name(name).build())
                .flatMap(franchiseGateway::save);
    }
}
