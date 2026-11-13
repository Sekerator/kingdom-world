package io.github.sekerator.kingdomworld.common.siege;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyField;
import io.github.sekerator.kingdomworld.common.army.ArmySettings;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.diplomacy.DiplomacySettings;
import io.github.sekerator.kingdomworld.common.diplomacy.WarEvents;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.army.ArmyRules;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.combat.Battle;
import io.github.sekerator.kingdomworld.core.combat.Side;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyState;
import io.github.sekerator.kingdomworld.core.diplomacy.War;
import io.github.sekerator.kingdomworld.core.diplomacy.WarGoal;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
import io.github.sekerator.kingdomworld.core.npc.Settlement;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.siege.Gate;
import io.github.sekerator.kingdomworld.core.siege.Hold;
import io.github.sekerator.kingdomworld.core.siege.ProvinceHolds;
import io.github.sekerator.kingdomworld.core.siege.Siege;
import io.github.sekerator.kingdomworld.core.siege.SiegeRules;
import io.github.sekerator.kingdomworld.core.siege.SiegeState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.Province;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongToIntFunction;

/**
 * Places held by attackers: chunks of the enemy's land (the goal "land"), the squares of its cities (the goal "city"), its
 * throne hall (the goal "throne"), and the provinces of a state (the goals "land" and "city"). Near players the bodies standing
 * there count; far from players the squads do: a squad besieging a city whose gates are down holds its square while no squad
 * of the defenders stands there, and a squad holding a province first beats its garrison by numbers.
 */
final class SiegeHolds {
    /** Blocks above and below the middle of a square or a hall that still count. */
    private static final int HEIGHT = 8;
    /** Blocks from a city within which a squad besieges or defends it far from players. */
    private static final int SQUAD_REACH = 48;

    /** When the attackers last fought the defenders at the place of a province far from players, by the key of the hold. */
    private static final Map<String, Long> LAST_BATTLE = new HashMap<>();
    /** Whether a kingdom holds provinces, as the map said in a version of it. */
    private static final Map<UUID, Holding> HOLDING = new HashMap<>();
    /** The places of provinces, good for one version of the map and of the settlements. */
    private static final Map<Integer, ProvinceHolds.Target> TARGETS = new HashMap<>();
    private static ProvinceMap targetsMap;
    private static long targetsVersion;
    private static long targetsSettlements;
    /**
     * The chunks of the boundary stones by province, {the chunk of the middle of the province, the chunk of its stone}, good for
     * one version of the chunks of the map: the search through the tiles is done once.
     */
    private static final Map<Integer, long[]> STONES = new HashMap<>();
    private static ProvinceMap stonesMap;
    private static long stonesVersion;

    private record Holding(ProvinceMap map, long version, boolean any) {
    }

    /**
     * A squad of the defenders far from players and where it is.
     */
    private record Posted(ArmySquad squad, WorldPos at) {
    }

    private SiegeHolds() {
    }

    static void reset() {
        LAST_BATTLE.clear();
        HOLDING.clear();
        TARGETS.clear();
        targetsMap = null;
        STONES.clear();
        stonesMap = null;
    }

    static void tick(MinecraftServer server, SiegeRules rules, long now, long ticks) {
        SiegeState state = SiegeWork.state(server);
        DiplomacyState diplomacy = WarWork.state(server);
        for (War war : List.copyOf(diplomacy.wars())) {
            if (!war.active() || !war.goal().inTheWorld()) {
                continue;
            }
            UUID defender = war.defender();
            boolean frozen = WarWork.shielded(server, defender);
            Set<UUID> attackers = war.members(War.Side.ATTACKER);
            Set<UUID> defenders = war.members(War.Side.DEFENDER);
            switch (war.goal()) {
                case LAND -> {
                    chunks(server, state, war, attackers, defenders, frozen, rules, now, ticks);
                    provinces(server, state, war, attackers, defenders, frozen, rules, now, ticks);
                }
                case CITY -> {
                    seats(server, state, war, attackers, defenders, frozen, rules, now, ticks);
                    provinces(server, state, war, attackers, defenders, frozen, rules, now, ticks);
                }
                case THRONE -> seats(server, state, war, attackers, defenders, frozen, rules, now, ticks);
                default -> {
                }
            }
        }
    }

    // ---------------------------------------------------------------- land

    private static void chunks(MinecraftServer server, SiegeState state, War war, Set<UUID> attackers, Set<UUID> defenders, boolean frozen,
                               SiegeRules rules, long now, long ticks) {
        UUID defender = war.defender();
        Map<String, long[]> places = new LinkedHashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player instanceof FakePlayer || !WarEvents.fairGame(server, player) || !attackers.contains(SiegeWork.kingdomOf(server, player))) {
                continue;
            }
            String dimension = Dimensions.id(player.level());
            long chunk = ChunkKey.of(player.getBlockX() >> 4, player.getBlockZ() >> 4);
            // Claimed chunks are held one by one; the land of provinces is held by provinces.
            if (defender.equals(KingdomWorldApi.state(server).territory().claimOwner(dimension, chunk))) {
                places.put(dimension + "@" + chunk, new long[]{chunk});
            }
        }
        for (Hold hold : state.holdsOf(war.id())) {
            if (hold.kind() == Hold.Kind.CHUNK) {
                places.put(hold.dimension() + "@" + hold.target(), new long[]{hold.target()});
            }
        }
        Map<String, Boolean> abstractAttackers = new LinkedHashMap<>();
        for (UUID attacker : attackers) {
            for (ArmySquad squad : ArmyWork.state(server).squadsOf(attacker)) {
                Optional<WorldPos> anchor = squad.order().anchor();
                Order.Kind kind = squad.order().kind();
                if (anchor.isEmpty() || ArmyWork.hasBodies(squad) || squad.members().isEmpty()
                        || (kind != Order.Kind.HOLD && kind != Order.Kind.GUARD && kind != Order.Kind.SIEGE)) {
                    continue;
                }
                long chunk = ChunkKey.of(anchor.get().chunkX(), anchor.get().chunkZ());
                if (defender.equals(KingdomWorldApi.state(server).territory().claimOwner(anchor.get().dimension(), chunk))) {
                    String key = anchor.get().dimension() + "@" + chunk;
                    places.put(key, new long[]{chunk});
                    abstractAttackers.put(key, true);
                }
            }
        }
        for (Map.Entry<String, long[]> place : places.entrySet()) {
            String dimension = place.getKey().substring(0, place.getKey().lastIndexOf('@'));
            long chunk = place.getValue()[0];
            ServerLevel level = SiegeWork.level(server, dimension);
            int x = ChunkKey.x(chunk) * 16 + 8;
            int z = ChunkKey.z(chunk) * 16 + 8;
            boolean a;
            boolean d;
            if (level != null && level.isLoaded(new net.minecraft.core.BlockPos(x, 64, z)) && SiegeWork.watched(level, x, z)) {
                AABB box = new AABB(x - 8, level.getMinBuildHeight(), z - 8, x + 8, level.getMaxBuildHeight(), z + 8);
                SiegeWork.Near near = SiegeWork.near(server, level, box, attackers, defenders);
                a = near.anyAttacker();
                d = near.anyDefender();
            } else {
                a = abstractAttackers.getOrDefault(place.getKey(), false);
                d = defendedFarAway(server, defenders, dimension, x, z, 16);
            }
            Optional<Hold> existing = state.hold(Hold.key(Hold.Kind.CHUNK, war.id(), defender, dimension, chunk));
            if (existing.isEmpty() && !a) {
                continue;
            }
            Hold hold = existing.orElseGet(() -> state.holdAt(Hold.Kind.CHUNK, war.id(), war.attacker(), defender, dimension, chunk, now));
            Hold.Change change = hold.step(a, d, frozen, ticks, now, rules);
            changed(server, state, war, hold, change, rules, now, null, null);
            if (level != null && (a || d) && !hold.idle()) {
                bar(server, level, new AABB(x - 8, level.getMinBuildHeight(), z - 8, x + 8, level.getMaxBuildHeight(), z + 8), hold,
                        rules.ticks(hold.kind()), frozen);
            }
        }
    }

    // ---------------------------------------------------------------- cities and the throne

    private static void seats(MinecraftServer server, SiegeState state, War war, Set<UUID> attackers, Set<UUID> defenders, boolean frozen,
                              SiegeRules rules, long now, long ticks) {
        UUID defender = war.defender();
        boolean throne = war.goal() == io.github.sekerator.kingdomworld.core.diplomacy.WarGoal.THRONE;
        for (SiegeWork.Seat seat : SiegeWork.seats(server, defender)) {
            if (throne != (seat.city() == 0)) {
                // A war for a city takes a city, not the capital; a war for the throne takes the throne hall of the capital.
                if (!throne || seat.city() != 0) {
                    abstractGates(server, state, war, seat, rules, ticks);
                }
                continue;
            }
            Hold.Kind kind = throne ? Hold.Kind.THRONE : Hold.Kind.PLAZA;
            int radius = throne ? rules.throneRadius() : rules.plazaRadius();
            ServerLevel level = SiegeWork.level(server, seat.dimension());
            boolean a;
            boolean d;
            boolean watched = level != null && level.isLoaded(new net.minecraft.core.BlockPos(seat.x(), seat.y(), seat.z()))
                    && SiegeWork.watched(level, seat.x(), seat.z());
            AABB box = new AABB(seat.x() - radius, seat.y() - HEIGHT, seat.z() - radius, seat.x() + radius + 1, seat.y() + HEIGHT,
                    seat.z() + radius + 1);
            Optional<Siege> siege = besieged(server, state, war, seat, now);
            if (watched) {
                SiegeWork.Near near = SiegeWork.near(server, level, box, attackers, defenders);
                a = near.anyAttacker();
                d = near.anyDefender();
            } else {
                boolean open = abstractGates(server, state, war, seat, rules, ticks);
                a = siege.isPresent() && open && besiegersFarAway(server, attackers, seat) > 0;
                d = defendedFarAway(server, defenders, seat.dimension(), seat.x(), seat.z(), reach(server, seat));
            }
            if (a && siege.isEmpty()) {
                siege = start(server, state, war, seat, now, 0);
            }
            if (throne && !WarWork.isNpc(server, defender) && !state.kingWasSeen(war.id(), defender)) {
                // The throne of a king who was never online in this war is not taken.
                if (a && level != null) {
                    Component line = Component.translatable(SiegeWork.KEY + "bar.king_away").withStyle(ChatFormatting.GRAY);
                    tellIn(server, level, box, line);
                }
                continue;
            }
            Optional<Hold> existing = state.hold(Hold.key(kind, war.id(), defender, seat.dimension(), seat.city()));
            if (existing.isEmpty() && !a) {
                continue;
            }
            Hold hold = existing.orElseGet(() -> state.holdAt(kind, war.id(), war.attacker(), defender, seat.dimension(), seat.city(), now));
            Hold.Change change = hold.step(a, d, frozen, ticks, now, rules);
            changed(server, state, war, hold, change, rules, now, seat, null);
            if (level != null && (a || d) && !hold.taken() && hold.progress() > 0) {
                bar(server, level, box, hold, rules.ticks(hold.kind()), frozen);
            }
        }
    }

    /**
     * The siege of a city, if the attacker besieges it: a squad with the order "siege" stands at it.
     */
    private static Optional<Siege> besieged(MinecraftServer server, SiegeState state, War war, SiegeWork.Seat seat, long now) {
        Optional<Siege> siege = state.siege(war.id(), seat.kingdomId(), seat.city());
        if (siege.isPresent()) {
            return siege;
        }
        for (UUID attacker : war.members(War.Side.ATTACKER)) {
            for (ArmySquad squad : ArmyWork.state(server).squadsOf(attacker)) {
                if (squad.order().kind() == Order.Kind.SIEGE && near(squad.order().points().get(0), seat, reach(server, seat))) {
                    return start(server, state, war, seat, now, 0);
                }
            }
        }
        return Optional.empty();
    }

    static Optional<Siege> start(MinecraftServer server, SiegeState state, War war, SiegeWork.Seat seat, long now, int rams) {
        Siege siege = new Siege(war.id(), war.attacker(), seat.kingdomId(), seat.city(), seat.dimension(), seat.x(), seat.y(), seat.z(), now,
                rams, WarWork.isNpc(server, war.attacker()));
        if (!state.startSiege(siege)) {
            return Optional.empty();
        }
        WarWork.state(server).record(now, "siege", war.attacker(), seat.kingdomId(), war.id(), SiegeTexts.cityName(server, seat.kingdomId(),
                seat.city()));
        WarWork.tell(server, seat.kingdomId(), Component.translatable(SiegeWork.KEY + "siege_started_ours",
                SiegeTexts.cityLabel(server, seat.kingdomId(), seat.city())).withStyle(ChatFormatting.RED));
        WarWork.tell(server, war.attacker(), Component.translatable(SiegeWork.KEY + "siege_started_theirs",
                SiegeTexts.cityLabel(server, seat.kingdomId(), seat.city())).withStyle(ChatFormatting.GOLD));
        SiegeWork.LOGGER.info("War #{}: {} besieges {} of {}", war.id(), WarWork.name(server, war.attacker()).getString(), seat.name(),
                WarWork.name(server, seat.kingdomId()).getString());
        return Optional.of(siege);
    }

    /**
     * Far from players, the squads besieging a city strike its gates by their number and rams.
     *
     * @return whether the city lies open: no wall, or a gate of it breached
     */
    private static boolean abstractGates(MinecraftServer server, SiegeState state, War war, SiegeWork.Seat seat, SiegeRules rules, long ticks) {
        Optional<Wall> wall = RoadWork.state(server).wallOf(seat.city(), seat.kingdomId());
        if (wall.isEmpty() || wall.get().gates().isEmpty()) {
            return true;
        }
        Gate standing = null;
        for (int cell : wall.get().gates()) {
            Optional<Gate> gate = state.gate(wall.get().id(), cell);
            if (gate.isEmpty() || gate.get().breached()) {
                return true;
            }
            if (standing == null) {
                standing = gate.get();
            }
        }
        Optional<Siege> siege = state.siege(war.id(), seat.kingdomId(), seat.city());
        ServerLevel level = SiegeWork.level(server, seat.dimension());
        if (siege.isEmpty() || standing == null || (level != null && SiegeWork.watched(level, seat.x(), seat.z()))) {
            return false;
        }
        int soldiers = besiegersFarAway(server, war.members(War.Side.ATTACKER), seat);
        if (soldiers <= 0 || WarWork.shielded(server, seat.kingdomId())) {
            return false;
        }
        double hit = rules.abstractShare() * rules.hitPerSecond(soldiers, siege.get().rams()) * ticks / 20.0;
        if (standing.strike(hit)) {
            state.changed();
            if (level != null && level.isLoaded(SiegeGates.leafPositions(wall.get(), standing.cell()).get(SiegeGates.MIDDLE))) {
                SiegeGates.breach(server, level, wall.get(), standing, SiegeGates.leafPositions(wall.get(), standing.cell()));
            }
            SiegeGates.gateBroken(server, war.attacker(), seat.kingdomId(), seat.city(), KingdomWorldApi.gameTime(server));
            return true;
        }
        state.changed();
        return false;
    }

    /**
     * Soldiers of squads besieging a city far from players.
     */
    /**
     * How far from the seat of a city a squad still stands at it: {@value #SQUAD_REACH} blocks, or a little beyond the wall of
     * the city where the wall is wider, so that the gates of a large capital are part of it.
     */
    private static int reach(MinecraftServer server, SiegeWork.Seat seat) {
        return Math.max(SQUAD_REACH, RoadWork.state(server).wallOf(seat.city(), seat.kingdomId()).map(wall -> wall.radius() + 8).orElse(0));
    }

    private static int besiegersFarAway(MinecraftServer server, Set<UUID> attackers, SiegeWork.Seat seat) {
        int soldiers = 0;
        for (UUID attacker : attackers) {
            for (ArmySquad squad : ArmyWork.state(server).squadsOf(attacker)) {
                if (squad.order().kind() == Order.Kind.SIEGE && !ArmyWork.hasBodies(squad)
                        && near(squad.order().points().get(0), seat, reach(server, seat))) {
                    soldiers += squad.members().size();
                }
            }
        }
        return soldiers;
    }

    /**
     * Whether a squad of the defenders stands at a place far from players: one defending, holding or guarding there.
     */
    private static boolean defendedFarAway(MinecraftServer server, Set<UUID> defenders, String dimension, int x, int z, int reach) {
        for (UUID defender : defenders) {
            for (ArmySquad squad : ArmyWork.state(server).squadsOf(defender)) {
                Order.Kind kind = squad.order().kind();
                if ((kind == Order.Kind.DEFEND || kind == Order.Kind.HOLD || kind == Order.Kind.GUARD) && !squad.members().isEmpty()
                        && !ArmyWork.hasBodies(squad)) {
                    WorldPos at = squad.order().points().get(0);
                    if (at.dimension().equals(dimension) && Math.abs(at.x() - x) <= reach && Math.abs(at.z() - z) <= reach) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean near(WorldPos at, SiegeWork.Seat seat, int reach) {
        return at.dimension().equals(seat.dimension()) && Math.abs(at.x() - seat.x()) <= reach && Math.abs(at.z() - seat.z()) <= reach;
    }

    // ---------------------------------------------------------------- provinces of a state

    /**
     * The provinces of a state held in a war for land or for a city. Only the provinces at hand are looked at, never every
     * province of the state: those already held in this war, and those whose place an attacker stands at (a player in its
     * square or at its stone, a squad far from players within {@value ProvinceHolds#REACH} blocks of it).
     */
    private static void provinces(MinecraftServer server, SiegeState state, War war, Set<UUID> attackers, Set<UUID> defenders, boolean frozen,
                                  SiegeRules rules, long now, long ticks) {
        UUID defender = war.defender();
        TerritoryIndex territory = KingdomWorldApi.state(server).territory();
        Optional<ProvinceMap> attached = territory.provinceMap();
        if (attached.isEmpty() || !holdsProvinces(attached.get(), defender)) {
            return;
        }
        ProvinceMap map = attached.get();
        String dimension = map.dimension();
        NpcState npc = NpcWork.state(server);
        int capital = SiegeWork.capitalProvince(server, defender);
        WarGoal goal = war.goal();
        LongToIntFunction provinceAt = map::provinceAt;
        if (LAST_BATTLE.size() > 512) {
            // Battles long past say nothing any more.
            LAST_BATTLE.values().removeIf(last -> ProvinceHolds.battleDue(last, now));
        }
        Map<Integer, ProvinceHolds.Target> places = new LinkedHashMap<>();
        for (Hold hold : state.holdsOf(war.id())) {
            if (hold.kind() == Hold.Kind.PROVINCE && !hold.taken() && hold.dimension().equals(dimension)
                    && defender.equals(map.owner((int) hold.target()))) {
                ProvinceHolds.Target target = target(map, npc, (int) hold.target());
                if (target != null) {
                    places.put(target.province(), target);
                }
            }
        }
        ServerLevel level = SiegeWork.level(server, dimension);
        if (level != null) {
            int reach = (rules.plazaRadius() + 15) / 16;
            for (ServerPlayer player : level.players()) {
                if (player instanceof FakePlayer || !WarEvents.fairGame(server, player) || !attackers.contains(SiegeWork.kingdomOf(server, player))) {
                    continue;
                }
                for (int id : ProvinceHolds.near(player.getBlockX(), player.getBlockZ(), reach, provinceAt)) {
                    if (places.containsKey(id) || !defender.equals(map.owner(id))) {
                        continue;
                    }
                    ProvinceHolds.Target target = target(map, npc, id);
                    if (target != null && ProvinceHolds.eligible(goal, id, target.settled(), capital)
                            && target.contains(player.getBlockX(), player.getBlockZ(), rules.plazaRadius())) {
                        places.put(id, target);
                    }
                }
            }
        }
        Map<Integer, List<ArmySquad>> holding = new HashMap<>();
        int squadReach = (ProvinceHolds.REACH + 15) / 16;
        for (UUID attacker : attackers) {
            for (ArmySquad squad : ArmyWork.state(server).squadsOf(attacker)) {
                Order.Kind kind = squad.order().kind();
                if ((kind != Order.Kind.HOLD && kind != Order.Kind.GUARD && kind != Order.Kind.SIEGE) || squad.members().isEmpty()
                        || ArmyWork.hasBodies(squad) || !ArmyField.arrived(server, squad)) {
                    continue;
                }
                WorldPos at = ArmyField.positionOf(server, squad);
                if (at == null || !at.dimension().equals(dimension)) {
                    continue;
                }
                for (int id : ProvinceHolds.near(at.x(), at.z(), squadReach, provinceAt)) {
                    if (!defender.equals(map.owner(id))) {
                        continue;
                    }
                    ProvinceHolds.Target target = target(map, npc, id);
                    if (target == null || !target.reaches(at.x(), at.z(), ProvinceHolds.REACH)
                            || (!places.containsKey(id) && !ProvinceHolds.eligible(goal, id, target.settled(), capital))) {
                        continue;
                    }
                    places.putIfAbsent(id, target);
                    holding.computeIfAbsent(id, key -> new ArrayList<>()).add(squad);
                }
            }
        }
        List<Posted> posted = null;
        for (ProvinceHolds.Target target : places.values()) {
            int id = target.province();
            String key = Hold.key(Hold.Kind.PROVINCE, war.id(), defender, dimension, id);
            Optional<Hold> existing = state.hold(key);
            if (existing.isPresent() && existing.get().taken()) {
                // A taken province stays taken until the peace: nobody fights for it any more.
                continue;
            }
            boolean watched = level != null && level.isLoaded(new net.minecraft.core.BlockPos(target.x(), 64, target.z()))
                    && SiegeWork.watched(level, target.x(), target.z());
            AABB box = level == null ? null : box(level, target, rules);
            boolean a;
            boolean d;
            if (watched) {
                SiegeWork.Near near = SiegeWork.near(server, level, box, attackers, defenders);
                a = near.anyAttacker();
                d = near.anyDefender();
            } else {
                if (posted == null) {
                    posted = postedFarAway(server, defenders, dimension);
                }
                List<ArmySquad> ours = holding.getOrDefault(id, List.of());
                List<ArmySquad> theirs = at(posted, target);
                Settlement garrison = garrisonOf(npc, target, defenders);
                a = !ours.isEmpty();
                d = !theirs.isEmpty() || strength(garrison) > 0;
                if (a && d && !frozen && ProvinceHolds.battleDue(LAST_BATTLE.get(key), now) && !ArmyWork.isReadOnly(server)
                        && !NpcWork.isReadOnly(server)) {
                    LAST_BATTLE.put(key, now);
                    fight(server, war, target, dimension, ours, theirs, garrison);
                    ours = alive(server, ours);
                    theirs = alive(server, theirs);
                    a = !ours.isEmpty();
                    d = !theirs.isEmpty() || strength(garrison) > 0;
                }
            }
            if (existing.isEmpty() && !a) {
                continue;
            }
            Hold hold = existing.orElseGet(() -> state.holdAt(Hold.Kind.PROVINCE, war.id(), war.attacker(), defender, dimension, id, now));
            long need = rules.provinceTicks(target.settled());
            Hold.Change change = hold.step(a, d, frozen, ticks, now, need);
            changed(server, state, war, hold, change, rules, now, null, target);
            if (level != null && (a || d) && !hold.taken() && hold.progress() > 0) {
                bar(server, level, box, hold, need, frozen);
            }
        }
    }

    /**
     * Where a province is held now, empty for a province the map does not know.
     */
    static Optional<ProvinceHolds.Target> place(MinecraftServer server, int province) {
        Optional<ProvinceMap> map = KingdomWorldApi.state(server).territory().provinceMap();
        return map.map(provinces -> target(provinces, NpcWork.state(server), province));
    }

    /**
     * Game ticks a hold needs: a province where people live as long as the square of a city, an empty one as long as a chunk.
     */
    static long need(MinecraftServer server, Hold hold, SiegeRules rules) {
        if (hold.kind() != Hold.Kind.PROVINCE) {
            return rules.ticks(hold.kind());
        }
        return rules.provinceTicks(place(server, (int) hold.target()).map(ProvinceHolds.Target::settled).orElse(false));
    }

    /**
     * Whether a kingdom holds any province: looked up again only when the map changed.
     */
    static boolean holdsProvinces(ProvinceMap map, UUID kingdomId) {
        Holding known = HOLDING.get(kingdomId);
        if (known == null || known.map() != map || known.version() != map.version()) {
            known = new Holding(map, map.version(), !map.provincesOf(kingdomId).isEmpty());
            HOLDING.put(kingdomId, known);
        }
        return known.any();
    }

    /**
     * Where a province is held, {@code null} for no such province.
     */
    @Nullable
    static ProvinceHolds.Target target(ProvinceMap map, NpcState npc, int id) {
        if (targetsMap != map || targetsVersion != map.version() || targetsSettlements != npc.settlementsVersion()) {
            TARGETS.clear();
            targetsMap = map;
            targetsVersion = map.version();
            targetsSettlements = npc.settlementsVersion();
        }
        ProvinceHolds.Target known = TARGETS.get(id);
        if (known != null) {
            return known;
        }
        Optional<Province> province = map.province(id);
        if (province.isEmpty()) {
            return null;
        }
        Settlement seat = province.get().seat() == 0 ? null : npc.settlement((int) province.get().seat()).orElse(null);
        ProvinceHolds.Target target = ProvinceHolds.settled(province.get(), seat)
                ? ProvinceHolds.target(province.get(), seat, map::provinceAt, () -> 0L)
                : ProvinceHolds.stone(id, stoneChunk(map, province.get()));
        TARGETS.put(id, target);
        return target;
    }

    /**
     * The chunk of the boundary stone of a province, searched once for the chunks of the map as they are.
     */
    private static long stoneChunk(ProvinceMap map, Province province) {
        if (stonesMap != map || stonesVersion != map.chunksVersion()) {
            STONES.clear();
            stonesMap = map;
            stonesVersion = map.chunksVersion();
        }
        long middle = ChunkKey.of(province.centerX() >> 4, province.centerZ() >> 4);
        long[] known = STONES.get(province.id());
        if (known != null && known[0] == middle) {
            return known[1];
        }
        long chunk = ProvinceHolds.stoneChunk(province, map::provinceAt, () -> firstChunk(map, province.id(), middle));
        STONES.put(province.id(), new long[]{middle, chunk});
        return chunk;
    }

    /**
     * The first chunk of a province in its tiles, or {@code otherwise} for a province without chunks.
     */
    private static long firstChunk(ProvinceMap map, int id, long otherwise) {
        for (long key : map.tileKeysOf(id)) {
            char[] tile = map.tile(key);
            if (tile == null) {
                continue;
            }
            for (int i = 0; i < tile.length; i++) {
                if (tile[i] == id) {
                    return ChunkKey.of(ProvinceMap.tileX(key) * ProvinceMap.TILE + i % ProvinceMap.TILE,
                            ProvinceMap.tileZ(key) * ProvinceMap.TILE + i / ProvinceMap.TILE);
                }
            }
        }
        return otherwise;
    }

    /**
     * The box where bodies hold the place of a province: the square of the settlement a little above and below its ground (the
     * whole height while its ground is not known), or the whole chunk of the stone.
     */
    private static AABB box(ServerLevel level, ProvinceHolds.Target target, SiegeRules rules) {
        int[] bounds = target.bounds(rules.plazaRadius());
        boolean ground = target.settled() && target.y() != 0;
        return new AABB(bounds[0], ground ? target.y() - HEIGHT : level.getMinBuildHeight(), bounds[1], bounds[2],
                ground ? target.y() + HEIGHT : level.getMaxBuildHeight(), bounds[3]);
    }

    /**
     * The squads of the defenders that stand somewhere far from players: defending, holding or guarding, without bodies.
     */
    private static List<Posted> postedFarAway(MinecraftServer server, Set<UUID> defenders, String dimension) {
        List<Posted> result = new ArrayList<>();
        for (UUID kingdom : defenders) {
            for (ArmySquad squad : ArmyWork.state(server).squadsOf(kingdom)) {
                Order.Kind kind = squad.order().kind();
                if ((kind != Order.Kind.DEFEND && kind != Order.Kind.HOLD && kind != Order.Kind.GUARD) || squad.members().isEmpty()
                        || ArmyWork.hasBodies(squad)) {
                    continue;
                }
                WorldPos at = ArmyField.positionOf(server, squad);
                if (at != null && at.dimension().equals(dimension)) {
                    result.add(new Posted(squad, at));
                }
            }
        }
        return result;
    }

    private static List<ArmySquad> at(List<Posted> posted, ProvinceHolds.Target target) {
        List<ArmySquad> result = new ArrayList<>();
        for (Posted one : posted) {
            if (target.reaches(one.at().x(), one.at().z(), ProvinceHolds.REACH)) {
                result.add(one.squad());
            }
        }
        return result;
    }

    /**
     * The main settlement of a province whose garrison defends it: one of the defenders that stands.
     */
    @Nullable
    private static Settlement garrisonOf(NpcState npc, ProvinceHolds.Target target, Set<UUID> defenders) {
        if (!target.settled()) {
            return null;
        }
        Settlement seat = npc.settlement(target.seat()).orElse(null);
        return seat != null && defenders.contains(seat.kingdomId()) && seat.status() != Settlement.Status.RUINED ? seat : null;
    }

    private static int strength(@Nullable Settlement garrison) {
        return garrison == null ? 0 : garrison.guards() + garrison.militia();
    }

    /**
     * The squads as they are now, those with soldiers left.
     */
    private static List<ArmySquad> alive(MinecraftServer server, List<ArmySquad> squads) {
        List<ArmySquad> result = new ArrayList<>();
        for (ArmySquad squad : squads) {
            ArmyWork.state(server).squad(squad.id()).filter(now -> !now.members().isEmpty()).ifPresent(result::add);
        }
        return result;
    }

    private static ArmySquad strongest(MinecraftServer server, List<ArmySquad> squads) {
        ArmySquad best = squads.get(0);
        double bestPower = -1.0;
        for (ArmySquad squad : squads) {
            double power = ArmyWork.power(server, squad);
            if (power > bestPower) {
                best = squad;
                bestPower = power;
            }
        }
        return best;
    }

    /**
     * Far from players the strongest squad of the attackers at the place of a province fights its defenders by numbers: the
     * strongest squad of the defenders there with the guards and the militia of the settlement, or the garrison alone. The
     * fallen of the garrison are taken from its guards first, then from its militia.
     */
    private static void fight(MinecraftServer server, War war, ProvinceHolds.Target target, String dimension, List<ArmySquad> attacking,
                              List<ArmySquad> defending, @Nullable Settlement garrison) {
        ArmyRules armyRules = ArmySettings.rules();
        ArmySquad attacker = strongest(server, attacking);
        ArmySquad guard = defending.isEmpty() ? null : strongest(server, defending);
        Side ours = ArmyWork.side(server, attacker);
        Side guardSide = guard == null ? null : ArmyWork.side(server, guard);
        int men = strength(garrison);
        Side crowd = ProvinceHolds.garrison(garrison == null ? 0 : garrison.guards(), garrison == null ? 0 : garrison.militia());
        Side theirs = guardSide == null ? crowd : men > 0 ? guardSide.with(crowd) : guardSide;
        Battle.Result result = Battle.resolve(ours, theirs, armyRules.battle(), ArmyWork.rolls());
        if (result.winner() == Battle.Winner.NONE) {
            return;
        }
        UUID enemy = guard != null ? guard.kingdomId() : garrison != null ? garrison.kingdomId() : war.defender();
        WorldPos where = new WorldPos(dimension, target.x(), target.y() != 0 ? target.y() : 64, target.z());
        ArmyWork.Fought first = ArmyWork.apply(server, attacker, ours, result, where, armyRules);
        ArmyWork.announce(server, attacker, first, Component.translatable("kingdomworld.war.battle_at", WarWork.name(server, enemy)));
        if (guard != null) {
            ArmyWork.Fought second = ArmyWork.apply(server, guard, guardSide, WarWork.mirror(result), where, armyRules);
            ArmyWork.announce(server, guard, second, Component.translatable("kingdomworld.war.battle_at", WarWork.name(server,
                    attacker.kingdomId())));
        }
        // The squads have no crowd: whatever the defenders lost by head count, the garrison lost.
        int fallen = garrison == null ? 0 : garrison.loseDefenders(result.secondLost());
        if (garrison != null && men > 0) {
            WarWork.tell(server, garrison.kingdomId(), Component.translatable(SiegeWork.KEY + "garrison_fought",
                    SiegeTexts.provinceName(server, target.province()), fallen).withStyle(ChatFormatting.RED));
        }
        boolean won = result.winner() == Battle.Winner.FIRST;
        WarWork.scoreFieldBattle(server, attacker.kingdomId(), enemy, won, result.killedByFirst(theirs), result.killedBySecond(ours));
        SiegeWork.LOGGER.info("War #{}: a squad of {} fought the defenders of the province {} far from players and {}, the garrison lost {}",
                war.id(), WarWork.name(server, attacker.kingdomId()).getString(), target.province(), won ? "won" : "lost", fallen);
    }

    // ---------------------------------------------------------------- what a hold did

    /**
     * @param seat     the city or throne held, {@code null} for a chunk or a province
     * @param province the place of the province held, {@code null} for the others
     */
    private static void changed(MinecraftServer server, SiegeState state, War war, Hold hold, Hold.Change change, SiegeRules rules, long now,
                                @Nullable SiegeWork.Seat seat, @Nullable ProvinceHolds.Target province) {
        if (change == Hold.Change.NONE) {
            return;
        }
        state.changed();
        DiplomacyState diplomacy = WarWork.state(server);
        double points = DiplomacySettings.rules().scoreHoldDay();
        switch (change) {
            case TAKEN -> {
                // A place gives score once in a war, however often it is taken, freed and taken again.
                boolean score = state.scoreOnce(war.id(), hold.key());
                switch (hold.kind()) {
                    case CHUNK -> {
                        if (score) {
                            diplomacy.score(hold.attacker(), hold.defender(), DiplomacyState.Feat.HOLD, points / 2.0);
                        }
                        WarWork.tell(server, hold.defender(), Component.translatable(SiegeWork.KEY + "chunk_taken_ours",
                                ChunkKey.x(hold.target()), ChunkKey.z(hold.target())).withStyle(ChatFormatting.RED));
                        WarWork.tell(server, hold.attacker(), Component.translatable(SiegeWork.KEY + "chunk_taken_theirs",
                                ChunkKey.x(hold.target()), ChunkKey.z(hold.target())).withStyle(ChatFormatting.GREEN));
                    }
                    case PLAZA, THRONE -> {
                        boolean throne = hold.kind() == Hold.Kind.THRONE;
                        if (score) {
                            diplomacy.score(hold.attacker(), hold.defender(), DiplomacyState.Feat.HOLD, points * (throne ? 3 : 2));
                        }
                        String detail = seat == null ? "" : SiegeTexts.cityName(server, hold.defender(), seat.city());
                        diplomacy.record(now, throne ? "throne_taken" : "city_taken", hold.attacker(), hold.defender(), war.id(), detail);
                        Component label = seat == null ? Component.literal("?") : SiegeTexts.cityLabel(server, hold.defender(), seat.city());
                        WarWork.tell(server, hold.defender(), Component.translatable(SiegeWork.KEY + (throne ? "throne_taken_ours" : "city_taken_ours"),
                                label).withStyle(ChatFormatting.RED));
                        WarWork.tell(server, hold.attacker(), Component.translatable(SiegeWork.KEY + (throne ? "throne_taken_theirs"
                                : "city_taken_theirs"), label).withStyle(ChatFormatting.GREEN));
                        SiegeWork.LOGGER.info("War #{}: {} took {} of {}", war.id(), WarWork.name(server, hold.attacker()).getString(),
                                throne ? "the throne" : "the city " + detail, WarWork.name(server, hold.defender()).getString());
                    }
                    case PROVINCE -> {
                        // A province where people live is worth twice an empty one.
                        boolean settled = province != null && province.settled();
                        if (score) {
                            diplomacy.score(hold.attacker(), hold.defender(), DiplomacyState.Feat.HOLD, points * (settled ? 2 : 1));
                        }
                        diplomacy.record(now, "province_taken", hold.attacker(), hold.defender(), war.id(), Long.toString(hold.target()));
                        Component name = SiegeTexts.provinceName(server, (int) hold.target());
                        WarWork.tell(server, hold.defender(), Component.translatable(SiegeWork.KEY + "province_taken_ours", name)
                                .withStyle(ChatFormatting.RED));
                        WarWork.tell(server, hold.attacker(), Component.translatable(SiegeWork.KEY + "province_taken_theirs", name)
                                .withStyle(ChatFormatting.GREEN));
                        SiegeWork.LOGGER.info("War #{}: {} took the province {} ({}) of {}", war.id(), WarWork.name(server, hold.attacker()).getString(),
                                hold.target(), settled ? "its square" : "its boundary stone", WarWork.name(server, hold.defender()).getString());
                    }
                }
            }
            case FREED -> WarWork.tell(server, hold.defender(), Component.translatable(SiegeWork.KEY + "chunk_freed", ChunkKey.x(hold.target()),
                    ChunkKey.z(hold.target())).withStyle(ChatFormatting.GREEN));
            case INTERRUPTED -> SiegeWork.LOGGER.debug("War #{}: the hold {} was interrupted by defenders", war.id(), hold.key());
            default -> {
            }
        }
    }

    /**
     * The line above the hotbar of the players at a held place: "holding the square: 3 of 5 minutes".
     */
    private static void bar(MinecraftServer server, ServerLevel level, AABB box, Hold hold, long need, boolean frozen) {
        long minutes = hold.progress() / 1_200;
        long all = Math.max(1, (need + 1_199) / 1_200);
        Component line = frozen ? Component.translatable(SiegeWork.KEY + "bar.frozen").withStyle(ChatFormatting.GRAY)
                : hold.taken() && hold.kind() == Hold.Kind.CHUNK
                ? Component.translatable(SiegeWork.KEY + "bar.chunk_retaking", hold.percent(need)).withStyle(ChatFormatting.GOLD)
                : Component.translatable(SiegeWork.KEY + "bar.hold." + hold.kind().id(), minutes, all, SiegeTexts.minutes(all))
                .withStyle(ChatFormatting.GOLD);
        tellIn(server, level, box, line);
    }

    private static void tellIn(MinecraftServer server, ServerLevel level, AABB box, Component line) {
        for (ServerPlayer player : level.players()) {
            if (!(player instanceof FakePlayer) && box.contains(player.position())) {
                player.displayClientMessage(line, true);
            }
        }
    }
}
