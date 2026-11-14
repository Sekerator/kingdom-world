package io.github.sekerator.kingdomworld.core.state;

import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.land.ProvinceKind;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.ClaimRules;
import io.github.sekerator.kingdomworld.core.territory.Province;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules of moving a throne: only the king of a kingdom with land, not at war, at most once in the configured time; the
 * throne handed out stands only on the claimed land of the kingdom, and once it stands the capital and the chunk that cannot be
 * given up are there. A move finished or called off leaves its throne void.
 */
class KingdomThroneMoveTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final long DAY = 24_000L;

    private KingdomWorldState state;
    private AtomicInteger changes;
    private UUID king;
    private Kingdom kingdom;

    @BeforeEach
    void setUp() {
        state = new KingdomWorldState();
        changes = new AtomicInteger();
        state.setChangeListener(changes::incrementAndGet);
        king = UUID.randomUUID();
        kingdom = found("Valoria", king, 8, 8);
        // Land from chunk -1 to 2 along x: the 3x3 square and one more chunk to the east.
        assertEquals(Outcome.DONE, state.claim(king, kingdom.id(), OVERWORLD, ChunkKey.of(2, 0), KingdomRules.DEFAULT));
    }

    private Kingdom found(String name, UUID ruler, int blockX, int blockZ) {
        CreateKingdomResult result = state.found(UUID.randomUUID(), name, 0x336699, ruler, new Capital(OVERWORLD, blockX, 64, blockZ), 100L,
                KingdomRules.DEFAULT);
        return assertInstanceOf(CreateKingdomResult.Created.class, result).kingdom();
    }

    private UUID subject() {
        UUID subject = UUID.randomUUID();
        state.membership().apply(subject, kingdom.id(), 0L);
        state.membership().acceptApplication(king, subject, 0L);
        return subject;
    }

    private UUID begin(long time) {
        UUID move = UUID.randomUUID();
        assertEquals(Outcome.DONE, state.beginThroneMove(king, kingdom.id(), move, time, DAY, false));
        return move;
    }

    private Capital capital() {
        return state.territory().territory(kingdom.id()).map(Territory::capital).orElseThrow();
    }

    @Test
    void theKingMovesTheThroneOntoItsLandAndTheCapitalFollows() {
        UUID move = begin(1_000L);
        // Until the new throne stands the old one is the heart of the kingdom.
        assertEquals(Optional.of(kingdom), state.kingdomWithThroneAt(OVERWORLD, 8, 64, 8));
        assertTrue(state.throneMoves().isPending(kingdom.id(), move));

        int before = changes.get();
        assertEquals(Optional.empty(), state.checkThronePlace(king, kingdom.id(), move, OVERWORLD, 40, 3, false));
        assertEquals(Outcome.DONE, state.finishThroneMove(king, kingdom.id(), move, new Capital(OVERWORLD, 40, 70, 3), 2_000L, false));
        assertTrue(changes.get() > before, "the data is saved");

        assertEquals(new Capital(OVERWORLD, 40, 70, 3), capital());
        assertEquals(Optional.of(kingdom), state.kingdomWithThroneAt(OVERWORLD, 40, 70, 3));
        assertTrue(state.kingdomWithThroneAt(OVERWORLD, 8, 64, 8).isEmpty(), "the old place is no heart any more");
        assertTrue(state.throneMoves().pending(kingdom.id()).isEmpty());
        assertEquals(OptionalLong.of(2_000L), state.throneMoves().lastMoved(kingdom.id()));
        // The land stays as it was.
        assertEquals(10, state.territory().territory(kingdom.id()).orElseThrow().size());
    }

    @Test
    void theNewCapitalChunkCannotBeGivenUpAndTheOldOneCan() {
        UUID move = begin(0L);
        TerritoryIndex land = state.territory();
        long oldChunk = ChunkKey.of(0, 0);
        long newChunk = ChunkKey.of(2, 0);
        assertEquals(Optional.of(KingdomError.CAPITAL_CHUNK), ClaimRules.checkUnclaim(land, kingdom.id(), OVERWORLD, oldChunk));
        assertEquals(Outcome.DONE, state.finishThroneMove(king, kingdom.id(), move, new Capital(OVERWORLD, 40, 64, 8), 10L, false));

        assertEquals(Optional.of(KingdomError.CAPITAL_CHUNK), ClaimRules.checkUnclaim(land, kingdom.id(), OVERWORLD, newChunk));
        assertThrows(IllegalArgumentException.class, () -> land.release(kingdom.id(), newChunk));
        // The old capital chunk is an ordinary claim: it goes when the land stays joined without it.
        assertEquals(Optional.of(KingdomError.WOULD_DISCONNECT), ClaimRules.checkUnclaim(land, kingdom.id(), OVERWORLD, ChunkKey.of(1, 0)));
        assertEquals(Optional.empty(), ClaimRules.checkUnclaim(land, kingdom.id(), OVERWORLD, ChunkKey.of(-1, -1)));
        assertEquals(Outcome.DONE, state.unclaim(king, kingdom.id(), OVERWORLD, ChunkKey.of(-1, -1)));
        assertEquals(Outcome.DONE, state.unclaim(king, kingdom.id(), OVERWORLD, ChunkKey.of(-1, 0)));
        assertEquals(Outcome.DONE, state.unclaim(king, kingdom.id(), OVERWORLD, ChunkKey.of(-1, 1)));
        assertEquals(Optional.empty(), ClaimRules.checkUnclaim(land, kingdom.id(), OVERWORLD, ChunkKey.of(0, -1)));
        assertEquals(Outcome.DONE, state.unclaim(king, kingdom.id(), OVERWORLD, ChunkKey.of(0, -1)));
        assertEquals(Outcome.DONE, state.unclaim(king, kingdom.id(), OVERWORLD, ChunkKey.of(0, 1)));
        assertEquals(Outcome.DONE, state.unclaim(king, kingdom.id(), OVERWORLD, oldChunk));
        assertFalse(land.territory(kingdom.id()).orElseThrow().contains(oldChunk));
    }

    @Test
    void onlyTheKingMovesTheThrone() {
        UUID subject = subject();
        UUID stranger = UUID.randomUUID();
        UUID move = UUID.randomUUID();
        assertEquals(Optional.of(KingdomError.NOT_KING), state.beginThroneMove(subject, kingdom.id(), move, 0L, DAY, false).failure());
        assertEquals(Optional.of(KingdomError.NOT_KING), state.beginThroneMove(stranger, kingdom.id(), move, 0L, DAY, false).failure());
        assertEquals(Optional.of(KingdomError.NOT_KING), state.beginThroneMove(null, kingdom.id(), move, 0L, DAY, false).failure());
        assertTrue(state.throneMoves().pending(kingdom.id()).isEmpty());

        // The king of another kingdom is no king here either.
        UUID otherKing = UUID.randomUUID();
        found("Far Realm", otherKing, 10_000, 0);
        assertEquals(Optional.of(KingdomError.NOT_KING), state.beginThroneMove(otherKing, kingdom.id(), move, 0L, DAY, false).failure());

        UUID started = begin(0L);
        assertEquals(Optional.of(KingdomError.NOT_KING), state.checkThronePlace(subject, kingdom.id(), started, OVERWORLD, 40, 3, false));
        assertEquals(Optional.of(KingdomError.NOT_KING), state.finishThroneMove(otherKing, kingdom.id(), started,
                new Capital(OVERWORLD, 40, 64, 3), 0L, false).failure());
        assertEquals(Optional.of(KingdomError.NOT_KING), state.cancelThroneMove(subject, kingdom.id()).failure());
        assertTrue(state.throneMoves().isPending(kingdom.id(), started), "a refused try leaves the move going on");
        assertEquals(new Capital(OVERWORLD, 8, 64, 8), capital());

        assertEquals(Optional.of(KingdomError.UNKNOWN_KINGDOM), state.beginThroneMove(king, UUID.randomUUID(), move, 0L, DAY, false).failure());
    }

    @Test
    void aKingdomWithoutLandHasNoThroneToMove() {
        UUID landlessKing = UUID.randomUUID();
        Kingdom landless = assertInstanceOf(CreateKingdomResult.Created.class,
                state.found(UUID.randomUUID(), "Landless", 0, landlessKing, null, 0L, KingdomRules.DEFAULT)).kingdom();
        assertEquals(Optional.of(KingdomError.NO_CAPITAL),
                state.beginThroneMove(landlessKing, landless.id(), UUID.randomUUID(), 0L, DAY, false).failure());
    }

    @Test
    void theThroneStandsOnlyOnTheClaimedLandOfItsKingdom() {
        UUID move = begin(0L);
        // Land of nobody, another dimension, the land of another kingdom.
        assertEquals(Optional.of(KingdomError.THRONE_MOVE_OUTSIDE), state.checkThronePlace(king, kingdom.id(), move, OVERWORLD, 200, 200, false));
        assertEquals(Optional.of(KingdomError.THRONE_MOVE_OUTSIDE), state.checkThronePlace(king, kingdom.id(), move, NETHER, 8, 8, false));
        Kingdom other = found("Far Realm", UUID.randomUUID(), 10_000, 0);
        assertEquals(Optional.of(other), state.kingdomAt(OVERWORLD, ChunkKey.of(10_000 >> 4, 0)));
        assertEquals(Optional.of(KingdomError.THRONE_MOVE_OUTSIDE),
                state.checkThronePlace(king, kingdom.id(), move, OVERWORLD, 10_000, 3, false));
        assertEquals(Optional.of(KingdomError.THRONE_MOVE_OUTSIDE), state.finishThroneMove(king, kingdom.id(), move,
                new Capital(OVERWORLD, 10_000, 64, 3), 0L, false).failure());
        assertThrows(IllegalArgumentException.class, () -> state.territory().moveCapital(kingdom.id(), new Capital(OVERWORLD, 200, 64, 200)));
        assertThrows(IllegalArgumentException.class, () -> state.territory().moveCapital(kingdom.id(), new Capital(NETHER, 8, 64, 8)));

        // A province of the kingdom is its land, but its wilds are no claim; a claim made before the province came stays one.
        assertEquals(Outcome.DONE, state.claim(king, kingdom.id(), OVERWORLD, ChunkKey.of(3, 0), KingdomRules.DEFAULT));
        ProvinceMap provinces = new ProvinceMap(OVERWORLD);
        provinces.define(Province.plain(1, 64, 0, ProvinceKind.CORE));
        provinces.fill(1, 3, -4, 8, 4);
        state.territory().attachProvinces(provinces);
        state.territory().transferProvince(1, kingdom.id(), 0L);
        assertEquals(Optional.of(kingdom), state.kingdomAt(OVERWORLD, ChunkKey.of(5, 0)));
        assertEquals(Optional.of(KingdomError.THRONE_MOVE_WILD), state.checkThronePlace(king, kingdom.id(), move, OVERWORLD, 5 * 16, 3, false));
        assertEquals(Optional.of(KingdomError.ALREADY_OWNED),
                state.claim(king, kingdom.id(), OVERWORLD, ChunkKey.of(5, 0), KingdomRules.DEFAULT).failure());
        assertEquals(Optional.empty(), state.checkThronePlace(king, kingdom.id(), move, OVERWORLD, 3 * 16 + 5, 3, false));

        assertTrue(state.throneMoves().isPending(kingdom.id(), move), "the throne stays in the hand of the king");
        assertEquals(new Capital(OVERWORLD, 8, 64, 8), capital());
    }

    @Test
    void aKingdomAtWarOrBesiegedKeepsItsThrone() {
        assertEquals(Optional.of(KingdomError.THRONE_MOVE_AT_WAR),
                state.beginThroneMove(king, kingdom.id(), UUID.randomUUID(), 0L, DAY, true).failure());
        assertTrue(state.throneMoves().pending(kingdom.id()).isEmpty());

        // A war that starts while the throne is on its way: it waits in the hand until the war is over.
        UUID move = begin(0L);
        assertEquals(Optional.of(KingdomError.THRONE_MOVE_AT_WAR), state.checkThronePlace(king, kingdom.id(), move, OVERWORLD, 40, 3, true));
        assertEquals(Optional.of(KingdomError.THRONE_MOVE_AT_WAR), state.finishThroneMove(king, kingdom.id(), move,
                new Capital(OVERWORLD, 40, 64, 3), 0L, true).failure());
        assertTrue(state.throneMoves().isPending(kingdom.id(), move));
        assertEquals(new Capital(OVERWORLD, 8, 64, 8), capital());
        assertEquals(Outcome.DONE, state.finishThroneMove(king, kingdom.id(), move, new Capital(OVERWORLD, 40, 64, 3), 5L, false));
    }

    @Test
    void theThroneMovesAtMostOnceInTheConfiguredTime() {
        UUID move = begin(1_000L);
        assertEquals(Optional.of(KingdomError.THRONE_MOVE_PENDING),
                state.beginThroneMove(king, kingdom.id(), UUID.randomUUID(), 1_000L, DAY, false).failure());
        assertEquals(Outcome.DONE, state.finishThroneMove(king, kingdom.id(), move, new Capital(OVERWORLD, 40, 64, 3), 5_000L, false));

        assertEquals(Optional.of(KingdomError.THRONE_MOVE_TOO_SOON),
                state.beginThroneMove(king, kingdom.id(), UUID.randomUUID(), 5_000L + DAY - 1, DAY, false).failure());
        assertEquals(5_000L + DAY, state.throneMoves().nextAllowed(kingdom.id(), DAY));
        assertEquals(Outcome.DONE, state.beginThroneMove(king, kingdom.id(), UUID.randomUUID(), 5_000L + DAY, DAY, false));
        assertEquals(Outcome.DONE, state.cancelThroneMove(king, kingdom.id()));
        // With no time between moves configured the throne moves again at once.
        assertEquals(Outcome.DONE, state.beginThroneMove(king, kingdom.id(), UUID.randomUUID(), 5_001L, 0L, false));
    }

    @Test
    void aMoveCalledOffLeavesItsThroneVoidAndANewOneStartsAtOnce() {
        UUID move = begin(0L);
        assertEquals(Outcome.DONE, state.cancelThroneMove(king, kingdom.id()));
        assertTrue(state.throneMoves().pending(kingdom.id()).isEmpty());
        assertEquals(Optional.of(KingdomError.NO_THRONE_MOVE), state.cancelThroneMove(king, kingdom.id()).failure());

        assertEquals(Optional.of(KingdomError.THRONE_MOVE_VOID), state.checkThronePlace(king, kingdom.id(), move, OVERWORLD, 40, 3, false));
        assertEquals(Optional.of(KingdomError.THRONE_MOVE_VOID), state.finishThroneMove(king, kingdom.id(), move,
                new Capital(OVERWORLD, 40, 64, 3), 0L, false).failure());
        assertEquals(new Capital(OVERWORLD, 8, 64, 8), capital());
        assertTrue(state.throneMoves().lastMoved(kingdom.id()).isEmpty(), "a move called off does not count");

        // A new move starts at once; the throne of the old one stays void, whoever holds it.
        UUID next = begin(10L);
        assertEquals(Optional.of(KingdomError.THRONE_MOVE_VOID), state.checkThronePlace(king, kingdom.id(), move, OVERWORLD, 40, 3, false));
        assertEquals(Optional.of(KingdomError.THRONE_MOVE_VOID), state.checkThronePlace(subject(), kingdom.id(), move, OVERWORLD, 40, 3, false));
        assertEquals(Outcome.DONE, state.finishThroneMove(king, kingdom.id(), next, new Capital(OVERWORLD, 40, 64, 3), 20L, false));
        // A finished move leaves its throne void too: a second copy gives no second throne.
        assertEquals(Optional.of(KingdomError.THRONE_MOVE_VOID), state.checkThronePlace(king, kingdom.id(), next, OVERWORLD, 8, 8, false));
        assertEquals(new Capital(OVERWORLD, 40, 64, 3), capital());
    }

    @Test
    void aDissolvedKingdomLeavesNoMoveBehind() {
        UUID move = begin(0L);
        state.dissolve(kingdom.id());
        assertTrue(state.throneMoves().pending(kingdom.id()).isEmpty());
        assertEquals(Optional.of(KingdomError.THRONE_MOVE_VOID), state.checkThronePlace(king, kingdom.id(), move, OVERWORLD, 40, 3, false));
    }

    @Test
    void theLandStaysJoinedToTheNewCapital() {
        UUID move = begin(0L);
        assertEquals(Outcome.DONE, state.finishThroneMove(king, kingdom.id(), move, new Capital(OVERWORLD, 40, 64, 3), 0L, false));
        TerritoryIndex land = state.territory();
        // Giving up the chunk between the new capital and the rest would cut the rest off it.
        assertFalse(land.staysConnectedWithout(kingdom.id(), ChunkKey.of(1, 0)));
        assertTrue(land.staysConnectedWithout(kingdom.id(), ChunkKey.of(-1, -1)));
        assertFalse(land.staysConnectedWithout(kingdom.id(), ChunkKey.of(2, 0)), "the capital chunk itself never goes");
    }

    @Test
    void movingTheCapitalToWhereItIsChangesNothing() {
        TerritoryIndex land = state.territory();
        long version = land.version();
        land.moveCapital(kingdom.id(), new Capital(OVERWORLD, 8, 64, 8));
        assertEquals(version, land.version());
        land.moveCapital(kingdom.id(), new Capital(OVERWORLD, 9, 64, 8));
        assertTrue(land.version() > version);
        assertEquals(List.of(new Capital(OVERWORLD, 9, 64, 8)), List.of(capital()));
        assertThrows(IllegalArgumentException.class, () -> land.moveCapital(UUID.randomUUID(), new Capital(OVERWORLD, 8, 64, 8)));
    }
}
