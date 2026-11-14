package io.github.sekerator.kingdomworld.core.economy;

import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.github.sekerator.kingdomworld.core.kingdom.NameProblem;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.trade.ExchangeRates;

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
 * <p>Money only comes into being in three ways: the mint strikes coins from metal ({@link #mint}), merchants sell goods
 * abroad ({@link #exportIncome}) and an operator adds some ({@link #adminTreasury}, {@link #adminAccount}). Coins become
 * money on an account at a bank and money on an account becomes coins again ({@link #deposit}, {@link #withdraw});
 * everything else moves money from one place to another (payments, the market, exchanges, deals in escrow), or takes it
 * out of the world (wages, goods bought abroad). Every operation checks everything first and then changes all it
 * changes at once, or refuses with a reason and changes nothing. Amounts are whole numbers of the smallest unit
 * between 0 and {@link Money#MAX}.
 *
 * <p>Not thread safe; the server uses it from the server thread only.
 */
public final class Economy {
    private final Map<UUID, Treasury> treasuries = new LinkedHashMap<>();
    private final Accounts accounts = new Accounts();
    private final Map<UUID, RetiredCurrency> retired = new LinkedHashMap<>();
    /** Exchange rates by currency, see {@link ExchangeRates}. */
    private final Map<UUID, Long> rates = new LinkedHashMap<>();
    private long rateDay = -1;
    /** The game day, for the journal of payments that do not name one. */
    private long today;
    /** Payments of deals waiting for their caravans. */
    private final Map<Long, Escrow> escrows = new LinkedHashMap<>();
    private long nextEscrow = 1;
    /** Coins of each currency in the hands of players, as far as banks, treasuries and markets saw them go. */
    private final Map<UUID, Long> coinsOut = new LinkedHashMap<>();
    private CurrencyUnits defaultUnits = CurrencyUnits.DEFAULT;
    private long defaultPollTax = EconomyRules.DEFAULT.defaultPollTax();
    private int defaultSalesTaxPermille;
    /** Money of each currency held outside these books: in the escrow of guild contracts, for the sellers of provinces. */
    private final Map<String, java.util.function.ToLongFunction<UUID>> heldElsewhere = new LinkedHashMap<>();
    private Runnable changeListener = () -> {
    };

    /**
     * A payment of a deal held until its caravan arrives: taken from the payer's treasury (its own currency) or its
     * reserves (the other kingdom's currency), and given to the payee the same way, or back to the payer.
     */
    public record Escrow(long id, UUID payer, UUID payee, UUID currency, long amount) {
        public Escrow {
            Objects.requireNonNull(payer, "payer");
            Objects.requireNonNull(payee, "payee");
            Objects.requireNonNull(currency, "currency");
            if (!Money.isPayment(amount)) {
                throw new IllegalArgumentException("Escrow out of range: " + amount);
            }
        }
    }

    /**
     * A citizen on the payroll.
     *
     * @param permille the wage of the citizen in thousandths of the wage of its profession: 1000 for everybody but the
     *                 officers of the guard and the army
     */
    public record WageClaim(UUID citizenId, String profession, int permille) {
        public WageClaim {
            permille = Math.max(0, Math.min(1_000_000, permille));
        }

        public WageClaim(UUID citizenId, String profession) {
            this(citizenId, profession, 1_000);
        }

        /**
         * A wage of the profession as this citizen gets it.
         */
        public long scaled(long wage) {
            if (permille == 1_000 || wage <= 0) {
                return Math.max(0, wage);
            }
            return Money.multiplyCapped((long) Math.ceil(wage * (permille / 1_000.0)), 1);
        }
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
     * The game day for the journal of payments that do not name one; the server sets it as days pass.
     */
    public void setToday(long day) {
        today = Math.max(today, day);
    }

    /**
     * Income of a player in the currency of a kingdom pays back what the player owes that treasury first, up to the income:
     * sales to the market and of lots, rewards, payments from the treasury and from other players.
     *
     * @return what was paid back
     */
    public long repayFromIncome(UUID player, UUID currency, long income, long day) {
        Treasury treasury = treasuries.get(currency);
        if (treasury == null || income <= 0) {
            return 0;
        }
        long debt = treasury.debt(player);
        long repaid = Math.min(Math.min(debt, income), accounts.balance(player, currency));
        if (repaid <= 0 || !treasury.canReceive(repaid)) {
            return 0;
        }
        accounts.debit(player, currency, repaid);
        treasury.receive(repaid, LedgerCategory.TAXES, Math.max(day, today));
        treasury.setDebt(player, debt - repaid);
        changed();
        return repaid;
    }

    /**
     * The tax on the sales of lots new treasuries start with, from the server config.
     */
    public void setDefaultSalesTax(int permille) {
        this.defaultSalesTaxPermille = Math.max(0, Math.min(1000, permille));
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
     * All money of a currency on the books: its treasury, every account, the reserves other treasuries hold of it and
     * payments in escrow. Coins in the hands of players are not counted, they are not on any books.
     */
    public long supply(UUID currency) {
        Treasury treasury = treasuries.get(currency);
        long sum = Money.addCapped(treasury == null ? 0 : treasury.funds(), accounts.total(currency));
        for (Treasury other : treasuries.values()) {
            sum = Money.addCapped(sum, other.reserve(currency));
        }
        for (Escrow escrow : escrows.values()) {
            if (escrow.currency().equals(currency)) {
                sum = Money.addCapped(sum, escrow.amount());
            }
        }
        for (java.util.function.ToLongFunction<UUID> held : heldElsewhere.values()) {
            sum = Money.addCapped(sum, Math.max(0, held.applyAsLong(currency)));
        }
        return sum;
    }

    /**
     * Coins of a currency in the hands of players, as far as banks, treasuries and markets saw them go.
     */
    public long coinsOut(UUID currency) {
        return coinsOut.getOrDefault(currency, 0L);
    }

    public Map<UUID, Long> coinsOut() {
        return Collections.unmodifiableMap(coinsOut);
    }

    /**
     * Coins of a currency came into the hands of players (a positive count of their worth) or left them for a bank, a
     * treasury or a market (a negative one).
     */
    public void coinsIssued(UUID currency, long worth) {
        if (worth == 0) {
            return;
        }
        long next = Math.max(0, Math.min(Money.MAX, coinsOut(currency) + Math.max(-Money.MAX, Math.min(Money.MAX, worth))));
        if (next == 0) {
            coinsOut.remove(currency);
        } else {
            coinsOut.put(currency, next);
        }
        changed();
    }

    /**
     * The money of a currency the exchange rate counts: everything on the books and the coins in hands.
     */
    public long moneyMass(UUID currency) {
        return Money.addCapped(supply(currency), coinsOut(currency));
    }

    // ---------------------------------------------------------------- exchange rates

    /**
     * The exchange rate of a currency, {@link ExchangeRates#ONE} until the first game day counted it.
     */
    public long rate(UUID currency) {
        return rates.getOrDefault(currency, ExchangeRates.ONE);
    }

    public Map<UUID, Long> rates() {
        return Collections.unmodifiableMap(rates);
    }

    /**
     * The game day the rates were last counted on.
     */
    public long rateDay() {
        return rateDay;
    }

    /**
     * Moves the rate of every current currency a share of the way to its target, once a game day.
     *
     * @param targets the target rate of each currency
     */
    public void updateRates(long day, Map<UUID, Long> targets, io.github.sekerator.kingdomworld.core.trade.TradeRules rules) {
        if (day <= rateDay) {
            return;
        }
        boolean first = rateDay < 0;
        rateDay = day;
        targets.forEach((currency, target) -> {
            if (treasuries.containsKey(currency)) {
                rates.put(currency, ExchangeRates.smooth(first && !rates.containsKey(currency) ? ExchangeRates.ONE : rate(currency), target, rules));
            }
        });
        changed();
    }

    // ---------------------------------------------------------------- reserves, exchange and the market

    private Outcome checkCurrent(UUID currency) {
        if (!isActive(currency)) {
            return Outcome.fail(retired.containsKey(currency) ? KingdomError.OLD_CURRENCY : KingdomError.UNKNOWN_CURRENCY);
        }
        return Outcome.DONE;
    }

    /**
     * Whether a treasury could take money in a currency: its own onto its balance, another into its reserves.
     */
    private boolean canTake(Treasury treasury, UUID currency, long amount) {
        return currency.equals(treasury.kingdomId()) ? treasury.canReceive(amount) : treasury.canReceiveReserve(currency, amount);
    }

    private void take(Treasury treasury, UUID currency, long amount, LedgerCategory category, long day) {
        boolean done = currency.equals(treasury.kingdomId()) ? treasury.receive(amount, category, day) : treasury.receiveReserve(currency, amount);
        if (!done) {
            throw new IllegalStateException("The treasury of " + treasury.kingdomId() + " could not take " + amount);
        }
    }

    private long available(Treasury treasury, UUID currency) {
        return currency.equals(treasury.kingdomId()) ? treasury.funds() : treasury.reserve(currency);
    }

    private void give(Treasury treasury, UUID currency, long amount, LedgerCategory category, long day) {
        boolean done = currency.equals(treasury.kingdomId()) ? treasury.spend(amount, category, day) : treasury.spendReserve(currency, amount);
        if (!done) {
            throw new IllegalStateException("The treasury of " + treasury.kingdomId() + " could not give " + amount);
        }
    }

    /**
     * Whether a player could pay a kingdom an amount in a currency: from the account, or with coins the caller takes.
     *
     * @param payer the player paying from the account, or {@code null} for coins
     */
    public Outcome checkPayKingdom(UUID kingdomId, UUID currency, long amount, UUID payer) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        Outcome current = checkCurrent(currency);
        if (!current.ok()) {
            return current;
        }
        if (!Money.isPayment(amount)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (payer != null && accounts.balance(payer, currency) < amount) {
            return Outcome.fail(KingdomError.NOT_ENOUGH_MONEY);
        }
        return canTake(treasury, currency, amount) ? Outcome.DONE : Outcome.fail(KingdomError.TOO_MUCH_MONEY);
    }

    /**
     * A player pays a kingdom for its goods on its market: from the account or with coins the caller took, in the
     * kingdom's currency onto its balance or in another into its reserves.
     *
     * @param payer the player paying from the account, or {@code null} for coins
     */
    public Outcome payKingdom(UUID kingdomId, UUID currency, long amount, UUID payer, long day) {
        return payKingdom(kingdomId, currency, amount, payer, LedgerCategory.TRADE, day);
    }

    /**
     * A player pays a kingdom, written into the journal under a category.
     *
     * @param payer the player paying from the account, or {@code null} for coins
     */
    public Outcome payKingdom(UUID kingdomId, UUID currency, long amount, UUID payer, LedgerCategory category, long day) {
        Outcome check = checkPayKingdom(kingdomId, currency, amount, payer);
        if (!check.ok()) {
            return check;
        }
        if (payer != null) {
            accounts.debit(payer, currency, amount);
        }
        take(treasuries.get(kingdomId), currency, amount, category, day);
        changed();
        return Outcome.DONE;
    }

    /**
     * Whether a treasury could pay a player an amount of its own currency, onto the account or as coins.
     *
     * @param payee the player paid onto the account, or {@code null} for coins
     */
    public Outcome checkKingdomPays(UUID kingdomId, long amount, UUID payee) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (!Money.isPayment(amount)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (treasury.funds() < amount) {
            return Outcome.fail(KingdomError.TREASURY_EMPTY);
        }
        if (payee != null && !accounts.canCredit(payee, kingdomId, amount)) {
            return Outcome.fail(KingdomError.TOO_MUCH_MONEY);
        }
        return Outcome.DONE;
    }

    /**
     * A treasury pays a player for goods, onto the account or as coins the caller hands out.
     *
     * @param payee the player paid onto the account, or {@code null} for coins
     */
    public Outcome kingdomPays(UUID kingdomId, long amount, UUID payee, LedgerCategory category, long day) {
        Outcome check = checkKingdomPays(kingdomId, amount, payee);
        if (!check.ok()) {
            return check;
        }
        treasuries.get(kingdomId).spend(amount, category, day);
        if (payee != null) {
            accounts.credit(payee, kingdomId, amount);
            repayFromIncome(payee, kingdomId, amount, day);
        }
        changed();
        return Outcome.DONE;
    }

    /**
     * Whether the lot of a player can be sold: the buyer pays {@code paid} in a currency, the treasury keeps the tax and
     * pays the seller the rest of the price in its own currency.
     */
    public Outcome checkLotSale(UUID kingdomId, UUID currency, long paid, UUID payer, UUID seller, long price, long tax) {
        Outcome pay = checkPayKingdom(kingdomId, currency, paid, payer);
        if (!pay.ok()) {
            return pay;
        }
        Treasury treasury = treasuries.get(kingdomId);
        long toSeller = price - tax;
        long funds = currency.equals(kingdomId) ? Money.addCapped(treasury.funds(), paid) : treasury.funds();
        if (toSeller > 0 && funds < toSeller) {
            return Outcome.fail(KingdomError.TREASURY_EMPTY);
        }
        if (toSeller > 0 && !accounts.canCredit(seller, kingdomId, toSeller)) {
            return Outcome.fail(KingdomError.TOO_MUCH_MONEY);
        }
        return Outcome.DONE;
    }

    /**
     * Sells the lot of a player: the buyer pays, the treasury keeps the tax, the seller gets the rest on the account in
     * the kingdom's currency, offline or not.
     *
     * @param payer the buyer paying from the account, or {@code null} for coins the caller took
     */
    public Outcome sellLot(UUID kingdomId, UUID currency, long paid, UUID payer, UUID seller, long price, long tax, long day) {
        Outcome check = checkLotSale(kingdomId, currency, paid, payer, seller, price, tax);
        if (!check.ok()) {
            return check;
        }
        Treasury treasury = treasuries.get(kingdomId);
        if (payer != null) {
            accounts.debit(payer, currency, paid);
        }
        take(treasury, currency, paid, LedgerCategory.TRADE, day);
        long toSeller = price - tax;
        if (toSeller > 0) {
            treasury.spend(toSeller, LedgerCategory.TRADE, day);
            accounts.credit(seller, kingdomId, toSeller);
            repayFromIncome(seller, kingdomId, toSeller, day);
        }
        changed();
        return Outcome.DONE;
    }

    /**
     * What an amount of a currency gives in the currency of a kingdom's bank, by the rates and less the fee.
     */
    public long exchangeQuote(UUID kingdomId, UUID currency, long amount, int feePermille) {
        return ExchangeRates.convert(amount, rate(currency), rate(kingdomId), feePermille);
    }

    /**
     * Whether a player could exchange an amount of a currency at a kingdom's bank.
     *
     * @param payer the player paying from the account, or {@code null} for coins the caller takes
     * @param payee the player paid onto the account, or {@code null} for coins the caller hands out
     */
    public Outcome checkExchange(UUID kingdomId, UUID currency, long amount, int feePermille, UUID payer, UUID payee) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (currency.equals(kingdomId)) {
            return Outcome.fail(KingdomError.SAME_CURRENCY);
        }
        Outcome current = checkCurrent(currency);
        if (!current.ok()) {
            return current;
        }
        if (!Money.isPayment(amount)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (payer != null && accounts.balance(payer, currency) < amount) {
            return Outcome.fail(KingdomError.NOT_ENOUGH_MONEY);
        }
        long got = exchangeQuote(kingdomId, currency, amount, feePermille);
        if (got < 1) {
            return Outcome.fail(KingdomError.TOO_LITTLE);
        }
        if (treasury.funds() < got) {
            return Outcome.fail(KingdomError.TREASURY_EMPTY);
        }
        if (!treasury.canReceiveReserve(currency, amount) || (payee != null && !accounts.canCredit(payee, kingdomId, got))) {
            return Outcome.fail(KingdomError.TOO_MUCH_MONEY);
        }
        return Outcome.DONE;
    }

    /**
     * A player exchanges money of a currency at the bank of a kingdom: the treasury takes it into its reserves and pays
     * its own currency by the rates, less the fee, from its funds. Nothing is made: the currency given moves into the
     * reserves, the currency got moves out of the treasury.
     *
     * @return what the player got, or the reason it was refused
     */
    public Exchanged exchange(UUID kingdomId, UUID currency, long amount, int feePermille, UUID payer, UUID payee, long day) {
        Outcome check = checkExchange(kingdomId, currency, amount, feePermille, payer, payee);
        if (!check.ok()) {
            return new Exchanged(check, 0);
        }
        Treasury treasury = treasuries.get(kingdomId);
        long got = exchangeQuote(kingdomId, currency, amount, feePermille);
        if (payer != null) {
            accounts.debit(payer, currency, amount);
        }
        treasury.receiveReserve(currency, amount);
        treasury.spend(got, LedgerCategory.EXCHANGE, day);
        if (payee != null) {
            accounts.credit(payee, kingdomId, got);
        }
        changed();
        return new Exchanged(Outcome.DONE, got);
    }

    /**
     * The result of an exchange.
     */
    /**
     * What an annexation moved: the money of the loser, what it is worth in the currency of the winner, and the reserves of
     * other currencies that moved as they were.
     */
    public record Annexed(Outcome outcome, long funds, long worth, Map<UUID, Long> reserves, long left) {
    }

    /**
     * An annexed kingdom hands its treasury to the winner before its currency becomes old: its money leaves its treasury and
     * the treasury of the winner grows by what it is worth by the rates; reserves of other currencies move as they are, and
     * reserves of the currency of the winner become its money again. Nothing is kept in the currency that is about to go.
     */
    public Annexed annex(UUID winner, UUID loser, long day) {
        Treasury from = treasuries.get(loser);
        Treasury to = treasuries.get(winner);
        if (from == null || to == null || winner.equals(loser)) {
            return new Annexed(Outcome.fail(KingdomError.UNKNOWN_CURRENCY), 0, 0, Map.of(), 0);
        }
        // As much as the treasury of the winner can take; the rest stays with the loser until there is room.
        long funds = Math.max(0, from.funds());
        long worth = funds == 0 ? 0 : ExchangeRates.convert(funds, rate(loser), rate(winner), 0);
        while (funds > 0 && !to.canReceive(worth)) {
            funds /= 2;
            worth = ExchangeRates.convert(funds, rate(loser), rate(winner), 0);
        }
        if (funds > 0) {
            if (!from.spend(funds, LedgerCategory.WAR, day)) {
                return new Annexed(Outcome.fail(KingdomError.NOT_ENOUGH_MONEY), 0, 0, Map.of(), from.funds());
            }
            if (!to.receive(worth, LedgerCategory.WAR, day)) {
                from.receive(funds, LedgerCategory.WAR, day);
                return new Annexed(Outcome.fail(KingdomError.TOO_MUCH_MONEY), 0, 0, Map.of(), from.funds());
            }
        }
        Map<UUID, Long> moved = new LinkedHashMap<>();
        for (Map.Entry<UUID, Long> reserve : new LinkedHashMap<>(from.reserves()).entrySet()) {
            UUID currency = reserve.getKey();
            long amount = reserve.getValue();
            if (amount <= 0) {
                continue;
            }
            if (currency.equals(winner)) {
                if (to.receive(amount, LedgerCategory.WAR, day)) {
                    from.spendReserve(currency, amount);
                    moved.put(currency, amount);
                }
            } else if (to.canReceiveReserve(currency, amount)) {
                from.spendReserve(currency, amount);
                to.receiveReserve(currency, amount);
                moved.put(currency, amount);
            }
        }
        changed();
        long left = from.funds();
        return new Annexed(left == 0 ? Outcome.DONE : Outcome.fail(KingdomError.TOO_MUCH_MONEY), funds, worth, moved, left);
    }

    public record Exchanged(Outcome outcome, long got) {
    }

    /**
     * Change in a foreign currency for coins a player paid with: it comes out of the reserves the payment went into.
     */
    public Outcome reserveChange(UUID kingdomId, UUID currency, long amount) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null || !treasury.spendReserve(currency, amount)) {
            return Outcome.fail(KingdomError.NOT_ENOUGH_MONEY);
        }
        changed();
        return Outcome.DONE;
    }

    /**
     * Merchants sold goods abroad: new money for the treasury.
     */
    public Outcome exportIncome(UUID kingdomId, long amount, long day) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (amount <= 0) {
            return Outcome.DONE;
        }
        return treasury.receive(amount, LedgerCategory.TRADE, day) ? Outcome.DONE : Outcome.fail(KingdomError.TOO_MUCH_MONEY);
    }

    /**
     * Goods ordered abroad are paid for: the money leaves the world.
     */
    public Outcome importPayment(UUID kingdomId, long amount, long day) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (!Money.isPayment(amount)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        return treasury.spend(amount, LedgerCategory.TRADE, day) ? Outcome.DONE : Outcome.fail(KingdomError.NOT_ENOUGH_MONEY);
    }

    /**
     * The peaceful citizens of a kingdom of the world pay a tax out of the wages they were just paid. The caller sees to it
     * that the tax is no more than the part of those wages that would leave the world: no money is made.
     */
    public Outcome citizenTax(UUID kingdomId, long amount, long day) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (!Money.isPayment(amount)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (!treasury.canReceive(amount)) {
            return Outcome.fail(KingdomError.TOO_MUCH_MONEY);
        }
        treasury.receive(amount, LedgerCategory.TAXES, day);
        changed();
        return Outcome.DONE;
    }

    /**
     * How much the treasury of a kingdom can still take; 0 for a kingdom without one.
     */
    public long treasuryRoom(UUID kingdomId) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null) {
            return 0;
        }
        return Math.max(0, Money.MAX - Math.max(Math.max(0, treasury.balance()), Math.max(0, treasury.funds())));
    }

    /**
     * The townsfolk of the cities of a kingdom pay their dues into its treasury, as taxes; what a full treasury cannot take
     * is not taken.
     *
     * @return what was paid
     */
    public long folkDues(UUID kingdomId, long amount, long day) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null || amount <= 0) {
            return 0;
        }
        long paid = Math.min(amount, treasuryRoom(kingdomId));
        if (paid <= 0 || !treasury.receive(paid, LedgerCategory.TAXES, day)) {
            return 0;
        }
        changed();
        return paid;
    }

    /**
     * The army pays mercenaries from the treasury: the money leaves the world.
     */
    public Outcome armyPayment(UUID kingdomId, long amount, long day) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (!Money.isPayment(amount)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        return treasury.spend(amount, LedgerCategory.ARMY, day) ? Outcome.DONE : Outcome.fail(KingdomError.TREASURY_EMPTY);
    }

    /**
     * The court pays from the treasury: the share of a noble house, the wages of spies, works the court asked for. The money
     * leaves the world, as wages of citizens do.
     */
    public Outcome nobilityPayment(UUID kingdomId, long amount, long day) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (!Money.isPayment(amount)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (!treasury.spend(amount, LedgerCategory.NOBILITY, day)) {
            return Outcome.fail(KingdomError.TREASURY_EMPTY);
        }
        changed();
        return Outcome.DONE;
    }

    /**
     * Money of one treasury goes to another in the currency of the payer, in the books of both or not at all: a ransom, or
     * what a spy took.
     */
    public Outcome nobilityTransfer(UUID payer, UUID payee, long amount, long day) {
        Outcome check = checkHoldFromTreasury(payer, amount);
        if (!check.ok()) {
            return check;
        }
        if (!canReleaseToTreasury(payee, payer, amount)) {
            return Outcome.fail(treasuries.containsKey(payee) ? KingdomError.TOO_MUCH_MONEY : KingdomError.UNKNOWN_KINGDOM);
        }
        holdFromTreasury(payer, amount, LedgerCategory.NOBILITY, day);
        Outcome released = releaseToTreasury(payee, payer, amount, LedgerCategory.NOBILITY, day);
        if (!released.ok()) {
            releaseToTreasury(payer, payer, amount, LedgerCategory.NOBILITY, day);
            return released;
        }
        return Outcome.DONE;
    }

    public Outcome setSalesTax(UUID kingdomId, int permille, int maxPermille) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (permille < 0 || permille > Math.min(1000, maxPermille)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (permille == treasury.salesTaxPermille()) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        treasury.setSalesTax(permille);
        return Outcome.DONE;
    }

    // ---------------------------------------------------------------- escrow of deals

    public Optional<Escrow> escrow(long id) {
        return Optional.ofNullable(escrows.get(id));
    }

    public Map<Long, Escrow> escrows() {
        return Collections.unmodifiableMap(escrows);
    }

    /**
     * Whether a kingdom could pay an amount in a currency into escrow: its own from its treasury, another from its
     * reserves.
     */
    public Outcome checkEscrow(UUID payer, UUID payee, UUID currency, long amount) {
        Treasury from = treasuries.get(payer);
        Treasury to = treasuries.get(payee);
        if (from == null || to == null) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (!(currency.equals(payer) || currency.equals(payee))) {
            return Outcome.fail(KingdomError.UNKNOWN_CURRENCY);
        }
        if (!Money.isPayment(amount)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (available(from, currency) < amount) {
            return Outcome.fail(KingdomError.NOT_ENOUGH_MONEY);
        }
        return Outcome.DONE;
    }

    /**
     * Takes the payment of a deal into escrow.
     *
     * @return the escrow, or empty if the payer cannot pay
     */
    public Optional<Escrow> openEscrow(UUID payer, UUID payee, UUID currency, long amount, long day) {
        if (!checkEscrow(payer, payee, currency, amount).ok()) {
            return Optional.empty();
        }
        give(treasuries.get(payer), currency, amount, LedgerCategory.TRADE, day);
        Escrow escrow = new Escrow(nextEscrow++, payer, payee, currency, amount);
        escrows.put(escrow.id(), escrow);
        changed();
        return Optional.of(escrow);
    }

    /**
     * The caravan arrived: the payment goes to the payee, its own currency onto its balance, another into its reserves.
     * If the payee is gone or cannot take it, it goes back to the payer.
     */
    public Outcome releaseEscrow(long id, long day) {
        Escrow escrow = escrows.get(id);
        if (escrow == null) {
            return Outcome.fail(KingdomError.UNKNOWN_DEAL);
        }
        Treasury payee = treasuries.get(escrow.payee());
        if (payee == null || !canTake(payee, escrow.currency(), escrow.amount())) {
            return refundEscrow(id, day);
        }
        escrows.remove(id);
        take(payee, escrow.currency(), escrow.amount(), LedgerCategory.TRADE, day);
        changed();
        return Outcome.DONE;
    }

    /**
     * The caravan was lost or called off: the payment goes back to the payer. If the payer is gone, the money of its
     * dissolved treasury is gone with it.
     */
    public Outcome refundEscrow(long id, long day) {
        Escrow escrow = escrows.remove(id);
        if (escrow == null) {
            return Outcome.fail(KingdomError.UNKNOWN_DEAL);
        }
        Treasury payer = treasuries.get(escrow.payer());
        if (payer != null && canTake(payer, escrow.currency(), escrow.amount())) {
            take(payer, escrow.currency(), escrow.amount(), LedgerCategory.TRADE, day);
        }
        changed();
        return Outcome.DONE;
    }

    /**
     * Robbers took part of the goods of a caravan: that part of the payment goes back to the payer, the rest stays in escrow
     * until the caravan arrives.
     */
    public Outcome refundEscrowPart(long id, long amount, long day) {
        Escrow escrow = escrows.get(id);
        if (escrow == null) {
            return Outcome.fail(KingdomError.UNKNOWN_DEAL);
        }
        if (amount <= 0) {
            return Outcome.DONE;
        }
        if (amount >= escrow.amount()) {
            return refundEscrow(id, day);
        }
        escrows.put(id, new Escrow(escrow.id(), escrow.payer(), escrow.payee(), escrow.currency(), escrow.amount() - amount));
        Treasury payer = treasuries.get(escrow.payer());
        if (payer != null && canTake(payer, escrow.currency(), amount)) {
            take(payer, escrow.currency(), amount, LedgerCategory.TRADE, day);
        }
        changed();
        return Outcome.DONE;
    }

    // ---------------------------------------------------------------- money held for guild contracts

    /**
     * Tells the books how much money of each currency the guild holds in the escrow of its contracts, so that the supply
     * of a currency counts it.
     */
    public void setHeldElsewhere(java.util.function.ToLongFunction<UUID> held) {
        setHeldElsewhere("guild", held);
    }

    /**
     * Tells the books how much money of each currency one holder outside them keeps, by its name; a new one of the same
     * name takes its place.
     */
    public void setHeldElsewhere(String holder, java.util.function.ToLongFunction<UUID> held) {
        heldElsewhere.put(Objects.requireNonNull(holder, "holder"), Objects.requireNonNull(held, "held"));
    }

    // ---------------------------------------------------------------- provinces bought

    /**
     * What a kingdom pays in its own currency for a price in the currency of another, by the rates of today: rounded up, -1
     * when it is more than any money.
     */
    public long landCost(UUID buyer, UUID seller, long price) {
        return buyer.equals(seller) ? price : ExchangeRates.priceIn(price, rate(seller), rate(buyer), 0);
    }

    /**
     * Whether a kingdom could pay another for a province.
     */
    public Outcome checkLandPayment(UUID buyer, UUID seller, long price) {
        long cost = landCost(buyer, seller, price);
        if (cost < 0 || !Money.isPayment(Math.max(1, cost))) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        Outcome check = checkHoldFromTreasury(buyer, Math.max(1, cost));
        if (!check.ok()) {
            return check;
        }
        if (!canReleaseToTreasury(seller, buyer, Math.max(1, cost))) {
            return Outcome.fail(treasuries.containsKey(seller) ? KingdomError.TOO_MUCH_MONEY : KingdomError.UNKNOWN_KINGDOM);
        }
        return Outcome.DONE;
    }

    /**
     * A province is paid for: the buyer's treasury pays the price, set in the currency of the seller, in its own currency by
     * the rates of today, into the reserves of the seller; a line "Land" on both sides, in the books of both or not at all.
     * Nothing is made or lost: the same money moves.
     *
     * @return what the buyer paid in its currency, or the reason it could not
     */
    public Exchanged landPayment(UUID buyer, UUID seller, long price, long day) {
        Outcome check = checkLandPayment(buyer, seller, price);
        if (!check.ok()) {
            return new Exchanged(check, 0);
        }
        long cost = Math.max(1, landCost(buyer, seller, price));
        holdFromTreasury(buyer, cost, LedgerCategory.LAND, day);
        Outcome released = releaseToTreasury(seller, buyer, cost, LedgerCategory.LAND, day);
        if (!released.ok()) {
            releaseToTreasury(buyer, buyer, cost, LedgerCategory.LAND, day);
            return new Exchanged(released, 0);
        }
        noteLandIncome(seller, buyer, cost, day);
        return new Exchanged(Outcome.DONE, cost);
    }

    /**
     * Money of another currency a seller of a province took into its reserves: its journal shows what it is worth in its own
     * currency under "Land", as money of its own would be shown. Nothing on the balances changes.
     */
    public void noteLandIncome(UUID seller, UUID currency, long amount, long day) {
        Treasury treasury = treasuries.get(seller);
        if (treasury == null || currency.equals(seller) || amount <= 0) {
            return;
        }
        long worth = ExchangeRates.convert(amount, rate(currency), rate(seller), 0);
        if (worth > 0) {
            treasury.journal().record(day, LedgerCategory.LAND, worth, 0);
            changed();
        }
    }

    /**
     * Whether a treasury could put an amount of its own currency into escrow.
     */
    public Outcome checkHoldFromTreasury(UUID kingdomId, long amount) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (!Money.isPayment(amount)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        return treasury.funds() >= amount ? Outcome.DONE : Outcome.fail(KingdomError.TREASURY_EMPTY);
    }

    /**
     * Money of a treasury goes into escrow; the caller keeps it in its books in the same moment.
     */
    public Outcome holdFromTreasury(UUID kingdomId, long amount, LedgerCategory category, long day) {
        Outcome check = checkHoldFromTreasury(kingdomId, amount);
        if (!check.ok()) {
            return check;
        }
        give(treasuries.get(kingdomId), kingdomId, amount, category, day);
        changed();
        return Outcome.DONE;
    }

    /**
     * Whether a player could put an amount of a current currency into escrow from the account.
     */
    public Outcome checkHoldFromAccount(UUID player, UUID currency, long amount) {
        Outcome current = checkCurrent(currency);
        if (!current.ok()) {
            return current;
        }
        if (!Money.isPayment(amount)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        return accounts.balance(player, currency) >= amount ? Outcome.DONE : Outcome.fail(KingdomError.NOT_ENOUGH_MONEY);
    }

    /**
     * Money of a player's account goes into escrow; the caller keeps it in its books in the same moment.
     */
    public Outcome holdFromAccount(UUID player, UUID currency, long amount) {
        Outcome check = checkHoldFromAccount(player, currency, amount);
        if (!check.ok()) {
            return check;
        }
        accounts.debit(player, currency, amount);
        changed();
        return Outcome.DONE;
    }

    /**
     * Whether money from escrow could go onto a player's account; old currencies are kept on accounts too.
     */
    public boolean canReleaseToAccount(UUID player, UUID currency, long amount) {
        return amount == 0 || accounts.canCredit(player, currency, amount);
    }

    /**
     * Money from escrow goes onto a player's account.
     */
    public Outcome releaseToAccount(UUID player, UUID currency, long amount) {
        if (!Money.isAmount(amount)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (amount == 0) {
            return Outcome.DONE;
        }
        if (!accounts.canCredit(player, currency, amount)) {
            return Outcome.fail(KingdomError.TOO_MUCH_MONEY);
        }
        accounts.credit(player, currency, amount);
        changed();
        return Outcome.DONE;
    }

    /**
     * Whether money from escrow could go to a treasury: its own currency onto its balance, another into its reserves.
     */
    public boolean canReleaseToTreasury(UUID kingdomId, UUID currency, long amount) {
        Treasury treasury = treasuries.get(kingdomId);
        return amount == 0 || (treasury != null && isActive(currency) && canTake(treasury, currency, amount));
    }

    /**
     * Money from escrow goes to a treasury: its own currency onto its balance, another into its reserves.
     */
    public Outcome releaseToTreasury(UUID kingdomId, UUID currency, long amount, LedgerCategory category, long day) {
        if (!Money.isAmount(amount)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (amount == 0) {
            return Outcome.DONE;
        }
        if (!canReleaseToTreasury(kingdomId, currency, amount)) {
            return Outcome.fail(treasuries.containsKey(kingdomId) ? KingdomError.TOO_MUCH_MONEY : KingdomError.UNKNOWN_KINGDOM);
        }
        take(treasuries.get(kingdomId), currency, amount, category, day);
        changed();
        return Outcome.DONE;
    }

    /**
     * Whether a player could pay a fee to a treasury from the account in its currency.
     */
    public Outcome checkPayTreasury(UUID player, UUID kingdomId, long amount) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (!Money.isPayment(amount)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (accounts.balance(player, kingdomId) < amount) {
            return Outcome.fail(KingdomError.NOT_ENOUGH_MONEY);
        }
        return treasury.canReceive(amount) ? Outcome.DONE : Outcome.fail(KingdomError.TOO_MUCH_MONEY);
    }

    /**
     * A player pays a fee to a treasury from the account in its currency, without touching any debt.
     */
    public Outcome payTreasury(UUID player, UUID kingdomId, long amount, LedgerCategory category, long day) {
        Outcome check = checkPayTreasury(player, kingdomId, amount);
        if (!check.ok()) {
            return check;
        }
        accounts.debit(player, kingdomId, amount);
        treasuries.get(kingdomId).receive(amount, category, day);
        changed();
        return Outcome.DONE;
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
        treasury.restore("", 0, Coins.NONE, defaultPollTax, defaultSalesTaxPermille, Map.of(), Map.of(), day, Payroll.Summary.NONE);
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
        // Reserves of the old currency in other treasuries cannot be spent any more.
        for (Treasury other : treasuries.values()) {
            other.dropReserve(kingdomId);
        }
        rates.remove(kingdomId);
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
     * Sets what the mint of a kingdom does with one metal of the config.
     */
    public Outcome setMintMetal(UUID kingdomId, String metal, MintMetals.Setting setting) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_CURRENCY);
        }
        if (!MintMetals.isValidMetal(metal)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (setting.equals(treasury.mintMetal(metal))) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        return treasury.setMintMetal(metal, setting) ? Outcome.DONE : Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
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
        repayFromIncome(to, currency, amount, today);
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
        repayFromIncome(player, kingdomId, amount, day);
        return Outcome.DONE;
    }

    /**
     * Coins leave the vault of a treasury for somebody's hands; the caller puts exactly these coins into an inventory in
     * the same moment. Written into the journal as a transfer.
     */
    public Outcome takeFromVault(UUID kingdomId, Denomination denomination, long count, long day) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_CURRENCY);
        }
        if (count < 1 || count > Money.MAX) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (!treasury.takeCoins(denomination, count, LedgerCategory.TRANSFERS, day)) {
            return Outcome.fail(KingdomError.NO_COINS);
        }
        return Outcome.DONE;
    }

    /**
     * Coins that were taken from a vault but did not fit into an inventory go back into it.
     */
    public Outcome returnToVault(UUID kingdomId, Denomination denomination, long count, long day) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_CURRENCY);
        }
        return treasury.store(denomination, count, LedgerCategory.TRANSFERS, day) ? Outcome.DONE : Outcome.fail(KingdomError.TOO_MUCH_MONEY);
    }

    /**
     * A player owes a treasury more, for example the part of a fine the player could not pay.
     */
    public Outcome addDebt(UUID kingdomId, UUID player, long amount) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (!Money.isAmount(amount)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (amount > 0) {
            treasury.setDebt(player, Money.addCapped(treasury.debt(player), amount));
            changed();
        }
        return Outcome.DONE;
    }

    /**
     * A player pays back a debt with coins the caller took from the player in the same moment.
     */
    public Outcome repayDebt(UUID kingdomId, UUID player, long amount, long day) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        long debt = treasury.debt(player);
        if (!Money.isPayment(amount) || amount > debt) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (!treasury.canReceive(amount)) {
            return Outcome.fail(KingdomError.TOO_MUCH_MONEY);
        }
        treasury.receive(amount, LedgerCategory.TAXES, day);
        treasury.setDebt(player, debt - amount);
        changed();
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

    /**
     * The money a kingdom of the world has when it appears; written into its journal as its founding.
     */
    public Outcome foundingGrant(UUID kingdomId, long amount, long day) {
        Treasury treasury = treasuries.get(kingdomId);
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_CURRENCY);
        }
        if (amount <= 0 || amount > Money.MAX) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        return treasury.receive(amount, LedgerCategory.FOUNDING, day) ? Outcome.DONE : Outcome.fail(KingdomError.TOO_MUCH_MONEY);
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
            wages[i] = claims.get(i).scaled(treasury.wage(claims.get(i).profession(), rules));
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
            percents.put(claim.citizenId(), Payroll.percent(paid[i], claim.scaled(rules.defaultWage(claim.profession()))));
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

    public void restoreRate(UUID currency, long rate) {
        if (rate < 1) {
            throw new IllegalArgumentException("Rate of " + currency + " out of range");
        }
        rates.put(currency, rate);
    }

    public void restoreRateDay(long day) {
        rateDay = day;
    }

    public void restoreEscrow(Escrow escrow) {
        if (escrows.containsKey(escrow.id())) {
            throw new IllegalArgumentException("Two escrows with the id " + escrow.id());
        }
        escrows.put(escrow.id(), escrow);
        nextEscrow = Math.max(nextEscrow, escrow.id() + 1);
    }

    public void restoreCoinsOut(UUID currency, long worth) {
        if (!Money.isAmount(worth)) {
            throw new IllegalArgumentException("Coins of " + currency + " out of range");
        }
        if (worth > 0) {
            coinsOut.put(currency, worth);
        }
    }

    public long nextEscrowId() {
        return nextEscrow;
    }
}
