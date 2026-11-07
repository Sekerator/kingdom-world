package io.github.sekerator.kingdomworld.common.data;

import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.ConstructionBook;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
 *   nextConstruction, nextBuilding,
 *   constructions: [ { id, kingdom, template, fingerprint, dimension, x, y, z, rotation, mirror, box: [6 ints], created,
 *                      clear: [x, y, z, ...], steps, priority, paused, orderMissing, done: [longs], pending: [ints],
 *                      delivered: [ { item, count } ], orders: [ { item, order } ], status, statusPos?: [x, y, z], carry } ],
 *   buildings: [ { id, kingdom, template, type, category, dimension, x, y, z, rotation, mirror, box, fingerprint, built,
 *                  admin, integrity, checked } ]
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
        return tag;
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
        return tag;
    }

    static CompoundTag write(Building building) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("id", building.id());
        tag.putUUID("kingdom", building.kingdomId());
        tag.putString("template", building.templateId());
        tag.putString("type", building.type());
        tag.putString("category", building.category());
        writePlace(tag, building.anchor(), building.placement(), building.box());
        tag.putLong("fingerprint", building.fingerprint());
        tag.putLong("built", building.builtTime());
        tag.putBoolean("admin", building.admin());
        tag.putInt("integrity", building.integrity());
        tag.putLong("checked", building.checked());
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
        book.restoreCounters(tag.getLong("nextConstruction"), tag.getLong("nextBuilding"));
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
        Construction construction = new Construction(id, tag.getUUID("kingdom"), tag.getString("template"), tag.getLong("fingerprint"),
                anchor(tag), new Placement(tag.getInt("rotation"), tag.getBoolean("mirror")), box(tag), tag.getLong("created"), clear, steps);
        Map<String, Integer> delivered = new LinkedHashMap<>();
        ListTag deliveredTag = tag.getList("delivered", Tag.TAG_COMPOUND);
        for (int i = 0; i < deliveredTag.size(); i++) {
            CompoundTag entry = deliveredTag.getCompound(i);
            int count = entry.getInt("count");
            if (count < 0) {
                throw new IllegalArgumentException("negative count of materials");
            }
            delivered.merge(entry.getString("item"), count, Integer::sum);
        }
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
                tag.getDouble("carry"));
        return construction;
    }

    static Building readBuilding(CompoundTag tag) {
        require(tag, "id", "kingdom", "template", "dimension", "x", "y", "z", "box");
        if (!tag.hasUUID("kingdom")) {
            throw new IllegalArgumentException("the kingdom is not a UUID");
        }
        return new Building(tag.getLong("id"), tag.getUUID("kingdom"), tag.getString("template"), tag.getString("type"),
                tag.getString("category"), anchor(tag), new Placement(tag.getInt("rotation"), tag.getBoolean("mirror")), box(tag),
                tag.getLong("fingerprint"), tag.getLong("built"), tag.getBoolean("admin"),
                tag.contains("integrity") ? tag.getInt("integrity") : -1, tag.contains("checked") ? tag.getLong("checked") : -1);
    }

    /**
     * An empty register, for data upgraded from an older format.
     */
    public static CompoundTag empty() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("nextConstruction", 1);
        tag.putLong("nextBuilding", 1);
        tag.put("constructions", new ListTag());
        tag.put("buildings", new ListTag());
        return tag;
    }
}
