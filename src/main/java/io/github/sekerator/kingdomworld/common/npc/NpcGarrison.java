package io.github.sekerator.kingdomworld.common.npc;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmySettings;
import io.github.sekerator.kingdomworld.common.army.ArmyStaff;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.road.CityWalls;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.army.ArmyRules;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.ArmyState;
import io.github.sekerator.kingdomworld.core.army.Branch;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.army.Post;
import io.github.sekerator.kingdomworld.core.army.Soldier;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.BuildingRoles;
import io.github.sekerator.kingdomworld.core.npc.NpcComposition;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcRules;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.RoyalArmy;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The garrison of a kingdom of the world: how many soldiers it keeps and where they stand.
 *
 * <p>A capital of districts names its posts: two guards at every gate, two inside the storehouse and a round about it,
 * the hall of the palace with the captain of the guard, the door of the palace, the square, and a round along the wall.
 * Every town has a few guards of its own. The royal army is far larger than the guard ({@link RoyalArmy}): a part of it stands
 * in reserve at the barracks, its captain with some of it at the palace, and the rest lives in the camps of the kingdom
 * ({@link NpcCamps}) in squads of {@link #ARMY_SQUAD}, each under its sergeant, the most experienced soldier of the squad. Until a
 * camp stands, or when there is no land for one, those squads wait at the barracks of the capital. The army is not taken
 * from the people: its recruits come from abroad, all of them at the founding and {@link NpcRules#armyPerDay} a day after.
 * A capital of the first layout has no named posts: its gates, a storehouse of the register of buildings and the throne
 * stand in for them.
 *
 * <p>The posts are reckoned anew every time, not once: a gate that is gone loses its guards, a new one gets some, and a
 * post that lost soldiers is filled from the less important ones. Squads the ruler sent elsewhere (a road patrol, the
 * guards of a caravan, a march in a war) are left alone.
 */
public final class NpcGarrison {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Most soldiers of the guard of the people of a kingdom, in a hundred: the rest works. */
    private static final int MOST_PERCENT = 50;
    /** Food points a loaf of bread gives. */
    private static final int BREAD_NUTRITION = 5;
    /** Soldiers of a squad of the army beyond the capital, its sergeant included. */
    static final int ARMY_SQUAD = 20;
    /** The prefix of the posts of the army that waits at the capital for a camp. */
    static final String FIELD = "field";

    /**
     * A post of the garrison.
     *
     * @param id      what the post is: a gate, the storehouse, a town and so on
     * @param order   what the squad at the post does
     * @param wanted  soldiers it should have
     * @param most    soldiers it may have when there are more than the posts ask for
     * @param branch  who stands there: the guard or the army
     * @param officer the officer of the branch who stands there, or {@code null}
     */
    record Duty(String id, Order order, int wanted, int most, Branch branch, @Nullable Post officer) {
    }

    private NpcGarrison() {
    }

    private static Optional<NpcSite> site(MinecraftServer server, UUID kingdomId) {
        return NpcWork.state(server).kingdom(kingdomId).flatMap(kingdom -> NpcWork.state(server).site(kingdom.siteId()));
    }

    // ---------------------------------------------------------------- how many

    /**
     * The soldiers a kingdom should have: {the guard with the guards of its towns, the royal army}. The guard is taken from
     * the people and never more than half of them serve; the army comes from abroad.
     */
    public static int[] wanted(MinecraftServer server, UUID kingdomId) {
        Optional<NpcSite> site = site(server, kingdomId);
        if (site.isEmpty()) {
            return new int[]{0, 0};
        }
        NpcRules rules = NpcSettings.rules();
        NpcLevel level = site.get().level();
        int guards = rules.guards(level) + RoadWork.state(server).citiesOf(kingdomId).size() * rules.townGuards(level);
        ArmyState army = ArmyWork.state(server);
        int people = 0;
        for (Citizen citizen : KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdomId)) {
            people += army.soldier(citizen.id()).map(soldier -> soldier.branch() == Branch.ARMY ? 0 : 1).orElse(1);
        }
        return new int[]{Math.min(guards, people * MOST_PERCENT / 100), royalArmy(server, kingdomId)};
    }

    /**
     * The royal army a kingdom should have, by its size, its seed and its ruler.
     */
    public static int royalArmy(MinecraftServer server, UUID kingdomId) {
        Optional<io.github.sekerator.kingdomworld.core.npc.NpcKingdom> npc = NpcWork.state(server).kingdom(kingdomId);
        Optional<NpcSite> site = site(server, kingdomId);
        if (npc.isEmpty() || site.isEmpty()) {
            return 0;
        }
        return RoyalArmy.size(NpcSettings.rules(), npc.get().level(), site.get().seed(), npc.get().character(), npc.get().traits());
    }

    /**
     * Recruits from abroad join the royal army: they arrive at a camp of the kingdom (at the barracks of its capital while
     * there is none), with no bed of their own, and become warriors and archers, two warriors to an archer. Nothing is paid
     * and nothing taken from the people; their gear comes from the staff.
     *
     * @return how many joined
     */
    static int enlist(MinecraftServer server, UUID kingdomId, int count) {
        return enlist(server, kingdomId, count, false);
    }

    /**
     * @param kit whether every recruit gets the kit of its post at once, for nothing: at the founding the gear of the army is a
     *            part of the wealth of the kingdom
     */
    static int enlist(MinecraftServer server, UUID kingdomId, int count, boolean kit) {
        if (count <= 0 || ArmyWork.isReadOnly(server) || KingdomWorldApi.isReadOnly(server)) {
            return 0;
        }
        WorldPos throne = ArmyWork.capitalOf(server, kingdomId);
        if (throne == null) {
            return 0;
        }
        List<io.github.sekerator.kingdomworld.core.npc.ArmyCamp> camps = NpcCamps.built(server, kingdomId);
        WorldPos barracks = building(server, kingdomId, BuildingRoles.BARRACKS, throne).map(Building::anchor).orElse(throne);
        ArmyState army = ArmyWork.state(server);
        KingdomWorldState world = KingdomWorldApi.state(server);
        ArmyRules rules = ArmySettings.rulesFor(server, kingdomId);
        int joined = 0;
        for (int i = 0; i < count; i++) {
            io.github.sekerator.kingdomworld.core.npc.ArmyCamp camp = camps.isEmpty() ? null : camps.get(i % camps.size());
            WorldPos place = camp == null ? barracks : new WorldPos(throne.dimension(), camp.x(), camp.y() + 1, camp.z());
            Optional<Citizen> recruit = io.github.sekerator.kingdomworld.common.citizen.CitizenLife.arrive(server, kingdomId, place, false, false);
            if (recruit.isEmpty()) {
                break;
            }
            int size = army.branchOf(kingdomId, Branch.ARMY).size();
            String profession = size % 3 == 2 ? io.github.sekerator.kingdomworld.core.citizen.Professions.ARCHER.id()
                    : io.github.sekerator.kingdomworld.core.citizen.Professions.WARRIOR.id();
            UUID id = recruit.get().id();
            io.github.sekerator.kingdomworld.core.kingdom.Outcome outcome = ArmyWork.change(server, "enlist a soldier of the royal army",
                    state -> state.recruit(null, kingdomId, id, profession, KingdomWorldApi.gameTime(server), rules, world));
            if (!outcome.ok()) {
                // No room in the army: the newcomer stays a citizen without work.
                LOGGER.debug("A recruit of {} was not taken into the army: {}", kingdomId, outcome.error());
                break;
            }
            army.setBranch(null, id, Branch.ARMY, world);
            if (army.losses(kingdomId, Branch.ARMY) > 0) {
                army.replaced(kingdomId, Branch.ARMY);
            }
            if (kit) {
                army.soldier(id).ifPresent(soldier -> io.github.sekerator.kingdomworld.common.army.ArmyKits.issue(server, recruit.get(), soldier,
                        true));
            }
            joined++;
        }
        if (kit && joined > 0) {
            // Recruits who come at the founding bring bread for as many days as the kingdom has for its people: like their gear,
            // a part of the wealth of the kingdom at its founding.
            long nutrition = (long) joined * NpcSettings.rules().foodDays()
                    * io.github.sekerator.kingdomworld.common.citizen.CitizenSettings.rules().dailyNutrition();
            int bread = (int) Math.ceil(nutrition / (double) BREAD_NUTRITION);
            while (bread > 0) {
                int loaves = Math.min(64, bread);
                io.github.sekerator.kingdomworld.common.storage.KingdomStorage.insert(server, kingdomId,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, loaves), throne);
                bread -= loaves;
            }
        }
        return joined;
    }

    // ---------------------------------------------------------------- at the founding

    /** Recruits from abroad who join the army in a tick of the appearance of a kingdom at most. */
    private static final int ENLIST_PER_TICK = 16;

    /**
     * The soldiers of the plan of a capital who become its army at the founding: a part of those who live in the capital.
     */
    private static int plannedArmy(NpcSite site, int planned) {
        NpcRules rules = NpcSettings.rules();
        NpcLevel level = site.level();
        int inCapital = Math.min(planned, rules.guards(level) + rules.army(level));
        // A capital of the first layout was planned for a handful of soldiers: all of them are its guard.
        return site.districts() ? inCapital * rules.army(level) / Math.max(1, rules.guards(level) + rules.army(level)) : 0;
    }

    /**
     * While a kingdom appears, the recruits of its royal army come from abroad a part a tick, each with the kit of its post, so
     * that no tick of the appearance takes long.
     *
     * @return whether the army from abroad is whole, so that the garrison may be founded
     */
    static boolean enlistSome(MinecraftServer server, NpcSite site) {
        UUID id = site.kingdomId().orElse(null);
        if (id == null || ArmyWork.isReadOnly(server) || KingdomWorldApi.isReadOnly(server)) {
            return true;
        }
        ArmyState army = ArmyWork.state(server);
        // Until the founding the soldiers of the plan serve in the guard and the recruits from abroad in the army.
        int lacking = royalArmy(server, id) - plannedArmy(site, army.branchOf(id, Branch.GUARD).size()) - army.branchOf(id, Branch.ARMY).size();
        if (lacking <= 0) {
            return true;
        }
        int joined = enlist(server, id, Math.min(ENLIST_PER_TICK, lacking), true);
        return joined <= 0 || joined >= lacking;
    }

    /**
     * The soldiers of a kingdom that just appeared become its guard and its army, the best of them officers, and get the
     * kits of their posts: at its founding the gear is a part of the wealth of the kingdom, so nothing is paid.
     */
    static void found(MinecraftServer server, NpcSite site, NpcComposition composition) {
        UUID id = site.kingdomId().orElse(null);
        if (id == null || ArmyWork.isReadOnly(server)) {
            return;
        }
        ArmyState army = ArmyWork.state(server);
        KingdomWorldState world = KingdomWorldApi.state(server);
        // The soldiers of the plan; the recruits from abroad who came while the kingdom appeared serve in the army already.
        List<Soldier> soldiers = new ArrayList<>(army.branchOf(id, Branch.GUARD));
        if (soldiers.isEmpty()) {
            return;
        }
        NpcRules rules = NpcSettings.rules();
        NpcLevel level = site.level();
        WorldPos throne = ArmyWork.capitalOf(server, id);
        // Those who sleep nearest to the throne are the garrison of the capital; the others guard the towns.
        soldiers.sort(Comparator.comparingDouble(soldier -> world.citizens().get(soldier.citizenId())
                .map(citizen -> citizen.home() != null ? citizen.home() : citizen.lastPos())
                .map(at -> throne != null && throne.dimension().equals(at.dimension()) ? throne.distanceSqr(at) : Double.MAX_VALUE)
                .orElse(Double.MAX_VALUE)));
        int inCapital = Math.min(soldiers.size(), rules.guards(level) + rules.army(level));
        int inArmy = plannedArmy(site, soldiers.size());
        for (int i = 0; i < soldiers.size(); i++) {
            UUID citizen = soldiers.get(i).citizenId();
            boolean ofArmy = i >= inCapital - inArmy && i < inCapital;
            if (ofArmy) {
                army.setBranch(null, citizen, Branch.ARMY, world);
            }
            // They are no recruits: the guard knows its trade, the army knows it better.
            army.reward(citizen, ofArmy ? 300 : 100, 0, 0);
        }
        // The bunks of the barracks are theirs: whoever else lay down there takes a bed the soldiers leave.
        for (Citizen citizen : world.citizens().registry().citizensOf(id)) {
            if (citizen.home() != null && army.soldier(citizen.id()).isEmpty()
                    && io.github.sekerator.kingdomworld.common.construction.BuildingEffects.inBarracks(server, id, citizen.home())) {
                world.citizens().releaseHome(citizen);
            }
        }
        for (Soldier soldier : soldiers) {
            world.citizens().get(soldier.citizenId()).ifPresent(citizen -> ArmyWork.billet(server, citizen));
        }
        // The royal army beyond the soldiers of the plan came from abroad while the kingdom appeared; whoever is still lacking
        // comes now.
        enlist(server, id, royalArmy(server, id) - army.branchOf(id, Branch.ARMY).size(), true);
        int enlisted = Math.max(0, army.branchOf(id, Branch.ARMY).size() - inArmy);
        for (Soldier soldier : army.branchOf(id, Branch.ARMY)) {
            if (army.soldier(soldier.citizenId()).map(known -> known.experience() < 300).orElse(false)) {
                army.reward(soldier.citizenId(), 300 - soldier.experience(), 0, 0);
            }
        }
        ArmyStaff.Report report = ArmyStaff.muster(server, id, true);
        for (Branch branch : Branch.values()) {
            army.officer(id, branch, Post.VICE_CAPTAIN).ifPresent(officer -> army.reward(officer.citizenId(), 1_200, 0, 0));
            army.officer(id, branch, Post.CAPTAIN).ifPresent(officer -> army.reward(officer.citizenId(), 2_700, 0, 0));
        }
        LOGGER.info("Garrison of {}: {} of the guard, {} of the army ({} recruit(s) from abroad), {} officer(s), {} piece(s) of gear", id,
                army.branchOf(id, Branch.GUARD).size(), army.branchOf(id, Branch.ARMY).size(), enlisted, report.promoted(), report.issued());
    }

    // ---------------------------------------------------------------- every day

    /**
     * Once a game day, with the turn of the ruler: the staff replaces a few of the fallen and fills the garrison up to its
     * size, fills the posts of officers and issues gear from the treasury, and the soldiers are put on their posts.
     */
    public static void daily(MinecraftServer server, UUID kingdomId) {
        if (ArmyWork.isReadOnly(server) || !NpcWork.built(server, kingdomId)) {
            return;
        }
        int[] wanted = wanted(server, kingdomId);
        ArmyState army = ArmyWork.state(server);
        // The army fills up from abroad a few a day (a kingdom that appeared before the large armies, or lost soldiers in a
        // war); the staff sees to the guard, the officers and the kits of all.
        if (army.staffDay(kingdomId) < ArmyWork.day(server)) {
            int lacking = wanted[1] - army.branchOf(kingdomId, Branch.ARMY).size();
            int joined = enlist(server, kingdomId, Math.min(NpcSettings.rules().armyPerDay(), lacking));
            if (joined > 0) {
                LOGGER.info("{} recruit(s) from abroad joined the army of {}: {} of {}", joined, kingdomId,
                        army.branchOf(kingdomId, Branch.ARMY).size(), wanted[1]);
            }
        }
        ArmyStaff.daily(server, kingdomId, wanted[0], army.branchOf(kingdomId, Branch.ARMY).size());
        station(server, kingdomId);
    }

    // ---------------------------------------------------------------- the posts

    private static WorldPos at(String dimension, NpcSite site, int[] point) {
        return new WorldPos(dimension, site.x() + point[0], site.groundY() + 1, site.z() + point[1]);
    }

    private static List<WorldPos> route(String dimension, NpcSite site, List<int[]> points) {
        List<WorldPos> route = new ArrayList<>();
        for (int[] point : points) {
            if (route.size() < Order.MAX_POINTS) {
                route.add(at(dimension, site, point));
            }
        }
        return route;
    }

    /**
     * A building of a role of the kingdom, the nearest to a place.
     */
    private static Optional<Building> building(MinecraftServer server, UUID kingdomId, String role, WorldPos near) {
        Building best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Building building : KingdomWorldApi.state(server).construction().buildingsOf(kingdomId)) {
            if (!role.equals(building.role()) || !building.anchor().dimension().equals(near.dimension())) {
                continue;
            }
            double distance = near.distanceSqr(building.anchor());
            if (distance < bestDistance) {
                best = building;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * The posts of a kingdom as its capital and towns stand now, the most important first.
     */
    static List<Duty> duties(MinecraftServer server, UUID kingdomId) {
        Optional<NpcSite> found = site(server, kingdomId);
        WorldPos throne = ArmyWork.capitalOf(server, kingdomId);
        if (found.isEmpty() || throne == null) {
            return List.of();
        }
        NpcSite site = found.get();
        String dimension = throne.dimension();
        NpcRules rules = NpcSettings.rules();
        NpcComposition composition = NpcSpawner.composition(site);
        List<Duty> duties = new ArrayList<>();
        WorldPos palace = throne;
        if (!composition.posts().isEmpty()) {
            // A capital of districts names its posts.
            for (NpcComposition.Post post : composition.posts()) {
                if (post.name().startsWith("gate_") && !post.points().isEmpty()) {
                    duties.add(new Duty(post.name(), Order.guard(at(dimension, site, post.points().get(0)), 0), 2, 3, Branch.GUARD, null));
                }
            }
            Optional<NpcComposition.Post> stores = composition.post("stores").filter(post -> !post.points().isEmpty());
            Optional<NpcComposition.Post> hall = composition.post("palace_hall").filter(post -> !post.points().isEmpty());
            Optional<NpcComposition.Post> round = composition.post("stores_round").filter(post -> post.points().size() >= 2);
            Optional<NpcComposition.Post> door = composition.post("palace_door").filter(post -> !post.points().isEmpty());
            Optional<NpcComposition.Post> square = composition.post("square").filter(post -> !post.points().isEmpty());
            Optional<NpcComposition.Post> wall = composition.post("wall_round").filter(post -> post.points().size() >= 2);
            stores.ifPresent(post -> duties.add(new Duty("stores", Order.hold(at(dimension, site, post.points().get(0))), 2, 2, Branch.GUARD, null)));
            hall.ifPresent(post -> duties.add(new Duty("palace_hall", Order.hold(at(dimension, site, post.points().get(0))), 2, 3, Branch.GUARD,
                    Post.CAPTAIN)));
            round.ifPresent(post -> duties.add(new Duty("stores_round", Order.patrol(route(dimension, site, post.points())), 2, 3, Branch.GUARD,
                    Post.VICE_CAPTAIN)));
            door.ifPresent(post -> duties.add(new Duty("palace_door", Order.hold(at(dimension, site, post.points().get(0))), 2, 2, Branch.GUARD,
                    null)));
            square.ifPresent(post -> duties.add(new Duty("square", Order.hold(at(dimension, site, post.points().get(0))), 2, 4, Branch.GUARD, null)));
            wall.ifPresent(post -> duties.add(new Duty("wall_round", Order.patrol(route(dimension, site, post.points())), 3,
                    ArmySquad.MAX_MEMBERS, Branch.GUARD, null)));
            if (door.isPresent()) {
                palace = at(dimension, site, door.get().points().get(0));
            }
        } else {
            // A capital of the first layout: the gates of its wall, a storehouse of the register and the throne.
            List<WorldPos> gates = CityWalls.gates(server, kingdomId, 0L);
            for (int i = 0; i < gates.size(); i++) {
                duties.add(new Duty("gate_" + i, Order.guard(gates.get(i), 0), 2, 3, Branch.GUARD, null));
            }
            Optional<Building> stores = building(server, kingdomId, BuildingRoles.WAREHOUSE, throne);
            stores.ifPresent(building -> duties.add(new Duty("stores", Order.hold(building.anchor()), 2, 2, Branch.GUARD, null)));
            duties.add(new Duty("palace_hall", Order.hold(throne), 2, 3, Branch.GUARD, Post.CAPTAIN));
            stores.ifPresent(building -> {
                Box box = building.box();
                int y = building.anchor().y();
                duties.add(new Duty("stores_round", Order.patrol(List.of(new WorldPos(dimension, box.minX() - 2, y, box.minZ() - 2),
                        new WorldPos(dimension, box.maxX() + 2, y, box.minZ() - 2), new WorldPos(dimension, box.maxX() + 2, y, box.maxZ() + 2),
                        new WorldPos(dimension, box.minX() - 2, y, box.maxZ() + 2))), 2, ArmySquad.MAX_MEMBERS, Branch.GUARD, Post.VICE_CAPTAIN));
            });
        }
        // Every town has guards of its own at its town hall.
        int ofTown = rules.townGuards(site.level());
        if (ofTown > 0) {
            for (City city : RoadWork.state(server).citiesOf(kingdomId)) {
                if (city.dimension().equals(dimension)) {
                    duties.add(new Duty("town_" + city.id(), Order.guard(new WorldPos(dimension, city.x(), city.y(), city.z()), 1), ofTown,
                            ofTown, Branch.GUARD, null));
                }
            }
        }
        // The army in reserve: at the barracks with its vice-captain, and at the palace with its captain.
        WorldPos barracks = building(server, kingdomId, BuildingRoles.BARRACKS, throne).map(Building::anchor).orElse(throne);
        int capital = Math.max(1, rules.army(site.level()));
        int atPalace = Math.min(6, 1 + capital / 3);
        duties.add(new Duty("reserve", Order.guard(barracks, 0), 0, Math.max(1, capital - atPalace), Branch.ARMY, Post.VICE_CAPTAIN));
        duties.add(new Duty("reserve_palace", Order.guard(palace, 0), 0, atPalace, Branch.ARMY, Post.CAPTAIN));
        // The rest of the army in squads in the camps, as many squads as the army needs; those the camps cannot hold wait at
        // the barracks of the capital.
        int beyond = Math.max(0, royalArmy(server, kingdomId) - capital);
        int squads = (beyond + ARMY_SQUAD - 1) / ARMY_SQUAD;
        int perCamp = (NpcSettings.rules().campSoldiers() + ARMY_SQUAD - 1) / ARMY_SQUAD;
        List<List<WorldPos>> camps = NpcCamps.squadPlaces(server, kingdomId, dimension, perCamp);
        int placed = 0;
        for (int c = 0; c < camps.size() && placed < squads; c++) {
            for (int n = 0; n < perCamp && placed < squads; n++, placed++) {
                duties.add(new Duty(NpcCamps.DUTY + c + "_" + n, Order.guard(camps.get(c).get(n), 0), 0, ARMY_SQUAD, Branch.ARMY, null));
            }
        }
        for (int n = 0; placed < squads; n++, placed++) {
            duties.add(new Duty(FIELD + "_" + n, Order.guard(barracks, 0), 0, ARMY_SQUAD, Branch.ARMY, null));
        }
        return duties;
    }

    /**
     * Puts the soldiers of a kingdom on their posts as the capital and the towns stand now.
     *
     * @return how many soldiers changed their post
     */
    public static int station(MinecraftServer server, UUID kingdomId) {
        if (ArmyWork.isReadOnly(server) || KingdomWorldApi.isReadOnly(server)) {
            return 0;
        }
        List<Duty> duties = duties(server, kingdomId);
        if (duties.isEmpty()) {
            return 0;
        }
        ArmyState army = ArmyWork.state(server);
        KingdomWorldState world = KingdomWorldApi.state(server);
        ArmyRules rules = ArmySettings.rulesFor(server, kingdomId);
        Map<String, Duty> known = new LinkedHashMap<>();
        duties.forEach(duty -> known.put(duty.id(), duty));
        // A post that is gone (a gate walled up, a town lost) lets its soldiers go.
        for (ArmySquad squad : army.squadsOf(kingdomId)) {
            Optional<String> duty = army.duty(squad.id());
            if (duty.isPresent() && !known.containsKey(duty.get())) {
                army.disband(null, squad.id(), world);
            }
        }
        int moved = 0;
        for (Branch branch : Branch.values()) {
            moved += fill(server, kingdomId, branch, duties, rules);
        }
        sergeants(server, kingdomId);
        // Every squad of a post does what the post asks, unless the ruler sent it elsewhere for now.
        for (Duty duty : duties) {
            army.squadOfDuty(kingdomId, duty.id()).ifPresent(squad -> {
                Order.Kind kind = squad.order().kind();
                if (!squad.order().equals(duty.order()) && (kind == Order.Kind.NONE || kind == duty.order().kind())) {
                    ArmyWork.order(server, null, squad.id(), duty.order());
                }
            });
        }
        if (moved > 0) {
            LOGGER.debug("Garrison of {}: {} soldier(s) took a post", kingdomId, moved);
        }
        return moved;
    }

    private static int members(ArmyState army, UUID kingdomId, Duty duty) {
        return army.squadOfDuty(kingdomId, duty.id()).map(squad -> squad.members().size()).orElse(0);
    }

    /**
     * Puts a soldier on a post: into the squad that stands there, or a new one.
     */
    private static boolean assign(MinecraftServer server, UUID kingdomId, Duty duty, UUID citizen, ArmyRules rules) {
        ArmyState army = ArmyWork.state(server);
        KingdomWorldState world = KingdomWorldApi.state(server);
        Optional<ArmySquad> squad = army.squadOfDuty(kingdomId, duty.id());
        if (squad.isPresent()) {
            return squad.get().members().contains(citizen) || army.join(null, squad.get().id(), citizen, rules, world).ok();
        }
        // Out of the squad of another post first: a soldier serves in one squad.
        army.leaveSquad(citizen);
        ArmyState.Formed formed = army.formSquad(null, kingdomId, List.of(citizen), rules, world);
        if (!formed.outcome().ok() || formed.squad().isEmpty()) {
            return false;
        }
        army.setDuty(formed.squad().get().id(), duty.id());
        ArmyWork.order(server, null, formed.squad().get().id(), duty.order());
        return true;
    }

    /**
     * Fills the posts of a branch, the most important first: its officers go where they belong, soldiers without a squad
     * take the posts that lack somebody, a post that still lacks takes from the least important one, and who is left over
     * joins the posts that have room.
     */
    private static int fill(MinecraftServer server, UUID kingdomId, Branch branch, List<Duty> all, ArmyRules rules) {
        ArmyState army = ArmyWork.state(server);
        List<Duty> duties = all.stream().filter(duty -> duty.branch() == branch).toList();
        if (duties.isEmpty()) {
            return 0;
        }
        int moved = 0;
        // Officers first.
        for (Duty duty : duties) {
            if (duty.officer() == null) {
                continue;
            }
            Optional<Soldier> officer = army.officer(kingdomId, branch, duty.officer());
            if (officer.isPresent() && free(army, officer.get().citizenId(), duty) && assign(server, kingdomId, duty, officer.get().citizenId(), rules)) {
                moved++;
            }
        }
        List<UUID> idle = new ArrayList<>();
        for (Soldier soldier : army.branchOf(kingdomId, branch)) {
            if (army.squadOf(soldier.citizenId()).isEmpty()) {
                idle.add(soldier.citizenId());
            }
        }
        // What the posts ask for, in their order.
        for (Duty duty : duties) {
            int wanted = branch == Branch.ARMY ? 0 : duty.wanted();
            while (members(army, kingdomId, duty) < wanted) {
                UUID soldier = !idle.isEmpty() ? idle.remove(0) : donor(army, kingdomId, duties, duty);
                if (soldier == null || !assign(server, kingdomId, duty, soldier, rules)) {
                    break;
                }
                moved++;
            }
        }
        // Who is left over: the army to its posts in their order (the reserve, the palace with the captain, the squads of the
        // camps), the guard to the posts with room.
        if (branch == Branch.ARMY) {
            for (UUID soldier : idle) {
                for (Duty duty : duties) {
                    if (duty.officer() == Post.CAPTAIN && army.officer(kingdomId, branch, Post.CAPTAIN).isEmpty()) {
                        continue;
                    }
                    if (members(army, kingdomId, duty) < duty.most()) {
                        if (assign(server, kingdomId, duty, soldier, rules)) {
                            moved++;
                        }
                        break;
                    }
                }
            }
            return moved;
        }
        boolean placed = true;
        while (!idle.isEmpty() && placed) {
            placed = false;
            for (Duty duty : duties) {
                if (idle.isEmpty()) {
                    break;
                }
                if (members(army, kingdomId, duty) < duty.most() && assign(server, kingdomId, duty, idle.get(0), rules)) {
                    idle.remove(0);
                    moved++;
                    placed = true;
                }
            }
        }
        return moved;
    }

    /**
     * Every squad of the army beyond the capital is under its sergeant: the most experienced fighter of the squad commands it.
     */
    private static void sergeants(MinecraftServer server, UUID kingdomId) {
        ArmyState army = ArmyWork.state(server);
        KingdomWorldState world = KingdomWorldApi.state(server);
        for (ArmySquad squad : army.squadsOf(kingdomId)) {
            Optional<String> duty = army.duty(squad.id());
            if (duty.isEmpty() || !(duty.get().startsWith(NpcCamps.DUTY) || duty.get().startsWith(FIELD))) {
                continue;
            }
            UUID best = null;
            long most = -1;
            for (UUID member : squad.members()) {
                long experience = army.soldier(member).map(Soldier::experience).orElse(0L);
                if (experience > most) {
                    best = member;
                    most = experience;
                }
            }
            if (best != null && !best.equals(squad.commander())) {
                army.setCommander(null, squad.id(), best, world);
            }
        }
    }

    // ---------------------------------------------------------------- war

    /**
     * The squads of the royal army a ruler of the world takes to a war: those of the camps and those waiting at the capital
     * for a camp, while they stand at their posts. The guard, its posts and the reserve of the army at the capital stay home.
     */
    public static List<ArmySquad> fieldArmy(MinecraftServer server, UUID kingdomId) {
        ArmyState army = ArmyWork.state(server);
        List<ArmySquad> squads = new ArrayList<>();
        for (ArmySquad squad : army.squadsOf(kingdomId)) {
            Optional<String> duty = army.duty(squad.id());
            Order.Kind kind = squad.order().kind();
            if (duty.isPresent() && (duty.get().startsWith(NpcCamps.DUTY) || duty.get().startsWith(FIELD))
                    && (kind == Order.Kind.GUARD || kind == Order.Kind.NONE)) {
                squads.add(squad);
            }
        }
        return squads;
    }

    /**
     * Whether a squad stands at a post of the guard or in the reserve of the army at the capital: a ruler never sends it away.
     */
    public static boolean keepsHome(MinecraftServer server, ArmySquad squad) {
        return ArmyWork.state(server).duty(squad.id())
                .map(duty -> !duty.startsWith(NpcCamps.DUTY) && !duty.startsWith(FIELD)).orElse(false);
    }

    /**
     * Of the squads of the field army, how many a ruler sends away: all but a reserve of a third.
     */
    public static int sendable(int squads) {
        return squads - squads / 3;
    }

    /**
     * Whether a soldier is the sergeant of a squad of the army beyond the capital.
     */
    public static boolean sergeant(MinecraftServer server, UUID citizenId) {
        ArmyState army = ArmyWork.state(server);
        return army.squadOf(citizenId).filter(squad -> citizenId.equals(squad.commander()))
                .flatMap(squad -> army.duty(squad.id()))
                .map(duty -> duty.startsWith(NpcCamps.DUTY) || duty.startsWith(FIELD)).orElse(false);
    }

    /**
     * Whether a soldier may be moved to a post: it stands at none, or at another post of the garrison. A soldier of a squad
     * the ruler sent elsewhere stays there.
     */
    private static boolean free(ArmyState army, UUID citizen, Duty duty) {
        Optional<ArmySquad> squad = army.squadOf(citizen);
        if (squad.isEmpty()) {
            return true;
        }
        Optional<String> post = army.duty(squad.get().id());
        return post.isPresent() && !post.get().equals(duty.id());
    }

    /**
     * A fighter of the least important post that has somebody, for a more important post that lacks somebody.
     */
    @Nullable
    private static UUID donor(ArmyState army, UUID kingdomId, List<Duty> duties, Duty lacking) {
        for (int i = duties.size() - 1; i > duties.indexOf(lacking); i--) {
            Duty duty = duties.get(i);
            Optional<ArmySquad> squad = army.squadOfDuty(kingdomId, duty.id());
            if (squad.isEmpty()) {
                continue;
            }
            List<UUID> members = squad.get().members();
            for (int m = members.size() - 1; m >= 0; m--) {
                UUID citizen = members.get(m);
                if (army.soldier(citizen).map(soldier -> soldier.post() == Post.FIGHTER).orElse(false)) {
                    return citizen;
                }
            }
        }
        return null;
    }
}
