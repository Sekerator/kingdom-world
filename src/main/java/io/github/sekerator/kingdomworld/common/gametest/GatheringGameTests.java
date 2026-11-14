package io.github.sekerator.kingdomworld.common.gametest;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSimulation;
import io.github.sekerator.kingdomworld.common.citizen.work.GatheringSettings;
import io.github.sekerator.kingdomworld.common.citizen.work.MineOres;
import io.github.sekerator.kingdomworld.common.citizen.work.MinerBehavior;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.DeathRecord;
import io.github.sekerator.kingdomworld.core.citizen.GatheringRules;
import io.github.sekerator.kingdomworld.core.citizen.MineGoal;
import io.github.sekerator.kingdomworld.core.citizen.MinePlan;
import io.github.sekerator.kingdomworld.core.citizen.MineRules;
import io.github.sekerator.kingdomworld.core.citizen.PostYield;
import io.github.sekerator.kingdomworld.core.citizen.TaskRisks;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.state.KingdomRules;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Game tests of the gathering professions and work posts: felling, mining, fishing, herding and carrying near
 * players, tools, work and accidents far from players, and saving. Each test that founds a kingdom runs in a batch
 * of its own and clears the blocks it placed outside the test structure.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GatheringGameTests {
    private static final String EMPTY = "empty";

    private GatheringGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Places a work post and sets its profession.
     */
    static WorldPos post(GameTestHelper helper, BlockPos relative, String profession) {
        helper.setBlock(relative, ModRegistries.WORK_POST.get());
        WorldPos pos = CitizenGameTests.at(helper, relative.getX(), relative.getY(), relative.getZ());
        helper.assertTrue(PostWork.post(helper.getLevel().getServer(), pos).isPresent(), "The work post was not registered");
        KingdomWorldApi.change(helper.getLevel().getServer(), "test", state -> state.setPostProfession(null, pos, profession));
        return pos;
    }

    static WorldPos post(ServerLevel level, BlockPos absolute, String profession) {
        level.setBlockAndUpdate(absolute, ModRegistries.WORK_POST.get().defaultBlockState());
        WorldPos pos = new WorldPos(Dimensions.id(level), absolute.getX(), absolute.getY(), absolute.getZ());
        KingdomWorldApi.change(level.getServer(), "test", state -> state.setPostProfession(null, pos, profession));
        return pos;
    }

    /**
     * A citizen of the profession working at the post, with a body standing at {@code spot} (absolute).
     */
    static Citizen worker(GameTestHelper helper, Kingdom kingdom, String profession, WorldPos post, BlockPos spot) {
        MinecraftServer server = helper.getLevel().getServer();
        Citizen citizen = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
        KingdomWorldApi.change(server, "test", state -> state.setProfession(null, citizen.id(), profession));
        helper.assertTrue(KingdomWorldApi.state(server).citizens().assignWorkplace(citizen, post), "The worker got no seat at the post");
        CitizenBodies.force(citizen.id(), true);
        CitizenEntity body = CitizenBodies.spawnAt(helper.getLevel(), citizen, spot);
        helper.assertTrue(body != null, "No body for the worker");
        return citizen;
    }

    /**
     * A place in the coordinates of a test, for the messages of failures: the helper of the game turns its own answer half
     * round for a test that is not turned, so it is not used.
     */
    static BlockPos local(GameTestHelper helper, BlockPos absolute) {
        return absolute.subtract(helper.absolutePos(BlockPos.ZERO));
    }

    /**
     * The blocks of a layer of a test as rows of letters, north to south, for the messages of failures: "." air, "~" a fluid,
     * "#" a full block, otherwise the first letter of the block.
     */
    static String map(GameTestHelper helper, int fromX, int fromZ, int toX, int toZ, int y) {
        StringBuilder text = new StringBuilder();
        for (int z = fromZ; z <= toZ; z++) {
            text.append(z == fromZ ? "" : "/");
            for (int x = fromX; x <= toX; x++) {
                BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
                BlockState state = helper.getLevel().getBlockState(pos);
                if (state.isAir()) {
                    text.append('.');
                } else if (!state.getFluidState().isEmpty()) {
                    text.append('~');
                } else if (state.isCollisionShapeFullBlock(helper.getLevel(), pos)) {
                    text.append('#');
                } else {
                    text.append(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock()).getPath().charAt(0));
                }
            }
        }
        return text.toString();
    }

    static StorehouseInventory storehouse(ServerLevel level, BlockPos absolute) {
        level.setBlockAndUpdate(absolute, ModRegistries.STOREHOUSE.get().defaultBlockState());
        return KingdomSavedData.get(level.getServer()).storehouses()
                .get(new WorldPos(Dimensions.id(level), absolute.getX(), absolute.getY(), absolute.getZ()))
                .orElseThrow(() -> new IllegalStateException("The storehouse has no inventory"));
    }

    /** Blocks around a box cleared for a test within which lava and water could still flow into it. */
    private static final int FLUID_REACH = 8;
    /**
     * Blocks over a box within which water could still fall into it: the game clears the space some twenty blocks over the
     * template of a test, and the water of the aquifers around that shaft falls down it.
     */
    private static final int FLUID_ABOVE = 24;

    /**
     * Sets every block of a box (absolute, inclusive) to air: the test's own blocks are cleared before and after. The tests
     * stand deep under the land of an ordinary world, where the aquifers are lava and water: around a box under the sea level
     * the lava and water within {@value #FLUID_REACH} blocks, and {@value #FLUID_ABOVE} over it, turn to stone, so that none
     * flows in.
     */
    static void clear(ServerLevel level, BlockPos from, BlockPos to) {
        BlockPos low = new BlockPos(Math.min(from.getX(), to.getX()), Math.min(from.getY(), to.getY()), Math.min(from.getZ(), to.getZ()));
        BlockPos high = new BlockPos(Math.max(from.getX(), to.getX()), Math.max(from.getY(), to.getY()), Math.max(from.getZ(), to.getZ()));
        if (low.getY() < level.getSeaLevel()) {
            for (BlockPos pos : BlockPos.betweenClosed(low.offset(-FLUID_REACH, -FLUID_REACH, -FLUID_REACH),
                    high.offset(FLUID_REACH, FLUID_ABOVE, FLUID_REACH))) {
                if (level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.LiquidBlock && !inside(pos, low, high)) {
                    level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2 | 16);
                }
            }
        }
        for (BlockPos pos : BlockPos.betweenClosed(low, high)) {
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
        }
        // A container that goes drops what it held: the storehouses of a kingdom drop thousands of items, which would lie in
        // the world for five minutes and weigh on every test that measures ticks after this one.
        for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(from, to).inflate(2.0))) {
            item.discard();
        }
    }

    private static boolean inside(BlockPos pos, BlockPos low, BlockPos high) {
        return pos.getX() >= low.getX() && pos.getX() <= high.getX() && pos.getY() >= low.getY() && pos.getY() <= high.getY()
                && pos.getZ() >= low.getZ() && pos.getZ() <= high.getZ();
    }

    /**
     * Walls a box (absolute, inclusive) off the world around it: stone under it, over it and on its sides, air inside. The
     * framework clears and floors the space only a few blocks around the small template of these tests; a test using more
     * stands with the rest in the caves of the world deep below (lava, water, gravel, open drops), which differ from world
     * to world.
     */
    static void enclose(ServerLevel level, BlockPos from, BlockPos to) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(from.offset(-1, -1, -1), to.offset(1, 1, 1))) {
            boolean inside = pos.getX() >= from.getX() && pos.getX() <= to.getX() && pos.getY() >= from.getY() && pos.getY() <= to.getY()
                    && pos.getZ() >= from.getZ() && pos.getZ() <= to.getZ();
            BlockState wanted = inside ? air : stone;
            if (level.getBlockState(pos) != wanted) {
                level.setBlock(pos, wanted, 2 | 16);
            }
        }
    }

    static long produced(MinecraftServer server, Kingdom kingdom, String profession, String item) {
        return KingdomWorldApi.state(server).citizens().ledger().today(kingdom.id(), PostWork.gameDay(server)).counts()
                .getOrDefault(profession, Map.of()).getOrDefault(item, 0L);
    }

    static void end(GameTestHelper helper, Kingdom kingdom, Citizen citizen, BlockPos from, BlockPos to) {
        CitizenGameTests.finish(helper, kingdom, citizen);
        clear(helper.getLevel(), from, to);
        TestChunks.release(helper.getLevel(), from);
    }

    // ---------------------------------------------------------------- lumberjack

    /**
     * How high over its post the box of a lumberjack's test is cleared. A lumberjack looks at its zone, up to 32 blocks over the
     * post, column by column from the top down to the first ground, as under the open sky; the tests stand deep under the land,
     * where the shaft the game clears over a test ends in rock that would be the ground of every column.
     */
    static final int OPEN_ABOVE = 33;

    @GameTest(template = EMPTY, batch = "kingdomworld_work_lumberjack", timeoutTicks = 1200)
    public static void lumberjackFellsNaturalTreesAndSparesBuildings(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        level.setDayTime(1_000L);
        BlockPos from = helper.absolutePos(new BlockPos(-2, 0, -2));
        BlockPos to = helper.absolutePos(new BlockPos(9, OPEN_ABOVE, 6));
        clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Woodland", UUID.randomUUID());
        // A natural oak: four logs with leaves that grew there.
        for (int y = 0; y < 4; y++) {
            helper.setBlock(new BlockPos(5, y, 1), Blocks.OAK_LOG);
        }
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false).setValue(LeavesBlock.DISTANCE, 1);
        for (int x = 4; x <= 6; x++) {
            for (int z = 0; z <= 2; z++) {
                for (int y = 3; y <= 4; y++) {
                    if (!(x == 5 && z == 1 && y == 3)) {
                        helper.setBlock(new BlockPos(x, y, z), leaves);
                    }
                }
            }
        }
        // A pillar of a building: logs without leaves.
        for (int y = 0; y < 3; y++) {
            helper.setBlock(new BlockPos(8, y, 4), Blocks.OAK_LOG);
        }
        StorehouseInventory store = storehouse(level, helper.absolutePos(new BlockPos(0, 0, 3)));
        store.insertItem(0, new ItemStack(Items.IRON_AXE), false);
        store.insertItem(1, new ItemStack(Items.OAK_SAPLING), false);
        WorldPos postPos = post(helper, new BlockPos(0, 0, 0), "lumberjack");
        Citizen worker = worker(helper, kingdom, "lumberjack", postPos, helper.absolutePos(new BlockPos(3, 0, 1)));

        helper.succeedWhen(() -> {
            for (int y = 0; y < 4; y++) {
                helper.assertTrue(!helper.getBlockState(new BlockPos(5, y, 1)).is(Blocks.OAK_LOG), "The tree still stands");
            }
            helper.assertTrue(helper.getBlockState(new BlockPos(5, 0, 1)).is(Blocks.OAK_SAPLING), "No oak sapling was planted");
            for (int y = 0; y < 3; y++) {
                helper.assertTrue(helper.getBlockState(new BlockPos(8, y, 4)).is(Blocks.OAK_LOG), "A log of the building was felled");
            }
            helper.assertTrue(produced(server, kingdom, "lumberjack", "minecraft:oak_log") >= 4, "The logs were not counted");
            CitizenEntity body = CitizenBodies.body(worker.id());
            helper.assertTrue(body != null && body.getMainHandItem().is(Items.IRON_AXE) && body.getMainHandItem().getDamageValue() > 0,
                    "The axe was not taken or did not wear");
            end(helper, kingdom, worker, from, to);
        });
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_work_lumberjack_cabin", timeoutTicks = 1200)
    public static void lumberjackLeavesALogCabinThatTouchesATree(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        level.setDayTime(1_000L);
        BlockPos from = helper.absolutePos(new BlockPos(-2, 0, -2));
        BlockPos to = helper.absolutePos(new BlockPos(9, OPEN_ABOVE, 6));
        clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Log Cabins", UUID.randomUUID());
        for (int y = 0; y < 4; y++) {
            helper.setBlock(new BlockPos(5, y, 1), Blocks.OAK_LOG);
        }
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false).setValue(LeavesBlock.DISTANCE, 1);
        for (int x = 4; x <= 6; x++) {
            for (int z = 0; z <= 2; z++) {
                for (int y = 3; y <= 4; y++) {
                    if (!(x == 5 && z == 1 && y == 3)) {
                        helper.setBlock(new BlockPos(x, y, z), leaves);
                    }
                }
            }
        }
        // A wall of lying logs against the trunk and a corner post of the same wood under a plank roof, touching the
        // trunk diagonally.
        BlockState lying = Blocks.OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,
                net.minecraft.core.Direction.Axis.X);
        for (int x = 6; x <= 8; x++) {
            helper.setBlock(new BlockPos(x, 0, 1), lying);
            helper.setBlock(new BlockPos(x, 1, 1), lying);
        }
        for (int y = 0; y < 3; y++) {
            helper.setBlock(new BlockPos(6, y, 2), Blocks.OAK_LOG);
        }
        helper.setBlock(new BlockPos(6, 3, 2), Blocks.OAK_PLANKS);
        helper.setBlock(new BlockPos(7, 3, 2), Blocks.OAK_PLANKS);
        StorehouseInventory store = storehouse(level, helper.absolutePos(new BlockPos(0, 0, 3)));
        store.insertItem(0, new ItemStack(Items.IRON_AXE), false);
        store.insertItem(1, new ItemStack(Items.OAK_SAPLING), false);
        WorldPos postPos = post(helper, new BlockPos(0, 0, 0), "lumberjack");
        Citizen worker = worker(helper, kingdom, "lumberjack", postPos, helper.absolutePos(new BlockPos(3, 0, 1)));

        helper.succeedWhen(() -> {
            for (int y = 0; y < 4; y++) {
                helper.assertTrue(!helper.getBlockState(new BlockPos(5, y, 1)).is(Blocks.OAK_LOG), "The tree still stands");
            }
            for (int x = 6; x <= 8; x++) {
                helper.assertTrue(helper.getBlockState(new BlockPos(x, 0, 1)).is(Blocks.OAK_LOG)
                        && helper.getBlockState(new BlockPos(x, 1, 1)).is(Blocks.OAK_LOG), "A log of the wall was felled");
            }
            for (int y = 0; y < 3; y++) {
                helper.assertTrue(helper.getBlockState(new BlockPos(6, y, 2)).is(Blocks.OAK_LOG), "A log of the corner post was felled");
            }
            helper.assertTrue(helper.getBlockState(new BlockPos(6, 3, 2)).is(Blocks.OAK_PLANKS), "The roof was broken");
            helper.assertTrue(produced(server, kingdom, "lumberjack", "minecraft:oak_log") == 4,
                    "Expected exactly the 4 logs of the tree, got " + produced(server, kingdom, "lumberjack", "minecraft:oak_log"));
            end(helper, kingdom, worker, from, to);
        });
    }

    /**
     * A natural oak of four logs with a crown two blocks around its top, its leaves told how far they are from the logs.
     */
    private static void oak(GameTestHelper helper, int x, int z) {
        for (int y = 0; y < 4; y++) {
            helper.setBlock(new BlockPos(x, y, z), Blocks.OAK_LOG);
        }
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int y = 3; y <= 4; y++) {
                    BlockPos pos = new BlockPos(x + dx, y, z + dz);
                    if (!(dx == 0 && dz == 0 && y == 3) && helper.getBlockState(pos).isAir()) {
                        helper.setBlock(pos, leaves);
                    }
                }
            }
        }
    }

    /**
     * Lets the leaves of a box work out how far they are from the logs, as leaves that grew do.
     */
    private static void settleLeaves(GameTestHelper helper, BlockPos from, BlockPos to) {
        for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(from), helper.absolutePos(to))) {
            BlockState state = helper.getLevel().getBlockState(pos);
            if (state.getBlock() instanceof LeavesBlock) {
                helper.getLevel().scheduleTick(pos.immutable(), state.getBlock(), 1);
            }
        }
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_work_lumberjack_replant", timeoutTicks = 1600)
    public static void lumberjackReplantsATreeAndLeavesTheCrownOfItsNeighbour(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.setDayTime(1_000L);
        BlockPos from = helper.absolutePos(new BlockPos(-2, 0, -2));
        BlockPos to = helper.absolutePos(new BlockPos(14, OPEN_ABOVE, 6));
        clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Replanted Wood", UUID.randomUUID());
        // Two oaks whose crowns touch: the lumberjack fells the nearer one first.
        oak(helper, 5, 1);
        oak(helper, 10, 1);
        settleLeaves(helper, new BlockPos(2, 3, -2), new BlockPos(13, 4, 4));
        // The storehouse has the axe only; a sapling waits in the chest of the post, whatever the leaves drop.
        StorehouseInventory store = storehouse(level, helper.absolutePos(new BlockPos(0, 0, 4)));
        store.insertItem(0, new ItemStack(Items.IRON_AXE), false);
        helper.setBlock(new BlockPos(-1, 0, 0), Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(new BlockPos(-1, 0, 0));
        chest.setItem(0, new ItemStack(Items.OAK_SAPLING));
        WorldPos postPos = post(helper, new BlockPos(0, 0, 0), "lumberjack");
        Citizen worker = worker(helper, kingdom, "lumberjack", postPos, helper.absolutePos(new BlockPos(2, 0, 0)));

        helper.succeedWhen(() -> {
            for (int y = 1; y < 4; y++) {
                helper.assertTrue(!helper.getBlockState(new BlockPos(5, y, 1)).is(Blocks.OAK_LOG), "The nearer oak still stands");
            }
            helper.assertTrue(helper.getBlockState(new BlockPos(5, 0, 1)).is(Blocks.OAK_SAPLING), "No oak sapling where the oak stood");
            // The neighbour keeps its crown, so that it stays a tree the lumberjack fells in its turn.
            int leaves = 0;
            for (BlockPos side : List.of(new BlockPos(9, 3, 1), new BlockPos(11, 3, 1), new BlockPos(10, 3, 0), new BlockPos(10, 3, 2),
                    new BlockPos(10, 4, 1))) {
                if (helper.getBlockState(side).is(Blocks.OAK_LEAVES)) {
                    leaves++;
                }
            }
            helper.assertTrue(helper.getBlockState(new BlockPos(10, 3, 1)).is(Blocks.OAK_LOG) && leaves >= 4,
                    "The crown of the neighbouring oak was stripped: " + leaves + " leaves at its top");
            end(helper, kingdom, worker, from, to);
        });
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_work_lumberjack_grove", timeoutTicks = 2400)
    public static void lumberjackPlantsNewSaplingsOnlyOnFreeNaturalGround(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        level.setDayTime(1_000L);
        UUID king = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest New Grove", king);
        // New saplings need the open sky: the grove lies on a platform of grass over the surface of the world, on land of the
        // kingdom two chunks east of its capital, beyond the square of a village.
        ChunkPos capital = new ChunkPos(helper.absolutePos(new BlockPos(2, 0, 2)));
        for (int dx = 2; dx <= 3; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                ChunkPos chunk = new ChunkPos(capital.x + dx, capital.z + dz);
                helper.assertTrue(KingdomWorldApi.claimChunk(level, chunk, kingdom.id(), king).ok(), "No land for the grove at " + chunk);
                level.setChunkForced(chunk.x, chunk.z, true);
                level.getChunk(chunk.x, chunk.z);
            }
        }
        int minX = (capital.x + 2) << 4;
        int minZ = capital.z << 4;
        int top = level.getMinBuildHeight();
        for (int x = minX; x < minX + 32; x++) {
            for (int z = minZ; z < minZ + 32; z++) {
                top = Math.max(top, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z));
            }
        }
        int y = Math.min(top + 2, level.getMaxBuildHeight() - 40);
        BlockPos low = new BlockPos(minX + 2, y, minZ + 2);
        BlockPos high = new BlockPos(minX + 29, y, minZ + 29);
        for (BlockPos pos : BlockPos.betweenClosed(low, high)) {
            level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 2 | 16);
        }
        BlockPos center = new BlockPos(minX + 16, y + 1, minZ + 16);
        // Things of people near three spots of the grid: a plank of a house, a path, a field.
        BlockPos plank = center.offset(6, 0, 1);
        BlockPos path = center.offset(-5, -1, 2);
        BlockPos field = center.offset(1, -1, 7);
        level.setBlock(plank, Blocks.OAK_PLANKS.defaultBlockState(), 2 | 16);
        level.setBlock(path, Blocks.DIRT_PATH.defaultBlockState(), 2 | 16);
        level.setBlock(field, Blocks.FARMLAND.defaultBlockState(), 2 | 16);
        StorehouseInventory store = storehouse(level, center.offset(0, 0, -2));
        store.insertItem(0, new ItemStack(Items.IRON_AXE), false);
        store.insertItem(1, new ItemStack(Items.OAK_SAPLING, 16), false);
        WorldPos postPos = post(level, center, "lumberjack");
        KingdomWorldApi.change(server, "test", state -> state.setPostRadius(null, postPos, 10, 32));
        Citizen worker = worker(helper, kingdom, "lumberjack", postPos, center.offset(1, 0, 1));

        helper.succeedWhen(() -> {
            int planted = 0;
            for (BlockPos pos : BlockPos.betweenClosed(low.above(), high.above())) {
                if (!level.getBlockState(pos).is(Blocks.OAK_SAPLING)) {
                    continue;
                }
                planted++;
                helper.assertTrue(io.github.sekerator.kingdomworld.core.citizen.Groves.onGrid(center.getX(), center.getZ(), pos.getX(), pos.getZ()),
                        "A sapling off the grid at " + pos);
                for (BlockPos built : List.of(plank, path, field)) {
                    helper.assertTrue(Math.max(Math.abs(built.getX() - pos.getX()), Math.abs(built.getZ() - pos.getZ())) > 2,
                            "A sapling at " + pos + " next to " + level.getBlockState(built));
                }
            }
            helper.assertTrue(planted >= 3, "Only " + planted + " new saplings were planted");
            CitizenGameTests.finish(helper, kingdom, worker);
            for (BlockPos pos : BlockPos.betweenClosed(low, high.above(2))) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
            for (int dx = 2; dx <= 3; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    level.setChunkForced(capital.x + dx, capital.z + dz, false);
                }
            }
        });
    }

    // ---------------------------------------------------------------- miner

    /**
     * A block of rock around a miner's post for a test: the post stands one block from the southern edge of the capital
     * chunk, the chunk south of it is given up, and the mine is kept small (one level, three blocks of corridor each way,
     * branches of eight) so that a miner digs all of it within a test.
     */
    private record MineSite(Kingdom kingdom, WorldPos postPos, BlockPos stand, BlockPos from, BlockPos to, int edgeZ,
                            List<MinePlan.Cell> cells) {
        List<MinePlan.Cell> of(MinePlan.Kind kind) {
            List<MinePlan.Cell> found = new java.util.ArrayList<>();
            for (MinePlan.Cell cell : cells) {
                if (cell.kind() == kind) {
                    found.add(cell);
                }
            }
            return found;
        }

        boolean planned(BlockPos pos) {
            for (MinePlan.Cell cell : cells) {
                if (cell.x() == pos.getX() && cell.z() == pos.getZ()) {
                    return true;
                }
            }
            return false;
        }

        /**
         * A block of the wall of a cell that no tunnel of the plan goes through, with the next block behind it, or
         * {@code null} if every side of the cell is a tunnel.
         */
        BlockPos wall(MinePlan.Cell cell) {
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos side = at(cell).relative(direction);
                if (!planned(side) && !planned(side.relative(direction))) {
                    return side;
                }
            }
            return null;
        }
    }

    private static BlockPos at(MinePlan.Cell cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }

    private static boolean dug(ServerLevel level, MinePlan.Cell cell) {
        return level.getBlockState(at(cell)).isAir() && level.getBlockState(at(cell).above()).isAir();
    }

    private static MineSite mineSite(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, name, UUID.randomUUID());
        BlockPos throne = helper.absolutePos(new BlockPos(2, 0, 2));
        ChunkPos capital = new ChunkPos(throne);
        int y = throne.getY();
        int x0 = capital.getMiddleBlockX();
        int z0 = capital.getMaxBlockZ() - 1;
        BlockPos from = new BlockPos(x0 - 8, y - 1, z0 - 14);
        BlockPos to = new BlockPos(x0 + 8, y + 4, z0 + 3);
        KingdomWorldApi.change(server, "test", state -> state.unclaim(null, kingdom.id(), Dimensions.id(level),
                ChunkKey.of(capital.x, capital.z + 1)));
        // Solid rock with a floor and a roof, so that the caves of the world around the test do not matter; the throne
        // of the kingdom keeps its place.
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (pos.getX() != throne.getX() || pos.getZ() != throne.getZ() || pos.getY() < throne.getY()) {
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2 | 16);
            }
        }
        // Room for the miner to stand behind its post, and a storehouse beside it.
        BlockPos stand = new BlockPos(x0, y, z0 + 1);
        level.setBlockAndUpdate(stand, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(stand.above(), Blocks.AIR.defaultBlockState());
        GatheringRules rules = GatheringRules.DEFAULT;
        GatheringSettings.override(new GatheringRules(rules.defaultRadius(), rules.maxRadius(), rules.lumberjackDefaultRadius(),
                rules.lumberjackMaxRadius(), rules.maxSeats(), rules.maxTreeBlocks(), 1, rules.maxHerd(), rules.carrierRange(),
                rules.carrierItemsPerMinute(), 0.0, rules.risks()));
        GatheringSettings.overrideMine(new MineRules(3, 8, 24, 0, Map.of()));
        StorehouseInventory store = storehouse(level, new BlockPos(x0 + 1, y, z0 + 1));
        store.insertItem(0, new ItemStack(Items.STONE_PICKAXE), false);
        WorldPos postPos = post(level, new BlockPos(x0, y, z0), "miner");
        // The mine of the test stays on the level of its post, inside the rock laid for it, whatever the height of the world.
        KingdomWorldApi.change(server, "test", state -> state.setPostMineGoal(null, postPos, "", 0, false, y));
        List<MinePlan.Cell> cells = MinerBehavior.cells(level, PostWork.post(server, postPos).orElseThrow());
        for (MinePlan.Cell cell : cells) {
            helper.assertTrue(cell.x() >= from.getX() && cell.x() <= to.getX() && cell.z() >= from.getZ() && cell.z() <= to.getZ()
                    && cell.y() == y, "The mine of the test leaves its rock: " + cell);
        }
        return new MineSite(kingdom, postPos, stand, from, to, capital.getMaxBlockZ(), cells);
    }

    private static void endMine(GameTestHelper helper, MineSite site, Citizen worker) {
        GatheringSettings.override(null);
        GatheringSettings.overrideMine(null);
        end(helper, site.kingdom(), worker, site.from(), site.to());
    }

    private static String minerState(MinecraftServer server, MineSite site, Citizen worker) {
        CitizenEntity body = CitizenBodies.body(worker.id());
        return "worker " + worker.workStatus() + ", post " + PostWork.post(server, site.postPos()).map(WorkPost::status).orElse(null)
                + ", body " + (body == null ? "none" : body.blockPosition().toShortString() + " holding " + body.getMainHandItem());
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_work_miner", timeoutTicks = 3000)
    public static void minerDigsRockAndOreButNotChestsLandOfOthersOrLava(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        MineSite site = mineSite(helper, "GameTest Deep Halls");
        Kingdom kingdom = site.kingdom();
        List<MinePlan.Cell> corridor = site.of(MinePlan.Kind.CORRIDOR);
        List<MinePlan.Cell> branches = site.of(MinePlan.Kind.BRANCH);
        helper.assertTrue(corridor.size() >= 3 && !branches.isEmpty(), "The mine of the test has no corridor with branches");
        // Coal lies in the way of the corridor, a chest in the way of the first branch, lava behind the wall of the
        // corridor, and one branch runs up to the land given up.
        BlockPos coal = at(corridor.get(0));
        BlockPos chest = at(branches.get(0));
        MinePlan.Cell besideLava = null;
        BlockPos lava = null;
        for (int i = 1; i < corridor.size() && lava == null; i++) {
            besideLava = corridor.get(i);
            lava = site.wall(besideLava);
        }
        MinePlan.Cell edge = null;
        for (MinePlan.Cell cell : site.cells()) {
            if (cell.z() == site.edgeZ()) {
                edge = cell;
            }
        }
        helper.assertTrue(lava != null && edge != null, "The mine of the test has no wall for the lava or does not reach the edge of the land");
        BlockPos outside = at(edge).south();
        level.setBlockAndUpdate(coal, Blocks.COAL_ORE.defaultBlockState());
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, net.minecraft.core.Direction.NORTH));
        level.setBlockAndUpdate(lava, Blocks.LAVA.defaultBlockState());
        Citizen worker = worker(helper, kingdom, "miner", site.postPos(), site.stand());
        MinePlan.Cell entrance = site.cells().get(0);
        MinePlan.Cell dry = besideLava;
        MinePlan.Cell last = edge;
        BlockPos walled = lava;

        helper.succeedWhen(() -> {
            helper.assertTrue(dug(level, entrance), "The entrance of the mine is not dug: " + minerState(server, site, worker)
                    + ", entrance " + level.getBlockState(at(entrance)) + " over " + level.getBlockState(at(entrance).below()));
            helper.assertTrue(level.getBlockState(coal).isAir(), "The coal ore was not mined: " + minerState(server, site, worker));
            helper.assertTrue(dug(level, dry), "The tunnel next to the lava was not dug: " + minerState(server, site, worker));
            helper.assertTrue(level.getBlockState(walled).is(net.minecraftforge.common.Tags.Blocks.COBBLESTONE), "The lava was not walled off");
            helper.assertTrue(level.getBlockState(chest).is(Blocks.CHEST), "The miner broke a chest");
            helper.assertTrue(dug(level, last), "The branch to the edge of the land was not dug: " + minerState(server, site, worker));
            helper.assertTrue(level.getBlockState(outside).is(Blocks.STONE), "The miner dug on land that is not the kingdom's");
            helper.assertTrue(produced(server, kingdom, "miner", "minecraft:coal") >= 1, "The coal was not counted");
            endMine(helper, site, worker);
        });
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_work_miner_vein", timeoutTicks = 3000)
    public static void minerTakesTheVeinBesideItsCorridorButNotOreItCannotSee(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        MineSite site = mineSite(helper, "GameTest Iron Vein");
        Kingdom kingdom = site.kingdom();
        List<MinePlan.Cell> corridor = site.of(MinePlan.Kind.CORRIDOR);
        MinePlan.Cell beside = null;
        BlockPos first = null;
        for (int i = 0; i < corridor.size() && first == null; i++) {
            beside = corridor.get(i);
            first = site.wall(beside);
        }
        helper.assertTrue(first != null, "The corridor of the test has no wall for a vein");
        // The vein starts in the wall of the corridor, goes one block deeper into the rock and one block up there; one
        // more block of ore lies over the corridor behind a block of rock.
        BlockPos second = first.offset(first.getX() - beside.x(), 0, first.getZ() - beside.z());
        BlockPos third = second.above();
        BlockPos rock = first.above();
        BlockPos hidden = at(beside).above(3);
        for (BlockPos ore : List.of(first, second, third, hidden)) {
            level.setBlockAndUpdate(ore, Blocks.IRON_ORE.defaultBlockState());
        }
        Citizen worker = worker(helper, kingdom, "miner", site.postPos(), site.stand());
        MinePlan.Cell tunnel = beside;
        BlockPos start = first;

        helper.succeedWhen(() -> {
            helper.assertTrue(dug(level, tunnel), "The corridor beside the vein is not dug: " + minerState(server, site, worker));
            helper.assertTrue(level.getBlockState(start).isAir(), "The ore in the wall of the corridor was not taken: "
                    + minerState(server, site, worker));
            helper.assertTrue(level.getBlockState(second).isAir() && level.getBlockState(third).isAir(),
                    "The miner did not follow the vein from the ore it took: " + level.getBlockState(second) + ", " + level.getBlockState(third));
            helper.assertTrue(level.getBlockState(rock).is(Blocks.STONE), "The miner dug rock around the vein");
            helper.assertTrue(level.getBlockState(hidden).is(Blocks.IRON_ORE), "The miner took ore it could not see");
            helper.assertTrue(produced(server, kingdom, "miner", "minecraft:raw_iron") >= 3, "The ore of the vein was not counted: "
                    + produced(server, kingdom, "miner", "minecraft:raw_iron"));
            endMine(helper, site, worker);
        });
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_work_miner_goal")
    public static void theGoalOfAMineIsMetWhenTheStorehousesHoldTheAmount(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Iron Goal", UUID.randomUUID());
        StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 0);
        WorldPos postPos = post(helper, new BlockPos(1, 0, 0), "miner");
        try {
            WorkPost post = PostWork.post(server, postPos).orElseThrow();
            helper.assertTrue(post.mineGoal().equals(MineGoal.ANYTHING), "A new post does not dig anything: " + post.mineGoal());
            helper.assertTrue(MinerBehavior.depth(level, post) == Math.min(MineRules.ANYTHING_DEPTH, postPos.y()),
                    "Anything is not dug from the height of 16 or the level of a lower post");
            helper.assertTrue(MineOres.kinds().contains("forge:ores/iron"), "Iron is not among the kinds of ore: " + MineOres.kinds());
            helper.assertTrue(MineOres.drop(level, "forge:ores/iron") == Items.RAW_IRON, "Iron ore does not count as raw iron: "
                    + MineOres.drop(level, "forge:ores/iron"));
            helper.assertTrue(KingdomWorldApi.change(server, "test",
                    state -> state.setPostMineGoal(null, postPos, "forge:ores/iron", 8, true, 0)).ok(), "The goal was not set");
            int depth = GatheringSettings.mine().depth(post.mineGoal(), postPos.y(), level.getMinBuildHeight());
            helper.assertTrue(MinerBehavior.depth(level, post) == depth && depth <= Math.min(16, postPos.y()),
                    "The mine for iron is not at the depth of iron: " + MinerBehavior.depth(level, post));
            for (MinePlan.Cell cell : MinerBehavior.cells(level, post)) {
                helper.assertTrue(cell.kind() == MinePlan.Kind.STAIR || cell.y() <= depth, "A tunnel lies over the working depth: " + cell);
            }

            helper.assertTrue(MinerBehavior.stock(level, post) == 0 && !post.mineGoal().metBy(MinerBehavior.stock(level, post)),
                    "The goal is met with empty storehouses");
            store.insertItem(0, new ItemStack(Items.RAW_IRON, 5), false);
            store.insertItem(1, new ItemStack(Items.IRON_INGOT, 32), false);
            helper.assertTrue(MinerBehavior.stock(level, post) == 5 && !post.mineGoal().metBy(5), "Five of eight met the goal, or ingots counted");
            store.insertItem(2, new ItemStack(Items.RAW_IRON, 3), false);
            helper.assertTrue(MinerBehavior.stock(level, post) == 8 && post.mineGoal().metBy(MinerBehavior.stock(level, post)),
                    "The goal is not met with the amount in the storehouses: " + MinerBehavior.stock(level, post));
            // The stock falls below the amount: the goal stands again.
            store.extractItem(2, 1, false);
            helper.assertTrue(!post.mineGoal().metBy(MinerBehavior.stock(level, post)), "The goal stays met with less than the amount");
            // Without a limit the goal is never met.
            KingdomWorldApi.change(server, "test", state -> state.setPostMineGoal(null, postPos, "forge:ores/iron", 0, true, 0));
            helper.assertTrue(!post.mineGoal().metBy(MinerBehavior.stock(level, post)), "A goal without a limit was met");
        } finally {
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- fisher

    @GameTest(template = EMPTY, batch = "kingdomworld_work_fisher", timeoutTicks = 1400)
    public static void fisherCatchesFromTheWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        level.setDayTime(1_000L);
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(7, 3, 4));
        clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Fishermen", UUID.randomUUID());
        for (int x = 4; x <= 6; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.WATER);
            }
        }
        helper.setBlock(new BlockPos(3, 0, 1), Blocks.STONE);
        StorehouseInventory store = storehouse(level, helper.absolutePos(new BlockPos(0, 0, 3)));
        store.insertItem(0, new ItemStack(Items.FISHING_ROD), false);
        WorldPos postPos = post(helper, new BlockPos(0, 0, 0), "fisher");
        Citizen worker = worker(helper, kingdom, "fisher", postPos, helper.absolutePos(new BlockPos(3, 1, 1)));

        helper.succeedWhen(() -> {
            long caught = KingdomWorldApi.state(server).citizens().ledger().today(kingdom.id(), PostWork.gameDay(server)).total("fisher");
            helper.assertTrue(caught >= 1, "Nothing was caught yet");
            CitizenEntity body = CitizenBodies.body(worker.id());
            helper.assertTrue(body != null && body.getMainHandItem().is(Items.FISHING_ROD) && body.getMainHandItem().getDamageValue() > 0,
                    "The rod did not wear");
            end(helper, kingdom, worker, from, to);
        });
    }

    // ---------------------------------------------------------------- herder

    private static void pen(GameTestHelper helper) {
        for (int i = -1; i <= 7; i++) {
            for (BlockPos pos : List.of(new BlockPos(i, 0, -1), new BlockPos(i, 0, 7), new BlockPos(-1, 0, i), new BlockPos(7, 0, i))) {
                helper.setBlock(pos, Blocks.OAK_FENCE);
            }
        }
    }

    private static Cow cow(GameTestHelper helper, int x, int z, boolean baby, String name) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(x, 0, z));
        if (baby) {
            cow.setAge(-24_000);
        }
        if (name != null) {
            cow.setCustomName(Component.literal(name));
        }
        return cow;
    }

    private static List<Cow> cows(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(Cow.class, new AABB(helper.absolutePos(new BlockPos(-2, -1, -2)),
                helper.absolutePos(new BlockPos(8, 3, 8))), Animal::isAlive);
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_work_herder_cull", timeoutTicks = 1600)
    public static void herderCullsGrownAnimalsBeyondTheHerd(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        level.setDayTime(1_000L);
        BlockPos from = helper.absolutePos(new BlockPos(-2, 0, -2));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        // The pen and the storehouse reach beyond the space the framework clears: walled off the caves of the world.
        enclose(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Pastures", UUID.randomUUID());
        pen(helper);
        Cow baby = cow(helper, 5, 5, true, null);
        Cow named = cow(helper, 5, 1, false, "Burenka");
        for (int i = 0; i < 4; i++) {
            cow(helper, 3 + i % 2, 3 + i / 2, false, null);
        }
        StorehouseInventory store = storehouse(level, helper.absolutePos(new BlockPos(6, 0, 6)));
        store.insertItem(0, new ItemStack(Items.IRON_SWORD), false);
        WorldPos postPos = post(helper, new BlockPos(0, 0, 0), "herder");
        KingdomWorldApi.change(server, "test", state -> state.setPostAnimal(null, postPos, "minecraft:cow"));
        KingdomWorldApi.change(server, "test", state -> state.setPostHerdSize(null, postPos, 3, 64));
        Citizen worker = worker(helper, kingdom, "herder", postPos, helper.absolutePos(new BlockPos(1, 0, 1)));

        helper.succeedWhen(() -> {
            List<Cow> left = cows(helper);
            helper.assertTrue(baby.isAlive(), "The calf was culled");
            helper.assertTrue(named.isAlive(), "A cow with a name was culled");
            helper.assertTrue(left.size() <= 3, "The herd is still larger than its size: " + left.size());
            helper.assertTrue(left.size() == 3, "The herder culled too many: " + left.size());
            end(helper, kingdom, worker, from, to);
            left.forEach(cow -> cow.discard());
            level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(from, to).inflate(2)).forEach(e -> e.discard());
        });
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_work_herder_drops", timeoutTicks = 1600)
    public static void herderPicksUpOnlyTheDropsOfItsOwnCull(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        level.setDayTime(1_000L);
        BlockPos from = helper.absolutePos(new BlockPos(-2, 0, -2));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        // The far side of the pen and the storehouse lie beyond the space the framework clears: without walls the lava or the
        // gravel of the caves around reached in, and the herder died in them before it culled.
        enclose(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Fair Butchers", UUID.randomUUID());
        pen(helper);
        for (int i = 0; i < 3; i++) {
            cow(helper, 3 + i, 3, false, null);
        }
        // Things a player threw into the pen, all over it.
        List<net.minecraft.world.entity.item.ItemEntity> thrown = new java.util.ArrayList<>();
        for (int x = 1; x <= 5; x += 2) {
            for (int z = 1; z <= 5; z += 2) {
                net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(level,
                        helper.absolutePos(new BlockPos(x, 0, z)).getX() + 0.5, helper.absolutePos(BlockPos.ZERO).getY() + 0.1,
                        helper.absolutePos(new BlockPos(x, 0, z)).getZ() + 0.5, new ItemStack(Items.DIAMOND));
                item.setUnlimitedLifetime();
                item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                level.addFreshEntity(item);
                thrown.add(item);
            }
        }
        StorehouseInventory store = storehouse(level, helper.absolutePos(new BlockPos(6, 0, 6)));
        store.insertItem(0, new ItemStack(Items.IRON_SWORD), false);
        WorldPos postPos = post(helper, new BlockPos(0, 0, 0), "herder");
        KingdomWorldApi.change(server, "test", state -> state.setPostAnimal(null, postPos, "minecraft:cow"));
        KingdomWorldApi.change(server, "test", state -> state.setPostHerdSize(null, postPos, 2, 64));
        Citizen worker = worker(helper, kingdom, "herder", postPos, helper.absolutePos(new BlockPos(1, 0, 1)));

        helper.succeedWhen(() -> {
            helper.assertTrue(cows(helper).size() == 2, "The herder did not cull the third cow");
            helper.assertTrue(produced(server, kingdom, "herder", "minecraft:beef") >= 1, "The beef of the culled cow was not picked up");
            for (net.minecraft.world.entity.item.ItemEntity item : thrown) {
                helper.assertTrue(item.isAlive() && item.getItem().is(Items.DIAMOND), "The herder picked up a diamond a player threw");
            }
            helper.assertTrue(produced(server, kingdom, "herder", "minecraft:diamond") == 0, "A diamond was counted as the herder's");
            end(helper, kingdom, worker, from, to);
            cows(helper).forEach(cow -> cow.discard());
            level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(from, to).inflate(2)).forEach(e -> e.discard());
        });
    }

    @GameTest(template = EMPTY)
    public static void herderNeverCullsPetsMountsOrMarkedAnimals(GameTestHelper helper) {
        net.minecraft.world.entity.animal.Wolf pet = helper.spawn(EntityType.WOLF, new BlockPos(1, 0, 1));
        pet.setTame(true);
        pet.setOwnerUUID(UUID.randomUUID());
        net.minecraft.world.entity.animal.Wolf wild = helper.spawn(EntityType.WOLF, new BlockPos(2, 0, 1));
        net.minecraft.world.entity.animal.horse.Horse mount = helper.spawn(EntityType.HORSE, new BlockPos(3, 0, 1));
        mount.setTamed(true);
        mount.setOwnerUUID(UUID.randomUUID());
        net.minecraft.world.entity.animal.horse.Horse stray = helper.spawn(EntityType.HORSE, new BlockPos(4, 0, 1));
        Cow named = cow(helper, 1, 3, false, "Zorka");
        Cow calf = cow(helper, 2, 3, true, null);
        Cow led = cow(helper, 3, 3, false, null);
        led.setLeashedTo(wild, true);
        Cow plain = cow(helper, 4, 3, false, null);
        try {
            helper.assertTrue(!io.github.sekerator.kingdomworld.common.citizen.work.HerderBehavior.mayCull(pet), "A tamed wolf may be culled");
            helper.assertTrue(!io.github.sekerator.kingdomworld.common.citizen.work.HerderBehavior.mayCull(mount), "A tamed horse may be culled");
            helper.assertTrue(!io.github.sekerator.kingdomworld.common.citizen.work.HerderBehavior.mayCull(named), "A named cow may be culled");
            helper.assertTrue(!io.github.sekerator.kingdomworld.common.citizen.work.HerderBehavior.mayCull(calf), "A calf may be culled");
            helper.assertTrue(!io.github.sekerator.kingdomworld.common.citizen.work.HerderBehavior.mayCull(led), "A cow on a lead may be culled");
            helper.assertTrue(io.github.sekerator.kingdomworld.common.citizen.work.HerderBehavior.mayCull(plain), "A plain cow may not be culled");
            helper.assertTrue(io.github.sekerator.kingdomworld.common.citizen.work.HerderBehavior.mayCull(wild), "A wild wolf may not be culled");
            helper.assertTrue(io.github.sekerator.kingdomworld.common.citizen.work.HerderBehavior.mayCull(stray), "A wild horse may not be culled");
            helper.assertTrue(io.github.sekerator.kingdomworld.common.citizen.work.HerderBehavior.isSomebodys(pet)
                    && io.github.sekerator.kingdomworld.common.citizen.work.HerderBehavior.isSomebodys(mount)
                    && !io.github.sekerator.kingdomworld.common.citizen.work.HerderBehavior.isSomebodys(plain), "Pets are not told from the herd");
        } finally {
            led.dropLeash(true, false);
            for (Animal animal : List.of(pet, wild, mount, stray, named, calf, led, plain)) {
                animal.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_work_herder_breed", timeoutTicks = 1200)
    public static void herderBreedsTheHerdWithFeed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        level.setDayTime(1_000L);
        BlockPos from = helper.absolutePos(new BlockPos(-2, 0, -2));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        // The pen and the storehouse reach beyond the space the framework clears: walled off the caves of the world.
        enclose(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Cowsheds", UUID.randomUUID());
        pen(helper);
        Cow first = cow(helper, 3, 3, false, null);
        Cow second = cow(helper, 4, 3, false, null);
        StorehouseInventory store = storehouse(level, helper.absolutePos(new BlockPos(6, 0, 6)));
        store.insertItem(0, new ItemStack(Items.WHEAT, 8), false);
        WorldPos postPos = post(helper, new BlockPos(0, 0, 0), "herder");
        KingdomWorldApi.change(server, "test", state -> state.setPostAnimal(null, postPos, "minecraft:cow"));
        KingdomWorldApi.change(server, "test", state -> state.setPostHerdSize(null, postPos, 6, 64));
        Citizen worker = worker(helper, kingdom, "herder", postPos, helper.absolutePos(new BlockPos(1, 0, 1)));

        helper.succeedWhen(() -> {
            List<Cow> herd = cows(helper);
            boolean bred = herd.stream().anyMatch(Animal::isBaby) || first.isInLove() || second.isInLove();
            helper.assertTrue(bred, "The herder did not breed the cows");
            helper.assertTrue(CitizenGameTests.count(store, Items.WHEAT) < 8, "No feed was taken");
            end(helper, kingdom, worker, from, to);
            herd.forEach(cow -> cow.discard());
        });
    }

    // ---------------------------------------------------------------- carrier

    @GameTest(template = EMPTY, batch = "kingdomworld_work_carrier", timeoutTicks = 1200)
    public static void carrierBringsGoodsFromPostsToTheStorehouse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.setDayTime(1_000L);
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(9, 2, 5));
        clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Porters", UUID.randomUUID());
        post(helper, new BlockPos(7, 0, 0), "fisher");
        helper.setBlock(new BlockPos(8, 0, 0), Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(8, 0, 0)));
        chest.setItem(0, new ItemStack(Items.COD, 20));
        storehouse(level, helper.absolutePos(new BlockPos(0, 0, 4)));
        WorldPos carrierPost = post(helper, new BlockPos(0, 0, 0), "carrier");
        Citizen worker = worker(helper, kingdom, "carrier", carrierPost, helper.absolutePos(new BlockPos(1, 0, 1)));

        helper.succeedWhen(() -> {
            helper.assertTrue(chest.isEmpty(), "The container of the post was not emptied");
            // The carrier takes the goods to the storehouse nearest to the post; any storehouse of the kingdom counts.
            long stored = io.github.sekerator.kingdomworld.common.storage.KingdomStorage.count(level.getServer(), kingdom.id(),
                    stack -> stack.is(Items.COD));
            helper.assertTrue(stored >= 20, "The goods did not reach a storehouse: " + stored);
            end(helper, kingdom, worker, from, to);
        });
    }

    // ---------------------------------------------------------------- work far from players

    static GatheringRules safeRules(double deathChance) {
        GatheringRules rules = GatheringRules.DEFAULT;
        return new GatheringRules(rules.defaultRadius(), rules.maxRadius(), rules.lumberjackDefaultRadius(), rules.lumberjackMaxRadius(),
                rules.maxSeats(), rules.maxTreeBlocks(), rules.mineLevels(), rules.maxHerd(), rules.carrierRange(),
                rules.carrierItemsPerMinute(), deathChance > 0 ? 1.0 : 0.0,
                Map.of("miner", new TaskRisks.Risk(0.0, deathChance)));
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_work_abstract")
    public static void workersFarFromPlayersGatherWearAndReplaceTools(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Far Mines", UUID.randomUUID());
        StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 0);
        WorldPos postPos = post(helper, new BlockPos(1, 0, 0), "miner");
        Citizen miner = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
        GatheringSettings.override(safeRules(0.0));
        try {
            KingdomWorldApi.change(server, "test", state -> state.setProfession(null, miner.id(), "miner"));
            KingdomWorldApi.state(server).citizens().assignWorkplace(miner, postPos);
            WorkPost post = PostWork.post(server, postPos).orElseThrow();
            post.setYield(new PostYield(Map.of("minecraft:cobblestone", 480.0), Map.of(), 480.0, true));

            // Without a pickaxe the miner stands idle and says why.
            CitizenSimulation.step(server, kingdom.id());
            helper.assertTrue(miner.workStatus() == WorkStatus.NO_TOOL, "Expected no tool, got " + miner.workStatus());
            helper.assertTrue(post.status() == WorkStatus.NO_TOOL, "The post does not show the reason: " + post.status());

            // The most worn pickaxe is taken first; it breaks during the step and the next one is taken in the step after.
            ItemStack worn = new ItemStack(Items.STONE_PICKAXE);
            worn.setDamageValue(worn.getMaxDamage() - 1);
            store.insertItem(1, worn, false);
            store.insertItem(2, new ItemStack(Items.STONE_PICKAXE), false);
            CitizenSimulation.step(server, kingdom.id());
            helper.assertTrue(miner.workStatus() == WorkStatus.WORKING, "The miner does not work: " + miner.workStatus());
            int cobblestone = CitizenGameTests.count(store, Items.COBBLESTONE);
            helper.assertTrue(cobblestone >= 15, "Too little was gathered far from players: " + cobblestone);
            ItemStack held = KingdomSavedData.get(server).gear().get(miner.id(), EquipmentSlot.MAINHAND);
            helper.assertTrue(held.isEmpty(), "The worn pickaxe did not break: " + held);
            helper.assertTrue(CitizenGameTests.count(store, Items.STONE_PICKAXE) == 1, "The fresh pickaxe was taken before the worn one");
            CitizenSimulation.step(server, kingdom.id());
            held = KingdomSavedData.get(server).gear().get(miner.id(), EquipmentSlot.MAINHAND);
            helper.assertTrue(held.is(Items.STONE_PICKAXE) && held.getDamageValue() > 0, "The next pickaxe was not taken or did not wear");
            helper.assertTrue(CitizenGameTests.count(store, Items.STONE_PICKAXE) == 0, "The pickaxe is still in the storehouse");
            helper.assertTrue(produced(server, kingdom, "miner", "minecraft:cobblestone") >= cobblestone, "Production was not counted");
            helper.assertTrue(post.producedOn(PostWork.gameDay(server)) >= cobblestone, "The post did not count its production");
        } finally {
            GatheringSettings.override(null);
            CitizenGameTests.finish(helper, kingdom, miner);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_work_accident")
    public static void deadlyAccidentsFarFromPlayersGoToTheChronicle(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Collapsed Mine", UUID.randomUUID());
        StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 0);
        store.insertItem(0, new ItemStack(Items.IRON_PICKAXE), false);
        WorldPos postPos = post(helper, new BlockPos(1, 0, 0), "miner");
        Citizen miner = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
        GatheringSettings.override(safeRules(1.0));
        try {
            KingdomWorldApi.change(server, "test", state -> state.setProfession(null, miner.id(), "miner"));
            KingdomWorldApi.state(server).citizens().assignWorkplace(miner, postPos);
            PostWork.post(server, postPos).orElseThrow()
                    .setYield(new PostYield(Map.of("minecraft:cobblestone", 100.0), Map.of(), 100.0, true));
            CitizenSimulation.step(server, kingdom.id());
            helper.assertTrue(KingdomWorldApi.state(server).citizens().get(miner.id()).isEmpty(), "The miner survived a certain accident");
            List<DeathRecord> chronicle = KingdomWorldApi.state(server).citizens().registry().chronicle(kingdom.id());
            helper.assertTrue(!chronicle.isEmpty() && chronicle.get(0).cause() == CauseOfDeath.MINE,
                    "The chronicle does not say the miner died in the mine");
            helper.assertTrue(CitizenGameTests.count(store, Items.IRON_PICKAXE) == 1, "The pickaxe of the dead miner did not come back");
            helper.assertTrue(PostWork.post(server, postPos).orElseThrow().workers().isEmpty(), "The dead miner still has a seat");
        } finally {
            GatheringSettings.override(null);
            CitizenGameTests.finish(helper, kingdom, miner);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- saved data

    @GameTest(template = EMPTY)
    public static void postsProductionAndGearSurviveSaving(GameTestHelper helper) {
        KingdomSavedData data = new KingdomSavedData();
        CompoundTag kingdomTag;
        io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult result = data.state().found(UUID.randomUUID(), "Saved Posts",
                0x123456, UUID.randomUUID(), new Capital("minecraft:overworld", 8, 64, 8), 1L, KingdomRules.DEFAULT);
        Kingdom kingdom = ((io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult.Created) result).kingdom();
        WorldPos postPos = new WorldPos("minecraft:overworld", 10, 64, 10);
        data.state().citizens().addSite(postPos, io.github.sekerator.kingdomworld.core.citizen.SiteKinds.WORK_POST, kingdom.id());
        data.state().setPostProfession(null, postPos, "lumberjack");
        data.state().setPostSeats(null, postPos, 2, 4);
        Citizen citizen = new Citizen(UUID.randomUUID(), kingdom.id(), 3, io.github.sekerator.kingdomworld.core.citizen.Gender.MALE, 0,
                0L, postPos);
        data.state().citizens().add(citizen);
        data.state().citizens().setProfession(citizen, "lumberjack");
        data.state().citizens().assignWorkplace(citizen, postPos);
        citizen.setWorkStatus(WorkStatus.NO_TREES);
        WorkPost post = data.state().citizens().posts().get(postPos).orElseThrow();
        post.setYield(new PostYield(Map.of("minecraft:oak_log", 40.0), Map.of(), 40.0, true));
        post.addProduced(5L, 12L);
        data.state().citizens().ledger().record(kingdom.id(), 5L, "lumberjack", "minecraft:oak_log", 12L);
        ItemStack axe = new ItemStack(Items.IRON_AXE);
        axe.setDamageValue(17);
        data.gear().set(citizen.id(), EquipmentSlot.MAINHAND, axe);
        // A mine with a goal: a kind of ore, an amount and a depth set by hand.
        WorldPos minePos = new WorldPos("minecraft:overworld", 12, 64, 12);
        data.state().citizens().addSite(minePos, io.github.sekerator.kingdomworld.core.citizen.SiteKinds.WORK_POST, kingdom.id());
        data.state().setPostProfession(null, minePos, "miner");
        data.state().setPostMineGoal(null, minePos, "forge:ores/iron", 64, false, -20);

        CompoundTag saved = data.save(new CompoundTag());
        KingdomSavedData reloaded = KingdomSavedData.load(saved);
        helper.assertTrue(!reloaded.isReadOnly(), "Saved posts could not be read back: " + reloaded.readOnlyReason());
        WorkPost back = reloaded.state().citizens().posts().get(postPos).orElse(null);
        helper.assertTrue(back != null && back.professionId().equals("lumberjack") && back.seats() == 2, "The post lost its settings");
        helper.assertTrue(back.workers().contains(citizen.id()), "The post lost its worker");
        helper.assertTrue(back.mineGoal().equals(MineGoal.ANYTHING), "A post without a goal came back with one: " + back.mineGoal());
        WorkPost mine = reloaded.state().citizens().posts().get(minePos).orElse(null);
        helper.assertTrue(mine != null && mine.mineGoal().equals(new MineGoal("forge:ores/iron", 64, false, -20)),
                "The mine lost its goal: " + (mine == null ? null : mine.mineGoal()));
        helper.assertTrue(back.yield().measured() && back.producedOn(5L) == 12L, "The post lost its yield or production");
        helper.assertTrue(reloaded.state().citizens().get(citizen.id()).orElseThrow().workStatus() == WorkStatus.NO_TREES,
                "The citizen lost the state of its work");
        helper.assertTrue(reloaded.state().citizens().ledger().today(kingdom.id(), 5L).total("lumberjack") == 12L,
                "The record of production was lost");
        helper.assertTrue(reloaded.gear().get(citizen.id(), EquipmentSlot.MAINHAND).getDamageValue() == 17, "The axe was lost");

        // Format 3 (stage 2a) is upgraded: no posts, no production, no gear yet.
        kingdomTag = saved.getList("kingdoms", 10).getCompound(0).copy();
        CompoundTag population = kingdomTag.getCompound("population");
        population.remove("posts");
        population.remove("production");
        ListTag sites = new ListTag();
        population.put("sites", sites);
        ListTag citizens = population.getList("citizens", 10);
        for (int i = 0; i < citizens.size(); i++) {
            citizens.getCompound(i).remove("workStatus");
            citizens.getCompound(i).remove("workplace");
            citizens.getCompound(i).putString("profession", "farmer");
        }
        ListTag kingdoms = new ListTag();
        kingdoms.add(kingdomTag);
        CompoundTag old = new CompoundTag();
        old.putInt("formatVersion", 3);
        old.put("kingdoms", kingdoms);
        old.put("storehouses", new ListTag());
        KingdomSavedData upgraded = KingdomSavedData.load(old);
        helper.assertTrue(!upgraded.isReadOnly(), "Format 3 data became read-only: " + upgraded.readOnlyReason());
        helper.assertTrue(upgraded.isDirty(), "Upgraded data is not marked for saving");
        helper.assertTrue(upgraded.state().citizens().get(citizen.id()).map(c -> c.workStatus() == WorkStatus.NONE).orElse(false),
                "The citizen was lost in the upgrade");
        helper.assertTrue(upgraded.save(new CompoundTag()).getInt("formatVersion") == KingdomSavedData.FORMAT_VERSION, "Saved in the old format");

        // A broken post makes the data read-only instead of being dropped.
        CompoundTag broken = saved.copy();
        broken.getList("kingdoms", 10).getCompound(0).getCompound("population").getList("posts", 10).getCompound(0).putInt("radius", 0);
        helper.assertTrue(KingdomSavedData.load(broken).isReadOnly(), "An unreadable post did not make the data read-only");
        helper.succeed();
    }
}
