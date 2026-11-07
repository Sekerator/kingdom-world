package io.github.sekerator.kingdomworld.common.network.data;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * What the screens of the life of cities show: the window of a tavern and the life of a kingdom in the citizens tab.
 */
public final class LifeViews {
    public static final int MAX_ROWS = 64;
    public static final int MAX_RUMORS = 8;
    public static final int MAX_EVENTS = 50;
    public static final int MAX_NAME = 64;

    private LifeViews() {
    }

    private static <T> List<T> readList(FriendlyByteBuf buf, int max, Function<FriendlyByteBuf, T> reader) {
        int size = buf.readVarInt();
        if (size < 0 || size > max) {
            throw new IllegalArgumentException("List of " + size + " entries, at most " + max);
        }
        List<T> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(reader.apply(buf));
        }
        return list;
    }

    private static <T> void writeList(FriendlyByteBuf buf, List<T> list, int max, BiConsumer<FriendlyByteBuf, T> writer) {
        int size = Math.min(max, list.size());
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            writer.accept(buf, list.get(i));
        }
    }

    private static String cut(String text) {
        return text.length() > MAX_NAME ? text.substring(0, MAX_NAME) : text;
    }

    // ---------------------------------------------------------------- the tavern

    /**
     * Something the tavern serves: what the storehouses hold of it and its price for the player.
     */
    public record Good(ItemStack stack, long price, long stock) {
        void encode(FriendlyByteBuf buf) {
            buf.writeItem(stack);
            buf.writeVarLong(price);
            buf.writeVarLong(stock);
        }

        static Good decode(FriendlyByteBuf buf) {
            return new Good(buf.readItem(), buf.readVarLong(), buf.readVarLong());
        }
    }

    /**
     * A rumor; one about a camp may be bought marked on the atlas.
     */
    public record Told(Component text, long campId, boolean marked) {
        void encode(FriendlyByteBuf buf) {
            buf.writeComponent(text);
            buf.writeVarLong(campId);
            buf.writeBoolean(marked);
        }

        static Told decode(FriendlyByteBuf buf) {
            return new Told(buf.readComponent(), buf.readVarLong(), buf.readBoolean());
        }
    }

    /**
     * The window of a tavern.
     *
     * @param innkeeper the seed of the innkeeper's name
     * @param open      whether an innkeeper keeps it open
     * @param rooms     free rooms (beds) now
     * @param roomLeft  ticks the player's own room here is still theirs, 0 for none
     */
    public record TavernView(UUID kingdomId, TradeViews.Currency currency, int innkeeper, boolean female, boolean open, List<Good> goods, List<Told> rumors,
                             long rumorPrice, int rooms, long roomPrice, long roomLeft, long coins, long account) {
        public TavernView {
            Objects.requireNonNull(kingdomId, "kingdomId");
            Objects.requireNonNull(currency, "currency");
            goods = List.copyOf(goods);
            rumors = List.copyOf(rumors);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            currency.encode(buf);
            buf.writeInt(innkeeper);
            buf.writeBoolean(female);
            buf.writeBoolean(open);
            writeList(buf, goods, MAX_ROWS, (b, good) -> good.encode(b));
            writeList(buf, rumors, MAX_RUMORS, (b, told) -> told.encode(b));
            buf.writeVarLong(rumorPrice);
            buf.writeVarInt(rooms);
            buf.writeVarLong(roomPrice);
            buf.writeVarLong(roomLeft);
            buf.writeVarLong(coins);
            buf.writeVarLong(account);
        }

        public static TavernView decode(FriendlyByteBuf buf) {
            return new TavernView(buf.readUUID(), TradeViews.Currency.decode(buf), buf.readInt(), buf.readBoolean(), buf.readBoolean(),
                    readList(buf, MAX_ROWS, Good::decode), readList(buf, MAX_RUMORS, Told::decode), buf.readVarLong(), buf.readVarInt(),
                    buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong());
        }
    }

    // ---------------------------------------------------------------- the life of a kingdom

    /**
     * An ill citizen.
     *
     * @param where the infirmary it is treated in, or {@code null} at home
     */
    public record Sick(int seed, boolean female, String profession, BlockPos where, int percent) {
        void encode(FriendlyByteBuf buf) {
            buf.writeInt(seed);
            buf.writeBoolean(female);
            buf.writeUtf(profession, 32);
            buf.writeBoolean(where != null);
            if (where != null) {
                buf.writeBlockPos(where);
            }
            buf.writeVarInt(percent);
        }

        static Sick decode(FriendlyByteBuf buf) {
            return new Sick(buf.readInt(), buf.readBoolean(), buf.readUtf(32), buf.readBoolean() ? buf.readBlockPos() : null, buf.readVarInt());
        }
    }

    /**
     * A festival, the day of the founding or a fair, for the chronicle.
     *
     * @param kind {@code festival}, {@code founding} or {@code fair}
     */
    public record Event(String kind, long day, String place) {
        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(kind, 16);
            buf.writeVarLong(day);
            buf.writeUtf(cut(place), MAX_NAME);
        }

        static Event decode(FriendlyByteBuf buf) {
            return new Event(buf.readUtf(16), buf.readVarLong(), buf.readUtf(MAX_NAME));
        }
    }

    /**
     * A city that holds fairs: in how many days the next one is, 0 for today.
     */
    public record Fair(String city, long days) {
        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(cut(city), MAX_NAME);
            buf.writeVarLong(days);
        }

        static Fair decode(FriendlyByteBuf buf) {
            return new Fair(buf.readUtf(MAX_NAME), buf.readVarLong());
        }
    }

    /**
     * The life of a kingdom in the citizens tab.
     *
     * @param counts     citizens asleep, at work, in the tavern and otherwise, now
     * @param hours      hours of the morning, the day, the evening and the night of a citizen
     * @param festival   0 a festival today, a number the days since the last, -1 none yet
     * @param nextIn     days until the king may call a festival, 0 now
     * @param cost       what a festival costs the treasury now
     * @param treat      food points of treat it takes
     * @param mood       mood a festival gives
     * @param taverns    working taverns, and of them open ones
     */
    public record TabView(UUID kingdomId, boolean canManage, int[] counts, int[] hours, List<Sick> sick, long festival, long nextIn, long cost,
                          int treat, int mood, int[] taverns, int guests, List<Fair> fairs, List<Event> events, TradeViews.Currency currency) {
        public TabView {
            Objects.requireNonNull(kingdomId, "kingdomId");
            counts = counts.clone();
            hours = hours.clone();
            taverns = taverns.clone();
            sick = List.copyOf(sick);
            fairs = List.copyOf(fairs);
            events = List.copyOf(events);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeBoolean(canManage);
            buf.writeVarIntArray(counts);
            buf.writeVarIntArray(hours);
            writeList(buf, sick, MAX_ROWS, (b, row) -> row.encode(b));
            buf.writeLong(festival);
            buf.writeVarLong(nextIn);
            buf.writeVarLong(cost);
            buf.writeVarInt(treat);
            buf.writeVarInt(mood);
            buf.writeVarIntArray(taverns);
            buf.writeVarInt(guests);
            writeList(buf, fairs, MAX_ROWS, (b, row) -> row.encode(b));
            writeList(buf, events, MAX_EVENTS, (b, row) -> row.encode(b));
            currency.encode(buf);
        }

        public static TabView decode(FriendlyByteBuf buf) {
            return new TabView(buf.readUUID(), buf.readBoolean(), buf.readVarIntArray(4), buf.readVarIntArray(4), readList(buf, MAX_ROWS, Sick::decode),
                    buf.readLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarIntArray(2), buf.readVarInt(),
                    readList(buf, MAX_ROWS, Fair::decode), readList(buf, MAX_EVENTS, Event::decode), TradeViews.Currency.decode(buf));
        }
    }

    /**
     * Festivals and fairs on the atlas: where they are today.
     *
     * @param fair whether it is a fair (otherwise a festival)
     */
    public record MapEvent(String dimension, int x, int z, boolean fair, int color) {
        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(dimension, 128);
            buf.writeInt(x);
            buf.writeInt(z);
            buf.writeBoolean(fair);
            buf.writeInt(color);
        }

        static MapEvent decode(FriendlyByteBuf buf) {
            return new MapEvent(buf.readUtf(128), buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readInt());
        }

        public static void encodeAll(FriendlyByteBuf buf, List<MapEvent> events) {
            writeList(buf, events, MAX_ROWS, (b, event) -> event.encode(b));
        }

        public static List<MapEvent> decodeAll(FriendlyByteBuf buf) {
            return readList(buf, MAX_ROWS, MapEvent::decode);
        }
    }
}
