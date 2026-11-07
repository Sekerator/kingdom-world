package io.github.sekerator.kingdomworld.compat.xaero;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.highlight.ChunkHighlighter;

import java.util.List;

/**
 * Land of kingdoms on Xaero's World Map. Every answer comes from {@link KingdomHighlights}; nothing here throws into
 * Xaero's code.
 */
final class WorldMapKingdomHighlighter extends ChunkHighlighter {
    private final KingdomHighlights land;

    WorldMapKingdomHighlighter(KingdomHighlights land) {
        super(true);
        this.land = land;
    }

    private static String id(ResourceKey<Level> dimension) {
        return dimension.location().toString();
    }

    @Override
    public int calculateRegionHash(ResourceKey<Level> dimension, int regionX, int regionZ) {
        try {
            return land.regionHash(id(dimension), regionX, regionZ);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    @Override
    public boolean regionHasHighlights(ResourceKey<Level> dimension, int regionX, int regionZ) {
        try {
            return land.regionHasLand(id(dimension), regionX, regionZ);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        try {
            return land.owner(id(dimension), chunkX, chunkZ) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    protected int[] getColors(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        try {
            return land.colors(id(dimension), chunkX, chunkZ, resultStore) ? resultStore : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        try {
            return land.shortText(id(dimension), chunkX, chunkZ);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public Component getChunkHighlightBluntTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        try {
            return land.longText(id(dimension), chunkX, chunkZ);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public void addMinimapBlockHighlightTooltips(List<Component> list, ResourceKey<Level> dimension, int blockX, int blockZ, int width) {
        try {
            list.addAll(land.lines(id(dimension), blockX >> 4, blockZ >> 4));
        } catch (RuntimeException e) {
            // Nothing to add.
        }
    }
}
