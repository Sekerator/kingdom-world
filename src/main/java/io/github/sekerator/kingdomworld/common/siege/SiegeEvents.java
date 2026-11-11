package io.github.sekerator.kingdomworld.common.siege;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.construction.TemplateBlocks;
import io.github.sekerator.kingdomworld.common.diplomacy.DiplomacySettings;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyState;
import io.github.sekerator.kingdomworld.core.protection.AccessPolicy;
import io.github.sekerator.kingdomworld.core.protection.AccessSubject;
import io.github.sekerator.kingdomworld.core.protection.ProtectedAction;
import io.github.sekerator.kingdomworld.core.siege.Gate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * The war in the world as events: land of the enemy opens to the players of the other side while the war goes on and the
 * enemy is not shielded (they break and place blocks, open and rob its chests); the throne and blocks the builders never
 * clear stay whole; explosions break the open land only; blocks of buildings and walls broken in war are written down for
 * the mending; enemies cannot open the leaves of a closed gate; a ram breaks gates and walls faster.
 */
public final class SiegeEvents {
    private static int ticks;

    /**
     * Players of a kingdom at war with the owner may break, place, use items, open containers and doors on its open land.
     * Hurting citizens is decided by the rules of 9a.
     */
    private static final AccessPolicy.Exemption WAR_LAND = (subject, owner, action, dimension, chunk) -> {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || subject.kind() != AccessSubject.Kind.PLAYER || action == ProtectedAction.ATTACK_CITIZEN) {
            return false;
        }
        UUID own = KingdomWorldApi.state(server).membership().kingdomOf(subject.playerId()).orElse(null);
        return SiegeWork.open(server, own, owner);
    };

    private SiegeEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(SiegeEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(SiegeEvents::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(SiegeEvents::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(SiegeEvents::onLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, SiegeEvents::onBreakFirst);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, BlockEvent.BreakEvent.class, SiegeEvents::onBroken);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, BlockEvent.EntityPlaceEvent.class, SiegeEvents::onPlaced);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, SiegeEvents::onRightClickBlock);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, PlayerInteractEvent.RightClickBlock.class, SiegeEvents::onOpened);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, SiegeEvents::onExplosion);
        MinecraftForge.EVENT_BUS.addListener(SiegeEvents::onBreakSpeed);
        KingdomWorldApi.accessPolicy().addExemption(WAR_LAND);
    }

    // ---------------------------------------------------------------- the server

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++ticks % 20 != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        try {
            if (ticks % 400 == 0) {
                SiegeSettings.refresh();
            }
            SiegeWork.tick(server);
        } catch (RuntimeException e) {
            SiegeWork.LOGGER.error("The second of the sieges failed", e);
        }
    }

    private static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        SiegeSavedData data = SiegeSavedData.get(event.getServer());
        io.github.sekerator.kingdomworld.core.siege.SiegeState state = data.state();
        int peace = 0;
        for (java.util.UUID kingdom : state.damage().all().keySet()) {
            peace += state.damage().countFree(kingdom);
        }
        SiegeWork.LOGGER.info("KingdomWorld sieges: {} gate(s), {} siege(s), {} hold(s), {} conquest(s) in the journal, {} kingdom(s) with war damage, "
                + "{} block(s) damaged in peace, data format {}{}", state.gates().size(), state.sieges().size(), state.holds().size(),
                state.conquests().size(), state.damage().all().size(), peace, SiegeSavedData.FORMAT_VERSION, data.isReadOnly() ? " (READ-ONLY)" : "");
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        ticks = 0;
        SiegeWork.reset();
        SiegeRepair.reset();
        SiegeSettings.reset();
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            SiegeWork.state(player.server).offerTo(player.getUUID()).ifPresent(offer -> SiegeConquests.tellOffer(player, offer));
        }
    }

    // ---------------------------------------------------------------- blocks

    @Nullable
    private static UUID owner(Level level, BlockPos pos) {
        return KingdomWorldApi.ownerAt(level, pos);
    }

    /**
     * Whether a player breaks or uses the land of an enemy only because the war opened it (not as a member of the owner).
     */
    private static boolean byWar(MinecraftServer server, Player player, @Nullable UUID owner) {
        if (owner == null || player instanceof FakePlayer) {
            return false;
        }
        UUID own = SiegeWork.kingdomOf(server, player);
        return own != null && !own.equals(owner) && SiegeWork.open(server, own, owner);
    }

    private static void onBreakFirst(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getPlayer() == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        BlockPos pos = event.getPos();
        if (event.getState().is(TemplateBlocks.UNCLEARABLE) && byWar(server, event.getPlayer(), owner(level, pos))) {
            event.setCanceled(true);
            event.getPlayer().displayClientMessage(Component.translatable(SiegeWork.KEY + "unbreakable").withStyle(ChatFormatting.RED), true);
        }
    }

    private static void onBroken(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getPlayer() == null || SiegeWork.isReadOnly(level.getServer())) {
            return;
        }
        MinecraftServer server = level.getServer();
        SiegeWork.state(server).removedContainer(io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(level), event.getPos().asLong());
        UUID owner = owner(level, event.getPos());
        if (!byWar(server, event.getPlayer(), owner)) {
            return;
        }
        long war = WarWork.state(server).warBetween(SiegeWork.kingdomOf(server, event.getPlayer()), owner).map(w -> w.id()).orElse(0L);
        SiegeRepair.broken(server, owner, level, event.getPos(), event.getState(), war);
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        Optional<Gate> gate = SiegeGates.gateAt(server, event.getPos());
        if (gate.isEmpty() || !gate.get().closed()) {
            return;
        }
        if (!SiegeWork.friends(server, SiegeWork.kingdomOf(server, event.getEntity()), gate.get().kingdomId())) {
            // A closed gate of the enemy does not open from outside; it is broken.
            event.setUseBlock(Event.Result.DENY);
            event.getEntity().displayClientMessage(Component.translatable(SiegeWork.KEY + "gate_shut").withStyle(ChatFormatting.RED), true);
        }
    }

    /**
     * A container placed on the land of a kingdom by a player of another kingdom is remembered: robbing it in war is no loot.
     */
    private static void onPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Player player) || SiegeWork.isReadOnly(level.getServer())) {
            return;
        }
        MinecraftServer server = level.getServer();
        BlockPos pos = event.getPos();
        UUID owner = owner(level, pos);
        if (owner == null || owner.equals(SiegeWork.kingdomOf(server, player)) || !container(level, pos)) {
            return;
        }
        SiegeWork.state(server).placedByOutsider(io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(level) + "@" + pos.asLong());
    }

    private static boolean container(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity != null && blockEntity.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).isPresent();
    }

    /**
     * Whether a container holds anything to take.
     */
    private static boolean holdsAnything(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return false;
        }
        return blockEntity.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).map(handler -> {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (!handler.getStackInSlot(slot).isEmpty()) {
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    /**
     * A container of the enemy opened on its open land: loot of war, worth some score once per container and war, only for a
     * container of the enemy (not one an outsider placed there) that holds something, and no more than
     * {@link io.github.sekerator.kingdomworld.core.siege.SiegeState#MAX_LOOT_SCORE} for a side in a war.
     */
    private static void onOpened(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getUseBlock() == Event.Result.DENY || SiegeWork.isReadOnly(level.getServer())) {
            return;
        }
        MinecraftServer server = level.getServer();
        BlockPos pos = event.getPos();
        if (!container(level, pos)) {
            return;
        }
        UUID owner = owner(level, pos);
        if (!byWar(server, event.getEntity(), owner)) {
            return;
        }
        String dimension = io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(level);
        io.github.sekerator.kingdomworld.core.siege.SiegeState state = SiegeWork.state(server);
        if (state.placedByOutsider(dimension, pos.asLong()) || !holdsAnything(level, pos)) {
            return;
        }
        UUID own = SiegeWork.kingdomOf(server, event.getEntity());
        DiplomacyState diplomacy = WarWork.state(server);
        long war = diplomacy.warBetween(own, owner).map(w -> w.id()).orElse(0L);
        double points = state.rob(war, own, dimension, pos.asLong(), DiplomacySettings.rules().scoreCaravan() / 3.0);
        if (points > 0) {
            diplomacy.score(own, owner, DiplomacyState.Feat.HOLD, points);
            event.getEntity().displayClientMessage(Component.translatable(SiegeWork.KEY + "loot").withStyle(ChatFormatting.GOLD), true);
        }
    }

    /**
     * Explosions on land of a kingdom open to the enemy who lit them break blocks there (if the config allows); the throne,
     * blocks never cleared and land of third kingdoms stay whole. Broken blocks of buildings and walls are written down.
     */
    private static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        UUID by = exploderKingdom(server, event.getExplosion());
        event.getAffectedBlocks().removeIf(pos -> {
            BlockState state = level.getBlockState(pos);
            if (state.is(ModRegistries.THRONE.get()) && KingdomWorldApi.kingdomWithThroneAt(level, pos).isPresent()) {
                return true;
            }
            UUID owner = owner(level, pos);
            return owner != null && by != null && SiegeWork.open(server, by, owner) && state.is(TemplateBlocks.UNCLEARABLE);
        });
        if (by == null || SiegeWork.isReadOnly(server)) {
            return;
        }
        for (BlockPos pos : event.getAffectedBlocks()) {
            UUID owner = owner(level, pos);
            if (owner != null && SiegeWork.open(server, by, owner)) {
                long war = WarWork.state(server).warBetween(by, owner).map(w -> w.id()).orElse(0L);
                SiegeRepair.broken(server, owner, level, pos, level.getBlockState(pos), war);
            }
        }
    }

    /**
     * Whether an explosion may break a block of claimed land: the land is open to the kingdom of whoever lit it, and the
     * config lets explosions break in war. Asked by the protection of land before it keeps claimed blocks whole.
     */
    public static boolean explosionBreaks(Level level, Explosion explosion, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel) || !SiegeSettings.rules().explosions()) {
            return false;
        }
        MinecraftServer server = serverLevel.getServer();
        UUID by = exploderKingdom(server, explosion);
        UUID owner = owner(level, pos);
        return by != null && owner != null && SiegeWork.open(server, by, owner) && !level.getBlockState(pos).is(TemplateBlocks.UNCLEARABLE)
                && !level.getBlockState(pos).is(ModRegistries.THRONE.get());
    }

    @Nullable
    private static UUID exploderKingdom(MinecraftServer server, Explosion explosion) {
        Entity source = explosion.getIndirectSourceEntity();
        if (source == null) {
            source = explosion.getExploder();
        }
        return source instanceof Player || source instanceof io.github.sekerator.kingdomworld.common.citizen.CitizenEntity
                ? SiegeWork.kingdomOf(server, source) : null;
    }

    /**
     * A ram in the hands of a player breaks the leaves of gates and the blocks of walls of the enemy faster.
     */
    private static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (player.getMainHandItem().is(SiegeRegistries.BATTERING_RAM.get()) && rammed(event.getState())) {
            // Decided by the block alone, the same way on the client and the server, so that breaking stays in step.
            event.setNewSpeed(event.getNewSpeed() * (float) SiegeSettings.rules().ramPlayerFactor());
        }
    }

    /**
     * Blocks a ram is made for: leaves of gates, palisades of logs and walls of stone.
     */
    static boolean rammed(BlockState state) {
        return state.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock || state.is(net.minecraft.world.level.block.Blocks.IRON_BARS)
                || state.is(net.minecraft.tags.BlockTags.LOGS) || state.is(net.minecraft.tags.BlockTags.STONE_BRICKS)
                || state.is(net.minecraft.world.level.block.Blocks.COBBLESTONE) || state.is(net.minecraft.world.level.block.Blocks.STONE)
                || state.is(net.minecraft.world.level.block.Blocks.MOSSY_COBBLESTONE)
                // Walls are built of every stone of the tag of wall stones: the kingdoms of the world build in brick, sandstone,
                // deepslate and mud bricks too.
                || new net.minecraft.world.item.ItemStack(state.getBlock().asItem())
                        .is(io.github.sekerator.kingdomworld.common.road.CityWalls.WALL_STONE);
    }
}
