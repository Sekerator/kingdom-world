package io.github.sekerator.kingdomworld.common;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEvents;
import io.github.sekerator.kingdomworld.common.command.KingdomCommand;
import io.github.sekerator.kingdomworld.common.command.KingdomWorldCommand;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.kingdom.TerritorySync;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.common.protection.FireProtectionHook;
import io.github.sekerator.kingdomworld.common.protection.ProtectionEvents;
import io.github.sekerator.kingdomworld.common.protection.ProtectionService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.LogicalSide;
import org.slf4j.Logger;

/**
 * Game event listeners that run on both physical sides (they act on the logical server).
 */
public final class CommonEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CommonEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(CommonEvents::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(CommonEvents::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(CommonEvents::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(CommonEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(CommonEvents::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(CommonEvents::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(CommonEvents::onPlayerLoggedOut);
        ProtectionEvents.register();
        CitizenEvents.register();
        io.github.sekerator.kingdomworld.common.economy.EconomyEvents.register();
        io.github.sekerator.kingdomworld.common.trade.TradeEvents.register();
        io.github.sekerator.kingdomworld.common.guild.GuildEvents.register();
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        KingdomWorldCommand.register(event.getDispatcher());
        KingdomCommand.register(event.getDispatcher());
    }

    private static void onServerStarted(ServerStartedEvent event) {
        KingdomWorldConfig.Server config = KingdomWorldConfig.SERVER;
        LOGGER.info("KingdomWorld server config: maxActiveNpcs={}, simulationIntervalTicks={}, afkTimeoutSeconds={}, combatTagSeconds={}",
                config.maxActiveNpcs.get(), config.simulationIntervalTicks.get(),
                config.afkTimeoutSeconds.get(), config.combatTagSeconds.get());
        LOGGER.info("KingdomWorld kingdom rules: allowedDimensions={}, minChunksFromOtherKingdom={}, initialClaimRadius={}, "
                        + "baseMaxChunks={}, chunksPerMember={}, claimBufferChunks={}",
                config.allowedDimensions.get(), config.minChunksFromOtherKingdom.get(), config.initialClaimRadius.get(),
                config.baseMaxChunks.get(), config.chunksPerMember.get(), config.claimBufferChunks.get());
        LOGGER.info("KingdomWorld protection: interactions={}, explosions={}, fire={}, fakePlayers={}",
                config.protectInteractions.get(), config.protectFromExplosions.get(), config.protectFromFire.get(),
                config.allowFakePlayers.get());
        // The fire block carries a marker interface when the fire hook was applied.
        Object fire = Blocks.FIRE;
        if (fire instanceof FireProtectionHook) {
            LOGGER.info("KingdomWorld fire protection: active");
        } else {
            LOGGER.error("KingdomWorld fire protection: the fire hook is missing, fire can spread into kingdom land");
        }
        // Load the data eagerly so that problems with it show up at startup rather than on first use.
        KingdomSavedData data = KingdomSavedData.get(event.getServer());
        LOGGER.info("KingdomWorld citizens: activationChunks={}, growthFoodDays={}, growthIntervalMinutes={}, starvationCanKill={}, "
                        + "dailyNutrition={}, infirmaryCapacity={}, woundedHealthPercent={}, maxCitizensPerKingdom={}, farmRadius={}",
                config.citizenActivationChunks.get(), config.growthFoodDays.get(), config.growthIntervalMinutes.get(),
                config.starvationCanKill.get(), config.dailyNutrition.get(), config.infirmaryCapacity.get(),
                config.woundedHealthPercent.get(), config.maxCitizensPerKingdom.get(), config.farmRadius.get());
        LOGGER.info("KingdomWorld loaded {} kingdom(s) with {} citizen(s), {} work post(s), {} storehouse(s), {} treasury(ies), "
                        + "{} player(s) with accounts, {} market(s), {} deal(s), {} caravan(s) on the way, data format {}{}",
                data.registry().size(), data.state().citizens().registry().size(), data.state().citizens().posts().size(),
                data.storehouses().size(), data.state().economy().treasuries().size(), data.state().economy().accounts().players().size(),
                data.state().trade().markets().size(), data.state().trade().deals().size(), data.state().trade().caravans().size(),
                KingdomSavedData.FORMAT_VERSION,
                data.isReadOnly() ? " (READ-ONLY: " + data.readOnlyReason().orElse("?") + ")" : "");
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        ServerRequestGuard.reset();
        ProtectionService.reset();
        TerritorySync.reset();
        CitizenEvents.reset();
        io.github.sekerator.kingdomworld.common.economy.EconomyEvents.reset();
        io.github.sekerator.kingdomworld.common.trade.TradeEvents.reset();
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            TerritorySync.flush(event.getServer());
        }
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.side == LogicalSide.SERVER
                && event.player instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            TerritorySync.checkBorder(player);
        }
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            KingdomActions.greet(player);
        }
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerRequestGuard.forget(event.getEntity().getUUID());
        ProtectionService.forget(event.getEntity().getUUID());
        TerritorySync.forget(event.getEntity().getUUID());
        KingdomCommand.forget(event.getEntity().getUUID());
    }
}
