package co.com.bancolombia.api.config;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import reactor.core.publisher.Mono;

import java.security.MessageDigest;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Accepts a request only when both parts match the configured API Credential.
 * It does no hashing, blocking work or I/O, so it is safe on the event loop.
 */
@RequiredArgsConstructor
final class ApiCredentialAuthenticationManager implements ReactiveAuthenticationManager {
    private static final String INVALID_CREDENTIAL = "Invalid API credential";
    private static final String NO_PASSWORD = "";

    private final ApiCredentialProperties credential;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        return Mono.just(authentication)
                .filter(this::matches)
                .<Authentication>map(given -> UsernamePasswordAuthenticationToken.authenticated(
                        given.getName(), null, AuthorityUtils.NO_AUTHORITIES))
                .switchIfEmpty(Mono.error(() -> new BadCredentialsException(INVALID_CREDENTIAL)));
    }

    /** Both comparisons always run, so the response time does not show which part was wrong. */
    private boolean matches(Authentication given) {
        boolean usernameMatches = constantTimeEquals(credential.username(), given.getName());
        boolean passwordMatches = constantTimeEquals(credential.password(),
                Objects.toString(given.getCredentials(), NO_PASSWORD));
        return usernameMatches && passwordMatches;
    }

    /** The time depends only on the length of the expected value, never on the given one. */
    private static boolean constantTimeEquals(String expected, String given) {
        return MessageDigest.isEqual(expected.getBytes(UTF_8), given.getBytes(UTF_8));
    }
}
