package io.github.sekerator.kingdomworld.core.construction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The open register of building roles. The mod registers its own (house, warehouse, workshop, market, tower, wall, well, tavern);
 * other parts of the mod and other mods add theirs with {@link #register}, data packs with role files that are put in
 * place on every reload ({@link #replaceData}). A role of the code wins over a data role of the same id.
 *
 * <p>Thread safe: the register is read by the server thread and by game tests.
 */
public final class BuildingRoles {
    public static final String HOUSE = "house";
    public static final String WAREHOUSE = "warehouse";
    public static final String WORKSHOP = "workshop";
    public static final String MARKET = "market";
    public static final String TOWER = "tower";
    public static final String WALL = "wall";
    public static final String WELL = "well";
    /** A barracks of the army: needs a barracks block inside. */
    public static final String BARRACKS = "barracks";
    /** A mage tower: needs a magic focus inside. */
    public static final String MAGE_TOWER = "mage_tower";
    /** A tavern: an innkeeper at a post inside serves food and drink in the evening; its beds are rooms for guests. */
    public static final String TAVERN = "tavern";

    /** Mood a house gives the citizens who live in it. */
    public static final String COMFORT = "comfort";
    /** Containers of a warehouse that count as storehouses. */
    public static final String CONTAINERS = "containers";
    /** How much faster the posts of a workshop work, as a factor. */
    public static final String SPEED = "speed";
    /** Seats a market adds to every merchant post in it. */
    public static final String MERCHANTS = "merchants";
    /** Lots a market adds for every player. */
    public static final String LOTS = "lots";
    /** How much farther guards and garrisons see from a tower, in blocks. */
    public static final String SIGHT = "sight";
    /** Strength of a wall for the wars to come. */
    public static final String FORTIFICATION = "fortification";
    /** Mood a well gives the citizens who live near it. */
    public static final String MOOD = "mood";
    /** How far a well reaches, in blocks. */
    public static final String RADIUS = "radius";
    /** Soldiers more a barracks building lets train at the same time. */
    public static final String BUNKS = "bunks";
    /** Citizens a tavern serves in an evening. */
    public static final String GUESTS = "guests";

    private static final Map<String, BuildingRole> CODE = new LinkedHashMap<>();
    private static volatile Map<String, BuildingRole> data = Map.of();

    static {
        register(new BuildingRole(HOUSE, BuildingRole.Requirement.BED, Map.of(COMFORT, 5.0), Map.of(COMFORT, 5.0)));
        register(new BuildingRole(WAREHOUSE, BuildingRole.Requirement.CONTAINER, Map.of(CONTAINERS, 8.0), Map.of(CONTAINERS, 8.0)));
        register(new BuildingRole(WORKSHOP, BuildingRole.Requirement.NONE, Map.of(SPEED, 1.25), Map.of(SPEED, 0.15)));
        register(new BuildingRole(MARKET, BuildingRole.Requirement.NONE, ordered(MERCHANTS, 2.0, LOTS, 2.0), ordered(MERCHANTS, 1.0, LOTS, 2.0)));
        register(new BuildingRole(TOWER, BuildingRole.Requirement.NONE, Map.of(SIGHT, 16.0), Map.of(SIGHT, 8.0)));
        register(new BuildingRole(WALL, BuildingRole.Requirement.NONE, Map.of(FORTIFICATION, 100.0), Map.of(FORTIFICATION, 50.0)));
        register(new BuildingRole(WELL, BuildingRole.Requirement.NONE, ordered(MOOD, 4.0, RADIUS, 24.0), ordered(MOOD, 2.0, RADIUS, 8.0)));
        register(new BuildingRole(BARRACKS, BuildingRole.Requirement.NONE, Map.of(BUNKS, 4.0), Map.of(BUNKS, 4.0)));
        register(new BuildingRole(MAGE_TOWER, BuildingRole.Requirement.NONE, Map.of(SPEED, 1.25), Map.of(SPEED, 0.15)));
        register(new BuildingRole(TAVERN, BuildingRole.Requirement.NONE, Map.of(GUESTS, 12.0), Map.of(GUESTS, 8.0)));
    }

    private BuildingRoles() {
    }

    private static Map<String, Double> ordered(String a, double va, String b, double vb) {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put(a, va);
        map.put(b, vb);
        return map;
    }

    /**
     * Adds a role of the code: the mod's parts (the barracks of the army, the tower of the mages) and other mods.
     *
     * @throws IllegalArgumentException if a role of the code has the id already
     */
    public static synchronized void register(BuildingRole role) {
        if (CODE.containsKey(role.id())) {
            throw new IllegalArgumentException("Building role " + role.id() + " is registered already");
        }
        CODE.put(role.id(), role);
    }

    /**
     * Puts the roles of the data packs in place of the ones read before.
     */
    public static void replaceData(Collection<BuildingRole> roles) {
        Map<String, BuildingRole> next = new LinkedHashMap<>();
        for (BuildingRole role : roles) {
            next.put(role.id(), role);
        }
        data = Map.copyOf(next);
    }

    public static synchronized Optional<BuildingRole> get(String id) {
        if (id == null || id.isEmpty()) {
            return Optional.empty();
        }
        String key = id.toLowerCase(Locale.ROOT);
        BuildingRole role = CODE.get(key);
        return Optional.ofNullable(role != null ? role : data.get(key));
    }

    public static boolean exists(String id) {
        return get(id).isPresent();
    }

    /**
     * Every role, those of the code first.
     */
    public static synchronized List<BuildingRole> all() {
        List<BuildingRole> result = new ArrayList<>(CODE.values());
        for (BuildingRole role : data.values()) {
            if (!CODE.containsKey(role.id())) {
                result.add(role);
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- the state of a role

    /**
     * Whether the role of a building works, and if not, why.
     */
    public enum Status {
        /** The role works. */
        ACTIVE(true),
        /** The building has no role. */
        NONE(false),
        /** Too much of the building is gone; a repair brings the role back. */
        DAMAGED(false),
        /** The builders take the building down. */
        DEMOLISHING(false),
        /** The building no longer stands on the land of its kingdom. */
        LAND_LOST(false),
        /** The building lacks what the role needs (a bed, a container). */
        LACKING(false);

        private final boolean works;

        Status(boolean works) {
            this.works = works;
        }

        public boolean works() {
            return works;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * The state of the role of a building from what the register knows: the role works while the building is at least
     * {@code threshold} percent whole; a building not looked at yet counts as whole.
     *
     * @param demolishing whether a demolition of the building is under way
     * @param onLand      whether the building stands on its kingdom's land
     */
    public static Status status(Building building, int threshold, boolean demolishing, boolean onLand) {
        if (building.role().isEmpty()) {
            return Status.NONE;
        }
        if (demolishing) {
            return Status.DEMOLISHING;
        }
        if (!onLand) {
            return Status.LAND_LOST;
        }
        if (building.integrity() >= 0 && building.integrity() < threshold) {
            return Status.DAMAGED;
        }
        return Status.ACTIVE;
    }
}
