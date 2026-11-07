package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * What a profession does in the world. A profession is a core {@link io.github.sekerator.kingdomworld.core.citizen.Profession}
 * (id and kind of workplace) plus one of these, registered in {@link ProfessionBehaviors}; new professions add
 * both without touching the rest of the citizen code.
 *
 * <p>Professions that work at work posts get most of their work far from players for free from {@link PostWork}:
 * they say which tool they need, how their zone is estimated and what their accidents are called, and gather by
 * the rates measured while players watched.
 */
public interface ProfessionBehavior {

    /**
     * Whether a block is a workplace of this profession of its own (a composter for a farmer); work posts are
     * workplaces of every profession that works at posts. Kingdom land is scanned for such blocks.
     */
    boolean isWorkplace(BlockState state);

    /**
     * The work of a body near players, run as a goal of the body while the citizen works; {@code null} if the
     * profession has nothing to do in the world.
     */
    @Nullable
    Goal createWork(CitizenEntity body);

    /**
     * One simulation step of the work of a citizen without a body.
     */
    void workAbstractly(MinecraftServer server, Citizen citizen, int intervalTicks);

    /**
     * Looks at the citizen's workplace, which is loaded, and remembers what the abstract work is based on (for a
     * farmer at a composter: the number of plots and the crop).
     */
    void survey(ServerLevel level, Citizen citizen);

    // ---------------------------------------------------------------- work at posts

    /**
     * The tool the work needs in the main hand.
     */
    default Equipment.ToolNeed tool() {
        return Equipment.ToolNeed.NONE;
    }

    /**
     * A second tool kept in the other hand, never required (shears of a herder).
     */
    default Equipment.ToolNeed secondTool() {
        return Equipment.ToolNeed.NONE;
    }

    /**
     * Whether the work is dangerous enough to put on armor from the storehouses.
     */
    default boolean risky() {
        return false;
    }

    /**
     * Whether citizens of the profession stand against monsters: they neither flee from them nor go home at night.
     */
    default boolean fights() {
        return false;
    }

    /**
     * How the chronicle names a deadly accident of this work far from players.
     */
    default CauseOfDeath accidentCause() {
        return CauseOfDeath.WORK;
    }

    /**
     * A look over the zone of a post that estimates what one worker gathers there in a day, until the work is
     * measured; {@code null} if the profession gathers nothing by itself (carriers).
     */
    @Nullable
    default ZoneSurvey surveyZone(ServerLevel level, WorkPost post) {
        return null;
    }

    /**
     * Items the post wants in its container, by item id with the count it wants there; carriers bring them from
     * the storehouses. The production chains of later professions take their inputs this way.
     *
     * @param container what the container of the post holds now, by item id
     */
    default Map<String, Integer> wanted(WorkPost post, Map<String, Integer> container) {
        return Map.of();
    }

    /**
     * Whether an item in the post's container is an input of the work, so that carriers leave it there.
     */
    default boolean isInput(ItemStack stack) {
        return false;
    }

    /**
     * Whether an item in the container of a particular post is an input of its work; by default the same for every
     * post of the profession.
     */
    default boolean isInput(WorkPost post, ItemStack stack) {
        return isInput(stack);
    }

    /**
     * Whether the zone of a post is looked at again from time to time even after its work was measured, because
     * what stands there matters to the work (the workstations of a workshop).
     */
    default boolean resurveys() {
        return false;
    }
}
