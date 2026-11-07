package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientGuildHandlers;
import io.github.sekerator.kingdomworld.common.guild.GuildActions;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.common.network.data.GuildViews;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.guild.Contract;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The guild on the network: the desk screen and the "Guild" tab of the throne screen. Requests from clients are checked by
 * the server (distance to the desk, rights, ranks, money, limits); answers carry a fresh view.
 */
public final class GuildPackets {
    /** Citizens one request may name, for forming a squad. */
    public static final int MAX_CITIZENS = 16;

    private GuildPackets() {
    }

    // ---------------------------------------------------------------- the desk

    /**
     * Server to client: open the desk screen, or refresh it.
     */
    public record DeskOpen(GuildViews.DeskView view, boolean open) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
            buf.writeBoolean(open);
        }

        public static DeskOpen decode(FriendlyByteBuf buf) {
            return new DeskOpen(GuildViews.DeskView.decode(buf), buf.readBoolean());
        }

        public static void handle(DeskOpen message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientGuildHandlers.handleDesk(message.view(), message.open()));
        }
    }

    /**
     * Client to server: something done at a desk.
     *
     * @param id          the contract taken, given up, handed in or called off
     * @param kind        the kind of a new contract
     * @param target      a monster id, empty for a hunt by rank, or an item id
     * @param rank        the lowest rank of monsters for a hunt by rank
     * @param minRank     the lowest rank that may take the new contract
     * @param forKingdom  whether the branch's kingdom posts and pays, rather than the player
     * @param squads      whether squads of citizens may take the player's contract
     */
    public record DeskAction(BlockPos pos, Action action, long id, Contract.Kind kind, String target, int rank, int count, long reward, int minRank,
                             int days, boolean forKingdom, boolean squads) {
        public static final RequestPolicy POLICY = new RequestPolicy("guild_desk", 0, 20, 10_000L);

        public enum Action {
            REFRESH,
            JOIN,
            TAKE,
            ABANDON,
            HAND_IN,
            CANCEL,
            EXAM,
            POST
        }

        public DeskAction {
            target = target == null ? "" : target;
            kind = kind == null ? Contract.Kind.HUNT : kind;
        }

        public static DeskAction simple(BlockPos pos, Action action, long id) {
            return new DeskAction(pos, action, id, Contract.Kind.HUNT, "", 0, 0, 0, 0, 0, false, false);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeEnum(action);
            buf.writeVarLong(id);
            buf.writeEnum(kind);
            buf.writeUtf(target, Contract.MAX_TARGET);
            buf.writeVarInt(rank);
            buf.writeVarInt(count);
            buf.writeVarLong(reward);
            buf.writeVarInt(minRank);
            buf.writeVarInt(days);
            buf.writeBoolean(forKingdom);
            buf.writeBoolean(squads);
        }

        public static DeskAction decode(FriendlyByteBuf buf) {
            return new DeskAction(buf.readBlockPos(), KingdomView.readEnum(buf, Action.class), buf.readVarLong(),
                    KingdomView.readEnum(buf, Contract.Kind.class), buf.readUtf(Contract.MAX_TARGET), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
        }

        public static void handle(DeskAction message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "rank", message.rank(), 0, Rank.COUNT - 1)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "minRank", message.minRank(), 0, Rank.COUNT - 1)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "count", message.count(), 0, Contract.MAX_COUNT)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "reward", message.reward(), 0, Money.MAX)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "days", message.days(), 0, 365)) {
                return;
            }
            GuildActions.handleDesk(sender, message);
        }
    }

    // ---------------------------------------------------------------- the guild tab

    /**
     * Client to server: show me the guild tab of this kingdom.
     */
    public record TabRequest(UUID kingdomId) {
        public static final RequestPolicy POLICY = new RequestPolicy("guild_view", 0, 20, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
        }

        public static TabRequest decode(FriendlyByteBuf buf) {
            return new TabRequest(buf.readUUID());
        }

        public static void handle(TabRequest message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)) {
                GuildActions.sendTab(sender, message.kingdomId());
            }
        }
    }

    /**
     * Server to client: the guild tab.
     */
    public record TabResponse(GuildViews.GuildTabView view) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
        }

        public static TabResponse decode(FriendlyByteBuf buf) {
            return new TabResponse(GuildViews.GuildTabView.decode(buf));
        }

        public static void handle(TabResponse message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientGuildHandlers.handleTab(message.view()));
        }
    }

    /**
     * Client to server: a decision about the kingdom's branch, squads or contracts.
     *
     * @param first    the commission in permille, the budget, the squad, or the contract called off
     * @param second   the contract a squad goes to
     * @param citizens the members of a new squad
     */
    public record TabChange(UUID kingdomId, Action action, long first, long second, List<UUID> citizens) {
        public static final RequestPolicy POLICY = new RequestPolicy("guild_change", 0, 20, 10_000L);

        public enum Action {
            SET_COMMISSION,
            SET_BUDGET,
            FORM_SQUAD,
            DISBAND,
            SEND,
            CANCEL
        }

        public TabChange {
            citizens = citizens == null ? List.of() : List.copyOf(citizens);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeEnum(action);
            buf.writeVarLong(first);
            buf.writeVarLong(second);
            buf.writeVarInt(citizens.size());
            for (UUID citizen : citizens) {
                buf.writeUUID(citizen);
            }
        }

        public static TabChange decode(FriendlyByteBuf buf) {
            UUID kingdom = buf.readUUID();
            Action action = KingdomView.readEnum(buf, Action.class);
            long first = buf.readVarLong();
            long second = buf.readVarLong();
            int size = buf.readVarInt();
            if (size < 0 || size > MAX_CITIZENS) {
                throw new IllegalArgumentException("Too many citizens in a guild request: " + size);
            }
            List<UUID> citizens = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                citizens.add(buf.readUUID());
            }
            return new TabChange(kingdom, action, first, second, citizens);
        }

        public static void handle(TabChange message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "first", message.first(), 0, Money.MAX)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "second", message.second(), 0, Long.MAX_VALUE)
                    || !ServerRequestGuard.requireSize(sender, POLICY, "citizens", message.citizens(), MAX_CITIZENS)) {
                return;
            }
            GuildActions.handleTab(sender, message);
        }
    }
}
