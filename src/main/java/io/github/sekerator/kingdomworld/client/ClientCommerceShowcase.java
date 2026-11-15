package io.github.sekerator.kingdomworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.client.screen.ShopScreen;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.commerce.CommerceSavedData;
import io.github.sekerator.kingdomworld.common.commerce.CommerceWork;
import io.github.sekerator.kingdomworld.common.commerce.ShopActions;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.common.economy.EconomyEvents;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.packet.EconomyPackets;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.road.CityAreas;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.commerce.Business;
import io.github.sekerator.kingdomworld.core.economy.CityPurses;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.road.City;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The money of the cities, the shops and the workshops of citizens, in the client self-check ({@code -PclientSelfTest=commerce}):
 * in a copy of a test world the player founds a kingdom with a counter beside the throne, a town east of it and goods in a
 * storehouse, lets a few days of the cities pass, then shows the views of the cities of the Treasury tab, the window of the
 * capital, the overview, the switch of the market, the window of the shop and its keeper behind the counter. Then a crafting
 * table and a furnace are put beside the counter, its shop becomes a bakery that bakes of wheat of the crown, the crown orders
 * bread no workshop of its own makes and buys it of the bakery, and the Orders tab, the view of imports, the window of the capital
 * with its trades and the window of the bakery are shown. The screenshots go into the folder {@code citizen-economy-2} of the
 * screenshot folder.
 */
final class ClientCommerceShowcase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "KingdomWorld client self-test: ";

    private final Path folder;
    private int ticks;
    private volatile BlockPos throne;
    private volatile BlockPos counter;
    private volatile BlockPos storehouse;
    private volatile UUID kingdomId;
    private volatile UUID keeperId;
    private volatile BlockPos folkPost;
    private volatile long worldShopId;
    private volatile String failure;
    private boolean finished;
    private boolean failed;

    ClientCommerceShowcase(Path folder) {
        this.folder = folder.resolve("citizen-economy-3");
    }

    boolean finished() {
        return finished;
    }

    boolean failed() {
        return failed;
    }

    void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) {
            return;
        }
        ticks++;
        if (failure != null) {
            LOGGER.error(PREFIX + "commerce showcase failed: {}", failure);
            failed = true;
            finished = true;
            return;
        }
        UUID playerId = minecraft.player.getUUID();
        switch (ticks) {
            case 40 -> server.execute(() -> prepare(server, playerId));
            case 60 -> server.execute(() -> days(server, 4));
            case 140 -> {
                if (throne == null) {
                    failure = "the kingdom was not prepared";
                    return;
                }
                minecraft.options.hideGui = false;
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        KingdomActions.useThrone(player, player.serverLevel(), throne);
                    }
                });
            }
            case 200 -> {
                if (minecraft.screen instanceof ThroneScreen screen) {
                    screen.selectTab("treasury");
                } else {
                    failure = "the throne screen did not open";
                }
            }
            case 240 -> {
                mouseAway(minecraft);
                show(minecraft, screen -> screen.showTreasury("overview", "today"));
            }
            case 270 -> shoot(minecraft, "treasury-overview-cities");
            case 280 -> show(minecraft, screen -> {
                screen.showTreasury("cities", "today");
                screen.showCities(false, false, "");
            });
            case 310 -> shoot(minecraft, "treasury-cities-treasury");
            case 320 -> show(minecraft, screen -> screen.showCities(true, false, ""));
            case 350 -> shoot(minecraft, "treasury-cities-crown");
            case 360 -> {
                show(minecraft, screen -> screen.showCities(false, false, "250"));
                if (kingdomId != null && minecraft.getConnection() != null) {
                    KingdomWorldNetwork.sendToServer(new EconomyPackets.CityDetailRequest(kingdomId, 0));
                }
            }
            case 410 -> shoot(minecraft, "treasury-city-panel");
            case 420 -> show(minecraft, screen -> screen.selectTab("market"));
            case 460 -> {
                mouseAway(minecraft);
                show(minecraft, screen -> screen.showMarket("settings", false, ItemStack.EMPTY, "", "", "", null));
            }
            case 490 -> shoot(minecraft, "market-sell-citizens");
            case 500 -> {
                minecraft.setScreen(null);
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null && counter != null) {
                        ShopActions.openAtCounter(player, counter);
                    }
                });
            }
            case 560 -> {
                mouseAway(minecraft);
                if (!(minecraft.screen instanceof ShopScreen)) {
                    failure = "the window of the shop did not open";
                }
            }
            case 580 -> shoot(minecraft, "shop-buy");
            case 590 -> {
                if (minecraft.screen instanceof ShopScreen shop) {
                    shop.showForCheck("about", true, "1");
                    shop.resize(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
                }
            }
            case 620 -> shoot(minecraft, "shop-about");
            case 630 -> {
                minecraft.setScreen(null);
                minecraft.options.hideGui = true;
                server.execute(() -> standBehindTheCounter(server, playerId));
            }
            case 720 -> shoot(minecraft, "keeper-at-counter");
            case 730 -> {
                minecraft.options.hideGui = false;
                server.execute(() -> {
                    bakery(server, playerId);
                    orderBread(server);
                });
            }
            case 760 -> server.execute(() -> {
                days(server, 2);
                io.github.sekerator.kingdomworld.common.commerce.CrownPurchases.step(server, kingdomId);
            });
            case 820 -> server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    KingdomActions.useThrone(player, player.serverLevel(), throne);
                }
            });
            case 870 -> {
                if (minecraft.screen instanceof ThroneScreen screen) {
                    screen.selectTab("orders");
                } else {
                    failure = "the throne screen did not open again";
                }
            }
            case 900 -> mouseAway(minecraft);
            case 920 -> shoot(minecraft, "orders-buy-citizens");
            case 930 -> show(minecraft, screen -> screen.selectTab("market"));
            case 960 -> {
                mouseAway(minecraft);
                show(minecraft, screen -> screen.showMarket("import", false, ItemStack.EMPTY, "", "", "", null));
            }
            case 990 -> shoot(minecraft, "market-import-citizens");
            case 1000 -> show(minecraft, screen -> screen.selectTab("treasury"));
            case 1030 -> {
                show(minecraft, screen -> {
                    screen.showTreasury("cities", "today");
                    screen.showCities(false, false, "");
                });
                if (kingdomId != null && minecraft.getConnection() != null) {
                    KingdomWorldNetwork.sendToServer(new EconomyPackets.CityDetailRequest(kingdomId, 0));
                }
            }
            case 1070 -> {
                mouseAway(minecraft);
                if (minecraft.screen instanceof ThroneScreen screen) {
                    // The lines of the shops of the capital, under the taxes and the means of its people.
                    for (int i = 0; i < 8; i++) {
                        screen.mouseScrolled(minecraft.getWindow().getGuiScaledWidth() / 2.0, minecraft.getWindow().getGuiScaledHeight() / 2.0, -1);
                    }
                }
            }
            case 1090 -> shoot(minecraft, "treasury-city-panel-trades");
            case 1100 -> {
                minecraft.setScreen(null);
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null && counter != null) {
                        player.teleportTo(server.overworld(), counter.getX() + 0.5, counter.getY() + 0.5, counter.getZ() + 3.5, 180.0f, 8.0f);
                        player.getInventory().add(new ItemStack(Items.WHEAT, 40));
                        player.getInventory().add(new ItemStack(Items.BREAD, 12));
                        ShopActions.openAtCounter(player, counter);
                    }
                });
            }
            case 1160 -> {
                mouseAway(minecraft);
                if (minecraft.screen instanceof ShopScreen shop) {
                    shop.showForCheck("sell", true, "10");
                    shop.resize(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
                } else {
                    failure = "the window of the bakery did not open";
                }
            }
            case 1180 -> shoot(minecraft, "workshop-sell");
            case 1190 -> {
                if (minecraft.screen instanceof ShopScreen shop) {
                    shop.showForCheck("about", true, "1");
                    shop.resize(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
                }
            }
            case 1210 -> shoot(minecraft, "workshop-about");
            case 1220 -> {
                if (minecraft.screen instanceof ShopScreen shop) {
                    shop.showForCheck("buy", true, "1");
                    shop.resize(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
                }
            }
            case 1240 -> shoot(minecraft, "workshop-buy");
            case 1250 -> {
                minecraft.setScreen(null);
                server.execute(() -> folk(server, playerId));
            }
            case 1280 -> server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null && throne != null) {
                    KingdomActions.useThrone(player, player.serverLevel(), throne);
                }
            });
            case 1330 -> show(minecraft, screen -> {
                screen.selectTab("roads");
                screen.showRoads("cities", 0, 0);
            });
            case 1360 -> {
                if (minecraft.screen instanceof ThroneScreen screen) {
                    // The capital hovered: where its townsfolk work.
                    int[] point = screen.roadsRowPoint(0);
                    mouseAt(minecraft, point[0], point[1]);
                }
            }
            case 1380 -> shoot(minecraft, "roads-city-folk-work");
            case 1390 -> {
                mouseAway(minecraft);
                show(minecraft, screen -> {
                    screen.selectTab("treasury");
                    screen.showTreasury("cities", "today");
                    screen.showCities(false, false, "");
                });
                if (kingdomId != null && minecraft.getConnection() != null) {
                    KingdomWorldNetwork.sendToServer(new EconomyPackets.CityDetailRequest(kingdomId, 0));
                }
            }
            case 1430 -> {
                mouseAway(minecraft);
                if (minecraft.screen instanceof ThroneScreen screen) {
                    // The lines of the townsfolk, under the means of the people of the capital.
                    for (int i = 0; i < 6; i++) {
                        screen.mouseScrolled(minecraft.getWindow().getGuiScaledWidth() / 2.0, minecraft.getWindow().getGuiScaledHeight() / 2.0, -1);
                    }
                }
            }
            case 1450 -> shoot(minecraft, "treasury-city-panel-folk");
            case 1460 -> {
                minecraft.setScreen(null);
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null && folkPost != null) {
                        io.github.sekerator.kingdomworld.common.citizen.work.WorkPostActions.open(player, folkPost);
                    }
                });
            }
            case 1500 -> mouseAway(minecraft);
            case 1520 -> shoot(minecraft, "post-folk");
            case 1530 -> {
                minecraft.setScreen(null);
                server.execute(() -> worldShop(server, playerId));
            }
            case 1700 -> server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null && worldShopId != 0) {
                    ShopActions.openShop(player, worldShopId);
                }
            });
            case 1740 -> {
                mouseAway(minecraft);
                if (!(minecraft.screen instanceof ShopScreen)) {
                    failure = "the window of the shop of the world did not open";
                }
            }
            case 1760 -> shoot(minecraft, "shop-buy-world");
            case 1770 -> {
                minecraft.setScreen(null);
            }
            case 1790 -> shoot(minecraft, "world-shop-keeper");
            case 1800 -> {
                LOGGER.info(PREFIX + "commerce showcase finished");
                finished = true;
            }
            default -> {
            }
        }
    }

    private interface ScreenStep {
        void apply(ThroneScreen screen);
    }

    private void show(Minecraft minecraft, ScreenStep step) {
        if (minecraft.screen instanceof ThroneScreen screen) {
            step.apply(screen);
            screen.resize(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        } else {
            failure = "the throne screen is gone";
        }
    }

    /**
     * Moves the mouse into the corner of the window, so that no hover covers what the screenshot shows.
     */
    private static void mouseAway(Minecraft minecraft) {
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(minecraft.getWindow().getWindow(), 1, 1);
        try {
            var xpos = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            var ypos = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            xpos.setAccessible(true);
            ypos.setAccessible(true);
            xpos.setDouble(minecraft.mouseHandler, 1);
            ypos.setDouble(minecraft.mouseHandler, 1);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn(PREFIX + "commerce showcase: the mouse could not be moved: {}", e.toString());
        }
    }

    /**
     * Puts the mouse on a point of the screen, in the scaled coordinates of the interface.
     */
    private static void mouseAt(Minecraft minecraft, int x, int y) {
        double scale = minecraft.getWindow().getGuiScale();
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(minecraft.getWindow().getWindow(), x * scale, y * scale);
        try {
            var xpos = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            var ypos = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            xpos.setAccessible(true);
            ypos.setAccessible(true);
            xpos.setDouble(minecraft.mouseHandler, x * scale);
            ypos.setDouble(minecraft.mouseHandler, y * scale);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn(PREFIX + "commerce showcase: the mouse could not be moved: {}", e.toString());
        }
    }

    /**
     * The capital full of townsfolk: 280 of them, 90 hands of its workshops and 50 idle, what they earned a head, and a farm with
     * townsfolk on its seats beside the throne.
     */
    private void folk(IntegratedServer server, UUID playerId) {
        try {
            if (kingdomId == null || throne == null) {
                failure = "no kingdom for the townsfolk";
                return;
            }
            ServerLevel level = server.overworld();
            var charter = RoadWork.state(server).charterOf(kingdomId, 0, io.github.sekerator.kingdomworld.core.road.CityCharter.NEVER);
            charter.setFolk(280);
            charter.setFolkWork(90, 50);
            charter.setFolkEarned(280L * 4);
            RoadWork.state(server).charterChanged();
            BlockPos at = surface(level, throne.getX() + 3, throne.getZ() - 4);
            level.setBlockAndUpdate(at, ModRegistries.WORK_POST.get().defaultBlockState());
            WorldPos pos = new WorldPos("minecraft:overworld", at.getX(), at.getY(), at.getZ());
            KingdomWorldApi.change(server, "showcase", state -> state.setPostProfession(null, pos, "farmer"));
            KingdomWorldApi.change(server, "showcase", state -> state.setPostSeats(null, pos, 16, 16));
            KingdomWorldApi.state(server).citizens().posts().get(pos).ifPresent(post -> {
                post.rememberFarm(48, "minecraft:wheat");
                post.setFolkWorkers(12);
            });
            folkPost = at;
            LOGGER.info(PREFIX + "commerce showcase: 280 townsfolk in the capital, a farm with townsfolk at {}", at.toShortString());
        } catch (RuntimeException e) {
            failure = e.toString();
            LOGGER.error(PREFIX + "commerce showcase could not settle the townsfolk", e);
        }
    }

    /**
     * A shop of citizens in a capital of the world: of the first kingdom of the world with a capital, in a house of a merchant
     * where it has one, with bread of its ruler; the player stands in front of it.
     */
    private void worldShop(IntegratedServer server, UUID playerId) {
        try {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = server.overworld();
            for (io.github.sekerator.kingdomworld.core.npc.NpcKingdom npc : io.github.sekerator.kingdomworld.common.npc.NpcWork.state(server).kingdoms()) {
                UUID id = npc.kingdomId();
                var capital = io.github.sekerator.kingdomworld.common.road.CityWalls.place(server, id, 0);
                if (capital.isEmpty() || !capital.get().dimension().equals("minecraft:overworld")) {
                    continue;
                }
                List<io.github.sekerator.kingdomworld.common.commerce.BusinessPlaces.Spot> spots = io.github.sekerator.kingdomworld.common.commerce
                        .BusinessPlaces.of(server, id, 0);
                Business.Place place = spots.stream().map(io.github.sekerator.kingdomworld.common.commerce.BusinessPlaces.Spot::place)
                        .filter(found -> found.kind() == Business.PlaceKind.MERCHANT_HOUSE).findFirst()
                        .orElse(new Business.Place(Business.PlaceKind.MERCHANT_HOUSE, 0, 0, "minecraft:overworld", capital.get().x() + 4,
                                capital.get().y(), capital.get().z() + 4));
                long day = TreasuryActions.day(server);
                Business shop = CommerceSavedData.get(server).shopsOf(id, 0).stream().findFirst()
                        .orElseGet(() -> CommerceSavedData.get(server).open(id, 0, place, day, 200));
                shop.changeTrade(Business.GENERAL_STORE, day);
                if (!shop.kept()) {
                    shop.setKeepers(List.of(), true);
                    shop.setStatus(Business.Status.FAMILY);
                }
                shop.addGoods("minecraft:bread", io.github.sekerator.kingdomworld.core.commerce.GoodsCategory.FOOD.ordinal(), 24, 3_000);
                shop.addGoods("minecraft:torch", io.github.sekerator.kingdomworld.core.commerce.GoodsCategory.HOUSEHOLD.ordinal(), 32, 1_000);
                CommerceSavedData.get(server).setDirty();
                worldShopId = shop.id();
                if (player != null) {
                    // Above the place of the shop, looking down at the capital around it.
                    BlockPos front = surface(level, shop.place().x(), shop.place().z() + 10);
                    player.teleportTo(level, front.getX() + 0.5, front.getY() + 12, front.getZ() + 0.5, 180.0f, 35.0f);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                }
                LOGGER.info(PREFIX + "commerce showcase: a shop of citizens of {} at {} {} {}", npc.name(), shop.place().x(), shop.place().y(),
                        shop.place().z());
                return;
            }
            // A world without kingdoms of the world: one is founded south of the capital of the player and made one.
            if (throne == null || player == null) {
                failure = "no kingdom of the world with a capital";
                return;
            }
            BlockPos seat = surface(level, throne.getX(), throne.getZ() + 128);
            level.setBlockAndUpdate(seat, ModRegistries.THRONE.get().defaultBlockState());
            CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, seat, "Белозерское княжество", 0x8E3B2E, UUID.randomUUID());
            if (!(result instanceof CreateKingdomResult.Created created)) {
                failure = "the kingdom of the world was not founded: " + result;
                return;
            }
            UUID id = created.kingdom().id();
            long day = TreasuryActions.day(server);
            io.github.sekerator.kingdomworld.common.npc.NpcWork.state(server).putKingdom(new io.github.sekerator.kingdomworld.core.npc.NpcKingdom(
                    id, -1, io.github.sekerator.kingdomworld.core.npc.NpcLevel.SMALL,
                    new io.github.sekerator.kingdomworld.core.npc.NpcNames.Name("Belozersk", "Белозерское княжество"),
                    io.github.sekerator.kingdomworld.core.npc.RulerCharacter.MERCHANT,
                    List.of(io.github.sekerator.kingdomworld.core.npc.RulerCharacter.Trait.PATIENT), 4321,
                    io.github.sekerator.kingdomworld.core.citizen.Gender.FEMALE, List.of(), KingdomWorldApi.gameTime(server), day));
            Citizen keeper = CitizenLife.arrive(server, id, new WorldPos("minecraft:overworld", seat.getX(), seat.getY(), seat.getZ()), false)
                    .orElse(null);
            Business.Place place = new Business.Place(Business.PlaceKind.MERCHANT_HOUSE, 0, 0, "minecraft:overworld", seat.getX() + 3, seat.getY(),
                    seat.getZ() + 3);
            Business shop = CommerceSavedData.get(server).open(id, 0, place, day, 200);
            shop.changeTrade(Business.GENERAL_STORE, day);
            if (keeper != null) {
                shop.setKeepers(List.of(keeper.id()), false);
                keeper.setBusiness(shop.id());
                shop.setStatus(Business.Status.TRADING);
            } else {
                shop.setKeepers(List.of(), true);
                shop.setStatus(Business.Status.FAMILY);
            }
            shop.addGoods("minecraft:bread", io.github.sekerator.kingdomworld.core.commerce.GoodsCategory.FOOD.ordinal(), 24, 3_000);
            shop.addGoods("minecraft:torch", io.github.sekerator.kingdomworld.core.commerce.GoodsCategory.HOUSEHOLD.ordinal(), 32, 1_000);
            CommerceSavedData.get(server).setDirty();
            worldShopId = shop.id();
            BlockPos front = surface(level, seat.getX() + 3, seat.getZ() + 6);
            player.teleportTo(level, front.getX() + 0.5, front.getY(), front.getZ() + 0.5, 180.0f, 10.0f);
            LOGGER.info(PREFIX + "commerce showcase: a kingdom of the world founded at {} with a shop of citizens", seat.toShortString());
        } catch (RuntimeException e) {
            failure = e.toString();
            LOGGER.error(PREFIX + "commerce showcase could not open a shop of the world", e);
        }
    }

    private void shoot(Minecraft minecraft, String name) {
        Path file = folder.resolve(name + ".png");
        try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            Files.createDirectories(file.getParent());
            image.writeToFile(file);
            LOGGER.info(PREFIX + "screenshot of {} saved to {}", name, file);
        } catch (IOException e) {
            LOGGER.error(PREFIX + "could not save the screenshot of {}", name, e);
            failed = true;
        }
    }

    /**
     * Founds the kingdom of the player: a throne, a counter beside it, beds and citizens, goods in a storehouse, money in the
     * treasury and a town east of the capital.
     */
    private void prepare(IntegratedServer server, UUID playerId) {
        try {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = server.overworld();
            level.setDayTime(6000);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            level.setWeatherParameters(12_000, 0, false, false);
            player.setGameMode(GameType.CREATIVE);
            KingdomWorldApi.kingdomOf(server, playerId).ifPresent(old -> KingdomWorldApi.dissolveKingdom(server, old.id()));
            BlockPos at = player.blockPosition();
            BlockPos seat = surface(level, at.getX() + 3, at.getZ());
            level.setBlockAndUpdate(seat, ModRegistries.THRONE.get().defaultBlockState());
            CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, seat, "Ольховский край", 0x2E7DA0, playerId);
            if (!(result instanceof CreateKingdomResult.Created created)) {
                failure = "the kingdom was not founded: " + result;
                return;
            }
            Kingdom kingdom = created.kingdom();
            kingdomId = kingdom.id();
            var state = KingdomWorldApi.state(server);
            state.economy().adminTreasury(kingdom.id(), 50_000, TreasuryActions.day(server));
            // A storehouse full of goods of every kind.
            BlockPos store = surface(level, seat.getX() - 4, seat.getZ() - 3);
            level.setBlockAndUpdate(store, ModRegistries.STOREHOUSE.get().defaultBlockState());
            storehouse = store;
            StorehouseInventory inventory = KingdomSavedData.get(server).storehouses()
                    .get(new WorldPos("minecraft:overworld", store.getX(), store.getY(), store.getZ()))
                    .orElseThrow(() -> new IllegalStateException("The storehouse has no inventory"));
            ItemStack[] goods = {new ItemStack(Items.BREAD, 64), new ItemStack(Items.COOKED_BEEF, 64), new ItemStack(Items.BAKED_POTATO, 64),
                    new ItemStack(Items.LEATHER, 64), new ItemStack(Items.WHITE_WOOL, 64), new ItemStack(Items.TORCH, 64),
                    new ItemStack(Items.BOOK, 32), new ItemStack(Items.LANTERN, 32), new ItemStack(Items.PAINTING, 16),
                    new ItemStack(Items.CLOCK, 4)};
            for (int i = 0; i < goods.length; i++) {
                inventory.insertItem(i, goods[i], false);
            }
            for (int i = 0; i < 8; i++) {
                inventory.insertItem(goods.length + i, new ItemStack(i % 2 == 0 ? Items.STONE_SHOVEL : Items.SHEARS), false);
            }
            // A counter in front of the throne, and the beds and citizens of the capital: one keeper, the others at work.
            counter = surface(level, seat.getX() - 2, seat.getZ() + 2);
            level.setBlockAndUpdate(counter, ModRegistries.SHOP_COUNTER.get().defaultBlockState());
            CommerceSavedData.get(server).putCounter(new CommerceSavedData.Counter(kingdom.id(), "minecraft:overworld", counter.getX(),
                    counter.getY(), counter.getZ()));
            List<WorldPos> beds = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                beds.add(bed(level, kingdom, seat.getX() - 6 + 2 * i, seat.getZ() + 7));
            }
            List<Citizen> citizens = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                CitizenLife.arrive(server, kingdom.id(), new WorldPos("minecraft:overworld", seat.getX(), seat.getY(), seat.getZ()), false)
                        .ifPresent(citizens::add);
            }
            for (int i = 0; i < citizens.size(); i++) {
                state.citizens().assignHome(citizens.get(i), beds.get(i));
                if (i > 0) {
                    // Workers of the crown earn wages the townspeople spend.
                    state.citizens().setProfession(citizens.get(i), i % 2 == 0 ? "lumberjack" : "farmer");
                }
            }
            if (!citizens.isEmpty()) {
                keeperId = citizens.get(0).id();
            }
            // A town east of the capital, on land of the kingdom, with a grant of the crown.
            ChunkPos home = new ChunkPos(seat);
            for (int dx = 2; dx <= 4; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    KingdomWorldApi.claimChunk(level, new ChunkPos(home.x + dx, home.z + dz), kingdom.id(), null);
                }
            }
            BlockPos hall = surface(level, seat.getX() + 44, seat.getZ());
            City town = RoadWork.state(server).addCity(kingdom.id(), "Каменка", "minecraft:overworld", hall.getX(), hall.getY(), hall.getZ(),
                    KingdomWorldApi.gameTime(server));
            CityAreas.invalidate();
            state.economy().cityGrant(kingdom.id(), town.id(), 1_500, TreasuryActions.day(server));
            throne = seat;
            player.teleportTo(level, at.getX() + 0.5, seat.getY(), at.getZ() + 0.5, -90.0f, 5.0f);
            LOGGER.info(PREFIX + "commerce showcase: kingdom at {}, counter at {}, {} citizen(s)", seat.toShortString(), counter.toShortString(),
                    citizens.size());
        } catch (RuntimeException e) {
            failure = e.toString();
            LOGGER.error(PREFIX + "commerce showcase could not prepare", e);
        }
    }

    /**
     * Lets days of the kingdom pass: the game time moves a day, the payday is held and the cities live their days.
     */
    private void days(IntegratedServer server, int count) {
        try {
            if (kingdomId == null) {
                return;
            }
            ServerLevel level = server.overworld();
            Kingdom kingdom = KingdomWorldApi.kingdom(server, kingdomId).orElseThrow();
            var economy = KingdomWorldApi.state(server).economy();
            for (int i = 0; i < count; i++) {
                ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(level.getGameTime() + 24_000L);
                long day = TreasuryActions.day(server);
                economy.treasuryToPurse(CityPurses.PurseKey.cityPurse(kingdomId, 0), 120, LedgerCategory.TRANSFERS, day);
                EconomyEvents.payday(server, kingdom, day);
                CommerceWork.drain(server);
            }
            List<Business> shops = CommerceSavedData.get(server).shopsOf(kingdomId, 0);
            LOGGER.info(PREFIX + "commerce showcase: {} shop(s) in the capital after the days", shops.size());
        } catch (RuntimeException e) {
            failure = e.toString();
            LOGGER.error(PREFIX + "commerce showcase could not let the days pass", e);
        }
    }

    /**
     * A crafting table and a furnace beside the counter, looked at; the shop of the counter takes up baking, fixed, and the
     * storehouse gets wheat for it.
     */
    private void bakery(IntegratedServer server, UUID playerId) {
        try {
            if (counter == null || kingdomId == null) {
                failure = "no counter for the bakery";
                return;
            }
            ServerLevel level = server.overworld();
            BlockPos table = surface(level, counter.getX() + 2, counter.getZ());
            level.setBlockAndUpdate(table, Blocks.CRAFTING_TABLE.defaultBlockState());
            BlockPos oven = surface(level, counter.getX() + 3, counter.getZ());
            level.setBlockAndUpdate(oven, Blocks.FURNACE.defaultBlockState());
            long day = TreasuryActions.day(server);
            // The place was looked at today before the workstations stood: it is looked at again now, as on the next day.
            String place = "counter/minecraft:overworld/" + counter.getX() + "/" + counter.getY() + "/" + counter.getZ();
            CommerceSavedData.get(server).putSurvey(place, new CommerceSavedData.Survey(java.util.Set.of(), day - 1));
            io.github.sekerator.kingdomworld.common.citizen.CitizenSites.scanChunk(level, level.getChunkAt(counter));
            for (Business shop : CommerceSavedData.get(server).shopsOf(kingdomId, 0)) {
                if (shop.place().kind() == Business.PlaceKind.COUNTER) {
                    shop.setPinned("bakery");
                    shop.changeTrade("bakery", day);
                }
            }
            CommerceSavedData.get(server).setDirty();
            KingdomSavedData.get(server).storehouses().get(new WorldPos("minecraft:overworld", storehouse.getX(), storehouse.getY(), storehouse.getZ()))
                    .ifPresent(inventory -> {
                        int added = 0;
                        for (int slot = 0; slot < inventory.getSlots(); slot++) {
                            if (inventory.getStackInSlot(slot).isEmpty() && added++ < 4) {
                                inventory.insertItem(slot, new ItemStack(Items.WHEAT, 64), false);
                            }
                        }
                    });
            LOGGER.info(PREFIX + "commerce showcase: the counter is a bakery, its place has {}", io.github.sekerator.kingdomworld.common.commerce
                    .PlaceSurvey.stations(server, "counter/minecraft:overworld/" + counter.getX() + "/" + counter.getY() + "/" + counter.getZ()));
        } catch (RuntimeException e) {
            failure = e.toString();
            LOGGER.error(PREFIX + "commerce showcase could not set up the bakery", e);
        }
    }

    /**
     * The crown orders bread, which no workshop of its own bakes: with none ready, it pays the bakery in advance for it.
     */
    private void orderBread(IntegratedServer server) {
        try {
            if (kingdomId == null) {
                return;
            }
            var state = KingdomWorldApi.state(server);
            state.order(null, kingdomId, "minecraft:bread", io.github.sekerator.kingdomworld.core.production.Order.Kind.MAKE, 40,
                    io.github.sekerator.kingdomworld.core.production.Order.Priority.NORMAL, KingdomWorldApi.gameTime(server), Integer.MAX_VALUE,
                    id -> true);
            io.github.sekerator.kingdomworld.common.production.Production.plan(server, kingdomId);
            io.github.sekerator.kingdomworld.common.commerce.CrownPurchases.step(server, kingdomId);
        } catch (RuntimeException e) {
            failure = e.toString();
            LOGGER.error(PREFIX + "commerce showcase could not order bread", e);
        }
    }

    /**
     * The keeper stands behind its counter, the player in front of it looking at both.
     */
    private void standBehindTheCounter(IntegratedServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        ServerLevel level = server.overworld();
        if (player == null || counter == null) {
            return;
        }
        // The keeper of the shop at the counter, chosen by the city: the townsperson nearest to it.
        UUID keeper = kingdomId == null ? keeperId : CommerceSavedData.get(server).shopsOf(kingdomId, 0).stream()
                .filter(shop -> !shop.keepers().isEmpty()).map(shop -> shop.keepers().get(0)).findFirst().orElse(keeperId);
        BlockPos behind = counter.north();
        int aside = 0;
        for (CitizenEntity body : level.getEntitiesOfClass(CitizenEntity.class, new AABB(counter).inflate(64))) {
            body.getNavigation().stop();
            if (keeper != null && keeper.equals(body.citizenId())) {
                body.teleportTo(behind.getX() + 0.5, behind.getY(), behind.getZ() + 0.5);
                body.setYRot(0.0f);
                body.setYHeadRot(0.0f);
            } else {
                // The others stand aside, so that nobody hides the keeper behind its counter.
                BlockPos away = surface(level, counter.getX() + 6 + 2 * aside++, counter.getZ() - 4);
                body.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
            }
        }
        player.teleportTo(level, counter.getX() + 0.5, counter.getY() + 0.5, counter.getZ() + 3.5, 180.0f, 8.0f);
    }

    private static WorldPos bed(ServerLevel level, Kingdom kingdom, int x, int z) {
        BlockPos head = surface(level, x, z);
        BlockState bed = Blocks.BLUE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        level.setBlockAndUpdate(head.south(), bed.setValue(BedBlock.PART, BedPart.FOOT));
        level.setBlockAndUpdate(head, bed.setValue(BedBlock.PART, BedPart.HEAD));
        WorldPos pos = new WorldPos("minecraft:overworld", head.getX(), head.getY(), head.getZ());
        KingdomWorldApi.state(level.getServer()).citizens().addSite(pos, SiteKinds.BED, kingdom.id());
        return pos;
    }

    private static BlockPos surface(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }
}
