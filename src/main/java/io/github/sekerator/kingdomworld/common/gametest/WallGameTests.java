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
import io.github.sekerator.kingdomworld.common.road.CityWalls;
import io.github.sekerator.kingdomworld.common.road.RoadSettings;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadRules;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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

    // ---------------------------------------------------------------- walls in place of walls, through trees, over holes, at a height

    /** The middle of the city of the tests of building walls, relative. */
    private static final BlockPos MIDDLE = new BlockPos(6, 0, 6);

    /**
     * Clears the land of a test of building walls and lays grass from 0 to 12 both ways at relative height 0, with stone under it;
     * walls of radius 4 around the middle.
     */
    private static void land(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GatheringGameTests.clear(level, helper.absolutePos(new BlockPos(-1, -3, -1)), helper.absolutePos(new BlockPos(13, 8, 13)));
        GatheringGameTests.clear(level, helper.absolutePos(new BlockPos(1, 9, 1)), new BlockPos(helper.absolutePos(new BlockPos(11, 0, 11)).getX(),
                level.getMaxBuildHeight() - 1, helper.absolutePos(new BlockPos(11, 0, 11)).getZ()));
        for (int x = 0; x <= 12; x++) {
            for (int z = 0; z <= 12; z++) {
                for (int y = -3; y < 0; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
            }
        }
        RoadRules.Builder rules = RoadSettings.configuredRoads();
        rules.wallRadius = 4;
        rules.townPeople = 50;
        rules.cityPeople = 100;
        RoadSettings.override(rules.build(), null);
    }

    private static City city(GameTestHelper helper, Kingdom kingdom) {
        BlockPos middle = helper.absolutePos(MIDDLE);
        return RoadWork.state(helper.getLevel().getServer()).addCity(kingdom.id(), "GameTest Wall Town", Dimensions.id(helper.getLevel()),
                middle.getX(), middle.getY(), middle.getZ(), 1);
    }

    private static Wall build(GameTestHelper helper, Kingdom kingdom, City city, Wall.WallClass wallClass, int baseY) {
        MinecraftServer server = helper.getLevel().getServer();
        Wall[] made = new Wall[1];
        Outcome laid = CityWalls.build(server, null, kingdom.id(), city.id(), wallClass, made, 0, baseY);
        helper.assertTrue(laid.ok(), "The wall was not laid out: " + laid.error());
        return made[0];
    }

    /**
     * Pays every column of a wall from the storehouses and puts it into the world.
     */
    private static void payAndPlace(GameTestHelper helper, Wall wall) {
        MinecraftServer server = helper.getLevel().getServer();
        for (Road.Section section : wall.sections()) {
            boolean paid = true;
            while (!section.done() && paid) {
                paid = CityWalls.payColumn(server, wall, section);
            }
        }
        helper.assertTrue(wall.allPaid(), "The wall was not paid for: " + wall.paid() + " of " + wall.blocks().size());
        CityWalls.placeNow(server, wall);
    }

    private static boolean logAt(ServerLevel level, int x, int y, int z) {
        return level.getBlockState(new BlockPos(x, y, z)).is(BlockTags.LOGS);
    }

    private static long stored(MinecraftServer server, Kingdom kingdom, Item item) {
        return KingdomStorage.count(server, kingdom.id(), stack -> stack.is(item));
    }

    private static int count(GameTestHelper helper, Block block) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(1, 1, 1)), helper.absolutePos(new BlockPos(11, 8, 11)))) {
            if (helper.getLevel().getBlockState(pos).is(block)) {
                count++;
            }
        }
        return count;
    }

    private static void finish(GameTestHelper helper, Kingdom kingdom) {
        RoadSettings.override(null, null);
        RoadWork.state(helper.getLevel().getServer()).forgetKingdom(kingdom.id());
        CitizenGameTests.finish(helper, kingdom);
        GatheringGameTests.clear(helper.getLevel(), helper.absolutePos(new BlockPos(-1, -3, -1)), helper.absolutePos(new BlockPos(13, 8, 13)));
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_walls_swap", timeoutTicks = 200)
    public static void aPalisadeTakesThePlaceOfAStoneWall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        land(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Wall Swappers", UUID.randomUUID());
        try {
            StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 12);
            for (int slot = 0; slot < 4; slot++) {
                store.insertItem(slot, new ItemStack(Items.COBBLESTONE, 64), false);
            }
            store.insertItem(4, new ItemStack(Items.OAK_LOG, 64), false);
            store.insertItem(5, new ItemStack(Items.OAK_LOG, 64), false);
            City city = city(helper, kingdom);
            Wall stone = build(helper, kingdom, city, Wall.WallClass.STONE, Wall.FOLLOW_GROUND);
            payAndPlace(helper, stone);
            helper.assertTrue(stone.built() && count(helper, Blocks.COBBLESTONE) == stone.items(), "The stone wall was not placed");
            BlockPos south = helper.absolutePos(new BlockPos(6, 0, 10));
            helper.assertTrue(CityWalls.gateAt(server, null, stone.id(), south.getX(), south.getZ()).ok(), "No gate in the stone wall");
            int y = helper.absolutePos(MIDDLE).getY();
            Wall palisade = build(helper, kingdom, city, Wall.WallClass.PALISADE, Wall.FOLLOW_GROUND);
            helper.assertTrue(RoadWork.state(server).wall(stone.id()).isEmpty(), "The old wall is still kept");
            for (RouteCell cell : palisade.cells()) {
                helper.assertTrue(cell.y() == y, "The palisade stands on the stone at " + cell.x() + " " + cell.z() + ": " + cell.y());
            }
            helper.assertTrue(palisade.gateCells().stream().anyMatch(g -> g.x() == south.getX() && g.z() == south.getZ()),
                    "The gate of the stone wall did not pass to the palisade: " + palisade.gateCells());
            payAndPlace(helper, palisade);
            for (int i = 0; i < 5 && palisade.hasReplaced(); i++) {
                CityWalls.takeDownOld(server);
            }
            helper.assertTrue(!palisade.hasReplaced(), "Blocks of the stone wall wait to come down: " + palisade.replacedBlocks().length);
            helper.assertTrue(count(helper, Blocks.COBBLESTONE) == 0, "Stone of the old wall is left: " + count(helper, Blocks.COBBLESTONE));
            for (int index = 0; index < palisade.cells().size(); index++) {
                RouteCell cell = palisade.cells().get(index);
                for (int up = 1; up <= Wall.TALLEST; up++) {
                    BlockPos pos = new BlockPos(cell.x(), y + up, cell.z());
                    if (palisade.inGate(index) || up > 4) {
                        helper.assertTrue(level.getBlockState(pos).isAir(), "Not open at " + pos + ": " + level.getBlockState(pos));
                    } else {
                        helper.assertTrue(logAt(level, cell.x(), y + up, cell.z()), "No log of the palisade at " + pos);
                    }
                }
            }
            long back = stored(server, kingdom, Items.COBBLESTONE);
            helper.assertTrue(back == 256, "The stone of the old wall did not go back to the storehouse: " + back);
            long walls = KingdomWorldApi.state(server).construction().buildingsOf(kingdom.id()).stream()
                    .filter(b -> b.templateId().equals(CityWalls.WALL_TEMPLATE)).count();
            long gates = KingdomWorldApi.state(server).construction().buildingsOf(kingdom.id()).stream()
                    .filter(b -> b.templateId().equals(CityWalls.GATE_TEMPLATE)).count();
            helper.assertTrue(walls == 1 && gates == palisade.gates().size(), "The register holds " + walls + " walls and " + gates + " gates");
            Wall read = CityWalls.throughSave(palisade);
            helper.assertTrue(read.baseY() == palisade.baseY() && java.util.Arrays.equals(read.grounds(), palisade.grounds())
                    && read.placedBlocks().length == palisade.placedBlocks().length, "The wall did not survive saving");
        } finally {
            finish(helper, kingdom);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_walls_again", timeoutTicks = 200)
    public static void aStoneWallOrderedAgainAddsOnlyWhatIsMissing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        land(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Wall Menders", UUID.randomUUID());
        try {
            StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 12);
            for (int slot = 0; slot < 6; slot++) {
                store.insertItem(slot, new ItemStack(Items.COBBLESTONE, 64), false);
            }
            City city = city(helper, kingdom);
            Wall first = build(helper, kingdom, city, Wall.WallClass.STONE, Wall.FOLLOW_GROUND);
            payAndPlace(helper, first);
            int y = helper.absolutePos(MIDDLE).getY();
            RouteCell broken = first.cells().get(9);
            for (int up = 1; up <= 3; up++) {
                level.removeBlock(new BlockPos(broken.x(), y + up, broken.z()), false);
            }
            Wall again = build(helper, kingdom, city, Wall.WallClass.STONE, Wall.FOLLOW_GROUND);
            for (RouteCell cell : again.cells()) {
                helper.assertTrue(cell.y() == y, "The wall ordered again stands on the old one at " + cell.x() + " " + cell.z() + ": " + cell.y());
            }
            payAndPlace(helper, again);
            CityWalls.takeDownOld(server);
            for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(1, 1, 1)), helper.absolutePos(new BlockPos(11, 8, 11)))) {
                if (level.getBlockState(pos).is(Blocks.COBBLESTONE)) {
                    helper.assertTrue(pos.getY() <= y + Wall.TALLEST, "Stone stacked on the wall at " + pos);
                }
            }
            helper.assertTrue(count(helper, Blocks.COBBLESTONE) == again.items(), "Stone in the world " + count(helper, Blocks.COBBLESTONE)
                    + " is not the wall's " + again.items());
            for (int up = 1; up <= 3; up++) {
                BlockPos pos = new BlockPos(broken.x(), y + up, broken.z());
                helper.assertTrue(level.getBlockState(pos).is(Blocks.COBBLESTONE), "The hole was not mended at " + pos);
            }
            long left = stored(server, kingdom, Items.COBBLESTONE);
            helper.assertTrue(left == 384 - again.items() - 3, "The stone paid for the blocks kept did not go back: " + left);
            RouteCell kept = again.cells().get(20);
            helper.assertTrue(again.placedHere(BlockPos.asLong(kept.x(), y + 2, kept.z())), "The blocks kept are not the new wall's own");
            // A gate cut now opens the whole column of the blocks kept.
            BlockPos north = helper.absolutePos(new BlockPos(6, 0, 2));
            helper.assertTrue(CityWalls.gateAt(server, null, again.id(), north.getX(), north.getZ()).ok(), "No gate cut");
            for (int up = 1; up <= Wall.TALLEST; up++) {
                helper.assertTrue(level.getBlockState(north.above(up)).isAir(), "The gate did not open the old blocks at " + north.above(up));
            }
        } finally {
            finish(helper, kingdom);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_walls_trees", timeoutTicks = 200)
    public static void aWallThroughTreesAndPlantsHasNoGaps(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        land(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Wall Clearers", UUID.randomUUID());
        try {
            StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 12);
            store.insertItem(0, new ItemStack(Items.SPRUCE_LOG, 64), false);
            store.insertItem(1, new ItemStack(Items.SPRUCE_LOG, 64), false);
            // A birch with its trunk on the west side of the ring, its crown over it.
            for (int up = 1; up <= 5; up++) {
                helper.setBlock(new BlockPos(2, up, 4), Blocks.BIRCH_LOG);
            }
            for (int x = 1; x <= 3; x++) {
                for (int z = 3; z <= 5; z++) {
                    for (int up = 4; up <= 6; up++) {
                        if (x != 2 || z != 4 || up == 6) {
                            helper.setBlock(new BlockPos(x, up, z), Blocks.BIRCH_LEAVES);
                        }
                    }
                }
            }
            // Leaves hanging over the north side, plants on the ground.
            helper.setBlock(new BlockPos(6, 3, 2), Blocks.OAK_LEAVES);
            helper.setBlock(new BlockPos(6, 4, 2), Blocks.OAK_LEAVES);
            helper.setBlock(new BlockPos(4, 1, 2), Blocks.POPPY);
            helper.setBlock(new BlockPos(8, 1, 2), Blocks.OAK_SAPLING);
            helper.setBlock(new BlockPos(10, 1, 4), Blocks.SNOW);
            helper.setBlock(new BlockPos(10, 1, 5), Blocks.GRASS);
            // A fence a player put up on the east side: the wall leaves it and has a gap there.
            helper.setBlock(new BlockPos(10, 1, 8), Blocks.OAK_FENCE);
            City city = city(helper, kingdom);
            Wall wall = build(helper, kingdom, city, Wall.WallClass.PALISADE, Wall.FOLLOW_GROUND);
            int y = helper.absolutePos(MIDDLE).getY();
            for (RouteCell cell : wall.cells()) {
                helper.assertTrue(cell.y() == y, "The ring left the ground at " + cell.x() + " " + cell.z() + ": " + cell.y());
            }
            payAndPlace(helper, wall);
            BlockPos planks = helper.absolutePos(new BlockPos(10, 1, 8));
            for (RouteCell cell : wall.cells()) {
                for (int up = 1; up <= 4; up++) {
                    BlockPos pos = new BlockPos(cell.x(), y + up, cell.z());
                    if (!pos.equals(planks)) {
                        helper.assertTrue(level.getBlockState(pos).is(Blocks.SPRUCE_LOG), "A gap in the wall at " + pos + ": " + level.getBlockState(pos));
                    }
                }
            }
            helper.assertTrue(level.getBlockState(planks).is(Blocks.OAK_FENCE), "The fence of a player was broken");
            helper.assertTrue(CityWalls.blockedInWay(wall.id()), "The fence in the way was not noted");
            helper.assertTrue(count(helper, Blocks.BIRCH_LOG) == 0, "The birch still stands");
            long birch = stored(server, kingdom, Items.BIRCH_LOG);
            long poppy = stored(server, kingdom, Items.POPPY);
            long sapling = stored(server, kingdom, Items.OAK_SAPLING);
            helper.assertTrue(birch == 5 && poppy == 1 && sapling >= 1, "What was cleared did not reach the storehouse: " + birch + " birch, "
                    + poppy + " poppy, " + sapling + " sapling");
        } finally {
            finish(helper, kingdom);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_walls_hole", timeoutTicks = 200)
    public static void aWallOverAHoleIsFilledUnder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        land(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Wall Fillers", UUID.randomUUID());
        try {
            StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 12);
            store.insertItem(0, new ItemStack(Items.OAK_LOG, 64), false);
            store.insertItem(1, new ItemStack(Items.OAK_LOG, 64), false);
            City city = city(helper, kingdom);
            Wall wall = build(helper, kingdom, city, Wall.WallClass.PALISADE, Wall.FOLLOW_GROUND);
            int y = helper.absolutePos(MIDDLE).getY();
            // After the layout, holes three blocks deep are dug under two columns.
            BlockPos[] holes = {helper.absolutePos(new BlockPos(2, 0, 6)), helper.absolutePos(new BlockPos(6, 0, 2))};
            for (BlockPos hole : holes) {
                for (int down = 0; down < 3; down++) {
                    level.setBlockAndUpdate(hole.below(down), Blocks.AIR.defaultBlockState());
                }
            }
            payAndPlace(helper, wall);
            helper.assertTrue(!wall.built(), "The wall went up over the holes without earth");
            store.insertItem(2, new ItemStack(Items.DIRT, 16), false);
            CityWalls.placeNow(server, wall);
            helper.assertTrue(wall.built(), "The wall did not go up once there was earth");
            for (BlockPos hole : holes) {
                for (int down = 0; down < 3; down++) {
                    helper.assertTrue(level.getBlockState(hole.below(down)).is(Blocks.DIRT), "The hole is open at " + hole.below(down));
                }
                helper.assertTrue(logAt(level, hole.getX(), y + 1, hole.getZ()), "No wall over the hole at " + hole);
            }
            long dirt = stored(server, kingdom, Items.DIRT);
            helper.assertTrue(dirt == 16 - 6, "The fill took other than six blocks of earth: " + (16 - dirt));
        } finally {
            finish(helper, kingdom);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_walls_height", timeoutTicks = 200)
    public static void aWallAtAChosenHeightStandsOnIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        land(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Wall Levellers", UUID.randomUUID());
        try {
            StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 12);
            store.insertItem(0, new ItemStack(Items.OAK_LOG, 64), false);
            store.insertItem(1, new ItemStack(Items.OAK_LOG, 64), false);
            store.insertItem(2, new ItemStack(Items.DIRT, 64), false);
            // A hill three blocks high along the east side, a dip two blocks deep along the west side.
            for (int z = 1; z <= 11; z++) {
                for (int up = 1; up <= 3; up++) {
                    helper.setBlock(new BlockPos(10, up, z), Blocks.DIRT);
                }
                helper.setBlock(new BlockPos(2, 0, z), Blocks.AIR);
                helper.setBlock(new BlockPos(2, -1, z), Blocks.AIR);
            }
            City city = city(helper, kingdom);
            int y = helper.absolutePos(MIDDLE).getY();
            int base = y + 2;
            Wall wall = build(helper, kingdom, city, Wall.WallClass.PALISADE, base);
            helper.assertTrue(wall.baseY() == base && wall.earth() > 0 && wall.cutTotal() > 0, "The earth of the wall is not counted: "
                    + wall.earth() + " to fill, " + wall.cutTotal() + " to cut");
            payAndPlace(helper, wall);
            helper.assertTrue(wall.built(), "The wall at a height did not go up");
            for (Wall.Block block : wall.blocks()) {
                helper.assertTrue(block.y() == base - 1, "A column stands off the height: " + block);
                for (int up = 0; up < 4; up++) {
                    helper.assertTrue(logAt(level, block.x(), base + up, block.z()), "No log at " + block.x() + " " + (base + up) + " " + block.z());
                }
                for (int under = base - 1; under > y - 2; under--) {
                    helper.assertTrue(!level.getBlockState(new BlockPos(block.x(), under, block.z())).isAir(), "A hole under the wall at "
                            + block.x() + " " + under + " " + block.z());
                }
            }
            // A gate is cut at the height: its floor is the foot of the wall.
            BlockPos south = helper.absolutePos(new BlockPos(6, 0, 10));
            helper.assertTrue(CityWalls.gateAt(server, null, wall.id(), south.getX(), south.getZ()).ok(), "No gate cut");
            helper.assertTrue(!level.getBlockState(new BlockPos(south.getX(), base - 1, south.getZ())).isAir()
                    && level.getBlockState(new BlockPos(south.getX(), base, south.getZ())).isAir(), "The floor of the gate is not at the foot");
        } finally {
            finish(helper, kingdom);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_walls_others", timeoutTicks = 200)
    public static void aWallLeavesTheWallOfAnotherKingdomAndABeam(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        land(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Wall Neighbours", UUID.randomUUID());
        UUID other = UUID.randomUUID();
        long[] otherWall = {0};
        try {
            StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 12);
            store.insertItem(0, new ItemStack(Items.SPRUCE_LOG, 64), false);
            store.insertItem(1, new ItemStack(Items.SPRUCE_LOG, 64), false);
            // Two logs of a palisade of another kingdom stand in the line of the wall, on its north side.
            BlockPos palisade = helper.absolutePos(new BlockPos(6, 1, 2));
            level.setBlockAndUpdate(palisade, Blocks.OAK_LOG.defaultBlockState());
            level.setBlockAndUpdate(palisade.above(), Blocks.OAK_LOG.defaultBlockState());
            // A beam of a player hangs over the air further along the same side.
            BlockPos beam = helper.absolutePos(new BlockPos(8, 3, 2));
            level.setBlockAndUpdate(beam, Blocks.OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,
                    net.minecraft.core.Direction.Axis.X));
            RoadWork.change(server, "a wall of another kingdom", state -> {
                Wall theirs = new Wall(state.nextWallId(), 0, other, Wall.WallClass.PALISADE, Dimensions.id(level), palisade.getX() + 200,
                        palisade.getZ(), ring(palisade.getX() + 200, palisade.getZ(), 4, palisade.getY() - 1), List.of(), 0);
                theirs.markPlaced(palisade.asLong());
                theirs.markPlaced(palisade.above().asLong());
                state.putWall(theirs);
                otherWall[0] = theirs.id();
                return Outcome.DONE;
            });
            City city = city(helper, kingdom);
            Wall wall = build(helper, kingdom, city, Wall.WallClass.PALISADE, Wall.FOLLOW_GROUND);
            int y = helper.absolutePos(MIDDLE).getY();
            payAndPlace(helper, wall);
            helper.assertTrue(level.getBlockState(palisade).is(Blocks.OAK_LOG) && level.getBlockState(palisade.above()).is(Blocks.OAK_LOG),
                    "The palisade of another kingdom was taken down");
            helper.assertTrue(level.getBlockState(beam).is(Blocks.OAK_LOG), "The beam of a player was taken down");
            helper.assertTrue(CityWalls.blockedInWay(wall.id()), "The blocks of others in the way were not noted");
            // Around them the wall stands whole.
            helper.assertTrue(level.getBlockState(palisade.above(2)).is(Blocks.SPRUCE_LOG) && level.getBlockState(palisade.above(3)).is(Blocks.SPRUCE_LOG),
                    "The wall has no blocks over the palisade of the neighbour");
            for (int up = 1; up <= 4; up++) {
                BlockPos at = new BlockPos(beam.getX(), y + up, beam.getZ());
                if (!at.equals(beam)) {
                    helper.assertTrue(level.getBlockState(at).is(Blocks.SPRUCE_LOG), "A gap around the beam at " + at);
                }
            }
            helper.assertTrue(stored(server, kingdom, Items.OAK_LOG) == 0, "Logs of others reached the storehouse");
        } finally {
            RoadWork.state(server).removeWall(otherWall[0]);
            finish(helper, kingdom);
        }
        helper.succeed();
    }
}
