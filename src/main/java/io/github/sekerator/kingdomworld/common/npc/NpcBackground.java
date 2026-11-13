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
        Optional<io.github.sekerator.kingdomworld.core.npc.Settlement> nearSettlement = nearestSettlement(server, state, true);
        if (spawning.isPresent()) {
            NpcSite site = spawning.get();
            if (near.isEmpty() && nearSettlement.isPresent() && NpcSpawner.inBackground() && !watched(server, site)) {
                // One place rises at a time: a settlement a player came to goes before a capital nobody watches.
                LOGGER.info("A player came near {}; {} waits", nearSettlement.get(), site);
                NpcSpawner.putAside(server, site);
                SettlementRaiser.start(server, nearSettlement.get(), false);
                return;
            }
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
        Optional<io.github.sekerator.kingdomworld.core.npc.Settlement> raising = SettlementRaiser.raising(state);
        if (near.isPresent()) {
            LOGGER.info("A player came near the place of a kingdom of the world: {}", near.get());
            raising.ifPresent(settlement -> SettlementRaiser.putAside(server, settlement));
            NpcSpawner.start(server, near.get(), false);
            return;
        }
        if (nearSettlement.isPresent()) {
            io.github.sekerator.kingdomworld.core.npc.Settlement settlement = nearSettlement.get();
            if (raising.isEmpty() || raising.get().id() != settlement.id()) {
                if (raising.isEmpty() || SettlementRaiser.inBackground() || !watched(server, raising.get())) {
                    LOGGER.info("A player came near {}", settlement);
                    SettlementRaiser.start(server, settlement, false);
                }
            } else if (SettlementRaiser.inBackground()) {
                SettlementRaiser.setBackground(false);
            }
            return;
        }
        if (raising.isPresent()) {
            // A settlement raised for a player who went away goes on with the small budget while players are online.
            if (!SettlementRaiser.inBackground() && !watched(server, raising.get()) && rules.background() && playersOnline(server)) {
                SettlementRaiser.setBackground(true);
            }
            return;
        }
        if (rules.background() && !waiting(server)) {
            Optional<NpcSite> capital = nearest(server, state, false);
            if (capital.isPresent()) {
                LOGGER.info("The buildings of the kingdom of the world of {} are raised in the background", capital.get());
                NpcSpawner.start(server, capital.get(), true);
                return;
            }
            // When every capital stands, the large cities and then the small towns near players, as the config names them.
            nearestSettlement(server, state, false).ifPresent(settlement -> {
                LOGGER.info("{} is raised in the background", settlement);
                SettlementRaiser.start(server, settlement, true);
            });
        }
    }

    /**
     * Whether a player is within the distance at which a settlement rises at once.
     */
    private static boolean watched(MinecraftServer server, io.github.sekerator.kingdomworld.core.npc.Settlement settlement) {
        double reach = SettlementSettings.triggerChunks(settlement.tier()) * 16.0;
        for (ServerPlayer player : server.overworld().players()) {
            if (player.isSpectator() || player instanceof FakePlayer) {
                continue;
            }
            double dx = player.getX() - settlement.x();
            double dz = player.getZ() - settlement.z();
            if (dx * dx + dz * dz <= reach * reach) {
                return true;
            }
        }
        return false;
    }

    /**
     * The settlement still to be raised that is nearest to a player.
     *
     * @param close whether only one within the distance of its rank at which it rises at once counts; otherwise one of the ranks
     *              raised in the background within the distance of the background, the larger ranks first
     */
    static Optional<io.github.sekerator.kingdomworld.core.npc.Settlement> nearestSettlement(MinecraftServer server, NpcState state, boolean close) {
        if (state.settlements().isEmpty()) {
            return Optional.empty();
        }
        int reach = close ? 16 * 16 : SettlementSettings.backgroundBlocks();
        // A settlement of a kingdom of players, or one the journal of conquests is handing over, is not raised.
        java.util.function.Predicate<io.github.sekerator.kingdomworld.core.npc.Settlement> handingOver =
                io.github.sekerator.kingdomworld.common.siege.SiegeWork.handingOver(server);
        io.github.sekerator.kingdomworld.core.npc.Settlement best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer player : server.overworld().players()) {
            if (player.isSpectator() || player instanceof FakePlayer) {
                continue;
            }
            for (io.github.sekerator.kingdomworld.core.npc.Settlement settlement : SettlementIndex.near(server, (int) player.getX(), (int) player.getZ(), reach)) {
                if (!SettlementRaiser.waiting(settlement) || state.kingdom(settlement.kingdomId()).isEmpty() || handingOver.test(settlement)) {
                    continue;
                }
                double dx = player.getX() - settlement.x();
                double dz = player.getZ() - settlement.z();
                double distance = dx * dx + dz * dz;
                if (close) {
                    double trigger = SettlementSettings.triggerChunks(settlement.tier()) * 16.0;
                    if (distance > trigger * trigger) {
                        continue;
                    }
                } else if (!SettlementSettings.background(settlement.tier())) {
                    continue;
                }
                boolean higher = best != null && settlement.tier().ordinal() < best.tier().ordinal();
                boolean same = best != null && settlement.tier() == best.tier();
                if (best == null || (!close && higher) || ((close || same) && distance < bestDistance)) {
                    best = settlement;
                    bestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(best);
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
