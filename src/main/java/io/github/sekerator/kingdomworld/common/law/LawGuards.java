package io.github.sekerator.kingdomworld.common.law;

import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.law.Alarm;
import io.github.sekerator.kingdomworld.core.law.LawRules;
import io.github.sekerator.kingdomworld.core.law.Wanted;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The guards: soldiers whose squad holds a point, guards an area or walks a patrol (and, with the roads, the guards of the
 * gates). A guard that sees a wanted player in a city of its kingdom or near it first calls out; the player may give up (a
 * click on the guard opens the window to pay the fine). Who neither gives up nor leaves in {@link LawRules#hailTicks} is
 * attacked: beaten down to little health for petty crimes, killed for a murder. Players away from the keyboard or offline
 * are never touched; their record waits.
 *
 * <p>An alarm ({@link LawAlarms}) calls every soldier of the kingdom near its place, guards and soldiers without an order
 * alike: they run to where the offender was last seen and, once they see the offender, while the attack goes on or after a
 * killing, strike without calling out. A soldier that does not see the offender knows only that place.
 */
final class LawGuards {
    /** How far a guard looks for the wanted, in blocks. */
    static final double SEARCH = 16.0;
    /** How far a soldier sees the offender of an alarm it answers, in blocks, when nothing stands between them. */
    static final double SIGHT = 32.0;

    /**
     * A wanted player was called out by a guard of a kingdom at a time.
     */
    private record Hail(UUID kingdom, long at) {
    }

    private static final Map<UUID, Hail> HAILS = new HashMap<>();

    private LawGuards() {
    }

    static void reset() {
        HAILS.clear();
    }

    static void forget(UUID player) {
        HAILS.remove(player);
    }

    /**
     * Whether a soldier body is a guard: its squad holds a point, guards an area or patrols.
     */
    static boolean isGuard(MinecraftServer server, CitizenEntity body) {
        if (!LawWork.isSoldier(body) || body.citizenId() == null || ArmyWork.isReadOnly(server)) {
            return false;
        }
        Order.Kind kind = ArmyWork.orderOf(server, body.citizenId()).kind();
        return kind == Order.Kind.HOLD || kind == Order.Kind.GUARD || kind == Order.Kind.PATROL;
    }

    /**
     * Whether guards may stop a player at all: online, at the keyboard, alive and no spectator. A player in creative mode is
     * dealt with as any other while the config says so; the blows do such a player no harm.
     */
    static boolean catchable(MinecraftServer server, ServerPlayer player) {
        return LawAlarms.answers(player) && player.isAlive() && !player.hasDisconnected() && !LawWork.isAway(player);
    }

    /**
     * The player a soldier deals with now for the law, if any. An alarm of its kingdom calls every soldier near, with an
     * order or without: it runs to the offender, and strikes at once while the attack goes on or after a killing. Otherwise
     * a guard deals with the wanted it sees: the first time it calls out, later it attacks.
     */
    @Nullable
    static LawWork.Pursuit pursue(MinecraftServer server, CitizenEntity guard) {
        if (LawWork.isReadOnly(server) || !LawWork.isSoldier(guard) || guard.kingdomId() == null) {
            return null;
        }
        LawWork.Pursuit called = answer(server, guard);
        if (called != null) {
            return called;
        }
        if (!isGuard(server, guard)) {
            return null;
        }
        UUID kingdom = guard.kingdomId();
        ServerPlayer best = null;
        double bestDistance = SEARCH * SEARCH;
        for (ServerPlayer player : guard.serverLevel().players()) {
            double distance = guard.distanceToSqr(player);
            // A player in creative mode cannot be made to pay, so the chase ends with the alarm: a guard on its round does not
            // start it anew for an old record.
            if (distance >= bestDistance || player.isCreative() || !catchable(server, player)
                    || LawWork.state(server).wanted(kingdom, player.getUUID()).isEmpty()
                    || !LawAlarms.inChase(server, kingdom, player) || !guard.hasLineOfSight(player)) {
                continue;
            }
            best = player;
            bestDistance = distance;
        }
        if (best == null) {
            return null;
        }
        return callOut(server, guard, best, kingdom, false);
    }

    /**
     * A soldier that sees a wanted player calls out the first time and attacks once the time to give up is over.
     */
    private static LawWork.Pursuit callOut(MinecraftServer server, CitizenEntity guard, ServerPlayer player, UUID kingdom, boolean urgent) {
        long now = LawWork.now(server);
        Hail hail = HAILS.get(player.getUUID());
        if (hail == null || !hail.kingdom().equals(kingdom)) {
            hail(server, guard, player, kingdom, now);
            return LawWork.Pursuit.inSight(player, false, urgent);
        }
        return LawWork.Pursuit.inSight(player, now - hail.at() >= LawSettings.rules().hailTicks, urgent);
    }

    /**
     * What a soldier does about an alarm of its kingdom in reach: it runs to where the offender was last seen. Seeing the
     * offender, it is a witness itself; then it strikes at once while the attack goes on or after a killing, and calls out as
     * a guard does when the matter is petty or the blows stopped.
     */
    @Nullable
    private static LawWork.Pursuit answer(MinecraftServer server, CitizenEntity soldier) {
        Alarm alarm = LawAlarms.callFor(server, soldier);
        if (alarm == null) {
            return null;
        }
        ServerPlayer offender = LawAlarms.offender(server, alarm);
        if (offender == null || offender.level() != soldier.level() || !catchable(server, offender)) {
            return null;
        }
        UUID kingdom = alarm.kingdomId();
        boolean sees = soldier.distanceToSqr(offender) < SIGHT * SIGHT && soldier.hasLineOfSight(offender);
        if (!sees) {
            // Out of sight: the soldier knows only where somebody of the kingdom last saw the offender, and searches there.
            return LawWork.Pursuit.lastSeen(offender, new BlockPos(alarm.x(), alarm.y(), alarm.z()));
        }
        LawAlarms.sees(server, kingdom, offender);
        if (alarm.violent(LawWork.now(server), LawSettings.rules())) {
            return LawWork.Pursuit.inSight(offender, true, true);
        }
        if (LawWork.state(server).wanted(kingdom, offender.getUUID()).isPresent()) {
            return callOut(server, soldier, offender, kingdom, true);
        }
        return LawWork.Pursuit.inSight(offender, false, true);
    }

    /**
     * A guard calls out to a wanted player: one line above the hotbar with what the player owes, and how to give up.
     */
    static void hail(MinecraftServer server, @Nullable CitizenEntity guard, ServerPlayer player, UUID kingdom, long now) {
        HAILS.put(player.getUUID(), new Hail(kingdom, now));
        Optional<Wanted> wanted = LawWork.state(server).wanted(kingdom, player.getUUID());
        if (wanted.isEmpty()) {
            return;
        }
        Component message = wanted.get().executable() ? Component.translatable(LawWork.KEY + "hail_murder")
                : Component.translatable(LawWork.KEY + "hail", LawTexts.shortAmount(wanted.get().fine(LawSettings.rules(),
                LawWork.state(server).law(kingdom).fineMultiplierPermille()), LawTexts.units(server, kingdom)));
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        if (guard != null) {
            guard.level().playSound(null, guard.getX(), guard.getY(), guard.getZ(), SoundEvents.VINDICATOR_AMBIENT, SoundSource.HOSTILE, 1.0f, 0.8f);
            guard.getLookControl().setLookAt(player, 30.0f, 30.0f);
        }
    }

    /**
     * Whether the soldiers of a kingdom may fight a player now for the law: called out long enough ago, still wanted and still
     * where its guards chase.
     */
    static boolean pursues(MinecraftServer server, UUID kingdom, Player player) {
        if (LawWork.isReadOnly(server)) {
            return false;
        }
        if (LawAlarms.strikes(server, kingdom, player)) {
            // An attack that goes on, or a killing: nobody calls out first.
            return true;
        }
        Hail hail = HAILS.get(player.getUUID());
        if (hail == null || !hail.kingdom().equals(kingdom)) {
            return false;
        }
        if (LawWork.now(server) - hail.at() < LawSettings.rules().hailTicks) {
            return false;
        }
        return LawWork.state(server).wanted(kingdom, player.getUUID()).isPresent() && LawAlarms.inChase(server, kingdom, player);
    }

    /**
     * Forgets calls of players who are no longer wanted, and old ones: a player who left and came back is called out again.
     */
    static void tick(MinecraftServer server, long now) {
        long stale = Math.max(1200L, LawSettings.rules().hailTicks * 12L);
        HAILS.entrySet().removeIf(entry -> now - entry.getValue().at() > stale
                || LawWork.state(server).wanted(entry.getValue().kingdom(), entry.getKey()).isEmpty());
    }

    /**
     * Calls out a player at once as a guard would, for checks and commands.
     */
    static void hailNow(MinecraftServer server, ServerPlayer player, UUID kingdom, long at) {
        HAILS.put(player.getUUID(), new Hail(kingdom, at));
    }
}
