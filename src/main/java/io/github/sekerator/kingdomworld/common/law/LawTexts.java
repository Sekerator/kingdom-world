package io.github.sekerator.kingdomworld.common.law;

import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.Plurals;
import io.github.sekerator.kingdomworld.core.law.Crime;
import io.github.sekerator.kingdomworld.core.law.CrimeKind;
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
        MutableComponent text = switch (crime.kind()) {
            case THEFT -> Component.translatable(KEY + "record.theft", MoneyTexts.amount(crime.worth(), units));
            case ASSAULT -> crime.victim().isEmpty() ? crime(CrimeKind.ASSAULT) : Component.translatable(KEY + "record.assault", crime.victim());
            case MURDER -> crime.victim().isEmpty() ? crime(CrimeKind.MURDER) : Component.translatable(KEY + "record.murder", crime.victim());
            case TRESPASS -> crime(CrimeKind.TRESPASS);
        };
        if (crime.count() > 1) {
            text = Component.translatable(KEY + "record.times", text, times(crime.count()));
        }
        return text;
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
