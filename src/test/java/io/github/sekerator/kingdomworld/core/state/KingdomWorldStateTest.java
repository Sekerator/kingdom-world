package io.github.sekerator.kingdomworld.core.state;

import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Member;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.membership.RolePermissions;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.ChunkLimitPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KingdomWorldStateTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final KingdomRules RULES = KingdomRules.DEFAULT;

    private KingdomWorldState state;
    private AtomicInteger changes;
    private UUID founder;

    @BeforeEach
    void setUp() {
        state = new KingdomWorldState();
        changes = new AtomicInteger();
        state.setChangeListener(changes::incrementAndGet);
        founder = UUID.randomUUID();
    }

    private Kingdom found(String name, UUID ruler, int blockX, int blockZ) {
        CreateKingdomResult result = state.found(UUID.randomUUID(), name, 0x336699, ruler,
                new Capital(OVERWORLD, blockX, 64, blockZ), 100L, RULES);
        return assertInstanceOf(CreateKingdomResult.Created.class, result).kingdom();
    }

    private CreateKingdomResult.Error refusal(CreateKingdomResult result) {
        return assertInstanceOf(CreateKingdomResult.Rejected.class, result).error();
    }

    @Test
    void foundingCreatesKingdomKingAndLand() {
        Kingdom kingdom = found("Valoria", founder, 8, 8);
        assertEquals(Optional.of(kingdom), state.kingdomOf(founder));
        assertEquals(Role.KING, state.membership().memberOf(founder).orElseThrow().role());
        assertEquals(9, state.territory().territory(kingdom.id()).orElseThrow().size());
        assertEquals(Optional.of(kingdom), state.kingdomAt(OVERWORLD, ChunkKey.of(1, -1)));
        assertEquals(Optional.of(kingdom), state.kingdomWithThroneAt(OVERWORLD, 8, 64, 8));
        assertTrue(state.kingdomWithThroneAt(OVERWORLD, 8, 65, 8).isEmpty());
        assertTrue(changes.get() > 0);
    }

    @Test
    void foundingChecksFounderDimensionAndDistance() {
        found("Valoria", founder, 8, 8);
        UUID other = UUID.randomUUID();
        assertEquals(CreateKingdomResult.Error.ALREADY_IN_KINGDOM, refusal(state.found(UUID.randomUUID(), "Second Realm",
                0, founder, new Capital(OVERWORLD, 1000, 64, 1000), 0L, RULES)));
        assertEquals(CreateKingdomResult.Error.DIMENSION_NOT_ALLOWED, refusal(state.found(UUID.randomUUID(), "Nether Realm",
                0, other, new Capital("minecraft:the_nether", 1000, 64, 1000), 0L, RULES)));
        assertEquals(CreateKingdomResult.Error.INSIDE_OTHER_KINGDOM, refusal(state.found(UUID.randomUUID(), "Inner Realm",
                0, other, new Capital(OVERWORLD, 20, 64, 20), 0L, RULES)));
        // Valoria's land reaches chunk x=1. A throne at chunk x=5 is 4 chunks away: too close with the default 4.
        assertEquals(CreateKingdomResult.Error.TOO_CLOSE_TO_OTHER_KINGDOM, refusal(state.found(UUID.randomUUID(), "Near Realm",
                0, other, new Capital(OVERWORLD, 5 * 16, 64, 0), 0L, RULES)));
        Kingdom far = found("Far Realm", other, 6 * 16, 0);
        // Five chunks away the full 3x3 square fits.
        assertEquals(9, state.territory().territory(far.id()).orElseThrow().size());
        assertEquals(CreateKingdomResult.Error.NAME_TAKEN, refusal(state.found(UUID.randomUUID(), "valoria",
                0, UUID.randomUUID(), new Capital(OVERWORLD, 10_000, 64, 0), 0L, RULES)));
        assertEquals(2, state.kingdoms().size());
    }

    @Test
    void kingdomWithoutCapitalHasNoLand() {
        CreateKingdomResult result = state.found(UUID.randomUUID(), "Landless", 0, null, null, 0L, RULES);
        Kingdom kingdom = assertInstanceOf(CreateKingdomResult.Created.class, result).kingdom();
        assertTrue(state.territory().territory(kingdom.id()).isEmpty());
        assertEquals(Optional.of(KingdomError.NO_CAPITAL), state.claim(null, kingdom.id(), OVERWORLD, 0L, RULES).failure());
    }

    @Test
    void claimingNeedsTheTerritoryRightAndRespectsTheLimit() {
        Kingdom kingdom = found("Valoria", founder, 8, 8);
        UUID subject = UUID.randomUUID();
        state.membership().apply(subject, kingdom.id(), 0L);
        state.membership().acceptApplication(founder, subject, 0L);

        assertEquals(Optional.of(KingdomError.NO_PERMISSION),
                state.claim(subject, kingdom.id(), OVERWORLD, ChunkKey.of(2, 0), RULES).failure());
        assertEquals(Optional.of(KingdomError.NO_PERMISSION),
                state.claim(UUID.randomUUID(), kingdom.id(), OVERWORLD, ChunkKey.of(2, 0), RULES).failure());
        assertEquals(Outcome.DONE, state.claim(founder, kingdom.id(), OVERWORLD, ChunkKey.of(2, 0), RULES));

        // One subject: 25 + 4 = 29 chunks.
        assertEquals(29, state.chunkLimit(kingdom.id(), RULES));
        KingdomRules tight = RULES.withChunkLimit(ChunkLimitPolicy.perSubject(10, 0));
        assertEquals(Optional.of(KingdomError.LIMIT_REACHED),
                state.claim(founder, kingdom.id(), OVERWORLD, ChunkKey.of(3, 0), tight).failure());
        // Administrators ignore the limit.
        assertEquals(Outcome.DONE, state.claim(null, kingdom.id(), OVERWORLD, ChunkKey.of(3, 0), tight));

        assertEquals(Outcome.DONE, state.unclaim(founder, kingdom.id(), OVERWORLD, ChunkKey.of(3, 0)));
        assertEquals(Optional.of(KingdomError.NO_PERMISSION),
                state.unclaim(subject, kingdom.id(), OVERWORLD, ChunkKey.of(2, 0)).failure());
        assertEquals(Optional.of(KingdomError.CAPITAL_CHUNK),
                state.unclaim(founder, kingdom.id(), OVERWORLD, ChunkKey.of(0, 0)).failure());
        assertEquals(Optional.of(KingdomError.UNKNOWN_KINGDOM),
                state.claim(founder, UUID.randomUUID(), OVERWORLD, ChunkKey.of(2, 0), RULES).failure());
    }

    @Test
    void dissolvingFreesEverything() {
        Kingdom kingdom = found("Valoria", founder, 8, 8);
        UUID invited = UUID.randomUUID();
        state.membership().invite(founder, invited, 0L);
        assertEquals(Optional.of(kingdom), state.dissolve(kingdom.id()));
        assertTrue(state.kingdoms().isEmpty());
        assertTrue(state.kingdomOf(founder).isEmpty());
        assertTrue(state.membership().invitationsOf(invited).isEmpty());
        assertNull(state.territory().ownerOf(OVERWORLD, ChunkKey.of(0, 0)));
        assertTrue(state.dissolve(kingdom.id()).isEmpty());
        // The land is free for a new kingdom of the same founder.
        found("Valoria Reborn", founder, 8, 8);
    }

    @Test
    void abdicationAndCrowningUpdateTheRuler() {
        Kingdom kingdom = found("Valoria", founder, 8, 8);
        UUID heir = UUID.randomUUID();
        state.membership().invite(founder, heir, 0L);
        state.membership().acceptInvitation(heir, kingdom.id(), 0L);

        assertEquals(Outcome.DONE, state.abdicate(founder, heir));
        assertEquals(heir, state.kingdoms().get(kingdom.id()).orElseThrow().rulerId());
        assertEquals(Role.SUBJECT, state.membership().memberOf(founder).orElseThrow().role());

        UUID outsider = UUID.randomUUID();
        assertEquals(Outcome.DONE, state.crown(kingdom.id(), outsider, 5L));
        assertEquals(outsider, state.kingdoms().get(kingdom.id()).orElseThrow().rulerId());
        assertEquals(Role.SUBJECT, state.membership().memberOf(heir).orElseThrow().role());
        assertEquals(Optional.of(KingdomError.UNKNOWN_KINGDOM), state.crown(UUID.randomUUID(), outsider, 5L).failure());
    }

    @Test
    void restoreRejectsInconsistentKingdomsWithoutSideEffects() {
        Kingdom existing = found("Valoria", founder, 8, 8);
        UUID ruler = UUID.randomUUID();
        Kingdom restored = new Kingdom(UUID.randomUUID(), "Restored", 0x123456, ruler, 0L);
        Capital overlapping = new Capital(OVERWORLD, 8, 64, 8);
        int before = changes.get();
        assertThrows(IllegalArgumentException.class, () -> state.restore(restored, overlapping, List.of(),
                RolePermissions.defaults(), List.of(new Member(ruler, Role.KING, 0L)), Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> state.restore(restored, new Capital(OVERWORLD, 5000, 64, 0),
                List.of(), RolePermissions.defaults(), List.of(), Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> state.restore(restored, null, List.of(ChunkKey.of(99, 99)),
                RolePermissions.defaults(), List.of(new Member(ruler, Role.KING, 0L)), Map.of(), Map.of()));
        assertEquals(before, changes.get());
        assertEquals(1, state.kingdoms().size());

        state.restore(restored, new Capital(OVERWORLD, 5000, 64, 0), List.of(ChunkKey.of(313, 0)),
                RolePermissions.defaults(), List.of(new Member(ruler, Role.KING, 0L)), Map.of(), Map.of());
        assertEquals(Set.of(ChunkKey.of(312, 0), ChunkKey.of(313, 0)),
                Set.copyOf(state.territory().territory(restored.id()).orElseThrow().chunks()));
        assertEquals(Optional.of(restored), state.kingdomOf(ruler));
        assertEquals(Optional.of(existing), state.kingdomOf(founder));
    }
}
