package io.github.sekerator.kingdomworld.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.client.map.ClientTerritoryCache;
import io.github.sekerator.kingdomworld.client.network.ClientKingdomCache;
import io.github.sekerator.kingdomworld.client.render.CitizenRenderer;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomListRequestPacket;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * Client-only bootstrap. Called from the mod constructor on the physical client only.
 */
public final class KingdomWorldClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Opens the kingdom map; K by default, changeable in the controls. */
    public static final KeyMapping MAP_KEY = new KeyMapping("key.kingdomworld.map", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.kingdomworld");
    /** Opens the window of the party; P by default. */
    public static final KeyMapping PARTY_KEY = new KeyMapping("key.kingdomworld.party", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, "key.categories.kingdomworld");
    private static boolean titleScreenSeen;

    private KingdomWorldClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(KingdomWorldClient::onClientSetup);
        modBus.addListener(KingdomWorldClient::onRegisterKeys);
        modBus.addListener(KingdomWorldClient::onRegisterRenderers);
        modBus.addListener(KingdomWorldClient::onItemColors);
        modBus.addListener(KingdomWorldClient::onRegisterOverlays);
        MinecraftForge.EVENT_BUS.addListener(io.github.sekerator.kingdomworld.client.construction.ClientBlueprint::onInteraction);
        MinecraftForge.EVENT_BUS.addListener(io.github.sekerator.kingdomworld.client.construction.BlueprintRenderer::onRenderLevel);
        MinecraftForge.EVENT_BUS.addListener(KingdomWorldClient::onLoggingIn);
        MinecraftForge.EVENT_BUS.addListener(KingdomWorldClient::onLoggingOut);
        MinecraftForge.EVENT_BUS.addListener(KingdomWorldClient::onScreenOpening);
        MinecraftForge.EVENT_BUS.addListener(KingdomWorldClient::onClientTick);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        // Coins show their kind through the model: copper 0, silver 0.5, gold 1.
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(ModRegistries.COIN.get(),
                io.github.sekerator.kingdomworld.KingdomWorld.id("denomination"), (stack, level, entity, seed) ->
                        io.github.sekerator.kingdomworld.common.economy.CoinItem.denomination(stack)
                                .map(denomination -> denomination.ordinal() / 2.0f).orElse(0.0f)));
        event.enqueueWork(() -> net.minecraft.client.gui.screens.MenuScreens.register(
                io.github.sekerator.kingdomworld.common.companion.CompanionRegistries.MENU.get(),
                io.github.sekerator.kingdomworld.client.screen.CompanionScreen::new));
        LOGGER.debug("KingdomWorld client setup complete");
    }

    /**
     * The face of a coin takes the color of its kingdom; coins of a dissolved kingdom turn grey.
     */
    private static void onItemColors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> {
            if (tint != 1) {
                return 0xFFFFFFFF;
            }
            return io.github.sekerator.kingdomworld.common.economy.CoinItem.currency(stack)
                    .flatMap(io.github.sekerator.kingdomworld.common.economy.CurrencyDirectory::get)
                    .map(info -> info.old() ? 0xFF808080 : 0xFF000000 | info.color())
                    .orElse(0xFFA0A0A0);
        }, ModRegistries.COIN.get());
        // A monster crystal shines in the color of its rank.
        event.register((stack, tint) -> tint == 0 ? 0xFF000000 | io.github.sekerator.kingdomworld.common.guild.MonsterCrystalItem.color(stack)
                : 0xFFFFFFFF, io.github.sekerator.kingdomworld.common.guild.GuildRegistries.MONSTER_CRYSTAL.get());
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(MAP_KEY);
        event.register(PARTY_KEY);
        event.register(io.github.sekerator.kingdomworld.client.construction.ClientBlueprint.ROTATE_KEY);
        event.register(io.github.sekerator.kingdomworld.client.construction.ClientBlueprint.MIRROR_KEY);
    }

    /**
     * The hints of a blueprint in hand, above everything else.
     */
    private static void onRegisterOverlays(net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("blueprint", (gui, graphics, partialTick, width, height) ->
                io.github.sekerator.kingdomworld.client.construction.BlueprintRenderer.drawHud(graphics, Minecraft.getInstance().font, width));
        event.registerAboveAll("party", (gui, graphics, partialTick, width, height) -> PartyHud.draw(graphics, width, height));
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistries.CITIZEN.get(), CitizenRenderer::new);
        event.registerEntityRenderer(io.github.sekerator.kingdomworld.common.companion.CompanionRegistries.COMPANION.get(),
                io.github.sekerator.kingdomworld.client.render.CompanionRenderer::new);
        event.registerEntityRenderer(io.github.sekerator.kingdomworld.common.magic.MagicRegistries.SPELL_FIREBALL.get(),
                context -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(context, 0.75f, true));
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ClientSelfTest.onClientTick();
        io.github.sekerator.kingdomworld.client.construction.ClientBlueprint.tick();
        Minecraft minecraft = Minecraft.getInstance();
        io.github.sekerator.kingdomworld.client.map.ClientMapSync.tick(minecraft);
        while (MAP_KEY.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                ClientHooks.openKingdomMap();
            }
        }
        while (PARTY_KEY.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null && minecraft.getConnection() != null) {
                KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.CompanionPackets.PartyRequest());
            }
        }
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        io.github.sekerator.kingdomworld.common.economy.CurrencyDirectory.clear();
        ClientKingdomCache.clear();
        ClientTerritoryCache.clear();
        io.github.sekerator.kingdomworld.client.map.ClientTradeCache.clear();
        io.github.sekerator.kingdomworld.client.map.ClientMapSync.reset();
        if (KingdomWorldConfig.CLIENT.requestKingdomListOnJoin.get()) {
            KingdomWorldNetwork.sendToServer(new KingdomListRequestPacket(0, KingdomListRequestPacket.MAX_PAGE_SIZE));
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        io.github.sekerator.kingdomworld.client.construction.ClientBlueprint.clear();
        PartyHud.clear();
        io.github.sekerator.kingdomworld.common.economy.CurrencyDirectory.clear();
        ClientKingdomCache.clear();
        ClientTerritoryCache.clear();
        io.github.sekerator.kingdomworld.client.map.ClientTradeCache.clear();
        io.github.sekerator.kingdomworld.client.map.ClientMapSync.reset();
    }

    private static void onScreenOpening(ScreenEvent.Opening event) {
        if (!titleScreenSeen && event.getNewScreen() instanceof TitleScreen) {
            titleScreenSeen = true;
            LOGGER.debug("KingdomWorld client reached the title screen");
        }
        if (event.getNewScreen() instanceof TitleScreen) {
            ClientSelfTest.onTitleScreen();
        }
    }
}
