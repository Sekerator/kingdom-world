package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Entry;
import io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Kind;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateCatalogTest {
    @Test
    void everyTemplateHasAUniqueIdAndRoomForAFloor() {
        Set<String> ids = new HashSet<>();
        for (Entry entry : TemplateCatalog.all()) {
            assertTrue(ids.add(entry.id()), "twice: " + entry.id());
            assertTrue(entry.id().startsWith("kingdomworld:"), entry.id());
            assertTrue(entry.width() >= 5 && entry.depth() >= 5, entry.toString());
            assertTrue(entry.beds() >= 0, entry.toString());
            assertEquals(entry, TemplateCatalog.of(entry.id()));
        }
        assertFalse(ids.isEmpty());
    }

    @Test
    void plansChooseAmongEnoughVariantsAndNeverAnUpgrade() {
        for (Kind kind : Kind.values()) {
            for (Entry entry : TemplateCatalog.variants(kind)) {
                assertFalse(entry.upgrade(), entry.id());
                assertEquals(kind, entry.kind());
            }
        }
        assertTrue(TemplateCatalog.variants(Kind.SMALL_HOUSE).size() >= 4);
        assertTrue(TemplateCatalog.variants(Kind.LARGE_HOUSE).size() >= 4);
        assertTrue(TemplateCatalog.variants(Kind.WORKSHOP).size() >= 3);
        assertTrue(TemplateCatalog.variants(Kind.STOREHOUSE).size() >= 2);
        assertTrue(TemplateCatalog.variants(Kind.TOWER).size() >= 2);
        assertTrue(TemplateCatalog.variants(Kind.TOWER).size() + TemplateCatalog.variants(Kind.CORNER_TOWER).size() >= 3);
        assertTrue(TemplateCatalog.variants(Kind.WELL).size() >= 2);
        assertTrue(TemplateCatalog.variants(Kind.TAVERN).size() >= 2);
        assertTrue(TemplateCatalog.variants(Kind.MARKET).size() >= 2);
        assertTrue(TemplateCatalog.variants(Kind.BARRACKS).size() + TemplateCatalog.variants(Kind.BARRACKS_HALL).size() >= 2);
        // Upgrades are in the table, but only for rulers who improve a building.
        assertTrue(TemplateCatalog.of("kingdomworld:small_house_2").upgrade());
    }

    @Test
    void anUnknownTemplateIsAnError() {
        assertThrows(IllegalArgumentException.class, () -> NpcComposition.beds("kingdomworld:no_such_building"));
        assertThrows(IllegalArgumentException.class, () -> TemplateCatalog.of("minecraft:village"));
        assertTrue(TemplateCatalog.get("kingdomworld:no_such_building").isEmpty());
        assertFalse(NpcComposition.bunks("kingdomworld:no_such_building"));
        assertEquals(12, NpcComposition.beds("kingdomworld:barracks_hall"));
        assertTrue(NpcComposition.bunks("kingdomworld:barracks"));
        assertFalse(NpcComposition.bunks("kingdomworld:large_house"));
        assertEquals("warehouse", TemplateCatalog.of("kingdomworld:storage_yard").role());
    }
}
