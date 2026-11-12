package io.github.sekerator.kingdomworld.core.land;

import java.util.List;

/**
 * The states of a world over its survey: which cell belongs to which state, and the cells of the spawn zone. Immutable.
 */
public final class StatePlan {
    private final SurveyRaster raster;
    private final StateRules rules;
    private final long seed;
    private final int[] owner;
    private final boolean[] spawnZone;
    private final List<PlannedState> states;
    private final double scale;
    private final int missing;
    private final int ringCells;
    private final int landCells;
    private final int stateLand;

    StatePlan(SurveyRaster raster, StateRules rules, long seed, int[] owner, boolean[] spawnZone, List<PlannedState> states, double scale,
              int missing, int ringCells, int landCells) {
        this.raster = raster;
        this.rules = rules;
        this.seed = seed;
        this.owner = owner;
        this.spawnZone = spawnZone;
        this.states = List.copyOf(states);
        this.scale = scale;
        this.missing = missing;
        this.ringCells = ringCells;
        this.landCells = landCells;
        int land = 0;
        for (int cell = 0; cell < owner.length; cell++) {
            if (owner[cell] != 0 && raster.terrain(cell).dry()) {
                land++;
            }
        }
        this.stateLand = land;
    }

    public SurveyRaster raster() {
        return raster;
    }

    public StateRules rules() {
        return rules;
    }

    public long seed() {
        return seed;
    }

    public List<PlannedState> states() {
        return states;
    }

    public PlannedState state(int index) {
        return states.get(index);
    }

    /**
     * The state a cell belongs to, or -1.
     */
    public int stateAt(int cell) {
        return owner[cell] - 1;
    }

    /**
     * Whether a cell reaches into the spawn zone: no state and no province is there.
     */
    public boolean inSpawnZone(int cell) {
        return spawnZone[cell];
    }

    /**
     * How much the targets were shrunk so that the states fit the ring and leave enough neutral land: 1 if they were not.
     */
    public double scale() {
        return scale;
    }

    public boolean shrunk() {
        return scale < 1;
    }

    /**
     * States of the quota for which no seed was found.
     */
    public int missing() {
        return missing;
    }

    /**
     * Cells between the spawn zone and the radius.
     */
    public int ringCells() {
        return ringCells;
    }

    /**
     * Cells of land beyond the spawn zone.
     */
    public int landCells() {
        return landCells;
    }

    /**
     * Cells of land the states took.
     */
    public int stateLand() {
        return stateLand;
    }

    /**
     * The share of the land beyond the spawn zone no state took.
     */
    public double neutralShare() {
        return landCells == 0 ? 1 : 1 - (double) stateLand / landCells;
    }
}
