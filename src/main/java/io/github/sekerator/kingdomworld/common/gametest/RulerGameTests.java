package io.github.sekerator.kingdomworld.common.gametest;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplates;
import io.github.sekerator.kingdomworld.common.economy.ItemPrices;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.npc.NpcRulers;
import io.github.sekerator.kingdomworld.common.npc.NpcSpawner;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.trade.TradeSettings;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.TreasuryJournal;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.RulerDecision;
import io.github.sekerator.kingdomworld.core.npc.RulerMemory;
import io.github.sekerator.kingdomworld.core.npc.RulerMind;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import io.github.sekerator.kingdomworld.core.trade.Deal;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Game tests of the rulers of the kingdoms of the world, on the platform high above the test place that the tests of the
 * kingdoms of the world use: a small kingdom appears there, its ruler decides, and the test looks at what came of it.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RulerGameTests {
    /** Steps of the jobs of rulers a test runs at most. */
    private static final int JOB_STEPS = 400;
    /** Blocks east of the place where a kingdom of players is founded: as close as the rules of founding allow. */
    private static final int NEIGHBOURS = 136;

    private RulerGameTests() {
    }

    private static NpcKingdom appear(GameTestHelper helper, NpcSite site) {
        MinecraftServer server = helper.getLevel().getServer();
        NpcSpawner.runNow(server, site, NpcGameTests.STEPS);
        helper.assertTrue(site.status() == NpcSite.Status.SPAWNED, "The kingdom of the world did not appear: " + site + " " + site.reason());
        Kingdom kingdom = NpcGameTests.kingdomOf(helper, site);
        return NpcWork.kingdom(server, kingdom.id()).orElseThrow();
    }

    /**
     * A kingdom of players with its throne at a place, for neighbours and partners.
     */
    private static UUID neighbours(GameTestHelper helper, BlockPos throne, String name, UUID king) {
        ServerLevel level = helper.getLevel();
        level.setBlockAndUpdate(throne, ModRegistries.THRONE.get().defaultBlockState());
        CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, throne, name, 0x2E86C1, king);
        helper.assertTrue(result instanceof CreateKingdomResult.Created, "The neighbours were not founded: " + result);
        return ((CreateKingdomResult.Created) result).kingdom().id();
    }

    @GameTest(template = NpcGameTests.EMPTY, batch = "kingdomworld_ruler_house", timeoutTicks = 2400)
    public static void aRulerWhoseBedsAreTakenLaysOutAHouseAndBuildersBuildIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = NpcGameTests.platform(helper);
        NpcSite site = NpcGameTests.plant(helper, center);
        NpcKingdom npc = appear(helper, site);
        UUID id = npc.kingdomId();
        long day = TreasuryActions.day(server);
        // A ring of land around the kingdom, so that its plots beyond the wall are its own (claims of rulers need ticks).
        Territory land = KingdomWorldApi.state(server).territory().territory(id).orElseThrow();
        for (long chunk : List.copyOf(land.chunks())) {
            for (int[] side : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}}) {
                KingdomWorldApi.claimChunk(level, new net.minecraft.world.level.ChunkPos(ChunkKey.x(chunk) + side[0], ChunkKey.z(chunk) + side[1]), id,
                        null);
            }
        }
        boolean laid = false;
        for (int turn = 0; turn < 4 && !laid; turn++) {
            Optional<RulerMind.Plan> plan = NpcRulers.turn(server, NpcWork.kingdom(server, id).orElse(npc), day, true);
            helper.assertTrue(plan.isPresent(), "The ruler took no turn");
            NpcRulers.runJobs(server, JOB_STEPS);
            boolean wanted = plan.get().decisions().stream().anyMatch(d -> d.kind() == RulerDecision.Kind.BUILD && d.subject().equals("house"))
                    || plan.get().needs().stream().anyMatch(n -> n.kind() == RulerMemory.NeedKind.BUILDING && n.subject().equals("house"));
            helper.assertTrue(wanted || turn > 0, "The beds are taken but the ruler wants no house: " + plan.get());
            laid = house(server, id).isPresent();
        }
        helper.assertTrue(laid, "No house was laid out: " + NpcWork.state(server).ruler(id).needs() + "; " + NpcRulers.lastBuilds(id));
        Construction house = house(server, id).orElseThrow();
        helper.assertTrue(house.orderMissing(), "The house does not order what it lacks");
        // The kingdom bought what the house lacks (abroad and from the workshops, which takes days): it is in the storehouses now.
        io.github.sekerator.kingdomworld.common.construction.ConstructionWork.plan(house).ifPresent(plan ->
                io.github.sekerator.kingdomworld.common.construction.ConstructionWork.missing(server, house, plan).forEach((item, count) -> {
                    net.minecraft.world.item.Item found = io.github.sekerator.kingdomworld.common.trade.GoodsStacks.item(item);
                    for (int left = count; left > 0 && found != net.minecraft.world.item.Items.AIR; left -= 64) {
                        io.github.sekerator.kingdomworld.common.storage.KingdomStorage.insert(server, id,
                                new net.minecraft.world.item.ItemStack(found, Math.min(64, left)), null);
                    }
                }));
        helper.succeedWhen(() -> {
            Optional<Construction> current = KingdomWorldApi.state(server).construction().get(house.id());
            boolean built = current.isEmpty() || current.get().doneCount() > 0 || !current.get().pending().isEmpty();
            helper.assertTrue(built, "The builders have not started the house: " + current.map(c -> c.status().id()).orElse("-"));
            NpcGameTests.cleanUp(helper, site, center);
        });
    }

    private static Optional<Construction> house(MinecraftServer server, UUID id) {
        return KingdomWorldApi.state(server).construction().of(id).stream()
                .filter(c -> BuildingTemplates.get(c.templateId()).map(t -> t.role().equals("house")).orElse(false)).findFirst();
    }

    @GameTest(template = NpcGameTests.EMPTY, batch = "kingdomworld_ruler_land", timeoutTicks = 1200)
    public static void aRulerNeverClaimsTheLandOfOthersNorWherePlayersLived(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = NpcGameTests.platform(helper);
        NpcSite site = NpcGameTests.plant(helper, center);
        NpcKingdom npc = appear(helper, site);
        UUID id = npc.kingdomId();
        KingdomWorldState state = KingdomWorldApi.state(server);
        UUID neighbour = neighbours(helper, center.offset(NEIGHBOURS, 1, 0), "GameTest Border", UUID.randomUUID());
        String dimension = state.territory().territory(id).orElseThrow().dimension();
        Set<Long> theirs = new HashSet<>(state.territory().territory(neighbour).orElseThrow().chunks());
        // Players lived just west of the land of the kingdom.
        Territory own = state.territory().territory(id).orElseThrow();
        int west = own.chunks().stream().mapToInt(ChunkKey::x).min().orElseThrow() - 1;
        long lived = ChunkKey.of(west, center.getZ() >> 4);
        level.getChunk(west, center.getZ() >> 4).setInhabitedTime(10L * 60 * 60 * 20);
        int before = own.size();
        int limit = io.github.sekerator.kingdomworld.common.npc.NpcSettings.rulers().maxChunks(npc.level());
        NpcRulers.carryOutNow(server, npc, RulerDecision.of(RulerDecision.Kind.CLAIM, "", limit));
        String[] problem = {null};
        helper.succeedWhen(() -> {
            if (problem[0] == null) {
                helper.assertTrue(!NpcRulers.working(), "The claim goes on");
                Territory after = state.territory().territory(id).orElseThrow();
                problem[0] = landProblem(state, after, before, lived, theirs, neighbour, dimension, id, limit);
                level.getChunk(ChunkKey.x(lived), ChunkKey.z(lived)).setInhabitedTime(0);
                KingdomWorldApi.dissolveKingdom(server, neighbour);
                level.setBlockAndUpdate(center.offset(NEIGHBOURS, 1, 0), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                NpcGameTests.cleanUp(helper, site, center);
            }
            helper.assertTrue(problem[0].isEmpty(), problem[0]);
        });
    }

    /**
     * What is wrong with the land a ruler claimed, or an empty text.
     */
    private static String landProblem(KingdomWorldState state, Territory after, int before, long lived, Set<Long> theirs, UUID neighbour,
                                      String dimension, UUID id, int limit) {
        if (after.size() <= before) {
            return "No land was claimed: " + after.size();
        }
        if (after.chunks().contains(lived)) {
            return "Land where players lived was claimed";
        }
        int buffer = KingdomWorldApi.rules().claimBufferChunks();
        for (long chunk : after.chunks()) {
            if (theirs.contains(chunk)) {
                return "A chunk of the neighbours was claimed";
            }
            if (state.territory().hasForeignLandWithin(dimension, chunk, buffer, id)) {
                return "A chunk next to the land of the neighbours was claimed: " + ChunkKey.x(chunk) + " " + ChunkKey.z(chunk);
            }
        }
        if (!state.territory().territory(neighbour).map(t -> t.chunks().equals(theirs)).orElse(false)) {
            return "The land of the neighbours changed";
        }
        return after.size() <= limit ? "" : "The kingdom grew beyond its limit: " + after.size();
    }

    @GameTest(template = NpcGameTests.EMPTY, batch = "kingdomworld_ruler_deal", timeoutTicks = 200)
    public static void aRulerTakesAFairDealOfAPlayerAndDeclinesARobbingOne(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = NpcGameTests.platform(helper);
        NpcSite site = null;
        UUID others = null;
        try {
            site = NpcGameTests.plant(helper, center);
            NpcKingdom npc = appear(helper, site);
            UUID id = npc.kingdomId();
            UUID king = UUID.randomUUID();
            others = neighbours(helper, center.offset(NEIGHBOURS, 1, 0), "GameTest Traders", king);
            UUID traders = others;
            KingdomWorldState state = KingdomWorldApi.state(server);
            long day = TreasuryActions.day(server);
            state.economy().adminTreasury(traders, 5_000, day);
            long worth = Math.max(1, ItemPrices.table(server).value("minecraft:cobblestone").units()) * 32;
            long now = KingdomWorldApi.gameTime(server);
            Deal fair = state.proposeDeal(king, traders, id, false, "minecraft:cobblestone", 32, worth, true, 0, now, TradeSettings.rules())
                    .deal().orElse(null);
            Deal robbing = state.proposeDeal(king, traders, id, false, "minecraft:cobblestone", 32, 1, true, 0, now, TradeSettings.rules())
                    .deal().orElse(null);
            helper.assertTrue(fair != null && robbing != null, "The deals were not offered");
            NpcRulers.turn(server, npc, day, false);
            Deal answered = state.trade().deal(fair.id()).orElseThrow();
            helper.assertTrue(answered.status() == Deal.Status.ACTIVE || answered.status() == Deal.Status.DONE,
                    "The fair deal was not taken: " + answered.status() + " " + NpcWork.state(server).ruler(id).decrees());
            Deal refused = state.trade().deal(robbing.id()).orElseThrow();
            helper.assertTrue(refused.status() == Deal.Status.DECLINED, "The robbing deal was not declined: " + refused.status());
            RulerMemory memory = NpcWork.state(server).ruler(id);
            helper.assertTrue(memory.decrees().stream().anyMatch(d -> d.decision().kind() == RulerDecision.Kind.DECLINE
                    && d.decision().subject().equals(RulerMind.PRICE_LOW)), "The refusal has no reason: " + memory.decrees());
            helper.assertTrue(memory.dealings(king) != 0, "The ruler does not remember its dealings with the king");
            helper.succeed();
        } finally {
            if (others != null) {
                KingdomWorldApi.dissolveKingdom(server, others);
            }
            level.setBlockAndUpdate(center.offset(NEIGHBOURS, 1, 0), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            NpcGameTests.cleanUp(helper, site, center);
        }
    }

    @GameTest(template = NpcGameTests.EMPTY, batch = "kingdomworld_ruler_money", timeoutTicks = 200)
    public static void turnsOfARulerMakeNoMoneyAndTheJournalAgrees(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = NpcGameTests.platform(helper);
        NpcSite site = null;
        try {
            site = NpcGameTests.plant(helper, center);
            NpcKingdom npc = appear(helper, site);
            UUID id = npc.kingdomId();
            long day = TreasuryActions.day(server);
            TreasuryJournal.Totals foundingBefore = founding(server, id, day);
            long[] money = NpcRulers.runDays(server, npc, 5);
            helper.assertTrue(money[1] == money[0] + money[2] - money[3], "The treasury went from " + money[0] + " to " + money[1]
                    + " while the journal says +" + money[2] + " -" + money[3]);
            TreasuryJournal.Totals foundingAfter = founding(server, id, day);
            helper.assertTrue(foundingAfter.income() == foundingBefore.income(), "Money came from nothing as a founding");
            long admin = KingdomWorldApi.treasury(server, id).orElseThrow().journal().totals(day, TreasuryJournal.Period.WEEK)
                    .getOrDefault(LedgerCategory.ADMIN, TreasuryJournal.Totals.ZERO).income();
            helper.assertTrue(admin == 0, "Money came from an operator");
            helper.assertTrue(NpcWork.state(server).ruler(id).turns() >= 5, "The turns were not taken");
            NpcRulers.runJobs(server, JOB_STEPS);
            helper.succeed();
        } finally {
            NpcGameTests.cleanUp(helper, site, center);
        }
    }

    private static TreasuryJournal.Totals founding(MinecraftServer server, UUID id, long day) {
        return KingdomWorldApi.treasury(server, id).orElseThrow().journal().totals(day, TreasuryJournal.Period.WEEK)
                .getOrDefault(LedgerCategory.FOUNDING, TreasuryJournal.Totals.ZERO);
    }

    @GameTest(template = NpcGameTests.EMPTY, batch = "kingdomworld_ruler_chronicle", timeoutTicks = 200)
    public static void anImportantDecreeIsWrittenInTheChronicle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = NpcGameTests.platform(helper);
        NpcSite site = null;
        try {
            site = NpcGameTests.plant(helper, center);
            NpcKingdom npc = appear(helper, site);
            UUID id = npc.kingdomId();
            long tax = KingdomWorldApi.treasury(server, id).orElseThrow().pollTax() + 7;
            NpcRulers.carryOutNow(server, npc, RulerDecision.of(RulerDecision.Kind.TAX, "", tax).withExtra(70));
            RulerMemory memory = NpcWork.state(server).ruler(id);
            helper.assertTrue(!memory.chronicle().isEmpty() && memory.chronicle().get(0).decision().kind() == RulerDecision.Kind.TAX,
                    "The decree is not in the chronicle: " + memory.chronicle());
            helper.assertTrue(KingdomWorldApi.treasury(server, id).orElseThrow().pollTax() == tax, "The tax was not set");
            helper.assertTrue(NpcRulers.chronicle(server, id).size() == memory.chronicle().size(), "The chronicle is not shown");
            // A feast of the life of towns, paid from the treasury like the feast of a king.
            helper.assertTrue(io.github.sekerator.kingdomworld.common.npc.RulerFestivals.available(), "Rulers have no host of feasts");
            long before = KingdomWorldApi.treasury(server, id).orElseThrow().funds();
            NpcRulers.carryOutNow(server, npc, RulerDecision.of(RulerDecision.Kind.FESTIVAL, "", 0));
            helper.assertTrue(memory.decrees().get(0).decision().kind() == RulerDecision.Kind.FESTIVAL, "No feast was held: " + memory.decrees());
            helper.assertTrue(KingdomWorldApi.treasury(server, id).orElseThrow().funds() <= before, "A feast brought money");
            // A decree of no importance is remembered but not written.
            NpcRulers.carryOutNow(server, npc, RulerDecision.of(RulerDecision.Kind.ENTRY_FEE, "", 33));
            helper.assertTrue(memory.decrees().get(0).decision().kind() == RulerDecision.Kind.ENTRY_FEE, "The fee is not remembered");
            helper.assertTrue(memory.chronicle().stream().noneMatch(d -> d.decision().kind() == RulerDecision.Kind.ENTRY_FEE),
                    "A small decree went to the chronicle");
            helper.succeed();
        } finally {
            NpcGameTests.cleanUp(helper, site, center);
        }
    }
}
