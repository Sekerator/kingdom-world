package io.github.sekerator.kingdomworld.core.companion;

/**
 * What a companion is good at, each from {@link #MIN} to {@link #MAX}: fighting (how hard it hits and how strong it counts),
 * carrying (how many slots its pack has) and stamina (how slowly it gets hungry and how fast its wounds close).
 */
public record Skills(int combat, int carrying, int stamina) {
    public static final int MIN = 1;
    public static final int MAX = 10;
    /** Slots of a pack at the lowest carrying skill; every three levels of the skill add a row. */
    public static final int BASE_SLOTS = 9;
    /** Slots a pack has at most. */
    public static final int MAX_SLOTS = 36;

    public Skills {
        combat = clamp(combat);
        carrying = clamp(carrying);
        stamina = clamp(stamina);
    }

    private static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }

    /**
     * Slots of the pack: 9 at carrying 1-3, 18 at 4-6, 27 at 7-9, 36 at 10.
     */
    public int slots() {
        return Math.min(MAX_SLOTS, BASE_SLOTS * (1 + (carrying - MIN) / 3));
    }

    /**
     * How fast hunger grows: from 1.15 at stamina 1 down to 0.7 at stamina 10.
     */
    public double hungerRate() {
        return 1.2 - stamina * 0.05;
    }

    /**
     * The skill a new level raises: the lowest one, fighting first among equals.
     */
    public Skills raised() {
        if (combat <= carrying && combat <= stamina && combat < MAX) {
            return new Skills(combat + 1, carrying, stamina);
        }
        if (carrying <= stamina && carrying < MAX) {
            return new Skills(combat, carrying + 1, stamina);
        }
        if (stamina < MAX) {
            return new Skills(combat, carrying, stamina + 1);
        }
        if (combat < MAX) {
            return new Skills(combat + 1, carrying, stamina);
        }
        return carrying < MAX ? new Skills(combat, carrying + 1, stamina) : this;
    }

    public int sum() {
        return combat + carrying + stamina;
    }
}
