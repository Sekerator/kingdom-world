package io.github.sekerator.kingdomworld.common.data;

import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.BuildingRoles;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.ConstructionBook;
import io.github.sekerator.kingdomworld.core.construction.CustomBlueprint;
import io.github.sekerator.kingdomworld.core.construction.Facing;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Saved form of the constructions and the register of buildings, at the root of the kingdom data:
 *
 * <pre>
 * construction: {
 *   nextConstruction, nextBuilding, nextBlueprint,
 *   constructions: [ { id, kingdom, template, fingerprint, dimension, x, y, z, rotation, mirror, box: [6 ints], created,
 *                      clear: [x, y, z, ...], steps, priority, paused, orderMissing, done: [longs], pending: [ints],
 *                      delivered: [ { item, count } ], orders: [ { item, order } ], status, statusPos?: [x, y, z], carry,
 *                      kind, building, wood, stone, skip: [longs], removed: [ { item, count } ], earthwork?, scaffolds?: [longs] } ],
 *   buildings: [ { id, kingdom, template, type, category, role, level, wood, stone, dimension, x, y, z, rotation, mirror,
 *                  box, fingerprint, built, admin, integrity, checked, autoRepair, damaged: [longs] } ],
 *   blueprints: [ { id, kingdom, name, category, role, size: [3 ints], palette: [state strings], blocks: [ints],
 *                   anchor: [3 ints], entrance, created, author } ],
 *   leftScaffolds?: [ { dimension, cells: [longs] } ],
 *   drops?: [ { dimension, x, y, z, item, count, kingdom, since } ]
 * }
 * </pre>
 *
 * Reading never throws; every entry that cannot be read is described, so that the caller keeps the data read-only.
 */
public final class ConstructionNbt {
    public static final String CONSTRUCTION = "construction";

    private ConstructionNbt() {
    }

    // ---------------------------------------------------------------- writing

    public static CompoundTag write(ConstructionBook book) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("nextConstruction", book.nextConstructionId());
        tag.putLong("nextBuilding", book.nextBuildingId());
        tag.putLong("nextBlueprint", book.nextBlueprintId());
        ListTag constructions = new ListTag();
        for (Construction construction : book.all()) {
            constructions.add(write(construction));
        }
        tag.put("constructions", constructions);
        ListTag buildings = new ListTag();
        for (Building building : book.buildings()) {
            buildings.add(write(building));
        }
        tag.put("buildings", buildings);
        ListTag blueprints = new ListTag();
        for (CustomBlueprint blueprint : book.blueprints()) {
            blueprints.add(write(blueprint));
        }
        tag.put("blueprints", blueprints);
        ListTag left = new ListTag();
        book.leftScaffolds().forEach((dimension, cells) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("dimension", dimension);
            entry.putLongArray("cells", cells.stream().mapToLong(Long::longValue).toArray());
            left.add(entry);
        });
        if (!left.isEmpty()) {
            tag.put("leftScaffolds", left);
        }
        ListTag drops = new ListTag();
        for (ConstructionBook.Drop drop : book.drops()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("dimension", drop.dimension());
            entry.putInt("x", drop.x());
            entry.putInt("y", drop.y());
            entry.putInt("z", drop.z());
            entry.putString("item", drop.item());
            entry.putInt("count", drop.count());
            entry.putUUID("kingdom", drop.kingdomId());
            entry.putLong("since", drop.since());
            drops.add(entry);
        }
        if (!drops.isEmpty()) {
            tag.put("drops", drops);
        }
        return tag;
    }

    private static void writeItems(CompoundTag tag, String key, Map<String, Integer> items) {
        ListTag list = new ListTag();
        items.forEach((item, count) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("item", item);
            entry.putInt("count", count);
            list.add(entry);
        });
        tag.put(key, list);
    }

    private static Map<String, Integer> readItems(CompoundTag tag, String key) {
        Map<String, Integer> items = new LinkedHashMap<>();
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int count = entry.getInt("count");
            if (count < 0) {
                throw new IllegalArgumentException("negative count in '" + key + "'");
            }
            items.merge(entry.getString("item"), count, Integer::sum);
        }
        return items;
    }

    static CompoundTag write(CustomBlueprint blueprint) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("id", blueprint.id());
        tag.putUUID("kingdom", blueprint.kingdomId());
        tag.putString("name", blueprint.name());
        tag.putString("category", blueprint.category());
        tag.putString("role", blueprint.role());
        tag.putIntArray("size", new int[]{blueprint.sizeX(), blueprint.sizeY(), blueprint.sizeZ()});
        ListTag palette = new ListTag();
        for (String state : blueprint.palette()) {
            palette.add(StringTag.valueOf(state));
        }
        tag.put("palette", palette);
        tag.putIntArray("blocks", blueprint.blocks());
        tag.putIntArray("anchor", new int[]{blueprint.anchorX(), blueprint.anchorY(), blueprint.anchorZ()});
        tag.putString("entrance", blueprint.entrance().id());
        tag.putLong("created", blueprint.created());
        tag.putUUID("author", blueprint.author());
        return tag;
    }

    static CustomBlueprint readBlueprint(CompoundTag tag) {
        require(tag, "id", "kingdom", "name", "size", "palette", "blocks", "anchor", "author");
        if (!tag.hasUUID("kingdom") || !tag.hasUUID("author")) {
            throw new IllegalArgumentException("the kingdom or the author is not a UUID");
        }
        int[] size = tag.getIntArray("size");
        int[] anchor = tag.getIntArray("anchor");
        if (size.length != 3 || anchor.length != 3) {
            throw new IllegalArgumentException("the size or the anchor is not three numbers");
        }
        ListTag paletteTag = tag.getList("palette", Tag.TAG_STRING);
        List<String> palette = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++) {
            palette.add(paletteTag.getString(i));
        }
        String name = tag.getString("name");
        if (name.isBlank() || name.length() > CustomBlueprint.MAX_NAME) {
            throw new IllegalArgumentException("bad name of a blueprint");
        }
        return new CustomBlueprint(tag.getLong("id"), tag.getUUID("kingdom"), name, tag.getString("category"),
                cut(tag.getString("role")), size[0], size[1], size[2], palette, tag.getIntArray("blocks"), anchor[0], anchor[1], anchor[2],
                Facing.byId(tag.getString("entrance")).orElse(Facing.SOUTH), tag.getLong("created"), tag.getUUID("author"));
    }

    private static String cut(String text) {
        return text.length() > Building.MAX_NAME ? text.substring(0, Building.MAX_NAME) : text;
    }

    private static void writePlace(CompoundTag tag, WorldPos anchor, Placement placement, Box box) {
        tag.putString("dimension", anchor.dimension());
        tag.putInt("x", anchor.x());
        tag.putInt("y", anchor.y());
        tag.putInt("z", anchor.z());
        tag.putInt("rotation", placement.rotation());
        tag.putBoolean("mirror", placement.mirror());
        tag.putIntArray("box", new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
    }

    static CompoundTag write(Construction construction) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("id", construction.id());
        tag.putUUID("kingdom", construction.kingdomId());
        tag.putString("template", construction.templateId());
        tag.putLong("fingerprint", construction.fingerprint());
        writePlace(tag, construction.anchor(), construction.placement(), construction.box());
        tag.putLong("created", construction.created());
        List<int[]> clear = construction.clear();
        int[] packed = new int[clear.size() * 3];
        for (int i = 0; i < clear.size(); i++) {
            packed[i * 3] = clear.get(i)[0];
            packed[i * 3 + 1] = clear.get(i)[1];
            packed[i * 3 + 2] = clear.get(i)[2];
        }
        tag.putIntArray("clear", packed);
        tag.putInt("steps", construction.steps());
        tag.putString("priority", construction.priority().id());
        tag.putBoolean("paused", construction.paused());
        tag.putBoolean("orderMissing", construction.orderMissing());
        tag.putLongArray("done", construction.doneSet().toLongArray());
        tag.putIntArray("pending", construction.pending().stream().mapToInt(Integer::intValue).toArray());
        ListTag delivered = new ListTag();
        construction.delivered().forEach((item, count) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("item", item);
            entry.putInt("count", count);
            delivered.add(entry);
        });
        tag.put("delivered", delivered);
        ListTag orders = new ListTag();
        construction.orders().forEach((item, order) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("item", item);
            entry.putLong("order", order);
            orders.add(entry);
        });
        tag.put("orders", orders);
        tag.putString("status", construction.status().id());
        if (construction.statusPos() != null) {
            tag.putIntArray("statusPos", new int[]{construction.statusPos().x(), construction.statusPos().y(), construction.statusPos().z()});
        }
        tag.putDouble("carry", construction.carry());
        tag.putString("kind", construction.kind().id());
        tag.putLong("building", construction.building());
        tag.putString("wood", construction.materials().wood());
        tag.putString("stone", construction.materials().stone());
        if (!construction.materials().look().isEmpty()) {
            tag.putString("look", construction.materials().look());
        }
        tag.putLongArray("skip", construction.skip().toLongArray());
        writeItems(tag, "removed", construction.removed());
        if (construction.earthwork() != null) {
            tag.put("earthwork", writeEarthwork(construction.earthwork()));
        }
        if (construction.hasScaffolds()) {
            tag.putLongArray("scaffolds", construction.scaffolds().stream().mapToLong(Long::longValue).toArray());
        }
        return tag;
    }

    /**
     * The land of earthworks: { minX, minZ, sizeX, sizeZ, mode, target, smooth, ground: [ints], top: [ints], dug: [ { item, count } ],
     * stock: [ { item, count } ] }.
     */
    private static CompoundTag writeEarthwork(io.github.sekerator.kingdomworld.core.construction.Earthwork work) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("minX", work.minX());
        tag.putInt("minZ", work.minZ());
        tag.putInt("sizeX", work.sizeX());
        tag.putInt("sizeZ", work.sizeZ());
        tag.putString("mode", work.mode().id());
        tag.putInt("target", work.target());
        tag.putBoolean("smooth", work.smooth());
        tag.putIntArray("ground", work.ground());
        tag.putIntArray("top", work.top());
        writeItems(tag, "dug", work.dug());
        writeItems(tag, "stock", work.stock());
        return tag;
    }

    private static io.github.sekerator.kingdomworld.core.construction.Earthwork readEarthwork(CompoundTag tag) {
        return new io.github.sekerator.kingdomworld.core.construction.Earthwork(tag.getInt("minX"), tag.getInt("minZ"), tag.getInt("sizeX"),
                tag.getInt("sizeZ"), io.github.sekerator.kingdomworld.core.construction.Earthwork.Mode.byId(tag.getString("mode")), tag.getInt("target"),
                tag.getBoolean("smooth"), tag.getIntArray("ground"), tag.getIntArray("top"), readItems(tag, "dug"), readItems(tag, "stock"));
    }

    static CompoundTag write(Building building) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("id", building.id());
        tag.putUUID("kingdom", building.kingdomId());
        tag.putString("template", building.templateId());
        tag.putString("type", building.type());
        tag.putString("category", building.category());
        tag.putString("role", building.role());
        tag.putInt("level", building.level());
        tag.putString("wood", building.materials().wood());
        tag.putString("stone", building.materials().stone());
        if (!building.materials().look().isEmpty()) {
            tag.putString("look", building.materials().look());
        }
        writePlace(tag, building.anchor(), building.placement(), building.box());
        tag.putLong("fingerprint", building.fingerprint());
        tag.putLong("built", building.builtTime());
        tag.putBoolean("admin", building.admin());
        tag.putInt("integrity", building.integrity());
        tag.putLong("checked", building.checked());
        tag.putBoolean("autoRepair", building.autoRepair());
        tag.putLongArray("damaged", building.damaged().toLongArray());
        return tag;
    }

    // ---------------------------------------------------------------- reading

    /**
     * Restores the constructions and buildings into the state; the kingdoms must be restored already.
     *
     * @return descriptions of the entries that could not be read
     */
    public static List<String> read(Tag raw, KingdomWorldState state) {
        List<String> problems = new ArrayList<>();
        if (!(raw instanceof CompoundTag tag)) {
            problems.add("'" + CONSTRUCTION + "' is not a compound");
            return problems;
        }
        ConstructionBook book = state.construction();
        ListTag constructions = tag.getList("constructions", Tag.TAG_COMPOUND);
        for (int i = 0; i < constructions.size(); i++) {
            try {
                Construction construction = readConstruction(constructions.getCompound(i));
                if (state.kingdoms().get(construction.kingdomId()).isEmpty()) {
                    throw new IllegalArgumentException("unknown kingdom " + construction.kingdomId());
                }
                book.restore(construction);
            } catch (RuntimeException e) {
                problems.add("construction #" + i + ": " + e.getMessage());
            }
        }
        ListTag buildings = tag.getList("buildings", Tag.TAG_COMPOUND);
        for (int i = 0; i < buildings.size(); i++) {
            try {
                Building building = readBuilding(buildings.getCompound(i));
                if (state.kingdoms().get(building.kingdomId()).isEmpty()) {
                    throw new IllegalArgumentException("unknown kingdom " + building.kingdomId());
                }
                book.restore(building);
            } catch (RuntimeException e) {
                problems.add("building #" + i + ": " + e.getMessage());
            }
        }
        ListTag blueprints = tag.getList("blueprints", Tag.TAG_COMPOUND);
        for (int i = 0; i < blueprints.size(); i++) {
            try {
                CustomBlueprint blueprint = readBlueprint(blueprints.getCompound(i));
                if (state.kingdoms().get(blueprint.kingdomId()).isEmpty()) {
                    throw new IllegalArgumentException("unknown kingdom " + blueprint.kingdomId());
                }
                book.restore(blueprint);
            } catch (RuntimeException e) {
                problems.add("blueprint #" + i + ": " + e.getMessage());
            }
        }
        ListTag left = tag.getList("leftScaffolds", Tag.TAG_COMPOUND);
        for (int i = 0; i < left.size(); i++) {
            CompoundTag entry = left.getCompound(i);
            book.restoreLeftScaffolds(entry.getString("dimension"), entry.getLongArray("cells"));
        }
        ListTag drops = tag.getList("drops", Tag.TAG_COMPOUND);
        for (int i = 0; i < drops.size(); i++) {
            CompoundTag entry = drops.getCompound(i);
            if (entry.hasUUID("kingdom") && !entry.getString("item").isEmpty() && entry.getInt("count") > 0) {
                book.restoreDrop(new ConstructionBook.Drop(entry.getString("dimension"), entry.getInt("x"), entry.getInt("y"), entry.getInt("z"),
                        entry.getString("item"), entry.getInt("count"), entry.getUUID("kingdom"), entry.getLong("since")));
            }
        }
        book.restoreCounters(tag.getLong("nextConstruction"), tag.getLong("nextBuilding"));
        book.restoreBlueprintCounter(tag.getLong("nextBlueprint"));
        return problems;
    }

    private static void require(CompoundTag tag, String... keys) {
        for (String key : keys) {
            if (!tag.contains(key)) {
                throw new IllegalArgumentException("missing '" + key + "'");
            }
        }
    }

    private static Box box(CompoundTag tag) {
        int[] box = tag.getIntArray("box");
        if (box.length != 6) {
            throw new IllegalArgumentException("the box is not six numbers");
        }
        return new Box(box[0], box[1], box[2], box[3], box[4], box[5]);
    }

    private static WorldPos anchor(CompoundTag tag) {
        return new WorldPos(tag.getString("dimension"), tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    static Construction readConstruction(CompoundTag tag) {
        require(tag, "id", "kingdom", "template", "fingerprint", "dimension", "x", "y", "z", "box", "steps", "clear");
        if (!tag.hasUUID("kingdom")) {
            throw new IllegalArgumentException("the kingdom is not a UUID");
        }
        long id = tag.getLong("id");
        if (id <= 0) {
            throw new IllegalArgumentException("invalid number " + id);
        }
        int[] packed = tag.getIntArray("clear");
        if (packed.length % 3 != 0) {
            throw new IllegalArgumentException("the positions to clear are not triples");
        }
        List<int[]> clear = new ArrayList<>(packed.length / 3);
        for (int i = 0; i < packed.length; i += 3) {
            clear.add(new int[]{packed[i], packed[i + 1], packed[i + 2]});
        }
        int steps = tag.getInt("steps");
        if (steps < 0) {
            throw new IllegalArgumentException("negative number of steps");
        }
        long building = tag.getLong("building");
        if (building < 0) {
            throw new IllegalArgumentException("invalid building " + building);
        }
        Construction construction = new Construction(id, tag.getUUID("kingdom"), tag.getString("template"), tag.getLong("fingerprint"),
                anchor(tag), new Placement(tag.getInt("rotation"), tag.getBoolean("mirror")), box(tag), tag.getLong("created"), clear, steps,
                Construction.Kind.byId(tag.getString("kind")), building, new Materials(tag.getString("wood"), tag.getString("stone"), tag.getString("look")),
                BitSet.valueOf(tag.getLongArray("skip")));
        if (tag.contains("earthwork", Tag.TAG_COMPOUND)) {
            // Heights that do not fit the rectangle throw, and the construction is left out with the problem told.
            construction.setEarthwork(readEarthwork(tag.getCompound("earthwork")));
        } else if (construction.kind() == Construction.Kind.EARTHWORK) {
            throw new IllegalArgumentException("earthworks without their land");
        }
        Map<String, Integer> delivered = readItems(tag, "delivered");
        Map<String, Long> orders = new LinkedHashMap<>();
        ListTag ordersTag = tag.getList("orders", Tag.TAG_COMPOUND);
        for (int i = 0; i < ordersTag.size(); i++) {
            CompoundTag entry = ordersTag.getCompound(i);
            orders.put(entry.getString("item"), entry.getLong("order"));
        }
        List<Integer> pending = new ArrayList<>();
        for (int step : tag.getIntArray("pending")) {
            pending.add(step);
        }
        int[] statusPos = tag.getIntArray("statusPos");
        WorldPos at = statusPos.length == 3 ? new WorldPos(tag.getString("dimension"), statusPos[0], statusPos[1], statusPos[2]) : null;
        construction.restore(Construction.Priority.byId(tag.getString("priority")), tag.getBoolean("paused"), tag.getBoolean("orderMissing"),
                BitSet.valueOf(tag.getLongArray("done")), pending, delivered, orders, Construction.Status.byId(tag.getString("status")), at,
                tag.getDouble("carry"), readItems(tag, "removed"));
        construction.restoreScaffolds(tag.getLongArray("scaffolds"));
        return construction;
    }

    static Building readBuilding(CompoundTag tag) {
        require(tag, "id", "kingdom", "template", "dimension", "x", "y", "z", "box");
        if (!tag.hasUUID("kingdom")) {
            throw new IllegalArgumentException("the kingdom is not a UUID");
        }
        return new Building(tag.getLong("id"), tag.getUUID("kingdom"), tag.getString("template"), cut(tag.getString("type")),
                cut(tag.getString("category")), cut(tag.getString("role")), tag.contains("level") ? tag.getInt("level") : 1,
                new Materials(tag.getString("wood"), tag.getString("stone"), tag.getString("look")), anchor(tag),
                new Placement(tag.getInt("rotation"), tag.getBoolean("mirror")), box(tag), tag.getLong("fingerprint"), tag.getLong("built"),
                tag.getBoolean("admin"), tag.contains("integrity") ? tag.getInt("integrity") : -1,
                tag.contains("checked") ? tag.getLong("checked") : -1, tag.getBoolean("autoRepair"), BitSet.valueOf(tag.getLongArray("damaged")));
    }

    /**
     * An empty register, for data upgraded from an older format.
     */
    public static CompoundTag empty() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("nextConstruction", 1);
        tag.putLong("nextBuilding", 1);
        tag.putLong("nextBlueprint", 1);
        tag.put("constructions", new ListTag());
        tag.put("buildings", new ListTag());
        tag.put("blueprints", new ListTag());
        return tag;
    }

    /**
     * Format 9 gives every building of the register a role, a level, the template's own materials, no repair by itself and
     * nothing found damaged, every construction the kind of a new building, and the register an empty list of saved
     * blueprints. The role of a building of format 8 is its type when that names one of the roles of the mod.
     */
    public static void upgradeFrom8(CompoundTag construction) {
        java.util.Set<String> roles = java.util.Set.of(BuildingRoles.HOUSE, BuildingRoles.WAREHOUSE, BuildingRoles.WORKSHOP, BuildingRoles.MARKET,
                BuildingRoles.TOWER, BuildingRoles.WALL, BuildingRoles.WELL);
        ListTag buildings = construction.getList("buildings", Tag.TAG_COMPOUND);
        for (int i = 0; i < buildings.size(); i++) {
            CompoundTag building = buildings.getCompound(i);
            if (!building.contains("role")) {
                String type = building.getString("type");
                building.putString("role", roles.contains(type) ? type : "");
            }
            if (!building.contains("level")) {
                building.putInt("level", 1);
            }
        }
        ListTag constructions = construction.getList("constructions", Tag.TAG_COMPOUND);
        for (int i = 0; i < constructions.size(); i++) {
            CompoundTag entry = constructions.getCompound(i);
            if (!entry.contains("kind")) {
                entry.putString("kind", Construction.Kind.BUILD.id());
            }
        }
        if (!construction.contains("blueprints")) {
            construction.put("blueprints", new ListTag());
        }
        if (!construction.contains("nextBlueprint")) {
            construction.putLong("nextBlueprint", 1);
        }
    }
}
