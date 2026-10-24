package co.com.bancolombia.model.page;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageTest {

    @ParameterizedTest
    @ValueSource(ints = {1, Page.DEFAULT_SIZE, Page.MAX_SIZE})
    void acceptsSizeInRange(int size) {
        assertEquals(size, Page.builder().number(Page.DEFAULT_NUMBER).size(size).build().size());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, Integer.MAX_VALUE})
    void acceptsNonNegativeNumber(int number) {
        assertEquals(number, Page.builder().number(number).size(Page.DEFAULT_SIZE).build().number());
    }

    @Test
    void rejectsNegativeNumber() {
        assertInvalid(-1, Page.DEFAULT_SIZE);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, Page.MAX_SIZE + 1})
    void rejectsSizeOutOfRange(int size) {
        assertInvalid(Page.DEFAULT_NUMBER, size);
    }

    @Test
    void offsetSkipsTheElementsOfThePreviousPages() {
        assertEquals(40L, Page.builder().number(2).size(20).build().offset());
    }

    @Test
    void offsetOfTheLastPossiblePageDoesNotOverflow() {
        assertEquals((long) Integer.MAX_VALUE * Page.MAX_SIZE,
                Page.builder().number(Integer.MAX_VALUE).size(Page.MAX_SIZE).build().offset());
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "1, 1", "20, 1", "21, 2", "40, 2"})
    void totalPagesRoundsUp(long totalElements, int expected) {
        assertEquals(expected, Page.builder().number(0).size(20).build().totalPages(totalElements));
    }

    private static void assertInvalid(int number, int size) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> Page.builder().number(number).size(size).build());
        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
    }
}
