package co.com.bancolombia.r2dbc.product;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.exception.TechnicalException;
import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.r2dbc.resilience.DatabaseResilienceFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.publisher.PublisherProbe;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductRepositoryAdapterTest {

    /** Longer than every backoff of the fast test config. */
    private static final Duration ALL_BACKOFFS = Duration.ofSeconds(1);
    private static final UUID BRANCH_ID = UUID.randomUUID();

    @Mock
    private ProductDataRepository repository;

    private ProductRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ProductRepositoryAdapter(repository, DatabaseResilienceFixture.fastDatabaseResilience());
    }

    @Test
    void insertsWithoutIdAndEmitsProductWithGeneratedId() {
        UUID id = UUID.randomUUID();
        when(repository.save(new ProductData(null, BRANCH_ID, "Cola", 5)))
                .thenReturn(Mono.just(new ProductData(id, BRANCH_ID, "Cola", 5)));

        StepVerifier.create(adapter.save(product()))
                .expectNextMatches(product -> id.equals(product.id()) && BRANCH_ID.equals(product.branchId())
                        && "Cola".equals(product.name()) && product.stock() == 5)
                .verifyComplete();
    }

    @Test
    void mapsDuplicateKeyToDuplicateProductName() {
        when(repository.save(new ProductData(null, BRANCH_ID, "Cola", 5)))
                .thenReturn(Mono.error(new DuplicateKeyException("uq_product_branch_name")));

        StepVerifier.create(adapter.save(product()))
                .expectErrorMatches(error -> error instanceof BusinessException exception
                        && exception.getErrorCode() == ErrorCode.DUPLICATE_PRODUCT_NAME)
                .verify();
    }

    @Test
    void propagatesOtherErrorsUnchanged() {
        when(repository.save(new ProductData(null, BRANCH_ID, "Cola", 5)))
                .thenReturn(Mono.error(new DataIntegrityViolationException("ck_product_stock_non_negative")));

        StepVerifier.create(adapter.save(product()))
                .expectError(DataIntegrityViolationException.class)
                .verify();
    }

    @Test
    void deleteEmitsTrueWhenTheActiveProductWasMarked() {
        UUID id = UUID.randomUUID();
        when(repository.softDelete(id, BRANCH_ID)).thenReturn(Mono.just(1));

        StepVerifier.create(adapter.delete(id, BRANCH_ID)).expectNext(true).verifyComplete();
    }

    @Test
    void deleteEmitsFalseWhenNoActiveProductMatches() {
        UUID id = UUID.randomUUID();
        when(repository.softDelete(id, BRANCH_ID)).thenReturn(Mono.just(0));

        StepVerifier.create(adapter.delete(id, BRANCH_ID)).expectNext(false).verifyComplete();
    }

    @Test
    void deleteIsRetriedAfterATimeoutAndRecovers() {
        UUID id = UUID.randomUUID();
        AtomicInteger subscriptions = new AtomicInteger();
        Iterator<Mono<Integer>> answers = List.of(Mono.<Integer>never(), Mono.just(1)).iterator();
        when(repository.softDelete(id, BRANCH_ID)).thenReturn(Mono.defer(answers::next)
                .doOnSubscribe(subscription -> subscriptions.incrementAndGet()));

        StepVerifier.withVirtualTime(() -> adapter.delete(id, BRANCH_ID))
                .expectSubscription()
                .thenAwait(DatabaseResilienceFixture.TIMEOUT)
                .thenAwait(ALL_BACKOFFS)
                .expectNext(true)
                .verifyComplete();
        assertThat(subscriptions).hasValue(2);
    }

    @Test
    void stalledDeleteFailsWithDatabaseUnavailable() {
        UUID id = UUID.randomUUID();
        PublisherProbe<Integer> stalled = PublisherProbe.of(Mono.never());
        when(repository.softDelete(id, BRANCH_ID)).thenReturn(stalled.mono());

        StepVerifier.withVirtualTime(() -> adapter.delete(id, BRANCH_ID))
                .expectSubscription()
                .thenAwait(DatabaseResilienceFixture.TIMEOUT.multipliedBy(DatabaseResilienceFixture.MAX_ATTEMPTS)
                        .plus(ALL_BACKOFFS))
                .expectErrorMatches(error -> error instanceof TechnicalException exception
                        && exception.getErrorCode() == TechnicalException.ErrorCode.DATABASE_UNAVAILABLE)
                .verify();
        // The retries count in the breaker, so it can open before the last attempt
        assertThat(stalled.subscribeCount()).isBetween(2L, (long) DatabaseResilienceFixture.MAX_ATTEMPTS);
    }

    @Test
    void stalledSaveFailsWithDatabaseUnavailable() {
        when(repository.save(new ProductData(null, BRANCH_ID, "Cola", 5))).thenReturn(Mono.never());

        StepVerifier.withVirtualTime(() -> adapter.save(product()))
                .expectSubscription()
                .thenAwait(DatabaseResilienceFixture.TIMEOUT)
                .expectErrorMatches(error -> error instanceof TechnicalException exception
                        && exception.getErrorCode() == TechnicalException.ErrorCode.DATABASE_UNAVAILABLE)
                .verify();
    }

    @Test
    void duplicateNameIsTriedOnceAndMapsToDuplicateProductName() {
        PublisherProbe<ProductData> duplicate = PublisherProbe.of(Mono.error(new DuplicateKeyException("uq_product_branch_name")));
        when(repository.save(new ProductData(null, BRANCH_ID, "Cola", 5))).thenReturn(duplicate.mono());

        StepVerifier.create(adapter.save(product()))
                .expectErrorMatches(error -> error instanceof BusinessException exception
                        && exception.getErrorCode() == ErrorCode.DUPLICATE_PRODUCT_NAME)
                .verify();
        assertThat(duplicate.subscribeCount()).isEqualTo(1);
    }

    @Test
    void updatesStockAndEmitsTheUpdatedProduct() {
        UUID id = UUID.randomUUID();
        when(repository.updateStock(id, BRANCH_ID, 0))
                .thenReturn(Mono.just(new ProductData(id, BRANCH_ID, "Cola", 0)));

        StepVerifier.create(adapter.updateStock(id, BRANCH_ID, 0))
                .expectNextMatches(product -> id.equals(product.id()) && BRANCH_ID.equals(product.branchId())
                        && "Cola".equals(product.name()) && product.stock() == 0)
                .verifyComplete();
    }

    @Test
    void updateStockEmitsNothingWhenNoActiveProductMatches() {
        UUID id = UUID.randomUUID();
        when(repository.updateStock(id, BRANCH_ID, 3)).thenReturn(Mono.empty());

        StepVerifier.create(adapter.updateStock(id, BRANCH_ID, 3))
                .verifyComplete();
    }

    private static Product product() {
        return Product.builder().branchId(BRANCH_ID).name("Cola").stock(5).build();
    }
}
