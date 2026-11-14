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

import java.util.List;

/**
 * The block entity of a storehouse. It holds no items itself: it offers the storehouse's inventory, which lives in
 * the kingdom data, as an item handler capability, so hoppers, Create funnels, chutes and belts and pipes of other
 * mods put items in and take them out. A half of a double storehouse offers both halves, as a large chest does. While
 * the kingdom data is read-only the handler accepts and gives nothing, because the change could not be saved.
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
     * The handler that every container access goes through: this storehouse, or both halves of a double one; {@code null}
     * on the client.
     */
    @Nullable
    public IItemHandlerModifiable handler() {
        return level instanceof ServerLevel server ? StorehouseBlock.handler(server, StorehouseBlock.halves(server, worldPosition)) : null;
    }

    /**
     * The handler of this storehouse alone, the one half of a double storehouse; {@code null} on the client. What empties or
     * looks into the block itself (to break or replace it) takes this, not the capability, which shows both halves.
     */
    @Nullable
    public IItemHandlerModifiable ownHandler() {
        return level instanceof ServerLevel server ? new Handler(server, worldPosition) : null;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER && level instanceof ServerLevel server && !remove) {
            if (!handler.isPresent()) {
                Joined created = new Joined(server, worldPosition);
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
     * What hoppers and pipes see: this storehouse, or both halves of a double one in the order of
     * {@link StorehouseBlock#halves}, the slots of the half of type RIGHT first. Which it is is looked up once a tick and again
     * whenever a storehouse comes or goes, so a hopper that holds the handler sees the storehouses join and part at once.
     */
    private static final class Joined implements IItemHandlerModifiable {
        private static final IItemHandlerModifiable[] NONE = new IItemHandlerModifiable[0];

        private final ServerLevel level;
        private final BlockPos pos;
        private long tick = Long.MIN_VALUE;
        @Nullable
        private StorehouseRegistry registry;
        private long version;
        private IItemHandlerModifiable[] parts = NONE;

        private Joined(ServerLevel level, BlockPos pos) {
            this.level = level;
            this.pos = pos.immutable();
        }

        private IItemHandlerModifiable[] parts() {
            long now = level.getGameTime();
            StorehouseRegistry storehouses = KingdomSavedData.get(level.getServer()).storehouses();
            if (now != tick || storehouses != registry || storehouses.version() != version) {
                tick = now;
                registry = storehouses;
                version = storehouses.version();
                List<BlockPos> halves = StorehouseBlock.halves(level, pos);
                IItemHandlerModifiable[] found = new IItemHandlerModifiable[halves.size()];
                for (int i = 0; i < found.length; i++) {
                    found[i] = new Handler(level, halves.get(i));
                }
                parts = found;
            }
            return parts;
        }

        @Nullable
        private IItemHandlerModifiable part(int slot) {
            IItemHandlerModifiable[] all = parts();
            int index = slot / StorehouseInventory.SLOTS;
            return slot >= 0 && index < all.length ? all[index] : null;
        }

        @Override
        public int getSlots() {
            return parts().length * StorehouseInventory.SLOTS;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            IItemHandlerModifiable part = part(slot);
            return part == null ? ItemStack.EMPTY : part.getStackInSlot(slot % StorehouseInventory.SLOTS);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            IItemHandlerModifiable part = part(slot);
            return part == null ? stack : part.insertItem(slot % StorehouseInventory.SLOTS, stack, simulate);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            IItemHandlerModifiable part = part(slot);
            return part == null ? ItemStack.EMPTY : part.extractItem(slot % StorehouseInventory.SLOTS, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            IItemHandlerModifiable part = part(slot);
            return part == null ? 0 : part.getSlotLimit(slot % StorehouseInventory.SLOTS);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            IItemHandlerModifiable part = part(slot);
            return part != null && part.isItemValid(slot % StorehouseInventory.SLOTS, stack);
        }

        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack stack) {
            IItemHandlerModifiable part = part(slot);
            if (part != null) {
                part.setStackInSlot(slot % StorehouseInventory.SLOTS, stack);
            }
        }
    }

    /**
     * One storehouse. Looks the inventory up on every access, so it is always the one in the kingdom data.
     */
    private static final class Handler implements IItemHandlerModifiable {
        private final ServerLevel level;
        private final WorldPos pos;
        private final BlockPos blockPos;

        private Handler(ServerLevel level, BlockPos pos) {
            this.level = level;
            this.pos = position(level, pos);
            this.blockPos = pos.immutable();
        }

        private KingdomSavedData data() {
            return KingdomSavedData.get(level.getServer());
        }

        /**
         * The inventory, or {@code null} once the storehouse is gone: a handler another mod kept after that makes no new
         * empty record, takes nothing and gives nothing.
         */
        @Nullable
        private StorehouseInventory inventory() {
            java.util.Optional<StorehouseInventory> found = data().storehouses().get(pos);
            if (found.isPresent()) {
                return found.get();
            }
            return level.isLoaded(blockPos) && level.getBlockEntity(blockPos) instanceof StorehouseBlockEntity storehouse && !storehouse.isRemoved()
                    ? data().storehouses().getOrCreate(pos) : null;
        }

        @Override
        public int getSlots() {
            return StorehouseInventory.SLOTS;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            StorehouseInventory inventory = inventory();
            return inventory == null ? ItemStack.EMPTY : inventory.getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            StorehouseInventory inventory = data().isReadOnly() ? null : inventory();
            return inventory == null ? stack : inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            StorehouseInventory inventory = data().isReadOnly() ? null : inventory();
            return inventory == null ? ItemStack.EMPTY : inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            StorehouseInventory inventory = inventory();
            return inventory == null ? 0 : inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            StorehouseInventory inventory = data().isReadOnly() ? null : inventory();
            return inventory != null && inventory.isItemValid(slot, stack);
        }

        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack stack) {
            StorehouseInventory inventory = data().isReadOnly() ? null : inventory();
            if (inventory != null) {
                inventory.setStackInSlot(slot, stack);
            }
        }
    }
}
