package io.github.sekerator.kingdomworld.common.block;

import io.github.sekerator.kingdomworld.core.citizen.Profession;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The looks of a work post: the profession of the post by its name, none without one and a sheet of paper for a profession
 * of another mod; the names never change, because worlds and resource packs keep them.
 */
class WorkPostLookTest {

    @Test
    void aPostWithoutAProfessionShowsNothing() {
        assertEquals(WorkPostLook.NONE, WorkPostLook.of(null));
        assertEquals(WorkPostLook.NONE, WorkPostLook.of(Professions.UNEMPLOYED.id()));
    }

    @Test
    void everyProfessionOfAPostHasItsOwnLook() {
        List<String> ids = new java.util.ArrayList<>(Professions.postProfessions().stream().map(Profession::id).toList());
        ids.add("innkeeper");
        assertTrue(ids.size() >= 16, "professions of posts: " + ids);
        for (String id : ids) {
            WorkPostLook look = WorkPostLook.of(id);
            assertEquals(id, look.getSerializedName(), "the look of " + id);
        }
    }

    @Test
    void otherProfessionsShowASheetOfPaper() {
        for (String id : List.of(Professions.ADVENTURER.id(), Professions.WARRIOR.id(), Professions.ARCHER.id(), "somemod:alchemist",
                "baker", "none", "other", "")) {
            assertEquals(WorkPostLook.OTHER, WorkPostLook.of(id), id);
        }
    }

    @Test
    void namesAreUniqueAndFitABlockState() {
        Set<String> names = new HashSet<>();
        for (WorkPostLook look : WorkPostLook.values()) {
            assertTrue(look.getSerializedName().matches("[a-z0-9_]+"), look.getSerializedName());
            assertTrue(names.add(look.getSerializedName()), "twice: " + look.getSerializedName());
        }
    }

    @Test
    void namesAreNeverRenamedOrRemoved() {
        // Worlds keep the names in their chunks and resource packs in their files. A new look is added here too, with its model
        // and a new network protocol.
        List<String> frozen = List.of("none", "farmer", "lumberjack", "miner", "fisher", "herder", "carrier", "smelter", "cook",
                "crafter", "smith", "minter", "merchant", "registrar", "builder", "mage", "innkeeper", "other");
        List<String> names = Arrays.stream(WorkPostLook.values()).map(WorkPostLook::getSerializedName).toList();
        assertEquals(frozen, names);
    }
}
