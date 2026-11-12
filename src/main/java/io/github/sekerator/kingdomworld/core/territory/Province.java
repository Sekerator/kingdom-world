package io.github.sekerator.kingdomworld.core.territory;

import io.github.sekerator.kingdomworld.core.land.BorderKind;
import io.github.sekerator.kingdomworld.core.land.ProvinceKind;

import java.util.List;
import java.util.Objects;

/**
 * What a province is: its number, its middle, its neighbours with the kind of the border each shares with it, what it is to its
 * state, the seed of its names and its main settlement. Who holds it, since when and whether it is held for a buyer live in
 * the {@link ProvinceMap}, as do its chunks.
 *
 * @param id          the number of the province, from 1 to {@value ProvinceMap#MAX_ID}
 * @param centerX     the block in its middle, where its name and its stone stand
 * @param centerZ     the block in its middle
 * @param neighbours  the provinces it borders, by number
 * @param borderKinds what most of the border with each neighbour runs along, in the order of the neighbours
 * @param kind        what it is to its state
 * @param nameSeed    the seed of its names in both alphabets
 * @param seat        the id of its main settlement, or 0
 */
public record Province(int id, int centerX, int centerZ, List<Integer> neighbours, List<BorderKind> borderKinds, ProvinceKind kind,
                       long nameSeed, long seat) {
    public Province {
        if (id < 1 || id > ProvinceMap.MAX_ID) {
            throw new IllegalArgumentException("Province number out of range: " + id);
        }
        Objects.requireNonNull(kind, "kind");
        neighbours = List.copyOf(neighbours);
        borderKinds = List.copyOf(borderKinds);
        if (neighbours.size() != borderKinds.size()) {
            throw new IllegalArgumentException("A kind of border for every neighbour");
        }
    }

    /**
     * A province without neighbours, for operators and tests.
     */
    public static Province plain(int id, int centerX, int centerZ, ProvinceKind kind) {
        return new Province(id, centerX, centerZ, List.of(), List.of(), kind, id, 0);
    }

    public boolean wild() {
        return kind == ProvinceKind.WILD;
    }
}
