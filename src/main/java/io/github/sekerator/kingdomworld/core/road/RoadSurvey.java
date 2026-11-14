package io.github.sekerator.kingdomworld.core.road;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The earthworks of every column of a levelled road from the land under it: what is dug above its deck (soil by builders, rock
 * by miners, less the hollows of a cave), what is filled under it, the piers of its bridges, the lining of its tunnels and of a
 * cave opening under its deck, and its torches.
 */
public final class RoadSurvey {

    /**
     * The land of a column, or {@code null} when it is not known: such a column gets an empty survey.
     */
    @FunctionalInterface
    public interface Land {
        LandColumn at(int x, int z);
    }

    private RoadSurvey() {
    }

    /**
     * The surveys of the blocks of a levelled road, in the order of its blocks.
     */
    public static List<RoadLayout.ColumnSurvey> survey(List<RouteCell> cells, RoadClass roadClass, Land land, EarthRules earth) {
        List<RoadLayout.Block> blocks = RoadLayout.blocks(cells, roadClass, RoadLayout.LEVELLED, null);
        List<RoadLayout.ColumnSurvey> surveys = new ArrayList<>(blocks.size());
        Set<Integer> lit = new HashSet<>();
        for (RoadLayout.Block block : blocks) {
            boolean pier = block.kind() == RouteCell.Kind.BRIDGE && block.role() == RoadLayout.Role.MIDDLE
                    && block.cell() % EarthRules.PIER_EVERY == 0;
            // One torch on the floor of a verge of every so many blocks of a tunnel.
            boolean light = block.kind() == RouteCell.Kind.TUNNEL && block.role() == RoadLayout.Role.VERGE
                    && block.cell() % EarthRules.TORCH_EVERY == EarthRules.TORCH_AT && lit.add(block.cell());
            surveys.add(column(land.at(block.x(), block.z()), block.y(), block.kind(), block.role(), pier, light, earth.tunnelHeight()));
        }
        return surveys;
    }

    /**
     * The earthworks of one column with its deck at a height.
     *
     * @param pier  whether a pier stands under it, for a bridge
     * @param light whether a torch stands on it
     */
    public static RoadLayout.ColumnSurvey column(LandColumn land, int deck, RouteCell.Kind kind, RoadLayout.Role role, boolean pier,
                                                 boolean light, int tunnelHeight) {
        if (land == null) {
            return RoadLayout.ColumnSurvey.EMPTY;
        }
        if (land.has(LandColumn.ROAD)) {
            return new RoadLayout.ColumnSurvey(0, 0, 0, 0, 0, 0, false, true);
        }
        int ground = land.ground();
        switch (kind) {
            case BRIDGE -> {
                int height = pier ? Math.max(0, deck - 1 - ground) : 0;
                return new RoadLayout.ColumnSurvey(0, 0, 0, 0, height, 0, false, false);
            }
            case TUNNEL -> {
                int[] dug = dig(land, deck + 1, deck + tunnelHeight);
                int soilBottom = ground - land.soil() + 1;
                // A roof of soil, sand or gravel would fall: it is propped with stone.
                int roof = land.soil() > 0 && deck + tunnelHeight + 1 >= soilBottom ? 1 : 0;
                return new RoadLayout.ColumnSurvey(dug[0], dug[1], 0, 0, 0, roof + hole(land, deck), light, false);
            }
            default -> {
                int soil = 0;
                int rock = 0;
                int fill = 0;
                int lining = 0;
                if (ground > deck) {
                    int[] dug = dig(land, deck + 1, ground);
                    soil = dug[0];
                    rock = dug[1];
                } else if (ground < deck) {
                    // On a verge up to the deck, under the way up to the block below it.
                    fill = role == RoadLayout.Role.VERGE ? deck - ground : deck - ground - 1;
                }
                if (ground >= deck) {
                    lining = hole(land, deck);
                }
                return new RoadLayout.ColumnSurvey(soil, rock, land.logs(), fill, 0, lining, light, false);
            }
        }
    }

    /**
     * Soil and rock dug between two heights, both included: the soil from the ground down, the rest rock, the hollow of a cave
     * nothing.
     */
    static int[] dig(LandColumn land, int low, int high) {
        if (high < low) {
            return new int[]{0, 0};
        }
        int count = high - low + 1;
        int soilTop = land.ground();
        int soilBottom = land.ground() - land.soil() + 1;
        int soil = land.soil() <= 0 ? 0 : Math.max(0, Math.min(high, soilTop) - Math.max(low, soilBottom) + 1);
        int air = land.caveWithin(low, high);
        int rock = Math.max(0, count - soil - air);
        return new int[]{soil, rock};
    }

    /**
     * Whether a cave opens right under the deck: a block of lining covers it.
     */
    static int hole(LandColumn land, int deck) {
        return land.cave() && land.caveBottom() <= deck - 1 && deck - 1 <= land.caveTop() ? 1 : 0;
    }

    /**
     * Seconds of the earthworks of the blocks of a road in all.
     */
    public static double earthTotal(List<RoadLayout.Block> blocks, RoadClass roadClass, RoadRules rules, EarthRules earth) {
        double sum = 0;
        for (RoadLayout.Block block : blocks) {
            sum += ColumnWork.earthwork(block, roadClass, rules, earth);
        }
        return sum;
    }
}
