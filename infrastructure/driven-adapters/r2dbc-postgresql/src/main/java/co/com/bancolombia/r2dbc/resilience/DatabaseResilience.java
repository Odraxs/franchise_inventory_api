package co.com.bancolombia.r2dbc.resilience;

import co.com.bancolombia.model.exception.TechnicalException;
import co.com.bancolombia.model.exception.TechnicalException.ErrorCode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeoutException;

/**
 * Wraps every database call with a Timeout, the shared Circuit Breaker and a Retry, in that order from inner
 * to outer: each retry attempt gets its own Timeout and counts in the breaker. Settings are in application.yaml.
 */
@Component
@RequiredArgsConstructor
public class DatabaseResilience {

    private static final String DATABASE = "database";
    static final String DATABASE_NON_IDEMPOTENT = "databaseNonIdempotent";
    static final String DATABASE_IDEMPOTENT = "databaseIdempotent";

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;
    private final RetryRegistry retryRegistry;

    /** For a read or the soft delete: safe to repeat, so a Timeout is retried too. */
    public <T> Mono<T> idempotent(Mono<T> call) {
        return protect(call, DATABASE_IDEMPOTENT);
    }

    /**
     * For a multi-row read: the Timeout covers the whole stream and a retry re-reads it from the start. Each attempt
     * is buffered and its rows are emitted only once it completes, so a failed attempt never leaks partial rows.
     */
    public <T> Flux<T> idempotent(Flux<T> call) {
        return idempotent(call.collectList()).flatMapIterable(rows -> rows);
    }

    /** For an INSERT: it may be committed when it times out, so only connection-level errors are retried. */
    public <T> Mono<T> nonIdempotent(Mono<T> call) {
        return protect(call, DATABASE_NON_IDEMPOTENT);
    }

    private <T> Mono<T> protect(Mono<T> call, String retryInstance) {
        return call
                .transformDeferred(TimeLimiterOperator.of(timeLimiterRegistry.timeLimiter(DATABASE)))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreakerRegistry.circuitBreaker(DATABASE)))
                .transformDeferred(RetryOperator.of(retryRegistry.retry(retryInstance)))
                .onErrorMap(DatabaseResilience::isInfrastructureFailure,
                        error -> new TechnicalException(ErrorCode.DATABASE_UNAVAILABLE, error));
    }

    /** Any other error passes through: business errors keep their adapter mapping, unknown ones become a 500. */
    private static boolean isInfrastructureFailure(Throwable error) {
        return error instanceof TimeoutException
                || error instanceof CallNotPermittedException
                || error instanceof TransientDataAccessException
                || error instanceof DataAccessResourceFailureException;
    }
}
