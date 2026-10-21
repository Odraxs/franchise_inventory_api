package co.com.bancolombia.api;

import co.com.bancolombia.api.dto.CreateFranchiseRequest;
import co.com.bancolombia.api.dto.ErrorResponse;
import co.com.bancolombia.api.dto.FranchiseResponse;
import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.franchise.Franchise;
import co.com.bancolombia.usecase.createfranchise.CreateFranchiseUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class Handler {
    private static final Map<ErrorCode, HttpStatus> STATUS_BY_ERROR_CODE = Map.of(
            ErrorCode.INVALID_FRANCHISE_NAME, HttpStatus.BAD_REQUEST,
            ErrorCode.DUPLICATE_FRANCHISE_NAME, HttpStatus.CONFLICT);

    private final CreateFranchiseUseCase createFranchiseUseCase;

    public Mono<ServerResponse> createFranchise(ServerRequest serverRequest) {
        return serverRequest.bodyToMono(CreateFranchiseRequest.class)
                .defaultIfEmpty(new CreateFranchiseRequest(null))
                .map(request -> Franchise.builder().name(request.name()).build())
                .flatMap(createFranchiseUseCase::execute)
                .flatMap(franchise -> ServerResponse
                        .created(UriComponentsBuilder.fromPath("/franchises/{id}").build(franchise.getId()))
                        .bodyValue(new FranchiseResponse(franchise.getId(), franchise.getName())))
                .onErrorResume(BusinessException.class, this::toErrorResponse);
    }

    private Mono<ServerResponse> toErrorResponse(BusinessException exception) {
        return ServerResponse
                .status(STATUS_BY_ERROR_CODE.getOrDefault(exception.getErrorCode(), HttpStatus.UNPROCESSABLE_CONTENT))
                .bodyValue(new ErrorResponse(exception.getErrorCode().name(), exception.getMessage()));
    }
}
