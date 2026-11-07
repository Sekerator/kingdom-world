package io.github.sekerator.kingdomworld.common.companion;

import io.github.sekerator.kingdomworld.common.network.data.CompanionViews;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * The pack of a companion next to the owner's inventory. On the server the slots are the pack kept with the companion's
 * record, so what is moved here is moved there and nowhere else; the window closes when the companion dies, goes away or
 * changes hands. On the client the slots mirror it, and the view carries the companion's state for the screen.
 */
public class CompanionMenu extends AbstractContainerMenu {
    /** Pixels of one slot. */
    public static final int SLOT = 18;
    /** Where the pack starts in the window. */
    public static final int PACK_TOP = 18;

    private final UUID companionId;
    private final Container pack;
    private final int usable;
    private CompanionViews.CompanionView view;

    /**
     * The menu on the server.
     */
    public CompanionMenu(int id, Inventory inventory, UUID companionId, Container pack, int usable, CompanionViews.CompanionView view) {
        super(CompanionRegistries.MENU.get(), id);
        this.companionId = companionId;
        this.pack = pack;
        this.usable = Math.max(1, Math.min(pack.getContainerSize(), usable));
        this.view = view;
        addSlots(inventory);
    }

    /**
     * The menu on the client, as the server described it.
     */
    public static CompanionMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buf) {
        UUID companion = buf.readUUID();
        int usable = Math.max(1, Math.min(io.github.sekerator.kingdomworld.core.companion.Skills.MAX_SLOTS, buf.readVarInt()));
        CompanionViews.CompanionView view = CompanionViews.CompanionView.decode(buf);
        return new CompanionMenu(id, inventory, companion, new SimpleContainer(usable), usable, view);
    }

    private void addSlots(Inventory inventory) {
        int rows = rows();
        for (int i = 0; i < usable; i++) {
            addSlot(new Slot(pack, i, 8 + (i % 9) * SLOT, PACK_TOP + (i / 9) * SLOT));
        }
        int top = inventoryTop(rows);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * SLOT, top + row * SLOT));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * SLOT, top + 58));
        }
    }

    public int rows() {
        return (usable + 8) / 9;
    }

    /**
     * Where the owner's inventory starts in the window.
     */
    public static int inventoryTop(int rows) {
        return PACK_TOP + rows * SLOT + 13;
    }

    public UUID companionId() {
        return companionId;
    }

    public int usable() {
        return usable;
    }

    public CompanionViews.CompanionView view() {
        return view;
    }

    public void setView(CompanionViews.CompanionView value) {
        if (value.id().equals(companionId)) {
            view = value;
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index < usable) {
            if (!moveItemStackTo(stack, usable, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, usable, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return before;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        return valid(serverPlayer);
    }

    private boolean valid(ServerPlayer player) {
        MinecraftServer server = player.server;
        Optional<io.github.sekerator.kingdomworld.core.companion.Companion> companion = CompanionWork.state(server).companion(companionId);
        if (companion.isEmpty() || !companion.get().owner().equals(player.getUUID()) || CompanionWork.isReadOnly(server)) {
            return false;
        }
        return CompanionBodies.body(companionId).map(body -> body.level() == player.level() && body.distanceToSqr(player) < 8.0 * 8.0)
                .orElse(false);
    }

    /**
     * Closes the window of a companion for whoever has it open, before the companion goes away.
     */
    static void closeFor(MinecraftServer server, UUID companionId) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof CompanionMenu menu && menu.companionId.equals(companionId)) {
                player.closeContainer();
            }
        }
    }
}
