package io.github.sekerator.kingdomworld.core.kingdom;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KingdomTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RULER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Test
    void holdsItsFields() {
        Kingdom kingdom = new Kingdom(ID, "Valoria", 0x123456, RULER, 42L);
        assertEquals(ID, kingdom.id());
        assertEquals("Valoria", kingdom.name());
        assertEquals(0x123456, kingdom.color());
        assertEquals(Optional.of(RULER), kingdom.ruler());
        assertEquals(42L, kingdom.createdGameTime());
    }

    @Test
    void rulerIsOptional() {
        Kingdom kingdom = new Kingdom(ID, "Valoria", 0, null, 0L);
        assertEquals(Optional.empty(), kingdom.ruler());
        assertEquals(Optional.of(RULER), kingdom.withRuler(RULER).ruler());
        assertEquals(Optional.empty(), kingdom.withRuler(RULER).withRuler(null).ruler());
    }

    @Test
    void withColorKeepsIdentity() {
        Kingdom kingdom = new Kingdom(ID, "Valoria", 0, RULER, 7L).withColor(0xFFFFFF);
        assertEquals(0xFFFFFF, kingdom.color());
        assertEquals(ID, kingdom.id());
        assertEquals(7L, kingdom.createdGameTime());
    }

    @Test
    void rejectsInvalidState() {
        assertThrows(NullPointerException.class, () -> new Kingdom(null, "Valoria", 0, null, 0L));
        assertThrows(NullPointerException.class, () -> new Kingdom(ID, null, 0, null, 0L));
        assertThrows(IllegalArgumentException.class, () -> new Kingdom(ID, "  Valoria", 0, null, 0L));
        assertThrows(IllegalArgumentException.class, () -> new Kingdom(ID, "V", 0, null, 0L));
        assertThrows(IllegalArgumentException.class, () -> new Kingdom(ID, "Valoria", -1, null, 0L));
        assertThrows(IllegalArgumentException.class, () -> new Kingdom(ID, "Valoria", 0x1000000, null, 0L));
        assertThrows(IllegalArgumentException.class, () -> new Kingdom(ID, "Valoria", 0, null, -1L));
    }

    @Test
    void summaryExposesOnlyPublicData() {
        KingdomSummary summary = KingdomSummary.of(new Kingdom(ID, "Valoria", 0xABCDEF, RULER, 5L));
        assertEquals(new KingdomSummary(ID, "Valoria", 0xABCDEF, true), summary);
        assertEquals(false, KingdomSummary.of(new Kingdom(ID, "Valoria", 0, null, 5L)).hasRuler());
    }

    @Test
    void defaultColorsAreDeterministicAndValid() {
        UUID id = UUID.randomUUID();
        int color = KingdomColors.defaultFor(id);
        assertEquals(color, KingdomColors.defaultFor(id));
        assertEquals(true, color >= 0 && color <= Kingdom.MAX_COLOR);
        for (int i = 0; i < 100; i++) {
            assertEquals(true, KingdomColors.PALETTE.contains(KingdomColors.defaultFor(UUID.randomUUID())));
        }
    }
}
