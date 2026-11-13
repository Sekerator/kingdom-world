package io.github.sekerator.kingdomworld.common.npc;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmySettings;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.road.CityWalls;
import io.github.sekerator.kingdomworld.common.road.RoadSync;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.army.ArmyRules;
import io.github.sekerator.kingdomworld.core.army.ArmyState;
import io.github.sekerator.kingdomworld.core.army.Branch;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.land.Tier;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
import io.github.sekerator.kingdomworld.core.npc.Settlement;
import io.github.sekerator.kingdomworld.core.npc.SettlementLayouts;
import io.github.sekerator.kingdomworld.core.npc.SettlementLife;
import io.github.sekerator.kingdomworld.core.npc.SettlementStage;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The guard, the wall and the posts of a settlement of a state as it is raised: its guard (a number while it is data) comes from
 * abroad as soldiers of the guard with the kits of their posts paid by the treasury, without taking a citizen from his work; a
 * large city gets its wall of its style and a small town of a large kingdom its palisade, paid as data and placed when their
 * chunks load; then the guard takes its post at the town hall.
 */
public final class SettlementGarrison {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Soldiers coming from abroad in a tick at most. */
    private static final int GUARDS_PER_TICK = 2;

    private SettlementGarrison() {
    }

    /**
     * The soldiers who serve a settlement in person once it stands: the guard of a large city (8) and of a small town (4), the
     * watchmen of a village (2) and of a hamlet (1).
     */
    public static int guards(Tier tier) {
        return SettlementLife.watch(tier);
    }

    /**
     * The soldiers of the guard of a kingdom that serve a standing settlement, by settlement.
     */
    public static Map<Integer, Integer> guardsBySettlement(MinecraftServer server, UUID kingdomId) {
        Map<Integer, Integer> counts = new HashMap<>();
        ArmyState army = ArmyWork.state(server);
        KingdomWorldState world = KingdomWorldApi.state(server);
        army.branchOf(kingdomId, Branch.GUARD).forEach(soldier -> world.citizens().get(soldier.citizenId())
                .filter(citizen -> citizen.settlement() != 0)
                .ifPresent(citizen -> counts.merge(citizen.settlement(), 1, Integer::sum)));
        return counts;
    }

    /**
     * The guard of the standing settlements of a kingdom, to add to the guard of its capital.
     */
    public static int standingGuards(MinecraftServer server, UUID kingdomId) {
        int guards = 0;
        for (Settlement settlement : NpcWork.state(server).settlementsOf(kingdomId)) {
            if (settlement.status() == Settlement.Status.STANDING || settlement.status() == Settlement.Status.RAISING
                    && SettlementStage.byId(settlement.stage()).ordinal() > SettlementStage.GUARDS.ordinal()) {
                guards += guards(settlement.tier());
            }
        }
        return guards;
    }

    /**
     * The soldiers of the guard of a kingdom who serve its standing settlements in person, at most as many as each keeps.
     */
    public static int serving(MinecraftServer server, UUID kingdomId) {
        Map<Integer, Integer> serving = guardsBySettlement(server, kingdomId);
        int count = 0;
        for (Settlement settlement : NpcWork.state(server).settlementsOf(kingdomId)) {
            if (settlement.status() == Settlement.Status.STANDING || settlement.status() == Settlement.Status.RAISING
                    && SettlementStage.byId(settlement.stage()).ordinal() > SettlementStage.GUARDS.ordinal()) {
                count += Math.min(guards(settlement.tier()), serving.getOrDefault(settlement.id(), 0));
            }
        }
        return count;
    }

    /**
     * The posts of the guards and watches of the settlements of a kingdom that stand: a large city its gate (2), its round (3)
     * and its town hall (3), a small town its gate (1) and its round (3), a village and a hamlet the round of their watch. The
     * gate is the one towards the settlement it belongs to.
     */
    static List<NpcGarrison.Duty> duties(MinecraftServer server, UUID kingdomId, String dimension) {
        List<NpcGarrison.Duty> duties = new java.util.ArrayList<>();
        NpcState state = NpcWork.state(server);
        Optional<NpcSite> site = state.kingdom(kingdomId).map(io.github.sekerator.kingdomworld.core.npc.NpcKingdom::siteId).flatMap(state::site);
        if (site.isEmpty() || !Dimensions.id(server.overworld()).equals(dimension)) {
            return duties;
        }
        for (Settlement settlement : state.settlementsOf(kingdomId)) {
            boolean serves = settlement.status() == Settlement.Status.STANDING || settlement.status() == Settlement.Status.RAISING
                    && SettlementStage.byId(settlement.stage()).ordinal() > SettlementStage.GUARDS.ordinal();
            if (!serves || settlement.tier() == Tier.CAPITAL || guards(settlement.tier()) <= 0) {
                continue;
            }
            SettlementLayouts.Layout layout = SettlementRaiser.cachedLayout(settlement, site.get());
            int id = settlement.id();
            int y = settlement.groundY() + 1;
            java.util.function.Function<int[], WorldPos> at = point -> new WorldPos(dimension, settlement.x() + point[0],
                    y + (point.length > 2 ? point[2] : 0), settlement.z() + point[1]);
            Optional<io.github.sekerator.kingdomworld.core.npc.NpcComposition.Post> round = layout.post(SettlementLayouts.ROUND);
            List<WorldPos> route = new java.util.ArrayList<>();
            round.ifPresent(post -> post.points().forEach(point -> route.add(at.apply(point))));
            Optional<WorldPos> gate = gateTowardsParent(state, settlement, layout).map(at);
            WorldPos hall = new WorldPos(dimension, settlement.x(), y, settlement.z());
            switch (settlement.tier()) {
                case CITY -> {
                    gate.ifPresent(point -> duties.add(new NpcGarrison.Duty("sgate_" + id, Order.guard(point, 0), 2, 2, Branch.GUARD, null, id)));
                    if (route.size() >= 2) {
                        duties.add(new NpcGarrison.Duty("sround_" + id, Order.patrol(route), 3, 3, Branch.GUARD, null, id));
                    }
                    String hallId = settlement.cityId() != 0 ? "town_" + settlement.cityId() : "shall_" + id;
                    duties.add(new NpcGarrison.Duty(hallId, Order.guard(hall, 1), 3, 3, Branch.GUARD, null, id));
                }
                case TOWN -> {
                    gate.ifPresent(point -> duties.add(new NpcGarrison.Duty("sgate_" + id, Order.guard(point, 0), 1, 1, Branch.GUARD, null, id)));
                    duties.add(new NpcGarrison.Duty("sround_" + id, route.size() >= 2 ? Order.patrol(route) : Order.guard(hall, 1), 3, 3,
                            Branch.GUARD, null, id));
                }
                default -> {
                    int watch = guards(settlement.tier());
                    duties.add(new NpcGarrison.Duty("swatch_" + id, route.size() >= 2 ? Order.patrol(route) : Order.guard(hall, 2), watch, watch,
                            Branch.GUARD, null, id));
                }
            }
        }
        return duties;
    }

    /**
     * The post inside the gate of a settlement that looks towards the settlement it belongs to (the first gate when it belongs
     * to none), {x, z} or {x, z, y} from its middle.
     */
    static Optional<int[]> gateTowardsParent(NpcState state, Settlement settlement, SettlementLayouts.Layout layout) {
        List<io.github.sekerator.kingdomworld.core.npc.NpcComposition.Post> gates = layout.gatePosts();
        if (gates.isEmpty()) {
            return Optional.empty();
        }
        Optional<Settlement> parent = state.settlement(settlement.parent());
        double dx = parent.map(p -> (double) p.x() - settlement.x()).orElse(0.0);
        double dz = parent.map(p -> (double) p.z() - settlement.z()).orElse(0.0);
        int[] best = gates.get(0).points().get(0);
        double bestScore = -Double.MAX_VALUE;
        for (io.github.sekerator.kingdomworld.core.npc.NpcComposition.Post post : gates) {
            int[] point = post.points().get(0);
            double score = (point[0] * dx + point[1] * dz) / Math.max(1.0, Math.hypot(point[0], point[1]));
            if (score > bestScore) {
                bestScore = score;
                best = point;
            }
        }
        return Optional.of(best);
    }

    /**
     * The settlement whose town a city of the roads is, if any.
     */
    public static Optional<Settlement> ofCity(MinecraftServer server, UUID kingdomId, long cityId) {
        for (Settlement settlement : NpcWork.state(server).settlementsOf(kingdomId)) {
            if (settlement.cityId() == cityId && cityId != 0) {
                return Optional.of(settlement);
            }
        }
        return Optional.empty();
    }

    /**
     * The guard of a settlement comes from abroad, a part a tick: each soldier lives in the settlement, serves in the guard and
     * gets the kit of its post from the treasury, by the books.
     */
    static void guards(MinecraftServer server, Settlement settlement, SettlementLayouts.Layout layout, long deadline) {
        int wanted = guards(settlement.tier());
        if (wanted <= 0 || ArmyWork.isReadOnly(server) || KingdomWorldApi.isReadOnly(server)) {
            settlement.give(Settlement.GIVEN_GUARDS);
            settlement.setStage(SettlementStage.ROADS.id(), 0);
            return;
        }
        int have = guardsBySettlement(server, settlement.kingdomId()).getOrDefault(settlement.id(), 0);
        int came = have >= wanted ? 0 : come(server, settlement, Math.min(GUARDS_PER_TICK, wanted - have), deadline);
        // Nobody came while there was time: the world has no room for more people.
        boolean nobody = have < wanted && came == 0 && System.nanoTime() - deadline < 0;
        if (came < 0 || nobody || have + came >= wanted) {
            // Whole, or no room in the army of the kingdom: the settlement keeps the rest of its guard as a number.
            settlement.give(Settlement.GIVEN_GUARDS);
            LOGGER.info("The guard of {} came: {} soldier(s) of {}", settlement, have + Math.max(0, came), wanted);
            settlement.setStage(SettlementStage.ROADS.id(), 0);
        }
    }

    /**
     * Soldiers of the guard or the watch of a settlement come from abroad: each lives in the settlement, serves in the guard,
     * gets the kit of its post paid by the treasury and sleeps in a bunk of its barracks or a free bed of its houses. A large
     * city and a small town get warriors and archers by turns, a village and a hamlet warriors.
     *
     * @return how many came, or -1 when the army of the kingdom had no room
     */
    static int come(MinecraftServer server, Settlement settlement, int count, long deadline) {
        UUID kingdomId = settlement.kingdomId();
        ArmyState army = ArmyWork.state(server);
        KingdomWorldState world = KingdomWorldApi.state(server);
        ArmyRules rules = ArmySettings.rulesFor(server, kingdomId);
        WorldPos hall = new WorldPos(Dimensions.id(server.overworld()), settlement.x(), settlement.groundY() + 1, settlement.z());
        boolean watch = settlement.tier() == Tier.VILLAGE || settlement.tier() == Tier.HAMLET;
        int joined = 0;
        while (joined < count && System.nanoTime() - deadline < 0) {
            Optional<Citizen> recruit = CitizenLife.arrive(server, kingdomId, hall, false, false);
            if (recruit.isEmpty()) {
                break;
            }
            UUID id = recruit.get().id();
            int serving = guardsBySettlement(server, kingdomId).getOrDefault(settlement.id(), 0);
            String profession = watch || serving % 2 == 0 ? Professions.WARRIOR.id() : Professions.ARCHER.id();
            Outcome outcome = ArmyWork.change(server, "enlist a guard of a settlement",
                    state -> state.recruit(null, kingdomId, id, profession, KingdomWorldApi.gameTime(server), rules, world));
            if (!outcome.ok()) {
                LOGGER.info("The guard of {} was not taken into the army: {}", settlement, outcome.error());
                KingdomWorldApi.change(server, "send a recruit back", state -> {
                    state.citizens().registry().remove(id);
                    return Outcome.DONE;
                });
                return joined > 0 ? joined : -1;
            }
            KingdomWorldApi.change(server, "settle a guard", state -> {
                recruit.get().setSettlement(settlement.id());
                return Outcome.DONE;
            });
            army.setBranch(null, id, Branch.GUARD, world);
            army.soldier(id).ifPresent(soldier -> io.github.sekerator.kingdomworld.common.army.ArmyKits.issue(server, recruit.get(), soldier, false));
            // A guard sleeps in a bunk of the barracks of the settlement if it has one, otherwise in a free bed of its houses.
            ArmyWork.billet(server, recruit.get());
            joined++;
        }
        return joined;
    }

    /** Soldiers of the guards and watches of the settlements of a kingdom who come from abroad in a day at most. */
    static final int REFILL_PER_DAY = 8;

    /**
     * Once a day the settlements of a kingdom of the world that stand get from abroad the guards and watchmen they lost,
     * a few a day: the guard of a settlement is its own and is not taken from the people of the capital.
     *
     * @return how many came
     */
    static int refill(MinecraftServer server, UUID kingdomId) {
        if (ArmyWork.isReadOnly(server) || KingdomWorldApi.isReadOnly(server)) {
            return 0;
        }
        Map<Integer, Integer> serving = guardsBySettlement(server, kingdomId);
        int left = REFILL_PER_DAY;
        int came = 0;
        for (Settlement settlement : NpcWork.state(server).settlementsOf(kingdomId)) {
            if (left <= 0) {
                break;
            }
            if (settlement.status() != Settlement.Status.STANDING || SettlementRaiser.renewing(settlement)
                    || !settlement.given(Settlement.GIVEN_LIVING)) {
                continue;
            }
            int lacking = guards(settlement.tier()) - serving.getOrDefault(settlement.id(), 0);
            if (lacking <= 0) {
                continue;
            }
            int joined = come(server, settlement, Math.min(left, lacking), Long.MAX_VALUE / 2 + System.nanoTime());
            if (joined < 0) {
                break;
            }
            left -= joined;
            came += joined;
        }
        if (came > 0) {
            LOGGER.info("{} soldier(s) of the guard came from abroad to the settlements of {}", came, kingdomId);
        }
        return came;
    }

    /**
     * The wall of a large city in the style of its kingdom (stone or a palisade) and the palisade of a small town of a large
     * kingdom, around its town hall: laid out as data, paid a part a tick, placed as their chunks load.
     */
    static void wall(MinecraftServer server, ServerLevel level, Settlement settlement, NpcSite site, SettlementLayouts.Layout layout) {
        if (layout.wall() <= 0 || settlement.cityId() == 0 || RoadWork.isReadOnly(server)) {
            settlement.setStage(SettlementStage.LAW.id(), 0);
            return;
        }
        UUID kingdomId = settlement.kingdomId();
        Optional<City> city = RoadWork.state(server).city(settlement.cityId());
        if (city.isEmpty()) {
            settlement.setStage(SettlementStage.LAW.id(), 0);
            return;
        }
        Optional<Wall> existing = RoadWork.state(server).wallOf(city.get().id(), kingdomId);
        if (existing.isEmpty()) {
            Wall[] made = new Wall[1];
            boolean stone = layout.city() != null && layout.city().stoneWall();
            Outcome built = CityWalls.build(server, null, kingdomId, city.get().id(), stone ? Wall.WallClass.STONE : Wall.WallClass.PALISADE, made,
                    layout.wall());
            if (!built.ok() || made[0] == null) {
                LOGGER.warn("The wall of {} was refused: {}", settlement, built.error());
                settlement.setStage(SettlementStage.LAW.id(), 0);
                return;
            }
            existing = Optional.of(made[0]);
        }
        if (settlement.step() == 0) {
            int gates = gates(server, settlement, layout, existing.get());
            LOGGER.info("The wall of {} has {} gate(s)", settlement, gates);
            settlement.setStage(SettlementStage.WALL.id(), 1);
            return;
        }
        if (NpcSpawner.payWall(server, level, site, existing.get())) {
            RoadSync.changed();
            settlement.setStage(SettlementStage.LAW.id(), 0);
        }
    }

    /**
     * The gates of the wall of a settlement: those of its plan, one towards every settlement a road of the plan leads to from
     * it, and one wherever a road laid already comes in.
     *
     * @return the gates of the wall now
     */
    static int gates(MinecraftServer server, Settlement settlement, SettlementLayouts.Layout layout, Wall wall) {
        for (int[] gate : layout.gates()) {
            CityWalls.cutGate(server, wall, settlement.x() + gate[0], settlement.z() + gate[1]);
        }
        NpcState state = NpcWork.state(server);
        for (io.github.sekerator.kingdomworld.core.npc.PlannedRoad road : state.plannedRoads()) {
            int other = road.from() == settlement.id() ? road.to() : road.to() == settlement.id() ? road.from() : 0;
            Optional<Settlement> there = other == 0 ? Optional.empty() : state.settlement(other);
            if (there.isEmpty()) {
                continue;
            }
            double dx = there.get().x() - settlement.x();
            double dz = there.get().z() - settlement.z();
            double length = Math.max(1.0, Math.max(Math.abs(dx), Math.abs(dz)));
            // The ring of a wall is a square: the gate lies where the line to the other settlement crosses it.
            int x = settlement.x() + (int) Math.round(dx / length * wall.radius());
            int z = settlement.z() + (int) Math.round(dz / length * wall.radius());
            CityWalls.cutGate(server, wall, x, z);
        }
        for (io.github.sekerator.kingdomworld.core.road.Road road : List.copyOf(RoadWork.state(server).roads())) {
            if (road.kingdomId().equals(settlement.kingdomId())) {
                CityWalls.gatesFor(server, road);
            }
        }
        return wall.gates().size();
    }

    /**
     * The town of a settlement takes the laws of its ruler (set for the kingdom at its founding) and its guard its post at the
     * town hall.
     */
    static void law(MinecraftServer server, Settlement settlement, NpcSite site, SettlementLayouts.Layout layout) {
        if (!ArmyWork.isReadOnly(server) && guards(settlement.tier()) > 0) {
            NpcGarrison.station(server, settlement.kingdomId());
        }
        settlement.setStage(SettlementStage.DONE.id(), 0);
    }

    static int standing(NpcState state, UUID kingdomId) {
        int count = 0;
        for (Settlement settlement : state.settlementsOf(kingdomId)) {
            if (settlement.status() == Settlement.Status.STANDING) {
                count++;
            }
        }
        return count;
    }
}
