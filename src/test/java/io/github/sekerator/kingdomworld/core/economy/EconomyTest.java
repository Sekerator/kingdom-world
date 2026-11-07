package io.github.sekerator.kingdomworld.core.economy;

import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyTest {
    private Economy economy;
    private UUID kingdom;
    private UUID alice;
    private UUID bob;
    private int changes;

    @BeforeEach
    void setUp() {
        economy = new Economy();
        economy.setChangeListener(() -> changes++);
        kingdom = UUID.randomUUID();
        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
        economy.open(kingdom, 0);
    }

    private Treasury treasury() {
        return economy.treasury(kingdom).orElseThrow();
    }

    private void give(UUID player, long amount) {
        assertTrue(economy.adminAccount(player, kingdom, amount).ok());
    }

    /**
     * Money on the books: treasury and accounts.
     */
    private long books() {
        return economy.supply(kingdom);
    }

    @Test
    void transfersMoveMoneyWithoutMakingAny() {
        give(alice, 500);
        long before = books();
        assertTrue(economy.transfer(alice, bob, kingdom, 120).ok());
        assertEquals(380, economy.accounts().balance(alice, kingdom));
        assertEquals(120, economy.accounts().balance(bob, kingdom));
        assertEquals(before, books());

        assertEquals(Optional.of(KingdomError.NOT_ENOUGH_MONEY), economy.transfer(alice, bob, kingdom, 381).failure());
        assertEquals(Optional.of(KingdomError.VALUE_OUT_OF_RANGE), economy.transfer(alice, bob, kingdom, 0).failure());
        assertEquals(Optional.of(KingdomError.VALUE_OUT_OF_RANGE), economy.transfer(alice, bob, kingdom, -5).failure());
        assertEquals(Optional.of(KingdomError.TARGET_IS_SELF), economy.transfer(alice, alice, kingdom, 5).failure());
        assertEquals(Optional.of(KingdomError.UNKNOWN_CURRENCY), economy.transfer(alice, bob, UUID.randomUUID(), 5).failure());
        assertEquals(before, books(), "refused transfers change nothing");
    }

    @Test
    void balancesNeverOverflow() {
        give(alice, Money.MAX);
        give(bob, 10);
        assertEquals(Optional.of(KingdomError.TOO_MUCH_MONEY), economy.transfer(bob, alice, kingdom, 10).failure());
        assertEquals(Optional.of(KingdomError.TOO_MUCH_MONEY), economy.adminAccount(alice, kingdom, 1).failure());
        assertEquals(Optional.of(KingdomError.TOO_MUCH_MONEY), economy.deposit(alice, kingdom, 1).failure());
        assertEquals(Money.MAX, economy.accounts().balance(alice, kingdom));
        assertEquals(10, economy.accounts().balance(bob, kingdom));
        assertTrue(economy.adminTreasury(kingdom, Money.MAX, 0).ok());
        assertEquals(Optional.of(KingdomError.TOO_MUCH_MONEY), economy.adminTreasury(kingdom, 1, 0).failure());
        assertEquals(Optional.of(KingdomError.TOO_MUCH_MONEY), economy.mint(kingdom, Denomination.GOLD, 1, true, 0).failure());
        assertEquals(Optional.of(KingdomError.TOO_MUCH_MONEY), economy.playerToTreasury(bob, kingdom, 10, 0).failure());
        assertEquals(10, economy.accounts().balance(bob, kingdom), "a refused payment takes nothing");
    }

    @Test
    void bankDepositsAndWithdrawalsChangeTheAccountByTheExactAmount() {
        assertTrue(economy.deposit(alice, kingdom, 125).ok());
        assertEquals(125, economy.accounts().balance(alice, kingdom));
        assertEquals(Optional.of(KingdomError.NOT_ENOUGH_MONEY), economy.withdraw(alice, kingdom, 126).failure());
        assertTrue(economy.withdraw(alice, kingdom, 100).ok());
        assertEquals(25, economy.accounts().balance(alice, kingdom));
        assertEquals(Optional.of(KingdomError.NO_COINS), economy.deposit(alice, kingdom, 0).failure());
        assertTrue(economy.withdraw(alice, kingdom, 25).ok());
        assertTrue(economy.accounts().of(alice).isEmpty(), "an empty account is forgotten");
    }

    @Test
    void theTreasuryPaysFromItsBalanceThenFromItsVault() {
        assertTrue(economy.mint(kingdom, Denomination.GOLD, 2, true, 0).ok());
        assertTrue(economy.mint(kingdom, Denomination.COPPER, 3, true, 0).ok());
        assertTrue(economy.adminTreasury(kingdom, 4, 0).ok());
        assertEquals(new Coins(3, 0, 2), treasury().vault());
        assertEquals(207, treasury().funds());
        long before = books();

        assertTrue(economy.treasuryToPlayer(kingdom, alice, 10, 1).ok());
        // 4 from the balance, 3 copper coins, then one gold coin with 97 change back on the balance.
        assertEquals(new Coins(0, 0, 1), treasury().vault());
        assertEquals(97, treasury().balance());
        assertEquals(197, treasury().funds());
        assertEquals(10, economy.accounts().balance(alice, kingdom));
        assertEquals(before, books(), "paying out moves money, it makes none");
        assertEquals(Optional.of(KingdomError.NOT_ENOUGH_MONEY), economy.treasuryToPlayer(kingdom, alice, 198, 1).failure());
        assertEquals(new TreasuryJournal.Totals(0, 10),
                treasury().journal().totals(1, TreasuryJournal.Period.TODAY).get(LedgerCategory.TRANSFERS));
        assertEquals(new TreasuryJournal.Totals(203, 0),
                treasury().journal().totals(1, TreasuryJournal.Period.YESTERDAY).get(LedgerCategory.MINTING));
    }

    @Test
    void mintedCoinsGoToTheVaultOrTheBalance() {
        assertTrue(economy.mint(kingdom, Denomination.SILVER, 3, false, 2).ok());
        assertEquals(30, treasury().balance());
        assertTrue(treasury().vault().isEmpty());
        assertEquals(Optional.of(KingdomError.VALUE_OUT_OF_RANGE), economy.mint(kingdom, Denomination.SILVER, 0, true, 2).failure());
        assertEquals(Optional.of(KingdomError.UNKNOWN_CURRENCY), economy.mint(UUID.randomUUID(), Denomination.SILVER, 1, true, 2).failure());
    }

    @Test
    void paymentsIntoTheTreasuryPayDebtsFirst() {
        give(alice, 100);
        economy.payday(kingdom, 1, List.of(alice), List.of(), EconomyRules.DEFAULT);
        assertTrue(economy.adminAccount(alice, kingdom, -90).ok());
        assertTrue(economy.setPollTax(kingdom, 30, 1_000).ok());
        economy.payday(kingdom, 2, List.of(alice), List.of(), EconomyRules.DEFAULT);
        assertEquals(30, treasury().debt(alice));
        give(alice, 50);
        assertTrue(economy.playerToTreasury(alice, kingdom, 50, 2).ok());
        assertEquals(0, treasury().debt(alice));
        assertEquals(new TreasuryJournal.Totals(30, 0), treasury().journal().totals(2, TreasuryJournal.Period.TODAY).get(LedgerCategory.TAXES));
        assertEquals(new TreasuryJournal.Totals(20, 0),
                treasury().journal().totals(2, TreasuryJournal.Period.TODAY).get(LedgerCategory.TRANSFERS));
    }

    @Test
    void taxesAreCollectedOnceADayAndShortfallsBecomeDebts() {
        give(alice, 25);
        give(bob, 100);
        assertEquals(10, treasury().pollTax(), "new kingdoms start with the tax of the config");
        Economy.Payday first = economy.payday(kingdom, 1, List.of(alice, bob), List.of(), EconomyRules.DEFAULT);
        assertEquals(2, first.taxes().size());
        assertEquals(15, economy.accounts().balance(alice, kingdom));
        assertEquals(90, economy.accounts().balance(bob, kingdom));
        assertEquals(20, treasury().balance());
        assertEquals(Economy.Payday.NONE, economy.payday(kingdom, 1, List.of(alice, bob), List.of(), EconomyRules.DEFAULT),
                "a second payday on the same day does nothing");

        assertTrue(economy.adminAccount(alice, kingdom, -15).ok());
        economy.payday(kingdom, 2, List.of(alice, bob), List.of(), EconomyRules.DEFAULT);
        assertEquals(10, treasury().debt(alice));
        economy.payday(kingdom, 3, List.of(alice, bob), List.of(), EconomyRules.DEFAULT);
        assertEquals(20, treasury().debt(alice), "the debt grows by the tax of each unpaid day");
        give(alice, 25);
        Economy.Payday fourth = economy.payday(kingdom, 4, List.of(alice, bob), List.of(), EconomyRules.DEFAULT);
        assertEquals(25, fourth.taxes().get(0).paid(), "old debt and the new tax are collected together");
        assertEquals(5, treasury().debt(alice));
        assertEquals(0, economy.accounts().balance(alice, kingdom));
        assertEquals(Optional.of(KingdomError.VALUE_OUT_OF_RANGE), economy.setPollTax(kingdom, 1_001, 1_000).failure());
    }

    @Test
    void wagesArePaidInFullOrInEqualShares() {
        UUID smith = UUID.randomUUID();
        UUID farmer = UUID.randomUUID();
        UUID idle = UUID.randomUUID();
        List<Economy.WageClaim> claims = List.of(new Economy.WageClaim(smith, "smith"), new Economy.WageClaim(farmer, "farmer"),
                new Economy.WageClaim(idle, "unemployed"));
        assertTrue(economy.adminTreasury(kingdom, 100, 0).ok());
        Economy.Payday full = economy.payday(kingdom, 1, List.of(), claims, EconomyRules.DEFAULT);
        assertEquals(10, full.pay().get(smith));
        assertEquals(6, full.pay().get(farmer));
        assertEquals(100, full.percents().get(smith));
        assertEquals(Payroll.NOTHING_EXPECTED, full.percents().get(idle));
        assertEquals(84, treasury().funds());
        assertEquals(0, full.summary().underpaid());

        assertTrue(economy.adminTreasury(kingdom, -76, 1).ok());
        Economy.Payday half = economy.payday(kingdom, 2, List.of(), claims, EconomyRules.DEFAULT);
        assertEquals(5, half.pay().get(smith));
        assertEquals(3, half.pay().get(farmer));
        assertEquals(50, half.percents().get(smith));
        assertEquals(2, half.summary().underpaid());
        assertEquals(0, treasury().funds());

        Economy.Payday none = economy.payday(kingdom, 3, List.of(), claims, EconomyRules.DEFAULT);
        assertEquals(0, none.pay().get(smith));
        assertEquals(0, none.percents().get(farmer));
        assertEquals(0, none.summary().percent());
        assertEquals(new TreasuryJournal.Totals(0, 8), treasury().journal().totals(2, TreasuryJournal.Period.TODAY).get(LedgerCategory.WAGES));
    }

    @Test
    void kingsSetWagesWithinTheLimit() {
        assertTrue(economy.setWage(kingdom, "smith", 25, 200, EconomyRules.DEFAULT).ok());
        assertEquals(25, treasury().wage("smith", EconomyRules.DEFAULT));
        assertEquals(6, treasury().wage("farmer", EconomyRules.DEFAULT));
        assertEquals(Optional.of(KingdomError.VALUE_OUT_OF_RANGE), economy.setWage(kingdom, "smith", 201, 200, EconomyRules.DEFAULT).failure());
        assertEquals(Optional.of(KingdomError.UNCHANGED), economy.setWage(kingdom, "smith", 25, 200, EconomyRules.DEFAULT).failure());
        assertTrue(economy.adminTreasury(kingdom, 1_000, 0).ok());
        Economy.Payday payday = economy.payday(kingdom, 1, List.of(), List.of(new Economy.WageClaim(UUID.randomUUID(), "smith")),
                EconomyRules.DEFAULT);
        assertEquals(200, payday.percents().values().iterator().next(), "pay above the usual wage counts up to twice");
    }

    @Test
    void theFirstPaydayOfUpgradedDataOnlyStartsTheCount() {
        UUID other = UUID.randomUUID();
        Treasury upgraded = new Treasury(other, CurrencyUnits.DEFAULT, 10);
        upgraded.restore("", 0, Coins.NONE, 10, 0, java.util.Map.of(), java.util.Map.of(), Treasury.NO_PAYDAY, Payroll.Summary.NONE);
        economy.restore(upgraded);
        give(alice, 0 + 1);
        assertEquals(Economy.Payday.NONE, economy.payday(other, 50, List.of(alice), List.of(), EconomyRules.DEFAULT));
        assertEquals(50, upgraded.lastPayday());
        assertEquals(0, upgraded.debt(alice));
    }

    @Test
    void currenciesOfDissolvedKingdomsBecomeOld() {
        give(alice, 40);
        economy.retire(kingdom, "Eldaria", 0x123456, 99);
        assertFalse(economy.isActive(kingdom));
        assertEquals("Eldaria", economy.retired().get(kingdom).kingdomName());
        assertEquals(40, economy.accounts().balance(alice, kingdom), "accounts in an old currency stay");
        assertEquals(Optional.of(KingdomError.OLD_CURRENCY), economy.transfer(alice, bob, kingdom, 10).failure());
        assertEquals(Optional.of(KingdomError.OLD_CURRENCY), economy.withdraw(alice, kingdom, 10).failure());
        assertEquals(Optional.of(KingdomError.OLD_CURRENCY), economy.deposit(alice, kingdom, 10).failure());
        assertEquals(CurrencyUnits.DEFAULT, economy.units(kingdom).orElseThrow());
    }

    @Test
    void currencyNamesFollowTheRulesOfKingdomNames() {
        UUID other = UUID.randomUUID();
        economy.open(other, 0);
        assertTrue(economy.renameCurrency(kingdom, "  Golden   Crown ").ok());
        assertEquals("Golden Crown", treasury().currencyName());
        assertEquals(Optional.of(KingdomError.CURRENCY_NAME_TAKEN), economy.renameCurrency(other, "golden crown").failure());
        assertEquals(Optional.of(KingdomError.INVALID_CURRENCY_NAME), economy.renameCurrency(other, "ab").failure());
        assertEquals(Optional.of(KingdomError.INVALID_CURRENCY_NAME), economy.renameCurrency(other, "Crown!").failure());
        assertTrue(economy.renameCurrency(other, "Крона Севера").ok());
        assertTrue(economy.renameCurrency(kingdom, "").ok(), "an empty name gives back the name of the kingdom");
        assertEquals("", treasury().currencyName());
    }

    @Test
    void everyChangeIsReportedForSaving() {
        int before = changes;
        give(alice, 5);
        assertTrue(changes > before);
        before = changes;
        assertTrue(economy.mint(kingdom, Denomination.COPPER, 1, true, 0).ok());
        assertTrue(changes > before);
    }
}
