package io.github.sekerator.kingdomworld.common.road;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A chunk read from the disk for the planner of roads: its surface comes back from the height map and its blocks from the sections as
 * the game saves them, and only a chunk of this version generated in full with that height map and sections that can be read is taken.
 */
class LandDecodeTest {
    private static final int HEIGHT = 384;
    private static final int VERSION = 3465;

    private static long[] heightMap(int[] heights) {
        SimpleBitStorage storage = new SimpleBitStorage(Mth.ceillog2(HEIGHT + 1), 256);
        for (int i = 0; i < 256; i++) {
            storage.set(i, heights[i]);
        }
        return storage.getRaw().clone();
    }

    private static CompoundTag chunk(int version, String status, boolean heightMap) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", version);
        tag.putString("Status", status);
        CompoundTag maps = new CompoundTag();
        if (heightMap) {
            maps.put("WORLD_SURFACE", new LongArrayTag(heightMap(new int[256])));
        }
        tag.put("Heightmaps", maps);
        return tag;
    }

    @Test
    void surfaceComesBackAsSaved() {
        int[] heights = new int[256];
        for (int i = 0; i < 256; i++) {
            // From the bottom of the world to its top, hills and a hole.
            heights[i] = i == 7 ? 0 : i == 8 ? HEIGHT : 128 + (i * 37) % 97;
        }
        assertArrayEquals(heights, LandDecode.surface(heightMap(heights), HEIGHT));
    }

    @Test
    void surfaceOfAnotherHeightIsRefused() {
        long[] raw = heightMap(new int[256]);
        long[] shorter = new long[raw.length - 1];
        assertThrows(RuntimeException.class, () -> LandDecode.surface(shorter, HEIGHT));
    }

    @Test
    void onlyChunksOfThisVersionGeneratedInFull() {
        assertTrue(LandDecode.accepted(chunk(VERSION, "minecraft:full", true), VERSION));
        assertTrue(LandDecode.accepted(chunk(VERSION, "full", true), VERSION));
        assertFalse(LandDecode.accepted(chunk(VERSION, "minecraft:features", true), VERSION), "not generated in full");
        assertFalse(LandDecode.accepted(chunk(VERSION, "minecraft:empty", true), VERSION), "not generated");
        assertFalse(LandDecode.accepted(chunk(VERSION - 1, "minecraft:full", true), VERSION), "saved by an older version");
        assertFalse(LandDecode.accepted(chunk(VERSION + 1, "minecraft:full", true), VERSION), "saved by a newer version");
        assertFalse(LandDecode.accepted(chunk(VERSION, "minecraft:full", false), VERSION), "no height map");
        assertFalse(LandDecode.accepted(new CompoundTag(), VERSION), "nothing saved");
    }

    @Test
    void sectionsComeBackAsSaved() {
        try {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        } catch (Throwable e) {
            // The hooks of the network of Forge want the loader; the blocks are registered before them.
        }
        PalettedContainer<BlockState> states;
        try {
            states = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES);
        } catch (Throwable e) {
            Assumptions.assumeTrue(false, "the blocks of the game are not there without the game: " + e);
            return;
        }
        states.set(0, 0, 0, Blocks.STONE.defaultBlockState());
        states.set(5, 15, 9, Blocks.WATER.defaultBlockState());
        states.set(15, 7, 15, Blocks.OAK_LOG.defaultBlockState());
        Tag saved = PalettedContainer.codecRW(Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES,
                Blocks.AIR.defaultBlockState()).encodeStart(NbtOps.INSTANCE, states).getOrThrow(false, error -> { });
        CompoundTag tag = chunk(LandDecode.dataVersion(), "minecraft:full", true);
        ListTag sections = new ListTag();
        CompoundTag section = new CompoundTag();
        // The section from 64 to 79 in a world from -64.
        section.putByte("Y", (byte) 4);
        section.put("block_states", saved);
        sections.add(section);
        CompoundTag above = new CompoundTag();
        above.putByte("Y", (byte) 30);
        above.put("block_states", saved);
        sections.add(above);
        tag.put("sections", sections);

        LandScan.Copy copy = LandDecode.copy(tag, 3, -4, -64, HEIGHT).orElseThrow();
        assertEquals(3, copy.chunkX());
        assertEquals(-4, copy.chunkZ());
        assertEquals(HEIGHT / 16, copy.sections().length);
        PalettedContainer<BlockState> read = copy.sections()[8];
        assertEquals(Blocks.STONE.defaultBlockState(), read.get(0, 0, 0));
        assertEquals(Blocks.WATER.defaultBlockState(), read.get(5, 15, 9));
        assertEquals(Blocks.OAK_LOG.defaultBlockState(), read.get(15, 7, 15));
        assertEquals(Blocks.AIR.defaultBlockState(), read.get(1, 1, 1));
        assertNull(copy.sections()[0], "a section not saved stays empty");

        CompoundTag broken = section.copy();
        broken.put("block_states", new CompoundTag());
        ListTag wrong = new ListTag();
        wrong.add(broken);
        tag.put("sections", wrong);
        assertEquals(Optional.empty(), LandDecode.copy(tag, 3, -4, -64, HEIGHT), "a section that cannot be read leaves the chunk to the guess");
    }

    @Test
    void aChunkNotToUseIsLeftToTheGuess() {
        Optional<LandScan.Copy> copy = LandDecode.copy(chunk(1, "minecraft:full", true), 3, -4, -64, HEIGHT);
        assertEquals(Optional.empty(), copy);
    }
}
