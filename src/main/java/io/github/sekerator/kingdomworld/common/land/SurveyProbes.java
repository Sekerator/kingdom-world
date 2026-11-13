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
