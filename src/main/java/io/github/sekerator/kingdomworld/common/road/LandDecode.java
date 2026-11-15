package io.github.sekerator.kingdomworld.common.road;

import com.mojang.serialization.Codec;
import io.github.sekerator.kingdomworld.core.road.LandSnapshot;
import io.github.sekerator.kingdomworld.core.road.LandTile;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/**
 * Reads a generated chunk from the disk for the planner of roads, off the main thread: only a chunk saved by this version of the
 * game and generated in full is taken, its surface from its height map and its blocks section by section with the codec the game
 * saves them with; anything else, or anything that cannot be read, is left to the guess, and a loaded chunk always counts over its
 * copy on the disk.
 */
public final class LandDecode {
    /** Chunks read from the disk at once, all plans together. */
    static final int AT_ONCE = 32;
    private static final Semaphore SLOTS = new Semaphore(AT_ONCE);

    private LandDecode() {
    }

    /**
     * The codec of the blocks of a section, as the game saves them; made on first use.
     */
    private static final class States {
        static final Codec<PalettedContainer<BlockState>> CODEC = PalettedContainer.codecRW(Block.BLOCK_STATE_REGISTRY, BlockState.CODEC,
                PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());
    }

    /**
     * The data version this game saves chunks with.
     */
    static int dataVersion() {
        return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    }

    /**
     * Whether a chunk read from the disk may be used: saved by this version, generated in full, with the height map of its surface.
     */
    public static boolean accepted(CompoundTag tag, int dataVersion) {
        String status = tag.getString("Status");
        return tag.getInt("DataVersion") == dataVersion && ("minecraft:full".equals(status) || "full".equals(status))
                && tag.getCompound("Heightmaps").contains("WORLD_SURFACE", Tag.TAG_LONG_ARRAY);
    }

    /**
     * The first free height of every column, from the height map of the surface as it is saved: {@code x + z * 16}, counted from the
     * bottom of the world.
     */
    public static int[] surface(long[] raw, int height) {
        SimpleBitStorage storage = new SimpleBitStorage(Mth.ceillog2(height + 1), 256, raw);
        int[] heights = new int[256];
        for (int i = 0; i < 256; i++) {
            heights[i] = storage.get(i);
        }
        return heights;
    }

    /**
     * Reads chunks from the disk into tiles, on the thread of a planner, {@link #AT_ONCE} reads at once for all plans together;
     * a chunk not saved, saved by another version, not generated in full or not to be read is left out.
     */
    static Map<Long, LandTile> read(ServerLevel level, Collection<Long> chunks, LandSnapshot snapshot, BooleanSupplier cancelled) {
        Map<Long, LandTile> tiles = new HashMap<>();
        int minY = level.getMinBuildHeight();
        int height = level.getHeight();
        Iterator<Long> next = chunks.iterator();
        Deque<Map.Entry<Long, CompletableFuture<Optional<CompoundTag>>>> pending = new ArrayDeque<>();
        try {
            while ((next.hasNext() || !pending.isEmpty()) && !cancelled.getAsBoolean()) {
                if (next.hasNext() && pending.size() < AT_ONCE && SLOTS.tryAcquire(pending.isEmpty() ? 50 : 0, TimeUnit.MILLISECONDS)) {
                    long key = next.next();
                    pending.add(Map.entry(key, load(level, key)));
                    continue;
                }
                Map.Entry<Long, CompletableFuture<Optional<CompoundTag>>> first = pending.peek();
                if (first == null) {
                    continue;
                }
                Optional<CompoundTag> tag;
                try {
                    tag = first.getValue().get(100, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    continue;
                } catch (ExecutionException e) {
                    tag = Optional.empty();
                }
                pending.poll();
                SLOTS.release();
                long key = first.getKey();
                int chunkX = (int) (key >> 32);
                int chunkZ = (int) key;
                Optional<LandScan.Copy> copy = tag.flatMap(t -> copy(t, chunkX, chunkZ, minY, height));
                if (copy.isPresent()) {
                    try {
                        tiles.put(key, LandScan.read(copy.get(), snapshot));
                    } catch (RuntimeException e) {
                        // Left to the other way.
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // The reads still going finish on their own; their places are given back.
            SLOTS.release(pending.size());
        }
        return tiles;
    }

    private static CompletableFuture<Optional<CompoundTag>> load(ServerLevel level, long key) {
        try {
            return level.getChunkSource().chunkMap.read(new ChunkPos((int) (key >> 32), (int) key));
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    /**
     * The blocks of a chunk read from the disk as a copy for {@link LandScan#read}, or empty when it is not one to use.
     */
    static Optional<LandScan.Copy> copy(CompoundTag tag, int chunkX, int chunkZ, int minY, int height) {
        try {
            if (!accepted(tag, dataVersion())) {
                return Optional.empty();
            }
            long[] surface = tag.getCompound("Heightmaps").getLongArray("WORLD_SURFACE");
            // Read once to be sure the array fits the height of the world.
            surface(surface, height);
            int minSection = minY >> 4;
            int sections = height >> 4;
            @SuppressWarnings("unchecked")
            PalettedContainer<BlockState>[] states = new PalettedContainer[sections];
            ListTag list = tag.getList("sections", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag section = list.getCompound(i);
                int index = section.getByte("Y") - minSection;
                if (index < 0 || index >= sections || !section.contains("block_states", Tag.TAG_COMPOUND)) {
                    continue;
                }
                Optional<PalettedContainer<BlockState>> parsed = States.CODEC.parse(NbtOps.INSTANCE, section.getCompound("block_states")).result();
                if (parsed.isEmpty()) {
                    return Optional.empty();
                }
                states[index] = parsed.get();
            }
            return Optional.of(new LandScan.Copy(chunkX, chunkZ, minY, height, 0, states, surface));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
