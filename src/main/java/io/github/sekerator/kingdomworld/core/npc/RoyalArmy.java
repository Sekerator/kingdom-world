package io.github.sekerator.kingdomworld.core.npc;

import java.util.List;
import java.util.SplittableRandom;

/**
 * The size of the royal army of a kingdom of the world, and where it lives.
 *
 * <p>The army is far larger than the guard: between {@link NpcRules#armyLeast} and {@link NpcRules#armyMost} of its size. The
 * seed of the kingdom chooses the number and the character of its ruler leans it: a warrior keeps an army near the largest,
 * a merchant near the least, a builder and a mage between. Bold and ambitious rulers keep a little more, thrifty and patient
 * ones a little less.
 *
 * <p>A part of the army lives in the capital ({@link NpcRules#army}: its reserve at the barracks and the palace); the rest
 * lives in camps on the land of the kingdom, {@link NpcRules#campSoldiers} to a camp, three camps at most.
 */
public final class RoyalArmy {
    /** The most camps a kingdom keeps. */
    public static final int MAX_CAMPS = 3;

    private RoyalArmy() {
    }

    /**
     * Where in its range the army of a ruler lies: {from, to}, shares of the range.
     */
    static double[] leaning(RulerCharacter character, List<RulerCharacter.Trait> traits) {
        double from;
        double to;
        switch (character) {
            case WARRIOR -> {
                from = 0.7;
                to = 1.0;
            }
            case MERCHANT -> {
                from = 0.0;
                to = 0.3;
            }
            case MAGE -> {
                from = 0.3;
                to = 0.65;
            }
            default -> {
                from = 0.25;
                to = 0.6;
            }
        }
        double shift = 0;
        for (RulerCharacter.Trait trait : traits) {
            shift += switch (trait) {
                case BOLD, AMBITIOUS -> 0.08;
                case THRIFTY, PATIENT -> -0.08;
                default -> 0;
            };
        }
        return new double[]{Math.max(0, Math.min(1, from + shift)), Math.max(0, Math.min(1, to + shift))};
    }

    /**
     * The soldiers of the royal army of a kingdom, officers included.
     */
    public static int size(NpcRules rules, NpcLevel level, long seed, RulerCharacter character, List<RulerCharacter.Trait> traits) {
        int least = rules.armyLeast(level);
        int most = Math.max(least, rules.armyMost(level));
        double[] lean = leaning(character, traits);
        double share = lean[0] + new SplittableRandom(seed ^ 0x41524D59L).nextDouble() * (lean[1] - lean[0]);
        return least + (int) Math.round(share * (most - least));
    }

    /**
     * The soldiers of an army of a size who live in the capital.
     */
    public static int inCapital(NpcRules rules, NpcLevel level, int size) {
        return Math.min(size, rules.army(level));
    }

    /**
     * The camps an army of a size needs: none when the capital holds it all, otherwise one for every
     * {@link NpcRules#campSoldiers} soldiers beyond the capital, {@link #MAX_CAMPS} at most.
     */
    public static int camps(NpcRules rules, NpcLevel level, int size) {
        int beyond = size - inCapital(rules, level, size);
        if (beyond <= 0) {
            return 0;
        }
        return Math.min(MAX_CAMPS, (beyond + rules.campSoldiers() - 1) / rules.campSoldiers());
    }
}
