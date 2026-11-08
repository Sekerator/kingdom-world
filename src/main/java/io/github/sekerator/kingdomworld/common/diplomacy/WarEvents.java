package io.github.sekerator.kingdomworld.common.diplomacy;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.law.LawWork;
import io.github.sekerator.kingdomworld.common.protection.ProtectionService;
import io.github.sekerator.kingdomworld.core.army.Hostility;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyRules;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyState;
import io.github.sekerator.kingdomworld.core.law.CrimeKind;
import io.github.sekerator.kingdomworld.core.law.LawPolicy;
import io.github.sekerator.kingdomworld.core.protection.AccessPolicy;
import io.github.sekerator.kingdomworld.core.protection.AccessSubject;
import io.github.sekerator.kingdomworld.core.protection.ProtectedAction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Wars near players: soldiers of kingdoms at war fight each other and the enemy players who are online at the keyboard,
 * players of kingdoms at war may fight each other while players of kingdoms at peace may not, nobody hurts a player who is
 * away or offline, the law judges no blow or killing of an enemy, a player who enters the land of an enemy hears it, and
 * kills of enemies count for the score.
 */
public final class WarEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = WarWork.KEY;
    /** The land each player stood on at the last check, for the line above the hotbar. */
    private static final Map<UUID, UUID> LAND = new HashMap<>();
    /** When each attacker was last told why a blow was refused. */
    private static final Map<UUID, Long> TOLD = new HashMap<>();

    private static volatile MinecraftServer current;

    private static final Hostility HOSTILITY = new Hostility() {
        @Override
        public boolean isEnemy(UUID kingdomId, UUID playerId, UUID playerKingdom) {
            MinecraftServer server = current;
            return server != null && playerKingdom != null && WarWork.fighting(server, kingdomId, playerKingdom);
        }

        @Override
        public boolean areEnemies(UUID kingdomId, UUID otherKingdom) {
            MinecraftServer server = current;
            return server != null && WarWork.fighting(server, kingdomId, otherKingdom);
        }

        @Override
        public boolean atWar(UUID kingdomId) {
            MinecraftServer server = current;
            return server != null && WarWork.state(server).fightsAnyWar(kingdomId);
        }
    };

    /**
     * The law of a kingdom does not judge blows and killings of enemies in war: neither an enemy on its land, nor its own
     * people fighting the enemy at home or on the land of the enemy.
     */
    private static final LawPolicy.Exemption LAW = new LawPolicy.Exemption() {
        @Override
        public boolean excuses(UUID kingdomId, UUID player, CrimeKind kind) {
            return excuses(kingdomId, player, kind, null);
        }

        @Override
        public boolean excuses(UUID kingdomId, UUID player, CrimeKind kind, UUID victimKingdom) {
            MinecraftServer server = current;
            if (server == null || (kind != CrimeKind.ASSAULT && kind != CrimeKind.MURDER)) {
                return false;
            }
            UUID own = KingdomWorldApi.state(server).membership().kingdomOf(player).orElse(null);
            if (own == null) {
                return false;
            }
            if (WarWork.fighting(server, own, kingdomId)) {
                return true;
            }
            return victimKingdom != null && WarWork.fighting(server, own, victimKingdom)
                    && (kingdomId.equals(own) || kingdomId.equals(victimKingdom));
        }
    };

    /**
     * Players may hurt citizens of a kingdom they fight; blocks, containers and the rest of the land stay protected.
     */
    private static final AccessPolicy.Exemption CITIZENS = (subject, owner, action, dimension, chunk) -> {
        MinecraftServer server = current;
        if (server == null || action != ProtectedAction.ATTACK_CITIZEN || subject.kind() != AccessSubject.Kind.PLAYER) {
            return false;
        }
        UUID own = KingdomWorldApi.state(server).membership().kingdomOf(subject.playerId()).orElse(null);
        return own != null && WarWork.fighting(server, own, owner);
    };

    private WarEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(WarEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(WarEvents::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(WarEvents::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(WarEvents::onLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, LivingAttackEvent.class, WarEvents::onAttack);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, LivingDeathEvent.class, WarEvents::onDeath);
        KingdomWorldApi.accessPolicy().addExemption(CITIZENS);
        LawWork.policy().addExemption(LAW);
        io.github.sekerator.kingdomworld.common.life.LifeRumors.register(WarRumors.SOURCE, WarRumors::rumors);
    }

    // ---------------------------------------------------------------- the server

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        current = server;
        DiplomacySettings.refresh();
        long started = System.nanoTime();
        try {
            WarWork.tick(server);
            enemyLand(server);
        } catch (RuntimeException e) {
            LOGGER.error("KingdomWorld war tick failed", e);
        }
        long spent = System.nanoTime() - started;
        ticks++;
        total += spent;
        worst = Math.max(worst, spent);
    }

    private static long ticks;
    private static long total;
    private static long worst;

    /**
     * How long the work of the wars took per second since the start, for operators.
     */
    public static String timing() {
        double average = ticks == 0 ? 0.0 : total / (double) ticks / 1_000_000.0;
        return String.format(java.util.Locale.ROOT, "Wars: %d tick(s) with work, average %.3f ms, worst %.3f ms", ticks, average, worst / 1_000_000.0);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        current = server;
        ArmyWork.setHostility(HOSTILITY);
        DiplomacySavedData data = DiplomacySavedData.get(server);
        DiplomacyState state = data.state();
        long wars = state.wars().stream().filter(war -> !war.over()).count();
        LOGGER.info("KingdomWorld diplomacy: {} treaty(ies), {} war(s) going on, {} open offer(s), data format {}{}", state.treaties().size(), wars,
                state.proposals().stream().filter(p -> p.open()).count(), DiplomacySavedData.FORMAT_VERSION,
                data.isReadOnly() ? " (READ-ONLY: " + data.readOnlyReason().orElse("?") + ")" : "");
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        ArmyWork.setHostility(null);
        current = null;
        DiplomacySettings.reset();
        WarWork.reset();
        ticks = 0;
        total = 0;
        worst = 0;
        LAND.clear();
        TOLD.clear();
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAND.remove(event.getEntity().getUUID());
        TOLD.remove(event.getEntity().getUUID());
    }

    // ---------------------------------------------------------------- the land of the enemy

    /**
     * A player who steps onto the land of a kingdom at war with its own hears it above the hotbar.
     */
    private static void enemyLand(MinecraftServer server) {
        DiplomacyState state = WarWork.state(server);
        if (state.wars().stream().noneMatch(war -> war.active())) {
            LAND.clear();
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID own = KingdomWorldApi.state(server).membership().kingdomOf(player.getUUID()).orElse(null);
            if (own == null) {
                continue;
            }
            UUID owner = KingdomWorldApi.ownerAt(player.level(), player.blockPosition());
            UUID before = LAND.put(player.getUUID(), owner);
            if (owner != null && !owner.equals(before) && WarWork.fighting(server, own, owner)) {
                boolean shielded = WarWork.shielded(server, owner);
                player.displayClientMessage(Component.translatable(KEY + (shielded ? "enemy_land_shielded" : "enemy_land"),
                        Component.literal(ProtectionService.shortName(WarWork.name(server, owner).getString()))).withStyle(ChatFormatting.RED), true);
            }
        }
    }

    // ---------------------------------------------------------------- blows

    @Nullable
    private static UUID kingdomOf(MinecraftServer server, Entity entity) {
        if (entity instanceof CitizenEntity citizen) {
            return citizen.kingdomId();
        }
        if (entity instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            return KingdomWorldApi.state(server).membership().kingdomOf(player.getUUID()).orElse(null);
        }
        return null;
    }

    /**
     * Whether a player may be attacked in war at all: online, at the keyboard and playing.
     */
    public static boolean fairGame(MinecraftServer server, ServerPlayer player) {
        return !player.hasDisconnected() && server.getPlayerList().getPlayer(player.getUUID()) != null && !ArmyWork.isAway(player)
                && !player.isCreative() && !player.isSpectator();
    }

    private static void onAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        MinecraftServer server = victim.getServer();
        if (victim.level().isClientSide() || server == null || event.isCanceled()) {
            return;
        }
        Entity source = event.getSource().getEntity();
        if (source == null || source == victim) {
            return;
        }
        if (victim instanceof ServerPlayer target && !(target instanceof FakePlayer) && source instanceof ServerPlayer striker
                && !(striker instanceof FakePlayer)) {
            UUID a = kingdomOf(server, striker);
            UUID b = kingdomOf(server, target);
            if (a == null || b == null || a.equals(b)) {
                return;
            }
            if (WarWork.fighting(server, a, b)) {
                if (!fairGame(server, target)) {
                    event.setCanceled(true);
                    refuse(server, striker, "away");
                    return;
                }
                WarWork.fought(server, a);
                WarWork.fought(server, b);
                return;
            }
            if (DiplomacySettings.protectPeacefulPlayers() && !outlaw(server, target)) {
                event.setCanceled(true);
                refuse(server, striker, "peace");
            }
            return;
        }
        // Soldiers and players in a fight of a war near players: no shield for their kingdoms for a while.
        UUID a = kingdomOf(server, source);
        UUID b = kingdomOf(server, victim);
        if (a != null && b != null && !a.equals(b) && soldierOrPlayer(source) && soldierOrPlayer(victim) && WarWork.fighting(server, a, b)) {
            WarWork.fought(server, a);
            WarWork.fought(server, b);
        }
    }

    private static boolean soldierOrPlayer(Entity entity) {
        return entity instanceof ServerPlayer || (entity instanceof CitizenEntity citizen && Professions.isMilitary(citizen.professionId()));
    }

    private static boolean outlaw(MinecraftServer server, ServerPlayer player) {
        return !LawWork.isReadOnly(server) && !LawWork.state(server).wantedOf(player.getUUID()).isEmpty();
    }

    private static void refuse(MinecraftServer server, ServerPlayer striker, String why) {
        long now = KingdomWorldApi.gameTime(server);
        Long last = TOLD.get(striker.getUUID());
        if (last != null && now - last < 40) {
            return;
        }
        TOLD.put(striker.getUUID(), now);
        striker.displayClientMessage(Component.translatable(KEY + "no_blow." + why).withStyle(ChatFormatting.GOLD), true);
    }

    // ---------------------------------------------------------------- kills

    /**
     * An enemy soldier or player killed near players counts for the score of the side of the killer.
     */
    private static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        MinecraftServer server = victim.getServer();
        if (victim.level().isClientSide() || server == null || WarWork.isReadOnly(server) || !soldierOrPlayer(victim)) {
            return;
        }
        Entity killer = event.getSource().getEntity();
        if (killer == null) {
            killer = victim.getKillCredit();
        }
        if (killer == null || !soldierOrPlayer(killer)) {
            return;
        }
        UUID a = kingdomOf(server, killer);
        UUID b = kingdomOf(server, victim);
        if (a == null || b == null || a.equals(b) || !WarWork.fighting(server, a, b)) {
            return;
        }
        try {
            DiplomacyRules rules = DiplomacySettings.rules();
            DiplomacyState state = WarWork.state(server);
            double points = victim instanceof ServerPlayer ? rules.scorePlayerKill() : rules.scoreKill();
            state.score(a, b, DiplomacyState.Feat.KILL, points);
            state.exhaust(b, a, rules.exhaustionLoss(), 1);
            Optional<io.github.sekerator.kingdomworld.core.diplomacy.War> war = state.warBetween(a, b);
            if (victim instanceof ServerPlayer dead && war.isPresent()) {
                state.record(KingdomWorldApi.gameTime(server), "kill", a, b, war.get().id(), dead.getGameProfile().getName());
            }
        } catch (RuntimeException e) {
            LOGGER.error("KingdomWorld war could not count the death of {}", victim.getName().getString(), e);
        }
    }
}
