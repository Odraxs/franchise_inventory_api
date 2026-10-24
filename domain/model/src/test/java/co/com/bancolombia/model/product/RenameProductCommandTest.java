package co.com.bancolombia.model.product;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RenameProductCommandTest {

    @Test
    void holdsTheStrippedName() {
        assertEquals("Cola", RenameProductCommand.builder().name("  Cola  ").build().name());
    }

    @Test
    void acceptsNameWithMaximumLength() {
        String name = "a".repeat(Product.MAX_NAME_LENGTH);

        assertEquals(name, RenameProductCommand.builder().name(name).build().name());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void rejectsMissingOrBlankNamesWithInvalidProductName(String name) {
        assertInvalidName(name);
    }

    @Test
    void rejectsTooLongNamesWithInvalidProductName() {
        assertInvalidName("a".repeat(Product.MAX_NAME_LENGTH + 1));
    }

    private static void assertInvalidName(String name) {
        RenameProductCommand.RenameProductCommandBuilder builder = RenameProductCommand.builder().name(name);

        BusinessException error = assertThrows(BusinessException.class, builder::build);
        assertEquals(ErrorCode.INVALID_PRODUCT_NAME, error.getErrorCode());
    }
}
