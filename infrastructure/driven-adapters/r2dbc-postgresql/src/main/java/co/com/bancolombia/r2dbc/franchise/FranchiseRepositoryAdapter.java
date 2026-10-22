package co.com.bancolombia.r2dbc.franchise;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.franchise.Franchise;
import co.com.bancolombia.model.franchise.gateways.FranchiseGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class FranchiseRepositoryAdapter implements FranchiseGateway {

    private final FranchiseDataRepository repository;

    /** uq_franchise_name rejects a name that is already taken (ignoring case). */
    @Override
    public Mono<Franchise> save(Franchise franchise) {
        return repository.save(toData(franchise))
                .map(FranchiseRepositoryAdapter::toEntity)
                .onErrorMap(DuplicateKeyException.class,
                        error -> new BusinessException(ErrorCode.DUPLICATE_FRANCHISE_NAME, error));
    }

    @Override
    public Mono<Boolean> existsById(UUID id) {
        return repository.existsById(id);
    }

    private static FranchiseData toData(Franchise franchise) {
        return new FranchiseData(franchise.id(), franchise.name());
    }

    private static Franchise toEntity(FranchiseData data) {
        return Franchise.builder().id(data.id()).name(data.name()).build();
    }
}
