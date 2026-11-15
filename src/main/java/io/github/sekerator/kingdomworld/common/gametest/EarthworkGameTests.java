package io.github.sekerator.kingdomworld.common.gametest;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.work.GatheringSettings;
import io.github.sekerator.kingdomworld.common.construction.BuilderBehavior;
import io.github.sekerator.kingdomworld.common.construction.ConstructionSettings;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.construction.Earthworks;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.ConstructionRules;
import io.github.sekerator.kingdomworld.core.construction.Earthwork;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Earthworks by the builders far from players: a hill levelled, a pit filled from the storehouse, a chest with something in it
 * left where it stands.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EarthworkGameTests {
    private static final String EMPTY = "empty";
    private static final ConstructionRules FAST = new ConstructionRules(4, 64, 4.5, 12.0, 400, 2, 0.0, 48, 32, 48);

    private EarthworkGameTests() {
    }

    /** How high over the floor of the test its plot is cleared and looked at from. */
    private static final int OPEN_ABOVE = 9;

    /**
     * Clears the place of the test up to {@link #OPEN_ABOVE} and has the land of plots looked at from just over it: the tests stand
     * deep under the land, in the caves of the world beyond the small box the game clears, and the rock over them would be the
     * surface of every column.
     */
    private static void open(GameTestHelper helper) {
        GatheringGameTests.clear(helper.getLevel(), helper.absolutePos(new BlockPos(-1, 0, -1)), helper.absolutePos(new BlockPos(10, OPEN_ABOVE, 10)));
        Earthworks.lookFromForTests(helper.absolutePos(new BlockPos(0, OPEN_ABOVE + 1, 0)).getY());
    }

    /**
     * Flat land of earth one block thick over the floor of the test, from 0 to 9 both ways.
     */
    private static void floor(GameTestHelper helper, int height) {
        for (int x = 0; x <= 9; x++) {
            for (int z = 0; z <= 9; z++) {
                for (int y = 0; y < height; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
                }
            }
        }
    }

    private static Citizen builder(GameTestHelper helper, Kingdom kingdom) {
        MinecraftServer server = helper.getLevel().getServer();
        WorldPos post = GatheringGameTests.post(helper, new BlockPos(0, 3, 0), "builder");
        Citizen citizen = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
        KingdomWorldApi.change(server, "test", state -> state.setProfession(null, citizen.id(), "builder"));
        helper.assertTrue(KingdomWorldApi.state(server).citizens().assignWorkplace(citizen, post), "The builder got no seat at the post");
        return citizen;
    }

    /**
     * Work far from players until the earthworks are done or the rounds run out: the builder works, a second of construction puts
     * what was done into the world.
     */
    private static void workFar(MinecraftServer server, Citizen builder, Construction construction, int rounds) {
        GatheringSettings.override(GatheringGameTests.safeRules(0.0));
        try {
            for (int i = 0; i < rounds && KingdomWorldApi.state(server).construction().get(construction.id()).isPresent(); i++) {
                new BuilderBehavior().workAbstractly(server, builder, 1_200);
                ConstructionWork.tick(server, 1);
            }
        } finally {
            GatheringSettings.override(null);
        }
    }

    private static Construction lay(GameTestHelper helper, Kingdom kingdom, BlockPos first, BlockPos second, Earthwork.Mode mode, int target) {
        Earthworks.Request request = new Earthworks.Request(helper.absolutePos(first), helper.absolutePos(second), mode,
                helper.absolutePos(new BlockPos(0, target, 0)).getY(), false);
        KingdomWorldState.ConstructionChange change = Earthworks.lay(helper.getLevel(), null, kingdom.id(), request);
        if (change.construction().isEmpty()) {
            throw new IllegalStateException("The earthworks were refused: " + change.outcome());
        }
        return change.construction().get();
    }

    private static long stored(MinecraftServer server, Kingdom kingdom, Item item) {
        return KingdomStorage.count(server, kingdom.id(), stack -> stack.is(item));
    }

    private static void end(GameTestHelper helper, Kingdom kingdom, Citizen... citizens) {
        ConstructionSettings.override(null);
        Earthworks.lookFromForTests(null);
        CitizenGameTests.finish(helper, kingdom, citizens);
        GatheringGameTests.clear(helper.getLevel(), helper.absolutePos(new BlockPos(-1, 0, -1)), helper.absolutePos(new BlockPos(10, OPEN_ABOVE, 10)));
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_earthwork_hill", timeoutTicks = 400)
    public static void aHillIsLevelledAndItsStoneGoesToTheStorehouse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ConstructionSettings.override(FAST);
        open(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Diggers", UUID.randomUUID());
        floor(helper, 1);
        // A hill of stone three blocks high on the earth, and a tree on it.
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                for (int y = 1; y <= 3; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        helper.setBlock(new BlockPos(4, 4, 4), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(4, 5, 4), Blocks.OAK_LEAVES);
        StorehouseInventory store = GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(8, 1, 8)));
        Citizen builder = builder(helper, kingdom);
        try {
            Construction work = lay(helper, kingdom, new BlockPos(2, 0, 2), new BlockPos(6, 0, 6), Earthwork.Mode.LEVEL, 0);
            helper.assertTrue(work.kind() == Construction.Kind.EARTHWORK, "Not earthworks: " + work.kind());
            workFar(server, builder, work, 200);
            for (int x = 3; x <= 5; x++) {
                for (int z = 3; z <= 5; z++) {
                    for (int y = 1; y <= 5; y++) {
                        helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(x, y, z))).isAir(), "The hill still stands at "
                                + x + " " + y + " " + z);
                    }
                }
            }
            helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(4, 0, 4))).is(Blocks.DIRT), "The ground under the hill was dug");
            helper.assertTrue(KingdomWorldApi.state(server).construction().get(work.id()).isEmpty(), "The earthworks are not finished");
            helper.assertTrue(stored(server, kingdom, Items.COBBLESTONE) == 27, "The stone of the hill did not go to the storehouse: "
                    + stored(server, kingdom, Items.COBBLESTONE));
            helper.assertTrue(stored(server, kingdom, Items.OAK_LOG) == 1, "The wood of the tree did not go to the storehouse");
            helper.assertTrue(store != null, "No storehouse");
        } finally {
            end(helper, kingdom, builder);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_earthwork_pit", timeoutTicks = 400)
    public static void aPitIsFilledWithEarthFromTheStorehouse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ConstructionSettings.override(FAST);
        open(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Fillers", UUID.randomUUID());
        floor(helper, 3);
        // A pit of three by three, two blocks deep.
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
                helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
            }
        }
        StorehouseInventory store = GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(8, 3, 8)));
        store.insertItem(0, new ItemStack(Items.DIRT, 30), false);
        Citizen builder = builder(helper, kingdom);
        try {
            Construction work = lay(helper, kingdom, new BlockPos(2, 2, 2), new BlockPos(6, 2, 6), Earthwork.Mode.FILL, 2);
            workFar(server, builder, work, 200);
            for (int x = 3; x <= 5; x++) {
                for (int z = 3; z <= 5; z++) {
                    for (int y = 1; y <= 2; y++) {
                        helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(x, y, z))).is(Blocks.DIRT), "The pit is open at "
                                + x + " " + y + " " + z);
                    }
                }
            }
            helper.assertTrue(stored(server, kingdom, Items.DIRT) == 30 - 18, "The earth of the fill did not come from the storehouse: "
                    + stored(server, kingdom, Items.DIRT) + " left");
            helper.assertTrue(KingdomWorldApi.state(server).construction().get(work.id()).isEmpty(), "The earthworks are not finished");
        } finally {
            end(helper, kingdom, builder);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_earthwork_chest", timeoutTicks = 400)
    public static void aChestWithSomethingInItStaysWhereItStands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ConstructionSettings.override(FAST);
        open(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Careful Diggers", UUID.randomUUID());
        floor(helper, 1);
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                for (int y = 1; y <= 2; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
                }
            }
        }
        // A chest with an apple in the middle of the hill, a pillar of bricks somebody built at its edge.
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.CHEST);
        Container chest = (Container) level.getBlockEntity(helper.absolutePos(new BlockPos(4, 2, 4)));
        chest.setItem(0, new ItemStack(Items.APPLE));
        helper.setBlock(new BlockPos(5, 1, 5), Blocks.BRICKS);
        GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(8, 1, 8)));
        Citizen builder = builder(helper, kingdom);
        try {
            Earthworks.Survey survey = Earthworks.survey(level, kingdom.id(), new Earthworks.Request(helper.absolutePos(new BlockPos(2, 0, 2)),
                    helper.absolutePos(new BlockPos(6, 0, 6)), Earthwork.Mode.CUT, helper.absolutePos(BlockPos.ZERO).getY(), false));
            helper.assertTrue(survey.keptAll() == 2, "The chest and the bricks are not left as they are: " + survey.keptAll());
            Construction work = lay(helper, kingdom, new BlockPos(2, 0, 2), new BlockPos(6, 0, 6), Earthwork.Mode.CUT, 0);
            workFar(server, builder, work, 200);
            helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(4, 2, 4))).is(Blocks.CHEST) && chest.getItem(0).is(Items.APPLE),
                    "The chest with the apple was dug");
            helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(5, 1, 5))).is(Blocks.BRICKS), "The bricks were dug");
            helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(3, 1, 3))).isAir(), "The earth around them was not dug");
        } finally {
            end(helper, kingdom, builder);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_earthwork_wall", timeoutTicks = 400)
    public static void aFloorLevelledAlongAPalisadeIsFlat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ConstructionSettings.override(FAST);
        open(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Flat Floors", UUID.randomUUID());
        floor(helper, 1);
        // The plot to cut: two blocks of earth over the floor.
        for (int x = 2; x <= 7; x++) {
            for (int z = 3; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.DIRT);
            }
        }
        // A palisade of the kingdom along the north edge with fences on top, and a walkway of fences and planks along the east edge.
        java.util.List<io.github.sekerator.kingdomworld.core.road.RouteCell> cells = new java.util.ArrayList<>();
        for (int x = 1; x <= 8; x++) {
            BlockPos foot = helper.absolutePos(new BlockPos(x, 0, 2));
            cells.add(new io.github.sekerator.kingdomworld.core.road.RouteCell(foot.getX(), foot.getY(), foot.getZ(),
                    io.github.sekerator.kingdomworld.core.road.RouteCell.Kind.GROUND, 0, null));
            for (int y = 1; y <= 4; y++) {
                helper.setBlock(new BlockPos(x, y, 2), Blocks.OAK_LOG);
            }
            helper.setBlock(new BlockPos(x, 5, 2), Blocks.OAK_FENCE);
        }
        for (int z = 3; z <= 7; z++) {
            helper.setBlock(new BlockPos(8, 1, z), Blocks.OAK_FENCE);
            helper.setBlock(new BlockPos(8, 2, z), Blocks.OAK_FENCE);
            helper.setBlock(new BlockPos(8, 3, z), Blocks.OAK_PLANKS);
        }
        io.github.sekerator.kingdomworld.core.road.Wall[] wall = new io.github.sekerator.kingdomworld.core.road.Wall[1];
        io.github.sekerator.kingdomworld.common.road.RoadWork.change(server, "test wall", state -> {
            BlockPos middle = helper.absolutePos(new BlockPos(5, 0, 6));
            wall[0] = new io.github.sekerator.kingdomworld.core.road.Wall(state.nextWallId(), 0, kingdom.id(),
                    io.github.sekerator.kingdomworld.core.road.Wall.WallClass.PALISADE,
                    io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(level), middle.getX(), middle.getZ(), cells, java.util.List.of(), 0);
            for (io.github.sekerator.kingdomworld.core.road.RouteCell cell : cells) {
                for (int up = 1; up <= 4; up++) {
                    wall[0].markPlaced(BlockPos.asLong(cell.x(), cell.y() + up, cell.z()));
                }
            }
            state.putWall(wall[0]);
            return io.github.sekerator.kingdomworld.core.kingdom.Outcome.DONE;
        });
        Citizen builder = builder(helper, kingdom);
        try {
            int target = helper.absolutePos(new BlockPos(0, 0, 0)).getY();
            Earthworks.Request request = new Earthworks.Request(helper.absolutePos(new BlockPos(2, 0, 3)), helper.absolutePos(new BlockPos(7, 0, 7)),
                    Earthwork.Mode.LEVEL, target, true);
            Earthworks.Survey survey = Earthworks.survey(level, kingdom.id(), request);
            helper.assertTrue(survey.error() == null && survey.work() != null, "The plot was refused: " + survey.error());
            for (int dx = 0; dx < 6; dx++) {
                for (int dz = 0; dz < 5; dz++) {
                    helper.assertTrue(survey.work().surface(dx, dz) == target, "A step by the wall at " + dx + " " + dz + ": "
                            + survey.work().surface(dx, dz));
                }
            }
            helper.assertTrue(survey.fills() == 0, "A smoothed floor by a palisade fills " + survey.fills());
            KingdomWorldState.ConstructionChange change = Earthworks.lay(level, null, kingdom.id(), request);
            helper.assertTrue(change.construction().isPresent(), "The earthworks were refused: " + change.outcome());
            Construction work = change.construction().get();
            workFar(server, builder, work, 200);
            for (int x = 2; x <= 7; x++) {
                for (int z = 3; z <= 7; z++) {
                    helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(x, 0, z))).is(Blocks.DIRT), "The floor was dug at "
                            + x + " " + z);
                    for (int y = 1; y <= 5; y++) {
                        helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(x, y, z))).isAir(), "The floor is not flat at "
                                + x + " " + y + " " + z);
                    }
                }
            }
            for (int x = 1; x <= 8; x++) {
                for (int y = 1; y <= 4; y++) {
                    helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(x, y, 2))).is(Blocks.OAK_LOG), "The palisade was touched at "
                            + x + " " + y);
                }
            }
            for (int z = 3; z <= 7; z++) {
                helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(8, 3, z))).is(Blocks.OAK_PLANKS), "The walkway was touched");
            }
            helper.assertTrue(KingdomWorldApi.state(server).construction().get(work.id()).isEmpty(), "The earthworks are not finished");
        } finally {
            io.github.sekerator.kingdomworld.common.road.RoadWork.state(server).forgetKingdom(kingdom.id());
            end(helper, kingdom, builder);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_earthwork_wall_after", timeoutTicks = 400)
    public static void aWallLaidOutAfterTheEarthworksKeepsItsColumn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ConstructionSettings.override(FAST);
        open(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Kept Columns", UUID.randomUUID());
        floor(helper, 1);
        for (int x = 2; x <= 7; x++) {
            for (int z = 3; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.DIRT);
            }
        }
        Citizen builder = builder(helper, kingdom);
        try {
            Construction work = lay(helper, kingdom, new BlockPos(2, 0, 3), new BlockPos(7, 0, 7), Earthwork.Mode.LEVEL, 0);
            // A wall is laid out through the plot after the work: its logs stand in place of the earth.
            BlockPos foot = helper.absolutePos(new BlockPos(5, 0, 5));
            java.util.List<io.github.sekerator.kingdomworld.core.road.RouteCell> cells = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                cells.add(new io.github.sekerator.kingdomworld.core.road.RouteCell(foot.getX() + 3 * i, foot.getY(), foot.getZ() + 20,
                        io.github.sekerator.kingdomworld.core.road.RouteCell.Kind.GROUND, 0, null));
            }
            cells.set(0, new io.github.sekerator.kingdomworld.core.road.RouteCell(foot.getX(), foot.getY(), foot.getZ(),
                    io.github.sekerator.kingdomworld.core.road.RouteCell.Kind.GROUND, 0, null));
            io.github.sekerator.kingdomworld.common.road.RoadWork.change(server, "test wall", state -> {
                io.github.sekerator.kingdomworld.core.road.Wall wall = new io.github.sekerator.kingdomworld.core.road.Wall(state.nextWallId(), 0,
                        kingdom.id(), io.github.sekerator.kingdomworld.core.road.Wall.WallClass.PALISADE,
                        io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(level), foot.getX(), foot.getZ() + 10, cells, java.util.List.of(), 0);
                for (int up = 1; up <= 4; up++) {
                    wall.markPlaced(foot.above(up).asLong());
                }
                state.putWall(wall);
                return io.github.sekerator.kingdomworld.core.kingdom.Outcome.DONE;
            });
            for (int up = 1; up <= 4; up++) {
                level.setBlockAndUpdate(foot.above(up), Blocks.OAK_LOG.defaultBlockState());
            }
            workFar(server, builder, work, 200);
            for (int up = 1; up <= 4; up++) {
                helper.assertTrue(level.getBlockState(foot.above(up)).is(Blocks.OAK_LOG), "The earthworks dug the log of the wall at " + foot.above(up));
            }
            helper.assertTrue(level.getBlockState(foot).is(Blocks.DIRT), "The ground under the wall was dug");
            helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(3, 1, 4))).isAir(), "The rest of the plot was not cut");
        } finally {
            io.github.sekerator.kingdomworld.common.road.RoadWork.state(server).forgetKingdom(kingdom.id());
            end(helper, kingdom, builder);
        }
        helper.succeed();
    }
}
