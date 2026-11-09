package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.ItemPrices;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.item.GuideBookItem;
import io.github.sekerator.kingdomworld.common.npc.NpcRulers;
import io.github.sekerator.kingdomworld.common.npc.NpcSettings;
import io.github.sekerator.kingdomworld.common.npc.NpcSpawner;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.TreasuryJournal;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.RulerDecision;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Kingdoms of the world that stand as data from the start (land, ruler, treasury) and are raised in the world later, and the
 * guide book given once on the first join.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldKingdomGameTests {
    private WorldKingdomGameTests() {
    }

    private static int land(MinecraftServer server, UUID kingdom) {
        return KingdomWorldApi.state(server).territory().territory(kingdom).map(Territory::size).orElse(0);
    }

    private static long founding(MinecraftServer server, UUID kingdom) {
        return KingdomWorldApi.treasury(server, kingdom).orElseThrow().journal()
                .totals(TreasuryActions.day(server), TreasuryJournal.Period.WEEK)
                .getOrDefault(LedgerCategory.FOUNDING, TreasuryJournal.Totals.ZERO).income();
    }

    @GameTest(template = NpcGameTests.EMPTY, batch = "kingdomworld_npc_data", timeoutTicks = 200)
    public static void aKingdomOfTheWorldStandsAsDataFirstAndIsRaisedLater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = NpcGameTests.platform(helper);
        NpcSite site = null;
        try {
            site = NpcWork.planAt(server, center.getX(), center.getZ(), NpcLevel.SMALL, List.of()).orElse(null);
            helper.assertTrue(site != null, "The data of the kingdoms of the world is read-only");
            // The platform stands where the generator knows no ground: the square is levelled to it.
            site.setGroundY(NpcGameTests.PLATFORM);
            helper.assertTrue(NpcWork.foundNow(server, site), "The kingdom was not founded as data: " + site + " " + site.reason());
            Kingdom kingdom = NpcGameTests.kingdomOf(helper, site);
            helper.assertTrue(site.status() == NpcSite.Status.FOUNDED && !NpcWork.built(server, kingdom.id()), "Not waiting for its buildings: " + site);
            helper.assertTrue(!level.getBlockState(NpcSpawner.thronePos(site)).is(ModRegistries.THRONE.get()), "A throne stands before the buildings");
            int radius = NpcSettings.rules().landRadius(NpcLevel.SMALL);
            int held = land(server, kingdom.id());
            helper.assertTrue(held == (2 * radius + 1) * (2 * radius + 1), "Land of " + held + " chunks");
            long money = NpcSettings.rules().money(NpcLevel.SMALL);
            helper.assertTrue(KingdomWorldApi.treasury(server, kingdom.id()).orElseThrow().balance() == money && founding(server, kingdom.id()) == money,
                    "The treasury does not hold its founding money");
            helper.assertTrue(NpcWork.plannedPeople(server, kingdom.id()).orElse(0) > 0, "No people are planned for the map");

            // The ruler grows the land by the data, without its buildings.
            NpcKingdom record = NpcWork.kingdom(server, kingdom.id()).orElseThrow();
            NpcRulers.carryOutNow(server, record, RulerDecision.of(RulerDecision.Kind.CLAIM, "", 2));
            for (int i = 0; i < 400 && !NpcRulers.runJobs(server, 1); i++) {
                try {
                    // Chunks that are not loaded are read from their region files off the main thread.
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            helper.assertTrue(NpcRulers.runJobs(server, 1), "The claim of the ruler did not end");
            int grown = land(server, kingdom.id());
            helper.assertTrue(grown == held + 2, "The land grew from " + held + " to " + grown);

            // Raising the buildings later gives nothing twice.
            NpcSpawner.runNow(server, site, NpcGameTests.STEPS);
            helper.assertTrue(site.status() == NpcSite.Status.SPAWNED, "The buildings were not raised: " + site + " at " + site.stage().id() + " "
                    + site.reason());
            helper.assertTrue(level.getBlockState(NpcSpawner.thronePos(site)).is(ModRegistries.THRONE.get()), "No throne once the buildings stand");
            helper.assertTrue(KingdomWorldApi.kingdomWithThroneAt(level, NpcSpawner.thronePos(site)).map(k -> k.id().equals(kingdom.id())).orElse(false),
                    "The throne is not the heart of the kingdom");
            helper.assertTrue(NpcWork.built(server, kingdom.id()), "The kingdom does not count as built");
            helper.assertTrue(founding(server, kingdom.id()) == money, "The founding money came twice: " + founding(server, kingdom.id()));
            helper.assertTrue(land(server, kingdom.id()) >= grown, "Land was lost while the buildings were raised");
            helper.assertTrue(!KingdomWorldApi.state(server).construction().buildingsOf(kingdom.id()).isEmpty(), "No buildings");
            helper.succeed();
        } finally {
            NpcGameTests.cleanUp(helper, site, center);
        }
    }

    @GameTest(template = NpcGameTests.EMPTY, batch = "kingdomworld_guide", timeoutTicks = 40)
    public static void theGuideIsGivenOnceAndIsWorthNothing(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), "GuideReader"));
        ItemStack book = new ItemStack(ModRegistries.GUIDE_BOOK.get());
        helper.assertTrue(GuideBookItem.giveOnFirstJoin(player), "The book was not given on the first join");
        helper.assertTrue(!GuideBookItem.giveOnFirstJoin(player), "The book was given on a second join");
        helper.assertTrue(player.getInventory().countItem(book.getItem()) == 1, "Books: " + player.getInventory().countItem(book.getItem()));
        helper.assertTrue(!GuideBookItem.giveAgain(player), "A second book was given to a player who carries one");
        player.getInventory().clearContent();
        helper.assertTrue(GuideBookItem.giveAgain(player) && player.getInventory().countItem(book.getItem()) == 1, "The book was not given again");
        helper.assertTrue(ItemPrices.value(server, book).units() == 0, "The book is worth money: " + ItemPrices.value(server, book).units());
        helper.succeed();
    }
}
