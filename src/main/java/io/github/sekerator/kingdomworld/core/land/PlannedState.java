package io.github.sekerator.kingdomworld.core.land;

import io.github.sekerator.kingdomworld.core.npc.NpcLevel;

import java.util.List;

/**
 * A state of the plan: where it was seeded and how much land it got.
 *
 * @param index            the number of the state in the plan, from 0
 * @param level            how large it is
 * @param seed             the seed of the state, for its names and settlements
 * @param targetChunks     the land it was meant to get, in chunks (after the targets were shrunk to fit, if they were)
 * @param seedCell         the cell its land grew from
 * @param cells            the cells of land it got
 * @param cramped          whether it got less than its target (the sea or its neighbours hemmed it in)
 * @param moves            how many times its seed was moved because it got too little land
 * @param throneCandidates cells for the throne, the best first: the server judges them in the world one after another
 */
public record PlannedState(int index, NpcLevel level, long seed, int targetChunks, int seedCell, int seedX, int seedZ, int cells,
                           boolean cramped, int moves, List<Integer> throneCandidates) {
    public PlannedState {
        throneCandidates = List.copyOf(throneCandidates);
    }

    /**
     * The land of the state in chunks, by its cells.
     */
    public int chunks() {
        return cells * SurveyRaster.CHUNKS_PER_CELL;
    }

    /**
     * The land it was meant to get, in cells.
     */
    public int targetCells() {
        return StatePlanner.cellsOf(targetChunks);
    }
}
