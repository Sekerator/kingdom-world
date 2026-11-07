package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * Whether blocks lie on a kingdom's own land, remembering the last chunk asked about, since work and surveys ask
 * block after block. Workers never touch a block that fails this check.
 */
public final class LandCheck {
    private final TerritoryIndex territory;
    private final String dimension;
    private final UUID kingdomId;
    private long lastChunk = Long.MIN_VALUE;
    private boolean lastOwned;

    public LandCheck(ServerLevel level, UUID kingdomId) {
        this.territory = KingdomWorldApi.state(level.getServer()).territory();
        this.dimension = Dimensions.id(level);
        this.kingdomId = kingdomId;
    }

    public boolean owns(int x, int z) {
        long chunk = ChunkKey.of(x >> 4, z >> 4);
        if (chunk != lastChunk) {
            lastChunk = chunk;
            lastOwned = kingdomId.equals(territory.ownerOf(dimension, chunk));
        }
        return lastOwned;
    }

    public boolean owns(BlockPos pos) {
        return owns(pos.getX(), pos.getZ());
    }
}
