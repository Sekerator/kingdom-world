package io.github.sekerator.kingdomworld.common.economy;

import io.github.sekerator.kingdomworld.core.economy.Denomination;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the mint strikes from metal: copper into copper coins, iron or silver (where a mod adds it) into silver coins,
 * gold into gold coins. Read from the server config as {@code item=coin:count} or {@code #tag=coin:count}.
 */
public final class MintRates {
    /** The rates of a new config. */
    public static final List<String> DEFAULTS = List.of("#forge:ingots/copper=copper:5", "#forge:ingots/iron=silver:2",
            "#forge:ingots/silver=silver:3", "#forge:ingots/gold=gold:1");

    /**
     * One rate: an item or a tag of items and the coins one of them gives.
     *
     * @param target an item id, or a tag id after {@code #}
     */
    public record Rate(String target, Denomination coin, int count) {
        public boolean isTag() {
            return target.startsWith("#");
        }
    }

    private final List<Rate> rates;
    private final List<Object> matchers;

    public MintRates(List<Rate> rates) {
        this.rates = List.copyOf(rates);
        this.matchers = new ArrayList<>();
        for (Rate rate : this.rates) {
            ResourceLocation id = ResourceLocation.tryParse(rate.isTag() ? rate.target().substring(1) : rate.target());
            if (id == null) {
                matchers.add(null);
            } else if (rate.isTag()) {
                matchers.add(TagKey.create(Registries.ITEM, id));
            } else {
                matchers.add(ForgeRegistries.ITEMS.getValue(id));
            }
        }
    }

    public static MintRates fromConfig(List<? extends String> texts) {
        List<Rate> rates = new ArrayList<>();
        for (String text : texts) {
            parse(text).ifPresent(rates::add);
        }
        return new MintRates(rates);
    }

    /**
     * Reads one rate of the config.
     */
    public static Optional<Rate> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String[] sides = text.strip().split("=");
        if (sides.length != 2) {
            return Optional.empty();
        }
        String target = sides[0].strip();
        String[] coin = sides[1].strip().split(":");
        if (coin.length != 2 || target.isEmpty()) {
            return Optional.empty();
        }
        String id = target.startsWith("#") ? target.substring(1) : target;
        if (ResourceLocation.tryParse(id) == null) {
            return Optional.empty();
        }
        Optional<Denomination> denomination = Denomination.byId(coin[0].strip());
        try {
            int count = Integer.parseInt(coin[1].strip());
            if (denomination.isEmpty() || count < 1 || count > 4_096) {
                return Optional.empty();
            }
            return Optional.of(new Rate(target, denomination.get(), count));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * The rate an item is struck at, or {@code null} if the mint does not take it.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public Rate rateOf(ItemStack stack) {
        if (stack.isEmpty() || stack.is(io.github.sekerator.kingdomworld.common.registry.ModRegistries.COIN.get())) {
            return null;
        }
        for (int i = 0; i < rates.size(); i++) {
            Object matcher = matchers.get(i);
            if (matcher instanceof TagKey<?> tag && stack.is((TagKey<Item>) tag)) {
                return rates.get(i);
            }
            if (matcher instanceof Item item && stack.is(item)) {
                return rates.get(i);
            }
        }
        return null;
    }

    /**
     * The first rate that strikes a kind of coin; old coins of that kind melt back at it.
     */
    @Nullable
    public Rate firstFor(Denomination coin) {
        for (Rate rate : rates) {
            if (rate.coin() == coin && metal(rate) != null) {
                return rate;
            }
        }
        return null;
    }

    /**
     * The metal of a rate: its item, or the first item of its tag; {@code null} if there is none.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public Item metal(Rate rate) {
        Object matcher = matchers.get(rates.indexOf(rate));
        if (matcher instanceof Item item) {
            return item == net.minecraft.world.item.Items.AIR ? null : item;
        }
        if (matcher instanceof TagKey<?> tag) {
            var tags = ForgeRegistries.ITEMS.tags();
            if (tags != null) {
                return tags.getTag((TagKey<Item>) tag).stream().findFirst().orElse(null);
            }
        }
        return null;
    }

    public boolean takes(ItemStack stack) {
        return rateOf(stack) != null;
    }

    public List<Rate> rates() {
        return rates;
    }
}
