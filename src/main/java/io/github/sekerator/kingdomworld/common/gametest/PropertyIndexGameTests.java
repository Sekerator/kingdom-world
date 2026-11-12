package io.github.sekerator.kingdomworld.common.gametest;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.law.LawVandalism;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.ConstructionBook;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.road.Endpoint;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadClass;
import io.github.sekerator.kingdomworld.core.road.RoadLayout;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.road.Wall;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;

/**
 * What a stranger breaks is property of a kingdom by the same answers as before the indexes: a block in the box of one of its
 * registered buildings, in one of its walls or on one of its roads; not a block beside them, and not a block of the wall of
 * another kingdom. Everything stands far from the city of the kingdom, so that only the buildings, walls and roads count.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PropertyIndexGameTests {
    private static final String EMPTY = "empty";

    private PropertyIndexGameTests() {
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_property_index")
    public static void buildingsWallsAndRoadsAreFoundAsBefore(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        String dimension = Dimensions.id(level);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Property Index", UUID.randomUUID());
        Kingdom other = null;
        ConstructionBook book = KingdomWorldApi.state(server).construction();
        RoadState roads = RoadWork.state(server);
        long now = KingdomWorldApi.gameTime(server);
        BlockPos base = helper.absolutePos(new BlockPos(0, 1, 0)).offset(200, 0, 0);
        long buildingId = book.nextBuildingId();
        List<Long> walls = new ArrayList<>();
        Road road = null;
        try {
            for (Kingdom stale : KingdomWorldApi.kingdoms(server)) {
                if (stale.name().equals("GameTest Property Neighbour")) {
                    KingdomWorldApi.dissolveKingdom(server, stale.id());
                }
            }
            CreateKingdomResult created = KingdomWorldApi.createKingdom(server, "GameTest Property Neighbour", UUID.randomUUID());
            other = ((CreateKingdomResult.Created) created).kingdom();

            Box box = new Box(base.getX(), base.getY(), base.getZ(), base.getX() + 6, base.getY() + 5, base.getZ() + 6);
            book.putBuilding(new Building(buildingId, kingdom.id(), "kingdomworld:test_property", "house", "test", "", 1, Materials.NONE,
                    new WorldPos(dimension, base.getX(), base.getY(), base.getZ()), new Placement(0, false), box, 0L, now, true, 100, now, false,
                    new BitSet()));
            Wall own = wall(roads, kingdom.id(), dimension, base.offset(20, 0, 0), now);
            Wall theirs = wall(roads, other.id(), dimension, base.offset(40, 0, 0), now);
            walls.add(own.id());
            walls.add(theirs.id());
            List<RouteCell> cells = new ArrayList<>();
            for (int x = 0; x < 24; x++) {
                cells.add(new RouteCell(base.getX() + 60 + x, base.getY(), base.getZ(), RouteCell.Kind.GROUND, 0, null));
            }
            road = roads.addRoad(kingdom.id(), RoadClass.GRAVEL, dimension, Endpoint.point(base.getX() + 60, base.getZ()),
                    Endpoint.point(base.getX() + 83, base.getZ()), cells, RoadState.sectionOwners(cells), RoadState.crossedSections(cells, kingdom.id()),
                    Road.Mode.OWN, now, now + 24_000, List.of());

            helper.assertTrue(LawVandalism.isProperty(server, kingdom.id(), level, base.offset(3, 2, 3)), "A block of a building is no property");
            helper.assertTrue(!LawVandalism.isProperty(server, kingdom.id(), level, base.offset(8, 2, 3)), "A block beside a building is property");
            helper.assertTrue(!LawVandalism.isProperty(server, other.id(), level, base.offset(3, 2, 3)),
                    "A building is property of another kingdom");
            helper.assertTrue(LawVandalism.isProperty(server, kingdom.id(), level, base.offset(20, 1, 0)), "A block of its wall is no property");
            helper.assertTrue(!LawVandalism.isProperty(server, kingdom.id(), level, base.offset(40, 1, 0)),
                    "The wall of another kingdom is property of this one");
            helper.assertTrue(LawVandalism.isProperty(server, other.id(), level, base.offset(40, 1, 0)), "Its own wall is no property of the other");
            RoadLayout.Block onRoad = road.blocks().get(road.blocks().size() / 2);
            helper.assertTrue(LawVandalism.isProperty(server, kingdom.id(), level, new BlockPos(onRoad.x(), onRoad.y(), onRoad.z())),
                    "A block of its road is no property");
            helper.assertTrue(!LawVandalism.isProperty(server, kingdom.id(), level, new BlockPos(onRoad.x(), onRoad.y() + 5, onRoad.z())),
                    "A block high above its road is property");
            helper.assertTrue(!LawVandalism.isProperty(server, kingdom.id(), level, new BlockPos(onRoad.x(), onRoad.y(), onRoad.z() + 12)),
                    "A block beside its road is property");

            // The answers follow changes at once: a road taken away, a wall gone, a building pulled down.
            roads.removeRoad(road.id());
            helper.assertTrue(!LawVandalism.isProperty(server, kingdom.id(), level, new BlockPos(onRoad.x(), onRoad.y(), onRoad.z())),
                    "A road taken away is still property");
            road = null;
            book.removeBuilding(buildingId);
            helper.assertTrue(!LawVandalism.isProperty(server, kingdom.id(), level, base.offset(3, 2, 3)), "A building pulled down is still property");
        } finally {
            if (road != null) {
                roads.removeRoad(road.id());
            }
            for (long id : walls) {
                roads.removeWall(id);
            }
            book.removeBuilding(buildingId);
            if (other != null) {
                KingdomWorldApi.dissolveKingdom(server, other.id());
            }
            KingdomWorldApi.dissolveKingdom(server, kingdom.id());
        }
        helper.succeed();
    }

    /**
     * A palisade of six cells along x from a place, its blocks one and two above the ground marked as its own.
     */
    private static Wall wall(RoadState roads, UUID kingdomId, String dimension, BlockPos from, long now) {
        List<RouteCell> cells = new ArrayList<>();
        for (int x = 0; x < 6; x++) {
            cells.add(new RouteCell(from.getX() + x, from.getY(), from.getZ(), RouteCell.Kind.GROUND, 0, null));
        }
        Wall wall = new Wall(roads.nextWallId(), 0, kingdomId, Wall.WallClass.PALISADE, dimension, from.getX() + 3, from.getZ(), cells, List.of(),
                now);
        for (RouteCell cell : cells) {
            wall.markPlaced(BlockPos.asLong(cell.x(), cell.y() + 1, cell.z()));
            wall.markPlaced(BlockPos.asLong(cell.x(), cell.y() + 2, cell.z()));
        }
        roads.putWall(wall);
        return wall;
    }
}
