package io.github.sekerator.kingdomworld.common.guild;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.core.guild.GuildState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.slf4j.Logger;

/**
 * Wires the guild into the game: monsters from spawners are marked, deaths of monsters give crystals, points and
 * progress, crystals fall with the drops, and once a second contracts run out, squads come back and the guild posts
 * contracts of its own.
 */
public final class GuildEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHECK_TICKS = 20;

    private GuildEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDeathEvent.class, GuildEvents::onDeath);
        MinecraftForge.EVENT_BUS.addListener(GuildEvents::onDrops);
        MinecraftForge.EVENT_BUS.addListener(GuildEvents::onFinalizeSpawn);
        MinecraftForge.EVENT_BUS.addListener(GuildEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(GuildEvents::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(GuildEvents::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(GuildEvents::onTags);
    }

    private static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (event.getSpawnType() == MobSpawnType.SPAWNER && !event.getLevel().isClientSide()) {
            event.getEntity().getPersistentData().putBoolean(GuildWork.SPAWNER_TAG, true);
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || victim.getServer() == null) {
            return;
        }
        Entity killer = event.getSource().getEntity();
        if (killer == null) {
            killer = victim.getKillCredit();
        }
        GuildWork.onKill(victim.getServer(), victim, killer);
    }

    private static void onDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        GuildWork.takeDrop(victim.getUUID()).ifPresent(stack -> event.getDrops().add(new ItemEntity(victim.level(), victim.getX(),
                victim.getY(), victim.getZ(), stack)));
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_TICKS != 0) {
            return;
        }
        GuildSettings.refresh();
        GuildWork.tick(server);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        GuildSavedData data = GuildSavedData.get(event.getServer());
        GuildState guild = data.state();
        LOGGER.info("KingdomWorld guild: {} member(s), {} adventurer(s), {} branch(es), {} contract(s), {} squad(s), data format {}{}",
                guild.members().size(), guild.hirelings().size(), guild.branches().size(), guild.contracts().size(), guild.squads().size(),
                GuildSavedData.FORMAT_VERSION, data.isReadOnly() ? " (READ-ONLY: " + data.readOnlyReason().orElse("?") + ")" : "");
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        GuildSettings.reset();
        GuildWork.reset();
    }

    private static void onTags(TagsUpdatedEvent event) {
        MonsterRanks.invalidate();
    }
}
