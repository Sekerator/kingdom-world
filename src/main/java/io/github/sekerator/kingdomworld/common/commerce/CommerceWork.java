package io.github.sekerator.kingdomworld.common.commerce;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.npc.NpcTexts;
import io.github.sekerator.kingdomworld.common.road.CityPeople;
import io.github.sekerator.kingdomworld.common.road.CityWalls;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.commerce.CityMeans;
import io.github.sekerator.kingdomworld.core.commerce.Evasion;
import io.github.sekerator.kingdomworld.core.commerce.TaxBurden;
import io.github.sekerator.kingdomworld.core.economy.CityEconomyRules;
import io.github.sekerator.kingdomworld.core.economy.CityLedger;
import io.github.sekerator.kingdomworld.core.economy.CityPurses;
import io.github.sekerator.kingdomworld.core.economy.CityStatement;
import io.github.sekerator.kingdomworld.core.economy.CityTreasury;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The days of the cities of the kingdoms of players. After the payday of a kingdom every one of its cities, the capital first,
 * lives its day in turn: its shops buy and sell, its treasury sends the crown its share and what it keeps beyond its reserve,
 * its purse lets go what it holds beyond the days it keeps, and the day becomes its statement.
 *
 * <p>The days run in the ticks after the payday, no longer than {@value #BUDGET_NANOS} nanoseconds a tick, so that a kingdom
 * with a hundred cities does not stop the server: a city whose day is not done goes on in the next tick.
 */
public final class CommerceWork {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final String KEY = "kingdomworld.treasury.cities.";
    /** Time the days of cities may take in one tick. */
    static final long BUDGET_NANOS = 1_000_000L;
    /** Above this evasion the treasurers hear of a city once a day. */
    private static final int EVASION_TOLD_PERCENT = 20;

    /**
     * The day of a city waiting in the queue.
     */
    record Job(UUID kingdom, long city, long day) {
    }

    private static final Deque<Job> QUEUE = new ArrayDeque<>();
    /** The means of the people of each city, by kingdom and city, and the day they were worked out. */
    private static final Map<String, long[]> MEANS = new HashMap<>();

    private CommerceWork() {
    }

    public static void reset() {
        QUEUE.clear();
        MEANS.clear();
        CityOf.reset();
        Shops.reset();
        CityGoods.reset();
        CrownPurchases.reset();
    }

    private static String key(UUID kingdom, long city) {
        return kingdom + "/" + city;
    }

    // ---------------------------------------------------------------- payday

    /**
     * The payday of a kingdom was held and the houses took their share: the earnings of the shops are paid, and the days of its
     * cities are put into the queue. Nothing for a kingdom of the world or with the money of the cities off.
     */
    public static void payday(MinecraftServer server, UUID kingdom, long day) {
        if (!WorldShops.shops(server, kingdom) || KingdomWorldApi.isReadOnly(server)) {
            return;
        }
        Economy economy = KingdomWorldApi.state(server).economy();
        boolean first = economy.cityTreasuriesOf(kingdom).isEmpty();
        List<CityWalls.Place> places = CityPeople.places(server, kingdom);
        // The shops pay their keepers what they earned yesterday, before their tills are counted as anybody's.
        Shops.payday(server, kingdom, day);
        orphans(server, kingdom, places, day);
        // The shops of the kingdom buy of the crown once, before the days of the cities.
        QUEUE.add(new Job(kingdom, -1, day));
        for (CityWalls.Place place : places) {
            economy.openCityTreasury(kingdom, place.cityId());
            QUEUE.add(new Job(kingdom, place.cityId(), day));
        }
        if (first && !places.isEmpty() && CityOf.active(server, kingdom)) {
            KingdomWorldApi.kingdom(server, kingdom).ifPresent(found -> KingdomActions.notifyMembers(server, found, Permission.MANAGE_TREASURY,
                    Component.translatable(KEY + "introduced", KingdomActions.kingdomName(found)).withStyle(ChatFormatting.GOLD)));
        }
        tellEvasion(server, kingdom, places);
    }

    /**
     * Money of a purse, a treasury or a till whose city or shop its kingdom no longer has goes into the purse of the townspeople
     * of the capital, so that nothing stays in a key nobody reads.
     */
    private static void orphans(MinecraftServer server, UUID kingdom, List<CityWalls.Place> places, long day) {
        Economy economy = KingdomWorldApi.state(server).economy();
        Set<Long> cities = new HashSet<>();
        for (CityWalls.Place place : places) {
            cities.add(place.cityId());
        }
        CityPurses.PurseKey capital = CityPurses.PurseKey.cityPurse(kingdom, 0);
        for (CityPurses.PurseKey key : economy.purses().keysOf(kingdom)) {
            boolean lost = switch (key.kind()) {
                case CITY_PURSE, CITY_TREASURY -> !cities.contains(key.id());
                case TILL -> !Shops.exists(server, key.id(), kingdom);
            };
            if (lost && !key.equals(capital)) {
                long amount = economy.purse(key);
                if (economy.purseMove(key, capital, amount, CityLedger.OTHER, day).ok()) {
                    LOGGER.info("{} of a {} {} of {} without its city or shop went to the townspeople of the capital", amount,
                            key.kind().id(), key.id(), kingdom);
                }
            }
        }
        for (CityTreasury city : economy.cityTreasuriesOf(kingdom)) {
            if (!cities.contains(city.cityId()) && economy.purse(city.key()) == 0) {
                economy.dropCityTreasury(kingdom, city.cityId());
            }
        }
    }

    /**
     * Tells the treasurers of a kingdom once a day of the cities whose people evade more than a fifth of their taxes.
     */
    private static void tellEvasion(MinecraftServer server, UUID kingdom, List<CityWalls.Place> places) {
        Optional<Treasury> treasury = KingdomWorldApi.state(server).economy().treasury(kingdom);
        if (treasury.isEmpty()) {
            return;
        }
        CityEconomyRules rules = CityEconomySettings.rules();
        List<Component> names = new ArrayList<>();
        for (CityWalls.Place place : places) {
            int evasion = Evasion.percent(burden(server, kingdom, place.cityId()), rules.evasionStartPercent(), rules.evasionMaxPercent());
            if (evasion > EVASION_TOLD_PERCENT) {
                names.add(NpcTexts.city(place.name()));
            }
        }
        if (names.isEmpty()) {
            return;
        }
        MutableComponent list = Component.empty();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                list.append(", ");
            }
            list.append(names.get(i));
        }
        KingdomWorldApi.kingdom(server, kingdom).ifPresent(found -> KingdomActions.notifyMembers(server, found, Permission.MANAGE_TREASURY,
                Component.translatable("kingdomworld.treasury.cities.evasion", list).withStyle(ChatFormatting.GOLD)));
    }

    // ---------------------------------------------------------------- the taxes of a city

    /**
     * The sales tax, the tithe and the share of the crown of a city in thousandths: its own, else those of its kingdom.
     */
    public static int salesTax(MinecraftServer server, UUID kingdom, long city) {
        Economy economy = KingdomWorldApi.state(server).economy();
        if (WorldShops.active(server, kingdom)) {
            // The cities of a ruler of the world take the sales tax of its market.
            return economy.treasury(kingdom).map(Treasury::salesTaxPermille).orElse(0);
        }
        int own = economy.treasury(kingdom).map(CityEconomySettings::salesTax).orElse(CityEconomySettings.rules().salesTaxPercent() * 10);
        return economy.cityTreasury(kingdom, city).map(found -> found.salesTax(own)).orElse(own);
    }

    public static int tithe(MinecraftServer server, UUID kingdom, long city) {
        Economy economy = KingdomWorldApi.state(server).economy();
        int own = economy.treasury(kingdom).map(CityEconomySettings::tithe).orElse(CityEconomySettings.rules().tithePercent() * 10);
        return economy.cityTreasury(kingdom, city).map(found -> found.tithe(own)).orElse(own);
    }

    public static int crownShare(MinecraftServer server, UUID kingdom, long city) {
        Economy economy = KingdomWorldApi.state(server).economy();
        if (WorldShops.active(server, kingdom)) {
            // The treasuries of the cities of the world pass all they take on to the ruler.
            return 1000;
        }
        int own = economy.treasury(kingdom).map(CityEconomySettings::crownShare).orElse(CityEconomySettings.rules().crownSharePercent() * 10);
        return economy.cityTreasury(kingdom, city).map(found -> found.crownShare(own)).orElse(own);
    }

    /**
     * The sales tax and the tithe of a city together, in thousandths.
     */
    public static int burden(MinecraftServer server, UUID kingdom, long city) {
        return salesTax(server, kingdom, city) + tithe(server, kingdom, city);
    }

    /**
     * The share of its taxes the people of a city evade, in thousandths.
     */
    public static int evasion(MinecraftServer server, UUID kingdom, long city) {
        if (WorldShops.active(server, kingdom)) {
            return 0;
        }
        CityEconomyRules rules = CityEconomySettings.rules();
        return Evasion.permille(burden(server, kingdom, city), rules.evasionStartPercent(), rules.evasionMaxPercent());
    }

    /**
     * Whether a city has a head who spends its money: a governor, a mayor or a house of the nobility. A city without one keeps
     * only a week of its own share; a city with one keeps a month of it besides what it promised its constructions.
     */
    public static boolean hasHead(MinecraftServer server, UUID kingdom, long city) {
        return io.github.sekerator.kingdomworld.common.road.CityGovernors.governor(server, kingdom, city).present();
    }

    // ---------------------------------------------------------------- the burden of the taxes

    /**
     * The taxes of every city of a kingdom with the people who pay them: its named citizens and its townsfolk.
     */
    private static List<TaxBurden.City> cityBurdens(MinecraftServer server, UUID kingdom) {
        List<TaxBurden.City> cities = new ArrayList<>();
        if (!CityOf.active(server, kingdom)) {
            return cities;
        }
        Map<Long, Integer> named = CityOf.counts(kingdom);
        Map<Long, Integer> folk = CityPeople.folkByCity(server, kingdom);
        boolean known = !named.isEmpty();
        for (CityWalls.Place place : CityPeople.places(server, kingdom)) {
            long people = (long) folk.getOrDefault(place.cityId(), 0) + (known ? named.getOrDefault(place.cityId(), 0)
                    : place.cityId() == 0 ? KingdomWorldApi.state(server).citizens().registry().count(kingdom) : 0);
            cities.add(new TaxBurden.City(burden(server, kingdom, place.cityId()), people));
        }
        return cities;
    }

    /**
     * How heavy the taxes of a kingdom weigh, in percent, for its stability and its nobles.
     */
    public static int taxBurden(MinecraftServer server, UUID kingdom) {
        long usual = io.github.sekerator.kingdomworld.common.economy.EconomySettings.rules().defaultPollTax();
        long poll = KingdomWorldApi.state(server).economy().treasury(kingdom).map(Treasury::pollTax).orElse(usual);
        return TaxBurden.percent(poll, usual, cityBurdens(server, kingdom));
    }

    /**
     * Whether the taxes of the cities of a kingdom weigh more than its poll tax.
     */
    public static boolean citiesHeavier(MinecraftServer server, UUID kingdom) {
        long usual = io.github.sekerator.kingdomworld.common.economy.EconomySettings.rules().defaultPollTax();
        long poll = KingdomWorldApi.state(server).economy().treasury(kingdom).map(Treasury::pollTax).orElse(usual);
        return TaxBurden.citiesHeavier(poll, usual, cityBurdens(server, kingdom));
    }

    /**
     * The nobles asked to lower the taxes and the taxes of the cities weigh more: the sales tax and the tithe of the kingdom
     * fall by the share asked, and every city set above them keeps no more than them.
     */
    public static io.github.sekerator.kingdomworld.core.kingdom.Outcome lowerCityTaxes(MinecraftServer server, UUID kingdom, long percent) {
        Economy economy = KingdomWorldApi.state(server).economy();
        Optional<Treasury> treasury = economy.treasury(kingdom);
        if (treasury.isEmpty()) {
            return io.github.sekerator.kingdomworld.core.kingdom.Outcome.fail(io.github.sekerator.kingdomworld.core.kingdom.KingdomError.UNKNOWN_KINGDOM);
        }
        int keep = (int) (100 - Math.max(0, Math.min(100, percent)));
        int salesTax = CityEconomySettings.salesTax(treasury.get()) * keep / 100;
        int tithe = CityEconomySettings.tithe(treasury.get()) * keep / 100;
        int share = CityEconomySettings.crownShare(treasury.get());
        io.github.sekerator.kingdomworld.core.kingdom.Outcome outcome = economy.setKingdomCityTaxes(kingdom, salesTax, tithe, share);
        meansChanged(kingdom);
        return outcome.ok() || outcome.error() == io.github.sekerator.kingdomworld.core.kingdom.KingdomError.UNCHANGED
                ? io.github.sekerator.kingdomworld.core.kingdom.Outcome.DONE : outcome;
    }

    // ---------------------------------------------------------------- the means of the people

    /**
     * The mood the money of its city gives a citizen of a kingdom of players; 0 elsewhere.
     */
    public static int means(MinecraftServer server, Citizen citizen) {
        if (!CityOf.active(server, citizen.kingdomId())) {
            return 0;
        }
        return meansOf(server, citizen.kingdomId(), CityOf.of(server, citizen)).value();
    }

    /**
     * The means of the people of a city, worked out once a day.
     */
    public static CityMeans meansOf(MinecraftServer server, UUID kingdom, long city) {
        long day = TreasuryActions.day(server);
        long[] known = MEANS.get(key(kingdom, city));
        if (known != null && known[0] == day) {
            return new CityMeans((int) known[1], (int) known[2], (int) known[3], (int) known[4], (int) known[5]);
        }
        CityEconomyRules rules = CityEconomySettings.rules();
        Optional<CityTreasury> treasury = KingdomWorldApi.state(server).economy().cityTreasury(kingdom, city);
        int basket = treasury.map(found -> found.latest().basketPercent()).orElse(0);
        boolean recalled = treasury.map(found -> found.recalled(day)).orElse(false);
        CityMeans means = CityMeans.of(burden(server, kingdom, city), basket, recalled, hasHead(server, kingdom, city),
                crownShare(server, kingdom, city), rules.crownShareFairPercent());
        MEANS.put(key(kingdom, city), new long[]{day, means.burden(), means.shops(), means.recall(), means.share(), means.value()});
        return means;
    }

    /**
     * Forgets the means of a city, after its taxes or its treasury changed.
     */
    public static void meansChanged(UUID kingdom) {
        MEANS.keySet().removeIf(key -> key.startsWith(kingdom + "/"));
    }

    // ---------------------------------------------------------------- the days of the cities

    /**
     * Lives the days of cities in the queue for as long as a tick allows.
     */
    public static void tick(MinecraftServer server) {
        if (QUEUE.isEmpty() || KingdomWorldApi.isReadOnly(server)) {
            return;
        }
        long start = System.nanoTime();
        while (!QUEUE.isEmpty() && System.nanoTime() - start < BUDGET_NANOS) {
            Job job = QUEUE.peek();
            boolean done;
            try {
                done = step(server, job, start);
            } catch (RuntimeException e) {
                LOGGER.error("KingdomWorld could not live the day of city {} of {}", job.city(), job.kingdom(), e);
                done = true;
            }
            if (done) {
                QUEUE.poll();
            }
        }
    }

    /**
     * Whether days of cities wait in the queue, for checks.
     */
    public static boolean busy() {
        return !QUEUE.isEmpty();
    }

    /**
     * Lives every day in the queue at once, for game tests.
     */
    public static void drain(MinecraftServer server) {
        int guard = 0;
        while (!QUEUE.isEmpty() && guard++ < 1_000_000) {
            Job job = QUEUE.peek();
            if (step(server, job, Long.MAX_VALUE / 2 + System.nanoTime())) {
                QUEUE.poll();
            }
        }
    }

    /**
     * Lives as much of the day of a city as the time allows.
     *
     * @param start when the tick began, for the budget
     * @return whether the day is done
     */
    private static boolean step(MinecraftServer server, Job job, long start) {
        Economy economy = KingdomWorldApi.state(server).economy();
        if (economy.treasury(job.kingdom()).isEmpty() || !WorldShops.shops(server, job.kingdom())) {
            return true;
        }
        if (job.city() < 0) {
            return Shops.restock(server, job.kingdom(), job.day(), start);
        }
        CityTreasury treasury = economy.openCityTreasury(job.kingdom(), job.city());
        if (treasury.lastDay() >= job.day()) {
            return true;
        }
        if (!Shops.dayOfShops(server, job.kingdom(), job.city(), job.day(), start)) {
            return false;
        }
        settleTreasury(server, job.kingdom(), job.city(), job.day());
        purseOverflow(server, job.kingdom(), job.city(), job.day());
        // What the people of the city ate from the storehouses, named and townsfolk.
        long people = CityOf.counts(job.kingdom()).getOrDefault(job.city(), 0)
                + io.github.sekerator.kingdomworld.common.road.RoadWork.state(server).folk(job.kingdom(), job.city());
        economy.cityTally(job.kingdom(), job.city()).add(CityStatement.Line.GOODS_EATEN, CityGoods.eaten(server, job.kingdom(), people));
        CityStatement statement = economy.sealCity(job.kingdom(), job.city(), job.day());
        MEANS.remove(key(job.kingdom(), job.city()));
        LOGGER.debug("Day {} of city {} of {}: taxes {}, to the crown {}, purse in {}", job.day(), job.city(), job.kingdom(),
                statement.taxIncome(), statement.get(CityStatement.Line.TO_CROWN), statement.get(CityStatement.Line.PURSE_IN));
        return true;
    }

    /**
     * The treasury of a city sends the crown its share of the taxes of the day, and a city without a head what it keeps beyond a
     * week of its own share. What the treasury of the kingdom cannot take stays.
     */
    static void settleTreasury(MinecraftServer server, UUID kingdom, long city, long day) {
        Economy economy = KingdomWorldApi.state(server).economy();
        CityTreasury treasury = economy.openCityTreasury(kingdom, city);
        CityStatement.Tally tally = treasury.tally();
        long taxToday = Money.addCapped(tally.get(CityStatement.Line.TITHE), tally.get(CityStatement.Line.SALES_TAX));
        int share = crownShare(server, kingdom, city);
        boolean head = hasHead(server, kingdom, city);
        long averageKept = treasury.averageWithToday(CityStatement.Line.KEPT);
        CityTreasury.Settlement settlement = CityTreasury.settle(economy.purse(treasury.key()), treasury.granted(), taxToday,
                treasury.averageTax(), share, head, averageKept, treasury.committed(), CityEconomySettings.rules());
        long kept = settlement.kept();
        if (settlement.share() > 0 && !economy.purseToTreasury(treasury.key(), settlement.share(), LedgerCategory.CITIES,
                CityLedger.CROWN_SHARE, day).ok()) {
            // The treasury of the kingdom is full: the share stays in the city.
            kept += settlement.share();
        }
        tally.add(CityStatement.Line.KEPT, kept);
        if (settlement.idle() > 0) {
            economy.purseToTreasury(treasury.key(), settlement.idle(), LedgerCategory.CITIES, CityLedger.IDLE_SHARE, day);
        }
    }

    /**
     * The purse of the townspeople keeps the days of what comes into it the config says; more leaves the world, as all wages
     * did before.
     */
    static void purseOverflow(MinecraftServer server, UUID kingdom, long city, long day) {
        Economy economy = KingdomWorldApi.state(server).economy();
        CityTreasury treasury = economy.openCityTreasury(kingdom, city);
        CityPurses.PurseKey purse = CityPurses.PurseKey.cityPurse(kingdom, city);
        // The purses of the cities of the world keep nothing: what is left of them leaves the world, as their wages did before.
        long keep = WorldShops.active(server, kingdom) ? 0
                : Money.multiplyCapped(CityEconomySettings.rules().purseKeepDays(), treasury.averageWithToday(CityStatement.Line.PURSE_IN));
        long over = economy.purse(purse) - keep;
        if (over > 0) {
            economy.purseLeaves(purse, city, over, CityPurses.Outflow.PURSE_FULL, day);
        }
    }
}
