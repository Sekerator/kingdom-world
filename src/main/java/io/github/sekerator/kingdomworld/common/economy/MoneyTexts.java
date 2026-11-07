package io.github.sekerator.kingdomworld.common.economy;

import io.github.sekerator.kingdomworld.core.economy.Coins;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.Denomination;
import io.github.sekerator.kingdomworld.core.economy.Plurals;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Texts about money, made of translation keys so that every player reads them in their own language with the right
 * plural forms: «1 золотой 2 серебряника 5 медяков», "1 gold coin 2 silver coins 5 copper coins". The key of a number
 * of coins ends in its {@link Plurals.Form}, which serves Russian and English at once.
 */
public final class MoneyTexts {
    private static final String MONEY = "kingdomworld.money.";

    private MoneyTexts() {
    }

    /**
     * A number of coins of one kind: «2 золотых».
     */
    public static MutableComponent coins(Denomination denomination, long count) {
        return Component.translatable(MONEY + denomination.id() + "." + Plurals.form(count).suffix(), count);
    }

    /**
     * An amount counted out in the fewest coins: «1 золотой 2 серебряника 5 медяков»; nothing at all is «0 медяков».
     */
    public static MutableComponent amount(long amount, CurrencyUnits units) {
        return coins(Coins.countOut(Math.max(0, amount), units, Denomination.GOLD));
    }

    /**
     * Coins of each kind, the largest first, without the kinds there are none of.
     */
    public static MutableComponent coins(Coins coins) {
        MutableComponent text = null;
        for (Denomination denomination : Denomination.largestFirst()) {
            long count = coins.count(denomination);
            if (count == 0) {
                continue;
            }
            MutableComponent part = coins(denomination, count);
            text = text == null ? part : Component.translatable(MONEY + "join", text, part);
        }
        return text == null ? coins(Denomination.COPPER, 0) : text;
    }

    /**
     * The name of a currency: the one its king gave it, or the one derived from its kingdom («крона королевства Эльдария»).
     */
    public static MutableComponent currency(String name, String kingdomName) {
        return name == null || name.isEmpty() ? Component.translatable("kingdomworld.currency.default", kingdomName) : Component.literal(name);
    }

    /**
     * The short name of a currency for chat: the one its king gave it, or the name of its kingdom; hovering shows the full
     * name, so that «крона королевства» does not stand in front of every long name.
     */
    public static MutableComponent currencyShort(String name, String kingdomName) {
        MutableComponent full = currency(name, kingdomName);
        return Component.literal(name == null || name.isEmpty() ? kingdomName : name)
                .withStyle(style -> style.withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                        net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, full)));
    }

    /**
     * The name of a coin: «Золотой», "Gold coin".
     */
    public static MutableComponent coinName(Denomination denomination) {
        return Component.translatable("item.kingdomworld.coin." + denomination.id());
    }
}
