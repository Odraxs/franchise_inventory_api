package co.com.bancolombia.r2dbc.resilience;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.TechnicalException;
import co.com.bancolombia.model.exception.TechnicalException.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.publisher.PublisherProbe;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static co.com.bancolombia.r2dbc.resilience.DatabaseResilienceFixture.MINIMUM_NUMBER_OF_CALLS;
import static co.com.bancolombia.r2dbc.resilience.DatabaseResilienceFixture.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

class DatabaseResilienceTest {

    /** Longer than every backoff of the fast test config. */
    private static final Duration ALL_BACKOFFS = Duration.ofSeconds(1);
    private static final String ROW = "row";

    private DatabaseResilience resilience;

    @BeforeEach
    void setUp() {
        resilience = DatabaseResilienceFixture.fastDatabaseResilience();
    }

    @Test
    void nonIdempotentTurnsATimeoutIntoDatabaseUnavailableWithoutRetrying() {
        PublisherProbe<String> stalled = PublisherProbe.of(Mono.never());

        StepVerifier.withVirtualTime(() -> resilience.nonIdempotent(stalled.mono()))
                .expectSubscription()
                .thenAwait(TIMEOUT)
                .expectErrorMatches(error -> error instanceof TechnicalException exception
                        && exception.getErrorCode() == ErrorCode.DATABASE_UNAVAILABLE
                        && exception.getCause() instanceof TimeoutException)
                .verify();
        assertThat(stalled.subscribeCount()).isEqualTo(1);
    }

    @Test
    void nonIdempotentRetriesAConnectionErrorAndRecovers() {
        AtomicInteger subscriptions = new AtomicInteger();
        Iterator<Mono<String>> answers = List.of(
                Mono.<String>error(new TransientDataAccessResourceException("connection reset")),
                Mono.just(ROW)).iterator();
        Mono<String> call = Mono.defer(answers::next).doOnSubscribe(subscription -> subscriptions.incrementAndGet());

        StepVerifier.withVirtualTime(() -> resilience.nonIdempotent(call))
                .expectSubscription()
                .thenAwait(ALL_BACKOFFS)
                .expectNext(ROW)
                .verifyComplete();
        assertThat(subscriptions).hasValue(2);
    }

    @Test
    void nonIdempotentFailsFastWithDatabaseUnavailableOnceTheBreakerIsOpen() {
        Mono<Void> databaseDown = Flux.range(0, MINIMUM_NUMBER_OF_CALLS)
                .concatMap(call -> resilience.nonIdempotent(
                                Mono.error(new DataAccessResourceFailureException("connection refused")))
                        .onErrorComplete())
                .then();
        PublisherProbe<String> next = PublisherProbe.of(Mono.just(ROW));

        StepVerifier.create(databaseDown.then(resilience.nonIdempotent(next.mono())))
                .expectErrorMatches(error -> error instanceof TechnicalException exception
                        && exception.getErrorCode() == ErrorCode.DATABASE_UNAVAILABLE
                        && exception.getCause() instanceof CallNotPermittedException)
                .verify();
        next.assertWasNotSubscribed();
    }

    static Stream<RuntimeException> infrastructureErrorsTheRetryDoesNotRepeat() {
        return Stream.of(new DataAccessResourceFailureException("connection refused"),
                new QueryTimeoutException("statement timeout"));
    }

    @ParameterizedTest
    @MethodSource("infrastructureErrorsTheRetryDoesNotRepeat")
    void nonIdempotentTurnsAnInfrastructureErrorIntoDatabaseUnavailable(RuntimeException infrastructureError) {
        PublisherProbe<String> failing = PublisherProbe.of(Mono.error(infrastructureError));

        StepVerifier.create(resilience.nonIdempotent(failing.mono()))
                .expectErrorMatches(error -> error instanceof TechnicalException exception
                        && exception.getErrorCode() == ErrorCode.DATABASE_UNAVAILABLE
                        && exception.getCause() == infrastructureError)
                .verify();
        assertThat(failing.subscribeCount()).isEqualTo(1);
    }

    static Stream<RuntimeException> nonInfrastructureErrors() {
        return Stream.of(new DuplicateKeyException("uq_franchise_name"), new IllegalStateException("bug"));
    }

    @ParameterizedTest
    @MethodSource("nonInfrastructureErrors")
    void nonIdempotentPassesANonInfrastructureErrorThroughWithoutRetryingOrOpeningTheBreaker(
            RuntimeException nonInfrastructureError) {
        PublisherProbe<String> failing = PublisherProbe.of(Mono.error(nonInfrastructureError));
        PublisherProbe<String> next = PublisherProbe.of(Mono.just(ROW));
        Mono<Void> repeatedFailures = Flux.range(0, MINIMUM_NUMBER_OF_CALLS)
                .concatMap(call -> resilience.nonIdempotent(failing.mono())
                        .onErrorResume(error -> error == nonInfrastructureError, error -> Mono.empty()))
                .then();

        StepVerifier.create(repeatedFailures.then(resilience.nonIdempotent(next.mono())))
                .expectNext(ROW)
                .verifyComplete();
        assertThat(failing.subscribeCount()).isEqualTo(MINIMUM_NUMBER_OF_CALLS);
        next.assertWasSubscribed();
    }

    @Test
    void idempotentRetriesATimeoutAndRecovers() {
        AtomicInteger subscriptions = new AtomicInteger();
        Iterator<Mono<String>> answers = List.of(Mono.<String>never(), Mono.just(ROW)).iterator();
        Mono<String> call = Mono.defer(answers::next)
                .doOnSubscribe(subscription -> subscriptions.incrementAndGet());

        StepVerifier.withVirtualTime(() -> resilience.idempotent(call))
                .expectSubscription()
                .thenAwait(TIMEOUT)
                .thenAwait(ALL_BACKOFFS)
                .expectNext(ROW)
                .verifyComplete();
        assertThat(subscriptions).hasValue(2);
    }

    @Test
    void idempotentPassesABusinessErrorThroughWithoutRetrying() {
        PublisherProbe<String> failing = PublisherProbe.of(Mono.error(
                new BusinessException(BusinessException.ErrorCode.INVALID_FRANCHISE_NAME)));

        StepVerifier.create(resilience.idempotent(failing.mono()))
                .expectError(BusinessException.class)
                .verify();
        assertThat(failing.subscribeCount()).isEqualTo(1);
    }
}
