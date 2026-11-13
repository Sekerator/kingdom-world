package io.github.sekerator.kingdomworld.common.magic;

import io.github.sekerator.kingdomworld.core.economy.Plurals;
import io.github.sekerator.kingdomworld.core.magic.EnergyCategory;
import io.github.sekerator.kingdomworld.core.magic.KingdomSpell;
import io.github.sekerator.kingdomworld.core.magic.PlayerSpell;
import io.github.sekerator.kingdomworld.core.magic.Research;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Texts of magic made of translation keys, with the right plural forms in both languages: energy, chunks, seconds, the
 * names of spells and researches. Works on both sides.
 */
public final class MagicTexts {
    private static final String COUNT = "kingdomworld.count.";
    private static final String MAGIC = "kingdomworld.magic.";

    private MagicTexts() {
    }

    /**
     * An amount of energy: «5 единиц энергии», "5 energy".
     */
    public static MutableComponent energy(long amount) {
        return Component.translatable(COUNT + "energy." + Plurals.form(amount).suffix(), amount);
    }

    /**
     * A number of chunks: «3 чанка».
     */
    public static MutableComponent chunks(long count) {
        return Component.translatable(COUNT + "chunks." + Plurals.form(count).suffix(), count);
    }

    /**
     * Ticks as whole seconds, at least one: «2 секунды».
     */
    public static MutableComponent seconds(long ticks) {
        long seconds = Math.max(1, (ticks + 19) / 20);
        return Component.translatable(COUNT + "seconds." + Plurals.form(seconds).suffix(), seconds);
    }

    /**
     * The level of a learned spell, written I to IV.
     */
    public static MutableComponent level(int level) {
        String[] numerals = {"0", "I", "II", "III", "IV"};
        return Component.literal(numerals[Math.max(0, Math.min(numerals.length - 1, level))]);
    }

    public static MutableComponent spell(PlayerSpell spell) {
        return Component.translatable(MAGIC + "spell." + spell.id());
    }

    public static MutableComponent spellHint(PlayerSpell spell) {
        return Component.translatable(MAGIC + "spell." + spell.id() + ".hint");
    }

    public static MutableComponent kingdomSpell(KingdomSpell spell) {
        return Component.translatable(MAGIC + "kingdom_spell." + spell.id());
    }

    public static MutableComponent kingdomSpellHint(KingdomSpell spell) {
        return Component.translatable(MAGIC + "kingdom_spell." + spell.id() + ".hint");
    }

    /**
     * What a spell of a kingdom costs: «5 единиц энергии за чанк в день».
     */
    public static MutableComponent price(KingdomSpell spell, long price) {
        return Component.translatable(MAGIC + "kingdom_spell." + spell.id() + ".price", energy(price));
    }

    public static MutableComponent research(Research research) {
        return Component.translatable(MAGIC + "research." + research.id());
    }

    public static MutableComponent category(EnergyCategory category) {
        return Component.translatable(MAGIC + "category." + category.id());
    }
}
