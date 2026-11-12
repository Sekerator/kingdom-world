package io.github.sekerator.kingdomworld.common.army;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.ArmyState;
import io.github.sekerator.kingdomworld.core.army.FogOfWar;
import io.github.sekerator.kingdomworld.core.army.March;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.combat.Formation;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.trade.Caravan;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The army on the map of the atlas, on the server: the view of the kingdom of a player who runs its army, and the orders
 * given there. Rights, squads, targets and places are checked here; a squad of another kingdom can be attacked only while
 * the kingdom sees it or remembers where it was seen, and only in a war that is fought.
 */
public final class ArmyMap {
    private static final String KEY = "kingdomworld.army.map.";

    private ArmyMap() {
    }

    private static Optional<Kingdom> commanded(ServerPlayer player) {
        return KingdomWorldApi.kingdomOf(player.server, player.getUUID()).filter(kingdom -> ArmyField.commands(player, kingdom.id()));
    }

    public static void send(ServerPlayer player) {
        KingdomWorldNetwork.sendToPlayer(player, view(player));
    }

    /**
     * What the kingdom of a player sees in the dimension of the player.
     */
    public static ArmyMapPackets.View view(ServerPlayer player) {
        Optional<Kingdom> kingdom = commanded(player);
        if (kingdom.isEmpty() || ArmyWork.isReadOnly(player.server)) {
            return ArmyMapPackets.View.NONE;
        }
        return view(player.server, kingdom, Dimensions.id(player.level()));
    }

    /**
     * What a kingdom sees in a dimension: its own squads, those of others in sight and the battles going on.
     */
    public static ArmyMapPackets.View view(MinecraftServer server, Optional<Kingdom> kingdom, String dimension) {
        if (kingdom.isEmpty()) {
            return ArmyMapPackets.View.NONE;
        }
        UUID id = kingdom.get().id();
        ArmyState army = ArmyWork.state(server);
        List<ArmyMapPackets.Own> own = new ArrayList<>();
        for (ArmySquad squad : army.squadsOf(id)) {
            WorldPos at = ArmyField.positionOf(server, squad);
            if (at == null || !at.dimension().equals(dimension) || own.size() >= ArmyMapPackets.MAX_ENTRIES) {
                continue;
            }
            Optional<March> march = army.march(squad.id());
            boolean marching = march.isPresent() && !march.get().arrived();
            int state = ArmyField.engaged(squad.id()) ? 2 : marching ? 1 : 0;
            List<WorldPos> ahead = marching ? march.get().path() : List.of();
            int shown = Math.min(ahead.size(), ArmyField.MAX_SHOWN_PATH);
            int[] path = new int[shown * 2];
            for (int i = 0; i < shown; i++) {
                // The last points matter most: where the squad goes.
                WorldPos point = ahead.get(ahead.size() - shown + i);
                path[2 * i] = point.x();
                path[2 * i + 1] = point.z();
            }
            own.add(new ArmyMapPackets.Own(squad.id(), squad.mercenaries(), squad.order().kind().id(), at.x(), at.z(), ArmyField.size(server, squad),
                    state, (int) Math.min(Integer.MAX_VALUE, ArmyField.secondsLeft(server, squad)), squad.formation().id(), path));
        }
        FogOfWar.View seen = ArmyField.view(server, id);
        Set<UUID> allies = ArmyField.always(server, id);
        long now = KingdomWorldApi.gameTime(server);
        List<ArmyMapPackets.Other> others = new ArrayList<>();
        for (FogOfWar.Contact contact : seen.visible()) {
            if (contact.at().dimension().equals(dimension) && others.size() < ArmyMapPackets.MAX_ENTRIES) {
                others.add(other(server, contact.squadId(), contact.kingdomId(), contact.at(), contact.size(), allies, 0));
            }
        }
        List<ArmyMapPackets.Other> marks = new ArrayList<>();
        for (FogOfWar.Sighting mark : seen.lastSeen()) {
            if (mark.at().dimension().equals(dimension) && marks.size() < ArmyMapPackets.MAX_ENTRIES) {
                marks.add(other(server, mark.squadId(), mark.kingdomId(), mark.at(), mark.size(), allies, (int) Math.max(1, (now - mark.seen()) / 20)));
            }
        }
        List<WorldPos> battles = ArmyField.battles(server, id, dimension);
        int count = Math.min(battles.size(), ArmyMapPackets.MAX_ENTRIES);
        int[] places = new int[count * 2];
        for (int i = 0; i < count; i++) {
            places[2 * i] = battles.get(i).x();
            places[2 * i + 1] = battles.get(i).z();
        }
        return new ArmyMapPackets.View(true, dimension, kingdom.get().color(), own, others, marks, places);
    }

    private static ArmyMapPackets.Other other(MinecraftServer server, long squad, UUID kingdomId, WorldPos at, int size, Set<UUID> allies, int seconds) {
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdom(server, kingdomId);
        String name = kingdom.map(Kingdom::name).orElse("?");
        return new ArmyMapPackets.Other(squad, name.length() > 64 ? name.substring(0, 64) : name, kingdom.map(Kingdom::color).orElse(0x808080),
                at.x(), at.z(), size, allies.contains(kingdomId), seconds);
    }

    /**
     * An order or a formation given on the map: the answer goes to the player, then a fresh view.
     */
    public static void command(ServerPlayer player, ArmyMapPackets.Command command) {
        KingdomActions.reply(player, apply(player, command));
        send(player);
    }

    /**
     * Applies an order or a formation given on the map.
     */
    public static KingdomActions.Feedback apply(ServerPlayer player, ArmyMapPackets.Command command) {
        MinecraftServer server = player.server;
        Optional<Kingdom> kingdom = commanded(player);
        if (kingdom.isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.NO_PERMISSION);
        }
        UUID id = kingdom.get().id();
        ArmyState army = ArmyWork.state(server);
        if (command.squads().isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_SQUAD);
        }
        for (long squad : command.squads()) {
            if (army.squad(squad).filter(found -> found.kingdomId().equals(id)).isEmpty()) {
                return KingdomActions.Feedback.error(KingdomError.UNKNOWN_SQUAD);
            }
        }
        if (command.action() == ArmyMapPackets.Command.Action.FORMATION) {
            Optional<Formation> formation = Formation.byId(command.kind());
            if (formation.isEmpty()) {
                return KingdomActions.Feedback.error(KingdomError.BAD_ORDER);
            }
            int changed = 0;
            for (long squad : command.squads()) {
                if (ArmyWork.change(server, "change a formation", state -> state.setFormation(player.getUUID(), squad, formation.get(),
                        KingdomWorldApi.state(server))).ok()) {
                    changed++;
                }
            }
            return changed > 0 ? KingdomActions.Feedback.ok(KEY + "formation", ArmyTexts.formationName(formation.get()))
                    : KingdomActions.Feedback.error(KingdomError.UNCHANGED);
        }
        Optional<Order.Kind> kind = Order.Kind.byId(command.kind());
        if (kind.isEmpty() || kind.get() == Order.Kind.NONE || kind.get() == Order.Kind.FOLLOW || kind.get() == Order.Kind.DEFEND) {
            return KingdomActions.Feedback.error(KingdomError.BAD_ORDER);
        }
        ServerLevel level = player.serverLevel();
        List<WorldPos> points = places(level, player, command.points());
        List<WorldPos> via = places(level, player, command.via());
        Order order;
        try {
            order = order(server, id, kind.get(), points, command.target(), command.radius());
        } catch (OrderRefused refused) {
            return KingdomActions.Feedback.error(refused.error);
        } catch (IllegalArgumentException e) {
            return KingdomActions.Feedback.error(KingdomError.BAD_ORDER);
        }
        if (!via.isEmpty()) {
            if (!order.kind().marches()) {
                return KingdomActions.Feedback.error(KingdomError.BAD_ORDER);
            }
            order = order.withVia(via);
        }
        int given = 0;
        Outcome first = null;
        for (long squad : command.squads()) {
            Outcome outcome = ArmyWork.order(server, player.getUUID(), squad, order);
            if (outcome.ok()) {
                given++;
            } else if (first == null) {
                first = outcome;
            }
        }
        if (given == 0) {
            return KingdomActions.Feedback.error(first != null ? first.error() : KingdomError.BAD_ORDER);
        }
        return KingdomActions.Feedback.ok(KEY + "ordered", ArmyTexts.orderName(order.kind()), given);
    }

    private static final class OrderRefused extends RuntimeException {
        private final KingdomError error;

        OrderRefused(KingdomError error) {
            super(error.name(), null, false, false);
            this.error = error;
        }
    }

    /**
     * The order a command asks for, with what the server knows put in: the place of a squad attacked, the deal of a caravan,
     * the barracks to go back to.
     */
    static Order order(MinecraftServer server, UUID kingdomId, Order.Kind kind, List<WorldPos> points, long target, int radius) {
        if (kind != Order.Kind.RETREAT && kind != Order.Kind.ESCORT && points.isEmpty() && !(kind == Order.Kind.ATTACK && target != 0)) {
            throw new OrderRefused(KingdomError.BAD_ORDER);
        }
        return switch (kind) {
            case MOVE -> Order.move(points.get(0));
            case HOLD -> Order.hold(points.get(0));
            case GUARD -> Order.guard(points.get(0), radius);
            case SCOUT -> Order.scout(points.get(0), radius);
            case PATROL -> Order.patrol(points);
            case RETREAT -> {
                WorldPos home = ArmyField.home(server, kingdomId);
                if (home == null) {
                    throw new OrderRefused(KingdomError.BAD_ORDER);
                }
                yield Order.retreat(home);
            }
            case SIEGE -> {
                UUID owner = ownerAt(server, points.get(0));
                if (owner == null || !WarWork.hostileNow(server, kingdomId, owner)) {
                    throw new OrderRefused(KingdomError.NOT_AT_WAR);
                }
                yield Order.siege(points.get(0));
            }
            case ESCORT -> {
                Caravan caravan = KingdomWorldApi.state(server).trade().caravans().stream().filter(one -> one.id() == target).findFirst()
                        .orElseThrow(() -> new OrderRefused(KingdomError.BAD_ORDER));
                yield Order.escort(caravan.dealId());
            }
            case ATTACK -> {
                if (target != 0) {
                    ArmySquad enemy = ArmyWork.state(server).squad(target).orElseThrow(() -> new OrderRefused(KingdomError.UNKNOWN_SQUAD));
                    if (enemy.kingdomId().equals(kingdomId) || !WarWork.hostileNow(server, kingdomId, enemy.kingdomId())) {
                        throw new OrderRefused(KingdomError.NOT_AT_WAR);
                    }
                    if (!ArmyField.known(server, kingdomId, target)) {
                        // What the kingdom does not see cannot be attacked.
                        throw new OrderRefused(KingdomError.UNKNOWN_SQUAD);
                    }
                    WorldPos at = ArmyField.sees(server, kingdomId, enemy) ? ArmyField.positionOf(server, enemy)
                            : ArmyField.view(server, kingdomId).lastSeen().stream().filter(mark -> mark.squadId() == target).findFirst()
                            .map(FogOfWar.Sighting::at).orElse(null);
                    if (at == null) {
                        throw new OrderRefused(KingdomError.UNKNOWN_SQUAD);
                    }
                    yield Order.attack(at, target);
                }
                WorldPos place = points.get(0);
                // Only camps the kingdom knows: an unknown camp answers as an empty place.
                boolean camp = RoadWork.state(server).camps().stream().anyMatch(one -> !one.cleared() && one.known().contains(kingdomId)
                        && one.dimension().equals(place.dimension())
                        && one.distanceSqr(place.x(), place.z()) <= (double) ArmyField.PLACE_REACH * ArmyField.PLACE_REACH);
                UUID owner = ownerAt(server, place);
                if (!camp && (owner == null || !WarWork.hostileNow(server, kingdomId, owner))) {
                    // A city of an enemy whose players are away is protected; a place with nothing to fight is no target.
                    throw new OrderRefused(owner == null ? KingdomError.BAD_ORDER : KingdomError.NOT_AT_WAR);
                }
                yield Order.attack(place, 0);
            }
            default -> throw new OrderRefused(KingdomError.BAD_ORDER);
        };
    }

    private static UUID ownerAt(MinecraftServer server, WorldPos pos) {
        return KingdomWorldApi.state(server).territory().ownerOf(pos.dimension(), ChunkKey.of(pos.chunkX(), pos.chunkZ()));
    }

    /**
     * Places of a command in the dimension of the player, at the height of the ground where it is known.
     */
    static List<WorldPos> places(ServerLevel level, ServerPlayer player, int[] xz) {
        List<WorldPos> places = new ArrayList<>();
        String dimension = Dimensions.id(level);
        for (int i = 0; i + 1 < xz.length; i += 2) {
            int x = xz[i];
            int z = xz[i + 1];
            int y = level.hasChunk(x >> 4, z >> 4) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : player.getBlockY();
            places.add(new WorldPos(dimension, x, y, z));
        }
        return places;
    }
}
