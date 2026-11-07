package io.github.sekerator.kingdomworld.common.law;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.companion.CompanionBodies;
import io.github.sekerator.kingdomworld.common.companion.CompanionEntity;
import io.github.sekerator.kingdomworld.common.companion.CompanionSettings;
import io.github.sekerator.kingdomworld.common.companion.CompanionTexts;
import io.github.sekerator.kingdomworld.common.companion.CompanionWork;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.core.companion.Companion;
import io.github.sekerator.kingdomworld.core.companion.CompanionState;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.law.LawRules;
import io.github.sekerator.kingdomworld.core.law.LawState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The ban of slavery. On the land of a kingdom that forbids slaves, the slave of a foreign owner is set free (a free
 * companion in the owner's service, grateful and loyal, as the companions stage frees slaves) and the owner pays a fine to
 * the treasury; the slaves of its own subjects are freed by the companions themselves. An owner with slaves hears once per
 * approach that the land ahead forbids them, so they may stay out. The slave market of such a kingdom is closed.
 */
final class LawSlavery {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The kingdom each owner was last warned of; forgotten when the owner is far again. */
    private static final Map<UUID, UUID> WARNED = new HashMap<>();

    private LawSlavery() {
    }

    static void reset() {
        WARNED.clear();
    }

    static void tick(MinecraftServer server, LawRules rules) {
        if (CompanionWork.isReadOnly(server)) {
            return;
        }
        LawState law = LawWork.state(server);
        if (law.laws().values().stream().noneMatch(k -> k.slaveryBanned())) {
            WARNED.clear();
            return;
        }
        freeForeignSlaves(server, law, rules);
        warnOwners(server, law, rules);
    }

    /**
     * Slaves with a body on the land of a kingdom that forbids them go free; their foreign owners pay.
     *
     * @return how many were set free
     */
    static int freeForeignSlaves(MinecraftServer server, LawState law, LawRules rules) {
        CompanionState companions = CompanionWork.state(server);
        long now = LawWork.now(server);
        long day = TreasuryActions.day(server);
        int freed = 0;
        for (Companion companion : List.copyOf(companions.companions())) {
            if (!companion.isSlave()) {
                continue;
            }
            Optional<CompanionEntity> body = CompanionBodies.body(companion.id());
            if (body.isEmpty()) {
                continue;
            }
            UUID land = KingdomWorldApi.ownerAt(body.get().level(), body.get().blockPosition());
            if (land == null || !law.slaveryBanned(land)
                    || KingdomWorldApi.state(server).membership().isMember(companion.owner(), land)) {
                continue;
            }
            Outcome done = CompanionWork.change(server, "free a slave by law",
                    state -> state.freeIntoService(companion.owner(), companion.id(), now, day, CompanionSettings.rules()));
            if (!done.ok()) {
                continue;
            }
            freed++;
            long fine = rules.slaveryFine;
            ServerPlayer owner = server.getPlayerList().getPlayer(companion.owner());
            if (fine > 0) {
                if (owner != null) {
                    LawPunish.collectFine(owner, land, fine);
                } else {
                    KingdomWorldApi.state(server).economy().addDebt(land, companion.owner(), fine);
                }
            }
            if (owner != null) {
                owner.sendSystemMessage(Component.translatable(CitizenTexts.gendered(LawWork.KEY + "slave_freed", companion.gender()),
                        LawWork.kingdomName(server, land, ""), CompanionTexts.name(companion), MoneyTexts.amount(fine, LawTexts.units(server, land)))
                        .withStyle(ChatFormatting.GOLD));
            }
            LOGGER.info("The law of {} set free the slave {} of {}; fine {}", land, companion.id(), companion.owner(), fine);
        }
        return freed;
    }

    /**
     * Owners of slaves near the land of a kingdom that forbids them hear it once per approach.
     */
    private static void warnOwners(MinecraftServer server, LawState law, LawRules rules) {
        CompanionState companions = CompanionWork.state(server);
        TerritoryIndex territory = KingdomWorldApi.state(server).territory();
        int radius = Math.max(0, (rules.slaveryWarnBlocks + 15) / 16);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player instanceof FakePlayer || companions.ownedBy(player.getUUID()).stream().noneMatch(Companion::isSlave)) {
                WARNED.remove(player.getUUID());
                continue;
            }
            String dimension = Dimensions.id(player.level());
            int cx = player.getBlockX() >> 4;
            int cz = player.getBlockZ() >> 4;
            UUID found = null;
            for (int dx = -radius; dx <= radius && found == null; dx++) {
                for (int dz = -radius; dz <= radius && found == null; dz++) {
                    UUID owner = territory.ownerOf(dimension, ChunkKey.of(cx + dx, cz + dz));
                    if (owner != null && law.slaveryBanned(owner) && !KingdomWorldApi.state(server).membership().isMember(player.getUUID(), owner)) {
                        found = owner;
                    }
                }
            }
            if (found == null) {
                WARNED.remove(player.getUUID());
                continue;
            }
            if (!found.equals(WARNED.get(player.getUUID()))) {
                WARNED.put(player.getUUID(), found);
                player.sendSystemMessage(Component.translatable(LawWork.KEY + "slavery_warning", LawWork.kingdomName(server, found, ""),
                        MoneyTexts.amount(rules.slaveryFine, LawTexts.units(server, found))).withStyle(ChatFormatting.GOLD));
            }
        }
    }
}
