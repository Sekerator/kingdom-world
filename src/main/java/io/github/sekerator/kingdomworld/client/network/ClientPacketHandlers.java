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
        ClientHooks.openFounding(packet.throne(), packet.suggestedColor(),
                io.github.sekerator.kingdomworld.core.npc.ArchitecturalStyles.id(packet.suggestedStyle()));
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

    public static void handleFlags(io.github.sekerator.kingdomworld.common.network.packet.FlagPackets.Directory packet) {
        io.github.sekerator.kingdomworld.client.flag.ClientFlags.apply(packet);
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

    public static void handleEarthwork(io.github.sekerator.kingdomworld.common.network.packet.EarthworkPackets.Result result) {
        io.github.sekerator.kingdomworld.client.construction.ClientEarthwork.accept(result);
    }

    public static void handleConstructionCheck(io.github.sekerator.kingdomworld.common.network.packet.ConstructionPackets.CheckResult result) {
        if (Minecraft.getInstance().screen instanceof io.github.sekerator.kingdomworld.client.screen.KingdomMapScreen map) {
            // A building placed on the atlas, checked against the data.
            map.buildChecked(result);
            return;
        }
        io.github.sekerator.kingdomworld.client.construction.ClientBlueprint.accept(result);
    }

    public static void handleStorageView(StorageView view) {
        if (Minecraft.getInstance().screen instanceof ThroneScreen throne) {
            throne.updateStorage(view);
        }
    }

    public static void handleWorkPostView(WorkPostViewPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof WorkPostScreen screen && screen.shows(packet.view())) {
            screen.update(packet.view());
        } else if (packet.open()) {
            if (minecraft.screen instanceof io.github.sekerator.kingdomworld.client.screen.WorkforceScreen workforce) {
                // Opened from the window of the workers: closing it goes back there.
                minecraft.setScreen(new WorkPostScreen(packet.view(), workforce));
            } else {
                ClientHooks.openWorkPost(packet.view());
            }
        }
    }

    /**
     * A packet of the citizens of the player's kingdom for the atlas; the atlas shows them once the whole answer came.
     */
    public static void handleCitizenMap(io.github.sekerator.kingdomworld.common.network.packet.CitizenMapPackets.View packet) {
        io.github.sekerator.kingdomworld.client.map.ClientCitizenMap.receive(packet);
    }

    /**
     * Where a citizen looked for is: its body outlined and the pointer to it.
     */
    public static void handleCitizenFound(io.github.sekerator.kingdomworld.common.network.packet.WorkforcePackets.Found packet) {
        io.github.sekerator.kingdomworld.client.CitizenFinder.found(packet);
        if (Minecraft.getInstance().screen instanceof io.github.sekerator.kingdomworld.client.screen.WorkforceScreen screen) {
            screen.findChanged();
        }
    }

    /**
     * A page of the window of the workers: for the open window, or the window opened by the key, the throne or the command.
     */
    public static void handleWorkforceView(io.github.sekerator.kingdomworld.common.network.packet.WorkforcePackets.View packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof io.github.sekerator.kingdomworld.client.screen.WorkforceScreen screen) {
            screen.update(packet.view());
        } else if (packet.open()) {
            net.minecraft.client.gui.screens.Screen parent = minecraft.screen instanceof ThroneScreen
                    || minecraft.screen instanceof io.github.sekerator.kingdomworld.client.screen.KingdomMapScreen ? minecraft.screen : null;
            minecraft.setScreen(new io.github.sekerator.kingdomworld.client.screen.WorkforceScreen(parent, packet.view()));
        }
    }

    /**
     * The server opens the guide ({@code /kingdom guide}).
     */
    public static void handleOpenGuide() {
        ClientHooks.openGuide();
    }

    /**
     * The numbers of the configuration of the server the guide shows: the pages are laid out again with them.
     */
    public static void handleGuideNumbers(io.github.sekerator.kingdomworld.common.network.packet.GuidePackets.Numbers packet) {
        io.github.sekerator.kingdomworld.client.guide.ClientGuideNumbers.received(packet.values());
        if (Minecraft.getInstance().screen instanceof io.github.sekerator.kingdomworld.client.screen.GuideScreen screen) {
            screen.numbersChanged();
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

    public static void handleProvinceSnapshot(io.github.sekerator.kingdomworld.common.network.packet.ProvinceSnapshotPacket packet) {
        io.github.sekerator.kingdomworld.client.map.ClientProvinceMap.apply(packet);
    }

    public static void handleProvinceDelta(io.github.sekerator.kingdomworld.common.network.packet.ProvinceDeltaPacket packet) {
        io.github.sekerator.kingdomworld.client.map.ClientProvinceMap.apply(packet);
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
