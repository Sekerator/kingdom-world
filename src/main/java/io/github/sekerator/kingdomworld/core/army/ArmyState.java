package io.github.sekerator.kingdomworld.core.army;

import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.trade.Caravan;
import io.github.sekerator.kingdomworld.core.trade.Deal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/**
 * The armies of one world: soldiers among the citizens, squads and their orders, mercenaries on offer and hired, and the
 * barracks where soldiers train.
 *
 * <p>Every operation checks first and then changes everything it changes, or refuses with a reason and changes nothing.
 * Money moves only from a treasury out of the world (the pay of mercenaries), through {@code Economy.armyPayment}; a band
 * that is not paid leaves. Soldiers are citizens: their life, health and gear stay with the citizen; dismissing one makes
 * an unemployed citizen again.
 *
 * <p>Not thread safe; the server uses it from the server thread only.
 */
public final class ArmyState {
    private final Map<UUID, Soldier> soldiers = new LinkedHashMap<>();
    private final Map<Long, ArmySquad> squads = new LinkedHashMap<>();
    private final Map<Long, MercenaryBand> bands = new LinkedHashMap<>();
    private final Map<UUID, Set<WorldPos>> barracks = new LinkedHashMap<>();
    private final Map<UUID, Long> offersDay = new LinkedHashMap<>();
    private long nextSquad = 1;
    private long nextBand = 1;
    private Runnable changeListener = () -> {
    };

    public void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
    }

    private void changed() {
        changeListener.run();
    }

    // ---------------------------------------------------------------- reading

    public Optional<Soldier> soldier(UUID citizenId) {
        return Optional.ofNullable(soldiers.get(citizenId));
    }

    public Collection<Soldier> soldiers() {
        return Collections.unmodifiableCollection(soldiers.values());
    }

    public List<Soldier> soldiersOf(UUID kingdomId) {
        List<Soldier> result = new ArrayList<>();
        for (Soldier soldier : soldiers.values()) {
            if (soldier.kingdomId().equals(kingdomId)) {
                result.add(soldier);
            }
        }
        return result;
    }

    public Optional<ArmySquad> squad(long id) {
        return Optional.ofNullable(squads.get(id));
    }

    public Collection<ArmySquad> squads() {
        return Collections.unmodifiableCollection(squads.values());
    }

    public List<ArmySquad> squadsOf(UUID kingdomId) {
        List<ArmySquad> result = new ArrayList<>();
        for (ArmySquad squad : squads.values()) {
            if (squad.kingdomId().equals(kingdomId)) {
                result.add(squad);
            }
        }
        return result;
    }

    /**
     * The squad a soldier serves in.
     */
    public Optional<ArmySquad> squadOf(UUID citizenId) {
        for (ArmySquad squad : squads.values()) {
            if (squad.members().contains(citizenId)) {
                return Optional.of(squad);
            }
        }
        return Optional.empty();
    }

    /**
     * The squad a hired band is.
     */
    public Optional<ArmySquad> squadOfBand(long bandId) {
        for (ArmySquad squad : squads.values()) {
            if (squad.band() == bandId) {
                return Optional.of(squad);
            }
        }
        return Optional.empty();
    }

    public Optional<MercenaryBand> band(long id) {
        return Optional.ofNullable(bands.get(id));
    }

    public Collection<MercenaryBand> bands() {
        return Collections.unmodifiableCollection(bands.values());
    }

    public List<MercenaryBand> offersOf(UUID kingdomId) {
        List<MercenaryBand> result = new ArrayList<>();
        for (MercenaryBand band : bands.values()) {
            if (band.kingdomId().equals(kingdomId) && !band.hired()) {
                result.add(band);
            }
        }
        return result;
    }

    public List<MercenaryBand> hiredOf(UUID kingdomId) {
        List<MercenaryBand> result = new ArrayList<>();
        for (MercenaryBand band : bands.values()) {
            if (band.kingdomId().equals(kingdomId) && band.hired()) {
                result.add(band);
            }
        }
        return result;
    }

    /**
     * Mercenaries a kingdom has hired.
     */
    public int mercenaryCount(UUID kingdomId) {
        int count = 0;
        for (MercenaryBand band : hiredOf(kingdomId)) {
            count += band.fighters().size();
        }
        return count;
    }

    /**
     * What the hired bands of a kingdom cost a day.
     */
    public long mercenaryCost(UUID kingdomId) {
        long sum = 0;
        for (MercenaryBand band : hiredOf(kingdomId)) {
            sum = Money.addCapped(sum, band.dailyPrice());
        }
        return sum;
    }

    /**
     * The squads that escort the caravans of a deal.
     */
    public List<ArmySquad> escortsOf(long dealId) {
        List<ArmySquad> result = new ArrayList<>();
        for (ArmySquad squad : squads.values()) {
            if (squad.order().kind() == Order.Kind.ESCORT && squad.order().deal() == dealId) {
                result.add(squad);
            }
        }
        return result;
    }

    public Set<WorldPos> barracksOf(UUID kingdomId) {
        return Collections.unmodifiableSet(barracks.getOrDefault(kingdomId, Set.of()));
    }

    public Map<UUID, Set<WorldPos>> barracks() {
        return Collections.unmodifiableMap(barracks);
    }

    /**
     * The game day the offers of a kingdom were last made, or {@link Long#MIN_VALUE}.
     */
    public long offersDay(UUID kingdomId) {
        return offersDay.getOrDefault(kingdomId, Long.MIN_VALUE);
    }

    public Map<UUID, Long> offersDays() {
        return Collections.unmodifiableMap(offersDay);
    }

    public long nextSquadId() {
        return nextSquad;
    }

    public long nextBandId() {
        return nextBand;
    }

    // ---------------------------------------------------------------- rights

    /**
     * Whether the actor may run the army of the kingdom: the kingdom exists and the actor holds {@link Permission#MANAGE_ARMY};
     * {@code null} is an operator or the server itself.
     */
    public Outcome checkArmy(UUID actor, UUID kingdomId, KingdomWorldState world) {
        if (world.kingdoms().get(kingdomId).isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (actor != null && !world.membership().hasPermission(actor, kingdomId, Permission.MANAGE_ARMY)) {
            return Outcome.fail(world.membership().isMember(actor, kingdomId) ? KingdomError.NO_PERMISSION : KingdomError.NOT_A_MEMBER);
        }
        return Outcome.DONE;
    }

    // ---------------------------------------------------------------- soldiers

    /**
     * Whether a citizen may be recruited as a warrior or an archer.
     */
    public Outcome checkRecruit(UUID actor, UUID kingdomId, UUID citizenId, String professionId, ArmyRules rules, KingdomWorldState world) {
        Outcome check = checkArmy(actor, kingdomId, world);
        if (!check.ok()) {
            return check;
        }
        if (!Professions.isMilitary(professionId)) {
            return Outcome.fail(KingdomError.UNKNOWN_PROFESSION);
        }
        Optional<Citizen> citizen = world.citizens().get(citizenId);
        if (citizen.isEmpty() || !citizen.get().kingdomId().equals(kingdomId)) {
            return Outcome.fail(KingdomError.UNKNOWN_CITIZEN);
        }
        if (soldiers.containsKey(citizenId)) {
            return citizen.get().professionId().equals(professionId) ? Outcome.fail(KingdomError.ALREADY_A_SOLDIER) : Outcome.DONE;
        }
        if (soldiersOf(kingdomId).size() >= rules.maxSoldiers()) {
            return Outcome.fail(KingdomError.TOO_MANY_SOLDIERS);
        }
        return Outcome.DONE;
    }

    /**
     * Recruits a citizen as a warrior or an archer; a soldier already serving changes between the two.
     */
    public Outcome recruit(UUID actor, UUID kingdomId, UUID citizenId, String professionId, long now, ArmyRules rules, KingdomWorldState world) {
        Outcome check = checkRecruit(actor, kingdomId, citizenId, professionId, rules, world);
        if (!check.ok()) {
            return check;
        }
        Citizen citizen = world.citizens().get(citizenId).orElseThrow();
        world.citizens().setProfession(citizen, professionId);
        soldiers.putIfAbsent(citizenId, Soldier.recruit(citizenId, kingdomId, now));
        changed();
        return Outcome.DONE;
    }

    public Outcome checkDismiss(UUID actor, UUID citizenId, KingdomWorldState world) {
        Soldier soldier = soldiers.get(citizenId);
        if (soldier == null) {
            return Outcome.fail(KingdomError.NOT_A_SOLDIER);
        }
        return checkArmy(actor, soldier.kingdomId(), world);
    }

    /**
     * Dismisses a soldier: it leaves its squad and the army and is an unemployed citizen again. The caller brings its gear
     * back to the storehouses.
     */
    public Outcome dismiss(UUID actor, UUID citizenId, KingdomWorldState world) {
        Outcome check = checkDismiss(actor, citizenId, world);
        if (!check.ok()) {
            return check;
        }
        forgetSoldier(citizenId);
        world.citizens().get(citizenId).ifPresent(citizen -> world.citizens().setProfession(citizen, Professions.UNEMPLOYED.id()));
        changed();
        return Outcome.DONE;
    }

    /**
     * Forgets a soldier who died or left the army; its squad loses it, an empty squad is gone.
     */
    public void forgetSoldier(UUID citizenId) {
        boolean any = soldiers.remove(citizenId) != null;
        for (ArmySquad squad : List.copyOf(squads.values())) {
            if (squad.members().contains(citizenId)) {
                List<UUID> rest = new ArrayList<>(squad.members());
                rest.remove(citizenId);
                if (rest.isEmpty()) {
                    squads.remove(squad.id());
                } else {
                    squads.put(squad.id(), squad.withMembers(rest, citizenId.equals(squad.commander()) ? null : squad.commander()));
                }
                any = true;
            }
        }
        if (any) {
            changed();
        }
    }

    /**
     * A soldier gained experience, kills and battles.
     */
    public void reward(UUID citizenId, long experience, int kills, int battles) {
        Soldier soldier = soldiers.get(citizenId);
        if (soldier != null) {
            soldiers.put(citizenId, soldier.earned(experience, kills, battles));
            changed();
        }
    }

    /**
     * A soldier trained; training stops at the cap of the rules.
     */
    public void train(UUID citizenId, long experience, ArmyRules rules) {
        Soldier soldier = soldiers.get(citizenId);
        if (soldier != null) {
            Soldier trained = soldier.trained(experience, rules.trainingCap());
            if (trained != soldier) {
                soldiers.put(citizenId, trained);
                changed();
            }
        }
    }

    // ---------------------------------------------------------------- squads

    /**
     * The result of forming a squad.
     */
    public record Formed(Outcome outcome, Optional<ArmySquad> squad) {
    }

    /**
     * Forms a squad of soldiers of one kingdom, each in no other squad; the first is its commander.
     */
    public Formed formSquad(UUID actor, UUID kingdomId, List<UUID> members, ArmyRules rules, KingdomWorldState world) {
        Outcome check = checkArmy(actor, kingdomId, world);
        if (!check.ok()) {
            return new Formed(check, Optional.empty());
        }
        if (members.isEmpty() || new HashSet<>(members).size() != members.size() || members.size() > rules.maxSquadSize()) {
            return new Formed(Outcome.fail(KingdomError.SQUAD_SIZE), Optional.empty());
        }
        if (squadsOf(kingdomId).size() >= rules.maxSquads()) {
            return new Formed(Outcome.fail(KingdomError.TOO_MANY_SQUADS), Optional.empty());
        }
        for (UUID member : members) {
            Soldier soldier = soldiers.get(member);
            if (soldier == null || !soldier.kingdomId().equals(kingdomId)) {
                return new Formed(Outcome.fail(KingdomError.NOT_A_SOLDIER), Optional.empty());
            }
            if (squadOf(member).isPresent()) {
                return new Formed(Outcome.fail(KingdomError.IN_ANOTHER_SQUAD), Optional.empty());
            }
        }
        ArmySquad squad = new ArmySquad(nextSquad++, kingdomId, members, members.get(0), 0, Order.NONE);
        squads.put(squad.id(), squad);
        changed();
        return new Formed(Outcome.DONE, Optional.of(squad));
    }

    /**
     * Adds a soldier to a squad of soldiers, or moves it there from another one.
     */
    public Outcome join(UUID actor, long squadId, UUID citizenId, ArmyRules rules, KingdomWorldState world) {
        ArmySquad squad = squads.get(squadId);
        if (squad == null || squad.mercenaries()) {
            return Outcome.fail(KingdomError.UNKNOWN_SQUAD);
        }
        Outcome check = checkArmy(actor, squad.kingdomId(), world);
        if (!check.ok()) {
            return check;
        }
        Soldier soldier = soldiers.get(citizenId);
        if (soldier == null || !soldier.kingdomId().equals(squad.kingdomId())) {
            return Outcome.fail(KingdomError.NOT_A_SOLDIER);
        }
        if (squad.members().contains(citizenId)) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        if (squad.members().size() >= rules.maxSquadSize()) {
            return Outcome.fail(KingdomError.SQUAD_SIZE);
        }
        Optional<ArmySquad> old = squadOf(citizenId);
        if (old.isPresent()) {
            List<UUID> rest = new ArrayList<>(old.get().members());
            rest.remove(citizenId);
            if (rest.isEmpty()) {
                squads.remove(old.get().id());
            } else {
                squads.put(old.get().id(), old.get().withMembers(rest, citizenId.equals(old.get().commander()) ? null : old.get().commander()));
            }
        }
        List<UUID> members = new ArrayList<>(squads.get(squadId).members());
        members.add(citizenId);
        squads.put(squadId, squads.get(squadId).withMembers(members, squads.get(squadId).commander()));
        changed();
        return Outcome.DONE;
    }

    /**
     * Disbands a squad of soldiers; they stay soldiers. A band of mercenaries is sent away with {@link #release}.
     */
    public Outcome disband(UUID actor, long squadId, KingdomWorldState world) {
        ArmySquad squad = squads.get(squadId);
        if (squad == null) {
            return Outcome.fail(KingdomError.UNKNOWN_SQUAD);
        }
        Outcome check = checkArmy(actor, squad.kingdomId(), world);
        if (!check.ok()) {
            return check;
        }
        if (squad.mercenaries()) {
            return release(actor, squad.band(), world);
        }
        squads.remove(squadId);
        changed();
        return Outcome.DONE;
    }

    public Outcome setCommander(UUID actor, long squadId, UUID citizenId, KingdomWorldState world) {
        ArmySquad squad = squads.get(squadId);
        if (squad == null || squad.mercenaries()) {
            return Outcome.fail(KingdomError.UNKNOWN_SQUAD);
        }
        Outcome check = checkArmy(actor, squad.kingdomId(), world);
        if (!check.ok()) {
            return check;
        }
        if (!squad.members().contains(citizenId)) {
            return Outcome.fail(KingdomError.NOT_A_SOLDIER);
        }
        if (citizenId.equals(squad.commander())) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        squads.put(squadId, squad.withMembers(squad.members(), citizenId));
        changed();
        return Outcome.DONE;
    }

    /**
     * Whether a squad may get an order: places in the kingdom's dimension, a member of the kingdom to follow, a deal of the
     * kingdom (as seller or buyer) that is going on.
     *
     * @param dimension the dimension of the kingdom's capital
     */
    public Outcome checkOrder(UUID actor, long squadId, Order order, String dimension, KingdomWorldState world) {
        ArmySquad squad = squads.get(squadId);
        if (squad == null) {
            return Outcome.fail(KingdomError.UNKNOWN_SQUAD);
        }
        Outcome check = checkArmy(actor, squad.kingdomId(), world);
        if (!check.ok()) {
            return check;
        }
        for (WorldPos point : order.points()) {
            if (!point.dimension().equals(dimension)) {
                return Outcome.fail(KingdomError.BAD_ORDER);
            }
        }
        if (order.kind() == Order.Kind.FOLLOW && !world.membership().isMember(order.player(), squad.kingdomId())) {
            return Outcome.fail(KingdomError.BAD_ORDER);
        }
        if (order.kind() == Order.Kind.ESCORT) {
            Optional<Deal> deal = world.trade().deal(order.deal());
            boolean onTheWay = world.trade().caravans().stream()
                    .anyMatch(caravan -> caravan.dealId() == order.deal() && caravan.state() == Caravan.State.TRAVELING);
            if (deal.isEmpty() || (deal.get().status() != Deal.Status.ACTIVE && !onTheWay)
                    || !(deal.get().seller().equals(squad.kingdomId()) || deal.get().buyer().equals(squad.kingdomId()))) {
                return Outcome.fail(KingdomError.BAD_ORDER);
            }
        }
        if (order.equals(squad.order())) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        return Outcome.DONE;
    }

    public Outcome setOrder(UUID actor, long squadId, Order order, String dimension, KingdomWorldState world) {
        Outcome check = checkOrder(actor, squadId, order, dimension, world);
        if (!check.ok()) {
            return check;
        }
        squads.put(squadId, squads.get(squadId).withOrder(order));
        changed();
        return Outcome.DONE;
    }

    // ---------------------------------------------------------------- mercenaries

    /**
     * Makes the offers of a kingdom for a day: the old offers go, new bands come.
     */
    public List<MercenaryBand> refreshOffers(UUID kingdomId, long day, ArmyRules rules, DoubleSupplier random) {
        bands.values().removeIf(band -> band.kingdomId().equals(kingdomId) && !band.hired());
        List<MercenaryBand> made = new ArrayList<>();
        for (int i = 0; i < rules.offersPerDay(); i++) {
            MercenaryBand band = MercenaryMarket.offer(nextBand++, kingdomId, day, rules, random);
            bands.put(band.id(), band);
            made.add(band);
        }
        offersDay.put(kingdomId, day);
        changed();
        return made;
    }

    /**
     * The advance a kingdom pays to hire a band.
     */
    public static long advance(MercenaryBand band, ArmyRules rules) {
        return Money.multiplyCapped(band.dailyPrice(), rules.advanceDays());
    }

    public Outcome checkHire(UUID actor, UUID kingdomId, long bandId, ArmyRules rules, KingdomWorldState world) {
        Outcome check = checkArmy(actor, kingdomId, world);
        if (!check.ok()) {
            return check;
        }
        MercenaryBand band = bands.get(bandId);
        if (band == null || band.hired() || !band.kingdomId().equals(kingdomId)) {
            return Outcome.fail(KingdomError.UNKNOWN_OFFER);
        }
        if (mercenaryCount(kingdomId) + band.fighters().size() > rules.maxMercenaries()) {
            return Outcome.fail(KingdomError.TOO_MANY_MERCENARIES);
        }
        if (squadsOf(kingdomId).size() >= rules.maxSquads()) {
            return Outcome.fail(KingdomError.TOO_MANY_SQUADS);
        }
        long advance = advance(band, rules);
        if (world.economy().treasury(kingdomId).map(treasury -> treasury.funds() < advance).orElse(true)) {
            return Outcome.fail(KingdomError.TREASURY_EMPTY);
        }
        return Outcome.DONE;
    }

    /**
     * Hires a band on offer: the advance leaves the treasury and the band serves as a squad of its own, paid for
     * {@link ArmyRules#advanceDays} days from today.
     */
    public Formed hire(UUID actor, UUID kingdomId, long bandId, long day, ArmyRules rules, KingdomWorldState world) {
        Outcome check = checkHire(actor, kingdomId, bandId, rules, world);
        if (!check.ok()) {
            return new Formed(check, Optional.empty());
        }
        MercenaryBand band = bands.get(bandId);
        Outcome paid = world.economy().armyPayment(kingdomId, advance(band, rules), day);
        if (!paid.ok()) {
            return new Formed(paid, Optional.empty());
        }
        bands.put(bandId, band.hiredOn(day, rules.advanceDays()));
        ArmySquad squad = new ArmySquad(nextSquad++, kingdomId, List.of(), null, bandId, Order.NONE);
        squads.put(squad.id(), squad);
        changed();
        return new Formed(Outcome.DONE, Optional.of(squad));
    }

    /**
     * Sends a hired band away. What it was paid in advance stays with it.
     */
    public Outcome release(UUID actor, long bandId, KingdomWorldState world) {
        MercenaryBand band = bands.get(bandId);
        if (band == null || !band.hired()) {
            return Outcome.fail(KingdomError.UNKNOWN_SQUAD);
        }
        Outcome check = checkArmy(actor, band.kingdomId(), world);
        if (!check.ok()) {
            return check;
        }
        leave(bandId);
        return Outcome.DONE;
    }

    private void leave(long bandId) {
        bands.remove(bandId);
        squads.values().removeIf(squad -> squad.band() == bandId);
        changed();
    }

    /**
     * The daily pay of the hired bands of a kingdom: each band not paid for today gets its daily price from the treasury,
     * or leaves.
     *
     * @return the bands that left
     */
    public List<MercenaryBand> payday(UUID kingdomId, long day, KingdomWorldState world) {
        List<MercenaryBand> left = new ArrayList<>();
        for (MercenaryBand band : hiredOf(kingdomId)) {
            if (band.paidUntil() >= day) {
                continue;
            }
            if (world.economy().armyPayment(kingdomId, band.dailyPrice(), day).ok()) {
                bands.put(band.id(), band.paidThrough(day));
                changed();
            } else {
                leave(band.id());
                left.add(band);
            }
        }
        return left;
    }

    /**
     * Puts a band back after a fight: the fallen are gone (with their gear, which never falls into the world), the others
     * carry their wounds; a band with nobody left is gone with its squad.
     */
    public void updateBand(MercenaryBand band) {
        if (!bands.containsKey(band.id())) {
            return;
        }
        if (band.fighters().isEmpty()) {
            leave(band.id());
            return;
        }
        bands.put(band.id(), band);
        changed();
    }

    /**
     * A mercenary fell.
     *
     * @return whether the band is left with nobody
     */
    public boolean mercenaryFell(long bandId, UUID fighterId) {
        MercenaryBand band = bands.get(bandId);
        if (band == null) {
            return false;
        }
        List<Mercenary> rest = new ArrayList<>();
        for (Mercenary fighter : band.fighters()) {
            if (!fighter.id().equals(fighterId)) {
                rest.add(fighter);
            }
        }
        if (rest.isEmpty()) {
            leave(bandId);
            return true;
        }
        bands.put(bandId, band.withFighters(rest));
        changed();
        return false;
    }

    // ---------------------------------------------------------------- barracks

    /**
     * A barracks stands on the kingdom's land.
     *
     * @return whether it was new
     */
    public boolean addBarracks(UUID kingdomId, WorldPos pos) {
        removeBarracks(pos);
        boolean added = barracks.computeIfAbsent(kingdomId, k -> new LinkedHashSet<>()).add(pos);
        changed();
        return added;
    }

    public boolean removeBarracks(WorldPos pos) {
        boolean removed = false;
        for (Set<WorldPos> set : barracks.values()) {
            removed |= set.remove(pos);
        }
        barracks.values().removeIf(Set::isEmpty);
        if (removed) {
            changed();
        }
        return removed;
    }

    // ---------------------------------------------------------------- kingdoms that are gone

    /**
     * A kingdom was dissolved: its soldiers, squads, bands and barracks are forgotten.
     */
    public void forgetKingdom(UUID kingdomId) {
        soldiers.values().removeIf(soldier -> soldier.kingdomId().equals(kingdomId));
        squads.values().removeIf(squad -> squad.kingdomId().equals(kingdomId));
        bands.values().removeIf(band -> band.kingdomId().equals(kingdomId));
        barracks.remove(kingdomId);
        offersDay.remove(kingdomId);
        changed();
    }

    // ---------------------------------------------------------------- saved data

    public void restoreSoldier(Soldier soldier) {
        if (soldiers.putIfAbsent(soldier.citizenId(), soldier) != null) {
            throw new IllegalArgumentException("Two soldiers " + soldier.citizenId());
        }
    }

    public void restoreSquad(ArmySquad squad) {
        for (UUID member : squad.members()) {
            if (squadOf(member).isPresent()) {
                throw new IllegalArgumentException("Soldier " + member + " is in two squads");
            }
        }
        if (squad.mercenaries() && squadOfBand(squad.band()).isPresent()) {
            throw new IllegalArgumentException("Band #" + squad.band() + " is two squads");
        }
        if (squads.putIfAbsent(squad.id(), squad) != null) {
            throw new IllegalArgumentException("Two squads #" + squad.id());
        }
        nextSquad = Math.max(nextSquad, squad.id() + 1);
    }

    public void restoreBand(MercenaryBand band) {
        if (bands.putIfAbsent(band.id(), band) != null) {
            throw new IllegalArgumentException("Two bands #" + band.id());
        }
        nextBand = Math.max(nextBand, band.id() + 1);
    }

    public void restoreBarracks(UUID kingdomId, WorldPos pos) {
        barracks.computeIfAbsent(kingdomId, k -> new LinkedHashSet<>()).add(pos);
    }

    public void restoreOffersDay(UUID kingdomId, long day) {
        offersDay.put(kingdomId, day);
    }

    public void restoreNextIds(long squad, long band) {
        nextSquad = Math.max(nextSquad, squad);
        nextBand = Math.max(nextBand, band);
    }

    /**
     * Checks what was read: squads of soldiers that are gone and bands without their squad.
     *
     * @return what does not fit
     */
    public List<String> verify() {
        List<String> problems = new ArrayList<>();
        for (ArmySquad squad : squads.values()) {
            for (UUID member : squad.members()) {
                Soldier soldier = soldiers.get(member);
                if (soldier == null || !soldier.kingdomId().equals(squad.kingdomId())) {
                    problems.add("squad #" + squad.id() + " has a member who is no soldier of its kingdom: " + member);
                }
            }
            if (squad.mercenaries()) {
                MercenaryBand band = bands.get(squad.band());
                if (band == null || !band.hired() || !band.kingdomId().equals(squad.kingdomId())) {
                    problems.add("squad #" + squad.id() + " is a band that is not hired: #" + squad.band());
                }
            }
        }
        for (MercenaryBand band : bands.values()) {
            if (band.hired() && squadOfBand(band.id()).isEmpty()) {
                problems.add("hired band #" + band.id() + " has no squad");
            }
        }
        return problems;
    }

    /**
     * Forgets everything, for data that could not be read.
     */
    public void clear() {
        soldiers.clear();
        squads.clear();
        bands.clear();
        barracks.clear();
        offersDay.clear();
        nextSquad = 1;
        nextBand = 1;
    }
}
