package io.github.sekerator.kingdomworld.common.data;

import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.state.KingdomRules;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.ThroneMoves;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A moved throne, a move going on and the time of the last move survive saving; data saved before thrones moved loads as
 * before, with no move and no time.
 */
class ThroneMoveNbtTest {
    private static final String OVERWORLD = "minecraft:overworld";

    private static Kingdom found(KingdomWorldState state, UUID king) {
        CreateKingdomResult result = state.found(UUID.randomUUID(), "Valoria", 0x336699, king, new Capital(OVERWORLD, 8, 64, 8), 100L,
                KingdomRules.DEFAULT);
        return assertInstanceOf(CreateKingdomResult.Created.class, result).kingdom();
    }

    @Test
    void theMovedThroneAndTheMoveGoingOnAreSaved() {
        KingdomWorldState state = new KingdomWorldState();
        UUID king = UUID.randomUUID();
        Kingdom kingdom = found(state, king);
        UUID first = UUID.randomUUID();
        assertEquals(Outcome.DONE, state.beginThroneMove(king, kingdom.id(), first, 200L, 24_000L, false));
        assertEquals(Outcome.DONE, state.finishThroneMove(king, kingdom.id(), first, new Capital(OVERWORLD, 20, 70, -5), 300L, false));
        UUID second = UUID.randomUUID();
        assertEquals(Outcome.DONE, state.beginThroneMove(king, kingdom.id(), second, 30_000L, 24_000L, false));

        CompoundTag tag = KingdomNbt.write(state, kingdom);
        KingdomWorldState loaded = new KingdomWorldState();
        KingdomNbt.restore(loaded, tag);

        assertEquals(new Capital(OVERWORLD, 20, 70, -5), loaded.territory().territory(kingdom.id()).map(Territory::capital).orElseThrow());
        assertEquals(Optional.of(kingdom), loaded.kingdomWithThroneAt(OVERWORLD, 20, 70, -5));
        assertEquals(Optional.of(new ThroneMoves.Move(second, 30_000L)), loaded.throneMoves().pending(kingdom.id()));
        assertEquals(OptionalLong.of(300L), loaded.throneMoves().lastMoved(kingdom.id()));
        assertTrue(loaded.throneMoves().isPending(kingdom.id(), second));
        assertFalse(loaded.throneMoves().isPending(kingdom.id(), first));
    }

    @Test
    void dataFromBeforeThronesMovedLoadsWithoutAMove() {
        KingdomWorldState state = new KingdomWorldState();
        UUID king = UUID.randomUUID();
        Kingdom kingdom = found(state, king);
        CompoundTag tag = KingdomNbt.write(state, kingdom);
        assertFalse(tag.contains(KingdomNbt.THRONE_MOVE));
        assertFalse(tag.contains(KingdomNbt.THRONE_MOVED));

        KingdomWorldState loaded = new KingdomWorldState();
        KingdomNbt.restore(loaded, tag);
        assertEquals(Optional.of(kingdom), loaded.kingdomWithThroneAt(OVERWORLD, 8, 64, 8));
        assertTrue(loaded.throneMoves().pending(kingdom.id()).isEmpty());
        assertTrue(loaded.throneMoves().lastMoved(kingdom.id()).isEmpty());
        assertEquals(Outcome.DONE, loaded.beginThroneMove(king, kingdom.id(), UUID.randomUUID(), 0L, 24_000L, false));
    }

    @Test
    void aMoveThatCannotBeReadIsDroppedRatherThanLockingTheData() {
        KingdomWorldState state = new KingdomWorldState();
        UUID king = UUID.randomUUID();
        Kingdom kingdom = found(state, king);
        CompoundTag tag = KingdomNbt.write(state, kingdom);
        tag.put(KingdomNbt.THRONE_MOVE, StringTag.valueOf("broken"));

        KingdomWorldState loaded = new KingdomWorldState();
        KingdomNbt.restore(loaded, tag);
        assertTrue(loaded.kingdoms().get(kingdom.id()).isPresent());
        assertTrue(loaded.throneMoves().pending(kingdom.id()).isEmpty());
    }
}
