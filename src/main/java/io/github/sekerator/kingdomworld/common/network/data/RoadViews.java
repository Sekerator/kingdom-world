package io.github.sekerator.kingdomworld.common.network.data;

import io.github.sekerator.kingdomworld.core.road.CityLevel;
import io.github.sekerator.kingdomworld.core.road.Crossing;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadClass;
import io.github.sekerator.kingdomworld.core.road.Wall;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

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

    /**
     * A city of the kingdom, or its capital (id 0).
     *
     * @param level     the level of the city by its people and buildings
     * @param people    the named citizens whose home is in the city
     * @param wallId    its wall, 0 for none
     * @param wallClass the class of the wall, {@code null} for none
     * @param wallBuilt how much of the wall stands, in thousandths
     * @param gates     gates in the wall
     * @param folk      its townsfolk, who live there as a number
     * @param beds      the beds of the kingdom in it
     * @param freeBeds  of those, the ones free of named citizens and townsfolk alike
     */
    public record CityRow(long id, String name, int x, int y, int z, boolean capital, int roads, CityLevel level, int people, int buildings,
                          long wallId, @Nullable Wall.WallClass wallClass, int wallBuilt, int gates, int folk, int beds, int freeBeds) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeUtf(name, NAME);
            buf.writeInt(x);
            buf.writeInt(y);
            buf.writeInt(z);
            buf.writeBoolean(capital);
            buf.writeVarInt(roads);
            buf.writeEnum(level);
            buf.writeVarInt(people);
            buf.writeVarInt(buildings);
            buf.writeVarLong(wallId);
            buf.writeVarInt(wallClass == null ? 0 : wallClass.ordinal() + 1);
            buf.writeVarInt(wallBuilt);
            buf.writeVarInt(gates);
            buf.writeVarInt(Math.max(0, folk));
            buf.writeVarInt(Math.max(0, beds));
            buf.writeVarInt(Math.max(0, freeBeds));
        }

        static CityRow decode(FriendlyByteBuf buf) {
            long id = buf.readVarLong();
            String name = buf.readUtf(NAME);
            int x = buf.readInt();
            int y = buf.readInt();
            int z = buf.readInt();
            boolean capital = buf.readBoolean();
            int roads = buf.readVarInt();
            CityLevel level = readEnum(buf, CityLevel.class);
            int people = buf.readVarInt();
            int buildings = buf.readVarInt();
            long wallId = buf.readVarLong();
            int wallClass = buf.readVarInt();
            Wall.WallClass[] classes = Wall.WallClass.values();
            return new CityRow(id, name, x, y, z, capital, roads, level, people, buildings, wallId,
                    wallClass <= 0 || wallClass > classes.length ? null : classes[wallClass - 1], buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
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
    /**
     * @param cityDistance the least distance between cities along x or z the server keeps, in blocks
     * @param townPeople   the citizens and {@code townBuildings} the buildings that make a city a town; {@code cityPeople} and
     *                     {@code cityBuildings} a large city
     */
    public record RoadTabView(UUID kingdomId, boolean canManage, boolean canOrder, int builders, int maxCities, int maxRoads,
                              List<CityRow> cities, List<RoadRow> roads, List<CrossingRow> crossings, List<CampRow> camps, List<RiskRow> risks,
                              List<SquadRow> squads, int cityDistance, int townPeople, int townBuildings, int cityPeople, int cityBuildings) {
        public RoadTabView(UUID kingdomId, boolean canManage, boolean canOrder, int builders, int maxCities, int maxRoads, List<CityRow> cities,
                           List<RoadRow> roads, List<CrossingRow> crossings, List<CampRow> camps, List<RiskRow> risks, List<SquadRow> squads) {
            this(kingdomId, canManage, canOrder, builders, maxCities, maxRoads, cities, roads, crossings, camps, risks, squads, 112, 12, 6, 40, 20);
        }

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
            buf.writeVarInt(cityDistance);
            buf.writeVarInt(townPeople);
            buf.writeVarInt(townBuildings);
            buf.writeVarInt(cityPeople);
            buf.writeVarInt(cityBuildings);
        }

        public static RoadTabView decode(FriendlyByteBuf buf) {
            return new RoadTabView(buf.readUUID(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    readList(buf, MAX_ROWS, CityRow::decode), readList(buf, MAX_ROWS, RoadRow::decode), readList(buf, MAX_ROWS, CrossingRow::decode),
                    readList(buf, MAX_ROWS, CampRow::decode), readList(buf, MAX_ROWS, RiskRow::decode), readList(buf, MAX_ROWS, SquadRow::decode),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }
    }

    // ---------------------------------------------------------------- the map

    /**
     * A city on the map; id 0 stands for a capital, which the maps draw from the kingdoms and only add the level to.
     *
     * @param kingdom    the size of the kingdom of the world a capital belongs to, from the first minute of the world; {@code null}
     *                   for other cities and for kingdoms of players
     * @param settlement what a settlement of a state of the world is, for the settlements of the plan; {@code null} for others
     */
    public record MapCity(long id, UUID kingdomId, String name, String dimension, int x, int z, int color, CityLevel level,
                          @Nullable MapKingdom kingdom, @Nullable MapSettlement settlement) {
        public MapCity(long id, UUID kingdomId, String name, String dimension, int x, int z, int color, CityLevel level) {
            this(id, kingdomId, name, dimension, x, z, color, level, null, null);
        }

        public MapCity(long id, UUID kingdomId, String name, String dimension, int x, int z, int color, CityLevel level,
                       @Nullable MapKingdom kingdom) {
            this(id, kingdomId, name, dimension, x, z, color, level, kingdom, null);
        }

        /**
         * Whether it is the capital of a state of the plan, which the maps draw from the kingdoms as any capital.
         */
        public boolean capitalOfPlan() {
            return settlement != null && settlement.tier() == io.github.sekerator.kingdomworld.core.land.Tier.CAPITAL;
        }

        /**
         * The same city under another name.
         */
        public MapCity named(String other) {
            return new MapCity(id, kingdomId, other, dimension, x, z, color, level, kingdom, settlement);
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeUUID(kingdomId);
            buf.writeUtf(name, NAME);
            buf.writeUtf(dimension, NAME);
            buf.writeInt(x);
            buf.writeInt(z);
            buf.writeInt(color);
            buf.writeEnum(level);
            buf.writeBoolean(kingdom != null);
            if (kingdom != null) {
                kingdom.encode(buf);
            }
            buf.writeBoolean(settlement != null);
            if (settlement != null) {
                settlement.encode(buf);
            }
        }

        static MapCity decode(FriendlyByteBuf buf) {
            return new MapCity(buf.readVarLong(), buf.readUUID(), buf.readUtf(NAME), buf.readUtf(NAME), buf.readInt(), buf.readInt(), buf.readInt(),
                    readEnum(buf, CityLevel.class), buf.readBoolean() ? MapKingdom.decode(buf) : null, buf.readBoolean() ? MapSettlement.decode(buf) : null);
        }
    }

    /**
     * A settlement of a state of the world on the map: its rank, its numbers and its province.
     *
     * @param data whether it lives as data, not raised in the world yet: the maps draw it dashed
     */
    public record MapSettlement(io.github.sekerator.kingdomworld.core.land.Tier tier, int population, int guards, int militia, int province,
                                boolean data, int named) {
        public MapSettlement {
            java.util.Objects.requireNonNull(tier, "tier");
            if (population < 0 || population > NpcKingdomInfo.MAX_COUNT || guards < 0 || guards > NpcKingdomInfo.MAX_COUNT || militia < 0
                    || militia > NpcKingdomInfo.MAX_COUNT || province < 0 || named < 0 || named > NpcKingdomInfo.MAX_COUNT) {
                throw new IllegalArgumentException("Settlement out of range");
            }
        }

        public MapSettlement(io.github.sekerator.kingdomworld.core.land.Tier tier, int population, int guards, int militia, int province,
                             boolean data) {
            this(tier, population, guards, militia, province, data, 0);
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeEnum(tier);
            buf.writeVarInt(population);
            buf.writeVarInt(guards);
            buf.writeVarInt(militia);
            buf.writeVarInt(province);
            buf.writeBoolean(data);
            buf.writeVarInt(named);
        }

        static MapSettlement decode(FriendlyByteBuf buf) {
            return new MapSettlement(readEnum(buf, io.github.sekerator.kingdomworld.core.land.Tier.class), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt());
        }
    }

    /**
     * How large a kingdom of the world is, for the list of kingdoms of the atlas: its level and its people and soldiers, those it
     * is expected to have while its capital is being built and the real ones once it stands.
     */
    public record MapKingdom(io.github.sekerator.kingdomworld.core.npc.NpcLevel level, int people, int soldiers, boolean raising,
                             io.github.sekerator.kingdomworld.core.guild.Rank adventurers, io.github.sekerator.kingdomworld.core.guild.Rank mages) {
        public MapKingdom(io.github.sekerator.kingdomworld.core.npc.NpcLevel level, int people, int soldiers, boolean raising) {
            this(level, people, soldiers, raising, io.github.sekerator.kingdomworld.core.guild.Rank.F, io.github.sekerator.kingdomworld.core.guild.Rank.F);
        }

        public MapKingdom {
            java.util.Objects.requireNonNull(level, "level");
            java.util.Objects.requireNonNull(adventurers, "adventurers");
            java.util.Objects.requireNonNull(mages, "mages");
            if (people < 0 || people > NpcKingdomInfo.MAX_COUNT || soldiers < 0 || soldiers > NpcKingdomInfo.MAX_COUNT) {
                throw new IllegalArgumentException("Kingdom of the world out of range");
            }
        }

        /**
         * The size of the kingdom of the world players are shown.
         */
        public static MapKingdom of(NpcKingdomInfo info) {
            return new MapKingdom(info.level(), info.people(), info.soldiers(), info.raising(), info.adventurers(), info.mages());
        }

        /**
         * Whether the guilds of the kingdom have their ranks, to be shown.
         */
        public boolean ranked() {
            return adventurers != io.github.sekerator.kingdomworld.core.guild.Rank.F || mages != io.github.sekerator.kingdomworld.core.guild.Rank.F;
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeEnum(level);
            buf.writeVarInt(people);
            buf.writeVarInt(soldiers);
            buf.writeBoolean(raising);
            buf.writeEnum(adventurers);
            buf.writeEnum(mages);
        }

        static MapKingdom decode(FriendlyByteBuf buf) {
            io.github.sekerator.kingdomworld.core.npc.NpcLevel level = readEnum(buf, io.github.sekerator.kingdomworld.core.npc.NpcLevel.class);
            int people = buf.readVarInt();
            int soldiers = buf.readVarInt();
            if (people < 0 || people > NpcKingdomInfo.MAX_COUNT || soldiers < 0 || soldiers > NpcKingdomInfo.MAX_COUNT) {
                throw new IllegalArgumentException("People out of range: " + people + ", " + soldiers);
            }
            boolean raising = buf.readBoolean();
            io.github.sekerator.kingdomworld.core.guild.Rank adventurers = readEnum(buf, io.github.sekerator.kingdomworld.core.guild.Rank.class);
            io.github.sekerator.kingdomworld.core.guild.Rank mages = readEnum(buf, io.github.sekerator.kingdomworld.core.guild.Rank.class);
            return new MapKingdom(level, people, soldiers, raising, adventurers, mages);
        }
    }

    /**
     * The wall of a city on the map: a square ring around its middle with its gates.
     *
     * @param built how much of it stands, in thousandths
     */
    public record MapWall(long id, String dimension, int x, int z, int radius, Wall.WallClass wallClass, int built, int[] gateXs, int[] gateZs) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeUtf(dimension, NAME);
            buf.writeInt(x);
            buf.writeInt(z);
            buf.writeVarInt(radius);
            buf.writeEnum(wallClass);
            buf.writeVarInt(built);
            writeInts(buf, gateXs);
            writeInts(buf, gateZs);
        }

        static MapWall decode(FriendlyByteBuf buf) {
            return new MapWall(buf.readVarLong(), buf.readUtf(NAME), buf.readInt(), buf.readInt(), buf.readVarInt(), readEnum(buf, Wall.WallClass.class),
                    buf.readVarInt(), readInts(buf, 64), readInts(buf, 64));
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof MapWall wall && id == wall.id && dimension.equals(wall.dimension) && x == wall.x && z == wall.z
                    && radius == wall.radius && wallClass == wall.wallClass && built == wall.built && java.util.Arrays.equals(gateXs, wall.gateXs)
                    && java.util.Arrays.equals(gateZs, wall.gateZs);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(id, dimension, x, z, radius, wallClass, built, java.util.Arrays.hashCode(gateXs), java.util.Arrays.hashCode(gateZs));
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

        @Override
        public boolean equals(Object other) {
            return other instanceof MapRoad road && id == road.id && kingdomId.equals(road.kingdomId) && dimension.equals(road.dimension)
                    && roadClass == road.roadClass && color == road.color && built == road.built && java.util.Arrays.equals(xs, road.xs)
                    && java.util.Arrays.equals(zs, road.zs);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(id, kingdomId, dimension, roadClass, color, built, java.util.Arrays.hashCode(xs), java.util.Arrays.hashCode(zs));
        }
    }

    /**
     * The cities and roads of one kingdom on the map, as one part: the maps get again only the parts whose version changed.
     */
    public record MapPart(UUID kingdomId, long version, List<MapCity> cities, List<MapRoad> roads) {
        public MapPart {
            java.util.Objects.requireNonNull(kingdomId, "kingdom");
            cities = List.copyOf(cities);
            roads = List.copyOf(roads);
        }

        /**
         * About how many bytes the part takes in a packet.
         */
        public int weight() {
            int weight = 32;
            for (MapCity city : cities) {
                weight += 64 + city.name().length() + city.dimension().length();
            }
            for (MapRoad road : roads) {
                weight += 48 + road.dimension().length() + 10 * road.xs().length;
            }
            return weight;
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeVarLong(version);
            writeList(buf, cities, (b, row) -> row.encode(b));
            writeList(buf, roads, (b, row) -> row.encode(b));
        }

        static MapPart decode(FriendlyByteBuf buf) {
            return new MapPart(buf.readUUID(), buf.readVarLong(), readList(buf, 4_096, MapCity::decode), readList(buf, 4_096, MapRoad::decode));
        }
    }

    /**
     * A change of what the maps draw: the parts of kingdoms that changed, the kingdoms gone from the maps, and the camps and walls
     * as a whole; with {@code reset} the maps forget the parts they had first.
     */
    public record MapChange(boolean reset, List<MapPart> parts, List<UUID> dropped, List<MapCamp> camps, List<MapWall> walls) {
        public MapChange {
            parts = List.copyOf(parts);
            dropped = List.copyOf(dropped);
            camps = List.copyOf(camps);
            walls = List.copyOf(walls);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(reset);
            writeList(buf, parts, (b, row) -> row.encode(b));
            writeList(buf, dropped, FriendlyByteBuf::writeUUID);
            writeList(buf, camps, (b, row) -> row.encode(b));
            writeList(buf, walls, (b, row) -> row.encode(b));
        }

        public static MapChange decode(FriendlyByteBuf buf) {
            return new MapChange(buf.readBoolean(), readList(buf, 1_024, MapPart::decode), readList(buf, 4_096, FriendlyByteBuf::readUUID),
                    readList(buf, 1_024, MapCamp::decode), readList(buf, 1_024, MapWall::decode));
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
    public record MapData(List<MapCity> cities, List<MapRoad> roads, List<MapCamp> camps, List<MapWall> walls) {
        public static final MapData EMPTY = new MapData(List.of(), List.of(), List.of(), List.of());

        public MapData {
            cities = List.copyOf(cities);
            roads = List.copyOf(roads);
            camps = List.copyOf(camps);
            walls = List.copyOf(walls);
        }

        public MapData(List<MapCity> cities, List<MapRoad> roads, List<MapCamp> camps) {
            this(cities, roads, camps, List.of());
        }

        public void encode(FriendlyByteBuf buf) {
            writeList(buf, cities, (b, row) -> row.encode(b));
            writeList(buf, roads, (b, row) -> row.encode(b));
            writeList(buf, camps, (b, row) -> row.encode(b));
            writeList(buf, walls, (b, row) -> row.encode(b));
        }

        public static MapData decode(FriendlyByteBuf buf) {
            return new MapData(readList(buf, 4_096, MapCity::decode), readList(buf, 4_096, MapRoad::decode), readList(buf, 1_024, MapCamp::decode),
                    readList(buf, 1_024, MapWall::decode));
        }
    }
}
