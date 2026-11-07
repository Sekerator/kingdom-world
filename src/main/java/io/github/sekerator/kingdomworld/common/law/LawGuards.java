package io.github.sekerator.kingdomworld.common.law;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.law.LawRules;
import io.github.sekerator.kingdomworld.core.law.Wanted;
import net.minecraft.ChatFormatting;
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
 * gates). A guard that sees a wanted player on the land of its kingdom first calls out; the player may give up (a click on
 * the guard opens the window to pay the fine). Who neither gives up nor leaves the land in {@link LawRules#hailTicks} is
 * attacked: beaten down to little health for petty crimes, killed for a murder. Players away from the keyboard or offline
 * are never touched; their record waits.
 */
final class LawGuards {
    /** How far a guard looks for the wanted, in blocks. */
    static final double SEARCH = 16.0;

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
     * Whether guards may stop a player at all: online, at the keyboard, alive, not in creative or spectator mode.
     */
    static boolean catchable(MinecraftServer server, ServerPlayer player) {
        return !(player instanceof FakePlayer) && player.isAlive() && !player.isCreative() && !player.isSpectator() && !player.hasDisconnected()
                && !LawWork.isAway(player);
    }

    /**
     * The wanted player a guard deals with now, if any: the first time it calls out, later it attacks.
     */
    @Nullable
    static LawWork.Pursuit pursue(MinecraftServer server, CitizenEntity guard) {
        if (LawWork.isReadOnly(server) || !isGuard(server, guard)) {
            return null;
        }
        UUID kingdom = guard.kingdomId();
        ServerPlayer best = null;
        double bestDistance = SEARCH * SEARCH;
        for (ServerPlayer player : guard.serverLevel().players()) {
            double distance = guard.distanceToSqr(player);
            if (distance >= bestDistance || !catchable(server, player) || LawWork.state(server).wanted(kingdom, player.getUUID()).isEmpty()
                    || !kingdom.equals(KingdomWorldApi.ownerAt(player.level(), player.blockPosition())) || !guard.hasLineOfSight(player)) {
                continue;
            }
            best = player;
            bestDistance = distance;
        }
        if (best == null) {
            return null;
        }
        long now = LawWork.now(server);
        Hail hail = HAILS.get(best.getUUID());
        if (hail == null || !hail.kingdom().equals(kingdom)) {
            hail(server, guard, best, kingdom, now);
            return new LawWork.Pursuit(best, false);
        }
        return new LawWork.Pursuit(best, now - hail.at() >= LawSettings.rules().hailTicks);
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
     * on the land of the kingdom.
     */
    static boolean pursues(MinecraftServer server, UUID kingdom, Player player) {
        Hail hail = HAILS.get(player.getUUID());
        if (hail == null || !hail.kingdom().equals(kingdom) || LawWork.isReadOnly(server)) {
            return false;
        }
        if (LawWork.now(server) - hail.at() < LawSettings.rules().hailTicks) {
            return false;
        }
        return LawWork.state(server).wanted(kingdom, player.getUUID()).isPresent()
                && kingdom.equals(KingdomWorldApi.ownerAt(player.level(), player.blockPosition()));
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
