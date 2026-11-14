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
            helper.assertTrue(theirs.maxRoads() >= 200 && theirs.maxCities() == 0, "A state keeps the limits of players: " + theirs.maxRoads()
                    + " roads, " + theirs.maxCities() + " cities");
            helper.succeed();
        } finally {
            forget(server, state);
            KingdomWorldApi.dissolveKingdom(server, players.id());
        }
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_land_planned_road", timeoutTicks = 2_400)
    public static void aRoadOfThePlanIsLaidOnceBetweenStandingSettlements(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos start = helper.absolutePos(new BlockPos(1, 0, 1));
        // The road is searched on the surface of the world, far above the test: its line goes where that is dry land.
        int[] dry = SurfaceLand.near(helper.getLevel(), start.getX(), start.getZ(), (x, z) -> dryLine(helper.getLevel(), x, z));
        helper.assertTrue(dry != null, "No dry land for the road of the plan within " + SurfaceLand.RINGS * SurfaceLand.STEP + " blocks of the test");
        Kingdom state = worldKingdom(server, "GameTest Road Plan State");
        int x0 = dry[0];
        int z0 = dry[1];
        Settlement town = NpcWork.state(server).addSettlement(state.id(), 0, 0, Tier.TOWN, x0, z0, 11, new NpcNames.Name("Roadton", "Дорогин"));
        Settlement village = NpcWork.state(server).addSettlement(state.id(), 0, town.id(), Tier.VILLAGE, x0 + 120, z0 + 40, 12,
                new NpcNames.Name("Lanesby", "Тропинск"));
        town.setStatus(Settlement.Status.STANDING);
        village.setStatus(Settlement.Status.STANDING);
        PlannedRoad planned = NpcWork.state(server).addPlannedRoad(town.id(), village.id(), RoadClass.PATH, new int[]{x0, x0 + 60, x0 + 120},
                new int[]{z0, z0 + 25, z0 + 40});
        helper.assertTrue(PlannedRoads.start(server, planned).ok(), "The search for the road of the plan did not start");
        // The settlements and the road go whether the test passes or fails: a town left standing would make the land of the
        // tests after this one its own.
        Runnable cleanUp = () -> {
            RoadWork.change(server, "forget the roads of a test", roads -> {
                roads.forgetKingdom(state.id());
                return io.github.sekerator.kingdomworld.core.kingdom.Outcome.DONE;
            });
            forget(server, state);
        };
        waitForRoad(helper, 0, 2_200, planned, state, cleanUp);
    }

    /**
     * The line of the road of the plan of the test (120 blocks east and 40 south) runs over dry land of nobody without a cliff.
     */
    private static boolean dryLine(ServerLevel level, int x0, int z0) {
        int[] xs = {x0, x0 + 60, x0 + 120};
        int[] zs = {z0, z0 + 25, z0 + 40};
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int i = 0; i + 1 < xs.length; i++) {
            int steps = (int) Math.ceil(Math.hypot(xs[i + 1] - xs[i], zs[i + 1] - zs[i]) / 4.0);
            for (int k = 0; k <= steps; k++) {
                int y = SurfaceLand.dry(level, xs[i] + (xs[i + 1] - xs[i]) * k / steps, zs[i] + (zs[i + 1] - zs[i]) * k / steps);
                if (y == Integer.MIN_VALUE) {
                    return false;
                }
                low = Math.min(low, y);
                high = Math.max(high, y);
                if (high - low > 12) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void waitForRoad(GameTestHelper helper, int tick, int limit, PlannedRoad planned, Kingdom state, Runnable cleanUp) {
        MinecraftServer server = helper.getLevel().getServer();
        PlannedRoad now = NpcWork.state(server).plannedRoads().stream().filter(road -> road.id() == planned.id()).findFirst().orElse(null);
        if (now != null && now.roadId() == 0 && tick < limit) {
            helper.runAfterDelay(1, () -> waitForRoad(helper, tick + 1, limit, planned, state, cleanUp));
            return;
        }
        try {
            helper.assertTrue(now != null, "The road of the plan is gone");
            helper.assertTrue(now.roadId() != 0, "The road of the plan is not laid in " + limit + " ticks; the land along its line: "
                    + land(helper.getLevel(), planned));
            Road road = RoadWork.state(server).road(now.roadId()).orElse(null);
            helper.assertTrue(road != null, "The road of the plan is not in the register");
            helper.assertTrue(road.paid() == road.units(), "The road of the plan is not paid whole");
            long count = RoadWork.state(server).roads().stream().filter(other -> other.kingdomId().equals(state.id())).count();
            helper.assertTrue(count == 1, "The road of the plan was laid " + count + " times");
        } finally {
            cleanUp.run();
        }
        helper.succeed();
    }

    /** The ground every ten blocks of the line of a road of the plan, for the message of a failure. */
    private static String land(ServerLevel level, PlannedRoad planned) {
        StringBuilder text = new StringBuilder();
        int[] xs = planned.xs();
        int[] zs = planned.zs();
        for (int i = 0; i + 1 < xs.length; i++) {
            int steps = Math.max(1, (int) Math.hypot(xs[i + 1] - xs[i], zs[i + 1] - zs[i]) / 10);
            for (int k = 0; k <= steps; k++) {
                int x = xs[i] + (xs[i + 1] - xs[i]) * k / steps;
                int z = zs[i] + (zs[i + 1] - zs[i]) * k / steps;
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    text.append(x).append(',').append(z).append(" not loaded; ");
                    continue;
                }
                BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
                UUID owner = KingdomWorldApi.ownerOfChunk(level, x >> 4, z >> 4);
                text.append(x).append(',').append(z).append(" y ").append(top.getY() - 1).append(' ')
                        .append(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(level.getBlockState(top.below()).getBlock()).getPath())
                        .append(owner == null ? "" : " of " + KingdomWorldApi.kingdom(level.getServer(), owner).map(Kingdom::name).orElse(owner.toString()))
                        .append("; ");
            }
        }
        return text.toString();
    }
}
