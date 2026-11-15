package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.npc.RulerDecision.Kind;
import io.github.sekerator.kingdomworld.core.npc.RulerMemory.Need;
import io.github.sekerator.kingdomworld.core.npc.RulerMemory.NeedKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * How the ruler of a kingdom of the world decides: from a snapshot of its kingdom to a few decisions a turn, the answers to the
 * deals offered to it and what its kingdom needs. A ruler plays by the rules of a king of players: money comes only from
 * taxes, trade and the market, buildings from materials and builders. A poor ruler cuts its army and its building, raises
 * taxes and sells what it can spare; nothing makes money for it.
 */
public final class RulerMind {
    public static final String HOUSE = "house";
    public static final String WAREHOUSE = "warehouse";
    public static final String MAGE_TOWER = "mage_tower";
    public static final String BREAD = "minecraft:bread";
    /** The honorary title a ruler gives a subject of good name. */
    public static final String COUNCILLOR = "councillor";
    /** The reputation a subject needs to become an honorary councillor. */
    public static final int COUNCILLOR_REPUTATION = 50;
    /** Robbers further than this from the roads and the capital of a kingdom are no threat to it, in blocks. */
    public static final int THREAT_DISTANCE = 320;
    /** Buildings a ruler builds or raises at once at most, so that its builders finish what they start. */
    public static final int MAX_BUILDING = 2;
    /** The sales tax of a ruler of no particular leaning, in thousandths. */
    public static final int BASE_SALES_TAX = 50;
    /** Reasons for declining a deal. */
    public static final String NO_GOODS = "no_goods";
    public static final String NEEDED = "needed";
    public static final String PRICE_LOW = "price_low";
    public static final String PRICE_HIGH = "price_high";
    public static final String NO_MONEY = "no_money";
    public static final String NOT_NEEDED = "not_needed";
    public static final String DISTRUST = "distrust";
    public static final List<String> REASONS = List.of(NO_GOODS, NEEDED, PRICE_LOW, PRICE_HIGH, NO_MONEY, NOT_NEEDED, DISTRUST);
    /** Weapons and tools the workshops keep for the army and the workers. */
    public static final List<String> WEAPONS = List.of("minecraft:iron_sword", "minecraft:bow", "minecraft:arrow");
    public static final List<String> TOOLS = List.of("minecraft:iron_pickaxe", "minecraft:iron_axe", "minecraft:iron_hoe");
    /** Plain tools of stone the smiths make of cobblestone and sticks, one in store for every two workers who wear them out. */
    public static final List<String> STONE_TOOLS = List.of("minecraft:stone_pickaxe", "minecraft:stone_axe");
    /** Days of food a ruler wants in its storehouses: bread is ordered up to that, not only when the stores run low. */
    public static final int FOOD_DAYS = 5;

    /**
     * What a ruler decided in a turn.
     *
     * @param decisions what it does, the most pressing first, no more than the rules allow
     * @param answers   its answers to the deals offered to it, which do not count as decisions
     * @param needs     what its kingdom lacks and what it saves for
     */
    public record Plan(List<RulerDecision> decisions, List<RulerDecision> answers, List<Need> needs) {
        public Plan {
            decisions = List.copyOf(decisions);
            answers = List.copyOf(answers);
            needs = List.copyOf(needs);
        }
    }

    /**
     * Whether a ruler takes a deal, and why not.
     */
    public record Verdict(boolean accept, String reason) {
    }

    /**
     * How a ruler feels about a player.
     */
    public enum Attitude {
        HOSTILE,
        COLD,
        NEUTRAL,
        WARM,
        FRIENDLY;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Attitude of(int value) {
            if (value <= -50) {
                return HOSTILE;
            }
            if (value <= -15) {
                return COLD;
            }
            if (value < 15) {
                return NEUTRAL;
            }
            return value < 50 ? WARM : FRIENDLY;
        }
    }

    /**
     * A candidate decision with its score and what it costs the treasury now, so that the decisions of one turn never spend
     * together more than the ruler may spare.
     */
    private record Scored(int score, RulerDecision decision, long cost) {
        Scored(int score, RulerDecision decision) {
            this(score, decision, 0);
        }
    }

    /**
     * What goods of a worth cost when bought abroad.
     */
    public static long importCost(RulerView view, long worth) {
        return (long) Math.min(Long.MAX_VALUE / 4, Math.max(0, worth) * (double) Math.max(1000, view.importPermille) / 1000.0);
    }

    private RulerMind() {
    }

    // ---------------------------------------------------------------- temper

    /**
     * How greedy a ruler is: thrifty, ambitious and proud rulers take more, generous and just ones less.
     */
    public static int greed(List<RulerCharacter.Trait> traits) {
        int greed = 0;
        for (RulerCharacter.Trait trait : traits) {
            switch (trait) {
                case THRIFTY, AMBITIOUS, PROUD -> greed++;
                case GENEROUS, JUST -> greed--;
                default -> {
                }
            }
        }
        return greed;
    }

    /** Days of upkeep below which a ruler counts itself poor. */
    public static final int POOR_DAYS = 3;
    /** Days of upkeep a poor ruler waits for before it counts itself well again, so that hard times do not flicker. */
    public static final int WELL_DAYS = 6;
    /** Days of the soldiers' pay a ruler keeps aside for every war it fights, so that sieges do not melt its reserve. */
    public static final int WAR_CHEST_DAYS = 10;
    /** Days of upkeep from which a ruler counts itself rich: it builds and keeps more than the others. */
    public static final int RICH_DAYS = 365;

    /**
     * What a day costs the kingdom whatever its ruler decides: its upkeep, or its expenses when the upkeep is not known.
     */
    public static long dayCost(RulerView view) {
        return Math.max(1, view.upkeep > 0 ? view.upkeep : view.expenses);
    }

    /**
     * For how many days of its upkeep the treasury pays.
     */
    public static long keepDays(RulerView view) {
        return Math.max(0, view.balance) / dayCost(view);
    }

    /**
     * For how many days the treasury lasts at the pace of the last week, or {@link Long#MAX_VALUE} while it earns more than it
     * spends.
     */
    public static long runway(RulerView view) {
        long burn = view.expenses - view.income;
        return burn <= 0 ? Long.MAX_VALUE : Math.max(0, view.balance) / burn;
    }

    /**
     * Whether the treasury pays a year of upkeep: such a ruler builds, keeps soldiers and invests beyond the needs of the day.
     */
    public static boolean rich(RulerView view) {
        return keepDays(view) >= RICH_DAYS;
    }

    /**
     * The money a ruler keeps for hard times: some days of upkeep, more for a thrifty ruler and less for a bold one, and the
     * pay of its soldiers for some days for every war it fights.
     */
    public static long reserve(RulerView view, RulerRules rules) {
        double reserve = Math.max(100, dayCost(view) * (double) rules.reserveDays());
        if (view.has(RulerCharacter.Trait.THRIFTY) || view.has(RulerCharacter.Trait.CAUTIOUS)) {
            reserve *= 1.5;
        }
        if (view.has(RulerCharacter.Trait.BOLD) || view.has(RulerCharacter.Trait.GENEROUS)) {
            reserve *= 0.75;
        }
        reserve += (double) Math.max(0, view.wars) * Math.max(0, view.armyPayroll) * WAR_CHEST_DAYS;
        return (long) Math.min(Long.MAX_VALUE / 4, reserve);
    }

    /**
     * Whether the treasury is too poor: it pays no more than a few days of upkeep, or it is below the reserve and loses money.
     * A ruler that was poor counts itself well again only with twice the days and a quarter above the reserve, so that its hard
     * times do not start and end every other day.
     */
    public static boolean poor(RulerView view, RulerRules rules) {
        long days = keepDays(view);
        boolean losing = view.income < view.expenses;
        long reserve = reserve(view, rules);
        if (view.wasPoor) {
            return days < WELL_DAYS || (losing && view.balance < reserve + reserve / 4);
        }
        return days < POOR_DAYS || (losing && view.balance < reserve);
    }

    /**
     * The money a ruler may spend beyond its reserve and what it promised already.
     */
    public static long spare(RulerView view, RulerRules rules) {
        return Math.max(0, view.balance - reserve(view, rules) - Math.max(0, view.committed));
    }

    /**
     * What a ruler spends on goods from abroad in a day at most: half what it may spare, and of that no more than three days
     * of upkeep or a sixtieth of what it may spare, whichever is more. A rich ruler buys what its builders lack without
     * pouring its treasury abroad in a few days.
     */
    public static long importBudget(RulerView view, RulerRules rules) {
        long spare = spare(view, rules);
        long budget = Math.max(dayCost(view) * 3, spare / 60);
        // Builders who stand still for want of materials are the dearest waste: the ruler buys for them twice as much.
        if (view.stalled > 0) {
            budget *= 2;
        }
        return Math.min(spare / 2, budget);
    }

    /** Days a treasury that loses money has left before its ruler raises taxes, long before it is poor. */
    public static final int TAX_RUNWAY_DAYS = 60;

    /**
     * How a ruler leans its taxes on its treasury: a quarter more when it is poor, some more when it loses money and has two
     * months left, a tenth less when it is rich and wants trade more than coins.
     */
    private static double purse(RulerView view, RulerRules rules) {
        if (poor(view, rules)) {
            return 0.25;
        }
        if (runway(view) < TAX_RUNWAY_DAYS) {
            return 0.15;
        }
        return rich(view) ? -0.1 : 0;
    }

    /**
     * How many buildings of a role of the table a ruler wants: a rich ruler a workshop, a market, a storehouse and a well more
     * than its level asks, for the trade and the work they bring.
     */
    public static int wanted(RulerView view, RulerRules.RoleWant want) {
        int count = want.count(view.cityLevel);
        if (rich(view) && INVESTMENTS.contains(want.role())) {
            count++;
        }
        return count;
    }

    /** Roles a rich ruler builds one more of than its level asks. */
    public static final List<String> INVESTMENTS = List.of("workshop", "market", WAREHOUSE, "well");

    /**
     * Buildings a ruler builds or raises at once: one more for a rich ruler.
     */
    public static int buildingSlots(RulerView view) {
        return Math.min(view.maxConstructions, MAX_BUILDING + (rich(view) ? 1 : 0));
    }

    /**
     * The poll tax a ruler wants: the usual one, higher for a greedy ruler and in hard times, lower for a just or generous one.
     */
    public static long pollTax(RulerView view, RulerRules rules) {
        double factor = Math.max(0.25, 1 + 0.35 * greed(view.traits));
        factor += purse(view, rules);
        return Math.max(0, Math.min(view.maxPollTax, Math.round(view.defaultPollTax * factor)));
    }

    /**
     * The sales tax a ruler wants on the lots of players, in thousandths.
     */
    public static int salesTax(RulerView view, RulerRules rules) {
        double factor = Math.max(0.25, 1 + 0.35 * greed(view.traits));
        if (view.character == RulerCharacter.MERCHANT) {
            factor *= 0.8;
        }
        factor += purse(view, rules);
        return (int) Math.max(0, Math.min(view.maxSalesTaxPermille, Math.round(BASE_SALES_TAX * factor)));
    }

    /** The most tithe a ruler takes, in thousandths. */
    public static final int MAX_TITHE = 500;

    /**
     * The tithe a ruler wants on what the shopkeepers of its kingdom earn, in thousandths: as with the sales tax, the greedier
     * the more, a quarter more when its treasury is poor, from nothing up to a half.
     */
    public static int tithe(RulerView view, RulerRules rules) {
        double factor = Math.max(0.25, 1 + 0.35 * greed(view.traits));
        factor += purse(view, rules);
        return (int) Math.max(0, Math.min(MAX_TITHE, Math.round(view.baseTithePercent * 10 * factor)));
    }

    /**
     * A ruler of a kingdom with shops of citizens sets the tithe it wants once it is two points off the one it has; a modest
     * decision. Without shops it never comes up.
     */
    private static void tithe(RulerView view, RulerRules rules, List<Scored> out) {
        if (view.shops <= 0) {
            return;
        }
        int wanted = tithe(view, rules);
        if (Math.abs(wanted - view.tithePermille) >= 20) {
            out.add(new Scored(30, RulerDecision.of(Kind.TITHE, "", wanted)));
        }
    }

    /**
     * Whether robbers or murderers threaten the kingdom.
     */
    public static boolean threatened(RulerView view) {
        if (view.murderers > 0 || view.threat > 0) {
            return true;
        }
        for (RulerView.Camp camp : view.camps) {
            if (camp.distance() <= THREAT_DISTANCE) {
                return true;
            }
        }
        return false;
    }

    /**
     * The soldiers a ruler wants: a share of its citizens by its leaning, more under threat, no more than its income pays.
     */
    public static int soldiersWanted(RulerView view, RulerRules rules) {
        if (view.population < 4) {
            return Math.min(view.soldiers, 1);
        }
        double factor = switch (view.character) {
            case WARRIOR -> 2.0;
            case MERCHANT -> 0.8;
            default -> 1.0;
        };
        if (view.has(RulerCharacter.Trait.CAUTIOUS)) {
            factor *= 1.25;
        }
        boolean threat = threatened(view);
        if (threat) {
            factor *= 1.5;
        }
        int wanted = (int) Math.round(view.population * rules.soldiersPermille() / 1000.0 * factor);
        wanted = Math.max(threat ? 2 : 1, wanted);
        double share = view.character == RulerCharacter.WARRIOR ? 0.5 : 0.35;
        long wage = Math.max(1, view.soldierWage);
        int paid = (int) Math.max(1, Math.floor(Math.max(view.income, view.expenses) * share / wage));
        return Math.min(wanted, Math.max(paid, Math.min(view.soldiers, wanted)));
    }

    /**
     * What the entry fee of a large city should be: the fee of the character, a quarter more under threat and a fifth less
     * when the citizens are unhappy.
     */
    public static long entryFee(RulerView view, RulerRules rules) {
        long base = NpcLaws.of(view.character, view.traits, view.maxEntryFee, view.maxBounty).entryFee();
        double factor = 1;
        if (threatened(view)) {
            factor *= 1.25;
        }
        if (view.mood < rules.festivalMood()) {
            factor *= 0.8;
        }
        return Math.max(0, Math.min(view.maxEntryFee, Math.round(base * factor)));
    }

    /**
     * What the reward for the head of a wanted player should be: twice the usual one while murderers are wanted, no more than
     * the treasury can spare.
     */
    public static long bounty(RulerView view, RulerRules rules) {
        long base = NpcLaws.of(view.character, view.traits, view.maxEntryFee, view.maxBounty).bounty();
        // Murderers double the reward; its people killed in its land lately treble it.
        long wanted = view.killed >= 2 ? base * 3 : view.murderers > 0 ? base * 2 : base;
        long spare = spare(view, rules);
        if (wanted > base && wanted > spare) {
            wanted = Math.max(base, spare);
        }
        return Math.max(0, Math.min(view.maxBounty, wanted));
    }

    /**
     * How a ruler feels about a player, from the player's name under its law and their dealings.
     *
     * @param reputation the reputation of the player in the kingdom, from -100 to 100
     * @param dealings   how the deals with the player went, from -100 to 100
     */
    public static int attitude(int reputation, int dealings, List<RulerCharacter.Trait> traits) {
        double value = reputation * 0.7 + dealings * 0.6;
        if (traits.contains(RulerCharacter.Trait.PROUD)) {
            value -= 10;
        }
        if (traits.contains(RulerCharacter.Trait.GENEROUS)) {
            value += 10;
        }
        return (int) Math.max(-RulerMemory.MAX_RELATION, Math.min(RulerMemory.MAX_RELATION, Math.round(value)));
    }

    /**
     * Whether a ruler lets a road of another kingdom pass over its land: merchants gladly, warriors only for friends.
     *
     * @param relation how the ruler stands with that kingdom
     */
    public static boolean letsPass(RulerCharacter character, List<RulerCharacter.Trait> traits, int relation) {
        int needed = switch (character) {
            case MERCHANT -> -40;
            case BUILDER -> -10;
            case MAGE -> 0;
            case WARRIOR -> 15;
        };
        for (RulerCharacter.Trait trait : traits) {
            switch (trait) {
                case GENEROUS -> needed -= 15;
                case JUST, BOLD -> needed -= 5;
                case PROUD -> needed += 15;
                case CAUTIOUS -> needed += 10;
                default -> {
                }
            }
        }
        return relation >= needed;
    }

    /**
     * How far from the worth of goods a ruler goes in a deal, in thousandths: a merchant bargains hard, a generous ruler
     * gives way.
     */
    public static int slack(RulerView view, RulerRules rules) {
        double slack = rules.slackPermille();
        if (view.character == RulerCharacter.MERCHANT) {
            slack *= 0.6;
        }
        if (view.has(RulerCharacter.Trait.GENEROUS)) {
            slack *= 2;
        }
        if (view.has(RulerCharacter.Trait.THRIFTY) || view.has(RulerCharacter.Trait.PROUD)) {
            slack *= 0.5;
        }
        return (int) Math.round(slack);
    }

    /**
     * The price a ruler asks for goods it sells, on top of their worth, in thousandths.
     */
    public static int markup(RulerView view, RulerRules rules) {
        int markup = view.character == RulerCharacter.MERCHANT ? 80 : 40;
        if (view.has(RulerCharacter.Trait.GENEROUS) || view.has(RulerCharacter.Trait.JUST)) {
            markup /= 2;
        }
        return Math.min(markup, Math.max(0, rules.slackPermille() / 2));
    }

    /**
     * Whether a ruler takes a deal offered to it: a fair price for goods it can spare or needs and can pay for is taken, a
     * robbing one is declined with the reason. A price is fair against what the ruler would do otherwise: a sale that pays
     * more than its merchants get abroad for a surplus, a purchase of something it lacks that costs less than buying it
     * abroad. A regular deal is weighed by what it takes in a week: the goods it drains and the money it costs a day.
     */
    public static Verdict judge(RulerView.Offer offer, RulerView view, RulerMemory memory, RulerRules rules) {
        if (memory.relation(offer.partner()) <= -60) {
            return new Verdict(false, DISTRUST);
        }
        double slack = slack(view, rules) / 1000.0;
        long worth = Math.max(1, offer.worth());
        RulerView.Good good = view.goods.get(offer.item());
        if (offer.weSell()) {
            if (offer.free() < offer.count()) {
                return new Verdict(false, NO_GOODS);
            }
            // A regular deal takes its goods every few days: what a week of it takes must leave the kingdom what it wants.
            long drained = offer.count() * offer.weekly();
            if (good != null && good.stock() - drained < good.want()) {
                return new Verdict(false, NEEDED);
            }
            boolean surplus = good != null && good.surplus() >= offer.count();
            boolean fair = offer.price() >= worth * (1 - slack);
            boolean betterThanAbroad = surplus && offer.price() * 1000.0 >= worth * (double) view.exportPermille;
            if (!fair && !betterThanAbroad) {
                return new Verdict(false, PRICE_LOW);
            }
            return new Verdict(true, "");
        }
        boolean needed = offer.need() > 0;
        boolean fair = offer.price() <= worth * (1 + slack);
        boolean cheaperThanAbroad = needed && offer.price() * 1000.0 <= worth * (double) view.importPermille;
        if (!fair && !cheaperThanAbroad) {
            return new Verdict(false, PRICE_HIGH);
        }
        long spare = spare(view, rules);
        if (offer.price() > spare) {
            return new Verdict(false, NO_MONEY);
        }
        if (offer.periodDays() > 0) {
            // A regular purchase is paid every few days: no more a day than the kingdom earns beyond its upkeep and a sixtieth
            // of what it may spare.
            long daily = offer.price() / Math.max(1, offer.periodDays());
            long room = Math.max(0, view.income - dayCost(view)) + spare / 60;
            if (daily > room) {
                return new Verdict(false, NO_MONEY);
            }
        }
        if (!needed) {
            // Goods it does not need only for a merchant, and only cheap enough to sell again at home.
            if (view.character != RulerCharacter.MERCHANT || offer.price() > worth * (1 - slack / 2)) {
                return new Verdict(false, NOT_NEEDED);
            }
        }
        return new Verdict(true, "");
    }

    /**
     * The answers to all the deals offered, one after another: each is judged by what the deals taken before it left of the
     * money and the goods, the sales first, as they bring money in.
     */
    public static List<RulerDecision> judgeAll(RulerView view, RulerMemory memory, RulerRules rules) {
        RulerView left = view.copy();
        List<RulerView.Offer> offers = new ArrayList<>(view.offers);
        offers.sort(Comparator.comparing((RulerView.Offer offer) -> !offer.weSell()).thenComparing(RulerView.Offer::need, Comparator.reverseOrder()));
        List<RulerDecision> answers = new ArrayList<>();
        for (RulerView.Offer offer : offers) {
            Verdict verdict = judge(offer, left, memory, rules);
            if (verdict.accept()) {
                RulerView.Good good = left.goods.get(offer.item());
                if (offer.weSell()) {
                    left.balance += offer.price();
                    if (good != null) {
                        left.goods.put(offer.item(), new RulerView.Good(good.item(), Math.max(0, good.stock() - offer.count() * offer.weekly()),
                                good.want(), good.worth()));
                    }
                } else {
                    left.balance -= offer.price();
                    if (offer.periodDays() > 0) {
                        // What it pays every few days from now on is upkeep too.
                        left.upkeep = dayCost(left) + offer.price() / Math.max(1, offer.periodDays());
                    }
                    if (good != null) {
                        left.goods.put(offer.item(), new RulerView.Good(good.item(), good.stock() + offer.count(), good.want(), good.worth()));
                    }
                }
            }
            answers.add(new RulerDecision(verdict.accept() ? Kind.ACCEPT : Kind.DECLINE, verdict.reason(), offer.count(), offer.deal(),
                    offer.partner().toString(), offer.partnerName()));
        }
        return answers;
    }

    /**
     * Whether a regular deal under way still pays: the same test as when it was offered, with today's money and stock, the
     * goods it took already back in the count.
     */
    public static boolean keeps(RulerView.Offer deal, RulerView view, RulerMemory memory, RulerRules rules) {
        if (deal.periodDays() <= 0) {
            return true;
        }
        Verdict verdict = judge(deal, view, memory, rules);
        return verdict.accept() || DISTRUST.equals(verdict.reason()) || NO_GOODS.equals(verdict.reason());
    }

    /**
     * The roles a ruler would lay out a building of in this turn: a house when the beds are nearly taken, a storehouse when the
     * stores are full and the first role of the table its kingdom lacks. Only for these are places looked for.
     */
    public static List<String> wantedRoles(RulerView view, RulerRules rules) {
        List<String> roles = new ArrayList<>();
        if (view.bedsTotal - view.bedsUsed <= rules.freeBeds() && view.planned(HOUSE) == 0) {
            roles.add(HOUSE);
        }
        if (view.storageSlots > 0 && view.storageUsed * 100L >= (long) view.storageSlots * rules.storageFullPercent() && view.planned(WAREHOUSE) == 0) {
            roles.add(WAREHOUSE);
        }
        for (RulerRules.RoleWant want : rules.roles()) {
            if (view.knownRoles.contains(want.role()) && view.built(want.role()) + view.planned(want.role()) < wanted(view, want)) {
                if (!roles.contains(want.role())) {
                    roles.add(want.role());
                }
                break;
            }
        }
        return roles;
    }

    // ---------------------------------------------------------------- the turn

    /**
     * The decisions of a turn, the answers to the deals offered and the needs of the kingdom.
     */
    public static Plan decide(RulerView view, RulerMemory memory, RulerRules rules) {
        List<RulerDecision> answers = judgeAll(view, memory, rules);
        List<Scored> candidates = new ArrayList<>();
        List<Need> needs = new ArrayList<>();
        boolean poor = poor(view, rules);
        long reserve = reserve(view, rules);
        long spare = spare(view, rules);
        boolean threat = threatened(view);
        // A kingdom with a garrison keeps it: its size is set by the size of the kingdom, and its staff fills the ranks.
        int soldiers = view.garrison ? view.soldiers : soldiersWanted(view, rules);
        if (poor) {
            hardTimes(view, rules, candidates, soldiers);
            needs.add(new Need(NeedKind.MONEY, "", Math.max(0, reserve - view.balance)));
        } else {
            growth(view, memory, rules, candidates, needs, spare);
            army(view, rules, candidates, needs, spare, threat, soldiers);
            trade(view, memory, rules, candidates, spare);
            household(view, memory, rules, candidates, spare, threat);
        }
        goods(view, needs);
        tithe(view, rules, candidates);
        List<RulerDecision> decisions = pick(candidates, rules.decisionsPerTurn(), poor ? 0 : spare);
        return new Plan(decisions, answers, trimNeeds(needs));
    }

    /**
     * A poor ruler lets soldiers go, stops its building, raises taxes, sells its surplus and pays the usual wages again.
     */
    private static void hardTimes(RulerView view, RulerRules rules, List<Scored> out, int soldiers) {
        int keep = threatened(view) ? Math.min(view.soldiers, 2) : Math.min(view.soldiers, 1);
        keep = Math.min(keep, soldiers);
        if (view.soldiers > keep && !view.garrison) {
            out.add(new Scored(100, RulerDecision.of(Kind.DISMISS, "", Math.max(1, (view.soldiers - keep + 1) / 2))));
        }
        if (view.constructions > view.paused || !view.buying.isEmpty()) {
            out.add(new Scored(95, RulerDecision.of(Kind.PAUSE, "", view.constructions)));
        }
        long tax = pollTax(view, rules);
        int sales = salesTax(view, rules);
        if (tax > view.pollTax && view.subjects > 0 || sales > view.salesTaxPermille) {
            out.add(new Scored(80, RulerDecision.of(Kind.TAX, "", Math.max(tax, view.pollTax)).withExtra(Math.max(sales, view.salesTaxPermille))));
        }
        surplus(view).ifPresent(good -> out.add(new Scored(70, RulerDecision.of(Kind.SELL, good.item(), good.surplus()))));
        for (RulerView.Neighbour neighbour : view.neighbours) {
            Optional<RulerView.Good> sold = wantedBy(view, neighbour);
            if (sold.isPresent() && neighbour.deals() == 0 && view.openDeals < view.maxDeals) {
                out.add(new Scored(65, offer(view, rules, neighbour, sold.get(), true)));
                break;
            }
        }
        if (view.wagePercent > 100) {
            out.add(new Scored(60, RulerDecision.of(Kind.WAGE, "", 100)));
        }
    }

    /**
     * Houses when the beds fill up, storehouses when the stores do, the buildings its level wants, repairs, upgrades, land and
     * towns.
     */
    private static void growth(RulerView view, RulerMemory memory, RulerRules rules, List<Scored> out, List<Need> needs, long spare) {
        int builder = builderBonus(view.character);
        boolean landWanted = false;
        int slots = buildingSlots(view);
        boolean room = view.constructions < slots;
        if (view.bedsTotal - view.bedsUsed <= rules.freeBeds() && view.planned(HOUSE) == 0) {
            landWanted |= build(view, HOUSE, 85 + builder, spare, room, out, needs);
        }
        if (view.storageSlots > 0 && view.storageUsed * 100L >= (long) view.storageSlots * rules.storageFullPercent()
                && view.planned(WAREHOUSE) == 0) {
            landWanted |= build(view, WAREHOUSE, 80 + builder, spare, room, out, needs);
        }
        for (RulerRules.RoleWant want : rules.roles()) {
            if (!view.knownRoles.contains(want.role())) {
                continue;
            }
            if (view.built(want.role()) + view.planned(want.role()) < wanted(view, want)) {
                int bonus = builder + roleBonus(view.character, want.role());
                landWanted |= build(view, want.role(), 60 + bonus, spare, room, out, needs);
                break;
            }
        }
        if (!view.damaged.isEmpty()) {
            out.add(new Scored(70, new RulerDecision(Kind.REPAIR, "", view.damaged.size(), 0, Long.toString(view.damaged.get(0)), "")));
        }
        // An upgrade leaves a place for a new building the kingdom wants.
        if (view.constructions + (wantedRoles(view, rules).isEmpty() ? 1 : 2) <= slots) {
            for (RulerView.Option upgrade : view.upgrades) {
                long cost = importCost(view, upgrade.missing());
                if (cost <= spare / 2) {
                    out.add(new Scored(40 + builder, new RulerDecision(Kind.UPGRADE, upgrade.role(), 1, 0, upgrade.target(), upgrade.template()),
                            cost));
                    break;
                }
            }
        }
        boolean canClaim = view.chunks < view.maxChunks && view.landToClaim;
        if (canClaim && landWanted) {
            out.add(new Scored(75, RulerDecision.of(Kind.CLAIM, "", Math.min(rules.chunksPerClaim(), view.maxChunks - view.chunks))));
        } else if (canClaim && (view.has(RulerCharacter.Trait.AMBITIOUS) || view.character == RulerCharacter.BUILDER)
                && memory.turns() % 3 == 0) {
            out.add(new Scored(20 + builder, RulerDecision.of(Kind.CLAIM, "", Math.min(rules.chunksPerClaim(), view.maxChunks - view.chunks))));
        } else if (canClaim && rules.growthChunks() > 0) {
            // The land grows a little every turn up to its limit, so that the borders on the map move slowly outwards.
            out.add(new Scored(30 + builder, RulerDecision.of(Kind.CLAIM, "", Math.min(rules.growthChunks(), view.maxChunks - view.chunks))));
        }
        if (landWanted && !canClaim) {
            needs.add(new Need(NeedKind.LAND, "", 0));
        }
        // A town more for every so many citizens: a kingdom that appeared with its towns founds the next one only once it grew.
        if (view.population >= (long) rules.townPopulation() * (view.towns + 1) && view.towns < rules.maxTowns() && view.townSpot
                && spare >= Math.max(500, dayCost(view) * 10)) {
            int bonus = builder + (view.has(RulerCharacter.Trait.AMBITIOUS) ? 10 : 0);
            out.add(new Scored(45 + bonus, RulerDecision.of(Kind.TOWN, "", 1)));
        }
    }

    /**
     * How much a ruler of a character leans to a building of a role of the table beyond the others: a warrior to barracks and
     * towers, a merchant to markets and storehouses, a mage to the tower of mages. A builder leans to every building alike, by
     * {@link #builderBonus}.
     */
    public static int roleBonus(RulerCharacter character, String role) {
        return switch (role) {
            case "barracks", "tower" -> character == RulerCharacter.WARRIOR ? 15 : 0;
            case "market", WAREHOUSE -> character == RulerCharacter.MERCHANT ? 10 : 0;
            case MAGE_TOWER -> character == RulerCharacter.MAGE ? 20 : 0;
            default -> 0;
        };
    }

    /**
     * How much a ruler of a character leans to building at all: a builder more than the others.
     */
    public static int builderBonus(RulerCharacter character) {
        return character == RulerCharacter.BUILDER ? 10 : 0;
    }

    /**
     * Lays out a building of a role when there is a place and the money for what it lacks; otherwise saves for it.
     *
     * @return whether only a place is lacking
     */
    private static boolean build(RulerView view, String role, int score, long spare, boolean room, List<Scored> out, List<Need> needs) {
        Optional<RulerView.Option> option = view.builds.stream().filter(o -> o.role().equals(role)).findFirst();
        if (option.isEmpty()) {
            return false;
        }
        RulerView.Option chosen = option.get();
        if (!chosen.place()) {
            needs.add(new Need(NeedKind.BUILDING, role, chosen.missing()));
            return true;
        }
        long cost = importCost(view, chosen.missing());
        if (cost > spare || !room) {
            needs.add(new Need(NeedKind.BUILDING, role, chosen.missing()));
            return false;
        }
        out.add(new Scored(score, new RulerDecision(Kind.BUILD, role, 1, 0, chosen.template(), ""), cost));
        return false;
    }

    /**
     * Soldiers by income and threats, patrols on the roads, guards for the caravans and contracts on the camps of robbers.
     */
    private static void army(RulerView view, RulerRules rules, List<Scored> out, List<Need> needs, long spare, boolean threat, int wanted) {
        int warrior = view.character == RulerCharacter.WARRIOR ? 10 : 0;
        if (view.soldiers < wanted) {
            if (view.unemployed > 0 && spare >= view.soldierWage * 3) {
                int hire = Math.min(Math.min(wanted - view.soldiers, view.unemployed), 2);
                out.add(new Scored(65 + warrior + (threat ? 20 : 0), RulerDecision.of(Kind.HIRE, "", hire), view.soldierWage * 3 * hire));
            }
            if (threat) {
                needs.add(new Need(NeedKind.SOLDIERS, "", wanted - view.soldiers));
            }
        } else if (view.soldiers > Math.max(2, wanted * 2)) {
            out.add(new Scored(50, RulerDecision.of(Kind.DISMISS, "", Math.max(1, (view.soldiers - wanted) / 2))));
        }
        if (view.roads > 0 && view.soldiers >= 2 && view.patrols == 0
                && (threat || view.character == RulerCharacter.WARRIOR || view.has(RulerCharacter.Trait.CAUTIOUS))) {
            out.add(new Scored(55 + (threat ? 15 : 0), RulerDecision.of(Kind.PATROL, "", 1)));
        }
        if (view.openDeals > 0 && view.escorts == 0 && view.soldiers >= 4 && threat) {
            out.add(new Scored(45 + warrior, RulerDecision.of(Kind.GUARD, "", 1)));
        }
        view.camps.stream().filter(camp -> !camp.contracted() && camp.distance() <= THREAT_DISTANCE && camp.reward() <= spare)
                .min(Comparator.comparingInt(RulerView.Camp::distance))
                .ifPresent(camp -> {
                    int bonus = warrior + (view.has(RulerCharacter.Trait.BOLD) || view.has(RulerCharacter.Trait.CAUTIOUS) ? 10 : 0);
                    out.add(new Scored(60 + bonus, new RulerDecision(Kind.CONTRACT, "", camp.strength(), camp.reward(), camp.id(), ""),
                            camp.reward()));
                });
    }

    /**
     * Surplus to the market, goods it lacks bought from players, deals with neighbours and roads to them.
     */
    private static void trade(RulerView view, RulerMemory memory, RulerRules rules, List<Scored> out, long spare) {
        int merchant = view.character == RulerCharacter.MERCHANT ? 20 : 0;
        surplus(view).ifPresent(good -> out.add(new Scored(30 + merchant / 2 + 5, RulerDecision.of(Kind.SELL, good.item(), good.surplus()))));
        view.goods.values().stream()
                .filter(good -> good.shortage() > 0 && !view.buying.contains(good.item()) && good.shortage() * good.worth() <= spare)
                .max(Comparator.comparingLong(good -> good.shortage() * good.worth()))
                .ifPresent(good -> out.add(new Scored(50, RulerDecision.of(Kind.BUY, good.item(), good.shortage()), good.shortage() * good.worth())));
        if (view.openDeals < view.maxDeals) {
            for (RulerView.Neighbour neighbour : view.neighbours) {
                if (neighbour.deals() > 0 || memory.relation(neighbour.id()) < 0) {
                    continue;
                }
                Optional<RulerView.Good> sold = wantedBy(view, neighbour);
                if (sold.isPresent()) {
                    out.add(new Scored(40 + merchant, offer(view, rules, neighbour, sold.get(), true)));
                    break;
                }
                Optional<RulerView.Good> bought = view.goods.values().stream()
                        .filter(good -> good.shortage() > 0 && neighbour.spares().contains(good.item())
                                && good.shortage() * good.worth() <= spare)
                        .findFirst();
                if (bought.isPresent()) {
                    out.add(new Scored(40 + merchant, offer(view, rules, neighbour, bought.get(), false)));
                    break;
                }
            }
        }
        view.neighbours.stream().filter(n -> !n.road() && n.distance() <= rules.roadDistance() && memory.relation(n.id()) > -30
                        && !recent(memory, Kind.ROAD, n.id().toString(), view.day, 5))
                .min(Comparator.comparingInt(RulerView.Neighbour::distance))
                .ifPresent(n -> out.add(new Scored(35 + merchant + (view.character == RulerCharacter.BUILDER ? 10 : 0),
                        RulerDecision.of(Kind.ROAD, "", n.distance(), n.id().toString(), n.name()))));
    }

    /**
     * Orders for the workshops, the mint, taxes, wages, the law, feasts, the tower and positions.
     */
    private static void household(RulerView view, RulerMemory memory, RulerRules rules, List<Scored> out, long spare, boolean threat) {
        // Bread up to the days of food the ruler wants, from the wheat that heaps up in the storehouses, and for its settlements
        // that have nothing left to eat.
        RulerView.Good wheat = view.goods.get("minecraft:wheat");
        boolean heap = wheat != null && wheat.stock() > 4 * Math.max(1, wheat.want());
        boolean hungry = view.hungrySettlements > 0;
        if ((view.foodDays < FOOD_DAYS || heap || hungry) && view.makeable.contains(BREAD) && !view.ordered.contains(BREAD)) {
            long amount = want(view, BREAD, view.population * 3L) + (hungry ? (long) view.settlementPeople * 3 / Math.max(1, view.settlements)
                    * view.hungrySettlements : 0);
            out.add(new Scored(view.foodDays < 3 || hungry ? 92 : 58, RulerDecision.of(Kind.ORDER, BREAD, Math.max(1, amount))));
        }
        for (String item : STONE_TOOLS) {
            if (view.makeable.contains(item) && !view.ordered.contains(item) && shortage(view, item) > 0) {
                // Tools of stone of its own smith before buying them abroad: the workers wear them out every few days.
                out.add(new Scored(47, RulerDecision.of(Kind.ORDER, item, want(view, item, 1))));
                break;
            }
        }
        for (String item : WEAPONS) {
            if (view.makeable.contains(item) && !view.ordered.contains(item) && shortage(view, item) > 0) {
                out.add(new Scored(30 + (view.character == RulerCharacter.WARRIOR ? 10 : 0) + (threat ? 10 : 0),
                        RulerDecision.of(Kind.ORDER, item, want(view, item, 1))));
                break;
            }
        }
        for (String item : TOOLS) {
            if (view.makeable.contains(item) && !view.ordered.contains(item) && shortage(view, item) > 0) {
                // Before weapons in peace: woodcutters and miners whose tool wore out stand idle until the smith makes one.
                out.add(new Scored(45, RulerDecision.of(Kind.ORDER, item, want(view, item, 1))));
                break;
            }
        }
        if (view.mintIdle) {
            out.add(new Scored(35, RulerDecision.of(Kind.MINT, "", 1)));
        }
        long tax = pollTax(view, rules);
        int sales = salesTax(view, rules);
        if ((view.subjects > 0 && Math.abs(tax - view.pollTax) >= Math.max(1, tax / 10)) || Math.abs(sales - view.salesTaxPermille) >= 10) {
            out.add(new Scored(25, RulerDecision.of(Kind.TAX, "", view.subjects > 0 ? tax : view.pollTax).withExtra(sales)));
        }
        int wage = view.has(RulerCharacter.Trait.GENEROUS) && spare > view.expenses * 10 ? 125 : 100;
        if (wage != view.wagePercent) {
            out.add(new Scored(15, RulerDecision.of(Kind.WAGE, "", wage)));
        }
        if (view.largeCity) {
            long fee = entryFee(view, rules);
            if (Math.abs(fee - view.entryFee) > Math.max(1, view.entryFee / 5)) {
                out.add(new Scored(20, RulerDecision.of(Kind.ENTRY_FEE, "", fee)));
            }
        }
        long bounty = bounty(view, rules);
        if (Math.abs(bounty - view.bounty) > Math.max(1, view.bounty / 5)) {
            out.add(new Scored(20 + (view.murderers > 0 ? 25 : 0), RulerDecision.of(Kind.BOUNTY, "", bounty)));
        }
        if (view.festivals && view.mood < rules.festivalMood() && spare >= view.festivalCost) {
            out.add(new Scored(50, RulerDecision.of(Kind.FESTIVAL, "", view.festivalCost), view.festivalCost));
        }
        // Every ruler with a guild of mages has it research further; a mage cares for it more than the others.
        if (view.tower && !view.researching && !view.research.isEmpty()) {
            out.add(new Scored(view.character == RulerCharacter.MAGE ? 55 : 25, RulerDecision.of(Kind.RESEARCH, view.research, 1)));
        }
        // A subject of good name becomes an honorary councillor: an honour without any right in the kingdom.
        if (view.councillorFree) {
            view.candidates.stream().filter(candidate -> candidate.reputation() >= COUNCILLOR_REPUTATION)
                    .max(Comparator.comparingInt(RulerView.Candidate::reputation))
                    .ifPresent(candidate -> out.add(new Scored(10 + (view.has(RulerCharacter.Trait.GENEROUS) ? 10 : 0),
                            RulerDecision.of(Kind.APPOINT, COUNCILLOR, 1, candidate.player().toString(), candidate.name()))));
        }
    }

    // ---------------------------------------------------------------- helpers

    private static RulerDecision offer(RulerView view, RulerRules rules, RulerView.Neighbour neighbour, RulerView.Good good, boolean sell) {
        long count = sell ? Math.min(good.surplus(), 64) : Math.min(good.shortage(), 64);
        count = Math.max(1, count);
        double factor = sell ? 1 + markup(view, rules) / 1000.0 : 1 - markup(view, rules) / 2000.0;
        long price = Math.max(1, Math.round(good.worth() * count * factor));
        return new RulerDecision(Kind.OFFER, good.item(), sell ? count : -count, price, neighbour.id().toString(), neighbour.name());
    }

    /**
     * Whether the ruler decreed the same thing about the same target within some days: a road that is being searched for or
     * could not be found is not ordered again at once.
     */
    private static boolean recent(RulerMemory memory, Kind kind, String target, long day, int days) {
        for (RulerMemory.Decree decree : memory.decrees()) {
            if (decree.decision().kind() == kind && decree.decision().target().equals(target) && day - decree.day() < days) {
                return true;
            }
        }
        return false;
    }

    private static Optional<RulerView.Good> wantedBy(RulerView view, RulerView.Neighbour neighbour) {
        for (String item : neighbour.wants()) {
            RulerView.Good good = view.goods.get(item);
            if (good != null && good.surplus() > 0) {
                return Optional.of(good);
            }
        }
        if (!neighbour.npc()) {
            // A king of players is offered the biggest surplus, whether or not the market sells it already.
            return view.goods.values().stream().filter(good -> good.surplus() > 0)
                    .max(Comparator.comparingLong(good -> good.surplus() * Math.max(1, good.worth())));
        }
        return Optional.empty();
    }

    /**
     * The surplus its merchants do not sell abroad yet, the most worth first: a good on the market of the kingdom from its
     * founding is still a heap in the storehouses until its merchants take it abroad.
     */
    private static Optional<RulerView.Good> surplus(RulerView view) {
        return view.goods.values().stream().filter(good -> good.surplus() > 0 && !view.exporting.contains(good.item()) && !view.food.contains(good.item()))
                .max(Comparator.comparingLong(good -> good.surplus() * Math.max(1, good.worth())));
    }

    private static long shortage(RulerView view, String item) {
        RulerView.Good good = view.goods.get(item);
        return good == null ? 0 : good.shortage();
    }

    private static long want(RulerView view, String item, long fallback) {
        RulerView.Good good = view.goods.get(item);
        return Math.max(1, good == null || good.want() <= 0 ? fallback : good.want());
    }

    /**
     * Goods the kingdom lacks, the most valuable lack first.
     */
    private static void goods(RulerView view, List<Need> needs) {
        if (view.foodDays < FOOD_DAYS) {
            needs.add(0, new Need(NeedKind.FOOD, BREAD, shortage(view, BREAD)));
        }
        view.goods.values().stream().filter(good -> good.shortage() > 0 && !good.item().equals(BREAD))
                .sorted(Comparator.comparingLong((RulerView.Good good) -> good.shortage() * Math.max(1, good.worth())).reversed())
                .limit(3).forEach(good -> needs.add(new Need(NeedKind.GOODS, good.item(), good.shortage())));
    }

    private static List<Need> trimNeeds(List<Need> needs) {
        List<Need> result = new ArrayList<>();
        for (Need need : needs) {
            boolean known = result.stream().anyMatch(n -> n.kind() == need.kind() && n.subject().equals(need.subject()));
            if (!known && result.size() < RulerMemory.MAX_NEEDS) {
                result.add(need);
            }
        }
        return result;
    }

    /**
     * The most pressing decisions, one of a kind, no more than the limit; hiring and letting soldiers go never come together.
     */
    static List<RulerDecision> pick(List<Scored> candidates, int limit, long budget) {
        List<Scored> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingInt(Scored::score).reversed());
        Set<Kind> kinds = EnumSet.noneOf(Kind.class);
        List<RulerDecision> result = new ArrayList<>();
        long left = budget;
        for (Scored scored : sorted) {
            if (result.size() >= limit) {
                break;
            }
            Kind kind = scored.decision().kind();
            if (kinds.contains(kind) || (kind == Kind.HIRE && kinds.contains(Kind.DISMISS)) || (kind == Kind.DISMISS && kinds.contains(Kind.HIRE))) {
                continue;
            }
            // What the decisions taken before in this turn spend is not there for this one.
            if (scored.cost() > 0 && scored.cost() > left) {
                continue;
            }
            left -= Math.max(0, scored.cost());
            kinds.add(kind);
            result.add(scored.decision());
        }
        return result;
    }
}
