package co.com.bancolombia.api;

import co.com.bancolombia.api.dto.BranchResponse;
import co.com.bancolombia.api.dto.CreateBranchRequest;
import co.com.bancolombia.api.dto.CreateFranchiseRequest;
import co.com.bancolombia.api.dto.ErrorResponse;
import co.com.bancolombia.api.dto.FranchiseResponse;
import co.com.bancolombia.model.branch.Branch;
import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.franchise.Franchise;
import co.com.bancolombia.usecase.addbranch.AddBranchUseCase;
import co.com.bancolombia.usecase.createfranchise.CreateFranchiseUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class Handler {
    private final CreateFranchiseUseCase createFranchiseUseCase;
    private final AddBranchUseCase addBranchUseCase;

    public Mono<ServerResponse> createFranchise(ServerRequest serverRequest) {
        return serverRequest.bodyToMono(CreateFranchiseRequest.class)
                .onErrorMap(ServerWebInputException.class,
                        error -> new BusinessException(ErrorCode.INVALID_REQUEST, error))
                .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.INVALID_REQUEST)))
                .map(request -> Franchise.builder().name(request.name()).build())
                .flatMap(createFranchiseUseCase::execute)
                .flatMap(franchise -> ServerResponse
                        .created(UriComponentsBuilder.fromPath("/franchises/{id}").build(franchise.id()))
                        .bodyValue(new FranchiseResponse(franchise.id(), franchise.name())))
                .onErrorResume(BusinessException.class, this::toErrorResponse);
    }

    public Mono<ServerResponse> addBranch(ServerRequest serverRequest) {
        return parseId(serverRequest.pathVariable("franchiseId"))
                .flatMap(franchiseId -> serverRequest.bodyToMono(CreateBranchRequest.class)
                        .onErrorMap(ServerWebInputException.class,
                                error -> new BusinessException(ErrorCode.INVALID_REQUEST, error))
                        .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.INVALID_REQUEST)))
                        .map(request -> Branch.builder().franchiseId(franchiseId).name(request.name()).build()))
                .flatMap(addBranchUseCase::execute)
                .flatMap(branch -> ServerResponse
                        .created(UriComponentsBuilder.fromPath("/franchises/{franchiseId}/branches/{id}")
                                .build(branch.franchiseId(), branch.id()))
                        .bodyValue(new BranchResponse(branch.id(), branch.name())))
                .onErrorResume(BusinessException.class, this::toErrorResponse);
    }

    private static Mono<UUID> parseId(String value) {
        return Mono.fromCallable(() -> UUID.fromString(value))
                .onErrorMap(IllegalArgumentException.class,
                        error -> new BusinessException(ErrorCode.INVALID_REQUEST, error));
    }

    private Mono<ServerResponse> toErrorResponse(BusinessException exception) {
        return ServerResponse
                .status(statusOf(exception.getErrorCode()))
                .bodyValue(new ErrorResponse(exception.getErrorCode().name(), exception.getMessage()));
    }

    /** The code name sets the status: invalid values 400, not found 404, duplicates 409, others 422. */
    private static HttpStatus statusOf(ErrorCode errorCode) {
        String name = errorCode.name();
        if (name.startsWith("INVALID_")) {
            return HttpStatus.BAD_REQUEST;
        }
        if (name.endsWith("_NOT_FOUND")) {
            return HttpStatus.NOT_FOUND;
        }
        if (name.startsWith("DUPLICATE_")) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.UNPROCESSABLE_CONTENT;
    }
}
