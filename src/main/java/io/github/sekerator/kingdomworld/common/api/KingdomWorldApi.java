package io.github.sekerator.kingdomworld.common.api;

import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomColors;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomRegistry;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side entry point for reading and changing kingdoms. Commands, network handlers and other mods
 * should go through this class instead of touching saved data directly.
 *
 * <p>Every method must be called on the server thread; calls from other threads fail fast.
 */
public final class KingdomWorldApi {

    private KingdomWorldApi() {
    }

    public static List<Kingdom> kingdoms(MinecraftServer server) {
        return registry(server).all();
    }

    public static Optional<Kingdom> kingdom(MinecraftServer server, UUID id) {
        return registry(server).get(id);
    }

    public static Optional<Kingdom> findKingdom(MinecraftServer server, String name) {
        return registry(server).findByName(name);
    }

    /**
     * Founds a kingdom with a default color at the current overworld game time.
     *
     * @param rulerId the ruler, or {@code null} for a kingdom without a ruler
     */
    public static CreateKingdomResult createKingdom(MinecraftServer server, String name, @Nullable UUID rulerId) {
        KingdomRegistry registry = registry(server);
        UUID id = UUID.randomUUID();
        while (registry.get(id).isPresent()) {
            id = UUID.randomUUID();
        }
        long gameTime = server.overworld().getGameTime();
        return registry.create(id, name, KingdomColors.defaultFor(id), rulerId, gameTime);
    }

    public static Optional<Kingdom> removeKingdom(MinecraftServer server, UUID id) {
        return registry(server).remove(id);
    }

    private static KingdomRegistry registry(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("KingdomWorld state must only be accessed on the server thread, not "
                    + Thread.currentThread().getName());
        }
        return KingdomSavedData.get(server).registry();
    }
}
