package io.github.sekerator.kingdomworld.common.gametest;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSettings;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSimulation;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSites;
import io.github.sekerator.kingdomworld.common.road.RoadActions;
import io.github.sekerator.kingdomworld.common.road.RoadSettings;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenRules;
import io.github.sekerator.kingdomworld.core.citizen.Citizens;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.road.RoadRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Game tests of a kingdom of players without limits of the server: it founds more cities than the old limit of six, and a
 * newcomer comes to a kingdom of more citizens than the old limit of two hundred while it has a bed and food. Each test founds
 * its kingdom in a batch of its own.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingdomCapsGameTests {
    private static final String EMPTY = "empty";

    private KingdomCapsGameTests() {
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_caps_cities")
    public static void aKingdomOfPlayersFoundsSevenCitiesAndMore(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(16, 3, 16));
        GatheringGameTests.clear(level, from, to);
        UUID king = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Many Cities", king);
        try {
            // The limit of the configuration as the server reads it: none by default, and none for the old default of six.
            RoadRules.Builder rules = RoadSettings.configuredRoads();
            helper.assertTrue(rules.maxCities == 0 && rules.maxRoads == 0, "The server has limits: " + rules.maxCities + " cities, "
                    + rules.maxRoads + " roads");
            // The cities stand closer than on a real server, so that eight of them fit on the land of the test.
            rules.minCityDistance = 4;
            RoadSettings.override(rules.build(), null);
            int[][] places = {{1, 6}, {6, 6}, {11, 6}, {1, 11}, {6, 11}, {11, 11}, {1, 15}, {6, 15}};
            for (int i = 0; i < places.length; i++) {
                BlockPos hall = helper.absolutePos(new BlockPos(places[i][0], 0, places[i][1]));
                Outcome city = RoadWork.foundCity(level, null, kingdom.id(), hall, "City " + (i + 1));
                helper.assertTrue(city.ok(), "City " + (i + 1) + " was refused: " + city);
            }
            int cities = RoadWork.state(server).citiesOf(kingdom.id()).size();
            helper.assertTrue(cities == places.length, "The kingdom has " + cities + " cities instead of " + places.length);
            helper.assertTrue(RoadActions.tabView(server, kingdom.id(), king).map(view -> view.maxCities() == 0 && view.maxRoads() == 0)
                    .orElse(false), "The roads tab shows a limit of cities or roads");
        } finally {
            RoadSettings.override(null, null);
            CitizenGameTests.finish(helper, kingdom);
            GatheringGameTests.clear(level, from, to);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_caps_citizens", timeoutTicks = 400)
    public static void aNewcomerComesToAKingdomOfTwoHundredAndOneCitizens(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(15, 4, 15));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Two Hundred", UUID.randomUUID());
        try {
            CitizenRules rules = CitizenSettings.rules();
            helper.assertTrue(rules.maxPerKingdom() == 0, "The server limits the citizens of a kingdom to " + rules.maxPerKingdom());
            StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 2);
            for (int slot = 0; slot < 20; slot++) {
                store.insertItem(slot, new ItemStack(Items.COOKED_BEEF, 64), false);
            }
            // 210 beds in three layers of ten rows of seven, on the land around the throne.
            BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
            Set<ChunkPos> chunks = new HashSet<>();
            for (int y = 0; y < 3; y++) {
                for (int z = 4; z < 14; z++) {
                    for (int x = 0; x < 14; x += 2) {
                        helper.setBlock(new BlockPos(x, y, z), bed.setValue(BedBlock.PART, BedPart.FOOT));
                        helper.setBlock(new BlockPos(x + 1, y, z), bed.setValue(BedBlock.PART, BedPart.HEAD));
                        chunks.add(new ChunkPos(helper.absolutePos(new BlockPos(x + 1, y, z))));
                    }
                }
            }
            for (ChunkPos chunk : chunks) {
                CitizenSites.scanChunk(level, level.getChunk(chunk.x, chunk.z));
            }
            Citizens citizens = KingdomWorldApi.state(server).citizens();
            List<io.github.sekerator.kingdomworld.core.citizen.Site> beds = citizens.sites().sitesOf(kingdom.id(), SiteKinds.BED);
            helper.assertTrue(beds.size() == 210, "Beds found: " + beds.size());
            // 201 citizens, each at home in a bed of its own; nine beds stay free.
            long born = KingdomWorldApi.gameTime(server) - 30L * 24_000L;
            WorldPos throne = CitizenGameTests.at(helper, 2, 0, 2);
            for (int i = 0; i < 201; i++) {
                Citizen citizen = new Citizen(UUID.randomUUID(), kingdom.id(), 1000 + i, i % 2 == 0 ? Gender.FEMALE : Gender.MALE,
                        i % CitizenLife.SKINS, born, throne);
                citizens.add(citizen);
                helper.assertTrue(citizens.assignHome(citizen, beds.get(i).pos()), "Citizen " + i + " got no home");
            }
            helper.assertTrue(citizens.registry().count(kingdom.id()) == 201, "The kingdom has " + citizens.registry().count(kingdom.id())
                    + " citizens");
            // Without townsfolk every newcomer is a named citizen: the 202nd comes.
            io.github.sekerator.kingdomworld.common.citizen.FolkSettings.override(new io.github.sekerator.kingdomworld.core.road.FolkRules(0, 4, 8, 1));
            CitizenSimulation.step(server, kingdom.id());
            int now = citizens.registry().count(kingdom.id());
            helper.assertTrue(now == 202, "No newcomer came to the kingdom of 201 citizens with free beds and food: " + now);
            // With the quota of named citizens a city of so many grows by its townsfolk instead, and still grows.
            io.github.sekerator.kingdomworld.common.citizen.FolkSettings.override(null);
            io.github.sekerator.kingdomworld.core.road.RoadState roads = io.github.sekerator.kingdomworld.common.road.RoadWork.state(server);
            roads.charter(kingdom.id(), 0).ifPresent(charter -> charter.setLastArrival(io.github.sekerator.kingdomworld.core.road.CityCharter.NEVER));
            CitizenSimulation.step(server, kingdom.id());
            helper.assertTrue(citizens.registry().count(kingdom.id()) == 202 && roads.folk(kingdom.id(), 0) == 1,
                    "The capital full of named citizens did not grow by its townsfolk: " + citizens.registry().count(kingdom.id()) + " named, "
                            + roads.folk(kingdom.id(), 0) + " townsfolk");
        } finally {
            io.github.sekerator.kingdomworld.common.citizen.FolkSettings.override(null);
            CitizenGameTests.finish(helper, kingdom);
            GatheringGameTests.clear(level, from, to);
        }
        helper.succeed();
    }
}
