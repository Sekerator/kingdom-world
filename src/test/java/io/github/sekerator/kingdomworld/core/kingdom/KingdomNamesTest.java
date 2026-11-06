package io.github.sekerator.kingdomworld.core.kingdom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KingdomNamesTest {

    @Test
    void normalizeTrimsAndCollapsesWhitespace() {
        assertEquals("Old Valoria", KingdomNames.normalize("  Old \t  Valoria \n"));
    }

    @Test
    void keyIgnoresCaseAndSpacing() {
        assertEquals(KingdomNames.key("Old Valoria"), KingdomNames.key("  old   VALORIA "));
        assertEquals(KingdomNames.key("Северный Предел"), KingdomNames.key("северный предел"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Valoria", "Северный Предел", "Kar-Duum", "O'Neill Hold", "Realm_42", "123"})
    void acceptsReasonableNames(String name) {
        assertEquals(Optional.empty(), KingdomNames.validate(name));
        assertTrue(KingdomNames.isValid(name));
    }

    @Test
    void rejectsEmptyAndBlank() {
        assertEquals(Optional.of(NameProblem.EMPTY), KingdomNames.validate(""));
        assertEquals(Optional.of(NameProblem.EMPTY), KingdomNames.validate("   "));
    }

    @Test
    void enforcesLengthLimits() {
        assertEquals(Optional.of(NameProblem.TOO_SHORT), KingdomNames.validate("Ab"));
        assertEquals(Optional.empty(), KingdomNames.validate("Abc"));
        assertEquals(Optional.empty(), KingdomNames.validate("a".repeat(KingdomNames.MAX_LENGTH)));
        assertEquals(Optional.of(NameProblem.TOO_LONG), KingdomNames.validate("a".repeat(KingdomNames.MAX_LENGTH + 1)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-Valoria", "Valoria!", "Val§oria", "Valoria\u0000", "<b>Valoria</b>", "Valoria.exe"})
    void rejectsInvalidCharacters(String name) {
        assertEquals(Optional.of(NameProblem.INVALID_CHARACTERS), KingdomNames.validate(name));
    }
}
