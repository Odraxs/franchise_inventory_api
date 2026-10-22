package co.com.bancolombia.model.branch;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import lombok.Builder;

import java.util.UUID;

/**
 * A Branch always holds a stripped name that is not blank and has at most {@value MAX_NAME_LENGTH} characters.
 * Creating one with another name fails with a Business Exception coded INVALID_BRANCH_NAME.
 */
@Builder(toBuilder = true)
public record Branch(UUID id, UUID franchiseId, String name) {
    public static final int MAX_NAME_LENGTH = 100;

    public Branch {
        name = validName(name);
    }

    private static String validName(String name) {
        String stripped = name == null ? "" : name.strip();
        if (stripped.isEmpty() || stripped.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_BRANCH_NAME);
        }
        return stripped;
    }
}
