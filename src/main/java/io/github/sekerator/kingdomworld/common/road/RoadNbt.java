package io.github.sekerator.kingdomworld.common.road;

import io.github.sekerator.kingdomworld.core.road.BanditCamp;
import io.github.sekerator.kingdomworld.core.road.CaravanRoute;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.Crossing;
import io.github.sekerator.kingdomworld.core.road.Endpoint;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadClass;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.road.TravelPlan;
import io.github.sekerator.kingdomworld.core.trade.Goods;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cities, roads, requests, camps and ways of caravans in saved data. Every entry is read on its own: an entry that cannot be
 * read is described and left out, and the caller keeps the whole data read-only.
 *
 * <pre>
 * cities: [ { id, kingdom, name, dim, x, y, z, founded } ]
 * roads: [ { id, kingdom, class, dim, from: { kind, city, kingdom?, x, z }, to, cells: [x, y, z, kind * 256 + depth, ...], mode,
 *            created, status, paused, traffic, sections: [ { builder, paid, placed, condition, repairing, repairPaid,
 *            pool: [ { item, count } ] } ] } ]
 * crossings: [ { id, road, requester, owner, mode, status, sections, created, expires } ]
 * camps: [ { id, dim, x, y, z, bandits, leader, founded, lastGrowth, built, raids, lastRaid, known: [uuid],
 *            loot: [ { item, count, data } ], structure: [pos], chest? } ]
 * routes: [ { caravan, dim, depart, checked, xs, zs, ticks, roads, sections, classes, lengths, exactLengths } ]
 * walls: [ { id, city, kingdom, class, dim, cx, cz, cells: [x, y, z, ...], gates: [cell], created, building, placed: [pos],
 *           sections: [ { paid, placed, pool } ] } ]
 * consents: ["owner>requester"]
 * nextCity, nextRoad, nextCrossing, nextCamp, nextWall
 * </pre>
 */
final class RoadNbt {
    private RoadNbt() {
    }

    static void write(RoadState state, CompoundTag tag) {
        ListTag cities = new ListTag();
        for (City city : state.cities()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("id", city.id());
            entry.putUUID("kingdom", city.kingdomId());
            entry.putString("name", city.name());
            entry.putString("dim", city.dimension());
            entry.putInt("x", city.x());
            entry.putInt("y", city.y());
            entry.putInt("z", city.z());
            entry.putLong("founded", city.founded());
            cities.add(entry);
        }
        tag.put("cities", cities);
        ListTag roads = new ListTag();
        for (Road road : state.roads()) {
            roads.add(writeRoad(road));
        }
        tag.put("roads", roads);
        ListTag crossings = new ListTag();
        for (Crossing crossing : state.crossings()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("id", crossing.id());
            entry.putLong("road", crossing.roadId());
            entry.putUUID("requester", crossing.requester());
            entry.putUUID("owner", crossing.owner());
            entry.putString("mode", crossing.mode().id());
            entry.putString("status", crossing.status().id());
            entry.putInt("sections", crossing.sections());
            entry.putLong("created", crossing.created());
            entry.putLong("expires", crossing.expires());
            crossings.add(entry);
        }
        tag.put("crossings", crossings);
        ListTag camps = new ListTag();
        for (BanditCamp camp : state.camps()) {
            camps.add(writeCamp(camp));
        }
        tag.put("camps", camps);
        ListTag routes = new ListTag();
        for (CaravanRoute route : state.routes()) {
            routes.add(writeRoute(route));
        }
        tag.put("routes", routes);
        ListTag walls = new ListTag();
        for (io.github.sekerator.kingdomworld.core.road.Wall wall : state.walls()) {
            walls.add(writeWall(wall));
        }
        tag.put("walls", walls);
        ListTag consents = new ListTag();
        for (String pair : state.consents()) {
            consents.add(net.minecraft.nbt.StringTag.valueOf(pair));
        }
        tag.put("consents", consents);
        tag.putLong("nextWall", state.counters()[4]);
        long[] counters = state.counters();
        tag.putLong("nextCity", counters[0]);
        tag.putLong("nextRoad", counters[1]);
        tag.putLong("nextCrossing", counters[2]);
        tag.putLong("nextCamp", counters[3]);
    }

    private static CompoundTag writeEndpoint(Endpoint endpoint) {
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", endpoint.kind().id());
        tag.putLong("city", endpoint.city());
        if (endpoint.kingdom() != null) {
            tag.putUUID("kingdom", endpoint.kingdom());
        }
        tag.putInt("x", endpoint.x());
        tag.putInt("z", endpoint.z());
        return tag;
    }

    private static Endpoint readEndpoint(CompoundTag tag) {
        Endpoint.Kind kind = Endpoint.Kind.valueOf(tag.getString("kind").toUpperCase(java.util.Locale.ROOT));
        return new Endpoint(kind, tag.getLong("city"), tag.hasUUID("kingdom") ? tag.getUUID("kingdom") : null, tag.getInt("x"), tag.getInt("z"));
    }

    private static CompoundTag writeRoad(Road road) {
        CompoundTag entry = new CompoundTag();
        entry.putLong("id", road.id());
        entry.putUUID("kingdom", road.kingdomId());
        entry.putString("class", road.roadClass().id());
        entry.putString("dim", road.dimension());
        entry.put("from", writeEndpoint(road.from()));
        entry.put("to", writeEndpoint(road.to()));
        int[] cells = new int[road.cells().size() * 4];
        for (int i = 0; i < road.cells().size(); i++) {
            RouteCell cell = road.cells().get(i);
            cells[i * 4] = cell.x();
            cells[i * 4 + 1] = cell.y();
            cells[i * 4 + 2] = cell.z();
            cells[i * 4 + 3] = cell.kind().ordinal() * 256 + Math.min(255, cell.depth());
        }
        entry.putIntArray("cells", cells);
        entry.putString("mode", road.mode().id());
        entry.putLong("created", road.created());
        entry.putString("status", road.status().id());
        entry.putBoolean("paused", road.paused());
        entry.putInt("traffic", road.traffic());
        ListTag sections = new ListTag();
        for (Road.Section section : road.sections()) {
            CompoundTag s = new CompoundTag();
            s.putUUID("builder", section.builder());
            s.putInt("paid", section.paid());
            s.putInt("placed", section.placed());
            s.putDouble("condition", section.condition());
            s.putBoolean("repairing", section.repairing());
            s.putInt("repairPaid", section.repairPaid());
            ListTag pool = new ListTag();
            section.pool().forEach((item, count) -> {
                CompoundTag p = new CompoundTag();
                p.putString("item", item);
                p.putInt("count", count);
                pool.add(p);
            });
            s.put("pool", pool);
            sections.add(s);
        }
        entry.put("sections", sections);
        return entry;
    }

    private static Road readRoad(CompoundTag entry) {
        int[] packed = entry.getIntArray("cells");
        if (packed.length % 4 != 0) {
            throw new IllegalArgumentException("cells of a road are damaged");
        }
        List<RouteCell> cells = new ArrayList<>();
        for (int i = 0; i < packed.length; i += 4) {
            int kind = packed[i + 3] / 256;
            if (kind < 0 || kind >= RouteCell.Kind.values().length) {
                throw new IllegalArgumentException("unknown kind of a block of road");
            }
            cells.add(new RouteCell(packed[i], packed[i + 1], packed[i + 2], RouteCell.Kind.values()[kind], packed[i + 3] % 256, null));
        }
        RoadClass roadClass = RoadClass.byId(entry.getString("class")).orElseThrow(() -> new IllegalArgumentException("unknown class"));
        Road.Mode mode = Road.Mode.valueOf(entry.getString("mode").toUpperCase(java.util.Locale.ROOT));
        Road.Status status = Road.Status.valueOf(entry.getString("status").toUpperCase(java.util.Locale.ROOT));
        ListTag sections = entry.getList("sections", Tag.TAG_COMPOUND);
        List<UUID> builders = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            builders.add(sections.getCompound(i).getUUID("builder"));
        }
        Road road = new Road(entry.getLong("id"), entry.getUUID("kingdom"), roadClass, entry.getString("dim"),
                readEndpoint(entry.getCompound("from")), readEndpoint(entry.getCompound("to")), cells, mode, entry.getLong("created"), status,
                builders);
        if (sections.size() != road.sections().size()) {
            throw new IllegalArgumentException("road " + road.id() + " has " + sections.size() + " sections, its way makes "
                    + road.sections().size());
        }
        for (int i = 0; i < sections.size(); i++) {
            CompoundTag s = sections.getCompound(i);
            Map<String, Integer> pool = new LinkedHashMap<>();
            ListTag items = s.getList("pool", Tag.TAG_COMPOUND);
            for (int j = 0; j < items.size(); j++) {
                CompoundTag p = items.getCompound(j);
                pool.merge(p.getString("item"), Math.max(0, p.getInt("count")), Integer::sum);
            }
            road.section(i).restore(s.getInt("paid"), s.getInt("placed"), s.getDouble("condition"), s.getBoolean("repairing"),
                    s.getInt("repairPaid"), pool);
        }
        road.setPaused(entry.getBoolean("paused"));
        road.restoreTraffic(entry.getInt("traffic"));
        return road;
    }

    private static CompoundTag writeWall(io.github.sekerator.kingdomworld.core.road.Wall wall) {
        CompoundTag entry = new CompoundTag();
        entry.putLong("id", wall.id());
        entry.putLong("city", wall.cityId());
        entry.putUUID("kingdom", wall.kingdomId());
        entry.putString("class", wall.wallClass().id());
        entry.putString("dim", wall.dimension());
        entry.putInt("cx", wall.centerX());
        entry.putInt("cz", wall.centerZ());
        int[] cells = new int[wall.cells().size() * 3];
        for (int i = 0; i < wall.cells().size(); i++) {
            RouteCell cell = wall.cells().get(i);
            cells[i * 3] = cell.x();
            cells[i * 3 + 1] = cell.y();
            cells[i * 3 + 2] = cell.z();
        }
        entry.putIntArray("cells", cells);
        entry.putIntArray("gates", wall.gates().stream().mapToInt(Integer::intValue).toArray());
        entry.putLong("created", wall.created());
        entry.putLong("building", wall.buildingId());
        entry.putLongArray("placed", wall.placedBlocks());
        ListTag sections = new ListTag();
        for (Road.Section section : wall.sections()) {
            CompoundTag s = new CompoundTag();
            s.putInt("paid", section.paid());
            s.putInt("placed", section.placed());
            ListTag pool = new ListTag();
            section.pool().forEach((item, count) -> {
                CompoundTag p = new CompoundTag();
                p.putString("item", item);
                p.putInt("count", count);
                pool.add(p);
            });
            s.put("pool", pool);
            sections.add(s);
        }
        entry.put("sections", sections);
        return entry;
    }

    private static io.github.sekerator.kingdomworld.core.road.Wall readWall(CompoundTag entry) {
        int[] packed = entry.getIntArray("cells");
        if (packed.length % 3 != 0) {
            throw new IllegalArgumentException("blocks of a wall are damaged");
        }
        List<RouteCell> cells = new ArrayList<>();
        for (int i = 0; i < packed.length; i += 3) {
            cells.add(new RouteCell(packed[i], packed[i + 1], packed[i + 2], RouteCell.Kind.GROUND, 0, null));
        }
        List<Integer> gates = new ArrayList<>();
        for (int gate : entry.getIntArray("gates")) {
            gates.add(gate);
        }
        io.github.sekerator.kingdomworld.core.road.Wall wall = new io.github.sekerator.kingdomworld.core.road.Wall(entry.getLong("id"),
                entry.getLong("city"), entry.getUUID("kingdom"),
                io.github.sekerator.kingdomworld.core.road.Wall.WallClass.byId(entry.getString("class"))
                        .orElseThrow(() -> new IllegalArgumentException("unknown class of wall")),
                entry.getString("dim"), entry.getInt("cx"), entry.getInt("cz"), cells, gates, entry.getLong("created"));
        wall.setBuildingId(entry.getLong("building"));
        wall.restorePlaced(entry.getLongArray("placed"));
        ListTag sections = entry.getList("sections", Tag.TAG_COMPOUND);
        for (int i = 0; i < sections.size() && i < wall.sections().size(); i++) {
            CompoundTag s = sections.getCompound(i);
            Map<String, Integer> pool = new LinkedHashMap<>();
            ListTag items = s.getList("pool", Tag.TAG_COMPOUND);
            for (int j = 0; j < items.size(); j++) {
                pool.merge(items.getCompound(j).getString("item"), Math.max(0, items.getCompound(j).getInt("count")), Integer::sum);
            }
            wall.sections().get(i).restore(s.getInt("paid"), s.getInt("placed"), 1.0, false, 0, pool);
        }
        return wall;
    }

    private static CompoundTag writeCamp(BanditCamp camp) {
        CompoundTag entry = new CompoundTag();
        entry.putLong("id", camp.id());
        entry.putString("dim", camp.dimension());
        entry.putInt("x", camp.x());
        entry.putInt("y", camp.y());
        entry.putInt("z", camp.z());
        entry.putInt("bandits", camp.bandits());
        entry.putBoolean("leader", camp.leader());
        entry.putLong("founded", camp.founded());
        entry.putLong("lastGrowth", camp.lastGrowth());
        entry.putBoolean("built", camp.built());
        entry.putInt("raids", camp.raids());
        entry.putLong("lastRaid", camp.lastRaid());
        ListTag known = new ListTag();
        for (UUID id : camp.known()) {
            CompoundTag k = new CompoundTag();
            k.putUUID("id", id);
            known.add(k);
        }
        entry.put("known", known);
        entry.put("loot", writeGoods(camp.loot()));
        entry.put("structure", new LongArrayTag(camp.structure().stream().mapToLong(Long::longValue).toArray()));
        camp.chest().ifPresent(chest -> entry.putLong("chest", chest));
        return entry;
    }

    static ListTag writeGoods(List<Goods> goods) {
        ListTag list = new ListTag();
        for (Goods entry : goods) {
            CompoundTag g = new CompoundTag();
            g.putString("item", entry.item());
            g.putInt("count", entry.count());
            if (entry.hasData()) {
                g.putString("data", entry.data());
            }
            list.add(g);
        }
        return list;
    }

    static List<Goods> readGoods(ListTag list) {
        List<Goods> goods = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag g = list.getCompound(i);
            goods.add(new Goods(g.getString("item"), g.getInt("count"), g.getString("data")));
        }
        return goods;
    }

    private static BanditCamp readCamp(CompoundTag entry) {
        BanditCamp camp = new BanditCamp(entry.getLong("id"), entry.getString("dim"), entry.getInt("x"), entry.getInt("y"), entry.getInt("z"),
                entry.getInt("bandits"), entry.getBoolean("leader"), entry.getLong("founded"));
        Set<UUID> known = new LinkedHashSet<>();
        ListTag list = entry.getList("known", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            known.add(list.getCompound(i).getUUID("id"));
        }
        List<Goods> loot = readGoods(entry.getList("loot", Tag.TAG_COMPOUND));
        if (loot.size() > BanditCamp.MAX_LOOT) {
            throw new IllegalArgumentException("camp " + camp.id() + " holds more goods than a camp keeps");
        }
        camp.restore(entry.getLong("lastGrowth"), loot, known, entry.getBoolean("built"), entry.getInt("raids"), entry.getLong("lastRaid"));
        List<Long> structure = new ArrayList<>();
        for (long position : entry.getLongArray("structure")) {
            structure.add(position);
        }
        camp.setStructure(structure);
        camp.setChest(entry.contains("chest", Tag.TAG_LONG) ? entry.getLong("chest") : null);
        return camp;
    }

    private static CompoundTag writeRoute(CaravanRoute route) {
        CompoundTag entry = new CompoundTag();
        entry.putLong("caravan", route.caravanId());
        entry.putString("dim", route.dimension());
        entry.putLong("depart", route.departTime());
        entry.putInt("checked", route.checked());
        List<TravelPlan.Point> points = route.plan().points();
        int[] xs = new int[points.size()];
        int[] zs = new int[points.size()];
        long[] ticks = new long[points.size()];
        for (int i = 0; i < points.size(); i++) {
            xs[i] = points.get(i).x();
            zs[i] = points.get(i).z();
            ticks[i] = points.get(i).tick();
        }
        entry.putIntArray("xs", xs);
        entry.putIntArray("zs", zs);
        entry.put("ticks", new LongArrayTag(ticks));
        List<TravelPlan.Piece> pieces = route.plan().pieces();
        long[] roads = new long[pieces.size()];
        int[] sections = new int[pieces.size()];
        int[] classes = new int[pieces.size()];
        int[] lengths = new int[pieces.size()];
        long[] exact = new long[pieces.size()];
        for (int i = 0; i < pieces.size(); i++) {
            TravelPlan.Piece piece = pieces.get(i);
            roads[i] = piece.roadId();
            sections[i] = piece.section();
            classes[i] = piece.roadClass() == null ? -1 : piece.roadClass().ordinal();
            lengths[i] = (int) Math.round(piece.length() * 16.0);
            exact[i] = Double.doubleToLongBits(piece.length());
        }
        entry.put("roads", new LongArrayTag(roads));
        entry.putIntArray("sections", sections);
        entry.putIntArray("classes", classes);
        entry.putIntArray("lengths", lengths);
        // The lengths as they are, so that the way reads back the same; "lengths" stays for data written before.
        entry.put("exactLengths", new LongArrayTag(exact));
        return entry;
    }

    private static CaravanRoute readRoute(CompoundTag entry) {
        int[] xs = entry.getIntArray("xs");
        int[] zs = entry.getIntArray("zs");
        long[] ticks = entry.getLongArray("ticks");
        long[] roads = entry.getLongArray("roads");
        int[] sections = entry.getIntArray("sections");
        int[] classes = entry.getIntArray("classes");
        int[] lengths = entry.getIntArray("lengths");
        long[] exact = entry.getLongArray("exactLengths");
        int pieces = xs.length - 1;
        if (zs.length != xs.length || ticks.length != xs.length || roads.length != pieces || sections.length != pieces
                || classes.length != pieces || lengths.length != pieces) {
            throw new IllegalArgumentException("the way of a caravan is damaged");
        }
        List<TravelPlan.Point> points = new ArrayList<>();
        for (int i = 0; i < xs.length; i++) {
            points.add(new TravelPlan.Point(xs[i], zs[i], ticks[i]));
        }
        List<TravelPlan.Piece> list = new ArrayList<>();
        for (int i = 0; i < pieces; i++) {
            RoadClass roadClass = classes[i] < 0 || classes[i] >= RoadClass.values().length ? null : RoadClass.values()[classes[i]];
            double length = exact.length == pieces ? Double.longBitsToDouble(exact[i]) : lengths[i] / 16.0;
            if (!Double.isFinite(length) || length < 0) {
                throw new IllegalArgumentException("the way of a caravan is damaged");
            }
            list.add(new TravelPlan.Piece(roads[i], sections[i], roadClass, length));
        }
        return new CaravanRoute(entry.getLong("caravan"), entry.getString("dim"), entry.getLong("depart"), new TravelPlan(points, list),
                entry.getInt("checked"));
    }

    /**
     * Reads everything it can into the state.
     *
     * @return what could not be read
     */
    static List<String> read(CompoundTag tag, RoadState state) {
        List<String> problems = new ArrayList<>();
        ListTag cities = tag.getList("cities", Tag.TAG_COMPOUND);
        for (int i = 0; i < cities.size(); i++) {
            try {
                CompoundTag entry = cities.getCompound(i);
                state.putCity(new City(entry.getLong("id"), entry.getUUID("kingdom"), entry.getString("name"), entry.getString("dim"),
                        entry.getInt("x"), entry.getInt("y"), entry.getInt("z"), entry.getLong("founded")));
            } catch (RuntimeException e) {
                problems.add("city " + i + ": " + e.getMessage());
            }
        }
        ListTag roads = tag.getList("roads", Tag.TAG_COMPOUND);
        for (int i = 0; i < roads.size(); i++) {
            try {
                state.putRoad(readRoad(roads.getCompound(i)));
            } catch (RuntimeException e) {
                problems.add("road " + i + ": " + e.getMessage());
            }
        }
        ListTag crossings = tag.getList("crossings", Tag.TAG_COMPOUND);
        for (int i = 0; i < crossings.size(); i++) {
            try {
                CompoundTag entry = crossings.getCompound(i);
                state.putCrossing(new Crossing(entry.getLong("id"), entry.getLong("road"), entry.getUUID("requester"), entry.getUUID("owner"),
                        Road.Mode.valueOf(entry.getString("mode").toUpperCase(java.util.Locale.ROOT)),
                        Crossing.Status.valueOf(entry.getString("status").toUpperCase(java.util.Locale.ROOT)), entry.getInt("sections"),
                        entry.getLong("created"), entry.getLong("expires")));
            } catch (RuntimeException e) {
                problems.add("request " + i + ": " + e.getMessage());
            }
        }
        ListTag camps = tag.getList("camps", Tag.TAG_COMPOUND);
        for (int i = 0; i < camps.size(); i++) {
            try {
                state.putCamp(readCamp(camps.getCompound(i)));
            } catch (RuntimeException e) {
                problems.add("camp " + i + ": " + e.getMessage());
            }
        }
        ListTag routes = tag.getList("routes", Tag.TAG_COMPOUND);
        for (int i = 0; i < routes.size(); i++) {
            try {
                state.putRoute(readRoute(routes.getCompound(i)));
            } catch (RuntimeException e) {
                problems.add("way of a caravan " + i + ": " + e.getMessage());
            }
        }
        ListTag walls = tag.getList("walls", Tag.TAG_COMPOUND);
        for (int i = 0; i < walls.size(); i++) {
            try {
                state.putWall(readWall(walls.getCompound(i)));
            } catch (RuntimeException e) {
                problems.add("wall " + i + ": " + e.getMessage());
            }
        }
        ListTag consents = tag.getList("consents", Tag.TAG_STRING);
        for (int i = 0; i < consents.size(); i++) {
            state.restoreConsent(consents.getString(i));
        }
        state.restoreWallCounter(tag.getLong("nextWall"));
        state.restoreCounters(tag.getLong("nextCity"), tag.getLong("nextRoad"), tag.getLong("nextCrossing"), tag.getLong("nextCamp"));
        return problems;
    }
}
