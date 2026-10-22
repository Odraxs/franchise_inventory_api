package co.com.bancolombia.model.branch;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BranchTest {

    @Test
    void stripsSurroundingSpacesFromTheName() {
        assertEquals("Centro", Branch.builder().name("  Centro  ").build().name());
    }

    @Test
    void acceptsNameWithMaximumLength() {
        String name = "a".repeat(Branch.MAX_NAME_LENGTH);

        assertEquals(name, Branch.builder().name(name).build().name());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void rejectsNullAndBlankNames(String name) {
        assertInvalidName(name);
    }

    @Test
    void rejectsTooLongName() {
        assertInvalidName("a".repeat(Branch.MAX_NAME_LENGTH + 1));
    }

    @Test
    void measuresTheLengthAfterStripping() {
        assertEquals("a".repeat(Branch.MAX_NAME_LENGTH),
                Branch.builder().name(" " + "a".repeat(Branch.MAX_NAME_LENGTH) + " ").build().name());
    }

    private static void assertInvalidName(String name) {
        BusinessException exception = assertThrows(BusinessException.class, () -> Branch.builder().name(name).build());
        assertEquals(ErrorCode.INVALID_BRANCH_NAME, exception.getErrorCode());
    }
}
