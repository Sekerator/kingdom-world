package io.github.sekerator.kingdomworld.common.land;

import io.github.sekerator.kingdomworld.core.land.SurveyClassifier;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraftforge.common.Tags;

/**
 * What the generator of the world says of a point of the land without generating anything: the kind of its biome and the height
 * of its ground from the noise, as the plan of the places of the kingdoms of the world reads them.
 */
public final class SurveyProbes {
    private SurveyProbes() {
    }

    /**
     * The kind of the biome at a block, by its tags and, for swamps, mountains and deserts of other mods without the tags, by its
     * name: the sea, a river, the shore, a swamp, mountains or peaks, hills, a desert or a badland, or open land.
     */
    public static SurveyClassifier.Probe probe(ServerLevel level, int x, int y, int z) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        Holder<Biome> biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                random.sampler());
        return probe(biome);
    }

    static SurveyClassifier.Probe probe(Holder<Biome> biome) {
        String id = biome.unwrapKey().map(key -> key.location().getPath()).orElse("");
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN)) {
            return SurveyClassifier.Probe.OCEAN;
        }
        if (biome.is(BiomeTags.IS_RIVER)) {
            return SurveyClassifier.Probe.RIVER;
        }
        if (biome.is(BiomeTags.IS_BEACH)) {
            return SurveyClassifier.Probe.BEACH;
        }
        if (biome.is(Tags.Biomes.IS_SWAMP) || id.contains("swamp") || id.contains("marsh") || id.contains("bog")) {
            return SurveyClassifier.Probe.SWAMP;
        }
        if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(Tags.Biomes.IS_MOUNTAIN) || biome.is(Tags.Biomes.IS_PEAK) || id.contains("peak")
                || id.contains("mountain")) {
            return SurveyClassifier.Probe.MOUNTAIN;
        }
        if (biome.is(BiomeTags.IS_HILL)) {
            return SurveyClassifier.Probe.HILL;
        }
        if (biome.is(Tags.Biomes.IS_DESERT) || biome.is(BiomeTags.IS_BADLANDS) || id.contains("desert") || id.contains("badlands")
                || id.contains("mesa")) {
            return SurveyClassifier.Probe.DESERT;
        }
        return SurveyClassifier.Probe.LAND;
    }

    /** The guess of the height is not trusted this close to the level of the sea: the height itself is looked at there. */
    public static final int SEA_BAND = 8;

    /**
     * A guess of the height of the ground at a block, from the depth of the noise of the generator: where it crosses zero, the
     * line along which the generator lays the land before its hills and caves. One look at two heights instead of a column of
     * the noise; a few blocks off (some blocks lower on land), about a hundred times cheaper than {@link #floor}. Null when the
     * generator has no such depth (a flat world, a generator of a mod without it).
     */
    public static Integer guess(ServerLevel level, int x, int z) {
        net.minecraft.world.level.levelgen.DensityFunction depth = level.getChunkSource().randomState().router().depth();
        double low = depth.compute(new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(x, 0, z));
        double high = depth.compute(new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(x, 128, z));
        double slope = (high - low) / 128.0;
        if (!(slope < -1e-6) || Double.isNaN(low)) {
            return null;
        }
        double y = -low / slope;
        return (int) Math.round(Math.max(level.getMinBuildHeight(), Math.min(level.getMaxBuildHeight(), y)));
    }

    /**
     * Whether a guess of the height lies so near the level of the sea that the height itself must be looked at.
     */
    public static boolean nearSea(int guess, int seaLevel) {
        return Math.abs(guess - seaLevel) <= SEA_BAND;
    }

    /**
     * The height of the ground at a block, the bottom of the water where there is water over it: the first block above the
     * ground, so that ground at the level of the sea or below lies under the water of a sea or a lake.
     */
    public static int floor(ServerLevel level, int x, int z) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
    }
}
