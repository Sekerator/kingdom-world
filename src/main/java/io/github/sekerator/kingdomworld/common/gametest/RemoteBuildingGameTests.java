package io.github.sekerator.kingdomworld.common.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.construction.BuildingEffects;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplate;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplates;
import io.github.sekerator.kingdomworld.common.construction.ConstructionSettings;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.construction.RemoteWorks;
import io.github.sekerator.kingdomworld.common.construction.SiteLoader;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.production.Production;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.BuildPlan;
import io.github.sekerator.kingdomworld.core.construction.Commission;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.production.Order;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Game tests of building far from the capital: a house and a wall far from every player are finished by themselves, the place
 * is loaded for a while and let go again; orders from the capital pass over the blocks of players, set up posts of builders and
 * leave the orders of the king alone. Each test founds its kingdom in a batch of its own; the far places lie two thousand
 * blocks away, where nothing is loaded, on meadows made over the land.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RemoteBuildingGameTests {
    private static final String EMPTY = "empty";
    /** Blocks between the test and the far place: nothing is loaded there. */
    static final int FAR = 2_048;
    /** The height of the far place: above any land, nothing to clear. */
    static final int HIGH = 220;

    private RemoteBuildingGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    /**
     * A structure tag as the structure block writes it, with block states written {@code name[property=value,...]}.
     */
    static CompoundTag structure(int sizeX, int sizeY, int sizeZ, List<String> palette, int[][] blocks) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", 3465);
        ListTag size = new ListTag();
        for (int value : new int[]{sizeX, sizeY, sizeZ}) {
            size.add(net.minecraft.nbt.IntTag.valueOf(value));
        }
        tag.put("size", size);
        ListTag paletteTag = new ListTag();
        for (String state : palette) {
            CompoundTag entry = new CompoundTag();
            int open = state.indexOf('[');
            entry.putString("Name", open < 0 ? state : state.substring(0, open));
            if (open >= 0) {
                CompoundTag properties = new CompoundTag();
                for (String pair : state.substring(open + 1, state.length() - 1).split(",")) {
                    String[] parts = pair.split("=", 2);
                    properties.putString(parts[0], parts[1]);
                }
                entry.put("Properties", properties);
            }
            paletteTag.add(entry);
        }
        tag.put("palette", paletteTag);
        ListTag list = new ListTag();
        for (int[] block : blocks) {
            CompoundTag entry = new CompoundTag();
            ListTag pos = new ListTag();
            for (int i = 0; i < 3; i++) {
                pos.add(net.minecraft.nbt.IntTag.valueOf(block[i]));
            }
            entry.put("pos", pos);
            entry.putInt("state", block[3]);
            list.add(entry);
        }
        tag.put("blocks", list);
        tag.put("entities", new ListTag());
        return tag;
    }

    /**
     * A tiny house: a floor of cobblestone five by five, a bed on it and air above.
     */
    static BuildingTemplate house(String name) {
        List<String> palette = List.of("minecraft:cobblestone", "minecraft:red_bed[facing=east,occupied=false,part=foot]",
                "minecraft:red_bed[facing=east,occupied=false,part=head]", "minecraft:air");
        List<int[]> blocks = new ArrayList<>();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                blocks.add(new int[]{x, 0, z, 0});
                int state = z == 2 && x == 1 ? 1 : z == 2 && x == 2 ? 2 : 3;
                blocks.add(new int[]{x, 1, z, state});
                blocks.add(new int[]{x, 2, z, 3});
            }
        }
        JsonObject description = JsonParser.parseString("{\"name\": \"kingdomworld.building.small_house\", \"fallback_name\": \"" + name
                + "\", \"category\": \"housing\", \"type\": \"test\", \"icon\": \"minecraft:red_bed\", \"anchor\": [2, 0, 4], "
                + "\"entrance\": \"south\"}").getAsJsonObject();
        return BuildingTemplates.addForTest(KingdomWorld.id("test_" + name), description, structure(5, 3, 5, palette, blocks.toArray(new int[0][])));
    }

    /**
     * Gives the kingdom every chunk of a box, widened by a block, wherever it lies.
     */
    static void claim(MinecraftServer server, Kingdom kingdom, int minX, int minZ, int maxX, int maxZ) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        for (int cx = (minX - 1) >> 4; cx <= (maxX + 1) >> 4; cx++) {
            for (int cz = (minZ - 1) >> 4; cz <= (maxZ + 1) >> 4; cz++) {
                state.territory().assign(kingdom.id(), io.github.sekerator.kingdomworld.core.territory.ChunkKey.of(cx, cz));
            }
        }
    }

    /**
     * A construction laid out far away from the data, everything of it already paid for by builders who were there.
     */
    static Construction paidFar(GameTestHelper helper, Kingdom kingdom, BuildingTemplate template, BlockPos anchor) {
        MinecraftServer server = helper.getLevel().getServer();
        Box box = template.box(anchor, Placement.NONE);
        claim(server, kingdom, box.minX(), box.minZ(), box.maxX(), box.maxZ());
        BuildPlan plan = ConstructionWork.plan(template, anchor, Placement.NONE, List.of());
        KingdomWorldState.ConstructionChange change = KingdomWorldApi.state(server).layConstruction(null, kingdom.id(), template.id().toString(),
                template.fingerprint(), new WorldPos(Dimensions.id(helper.getLevel()), anchor.getX(), anchor.getY(), anchor.getZ()), Placement.NONE,
                box, List.of(), plan.size(), KingdomWorldApi.gameTime(server), ConstructionSettings.rules());
        helper.assertTrue(change.construction().isPresent(), "The far construction was refused: " + change.outcome());
        Construction construction = change.construction().get();
        for (int step = 0; step < plan.size(); step++) {
            construction.markPending(step);
        }
        construction.setStatus(Construction.Status.WAITING_FOR_LOAD);
        return construction;
    }

    // ---------------------------------------------------------------- far constructions

    @GameTest(template = EMPTY, batch = "kingdomworld_remote_house", timeoutTicks = 6_000)
    public static void aFarHouseIsFinishedWithoutAPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Far Houses", UUID.randomUUID());
        BuildingTemplate template = house("far_house");
        BlockPos anchor = new BlockPos(helper.absolutePos(BlockPos.ZERO).getX() + FAR, HIGH, helper.absolutePos(BlockPos.ZERO).getZ());
        Construction construction = paidFar(helper, kingdom, template, anchor);
        long id = construction.id();
        helper.assertTrue(!ConstructionWork.loaded(level, construction), "The far place is loaded already");
        helper.succeedWhen(() -> {
            KingdomWorldState state = KingdomWorldApi.state(server);
            helper.assertTrue(state.construction().get(id).isEmpty(), "The far house is not finished yet");
            helper.assertTrue(state.construction().buildingsOf(kingdom.id()).stream().anyMatch(building -> building.templateId().equals(template.id().toString())),
                    "The far house is not in the register");
            helper.assertTrue(BuildingEffects.freeBeds(server, kingdom.id())[0] >= 1, "The bed of the far house is not in the data");
            helper.assertTrue(SiteLoader.describe().isEmpty(), "The far place is still held: " + SiteLoader.describe().orElse(""));
            CitizenGameTests.finish(helper, kingdom);
            BuildingTemplates.removeForTest(template.id());
        });
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_remote_wall", timeoutTicks = 6_000)
    public static void aFarWallIsPutUpAndRegistered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Far Walls", UUID.randomUUID());
        int cx = helper.absolutePos(BlockPos.ZERO).getX() + FAR;
        int cz = helper.absolutePos(BlockPos.ZERO).getZ() + 8;
        List<RouteCell> cells = new ArrayList<>();
        int r = 4;
        for (int x = -r; x <= r; x++) {
            cells.add(new RouteCell(cx + x, HIGH, cz - r, RouteCell.Kind.GROUND, 0, null));
        }
        for (int z = -r + 1; z <= r; z++) {
            cells.add(new RouteCell(cx + r, HIGH, cz + z, RouteCell.Kind.GROUND, 0, null));
        }
        for (int x = r - 1; x >= -r; x--) {
            cells.add(new RouteCell(cx + x, HIGH, cz + r, RouteCell.Kind.GROUND, 0, null));
        }
        for (int z = r - 1; z > -r; z--) {
            cells.add(new RouteCell(cx - r, HIGH, cz + z, RouteCell.Kind.GROUND, 0, null));
        }
        claim(server, kingdom, cx - r, cz - r, cx + r, cz + r);
        RoadState roads = RoadWork.state(server);
        Wall wall = new Wall(roads.nextWallId(), 0, kingdom.id(), Wall.WallClass.PALISADE, Dimensions.id(level), cx, cz, cells, List.of(),
                KingdomWorldApi.gameTime(server));
        for (Road.Section section : wall.sections()) {
            List<Wall.Block> blocks = wall.blocksOf(section);
            for (int i = 0; i < section.units(); i++) {
                section.markPaid(Map.of("minecraft:oak_log", blocks.get(i).height()));
            }
        }
        roads.putWall(wall);
        long id = wall.id();
        helper.succeedWhen(() -> {
            Wall now = RoadWork.state(server).wall(id).orElseThrow();
            helper.assertTrue(now.built(), "The far wall does not stand yet");
            helper.assertTrue(now.buildingId() != 0, "The far wall is not in the register");
            helper.assertTrue(SiteLoader.describe().isEmpty(), "The far place is still held");
            KingdomWorldApi.state(server).construction().removeBuilding(now.buildingId());
            RoadWork.state(server).removeWall(id);
            CitizenGameTests.finish(helper, kingdom);
        });
    }

    // ---------------------------------------------------------------- builders of other cities

    @GameTest(template = EMPTY, batch = "kingdomworld_remote_helpers", timeoutTicks = 400)
    public static void helpersPayForAConstructionNoPostReaches(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Helpers", UUID.randomUUID());
        BuildingTemplate template = house("helped_house");
        io.github.sekerator.kingdomworld.core.citizen.Citizen builder = null;
        try {
            io.github.sekerator.kingdomworld.common.storage.StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 4);
            store.insertItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 64), false);
            store.insertItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RED_BED, 1), false);
            WorldPos post = GatheringGameTests.post(helper, new BlockPos(6, 0, 6), "builder");
            builder = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
            UUID id = builder.id();
            helper.assertTrue(KingdomWorldApi.change(server, "test", state -> state.assign(null, post, id)).ok(), "The builder was not hired");
            // A house laid out two thousand blocks away, where no post reaches, nothing paid for yet.
            BlockPos anchor = new BlockPos(helper.absolutePos(BlockPos.ZERO).getX() + FAR, HIGH, helper.absolutePos(BlockPos.ZERO).getZ());
            Construction construction = far(helper, kingdom, template, anchor, false);
            helper.assertTrue(io.github.sekerator.kingdomworld.common.construction.RemoteCrews.unreached(server, kingdom.id()).contains(construction),
                    "A post reaches the far house");
            for (int second = 0; second < 5; second++) {
                io.github.sekerator.kingdomworld.common.road.RoadBuilding.tick(server);
            }
            helper.assertTrue(!construction.pending().isEmpty(), "The helper paid for nothing");
            helper.assertTrue(io.github.sekerator.kingdomworld.common.construction.RemoteCrews.helping(construction.id())[0] == 1,
                    "The helper is not counted at the far house");
            helper.assertTrue(CitizenGameTests.count(store, net.minecraft.world.item.Items.COBBLESTONE) < 64, "No cobblestone was taken");
            helper.assertTrue(ConstructionWork.builders(construction, KingdomWorldApi.gameTime(server)) >= 1,
                    "The far house has no builder and would stop");
        } finally {
            if (builder != null) {
                CitizenGameTests.finish(helper, kingdom, builder);
            } else {
                CitizenGameTests.finish(helper, kingdom);
            }
            BuildingTemplates.removeForTest(template.id());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_remote_cart", timeoutTicks = 2_400)
    public static void aCartBringsMaterialsToAVillageWithoutAStorehouse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(24, 4, 24));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Carts", UUID.randomUUID());
        BuildingTemplate template = house("carted_house");
        // Small squares: the capital around its throne, the village around its town hall, the storehouse only in the capital.
        io.github.sekerator.kingdomworld.core.road.RoadRules.Builder rules = io.github.sekerator.kingdomworld.common.road.RoadSettings.configuredRoads();
        rules.wallRadius = 4;
        rules.minCityDistance = 8;
        io.github.sekerator.kingdomworld.common.road.RoadSettings.override(rules.build(), null);
        io.github.sekerator.kingdomworld.core.citizen.Citizen builder = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
        io.github.sekerator.kingdomworld.common.storage.StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 4);
        store.insertItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 64), false);
        store.insertItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RED_BED, 1), false);
        helper.assertTrue(RoadWork.foundCity(level, null, kingdom.id(), helper.absolutePos(new BlockPos(16, 0, 16)), "Cartville").ok(),
                "The village was not founded");
        WorldPos post = GatheringGameTests.post(helper, new BlockPos(16, 0, 13), "builder");
        UUID id = builder.id();
        helper.assertTrue(KingdomWorldApi.change(server, "test", state -> state.assign(null, post, id)).ok(), "The builder was not hired");
        io.github.sekerator.kingdomworld.common.citizen.CitizenBodies.force(id, true);
        KingdomWorldState.ConstructionChange change = ConstructionWork.lay(level, null, kingdom.id(), template, helper.absolutePos(new BlockPos(16, 1, 20)),
                Placement.NONE);
        helper.assertTrue(change.construction().isPresent(), "The village house was refused: " + change.outcome());
        Construction construction = change.construction().get();
        helper.succeedWhen(() -> {
            helper.assertTrue(construction.doneCount() > 0 || !construction.delivered().isEmpty(),
                    "Nothing came to the village house from the storehouse of the capital");
            helper.assertTrue(construction.status() != Construction.Status.NO_MATERIALS, "The village house lacks materials");
            io.github.sekerator.kingdomworld.common.road.RoadSettings.override(null, null);
            CitizenGameTests.finish(helper, kingdom, builder);
            GatheringGameTests.clear(level, from, to);
            BuildingTemplates.removeForTest(template.id());
        });
    }

    // ---------------------------------------------------------------- orders from the capital

    /**
     * A flat meadow of grass far away at {@link #HIGH}, a radius around a middle, for a city whose places are looked at: it is
     * loaded while it is made and let go again. The kingdom gets its land.
     */
    static void meadow(GameTestHelper helper, Kingdom kingdom, BlockPos middle, int radius, java.util.function.Consumer<ServerLevel> extra) {
        ServerLevel level = helper.getLevel();
        claim(level.getServer(), kingdom, middle.getX() - radius, middle.getZ() - radius, middle.getX() + radius, middle.getZ() + radius);
        BlockPos from = new BlockPos(middle.getX() - radius, HIGH, middle.getZ() - radius);
        BlockPos to = new BlockPos(middle.getX() + radius, HIGH, middle.getZ() + radius);
        TestChunks.hold(level, from, to);
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        }
        extra.accept(level);
        TestChunks.release(level, from);
    }

    /**
     * A city founded far away over a meadow, its town hall on the grass.
     */
    static long farCity(GameTestHelper helper, Kingdom kingdom, BlockPos hall, String name) {
        MinecraftServer server = helper.getLevel().getServer();
        Outcome founded = RoadWork.foundCity(helper.getLevel(), null, kingdom.id(), hall, name);
        helper.assertTrue(founded.ok(), "The far city was not founded: " + founded);
        return RoadWork.state(server).citiesOf(kingdom.id()).stream().filter(city -> city.name().equals(name)).findFirst().orElseThrow().id();
    }

    /**
     * Whether a block of a place far away still stands, the place loaded for a moment to look.
     */
    static boolean stands(ServerLevel level, BlockPos pos, Block block) {
        TestChunks.hold(level, pos, pos);
        try {
            return level.getBlockState(pos).is(block);
        } finally {
            TestChunks.release(level, pos);
        }
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_remote_order_build", timeoutTicks = 12_000)
    public static void anOrderPassesOverTheFenceAndTheChestOfAPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Orders", UUID.randomUUID());
        BuildingTemplate template = house("ordered_house");
        BlockPos hall = new BlockPos(helper.absolutePos(BlockPos.ZERO).getX() + FAR, HIGH + 1, helper.absolutePos(BlockPos.ZERO).getZ());
        // A fence and a chest of a player on the nearest ring around the town hall.
        BlockPos fence = hall.offset(0, 0, -5);
        BlockPos chest = hall.offset(5, 0, 0);
        meadow(helper, kingdom, hall, 32, at -> {
            at.setBlock(fence, Blocks.OAK_FENCE.defaultBlockState(), 3);
            at.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        });
        long city = farCity(helper, kingdom, hall, "Orderville");
        helper.assertTrue(RemoteWorks.orderBuild(server, null, kingdom.id(), template.id().toString(), Materials.NONE, city,
                Construction.Priority.NORMAL).ok(), "The order was refused");
        helper.succeedWhen(() -> {
            KingdomWorldState state = KingdomWorldApi.state(server);
            helper.assertTrue(state.construction().commissionsOf(kingdom.id()).isEmpty(), "The order is not laid out yet: "
                    + state.construction().commissionsOf(kingdom.id()));
            Construction laid = state.construction().of(kingdom.id()).stream().filter(c -> c.templateId().equals(template.id().toString()))
                    .findFirst().orElseThrow(() -> new GameTestAssertException("No house was laid out"));
            Box box = laid.box();
            Box wide = new Box(box.minX() - 1, box.minY() - 1, box.minZ() - 1, box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
            helper.assertTrue(!wide.contains(fence.getX(), fence.getY(), fence.getZ()), "The house was laid over the fence");
            helper.assertTrue(!wide.contains(chest.getX(), chest.getY(), chest.getZ()), "The house was laid over the chest");
            helper.assertTrue(box.minY() == HIGH + 1, "The house does not stand on the meadow: " + box);
            helper.assertTrue(ConstructionWork.cityOf(server, laid) == city, "The house was laid outside its city");
            ConstructionWork.cancel(server, null, kingdom.id(), laid.id());
            helper.assertTrue(stands(level, fence, Blocks.OAK_FENCE), "The fence is gone");
            helper.assertTrue(stands(level, chest, Blocks.CHEST), "The chest is gone");
            RoadWork.state(server).removeCity(city);
            CitizenGameTests.finish(helper, kingdom);
            BuildingTemplates.removeForTest(template.id());
        });
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_remote_order_at", timeoutTicks = 12_000)
    public static void anOrderAtABuiltPlaceIsRefusedWithItsReason(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Orders At", UUID.randomUUID());
        BuildingTemplate template = house("ordered_at_house");
        BlockPos point = new BlockPos(helper.absolutePos(BlockPos.ZERO).getX() + FAR, HIGH + 1, helper.absolutePos(BlockPos.ZERO).getZ());
        // The point and everything within reach of it is built over with the planks of a player.
        meadow(helper, kingdom, point, 48, at -> {
            for (BlockPos pos : BlockPos.betweenClosed(point.offset(-18, 0, -18), point.offset(18, 0, 18))) {
                at.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 2);
            }
        });
        String dimension = Dimensions.id(level);
        // On the map: a construction there already is told at once, free land is not refused.
        Construction there = far(helper, kingdom, template, point.offset(30, 0, 0), false);
        helper.assertTrue(RemoteWorks.checkPoint(server, null, kingdom.id(), template.id().toString(), dimension, point.getX() + 30,
                point.getZ() - 2, 0) != null, "The map lets a house be ordered over another");
        helper.assertTrue(RemoteWorks.checkPoint(server, null, kingdom.id(), template.id().toString(), dimension, point.getX(), point.getZ(), 0)
                == null, "The map refuses the land of the kingdom");
        ConstructionWork.cancel(server, null, kingdom.id(), there.id());
        // In the world: the planks are seen when the order is worked on.
        helper.assertTrue(RemoteWorks.orderAt(server, null, kingdom.id(), template.id().toString(), Materials.NONE, dimension, point.getX(),
                point.getZ(), 2, Construction.Priority.NORMAL).ok(), "The order was refused on the map");
        helper.succeedWhen(() -> {
            KingdomWorldState state = KingdomWorldApi.state(server);
            List<Commission> orders = state.construction().commissionsOf(kingdom.id());
            helper.assertTrue(orders.size() == 1, "The order is gone");
            Commission order = orders.get(0);
            helper.assertTrue(order.state() == Commission.State.NO_PLACE, "The order does not say it has no place: " + order.state());
            helper.assertTrue(order.reason().startsWith("blocked|"), "The order does not tell the block in the way: " + order.reason());
            helper.assertTrue(state.construction().of(kingdom.id()).stream().noneMatch(c -> c.templateId().equals(template.id().toString())),
                    "A house was laid over the planks");
            RemoteWorks.cancel(server, null, kingdom.id(), order.id());
            helper.assertTrue(stands(level, point, Blocks.OAK_PLANKS), "The planks are gone");
            CitizenGameTests.finish(helper, kingdom);
            BuildingTemplates.removeForTest(template.id());
        });
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_remote_order_post", timeoutTicks = 6_000)
    public static void aPostFromAfarHiresTheUnemployed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Posts From Afar", UUID.randomUUID());
        Citizen idle = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
        StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 4);
        store.insertItem(0, new ItemStack(ModRegistries.WORK_POST_ITEM.get()), false);
        // The citizen lives in the capital; a free bed waits in the far city.
        WorldPos capitalBed = LifeGameTests.bed(helper, kingdom, new BlockPos(3, 0, 6));
        helper.assertTrue(KingdomWorldApi.change(server, "test", state -> state.citizens().assignHome(idle, capitalBed)
                ? io.github.sekerator.kingdomworld.core.kingdom.Outcome.DONE
                : io.github.sekerator.kingdomworld.core.kingdom.Outcome.fail(io.github.sekerator.kingdomworld.core.kingdom.KingdomError.UNCHANGED)).ok(),
                "The citizen got no home in the capital");
        BlockPos hall = new BlockPos(helper.absolutePos(BlockPos.ZERO).getX() + FAR, HIGH + 1, helper.absolutePos(BlockPos.ZERO).getZ());
        meadow(helper, kingdom, hall, 20, at -> {
        });
        long city = farCity(helper, kingdom, hall, "Postville");
        WorldPos farBed = new WorldPos(Dimensions.id(level), hall.getX() + 6, hall.getY(), hall.getZ() + 6);
        KingdomWorldApi.state(server).citizens().addSite(farBed, io.github.sekerator.kingdomworld.core.citizen.SiteKinds.BED, kingdom.id());
        helper.assertTrue(RemoteWorks.orderPost(server, null, kingdom.id(), city, Commission.Origin.KING).ok(), "The post was refused");
        UUID id = idle.id();
        helper.succeedWhen(() -> {
            KingdomWorldState state = KingdomWorldApi.state(server);
            helper.assertTrue(state.construction().commissionsOf(kingdom.id()).isEmpty(), "The post is not set up yet: "
                    + state.construction().commissionsOf(kingdom.id()));
            WorkPost post = state.citizens().posts().postsOf(kingdom.id(), Professions.BUILDER.id()).stream()
                    .filter(found -> Math.abs(found.pos().x() - hall.getX()) <= 12 && Math.abs(found.pos().z() - hall.getZ()) <= 12).findFirst()
                    .orElseThrow(() -> new GameTestAssertException("No post of builders by the far town hall"));
            helper.assertTrue(post.workers().contains(id), "The citizen without work was not hired for the far post");
            Citizen hired = state.citizens().get(id).orElseThrow();
            helper.assertTrue(farBed.equals(hired.home()), "The citizen did not move to the bed of the city of its post: " + hired.home());
            helper.assertTrue(state.citizens().sites().get(capitalBed).map(site -> site.isFree()).orElse(false),
                    "The bed in the capital was not left");
            helper.assertTrue(CitizenGameTests.count(store, ModRegistries.WORK_POST_ITEM.get()) == 0, "The work post was not taken from the storehouse");
            helper.assertTrue(stands(level, new BlockPos(post.pos().x(), post.pos().y(), post.pos().z()), ModRegistries.WORK_POST.get()),
                    "No work post stands by the far town hall");
            RoadWork.state(server).removeCity(city);
            CitizenGameTests.finish(helper, kingdom, idle);
        });
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_remote_found", timeoutTicks = 6_000)
    public static void aCityFoundedFromTheMapTakesItsTownHallFromTheStorehouse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Founding", UUID.randomUUID());
        StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 4);
        store.insertItem(0, new ItemStack(io.github.sekerator.kingdomworld.common.road.RoadRegistries.TOWN_HALL_ITEM.get()), false);
        BlockPos middle = new BlockPos(helper.absolutePos(BlockPos.ZERO).getX() + FAR, HIGH + 1, helper.absolutePos(BlockPos.ZERO).getZ());
        meadow(helper, kingdom, middle, 20, at -> {
        });
        String dimension = Dimensions.id(level);
        // Off the land of the kingdom nothing is ordered.
        helper.assertTrue(!io.github.sekerator.kingdomworld.common.construction.RemoteHalls.order(server, null, kingdom.id(), dimension,
                (middle.getX() >> 4) + 40, middle.getZ() >> 4, "Nowhere", false).ok(), "A city was ordered off the land");
        helper.assertTrue(io.github.sekerator.kingdomworld.common.construction.RemoteHalls.order(server, null, kingdom.id(), dimension,
                middle.getX() >> 4, middle.getZ() >> 4, "Hallville", false).ok(), "The city was refused");
        helper.succeedWhen(() -> {
            KingdomWorldState state = KingdomWorldApi.state(server);
            helper.assertTrue(state.construction().commissionsOf(kingdom.id()).isEmpty(), "The city is not founded yet: "
                    + state.construction().commissionsOf(kingdom.id()));
            io.github.sekerator.kingdomworld.core.road.City city = RoadWork.state(server).citiesOf(kingdom.id()).stream()
                    .filter(found -> found.name().equals("Hallville")).findFirst()
                    .orElseThrow(() -> new GameTestAssertException("No city Hallville"));
            helper.assertTrue(city.x() >> 4 == middle.getX() >> 4 && city.z() >> 4 == middle.getZ() >> 4, "The city stands in another chunk");
            helper.assertTrue(city.y() == HIGH + 1, "The town hall does not stand on the meadow: " + city.y());
            helper.assertTrue(CitizenGameTests.count(store, io.github.sekerator.kingdomworld.common.road.RoadRegistries.TOWN_HALL_ITEM.get()) == 0,
                    "The town hall was not taken from the storehouse");
            BlockPos hall = new BlockPos(city.x(), city.y(), city.z());
            helper.assertTrue(stands(level, hall, io.github.sekerator.kingdomworld.common.road.RoadRegistries.TOWN_HALL.get()),
                    "No town hall stands in the chunk");
            RoadWork.state(server).removeCity(city.id());
            TestChunks.hold(level, hall, hall);
            level.setBlock(hall, Blocks.AIR.defaultBlockState(), 3);
            TestChunks.release(level, hall);
            CitizenGameTests.finish(helper, kingdom);
        });
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_remote_order_limit", timeoutTicks = 200)
    public static void tenConstructionsOrderWhatTheyLackAndTheKingStillOrders(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Many Cities", UUID.randomUUID());
        BuildingTemplate template = house("many_house");
        try {
            int ordered = 0;
            for (int i = 0; i < 10; i++) {
                BlockPos anchor = new BlockPos(helper.absolutePos(BlockPos.ZERO).getX() + FAR + i * 200, HIGH, helper.absolutePos(BlockPos.ZERO).getZ());
                Construction construction = far(helper, kingdom, template, anchor, false);
                construction.setOrderMissing(true);
                if (ConstructionWork.orderItem(server, construction, "minecraft:oak_planks", 64)) {
                    ordered++;
                }
            }
            helper.assertTrue(ordered == 10, "Not every construction could order what it lacks: " + ordered);
            KingdomWorldState state = KingdomWorldApi.state(server);
            helper.assertTrue(state.kingOrders(kingdom.id()) == 0, "The orders of the constructions count against the king");
            // The king may hold one order of his own: the ten of the constructions do not take its place.
            KingdomWorldState.Ordering king = state.order(null, kingdom.id(), "minecraft:bread", Order.Kind.MAKE, 1, Order.Priority.NORMAL,
                    KingdomWorldApi.gameTime(server), 1, found -> Production.orderable(server, found));
            helper.assertTrue(king.outcome().ok(), "The order of the king was refused: " + king.outcome());
            helper.assertTrue(state.kingOrders(kingdom.id()) == 1, "The order of the king is not counted");
        } finally {
            for (Construction construction : List.copyOf(KingdomWorldApi.state(server).construction().of(kingdom.id()))) {
                ConstructionWork.cancel(server, null, kingdom.id(), construction.id());
            }
            CitizenGameTests.finish(helper, kingdom);
            BuildingTemplates.removeForTest(template.id());
        }
        helper.succeed();
    }

    /**
     * A construction laid out far away from the data, paid for or not.
     */
    static Construction far(GameTestHelper helper, Kingdom kingdom, BuildingTemplate template, BlockPos anchor, boolean paid) {
        if (paid) {
            return paidFar(helper, kingdom, template, anchor);
        }
        MinecraftServer server = helper.getLevel().getServer();
        Box box = template.box(anchor, Placement.NONE);
        claim(server, kingdom, box.minX(), box.minZ(), box.maxX(), box.maxZ());
        BuildPlan plan = ConstructionWork.plan(template, anchor, Placement.NONE, List.of());
        KingdomWorldState.ConstructionChange change = KingdomWorldApi.state(server).layConstruction(null, kingdom.id(), template.id().toString(),
                template.fingerprint(), new WorldPos(Dimensions.id(helper.getLevel()), anchor.getX(), anchor.getY(), anchor.getZ()), Placement.NONE,
                box, List.of(), plan.size(), KingdomWorldApi.gameTime(server), ConstructionSettings.rules());
        helper.assertTrue(change.construction().isPresent(), "The far construction was refused: " + change.outcome());
        return change.construction().get();
    }
}
