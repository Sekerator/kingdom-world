package io.github.sekerator.kingdomworld.common.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;

/**
 * The chunks of the fenced area of a game test, kept loaded and ticking while it runs. The world of the game tests ticks the
 * entities only in the chunks of the structures of the tests, and the stand-ins of players hold none: a body that walks out of
 * them stands frozen. Only the chunks nothing forced before are let go at the end, so the chunks the game holds for the
 * structures of the tests stay as they were.
 */
final class TestChunks {
    /** The chunks forced for an area, by the first corner of the area. */
    private static final Map<Long, long[]> HELD = new HashMap<>();

    private TestChunks() {
    }

    /**
     * Forces the chunks of an area that are not forced yet.
     */
    static void hold(ServerLevel level, BlockPos from, BlockPos to) {
        release(level, from);
        java.util.List<Long> forced = new java.util.ArrayList<>();
        for (int cx = Math.min(from.getX(), to.getX()) >> 4; cx <= Math.max(from.getX(), to.getX()) >> 4; cx++) {
            for (int cz = Math.min(from.getZ(), to.getZ()) >> 4; cz <= Math.max(from.getZ(), to.getZ()) >> 4; cz++) {
                long chunk = ChunkPos.asLong(cx, cz);
                if (!level.getForcedChunks().contains(chunk)) {
                    level.setChunkForced(cx, cz, true);
                    forced.add(chunk);
                }
            }
        }
        HELD.put(from.asLong(), forced.stream().mapToLong(Long::longValue).toArray());
    }

    /**
     * Lets go of the chunks forced for the area with this first corner.
     */
    static void release(ServerLevel level, BlockPos from) {
        long[] chunks = HELD.remove(from.asLong());
        if (chunks == null) {
            return;
        }
        for (long chunk : chunks) {
            level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
        }
    }
}
