package io.github.sekerator.kingdomworld.common.protection;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.protection.ProtectedAction;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Game event hooks that enforce land protection. Each hook turns the event into a {@link ProtectedAction} and
 * asks {@link ProtectionService}; nothing here decides on its own. Hooks run with high priority so that other
 * mods see the cancelled state.
 */
public final class ProtectionEvents {

    private ProtectionEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ProtectionEvents::onBreak);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ProtectionEvents::onPlace);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ProtectionEvents::onRightClickBlock);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ProtectionEvents::onEntityInteract);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ProtectionEvents::onEntityInteractSpecific);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ProtectionEvents::onAttackEntity);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ProtectionEvents::onBucket);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ProtectionEvents::onTrample);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ProtectionEvents::onExplosion);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ProtectionEvents::onFluidPlace);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ProtectionEvents::onPiston);
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) {
            return;
        }
        Player player = event.getPlayer();
        BlockPos pos = event.getPos();
        if (event.getState().is(ModRegistries.THRONE.get())) {
            Optional<Kingdom> heart = KingdomWorldApi.kingdomWithThroneAt(level, pos);
            if (heart.isPresent()) {
                // The heart of a kingdom never breaks; the king dissolves the kingdom first.
                event.setCanceled(true);
                boolean king = player != null && player.getUUID().equals(heart.get().rulerId());
                ProtectionService.notify(player, Component.translatable(king
                        ? "kingdomworld.protection.throne_king" : "kingdomworld.protection.throne"));
                return;
            }
        }
        if (!ProtectionService.isAllowed(player, level, pos, ProtectedAction.BREAK)) {
            if (player != null && io.github.sekerator.kingdomworld.common.law.LawEvents.harvest(player, level, pos, event.getState())) {
                // Taking a ripe crop is a theft now rather than refused; the field itself stays protected.
                return;
            }
            event.setCanceled(true);
            ProtectionService.notifyDenied(player, level, pos);
        }
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            for (BlockSnapshot snapshot : multi.getReplacedBlockSnapshots()) {
                if (!ProtectionService.isAllowed(player, level, snapshot.getPos(), ProtectedAction.PLACE)) {
                    event.setCanceled(true);
                    ProtectionService.notifyDenied(player, level, snapshot.getPos());
                    return;
                }
            }
        } else if (!ProtectionService.isAllowed(player, level, event.getPos(), ProtectedAction.PLACE)) {
            event.setCanceled(true);
            ProtectionService.notifyDenied(player, level, event.getPos());
        }
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }
        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        ProtectedAction blockAction = classify(level, pos, level.getBlockState(pos));
        boolean denied = false;
        if (blockAction != null && !ProtectionService.isAllowed(player, level, pos, blockAction)) {
            if (blockAction == ProtectedAction.CONTAINER && !(player instanceof net.minecraftforge.common.util.FakePlayer)) {
                // Another's container opens: what is taken from it is a theft, judged by the law.
                io.github.sekerator.kingdomworld.common.law.LawEvents.containerUse(player, level, pos);
                return;
            }
            event.setUseBlock(Event.Result.DENY);
            denied = true;
        }
        if (!event.getItemStack().isEmpty()) {
            Direction face = event.getFace() != null ? event.getFace() : event.getHitVec().getDirection();
            BlockPos target = pos.relative(face);
            if (!ProtectionService.isAllowed(player, level, pos, ProtectedAction.USE_ITEM)
                    || !ProtectionService.isAllowed(player, level, target, ProtectedAction.USE_ITEM)) {
                event.setUseItem(Event.Result.DENY);
                denied = true;
            }
        }
        if (denied) {
            ProtectionService.notifyDenied(player, level, pos);
        }
    }

    /**
     * What using the block itself means for protection, or {@code null} if using it is always allowed.
     */
    public static ProtectedAction classify(Level level, BlockPos pos, BlockState state) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null && hasInventory(blockEntity)) {
            return ProtectedAction.CONTAINER;
        }
        if (isInteractive(state)) {
            return ProtectedAction.INTERACT;
        }
        return null;
    }

    private static boolean hasInventory(BlockEntity blockEntity) {
        return blockEntity instanceof Container
                || blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent();
    }

    private static boolean isInteractive(BlockState state) {
        return state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.BUTTONS)
                || state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock || state.getBlock() instanceof ButtonBlock
                || state.getBlock() instanceof LeverBlock || state.getBlock() instanceof DiodeBlock
                || state.getBlock() instanceof DaylightDetectorBlock || state.getBlock() instanceof NoteBlock;
    }

    private static ProtectedAction classify(Entity target) {
        if (target instanceof ContainerEntity) {
            return ProtectedAction.CONTAINER;
        }
        if (target instanceof ItemFrame || target instanceof ArmorStand) {
            return ProtectedAction.INTERACT;
        }
        return null;
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        checkEntityUse(event, event.getTarget());
    }

    private static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        checkEntityUse(event, event.getTarget());
    }

    private static void checkEntityUse(PlayerInteractEvent event, Entity target) {
        Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }
        ProtectedAction action = classify(target);
        if (action == ProtectedAction.CONTAINER && !(event.getEntity() instanceof net.minecraftforge.common.util.FakePlayer)
                && !ProtectionService.isAllowed(event.getEntity(), level, target.blockPosition(), action)) {
            // A cart with a chest opens like a chest: what is taken is a theft.
            io.github.sekerator.kingdomworld.common.law.LawEvents.containerUse(event.getEntity(), level, target.blockPosition());
            return;
        }
        if (action != null && !ProtectionService.isAllowed(event.getEntity(), level, target.blockPosition(), action)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            ProtectionService.notifyDenied(event.getEntity(), level, target.blockPosition());
        }
    }

    private static void onAttackEntity(AttackEntityEvent event) {
        Entity target = event.getTarget();
        Level level = target.level();
        if (level.isClientSide || !(target instanceof HangingEntity || target instanceof ArmorStand)) {
            return;
        }
        if (!ProtectionService.isAllowed(event.getEntity(), level, target.blockPosition(), ProtectedAction.BREAK)) {
            event.setCanceled(true);
            ProtectionService.notifyDenied(event.getEntity(), level, target.blockPosition());
        }
    }

    private static void onBucket(FillBucketEvent event) {
        Level level = event.getLevel();
        HitResult hit = event.getTarget();
        if (level.isClientSide || !(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockPos next = pos.relative(blockHit.getDirection());
        Player player = event.getEntity();
        if (!ProtectionService.isAllowed(player, level, pos, ProtectedAction.USE_ITEM)
                || !ProtectionService.isAllowed(player, level, next, ProtectedAction.USE_ITEM)) {
            event.setCanceled(true);
            ProtectionService.notifyDenied(player, level, pos);
        }
    }

    private static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) {
            return;
        }
        if (!ProtectionService.isAllowed(event.getEntity(), level, event.getPos(), ProtectedAction.BREAK)) {
            event.setCanceled(true);
        }
    }

    private static void onExplosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide || !ProtectionService.protectsFromExplosions()) {
            return;
        }
        TerritoryIndex territory = ProtectionService.territory(level);
        if (territory == null) {
            return;
        }
        String dimension = Dimensions.id(level);
        if (territory.claimsIn(dimension).isEmpty()) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos -> isClaimed(territory, dimension, pos));
        event.getAffectedEntities().removeIf(entity -> (entity instanceof HangingEntity || entity instanceof ArmorStand)
                && isClaimed(territory, dimension, entity.blockPosition()));
    }

    private static boolean isClaimed(TerritoryIndex territory, String dimension, BlockPos pos) {
        UUID owner = territory.ownerOf(dimension, ChunkKey.of(pos.getX() >> 4, pos.getZ() >> 4));
        return owner != null;
    }

    /**
     * A piston may only move blocks within land of its own owner (or within unclaimed land), so pistons cannot
     * push blocks into a kingdom or pull them out of it from outside.
     */
    private static void onPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) {
            return;
        }
        TerritoryIndex territory = ProtectionService.territory(level);
        String dimension = Dimensions.id(level);
        if (territory == null || territory.claimsIn(dimension).isEmpty()) {
            return;
        }
        if (movesForeignBlocks(territory, dimension, event)) {
            event.setCanceled(true);
        }
    }

    public static boolean movesForeignBlocks(TerritoryIndex territory, String dimension, PistonEvent event) {
        UUID pistonOwner = ownerOf(territory, dimension, event.getPos());
        List<BlockPos> touched = new ArrayList<>();
        boolean extend = event.getPistonMoveType().isExtend;
        if (extend) {
            touched.add(event.getFaceOffsetPos());
        }
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver != null && resolver.resolve()) {
            Direction movement = extend ? event.getDirection() : event.getDirection().getOpposite();
            for (BlockPos pos : resolver.getToPush()) {
                touched.add(pos);
                touched.add(pos.relative(movement));
            }
            touched.addAll(resolver.getToDestroy());
        }
        for (BlockPos pos : touched) {
            UUID owner = ownerOf(territory, dimension, pos);
            if (owner != null && !owner.equals(pistonOwner)) {
                return true;
            }
        }
        return false;
    }

    private static UUID ownerOf(TerritoryIndex territory, String dimension, BlockPos pos) {
        return territory.ownerOf(dimension, ChunkKey.of(pos.getX() >> 4, pos.getZ() >> 4));
    }

    private static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        // Lava sets fire to nearby blocks through this event.
        if (event.getNewState().getBlock() instanceof BaseFireBlock && event.getLevel() instanceof Level level
                && ProtectionService.protectsFromFire(level, event.getPos())) {
            event.setNewState(event.getOriginalState());
        }
    }
}
