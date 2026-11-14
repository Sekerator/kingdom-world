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
}
