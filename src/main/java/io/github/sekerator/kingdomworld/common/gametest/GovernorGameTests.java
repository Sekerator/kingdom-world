package io.github.sekerator.kingdomworld.common.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplate;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplates;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.nobility.NobilitySettings;
import io.github.sekerator.kingdomworld.common.nobility.NobilityWork;
import io.github.sekerator.kingdomworld.common.road.CityGovernors;
import io.github.sekerator.kingdomworld.common.road.RoadSettings;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.BuildPlan;
import io.github.sekerator.kingdomworld.core.construction.Commission;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.nobility.NobilityState;
import io.github.sekerator.kingdomworld.core.nobility.NobleHouse;
import io.github.sekerator.kingdomworld.core.road.CityCharter;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Game tests of the governors of cities: a mayor orders a house when the beds run out, a mayor who dies or is dismissed stops
 * and its orders go, a citizen made baron heads a house without wealth outside the limit and lives on until it dies, and what a
 * governor lays out leaves the blocks of players standing. Each test founds its kingdom in a batch of its own.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GovernorGameTests {
    private static final String EMPTY = "empty";

    private GovernorGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    /**
     * A house of the role of houses: a floor of cobblestone five by five with a bed, which the storehouse of a test can pay for.
     */
    static BuildingTemplate house(String name) {
        List<String> palette = List.of("minecraft:cobblestone", "minecraft:red_bed[facing=east,occupied=false,part=foot]",
                "minecraft:red_bed[facing=east,occupied=false,part=head]", "minecraft:air");
        List<int[]> blocks = new ArrayList<>();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                blocks.add(new int[]{x, 0, z, 0});
                blocks.add(new int[]{x, 1, z, z == 2 && x == 1 ? 1 : z == 2 && x == 2 ? 2 : 3});
                blocks.add(new int[]{x, 2, z, 3});
            }
        }
        JsonObject description = JsonParser.parseString("{\"name\": \"kingdomworld.building.small_house\", \"fallback_name\": \"" + name
                + "\", \"category\": \"housing\", \"role\": \"house\", \"type\": \"test\", \"icon\": \"minecraft:red_bed\", "
                + "\"anchor\": [2, 0, 4], \"entrance\": \"south\"}").getAsJsonObject();
        CompoundTag structure = RemoteBuildingGameTests.structure(5, 3, 5, palette, blocks.toArray(new int[0][]));
        return BuildingTemplates.addForTest(KingdomWorld.id("test_" + name), description, structure);
    }

    /**
     * Somebody of the kingdom plays today, so that its governors decide.
     */
    static void played(MinecraftServer server, Kingdom kingdom) {
        WarWork.state(server).presence().present(kingdom.id(), KingdomWorldApi.gameTime(server));
    }

    /**
     * The orders of the governor of a city not laid out yet.
     */
    static List<Commission> governorOrders(MinecraftServer server, Kingdom kingdom, long cityId) {
        return KingdomWorldApi.state(server).construction().commissionsOf(kingdom.id()).stream()
                .filter(commission -> commission.origin() == Commission.Origin.GOVERNOR && commission.cityId() == cityId).toList();
    }

    /**
     * A capital whose every bed is taken (it has none), with a builder at a post and a carrier at another, so that nobody is
     * without work: the carrier is the citizen made mayor.
     */
    static Citizen[] capitalWithoutBeds(GameTestHelper helper, Kingdom kingdom) {
        MinecraftServer server = helper.getLevel().getServer();
        WorldPos builderPost = GatheringGameTests.post(helper, new BlockPos(6, 0, 6), "builder");
        WorldPos carrierPost = GatheringGameTests.post(helper, new BlockPos(8, 0, 2), "carrier");
        Citizen builder = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
        Citizen mayor = CitizenGameTests.newcomer(helper, kingdom, 1, 3);
        helper.assertTrue(KingdomWorldApi.change(server, "test", state -> state.assign(null, builderPost, builder.id())).ok(),
                "The builder was not hired");
        helper.assertTrue(KingdomWorldApi.change(server, "test", state -> state.assign(null, carrierPost, mayor.id())).ok(),
                "The carrier was not hired");
        return new Citizen[]{builder, mayor};
    }

    // ---------------------------------------------------------------- mayors

    @GameTest(template = EMPTY, batch = "kingdomworld_governor_house", timeoutTicks = 200)
    public static void aMayorOrdersAHouseWhenTheBedsRunOut(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Mayor House", UUID.randomUUID());
        BuildingTemplate template = house("governor_house");
        Citizen[] people = capitalWithoutBeds(helper, kingdom);
        try {
            RoadSettings.overrideGovernorStock(100);
            StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 4);
            store.insertItem(0, new ItemStack(Items.COBBLESTONE, 64), false);
            store.insertItem(1, new ItemStack(Items.RED_BED, 1), false);
            helper.assertTrue(CityGovernors.appoint(server, null, kingdom.id(), people[1].id(), 0).success(), "The carrier was not made mayor");
            helper.assertTrue(CityGovernors.governor(server, kingdom.id(), 0).kind() == CityGovernors.Kind.MAYOR, "The capital has no mayor");
            played(server, kingdom);
            CityGovernors.decideNow(server, kingdom.id(), 0);
            List<Commission> orders = governorOrders(server, kingdom, 0);
            helper.assertTrue(orders.size() == 1 && orders.get(0).templateId().equals(template.id().toString()),
                    "The mayor did not order the house the storehouse pays for: " + orders + ", note "
                            + RoadWork.state(server).charter(kingdom.id(), 0).map(CityCharter::note).orElse(""));
            helper.assertTrue(orders.get(0).actor() == null && orders.get(0).kind() == Commission.Kind.BUILD, "The order is not the governor's");
            CityGovernors.decideNow(server, kingdom.id(), 0);
            helper.assertTrue(governorOrders(server, kingdom, 0).size() == 1, "One builder took a second construction at once");
        } finally {
            RoadSettings.overrideGovernorStock(null);
            CitizenGameTests.finish(helper, kingdom, people);
            BuildingTemplates.removeForTest(template.id());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_governor_mayor_dies", timeoutTicks = 200)
    public static void theDeathOfTheMayorEndsTheOfficeAndTheBuilding(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Mayor Dies", UUID.randomUUID());
        Citizen[] people = capitalWithoutBeds(helper, kingdom);
        try {
            helper.assertTrue(CityGovernors.appoint(server, null, kingdom.id(), people[1].id(), 0).success(), "The carrier was not made mayor");
            CitizenLife.die(server, people[1].id(), CauseOfDeath.OTHER, "", people[1].lastPos());
            helper.assertTrue(CityGovernors.governor(server, kingdom.id(), 0).kind() == CityGovernors.Kind.NONE, "A dead mayor still governs");
            helper.assertTrue(RoadWork.state(server).charter(kingdom.id(), 0).map(CityCharter::mayor).orElse(null) == null,
                    "The office did not end");
            played(server, kingdom);
            CityGovernors.decideNow(server, kingdom.id(), 0);
            helper.assertTrue(governorOrders(server, kingdom, 0).isEmpty(), "A city without a governor ordered something");
        } finally {
            CitizenGameTests.finish(helper, kingdom, people);
        }
        helper.succeed();
    }

    /**
     * A city whose governor's order found no place for want of room widens its square by a ring over the land of its kingdom, and
     * the order looks for a place again; an order kept from its place by a block of a player widens nothing.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_governor_expand", timeoutTicks = 200)
    public static void aCityOutOfRoomWidensOverItsOwnLandOnly(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Wider City", UUID.randomUUID());
        BuildingTemplate template = house("governor_wider");
        Citizen[] people = capitalWithoutBeds(helper, kingdom);
        try {
            helper.assertTrue(CityGovernors.appoint(server, null, kingdom.id(), people[1].id(), 0).success(), "The carrier was not made mayor");
            played(server, kingdom);
            helper.assertTrue(io.github.sekerator.kingdomworld.common.construction.RemoteWorks.orderForGovernor(server, kingdom.id(),
                    template.id().toString(), 0).ok(), "The governor's order was refused");
            Commission order = governorOrders(server, kingdom, 0).get(0);
            int before = io.github.sekerator.kingdomworld.common.road.CityAreas.of(server, kingdom.id()).stream()
                    .filter(area -> area.cityId() == 0).findFirst().orElseThrow().radius();
            // A block of a player in the way is no want of room.
            KingdomWorldApi.state(server).construction().putCommission(order.withState(Commission.State.NO_PLACE, "blocked|1|2|3",
                    KingdomWorldApi.gameTime(server) + 24_000));
            CityGovernors.decideNow(server, kingdom.id(), 0);
            helper.assertTrue(RoadWork.state(server).charter(kingdom.id(), 0).map(CityCharter::extent).orElse(0) == 0,
                    "A city widened for an order kept by a block of a player");
            // Every place of the data looked at in vain: the city widens over its own land and the order looks again.
            KingdomWorldApi.state(server).construction().putCommission(order.withState(Commission.State.NO_PLACE, "no_place",
                    KingdomWorldApi.gameTime(server) + 24_000));
            CityGovernors.decideNow(server, kingdom.id(), 0);
            int extent = RoadWork.state(server).charter(kingdom.id(), 0).map(CityCharter::extent).orElse(0);
            helper.assertTrue(extent > before, "The city did not widen: extent " + extent + ", square " + before + ", note "
                    + RoadWork.state(server).charter(kingdom.id(), 0).map(CityCharter::note).orElse(""));
            io.github.sekerator.kingdomworld.common.road.CityAreas.Area wider = io.github.sekerator.kingdomworld.common.road.CityAreas
                    .of(server, kingdom.id()).stream().filter(area -> area.cityId() == 0).findFirst().orElseThrow();
            helper.assertTrue(wider.radius() >= extent, "The square of the city is not wider: " + wider.radius());
            helper.assertTrue(governorOrders(server, kingdom, 0).stream().noneMatch(found -> found.state() == Commission.State.NO_PLACE),
                    "The order does not look for a place again");
            // The wider square counts only on the land of the kingdom.
            String dimension = io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(helper.getLevel());
            for (int dx = -wider.radius(); dx <= wider.radius(); dx += 8) {
                int x = wider.x() + dx;
                int z = wider.z() + wider.radius();
                boolean own = kingdom.id().equals(KingdomWorldApi.state(server).territory().ownerOf(dimension,
                        io.github.sekerator.kingdomworld.core.territory.ChunkKey.of(x >> 4, z >> 4)));
                helper.assertTrue(io.github.sekerator.kingdomworld.common.road.CityAreas.inCity(server, kingdom.id(), dimension, x, z) == own,
                        "The edge of the wider square at " + x + " " + z + " is the city " + !own + " though the land is the kingdom's " + own);
            }
        } finally {
            CitizenGameTests.finish(helper, kingdom, people);
            BuildingTemplates.removeForTest(template.id());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_governor_dismissed", timeoutTicks = 200)
    public static void aDismissedGovernorStopsAndItsOrdersGo(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Mayor Dismissed", UUID.randomUUID());
        BuildingTemplate template = house("dismissed_house");
        Citizen[] people = capitalWithoutBeds(helper, kingdom);
        try {
            RoadSettings.overrideGovernorStock(100);
            StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 4);
            store.insertItem(0, new ItemStack(Items.COBBLESTONE, 64), false);
            store.insertItem(1, new ItemStack(Items.RED_BED, 1), false);
            helper.assertTrue(CityGovernors.appoint(server, null, kingdom.id(), people[1].id(), 0).success(), "The carrier was not made mayor");
            played(server, kingdom);
            CityGovernors.decideNow(server, kingdom.id(), 0);
            helper.assertTrue(!governorOrders(server, kingdom, 0).isEmpty(), "The mayor ordered nothing");
            helper.assertTrue(CityGovernors.dismiss(server, null, kingdom.id(), 0).success(), "The mayor was not dismissed");
            helper.assertTrue(governorOrders(server, kingdom, 0).isEmpty(), "The orders of the dismissed mayor stay");
            CityGovernors.decideNow(server, kingdom.id(), 0);
            helper.assertTrue(governorOrders(server, kingdom, 0).isEmpty(), "A city without a governor ordered something");
            helper.assertTrue(CityGovernors.pause(server, null, kingdom.id(), 0, true).success(), "The city was not stopped");
            helper.assertTrue(CityGovernors.appoint(server, null, kingdom.id(), people[1].id(), 0).success(), "The carrier was not made mayor again");
            CityGovernors.decideNow(server, kingdom.id(), 0);
            helper.assertTrue(governorOrders(server, kingdom, 0).isEmpty(), "A stopped governor ordered something");
        } finally {
            RoadSettings.overrideGovernorStock(null);
            CitizenGameTests.finish(helper, kingdom, people);
            BuildingTemplates.removeForTest(template.id());
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- barons

    @GameTest(template = EMPTY, batch = "kingdomworld_governor_baron", timeoutTicks = 2_400)
    public static void aCitizenMadeBaronHeadsAHouseUntilItDies(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Baron", UUID.randomUUID());
        Citizen citizen = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
        try {
            BlockPos hall = new BlockPos(helper.absolutePos(BlockPos.ZERO).getX() + RemoteBuildingGameTests.FAR, RemoteBuildingGameTests.HIGH + 1,
                    helper.absolutePos(BlockPos.ZERO).getZ());
            RemoteBuildingGameTests.meadow(helper, kingdom, hall, 20, at -> {
            });
            long city = RemoteBuildingGameTests.farCity(helper, kingdom, hall, "Baronville");
            NobilityState nobility = NobilityWork.state(server);
            int limit = NobilitySettings.rules().maxHouses();
            for (int i = 0; i < limit; i++) {
                nobility.found(kingdom.id(), 100 + i, NobilityWork.day(server));
            }
            helper.assertTrue(CityGovernors.makeBaron(server, null, kingdom.id(), citizen.id(), city).success(),
                    "The citizen was not made baron while the houses of the kingdom are at their limit");
            NobleHouse house = nobility.houseOf(citizen.id()).orElseThrow();
            helper.assertTrue(house.wealth() == 0, "The house of a citizen has wealth: " + house.wealth());
            helper.assertTrue(house.headSeed() == citizen.nameSeed(), "The head does not have the name of the citizen");
            helper.assertTrue(nobility.countedHouses(kingdom.id()) == limit, "The house of the citizen counts against the limit");
            helper.assertTrue(CityGovernors.governor(server, kingdom.id(), city).kind() == CityGovernors.Kind.HOUSE, "The house does not govern");
            played(server, kingdom);
            CityGovernors.decideNow(server, kingdom.id(), city);
            helper.assertTrue(governorOrders(server, kingdom, city).stream().anyMatch(order -> order.kind() == Commission.Kind.POST),
                    "The baron did not order a post of builders, the citizen being without work");
            // Every head that could die by chance dies; the citizen lives on.
            NobilityWork.overrideRolls(() -> 0.0);
            NobilityWork.kingdomDay(server, kingdom.id(), NobilityWork.day(server) + 1);
            helper.assertTrue(house.citizen() != null && house.headSeed() == citizen.nameSeed(), "The head who is a citizen died by chance");
            CitizenLife.die(server, citizen.id(), CauseOfDeath.OTHER, "", citizen.lastPos());
            NobilityWork.kingdomDay(server, kingdom.id(), NobilityWork.day(server) + 2);
            helper.assertTrue(house.citizen() == null, "The tie to the dead citizen did not end");
            helper.assertTrue(nobility.house(house.id()).isPresent() && nobility.holderOf(kingdom.id(),
                    io.github.sekerator.kingdomworld.core.nobility.Fief.city(city)).isPresent(), "The heir did not keep the city");
        } finally {
            NobilityWork.overrideRolls(null);
            CitizenGameTests.finish(helper, kingdom, citizen);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- the blocks of players

    @GameTest(template = EMPTY, batch = "kingdomworld_governor_player_block", timeoutTicks = 2_400)
    public static void whatAGovernorLaysOutLeavesTheBlockOfAPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Governor Fence", UUID.randomUUID());
        BuildingTemplate template = house("fenced_house");
        try {
            BlockPos anchor = new BlockPos(helper.absolutePos(BlockPos.ZERO).getX() + RemoteBuildingGameTests.FAR, RemoteBuildingGameTests.HIGH,
                    helper.absolutePos(BlockPos.ZERO).getZ());
            Construction construction = RemoteBuildingGameTests.paidFar(helper, kingdom, template, anchor);
            construction.setGovernor(true);
            Box box = construction.box();
            BlockPos fence = new BlockPos(box.minX(), box.minY(), box.minZ());
            TestChunks.hold(level, new BlockPos(box.minX() - 1, box.minY(), box.minZ() - 1), new BlockPos(box.maxX() + 1, box.maxY(), box.maxZ() + 1));
            level.setBlock(fence, Blocks.OAK_FENCE.defaultBlockState(), 3);
            BuildPlan plan = ConstructionWork.plan(construction).orElseThrow();
            ConstructionWork.applyPending(level, construction, plan, Integer.MAX_VALUE);
            helper.assertTrue(level.getBlockState(fence).is(Blocks.OAK_FENCE), "The fence of a player was broken");
            helper.assertTrue(construction.status() == Construction.Status.BLOCKED, "The construction does not wait: " + construction.status());
            helper.assertTrue(KingdomWorldApi.state(server).construction().get(construction.id()).isPresent(), "The construction was finished");
            helper.assertTrue(level.getBlockState(fence.east()).is(Blocks.COBBLESTONE), "The rest of the floor was not laid");
            // A construction of the king clears its place as always.
            construction.setGovernor(false);
            ConstructionWork.applyPending(level, construction, plan, Integer.MAX_VALUE);
            helper.assertTrue(level.getBlockState(fence).is(Blocks.COBBLESTONE), "A construction of the king left the fence");
            TestChunks.release(level, new BlockPos(box.minX() - 1, box.minY(), box.minZ() - 1));
            KingdomWorldState state = KingdomWorldApi.state(server);
            state.construction().get(construction.id()).ifPresent(left -> ConstructionWork.cancel(server, null, kingdom.id(), left.id()));
        } finally {
            CitizenGameTests.finish(helper, kingdom);
            BuildingTemplates.removeForTest(template.id());
        }
        helper.succeed();
    }
}
