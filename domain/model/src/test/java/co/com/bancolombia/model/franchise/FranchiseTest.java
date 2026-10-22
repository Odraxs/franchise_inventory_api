package co.com.bancolombia.model.franchise;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FranchiseTest {

    @Test
    void stripsSurroundingSpacesFromTheName() {
        assertEquals("Nequi", Franchise.builder().name("  Nequi  ").build().name());
    }

    @Test
    void acceptsNameWithMaximumLength() {
        String name = "a".repeat(Franchise.MAX_NAME_LENGTH);

        assertEquals(name, Franchise.builder().name(name).build().name());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void rejectsNullAndBlankNames(String name) {
        assertInvalidName(name);
    }

    @Test
    void rejectsTooLongName() {
        assertInvalidName("a".repeat(Franchise.MAX_NAME_LENGTH + 1));
    }

    @Test
    void measuresTheLengthAfterStripping() {
        assertEquals("a".repeat(Franchise.MAX_NAME_LENGTH),
                Franchise.builder().name(" " + "a".repeat(Franchise.MAX_NAME_LENGTH) + " ").build().name());
    }

    private static void assertInvalidName(String name) {
        BusinessException exception = assertThrows(BusinessException.class, () -> Franchise.builder().name(name).build());
        assertEquals(ErrorCode.INVALID_FRANCHISE_NAME, exception.getErrorCode());
    }
}
