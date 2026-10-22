package co.com.bancolombia.r2dbc.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/** A real DatabaseResilience with the production exception lists, a small window and short durations. */
public final class DatabaseResilienceFixture {

    public static final Duration TIMEOUT = Duration.ofMillis(100);
    public static final int MAX_ATTEMPTS = 3;
    public static final int MINIMUM_NUMBER_OF_CALLS = 2;
    private static final int SLIDING_WINDOW_SIZE = 4;
    private static final float FAILURE_RATE_THRESHOLD = 50;
    private static final Duration WAIT_DURATION_IN_OPEN_STATE = Duration.ofSeconds(10);
    private static final Duration RETRY_WAIT_DURATION = Duration.ofMillis(10);
    private static final double BACKOFF_MULTIPLIER = 2;
    private static final double RANDOMIZED_WAIT_FACTOR = 0.5;

    private DatabaseResilienceFixture() {
    }

    /** Fresh registries on every call, so no breaker state leaks between tests. */
    @SuppressWarnings("unchecked")
    public static DatabaseResilience fastDatabaseResilience() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(SLIDING_WINDOW_SIZE)
                .minimumNumberOfCalls(MINIMUM_NUMBER_OF_CALLS)
                .failureRateThreshold(FAILURE_RATE_THRESHOLD)
                .waitDurationInOpenState(WAIT_DURATION_IN_OPEN_STATE)
                .recordExceptions(TimeoutException.class, TransientDataAccessException.class,
                        DataAccessResourceFailureException.class)
                .build();
        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom().timeoutDuration(TIMEOUT).build();
        RetryConfig nonIdempotentRetry = RetryConfig.custom()
                .maxAttempts(MAX_ATTEMPTS)
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(RETRY_WAIT_DURATION,
                        BACKOFF_MULTIPLIER, RANDOMIZED_WAIT_FACTOR))
                .retryExceptions(TransientDataAccessResourceException.class)
                .build();
        RetryConfig idempotentRetry = RetryConfig.custom()
                .maxAttempts(MAX_ATTEMPTS)
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(RETRY_WAIT_DURATION,
                        BACKOFF_MULTIPLIER, RANDOMIZED_WAIT_FACTOR))
                .retryExceptions(TimeoutException.class, TransientDataAccessException.class)
                .build();
        // Spring's auto-config creates every retry instance eagerly; retry(name) alone would use the default config
        RetryRegistry retryRegistry = RetryRegistry.ofDefaults();
        retryRegistry.retry(DatabaseResilience.DATABASE_NON_IDEMPOTENT, nonIdempotentRetry);
        retryRegistry.retry(DatabaseResilience.DATABASE_IDEMPOTENT, idempotentRetry);
        return new DatabaseResilience(CircuitBreakerRegistry.of(circuitBreakerConfig),
                TimeLimiterRegistry.of(timeLimiterConfig), retryRegistry);
    }
}
