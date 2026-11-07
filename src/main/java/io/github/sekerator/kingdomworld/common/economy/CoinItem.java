package io.github.sekerator.kingdomworld.common.economy;

import io.github.sekerator.kingdomworld.common.network.data.CurrencyInfo;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.economy.Denomination;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A coin of a kingdom's currency. The stack's data names the currency (the kingdom's id) and the kind of coin, so
 * coins of one currency and kind stack. The client colors the coin in its kingdom's color and names the currency
 * in the tooltip; coins of a dissolved kingdom stay and are marked as old. Coins come only from the mint and from
 * accounts at a bank.
 */
public class CoinItem extends Item {
    static final String CURRENCY = "Currency";
    static final String DENOMINATION = "Denomination";

    public CoinItem(Properties properties) {
        super(properties);
    }

    /**
     * Coins of a currency.
     */
    public static ItemStack stack(UUID currency, Denomination denomination, int count) {
        ItemStack stack = new ItemStack(ModRegistries.COIN.get(), count);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(CURRENCY, currency);
        tag.putString(DENOMINATION, denomination.id());
        return stack;
    }

    public static Optional<UUID> currency(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return stack.is(ModRegistries.COIN.get()) && tag != null && tag.hasUUID(CURRENCY) ? Optional.of(tag.getUUID(CURRENCY)) : Optional.empty();
    }

    public static Optional<Denomination> denomination(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return stack.is(ModRegistries.COIN.get()) && tag != null ? Denomination.byId(tag.getString(DENOMINATION)) : Optional.empty();
    }

    /**
     * Whether the stack holds coins of exactly this currency and kind.
     */
    public static boolean is(ItemStack stack, UUID currency, Denomination denomination) {
        return currency(stack).filter(currency::equals).isPresent() && denomination(stack).filter(denomination::equals).isPresent();
    }

    @Override
    public Component getName(ItemStack stack) {
        return denomination(stack).<Component>map(MoneyTexts::coinName).orElseGet(() -> Component.translatable("item.kingdomworld.coin"));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Optional<UUID> currency = currency(stack);
        Optional<Denomination> denomination = denomination(stack);
        if (currency.isEmpty() || denomination.isEmpty()) {
            tooltip.add(Component.translatable("item.kingdomworld.coin.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        Optional<CurrencyInfo> info = CurrencyDirectory.get(currency.get());
        if (info.isEmpty()) {
            tooltip.add(Component.translatable(CurrencyDirectory.received() ? "item.kingdomworld.coin.unknown" : "item.kingdomworld.coin.loading")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.kingdomworld.coin.currency", MoneyTexts.currency(info.get().name(), info.get().kingdomName())
                .withStyle(style -> style.withColor(info.get().color()))).withStyle(ChatFormatting.GRAY));
        long worth = info.get().units().value(denomination.get());
        tooltip.add(Component.translatable("item.kingdomworld.coin.worth", MoneyTexts.coins(Denomination.COPPER, worth))
                .withStyle(ChatFormatting.GRAY));
        if (info.get().old()) {
            tooltip.add(Component.translatable("item.kingdomworld.coin.old", info.get().kingdomName()).withStyle(ChatFormatting.RED));
        }
    }
}
