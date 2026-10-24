package co.com.bancolombia.model.page;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import lombok.Builder;

/**
 * One page of a list: a 0-based number and a size from 1 to {@value MAX_SIZE}.
 * Creating one with a negative number or a size out of range fails with a Business Exception coded INVALID_REQUEST.
 */
@Builder(toBuilder = true)
public record Page(int number, int size) {
    public static final int DEFAULT_NUMBER = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public Page {
        if (number < 0 || size < 1 || size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    /** The number of elements before this page; a long, so a large page number does not overflow. */
    public long offset() {
        return (long) number * size;
    }

    /** The number of pages that hold this many elements; zero elements make zero pages. */
    public int totalPages(long totalElements) {
        return (int) ((totalElements + size - 1) / size);
    }
}
