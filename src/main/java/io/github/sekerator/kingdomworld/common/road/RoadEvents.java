package io.github.sekerator.kingdomworld.common.road;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import org.slf4j.Logger;

/**
 * Wires cities, roads and robbers into the game: the search for ways every tick, the roads once a second, the speed of
 * players on roads; robbers and merchants of caravans that drop nothing, cannot be taken or hurt by players (merchants),
 * never break blocks and never attack players away from the game; bodies left over from an earlier run go; blocks of camps
 * drop nothing.
 */
public final class RoadEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SECOND = 20;
    private static final int SPEED_TICKS = 5;

    private RoadEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(RoadEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, LivingAttackEvent.class, RoadEvents::onAttack);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDeathEvent.class, RoadEvents::onDeath);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, LivingDropsEvent.class, RoadEvents::onDrops);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, LivingExperienceDropEvent.class, RoadEvents::onExperience);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, EntityJoinLevelEvent.class, RoadEvents::onJoin);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, PlayerInteractEvent.EntityInteract.class, RoadEvents::onInteract);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, PlayerInteractEvent.EntityInteractSpecific.class,
                RoadEvents::onInteractSpecific);
        MinecraftForge.EVENT_BUS.addListener(RoadEvents::onGriefing);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, BlockEvent.FarmlandTrampleEvent.class, RoadEvents::onTrample);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, BlockEvent.BreakEvent.class, RoadEvents::onBreak);
        MinecraftForge.EVENT_BUS.addListener(RoadEvents::onExplosion);
        MinecraftForge.EVENT_BUS.addListener(RoadEvents::onMount);
        MinecraftForge.EVENT_BUS.addListener(RoadEvents::onLogin);
        MinecraftForge.EVENT_BUS.addListener(RoadEvents::onTags);
        MinecraftForge.EVENT_BUS.addListener(RoadEvents::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(RoadEvents::onServerStopped);
    }

    static boolean isPack(Entity entity) {
        return entity instanceof Llama && entity.getTags().contains(RoadCaravans.PACK_TAG);
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        try {
            RoadPlanning.tickSearch(server);
        } catch (RuntimeException e) {
            LOGGER.error("KingdomWorld search for roads failed", e);
        }
        try {
            CityWalls.tickLayouts(server);
        } catch (RuntimeException e) {
            LOGGER.error("KingdomWorld laying out of walls failed", e);
        }
        if (server.getTickCount() % SPEED_TICKS == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                RoadSpeed.update(player);
            }
        }
        if (server.getTickCount() % SECOND != 0) {
            return;
        }
        RoadSettings.refresh();
        try {
            RoadWork.tick(server);
        } catch (RuntimeException e) {
            LOGGER.error("KingdomWorld roads tick failed", e);
        }
    }

    private static void onAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if ((victim instanceof CaravanMerchantEntity || isPack(victim)) && attacker instanceof Player) {
            // Only robbers fall upon caravans; players of the same world do not.
            event.setCanceled(true);
            return;
        }
        if (attacker instanceof BanditEntity && victim instanceof ServerPlayer player && !BanditWork.mayAttack(player)) {
            event.setCanceled(true);
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
        try {
            if (victim instanceof BanditEntity bandit) {
                BanditWork.died(server, bandit, killer);
            } else if (victim instanceof CaravanMerchantEntity merchant) {
                RoadCaravans.merchantDied(server, merchant, killer);
            }
        } catch (RuntimeException e) {
            LOGGER.error("KingdomWorld could not count the death of {}", victim.getType(), e);
        }
    }

    private static void onDrops(LivingDropsEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof BanditEntity || entity instanceof CaravanMerchantEntity || isPack(entity)) {
            event.setCanceled(true);
        }
    }

    private static void onExperience(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof CaravanMerchantEntity || isPack(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        // Robbers and merchants are never saved; pack llamas are, and those of an earlier run go.
        if (!event.getLevel().isClientSide() && isPack(event.getEntity()) && !RoadCaravans.ours(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    private static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (isPack(event.getTarget()) || event.getTarget() instanceof CaravanMerchantEntity) {
            event.setCanceled(true);
        }
    }

    private static void onInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (isPack(event.getTarget()) || event.getTarget() instanceof CaravanMerchantEntity) {
            event.setCanceled(true);
        }
    }

    private static void onGriefing(EntityMobGriefingEvent event) {
        if (event.getEntity() instanceof BanditEntity || event.getEntity() instanceof CaravanMerchantEntity || isPack(event.getEntity())) {
            event.setResult(Event.Result.DENY);
        }
    }

    private static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getEntity() instanceof BanditEntity || event.getEntity() instanceof CaravanMerchantEntity || isPack(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        BanditWork.campBlock(level, pos).ifPresent(camp -> {
            if (RoadWork.isReadOnly(level.getServer())) {
                return;
            }
            event.setCanceled(true);
            BanditWork.breakCampBlock(level, pos, camp);
        });
    }

    private static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos -> BanditWork.campBlock(level, pos).isPresent());
    }

    private static void onMount(EntityMountEvent event) {
        if (event.isDismounting() && !event.getLevel().isClientSide()) {
            RoadSpeed.dismounted(event.getEntityBeingMounted());
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        RoadSync.forget(event.getEntity().getUUID());
    }

    private static void onTags(TagsUpdatedEvent event) {
        BanditGear.invalidate();
    }

    private static void onServerStarted(ServerStartedEvent event) {
        // Slaves who escape their owners run to the camps of robbers.
        io.github.sekerator.kingdomworld.common.companion.CompanionWork.setEscapeRoute((server, companion, pack, where) ->
                BanditWork.takeRunaway(server, "Companion " + companion.id(), pack, where));
        RoadSavedData data = RoadSavedData.get(event.getServer());
        RoadState roads = data.state();
        LOGGER.info("KingdomWorld roads: {} city(ies), {} road(s), {} camp(s) of robbers, {} caravan(s) on their way, data format {}{}",
                roads.cities().size(), roads.roads().size(), roads.camps().size(), roads.routes().size(), RoadSavedData.FORMAT_VERSION,
                data.isReadOnly() ? " (READ-ONLY: " + data.readOnlyReason().orElse("?") + ")" : "");
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        RoadSettings.reset();
        RoadWork.reset();
    }
}
