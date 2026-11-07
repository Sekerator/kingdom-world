package io.github.sekerator.kingdomworld.common.gametest;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSimulation;
import io.github.sekerator.kingdomworld.common.citizen.work.GatheringSettings;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.DeathRecord;
import io.github.sekerator.kingdomworld.core.citizen.GatheringRules;
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

    static StorehouseInventory storehouse(ServerLevel level, BlockPos absolute) {
        level.setBlockAndUpdate(absolute, ModRegistries.STOREHOUSE.get().defaultBlockState());
        return KingdomSavedData.get(level.getServer()).storehouses()
                .get(new WorldPos(Dimensions.id(level), absolute.getX(), absolute.getY(), absolute.getZ()))
                .orElseThrow(() -> new IllegalStateException("The storehouse has no inventory"));
    }

    /**
     * Sets every block of a box (absolute, inclusive) to air: the test's own blocks are cleared before and after.
     */
    static void clear(ServerLevel level, BlockPos from, BlockPos to) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
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
    }

    // ---------------------------------------------------------------- lumberjack

    @GameTest(template = EMPTY, batch = "kingdomworld_work_lumberjack", timeoutTicks = 1200)
    public static void lumberjackFellsNaturalTreesAndSparesBuildings(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        level.setDayTime(1_000L);
        BlockPos from = helper.absolutePos(new BlockPos(-2, 0, -2));
        BlockPos to = helper.absolutePos(new BlockPos(9, 8, 6));
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
        BlockPos to = helper.absolutePos(new BlockPos(9, 8, 6));
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

    // ---------------------------------------------------------------- miner

    @GameTest(template = EMPTY, batch = "kingdomworld_work_miner", timeoutTicks = 1600)
    public static void minerDigsRockAndOreButNotChestsLandOfOthersOrLava(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Deep Halls", UUID.randomUUID());
        BlockPos throne = helper.absolutePos(new BlockPos(2, 0, 2));
        ChunkPos capital = new ChunkPos(throne);
        int y = throne.getY();
        // The post stands two blocks from the eastern edge of the capital chunk; the chunk east of it is given up.
        int x0 = capital.getMaxBlockX() - 1;
        int z0 = capital.getMiddleBlockZ();
        BlockPos from = new BlockPos(x0 - 4, y, z0 - 4);
        BlockPos to = new BlockPos(x0 + 4, y + 2, z0 + 4);
        clear(level, from, to);
        KingdomWorldApi.change(server, "test", state -> state.unclaim(null, kingdom.id(), Dimensions.id(level),
                ChunkKey.of(capital.x + 1, capital.z)));
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(x0 - 3, y, z0 - 3), new BlockPos(x0 + 3, y + 1, z0 + 3))) {
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        }
        BlockPos coal = new BlockPos(x0 + 1, y, z0 + 1);
        BlockPos outside = new BlockPos(x0 + 2, y, z0 + 1);
        BlockPos chest = new BlockPos(x0 - 2, y, z0 + 1);
        BlockPos lava = new BlockPos(x0 - 1, y, z0 + 2);
        level.setBlockAndUpdate(coal, Blocks.COAL_ORE.defaultBlockState());
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, net.minecraft.core.Direction.NORTH));
        level.setBlockAndUpdate(lava, Blocks.LAVA.defaultBlockState());
        // Room for the miner to stand behind its post.
        level.setBlockAndUpdate(new BlockPos(x0, y, z0 - 1), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(x0, y + 1, z0 - 1), Blocks.AIR.defaultBlockState());
        StorehouseInventory store = storehouse(level, new BlockPos(x0 - 4, y, z0 - 4));
        store.insertItem(0, new ItemStack(Items.STONE_PICKAXE), false);
        WorldPos postPos = post(level, new BlockPos(x0, y, z0), "miner");
        KingdomWorldApi.change(server, "test", state -> state.setPostRadius(null, postPos, 3, 16));
        Citizen worker = worker(helper, kingdom, "miner", postPos, new BlockPos(x0, y, z0 - 1));

        helper.succeedWhen(() -> {
            helper.assertTrue(level.getBlockState(new BlockPos(x0, y, z0 + 1)).isAir()
                    && level.getBlockState(new BlockPos(x0, y + 1, z0 + 1)).isAir(), "The entrance of the mine is not dug");
            helper.assertTrue(level.getBlockState(coal).isAir(), "The coal ore was not mined");
            helper.assertTrue(level.getBlockState(new BlockPos(x0 - 1, y, z0 + 1)).isAir(), "The tunnel next to the lava was not dug");
            helper.assertTrue(level.getBlockState(lava).is(net.minecraftforge.common.Tags.Blocks.COBBLESTONE), "The lava was not walled off");
            helper.assertTrue(level.getBlockState(chest).is(Blocks.CHEST), "The miner broke a chest");
            helper.assertTrue(level.getBlockState(outside).is(Blocks.STONE), "The miner dug on land that is not the kingdom's");
            helper.assertTrue(produced(server, kingdom, "miner", "minecraft:coal") >= 1, "The coal was not counted");
            end(helper, kingdom, worker, from, to);
        });
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
        clear(level, from, to);
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
        clear(level, from, to);
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
        clear(level, from, to);
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
        return new GatheringRules(rules.defaultRadius(), rules.maxRadius(), rules.maxSeats(), rules.maxTreeBlocks(), rules.mineLevels(),
                rules.maxHerd(), rules.carrierRange(), rules.carrierItemsPerMinute(), deathChance > 0 ? 1.0 : 0.0,
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

        CompoundTag saved = data.save(new CompoundTag());
        KingdomSavedData reloaded = KingdomSavedData.load(saved);
        helper.assertTrue(!reloaded.isReadOnly(), "Saved posts could not be read back: " + reloaded.readOnlyReason());
        WorkPost back = reloaded.state().citizens().posts().get(postPos).orElse(null);
        helper.assertTrue(back != null && back.professionId().equals("lumberjack") && back.seats() == 2, "The post lost its settings");
        helper.assertTrue(back.workers().contains(citizen.id()), "The post lost its worker");
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
