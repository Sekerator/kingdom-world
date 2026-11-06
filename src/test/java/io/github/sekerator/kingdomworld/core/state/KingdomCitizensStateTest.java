package io.github.sekerator.kingdomworld.core.state;

import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KingdomCitizensStateTest {
    private static final String OVERWORLD = "minecraft:overworld";

    private KingdomWorldState state;
    private UUID king;
    private Kingdom kingdom;
    private Citizen citizen;

    @BeforeEach
    void setUp() {
        state = new KingdomWorldState();
        king = UUID.randomUUID();
        CreateKingdomResult result = state.found(UUID.randomUUID(), "Citizen Realm", 0x336699, king,
                new Capital(OVERWORLD, 8, 64, 8), 0L, KingdomRules.DEFAULT);
        kingdom = assertInstanceOf(CreateKingdomResult.Created.class, result).kingdom();
        citizen = new Citizen(UUID.randomUUID(), kingdom.id(), 5, Gender.MALE, 0, 0L, new WorldPos(OVERWORLD, 8, 64, 8));
        state.citizens().add(citizen);
    }

    @Test
    void professionsNeedTheRightToManageCitizens() {
        UUID steward = UUID.randomUUID();
        UUID subject = UUID.randomUUID();
        for (UUID player : new UUID[]{steward, subject}) {
            state.membership().invite(king, player, 1L);
            state.membership().acceptInvitation(player, kingdom.id(), 1L);
        }
        state.membership().setRole(king, steward, Role.STEWARD);

        assertEquals(Optional.of(KingdomError.NO_PERMISSION), state.setProfession(subject, citizen.id(), "farmer").failure());
        assertEquals(Optional.of(KingdomError.NO_PERMISSION), state.setProfession(UUID.randomUUID(), citizen.id(), "farmer").failure());
        assertTrue(state.setProfession(steward, citizen.id(), "farmer").ok());
        assertEquals(Professions.FARMER.id(), citizen.professionId());
        assertEquals(Optional.of(KingdomError.UNCHANGED), state.setProfession(king, citizen.id(), "farmer").failure());
        assertEquals(Optional.of(KingdomError.UNKNOWN_PROFESSION), state.setProfession(king, citizen.id(), "dragon_tamer").failure());
        assertEquals(Optional.of(KingdomError.UNKNOWN_CITIZEN), state.setProfession(king, UUID.randomUUID(), "farmer").failure());
        assertEquals(Outcome.DONE, state.setProfession(null, citizen.id(), "unemployed"));
    }

    @Test
    void givingUpLandFreesItsBeds() {
        long chunk = ChunkKey.of(1, 0);
        WorldPos bed = new WorldPos(OVERWORLD, 20, 64, 4);
        assertEquals(chunk, bed.chunk());
        state.citizens().addSite(bed, SiteKinds.BED, kingdom.id());
        assertTrue(state.citizens().assignHome(citizen, bed));
        assertTrue(state.unclaim(king, kingdom.id(), OVERWORLD, chunk).ok());
        assertNull(citizen.home());
        assertTrue(state.citizens().sites().get(bed).isEmpty());
    }

    @Test
    void dissolvingRemovesThePeople() {
        state.citizens().addSite(new WorldPos(OVERWORLD, 1, 64, 1), SiteKinds.BED, kingdom.id());
        state.dissolve(kingdom.id());
        assertTrue(state.citizens().get(citizen.id()).isEmpty());
        assertEquals(0, state.citizens().sites().size());
    }
}
