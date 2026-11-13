package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyActions;
import io.github.sekerator.kingdomworld.common.army.ArmyForce;
import io.github.sekerator.kingdomworld.common.army.ArmyKits;
import io.github.sekerator.kingdomworld.common.army.ArmyRelief;
import io.github.sekerator.kingdomworld.common.army.ArmySavedData;
import io.github.sekerator.kingdomworld.common.army.ArmySettings;
import io.github.sekerator.kingdomworld.common.army.ArmyStaff;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.guild.GuildWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.law.LawAlarms;
import io.github.sekerator.kingdomworld.common.law.LawSettings;
import io.github.sekerator.kingdomworld.common.law.LawStandIn;
import io.github.sekerator.kingdomworld.common.law.LawWork;
import io.github.sekerator.kingdomworld.common.network.packet.ArmyPackets;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.ArmyState;
import io.github.sekerator.kingdomworld.core.army.Branch;
import io.github.sekerator.kingdomworld.core.army.ForceRules;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.army.Post;
import io.github.sekerator.kingdomworld.core.army.Relief;
import io.github.sekerator.kingdomworld.core.army.Soldier;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.law.Alarm;
import io.github.sekerator.kingdomworld.core.law.Confiscation;
import io.github.sekerator.kingdomworld.core.law.LawRules;
import io.github.sekerator.kingdomworld.core.law.LawState;
import io.github.sekerator.kingdomworld.core.law.Wanted;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Game tests of the city guard and the royal army: the body of an officer has the health and the issued gear of its post
 * and keeps them through the copying between body and record; the issued gear of a fallen captain does not fall, that of a
 * fighter does; an alarm with guards out of the fight brings the vice-captain of the guard, then its captain, then the
 * army; after the alarm a guard goes back to its post; a fallen officer is replaced after the days of the rules, and only
 * when the treasury can pay the kit; a king gives a post, which counts only in armor of the post; army data older than the
 * branches gets them by the orders of the squads. Every test runs in a batch of its own, in a box walled off the caves of
 * the world, and clears what it built.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuardArmyGameTests {
    private static final String EMPTY = "empty";

    private GuardArmyGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    /**
     * The fenced area of a test, with its chunks kept loaded and ticking until {@link #end}: the world of the game tests ticks
     * the entities only in the chunks of the structures of the tests, and the stand-ins of players hold no chunks, so a body at
     * the far end of a long area would stand frozen whenever no other test happens to lie there.
     */
    private static BlockPos[] area(GameTestHelper helper, int toX, int toZ) {
        BlockPos from = helper.absolutePos(new BlockPos(-2, 0, -2));
        BlockPos to = helper.absolutePos(new BlockPos(toX, 3, toZ));
        GatheringGameTests.enclose(helper.getLevel(), from, to);
        TestChunks.hold(helper.getLevel(), from, to);
        return new BlockPos[]{from, to};
    }

    private static void noon(ServerLevel level) {
        level.setDayTime(Math.floorDiv(level.getDayTime(), 24_000L) * 24_000L + 6_000L);
    }

    /**
     * A player who stands in for a real one: found by the alarms and counted as online, though in no list of players.
     */
    private static ServerPlayer player(GameTestHelper helper, UUID id, String name, int x, int z) {
        ServerPlayer player = LawStandIn.create(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(id, name));
        BlockPos at = helper.absolutePos(new BlockPos(x, 0, z));
        player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        ArmyWork.markOnline(player.getUUID(), true);
        LawAlarms.standInForCheck(player, true);
        return player;
    }

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

    private static long funds(MinecraftServer server, Kingdom kingdom) {
        return KingdomWorldApi.state(server).economy().treasury(kingdom.id()).orElseThrow().funds();
    }

    private static void fund(GameTestHelper helper, Kingdom kingdom, long amount) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(KingdomWorldApi.state(server).economy().adminTreasury(kingdom.id(), amount, ArmyWork.day(server)).ok(),
                "The treasury took no money");
    }

    /**
     * Shields ward nothing off, so that every blow of a test lands.
     */
    private static void noShields() {
        ForceRules.Builder rules = ArmySettings.configuredForce();
        rules.blockChance = 0.0;
        ArmySettings.overrideForce(rules.build());
    }

    private static Soldier record(MinecraftServer server, Citizen citizen) {
        return ArmyWork.state(server).soldier(citizen.id()).orElseThrow(() -> new IllegalStateException("The citizen is no soldier"));
    }

    /**
     * Posts a soldier: a squad of its own that holds the place.
     */
    private static void post(GameTestHelper helper, Kingdom kingdom, Citizen citizen, int x, int z) {
        MinecraftServer server = helper.getLevel().getServer();
        ArmySquad squad = ArmyWork.state(server).formSquad(null, kingdom.id(), List.of(citizen.id()), ArmySettings.rules(),
                KingdomWorldApi.state(server)).squad().orElseThrow(() -> new IllegalStateException("No squad was formed"));
        BlockPos at = helper.absolutePos(new BlockPos(x, 0, z));
        helper.assertTrue(ArmyWork.order(server, null, squad.id(), Order.hold(new WorldPos(Dimensions.id(helper.getLevel()), at.getX(), at.getY(),
                at.getZ()))).ok(), "The squad took no order");
    }

    private static ItemStack enchanted(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 1);
        return stack;
    }

    private static List<ItemEntity> itemsNear(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3.0));
    }

    private static void end(GameTestHelper helper, Kingdom kingdom, BlockPos[] area, List<ServerPlayer> players, Citizen... citizens) {
        MinecraftServer server = helper.getLevel().getServer();
        TestChunks.release(helper.getLevel(), area[0]);
        LawSettings.override(null);
        ArmySettings.overrideForce(null);
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
        ArmyWork.state(server).forgetKingdom(kingdom.id());
        GuildWork.state(server).forgetKingdom(kingdom.id(), TreasuryActions.day(server), KingdomWorldApi.state(server));
        CitizenGameTests.finish(helper, kingdom, citizens);
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(area[0], area[1]).inflate(2.0))) {
            item.discard();
        }
        GatheringGameTests.clear(helper.getLevel(), area[0], area[1]);
    }

    // ---------------------------------------------------------------- the body of an officer

    @GameTest(template = EMPTY, batch = "kingdomworld_guardarmy_officer")
    public static void theBodyOfAnOfficerHasTheHealthAndTheGearOfItsPostAndKeepsThemThroughTheSync(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper, 9, 9);
        noon(level);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Captain", UUID.randomUUID());
        Citizen citizen = soldier(helper, kingdom, 4, 4);
        fund(helper, kingdom, 50_000_000L);
        helper.assertTrue(record(server, citizen).branch() == Branch.GUARD && record(server, citizen).post() == Post.FIGHTER,
                "A recruit is no fighter of the guard");
        helper.assertTrue(ArmyStaff.appoint(server, null, citizen.id(), Post.CAPTAIN).ok(), "The soldier got no post");
        Soldier captain = record(server, citizen);
        // The kingdom pays the kit of the post, piece by piece, exactly its price.
        long price = ArmyKits.missingPrice(server, citizen, captain);
        long before = funds(server, kingdom);
        ArmyKits.Issued issued = ArmyKits.issue(server, citizen, captain, false);
        helper.assertTrue(price > 0 && issued.whole() && issued.pieces() == 6 && issued.paid() == price,
                "The kit was not issued whole at its price: " + issued + ", price " + price);
        helper.assertTrue(funds(server, kingdom) == before - price, "The treasury did not pay the price of the kit");
        helper.assertTrue(price == ArmyKits.price(server, captain, false), "The price of a whole kit is not the sum of its pieces");
        CitizenEntity body = body(helper, citizen, 4, 4, false);
        ForceRules rules = ArmySettings.force();
        float full = (float) rules.health(Branch.GUARD, Post.CAPTAIN);
        helper.assertTrue(full == 120.0f && body.getMaxHealth() == full && body.getHealth() == full,
                "The captain has not the health of its post: " + body.getHealth() + "/" + body.getMaxHealth());
        ItemStack chest = body.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack sword = body.getItemBySlot(EquipmentSlot.MAINHAND);
        helper.assertTrue(chest.is(Items.NETHERITE_CHESTPLATE) && ArmyKits.isOfficerIssue(chest)
                && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.ALL_DAMAGE_PROTECTION, chest) == 3, "The chestplate is not that of the kit");
        helper.assertTrue(sword.is(Items.NETHERITE_SWORD) && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, sword) == 4
                && body.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD), "The sword and the shield are not those of the kit");
        // The record takes the health of the body as it is: nothing cuts it to the health of a citizen.
        CitizenBodies.update(server);
        helper.assertTrue(citizen.maxHealth() == full && citizen.health() == full && body.getMaxHealth() == full && body.getHealth() == full,
                "The sync cut the health of the captain: record " + citizen.health() + "/" + citizen.maxHealth() + ", body " + body.getHealth());
        body.setHealth(90.0f);
        CitizenBodies.update(server);
        helper.assertTrue(citizen.health() == 90.0f && body.getHealth() == 90.0f, "The sync changed the health of a hurt captain");
        // What is issued is issued once, and is not swapped for something from the storehouses.
        helper.assertTrue(ArmyKits.issue(server, citizen, captain, false).pieces() == 0 && funds(server, kingdom) == before - price,
                "The kit was issued twice");
        CitizenGameTests.storehouse(helper, 1, 1, 1);
        ItemStack better = new ItemStack(Items.NETHERITE_SWORD);
        better.enchant(Enchantments.SHARPNESS, 5);
        helper.assertTrue(KingdomStorage.insert(server, kingdom.id(), better, null).isEmpty(), "The storehouse took no sword");
        helper.assertTrue(ArmyWork.equip(server, citizen).isEmpty() && ArmyKits.isIssued(body.getItemBySlot(EquipmentSlot.MAINHAND)),
                "The captain swapped its issued sword for one from the storehouse");
        // Without the armor of its post an officer is as strong as a fighter, and as wounded as it was.
        body.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        CitizenBodies.update(server);
        helper.assertTrue(body.getMaxHealth() == 30.0f && Math.abs(body.getHealth() - 22.5f) < 0.01f && citizen.maxHealth() == 30.0f
                && ArmyForce.standing(server, captain) == Post.FIGHTER, "A captain without its armor keeps the strength of the post: "
                + body.getHealth() + "/" + body.getMaxHealth());
        end(helper, kingdom, area, List.of(), citizen);
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_guardarmy_blows")
    public static void theBlowsOfAnOfficerAreThoseOfItsPostAndItStandsFirm(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper, 9, 9);
        noon(level);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Army Captain", UUID.randomUUID());
        Citizen citizen = soldier(helper, kingdom, 4, 4);
        fund(helper, kingdom, 50_000_000L);
        helper.assertTrue(ArmyStaff.setBranch(server, null, citizen.id(), Branch.ARMY).ok() && ArmyStaff.appoint(server, null, citizen.id(),
                Post.CAPTAIN).ok(), "The soldier got no post in the army");
        helper.assertTrue(ArmyKits.issue(server, citizen, record(server, citizen), false).whole(), "The captain got no kit");
        CitizenEntity body = body(helper, citizen, 4, 4, false);
        helper.assertTrue(body.getMaxHealth() == 160.0f, "The captain of the army has not 160 health: " + body.getMaxHealth());
        // What a body holds and wears counts from its next tick on.
        helper.runAfterDelay(5, () -> {
            // The hand, a netherite sword and the two points of the post; Sharpness comes on top with every blow.
            helper.assertTrue(Math.abs(body.getAttributeValue(Attributes.ATTACK_DAMAGE) - 10.0) < 1.0e-6,
                    "The blow of the captain is not that of its sword and post: " + body.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(body.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 0.999, "The captain is thrown about by blows");
            helper.assertTrue(body.getAttributeValue(Attributes.ARMOR) == 20.0 && body.getAttributeValue(Attributes.ARMOR_TOUGHNESS) == 12.0,
                    "The armor of the captain is not that of netherite");
            end(helper, kingdom, area, List.of(), citizen);
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- what falls

    @GameTest(template = EMPTY, batch = "kingdomworld_guardarmy_drops")
    public static void theIssuedGearOfAFallenCaptainDoesNotFallAndThatOfAFighterDoes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper, 24, 8);
        noon(level);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Fallen Captain", UUID.randomUUID());
        noShields();
        fund(helper, kingdom, 50_000_000L);
        Citizen officer = soldier(helper, kingdom, 4, 4);
        Citizen fighter = soldier(helper, kingdom, 18, 4);
        helper.assertTrue(ArmyStaff.appoint(server, null, officer.id(), Post.CAPTAIN).ok(), "The soldier got no post");
        helper.assertTrue(ArmyKits.issue(server, officer, record(server, officer), false).whole(), "The captain got no kit");
        helper.assertTrue(ArmyKits.issue(server, fighter, record(server, fighter), false).pieces() == 6, "The fighter got no kit");
        CitizenEntity captain = body(helper, officer, 4, 4, false);
        CitizenEntity guard = body(helper, fighter, 18, 4, false);
        helper.assertTrue(captain.getItemBySlot(EquipmentSlot.HEAD).is(Items.NETHERITE_HELMET) && guard.getItemBySlot(EquipmentSlot.HEAD)
                .is(Items.IRON_HELMET) && ArmyKits.isIssued(guard.getItemBySlot(EquipmentSlot.HEAD)), "The bodies do not wear their kits");
        ServerPlayer killer = player(helper, UUID.randomUUID(), "kw-captain-killer", 6, 4);
        BlockPos captainAt = captain.blockPosition();
        BlockPos guardAt = guard.blockPosition();
        captain.hurt(level.damageSources().playerAttack(killer), 100_000.0f);
        guard.hurt(level.damageSources().playerAttack(killer), 100_000.0f);
        helper.assertTrue(!captain.isAlive() && !guard.isAlive(), "The soldiers live");
        helper.succeedWhen(() -> {
            // No captain is a mine of enchanted netherite: its issued gear is gone with it.
            List<ItemEntity> fromCaptain = itemsNear(level, captainAt);
            helper.assertTrue(fromCaptain.isEmpty(), "The captain dropped " + fromCaptain.size() + " item(s): "
                    + fromCaptain.stream().map(item -> item.getItem().toString()).toList());
            // The gear of a fighter falls as that of any soldier, as plain gear without the mark of the issue.
            List<ItemEntity> fromGuard = itemsNear(level, guardAt);
            helper.assertTrue(fromGuard.size() == 6, "The fighter dropped " + fromGuard.size() + " item(s), not its six pieces");
            boolean sword = false;
            for (ItemEntity item : fromGuard) {
                helper.assertTrue(!ArmyKits.isIssued(item.getItem()), "A dropped piece carries the mark of the issue: " + item.getItem());
                sword |= item.getItem().is(Items.IRON_SWORD) && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, item.getItem()) == 1;
            }
            helper.assertTrue(sword, "The sword of the fighter did not fall");
            end(helper, kingdom, area, List.of(killer), officer, fighter);
        });
    }

    // ---------------------------------------------------------------- the steps of an alarm

    @GameTest(template = EMPTY, batch = "kingdomworld_guardarmy_relief", timeoutTicks = 600)
    public static void anAlarmBringsTheViceCaptainThenTheCaptainThenTheArmy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper, 58, 8);
        noon(level);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Hard Pressed Guard", UUID.randomUUID());
        LawRules.Builder law = LawSettings.configured();
        law.reportMinTicks = 1_000_000;
        law.reportMaxTicks = 1_000_000;
        LawSettings.override(law.build());
        noShields();
        List<Citizen> all = new ArrayList<>();
        // Six fighters of the guard stand around the offender; the officers and the army hold their posts farther off.
        List<CitizenEntity> guards = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Citizen guard = soldier(helper, kingdom, 6 + i, i % 2 == 0 ? 2 : 5);
            all.add(guard);
            guards.add(body(helper, guard, 6 + i, i % 2 == 0 ? 2 : 5, false));
        }
        int[] posts = {30, 34, 40, 44, 48, 54};
        Citizen[] staff = new Citizen[posts.length];
        CitizenEntity[] bodies = new CitizenEntity[posts.length];
        for (int i = 0; i < posts.length; i++) {
            staff[i] = soldier(helper, kingdom, posts[i], 3);
            all.add(staff[i]);
            if (i >= 2) {
                helper.assertTrue(ArmyStaff.setBranch(server, null, staff[i].id(), Branch.ARMY).ok(), "The soldier did not join the army");
            }
        }
        // 0 the vice-captain of the guard, 1 its captain, 2 the vice-captain of the army, 3 and 5 its fighters, 4 its captain.
        helper.assertTrue(ArmyStaff.appoint(server, null, staff[0].id(), Post.VICE_CAPTAIN).ok() && ArmyStaff.appoint(server, null, staff[1].id(),
                Post.CAPTAIN).ok() && ArmyStaff.appoint(server, null, staff[2].id(), Post.VICE_CAPTAIN).ok() && ArmyStaff.appoint(server, null,
                staff[4].id(), Post.CAPTAIN).ok(), "The officers got no posts");
        for (int i = 0; i < posts.length; i++) {
            post(helper, kingdom, staff[i], posts[i], 3);
            bodies[i] = body(helper, staff[i], posts[i], 3, true);
        }
        ServerPlayer offender = player(helper, UUID.randomUUID(), "kw-rioter", 4, 3);
        offender.setGameMode(GameType.CREATIVE);
        double[] start = new double[posts.length];
        for (int i = 0; i < posts.length; i++) {
            start[i] = bodies[i].distanceTo(offender);
        }
        Citizen[] citizens = all.toArray(new Citizen[0]);
        Runnable[] wound = new Runnable[3];
        for (int stage = 0; stage < 3; stage++) {
            int first = stage * 2;
            wound[stage] = () -> {
                for (int i = first; i < first + 2; i++) {
                    guards.get(i).hurt(level.damageSources().playerAttack(offender), 20.0f);
                    helper.assertTrue(guards.get(i).isAlive() && guards.get(i).isWounded(), "The guard is not wounded");
                }
            };
        }
        java.util.function.BiConsumer<Integer, Boolean> came = (index, expected) -> {
            double now = bodies[index].distanceTo(offender);
            boolean moved = now < start[index] - 4.0;
            io.github.sekerator.kingdomworld.core.tactics.Duty duty = io.github.sekerator.kingdomworld.common.tactics.SquadTactics.duty(bodies[index]);
            helper.assertTrue(moved == expected, (expected ? "Did not come out: " : "Came out too early: ") + "soldier " + index + " at "
                    + now + " of " + start[index] + " blocks, step " + ArmyRelief.step(kingdom.id(), offender.getUUID()) + ", duty "
                    + (duty == null ? "none" : duty.role() + " at " + duty.target()) + ", target " + bodies[index].getTarget() + ", path "
                    + !bodies[index].getNavigation().isDone() + ", alive " + bodies[index].isAlive() + ", called "
                    + ArmyRelief.isCalled(staff[index].id()) + ", bodies in the world " + level.getEntities(
                    net.minecraft.world.level.entity.EntityTypeTest.forClass(CitizenEntity.class), entity -> true).size() + ", pursuit "
                    + java.util.Optional.ofNullable(io.github.sekerator.kingdomworld.common.law.LawWork.pursuit(server, bodies[index]))
                    .map(p -> p.player().getGameProfile().getName() + (p.urgent() ? " urgent" : "")).orElse("none") + ", fights "
                    + io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehaviors.fights(bodies[index]) + ", goals "
                    + bodies[index].goalSelector.getRunningGoals().map(goal -> goal.getGoal().getClass().getSimpleName()).toList() + ", ticking "
                    + level.isPositionEntityTicking(bodies[index].blockPosition()));
        };
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(LawAlarms.active(server, kingdom.id()).isEmpty(), "An alarm before anything happened");
            wound[0].run();
            Alarm alarm = LawAlarms.of(server, kingdom.id(), offender.getUUID()).orElseThrow(() -> new IllegalStateException("No alarm"));
            helper.assertTrue(alarm.guardsWounded() == 2, "The alarm does not count the wounded guards: " + alarm.guardsWounded());
        });
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(ArmyRelief.step(kingdom.id(), offender.getUUID()) == Relief.Step.GUARD_VICE, "Two guards out did not call the "
                    + "vice-captain: " + ArmyRelief.step(kingdom.id(), offender.getUUID()));
            came.accept(0, true);
            for (int i = 1; i < posts.length; i++) {
                came.accept(i, false);
            }
            wound[1].run();
        });
        helper.runAtTickTime(195, () -> {
            helper.assertTrue(ArmyRelief.step(kingdom.id(), offender.getUUID()) == Relief.Step.GUARD_CAPTAIN, "Four guards out did not call the "
                    + "captain: " + ArmyRelief.step(kingdom.id(), offender.getUUID()));
            came.accept(1, true);
            for (int i = 2; i < posts.length; i++) {
                came.accept(i, false);
            }
            wound[2].run();
        });
        helper.runAtTickTime(290, () -> {
            helper.assertTrue(ArmyRelief.step(kingdom.id(), offender.getUUID()) == Relief.Step.ARMY, "Six guards out did not call the army: "
                    + ArmyRelief.step(kingdom.id(), offender.getUUID()));
            // The vice-captain of the army leads half of its fighters out: the nearer one of the two.
            came.accept(2, true);
            came.accept(3, true);
            came.accept(4, false);
            came.accept(5, false);
            // The vice-captain of the army is beaten down: its captain comes with everybody left.
            bodies[2].setHealth(bodies[2].getMaxHealth() * 0.2f);
        });
        helper.runAtTickTime(385, () -> {
            helper.assertTrue(ArmyRelief.step(kingdom.id(), offender.getUUID()) == Relief.Step.ARMY_CAPTAIN, "The vice-captain of the army out "
                    + "did not call its captain: " + ArmyRelief.step(kingdom.id(), offender.getUUID()));
            came.accept(4, true);
            came.accept(5, true);
            helper.assertTrue(offender.isAlive() && offender.getHealth() == offender.getMaxHealth(), "A player in creative mode was hurt");
            end(helper, kingdom, area, List.of(offender), citizens);
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- back to the posts

    @GameTest(template = EMPTY, batch = "kingdomworld_guardarmy_return", timeoutTicks = 1200)
    public static void afterTheAlarmASoldierGoesBackToItsPost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper, 36, 8);
        noon(level);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Guard Post", UUID.randomUUID());
        LawRules.Builder law = LawSettings.configured();
        law.reportMinTicks = 1_000_000;
        law.reportMaxTicks = 1_000_000;
        LawSettings.override(law.build());
        Citizen struck = CitizenGameTests.newcomer(helper, kingdom, 5, 3);
        Citizen guard = soldier(helper, kingdom, 30, 3);
        post(helper, kingdom, guard, 30, 3);
        CitizenEntity victim = body(helper, struck, 5, 3, false);
        CitizenEntity soldier = body(helper, guard, 30, 3, true);
        ServerPlayer offender = player(helper, UUID.randomUUID(), "kw-brawler", 4, 3);
        offender.setGameMode(GameType.CREATIVE);
        BlockPos home = helper.absolutePos(new BlockPos(30, 0, 3));
        victim.hurt(level.damageSources().playerAttack(offender), 1.0f);
        helper.assertTrue(LawAlarms.active(server, kingdom.id()).size() == 1, "The blow raised no alarm");
        boolean[] came = {false};
        helper.onEachTick(() -> {
            if (!came[0] && soldier.distanceTo(offender) < 6.0) {
                came[0] = true;
                // The alarm runs out at once: nothing feeds it any more.
                LawRules.Builder over = LawSettings.configured();
                over.reportMinTicks = 1_000_000;
                over.reportMaxTicks = 1_000_000;
                over.alarmTicks = 20;
                LawSettings.override(over.build());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(came[0], "The guard did not come to the alarm: " + soldier.distanceTo(offender));
            helper.assertTrue(LawAlarms.active(server, kingdom.id()).isEmpty(), "The alarm still lasts");
            double away = Math.sqrt(soldier.distanceToSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5));
            helper.assertTrue(away < 4.0, "The guard is not back at its post: " + away + " blocks away");
            helper.assertTrue(soldier.getTarget() == null, "The guard still fights");
            end(helper, kingdom, area, List.of(offender), struck, guard);
        });
    }

    // ---------------------------------------------------------------- the fallen are replaced

    @GameTest(template = EMPTY, batch = "kingdomworld_guardarmy_replace")
    public static void aFallenOfficerIsReplacedAfterTheDaysAndOnlyWhenTheKitIsPaid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper, 12, 9);
        noon(level);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Fallen Staff", UUID.randomUUID());
        ArmyState army = ArmyWork.state(server);
        ForceRules rules = ArmySettings.force();
        long day = ArmyWork.day(server);
        Citizen[] soldiers = new Citizen[6];
        List<Citizen> all = new ArrayList<>();
        for (int i = 0; i < soldiers.length; i++) {
            soldiers[i] = soldier(helper, kingdom, 2 + i, 4);
            all.add(soldiers[i]);
        }
        army.reward(soldiers[1].id(), 500, 0, 0);
        army.reward(soldiers[2].id(), 900, 0, 0);
        for (int i = 0; i < 3; i++) {
            all.add(CitizenGameTests.newcomer(helper, kingdom, 2 + i, 6));
        }
        Citizen[] citizens = all.toArray(new Citizen[0]);
        fund(helper, kingdom, 50_000_000L);
        helper.assertTrue(ArmyStaff.appoint(server, null, soldiers[0].id(), Post.CAPTAIN).ok(), "The soldier got no post");
        helper.assertTrue(ArmyKits.issue(server, soldiers[0], record(server, soldiers[0]), false).whole(), "The captain got no kit");
        // The captain and two fighters fall far from players.
        BlockPos at = helper.absolutePos(new BlockPos(2, 0, 4));
        WorldPos place = new WorldPos(Dimensions.id(level), at.getX(), at.getY(), at.getZ());
        for (int i : new int[]{0, 4, 5}) {
            helper.assertTrue(CitizenLife.die(server, soldiers[i].id(), CauseOfDeath.BATTLE, "", place).isPresent(), "The soldier did not die");
        }
        ArmyWork.tick(server);
        helper.assertTrue(army.soldier(soldiers[0].id()).isEmpty() && army.losses(kingdom.id(), Branch.GUARD) == 3,
                "The fallen are not counted: " + army.losses(kingdom.id(), Branch.GUARD));
        helper.assertTrue(army.vacanciesOf(kingdom.id()).equals(java.util.Map.of(new ArmyState.Slot(Branch.GUARD, Post.CAPTAIN), day)),
                "The post of the fallen captain is not vacant from today: " + army.vacanciesOf(kingdom.id()));
        helper.assertTrue(KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.NETHERITE_SWORD)) == 0,
                "The issued sword of the fallen captain went to the storehouses");
        // An empty treasury: the fallen are replaced, a few a day, but no post is filled, for nobody pays its kit.
        helper.assertTrue(KingdomWorldApi.state(server).economy().armyPayment(kingdom.id(), funds(server, kingdom), day).ok(),
                "The treasury was not emptied");
        ArmyStaff.Report first = ArmyStaff.daily(server, kingdom.id());
        helper.assertTrue(first.recruited() == rules.replacePerDay && first.promoted() == 0 && first.issued() == 0 && first.paid() == 0,
                "The first day of an empty treasury: " + first);
        helper.assertTrue(army.branchOf(kingdom.id(), Branch.GUARD).size() == 5 && army.losses(kingdom.id(), Branch.GUARD) == 1,
                "Not two of the three fallen were replaced: " + army.branchOf(kingdom.id(), Branch.GUARD).size());
        helper.assertTrue(ArmyStaff.daily(server, kingdom.id()).equals(ArmyStaff.Report.NOTHING), "The staff was seen to twice a day");
        // Money comes, but the days of the fallen captain are not over: the post of the vice-captain, which nobody fell
        // from, is filled at once by the best fighter, that of the captain stays vacant.
        fund(helper, kingdom, 50_000_000L);
        army.setStaffDay(kingdom.id(), day - 1);
        long before = funds(server, kingdom);
        ArmyStaff.Report second = ArmyStaff.daily(server, kingdom.id());
        helper.assertTrue(second.recruited() == 1 && second.promoted() == 1 && second.issued() > 0, "The second day: " + second);
        helper.assertTrue(funds(server, kingdom) == before - second.paid() && second.paid() > 0, "The treasury did not pay what was issued");
        helper.assertTrue(army.officer(kingdom.id(), Branch.GUARD, Post.VICE_CAPTAIN).map(Soldier::citizenId).equals(java.util.Optional.of(
                soldiers[2].id())) && army.officer(kingdom.id(), Branch.GUARD, Post.CAPTAIN).isEmpty(), "The best fighter is not the vice-captain, "
                + "or the post of the captain was filled too early");
        helper.assertTrue(army.losses(kingdom.id(), Branch.GUARD) == 0 && army.branchOf(kingdom.id(), Branch.GUARD).size() == 6,
                "The guard is not whole again");
        // The days are over: the vice-captain rises to captain and the next best fighter takes its post.
        army.restoreVacancy(kingdom.id(), Branch.GUARD, Post.CAPTAIN, day - rules.officerReplaceDays);
        army.setStaffDay(kingdom.id(), day - 1);
        ArmyStaff.Report third = ArmyStaff.daily(server, kingdom.id());
        helper.assertTrue(third.promoted() == 2 && third.recruited() == 0, "The third day: " + third);
        helper.assertTrue(army.officer(kingdom.id(), Branch.GUARD, Post.CAPTAIN).map(Soldier::citizenId).equals(java.util.Optional.of(
                soldiers[2].id())) && army.officer(kingdom.id(), Branch.GUARD, Post.VICE_CAPTAIN).map(Soldier::citizenId)
                .equals(java.util.Optional.of(soldiers[1].id())), "The posts were not filled by the best fighters");
        helper.assertTrue(army.vacanciesOf(kingdom.id()).isEmpty(), "A filled post is still vacant");
        ItemStack helmet = ArmyWork.hands(server, soldiers[2].id()).get(EquipmentSlot.HEAD);
        helper.assertTrue(helmet.is(Items.NETHERITE_HELMET) && ArmyKits.isOfficerIssue(helmet), "The new captain wears no kit of a captain");
        helper.assertTrue(ArmyWork.hands(server, soldiers[1].id()).get(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET),
                "The new vice-captain wears no kit of a vice-captain");
        helper.assertTrue(ArmyWork.hands(server, soldiers[3].id()).get(EquipmentSlot.HEAD).is(Items.IRON_HELMET),
                "A fighter of the guard wears no kit of a fighter");
        // The gear of the post the vice-captain left was issued gear of an officer: it is gone, not in the storehouses.
        helper.assertTrue(KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.DIAMOND_HELMET)) == 0,
                "Issued gear of an officer went to the storehouses");
        end(helper, kingdom, area, List.of(), citizens);
        helper.succeed();
    }

    // ---------------------------------------------------------------- a king gives a post

    @GameTest(template = EMPTY, batch = "kingdomworld_guardarmy_appoint")
    public static void aKingGivesAPostWhichCountsOnlyInArmorOfThePost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper, 9, 9);
        noon(level);
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Appointing King", kingId);
        ServerPlayer king = player(helper, kingId, "kw-king", 2, 3);
        ServerPlayer stranger = player(helper, UUID.randomUUID(), "kw-stranger", 2, 5);
        Citizen citizen = soldier(helper, kingdom, 5, 5);
        Citizen other = soldier(helper, kingdom, 6, 5);
        CitizenEntity body = body(helper, citizen, 5, 5, false);
        helper.assertTrue(body.getMaxHealth() == 30.0f && body.getHealth() == 30.0f, "A fighter of the guard has not 30 health: "
                + body.getHealth() + "/" + body.getMaxHealth());
        ArmyPackets.TabChange vice = new ArmyPackets.TabChange(kingdom.id(), ArmyPackets.TabChange.Action.POST_VICE_CAPTAIN, 0, 0,
                List.of(citizen.id()));
        // Only who runs the army gives posts.
        KingdomActions.Feedback refused = ArmyActions.applyTab(server, stranger, vice);
        helper.assertTrue(!refused.success() && record(server, citizen).post() == Post.FIGHTER, "A stranger gave a post");
        helper.assertTrue(ArmyActions.applyTab(server, king, vice).success() && record(server, citizen).post() == Post.VICE_CAPTAIN,
                "The king gave no post");
        helper.assertTrue(ArmyStaff.wagePermille(server, citizen.id()) == 3_000 && ArmyStaff.wagePermille(server, other.id()) == 1_000,
                "An officer is not paid more than a fighter");
        // The post alone gives nothing.
        CitizenBodies.update(server);
        helper.assertTrue(body.getMaxHealth() == 30.0f, "A post without armor gives health: " + body.getMaxHealth());
        // Full enchanted diamond armor: the vice-captain has the health of its post.
        Item[] diamond = {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
        Item[] netherite = {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < slots.length; i++) {
            body.setItemSlot(slots[i], enchanted(diamond[i]));
        }
        CitizenBodies.update(server);
        helper.assertTrue(body.getMaxHealth() == 80.0f && body.getHealth() == 80.0f && citizen.maxHealth() == 80.0f,
                "A vice-captain in enchanted diamond has not 80 health: " + body.getHealth() + "/" + body.getMaxHealth());
        helper.assertTrue(ArmyActions.tabView(server, kingdom.id(), kingId).orElseThrow().soldierRows().stream()
                .anyMatch(row -> row.id().equals(citizen.id()) && row.branch() == Branch.GUARD && row.post() == Post.VICE_CAPTAIN
                        && row.maxHealth() == 80 && row.health() == 80), "The army tab does not show the branch, the post and the health");
        // One piece without an enchantment, or of iron, and the post counts for nothing.
        body.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        CitizenBodies.update(server);
        helper.assertTrue(body.getMaxHealth() == 30.0f, "Plain diamond boots count for the post");
        body.setItemSlot(EquipmentSlot.FEET, enchanted(Items.IRON_BOOTS));
        CitizenBodies.update(server);
        helper.assertTrue(body.getMaxHealth() == 30.0f, "Iron boots count for the post");
        body.setItemSlot(EquipmentSlot.FEET, enchanted(Items.NETHERITE_BOOTS));
        CitizenBodies.update(server);
        helper.assertTrue(body.getMaxHealth() == 80.0f, "Better armor than the post asks does not count");
        // A captain needs netherite: in diamond it is as strong as a fighter.
        helper.assertTrue(ArmyActions.applyTab(server, king, new ArmyPackets.TabChange(kingdom.id(), ArmyPackets.TabChange.Action.POST_CAPTAIN, 0,
                0, List.of(citizen.id()))).success() && record(server, citizen).post() == Post.CAPTAIN, "The king gave no post of a captain");
        CitizenBodies.update(server);
        helper.assertTrue(body.getMaxHealth() == 30.0f, "A captain in diamond has the health of its post");
        for (int i = 0; i < slots.length; i++) {
            body.setItemSlot(slots[i], enchanted(netherite[i]));
        }
        CitizenBodies.update(server);
        helper.assertTrue(body.getMaxHealth() == 120.0f, "A captain in enchanted netherite has not 120 health: " + body.getMaxHealth());
        // A branch has one captain: the next one takes the post.
        helper.assertTrue(ArmyActions.applyTab(server, king, new ArmyPackets.TabChange(kingdom.id(), ArmyPackets.TabChange.Action.POST_CAPTAIN, 0,
                0, List.of(other.id()))).success() && record(server, other).post() == Post.CAPTAIN
                && record(server, citizen).post() == Post.FIGHTER, "Two captains of the guard");
        // Moved to the army, the soldier is a fighter there, stronger than a guard.
        helper.assertTrue(ArmyActions.applyTab(server, king, new ArmyPackets.TabChange(kingdom.id(), ArmyPackets.TabChange.Action.BRANCH_ARMY, 0, 0,
                List.of(citizen.id()))).success() && record(server, citizen).branch() == Branch.ARMY, "The king moved no soldier to the army");
        CitizenBodies.update(server);
        helper.assertTrue(body.getMaxHealth() == 40.0f && Math.abs(body.getAttributeValue(Attributes.ATTACK_DAMAGE) - 2.0) < 1.0e-6,
                "A fighter of the army has not the strength of the army: " + body.getMaxHealth());
        // The gear came from the kingdom itself, not from an issue: dismissed, the soldier brings it back.
        helper.assertTrue(!ArmyKits.isIssued(body.getItemBySlot(EquipmentSlot.HEAD)), "Gear a player gave carries the mark of an issue");
        end(helper, kingdom, area, List.of(king, stranger), citizen, other);
        helper.succeed();
    }

    // ---------------------------------------------------------------- issued gear and other professions

    @GameTest(template = EMPTY, batch = "kingdomworld_guardarmy_profession")
    public static void issuedGearNeverReachesTheStorehousesWithItsMarkWhenAProfessionChanges(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper, 9, 9);
        noon(level);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Changed Trade", UUID.randomUUID());
        CitizenGameTests.storehouse(helper, 1, 1, 1);
        fund(helper, kingdom, 50_000_000L);
        Citizen officer = soldier(helper, kingdom, 4, 4);
        Citizen fighter = soldier(helper, kingdom, 6, 4);
        helper.assertTrue(ArmyStaff.appoint(server, null, officer.id(), Post.CAPTAIN).ok(), "The soldier got no post");
        helper.assertTrue(ArmyKits.issue(server, officer, record(server, officer), false).whole()
                && ArmyKits.issue(server, fighter, record(server, fighter), false).whole(), "The soldiers got no kits");
        java.util.function.LongSupplier marked = () -> KingdomStorage.count(server, kingdom.id(), ArmyKits::isIssued);
        java.util.function.ToLongFunction<Item> stored = item -> KingdomStorage.count(server, kingdom.id(), stack -> stack.is(item));
        helper.assertTrue(marked.getAsLong() == 0 && ArmyKits.isOfficerIssue(ArmyWork.hands(server, officer.id()).get(EquipmentSlot.MAINHAND))
                && ArmyKits.isOfficerIssue(ArmyWork.hands(server, officer.id()).get(EquipmentSlot.OFFHAND)), "The captain holds no issued gear");
        // A warrior becomes an archer: what it held does not suit the new work and leaves its hands. The sword and the
        // shield of the post are gone; they do not lie in a storehouse for anybody to take.
        helper.assertTrue(ArmyWork.recruit(server, null, kingdom.id(), officer.id(), "archer").ok(), "The captain did not become an archer");
        helper.assertTrue(marked.getAsLong() == 0 && stored.applyAsLong(Items.NETHERITE_SWORD) == 0 && stored.applyAsLong(Items.SHIELD) == 0,
                "Issued gear of an officer lies in the storehouses: " + marked.getAsLong() + " marked, "
                        + stored.applyAsLong(Items.NETHERITE_SWORD) + " sword(s), " + stored.applyAsLong(Items.SHIELD) + " shield(s)");
        helper.assertTrue(!ArmyKits.isIssued(ArmyWork.hands(server, officer.id()).get(EquipmentSlot.MAINHAND))
                && ArmyWork.hands(server, officer.id()).get(EquipmentSlot.OFFHAND).isEmpty(), "The captain still holds the gear of a warrior");
        helper.assertTrue(record(server, officer).post() == Post.CAPTAIN
                && ArmyKits.isOfficerIssue(ArmyWork.hands(server, officer.id()).get(EquipmentSlot.CHEST)), "The captain lost its post or its armor");
        // The gear of a fighter comes back as plain gear: one sword and one shield, without the mark of the issue.
        helper.assertTrue(ArmyWork.recruit(server, null, kingdom.id(), fighter.id(), "archer").ok(), "The fighter did not become an archer");
        ItemStack inHand = ArmyWork.hands(server, fighter.id()).get(EquipmentSlot.MAINHAND);
        helper.assertTrue(marked.getAsLong() == 0 && !ArmyKits.isIssued(inHand), "Gear of a fighter kept the mark of the issue");
        helper.assertTrue(stored.applyAsLong(Items.IRON_SWORD) + (inHand.is(Items.IRON_SWORD) ? 1 : 0) == 1
                && stored.applyAsLong(Items.SHIELD) == 1, "The sword and the shield of the fighter did not come back: "
                + stored.applyAsLong(Items.IRON_SWORD) + " sword(s), " + stored.applyAsLong(Items.SHIELD) + " shield(s)");
        // Somebody else gives the captain peaceful work, as a ruler or an operator may: none of its issued gear is left,
        // on it or in the storehouses.
        KingdomWorldApi.state(server).citizens().setProfession(officer, io.github.sekerator.kingdomworld.core.citizen.Professions.FARMER.id());
        io.github.sekerator.kingdomworld.common.citizen.CitizenActions.afterProfessionChange(server, officer);
        ArmyWork.tick(server);
        helper.assertTrue(ArmyWork.state(server).soldier(officer.id()).isEmpty(), "A farmer is still a soldier");
        for (EquipmentSlot slot : io.github.sekerator.kingdomworld.common.citizen.GearRegistry.SLOTS) {
            helper.assertTrue(!ArmyKits.isIssued(ArmyWork.hands(server, officer.id()).get(slot)), "A farmer wears issued gear in " + slot);
        }
        helper.assertTrue(marked.getAsLong() == 0 && stored.applyAsLong(Items.NETHERITE_CHESTPLATE) == 0
                && stored.applyAsLong(Items.NETHERITE_HELMET) == 0, "Issued armor of an officer lies in the storehouses");
        helper.assertTrue(officer.maxHealth() == Citizen.MAX_HEALTH, "A farmer has the health of a soldier: " + officer.maxHealth());
        end(helper, kingdom, area, List.of(), officer, fighter);
        helper.succeed();
    }

    // ---------------------------------------------------------------- shields

    /**
     * A soldier with a shield that looks east, where the players of these tests stand.
     */
    private static CitizenEntity shieldBearer(GameTestHelper helper, Citizen citizen, int x, int z) {
        CitizenEntity body = body(helper, citizen, x, z, false);
        body.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        body.setYRot(-90.0f);
        body.setYHeadRot(-90.0f);
        return body;
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_guardarmy_shield_law")
    public static void aBlowAShieldTookIsAnAssaultAllTheSame(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper, 9, 9);
        noon(level);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Shield Wall", UUID.randomUUID());
        LawRules.Builder law = LawSettings.configured();
        law.reportMinTicks = 1_000_000;
        law.reportMaxTicks = 1_000_000;
        LawSettings.override(law.build());
        ForceRules.Builder force = ArmySettings.configuredForce();
        force.blockChance = 0.9;
        ArmySettings.overrideForce(force.build());
        Citizen guard = soldier(helper, kingdom, 4, 4);
        CitizenEntity body = shieldBearer(helper, guard, 4, 4);
        List<ServerPlayer> players = new ArrayList<>();
        boolean warded = false;
        // Nine blows in ten are warded off; every blow comes from a player nobody has seen before, so that the first one
        // the shield takes is the only thing the law knows of that player.
        for (int i = 0; i < 40 && !warded; i++) {
            ServerPlayer striker = player(helper, UUID.randomUUID(), "kw-striker-" + i, 6, 4);
            players.add(striker);
            body.invulnerableTime = 0;
            body.setHealth(body.getMaxHealth());
            int worn = body.getItemBySlot(EquipmentSlot.OFFHAND).getDamageValue();
            helper.assertTrue(LawAlarms.of(server, kingdom.id(), striker.getUUID()).isEmpty(), "An alarm for a player who did nothing");
            body.hurt(level.damageSources().playerAttack(striker), 2.0f);
            if (body.getHealth() < body.getMaxHealth()) {
                continue;
            }
            warded = true;
            helper.assertTrue(body.getItemBySlot(EquipmentSlot.OFFHAND).getDamageValue() == worn + 1, "The shield that took the blow did not wear");
            Alarm alarm = LawAlarms.of(server, kingdom.id(), striker.getUUID()).orElseThrow(() -> new IllegalStateException(
                    "A blow the shield took raised no alarm"));
            helper.assertTrue(alarm.violent(LawWork.now(server), LawSettings.rules()) && alarm.fightTicks(LawWork.now(server) + 20) == 20,
                    "The fight does not count from the blow the shield took");
            Wanted wanted = LawWork.state(server).wanted(kingdom.id(), striker.getUUID()).orElseThrow(() -> new IllegalStateException(
                    "A blow at a soldier the shield took is no crime"));
            helper.assertTrue(wanted.crimes().size() == 1 && wanted.crimes().get(0).kind() == io.github.sekerator.kingdomworld.core.law.CrimeKind.ASSAULT,
                    "The record is wrong: " + wanted.crimes());
            helper.assertTrue(ArmyWork.mayAttack(server, kingdom.id(), striker), "The soldier may not answer a blow its shield took");
        }
        helper.assertTrue(warded, "No blow of forty was warded off at nine chances in ten");
        end(helper, kingdom, area, players, guard);
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_guardarmy_shield_once")
    public static void theShieldIsTriedOnceForABlowThatLands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper, 9, 9);
        noon(level);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest One Blow", UUID.randomUUID());
        noShields();
        Citizen guard = soldier(helper, kingdom, 4, 4);
        CitizenEntity body = shieldBearer(helper, guard, 4, 4);
        ServerPlayer striker = player(helper, UUID.randomUUID(), "kw-flurry", 6, 4);
        striker.setGameMode(GameType.CREATIVE);
        // A blow lands. For half a second the body takes no other, though the game asks about every attack.
        body.hurt(level.damageSources().playerAttack(striker), 2.0f);
        float health = body.getHealth();
        int worn = body.getItemBySlot(EquipmentSlot.OFFHAND).getDamageValue();
        helper.assertTrue(health < body.getMaxHealth() && body.invulnerableTime > 10, "The blow did not land");
        ForceRules.Builder force = ArmySettings.configuredForce();
        force.blockChance = 0.9;
        ArmySettings.overrideForce(force.build());
        for (int i = 0; i < 60; i++) {
            body.hurt(level.damageSources().playerAttack(striker), 2.0f);
        }
        // None of them was a blow: the shield did not wear and the chance was not tried sixty times.
        helper.assertTrue(body.getHealth() == health, "A blow landed on a body that takes none: " + body.getHealth());
        helper.assertTrue(body.getItemBySlot(EquipmentSlot.OFFHAND).getDamageValue() == worn, "The shield wore by "
                + (body.getItemBySlot(EquipmentSlot.OFFHAND).getDamageValue() - worn) + " for attacks that were no blows");
        // The next blow that would land is warded off or lands, once.
        body.invulnerableTime = 0;
        body.hurt(level.damageSources().playerAttack(striker), 2.0f);
        int after = body.getItemBySlot(EquipmentSlot.OFFHAND).getDamageValue();
        helper.assertTrue(after == worn + 1, "One blow wore the shield by " + (after - worn));
        end(helper, kingdom, area, List.of(striker), guard);
        helper.succeed();
    }

    // ---------------------------------------------------------------- the garrison of a kingdom of the world

    private static ArmySquad post(GameTestHelper helper, MinecraftServer server, UUID id, String duty) {
        return ArmyWork.state(server).squadOfDuty(id, duty).orElseThrow(() -> new IllegalStateException("Nobody stands at the post " + duty
                + ": " + ArmyWork.state(server).duties().values()));
    }

    /**
     * Where a post of the plan of a capital stands in the world: {x, z} on the street, {x, z, y} y layers above it.
     */
    private static WorldPos at(String dimension, io.github.sekerator.kingdomworld.core.npc.NpcSite site, int[] point) {
        BlockPos feet = NpcGameTests.feet(site, point);
        return new WorldPos(dimension, feet.getX(), feet.getY(), feet.getZ());
    }

    /**
     * Asserts that the guard of a styled capital with soldiers left after the posts of the capital holds the posts of its
     * castle: two at the gate of the castle, the treasury, and a round along the points of the wall of the castle in their order.
     */
    static void assertCastleDuties(GameTestHelper helper, io.github.sekerator.kingdomworld.core.npc.NpcSite site, UUID id) {
        MinecraftServer server = helper.getLevel().getServer();
        String dimension = Dimensions.id(helper.getLevel());
        io.github.sekerator.kingdomworld.core.npc.NpcComposition composition =
                io.github.sekerator.kingdomworld.common.npc.NpcSpawner.composition(site);
        io.github.sekerator.kingdomworld.common.npc.NpcGarrison.station(server, id);
        int[] gatePoint = composition.post("castle_gate").orElseThrow().points().get(0);
        ArmySquad gate = post(helper, server, id, "castle_gate");
        helper.assertTrue(gate.members().size() == 2 && gate.order().equals(Order.guard(at(dimension, site, gatePoint), 0)),
                "The gate of the castle is not guarded by two: " + gate.members().size() + " " + gate.order());
        int[] treasuryPoint = composition.post("treasury").orElseThrow().points().get(0);
        ArmySquad treasury = post(helper, server, id, "treasury");
        helper.assertTrue(!treasury.members().isEmpty() && treasury.order().equals(Order.hold(at(dimension, site, treasuryPoint))),
                "The treasury of the castle is not held: " + treasury.members().size() + " " + treasury.order());
        List<int[]> wallPoints = composition.post("castle_wall").orElseThrow().points();
        int corners = Math.min(wallPoints.size(), Order.MAX_POINTS);
        ArmySquad wall = post(helper, server, id, "castle_wall");
        helper.assertTrue(wall.members().size() >= 2 && wall.order().kind() == Order.Kind.PATROL
                && wall.order().points().size() == corners, "No round of two on the wall of the castle: " + wall.members().size()
                + " " + wall.order());
        for (int i = 0; i < corners; i++) {
            helper.assertTrue(wall.order().points().get(i).equals(at(dimension, site, wallPoints.get(i))), "The round of the wall of the "
                    + "castle goes through " + wall.order().points().get(i) + " instead of its point " + i);
        }
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_guardarmy_garrison", timeoutTicks = 200)
    public static void theGarrisonOfARaisedCapitalStandsAtItsPosts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        // A small styled capital, as new worlds have them.
        BlockPos center = NpcGameTests.platform(helper, NpcGameTests.HALF3);
        io.github.sekerator.kingdomworld.common.npc.NpcSettings.override(io.github.sekerator.kingdomworld.core.npc.NpcRules.DEFAULT);
        io.github.sekerator.kingdomworld.core.npc.NpcSite site = null;
        try {
            helper.assertTrue(io.github.sekerator.kingdomworld.common.npc.NpcWork.state(server).spawning().isEmpty(),
                    "Another kingdom of the world is appearing");
            site = NpcGameTests.plantStyled(helper, center, io.github.sekerator.kingdomworld.core.npc.NpcLevel.SMALL, "");
            Kingdom kingdom = NpcGameTests.raise(helper, site, NpcGameTests.STYLED_STEPS);
            UUID id = kingdom.id();
            ArmyState army = ArmyWork.state(server);
            io.github.sekerator.kingdomworld.core.npc.NpcComposition composition =
                    io.github.sekerator.kingdomworld.common.npc.NpcSpawner.composition(site);
            String dimension = Dimensions.id(level);
            // A small kingdom: ten of the guard and a royal army of fifty to a hundred, far larger, officers among them, in the
            // kits of their posts, and the kits cost the treasury nothing at the founding.
            int royal = io.github.sekerator.kingdomworld.common.npc.NpcGarrison.royalArmy(server, id);
            helper.assertTrue(royal >= 50 && royal <= 100, "A small kingdom wants an army of " + royal);
            helper.assertTrue(army.branchOf(id, Branch.GUARD).size() == 10 && army.branchOf(id, Branch.ARMY).size() == royal,
                    "The garrison is " + army.branchOf(id, Branch.GUARD).size() + " and " + army.branchOf(id, Branch.ARMY).size());
            for (Branch branch : Branch.values()) {
                for (Post post : new Post[]{Post.VICE_CAPTAIN, Post.CAPTAIN}) {
                    Soldier officer = army.officer(id, branch, post).orElseThrow(() -> new IllegalStateException("No " + post + " of the "
                            + branch));
                    ItemStack helmet = ArmyWork.hands(server, officer.citizenId()).get(EquipmentSlot.HEAD);
                    helper.assertTrue(ArmyKits.isOfficerIssue(helmet) && helmet.is(post == Post.CAPTAIN ? Items.NETHERITE_HELMET
                            : Items.DIAMOND_HELMET), "The " + post + " of the " + branch + " wears no kit: " + helmet);
                    helper.assertTrue(ArmyForce.standing(server, officer) == post, "The " + post + " has not the strength of its post");
                }
            }
            long money = io.github.sekerator.kingdomworld.core.npc.NpcRules.DEFAULT.money(io.github.sekerator.kingdomworld.core.npc.NpcLevel.SMALL);
            helper.assertTrue(funds(server, kingdom) == money, "The kits of the founding were paid: " + funds(server, kingdom) + " of " + money);
            // The guard and the reserve of the army sleep in bunks of the barracks; the rest of the army lives in the tents of
            // the army, in squads under their sergeants, and needs no bed.
            int quartered = 0;
            for (Soldier soldier : army.soldiersOf(id)) {
                Citizen citizen = KingdomWorldApi.state(server).citizens().get(soldier.citizenId()).orElseThrow();
                if (io.github.sekerator.kingdomworld.common.npc.NpcCamps.quartered(server, soldier.citizenId())) {
                    quartered++;
                    helper.assertTrue(soldier.branch() == Branch.ARMY && citizen.home() == null, "A soldier of a camp took a bed");
                    continue;
                }
                helper.assertTrue(citizen.home() != null && io.github.sekerator.kingdomworld.common.construction.BuildingEffects.inBarracks(server,
                        id, citizen.home()), "A soldier sleeps outside the barracks");
            }
            helper.assertTrue(quartered == royal - 6, "Of the army " + quartered + " live beyond the capital");
            for (ArmySquad squad : army.squadsOf(id)) {
                String duty = army.duty(squad.id()).orElse("");
                if (!duty.startsWith("field") && !duty.startsWith("camp")) {
                    continue;
                }
                long most = squad.members().stream().mapToLong(member -> army.soldier(member).orElseThrow().experience()).max().orElse(0);
                helper.assertTrue(squad.members().size() <= 20 && army.soldier(squad.commander()).orElseThrow().experience() == most
                        && io.github.sekerator.kingdomworld.common.npc.NpcGarrison.sergeant(server, squad.commander()),
                        "The squad " + duty + " of " + squad.members().size() + " is not under its sergeant");
                // Far from players a battle counts every soldier of the squad, not only those with a body.
                helper.assertTrue(ArmyWork.side(server, squad).fighters().size() == squad.members().size(), "A battle would count "
                        + ArmyWork.side(server, squad).fighters().size() + " of " + squad.members().size());
            }
            // Two inside the storehouse, a round about it with the vice-captain, two at every gate, the captain in the hall.
            ArmySquad stores = post(helper, server, id, "stores");
            int[] storesPoint = composition.post("stores").orElseThrow().points().get(0);
            helper.assertTrue(stores.members().size() == 2 && stores.order().equals(Order.hold(at(dimension, site, storesPoint))),
                    "The storehouse is not held by two: " + stores.members().size() + " " + stores.order());
            ArmySquad round = post(helper, server, id, "stores_round");
            helper.assertTrue(round.members().size() >= 2 && round.order().kind() == Order.Kind.PATROL && round.order().points().size() == 4
                    && round.members().contains(army.officer(id, Branch.GUARD, Post.VICE_CAPTAIN).orElseThrow().citizenId()),
                    "No round of two with the vice-captain about the storehouse: " + round.members().size() + " " + round.order());
            int gates = 0;
            for (io.github.sekerator.kingdomworld.core.npc.NpcComposition.Post named : composition.posts()) {
                if (named.name().startsWith("gate_")) {
                    gates++;
                    ArmySquad gate = post(helper, server, id, named.name());
                    helper.assertTrue(gate.members().size() == 2 && gate.order().kind() == Order.Kind.GUARD, "The gate " + named.name()
                            + " is not guarded by two: " + gate.members().size());
                }
            }
            helper.assertTrue(gates == 2, "A small capital has " + gates + " gates with posts");
            helper.assertTrue(post(helper, server, id, "palace_hall").members().contains(army.officer(id, Branch.GUARD, Post.CAPTAIN)
                    .orElseThrow().citizenId()), "The captain of the guard is not in the hall of the palace");
            // The posts of the castle come after those: the ten of the guard of a small capital are taken by the posts above.
            helper.assertTrue(composition.post("castle_gate").isPresent() && composition.post("treasury").isPresent()
                    && composition.post("castle_wall").isPresent(), "The castle of the capital names no posts of its own");
            // The army in reserve: the barracks with its vice-captain, the palace with its captain.
            ArmySquad reserve = post(helper, server, id, "reserve");
            ArmySquad palace = post(helper, server, id, "reserve_palace");
            helper.assertTrue(reserve.members().contains(army.officer(id, Branch.ARMY, Post.VICE_CAPTAIN).orElseThrow().citizenId())
                    && palace.members().contains(army.officer(id, Branch.ARMY, Post.CAPTAIN).orElseThrow().citizenId())
                    && reserve.members().size() + palace.members().size() == 6, "The army is not in reserve: " + reserve.members().size()
                    + " and " + palace.members().size());
            for (Soldier soldier : army.soldiersOf(id)) {
                helper.assertTrue(army.squadOf(soldier.citizenId()).flatMap(squad -> army.duty(squad.id())).isPresent(), "A soldier has no post");
            }
            // The posts are reckoned again, not once: nothing moves while nothing changed,
            helper.assertTrue(io.github.sekerator.kingdomworld.common.npc.NpcGarrison.station(server, id) == 0, "Soldiers changed posts for nothing");
            // and a gate that lost a guard gets one from a less important post.
            ArmySquad gate = post(helper, server, id, "gate_north");
            UUID fallen = gate.members().get(0);
            army.fell(fallen, ArmyWork.day(server));
            helper.assertTrue(post(helper, server, id, "gate_north").members().size() == 1, "The fallen guard still stands at the gate");
            helper.assertTrue(io.github.sekerator.kingdomworld.common.npc.NpcGarrison.station(server, id) == 1
                    && post(helper, server, id, "gate_north").members().size() == 2 && post(helper, server, id, "stores").members().size() == 2,
                    "The gate was not filled from a less important post");
            // The posts of the squads are kept with the army.
            ArmySavedData reread = ArmySavedData.load(ArmySavedData.get(server).save(new CompoundTag()));
            helper.assertTrue(!reread.isReadOnly() && reread.state().squadOfDuty(id, "stores").map(squad -> squad.id() == stores.id())
                    .orElse(false), "The posts were lost with the saved data");
        } finally {
            io.github.sekerator.kingdomworld.common.npc.NpcSettings.override(null);
            NpcGameTests.cleanUp(helper, site, center, NpcGameTests.HALF3);
        }
        helper.succeed();
    }

    /**
     * The ruler of a styled kingdom raises a camp of its army in its style beyond its capital.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_guardarmy_camp", timeoutTicks = 3000)
    public static void aRulerRaisesACampOfItsArmyOnItsOwnLand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = NpcGameTests.platform(helper, NpcGameTests.HALF3);
        io.github.sekerator.kingdomworld.common.npc.NpcSettings.override(io.github.sekerator.kingdomworld.core.npc.NpcRules.DEFAULT);
        helper.assertTrue(io.github.sekerator.kingdomworld.common.npc.NpcWork.state(server).spawning().isEmpty(),
                "Another kingdom of the world is appearing");
        io.github.sekerator.kingdomworld.core.npc.NpcSite site = NpcGameTests.plantStyled(helper, center,
                io.github.sekerator.kingdomworld.core.npc.NpcLevel.SMALL, "");
        UUID id;
        try {
            id = NpcGameTests.raise(helper, site, NpcGameTests.STYLED_STEPS).id();
            io.github.sekerator.kingdomworld.core.npc.NpcKingdom npc = io.github.sekerator.kingdomworld.common.npc.NpcWork.kingdom(server, id)
                    .orElseThrow();
            // The army beyond the capital waits at its barracks until the ruler raises a camp for it.
            helper.assertTrue(io.github.sekerator.kingdomworld.common.npc.NpcCamps.built(server, id).isEmpty(), "A camp stands already");
            io.github.sekerator.kingdomworld.core.npc.RulerDecision camp = io.github.sekerator.kingdomworld.common.npc.NpcCamps.decision(server,
                    npc);
            helper.assertTrue(camp != null && camp.kind() == io.github.sekerator.kingdomworld.core.npc.RulerDecision.Kind.CAMP,
                    "The ruler wants no camp: " + camp);
            io.github.sekerator.kingdomworld.common.npc.NpcRulers.carryOutNow(server, npc, camp);
        } catch (RuntimeException e) {
            io.github.sekerator.kingdomworld.common.npc.NpcSettings.override(null);
            NpcGameTests.cleanUp(helper, site, center, NpcGameTests.HALF3);
            throw e;
        }
        UUID kingdomId = id;
        io.github.sekerator.kingdomworld.core.npc.NpcSite placed = site;
        int[] ticks = {0};
        boolean[] over = {false};
        helper.onEachTick(() -> {
            if (over[0]) {
                return;
            }
            ticks[0]++;
            // A step a tick, as the server goes: its chunks load meanwhile.
            io.github.sekerator.kingdomworld.common.npc.NpcRulers.runJobs(server, 1);
            List<io.github.sekerator.kingdomworld.core.npc.ArmyCamp> built = io.github.sekerator.kingdomworld.common.npc.NpcCamps.built(server,
                    kingdomId);
            if (built.isEmpty() && ticks[0] < 2800) {
                return;
            }
            over[0] = true;
            try {
                helper.assertTrue(!built.isEmpty(), "No camp was raised: " + io.github.sekerator.kingdomworld.common.npc.NpcWork.state(server)
                        .camps(kingdomId));
                io.github.sekerator.kingdomworld.core.npc.ArmyCamp raised = built.get(0);
                String dimension = Dimensions.id(level);
                // On its own land, beyond the land the kingdom appeared with.
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        long chunk = io.github.sekerator.kingdomworld.core.territory.ChunkKey.of((raised.x() >> 4) + dx, (raised.z() >> 4) + dz);
                        helper.assertTrue(kingdomId.equals(KingdomWorldApi.state(server).territory().ownerOf(dimension, chunk)),
                                "The camp stands on land that is not the kingdom's");
                    }
                }
                int reach = NpcGameTests.landRadius(placed) * 16;
                helper.assertTrue(Math.max(Math.abs(raised.x() - placed.x()), Math.abs(raised.z() - placed.z())) > reach,
                        "The camp lies inside the land of the capital: " + raised);
                // Its buildings in the style of the kingdom: the drill yard, two barracks, a store and a watchtower.
                java.util.Map<String, Integer> found = new java.util.HashMap<>();
                for (io.github.sekerator.kingdomworld.core.construction.Building building : KingdomWorldApi.state(server).construction()
                        .buildingsOf(kingdomId)) {
                    if (Math.abs(building.anchor().x() - raised.x()) <= 30 && Math.abs(building.anchor().z() - raised.z()) <= 30) {
                        found.merge(building.templateId(), 1, Integer::sum);
                    }
                }
                String style = placed.styled() ? placed.style() : "";
                java.util.Map<String, Integer> wanted = new java.util.HashMap<>();
                for (io.github.sekerator.kingdomworld.core.npc.ArmyCamp.Building plan : io.github.sekerator.kingdomworld.core.npc.ArmyCamp
                        .buildings(style)) {
                    wanted.merge(plan.template(), 1, Integer::sum);
                }
                helper.assertTrue(found.equals(wanted), "The camp of the style '" + style + "' has " + found + " instead of " + wanted);
                // The squads of the army beyond the capital stand in the camp, quartered there, each under its sergeant.
                ArmyState army = ArmyWork.state(server);
                int inCamp = 0;
                for (ArmySquad squad : army.squadsOf(kingdomId)) {
                    String duty = army.duty(squad.id()).orElse("");
                    if (!duty.startsWith("camp")) {
                        continue;
                    }
                    WorldPos at = squad.order().points().get(0);
                    helper.assertTrue(Math.abs(at.x() - raised.x()) <= io.github.sekerator.kingdomworld.core.npc.ArmyCamp.REACH
                            && Math.abs(at.z() - raised.z()) <= io.github.sekerator.kingdomworld.core.npc.ArmyCamp.REACH,
                            "The squad " + duty + " stands outside the camp: " + at);
                    for (UUID member : squad.members()) {
                        helper.assertTrue(io.github.sekerator.kingdomworld.common.npc.NpcCamps.quartered(server, member), "A soldier of the camp has "
                                + "no quarters");
                        inCamp++;
                    }
                }
                int beyond = io.github.sekerator.kingdomworld.common.npc.NpcGarrison.royalArmy(server, kingdomId) - 6;
                helper.assertTrue(inCamp == Math.min(beyond, io.github.sekerator.kingdomworld.core.npc.NpcRules.DEFAULT.campSoldiers()),
                        "The camp holds " + inCamp + " of " + beyond);
                helper.succeed();
            } finally {
                io.github.sekerator.kingdomworld.common.npc.NpcSettings.override(null);
                NpcGameTests.cleanUp(helper, placed, center, NpcGameTests.HALF3);
            }
        });
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_guardarmy_warhome", timeoutTicks = 200)
    public static void theSquadsOfACampComeHomeAfterAWarWhoeverHoldsTheirPlace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = NpcGameTests.platform(helper);
        io.github.sekerator.kingdomworld.common.npc.NpcSettings.override(io.github.sekerator.kingdomworld.core.npc.NpcRules.DEFAULT);
        io.github.sekerator.kingdomworld.core.npc.NpcSite site = null;
        Kingdom enemy = null;
        try {
            site = NpcGameTests.plant(helper, center);
            UUID id = NpcGameTests.raise(helper, site, NpcGameTests.STEPS).id();
            ArmyState army = ArmyWork.state(server);
            List<ArmySquad> field = io.github.sekerator.kingdomworld.common.npc.NpcGarrison.fieldArmy(server, id);
            helper.assertTrue(field.size() >= 2, "The kingdom has " + field.size() + " squad(s) beyond its capital");
            // A kingdom of players far off: founding one on the platform would take the land of the kingdom of the world.
            BlockPos throne = center.offset(320, 0, 0);
            level.setBlockAndUpdate(throne, io.github.sekerator.kingdomworld.common.registry.ModRegistries.THRONE.get().defaultBlockState());
            io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult created = KingdomWorldApi.foundKingdom(level, throne,
                    "GameTest War Home", 0x2E86C1, UUID.randomUUID());
            helper.assertTrue(created instanceof io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult.Created,
                    "The enemy was not founded: " + created);
            enemy = ((io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult.Created) created).kingdom();
            Kingdom attacker = KingdomWorldApi.kingdom(server, id).orElseThrow(() -> new IllegalStateException("The kingdom of the world is gone"));
            io.github.sekerator.kingdomworld.core.diplomacy.War war = WarGameTests.war(helper, attacker, enemy,
                    io.github.sekerator.kingdomworld.core.diplomacy.WarGoal.RAID);
            // Two squads of the camps hold a city that is their own land now (taken, or the enemy gone), another besieges.
            WorldPos taken = ArmyWork.capitalOf(server, id);
            List<ArmySquad> sent = List.of(field.get(0), field.get(1));
            helper.assertTrue(ArmyWork.order(server, null, sent.get(0).id(), Order.hold(taken)).ok()
                    && ArmyWork.order(server, null, sent.get(1).id(), Order.siege(taken)).ok(), "The squads took no orders of war");
            long warId = war.id();
            war = io.github.sekerator.kingdomworld.common.diplomacy.WarWork.state(server).war(warId)
                    .orElseThrow(() -> new IllegalStateException("The war #" + warId + " is gone before its end"));
            helper.assertTrue(io.github.sekerator.kingdomworld.common.diplomacy.WarWork.end(server, war,
                    io.github.sekerator.kingdomworld.core.diplomacy.War.Result.ADMIN,
                    io.github.sekerator.kingdomworld.core.diplomacy.PeaceTerms.WHITE).ok(), "The war did not end");
            // At peace they come home: the order of war is gone at once and the next stationing puts them back in the camp.
            io.github.sekerator.kingdomworld.common.npc.NpcGarrison.station(server, id);
            for (ArmySquad squad : sent) {
                ArmySquad now = army.squad(squad.id()).orElse(null);
                helper.assertTrue(now != null, "The squad #" + squad.id() + " is gone after the peace");
                String duty = army.duty(now.id()).orElse("");
                helper.assertTrue(now.order().kind() == Order.Kind.GUARD && (duty.startsWith("camp") || duty.startsWith("field")),
                        "The squad " + duty + " stays at war after the peace: " + now.order());
            }
        } finally {
            io.github.sekerator.kingdomworld.common.npc.NpcSettings.override(null);
            if (enemy != null) {
                io.github.sekerator.kingdomworld.common.diplomacy.WarWork.markPresent(enemy.id(), null);
                site.kingdomId().ifPresent(npc -> io.github.sekerator.kingdomworld.common.diplomacy.WarWork.markPresent(npc, null));
                KingdomWorldApi.dissolveKingdom(server, enemy.id());
                level.setBlockAndUpdate(center.offset(320, 0, 0), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
            NpcGameTests.cleanUp(helper, site, center);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_guardarmy_oldkingdom", timeoutTicks = 200)
    public static void aKingdomRaisedBeforeGetsPostsAndFillsItsRanksByAndBy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = NpcGameTests.platform(helper);
        io.github.sekerator.kingdomworld.core.npc.NpcSite site = null;
        try {
            helper.assertTrue(io.github.sekerator.kingdomworld.common.npc.NpcWork.state(server).spawning().isEmpty(),
                    "Another kingdom of the world is appearing");
            // A kingdom of the first layout raised before the royal armies: no named posts, a handful of soldiers.
            io.github.sekerator.kingdomworld.core.npc.NpcRules d = io.github.sekerator.kingdomworld.core.npc.NpcRules.DEFAULT;
            io.github.sekerator.kingdomworld.common.npc.NpcSettings.override(new io.github.sekerator.kingdomworld.core.npc.NpcRules(
                    d.generate(), d.count(), d.radius(), d.minDistance(), d.minSpawnDistance(), d.triggerChunks(), d.sizeQuota(),
                    d.sizeWeights(), d.landRadius(), d.towns(), d.townDistance(), d.money(), d.foodDays(), d.materials(), d.soldiers(),
                    d.blocksPerTick(), d.chunksPerTick(), d.maxInhabitedMinutes(), d.attemptsPerSite(), d.guards(), d.army(), d.townGuards(),
                    new int[]{0, 0, 0}, new int[]{0, 0, 0}, d.armyPerDay(), d.campSoldiers(), d.campBodies(), d.armyBodies()));
            site = io.github.sekerator.kingdomworld.common.npc.NpcWork.planGridAt(server, center.getX(), center.getZ(),
                    io.github.sekerator.kingdomworld.core.npc.NpcLevel.SMALL, List.of()).orElse(null);
            helper.assertTrue(site != null, "The data of the kingdoms of the world is read-only");
            Kingdom kingdom = NpcGameTests.raise(helper, site, NpcGameTests.STEPS);
            // The large armies come with this version: the kingdom fills its army by and by.
            io.github.sekerator.kingdomworld.common.npc.NpcSettings.override(d);
            UUID id = kingdom.id();
            ArmyState army = ArmyWork.state(server);
            int before = army.soldiersOf(id).size();
            helper.assertTrue(before == 2 && army.branchOf(id, Branch.GUARD).size() == 2, "A kingdom of the first layout starts with " + before);
            // What stands in for the named posts: the gates of its wall, or without a wall a storehouse of its register of
            // buildings. The two soldiers hold the most important post together.
            List<WorldPos> gates = io.github.sekerator.kingdomworld.common.road.CityWalls.gates(server, id, 0L);
            ArmySquad first = post(helper, server, id, gates.isEmpty() ? "stores" : "gate_0");
            helper.assertTrue(first.members().size() == 2, "The first post is not held by two: " + first.members().size());
            if (gates.isEmpty()) {
                WorldPos stores = KingdomWorldApi.state(server).construction().buildingsOf(id).stream()
                        .filter(building -> io.github.sekerator.kingdomworld.core.construction.BuildingRoles.WAREHOUSE.equals(building.role()))
                        .map(io.github.sekerator.kingdomworld.core.construction.Building::anchor).filter(first.order().points()::contains)
                        .findFirst().orElse(null);
                helper.assertTrue(stores != null && first.order().kind() == Order.Kind.HOLD, "The soldiers do not hold a storehouse: "
                        + first.order());
            } else {
                helper.assertTrue(first.order().equals(Order.guard(gates.get(0), 0)), "The soldiers do not guard the first gate: " + first.order());
            }
            // The ranks fill by and by: the guard by two recruits a day from the citizens without work, never half of the
            // people, and the royal army by five recruits a day from abroad.
            int[] wanted = io.github.sekerator.kingdomworld.common.npc.NpcGarrison.wanted(server, id);
            int people = KingdomWorldApi.state(server).citizens().registry().citizensOf(id).size();
            helper.assertTrue(wanted[0] > before && wanted[0] <= people / 2 && wanted[1] >= 50 && wanted[1] <= 100, "The garrison wanted is "
                    + wanted[0] + " and " + wanted[1] + " for " + people + " people");
            int perDay = ArmySettings.force().replacePerDay + d.armyPerDay();
            BlockPos throne = io.github.sekerator.kingdomworld.common.npc.NpcSpawner.thronePos(site);
            WorldPos place = new WorldPos(Dimensions.id(level), throne.getX(), throne.getY(), throne.getZ() + 3);
            for (int i = 0; i < 5; i++) {
                helper.assertTrue(CitizenLife.arrive(server, id, place, false).isPresent(), "No newcomer arrived");
            }
            fund(helper, kingdom, 5_000_000L);
            army.setStaffDay(id, ArmyWork.day(server) - 1);
            io.github.sekerator.kingdomworld.common.npc.NpcGarrison.daily(server, id);
            int after = army.soldiersOf(id).size();
            helper.assertTrue(after == before + perDay && army.branchOf(id, Branch.ARMY).size() == d.armyPerDay(), "The ranks grew by "
                    + (after - before) + " in a day, the army is " + army.branchOf(id, Branch.ARMY).size());
            // The same day again changes nothing; the next day brings the next recruits, and every one of them has a post.
            io.github.sekerator.kingdomworld.common.npc.NpcGarrison.daily(server, id);
            helper.assertTrue(army.soldiersOf(id).size() == after, "The ranks grew twice in a day");
            army.setStaffDay(id, ArmyWork.day(server) - 1);
            io.github.sekerator.kingdomworld.common.npc.NpcGarrison.daily(server, id);
            helper.assertTrue(army.soldiersOf(id).size() == after + perDay, "The ranks did not grow the next day");
            for (Soldier soldier : army.soldiersOf(id)) {
                helper.assertTrue(army.squadOf(soldier.citizenId()).flatMap(squad -> army.duty(squad.id())).isPresent(), "A recruit has no post");
                helper.assertTrue(ArmyKits.isIssued(ArmyWork.hands(server, soldier.citizenId()).get(EquipmentSlot.CHEST)), "A soldier has no kit");
            }
            helper.assertTrue(army.squadOfDuty(id, "palace_hall").isPresent(), "The recruits did not take the post at the throne");
        } finally {
            io.github.sekerator.kingdomworld.common.npc.NpcSettings.override(null);
            NpcGameTests.cleanUp(helper, site, center);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- old data

    @GameTest(template = EMPTY, batch = "kingdomworld_guardarmy_data")
    public static void armyDataOlderThanTheBranchesGetsThemByTheOrdersOfTheSquads(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper, 9, 9);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Old Army", UUID.randomUUID());
        Citizen holding = soldier(helper, kingdom, 3, 3);
        Citizen marching = soldier(helper, kingdom, 4, 3);
        Citizen idle = soldier(helper, kingdom, 5, 3);
        post(helper, kingdom, holding, 3, 3);
        ArmySquad squad = ArmyWork.state(server).formSquad(null, kingdom.id(), List.of(marching.id()), ArmySettings.rules(),
                KingdomWorldApi.state(server)).squad().orElseThrow();
        BlockPos to = helper.absolutePos(new BlockPos(7, 0, 7));
        helper.assertTrue(ArmyWork.order(server, null, squad.id(), Order.move(new WorldPos(Dimensions.id(level), to.getX(), to.getY(), to.getZ())))
                .ok(), "The squad took no order");
        helper.assertTrue(ArmyStaff.setBranch(server, null, idle.id(), Branch.ARMY).ok() && ArmyStaff.appoint(server, null, idle.id(),
                Post.CAPTAIN).ok(), "The soldier got no post");
        ArmyWork.state(server).fell(soldier(helper, kingdom, 6, 3).id(), 7);
        // The data of today keeps branches, posts, the count of the fallen and the vacant posts.
        CompoundTag saved = ArmySavedData.get(server).save(new CompoundTag());
        helper.assertTrue(saved.getInt("formatVersion") == 3, "The army data is not of format 3: " + saved.getInt("formatVersion"));
        ArmySavedData reread = ArmySavedData.load(saved.copy());
        helper.assertTrue(!reread.isReadOnly() && !reread.isDirty(), "The army data of today does not read back: " + reread.readOnlyReason());
        ArmyState again = reread.state();
        helper.assertTrue(again.soldier(idle.id()).map(soldier -> soldier.branch() == Branch.ARMY && soldier.post() == Post.CAPTAIN).orElse(false)
                && again.soldier(holding.id()).map(soldier -> soldier.branch() == Branch.GUARD).orElse(false)
                && again.losses(kingdom.id(), Branch.GUARD) == 1, "Branches, posts or losses were lost on the way");
        // Data of format 2 knows no branches: the guard is who holds, guards or patrols, the army everybody else.
        CompoundTag old = saved.copy();
        old.putInt("formatVersion", 2);
        old.remove("losses");
        old.remove("vacancies");
        old.remove("staff");
        ListTag soldiers = old.getList("soldiers", Tag.TAG_COMPOUND);
        for (int i = 0; i < soldiers.size(); i++) {
            soldiers.getCompound(i).remove("branch");
            soldiers.getCompound(i).remove("post");
        }
        ArmySavedData migrated = ArmySavedData.load(old);
        helper.assertTrue(!migrated.isReadOnly() && migrated.isDirty(), "Old army data was not taken over: " + migrated.readOnlyReason());
        ArmyState state = migrated.state();
        helper.assertTrue(state.soldier(holding.id()).orElseThrow().branch() == Branch.GUARD, "A soldier who holds a point is not in the guard");
        helper.assertTrue(state.soldier(marching.id()).orElseThrow().branch() == Branch.ARMY
                && state.soldier(idle.id()).orElseThrow().branch() == Branch.ARMY, "Soldiers without a post to hold are not in the army");
        for (Soldier soldier : state.soldiersOf(kingdom.id())) {
            helper.assertTrue(soldier.post() == Post.FIGHTER, "Old data made an officer");
        }
        helper.assertTrue(migrated.save(new CompoundTag()).getInt("formatVersion") == 3, "Taken over data is not saved in format 3");
        // A branch or a post this version does not know shuts nothing: the soldier is kept, as a fighter of the guard.
        CompoundTag strange = saved.copy();
        ListTag odd = strange.getList("soldiers", Tag.TAG_COMPOUND);
        for (int i = 0; i < odd.size(); i++) {
            if (odd.getCompound(i).getUUID("citizen").equals(idle.id())) {
                odd.getCompound(i).putString("branch", "navy");
                odd.getCompound(i).putString("post", "admiral");
            }
        }
        CompoundTag lostAtSea = new CompoundTag();
        lostAtSea.putUUID("kingdom", kingdom.id());
        lostAtSea.putString("branch", "navy");
        lostAtSea.putInt("count", 5);
        strange.getList("losses", Tag.TAG_COMPOUND).add(lostAtSea);
        ArmySavedData kept = ArmySavedData.load(strange);
        helper.assertTrue(!kept.isReadOnly(), "An unknown branch shut the whole army: " + kept.readOnlyReason());
        helper.assertTrue(kept.state().soldiersOf(kingdom.id()).size() == ArmyWork.state(server).soldiersOf(kingdom.id()).size(),
                "A soldier was lost with its unknown branch");
        helper.assertTrue(kept.state().soldier(idle.id()).map(soldier -> soldier.branch() == Branch.GUARD && soldier.post() == Post.FIGHTER)
                .orElse(false), "A soldier of an unknown branch and post is no fighter of the guard");
        helper.assertTrue(kept.state().soldier(holding.id()).map(soldier -> soldier.branch() == Branch.GUARD).orElse(false)
                && kept.state().soldier(marching.id()).isPresent() && kept.state().squadsOf(kingdom.id()).size() == 2,
                "The other soldiers and their squads did not come through");
        helper.assertTrue(kept.state().losses(kingdom.id(), Branch.GUARD) == 1 && kept.state().losses(kingdom.id(), Branch.ARMY) == 0,
                "The fallen of an unknown branch were counted somewhere");
        // What was understood with a stand-in is saved as it is understood.
        helper.assertTrue(kept.isDirty() && kept.save(new CompoundTag()).getList("soldiers", Tag.TAG_COMPOUND).stream()
                .noneMatch(entry -> ((CompoundTag) entry).getString("branch").equals("navy")), "The stand-in is not saved");
        // Data of a newer format is kept as it is.
        CompoundTag newer = saved.copy();
        newer.putInt("formatVersion", 4);
        helper.assertTrue(ArmySavedData.load(newer).isReadOnly(), "Army data of a newer format is not read-only");
        end(helper, kingdom, area, List.of(), holding, marching, idle);
        helper.succeed();
    }
}
