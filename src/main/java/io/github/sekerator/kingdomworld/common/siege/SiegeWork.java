package io.github.sekerator.kingdomworld.common.siege;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.diplomacy.WarEvents;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.diplomacy.Bond;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyState;
import io.github.sekerator.kingdomworld.core.diplomacy.PeaceTerms;
import io.github.sekerator.kingdomworld.core.diplomacy.War;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
import io.github.sekerator.kingdomworld.core.npc.Settlement;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.siege.Conquest;
import io.github.sekerator.kingdomworld.core.siege.Gate;
import io.github.sekerator.kingdomworld.core.siege.Hold;
import io.github.sekerator.kingdomworld.core.siege.ProvinceHolds;
import io.github.sekerator.kingdomworld.core.siege.SiegeRules;
import io.github.sekerator.kingdomworld.core.siege.SiegeState;
import io.github.sekerator.kingdomworld.core.siege.WarLand;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.Province;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * War in the world: whether land is open to an enemy, who stands where, and the second of the sieges that runs the gates,
 * the holds, the peace and the mending of damage. Pure decisions live in {@code core.siege}; this is the glue to the world.
 */
public final class SiegeWork {
    public static final String KEY = "kingdomworld.siege.";
    public static final Logger LOGGER = LogUtils.getLogger();
    /** Blocks from a player within which a place is played in the world rather than far from players. */
    static final double WATCHED = 64.0;

    /** Seconds of the journal of conquests run at most at once for game tests. */
    static final int FORCED_CONQUEST_SECONDS = 10_000;

    private static long lastTick = Long.MIN_VALUE;
    private static long busiest;

    private SiegeWork() {
    }

    public static SiegeState state(MinecraftServer server) {
        return SiegeSavedData.get(server).state();
    }

    public static boolean isReadOnly(MinecraftServer server) {
        return SiegeSavedData.get(server).isReadOnly() || WarWork.isReadOnly(server);
    }

    public static void reset() {
        lastTick = Long.MIN_VALUE;
        busiest = 0;
        SiegeGates.reset();
        SiegeHolds.reset();
        SiegeRepair.reset();
        SiegeNpc.reset();
        SiegeConquests.reset();
    }

    // ---------------------------------------------------------------- open land

    /**
     * The kingdom an entity acts for: the kingdom of a player, or of a citizen.
     */
    @Nullable
    public static UUID kingdomOf(MinecraftServer server, @Nullable Entity entity) {
        if (entity instanceof CitizenEntity citizen) {
            return citizen.kingdomId();
        }
        if (entity instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            return KingdomWorldApi.state(server).membership().kingdomOf(player.getUUID()).orElse(null);
        }
        return null;
    }

    public static WarLand.Access access(MinecraftServer server, @Nullable UUID actor, @Nullable UUID owner) {
        return WarLand.access(WarWork.state(server), actor, owner, kingdom -> WarWork.shielded(server, kingdom));
    }

    /**
     * Whether the land of {@code owner} is open to {@code actor} in war now.
     */
    public static boolean open(MinecraftServer server, @Nullable UUID actor, @Nullable UUID owner) {
        return actor != null && owner != null && !actor.equals(owner) && WarWork.fighting(server, actor, owner)
                && access(server, actor, owner) == WarLand.Access.OPEN;
    }

    /**
     * Whether two kingdoms are friends for a gate: the same kingdom, allies, an overlord and its vassal.
     */
    static boolean friends(MinecraftServer server, @Nullable UUID a, UUID b) {
        if (a == null) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        Bond bond = WarWork.state(server).bond(a, b, KingdomWorldApi.gameTime(server));
        return bond == Bond.ALLIANCE || bond == Bond.VASSAL;
    }

    // ---------------------------------------------------------------- who stands where

    /**
     * Fighters standing in a box: players at the keyboard and soldiers or guards, of either side.
     */
    record Near(int attackers, int defenders, int attackerPlayers, int defenderPlayers) {
        boolean anyAttacker() {
            return attackers > 0;
        }

        boolean anyDefender() {
            return defenders > 0;
        }
    }

    static Near near(MinecraftServer server, ServerLevel level, AABB box, Set<UUID> attackers, Set<UUID> defenders) {
        int a = 0;
        int d = 0;
        int ap = 0;
        int dp = 0;
        for (ServerPlayer player : level.players()) {
            if (player instanceof FakePlayer || !box.contains(player.position()) || !player.isAlive() || !WarEvents.fairGame(server, player)) {
                continue;
            }
            UUID kingdom = kingdomOf(server, player);
            if (kingdom == null) {
                continue;
            }
            if (attackers.contains(kingdom)) {
                a++;
                ap++;
            } else if (defenders.contains(kingdom)) {
                d++;
                dp++;
            }
        }
        for (CitizenEntity citizen : level.getEntitiesOfClass(CitizenEntity.class, box,
                body -> body.isAlive() && body.kingdomId() != null && fighter(server, body))) {
            if (attackers.contains(citizen.kingdomId())) {
                a++;
            } else if (defenders.contains(citizen.kingdomId())) {
                d++;
            }
        }
        return new Near(a, d, ap, dp);
    }

    /**
     * Whether a citizen fights for its kingdom: a soldier (guards at the gates are soldiers too).
     */
    static boolean fighter(MinecraftServer server, CitizenEntity body) {
        return Professions.isMilitary(body.professionId());
    }

    /**
     * Whether a player is within some blocks of a place: there the war is played in the world.
     */
    static boolean watched(ServerLevel level, int x, int z) {
        for (ServerPlayer player : level.players()) {
            if (!(player instanceof FakePlayer) && !player.isSpectator()) {
                double dx = player.getX() - x;
                double dz = player.getZ() - z;
                if (dx * dx + dz * dz <= WATCHED * WATCHED) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    static ServerLevel level(MinecraftServer server, String dimension) {
        return Dimensions.key(dimension).map(server::getLevel).orElse(null);
    }

    /**
     * A place of a kingdom that can be besieged: a city (0 for the capital).
     */
    public record Seat(UUID kingdomId, long city, String dimension, int x, int y, int z, String name) {
    }

    static List<Seat> seats(MinecraftServer server, UUID kingdomId) {
        List<Seat> result = new ArrayList<>();
        Optional<Capital> capital = RoadWork.capital(server, kingdomId);
        capital.ifPresent(c -> result.add(new Seat(kingdomId, 0, c.dimension(), c.x(), c.y(), c.z(),
                KingdomWorldApi.kingdom(server, kingdomId).map(k -> k.name()).orElse("?"))));
        for (City city : RoadWork.state(server).citiesOf(kingdomId)) {
            result.add(new Seat(kingdomId, city.id(), city.dimension(), city.x(), city.y(), city.z(), city.name()));
        }
        return result;
    }

    /**
     * The province the capital of a kingdom stands in, 0 for none: the heart of a state, which only an annexation takes.
     */
    public static int capitalProvince(MinecraftServer server, UUID kingdomId) {
        return RoadWork.capital(server, kingdomId).map(capital -> KingdomWorldApi.state(server).territory().provinceAt(capital.dimension(),
                io.github.sekerator.kingdomworld.core.territory.ChunkKey.of(capital.x() >> 4, capital.z() >> 4))).orElse(0);
    }

    /**
     * The provinces a peace hands over whole in a war on a state, for its claim: when the attacker wins and the defender holds
     * provinces, as many of the provinces the attacker took as the terms ask for land (the first taken first), and for a city
     * one province where people live (the one of the city the terms name, else the first taken); never the province of the
     * capital, which only an annexation takes. Empty for a peace on a kingdom without provinces, a peace for the throne (an
     * annexation) and a peace the defender won.
     */
    public static List<Integer> provincesOfPeace(MinecraftServer server, War war, PeaceTerms terms) {
        if (terms.isWhite() || !terms.claims() || terms.throne() || !war.attacker().equals(terms.winner())) {
            return List.of();
        }
        UUID loser = war.defender();
        TerritoryIndex territory = KingdomWorldApi.state(server).territory();
        Optional<ProvinceMap> attached = territory.provinceMap();
        if (attached.isEmpty() || territory.provincesOf(loser).isEmpty()) {
            return List.of();
        }
        ProvinceMap map = attached.get();
        NpcState npc = NpcWork.state(server);
        List<Hold> holds = state(server).holdsOf(war.id());
        int capital = capitalProvince(server, loser);
        List<Integer> provinces = new ArrayList<>();
        if (terms.landChunks() > 0) {
            provinces.addAll(Conquest.provincesToCede(holds, terms.winner(), loser, war.id(), terms.provinces(), capital));
        }
        if (!terms.city().isEmpty()) {
            String named = terms.city();
            Conquest.provinceOfCity(holds, terms.winner(), loser, war.id(), named, id -> seatName(map, npc, (int) id, named),
                    id -> map.province((int) id).map(province -> ProvinceHolds.settled(province, seatOf(npc, province))).orElse(false),
                    capital).filter(id -> !provinces.contains(id)).ifPresent(provinces::add);
        }
        return provinces;
    }

    @Nullable
    private static Settlement seatOf(NpcState npc, Province province) {
        return province.seat() == 0 ? null : npc.settlement((int) province.seat()).orElse(null);
    }

    /**
     * The name of the main settlement of a province as the terms may name it: in the alphabet the terms use.
     */
    private static String seatName(ProvinceMap map, NpcState npc, int id, String named) {
        Settlement seat = map.province(id).map(province -> seatOf(npc, province)).orElse(null);
        if (seat == null) {
            return "";
        }
        return named.equalsIgnoreCase(seat.name().cyrillic()) ? seat.name().cyrillic() : seat.name().latin();
    }

    /**
     * What stands in a province ceded by treaty goes to the kingdom that asked for it by the journal of conquests, as in a
     * province a peace hands over: the claims of the one who ceded it in it, then buildings, citizens, cities and settlements.
     * The province itself is the asker's already, from the moment it was paid for.
     *
     * @param dimension the world of the provinces
     */
    public static void cede(MinecraftServer server, UUID winner, UUID loser, int province, String dimension, long now) {
        state(server).addConquest(Conquest.Kind.PROVINCE, 0, winner, loser, dimension, List.of((long) province), 0, now);
        LOGGER.info("The province {} ceded by {} to {} goes over by the journal of conquests", province, WarWork.name(server, loser).getString(),
                WarWork.name(server, winner).getString());
    }

    /**
     * Whether a province of a kingdom is at stake in a war or in a peace the world carries out: an enemy holds it or has taken
     * it, a claim of a peace or a conquest still in the journal names it, or its holder is being annexed. Such a province is not
     * ceded by treaty, so what a war won is never given to another meanwhile.
     */
    public static boolean atStake(MinecraftServer server, UUID holder, int province) {
        SiegeState siege = state(server);
        for (Hold hold : siege.holds()) {
            if (hold.kind() == Hold.Kind.PROVINCE && hold.target() == province && hold.defender().equals(holder)) {
                return true;
            }
        }
        for (Conquest conquest : siege.conquests()) {
            if (conquest.kind() == Conquest.Kind.ANNEX && conquest.loser().equals(holder)
                    || conquest.kind() == Conquest.Kind.PROVINCE && conquest.provinces().contains(province)) {
                return true;
            }
        }
        for (DiplomacyState.Claim claim : WarWork.state(server).claims()) {
            if (claim.loser().equals(holder) && (claim.throne() || claim.provinces().contains(province))) {
                return true;
            }
        }
        return false;
    }

    static Optional<Seat> seat(MinecraftServer server, UUID kingdomId, long cityId) {
        for (Seat seat : seats(server, kingdomId)) {
            if (seat.city() == cityId) {
                return Optional.of(seat);
            }
        }
        return Optional.empty();
    }

    /**
     * The city of a kingdom nearest to a place within some blocks.
     */
    public static Optional<Seat> seatNear(MinecraftServer server, UUID kingdomId, WorldPos at, int within) {
        Seat best = null;
        long bestDistance = (long) within * within;
        for (Seat seat : seats(server, kingdomId)) {
            if (!seat.dimension().equals(at.dimension())) {
                continue;
            }
            long dx = seat.x() - at.x();
            long dz = seat.z() - at.z();
            if (dx * dx + dz * dz <= bestDistance) {
                best = seat;
                bestDistance = dx * dx + dz * dz;
            }
        }
        return Optional.ofNullable(best);
    }

    // ---------------------------------------------------------------- soldiers at a siege

    /**
     * Where a soldier besieging the city at a place walks: to the nearest closed gate of its wall, else to the square.
     */
    public static WorldPos siegeTarget(MinecraftServer server, CitizenEntity body, WorldPos city) {
        String dimension = Dimensions.id(body.level());
        if (!city.dimension().equals(dimension)) {
            return city;
        }
        UUID owner = KingdomWorldApi.state(server).territory().ownerOf(dimension,
                io.github.sekerator.kingdomworld.core.territory.ChunkKey.of(city.chunkX(), city.chunkZ()));
        if (owner == null) {
            return city;
        }
        SiegeState state = state(server);
        WorldPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Wall wall : RoadWork.state(server).walls()) {
            if (!wall.kingdomId().equals(owner) || !wall.dimension().equals(dimension)
                    || Math.abs(wall.centerX() - city.x()) > wall.radius() + 8 || Math.abs(wall.centerZ() - city.z()) > wall.radius() + 8) {
                continue;
            }
            boolean inside = wall.inside((int) Math.floor(body.getX()), (int) Math.floor(body.getZ()));
            if (inside) {
                return city;
            }
            for (int cell : wall.gates()) {
                Optional<Gate> gate = state.gate(wall.id(), cell);
                if (gate.isEmpty() || !gate.get().closed()) {
                    // An open or broken gate: walk through it to the square.
                    continue;
                }
                RouteCell at = wall.cells().get(cell);
                double distance = body.distanceToSqr(at.x() + 0.5, at.y() + 1, at.z() + 0.5);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new WorldPos(dimension, at.x(), at.y() + 1, at.z());
                }
            }
        }
        return best != null ? best : city;
    }

    /**
     * Whether a soldier body besieges: a soldier of a squad with the order "siege".
     */
    static boolean besieging(MinecraftServer server, CitizenEntity body) {
        return body.citizenId() != null && ArmyWork.orderOf(server, body.citizenId()).kind() == Order.Kind.SIEGE;
    }

    // ---------------------------------------------------------------- the second of the sieges

    /**
     * Runs once a second: kings seen, gates, holds and sieges, peaces carried out, damage mended.
     */
    public static void tick(MinecraftServer server) {
        if (isReadOnly(server)) {
            return;
        }
        long now = KingdomWorldApi.gameTime(server);
        long ticks = lastTick == Long.MIN_VALUE ? 20 : Math.max(0, Math.min(200, now - lastTick));
        lastTick = now;
        long started = System.nanoTime();
        SiegeRules rules = SiegeSettings.rules();
        DiplomacyState diplomacy = WarWork.state(server);
        for (War war : diplomacy.wars()) {
            if (war.active()) {
                for (War.Side side : War.Side.values()) {
                    UUID leader = war.leader(side);
                    if (!WarWork.isNpc(server, leader) && kingAtHand(server, leader)) {
                        state(server).kingSeen(war.id(), leader);
                    }
                }
            }
        }
        SiegeGates.tick(server, rules, now);
        SiegeHolds.tick(server, rules, now, ticks);
        SiegeConquests.tick(server, rules, now);
        SiegeRepair.tick(server, rules, now);
        SiegeNpc.tick(server, now);
        busiest = Math.max(busiest, System.nanoTime() - started);
    }

    /**
     * Whether a soldier of a siege (or of the defence of a city) would go beyond the budget of bodies of its kingdom; counts it
     * when it does not.
     */
    public static boolean overBudget(MinecraftServer server, io.github.sekerator.kingdomworld.core.citizen.Citizen citizen,
                                     java.util.Map<UUID, Integer> counted) {
        if (!Professions.isMilitary(citizen.professionId()) || ArmyWork.isReadOnly(server)) {
            return false;
        }
        Order.Kind kind = ArmyWork.orderOf(server, citizen.id()).kind();
        if (kind != Order.Kind.SIEGE && kind != Order.Kind.DEFEND) {
            return false;
        }
        int count = counted.merge(citizen.kingdomId(), 1, Integer::sum);
        return count > SiegeSettings.rules().maxBodies();
    }

    // ---------------------------------------------------------------- for checks

    /**
     * Runs the gates once, for game tests.
     */
    public static void forceGates(MinecraftServer server) {
        SiegeGates.tick(server, SiegeSettings.rules(), KingdomWorldApi.gameTime(server));
    }

    /**
     * Runs the holds once as if some ticks went by, for game tests.
     */
    public static void forceHolds(MinecraftServer server, long ticks) {
        SiegeHolds.tick(server, SiegeSettings.rules(), KingdomWorldApi.gameTime(server), ticks);
    }

    /**
     * Turns claims into conquests and carries them out until the journal is empty (a step of many things takes several
     * seconds), at most {@value #FORCED_CONQUEST_SECONDS} seconds of the journal, for game tests.
     */
    public static void forceConquests(MinecraftServer server) {
        for (int i = 0; i < FORCED_CONQUEST_SECONDS; i++) {
            if (forceConquestSecond(server)) {
                return;
            }
        }
    }

    /**
     * One second of the journal of conquests at once, for game tests that measure it: claims become conquests and every
     * conquest does as much of its step as the share of time of the second allows.
     *
     * @return whether the journal and the claims of peace are empty afterwards
     */
    public static boolean forceConquestSecond(MinecraftServer server) {
        SiegeConquests.tick(server, SiegeSettings.rules(), KingdomWorldApi.gameTime(server));
        return state(server).conquests().isEmpty() && WarWork.state(server).claims().isEmpty();
    }

    /**
     * Mends up to some broken blocks of a kingdom at once, for game tests and operators.
     */
    public static int forceMend(MinecraftServer server, UUID kingdomId, int most) {
        return SiegeRepair.mend(server, kingdomId, most, KingdomWorldApi.gameTime(server));
    }

    /**
     * Hangs the leaves of a gate at once without paying, or breaks them, for game tests and operators.
     */
    public static boolean forceHang(MinecraftServer server, Wall wall, int cell, boolean hung) {
        return SiegeGates.set(server, wall, cell, hung, SiegeSettings.rules());
    }

    public static void forceNpc(MinecraftServer server) {
        SiegeNpc.reset();
        SiegeNpc.tick(server, KingdomWorldApi.gameTime(server));
    }

    /**
     * The longest second of the sieges so far, in microseconds, for the measure of load.
     */
    public static long busiestMicros() {
        return busiest / 1_000;
    }

    public static void forgetBusiest() {
        busiest = 0;
    }

    /**
     * Whether the king of a kingdom of players is online and at the keyboard.
     */
    static boolean kingAtHand(MinecraftServer server, UUID kingdomId) {
        Optional<UUID> ruler = KingdomWorldApi.kingdom(server, kingdomId).flatMap(k -> Optional.ofNullable(k.rulerId()));
        if (ruler.isEmpty()) {
            return false;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(ruler.get());
        return player != null && WarEvents.fairGame(server, player);
    }
}
