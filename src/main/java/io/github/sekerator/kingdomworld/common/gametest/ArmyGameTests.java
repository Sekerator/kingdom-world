package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyActions;
import io.github.sekerator.kingdomworld.common.army.ArmyCaravans;
import io.github.sekerator.kingdomworld.common.army.ArmySavedData;
import io.github.sekerator.kingdomworld.common.army.ArmySettings;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.work.Equipment;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.common.network.packet.ArmyPackets;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.common.trade.TradeWork;
import io.github.sekerator.kingdomworld.core.army.ArmyRules;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.ArmyState;
import io.github.sekerator.kingdomworld.core.army.Mercenary;
import io.github.sekerator.kingdomworld.core.army.MercenaryBand;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.army.Soldier;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.DeathRecord;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.combat.Side;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.trade.Caravan;
import io.github.sekerator.kingdomworld.core.trade.Deal;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/**
 * Game tests of the army: a recruited citizen takes the best weapon and armor of the storehouses and brings them back when
 * dismissed; a soldier kills a monster on the kingdom's land and never attacks a foreign player in peace; an unpaid band
 * of mercenaries leaves and no money appears; guards save a caravan from an ambush; a soldier who falls far from players
 * goes to the chronicle; the server checks the right to run the army; the army survives saving, newer data stays read-only
 * and the marshal gets the new right on upgrade. Every test runs in a batch of its own and clears what it built.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArmyGameTests {
    private static final String EMPTY = "empty";

    private ArmyGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    private static ServerPlayer player(GameTestHelper helper, UUID id, String name) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(id, name));
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return player;
    }

    private static void join(MinecraftServer server, Kingdom kingdom, UUID player, Role role) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        state.membership().invite(kingdom.rulerId(), player, 1L);
        state.membership().acceptInvitation(player, kingdom.id(), 1L);
        if (role != Role.SUBJECT) {
            state.membership().setRole(kingdom.rulerId(), player, role);
        }
    }

    private static Citizen soldier(GameTestHelper helper, Kingdom kingdom, int x, String profession) {
        MinecraftServer server = helper.getLevel().getServer();
        Citizen citizen = CitizenGameTests.newcomer(helper, kingdom, x, 5);
        helper.assertTrue(ArmyWork.recruit(server, null, kingdom.id(), citizen.id(), profession).ok(), "The citizen was not recruited");
        return citizen;
    }

    private static ArmySquad squad(MinecraftServer server, Kingdom kingdom, Citizen... members) {
        List<UUID> ids = java.util.Arrays.stream(members).map(Citizen::id).toList();
        return ArmyWork.state(server).formSquad(null, kingdom.id(), ids, ArmySettings.rules(), KingdomWorldApi.state(server)).squad()
                .orElseThrow(() -> new IllegalStateException("The squad was not formed"));
    }

    /**
     * Numbers for battles: the first ones as given, then the last one again.
     */
    private static DoubleSupplier rolls(double... values) {
        return new DoubleSupplier() {
            private int next;

            @Override
            public double getAsDouble() {
                return values[Math.min(next++, values.length - 1)];
            }
        };
    }

    private static void end(GameTestHelper helper, Kingdom kingdom, BlockPos from, BlockPos to, Citizen... citizens) {
        ArmyWork.overrideRolls(null);
        ArmySettings.override(null);
        ArmyWork.state(helper.getLevel().getServer()).forgetKingdom(kingdom.id());
        CitizenGameTests.finish(helper, kingdom, citizens);
        GatheringGameTests.clear(helper.getLevel(), from, to);
    }

    private static BlockPos from(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(-1, 0, -1));
    }

    private static BlockPos to(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(8, 3, 8));
    }

    // ---------------------------------------------------------------- gear

    @GameTest(template = EMPTY, batch = "kingdomworld_army_recruit")
    public static void aRecruitTakesTheBestGearAndBringsItBackWhenDismissed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GatheringGameTests.clear(level, from(helper), to(helper));
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Iron Legion", UUID.randomUUID());
        StorehouseInventory storehouse = CitizenGameTests.storehouse(helper, 0, 0, 4);
        storehouse.insertItem(0, new ItemStack(Items.WOODEN_SWORD), false);
        storehouse.insertItem(1, new ItemStack(Items.IRON_SWORD), false);
        storehouse.insertItem(2, new ItemStack(Items.DIAMOND_SWORD), false);
        storehouse.insertItem(3, new ItemStack(Items.IRON_CHESTPLATE), false);
        storehouse.insertItem(4, new ItemStack(Items.LEATHER_CHESTPLATE), false);
        storehouse.insertItem(5, new ItemStack(Items.SHIELD), false);
        storehouse.insertItem(6, new ItemStack(Items.BOW), false);
        storehouse.insertItem(7, new ItemStack(Items.CROSSBOW), false);
        Citizen warrior = null;
        Citizen archer = null;
        try {
            warrior = soldier(helper, kingdom, 1, Professions.WARRIOR.id());
            Equipment.Hands hands = ArmyWork.hands(server, warrior.id());
            helper.assertTrue(hands.get(EquipmentSlot.MAINHAND).is(Items.DIAMOND_SWORD), "The warrior did not take the best sword: "
                    + hands.get(EquipmentSlot.MAINHAND));
            helper.assertTrue(hands.get(EquipmentSlot.OFFHAND).is(Items.SHIELD), "The warrior took no shield");
            helper.assertTrue(hands.get(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "The warrior did not take the best armor");
            archer = soldier(helper, kingdom, 2, Professions.ARCHER.id());
            helper.assertTrue(ArmyWork.hands(server, archer.id()).get(EquipmentSlot.MAINHAND).is(Items.CROSSBOW),
                    "The archer did not take the crossbow");
            helper.assertTrue(CitizenGameTests.count(storehouse, Items.DIAMOND_SWORD) == 0 && CitizenGameTests.count(storehouse, Items.IRON_SWORD) == 1,
                    "The gear did not leave the storehouse");

            helper.assertTrue(ArmyWork.dismiss(server, null, warrior.id()).ok(), "The warrior was not dismissed");
            helper.assertTrue(warrior.professionId().equals(Professions.UNEMPLOYED.id()), "The dismissed warrior is still a soldier");
            helper.assertTrue(ArmyWork.hands(server, warrior.id()).get(EquipmentSlot.MAINHAND).isEmpty(), "The dismissed warrior kept the sword");
            helper.assertTrue(CitizenGameTests.count(storehouse, Items.DIAMOND_SWORD) == 1 && CitizenGameTests.count(storehouse, Items.SHIELD) == 1
                    && CitizenGameTests.count(storehouse, Items.IRON_CHESTPLATE) == 1, "The gear did not come back to the storehouse");
            helper.assertTrue(CitizenGameTests.count(storehouse, Items.WOODEN_SWORD) == 1, "Gear appeared or vanished");
        } finally {
            if (archer != null) {
                end(helper, kingdom, from(helper), to(helper), warrior, archer);
            } else {
                end(helper, kingdom, from(helper), to(helper));
            }
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- fights near players

    @GameTest(template = EMPTY, batch = "kingdomworld_army_fight", timeoutTicks = 600)
    public static void aSoldierKillsAMonsterOnTheLand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GatheringGameTests.clear(level, from(helper), to(helper));
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Watchful Keep", UUID.randomUUID());
        CitizenGameTests.storehouse(helper, 0, 0, 4).insertItem(0, new ItemStack(Items.DIAMOND_SWORD), false);
        Citizen citizen = soldier(helper, kingdom, 1, Professions.WARRIOR.id());
        CitizenBodies.force(citizen.id(), true);
        CitizenBodies.update(server);
        CitizenEntity body = CitizenBodies.body(citizen.id());
        helper.assertTrue(body != null, "The soldier got no body");
        // A husk does not burn in the sun, so only the soldier can kill it.
        Husk zombie = helper.spawn(EntityType.HUSK, new BlockPos(4, 1, 5));
        zombie.setNoAi(true);
        helper.succeedWhen(() -> {
            helper.assertTrue(!zombie.isAlive(), "The zombie is still alive");
            Soldier record = ArmyWork.state(server).soldier(citizen.id()).orElseThrow();
            helper.assertTrue(record.kills() >= 1 && record.experience() > 0, "The kill gave no experience of war");
            end(helper, kingdom, from(helper), to(helper), citizen);
        });
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_army_peace", timeoutTicks = 200)
    public static void aSoldierNeverAttacksAForeignPlayerInPeace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GatheringGameTests.clear(level, from(helper), to(helper));
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Peaceful Guard", UUID.randomUUID());
        CitizenGameTests.storehouse(helper, 0, 0, 4).insertItem(0, new ItemStack(Items.IRON_SWORD), false);
        Citizen citizen = soldier(helper, kingdom, 1, Professions.WARRIOR.id());
        CitizenBodies.force(citizen.id(), true);
        CitizenBodies.update(server);
        CitizenEntity body = CitizenBodies.body(citizen.id());
        ServerPlayer stranger = player(helper, UUID.randomUUID(), "kw-stranger");
        helper.assertTrue(body != null, "The soldier got no body");
        helper.assertTrue(!ArmyWork.mayAttack(server, kingdom.id(), stranger), "A soldier may attack a player in peace");
        float health = stranger.getHealth();
        helper.assertTrue(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingAttackEvent(
                stranger, level.damageSources().mobAttack(body), 5.0f)), "A blow of a soldier at a player was not stopped in peace");
        body.hurt(level.damageSources().playerAttack(stranger), 1.0f);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(body.getTarget() != stranger, "The soldier took the player for a target");
            helper.assertTrue(stranger.getHealth() == health, "The player was hurt");
            end(helper, kingdom, from(helper), to(helper), citizen);
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- mercenaries

    @GameTest(template = EMPTY, batch = "kingdomworld_army_mercenaries")
    public static void anUnpaidBandLeavesAndNoMoneyAppears(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Hired Swords", UUID.randomUUID());
        try {
            ArmyState army = ArmyWork.state(server);
            ArmyRules rules = ArmySettings.rules();
            long day = ArmyWork.day(server);
            army.refreshOffers(kingdom.id(), day, rules, new Random(4)::nextDouble);
            MercenaryBand offer = army.offersOf(kingdom.id()).get(0);
            long advance = ArmyState.advance(offer, rules);
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminTreasury(kingdom.id(), advance, day);
            long supply = economy.supply(kingdom.id());
            ServerPlayer king = player(helper, kingdom.rulerId(), "kw-king");
            helper.assertTrue(ArmyActions.applyTab(server, king, new ArmyPackets.TabChange(kingdom.id(), ArmyPackets.TabChange.Action.HIRE,
                    offer.id(), 0, List.of())).success(), "The king could not hire the band");
            helper.assertTrue(economy.treasury(kingdom.id()).orElseThrow().funds() == 0, "The advance did not leave the treasury");
            helper.assertTrue(economy.supply(kingdom.id()) == supply - advance, "The advance made or kept money");
            helper.assertTrue(army.payday(kingdom.id(), day + rules.advanceDays() - 1, KingdomWorldApi.state(server)).isEmpty(),
                    "A band paid in advance left");
            List<MercenaryBand> left = army.payday(kingdom.id(), day + rules.advanceDays(), KingdomWorldApi.state(server));
            helper.assertTrue(left.size() == 1 && army.band(offer.id()).isEmpty(), "The unpaid band stayed");
            helper.assertTrue(army.squadOfBand(offer.id()).isEmpty(), "The squad of the band stayed");
            helper.assertTrue(economy.supply(kingdom.id()) == supply - advance, "Money appeared when the band left");
            helper.assertTrue(economy.treasury(kingdom.id()).orElseThrow().funds() == 0, "The treasury changed");
        } finally {
            ArmyWork.state(server).forgetKingdom(kingdom.id());
            KingdomWorldApi.dissolveKingdom(server, kingdom.id());
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- caravans

    @GameTest(template = EMPTY, batch = "kingdomworld_army_caravan")
    public static void guardsSaveACaravanFromAnAmbush(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GatheringGameTests.clear(level, from(helper), to(helper));
        TradeGameTests.clearSecond(helper);
        Kingdom seller = CitizenGameTests.found(helper, "GameTest Guarded Road", UUID.randomUUID());
        Kingdom buyer = TradeGameTests.second(helper, "GameTest Far Market", UUID.randomUUID());
        StorehouseInventory storehouse = CitizenGameTests.storehouse(helper, 0, 0, 4);
        storehouse.insertItem(0, new ItemStack(Items.WHEAT, 64), false);
        storehouse.insertItem(1, new ItemStack(Items.DIAMOND_SWORD), false);
        storehouse.insertItem(2, new ItemStack(Items.DIAMOND_SWORD), false);
        storehouse.insertItem(3, new ItemStack(Items.DIAMOND_SWORD), false);
        CitizenGameTests.storehouse(helper, TradeGameTests.SECOND, 0, 4);
        Citizen[] guards = new Citizen[3];
        try {
            for (int i = 0; i < guards.length; i++) {
                guards[i] = soldier(helper, seller, 1 + i, Professions.WARRIOR.id());
            }
            ArmySquad squad = squad(server, seller, guards);
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminTreasury(buyer.id(), 500, 0);
            Deal deal = TradeGameTests.propose(server, seller, buyer, 16, 80);
            helper.assertTrue(TradeWork.accept(server, null, buyer.id(), deal.id()).ok(), "The buyer could not accept");
            Caravan caravan = KingdomWorldApi.state(server).trade().caravans().iterator().next();
            helper.assertTrue(ArmyWork.order(server, null, squad.id(), Order.escort(deal.id())).ok(), "The squad could not escort the deal");
            helper.assertTrue(ArmyWork.isAwayFromHome(server, guards[0].id()), "The guards did not go with the caravan");
            double guarded = ArmyCaravans.lossChance(server, caravan, 0.5);
            helper.assertTrue(guarded < 0.5, "Guards did not lower the chance of a loss: " + guarded);
            // Something lies in wait (0.0), the guards win (0.0), and nobody of them is hurt (0.99).
            TradeWork.overrideRoll(rolls(0.0, 0.0, 0.99));
            TradeWork.arrive(server, caravan, KingdomWorldApi.gameTime(server));
            helper.assertTrue(KingdomStorage.count(server, buyer.id(), stack -> stack.is(Items.WHEAT)) == 16, "The guarded caravan did not arrive");
            helper.assertTrue(KingdomWorldApi.state(server).trade().caravans().isEmpty() && economy.escrows().isEmpty(), "The caravan stayed");
            Soldier record = ArmyWork.state(server).soldier(guards[0].id()).orElseThrow();
            helper.assertTrue(record.battles() == 1, "The guards did not fight");
            helper.assertTrue(!ArmyWork.isAwayFromHome(server, guards[0].id()), "The guards did not come back");
        } finally {
            TradeWork.overrideRoll(null);
            ArmyWork.state(server).forgetKingdom(seller.id());
            KingdomWorldApi.dissolveKingdom(server, buyer.id());
            end(helper, seller, from(helper), to(helper), guards[0] == null ? new Citizen[0] : java.util.Arrays.stream(guards)
                    .filter(java.util.Objects::nonNull).toArray(Citizen[]::new));
            TradeGameTests.clearSecond(helper);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- battles far from players

    @GameTest(template = EMPTY, batch = "kingdomworld_army_fallen")
    public static void aSoldierWhoFallsGoesToTheChronicle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GatheringGameTests.clear(level, from(helper), to(helper));
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Last Stand", UUID.randomUUID());
        StorehouseInventory storehouse = CitizenGameTests.storehouse(helper, 0, 0, 4);
        storehouse.insertItem(0, new ItemStack(Items.IRON_SWORD), false);
        Citizen citizen = soldier(helper, kingdom, 1, Professions.WARRIOR.id());
        try {
            ArmySquad squad = squad(server, kingdom, citizen);
            // The squad loses (0.99) and its soldier falls (0.0).
            ArmyWork.overrideRolls(rolls(0.99, 0.0));
            WorldPos at = CitizenGameTests.at(helper, 3, 0, 3);
            ArmyWork.Fought fought = ArmyWork.fight(server, squad, Side.crowd(Side.Kind.MONSTERS, 500, 5), at);
            helper.assertTrue(!fought.won() && fought.fallen().size() == 1, "The soldier did not fall");
            helper.assertTrue(KingdomWorldApi.state(server).citizens().get(citizen.id()).isEmpty(), "The fallen soldier is still a citizen");
            List<DeathRecord> chronicle = KingdomWorldApi.state(server).citizens().registry().chronicle(kingdom.id());
            helper.assertTrue(!chronicle.isEmpty() && chronicle.get(0).cause() == CauseOfDeath.BATTLE, "The chronicle does not tell of the battle");
            helper.assertTrue(ArmyWork.state(server).squad(squad.id()).isEmpty(), "The squad of the fallen stayed");
            ArmyWork.tick(server);
            helper.assertTrue(ArmyWork.state(server).soldier(citizen.id()).isEmpty(), "The fallen soldier stayed in the army");
            helper.assertTrue(CitizenGameTests.count(storehouse, Items.IRON_SWORD) == 1, "The sword of the fallen did not come back");
        } finally {
            end(helper, kingdom, from(helper), to(helper));
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- rights and data

    @GameTest(template = EMPTY, batch = "kingdomworld_army_rights")
    public static void theServerChecksTheRightToRunTheArmy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Chain Of Command", UUID.randomUUID());
        Citizen citizen = CitizenGameTests.newcomer(helper, kingdom, 1, 5);
        ServerPlayer subject = player(helper, UUID.randomUUID(), "kw-subject");
        ServerPlayer marshal = player(helper, UUID.randomUUID(), "kw-marshal");
        ServerPlayer stranger = player(helper, UUID.randomUUID(), "kw-stranger");
        try {
            join(server, kingdom, subject.getUUID(), Role.SUBJECT);
            join(server, kingdom, marshal.getUUID(), Role.MARSHAL);
            ArmyPackets.TabChange recruit = new ArmyPackets.TabChange(kingdom.id(), ArmyPackets.TabChange.Action.RECRUIT_WARRIOR, 0, 0,
                    List.of(citizen.id()));
            helper.assertTrue(!ArmyActions.applyTab(server, subject, recruit).success(), "A subject recruited a soldier");
            helper.assertTrue(!ArmyActions.applyTab(server, stranger, recruit).success(), "A stranger recruited a soldier");
            helper.assertTrue(citizen.professionId().equals(Professions.UNEMPLOYED.id()), "A refused recruitment changed the citizen");
            helper.assertTrue(!KingdomWorldApi.change(server, "test", state -> state.setProfession(kingdom.rulerId(), citizen.id(),
                    Professions.WARRIOR.id())).ok(), "A soldier came from the citizens tab");
            helper.assertTrue(KingdomWorldApi.state(server).membership().hasPermission(marshal.getUUID(), kingdom.id(), Permission.MANAGE_ARMY),
                    "The marshal of a new kingdom cannot run the army");
            helper.assertTrue(ArmyActions.applyTab(server, marshal, recruit).success(), "The marshal could not recruit");
            helper.assertTrue(citizen.professionId().equals(Professions.WARRIOR.id()), "The recruit is no warrior");
            ArmyActions.handleTab(subject, new ArmyPackets.TabChange(kingdom.id(), ArmyPackets.TabChange.Action.DISMISS, 0, 0,
                    List.of(citizen.id())));
            helper.assertTrue(ArmyWork.state(server).soldier(citizen.id()).isPresent(), "A subject dismissed a soldier through the network");
            helper.assertTrue(ArmyActions.tabView(server, kingdom.id(), stranger.getUUID()).isEmpty(), "A stranger saw the army");
            helper.assertTrue(!ArmyActions.tabView(server, kingdom.id(), subject.getUUID()).orElseThrow().canManage(),
                    "The tab lets a subject run the army");
        } finally {
            ArmyWork.state(server).forgetKingdom(kingdom.id());
            KingdomWorldApi.dissolveKingdom(server, kingdom.id());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theArmySurvivesSavingAndNewerDataStaysReadOnly(GameTestHelper helper) {
        ArmySavedData data = new ArmySavedData();
        ArmyState army = data.state();
        UUID kingdom = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        army.restoreSoldier(new Soldier(first, kingdom, 750, 4, 2, 100));
        army.restoreSoldier(new Soldier(second, kingdom, 20, 0, 0, 200));
        WorldPos point = new WorldPos("minecraft:overworld", 10, 64, -20);
        army.restoreSquad(new ArmySquad(4, kingdom, List.of(first, second), second, 0, Order.patrol(List.of(point,
                new WorldPos("minecraft:overworld", 30, 64, -20)))));
        Mercenary mercenary = new Mercenary(UUID.randomUUID(), 77, true, 2, "minecraft:crossbow", 8, 0.8, true, 12, 0, 300, 0.75);
        army.restoreBand(new MercenaryBand(9, kingdom, List.of(mercenary), 25, 3, 6));
        army.restoreSquad(new ArmySquad(5, kingdom, List.of(), null, 9, Order.hold(point)));
        army.restoreBand(new MercenaryBand(10, kingdom, List.of(mercenary), 30, 3, MercenaryBand.OFFER));
        army.restoreBarracks(kingdom, point);
        army.restoreOffersDay(kingdom, 3);

        CompoundTag saved = data.save(new CompoundTag());
        ArmySavedData reloaded = ArmySavedData.load(saved);
        helper.assertTrue(!reloaded.isReadOnly(), "Saved army data could not be read back: " + reloaded.readOnlyReason());
        ArmyState back = reloaded.state();
        helper.assertTrue(back.soldier(first).equals(army.soldier(first)), "The soldier changed");
        helper.assertTrue(back.squad(4).equals(army.squad(4)), "The squad changed: " + back.squad(4));
        helper.assertTrue(back.squad(5).equals(army.squad(5)), "The squad of the band changed");
        helper.assertTrue(back.band(9).equals(army.band(9)) && back.band(10).equals(army.band(10)), "The bands changed");
        helper.assertTrue(back.barracksOf(kingdom).contains(point) && back.offersDay(kingdom) == 3, "The barracks or the offers changed");
        helper.assertTrue(back.nextSquadId() == 6 && back.nextBandId() == 11, "The next ids changed");

        CompoundTag newer = saved.copy();
        newer.putInt("formatVersion", ArmySavedData.FORMAT_VERSION + 1);
        newer.putString("futureField", "kept");
        ArmySavedData locked = ArmySavedData.load(newer);
        helper.assertTrue(locked.isReadOnly(), "Newer army data was not kept read-only");
        helper.assertTrue("kept".equals(locked.save(new CompoundTag()).getString("futureField")), "Read-only data was not kept as it was");
        CompoundTag broken = saved.copy();
        broken.getList("squads", Tag.TAG_COMPOUND).getCompound(0).getCompound("order").putString("kind", "charge");
        helper.assertTrue(ArmySavedData.load(broken).isReadOnly(), "An unreadable order did not keep the data read-only");
        CompoundTag orphan = saved.copy();
        orphan.getList("bands", Tag.TAG_COMPOUND).remove(0);
        helper.assertTrue(ArmySavedData.load(orphan).isReadOnly(), "A squad of a missing band did not keep the data read-only");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theMarshalGetsTheRightToRunTheArmyOnUpgrade(GameTestHelper helper) {
        KingdomSavedData data = new KingdomSavedData();
        UUID king = UUID.randomUUID();
        UUID marshal = UUID.randomUUID();
        Kingdom kingdom = ((io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult.Created) data.state().found(UUID.randomUUID(),
                "Upgraded Army Realm", 0x654321, king, new io.github.sekerator.kingdomworld.core.territory.Capital("minecraft:overworld", 300, 64,
                        300), 1L, io.github.sekerator.kingdomworld.core.state.KingdomRules.DEFAULT)).kingdom();
        data.state().membership().invite(king, marshal, 2L);
        data.state().membership().acceptInvitation(marshal, kingdom.id(), 3L);
        data.state().membership().setRole(king, marshal, Role.MARSHAL);
        CompoundTag saved = data.save(new CompoundTag());
        // Data of the release before the army: the guild's upgrade only, and no marshal with the right.
        ListTag upgrades = new ListTag();
        upgrades.add(StringTag.valueOf("guild_rights"));
        saved.put("upgrades", upgrades);
        ListTag marshalRights = saved.getList("kingdoms", Tag.TAG_COMPOUND).getCompound(0).getCompound("permissions")
                .getList("marshal", Tag.TAG_STRING);
        for (int i = marshalRights.size() - 1; i >= 0; i--) {
            if (marshalRights.getString(i).equals("manage_army")) {
                marshalRights.remove(i);
            }
        }
        KingdomSavedData upgraded = KingdomSavedData.load(saved);
        helper.assertTrue(!upgraded.isReadOnly(), "Upgraded data is read-only: " + upgraded.readOnlyReason());
        helper.assertTrue(upgraded.state().membership().hasPermission(marshal, kingdom.id(), Permission.MANAGE_ARMY),
                "The marshal did not get the right to run the army");
        CompoundTag again = upgraded.save(new CompoundTag());
        helper.assertTrue(again.getList("upgrades", Tag.TAG_STRING).contains(StringTag.valueOf("army_rights")), "The upgrade was not noted");
        ListTag kept = again.getList("kingdoms", Tag.TAG_COMPOUND).getCompound(0).getCompound("permissions").getList("marshal", Tag.TAG_STRING);
        for (int i = kept.size() - 1; i >= 0; i--) {
            if (kept.getString(i).equals("manage_army")) {
                kept.remove(i);
            }
        }
        helper.assertTrue(!KingdomSavedData.load(again).state().membership().hasPermission(marshal, kingdom.id(), Permission.MANAGE_ARMY),
                "An upgrade ran twice");
        helper.assertTrue(KingdomError.valueOf("NOT_A_SOLDIER") != null, "Errors of the army are missing");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void armyPacketsSurviveEncoding(GameTestHelper helper) {
        ArmyPackets.TabChange change = new ArmyPackets.TabChange(UUID.randomUUID(), ArmyPackets.TabChange.Action.ESCORT, 7, 9,
                List.of(UUID.randomUUID(), UUID.randomUUID()));
        net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        change.encode(buf);
        helper.assertTrue(ArmyPackets.TabChange.decode(buf).equals(change), "The army change did not survive encoding");
        ArmyPackets.BarracksAction action = new ArmyPackets.BarracksAction(new BlockPos(1, 2, 3), ArmyPackets.BarracksAction.Action.RECRUIT_ARCHER);
        buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        action.encode(buf);
        helper.assertTrue(ArmyPackets.BarracksAction.decode(buf).equals(action), "The barracks action did not survive encoding");
        helper.succeed();
    }
}
