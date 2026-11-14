package io.github.sekerator.kingdomworld.common.construction;

import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Holds some chunks of a level loaded for a while: the place of a construction far from players while what its builders paid
 * for is put in, or the place a work ordered from the capital looks at. The only loader of the construction far from players.
 *
 * <p>Every chunk gets a region ticket at distance 0: it loads whole (and is generated if it never was, in the worker threads of
 * the game), but its entities do not tick, so no body of a citizen appears in it. The tickets live {@value #LIFESPAN} ticks
 * unless they are given again, which the hold does every {@value #REFRESH} ticks while it lasts; they are never saved, so a
 * restart leaves nothing held. All holds together ask for at most {@code siteLoadChunksPerTick} new tickets a tick, and they let
 * their chunks go one every two ticks. No chunk is ever forced and none is loaded in the tick.
 */
public final class ChunkHold {
    /** Ticks a ticket lives unless it is given again: a holder that is gone frees its chunks by itself. */
    static final int LIFESPAN = 600;
    /** Ticks between two renewals of the tickets of a hold. */
    static final int REFRESH = 200;
    static final TicketType<ChunkPos> TICKET = TicketType.create("kingdomworld_site", Comparator.comparingLong(ChunkPos::toLong), LIFESPAN);

    private static int budgetTick = Integer.MIN_VALUE;
    private static int added;

    private final ServerLevel level;
    private final List<ChunkPos> chunks;
    private final Set<Long> held = new LinkedHashSet<>();
    private int lastRefresh = Integer.MIN_VALUE;
    private int lastRelease = Integer.MIN_VALUE;
    private boolean releasing;

    /**
     * A hold of the chunks given as {@link ChunkKey}s; nothing is held before {@link #hold()}.
     */
    public ChunkHold(ServerLevel level, Collection<Long> chunkKeys) {
        this.level = level;
        List<ChunkPos> list = new ArrayList<>(chunkKeys.size());
        for (long key : new LinkedHashSet<>(chunkKeys)) {
            list.add(new ChunkPos(ChunkKey.x(key), ChunkKey.z(key)));
        }
        this.chunks = List.copyOf(list);
    }

    public ServerLevel level() {
        return level;
    }

    /**
     * The chunks of the hold.
     */
    public List<ChunkPos> chunks() {
        return chunks;
    }

    /**
     * Asks for the tickets not yet given, within the budget of the tick shared by all holds, and gives the held ones again
     * every {@value #REFRESH} ticks so that they do not run out.
     */
    public void hold() {
        if (releasing) {
            return;
        }
        int tick = level.getServer().getTickCount();
        if (tick != budgetTick) {
            budgetTick = tick;
            added = 0;
        }
        int most = ConstructionSettings.siteLoadChunksPerTick();
        for (ChunkPos pos : chunks) {
            if (held.contains(pos.toLong())) {
                continue;
            }
            if (added >= most) {
                break;
            }
            level.getChunkSource().addRegionTicket(TICKET, pos, 0, pos);
            held.add(pos.toLong());
            added++;
        }
        if (tick - lastRefresh >= REFRESH) {
            lastRefresh = tick;
            for (ChunkPos pos : chunks) {
                if (held.contains(pos.toLong())) {
                    level.getChunkSource().addRegionTicket(TICKET, pos, 0, pos);
                }
            }
        }
    }

    /**
     * Whether every chunk of the hold is loaded: held, and in the map of the game (a ticket alone says nothing yet).
     */
    public boolean ready() {
        if (releasing || held.size() < chunks.size()) {
            return false;
        }
        for (ChunkPos pos : chunks) {
            if (level.getChunkSource().getChunkNow(pos.x, pos.z) == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a player is near one of the chunks: its entities tick, the game holds it anyway, and the work of the place goes on
     * as near any player.
     */
    public boolean visited() {
        for (ChunkPos pos : chunks) {
            if (level.isPositionEntityTicking(pos.getWorldPosition())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lets one chunk go every two ticks.
     *
     * @return whether every chunk is let go
     */
    public boolean release() {
        releasing = true;
        int tick = level.getServer().getTickCount();
        if (held.isEmpty()) {
            return true;
        }
        if (tick - lastRelease < 2) {
            return false;
        }
        lastRelease = tick;
        long key = held.iterator().next();
        held.remove(key);
        ChunkPos pos = new ChunkPos(key);
        level.getChunkSource().removeRegionTicket(TICKET, pos, 0, pos);
        return held.isEmpty();
    }

    /**
     * Lets every chunk go at once, when the server stops.
     */
    public void releaseAll() {
        releasing = true;
        for (long key : held) {
            ChunkPos pos = new ChunkPos(key);
            level.getChunkSource().removeRegionTicket(TICKET, pos, 0, pos);
        }
        held.clear();
    }

    /**
     * Whether the holds wait: the server is busy, its average tick longer than {@code siteLoadPauseMillis}.
     */
    public static boolean paused(MinecraftServer server) {
        return server.getAverageTickTime() > ConstructionSettings.siteLoadPauseMillis();
    }

    /**
     * Whether a chunk is in the map of the game now: loaded, not only asked for. Unlike {@code hasChunk} and {@code isLoaded},
     * which already answer yes while a chunk with a ticket still loads.
     */
    public static boolean loaded(ServerLevel level, int chunkX, int chunkZ) {
        return level.getChunkSource().getChunkNow(chunkX, chunkZ) != null;
    }

    /**
     * Whether the chunk of a position is loaded now, see {@link #loaded(ServerLevel, int, int)}.
     */
    public static boolean loaded(ServerLevel level, BlockPos pos) {
        return loaded(level, pos.getX() >> 4, pos.getZ() >> 4);
    }

    /**
     * The chunks of a box widened by one block on every side: placing a block at the edge of a box updates its neighbours, and
     * the game would load the chunk of a missing neighbour in the tick.
     */
    public static Set<Long> ringed(int minX, int minZ, int maxX, int maxZ) {
        Set<Long> keys = new LinkedHashSet<>();
        for (int cx = (minX - 1) >> 4; cx <= (maxX + 1) >> 4; cx++) {
            for (int cz = (minZ - 1) >> 4; cz <= (maxZ + 1) >> 4; cz++) {
                keys.add(ChunkKey.of(cx, cz));
            }
        }
        return keys;
    }
}
