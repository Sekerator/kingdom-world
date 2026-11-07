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
    private long nextConstruction = 1;
    private long nextBuilding = 1;
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
        Construction construction = new Construction(nextConstruction++, kingdomId, templateId, fingerprint, anchor, placement, box, created,
                clear, steps);
        construction.setChangeListener(changeListener);
        constructions.put(construction.id(), construction);
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
        Building building = new Building(nextBuilding++, construction.kingdomId(), construction.templateId(), cut(type), cut(category),
                construction.anchor(), construction.placement(), construction.box(), construction.fingerprint(), builtTime, admin, 100, builtTime);
        buildings.put(building.id(), building);
        changed();
        return building;
    }

    private static String cut(String text) {
        return text.length() > Building.MAX_NAME ? text.substring(0, Building.MAX_NAME) : text;
    }

    public void putBuilding(Building building) {
        Building old = buildings.put(building.id(), building);
        if (!building.equals(old)) {
            changed();
        }
    }

    public Optional<Building> removeBuilding(long id) {
        Building removed = buildings.remove(id);
        if (removed != null) {
            changed();
        }
        return Optional.ofNullable(removed);
    }

    // ---------------------------------------------------------------- places

    /**
     * A construction or building of any kingdom whose box meets the given one, other than the construction {@code except}.
     */
    public Optional<Object> overlapping(String dimension, Box box, long except) {
        for (Construction construction : constructions.values()) {
            if (construction.id() != except && construction.dimension().equals(dimension) && construction.box().intersects(box)) {
                return Optional.of(construction);
            }
        }
        for (Building building : buildings.values()) {
            if (building.dimension().equals(dimension) && building.box().intersects(box)) {
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
        boolean any = !removed.isEmpty() | buildings.values().removeIf(building -> building.kingdomId().equals(kingdomId));
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
    }

    public void restoreCounters(long constructions, long buildings) {
        nextConstruction = Math.max(nextConstruction, constructions);
        nextBuilding = Math.max(nextBuilding, buildings);
    }

    public long nextBuildingId() {
        return nextBuilding;
    }
}
