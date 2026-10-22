package co.com.bancolombia.model.product;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import lombok.Builder;

import java.util.UUID;

/**
 * A Product always holds a stripped name that is not blank and has at most {@value MAX_NAME_LENGTH} characters,
 * and a stock from 0 to {@value MAX_STOCK}.
 * Creating one with another name fails with a Business Exception coded INVALID_PRODUCT_NAME,
 * and with another stock, INVALID_STOCK.
 */
@Builder(toBuilder = true)
public record Product(UUID id, UUID branchId, String name, Long stock) {
    public static final int MAX_NAME_LENGTH = 100;
    public static final long MAX_STOCK = Integer.MAX_VALUE;

    public Product {
        name = validName(name);
        validateStock(stock);
    }

    private static String validName(String name) {
        String stripped = name == null ? "" : name.strip();
        if (stripped.isEmpty() || stripped.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_PRODUCT_NAME);
        }
        return stripped;
    }

    private static void validateStock(Long stock) {
        if (stock == null || stock < 0 || stock > MAX_STOCK) {
            throw new BusinessException(ErrorCode.INVALID_STOCK);
        }
    }
}
