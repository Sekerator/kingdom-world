package io.github.sekerator.kingdomworld.common.magic;

import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWorkGoal;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehavior;
import io.github.sekerator.kingdomworld.common.citizen.work.ZoneSurveys;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * A mage near players: walks to the nearest focus in the zone of its post and dissolves crystals there one after another,
 * as long as there are crystals and room in the store.
 */
final class MageGoal extends PostWorkGoal {
    private static final double FOCUS_REACH_SQR = 2.5 * 2.5;
    private static final int GIVE_UP_TICKS = 600;
    /** Crystals in one round of work before the mage looks around again. */
    private static final int ROUND = 8;

    @Nullable
    private BlockPos focus;
    private int timer;
    private int total;
    private int walking;
    private int dissolved;

    MageGoal(CitizenEntity body, ProfessionBehavior behavior) {
        super(body, behavior);
    }

    @Override
    protected boolean deliversToPost() {
        return false;
    }

    @Override
    protected void onStart() {
        focus = null;
        timer = 0;
        total = 0;
        walking = 0;
        dissolved = 0;
    }

    @Override
    @Nullable
    protected WorkStatus work(ServerLevel level) {
        MinecraftServer server = level.getServer();
        Optional<Citizen> citizen = citizen();
        if (citizen.isEmpty()) {
            return WorkStatus.UNKNOWN;
        }
        if (focus == null || !level.getBlockState(focus).is(MagicRegistries.MAGIC_FOCUS.get())) {
            focus = pickFocus(level);
            if (focus == null) {
                return MageBehavior.surveyed(post.pos()) ? WorkStatus.NO_WORKSTATION : WorkStatus.WAITING;
            }
        }
        if (!near(focus, FOCUS_REACH_SQR)) {
            moveTo(focus);
            if (++walking > GIVE_UP_TICKS) {
                focus = null;
                ZoneSurveys.request(post.pos());
                return WorkStatus.NO_WORKSTATION;
            }
            return null;
        }
        lookAt(focus);
        if (total <= 0) {
            total = MageBehavior.ticks(citizen.get());
            timer = 0;
        }
        timer++;
        post.setCurrent("kingdomworld:monster_crystal", timer / (double) Math.max(1, total));
        if (timer % 10 == 0) {
            body.swing(InteractionHand.MAIN_HAND);
            level.sendParticles(ParticleTypes.ENCHANT, focus.getX() + 0.5, focus.getY() + 1.2, focus.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.5);
        }
        if (timer < total) {
            return null;
        }
        total = 0;
        MageBehavior.Dissolved result = MageBehavior.dissolve(server, post, true);
        if (result != MageBehavior.Dissolved.DONE) {
            return MageBehavior.status(result);
        }
        level.playSound(null, focus, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 0.5f, 1.5f);
        level.sendParticles(ParticleTypes.END_ROD, focus.getX() + 0.5, focus.getY() + 1.3, focus.getZ() + 0.5, 12, 0.2, 0.4, 0.2, 0.05);
        citizen.get().addExperience(1);
        report(WorkStatus.WORKING);
        return ++dissolved >= ROUND ? WorkStatus.WORKING : null;
    }

    @Nullable
    private BlockPos pickFocus(ServerLevel level) {
        if (!MageBehavior.surveyed(post.pos())) {
            ZoneSurveys.request(post.pos());
            return null;
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : MageBehavior.knownFoci(post.pos())) {
            if (!level.isLoaded(pos) || !level.getBlockState(pos).is(MagicRegistries.MAGIC_FOCUS.get())) {
                continue;
            }
            double distance = body.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distance < bestDistance) {
                best = pos;
                bestDistance = distance;
            }
        }
        if (best == null) {
            ZoneSurveys.request(post.pos());
        }
        return best;
    }
}
