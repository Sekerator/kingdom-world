package io.github.sekerator.kingdomworld.core.land;

/**
 * How large the provinces of the plan are, in cells of the survey (16 chunks each).
 *
 * @param cellsPerProvince a group of land is split into its cells divided by this, rounded
 * @param minCells         a smaller province joins a neighbour
 * @param maxCells         a larger province is split in two
 */
public record ProvinceRules(int cellsPerProvince, int minCells, int maxCells) {
    /** About 1600 chunks a province, from 800 to 2560. */
    public static final ProvinceRules DEFAULT = new ProvinceRules(100, 50, 160);

    public ProvinceRules {
        if (minCells < 1 || cellsPerProvince < minCells || maxCells < 2 * minCells || maxCells < cellsPerProvince) {
            throw new IllegalArgumentException("Rules of provinces out of range");
        }
    }
}
