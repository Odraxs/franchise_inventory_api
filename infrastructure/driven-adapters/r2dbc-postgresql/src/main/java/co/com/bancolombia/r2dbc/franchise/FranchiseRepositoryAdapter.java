package co.com.bancolombia.r2dbc.franchise;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.franchise.Franchise;
import co.com.bancolombia.model.franchise.gateways.FranchiseGateway;
import co.com.bancolombia.r2dbc.helper.ReactiveAdapterOperations;
import org.reactivecommons.utils.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public class FranchiseRepositoryAdapter
        extends ReactiveAdapterOperations<Franchise, FranchiseData, UUID, FranchiseDataRepository>
        implements FranchiseGateway {

    public FranchiseRepositoryAdapter(FranchiseDataRepository repository, ObjectMapper mapper) {
        super(repository, mapper, data -> Franchise.builder().id(data.id()).name(data.name()).build());
    }

    @Override
    protected FranchiseData toData(Franchise franchise) {
        return new FranchiseData(franchise.getId(), franchise.getName());
    }

    /** uq_franchise_name rejects a name that is already taken (ignoring case). */
    @Override
    public Mono<Franchise> save(Franchise franchise) {
        return super.save(franchise)
                .onErrorMap(DuplicateKeyException.class,
                        error -> new BusinessException(ErrorCode.DUPLICATE_FRANCHISE_NAME, error));
    }
}
