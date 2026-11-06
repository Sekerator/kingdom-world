package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * What a profession does in the world. A profession is a core {@link io.github.sekerator.kingdomworld.core.citizen.Profession}
 * (id and kind of workplace) plus one of these, registered in {@link ProfessionBehaviors}; new professions add
 * both without touching the rest of the citizen code.
 */
public interface ProfessionBehavior {

    /**
     * Whether a block is a workplace of this profession. Kingdom land is scanned for such blocks.
     */
    boolean isWorkplace(BlockState state);

    /**
     * The work of a body near players, run as a goal of the body while the citizen works; {@code null} if the
     * profession has nothing to do in the world.
     */
    @Nullable
    Goal createWork(CitizenEntity body);

    /**
     * One simulation step of the work of a citizen without a body, usually based on what {@link #survey} saw.
     */
    void workAbstractly(MinecraftServer server, Citizen citizen, int intervalTicks);

    /**
     * Looks at the citizen's workplace, which is loaded, and remembers what the abstract work is based on (for a
     * farmer: the number of plots and the crop).
     */
    void survey(ServerLevel level, Citizen citizen);
}
