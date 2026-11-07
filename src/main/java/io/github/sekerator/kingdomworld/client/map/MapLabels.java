package io.github.sekerator.kingdomworld.client.map;

import io.github.sekerator.kingdomworld.common.economy.CurrencyDirectory;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.network.data.KingdomMapInfo;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Texts that map integrations on the client show for kingdoms and caravans, in the player's language.
 */
public final class MapLabels {
    private MapLabels() {
    }

    /**
     * The lines shown when the pointer rests on a kingdom's land: name, ruler and currency.
     */
    public static List<Component> kingdom(KingdomMapInfo info) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(info.name()));
        lines.add(ruler(info));
        lines.add(Component.translatable("kingdomworld.map.currency", currency(info)));
        return lines;
    }

    public static Component ruler(KingdomMapInfo info) {
        return info.rulerName().isEmpty() ? Component.translatable("kingdomworld.map.ruler_none")
                : Component.translatable("kingdomworld.map.ruler", info.rulerName());
    }

    public static Component currency(KingdomMapInfo info) {
        String name = CurrencyDirectory.get(info.id()).map(currency -> currency.name()).orElse("");
        return MoneyTexts.currency(name, info.name());
    }

    public static Component capital(KingdomMapInfo info) {
        return Component.translatable("kingdomworld.map.capital", info.name());
    }

    public static Component caravan(TradeViews.CaravanInfo caravan, long gameTime) {
        KingdomMapInfo from = ClientTerritoryCache.kingdom(caravan.seller());
        KingdomMapInfo to = ClientTerritoryCache.kingdom(caravan.buyer());
        return Component.translatable("kingdomworld.map.caravan", from == null ? "?" : from.name(), to == null ? "?" : to.name(),
                (int) Math.round(caravan.progress(gameTime) * 100));
    }

    /**
     * Lines joined for map mods that show one text.
     */
    public static String joined(List<Component> lines) {
        StringBuilder text = new StringBuilder();
        for (Component line : lines) {
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(line.getString());
        }
        return text.toString();
    }
}
