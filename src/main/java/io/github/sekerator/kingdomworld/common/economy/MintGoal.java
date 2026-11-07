package io.github.sekerator.kingdomworld.common.economy;

import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWorkGoal;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehavior;
import io.github.sekerator.kingdomworld.common.citizen.work.ZoneSurveys;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * A minter near players: walks to the nearest mint in the zone of its post and strikes coins there, one item of metal
 * of the storehouses after another, as long as there is metal.
 */
final class MintGoal extends PostWorkGoal {
    private static final double MINT_REACH_SQR = 2.5 * 2.5;
    private static final int GIVE_UP_TICKS = 600;
    /** Strikings in one round of work before the minter looks around again. */
    private static final int ROUND = 8;

    @Nullable
    private BlockPos mint;
    @Nullable
    private ItemStack metal;
    private int timer;
    private int total;
    private int walking;
    private int struck;

    MintGoal(CitizenEntity body, ProfessionBehavior behavior) {
        super(body, behavior);
    }

    @Override
    protected boolean deliversToPost() {
        return false;
    }

    @Override
    protected void onStart() {
        mint = null;
        metal = null;
        timer = 0;
        walking = 0;
        struck = 0;
    }

    @Override
    @Nullable
    protected WorkStatus work(ServerLevel level) {
        MinecraftServer server = level.getServer();
        Optional<Citizen> citizen = citizen();
        if (citizen.isEmpty()) {
            return WorkStatus.UNKNOWN;
        }
        if (mint == null || !level.getBlockState(mint).is(ModRegistries.MINT.get())) {
            mint = pickMint(level);
            if (mint == null) {
                return MinterBehavior.surveyed(post.pos()) ? WorkStatus.NO_WORKSTATION : WorkStatus.WAITING;
            }
        }
        if (metal == null) {
            metal = MinterBehavior.nextMetal(server, post.kingdomId());
            if (metal == null) {
                return WorkStatus.NO_INGREDIENTS;
            }
            timer = 0;
            total = MinterBehavior.ticks(citizen.get());
        }
        if (!near(mint, MINT_REACH_SQR)) {
            moveTo(mint);
            if (++walking > GIVE_UP_TICKS) {
                mint = null;
                ZoneSurveys.request(post.pos());
                return WorkStatus.NO_WORKSTATION;
            }
            return null;
        }
        lookAt(mint);
        timer++;
        post.setCurrent(KingdomStorage.itemId(metal), timer / (double) Math.max(1, total));
        if (timer % 20 == 0) {
            body.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, mint, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.15f, 1.6f);
        }
        if (timer < total) {
            return null;
        }
        MinterBehavior.Struck result = MinterBehavior.strike(server, post, true);
        metal = null;
        if (result != MinterBehavior.Struck.DONE) {
            return MinterBehavior.status(result);
        }
        citizen.get().addExperience(1);
        report(WorkStatus.WORKING);
        return ++struck >= ROUND ? WorkStatus.WORKING : null;
    }

    @Nullable
    private BlockPos pickMint(ServerLevel level) {
        if (!MinterBehavior.surveyed(post.pos())) {
            ZoneSurveys.request(post.pos());
            return null;
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : MinterBehavior.knownMints(post.pos())) {
            if (!level.isLoaded(pos) || !level.getBlockState(pos).is(ModRegistries.MINT.get())) {
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
