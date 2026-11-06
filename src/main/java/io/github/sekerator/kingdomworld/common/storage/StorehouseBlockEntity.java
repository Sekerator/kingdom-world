package io.github.sekerator.kingdomworld.common.storage;

import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The block entity of a storehouse. It holds no items itself: it offers the storehouse's inventory, which lives in
 * the kingdom data, as an item handler capability, so hoppers, Create funnels, chutes and belts and pipes of other
 * mods put items in and take them out. While the kingdom data is read-only the handler accepts and gives nothing,
 * because the change could not be saved.
 */
public class StorehouseBlockEntity extends BlockEntity {
    private LazyOptional<IItemHandler> handler = LazyOptional.empty();

    public StorehouseBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.STOREHOUSE_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            KingdomSavedData.get(server.getServer()).storehouses().getOrCreate(position(server, worldPosition));
        }
    }

    static WorldPos position(ServerLevel level, BlockPos pos) {
        return new WorldPos(Dimensions.id(level), pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * The handler that every container access goes through; {@code null} on the client.
     */
    @Nullable
    public IItemHandlerModifiable handler() {
        return level instanceof ServerLevel server ? new Handler(server, worldPosition) : null;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER && level instanceof ServerLevel server && !remove) {
            if (!handler.isPresent()) {
                Handler created = new Handler(server, worldPosition);
                handler = LazyOptional.of(() -> created);
            }
            return handler.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        handler.invalidate();
        handler = LazyOptional.empty();
    }

    /**
     * Looks the inventory up on every access, so it is always the one in the kingdom data.
     */
    private static final class Handler implements IItemHandlerModifiable {
        private final ServerLevel level;
        private final WorldPos pos;

        private Handler(ServerLevel level, BlockPos pos) {
            this.level = level;
            this.pos = position(level, pos);
        }

        private KingdomSavedData data() {
            return KingdomSavedData.get(level.getServer());
        }

        private StorehouseInventory inventory() {
            return data().storehouses().getOrCreate(pos);
        }

        @Override
        public int getSlots() {
            return StorehouseInventory.SLOTS;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return inventory().getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (data().isReadOnly()) {
                return stack;
            }
            return inventory().insertItem(slot, stack, simulate);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (data().isReadOnly()) {
                return ItemStack.EMPTY;
            }
            return inventory().extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory().getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return !data().isReadOnly() && inventory().isItemValid(slot, stack);
        }

        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack stack) {
            if (!data().isReadOnly()) {
                inventory().setStackInSlot(slot, stack);
            }
        }
    }
}
