package io.github.sekerator.kingdomworld.core.npc;

/**
 * What a citizen of a settlement is made from: the seed of its settlement and its slot there give the same name, look and age
 * every time, so that a citizen folded into the numbers of its settlement comes back as itself.
 */
public final class Settler {
    private Settler() {
    }

    /**
     * The bits a settler is made from: its name seed in the low 32 bits, its gender, look and age above them.
     */
    public static long mix(long settlementSeed, int slot) {
        long z = settlementSeed + (slot + 1L) * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
