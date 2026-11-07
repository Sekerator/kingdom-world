package io.github.sekerator.kingdomworld.common.army;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.work.Equipment;
import io.github.sekerator.kingdomworld.core.army.ArmyState;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Wires the army into the game: once a second the army moves on; soldiers never hurt players or citizens in peace (a stray
 * arrow included); monsters that attack the people of a kingdom on its land raise the alarm; soldiers gain experience for
 * the monsters they kill; their weapons and armor wear in fights.
 */
public final class ArmyEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHECK_TICKS = 20;

    private ArmyEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(ArmyEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, LivingAttackEvent.class, ArmyEvents::onAttack);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, LivingHurtEvent.class, ArmyEvents::onHurt);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDeathEvent.class, ArmyEvents::onDeath);
        MinecraftForge.EVENT_BUS.addListener(ArmyEvents::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(ArmyEvents::onServerStopped);
    }

    private static CitizenEntity soldier(Entity entity) {
        return entity instanceof CitizenEntity body && body.kingdomId() != null && Professions.isMilitary(body.professionId()) ? body : null;
    }

    private static void onAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        MinecraftServer server = victim.getServer();
        if (victim.level().isClientSide() || server == null) {
            return;
        }
        CitizenEntity attacker = soldier(event.getSource().getEntity());
        if (attacker != null && (victim instanceof Player || victim instanceof CitizenEntity)
                && !ArmyWork.mayAttack(server, attacker.kingdomId(), victim)) {
            // Soldiers do not hurt players or citizens in peace, by a blow or by a stray arrow.
            event.setCanceled(true);
            return;
        }
        Entity source = event.getSource().getEntity();
        if (source instanceof Mob mob && source instanceof Enemy && !(source instanceof CitizenEntity)) {
            UUID kingdom = null;
            if (victim instanceof CitizenEntity body) {
                kingdom = body.kingdomId();
            } else if (victim instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
                kingdom = KingdomWorldApi.state(server).membership().kingdomOf(player.getUUID()).orElse(null);
            }
            if (kingdom != null && kingdom.equals(KingdomWorldApi.ownerAt(victim.level(), victim.blockPosition()))) {
                ArmyWork.raiseAlarm(kingdom, mob, KingdomWorldApi.gameTime(server));
            }
        }
    }

    private static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || event.getAmount() <= 0) {
            return;
        }
        CitizenEntity wearer = soldier(victim);
        if (wearer != null) {
            Equipment.Hands hands = Equipment.of(wearer);
            for (EquipmentSlot slot : Equipment.ARMOR) {
                Equipment.wear(hands, slot, 1, wearer.getRandom());
            }
            if (ArmyGear.isShield(wearer.getItemBySlot(EquipmentSlot.OFFHAND))) {
                Equipment.wear(hands, EquipmentSlot.OFFHAND, 1, wearer.getRandom());
            }
        }
        CitizenEntity striker = soldier(event.getSource().getEntity());
        if (striker != null && !(event.getSource().getDirectEntity() instanceof Projectile)) {
            Equipment.wear(Equipment.of(striker), EquipmentSlot.MAINHAND, 1, striker.getRandom());
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
        CitizenEntity soldier = soldier(killer);
        if (soldier != null && !(victim instanceof Player) && !(victim instanceof CitizenEntity)) {
            try {
                ArmyWork.onKill(victim.getServer(), soldier, victim);
            } catch (RuntimeException e) {
                LOGGER.error("KingdomWorld army could not count the kill of {}", victim.getType(), e);
            }
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_TICKS != 0) {
            return;
        }
        ArmySettings.refresh();
        try {
            ArmyWork.tick(server);
        } catch (RuntimeException e) {
            LOGGER.error("KingdomWorld army tick failed", e);
        }
    }

    private static void onServerStarted(ServerStartedEvent event) {
        ArmySavedData data = ArmySavedData.get(event.getServer());
        ArmyState army = data.state();
        LOGGER.info("KingdomWorld army: {} soldier(s), {} squad(s), {} band(s) of mercenaries, data format {}{}", army.soldiers().size(),
                army.squads().size(), army.bands().size(), ArmySavedData.FORMAT_VERSION,
                data.isReadOnly() ? " (READ-ONLY: " + data.readOnlyReason().orElse("?") + ")" : "");
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        ArmySettings.reset();
        ArmyWork.reset();
    }
}
