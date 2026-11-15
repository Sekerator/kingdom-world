package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.CoinStacks;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.kingdom.ThroneMoving;
import io.github.sekerator.kingdomworld.common.network.packet.EconomyPackets;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomActionPacket;
import io.github.sekerator.kingdomworld.common.road.RoadActions;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.economy.Coins;
import io.github.sekerator.kingdomworld.core.economy.Denomination;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Game tests of walls and gates: the window of the own kingdom opens from afar while coins and the throne to move are handed
 * out at the throne only; a gate is cut into the wall the player stands by, whatever city the window chose.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WallGameTests {
    private static final String EMPTY = "empty";

    private WallGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    static ServerPlayer player(ServerLevel level, UUID id, String name, BlockPos at) {
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(id, name));
        player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return player;
    }

    static void join(MinecraftServer server, Kingdom kingdom, UUID player) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        state.membership().invite(kingdom.rulerId(), player, 1L);
        state.membership().acceptInvitation(player, kingdom.id(), 1L);
    }

    /**
     * A square ring of blocks around a place at a height, going round.
     */
    static List<RouteCell> ring(int centerX, int centerZ, int radius, int y) {
        List<RouteCell> cells = new ArrayList<>();
        for (int x = centerX - radius; x < centerX + radius; x++) {
            cells.add(new RouteCell(x, y, centerZ - radius, RouteCell.Kind.GROUND, 0, null));
        }
        for (int z = centerZ - radius; z < centerZ + radius; z++) {
            cells.add(new RouteCell(centerX + radius, y, z, RouteCell.Kind.GROUND, 0, null));
        }
        for (int x = centerX + radius; x > centerX - radius; x--) {
            cells.add(new RouteCell(x, y, centerZ + radius, RouteCell.Kind.GROUND, 0, null));
        }
        for (int z = centerZ + radius; z > centerZ - radius; z--) {
            cells.add(new RouteCell(centerX - radius, y, z, RouteCell.Kind.GROUND, 0, null));
        }
        return cells;
    }

    static String key(Component message) {
        return message.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : message.getString();
    }

    private static boolean carriesMoveThrone(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (ThroneMoving.binding(player.getInventory().getItem(slot)).isPresent()) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- the window from afar

    @GameTest(template = EMPTY, batch = "kingdomworld_walls_window")
    public static void theWindowOpensFromAfarButCoinsAreTakenAtTheThrone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Far Window", kingId);
        BlockPos throne = helper.absolutePos(new BlockPos(2, 0, 2));
        ServerPlayer king = player(level, kingId, "kw-king", throne.offset(60, 1, 0));
        ServerPlayer stranger = player(level, UUID.randomUUID(), "kw-stranger", throne.offset(60, 1, 0));
        try {
            helper.assertTrue(KingdomActions.openOwn(king).success(), "The king could not open the window 60 blocks from the throne");
            KingdomActions.Feedback none = KingdomActions.openOwn(stranger);
            helper.assertTrue(!none.success() && key(none.message()).equals("kingdomworld.error.not_in_kingdom"),
                    "A player of no kingdom was answered " + none.message().getString());
            helper.assertTrue(!KingdomActions.atThrone(king, kingdom.id()), "60 blocks off counts as at the throne");

            KingdomWorldApi.state(server).economy().mint(kingdom.id(), Denomination.GOLD, 5, true, TreasuryActions.day(server));
            EconomyPackets.TreasuryChange take = new EconomyPackets.TreasuryChange(kingdom.id(), EconomyPackets.TreasuryChange.Action.TAKE_COINS,
                    "gold", 3);
            TreasuryActions.handle(king, take);
            helper.assertTrue(CoinStacks.count(king.getInventory(), kingdom.id()).equals(new Coins(0, 0, 0)), "Coins were taken from afar");
            helper.assertTrue(KingdomWorldApi.treasury(server, kingdom.id()).orElseThrow().vault().gold() == 5, "The vault gave coins from afar");
            KingdomActions.handleAction(king, new KingdomActionPacket(KingdomActionPacket.Action.MOVE_THRONE, null, 0));
            helper.assertTrue(!carriesMoveThrone(king), "The throne to move was handed out from afar");

            // At the throne both work.
            king.setPos(throne.getX() + 3.5, throne.getY() + 1, throne.getZ() + 0.5);
            helper.assertTrue(KingdomActions.atThrone(king, kingdom.id()), "3 blocks off is not at the throne");
            TreasuryActions.handle(king, take);
            helper.assertTrue(CoinStacks.count(king.getInventory(), kingdom.id()).equals(new Coins(0, 0, 3)), "The coins did not reach the hands");
            helper.assertTrue(KingdomWorldApi.treasury(server, kingdom.id()).orElseThrow().vault().gold() == 2, "The vault still holds them");
            KingdomActions.handleAction(king, new KingdomActionPacket(KingdomActionPacket.Action.MOVE_THRONE, null, 0));
            helper.assertTrue(carriesMoveThrone(king), "The king at the throne got no throne to move");
            // Calling the move off works from afar.
            king.setPos(throne.getX() + 60.5, throne.getY() + 1, throne.getZ() + 0.5);
            KingdomActions.handleAction(king, new KingdomActionPacket(KingdomActionPacket.Action.CANCEL_THRONE_MOVE, null, 0));
            helper.assertTrue(KingdomWorldApi.state(server).throneMoves().pending(kingdom.id()).isEmpty(), "The move was not called off from afar");
        } finally {
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- a gate where the player stands

    @GameTest(template = EMPTY, batch = "kingdomworld_walls_gate_here")
    public static void aGateIsCutWhereThePlayerStands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Gatehouse", kingId);
        BlockPos throne = helper.absolutePos(new BlockPos(2, 0, 2));
        int y = throne.getY();
        String dimension = Dimensions.id(level);
        try {
            City city = RoadWork.state(server).addCity(kingdom.id(), "GameTest Gate Town", dimension, throne.getX() + 40, y,
                    throne.getZ(), 1);
            Wall[] walls = new Wall[2];
            RoadWork.change(server, "test walls", state -> {
                walls[0] = new Wall(state.nextWallId(), 0, kingdom.id(), Wall.WallClass.PALISADE, dimension, throne.getX(), throne.getZ(),
                        ring(throne.getX(), throne.getZ(), 8, y), List.of(), 0);
                state.putWall(walls[0]);
                walls[1] = new Wall(state.nextWallId(), city.id(), kingdom.id(), Wall.WallClass.PALISADE, dimension, city.x(), city.z(),
                        ring(city.x(), city.z(), 6, y), List.of(), 0);
                state.putWall(walls[1]);
                return io.github.sekerator.kingdomworld.core.kingdom.Outcome.DONE;
            });
            Wall capitalWall = walls[0];
            Wall cityWall = walls[1];
            // Three blocks outside the west side of the city, with the capital chosen in the window.
            ServerPlayer king = player(level, kingId, "kw-king", new BlockPos(city.x() - 9, y + 1, city.z()));
            KingdomActions.Feedback cut = RoadActions.gateHere(king, kingdom.id(), 0);
            helper.assertTrue(cut.success() && key(cut.message()).equals("kingdomworld.screen.roads.gate_cut")
                    && cut.message().getString().contains("GameTest Gate Town"), "No gate in the wall of the city: " + cut.message().getString());
            helper.assertTrue(cityWall.gates().size() == 1 && capitalWall.gates().isEmpty(), "The gate went into the wrong wall");
            RouteCell gate = cityWall.gateCells().get(0);
            helper.assertTrue(gate.x() == city.x() - 6 && gate.z() == city.z(), "The gate is not where the player stands: " + gate);
            // There already.
            KingdomActions.Feedback again = RoadActions.gateHere(king, kingdom.id(), city.id());
            helper.assertTrue(!again.success() && key(again.message()).equals("kingdomworld.screen.roads.gate_exists"),
                    "A second gate at the same place: " + again.message().getString());
            // Too far.
            king.setPos(city.x() - 16 + 0.5, y + 1, city.z() + 0.5);
            KingdomActions.Feedback far = RoadActions.gateHere(king, kingdom.id(), city.id());
            helper.assertTrue(!far.success() && key(far.message()).equals("kingdomworld.screen.roads.gate_far")
                    && far.message().getString().contains("10"), "Ten blocks off was answered " + far.message().getString());
            helper.assertTrue(cityWall.gates().size() == 1 && capitalWall.gates().isEmpty(), "A gate was cut from afar");
            // The same place in another dimension: no wall there.
            ServerLevel nether = server.getLevel(Level.NETHER);
            if (nether != null) {
                ServerPlayer below = player(nether, kingId, "kw-king", new BlockPos(city.x() - 9, y + 1, city.z() + 3));
                KingdomActions.Feedback none = RoadActions.gateHere(below, kingdom.id(), city.id());
                helper.assertTrue(!none.success() && key(none.message()).equals("kingdomworld.screen.roads.gate_no_wall"),
                        "A gate was cut from the Nether: " + none.message().getString());
                helper.assertTrue(cityWall.gates().size() == 1, "The overworld wall got a gate from the Nether");
            }
            // A subject without the right to run construction.
            ServerPlayer subject = player(level, UUID.randomUUID(), "kw-page", new BlockPos(city.x() + 9, y + 1, city.z()));
            join(server, kingdom, subject.getUUID());
            KingdomActions.Feedback refused = RoadActions.gateHere(subject, kingdom.id(), city.id());
            helper.assertTrue(!refused.success() && key(refused.message()).equals("kingdomworld.error.no_permission"),
                    "A subject cut a gate: " + refused.message().getString());
            helper.assertTrue(cityWall.gates().size() == 1, "The subject's gate was cut");
        } finally {
            RoadWork.state(server).forgetKingdom(kingdom.id());
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }
}
