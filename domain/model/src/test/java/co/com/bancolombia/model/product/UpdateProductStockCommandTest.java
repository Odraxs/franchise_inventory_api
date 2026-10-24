package co.com.bancolombia.model.product;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UpdateProductStockCommandTest {

    @Test
    void acceptsZeroStock() {
        assertEquals(0, UpdateProductStockCommand.builder().stock(0).build().stock());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, Integer.MIN_VALUE})
    void rejectsMissingOrNegativeStockWithInvalidStock(Integer stock) {
        UpdateProductStockCommand.UpdateProductStockCommandBuilder builder =
                UpdateProductStockCommand.builder().stock(stock);

        BusinessException error = assertThrows(BusinessException.class, builder::build);
        assertEquals(ErrorCode.INVALID_STOCK, error.getErrorCode());
    }
}
