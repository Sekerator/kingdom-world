package io.github.sekerator.kingdomworld.common.law;

import com.mojang.logging.LogUtils;
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
 * The alarms of the kingdoms on the server. A blow at a citizen or a soldier, a killing, or a theft from a storehouse that
 * somebody of the kingdom saw (the victim who lived and saw the offender, a witness in sight, a soldier watching the
 * storehouse) raises the alarm of the kingdom at once, with the offender and the place; nobody waits for a witness to reach
 * the guards. While it lasts, every soldier of the kingdom whose body is in the city of the place (and a little beyond its
 * wall), or within {@link LawRules#alarmRadius} blocks of it, runs there, with an order or without one.
 *
 * <p>Nobody sees through walls: the place of an alarm is where a soldier or a citizen of the kingdom last saw the offender,
 * and the alarm ends {@link LawRules#alarmTicks} after that sighting. The guards chase only in a city of the kingdom and
 * {@value #CHASE_BEYOND_CITY} blocks beyond its wall or edge: an offender further off, or one who left, died or paid, ends
 * the alarm and the soldiers go back to their posts. The record stays; the bounty of the guild and the next visit deal with
 * it.
 *
 * <p>A soldier that sees the offender is a witness itself: what witnesses still carry to the guards is written into the
 * record at once, whatever became of them.
 *
 * <p>Who of the soldiers in reach answers is decided by the army ({@code ArmyRelief}): the fighters of the guard at once,
 * its officers and the royal army step by step.
 *
 * <p><b>For the army.</b> {@link #active} gives the alarms of a kingdom as they stand: the offender, the
 * place ({@link Alarm#dimension()}, {@link Alarm#x()}, {@link Alarm#y()}, {@link Alarm#z()}), when the alarm began and how
 * long it lasts ({@link Alarm#started()}, {@link Alarm#duration}), whether somebody was killed ({@link Alarm#killing()}),
 * and how many soldiers the offender killed and wounded since ({@link Alarm#guardsKilled()},
 * {@link Alarm#guardsWounded()}). {@link #cityOf} names the city of the place. Reinforcements are decided from these.
 */
public final class LawAlarms {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** How far beyond the wall of its city an alarm still calls soldiers, in blocks. */
    static final int BEYOND_WALL = 16;
    /** How far beyond the wall or the edge of a city of the kingdom its guards chase an offender, in blocks. */
    public static final int CHASE_BEYOND_CITY = 48;
    /** How many of the nearest bodies of a kingdom look for the offender of an alarm once a second. */
    private static final int LOOKERS = 8;
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
     * Every alarm that lasts now, of all kingdoms.
     */
    public static List<Alarm> all(MinecraftServer server) {
        if (BOOK.isEmpty()) {
            return List.of();
        }
        long now = LawWork.now(server);
        LawRules rules = LawSettings.rules();
        return BOOK.all().stream().filter(alarm -> alarm.active(now, rules)).toList();
    }

    /**
     * Whether an alarm calls the soldiers who stand at a place: in the city of the alarm (and a little beyond its wall), or
     * within {@link LawRules#alarmRadius} blocks of it.
     */
    public static boolean reaches(MinecraftServer server, Alarm alarm, String dimension, int x, int z) {
        if (!alarm.dimension().equals(dimension)) {
            return false;
        }
        double dx = x - alarm.x();
        double dz = z - alarm.z();
        double radius = LawSettings.rules().alarmRadius;
        if (dx * dx + dz * dz <= radius * radius) {
            return true;
        }
        Optional<CityAreas.Area> city = cityOf(server, alarm);
        if (city.isEmpty()) {
            return false;
        }
        int reach = city.get().radius() + BEYOND_WALL;
        return Math.abs(x - city.get().x()) <= reach && Math.abs(z - city.get().z()) <= reach;
    }

    /**
     * The alarm of a kingdom for an offender, if one lasts.
     */
    public static Optional<Alarm> of(MinecraftServer server, UUID kingdomId, UUID offender) {
        return BOOK.of(kingdomId, offender).filter(alarm -> alarm.active(LawWork.now(server), LawSettings.rules()));
    }

    /**
     * Whether the guards of a kingdom chase at a place: in one of its cities or {@value #CHASE_BEYOND_CITY} blocks beyond its
     * wall or edge. The land of a state is far wider: beyond this the chase ends.
     */
    public static boolean inChase(MinecraftServer server, UUID kingdomId, String dimension, int x, int z) {
        return CityAreas.near(server, kingdomId, dimension, x, z, CHASE_BEYOND_CITY).isPresent();
    }

    static boolean inChase(MinecraftServer server, UUID kingdomId, Player player) {
        return inChase(server, kingdomId, Dimensions.id(player.level()), player.getBlockX(), player.getBlockZ());
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
     * A soldier of the kingdom sees the offender of its alarm: the soldier is a witness, and the alarm knows where the offender
     * is now. What witnesses still carry to the guards is written into the record now.
     */
    static void sees(MinecraftServer server, UUID kingdomId, ServerPlayer offender) {
        if (LawWork.isReadOnly(server) || !BOOK.seen(kingdomId, offender.getUUID())) {
            return;
        }
        sighted(server, kingdomId, offender);
        LawWork.reportNow(server, kingdomId, offender.getUUID());
    }

    private static void sighted(MinecraftServer server, UUID kingdomId, ServerPlayer offender) {
        BOOK.sighted(kingdomId, offender.getUUID(), Dimensions.id(offender.level()), offender.getBlockX(), offender.getBlockY(),
                offender.getBlockZ(), LawWork.now(server));
    }

    /**
     * Whether somebody of a kingdom sees a player now: a soldier within {@link LawGuards#SIGHT} blocks or a citizen awake within
     * {@link LawRules#witnessRadius} blocks, with the player in its line of sight. Only the nearest few look.
     */
    static boolean seenBy(UUID kingdomId, ServerPlayer player, LawRules rules) {
        double reach = Math.max(LawGuards.SIGHT, rules.witnessRadius);
        List<CitizenEntity> bodies = new java.util.ArrayList<>(player.level().getEntitiesOfClass(CitizenEntity.class,
                player.getBoundingBox().inflate(reach, 8.0, reach), body -> body.isAlive() && !body.isSleeping()
                        && kingdomId.equals(body.kingdomId()) && body.citizenId() != null));
        bodies.sort(java.util.Comparator.comparingDouble(body -> body.distanceToSqr(player)));
        for (int i = 0; i < Math.min(LOOKERS, bodies.size()); i++) {
            CitizenEntity body = bodies.get(i);
            double sight = LawWork.isSoldier(body) ? LawGuards.SIGHT : rules.witnessRadius;
            if (body.distanceToSqr(player) <= sight * sight && body.hasLineOfSight(player)) {
                return true;
            }
        }
        return false;
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
            // The guard answers at once; its officers and the army come out step by step, when the guard is hard pressed.
            if (distance < bestDistance && (distance <= (double) rules.alarmRadius * rules.alarmRadius || sameCity(server, alarm, soldier))
                    && io.github.sekerator.kingdomworld.common.army.ArmyRelief.answers(server, soldier, alarm)) {
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
     * attack goes on or somebody was killed, and the player is still where the guards chase.
     */
    static boolean strikes(MinecraftServer server, UUID kingdomId, Player player) {
        Optional<Alarm> alarm = BOOK.of(kingdomId, player.getUUID());
        if (alarm.isEmpty()) {
            return false;
        }
        long now = LawWork.now(server);
        LawRules rules = LawSettings.rules();
        return alarm.get().active(now, rules) && alarm.get().violent(now, rules) && inChase(server, kingdomId, player);
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
                    || !answers(offender) || !inChase(server, alarm.kingdomId(), offender)) {
                // Gone beyond the city and the ground around it, from the server or from life: the soldiers go back to their
                // posts. The record stays.
                BOOK.end(alarm.kingdomId(), alarm.offender());
                continue;
            }
            // Only what somebody of the kingdom sees moves the place of the alarm and keeps it going.
            if (seenBy(alarm.kingdomId(), offender, rules)) {
                sighted(server, alarm.kingdomId(), offender);
            }
        }
    }
}
