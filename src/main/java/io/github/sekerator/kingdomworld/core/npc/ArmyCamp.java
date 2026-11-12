package io.github.sekerator.kingdomworld.core.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A camp of the royal army of a kingdom of the world: a square of three chunks on its land with barracks, a drill yard, a
 * barn, a watchtower and four fields, where the army beyond the capital lives. In peace its soldiers work its fields, two to
 * a field, and feed the camp. A camp with no land for it (players lived there, another
 * kingdom is near, the ground is water) is kept as {@link Status#NO_LAND}: its soldiers live abstractly at the capital.
 *
 * @param x the middle of the camp (the middle of a chunk)
 * @param z the middle of the camp
 * @param y the ground of the camp, the height its buildings stand on; 0 before it is levelled
 */
public record ArmyCamp(int x, int z, int y, Status status) {
    /** Blocks from the middle of a camp to the edge of its buildings. */
    public static final int REACH = 22;

    public enum Status {
        /** Its land is claimed or being claimed, its buildings go up. */
        BUILDING,
        /** It stands; its soldiers live there. */
        BUILT,
        /** No land was found for it. */
        NO_LAND;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Status> byId(String id) {
            for (Status status : values()) {
                if (status.id().equals(id)) {
                    return Optional.of(status);
                }
            }
            return Optional.empty();
        }
    }

    public ArmyCamp {
        if (status == null) {
            throw new IllegalArgumentException("A camp needs a status");
        }
    }

    public ArmyCamp with(int ground, Status value) {
        return new ArmyCamp(x, z, ground, value);
    }

    public boolean built() {
        return status == Status.BUILT;
    }

    /**
     * A building of a camp: a template centred on a point of the camp, facing a way.
     *
     * @param dx     from the middle of the camp
     * @param dz     from the middle of the camp
     * @param facing quarter turns of the template, clockwise, as the construction turns it
     */
    public record Building(String template, int dx, int dz, int facing) {
    }

    /** Fields of a camp. */
    public static final int FIELDS = 4;
    /** Crops a field of a camp holds: nine by nine round the water in its middle. */
    public static final int FIELD_PLOTS = 80;
    /** Soldiers who work one field. */
    public static final int FIELD_HANDS = 2;

    /**
     * The buildings of a camp: the drill yard in the middle, two barracks north and south of it, a barn east of it and a
     * watchtower west of it. All of them lie within {@link #REACH} of the middle.
     */
    public static List<Building> buildings() {
        return List.of(
                new Building("kingdomworld:drill_yard", 0, 0, 0),
                new Building("kingdomworld:barracks_hall", 0, -13, 2),
                new Building("kingdomworld:barracks_hall", 0, 13, 0),
                new Building("kingdomworld:barn", 15, 0, 3),
                new Building("kingdomworld:wooden_watchtower", -15, 0, 1));
    }

    /**
     * The buildings of a camp of a kingdom of an architectural style, at the same places as {@link #buildings()}; for an empty
     * style the plain ones.
     */
    public static List<Building> buildings(String style) {
        if (!ArchitecturalStyles.known(style)) {
            return buildings();
        }
        String prefix = "kingdomworld:" + style + "_camp_";
        return List.of(
                new Building(prefix + "yard", 0, 0, 0),
                new Building(prefix + "barracks", 0, -13, 2),
                new Building(prefix + "barracks", 0, 13, 0),
                new Building(prefix + "store", 15, 0, 3),
                new Building(prefix + "tower", -15, 0, 1));
    }

    /**
     * The middles of the fields of a camp, at its four corners: {dx, dz} from the middle of the camp, eleven by eleven each.
     */
    public static List<int[]> fields() {
        return List.of(new int[]{15, -14}, new int[]{15, 14}, new int[]{-15, -14}, new int[]{-15, 14});
    }

    /**
     * Where the squads of a camp stand: in ranks on the drill yard and beside it, the first squads nearest the middle.
     *
     * @return {dx, dz} from the middle, for as many squads as asked
     */
    public static List<int[]> squadPoints(int squads) {
        int[][] around = {{-3, -2}, {3, -2}, {-3, 2}, {3, 2}, {-9, -3}, {-9, 3}, {0, -7}, {0, 7}, {7, -7}, {7, 7}};
        List<int[]> points = new ArrayList<>();
        for (int i = 0; i < squads; i++) {
            int[] point = around[i % around.length];
            int ring = i / around.length;
            points.add(new int[]{point[0] - ring * 2, point[1]});
        }
        return points;
    }
}
