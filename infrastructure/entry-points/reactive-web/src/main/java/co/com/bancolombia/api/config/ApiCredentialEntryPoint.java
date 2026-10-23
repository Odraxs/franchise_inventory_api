package co.com.bancolombia.api.config;

import co.com.bancolombia.api.dto.ErrorResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Answers a missing, wrong or malformed API Credential with one and the same 401,
 * so the response never shows which part of the credential was wrong.
 */
final class ApiCredentialEntryPoint implements ServerAuthenticationEntryPoint {
    private static final String CHALLENGE = "Basic realm=\"franchise-inventory\"";
    private static final ErrorResponse UNAUTHORIZED = ErrorResponse.builder()
            .code("UNAUTHORIZED")
            .message("A valid API credential is required, sent with HTTP Basic")
            .build();

    private final ServerResponse.Context context;

    ApiCredentialEntryPoint(List<HttpMessageWriter<?>> messageWriters) {
        this.context = new WriterContext(messageWriters);
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException exception) {
        return ServerResponse.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, CHALLENGE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(UNAUTHORIZED)
                .flatMap(response -> response.writeTo(exchange, context));
    }

    /** Writes the body with the app's codecs, the same ones the Handler uses. */
    private record WriterContext(List<HttpMessageWriter<?>> messageWriters) implements ServerResponse.Context {
        @Override
        public List<ViewResolver> viewResolvers() {
            return List.of();
        }
    }
}
