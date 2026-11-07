package io.github.sekerator.kingdomworld.common.network.data;

import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.companion.CompanionOrder;
import io.github.sekerator.kingdomworld.core.companion.LoyaltyReason;
import io.github.sekerator.kingdomworld.core.companion.Origin;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What the server shows about companions and parties: the market and the hire at a desk of the guild, the window of a
 * companion, the window of a party and the list in the corner of the screen. Names travel as seeds so that every client
 * shows them in its own alphabet.
 */
public final class CompanionViews {
    /** Offers one market shows at most. */
    public static final int MAX_OFFERS = 32;
    /** Rows of a list at most. */
    public static final int MAX_ROWS = 64;
    /** Longest name of a player. */
    public static final int MAX_NAME = 64;

    private CompanionViews() {
    }

    private static <T> List<T> readList(FriendlyByteBuf buf, int max, java.util.function.Function<FriendlyByteBuf, T> reader) {
        int size = buf.readVarInt();
        if (size < 0 || size > max) {
            throw new IllegalArgumentException("List too long: " + size);
        }
        List<T> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(reader.apply(buf));
        }
        return result;
    }

    private static <T> void writeList(FriendlyByteBuf buf, List<T> list, java.util.function.BiConsumer<T, FriendlyByteBuf> writer) {
        buf.writeVarInt(list.size());
        for (T item : list) {
            writer.accept(item, buf);
        }
    }

    // ---------------------------------------------------------------- the market

    /**
     * A person on the market.
     *
     * @param price what the slave costs, or the daily wage
     * @param power strength, rounded
     */
    public record OfferRow(long id, boolean slave, int seed, Gender gender, int skin, Origin origin, int level, int combat, int carrying,
                           int stamina, int health, int power, long price) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeBoolean(slave);
            buf.writeInt(seed);
            buf.writeEnum(gender);
            buf.writeVarInt(skin);
            buf.writeEnum(origin);
            buf.writeVarInt(level);
            buf.writeVarInt(combat);
            buf.writeVarInt(carrying);
            buf.writeVarInt(stamina);
            buf.writeVarInt(health);
            buf.writeVarInt(power);
            buf.writeVarLong(price);
        }

        public static OfferRow decode(FriendlyByteBuf buf) {
            return new OfferRow(buf.readVarLong(), buf.readBoolean(), buf.readInt(), KingdomView.readEnum(buf, Gender.class), buf.readVarInt(),
                    KingdomView.readEnum(buf, Origin.class), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarLong());
        }
    }

    /**
     * The market of slaves and the hire of free companions at a desk.
     *
     * @param member        whether the viewer is in the guild
     * @param owned         companions the viewer keeps
     * @param limit         companions the viewer's rank allows
     * @param closed        whether the king closed the market of slaves here
     * @param canClose      whether the viewer is the king who may close or open it
     * @param forbidden     whether the law forbids slaves here
     * @param ticksToChange ticks until the offers change
     */
    public record MarketView(BlockPos pos, UUID branch, String branchName, int branchColor, TradeViews.Currency currency, boolean member,
                             Rank rank, int owned, int limit, long balance, boolean closed, boolean canClose, boolean forbidden,
                             long ticksToChange, List<OfferRow> slaves, List<OfferRow> free) {
        public MarketView {
            slaves = List.copyOf(slaves);
            free = List.copyOf(free);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeUUID(branch);
            buf.writeUtf(branchName, 256);
            buf.writeInt(branchColor);
            currency.encode(buf);
            buf.writeBoolean(member);
            buf.writeEnum(rank);
            buf.writeVarInt(owned);
            buf.writeVarInt(limit);
            buf.writeVarLong(balance);
            buf.writeBoolean(closed);
            buf.writeBoolean(canClose);
            buf.writeBoolean(forbidden);
            buf.writeVarLong(ticksToChange);
            writeList(buf, slaves, OfferRow::encode);
            writeList(buf, free, OfferRow::encode);
        }

        public static MarketView decode(FriendlyByteBuf buf) {
            return new MarketView(buf.readBlockPos(), buf.readUUID(), buf.readUtf(256), buf.readInt(), TradeViews.Currency.decode(buf),
                    buf.readBoolean(), KingdomView.readEnum(buf, Rank.class), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readVarLong(), readList(buf, MAX_OFFERS, OfferRow::decode),
                    readList(buf, MAX_OFFERS, OfferRow::decode));
        }
    }

    // ---------------------------------------------------------------- a companion

    /**
     * A change of loyalty, for the window.
     */
    public record Note(LoyaltyReason reason, int change) {
    }

    /**
     * The window of a companion.
     *
     * @param power      strength with the gear it carries, rounded
     * @param nextLevel  experience the next level needs, 0 at the top
     * @param wage       the daily wage of a free companion; for a slave, what it would ask once set free
     * @param sellValue  what the market pays for it, 0 for a free companion
     * @param canSell    whether the market can pay that now
     * @param subject    whether the owner is a subject of a kingdom that could take it in as a citizen
     * @param gear       what it holds and wears: main hand, off hand, head, chest, legs, feet
     */
    public record CompanionView(UUID id, int seed, Gender gender, int skin, boolean slave, Origin origin, int level, long experience,
                                long nextLevel, int combat, int carrying, int stamina, float health, float maxHealth, int food,
                                boolean starving, int loyalty, boolean warned, List<Note> notes, CompanionOrder order, int power, int kills,
                                TradeViews.Currency currency, long wage, long sellValue, boolean canSell, boolean subject, int slots,
                                List<ItemStack> gear) {
        public CompanionView {
            notes = List.copyOf(notes);
            gear = List.copyOf(gear);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeInt(seed);
            buf.writeEnum(gender);
            buf.writeVarInt(skin);
            buf.writeBoolean(slave);
            buf.writeEnum(origin);
            buf.writeVarInt(level);
            buf.writeVarLong(experience);
            buf.writeVarLong(nextLevel);
            buf.writeVarInt(combat);
            buf.writeVarInt(carrying);
            buf.writeVarInt(stamina);
            buf.writeFloat(health);
            buf.writeFloat(maxHealth);
            buf.writeVarInt(food);
            buf.writeBoolean(starving);
            buf.writeVarInt(loyalty);
            buf.writeBoolean(warned);
            writeList(buf, notes, (note, out) -> {
                out.writeEnum(note.reason());
                out.writeInt(note.change());
            });
            buf.writeEnum(order);
            buf.writeVarInt(power);
            buf.writeVarInt(kills);
            currency.encode(buf);
            buf.writeVarLong(wage);
            buf.writeVarLong(sellValue);
            buf.writeBoolean(canSell);
            buf.writeBoolean(subject);
            buf.writeVarInt(slots);
            writeList(buf, gear, (stack, out) -> out.writeItem(stack));
        }

        public static CompanionView decode(FriendlyByteBuf buf) {
            return new CompanionView(buf.readUUID(), buf.readInt(), KingdomView.readEnum(buf, Gender.class), buf.readVarInt(), buf.readBoolean(),
                    KingdomView.readEnum(buf, Origin.class), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(),
                    buf.readBoolean(), readList(buf, 16, in -> new Note(KingdomView.readEnum(in, LoyaltyReason.class), in.readInt())),
                    KingdomView.readEnum(buf, CompanionOrder.class), buf.readVarInt(), buf.readVarInt(), TradeViews.Currency.decode(buf),
                    buf.readVarLong(), buf.readVarLong(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(),
                    readList(buf, 8, FriendlyByteBuf::readItem));
        }
    }

    // ---------------------------------------------------------------- a party

    /**
     * A member of a party.
     *
     * @param permille what the member gets of a reward, in thousandths
     */
    public record MemberRow(UUID player, String name, boolean leader, boolean online, float health, float maxHealth, int share, int permille,
                            String rank) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(player);
            buf.writeUtf(name, MAX_NAME);
            buf.writeBoolean(leader);
            buf.writeBoolean(online);
            buf.writeFloat(health);
            buf.writeFloat(maxHealth);
            buf.writeVarInt(share);
            buf.writeVarInt(permille);
            buf.writeUtf(rank, 8);
        }

        public static MemberRow decode(FriendlyByteBuf buf) {
            return new MemberRow(buf.readUUID(), buf.readUtf(MAX_NAME), buf.readBoolean(), buf.readBoolean(), buf.readFloat(), buf.readFloat(),
                    buf.readVarInt(), buf.readVarInt(), buf.readUtf(8));
        }
    }

    /**
     * An invitation, to the player or from the party.
     *
     * @param party the party; for an invitation from the party, the name is the invited player's
     */
    public record InviteRow(long party, String name, long ticksLeft) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(party);
            buf.writeUtf(name, MAX_NAME);
            buf.writeVarLong(ticksLeft);
        }

        public static InviteRow decode(FriendlyByteBuf buf) {
            return new InviteRow(buf.readVarLong(), buf.readUtf(MAX_NAME), buf.readVarLong());
        }
    }

    /**
     * The window of a party.
     *
     * @param inParty   whether the viewer is in a party
     * @param leader    whether the viewer leads it
     * @param member    whether the viewer is in the guild
     * @param contracts contracts the party holds
     */
    public record PartyView(boolean member, boolean inParty, boolean leader, int maxMembers, boolean evenSplit, boolean friendlyFire,
                            int contracts, String leaderRank, List<MemberRow> members, List<InviteRow> invited, List<InviteRow> invitations) {
        public PartyView {
            members = List.copyOf(members);
            invited = List.copyOf(invited);
            invitations = List.copyOf(invitations);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(member);
            buf.writeBoolean(inParty);
            buf.writeBoolean(leader);
            buf.writeVarInt(maxMembers);
            buf.writeBoolean(evenSplit);
            buf.writeBoolean(friendlyFire);
            buf.writeVarInt(contracts);
            buf.writeUtf(leaderRank, 8);
            writeList(buf, members, MemberRow::encode);
            writeList(buf, invited, InviteRow::encode);
            writeList(buf, invitations, InviteRow::encode);
        }

        public static PartyView decode(FriendlyByteBuf buf) {
            return new PartyView(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(),
                    buf.readVarInt(), buf.readUtf(8), readList(buf, MAX_ROWS, MemberRow::decode), readList(buf, MAX_ROWS, InviteRow::decode),
                    readList(buf, MAX_ROWS, InviteRow::decode));
        }
    }

    // ---------------------------------------------------------------- the list in the corner

    /**
     * A line of the list in the corner: a member of the party or a companion of the player.
     *
     * @param name the player's name; empty for a companion, whose name comes from the seed
     */
    public record HudLine(boolean companion, String name, int seed, Gender gender, boolean leader, boolean online, float health,
                          float maxHealth, boolean warned) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(companion);
            buf.writeUtf(name, MAX_NAME);
            buf.writeInt(seed);
            buf.writeEnum(gender);
            buf.writeBoolean(leader);
            buf.writeBoolean(online);
            buf.writeFloat(health);
            buf.writeFloat(maxHealth);
            buf.writeBoolean(warned);
        }

        public static HudLine decode(FriendlyByteBuf buf) {
            return new HudLine(buf.readBoolean(), buf.readUtf(MAX_NAME), buf.readInt(), KingdomView.readEnum(buf, Gender.class), buf.readBoolean(),
                    buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
        }
    }

    public static void writeHud(FriendlyByteBuf buf, List<HudLine> lines) {
        writeList(buf, lines, HudLine::encode);
    }

    public static List<HudLine> readHud(FriendlyByteBuf buf) {
        return readList(buf, MAX_ROWS, HudLine::decode);
    }
}
