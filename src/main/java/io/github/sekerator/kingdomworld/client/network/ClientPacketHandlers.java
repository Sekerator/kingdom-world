package io.github.sekerator.kingdomworld.client.network;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.client.ClientHooks;
import io.github.sekerator.kingdomworld.client.map.ClientTerritoryCache;
import io.github.sekerator.kingdomworld.client.screen.ResultListener;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.client.screen.WorkPostScreen;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.network.packet.ActionResultPacket;
import io.github.sekerator.kingdomworld.common.network.packet.BorderCrossingPacket;
import io.github.sekerator.kingdomworld.common.network.packet.CitizenViewPacket;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomListResponsePacket;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomViewPacket;
import io.github.sekerator.kingdomworld.common.network.packet.MapAreaPacket;
import io.github.sekerator.kingdomworld.common.network.packet.OpenFoundingScreenPacket;
import io.github.sekerator.kingdomworld.common.network.packet.TerritoryDeltaPacket;
import io.github.sekerator.kingdomworld.common.network.packet.WorkPostViewPacket;
import io.github.sekerator.kingdomworld.common.network.data.StorageView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.slf4j.Logger;

/**
 * Client-side handling of server packets. Runs on the client main thread.
 */
public final class ClientPacketHandlers {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ClientPacketHandlers() {
    }

    public static void handleKingdomList(KingdomListResponsePacket packet) {
        ClientKingdomCache.update(packet.total(), packet.kingdoms());
        LOGGER.debug("Received kingdom list page {}: {} of {} kingdom(s)", packet.page(), packet.kingdoms().size(), packet.total());
    }

    public static void handleOpenFounding(OpenFoundingScreenPacket packet) {
        ClientHooks.openFounding(packet.throne(), packet.suggestedColor());
    }

    public static void handleKingdomView(KingdomViewPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ThroneScreen throne && throne.kingdomId().equals(packet.view().id())) {
            throne.update(packet.view());
        } else if (packet.open()) {
            ClientHooks.openThrone(packet.view());
        }
    }

    public static void handleCitizenView(CitizenViewPacket packet) {
        if (Minecraft.getInstance().screen instanceof ThroneScreen throne) {
            throne.updateCitizens(packet.view());
        }
    }

    public static void handleOrdersView(io.github.sekerator.kingdomworld.common.network.data.OrdersView view) {
        if (Minecraft.getInstance().screen instanceof ThroneScreen throne) {
            throne.updateOrders(view);
        }
    }

    public static void handleCatalog(io.github.sekerator.kingdomworld.common.network.data.CatalogView view) {
        if (Minecraft.getInstance().screen instanceof ThroneScreen throne) {
            throne.updateCatalog(view);
        }
    }

    public static void handleTreasuryView(io.github.sekerator.kingdomworld.common.network.data.TreasuryView view) {
        if (Minecraft.getInstance().screen instanceof ThroneScreen throne) {
            throne.updateTreasury(view);
        }
    }

    public static void handleBank(io.github.sekerator.kingdomworld.common.network.data.BankView view, boolean open) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof io.github.sekerator.kingdomworld.client.screen.BankScreen bank && bank.pos().equals(view.pos())) {
            bank.update(view);
        } else if (open) {
            ClientHooks.openBank(view);
        }
    }

    public static void handleStall(io.github.sekerator.kingdomworld.common.network.data.TradeViews.StallView view, boolean open) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof io.github.sekerator.kingdomworld.client.screen.StallScreen stall && stall.pos().equals(view.pos())) {
            stall.update(view);
        } else if (open) {
            ClientHooks.openStall(view);
        }
    }

    public static void handleMarketView(io.github.sekerator.kingdomworld.common.network.data.TradeViews.MarketView view) {
        if (Minecraft.getInstance().screen instanceof ThroneScreen throne) {
            throne.updateMarket(view);
        }
    }

    public static void handleTradeView(io.github.sekerator.kingdomworld.common.network.data.TradeViews.TradeView view) {
        if (Minecraft.getInstance().screen instanceof ThroneScreen throne) {
            throne.updateTrade(view);
        }
    }

    public static void handleExchange(io.github.sekerator.kingdomworld.common.network.data.TradeViews.ExchangeView view, boolean open) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof io.github.sekerator.kingdomworld.client.screen.ExchangeScreen exchange && exchange.pos().equals(view.pos())) {
            exchange.update(view);
        } else if (open) {
            ClientHooks.openExchange(view);
        }
    }

    public static void handleCaravans(java.util.List<io.github.sekerator.kingdomworld.common.network.data.TradeViews.CaravanInfo> caravans) {
        io.github.sekerator.kingdomworld.client.map.ClientTradeCache.replace(caravans);
    }

    public static void handleCurrencies(java.util.List<io.github.sekerator.kingdomworld.common.network.data.CurrencyInfo> currencies) {
        io.github.sekerator.kingdomworld.common.economy.CurrencyDirectory.replace(currencies);
    }

    public static void handleConstructionView(io.github.sekerator.kingdomworld.common.network.data.ConstructionView view) {
        if (Minecraft.getInstance().screen instanceof ThroneScreen throne) {
            throne.updateConstruction(view);
        }
    }

    public static void handleTemplateData(io.github.sekerator.kingdomworld.common.network.packet.ConstructionPackets.TemplateData data) {
        io.github.sekerator.kingdomworld.client.construction.ClientBlueprint.accept(data);
    }

    public static void handleConstructionCheck(io.github.sekerator.kingdomworld.common.network.packet.ConstructionPackets.CheckResult result) {
        io.github.sekerator.kingdomworld.client.construction.ClientBlueprint.accept(result);
    }

    public static void handleStorageView(StorageView view) {
        if (Minecraft.getInstance().screen instanceof ThroneScreen throne) {
            throne.updateStorage(view);
        }
    }

    public static void handleWorkPostView(WorkPostViewPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof WorkPostScreen screen && screen.pos().equals(packet.view().pos())) {
            screen.update(packet.view());
        } else if (packet.open()) {
            ClientHooks.openWorkPost(packet.view());
        }
    }

    public static void handleActionResult(ActionResultPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ResultListener listener) {
            listener.onResult(packet.success(), packet.message());
        } else if (minecraft.player != null) {
            minecraft.player.displayClientMessage(packet.message().copy()
                    .withStyle(packet.success() ? ChatFormatting.WHITE : ChatFormatting.RED), false);
        }
    }

    public static void handleMapArea(MapAreaPacket packet) {
        ClientTerritoryCache.applyArea(packet);
    }

    public static void handleTerritoryDelta(TerritoryDeltaPacket packet) {
        ClientTerritoryCache.applyDelta(packet);
    }

    public static void handleTerritorySnapshot(io.github.sekerator.kingdomworld.common.network.packet.TerritorySnapshotPacket packet) {
        ClientTerritoryCache.applySnapshot(packet);
        io.github.sekerator.kingdomworld.client.map.ClientMapSync.onSnapshot(packet);
    }

    public static void handleBorderCrossing(BorderCrossingPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !KingdomWorldConfig.CLIENT.showBorderMessages.get()) {
            return;
        }
        minecraft.player.displayClientMessage(borderMessage(minecraft.font, minecraft.getWindow().getGuiScaledWidth(),
                packet.entered(), packet.kingdomName(), packet.color()), true);
    }

    /**
     * The border message with the kingdom name shortened so that the line fits the screen; lines above the hotbar
     * are never wrapped.
     */
    public static Component borderMessage(Font font, int screenWidth, boolean entered, String kingdomName, int color) {
        String key = entered ? "kingdomworld.border.entered" : "kingdomworld.border.left";
        int available = screenWidth - 24 - font.width(Component.translatable(key, ""));
        String shown = kingdomName;
        if (font.width(shown) > available) {
            shown = font.plainSubstrByWidth(kingdomName, Math.max(0, available - font.width("\u2026"))) + "\u2026";
        }
        Component name = Component.literal(shown).withStyle(style -> style.withColor(TextColor.fromRgb(color)));
        return Component.translatable(key, name);
    }
}
