package io.github.sekerator.kingdomworld.core.npc;

import java.util.List;
import java.util.Optional;

/**
 * The plan of a capital laid out in an architectural style ({@link NpcSite#LAYOUT_STYLED}), the same for the same seed: the
 * castle of the style around the throne, the institutions on the square, quarters by wealth in rings of rows, avenues to the
 * gates of the wall.
 *
 * <p>All positions are in blocks from the throne, x to the east and z to the south.
 */
public final class StyledPlan {
    /** Blocks from an avenue to where the rows of the last ring begin on a side with a gate: the gate square. */
    public static final int GATE_CLEAR = 13;

    private StyledPlan() {
    }

    /**
     * The measures of a styled capital of a level.
     *
     * @param precinct half the side of the ground of the castle, plus one
     * @param rings    where each ring of rows begins, from the throne
     * @param depths   how deep each ring of rows is
     * @param inward   whether the fronts of that ring look towards the throne
     * @param gardens  blocks of gardens between the last ring and the way along the wall
     * @param walk     blocks kept free along the wall inside it
     * @param gates    the sides that have a gate: 0 north, 1 east, 2 south, 3 west
     * @param fields   whether the last ring has fields inside the wall on its east and west sides
     */
    public record Shape(NpcLevel level, int precinct, int[] rings, int[] depths, boolean[] inward, int gardens, int walk, int[] gates,
                        boolean fields) {
        /**
         * The outermost line of the last ring.
         */
        public int last() {
            int n = rings.length;
            return rings[n - 1] + depths[n - 1] - 1;
        }

        /**
         * The radius of the wall.
         */
        public int wall() {
            return last() + gardens + walk + 1;
        }
    }

    /**
     * The measures of the styled capital of a level.
     */
    public static Shape shape(NpcLevel level) {
        return switch (level) {
            case SMALL -> new Shape(level, 16, new int[]{27, 54}, new int[]{25, 15}, new boolean[]{true, false}, 0, 5,
                    new int[]{CityPlan.SOUTH, CityPlan.NORTH}, true);
            case MEDIUM -> new Shape(level, 21, new int[]{31, 62}, new int[]{29, 15}, new boolean[]{true, false}, 3, 6,
                    new int[]{CityPlan.SOUTH, CityPlan.NORTH, CityPlan.EAST, CityPlan.WEST}, false);
            case LARGE -> new Shape(level, 24, new int[]{38, 77}, new int[]{37, 15}, new boolean[]{true, false}, 3, 6,
                    new int[]{CityPlan.SOUTH, CityPlan.NORTH, CityPlan.EAST, CityPlan.WEST}, false);
        };
    }

    /**
     * A post of work outside the buildings of a styled capital.
     *
     * @param what  the profession
     * @param x     in blocks east of the throne
     * @param z     in blocks south of the throne
     * @param seats the workers the post takes
     */
    public record WorkPlace(String what, int x, int z, int seats) {
    }

    /**
     * The posts of work of a styled capital outside its buildings: the builders and carriers by the gates.
     */
    public static List<WorkPlace> workPlaces(NpcComposition composition) {
        return List.of();
    }

    /**
     * The seats of a profession in the buildings of a styled capital (the yards of the builders and of the carriers).
     */
    public static int seatsInBuildings(NpcComposition composition, String profession) {
        return 0;
    }

    /**
     * Where the bank of a styled capital stands in its treasury: {x, z, y} from the throne, y in layers above the street.
     */
    public static Optional<int[]> bank(NpcComposition composition) {
        return Optional.empty();
    }

    /**
     * The profession of the post of work in a building of a template: {@code registrar}, {@code minter}, {@code builder},
     * {@code carrier}, or empty for a building without one.
     */
    public static String profession(TemplateCatalog.Entry entry) {
        return "";
    }
}
