package io.github.sekerator.kingdomworld.core.kingdom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KingdomRegistryTest {
    private KingdomRegistry registry;
    private AtomicInteger changes;

    @BeforeEach
    void setUp() {
        registry = new KingdomRegistry();
        changes = new AtomicInteger();
        registry.setChangeListener(changes::incrementAndGet);
    }

    private Kingdom create(String name) {
        CreateKingdomResult result = registry.create(UUID.randomUUID(), name, 0x336699, null, 100L);
        return assertInstanceOf(CreateKingdomResult.Created.class, result).kingdom();
    }

    private CreateKingdomResult.Error rejection(CreateKingdomResult result) {
        return assertInstanceOf(CreateKingdomResult.Rejected.class, result).error();
    }

    @Test
    void createStoresNormalizedKingdom() {
        Kingdom kingdom = create("  Old   Valoria ");
        assertEquals("Old Valoria", kingdom.name());
        assertEquals(Optional.of(kingdom), registry.get(kingdom.id()));
        assertEquals(1, registry.size());
        assertEquals(1, changes.get());
    }

    @Test
    void findByNameIgnoresCaseAndSpacing() {
        Kingdom kingdom = create("Северный Предел");
        assertEquals(Optional.of(kingdom), registry.findByName("северный   предел"));
        assertEquals(Optional.empty(), registry.findByName("Южный Предел"));
    }

    @Test
    void createRejectsDuplicateNames() {
        create("Valoria");
        assertEquals(CreateKingdomResult.Error.NAME_TAKEN,
                rejection(registry.create(UUID.randomUUID(), "VALORIA", 0, null, 0L)));
        assertEquals(1, registry.size());
        assertEquals(1, changes.get());
    }

    @Test
    void createRejectsDuplicateIds() {
        Kingdom kingdom = create("Valoria");
        assertEquals(CreateKingdomResult.Error.ID_TAKEN,
                rejection(registry.create(kingdom.id(), "Other Realm", 0, null, 0L)));
    }

    @Test
    void createRejectsInvalidNames() {
        UUID id = UUID.randomUUID();
        assertEquals(CreateKingdomResult.Error.NAME_EMPTY, rejection(registry.create(id, " ", 0, null, 0L)));
        assertEquals(CreateKingdomResult.Error.NAME_TOO_SHORT, rejection(registry.create(id, "Ab", 0, null, 0L)));
        assertEquals(CreateKingdomResult.Error.NAME_TOO_LONG, rejection(registry.create(id, "x".repeat(40), 0, null, 0L)));
        assertEquals(CreateKingdomResult.Error.NAME_INVALID_CHARACTERS, rejection(registry.create(id, "Bad!Name", 0, null, 0L)));
        assertTrue(registry.isEmpty());
        assertEquals(0, changes.get());
    }

    @Test
    void addRejectsDuplicates() {
        Kingdom kingdom = new Kingdom(UUID.randomUUID(), "Valoria", 0, null, 0L);
        assertEquals(KingdomRegistry.AddResult.ADDED, registry.add(kingdom));
        assertEquals(KingdomRegistry.AddResult.DUPLICATE_ID, registry.add(kingdom.withColor(1)));
        assertEquals(KingdomRegistry.AddResult.DUPLICATE_NAME,
                registry.add(new Kingdom(UUID.randomUUID(), "valoria", 0, null, 0L)));
        assertEquals(1, registry.size());
    }

    @Test
    void removeFreesTheName() {
        Kingdom kingdom = create("Valoria");
        assertEquals(Optional.of(kingdom), registry.remove(kingdom.id()));
        assertFalse(registry.isNameTaken("Valoria"));
        assertEquals(Optional.empty(), registry.get(kingdom.id()));
        assertEquals(2, changes.get());
        create("Valoria");
    }

    @Test
    void removingUnknownIdChangesNothing() {
        create("Valoria");
        assertEquals(Optional.empty(), registry.remove(UUID.randomUUID()));
        assertEquals(1, registry.size());
        assertEquals(1, changes.get());
    }

    @Test
    void allKeepsInsertionOrderAndIsASnapshot() {
        Kingdom first = create("First Realm");
        Kingdom second = create("Second Realm");
        Kingdom third = create("Third Realm");
        List<Kingdom> all = registry.all();
        assertEquals(List.of(first, second, third), all);
        registry.remove(second.id());
        assertEquals(3, all.size());
        assertThrows(UnsupportedOperationException.class, () -> all.add(first));
    }
}
