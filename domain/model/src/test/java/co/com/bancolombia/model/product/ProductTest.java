package co.com.bancolombia.model.product;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductTest {

    @Test
    void stripsSurroundingSpacesFromTheName() {
        assertEquals("Cola", Product.builder().name("  Cola  ").stock(1L).build().name());
    }

    @Test
    void acceptsNameWithMaximumLength() {
        String name = "a".repeat(Product.MAX_NAME_LENGTH);

        assertEquals(name, Product.builder().name(name).stock(1L).build().name());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void rejectsNullAndBlankNames(String name) {
        assertInvalid(ErrorCode.INVALID_PRODUCT_NAME, name, 1L);
    }

    @Test
    void rejectsTooLongName() {
        assertInvalid(ErrorCode.INVALID_PRODUCT_NAME, "a".repeat(Product.MAX_NAME_LENGTH + 1), 1L);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, Product.MAX_STOCK})
    void acceptsStockInRange(long stock) {
        assertEquals(stock, Product.builder().name("Cola").stock(stock).build().stock());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {-1L, Product.MAX_STOCK + 1})
    void rejectsMissingAndOutOfRangeStock(Long stock) {
        assertInvalid(ErrorCode.INVALID_STOCK, "Cola", stock);
    }

    private static void assertInvalid(ErrorCode expected, String name, Long stock) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> Product.builder().name(name).stock(stock).build());
        assertEquals(expected, exception.getErrorCode());
    }
}
