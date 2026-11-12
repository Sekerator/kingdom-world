package io.github.sekerator.kingdomworld.common.kingdom;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * The architectural style of a kingdom on the server: the one it chose or was given, the one the land of a throne suggests,
 * changing it on the throne for a fee, and the material of its walls.
 */
public final class KingdomStyles {
    private KingdomStyles() {
    }

    /**
     * The style of a kingdom: the one it keeps, that of the place of a kingdom of the world, or the one its land suggests.
     */
    public static String of(MinecraftServer server, UUID kingdomId) {
        return "";
    }

    /**
     * The style the land around a throne suggests, by its climate and mountains.
     */
    public static String suggested(ServerLevel level, BlockPos throne) {
        return "";
    }

    /**
     * What changing the style of a kingdom costs its treasury.
     */
    public static long changeCost() {
        return KingdomWorldConfig.SERVER.styleChangeCost.get();
    }

    /**
     * Changes the style of a kingdom of players for the fee: only its king may, and only to another style.
     *
     * @param actor the player who asks, or {@code null}
     */
    public static KingdomActions.Feedback change(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, String style) {
        return KingdomActions.Feedback.error(KingdomError.NO_PERMISSION);
    }

    /**
     * The item the walls of a kingdom are built of in its style, if the style has one.
     *
     * @param palisade whether the wall is a palisade of logs rather than of stone
     */
    public static Optional<String> wallItem(MinecraftServer server, UUID kingdomId, boolean palisade) {
        return Optional.empty();
    }
}
