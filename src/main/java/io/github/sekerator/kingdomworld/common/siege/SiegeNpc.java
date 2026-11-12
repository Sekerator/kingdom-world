package io.github.sekerator.kingdomworld.common.siege;

import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyState;
import io.github.sekerator.kingdomworld.core.diplomacy.War;
import io.github.sekerator.kingdomworld.core.diplomacy.WarGoal;
import io.github.sekerator.kingdomworld.core.siege.Siege;
import io.github.sekerator.kingdomworld.core.siege.SiegeState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Rulers of the world at war in the world: a ruler that leads an attack for a city or the throne besieges it once the land of
 * the enemy is open, brings a ram it builds from its storehouses, and sends half its squads with the order "siege"; a ruler
 * whose city is besieged sends the squads at home to defend it. A ruler holds no more than one siege at a time; whether
 * rulers declare wars on kingdoms of players at all is decided by diplomacy (9a), here they only fight the wars there are.
 */
final class SiegeNpc {
    /** Game ticks between two decisions. */
    static final long EVERY = 600;
    /** Logs a ram takes. */
    private static final int RAM_LOGS = 2;
    /** Iron ingots a ram takes (or one block of iron). */
    private static final int RAM_IRON = 9;

    private static long last = Long.MIN_VALUE;

    private SiegeNpc() {
    }

    static void reset() {
        last = Long.MIN_VALUE;
    }

    static void tick(MinecraftServer server, long now) {
        if (now - last < EVERY || ArmyWork.isReadOnly(server)) {
            return;
        }
        last = now;
        SiegeState state = SiegeWork.state(server);
        DiplomacyState diplomacy = WarWork.state(server);
        for (War war : List.copyOf(diplomacy.wars())) {
            if (!war.active()) {
                continue;
            }
            UUID attacker = war.attacker();
            if (WarWork.isNpc(server, attacker) && (war.goal() == WarGoal.CITY || war.goal() == WarGoal.THRONE)
                    && state.siegesBy(attacker).isEmpty() && SiegeWork.open(server, attacker, war.defender())) {
                besiege(server, state, war, now);
            }
        }
        for (Siege siege : List.copyOf(state.sieges())) {
            if (WarWork.isNpc(server, siege.defender())) {
                defend(server, siege);
            }
        }
        for (var kingdom : io.github.sekerator.kingdomworld.common.api.KingdomWorldApi.kingdoms(server)) {
            if (!WarWork.isNpc(server, kingdom.id()) || !state.siegesOf(kingdom.id()).isEmpty()) {
                continue;
            }
            for (ArmySquad squad : List.copyOf(ArmyWork.state(server).squadsOf(kingdom.id()))) {
                if (squad.order().kind() == Order.Kind.DEFEND) {
                    ArmyWork.order(server, null, squad.id(), Order.NONE);
                }
            }
        }
    }

    private static void besiege(MinecraftServer server, SiegeState state, War war, long now) {
        UUID attacker = war.attacker();
        Optional<SiegeWork.Seat> target = target(server, war);
        if (target.isEmpty()) {
            return;
        }
        List<ArmySquad> squads = new ArrayList<>();
        for (ArmySquad squad : ArmyWork.state(server).squadsOf(attacker)) {
            Order.Kind kind = squad.order().kind();
            // The guard and the reserve at the capital stay home.
            if (!squad.members().isEmpty() && !io.github.sekerator.kingdomworld.common.npc.NpcGarrison.keepsHome(server, squad)
                    && (kind == Order.Kind.NONE || kind == Order.Kind.HOLD || kind == Order.Kind.PATROL || kind == Order.Kind.GUARD)) {
                squads.add(squad);
            }
        }
        if (squads.isEmpty()) {
            return;
        }
        int rams = buildRam(server, attacker, target.get()) ? 1 : 0;
        Optional<Siege> siege = SiegeHolds.start(server, state, war, target.get(), now, rams);
        if (siege.isEmpty()) {
            return;
        }
        WorldPos city = new WorldPos(target.get().dimension(), target.get().x(), target.get().y(), target.get().z());
        int send = Math.max(1, (squads.size() + 1) / 2);
        for (int i = 0; i < send; i++) {
            ArmyWork.order(server, null, squads.get(i).id(), Order.siege(city));
        }
        SiegeWork.LOGGER.info("Ruler of {} besieges {} with {} squad(s) and {} ram(s)", WarWork.name(server, attacker).getString(),
                target.get().name(), send, rams);
    }

    /**
     * The city a ruler besieges: the throne for a war for the throne, else the city of the enemy nearest its own capital.
     */
    private static Optional<SiegeWork.Seat> target(MinecraftServer server, War war) {
        UUID defender = war.defender();
        if (war.goal() == WarGoal.THRONE) {
            return SiegeWork.seat(server, defender, 0);
        }
        Optional<Capital> home = RoadWork.capital(server, war.attacker());
        SiegeWork.Seat best = null;
        long bestDistance = Long.MAX_VALUE;
        for (SiegeWork.Seat seat : SiegeWork.seats(server, defender)) {
            if (seat.city() == 0) {
                continue;
            }
            long dx = home.map(c -> (long) c.x() - seat.x()).orElse(0L);
            long dz = home.map(c -> (long) c.z() - seat.z()).orElse(0L);
            if (dx * dx + dz * dz < bestDistance) {
                best = seat;
                bestDistance = dx * dx + dz * dz;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * A ruler builds a ram from its storehouses: two logs and nine iron ingots (or a block of iron).
     */
    private static boolean buildRam(MinecraftServer server, UUID kingdom, SiegeWork.Seat near) {
        WorldPos at = new WorldPos(near.dimension(), near.x(), near.y(), near.z());
        if (KingdomStorage.count(server, kingdom, stack -> stack.is(ItemTags.LOGS)) < RAM_LOGS) {
            return false;
        }
        boolean block = KingdomStorage.count(server, kingdom, stack -> stack.is(Items.IRON_BLOCK)) >= 1;
        if (!block && KingdomStorage.count(server, kingdom, stack -> stack.is(Items.IRON_INGOT)) < RAM_IRON) {
            return false;
        }
        // Both are counted before anything is taken; whatever was taken goes back if the other part is not there after all.
        List<ItemStack> logs = KingdomStorage.take(server, kingdom, stack -> stack.is(ItemTags.LOGS), RAM_LOGS, at);
        if (logs.stream().mapToInt(ItemStack::getCount).sum() < RAM_LOGS) {
            giveBack(server, kingdom, logs, at);
            return false;
        }
        List<ItemStack> iron = block ? KingdomStorage.take(server, kingdom, stack -> stack.is(Items.IRON_BLOCK), 1, at)
                : KingdomStorage.take(server, kingdom, stack -> stack.is(Items.IRON_INGOT), RAM_IRON, at);
        if (iron.stream().mapToInt(ItemStack::getCount).sum() < (block ? 1 : RAM_IRON)) {
            giveBack(server, kingdom, logs, at);
            giveBack(server, kingdom, iron, at);
            return false;
        }
        return true;
    }

    private static void giveBack(MinecraftServer server, UUID kingdom, List<ItemStack> stacks, WorldPos at) {
        for (ItemStack stack : stacks) {
            ItemStack rest = KingdomStorage.insert(server, kingdom, stack, at);
            if (!rest.isEmpty()) {
                SiegeWork.LOGGER.warn("Ruler of {} could not put back {} x {} after a ram was not built", WarWork.name(server, kingdom).getString(),
                        rest.getCount(), KingdomStorage.itemId(rest));
            }
        }
    }

    /**
     * A ruler whose city is besieged sends the squads at home (or watching its own land) to defend it, half of them.
     */
    private static void defend(MinecraftServer server, Siege siege) {
        WorldPos city = new WorldPos(siege.dimension(), siege.x(), siege.y(), siege.z());
        // A royal army sends its camps to relieve the city, all but a reserve of a third.
        List<ArmySquad> field = io.github.sekerator.kingdomworld.common.npc.NpcGarrison.fieldArmy(server, siege.defender());
        boolean relieving = ArmyWork.state(server).squadsOf(siege.defender()).stream()
                .anyMatch(squad -> squad.order().kind() == Order.Kind.DEFEND && squad.order().points().get(0).equals(city));
        if (!field.isEmpty() && !relieving) {
            int send = io.github.sekerator.kingdomworld.common.npc.NpcGarrison.sendable(field.size());
            int soldiers = 0;
            for (int i = 0; i < send; i++) {
                if (ArmyWork.order(server, null, field.get(i).id(), Order.defend(city)).ok()) {
                    soldiers += field.get(i).members().size();
                }
            }
            SiegeWork.LOGGER.info("Ruler of {} sends {} squad(s) of its army, {} soldier(s), to relieve {}",
                    WarWork.name(server, siege.defender()).getString(), send, soldiers,
                    SiegeTexts.cityName(server, siege.defender(), siege.city()));
            return;
        }
        List<ArmySquad> free = new ArrayList<>();
        for (ArmySquad squad : ArmyWork.state(server).squadsOf(siege.defender())) {
            Order order = squad.order();
            if (order.kind() == Order.Kind.DEFEND && order.points().get(0).equals(city)) {
                return;
            }
            if (!squad.members().isEmpty() && (order.kind() == Order.Kind.NONE || order.kind() == Order.Kind.PATROL
                    || order.kind() == Order.Kind.GUARD)) {
                free.add(squad);
            }
        }
        int send = Math.max(free.isEmpty() ? 0 : 1, free.size() / 2);
        for (int i = 0; i < send; i++) {
            ArmyWork.order(server, null, free.get(i).id(), Order.defend(city));
        }
        if (send > 0) {
            SiegeWork.LOGGER.info("Ruler of {} sends {} squad(s) to defend {}", WarWork.name(server, siege.defender()).getString(), send,
                    SiegeTexts.cityName(server, siege.defender(), siege.city()));
        }
    }
}
