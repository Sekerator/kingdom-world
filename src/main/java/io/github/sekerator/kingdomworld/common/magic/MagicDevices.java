package io.github.sekerator.kingdomworld.common.magic;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.magic.MagicDevice;
import io.github.sekerator.kingdomworld.core.magic.MagicState;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The blocks of magic on the land of kingdoms: placing one on kingdom land records it for that kingdom, breaking it forgets
 * it, and from time to time the loaded ones are checked again (a block gone, or land that changed hands).
 */
public final class MagicDevices {
    private MagicDevices() {
    }

    public static WorldPos at(Level level, BlockPos pos) {
        return new WorldPos(Dimensions.id(level), pos.getX(), pos.getY(), pos.getZ());
    }

    public static BlockPos blockPos(WorldPos pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }

    /**
     * Whether a player may place a block of magic here: generators and gates spend the kingdom's energy for others, so they
     * need kingdom land and the right to run the mage tower; a gate also needs room under the kingdom's limit. Says why not.
     */
    public static boolean mayPlace(ServerPlayer player, BlockPos pos, MagicDevice.Kind kind) {
        if (kind != MagicDevice.Kind.GENERATOR && kind != MagicDevice.Kind.GATE) {
            return true;
        }
        UUID owner = KingdomWorldApi.ownerAt(player.level(), pos);
        KingdomError refusal = null;
        if (owner == null) {
            refusal = KingdomError.NOT_OWNED;
        } else if (!KingdomWorldApi.state(player.server).membership().hasPermission(player.getUUID(), owner, Permission.MANAGE_MAGIC)) {
            refusal = KingdomError.NO_PERMISSION;
        } else if (kind == MagicDevice.Kind.GATE) {
            Outcome room = MagicWork.state(player.server).checkDevice(kind, owner, MagicSettings.rules());
            refusal = room.ok() ? null : room.error();
        }
        if (refusal != null) {
            if (player.connection != null) {
                player.displayClientMessage(KingdomActions.errorMessage(refusal), true);
            }
            return false;
        }
        return true;
    }

    /**
     * Whether a kind of device spends the kingdom's energy for others, so that only a member with the right to run the mage
     * tower may set it up.
     */
    public static boolean needsRight(MagicDevice.Kind kind) {
        return kind == MagicDevice.Kind.GENERATOR || kind == MagicDevice.Kind.GATE;
    }

    /**
     * A block of magic was placed by any means (a player, a machine, a command): on kingdom land a focus or an accumulator
     * now belongs to that kingdom. Generators and gates are only set up by {@link #placedBy}.
     */
    public static void placed(Level level, BlockPos pos, MagicDevice.Kind kind) {
        if (!needsRight(kind)) {
            register(level, pos, kind);
        }
    }

    /**
     * A player placed a block of magic. A generator or a gate is set up only by a real player who holds the right to run the
     * mage tower of the kingdom whose land it stands on; placed by a machine, a command, a player without the right or on
     * land of nobody, it stays an inert block until such a player places it again.
     */
    public static void placedBy(Level level, BlockPos pos, MagicDevice.Kind kind, @org.jetbrains.annotations.Nullable LivingEntity placer) {
        if (!needsRight(kind) || !(level instanceof ServerLevel serverLevel) || !(placer instanceof ServerPlayer player)
                || player instanceof FakePlayer) {
            return;
        }
        UUID owner = KingdomWorldApi.ownerAt(level, pos);
        if (owner != null && KingdomWorldApi.state(serverLevel.getServer()).membership().hasPermission(player.getUUID(), owner,
                Permission.MANAGE_MAGIC)) {
            register(level, pos, kind);
        }
    }

    private static void register(Level level, BlockPos pos, MagicDevice.Kind kind) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        MinecraftServer server = serverLevel.getServer();
        UUID owner = KingdomWorldApi.ownerAt(level, pos);
        if (owner == null || MagicWork.isReadOnly(server)) {
            return;
        }
        MagicState state = MagicWork.state(server);
        if (kind == MagicDevice.Kind.GATE && state.device(at(level, pos)).isEmpty() && !state.checkDevice(kind, owner, MagicSettings.rules()).ok()) {
            return;
        }
        state.putDevice(kind, owner, at(level, pos));
    }

    /**
     * The device of a kind set up at a place for the kingdom that owns the land there now; empty for a block nobody set up,
     * one of another kind, or one whose land changed hands.
     */
    public static Optional<MagicDevice> working(Level level, BlockPos pos, MagicDevice.Kind kind) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return Optional.empty();
        }
        UUID owner = KingdomWorldApi.ownerAt(level, pos);
        if (owner == null) {
            return Optional.empty();
        }
        return MagicWork.state(serverLevel.getServer()).device(at(level, pos))
                .filter(device -> device.kind() == kind && device.kingdomId().equals(owner));
    }

    /**
     * A block of magic was broken or replaced.
     */
    public static void removed(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel && !MagicWork.isReadOnly(serverLevel.getServer())) {
            MagicWork.state(serverLevel.getServer()).removeDevice(at(level, pos));
        }
    }

    /**
     * Looks at the loaded devices again: a block that is gone is forgotten, land that is nobody's frees its devices, land
     * that changed hands takes its foci and accumulators with it, while its generators and gates stop until a member of the
     * new kingdom with the right sets them up again.
     */
    public static void check(MinecraftServer server) {
        MagicState state = MagicWork.state(server);
        for (MagicDevice device : List.copyOf(state.devices())) {
            Optional<ServerLevel> level = Dimensions.key(device.pos().dimension()).map(server::getLevel);
            if (level.isEmpty()) {
                continue;
            }
            BlockPos pos = blockPos(device.pos());
            if (!level.get().isLoaded(pos)) {
                continue;
            }
            if (!(level.get().getBlockState(pos).getBlock() instanceof MagicDeviceBlock block) || block.kind() != device.kind()) {
                state.removeDevice(device.pos());
                continue;
            }
            UUID owner = KingdomWorldApi.ownerAt(level.get(), pos);
            if (owner == null) {
                state.removeDevice(device.pos());
            } else if (!owner.equals(device.kingdomId())) {
                state.removeDevice(device.pos());
                if (!needsRight(device.kind())) {
                    state.putDevice(device.kind(), owner, device.pos());
                }
            }
        }
    }
}
