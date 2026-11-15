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
    /** The purses of the townspeople, the treasuries of the cities and the tills of the shops. */
    private final CityPurses purses = new CityPurses();
    /** What the treasury of each city keeps besides its money, by kingdom and city. */
    private final Map<String, CityTreasury> cityTreasuries = new LinkedHashMap<>();
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
     *                 officers of the guard and the army; for the townsfolk of a post a thousand for each of them
     * @param folk     whether the claim is that of the townsfolk of a post, all of them in one: they are paid after the named
     *                 citizens, out of what is left
     */
    public record WageClaim(UUID citizenId, String profession, int permille, boolean folk) {
        public WageClaim {
            permille = Math.max(0, Math.min(folk ? Integer.MAX_VALUE : 1_000_000, permille));
        }

        public WageClaim(UUID citizenId, String profession, int permille) {
            this(citizenId, profession, permille, false);
        }

        public WageClaim(UUID citizenId, String profession) {
            this(citizenId, profession, 1_000);
        }

        /**
         * The one claim of the townsfolk of a post: the wage of its profession for each of them.
         */
        public static WageClaim folk(UUID postId, String profession, int workers) {
            return new WageClaim(postId, profession, (int) Math.min(Integer.MAX_VALUE, Math.max(0L, workers) * 1_000L), true);
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
    public record Payday(List<TaxPaid> taxes, Map<UUID, Long> pay, Map<UUID, Integer> percents, Payroll.Summary summary,
                         Map<UUID, Long> cities) {
        public static final Payday NONE = new Payday(List.of(), Map.of(), Map.of(), Payroll.Summary.NONE, Map.of());

        public Payday(List<TaxPaid> taxes, Map<UUID, Long> pay, Map<UUID, Integer> percents, Payroll.Summary summary) {
            this(taxes, pay, percents, summary, Map.of());
        }

        /**
         * The city into whose purse the wage of a citizen went, or -1 when it left the world.
         */
        public long cityOf(UUID citizen) {
            Long city = cities.get(citizen);
            return city == null ? -1 : city;
        }
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
        return Money.addCapped(sum, purses.total(currency));
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
        // What the cities of the loser still hold joins its treasury and goes to the winner with it.
        sweepPurses(loser, day);
        // Money of the loser the winner seized from its cities becomes money of the winner by the rates, before the currency of
        // the loser becomes old.
        long seized = to.reserve(loser);
        if (seized > 0) {
            long seizedWorth = ExchangeRates.convert(seized, rate(loser), rate(winner), 0);
            if (seizedWorth > 0 && to.canReceive(seizedWorth)) {
                to.spendReserve(loser, seized);
                to.receive(seizedWorth, LedgerCategory.WAR, day);
            }
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
        if (!treasuries.containsKey(kingdomId)) {
            return;
        }
        // The money of its cities joins its treasury first, so that it goes with it as the money of the kingdom.
        sweepPurses(kingdomId, Math.floorDiv(gameTime, 24_000L));
        purses.forget(kingdomId);
        cityTreasuries.values().removeIf(city -> city.kingdomId().equals(kingdomId));
        Treasury treasury = treasuries.remove(kingdomId);
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
        return payday(kingdomId, day, taxpayers, claims, rules, citizen -> -1);
    }

    /**
     * The daily payday of a kingdom whose wages stay in its cities: the wage of each citizen goes into the purse of the
     * townspeople of its city, written into the journal as wages as before.
     *
     * @param cityOf the city of each citizen (0 the capital), or -1 for a wage that leaves the world as before
     */
    public Payday payday(UUID kingdomId, long day, List<UUID> taxpayers, List<WageClaim> claims, EconomyRules rules,
                         java.util.function.ToLongFunction<UUID> cityOf) {
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
        long[] paid = distribute(claims, wages, treasury.funds());
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
        Map<UUID, Long> cities = new LinkedHashMap<>();
        for (int i = 0; i < claims.size(); i++) {
            long city = paid[i] <= 0 ? -1 : cityOf.applyAsLong(claims.get(i).citizenId());
            if (city < 0) {
                continue;
            }
            CityPurses.PurseKey key = CityPurses.PurseKey.cityPurse(kingdomId, city);
            CityStatement.Tally tally = openCityTreasury(kingdomId, city).tally();
            tally.add(CityStatement.Line.CROWN_WAGES, paid[i]);
            if (purses.canReceive(key, paid[i])) {
                purses.set(key, purses.balance(key) + paid[i]);
                tally.add(CityStatement.Line.PURSE_IN, paid[i]);
                cities.put(claims.get(i).citizenId(), city);
            } else {
                // A purse that cannot hold more lets the wage go, as all wages went before.
                purses.noteOutflow(kingdomId, city, CityPurses.Outflow.PURSE_FULL, paid[i], day);
            }
        }
        Payroll.Summary summary = new Payroll.Summary(day, due, total, claims.size(), underpaid);
        treasury.setLastPayroll(summary);
        treasury.setLastPayday(day);
        changed();
        return new Payday(List.copyOf(taxes), pay, percents, summary, cities);
    }

    /**
     * Shares out the money of a treasury for wages: the named citizens first, the same share of each when it has too little, then
     * the townsfolk of the posts out of what is left. The townsfolk took their seats by themselves and never take the wage of a
     * named worker.
     */
    static long[] distribute(List<WageClaim> claims, long[] wages, long funds) {
        int folk = 0;
        for (WageClaim claim : claims) {
            if (claim.folk()) {
                folk++;
            }
        }
        if (folk == 0) {
            return Payroll.distribute(wages, funds);
        }
        long[] named = new long[claims.size() - folk];
        long[] theirs = new long[folk];
        for (int i = 0, n = 0, f = 0; i < claims.size(); i++) {
            if (claims.get(i).folk()) {
                theirs[f++] = wages[i];
            } else {
                named[n++] = wages[i];
            }
        }
        long[] namedPaid = Payroll.distribute(named, funds);
        long left = Math.max(0, funds);
        for (long pay : namedPaid) {
            left -= pay;
        }
        long[] folkPaid = Payroll.distribute(theirs, left);
        long[] paid = new long[claims.size()];
        for (int i = 0, n = 0, f = 0; i < claims.size(); i++) {
            paid[i] = claims.get(i).folk() ? folkPaid[f++] : namedPaid[n++];
        }
        return paid;
    }

    // ---------------------------------------------------------------- the money of the cities

    private static String cityKey(UUID kingdom, long city) {
        return kingdom + "/" + city;
    }

    /**
     * The purses of the townspeople, the treasuries of the cities and the tills of the shops.
     */
    public CityPurses purses() {
        return purses;
    }

    /**
     * What a purse holds.
     */
    public long purse(CityPurses.PurseKey key) {
        return purses.balance(key);
    }

    public Optional<CityTreasury> cityTreasury(UUID kingdom, long city) {
        return Optional.ofNullable(cityTreasuries.get(cityKey(kingdom, city)));
    }

    /**
     * The treasury of a city, opened the first time it is needed.
     */
    public CityTreasury openCityTreasury(UUID kingdom, long city) {
        CityTreasury found = cityTreasuries.get(cityKey(kingdom, city));
        if (found != null) {
            return found;
        }
        CityTreasury made = new CityTreasury(kingdom, city);
        cityTreasuries.put(cityKey(kingdom, city), made);
        changed();
        return made;
    }

    public Collection<CityTreasury> cityTreasuries() {
        return Collections.unmodifiableCollection(cityTreasuries.values());
    }

    public List<CityTreasury> cityTreasuriesOf(UUID kingdom) {
        List<CityTreasury> result = new ArrayList<>();
        for (CityTreasury city : cityTreasuries.values()) {
            if (city.kingdomId().equals(kingdom)) {
                result.add(city);
            }
        }
        return result;
    }

    /**
     * Forgets what a city kept besides its money, once its money went elsewhere.
     */
    public void dropCityTreasury(UUID kingdom, long city) {
        if (cityTreasuries.remove(cityKey(kingdom, city)) != null) {
            changed();
        }
    }

    /**
     * The city a purse belongs to: its own id for a purse or a treasury of a city; -1 for a till, whose city the caller knows.
     */
    private static long cityOf(CityPurses.PurseKey key) {
        return key.kind() == CityPurses.PurseKind.TILL ? -1 : key.id();
    }

    private void credit(CityPurses.PurseKey key, long amount, CityLedger line, long day) {
        purses.set(key, purses.balance(key) + amount);
        if (key.kind() == CityPurses.PurseKind.CITY_TREASURY) {
            CityTreasury city = openCityTreasury(key.kingdom(), key.id());
            city.journal().record(day, line, amount, 0);
            switch (line) {
                case TITHE -> city.tally().add(CityStatement.Line.TITHE, amount);
                case SALES_TAX -> city.tally().add(CityStatement.Line.SALES_TAX, amount);
                case GRANT -> {
                    city.tally().add(CityStatement.Line.GRANTED, amount);
                    city.tally().add(CityStatement.Line.CROWN_GRANTS, amount);
                    city.setGranted(Money.addCapped(city.granted(), amount));
                }
                default -> {
                }
            }
        } else if (key.kind() == CityPurses.PurseKind.CITY_PURSE) {
            openCityTreasury(key.kingdom(), key.id()).tally().add(CityStatement.Line.PURSE_IN, amount);
        }
    }

    private void debit(CityPurses.PurseKey key, long amount, CityLedger line, long day) {
        long left = purses.balance(key) - amount;
        purses.set(key, left);
        if (key.kind() == CityPurses.PurseKind.CITY_TREASURY) {
            CityTreasury city = openCityTreasury(key.kingdom(), key.id());
            city.journal().record(day, line, 0, amount);
            switch (line) {
                case CROWN_SHARE, IDLE_SHARE, RECALL -> {
                    city.tally().add(CityStatement.Line.TO_CROWN, amount);
                    city.tally().add(CityStatement.Line.CROWN_SHARE, amount);
                }
                case MATERIALS, IMPORTS, PURCHASES, OTHER -> {
                    city.tally().add(CityStatement.Line.SPENT, amount);
                    // What the crown granted is spent first.
                    city.setGranted(Math.max(0, city.granted() - amount));
                }
                default -> {
                }
            }
            city.setGranted(Math.min(city.granted(), left));
        }
    }

    /**
     * Money moves between two purses of one kingdom, written into the journal of a treasury of a city among them under a line.
     */
    public Outcome purseMove(CityPurses.PurseKey from, CityPurses.PurseKey to, long amount, CityLedger line, long day) {
        if (!from.kingdom().equals(to.kingdom())) {
            return Outcome.fail(KingdomError.SAME_CURRENCY);
        }
        if (!Money.isAmount(amount)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (amount == 0 || from.equals(to)) {
            return Outcome.DONE;
        }
        if (purses.balance(from) < amount) {
            return Outcome.fail(KingdomError.NOT_ENOUGH_MONEY);
        }
        if (!purses.canReceive(to, amount)) {
            return Outcome.fail(KingdomError.TOO_MUCH_MONEY);
        }
        debit(from, amount, line, day);
        credit(to, amount, line, day);
        changed();
        return Outcome.DONE;
    }

    public Outcome purseMove(CityPurses.PurseKey from, CityPurses.PurseKey to, long amount) {
        return purseMove(from, to, amount, CityLedger.OTHER, today);
    }

    /**
     * Money of a purse goes into the treasury of its kingdom, under a category of its journal and, for the treasury of a city,
     * a line of the city's.
     */
    public Outcome purseToTreasury(CityPurses.PurseKey key, long amount, LedgerCategory category, CityLedger line, long day) {
        Treasury treasury = treasuries.get(key.kingdom());
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (!Money.isAmount(amount)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (amount == 0) {
            return Outcome.DONE;
        }
        if (purses.balance(key) < amount) {
            return Outcome.fail(KingdomError.NOT_ENOUGH_MONEY);
        }
        if (!treasury.canReceive(amount)) {
            return Outcome.fail(KingdomError.TOO_MUCH_MONEY);
        }
        debit(key, amount, line, day);
        treasury.receive(amount, category, day);
        changed();
        return Outcome.DONE;
    }

    public Outcome purseToTreasury(CityPurses.PurseKey key, long amount, LedgerCategory category, long day) {
        return purseToTreasury(key, amount, category, CityLedger.OTHER, day);
    }

    /**
     * Money of the treasury of a kingdom goes into one of its purses.
     */
    public Outcome treasuryToPurse(CityPurses.PurseKey key, long amount, LedgerCategory category, CityLedger line, long day) {
        Treasury treasury = treasuries.get(key.kingdom());
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (!Money.isAmount(amount)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (amount == 0) {
            return Outcome.DONE;
        }
        if (treasury.funds() < amount) {
            return Outcome.fail(KingdomError.TREASURY_EMPTY);
        }
        if (!purses.canReceive(key, amount)) {
            return Outcome.fail(KingdomError.TOO_MUCH_MONEY);
        }
        treasury.spend(amount, category, day);
        credit(key, amount, line, day);
        changed();
        return Outcome.DONE;
    }

    public Outcome treasuryToPurse(CityPurses.PurseKey key, long amount, LedgerCategory category, long day) {
        return treasuryToPurse(key, amount, category, CityLedger.OTHER, day);
    }

    /**
     * Whether a player could pay a shop: the price into its till and the tax on the sale into the treasury of its city, from
     * the account or with coins the caller takes, in the currency of the kingdom of the shop only.
     *
     * @param player the player paying from the account, or {@code null} for coins
     */
    public Outcome checkPlayerPurchase(UUID player, CityPurses.PurseKey till, CityPurses.PurseKey cityTreasury, long price, long tax) {
        if (!treasuries.containsKey(till.kingdom()) || !till.kingdom().equals(cityTreasury.kingdom())) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (!Money.isAmount(price) || !Money.isAmount(tax) || !Money.isPayment(price + tax)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (player != null && accounts.balance(player, till.kingdom()) < price + tax) {
            return Outcome.fail(KingdomError.NOT_ENOUGH_MONEY);
        }
        if (!purses.canReceive(till, price) || !purses.canReceive(cityTreasury, tax)) {
            return Outcome.fail(KingdomError.TOO_MUCH_MONEY);
        }
        return Outcome.DONE;
    }

    /**
     * A player pays a shop: the price into its till, the tax into the treasury of its city. Coins are taken from the inventory
     * by the caller, who also counts them out of the hands of players as a bank does when they are handed in.
     *
     * @param player the player paying from the account, or {@code null} for coins
     */
    public Outcome playerPurchase(UUID player, CityPurses.PurseKey till, CityPurses.PurseKey cityTreasury, long price, long tax, long day) {
        Outcome check = checkPlayerPurchase(player, till, cityTreasury, price, tax);
        if (!check.ok()) {
            return check;
        }
        if (player != null) {
            accounts.debit(player, till.kingdom(), price + tax);
        }
        if (price > 0) {
            credit(till, price, CityLedger.OTHER, day);
        }
        if (tax > 0) {
            credit(cityTreasury, tax, CityLedger.SALES_TAX, day);
        }
        changed();
        return Outcome.DONE;
    }

    /**
     * Money of a purse goes onto the account of a player in the currency of its kingdom: a shop of citizens pays a player for what
     * it bought. As all income of a player, it pays back what the player owes the treasury first.
     */
    public Outcome purseToAccount(CityPurses.PurseKey key, UUID player, long amount, long day) {
        if (!treasuries.containsKey(key.kingdom())) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (!Money.isAmount(amount)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (amount == 0) {
            return Outcome.DONE;
        }
        if (purses.balance(key) < amount) {
            return Outcome.fail(KingdomError.NOT_ENOUGH_MONEY);
        }
        if (!accounts.canCredit(player, key.kingdom(), amount)) {
            return Outcome.fail(KingdomError.TOO_MUCH_MONEY);
        }
        debit(key, amount, CityLedger.PURCHASES, day);
        accounts.credit(player, key.kingdom(), amount);
        repayFromIncome(player, key.kingdom(), amount, day);
        changed();
        return Outcome.DONE;
    }

    /**
     * The townspeople of a city buy the lot of a player: the price leaves their purse, the sales tax of their city in it goes into
     * the treasury of the city (what they evade of it to the seller), the rest onto the account of the seller in the currency of
     * the kingdom, paying back what the seller owes the treasury first.
     *
     * @param tax the tax collected out of the price
     */
    public Outcome purseBuysLot(CityPurses.PurseKey purse, CityPurses.PurseKey cityTreasury, UUID seller, long price, long tax, long day) {
        if (!treasuries.containsKey(purse.kingdom()) || !purse.kingdom().equals(cityTreasury.kingdom())) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (!Money.isPayment(price) || !Money.isAmount(tax) || tax > price) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (purses.balance(purse) < price) {
            return Outcome.fail(KingdomError.NOT_ENOUGH_MONEY);
        }
        long toSeller = price - tax;
        if (!purses.canReceive(cityTreasury, tax) || !accounts.canCredit(seller, purse.kingdom(), toSeller)) {
            return Outcome.fail(KingdomError.TOO_MUCH_MONEY);
        }
        debit(purse, price, CityLedger.OTHER, day);
        if (tax > 0) {
            credit(cityTreasury, tax, CityLedger.SALES_TAX, day);
        }
        if (toSeller > 0) {
            accounts.credit(seller, purse.kingdom(), toSeller);
            repayFromIncome(seller, purse.kingdom(), toSeller, day);
        }
        changed();
        return Outcome.DONE;
    }

    /**
     * Money comes from abroad into a purse: what a trading house of citizens got for the goods citizens made that it exported. It
     * is new money of the currency, as the income of the merchants of the crown is.
     */
    public Outcome purseFromAbroad(CityPurses.PurseKey key, long amount, long day) {
        if (!treasuries.containsKey(key.kingdom())) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (!Money.isAmount(amount)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (amount == 0) {
            return Outcome.DONE;
        }
        if (!purses.canReceive(key, amount)) {
            return Outcome.fail(KingdomError.TOO_MUCH_MONEY);
        }
        credit(key, amount, CityLedger.OTHER, day);
        changed();
        return Outcome.DONE;
    }

    /**
     * Money of a purse leaves the world: goods from afar, the upkeep of a shop, goods a city bought abroad, a purse too full.
     *
     * @param city the city it is counted for; for a purse or a treasury of a city its own is taken
     */
    public Outcome purseLeaves(CityPurses.PurseKey key, long city, long amount, CityPurses.Outflow outflow, long day) {
        if (!Money.isAmount(amount)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (amount == 0) {
            return Outcome.DONE;
        }
        if (purses.balance(key) < amount) {
            return Outcome.fail(KingdomError.NOT_ENOUGH_MONEY);
        }
        debit(key, amount, outflow == CityPurses.Outflow.IMPORT ? CityLedger.IMPORTS : CityLedger.OTHER, day);
        long counted = cityOf(key) >= 0 ? cityOf(key) : city;
        purses.noteOutflow(key.kingdom(), counted, outflow, amount, day);
        changed();
        return Outcome.DONE;
    }

    /**
     * Money of a purse goes to a holder outside the books in the same moment (the purse of a house of citizens, which the books
     * count through {@link #setHeldElsewhere}).
     *
     * @return what moved: as much as the purse holds, at most the amount
     */
    public long purseHold(CityPurses.PurseKey key, long amount) {
        long moved = Math.max(0, Math.min(amount, purses.balance(key)));
        if (moved > 0) {
            purses.set(key, purses.balance(key) - moved);
            if (key.kind() == CityPurses.PurseKind.CITY_TREASURY) {
                CityTreasury city = openCityTreasury(key.kingdom(), key.id());
                city.setGranted(Math.min(city.granted(), purses.balance(key)));
            }
            changed();
        }
        return moved;
    }

    /**
     * Money a holder outside the books kept comes back into a purse; the holder lets it go in the same moment.
     *
     * @return what the purse took: all of it, unless the purse cannot hold more or its kingdom is gone
     */
    public long purseRelease(CityPurses.PurseKey key, long amount, long day) {
        if (amount <= 0 || !purses.canReceive(key, amount) || !treasuries.containsKey(key.kingdom())) {
            return 0;
        }
        credit(key, amount, CityLedger.OTHER, day);
        changed();
        return amount;
    }

    /**
     * Every purse of a kingdom goes into its treasury as far as it has room, written as money of the cities: a kingdom that is
     * dissolved or annexed.
     *
     * @return what moved
     */
    public long sweepPurses(UUID kingdom, long day) {
        Treasury treasury = treasuries.get(kingdom);
        if (treasury == null) {
            return 0;
        }
        long moved = 0;
        for (CityPurses.PurseKey key : purses.keysOf(kingdom)) {
            long amount = purses.balance(key);
            if (amount > 0 && treasury.canReceive(amount)) {
                debit(key, amount, CityLedger.OTHER, day);
                treasury.receive(amount, LedgerCategory.CITIES, day);
                moved += amount;
            }
        }
        if (moved > 0) {
            changed();
        }
        return moved;
    }

    /**
     * Purses of a kingdom go to a winner of a war, into its reserves of the currency of the loser; what the winner cannot take
     * goes into the treasury of the loser.
     *
     * @return what the winner took
     */
    public long seizePurses(List<CityPurses.PurseKey> keys, UUID winner, long day) {
        Treasury to = treasuries.get(winner);
        long seized = 0;
        for (CityPurses.PurseKey key : keys) {
            long amount = purses.balance(key);
            if (amount <= 0) {
                continue;
            }
            Treasury loser = treasuries.get(key.kingdom());
            if (to != null && !winner.equals(key.kingdom()) && to.canReceiveReserve(key.kingdom(), amount)) {
                debit(key, amount, CityLedger.OTHER, day);
                to.receiveReserve(key.kingdom(), amount);
                long worth = ExchangeRates.convert(amount, rate(key.kingdom()), rate(winner), 0);
                if (worth > 0) {
                    to.journal().record(day, LedgerCategory.WAR, worth, 0);
                }
                seized += amount;
            } else if (loser != null && loser.canReceive(amount)) {
                debit(key, amount, CityLedger.OTHER, day);
                loser.receive(amount, LedgerCategory.CITIES, day);
            }
        }
        changed();
        return seized;
    }

    /**
     * The crown grants a city money from the treasury of the kingdom; written as a transfer for the crown, as a grant for the
     * city, which spends it before its own.
     */
    public Outcome cityGrant(UUID kingdom, long city, long amount, long day) {
        if (!Money.isPayment(amount)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        return treasuryToPurse(CityPurses.PurseKey.cityTreasury(kingdom, city), amount, LedgerCategory.TRANSFERS, CityLedger.GRANT, day);
    }

    /**
     * What the crown took from a city.
     *
     * @param taken  all it took
     * @param beyond of that, what was not granted to the city before: its people resent it
     */
    public record Recall(Outcome outcome, long taken, long beyond) {
    }

    /**
     * The crown takes money of a city into the treasury of the kingdom, no more than it holds besides what its head promised
     * its constructions; what was granted and not spent goes first.
     */
    public Recall cityRecall(UUID kingdom, long city, long amount, long day) {
        if (!Money.isPayment(amount)) {
            return new Recall(Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE), 0, 0);
        }
        CityTreasury treasury = openCityTreasury(kingdom, city);
        CityPurses.PurseKey key = treasury.key();
        long free = Math.max(0, purses.balance(key) - treasury.committed());
        if (amount > free) {
            return new Recall(Outcome.fail(KingdomError.NOT_ENOUGH_MONEY), 0, 0);
        }
        long granted = Math.min(treasury.granted(), amount);
        long beyond = amount - granted;
        long grantedBefore = treasury.granted();
        Outcome moved = purseToTreasury(key, amount, LedgerCategory.TRANSFERS, CityLedger.RECALL, day);
        if (!moved.ok()) {
            return new Recall(moved, 0, 0);
        }
        treasury.setGranted(Math.min(Math.max(0, grantedBefore - granted), purses.balance(key)));
        if (beyond > 0) {
            treasury.setRecalledUntil(day + CityTreasury.RECALL_DAYS - 1);
        }
        changed();
        return new Recall(Outcome.DONE, amount, beyond);
    }

    /**
     * What the head of a city promises a construction, or gives back; never more than its treasury holds.
     *
     * @return whether it could
     */
    public boolean cityCommit(UUID kingdom, long city, long delta) {
        CityTreasury treasury = openCityTreasury(kingdom, city);
        long next = treasury.committed() + delta;
        if (next < 0 || next > purses.balance(treasury.key())) {
            return false;
        }
        treasury.setCommitted(next);
        changed();
        return true;
    }

    /**
     * Sets the taxes and the share of the crown of a city, in thousandths; {@link CityTreasury#AS_KINGDOM} follows the kingdom.
     */
    public Outcome setCityTaxes(UUID kingdom, long city, int salesTax, int tithe, int crownShare) {
        if (!treasuries.containsKey(kingdom)) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (!inRange(salesTax, CityEconomyRules.MAX_SALES_TAX_PERCENT, true) || !inRange(tithe, CityEconomyRules.MAX_TITHE_PERCENT, true)
                || !inRange(crownShare, CityEconomyRules.MAX_CROWN_SHARE_PERCENT, true)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        CityTreasury treasury = openCityTreasury(kingdom, city);
        if (treasury.salesTaxSetting() == salesTax && treasury.titheSetting() == tithe && treasury.crownShareSetting() == crownShare) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        treasury.setTaxes(salesTax, tithe, crownShare);
        changed();
        return Outcome.DONE;
    }

    /**
     * Sets the taxes and the share of the crown every city of a kingdom follows unless set for itself, in thousandths.
     */
    public Outcome setKingdomCityTaxes(UUID kingdom, int salesTax, int tithe, int crownShare) {
        Treasury treasury = treasuries.get(kingdom);
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (!inRange(salesTax, CityEconomyRules.MAX_SALES_TAX_PERCENT, false) || !inRange(tithe, CityEconomyRules.MAX_TITHE_PERCENT, false)
                || !inRange(crownShare, CityEconomyRules.MAX_CROWN_SHARE_PERCENT, false)) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (treasury.citySalesTaxSetting() == salesTax && treasury.cityTitheSetting() == tithe && treasury.crownShareSetting() == crownShare) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        treasury.setCityTaxes(salesTax, tithe, crownShare);
        // A city set above the kingdom's new taxes keeps no more than them.
        capCityTaxes(kingdom, salesTax, tithe);
        changed();
        return Outcome.DONE;
    }

    private static boolean inRange(int permille, int maxPercent, boolean asKingdom) {
        return (asKingdom && permille == CityTreasury.AS_KINGDOM) || (permille >= 0 && permille <= maxPercent * 10);
    }

    /**
     * Lowers the taxes of the cities of a kingdom set above the given ones down to them.
     */
    public void capCityTaxes(UUID kingdom, int salesTax, int tithe) {
        boolean changed = false;
        for (CityTreasury city : cityTreasuriesOf(kingdom)) {
            changed |= city.capTaxes(salesTax, tithe);
        }
        if (changed) {
            changed();
        }
    }

    /**
     * The tally of the day of a city, for the lines the books do not write themselves (taverns, exports, goods).
     */
    public CityStatement.Tally cityTally(UUID kingdom, long city) {
        return openCityTreasury(kingdom, city).tally();
    }

    /**
     * Ends the day of a city: what it wrote becomes its statement.
     */
    public CityStatement sealCity(UUID kingdom, long city, long day) {
        CityTreasury treasury = openCityTreasury(kingdom, city);
        CityStatement statement = treasury.seal(day, purses.balance(treasury.key()));
        changed();
        return statement;
    }

    /**
     * Restores a purse read from saved data.
     */
    public void restorePurse(CityPurses.PurseKey key, long amount) {
        purses.restore(key, amount);
    }

    /**
     * Money of a purse of a kind this version does not know goes into the treasury of its kingdom, as far as it has room.
     */
    public void restoreUnknownPurse(UUID kingdom, long amount) {
        Treasury treasury = treasuries.get(kingdom);
        if (treasury != null && Money.isAmount(amount) && treasury.canReceive(amount)) {
            treasury.receive(amount, LedgerCategory.CITIES, today);
        }
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

    /**
     * Restores the treasury of a city read from saved data.
     */
    public void restoreCityTreasury(CityTreasury city) {
        cityTreasuries.put(cityKey(city.kingdomId(), city.cityId()), city);
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
