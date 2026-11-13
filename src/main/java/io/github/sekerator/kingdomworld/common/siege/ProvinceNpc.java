package io.github.sekerator.kingdomworld.common.siege;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyField;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.npc.NpcGarrison;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyState;
import io.github.sekerator.kingdomworld.core.diplomacy.War;
import io.github.sekerator.kingdomworld.core.diplomacy.WarGoal;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
import io.github.sekerator.kingdomworld.core.siege.Hold;
import io.github.sekerator.kingdomworld.core.siege.ProvinceHolds;
import io.github.sekerator.kingdomworld.core.siege.SiegeState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Rulers of the world in a war on a state, province by province. The ruler of the attackers sends its field army to hold the
 * nearest province of the enemy where people live (in a war for land, once none is left, the boundary stone of the nearest
 * empty one), one province at a time, and on to the next once it is taken. The ruler of the state sends relief to the province
 * of it the enemy holds: its field army, or half of its free squads. Both march with the order "hold", so that far from
 * players they fight at the place of the province ({@link SiegeHolds}); decided with the sieges, every {@value SiegeNpc#EVERY}
 * ticks. Squads of relief go home once their ruler is at peace; squads of an attack are called home by the peace itself.
 */
final class ProvinceNpc {
    /**
     * Where a ruler sends its army now.
     *
     * @param war      the war of an attack, 0 for relief
     * @param province the province
     * @param point    the point of the order: the place of the province on the ground
     */
    private record Aim(long war, int province, WorldPos point) {
    }

    /** The province each ruler attacking a state marches on now, by kingdom. */
    private static final Map<UUID, Aim> MARCHES = new HashMap<>();
    /** The province of its own each ruler of a state relieves now, by kingdom. */
    private static final Map<UUID, Aim> RELIEFS = new HashMap<>();

    private ProvinceNpc() {
    }

    static void reset() {
        MARCHES.clear();
        RELIEFS.clear();
    }

    static void tick(MinecraftServer server, SiegeState state, DiplomacyState diplomacy) {
        Optional<ProvinceMap> attached = KingdomWorldApi.state(server).territory().provinceMap();
        if (attached.isEmpty()) {
            return;
        }
        ProvinceMap map = attached.get();
        NpcState npc = NpcWork.state(server);
        Set<UUID> marching = new HashSet<>();
        Set<UUID> relieving = new HashSet<>();
        for (War war : List.copyOf(diplomacy.wars())) {
            if (!war.active() || (war.goal() != WarGoal.LAND && war.goal() != WarGoal.CITY) || !SiegeHolds.holdsProvinces(map, war.defender())) {
                continue;
            }
            for (UUID member : war.members(War.Side.ATTACKER)) {
                if (WarWork.isNpc(server, member) && marching.add(member) && SiegeWork.open(server, member, war.defender())) {
                    march(server, state, map, npc, war, member);
                }
            }
            if (WarWork.isNpc(server, war.defender()) && relieving.add(war.defender())) {
                relieve(server, state, diplomacy, map, npc, war.defender());
            }
        }
        MARCHES.keySet().retainAll(marching);
        RELIEFS.keySet().retainAll(relieving);
        dismiss(server, diplomacy, map, npc);
    }

    // ---------------------------------------------------------------- the attack

    /**
     * A ruler of the attackers sends its army on to the province at hand: the one it marches on while it is not taken, else
     * the next nearest to where its army stands. The squads it sent before are those holding a place on the land of the enemy
     * (at first its capital, where they went when the war started); with none left it sends its field army.
     */
    private static void march(MinecraftServer server, SiegeState state, ProvinceMap map, NpcState npc, War war, UUID member) {
        String dimension = map.dimension();
        Optional<Capital> home = RoadWork.capital(server, member);
        if (home.isEmpty() || !home.get().dimension().equals(dimension)) {
            return;
        }
        UUID defender = war.defender();
        Set<UUID> enemies = war.members(War.Side.DEFENDER);
        TerritoryIndex territory = KingdomWorldApi.state(server).territory();
        List<ArmySquad> army = new ArrayList<>();
        for (ArmySquad squad : ArmyWork.state(server).squadsOf(member)) {
            Optional<WorldPos> anchor = squad.order().anchor();
            if (squad.order().kind() != Order.Kind.HOLD || squad.members().isEmpty() || anchor.isEmpty()
                    || !anchor.get().dimension().equals(dimension) || NpcGarrison.keepsHome(server, squad)) {
                continue;
            }
            UUID owner = territory.ownerOf(dimension, ChunkKey.of(anchor.get().chunkX(), anchor.get().chunkZ()));
            if (owner != null && enemies.contains(owner)) {
                army.add(squad);
            }
        }
        int capital = SiegeWork.capitalProvince(server, defender);
        Aim aim = MARCHES.get(member);
        if (aim != null && (aim.war() != war.id() || !defender.equals(map.owner(aim.province()))
                || taken(state, war, dimension, aim.province()))) {
            aim = null;
        }
        if (aim != null) {
            // The place of the province moved (its settlement rose a few chunks away, or stands no more): the army goes there.
            ProvinceHolds.Target place = SiegeHolds.target(map, npc, aim.province());
            if (place == null) {
                aim = null;
            } else if (moved(aim, place)) {
                aim = new Aim(aim.war(), aim.province(), point(server, dimension, place));
                MARCHES.put(member, aim);
            }
        }
        if (aim == null) {
            WorldPos from = army.isEmpty() ? null : ArmyField.positionOf(server, army.get(0));
            int fromX = from != null && from.dimension().equals(dimension) ? from.x() : home.get().x();
            int fromZ = from != null && from.dimension().equals(dimension) ? from.z() : home.get().z();
            List<ProvinceHolds.Target> places = new ArrayList<>();
            for (int id : map.provincesOf(defender)) {
                ProvinceHolds.Target place = SiegeHolds.target(map, npc, id);
                if (place != null) {
                    places.add(place);
                }
            }
            Optional<ProvinceHolds.Target> next = ProvinceHolds.next(places, war.goal(), capital, id -> taken(state, war, dimension, id),
                    fromX, fromZ);
            if (next.isEmpty()) {
                // Nothing is left to take: the army stays where it stands until the peace.
                return;
            }
            aim = new Aim(war.id(), next.get().province(), point(server, dimension, next.get()));
            MARCHES.put(member, aim);
            SiegeWork.LOGGER.info("War #{}: the ruler of {} marches on the province {} ({}) of {}", war.id(), WarWork.name(server, member).getString(),
                    aim.province(), next.get().settled() ? "its square" : "its boundary stone", WarWork.name(server, defender).getString());
        }
        if (army.isEmpty()) {
            army = send(server, member);
        }
        order(server, member, army, Order.hold(aim.point()), "to hold the province " + aim.province());
    }

    private static boolean taken(SiegeState state, War war, String dimension, int province) {
        return state.hold(Hold.key(Hold.Kind.PROVINCE, war.id(), war.defender(), dimension, province)).map(Hold::taken).orElse(false);
    }

    // ---------------------------------------------------------------- the relief

    /**
     * A ruler of a state sends relief to the province of it the enemy holds now and whose hold went furthest: the squads it
     * sent to hold a place of its provinces before, or, with none, its field army or half of its free squads. With no province
     * held the relief stays where it stands.
     */
    private static void relieve(MinecraftServer server, SiegeState state, DiplomacyState diplomacy, ProvinceMap map, NpcState npc, UUID kingdomId) {
        String dimension = map.dimension();
        List<Hold> holds = new ArrayList<>();
        List<WorldPos> enemyAt = new ArrayList<>();
        for (War war : diplomacy.warsOf(kingdomId)) {
            if (!war.active() || !war.defender().equals(kingdomId)) {
                continue;
            }
            holds.addAll(state.holdsOf(war.id()));
            for (UUID enemy : war.members(War.Side.ATTACKER)) {
                for (ArmySquad squad : ArmyWork.state(server).squadsOf(enemy)) {
                    Order order = squad.order();
                    if ((order.kind() == Order.Kind.HOLD || order.kind() == Order.Kind.GUARD || order.kind() == Order.Kind.SIEGE)
                            && !squad.members().isEmpty()) {
                        order.anchor().filter(at -> at.dimension().equals(dimension)).ifPresent(enemyAt::add);
                    }
                }
            }
        }
        int province = ProvinceHolds.relief(holds, kingdomId, id -> {
            ProvinceHolds.Target place = SiegeHolds.target(map, npc, id);
            return place != null && enemyAt.stream().anyMatch(at -> place.reaches(at.x(), at.z(), ProvinceHolds.REACH));
        });
        if (province == 0) {
            return;
        }
        Aim aim = RELIEFS.get(kingdomId);
        ProvinceHolds.Target place = SiegeHolds.target(map, npc, province);
        if (place == null) {
            return;
        }
        // The relief sent to where the place of the province was before it moved goes on to where it is now.
        WorldPos before = aim != null && aim.province() == province && moved(aim, place) ? aim.point() : null;
        if (aim == null || aim.province() != province || before != null) {
            aim = new Aim(0, province, point(server, dimension, place));
            RELIEFS.put(kingdomId, aim);
            SiegeWork.LOGGER.info("The ruler of {} sends relief to its province {} ({})", WarWork.name(server, kingdomId).getString(), province,
                    place.settled() ? "its square" : "its boundary stone");
        }
        List<ArmySquad> relief = new ArrayList<>();
        for (ArmySquad squad : ArmyWork.state(server).squadsOf(kingdomId)) {
            // A squad holding the place of a province of its own is relief; one at a province of an enemy attacks it.
            if (squad.order().kind() == Order.Kind.HOLD && !squad.members().isEmpty() && !NpcGarrison.keepsHome(server, squad)
                    && (kingdomId.equals(map.owner(squad.order().anchor().map(at -> placeAt(map, npc, at)).orElse(0)))
                    || before != null && squad.order().anchor().map(before::equals).orElse(false))) {
                relief.add(squad);
            }
        }
        if (relief.isEmpty()) {
            relief = send(server, kingdomId);
        }
        order(server, kingdomId, relief, Order.hold(aim.point()), "to relieve the province " + province);
    }

    /**
     * A ruler at peace calls home its squads still holding the place of a province: relief sent in a war that is over.
     */
    private static void dismiss(MinecraftServer server, DiplomacyState diplomacy, ProvinceMap map, NpcState npc) {
        for (Kingdom kingdom : KingdomWorldApi.kingdoms(server)) {
            if (!WarWork.isNpc(server, kingdom.id()) || !diplomacy.warsOf(kingdom.id()).isEmpty()) {
                continue;
            }
            for (ArmySquad squad : List.copyOf(ArmyWork.state(server).squadsOf(kingdom.id()))) {
                if (squad.order().kind() == Order.Kind.HOLD && !NpcGarrison.keepsHome(server, squad)
                        && squad.order().anchor().map(at -> placeAt(map, npc, at)).orElse(0) != 0) {
                    ArmyWork.order(server, null, squad.id(), Order.NONE);
                }
            }
        }
    }

    /**
     * Whether the place of a province is no longer where an army was sent: its settlement rose a few chunks away, or a
     * province held at the square of its settlement is held at its boundary stone now.
     */
    private static boolean moved(Aim aim, ProvinceHolds.Target place) {
        return aim.point().x() != place.x() || aim.point().z() != place.z();
    }

    /**
     * The province whose place a point is: the square of its settlement or its boundary stone; 0 for a point elsewhere.
     */
    private static int placeAt(ProvinceMap map, NpcState npc, WorldPos at) {
        if (!at.dimension().equals(map.dimension())) {
            return 0;
        }
        int province = map.provinceAt(ChunkKey.of(at.chunkX(), at.chunkZ()));
        ProvinceHolds.Target place = province == 0 ? null : SiegeHolds.target(map, npc, province);
        return place != null && place.x() == at.x() && place.z() == at.z() ? province : 0;
    }

    // ---------------------------------------------------------------- squads

    /**
     * The squads a ruler sends out: its field army but a reserve of a third, else half of its squads at home or watching its
     * land; the guard and the reserve at the capital stay home.
     */
    private static List<ArmySquad> send(MinecraftServer server, UUID kingdomId) {
        List<ArmySquad> field = NpcGarrison.fieldArmy(server, kingdomId);
        if (!field.isEmpty()) {
            return List.copyOf(field.subList(0, NpcGarrison.sendable(field.size())));
        }
        List<ArmySquad> home = new ArrayList<>();
        List<ArmySquad> watching = new ArrayList<>();
        for (ArmySquad squad : ArmyWork.state(server).squadsOf(kingdomId)) {
            Order.Kind kind = squad.order().kind();
            if (squad.members().isEmpty() || NpcGarrison.keepsHome(server, squad)) {
                continue;
            }
            if (kind == Order.Kind.NONE) {
                home.add(squad);
            } else if (kind == Order.Kind.PATROL || kind == Order.Kind.GUARD) {
                watching.add(squad);
            }
        }
        home.addAll(watching);
        return List.copyOf(home.subList(0, (home.size() + 1) / 2));
    }

    private static void order(MinecraftServer server, UUID kingdomId, List<ArmySquad> squads, Order order, String what) {
        int sent = 0;
        int soldiers = 0;
        for (ArmySquad squad : squads) {
            if (!squad.order().equals(order) && ArmyWork.order(server, null, squad.id(), order).ok()) {
                sent++;
                soldiers += squad.members().size();
            }
        }
        if (sent > 0) {
            SiegeWork.LOGGER.info("The ruler of {} sends {} squad(s), {} soldier(s), {}", WarWork.name(server, kingdomId).getString(), sent,
                    soldiers, what);
        }
    }

    /**
     * The point of an order to hold the place of a province: its middle on the ground of the settlement, or on the surface
     * where the chunk is loaded, else just above the sea.
     */
    private static WorldPos point(MinecraftServer server, String dimension, ProvinceHolds.Target place) {
        int y = place.y();
        if (y == 0) {
            @Nullable ServerLevel level = SiegeWork.level(server, dimension);
            if (level != null && level.hasChunk(place.x() >> 4, place.z() >> 4)) {
                y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, place.x(), place.z());
            } else {
                y = level != null ? level.getSeaLevel() + 1 : 64;
            }
        }
        return new WorldPos(dimension, place.x(), y, place.z());
    }
}
