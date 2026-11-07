package io.github.sekerator.kingdomworld.common.life;

import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.Plurals;
import io.github.sekerator.kingdomworld.core.life.Rumor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;
import java.util.function.Function;

/**
 * Texts of the life of cities: counts in the right form of both languages and rumors as players read them.
 */
public final class LifeTexts {
    public static final String KEY = "kingdomworld.life.";

    private LifeTexts() {
    }

    private static MutableComponent count(String what, long count) {
        return Component.translatable(KEY + "count." + what + "." + Plurals.form(count).suffix(), count);
    }

    public static MutableComponent days(long count) {
        return count("days", count);
    }

    public static MutableComponent citizens(long count) {
        return count("citizens", count);
    }

    public static MutableComponent kingdoms(long count) {
        return count("kingdoms", count);
    }

    public static MutableComponent guests(long count) {
        return count("guests", count);
    }

    public static MutableComponent hours(long count) {
        return count("hours", count);
    }

    /**
     * A rumor as players read it.
     *
     * @param units the coins of a currency by its id, for amounts of money
     */
    public static Component rumor(Rumor rumor, Function<UUID, CurrencyUnits> units) {
        Object[] args = new Object[rumor.args().size()];
        for (int i = 0; i < args.length; i++) {
            Rumor.Arg arg = rumor.args().get(i);
            args[i] = switch (arg.kind()) {
                case PLAIN -> arg.text();
                case KEY -> Component.translatable(arg.text());
                case ITEM -> item(arg.text());
                case MONEY -> money(arg.text(), units);
            };
        }
        return Component.translatable(rumor.key(), args);
    }

    private static Object item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        Item item = key == null ? null : ForgeRegistries.ITEMS.getValue(key);
        return item == null ? id : Component.translatable(item.getDescriptionId());
    }

    private static Object money(String text, Function<UUID, CurrencyUnits> units) {
        int bar = text.indexOf('|');
        try {
            long amount = Long.parseLong(text.substring(0, bar));
            return MoneyTexts.amount(amount, units.apply(UUID.fromString(text.substring(bar + 1))));
        } catch (RuntimeException e) {
            return text;
        }
    }
}
