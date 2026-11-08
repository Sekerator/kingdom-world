package io.github.sekerator.kingdomworld.common.gametest;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmySettings;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.protection.ProtectionService;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.siege.SiegeGates;
import io.github.sekerator.kingdomworld.common.siege.SiegeSettings;
import io.github.sekerator.kingdomworld.common.siege.SiegeWork;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.diplomacy.War;
import io.github.sekerator.kingdomworld.core.diplomacy.WarGoal;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.protection.ProtectedAction;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.siege.Conquest;
import io.github.sekerator.kingdomworld.core.siege.Gate;
import io.github.sekerator.kingdomworld.core.siege.Hold;
import io.github.sekerator.kingdomworld.core.siege.Siege;
import io.github.sekerator.kingdomworld.core.siege.SiegeRules;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * War in the world: the land of an enemy opens in war (blocks and chests) but not the land of a third kingdom, of a shielded
 * enemy or the throne; gates close before enemy soldiers, open before their own, and fall to soldiers with a ram in the
 * expected time; holding a square far from players takes a city and defenders interrupt it; an annexation moves money and
 * citizens without making any; builders mend war damage from the storehouse after the war. Every test fences its place:
 * everything it builds stays in the cleared box of the template.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SiegeGameTests {
    private static final String EMPTY = "empty";

    private SiegeGameTests() {
    }

    private static void finish(GameTestHelper helper, Kingdom home, List<Kingdom> others, List<UUID> players, List<Long> walls,
                               Citizen... citizens) {
        MinecraftServer server = helper.getLevel().getServer();
        SiegeSettings.override(null);
        for (long wall : walls) {
            RoadWork.state(server).removeWall(wall);
            SiegeWork.state(server).removeGatesOf(wall);
        }
        SiegeWork.state(server).forget(home.id());
        for (Kingdom other : others) {
            SiegeWork.state(server).forget(other.id());
            for (City city : List.copyOf(RoadWork.state(server).citiesOf(other.id()))) {
                RoadWork.state(server).removeCity(city.id());
            }
        }
        for (City city : List.copyOf(RoadWork.state(server).citiesOf(home.id()))) {
            RoadWork.state(server).removeCity(city.id());
        }
        WarGameTests.end(helper, home, others, players, citizens);
    }

    /**
     * A ring of a palisade around the place of the test, owned by a kingdom, with a gate in the middle of its north side:
     * cells x 0..5 by z 0..5 on the ground of the template, the gate at x 2 (leaves at x 1..3, z 0).
     */
    private static Wall ring(GameTestHelper helper, UUID kingdomId, long id) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        List<RouteCell> cells = new ArrayList<>();
        for (int x = 0; x <= 5; x++) {
            cells.add(new RouteCell(origin.getX() + x, origin.getY(), origin.getZ(), RouteCell.Kind.GROUND, 0, null));
        }
        for (int z = 1; z <= 5; z++) {
            cells.add(new RouteCell(origin.getX() + 5, origin.getY(), origin.getZ() + z, RouteCell.Kind.GROUND, 0, null));
        }
        for (int x = 4; x >= 0; x--) {
            cells.add(new RouteCell(origin.getX() + x, origin.getY(), origin.getZ() + 5, RouteCell.Kind.GROUND, 0, null));
        }
        for (int z = 4; z >= 1; z--) {
            cells.add(new RouteCell(origin.getX(), origin.getY(), origin.getZ() + z, RouteCell.Kind.GROUND, 0, null));
        }
        Wall wall = new Wall(id, 0, kingdomId, Wall.WallClass.PALISADE, Dimensions.id(helper.getLevel()), origin.getX() + 2, origin.getZ() + 2,
                cells, List.of(2), KingdomWorldApi.gameTime(helper.getLevel().getServer()));
        RoadWork.state(helper.getLevel().getServer()).putWall(wall);
        return wall;
    }

    private static ArmySquad squad(GameTestHelper helper, Kingdom kingdom, Citizen... soldiers) {
        MinecraftServer server = helper.getLevel().getServer();
        List<UUID> members = new ArrayList<>();
        for (Citizen soldier : soldiers) {
            members.add(soldier.id());
        }
        ArmyWork.change(server, "form a squad for a check", state -> state.formSquad(null, kingdom.id(), members, ArmySettings.rules(),
                KingdomWorldApi.state(server)).outcome());
        return ArmyWork.state(server).squadsOf(kingdom.id()).stream().filter(s -> s.members().contains(soldiers[0].id())).findFirst()
                .orElseThrow(() -> new IllegalStateException("No squad was formed"));
    }

    private static CitizenEntity body(GameTestHelper helper, Citizen citizen, double x, double z) {
        MinecraftServer server = helper.getLevel().getServer();
        CitizenBodies.force(citizen.id(), true);
        CitizenBodies.update(server);
        CitizenEntity body = CitizenBodies.body(citizen.id());
        helper.assertTrue(body != null, "The citizen got no body");
        BlockPos at = helper.absolutePos(BlockPos.ZERO);
        body.moveTo(at.getX() + x, at.getY() + 1, at.getZ() + z, 0, 0);
        body.getNavigation().stop();
        return body;
    }

    // ---------------------------------------------------------------- open land

    @GameTest(template = EMPTY, batch = "kingdomworld_siege_land")
    public static void warOpensTheLandOfAnEnemyButNotOfAThirdKingdomAShieldedEnemyOrTheThrone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom[] both = WarGameTests.kingdoms(helper, "Siege Land", UUID.randomUUID(), UUID.randomUUID());
        Kingdom home = both[0];
        Kingdom enemy = both[1];
        Kingdom third = KingdomWorldApi.kingdoms(server).stream().filter(k -> k.name().equals("GameTest Third Siege")).findFirst()
                .orElseGet(() -> ((io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult.Created) KingdomWorldApi.createKingdom(server,
                        "GameTest Third Siege", UUID.randomUUID())).kingdom());
        UUID raiderId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        ServerPlayer raider = WarGameTests.player(helper, raiderId, "kw-raider", 4, 4);
        ServerPlayer stranger = WarGameTests.player(helper, strangerId, "kw-stranger", 4, 3);
        WarGameTests.join(server, enemy, raiderId);
        WarGameTests.join(server, third, strangerId);
        BlockPos wallBlock = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos chest = helper.absolutePos(new BlockPos(4, 1, 2));
        BlockPos throne = helper.absolutePos(new BlockPos(2, 0, 2));
        level.setBlockAndUpdate(wallBlock, Blocks.STONE_BRICKS.defaultBlockState());
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        try {
            helper.assertTrue(!ProtectionService.isAllowed(raider, level, wallBlock, ProtectedAction.BREAK), "A foreigner breaks blocks in peace");
            helper.assertTrue(!ProtectionService.isAllowed(raider, level, chest, ProtectedAction.CONTAINER), "A foreigner robs a chest in peace");
            WarGameTests.war(helper, enemy, home, WarGoal.CITY);
            helper.assertTrue(ProtectionService.isAllowed(raider, level, wallBlock, ProtectedAction.BREAK), "An enemy may not break blocks in war");
            helper.assertTrue(ProtectionService.isAllowed(raider, level, wallBlock, ProtectedAction.PLACE), "An enemy may not place blocks in war");
            helper.assertTrue(ProtectionService.isAllowed(raider, level, chest, ProtectedAction.CONTAINER), "An enemy may not rob a chest in war");
            helper.assertTrue(!ProtectionService.isAllowed(stranger, level, wallBlock, ProtectedAction.BREAK),
                    "A third kingdom breaks blocks on land at war with another");
            BlockEvent.BreakEvent onThrone = new BlockEvent.BreakEvent(level, throne, level.getBlockState(throne), raider);
            MinecraftForge.EVENT_BUS.post(onThrone);
            helper.assertTrue(level.getBlockState(throne).is(ModRegistries.THRONE.get()) && onThrone.isCanceled(), "The throne broke in war");
            BlockPos spawner = helper.absolutePos(new BlockPos(5, 1, 4));
            level.setBlockAndUpdate(spawner, Blocks.SPAWNER.defaultBlockState());
            BlockEvent.BreakEvent onSpawner = new BlockEvent.BreakEvent(level, spawner, level.getBlockState(spawner), raider);
            MinecraftForge.EVENT_BUS.post(onSpawner);
            helper.assertTrue(onSpawner.isCanceled(), "A block the builders never clear broke in war");
            // The defenders go away from the keyboard: after the delay their land is shielded and shut again.
            WarWork.markPresent(home.id(), false);
            long now = KingdomWorldApi.gameTime(server);
            WarWork.state(server).presence().restore(home.id(), now - DiplomacyRulesHolder.absence(), Long.MIN_VALUE / 4);
            helper.assertTrue(WarWork.shielded(server, home.id()), "The defenders away from the keyboard are not shielded");
            helper.assertTrue(!ProtectionService.isAllowed(raider, level, wallBlock, ProtectedAction.BREAK),
                    "An enemy breaks the land of defenders who are away");
        } finally {
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(5, 1, 4)), Blocks.AIR.defaultBlockState());
            WarWork.markPresent(third.id(), null);
            WarWork.state(server).forget(third.id(), KingdomWorldApi.gameTime(server));
            finish(helper, home, List.of(enemy, third), List.of(raiderId, strangerId), List.of());
        }
        helper.succeed();
    }

    /**
     * The delay of the shield of 9a, read once.
     */
    private static final class DiplomacyRulesHolder {
        static long absence() {
            return io.github.sekerator.kingdomworld.common.diplomacy.DiplomacySettings.rules().absenceTicks();
        }
    }

    // ---------------------------------------------------------------- gates

    @GameTest(template = EMPTY, batch = "kingdomworld_siege_gate", timeoutTicks = 200)
    public static void gatesCloseBeforeEnemiesOpenBeforeTheirOwnAndFallToSoldiersWithARam(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom[] both = WarGameTests.kingdoms(helper, "Siege Gate", UUID.randomUUID(), UUID.randomUUID());
        Kingdom home = both[0];
        Kingdom enemy = both[1];
        long wallId = 9_000_000L + Math.abs(helper.absolutePos(BlockPos.ZERO).asLong() % 1_000_000L);
        // The gate belongs to the enemy; the soldiers of the home kingdom besiege it.
        Wall wall = ring(helper, enemy.id(), wallId);
        SiegeRules rules = SiegeRules.DEFAULT;
        SiegeRules quick = new SiegeRules(12, 30, rules.soldierHit(), rules.gateSlots(), rules.ramHit(), rules.maxRams(), rules.abstractShare(),
                rules.occupyTicks(), rules.plazaTicks(), rules.throneTicks(), rules.plazaRadius(), rules.throneRadius(), rules.closeRadius(),
                rules.openRadius(), rules.repairQuietTicks(), rules.explosions(), rules.maxBodies(), rules.ramPlayerFactor());
        SiegeSettings.override(quick);
        Citizen attacker = WarGameTests.soldier(helper, home, 2);
        Citizen gatekeeper = io.github.sekerator.kingdomworld.common.gametest.CitizenGameTests.newcomer(helper, enemy, 3, 3);
        List<BlockPos> leaves = SiegeGates.leafPositions(wall, 2);
        try {
            SiegeWork.forceGates(server);
            helper.assertTrue(SiegeWork.state(server).gate(wallId, 2).isPresent(), "The gate of the wall is not known");
            helper.assertTrue(SiegeWork.forceHang(server, wall, 2, true), "The leaves were not hung");
            SiegeWork.forceGates(server);
            BlockState leaf = level.getBlockState(leaves.get(2));
            helper.assertTrue(leaf.getBlock() instanceof FenceGateBlock && leaf.getValue(FenceGateBlock.OPEN), "A gate in peace is not open");
            War war = WarGameTests.war(helper, home, enemy, WarGoal.CITY);
            ArmySquad squad = squad(helper, home, attacker);
            ArmyWork.order(server, null, squad.id(), Order.siege(new WorldPos(Dimensions.id(level), leaves.get(2).getX(), leaves.get(2).getY(),
                    leaves.get(2).getZ() + 3)));
            CitizenEntity soldier = body(helper, attacker, 2.5, -2.0);
            CitizenEntity keeper = body(helper, gatekeeper, 2.5, 1.5);
            // An enemy soldier near and one of their own right at the gate: the gate opens for its own.
            soldier.moveTo(soldier.getX(), soldier.getY(), helper.absolutePos(BlockPos.ZERO).getZ() - 12.0, 0, 0);
            SiegeWork.forceGates(server);
            Gate gate = SiegeWork.state(server).gate(wallId, 2).orElseThrow();
            helper.assertTrue(!gate.closed(), "The gate did not open for its own while the enemy was not at it");
            keeper.discard();
            SiegeWork.forceGates(server);
            helper.assertTrue(gate.closed() && !level.getBlockState(leaves.get(2)).getValue(FenceGateBlock.OPEN),
                    "The gate did not close before an enemy");
            // The soldier comes to the gate with a ram of the siege: 1 a second and 3 for the ram, the leaves hold 12.
            SiegeWork.state(server).startSiege(new Siege(war.id(), home.id(), enemy.id(), 0, Dimensions.id(level), leaves.get(2).getX(),
                    leaves.get(2).getY(), leaves.get(2).getZ(), 0, 1, false));
            soldier.moveTo(leaves.get(2).getX() + 0.5, leaves.get(2).getY(), leaves.get(2).getZ() - 1.5, 0, 0);
            long expected = quick.secondsToBreak(io.github.sekerator.kingdomworld.core.siege.GateMaterial.WOOD, 1, 1);
            helper.assertTrue(expected == 3, "Leaves of 12 should hold three seconds against a soldier with a ram, not " + expected);
            for (int second = 1; second < expected; second++) {
                SiegeWork.forceGates(server);
                helper.assertTrue(!gate.breached(), "The gate broke too early, after " + second + " s");
            }
            SiegeWork.forceGates(server);
            helper.assertTrue(gate.breached(), "The gate did not break in the expected time");
            for (BlockPos at : leaves) {
                helper.assertTrue(!(level.getBlockState(at).getBlock() instanceof FenceGateBlock), "A leaf stands after the gate broke");
            }
            helper.assertTrue(!level.getEntitiesOfClass(ItemEntity.class, new AABB(leaves.get(2)).inflate(4)).isEmpty(),
                    "The broken leaves dropped nothing");
        } finally {
            for (BlockPos at : leaves) {
                level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
            }
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(leaves.get(2)).inflate(6))) {
                item.discard();
            }
            finish(helper, home, List.of(enemy), List.of(), List.of(wallId), attacker, gatekeeper);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- holds

    @GameTest(template = EMPTY, batch = "kingdomworld_siege_hold")
    public static void holdingTheSquareTakesTheCityAndDefendersInterruptIt(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom[] both = WarGameTests.kingdoms(helper, "Siege Hold", UUID.randomUUID(), UUID.randomUUID());
        Kingdom home = both[0];
        Kingdom enemy = both[1];
        BlockPos square = helper.absolutePos(new BlockPos(3, 1, 3));
        City city = RoadWork.state(server).addCity(enemy.id(), "GameTest Siege Town", Dimensions.id(helper.getLevel()), square.getX(),
                square.getY(), square.getZ(), KingdomWorldApi.gameTime(server));
        Citizen besieger = WarGameTests.soldier(helper, home, 1);
        Citizen defender = io.github.sekerator.kingdomworld.common.gametest.CitizenGameTests.newcomer(helper, enemy, 4, 4);
        SiegeSettings.override(SiegeRules.DEFAULT.withTimes(1_200, 1_200, 1_200));
        try {
            helper.assertTrue(ArmyWork.recruit(server, null, enemy.id(), defender.id(),
                    io.github.sekerator.kingdomworld.core.citizen.Professions.WARRIOR.id()).ok(), "The defender was not recruited");
            War war = WarGameTests.war(helper, home, enemy, WarGoal.CITY);
            WorldPos at = new WorldPos(city.dimension(), city.x(), city.y(), city.z());
            ArmyWork.order(server, null, squad(helper, home, besieger).id(), Order.siege(at));
            ArmySquad guards = squad(helper, enemy, defender);
            // Far from players the squads count: the besiegers hold the square while no squad of the defenders stands there.
            SiegeWork.forceHolds(server, 600);
            String key = Hold.key(Hold.Kind.PLAZA, war.id(), enemy.id(), city.dimension(), city.id());
            Hold hold = SiegeWork.state(server).hold(key).orElseThrow(() -> new IllegalStateException("The square is not held"));
            helper.assertTrue(hold.progress() == 600 && !hold.taken(), "The hold did not grow: " + hold.progress());
            helper.assertTrue(SiegeWork.state(server).siege(war.id(), enemy.id(), city.id()).isPresent(), "The siege was not recorded");
            ArmyWork.order(server, null, guards.id(), Order.defend(at));
            SiegeWork.forceHolds(server, 600);
            helper.assertTrue(hold.progress() == 0, "Defenders did not interrupt the hold");
            ArmyWork.order(server, null, guards.id(), Order.NONE);
            SiegeWork.forceHolds(server, 600);
            SiegeWork.forceHolds(server, 600);
            helper.assertTrue(hold.taken(), "Holding the square for its time did not take the city");
            // The peace for the city hands it over with its people.
            io.github.sekerator.kingdomworld.core.kingdom.Outcome ended = WarWork.end(server, war, War.Result.ADMIN,
                    io.github.sekerator.kingdomworld.core.diplomacy.PeaceTerms.forGoal(WarGoal.CITY, home.id(), true, 0,
                            io.github.sekerator.kingdomworld.common.diplomacy.DiplomacySettings.rules()));
            helper.assertTrue(ended.ok(), "The peace failed: " + ended);
            SiegeWork.forceConquests(server);
            helper.assertTrue(RoadWork.state(server).city(city.id()).map(c -> c.kingdomId().equals(home.id())).orElse(false),
                    "The city did not go to the winner");
        } finally {
            finish(helper, home, List.of(enemy), List.of(), List.of(), besieger, defender);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- annexation

    @GameTest(template = EMPTY, batch = "kingdomworld_siege_annex")
    public static void anAnnexationMovesTheTreasuryAndTheCitizensWithoutMakingAny(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom[] both = WarGameTests.kingdoms(helper, "Siege Annex", UUID.randomUUID(), UUID.randomUUID());
        Kingdom loser = both[0];
        Kingdom winner = both[1];
        Citizen first = io.github.sekerator.kingdomworld.common.gametest.CitizenGameTests.newcomer(helper, loser, 1, 1);
        Citizen second = io.github.sekerator.kingdomworld.common.gametest.CitizenGameTests.newcomer(helper, loser, 4, 1);
        KingdomWorldState world = KingdomWorldApi.state(server);
        Economy economy = world.economy();
        long day = WarWork.day(server);
        try {
            economy.adminTreasury(loser.id(), 640, day);
            long supply = economy.supply(loser.id());
            int citizens = world.citizens().registry().size();
            long fundsBefore = economy.treasury(winner.id()).orElseThrow().funds();
            long worth = economy.exchangeQuote(winner.id(), loser.id(), 640, 0);
            SiegeWork.state(server).addConquest(Conquest.Kind.ANNEX, 0, winner.id(), loser.id(), Dimensions.id(helper.getLevel()), List.of(), 0,
                    KingdomWorldApi.gameTime(server));
            long moved = economy.treasury(loser.id()).orElseThrow().funds();
            SiegeWork.forceConquests(server);
            helper.assertTrue(moved == 640, "The treasury of the loser was not full before: " + moved);
            helper.assertTrue(worth > 0 && economy.treasury(winner.id()).orElseThrow().funds() - fundsBefore == worth,
                    "The winner did not get what the treasury of the loser is worth: " + worth);
            helper.assertTrue(KingdomWorldApi.kingdom(server, loser.id()).isEmpty(), "The annexed kingdom is still there");
            helper.assertTrue(world.citizens().registry().size() == citizens, "Citizens were made or lost: "
                    + citizens + " before, " + world.citizens().registry().size() + " after");
            helper.assertTrue(world.citizens().get(first.id()).map(c -> c.kingdomId().equals(winner.id())).orElse(false)
                    && world.citizens().get(second.id()).map(c -> c.kingdomId().equals(winner.id())).orElse(false),
                    "The citizens did not become citizens of the winner");
            helper.assertTrue(supply >= 640, "The money of the loser was not counted");
        } finally {
            for (Citizen citizen : List.of(first, second)) {
                CitizenBodies.force(citizen.id(), false);
                world.citizens().registry().remove(citizen.id());
            }
            finish(helper, loser, List.of(winner), List.of(), List.of());
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- mending

    @GameTest(template = EMPTY, batch = "kingdomworld_siege_mend")
    public static void afterTheWarBuildersMendBrokenBlocksFromTheStorehouse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom[] both = WarGameTests.kingdoms(helper, "Siege Mend", UUID.randomUUID(), UUID.randomUUID());
        Kingdom home = both[0];
        Kingdom enemy = both[1];
        long wallId = 9_500_000L + Math.abs(helper.absolutePos(BlockPos.ZERO).asLong() % 1_000_000L);
        Wall wall = ring(helper, home.id(), wallId);
        BlockPos block = helper.absolutePos(new BlockPos(5, 1, 3));
        wall.markPlaced(block.asLong());
        level.setBlockAndUpdate(block, Blocks.SPRUCE_LOG.defaultBlockState());
        StorehouseInventory storehouse = CitizenGameTests.storehouse(helper, 1, 1, 4);
        UUID raiderId = UUID.randomUUID();
        ServerPlayer raider = WarGameTests.player(helper, raiderId, "kw-sapper", 4, 3);
        WarGameTests.join(server, enemy, raiderId);
        try {
            War war = WarGameTests.war(helper, enemy, home, WarGoal.CITY);
            BlockEvent.BreakEvent broken = new BlockEvent.BreakEvent(level, block, level.getBlockState(block), raider);
            MinecraftForge.EVENT_BUS.post(broken);
            helper.assertTrue(!broken.isCanceled(), "The enemy could not break a block of the wall in war");
            level.removeBlock(block, false);
            helper.assertTrue(SiegeWork.state(server).damage().count(home.id()) == 1, "The broken block of the wall was not written down");
            helper.assertTrue(SiegeWork.forceMend(server, home.id(), 10) == 0 || WarWork.state(server).fightsAnyWar(home.id()),
                    "Mending ran while the war went on");
            WarWork.end(server, war, War.Result.ADMIN, io.github.sekerator.kingdomworld.core.diplomacy.PeaceTerms.WHITE);
            helper.assertTrue(SiegeWork.forceMend(server, home.id(), 10) == 0, "A block was mended without the materials");
            storehouse.insertItem(0, new ItemStack(Items.SPRUCE_LOG, 1), false);
            helper.assertTrue(SiegeWork.forceMend(server, home.id(), 10) == 1, "The builders did not mend the block");
            helper.assertTrue(level.getBlockState(block).is(Blocks.SPRUCE_LOG), "The block was not put back");
            helper.assertTrue(storehouse.getStackInSlot(0).isEmpty(), "The block was put back without paying for it");
            helper.assertTrue(SiegeWork.state(server).damage().count(home.id()) == 0, "The mended block is still written down");
        } finally {
            level.setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
            finish(helper, home, List.of(enemy), List.of(raiderId), List.of(wallId));
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- load

    /**
     * Thirty soldiers on each side besiege and defend near one another as bodies inside a fence of barriers; the ticks of the
     * server are measured over ten seconds (the average and the worst), and the siege must not lay the server down.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_siege_load", timeoutTicks = 400)
    public static void aSiegeOfThirtyAgainstThirtyKeepsTheServerUp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom[] both = WarGameTests.kingdoms(helper, "Siege Load", UUID.randomUUID(), UUID.randomUUID());
        Kingdom home = both[0];
        Kingdom enemy = both[1];
        List<Citizen> all = new ArrayList<>();
        fence(helper, true);
        WarGameTests.war(helper, enemy, home, WarGoal.CITY);
        BlockPos center = helper.absolutePos(new BlockPos(2, 1, 2));
        WorldPos city = new WorldPos(Dimensions.id(level), center.getX(), center.getY(), center.getZ());
        for (Kingdom kingdom : List.of(home, enemy)) {
            List<Citizen> side = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                Citizen citizen = CitizenGameTests.newcomer(helper, kingdom, kingdom == home ? 1 + i % 2 : 4 + i % 2, 1 + i % 4);
                helper.assertTrue(ArmyWork.recruit(server, null, kingdom.id(), citizen.id(),
                        io.github.sekerator.kingdomworld.core.citizen.Professions.WARRIOR.id()).ok(), "A soldier was not recruited");
                side.add(citizen);
            }
            int size = ArmySettings.rules().maxSquadSize();
            for (int from = 0; from < side.size(); from += size) {
                ArmySquad squad = squad(helper, kingdom, side.subList(from, Math.min(side.size(), from + size)).toArray(Citizen[]::new));
                ArmyWork.order(server, null, squad.id(), kingdom == home ? Order.defend(city) : Order.siege(city));
            }
            for (Citizen citizen : side) {
                CitizenBodies.force(citizen.id(), true);
            }
            all.addAll(side);
        }
        CitizenBodies.update(server);
        long[] sum = new long[3];
        int start = server.getTickCount();
        helper.onEachTick(() -> {
            if (server.getTickCount() - start > 100) {
                long nanos = server.tickTimes[Math.floorMod(server.getTickCount() - 1, server.tickTimes.length)];
                sum[0] += nanos;
                sum[1]++;
                sum[2] = Math.max(sum[2], nanos);
            }
        });
        helper.runAfterDelay(300, () -> {
            try {
                long bodies = level.getEntitiesOfClass(CitizenEntity.class, new AABB(center).inflate(16)).size();
                double average = sum[1] == 0 ? 0 : sum[0] / (double) sum[1] / 1_000_000.0;
                double worst = sum[2] / 1_000_000.0;
                SiegeWork.LOGGER.info("Siege load: 30 against 30 soldiers, {} bodies at the end, {} ticks measured, average {} ms, worst {} ms",
                        bodies, sum[1], String.format(java.util.Locale.ROOT, "%.2f", average), String.format(java.util.Locale.ROOT, "%.2f", worst));
                helper.assertTrue(sum[1] > 100, "The ticks were not measured");
                helper.assertTrue(average < 50.0, "A siege of thirty against thirty took " + average + " ms a tick on average");
            } finally {
                fence(helper, false);
                finish(helper, home, List.of(enemy), List.of(), List.of(), all.toArray(Citizen[]::new));
            }
            helper.succeed();
        });
    }

    /**
     * A fence of barriers around the place of the test, so that the soldiers stay within it.
     */
    private static void fence(GameTestHelper helper, boolean up) {
        ServerLevel level = helper.getLevel();
        for (int x = -1; x <= 6; x++) {
            for (int z = -1; z <= 6; z++) {
                if (x != -1 && x != 6 && z != -1 && z != 6) {
                    continue;
                }
                for (int y = 1; y <= 3; y++) {
                    level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, z)), up ? Blocks.BARRIER.defaultBlockState()
                            : Blocks.AIR.defaultBlockState());
                }
            }
        }
    }
}
