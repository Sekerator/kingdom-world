package io.github.sekerator.kingdomworld.core.economy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The treasury of a kingdom: money on its books (the balance) and coins in its vault, both in the kingdom's own
 * currency, with the journal of income and expenses, the taxes and wages the king set, and the debts of the
 * subjects. The treasury pays from its balance first and then from the vault; change for a coin goes to the
 * balance, so that no money appears or vanishes.
 *
 * <p>Changed only through {@link Economy} and {@link io.github.sekerator.kingdomworld.core.state.KingdomWorldState};
 * every change notifies the listener so that the data is saved.
 */
public final class Treasury {
    /** Longest name of a currency, the same as for kingdoms. */
    public static final int MAX_NAME_LENGTH = io.github.sekerator.kingdomworld.core.kingdom.KingdomNames.MAX_LENGTH;
    /** Day of the last payday before the first one was held. */
    public static final long NO_PAYDAY = -1L;

    private final UUID kingdomId;
    private final CurrencyUnits units;
    private String currencyName = "";
    private long balance;
    private Coins vault = Coins.NONE;
    private final TreasuryJournal journal = new TreasuryJournal();
    private long pollTax;
    private int salesTaxPermille;
    private final Map<String, Long> wages = new LinkedHashMap<>();
    private final Map<UUID, Long> debts = new LinkedHashMap<>();
    private final Map<UUID, Long> reserves = new LinkedHashMap<>();
    private long lastPayday = NO_PAYDAY;
    private Payroll.Summary lastPayroll = Payroll.Summary.NONE;
    private Runnable changeListener = () -> {
    };

    public Treasury(UUID kingdomId, CurrencyUnits units, long pollTax) {
        this.kingdomId = Objects.requireNonNull(kingdomId, "kingdomId");
        this.units = Objects.requireNonNull(units, "units");
        if (!Money.isAmount(pollTax)) {
            throw new IllegalArgumentException("Poll tax out of range: " + pollTax);
        }
        this.pollTax = pollTax;
    }

    void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
    }

    private void changed() {
        changeListener.run();
    }

    public UUID kingdomId() {
        return kingdomId;
    }

    /**
     * The currency of the kingdom has the kingdom's id.
     */
    public UUID currencyId() {
        return kingdomId;
    }

    public CurrencyUnits units() {
        return units;
    }

    /**
     * The name the king gave the currency, empty while it carries the name derived from the kingdom's.
     */
    public String currencyName() {
        return currencyName;
    }

    void setCurrencyName(String name) {
        String value = name == null ? "" : name;
        if (!value.equals(currencyName)) {
            currencyName = value;
            changed();
        }
    }

    public long balance() {
        return balance;
    }

    public Coins vault() {
        return vault;
    }

    /**
     * Balance and vault together.
     */
    public long funds() {
        return Money.addCapped(balance, vault.value(units));
    }

    public TreasuryJournal journal() {
        return journal;
    }

    public long pollTax() {
        return pollTax;
    }

    void setPollTax(long value) {
        if (value != pollTax) {
            pollTax = value;
            changed();
        }
    }

    /**
     * Tax on the sales of players' lots in thousandths; the king sets it within the config.
     */
    public int salesTaxPermille() {
        return salesTaxPermille;
    }

    /**
     * Wages the king set, by profession; the other professions get the usual wage.
     */
    public Map<String, Long> wages() {
        return Collections.unmodifiableMap(wages);
    }

    /**
     * The daily wage of a profession in this kingdom.
     */
    public long wage(String profession, EconomyRules rules) {
        Long set = wages.get(profession);
        return set != null ? set : rules.defaultWage(profession);
    }

    void setWage(String profession, long value) {
        Long previous = wages.put(profession, value);
        if (previous == null || previous != value) {
            changed();
        }
    }

    public Map<UUID, Long> debts() {
        return Collections.unmodifiableMap(debts);
    }

    public long debt(UUID player) {
        return debts.getOrDefault(player, 0L);
    }

    void setDebt(UUID player, long value) {
        Long previous = value <= 0 ? debts.remove(player) : debts.put(player, Math.min(Money.MAX, value));
        if (previous == null ? value > 0 : previous != value) {
            changed();
        }
    }

    public long lastPayday() {
        return lastPayday;
    }

    void setLastPayday(long day) {
        if (day != lastPayday) {
            lastPayday = day;
            changed();
        }
    }

    public Payroll.Summary lastPayroll() {
        return lastPayroll;
    }

    void setLastPayroll(Payroll.Summary summary) {
        lastPayroll = Objects.requireNonNull(summary, "summary");
        changed();
    }

    // ---------------------------------------------------------------- money in and out

    /**
     * Whether the balance can take an amount more.
     */
    boolean canReceive(long amount) {
        return Money.isAmount(amount) && Money.add(balance, amount).isPresent() && Money.add(funds(), amount).isPresent();
    }

    /**
     * Puts money on the balance and writes it into the journal.
     *
     * @return {@code false} if the balance would grow beyond {@link Money#MAX}; nothing changes then
     */
    boolean receive(long amount, LedgerCategory category, long day) {
        if (!canReceive(amount)) {
            return false;
        }
        if (amount == 0) {
            return true;
        }
        balance += amount;
        journal.record(day, category, amount, 0);
        changed();
        return true;
    }

    /**
     * Pays money from the balance and, when that is not enough, from the vault: coins from the smallest up are taken
     * out, and the change for the last coin goes to the balance.
     *
     * @return {@code false} if balance and vault together hold less; nothing changes then
     */
    boolean spend(long amount, LedgerCategory category, long day) {
        if (!Money.isAmount(amount) || funds() < amount) {
            return false;
        }
        if (amount == 0) {
            return true;
        }
        long fromBalance = Math.min(balance, amount);
        long need = amount - fromBalance;
        long newBalance = balance - fromBalance;
        Coins coins = vault;
        for (Denomination denomination : Denomination.values()) {
            if (need <= 0) {
                break;
            }
            long value = units.value(denomination);
            long count = Math.min(coins.count(denomination), (need + value - 1) / value);
            coins = coins.minus(denomination, count);
            long taken = count * value;
            if (taken >= need) {
                newBalance += taken - need;
                need = 0;
            } else {
                need -= taken;
            }
        }
        if (need > 0) {
            // funds() said there was enough; the coins must cover it.
            throw new IllegalStateException("The vault of " + kingdomId + " did not cover " + amount);
        }
        balance = newBalance;
        vault = coins;
        journal.record(day, category, 0, amount);
        changed();
        return true;
    }

    /**
     * Puts new coins into the vault and writes their worth into the journal.
     *
     * @return {@code false} if the vault would hold too much; nothing changes then
     */
    boolean store(Denomination denomination, long count, LedgerCategory category, long day) {
        if (count < 0) {
            return false;
        }
        long worth = Money.multiplyCapped(count, units.value(denomination));
        if (worth >= Money.MAX || Money.add(funds(), worth).isEmpty()) {
            return false;
        }
        Coins next;
        try {
            next = vault.plus(denomination, count);
        } catch (ArithmeticException e) {
            return false;
        }
        if (count == 0) {
            return true;
        }
        vault = next;
        journal.record(day, category, worth, 0);
        changed();
        return true;
    }

    // ---------------------------------------------------------------- reserves of other currencies

    /**
     * Money of other currencies the treasury holds, by currency: what players exchanged at its bank or paid on its
     * market, and what other kingdoms paid it in their currency.
     */
    public Map<UUID, Long> reserves() {
        return Collections.unmodifiableMap(reserves);
    }

    public long reserve(UUID currency) {
        return reserves.getOrDefault(currency, 0L);
    }

    boolean canReceiveReserve(UUID currency, long amount) {
        return !currency.equals(kingdomId) && Money.isAmount(amount) && Money.add(reserve(currency), amount).isPresent();
    }

    boolean receiveReserve(UUID currency, long amount) {
        if (!canReceiveReserve(currency, amount)) {
            return false;
        }
        if (amount > 0) {
            reserves.put(currency, reserve(currency) + amount);
            changed();
        }
        return true;
    }

    boolean spendReserve(UUID currency, long amount) {
        if (!Money.isAmount(amount) || reserve(currency) < amount) {
            return false;
        }
        if (amount > 0) {
            long left = reserve(currency) - amount;
            if (left == 0) {
                reserves.remove(currency);
            } else {
                reserves.put(currency, left);
            }
            changed();
        }
        return true;
    }

    /**
     * Forgets the reserves of a currency whose kingdom was dissolved; its money is old and waits like any other.
     */
    long dropReserve(UUID currency) {
        Long dropped = reserves.remove(currency);
        if (dropped != null) {
            changed();
        }
        return dropped == null ? 0 : dropped;
    }

    void setSalesTax(int permille) {
        if (permille != salesTaxPermille) {
            salesTaxPermille = permille;
            changed();
        }
    }

    /**
     * Restores the reserves from saved data. Does not notify.
     */
    public void restoreReserves(Map<UUID, Long> values) {
        reserves.clear();
        values.forEach((currency, amount) -> {
            if (!Money.isAmount(amount) || currency.equals(kingdomId)) {
                throw new IllegalArgumentException("Reserve of " + currency + " out of range");
            }
            if (amount > 0) {
                reserves.put(currency, amount);
            }
        });
    }

    /**
     * Takes coins out of the vault, for example into the hands of the king, and writes their worth into the journal.
     *
     * @return {@code false} if the vault holds fewer of them; nothing changes then
     */
    boolean takeCoins(Denomination denomination, long count, LedgerCategory category, long day) {
        if (count < 1 || count > vault.count(denomination)) {
            return false;
        }
        long worth = Money.multiplyCapped(count, units.value(denomination));
        vault = vault.minus(denomination, count);
        journal.record(day, category, 0, worth);
        changed();
        return true;
    }

    // ---------------------------------------------------------------- saved data

    /**
     * Restores everything but the journal from saved data. Does not notify.
     */
    public void restore(String name, long balance, Coins vault, long pollTax, int salesTaxPermille, Map<String, Long> wages,
                        Map<UUID, Long> debts, long lastPayday, Payroll.Summary lastPayroll) {
        if (!Money.isAmount(balance) || !Money.isAmount(pollTax) || salesTaxPermille < 0 || salesTaxPermille > 1000) {
            throw new IllegalArgumentException("Treasury of " + kingdomId + " out of range");
        }
        if (Money.add(balance, vault.value(units)).isEmpty()) {
            throw new IllegalArgumentException("Treasury of " + kingdomId + " holds too much");
        }
        this.currencyName = name == null ? "" : name;
        this.balance = balance;
        this.vault = Objects.requireNonNull(vault, "vault");
        this.pollTax = pollTax;
        this.salesTaxPermille = salesTaxPermille;
        this.wages.clear();
        wages.forEach((profession, wage) -> {
            if (!Money.isAmount(wage)) {
                throw new IllegalArgumentException("Wage of " + profession + " out of range");
            }
            this.wages.put(profession, wage);
        });
        this.debts.clear();
        debts.forEach((player, debt) -> {
            if (!Money.isAmount(debt)) {
                throw new IllegalArgumentException("Debt of " + player + " out of range");
            }
            if (debt > 0) {
                this.debts.put(player, debt);
            }
        });
        this.lastPayday = lastPayday;
        this.lastPayroll = Objects.requireNonNull(lastPayroll, "lastPayroll");
    }
}
