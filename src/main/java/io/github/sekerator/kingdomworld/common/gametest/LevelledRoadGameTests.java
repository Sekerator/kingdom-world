package io.github.sekerator.kingdomworld.common.gametest;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.work.Equipment;
import io.github.sekerator.kingdomworld.common.construction.SiteLoader;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.road.RoadBuilding;
import io.github.sekerator.kingdomworld.common.road.RoadCrews;
import io.github.sekerator.kingdomworld.common.road.RoadEarthworks;
import io.github.sekerator.kingdomworld.common.road.RoadLand;
import io.github.sekerator.kingdomworld.common.road.RoadSavedData;
import io.github.sekerator.kingdomworld.common.road.RoadSettings;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.road.Crossing;
import io.github.sekerator.kingdomworld.core.road.EarthRules;
import io.github.sekerator.kingdomworld.core.road.Endpoint;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadClass;
import io.github.sekerator.kingdomworld.core.road.RoadLayout;
import io.github.sekerator.kingdomworld.core.road.RoadRules;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import io.github.sekerator.kingdomworld.core.road.RoadSurvey;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Levelled roads laid on ways given by the code: a hill cut down to the grade, a tunnel through rock with its torches, its
 * roof propped and lava closed off, a bridge with piers and rails over wide water, what belongs to players left standing,
 * foreign land untouched, miners paying for the rock, a far road laid without a player, and a road of the first layout as
 * before.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LevelledRoadGameTests {
    private static final String EMPTY = "empty";

    private LevelledRoadGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    /**
     * A straight way along x at relative z and height, each block of a kind.
     */
    private static List<RouteCell> way(GameTestHelper helper, int fromX, int toX, int y, int z, java.util.function.IntFunction<RouteCell.Kind> kind) {
        List<RouteCell> cells = new ArrayList<>();
        for (int x = fromX; x <= toX; x++) {
            BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
            cells.add(new RouteCell(pos.getX(), pos.getY(), pos.getZ(), kind.apply(x), 0,
                    KingdomWorldApi.ownerAt(helper.getLevel(), pos)));
        }
        return cells;
    }

    /**
     * A levelled road on a way, its earthworks surveyed from the world as it is.
     */
    static Road levelled(ServerLevel level, UUID kingdomId, RoadClass roadClass, List<RouteCell> cells, Road.Mode mode, List<Crossing> sent) {
        MinecraftServer server = level.getServer();
        EarthRules earth = RoadSettings.earth();
        List<RoadLayout.ColumnSurvey> surveys = RoadSurvey.survey(cells, roadClass, (x, z) ->
                level.isLoaded(new BlockPos(x, 0, z)) ? RoadLand.column(level, x, z) : null, earth);
        long now = KingdomWorldApi.gameTime(server);
        RouteCell first = cells.get(0);
        RouteCell last = cells.get(cells.size() - 1);
        Road road = RoadWork.state(server).addRoad(kingdomId, roadClass, Dimensions.id(level), Endpoint.point(first.x(), first.z()),
                Endpoint.point(last.x(), last.z()), cells, RoadState.sectionOwners(cells), RoadState.crossedSections(cells, kingdomId), mode, now,
                now + 24_000, sent, RoadLayout.LEVELLED, surveys);
        road.setEarthTotal(RoadSurvey.earthTotal(road.blocks(), roadClass, RoadSettings.rules(), earth));
        return road;
    }

    /**
     * Pays for every block of a road from the storehouses at once.
     */
    private static void payAll(GameTestHelper helper, Road road) {
        MinecraftServer server = helper.getLevel().getServer();
        for (Road.Section section : road.sections()) {
            while (!section.done()) {
                helper.assertTrue(RoadBuilding.payNow(server, road, section), "A block of the road could not be paid for: section "
                        + section.index() + ", block " + section.paid() + ", " + road.blocksOf(section).get(section.paid()));
            }
        }
    }

    private static void stock(StorehouseInventory store, ItemStack... stacks) {
        int slot = 0;
        for (ItemStack stack : stacks) {
            store.insertItem(slot++, stack, false);
        }
    }

    private static boolean air(ServerLevel level, BlockPos from, BlockPos to) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (!level.getBlockState(pos).isAir()) {
                return false;
            }
        }
        return true;
    }

    private static void fill(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(x0, y0, z0), new BlockPos(x1, y1, z1))) {
            helper.setBlock(pos, block);
        }
    }

    private static void end(GameTestHelper helper, Kingdom kingdom, BlockPos from, BlockPos to, Road... roads) {
        end(helper, kingdom, from, to, new Citizen[0], roads);
    }

    private static void end(GameTestHelper helper, Kingdom kingdom, BlockPos from, BlockPos to, Citizen[] citizens, Road... roads) {
        RoadSettings.override(null, null);
        RoadSettings.overrideEarth(null);
        MinecraftServer server = helper.getLevel().getServer();
        for (Road road : roads) {
            if (road != null) {
                RoadWork.state(server).removeRoad(road.id());
            }
        }
        RoadWork.state(server).forgetKingdom(kingdom.id());
        CitizenGameTests.finish(helper, kingdom, citizens);
        GatheringGameTests.clear(helper.getLevel(), from, to);
    }

    // ---------------------------------------------------------------- the tests

    /**
     * 1. A hill across the way is cut down to the road: the soil and the ore go to the storehouse, the road keeps its grade.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_roads_levelled_cut")
    public static void aHillIsCutDownToTheGrade(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(13, 8, 10));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Hillcutters", UUID.randomUUID());
        Road road = null;
        try {
            fill(helper, 0, 0, 1, 12, 0, 9, Blocks.GRASS_BLOCK);
            fill(helper, 4, 1, 2, 8, 3, 8, Blocks.DIRT);
            fill(helper, 5, 1, 3, 7, 2, 7, Blocks.STONE);
            helper.setBlock(new BlockPos(6, 1, 5), Blocks.IRON_ORE);
            fill(helper, 4, 4, 2, 8, 4, 8, Blocks.GRASS_BLOCK);
            stock(CitizenGameTests.storehouse(helper, 0, 1, 0), new ItemStack(Items.GRAVEL, 64));
            road = levelled(level, kingdom.id(), RoadClass.GRAVEL, way(helper, 1, 11, 0, 5, x -> x >= 4 && x <= 8 ? RouteCell.Kind.CUT
                    : RouteCell.Kind.GROUND), Road.Mode.OWN, new ArrayList<>());
            helper.assertTrue(road.earthTotal() > 0, "The hill gives no earthworks");
            payAll(helper, road);
            RoadBuilding.placeNow(server, road);
            helper.assertTrue(air(level, helper.absolutePos(new BlockPos(4, 1, 3)), helper.absolutePos(new BlockPos(8, 4, 7))),
                    "The hill over the road and its verges was not cut away: " + RoadEarthworks.blocked(road.id()));
            for (int x = 1; x <= 11; x++) {
                helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(x, 0, 5))).is(Blocks.GRAVEL), "No gravel at " + x);
            }
            helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(6, 1, 2))).is(Blocks.DIRT),
                    "The hill beyond the verge was dug");
            helper.assertTrue(KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.RAW_IRON)) >= 1,
                    "The ore did not go to the storehouse");
            helper.assertTrue(KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.DIRT)) > 0,
                    "The soil dug did not go to the storehouse");
            helper.assertTrue(road.credit().isEmpty() && road.owed().isEmpty(), "The credit of a road laid was not settled");
            List<RouteCell> cells = road.cells();
            for (int i = 1; i < cells.size(); i++) {
                helper.assertTrue(Math.abs(cells.get(i).y() - cells.get(i - 1).y()) <= 1, "A step steeper than the grade");
            }
        } finally {
            end(helper, kingdom, from, to, road);
        }
        helper.succeed();
    }

    /**
     * 2. A tunnel three blocks high through rock: lit on its verge, its sandy roof propped, the lava beside it closed off.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_roads_levelled_tunnel")
    public static void aTunnelIsDugLitAndPropped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(15, 10, 10));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Tunnellers", UUID.randomUUID());
        Road road = null;
        try {
            fill(helper, 0, 0, 1, 14, 0, 9, Blocks.STONE);
            fill(helper, 3, 1, 1, 12, 8, 9, Blocks.STONE);
            fill(helper, 6, 4, 3, 7, 4, 7, Blocks.SAND);
            helper.setBlock(new BlockPos(9, 2, 2), Blocks.LAVA);
            stock(CitizenGameTests.storehouse(helper, 0, 1, 0), new ItemStack(Items.GRAVEL, 64), new ItemStack(Items.TORCH, 8),
                    new ItemStack(Items.COBBLESTONE, 32));
            road = levelled(level, kingdom.id(), RoadClass.GRAVEL, way(helper, 1, 14, 0, 5, x -> x >= 3 && x <= 12 ? RouteCell.Kind.TUNNEL
                    : RouteCell.Kind.GROUND), Road.Mode.OWN, new ArrayList<>());
            payAll(helper, road);
            RoadBuilding.placeNow(server, road);
            helper.assertTrue(RoadEarthworks.blocked(road.id()).isEmpty(), "Columns were held up: " + RoadEarthworks.blocked(road.id()));
            for (int x = 3; x <= 12; x++) {
                for (int z = 3; z <= 7; z++) {
                    for (int y = 1; y <= 3; y++) {
                        BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
                        helper.assertTrue(level.getBlockState(pos).isAir() || level.getBlockState(pos).is(Blocks.TORCH),
                                "The tunnel is not open at " + x + " " + y + " " + z);
                    }
                }
            }
            int torches = 0;
            for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(3, 1, 3)), helper.absolutePos(new BlockPos(12, 1, 7)))) {
                if (level.getBlockState(pos).is(Blocks.TORCH)) {
                    torches++;
                    helper.assertTrue(pos.getZ() == helper.absolutePos(new BlockPos(0, 0, 3)).getZ() || pos.getZ() == helper.absolutePos(
                            new BlockPos(0, 0, 7)).getZ(), "A torch off the verge");
                }
            }
            helper.assertTrue(torches == 1, "The tunnel of ten blocks has no torch on its verge: " + torches);
            helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(6, 4, 5))).is(Blocks.COBBLESTONE),
                    "The sandy roof was not propped");
            helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(9, 2, 2))).is(Blocks.COBBLESTONE),
                    "The lava beside the tunnel was not closed off");
            helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(6, 4, 2))).is(Blocks.STONE), "The rock beyond was dug");
        } finally {
            end(helper, kingdom, from, to, road);
        }
        helper.succeed();
    }

    /**
     * 3. Water forty blocks wide: a bridge on piers from the bed, with rails along both verges.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_roads_levelled_bridge")
    public static void aBridgeOverWideWaterHasPiersAndRails(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, -6, -1));
        BlockPos to = helper.absolutePos(new BlockPos(45, 4, 10));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Bridgewrights", UUID.randomUUID());
        Road road = null;
        try {
            fill(helper, 0, -6, 1, 43, -6, 9, Blocks.STONE);
            fill(helper, 0, -5, 1, 1, 0, 9, Blocks.DIRT);
            fill(helper, 42, -5, 1, 43, 0, 9, Blocks.DIRT);
            fill(helper, 2, -5, 1, 41, 0, 9, Blocks.WATER);
            stock(CitizenGameTests.storehouse(helper, 0, 2, 0), new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.OAK_PLANKS, 64),
                    new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.OAK_FENCE, 64),
                    new ItemStack(Items.OAK_FENCE, 64), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.GRAVEL, 64),
                    new ItemStack(Items.DIRT, 64));
            road = levelled(level, kingdom.id(), RoadClass.GRAVEL, way(helper, 0, 43, 1, 5, x -> x >= 2 && x <= 41 ? RouteCell.Kind.BRIDGE
                    : RouteCell.Kind.FILL), Road.Mode.OWN, new ArrayList<>());
            payAll(helper, road);
            RoadBuilding.placeNow(server, road);
            helper.assertTrue(RoadEarthworks.blocked(road.id()).isEmpty(), "Columns were held up: " + RoadEarthworks.blocked(road.id()));
            for (int x = 2; x <= 41; x++) {
                for (int z = 3; z <= 7; z++) {
                    helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(x, 1, z))).is(Blocks.OAK_PLANKS),
                            "No deck at " + x + " " + z);
                }
                helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(x, 2, 3))).is(Blocks.OAK_FENCE)
                        && level.getBlockState(helper.absolutePos(new BlockPos(x, 2, 7))).is(Blocks.OAK_FENCE), "No rails at " + x);
            }
            int piers = 0;
            for (int x = 2; x <= 41; x++) {
                if (level.getBlockState(helper.absolutePos(new BlockPos(x, -5, 5))).is(Blocks.COBBLESTONE)) {
                    piers++;
                    for (int y = -5; y <= 0; y++) {
                        helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(x, y, 5))).is(Blocks.COBBLESTONE),
                                "The pier at " + x + " does not reach the deck");
                    }
                }
            }
            helper.assertTrue(piers == 10, "A bridge of forty blocks stands on ten piers, not " + piers);
        } finally {
            end(helper, kingdom, from, to, road);
        }
        helper.succeed();
    }

    /**
     * 4. A block a player set and a chest in the way stand; their columns are held up and shown.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_roads_levelled_kept")
    public static void theBlocksOfPlayersAndAChestStand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(13, 8, 10));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Keepers", UUID.randomUUID());
        Road road = null;
        try {
            fill(helper, 0, 0, 1, 12, 0, 9, Blocks.GRASS_BLOCK);
            fill(helper, 4, 1, 2, 8, 3, 8, Blocks.DIRT);
            helper.setBlock(new BlockPos(5, 1, 4), Blocks.COBBLESTONE);
            helper.setBlock(new BlockPos(7, 2, 5), Blocks.CHEST);
            ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(7, 2, 5)));
            helper.assertTrue(chest != null, "No chest");
            chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
            stock(CitizenGameTests.storehouse(helper, 0, 1, 0), new ItemStack(Items.GRAVEL, 64));
            road = levelled(level, kingdom.id(), RoadClass.GRAVEL, way(helper, 1, 11, 0, 5, x -> x >= 4 && x <= 8 ? RouteCell.Kind.CUT
                    : RouteCell.Kind.GROUND), Road.Mode.OWN, new ArrayList<>());
            payAll(helper, road);
            RoadBuilding.placeNow(server, road);
            helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(5, 1, 4))).is(Blocks.COBBLESTONE), "The block was dug");
            helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(7, 2, 5))).is(Blocks.CHEST), "The chest was dug");
            ChestBlockEntity still = (ChestBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(7, 2, 5)));
            helper.assertTrue(still != null && still.getItem(0).is(Items.DIAMOND) && still.getItem(0).getCount() == 3,
                    "The chest lost its items");
            List<RoadEarthworks.Blocked> blocked = RoadEarthworks.blocked(road.id());
            helper.assertTrue(!blocked.isEmpty() && blocked.stream().allMatch(b -> b.hold() == RoadEarthworks.Hold.MADE_BLOCK),
                    "The columns in the way are not shown: " + blocked);
            helper.assertTrue(!road.section(0).placedAll(), "A column in the way counted as laid");
        } finally {
            end(helper, kingdom, from, to, road);
        }
        helper.succeed();
    }

    /**
     * 5. A levelled road over the hill of another kingdom without its consent changes nothing there.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_roads_levelled_foreign")
    public static void foreignLandIsUntouchedWithoutConsent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(9, 3, 9));
        GatheringGameTests.clear(level, from, to);
        TradeGameTests.clearSecond(helper);
        Kingdom ours = CitizenGameTests.found(helper, "GameTest Levellers", UUID.randomUUID());
        Kingdom theirs = TradeGameTests.second(helper, "GameTest Hillkeepers", UUID.randomUUID());
        int sx = TradeGameTests.SECOND;
        Road road = null;
        try {
            fill(helper, sx, 0, 3, sx + 7, 0, 7, Blocks.GRASS_BLOCK);
            fill(helper, sx + 2, 1, 3, sx + 5, 2, 7, Blocks.DIRT);
            stock(CitizenGameTests.storehouse(helper, 0, 0, 8), new ItemStack(Items.GRAVEL, 64));
            List<Crossing> sent = new ArrayList<>();
            road = levelled(level, ours.id(), RoadClass.GRAVEL, way(helper, sx, sx + 7, 0, 5, x -> x >= sx + 2 && x <= sx + 5
                    ? RouteCell.Kind.CUT : RouteCell.Kind.GROUND), Road.Mode.PAYER, sent);
            helper.assertTrue(road.status() == Road.Status.WAITING_CONSENT, "The road did not ask for consent");
            for (Road.Section section : road.sections()) {
                while (!section.done()) {
                    section.markPaid(Map.of("minecraft:gravel", 1));
                }
            }
            RoadBuilding.placeNow(server, road);
            helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(sx + 3, 1, 5))).is(Blocks.DIRT), "Their hill was dug");
            helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(sx, 0, 5))).is(Blocks.GRASS_BLOCK), "Their grass was touched");
            helper.assertTrue(road.section(0).placed() == 0, "A column was laid on foreign land without consent");
        } finally {
            KingdomWorldApi.dissolveKingdom(server, theirs.id());
            RoadWork.state(server).forgetKingdom(theirs.id());
            end(helper, ours, from, to, road);
            TradeGameTests.clearSecond(helper);
        }
        helper.succeed();
    }

    /**
     * 7. Miners pay for the rock of a cutting and wear their pickaxes; with the help turned off they give nothing.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_roads_levelled_miners")
    public static void minersPayForTheRockAndWearTheirPickaxes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(13, 8, 10));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Quarrymen", UUID.randomUUID());
        Road road = null;
        List<Citizen> citizens = new ArrayList<>();
        try {
            fill(helper, 0, 0, 1, 12, 0, 9, Blocks.STONE);
            fill(helper, 3, 1, 2, 9, 2, 8, Blocks.STONE);
            stock(CitizenGameTests.storehouse(helper, 0, 1, 0), new ItemStack(Items.GRAVEL, 64), new ItemStack(Items.IRON_PICKAXE));
            RoadRules.Builder rules = RoadSettings.configuredRoads();
            rules.secondsPerBlock = new double[]{0.05, 0.05, 0.05};
            RoadSettings.override(rules.build(), null);
            EarthRules.Builder earth = RoadSettings.configuredEarth();
            earth.digRockSeconds = 0.5;
            earth.roadMinerShare = 1.0;
            RoadSettings.overrideEarth(earth.build());
            WorldPos builderPost = GatheringGameTests.post(helper, new BlockPos(11, 1, 0), "builder");
            Citizen builder = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
            citizens.add(builder);
            KingdomWorldApi.change(server, "test", state -> state.setProfession(null, builder.id(), "builder"));
            helper.assertTrue(KingdomWorldApi.state(server).citizens().assignWorkplace(builder, builderPost), "The builder got no seat");
            WorldPos minerPost = GatheringGameTests.post(helper, new BlockPos(12, 1, 0), "miner");
            Citizen miner = CitizenGameTests.newcomer(helper, kingdom, 2, 1);
            citizens.add(miner);
            KingdomWorldApi.change(server, "test", state -> state.setProfession(null, miner.id(), "miner"));
            helper.assertTrue(KingdomWorldApi.state(server).citizens().assignWorkplace(miner, minerPost), "The miner got no seat");
            road = levelled(level, kingdom.id(), RoadClass.GRAVEL, way(helper, 1, 11, 0, 5, x -> x >= 3 && x <= 9 ? RouteCell.Kind.CUT
                    : RouteCell.Kind.GROUND), Road.Mode.OWN, new ArrayList<>());
            int helped = 0;
            for (int i = 0; i < 200 && !road.built(); i++) {
                RoadBuilding.tick(server);
                helped = Math.max(helped, RoadCrews.helpers(kingdom.id()));
            }
            helper.assertTrue(road.built(), "Builders and miners did not pay for the road: " + road.paid() + " of " + road.units());
            helper.assertTrue(helped == 1, "The miner did not help: " + helped);
            ItemStack pickaxe = Equipment.of(server, miner.id()).get(EquipmentSlot.MAINHAND);
            helper.assertTrue(pickaxe.is(Items.IRON_PICKAXE) && pickaxe.getDamageValue() > 0, "The pickaxe of the miner is not worn: " + pickaxe);
            helper.assertTrue(RoadCrews.used(kingdom.id()) > 0, "The mines do not know the roads took the time of the miner");
            int worn = pickaxe.getDamageValue();
            RoadWork.state(server).setMinersHelp(kingdom.id(), false);
            // The same rock again, for a second road over the first.
            fill(helper, 3, 1, 2, 9, 2, 8, Blocks.STONE);
            Road second = levelled(level, kingdom.id(), RoadClass.GRAVEL, way(helper, 1, 11, 0, 5, x -> RouteCell.Kind.CUT), Road.Mode.OWN,
                    new ArrayList<>());
            int before = second.paid();
            for (int i = 0; i < 5; i++) {
                RoadBuilding.tick(server);
                helper.assertTrue(RoadCrews.helpers(kingdom.id()) == 0, "A miner helped with the help turned off");
            }
            helper.assertTrue(second.paid() > before, "The builders did not dig the rock alone");
            helper.assertTrue(Equipment.of(server, miner.id()).get(EquipmentSlot.MAINHAND).getDamageValue() == worn,
                    "The pickaxe wore with the help turned off");
            RoadWork.state(server).removeRoad(second.id());
        } finally {
            RoadWork.state(server).setMinersHelp(kingdom.id(), true);
            end(helper, kingdom, from, to, citizens.toArray(new Citizen[0]), road);
        }
        helper.succeed();
    }

    /**
     * 8. A levelled road paid for far from any player is laid by the loader of places.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_roads_levelled_far", timeoutTicks = 6_000)
    public static void aFarRoadIsLaidWithoutAPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Far Roads", UUID.randomUUID());
        int x0 = helper.absolutePos(BlockPos.ZERO).getX() + RemoteBuildingGameTests.FAR;
        int z0 = helper.absolutePos(BlockPos.ZERO).getZ() + 64;
        BlockPos middle = new BlockPos(x0 + 20, RemoteBuildingGameTests.HIGH, z0);
        RemoteBuildingGameTests.meadow(helper, kingdom, middle, 24, l -> {
        });
        List<RouteCell> cells = new ArrayList<>();
        for (int x = 0; x <= 40; x++) {
            cells.add(new RouteCell(x0 + x, RemoteBuildingGameTests.HIGH, z0, RouteCell.Kind.GROUND, 0, kingdom.id()));
        }
        // The far land is not loaded: its surveys are empty, the road lies on the meadow as it is.
        Road road = levelled(level, kingdom.id(), RoadClass.GRAVEL, cells, Road.Mode.OWN, new ArrayList<>());
        for (Road.Section section : road.sections()) {
            List<RoadLayout.Block> blocks = road.blocksOf(section);
            while (!section.done()) {
                RoadLayout.Block block = blocks.get(section.paid());
                section.markPaid(block.role() == RoadLayout.Role.VERGE ? Map.of() : Map.of("minecraft:gravel", 1));
            }
        }
        long id = road.id();
        helper.succeedWhen(() -> {
            Road now = RoadWork.state(server).road(id).orElseThrow();
            for (Road.Section section : now.sections()) {
                helper.assertTrue(section.placedAll(), "The far road is not laid yet: section " + section.index() + " " + section.placed());
            }
            helper.assertTrue(SiteLoader.describe().isEmpty(), "The far place is still held");
            RoadWork.state(server).removeRoad(id);
            CitizenGameTests.finish(helper, kingdom);
        });
    }

    /**
     * 9. A road of the first layout read back from saved data has the same blocks and is laid as before.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_roads_levelled_old")
    public static void anOldRoadIsReadAndLaidAsBefore(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(9, 3, 9));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Old Roads", UUID.randomUUID());
        Road road = null;
        try {
            fill(helper, 1, 0, 4, 7, 0, 6, Blocks.GRASS_BLOCK);
            List<RouteCell> cells = way(helper, 1, 7, 0, 5, x -> RouteCell.Kind.GROUND);
            long now = KingdomWorldApi.gameTime(server);
            road = RoadWork.state(server).addRoad(kingdom.id(), RoadClass.GRAVEL, Dimensions.id(level), Endpoint.point(cells.get(0).x(),
                    cells.get(0).z()), Endpoint.point(cells.get(6).x(), cells.get(6).z()), cells, RoadState.sectionOwners(cells), Map.of(),
                    Road.Mode.OWN, now, now, new ArrayList<>());
            CompoundTag saved = RoadSavedData.get(server).save(new CompoundTag());
            RoadState read = RoadSavedData.load(saved).state();
            Road back = read.road(road.id()).orElseThrow();
            helper.assertTrue(!back.levelled(), "An old road read back as levelled");
            helper.assertTrue(back.blocks().equals(RoadLayout.blocks(road.cells(), RoadClass.GRAVEL)), "The blocks of an old road changed");
            for (Road.Section section : road.sections()) {
                while (!section.done()) {
                    section.markPaid(Map.of("minecraft:gravel", 1));
                }
            }
            RoadBuilding.placeNow(server, road);
            int gravel = 0;
            for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(1, 0, 4)), helper.absolutePos(new BlockPos(7, 0, 6)))) {
                gravel += level.getBlockState(pos).is(Blocks.GRAVEL) ? 1 : 0;
            }
            helper.assertTrue(gravel == road.units(), "The old road was not laid as before: " + gravel + " of " + road.units());
        } finally {
            end(helper, kingdom, from, to, road);
        }
        helper.succeed();
    }

    /**
     * 6. A point between on free land is kept: the road passes within two blocks of it.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_roads_levelled_point", timeoutTicks = 2_400)
    public static void aPointBetweenIsKept(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Waymarkers", UUID.randomUUID());
        int x0 = helper.absolutePos(BlockPos.ZERO).getX() + RemoteBuildingGameTests.FAR + 600;
        int z0 = helper.absolutePos(BlockPos.ZERO).getZ() + 64;
        RemoteBuildingGameTests.meadow(helper, kingdom, new BlockPos(x0 + 40, RemoteBuildingGameTests.HIGH, z0), 48, l -> {
        });
        try {
            io.github.sekerator.kingdomworld.core.road.RoutePlan.Result result = io.github.sekerator.kingdomworld.common.road.RoadPlanSessions.planNow(
                    level, kingdom.id(), List.of(new int[]{x0, z0}, new int[]{x0 + 40, z0 + 30}, new int[]{x0 + 80, z0}), RoadClass.GRAVEL,
                    io.github.sekerator.kingdomworld.core.road.RouteStyle.TERRAIN);
            helper.assertTrue(result.found(), "No way through the point: " + result.failure() + " at " + java.util.Arrays.toString(result.stuck()));
            int[] point = result.snapped().get(1);
            helper.assertTrue(Math.abs(point[0] - (x0 + 40)) <= 2 && Math.abs(point[1] - (z0 + 30)) <= 2, "The point moved off: "
                    + point[0] + " " + point[1]);
            helper.assertTrue(result.cells().stream().anyMatch(cell -> Math.abs(cell.x() - point[0]) <= 2 && Math.abs(cell.z() - point[1]) <= 2),
                    "The road does not pass the point");
        } finally {
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }

    /**
     * 10. A road to its own city behind a wall without a gate goes through the wall, and a gate is cut there when it is laid.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_roads_levelled_gate", timeoutTicks = 2_400)
    public static void aRoadIntoAWalledCityCutsAGate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Gatecutters", UUID.randomUUID());
        int cx = helper.absolutePos(BlockPos.ZERO).getX() + RemoteBuildingGameTests.FAR + 900;
        int cz = helper.absolutePos(BlockPos.ZERO).getZ() + 64;
        int high = RemoteBuildingGameTests.HIGH;
        RemoteBuildingGameTests.meadow(helper, kingdom, new BlockPos(cx - 20, high, cz), 48, l -> {
        });
        RoadState roads = RoadWork.state(server);
        io.github.sekerator.kingdomworld.core.road.City city = roads.addCity(kingdom.id(), "GameTest Walled", Dimensions.id(level), cx, high + 1, cz,
                KingdomWorldApi.gameTime(server));
        List<RouteCell> ring = new ArrayList<>();
        int r = 8;
        for (int x = -r; x <= r; x++) {
            ring.add(new RouteCell(cx + x, high, cz - r, RouteCell.Kind.GROUND, 0, null));
        }
        for (int z = -r + 1; z <= r; z++) {
            ring.add(new RouteCell(cx + r, high, cz + z, RouteCell.Kind.GROUND, 0, null));
        }
        for (int x = r - 1; x >= -r; x--) {
            ring.add(new RouteCell(cx + x, high, cz + r, RouteCell.Kind.GROUND, 0, null));
        }
        for (int z = r - 1; z > -r; z--) {
            ring.add(new RouteCell(cx - r, high, cz + z, RouteCell.Kind.GROUND, 0, null));
        }
        io.github.sekerator.kingdomworld.core.road.Wall wall = new io.github.sekerator.kingdomworld.core.road.Wall(roads.nextWallId(), city.id(),
                kingdom.id(), io.github.sekerator.kingdomworld.core.road.Wall.WallClass.PALISADE, Dimensions.id(level), cx, cz, ring, List.of(),
                KingdomWorldApi.gameTime(server));
        roads.putWall(wall);
        try {
            io.github.sekerator.kingdomworld.core.road.RoutePlan.Result result = io.github.sekerator.kingdomworld.common.road.RoadPlanSessions.planNow(
                    level, kingdom.id(), List.of(new int[]{cx - 50, cz}, new int[]{cx, cz}), RoadClass.GRAVEL,
                    io.github.sekerator.kingdomworld.core.road.RouteStyle.TERRAIN);
            helper.assertTrue(result.found(), "No way into the city: " + result.failure());
            io.github.sekerator.kingdomworld.common.road.RoadPlanning.Plan plan = io.github.sekerator.kingdomworld.common.road.RoadPlanSessions.lastTestPlan;
            helper.assertTrue(plan != null && !plan.gates().isEmpty(), "The plan cuts no gate into the wall");
            int before = wall.gates().size();
            Road[] made = new Road[1];
            io.github.sekerator.kingdomworld.core.kingdom.Outcome built = io.github.sekerator.kingdomworld.common.road.RoadPlanning.build(server, null,
                    kingdom.id(), plan.id(), RoadClass.GRAVEL, Road.Mode.OWN, made);
            helper.assertTrue(built.ok(), "The road was not laid: " + built.error());
            helper.assertTrue(made[0].levelled(), "The road is not levelled");
            helper.assertTrue(wall.gates().size() > before, "No gate was ordered in the wall");
            roads.removeRoad(made[0].id());
        } finally {
            roads.removeWall(wall.id());
            roads.removeCity(city.id());
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }
}
