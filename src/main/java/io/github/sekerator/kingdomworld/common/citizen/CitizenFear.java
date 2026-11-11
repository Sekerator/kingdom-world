package io.github.sekerator.kingdomworld.common.citizen;

import io.github.sekerator.kingdomworld.common.law.LawSettings;
import io.github.sekerator.kingdomworld.core.citizen.Flight;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.law.LawRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The fright of citizens: a blow at a citizen or a soldier frightens the one struck, everybody who sees it and everybody
 * who hears it close by, and each of those frightens the bodies right around it once. The frightened run until some time
 * after the last blow ({@link io.github.sekerator.kingdomworld.common.citizen.goal.FearGoal}). Soldiers and adventurers
 * never run. The fright is a reaction of the body and asks no law: a king who beats his own people and an enemy in war
 * frighten as a robber does, and so does a player in creative mode.
 *
 * <p>The look around a blow is taken at most once in {@value #SCAN_TICKS} ticks for one attacker, and the bodies a runner
 * frightens do not frighten further, so a massacre costs a bounded number of searches.
 */
public final class CitizenFear {
    /** Ticks between two looks around the blows of one attacker. */
    static final int SCAN_TICKS = 10;

    private static final Map<UUID, Long> LAST_SCAN = new HashMap<>();

    private CitizenFear() {
    }

    static void reset() {
        LAST_SCAN.clear();
    }

    /**
     * Whether a body runs when frightened: every citizen with a record but soldiers and adventurers, who fight.
     */
    public static boolean flees(CitizenEntity body) {
        return body.citizenId() != null
                && !io.github.sekerator.kingdomworld.common.life.LifeWork.keepsWatch(body.professionId());
    }

    /**
     * Whether a blow of this attacker at a body frightens: a player in any game mode but a spectator, a monster, a soldier of
     * another kingdom. Machines of mods frighten nobody.
     */
    public static boolean frightens(@Nullable Entity attacker, CitizenEntity victim) {
        if (attacker instanceof Player player) {
            return !(player instanceof FakePlayer) && !player.isSpectator();
        }
        if (attacker instanceof CitizenEntity soldier) {
            return soldier.kingdomId() != null && !soldier.kingdomId().equals(victim.kingdomId()) && Professions.isMilitary(soldier.professionId());
        }
        return attacker instanceof Mob && attacker instanceof Enemy;
    }

    static void onHurt(LivingHurtEvent event) {
        if (event.getAmount() > 0 && event.getEntity() instanceof CitizenEntity victim && !victim.level().isClientSide) {
            violence(victim, event.getSource().getEntity());
        }
    }

    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof CitizenEntity victim && !victim.level().isClientSide) {
            Entity killer = event.getSource().getEntity();
            violence(victim, killer != null ? killer : victim.getKillCredit());
        }
    }

    /**
     * Somebody struck or killed a citizen or a soldier: the one struck is frightened, and so is everybody who sees or hears it.
     */
    public static void violence(CitizenEntity victim, @Nullable Entity attacker) {
        if (attacker == null || victim.kingdomId() == null || !(victim.level() instanceof ServerLevel level) || !frightens(attacker, victim)) {
            return;
        }
        LawRules rules = LawSettings.rules();
        if (rules.fearTicks <= 0) {
            return;
        }
        if (victim.isAlive() && flees(victim)) {
            victim.frighten(attacker, rules.fearTicks, true);
        }
        long now = level.getGameTime();
        Long last = LAST_SCAN.get(attacker.getUUID());
        if (last != null && now - last < SCAN_TICKS && now >= last) {
            return;
        }
        if (LAST_SCAN.size() > 256) {
            LAST_SCAN.values().removeIf(at -> now - at > 200 || now < at);
        }
        LAST_SCAN.put(attacker.getUUID(), now);
        double sight = rules.fearSightRadius;
        double hear = rules.fearHearRadius;
        double reach = Math.max(sight, hear);
        for (CitizenEntity body : level.getEntitiesOfClass(CitizenEntity.class, victim.getBoundingBox().inflate(reach),
                body -> body != victim && body.isAlive() && flees(body))) {
            double distance = body.distanceToSqr(victim);
            boolean hears = distance <= hear * hear;
            // A sleeper sees nothing; the noise close by wakes it.
            boolean sees = !hears && !body.isSleeping() && distance <= sight * sight
                    && (body.hasLineOfSight(victim) || body.hasLineOfSight(attacker));
            if (hears || sees) {
                body.frighten(attacker, rules.fearTicks, true);
            }
        }
    }

    /**
     * A body that just began to run frightens the calm ones right around it, who do not frighten further.
     */
    public static void spread(CitizenEntity runner) {
        UUID threatId = runner.threatId();
        if (threatId == null || !(runner.level() instanceof ServerLevel level)) {
            return;
        }
        Entity threat = level.getEntity(threatId);
        LawRules rules = LawSettings.rules();
        if (threat == null || rules.fearSpreadRadius <= 0) {
            return;
        }
        for (CitizenEntity body : level.getEntitiesOfClass(CitizenEntity.class, runner.getBoundingBox().inflate(rules.fearSpreadRadius),
                body -> body != runner && body.isAlive() && !body.isFrightened() && !body.isSleeping() && flees(body))) {
            body.frighten(threat, rules.fearTicks, false);
        }
    }

    /**
     * A witness of a crime is frightened and, when a soldier of its kingdom stands within reach and not behind the offender,
     * runs to that soldier rather than home.
     *
     * @return whether the witness runs to a soldier
     */
    public static boolean runToGuard(CitizenEntity witness, Entity offender, List<CitizenEntity> soldiers) {
        LawRules rules = LawSettings.rules();
        if (!flees(witness) || rules.fearTicks <= 0) {
            return false;
        }
        witness.frighten(offender, rules.fearTicks, true);
        CitizenEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (CitizenEntity soldier : soldiers) {
            double distance = soldier.distanceToSqr(witness);
            if (distance < best && soldier.isAlive() && Flight.leadsAway(witness.getX(), witness.getZ(), offender.getX(), offender.getZ(),
                    soldier.getX(), soldier.getZ())) {
                best = distance;
                nearest = soldier;
            }
        }
        if (nearest == null) {
            return false;
        }
        witness.setFearGuard(nearest.getUUID());
        return true;
    }

    /**
     * Whether a living thing is a citizen body that is frightened now, for checks.
     */
    public static boolean isFrightened(LivingEntity entity) {
        return entity instanceof CitizenEntity body && body.isFrightened();
    }
}
