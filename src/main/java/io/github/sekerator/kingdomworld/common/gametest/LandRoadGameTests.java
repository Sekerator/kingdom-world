package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.land.LandEvents;
import io.github.sekerator.kingdomworld.common.land.Wilderness;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.protection.ProtectionService;
import io.github.sekerator.kingdomworld.common.road.PlannedRoads;
import io.github.sekerator.kingdomworld.common.road.RoadSettings;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.land.ProvinceKind;
import io.github.sekerator.kingdomworld.core.land.Tier;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcNames;
import io.github.sekerator.kingdomworld.core.npc.PlannedRoad;
import io.github.sekerator.kingdomworld.core.npc.RulerCharacter;
import io.github.sekerator.kingdomworld.core.npc.Settlement;
import io.github.sekerator.kingdomworld.core.protection.ProtectedAction;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadClass;
import io.github.sekerator.kingdomworld.core.road.RoadRules;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.Province;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Game tests of the land of the states of the world: their wilds are free to players and their settlements are not, and the
 * roads of their plan become real roads once, between settlements that stand, with the limits of cities and roads for the
 * kingdoms of players only.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LandRoadGameTests {
    private static final String EMPTY = "empty";
    private static final int PROVINCE = 64_801;

    private LandRoadGameTests() {
    }

    private static Kingdom worldKingdom(MinecraftServer server, String name) {
        for (Kingdom stale : KingdomWorldApi.kingdoms(server)) {
            if (stale.name().equals(name)) {
                NpcWork.state(server).removeSettlementsOf(stale.id());
                NpcWork.state(server).removeKingdom(stale.id());
                KingdomWorldApi.dissolveKingdom(server, stale.id());
            }
        }
        CreateKingdomResult created = KingdomWorldApi.createKingdom(server, name, null);
        if (!(created instanceof CreateKingdomResult.Created result)) {
            throw new IllegalStateException("The kingdom was not created: " + created);
        }
        Kingdom kingdom = result.kingdom();
        NpcWork.state(server).putKingdom(new NpcKingdom(kingdom.id(), -1, NpcLevel.SMALL, new NpcNames.Name(name, name), RulerCharacter.BUILDER,
                List.of(RulerCharacter.Trait.PATIENT), 99, Gender.MALE, List.of(), KingdomWorldApi.gameTime(server), TreasuryActions.day(server)));
        return kingdom;
    }

    private static void forget(MinecraftServer server, Kingdom kingdom) {
        NpcWork.state(server).removeSettlementsOf(kingdom.id());
        NpcWork.state(server).removeKingdom(kingdom.id());
        KingdomWorldApi.dissolveKingdom(server, kingdom.id());
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_land_wilds", timeoutTicks = 100)
    public static void theWildsOfAStateAreFreeAndItsSettlementsAreNot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom state = worldKingdom(server, "GameTest Wild State");
        TerritoryIndex territory = LandEvents.attach(server);
        ProvinceMap map = territory.provinceMap().orElseThrow();
        ChunkPos at = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        BlockPos stone = helper.absolutePos(new BlockPos(2, 1, 2));
        try {
            if (map.exists(PROVINCE)) {
                territory.clearProvince(PROVINCE);
            }
            map.define(Province.plain(PROVINCE, at.getMiddleBlockX(), at.getMiddleBlockZ(), ProvinceKind.CORE));
            territory.fillProvince(PROVINCE, at.x - 3, at.z - 3, at.x + 3, at.z + 3);
            territory.transferProvince(PROVINCE, state.id(), 0);
            ServerPlayer stranger = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "kw-wanderer"));
            long chunk = ChunkKey.of(stone.getX() >> 4, stone.getZ() >> 4);
            helper.assertTrue(Wilderness.isWild(server, "minecraft:overworld", chunk) || !Wilderness.open(), "The land of the state is not wild");
            helper.assertTrue(ProtectionService.isAllowed(stranger, level, stone, ProtectedAction.BREAK)
                    && ProtectionService.isAllowed(stranger, level, stone, ProtectedAction.PLACE), "A player may not dig or build in the wilds");
            // A village stands here: the state lives in its square, and the law holds there.
            Settlement village = NpcWork.state(server).addSettlement(state.id(), PROVINCE, 0, Tier.VILLAGE, stone.getX(), stone.getZ(), 5,
                    new NpcNames.Name("Wildholm", "Дикохольм"));
            village.setStatus(Settlement.Status.STANDING);
            helper.assertTrue(!Wilderness.isWild(server, "minecraft:overworld", chunk), "The square of a standing village is wild");
            helper.assertTrue(!ProtectionService.isAllowed(stranger, level, stone, ProtectedAction.BREAK), "A player may dig in a village");
            helper.succeed();
        } finally {
            if (map.exists(PROVINCE)) {
                territory.clearProvince(PROVINCE);
            }
            forget(server, state);
        }
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_land_road_limits", timeoutTicks = 100)
    public static void theLimitsOfCitiesAndRoadsAreForPlayersOnly(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom players = CitizenGameTests.found(helper, "GameTest Road Limits", UUID.randomUUID());
        Kingdom state = worldKingdom(server, "GameTest Road State");
        try {
            RoadRules ours = RoadSettings.rulesFor(server, players.id());
            RoadRules theirs = RoadSettings.rulesFor(server, state.id());
            helper.assertTrue(ours.maxRoads() == RoadSettings.rules().maxRoads() && ours.maxCities() == RoadSettings.rules().maxCities(),
                    "The limits of a kingdom of players changed");
            helper.assertTrue(theirs.maxRoads() >= 200 && theirs.maxCities() >= 256, "A state keeps the limits of players: " + theirs.maxRoads());
            helper.succeed();
        } finally {
            forget(server, state);
            KingdomWorldApi.dissolveKingdom(server, players.id());
        }
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_land_planned_road", timeoutTicks = 2_400)
    public static void aRoadOfThePlanIsLaidOnceBetweenStandingSettlements(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom state = worldKingdom(server, "GameTest Road Plan State");
        BlockPos start = helper.absolutePos(new BlockPos(1, 0, 1));
        int x0 = start.getX();
        int z0 = start.getZ();
        Settlement town = NpcWork.state(server).addSettlement(state.id(), 0, 0, Tier.TOWN, x0, z0, 11, new NpcNames.Name("Roadton", "Дорогин"));
        Settlement village = NpcWork.state(server).addSettlement(state.id(), 0, town.id(), Tier.VILLAGE, x0 + 120, z0 + 40, 12,
                new NpcNames.Name("Lanesby", "Тропинск"));
        town.setStatus(Settlement.Status.STANDING);
        village.setStatus(Settlement.Status.STANDING);
        PlannedRoad planned = NpcWork.state(server).addPlannedRoad(town.id(), village.id(), RoadClass.PATH, new int[]{x0, x0 + 60, x0 + 120},
                new int[]{z0, z0 + 25, z0 + 40});
        helper.assertTrue(PlannedRoads.start(server, planned).ok(), "The search for the road of the plan did not start");
        helper.succeedWhen(() -> {
            PlannedRoad now = NpcWork.state(server).plannedRoads().stream().filter(road -> road.id() == planned.id()).findFirst().orElseThrow();
            if (now.roadId() == 0) {
                throw new GameTestAssertException("The road of the plan is not laid yet");
            }
            Road road = RoadWork.state(server).road(now.roadId()).orElseThrow();
            helper.assertTrue(road.paid() == road.units(), "The road of the plan is not paid whole");
            long count = RoadWork.state(server).roads().stream().filter(other -> other.kingdomId().equals(state.id())).count();
            helper.assertTrue(count == 1, "The road of the plan was laid " + count + " times");
            RoadWork.change(server, "forget the roads of a test", roads -> {
                roads.forgetKingdom(state.id());
                return io.github.sekerator.kingdomworld.core.kingdom.Outcome.DONE;
            });
            forget(server, state);
        });
    }
}
