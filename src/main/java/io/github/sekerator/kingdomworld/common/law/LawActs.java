package io.github.sekerator.kingdomworld.common.law;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.siege.SiegeRepair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What a player who is not online does by the word of an operator: a stand-in that is in no list of players uses a block,
 * takes from a container, breaks a block and strikes a citizen as the player would, through the same events of the game.
 * A server without players is checked this way for what the law makes of a stranger. The stand-in stays where the operator
 * put it, is found by the alarms and counts as present, until the operator lets it leave.
 */
public final class LawActs {
    /**
     * What the use of a block came to.
     */
    public enum Use {
        /** Nothing changed and no window opened. */
        NOTHING,
        /** A door, a trapdoor or a gate is open now. */
        OPENED,
        /** It is shut now. */
        CLOSED,
        /** A window of a container opened. */
        WINDOW
    }

    /**
     * @param taken what was taken from the window that opened, or an empty stack
     */
    public record Used(Use use, BlockState block, ItemStack taken) {
    }

    /**
     * @param gone    whether the block left its place
     * @param dropped items that lie around the place and did not before
     * @param waiting blocks of the kingdom damaged in peace that wait to be put back
     */
    public record Broken(BlockState block, boolean gone, int dropped, int waiting) {
    }

    private static final Map<UUID, ServerPlayer> PRESENT = new HashMap<>();

    private LawActs() {
    }

    static void reset() {
        PRESENT.clear();
    }

    /**
     * The stand-in of a player who was put somewhere and has not left yet.
     */
    @Nullable
    public static ServerPlayer present(UUID player) {
        return PRESENT.get(player);
    }

    /**
     * Puts the stand-in of a player at a place; it stays there until it is put elsewhere or leaves.
     */
    public static ServerPlayer arrive(MinecraftServer server, ServerLevel level, GameProfile profile, BlockPos at) {
        ServerPlayer player = PRESENT.get(profile.getId());
        if (player == null || player.level() != level) {
            if (player != null) {
                leave(profile.getId());
            }
            player = LawStandIn.create(server, level, profile);
            PRESENT.put(profile.getId(), player);
        }
        player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        ArmyWork.markOnline(profile.getId(), true);
        LawAlarms.standInForCheck(player, true);
        return player;
    }

    /**
     * The stand-in leaves as a player who logs out: what it took or broke a moment ago is told to the law first.
     *
     * @return whether it was there
     */
    public static boolean leave(UUID id) {
        ServerPlayer player = PRESENT.remove(id);
        if (player == null) {
            return false;
        }
        LawContainers.forget(player);
        LawVandalism.reportNow(player);
        LawGuards.forget(id);
        ArmyWork.markOnline(id, false);
        LawAlarms.standInForCheck(player, false);
        return true;
    }

    /**
     * The stand-in clicks a block with the right button. If a window of a container opens, it takes one item from it and
     * shuts the window.
     */
    public static Used use(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        BlockState before = level.getBlockState(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        AbstractContainerMenu menu = player.containerMenu;
        if (menu != player.inventoryMenu) {
            ItemStack taken = ItemStack.EMPTY;
            for (Slot slot : menu.slots) {
                if (slot.container != player.getInventory() && slot.hasItem() && slot.mayPickup(player)) {
                    taken = slot.remove(1);
                    slot.setChanged();
                    break;
                }
            }
            ItemStack kept = taken.copy();
            if (!taken.isEmpty()) {
                player.getInventory().add(taken);
            }
            player.closeContainer();
            return new Used(Use.WINDOW, before, kept);
        }
        BlockState after = level.getBlockState(pos);
        if (before.hasProperty(BlockStateProperties.OPEN) && after.hasProperty(BlockStateProperties.OPEN)
                && before.getValue(BlockStateProperties.OPEN) != after.getValue(BlockStateProperties.OPEN)) {
            return new Used(after.getValue(BlockStateProperties.OPEN) ? Use.OPENED : Use.CLOSED, before, ItemStack.EMPTY);
        }
        return new Used(Use.NOTHING, before, ItemStack.EMPTY);
    }

    /**
     * The stand-in breaks a block as a player in survival mode would, and the law is told of it at once.
     */
    public static Broken breakBlock(ServerPlayer player, UUID kingdom, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        BlockState before = level.getBlockState(pos);
        AABB around = new AABB(pos).inflate(3.0);
        int lay = items(level, around);
        player.gameMode.destroyBlock(pos);
        LawVandalism.reportNow(player);
        boolean gone = !before.isAir() && !level.getBlockState(pos).is(before.getBlock());
        return new Broken(before, gone, Math.max(0, items(level, around) - lay), SiegeRepair.waiting(player.server, kingdom));
    }

    private static int items(ServerLevel level, AABB around) {
        int count = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, around)) {
            count += item.getItem().getCount();
        }
        return count;
    }

    /**
     * The stand-in strikes the body of a citizen with a bare hand.
     *
     * @return whether the blow landed
     */
    public static boolean strike(ServerPlayer player, CitizenEntity body) {
        return body.hurt(player.damageSources().playerAttack(player), 1.0f);
    }

    /**
     * How far the nearest soldier body of a kingdom stands from a player, in whole blocks; -1 when no soldier of it has
     * a body in the world of the player.
     */
    public static int nearestSoldier(UUID kingdom, ServerPlayer player) {
        double nearest = Double.MAX_VALUE;
        for (CitizenEntity body : CitizenBodies.bodies()) {
            if (kingdom.equals(body.kingdomId()) && LawWork.isSoldier(body) && body.level() == player.level() && body.isAlive()) {
                nearest = Math.min(nearest, body.distanceTo(player));
            }
        }
        return nearest == Double.MAX_VALUE ? -1 : (int) Math.round(nearest);
    }
}
