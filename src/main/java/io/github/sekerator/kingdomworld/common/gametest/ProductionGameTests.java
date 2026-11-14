package io.github.sekerator.kingdomworld.common.gametest;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSimulation;
import io.github.sekerator.kingdomworld.common.citizen.work.GatheringSettings;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehaviors;
import io.github.sekerator.kingdomworld.common.citizen.work.ZoneSurveys;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.common.production.Production;
import io.github.sekerator.kingdomworld.common.production.Recipes;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.production.Order;
import io.github.sekerator.kingdomworld.core.production.ProductionPlanner;
import io.github.sekerator.kingdomworld.core.production.RecipeBook;
import io.github.sekerator.kingdomworld.core.production.RecipeKind;
import io.github.sekerator.kingdomworld.core.production.RecipeOption;
import io.github.sekerator.kingdomworld.core.production.Stations;
import io.github.sekerator.kingdomworld.core.state.KingdomRules;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Game tests of the workshops and the king's orders: a smelter with a real furnace, a cook, a crafter at the
 * crafting table and a smith at the smithing table near players; chains of orders, reservations, keep orders and
 * making things far from players without duplicates; which recipes count; saving. Tests that found a kingdom run
 * in batches of their own and clear the blocks they placed.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProductionGameTests {
    private static final String EMPTY = "empty";

    private ProductionGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    private static long stored(MinecraftServer server, Kingdom kingdom, Item item) {
        return KingdomStorage.count(server, kingdom.id(), stack -> stack.is(item));
    }

    /**
     * Looks at the zone of a post right away, so that its workstations are known.
     */
    private static WorkPost surveyed(MinecraftServer server, WorldPos pos) {
        WorkPost post = PostWork.post(server, pos).orElseThrow();
        ZoneSurveys.surveyNow(server, post);
        return post;
    }

    /**
     * A citizen of a workshop without a body, working at the post.
     */
    private static Citizen farWorker(GameTestHelper helper, Kingdom kingdom, String profession, WorldPos post) {
        MinecraftServer server = helper.getLevel().getServer();
        Citizen citizen = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
        KingdomWorldApi.change(server, "test", state -> state.setProfession(null, citizen.id(), profession));
        helper.assertTrue(KingdomWorldApi.state(server).citizens().assignWorkplace(citizen, post), "The worker got no seat at the post");
        return citizen;
    }

    private static Order order(MinecraftServer server, Kingdom kingdom, Item item, Order.Kind kind, int amount) {
        KingdomWorldState.Ordering ordering = KingdomWorldApi.state(server).order(null, kingdom.id(), Recipes.itemId(item), kind, amount,
                Order.Priority.NORMAL, KingdomWorldApi.gameTime(server), 32, id -> Production.orderable(server, id));
        if (ordering.order().isEmpty()) {
            throw new IllegalStateException("The order was refused: " + ordering.outcome());
        }
        return ordering.order().get();
    }

    /**
     * Runs simulation steps without accidents.
     */
    private static void steps(MinecraftServer server, Kingdom kingdom, int count) {
        GatheringSettings.override(GatheringGameTests.safeRules(0.0));
        try {
            for (int i = 0; i < count; i++) {
                CitizenSimulation.step(server, kingdom.id());
            }
        } finally {
            GatheringSettings.override(null);
        }
    }

    private static void fill(StorehouseInventory store, ItemStack... stacks) {
        int slot = 0;
        for (ItemStack stack : stacks) {
            while (!store.getStackInSlot(slot).isEmpty()) {
                slot++;
            }
            store.insertItem(slot, stack, false);
        }
    }

    // ---------------------------------------------------------------- near players

    /**
     * The corner of the box a test of a workshop clears: the zone of a post reaches 7 blocks around it, the tests stand 8 blocks
     * apart, and a furnace or a table another test left in the zone would be the workshop the worker goes to.
     */
    private static final BlockPos ZONE_FROM = new BlockPos(-7, 0, -7);

    @GameTest(template = EMPTY, batch = "kingdomworld_workshop_smelter", timeoutTicks = 1800)
    public static void smelterFillsAVanillaFurnaceAndTakesTheIngot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        level.setDayTime(1_000L);
        BlockPos from = helper.absolutePos(ZONE_FROM);
        BlockPos to = helper.absolutePos(new BlockPos(7, 3, 6));
        GatheringGameTests.clear(level, from, to);
        // The worker walks beyond the chunks the game holds for the structure of the test: the box is held too.
        TestChunks.hold(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Foundry", UUID.randomUUID());
        helper.setBlock(new BlockPos(5, 0, 2), Blocks.FURNACE);
        StorehouseInventory store = GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(0, 0, 4)));
        fill(store, new ItemStack(Items.RAW_IRON), new ItemStack(Items.COAL));
        WorldPos postPos = GatheringGameTests.post(helper, new BlockPos(0, 0, 0), "smelter");
        WorkPost post = surveyed(server, postPos);
        helper.assertTrue(post.hasStation(Stations.FURNACE), "The furnace was not found in the zone");
        Citizen worker = GatheringGameTests.worker(helper, kingdom, "smelter", postPos, helper.absolutePos(new BlockPos(2, 0, 2)));

        helper.succeedWhen(() -> {
            helper.assertTrue(stored(server, kingdom, Items.IRON_INGOT) >= 1, "No ingot reached the storehouse: worker " + worker.workStatus()
                    + ", raw iron " + stored(server, kingdom, Items.RAW_IRON) + ", coal " + stored(server, kingdom, Items.COAL) + ", furnaces "
                    + furnacesAround(level, postPos, 8));
            helper.assertTrue(stored(server, kingdom, Items.RAW_IRON) == 0, "The raw iron is still in the storehouse");
            AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(5, 0, 2)));
            helper.assertTrue(furnace != null && furnace.getItem(0).isEmpty() && furnace.getItem(2).isEmpty(),
                    "The furnace was not emptied");
            helper.assertTrue(GatheringGameTests.produced(server, kingdom, "smelter", "minecraft:iron_ingot") >= 1, "The ingot was not counted");
            helper.assertTrue(!Production.reservations().has(worker.id()), "The reservation was not released");
            GatheringGameTests.end(helper, kingdom, worker, from, to);
        });
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_workshop_blocked_furnace", timeoutTicks = 1800)
    public static void aSmelterLeavesAFurnaceWhoseOutputHoldsSomethingElse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        level.setDayTime(1_000L);
        BlockPos from = helper.absolutePos(ZONE_FROM);
        BlockPos to = helper.absolutePos(new BlockPos(7, 3, 6));
        GatheringGameTests.clear(level, from, to);
        // The worker walks beyond the chunks the game holds for the structure of the test: the box is held too.
        TestChunks.hold(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Glassworks", UUID.randomUUID());
        // The nearer furnace holds glass in its output: iron put into it would never come out.
        helper.setBlock(new BlockPos(3, 0, 1), Blocks.FURNACE);
        helper.setBlock(new BlockPos(5, 0, 2), Blocks.FURNACE);
        AbstractFurnaceBlockEntity blocked = (AbstractFurnaceBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(3, 0, 1)));
        blocked.setItem(2, new ItemStack(Items.GLASS));
        StorehouseInventory store = GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(0, 0, 4)));
        fill(store, new ItemStack(Items.RAW_IRON), new ItemStack(Items.COAL));
        WorldPos postPos = GatheringGameTests.post(helper, new BlockPos(0, 0, 0), "smelter");
        surveyed(server, postPos);
        Citizen worker = GatheringGameTests.worker(helper, kingdom, "smelter", postPos, helper.absolutePos(new BlockPos(2, 0, 2)));

        helper.succeedWhen(() -> {
            helper.assertTrue(stored(server, kingdom, Items.IRON_INGOT) >= 1, "No ingot reached the storehouse: worker " + worker.workStatus()
                    + ", furnaces " + furnacesAround(level, postPos, 8));
            helper.assertTrue(blocked.getItem(0).isEmpty() && blocked.getItem(1).isEmpty() && blocked.getItem(2).is(Items.GLASS),
                    "The furnace with glass was used: " + furnacesAround(level, postPos, 8));
            GatheringGameTests.end(helper, kingdom, worker, from, to);
        });
    }

    /**
     * The furnaces around a post with what they hold, for the messages of failed tests.
     */
    static String furnacesAround(ServerLevel level, WorldPos post, int reach) {
        List<String> found = new java.util.ArrayList<>();
        BlockPos center = new BlockPos(post.x(), post.y(), post.z());
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-reach, -3, -reach), center.offset(reach, 3, reach))) {
            if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) {
                found.add(pos.subtract(center).toShortString() + " [" + furnace.getItem(0) + ", " + furnace.getItem(1) + ", " + furnace.getItem(2)
                        + "]");
            }
        }
        return found.toString();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_workshop_cook", timeoutTicks = 1800)
    public static void cookCooksFoodWhileTheStoresRunShort(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        level.setDayTime(1_000L);
        BlockPos from = helper.absolutePos(ZONE_FROM);
        BlockPos to = helper.absolutePos(new BlockPos(7, 3, 6));
        GatheringGameTests.clear(level, from, to);
        // The worker walks beyond the chunks the game holds for the structure of the test: the box is held too.
        TestChunks.hold(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Kitchens", UUID.randomUUID());
        helper.setBlock(new BlockPos(5, 0, 2), Blocks.SMOKER);
        StorehouseInventory store = GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(0, 0, 4)));
        fill(store, new ItemStack(Items.BEEF, 2), new ItemStack(Items.CHARCOAL));
        WorldPos postPos = GatheringGameTests.post(helper, new BlockPos(0, 0, 0), "cook");
        surveyed(server, postPos);
        Citizen worker = GatheringGameTests.worker(helper, kingdom, "cook", postPos, helper.absolutePos(new BlockPos(2, 0, 2)));
        // A fed cook: it does not eat the beef before cooking it.
        worker.setFood(io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds.MAX_FOOD);

        helper.succeedWhen(() -> {
            WorkPost cookPost = PostWork.post(server, postPos).orElseThrow();
            helper.assertTrue(stored(server, kingdom, Items.COOKED_BEEF) >= 1, "No cooked beef reached the storehouse: worker "
                    + worker.workStatus() + ", post " + cookPost.status() + ", stations " + cookPost.stations() + ", beef "
                    + stored(server, kingdom, Items.BEEF) + ", food " + worker.food() + ", hold " + Production.reservations().hold(worker.id()));
            helper.assertTrue(GatheringGameTests.produced(server, kingdom, "cook", "minecraft:cooked_beef") >= 1, "The food was not counted");
            GatheringGameTests.end(helper, kingdom, worker, from, to);
        });
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_workshop_crafter", timeoutTicks = 1600)
    public static void crafterMakesWhatIsOrderedAtTheCraftingTable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        level.setDayTime(1_000L);
        BlockPos from = helper.absolutePos(ZONE_FROM);
        BlockPos to = helper.absolutePos(new BlockPos(7, 3, 6));
        GatheringGameTests.clear(level, from, to);
        // The worker walks beyond the chunks the game holds for the structure of the test: the box is held too.
        TestChunks.hold(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Joinery", UUID.randomUUID());
        helper.setBlock(new BlockPos(5, 0, 2), Blocks.CRAFTING_TABLE);
        StorehouseInventory store = GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(0, 0, 4)));
        fill(store, new ItemStack(Items.OAK_LOG, 2));
        WorldPos postPos = GatheringGameTests.post(helper, new BlockPos(0, 0, 0), "crafter");
        surveyed(server, postPos);
        Citizen worker = GatheringGameTests.worker(helper, kingdom, "crafter", postPos, helper.absolutePos(new BlockPos(2, 0, 2)));
        Order planks = order(server, kingdom, Items.OAK_PLANKS, Order.Kind.MAKE, 4);

        helper.succeedWhen(() -> {
            io.github.sekerator.kingdomworld.common.citizen.CitizenEntity body = io.github.sekerator.kingdomworld.common.citizen.CitizenBodies.body(worker.id());
            helper.assertTrue(stored(server, kingdom, Items.OAK_PLANKS) == 4, "Expected 4 planks in the storehouse, got "
                    + stored(server, kingdom, Items.OAK_PLANKS) + ": logs " + stored(server, kingdom, Items.OAK_LOG) + ", worker "
                    + worker.workStatus() + ", body " + (body == null ? "none" : GatheringGameTests.local(helper, body.blockPosition()).toShortString()
                    + " holding " + body.getMainHandItem() + (body.isAlive() ? "" : " dead") + ", health " + body.getHealth()
                    + (body.isWounded() ? " wounded" : "") + (helper.getLevel().isDay() ? "" : " in the dark") + ", going to "
                    + (body.getNavigation().getTargetPos() == null ? "nothing" : GatheringGameTests.local(helper, body.getNavigation().getTargetPos()).toShortString())
                    + ", on " + helper.getLevel().getBlockState(body.blockPosition().below()).getBlock() + ", goals "
                    + body.goalSelector.getRunningGoals().map(goal -> goal.getGoal().getClass().getSimpleName()).toList()
                    + ", reservations " + Production.reservations().hold(worker.id())) + ", table " + helper.getBlockState(new BlockPos(5, 0, 2))
                    + ", storehouse " + helper.getBlockState(new BlockPos(0, 0, 4)) + ", feet " + GatheringGameTests.map(helper, -7, -7, 7, 7, 0)
                    + ", heads " + GatheringGameTests.map(helper, -7, -7, 7, 7, 1) + ", floor " + GatheringGameTests.map(helper, -7, -7, 7, 7, -1));
            helper.assertTrue(stored(server, kingdom, Items.OAK_LOG) == 1, "More than one log was used");
            Production.plan(server, kingdom.id());
            helper.assertTrue(KingdomWorldApi.state(server).orders().get(kingdom.id(), planks.id()).isEmpty(), "The finished order stayed");
            GatheringGameTests.end(helper, kingdom, worker, from, to);
        });
    }

    /**
     * A double storehouse of two halves facing north, its left half at {@code relative}; the inventories of both halves, the
     * left one first.
     */
    private static List<StorehouseInventory> doubleStorehouse(GameTestHelper helper, BlockPos relative) {
        net.minecraft.world.level.block.state.BlockState state = io.github.sekerator.kingdomworld.common.registry.ModRegistries.STOREHOUSE.get()
                .defaultBlockState().setValue(io.github.sekerator.kingdomworld.common.storage.StorehouseBlock.FACING, net.minecraft.core.Direction.NORTH);
        BlockPos right = relative.east();
        helper.setBlock(relative, state.setValue(io.github.sekerator.kingdomworld.common.storage.StorehouseBlock.TYPE,
                net.minecraft.world.level.block.state.properties.ChestType.LEFT));
        helper.setBlock(right, state.setValue(io.github.sekerator.kingdomworld.common.storage.StorehouseBlock.TYPE,
                net.minecraft.world.level.block.state.properties.ChestType.RIGHT));
        var registry = io.github.sekerator.kingdomworld.common.data.KingdomSavedData.get(helper.getLevel().getServer()).storehouses();
        return List.of(registry.get(CitizenGameTests.at(helper, relative.getX(), relative.getY(), relative.getZ())).orElseThrow(),
                registry.get(CitizenGameTests.at(helper, right.getX(), right.getY(), right.getZ())).orElseThrow());
    }

    /**
     * The planks a crafter needs lie on the far side of the workshop: one in a storehouse there, one in the far half of a double
     * storehouse, none in the storehouse by the post. The crafter walks to both, takes what each holds and makes the sticks of a
     * plank of oak and a plank of spruce together.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_workshop_far_storehouse", timeoutTicks = 2400)
    public static void crafterFetchesFromTheStorehousesWhereTheMaterialLies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        level.setDayTime(1_000L);
        BlockPos from = helper.absolutePos(ZONE_FROM);
        BlockPos to = helper.absolutePos(new BlockPos(7, 3, 6));
        GatheringGameTests.clear(level, from, to);
        TestChunks.hold(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Far Shelves", UUID.randomUUID());
        helper.setBlock(new BlockPos(2, 0, -2), Blocks.CRAFTING_TABLE);
        // A storehouse by the post holding nothing of what is needed: the worker does not stop there.
        GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(1, 0, 1)));
        StorehouseInventory far = GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(6, 0, 5)));
        List<StorehouseInventory> halves = doubleStorehouse(helper, new BlockPos(-6, 0, 5));
        fill(far, new ItemStack(Items.OAK_PLANKS, 1));
        fill(halves.get(1), new ItemStack(Items.SPRUCE_PLANKS, 1));
        WorldPos postPos = GatheringGameTests.post(helper, new BlockPos(0, 0, 0), "crafter");
        surveyed(server, postPos);
        Citizen worker = GatheringGameTests.worker(helper, kingdom, "crafter", postPos, helper.absolutePos(new BlockPos(1, 0, -1)));
        order(server, kingdom, Items.STICK, Order.Kind.MAKE, 4);
        boolean[] visitedFar = new boolean[2];

        helper.succeedWhen(() -> {
            io.github.sekerator.kingdomworld.common.citizen.CitizenEntity body = io.github.sekerator.kingdomworld.common.citizen.CitizenBodies.body(worker.id());
            if (body != null) {
                // The worker goes to the storehouses themselves, not only to the one by its post.
                visitedFar[0] |= body.blockPosition().distSqr(helper.absolutePos(new BlockPos(6, 0, 5))) <= 16;
                visitedFar[1] |= body.blockPosition().distSqr(helper.absolutePos(new BlockPos(-5, 0, 5))) <= 16;
            }
            helper.assertTrue(stored(server, kingdom, Items.STICK) == 4, "Expected 4 sticks, got " + stored(server, kingdom, Items.STICK)
                    + ": worker " + worker.workStatus() + ", reservations " + Production.reservations().hold(worker.id())
                    + ", body at " + (body == null ? "none" : GatheringGameTests.local(helper, body.blockPosition()).toShortString()));
            helper.assertTrue(stored(server, kingdom, Items.OAK_PLANKS) == 0 && stored(server, kingdom, Items.SPRUCE_PLANKS) == 0,
                    "The planks were not used together");
            helper.assertTrue(visitedFar[0] && visitedFar[1], "The worker did not walk to the storehouses holding the planks: " + visitedFar[0]
                    + " " + visitedFar[1]);
            GatheringGameTests.end(helper, kingdom, worker, from, to);
        });
    }

    /**
     * Far from players the same planks, in a storehouse and in either half of a double storehouse, are counted together by the
     * window of orders and the planner and taken by the making.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_workshop_far_storehouse_abstract")
    public static void materialsOnEveryStorehouseCountFarFromPlayers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(ZONE_FROM);
        BlockPos to = helper.absolutePos(new BlockPos(9, 3, 9));
        GatheringGameTests.clear(level, from, to);
        TestChunks.hold(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Scattered Stores", UUID.randomUUID());
        StorehouseInventory single = CitizenGameTests.storehouse(helper, 0, 0, 4);
        List<StorehouseInventory> halves = doubleStorehouse(helper, new BlockPos(-6, 0, 6));
        fill(single, new ItemStack(Items.OAK_PLANKS, 1));
        fill(halves.get(0), new ItemStack(Items.BIRCH_PLANKS, 1));
        fill(halves.get(1), new ItemStack(Items.SPRUCE_PLANKS, 2));
        helper.setBlock(new BlockPos(6, 0, 1), Blocks.CRAFTING_TABLE);
        WorldPos postPos = GatheringGameTests.post(helper, new BlockPos(5, 0, 1), "crafter");
        Citizen worker = farWorker(helper, kingdom, "crafter", postPos);
        surveyed(server, postPos);
        try {
            helper.assertTrue(KingdomStorage.countsById(server, kingdom.id()).getOrDefault("minecraft:spruce_planks", 0L) == 2,
                    "The far half of the double storehouse is not counted");
            Order sticks = order(server, kingdom, Items.STICK, Order.Kind.MAKE, 8);
            Production.plan(server, kingdom.id());
            helper.assertTrue(sticks.status() == Order.Status.READY, "Four planks of three woods are not enough for the planner: "
                    + sticks.status() + " " + sticks.missing());
            steps(server, kingdom, 4);
            helper.assertTrue(stored(server, kingdom, Items.STICK) == 8, "Expected 8 sticks, got " + stored(server, kingdom, Items.STICK)
                    + ", worker " + worker.workStatus());
            helper.assertTrue(stored(server, kingdom, Items.OAK_PLANKS) + stored(server, kingdom, Items.BIRCH_PLANKS)
                    + stored(server, kingdom, Items.SPRUCE_PLANKS) == 0, "Planks were left or made");
        } finally {
            CitizenGameTests.finish(helper, kingdom, worker);
            GatheringGameTests.clear(level, from, to);
            TestChunks.release(level, from);
        }
        helper.succeed();
    }

    /**
     * A plan to take from two storehouses fails at the second, which changed meanwhile: what was taken from the first goes back
     * into the very slot it came from, and nothing moves anywhere else.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_storehouse_plan_back")
    public static void aFailedPlanPutsEverythingBackWhereItWas(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Honest Shelves", UUID.randomUUID());
        try {
            StorehouseInventory first = CitizenGameTests.storehouse(helper, 0, 0, 4);
            StorehouseInventory second = CitizenGameTests.storehouse(helper, 4, 0, 4);
            first.setStackInSlot(7, new ItemStack(Items.OAK_PLANKS, 5));
            second.setStackInSlot(3, new ItemStack(Items.SPRUCE_PLANKS, 5));
            StorehouseInventory third = CitizenGameTests.storehouse(helper, 2, 0, 0);
            List<KingdomStorage.Take> plan = KingdomStorage.plan(server, kingdom.id(), java.util.Map.of("minecraft:oak_planks", 3,
                    "minecraft:spruce_planks", 3), item -> 0, CitizenGameTests.at(helper, 0, 0, 4));
            helper.assertTrue(plan != null && plan.size() == 2, "No plan over both storehouses: " + plan);
            // The storehouse the plan takes from last changes before the plan is carried out.
            KingdomStorage.Take last = plan.get(1);
            last.found().inventory().setStackInSlot(last.found().slot(), ItemStack.EMPTY);
            helper.assertTrue(KingdomStorage.takePlanned(server, plan) == null, "A plan that could not be carried out took something");
            KingdomStorage.Take firstTake = plan.get(0);
            ItemStack back = firstTake.found().inventory().getStackInSlot(firstTake.found().slot());
            helper.assertTrue(back.getCount() == 5 && ItemStack.isSameItemSameTags(back, firstTake.found().stack()),
                    "What was taken did not go back into its slot: " + back);
            helper.assertTrue(third.isEmpty(), "What was taken went to another storehouse");
        } finally {
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }

    /**
     * Far from players a crafter whose kingdom has only a storehouse of farmers makes nothing and takes nothing: there is nowhere
     * its planks could go. The logs stay, the post says so.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_workshop_no_storehouse")
    public static void workFarFromPlayersWithoutAStorehouseForItWaits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(ZONE_FROM);
        BlockPos to = helper.absolutePos(new BlockPos(9, 3, 9));
        GatheringGameTests.clear(level, from, to);
        TestChunks.hold(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Locked Barns", UUID.randomUUID());
        StorehouseInventory barn = CitizenGameTests.storehouse(helper, 0, 0, 4);
        barn.setAccepts(io.github.sekerator.kingdomworld.core.citizen.StorehouseRules.Accepts.of(List.of("farmer")));
        fill(barn, new ItemStack(Items.OAK_LOG, 4));
        helper.setBlock(new BlockPos(6, 0, 1), Blocks.CRAFTING_TABLE);
        WorldPos postPos = GatheringGameTests.post(helper, new BlockPos(5, 0, 1), "crafter");
        Citizen worker = farWorker(helper, kingdom, "crafter", postPos);
        surveyed(server, postPos);
        try {
            order(server, kingdom, Items.OAK_PLANKS, Order.Kind.MAKE, 8);
            steps(server, kingdom, 3);
            helper.assertTrue(stored(server, kingdom, Items.OAK_LOG) == 4 && stored(server, kingdom, Items.OAK_PLANKS) == 0,
                    "Logs were used for planks that could go nowhere: logs " + stored(server, kingdom, Items.OAK_LOG) + ", planks "
                            + stored(server, kingdom, Items.OAK_PLANKS));
            helper.assertTrue(worker.workStatus() == WorkStatus.NO_STOREHOUSE, "Expected no storehouse for the work, got " + worker.workStatus());
            // A common storehouse lets the work go on.
            CitizenGameTests.storehouse(helper, 2, 0, 4);
            steps(server, kingdom, 3);
            helper.assertTrue(stored(server, kingdom, Items.OAK_PLANKS) > 0, "The crafter did not work with a common storehouse");
        } finally {
            CitizenGameTests.finish(helper, kingdom, worker);
            GatheringGameTests.clear(level, from, to);
            TestChunks.release(level, from);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_workshop_smithing", timeoutTicks = 1600)
    public static void smithUpgradesToNetheriteAtTheSmithingTable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        level.setDayTime(1_000L);
        BlockPos from = helper.absolutePos(ZONE_FROM);
        BlockPos to = helper.absolutePos(new BlockPos(7, 3, 6));
        GatheringGameTests.clear(level, from, to);
        // The worker walks beyond the chunks the game holds for the structure of the test: the box is held too.
        TestChunks.hold(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Forges", UUID.randomUUID());
        helper.setBlock(new BlockPos(5, 0, 2), Blocks.SMITHING_TABLE);
        StorehouseInventory store = GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(0, 0, 4)));
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(Enchantments.SHARPNESS, 3);
        fill(store, sword, new ItemStack(Items.NETHERITE_INGOT), new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
        WorldPos postPos = GatheringGameTests.post(helper, new BlockPos(0, 0, 0), "smith");
        surveyed(server, postPos);
        Citizen worker = GatheringGameTests.worker(helper, kingdom, "smith", postPos, helper.absolutePos(new BlockPos(2, 0, 2)));
        order(server, kingdom, Items.NETHERITE_SWORD, Order.Kind.MAKE, 1);

        helper.succeedWhen(() -> {
            List<KingdomStorage.Found> found = KingdomStorage.find(server, kingdom.id(), stack -> stack.is(Items.NETHERITE_SWORD), null);
            helper.assertTrue(found.size() == 1, "No netherite sword reached the storehouse");
            helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, found.get(0).stack()) == 3,
                    "The upgrade lost the enchantment of the sword");
            helper.assertTrue(stored(server, kingdom, Items.DIAMOND_SWORD) == 0 && stored(server, kingdom, Items.NETHERITE_INGOT) == 0
                    && stored(server, kingdom, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) == 0, "The ingredients were not used up");
            GatheringGameTests.end(helper, kingdom, worker, from, to);
        });
    }

    // ---------------------------------------------------------------- far from players

    @GameTest(template = EMPTY, batch = "kingdomworld_workshop_chain")
    public static void smithMakesAnIronSwordThroughAChainFromRawIron(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(ZONE_FROM);
        BlockPos to = helper.absolutePos(new BlockPos(9, 3, 9));
        GatheringGameTests.clear(level, from, to);
        // The worker walks beyond the chunks the game holds for the structure of the test: the box is held too.
        TestChunks.hold(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Armoury", UUID.randomUUID());
        StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 4);
        fill(store, new ItemStack(Items.RAW_IRON, 2), new ItemStack(Items.COAL), new ItemStack(Items.STICK));
        helper.setBlock(new BlockPos(6, 0, 1), Blocks.CRAFTING_TABLE);
        helper.setBlock(new BlockPos(6, 0, 7), Blocks.FURNACE);
        WorldPos smithy = GatheringGameTests.post(helper, new BlockPos(5, 0, 1), "smith");
        WorldPos foundry = GatheringGameTests.post(helper, new BlockPos(5, 0, 7), "smelter");
        Citizen smith = farWorker(helper, kingdom, "smith", smithy);
        Citizen smelter = farWorker(helper, kingdom, "smelter", foundry);
        surveyed(server, smithy);
        surveyed(server, foundry);
        // The smelter works only for orders here.
        PostWork.post(server, foundry).orElseThrow().setAuto(false);
        try {
            Order sword = order(server, kingdom, Items.IRON_SWORD, Order.Kind.MAKE, 1);
            Production.plan(server, kingdom.id());
            helper.assertTrue(sword.status() == Order.Status.WAITING_FOR_PARTS, "Expected the sword to wait for parts, got " + sword.status());
            List<Order> subs = KingdomWorldApi.state(server).orders().children(kingdom.id(), sword.id());
            helper.assertTrue(subs.size() == 1 && subs.get(0).item().equals("minecraft:iron_ingot") && subs.get(0).amount() == 2,
                    "Expected a sub-order for 2 ingots, got " + subs);
            steps(server, kingdom, 4);
            helper.assertTrue(stored(server, kingdom, Items.IRON_SWORD) == 1, "The sword was not made: " + smith.workStatus()
                    + ", " + smelter.workStatus() + ", " + KingdomWorldApi.state(server).orders().ordersOf(kingdom.id()));
            helper.assertTrue(stored(server, kingdom, Items.RAW_IRON) == 0 && stored(server, kingdom, Items.IRON_INGOT) == 0
                    && stored(server, kingdom, Items.STICK) == 0, "Ingredients were left or made twice");
            helper.assertTrue(stored(server, kingdom, Items.COAL) == 0, "The fuel was not burned");
            helper.assertTrue(KingdomWorldApi.state(server).orders().ordersOf(kingdom.id()).isEmpty(), "Orders stayed after the sword was made");
            helper.assertTrue(GatheringGameTests.produced(server, kingdom, "smelter", "minecraft:iron_ingot") == 2,
                    "The smelting was not counted");
        } finally {
            CitizenGameTests.finish(helper, kingdom, smith, smelter);
            GatheringGameTests.clear(level, from, to);
            TestChunks.release(level, from);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_workshop_reservation")
    public static void aReservationKeepsTwoWorkersOffOneIngredient(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(ZONE_FROM);
        BlockPos to = helper.absolutePos(new BlockPos(9, 3, 9));
        GatheringGameTests.clear(level, from, to);
        // The worker walks beyond the chunks the game holds for the structure of the test: the box is held too.
        TestChunks.hold(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Two Carpenters", UUID.randomUUID());
        StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 4);
        fill(store, new ItemStack(Items.OAK_LOG));
        helper.setBlock(new BlockPos(6, 0, 1), Blocks.CRAFTING_TABLE);
        helper.setBlock(new BlockPos(6, 0, 7), Blocks.CRAFTING_TABLE);
        WorldPos first = GatheringGameTests.post(helper, new BlockPos(5, 0, 1), "crafter");
        WorldPos second = GatheringGameTests.post(helper, new BlockPos(5, 0, 7), "crafter");
        Citizen early = farWorker(helper, kingdom, "crafter", first);
        Citizen late = farWorker(helper, kingdom, "crafter", second);
        surveyed(server, first);
        WorkPost lateSeat = surveyed(server, second);
        try {
            order(server, kingdom, Items.OAK_PLANKS, Order.Kind.MAKE, 8);
            Production.plan(server, kingdom.id());
            ProductionPlanner.Job job = ProductionPlanner.nextJob(KingdomWorldApi.state(server).orders(), Production.world(server, kingdom.id()),
                    "crafter", Set.of(Stations.CRAFTING_TABLE), early.id());
            helper.assertTrue(job != null && job.picks().equals(Map.of("minecraft:oak_log", 1)), "The first worker found no work: " + job);
            // The first worker set the log aside, as a worker near players does when it starts.
            Production.reserve(early.id(), kingdom.id(), job, Map.of());
            GatheringSettings.override(GatheringGameTests.safeRules(0.0));
            ProfessionBehaviors.get("crafter").orElseThrow().workAbstractly(server, late, 1200);
            helper.assertTrue(late.workStatus() == WorkStatus.NO_INGREDIENTS, "The second worker should lack materials, got " + late.workStatus());
            helper.assertTrue(stored(server, kingdom, Items.OAK_LOG) == 1 && stored(server, kingdom, Items.OAK_PLANKS) == 0,
                    "The second worker used the log set aside for the first");
            // The first worker gives up: the log is free again.
            Production.release(early.id());
            ProfessionBehaviors.get("crafter").orElseThrow().workAbstractly(server, late, 1200);
            helper.assertTrue(stored(server, kingdom, Items.OAK_PLANKS) == 4 && stored(server, kingdom, Items.OAK_LOG) == 0,
                    "After the release the second worker should have made the planks");
            helper.assertTrue(lateSeat.producedOn(PostWork.gameDay(server)) == 4, "The post did not count its planks");
        } finally {
            GatheringSettings.override(null);
            Production.release(early.id());
            CitizenGameTests.finish(helper, kingdom, early, late);
            GatheringGameTests.clear(level, from, to);
            TestChunks.release(level, from);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_workshop_keep")
    public static void aKeepOrderHoldsItsQuota(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(ZONE_FROM);
        BlockPos to = helper.absolutePos(new BlockPos(9, 3, 9));
        GatheringGameTests.clear(level, from, to);
        // The worker walks beyond the chunks the game holds for the structure of the test: the box is held too.
        TestChunks.hold(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Stick Stores", UUID.randomUUID());
        StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 4);
        fill(store, new ItemStack(Items.BIRCH_PLANKS, 20));
        helper.setBlock(new BlockPos(6, 0, 1), Blocks.CRAFTING_TABLE);
        WorldPos postPos = GatheringGameTests.post(helper, new BlockPos(5, 0, 1), "crafter");
        Citizen worker = farWorker(helper, kingdom, "crafter", postPos);
        surveyed(server, postPos);
        try {
            Order keep = order(server, kingdom, Items.STICK, Order.Kind.KEEP, 8);
            steps(server, kingdom, 3);
            helper.assertTrue(stored(server, kingdom, Items.STICK) == 8, "Expected 8 sticks, got " + stored(server, kingdom, Items.STICK));
            helper.assertTrue(stored(server, kingdom, Items.BIRCH_PLANKS) == 16, "Planks were used beyond the quota");
            Production.plan(server, kingdom.id());
            helper.assertTrue(keep.status() == Order.Status.SATISFIED, "The quota is met but the order says " + keep.status());
            // Sticks are used up: the crafter makes them again.
            KingdomStorage.take(server, kingdom.id(), stack -> stack.is(Items.STICK), 5, null);
            steps(server, kingdom, 2);
            helper.assertTrue(stored(server, kingdom, Items.STICK) >= 8, "The quota was not made up again");
            helper.assertTrue(KingdomWorldApi.state(server).orders().get(kingdom.id(), keep.id()).isPresent(), "A keep order must stay");
        } finally {
            CitizenGameTests.finish(helper, kingdom, worker);
            GatheringGameTests.clear(level, from, to);
            TestChunks.release(level, from);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_workshop_abstract")
    public static void makingFarFromPlayersNeverDuplicatesOrLoses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(ZONE_FROM);
        BlockPos to = helper.absolutePos(new BlockPos(9, 3, 9));
        GatheringGameTests.clear(level, from, to);
        // The worker walks beyond the chunks the game holds for the structure of the test: the box is held too.
        TestChunks.hold(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Honest Mills", UUID.randomUUID());
        StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 4);
        fill(store, new ItemStack(Items.OAK_LOG, 5));
        helper.setBlock(new BlockPos(6, 0, 1), Blocks.CRAFTING_TABLE);
        WorldPos postPos = GatheringGameTests.post(helper, new BlockPos(5, 0, 1), "crafter");
        Citizen worker = farWorker(helper, kingdom, "crafter", postPos);
        surveyed(server, postPos);
        try {
            order(server, kingdom, Items.OAK_PLANKS, Order.Kind.MAKE, 12);
            steps(server, kingdom, 4);
            long logs = stored(server, kingdom, Items.OAK_LOG);
            long planks = stored(server, kingdom, Items.OAK_PLANKS);
            helper.assertTrue(planks == 12 && logs == 2, "Expected 12 planks from 3 logs, got " + planks + " planks and " + logs + " logs");
            helper.assertTrue(GatheringGameTests.produced(server, kingdom, "crafter", "minecraft:oak_planks") == 12, "Production counted wrong");

            // A full storehouse: nothing is taken, nothing is lost.
            for (int slot = 0; slot < store.getSlots(); slot++) {
                store.setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            store.setStackInSlot(0, new ItemStack(Items.OAK_LOG, 1));
            order(server, kingdom, Items.OAK_PLANKS, Order.Kind.MAKE, 4);
            steps(server, kingdom, 2);
            helper.assertTrue(stored(server, kingdom, Items.OAK_LOG) == 1 && stored(server, kingdom, Items.OAK_PLANKS) == 0,
                    "A making without room took the log or made planks out of nothing");
            helper.assertTrue(worker.workStatus() == WorkStatus.STORAGE_FULL, "Expected storehouses full, got " + worker.workStatus());
        } finally {
            CitizenGameTests.finish(helper, kingdom, worker);
            GatheringGameTests.clear(level, from, to);
            TestChunks.release(level, from);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- recipes

    @GameTest(template = EMPTY)
    public static void specialRecipesAndRecyclingAreLeftOut(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RecipeBook book = Recipes.book(server);
        helper.assertTrue(book.size() > 300, "Too few recipes were read: " + book.size());
        for (String special : List.of("minecraft:firework_star", "minecraft:tipped_arrow", "minecraft:suspicious_stew")) {
            helper.assertTrue(!book.canMake(special), special + " comes only from a special recipe and must not be ordered");
        }
        for (String special : List.of("minecraft:firework_rocket", "minecraft:firework_star", "minecraft:armor_dye", "minecraft:map_cloning",
                "minecraft:repair_item", "minecraft:shield_decoration")) {
            helper.assertTrue(book.recipe(special).isEmpty(), "The special recipe " + special + " was taken");
        }
        helper.assertTrue(book.recipe("minecraft:firework_rocket_simple").isPresent(), "The plain recipe of rockets is missing");
        helper.assertTrue(book.recipesFor("minecraft:iron_sword").stream().anyMatch(r -> r.profession().equals("smith")
                && r.kind() == RecipeKind.CRAFTING), "The smith does not make iron swords");
        helper.assertTrue(book.recipesFor("minecraft:netherite_sword").stream().anyMatch(r -> r.kind() == RecipeKind.SMITHING),
                "The netherite upgrade is missing");
        helper.assertTrue(book.recipesFor("minecraft:iron_ingot").stream().anyMatch(r -> r.profession().equals("smelter")
                && r.kind() == RecipeKind.SMELTING), "The smelter does not smelt raw iron");
        helper.assertTrue(book.recipesFor("minecraft:bread").stream().allMatch(r -> r.profession().equals("cook")), "Bread is not the cook's");
        helper.assertTrue(book.recipesFor("minecraft:cooked_beef").stream().allMatch(r -> r.profession().equals("cook")),
                "Cooking beef is not the cook's");
        helper.assertTrue(book.recipesFor("minecraft:stick").stream().allMatch(r -> r.profession().equals("crafter")),
                "Sticks are not the crafter's");
        helper.assertTrue(book.recipesFor("minecraft:stone_bricks").stream().anyMatch(r -> r.kind() == RecipeKind.STONECUTTING),
                "The stonecutter is missing");
        for (RecipeOption recipe : book.recipesFor("minecraft:iron_nugget")) {
            helper.assertTrue(!recipe.uses("minecraft:iron_sword"), "Iron gear would be melted into nuggets");
        }
        // Planks take any log of their kind: a tag becomes choices.
        helper.assertTrue(book.recipesFor("minecraft:oak_planks").stream().anyMatch(r -> r.parts().get(0).choices().size() > 1),
                "The tag of oak logs did not become choices");
        helper.assertTrue(!Recipes.autoRecipes(server, "smelter").isEmpty()
                && Recipes.autoRecipes(server, "smelter").stream().noneMatch(r -> r.uses("minecraft:iron_sword")),
                "The smelter's own work is wrong");
        helper.succeed();
    }

    /**
     * The catalog of the orders tab names the workshop of each item, and the client knows the name of that
     * profession: a sword is the smith's, not an unknown work.
     */
    @GameTest(template = EMPTY)
    public static void theCatalogNamesTheWorkshopOfASword(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        io.github.sekerator.kingdomworld.common.network.data.CatalogView catalog = io.github.sekerator.kingdomworld.common.production.OrderActions
                .catalog(server, UUID.randomUUID(), "iron_sword", 0, 16, List.of());
        String profession = catalog.entries().stream().filter(entry -> entry.sample().is(Items.IRON_SWORD))
                .map(io.github.sekerator.kingdomworld.common.network.data.CatalogView.Entry::profession).findFirst().orElse("");
        helper.assertTrue(profession.equals("smith"), "The catalog names '" + profession + "' for the iron sword");
        for (String workshop : List.of("smelter", "cook", "crafter", "smith")) {
            helper.assertTrue(io.github.sekerator.kingdomworld.core.citizen.Professions.REGISTRY.isKnown(workshop),
                    "The profession " + workshop + " is unknown without the server's behaviours");
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- saved data

    @GameTest(template = EMPTY)
    public static void ordersAndWorkshopsSurviveSaving(GameTestHelper helper) {
        KingdomSavedData data = new KingdomSavedData();
        CreateKingdomResult result = data.state().found(UUID.randomUUID(), "Saved Orders", 0x123456, UUID.randomUUID(),
                new Capital("minecraft:overworld", 8, 64, 8), 1L, KingdomRules.DEFAULT);
        Kingdom kingdom = ((CreateKingdomResult.Created) result).kingdom();
        WorldPos postPos = new WorldPos("minecraft:overworld", 10, 64, 10);
        data.state().citizens().addSite(postPos, SiteKinds.WORK_POST, kingdom.id());
        data.state().setPostProfession(null, postPos, "smith");
        WorkPost post = data.state().citizens().posts().get(postPos).orElseThrow();
        post.setAuto(false);
        post.setStations(Map.of(Stations.CRAFTING_TABLE, 2, Stations.SMITHING_TABLE, 1));
        Order sword = data.state().orders().add(kingdom.id(), "minecraft:iron_sword", Order.Kind.MAKE, 3, Order.Priority.HIGH, 5L);
        data.state().orders().addMade(sword, 1);
        data.state().orders().setPaused(sword, true);
        Order sticks = data.state().orders().add(kingdom.id(), "minecraft:stick", Order.Kind.KEEP, 16, Order.Priority.LOW, 6L);

        CompoundTag saved = data.save(new CompoundTag());
        KingdomSavedData reloaded = KingdomSavedData.load(saved);
        helper.assertTrue(!reloaded.isReadOnly(), "Saved orders could not be read back: " + reloaded.readOnlyReason());
        Order back = reloaded.state().orders().get(kingdom.id(), sword.id()).orElse(null);
        helper.assertTrue(back != null && back.made() == 1 && back.paused() && back.priority() == Order.Priority.HIGH
                && back.amount() == 3 && back.kind() == Order.Kind.MAKE, "The order lost its state: " + back);
        helper.assertTrue(reloaded.state().orders().get(kingdom.id(), sticks.id()).map(o -> o.kind() == Order.Kind.KEEP).orElse(false),
                "The keep order was lost");
        helper.assertTrue(reloaded.state().orders().nextId(kingdom.id()) == sticks.id() + 1, "Order ids would be reused");
        WorkPost postBack = reloaded.state().citizens().posts().get(postPos).orElseThrow();
        helper.assertTrue(!postBack.auto() && postBack.stations().getOrDefault(Stations.CRAFTING_TABLE, 0) == 2,
                "The workshop lost its mode or workstations");

        // Format 4 (stage 2b) is upgraded: no orders, posts work on their own, the steward gets the new right.
        CompoundTag kingdomTag = saved.getList("kingdoms", 10).getCompound(0).copy();
        kingdomTag.remove("orders");
        ListTag posts = kingdomTag.getCompound("population").getList("posts", 10);
        for (int i = 0; i < posts.size(); i++) {
            posts.getCompound(i).remove("auto");
            posts.getCompound(i).remove("stations");
        }
        ListTag steward = new ListTag();
        steward.add(StringTag.valueOf("build"));
        steward.add(StringTag.valueOf("manage_citizens"));
        kingdomTag.getCompound("permissions").put("steward", steward);
        ListTag kingdoms = new ListTag();
        kingdoms.add(kingdomTag);
        CompoundTag old = new CompoundTag();
        old.putInt("formatVersion", 4);
        old.put("kingdoms", kingdoms);
        KingdomSavedData upgraded = KingdomSavedData.load(old);
        helper.assertTrue(!upgraded.isReadOnly(), "Format 4 data became read-only: " + upgraded.readOnlyReason());
        helper.assertTrue(upgraded.isDirty(), "Upgraded data is not marked for saving");
        helper.assertTrue(upgraded.state().orders().ordersOf(kingdom.id()).isEmpty(), "Format 4 data came with orders");
        helper.assertTrue(upgraded.state().citizens().posts().get(postPos).orElseThrow().auto(), "An upgraded post does not work on its own");
        helper.assertTrue(upgraded.state().membership().kingdom(kingdom.id()).orElseThrow().permissions()
                .has(Role.STEWARD, Permission.MANAGE_PRODUCTION), "The steward did not get the right to manage production");

        // A broken order makes the data read-only instead of being dropped.
        CompoundTag broken = saved.copy();
        broken.getList("kingdoms", 10).getCompound(0).getCompound("orders").getList("list", 10).getCompound(0).putString("kind", "nonsense");
        helper.assertTrue(KingdomSavedData.load(broken).isReadOnly(), "An unreadable order did not make the data read-only");
        helper.succeed();
    }
}
