package io.github.sekerator.kingdomworld.common.gametest;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.commerce.CommerceSavedData;
import io.github.sekerator.kingdomworld.common.commerce.CommerceWork;
import io.github.sekerator.kingdomworld.common.commerce.ShopActions;
import io.github.sekerator.kingdomworld.common.commerce.WorldShops;
import io.github.sekerator.kingdomworld.common.economy.EconomyEvents;
import io.github.sekerator.kingdomworld.common.economy.Households;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.network.packet.CommercePackets;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.commerce.Business;
import io.github.sekerator.kingdomworld.core.commerce.GoodsCategory;
import io.github.sekerator.kingdomworld.core.economy.CityPurses;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcNames;
import io.github.sekerator.kingdomworld.core.npc.RulerCharacter;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Game tests of the shops of citizens in the kingdoms of the world: a player buys in a shop of a capital of the world, and the tax
 * of the ruler on the wages of its citizens comes out of the purses of its cities, the same sum as before.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldShopGameTests {
    private static final String EMPTY = "empty";

    private WorldShopGameTests() {
    }

    /**
     * A kingdom founded for the test and made a kingdom of the world.
     */
    private static Kingdom world(GameTestHelper helper, String name, UUID king) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, name, king);
        long day = TreasuryActions.day(server);
        NpcWork.state(server).putKingdom(new NpcKingdom(kingdom.id(), -1, NpcLevel.SMALL, new NpcNames.Name(name, name), RulerCharacter.BUILDER,
                List.of(RulerCharacter.Trait.PATIENT), 1234, Gender.MALE, List.of(), KingdomWorldApi.gameTime(server), day));
        return kingdom;
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_world_shop_buy", timeoutTicks = 200)
    public static void aPlayerBuysInAShopOfACapitalOfTheWorld(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID king = UUID.randomUUID();
        Kingdom kingdom = world(helper, "GameTest World Shop", king);
        Citizen keeper = CitizenGameTests.newcomer(helper, kingdom, 5, 5);
        try {
            helper.assertTrue(WorldShops.active(server, kingdom.id()), "The kingdom of the world keeps no shops");
            Economy economy = KingdomWorldApi.state(server).economy();
            long day = TreasuryActions.day(server);
            WorldPos at = CitizenGameTests.at(helper, 5, 0, 5);
            Business.Place place = new Business.Place(Business.PlaceKind.MERCHANT_HOUSE, 1, 0, at.dimension(), at.x(), at.y(), at.z());
            Business shop = CommerceSavedData.get(server).open(kingdom.id(), 0, place, day, 200);
            shop.changeTrade(Business.GENERAL_STORE, day);
            shop.setKeepers(List.of(keeper.id()), false);
            shop.setStatus(Business.Status.TRADING);
            keeper.setBusiness(shop.id());
            shop.addGoods("minecraft:bread", GoodsCategory.FOOD.ordinal(), 10, 2_000);
            ServerPlayer player = CraftGameTests.player(helper, king, 5, 6);
            economy.adminAccount(king, kingdom.id(), 1_000);
            CityPurses.PurseKey till = CityPurses.PurseKey.till(kingdom.id(), shop.id());
            CityPurses.PurseKey cityTreasury = CityPurses.PurseKey.cityTreasury(kingdom.id(), 0);
            long tillBefore = economy.purse(till);
            long taxBefore = economy.purse(cityTreasury);
            var feedback = ShopActions.purchase(player, shop, new CommercePackets.ShopTrade(shop.id(), CommercePackets.ShopTrade.Action.BUY,
                    "minecraft:bread", 4, 1_000_000, true));
            helper.assertTrue(feedback.success(), "The player could not buy in the shop of the world: " + feedback);
            helper.assertTrue(player.getInventory().countItem(Items.BREAD) == 4, "The bread is not with the player");
            helper.assertTrue(economy.purse(till) > tillBefore, "The till got nothing");
            int tax = CommerceWork.salesTax(server, kingdom.id(), 0);
            helper.assertTrue(tax == KingdomWorldApi.state(server).economy().treasury(kingdom.id()).orElseThrow().salesTaxPermille(),
                    "The city of the ruler does not take the sales tax of its market");
            helper.assertTrue(tax == 0 || economy.purse(cityTreasury) > taxBefore, "The sales tax did not go to the city");
            helper.assertTrue(CommerceWork.crownShare(server, kingdom.id(), 0) == 1000, "The treasury of a city of the world keeps a share");
        } finally {
            NpcWork.state(server).removeKingdom(kingdom.id());
            CitizenGameTests.finish(helper, kingdom, keeper);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_world_shop_tax", timeoutTicks = 200)
    public static void theTaxOfARulerOfTheWorldComesOutOfThePursesAsBefore(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = world(helper, "GameTest World Tax", UUID.randomUUID());
        Citizen farmer = CitizenGameTests.newcomer(helper, kingdom, 4, 4);
        try {
            KingdomWorldApi.change(server, "test", state -> state.setProfession(null, farmer.id(), "farmer"));
            Economy economy = KingdomWorldApi.state(server).economy();
            long day = TreasuryActions.day(server);
            economy.adminTreasury(kingdom.id(), 5_000, day);
            // The first payday of a kingdom only starts the count.
            EconomyEvents.payday(server, kingdom, day + 1);
            long before = economy.treasury(kingdom.id()).orElseThrow().funds();
            Economy.Payday payday = EconomyEvents.payday(server, kingdom, day + 2);
            long wage = payday.pay().getOrDefault(farmer.id(), 0L);
            helper.assertTrue(wage > 0, "The farmer was not paid");
            int percent = Math.max(0, Math.min(100 - Households.rules().sharePercent(),
                    io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig.SERVER.npcKingdoms.citizenTaxPercent.get()));
            long after = economy.treasury(kingdom.id()).orElseThrow().funds();
            helper.assertTrue(after == before - wage + wage * percent / 100, "The ruler got another tax than before: " + (after - before + wage)
                    + " of " + wage * percent / 100);
            helper.assertTrue(economy.purse(CityPurses.PurseKey.cityPurse(kingdom.id(), 0)) == 0,
                    "The rest of the wage stayed in the purse instead of leaving the world as before");
        } finally {
            NpcWork.state(server).removeKingdom(kingdom.id());
            CitizenGameTests.finish(helper, kingdom, farmer);
        }
        helper.succeed();
    }
}
