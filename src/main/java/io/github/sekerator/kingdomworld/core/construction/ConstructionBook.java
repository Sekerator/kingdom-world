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
    /** Counts every construction or building that came or went: the index of places is made again after a change. */
    private long layout;
    private long indexedAt = -1;
    /** Constructions and buildings by dimension and chunk, made when asked after a change. */
    private final Map<String, Map<Long, List<Object>>> index = new java.util.HashMap<>();
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
        layout++;
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
        layout++;
        leave(removed);
        removed.setChangeListener(() -> {
        });
        if (removed.building() > 0) {
            version++;
        }
        changed();
        return Optional.of(removed);
    }

    // ---------------------------------------------------------------- orders from afar

    private final Map<Long, Commission> commissions = new LinkedHashMap<>();
    private long nextCommission = 1;

    /**
     * Keeps an order from afar under the next free number.
     */
    public Commission addCommission(Commission commission) {
        Commission numbered = commission.withId(nextCommission++);
        commissions.put(numbered.id(), numbered);
        changed();
        return numbered;
    }

    /**
     * Replaces an order with its new state.
     */
    public void putCommission(Commission commission) {
        if (commissions.containsKey(commission.id())) {
            commissions.put(commission.id(), commission);
            changed();
        }
    }

    public Optional<Commission> removeCommission(long id) {
        Commission removed = commissions.remove(id);
        if (removed != null) {
            changed();
        }
        return Optional.ofNullable(removed);
    }

    public Optional<Commission> commission(long id) {
        return Optional.ofNullable(commissions.get(id));
    }

    public List<Commission> commissions() {
        return List.copyOf(commissions.values());
    }

    public List<Commission> commissionsOf(UUID kingdomId) {
        List<Commission> result = new ArrayList<>();
        for (Commission commission : commissions.values()) {
            if (commission.kingdomId().equals(kingdomId)) {
                result.add(commission);
            }
        }
        return result;
    }

    public long nextCommissionId() {
        return nextCommission;
    }

    public void restoreCommission(Commission commission) {
        commissions.put(commission.id(), commission);
        nextCommission = Math.max(nextCommission, commission.id() + 1);
    }

    public void restoreCommissionCounter(long next) {
        nextCommission = Math.max(nextCommission, next);
    }

    // ---------------------------------------------------------------- items to drop

    /**
     * Items that did not fit into the storehouses where nothing was loaded: they are dropped at their place, once, when its
     * chunk is loaded, rather than the chunk being loaded in the tick for them.
     *
     * @param since the tick the first of them waited
     */
    public record Drop(String dimension, int x, int y, int z, String item, int count, UUID kingdomId, long since) {
        public long chunk() {
            return io.github.sekerator.kingdomworld.core.territory.ChunkKey.of(x >> 4, z >> 4);
        }

        private Drop more(int count) {
            return new Drop(dimension, x, y, z, item, this.count + count, kingdomId, since);
        }
    }

    private record DropKey(String dimension, int x, int y, int z, String item, UUID kingdomId) {
    }

    private final Map<DropKey, Drop> drops = new LinkedHashMap<>();

    /**
     * Keeps items to be dropped at a place; the same item at the same place adds up.
     */
    public void addDrop(String dimension, int x, int y, int z, String item, int count, UUID kingdomId, long now) {
        if (count <= 0) {
            return;
        }
        DropKey key = new DropKey(dimension, x, y, z, item, kingdomId);
        Drop old = drops.get(key);
        drops.put(key, old == null ? new Drop(dimension, x, y, z, item, count, kingdomId, now) : old.more(count));
        changed();
    }

    public List<Drop> drops() {
        return List.copyOf(drops.values());
    }

    /**
     * Takes the items waiting in a chunk out of the book, to be dropped in the same step.
     */
    public List<Drop> takeDrops(String dimension, long chunk) {
        List<Drop> taken = new ArrayList<>();
        java.util.Iterator<Drop> all = drops.values().iterator();
        while (all.hasNext()) {
            Drop drop = all.next();
            if (drop.dimension().equals(dimension) && drop.chunk() == chunk) {
                taken.add(drop);
                all.remove();
            }
        }
        if (!taken.isEmpty()) {
            changed();
        }
        return taken;
    }

    public void restoreDrop(Drop drop) {
        DropKey key = new DropKey(drop.dimension(), drop.x(), drop.y(), drop.z(), drop.item(), drop.kingdomId());
        Drop old = drops.get(key);
        drops.put(key, old == null ? drop : old.more(drop.count()));
    }

    // ---------------------------------------------------------------- scaffolds left behind

    /** Most scaffolds kept to be taken down after their constructions went away. */
    public static final int MAX_LEFT_SCAFFOLDS = 65_536;

    /** Scaffolds of constructions that went away while some stood where nothing was loaded, by dimension. */
    private final Map<String, java.util.Set<Long>> leftScaffolds = new LinkedHashMap<>();

    /**
     * The scaffolds of a construction that goes away are taken down by the work of construction later, where their place is loaded.
     */
    private void leave(Construction construction) {
        java.util.Set<Long> cells = construction.takeScaffolds();
        if (!cells.isEmpty()) {
            leaveScaffolds(construction.dimension(), cells);
        }
    }

    /**
     * Scaffolds to be taken down once their place is loaded, though no construction keeps them.
     */
    public void leaveScaffolds(String dimension, Collection<Long> cells) {
        java.util.Set<Long> left = leftScaffolds.computeIfAbsent(dimension, key -> new java.util.LinkedHashSet<>());
        int before = left.size();
        for (long cell : cells) {
            if (leftScaffoldCount() >= MAX_LEFT_SCAFFOLDS) {
                break;
            }
            left.add(cell);
        }
        if (left.isEmpty()) {
            leftScaffolds.remove(dimension);
        }
        if (left.size() != before) {
            changed();
        }
    }

    /**
     * Scaffolds no construction keeps, by dimension; read only.
     */
    public Map<String, java.util.Set<Long>> leftScaffolds() {
        Map<String, java.util.Set<Long>> copy = new LinkedHashMap<>();
        leftScaffolds.forEach((dimension, cells) -> copy.put(dimension, java.util.Collections.unmodifiableSet(cells)));
        return java.util.Collections.unmodifiableMap(copy);
    }

    public int leftScaffoldCount() {
        int count = 0;
        for (java.util.Set<Long> cells : leftScaffolds.values()) {
            count += cells.size();
        }
        return count;
    }

    /**
     * A scaffold left behind was taken down, or something else stands in its place.
     */
    public void forgetLeftScaffold(String dimension, long cell) {
        java.util.Set<Long> left = leftScaffolds.get(dimension);
        if (left != null && left.remove(cell)) {
            if (left.isEmpty()) {
                leftScaffolds.remove(dimension);
            }
            changed();
        }
    }

    /**
     * Restores the scaffolds left behind from saved data. Does not notify.
     */
    public void restoreLeftScaffolds(String dimension, long[] cells) {
        java.util.Set<Long> left = leftScaffolds.computeIfAbsent(dimension, key -> new java.util.LinkedHashSet<>());
        for (long cell : cells) {
            if (leftScaffoldCount() >= MAX_LEFT_SCAFFOLDS) {
                break;
            }
            left.add(cell);
        }
        if (left.isEmpty()) {
            leftScaffolds.remove(dimension);
        }
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
        layout++;
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
        layout++;
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
        for (Object found : near(dimension, box)) {
            if (found instanceof Construction construction && construction.id() != except && construction.box().intersects(box)) {
                return Optional.of(construction);
            }
        }
        for (Object found : near(dimension, box)) {
            if (found instanceof Building building && building.id() != exceptBuilding && building.box().intersects(box)) {
                return Optional.of(building);
            }
        }
        return Optional.empty();
    }

    /**
     * A construction of any kingdom whose box meets the given one, other than {@code except}; buildings do not count.
     */
    public Optional<Construction> overlappingConstruction(String dimension, Box box, long except) {
        for (Object found : near(dimension, box)) {
            if (found instanceof Construction construction && construction.id() != except && construction.box().intersects(box)) {
                return Optional.of(construction);
            }
        }
        return Optional.empty();
    }

    /**
     * The construction whose box holds a position, if any.
     */
    public Optional<Construction> at(String dimension, int x, int y, int z) {
        for (Object found : near(dimension, new Box(x, y, z, x, y, z))) {
            if (found instanceof Construction construction && construction.box().contains(x, y, z)) {
                return Optional.of(construction);
            }
        }
        return Optional.empty();
    }

    /**
     * The constructions and buildings whose chunks meet those of a box, each once, constructions first in the order they came.
     */
    public List<Object> near(String dimension, Box box) {
        if (indexedAt != layout) {
            index.clear();
            for (Construction construction : constructions.values()) {
                put(construction.dimension(), construction.box(), construction);
            }
            for (Building building : buildings.values()) {
                put(building.dimension(), building.box(), building);
            }
            indexedAt = layout;
        }
        Map<Long, List<Object>> chunks = index.get(dimension);
        if (chunks == null) {
            return List.of();
        }
        java.util.LinkedHashSet<Object> found = new java.util.LinkedHashSet<>();
        for (long chunk : box.chunks()) {
            List<Object> here = chunks.get(chunk);
            if (here != null) {
                found.addAll(here);
            }
        }
        List<Object> sorted = new ArrayList<>(found.size());
        for (Object object : found) {
            if (object instanceof Construction) {
                sorted.add(object);
            }
        }
        for (Object object : found) {
            if (!(object instanceof Construction)) {
                sorted.add(object);
            }
        }
        return sorted;
    }

    private void put(String dimension, Box box, Object object) {
        Map<Long, List<Object>> chunks = index.computeIfAbsent(dimension, key -> new java.util.HashMap<>());
        for (long chunk : box.chunks()) {
            chunks.computeIfAbsent(chunk, key -> new ArrayList<>()).add(object);
        }
    }

    // ---------------------------------------------------------------- kingdoms

    /**
     * Forgets the constructions and buildings of a kingdom that is gone.
     *
     * @return the constructions it had
     */
    public List<Construction> removeKingdom(UUID kingdomId) {
        layout++;
        commissions.values().removeIf(commission -> commission.kingdomId().equals(kingdomId));
        List<Construction> removed = new ArrayList<>();
        constructions.values().removeIf(construction -> {
            if (construction.kingdomId().equals(kingdomId)) {
                removed.add(construction);
                return true;
            }
            return false;
        });
        removed.forEach(this::leave);
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
        layout++;
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
        layout++;
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
