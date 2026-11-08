package io.github.sekerator.kingdomworld.core.nobility;

import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.npc.RulerCharacter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.DoubleSupplier;

/**
 * The nobility of a world: the houses of every kingdom with their titles and fiefs, the courts with their parties,
 * stability, demands and plots, and the spies. Every change goes through here; the world (money, rulers, cities, messages)
 * acts on the {@link Happening}s the days bring.
 *
 * <p>Nothing at a court of players moves on a day none of its players was there: demands wait, plots do not ripen, rebels
 * keep nothing and spies sent against it wait at its border.
 */
public final class NobilityState {
    /** Played days a stability must stay low before a house may start to plot. */
    static final int PLOT_LOW_DAYS = 2;
    /** Loyalty below which a house is discontented enough to plot. */
    static final int PLOT_LOYALTY = 40;
    /** Loyalty from which a plotting house gives up. */
    static final int CONTENT_LOYALTY = 55;

    private final Map<Long, NobleHouse> houses = new LinkedHashMap<>();
    private final Map<Long, Grant> grants = new LinkedHashMap<>();
    private final Map<UUID, Court> courts = new LinkedHashMap<>();
    private final Map<Long, Demand> demands = new LinkedHashMap<>();
    private final Map<Long, Plot> plots = new LinkedHashMap<>();
    private final Map<Long, Spy> spies = new LinkedHashMap<>();
    private long nextId = 1;
    private Runnable listener = () -> {
    };

    public void setChangeListener(Runnable value) {
        listener = value == null ? () -> {
        } : value;
    }

    public void changed() {
        listener.run();
    }

    private long nextId() {
        return nextId++;
    }

    public long peekNextId() {
        return nextId;
    }

    // ---------------------------------------------------------------- reading

    public Collection<NobleHouse> houses() {
        return Collections.unmodifiableCollection(houses.values());
    }

    public List<NobleHouse> houses(UUID kingdomId) {
        List<NobleHouse> result = new ArrayList<>();
        for (NobleHouse house : houses.values()) {
            if (house.kingdomId().equals(kingdomId)) {
                result.add(house);
            }
        }
        return result;
    }

    public Optional<NobleHouse> house(long id) {
        return Optional.ofNullable(houses.get(id));
    }

    public Collection<Grant> grants() {
        return Collections.unmodifiableCollection(grants.values());
    }

    public List<Grant> grants(UUID kingdomId) {
        List<Grant> result = new ArrayList<>();
        for (Grant grant : grants.values()) {
            if (grant.kingdomId().equals(kingdomId)) {
                result.add(grant);
            }
        }
        return result;
    }

    public Optional<Grant> grant(long id) {
        return Optional.ofNullable(grants.get(id));
    }

    public List<Grant> grantsOf(NobleHouse house) {
        List<Grant> result = new ArrayList<>();
        for (Grant grant : grants.values()) {
            if (grant.house() == house.id()) {
                result.add(grant);
            }
        }
        return result;
    }

    public Optional<Grant> holderOf(UUID kingdomId, Fief fief) {
        for (Grant grant : grants.values()) {
            if (grant.kingdomId().equals(kingdomId) && grant.fief().equals(fief)) {
                return Optional.of(grant);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a player holds a title of a kingdom: the law spares them, the gates let them in for free.
     */
    public boolean isNoble(UUID kingdomId, UUID player) {
        if (kingdomId == null || player == null) {
            return false;
        }
        for (Grant grant : grants.values()) {
            if (player.equals(grant.player()) && grant.kingdomId().equals(kingdomId)) {
                return true;
            }
        }
        return false;
    }

    public Optional<Court> existingCourt(UUID kingdomId) {
        return Optional.ofNullable(courts.get(kingdomId));
    }

    public Court court(UUID kingdomId) {
        return courts.computeIfAbsent(kingdomId, Court::new);
    }

    public Collection<Court> courts() {
        return Collections.unmodifiableCollection(courts.values());
    }

    public Collection<Demand> demands() {
        return Collections.unmodifiableCollection(demands.values());
    }

    public Optional<Demand> openDemand(UUID kingdomId) {
        for (Demand demand : demands.values()) {
            if (demand.kingdomId().equals(kingdomId)) {
                return Optional.of(demand);
            }
        }
        return Optional.empty();
    }

    public Optional<Demand> demand(long id) {
        return Optional.ofNullable(demands.get(id));
    }

    public Collection<Plot> plots() {
        return Collections.unmodifiableCollection(plots.values());
    }

    public Optional<Plot> plotOf(UUID kingdomId) {
        for (Plot plot : plots.values()) {
            if (plot.kingdomId().equals(kingdomId)) {
                return Optional.of(plot);
            }
        }
        return Optional.empty();
    }

    public Optional<Plot> plot(long id) {
        return Optional.ofNullable(plots.get(id));
    }

    public Collection<Spy> spies() {
        return Collections.unmodifiableCollection(spies.values());
    }

    public List<Spy> spiesOf(UUID owner) {
        List<Spy> result = new ArrayList<>();
        for (Spy spy : spies.values()) {
            if (spy.owner().equals(owner)) {
                result.add(spy);
            }
        }
        return result;
    }

    public Optional<Spy> spy(long id) {
        return Optional.ofNullable(spies.get(id));
    }

    /**
     * The counter-intelligence of a kingdom in percent: a little everywhere, more with spies hunting at home and a
     * spymaster at court.
     */
    public int counter(UUID kingdomId, boolean spymaster, NobilityRules rules) {
        int hunters = 0;
        for (Spy spy : spies.values()) {
            if (spy.owner().equals(kingdomId) && spy.status() == Spy.Status.COUNTER) {
                hunters++;
            }
        }
        return Math.min(80, rules.counterBase() + 10 * hunters + (spymaster ? 15 : 0));
    }

    /**
     * The influence of the parties at a court, in percent: every party has a little; houses with titles and wealth give
     * their party more.
     */
    public Map<Faction, Integer> influence(UUID kingdomId) {
        EnumMap<Faction, Integer> weight = new EnumMap<>(Faction.class);
        for (Faction faction : Faction.values()) {
            weight.put(faction, 10);
        }
        for (NobleHouse house : houses(kingdomId)) {
            int titles = grantsOf(house).size();
            weight.merge(house.faction(), 15 + 10 * titles + (int) Math.min(20, house.wealth() / 500), Integer::sum);
        }
        int total = weight.values().stream().mapToInt(Integer::intValue).sum();
        EnumMap<Faction, Integer> result = new EnumMap<>(Faction.class);
        int given = 0;
        Faction largest = Faction.CROWN;
        for (Faction faction : Faction.values()) {
            int percent = weight.get(faction) * 100 / total;
            result.put(faction, percent);
            given += percent;
            if (weight.get(faction) > weight.get(largest)) {
                largest = faction;
            }
        }
        result.merge(largest, 100 - given, Integer::sum);
        return result;
    }

    /**
     * The content of the parties of a court weighed by their influence, -1 without houses.
     */
    public int content(UUID kingdomId) {
        if (houses(kingdomId).isEmpty()) {
            return -1;
        }
        Court court = court(kingdomId);
        Map<Faction, Integer> influence = influence(kingdomId);
        int sum = 0;
        for (Faction faction : Faction.values()) {
            sum += influence.get(faction) * court.content(faction);
        }
        return sum / 100;
    }

    /**
     * The average loyalty of the houses of a kingdom, -1 without houses.
     */
    public int loyalty(UUID kingdomId) {
        List<NobleHouse> own = houses(kingdomId);
        if (own.isEmpty()) {
            return -1;
        }
        int sum = 0;
        for (NobleHouse house : own) {
            sum += house.loyalty();
        }
        return sum / own.size();
    }

    /**
     * The stability of a kingdom with what its court adds to what the world says.
     */
    public Stability.Result stability(UUID kingdomId, Stability.Inputs world) {
        return Stability.compute(world.withCourt(loyalty(kingdomId), content(kingdomId)), court(kingdomId).modifiers());
    }

    // ---------------------------------------------------------------- houses

    /**
     * A new house of a kingdom, its name, head and character from a seed.
     */
    public NobleHouse found(UUID kingdomId, long seed, long day) {
        SplittableRandom random = new SplittableRandom(seed ^ 0x4E4F424C45L);
        RulerCharacter character = RulerCharacter.values()[random.nextInt(RulerCharacter.values().length)];
        RulerCharacter.Trait trait = RulerCharacter.Trait.values()[random.nextInt(RulerCharacter.Trait.values().length)];
        NobleHouse house = new NobleHouse(nextId(), kingdomId, random.nextInt(), random.nextInt(), random.nextInt(4) == 0 ? Gender.FEMALE : Gender.MALE,
                character, trait, Faction.of(character, trait), 200 + random.nextInt(1_800), 55 + random.nextInt(21), 1, day);
        houses.put(house.id(), house);
        court(kingdomId);
        changed();
        return house;
    }

    /**
     * The houses a kingdom of the world has from the start, with titles on its fiefs: cities make counts, marches barons, and
     * the first house of a large kingdom is a duke.
     */
    public List<NobleHouse> foundCourt(UUID kingdomId, int count, long seed, List<Fief> fiefs, boolean large, long day) {
        List<NobleHouse> made = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            NobleHouse house = found(kingdomId, seed + 7_919L * (i + 1), day);
            made.add(house);
            if (i < fiefs.size()) {
                Fief fief = fiefs.get(i);
                Title title = large && i == 0 ? Title.DUKE : fief.kind() == Fief.Kind.CITY ? Title.COUNT : Title.BARON;
                Grant grant = new Grant(nextId(), kingdomId, title, fief, house.id(), null, day);
                grants.put(grant.id(), grant);
            }
        }
        changed();
        return made;
    }

    /**
     * The head of a house dies; the heir takes over with a character of their own.
     */
    public void succeed(NobleHouse house, long seed) {
        SplittableRandom random = new SplittableRandom(seed ^ 0x484549524CL);
        RulerCharacter character = RulerCharacter.values()[random.nextInt(RulerCharacter.values().length)];
        RulerCharacter.Trait trait = RulerCharacter.Trait.values()[random.nextInt(RulerCharacter.Trait.values().length)];
        house.succeed(random.nextInt(), random.nextInt(4) == 0 ? Gender.FEMALE : Gender.MALE, character, trait);
        changed();
    }

    // ---------------------------------------------------------------- titles

    /**
     * Grants a title with a fief to a house of the kingdom or to a player. The fief must be free; a player holds one title
     * of a kingdom at most.
     */
    public Outcome grant(UUID kingdomId, Title title, Fief fief, long house, UUID player, long day) {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(fief, "fief");
        if (holderOf(kingdomId, fief).isPresent()) {
            return Outcome.fail(KingdomError.FIEF_TAKEN);
        }
        Court court = court(kingdomId);
        if (player != null) {
            if (isNoble(kingdomId, player)) {
                return Outcome.fail(KingdomError.ALREADY_NOBLE);
            }
            Grant grant = new Grant(nextId(), kingdomId, title, fief, 0, player, day);
            grants.put(grant.id(), grant);
            court.changeContent(Faction.OLD_NOBILITY, -5);
            court.write(new Note(day, "granted_player", List.of(Note.player(player), Note.title(title), Note.fief(fief))));
        } else {
            NobleHouse holder = houses.get(house);
            if (holder == null || !holder.kingdomId().equals(kingdomId)) {
                return Outcome.fail(KingdomError.NO_SUCH_NOBLE);
            }
            Grant grant = new Grant(nextId(), kingdomId, title, fief, house, null, day);
            grants.put(grant.id(), grant);
            holder.changeLoyalty(15);
            court.changeContent(Faction.OLD_NOBILITY, 5);
            court.write(new Note(day, "granted", List.of(Note.house(holder), Note.title(title), Note.fief(fief))));
        }
        court.modify("grant", 3, day + 3);
        changed();
        return Outcome.DONE;
    }

    /**
     * Takes a title back to the crown. The house is offended, and the others take note.
     */
    public Optional<Grant> revoke(long grantId, long day) {
        Grant grant = grants.remove(grantId);
        if (grant == null) {
            return Optional.empty();
        }
        Court court = court(grant.kingdomId());
        if (grant.heldByPlayer()) {
            court.write(new Note(day, "revoked_player", List.of(Note.player(grant.player()), Note.title(grant.title()), Note.fief(grant.fief()))));
        } else {
            NobleHouse holder = houses.get(grant.house());
            if (holder != null) {
                holder.changeLoyalty(-25);
                for (NobleHouse other : houses(grant.kingdomId())) {
                    if (other != holder) {
                        other.changeLoyalty(-5);
                    }
                }
                court.write(new Note(day, "revoked", List.of(Note.house(holder), Note.title(grant.title()), Note.fief(grant.fief()))));
            }
            court.modify("revoked", -4, day + 4);
        }
        changed();
        return Optional.of(grant);
    }

    /**
     * Hands a title with its fief to another house or player of the kingdom.
     */
    public Outcome transfer(long grantId, long house, UUID player, long day) {
        Grant grant = grants.get(grantId);
        if (grant == null) {
            return Outcome.fail(KingdomError.NO_SUCH_NOBLE);
        }
        if (player != null && isNoble(grant.kingdomId(), player)) {
            return Outcome.fail(KingdomError.ALREADY_NOBLE);
        }
        if (player == null) {
            NobleHouse holder = houses.get(house);
            if (holder == null || !holder.kingdomId().equals(grant.kingdomId())) {
                return Outcome.fail(KingdomError.NO_SUCH_NOBLE);
            }
            if (grant.house() == house) {
                return Outcome.fail(KingdomError.UNCHANGED);
            }
        }
        revoke(grantId, day);
        return grant(grant.kingdomId(), grant.title(), grant.fief(), player == null ? house : 0, player, day);
    }

    // ---------------------------------------------------------------- the day of a court

    /**
     * What a court needs to know of its kingdom for a demand.
     *
     * @param enemy      a kingdom it is at war with, or {@code null}
     * @param rival      the neighbour it likes least and could fight, or {@code null}
     * @param loot       what the kingdom took in wars lately
     * @param taxPercent its poll tax against the usual one, in percent
     * @param freeFief   whether a fief is free for a new title
     */
    public record Context(UUID enemy, UUID rival, long loot, int taxPercent, boolean freeFief) {
        public static final Context NONE = new Context(null, null, 0, 100, false);
    }

    /**
     * A day of a kingdom.
     *
     * @param npc     whether a ruler of the world rules it
     * @param played  whether one of its players was there today (always for a kingdom of the world)
     * @param counter its counter-intelligence in percent
     */
    public record DayInput(UUID kingdomId, boolean npc, boolean played, long day, Stability.Inputs inputs, int counter, Context context) {
        public DayInput {
            Objects.requireNonNull(kingdomId, "kingdomId");
            Objects.requireNonNull(inputs, "inputs");
            Objects.requireNonNull(context, "context");
        }
    }

    /**
     * A game day of one court: the stability of today; then, on a day the kingdom is played, the parties and houses settle,
     * demands come and lapse, plots start, ripen, are found out or strike, and heads of houses die.
     */
    public List<Happening> day(DayInput in, NobilityRules rules, DoubleSupplier rolls) {
        List<Happening> out = new ArrayList<>();
        UUID kingdomId = in.kingdomId();
        Court court = court(kingdomId);
        court.expire(in.day());
        court.setStability(stability(kingdomId, in.inputs()));
        if (!in.played() || court.lastPlayed() >= in.day()) {
            changed();
            return out;
        }
        court.played(in.day());
        long played = court.playedDays();
        int value = court.stability().value();
        List<NobleHouse> own = houses(kingdomId);
        settle(court, own, value, in.context());
        if (value < rules.plotBelow()) {
            court.setLow(court.lowDays() + 1, court.warned());
        } else if (value >= rules.plotBelow() + 10) {
            court.setLow(0, false);
        }
        if (court.rebelDays() > 0) {
            out.add(new Happening(Happening.Kind.REBELS, kingdomId, court.rebelHouse(), 0, null, court.rebelDays()));
            court.rebels(court.rebelDays() - 1, court.rebelHouse());
        }
        demands(in, rules, rolls, court, own, played, out);
        plots(in, rules, rolls, court, own, value, out);
        if (!in.npc() && !court.warned() && court.lowDays() >= rules.secessionWarnDays()) {
            for (NobleHouse house : own) {
                if (house.loyalty() < CONTENT_LOYALTY && grantsOf(house).stream().anyMatch(g -> g.fief().kind() == Fief.Kind.CITY)) {
                    court.setLow(court.lowDays(), true);
                    court.write(new Note(in.day(), "warning", List.of(Note.house(house))));
                    out.add(new Happening(Happening.Kind.SECESSION_WARNING, kingdomId, house.id(), 0, null, 0));
                    break;
                }
            }
        }
        for (NobleHouse house : own) {
            if (houses.containsKey(house.id()) && rolls.getAsDouble() < rules.deathChance()) {
                String old = Note.head(house);
                succeed(house, (long) (rolls.getAsDouble() * Long.MAX_VALUE));
                court.write(new Note(in.day(), "died", List.of(Note.house(house), old, Note.head(house))));
                out.add(new Happening(Happening.Kind.HEAD_DIED, kingdomId, house.id(), 0, null, 0));
            }
        }
        changed();
        return out;
    }

    /**
     * Parties drift back to the middle; a war pleases the war party and worries the merchants; houses lean to the mood of
     * the kingdom, titled houses and houses of a content party more.
     */
    private void settle(Court court, List<NobleHouse> own, int stability, Context context) {
        for (Faction faction : Faction.values()) {
            int content = court.content(faction);
            court.changeContent(faction, content < 50 ? 1 : content > 50 ? -1 : 0);
        }
        if (context.enemy() != null) {
            court.changeContent(Faction.WAR, 2);
            court.changeContent(Faction.MERCHANTS, -2);
        }
        if (context.taxPercent() > 120) {
            court.changeContent(Faction.MERCHANTS, -1);
        }
        for (NobleHouse house : own) {
            int target = 30 + stability / 2 + (grantsOf(house).isEmpty() ? 0 : 10) + (court.content(house.faction()) - 50) / 5;
            int difference = target - house.loyalty();
            house.changeLoyalty(Integer.signum(difference) * Math.min(3, Math.abs(difference)));
        }
    }

    private void demands(DayInput in, NobilityRules rules, DoubleSupplier rolls, Court court, List<NobleHouse> own, long played,
                         List<Happening> out) {
        Optional<Demand> open = openDemand(in.kingdomId());
        if (open.isPresent()) {
            if (played >= open.get().due()) {
                Demand demand = open.get();
                answer(demand.id(), Demand.Answer.LAPSED, in.day());
                out.add(new Happening(Happening.Kind.DEMAND_LAPSED, in.kingdomId(), demand.house(), demand.id(), demand.target(), demand.kind().ordinal()));
            }
            return;
        }
        if (own.isEmpty() || played - court.lastDemand() < rules.demandEveryDays()) {
            return;
        }
        Optional<NobilityMind.Ask> ask = NobilityMind.demand(court, own, this, in.context(), rules, rolls);
        if (ask.isEmpty()) {
            return;
        }
        Demand demand = new Demand(nextId(), in.kingdomId(), ask.get().faction(), ask.get().house(), ask.get().kind(), ask.get().target(),
                ask.get().value(), played, played + rules.demandDueDays());
        demands.put(demand.id(), demand);
        court.demanded(played);
        court.write(new Note(in.day(), "demand", List.of(Note.house(houses.get(demand.house())), "x" + demand.kind().id())));
        out.add(new Happening(Happening.Kind.DEMAND_MADE, in.kingdomId(), demand.house(), demand.id(), demand.target(), demand.kind().ordinal()));
    }

    private void plots(DayInput in, NobilityRules rules, DoubleSupplier rolls, Court court, List<NobleHouse> own, int value,
                       List<Happening> out) {
        UUID kingdomId = in.kingdomId();
        Optional<Plot> existing = plotOf(kingdomId);
        if (existing.isEmpty()) {
            if (court.lowDays() < PLOT_LOW_DAYS || value >= rules.plotBelow()) {
                return;
            }
            NobleHouse angriest = null;
            for (NobleHouse house : own) {
                if (house.loyalty() < PLOT_LOYALTY && (angriest == null || house.loyalty() < angriest.loyalty())) {
                    angriest = house;
                }
            }
            if (angriest != null && rolls.getAsDouble() < rules.plotChance()) {
                Plot plot = new Plot(nextId(), kingdomId, angriest.id(), 0, rules.plotDays(), Plot.Status.BREWING, null, in.day());
                plots.put(plot.id(), plot);
                out.add(new Happening(Happening.Kind.PLOT_STARTED, kingdomId, angriest.id(), plot.id(), null, 0));
            }
            return;
        }
        Plot plot = existing.get();
        if (plot.status() != Plot.Status.BREWING) {
            return;
        }
        NobleHouse house = houses.get(plot.house());
        if (house == null || !house.kingdomId().equals(kingdomId) || house.loyalty() >= CONTENT_LOYALTY) {
            plots.remove(plot.id());
            out.add(new Happening(Happening.Kind.PLOT_FIZZLED, kingdomId, plot.house(), plot.id(), null, 0));
            return;
        }
        plot.advance(1);
        if (rolls.getAsDouble() < in.counter() / 200.0) {
            plot.discover();
            court.write(new Note(in.day(), "plot_found", List.of(Note.house(house))));
            out.add(new Happening(Happening.Kind.PLOT_DISCOVERED, kingdomId, house.id(), plot.id(), plot.sponsor().orElse(null), 0));
            return;
        }
        if (!plot.ripe()) {
            return;
        }
        plots.remove(plot.id());
        if (in.npc()) {
            house.setLoyalty(90);
            for (NobleHouse other : own) {
                if (other != house) {
                    other.changeLoyalty(-5);
                }
            }
            court.modify("new_ruler", 5, in.day() + 5);
            court.setLow(0, false);
            court.write(new Note(in.day(), "overthrown", List.of(Note.house(house), Note.head(house))));
            out.add(new Happening(Happening.Kind.RULER_OVERTHROWN, kingdomId, house.id(), plot.id(), plot.sponsor().orElse(null), 0));
            return;
        }
        Optional<Grant> city = grantsOf(house).stream().filter(g -> g.fief().kind() == Fief.Kind.CITY).findFirst();
        if (city.isPresent() && court.warned() && court.lowDays() >= rules.secessionDays()) {
            court.write(new Note(in.day(), "seceded", List.of(Note.house(house), Note.fief(city.get().fief()))));
            out.add(new Happening(Happening.Kind.SECESSION, kingdomId, house.id(), plot.id(), plot.sponsor().orElse(null), city.get().fief().city()));
            return;
        }
        court.rebels(rules.rebelDays(), house.id());
        court.modify("rebels", -5, in.day() + 3);
        court.write(new Note(in.day(), "rebels", List.of(Note.house(house))));
        out.add(new Happening(Happening.Kind.REBELS, kingdomId, house.id(), plot.id(), plot.sponsor().orElse(null), rules.rebelDays()));
    }

    // ---------------------------------------------------------------- answers and verdicts

    /**
     * The crown answers a demand. The world has already done what an agreement or a bargain costs.
     */
    public Outcome answer(long demandId, Demand.Answer answer, long day) {
        Demand demand = demands.get(demandId);
        if (demand == null) {
            return Outcome.fail(KingdomError.NOT_PENDING);
        }
        demands.remove(demandId);
        Court court = court(demand.kingdomId());
        NobleHouse house = houses.get(demand.house());
        int content;
        int loyalty;
        switch (answer) {
            case ACCEPT -> {
                content = 20;
                loyalty = 10;
                court.modify("demand_met", 4, day + 3);
                switch (demand.kind()) {
                    case DECLARE_WAR -> court.changeContent(Faction.MERCHANTS, -10);
                    case MAKE_PEACE -> court.changeContent(Faction.WAR, -10);
                    case LOWER_TAX, GRANT_TITLE -> court.changeContent(Faction.CROWN, -5);
                    default -> {
                    }
                }
            }
            case BARGAIN -> {
                content = -10;
                loyalty = -5;
                court.modify("demand_bargained", -3, day + 3);
            }
            case REFUSE -> {
                content = -20;
                loyalty = -10;
                court.modify("demand_refused", -6, day + 4);
            }
            default -> {
                content = -10;
                loyalty = -5;
                court.modify("demand_ignored", -3, day + 3);
            }
        }
        court.changeContent(demand.faction(), content);
        if (house != null) {
            house.changeLoyalty(loyalty);
            court.write(new Note(day, "answer." + answer.id(), List.of(Note.house(house), "x" + demand.kind().id())));
        }
        changed();
        return Outcome.DONE;
    }

    /**
     * The crown decides the fate of a house whose plot was found out.
     *
     * @param seed the seed of the heir when the head is put to death
     */
    public Outcome verdict(long plotId, Plot.Verdict verdict, long day, long seed) {
        Plot plot = plots.get(plotId);
        if (plot == null || plot.status() != Plot.Status.DISCOVERED) {
            return Outcome.fail(KingdomError.NOT_PENDING);
        }
        plots.remove(plotId);
        Court court = court(plot.kingdomId());
        NobleHouse house = houses.get(plot.house());
        if (house == null) {
            changed();
            return Outcome.DONE;
        }
        switch (verdict) {
            case EXECUTE -> {
                String old = Note.head(house);
                succeed(house, seed);
                house.setLoyalty(30);
                for (NobleHouse other : houses(plot.kingdomId())) {
                    if (other != house) {
                        other.changeLoyalty(-5);
                    }
                }
                court.changeContent(house.faction(), -10);
                court.modify("execution", -4, day + 4);
                court.write(new Note(day, "verdict.execute", List.of(Note.house(house), old)));
            }
            case PARDON -> {
                house.changeLoyalty(25);
                court.modify("mercy", 2, day + 3);
                court.write(new Note(day, "verdict.pardon", List.of(Note.house(house))));
            }
            case EXILE -> {
                court.write(new Note(day, "verdict.exile", List.of(Note.house(house))));
                Faction faction = house.faction();
                removeHouse(house.id());
                for (NobleHouse other : houses(plot.kingdomId())) {
                    if (other.faction() == faction) {
                        other.changeLoyalty(-5);
                    }
                }
                court.changeContent(faction, -10);
                court.modify("exile", -3, day + 4);
            }
        }
        changed();
        return Outcome.DONE;
    }

    /**
     * Removes a house with its titles (they go back to the crown), its demand and its plot.
     */
    public Optional<NobleHouse> removeHouse(long houseId) {
        NobleHouse house = houses.remove(houseId);
        if (house == null) {
            return Optional.empty();
        }
        grants.values().removeIf(grant -> grant.house() == houseId);
        demands.values().removeIf(demand -> demand.house() == houseId);
        plots.values().removeIf(plot -> plot.house() == houseId);
        changed();
        return Optional.of(house);
    }

    /**
     * A city left a kingdom with the house that held it: the title on the city ends, the house goes with it to the kingdom
     * that has it now, and the crown it left is told.
     */
    public void seceded(UUID from, UUID to, long houseId, long city, long day) {
        grants.values().removeIf(grant -> grant.kingdomId().equals(from) && grant.fief().equals(Fief.city(city)));
        NobleHouse house = houses.get(houseId);
        if (house != null && to != null) {
            grants.values().removeIf(grant -> grant.house() == houseId);
            demands.values().removeIf(demand -> demand.house() == houseId);
            plots.values().removeIf(plot -> plot.house() == houseId);
            house.moveTo(to);
            house.setLoyalty(80);
            court(to).write(new Note(day, "joined", List.of(Note.house(house), Note.kingdom(from))));
        }
        court(from).setLow(0, false);
        court(from).modify("secession", -5, day + 5);
        changed();
    }

    // ---------------------------------------------------------------- spies

    public Spy hire(UUID owner, int seed, Gender gender, int skill, long day) {
        Spy spy = new Spy(nextId(), owner, seed, gender, skill, Spy.Status.IDLE, null, null, 0, 0, day);
        spies.put(spy.id(), spy);
        changed();
        return spy;
    }

    public Optional<Spy> dismiss(long spyId) {
        Spy spy = spies.remove(spyId);
        if (spy != null) {
            changed();
        }
        return Optional.ofNullable(spy);
    }

    /**
     * Sends a spy at home on a mission abroad.
     */
    public Outcome send(long spyId, Spy.Task task, UUID target, long house, long day) {
        Spy spy = spies.get(spyId);
        if (spy == null) {
            return Outcome.fail(KingdomError.NO_SUCH_NOBLE);
        }
        if (spy.status() == Spy.Status.MISSION || spy.status() == Spy.Status.CAPTURED || target == null || target.equals(spy.owner())) {
            return Outcome.fail(KingdomError.SPY_BUSY);
        }
        if (task == Spy.Task.ASSASSINATE) {
            NobleHouse aim = houses.get(house);
            if (aim == null || !aim.kingdomId().equals(target)) {
                return Outcome.fail(KingdomError.NO_SUCH_NOBLE);
            }
        }
        spy.send(new Spy.Mission(task, target, task == Spy.Task.ASSASSINATE ? house : 0, day, day + task.days()));
        changed();
        return Outcome.DONE;
    }

    /**
     * Calls a spy home from a mission, or sets a spy at home to hunt foreign spies or to rest.
     */
    public Outcome station(long spyId, boolean counter) {
        Spy spy = spies.get(spyId);
        if (spy == null) {
            return Outcome.fail(KingdomError.NO_SUCH_NOBLE);
        }
        if (spy.status() == Spy.Status.CAPTURED) {
            return Outcome.fail(KingdomError.SPY_BUSY);
        }
        spy.home(counter);
        changed();
        return Outcome.DONE;
    }

    /**
     * A caught spy comes home; the world has paid the ransom.
     */
    public Outcome ransomed(long spyId, long day) {
        Spy spy = spies.get(spyId);
        if (spy == null || spy.status() != Spy.Status.CAPTURED) {
            return Outcome.fail(KingdomError.NOT_PENDING);
        }
        court(spy.owner()).report(new Note(day, "report.ransomed", List.of(Note.person(spy.seed(), spy.gender()), Note.kingdom(spy.captor().orElseThrow()))));
        spy.home(false);
        changed();
        return Outcome.DONE;
    }

    /**
     * What the spies need to know of the world.
     */
    public interface SpyWorld {
        boolean exists(UUID kingdomId);

        boolean npc(UUID kingdomId);

        /** Whether a player of a kingdom of players was there today. */
        boolean played(UUID kingdomId);

        int counter(UUID kingdomId);

        int relation(UUID a, UUID b);

        boolean atWar(UUID a, UUID b);

        /** Whether a kingdom puts caught spies to death at once. */
        boolean harsh(UUID kingdomId);
    }

    /**
     * A game day of the spies: missions that end, spies caught on the way, prisoners whose ransom did not come.
     */
    public List<Happening> spiesDay(long day, SpyWorld world, NobilityRules rules, DoubleSupplier rolls) {
        List<Happening> out = new ArrayList<>();
        for (Spy spy : List.copyOf(spies.values())) {
            if (!world.exists(spy.owner())) {
                spies.remove(spy.id());
                continue;
            }
            if (spy.status() == Spy.Status.CAPTURED) {
                UUID captor = spy.captor().orElseThrow();
                if (day >= spy.releaseBy() || !world.exists(captor)) {
                    spies.remove(spy.id());
                    court(spy.owner()).report(new Note(day, "report.executed", List.of(Note.person(spy.seed(), spy.gender()), Note.kingdom(captor))));
                    out.add(new Happening(Happening.Kind.SPY_EXECUTED, spy.owner(), 0, spy.id(), captor, 0));
                }
                continue;
            }
            if (spy.status() != Spy.Status.MISSION) {
                continue;
            }
            Spy.Mission mission = spy.mission().orElseThrow();
            UUID target = mission.target();
            if (!world.exists(target)) {
                spy.home(false);
                continue;
            }
            if (mission.task().hostile() && !world.npc(target) && !world.played(target)) {
                // Nobody of that kingdom is there: the spy waits at its border.
                continue;
            }
            int counter = world.counter(target);
            if (day < mission.ends()) {
                if (mission.task().hostile() && rolls.getAsDouble() < counter / 1000.0) {
                    caught(spy, target, day, world, rules, out);
                }
                continue;
            }
            int bonus = world.atWar(spy.owner(), target) ? -10 : world.relation(spy.owner(), target) >= 30 ? 5 : 0;
            int chance = Math.max(5, Math.min(95, mission.task().chance() + (int) Math.round((spy.skill() - 50) * 0.6) - counter / 2 + bonus));
            if (rolls.getAsDouble() * 100 < chance) {
                done(spy, mission, day, world);
                spy.learn(3);
                spy.home(false);
                out.add(new Happening(Happening.Kind.SPY_DONE, spy.owner(), mission.house(), spy.id(), target, mission.task().ordinal()));
            } else if (rolls.getAsDouble() < rules.caughtChance()) {
                caught(spy, target, day, world, rules, out);
            } else {
                spy.learn(1);
                spy.home(false);
                court(spy.owner()).report(new Note(day, "report.failed", List.of(Note.person(spy.seed(), spy.gender()), Note.kingdom(target),
                        "x" + mission.task().id())));
                out.add(new Happening(Happening.Kind.SPY_FAILED, spy.owner(), 0, spy.id(), target, mission.task().ordinal()));
            }
        }
        changed();
        return out;
    }

    private void caught(Spy spy, UUID captor, long day, SpyWorld world, NobilityRules rules, List<Happening> out) {
        court(captor).write(new Note(day, "spy_caught", List.of(Note.person(spy.seed(), spy.gender()), Note.kingdom(spy.owner()))));
        if (world.harsh(captor)) {
            spies.remove(spy.id());
            court(spy.owner()).report(new Note(day, "report.executed", List.of(Note.person(spy.seed(), spy.gender()), Note.kingdom(captor))));
            out.add(new Happening(Happening.Kind.SPY_EXECUTED, spy.owner(), 0, spy.id(), captor, 1));
            return;
        }
        spy.capture(captor, rules.ransom(), day + rules.ransomDays());
        court(spy.owner()).report(new Note(day, "report.caught", List.of(Note.person(spy.seed(), spy.gender()), Note.kingdom(captor),
                Note.number(rules.ransom()))));
        out.add(new Happening(Happening.Kind.SPY_CAUGHT, spy.owner(), 0, spy.id(), captor, 0));
    }

    /**
     * What a mission does to the nobility of the target; money, stores and reports are the world's part.
     */
    private void done(Spy spy, Spy.Mission mission, long day, SpyWorld world) {
        UUID target = mission.target();
        Court court = court(target);
        switch (mission.task()) {
            case INCITE -> {
                court.modify("incited", -8, day + 3);
                Optional<Plot> plot = plotOf(target).filter(p -> p.status() == Plot.Status.BREWING);
                if (plot.isPresent()) {
                    plot.get().advance(1);
                    plot.get().backBy(spy.owner());
                } else {
                    houses(target).stream().min(Comparator.comparingInt(NobleHouse::loyalty)).ifPresent(house -> house.changeLoyalty(-8));
                }
            }
            case ASSASSINATE -> {
                NobleHouse house = houses.get(mission.house());
                if (house != null && house.kingdomId().equals(target) && world.npc(target)) {
                    String old = Note.head(house);
                    succeed(house, (long) spy.seed() * 31 + day);
                    house.changeLoyalty(-10);
                    court.modify("murder", -5, day + 3);
                    court.write(new Note(day, "murdered", List.of(Note.house(house), old)));
                }
            }
            default -> {
            }
        }
    }

    /**
     * Perhaps two houses of two kingdoms on good terms marry today.
     */
    public Optional<Happening> marriage(long day, NobilityRules rules, DoubleSupplier rolls, BiPredicate<UUID, UUID> friendly) {
        if (houses.size() < 2 || rolls.getAsDouble() >= rules.marriageChance()) {
            return Optional.empty();
        }
        List<NobleHouse> all = new ArrayList<>(houses.values());
        for (int attempt = 0; attempt < 8; attempt++) {
            NobleHouse a = all.get((int) (rolls.getAsDouble() * all.size()) % all.size());
            NobleHouse b = all.get((int) (rolls.getAsDouble() * all.size()) % all.size());
            if (a.kingdomId().equals(b.kingdomId()) || !friendly.test(a.kingdomId(), b.kingdomId())) {
                continue;
            }
            a.changeLoyalty(5);
            b.changeLoyalty(5);
            court(a.kingdomId()).write(new Note(day, "married", List.of(Note.house(a), Note.house(b), Note.kingdom(b.kingdomId()))));
            court(b.kingdomId()).write(new Note(day, "married", List.of(Note.house(b), Note.house(a), Note.kingdom(a.kingdomId()))));
            changed();
            return Optional.of(new Happening(Happening.Kind.MARRIAGE, a.kingdomId(), a.id(), b.id(), b.kingdomId(), 0));
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- kingdoms that are gone

    /**
     * A kingdom is no more: its houses, titles, court, demands and plots go; its spies are lost and missions against it end.
     */
    public void forget(UUID kingdomId) {
        houses.values().removeIf(house -> house.kingdomId().equals(kingdomId));
        grants.values().removeIf(grant -> grant.kingdomId().equals(kingdomId) || (grant.house() > 0 && !houses.containsKey(grant.house())));
        courts.remove(kingdomId);
        demands.values().removeIf(demand -> demand.kingdomId().equals(kingdomId));
        plots.values().removeIf(plot -> plot.kingdomId().equals(kingdomId));
        spies.values().removeIf(spy -> spy.owner().equals(kingdomId));
        for (Spy spy : spies.values()) {
            if (spy.mission().map(m -> m.target().equals(kingdomId)).orElse(false)) {
                spy.home(false);
            }
        }
        changed();
    }

    /**
     * Titles of players who are no members of anything and of cities that are gone are ended by the world through
     * {@link #revoke}; this drops plots and demands whose house is gone, after a load.
     */
    public List<String> verify() {
        List<String> problems = new ArrayList<>();
        grants.values().removeIf(grant -> grant.house() > 0 && !houses.containsKey(grant.house()));
        demands.values().removeIf(demand -> !houses.containsKey(demand.house()));
        plots.values().removeIf(plot -> !houses.containsKey(plot.house()));
        for (Spy spy : spies.values()) {
            if (spy.status() == Spy.Status.MISSION && spy.mission().isEmpty()) {
                problems.add("spy " + spy.id() + " on a mission without one");
            }
        }
        return problems;
    }

    // ---------------------------------------------------------------- operators and tests

    public void adminLoyalty(NobleHouse house, int value) {
        house.setLoyalty(value);
        changed();
    }

    public void adminContent(UUID kingdomId, Faction faction, int value) {
        Court court = court(kingdomId);
        court.changeContent(faction, value - court.content(faction));
        changed();
    }

    public void adminModifier(UUID kingdomId, String cause, int amount, long until) {
        court(kingdomId).modify(cause, amount, until);
        changed();
    }

    public void adminLow(UUID kingdomId, int days, boolean warned) {
        court(kingdomId).setLow(days, warned);
        changed();
    }

    /**
     * Starts a plot of a house at once, as far along as asked.
     */
    public Plot adminPlot(NobleHouse house, int progress, int needed, UUID sponsor, long day) {
        plots.values().removeIf(plot -> plot.kingdomId().equals(house.kingdomId()));
        Plot plot = new Plot(nextId(), house.kingdomId(), house.id(), progress, needed, Plot.Status.BREWING, sponsor, day);
        plots.put(plot.id(), plot);
        changed();
        return plot;
    }

    public void adminDiscover(Plot plot) {
        plot.discover();
        changed();
    }

    /**
     * Makes a demand at once, whatever the days.
     */
    public Demand adminDemand(NobleHouse house, Demand.Kind kind, UUID target, long value, int dueDays) {
        demands.values().removeIf(demand -> demand.kingdomId().equals(house.kingdomId()));
        Court court = court(house.kingdomId());
        Demand demand = new Demand(nextId(), house.kingdomId(), house.faction(), house.id(), kind, target, value, court.playedDays(),
                court.playedDays() + dueDays);
        demands.put(demand.id(), demand);
        changed();
        return demand;
    }

    /**
     * Lets the next day of a court count again, for operators who move days on.
     */
    public void adminReplay(UUID kingdomId) {
        Court court = court(kingdomId);
        court.restore(court.contents(), court.playedDays(), Long.MIN_VALUE, court.lowDays(), court.warned(), court.lastDemand(),
                court.lastPlayerGrant(), court.rebelDays(), court.rebelHouse(), court.modifiers(), court.chronicle(), court.reports(),
                court.stability().value());
        changed();
    }

    public void report(UUID kingdomId, Note note) {
        court(kingdomId).report(note);
        changed();
    }

    public void write(UUID kingdomId, Note note) {
        court(kingdomId).write(note);
        changed();
    }

    public void addWealth(NobleHouse house, long amount) {
        house.addWealth(amount);
        changed();
    }

    // ---------------------------------------------------------------- loading

    public void restoreHouse(NobleHouse house) {
        houses.put(house.id(), house);
        nextId = Math.max(nextId, house.id() + 1);
    }

    public void restoreGrant(Grant grant) {
        grants.put(grant.id(), grant);
        nextId = Math.max(nextId, grant.id() + 1);
    }

    public void restoreDemand(Demand demand) {
        demands.put(demand.id(), demand);
        nextId = Math.max(nextId, demand.id() + 1);
    }

    public void restorePlot(Plot plot) {
        plots.put(plot.id(), plot);
        nextId = Math.max(nextId, plot.id() + 1);
    }

    public void restoreSpy(Spy spy) {
        spies.put(spy.id(), spy);
        nextId = Math.max(nextId, spy.id() + 1);
    }

    public void restoreNextId(long value) {
        nextId = Math.max(nextId, value);
    }
}
