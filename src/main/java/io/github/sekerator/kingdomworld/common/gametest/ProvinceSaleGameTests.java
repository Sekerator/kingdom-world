package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.land.LandEvents;
import io.github.sekerator.kingdomworld.common.land.ProvinceSavedData;
import io.github.sekerator.kingdomworld.common.land.ProvinceTrade;
import io.github.sekerator.kingdomworld.common.land.StateGrowth;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.diplomacy.WarGoal;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.TreasuryJournal;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.land.BorderKind;
import io.github.sekerator.kingdomworld.core.land.ProvinceKind;
import io.github.sekerator.kingdomworld.core.land.Tier;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcNames;
import io.github.sekerator.kingdomworld.core.npc.ProvinceSale;
import io.github.sekerator.kingdomworld.core.npc.RulerCharacter;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.Province;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Game tests of the sale of provinces of the states of the world and of their growth. A state of the world is laid out in a
 * row of provinces west of the place of the test: its heart, two of its core and one on its edge, each {@value #WIDE} chunks
 * wide, with wild land beyond; their numbers are far above any a plan of the world gives, and every test gives them up.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProvinceSaleGameTests {
    private static final String EMPTY = "empty";
    /** Chunks across a province of the tests. */
    private static final int WIDE = 12;
    private static final int HEART = 64_901;
    private static final int CORE_NEAR = 64_902;
    private static final int CORE_FAR = 64_903;
    private static final int EDGE = 64_904;
    private static final int WILD = 64_905;
    private static final int WILD_PLAYER = 64_906;
    /** Wild land far away, so that the share of neutral land of the world of the tests stays large. */
    private static final int WILD_FAR = 64_907;

    private ProvinceSaleGameTests() {
    }

    // ---------------------------------------------------------------- the state of the tests

    /**
     * The state: its provinces from west to east heart, core, core, edge; the edge ends {@code gap} chunks west of the chunk of
     * the place of the test. Two wild provinces lie north of the edge and of the far core.
     */
    private record State(Kingdom kingdom, ChunkPos at, int gap) {
        int west(int index) {
            return at.x - gap - (4 - index) * WIDE;
        }
    }

    private static State state(GameTestHelper helper, String name, RulerCharacter character, int gap) {
        return state(helper, name, character, gap, new ChunkPos(helper.absolutePos(BlockPos.ZERO)));
    }

    /**
     * The state, its edge ending {@code gap} chunks west of a chunk: one chunk west of the land of a kingdom whose throne stands
     * there makes the edge its neighbour.
     */
    private static State state(GameTestHelper helper, String name, RulerCharacter character, int gap, ChunkPos at) {
        MinecraftServer server = helper.getLevel().getServer();
        for (Kingdom stale : KingdomWorldApi.kingdoms(server)) {
            if (stale.name().equals(name)) {
                NpcWork.state(server).removeKingdom(stale.id());
                KingdomWorldApi.dissolveKingdom(server, stale.id());
            }
        }
        CreateKingdomResult created = KingdomWorldApi.createKingdom(server, name, null);
        if (!(created instanceof CreateKingdomResult.Created result)) {
            throw new IllegalStateException("The state was not created: " + created);
        }
        Kingdom kingdom = result.kingdom();
        NpcWork.state(server).putKingdom(new NpcKingdom(kingdom.id(), -1, NpcLevel.SMALL, new NpcNames.Name(name, name), character, List.of(),
                4321, Gender.FEMALE, List.of(), KingdomWorldApi.gameTime(server), TreasuryActions.day(server)));
        State state = new State(kingdom, at, gap);
        TerritoryIndex territory = LandEvents.attach(server);
        ProvinceMap map = territory.provinceMap().orElseThrow();
        int[] ids = {HEART, CORE_NEAR, CORE_FAR, EDGE};
        ProvinceKind[] kinds = {ProvinceKind.HEART, ProvinceKind.CORE, ProvinceKind.CORE, ProvinceKind.FRONTIER};
        giveUp(helper);
        for (int i = 0; i < 4; i++) {
            List<Integer> near = new ArrayList<>();
            if (i > 0) {
                near.add(ids[i - 1]);
            }
            if (i < 3) {
                near.add(ids[i + 1]);
            }
            if (i == 3) {
                near.add(WILD);
            }
            if (i == 2) {
                near.add(WILD_PLAYER);
            }
            define(map, ids[i], state.west(i), at.z, near, kinds[i]);
            territory.fillProvince(ids[i], state.west(i), at.z, state.west(i) + WIDE - 1, at.z + WIDE - 1);
            territory.transferProvince(ids[i], kingdom.id(), 0);
        }
        define(map, WILD, state.west(3), at.z - WIDE, List.of(EDGE), ProvinceKind.WILD);
        territory.fillProvince(WILD, state.west(3), at.z - WIDE, state.west(3) + WIDE - 1, at.z - 1);
        define(map, WILD_PLAYER, state.west(2), at.z - WIDE, List.of(CORE_FAR), ProvinceKind.WILD);
        territory.fillProvince(WILD_PLAYER, state.west(2), at.z - WIDE, state.west(2) + WIDE - 1, at.z - 1);
        define(map, WILD_FAR, at.x + 200, at.z + 200, List.of(), ProvinceKind.WILD);
        territory.fillProvince(WILD_FAR, at.x + 200, at.z + 200, at.x + 239, at.z + 239);
        // The capital stands in the heart; the plan gave the state its four provinces.
        int heartX = state.west(0) * 16 + WIDE * 8;
        int heartZ = at.z * 16 + WIDE * 8;
        KingdomWorldApi.state(server).territory().establish(kingdom.id(), new Capital(Dimensions.id(helper.getLevel()), heartX, 64, heartZ), List.of());
        ProvinceSavedData.get(server).states().forget(kingdom.id());
        ProvinceSavedData.get(server).states().plan(kingdom.id(), 4, 4L * WIDE * WIDE);
        return state;
    }

    private static void define(ProvinceMap map, int id, int chunkX, int chunkZ, List<Integer> near, ProvinceKind kind) {
        List<BorderKind> borders = new ArrayList<>();
        for (int i = 0; i < near.size(); i++) {
            borders.add(BorderKind.values()[0]);
        }
        map.define(new Province(id, chunkX * 16 + WIDE * 8, chunkZ * 16 + WIDE * 8, near, borders, kind, id, 0));
    }

    private static void giveUp(GameTestHelper helper) {
        TerritoryIndex territory = LandEvents.attach(helper.getLevel().getServer());
        ProvinceMap map = territory.provinceMap().orElseThrow();
        for (int id : new int[]{HEART, CORE_NEAR, CORE_FAR, EDGE, WILD, WILD_PLAYER, WILD_FAR}) {
            if (map.exists(id)) {
                map.setHold(id, null);
                territory.clearProvince(id);
            }
        }
    }

    private static void end(GameTestHelper helper, Kingdom... kingdoms) {
        MinecraftServer server = helper.getLevel().getServer();
        ProvinceTrade.overrideWilling(null);
        StateGrowth.overrideSpawnZone(null);
        giveUp(helper);
        long now = KingdomWorldApi.gameTime(server);
        for (Kingdom kingdom : kingdoms) {
            if (kingdom != null) {
                WarWork.state(server).forget(kingdom.id(), now);
                ProvinceSavedData.get(server).states().forget(kingdom.id());
                NpcWork.state(server).removeSettlementsOf(kingdom.id());
                NpcWork.state(server).removeKingdom(kingdom.id());
                KingdomWorldApi.dissolveKingdom(server, kingdom.id());
            }
        }
    }

    private static ServerPlayer player(GameTestHelper helper, UUID id, String name) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(id, name));
    }

    private static ChunkPos capitalChunk(MinecraftServer server, Kingdom kingdom) {
        Capital capital = KingdomWorldApi.state(server).territory().territory(kingdom.id()).orElseThrow().capital();
        return new ChunkPos(capital.x() >> 4, capital.z() >> 4);
    }

    private static long worth(Economy economy, UUID currency, long amount, UUID in) {
        return io.github.sekerator.kingdomworld.core.trade.ExchangeRates.convert(amount, economy.rate(currency), economy.rate(in), 0);
    }

    // ---------------------------------------------------------------- tests

    @GameTest(template = EMPTY, batch = "kingdomworld_province_sale_kingdom", timeoutTicks = 100)
    public static void aKingdomBuysAProvinceNextToItsLandAndTheMoneyMovesInTheBooks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID king = UUID.randomUUID();
        Kingdom buyer = CitizenGameTests.found(helper, "GameTest Province Buyer", king);
        State state = state(helper, "GameTest Province Seller", RulerCharacter.MERCHANT, 1, capitalChunk(server, buyer));
        Kingdom seller = state.kingdom();
        try {
            ProvinceTrade.overrideWilling(true);
            Economy economy = KingdomWorldApi.state(server).economy();
            long day = TreasuryActions.day(server);
            economy.adminTreasury(buyer.id(), 50_000, day);
            ProvinceTrade.Quote quote = ProvinceTrade.quote(server, EDGE, buyer.id(), king);
            helper.assertTrue(quote.forSale() && quote.price() >= 1_500, "The edge is not for sale: " + quote);
            long buyerFunds = economy.treasury(buyer.id()).orElseThrow().funds();
            long sellerReserve = economy.treasury(seller.id()).orElseThrow().reserve(buyer.id());
            long supply = economy.supply(buyer.id());
            KingdomActions.Feedback bought = ProvinceTrade.buy(player(helper, king, "kw-buyer"), EDGE);
            helper.assertTrue(bought.success(), "The kingdom did not buy the edge: " + bought.message().getString());
            ProvinceMap map = LandEvents.attach(server).provinceMap().orElseThrow();
            helper.assertTrue(buyer.id().equals(map.owner(EDGE)), "The edge is not the buyer's");
            long paid = buyerFunds - economy.treasury(buyer.id()).orElseThrow().funds();
            long got = economy.treasury(seller.id()).orElseThrow().reserve(buyer.id()) - sellerReserve;
            helper.assertTrue(paid > 0 && paid == got && paid == economy.landCost(buyer.id(), seller.id(), quote.price()),
                    "The money did not move whole: paid " + paid + ", got " + got);
            helper.assertTrue(economy.supply(buyer.id()) == supply, "Money was made or lost: " + supply + " -> " + economy.supply(buyer.id()));
            TreasuryJournal.Totals spent = economy.treasury(buyer.id()).orElseThrow().journal().totals(day, TreasuryJournal.Period.TODAY)
                    .getOrDefault(LedgerCategory.LAND, TreasuryJournal.Totals.ZERO);
            TreasuryJournal.Totals earned = economy.treasury(seller.id()).orElseThrow().journal().totals(day, TreasuryJournal.Period.TODAY)
                    .getOrDefault(LedgerCategory.LAND, TreasuryJournal.Totals.ZERO);
            helper.assertTrue(spent.expense() == paid && earned.income() == worth(economy, buyer.id(), paid, seller.id()),
                    "No line \"Land\" on both sides: " + spent + ", " + earned);
            helper.assertTrue(ProvinceSavedData.get(server).states().entry(seller.id()).orElseThrow().sold() == 1, "The sale was not counted");
            // One sale in ten days: the next province is not for sale.
            helper.assertTrue(ProvinceTrade.quote(server, CORE_FAR, buyer.id(), king).refusal().isPresent(), "A second province was for sale");
            helper.succeed();
        } finally {
            end(helper, seller, buyer);
        }
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_province_sale_charter", timeoutTicks = 100)
    public static void aCharterFoundsAKingdomOnTheProvinceAndPaysTheSeller(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        State state = state(helper, "GameTest Charter Seller", RulerCharacter.MERCHANT, 3);
        Kingdom seller = state.kingdom();
        Kingdom founded = null;
        UUID bearer = UUID.randomUUID();
        try {
            ProvinceTrade.overrideWilling(true);
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminAccount(bearer, seller.id(), 20_000);
            long supply = economy.supply(seller.id());
            long sellerFunds = economy.treasury(seller.id()).orElseThrow().funds();
            ProvinceTrade.Quote quote = ProvinceTrade.quote(server, EDGE, null, bearer);
            KingdomActions.Feedback charter = ProvinceTrade.buy(player(helper, bearer, "kw-bearer"), EDGE);
            helper.assertTrue(charter.success(), "No charter: " + charter.message().getString());
            ProvinceMap map = LandEvents.attach(server).provinceMap().orElseThrow();
            ProvinceMap.SaleHold hold = map.hold(EDGE).orElseThrow();
            helper.assertTrue(hold.buyer().equals(bearer) && hold.amount() == quote.price() && economy.supply(seller.id()) == supply,
                    "The money is not held: " + hold + ", supply " + economy.supply(seller.id()));
            // The throne in the middle of the province, six chunks from the land of the seller around it.
            BlockPos throne = new BlockPos(state.west(3) * 16 + WIDE * 8, helper.absolutePos(BlockPos.ZERO).getY(), state.at().z * 16 + WIDE * 8);
            CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, throne, "GameTest Charter Kingdom", 0x2E86C1, bearer);
            helper.assertTrue(result instanceof CreateKingdomResult.Created, "The bearer could not found its kingdom: " + result);
            founded = ((CreateKingdomResult.Created) result).kingdom();
            helper.assertTrue(founded.id().equals(map.owner(EDGE)) && map.hold(EDGE).isEmpty(), "The province is not the new kingdom's");
            helper.assertTrue(economy.treasury(seller.id()).orElseThrow().funds() - sellerFunds == quote.price()
                    && economy.supply(seller.id()) == supply, "The seller was not paid from escrow");
            helper.succeed();
        } finally {
            end(helper, seller, founded);
        }
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_province_sale_return", timeoutTicks = 100)
    public static void aCharterWithoutAThroneGivesTheMoneyBack(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        State state = state(helper, "GameTest Charter Return", RulerCharacter.MERCHANT, 3);
        UUID bearer = UUID.randomUUID();
        try {
            ProvinceTrade.overrideWilling(true);
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminAccount(bearer, state.kingdom().id(), 20_000);
            long before = economy.accounts().balance(bearer, state.kingdom().id());
            helper.assertTrue(ProvinceTrade.buy(player(helper, bearer, "kw-late"), EDGE).success(), "No charter");
            ProvinceMap map = LandEvents.attach(server).provinceMap().orElseThrow();
            ProvinceMap.SaleHold hold = map.hold(EDGE).orElseThrow();
            // Three days pass without a throne.
            map.setHold(EDGE, new ProvinceMap.SaleHold(hold.buyer(), TreasuryActions.day(server), hold.seller(), hold.currency(), hold.amount()));
            ProvinceTrade.expire(server, map);
            helper.assertTrue(map.hold(EDGE).isEmpty() && economy.accounts().balance(bearer, state.kingdom().id()) == before
                    && state.kingdom().id().equals(map.owner(EDGE)), "The money did not come back whole");
            helper.succeed();
        } finally {
            end(helper, state.kingdom());
        }
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_province_sale_refused", timeoutTicks = 100)
    public static void theHeartASettledProvinceAndAnEnemyAtWarAreRefused(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID king = UUID.randomUUID();
        Kingdom buyer = CitizenGameTests.found(helper, "GameTest Province Enemy", king);
        State state = state(helper, "GameTest Province Proud", RulerCharacter.WARRIOR, 1, capitalChunk(server, buyer));
        try {
            helper.assertTrue(ProvinceTrade.quote(server, HEART, buyer.id(), king).refusal().orElse(null) == ProvinceSale.Refusal.HEART,
                    "The heart was for sale");
            helper.assertTrue(ProvinceTrade.quote(server, CORE_NEAR, buyer.id(), king).refusal().orElse(null) == ProvinceSale.Refusal.NEXT_TO_HEART,
                    "A province next to the heart was for sale");
            NpcWork.state(server).addSettlement(state.kingdom().id(), CORE_FAR, 0, Tier.VILLAGE, state.west(2) * 16 + 40, state.at().z * 16 + 40, 77,
                    new NpcNames.Name("Testholm", "Тестхольм"));
            helper.assertTrue(ProvinceTrade.quote(server, CORE_FAR, buyer.id(), king).refusal().orElse(null) == ProvinceSale.Refusal.SETTLED,
                    "A settled province was for sale");
            WarGameTests.war(helper, buyer, state.kingdom(), WarGoal.values()[0]);
            helper.assertTrue(WarWork.fighting(server, buyer.id(), state.kingdom().id())
                    && ProvinceTrade.quote(server, EDGE, buyer.id(), king).refusal().orElse(null) == ProvinceSale.Refusal.AT_WAR,
                    "The edge was for sale to an enemy at war");
            helper.succeed();
        } finally {
            WarWork.markPresent(buyer.id(), null);
            WarWork.markPresent(state.kingdom().id(), null);
            end(helper, state.kingdom(), buyer);
        }
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_province_growth", timeoutTicks = 1_200)
    public static void aRulerTakesAWildProvinceButNotOneNextToAPlayer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        // The land of a player in the wild province north of the far core.
        Kingdom player = CitizenGameTests.found(helper, "GameTest Wild Settler", UUID.randomUUID());
        State state = state(helper, "GameTest Growing State", RulerCharacter.BUILDER, 30);
        // The tests stand near the spawn of their world; the zone around it is left out here.
        StateGrowth.overrideSpawnZone(0);
        TerritoryIndex territory = LandEvents.attach(server);
        ProvinceMap map = territory.provinceMap().orElseThrow();
        // The claims of the player lie around the place of the test; the wild province beside them is the one nearest.
        territory.fillProvince(WILD_PLAYER, state.at().x - 3, state.at().z - 3, state.at().x + 3, state.at().z + 3);
        List<Integer> candidates = StateGrowth.candidates(server, state.kingdom().id());
        helper.assertTrue(candidates.contains(WILD) && !candidates.contains(WILD_PLAYER),
                "The wild provinces the state may take: " + candidates);
        helper.succeedWhen(() -> {
            if (!state.kingdom().id().equals(map.owner(WILD))) {
                throw new net.minecraft.gametest.framework.GameTestAssertException("The state did not take the wild province yet");
            }
            helper.assertTrue(map.owner(WILD_PLAYER) == null, "The state took the province next to a player");
            end(helper, state.kingdom(), player);
        });
    }
}
