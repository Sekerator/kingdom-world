package io.github.sekerator.kingdomworld.common.guild;

import io.github.sekerator.kingdomworld.core.guild.Rank;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * A crystal left by a monster: its rank is in the stack's data, so crystals of one rank stack and crystals of different
 * ranks do not. It holds energy that grows steeply with the rank; the kingdom counts the energy of the crystals in its
 * storehouses, and mages dissolve it into the kingdom's energy. A crystal whose energy a spell of a player partly used
 * remembers what is left in its data. Crystals only come from monsters killed by players
 * and adventurers (and from operators); there is no recipe.
 */
public class MonsterCrystalItem extends Item {
    public static final String RANK = "Rank";
    /** Energy left in a crystal that gave part of it to a spell. */
    public static final String ENERGY = "Energy";
    /** Colors of the crystal by rank, F first. */
    private static final int[] COLORS = {0x9E9E9E, 0x5CB85C, 0x3FB6B2, 0x4A7BE0, 0x9B59D0, 0xE0B040, 0xE0702A, 0xD03838, 0xFF60D0};

    public MonsterCrystalItem(Properties properties) {
        super(properties);
    }

    /**
     * Crystals of a rank.
     */
    public static ItemStack stack(Rank rank, int count) {
        ItemStack stack = new ItemStack(GuildRegistries.MONSTER_CRYSTAL.get(), Math.max(1, count));
        stack.getOrCreateTag().putString(RANK, rank.id());
        return stack;
    }

    /**
     * The rank of a crystal; empty for anything else or a crystal without a readable rank.
     */
    public static Optional<Rank> rank(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof MonsterCrystalItem)) {
            return Optional.empty();
        }
        CompoundTag tag = stack.getTag();
        return tag == null ? Optional.empty() : Rank.byId(tag.getString(RANK));
    }

    public static boolean isCrystal(ItemStack stack) {
        return rank(stack).isPresent();
    }

    /**
     * The energy each crystal of the stack holds: what is left in a crystal that gave part of its energy to a spell, the
     * energy of its rank otherwise; 0 for anything else.
     */
    public static long energy(ItemStack stack) {
        Optional<Rank> rank = rank(stack);
        if (rank.isEmpty()) {
            return 0;
        }
        long full = GuildSettings.rules().crystalEnergy(rank.get());
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(ENERGY, net.minecraft.nbt.Tag.TAG_LONG)) {
            return Math.max(0, Math.min(full, tag.getLong(ENERGY)));
        }
        return full;
    }

    /**
     * Whether the crystals of the stack already gave part of their energy.
     */
    public static boolean isStarted(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return isCrystal(stack) && tag != null && tag.contains(ENERGY, net.minecraft.nbt.Tag.TAG_LONG);
    }

    /**
     * One crystal of a rank that holds only {@code left} of its energy; such crystals stack only with crystals that hold
     * the same.
     */
    public static ItemStack started(Rank rank, long left) {
        ItemStack stack = stack(rank, 1);
        stack.getOrCreateTag().putLong(ENERGY, Math.max(0, left));
        return stack;
    }

    /**
     * The tint of a crystal of a rank, for the item color on the client.
     */
    public static int color(Rank rank) {
        return COLORS[rank.ordinal()];
    }

    public static int color(ItemStack stack) {
        return rank(stack).map(MonsterCrystalItem::color).orElse(0x808080);
    }

    @Override
    public Component getName(ItemStack stack) {
        return rank(stack).<Component>map(rank -> Component.translatable("item.kingdomworld.monster_crystal.ranked", rank.label()))
                .orElseGet(() -> super.getName(stack));
    }

    @Override
    public Rarity getRarity(ItemStack stack) {
        Rank rank = rank(stack).orElse(Rank.F);
        if (rank.atLeast(Rank.S)) {
            return Rarity.EPIC;
        }
        if (rank.atLeast(Rank.B)) {
            return Rarity.RARE;
        }
        return rank.atLeast(Rank.D) ? Rarity.UNCOMMON : Rarity.COMMON;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return rank(stack).map(rank -> rank.atLeast(Rank.SS)).orElse(false);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Optional<Rank> rank = rank(stack);
        if (rank.isEmpty()) {
            tooltip.add(Component.translatable("item.kingdomworld.monster_crystal.unknown").withStyle(ChatFormatting.GRAY));
            return;
        }
        long energy = energy(stack);
        tooltip.add(Component.translatable("item.kingdomworld.monster_crystal.rank", rank.get().label())
                .withStyle(style -> style.withColor(color(rank.get()))));
        if (isStarted(stack)) {
            tooltip.add(Component.translatable("item.kingdomworld.monster_crystal.energy_left", energy,
                    GuildSettings.rules().crystalEnergy(rank.get())).withStyle(ChatFormatting.GRAY));
        } else if (stack.getCount() > 1) {
            tooltip.add(Component.translatable("item.kingdomworld.monster_crystal.energy_stack", energy, energy * stack.getCount())
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("item.kingdomworld.monster_crystal.energy", energy).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.kingdomworld.monster_crystal.tooltip").withStyle(ChatFormatting.DARK_GRAY));
    }
}
