package co.com.bancolombia.r2dbc.product;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.product.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductRepositoryAdapterTest {

    private static final UUID BRANCH_ID = UUID.randomUUID();

    @Mock
    private ProductDataRepository repository;

    private ProductRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ProductRepositoryAdapter(repository);
    }

    @Test
    void insertsWithoutIdAndEmitsProductWithGeneratedId() {
        UUID id = UUID.randomUUID();
        when(repository.save(new ProductData(null, BRANCH_ID, "Cola", 5)))
                .thenReturn(Mono.just(new ProductData(id, BRANCH_ID, "Cola", 5)));

        StepVerifier.create(adapter.save(product()))
                .expectNextMatches(product -> id.equals(product.id()) && BRANCH_ID.equals(product.branchId())
                        && "Cola".equals(product.name()) && product.stock() == 5L)
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

    private static Product product() {
        return Product.builder().branchId(BRANCH_ID).name("Cola").stock(5L).build();
    }
}
