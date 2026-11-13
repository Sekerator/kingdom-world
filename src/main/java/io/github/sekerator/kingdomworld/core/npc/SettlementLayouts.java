package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.land.Tier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;

/**
 * How the settlements of a state are laid out, in the style of their kingdom: a large city as the districts of a medium capital
 * with its town hall where the palace would stand, a small town as a town of the kingdom (two rows along its street around its
 * town hall), a village as such a town of four to six plots without a tower, and a hamlet as two or three houses by a well.
 * Positions are in blocks from the middle of the settlement.
 */
public final class SettlementLayouts {
    /** Blocks the levelled ground of a village reaches from its middle. */
    static final int VILLAGE_REACH = 20;
    /** Blocks the levelled ground of a hamlet reaches from its middle. */
    static final int HAMLET_REACH = 18;

    private SettlementLayouts() {
    }

    /**
     * The layout of a settlement.
     *
     * @param plots  its buildings in the order they go up
     * @param reach  how far its levelled ground reaches from its middle, in blocks
     * @param fields the middles of its fields
     * @param hall   whether a town hall stands in its middle (a town, a large city)
     * @param wall   the radius of its wall, 0 for none
     * @param city   the districts of a large city, null for the others
     */
    public record Layout(Tier tier, List<NpcComposition.Plot> plots, int reach, List<int[]> fields, boolean hall, int wall, NpcComposition city) {
        public Layout {
            plots = List.copyOf(plots);
            fields = List.copyOf(fields);
        }

        /**
         * The beds of its houses, for the citizens who live there; the bunks of soldiers do not count.
         */
        public int homes() {
            int beds = 0;
            for (NpcComposition.Plot plot : plots) {
                Optional<TemplateCatalog.Entry> entry = TemplateCatalog.get(plot.template());
                if (entry.isPresent() && !TemplateCatalog.bunks(entry.get())) {
                    beds += entry.get().beds();
                }
            }
            return beds;
        }
    }

    /**
     * The side of the square of land of a settlement of a rank, in chunks.
     */
    public static int land(Tier tier) {
        return switch (tier) {
            case CAPITAL -> 0;
            case CITY -> 11;
            case TOWN -> 5;
            case VILLAGE -> 3;
            case HAMLET -> 2;
        };
    }

    /**
     * The side of the square of chunks held loaded while a settlement of a rank is raised: its land and a ring of a chunk.
     */
    public static int ticketSide(Tier tier) {
        return tier == Tier.CAPITAL ? 0 : land(tier) + 2;
    }

    /**
     * Chunks held loaded while a settlement of a rank is raised.
     */
    public static int tickets(Tier tier) {
        int side = ticketSide(tier);
        return side * side;
    }

    /**
     * The chunks of a square of a side around the chunk of a point, the west and north first; an even side leans west and north.
     */
    public static List<long[]> square(int x, int z, int side) {
        List<long[]> chunks = new ArrayList<>(side * side);
        int cx = x >> 4;
        int cz = z >> 4;
        int from = -(side / 2);
        for (int dz = 0; dz < side; dz++) {
            for (int dx = 0; dx < side; dx++) {
                chunks.add(new long[]{cx + from + dx, cz + from + dz});
            }
        }
        return chunks;
    }

    /**
     * The layout of a settlement of a rank of a kingdom.
     *
     * @param kingdom the level of the kingdom: the towns of a large one have an inn and a barracks and a palisade
     * @param styled  whether the kingdom is of the styled layout; otherwise its towns are of districts
     * @param style   its architectural style
     * @param rank    the rank of the guild house of a large city
     */
    public static Layout of(Tier tier, NpcLevel kingdom, boolean styled, String style, long seed, TemplateCatalog.Rank rank, NpcRules rules) {
        return switch (tier) {
            case CITY -> city(kingdom, styled, style, seed, rank, rules);
            case TOWN -> new Layout(tier, town(kingdom, styled, style, seed), CityPlan.TOWN_WALL, townFields(),
                    true, kingdom == NpcLevel.LARGE ? CityPlan.TOWN_WALL : 0, null);
            case VILLAGE -> village(kingdom, styled, style, seed);
            case HAMLET -> hamlet(kingdom, styled, style, seed);
            case CAPITAL -> throw new IllegalArgumentException("A capital is laid out as a capital");
        };
    }

    private static List<NpcComposition.Plot> town(NpcLevel kingdom, boolean styled, String style, long seed) {
        return styled ? StyledPlan.town(kingdom, seed, style, 0) : CityPlan.town(kingdom, seed, 0);
    }

    private static List<int[]> townFields() {
        return List.of(new int[]{-10, -CityPlan.TOWN_FIELD}, new int[]{10, CityPlan.TOWN_FIELD});
    }

    /**
     * A village: the plots of a town of a medium kingdom without its tower, four to six by the seed, and a field.
     */
    private static Layout village(NpcLevel kingdom, boolean styled, String style, long seed) {
        int count = 4 + new SplittableRandom(seed ^ 0x56494C4CL).nextInt(3);
        List<NpcComposition.Plot> plots = new ArrayList<>();
        for (NpcComposition.Plot plot : town(kingdom == NpcLevel.LARGE ? NpcLevel.MEDIUM : kingdom, styled, style, seed)) {
            if (plots.size() < count && !tower(plot.template())) {
                plots.add(plot);
            }
        }
        return new Layout(Tier.VILLAGE, plots, VILLAGE_REACH, List.of(new int[]{0, -VILLAGE_REACH - 7}), false, 0, null);
    }

    /**
     * A hamlet: two or three houses of a town and its well.
     */
    private static Layout hamlet(NpcLevel kingdom, boolean styled, String style, long seed) {
        int count = 2 + new SplittableRandom(seed ^ 0x48414D4CL).nextInt(2);
        List<NpcComposition.Plot> houses = new ArrayList<>();
        NpcComposition.Plot well = null;
        for (NpcComposition.Plot plot : town(kingdom == NpcLevel.LARGE ? NpcLevel.MEDIUM : kingdom, styled, style, seed)) {
            if (plot.template().endsWith("well")) {
                well = plot;
            } else if (house(plot.template()) && houses.size() < count) {
                houses.add(plot);
            }
        }
        List<NpcComposition.Plot> plots = new ArrayList<>(houses);
        if (well != null) {
            plots.add(well);
        }
        return new Layout(Tier.HAMLET, plots, HAMLET_REACH, List.of(), false, 0, null);
    }

    /**
     * A large city: the districts of a medium capital of the style of its kingdom with its town hall where the palace would stand.
     */
    private static Layout city(NpcLevel kingdom, boolean styled, String style, long seed, TemplateCatalog.Rank rank, NpcRules rules) {
        TemplateCatalog.Rank fitting = rank == TemplateCatalog.Rank.SS ? TemplateCatalog.Rank.S : rank;
        NpcComposition city = styled ? StyledPlan.make(NpcLevel.MEDIUM, seed, style, fitting, rules, 0) : CityPlan.make(NpcLevel.MEDIUM, seed, rules, 0);
        List<NpcComposition.Plot> plots = new ArrayList<>();
        for (NpcComposition.Plot plot : city.buildings()) {
            if (!palace(plot)) {
                plots.add(plot);
            }
        }
        // A city has 40 buildings at most (its gates, towers, decorations and wells aside): the houses last laid out stay lots.
        int buildings = 0;
        for (NpcComposition.Plot plot : plots) {
            buildings += building(plot) ? 1 : 0;
        }
        for (int i = plots.size() - 1; i >= 0 && buildings > MAX_CITY_BUILDINGS; i--) {
            if (residence(plots.get(i))) {
                plots.remove(i);
                buildings--;
            }
        }
        return new Layout(Tier.CITY, plots, city.reach(), city.fieldSpots(), true, city.wallRadius(), city);
    }

    /** Buildings of a large city at most, its gates, towers, decorations and wells aside. */
    static final int MAX_CITY_BUILDINGS = 40;

    /**
     * Whether a plot is a building of a city that counts: not a gate or tower of its wall, a decoration or a well.
     */
    public static boolean building(NpcComposition.Plot plot) {
        TemplateCatalog.Kind kind = TemplateCatalog.of(plot.template()).kind();
        return kind != TemplateCatalog.Kind.WALL_GATE && kind != TemplateCatalog.Kind.TOWER && kind != TemplateCatalog.Kind.DECORATION
                && kind != TemplateCatalog.Kind.WELL;
    }

    private static boolean residence(NpcComposition.Plot plot) {
        TemplateCatalog.Kind kind = TemplateCatalog.of(plot.template()).kind();
        return kind == TemplateCatalog.Kind.POOR_HOUSE || kind == TemplateCatalog.Kind.CRAFT_HOUSE || kind == TemplateCatalog.Kind.MERCHANT_HOUSE
                || kind == TemplateCatalog.Kind.NOBLE_HOUSE;
    }

    /**
     * Whether a plot is the palace (or castle) of a capital, which a large city has not.
     */
    static boolean palace(NpcComposition.Plot plot) {
        return "palace".equals(plot.quarter()) || StyledPlan.CASTLE.equals(plot.quarter());
    }

    private static boolean tower(String template) {
        return template.contains("tower") || template.contains("barracks") || template.contains("tavern");
    }

    private static boolean house(String template) {
        Optional<TemplateCatalog.Entry> entry = TemplateCatalog.get(template);
        return entry.isPresent() && entry.get().beds() > 0 && !TemplateCatalog.bunks(entry.get()) && !tower(template);
    }
}
