package io.github.sekerator.kingdomworld.common.siege;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.diplomacy.DiplomacySettings;
import io.github.sekerator.kingdomworld.common.diplomacy.WarEvents;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyState;
import io.github.sekerator.kingdomworld.core.diplomacy.War;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.siege.Gate;
import io.github.sekerator.kingdomworld.core.siege.Hold;
import io.github.sekerator.kingdomworld.core.siege.Siege;
import io.github.sekerator.kingdomworld.core.siege.SiegeRules;
import io.github.sekerator.kingdomworld.core.siege.SiegeState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Places held by attackers: chunks of the enemy's land (the goal "land"), the squares of its cities (the goal "city") and its
 * throne hall (the goal "throne"). Near players the bodies standing there count; far from players the squads do: a squad
 * besieging a city whose gates are down holds its square while no squad of the defenders stands there.
 */
final class SiegeHolds {
    /** Blocks above and below the middle of a square or a hall that still count. */
    private static final int HEIGHT = 8;
    /** Blocks from a city within which a squad besieges or defends it far from players. */
    private static final int SQUAD_REACH = 48;

    private SiegeHolds() {
    }

    static void reset() {
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
                case LAND -> chunks(server, state, war, attackers, defenders, frozen, rules, now, ticks);
                case CITY, THRONE -> seats(server, state, war, attackers, defenders, frozen, rules, now, ticks);
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
            if (defender.equals(KingdomWorldApi.state(server).territory().ownerOf(dimension, chunk))) {
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
                if (defender.equals(KingdomWorldApi.state(server).territory().ownerOf(anchor.get().dimension(), chunk))) {
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
            changed(server, state, war, hold, change, rules, now, null);
            if (level != null && (a || d) && !hold.idle()) {
                bar(server, level, new AABB(x - 8, level.getMinBuildHeight(), z - 8, x + 8, level.getMaxBuildHeight(), z + 8), hold, rules, frozen);
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
                d = defendedFarAway(server, defenders, seat.dimension(), seat.x(), seat.z(), SQUAD_REACH);
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
            changed(server, state, war, hold, change, rules, now, seat);
            if (level != null && (a || d) && !hold.taken() && hold.progress() > 0) {
                bar(server, level, box, hold, rules, frozen);
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
                if (squad.order().kind() == Order.Kind.SIEGE && near(squad.order().points().get(0), seat, SQUAD_REACH)) {
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
    private static int besiegersFarAway(MinecraftServer server, Set<UUID> attackers, SiegeWork.Seat seat) {
        int soldiers = 0;
        for (UUID attacker : attackers) {
            for (ArmySquad squad : ArmyWork.state(server).squadsOf(attacker)) {
                if (squad.order().kind() == Order.Kind.SIEGE && !ArmyWork.hasBodies(squad) && near(squad.order().points().get(0), seat, SQUAD_REACH)) {
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

    // ---------------------------------------------------------------- what a hold did

    private static void changed(MinecraftServer server, SiegeState state, War war, Hold hold, Hold.Change change, SiegeRules rules, long now,
                                SiegeWork.Seat seat) {
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
    private static void bar(MinecraftServer server, ServerLevel level, AABB box, Hold hold, SiegeRules rules, boolean frozen) {
        long need = rules.ticks(hold.kind());
        long minutes = hold.progress() / 1_200;
        long all = Math.max(1, (need + 1_199) / 1_200);
        Component line = frozen ? Component.translatable(SiegeWork.KEY + "bar.frozen").withStyle(ChatFormatting.GRAY)
                : hold.taken() && hold.kind() == Hold.Kind.CHUNK
                ? Component.translatable(SiegeWork.KEY + "bar.chunk_retaking", hold.percent(rules)).withStyle(ChatFormatting.GOLD)
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
