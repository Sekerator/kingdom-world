package io.github.sekerator.kingdomworld.common.gametest;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.npc.NpcRulers;
import io.github.sekerator.kingdomworld.common.npc.NpcSettings;
import io.github.sekerator.kingdomworld.common.npc.RulerActions;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.npc.NpcSpawner;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.trade.TradeSettings;
import io.github.sekerator.kingdomworld.common.trade.TradeWork;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcRules;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.trade.ExchangeRates;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Game tests of the rich crowns of the world: their currency keeps its worth, and the kingdoms of older worlds get their wealth
 * once.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RichRulerGameTests {
    private RichRulerGameTests() {
    }

    /**
     * A small kingdom of the world founded as data on the platform, without its buildings.
     */
    static NpcSite founded(GameTestHelper helper, BlockPos center) {
        MinecraftServer server = helper.getLevel().getServer();
        NpcSite site = NpcWork.planAt(server, center.getX(), center.getZ(), NpcLevel.SMALL, List.of()).orElse(null);
        helper.assertTrue(site != null, "The data of the kingdoms of the world is read-only");
        site.setGroundY(NpcGameTests.PLATFORM);
        helper.assertTrue(NpcWork.foundNow(server, site), "The kingdom was not founded as data: " + site + " " + site.reason());
        return site;
    }

    @GameTest(template = NpcGameTests.EMPTY, batch = "kingdomworld_rich_rate", timeoutTicks = 200)
    public static void theCurrencyOfARichCrownOfTheWorldKeepsItsWorth(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos center = NpcGameTests.platform(helper, NpcGameTests.HALF3);
        NpcSite site = null;
        try {
            site = founded(helper, center);
            Kingdom kingdom = NpcGameTests.kingdomOf(helper, site);
            long day = TreasuryActions.day(server);
            helper.assertTrue(KingdomWorldApi.state(server).economy().foundingGrant(kingdom.id(), 4_000_000, day).ok(), "The treasury took no money");
            // Fifteen days of rates: each moves a share of the way to the target, as the day of the rates does.
            long rate = ExchangeRates.ONE;
            for (int i = 0; i < 15; i++) {
                rate = ExchangeRates.smooth(rate, TradeWork.target(server, kingdom.id(), day), TradeSettings.rules());
            }
            helper.assertTrue(rate * 2 > ExchangeRates.ONE, "The rate of a rich crown fell to " + rate + " of " + ExchangeRates.ONE);
            helper.succeed();
        } finally {
            NpcGameTests.cleanUp(helper, site, center, NpcGameTests.HALF3);
        }
    }

    @GameTest(template = NpcGameTests.EMPTY, batch = "kingdomworld_rich_topup", timeoutTicks = 200)
    public static void aKingdomOfAnOlderWorldGetsItsWealthOnce(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos center = NpcGameTests.platform(helper, NpcGameTests.HALF3);
        NpcSite site = null;
        try {
            site = founded(helper, center);
            Kingdom kingdom = NpcGameTests.kingdomOf(helper, site);
            helper.assertTrue(site.given(NpcSite.MONEY) && site.given(NpcSite.WEALTH), "A new kingdom waits for a wealth it has");
            // The kingdom as an older version left it: its money given, the wealth of today not.
            site.restore(site.status(), kingdom.id(), site.stage(), site.step(), site.groundY(), site.reason(),
                    site.givenFlags() & ~NpcSite.WEALTH);
            long before = KingdomWorldApi.treasury(server, kingdom.id()).orElseThrow().balance();
            long delta = NpcSettings.rules().money(NpcLevel.SMALL) - NpcRules.LEGACY_MONEY[NpcLevel.SMALL.ordinal()];
            NpcSpawner.topUpWealth(server);
            NpcSpawner.topUpWealth(server);
            long after = KingdomWorldApi.treasury(server, kingdom.id()).orElseThrow().balance();
            helper.assertTrue(after - before == delta && site.given(NpcSite.WEALTH), "The treasury grew by " + (after - before) + " instead of "
                    + delta + " once");
            helper.succeed();
        } finally {
            NpcGameTests.cleanUp(helper, site, center, NpcGameTests.HALF3);
        }
    }

    @GameTest(template = NpcGameTests.EMPTY, batch = "kingdomworld_rich_posts", timeoutTicks = 2400)
    public static void aRulerSetsUpTheWorkItsKingdomLacks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos center = NpcGameTests.platform(helper);
        NpcSite site = null;
        try {
            site = NpcGameTests.plant(helper, center);
            NpcSpawner.runNow(server, site, NpcGameTests.STEPS);
            Kingdom kingdom = NpcGameTests.kingdomOf(helper, site);
            NpcKingdom npc = NpcWork.kingdom(server, kingdom.id()).orElseThrow();
            var posts = KingdomWorldApi.state(server).citizens().posts();
            long day = TreasuryActions.day(server);
            // Ore in the storehouses and nobody to smelt it: the ruler sets up what its kingdom lacks, a post a turn, the smelter among
            // them, and nothing twice.
            helper.assertTrue(posts.postsOf(kingdom.id(), "smelter").isEmpty(), "A small kingdom appeared with a smelter");
            KingdomStorage.insert(server, kingdom.id(), new ItemStack(Items.RAW_IRON, 16), null);
            List<String> set = new java.util.ArrayList<>();
            for (int turn = 0; turn < 3; turn++) {
                RulerActions.posts(server, npc, NpcRulers.look(server, npc, day)).ifPresent(set::add);
            }
            helper.assertTrue(set.contains("smelter") && set.size() == new java.util.HashSet<>(set).size(), "The ruler set up " + set);
            WorkPost smelter = posts.postsOf(kingdom.id(), "smelter").get(0);
            helper.assertTrue(!smelter.workers().isEmpty(), "Nobody works at the smelter");
            helper.succeed();
        } finally {
            NpcGameTests.cleanUp(helper, site, center);
        }
    }
}
