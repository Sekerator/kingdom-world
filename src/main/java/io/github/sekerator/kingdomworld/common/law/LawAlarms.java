package io.github.sekerator.kingdomworld.common.law;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.road.CityAreas;
import io.github.sekerator.kingdomworld.core.law.Alarm;
import io.github.sekerator.kingdomworld.core.law.AlarmBook;
import io.github.sekerator.kingdomworld.core.law.LawRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The alarms of the kingdoms on the server. A blow at a citizen or a soldier, a killing, or a theft from a storehouse a
 * soldier noticed raises the alarm of the kingdom at once, with the offender and the place; nobody waits for a witness to
 * reach the guards. While it lasts, every soldier of the kingdom whose body is in the city of the place (and a little
 * beyond its wall), or within {@link LawRules#alarmRadius} blocks of it, runs there, with an order or without one. The place
 * follows the offender while the offender stays on the land of the kingdom; the alarm ends when the offender left the land,
 * died or paid, or when nothing fed it for {@link LawRules#alarmTicks}.
 *
 * <p>A soldier that sees the offender is a witness itself: what nobody reported yet is written into the record at once,
 * whatever became of the first witnesses.
 *
 * <p><b>For the army of later stages.</b> {@link #active} gives the alarms of a kingdom as they stand: the offender, the
 * place ({@link Alarm#dimension()}, {@link Alarm#x()}, {@link Alarm#y()}, {@link Alarm#z()}), when the alarm began and how
 * long it lasts ({@link Alarm#started()}, {@link Alarm#duration}), whether somebody was killed ({@link Alarm#killing()}),
 * and how many soldiers the offender killed and wounded since ({@link Alarm#guardsKilled()},
 * {@link Alarm#guardsWounded()}). {@link #cityOf} names the city of the place. Reinforcements are decided from these.
 */
public final class LawAlarms {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** How far beyond the wall of its city an alarm still calls soldiers, in blocks. */
    static final int BEYOND_WALL = 16;
    /** How near an alarm a wounded soldier stays in the fight, in blocks. */
    private static final double HOLD_OUT = 32.0;

    private static final AlarmBook BOOK = new AlarmBook();
    /** Players game tests stand in for, who are in no list of players. */
    private static final Map<UUID, ServerPlayer> STAND_INS = new HashMap<>();

    private LawAlarms() {
    }

    static void reset() {
        BOOK.clear();
        STAND_INS.clear();
    }

    // ---------------------------------------------------------------- reading

    /**
     * The alarms of a kingdom that last now, the one raised first first.
     */
    public static List<Alarm> active(MinecraftServer server, UUID kingdomId) {
        long now = LawWork.now(server);
        LawRules rules = LawSettings.rules();
        return BOOK.in(kingdomId).stream().filter(alarm -> alarm.active(now, rules)).toList();
    }

    /**
     * The alarm of a kingdom for an offender, if one lasts.
     */
    public static Optional<Alarm> of(MinecraftServer server, UUID kingdomId, UUID offender) {
        return BOOK.of(kingdomId, offender).filter(alarm -> alarm.active(LawWork.now(server), LawSettings.rules()));
    }

    /**
     * The city of the kingdom the place of an alarm lies in, if it lies in one.
     */
    public static Optional<CityAreas.Area> cityOf(MinecraftServer server, Alarm alarm) {
        return CityAreas.at(server, alarm.kingdomId(), alarm.dimension(), alarm.x(), alarm.z());
    }

    /**
     * The player an alarm is about, if the player is on the server.
     */
    @Nullable
    public static ServerPlayer offender(MinecraftServer server, Alarm alarm) {
        return present(server, alarm.offender());
    }

    /**
     * A player who is on the server: one in the list of players, or one somebody stands in for.
     */
    @Nullable
    static ServerPlayer present(MinecraftServer server, UUID player) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        return online != null ? online : STAND_INS.get(player);
    }

    /**
     * Lets a player who is in no list of players be found as the offender of an alarm, for game tests and for the acts
     * of a stand-in.
     */
    public static void standInForCheck(ServerPlayer player, boolean known) {
        if (known) {
            STAND_INS.put(player.getUUID(), player);
        } else {
            STAND_INS.remove(player.getUUID());
        }
    }

    // ---------------------------------------------------------------- raising

    /**
     * Whether the soldiers answer what this player does: not a machine or a spectator, and a player in creative mode only
     * when the config lets them.
     */
    static boolean answers(Player player) {
        return !(player instanceof FakePlayer) && !player.isSpectator() && (!player.isCreative() || LawSettings.rules().pursueCreative);
    }

    /**
     * Raises the alarm of a kingdom for a player, or feeds the one that lasts.
     */
    static void raise(ServerPlayer offender, UUID kingdomId, BlockPos where, Alarm.Cause cause) {
        if (!answers(offender)) {
            return;
        }
        MinecraftServer server = offender.server;
        boolean fresh = BOOK.of(kingdomId, offender.getUUID()).isEmpty();
        BOOK.raise(kingdomId, offender.getUUID(), offender.getGameProfile().getName(), Dimensions.id(offender.level()), where.getX(), where.getY(),
                where.getZ(), LawWork.now(server), cause);
        if (fresh) {
            LOGGER.info("Alarm in {}: {} at {} {} {} ({})", kingdomId, offender.getGameProfile().getName(), where.getX(), where.getY(), where.getZ(),
                    cause);
        }
    }

    /**
     * A crime of the offender of an alarm that nobody reported: kept until a soldier sees the offender.
     */
    static void unseen(UUID kingdomId, UUID offender, Alarm.Deed deed) {
        BOOK.unseen(kingdomId, offender, deed);
    }

    /**
     * A soldier of the kingdom sees the offender of its alarm: the soldier is a witness. What nobody reported yet and what
     * witnesses still carry to the guards is written into the record now.
     */
    static void sees(MinecraftServer server, UUID kingdomId, ServerPlayer offender) {
        if (BOOK.of(kingdomId, offender.getUUID()).isEmpty() || LawWork.isReadOnly(server)) {
            return;
        }
        String name = offender.getGameProfile().getName();
        for (Alarm.Deed deed : BOOK.seen(kingdomId, offender.getUUID())) {
            LawWork.commit(server, kingdomId, offender.getUUID(), name, deed.kind(), deed.worth(), deed.victim());
        }
        LawWork.reportNow(server, kingdomId, offender.getUUID());
    }

    /**
     * The offender of an alarm wounded or killed a soldier of the kingdom.
     */
    static void guardHurt(UUID kingdomId, UUID offender, UUID soldier, boolean killed) {
        BOOK.guardHurt(kingdomId, offender, soldier, killed);
    }

    static void end(UUID kingdomId, UUID offender) {
        BOOK.end(kingdomId, offender);
    }

    // ---------------------------------------------------------------- soldiers

    /**
     * The alarm a soldier answers now: the nearest one of its kingdom whose place is in reach of its body.
     */
    @Nullable
    static Alarm callFor(MinecraftServer server, CitizenEntity soldier) {
        UUID kingdom = soldier.kingdomId();
        if (kingdom == null || BOOK.isEmpty()) {
            return null;
        }
        List<Alarm> alarms = BOOK.in(kingdom);
        if (alarms.isEmpty()) {
            return null;
        }
        long now = LawWork.now(server);
        LawRules rules = LawSettings.rules();
        String dimension = Dimensions.id(soldier.level());
        Alarm best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Alarm alarm : alarms) {
            if (!alarm.active(now, rules) || !alarm.dimension().equals(dimension)) {
                continue;
            }
            double dx = soldier.getX() - (alarm.x() + 0.5);
            double dz = soldier.getZ() - (alarm.z() + 0.5);
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance && (distance <= (double) rules.alarmRadius * rules.alarmRadius || sameCity(server, alarm, soldier))) {
                best = alarm;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Whether a soldier stands in the city of the place of an alarm, or a little beyond its wall.
     */
    private static boolean sameCity(MinecraftServer server, Alarm alarm, CitizenEntity soldier) {
        Optional<CityAreas.Area> city = cityOf(server, alarm);
        if (city.isEmpty()) {
            return false;
        }
        int reach = city.get().radius() + BEYOND_WALL;
        return Math.abs(soldier.getBlockX() - city.get().x()) <= reach && Math.abs(soldier.getBlockZ() - city.get().z()) <= reach;
    }

    /**
     * Whether the soldiers of a kingdom strike a player at once, without calling out: the alarm for the player lasts, the
     * attack goes on or somebody was killed, and the player still stands on the land of the kingdom.
     */
    static boolean strikes(MinecraftServer server, UUID kingdomId, Player player) {
        Optional<Alarm> alarm = BOOK.of(kingdomId, player.getUUID());
        if (alarm.isEmpty()) {
            return false;
        }
        long now = LawWork.now(server);
        LawRules rules = LawSettings.rules();
        return alarm.get().active(now, rules) && alarm.get().violent(now, rules)
                && kingdomId.equals(KingdomWorldApi.ownerAt(player.level(), player.blockPosition()));
    }

    /**
     * Whether a wounded soldier stays in the fight rather than walk off to be cared for: an alarm of its kingdom lasts close
     * by.
     */
    static boolean holdsOut(MinecraftServer server, CitizenEntity soldier) {
        UUID kingdom = soldier.kingdomId();
        if (kingdom == null || BOOK.isEmpty()) {
            return false;
        }
        long now = LawWork.now(server);
        LawRules rules = LawSettings.rules();
        String dimension = Dimensions.id(soldier.level());
        for (Alarm alarm : BOOK.in(kingdom)) {
            if (alarm.active(now, rules) && alarm.dimension().equals(dimension)
                    && soldier.distanceToSqr(alarm.x() + 0.5, alarm.y(), alarm.z() + 0.5) <= HOLD_OUT * HOLD_OUT) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- once a second

    static void tick(MinecraftServer server, long now) {
        if (BOOK.isEmpty()) {
            return;
        }
        LawRules rules = LawSettings.rules();
        for (Alarm ended : BOOK.expire(now, rules)) {
            LOGGER.debug("The alarm of {} for {} ran out after {} ticks", ended.kingdomId(), ended.name(), ended.duration(now));
        }
        for (Alarm alarm : BOOK.all()) {
            ServerPlayer offender = offender(server, alarm);
            if (LawWork.kingdom(server, alarm.kingdomId()).isEmpty() || offender == null || !offender.isAlive() || offender.hasDisconnected()
                    || !answers(offender)
                    || !alarm.kingdomId().equals(KingdomWorldApi.ownerAt(offender.level(), offender.blockPosition()))) {
                // Gone from the land, from the server or from life: the soldiers go back to their posts. The record stays.
                BOOK.end(alarm.kingdomId(), alarm.offender());
                continue;
            }
            BOOK.moved(alarm.kingdomId(), alarm.offender(), Dimensions.id(offender.level()), offender.getBlockX(), offender.getBlockY(),
                    offender.getBlockZ());
        }
    }
}
