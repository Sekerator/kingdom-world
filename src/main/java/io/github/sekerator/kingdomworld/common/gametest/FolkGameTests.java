package io.github.sekerator.kingdomworld.common.gametest;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSimulation;
import io.github.sekerator.kingdomworld.common.citizen.FolkSettings;
import io.github.sekerator.kingdomworld.common.road.CityAreas;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.CityCharter;
import io.github.sekerator.kingdomworld.core.road.FolkRules;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Game tests of the people of the cities of a kingdom of players: every city takes its own newcomers into its own beds, a city
 * full of named citizens grows by townsfolk who eat from the storehouses and pay their dues, and a city short of named citizens
 * takes its next newcomer out of its townsfolk.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FolkGameTests {
    private static final String EMPTY = "empty";

    private FolkGameTests() {
    }

    /**
     * A bed of the kingdom at a place given relative to the test, its two halves in the world and known to the kingdom.
     */
    private static WorldPos bed(GameTestHelper helper, Kingdom kingdom, int x, int z) {
        ServerLevel level = helper.getLevel();
        BlockPos head = helper.absolutePos(new BlockPos(x, 0, z));
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
        level.setBlockAndUpdate(head.west(), bed.setValue(BedBlock.PART, BedPart.FOOT));
        level.setBlockAndUpdate(head, bed.setValue(BedBlock.PART, BedPart.HEAD));
        WorldPos pos = CitizenGameTests.at(helper, x, 0, z);
        KingdomWorldApi.state(level.getServer()).citizens().addSite(pos, SiteKinds.BED, kingdom.id());
        return pos;
    }

    private static List<Citizen> citizens(MinecraftServer server, Kingdom kingdom) {
        return KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdom.id());
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_folk_cities")
    public static void everyCityTakesItsNewcomersIntoItsOwnBeds(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Two Towns", UUID.randomUUID());
        try {
            CitizenGameTests.storehouse(helper, 0, 0, 2).insertItem(0, new ItemStack(Items.BREAD, 64), false);
            // A city some thirty blocks from the throne, its town hall and one free bed beside it; the capital has no bed yet.
            WorldPos hall = CitizenGameTests.at(helper, 32, 0, 2);
            RoadState roads = RoadWork.state(server);
            City city = roads.addCity(kingdom.id(), "Ferry", hall.dimension(), hall.x(), hall.y(), hall.z(), KingdomWorldApi.gameTime(server));
            CityAreas.invalidate();
            WorldPos far = bed(helper, kingdom, 33, 5);
            CitizenSimulation.step(server, kingdom.id());
            List<Citizen> people = citizens(server, kingdom);
            helper.assertTrue(people.size() == 1, "Expected one newcomer for the city, got " + people.size());
            helper.assertTrue(far.equals(people.get(0).home()), "The newcomer of the city did not move into its bed: " + people.get(0).home());
            long now = KingdomWorldApi.gameTime(server);
            helper.assertTrue(roads.charter(kingdom.id(), city.id()).map(CityCharter::lastArrival).orElse(CityCharter.NEVER) == now,
                    "The city does not remember its newcomer");
            // The capital gets a bed: its newcomer comes at once, the city's newcomer a moment ago is no reason to wait.
            WorldPos home = bed(helper, kingdom, 4, 5);
            CitizenSimulation.step(server, kingdom.id());
            people = citizens(server, kingdom);
            helper.assertTrue(people.size() == 2, "The capital did not take its own newcomer: " + people.size());
            helper.assertTrue(people.stream().anyMatch(citizen -> home.equals(citizen.home())), "The newcomer of the capital has no bed there");
            CitizenSimulation.step(server, kingdom.id());
            helper.assertTrue(citizens(server, kingdom).size() == 2, "A newcomer came without a free bed");
        } finally {
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_folk_numbers")
    public static void aFullCityGrowsByTownsfolkWhoEatAndPay(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Folk", UUID.randomUUID());
        // One named citizen a city, two coins a head a day.
        FolkSettings.override(new FolkRules(1, 4, 8, 2));
        try {
            StorehouseInventory storehouse = CitizenGameTests.storehouse(helper, 0, 0, 2);
            storehouse.insertItem(0, new ItemStack(Items.BREAD, 64), false);
            bed(helper, kingdom, 4, 5);
            bed(helper, kingdom, 6, 5);
            CitizenSimulation.step(server, kingdom.id());
            helper.assertTrue(citizens(server, kingdom).size() == 1, "The first newcomer is a named citizen");
            RoadState roads = RoadWork.state(server);
            CityCharter capital = roads.charter(kingdom.id(), 0).orElseThrow(() -> new IllegalStateException("The capital keeps nothing"));
            // Long enough ago: the next newcomer is due.
            capital.setLastArrival(KingdomWorldApi.gameTime(server) - 10L * CitizenNeeds.TICKS_PER_DAY);
            CitizenSimulation.step(server, kingdom.id());
            helper.assertTrue(citizens(server, kingdom).size() == 1, "A named citizen came beyond the quota");
            helper.assertTrue(capital.folk() == 1, "The newcomer beyond the quota did not stay townsfolk: " + capital.folk());
            // A new day of the townsfolk: they eat bread and pay.
            Treasury treasury = KingdomWorldApi.treasury(server, kingdom.id()).orElseThrow();
            long balance = treasury.balance();
            int bread = CitizenGameTests.count(storehouse, Items.BREAD);
            capital.setFolk(3);
            capital.setLastDay(Math.floorDiv(KingdomWorldApi.gameTime(server), CitizenNeeds.TICKS_PER_DAY) - 1);
            CitizenSimulation.step(server, kingdom.id());
            helper.assertTrue(CitizenGameTests.count(storehouse, Items.BREAD) < bread, "The townsfolk ate nothing");
            helper.assertTrue(treasury.balance() >= balance + 6, "The townsfolk did not pay their dues: " + (treasury.balance() - balance));
            helper.assertTrue(capital.folk() >= 3 && capital.folk() <= 4, "The townsfolk of one free bed are a family at most: " + capital.folk());
            helper.assertTrue(citizens(server, kingdom).size() == 1, "Townsfolk became named citizens");
        } finally {
            FolkSettings.override(null);
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_folk_back")
    public static void aCityShortOfNamedCitizensTakesThemOutOfItsTownsfolk(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Folk Back", UUID.randomUUID());
        FolkSettings.override(new FolkRules(2, 4, 8, 1));
        try {
            CitizenGameTests.storehouse(helper, 0, 0, 2).insertItem(0, new ItemStack(Items.BREAD, 64), false);
            WorldPos home = bed(helper, kingdom, 4, 5);
            CityCharter capital = RoadWork.state(server).charterOf(kingdom.id(), 0, CityCharter.NEVER);
            capital.setFolk(5);
            CitizenSimulation.step(server, kingdom.id());
            List<Citizen> people = citizens(server, kingdom);
            helper.assertTrue(people.size() == 1 && home.equals(people.get(0).home()), "No named citizen came out of the townsfolk");
            helper.assertTrue(capital.folk() == 4, "The townsfolk did not lose the one who became a citizen: " + capital.folk());
        } finally {
            FolkSettings.override(null);
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }
}
