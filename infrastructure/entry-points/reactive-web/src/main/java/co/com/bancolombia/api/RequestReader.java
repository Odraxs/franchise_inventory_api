package co.com.bancolombia.api;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Reads path ids, query parameters and bodies; any value that cannot be read is an invalid request. */
final class RequestReader {

    private RequestReader() {
    }

    /** A body that cannot be parsed, or a missing body, is an invalid request. */
    static <T> Mono<T> readBody(ServerRequest serverRequest, Class<T> type) {
        return serverRequest.bodyToMono(type)
                .onErrorMap(ServerWebInputException.class,
                        error -> new BusinessException(ErrorCode.INVALID_REQUEST, error))
                .switchIfEmpty(Mono.error(() -> new BusinessException(ErrorCode.INVALID_REQUEST)));
    }

    static Mono<UUID> parseId(ServerRequest serverRequest, String pathVariable) {
        return Mono.fromCallable(() -> UUID.fromString(serverRequest.pathVariable(pathVariable)))
                .onErrorMap(IllegalArgumentException.class,
                        error -> new BusinessException(ErrorCode.INVALID_REQUEST, error));
    }

    /** A missing parameter takes the default; a value that is not an int (empty, non-numeric, too large) is invalid. */
    static Mono<Integer> parseIntQueryParam(ServerRequest serverRequest, String name, int defaultValue) {
        return Mono.fromCallable(() -> serverRequest.queryParam(name).map(Integer::valueOf).orElse(defaultValue))
                .onErrorMap(NumberFormatException.class,
                        error -> new BusinessException(ErrorCode.INVALID_REQUEST, error));
    }
}
