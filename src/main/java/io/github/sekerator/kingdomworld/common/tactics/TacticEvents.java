package io.github.sekerator.kingdomworld.common.tactics;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.slf4j.Logger;

/**
 * The tactics of squads in the server: their leaders decide at the end of every tick, and a blow of a fighter of a squad
 * from the side or the back of its target lands harder.
 */
public final class TacticEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean failed;

    private TacticEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(TacticEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingHurtEvent.class, TacticEvents::onHurt);
        MinecraftForge.EVENT_BUS.addListener(TacticEvents::onServerStopped);
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        long started = System.nanoTime();
        try {
            SquadTactics.tick(event.getServer());
            failed = false;
        } catch (RuntimeException e) {
            if (!failed) {
                LOGGER.error("KingdomWorld tactics: the squads could not decide", e);
                failed = true;
            }
        }
        io.github.sekerator.kingdomworld.common.command.PerfCommands.countWork(System.nanoTime() - started);
    }

    /**
     * A blow by hand of a fighter of a squad that fights by a plan lands harder from the side and from the back of its
     * target, as in the battles far from players.
     */
    private static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || event.getAmount() <= 0) {
            return;
        }
        Entity source = event.getSource().getEntity();
        if (!(source instanceof Mob striker) || event.getSource().getDirectEntity() instanceof Projectile || !SquadTactics.fightsByPlan(striker)) {
            return;
        }
        double multiplier = TacticSettings.rules().sideMultiplier(sideAngle(victim, striker));
        if (multiplier != 1.0) {
            event.setAmount((float) (event.getAmount() * multiplier));
        }
    }

    /**
     * The angle, in degrees, between where a struck one looks and the way to its striker: 0 in front, 180 behind.
     */
    static double sideAngle(LivingEntity victim, Entity striker) {
        double yaw = Math.toRadians(victim.getYHeadRot());
        double lookX = -Math.sin(yaw);
        double lookZ = Math.cos(yaw);
        double dx = striker.getX() - victim.getX();
        double dz = striker.getZ() - victim.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1e-6) {
            return 0.0;
        }
        double cos = Math.max(-1.0, Math.min(1.0, (lookX * dx + lookZ * dz) / length));
        return Math.toDegrees(Math.acos(cos));
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        SquadTactics.reset();
        TacticSettings.reset();
    }
}
