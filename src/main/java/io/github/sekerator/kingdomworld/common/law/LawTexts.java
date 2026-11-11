package io.github.sekerator.kingdomworld.common.law;

import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.Plurals;
import io.github.sekerator.kingdomworld.core.law.Crime;
import io.github.sekerator.kingdomworld.core.law.CrimeKind;
import io.github.sekerator.kingdomworld.core.law.Victims;
import io.github.sekerator.kingdomworld.core.law.Wanted;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

/**
 * Texts of the law: crimes, punishments, reputation, with the right plural forms. Used by the server for messages and by
 * the screens.
 */
public final class LawTexts {
    public static final String KEY = "kingdomworld.law.";

    private LawTexts() {
    }

    public static MutableComponent crime(CrimeKind kind) {
        return Component.translatable(KEY + "crime." + kind.id());
    }

    /**
     * One crime on a record: «кража на 25 м. (3 раза)», «нападение на Радомира», «убийство Радомира».
     */
    public static MutableComponent crime(Crime crime, CurrencyUnits units) {
        Component victim = victim(crime.victim());
        MutableComponent text = switch (crime.kind()) {
            case THEFT -> Victims.isTreasury(crime.victim())
                    ? Component.translatable(KEY + "record.theft_treasury", MoneyTexts.amount(crime.worth(), units))
                    : victim == null ? Component.translatable(KEY + "record.theft", MoneyTexts.amount(crime.worth(), units))
                    : Component.translatable(KEY + "record.theft_from", victim, MoneyTexts.amount(crime.worth(), units));
            case ASSAULT -> victim == null ? crime(CrimeKind.ASSAULT) : Component.translatable(KEY + "record.assault", victim);
            case MURDER -> victim == null ? crime(CrimeKind.MURDER) : Component.translatable(KEY + "record.murder", victim);
            case TRESPASS -> crime(CrimeKind.TRESPASS);
            case VANDALISM -> Component.translatable(KEY + "record.vandalism", MoneyTexts.amount(crime.worth(), units));
        };
        if (crime.count() > 1) {
            text = Component.translatable(KEY + "record.times", text, times(crime.count()));
        }
        return text;
    }

    /**
     * Who suffered, as a record keeps it: the name of a citizen in the alphabet of the reader, the name of a player as it
     * is, or {@code null} when the record names nobody.
     */
    @org.jetbrains.annotations.Nullable
    public static Component victim(String victim) {
        java.util.Optional<Victims.CitizenName> citizen = Victims.citizen(victim);
        if (citizen.isPresent()) {
            return io.github.sekerator.kingdomworld.common.citizen.CitizenTexts.name(citizen.get().nameSeed(),
                    citizen.get().female() ? io.github.sekerator.kingdomworld.core.citizen.Gender.FEMALE
                            : io.github.sekerator.kingdomworld.core.citizen.Gender.MALE);
        }
        return Victims.isName(victim) ? Component.literal(victim) : null;
    }

    public static MutableComponent times(long count) {
        return Component.translatable(KEY + "count.times." + Plurals.form(count).suffix(), count);
    }

    public static MutableComponent crimes(long count) {
        return Component.translatable(KEY + "count.crimes." + Plurals.form(count).suffix(), count);
    }

    public static MutableComponent days(long count) {
        return Component.translatable(KEY + "count.days." + Plurals.form(count).suffix(), count);
    }

    public static MutableComponent minutes(long count) {
        return Component.translatable(KEY + "count.minutes." + Plurals.form(count).suffix(), count);
    }

    /**
     * What awaits the player: the fine, or death for a murder.
     */
    public static MutableComponent punishment(boolean execution, long fine, CurrencyUnits units) {
        return execution ? Component.translatable(KEY + "punishment.execution")
                : Component.translatable(KEY + "punishment.fine", MoneyTexts.amount(fine, units));
    }

    /**
     * An amount in a few letters for a line above the hotbar: «12з 5с 3м», "12g 5s 3c".
     */
    public static MutableComponent shortAmount(long amount, CurrencyUnits units) {
        io.github.sekerator.kingdomworld.core.economy.Coins coins = io.github.sekerator.kingdomworld.core.economy.Coins.countOut(Math.max(0, amount),
                units, io.github.sekerator.kingdomworld.core.economy.Denomination.GOLD);
        MutableComponent text = null;
        for (io.github.sekerator.kingdomworld.core.economy.Denomination denomination
                : io.github.sekerator.kingdomworld.core.economy.Denomination.largestFirst()) {
            long count = coins.count(denomination);
            if (count == 0) {
                continue;
            }
            MutableComponent part = Component.translatable(KEY + "short." + denomination.id(), count);
            text = text == null ? part : text.append(" ").append(part);
        }
        return text == null ? Component.translatable(KEY + "short.copper", 0) : text;
    }

    /**
     * What awaits the player, in a few words for a line above the hotbar.
     */
    public static MutableComponent punishmentShort(boolean execution, long fine, CurrencyUnits units) {
        return execution ? Component.translatable(KEY + "punishment.execution")
                : Component.translatable(KEY + "punishment.fine", shortAmount(fine, units));
    }

    public static MutableComponent punishment(MinecraftServer server, Wanted wanted) {
        var state = LawWork.state(server);
        long fine = wanted.fine(LawSettings.rules(), state.law(wanted.kingdomId()).fineMultiplierPermille());
        return punishment(wanted.executable(), fine, units(server, wanted.kingdomId()));
    }

    /**
     * A reputation with its sign and a word: «+42 (уважают)».
     */
    public static MutableComponent reputation(int value) {
        String word = value >= 50 ? "respected" : value >= 10 ? "liked" : value > -10 ? "neutral" : value > -50 ? "disliked" : "hated";
        return Component.translatable(KEY + "reputation." + word, (value > 0 ? "+" : "") + value);
    }

    static CurrencyUnits units(MinecraftServer server, java.util.UUID kingdomId) {
        return io.github.sekerator.kingdomworld.common.trade.Purses.units(server, kingdomId);
    }
}
