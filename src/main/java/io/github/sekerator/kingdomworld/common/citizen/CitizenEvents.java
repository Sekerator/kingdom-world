package io.github.sekerator.kingdomworld.common.citizen;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.goal.PathBudget;
import io.github.sekerator.kingdomworld.common.citizen.goal.ScanBudget;
import io.github.sekerator.kingdomworld.common.citizen.work.GatheringSettings;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.citizen.work.ZoneSurveys;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.protection.ProtectionService;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.TerritoryListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Wires the citizens into the game: the server tick drives bodies, the simulation and the scan for places; block
 * and chunk events keep places current; monsters that hunt villagers hunt citizens too; and outsiders may not hurt
 * citizens in peacetime.
 */
public final class CitizenEvents {
    /** Rescans chunks that change hands. */
    public static final TerritoryListener TERRITORY_LISTENER =
            (dimension, chunk, oldOwner, newOwner) -> CitizenSites.scheduleChunk(dimension, chunk);

    private CitizenEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(CitizenEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, CitizenEvents::onJoin);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, CitizenEvents::onAttack);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, CitizenEvents::onPlayerAttack);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, CitizenEvents::onPlace);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, CitizenEvents::onBreak);
        MinecraftForge.EVENT_BUS.addListener(CitizenEvents::onChunkLoad);
        MinecraftForge.EVENT_BUS.addListener(io.github.sekerator.kingdomworld.common.citizen.work.HerderBehavior::onDrops);
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            PathBudget.reset();
            ScanBudget.reset();
            return;
        }
        var server = event.getServer();
        long tick = server.getTickCount();
        if (tick % CitizenBodies.UPDATE_INTERVAL == 0) {
            CitizenSettings.refresh();
            GatheringSettings.refresh();
            var rules = GatheringSettings.rules();
            KingdomWorldApi.state(server).citizens().setPostDefaults(rules.defaultRadius(), Math.max(2, rules.maxHerd() / 2));
            CitizenBodies.update(server);
            CitizenCare.update(server);
        }
        CitizenSites.tick(server);
        ZoneSurveys.tick(server);
        CitizenSimulation.tick(server, tick);
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (event.getLevel().isClientSide) {
            return;
        }
        if (entity instanceof CitizenEntity) {
            CitizenBodies.onJoin(event);
        } else if (entity instanceof Zombie zombie) {
            zombie.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(zombie, CitizenEntity.class, true));
        } else if (entity instanceof AbstractIllager illager) {
            illager.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(illager, CitizenEntity.class, true));
        }
    }

    /**
     * Players (and machines acting for them) may hurt a citizen only if the rules allow it: in peacetime only
     * members of the citizen's kingdom. Covers melee, arrows, thrown potions and lit explosives.
     */
    private static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof CitizenEntity citizen) || citizen.level().isClientSide) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof Player player && !ProtectionService.mayHurtCitizen(player, citizen)) {
            event.setCanceled(true);
            denied(player, citizen);
        }
    }

    private static void onPlayerAttack(AttackEntityEvent event) {
        if (event.getTarget() instanceof CitizenEntity citizen && !citizen.level().isClientSide
                && !ProtectionService.mayHurtCitizen(event.getEntity(), citizen)) {
            event.setCanceled(true);
            denied(event.getEntity(), citizen);
        }
    }

    private static void denied(Player player, CitizenEntity citizen) {
        if (citizen.getServer() == null || citizen.kingdomId() == null) {
            return;
        }
        KingdomWorldApi.kingdom(citizen.getServer(), citizen.kingdomId()).ifPresent(kingdom -> ProtectionService.notify(player,
                Component.translatable("kingdomworld.protection.citizen", Component.literal(ProtectionService.shortName(kingdom.name()))
                        .withStyle(style -> style.withColor(TextColor.fromRgb(kingdom.color()))))));
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) {
            CitizenSites.onPlaced(level, event.getPos(), event.getPlacedBlock());
        }
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) {
            // The block is still there; the scan of its chunk in a moment forgets the place.
            CitizenSites.schedule(level, event.getPos());
        }
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk
                && level.getServer().isSameThread()
                && KingdomWorldApi.ownerOfChunk(level, chunk.getPos().x, chunk.getPos().z) != null) {
            CitizenSites.scheduleChunk(Dimensions.id(level), ChunkKey.of(chunk.getPos().x, chunk.getPos().z));
        }
    }

    /**
     * Forgets everything kept in memory, when the server stops.
     */
    public static void reset() {
        CitizenBodies.reset();
        CitizenCare.reset();
        CitizenSites.reset();
        CitizenSettings.reset();
        GatheringSettings.reset();
        PostWork.reset();
        ZoneSurveys.reset();
    }
}
