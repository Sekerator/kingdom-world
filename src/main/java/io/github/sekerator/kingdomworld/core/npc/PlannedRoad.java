package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.road.RoadClass;

import java.util.Arrays;
import java.util.Objects;

/**
 * A road of the plan between two settlements, as a rough line over the land until the real road is laid along it.
 *
 * @param from   the settlement it starts at
 * @param to     the settlement it goes to
 * @param xs     the blocks of its points along x, from the start to the end
 * @param zs     the blocks of its points along z
 * @param roadId the real road laid along it, or 0 while there is none
 */
public record PlannedRoad(int id, int from, int to, RoadClass roadClass, int[] xs, int[] zs, long roadId) {
    public PlannedRoad {
        Objects.requireNonNull(roadClass, "class");
        if (id <= 0 || xs.length != zs.length || xs.length < 2) {
            throw new IllegalArgumentException("A planned road needs an id and two points at least");
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

    /**
     * The same road laid for real.
     */
    public PlannedRoad withRoad(long id) {
        return new PlannedRoad(this.id, from, to, roadClass, xs, zs, id);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PlannedRoad road && id == road.id && from == road.from && to == road.to && roadClass == road.roadClass
                && roadId == road.roadId && Arrays.equals(xs, road.xs) && Arrays.equals(zs, road.zs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, from, to, roadClass, roadId, Arrays.hashCode(xs), Arrays.hashCode(zs));
    }
}
