package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.companion.CompanionActions;
import io.github.sekerator.kingdomworld.common.companion.CompanionBodies;
import io.github.sekerator.kingdomworld.common.companion.CompanionEntity;
import io.github.sekerator.kingdomworld.common.companion.CompanionSavedData;
import io.github.sekerator.kingdomworld.common.companion.CompanionSettings;
import io.github.sekerator.kingdomworld.common.companion.CompanionWork;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.guild.GuildActions;
import io.github.sekerator.kingdomworld.common.guild.GuildWork;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.packet.CompanionPackets;
import io.github.sekerator.kingdomworld.common.network.packet.GuildPackets;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.companion.Companion;
import io.github.sekerator.kingdomworld.core.companion.CompanionRules;
import io.github.sekerator.kingdomworld.core.companion.CompanionState;
import io.github.sekerator.kingdomworld.core.companion.Offer;
import io.github.sekerator.kingdomworld.core.companion.Origin;
import io.github.sekerator.kingdomworld.core.companion.Party;
import io.github.sekerator.kingdomworld.core.companion.Skills;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.guild.Contract;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Game tests of companions and parties: a slave bought pays the treasury of the branch and follows the buyer; a companion
 * follows its owner and kills a monster; it never hurts a foreign player in peace; its pack survives the owner leaving and a
 * restart of the data without a copy; a hungry slave with low loyalty warns and then runs away; a slave set free serves for
 * pay or settles in the owner's kingdom; a party of two splits the reward of a contract; friendly fire off keeps the party
 * and its companions safe from each other; a king closes the slave market. Every test runs in a batch of its own and clears
 * what it built.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionGameTests {
    private static final String EMPTY = "empty";

    private CompanionGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    private static ServerPlayer player(GameTestHelper helper, String name, int x, int z) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        BlockPos at = helper.absolutePos(new BlockPos(x, 1, z));
        player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return player;
    }

    private static void member(MinecraftServer server, ServerPlayer player, Rank rank) {
        GuildWork.state(server).setRank(player.getUUID(), rank, KingdomWorldApi.gameTime(server));
    }

    private static Offer offer(MinecraftServer server, Kingdom kingdom, boolean slave) {
        List<Offer> offers = CompanionWork.state(server).offers(kingdom.id(), TreasuryActions.day(server), server.overworld().getSeed(),
                CompanionSettings.rules(), CitizenLife.SKINS);
        return offers.stream().filter(o -> o.slave() == slave).findFirst()
                .orElseThrow(() -> new IllegalStateException("No " + (slave ? "slave" : "free companion") + " on the market"));
    }

    private static Companion give(MinecraftServer server, ServerPlayer owner, Kingdom kingdom, int level) {
        return CompanionWork.state(server).give(owner.getUUID(), UUID.randomUUID(), 77, Gender.MALE, 0, level, new Skills(4, 4, 4),
                kingdom.id(), KingdomWorldApi.gameTime(server), TreasuryActions.day(server), CompanionSettings.rules());
    }

    private static CompanionEntity body(GameTestHelper helper, Companion companion, int x, int z) {
        CompanionEntity body = CompanionBodies.spawnAt(helper.getLevel(), companion, helper.absolutePos(new BlockPos(x, 1, z)));
        helper.assertTrue(body != null, "The companion got no body");
        return body;
    }

    private static List<CompanionEntity> bodiesOf(GameTestHelper helper, UUID companionId) {
        return helper.getLevel().getEntitiesOfClass(CompanionEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(48),
                body -> body.isAlive() && companionId.equals(body.companionId()));
    }

    private static int items(SimpleContainer pack) {
        int count = 0;
        for (int i = 0; i < pack.getContainerSize(); i++) {
            count += pack.getItem(i).getCount();
        }
        return count;
    }

    /**
     * Items on the ground of the test's area: of the kinds a test put into a pack (other tests leave other things around).
     */
    private static List<ItemEntity> dropped(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16),
                item -> item.getItem().is(Items.IRON_INGOT) || item.getItem().is(Items.GOLD_INGOT) || item.getItem().is(Items.DIAMOND_SWORD));
    }

    private static BlockPos from(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(-1, 0, -1));
    }

    private static BlockPos to(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(8, 3, 8));
    }

    /**
     * Clears what a test made: companions of the players and their packs and bodies, parties, the market of the kingdom,
     * the kingdom itself and the blocks and items of the area.
     */
    private static void end(GameTestHelper helper, Kingdom kingdom, ServerPlayer... players) {
        MinecraftServer server = helper.getLevel().getServer();
        CompanionState state = CompanionWork.state(server);
        for (ServerPlayer player : players) {
            CompanionWork.present(player, false);
            CompanionBodies.despawnAll(server, player.getUUID());
            for (Companion companion : state.ownedBy(player.getUUID())) {
                state.remove(companion.id());
                CompanionSavedData.get(server).takePack(companion.id());
            }
            state.parties().leave(player.getUUID());
        }
        CompanionBodies.update(server);
        CompanionSettings.override(null);
        state.forgetKingdom(kingdom.id());
        GuildWork.state(server).forgetKingdom(kingdom.id(), TreasuryActions.day(server), KingdomWorldApi.state(server));
        CitizenGameTests.finish(helper, kingdom);
        dropped(helper).forEach(ItemEntity::discard);
        GatheringGameTests.clear(helper.getLevel(), from(helper), to(helper));
    }

    // ---------------------------------------------------------------- the market

    @GameTest(template = EMPTY, batch = "kingdomworld_companion_market")
    public static void buyingASlavePaysTheTreasuryOfTheBranch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GatheringGameTests.clear(level, from(helper), to(helper));
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Slave Market", UUID.randomUUID());
        ServerPlayer buyer = player(helper, "kw-buyer", 2, 2);
        ServerPlayer outsider = player(helper, "kw-outsider", 3, 2);
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminAccount(buyer.getUUID(), kingdom.id(), 100_000);
            economy.adminAccount(outsider.getUUID(), kingdom.id(), 100_000);
            Offer slave = offer(server, kingdom, true);
            helper.assertTrue(!CompanionActions.applyMarket(outsider, kingdom, CompanionPackets.MarketAction.Action.TAKE, slave.id(), false)
                    .success(), "Somebody outside the guild bought a slave");
            member(server, buyer, Rank.F);
            long supply = economy.supply(kingdom.id());
            long treasury = economy.treasury(kingdom.id()).orElseThrow().funds();
            KingdomActions.Feedback bought = CompanionActions.applyMarket(buyer, kingdom, CompanionPackets.MarketAction.Action.TAKE, slave.id(), false);
            helper.assertTrue(bought.success(), "The slave was not bought: " + bought.message().getString());
            List<Companion> owned = CompanionWork.state(server).ownedBy(buyer.getUUID());
            helper.assertTrue(owned.size() == 1 && owned.get(0).isSlave() && owned.get(0).branch().equals(kingdom.id()),
                    "The buyer has no slave of the branch");
            helper.assertTrue(economy.treasury(kingdom.id()).orElseThrow().funds() == treasury + slave.price(),
                    "The price did not go to the treasury of the branch");
            helper.assertTrue(economy.accounts().balance(buyer.getUUID(), kingdom.id()) == 100_000 - slave.price(), "The buyer paid wrong");
            helper.assertTrue(economy.supply(kingdom.id()) == supply, "Buying made or lost money");
            Offer next = offer(server, kingdom, true);
            KingdomActions.Feedback second = CompanionActions.applyMarket(buyer, kingdom, CompanionPackets.MarketAction.Action.TAKE, next.id(), false);
            helper.assertTrue(!second.success() && second.message().getString().equals(KingdomActions.errorMessage(KingdomError.TOO_MANY_COMPANIONS)
                    .getString()), "Rank F allowed a second companion");
        } finally {
            end(helper, kingdom, buyer, outsider);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_companion_closed_market")
    public static void aKingClosesTheSlaveMarketButNotTheHire(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Closed Market", kingId);
        ServerPlayer king = new ServerPlayer(server, level, new GameProfile(kingId, "kw-king"));
        ServerPlayer buyer = player(helper, "kw-buyer", 2, 2);
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminAccount(buyer.getUUID(), kingdom.id(), 100_000);
            member(server, buyer, Rank.C);
            helper.assertTrue(!CompanionActions.applyMarket(buyer, kingdom, CompanionPackets.MarketAction.Action.CLOSE_MARKET, 0, false).success(),
                    "Somebody who is no king closed the market");
            helper.assertTrue(CompanionActions.applyMarket(king, kingdom, CompanionPackets.MarketAction.Action.CLOSE_MARKET, 0, false).success(),
                    "The king could not close the market");
            Offer slave = offer(server, kingdom, true);
            KingdomActions.Feedback refused = CompanionActions.applyMarket(buyer, kingdom, CompanionPackets.MarketAction.Action.TAKE, slave.id(), false);
            helper.assertTrue(!refused.success() && refused.message().getString().equals(
                    KingdomActions.errorMessage(KingdomError.SLAVE_MARKET_CLOSED).getString()), "A slave was sold on a closed market");
            Offer free = offer(server, kingdom, false);
            helper.assertTrue(CompanionActions.applyMarket(buyer, kingdom, CompanionPackets.MarketAction.Action.TAKE, free.id(), false).success(),
                    "Free companions could not be hired while the slave market is closed");
            helper.assertTrue(CompanionActions.applyMarket(king, kingdom, CompanionPackets.MarketAction.Action.OPEN_MARKET, 0, false).success(),
                    "The king could not open the market");
            helper.assertTrue(CompanionActions.applyMarket(buyer, kingdom, CompanionPackets.MarketAction.Action.TAKE, slave.id(), false).success(),
                    "A slave could not be bought on the market opened again");
        } finally {
            end(helper, kingdom, buyer, king);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- in the world

    @GameTest(template = EMPTY, batch = "kingdomworld_companion_fight", timeoutTicks = 1200)
    public static void aCompanionFollowsItsOwnerAndKillsAMonster(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GatheringGameTests.clear(level, from(helper), to(helper));
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Companion Hunt", UUID.randomUUID());
        ServerPlayer owner = player(helper, "kw-owner", 5, 5);
        Companion companion = CompanionWork.state(server).give(owner.getUUID(), UUID.randomUUID(), 77, Gender.MALE, 0, 10, new Skills(10, 4, 4),
                kingdom.id(), KingdomWorldApi.gameTime(server), TreasuryActions.day(server), CompanionSettings.rules());
        CompanionSavedData.get(server).pack(companion.id()).setItem(0, new ItemStack(Items.NETHERITE_SWORD));
        CompanionWork.present(owner, true);
        // The throne of the kingdom stands on the straight way from the companion to its owner: the way goes around it.
        BlockPos throne = helper.absolutePos(new BlockPos(2, 0, 2));
        helper.assertTrue(!level.getBlockState(throne).isPathfindable(level, throne, net.minecraft.world.level.pathfinder.PathComputationType.LAND),
                "Paths lead through the throne");
        CompanionEntity body = body(helper, companion, 1, 1);
        Husk[] husk = new Husk[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(body.distanceTo(owner) < 4.5f, "The companion did not follow its owner: it stands at "
                        + body.blockPosition() + ", the owner at " + owner.blockPosition()))
                .thenExecute(() -> {
                    helper.assertTrue(body.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND).is(Items.NETHERITE_SWORD),
                            "The companion did not take the sword from its pack");
                    // A husk does not burn in the sun; the owner fights it next to the companion, so the companion joins.
                    husk[0] = EntityType.HUSK.create(level);
                    husk[0].moveTo(body.getX() + 1.0, body.getY(), body.getZ(), 0.0f, 0.0f);
                    husk[0].setNoAi(true);
                    level.addFreshEntity(husk[0]);
                    owner.setLastHurtMob(husk[0]);
                })
                .thenWaitUntil(() -> helper.assertTrue(!husk[0].isAlive(), "The monster is still alive: health " + husk[0].getHealth()
                        + ", companion alive " + body.isAlive() + " removed " + body.isRemoved() + ", target " + body.getTarget()
                        + ", distance " + body.distanceTo(husk[0]) + ", hand " + body.getMainHandItem() + ", at " + body.blockPosition()
                        + ", monster at " + husk[0].blockPosition()))
                .thenExecute(() -> {
                    Companion record = CompanionWork.state(server).companion(companion.id()).orElseThrow();
                    helper.assertTrue(record.kills() >= 1 && record.experience() > 0, "The kill gave the companion no experience: kills "
                            + record.kills() + ", experience " + record.experience() + ", killed by " + (husk[0].getLastDamageSource() == null ? "?"
                            : husk[0].getLastDamageSource().getEntity()));
                    helper.assertTrue(CompanionSavedData.get(server).pack(companion.id()).getItem(0).getDamageValue() > 0,
                            "The sword in the pack did not wear");
                    end(helper, kingdom, owner);
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_companion_peace", timeoutTicks = 200)
    public static void aCompanionNeverHurtsAForeignPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GatheringGameTests.clear(level, from(helper), to(helper));
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Peaceful Companion", UUID.randomUUID());
        ServerPlayer owner = player(helper, "kw-owner", 6, 6);
        ServerPlayer stranger = player(helper, "kw-stranger", 2, 2);
        Companion companion = give(server, owner, kingdom, 10);
        CompanionSavedData.get(server).pack(companion.id()).setItem(0, new ItemStack(Items.IRON_SWORD));
        CompanionWork.present(owner, true);
        CompanionEntity body = body(helper, companion, 2, 3);
        float health = stranger.getHealth();
        helper.assertTrue(MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(stranger, level.damageSources().mobAttack(body), 5.0f)),
                "A blow of a companion at a player was not stopped");
        helper.assertTrue(MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(stranger, level.damageSources().arrow(
                new net.minecraft.world.entity.projectile.Arrow(level, body), body), 5.0f)), "An arrow of a companion at a player was not stopped");
        float before = body.getHealth();
        helper.assertTrue(!body.hurt(level.damageSources().playerAttack(stranger), 1.0f) && body.getHealth() == before,
                "A stranger hurt somebody else's companion");
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(body.getTarget() != stranger, "The companion took the player for a target");
            helper.assertTrue(stranger.getHealth() == health, "The player was hurt");
            end(helper, kingdom, owner, stranger);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_companion_offline", timeoutTicks = 200)
    public static void thePackSurvivesTheOwnerLeavingAndARestartWithoutACopy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GatheringGameTests.clear(level, from(helper), to(helper));
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Faithful Porter", UUID.randomUUID());
        dropped(helper).forEach(ItemEntity::discard);
        ServerPlayer owner = player(helper, "kw-owner", 4, 4);
        Companion companion = give(server, owner, kingdom, 2);
        SimpleContainer pack = CompanionSavedData.get(server).pack(companion.id());
        pack.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
        pack.setItem(1, new ItemStack(Items.DIAMOND_SWORD));
        CompanionWork.present(owner, true);
        CompanionBodies.update(server);
        helper.assertTrue(bodiesOf(helper, companion.id()).size() == 1, "The companion of an owner online got no body");
        // The owner leaves: the body goes, the pack stays, nothing falls.
        CompanionWork.present(owner, false);
        CompanionBodies.despawnAll(server, owner.getUUID());
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(bodiesOf(helper, companion.id()).isEmpty(), "The body stayed after its owner left");
            helper.assertTrue(dropped(helper).isEmpty(), "Something fell when the owner left");
            helper.assertTrue(items(CompanionSavedData.get(server).pack(companion.id())) == 11, "The pack changed when the owner left");
            // A restart: the data is written and read again.
            CompoundTag saved = CompanionSavedData.get(server).save(new CompoundTag());
            CompanionSavedData read = CompanionSavedData.load(saved);
            helper.assertTrue(!read.isReadOnly(), "Saved companion data did not read back");
            helper.assertTrue(read.state().companion(companion.id()).map(c -> c.loyalty() == companion.loyalty() && c.level() == 2).orElse(false),
                    "The companion did not survive saving");
            helper.assertTrue(items(read.pack(companion.id())) == 11 && read.pack(companion.id()).getItem(1).is(Items.DIAMOND_SWORD),
                    "The pack did not survive saving");
            // The owner comes back: one body again, the same pack.
            CompanionWork.present(owner, true);
            CompanionBodies.update(server);
            CompanionBodies.update(server);
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(bodiesOf(helper, companion.id()).size() == 1, "The owner came back to no body, or to two");
                helper.assertTrue(items(CompanionSavedData.get(server).pack(companion.id())) == 11, "The pack changed on the way back");
                end(helper, kingdom, owner);
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_companion_escape", timeoutTicks = 600)
    public static void aHungrySlaveWithLowLoyaltyWarnsAndRunsAway(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GatheringGameTests.clear(level, from(helper), to(helper));
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Cruel Master", UUID.randomUUID());
        dropped(helper).forEach(ItemEntity::discard);
        ServerPlayer owner = player(helper, "kw-owner", 4, 4);
        CompanionRules.Builder rules = CompanionSettings.configured();
        rules.starveTicks = 20;
        rules.hungerLoss = 10;
        rules.escapeBelow = 20;
        rules.escapeTicks = 60;
        CompanionSettings.override(rules.build());
        Companion companion = give(server, owner, kingdom, 1);
        CompanionSavedData.get(server).pack(companion.id()).setItem(0, new ItemStack(Items.GOLD_INGOT, 5));
        CompanionState state = CompanionWork.state(server);
        long now = KingdomWorldApi.gameTime(server);
        state.setFood(companion.id(), 0, now);
        state.setLoyalty(companion.id(), 25, now);
        CompanionWork.present(owner, true);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(companion.warned(), "The starving slave gave no warning"))
                .thenExecute(() -> helper.assertTrue(state.companion(companion.id()).isPresent(), "The slave ran away without waiting"))
                .thenWaitUntil(() -> helper.assertTrue(state.companion(companion.id()).isEmpty(), "The slave did not run away"))
                .thenExecute(() -> {
                    helper.assertTrue(CompanionSavedData.get(server).takePack(companion.id()).isEmpty(), "The pack stayed behind");
                    helper.assertTrue(bodiesOf(helper, companion.id()).isEmpty(), "The body stayed behind");
                    helper.assertTrue(dropped(helper).isEmpty(), "The runaway left its pack on the ground");
                    end(helper, kingdom, owner);
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_companion_freedom")
    public static void aSlaveSetFreeServesForPayOrSettlesInTheKingdom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Free Folk", kingId);
        ServerPlayer owner = player(helper, "kw-owner", 4, 4);
        try {
            CompanionRules rules = CompanionSettings.rules();
            Companion servant = give(server, owner, kingdom, 3);
            KingdomActions.Feedback freed = CompanionActions.applyCompanion(owner, servant.id(), CompanionPackets.CompanionAction.Action.FREE);
            helper.assertTrue(freed.success(), "The slave was not set free: " + freed.message().getString());
            helper.assertTrue(!servant.isSlave() && servant.origin() == Origin.FREEDMAN && servant.loyalty() >= rules.freedLoyalty
                    && servant.wage() > 0, "The freed slave does not serve for pay with high loyalty");
            Companion settler = give(server, owner, kingdom, 1);
            CompanionSavedData.get(server).pack(settler.id()).setItem(0, new ItemStack(Items.BREAD, 3));
            KingdomActions.Feedback refused = CompanionActions.applyCompanion(owner, settler.id(),
                    CompanionPackets.CompanionAction.Action.FREE_TO_KINGDOM);
            helper.assertTrue(!refused.success(), "A slave was set free into a kingdom of an owner who is no subject");
            KingdomWorldState world = KingdomWorldApi.state(server);
            world.membership().invite(kingId, owner.getUUID(), 1L);
            world.membership().acceptInvitation(owner.getUUID(), kingdom.id(), 1L);
            helper.assertTrue(!CompanionActions.applyCompanion(owner, settler.id(), CompanionPackets.CompanionAction.Action.FREE_TO_KINGDOM).success(),
                    "A slave settled in a kingdom without a free bed");
            LifeGameTests.bed(helper, kingdom, new BlockPos(6, 0, 6));
            int citizens = world.citizens().registry().citizensOf(kingdom.id()).size();
            KingdomActions.Feedback settled = CompanionActions.applyCompanion(owner, settler.id(),
                    CompanionPackets.CompanionAction.Action.FREE_TO_KINGDOM);
            helper.assertTrue(settled.success(), "The slave could not settle in the kingdom: " + settled.message().getString());
            List<Citizen> after = world.citizens().registry().citizensOf(kingdom.id());
            helper.assertTrue(after.size() == citizens + 1 && after.stream().anyMatch(c -> c.nameSeed() == settler.nameSeed()),
                    "No citizen of the same name settled in the kingdom");
            helper.assertTrue(CompanionWork.state(server).companion(settler.id()).isEmpty(), "The settler is still a companion");
            helper.assertTrue(owner.getInventory().countItem(Items.BREAD) == 3, "The settler did not give back what it carried");
            for (Citizen citizen : new ArrayList<>(after)) {
                if (citizen.nameSeed() == settler.nameSeed()) {
                    io.github.sekerator.kingdomworld.common.citizen.CitizenBodies.force(citizen.id(), false);
                }
            }
        } finally {
            end(helper, kingdom, owner);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- parties

    @GameTest(template = EMPTY, batch = "kingdomworld_companion_party_reward")
    public static void aPartyOfTwoSplitsTheRewardOfAContract(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GatheringGameTests.clear(level, from(helper), to(helper));
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Hunting Party", kingId);
        ServerPlayer king = new ServerPlayer(server, level, new GameProfile(kingId, "kw-king"));
        ServerPlayer leader = player(helper, "kw-leader", 2, 2);
        ServerPlayer second = player(helper, "kw-second", 3, 3);
        BlockPos desk = helper.absolutePos(new BlockPos(6, 0, 1));
        try {
            Economy economy = KingdomWorldApi.state(server).economy();
            economy.adminTreasury(kingdom.id(), 1_000, 0);
            member(server, leader, Rank.D);
            member(server, second, Rank.F);
            CompanionRules rules = CompanionSettings.rules();
            long now = KingdomWorldApi.gameTime(server);
            CompanionState state = CompanionWork.state(server);
            helper.assertTrue(state.parties().invite(leader.getUUID(), second.getUUID(), true, true, now, rules).ok(), "No invitation");
            helper.assertTrue(state.parties().accept(second.getUUID(), state.parties().partyOf(leader.getUUID()).orElseThrow().id(), now, rules).ok(),
                    "The invitation was not accepted");
            long supply = economy.supply(kingdom.id());
            helper.assertTrue(GuildActions.apply(king, kingdom, new GuildPackets.DeskAction(desk, GuildPackets.DeskAction.Action.POST, 0,
                    Contract.Kind.HUNT, "minecraft:zombie", 0, 2, 100, 0, 2, true, true)).success(), "The king could not post a hunt");
            Contract contract = GuildWork.state(server).contracts().stream().filter(c -> c.branch().equals(kingdom.id()) && c.reward() == 100)
                    .findFirst().orElseThrow();
            helper.assertTrue(GuildActions.apply(leader, kingdom, GuildPackets.DeskAction.simple(desk, GuildPackets.DeskAction.Action.TAKE,
                    contract.id())).success(), "The leader could not take the hunt");
            helper.assertTrue(state.parties().partyOf(leader.getUUID()).orElseThrow().contracts().containsKey(contract.id()),
                    "The contract of the leader is not the party's");
            for (int i = 0; i < 2; i++) {
                Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4 + i, 1, 4));
                zombie.setNoAi(true);
                zombie.hurt(level.damageSources().playerAttack(second), 10_000.0f);
            }
            helper.assertTrue(GuildWork.state(server).contract(contract.id()).isEmpty(), "Kills of the second member did not finish the hunt");
            long payout = contract.payout();
            long first = economy.accounts().balance(leader.getUUID(), kingdom.id());
            long other = economy.accounts().balance(second.getUUID(), kingdom.id());
            helper.assertTrue(first + other == payout && Math.abs(first - other) <= 1, "The reward was not split evenly: " + first + " and " + other);
            helper.assertTrue(economy.supply(kingdom.id()) == supply, "The split made or lost money");
        } finally {
            end(helper, kingdom, leader, second);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_companion_friendly_fire")
    public static void friendlyFireOffKeepsThePartySafeFromItself(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GatheringGameTests.clear(level, from(helper), to(helper));
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Friendly Fire", UUID.randomUUID());
        ServerPlayer leader = player(helper, "kw-leader", 2, 2);
        ServerPlayer second = player(helper, "kw-second", 3, 3);
        try {
            member(server, leader, Rank.F);
            member(server, second, Rank.F);
            CompanionRules rules = CompanionSettings.rules();
            long now = KingdomWorldApi.gameTime(server);
            CompanionState state = CompanionWork.state(server);
            state.parties().invite(leader.getUUID(), second.getUUID(), true, true, now, rules);
            state.parties().accept(second.getUUID(), state.parties().partyOf(leader.getUUID()).orElseThrow().id(), now, rules);
            Party party = state.parties().partyOf(leader.getUUID()).orElseThrow();
            helper.assertTrue(!party.friendlyFire(), "A new party allows friendly fire");
            Companion companion = give(server, second, kingdom, 1);
            CompanionWork.present(second, true);
            CompanionEntity body = body(helper, companion, 4, 4);
            helper.assertTrue(MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(second, level.damageSources().playerAttack(leader), 4.0f)),
                    "A member hurt another with friendly fire off");
            float before = body.getHealth();
            helper.assertTrue(!body.hurt(level.damageSources().playerAttack(leader), 4.0f) && body.getHealth() == before,
                    "A member hurt the companion of another with friendly fire off");
            helper.assertTrue(MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(leader, level.damageSources().mobAttack(body), 4.0f)),
                    "A companion hurt a member of its owner's party");
            helper.assertTrue(CompanionActions.applyParty(leader, CompanionPackets.PartyAction.Action.FIRE, leader.getUUID(), "", 1).success(),
                    "The leader could not turn friendly fire on");
            helper.assertTrue(!MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(second, level.damageSources().playerAttack(leader), 4.0f)),
                    "Friendly fire on still kept the members apart");
            helper.assertTrue(body.hurt(level.damageSources().playerAttack(leader), 1.0f), "Friendly fire on still kept the companion safe");
        } finally {
            end(helper, kingdom, leader, second);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_companion_packets")
    public static void companionPacketsSurviveEncoding(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Companion Packets", UUID.randomUUID());
        ServerPlayer owner = player(helper, "kw-owner", 4, 4);
        try {
            Companion companion = give(server, owner, kingdom, 5);
            CompanionSavedData.get(server).pack(companion.id()).setItem(0, new ItemStack(Items.BOW));
            var view = CompanionActions.view(server, companion);
            net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            view.encode(buf);
            var read = io.github.sekerator.kingdomworld.common.network.data.CompanionViews.CompanionView.decode(buf);
            helper.assertTrue(read.id().equals(view.id()) && read.level() == 5 && read.gear().size() == 6 && read.loyalty() == view.loyalty(),
                    "The view of a companion did not survive encoding");
            var party = CompanionActions.partyView(server, owner.getUUID());
            buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            party.encode(buf);
            helper.assertTrue(io.github.sekerator.kingdomworld.common.network.data.CompanionViews.PartyView.decode(buf).equals(party),
                    "The view of a party did not survive encoding");
        } finally {
            end(helper, kingdom, owner);
        }
        helper.succeed();
    }
}
