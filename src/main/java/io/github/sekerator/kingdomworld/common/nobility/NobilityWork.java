package io.github.sekerator.kingdomworld.common.nobility;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.economy.EconomySettings;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.law.LawWork;
import io.github.sekerator.kingdomworld.common.life.LifeWork;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.road.CityWalls;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.siege.SiegeWork;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyState;
import io.github.sekerator.kingdomworld.core.diplomacy.Relations;
import io.github.sekerator.kingdomworld.core.diplomacy.War;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.economy.TreasuryJournal;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.nobility.Court;
import io.github.sekerator.kingdomworld.core.nobility.Demand;
import io.github.sekerator.kingdomworld.core.nobility.Fief;
import io.github.sekerator.kingdomworld.core.nobility.Grant;
import io.github.sekerator.kingdomworld.core.nobility.Happening;
import io.github.sekerator.kingdomworld.core.nobility.NobilityMind;
import io.github.sekerator.kingdomworld.core.nobility.NobilityRules;
import io.github.sekerator.kingdomworld.core.nobility.NobilityState;
import io.github.sekerator.kingdomworld.core.nobility.NobleHouse;
import io.github.sekerator.kingdomworld.core.nobility.Note;
import io.github.sekerator.kingdomworld.core.nobility.Plot;
import io.github.sekerator.kingdomworld.core.nobility.Spy;
import io.github.sekerator.kingdomworld.core.nobility.Stability;
import io.github.sekerator.kingdomworld.core.nobility.Title;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.RulerCharacter;
import io.github.sekerator.kingdomworld.core.npc.RulerMemory;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.siege.Conquest;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.Function;

/**
 * The nobility of the running world: once a game day every court has its day (one kingdom a tick, without looking at the
 * world itself), then the spies and the marriages. Money goes through the books: the share of the nobles, the wages and
 * ransoms of spies, what rebels keep and what spies take.
 */
public final class NobilityWork {
    static final Logger LOGGER = LogUtils.getLogger();
    static final String KEY = NobilityTexts.KEY;
    /** Game days a lost war or a lost city weighs on the stability. */
    private static final long DEFEAT_DAYS = 10;
    private static final Random RANDOM = new Random();

    private static DoubleSupplier rolls;
    private static long lastDay = Long.MIN_VALUE;
    private static final Deque<UUID> QUEUE = new ArrayDeque<>();
    private static boolean worldDayDone = true;
    private static long dayOfQueue;
    /** The cost of the ticks of the nobility: count, sum and worst in nanoseconds. */
    private static long ticksMeasured;
    private static long ticksSum;
    private static long ticksWorst;

    private NobilityWork() {
    }

    // ---------------------------------------------------------------- access

    public static NobilityState state(MinecraftServer server) {
        return NobilitySavedData.get(server).state();
    }

    public static boolean isReadOnly(MinecraftServer server) {
        return NobilitySavedData.get(server).isReadOnly() || KingdomWorldApi.isReadOnly(server);
    }

    /**
     * Runs a change of the nobility, refusing it while the nobility or the kingdoms are read-only.
     */
    public static Outcome change(MinecraftServer server, String what, Function<NobilityState, Outcome> change) {
        if (isReadOnly(server)) {
            LOGGER.debug("Refused to {}: nobility or kingdom data is read-only", what);
            return Outcome.fail(KingdomError.READ_ONLY);
        }
        return change.apply(state(server));
    }

    public static void reset() {
        rolls = null;
        lastDay = Long.MIN_VALUE;
        QUEUE.clear();
        worldDayDone = true;
        ticksMeasured = 0;
        ticksSum = 0;
        ticksWorst = 0;
    }

    /**
     * Replaces the random numbers of the nobility, for game tests; {@code null} gives chance back its say.
     */
    public static void overrideRolls(DoubleSupplier value) {
        rolls = value;
    }

    static DoubleSupplier rolls() {
        return rolls != null ? rolls : RANDOM::nextDouble;
    }

    public static long day(MinecraftServer server) {
        return TreasuryActions.day(server);
    }

    /**
     * Whether a player of a kingdom of players was there today (a kingdom of the world is always played).
     */
    public static boolean played(MinecraftServer server, UUID kingdomId) {
        if (WarWork.isNpc(server, kingdomId) || WarWork.present(server, kingdomId)) {
            return true;
        }
        long[] seen = WarWork.state(server).presence().all().get(kingdomId);
        return seen != null && KingdomWorldApi.gameTime(server) - seen[0] <= CitizenNeeds.TICKS_PER_DAY;
    }

    /**
     * How the stability of a kingdom moves the mood of its citizens: down when it is low, up when it is high.
     */
    public static int moodShift(MinecraftServer server, UUID kingdomId) {
        if (server == null || kingdomId == null || NobilitySavedData.get(server).isReadOnly()) {
            return 0;
        }
        Optional<Court> court = state(server).existingCourt(kingdomId);
        if (court.isEmpty()) {
            return 0;
        }
        NobilityRules rules = NobilitySettings.rules();
        int value = court.get().stability().value();
        return value < rules.plotBelow() ? rules.moodLow() : value >= rules.highStability() ? rules.moodHigh() : 0;
    }

    /**
     * The ticks of the nobility measured since the start: count, average and worst in milliseconds.
     */
    public static double[] timing() {
        return new double[]{ticksMeasured, ticksMeasured == 0 ? 0 : ticksSum / 1e6 / ticksMeasured, ticksWorst / 1e6};
    }

    public static void resetTiming() {
        ticksMeasured = 0;
        ticksSum = 0;
        ticksWorst = 0;
    }

    // ---------------------------------------------------------------- texts

    static void tell(MinecraftServer server, UUID kingdomId, @Nullable Permission right, Component message) {
        KingdomWorldApi.kingdom(server, kingdomId).ifPresent(kingdom -> KingdomActions.notifyMembers(server, kingdom, right, message));
    }

    // ---------------------------------------------------------------- once a tick

    /**
     * When a game day begins, every court gets its day, one a tick; after the last one the spies and the marriages.
     */
    public static void tick(MinecraftServer server) {
        if (isReadOnly(server)) {
            return;
        }
        long start = System.nanoTime();
        long day = day(server);
        if (day != lastDay) {
            boolean first = lastDay == Long.MIN_VALUE;
            lastDay = day;
            if (!first) {
                startDay(server, day);
            }
        }
        boolean worked = false;
        if (!QUEUE.isEmpty()) {
            UUID kingdomId = QUEUE.poll();
            if (KingdomWorldApi.kingdom(server, kingdomId).isPresent()) {
                try {
                    kingdomDay(server, kingdomId, dayOfQueue);
                } catch (RuntimeException e) {
                    LOGGER.error("The day of the court of {} failed", kingdomId, e);
                }
            }
            worked = true;
        } else if (!worldDayDone) {
            worldDayDone = true;
            try {
                worldDay(server, dayOfQueue);
            } catch (RuntimeException e) {
                LOGGER.error("The day of the spies failed", e);
            }
            worked = true;
        }
        if (worked) {
            long spent = System.nanoTime() - start;
            ticksMeasured++;
            ticksSum += spent;
            ticksWorst = Math.max(ticksWorst, spent);
        }
    }

    /**
     * Queues the day of every kingdom; operators and game tests use it to move a day on at once.
     */
    public static void startDay(MinecraftServer server, long day) {
        QUEUE.clear();
        for (Kingdom kingdom : KingdomWorldApi.kingdoms(server)) {
            QUEUE.add(kingdom.id());
        }
        worldDayDone = false;
        dayOfQueue = day;
        forgetGone(server);
    }

    /**
     * Runs a whole day at once, for operators and game tests.
     */
    public static void runDay(MinecraftServer server) {
        long day = day(server);
        startDay(server, day);
        while (!QUEUE.isEmpty()) {
            UUID kingdomId = QUEUE.poll();
            if (KingdomWorldApi.kingdom(server, kingdomId).isPresent()) {
                kingdomDay(server, kingdomId, day);
            }
        }
        worldDayDone = true;
        worldDay(server, day);
    }

    private static void forgetGone(MinecraftServer server) {
        NobilityState state = state(server);
        List<UUID> gone = new ArrayList<>();
        for (Court court : state.courts()) {
            if (KingdomWorldApi.kingdom(server, court.kingdomId()).isEmpty()) {
                gone.add(court.kingdomId());
            }
        }
        for (NobleHouse house : state.houses()) {
            if (KingdomWorldApi.kingdom(server, house.kingdomId()).isEmpty() && !gone.contains(house.kingdomId())) {
                gone.add(house.kingdomId());
            }
        }
        gone.forEach(state::forget);
        // Titles of players who left the kingdom of a crown of players end; nobles of kingdoms of the world need not belong.
        for (Grant grant : List.copyOf(state.grants())) {
            if (grant.heldByPlayer() && !WarWork.isNpc(server, grant.kingdomId())
                    && !KingdomWorldApi.state(server).membership().isMember(grant.player(), grant.kingdomId())) {
                state.revoke(grant.id(), day(server));
            } else if (grant.fief().kind() == Fief.Kind.CITY && RoadWork.state(server).city(grant.fief().city())
                    .map(city -> !city.kingdomId().equals(grant.kingdomId())).orElse(true)) {
                state.revoke(grant.id(), day(server));
            }
        }
    }

    // ---------------------------------------------------------------- the day of a court

    static void kingdomDay(MinecraftServer server, UUID kingdomId, long day) {
        NobilityState state = state(server);
        NobilityRules rules = NobilitySettings.rules();
        boolean npc = WarWork.isNpc(server, kingdomId);
        if (npc && state.existingCourt(kingdomId).isEmpty()) {
            foundCourt(server, kingdomId, day);
        }
        boolean played = played(server, kingdomId);
        Stability.Inputs inputs = inputs(server, kingdomId, day);
        int counter = state.counter(kingdomId, spymaster(server, kingdomId), rules);
        NobilityState.Context context = context(server, kingdomId, day);
        List<Happening> happened = state.day(new NobilityState.DayInput(kingdomId, npc, played, day, inputs, counter, context), rules, rolls());
        for (Happening happening : happened) {
            try {
                act(server, happening, day);
            } catch (RuntimeException e) {
                LOGGER.error("The nobility could not carry out {}", happening, e);
            }
        }
        shares(server, kingdomId, day);
        wages(server, kingdomId, day);
        if (npc) {
            NobilityNpc.day(server, kingdomId, day);
        }
    }

    /**
     * The houses of a kingdom of the world, with titles on its towns and marches.
     */
    static void foundCourt(MinecraftServer server, UUID kingdomId, long day) {
        Optional<NpcKingdom> npc = NpcWork.kingdom(server, kingdomId);
        NpcLevel level = npc.map(NpcKingdom::level).orElse(NpcLevel.SMALL);
        long seed = npc.map(n -> (long) n.rulerSeed()).orElse((long) kingdomId.hashCode()) ^ kingdomId.getLeastSignificantBits();
        List<Fief> fiefs = new ArrayList<>();
        for (City city : RoadWork.state(server).citiesOf(kingdomId)) {
            fiefs.add(Fief.city(city.id()));
        }
        for (int side = 0; side < Fief.MARCHES; side++) {
            fiefs.add(Fief.march(side));
        }
        state(server).foundCourt(kingdomId, NobilityMind.houses(level, seed), seed, fiefs, level == NpcLevel.LARGE, day);
        LOGGER.info("The court of {} appears with {} house(s)", WarWork.name(server, kingdomId).getString(), state(server).houses(kingdomId).size());
    }

    static boolean spymaster(MinecraftServer server, UUID kingdomId) {
        Optional<NpcKingdom> npc = NpcWork.kingdom(server, kingdomId);
        if (npc.isPresent()) {
            return npc.get().traits().contains(RulerCharacter.Trait.CAUTIOUS) || npc.get().traits().contains(RulerCharacter.Trait.AMBITIOUS);
        }
        return KingdomWorldApi.state(server).membership().kingdom(kingdomId)
                .map(members -> members.members().values().stream().anyMatch(member -> member.role() == Role.SPYMASTER)).orElse(false);
    }

    /**
     * What the world says of the stability of a kingdom: the mood of its citizens, its tax, its wars, defeats and festivals.
     */
    static Stability.Inputs inputs(MinecraftServer server, UUID kingdomId, long day) {
        int mood = -1;
        int sum = 0;
        int count = 0;
        for (Citizen citizen : KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdomId)) {
            sum += citizen.mood();
            count++;
        }
        if (count > 0) {
            mood = sum / count;
        }
        int exhaustion = 0;
        int defeats = 0;
        long now = KingdomWorldApi.gameTime(server);
        long recent = now - DEFEAT_DAYS * CitizenNeeds.TICKS_PER_DAY;
        for (War war : WarWork.state(server).warsOf(kingdomId)) {
            Optional<War.Side> side = war.sideOf(kingdomId);
            if (side.isEmpty()) {
                continue;
            }
            if (!war.over()) {
                exhaustion = Math.max(exhaustion, (int) Math.round(war.exhaustion(side.get())));
            } else if (war.ended() >= recent && war.score(side.get()) < war.score(side.get().enemy())) {
                defeats++;
            }
        }
        for (Conquest conquest : SiegeWork.state(server).conquests()) {
            if (conquest.loser().equals(kingdomId) && conquest.made() >= recent) {
                defeats++;
            }
        }
        boolean feast;
        try {
            long festivalDay = LifeWork.state(server).festival(kingdomId).festivalDay();
            feast = festivalDay != Long.MIN_VALUE && day - festivalDay >= 0 && day - festivalDay <= 2;
        } catch (RuntimeException e) {
            feast = false;
        }
        return new Stability.Inputs(-1, mood, taxPercent(server, kingdomId), exhaustion, defeats, feast, -1);
    }

    static int taxPercent(MinecraftServer server, UUID kingdomId) {
        long usual = EconomySettings.rules().defaultPollTax();
        long tax = KingdomWorldApi.state(server).economy().treasury(kingdomId).map(Treasury::pollTax).orElse(usual);
        return usual <= 0 ? 100 : (int) Math.min(1_000, tax * 100 / usual);
    }

    static NobilityState.Context context(MinecraftServer server, UUID kingdomId, long day) {
        DiplomacyState diplomacy = WarWork.state(server);
        UUID enemy = null;
        for (UUID other : diplomacy.enemiesOf(kingdomId)) {
            if (diplomacy.fighting(kingdomId, other)) {
                enemy = other;
                break;
            }
        }
        UUID rival = null;
        int worst = -10;
        if (enemy == null) {
            for (Kingdom other : KingdomWorldApi.kingdoms(server)) {
                if (other.id().equals(kingdomId) || !WarWork.playable(server, other.id())) {
                    continue;
                }
                int relation = WarWork.relation(server, kingdomId, other.id());
                if (relation < worst) {
                    worst = relation;
                    rival = other.id();
                }
            }
        }
        long loot = 0;
        Optional<Treasury> treasury = KingdomWorldApi.state(server).economy().treasury(kingdomId);
        if (treasury.isPresent()) {
            for (long d = day - 7; d <= day; d++) {
                Map<LedgerCategory, TreasuryJournal.Totals> totals = treasury.get().journal().days().getOrDefault(d, Map.of());
                loot += totals.getOrDefault(LedgerCategory.WAR, TreasuryJournal.Totals.ZERO).income();
            }
        }
        boolean free = !freeFiefs(server, kingdomId).isEmpty();
        return new NobilityState.Context(enemy, rival, loot, taxPercent(server, kingdomId), free);
    }

    /**
     * The fiefs of a kingdom nobody holds: its cities and its four marches.
     */
    public static List<Fief> freeFiefs(MinecraftServer server, UUID kingdomId) {
        List<Fief> free = new ArrayList<>();
        NobilityState state = state(server);
        for (Fief fief : fiefs(server, kingdomId)) {
            if (state.holderOf(kingdomId, fief).isEmpty()) {
                free.add(fief);
            }
        }
        return free;
    }

    public static List<Fief> fiefs(MinecraftServer server, UUID kingdomId) {
        List<Fief> all = new ArrayList<>();
        for (City city : RoadWork.state(server).citiesOf(kingdomId)) {
            all.add(Fief.city(city.id()));
        }
        for (int side = 0; side < Fief.MARCHES; side++) {
            all.add(Fief.march(side));
        }
        return all;
    }

    // ---------------------------------------------------------------- what happened

    private static void act(MinecraftServer server, Happening happening, long day) {
        UUID kingdomId = happening.kingdomId();
        NobilityState state = state(server);
        boolean npc = WarWork.isNpc(server, kingdomId);
        Optional<NobleHouse> house = state.house(happening.house());
        Component houseName = house.<Component>map(NobilityTexts::house).orElse(Component.literal("?"));
        switch (happening.kind()) {
            case DEMAND_MADE -> {
                Optional<Demand> demand = state.demand(happening.id());
                if (demand.isEmpty()) {
                    return;
                }
                if (npc) {
                    NobilityNpc.answer(server, demand.get(), day);
                } else {
                    tell(server, kingdomId, Permission.MANAGE_NOBILITY, Component.translatable(KEY + "msg.demand", houseName,
                            Component.translatable(KEY + "word." + demand.get().kind().id())).withStyle(ChatFormatting.GOLD));
                }
            }
            case DEMAND_LAPSED -> {
                if (!npc) {
                    tell(server, kingdomId, Permission.MANAGE_NOBILITY, Component.translatable(KEY + "msg.lapsed", houseName).withStyle(ChatFormatting.GRAY));
                }
            }
            case PLOT_DISCOVERED -> {
                if (npc) {
                    NpcWork.kingdom(server, kingdomId).ifPresent(ruler -> {
                        Plot.Verdict verdict = NobilityMind.verdict(ruler.traits());
                        state.verdict(happening.id(), verdict, day, rolls().getAsDouble() > 0.5 ? RANDOM.nextLong() : day * 31);
                    });
                } else {
                    tell(server, kingdomId, Permission.MANAGE_NOBILITY, Component.translatable(KEY + "msg.plot_found", houseName).withStyle(ChatFormatting.RED));
                }
                if (happening.other() != null) {
                    WarWork.state(server).relate(kingdomId, happening.other(), Relations.Reason.PLOT_BACKED);
                }
            }
            case RULER_OVERTHROWN -> house.ifPresent(h -> overthrow(server, kingdomId, h, happening.other(), day));
            case REBELS -> rebels(server, kingdomId, happening, house, day);
            case SECESSION -> house.ifPresent(h -> NobilitySecession.secede(server, kingdomId, h, happening.value(), happening.other(), day));
            case SECESSION_WARNING -> tell(server, kingdomId, null, Component.translatable(KEY + "msg.warning", houseName).withStyle(ChatFormatting.RED));
            case HEAD_DIED -> LOGGER.debug("The head of the house {} of {} died", houseName.getString(), WarWork.name(server, kingdomId).getString());
            default -> {
            }
        }
    }

    /**
     * A plot overthrew the ruler of a kingdom of the world: the head of the house rules now, with their character.
     */
    static void overthrow(MinecraftServer server, UUID kingdomId, NobleHouse house, @Nullable UUID sponsor, long day) {
        Optional<NpcKingdom> old = NpcWork.kingdom(server, kingdomId);
        if (old.isEmpty()) {
            return;
        }
        NpcKingdom was = old.get();
        NpcKingdom now = new NpcKingdom(was.kingdomId(), was.siteId(), was.level(), was.name(), house.character(), List.of(house.trait()),
                house.headSeed(), house.headGender(), was.towns(), was.founded(), was.lastTurnDay());
        NpcWork.change(server, "a new ruler from a plot", npc -> {
            npc.putKingdom(now);
            return Outcome.DONE;
        });
        if (sponsor != null) {
            WarWork.state(server).relate(kingdomId, sponsor, Relations.Reason.PLOT_BACKED);
        }
        tell(server, kingdomId, null, Component.translatable(KEY + "msg.overthrown", WarWork.name(server, kingdomId), NobilityTexts.house(house),
                NobilityTexts.head(house)).withStyle(ChatFormatting.GOLD));
        LOGGER.info("A plot of the house {} overthrew the ruler of {}; {} rules now", house.family(io.github.sekerator.kingdomworld.core.citizen.CitizenNames.Alphabet.LATIN),
                WarWork.name(server, kingdomId).getString(), house.head(io.github.sekerator.kingdomworld.core.citizen.CitizenNames.Alphabet.LATIN));
    }

    /**
     * Rebels keep part of the income of yesterday; it leaves the treasury by the books.
     */
    private static void rebels(MinecraftServer server, UUID kingdomId, Happening happening, Optional<NobleHouse> house, long day) {
        if (happening.id() > 0) {
            tell(server, kingdomId, null, Component.translatable(KEY + "msg.rebels", house.<Component>map(NobilityTexts::house).orElse(Component.literal("?")))
                    .withStyle(ChatFormatting.RED));
            if (happening.other() != null) {
                WarWork.state(server).relate(kingdomId, happening.other(), Relations.Reason.PLOT_BACKED);
            }
        }
        long kept = Math.min(WarWork.income(server, kingdomId, day - 1) * NobilitySettings.rules().rebelPercent() / 100,
                WarWork.funds(server, kingdomId));
        if (kept > 0 && KingdomWorldApi.state(server).economy().nobilityPayment(kingdomId, kept, day).ok()) {
            house.ifPresent(h -> state(server).addWealth(h, kept));
            state(server).write(kingdomId, new Note(day, "kept", List.of(Note.number(kept))));
        }
    }

    // ---------------------------------------------------------------- money of the day

    /**
     * The holders of titles take their share of the income of their fief: a share of the income of yesterday, as large as
     * the part of the citizens of the kingdom who live there. Players get it onto their account; houses keep it.
     */
    static void shares(MinecraftServer server, UUID kingdomId, long day) {
        NobilityState state = state(server);
        List<Grant> grants = state.grants(kingdomId);
        if (grants.isEmpty()) {
            return;
        }
        long income = WarWork.income(server, kingdomId, day - 1);
        if (income <= 0) {
            return;
        }
        Map<Fief, Integer> people = people(server, kingdomId);
        int all = Math.max(1, people.values().stream().mapToInt(Integer::intValue).sum());
        NobilityRules rules = NobilitySettings.rules();
        for (Grant grant : grants) {
            long share = fiefShare(income, people.getOrDefault(grant.fief(), 0), all, rules.share(grant.title()));
            if (share <= 0) {
                continue;
            }
            var economy = KingdomWorldApi.state(server).economy();
            if (grant.heldByPlayer()) {
                if (economy.kingdomPays(kingdomId, share, grant.player(), LedgerCategory.NOBILITY, day).ok()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(grant.player());
                    if (player != null) {
                        player.sendSystemMessage(Component.translatable(KEY + "msg.share", NobilityTexts.fief(server, grant.fief()),
                                NobilityTexts.money(server, kingdomId, share)).withStyle(ChatFormatting.GREEN));
                    }
                }
            } else if (economy.nobilityPayment(kingdomId, share, day).ok()) {
                state.house(grant.house()).ifPresent(house -> state.addWealth(house, share));
            }
        }
    }

    /**
     * The share of a fief: the percent of the title of the part of the income its people make, at least one coin when its
     * people make any.
     */
    public static long fiefShare(long income, int people, int all, int percent) {
        if (income <= 0 || people <= 0 || all <= 0 || percent <= 0) {
            return 0;
        }
        return Math.max(1, income * people / all * percent / 100);
    }

    /**
     * How many citizens live in each fief: in a city (near it, nearer to it than to the capital and the other cities), or in the
     * march of the side of the capital they live on.
     */
    static Map<Fief, Integer> people(MinecraftServer server, UUID kingdomId) {
        Map<Fief, Integer> result = new HashMap<>();
        Optional<Capital> capital = KingdomWorldApi.state(server).territory().territory(kingdomId).map(Territory::capital);
        List<City> cities = RoadWork.state(server).citiesOf(kingdomId);
        for (Citizen citizen : KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdomId)) {
            WorldPos at = citizen.home() != null ? citizen.home() : citizen.lastPos();
            if (at == null || capital.isEmpty()) {
                continue;
            }
            City nearest = null;
            long best = distance(capital.get().x(), capital.get().z(), at);
            for (City city : cities) {
                long d = distance(city.x(), city.z(), at);
                if (city.dimension().equals(at.dimension()) && Math.abs(at.x() - city.x()) <= CityWalls.AREA && Math.abs(at.z() - city.z()) <= CityWalls.AREA
                        && d < best) {
                    best = d;
                    nearest = city;
                }
            }
            Fief fief = nearest != null ? Fief.city(nearest.id()) : Fief.march(Fief.side(at.x() - capital.get().x(), at.z() - capital.get().z()));
            result.merge(fief, 1, Integer::sum);
        }
        return result;
    }

    private static long distance(int x, int z, WorldPos pos) {
        long dx = (long) x - pos.x();
        long dz = (long) z - pos.z();
        return dx * dx + dz * dz;
    }

    /**
     * Spies cost their wage each day; a spy the treasury cannot pay leaves.
     */
    static void wages(MinecraftServer server, UUID kingdomId, long day) {
        NobilityState state = state(server);
        long wage = NobilitySettings.rules().spyWage();
        for (Spy spy : state.spiesOf(kingdomId)) {
            if (spy.status() == Spy.Status.CAPTURED || wage <= 0) {
                continue;
            }
            if (!KingdomWorldApi.state(server).economy().nobilityPayment(kingdomId, wage, day).ok()) {
                state.dismiss(spy.id());
                state.report(kingdomId, new Note(day, "report.unpaid", List.of(Note.person(spy.seed(), spy.gender()))));
            }
        }
    }

    // ---------------------------------------------------------------- the day of the world

    private static void worldDay(MinecraftServer server, long day) {
        NobilityState state = state(server);
        NobilityRules rules = NobilitySettings.rules();
        NobilityState.SpyWorld world = new NobilityState.SpyWorld() {
            @Override
            public boolean exists(UUID kingdomId) {
                return KingdomWorldApi.kingdom(server, kingdomId).isPresent();
            }

            @Override
            public boolean npc(UUID kingdomId) {
                return WarWork.isNpc(server, kingdomId);
            }

            @Override
            public boolean played(UUID kingdomId) {
                return NobilityWork.played(server, kingdomId);
            }

            @Override
            public int counter(UUID kingdomId) {
                return state.counter(kingdomId, spymaster(server, kingdomId), rules);
            }

            @Override
            public int relation(UUID a, UUID b) {
                return WarWork.relation(server, a, b);
            }

            @Override
            public boolean atWar(UUID a, UUID b) {
                return WarWork.fighting(server, a, b);
            }

            @Override
            public boolean harsh(UUID kingdomId) {
                return NpcWork.kingdom(server, kingdomId).map(npc -> NobilityMind.harsh(npc.traits())).orElse(false);
            }
        };
        for (Happening happening : state.spiesDay(day, world, rules, rolls())) {
            try {
                NobilitySpies.act(server, happening, day);
            } catch (RuntimeException e) {
                LOGGER.error("The spies could not carry out {}", happening, e);
            }
        }
        state.marriage(day, rules, rolls(), (a, b) -> !WarWork.state(server).warBetween(a, b).isPresent() && WarWork.relation(server, a, b) >= -10)
                .ifPresent(marriage -> {
                    WarWork.state(server).relate(marriage.kingdomId(), marriage.other(), Relations.Reason.MARRIAGE);
                    LOGGER.info("Houses of {} and {} married", WarWork.name(server, marriage.kingdomId()).getString(),
                            WarWork.name(server, marriage.other()).getString());
                });
    }

    /**
     * Food of a kingdom spoiled by a spy: what citizens would eat in a while is gone.
     */
    static int spoil(MinecraftServer server, UUID kingdomId) {
        return KingdomStorage.eat(server, kingdomId, 64, null);
    }

    /**
     * What a ruler of the world knows of a player: how well they dealt and their reputation there.
     */
    static int dealings(MinecraftServer server, UUID kingdomId, UUID player) {
        return NpcWork.state(server).existingRuler(kingdomId).map(memory -> memory.dealings(player)).orElse(0);
    }

    static Map<UUID, Integer> known(MinecraftServer server, UUID kingdomId) {
        return NpcWork.state(server).existingRuler(kingdomId).map(RulerMemory::players).orElse(Map.of());
    }

    static int reputation(MinecraftServer server, UUID kingdomId, UUID player) {
        return LawWork.reputation(server, kingdomId, player);
    }

    static Gender gender(double roll) {
        return roll < 0.3 ? Gender.FEMALE : Gender.MALE;
    }

    static Title titleFor(Fief fief) {
        return fief.kind() == Fief.Kind.CITY ? Title.COUNT : Title.BARON;
    }
}
