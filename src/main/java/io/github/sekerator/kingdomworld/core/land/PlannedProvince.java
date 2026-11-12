package io.github.sekerator.kingdomworld.core.land;

import java.util.List;

/**
 * A province of the plan.
 *
 * @param id          the number of the province in the plan, from 1 (0 is no province)
 * @param state       the state it belongs to, or -1 for a wild province
 * @param kind        what it is to its state
 * @param cells       its cells of the survey
 * @param centerCell  its cell nearest the middle of its cells, where its name and its stone stand
 * @param neighbours  the provinces it borders, by number, in order
 * @param borderKinds what most of the border with each neighbour runs along, in the order of the neighbours
 * @param nameSeed    the seed of its names
 */
public record PlannedProvince(int id, int state, ProvinceKind kind, int cells, int centerCell, int centerX, int centerZ,
                              List<Integer> neighbours, List<BorderKind> borderKinds, long nameSeed) {
    public PlannedProvince {
        neighbours = List.copyOf(neighbours);
        borderKinds = List.copyOf(borderKinds);
        if (neighbours.size() != borderKinds.size()) {
            throw new IllegalArgumentException("A kind of border for every neighbour");
        }
    }

    /**
     * Its land in chunks, by its cells.
     */
    public int chunks() {
        return cells * SurveyRaster.CHUNKS_PER_CELL;
    }

    public boolean wild() {
        return kind == ProvinceKind.WILD;
    }

    /**
     * The kind of the border with a neighbour, or null when the two do not touch.
     */
    public BorderKind borderWith(int neighbour) {
        int at = neighbours.indexOf(neighbour);
        return at < 0 ? null : borderKinds.get(at);
    }
}
