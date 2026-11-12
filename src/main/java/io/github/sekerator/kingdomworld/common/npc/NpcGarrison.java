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
 * Every town has a few guards of its own. The army stands in reserve at the barracks, its captain with a part of it at the
 * palace. A capital of the first layout has no named posts: its gates, a storehouse of the register of buildings and the
 * throne stand in for them.
 *
 * <p>The posts are reckoned anew every time, not once: a gate that is gone loses its guards, a new one gets some, and a
 * post that lost soldiers is filled from the less important ones. Squads the ruler sent elsewhere (a road patrol, the
 * guards of a caravan, a march in a war) are left alone.
 */
public final class NpcGarrison {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Most soldiers of the people of a kingdom, in a hundred: the rest works. */
    private static final int MOST_PERCENT = 50;

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
     * The soldiers a kingdom should have: {the guard with the guards of its towns, the army}. Never more than half of its
     * people serve.
     */
    public static int[] wanted(MinecraftServer server, UUID kingdomId) {
        Optional<NpcSite> site = site(server, kingdomId);
        if (site.isEmpty()) {
            return new int[]{0, 0};
        }
        NpcRules rules = NpcSettings.rules();
        NpcLevel level = site.get().level();
        int guards = rules.guards(level) + RoadWork.state(server).citiesOf(kingdomId).size() * rules.townGuards(level);
        int army = rules.army(level);
        int most = KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdomId).size() * MOST_PERCENT / 100;
        if (guards + army > most) {
            army = Math.min(army, most / 3);
            guards = Math.max(0, most - army);
        }
        return new int[]{guards, army};
    }

    // ---------------------------------------------------------------- at the founding

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
        List<Soldier> soldiers = new ArrayList<>(army.soldiersOf(id));
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
        // A capital of the first layout was planned for a handful of soldiers: all of them are its guard.
        int inArmy = site.districts() ? inCapital * rules.army(level) / Math.max(1, rules.guards(level) + rules.army(level)) : 0;
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
        ArmyStaff.Report report = ArmyStaff.muster(server, id, true);
        for (Branch branch : Branch.values()) {
            army.officer(id, branch, Post.VICE_CAPTAIN).ifPresent(officer -> army.reward(officer.citizenId(), 1_200, 0, 0));
            army.officer(id, branch, Post.CAPTAIN).ifPresent(officer -> army.reward(officer.citizenId(), 2_700, 0, 0));
        }
        LOGGER.info("Garrison of {}: {} of the guard, {} of the army, {} officer(s), {} piece(s) of gear", id,
                army.branchOf(id, Branch.GUARD).size(), army.branchOf(id, Branch.ARMY).size(), report.promoted(), report.issued());
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
        ArmyStaff.daily(server, kingdomId, wanted[0], wanted[1]);
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
        duties.add(new Duty("reserve", Order.guard(barracks, 0), ArmySquad.MAX_MEMBERS, ArmySquad.MAX_MEMBERS, Branch.ARMY, Post.VICE_CAPTAIN));
        duties.add(new Duty("reserve_palace", Order.guard(palace, 0), 0, 6, Branch.ARMY, Post.CAPTAIN));
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
        // Who is left over: the army to its reserve (a part of it with its captain), the guard to the posts with room.
        if (branch == Branch.ARMY) {
            Duty reserve = duties.get(0);
            Duty palace = duties.size() > 1 ? duties.get(1) : reserve;
            boolean captain = army.officer(kingdomId, branch, Post.CAPTAIN).isPresent();
            for (UUID soldier : idle) {
                Duty to = captain && members(army, kingdomId, palace) < Math.min(palace.most(), 1 + army.branchOf(kingdomId, branch).size() / 3)
                        ? palace : reserve;
                if (assign(server, kingdomId, to, soldier, rules)) {
                    moved++;
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
