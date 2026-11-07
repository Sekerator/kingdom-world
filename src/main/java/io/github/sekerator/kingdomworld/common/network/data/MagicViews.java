package io.github.sekerator.kingdomworld.common.network.data;

import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.github.sekerator.kingdomworld.core.magic.EnergyCategory;
import io.github.sekerator.kingdomworld.core.magic.KingdomSpell;
import io.github.sekerator.kingdomworld.core.magic.MagicDevice;
import io.github.sekerator.kingdomworld.core.magic.PlayerSpell;
import io.github.sekerator.kingdomworld.core.magic.Research;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * What the screens of magic show: the "Magic" tab of the throne screen, the screen of a magic focus and the screen of a
 * gate. Built by the server for one viewer; the client only draws them.
 */
public final class MagicViews {
    /** Rows one list of a view carries at most. */
    public static final int MAX_ROWS = 64;

    private MagicViews() {
    }

    private static <T> List<T> readList(FriendlyByteBuf buf, Function<FriendlyByteBuf, T> reader) {
        int size = Math.min(MAX_ROWS, buf.readVarInt());
        List<T> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(reader.apply(buf));
        }
        return list;
    }

    private static <T> void writeList(FriendlyByteBuf buf, List<T> list, BiConsumer<T, FriendlyByteBuf> writer) {
        int size = Math.min(MAX_ROWS, list.size());
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            writer.accept(list.get(i), buf);
        }
    }

    // ---------------------------------------------------------------- the magic tab

    /**
     * Energy in and out of one category over a period.
     */
    public record JournalRow(EnergyCategory category, long income, long expense) {
        void encode(FriendlyByteBuf buf) {
            buf.writeEnum(category);
            buf.writeVarLong(income);
            buf.writeVarLong(expense);
        }

        static JournalRow decode(FriendlyByteBuf buf) {
            return new JournalRow(KingdomView.readEnum(buf, EnergyCategory.class), buf.readVarLong(), buf.readVarLong());
        }
    }

    /**
     * A mage of the kingdom.
     *
     * @param speed how fast the mage works, in percent of the usual speed
     */
    public record MageRow(UUID id, Component name, int experience, int speed, WorkStatus status) {
        public MageRow {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(status, "status");
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeComponent(name);
            buf.writeVarInt(experience);
            buf.writeVarInt(speed);
            buf.writeEnum(status);
        }

        static MageRow decode(FriendlyByteBuf buf) {
            return new MageRow(buf.readUUID(), buf.readComponent(), buf.readVarInt(), buf.readVarInt(), KingdomView.readEnum(buf, WorkStatus.class));
        }
    }

    /**
     * A research of the tower and where it stands.
     *
     * @param percent how far the current research is
     */
    public record ResearchRow(Research research, ResearchState state, long cost, int minutes, int percent) {
        void encode(FriendlyByteBuf buf) {
            buf.writeEnum(research);
            buf.writeEnum(state);
            buf.writeVarLong(cost);
            buf.writeVarInt(minutes);
            buf.writeVarInt(percent);
        }

        static ResearchRow decode(FriendlyByteBuf buf) {
            return new ResearchRow(KingdomView.readEnum(buf, Research.class), KingdomView.readEnum(buf, ResearchState.class), buf.readVarLong(),
                    buf.readVarInt(), buf.readVarInt());
        }
    }

    public enum ResearchState {
        DONE,
        CURRENT,
        AVAILABLE,
        LOCKED
    }

    /**
     * A spell of the kingdom.
     *
     * @param units what a spell paid by the day is paid for now: chunks under the ward, posts fertility reaches
     */
    public record SpellRow(KingdomSpell spell, boolean researched, boolean on, long price, long units) {
        void encode(FriendlyByteBuf buf) {
            buf.writeEnum(spell);
            buf.writeBoolean(researched);
            buf.writeBoolean(on);
            buf.writeVarLong(price);
            buf.writeVarLong(units);
        }

        static SpellRow decode(FriendlyByteBuf buf) {
            return new SpellRow(KingdomView.readEnum(buf, KingdomSpell.class), buf.readBoolean(), buf.readBoolean(), buf.readVarLong(),
                    buf.readVarLong());
        }
    }

    /**
     * A gate or a generator of the kingdom.
     */
    public record DeviceRow(long id, MagicDevice.Kind kind, String dimension, BlockPos pos) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeEnum(kind);
            buf.writeUtf(dimension, 256);
            buf.writeBlockPos(pos);
        }

        static DeviceRow decode(FriendlyByteBuf buf) {
            return new DeviceRow(buf.readVarLong(), KingdomView.readEnum(buf, MagicDevice.Kind.class), buf.readUtf(256), buf.readBlockPos());
        }
    }

    /**
     * The "Magic" tab of a kingdom.
     *
     * @param crystals    crystals in the storehouses
     * @param crystalEnergy the energy they hold
     * @param journal     the journal for today, yesterday and the week, in this order
     * @param hereWarded  whether the chunk the viewer stands in is under the ward
     * @param hereOwned   whether the chunk the viewer stands in is land of the kingdom
     */
    public record TabView(UUID kingdomId, boolean canManage, long energy, long capacity, int accumulators, int foci, long crystals,
                          long crystalEnergy, List<List<JournalRow>> journal, List<MageRow> mages, List<ResearchRow> research, int towerLevel,
                          List<SpellRow> spells, int wardChunks, int wardMax, boolean hereWarded, boolean hereOwned, List<DeviceRow> gates,
                          List<DeviceRow> generators, int feRate, int feMaxPerTick) {
        public TabView {
            journal = List.copyOf(journal);
            mages = List.copyOf(mages);
            research = List.copyOf(research);
            spells = List.copyOf(spells);
            gates = List.copyOf(gates);
            generators = List.copyOf(generators);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeBoolean(canManage);
            buf.writeVarLong(energy);
            buf.writeVarLong(capacity);
            buf.writeVarInt(accumulators);
            buf.writeVarInt(foci);
            buf.writeVarLong(crystals);
            buf.writeVarLong(crystalEnergy);
            buf.writeVarInt(journal.size());
            for (List<JournalRow> period : journal) {
                writeList(buf, period, JournalRow::encode);
            }
            writeList(buf, mages, MageRow::encode);
            writeList(buf, research, ResearchRow::encode);
            buf.writeVarInt(towerLevel);
            writeList(buf, spells, SpellRow::encode);
            buf.writeVarInt(wardChunks);
            buf.writeVarInt(wardMax);
            buf.writeBoolean(hereWarded);
            buf.writeBoolean(hereOwned);
            writeList(buf, gates, DeviceRow::encode);
            writeList(buf, generators, DeviceRow::encode);
            buf.writeVarInt(feRate);
            buf.writeVarInt(feMaxPerTick);
        }

        public static TabView decode(FriendlyByteBuf buf) {
            UUID kingdom = buf.readUUID();
            boolean canManage = buf.readBoolean();
            long energy = buf.readVarLong();
            long capacity = buf.readVarLong();
            int accumulators = buf.readVarInt();
            int foci = buf.readVarInt();
            long crystals = buf.readVarLong();
            long crystalEnergy = buf.readVarLong();
            int periods = Math.min(3, buf.readVarInt());
            List<List<JournalRow>> journal = new ArrayList<>();
            for (int i = 0; i < periods; i++) {
                journal.add(readList(buf, JournalRow::decode));
            }
            List<MageRow> mages = readList(buf, MageRow::decode);
            List<ResearchRow> research = readList(buf, ResearchRow::decode);
            int level = buf.readVarInt();
            List<SpellRow> spells = readList(buf, SpellRow::decode);
            return new TabView(kingdom, canManage, energy, capacity, accumulators, foci, crystals, crystalEnergy, journal, mages, research, level,
                    spells, buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), readList(buf, DeviceRow::decode),
                    readList(buf, DeviceRow::decode), buf.readVarInt(), buf.readVarInt());
        }
    }

    // ---------------------------------------------------------------- the focus

    /**
     * A spell a player can learn at a focus.
     *
     * @param refusal why the player cannot learn it now, if not
     */
    public record Lesson(PlayerSpell spell, boolean learned, long price, long cost, Rank minRank, int towerLevel, Optional<KingdomError> refusal) {
        void encode(FriendlyByteBuf buf) {
            buf.writeEnum(spell);
            buf.writeBoolean(learned);
            buf.writeVarLong(price);
            buf.writeVarLong(cost);
            buf.writeVarInt(minRank.ordinal());
            buf.writeVarInt(towerLevel);
            buf.writeVarInt(refusal.map(error -> error.ordinal() + 1).orElse(0));
        }

        static Lesson decode(FriendlyByteBuf buf) {
            PlayerSpell spell = KingdomView.readEnum(buf, PlayerSpell.class);
            boolean learned = buf.readBoolean();
            long price = buf.readVarLong();
            long cost = buf.readVarLong();
            Rank rank = Rank.ofIndex(buf.readVarInt());
            int level = buf.readVarInt();
            int refusal = buf.readVarInt();
            KingdomError[] errors = KingdomError.values();
            return new Lesson(spell, learned, price, cost, rank, level,
                    refusal > 0 && refusal <= errors.length ? Optional.of(errors[refusal - 1]) : Optional.empty());
        }
    }

    /**
     * The screen of a magic focus for one player.
     *
     * @param staff the slots of the staff in the player's main hand; empty when the hand holds no staff
     */
    public record FocusView(BlockPos pos, UUID kingdomId, String kingdomName, int kingdomColor, TradeViews.Currency currency, int towerLevel,
                            Rank rank, long carried, long balance, List<Lesson> lessons, List<Optional<PlayerSpell>> staff,
                            Optional<Research> current, int percent) {
        public FocusView {
            lessons = List.copyOf(lessons);
            staff = List.copyOf(staff);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeUUID(kingdomId);
            buf.writeUtf(kingdomName, KingdomNames.MAX_LENGTH);
            buf.writeInt(kingdomColor);
            currency.encode(buf);
            buf.writeVarInt(towerLevel);
            buf.writeVarInt(rank.ordinal());
            buf.writeVarLong(carried);
            buf.writeVarLong(balance);
            writeList(buf, lessons, Lesson::encode);
            writeList(buf, staff, (slot, out) -> out.writeVarInt(slot.map(spell -> spell.ordinal() + 1).orElse(0)));
            buf.writeVarInt(current.map(research -> research.ordinal() + 1).orElse(0));
            buf.writeVarInt(percent);
        }

        public static FocusView decode(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            UUID kingdom = buf.readUUID();
            String name = buf.readUtf(KingdomNames.MAX_LENGTH);
            int color = buf.readInt();
            TradeViews.Currency currency = TradeViews.Currency.decode(buf);
            int level = buf.readVarInt();
            Rank rank = Rank.ofIndex(buf.readVarInt());
            long carried = buf.readVarLong();
            long balance = buf.readVarLong();
            List<Lesson> lessons = readList(buf, Lesson::decode);
            List<Optional<PlayerSpell>> staff = readList(buf, in -> {
                int value = in.readVarInt();
                return value > 0 && value <= PlayerSpell.COUNT ? Optional.of(PlayerSpell.values()[value - 1]) : Optional.<PlayerSpell>empty();
            });
            int current = buf.readVarInt();
            return new FocusView(pos, kingdom, name, color, currency, level, rank, carried, balance, lessons, staff,
                    current > 0 && current <= Research.COUNT ? Optional.of(Research.values()[current - 1]) : Optional.empty(), buf.readVarInt());
        }
    }

    // ---------------------------------------------------------------- gates

    /**
     * Another gate of the kingdom.
     *
     * @param distance in blocks, -1 in another dimension
     */
    public record GateRow(long id, String dimension, BlockPos pos, int distance) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeUtf(dimension, 256);
            buf.writeBlockPos(pos);
            buf.writeVarInt(distance + 1);
        }

        static GateRow decode(FriendlyByteBuf buf) {
            return new GateRow(buf.readVarLong(), buf.readUtf(256), buf.readBlockPos(), buf.readVarInt() - 1);
        }
    }

    /**
     * The screen of a gate: where it leads and what a trip costs.
     */
    public record GateView(BlockPos pos, UUID kingdomId, String kingdomName, int kingdomColor, long gateId, boolean on, long price, long energy,
                           List<GateRow> gates) {
        public GateView {
            gates = List.copyOf(gates);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeUUID(kingdomId);
            buf.writeUtf(kingdomName, KingdomNames.MAX_LENGTH);
            buf.writeInt(kingdomColor);
            buf.writeVarLong(gateId);
            buf.writeBoolean(on);
            buf.writeVarLong(price);
            buf.writeVarLong(energy);
            writeList(buf, gates, GateRow::encode);
        }

        public static GateView decode(FriendlyByteBuf buf) {
            return new GateView(buf.readBlockPos(), buf.readUUID(), buf.readUtf(KingdomNames.MAX_LENGTH), buf.readInt(), buf.readVarLong(),
                    buf.readBoolean(), buf.readVarLong(), buf.readVarLong(), readList(buf, GateRow::decode));
        }
    }
}
