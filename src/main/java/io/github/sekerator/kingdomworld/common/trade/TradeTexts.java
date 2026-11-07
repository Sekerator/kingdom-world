package io.github.sekerator.kingdomworld.common.trade;

import io.github.sekerator.kingdomworld.core.economy.Plurals;
import io.github.sekerator.kingdomworld.core.trade.ExchangeRates;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Texts of trade made of translation keys with the right plural forms in both languages: «5 предметов», "1 item",
 * «3 дня», and percents and rates written the same way everywhere.
 */
public final class TradeTexts {
    private static final String COUNT = "kingdomworld.count.";

    private TradeTexts() {
    }

    /**
     * A number of items: «21 предмет», "21 items".
     */
    public static MutableComponent items(long count) {
        return Component.translatable(COUNT + "items." + Plurals.form(count).suffix(), count);
    }

    /**
     * A number of game days: «2 дня».
     */
    public static MutableComponent days(long count) {
        return Component.translatable(COUNT + "days." + Plurals.form(count).suffix(), count);
    }

    /**
     * A number of minutes: «5 минут».
     */
    public static MutableComponent minutes(long count) {
        return Component.translatable(COUNT + "minutes." + Plurals.form(count).suffix(), count);
    }

    /**
     * Game ticks as whole minutes, at least one.
     */
    public static MutableComponent ticks(long ticks) {
        return minutes(Math.max(1, (ticks + 1_199) / 1_200));
    }

    /**
     * Thousandths as a percent with at most one decimal: «2,5%», "2.5%".
     */
    public static MutableComponent percent(int permille) {
        Component number = permille % 10 == 0 ? Component.literal(Integer.toString(permille / 10))
                : Component.translatable("kingdomworld.number.decimal", permille / 10, Integer.toString(Math.abs(permille % 10)));
        return Component.translatable("kingdomworld.number.percent", number);
    }

    /**
     * An exchange rate as a number with two decimals: «1,25», "1.25".
     */
    public static MutableComponent rate(long rate) {
        long hundredths = Math.round(rate * 100.0 / ExchangeRates.ONE);
        return Component.translatable("kingdomworld.number.decimal", hundredths / 100,
                String.format(java.util.Locale.ROOT, "%02d", hundredths % 100));
    }
}
