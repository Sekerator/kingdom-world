package io.github.sekerator.kingdomworld.common.network.data;

import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.github.sekerator.kingdomworld.core.law.Crime;
import io.github.sekerator.kingdomworld.core.law.CrimeKind;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What the screens of the law show: the "Law" tab of the throne screen, the player's window of the law, the window to give
 * up to a guard or to pay at a gate, the window to buy back confiscated goods, and the line above the hotbar. Built by the
 * server for one player; the client only shows them.
 */
public final class LawViews {
    public static final int MAX_ROWS = 256;
    public static final int MAX_CRIMES = 32;
    public static final int MAX_GOODS = 64;
    public static final int MAX_NAME = 64;

    private LawViews() {
    }

    private static <T> List<T> readList(FriendlyByteBuf buf, int max, java.util.function.Function<FriendlyByteBuf, T> reader) {
        int size = buf.readVarInt();
        if (size < 0 || size > max) {
            throw new IllegalArgumentException("List too long: " + size);
        }
        List<T> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(reader.apply(buf));
        }
        return list;
    }

    private static <T> void writeList(FriendlyByteBuf buf, List<T> list, int max, java.util.function.BiConsumer<FriendlyByteBuf, T> writer) {
        int size = Math.min(max, list.size());
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            writer.accept(buf, list.get(i));
        }
    }

    // ---------------------------------------------------------------- records of the wanted

    public record CrimeRow(CrimeKind kind, long worth, String victim, int count) {
        public CrimeRow {
            Objects.requireNonNull(kind, "kind");
            victim = victim == null ? "" : victim;
        }

        public static CrimeRow of(Crime crime) {
            return new CrimeRow(crime.kind(), crime.worth(), crime.victim(), crime.count());
        }

        public Crime crime() {
            return new Crime(kind, worth, 0, victim, Math.max(1, count));
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeEnum(kind);
            buf.writeVarLong(worth);
            buf.writeUtf(victim, MAX_NAME);
            buf.writeVarInt(count);
        }

        static CrimeRow decode(FriendlyByteBuf buf) {
            return new CrimeRow(KingdomView.readEnum(buf, CrimeKind.class), buf.readVarLong(), buf.readUtf(MAX_NAME), buf.readVarInt());
        }
    }

    /**
     * A wanted player.
     *
     * @param fine      what the player owes; 0 with {@code execution}
     * @param execution whether the guards kill this player
     * @param commuted  whether the court spared a murderer
     * @param set       whether the court set the fine
     * @param bounty    the reward for the head on the board of the guild, 0 for none
     */
    public record WantedRow(UUID player, String name, boolean online, List<CrimeRow> crimes, long fine, boolean execution, boolean commuted,
                            boolean set, long bounty, int reputation) {
        public WantedRow {
            Objects.requireNonNull(player, "player");
            name = name == null ? "" : name;
            crimes = List.copyOf(crimes);
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeUUID(player);
            buf.writeUtf(name, MAX_NAME);
            buf.writeBoolean(online);
            writeList(buf, crimes, MAX_CRIMES, (b, row) -> row.encode(b));
            buf.writeVarLong(fine);
            buf.writeBoolean(execution);
            buf.writeBoolean(commuted);
            buf.writeBoolean(set);
            buf.writeVarLong(bounty);
            buf.writeVarInt(reputation);
        }

        static WantedRow decode(FriendlyByteBuf buf) {
            return new WantedRow(buf.readUUID(), buf.readUtf(MAX_NAME), buf.readBoolean(), readList(buf, MAX_CRIMES, CrimeRow::decode),
                    buf.readVarLong(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readVarLong(), buf.readVarInt());
        }
    }

    // ---------------------------------------------------------------- the law tab

    /**
     * The "Law" tab of a kingdom.
     *
     * @param canManage whether the viewer holds the court
     * @param wanted    the wanted, for the court only
     * @param self      the viewer's own record here, if any
     * @param bounty    the reward for heads now; {@code bountyDefault} whether it is the config's
     * @param murderFine the fine that would replace the death of a murderer, with the multiplier of the kingdom
     */
    public record TabView(UUID kingdomId, TradeViews.Currency currency, boolean canManage, List<WantedRow> wanted, int multiplier,
                          long entryFee, long maxEntryFee, boolean slaveryBanned, long bounty, boolean bountyDefault, long maxBounty,
                          long murderFine, Optional<WantedRow> self, int reputation, long debt, int confiscations) {
        public TabView {
            Objects.requireNonNull(kingdomId, "kingdomId");
            Objects.requireNonNull(currency, "currency");
            wanted = List.copyOf(wanted);
            self = self == null ? Optional.empty() : self;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            currency.encode(buf);
            buf.writeBoolean(canManage);
            writeList(buf, wanted, MAX_ROWS, (b, row) -> row.encode(b));
            buf.writeVarInt(multiplier);
            buf.writeVarLong(entryFee);
            buf.writeVarLong(maxEntryFee);
            buf.writeBoolean(slaveryBanned);
            buf.writeVarLong(bounty);
            buf.writeBoolean(bountyDefault);
            buf.writeVarLong(maxBounty);
            buf.writeVarLong(murderFine);
            buf.writeBoolean(self.isPresent());
            self.ifPresent(row -> row.encode(buf));
            buf.writeVarInt(reputation);
            buf.writeVarLong(debt);
            buf.writeVarInt(confiscations);
        }

        public static TabView decode(FriendlyByteBuf buf) {
            return new TabView(buf.readUUID(), TradeViews.Currency.decode(buf), buf.readBoolean(), readList(buf, MAX_ROWS, WantedRow::decode),
                    buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readBoolean(), buf.readVarLong(), buf.readBoolean(),
                    buf.readVarLong(), buf.readVarLong(), buf.readBoolean() ? Optional.of(WantedRow.decode(buf)) : Optional.empty(),
                    buf.readVarInt(), buf.readVarLong(), buf.readVarInt());
        }
    }

    // ---------------------------------------------------------------- the player's window

    /**
     * What a player has to do with one kingdom.
     *
     * @param wanted     the record there, if any
     * @param passMinutes minutes the pass into its cities still holds, 0 for none
     * @param here       whether the player stands on its land
     */
    public record KingdomRow(TradeViews.Currency currency, Optional<WantedRow> wanted, int reputation, long debt, long account, long passMinutes,
                             boolean here) {
        public KingdomRow {
            Objects.requireNonNull(currency, "currency");
            wanted = wanted == null ? Optional.empty() : wanted;
        }

        void encode(FriendlyByteBuf buf) {
            currency.encode(buf);
            buf.writeBoolean(wanted.isPresent());
            wanted.ifPresent(row -> row.encode(buf));
            buf.writeVarInt(reputation);
            buf.writeVarLong(debt);
            buf.writeVarLong(account);
            buf.writeVarLong(passMinutes);
            buf.writeBoolean(here);
        }

        static KingdomRow decode(FriendlyByteBuf buf) {
            return new KingdomRow(TradeViews.Currency.decode(buf), buf.readBoolean() ? Optional.of(WantedRow.decode(buf)) : Optional.empty(),
                    buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readBoolean());
        }
    }

    /**
     * Goods the guards of a kingdom took from the player.
     *
     * @param gone    whether the kingdom is gone: the goods are given back for free anywhere
     * @param near    whether the player stands at a place where they are bought back
     */
    public record ConfiscationRow(long id, String kingdomName, int color, TradeViews.Currency currency, long worth, long ransom, int items,
                                  long daysLeft, boolean gone, boolean near, List<ItemStack> sample) {
        public ConfiscationRow {
            kingdomName = kingdomName == null ? "" : kingdomName;
            sample = List.copyOf(sample);
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeUtf(kingdomName, KingdomNames.MAX_LENGTH);
            buf.writeInt(color);
            currency.encode(buf);
            buf.writeVarLong(worth);
            buf.writeVarLong(ransom);
            buf.writeVarInt(items);
            buf.writeVarLong(daysLeft);
            buf.writeBoolean(gone);
            buf.writeBoolean(near);
            writeList(buf, sample, MAX_GOODS, FriendlyByteBuf::writeItem);
        }

        static ConfiscationRow decode(FriendlyByteBuf buf) {
            return new ConfiscationRow(buf.readVarLong(), buf.readUtf(KingdomNames.MAX_LENGTH), buf.readInt(), TradeViews.Currency.decode(buf),
                    buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readVarLong(), buf.readBoolean(), buf.readBoolean(),
                    readList(buf, MAX_GOODS, FriendlyByteBuf::readItem));
        }
    }

    /**
     * The player's window of the law.
     *
     * @param autoPay whether the player pays entry fees at the gates by themselves
     */
    public record PlayerView(List<KingdomRow> kingdoms, List<ConfiscationRow> confiscations, boolean autoPay) {
        public PlayerView {
            kingdoms = List.copyOf(kingdoms);
            confiscations = List.copyOf(confiscations);
        }

        public void encode(FriendlyByteBuf buf) {
            writeList(buf, kingdoms, MAX_ROWS, (b, row) -> row.encode(b));
            writeList(buf, confiscations, MAX_ROWS, (b, row) -> row.encode(b));
            buf.writeBoolean(autoPay);
        }

        public static PlayerView decode(FriendlyByteBuf buf) {
            return new PlayerView(readList(buf, MAX_ROWS, KingdomRow::decode), readList(buf, MAX_ROWS, ConfiscationRow::decode), buf.readBoolean());
        }
    }

    // ---------------------------------------------------------------- dialogs

    /**
     * A window that asks the player to pay: to give up to a guard, or to enter a city.
     *
     * @param amount    the fine or the fee
     * @param execution whether giving up means death (a murderer the court did not spare)
     * @param city      the city at whose gate the player stands, for an entry
     * @param coins     worth of the coins of the kingdom the player carries
     * @param account   what the account in the kingdom's currency holds
     */
    public record DialogView(Dialog dialog, UUID kingdomId, TradeViews.Currency currency, long amount, boolean execution, String city,
                             List<CrimeRow> crimes, long coins, long account) {
        public DialogView {
            Objects.requireNonNull(dialog, "dialog");
            Objects.requireNonNull(kingdomId, "kingdomId");
            city = city == null ? "" : city;
            crimes = List.copyOf(crimes);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeEnum(dialog);
            buf.writeUUID(kingdomId);
            currency.encode(buf);
            buf.writeVarLong(amount);
            buf.writeBoolean(execution);
            buf.writeUtf(city, KingdomNames.MAX_LENGTH);
            writeList(buf, crimes, MAX_CRIMES, (b, row) -> row.encode(b));
            buf.writeVarLong(coins);
            buf.writeVarLong(account);
        }

        public static DialogView decode(FriendlyByteBuf buf) {
            return new DialogView(KingdomView.readEnum(buf, Dialog.class), buf.readUUID(), TradeViews.Currency.decode(buf), buf.readVarLong(),
                    buf.readBoolean(), buf.readUtf(KingdomNames.MAX_LENGTH), readList(buf, MAX_CRIMES, CrimeRow::decode), buf.readVarLong(),
                    buf.readVarLong());
        }
    }

    public enum Dialog {
        SURRENDER,
        ENTRY
    }

    /**
     * The window to buy back confiscated goods.
     */
    public record RansomView(ConfiscationRow confiscation, List<ItemStack> goods, long coins, long account) {
        public RansomView {
            Objects.requireNonNull(confiscation, "confiscation");
            goods = List.copyOf(goods);
        }

        public void encode(FriendlyByteBuf buf) {
            confiscation.encode(buf);
            writeList(buf, goods, MAX_GOODS, FriendlyByteBuf::writeItem);
            buf.writeVarLong(coins);
            buf.writeVarLong(account);
        }

        public static RansomView decode(FriendlyByteBuf buf) {
            return new RansomView(ConfiscationRow.decode(buf), readList(buf, MAX_GOODS, FriendlyByteBuf::readItem), buf.readVarLong(),
                    buf.readVarLong());
        }
    }

    /**
     * The line above the hotbar while the player stands on the land of a kingdom that wants them.
     */
    public record WantedLine(boolean shown, String kingdomName, int color, boolean execution, long fine, long silver, long gold) {
        public static final WantedLine NONE = new WantedLine(false, "", 0, false, 0, 10, 100);

        public WantedLine {
            kingdomName = kingdomName == null ? "" : kingdomName;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(shown);
            buf.writeUtf(kingdomName, KingdomNames.MAX_LENGTH);
            buf.writeInt(color);
            buf.writeBoolean(execution);
            buf.writeVarLong(fine);
            buf.writeVarLong(silver);
            buf.writeVarLong(gold);
        }

        public static WantedLine decode(FriendlyByteBuf buf) {
            return new WantedLine(buf.readBoolean(), buf.readUtf(KingdomNames.MAX_LENGTH), buf.readInt(), buf.readBoolean(), buf.readVarLong(),
                    buf.readVarLong(), buf.readVarLong());
        }
    }
}
