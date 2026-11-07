package io.github.sekerator.kingdomworld.common.data;

import io.github.sekerator.kingdomworld.core.citizen.Profession;
import io.github.sekerator.kingdomworld.core.economy.Coins;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.economy.Payroll;
import io.github.sekerator.kingdomworld.core.economy.RetiredCurrency;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.economy.TreasuryJournal;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * NBT form of the money of the world (format 7): the treasury inside each kingdom's compound, the accounts of the
 * players, the old currencies, the exchange rates, payments in escrow and the coins in hands at the root.
 *
 * <pre>
 * treasury: { name, silver, gold, balance, vault: { copper, silver, gold }, pollTax, salesTax, wages: { profession: amount },
 *             debts: [ { player, amount } ], lastPayday, payroll: { day, due, paid, citizens, underpaid },
 *             journal: [ { day, entries: { category: { in, out } } } ], reserves: [ { currency, amount } ] }
 * economy:  { accounts: [ { player, currency, balance } ],
 *             retired: [ { id, name, kingdom, color, silver, gold, time } ],
 *             rates: [ { currency, rate } ], rateDay, escrows: [ { id, payer, payee, currency, amount } ], nextEscrow,
 *             coinsOut: [ { currency, amount } ] }
 * </pre>
 *
 * <p>Reading is strict like {@link KingdomNbt}: what cannot be understood throws {@link IllegalArgumentException}, so the
 * data becomes read-only instead of losing money.
 */
public final class EconomyNbt {
    static final String TREASURY = "treasury";
    static final String ECONOMY = "economy";
    private static final String NAME = "name";
    private static final String SILVER = "silver";
    private static final String GOLD = "gold";
    private static final String COPPER = "copper";
    private static final String BALANCE = "balance";
    private static final String VAULT = "vault";
    private static final String POLL_TAX = "pollTax";
    private static final String SALES_TAX = "salesTax";
    private static final String WAGES = "wages";
    private static final String DEBTS = "debts";
    private static final String PLAYER = "player";
    private static final String AMOUNT = "amount";
    private static final String LAST_PAYDAY = "lastPayday";
    private static final String PAYROLL = "payroll";
    private static final String DAY = "day";
    private static final String DUE = "due";
    private static final String PAID = "paid";
    private static final String CITIZENS = "citizens";
    private static final String UNDERPAID = "underpaid";
    private static final String JOURNAL = "journal";
    private static final String ENTRIES = "entries";
    private static final String IN = "in";
    private static final String OUT = "out";
    private static final String ACCOUNTS = "accounts";
    private static final String CURRENCY = "currency";
    private static final String RETIRED = "retired";
    private static final String ID = "id";
    private static final String KINGDOM = "kingdom";
    private static final String COLOR = "color";
    private static final String TIME = "time";

    private EconomyNbt() {
    }

    // ---------------------------------------------------------------- treasury

    public static CompoundTag write(Treasury treasury) {
        CompoundTag tag = new CompoundTag();
        tag.putString(NAME, treasury.currencyName());
        tag.putLong(SILVER, treasury.units().silver());
        tag.putLong(GOLD, treasury.units().gold());
        tag.putLong(BALANCE, treasury.balance());
        CompoundTag vault = new CompoundTag();
        vault.putLong(COPPER, treasury.vault().copper());
        vault.putLong(SILVER, treasury.vault().silver());
        vault.putLong(GOLD, treasury.vault().gold());
        tag.put(VAULT, vault);
        tag.putLong(POLL_TAX, treasury.pollTax());
        tag.putInt(SALES_TAX, treasury.salesTaxPermille());
        CompoundTag wages = new CompoundTag();
        treasury.wages().forEach(wages::putLong);
        tag.put(WAGES, wages);
        ListTag debts = new ListTag();
        treasury.debts().forEach((player, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(PLAYER, player);
            entry.putLong(AMOUNT, amount);
            debts.add(entry);
        });
        tag.put(DEBTS, debts);
        tag.putLong(LAST_PAYDAY, treasury.lastPayday());
        Payroll.Summary summary = treasury.lastPayroll();
        CompoundTag payroll = new CompoundTag();
        payroll.putLong(DAY, summary.day());
        payroll.putLong(DUE, summary.due());
        payroll.putLong(PAID, summary.paid());
        payroll.putInt(CITIZENS, summary.citizens());
        payroll.putInt(UNDERPAID, summary.underpaid());
        tag.put(PAYROLL, payroll);
        ListTag journal = new ListTag();
        treasury.journal().days().forEach((day, entries) -> {
            CompoundTag dayTag = new CompoundTag();
            dayTag.putLong(DAY, day);
            CompoundTag entriesTag = new CompoundTag();
            entries.forEach((category, totals) -> {
                CompoundTag totalsTag = new CompoundTag();
                totalsTag.putLong(IN, totals.income());
                totalsTag.putLong(OUT, totals.expense());
                entriesTag.put(category.id(), totalsTag);
            });
            dayTag.put(ENTRIES, entriesTag);
            journal.add(dayTag);
        });
        tag.put(JOURNAL, journal);
        ListTag reserves = new ListTag();
        treasury.reserves().forEach((currency, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(CURRENCY, currency);
            entry.putLong(AMOUNT, amount);
            reserves.add(entry);
        });
        tag.put("reserves", reserves);
        return tag;
    }

    /**
     * Reads the treasury of a kingdom. A kingdom of data without a treasury gets a new empty one whose first payday only
     * starts the count.
     *
     * @throws IllegalArgumentException if the tag cannot be understood
     */
    public static Treasury read(CompoundTag kingdomTag, UUID kingdomId, String name, CurrencyUnits defaults, long defaultPollTax) {
        if (!kingdomTag.contains(TREASURY)) {
            Treasury fresh = new Treasury(kingdomId, defaults, defaultPollTax);
            fresh.restore("", 0, Coins.NONE, defaultPollTax, 0, Map.of(), Map.of(), Treasury.NO_PAYDAY, Payroll.Summary.NONE);
            return fresh;
        }
        if (!kingdomTag.contains(TREASURY, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Treasury of " + name + " is not a compound");
        }
        CompoundTag tag = kingdomTag.getCompound(TREASURY);
        String where = "Treasury of " + name;
        long silver = tag.getLong(SILVER);
        long gold = tag.getLong(GOLD);
        if (!CurrencyUnits.valid(silver, gold)) {
            throw new IllegalArgumentException(where + " has invalid coin values " + silver + "/" + gold);
        }
        String currencyName = tag.getString(NAME);
        if (!currencyName.isEmpty() && (!KingdomNames.isValid(currencyName) || !currencyName.equals(KingdomNames.normalize(currencyName)))) {
            throw new IllegalArgumentException(where + " has an invalid currency name");
        }
        CompoundTag vaultTag = compound(tag, VAULT, where);
        Coins vault;
        try {
            vault = new Coins(vaultTag.getLong(COPPER), vaultTag.getLong(SILVER), vaultTag.getLong(GOLD));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(where + ": " + e.getMessage());
        }
        Map<String, Long> wages = new LinkedHashMap<>();
        CompoundTag wageTag = compound(tag, WAGES, where);
        for (String profession : wageTag.getAllKeys()) {
            if (!Profession.isValidId(profession) || !wageTag.contains(profession, Tag.TAG_LONG)) {
                throw new IllegalArgumentException(where + " has an invalid wage of '" + profession + "'");
            }
            wages.put(profession, wageTag.getLong(profession));
        }
        Map<UUID, Long> debts = new LinkedHashMap<>();
        for (CompoundTag entry : compounds(tag, DEBTS, where)) {
            if (!entry.hasUUID(PLAYER)) {
                throw new IllegalArgumentException(where + " has a debt without a player");
            }
            debts.put(entry.getUUID(PLAYER), entry.getLong(AMOUNT));
        }
        CompoundTag payrollTag = compound(tag, PAYROLL, where);
        Payroll.Summary payroll;
        try {
            payroll = new Payroll.Summary(payrollTag.contains(DAY) ? payrollTag.getLong(DAY) : -1, payrollTag.getLong(DUE),
                    payrollTag.getLong(PAID), payrollTag.getInt(CITIZENS), payrollTag.getInt(UNDERPAID));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(where + ": " + e.getMessage());
        }
        Treasury treasury = new Treasury(kingdomId, new CurrencyUnits(silver, gold), Math.max(0, Math.min(Money.MAX, tag.getLong(POLL_TAX))));
        treasury.restore(currencyName, tag.getLong(BALANCE), vault, tag.getLong(POLL_TAX), tag.getInt(SALES_TAX), wages, debts,
                tag.contains(LAST_PAYDAY, Tag.TAG_LONG) ? tag.getLong(LAST_PAYDAY) : Treasury.NO_PAYDAY, payroll);
        for (CompoundTag dayTag : compounds(tag, JOURNAL, where)) {
            Map<LedgerCategory, TreasuryJournal.Totals> entries = new EnumMap<>(LedgerCategory.class);
            CompoundTag entriesTag = compound(dayTag, ENTRIES, where);
            for (String key : entriesTag.getAllKeys()) {
                LedgerCategory category = LedgerCategory.byId(key)
                        .orElseThrow(() -> new IllegalArgumentException(where + " has an unknown journal category '" + key + "'"));
                CompoundTag totals = compound(entriesTag, key, where);
                try {
                    entries.put(category, new TreasuryJournal.Totals(totals.getLong(IN), totals.getLong(OUT)));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(where + ": " + e.getMessage());
                }
            }
            treasury.journal().restore(dayTag.getLong(DAY), entries);
        }
        Map<UUID, Long> reserves = new LinkedHashMap<>();
        for (CompoundTag entry : compounds(tag, "reserves", where)) {
            if (!entry.hasUUID(CURRENCY) || !Money.isAmount(entry.getLong(AMOUNT))) {
                throw new IllegalArgumentException(where + " has a reserve that cannot be read");
            }
            reserves.put(entry.getUUID(CURRENCY), entry.getLong(AMOUNT));
        }
        treasury.restoreReserves(reserves);
        return treasury;
    }

    // ---------------------------------------------------------------- accounts and old currencies

    public static CompoundTag writeWorld(Economy economy) {
        CompoundTag tag = new CompoundTag();
        ListTag accounts = new ListTag();
        for (UUID player : economy.accounts().players()) {
            economy.accounts().of(player).forEach((currency, balance) -> {
                CompoundTag entry = new CompoundTag();
                entry.putUUID(PLAYER, player);
                entry.putUUID(CURRENCY, currency);
                entry.putLong(BALANCE, balance);
                accounts.add(entry);
            });
        }
        tag.put(ACCOUNTS, accounts);
        ListTag retired = new ListTag();
        for (RetiredCurrency currency : economy.retired().values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(ID, currency.id());
            entry.putString(NAME, currency.name());
            entry.putString(KINGDOM, currency.kingdomName());
            entry.putInt(COLOR, currency.color());
            entry.putLong(SILVER, currency.units().silver());
            entry.putLong(GOLD, currency.units().gold());
            entry.putLong(TIME, currency.retiredTime());
            retired.add(entry);
        }
        tag.put(RETIRED, retired);
        ListTag rates = new ListTag();
        economy.rates().forEach((currency, rate) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(CURRENCY, currency);
            entry.putLong("rate", rate);
            rates.add(entry);
        });
        tag.put("rates", rates);
        tag.putLong("rateDay", economy.rateDay());
        ListTag escrows = new ListTag();
        for (Economy.Escrow escrow : economy.escrows().values()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong(ID, escrow.id());
            entry.putUUID("payer", escrow.payer());
            entry.putUUID("payee", escrow.payee());
            entry.putUUID(CURRENCY, escrow.currency());
            entry.putLong(AMOUNT, escrow.amount());
            escrows.add(entry);
        }
        tag.put("escrows", escrows);
        tag.putLong("nextEscrow", economy.nextEscrowId());
        ListTag coins = new ListTag();
        economy.coinsOut().forEach((currency, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(CURRENCY, currency);
            entry.putLong(AMOUNT, amount);
            coins.add(entry);
        });
        tag.put("coinsOut", coins);
        return tag;
    }

    /**
     * Reads the accounts and old currencies into the economy.
     *
     * @return what could not be read; empty if everything was
     */
    public static List<String> readWorld(Tag raw, Economy economy) {
        List<String> problems = new ArrayList<>();
        if (!(raw instanceof CompoundTag tag)) {
            problems.add("'" + ECONOMY + "' is not a compound");
            return problems;
        }
        try {
            for (CompoundTag entry : compounds(tag, ACCOUNTS, "accounts")) {
                if (!entry.hasUUID(PLAYER) || !entry.hasUUID(CURRENCY) || !Money.isAmount(entry.getLong(BALANCE))) {
                    problems.add("an account cannot be read");
                    continue;
                }
                economy.restoreAccount(entry.getUUID(PLAYER), entry.getUUID(CURRENCY), entry.getLong(BALANCE));
            }
        } catch (IllegalArgumentException e) {
            problems.add(e.getMessage());
        }
        try {
            for (CompoundTag entry : compounds(tag, RETIRED, "old currencies")) {
                long silver = entry.getLong(SILVER);
                long gold = entry.getLong(GOLD);
                if (!entry.hasUUID(ID) || !CurrencyUnits.valid(silver, gold)) {
                    problems.add("an old currency cannot be read");
                    continue;
                }
                economy.restoreRetired(new RetiredCurrency(entry.getUUID(ID), entry.getString(NAME), entry.getString(KINGDOM),
                        entry.getInt(COLOR), new CurrencyUnits(silver, gold), entry.getLong(TIME)));
            }
        } catch (IllegalArgumentException e) {
            problems.add(e.getMessage());
        }
        try {
            for (CompoundTag entry : compounds(tag, "rates", "exchange rates")) {
                if (!entry.hasUUID(CURRENCY) || entry.getLong("rate") < 1) {
                    problems.add("an exchange rate cannot be read");
                    continue;
                }
                economy.restoreRate(entry.getUUID(CURRENCY), entry.getLong("rate"));
            }
            economy.restoreRateDay(tag.contains("rateDay") ? tag.getLong("rateDay") : -1);
            for (CompoundTag entry : compounds(tag, "escrows", "escrows")) {
                if (!entry.hasUUID("payer") || !entry.hasUUID("payee") || !entry.hasUUID(CURRENCY)) {
                    problems.add("a payment in escrow cannot be read");
                    continue;
                }
                economy.restoreEscrow(new Economy.Escrow(entry.getLong(ID), entry.getUUID("payer"), entry.getUUID("payee"),
                        entry.getUUID(CURRENCY), entry.getLong(AMOUNT)));
            }
            for (CompoundTag entry : compounds(tag, "coinsOut", "coins in hands")) {
                if (!entry.hasUUID(CURRENCY)) {
                    problems.add("coins in hands cannot be read");
                    continue;
                }
                economy.restoreCoinsOut(entry.getUUID(CURRENCY), entry.getLong(AMOUNT));
            }
        } catch (IllegalArgumentException e) {
            problems.add(e.getMessage());
        }
        return problems;
    }

    private static CompoundTag compound(CompoundTag parent, String key, String where) {
        if (!parent.contains(key)) {
            return new CompoundTag();
        }
        if (!parent.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException(key + " of " + where + " is not a compound");
        }
        return parent.getCompound(key);
    }

    private static List<CompoundTag> compounds(CompoundTag parent, String key, String where) {
        if (!parent.contains(key)) {
            return List.of();
        }
        if (!parent.contains(key, Tag.TAG_LIST)) {
            throw new IllegalArgumentException(key + " of " + where + " is not a list");
        }
        ListTag list = (ListTag) parent.get(key);
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException(key + " of " + where + " does not hold compounds");
        }
        List<CompoundTag> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            result.add(list.getCompound(i));
        }
        return result;
    }
}
