package co.com.bancolombia.api;

import co.com.bancolombia.api.dto.BranchResponse;
import co.com.bancolombia.api.dto.CreateBranchRequest;
import co.com.bancolombia.api.dto.RenameBranchRequest;
import co.com.bancolombia.model.branch.Branch;
import co.com.bancolombia.usecase.addbranch.AddBranchUseCase;
import co.com.bancolombia.usecase.renamebranch.RenameBranchUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class BranchHandler {
    private final AddBranchUseCase addBranchUseCase;
    private final RenameBranchUseCase renameBranchUseCase;

    public Mono<ServerResponse> addBranch(ServerRequest serverRequest) {
        return RequestReader.parseId(serverRequest, ApiPaths.FRANCHISE_ID)
                .flatMap(franchiseId -> RequestReader.readBody(serverRequest, CreateBranchRequest.class)
                        .map(request -> Branch.builder().franchiseId(franchiseId).name(request.name()).build()))
                .flatMap(addBranchUseCase::execute)
                .flatMap(branch -> ServerResponse
                        .created(UriComponentsBuilder.fromPath(ApiPaths.BRANCH)
                                .build(branch.franchiseId(), branch.id()))
                        .bodyValue(BranchResponse.from(branch)))
                .transform(ErrorResponses::handle);
    }

    public Mono<ServerResponse> renameBranch(ServerRequest serverRequest) {
        return Mono.zip(RequestReader.parseId(serverRequest, ApiPaths.FRANCHISE_ID),
                        RequestReader.parseId(serverRequest, ApiPaths.ID))
                .flatMap(ids -> RequestReader.readBody(serverRequest, RenameBranchRequest.class)
                        .map(request -> Branch.builder().id(ids.getT2()).franchiseId(ids.getT1())
                                .name(request.name()).build()))
                .flatMap(renameBranchUseCase::execute)
                .flatMap(branch -> ServerResponse.ok().bodyValue(BranchResponse.from(branch)))
                .transform(ErrorResponses::handle);
    }
}
