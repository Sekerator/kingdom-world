package io.github.sekerator.kingdomworld.common.block;

import io.github.sekerator.kingdomworld.common.flag.FlagWork;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Which kingdom a flag in the world belongs to and how it came there. The server decides the kingdom and tells the clients,
 * which draw the flag that kingdom flies.
 */
public class KingdomFlagBlockEntity extends BlockEntity {
    static final String KINGDOM = "Kingdom";
    static final String ORIGIN = "Origin";

    /**
     * How a flag came to stand where it stands.
     */
    public enum Origin {
        /** A block of the template of a building. */
        TEMPLATE,
        /** Added to a building whose template has too few flags. */
        ADDED,
        /** One of the two flags beside a throne. */
        THRONE,
        /** On a city wall, beside a gate or along the wall. */
        WALL
    }

    @Nullable
    private UUID kingdom;
    private Origin origin = Origin.TEMPLATE;

    public KingdomFlagBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.KINGDOM_FLAG_BLOCK_ENTITY.get(), pos, state);
    }

    @Nullable
    public UUID kingdom() {
        return kingdom;
    }

    public Origin origin() {
        return origin;
    }

    /**
     * Gives the flag to a kingdom.
     *
     * @return whether that changed anything
     */
    public boolean setKingdom(@Nullable UUID newKingdom) {
        if (Objects.equals(kingdom, newKingdom)) {
            return false;
        }
        kingdom = newKingdom;
        setChanged();
        return true;
    }

    public void setOrigin(Origin newOrigin) {
        if (origin != newOrigin) {
            origin = Objects.requireNonNull(newOrigin, "origin");
            setChanged();
        }
    }

    /**
     * Tells the clients that see the flag whose it is now.
     */
    public void sendUpdate() {
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        kingdom = tag.hasUUID(KINGDOM) ? tag.getUUID(KINGDOM) : null;
        if (tag.contains(ORIGIN, Tag.TAG_BYTE)) {
            int value = tag.getByte(ORIGIN);
            origin = value >= 0 && value < Origin.values().length ? Origin.values()[value] : Origin.TEMPLATE;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (kingdom != null) {
            tag.putUUID(KINGDOM, kingdom);
        }
        tag.putByte(ORIGIN, (byte) origin.ordinal());
    }

    /**
     * The clients learn only the kingdom.
     */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        if (kingdom != null) {
            tag.putUUID(KINGDOM, kingdom);
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            FlagWork.loaded(serverLevel, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel serverLevel) {
            FlagWork.unloaded(serverLevel, worldPosition);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel serverLevel) {
            FlagWork.unloaded(serverLevel, worldPosition);
        }
    }

    /**
     * The cloth of a flag reaches two blocks: up from a standing flag, down from a flag on a wall.
     */
    @Override
    public AABB getRenderBoundingBox() {
        BlockPos pos = worldPosition;
        return getBlockState().getBlock() instanceof KingdomWallFlagBlock
                ? new AABB(pos.getX(), pos.getY() - 1, pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1)
                : new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
    }
}
