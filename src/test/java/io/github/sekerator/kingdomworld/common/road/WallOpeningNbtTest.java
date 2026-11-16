package io.github.sekerator.kingdomworld.common.road;

import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.road.Terrain;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.road.WallContour;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gates of a wall that wait for their place to be loaded to come down are kept in saved data, so that a restart in between
 * does not leave them shut; a wall written before reads with none waiting.
 */
class WallOpeningNbtTest {
    private static final UUID US = new UUID(5, 5);

    private static Wall wall() {
        List<RouteCell> ring = WallContour.ring((x, z) -> Terrain.Column.land(64), 0, 0, 12);
        return new Wall(7, 3, US, Wall.WallClass.PALISADE, "minecraft:overworld", 0, 0, ring, List.of(4), 0);
    }

    @Test
    void gatesWaitingForTheirPlaceSurviveSaving() {
        Wall wall = wall();
        int second = wall.cells().size() / 2;
        assertTrue(wall.addGate(second));
        wall.awaitOpening(4);
        wall.awaitOpening(second);
        Wall read = RoadNbt.readWall(RoadNbt.writeWall(wall));
        assertEquals(List.of(4, second), read.opening());
        assertTrue(read.opened(4));
        assertFalse(read.opened(4), "a gate opened once waits no more");
        assertEquals(List.of(second), read.opening());
    }

    @Test
    void aWallWrittenBeforeHasNoGateWaiting() {
        Wall wall = wall();
        CompoundTag entry = RoadNbt.writeWall(wall);
        assertFalse(entry.contains("opening"), "a wall with no gate waiting writes nothing for it");
        assertTrue(RoadNbt.readWall(entry).opening().isEmpty());
        wall.awaitOpening(wall.cells().size());
        wall.awaitOpening(-1);
        assertTrue(wall.opening().isEmpty(), "a place outside the ring is no gate");
    }
}
