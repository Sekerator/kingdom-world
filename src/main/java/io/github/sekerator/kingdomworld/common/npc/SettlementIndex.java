package io.github.sekerator.kingdomworld.common.npc;

import io.github.sekerator.kingdomworld.core.npc.NpcState;
import io.github.sekerator.kingdomworld.core.npc.Settlement;
import io.github.sekerator.kingdomworld.core.npc.SettlementGrid;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Optional;

/**
 * Where the settlements of the world stand, for the server thread: the grid of the settlements, built anew only when they
 * change.
 */
public final class SettlementIndex {
    private static NpcState indexed;
    private static long version = -1;
    private static SettlementGrid grid = SettlementGrid.of(List.of());

    private SettlementIndex() {
    }

    public static SettlementGrid grid(MinecraftServer server) {
        NpcState state = NpcWork.state(server);
        if (state != indexed || state.settlementsVersion() != version) {
            grid = SettlementGrid.of(state.settlements());
            indexed = state;
            version = state.settlementsVersion();
        }
        return grid;
    }

    /**
     * The settlement whose square of chunks holds a chunk.
     */
    public static Optional<Settlement> at(MinecraftServer server, int chunkX, int chunkZ) {
        int id = grid(server).at(chunkX, chunkZ);
        return id == 0 ? Optional.empty() : NpcWork.state(server).settlement(id);
    }

    /**
     * The settlements within a distance of a point, in blocks, nearest first.
     */
    public static List<Settlement> near(MinecraftServer server, int x, int z, int radius) {
        return grid(server).near(x, z, radius);
    }

    static void reset() {
        indexed = null;
        version = -1;
        grid = SettlementGrid.of(List.of());
    }
}
