package io.github.sekerator.kingdomworld.common.npc;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.core.npc.NpcBuildRules;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Decides which kingdom of the world has its buildings raised in the world, once a second. A player coming near a place that
 * is not raised yet makes it go first, at the full budget, so that the player sees it being built; in the background mode the
 * places founded as data are raised one after another with a small budget while players are on the server and ticks are short.
 */
final class NpcBackground {
    private static final Logger LOGGER = LogUtils.getLogger();

    private NpcBackground() {
    }

    /**
     * Whether a background raising waits now: nobody is on the server or its ticks are long.
     */
    static boolean waiting(MinecraftServer server) {
        return waiting(server, false);
    }

    /**
     * @param forced whether an operator lets it go on without players
     */
    static boolean waiting(MinecraftServer server, boolean forced) {
        return !forced && !playersOnline(server) || server.getAverageTickTime() > NpcSettings.build().pauseTickMillis();
    }

    private static boolean playersOnline(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player instanceof FakePlayer)) {
                return true;
            }
        }
        return false;
    }

    static void tick(MinecraftServer server) {
        if (!NpcSettings.rules().generate()) {
            return;
        }
        NpcState state = NpcWork.state(server);
        NpcBuildRules rules = NpcSettings.build();
        Optional<NpcSite> spawning = state.spawning();
        Optional<NpcSite> near = nearest(server, state, true);
        if (spawning.isPresent()) {
            NpcSite site = spawning.get();
            if (near.isPresent() && near.get().id() != site.id() && NpcSpawner.inBackground() && !watched(server, site)) {
                // A player came to another place: the one nobody watches waits, the other goes first.
                LOGGER.info("A player came near the place of a kingdom of the world: {}; {} waits", near.get(), site);
                NpcSpawner.putAside(server, site);
                NpcSpawner.start(server, near.get(), false);
                return;
            }
            boolean watched = watched(server, site);
            if (watched && NpcSpawner.inBackground()) {
                NpcSpawner.setBackground(false);
            } else if (!watched && !NpcSpawner.inBackground() && rules.background() && playersOnline(server)) {
                // The full budget gives way to the small one only while players are online and none of them watches:
                // on an empty server no tick of a player suffers, and the small budget would wait for players forever.
                NpcSpawner.setBackground(true);
            }
            return;
        }
        if (near.isPresent()) {
            LOGGER.info("A player came near the place of a kingdom of the world: {}", near.get());
            NpcSpawner.start(server, near.get(), false);
            return;
        }
        if (rules.background() && !waiting(server)) {
            nearest(server, state, false).ifPresent(site -> {
                LOGGER.info("The buildings of the kingdom of the world of {} are raised in the background", site);
                NpcSpawner.start(server, site, true);
            });
        }
    }

    /**
     * Whether a player is near a place, within the distance at which places are raised at once.
     */
    private static boolean watched(MinecraftServer server, NpcSite site) {
        double reach = NpcSettings.rules().triggerBlocks();
        for (ServerPlayer player : server.overworld().players()) {
            if (player.isSpectator() || player instanceof FakePlayer) {
                continue;
            }
            double dx = player.getX() - site.x();
            double dz = player.getZ() - site.z();
            if (dx * dx + dz * dz <= reach * reach) {
                return true;
            }
        }
        return false;
    }

    /**
     * The place still to be raised that is nearest to a player.
     *
     * @param close whether only a place within the distance of raising at once counts; otherwise only places founded as data,
     *              the large kingdoms before the small ones
     */
    private static Optional<NpcSite> nearest(MinecraftServer server, NpcState state, boolean close) {
        ServerLevel overworld = server.overworld();
        double reach = NpcSettings.rules().triggerBlocks();
        NpcSite best = null;
        double bestDistance = Double.MAX_VALUE;
        for (NpcSite site : state.sites()) {
            boolean waiting = site.status() == NpcSite.Status.FOUNDED || close && site.status() == NpcSite.Status.PLANNED;
            if (!waiting) {
                continue;
            }
            double distance = Double.MAX_VALUE;
            for (ServerPlayer player : overworld.players()) {
                if (player.isSpectator() || player instanceof FakePlayer) {
                    continue;
                }
                double dx = player.getX() - site.x();
                double dz = player.getZ() - site.z();
                distance = Math.min(distance, dx * dx + dz * dz);
            }
            if (close && distance > reach * reach) {
                continue;
            }
            // In the background the large kingdoms go before the small ones; among equals the nearest to a player.
            boolean larger = !close && best != null && site.level().ordinal() != best.level().ordinal();
            if (best == null || (larger ? site.level().ordinal() > best.level().ordinal() : distance < bestDistance)) {
                best = site;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }
}
