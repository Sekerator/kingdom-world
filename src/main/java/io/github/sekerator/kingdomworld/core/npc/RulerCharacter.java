package io.github.sekerator.kingdomworld.core.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.SplittableRandom;

/**
 * What the ruler of a kingdom of the world cares for most, with one or two traits of temper. The decisions of rulers come
 * later; for now the character is shown on the overview of the kingdom and kept for them.
 */
public enum RulerCharacter {
    /** Trade, markets and caravans. */
    MERCHANT,
    /** The army, walls and the safety of roads. */
    WARRIOR,
    /** Buildings, roads and new towns. */
    BUILDER,
    /** The tower of mages and the energy of crystals. */
    MAGE;

    /**
     * Traits of temper that colour the decisions of a ruler.
     */
    public enum Trait {
        GENEROUS,
        THRIFTY,
        CAUTIOUS,
        BOLD,
        PROUD,
        JUST,
        AMBITIOUS,
        PATIENT;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Trait> byId(String id) {
            for (Trait trait : values()) {
                if (trait.id().equals(id)) {
                    return Optional.of(trait);
                }
            }
            return Optional.empty();
        }

        /**
         * Whether two traits contradict each other (generous and thrifty, cautious and bold).
         */
        public boolean opposes(Trait other) {
            return switch (this) {
                case GENEROUS -> other == THRIFTY;
                case THRIFTY -> other == GENEROUS;
                case CAUTIOUS -> other == BOLD;
                case BOLD -> other == CAUTIOUS;
                default -> false;
            };
        }
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<RulerCharacter> byId(String id) {
        for (RulerCharacter character : values()) {
            if (character.id().equals(id)) {
                return Optional.of(character);
            }
        }
        return Optional.empty();
    }

    /**
     * The character of the ruler of a site. A mage rules only a large kingdom, which has a tower of mages.
     */
    public static RulerCharacter pick(long seed, NpcLevel level) {
        SplittableRandom random = new SplittableRandom(seed ^ 0x52554C4552L);
        RulerCharacter[] choices = level == NpcLevel.LARGE ? values() : new RulerCharacter[]{MERCHANT, WARRIOR, BUILDER};
        return choices[random.nextInt(choices.length)];
    }

    /**
     * One or two traits of the ruler of a site, never two that contradict each other.
     */
    public static List<Trait> traits(long seed) {
        SplittableRandom random = new SplittableRandom(seed ^ 0x5452414954L);
        Trait[] all = Trait.values();
        List<Trait> traits = new ArrayList<>();
        traits.add(all[random.nextInt(all.length)]);
        if (random.nextInt(3) > 0) {
            Trait second = all[random.nextInt(all.length)];
            if (second != traits.get(0) && !second.opposes(traits.get(0))) {
                traits.add(second);
            }
        }
        return List.copyOf(traits);
    }
}
