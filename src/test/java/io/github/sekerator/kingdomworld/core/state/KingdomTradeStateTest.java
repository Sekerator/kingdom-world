package io.github.sekerator.kingdomworld.core.state;

import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.membership.RolePermissions;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.trade.Caravan;
import io.github.sekerator.kingdomworld.core.trade.Deal;
import io.github.sekerator.kingdomworld.core.trade.Goods;
import io.github.sekerator.kingdomworld.core.trade.Market;
import io.github.sekerator.kingdomworld.core.trade.TradeRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KingdomTradeStateTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final long NOW = 5 * CitizenNeeds.TICKS_PER_DAY;
    private static final long DAY = 5;

    private final TradeRules rules = TradeRules.DEFAULT;
    private KingdomWorldState state;
    private Economy economy;
    private UUID kingA;
    private UUID kingB;
    private UUID chancellor;
    private UUID subject;
    private Kingdom a;
    private Kingdom b;

    @BeforeEach
    void setUp() {
        state = new KingdomWorldState();
        economy = state.economy();
        kingA = UUID.randomUUID();
        kingB = UUID.randomUUID();
        chancellor = UUID.randomUUID();
        subject = UUID.randomUUID();
        a = found("Amber Coast", kingA, 8);
        b = found("Birch Vale", kingB, 1_000);
        for (UUID player : new UUID[]{chancellor, subject}) {
            state.membership().invite(kingA, player, 1L);
            state.membership().acceptInvitation(player, a.id(), 1L);
        }
        state.membership().setRole(kingA, chancellor, Role.CHANCELLOR);
    }

    private Kingdom found(String name, UUID king, int x) {
        CreateKingdomResult result = state.found(UUID.randomUUID(), name, 0x336699, king, new Capital(OVERWORLD, x, 64, 8), NOW,
                KingdomRules.DEFAULT);
        return assertInstanceOf(CreateKingdomResult.Created.class, result).kingdom();
    }

    private long money(Kingdom kingdom) {
        return economy.supply(kingdom.id());
    }

    @Test
    void newRightsGoToTheirPositions() {
        RolePermissions defaults = RolePermissions.defaults();
        assertTrue(defaults.has(Role.TREASURER, Permission.MANAGE_MARKET) && defaults.has(Role.STEWARD, Permission.MANAGE_MARKET));
        assertTrue(defaults.has(Role.CHANCELLOR, Permission.MANAGE_TRADE) && defaults.has(Role.TREASURER, Permission.MANAGE_TRADE));
        assertFalse(defaults.has(Role.SUBJECT, Permission.MANAGE_MARKET) || defaults.has(Role.SUBJECT, Permission.MANAGE_TRADE));
        assertFalse(defaults.has(Role.MARSHAL, Permission.MANAGE_TRADE));
    }

    @Test
    void theServerChecksTheRightToManageTheMarket() {
        Market.SellEntry bread = new Market.SellEntry("minecraft:bread", 0, 10);
        assertEquals(Optional.of(KingdomError.NO_PERMISSION), state.putSell(subject, a.id(), bread, rules).failure());
        assertEquals(Optional.of(KingdomError.NO_PERMISSION), state.putSell(kingB, a.id(), bread, rules).failure());
        assertTrue(state.putSell(kingA, a.id(), bread, rules).ok());
        assertTrue(state.putSell(null, a.id(), new Market.SellEntry("minecraft:bread", 7, 0), rules).ok(), "Changing an entry");
        assertEquals(1, state.trade().market(a.id(), rules).sells().size());
        assertEquals(7, state.trade().market(a.id(), rules).sell("minecraft:bread").orElseThrow().price());
        assertEquals(Optional.of(KingdomError.UNKNOWN_ENTRY), state.removeSell(kingA, a.id(), "minecraft:apple").failure());
        for (int i = 0; i < rules.maxEntries(); i++) {
            state.putBuy(kingA, a.id(), new Market.BuyEntry("mod:item_" + i, 1, 10, 100), rules);
        }
        assertEquals(Optional.of(KingdomError.LIST_FULL),
                state.putBuy(kingA, a.id(), new Market.BuyEntry("mod:one_more", 1, 10, 100), rules).failure());
        assertEquals(Optional.of(KingdomError.VALUE_OUT_OF_RANGE), state.setSalesTax(kingA, a.id(), 999, rules).failure());
        assertTrue(state.setSalesTax(kingA, a.id(), 100, rules).ok());
        assertEquals(Optional.of(KingdomError.VALUE_OUT_OF_RANGE), state.setExchangeFee(kingA, a.id(), 500, rules).failure());
    }

    @Test
    void importsArePaidWhenOrderedAndLimitedPerDay() {
        assertEquals(Optional.of(KingdomError.NOT_ENOUGH_MONEY),
                state.orderImport(kingA, a.id(), "minecraft:iron_ingot", 10, 300, NOW, NOW + 100, rules).failure());
        economy.adminTreasury(a.id(), 100_000, DAY);
        long before = money(a);
        assertTrue(state.orderImport(kingA, a.id(), "minecraft:iron_ingot", 200, 6_000, NOW, NOW + 100, rules).ok());
        assertEquals(before - 6_000, money(a), "The money leaves the world when the order is paid");
        assertEquals(1, state.trade().market(a.id(), rules).imports().size());
        assertEquals(Optional.of(KingdomError.IMPORT_LIMIT),
                state.orderImport(kingA, a.id(), "minecraft:iron_ingot", rules.importMaxPerDay() - 199, 30, NOW, NOW + 100, rules).failure());
        assertTrue(state.orderImport(kingA, a.id(), "minecraft:iron_ingot", rules.importMaxPerDay() - 200, 30, NOW, NOW + 100, rules).ok());
        assertTrue(state.orderImport(kingA, a.id(), "minecraft:iron_ingot", 5, 30, NOW + CitizenNeeds.TICKS_PER_DAY, NOW + 100, rules).ok(),
                "A new day has a new limit");
        assertEquals(Optional.of(KingdomError.NO_PERMISSION),
                state.orderImport(subject, a.id(), "minecraft:iron_ingot", 1, 30, NOW, NOW + 100, rules).failure());
    }

    @Test
    void lotsSellWithTheTaxForTheTreasury() {
        UUID buyer = UUID.randomUUID();
        Goods sword = new Goods("minecraft:iron_sword", 1, "{Damage:3}");
        assertEquals(Optional.of(KingdomError.NOT_SUBJECT), state.listLot(buyer, a.id(), sword, 500, NOW, rules).failure());
        assertTrue(state.listLot(subject, a.id(), sword, 500, NOW, rules).ok());
        state.setSalesTax(kingA, a.id(), 100, rules);
        Market.Lot lot = state.trade().market(a.id(), rules).lots().get(0);
        assertEquals(Optional.of(KingdomError.TARGET_IS_SELF),
                state.buyLot(subject, a.id(), lot.id(), a.id(), 500, subject, DAY, rules).outcome().failure());
        assertEquals(Optional.of(KingdomError.NOT_ENOUGH_MONEY),
                state.buyLot(buyer, a.id(), lot.id(), a.id(), 500, buyer, DAY, rules).outcome().failure());
        economy.adminAccount(buyer, a.id(), 600);
        long before = money(a);
        KingdomWorldState.LotSale sale = state.buyLot(buyer, a.id(), lot.id(), a.id(), 500, buyer, DAY, rules);
        assertTrue(sale.outcome().ok());
        assertEquals(sword, sale.lot().orElseThrow().goods(), "The goods with their data");
        assertEquals(100, economy.accounts().balance(buyer, a.id()));
        assertEquals(450, economy.accounts().balance(subject, a.id()), "The seller gets the price less the tax");
        assertEquals(50, economy.treasury(a.id()).orElseThrow().funds(), "The treasury keeps the tax");
        assertEquals(before, money(a), "A sale makes no money");
        assertTrue(state.trade().market(a.id(), rules).lots().isEmpty());
        for (int i = 0; i < rules.maxLotsPerPlayer(); i++) {
            assertTrue(state.listLot(subject, a.id(), Goods.of("minecraft:dirt", 1), 5, NOW, rules).ok());
        }
        assertEquals(Optional.of(KingdomError.TOO_MANY_LOTS), state.listLot(subject, a.id(), Goods.of("minecraft:dirt", 1), 5, NOW, rules).failure());
        long id = state.trade().market(a.id(), rules).lots().get(0).id();
        assertTrue(state.withdrawLot(buyer, a.id(), id).isEmpty(), "Only the seller takes a lot back");
        assertTrue(state.withdrawLot(subject, a.id(), id).isPresent());
    }

    @Test
    void aDealSendsACaravanWithThePaymentInEscrow() {
        assertEquals(Optional.of(KingdomError.NO_PERMISSION), state.proposeDeal(subject, a.id(), b.id(), true, "minecraft:wheat", 32, 100, true,
                0, NOW, rules).outcome().failure());
        assertEquals(Optional.of(KingdomError.SAME_KINGDOM), state.proposeDeal(kingA, a.id(), a.id(), true, "minecraft:wheat", 32, 100, true,
                0, NOW, rules).outcome().failure());
        KingdomWorldState.Proposal proposal = state.proposeDeal(chancellor, a.id(), b.id(), true, "minecraft:wheat", 32, 100, false, 0, NOW,
                rules);
        assertTrue(proposal.outcome().ok());
        Deal deal = proposal.deal().orElseThrow();
        assertEquals(a.id(), deal.seller());
        assertEquals(b.id(), deal.currency(), "The price is in the buyer's currency");
        assertEquals(Optional.of(KingdomError.NOT_PARTNER), state.checkAnswer(kingA, a.id(), deal.id()).failure(), "The proposer cannot accept");
        assertTrue(state.checkAnswer(kingB, b.id(), deal.id()).ok());
        assertEquals(Optional.of(KingdomError.NOT_ENOUGH_MONEY), state.checkDelivery(deal).failure());

        economy.adminTreasury(b.id(), 1_000, DAY);
        long moneyA = money(a);
        long moneyB = money(b);
        Caravan caravan = state.sendCaravan(deal, List.of(Goods.of("minecraft:wheat", 32)), OVERWORLD, 8, 8, 1_000, 8, NOW, 2_400).orElseThrow();
        assertEquals(900, economy.treasury(b.id()).orElseThrow().funds(), "The payment left the buyer");
        assertEquals(100, economy.escrows().get(caravan.escrowId()).amount(), "and waits in escrow");
        assertEquals(moneyB, money(b), "Money in escrow is still money");
        assertEquals(0.5, caravan.progress(NOW + 1_200), 1e-9);

        assertTrue(economy.releaseEscrow(caravan.escrowId(), DAY).ok());
        assertEquals(100, economy.treasury(a.id()).orElseThrow().reserve(b.id()), "The seller holds the buyer's currency");
        assertEquals(moneyA, money(a));
        assertEquals(moneyB, money(b));
        assertTrue(economy.escrows().isEmpty());
    }

    @Test
    void aLostCaravanGivesThePaymentBack() {
        Deal deal = state.proposeDeal(kingB, b.id(), a.id(), false, "minecraft:iron_ingot", 4, 80, true, 3, NOW, rules).deal().orElseThrow();
        assertEquals(b.id(), deal.buyer());
        economy.adminTreasury(b.id(), 500, DAY);
        Caravan caravan = state.sendCaravan(deal, List.of(Goods.of("minecraft:iron_ingot", 4)), OVERWORLD, 8, 8, 1_000, 8, NOW, 2_400)
                .orElseThrow();
        assertEquals(420, economy.treasury(b.id()).orElseThrow().funds());
        assertTrue(economy.refundEscrow(caravan.escrowId(), DAY).ok());
        assertEquals(500, economy.treasury(b.id()).orElseThrow().funds(), "The payment came back");
        assertEquals(Optional.of(KingdomError.UNKNOWN_DEAL), economy.refundEscrow(caravan.escrowId(), DAY).failure(), "Only once");
    }

    @Test
    void dealsAreAnsweredOnceAndCalledOffByTheirKingdoms() {
        Deal deal = state.proposeDeal(kingA, a.id(), b.id(), true, "minecraft:oak_log", 64, 300, true, 2, NOW, rules).deal().orElseThrow();
        assertTrue(deal.regular());
        assertEquals(Optional.of(KingdomError.NOT_PARTNER), state.cancelDeal(kingB, b.id(), deal.id()).failure(),
                "Only the proposer takes a proposal back");
        assertTrue(state.declineDeal(kingB, b.id(), deal.id()).ok());
        assertEquals(Deal.Status.DECLINED, state.trade().deal(deal.id()).orElseThrow().status());
        assertEquals(Optional.of(KingdomError.DEAL_CLOSED), state.declineDeal(kingB, b.id(), deal.id()).failure());

        Deal second = state.proposeDeal(kingA, a.id(), b.id(), true, "minecraft:oak_log", 64, 300, true, 2, NOW, rules).deal().orElseThrow();
        state.trade().putDeal(second.withStatus(Deal.Status.ACTIVE));
        assertTrue(state.cancelDeal(kingB, b.id(), second.id()).ok(), "Either kingdom ends a regular deal");
        for (int i = 0; i < rules.maxDealsPerKingdom(); i++) {
            state.proposeDeal(kingA, a.id(), b.id(), true, "minecraft:stone", 1, 1, true, 0, NOW, rules);
        }
        assertEquals(Optional.of(KingdomError.TOO_MANY_DEALS), state.proposeDeal(kingA, a.id(), b.id(), true, "minecraft:stone", 1, 1, true, 0,
                NOW, rules).outcome().failure());
    }

    @Test
    void dissolvingTheBuyerSendsTheGoodsBackAndClosesItsMarket() {
        Deal deal = state.proposeDeal(kingA, a.id(), b.id(), true, "minecraft:wheat", 8, 50, true, 0, NOW, rules).deal().orElseThrow();
        UUID seller = UUID.randomUUID();
        state.membership().invite(kingB, seller, 1L);
        state.membership().acceptInvitation(seller, b.id(), 1L);
        assertTrue(state.listLot(seller, b.id(), Goods.of("minecraft:diamond", 3), 999, NOW, rules).ok());
        economy.adminTreasury(b.id(), 1_000, DAY);
        // The price is in the seller's currency: the buyer pays from its reserves of it.
        assertEquals(Optional.of(KingdomError.NOT_ENOUGH_MONEY), state.checkDelivery(deal).failure());
        economy.adminAccount(seller, a.id(), 100);
        assertTrue(economy.exchange(b.id(), a.id(), 100, 0, seller, seller, DAY).outcome().ok());
        Caravan caravan = state.sendCaravan(deal, List.of(Goods.of("minecraft:wheat", 8)), OVERWORLD, 8, 8, 1_000, 8, NOW, 2_400).orElseThrow();
        assertEquals(50, economy.treasury(b.id()).orElseThrow().reserve(a.id()));
        state.dissolve(b.id(), NOW + 10);
        assertEquals(Caravan.State.RETURNING, state.trade().caravan(caravan.id()).orElseThrow().state(), "The goods go back to the seller");
        assertTrue(economy.escrows().isEmpty());
        assertEquals(Deal.Status.CANCELLED, state.trade().deal(deal.id()).orElseThrow().status());
        assertTrue(state.trade().existing(b.id()).isEmpty(), "The market is closed");
        assertEquals(List.of(Goods.of("minecraft:diamond", 3)), state.trade().returnsOf(seller), "The lot waits for its seller");
    }
}
