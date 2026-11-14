package io.github.sekerator.kingdomworld.common.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplate;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplates;
import io.github.sekerator.kingdomworld.common.construction.ConstructionSettings;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.construction.Scaffolds;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.ConstructionRules;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.construction.Scaffolding;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Game tests of the scaffolding of the builders: a builder near players puts up the walls and the roof of a house too high to
 * reach from the ground from scaffolds and leaves none behind; scaffolds go only on the land of the kingdom and never into a
 * block holding something; a cancelled construction takes its scaffolds down; a citizen found on top of a column nobody holds
 * (a body come back where it last stood after a restart) is set on the ground unharmed before the column comes down.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScaffoldingGameTests {
    private static final String EMPTY = "empty";
    /** Fast rules for tests: a block in two ticks, no falls. */
    private static final ConstructionRules FAST = new ConstructionRules(4, 64, 4.5, 12.0, 400, 2, 0.0, 48, 32, 48);
    /** The walls of the tall house, in blocks over its floor. */
    private static final int WALLS = 8;

    private ScaffoldingGameTests() {
    }

    private static JsonObject description(String name, int anchorX, int anchorZ) {
        return JsonParser.parseString("{\"name\": \"kingdomworld.building.small_house\", \"fallback_name\": \"" + name
                + "\", \"category\": \"housing\", \"type\": \"test\", \"icon\": \"minecraft:oak_planks\", \"anchor\": [" + anchorX + ", 0, "
                + anchorZ + "], \"entrance\": \"south\"}").getAsJsonObject();
    }

    /**
     * A tall house of 5 by 5: a floor of cobblestone, walls of planks {@link #WALLS} high with a doorway in the south and a
     * flat roof of planks over them, out of reach from the floor.
     */
    private static BuildingTemplate tallHouse(String name) {
        List<String> palette = List.of("minecraft:cobblestone", "minecraft:oak_planks", "minecraft:air");
        List<int[]> blocks = new ArrayList<>();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                blocks.add(new int[]{x, 0, z, 0});
                for (int y = 1; y <= WALLS; y++) {
                    boolean wall = x == 0 || x == 4 || z == 0 || z == 4;
                    boolean doorway = x == 2 && z == 4 && y <= 2;
                    blocks.add(new int[]{x, y, z, wall && !doorway ? 1 : 2});
                }
                blocks.add(new int[]{x, WALLS + 1, z, 1});
            }
        }
        return BuildingTemplates.addForTest(KingdomWorld.id("test_" + name), description(name, 2, 4),
                ConstructionGameTests.structure(5, WALLS + 2, 5, palette, blocks.toArray(new int[0][])));
    }

    private static Construction lay(GameTestHelper helper, Kingdom kingdom, BuildingTemplate template, BlockPos absolute) {
        KingdomWorldState.ConstructionChange change = ConstructionWork.lay(helper.getLevel(), null, kingdom.id(), template, absolute,
                Placement.NONE);
        if (change.construction().isEmpty()) {
            throw new IllegalStateException("The construction was refused: " + change.outcome());
        }
        return change.construction().get();
    }

    /**
     * Stone under a box, so that the ground of the test is the same whatever the world around it.
     */
    private static void ground(ServerLevel level, BlockPos from, BlockPos to) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(from.getX(), from.getY() - 1, from.getZ()), new BlockPos(to.getX(), from.getY() - 1,
                to.getZ()))) {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2 | 16);
        }
    }

    /**
     * Whether a scaffold stands anywhere in a box.
     */
    private static boolean anyScaffold(ServerLevel level, BlockPos from, BlockPos to) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (Scaffolds.is(level.getBlockState(pos))) {
                return true;
            }
        }
        return false;
    }

    private static void end(GameTestHelper helper, Kingdom kingdom, BlockPos from, BlockPos to, BuildingTemplate template, Citizen... citizens) {
        ConstructionSettings.override(null);
        CitizenGameTests.finish(helper, kingdom, citizens);
        GatheringGameTests.clear(helper.getLevel(), from, to);
        BuildingTemplates.removeForTest(template.id());
    }

    // ---------------------------------------------------------------- a roof out of reach

    @GameTest(template = EMPTY, batch = "kingdomworld_scaffold_roof", timeoutTicks = 9_000)
    public static void aBuilderFinishesARoofFromScaffoldsAndLeavesNoneBehind(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        level.setDayTime(1_000L);
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(15, WALLS + 4, 15));
        GatheringGameTests.clear(level, from, to);
        ground(level, from, to);
        ConstructionSettings.override(FAST);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Roofers", UUID.randomUUID());
        BuildingTemplate template = tallHouse("scaffold_roof");
        StorehouseInventory store = GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(0, 0, 4)));
        store.insertItem(0, new ItemStack(Items.COBBLESTONE, 30), false);
        store.insertItem(1, new ItemStack(Items.OAK_PLANKS, 64), false);
        store.insertItem(2, new ItemStack(Items.OAK_PLANKS, 64), false);
        store.insertItem(3, new ItemStack(Items.OAK_PLANKS, 40), false);
        WorldPos post = GatheringGameTests.post(helper, new BlockPos(0, 0, 0), "builder");
        Citizen builder = GatheringGameTests.worker(helper, kingdom, "builder", post, helper.absolutePos(new BlockPos(1, 0, 1)));
        // The house stands at 7..11, 7..11 with its doorway in the south.
        Construction construction = lay(helper, kingdom, template, helper.absolutePos(new BlockPos(9, 0, 11)));
        BlockPos roof = helper.absolutePos(new BlockPos(9, WALLS + 1, 9));
        boolean[] climbed = {false};
        helper.onEachTick(() -> climbed[0] |= construction.hasScaffolds());

        helper.succeedWhen(() -> {
            List<Building> buildings = KingdomWorldApi.state(server).construction().buildingsOf(kingdom.id());
            helper.assertTrue(!buildings.isEmpty(), "Not built yet: status " + construction.status() + ", done " + construction.doneCount() + "/"
                    + construction.steps() + ", worker " + builder.workStatus() + ", scaffolds " + construction.scaffolds().size());
            helper.assertTrue(level.getBlockState(roof).is(Blocks.OAK_PLANKS), "The middle of the roof is missing");
            helper.assertTrue(climbed[0], "The builder never stood on scaffolds");
            helper.assertTrue(!anyScaffold(level, from, to), "Scaffolds were left standing");
            helper.assertTrue(KingdomWorldApi.state(server).construction().leftScaffolds().isEmpty(), "Scaffolds are left to be taken down");
            helper.assertTrue(buildings.get(0).integrity() == 100, "The house is not whole");
            end(helper, kingdom, from, to, template, builder);
        });
    }

    // ---------------------------------------------------------------- where scaffolds may go

    @GameTest(template = EMPTY, batch = "kingdomworld_scaffold_places")
    public static void scaffoldsStayOnTheLandOfTheKingdomAndOutOfBlocksHoldingSomething(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Scaffolders", UUID.randomUUID());
        BlockPos throne = helper.absolutePos(new BlockPos(2, 0, 2));
        ChunkPos capital = new ChunkPos(throne);
        int y = throne.getY();
        // Across the chunk from the throne, so that the place of the test keeps the throne out.
        int x0 = throne.getX() <= capital.getMiddleBlockX() ? capital.getMaxBlockX() - 3 : capital.getMinBlockX() + 3;
        int edge = capital.getMaxBlockZ();
        BlockPos from = new BlockPos(x0 - 3, y, edge - 6);
        BlockPos to = new BlockPos(x0 + 3, y + 4, edge + 4);
        GatheringGameTests.clear(level, from, to);
        ground(level, from, to);
        // The land south of the capital is given up: the band around the site reaches into it.
        KingdomWorldApi.change(server, "test", state -> state.unclaim(null, kingdom.id(), Dimensions.id(level), ChunkKey.of(capital.x, capital.z + 1)));
        BuildingTemplate template = ConstructionGameTests.hut("scaffold_places");
        // The hut stands with its south side a block from the edge of the land.
        Construction construction = lay(helper, kingdom, template, new BlockPos(x0, y, edge - 1));
        BlockPos chest = new BlockPos(x0 - 2, y + 1, edge - 3);
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(chest) instanceof Container container) {
            container.setItem(0, new ItemStack(Items.APPLE, 2));
        }
        BlockPos onLand = new BlockPos(x0 + 2, y + 1, edge - 1);
        BlockPos foreign = new BlockPos(x0, y + 1, edge + 2);
        BlockPos beyond = new BlockPos(x0, y + 1, edge - 9);
        level.setBlockAndUpdate(beyond, Blocks.AIR.defaultBlockState());
        try {
            helper.assertTrue(Scaffolding.inBand(construction.box(), foreign.getX(), foreign.getY(), foreign.getZ()),
                    "The test wants a place in the band beyond the land");
            helper.assertTrue(Scaffolds.placeable(level, construction, onLand), "A free place on the land of the kingdom takes no scaffold");
            helper.assertTrue(!Scaffolds.placeable(level, construction, foreign), "A scaffold may go on the land of others");
            helper.assertTrue(!Scaffolds.put(level, construction, foreign) && level.getBlockState(foreign).isAir(),
                    "A scaffold was put up on the land of others");
            helper.assertTrue(!Scaffolds.placeable(level, construction, chest) && !Scaffolds.put(level, construction, chest),
                    "A scaffold may go into a chest");
            helper.assertTrue(level.getBlockState(chest).is(Blocks.CHEST) && level.getBlockEntity(chest) instanceof Container container
                    && container.getItem(0).is(Items.APPLE), "The chest or what it holds is gone");
            helper.assertTrue(!Scaffolds.put(level, construction, beyond) && level.getBlockState(beyond).isAir(),
                    "A scaffold was put up far from the site");
            helper.assertTrue(Scaffolds.put(level, construction, onLand) && Scaffolds.is(level.getBlockState(onLand)),
                    "No scaffold was put up on the land of the kingdom");
            helper.assertTrue(construction.scaffolds().contains(Scaffolding.pack(onLand.getX(), onLand.getY(), onLand.getZ())),
                    "The construction does not keep its scaffold");
            helper.assertTrue(construction.scaffolds().size() == 1, "Scaffolds refused are kept: " + construction.scaffolds().size());
            // A scaffold is nothing to keep: the construction breaks it with no drop and builds over it.
            helper.assertTrue(!ConstructionWork.isProtected(level, onLand, level.getBlockState(onLand)), "A scaffold counts as a protected block");
            helper.succeed();
        } finally {
            CitizenGameTests.finish(helper, kingdom);
            GatheringGameTests.clear(level, from, to);
            BuildingTemplates.removeForTest(template.id());
        }
    }

    // ---------------------------------------------------------------- a citizen on a column nobody holds

    @GameTest(template = EMPTY, batch = "kingdomworld_scaffold_restart", timeoutTicks = 200)
    public static void aCitizenOnAColumnNobodyHoldsIsSetOnTheGroundUnharmed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(12, 12, 12));
        GatheringGameTests.clear(level, from, to);
        ground(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Fallers", UUID.randomUUID());
        BuildingTemplate template = ConstructionGameTests.hut("scaffold_restart");
        Construction construction = lay(helper, kingdom, template, helper.absolutePos(new BlockPos(7, 0, 8)));
        // A column of eight scaffolds, as a builder left it before the server stopped, and a citizen on top of it.
        List<BlockPos> column = new ArrayList<>();
        for (int y = 0; y < 8; y++) {
            column.add(helper.absolutePos(new BlockPos(9, y, 8)));
        }
        for (BlockPos cell : column) {
            helper.assertTrue(Scaffolds.put(level, construction, cell), "No scaffold at " + GatheringGameTests.local(helper, cell));
        }
        Citizen citizen = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
        CitizenBodies.force(citizen.id(), true);
        CitizenEntity body = CitizenBodies.spawnAt(level, citizen, column.get(column.size() - 1).above());
        helper.assertTrue(body != null, "No body for the citizen");
        float health = body.getHealth();
        BlockPos base = column.get(0);

        helper.succeedWhen(() -> {
            for (BlockPos cell : column) {
                helper.assertTrue(level.getBlockState(cell).isAir(), "A scaffold still stands at " + GatheringGameTests.local(helper, cell));
            }
            helper.assertTrue(body.isAlive() && body.onGround(), "The citizen is not standing on the ground");
            helper.assertTrue(body.getY() < base.getY() + 0.5, "The citizen is not on the ground: " + body.getY());
            helper.assertTrue(body.getHealth() >= health, "The citizen was hurt by the fall: " + body.getHealth() + " of " + health);
            CitizenGameTests.finish(helper, kingdom, citizen);
            GatheringGameTests.clear(level, from, to);
            BuildingTemplates.removeForTest(template.id());
        });
    }

    // ---------------------------------------------------------------- a cancelled construction

    @GameTest(template = EMPTY, batch = "kingdomworld_scaffold_cancel", timeoutTicks = 200)
    public static void aCancelledConstructionTakesItsScaffoldsDown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(12, 6, 12));
        GatheringGameTests.clear(level, from, to);
        ground(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Cancellers", UUID.randomUUID());
        BuildingTemplate template = ConstructionGameTests.hut("scaffold_cancel");
        Construction construction = lay(helper, kingdom, template, helper.absolutePos(new BlockPos(7, 0, 8)));
        List<BlockPos> column = List.of(helper.absolutePos(new BlockPos(9, 0, 8)), helper.absolutePos(new BlockPos(9, 1, 8)),
                helper.absolutePos(new BlockPos(9, 2, 8)));
        for (BlockPos cell : column) {
            helper.assertTrue(Scaffolds.put(level, construction, cell), "No scaffold at " + GatheringGameTests.local(helper, cell));
        }
        helper.assertTrue(construction.scaffolds().size() == column.size(), "The construction does not keep its scaffolds");
        ConstructionWork.cancel(server, null, kingdom.id(), construction.id());
        String dimension = Dimensions.id(level);
        Set<Long> left = KingdomWorldApi.state(server).construction().leftScaffolds().getOrDefault(dimension, Set.of());
        helper.assertTrue(left.size() == column.size(), "The scaffolds of the cancelled construction were forgotten");

        helper.succeedWhen(() -> {
            for (BlockPos cell : column) {
                helper.assertTrue(level.getBlockState(cell).isAir(), "A scaffold still stands at " + GatheringGameTests.local(helper, cell));
            }
            helper.assertTrue(!KingdomWorldApi.state(server).construction().leftScaffolds().containsKey(dimension)
                    || KingdomWorldApi.state(server).construction().leftScaffolds().get(dimension).stream()
                    .noneMatch(cell -> column.stream().anyMatch(pos -> pos.asLong() == cell)), "The scaffolds are still kept to be taken down");
            CitizenGameTests.finish(helper, kingdom);
            GatheringGameTests.clear(level, from, to);
            BuildingTemplates.removeForTest(template.id());
        });
    }
}
