package io.github.sekerator.kingdomworld.common.network.data;

import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.guild.Contract;
import io.github.sekerator.kingdomworld.core.guild.Rank;
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
 * What the screens of the guild show: the desk (a player's card, the board, the player's contracts) and the "Guild" tab
 * of the throne screen (the branch, adventurers and squads, the kingdom's contracts, crystals and their energy). Built by
 * the server for one viewer; lists are cut to {@link #MAX_ROWS}.
 */
public final class GuildViews {
    public static final int MAX_ROWS = 64;
    private static final int MAX_TARGET = Contract.MAX_TARGET;

    private GuildViews() {
    }

    private static Rank readRank(FriendlyByteBuf buf) {
        return Rank.ofIndex(buf.readVarInt());
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

    // ---------------------------------------------------------------- contracts

    /**
     * A contract as a viewer sees it.
     *
     * @param posterName the kingdom's or the player's name; empty for the guild
     * @param ticksLeft  until the contract runs out
     * @param mine       whether the viewer holds it
     * @param taken      whether somebody holds it
     * @param posted     whether the viewer posted it
     * @param canCancel  whether the viewer may call it off now
     */
    public record ContractRow(long id, Contract.Kind kind, String target, Rank targetRank, boolean boss, int count, int progress, Rank minRank,
                              long reward, int points, int commissionPermille, long ticksLeft, Contract.Poster poster, String posterName,
                              boolean mine, boolean taken, boolean posted, boolean canCancel, boolean squadsAllowed, long squad,
                              ItemStack icon, boolean party) {
        public ContractRow(long id, Contract.Kind kind, String target, Rank targetRank, boolean boss, int count, int progress, Rank minRank,
                           long reward, int points, int commissionPermille, long ticksLeft, Contract.Poster poster, String posterName,
                           boolean mine, boolean taken, boolean posted, boolean canCancel, boolean squadsAllowed, long squad, ItemStack icon) {
            this(id, kind, target, targetRank, boss, count, progress, minRank, reward, points, commissionPermille, ticksLeft, poster, posterName,
                    mine, taken, posted, canCancel, squadsAllowed, squad, icon, false);
        }

        public ContractRow {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(targetRank, "targetRank");
            Objects.requireNonNull(minRank, "minRank");
            Objects.requireNonNull(poster, "poster");
            target = target == null ? "" : target;
            posterName = posterName == null ? "" : posterName;
            icon = icon == null ? ItemStack.EMPTY : icon;
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeEnum(kind);
            buf.writeUtf(target, MAX_TARGET);
            buf.writeVarInt(targetRank.ordinal());
            buf.writeBoolean(boss);
            buf.writeVarInt(count);
            buf.writeVarInt(progress);
            buf.writeVarInt(minRank.ordinal());
            buf.writeVarLong(reward);
            buf.writeVarInt(points);
            buf.writeVarInt(commissionPermille);
            buf.writeVarLong(Math.max(0, ticksLeft));
            buf.writeEnum(poster);
            buf.writeUtf(posterName, KingdomNames.MAX_LENGTH);
            buf.writeBoolean(mine);
            buf.writeBoolean(taken);
            buf.writeBoolean(posted);
            buf.writeBoolean(canCancel);
            buf.writeBoolean(squadsAllowed);
            buf.writeVarLong(squad);
            buf.writeItem(icon);
            buf.writeBoolean(party);
        }

        static ContractRow decode(FriendlyByteBuf buf) {
            return new ContractRow(buf.readVarLong(), KingdomView.readEnum(buf, Contract.Kind.class), buf.readUtf(MAX_TARGET), readRank(buf),
                    buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), readRank(buf), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarLong(), KingdomView.readEnum(buf, Contract.Poster.class), buf.readUtf(KingdomNames.MAX_LENGTH), buf.readBoolean(),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readVarLong(), buf.readItem(),
                    buf.readBoolean());
        }

        /**
         * What the taker gets: the reward less the commission.
         */
        public long payout() {
            return reward - io.github.sekerator.kingdomworld.core.economy.Money.share(reward, commissionPermille, 1000);
        }
    }

    // ---------------------------------------------------------------- the desk

    /**
     * Where a player stands with the exam for the next rank.
     */
    public enum ExamState {
        /** Not in the guild. */
        NONE,
        TOO_FEW_POINTS,
        AVAILABLE,
        COOLDOWN,
        UNDER_WAY,
        /** The highest rank has no exam. */
        TOP
    }

    /**
     * A player's card of the guild.
     *
     * @param nextPoints the points the next rank needs, -1 at the top
     * @param examTicks  the time left of the exam, or until the next try
     */
    public record Card(boolean member, Rank rank, long points, long nextPoints, int kills, int contracts, ExamState exam, long examTicks,
                       int examProgress, int examCount, boolean examBoss) {
        public static final Card NONE = new Card(false, Rank.F, 0, -1, 0, 0, ExamState.NONE, 0, 0, 0, false);

        void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(member);
            buf.writeVarInt(rank.ordinal());
            buf.writeVarLong(points);
            buf.writeLong(nextPoints);
            buf.writeVarInt(kills);
            buf.writeVarInt(contracts);
            buf.writeEnum(exam);
            buf.writeVarLong(Math.max(0, examTicks));
            buf.writeVarInt(examProgress);
            buf.writeVarInt(examCount);
            buf.writeBoolean(examBoss);
        }

        static Card decode(FriendlyByteBuf buf) {
            return new Card(buf.readBoolean(), readRank(buf), buf.readVarLong(), buf.readLong(), buf.readVarInt(), buf.readVarInt(),
                    KingdomView.readEnum(buf, ExamState.class), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
        }

        public java.util.Optional<Rank> next() {
            return rank.next();
        }
    }

    /**
     * A desk of the guild as a player sees it.
     *
     * @param balance            what the player has in the branch's currency
     * @param canPostForKingdom  whether the player may post contracts for the branch's kingdom
     * @param postingFeePermille what players pay for posting, in permille of the reward
     */
    public record DeskView(BlockPos pos, UUID branch, String branchName, int branchColor, TradeViews.Currency currency, Card card,
                           List<ContractRow> board, List<ContractRow> mine, long balance, boolean canPostForKingdom, int commissionPermille,
                           int postingFeePermille, long joinFee, int maxActive, int maxDays, int maxCount, long budgetLeft) {
        public DeskView {
            Objects.requireNonNull(pos, "pos");
            Objects.requireNonNull(branch, "branch");
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(card, "card");
            branchName = branchName == null ? "" : branchName;
            board = List.copyOf(board);
            mine = List.copyOf(mine);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeUUID(branch);
            buf.writeUtf(branchName, KingdomNames.MAX_LENGTH);
            buf.writeInt(branchColor);
            currency.encode(buf);
            card.encode(buf);
            writeList(buf, board, ContractRow::encode);
            writeList(buf, mine, ContractRow::encode);
            buf.writeVarLong(balance);
            buf.writeBoolean(canPostForKingdom);
            buf.writeVarInt(commissionPermille);
            buf.writeVarInt(postingFeePermille);
            buf.writeVarLong(joinFee);
            buf.writeVarInt(maxActive);
            buf.writeVarInt(maxDays);
            buf.writeVarInt(maxCount);
            buf.writeVarLong(budgetLeft);
        }

        public static DeskView decode(FriendlyByteBuf buf) {
            return new DeskView(buf.readBlockPos(), buf.readUUID(), buf.readUtf(KingdomNames.MAX_LENGTH), buf.readInt(),
                    TradeViews.Currency.decode(buf), Card.decode(buf), readList(buf, ContractRow::decode), readList(buf, ContractRow::decode),
                    buf.readVarLong(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarLong());
        }
    }

    // ---------------------------------------------------------------- the guild tab

    /**
     * An adventurer of the kingdom.
     *
     * @param squad     the squad it belongs to, 0 for none
     * @param ticksLeft until its squad comes back, 0 at home
     * @param armor     armor points worn
     */
    public record AdventurerRow(UUID id, Component name, Rank rank, long points, int kills, CitizenState state, long squad, boolean away,
                                long ticksLeft, ItemStack weapon, int armor) {
        public AdventurerRow {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(rank, "rank");
            Objects.requireNonNull(state, "state");
            weapon = weapon == null ? ItemStack.EMPTY : weapon;
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeComponent(name);
            buf.writeVarInt(rank.ordinal());
            buf.writeVarLong(points);
            buf.writeVarInt(kills);
            buf.writeEnum(state);
            buf.writeVarLong(squad);
            buf.writeBoolean(away);
            buf.writeVarLong(Math.max(0, ticksLeft));
            buf.writeItem(weapon);
            buf.writeVarInt(armor);
        }

        static AdventurerRow decode(FriendlyByteBuf buf) {
            return new AdventurerRow(buf.readUUID(), buf.readComponent(), readRank(buf), buf.readVarLong(), buf.readVarInt(),
                    KingdomView.readEnum(buf, CitizenState.class), buf.readVarLong(), buf.readBoolean(), buf.readVarLong(), buf.readItem(),
                    buf.readVarInt());
        }
    }

    /**
     * A squad of the kingdom.
     *
     * @param members  the names of its members
     * @param power    its strength now (at home) or when it left
     * @param task     what its expedition is for, empty at home
     */
    public record SquadRow(long id, List<Component> members, Rank rank, int power, boolean away, long ticksLeft, long contractId, Component task) {
        public SquadRow {
            members = List.copyOf(members);
            Objects.requireNonNull(rank, "rank");
            task = task == null ? Component.empty() : task;
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            writeList(buf, members, (name, out) -> out.writeComponent(name));
            buf.writeVarInt(rank.ordinal());
            buf.writeVarInt(Math.max(0, power));
            buf.writeBoolean(away);
            buf.writeVarLong(Math.max(0, ticksLeft));
            buf.writeVarLong(contractId);
            buf.writeComponent(task);
        }

        static SquadRow decode(FriendlyByteBuf buf) {
            return new SquadRow(buf.readVarLong(), readList(buf, FriendlyByteBuf::readComponent), readRank(buf), buf.readVarInt(), buf.readBoolean(),
                    buf.readVarLong(), buf.readVarLong(), buf.readComponent());
        }
    }

    /**
     * The guild tab of a kingdom.
     *
     * @param crystals    crystals in the storehouses by rank, F first
     * @param energy      their energy by rank
     */
    public record GuildTabView(UUID kingdomId, boolean canManage, TradeViews.Currency currency, int commissionPermille, long dailyBudget,
                               long spentToday, int registrars, int desks, int minCommission, int maxCommission, long maxBudget, int minSquad,
                               int maxSquad, List<AdventurerRow> adventurers, List<SquadRow> squads, List<ContractRow> contracts,
                               long[] crystals, long[] energy) {
        public GuildTabView {
            Objects.requireNonNull(kingdomId, "kingdomId");
            Objects.requireNonNull(currency, "currency");
            adventurers = List.copyOf(adventurers);
            squads = List.copyOf(squads);
            contracts = List.copyOf(contracts);
            if (crystals.length != Rank.COUNT || energy.length != Rank.COUNT) {
                throw new IllegalArgumentException("Crystals by rank need " + Rank.COUNT + " values");
            }
        }

        public long totalEnergy() {
            long sum = 0;
            for (long value : energy) {
                sum = io.github.sekerator.kingdomworld.core.economy.Money.addCapped(sum, value);
            }
            return sum;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeBoolean(canManage);
            currency.encode(buf);
            buf.writeVarInt(commissionPermille);
            buf.writeVarLong(dailyBudget);
            buf.writeVarLong(spentToday);
            buf.writeVarInt(registrars);
            buf.writeVarInt(desks);
            buf.writeVarInt(minCommission);
            buf.writeVarInt(maxCommission);
            buf.writeVarLong(maxBudget);
            buf.writeVarInt(minSquad);
            buf.writeVarInt(maxSquad);
            writeList(buf, adventurers, AdventurerRow::encode);
            writeList(buf, squads, SquadRow::encode);
            writeList(buf, contracts, ContractRow::encode);
            for (int i = 0; i < Rank.COUNT; i++) {
                buf.writeVarLong(crystals[i]);
                buf.writeVarLong(energy[i]);
            }
        }

        public static GuildTabView decode(FriendlyByteBuf buf) {
            UUID kingdom = buf.readUUID();
            boolean canManage = buf.readBoolean();
            TradeViews.Currency currency = TradeViews.Currency.decode(buf);
            int commission = buf.readVarInt();
            long budget = buf.readVarLong();
            long spent = buf.readVarLong();
            int registrars = buf.readVarInt();
            int desks = buf.readVarInt();
            int minCommission = buf.readVarInt();
            int maxCommission = buf.readVarInt();
            long maxBudget = buf.readVarLong();
            int minSquad = buf.readVarInt();
            int maxSquad = buf.readVarInt();
            List<AdventurerRow> adventurers = readList(buf, AdventurerRow::decode);
            List<SquadRow> squads = readList(buf, SquadRow::decode);
            List<ContractRow> contracts = readList(buf, ContractRow::decode);
            long[] crystals = new long[Rank.COUNT];
            long[] energy = new long[Rank.COUNT];
            for (int i = 0; i < Rank.COUNT; i++) {
                crystals[i] = buf.readVarLong();
                energy[i] = buf.readVarLong();
            }
            return new GuildTabView(kingdom, canManage, currency, commission, budget, spent, registrars, desks, minCommission, maxCommission,
                    maxBudget, minSquad, maxSquad, adventurers, squads, contracts, crystals, energy);
        }
    }
}
