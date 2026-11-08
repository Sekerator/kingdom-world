package io.github.sekerator.kingdomworld.common.network.data;

import io.netty.handler.codec.DecoderException;
import io.github.sekerator.kingdomworld.core.nobility.Demand;
import io.github.sekerator.kingdomworld.core.nobility.Faction;
import io.github.sekerator.kingdomworld.core.nobility.Spy;
import io.github.sekerator.kingdomworld.core.nobility.Title;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What the client sees of the nobility: the "Nobility" tab of the throne screen with the houses and titles, the stability and
 * its causes, the parties and their demand, plots found out, the chronicle and, for those who run the spies, the secret
 * service. Names travel as components in both alphabets, so that each player reads them in their language.
 */
public final class NobilityViews {
    public static final int MAX_ROWS = 128;
    public static final int MAX_WORD = 32;

    private NobilityViews() {
    }

    private static <T> List<T> readList(FriendlyByteBuf buf, java.util.function.Function<FriendlyByteBuf, T> reader) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ROWS) {
            throw new DecoderException("Too many rows of the nobility: " + size);
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
     * A house of the kingdom; {@code titles} lists its titles and fiefs.
     */
    public record HouseRow(long id, Component name, Component head, Faction faction, int loyalty, long wealth, Component titles) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeComponent(name);
            buf.writeComponent(head);
            buf.writeEnum(faction);
            buf.writeVarInt(loyalty);
            buf.writeVarLong(wealth);
            buf.writeComponent(titles);
        }

        static HouseRow decode(FriendlyByteBuf buf) {
            return new HouseRow(buf.readVarLong(), buf.readComponent(), buf.readComponent(), KingdomView.readEnum(buf, Faction.class), buf.readVarInt(),
                    buf.readVarLong(), buf.readComponent());
        }
    }

    /**
     * A title with its fief and holder.
     */
    public record GrantRow(long id, Title title, Component fief, Component holder, boolean player) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeEnum(title);
            buf.writeComponent(fief);
            buf.writeComponent(holder);
            buf.writeBoolean(player);
        }

        static GrantRow decode(FriendlyByteBuf buf) {
            return new GrantRow(buf.readVarLong(), KingdomView.readEnum(buf, Title.class), buf.readComponent(), buf.readComponent(), buf.readBoolean());
        }
    }

    /**
     * A free fief, a subject who could be made noble, or another kingdom for a spy: an id (a code, a player, a kingdom) and a
     * name.
     */
    public record Choice(String id, Component name) {
        public Choice {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(id, 64);
            buf.writeComponent(name);
        }

        static Choice decode(FriendlyByteBuf buf) {
            return new Choice(buf.readUtf(64), buf.readComponent());
        }
    }

    public record CauseRow(String id, int amount) {
        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(id, MAX_WORD);
            buf.writeVarInt(amount);
        }

        static CauseRow decode(FriendlyByteBuf buf) {
            return new CauseRow(buf.readUtf(MAX_WORD), buf.readVarInt());
        }
    }

    public record FactionRow(Faction faction, int influence, int content) {
        void encode(FriendlyByteBuf buf) {
            buf.writeEnum(faction);
            buf.writeVarInt(influence);
            buf.writeVarInt(content);
        }

        static FactionRow decode(FriendlyByteBuf buf) {
            return new FactionRow(KingdomView.readEnum(buf, Faction.class), buf.readVarInt(), buf.readVarInt());
        }
    }

    /**
     * The open demand of a party; {@code id} 0 for none.
     *
     * @param text what is asked, with its cost
     * @param days played days left to answer
     */
    public record DemandRow(long id, Component house, Faction faction, Demand.Kind kind, Component text, long days) {
        public static final DemandRow NONE = new DemandRow(0, Component.empty(), Faction.CROWN, Demand.Kind.BUILD, Component.empty(), 0);

        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeComponent(house);
            buf.writeEnum(faction);
            buf.writeEnum(kind);
            buf.writeComponent(text);
            buf.writeVarLong(days);
        }

        static DemandRow decode(FriendlyByteBuf buf) {
            return new DemandRow(buf.readVarLong(), buf.readComponent(), KingdomView.readEnum(buf, Faction.class), KingdomView.readEnum(buf, Demand.Kind.class),
                    buf.readComponent(), buf.readVarLong());
        }
    }

    /**
     * A plot counter-intelligence found out, waiting for the verdict of the crown.
     */
    public record PlotRow(long id, Component house, Component backer) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeComponent(house);
            buf.writeComponent(backer);
        }

        static PlotRow decode(FriendlyByteBuf buf) {
            return new PlotRow(buf.readVarLong(), buf.readComponent(), buf.readComponent());
        }
    }

    /**
     * A spy of the kingdom; {@code doing} says where it is and what it does.
     */
    public record SpyRow(long id, Component name, int skill, Spy.Status status, Component doing) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeComponent(name);
            buf.writeVarInt(skill);
            buf.writeEnum(status);
            buf.writeComponent(doing);
        }

        static SpyRow decode(FriendlyByteBuf buf) {
            return new SpyRow(buf.readVarLong(), buf.readComponent(), buf.readVarInt(), KingdomView.readEnum(buf, Spy.Status.class), buf.readComponent());
        }
    }

    /**
     * The tab.
     *
     * @param canManage whether the viewer runs the court (titles, demands, verdicts)
     * @param canSpies  whether the viewer runs the spies; the secret service is empty otherwise
     * @param crown     whether the viewer is the king: only the king raises new houses and makes players noble
     * @param npc       whether a ruler of the world rules the kingdom (the viewer only looks)
     * @param warning   a warning of the crown that a city may leave, empty for none
     * @param counter   the counter-intelligence of the kingdom in percent
     * @param hire      what a spy costs to hire and each day
     */
    public record TabView(UUID kingdomId, boolean canManage, boolean canSpies, boolean crown, boolean npc, int stability, List<CauseRow> causes, Component warning,
                          List<HouseRow> houses, List<GrantRow> grants, List<Choice> fiefs, List<Choice> subjects, List<FactionRow> factions,
                          DemandRow demand, List<PlotRow> plots, List<Component> chronicle, List<SpyRow> spies, List<Component> reports,
                          List<Choice> targets, int counter, Component hire, int maxSpies) {
        public TabView {
            Objects.requireNonNull(kingdomId, "kingdomId");
            causes = List.copyOf(causes);
            houses = List.copyOf(houses);
            grants = List.copyOf(grants);
            fiefs = List.copyOf(fiefs);
            subjects = List.copyOf(subjects);
            factions = List.copyOf(factions);
            plots = List.copyOf(plots);
            chronicle = List.copyOf(chronicle);
            spies = List.copyOf(spies);
            reports = List.copyOf(reports);
            targets = List.copyOf(targets);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeBoolean(canManage);
            buf.writeBoolean(canSpies);
            buf.writeBoolean(crown);
            buf.writeBoolean(npc);
            buf.writeVarInt(stability);
            writeList(buf, causes, CauseRow::encode);
            buf.writeComponent(warning);
            writeList(buf, houses, HouseRow::encode);
            writeList(buf, grants, GrantRow::encode);
            writeList(buf, fiefs, Choice::encode);
            writeList(buf, subjects, Choice::encode);
            writeList(buf, factions, FactionRow::encode);
            demand.encode(buf);
            writeList(buf, plots, PlotRow::encode);
            writeList(buf, chronicle, (line, b) -> b.writeComponent(line));
            writeList(buf, spies, SpyRow::encode);
            writeList(buf, reports, (line, b) -> b.writeComponent(line));
            writeList(buf, targets, Choice::encode);
            buf.writeVarInt(counter);
            buf.writeComponent(hire);
            buf.writeVarInt(maxSpies);
        }

        public static TabView decode(FriendlyByteBuf buf) {
            return new TabView(buf.readUUID(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), readList(buf, CauseRow::decode),
                    buf.readComponent(), readList(buf, HouseRow::decode), readList(buf, GrantRow::decode), readList(buf, Choice::decode),
                    readList(buf, Choice::decode), readList(buf, FactionRow::decode), DemandRow.decode(buf), readList(buf, PlotRow::decode),
                    readList(buf, FriendlyByteBuf::readComponent), readList(buf, SpyRow::decode), readList(buf, FriendlyByteBuf::readComponent),
                    readList(buf, Choice::decode), buf.readVarInt(), buf.readComponent(), buf.readVarInt());
        }
    }
}
