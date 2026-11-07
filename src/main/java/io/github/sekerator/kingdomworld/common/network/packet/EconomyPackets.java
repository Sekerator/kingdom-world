package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientPacketHandlers;
import io.github.sekerator.kingdomworld.common.economy.BankActions;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.common.network.data.BankView;
import io.github.sekerator.kingdomworld.common.network.data.CurrencyInfo;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.common.network.data.TreasuryView;
import io.github.sekerator.kingdomworld.core.citizen.Profession;
import io.github.sekerator.kingdomworld.core.economy.Denomination;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Money on the network: the "Treasury" tab of the throne screen, the bank screen, and the list of currencies every
 * client keeps for coins and the map. Requests from clients are checked by the server (the right to manage the
 * treasury, the distance to the bank, the amounts); answers carry a fresh view.
 */
public final class EconomyPackets {

    private EconomyPackets() {
    }

    // ---------------------------------------------------------------- treasury

    /**
     * Client to server: show me the treasury of this kingdom.
     */
    public record TreasuryRequest(UUID kingdomId) {
        public static final RequestPolicy POLICY = new RequestPolicy("treasury_view", 0, 20, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
        }

        public static TreasuryRequest decode(FriendlyByteBuf buf) {
            return new TreasuryRequest(buf.readUUID());
        }

        public static void handle(TreasuryRequest message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)) {
                TreasuryActions.sendView(sender, message.kingdomId());
            }
        }
    }

    /**
     * Server to client: the treasury.
     */
    public record TreasuryResponse(TreasuryView view) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
        }

        public static TreasuryResponse decode(FriendlyByteBuf buf) {
            return new TreasuryResponse(TreasuryView.decode(buf));
        }

        public static void handle(TreasuryResponse message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleTreasuryView(message.view()));
        }
    }

    /**
     * Client to server: a decision about the treasury.
     *
     * @param text   the profession of a wage, or the new name of the currency
     * @param amount the new tax or wage
     */
    public record TreasuryChange(UUID kingdomId, Action action, String text, long amount) {
        public static final RequestPolicy POLICY = new RequestPolicy("treasury_change", 0, 20, 10_000L);

        public enum Action {
            POLL_TAX,
            WAGE,
            RENAME
        }

        public TreasuryChange {
            text = text == null ? "" : text;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeEnum(action);
            buf.writeUtf(text, KingdomNames.MAX_LENGTH * 2);
            buf.writeVarLong(amount);
        }

        public static TreasuryChange decode(FriendlyByteBuf buf) {
            return new TreasuryChange(buf.readUUID(), KingdomView.readEnum(buf, Action.class), buf.readUtf(KingdomNames.MAX_LENGTH * 2),
                    buf.readVarLong());
        }

        public static void handle(TreasuryChange message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireLength(sender, POLICY, "text", message.text(),
                    message.action() == Action.WAGE ? Profession.MAX_ID_LENGTH : KingdomNames.MAX_LENGTH * 2)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "amount", message.amount(), 0, Money.MAX)) {
                return;
            }
            TreasuryActions.handle(sender, message);
        }
    }

    // ---------------------------------------------------------------- bank

    /**
     * Server to client: open the bank screen, or refresh it.
     */
    public record BankOpen(BankView view, boolean open) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
            buf.writeBoolean(open);
        }

        public static BankOpen decode(FriendlyByteBuf buf) {
            return new BankOpen(BankView.decode(buf), buf.readBoolean());
        }

        public static void handle(BankOpen message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleBank(message.view(), message.open()));
        }
    }

    /**
     * Client to server: hand in coins at a bank or take money from the account.
     *
     * @param coin   the kind of coin: to hand in, or the largest to be paid out in; {@code null} hands in every coin of
     *               the currency, or pays out in the fewest coins
     * @param amount coins to hand in (0 for all of the kind) or the amount to take
     */
    public record BankChange(BlockPos pos, Action action, Denomination coin, long amount) {
        public static final RequestPolicy POLICY = new RequestPolicy("bank_change", 0, 20, 10_000L);

        public enum Action {
            DEPOSIT,
            WITHDRAW
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeEnum(action);
            buf.writeVarInt(coin == null ? -1 : coin.ordinal());
            buf.writeVarLong(amount);
        }

        public static BankChange decode(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            Action action = KingdomView.readEnum(buf, Action.class);
            int coin = buf.readVarInt();
            if (coin < -1 || coin >= Denomination.values().length) {
                throw new io.netty.handler.codec.DecoderException("Unknown coin " + coin);
            }
            return new BankChange(pos, action, coin < 0 ? null : Denomination.values()[coin], buf.readVarLong());
        }

        public static void handle(BankChange message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "amount", message.amount(), 0, Money.MAX)) {
                return;
            }
            BankActions.handle(sender, message);
        }
    }

    // ---------------------------------------------------------------- currencies

    /**
     * Server to client: every current and old currency.
     */
    public record Currencies(List<CurrencyInfo> currencies) {
        public Currencies {
            currencies = List.copyOf(currencies);
        }

        public void encode(FriendlyByteBuf buf) {
            CurrencyInfo.writeList(buf, currencies);
        }

        public static Currencies decode(FriendlyByteBuf buf) {
            return new Currencies(CurrencyInfo.readList(buf));
        }

        public static void handle(Currencies message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleCurrencies(message.currencies()));
        }
    }
}
