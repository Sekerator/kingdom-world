package io.github.sekerator.kingdomworld.common.network.data;

import io.github.sekerator.kingdomworld.core.citizen.Profession;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.economy.Coins;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.Denomination;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.MintMetals;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.economy.TreasuryJournal;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What the "Treasury" tab of the throne screen shows to a member of the kingdom.
 *
 * @param canManage      whether the viewer may set taxes and wages and pay players
 * @param canTakeCoins   whether the viewer may take coins of the vault into the hands: the king or the treasurer with the right
 * @param isKing         whether the viewer may rename the currency
 * @param currencyName   the name the king gave the currency, empty for the name derived from the kingdom's
 * @param journal        income and expenses by period and category
 * @param untilPayday    ticks until the next payday
 * @param lastPayroll    the last payday: day, due, paid, citizens, underpaid; day -1 before the first
 * @param wages          the wage of each profession
 * @param debtors        the subjects who owe the most, at most {@link #MAX_DEBTORS}
 * @param debtorCount    every subject who owes something
 * @param debtTotal      what all of them owe together
 * @param mint           the mint of the kingdom and the settings of its metals
 * @param rate           the exchange rate of the kingdom's currency
 * @param reserves       money of other currencies the treasury holds
 */
public record TreasuryView(UUID kingdomId, boolean canManage, boolean canTakeCoins, boolean isKing, String kingdomName, String currencyName, long silver, long gold,
                           long balance, Coins vault, List<JournalRow> journal, long pollTax, long maxPollTax, int taxpayers,
                           long untilPayday, Payroll lastPayroll, List<Wage> wages, long maxWage, List<Debtor> debtors, int debtorCount,
                           long debtTotal, Mint mint, long rate, List<Reserve> reserves) {
    public static final int MAX_DEBTORS = 64;
    public static final int MAX_WAGES = 64;
    public static final int MAX_JOURNAL = 64;
    public static final int MAX_NAME = 64;
    public static final int MAX_RESERVES = 256;

    /**
     * Money of another currency in the treasury.
     *
     * @param worth what it is worth in the treasury's own currency by the rates
     */
    public record Reserve(String name, String kingdomName, int color, long amount, long silver, long gold, long worth) {
        public Reserve {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(kingdomName, "kingdomName");
        }

        public CurrencyUnits units() {
            return CurrencyUnits.valid(silver, gold) ? new CurrencyUnits(silver, gold) : CurrencyUnits.DEFAULT;
        }
    }

    /**
     * Money in and out of one category over one period.
     */
    public record JournalRow(TreasuryJournal.Period period, LedgerCategory category, long income, long expense) {
    }

    /**
     * The last payday.
     */
    public record Payroll(long day, long due, long paid, int citizens, int underpaid) {
        public int percent() {
            return due <= 0 ? 100 : (int) Money.share(100, paid, due);
        }
    }

    /**
     * The daily wage of a profession.
     *
     * @param usual    the usual wage from the config, which citizens measure their pay against
     * @param citizens citizens of the profession
     */
    public record Wage(String profession, long wage, long usual, int citizens) {
        public Wage {
            Objects.requireNonNull(profession, "profession");
        }
    }

    public record Debtor(String name, long debt) {
        public Debtor {
            Objects.requireNonNull(name, "name");
        }
    }

    /**
     * The mint of the kingdom.
     *
     * @param posts   work posts of minters
     * @param workers minters at them
     * @param status  how the work goes at the best of them
     * @param today   worth of the coins struck today
     * @param week    worth of the coins struck in the last seven days
     * @param toVault whether struck coins go into the vault
     * @param metals  the metals of the config and what the mint does with each
     */
    public record Mint(int posts, int workers, WorkStatus status, long today, long week, boolean toVault, List<MintMetal> metals) {
        public Mint {
            metals = List.copyOf(metals);
            if (metals.size() > MAX_METALS) {
                throw new IllegalArgumentException("Too many metals of the mint");
            }
        }
    }

    /** The most metals of the mint a view carries. */
    public static final int MAX_METALS = 32;

    /**
     * A metal of the mint and what the mint does with it.
     *
     * @param metal  the rate of the config: an item, or a tag after {@code #}
     * @param item   an item of the metal, for its name and icon
     * @param coin   the kind of coin one item gives
     * @param count  how many of those coins
     * @param strike whether the mint strikes coins from it
     * @param keep   items of it that stay in the storehouses
     * @param stock  items of it the storehouses hold
     */
    public record MintMetal(String metal, ItemStack item, Denomination coin, int count, boolean strike, int keep, long stock) {
        public MintMetal {
            Objects.requireNonNull(metal, "metal");
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(coin, "coin");
        }

        public static void encodeAll(FriendlyByteBuf buf, List<MintMetal> metals) {
            buf.writeVarInt(metals.size());
            for (MintMetal metal : metals) {
                buf.writeUtf(metal.metal(), MintMetals.MAX_METAL_LENGTH);
                buf.writeItem(metal.item());
                buf.writeEnum(metal.coin());
                buf.writeVarInt(metal.count());
                buf.writeBoolean(metal.strike());
                buf.writeVarInt(metal.keep());
                buf.writeVarLong(metal.stock());
            }
        }

        public static List<MintMetal> decodeAll(FriendlyByteBuf buf) {
            int count = size(buf, MAX_METALS);
            List<MintMetal> metals = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String metal = buf.readUtf(MintMetals.MAX_METAL_LENGTH);
                ItemStack item = buf.readItem();
                Denomination coin = KingdomView.readEnum(buf, Denomination.class);
                int coins = buf.readVarInt();
                boolean strike = buf.readBoolean();
                int keep = buf.readVarInt();
                if (keep < 0 || keep > MintMetals.MAX_KEEP) {
                    throw new DecoderException("Metal to keep out of range: " + keep);
                }
                metals.add(new MintMetal(metal, item, coin, coins, strike, keep, amount(buf)));
            }
            return metals;
        }
    }

    public TreasuryView {
        Objects.requireNonNull(kingdomId, "kingdomId");
        Objects.requireNonNull(kingdomName, "kingdomName");
        Objects.requireNonNull(currencyName, "currencyName");
        Objects.requireNonNull(vault, "vault");
        journal = List.copyOf(journal);
        wages = List.copyOf(wages);
        debtors = List.copyOf(debtors);
        reserves = List.copyOf(reserves);
        if (journal.size() > MAX_JOURNAL || wages.size() > MAX_WAGES || debtors.size() > MAX_DEBTORS || reserves.size() > MAX_RESERVES) {
            throw new IllegalArgumentException("Treasury view too large");
        }
    }

    public CurrencyUnits units() {
        return CurrencyUnits.valid(silver, gold) ? new CurrencyUnits(silver, gold) : CurrencyUnits.DEFAULT;
    }

    public long funds() {
        return Money.addCapped(balance, vault.value(units()));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(kingdomId);
        buf.writeBoolean(canManage);
        buf.writeBoolean(canTakeCoins);
        buf.writeBoolean(isKing);
        buf.writeUtf(kingdomName, KingdomNames.MAX_LENGTH);
        buf.writeUtf(currencyName, KingdomNames.MAX_LENGTH);
        buf.writeVarLong(silver);
        buf.writeVarLong(gold);
        buf.writeVarLong(balance);
        buf.writeVarLong(vault.copper());
        buf.writeVarLong(vault.silver());
        buf.writeVarLong(vault.gold());
        buf.writeVarInt(journal.size());
        for (JournalRow row : journal) {
            buf.writeEnum(row.period());
            buf.writeEnum(row.category());
            buf.writeVarLong(row.income());
            buf.writeVarLong(row.expense());
        }
        buf.writeVarLong(pollTax);
        buf.writeVarLong(maxPollTax);
        buf.writeVarInt(taxpayers);
        buf.writeVarLong(untilPayday);
        buf.writeLong(lastPayroll.day());
        buf.writeVarLong(lastPayroll.due());
        buf.writeVarLong(lastPayroll.paid());
        buf.writeVarInt(lastPayroll.citizens());
        buf.writeVarInt(lastPayroll.underpaid());
        buf.writeVarInt(wages.size());
        for (Wage wage : wages) {
            buf.writeUtf(wage.profession(), Profession.MAX_ID_LENGTH);
            buf.writeVarLong(wage.wage());
            buf.writeVarLong(wage.usual());
            buf.writeVarInt(wage.citizens());
        }
        buf.writeVarLong(maxWage);
        buf.writeVarInt(debtors.size());
        for (Debtor debtor : debtors) {
            buf.writeUtf(debtor.name(), MAX_NAME);
            buf.writeVarLong(debtor.debt());
        }
        buf.writeVarInt(debtorCount);
        buf.writeVarLong(debtTotal);
        buf.writeVarInt(mint.posts());
        buf.writeVarInt(mint.workers());
        buf.writeEnum(mint.status());
        buf.writeVarLong(mint.today());
        buf.writeVarLong(mint.week());
        buf.writeBoolean(mint.toVault());
        MintMetal.encodeAll(buf, mint.metals());
        buf.writeVarLong(rate);
        buf.writeVarInt(reserves.size());
        for (Reserve reserve : reserves) {
            buf.writeUtf(reserve.name(), KingdomNames.MAX_LENGTH);
            buf.writeUtf(reserve.kingdomName(), KingdomNames.MAX_LENGTH);
            buf.writeInt(reserve.color());
            buf.writeVarLong(reserve.amount());
            buf.writeVarLong(reserve.silver());
            buf.writeVarLong(reserve.gold());
            buf.writeVarLong(reserve.worth());
        }
    }

    public static TreasuryView decode(FriendlyByteBuf buf) {
        UUID kingdomId = buf.readUUID();
        boolean canManage = buf.readBoolean();
        boolean canTakeCoins = buf.readBoolean();
        boolean isKing = buf.readBoolean();
        String kingdomName = buf.readUtf(KingdomNames.MAX_LENGTH);
        String currencyName = buf.readUtf(KingdomNames.MAX_LENGTH);
        long silver = buf.readVarLong();
        long gold = buf.readVarLong();
        long balance = amount(buf);
        Coins vault = new Coins(amount(buf), amount(buf), amount(buf));
        int rows = size(buf, MAX_JOURNAL);
        List<JournalRow> journal = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            journal.add(new JournalRow(KingdomView.readEnum(buf, TreasuryJournal.Period.class), KingdomView.readEnum(buf, LedgerCategory.class),
                    amount(buf), amount(buf)));
        }
        long pollTax = amount(buf);
        long maxPollTax = amount(buf);
        int taxpayers = buf.readVarInt();
        long untilPayday = buf.readVarLong();
        Payroll payroll = new Payroll(buf.readLong(), amount(buf), amount(buf), buf.readVarInt(), buf.readVarInt());
        int wageCount = size(buf, MAX_WAGES);
        List<Wage> wages = new ArrayList<>(wageCount);
        for (int i = 0; i < wageCount; i++) {
            wages.add(new Wage(buf.readUtf(Profession.MAX_ID_LENGTH), amount(buf), amount(buf), buf.readVarInt()));
        }
        long maxWage = amount(buf);
        int debtorCount = size(buf, MAX_DEBTORS);
        List<Debtor> debtors = new ArrayList<>(debtorCount);
        for (int i = 0; i < debtorCount; i++) {
            debtors.add(new Debtor(buf.readUtf(MAX_NAME), amount(buf)));
        }
        int allDebtors = buf.readVarInt();
        long debtTotal = amount(buf);
        Mint mint = new Mint(buf.readVarInt(), buf.readVarInt(), KingdomView.readEnum(buf, WorkStatus.class), amount(buf), amount(buf),
                buf.readBoolean(), MintMetal.decodeAll(buf));
        long rate = buf.readVarLong();
        int reserveCount = size(buf, MAX_RESERVES);
        List<Reserve> reserves = new ArrayList<>(reserveCount);
        for (int i = 0; i < reserveCount; i++) {
            reserves.add(new Reserve(buf.readUtf(KingdomNames.MAX_LENGTH), buf.readUtf(KingdomNames.MAX_LENGTH), buf.readInt(), amount(buf),
                    buf.readVarLong(), buf.readVarLong(), amount(buf)));
        }
        return new TreasuryView(kingdomId, canManage, canTakeCoins, isKing, kingdomName, currencyName, silver, gold, balance, vault, journal, pollTax,
                maxPollTax, taxpayers, untilPayday, payroll, wages, maxWage, debtors, allDebtors, debtTotal, mint, rate, reserves);
    }

    static long amount(FriendlyByteBuf buf) {
        long value = buf.readVarLong();
        if (!Money.isAmount(value)) {
            throw new DecoderException("Amount out of range: " + value);
        }
        return value;
    }

    static int size(FriendlyByteBuf buf, int max) {
        int size = buf.readVarInt();
        if (size < 0 || size > max) {
            throw new DecoderException("List size out of range: " + size);
        }
        return size;
    }
}
