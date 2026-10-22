package co.com.bancolombia.model.franchise;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import lombok.Builder;

import java.util.UUID;

/**
 * A Franchise always holds a stripped name that is not blank and has at most {@value MAX_NAME_LENGTH} characters.
 * Creating one with another name fails with a Business Exception coded INVALID_FRANCHISE_NAME.
 */
@Builder(toBuilder = true)
public record Franchise(UUID id, String name) {
    public static final int MAX_NAME_LENGTH = 100;

    public Franchise {
        name = validName(name);
    }

    private static String validName(String name) {
        String stripped = name == null ? "" : name.strip();
        if (stripped.isEmpty() || stripped.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_FRANCHISE_NAME);
        }
        return stripped;
    }
}
