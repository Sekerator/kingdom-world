package io.github.sekerator.kingdomworld.common.npc;

import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Where a ruler of the world holds a feast when its citizens are unhappy and its treasury can pay. Feasts belong to the life of
 * towns; until that part of the game registers a host, rulers hold none.
 */
public final class RulerFestivals {
    /**
     * Holds a feast in a kingdom, paid from its treasury like the feast of a king.
     */
    @FunctionalInterface
    public interface Host {
        Outcome hold(MinecraftServer server, UUID kingdomId, long budget);
    }

    private static Host host;

    private RulerFestivals() {
    }

    /**
     * Registers the host of feasts; {@code null} takes it away.
     */
    public static void setHost(@Nullable Host value) {
        host = value;
    }

    public static boolean available() {
        return host != null;
    }

    static Outcome hold(MinecraftServer server, UUID kingdomId, long budget) {
        Host current = host;
        return current == null ? Outcome.fail(KingdomError.UNKNOWN_KINGDOM) : current.hold(server, kingdomId, budget);
    }
}
