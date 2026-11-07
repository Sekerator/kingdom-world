package io.github.sekerator.kingdomworld.common.network.data;

import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.RetiredCurrency;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What a client knows about a currency: enough to name and color coins and to show a currency on the map.
 *
 * @param name        the name the king gave it, empty while it carries the name derived from the kingdom's
 * @param kingdomName the name of its kingdom
 * @param color       the color of its kingdom
 * @param old         whether its kingdom was dissolved
 */
public record CurrencyInfo(UUID id, String name, String kingdomName, int color, boolean old, long silver, long gold) {
    /** Most currencies one list carries. */
    public static final int MAX_LIST = 4_096;

    public CurrencyInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kingdomName, "kingdomName");
        color &= Kingdom.MAX_COLOR;
    }

    public static CurrencyInfo of(Kingdom kingdom, Treasury treasury) {
        return new CurrencyInfo(kingdom.id(), treasury.currencyName(), kingdom.name(), kingdom.color(), false, treasury.units().silver(),
                treasury.units().gold());
    }

    public static CurrencyInfo of(RetiredCurrency currency) {
        return new CurrencyInfo(currency.id(), currency.name(), currency.kingdomName(), currency.color(), true, currency.units().silver(),
                currency.units().gold());
    }

    public CurrencyUnits units() {
        return CurrencyUnits.valid(silver, gold) ? new CurrencyUnits(silver, gold) : CurrencyUnits.DEFAULT;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(name, KingdomNames.MAX_LENGTH);
        buf.writeUtf(kingdomName, KingdomNames.MAX_LENGTH);
        buf.writeInt(color);
        buf.writeBoolean(old);
        buf.writeVarLong(silver);
        buf.writeVarLong(gold);
    }

    public static CurrencyInfo decode(FriendlyByteBuf buf) {
        return new CurrencyInfo(buf.readUUID(), buf.readUtf(KingdomNames.MAX_LENGTH), buf.readUtf(KingdomNames.MAX_LENGTH), buf.readInt(),
                buf.readBoolean(), buf.readVarLong(), buf.readVarLong());
    }

    public static void writeList(FriendlyByteBuf buf, List<CurrencyInfo> list) {
        buf.writeVarInt(list.size());
        for (CurrencyInfo info : list) {
            info.encode(buf);
        }
    }

    public static List<CurrencyInfo> readList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_LIST) {
            throw new DecoderException("Too many currencies: " + size);
        }
        List<CurrencyInfo> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(decode(buf));
        }
        return list;
    }
}
