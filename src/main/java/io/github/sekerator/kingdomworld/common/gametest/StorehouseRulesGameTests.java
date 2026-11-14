package io.github.sekerator.kingdomworld.common.gametest;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.road.CityAreas;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.StorehouseRules;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.road.Wall;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Game tests of where workers take their goods: a lumberjack brings wood to the storehouse of wood past a nearer common one, the
 * room of the storehouses is counted in the order things are put away, and a worker of a post in a city finds a storehouse a
 * hundred blocks away inside the city but none beyond its wall.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StorehouseRulesGameTests {
    private static final String EMPTY = "empty";
    private static final BlockPos ZONE_FROM = new BlockPos(-7, 0, -7);

    private StorehouseRulesGameTests() {
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_storehouse_wood", timeoutTicks = 1200)
    public static void aLumberjackBringsWoodToTheStorehouseOfWood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        level.setDayTime(1_000L);
        BlockPos from = helper.absolutePos(ZONE_FROM);
        BlockPos to = helper.absolutePos(new BlockPos(7, 3, 7));
        GatheringGameTests.clear(level, from, to);
        TestChunks.hold(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Wood Yard", UUID.randomUUID());
        StorehouseInventory common = GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(1, 0, 2)));
        // A lumberjack without an axe does not go to work, and only a round of work ends with what it carries brought in.
        common.insertItem(0, new ItemStack(Items.IRON_AXE), false);
        StorehouseInventory wood = GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(6, 0, -6)));
        wood.setAccepts(StorehouseRules.Accepts.of(List.of("lumberjack")));
        StorehouseInventory food = GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(-2, 0, 1)));
        food.setAccepts(StorehouseRules.Accepts.of(List.of("farmer")));
        WorldPos postPos = GatheringGameTests.post(helper, new BlockPos(0, 0, 0), "lumberjack");
        Citizen worker = GatheringGameTests.worker(helper, kingdom, "lumberjack", postPos, helper.absolutePos(new BlockPos(1, 0, 1)));
        List<StorehouseInventory> order = KingdomStorage.deliveryTargets(level, kingdom.id(), "lumberjack", postPos,
                helper.absolutePos(new BlockPos(1, 0, 1)), List.of(new ItemStack(Items.OAK_LOG)));
        helper.assertTrue(order.size() == 2 && order.get(0) == wood && order.get(1) == common,
                "Expected the storehouse of wood, then the common one, not the one of farmers: " + order.stream().map(StorehouseInventory::pos).toList());
        CitizenEntity body = CitizenBodies.body(worker.id());
        helper.assertTrue(body != null, "No body for the lumberjack");
        body.carried().addItem(new ItemStack(Items.OAK_LOG, 12));

        helper.succeedWhen(() -> {
            helper.assertTrue(CitizenGameTests.count(wood, Items.OAK_LOG) == 12, "Expected 12 logs in the storehouse of wood, got "
                    + CitizenGameTests.count(wood, Items.OAK_LOG) + ", in the common one " + CitizenGameTests.count(common, Items.OAK_LOG)
                    + ", worker " + worker.workStatus());
            helper.assertTrue(CitizenGameTests.count(common, Items.OAK_LOG) == 0 && CitizenGameTests.count(food, Items.OAK_LOG) == 0,
                    "Wood went past the storehouse of wood");
            GatheringGameTests.end(helper, kingdom, worker, from, to);
        });
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_storehouse_room")
    public static void theRoomOfTheStorehousesIsCountedInTheOrderThingsArePutAway(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, 3));
        BlockPos to = helper.absolutePos(new BlockPos(7, 2, 6));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Full Stores", UUID.randomUUID());
        try {
            // The nearer storehouse has a single empty slot; the farther one a half stack of cobblestone and no empty slot.
            StorehouseInventory nearer = GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(0, 0, 4)));
            StorehouseInventory farther = GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(6, 0, 4)));
            for (int slot = 0; slot < StorehouseInventory.SLOTS; slot++) {
                if (slot < StorehouseInventory.SLOTS - 1) {
                    nearer.insertItem(slot, new ItemStack(Items.DIRT, 64), false);
                }
                farther.insertItem(slot, slot == 0 ? new ItemStack(Items.COBBLESTONE, 32) : new ItemStack(Items.DIRT, 64), false);
            }
            BlockPos at = helper.absolutePos(new BlockPos(0, 0, 5));
            WorldPos near = new WorldPos(Dimensions.id(level), at.getX(), at.getY(), at.getZ());
            // Put away nearest first, the cobblestone takes the empty slot of the nearer storehouse and leaves no room for the coal,
            // though the half stack of the farther one could have taken it.
            List<ItemStack> goods = List.of(new ItemStack(Items.COBBLESTONE, 20), new ItemStack(Items.COAL, 3));
            helper.assertFalse(KingdomStorage.fits(server, kingdom.id(), goods, near), "The cobblestone and the coal were counted as fitting");
            helper.assertFalse(KingdomStorage.fitsCarried(server, kingdom.id(), "miner", goods, near),
                    "What a miner carries was counted as fitting");
            // The coal first takes the empty slot, and the cobblestone goes onto the half stack: all of it fits.
            List<ItemStack> other = List.of(new ItemStack(Items.COAL, 3), new ItemStack(Items.COBBLESTONE, 20));
            helper.assertTrue(KingdomStorage.fits(server, kingdom.id(), other, near), "The coal and then the cobblestone did not fit");
            // The count says what really happens.
            ItemStack cobblestone = KingdomStorage.insert(server, kingdom.id(), new ItemStack(Items.COBBLESTONE, 20), near);
            ItemStack coal = KingdomStorage.insert(server, kingdom.id(), new ItemStack(Items.COAL, 3), near);
            helper.assertTrue(cobblestone.isEmpty() && coal.getCount() == 3, "Expected the coal alone to find no room, left "
                    + cobblestone + " and " + coal);
        } finally {
            GatheringGameTests.clear(level, from, to);
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_storehouse_city")
    public static void aStorehouseInsideTheCityIsFoundAtAnyDistance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        String dimension = Dimensions.id(level);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Long Streets", UUID.randomUUID());
        BlockPos throne = helper.absolutePos(new BlockPos(2, 0, 2));
        BlockPos inside = throne.offset(100, 0, 0);
        BlockPos beyond = throne.offset(130, 0, 0);
        TestChunks.hold(level, throne, beyond.offset(2, 0, 2));
        // Land runs on from the capital to both storehouses: a chunk apart from the land of the kingdom is never claimed.
        ChunkPos capital = new ChunkPos(throne);
        for (int x = capital.x; x <= new ChunkPos(beyond).x; x++) {
            KingdomWorldApi.claimChunk(level, new ChunkPos(x, capital.z), kingdom.id(), null);
        }
        for (BlockPos at : List.of(inside, beyond)) {
            helper.assertTrue(kingdom.id().equals(KingdomWorldApi.ownerAt(level, at)), "The land of the test was not claimed at " + at);
        }
        Wall wall = null;
        try {
            // A wall of the capital 110 blocks from the throne every way: the city reaches that far.
            List<RouteCell> ring = new ArrayList<>();
            for (int[] corner : new int[][]{{-110, -110}, {110, -110}, {110, 110}, {-110, 110}}) {
                ring.add(new RouteCell(throne.getX() + corner[0], throne.getY(), throne.getZ() + corner[1], RouteCell.Kind.GROUND, 0, null));
            }
            wall = new Wall(RoadWork.state(server).nextWallId(), 0, kingdom.id(), Wall.WallClass.PALISADE, dimension, throne.getX(), throne.getZ(),
                    ring, List.of(0), KingdomWorldApi.gameTime(server));
            RoadWork.state(server).putWall(wall);
            CityAreas.invalidate();
            StorehouseInventory far = GatheringGameTests.storehouse(level, inside);
            StorehouseInventory outside = GatheringGameTests.storehouse(level, beyond);
            WorldPos postPos = GatheringGameTests.post(helper, new BlockPos(0, 0, 0), "crafter");
            StorehouseRules.Reach reach = KingdomStorage.reach(server, kingdom.id(), postPos);
            helper.assertTrue(reach.city() != null && reach.city().radius() >= 110, "The post is not in the city: " + reach);
            List<StorehouseInventory> targets = KingdomStorage.deliveryTargets(level, kingdom.id(), "crafter", postPos,
                    helper.absolutePos(BlockPos.ZERO), null);
            helper.assertTrue(targets.contains(far), "A storehouse 100 blocks away inside the city was not found");
            helper.assertTrue(!targets.contains(outside), "A storehouse beyond the wall was found");
            helper.assertTrue(KingdomStorage.takingSources(level, kingdom.id(), postPos, helper.absolutePos(BlockPos.ZERO)).contains(far),
                    "A worker may not take from the storehouse inside the city");
        } finally {
            if (wall != null) {
                RoadWork.state(server).removeWall(wall.id());
                CityAreas.invalidate();
            }
            CitizenGameTests.finish(helper, kingdom);
            TestChunks.release(level, throne);
        }
        helper.succeed();
    }
}
