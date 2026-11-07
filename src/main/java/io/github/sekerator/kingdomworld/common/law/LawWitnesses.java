package io.github.sekerator.kingdomworld.common.law;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.core.law.LawRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Who saw a crime: citizens, soldiers and subjects of the kingdom within {@link LawRules#witnessRadius} blocks who see the
 * offender (a line of sight from their eyes). Subjects away from the keyboard see nothing; the victim is no witness.
 */
final class LawWitnesses {

    /**
     * The witnesses of a crime.
     *
     * @param soldier  whether a soldier saw it: soldiers report at once
     * @param citizens citizen ids of the witnesses among the citizens
     * @param players  ids of the subjects who saw it
     * @param bodies   the bodies of the citizens, who cry out and run to the guards
     */
    record Seen(boolean soldier, List<UUID> citizens, List<UUID> players, List<CitizenEntity> bodies) {
        boolean none() {
            return !soldier && citizens.isEmpty() && players.isEmpty();
        }
    }

    /** Blows this close wake sleeping citizens, in blocks. */
    static final double WAKE_RADIUS = 8.0;

    private LawWitnesses() {
    }

    static Seen find(ServerLevel level, ServerPlayer offender, UUID kingdomId, @Nullable Entity victim, LawRules rules) {
        double radius = rules.witnessRadius;
        boolean soldier = false;
        List<UUID> citizens = new ArrayList<>();
        List<CitizenEntity> bodies = new ArrayList<>();
        for (CitizenEntity body : level.getEntitiesOfClass(CitizenEntity.class, offender.getBoundingBox().inflate(radius),
                body -> body.isAlive() && body != victim && kingdomId.equals(body.kingdomId()) && body.citizenId() != null)) {
            if (body.isSleeping()) {
                // A sleeper sees nothing; the noise of blows close by wakes it (and it may then see the offender), a theft does not.
                if (victim == null || body.distanceToSqr(offender) > WAKE_RADIUS * WAKE_RADIUS) {
                    continue;
                }
                body.stopSleeping();
            }
            // At night a theft is seen only from closer by, except by soldiers, who keep watch.
            double sight = victim == null && !LawWork.isSoldier(body) && !level.isDay() ? radius / 2 : radius;
            if (body.distanceToSqr(offender) > sight * sight || !body.hasLineOfSight(offender)) {
                continue;
            }
            if (LawWork.isSoldier(body)) {
                soldier = true;
            } else {
                citizens.add(body.citizenId());
                bodies.add(body);
            }
        }
        List<UUID> players = new ArrayList<>();
        var membership = KingdomWorldApi.state(level.getServer()).membership();
        for (ServerPlayer player : level.players()) {
            if (player == offender || player == victim || player instanceof FakePlayer || !player.isAlive() || player.isSpectator()
                    || !membership.isMember(player.getUUID(), kingdomId) || LawWork.isAway(player)
                    || player.distanceToSqr(offender) > radius * radius || !player.hasLineOfSight(offender)) {
                continue;
            }
            players.add(player.getUUID());
        }
        return new Seen(soldier, citizens, players, bodies);
    }

    /**
     * A citizen who saw a crime runs to the nearest soldier of its kingdom.
     */
    static void runToGuards(CitizenEntity body) {
        if (body.kingdomId() == null || !(body.level() instanceof ServerLevel level)) {
            return;
        }
        CitizenEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (CitizenEntity soldier : LawWork.soldiersNear(level, body.blockPosition(), body.kingdomId(), 48.0)) {
            double distance = soldier.distanceToSqr(body);
            if (distance < best) {
                best = distance;
                nearest = soldier;
            }
        }
        if (nearest != null) {
            body.getNavigation().moveTo(nearest, 0.9);
        }
    }
}
