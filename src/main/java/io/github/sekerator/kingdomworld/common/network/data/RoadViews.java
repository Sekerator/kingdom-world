package io.github.sekerator.kingdomworld.common.network.data;

import io.github.sekerator.kingdomworld.core.road.Crossing;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadClass;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Cities, roads and robbers as the client sees them: the "Roads" tab of the throne screen, the plan of a road on the atlas
 * and what the atlas and the map mods draw.
 */
public final class RoadViews {
    /** Most rows of one list of the tab. */
    public static final int MAX_ROWS = 256;
    /** Most points of one line on the map. */
    public static final int MAX_POINTS = 4_096;
    private static final int NAME = 256;

    private RoadViews() {
    }

    static <T> void writeList(FriendlyByteBuf buf, List<T> list, BiConsumer<FriendlyByteBuf, T> writer) {
        buf.writeVarInt(list.size());
        for (T item : list) {
            writer.accept(buf, item);
        }
    }

    static <T> List<T> readList(FriendlyByteBuf buf, int max, Function<FriendlyByteBuf, T> reader) {
        int size = buf.readVarInt();
        if (size < 0 || size > max) {
            throw new IllegalArgumentException("Too many entries: " + size);
        }
        List<T> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(reader.apply(buf));
        }
        return list;
    }

    static void writeInts(FriendlyByteBuf buf, int[] values) {
        buf.writeVarInt(values.length);
        for (int value : values) {
            buf.writeInt(value);
        }
    }

    static int[] readInts(FriendlyByteBuf buf, int max) {
        int size = buf.readVarInt();
        if (size < 0 || size > max) {
            throw new IllegalArgumentException("Too many values: " + size);
        }
        int[] values = new int[size];
        for (int i = 0; i < size; i++) {
            values[i] = buf.readInt();
        }
        return values;
    }

    static <E extends Enum<E>> E readEnum(FriendlyByteBuf buf, Class<E> type) {
        return KingdomView.readEnum(buf, type);
    }

    // ---------------------------------------------------------------- the plan of a road

    /**
     * The plan of a road the server found, or why it found none.
     *
     * @param error     the id of the error, empty for a plan
     * @param searching whether the search still runs
     * @param materials items of each material (surface, bridge, fill) for each class (path, gravel, paved)
     * @param seconds   seconds of a builder's work for each class
     * @param builders  builders of the kingdom free for roads now
     * @param crossed   names of the kingdoms whose land it crosses
     * @param xs        points of the way to draw
     */
    public record PlanView(long planId, UUID kingdomId, String error, boolean searching, String from, String to, int length, int bridges,
                           int fills, int[] materials, int[] seconds, int builders, List<String> crossed, int[] xs, int[] zs) {
        public PlanView {
            crossed = List.copyOf(crossed);
        }

        public static PlanView failed(UUID kingdomId, String error) {
            return new PlanView(0, kingdomId, error, false, "", "", 0, 0, 0, new int[9], new int[3], 0, List.of(), new int[0], new int[0]);
        }

        public static PlanView searching(UUID kingdomId) {
            return new PlanView(0, kingdomId, "", true, "", "", 0, 0, 0, new int[9], new int[3], 0, List.of(), new int[0], new int[0]);
        }

        public int material(RoadClass roadClass, int material) {
            return materials[roadClass.ordinal() * 3 + material];
        }

        public int seconds(RoadClass roadClass) {
            return seconds[roadClass.ordinal()];
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(planId);
            buf.writeUUID(kingdomId);
            buf.writeUtf(error, 64);
            buf.writeBoolean(searching);
            buf.writeUtf(from, NAME);
            buf.writeUtf(to, NAME);
            buf.writeVarInt(length);
            buf.writeVarInt(bridges);
            buf.writeVarInt(fills);
            writeInts(buf, materials);
            writeInts(buf, seconds);
            buf.writeVarInt(builders);
            writeList(buf, crossed, (b, name) -> b.writeUtf(name, NAME));
            writeInts(buf, xs);
            writeInts(buf, zs);
        }

        public static PlanView decode(FriendlyByteBuf buf) {
            return new PlanView(buf.readVarLong(), buf.readUUID(), buf.readUtf(64), buf.readBoolean(), buf.readUtf(NAME), buf.readUtf(NAME),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), readInts(buf, 9), readInts(buf, 3), buf.readVarInt(),
                    readList(buf, 64, b -> b.readUtf(NAME)), readInts(buf, MAX_POINTS), readInts(buf, MAX_POINTS));
        }
    }

    // ---------------------------------------------------------------- the tab

    public record CityRow(long id, String name, int x, int y, int z, boolean capital, int roads) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeUtf(name, NAME);
            buf.writeInt(x);
            buf.writeInt(y);
            buf.writeInt(z);
            buf.writeBoolean(capital);
            buf.writeVarInt(roads);
        }

        static CityRow decode(FriendlyByteBuf buf) {
            return new CityRow(buf.readVarLong(), buf.readUtf(NAME), buf.readInt(), buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readVarInt());
        }
    }

    /**
     * A road of the kingdom.
     *
     * @param built     how much of it is paid for, in thousandths
     * @param condition its mean condition, in thousandths
     * @param ours      whether the kingdom planned it (and may give it up)
     * @param partners  names of the other kingdoms that build parts of it
     * @param worn      built sections below full condition and not being repaired
     * @param repairing sections being repaired
     * @param missing   items of each material the kingdom's part still takes
     */
    public record RoadRow(long id, String from, String to, RoadClass roadClass, int length, int built, int condition, Road.Status status,
                          boolean paused, boolean ours, String partners, Road.Mode mode, int worn, int repairing, int[] missing) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeUtf(from, NAME);
            buf.writeUtf(to, NAME);
            buf.writeEnum(roadClass);
            buf.writeVarInt(length);
            buf.writeVarInt(built);
            buf.writeVarInt(condition);
            buf.writeEnum(status);
            buf.writeBoolean(paused);
            buf.writeBoolean(ours);
            buf.writeUtf(partners, NAME);
            buf.writeEnum(mode);
            buf.writeVarInt(worn);
            buf.writeVarInt(repairing);
            writeInts(buf, missing);
        }

        static RoadRow decode(FriendlyByteBuf buf) {
            return new RoadRow(buf.readVarLong(), buf.readUtf(NAME), buf.readUtf(NAME), readEnum(buf, RoadClass.class), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), readEnum(buf, Road.Status.class), buf.readBoolean(), buf.readBoolean(), buf.readUtf(NAME),
                    readEnum(buf, Road.Mode.class), buf.readVarInt(), buf.readVarInt(), readInts(buf, 3));
        }
    }

    /**
     * A request to cross land: one the kingdom sent, or one it got.
     *
     * @param incoming whether another kingdom asks this one
     * @param ticksLeft ticks until it runs out, for an open one
     */
    public record CrossingRow(long id, long roadId, boolean incoming, String other, Road.Mode mode, Crossing.Status status, int sections,
                              long ticksLeft) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeVarLong(roadId);
            buf.writeBoolean(incoming);
            buf.writeUtf(other, NAME);
            buf.writeEnum(mode);
            buf.writeEnum(status);
            buf.writeVarInt(sections);
            buf.writeVarLong(Math.max(0, ticksLeft));
        }

        static CrossingRow decode(FriendlyByteBuf buf) {
            return new CrossingRow(buf.readVarLong(), buf.readVarLong(), buf.readBoolean(), buf.readUtf(NAME), readEnum(buf, Road.Mode.class),
                    readEnum(buf, Crossing.Status.class), buf.readVarInt(), buf.readVarLong());
        }
    }

    /**
     * A camp of robbers the kingdom knows of.
     *
     * @param distance blocks from the capital
     */
    public record CampRow(long id, int x, int z, int distance, int bandits, boolean leader, long loot, int raids) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeInt(x);
            buf.writeInt(z);
            buf.writeVarInt(distance);
            buf.writeVarInt(bandits);
            buf.writeBoolean(leader);
            buf.writeVarLong(loot);
            buf.writeVarInt(raids);
        }

        static CampRow decode(FriendlyByteBuf buf) {
            return new CampRow(buf.readVarLong(), buf.readInt(), buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                    buf.readVarLong(), buf.readVarInt());
        }
    }

    /**
     * The risk on the way of the caravans of a deal.
     *
     * @param permille the chance a caravan is robbed, in thousandths
     * @param onRoads  the share of the way on roads, in thousandths
     * @param minutes  minutes a caravan takes
     */
    public record RiskRow(long dealId, String partner, ItemStack goods, boolean selling, int permille, int onRoads, int minutes, boolean guarded) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(dealId);
            buf.writeUtf(partner, NAME);
            buf.writeItem(goods);
            buf.writeBoolean(selling);
            buf.writeVarInt(permille);
            buf.writeVarInt(onRoads);
            buf.writeVarInt(minutes);
            buf.writeBoolean(guarded);
        }

        static RiskRow decode(FriendlyByteBuf buf) {
            return new RiskRow(buf.readVarLong(), buf.readUtf(NAME), buf.readItem(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readBoolean());
        }
    }

    /**
     * A squad that could patrol a road.
     */
    public record SquadRow(long id, String name, int members) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeUtf(name, NAME);
            buf.writeVarInt(members);
        }

        static SquadRow decode(FriendlyByteBuf buf) {
            return new SquadRow(buf.readVarLong(), buf.readUtf(NAME), buf.readVarInt());
        }
    }

    /**
     * The "Roads" tab.
     *
     * @param canManage whether the viewer may plan, repair and answer
     * @param canOrder  whether the viewer may send squads to patrol
     * @param builders  builders free for roads in the last second
     */
    public record RoadTabView(UUID kingdomId, boolean canManage, boolean canOrder, int builders, int maxCities, int maxRoads,
                              List<CityRow> cities, List<RoadRow> roads, List<CrossingRow> crossings, List<CampRow> camps, List<RiskRow> risks,
                              List<SquadRow> squads) {
        public RoadTabView {
            cities = List.copyOf(cities);
            roads = List.copyOf(roads);
            crossings = List.copyOf(crossings);
            camps = List.copyOf(camps);
            risks = List.copyOf(risks);
            squads = List.copyOf(squads);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeBoolean(canManage);
            buf.writeBoolean(canOrder);
            buf.writeVarInt(builders);
            buf.writeVarInt(maxCities);
            buf.writeVarInt(maxRoads);
            writeList(buf, cities, (b, row) -> row.encode(b));
            writeList(buf, roads, (b, row) -> row.encode(b));
            writeList(buf, crossings, (b, row) -> row.encode(b));
            writeList(buf, camps, (b, row) -> row.encode(b));
            writeList(buf, risks, (b, row) -> row.encode(b));
            writeList(buf, squads, (b, row) -> row.encode(b));
        }

        public static RoadTabView decode(FriendlyByteBuf buf) {
            return new RoadTabView(buf.readUUID(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    readList(buf, MAX_ROWS, CityRow::decode), readList(buf, MAX_ROWS, RoadRow::decode), readList(buf, MAX_ROWS, CrossingRow::decode),
                    readList(buf, MAX_ROWS, CampRow::decode), readList(buf, MAX_ROWS, RiskRow::decode), readList(buf, MAX_ROWS, SquadRow::decode));
        }
    }

    // ---------------------------------------------------------------- the map

    public record MapCity(long id, UUID kingdomId, String name, String dimension, int x, int z, int color) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeUUID(kingdomId);
            buf.writeUtf(name, NAME);
            buf.writeUtf(dimension, NAME);
            buf.writeInt(x);
            buf.writeInt(z);
            buf.writeInt(color);
        }

        static MapCity decode(FriendlyByteBuf buf) {
            return new MapCity(buf.readVarLong(), buf.readUUID(), buf.readUtf(NAME), buf.readUtf(NAME), buf.readInt(), buf.readInt(), buf.readInt());
        }
    }

    /**
     * A road on the map: its way in points and how much of it is built.
     *
     * @param built how much is paid for, in thousandths
     */
    public record MapRoad(long id, UUID kingdomId, String dimension, RoadClass roadClass, int color, int built, int[] xs, int[] zs) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeUUID(kingdomId);
            buf.writeUtf(dimension, NAME);
            buf.writeEnum(roadClass);
            buf.writeInt(color);
            buf.writeVarInt(built);
            writeInts(buf, xs);
            writeInts(buf, zs);
        }

        static MapRoad decode(FriendlyByteBuf buf) {
            return new MapRoad(buf.readVarLong(), buf.readUUID(), buf.readUtf(NAME), readEnum(buf, RoadClass.class), buf.readInt(), buf.readVarInt(),
                    readInts(buf, MAX_POINTS), readInts(buf, MAX_POINTS));
        }
    }

    public record MapCamp(long id, String dimension, int x, int z, int size) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeUtf(dimension, NAME);
            buf.writeInt(x);
            buf.writeInt(z);
            buf.writeVarInt(size);
        }

        static MapCamp decode(FriendlyByteBuf buf) {
            return new MapCamp(buf.readVarLong(), buf.readUtf(NAME), buf.readInt(), buf.readInt(), buf.readVarInt());
        }
    }

    /**
     * What the maps draw of cities, roads and the camps the viewer's kingdom knows of.
     */
    public record MapData(List<MapCity> cities, List<MapRoad> roads, List<MapCamp> camps) {
        public static final MapData EMPTY = new MapData(List.of(), List.of(), List.of());

        public MapData {
            cities = List.copyOf(cities);
            roads = List.copyOf(roads);
            camps = List.copyOf(camps);
        }

        public void encode(FriendlyByteBuf buf) {
            writeList(buf, cities, (b, row) -> row.encode(b));
            writeList(buf, roads, (b, row) -> row.encode(b));
            writeList(buf, camps, (b, row) -> row.encode(b));
        }

        public static MapData decode(FriendlyByteBuf buf) {
            return new MapData(readList(buf, 4_096, MapCity::decode), readList(buf, 4_096, MapRoad::decode), readList(buf, 1_024, MapCamp::decode));
        }
    }
}
