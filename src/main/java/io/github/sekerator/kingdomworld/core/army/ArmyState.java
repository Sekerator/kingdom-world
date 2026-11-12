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
    private final SoldierBook soldiers = new SoldierBook();
    private final SquadBook squads = new SquadBook();
    private final Map<Long, MercenaryBand> bands = new LinkedHashMap<>();
    private final Map<UUID, Set<WorldPos>> barracks = new LinkedHashMap<>();
    private final Map<UUID, Long> offersDay = new LinkedHashMap<>();
    /** Where squads are and march, by squad; a squad without one stands at its home or at the place of its order. */
    private final Map<Long, March> marches = new LinkedHashMap<>();
    /** Soldiers that fell and are not replaced yet, by kingdom and branch. */
    private final Map<UUID, int[]> losses = new LinkedHashMap<>();
    /** Posts of officers that fell, by kingdom, with the game day they fell on. */
    private final Map<UUID, Map<Slot, Long>> vacancies = new LinkedHashMap<>();
    /** The game day the staff of a kingdom was last seen to. */
    private final Map<UUID, Long> staffDay = new LinkedHashMap<>();
    /** The post of the garrison a squad stands at, by squad: a gate, the storehouse, the hall of the palace and so on. */
    private final Map<Long, String> duties = new LinkedHashMap<>();
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
        return soldiers.ofKingdom(kingdomId);
    }

    public Optional<ArmySquad> squad(long id) {
        return Optional.ofNullable(squads.get(id));
    }

    public Collection<ArmySquad> squads() {
        return Collections.unmodifiableCollection(squads.values());
    }

    public List<ArmySquad> squadsOf(UUID kingdomId) {
        return squads.ofKingdom(kingdomId);
    }

    /**
     * The squad a soldier serves in.
     */
    public Optional<ArmySquad> squadOf(UUID citizenId) {
        return squads.ofMember(citizenId);
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
        for (ArmySquad squad : squadOf(citizenId).stream().toList()) {
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
     * Takes a soldier out of its squad; it stays a soldier. A squad left with nobody is gone.
     */
    public void leaveSquad(UUID citizenId) {
        for (ArmySquad squad : squadOf(citizenId).stream().toList()) {
            if (!squad.members().contains(citizenId)) {
                continue;
            }
            List<UUID> rest = new ArrayList<>(squad.members());
            rest.remove(citizenId);
            if (rest.isEmpty()) {
                squads.remove(squad.id());
            } else {
                squads.put(squad.id(), squad.withMembers(rest, citizenId.equals(squad.commander()) ? null : squad.commander()));
            }
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

    // ---------------------------------------------------------------- branches and posts

    /**
     * The post of an officer in a branch.
     */
    public record Slot(Branch branch, Post post) {
        public Slot {
            Objects.requireNonNull(branch, "branch");
            Objects.requireNonNull(post, "post");
        }
    }

    /**
     * The soldiers of a branch of a kingdom, officers included.
     */
    public List<Soldier> branchOf(UUID kingdomId, Branch branch) {
        List<Soldier> result = new ArrayList<>();
        for (Soldier soldier : soldiers.ofKingdom(kingdomId)) {
            if (soldier.branch() == branch) {
                result.add(soldier);
            }
        }
        return result;
    }

    /**
     * The officer who holds a post in a branch of a kingdom.
     */
    public Optional<Soldier> officer(UUID kingdomId, Branch branch, Post post) {
        if (!post.officer()) {
            return Optional.empty();
        }
        for (Soldier soldier : soldiers.ofKingdom(kingdomId)) {
            if (soldier.branch() == branch && soldier.post() == post) {
                return Optional.of(soldier);
            }
        }
        return Optional.empty();
    }

    /**
     * Moves a soldier to the other branch. An officer leaves its post behind and serves as a fighter there.
     */
    public Outcome setBranch(UUID actor, UUID citizenId, Branch branch, KingdomWorldState world) {
        Soldier soldier = soldiers.get(citizenId);
        if (soldier == null) {
            return Outcome.fail(KingdomError.NOT_A_SOLDIER);
        }
        Outcome check = checkArmy(actor, soldier.kingdomId(), world);
        if (!check.ok()) {
            return check;
        }
        if (soldier.branch() == branch) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        soldiers.put(citizenId, soldier.withBranch(branch).withPost(Post.FIGHTER));
        changed();
        return Outcome.DONE;
    }

    /**
     * Gives a soldier a post in its branch. A branch has one vice-captain and one captain: whoever held the post before
     * serves as a fighter again. The post is no longer vacant.
     */
    public Outcome appoint(UUID actor, UUID citizenId, Post post, KingdomWorldState world) {
        Soldier soldier = soldiers.get(citizenId);
        if (soldier == null) {
            return Outcome.fail(KingdomError.NOT_A_SOLDIER);
        }
        Outcome check = checkArmy(actor, soldier.kingdomId(), world);
        if (!check.ok()) {
            return check;
        }
        if (soldier.post() == post) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        if (post.officer()) {
            officer(soldier.kingdomId(), soldier.branch(), post)
                    .ifPresent(holder -> soldiers.put(holder.citizenId(), holder.withPost(Post.FIGHTER)));
            Map<Slot, Long> open = vacancies.get(soldier.kingdomId());
            if (open != null) {
                open.remove(new Slot(soldier.branch(), post));
                if (open.isEmpty()) {
                    vacancies.remove(soldier.kingdomId());
                }
            }
        }
        soldiers.put(citizenId, soldier.withPost(post));
        changed();
        return Outcome.DONE;
    }

    /**
     * A soldier fell: its branch has one to replace, the post of an officer is vacant from this day, and the army forgets
     * the soldier.
     */
    public void fell(UUID citizenId, long day) {
        Soldier soldier = soldiers.get(citizenId);
        if (soldier == null) {
            return;
        }
        int[] lost = losses.computeIfAbsent(soldier.kingdomId(), k -> new int[Branch.values().length]);
        lost[soldier.branch().ordinal()] = Math.min(ArmySquad.MAX_MEMBERS * 32, lost[soldier.branch().ordinal()] + 1);
        if (soldier.post().officer()) {
            vacancies.computeIfAbsent(soldier.kingdomId(), k -> new LinkedHashMap<>()).put(new Slot(soldier.branch(), soldier.post()), day);
        }
        forgetSoldier(citizenId);
        changed();
    }

    /**
     * Fallen soldiers of a branch a kingdom has not replaced yet.
     */
    public int losses(UUID kingdomId, Branch branch) {
        int[] lost = losses.get(kingdomId);
        return lost == null ? 0 : lost[branch.ordinal()];
    }

    /**
     * A fallen soldier of a branch was replaced.
     */
    public void replaced(UUID kingdomId, Branch branch) {
        int[] lost = losses.get(kingdomId);
        if (lost == null || lost[branch.ordinal()] <= 0) {
            return;
        }
        lost[branch.ordinal()]--;
        if (lost[0] == 0 && lost[1] == 0) {
            losses.remove(kingdomId);
        }
        changed();
    }

    public Map<UUID, int[]> losses() {
        return Collections.unmodifiableMap(losses);
    }

    /**
     * The posts of officers of a kingdom that fell, with the game day they fell on.
     */
    public Map<Slot, Long> vacanciesOf(UUID kingdomId) {
        return Collections.unmodifiableMap(vacancies.getOrDefault(kingdomId, Map.of()));
    }

    public Map<UUID, Map<Slot, Long>> vacancies() {
        return Collections.unmodifiableMap(vacancies);
    }

    /**
     * Whether a post may be filled today: nobody holds it, the branch is large enough for it, and the days of mourning of
     * a fallen officer are over. A post nobody fell from may be filled at once.
     */
    public boolean postOpen(UUID kingdomId, Branch branch, Post post, long day, ForceRules rules) {
        if (!post.officer() || officer(kingdomId, branch, post).isPresent() || branchOf(kingdomId, branch).size() < rules.minBranch(post)) {
            return false;
        }
        Long since = vacancies.getOrDefault(kingdomId, Map.of()).get(new Slot(branch, post));
        return since == null || day - since >= rules.officerReplaceDays;
    }

    /**
     * Who takes a vacant post of a branch: its vice-captain rises to captain, otherwise the best fighter, by experience of
     * war, then kills, then length of service.
     */
    public Optional<Soldier> successor(UUID kingdomId, Branch branch, Post post) {
        if (post == Post.CAPTAIN) {
            Optional<Soldier> vice = officer(kingdomId, branch, Post.VICE_CAPTAIN);
            if (vice.isPresent()) {
                return vice;
            }
        }
        Soldier best = null;
        for (Soldier soldier : branchOf(kingdomId, branch)) {
            if (soldier.post() != Post.FIGHTER) {
                continue;
            }
            if (best == null || soldier.experience() > best.experience()
                    || (soldier.experience() == best.experience() && (soldier.kills() > best.kills()
                    || (soldier.kills() == best.kills() && soldier.recruited() < best.recruited())))) {
                best = soldier;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * The game day the staff of a kingdom was last seen to, or {@link Long#MIN_VALUE}.
     */
    public long staffDay(UUID kingdomId) {
        return staffDay.getOrDefault(kingdomId, Long.MIN_VALUE);
    }

    public void setStaffDay(UUID kingdomId, long day) {
        Long previous = staffDay.put(kingdomId, day);
        if (previous == null || previous != day) {
            changed();
        }
    }

    public Map<UUID, Long> staffDays() {
        return Collections.unmodifiableMap(staffDay);
    }

    /**
     * The post of the garrison a squad stands at, if it stands at one.
     */
    public Optional<String> duty(long squadId) {
        return squads.containsKey(squadId) ? Optional.ofNullable(duties.get(squadId)) : Optional.empty();
    }

    /**
     * Puts a squad on a post of the garrison, or takes it off with {@code null}.
     */
    public void setDuty(long squadId, String duty) {
        if (!squads.containsKey(squadId)) {
            return;
        }
        String previous = duty == null || duty.isEmpty() ? duties.remove(squadId) : duties.put(squadId, duty);
        if (!Objects.equals(previous, duty == null || duty.isEmpty() ? null : duty)) {
            changed();
        }
    }

    /**
     * The squad of a kingdom that stands at a post of the garrison.
     */
    public Optional<ArmySquad> squadOfDuty(UUID kingdomId, String duty) {
        for (Map.Entry<Long, String> entry : duties.entrySet()) {
            ArmySquad squad = squads.get(entry.getKey());
            if (squad != null && squad.kingdomId().equals(kingdomId) && entry.getValue().equals(duty)) {
                return Optional.of(squad);
            }
        }
        return Optional.empty();
    }

    /**
     * The posts of every squad that still exists.
     */
    public Map<Long, String> duties() {
        duties.keySet().removeIf(id -> !squads.containsKey(id));
        return Collections.unmodifiableMap(duties);
    }

    public void restoreDuty(long squadId, String duty) {
        if (squads.containsKey(squadId) && duty != null && !duty.isEmpty()) {
            duties.put(squadId, duty);
        }
    }

    /**
     * Gives the soldiers of data older than the branches their branch: the guard for those whose squad holds a point,
     * guards an area or walks a patrol, the army for the others. All of them are fighters.
     *
     * @return how many soldiers went to the guard
     */
    public int assignBranchesByOrders() {
        int guards = 0;
        for (Soldier soldier : List.copyOf(soldiers.values())) {
            Branch branch = Branch.ofOrder(squadOf(soldier.citizenId()).map(squad -> squad.order().kind()).orElse(Order.Kind.NONE));
            soldiers.put(soldier.citizenId(), soldier.withBranch(branch).withPost(Post.FIGHTER));
            if (branch == Branch.GUARD) {
                guards++;
            }
        }
        return guards;
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
        for (WorldPos point : order.via()) {
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

    /**
     * Changes how a squad marches and fights.
     */
    public Outcome setFormation(UUID actor, long squadId, io.github.sekerator.kingdomworld.core.combat.Formation formation, KingdomWorldState world) {
        ArmySquad squad = squads.get(squadId);
        if (squad == null) {
            return Outcome.fail(KingdomError.UNKNOWN_SQUAD);
        }
        Outcome check = checkArmy(actor, squad.kingdomId(), world);
        if (!check.ok()) {
            return check;
        }
        if (squad.formation() == formation) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        squads.put(squadId, squad.withFormation(formation));
        changed();
        return Outcome.DONE;
    }

    // ---------------------------------------------------------------- marches

    /**
     * Where a squad is and marches, if it ever marched.
     */
    public Optional<March> march(long squadId) {
        return Optional.ofNullable(marches.get(squadId));
    }

    /**
     * Every march of a squad that still exists.
     */
    public Map<Long, March> marches() {
        marches.keySet().removeIf(id -> !squads.containsKey(id));
        return Collections.unmodifiableMap(marches);
    }

    /**
     * Moves a squad, without marking the data changed: marches change every second, and the saved data takes them as they
     * are when it is saved anyway.
     */
    public void setMarch(long squadId, March march) {
        if (squads.containsKey(squadId)) {
            marches.put(squadId, march);
        }
    }

    public void forgetMarch(long squadId) {
        marches.remove(squadId);
    }

    public void restoreMarch(long squadId, March march) {
        if (squads.containsKey(squadId)) {
            marches.put(squadId, march);
        }
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
        squads.removeIf(squad -> squad.band() == bandId);
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
    /**
     * Every fighter of a band fell: the band is gone, with its squad.
     */
    public void bandFell(long bandId) {
        if (bands.containsKey(bandId)) {
            leave(bandId);
            changed();
        }
    }

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
        soldiers.removeIf(soldier -> soldier.kingdomId().equals(kingdomId));
        squads.removeIf(squad -> squad.kingdomId().equals(kingdomId));
        bands.values().removeIf(band -> band.kingdomId().equals(kingdomId));
        barracks.remove(kingdomId);
        offersDay.remove(kingdomId);
        losses.remove(kingdomId);
        vacancies.remove(kingdomId);
        staffDay.remove(kingdomId);
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

    public void restoreLosses(UUID kingdomId, Branch branch, int count) {
        if (count > 0) {
            losses.computeIfAbsent(kingdomId, k -> new int[Branch.values().length])[branch.ordinal()] = count;
        }
    }

    public void restoreVacancy(UUID kingdomId, Branch branch, Post post, long day) {
        if (!post.officer()) {
            throw new IllegalArgumentException("A fighter leaves no vacant post");
        }
        vacancies.computeIfAbsent(kingdomId, k -> new LinkedHashMap<>()).put(new Slot(branch, post), day);
    }

    public void restoreStaffDay(UUID kingdomId, long day) {
        staffDay.put(kingdomId, day);
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
        Set<String> held = new HashSet<>();
        for (Soldier soldier : soldiers.values()) {
            if (soldier.post().officer() && !held.add(soldier.kingdomId() + "/" + soldier.branch() + "/" + soldier.post())) {
                problems.add("two soldiers hold the post " + soldier.post().id() + " of the " + soldier.branch().id() + " of "
                        + soldier.kingdomId());
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
        marches.clear();
        losses.clear();
        vacancies.clear();
        staffDay.clear();
        duties.clear();
        nextSquad = 1;
        nextBand = 1;
    }
}
