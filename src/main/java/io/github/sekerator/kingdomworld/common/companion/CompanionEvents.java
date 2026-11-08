package io.github.sekerator.kingdomworld.common.companion;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.kingdom.PlayerNames;
import io.github.sekerator.kingdomworld.core.companion.CompanionState;
import io.github.sekerator.kingdomworld.core.companion.Party;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

/**
 * Wires companions and parties into the game: once a second the companions live and the bodies follow their owners; a
 * player who leaves takes the bodies of the companions along (the records stay); bodies made elsewhere are refused;
 * companions never hurt players, citizens or other companions, not even by a stray arrow, and nobody but the owner and a
 * party that allows it hurts a companion; members of a party who turned friendly fire off do not hurt each other or each
 * other's companions; kills of companions give them experience.
 */
public final class CompanionEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CompanionEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(CompanionEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(CompanionEvents::onLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, EntityJoinLevelEvent.class, CompanionBodies::onJoin);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, LivingAttackEvent.class, CompanionEvents::onAttack);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDeathEvent.class, CompanionEvents::onDeath);
        MinecraftForge.EVENT_BUS.addListener(CompanionEvents::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(CompanionEvents::onServerStopped);
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CompanionWork.CHECK_TICKS != 0) {
            return;
        }
        CompanionSettings.refresh();
        try {
            CompanionWork.tick(server);
        } catch (RuntimeException e) {
            LOGGER.error("KingdomWorld companions tick failed", e);
        }
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            CompanionBodies.despawnAll(player.server, player.getUUID());
        }
    }

    /**
     * The player behind an entity: a player, or the owner of a companion.
     */
    @Nullable
    static UUID side(@Nullable Entity entity) {
        if (entity instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            return player.getUUID();
        }
        if (entity instanceof CompanionEntity companion) {
            return companion.owner();
        }
        return null;
    }

    private static void onAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        MinecraftServer server = victim.getServer();
        if (victim.level().isClientSide() || server == null) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof CompanionEntity && (victim instanceof Player || victim instanceof CitizenEntity || victim instanceof CompanionEntity)) {
            // Companions hurt no player, citizen or companion, not even by a stray arrow.
            event.setCanceled(true);
            return;
        }
        UUID from = side(attacker);
        UUID to = side(victim);
        if (from == null || to == null) {
            return;
        }
        CompanionState state = CompanionWork.state(server);
        Optional<Party> party = state.parties().partyOf(from);
        boolean together = party.isPresent() && party.get().has(to);
        if (together && !party.get().friendlyFire() && attacker != victim) {
            // Fire between members is off; one's own dynamite or arrow still hurts.
            event.setCanceled(true);
            return;
        }
        if (victim instanceof CompanionEntity companion && !from.equals(to) && !together) {
            // Somebody else's companion is not fair game.
            event.setCanceled(true);
            if (attacker instanceof ServerPlayer player && player.connection != null) {
                player.displayClientMessage(Component.translatable(companion.isFemale() ? "kingdomworld.companion.protected.female"
                        : "kingdomworld.companion.protected", companion.getDisplayName(),
                        PlayerNames.name(server, to)), true);
            }
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        MinecraftServer server = victim.getServer();
        if (victim.level().isClientSide() || server == null) {
            return;
        }
        Entity killer = event.getSource().getEntity();
        if (killer == null) {
            killer = victim.getKillCredit();
        }
        if (killer instanceof CompanionEntity companion && !(victim instanceof Player)) {
            try {
                CompanionWork.onKill(server, companion, victim);
            } catch (RuntimeException e) {
                LOGGER.error("KingdomWorld could not count the kill of a companion", e);
            }
        }
    }

    private static void onServerStarted(ServerStartedEvent event) {
        CompanionSavedData data = CompanionSavedData.get(event.getServer());
        CompanionState state = data.state();
        LOGGER.info("KingdomWorld companions: {} companion(s), {} market(s), {} part(ies), data format {}{}", state.companions().size(),
                state.markets().size(), state.parties().parties().size(), CompanionSavedData.FORMAT_VERSION,
                data.isReadOnly() ? " (READ-ONLY: " + data.readOnlyReason().orElse("?") + ")" : "");
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        CompanionSettings.reset();
        CompanionWork.reset();
    }
}
