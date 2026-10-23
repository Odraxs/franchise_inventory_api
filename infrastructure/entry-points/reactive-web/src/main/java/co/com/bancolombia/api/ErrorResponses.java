package co.com.bancolombia.api;

import co.com.bancolombia.api.dto.ErrorResponse;
import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.exception.DomainException;
import co.com.bancolombia.model.exception.TechnicalException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/** Maps domain exceptions to HTTP error responses. */
@Slf4j
final class ErrorResponses {
    private static final String INVALID_PREFIX = "INVALID_";
    private static final String NOT_FOUND_SUFFIX = "_NOT_FOUND";
    private static final String DUPLICATE_PREFIX = "DUPLICATE_";

    private ErrorResponses() {
    }

    /** Technical failures are logged before mapping; business failures are expected outcomes and stay unlogged. */
    static Mono<ServerResponse> handle(Mono<ServerResponse> response) {
        return response
                .onErrorResume(TechnicalException.class, ErrorResponses::toTechnicalErrorResponse)
                .onErrorResume(DomainException.class, ErrorResponses::toErrorResponse);
    }

    private static Mono<ServerResponse> toTechnicalErrorResponse(TechnicalException exception) {
        log.error("Technical failure: {}", exception.getCode(), exception);
        return toErrorResponse(exception);
    }

    private static Mono<ServerResponse> toErrorResponse(DomainException exception) {
        return ServerResponse
                .status(statusOf(exception))
                .bodyValue(ErrorResponse.builder().code(exception.getCode())
                        .message(exception.getMessage()).build());
    }

    /** A technical failure is always 503, whatever its code name; a business failure keeps the code-name rules. */
    private static HttpStatus statusOf(DomainException exception) {
        if (exception instanceof BusinessException businessException) {
            return businessStatusOf(businessException.getErrorCode());
        }
        return HttpStatus.SERVICE_UNAVAILABLE;
    }

    /** The code name sets the status: invalid values 400, not found 404, duplicates 409, others 422. */
    private static HttpStatus businessStatusOf(ErrorCode errorCode) {
        String name = errorCode.name();
        if (name.startsWith(INVALID_PREFIX)) {
            return HttpStatus.BAD_REQUEST;
        }
        if (name.endsWith(NOT_FOUND_SUFFIX)) {
            return HttpStatus.NOT_FOUND;
        }
        if (name.startsWith(DUPLICATE_PREFIX)) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.UNPROCESSABLE_CONTENT;
    }
}
