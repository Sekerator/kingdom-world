package io.github.sekerator.kingdomworld.common.network.data;

import io.github.sekerator.kingdomworld.core.economy.Coins;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What the bank screen shows: the bank's currency, the coins of it the player carries, the player's debt to the
 * bank's kingdom, and every account of the player.
 *
 * @param currencyId   the currency of the bank, the id of the kingdom whose land it stands on
 * @param currencyName the name the king gave it, empty for the name derived from the kingdom's
 * @param carried      coins of the bank's currency in the player's inventory
 * @param debt         what the player owes the treasury of the bank's kingdom
 */
public record BankView(BlockPos pos, UUID currencyId, String kingdomName, int color, String currencyName, long silver, long gold,
                       Coins carried, long debt, List<Account> accounts) {
    public static final int MAX_ACCOUNTS = 256;

    /**
     * An account of the player.
     */
    public record Account(UUID currency, String name, String kingdomName, int color, boolean old, long balance, long silver, long gold) {
        public Account {
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(kingdomName, "kingdomName");
            color &= Kingdom.MAX_COLOR;
        }

        public CurrencyUnits units() {
            return CurrencyUnits.valid(silver, gold) ? new CurrencyUnits(silver, gold) : CurrencyUnits.DEFAULT;
        }
    }

    public BankView {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(currencyId, "currencyId");
        Objects.requireNonNull(kingdomName, "kingdomName");
        Objects.requireNonNull(currencyName, "currencyName");
        Objects.requireNonNull(carried, "carried");
        color &= Kingdom.MAX_COLOR;
        accounts = List.copyOf(accounts);
        if (accounts.size() > MAX_ACCOUNTS) {
            throw new IllegalArgumentException("Too many accounts");
        }
    }

    public CurrencyUnits units() {
        return CurrencyUnits.valid(silver, gold) ? new CurrencyUnits(silver, gold) : CurrencyUnits.DEFAULT;
    }

    /**
     * What the player has on the account in the bank's currency.
     */
    public long balance() {
        for (Account account : accounts) {
            if (account.currency().equals(currencyId)) {
                return account.balance();
            }
        }
        return 0;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUUID(currencyId);
        buf.writeUtf(kingdomName, KingdomNames.MAX_LENGTH);
        buf.writeInt(color);
        buf.writeUtf(currencyName, KingdomNames.MAX_LENGTH);
        buf.writeVarLong(silver);
        buf.writeVarLong(gold);
        buf.writeVarLong(carried.copper());
        buf.writeVarLong(carried.silver());
        buf.writeVarLong(carried.gold());
        buf.writeVarLong(debt);
        buf.writeVarInt(accounts.size());
        for (Account account : accounts) {
            buf.writeUUID(account.currency());
            buf.writeUtf(account.name(), KingdomNames.MAX_LENGTH);
            buf.writeUtf(account.kingdomName(), KingdomNames.MAX_LENGTH);
            buf.writeInt(account.color());
            buf.writeBoolean(account.old());
            buf.writeVarLong(account.balance());
            buf.writeVarLong(account.silver());
            buf.writeVarLong(account.gold());
        }
    }

    public static BankView decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        UUID currencyId = buf.readUUID();
        String kingdomName = buf.readUtf(KingdomNames.MAX_LENGTH);
        int color = buf.readInt();
        String currencyName = buf.readUtf(KingdomNames.MAX_LENGTH);
        long silver = buf.readVarLong();
        long gold = buf.readVarLong();
        Coins carried = new Coins(TreasuryView.amount(buf), TreasuryView.amount(buf), TreasuryView.amount(buf));
        long debt = TreasuryView.amount(buf);
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ACCOUNTS) {
            throw new DecoderException("Too many accounts: " + size);
        }
        List<Account> accounts = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            accounts.add(new Account(buf.readUUID(), buf.readUtf(KingdomNames.MAX_LENGTH), buf.readUtf(KingdomNames.MAX_LENGTH), buf.readInt(),
                    buf.readBoolean(), TreasuryView.amount(buf), buf.readVarLong(), buf.readVarLong()));
        }
        return new BankView(pos, currencyId, kingdomName, color, currencyName, silver, gold, carried, debt, accounts);
    }
}
