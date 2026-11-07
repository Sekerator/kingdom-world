package io.github.sekerator.kingdomworld.common.magic;

import io.github.sekerator.kingdomworld.core.magic.PlayerSpell;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A staff with slots for spells. Spells a player learned are put into the slots at a magic focus; using the staff casts
 * the chosen spell, using it while sneaking chooses the next one. The slots and the choice are in the stack's data.
 */
public class StaffItem extends Item {
    public static final String SPELLS = "Spells";
    public static final String SELECTED = "Selected";

    public StaffItem(Properties properties) {
        super(properties);
    }

    /**
     * The spells in the slots of a staff; an empty slot is empty.
     */
    public static List<Optional<PlayerSpell>> slots(ItemStack stack, int count) {
        List<Optional<PlayerSpell>> slots = new ArrayList<>();
        CompoundTag tag = stack.getTag();
        ListTag list = tag == null ? new ListTag() : tag.getList(SPELLS, Tag.TAG_STRING);
        for (int i = 0; i < count; i++) {
            slots.add(i < list.size() ? PlayerSpell.byId(list.getString(i)) : Optional.empty());
        }
        return slots;
    }

    /**
     * Puts a spell into a slot of a staff, or clears the slot.
     */
    public static void setSlot(ItemStack stack, int slot, @Nullable PlayerSpell spell, int count) {
        List<Optional<PlayerSpell>> slots = slots(stack, count);
        if (slot < 0 || slot >= count) {
            return;
        }
        slots.set(slot, Optional.ofNullable(spell));
        ListTag list = new ListTag();
        for (Optional<PlayerSpell> entry : slots) {
            list.add(StringTag.valueOf(entry.map(PlayerSpell::id).orElse("")));
        }
        stack.getOrCreateTag().put(SPELLS, list);
        if (spell != null && slots.stream().filter(Optional::isPresent).count() == 1) {
            stack.getOrCreateTag().putInt(SELECTED, slot);
        }
    }

    public static int selectedSlot(ItemStack stack, int count) {
        CompoundTag tag = stack.getTag();
        int selected = tag == null ? 0 : tag.getInt(SELECTED);
        return Math.max(0, Math.min(count - 1, selected));
    }

    /**
     * The chosen spell of a staff, if its slot holds one.
     */
    public static Optional<PlayerSpell> selected(ItemStack stack, int count) {
        return slots(stack, count).get(selectedSlot(stack, count));
    }

    /**
     * Chooses the next slot that holds a spell.
     *
     * @return the slot chosen, or -1 if the staff holds no spell
     */
    public static int selectNext(ItemStack stack, int count) {
        List<Optional<PlayerSpell>> slots = slots(stack, count);
        int start = selectedSlot(stack, count);
        for (int step = 1; step <= count; step++) {
            int slot = (start + step) % count;
            if (slots.get(slot).isPresent()) {
                stack.getOrCreateTag().putInt(SELECTED, slot);
                return slot;
            }
        }
        return -1;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                PlayerSpells.select(serverPlayer, stack);
            } else {
                PlayerSpells.castWithStaff(serverPlayer, stack);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int count = MagicSettings.rules().staffSlots;
        List<Optional<PlayerSpell>> slots = slots(stack, count);
        int selected = selectedSlot(stack, count);
        for (int i = 0; i < slots.size(); i++) {
            Component name = slots.get(i).<Component>map(MagicTexts::spell).orElseGet(() -> Component.translatable("item.kingdomworld.staff.empty"));
            tooltip.add(Component.translatable(i == selected && slots.get(i).isPresent() ? "item.kingdomworld.staff.slot_selected"
                    : "item.kingdomworld.staff.slot", i + 1, name).withStyle(i == selected ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.kingdomworld.staff.tooltip").withStyle(ChatFormatting.DARK_GRAY));
    }
}
