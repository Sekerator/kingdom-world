package io.github.sekerator.kingdomworld.common.network.data;

import io.github.sekerator.kingdomworld.core.citizen.Profession;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.economy.Coins;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.economy.TreasuryJournal;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What the "Treasury" tab of the throne screen shows to a member of the kingdom.
 *
 * @param canManage      whether the viewer may set taxes and wages
 * @param isKing         whether the viewer may rename the currency
 * @param currencyName   the name the king gave the currency, empty for the name derived from the kingdom's
 * @param journal        income and expenses by period and category
 * @param untilPayday    ticks until the next payday
 * @param lastPayroll    the last payday: day, due, paid, citizens, underpaid; day -1 before the first
 * @param wages          the wage of each profession
 * @param debtors        the subjects who owe the most, at most {@link #MAX_DEBTORS}
 * @param debtorCount    every subject who owes something
 * @param debtTotal      what all of them owe together
 * @param mint           the mint of the kingdom
 */
public record TreasuryView(UUID kingdomId, boolean canManage, boolean isKing, String kingdomName, String currencyName, long silver, long gold,
                           long balance, Coins vault, List<JournalRow> journal, long pollTax, long maxPollTax, int taxpayers,
                           long untilPayday, Payroll lastPayroll, List<Wage> wages, long maxWage, List<Debtor> debtors, int debtorCount,
                           long debtTotal, Mint mint) {
    public static final int MAX_DEBTORS = 64;
    public static final int MAX_WAGES = 64;
    public static final int MAX_JOURNAL = 64;
    public static final int MAX_NAME = 64;

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
     */
    public record Mint(int posts, int workers, WorkStatus status, long today, long week, boolean toVault) {
    }

    public TreasuryView {
        Objects.requireNonNull(kingdomId, "kingdomId");
        Objects.requireNonNull(kingdomName, "kingdomName");
        Objects.requireNonNull(currencyName, "currencyName");
        Objects.requireNonNull(vault, "vault");
        journal = List.copyOf(journal);
        wages = List.copyOf(wages);
        debtors = List.copyOf(debtors);
        if (journal.size() > MAX_JOURNAL || wages.size() > MAX_WAGES || debtors.size() > MAX_DEBTORS) {
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
    }

    public static TreasuryView decode(FriendlyByteBuf buf) {
        UUID kingdomId = buf.readUUID();
        boolean canManage = buf.readBoolean();
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
                buf.readBoolean());
        return new TreasuryView(kingdomId, canManage, isKing, kingdomName, currencyName, silver, gold, balance, vault, journal, pollTax,
                maxPollTax, taxpayers, untilPayday, payroll, wages, maxWage, debtors, allDebtors, debtTotal, mint);
    }

    static long amount(FriendlyByteBuf buf) {
        long value = buf.readVarLong();
        if (!Money.isAmount(value)) {
            throw new DecoderException("Amount out of range: " + value);
        }
        return value;
    }

    private static int size(FriendlyByteBuf buf, int max) {
        int size = buf.readVarInt();
        if (size < 0 || size > max) {
            throw new DecoderException("List size out of range: " + size);
        }
        return size;
    }
}
