package io.github.sekerator.kingdomworld.common.magic;

import io.github.sekerator.kingdomworld.core.magic.MagicRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Where the magic of other mods plugs into the energy of kingdoms. The compat modules of those mods register their wells
 * here when the mod is installed; nothing else in the mod knows their classes. Every pulse a focus asks each well how many
 * units of energy it needs, the kingdom pays them first, and only then the well gives what they are worth.
 */
public final class MagicWells {
    /**
     * Refills the mana of a player.
     */
    public interface ManaWell {
        /** How the well is called in the log. */
        String name();

        boolean enabled(MagicRules rules);

        /**
         * Units of energy the player's missing mana is worth, rounded up; 0 if nothing is missing.
         */
        long need(ServerPlayer player, MagicRules rules);

        /**
         * Gives the player the mana of {@code units} of energy, already paid.
         *
         * @return the mana given
         */
        double give(ServerPlayer player, long units, MagicRules rules);
    }

    /**
     * Fills a block of another mod with its own kind of magic (source jars of Ars Nouveau).
     */
    public interface SourceWell {
        String name();

        boolean enabled(MagicRules rules);

        boolean accepts(BlockEntity entity);

        long need(BlockEntity entity, MagicRules rules);

        /**
         * @return what was given
         */
        double give(BlockEntity entity, long units, MagicRules rules);
    }

    private static final List<ManaWell> MANA = new CopyOnWriteArrayList<>();
    private static final List<SourceWell> SOURCE = new CopyOnWriteArrayList<>();

    private MagicWells() {
    }

    public static void register(ManaWell well) {
        MANA.add(well);
    }

    public static void register(SourceWell well) {
        SOURCE.add(well);
    }

    public static List<ManaWell> mana() {
        return List.copyOf(MANA);
    }

    public static List<SourceWell> source() {
        return List.copyOf(SOURCE);
    }

    public static boolean any() {
        return !MANA.isEmpty() || !SOURCE.isEmpty();
    }
}
