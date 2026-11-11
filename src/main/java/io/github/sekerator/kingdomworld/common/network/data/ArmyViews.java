package io.github.sekerator.kingdomworld.common.network.data;

import io.github.sekerator.kingdomworld.core.army.Branch;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.army.Post;
import io.github.sekerator.kingdomworld.core.army.SoldierRank;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What the screens of the army show: the "Army" tab of the throne screen (the strength and cost of the army, soldiers,
 * squads and their orders, citizens who can be recruited, mercenaries on offer and hired, guards of deals) and the barracks.
 * Built by the server for one viewer; lists are cut to {@link #MAX_ROWS}.
 */
public final class ArmyViews {
    public static final int MAX_ROWS = 64;

    private ArmyViews() {
    }

    private static <T> List<T> readList(FriendlyByteBuf buf, java.util.function.Function<FriendlyByteBuf, T> reader) {
        int size = Math.min(MAX_ROWS, buf.readVarInt());
        List<T> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(reader.apply(buf));
        }
        return list;
    }

    private static <T> void writeList(FriendlyByteBuf buf, List<T> list, java.util.function.BiConsumer<T, FriendlyByteBuf> writer) {
        int size = Math.min(MAX_ROWS, list.size());
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            writer.accept(list.get(i), buf);
        }
    }

    /**
     * A soldier.
     *
     * @param where     where it is and what it does now
     * @param squad     its squad, 0 for none
     * @param branch    the guard or the army
     * @param post      fighter, vice-captain or captain of its branch
     * @param maxHealth its full health, by its branch, its post and the armor it wears
     */
    public record SoldierRow(UUID id, Component name, boolean archer, SoldierRank rank, long experience, int kills, ItemStack weapon, int armor,
                             CitizenState state, int health, long squad, Component where, Branch branch, Post post, int maxHealth) {
        public SoldierRow {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(rank, "rank");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(where, "where");
            Objects.requireNonNull(branch, "branch");
            Objects.requireNonNull(post, "post");
            weapon = weapon == null ? ItemStack.EMPTY : weapon;
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeComponent(name);
            buf.writeBoolean(archer);
            buf.writeVarInt(rank.ordinal());
            buf.writeVarLong(experience);
            buf.writeVarInt(kills);
            buf.writeItem(weapon);
            buf.writeVarInt(armor);
            buf.writeEnum(state);
            buf.writeVarInt(health);
            buf.writeVarLong(squad);
            buf.writeComponent(where);
            buf.writeEnum(branch);
            buf.writeEnum(post);
            buf.writeVarInt(maxHealth);
        }

        static SoldierRow decode(FriendlyByteBuf buf) {
            return new SoldierRow(buf.readUUID(), buf.readComponent(), buf.readBoolean(), SoldierRank.ofIndex(buf.readVarInt()), buf.readVarLong(),
                    buf.readVarInt(), buf.readItem(), buf.readVarInt(), KingdomView.readEnum(buf, CitizenState.class), buf.readVarInt(),
                    buf.readVarLong(), buf.readComponent(), KingdomView.readEnum(buf, Branch.class), KingdomView.readEnum(buf, Post.class),
                    buf.readVarInt());
        }
    }

    /**
     * A squad of soldiers or a hired band.
     *
     * @param order   what it does, as a text
     * @param onRoad  whether it is away with a caravan
     */
    public record SquadRow(long id, boolean mercenaries, Component name, int members, Component commander, Order.Kind kind, Component order,
                           int power, boolean onRoad) {
        public SquadRow {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(commander, "commander");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(order, "order");
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeBoolean(mercenaries);
            buf.writeComponent(name);
            buf.writeVarInt(members);
            buf.writeComponent(commander);
            buf.writeEnum(kind);
            buf.writeComponent(order);
            buf.writeVarInt(power);
            buf.writeBoolean(onRoad);
        }

        static SquadRow decode(FriendlyByteBuf buf) {
            return new SquadRow(buf.readVarLong(), buf.readBoolean(), buf.readComponent(), buf.readVarInt(), buf.readComponent(),
                    KingdomView.readEnum(buf, Order.Kind.class), buf.readComponent(), buf.readVarInt(), buf.readBoolean());
        }
    }

    /**
     * A citizen who can be recruited.
     */
    public record CandidateRow(UUID id, Component name, String profession, boolean female, CitizenState state) {
        public CandidateRow {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(profession, "profession");
            Objects.requireNonNull(state, "state");
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeComponent(name);
            buf.writeUtf(profession, 32);
            buf.writeBoolean(female);
            buf.writeEnum(state);
        }

        static CandidateRow decode(FriendlyByteBuf buf) {
            return new CandidateRow(buf.readUUID(), buf.readComponent(), buf.readUtf(32), buf.readBoolean(),
                    KingdomView.readEnum(buf, CitizenState.class));
        }
    }

    /**
     * A band of mercenaries, on offer or hired.
     *
     * @param captain  its captain's name
     * @param weapon   the captain's weapon
     * @param advance  what hiring it costs now
     * @param daysLeft days it is paid for, 0 on offer
     */
    public record BandRow(long id, Component captain, int fighters, SoldierRank rank, int power, long dailyPrice, long advance, long daysLeft,
                          ItemStack weapon, boolean hired, long squad) {
        public BandRow {
            Objects.requireNonNull(captain, "captain");
            Objects.requireNonNull(rank, "rank");
            weapon = weapon == null ? ItemStack.EMPTY : weapon;
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeComponent(captain);
            buf.writeVarInt(fighters);
            buf.writeVarInt(rank.ordinal());
            buf.writeVarInt(power);
            buf.writeVarLong(dailyPrice);
            buf.writeVarLong(advance);
            buf.writeVarLong(daysLeft);
            buf.writeItem(weapon);
            buf.writeBoolean(hired);
            buf.writeVarLong(squad);
        }

        static BandRow decode(FriendlyByteBuf buf) {
            return new BandRow(buf.readVarLong(), buf.readComponent(), buf.readVarInt(), SoldierRank.ofIndex(buf.readVarInt()), buf.readVarInt(),
                    buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readItem(), buf.readBoolean(), buf.readVarLong());
        }
    }

    /**
     * A deal of the kingdom whose caravans can have guards.
     *
     * @param partner     the other kingdom
     * @param escort      the squad that guards it, 0 for none
     * @param lossPermille the chance a caravan is lost with the guards it has now
     */
    public record DealRow(long id, String partner, ItemStack goods, int count, boolean selling, long escort, int lossPermille, boolean onRoad) {
        public DealRow {
            Objects.requireNonNull(partner, "partner");
            goods = goods == null ? ItemStack.EMPTY : goods;
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeUtf(partner, KingdomNames.MAX_LENGTH);
            buf.writeItem(goods);
            buf.writeVarInt(count);
            buf.writeBoolean(selling);
            buf.writeVarLong(escort);
            buf.writeVarInt(lossPermille);
            buf.writeBoolean(onRoad);
        }

        static DealRow decode(FriendlyByteBuf buf) {
            return new DealRow(buf.readVarLong(), buf.readUtf(KingdomNames.MAX_LENGTH), buf.readItem(), buf.readVarInt(), buf.readBoolean(),
                    buf.readVarLong(), buf.readVarInt(), buf.readBoolean());
        }
    }

    /**
     * The "Army" tab.
     *
     * @param power        the strength of the whole army
     * @param soldierCost  wages of the soldiers a day
     * @param mercenaryCost pay of the mercenaries a day
     * @param barracks     barracks of the kingdom
     * @param trainingRoom soldiers the barracks train at a time
     */
    public record ArmyTabView(UUID kingdomId, boolean canManage, TradeViews.Currency currency, int soldiers, int maxSoldiers, int mercenaries,
                              int maxMercenaries, int power, long soldierCost, long mercenaryCost, int barracks, int trainingRoom,
                              int advanceDays, List<SoldierRow> soldierRows, List<SquadRow> squads, List<CandidateRow> candidates,
                              List<BandRow> offers, List<BandRow> hired, List<DealRow> deals) {
        public ArmyTabView {
            Objects.requireNonNull(kingdomId, "kingdomId");
            Objects.requireNonNull(currency, "currency");
            soldierRows = List.copyOf(soldierRows);
            squads = List.copyOf(squads);
            candidates = List.copyOf(candidates);
            offers = List.copyOf(offers);
            hired = List.copyOf(hired);
            deals = List.copyOf(deals);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeBoolean(canManage);
            currency.encode(buf);
            buf.writeVarInt(soldiers);
            buf.writeVarInt(maxSoldiers);
            buf.writeVarInt(mercenaries);
            buf.writeVarInt(maxMercenaries);
            buf.writeVarInt(power);
            buf.writeVarLong(soldierCost);
            buf.writeVarLong(mercenaryCost);
            buf.writeVarInt(barracks);
            buf.writeVarInt(trainingRoom);
            buf.writeVarInt(advanceDays);
            writeList(buf, soldierRows, SoldierRow::encode);
            writeList(buf, squads, SquadRow::encode);
            writeList(buf, candidates, CandidateRow::encode);
            writeList(buf, offers, BandRow::encode);
            writeList(buf, hired, BandRow::encode);
            writeList(buf, deals, DealRow::encode);
        }

        public static ArmyTabView decode(FriendlyByteBuf buf) {
            UUID kingdom = buf.readUUID();
            boolean canManage = buf.readBoolean();
            TradeViews.Currency currency = TradeViews.Currency.decode(buf);
            int soldiers = buf.readVarInt();
            int maxSoldiers = buf.readVarInt();
            int mercenaries = buf.readVarInt();
            int maxMercenaries = buf.readVarInt();
            int power = buf.readVarInt();
            long soldierCost = buf.readVarLong();
            long mercenaryCost = buf.readVarLong();
            int barracks = buf.readVarInt();
            int room = buf.readVarInt();
            int advanceDays = buf.readVarInt();
            return new ArmyTabView(kingdom, canManage, currency, soldiers, maxSoldiers, mercenaries, maxMercenaries, power, soldierCost,
                    mercenaryCost, barracks, room, advanceDays, readList(buf, SoldierRow::decode), readList(buf, SquadRow::decode),
                    readList(buf, CandidateRow::decode), readList(buf, BandRow::decode), readList(buf, BandRow::decode),
                    readList(buf, DealRow::decode));
        }
    }

    /**
     * The barracks screen.
     *
     * @param kingdomName empty when the barracks stands on no kingdom's land
     * @param training    soldiers who train at the barracks of the kingdom
     * @param nearest     the nearest unemployed citizen, for recruiting; empty without one
     */
    public record BarracksView(BlockPos pos, String kingdomName, int color, boolean canManage, int soldiers, int maxSoldiers, int barracks,
                               int capacity, long trainingCap, int trainingPerMinute, List<SoldierRow> training, Component nearest) {
        public BarracksView {
            Objects.requireNonNull(pos, "pos");
            Objects.requireNonNull(kingdomName, "kingdomName");
            Objects.requireNonNull(nearest, "nearest");
            training = List.copyOf(training);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeUtf(kingdomName, KingdomNames.MAX_LENGTH);
            buf.writeInt(color);
            buf.writeBoolean(canManage);
            buf.writeVarInt(soldiers);
            buf.writeVarInt(maxSoldiers);
            buf.writeVarInt(barracks);
            buf.writeVarInt(capacity);
            buf.writeVarLong(trainingCap);
            buf.writeVarInt(trainingPerMinute);
            writeList(buf, training, SoldierRow::encode);
            buf.writeComponent(nearest);
        }

        public static BarracksView decode(FriendlyByteBuf buf) {
            return new BarracksView(buf.readBlockPos(), buf.readUtf(KingdomNames.MAX_LENGTH), buf.readInt(), buf.readBoolean(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarInt(), readList(buf, SoldierRow::decode),
                    buf.readComponent());
        }
    }
}
