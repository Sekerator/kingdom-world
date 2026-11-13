package io.github.sekerator.kingdomworld.core.npc;

/**
 * A citizen of a settlement folded into its numbers while nobody is near, in 8 bytes: its slot in the settlement (its id, name
 * and look come from the slot), its profession, its age in days and its mood. It comes back as itself.
 *
 * @param slot       its slot in the settlement, 0 to 65535
 * @param profession the number of its profession in the list of professions, 0 to 255 (0 without work)
 * @param ageDays    its age in game days, 0 to 65535
 * @param mood       its mood, 0 to 255
 */
public record FoldedCitizen(int slot, int profession, int ageDays, int mood) {
    public FoldedCitizen {
        if (slot < 0 || slot > 0xFFFF) {
            throw new IllegalArgumentException("Slot out of range: " + slot);
        }
        profession = Math.max(0, Math.min(0xFF, profession));
        ageDays = Math.max(0, Math.min(0xFFFF, ageDays));
        mood = Math.max(0, Math.min(0xFF, mood));
    }

    public long pack() {
        return (long) slot | (long) profession << 16 | (long) ageDays << 24 | (long) mood << 40;
    }

    public static FoldedCitizen unpack(long packed) {
        return new FoldedCitizen((int) (packed & 0xFFFF), (int) (packed >>> 16 & 0xFF), (int) (packed >>> 24 & 0xFFFF),
                (int) (packed >>> 40 & 0xFF));
    }

    /**
     * The packed citizens of a settlement with one more, or with the one of the same slot replaced.
     */
    public static long[] with(long[] folded, FoldedCitizen citizen) {
        for (int i = 0; i < folded.length; i++) {
            if (unpack(folded[i]).slot() == citizen.slot()) {
                long[] copy = folded.clone();
                copy[i] = citizen.pack();
                return copy;
            }
        }
        long[] more = java.util.Arrays.copyOf(folded, folded.length + 1);
        more[folded.length] = citizen.pack();
        return more;
    }

    /**
     * The packed citizens of a settlement without the one of a slot.
     */
    public static long[] without(long[] folded, int slot) {
        int at = -1;
        for (int i = 0; i < folded.length; i++) {
            if (unpack(folded[i]).slot() == slot) {
                at = i;
                break;
            }
        }
        if (at < 0) {
            return folded;
        }
        long[] less = new long[folded.length - 1];
        System.arraycopy(folded, 0, less, 0, at);
        System.arraycopy(folded, at + 1, less, at, folded.length - at - 1);
        return less;
    }

    /**
     * The folded citizen of a slot, or null.
     */
    public static FoldedCitizen of(long[] folded, int slot) {
        for (long packed : folded) {
            FoldedCitizen citizen = unpack(packed);
            if (citizen.slot() == slot) {
                return citizen;
            }
        }
        return null;
    }
}
