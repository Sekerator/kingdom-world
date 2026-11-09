package io.github.sekerator.kingdomworld.common.army;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.construction.BuildingEffects;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.ArmyState;
import io.github.sekerator.kingdomworld.core.army.FieldRules;
import io.github.sekerator.kingdomworld.core.army.FogOfWar;
import io.github.sekerator.kingdomworld.core.army.March;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.army.RouteSearch;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.combat.Battle;
import io.github.sekerator.kingdomworld.core.combat.Flank;
import io.github.sekerator.kingdomworld.core.combat.Side;
import io.github.sekerator.kingdomworld.core.construction.BuildingRoles;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.road.Wall;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The army in the field: where squads are and how they march to the places of their orders, through the points they were
 * told to pass, faster on roads; battles of squads far from players, which last a while so that other squads may join them
 * and strike the side or the rear of an enemy already in battle; and what each kingdom sees of the squads of others (the fog
 * of war). Near players soldiers have bodies: the squad is where its soldiers are, and they walk the same way.
 *
 * <p>Everything here is the server's: the client gets only what its kingdom sees.
 */
public final class ArmyField {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "kingdomworld.army.field.";
    /** Chunks the searches of ways look at per second, all squads together. */
    private static final int SEARCH_BUDGET = 4_000;
    /** Blocks from a point within which soldiers with bodies count as there. */
    private static final double BODY_REACH = 8.0;
    /** Blocks around a camp of robbers or a city within which an attacked place counts. */
    public static final int PLACE_REACH = 48;
    /** Ticks a map of the army keeps its kingdom watched after the last request. */
    private static final long WATCH_TICKS = 400;
    /** Seconds between two chases of an enemy that moved. */
    private static final int CHASE_SECONDS = 5;
    /** Most points of a way sent to a client. */
    public static final int MAX_SHOWN_PATH = 16;

    /** The order each march was planned for. */
    private static final Map<Long, Order> PLANNED = new HashMap<>();
    /** Points still to reach after the current stretch, by squad: the points to pass, then the place of the order. */
    private static final Map<Long, List<WorldPos>> LEGS = new HashMap<>();
    /** Searches of the way of the current stretch, by squad. */
    private static final Map<Long, RouteSearch> SEARCHES = new HashMap<>();
    private static final Map<Long, Engagement> BATTLES = new LinkedHashMap<>();
    private static final FogOfWar FOG = new FogOfWar();
    private static final Map<UUID, FogOfWar.View> VIEWS = new HashMap<>();
    private static final Map<UUID, Long> VIEWED = new HashMap<>();
    private static final Map<UUID, Long> WATCHED = new HashMap<>();
    /** Kingdoms whose squads a spy reported: viewer, then the kingdom spied, then until when. */
    private static final Map<UUID, Map<UUID, Long>> SPIED = new HashMap<>();
    private static Map<String, Set<Long>> roadChunks = Map.of();
    private static long roadsAt = Long.MIN_VALUE;
    private static long nextBattle = 1;
    private static long lastSave;
    @Nullable
    private static Boolean instant;

    /**
     * A battle of squads far from players that is not decided yet.
     */
    static final class Engagement {
        final long id;
        final WorldPos at;
        final long ends;
        final UUID firstKingdom;
        final UUID secondKingdom;
        /** The attackers and their edges. */
        final Map<Long, Double> first = new LinkedHashMap<>();
        /** The attacked and their edges. */
        final Map<Long, Double> second = new LinkedHashMap<>();
        final WorldPos firstAt;
        final WorldPos secondAt;

        Engagement(long id, WorldPos at, long ends, UUID firstKingdom, UUID secondKingdom, WorldPos firstAt, WorldPos secondAt) {
            this.id = id;
            this.at = at;
            this.ends = ends;
            this.firstKingdom = firstKingdom;
            this.secondKingdom = secondKingdom;
            this.firstAt = firstAt;
            this.secondAt = secondAt;
        }

        boolean has(long squad) {
            return first.containsKey(squad) || second.containsKey(squad);
        }
    }

    private ArmyField() {
    }

    public static void reset() {
        PLANNED.clear();
        LEGS.clear();
        SEARCHES.clear();
        BATTLES.clear();
        FOG.clear();
        VIEWS.clear();
        VIEWED.clear();
        WATCHED.clear();
        SPIED.clear();
        roadChunks = Map.of();
        roadsAt = Long.MIN_VALUE;
        nextBattle = 1;
        instant = null;
    }

    /**
     * Makes marches end at once or take their time, for game tests; {@code null} gives the server its say (marches end at
     * once on a server of game tests, so the older tests find squads in place).
     */
    public static void overrideInstant(@Nullable Boolean value) {
        instant = value;
    }

    /**
     * Whether orders put squads in place at once.
     */
    public static boolean instantFor(MinecraftServer server) {
        return instant(server);
    }

    private static boolean instant(MinecraftServer server) {
        return instant != null ? instant : server instanceof net.minecraft.gametest.framework.GameTestServer;
    }

    // ---------------------------------------------------------------- where squads are

    /**
     * Where the soldiers of a kingdom live when no order sends them out: the first barracks, else the capital.
     */
    public static WorldPos home(MinecraftServer server, UUID kingdomId) {
        for (WorldPos barracks : ArmyWork.state(server).barracksOf(kingdomId)) {
            return barracks;
        }
        return ArmyWork.capitalOf(server, kingdomId);
    }

    /**
     * Where a squad is now.
     */
    @Nullable
    public static WorldPos positionOf(MinecraftServer server, ArmySquad squad) {
        Optional<March> march = ArmyWork.state(server).march(squad.id());
        if (march.isPresent()) {
            return march.get().at();
        }
        Order order = squad.order();
        if (order.kind() == Order.Kind.FOLLOW || order.kind() == Order.Kind.ESCORT) {
            List<WorldPos> at = ArmyWork.anchorsOf(server, squad);
            if (!at.isEmpty()) {
                return at.get(0);
            }
        }
        if (order.kind() != Order.Kind.NONE && order.kind() != Order.Kind.RETREAT && !order.points().isEmpty()) {
            return order.points().get(0);
        }
        return home(server, squad.kingdomId());
    }

    /**
     * The next point a marching squad heads for, or {@code null} for one that stands where its order wants it.
     */
    @Nullable
    public static WorldPos nextPoint(MinecraftServer server, long squadId) {
        if (engaged(squadId)) {
            return null;
        }
        return ArmyWork.state(server).march(squadId).filter(march -> !march.arrived()).map(march -> march.path().get(0)).orElse(null);
    }

    /**
     * Whether a squad stands where its order wants it (or has no place to go).
     */
    public static boolean arrived(MinecraftServer server, ArmySquad squad) {
        return ArmyWork.state(server).march(squad.id()).map(March::arrived).orElse(true);
    }

    /**
     * Blocks per second a squad marches here.
     */
    public static double speed(MinecraftServer server, ArmySquad squad, WorldPos at) {
        FieldRules rules = ArmySettings.field();
        double speed = rules.marchSpeed() * squad.formation().speed();
        if (onRoad(server, at)) {
            speed *= 1.0 + rules.roadBonus();
        }
        return speed;
    }

    /**
     * Seconds until a squad is where its order wants it.
     */
    public static long secondsLeft(MinecraftServer server, ArmySquad squad) {
        return ArmyWork.state(server).march(squad.id()).map(march -> march.secondsLeft(speed(server, squad, march.at()))).orElse(0L);
    }

    /**
     * The edge a squad has in the battle it fights (its formation, and the flank it strikes), 0 for one not in battle.
     */
    public static double edgeOf(long squadId) {
        for (Engagement battle : BATTLES.values()) {
            Double edge = battle.first.containsKey(squadId) ? battle.first.get(squadId) : battle.second.get(squadId);
            if (edge != null) {
                return edge;
            }
        }
        return 0.0;
    }

    public static boolean engaged(long squadId) {
        for (Engagement battle : BATTLES.values()) {
            if (battle.has(squadId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Places of the battles going on in a dimension.
     */
    public static List<WorldPos> battles(String dimension) {
        List<WorldPos> places = new ArrayList<>();
        for (Engagement battle : BATTLES.values()) {
            if (battle.at.dimension().equals(dimension)) {
                places.add(battle.at);
            }
        }
        return places;
    }

    static boolean onRoad(MinecraftServer server, WorldPos at) {
        long now = KingdomWorldApi.gameTime(server);
        if (now - roadsAt > 1_200 || now < roadsAt) {
            roadsAt = now;
            Map<String, Set<Long>> chunks = new HashMap<>();
            for (Road road : RoadWork.state(server).roads()) {
                if (!road.built()) {
                    continue;
                }
                Set<Long> of = chunks.computeIfAbsent(road.dimension(), key -> new HashSet<>());
                for (RouteCell cell : road.cells()) {
                    of.add(RouteSearch.key(cell.x() >> 4, cell.z() >> 4));
                }
            }
            roadChunks = chunks;
        }
        return roadChunks.getOrDefault(at.dimension(), Set.of()).contains(RouteSearch.key(at.chunkX(), at.chunkZ()));
    }

    // ---------------------------------------------------------------- marching

    /**
     * Plans the march of a squad for its order: where it goes, through which points.
     */
    static void plan(MinecraftServer server, ArmySquad squad) {
        ArmyState army = ArmyWork.state(server);
        Order order = squad.order();
        PLANNED.put(squad.id(), order);
        SEARCHES.remove(squad.id());
        LEGS.remove(squad.id());
        if (!order.kind().marches() && order.kind() != Order.Kind.NONE) {
            army.forgetMarch(squad.id());
            return;
        }
        WorldPos from = positionOf(server, squad);
        if (from == null) {
            return;
        }
        List<WorldPos> legs = new ArrayList<>(order.via());
        if (order.kind() == Order.Kind.NONE) {
            if (army.march(squad.id()).isEmpty()) {
                // A squad that never marched is at home already.
                return;
            }
            legs.add(home(server, squad.kingdomId()));
        } else if (order.kind() == Order.Kind.PATROL) {
            legs.addAll(order.points());
        } else {
            legs.add(order.points().get(0));
        }
        legs.removeIf(point -> !point.dimension().equals(from.dimension()));
        if (legs.isEmpty()) {
            army.setMarch(squad.id(), March.standing(from));
            return;
        }
        if (instant(server)) {
            army.setMarch(squad.id(), March.standing(order.kind() == Order.Kind.PATROL ? order.points().get(0) : legs.get(legs.size() - 1)));
            return;
        }
        March march = army.march(squad.id()).orElse(March.standing(from));
        LEGS.put(squad.id(), legs);
        army.setMarch(squad.id(), march.towards(legs));
        search(server, squad.id(), from, legs.get(0));
    }

    private static void search(MinecraftServer server, long squadId, WorldPos from, WorldPos to) {
        RouteSearch search = new RouteSearch(from, to, chunk -> roadChunks.getOrDefault(from.dimension(), Set.of()).contains(chunk),
                ArmySettings.field().roadCost());
        onRoad(server, from);
        SEARCHES.put(squadId, search);
    }

    /**
     * Once a second: marches go on, battles are fought and decided, kingdoms whose map of the army is open look again.
     */
    static void tick(MinecraftServer server, long now) {
        ArmyState army = ArmyWork.state(server);
        FieldRules rules = ArmySettings.field();
        Set<Long> alive = new HashSet<>();
        for (ArmySquad squad : List.copyOf(army.squads())) {
            alive.add(squad.id());
            if (!squad.order().equals(PLANNED.get(squad.id()))) {
                plan(server, squad);
            }
        }
        PLANNED.keySet().retainAll(alive);
        LEGS.keySet().retainAll(alive);
        SEARCHES.keySet().retainAll(alive);
        int budget = SEARCH_BUDGET;
        for (Map.Entry<Long, RouteSearch> entry : SEARCHES.entrySet()) {
            if (budget <= 0) {
                break;
            }
            budget -= entry.getValue().advance(budget);
        }
        boolean moved = false;
        for (ArmySquad squad : List.copyOf(army.squads())) {
            moved |= march(server, army, squad, rules, now);
        }
        fight(server, rules, now);
        if (moved && now - lastSave > 600) {
            lastSave = now;
            ArmySavedData.get(server).setDirty();
        }
        if (now % (rules.fogSeconds() * 20L) < 20) {
            WATCHED.values().removeIf(until -> until < now);
            for (UUID kingdom : List.copyOf(WATCHED.keySet())) {
                look(server, kingdom, now);
            }
            VIEWS.keySet().removeIf(kingdom -> !WATCHED.containsKey(kingdom));
        }
    }

    /**
     * One second of the march of a squad.
     *
     * @return whether it moved
     */
    private static boolean march(MinecraftServer server, ArmyState army, ArmySquad squad, FieldRules rules, long now) {
        Optional<March> current = army.march(squad.id());
        if (current.isEmpty() || current.get().arrived() || engaged(squad.id())) {
            return false;
        }
        March march = current.get();
        List<WorldPos> legs = LEGS.getOrDefault(squad.id(), List.of(march.destination()));
        RouteSearch search = SEARCHES.get(squad.id());
        if (search != null && search.state() != RouteSearch.State.SEARCHING) {
            // The way of the current stretch is found: it replaces the straight line to the end of the stretch.
            List<WorldPos> path = new ArrayList<>(search.path());
            path.addAll(legs.subList(Math.min(1, legs.size()), legs.size()));
            march = march.towards(path);
            SEARCHES.remove(squad.id());
        }
        CitizenEntity body = bodyOf(squad);
        if (body != null) {
            // Near players the soldiers walk; the squad is where they are.
            WorldPos at = new WorldPos(Dimensions.id(body.level()), body.getBlockX(), body.getBlockY(), body.getBlockZ());
            List<WorldPos> path = new ArrayList<>(march.path());
            while (!path.isEmpty() && path.get(0).dimension().equals(at.dimension())
                    && Math.hypot(path.get(0).x() - at.x(), path.get(0).z() - at.z()) <= BODY_REACH) {
                path.remove(0);
            }
            march = new March(at, path, march.faceX(), march.faceZ());
        } else {
            double blocks = speed(server, squad, march.at());
            Order order = squad.order();
            if (order.kind() == Order.Kind.ATTACK && order.target() != 0) {
                // A squad that falls upon an enemy stops at striking distance, so that it is clear where it strikes from.
                WorldPos enemy = army.squad(order.target()).map(target -> positionOf(server, target)).orElse(null);
                if (enemy != null && enemy.dimension().equals(march.at().dimension())) {
                    double distance = Math.hypot(enemy.x() - march.at().x(), enemy.z() - march.at().z());
                    blocks = Math.min(blocks, Math.max(0.0, distance - rules.engage() * 0.5));
                }
            }
            if (blocks <= 0.0) {
                return false;
            }
            march = march.step(blocks);
        }
        // The current stretch ends when only the points after it are left.
        if (legs.size() > 1 && march.path().size() <= legs.size() - 1) {
            List<WorldPos> rest = new ArrayList<>(legs.subList(1, legs.size()));
            LEGS.put(squad.id(), rest);
            search(server, squad.id(), march.at(), rest.get(0));
        }
        army.setMarch(squad.id(), march);
        if (march.arrived()) {
            LEGS.remove(squad.id());
            SEARCHES.remove(squad.id());
            arrive(server, army, squad, march, now);
        }
        return true;
    }

    @Nullable
    private static CitizenEntity bodyOf(ArmySquad squad) {
        if (squad.commander() != null && CitizenBodies.body(squad.commander()) != null) {
            return CitizenBodies.body(squad.commander());
        }
        for (UUID member : squad.members()) {
            CitizenEntity body = CitizenBodies.body(member);
            if (body != null) {
                return body;
            }
        }
        return null;
    }

    /**
     * A squad came where its order wanted it.
     */
    private static void arrive(MinecraftServer server, ArmyState army, ArmySquad squad, March march, long now) {
        Order order = squad.order();
        switch (order.kind()) {
            case NONE -> army.forgetMarch(squad.id());
            case PATROL -> {
                // Round and round: the route again from its first point.
                PLANNED.remove(squad.id());
                PLANNED.put(squad.id(), order);
                List<WorldPos> legs = new ArrayList<>(order.points());
                LEGS.put(squad.id(), legs);
                army.setMarch(squad.id(), march.towards(legs));
                search(server, squad.id(), march.at(), legs.get(0));
            }
            case RETREAT -> {
                army.setOrder(null, squad.id(), Order.NONE, "", KingdomWorldApi.state(server));
                army.forgetMarch(squad.id());
                PLANNED.put(squad.id(), Order.NONE);
                tell(server, squad, Component.translatable(KEY + "returned", ArmyTexts.squadName(squad)));
            }
            case ATTACK -> {
                if (order.target() == 0) {
                    attackPlace(server, army, squad, march.at());
                }
            }
            default -> tell(server, squad, Component.translatable(KEY + "arrived", ArmyTexts.squadName(squad), ArmyTexts.orderName(order.kind()),
                    march.at().x(), march.at().z()));
        }
    }

    /**
     * A squad told to attack a place came there: a camp of robbers is fought, the defenders of a city of an enemy at war
     * meet it. Then the squad stands there.
     */
    private static void attackPlace(MinecraftServer server, ArmyState army, ArmySquad squad, WorldPos at) {
        Optional<io.github.sekerator.kingdomworld.core.road.BanditCamp> camp = RoadWork.state(server).camps().stream()
                .filter(one -> !one.cleared() && one.dimension().equals(at.dimension()) && one.distanceSqr(at.x(), at.z()) <= (double) PLACE_REACH * PLACE_REACH)
                .findFirst();
        if (camp.isPresent()) {
            io.github.sekerator.kingdomworld.common.road.BanditWork.fightSquad(server, camp.get(), squad);
        } else {
            UUID owner = KingdomWorldApi.state(server).territory().ownerOf(at.dimension(),
                    io.github.sekerator.kingdomworld.core.territory.ChunkKey.of(at.chunkX(), at.chunkZ()));
            if (owner != null && WarWork.hostileNow(server, squad.kingdomId(), owner)) {
                WarWork.assault(server, squad, owner);
            }
        }
        army.setOrder(null, squad.id(), Order.move(at), at.dimension(), KingdomWorldApi.state(server));
        PLANNED.put(squad.id(), Order.move(at));
    }

    // ---------------------------------------------------------------- battles

    /**
     * Squads told to attack a squad chase it and fall upon it when they are close; battles going on are decided when their
     * time is up.
     */
    private static void fight(MinecraftServer server, FieldRules rules, long now) {
        ArmyState army = ArmyWork.state(server);
        for (ArmySquad squad : List.copyOf(army.squads())) {
            Order order = squad.order();
            if (order.kind() != Order.Kind.ATTACK || order.target() == 0 || engaged(squad.id())) {
                continue;
            }
            Optional<ArmySquad> target = army.squad(order.target());
            WorldPos at = positionOf(server, squad);
            if (target.isEmpty() || at == null) {
                army.setOrder(null, squad.id(), Order.move(at != null ? at : order.points().get(0)), "", KingdomWorldApi.state(server));
                continue;
            }
            WorldPos enemy = positionOf(server, target.get());
            if (enemy == null || !enemy.dimension().equals(at.dimension())) {
                continue;
            }
            double distance = Math.hypot(enemy.x() - at.x(), enemy.z() - at.z());
            if (distance <= rules.engage()) {
                if (ArmyWork.hasBodies(squad) || ArmyWork.hasBodies(target.get()) || !WarWork.hostileNow(server, squad.kingdomId(), target.get().kingdomId())) {
                    // Near players soldiers fight in the world; without war nobody fights.
                    continue;
                }
                engage(server, squad, target.get(), at, enemy, rules, now);
            } else if (now % (CHASE_SECONDS * 20L) < 20 && sees(server, squad.kingdomId(), target.get())
                    && Math.hypot(enemy.x() - order.points().get(0).x(), enemy.z() - order.points().get(0).z()) > rules.engage()) {
                // The enemy moved and is still in sight: the squad turns after it.
                army.setOrder(null, squad.id(), Order.attack(enemy, target.get().id()), "", KingdomWorldApi.state(server));
            }
        }
        for (Engagement battle : List.copyOf(BATTLES.values())) {
            if (now >= battle.ends) {
                BATTLES.remove(battle.id);
                decide(server, battle);
            }
        }
    }

    /**
     * A squad falls upon an enemy: a new battle, or the battle the enemy already fights, where a blow at its side or rear
     * is stronger.
     */
    static void engage(MinecraftServer server, ArmySquad attacker, ArmySquad target, WorldPos at, WorldPos enemy, FieldRules rules, long now) {
        for (Engagement battle : BATTLES.values()) {
            if (battle.second.containsKey(target.id())) {
                Flank flank = Flank.of(battle.secondAt.x(), battle.secondAt.z(), battle.firstAt.x() - battle.secondAt.x(),
                        battle.firstAt.z() - battle.secondAt.z(), at.x(), at.z());
                battle.first.put(attacker.id(), attacker.formation().attack() * flank.edge(rules.flank()));
                joined(server, attacker, flank, battle);
                return;
            }
            if (battle.first.containsKey(target.id())) {
                Flank flank = Flank.of(battle.firstAt.x(), battle.firstAt.z(), battle.secondAt.x() - battle.firstAt.x(),
                        battle.secondAt.z() - battle.firstAt.z(), at.x(), at.z());
                battle.second.put(attacker.id(), attacker.formation().attack() * flank.edge(rules.flank()));
                joined(server, attacker, flank, battle);
                return;
            }
        }
        WorldPos middle = new WorldPos(at.dimension(), (at.x() + enemy.x()) / 2, (at.y() + enemy.y()) / 2, (at.z() + enemy.z()) / 2);
        Engagement battle = new Engagement(nextBattle++, middle, now + rules.battleSeconds() * 20L, attacker.kingdomId(), target.kingdomId(), at, enemy);
        battle.first.put(attacker.id(), attacker.formation().attack());
        battle.second.put(target.id(), target.formation().defense());
        BATTLES.put(battle.id, battle);
        ArmyState army = ArmyWork.state(server);
        army.march(attacker.id()).ifPresent(march -> army.setMarch(attacker.id(), march.facing(enemy.x() - at.x(), enemy.z() - at.z())));
        army.march(target.id()).ifPresent(march -> army.setMarch(target.id(), march.facing(at.x() - enemy.x(), at.z() - enemy.z())));
        Component where = Component.translatable(KEY + "place", middle.x(), middle.z());
        tell(server, attacker, Component.translatable(KEY + "battle_started", ArmyTexts.squadName(attacker), WarWork.name(server, target.kingdomId()), where));
        tell(server, target, Component.translatable(KEY + "battle_attacked", ArmyTexts.squadName(target), WarWork.name(server, attacker.kingdomId()), where));
        LOGGER.info("Squad #{} fell upon squad #{} at {}", attacker.id(), target.id(), middle.coordinates());
    }

    private static void joined(MinecraftServer server, ArmySquad squad, Flank flank, Engagement battle) {
        tell(server, squad, Component.translatable(KEY + "battle_joined." + flank.name().toLowerCase(java.util.Locale.ROOT), ArmyTexts.squadName(squad)));
        LOGGER.info("Squad #{} joined battle #{} from the {}", squad.id(), battle.id, flank.name().toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Decides a battle: the strengths of the sides with their edges, then what it did to every squad. The losers who
     * survive go back to their barracks; the winners stand where they fought.
     */
    static void decide(MinecraftServer server, Engagement battle) {
        ArmyState army = ArmyWork.state(server);
        List<ArmySquad> first = squads(army, battle.first.keySet());
        List<ArmySquad> second = squads(army, battle.second.keySet());
        if (first.isEmpty() || second.isEmpty()) {
            return;
        }
        Map<Long, Side> sides = new HashMap<>();
        Side firstSide = Side.NOBODY;
        Side secondSide = Side.NOBODY;
        double firstEdge = 0.0;
        double secondEdge = 0.0;
        for (ArmySquad squad : first) {
            Side side = ArmyWork.side(server, squad);
            sides.put(squad.id(), side);
            firstSide = firstSide.isEmpty() && firstSide.size() == 0 ? side : firstSide.with(side);
            firstEdge += side.power() * battle.first.get(squad.id());
        }
        for (ArmySquad squad : second) {
            Side side = ArmyWork.side(server, squad);
            sides.put(squad.id(), side);
            secondSide = secondSide.isEmpty() && secondSide.size() == 0 ? side : secondSide.with(side);
            secondEdge += side.power() * battle.second.get(squad.id());
        }
        firstEdge = firstSide.power() > 0 ? firstEdge / firstSide.power() : 1.0;
        secondEdge = secondSide.power() > 0 ? secondEdge / secondSide.power() : 1.0;
        Battle.Result result = Battle.resolve(firstSide, firstEdge, secondSide, secondEdge, ArmySettings.rules().battle(), ArmyWork.rolls());
        if (result.winner() == Battle.Winner.NONE) {
            return;
        }
        boolean firstWon = result.winner() == Battle.Winner.FIRST;
        Component where = Component.translatable(KEY + "place", battle.at.x(), battle.at.z());
        int firstKills = 0;
        int secondKills = 0;
        for (ArmySquad squad : first) {
            ArmyWork.Fought fought = ArmyWork.apply(server, squad, sides.get(squad.id()), result, battle.at, ArmySettings.rules());
            firstKills = Math.max(firstKills, fought.kills());
            ArmyWork.announce(server, squad, fought, Component.translatable(KEY + "battle_at", WarWork.name(server, battle.secondKingdom), where));
        }
        Battle.Result mirrored = WarWork.mirror(result);
        for (ArmySquad squad : second) {
            ArmyWork.Fought fought = ArmyWork.apply(server, squad, sides.get(squad.id()), mirrored, battle.at, ArmySettings.rules());
            secondKills = Math.max(secondKills, fought.kills());
            ArmyWork.announce(server, squad, fought, Component.translatable(KEY + "battle_at", WarWork.name(server, battle.firstKingdom), where));
        }
        WarWork.scoreFieldBattle(server, battle.firstKingdom, battle.secondKingdom, firstWon, firstKills, secondKills);
        // The losers who are left go home; the winners who attacked stand where they fought.
        for (ArmySquad squad : firstWon ? second : first) {
            army.squad(squad.id()).ifPresent(left -> army.setOrder(null, left.id(), Order.retreat(home(server, left.kingdomId())), "",
                    KingdomWorldApi.state(server)));
        }
        for (ArmySquad squad : firstWon ? first : second) {
            army.squad(squad.id()).filter(left -> left.order().kind() == Order.Kind.ATTACK).ifPresent(left -> {
                WorldPos at = positionOf(server, left);
                if (at != null) {
                    army.setOrder(null, left.id(), Order.move(at), "", KingdomWorldApi.state(server));
                }
            });
        }
        LOGGER.info("Battle #{} at {} decided: {} won (edges {} and {})", battle.id, battle.at.coordinates(), firstWon ? "the attackers" : "the attacked",
                String.format(java.util.Locale.ROOT, "%.2f", firstEdge), String.format(java.util.Locale.ROOT, "%.2f", secondEdge));
    }

    private static List<ArmySquad> squads(ArmyState army, Set<Long> ids) {
        List<ArmySquad> found = new ArrayList<>();
        for (long id : ids) {
            army.squad(id).filter(squad -> !ArmyWork.hasBodies(squad)).ifPresent(found::add);
        }
        return found;
    }

    /**
     * Ends every battle at once, for game tests.
     */
    public static void decideAll(MinecraftServer server) {
        for (Engagement battle : List.copyOf(BATTLES.values())) {
            BATTLES.remove(battle.id);
            decide(server, battle);
        }
    }

    /**
     * Runs one second of the field at once, for game tests.
     */
    public static void step(MinecraftServer server) {
        tick(server, KingdomWorldApi.gameTime(server));
    }

    // ---------------------------------------------------------------- the fog of war

    /**
     * A spy of a kingdom scouted another: its squads stay in sight for a while.
     */
    public static void spied(MinecraftServer server, UUID viewer, UUID target) {
        SPIED.computeIfAbsent(viewer, key -> new HashMap<>()).put(target, KingdomWorldApi.gameTime(server) + ArmySettings.field().spyTicks());
    }

    /**
     * What a kingdom sees of the squads of others now, looking again when the last look is old.
     */
    public static FogOfWar.View view(MinecraftServer server, UUID kingdomId) {
        long now = KingdomWorldApi.gameTime(server);
        WATCHED.put(kingdomId, now + WATCH_TICKS);
        FogOfWar.View view = VIEWS.get(kingdomId);
        Long at = VIEWED.get(kingdomId);
        if (view == null || at == null || now - at >= ArmySettings.field().fogSeconds() * 20L || now < at) {
            view = look(server, kingdomId, now);
        }
        return view;
    }

    /**
     * Whether a kingdom sees a squad now.
     */
    public static boolean sees(MinecraftServer server, UUID kingdomId, ArmySquad squad) {
        if (squad.kingdomId().equals(kingdomId)) {
            return true;
        }
        WorldPos at = positionOf(server, squad);
        if (at == null) {
            return false;
        }
        FogOfWar.Contact contact = new FogOfWar.Contact(squad.id(), squad.kingdomId(), at, size(server, squad));
        return !FogOfWar.visible(kingdomId, eyes(server, kingdomId), List.of(contact), always(server, kingdomId)).isEmpty();
    }

    /**
     * Whether a kingdom sees a squad now or remembers where it was last seen.
     */
    public static boolean known(MinecraftServer server, UUID kingdomId, long squadId) {
        Optional<ArmySquad> squad = ArmyWork.state(server).squad(squadId);
        if (squad.isEmpty()) {
            return false;
        }
        if (sees(server, kingdomId, squad.get())) {
            return true;
        }
        return view(server, kingdomId).lastSeen().stream().anyMatch(mark -> mark.squadId() == squadId);
    }

    private static FogOfWar.View look(MinecraftServer server, UUID kingdomId, long now) {
        ArmyState army = ArmyWork.state(server);
        List<FogOfWar.Contact> contacts = new ArrayList<>();
        Set<Long> alive = new HashSet<>();
        for (ArmySquad squad : army.squads()) {
            alive.add(squad.id());
            if (squad.kingdomId().equals(kingdomId)) {
                continue;
            }
            WorldPos at = positionOf(server, squad);
            if (at != null) {
                contacts.add(new FogOfWar.Contact(squad.id(), squad.kingdomId(), at, size(server, squad)));
            }
        }
        FogOfWar.View view = FOG.look(kingdomId, eyes(server, kingdomId), contacts, always(server, kingdomId), alive, now,
                ArmySettings.field().lastSeenTicks());
        VIEWS.put(kingdomId, view);
        VIEWED.put(kingdomId, now);
        return view;
    }

    /**
     * Kingdoms whose squads a kingdom sees wherever they are: its allies, and kingdoms its spies scouted lately.
     */
    static Set<UUID> always(MinecraftServer server, UUID kingdomId) {
        long now = KingdomWorldApi.gameTime(server);
        Set<UUID> always = new HashSet<>();
        if (!WarWork.isReadOnly(server)) {
            always.addAll(WarWork.state(server).alliesOf(kingdomId, now));
        }
        Map<UUID, Long> spied = SPIED.get(kingdomId);
        if (spied != null) {
            spied.values().removeIf(until -> until < now);
            always.addAll(spied.keySet());
        }
        return always;
    }

    /**
     * What sees for a kingdom: its squads (archers and scouts farther), its working towers and walls, the walls of its
     * cities, and the areas its scouts watch.
     */
    static List<FogOfWar.Eye> eyes(MinecraftServer server, UUID kingdomId) {
        FieldRules rules = ArmySettings.field();
        List<FogOfWar.Eye> eyes = new ArrayList<>();
        for (ArmySquad squad : ArmyWork.state(server).squadsOf(kingdomId)) {
            WorldPos at = positionOf(server, squad);
            if (at == null) {
                continue;
            }
            double radius = rules.sight() * (1.0 + rules.archerSight() * archerShare(server, squad));
            if (squad.order().kind() == Order.Kind.SCOUT) {
                radius *= 1.0 + rules.scoutSight();
                if (arrived(server, squad)) {
                    WorldPos center = squad.order().points().get(0);
                    eyes.add(new FogOfWar.Eye(center.dimension(), center.x(), center.z(), (int) (squad.order().radius() * 16 + 8 + radius)));
                }
            }
            eyes.add(new FogOfWar.Eye(at.dimension(), at.x(), at.z(), (int) radius));
        }
        for (String role : List.of(BuildingRoles.TOWER, BuildingRoles.WALL)) {
            for (BuildingEffects.Effect effect : BuildingEffects.working(server, kingdomId, role)) {
                var box = effect.building().box();
                eyes.add(new FogOfWar.Eye(effect.building().dimension(), (box.minX() + box.maxX()) / 2, (box.minZ() + box.maxZ()) / 2, rules.postSight()));
            }
        }
        for (Wall wall : RoadWork.state(server).walls()) {
            if (wall.kingdomId().equals(kingdomId) && wall.built()) {
                eyes.add(new FogOfWar.Eye(wall.dimension(), wall.centerX(), wall.centerZ(), rules.postSight()));
            }
        }
        return eyes;
    }

    private static double archerShare(MinecraftServer server, ArmySquad squad) {
        if (squad.mercenaries()) {
            return ArmyWork.state(server).band(squad.band()).map(band -> band.fighters().isEmpty() ? 0.0
                    : band.fighters().stream().filter(io.github.sekerator.kingdomworld.core.army.Mercenary::ranged).count() / (double) band.fighters().size())
                    .orElse(0.0);
        }
        if (squad.members().isEmpty()) {
            return 0.0;
        }
        int archers = 0;
        for (UUID member : squad.members()) {
            if (KingdomWorldApi.state(server).citizens().get(member).map(citizen -> Professions.ARCHER.id().equals(citizen.professionId())).orElse(false)) {
                archers++;
            }
        }
        return archers / (double) squad.members().size();
    }

    /**
     * How many fight in a squad.
     */
    public static int size(MinecraftServer server, ArmySquad squad) {
        if (squad.mercenaries()) {
            return ArmyWork.state(server).band(squad.band()).map(band -> band.fighters().size()).orElse(0);
        }
        return squad.members().size();
    }

    // ---------------------------------------------------------------- messages

    /**
     * Tells the members of a kingdom who run its army; rare: arrivals, battles begun, returns.
     */
    private static void tell(MinecraftServer server, ArmySquad squad, Component message) {
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdom(server, squad.kingdomId());
        kingdom.ifPresent(found -> KingdomActions.notifyMembers(server, found, Permission.MANAGE_ARMY, message.copy().withStyle(ChatFormatting.AQUA)));
    }

    /**
     * Whether a player may see and command the army of a kingdom on the map.
     */
    public static boolean commands(ServerPlayer player, UUID kingdomId) {
        return ArmyWork.state(player.server).checkArmy(player.getUUID(), kingdomId, KingdomWorldApi.state(player.server)).ok();
    }
}
