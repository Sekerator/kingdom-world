package io.github.sekerator.kingdomworld.core.economy;

import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.github.sekerator.kingdomworld.core.kingdom.NameProblem;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The money of one world: the treasury of every kingdom, the accounts of the players and the currencies of dissolved
 * kingdoms.
 *
 * <p>Money only comes into being in two ways: the mint strikes coins from metal ({@link #mint}) and an operator adds
 * some ({@link #adminTreasury}, {@link #adminAccount}). Coins become money on an account at a bank and money on an
 * account becomes coins again ({@link #deposit}, {@link #withdraw}); everything else moves money from one place to
 * another, or takes it out of the world (wages). Every operation checks everything first and then changes all it
 * changes at once, or refuses with a reason and changes nothing. Amounts are whole numbers of the smallest unit
 * between 0 and {@link Money#MAX}.
 *
 * <p>Not thread safe; the server uses it from the server thread only.
 */
public final class Economy {
    private final Map<UUID, Treasury> treasuries = new LinkedHashMap<>();
    private final Accounts accounts = new Accounts();
    private final Map<UUID, RetiredCurrency> retired = new LinkedHashMap<>();
    private CurrencyUnits defaultUnits = CurrencyUnits.DEFAULT;
    private long defaultPollTax = EconomyRules.DEFAULT.defaultPollTax();
    private Runnable changeListener = () -> {
    };

    /**
     * A citizen on the payroll.
     */
    public record WageClaim(UUID citizenId, String profession) {
    }

    /**
     * What a payday did.
     *
     * @param taxes    what each subject paid and still owes
     * @param pay      what each citizen got, by citizen id
     * @param percents how much of its usual wage each citizen got, see {@link Payroll#percent}
     */
    public record Payday(List<TaxPaid> taxes, Map<UUID, Long> pay, Map<UUID, Integer> percents, Payroll.Summary summary) {
        public static final Payday NONE = new Payday(List.of(), Map.of(), Map.of(), Payroll.Summary.NONE);
    }

    /**
     * The taxes of one subject on a payday.
     */
    public record TaxPaid(UUID player, long tax, long paid, long debt) {
    }

    public void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
        for (Treasury treasury : treasuries.values()) {
            treasury.setChangeListener(listener);
        }
    }

    private void changed() {
        changeListener.run();
    }

    /**
     * The coin values and the tax new treasuries start with, from the server config.
     */
    public void setDefaults(CurrencyUnits units, long pollTax) {
        this.defaultUnits = Objects.requireNonNull(units, "units");
        this.defaultPollTax = Money.isAmount(pollTax) ? pollTax : 0;
    }

    // ---------------------------------------------------------------- reading

    public Optional<Treasury> treasury(UUID kingdomId) {
        return Optional.ofNullable(treasuries.get(kingdomId));
    }

    public Collection<Treasury> treasuries() {
        return Collections.unmodifiableCollection(treasuries.values());
    }

    public Accounts accounts() {
        return accounts;
    }

    public Map<UUID, RetiredCurrency> retired() {
        return Collections.unmodifiableMap(retired);
    }

    /**
     * Whether coins and accounts of a currency are in use: its kingdom exists.
     */
    public boolean isActive(UUID currency) {
        return treasuries.containsKey(currency);
    }

    /**
     * What the coins of a currency are worth, for current and old currencies.
     */
    public Optional<CurrencyUnits> units(UUID currency) {
        Treasury treasury = treasuries.get(currency);
        if (treasury != null) {
            return Optional.of(treasury.units());
        }
        RetiredCurrency old = retired.get(currency);
        return old == null ? Optional.empty() : Optional.of(old.units());
    }

    /**
     * All money of a currency on the books: the treasury and every account. Coins in the hands of players are not
     * counted, they are not on any books.
     */
    public long supply(UUID currency) {
        Treasury treasury = treasuries.get(currency);
        return Money.addCapped(treasury == null ? 0 : treasury.funds(), accounts.total(currency));
    }

    // ---------------------------------------------------------------- treasuries

    /**
     * Opens the treasury of a newly founded kingdom, with the default coin values and tax; an existing one is kept.
     */
    public Treasury open(UUID kingdomId, long day) {
        Treasury existing = treasuries.get(kingdomId);
        if (existing != null) {
            return existing;
        }
        Treasury treasury = new Treasury(kingdomId, defaultUnits, defaultPollTax);
        treasury.setChangeListener(changeListener);
        treasury.restore("", 0, Coins.NONE, defaultPollTax, 0, Map.of(), Map.of(), day, Payroll.Summary.NONE);
        treasuries.put(kingdomId, treasury);
        changed();
        return treasury;
    }

    /**
     * The kingdom was dissolved: its treasury is gone, its currency becomes old. Accounts in it stay.
     */
    public void retire(UUID kingdomId, String kingdomName, int color, long gameTime) {
        Treasury treasury = treasuries.remove(kingdomId);
        if (treasury == null) {
            return;
        }
        retired.put(kingdomId, new RetiredCurrency(kingdomId, treasury.currencyName(), kingdomName, color, treasury.units(), gameTime));
        changed();
    }

    /**
     * Renames the currency of a kingdom; an empty name gives it back the name derived from the kingdom's.
     */
    public Outcome renameCurrency(UUID kingdomId, String rawName) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_CURRENCY);
        }
        String name = rawName == null ? "" : KingdomNames.normalize(rawName);
        if (!name.isEmpty()) {
            Optional<NameProblem> problem = KingdomNames.validate(name);
            if (problem.isPresent()) {
                return Outcome.fail(KingdomError.INVALID_CURRENCY_NAME);
            }
            for (Treasury other : treasuries.values()) {
                if (other != treasury && KingdomNames.key(other.currencyName()).equals(KingdomNames.key(name))) {
                    return Outcome.fail(KingdomError.CURRENCY_NAME_TAKEN);
                }
            }
        }
        if (name.equals(treasury.currencyName())) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        treasury.setCurrencyName(name);
        return Outcome.DONE;
    }

    public Outcome setPollTax(UUID kingdomId, long amount, long max) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_CURRENCY);
        }
        if (amount < 0 || amount > Math.min(max, Money.MAX)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (amount == treasury.pollTax()) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        treasury.setPollTax(amount);
        return Outcome.DONE;
    }

    public Outcome setWage(UUID kingdomId, String profession, long amount, long max, EconomyRules rules) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_CURRENCY);
        }
        if (amount < 0 || amount > Math.min(max, Money.MAX)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (amount == treasury.wage(profession, rules)) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        treasury.setWage(profession, amount);
        return Outcome.DONE;
    }

    /**
     * Strikes coins into the treasury: into its vault, or straight onto its balance.
     */
    public Outcome mint(UUID kingdomId, Denomination denomination, long count, boolean toVault, long day) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_CURRENCY);
        }
        if (count < 1 || count > Money.MAX) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        boolean done = toVault ? treasury.store(denomination, count, LedgerCategory.MINTING, day)
                : count <= Money.MAX / treasury.units().value(denomination)
                && treasury.receive(count * treasury.units().value(denomination), LedgerCategory.MINTING, day);
        return done ? Outcome.DONE : Outcome.fail(KingdomError.TOO_MUCH_MONEY);
    }

    /**
     * Whether a treasury could take new coins worth an amount.
     */
    public boolean canMint(UUID kingdomId, long worth) {
        Treasury treasury = treasuries.get(kingdomId);
        return treasury != null && treasury.canReceive(worth);
    }

    // ---------------------------------------------------------------- players

    /**
     * A player pays another in a current currency.
     */
    public Outcome transfer(UUID from, UUID to, UUID currency, long amount) {
        if (from.equals(to)) {
            return Outcome.fail(KingdomError.TARGET_IS_SELF);
        }
        if (!isActive(currency)) {
            return Outcome.fail(retired.containsKey(currency) ? KingdomError.OLD_CURRENCY : KingdomError.UNKNOWN_CURRENCY);
        }
        if (!Money.isPayment(amount)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (accounts.balance(from, currency) < amount) {
            return Outcome.fail(KingdomError.NOT_ENOUGH_MONEY);
        }
        if (!accounts.canCredit(to, currency, amount)) {
            return Outcome.fail(KingdomError.TOO_MUCH_MONEY);
        }
        accounts.debit(from, currency, amount);
        accounts.credit(to, currency, amount);
        changed();
        return Outcome.DONE;
    }

    /**
     * Whether coins worth an amount may be put on a player's account in a current currency.
     */
    public Outcome checkDeposit(UUID player, UUID currency, long amount) {
        if (!isActive(currency)) {
            return Outcome.fail(retired.containsKey(currency) ? KingdomError.OLD_CURRENCY : KingdomError.UNKNOWN_CURRENCY);
        }
        if (!Money.isPayment(amount)) {
            return Outcome.fail(KingdomError.NO_COINS);
        }
        return accounts.canCredit(player, currency, amount) ? Outcome.DONE : Outcome.fail(KingdomError.TOO_MUCH_MONEY);
    }

    /**
     * Coins handed in at a bank become money on the account. The caller takes the coins in the same moment.
     */
    public Outcome deposit(UUID player, UUID currency, long amount) {
        Outcome check = checkDeposit(player, currency, amount);
        if (!check.ok()) {
            return check;
        }
        accounts.credit(player, currency, amount);
        changed();
        return Outcome.DONE;
    }

    /**
     * Money on the account leaves it to become coins at a bank. The caller hands out coins worth exactly the amount in
     * the same moment.
     */
    public Outcome withdraw(UUID player, UUID currency, long amount) {
        if (!isActive(currency)) {
            return Outcome.fail(retired.containsKey(currency) ? KingdomError.OLD_CURRENCY : KingdomError.UNKNOWN_CURRENCY);
        }
        if (!Money.isPayment(amount)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (accounts.balance(player, currency) < amount) {
            return Outcome.fail(KingdomError.NOT_ENOUGH_MONEY);
        }
        accounts.debit(player, currency, amount);
        changed();
        return Outcome.DONE;
    }

    /**
     * The treasury pays a player onto the player's account.
     */
    public Outcome treasuryToPlayer(UUID kingdomId, UUID player, long amount, long day) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_CURRENCY);
        }
        if (!Money.isPayment(amount)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (treasury.funds() < amount) {
            return Outcome.fail(KingdomError.NOT_ENOUGH_MONEY);
        }
        if (!accounts.canCredit(player, kingdomId, amount)) {
            return Outcome.fail(KingdomError.TOO_MUCH_MONEY);
        }
        treasury.spend(amount, LedgerCategory.TRANSFERS, day);
        accounts.credit(player, kingdomId, amount);
        return Outcome.DONE;
    }

    /**
     * A player pays into a treasury from the account in its currency. A debt of the player is paid back first.
     */
    public Outcome playerToTreasury(UUID player, UUID kingdomId, long amount, long day) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_CURRENCY);
        }
        if (!Money.isPayment(amount)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (accounts.balance(player, kingdomId) < amount) {
            return Outcome.fail(KingdomError.NOT_ENOUGH_MONEY);
        }
        if (!treasury.canReceive(amount)) {
            return Outcome.fail(KingdomError.TOO_MUCH_MONEY);
        }
        long debt = treasury.debt(player);
        long repaid = Math.min(debt, amount);
        accounts.debit(player, kingdomId, amount);
        treasury.receive(repaid, LedgerCategory.TAXES, day);
        treasury.receive(amount - repaid, LedgerCategory.TRANSFERS, day);
        treasury.setDebt(player, debt - repaid);
        changed();
        return Outcome.DONE;
    }

    // ---------------------------------------------------------------- operators

    /**
     * An operator adds money to an account or takes it away; a currency that is not in use is refused.
     */
    public Outcome adminAccount(UUID player, UUID currency, long delta) {
        if (!isActive(currency)) {
            return Outcome.fail(KingdomError.UNKNOWN_CURRENCY);
        }
        if (delta == 0 || Math.abs(delta) > Money.MAX) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (delta > 0) {
            if (!accounts.canCredit(player, currency, delta)) {
                return Outcome.fail(KingdomError.TOO_MUCH_MONEY);
            }
            accounts.credit(player, currency, delta);
        } else {
            if (accounts.balance(player, currency) < -delta) {
                return Outcome.fail(KingdomError.NOT_ENOUGH_MONEY);
            }
            accounts.debit(player, currency, -delta);
        }
        changed();
        return Outcome.DONE;
    }

    /**
     * An operator adds money to a treasury or takes it away; written into its journal as the operator's.
     */
    public Outcome adminTreasury(UUID kingdomId, long delta, long day) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_CURRENCY);
        }
        if (delta == 0 || Math.abs(delta) > Money.MAX) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        boolean done = delta > 0 ? treasury.receive(delta, LedgerCategory.ADMIN, day) : treasury.spend(-delta, LedgerCategory.ADMIN, day);
        return done ? Outcome.DONE : Outcome.fail(delta > 0 ? KingdomError.TOO_MUCH_MONEY : KingdomError.NOT_ENOUGH_MONEY);
    }

    // ---------------------------------------------------------------- payday

    /**
     * The daily payday of a kingdom: the subjects pay their tax (and old debts) from their accounts, then the citizens
     * get their wages, all of them or the same share of each when the treasury has too little. Held at most once a
     * day; the first call after a kingdom's data was upgraded only starts the count.
     *
     * @param taxpayers the subjects who pay the tax
     * @param claims    the citizens on the payroll
     */
    public Payday payday(UUID kingdomId, long day, List<UUID> taxpayers, List<WageClaim> claims, EconomyRules rules) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null || treasury.lastPayday() >= day) {
            return Payday.NONE;
        }
        if (treasury.lastPayday() == Treasury.NO_PAYDAY) {
            treasury.setLastPayday(day);
            return Payday.NONE;
        }
        List<TaxPaid> taxes = new ArrayList<>();
        for (UUID player : taxpayers) {
            long tax = treasury.pollTax();
            long debt = treasury.debt(player);
            if (tax == 0 && debt == 0) {
                continue;
            }
            Taxes.Collection collection = Taxes.collect(accounts.balance(player, kingdomId), debt, tax);
            long paid = collection.paid();
            if (paid > 0 && !treasury.canReceive(paid)) {
                // A treasury that is full takes nothing more; the subject owes what it could not take.
                paid = 0;
            }
            if (paid > 0) {
                accounts.debit(player, kingdomId, paid);
                treasury.receive(paid, LedgerCategory.TAXES, day);
            }
            long newDebt = Money.addCapped(debt, tax) - paid;
            treasury.setDebt(player, newDebt);
            taxes.add(new TaxPaid(player, tax, paid, newDebt));
        }

        long[] wages = new long[claims.size()];
        for (int i = 0; i < claims.size(); i++) {
            wages[i] = treasury.wage(claims.get(i).profession(), rules);
        }
        long[] paid = Payroll.distribute(wages, treasury.funds());
        long due = 0;
        long total = 0;
        int underpaid = 0;
        Map<UUID, Long> pay = new LinkedHashMap<>();
        Map<UUID, Integer> percents = new LinkedHashMap<>();
        for (int i = 0; i < claims.size(); i++) {
            WageClaim claim = claims.get(i);
            due = Money.addCapped(due, wages[i]);
            total = Money.addCapped(total, paid[i]);
            if (paid[i] < wages[i]) {
                underpaid++;
            }
            pay.put(claim.citizenId(), paid[i]);
            percents.put(claim.citizenId(), Payroll.percent(paid[i], rules.defaultWage(claim.profession())));
        }
        if (!treasury.spend(total, LedgerCategory.WAGES, day)) {
            throw new IllegalStateException("Wages of " + kingdomId + " exceeded its funds");
        }
        Payroll.Summary summary = new Payroll.Summary(day, due, total, claims.size(), underpaid);
        treasury.setLastPayroll(summary);
        treasury.setLastPayday(day);
        changed();
        return new Payday(List.copyOf(taxes), pay, percents, summary);
    }

    // ---------------------------------------------------------------- saved data

    /**
     * Restores a treasury read from saved data.
     *
     * @throws IllegalArgumentException if the kingdom has one already
     */
    public void restore(Treasury treasury) {
        if (treasuries.containsKey(treasury.kingdomId())) {
            throw new IllegalArgumentException("Two treasuries for " + treasury.kingdomId());
        }
        treasury.setChangeListener(changeListener);
        treasuries.put(treasury.kingdomId(), treasury);
    }

    /**
     * Forgets the treasury of a kingdom whose data could not be restored, without retiring its currency.
     */
    public void forget(UUID kingdomId) {
        treasuries.remove(kingdomId);
    }

    public void restoreAccount(UUID player, UUID currency, long amount) {
        if (!Money.isAmount(amount)) {
            throw new IllegalArgumentException("Account of " + player + " out of range");
        }
        accounts.set(player, currency, amount);
    }

    public void restoreRetired(RetiredCurrency currency) {
        retired.put(currency.id(), currency);
    }
}
