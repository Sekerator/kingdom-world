package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.construction.BuildingEffects;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.economy.CoinItem;
import io.github.sekerator.kingdomworld.common.economy.Households;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.guild.GuildWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.law.LawAlarms;
import io.github.sekerator.kingdomworld.common.law.LawEvents;
import io.github.sekerator.kingdomworld.common.law.LawPunish;
import io.github.sekerator.kingdomworld.common.law.LawSettings;
import io.github.sekerator.kingdomworld.common.law.LawStandIn;
import io.github.sekerator.kingdomworld.common.law.LawVandalism;
import io.github.sekerator.kingdomworld.common.law.LawWork;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.siege.SiegeRepair;
import io.github.sekerator.kingdomworld.common.siege.SiegeWork;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.common.trade.Purses;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.BuildingRoles;
import io.github.sekerator.kingdomworld.core.construction.ConstructionBook;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.economy.Coins;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.Denomination;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.HouseholdRules;
import io.github.sekerator.kingdomworld.core.economy.Payroll;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.law.Alarm;
import io.github.sekerator.kingdomworld.core.law.Confiscation;
import io.github.sekerator.kingdomworld.core.law.Crime;
import io.github.sekerator.kingdomworld.core.law.CrimeKind;
import io.github.sekerator.kingdomworld.core.law.LawRules;
import io.github.sekerator.kingdomworld.core.law.LawState;
import io.github.sekerator.kingdomworld.core.law.Victims;
import io.github.sekerator.kingdomworld.core.law.Wanted;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.wrapper.RecipeWrapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Game tests of the law in a city: doors open to strangers, with anything in hand, and nobody is shut in; a blow frightens
 * the citizens who see or hear it and they run, soldiers do not, and the fright ends; a killing calls a soldier from forty
 * blocks away, who strikes without calling out and writes the record itself; a soldier answers a blow nobody else saw; a
 * player in creative mode is answered like any other; a block of a city breaks without drops, is a crime when seen and
 * comes back, a stranger's block is taken out again, the land outside the city is free and the land of players who are
 * away stays shut; a theft from a house is a theft from the citizen who lives there, a theft from a storehouse next to a
 * soldier makes the thief wanted at once; a share of the wages lies in the house as coins and food. Every test runs in a
 * batch of its own, in a box walled off the caves of the world, and clears what it built.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CityLawGameTests {
    private static final String EMPTY = "empty";

    private CityLawGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    private static BlockPos[] area(GameTestHelper helper, int toX, int toZ) {
        BlockPos from = helper.absolutePos(new BlockPos(-2, 0, -2));
        BlockPos to = helper.absolutePos(new BlockPos(toX, 3, toZ));
        GatheringGameTests.enclose(helper.getLevel(), from, to);
        return new BlockPos[]{from, to};
    }

    private static void noon(ServerLevel level) {
        level.setDayTime(Math.floorDiv(level.getDayTime(), 24_000L) * 24_000L + 6_000L);
    }

    /**
     * A player who stands in for a real one: found by the alarms and counted as online, though in no list of players.
     */
    private static ServerPlayer player(GameTestHelper helper, String name, int x, int z) {
        ServerPlayer player = LawStandIn.create(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        BlockPos at = helper.absolutePos(new BlockPos(x, 0, z));
        player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        ArmyWork.markOnline(player.getUUID(), true);
        LawAlarms.standInForCheck(player, true);
        return player;
    }

    /**
     * @param moving whether the body acts by itself; a still one only stands and sees
     */
    private static CitizenEntity body(GameTestHelper helper, Citizen citizen, int x, int z, boolean moving) {
        CitizenBodies.force(citizen.id(), true);
        CitizenEntity body = CitizenBodies.spawnAt(helper.getLevel(), citizen, helper.absolutePos(new BlockPos(x, 0, z)));
        helper.assertTrue(body != null, "No body for the citizen");
        body.setNoAi(!moving);
        return body;
    }

    private static Citizen soldier(GameTestHelper helper, Kingdom kingdom, int x, int z) {
        Citizen citizen = CitizenGameTests.newcomer(helper, kingdom, x, z);
        helper.assertTrue(ArmyWork.recruit(helper.getLevel().getServer(), null, kingdom.id(), citizen.id(), "warrior").ok(),
                "The citizen was not recruited");
        return citizen;
    }

    /**
     * Reports of citizens are delivered only when a test asks.
     */
    private static LawRules.Builder slowReports() {
        LawRules.Builder rules = LawSettings.configured();
        rules.reportMinTicks = 1_000_000;
        rules.reportMaxTicks = 1_000_000;
        LawSettings.override(rules.build());
        return rules;
    }

    private static boolean breakRefused(ServerLevel level, BlockPos pos, ServerPlayer player) {
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player);
        MinecraftForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }

    private static PlayerInteractEvent.RightClickBlock click(ServerPlayer player, BlockPos pos) {
        PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        MinecraftForge.EVENT_BUS.post(event);
        return event;
    }

    private static List<ItemEntity> itemsNear(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3.0));
    }

    /**
     * A house of the kingdom in the register of buildings, around a barrel and a bed.
     */
    private static Building house(GameTestHelper helper, Kingdom kingdom, BlockPos from, BlockPos to) {
        MinecraftServer server = helper.getLevel().getServer();
        ConstructionBook book = KingdomWorldApi.state(server).construction();
        long id = book.nextBuildingId();
        book.restoreCounters(0, id + 1);
        long now = KingdomWorldApi.gameTime(server);
        Building building = new Building(id, kingdom.id(), "kingdomworld:test_house", "house", "housing", BuildingRoles.HOUSE, 1, Materials.NONE,
                new WorldPos(Dimensions.id(helper.getLevel()), from.getX(), from.getY(), from.getZ()), Placement.NONE,
                new Box(from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ()), 0, now, true, 100, now, false, new java.util.BitSet());
        book.putBuilding(building);
        BuildingEffects.changed();
        return building;
    }

    private static void end(GameTestHelper helper, Kingdom kingdom, BlockPos[] area, List<ServerPlayer> players, Citizen... citizens) {
        MinecraftServer server = helper.getLevel().getServer();
        LawSettings.override(null);
        Households.override(null);
        WarWork.markPresent(kingdom.id(), null);
        for (ServerPlayer player : players) {
            ArmyWork.markOnline(player.getUUID(), false);
            LawAlarms.standInForCheck(player, false);
        }
        LawWork.deliverNow(server);
        LawState state = LawWork.state(server);
        for (Wanted wanted : List.copyOf(state.wantedIn(kingdom.id()))) {
            state.clear(kingdom.id(), wanted.player());
            LawWork.cleared(server, wanted, false, "");
        }
        for (Confiscation confiscation : List.copyOf(state.confiscationsIn(kingdom.id()))) {
            state.release(confiscation.id());
        }
        state.forgetKingdom(kingdom.id());
        SiegeWork.state(server).damage().forget(kingdom.id());
        ArmyWork.state(server).forgetKingdom(kingdom.id());
        GuildWork.state(server).forgetKingdom(kingdom.id(), TreasuryActions.day(server), KingdomWorldApi.state(server));
        for (Building building : List.copyOf(KingdomWorldApi.state(server).construction().buildingsOf(kingdom.id()))) {
            KingdomWorldApi.state(server).construction().removeBuilding(building.id());
        }
        BuildingEffects.changed();
        CitizenGameTests.finish(helper, kingdom, citizens);
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(area[0], area[1]).inflate(2.0))) {
            item.discard();
        }
        GatheringGameTests.clear(helper.getLevel(), area[0], area[1]);
    }

    // ---------------------------------------------------------------- doors

    @GameTest(template = EMPTY, batch = "kingdomworld_citylaw_doors")
    public static void aStrangerOpensDoorsWithAnythingInHandAndIsNeverShutIn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos[] area = area(helper, 9, 9);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Open Doors", UUID.randomUUID());
        ServerPlayer stranger = player(helper, "kw-guest", 3, 3);
        try {
            BlockPos door = helper.absolutePos(new BlockPos(4, 0, 6));
            BlockPos trapdoor = helper.absolutePos(new BlockPos(5, 0, 6));
            BlockPos gate = helper.absolutePos(new BlockPos(6, 0, 6));
            BlockPos lever = helper.absolutePos(new BlockPos(7, 0, 6));
            BlockPos chest = helper.absolutePos(new BlockPos(8, 0, 6));
            level.setBlock(door, Blocks.OAK_DOOR.defaultBlockState(), 2 | 16);
            level.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2 | 16);
            level.setBlock(trapdoor, Blocks.OAK_TRAPDOOR.defaultBlockState(), 2 | 16);
            level.setBlock(gate, Blocks.OAK_FENCE_GATE.defaultBlockState(), 2 | 16);
            level.setBlock(lever, Blocks.LEVER.defaultBlockState(), 2 | 16);
            level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2 | 16);
            // The kingdom has nobody at home: its blocks stay shut, its doors do not.
            for (BlockPos pos : List.of(door, door.above(), trapdoor, gate)) {
                PlayerInteractEvent.RightClickBlock opened = click(stranger, pos);
                helper.assertTrue(!opened.isCanceled() && opened.getUseBlock() != Event.Result.DENY, "A stranger cannot open " + level.getBlockState(pos));
            }
            helper.assertTrue(click(stranger, lever).getUseBlock() == Event.Result.DENY, "A stranger pulled a lever");
            // With a thing in hand the door opens all the same, and the thing itself is still not used on the land.
            stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
            for (BlockPos pos : List.of(door, trapdoor, gate, chest)) {
                PlayerInteractEvent.RightClickBlock opened = click(stranger, pos);
                helper.assertTrue(!opened.isCanceled() && opened.getUseBlock() != Event.Result.DENY,
                        "A stranger with a thing in hand cannot open " + level.getBlockState(pos));
                helper.assertTrue(opened.getUseItem() == Event.Result.DENY, "A stranger may light a fire at " + level.getBlockState(pos));
            }
            // From inside a house the way out is the same door: nothing depends on the side.
            stranger.setPos(door.getX() + 0.5, door.getY(), door.getZ() + 1.5);
            helper.assertTrue(click(stranger, door).getUseBlock() != Event.Result.DENY, "A stranger is shut in");
            // A member opens doors as before.
            ServerPlayer king = LawStandIn.create(level.getServer(), level, new GameProfile(kingdom.rulerId(), "kw-king"));
            helper.assertTrue(click(king, door).getUseBlock() != Event.Result.DENY && click(king, lever).getUseBlock() != Event.Result.DENY,
                    "The king cannot use the doors and levers of the kingdom");
        } finally {
            end(helper, kingdom, area, List.of(stranger));
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- fright

    @GameTest(template = EMPTY, batch = "kingdomworld_citylaw_fear", timeoutTicks = 500)
    public static void aBlowFrightensTheCitizensInReachAndNeverASoldier(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos[] area = area(helper, 44, 10);
        noon(level);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Frightened Town", UUID.randomUUID());
        LawRules.Builder rules = LawSettings.configured();
        rules.fearTicks = 100;
        LawSettings.override(rules.build());
        // A wall with a citizen behind it, who hears the blow and does not see it.
        for (int x = 6; x <= 14; x++) {
            for (int y = 0; y <= 2; y++) {
                level.setBlock(helper.absolutePos(new BlockPos(x, y, 7)), Blocks.STONE.defaultBlockState(), 2 | 16);
            }
        }
        Citizen struck = CitizenGameTests.newcomer(helper, kingdom, 10, 4);
        // Twelve blocks off: too far to hear the blow, near enough to see it.
        Citizen seeing = CitizenGameTests.newcomer(helper, kingdom, 22, 3);
        Citizen hearing = CitizenGameTests.newcomer(helper, kingdom, 10, 9);
        Citizen far = CitizenGameTests.newcomer(helper, kingdom, 40, 4);
        Citizen guard = soldier(helper, kingdom, 9, 1);
        CitizenEntity victim = body(helper, struck, 10, 4, true);
        CitizenEntity witness = body(helper, seeing, 22, 3, true);
        CitizenEntity listener = body(helper, hearing, 10, 9, true);
        CitizenEntity distant = body(helper, far, 40, 4, false);
        CitizenEntity soldier = body(helper, guard, 9, 1, true);
        // The attacker is in creative mode: the fright asks no game mode.
        ServerPlayer attacker = player(helper, "kw-brute", 8, 4);
        attacker.setGameMode(GameType.CREATIVE);
        Citizen[] all = {struck, seeing, hearing, far, guard};
        helper.runAfterDelay(5, () -> {
            double victimBefore = victim.distanceTo(attacker);
            double witnessBefore = witness.distanceTo(attacker);
            victim.hurt(level.damageSources().playerAttack(attacker), 1.0f);
            helper.assertTrue(victim.isFrightened(), "The citizen who was struck is not frightened");
            helper.assertTrue(witness.isFrightened(), "The citizen who saw the blow is not frightened");
            helper.assertTrue(listener.isFrightened(), "The citizen who heard the blow behind the wall is not frightened");
            helper.assertTrue(!distant.isFrightened(), "A citizen far away is frightened");
            helper.assertTrue(!soldier.isFrightened(), "A soldier is frightened");
            helper.runAfterDelay(50, () -> {
                helper.assertTrue(victim.distanceTo(attacker) > victimBefore + 3.0, "The struck citizen did not run: " + victimBefore + " -> "
                        + victim.distanceTo(attacker));
                helper.assertTrue(witness.distanceTo(attacker) > witnessBefore + 2.0, "The witness did not run: " + witnessBefore + " -> "
                        + witness.distanceTo(attacker));
                helper.assertTrue(soldier.distanceTo(attacker) < 8.0, "The soldier ran away: " + soldier.distanceTo(attacker));
                helper.assertTrue(victim.isFrightened(), "The fright ended while it should last");
            });
            helper.runAfterDelay(140, () -> {
                helper.assertTrue(!victim.isFrightened() && !witness.isFrightened() && !listener.isFrightened(), "The fright never ends");
                end(helper, kingdom, area, List.of(attacker), all);
                helper.succeed();
            });
        });
    }

    // ---------------------------------------------------------------- alarm

    @GameTest(template = EMPTY, batch = "kingdomworld_citylaw_alarm", timeoutTicks = 1200)
    public static void aKillingCallsASoldierFromFortyBlocksWhoStrikesWithoutCallingOut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper, 50, 6);
        noon(level);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Alarmed Town", UUID.randomUUID());
        slowReports();
        Citizen dead = CitizenGameTests.newcomer(helper, kingdom, 4, 3);
        Citizen guard = soldier(helper, kingdom, 46, 3);
        CitizenEntity victim = body(helper, dead, 4, 3, false);
        CitizenEntity soldier = body(helper, guard, 46, 3, true);
        ServerPlayer murderer = player(helper, "kw-murderer", 5, 3);
        Citizen[] all = {dead, guard};
        helper.assertTrue(soldier.distanceTo(murderer) > 40.0, "The soldier stands too near: " + soldier.distanceTo(murderer));
        victim.hurt(level.damageSources().playerAttack(murderer), 1_000.0f);
        helper.assertTrue(!victim.isAlive(), "The citizen lives");
        // Nobody saw it: no record yet, but the alarm is raised at once, with the offender and the place.
        helper.assertTrue(LawWork.state(server).wanted(kingdom.id(), murderer.getUUID()).isEmpty(), "A killing nobody saw is on the record");
        List<Alarm> alarms = LawAlarms.active(server, kingdom.id());
        helper.assertTrue(alarms.size() == 1 && alarms.get(0).offender().equals(murderer.getUUID()) && alarms.get(0).killing(),
                "The killing raised no alarm: " + alarms.size());
        helper.assertTrue(Math.abs(alarms.get(0).x() - victim.getBlockX()) <= 2 && Math.abs(alarms.get(0).z() - victim.getBlockZ()) <= 2,
                "The alarm names another place");
        helper.assertTrue(alarms.get(0).unseen().size() == 1, "The alarm does not keep the killing for the guards");
        boolean[] swung = {false};
        helper.onEachTick(() -> swung[0] |= soldier.swinging && soldier.getTarget() == murderer);
        helper.succeedWhen(() -> {
            helper.assertTrue(soldier.distanceTo(murderer) < 6.0, "The soldier did not come: " + soldier.distanceTo(murderer));
            Wanted wanted = LawWork.state(server).wanted(kingdom.id(), murderer.getUUID()).orElseThrow(() -> new IllegalStateException(
                    "The soldier who saw the murderer wrote no record"));
            helper.assertTrue(wanted.murder() && wanted.executable(), "The record is not a murder: " + wanted.crimes());
            helper.assertTrue(ArmyWork.mayAttack(server, kingdom.id(), murderer), "The soldier may not strike the murderer");
            helper.assertTrue(swung[0], "The soldier did not strike");
            // It struck without calling out: nobody waited for the time to give up.
            helper.assertTrue(LawAlarms.of(server, kingdom.id(), murderer.getUUID()).map(Alarm::seen).orElse(false), "The alarm does not know the "
                    + "soldier saw the murderer");
            end(helper, kingdom, area, List.of(murderer), all);
        });
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_citylaw_selfdefence", timeoutTicks = 300)
    public static void aSoldierAnswersABlowNobodyElseSaw(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper, 9, 9);
        noon(level);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Lone Guard", UUID.randomUUID());
        slowReports();
        Citizen guard = soldier(helper, kingdom, 4, 4);
        CitizenEntity soldier = body(helper, guard, 4, 4, true);
        ServerPlayer brawler = player(helper, "kw-brawler", 6, 4);
        helper.assertTrue(!ArmyWork.mayAttack(server, kingdom.id(), brawler), "A soldier may attack a player who did nothing");
        soldier.hurt(level.damageSources().playerAttack(brawler), 1.0f);
        // The soldier is the witness of the blow at itself: the record is there at once, nobody carries a report.
        Wanted wanted = LawWork.state(server).wanted(kingdom.id(), brawler.getUUID()).orElseThrow(() -> new IllegalStateException(
                "A blow at a soldier nobody else saw is no crime"));
        helper.assertTrue(wanted.crimes().size() == 1 && wanted.crimes().get(0).kind() == CrimeKind.ASSAULT, "The record is wrong: " + wanted.crimes());
        helper.assertTrue(LawWork.pendingReports() == 0, "A report is on its way though the soldier saw it all");
        helper.assertTrue(ArmyWork.mayAttack(server, kingdom.id(), brawler), "The soldier may not answer the blow");
        boolean[] swung = {false};
        helper.onEachTick(() -> swung[0] |= soldier.swinging && soldier.getTarget() == brawler);
        helper.succeedWhen(() -> {
            helper.assertTrue(soldier.getTarget() == brawler, "The soldier did not turn on the player who struck it");
            helper.assertTrue(swung[0], "The soldier did not strike back");
            end(helper, kingdom, area, List.of(brawler), guard);
        });
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_citylaw_creative", timeoutTicks = 400)
    public static void aPlayerInCreativeModeIsAnsweredLikeAnyOther(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper, 20, 6);
        noon(level);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Creative Visitor", UUID.randomUUID());
        slowReports();
        Citizen dead = CitizenGameTests.newcomer(helper, kingdom, 15, 3);
        Citizen guard = soldier(helper, kingdom, 3, 3);
        CitizenEntity victim = body(helper, dead, 15, 3, false);
        CitizenEntity soldier = body(helper, guard, 3, 3, true);
        ServerPlayer creative = player(helper, "kw-creative", 14, 3);
        creative.setGameMode(GameType.CREATIVE);
        Citizen[] all = {dead, guard};
        helper.assertTrue(LawWork.catchable(server, creative), "The guards do not answer a player in creative mode");
        victim.hurt(level.damageSources().playerAttack(creative), 1_000.0f);
        helper.assertTrue(!victim.isAlive(), "The citizen lives");
        // The soldier stands in sight: the record is written as for any player.
        helper.assertTrue(LawWork.state(server).wanted(kingdom.id(), creative.getUUID()).map(Wanted::murder).orElse(false),
                "A killing by a player in creative mode is not on the record");
        helper.assertTrue(LawAlarms.active(server, kingdom.id()).size() == 1, "No alarm for a player in creative mode");
        boolean[] swung = {false};
        helper.onEachTick(() -> swung[0] |= soldier.swinging && soldier.getTarget() == creative);
        helper.succeedWhen(() -> {
            helper.assertTrue(soldier.getTarget() == creative && soldier.distanceTo(creative) < 4.0, "The soldier did not run up: "
                    + soldier.distanceTo(creative));
            helper.assertTrue(swung[0], "The soldier did not strike");
            helper.assertTrue(creative.getHealth() == creative.getMaxHealth() && creative.isAlive(), "A player in creative mode was hurt");
            // A server that does not want this turns it off: then the guards leave such players alone as before.
            LawRules.Builder off = LawSettings.configured();
            off.pursueCreative = false;
            LawSettings.override(off.build());
            helper.assertTrue(!LawWork.catchable(server, creative) && !ArmyWork.mayAttack(server, kingdom.id(), creative),
                    "The guards answer a player in creative mode though the config forbids it");
            end(helper, kingdom, area, List.of(creative), all);
        });
    }

    // ---------------------------------------------------------------- damaged property

    @GameTest(template = EMPTY, batch = "kingdomworld_citylaw_vandalism")
    public static void aBlockOfACityBreaksWithoutDropsIsACrimeWhenSeenAndComesBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper, 9, 9);
        noon(level);
        UUID king = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Mended Town", king);
        slowReports();
        WarWork.markPresent(kingdom.id(), true);
        Citizen seeing = CitizenGameTests.newcomer(helper, kingdom, 4, 2);
        body(helper, seeing, 4, 2, false);
        ServerPlayer vandal = player(helper, "kw-vandal", 3, 4);
        BlockPos first = helper.absolutePos(new BlockPos(5, 0, 5));
        BlockPos second = helper.absolutePos(new BlockPos(6, 0, 5));
        BlockPos placed = helper.absolutePos(new BlockPos(7, 0, 5));
        BlockPos chest = helper.absolutePos(new BlockPos(8, 0, 5));
        BlockPos throne = helper.absolutePos(new BlockPos(2, 0, 2));
        // Land of the kingdom outside its city: two chunks east of the capital, beyond the square of a village.
        ChunkPos capital = new ChunkPos(throne);
        BlockPos outside = new BlockPos(((capital.x + 2) << 4) + 15, first.getY(), throne.getZ());
        BlockState outsideBefore = level.getBlockState(outside);
        try {
            helper.assertTrue(KingdomWorldApi.claimChunk(level, new ChunkPos(capital.x + 2, capital.z), kingdom.id(), king).ok(), "No land outside the city");
            level.setBlock(first, Blocks.STONE_BRICKS.defaultBlockState(), 2 | 16);
            level.setBlock(second, Blocks.STONE_BRICKS.defaultBlockState(), 2 | 16);
            level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2 | 16);
            level.setBlock(outside, Blocks.STONE_BRICKS.defaultBlockState(), 2 | 16);
            long worth = LawPunish.worth(server, kingdom.id(), new ItemStack(Items.STONE_BRICKS));
            helper.assertTrue(worth > 0, "Stone bricks are worth nothing");

            // In the city: the block is gone, nothing falls, it is written down.
            helper.assertTrue(breakRefused(level, first, vandal) && level.getBlockState(first).isAir(), "The block of the city did not break as "
                    + "damaged property: " + level.getBlockState(first));
            helper.assertTrue(breakRefused(level, second, vandal) && level.getBlockState(second).isAir(), "The second block did not break");
            helper.assertTrue(itemsNear(level, first).isEmpty(), "A block that comes back for free dropped an item");
            helper.assertTrue(SiegeRepair.waiting(server, kingdom.id()) == 2, "The broken blocks were not written down: "
                    + SiegeRepair.waiting(server, kingdom.id()));
            helper.assertTrue(!LawVandalism.settled(server, kingdom.id(), LawWork.now(server)), "The blocks come back while the offender breaks");
            // Somebody saw it: one crime for both blocks, fined by their worth.
            LawVandalism.reportNow(vandal);
            LawWork.deliverNow(server);
            Wanted wanted = LawWork.state(server).wanted(kingdom.id(), vandal.getUUID()).orElseThrow(() -> new IllegalStateException(
                    "Damaged property somebody saw is no crime"));
            helper.assertTrue(wanted.crimes().size() == 1 && wanted.crimes().get(0).kind() == CrimeKind.VANDALISM
                    && wanted.crimes().get(0).worth() == 2 * worth, "The record is wrong: " + wanted.crimes() + ", worth of a block " + worth);
            helper.assertTrue(wanted.fine(LawSettings.rules(), 1000) == LawSettings.rules().vandalismFine(2 * worth), "The fine is wrong");
            // The blocks come back for free.
            helper.assertTrue(SiegeWork.forceMend(server, kingdom.id(), 10) == 2, "The blocks were not put back");
            helper.assertTrue(level.getBlockState(first).is(Blocks.STONE_BRICKS) && level.getBlockState(second).is(Blocks.STONE_BRICKS),
                    "The city is not as it was");
            helper.assertTrue(SiegeRepair.waiting(server, kingdom.id()) == 0, "Mended blocks are still written down");
            // Breaking it again gives nothing again: no endless blocks.
            helper.assertTrue(breakRefused(level, first, vandal) && level.getBlockState(first).isAir() && itemsNear(level, first).isEmpty(),
                    "The second breaking dropped an item");
            SiegeWork.forceMend(server, kingdom.id(), 10);

            // A block a stranger places in the city stays for now and is taken out again, as an item.
            BlockSnapshot before = BlockSnapshot.create(level.dimension(), level, placed);
            level.setBlock(placed, Blocks.COBBLESTONE.defaultBlockState(), 2 | 16);
            BlockEvent.EntityPlaceEvent place = new BlockEvent.EntityPlaceEvent(before, level.getBlockState(placed.below()), vandal);
            helper.assertTrue(!MinecraftForge.EVENT_BUS.post(place), "A stranger cannot place a block in the city");
            helper.assertTrue(SiegeRepair.waiting(server, kingdom.id()) == 1, "The placed block was not written down");
            // Its own block breaks as on nobody's land: it is no property of the city.
            helper.assertTrue(!breakRefused(level, placed, vandal), "A stranger cannot take the placed block back");
            SiegeWork.forceMend(server, kingdom.id(), 10);
            helper.assertTrue(level.getBlockState(placed).isAir(), "The block of a stranger stayed in the city");
            helper.assertTrue(itemsNear(level, placed).stream().anyMatch(item -> item.getItem().is(Items.COBBLESTONE)),
                    "The block of a stranger did not come out as an item");

            // Containers and the throne stay whole; explosives are not placed.
            helper.assertTrue(breakRefused(level, chest, vandal) && level.getBlockState(chest).is(Blocks.CHEST), "A chest broke in peace");
            helper.assertTrue(breakRefused(level, throne, vandal) && level.getBlockState(throne).is(ModRegistries.THRONE.get()), "The throne broke");
            helper.assertTrue(!LawVandalism.mayPlace(vandal, level, placed, new ItemStack(Items.TNT))
                    && !LawVandalism.mayPlace(vandal, level, placed, new ItemStack(Items.FLINT_AND_STEEL))
                    && LawVandalism.mayPlace(vandal, level, placed, new ItemStack(Items.COBBLESTONE)), "The wrong things may be placed");

            // Outside the city the land is free: the block breaks as anywhere, nothing is written down.
            int waiting = SiegeRepair.waiting(server, kingdom.id());
            helper.assertTrue(!LawVandalism.isProperty(server, kingdom.id(), level, outside), "The far end of the land counts as the city");
            helper.assertTrue(!breakRefused(level, outside, vandal), "A block outside the city cannot be broken");
            helper.assertTrue(SiegeRepair.waiting(server, kingdom.id()) == waiting, "A block outside the city was written down");

            // A kingdom of players nobody of whom is there keeps its land shut.
            WarWork.markPresent(kingdom.id(), false);
            helper.assertTrue(breakRefused(level, second, vandal) && level.getBlockState(second).is(Blocks.STONE_BRICKS),
                    "The land of players who are away was broken");
            helper.assertTrue(breakRefused(level, outside, vandal), "The land of players who are away is free outside the city");
            WarWork.markPresent(kingdom.id(), true);
            // A server that keeps the old rule refuses as before.
            LawRules.Builder forbidden = slowReports();
            forbidden.breakingIsCrime = false;
            LawSettings.override(forbidden.build());
            helper.assertTrue(breakRefused(level, second, vandal) && level.getBlockState(second).is(Blocks.STONE_BRICKS)
                    && breakRefused(level, outside, vandal), "Breaking is not refused though the config forbids it");
        } finally {
            level.setBlock(outside, outsideBefore, 2 | 16);
            end(helper, kingdom, area, List.of(vandal), seeing);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_citylaw_placing")
    public static void aStrangerPlacesOnlyPlainBlocksAndNoMachinesOrEntities(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper, 9, 9);
        noon(level);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Plain Blocks", UUID.randomUUID());
        WarWork.markPresent(kingdom.id(), true);
        ServerPlayer stranger = player(helper, "kw-placer", 3, 4);
        List<String> wrong = new java.util.ArrayList<>();
        try {
            BlockPos spot = helper.absolutePos(new BlockPos(6, 0, 6));
            // A hopper under a chest of the kingdom would draw its goods out with no theft on any record.
            BlockPos chest = helper.absolutePos(new BlockPos(5, 1, 5));
            BlockPos hopper = chest.below();
            level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2 | 16);
            BlockSnapshot before = BlockSnapshot.create(level.dimension(), level, hopper);
            level.setBlock(hopper, Blocks.HOPPER.defaultBlockState(), 2 | 16);
            if (!MinecraftForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(before, level.getBlockState(hopper.below()), stranger))) {
                wrong.add("a hopper was placed under a chest of the kingdom");
            }
            level.setBlock(hopper, Blocks.AIR.defaultBlockState(), 2 | 16);
            if (SiegeRepair.waiting(server, kingdom.id()) != 0) {
                wrong.add("a refused block was written down");
            }
            // Nothing that holds goods or data, nothing that gives or carries a signal, nothing that moves or burns.
            for (net.minecraft.world.item.Item item : List.of(Items.HOPPER, Items.CHEST, Items.TRAPPED_CHEST, Items.BARREL, Items.DISPENSER,
                    Items.DROPPER, Items.FURNACE, Items.SHULKER_BOX, Items.OAK_SIGN, Items.REDSTONE_TORCH, Items.REDSTONE_BLOCK, Items.REDSTONE,
                    Items.LEVER, Items.STONE_BUTTON, Items.STONE_PRESSURE_PLATE, Items.OBSERVER, Items.REPEATER, Items.COMPARATOR,
                    Items.DAYLIGHT_DETECTOR, Items.TRIPWIRE_HOOK, Items.TARGET, Items.ACTIVATOR_RAIL, Items.DETECTOR_RAIL, Items.POWERED_RAIL,
                    Items.RAIL, Items.PISTON, Items.STICKY_PISTON, Items.TNT)) {
                if (LawVandalism.mayPlace(stranger, level, spot, new ItemStack(item))) {
                    wrong.add("may place " + item);
                }
            }
            for (net.minecraft.world.item.Item item : List.of(Items.COBBLESTONE, Items.OAK_PLANKS, Items.GLASS, Items.DIRT, Items.TORCH,
                    Items.OAK_STAIRS, Items.OAK_FENCE)) {
                if (!LawVandalism.mayPlace(stranger, level, spot, new ItemStack(item))) {
                    wrong.add("may not place " + item);
                }
            }
            // Things that become entities: a click on a block with them is refused.
            BlockPos rail = helper.absolutePos(new BlockPos(7, 0, 5));
            level.setBlock(rail, Blocks.RAIL.defaultBlockState(), 2 | 16);
            for (net.minecraft.world.item.Item item : List.of(Items.MINECART, Items.HOPPER_MINECART, Items.CHEST_MINECART, Items.TNT_MINECART,
                    Items.ARMOR_STAND, Items.ITEM_FRAME, Items.PAINTING, Items.END_CRYSTAL, Items.OAK_BOAT, Items.PIG_SPAWN_EGG)) {
                stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
                if (click(stranger, rail).getUseItem() != Event.Result.DENY) {
                    wrong.add("a click on a block uses " + item);
                }
            }
            // A boat and a spawn egg are also used with no block clicked, where the player looks: here, down at the land.
            stranger.setXRot(90.0f);
            for (net.minecraft.world.item.Item item : List.of(Items.OAK_BOAT, Items.OAK_CHEST_BOAT, Items.PIG_SPAWN_EGG)) {
                stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
                PlayerInteractEvent.RightClickItem used = new PlayerInteractEvent.RightClickItem(stranger, InteractionHand.MAIN_HAND);
                MinecraftForge.EVENT_BUS.post(used);
                if (!used.isCanceled()) {
                    wrong.add("a click in the air uses " + item + " on the land");
                }
            }
            // A plain thing in hand used in the air is nobody's business.
            stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD));
            PlayerInteractEvent.RightClickItem eaten = new PlayerInteractEvent.RightClickItem(stranger, InteractionHand.MAIN_HAND);
            MinecraftForge.EVENT_BUS.post(eaten);
            if (eaten.isCanceled()) {
                wrong.add("bread cannot be eaten on the land of a kingdom");
            }
            helper.assertTrue(wrong.isEmpty(), "A stranger places more than plain blocks: " + String.join("; ", wrong));
        } finally {
            end(helper, kingdom, area, List.of(stranger));
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_citylaw_cascade")
    public static void whatStoodOnABrokenBlockGoesWithItDropsNothingAndComesBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper, 9, 9);
        noon(level);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Whole Town", UUID.randomUUID());
        WarWork.markPresent(kingdom.id(), true);
        ServerPlayer vandal = player(helper, "kw-breaker", 3, 3);
        List<String> wrong = new java.util.ArrayList<>();
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockPos underTorch = helper.absolutePos(new BlockPos(5, 0, 5));
        BlockPos underSand = helper.absolutePos(new BlockPos(7, 0, 5));
        BlockPos underDoor = helper.absolutePos(new BlockPos(5, 0, 7));
        BlockPos underFrame = helper.absolutePos(new BlockPos(7, 0, 7));
        BlockPos underSign = helper.absolutePos(new BlockPos(3, 0, 7));
        BlockState lower = Blocks.OAK_DOOR.defaultBlockState();
        BlockState upper = lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        net.minecraft.world.entity.decoration.ItemFrame frame = new net.minecraft.world.entity.decoration.ItemFrame(level, underFrame.east(),
                Direction.EAST);
        // Items an earlier test left in the caves around would count as dropped here.
        level.getEntitiesOfClass(ItemEntity.class, new AABB(area[0], area[1]).inflate(4.0)).forEach(ItemEntity::discard);
        try {
            for (BlockPos pos : List.of(underTorch, underSand, underDoor, underFrame, underSign)) {
                level.setBlock(pos, bricks, 2 | 16);
            }
            level.setBlock(underTorch.above(), Blocks.TORCH.defaultBlockState(), 2 | 16);
            level.setBlock(underSand.above(), Blocks.SAND.defaultBlockState(), 2 | 16);
            level.setBlock(underSand.above(2), Blocks.SAND.defaultBlockState(), 2 | 16);
            level.setBlock(underDoor.above(), lower, 2 | 16);
            level.setBlock(underDoor.above(2), upper, 2 | 16);
            level.setBlock(underSign.above(), Blocks.OAK_SIGN.defaultBlockState(), 2 | 16);
            frame.setItem(new ItemStack(Items.DIAMOND), false);
            level.addFreshEntity(frame);

            // A torch stands on the block: both go, nothing falls, both are written down.
            boolean refused = breakRefused(level, underTorch, vandal);
            if (!refused || !level.getBlockState(underTorch).isAir() || !level.getBlockState(underTorch.above()).isAir()) {
                wrong.add("the block under a torch and the torch are not both gone");
            }
            if (SiegeRepair.waiting(server, kingdom.id()) != 2) {
                wrong.add("a block and its torch made " + SiegeRepair.waiting(server, kingdom.id()) + " record(s), not 2");
            }
            // Sand lies on the block: it does not fall, it goes and comes back with the block.
            breakRefused(level, underSand, vandal);
            if (!level.getBlockState(underSand.above()).isAir() || !level.getBlockState(underSand.above(2)).isAir()) {
                wrong.add("the sand over a broken block stays to fall");
            }
            if (SiegeRepair.waiting(server, kingdom.id()) != 5) {
                wrong.add("after the sand " + SiegeRepair.waiting(server, kingdom.id()) + " record(s), not 5");
            }
            // A door stands on the block: both halves go with it.
            breakRefused(level, underDoor, vandal);
            if (!level.getBlockState(underDoor.above()).isAir() || !level.getBlockState(underDoor.above(2)).isAir()) {
                wrong.add("the door on a broken block stays");
            }
            if (SiegeRepair.waiting(server, kingdom.id()) != 8) {
                wrong.add("after the door " + SiegeRepair.waiting(server, kingdom.id()) + " record(s), not 8");
            }
            // A frame hangs on the block, a sign stands on another: those blocks do not break at all.
            if (!breakRefused(level, underFrame, vandal) || !level.getBlockState(underFrame).is(Blocks.STONE_BRICKS) || !frame.isAlive()) {
                wrong.add("a block with a frame on it broke");
            }
            if (!breakRefused(level, underSign, vandal) || !level.getBlockState(underSign).is(Blocks.STONE_BRICKS)
                    || !level.getBlockState(underSign.above()).is(Blocks.OAK_SIGN)) {
                wrong.add("a block with a sign on it broke");
            }
            if (!itemsNear(level, underDoor).isEmpty() || !itemsNear(level, underTorch).isEmpty() || !itemsNear(level, underSand).isEmpty()) {
                wrong.add("damaged property dropped " + level.getEntitiesOfClass(ItemEntity.class, new AABB(area[0], area[1]).inflate(2.0))
                        .stream().map(item -> item.getItem().toString()).toList());
            }
            if (!level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class, new AABB(area[0], area[1]).inflate(2.0)).isEmpty()) {
                wrong.add("sand is falling");
            }
            // Everything comes back, the block before what stands on it.
            for (int i = 0; i < 4; i++) {
                SiegeWork.forceMend(server, kingdom.id(), 64);
            }
            if (!level.getBlockState(underTorch).is(Blocks.STONE_BRICKS) || !level.getBlockState(underTorch.above()).is(Blocks.TORCH)) {
                wrong.add("the block and its torch did not come back");
            }
            if (!level.getBlockState(underSand).is(Blocks.STONE_BRICKS) || !level.getBlockState(underSand.above()).is(Blocks.SAND)
                    || !level.getBlockState(underSand.above(2)).is(Blocks.SAND)) {
                wrong.add("the block and its sand did not come back");
            }
            if (!level.getBlockState(underDoor).is(Blocks.STONE_BRICKS) || !level.getBlockState(underDoor.above()).equals(lower)
                    || !level.getBlockState(underDoor.above(2)).equals(upper)) {
                wrong.add("the block and its door did not come back");
            }
            if (SiegeRepair.waiting(server, kingdom.id()) != 0) {
                wrong.add("records are left after the mending: " + SiegeRepair.waiting(server, kingdom.id()));
            }
            // A book of damage that is full takes no more: the block does not break until the city is mended.
            io.github.sekerator.kingdomworld.core.siege.WarDamage damage = SiegeWork.state(server).damage();
            for (int i = 0; damage.count(kingdom.id()) < io.github.sekerator.kingdomworld.core.siege.WarDamage.MAX_PER_KINGDOM; i++) {
                damage.record(io.github.sekerator.kingdomworld.core.siege.WarDamage.Entry.inPeace(kingdom.id(), "kingdomworld:nowhere", i, "", "", 0));
            }
            if (!breakRefused(level, underTorch, vandal) || !level.getBlockState(underTorch).is(Blocks.STONE_BRICKS)
                    || damage.count(kingdom.id()) != io.github.sekerator.kingdomworld.core.siege.WarDamage.MAX_PER_KINGDOM) {
                wrong.add("a block broke though the book of damage is full");
            }
            if (damage.at(kingdom.id(), "kingdomworld:nowhere", 0).isEmpty()) {
                wrong.add("the oldest record gave way to a new one");
            }
            helper.assertTrue(wrong.isEmpty(), "Damaged property loses blocks or gives items: " + String.join("; ", wrong));
        } finally {
            frame.discard();
            SiegeWork.state(server).damage().forget(kingdom.id());
            end(helper, kingdom, area, List.of(vandal));
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_citylaw_frame_outside", timeoutTicks = 300)
    public static void aBlockThatHoldsAFrameDoesNotBreakOutsideTheCityEither(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper, 9, 9);
        noon(level);
        UUID king = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Far Wall", king);
        WarWork.markPresent(kingdom.id(), true);
        ServerPlayer stranger = player(helper, "kw-frame-thief", 3, 4);
        // Land of the kingdom outside its city, two chunks east of the capital: the wall of a house nobody registered.
        BlockPos throne = helper.absolutePos(new BlockPos(2, 0, 2));
        ChunkPos far = new ChunkPos(new ChunkPos(throne).x + 2, new ChunkPos(throne).z);
        BlockPos wall = new BlockPos(far.getMinBlockX() + 8, throne.getY(), throne.getZ());
        helper.assertTrue(KingdomWorldApi.claimChunk(level, far, kingdom.id(), king).ok(), "No land outside the city");
        // The frame must be in the world for the server, so the chunk is kept loaded with its entities.
        level.setChunkForced(far.x, far.z, true);
        helper.runAfterDelay(40, () -> {
            BlockState wallBefore = level.getBlockState(wall);
            BlockState airBefore = level.getBlockState(wall.east());
            level.setBlock(wall, Blocks.STONE_BRICKS.defaultBlockState(), 2 | 16);
            level.setBlock(wall.east(), Blocks.AIR.defaultBlockState(), 2 | 16);
            net.minecraft.world.entity.decoration.ItemFrame frame = new net.minecraft.world.entity.decoration.ItemFrame(level, wall.east(),
                    Direction.EAST);
            frame.setItem(new ItemStack(Items.DIAMOND), false);
            try {
                helper.assertTrue(level.addFreshEntity(frame) && level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class,
                        new AABB(wall).inflate(2.0)).contains(frame), "The frame is not in the world");
                helper.assertTrue(!LawVandalism.isProperty(server, kingdom.id(), level, wall), "The far wall counts as the city");
                // Outside the city blocks break freely, but not one a frame hangs on: its goods would fall to the breaker
                // with no theft on any record.
                helper.assertTrue(breakRefused(level, wall, stranger) && level.getBlockState(wall).is(Blocks.STONE_BRICKS) && frame.isAlive(),
                        "A block that holds a frame broke outside the city");
                helper.assertTrue(SiegeRepair.waiting(server, kingdom.id()) == 0, "A refused block was written down");
                // Without the frame the same block breaks as any block out there.
                frame.discard();
                helper.assertTrue(!breakRefused(level, wall, stranger), "A plain block outside the city cannot be broken");
            } finally {
                frame.discard();
                level.setBlock(wall, wallBefore, 2 | 16);
                level.setBlock(wall.east(), airBefore, 2 | 16);
                level.setChunkForced(far.x, far.z, false);
                end(helper, kingdom, area, List.of(stranger));
            }
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- houses and storehouses

    @GameTest(template = EMPTY, batch = "kingdomworld_citylaw_house")
    public static void aHouseKeepsAShareOfTheWagesAndATheftFromItIsATheftFromTheCitizen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper, 9, 9);
        noon(level);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Households", UUID.randomUUID());
        slowReports();
        Households.override(new HouseholdRules(20, 300, 6, 2));
        KingdomWorldApi.state(server).economy().adminTreasury(kingdom.id(), 10_000, TreasuryActions.day(server));
        Citizen owner = CitizenGameTests.newcomer(helper, kingdom, 6, 6);
        Citizen seeing = CitizenGameTests.newcomer(helper, kingdom, 3, 6);
        body(helper, seeing, 3, 6, false);
        ServerPlayer thief = player(helper, "kw-burglar", 4, 6);
        try {
            BlockPos barrel = helper.absolutePos(new BlockPos(5, 0, 5));
            level.setBlock(barrel, Blocks.BARREL.defaultBlockState(), 2 | 16);
            WorldPos bed = LifeGameTests.bed(helper, kingdom, new BlockPos(7, 0, 6));
            helper.assertTrue(KingdomWorldApi.state(server).citizens().assignHome(owner, bed), "The citizen got no bed");
            Building house = house(helper, kingdom, helper.absolutePos(new BlockPos(5, 0, 5)), helper.absolutePos(new BlockPos(8, 2, 8)));
            StorehouseInventory store = CitizenGameTests.storehouse(helper, 0, 0, 8);
            store.insertItem(0, new ItemStack(Items.BREAD, 40), false);
            Economy economy = KingdomWorldApi.state(server).economy();
            CurrencyUnits units = Purses.units(server, kingdom.id());
            long price = LawPunish.worth(server, kingdom.id(), new ItemStack(Items.BREAD));
            helper.assertTrue(price > 0 && price * 2 < 100, "Bread costs " + price);

            // A wage of 500 was paid: a fifth goes home with the citizen, nothing else changes yet.
            long funds = economy.treasury(kingdom.id()).orElseThrow().funds();
            long coinsOut = economy.coinsOut(kingdom.id());
            Households.onPayday(server, kingdom.id(), new Economy.Payday(List.of(), Map.of(owner.id(), 500L, seeing.id(), 500L), Map.of(),
                    Payroll.Summary.NONE));
            helper.assertTrue(Households.purse(server, house.id()) == 100, "The house was set aside " + Households.purse(server, house.id())
                    + " (a citizen without a house brings nothing home)");
            // The house is looked at: a portion of bread bought from the storehouse, the rest laid in as coins.
            helper.assertTrue(Households.settle(server, house.id()), "The house was not settled");
            Container inside = (Container) level.getBlockEntity(barrel);
            int bread = 0;
            Coins coins = Coins.NONE;
            for (int slot = 0; slot < inside.getContainerSize(); slot++) {
                ItemStack stack = inside.getItem(slot);
                if (stack.is(Items.BREAD)) {
                    bread += stack.getCount();
                } else if (CoinItem.currency(stack).filter(kingdom.id()::equals).isPresent()) {
                    coins = coins.plus(CoinItem.denomination(stack).orElse(Denomination.COPPER), stack.getCount());
                }
            }
            helper.assertTrue(bread == 2, "The house bought " + bread + " loaves");
            helper.assertTrue(coins.value(units) == 100 - 2 * price, "The house keeps " + coins.value(units) + " in coins, bread costs " + price);
            helper.assertTrue(Households.purse(server, house.id()) == 0, "Money is still set aside");
            helper.assertTrue(economy.treasury(kingdom.id()).orElseThrow().funds() == funds + 2 * price, "The price of the bread did not reach the treasury");
            helper.assertTrue(economy.coinsOut(kingdom.id()) == coinsOut + 100 - 2 * price, "The coins of the house are not counted");
            // The bread of the house is the citizen's, not the kingdom's: the stock of the kingdom is what its storehouses hold.
            helper.assertTrue(CitizenGameTests.count(store, Items.BREAD) == 38
                    && KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.BREAD)) == 38, "The barrel of a house counts as a storehouse");

            // Taking from the barrel is a theft from the citizen who lives there.
            ChestMenu menu = ChestMenu.threeRows(1, thief.getInventory(), inside);
            LawEvents.watchForCheck(thief, barrel, menu);
            for (int slot = 0; slot < inside.getContainerSize(); slot++) {
                if (inside.getItem(slot).is(Items.BREAD)) {
                    menu.getSlot(slot).remove(1);
                    break;
                }
            }
            LawEvents.closedForCheck(thief, menu);
            LawWork.deliverNow(server);
            Wanted wanted = LawWork.state(server).wanted(kingdom.id(), thief.getUUID()).orElseThrow(() -> new IllegalStateException(
                    "The theft from a house is no crime"));
            Crime crime = wanted.crimes().get(0);
            helper.assertTrue(crime.kind() == CrimeKind.THEFT && crime.worth() == price, "The record is wrong: " + wanted.crimes());
            helper.assertTrue(crime.victim().equals(Victims.citizen(owner.nameSeed(), owner.gender() == Gender.FEMALE)),
                    "The record does not name the citizen of the house: '" + crime.victim() + "'");
            helper.assertTrue(wanted.fine(LawSettings.rules(), 1000) == LawSettings.rules().theftFine(price), "A theft from a house is fined as "
                    + "a theft from the treasury");
        } finally {
            end(helper, kingdom, area, List.of(thief), owner, seeing);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_citylaw_storehouse")
    public static void aTheftFromAStorehouseNextToASoldierMakesTheThiefWantedAtOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper, 9, 9);
        noon(level);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Guarded Stores", UUID.randomUUID());
        slowReports();
        Citizen guard = soldier(helper, kingdom, 6, 1);
        body(helper, guard, 6, 1, false);
        ServerPlayer thief = player(helper, "kw-thief", 0, 4);
        try {
            // A wall between the thief and the soldier: the soldier hears the storehouse, it does not see the thief.
            for (int z = -1; z <= 6; z++) {
                for (int y = 0; y <= 2; y++) {
                    level.setBlock(helper.absolutePos(new BlockPos(3, y, z)), Blocks.STONE.defaultBlockState(), 2 | 16);
                }
            }
            StorehouseInventory storehouse = CitizenGameTests.storehouse(helper, 1, 0, 1);
            storehouse.insertItem(0, new ItemStack(Items.IRON_INGOT, 10), false);
            BlockPos pos = helper.absolutePos(new BlockPos(1, 0, 1));
            ChestMenu menu = new ChestMenu(MenuType.GENERIC_9x6, 1, thief.getInventory(), new RecipeWrapper(storehouse), 6);
            LawEvents.watchForCheck(thief, pos, menu);
            menu.getSlot(0).remove(4);
            LawEvents.closedForCheck(thief, menu);
            long worth = LawPunish.worth(server, kingdom.id(), new ItemStack(Items.IRON_INGOT, 4));
            // No report on its way: the soldier at the storehouse noticed it itself.
            Wanted wanted = LawWork.state(server).wanted(kingdom.id(), thief.getUUID()).orElseThrow(() -> new IllegalStateException(
                    "The soldier at the storehouse noticed nothing"));
            helper.assertTrue(LawWork.pendingReports() == 0, "A report is on its way");
            Crime crime = wanted.crimes().get(0);
            helper.assertTrue(crime.kind() == CrimeKind.THEFT && crime.worth() == worth && Victims.isTreasury(crime.victim()),
                    "The record is no theft from the treasury: " + wanted.crimes());
            LawRules rules = LawSettings.rules();
            helper.assertTrue(wanted.fine(rules, 1000) == rules.treasuryFine(worth) && rules.treasuryFine(worth) > rules.theftFine(worth),
                    "The treasury is fined as anybody: " + wanted.fine(rules, 1000));
            List<Alarm> alarms = LawAlarms.active(server, kingdom.id());
            helper.assertTrue(alarms.size() == 1 && !alarms.get(0).killing() && !alarms.get(0).violent(LawWork.now(server), rules),
                    "The theft raised no alarm, or one that lets the guards strike without calling out");
        } finally {
            end(helper, kingdom, area, List.of(thief), guard);
        }
        helper.succeed();
    }
}
