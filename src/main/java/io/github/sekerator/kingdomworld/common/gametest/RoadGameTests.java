package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.packet.RoadPackets;
import io.github.sekerator.kingdomworld.common.road.BanditEntity;
import io.github.sekerator.kingdomworld.common.road.BanditWork;
import io.github.sekerator.kingdomworld.common.road.RoadActions;
import io.github.sekerator.kingdomworld.common.road.RoadBuilding;
import io.github.sekerator.kingdomworld.common.road.RoadCaravans;
import io.github.sekerator.kingdomworld.common.road.RoadPlanning;
import io.github.sekerator.kingdomworld.common.road.RoadRegistries;
import io.github.sekerator.kingdomworld.common.road.RoadSavedData;
import io.github.sekerator.kingdomworld.common.road.RoadSettings;
import io.github.sekerator.kingdomworld.common.road.RoadSpeed;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.common.trade.TradeSettings;
import io.github.sekerator.kingdomworld.common.trade.TradeWork;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.road.BanditCamp;
import io.github.sekerator.kingdomworld.core.road.BanditRules;
import io.github.sekerator.kingdomworld.core.road.Crossing;
import io.github.sekerator.kingdomworld.core.road.Endpoint;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadClass;
import io.github.sekerator.kingdomworld.core.road.RoadRules;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.road.TravelPlan;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.trade.Caravan;
import io.github.sekerator.kingdomworld.core.trade.Deal;
import io.github.sekerator.kingdomworld.core.trade.Goods;
import io.github.sekerator.kingdomworld.core.trade.TradeRules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Game tests of roads and robbers: builders lay a section of road from the storehouse; a road does not go onto the land
 * of another kingdom without its consent; a player is faster on a road; a caravan takes a road and arrives sooner; robbers
 * take goods into their camp and the payment for them goes back; a player who clears a camp takes its chest, and its own
 * blocks drop nothing; robbers break no blocks and spare a player away from the game; the server checks rights; roads and
 * camps survive saving. Every test runs in a batch of its own and clears what it built.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RoadGameTests {
    private static final String EMPTY = "empty";

    private RoadGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    private static String dimension(GameTestHelper helper) {
        return Dimensions.id(helper.getLevel());
    }

    /**
     * Grass along a line of blocks at relative height 0, with air above.
     */
    private static void ground(GameTestHelper helper, int fromX, int toX, int fromZ, int toZ) {
        for (int x = fromX; x <= toX; x++) {
            for (int z = fromZ; z <= toZ; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
                helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
            }
        }
    }

    /**
     * A straight way along x at relative z and height 0, each block with the kingdom whose land it is.
     */
    private static List<RouteCell> way(GameTestHelper helper, int fromX, int toX, int z) {
        List<RouteCell> cells = new ArrayList<>();
        for (int x = fromX; x <= toX; x++) {
            BlockPos pos = helper.absolutePos(new BlockPos(x, 0, z));
            cells.add(new RouteCell(pos.getX(), pos.getY(), pos.getZ(), RouteCell.Kind.GROUND, 0, KingdomWorldApi.ownerAt(helper.getLevel(), pos)));
        }
        return cells;
    }

    private static Road road(GameTestHelper helper, Kingdom kingdom, RoadClass roadClass, List<RouteCell> cells, Road.Mode mode,
                             List<Crossing> sent) {
        MinecraftServer server = helper.getLevel().getServer();
        long now = KingdomWorldApi.gameTime(server);
        RouteCell first = cells.get(0);
        RouteCell last = cells.get(cells.size() - 1);
        return RoadWork.state(server).addRoad(kingdom.id(), roadClass, dimension(helper), Endpoint.point(first.x(), first.z()),
                Endpoint.point(last.x(), last.z()), cells, RoadState.sectionOwners(cells), RoadState.crossedSections(cells, kingdom.id()), mode,
                now, now + 24_000, sent);
    }

    private static void payAll(Road road) {
        for (Road.Section section : road.sections()) {
            while (!section.done()) {
                section.markPaid(Map.of());
            }
        }
    }

    private static Citizen builder(GameTestHelper helper, Kingdom kingdom, WorldPos post) {
        MinecraftServer server = helper.getLevel().getServer();
        Citizen citizen = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
        KingdomWorldApi.change(server, "test", state -> state.setProfession(null, citizen.id(), "builder"));
        helper.assertTrue(KingdomWorldApi.state(server).citizens().assignWorkplace(citizen, post), "The builder got no seat at the post");
        return citizen;
    }

    private static ServerPlayer player(GameTestHelper helper, UUID id, String name, int x, int y, int z) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(id, name));
        BlockPos at = helper.absolutePos(new BlockPos(x, y, z));
        player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return player;
    }

    /**
     * Makes a player look idle for a time, as if it did nothing since.
     */
    private static void awayFor(ServerPlayer player, long millis) {
        try {
            java.lang.reflect.Field field = ServerPlayer.class.getDeclaredField("lastActionTime");
            field.setAccessible(true);
            field.setLong(player, net.minecraft.Util.getMillis() - millis);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("The idle time of a player cannot be set", e);
        }
    }

    private static void join(MinecraftServer server, Kingdom kingdom, UUID player) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        state.membership().invite(kingdom.rulerId(), player, 1L);
        state.membership().acceptInvitation(player, kingdom.id(), 1L);
    }

    /**
     * Slow caravans, so that a road shows.
     */
    private static TradeRules slowCaravans() {
        TradeRules d = TradeRules.DEFAULT;
        return new TradeRules(d.exportFactor(), d.importFactor(), d.exportPerMerchant(), d.exportExperienceBonus(), d.exportMaxIncomePerDay(),
                d.importMaxPerDay(), d.importDelayTicks(), d.demandElasticity(), d.minPricePercent(), d.maxPricePercent(), d.stockTarget(),
                d.spreadPercent(), d.defaultSalesTaxPercent(), d.maxSalesTaxPercent(), d.maxLotsPerPlayer(), d.maxEntries(),
                d.defaultExchangeFeePercent(), d.maxExchangeFeePercent(), d.minRate(), d.maxRate(), d.rateSmoothingPercent(), d.productionDays(),
                d.minSupply(), 10.0, d.caravanLossChance(), d.proposalDays(), d.maxDealsPerKingdom(), d.maxPeriodDays());
    }

    private static int countIn(ServerLevel level, BlockPos from, BlockPos to, net.minecraft.world.level.block.Block block) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (level.getBlockState(pos).is(block)) {
                count++;
            }
        }
        return count;
    }

    private static void forget(MinecraftServer server, Road road) {
        RoadWork.state(server).removeRoad(road.id());
        RoadSpeed.invalidate();
    }

    private static void end(GameTestHelper helper, Kingdom kingdom, BlockPos from, BlockPos to, Citizen... citizens) {
        RoadSettings.override(null, null);
        TradeSettings.override(null);
        TradeWork.overrideRoll(null);
        MinecraftServer server = helper.getLevel().getServer();
        RoadWork.state(server).forgetKingdom(kingdom.id());
        CitizenGameTests.finish(helper, kingdom, citizens);
        GatheringGameTests.clear(helper.getLevel(), from, to);
    }

    // ---------------------------------------------------------------- building

    @GameTest(template = EMPTY, batch = "kingdomworld_roads_build")
    public static void buildersLayASectionFromTheStorehouse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(9, 3, 9));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Roadwrights", UUID.randomUUID());
        Citizen worker = null;
        try {
            StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 8);
            store.insertItem(0, new ItemStack(Items.GRAVEL, 64), false);
            ground(helper, 1, 7, 4, 6);
            worker = builder(helper, kingdom, GatheringGameTests.post(helper, new BlockPos(6, 0, 1), "builder"));
            RoadRules.Builder rules = RoadSettings.configuredRoads();
            rules.secondsPerBlock = new double[]{0.05, 0.05, 0.05};
            RoadSettings.override(rules.build(), null);
            Road road = road(helper, kingdom, RoadClass.GRAVEL, way(helper, 1, 7, 5), Road.Mode.OWN, new ArrayList<>());
            helper.assertTrue(road.status() == Road.Status.BUILDING, "A road on own land waits");
            int units = road.units();
            helper.assertTrue(units == 21, "A gravel road of seven blocks is not 21 blocks: " + units);
            for (int i = 0; i < 10 && !road.built(); i++) {
                RoadBuilding.tick(server);
            }
            helper.assertTrue(road.built(), "The builder did not pay for the road: " + road.paid() + " of " + units);
            helper.assertTrue(road.section(0).placedAll(), "The paid blocks were not placed: " + road.section(0).placed());
            int gravel = countIn(level, helper.absolutePos(new BlockPos(1, 0, 4)), helper.absolutePos(new BlockPos(7, 0, 6)), Blocks.GRAVEL);
            helper.assertTrue(gravel == units, "Gravel in the world: " + gravel);
            long left = KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.GRAVEL));
            helper.assertTrue(left == 64 - units, "The road took other than its blocks: " + left);
            long dirt = KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.DIRT));
            helper.assertTrue(dirt == units, "The grass the road replaced did not go to the storehouse: " + dirt);
            helper.assertTrue(road.section(0).pool().isEmpty(), "Paid items stayed in the section");
            forget(server, road);
        } finally {
            end(helper, kingdom, from, to, worker == null ? new Citizen[0] : new Citizen[]{worker});
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_roads_consent")
    public static void aRoadIsNotLaidOnForeignLandWithoutConsent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(9, 3, 9));
        GatheringGameTests.clear(level, from, to);
        TradeGameTests.clearSecond(helper);
        Kingdom ours = CitizenGameTests.found(helper, "GameTest Wayfarers", UUID.randomUUID());
        Kingdom theirs = TradeGameTests.second(helper, "GameTest Gatekeepers", UUID.randomUUID());
        int sx = TradeGameTests.SECOND;
        try {
            ground(helper, sx, sx + 7, 5, 5);
            CitizenGameTests.storehouse(helper, 0, 0, 8).insertItem(0, new ItemStack(Items.COBBLESTONE, 64), false);
            List<Crossing> sent = new ArrayList<>();
            List<RouteCell> cells = way(helper, sx, sx + 7, 5);
            helper.assertTrue(cells.stream().allMatch(cell -> theirs.id().equals(cell.owner())), "The way is not on their land");
            Road road = road(helper, ours, RoadClass.PATH, cells, Road.Mode.PAYER, sent);
            helper.assertTrue(road.status() == Road.Status.WAITING_CONSENT && sent.size() == 1, "The road did not ask: " + road.status());
            RoadBuilding.tick(server);
            helper.assertTrue(road.paid() == 0, "Builders worked on a road that waits for consent");
            payAll(road);
            RoadBuilding.placeNow(server, road);
            helper.assertTrue(road.section(0).placed() == 0, "A block of road was placed on foreign land without consent");
            helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(sx, 0, 5))).is(Blocks.GRASS_BLOCK), "The grass was touched");
            KingdomActions.Feedback refused = RoadActions.answer(server, ours.id(), sent.get(0).id(), true);
            helper.assertTrue(!refused.success(), "The planner answered for the owner of the land");
            helper.assertTrue(RoadActions.answer(server, theirs.id(), sent.get(0).id(), true).success(), "The owner could not agree");
            helper.assertTrue(road.status() == Road.Status.BUILDING, "Consent did not start the road");
            RoadBuilding.placeNow(server, road);
            int paths = countIn(level, helper.absolutePos(new BlockPos(sx, 0, 5)), helper.absolutePos(new BlockPos(sx + 7, 0, 5)), Blocks.DIRT_PATH);
            helper.assertTrue(paths == 8, "With consent the path was not laid: " + paths);
            forget(server, road);
        } finally {
            KingdomWorldApi.dissolveKingdom(server, theirs.id());
            RoadWork.state(server).forgetKingdom(theirs.id());
            end(helper, ours, from, to);
            TradeGameTests.clearSecond(helper);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_roads_speed")
    public static void aPlayerOnARoadIsFaster(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(9, 3, 9));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Swift Feet", UUID.randomUUID());
        try {
            ground(helper, 1, 7, 4, 8);
            for (int x = 1; x <= 7; x++) {
                for (int z = 4; z <= 6; z++) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.GRAVEL);
                }
            }
            Road road = road(helper, kingdom, RoadClass.GRAVEL, way(helper, 1, 7, 5), Road.Mode.OWN, new ArrayList<>());
            payAll(road);
            for (Road.Section section : road.sections()) {
                while (!section.placedAll()) {
                    section.markPlaced();
                }
            }
            RoadSpeed.invalidate();
            ServerPlayer player = player(helper, UUID.randomUUID(), "Runner", 4, 1, 5);
            // Standing on the ground: the block under the feet is what the player stands on.
            player.setOnGround(true);
            double base = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
            RoadSpeed.update(player);
            double onRoad = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
            double expected = base * (1.0 + RoadSettings.rules().playerSpeed(RoadClass.GRAVEL));
            helper.assertTrue(onRoad > base && Math.abs(onRoad - expected) < 1e-6, "Not faster on the road: " + base + " -> " + onRoad);
            BlockPos grass = helper.absolutePos(new BlockPos(4, 1, 8));
            player.setPos(grass.getX() + 0.5, grass.getY(), grass.getZ() + 0.5);
            player.setOnGround(true);
            RoadSpeed.update(player);
            helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - base) < 1e-9, "Still fast off the road");
            RoadRules.Builder off = RoadSettings.configuredRoads();
            off.speedOnRoads = false;
            RoadSettings.override(off.build(), null);
            BlockPos back = helper.absolutePos(new BlockPos(4, 1, 5));
            player.setPos(back.getX() + 0.5, back.getY(), back.getZ() + 0.5);
            player.setOnGround(true);
            RoadSpeed.update(player);
            helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - base) < 1e-9, "Faster though it is turned off");
            forget(server, road);
        } finally {
            end(helper, kingdom, from, to);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- caravans

    @GameTest(template = EMPTY, batch = "kingdomworld_roads_caravan")
    public static void aCaravanTakesTheRoadAndArrivesSooner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(9, 3, 9));
        GatheringGameTests.clear(level, from, to);
        TradeGameTests.clearSecond(helper);
        Kingdom seller = CitizenGameTests.found(helper, "GameTest Paved Way", UUID.randomUUID());
        Kingdom buyer = TradeGameTests.second(helper, "GameTest Far Market", UUID.randomUUID());
        CitizenGameTests.storehouse(helper, 0, 0, 4).insertItem(0, new ItemStack(Items.WHEAT, 64), false);
        CitizenGameTests.storehouse(helper, TradeGameTests.SECOND, 0, 4);
        Road road = null;
        try {
            TradeSettings.override(slowCaravans());
            Capital a = RoadWork.capital(server, seller.id()).orElseThrow();
            Capital b = RoadWork.capital(server, buyer.id()).orElseThrow();
            TravelPlan straight = RoadCaravans.plan(server, a.dimension(), a.x(), a.z(), b.x(), b.z());
            List<RouteCell> cells = new ArrayList<>();
            for (int x = a.x(); x <= b.x(); x++) {
                cells.add(new RouteCell(x, a.y() - 1, a.z(), RouteCell.Kind.GROUND, 0, null));
            }
            road = RoadWork.state(server).addRoad(seller.id(), RoadClass.PAVED, a.dimension(), Endpoint.capital(seller.id(), a.x(), a.z()),
                    Endpoint.capital(buyer.id(), b.x(), b.z()), cells, RoadState.sectionOwners(cells), Map.of(), Road.Mode.OWN, 0, 0,
                    new ArrayList<>());
            payAll(road);
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminTreasury(buyer.id(), 500, 0);
            Deal deal = TradeGameTests.propose(server, seller, buyer, 16, 80);
            helper.assertTrue(TradeWork.accept(server, null, buyer.id(), deal.id()).ok(), "The buyer could not accept");
            Caravan caravan = KingdomWorldApi.state(server).trade().caravans().iterator().next();
            long onRoad = caravan.arriveTime() - caravan.departTime();
            helper.assertTrue(onRoad < straight.ticks() / 2, "The caravan was not faster on the road: " + onRoad + " against " + straight.ticks());
            helper.assertTrue(RoadCaravans.roadsOf(server, caravan).contains(road.id()), "The caravan did not take the road");
            helper.assertTrue(road.traffic() == 1, "The road did not count the caravan");
            TradeWork.overrideRoll(() -> 1.0);
            TradeWork.arrive(server, caravan, caravan.arriveTime());
            helper.assertTrue(KingdomStorage.count(server, buyer.id(), stack -> stack.is(Items.WHEAT)) == 16, "The goods did not arrive");
            helper.assertTrue(RoadWork.state(server).route(caravan.id()).isEmpty(), "The way of the caravan stayed");
        } finally {
            if (road != null) {
                forget(server, road);
            }
            KingdomWorldApi.dissolveKingdom(server, buyer.id());
            end(helper, seller, from, to);
            TradeGameTests.clearSecond(helper);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_roads_ambush")
    public static void robbersTakeGoodsIntoTheirCampAndThePaymentGoesBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(9, 3, 9));
        GatheringGameTests.clear(level, from, to);
        TradeGameTests.clearSecond(helper);
        Kingdom seller = CitizenGameTests.found(helper, "GameTest Robbed Road", UUID.randomUUID());
        Kingdom buyer = TradeGameTests.second(helper, "GameTest Waiting Buyer", UUID.randomUUID());
        CitizenGameTests.storehouse(helper, 0, 0, 4).insertItem(0, new ItemStack(Items.WHEAT, 64), false);
        CitizenGameTests.storehouse(helper, TradeGameTests.SECOND, 0, 4);
        BanditCamp camp = null;
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminTreasury(buyer.id(), 500, 0);
            long money = economy.supply(buyer.id());
            Deal deal = TradeGameTests.propose(server, seller, buyer, 16, 80);
            helper.assertTrue(TradeWork.accept(server, null, buyer.id(), deal.id()).ok(), "The buyer could not accept");
            Caravan caravan = KingdomWorldApi.state(server).trade().caravans().iterator().next();
            BlockPos campAt = helper.absolutePos(new BlockPos(56, 0, 40));
            camp = BanditWork.found(server, dimension(helper), campAt.getX(), campAt.getZ(), 4);
            BanditRules.Builder rules = RoadSettings.configuredBandits();
            rules.merchantDeath = 0.0;
            RoadSettings.override(null, rules.build());
            Caravan left = RoadCaravans.forceAmbush(server, caravan, camp, () -> 0.0).orElseThrow();
            long taken = (long) Math.floor(16 * rules.unguardedLoot);
            helper.assertTrue(camp.lootItems() == taken, "The camp did not get the goods: " + camp.lootItems());
            helper.assertTrue(left.items() == 16 - taken, "The caravan kept other than the rest: " + left.items());
            long refund = 80 * taken / 16;
            helper.assertTrue(KingdomWorldApi.treasury(server, buyer.id()).orElseThrow().funds() == 500 - 80 + refund,
                    "The payment for the robbed goods did not come back");
            helper.assertTrue(economy.escrow(left.escrowId()).orElseThrow().amount() == 80 - refund, "The escrow keeps the wrong amount");
            helper.assertTrue(economy.supply(buyer.id()) == money, "The robbery made or lost money");
            helper.assertTrue(camp.knownTo(seller.id()) && camp.knownTo(buyer.id()), "The robbed kingdoms do not know the camp");
            TradeWork.overrideRoll(() -> 1.0);
            TradeWork.arrive(server, left, left.arriveTime());
            helper.assertTrue(KingdomStorage.count(server, buyer.id(), stack -> stack.is(Items.WHEAT)) == 16 - taken, "The rest did not arrive");
            helper.assertTrue(KingdomWorldApi.treasury(server, seller.id()).orElseThrow().reserve(buyer.id()) == 80 - refund,
                    "The seller was paid for robbed goods");
            helper.assertTrue(economy.escrows().isEmpty() && economy.supply(buyer.id()) == money, "Money vanished on the way");
        } finally {
            if (camp != null) {
                RoadWork.state(server).removeCamp(camp.id());
            }
            KingdomWorldApi.dissolveKingdom(server, buyer.id());
            end(helper, seller, from, to);
            TradeGameTests.clearSecond(helper);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_roads_chest")
    public static void aPlayerClearsACampAndTakesItsChest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(16, 4, 16));
        GatheringGameTests.clear(level, from, to);
        ground(helper, 0, 15, 0, 15);
        BlockPos center = helper.absolutePos(new BlockPos(7, 0, 7));
        BanditCamp camp = BanditWork.found(server, dimension(helper), center.getX(), center.getZ(), 0);
        try {
            camp.addLoot(List.of(Goods.of("minecraft:wheat", 20), Goods.of("minecraft:iron_ingot", 3)));
            BanditWork.build(level, camp);
            BanditWork.moveLootToChest(server, camp);
            helper.assertTrue(camp.chest().isPresent(), "The camp has no chest");
            helper.assertTrue(camp.loot().isEmpty(), "The goods did not go into the chest");
            BlockPos chest = BlockPos.of(camp.chest().getAsLong());
            helper.assertTrue(level.getBlockState(chest).is(Blocks.CHEST), "No chest in the world");
            BanditEntity leader = RoadRegistries.BANDIT_LEADER.get().create(level);
            helper.assertTrue(leader != null, "No robber");
            leader.setCampId(camp.id());
            leader.moveTo(center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5);
            level.addFreshEntity(leader);
            ServerPlayer player = player(helper, UUID.randomUUID(), "Hero", 7, 1, 9);
            leader.hurt(level.damageSources().playerAttack(player), 10_000.0f);
            helper.assertTrue(camp.cleared(), "Killing the leader of an empty camp did not clear it");
            int wool = 0;
            for (long position : List.copyOf(camp.structure())) {
                BlockPos pos = BlockPos.of(position);
                if (level.getBlockState(pos).is(Blocks.BROWN_WOOL)) {
                    wool++;
                    BanditWork.breakCampBlock(level, pos, camp);
                }
            }
            int inChest = 0;
            if (level.getBlockEntity(chest) instanceof net.minecraft.world.Container container) {
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    inChest += container.getItem(slot).getCount();
                }
            }
            helper.assertTrue(inChest == 23, "The chest holds " + inChest + " items instead of 23");
            BanditWork.breakCampBlock(level, chest, camp);
            List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(chest).inflate(16));
            int wheat = drops.stream().filter(item -> item.getItem().is(Items.WHEAT)).mapToInt(item -> item.getItem().getCount()).sum();
            int iron = drops.stream().filter(item -> item.getItem().is(Items.IRON_INGOT)).mapToInt(item -> item.getItem().getCount()).sum();
            helper.assertTrue(wheat == 20 && iron == 3, "The chest did not give its goods: " + wheat + " wheat, " + iron + " iron");
            helper.assertTrue(drops.stream().noneMatch(item -> item.getItem().is(Items.CHEST) || item.getItem().is(Items.BROWN_WOOL)
                    || item.getItem().is(Items.CAMPFIRE)), "A block of the camp dropped itself (" + wool + " wool broken)");
            helper.assertTrue(!level.getBlockState(chest).is(Blocks.CHEST), "The chest stayed");
            drops.forEach(ItemEntity::discard);
        } finally {
            RoadWork.state(server).removeCamp(camp.id());
            GatheringGameTests.clear(level, from, to);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_roads_peace", timeoutTicks = 200)
    public static void robbersBreakNoBlocksAndSpareAPlayerAway(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(9, 3, 9));
        GatheringGameTests.clear(level, from, to);
        ground(helper, 0, 8, 0, 8);
        BanditEntity bandit = RoadRegistries.BANDIT.get().create(level);
        helper.assertTrue(bandit != null, "No robber");
        BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
        bandit.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        level.addFreshEntity(bandit);
        helper.assertTrue(!ForgeEventFactory.getMobGriefingEvent(level, bandit), "A robber may grief");
        helper.assertTrue(bandit.goalSelector.getAvailableGoals().stream().noneMatch(goal -> goal.getGoal() instanceof BreakDoorGoal),
                "A robber breaks doors");
        int timeout = KingdomWorldConfig.SERVER.afkTimeoutSeconds.get();
        KingdomWorldConfig.SERVER.afkTimeoutSeconds.set(1);
        ServerPlayer player = player(helper, UUID.randomUUID(), "Sleeper", 5, 1, 5);
        helper.assertTrue(BanditWork.mayAttack(player), "Robbers spare a player at the keyboard");
        helper.runAfterDelay(5, () -> {
            try {
                // The player did nothing for a while: its last action is pushed back past the timeout.
                awayFor(player, 5_000L);
                helper.assertTrue(!BanditWork.mayAttack(player), "Robbers attack a player away from the game");
                float health = player.getHealth();
                boolean hurt = player.hurt(level.damageSources().mobAttack(bandit), 4.0f);
                helper.assertTrue(!hurt && player.getHealth() == health, "A robber hurt a player away from the game");
            } finally {
                KingdomWorldConfig.SERVER.afkTimeoutSeconds.set(timeout);
                bandit.discard();
                GatheringGameTests.clear(level, from, to);
            }
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- rights and saving

    @GameTest(template = EMPTY, batch = "kingdomworld_roads_rights")
    public static void theServerChecksTheRights(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(9, 3, 9));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Road Council", UUID.randomUUID());
        UUID subject = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        try {
            RoadRules.Builder rules = RoadSettings.configuredRoads();
            rules.minCityDistance = 4;
            RoadSettings.override(rules.build(), null);
            join(server, kingdom, subject);
            helper.assertTrue(KingdomWorldApi.state(server).membership().isMember(subject, kingdom.id()), "The subject did not join");
            BlockPos hall = helper.absolutePos(new BlockPos(6, 0, 6));
            Outcome city = RoadWork.foundCity(level, subject, kingdom.id(), hall, "Nowhere");
            helper.assertTrue(city.error() == KingdomError.NO_PERMISSION, "A subject founded a city: " + city);
            Outcome plan = RoadPlanning.start(level, subject, kingdom.id(), hall.getX(), hall.getZ(), hall.getX() + 40, hall.getZ());
            helper.assertTrue(plan.error() == KingdomError.NO_PERMISSION, "A subject planned a road: " + plan);
            ground(helper, 1, 7, 4, 6);
            Road road = road(helper, kingdom, RoadClass.PATH, way(helper, 1, 7, 5), Road.Mode.OWN, new ArrayList<>());
            KingdomActions.Feedback cancel = RoadActions.applyTab(server, subject, new RoadPackets.TabChange(kingdom.id(),
                    RoadPackets.TabChange.Action.CANCEL, road.id(), 0, ""));
            helper.assertTrue(!cancel.success() && RoadWork.state(server).road(road.id()).isPresent(), "A subject gave a road up");
            KingdomActions.Feedback patrol = RoadActions.applyTab(server, subject, new RoadPackets.TabChange(kingdom.id(),
                    RoadPackets.TabChange.Action.PATROL, road.id(), 1, ""));
            helper.assertTrue(!patrol.success(), "A subject sent a squad to patrol");
            helper.assertTrue(RoadActions.tabView(server, kingdom.id(), outsider).isEmpty(), "An outsider sees the roads tab");
            helper.assertTrue(RoadActions.tabView(server, kingdom.id(), subject).map(view -> !view.canManage()).orElse(false),
                    "A subject may manage roads in the tab");
            helper.assertTrue(RoadWork.foundCity(level, null, kingdom.id(), hall, "Rightful").ok(), "The operator could not found a city");
            Outcome close = RoadWork.foundCity(level, null, kingdom.id(), hall.offset(3, 0, 0), "Too Close");
            helper.assertTrue(close.error() == KingdomError.CITY_TOO_CLOSE, "A city was founded next to another: " + close);
            forget(server, road);
        } finally {
            end(helper, kingdom, from, to);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_roads_saving")
    public static void roadsAndCampsSurviveSaving(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(9, 3, 9));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Keepers of Ways", UUID.randomUUID());
        BanditCamp camp = null;
        Road road = null;
        try {
            road = road(helper, kingdom, RoadClass.PAVED, way(helper, 1, 40, 5), Road.Mode.OWN, new ArrayList<>());
            road.section(0).markPaid(Map.of("minecraft:cobblestone", 1));
            road.section(0).markPaid(Map.of("minecraft:stone_bricks", 1));
            road.section(0).markPlaced();
            road.section(1).setCondition(0.4);
            road.addTraffic(3);
            camp = BanditWork.found(server, dimension(helper), 500, 500, 5);
            camp.addLoot(List.of(Goods.of("minecraft:wheat", 12)));
            camp.learn(kingdom.id());
            RoadWork.state(server).addCity(kingdom.id(), "Keep", dimension(helper), 0, 64, 0, 1);
            RoadSavedData loaded = RoadSavedData.load(RoadSavedData.get(server).save(new CompoundTag()));
            helper.assertTrue(!loaded.isReadOnly(), "Saved roads became read-only: " + loaded.readOnlyReason());
            Road back = loaded.state().road(road.id()).orElseThrow();
            helper.assertTrue(back.paid() == 2 && back.section(0).placed() == 1 && back.section(0).pool().equals(road.section(0).pool()),
                    "The paid blocks changed in saving");
            helper.assertTrue(Math.abs(back.section(1).condition() - 0.4) < 1e-9 && back.traffic() == 3, "The wear changed in saving");
            helper.assertTrue(back.cells().equals(road.cells()), "The way changed in saving");
            BanditCamp savedCamp = loaded.state().camp(camp.id()).orElseThrow();
            helper.assertTrue(savedCamp.lootItems() == 12 && savedCamp.size() == 6 && savedCamp.knownTo(kingdom.id()), "The camp changed in saving");
            helper.assertTrue(loaded.state().citiesOf(kingdom.id()).size() == 1, "The city was lost in saving");
            CompoundTag newer = RoadSavedData.get(server).save(new CompoundTag());
            newer.putInt("formatVersion", RoadSavedData.FORMAT_VERSION + 1);
            RoadSavedData future = RoadSavedData.load(newer);
            helper.assertTrue(future.isReadOnly() && future.state().road(road.id()).isPresent(), "Newer data was not kept read-only");
            CompoundTag kept = future.save(new CompoundTag());
            helper.assertTrue(kept.getInt("formatVersion") == RoadSavedData.FORMAT_VERSION + 1, "Read-only data was overwritten");
        } finally {
            if (camp != null) {
                RoadWork.state(server).removeCamp(camp.id());
            }
            if (road != null) {
                forget(server, road);
            }
            end(helper, kingdom, from, to);
        }
        helper.succeed();
    }
}
