package io.github.sekerator.kingdomworld.core.citizen;

import java.util.BitSet;

/**
 * How the work on the levels of a mine goes: the blocks opened on a level one after another without meeting ore, and
 * the levels left because of it. A miner that has opened too many such blocks leaves the rest of the level and goes
 * on to the next one, so that it gets down to richer rock sooner; when no other level has work, the levels left are
 * taken up again.
 */
public final class MineProgress {
    private final BitSet left = new BitSet();
    private int level = -1;
    private int barren;

    /**
     * Counts a block of rock opened in a tunnel of a level.
     *
     * @param limit blocks without ore after which a level is left; 0 never leaves one
     * @return whether the level is left now
     */
    public boolean opened(int level, int limit) {
        if (level < 0 || limit <= 0) {
            return false;
        }
        if (level != this.level) {
            this.level = level;
            barren = 0;
        }
        if (++barren >= limit) {
            left.set(level);
            barren = 0;
            return true;
        }
        return false;
    }

    /**
     * Ore was met: the count of blocks without it starts anew.
     */
    public void ore() {
        barren = 0;
    }

    /**
     * Whether a level was left for lack of ore.
     */
    public boolean left(int level) {
        return level >= 0 && left.get(level);
    }

    public boolean anyLeft() {
        return !left.isEmpty();
    }

    /**
     * Takes the levels left up again.
     */
    public void resume() {
        left.clear();
        level = -1;
        barren = 0;
    }

    /** Blocks opened on the level at hand since ore was last met. */
    public int barren() {
        return barren;
    }
}
