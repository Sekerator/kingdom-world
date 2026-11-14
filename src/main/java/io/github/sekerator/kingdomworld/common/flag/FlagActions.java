package io.github.sekerator.kingdomworld.common.flag;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomStyles;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomFlag;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomFlags;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.KingdomMembers;
import io.github.sekerator.kingdomworld.core.membership.Member;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The king changes the flag of a kingdom of players. It costs nothing and takes effect at once: every flag of the kingdom in the
 * world shows the new one, no block changes.
 */
public final class FlagActions {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "kingdomworld.flag.";

    private FlagActions() {
    }

    /**
     * Gives a kingdom of players another flag. Only its king may, never for a kingdom of the world, and only with patterns the
     * game knows; the flag of the colour of the kingdom is kept as no flag of its own.
     *
     * @param actor the player who asks, or {@code null} for an operator
     */
    public static KingdomActions.Feedback change(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, KingdomFlag flag) {
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdom(server, kingdomId);
        if (kingdom.isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_KINGDOM);
        }
        if (KingdomWorldApi.isReadOnly(server)) {
            return KingdomActions.Feedback.error(KingdomError.READ_ONLY);
        }
        if (NpcWork.isNpc(server, kingdomId) || actor != null && !KingdomStyles.isKing(server, actor, kingdomId)) {
            return KingdomActions.Feedback.error(KingdomError.NO_PERMISSION);
        }
        for (KingdomFlag.Layer layer : flag.layers()) {
            if (!FlagPatterns.valid(layer.pattern())) {
                return KingdomActions.Feedback.fail(KEY + "unknown_pattern", layer.pattern());
            }
        }
        Component name = KingdomActions.kingdomName(kingdom.get());
        KingdomFlag kept = KingdomFlags.normalized(kingdom.get(), flag);
        if (Objects.equals(kept, kingdom.get().flag())) {
            return KingdomActions.Feedback.ok(KEY + "changed", name);
        }
        Outcome changed = KingdomWorldApi.change(server, "change the flag of " + kingdom.get().name(),
                state -> state.setKingdomFlag(kingdomId, kept) ? Outcome.DONE : Outcome.fail(KingdomError.UNKNOWN_KINGDOM));
        if (!changed.ok()) {
            return KingdomActions.Feedback.error(changed.error());
        }
        LOGGER.info("{} flies a new flag: {}", kingdom.get().name(), kept == null ? "the flag of its colour" : kept);
        FlagDirectory.pushNow(server);
        KingdomActions.Feedback feedback = KingdomActions.Feedback.ok(KEY + "changed", name);
        KingdomWorldApi.state(server).membership().kingdom(kingdomId).map(KingdomMembers::members).ifPresent(members -> {
            for (Member member : members.values()) {
                if (!member.playerId().equals(actor)) {
                    KingdomActions.tell(server, member.playerId(), feedback.message());
                }
            }
        });
        return feedback;
    }
}
