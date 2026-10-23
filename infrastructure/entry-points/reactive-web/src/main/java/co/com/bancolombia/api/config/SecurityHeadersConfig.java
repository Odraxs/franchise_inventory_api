package co.com.bancolombia.api.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/** Runs before the Spring Security filter chain, so a 401 from the chain carries these headers too. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersConfig implements WebFilter {
    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; frame-ancestors 'self'; form-action 'self'";
    /** Swagger UI draws its icons from data: images and sets inline styles, which the API policy blocks. */
    private static final String SWAGGER_UI_CONTENT_SECURITY_POLICY =
            "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
                    + "frame-ancestors 'self'; form-action 'self'";
    private static final List<String> SWAGGER_UI_PATH_PREFIXES = List.of("/swagger-ui", "/webjars/swagger-ui");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        HttpHeaders headers = exchange.getResponse().getHeaders();
        headers.set("Content-Security-Policy", contentSecurityPolicyOf(exchange));
        headers.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.remove("Server");
        headers.set("Cache-Control", "no-store");
        headers.set("Pragma", "no-cache");
        headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
        return chain.filter(exchange);
    }

    private static String contentSecurityPolicyOf(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        return SWAGGER_UI_PATH_PREFIXES.stream().anyMatch(path::startsWith)
                ? SWAGGER_UI_CONTENT_SECURITY_POLICY
                : CONTENT_SECURITY_POLICY;
    }
}
