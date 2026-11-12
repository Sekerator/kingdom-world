package io.github.sekerator.kingdomworld.core.land;

import io.github.sekerator.kingdomworld.core.road.RoadClass;

import java.util.Arrays;

/**
 * A road of the plan between two settlements: its class and the rough way over the survey, a point at least every
 * {@value RoadPlanner#MAX_STEP_CELLS} cells, from the middle of one settlement to the middle of the other.
 *
 * @param index     the number of the road in the plan, from 0
 * @param from      the settlement it starts at (the higher of the two)
 * @param to        the settlement it leads to
 * @param roadClass a path to a village or a hamlet, gravel to a town, paved between cities and capitals
 */
public record PlannedEdge(int index, int from, int to, RoadClass roadClass, int[] xs, int[] zs) {
    public PlannedEdge {
        if (xs == null || zs == null || xs.length != zs.length || xs.length < 2) {
            throw new IllegalArgumentException("A road has two points at least");
        }
        xs = xs.clone();
        zs = zs.clone();
    }

    @Override
    public int[] xs() {
        return xs.clone();
    }

    @Override
    public int[] zs() {
        return zs.clone();
    }

    public int points() {
        return xs.length;
    }

    public int x(int point) {
        return xs[point];
    }

    public int z(int point) {
        return zs[point];
    }

    /**
     * The length of the rough way, in blocks.
     */
    public double length() {
        double length = 0;
        for (int i = 1; i < xs.length; i++) {
            length += Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
        }
        return length;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PlannedEdge edge && edge.index == index && edge.from == from && edge.to == to && edge.roadClass == roadClass
                && Arrays.equals(edge.xs, xs) && Arrays.equals(edge.zs, zs);
    }

    @Override
    public int hashCode() {
        return ((index * 31 + from) * 31 + to) * 31 + Arrays.hashCode(xs);
    }

    @Override
    public String toString() {
        return "PlannedEdge[" + index + ": " + from + " -> " + to + ", " + roadClass.id() + ", " + xs.length + " points]";
    }
}
