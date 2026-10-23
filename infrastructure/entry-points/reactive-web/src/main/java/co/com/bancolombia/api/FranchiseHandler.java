package co.com.bancolombia.api;

import co.com.bancolombia.api.dto.CreateFranchiseRequest;
import co.com.bancolombia.api.dto.FranchiseResponse;
import co.com.bancolombia.api.dto.RenameFranchiseRequest;
import co.com.bancolombia.model.franchise.Franchise;
import co.com.bancolombia.usecase.createfranchise.CreateFranchiseUseCase;
import co.com.bancolombia.usecase.renamefranchise.RenameFranchiseUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class FranchiseHandler {
    private final CreateFranchiseUseCase createFranchiseUseCase;
    private final RenameFranchiseUseCase renameFranchiseUseCase;

    public Mono<ServerResponse> createFranchise(ServerRequest serverRequest) {
        return RequestReader.readBody(serverRequest, CreateFranchiseRequest.class)
                .map(request -> Franchise.builder().name(request.name()).build())
                .flatMap(createFranchiseUseCase::execute)
                .flatMap(franchise -> ServerResponse
                        .created(UriComponentsBuilder.fromPath(ApiPaths.FRANCHISE).build(franchise.id()))
                        .bodyValue(FranchiseResponse.from(franchise)))
                .transform(ErrorResponses::handle);
    }

    public Mono<ServerResponse> renameFranchise(ServerRequest serverRequest) {
        return RequestReader.parseId(serverRequest, ApiPaths.ID)
                .flatMap(id -> RequestReader.readBody(serverRequest, RenameFranchiseRequest.class)
                        .map(request -> Franchise.builder().id(id).name(request.name()).build()))
                .flatMap(renameFranchiseUseCase::execute)
                .flatMap(franchise -> ServerResponse.ok().bodyValue(FranchiseResponse.from(franchise)))
                .transform(ErrorResponses::handle);
    }
}
