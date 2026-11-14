package io.github.sekerator.kingdomworld.common.road;

import io.github.sekerator.kingdomworld.core.road.LandColumn;
import io.github.sekerator.kingdomworld.core.road.LandSnapshot;
import io.github.sekerator.kingdomworld.core.road.LandTile;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The land of a chunk for the planner of roads: on the main thread the blocks of a loaded chunk are copied, a section at a time
 * in the band from {@value RoadLand#DEPTH} blocks under its lowest ground up to its top, with its height map; off the main
 * thread the copy is read into a {@link LandTile} column by column.
 */
public final class LandScan {

    private LandScan() {
    }

    /**
     * The blocks of a chunk copied for reading elsewhere.
     *
     * @param sections the copied sections from {@code firstSection} up, {@code null} for a section not copied (all air above, below
     *                 the band)
     * @param surface  the height map of the surface, raw
     */
    public record Copy(int chunkX, int chunkZ, int minY, int height, int firstSection, PalettedContainer<BlockState>[] sections,
                       long[] surface) {
    }

    /**
     * Copies the blocks of a loaded chunk: cheap, the arrays of its sections are cloned.
     */
    @SuppressWarnings("unchecked")
    public static Copy copy(LevelChunk chunk) {
        int minY = chunk.getMinBuildHeight();
        int height = chunk.getHeight();
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE);
        int lowest = Integer.MAX_VALUE;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                lowest = Math.min(lowest, surface.getFirstAvailable(x, z) - 1);
            }
        }
        int from = Math.max(minY, lowest - RoadLand.DEPTH - 16);
        LevelChunkSection[] all = chunk.getSections();
        int first = chunk.getSectionIndex(from);
        PalettedContainer<BlockState>[] copies = new PalettedContainer[all.length - first];
        for (int i = first; i < all.length; i++) {
            LevelChunkSection section = all[i];
            copies[i - first] = section == null || section.hasOnlyAir() ? null : section.getStates().copy();
        }
        return new Copy(chunk.getPos().x, chunk.getPos().z, minY, height, first, copies, surface.getRawData().clone());
    }

    /**
     * Reads a copy into a tile; off the main thread.
     */
    public static LandTile read(Copy copy, LandSnapshot snapshot) {
        int bits = Mth.ceillog2(copy.height() + 1);
        SimpleBitStorage surface = new SimpleBitStorage(bits, 256, copy.surface());
        LandColumn[] columns = new LandColumn[LandTile.SIZE];
        BlockState air = Blocks.AIR.defaultBlockState();
        int bottom = copy.minY() + copy.firstSection() * 16;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int top = copy.minY() + surface.get(x + z * 16) - 1;
                final int lx = x;
                final int lz = z;
                LandColumn column = RoadLand.scan(y -> {
                    if (y < bottom) {
                        // Below the band copied: rock as far as the road is concerned.
                        return Blocks.STONE.defaultBlockState();
                    }
                    int index = (y - copy.minY()) / 16 - copy.firstSection();
                    if (index < 0 || index >= copy.sections().length) {
                        return air;
                    }
                    PalettedContainer<BlockState> section = copy.sections()[index];
                    return section == null ? air : section.get(lx, y & 15, lz);
                }, top, copy.minY());
                columns[x * 16 + z] = column;
            }
        }
        return new LandTile(copy.chunkX(), copy.chunkZ(), snapshot.owner(copy.chunkX(), copy.chunkZ()), false, columns);
    }
}
