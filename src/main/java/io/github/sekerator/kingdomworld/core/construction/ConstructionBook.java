package io.github.sekerator.kingdomworld.core.construction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The constructions and the register of finished buildings of all kingdoms. Places taken by a construction or a building
 * are not given to another one.
 *
 * <p>Not thread safe; the server uses it from the server thread only.
 */
public final class ConstructionBook {
    private final Map<Long, Construction> constructions = new LinkedHashMap<>();
    private final Map<Long, Building> buildings = new LinkedHashMap<>();
    private final Map<Long, CustomBlueprint> blueprints = new LinkedHashMap<>();
    private long nextConstruction = 1;
    private long nextBuilding = 1;
    private long nextBlueprint = 1;
    /** Counts changes of the saved blueprints, so that the templates made of them are made again. */
    private int blueprintRevision;
    /** Counts changes of the register of buildings and of the constructions that work on a building of it. */
    private long version;
    private Runnable changeListener = () -> {
    };

    public void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
        for (Construction construction : constructions.values()) {
            construction.setChangeListener(listener);
        }
    }

    private void changed() {
        changeListener.run();
    }

    /**
     * Grows whenever a building enters the register, is replaced by a different one or leaves it, and whenever a construction
     * that works on a building of the register (an upgrade, a repair, a demolition) is added or removed: what is worked out
     * from the buildings compares it to know when to work it out again. The progress of a construction does not count.
     */
    public long version() {
        return version;
    }

    // ---------------------------------------------------------------- constructions

    public Optional<Construction> get(long id) {
        return Optional.ofNullable(constructions.get(id));
    }

    public Collection<Construction> all() {
        return List.copyOf(constructions.values());
    }

    /**
     * Constructions of a kingdom, the most urgent first, then the oldest.
     */
    public List<Construction> of(UUID kingdomId) {
        List<Construction> result = new ArrayList<>();
        for (Construction construction : constructions.values()) {
            if (construction.kingdomId().equals(kingdomId)) {
                result.add(construction);
            }
        }
        result.sort(Comparator.comparing((Construction c) -> c.priority().ordinal()).reversed().thenComparingLong(Construction::id));
        return result;
    }

    public int count(UUID kingdomId) {
        int count = 0;
        for (Construction construction : constructions.values()) {
            if (construction.kingdomId().equals(kingdomId)) {
                count++;
            }
        }
        return count;
    }

    public long nextConstructionId() {
        return nextConstruction;
    }

    /**
     * Adds a construction with the next free number.
     */
    public Construction add(UUID kingdomId, String templateId, long fingerprint, io.github.sekerator.kingdomworld.core.citizen.WorldPos anchor,
                            Placement placement, Box box, long created, List<int[]> clear, int steps) {
        return add(kingdomId, templateId, fingerprint, anchor, placement, box, created, clear, steps, Construction.Kind.BUILD, 0,
                Materials.NONE, new java.util.BitSet());
    }

    /**
     * Adds a construction of any kind with the next free number.
     */
    public Construction add(UUID kingdomId, String templateId, long fingerprint, io.github.sekerator.kingdomworld.core.citizen.WorldPos anchor,
                            Placement placement, Box box, long created, List<int[]> clear, int steps, Construction.Kind kind, long building,
                            Materials materials, java.util.BitSet skip) {
        Construction construction = new Construction(nextConstruction++, kingdomId, templateId, fingerprint, anchor, placement, box, created,
                clear, steps, kind, building, materials, skip);
        construction.setChangeListener(changeListener);
        constructions.put(construction.id(), construction);
        if (construction.building() > 0) {
            version++;
        }
        changed();
        return construction;
    }

    public Optional<Construction> remove(long id) {
        Construction removed = constructions.remove(id);
        if (removed == null) {
            return Optional.empty();
        }
        removed.setChangeListener(() -> {
        });
        if (removed.building() > 0) {
            version++;
        }
        changed();
        return Optional.of(removed);
    }

    // ---------------------------------------------------------------- buildings

    public Optional<Building> building(long id) {
        return Optional.ofNullable(buildings.get(id));
    }

    public Collection<Building> buildings() {
        return List.copyOf(buildings.values());
    }

    public List<Building> buildingsOf(UUID kingdomId) {
        List<Building> result = new ArrayList<>();
        for (Building building : buildings.values()) {
            if (building.kingdomId().equals(kingdomId)) {
                result.add(building);
            }
        }
        return result;
    }

    /**
     * Enters a finished construction into the register.
     */
    public Building register(Construction construction, String type, String category, long builtTime, boolean admin) {
        return register(construction, type, category, "", 1, builtTime, admin);
    }

    /**
     * Enters a finished construction into the register with its role and level.
     */
    public Building register(Construction construction, String type, String category, String role, int level, long builtTime, boolean admin) {
        Building building = new Building(nextBuilding++, construction.kingdomId(), construction.templateId(), cut(type), cut(category),
                cut(role), level, construction.materials(), construction.anchor(), construction.placement(), construction.box(),
                construction.fingerprint(), builtTime, admin, 100, builtTime, false, new java.util.BitSet());
        buildings.put(building.id(), building);
        version++;
        changed();
        return building;
    }

    /**
     * The construction that works on a building of the register (an upgrade, a repair, a demolition), if any.
     */
    public Optional<Construction> forBuilding(long buildingId) {
        if (buildingId <= 0) {
            return Optional.empty();
        }
        for (Construction construction : constructions.values()) {
            if (construction.building() == buildingId) {
                return Optional.of(construction);
            }
        }
        return Optional.empty();
    }

    private static String cut(String text) {
        return text.length() > Building.MAX_NAME ? text.substring(0, Building.MAX_NAME) : text;
    }

    public void putBuilding(Building building) {
        Building old = buildings.put(building.id(), building);
        if (!building.equals(old)) {
            if (old == null || reshaped(old, building)) {
                version++;
            }
            changed();
        }
    }

    /**
     * Whether a building changed what the indexes of buildings rest on: its kingdom, place, box, template, role or level. Its
     * integrity and the map of its damage do not count: a siege marks them block by block.
     */
    private static boolean reshaped(Building old, Building building) {
        return !old.kingdomId().equals(building.kingdomId()) || !java.util.Objects.equals(old.box(), building.box())
                || !java.util.Objects.equals(old.anchor(), building.anchor()) || !java.util.Objects.equals(old.placement(), building.placement())
                || !java.util.Objects.equals(old.templateId(), building.templateId()) || !java.util.Objects.equals(old.role(), building.role())
                || old.level() != building.level() || old.fingerprint() != building.fingerprint();
    }

    public Optional<Building> removeBuilding(long id) {
        Building removed = buildings.remove(id);
        if (removed != null) {
            version++;
            changed();
        }
        return Optional.ofNullable(removed);
    }

    // ---------------------------------------------------------------- places

    /**
     * A construction or building of any kingdom whose box meets the given one, other than the construction {@code except}.
     */
    public Optional<Object> overlapping(String dimension, Box box, long except) {
        return overlapping(dimension, box, except, 0);
    }

    /**
     * A construction or building of any kingdom whose box meets the given one, other than the construction {@code except}
     * and the building {@code exceptBuilding} (the one an upgrade works on).
     */
    public Optional<Object> overlapping(String dimension, Box box, long except, long exceptBuilding) {
        for (Construction construction : constructions.values()) {
            if (construction.id() != except && construction.dimension().equals(dimension) && construction.box().intersects(box)) {
                return Optional.of(construction);
            }
        }
        for (Building building : buildings.values()) {
            if (building.id() != exceptBuilding && building.dimension().equals(dimension) && building.box().intersects(box)) {
                return Optional.of(building);
            }
        }
        return Optional.empty();
    }

    /**
     * The construction whose box holds a position, if any.
     */
    public Optional<Construction> at(String dimension, int x, int y, int z) {
        for (Construction construction : constructions.values()) {
            if (construction.dimension().equals(dimension) && construction.box().contains(x, y, z)) {
                return Optional.of(construction);
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- kingdoms

    /**
     * Forgets the constructions and buildings of a kingdom that is gone.
     *
     * @return the constructions it had
     */
    public List<Construction> removeKingdom(UUID kingdomId) {
        List<Construction> removed = new ArrayList<>();
        constructions.values().removeIf(construction -> {
            if (construction.kingdomId().equals(kingdomId)) {
                removed.add(construction);
                return true;
            }
            return false;
        });
        boolean anyBuilding = buildings.values().removeIf(building -> building.kingdomId().equals(kingdomId));
        if (anyBuilding || removed.stream().anyMatch(construction -> construction.building() > 0)) {
            version++;
        }
        boolean any = !removed.isEmpty() | anyBuilding;
        if (blueprints.values().removeIf(blueprint -> blueprint.kingdomId().equals(kingdomId))) {
            any = true;
            blueprintRevision++;
        }
        if (any) {
            changed();
        }
        return removed;
    }

    // ---------------------------------------------------------------- saved data

    /**
     * Restores a construction from saved data; its number must be free.
     *
     * @throws IllegalArgumentException if the number is taken
     */
    public void restore(Construction construction) {
        if (constructions.containsKey(construction.id())) {
            throw new IllegalArgumentException("Two constructions with number " + construction.id());
        }
        construction.setChangeListener(changeListener);
        constructions.put(construction.id(), construction);
        nextConstruction = Math.max(nextConstruction, construction.id() + 1);
        if (construction.building() > 0) {
            version++;
        }
    }

    /**
     * @throws IllegalArgumentException if the number is taken
     */
    public void restore(Building building) {
        if (buildings.containsKey(building.id())) {
            throw new IllegalArgumentException("Two buildings with number " + building.id());
        }
        buildings.put(building.id(), building);
        nextBuilding = Math.max(nextBuilding, building.id() + 1);
        version++;
    }

    public void restoreCounters(long constructions, long buildings) {
        nextConstruction = Math.max(nextConstruction, constructions);
        nextBuilding = Math.max(nextBuilding, buildings);
    }

    public long nextBuildingId() {
        return nextBuilding;
    }

    // ---------------------------------------------------------------- saved blueprints

    public Optional<CustomBlueprint> blueprint(long id) {
        return Optional.ofNullable(blueprints.get(id));
    }

    public Collection<CustomBlueprint> blueprints() {
        return List.copyOf(blueprints.values());
    }

    public List<CustomBlueprint> blueprintsOf(UUID kingdomId) {
        List<CustomBlueprint> result = new ArrayList<>();
        for (CustomBlueprint blueprint : blueprints.values()) {
            if (blueprint.kingdomId().equals(kingdomId)) {
                result.add(blueprint);
            }
        }
        return result;
    }

    public long nextBlueprintId() {
        return nextBlueprint;
    }

    /**
     * Counts changes of the saved blueprints.
     */
    public int blueprintRevision() {
        return blueprintRevision;
    }

    /**
     * Saves a blueprint under the next free number, which the blueprint given must carry.
     *
     * @throws IllegalArgumentException if it carries another number
     */
    public CustomBlueprint addBlueprint(CustomBlueprint blueprint) {
        if (blueprint.id() != nextBlueprint) {
            throw new IllegalArgumentException("Blueprint " + blueprint.id() + " is not the next one, " + nextBlueprint);
        }
        nextBlueprint++;
        blueprints.put(blueprint.id(), blueprint);
        blueprintRevision++;
        changed();
        return blueprint;
    }

    public void putBlueprint(CustomBlueprint blueprint) {
        if (!blueprint.equals(blueprints.put(blueprint.id(), blueprint))) {
            blueprintRevision++;
            changed();
        }
    }

    public Optional<CustomBlueprint> removeBlueprint(long id) {
        CustomBlueprint removed = blueprints.remove(id);
        if (removed != null) {
            blueprintRevision++;
            changed();
        }
        return Optional.ofNullable(removed);
    }

    /**
     * @throws IllegalArgumentException if the number is taken
     */
    public void restore(CustomBlueprint blueprint) {
        if (blueprints.containsKey(blueprint.id())) {
            throw new IllegalArgumentException("Two blueprints with number " + blueprint.id());
        }
        blueprints.put(blueprint.id(), blueprint);
        nextBlueprint = Math.max(nextBlueprint, blueprint.id() + 1);
        blueprintRevision++;
    }

    public void restoreBlueprintCounter(long next) {
        nextBlueprint = Math.max(nextBlueprint, next);
    }
}
