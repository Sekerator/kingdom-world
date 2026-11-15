package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSimulation;
import io.github.sekerator.kingdomworld.common.citizen.work.FarmerBehavior;
import io.github.sekerator.kingdomworld.common.commerce.CrownPurchases;
import io.github.sekerator.kingdomworld.common.construction.BuildingEffects;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.npc.NpcSettings;
import io.github.sekerator.kingdomworld.common.production.Production;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcRules;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.RulerRules;
import io.github.sekerator.kingdomworld.core.production.Order;
import io.github.sekerator.kingdomworld.core.production.OrderBook;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A stand that measures, one by one, the parts of the work of a kingdom of a thousand that a busy server feels: the free beds, the
 * storehouses a worker looks at, the look over a farm, the search for a job at a workshop, the plan of the orders and the purchases
 * of the crown. It prints the average and the worst of each and asks nothing of them: the numbers go into the table of the load.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LoadBenchGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NEWCOMERS = 700;
    private static final String[] ORDERED = {"minecraft:iron_ingot", "minecraft:bread", "minecraft:stone_bricks", "minecraft:chest",
            "minecraft:torch", "minecraft:glass", "minecraft:oak_planks", "minecraft:cooked_beef", "minecraft:iron_sword", "minecraft:ladder"};

    private LoadBenchGameTests() {
    }

    /**
     * Times a work: a few rounds to warm it up, then the average and the worst of the rounds, in the log.
     */
    private static void time(String what, int rounds, Supplier<Object> work) {
        for (int i = 0; i < Math.min(3, rounds); i++) {
            work.get();
        }
        long total = 0;
        long worst = 0;
        Object last = null;
        for (int i = 0; i < rounds; i++) {
            long start = System.nanoTime();
            last = work.get();
            long took = System.nanoTime() - start;
            total += took;
            worst = Math.max(worst, took);
        }
        LOGGER.info(String.format(Locale.ROOT, "Load bench: %s: %.3f ms on average, %.3f ms at worst (%d rounds; %s)", what,
                total / 1e6 / rounds, worst / 1e6, rounds, last));
    }

    @GameTest(template = NpcGameTests.EMPTY, batch = "kingdomworld_load_bench", timeoutTicks = 24_000, required = false)
    public static void thePartsOfTheWorkOfAKingdomOfAThousandAreMeasured(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos center = NpcGameTests.platform(helper, NpcGameTests.LARGE_HALF);
        NpcSettings.override(NpcRules.DEFAULT);
        NpcSettings.overrideRulers(RulerRules.DEFAULT);
        NpcSite site = null;
        List<UUID> added = new ArrayList<>();
        UUID kingdomId = null;
        List<Long> orders = new ArrayList<>();
        try {
            site = NpcGameTests.plant(helper, center, NpcLevel.LARGE);
            Kingdom kingdom = NpcGameTests.raise(helper, site, NpcGameTests.LARGE_STEPS);
            UUID id = kingdom.id();
            kingdomId = id;
            String dimension = Dimensions.id(helper.getLevel());
            for (int i = 0; i < NEWCOMERS; i++) {
                WorldPos place = new WorldPos(dimension, center.getX() + (i % 40) - 20, center.getY() + 1, center.getZ() + (i / 40) - 10);
                CitizenLife.arrive(server, id, place, false, false).map(Citizen::id).ifPresent(added::add);
            }
            var state = KingdomWorldApi.state(server);
            int citizens = state.citizens().registry().citizensOf(id).size();
            WorldPos near = new WorldPos(dimension, center.getX(), center.getY() + 1, center.getZ());
            LOGGER.info("Load bench: {} citizens, {} posts, {} storehouses", citizens, state.citizens().posts().postsOf(id).size(),
                    KingdomStorage.storehouses(server, id, null).size());

            // The beds: every citizen without one looks at the beds of the kingdom.
            time("a free bed", 200, () -> BuildingEffects.freeBed(server, id, near).map(bed -> bed.pos().toString()).orElse("none"));
            time("the free beds", 200, () -> java.util.Arrays.toString(BuildingEffects.freeBeds(server, id)));

            // The storehouses a worker looks at.
            time("the storehouses", 1_000, () -> KingdomStorage.storehouses(server, id, null).size());
            time("a storehouse for a farmer", 1_000, () -> KingdomStorage.hasStorehouseFor(server, id, Professions.FARMER.id()));
            BlockPos from = center.above();
            List<ItemStack> wheat = List.of(new ItemStack(Items.WHEAT, 32));
            time("the storehouses to take from", 1_000, () -> KingdomStorage.takingSources(helper.getLevel(), id, near, from).size());
            time("the storehouses to bring to", 1_000, () -> KingdomStorage.deliveryTargets(helper.getLevel(), id, Professions.FARMER.id(),
                    near, from, wheat).size());

            // The look over a farm of the largest zone.
            WorkPost farm = new WorkPost(near, id, 32, 0);
            time("the look over a farm of radius 32 at once (before)", 20, () -> {
                FarmerBehavior.surveyPostNow(server, farm);
                return farm.farmPlots();
            });
            // Now: a few columns a tick; a place without a post, so that nothing remembers the look.
            WorldPos away = new WorldPos(dimension, near.x() + 1, near.y(), near.z() + 1);
            WorkPost lone = new WorkPost(away, id, 32, 0);
            long worstSlice = 0;
            long allSlices = 0;
            int slices = 0;
            for (int round = 0; round < 20; round++) {
                FarmerBehavior.surveyPost(server, lone);
                while (FarmerBehavior.Looks.pending(away)) {
                    long began = System.nanoTime();
                    FarmerBehavior.Looks.tick(server);
                    long took = System.nanoTime() - began;
                    worstSlice = Math.max(worstSlice, took);
                    allSlices += took;
                    slices++;
                }
            }
            LOGGER.info(String.format(Locale.ROOT, "Load bench: the look over a farm of radius 32 tick by tick (now): %d ticks a look, %.3f ms a "
                    + "tick on average, %.3f ms at worst", slices / 20, allSlices / 1e6 / Math.max(1, slices), worstSlice / 1e6));

            // The search for a job at the workshops.
            List<WorkPost> workshops = new ArrayList<>();
            for (WorkPost post : state.citizens().posts().postsOf(id)) {
                if (Professions.usesStations(post.professionId()) && !post.workers().isEmpty()) {
                    workshops.add(post);
                }
            }
            workshops.sort(java.util.Comparator.comparing((WorkPost post) -> !Professions.SMELTER.id().equals(post.professionId())));
            if (!workshops.isEmpty()) {
                WorkPost workshop = workshops.get(0);
                UUID worker = workshop.workers().iterator().next();
                time("the search for a job at a workshop (" + workshop.professionId() + ")", 200, () -> Production.findJob(server, workshop,
                        worker, Set.copyOf(Production.stationsOf(workshop.professionId()))).reason());
            }

            // The plan of orders, the purchases of the crown and what constructions set aside, as the beginning of a step.
            OrderBook book = state.orders();
            long now = KingdomWorldApi.gameTime(server);
            for (int i = 0; i < 60; i++) {
                orders.add(book.add(id, ORDERED[i % ORDERED.length], Order.Kind.MAKE, 16 + i, Order.Priority.NORMAL, now).id());
            }
            book.setBuying(id, new OrderBook.Buying(true, 200, 1_000_000L));
            time("the plan of 60 orders", 50, () -> {
                Production.plan(server, id);
                return book.ordersOf(id).size();
            });
            time("the purchases of the crown with 60 orders", 50, () -> {
                CrownPurchases.step(server, id);
                return book.ordersOf(id).size();
            });
            time("what the constructions set aside", 50, () -> {
                ConstructionWork.refreshReservations(server, id);
                return "";
            });
            // Half of the orders done at once: the plan closes them in one step.
            long[] closing = new long[1];
            int open = book.ordersOf(id).size();
            int half = 0;
            for (Order order : book.mainOrdersOf(id)) {
                if (half++ % 2 == 0) {
                    book.addMade(order, order.amount());
                }
            }
            long start = System.nanoTime();
            Production.plan(server, id);
            closing[0] = System.nanoTime() - start;
            LOGGER.info(String.format(Locale.ROOT, "Load bench: the plan that closes %d of %d orders: %.3f ms", open - book.ordersOf(id).size(),
                    open, closing[0] / 1e6));

            // The whole step of the citizens, as the index bench measures it.
            time("a step of the citizens", 10, () -> {
                CitizenSimulation.step(server, id);
                return "";
            });
            helper.assertTrue(citizens >= 900, "The stand has only " + citizens + " citizens");
        } finally {
            if (kingdomId != null) {
                OrderBook book = KingdomWorldApi.state(server).orders();
                for (Order order : book.ordersOf(kingdomId)) {
                    book.remove(kingdomId, order.id());
                }
            }
            for (UUID id : added) {
                KingdomWorldApi.state(server).citizens().registry().remove(id);
            }
            NpcGameTests.cleanUp(helper, site, center, NpcGameTests.LARGE_HALF);
            NpcSettings.override(null);
            NpcSettings.overrideRulers(null);
        }
        helper.succeed();
    }

    /**
     * The wall of a large kingdom of the world, paid as its appearance pays it: before, the paid columns of a second (up to 960, 48
     * a tick) went up at once in the tick of the roads; now the walls go up every tick under a budget. Both are measured on the
     * same wall: a second's worth of columns at once, then the rest tick by tick.
     */
    @GameTest(template = NpcGameTests.EMPTY, batch = "kingdomworld_load_bench_wall", timeoutTicks = 24_000, required = false)
    public static void theWallOfALargeKingdomOfTheWorldGoesUpTickByTick(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos center = NpcGameTests.platform(helper, NpcGameTests.LARGE_HALF);
        NpcSettings.override(NpcRules.DEFAULT);
        NpcSettings.overrideRulers(RulerRules.DEFAULT);
        NpcSite site = null;
        try {
            site = NpcGameTests.plant(helper, center, NpcLevel.LARGE);
            // The composition of the place, worked out once for it: what the first tick of an appearance after a restart works out.
            long composed = System.nanoTime();
            io.github.sekerator.kingdomworld.common.npc.NpcSpawner.composition(site);
            LOGGER.info(String.format(Locale.ROOT, "Load bench: the composition of a large kingdom of the world: %.3f ms",
                    (System.nanoTime() - composed) / 1e6));
            // Up to the end of the wall: it is laid out and paid, none of it placed yet.
            for (int i = 0; i < NpcGameTests.LARGE_STEPS && site.status() == NpcSite.Status.SPAWNING && !wallPaid(server, site); i++) {
                io.github.sekerator.kingdomworld.common.npc.NpcSpawner.runNow(server, site, 1);
            }
            UUID id = site.kingdomId().orElseThrow();
            io.github.sekerator.kingdomworld.core.road.Wall wall = io.github.sekerator.kingdomworld.common.road.RoadWork.state(server)
                    .wallOf(0, id).orElse(null);
            helper.assertTrue(wall != null && wall.allPaid(), "The wall is not paid: " + wall + " at " + site.stage().id());
            int columns = 0;
            for (io.github.sekerator.kingdomworld.core.road.Road.Section section : wall.sections()) {
                columns += section.paid() - section.placed();
            }
            // Before: what a second paid (up to 960 columns), at once; half of the wall, so that the other half is left for now.
            int cap = Math.min(960, columns / 2);
            long start = System.nanoTime();
            int once = 0;
            for (io.github.sekerator.kingdomworld.core.road.Road.Section section : wall.sections()) {
                once += io.github.sekerator.kingdomworld.common.road.CityWalls.placeSection(helper.getLevel(), wall, section, cap - once);
                if (once >= cap) {
                    break;
                }
            }
            long before = System.nanoTime() - start;
            // Now: the rest, a tick at a time.
            long worst = 0;
            long total = 0;
            int ticks = 0;
            while (!wall.built() && ticks < 10_000) {
                long began = System.nanoTime();
                io.github.sekerator.kingdomworld.common.road.CityWalls.placeTick(server);
                long took = System.nanoTime() - began;
                worst = Math.max(worst, took);
                total += took;
                ticks++;
            }
            LOGGER.info(String.format(Locale.ROOT, "Load bench: a wall of %d paid columns: %d at once in %.3f ms (before); the other %d in %d "
                    + "ticks, %.3f ms a tick on average, %.3f ms at worst (now)", columns, once, before / 1e6, columns - once, ticks,
                    total / 1e6 / Math.max(1, ticks), worst / 1e6));
            helper.assertTrue(wall.built(), "The wall was not placed in " + ticks + " ticks");
        } finally {
            NpcGameTests.cleanUp(helper, site, center, NpcGameTests.LARGE_HALF);
            NpcSettings.override(null);
            NpcSettings.overrideRulers(null);
        }
        helper.succeed();
    }

    private static boolean wallPaid(MinecraftServer server, NpcSite site) {
        return site.kingdomId().flatMap(id -> io.github.sekerator.kingdomworld.common.road.RoadWork.state(server).wallOf(0, id))
                .map(io.github.sekerator.kingdomworld.core.road.Wall::allPaid).orElse(false);
    }

    /**
     * The chunks a large kingdom of the world with three towns asks for while it appears: all of them from the look at the land
     * to the end before, those of the land of the capital and of the towns alone between the look at the land and the roads now.
     */
    @GameTest(template = NpcGameTests.EMPTY, batch = "kingdomworld_load_bench_chunks", timeoutTicks = 200, required = false)
    public static void aLargeKingdomOfTheWorldHoldsTheChunksOfItsWaysOnlyWhileItNeedsThem(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos center = helper.absolutePos(BlockPos.ZERO);
        NpcSettings.override(NpcRules.DEFAULT);
        NpcSite site = null;
        try {
            int x = center.getX() + 4_000;
            int z = center.getZ() + 4_000;
            site = io.github.sekerator.kingdomworld.common.npc.NpcWork.plantAt(server, x, z, NpcLevel.LARGE,
                    List.of(new int[]{x + 420, z}, new int[]{x - 300, z + 330}, new int[]{x, z - 480}), NpcSite.LAYOUT_DISTRICTS, "").orElse(null);
            helper.assertTrue(site != null && site.towns().size() == 3, "The place has no three towns: " + site);
            int[] counts = io.github.sekerator.kingdomworld.common.npc.NpcSpawner.chunkCounts(site);
            LOGGER.info("Load bench: a large kingdom of the world with {} towns asks for {} chunks; between the look at the land and the "
                    + "roads it holds {} of them", site.towns().size(), counts[0], counts[1]);
        } finally {
            if (site != null) {
                io.github.sekerator.kingdomworld.common.npc.NpcWork.remove(server, site.id(), true);
            }
            NpcSettings.override(null);
        }
        helper.succeed();
    }

    /**
     * A kingdom of the world with a town appearing in ticks holds the chunks of the way to its town while it looks at the land, lets
     * them go for the stages between and asks for them again for its roads.
     */
    @GameTest(template = NpcGameTests.EMPTY, batch = "kingdomworld_npc_ways", timeoutTicks = 12_000)
    public static void aKingdomOfTheWorldLetsTheChunksOfItsWaysGoBetweenTheLookAndTheRoads(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos center = NpcGameTests.platform(helper);
        NpcSettings.override(NpcRules.DEFAULT);
        helper.assertTrue(io.github.sekerator.kingdomworld.common.npc.NpcWork.state(server).spawning().isEmpty(),
                "Another kingdom of the world is appearing");
        NpcSite site = io.github.sekerator.kingdomworld.common.npc.NpcWork.plantAt(server, center.getX(), center.getZ(), NpcLevel.SMALL,
                List.of(new int[]{center.getX() + 260, center.getZ()}), NpcSite.LAYOUT_DISTRICTS, "").orElse(null);
        helper.assertTrue(site != null, "The data of the kingdoms of the world is read-only");
        int[] counts = io.github.sekerator.kingdomworld.common.npc.NpcSpawner.chunkCounts(site);
        int[] early = {0};
        int[] middle = {Integer.MAX_VALUE};
        int[] roads = {0};
        long started = server.getTickCount();
        helper.onEachTick(() -> {
            int held = io.github.sekerator.kingdomworld.common.npc.NpcSpawner.heldChunks();
            io.github.sekerator.kingdomworld.core.npc.SpawnStage stage = site.stage();
            boolean spawning = site.status() == NpcSite.Status.SPAWNING;
            if (spawning && (stage == io.github.sekerator.kingdomworld.core.npc.SpawnStage.LOAD
                    || stage == io.github.sekerator.kingdomworld.core.npc.SpawnStage.CHECK)) {
                early[0] = Math.max(early[0], held);
            } else if (spawning && stage.ordinal() < io.github.sekerator.kingdomworld.core.npc.SpawnStage.ROADS.ordinal()) {
                // The fewest: the first tick of a stage comes to the test before the appearance lets the ways go.
                middle[0] = Math.min(middle[0], held);
            } else if (spawning && stage == io.github.sekerator.kingdomworld.core.npc.SpawnStage.ROADS) {
                roads[0] = Math.max(roads[0], held);
            }
            boolean over = !spawning || roads[0] > middle[0] || server.getTickCount() - started > 11_500;
            if (!over) {
                return;
            }
            String problem = null;
            if (site.towns().isEmpty()) {
                problem = "The town was left out: " + site.reason();
            } else if (early[0] != counts[0]) {
                problem = "While it looked at the land it held " + early[0] + " chunks of " + counts[0];
            } else if (middle[0] == Integer.MAX_VALUE || middle[0] > counts[1]) {
                problem = "Between the look and the roads it held " + middle[0] + " chunks, the land of the capital and the town are " + counts[1];
            } else if (roads[0] <= middle[0]) {
                problem = "It did not ask for the ways again for its roads: " + roads[0] + " at " + stage.id() + " " + site.status();
            }
            LOGGER.info("Load bench: a small kingdom of the world with a town held {} chunks while it looked at the land, {} between the "
                    + "look and the roads and {} for the roads ({} in all)", early[0], middle[0], roads[0], counts[0]);
            io.github.sekerator.kingdomworld.common.npc.NpcWork.remove(server, site.id(), true);
            NpcGameTests.cleanUp(helper, null, center);
            NpcSettings.override(null);
            if (problem != null) {
                helper.fail(problem);
            }
            helper.succeed();
        });
    }
}
