package io.github.sekerator.kingdomworld.common.network.data;

import io.github.sekerator.kingdomworld.core.diplomacy.Bond;
import io.github.sekerator.kingdomworld.core.diplomacy.Proposal;
import io.github.sekerator.kingdomworld.core.diplomacy.War;
import io.github.sekerator.kingdomworld.core.diplomacy.WarGoal;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What the client sees of diplomacy: the "Diplomacy" tab of the throne screen and the marks of relations, wars and shields
 * on the atlas. Names of kingdoms travel as components, so that each player reads the names of kingdoms of the world in
 * their language.
 */
public final class DiplomacyViews {
    public static final int MAX_ROWS = 256;
    public static final int MAX_CHRONICLE = 64;
    public static final int MAX_WORD = 32;

    private DiplomacyViews() {
    }

    private static <T> List<T> readList(FriendlyByteBuf buf, java.util.function.Function<FriendlyByteBuf, T> reader) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ROWS) {
            throw new DecoderException("Too many rows of diplomacy: " + size);
        }
        List<T> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(reader.apply(buf));
        }
        return list;
    }

    private static <T> void writeList(FriendlyByteBuf buf, List<T> list, java.util.function.BiConsumer<T, FriendlyByteBuf> writer) {
        int size = Math.min(list.size(), MAX_ROWS);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            writer.accept(list.get(i), buf);
        }
    }

    /**
     * Another kingdom as the viewer's kingdom stands with it.
     *
     * @param relation   how the viewer's kingdom stands with it, from -100 to 100
     * @param reason     the reason that weighs most, empty for none
     * @param vassalOf   whether the viewer's kingdom is its vassal
     * @param lordOf     whether it is the vassal of the viewer's kingdom
     * @param minutes    minutes a pact or a truce has left, -1 for none
     * @param playable   whether it can defend itself now (a war may be declared on it)
     * @param shielded   whether its players are all away and its land is shielded
     */
    public record KingdomRow(UUID id, Component name, int color, boolean npc, Bond bond, int relation, String reason, boolean vassalOf,
                             boolean lordOf, long minutes, boolean playable, boolean shielded) {
        public KingdomRow {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(bond, "bond");
            reason = reason == null ? "" : reason;
            color &= Kingdom.MAX_COLOR;
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeComponent(name);
            buf.writeInt(color);
            buf.writeBoolean(npc);
            buf.writeEnum(bond);
            buf.writeVarInt(relation);
            buf.writeUtf(reason, MAX_WORD);
            buf.writeBoolean(vassalOf);
            buf.writeBoolean(lordOf);
            buf.writeVarLong(minutes + 1);
            buf.writeBoolean(playable);
            buf.writeBoolean(shielded);
        }

        static KingdomRow decode(FriendlyByteBuf buf) {
            return new KingdomRow(buf.readUUID(), buf.readComponent(), buf.readInt(), buf.readBoolean(), KingdomView.readEnum(buf, Bond.class),
                    buf.readVarInt(), buf.readUtf(MAX_WORD), buf.readBoolean(), buf.readBoolean(), buf.readVarLong() - 1, buf.readBoolean(),
                    buf.readBoolean());
        }
    }

    /**
     * An offer that waits for an answer.
     *
     * @param incoming whether it was made to the viewer's kingdom
     * @param text     what is offered, in full
     */
    public record OfferRow(long id, boolean incoming, UUID other, Component otherName, Proposal.Kind kind, Component text, long minutes) {
        public OfferRow {
            Objects.requireNonNull(other, "other");
            Objects.requireNonNull(otherName, "otherName");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(text, "text");
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeBoolean(incoming);
            buf.writeUUID(other);
            buf.writeComponent(otherName);
            buf.writeEnum(kind);
            buf.writeComponent(text);
            buf.writeVarLong(Math.max(0, minutes));
        }

        static OfferRow decode(FriendlyByteBuf buf) {
            return new OfferRow(buf.readVarLong(), buf.readBoolean(), buf.readUUID(), buf.readComponent(), KingdomView.readEnum(buf, Proposal.Kind.class),
                    buf.readComponent(), buf.readVarLong());
        }
    }

    /**
     * A call of an ally to a war.
     */
    public record CallRow(long war, Component leader, Component enemy) {
        public CallRow {
            Objects.requireNonNull(leader, "leader");
            Objects.requireNonNull(enemy, "enemy");
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(war);
            buf.writeComponent(leader);
            buf.writeComponent(enemy);
        }

        static CallRow decode(FriendlyByteBuf buf) {
            return new CallRow(buf.readVarLong(), buf.readComponent(), buf.readComponent());
        }
    }

    /**
     * A war of the viewer's kingdom, from its side.
     *
     * @param leads      whether the viewer's kingdom leads its side and may make peace
     * @param attacker   whether its side attacked
     * @param minutes    minutes until the start while preparing, else minutes until the war ends by time
     * @param allies     the allies of both sides, in words
     * @param demand     the terms its side may ask now
     * @param concede    the terms the enemy may ask now
     * @param demandOk   whether the demand is more than a white peace
     */
    public record WarRow(long id, Component enemy, boolean leads, boolean attacker, WarGoal goal, War.Phase phase, long minutes, int ourScore,
                         int theirScore, int ourExhaustion, int theirExhaustion, boolean enemyShielded, boolean usShielded, Component allies,
                         Component demand, Component concede, boolean demandOk) {
        public WarRow {
            Objects.requireNonNull(enemy, "enemy");
            Objects.requireNonNull(goal, "goal");
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(allies, "allies");
            Objects.requireNonNull(demand, "demand");
            Objects.requireNonNull(concede, "concede");
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeComponent(enemy);
            buf.writeBoolean(leads);
            buf.writeBoolean(attacker);
            buf.writeEnum(goal);
            buf.writeEnum(phase);
            buf.writeVarLong(Math.max(0, minutes));
            buf.writeVarInt(ourScore);
            buf.writeVarInt(theirScore);
            buf.writeVarInt(ourExhaustion);
            buf.writeVarInt(theirExhaustion);
            buf.writeBoolean(enemyShielded);
            buf.writeBoolean(usShielded);
            buf.writeComponent(allies);
            buf.writeComponent(demand);
            buf.writeComponent(concede);
            buf.writeBoolean(demandOk);
        }

        static WarRow decode(FriendlyByteBuf buf) {
            return new WarRow(buf.readVarLong(), buf.readComponent(), buf.readBoolean(), buf.readBoolean(), KingdomView.readEnum(buf, WarGoal.class),
                    KingdomView.readEnum(buf, War.Phase.class), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readComponent(), buf.readComponent(), buf.readComponent(),
                    buf.readBoolean());
        }
    }

    /**
     * The "Diplomacy" tab.
     *
     * @param canManage   whether the viewer may make and answer offers and break treaties
     * @param canDeclare  whether the viewer may declare war
     * @param shielded    whether the viewer's kingdom is shielded now
     * @param chronicle   the latest entries of the chronicle of wars and treaties of the kingdom
     */
    public record TabView(UUID kingdomId, boolean canManage, boolean canDeclare, boolean shielded, List<KingdomRow> kingdoms,
                          List<OfferRow> offers, List<CallRow> calls, List<WarRow> wars, List<Component> chronicle) {
        public TabView {
            Objects.requireNonNull(kingdomId, "kingdomId");
            kingdoms = List.copyOf(kingdoms);
            offers = List.copyOf(offers);
            calls = List.copyOf(calls);
            wars = List.copyOf(wars);
            chronicle = List.copyOf(chronicle.size() > MAX_CHRONICLE ? chronicle.subList(0, MAX_CHRONICLE) : chronicle);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeBoolean(canManage);
            buf.writeBoolean(canDeclare);
            buf.writeBoolean(shielded);
            writeList(buf, kingdoms, KingdomRow::encode);
            writeList(buf, offers, OfferRow::encode);
            writeList(buf, calls, CallRow::encode);
            writeList(buf, wars, WarRow::encode);
            writeList(buf, chronicle, (line, out) -> out.writeComponent(line));
        }

        public static TabView decode(FriendlyByteBuf buf) {
            return new TabView(buf.readUUID(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), readList(buf, KingdomRow::decode),
                    readList(buf, OfferRow::decode), readList(buf, CallRow::decode), readList(buf, WarRow::decode),
                    readList(buf, FriendlyByteBuf::readComponent));
        }
    }

    /**
     * A kingdom on the atlas as the viewer's kingdom stands with it.
     */
    public record MapMark(UUID id, Component name, String dimension, int x, int z, Bond bond, int relation, boolean shielded) {
        public MapMark {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(bond, "bond");
        }

        public static MapMark of(UUID id, Component name, Capital capital, Bond bond, int relation, boolean shielded) {
            return new MapMark(id, name, capital.dimension(), capital.x(), capital.z(), bond, relation, shielded);
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeComponent(name);
            buf.writeUtf(dimension, 256);
            buf.writeVarInt(x);
            buf.writeVarInt(z);
            buf.writeEnum(bond);
            buf.writeVarInt(relation);
            buf.writeBoolean(shielded);
        }

        static MapMark decode(FriendlyByteBuf buf) {
            return new MapMark(buf.readUUID(), buf.readComponent(), buf.readUtf(256), buf.readVarInt(), buf.readVarInt(),
                    KingdomView.readEnum(buf, Bond.class), buf.readVarInt(), buf.readBoolean());
        }
    }

    /**
     * The marks of the atlas for one player; empty for a player without a kingdom.
     */
    public record MapView(List<MapMark> marks) {
        public static final MapView EMPTY = new MapView(List.of());

        public MapView {
            marks = List.copyOf(marks);
        }

        public void encode(FriendlyByteBuf buf) {
            writeList(buf, marks, MapMark::encode);
        }

        public static MapView decode(FriendlyByteBuf buf) {
            return new MapView(readList(buf, MapMark::decode));
        }
    }
}
