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
import io.github.sekerator.kingdomworld.common.construction.SiteLoader;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.BuildPlan;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Game tests of building far from the capital: a house and a wall far from every player are finished by themselves, the place
 * is loaded for a while and let go again. Each test founds its kingdom in a batch of its own; the far places lie two thousand
 * blocks away, where nothing is loaded.
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
}
