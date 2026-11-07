package io.github.sekerator.kingdomworld.compat.xaero;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.common.minimap.highlight.ChunkHighlighter;
import xaero.hud.minimap.info.render.compile.InfoDisplayCompiler;

/**
 * Land of kingdoms on Xaero's Minimap. Every answer comes from {@link KingdomHighlights}; nothing here throws into
 * Xaero's code.
 */
final class MinimapKingdomHighlighter extends ChunkHighlighter {
    private final KingdomHighlights land;

    MinimapKingdomHighlighter(KingdomHighlights land) {
        super(true);
        this.land = land;
    }

    private static String id(ResourceKey<Level> dimension) {
        return dimension.location().toString();
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
    public void addChunkHighlightTooltips(InfoDisplayCompiler compiler, ResourceKey<Level> dimension, int chunkX, int chunkZ, int width) {
        try {
            for (Component line : land.lines(id(dimension), chunkX, chunkZ)) {
                compiler.addLine(line);
            }
        } catch (RuntimeException e) {
            // Nothing to add.
        }
    }
}
