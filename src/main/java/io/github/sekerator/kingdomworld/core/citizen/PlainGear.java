package io.github.sekerator.kingdomworld.core.citizen;

import java.util.Collection;
import java.util.Set;

/**
 * The armor workers of dangerous work take from the storehouses: plain pieces, as a smith makes them, worn or not. Enchanted
 * pieces, named ones, pieces with attributes beyond those of their kind and pieces with any other data are the spoils of
 * players and stay in the storehouses for them; so do pieces that lift their wearer's step, which would let a worker climb
 * where it has no business.
 */
public final class PlainGear {
    /** The data of a piece that only says how worn it is. */
    public static final Set<String> WEAR = Set.of("Damage", "RepairCost");

    private PlainGear() {
    }

    /**
     * Whether a piece is plain.
     *
     * @param data         the keys of the data the piece carries (enchantments, a name, attributes and the marks of mods
     *                     all have their keys)
     * @param moreModifiers whether the piece changes attributes its kind does not
     * @param higherStep   whether the piece lifts the step of its wearer
     */
    public static boolean isPlain(Collection<String> data, boolean moreModifiers, boolean higherStep) {
        return !moreModifiers && !higherStep && WEAR.containsAll(data);
    }
}
