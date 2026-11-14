package io.github.sekerator.kingdomworld.common.road;

import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.road.Wall;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Where the roads and walls of a kingdom lie, as columns: a building laid out by the server keeps clear of them.
 */
public final class RoadFootprint {
    private RoadFootprint() {
    }

    /**
     * The columns of the roads of a kingdom, as x and z.
     */
    public static List<int[]> roads(MinecraftServer server, UUID kingdomId) {
        List<int[]> cells = new ArrayList<>();
        for (Road road : RoadWork.state(server).roadsOf(kingdomId)) {
            for (RouteCell cell : road.cells()) {
                cells.add(new int[]{cell.x(), cell.z()});
            }
        }
        return cells;
    }

    /**
     * The columns of the roads and of the walls of a kingdom, as x and z.
     */
    public static List<int[]> roadsAndWalls(MinecraftServer server, UUID kingdomId) {
        List<int[]> cells = roads(server, kingdomId);
        for (Wall wall : RoadWork.state(server).walls()) {
            if (wall.kingdomId().equals(kingdomId)) {
                for (RouteCell cell : wall.cells()) {
                    cells.add(new int[]{cell.x(), cell.z()});
                }
            }
        }
        return cells;
    }

    /**
     * Whether a column lies within a margin of a box, along x and z.
     */
    public static boolean near(List<int[]> cells, Box box, int margin) {
        for (int[] cell : cells) {
            if (cell[0] >= box.minX() - margin && cell[0] <= box.maxX() + margin && cell[1] >= box.minZ() - margin
                    && cell[1] <= box.maxZ() + margin) {
                return true;
            }
        }
        return false;
    }
}
