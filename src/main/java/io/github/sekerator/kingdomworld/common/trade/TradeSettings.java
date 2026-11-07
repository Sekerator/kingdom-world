package io.github.sekerator.kingdomworld.common.trade;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.trade.ExchangeRates;
import io.github.sekerator.kingdomworld.core.trade.TradeRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * The trade rules of the running server, built from the server config and refreshed once per second like the other
 * rules; game tests may replace them. Also knows which items never come from abroad.
 */
public final class TradeSettings {
    /** Items that never come from abroad in a new config: things of bosses, the rarest finds and items of the mod. */
    public static final List<String> DEFAULT_DENIED = List.of("minecraft:nether_star", "minecraft:elytra", "minecraft:dragon_egg",
            "minecraft:dragon_head", "minecraft:dragon_breath", "minecraft:totem_of_undying", "minecraft:heart_of_the_sea",
            "minecraft:enchanted_golden_apple", "minecraft:beacon", "minecraft:shulker_shell", "minecraft:netherite_scrap",
            "minecraft:ancient_debris", "minecraft:wither_skeleton_skull", "minecraft:echo_shard", "minecraft:sniffer_egg",
            "minecraft:trident", "minecraft:spawner", "minecraft:bedrock", "minecraft:command_block", "minecraft:barrier",
            "minecraft:structure_block", "kingdomworld:coin", "kingdomworld:founder_crown", "kingdomworld:monster_crystal");

    private static TradeRules override;
    private static TradeRules cached;
    private static List<Object> denied;

    private TradeSettings() {
    }

    public static TradeRules rules() {
        if (override != null) {
            return override;
        }
        TradeRules rules = cached;
        if (rules == null) {
            rules = fromConfig();
            cached = rules;
        }
        return rules;
    }

    public static void refresh() {
        cached = fromConfig();
        denied = null;
    }

    public static void reset() {
        cached = null;
        override = null;
        denied = null;
        waitOverride = -1;
    }

    /**
     * Replaces the configured rules, for game tests; {@code null} restores the config.
     */
    public static void override(TradeRules rules) {
        override = rules;
    }

    private static TradeRules fromConfig() {
        KingdomWorldConfig.Server c = KingdomWorldConfig.SERVER;
        TradeRules d = TradeRules.DEFAULT;
        try {
            double export = c.exportFactor.get();
            double imports = Math.max(c.importFactor.get(), export + 0.01);
            int minPrice = c.minPricePercent.get();
            int maxPrice = Math.max(minPrice, c.maxPricePercent.get());
            int maxTax = c.maxSalesTaxPercent.get();
            int maxFee = c.maxExchangeFeePercent.get();
            long minRate = Math.max(1, Math.round(c.minRate.get() * ExchangeRates.ONE));
            long maxRate = Math.max(minRate, Math.round(c.maxRate.get() * ExchangeRates.ONE));
            return new TradeRules(export, imports, c.exportPerMerchant.get(), c.exportExperienceBonus.get(), c.exportMaxIncomePerDay.get(),
                    c.importMaxPerDay.get(), c.importDelayMinutes.get() * 1200L, c.demandElasticity.get(), minPrice, maxPrice,
                    c.priceStockTarget.get(), c.spreadPercent.get(), Math.min(maxTax, c.defaultSalesTaxPercent.get()), maxTax,
                    c.maxLotsPerPlayer.get(), c.maxMarketEntries.get(), Math.min(maxFee, c.defaultExchangeFeePercent.get()), maxFee, minRate,
                    maxRate, c.rateSmoothingPercent.get(), c.rateProductionDays.get(), c.rateMinSupply.get(), c.caravanBlocksPerMinute.get(),
                    c.caravanLossChance.get(), c.proposalDays.get(), c.maxDealsPerKingdom.get(), c.maxDealPeriodDays.get());
        } catch (IllegalArgumentException e) {
            return d;
        }
    }

    /** Replaces the configured wait of caravans, for game tests; negative uses the config. */
    private static long waitOverride = -1;

    /**
     * Game ticks a caravan waits for room in the storehouses of the buyer before its deal is closed.
     */
    public static long caravanMaxWaitTicks() {
        if (waitOverride >= 0) {
            return waitOverride;
        }
        try {
            return KingdomWorldConfig.SERVER.caravanMaxWaitMinutes.get() * 1_200L;
        } catch (IllegalStateException e) {
            return 40 * 1_200L;
        }
    }

    /**
     * Replaces the wait of caravans, for game tests; a negative value restores the config.
     */
    public static void overrideCaravanWait(long ticks) {
        waitOverride = ticks;
    }

    /**
     * Whether an item never comes from abroad: listed in the config, a spawn egg or a coin.
     */
    @SuppressWarnings("unchecked")
    public static boolean importDenied(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() instanceof SpawnEggItem || GoodsStacks.untradeable(stack)) {
            return true;
        }
        List<Object> matchers = denied;
        if (matchers == null) {
            matchers = new ArrayList<>();
            for (String text : KingdomWorldConfig.SERVER.importDenied.get()) {
                boolean tag = text.startsWith("#");
                ResourceLocation id = ResourceLocation.tryParse(tag ? text.substring(1) : text);
                if (id != null) {
                    matchers.add(tag ? TagKey.create(Registries.ITEM, id) : ForgeRegistries.ITEMS.getValue(id));
                }
            }
            denied = matchers;
        }
        for (Object matcher : matchers) {
            if (matcher instanceof TagKey<?> tag && stack.is((TagKey<Item>) tag)) {
                return true;
            }
            if (matcher instanceof Item item && stack.is(item)) {
                return true;
            }
        }
        return false;
    }
}
