package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenCare;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSimulation;
import io.github.sekerator.kingdomworld.common.construction.BuildingEffects;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplate;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplates;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.economy.CoinStacks;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.law.LawSettings;
import io.github.sekerator.kingdomworld.common.law.LawStandIn;
import io.github.sekerator.kingdomworld.common.law.LawWork;
import io.github.sekerator.kingdomworld.common.life.InnkeeperBehavior;
import io.github.sekerator.kingdomworld.common.life.LifeRumors;
import io.github.sekerator.kingdomworld.common.life.LifeSettings;
import io.github.sekerator.kingdomworld.common.life.LifeWork;
import io.github.sekerator.kingdomworld.common.life.TavernActions;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.road.BanditWork;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.common.trade.Purses;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.BuildingRoles;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.law.CrimeKind;
import io.github.sekerator.kingdomworld.core.law.LawRules;
import io.github.sekerator.kingdomworld.core.life.LifeRules;
import io.github.sekerator.kingdomworld.core.life.LifeState;
import io.github.sekerator.kingdomworld.core.life.Rumor;
import io.github.sekerator.kingdomworld.core.road.BanditCamp;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
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
 * Game tests of the life of cities: a citizen spends the evening in the tavern and is in a better mood, sleeps in its bed at
 * night and sees no theft while asleep (a blow close by wakes it); the innkeeper sells food from the storehouse for the
 * treasury; a rumor of a camp points at a real camp and can be bought marked on the atlas; an ill citizen goes to the
 * infirmary and gets well; a festival takes its treat and raises the mood. Every test runs in a batch of its own, walls its
 * area off the caves of the world and clears what it built.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LifeGameTests {
    private static final String EMPTY = "empty";
    private static final ResourceLocation TAVERN = KingdomWorld.id("test_life_tavern");

    private LifeGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Rules that leave nothing to chance: one day for all, everybody goes to the tavern, nobody falls ill by itself.
     */
    private static void rules() {
        LifeRules.Builder rules = LifeSettings.configured();
        rules.spreadTicks = 0;
        rules.tavernShare = 100;
        rules.sickChance = 0;
        LifeSettings.override(rules.build());
    }

    private static BlockPos[] area(GameTestHelper helper) {
        BlockPos from = helper.absolutePos(new BlockPos(-2, 0, -2));
        BlockPos to = helper.absolutePos(new BlockPos(9, 3, 9));
        GatheringGameTests.enclose(helper.getLevel(), from, to);
        return new BlockPos[]{from, to};
    }

    private static void at(ServerLevel level, long dayTime) {
        long day = Math.floorDiv(level.getDayTime(), (long) LifeRules.DAY);
        level.setDayTime(day * LifeRules.DAY + dayTime);
    }

    /**
     * A small tavern of three by three, built at once; its role works.
     */
    private static Building tavern(GameTestHelper helper, Kingdom kingdom) {
        com.google.gson.JsonObject description = com.google.gson.JsonParser.parseString("{\"name\": \"kingdomworld.building.tavern\", "
                + "\"fallback_name\": \"Test tavern\", \"category\": \"trade\", \"type\": \"tavern\", \"role\": \"tavern\", \"level\": 1, "
                + "\"icon\": \"minecraft:barrel\", \"anchor\": [1, 0, 2], \"entrance\": \"south\"}").getAsJsonObject();
        List<String> palette = List.of("minecraft:cobblestone");
        int[][] blocks = new int[9][];
        int i = 0;
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                blocks[i++] = new int[]{x, 0, z, 0};
            }
        }
        BuildingTemplate template = BuildingTemplates.addForTest(TAVERN, description,
                ConstructionGameTests.structure(3, 2, 3, palette, blocks));
        KingdomWorldState.ConstructionChange change = ConstructionWork.lay(helper.getLevel(), null, kingdom.id(), template,
                helper.absolutePos(new BlockPos(6, 0, 7)), Placement.NONE, Materials.NONE);
        Construction construction = change.construction().orElseThrow(() -> new IllegalStateException("The tavern was refused: " + change.outcome()));
        Building building = ConstructionWork.finishNow(helper.getLevel().getServer(), construction).orElseThrow();
        BuildingEffects.changed();
        helper.assertTrue(BuildingRoles.TAVERN.equals(building.role()), "The tavern has no role: " + building.role());
        helper.assertTrue(BuildingEffects.of(helper.getLevel().getServer(), building).map(BuildingEffects.Effect::works).orElse(false),
                "The tavern does not work");
        return building;
    }

    /**
     * An innkeeper at a post in the corner of the tavern, with a body.
     */
    private static Citizen innkeeper(GameTestHelper helper, Kingdom kingdom, Building tavern) {
        BlockPos post = new BlockPos(tavern.box().maxX(), tavern.box().minY() + 1, tavern.box().maxZ());
        WorldPos pos = GatheringGameTests.post(helper.getLevel(), post, InnkeeperBehavior.INNKEEPER.id());
        Citizen citizen = GatheringGameTests.worker(helper, kingdom, InnkeeperBehavior.INNKEEPER.id(), pos, post.north());
        helper.assertTrue(LifeWork.open(helper.getLevel().getServer(), tavern), "The tavern is not open with its innkeeper");
        return citizen;
    }

    private static CitizenEntity body(GameTestHelper helper, Citizen citizen, BlockPos relative) {
        CitizenBodies.force(citizen.id(), true);
        CitizenEntity body = CitizenBodies.spawnAt(helper.getLevel(), citizen, helper.absolutePos(relative));
        helper.assertTrue(body != null, "No body for the citizen");
        return body;
    }

    private static void end(GameTestHelper helper, Kingdom kingdom, BlockPos[] area, Citizen... citizens) {
        LifeSettings.override(null);
        LifeWork.setRandomForCheck(null);
        for (Citizen citizen : citizens) {
            LifeWork.forget(helper.getLevel().getServer(), citizen.id());
        }
        LifeWork.state(helper.getLevel().getServer()).forgetKingdom(kingdom.id());
        CitizenGameTests.finish(helper, kingdom, citizens);
        GatheringGameTests.clear(helper.getLevel(), area[0], area[1]);
        BuildingTemplates.removeForTest(TAVERN);
        BuildingEffects.changed();
    }

    private static void fund(MinecraftServer server, Kingdom kingdom, long amount) {
        KingdomWorldApi.state(server).economy().adminTreasury(kingdom.id(), amount, TreasuryActions.day(server));
    }

    private static long funds(MinecraftServer server, Kingdom kingdom) {
        return KingdomWorldApi.state(server).economy().treasury(kingdom.id()).orElseThrow().funds();
    }

    private static long money(MinecraftServer server, Kingdom kingdom) {
        Economy economy = KingdomWorldApi.state(server).economy();
        return economy.supply(kingdom.id()) + economy.coinsOut(kingdom.id());
    }

    private static ServerPlayer player(GameTestHelper helper, String name, BlockPos relative) {
        ServerPlayer player = LawStandIn.create(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        BlockPos at = helper.absolutePos(relative);
        player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return player;
    }

    /**
     * A bed of the kingdom: the head at the place, the foot to the south.
     */
    private static WorldPos bed(GameTestHelper helper, Kingdom kingdom, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        BlockPos head = helper.absolutePos(relative);
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, net.minecraft.core.Direction.NORTH);
        level.setBlock(head.south(), bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
        level.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
        WorldPos pos = new WorldPos(Dimensions.id(level), head.getX(), head.getY(), head.getZ());
        KingdomWorldApi.state(level.getServer()).citizens().addSite(pos, SiteKinds.BED, kingdom.id());
        return pos;
    }

    // ---------------------------------------------------------------- the day

    @GameTest(template = EMPTY, batch = "kingdomworld_life_tavern", timeoutTicks = 800)
    public static void aCitizenGoesToTheTavernInTheEveningAndIsInABetterMood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper);
        rules();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Tavern Evening", UUID.randomUUID());
        Building tavern = tavern(helper, kingdom);
        StorehouseInventory store = GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(0, 0, 8)));
        store.insertItem(0, new ItemStack(Items.HONEY_BOTTLE, 5), false);
        Citizen keeper = innkeeper(helper, kingdom, tavern);
        Citizen guest = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
        body(helper, guest, new BlockPos(1, 0, 1));
        at(level, LifeSettings.rules().eveningStart() + 200);
        long today = LifeWork.today(server);
        helper.assertTrue(LifeWork.mood(server, guest) == 0, "The guest is glad before the evening");
        helper.succeedWhen(() -> {
            helper.assertTrue(LifeWork.state(server).tavernDay(guest.id()) == today, "The guest did not spend the evening in the tavern");
            helper.assertTrue(CitizenGameTests.count(store, Items.HONEY_BOTTLE) == 4, "The tavern served nothing from the storehouse");
            helper.assertTrue(LifeWork.mood(server, guest) == LifeSettings.rules().tavernMood(), "The evening gave no mood");
            end(helper, kingdom, area, guest, keeper);
        });
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_life_sleep", timeoutTicks = 800)
    public static void aCitizenSleepsInItsBedAtNight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper);
        rules();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Sleepers", UUID.randomUUID());
        WorldPos bed = bed(helper, kingdom, new BlockPos(6, 0, 6));
        Citizen sleeper = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
        helper.assertTrue(KingdomWorldApi.state(server).citizens().assignHome(sleeper, bed), "The citizen got no bed");
        CitizenEntity body = body(helper, sleeper, new BlockPos(1, 0, 1));
        at(level, 18_000);
        BlockPos head = new BlockPos(bed.x(), bed.y(), bed.z());
        helper.succeedWhen(() -> {
            helper.assertTrue(body.isSleeping(), "The citizen is not asleep");
            helper.assertTrue(body.getSleepingPos().filter(head::equals).isPresent(), "The citizen sleeps in another bed: " + body.getSleepingPos());
            helper.assertTrue(level.getBlockState(head).getValue(BedBlock.OCCUPIED), "The bed is not taken");
            // In the morning it gets up and the bed is free again.
            at(level, LifeSettings.rules().workStart() + 100);
            helper.assertTrue(io.github.sekerator.kingdomworld.common.life.DayGoal.shouldWake(body), "The citizen would sleep through the morning");
            body.stopSleeping();
            helper.assertTrue(!level.getBlockState(head).getValue(BedBlock.OCCUPIED), "The bed stays taken");
            end(helper, kingdom, area, sleeper);
        });
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_life_sleeping_witness", timeoutTicks = 200)
    public static void aSleeperSeesNoTheftButABlowCloseByWakesIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper);
        rules();
        LawRules.Builder slow = LawSettings.configured();
        slow.reportMinTicks = 1_000_000;
        slow.reportMaxTicks = 1_000_000;
        LawSettings.override(slow.build());
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Sleeping Witness", UUID.randomUUID());
        WorldPos bed = bed(helper, kingdom, new BlockPos(4, 0, 4));
        Citizen witness = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
        KingdomWorldApi.state(server).citizens().assignHome(witness, bed);
        CitizenEntity body = body(helper, witness, new BlockPos(4, 0, 2));
        at(level, 18_000);
        body.startSleeping(new BlockPos(bed.x(), bed.y(), bed.z()));
        ServerPlayer thief = player(helper, "kw-night-thief", new BlockPos(6, 0, 4));
        Chicken hen = EntityType.CHICKEN.create(level);
        try {
            helper.assertTrue(body.isSleeping(), "The witness is not asleep");
            helper.assertTrue(!LawWork.deed(thief, thief.blockPosition(), kingdom.id(), CrimeKind.THEFT, 40, "", null, false, null),
                    "A sleeper saw a theft");
            helper.assertTrue(body.isSleeping(), "A theft woke the sleeper");
            hen.moveTo(thief.getX() + 1, thief.getY(), thief.getZ());
            level.addFreshEntity(hen);
            helper.assertTrue(LawWork.deed(thief, thief.blockPosition(), kingdom.id(), CrimeKind.ASSAULT, 0, "", null, false, hen),
                    "A blow close by did not wake the sleeper to see it");
            helper.assertTrue(!body.isSleeping(), "The sleeper still sleeps after the blow");
        } finally {
            hen.discard();
            LawSettings.override(null);
            LawWork.deliverNow(server);
            LawWork.state(server).forgetKingdom(kingdom.id());
            end(helper, kingdom, area, witness);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- the tavern

    @GameTest(template = EMPTY, batch = "kingdomworld_life_innkeeper", timeoutTicks = 200)
    public static void theInnkeeperSellsFoodFromTheStorehouseAndTheMoneyGoesToTheTreasury(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper);
        rules();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Innkeeper", UUID.randomUUID());
        Building tavern = tavern(helper, kingdom);
        StorehouseInventory store = GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(0, 0, 8)));
        store.insertItem(0, new ItemStack(Items.BREAD, 10), false);
        store.insertItem(1, new ItemStack(Items.DIAMOND, 5), false);
        Citizen keeper = innkeeper(helper, kingdom, tavern);
        CitizenEntity body = CitizenBodies.body(keeper.id());
        ServerPlayer guest = player(helper, "kw-tavern-guest", new BlockPos(5, 0, 4));
        try {
            fund(server, kingdom, 10_000);
            Economy economy = KingdomWorldApi.state(server).economy();
            helper.assertTrue(economy.kingdomPays(kingdom.id(), 1_000, null, LedgerCategory.ADMIN, TreasuryActions.day(server)).ok(),
                    "The treasury could not hand out coins");
            Purses.payOut(guest, kingdom.id(), 1_000);
            long before = money(server, kingdom);
            long treasury = funds(server, kingdom);
            helper.assertTrue(TavernActions.open(guest, body), "Talking to the innkeeper opened no tavern");
            var view = TavernActions.view(guest, tavern, body.getId()).orElseThrow();
            helper.assertTrue(view.open() && view.goods().size() == 1 && view.goods().get(0).stack().is(Items.BREAD),
                    "The tavern does not serve just the bread: " + view.goods());
            long price = view.goods().get(0).price();
            KingdomActions.Feedback bought = TavernActions.buy(guest, tavern, "minecraft:bread", 3, false);
            helper.assertTrue(bought.success(), "The purchase failed: " + bought.message().getString());
            helper.assertTrue(guest.getInventory().countItem(Items.BREAD) == 3, "The guest got no bread");
            helper.assertTrue(CitizenGameTests.count(store, Items.BREAD) == 7, "The bread did not come from the storehouse");
            helper.assertTrue(funds(server, kingdom) == treasury + 3 * price, "The treasury did not get the price: " + (funds(server, kingdom) - treasury));
            helper.assertTrue(money(server, kingdom) == before, "Money was made or lost: " + (money(server, kingdom) - before));
            helper.assertTrue(CoinStacks.count(guest.getInventory(), kingdom.id()).value(Purses.units(server, kingdom.id())) == 1_000 - 3 * price,
                    "The guest paid the wrong amount");
            helper.assertTrue(!TavernActions.buy(guest, tavern, "minecraft:diamond", 1, false).success(), "The tavern sold a diamond");
        } finally {
            end(helper, kingdom, area, keeper);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_life_rumor_camp", timeoutTicks = 200)
    public static void aRumorOfACampPointsAtTheRealCampAndCanBeMarkedOnTheAtlas(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper);
        rules();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Rumors", UUID.randomUUID());
        Building tavern = tavern(helper, kingdom);
        Citizen keeper = innkeeper(helper, kingdom, tavern);
        WorldPos center = LifeWork.center(tavern);
        BanditCamp camp = BanditWork.found(server, center.dimension(), center.x() + 10, center.z() - 420, 6);
        ServerPlayer guest = player(helper, "kw-rumor-guest", new BlockPos(5, 0, 4));
        try {
            List<Rumor> rumors = LifeRumors.candidates(server, kingdom.id(), center);
            Rumor told = rumors.stream().filter(rumor -> rumor.campId() == camp.id()).findFirst()
                    .orElseThrow(() -> new IllegalStateException("No rumor of the camp: " + rumors));
            helper.assertTrue(told.args().get(0).text().equals("kingdomworld.life.camp.band"), "The camp sounds wrong: " + told.args());
            helper.assertTrue(told.args().get(1).text().equals("400"), "The distance is wrong: " + told.args());
            helper.assertTrue(told.args().get(2).text().equals("kingdomworld.life.direction.north"), "The direction is wrong: " + told.args());
            LifeRumors.refresh();
            // The talk of the day holds the camp: it is the only rumor.
            helper.assertTrue(LifeRumors.today(server, tavern).stream().anyMatch(rumor -> rumor.campId() == camp.id()), "The innkeeper keeps the camp quiet");
            fund(server, kingdom, 1_000);
            helper.assertTrue(KingdomWorldApi.state(server).economy().kingdomPays(kingdom.id(), 100, null, LedgerCategory.ADMIN,
                    TreasuryActions.day(server)).ok(), "The treasury could not hand out coins");
            Purses.payOut(guest, kingdom.id(), 100);
            KingdomActions.Feedback marked = TavernActions.mark(guest, tavern, camp.id(), false);
            helper.assertTrue(marked.success(), "The camp was not marked: " + marked.message().getString());
            helper.assertTrue(LifeWork.state(server).marks(guest.getUUID()).contains(camp.id()), "The atlas does not know the camp");
            helper.assertTrue(!TavernActions.mark(guest, tavern, camp.id(), false).success(), "The same camp was sold twice");
        } finally {
            RoadWork.state(server).removeCamp(camp.id());
            LifeWork.state(server).keepMarks(id -> false);
            end(helper, kingdom, area, keeper);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- illness

    @GameTest(template = EMPTY, batch = "kingdomworld_life_sick", timeoutTicks = 800)
    public static void anIllCitizenGoesToTheInfirmaryAndGetsWell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper);
        rules();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Infirmary", UUID.randomUUID());
        BlockPos infirmary = helper.absolutePos(new BlockPos(7, 0, 7));
        level.setBlockAndUpdate(infirmary, ModRegistries.INFIRMARY.get().defaultBlockState());
        KingdomWorldApi.state(server).citizens().addSite(new WorldPos(Dimensions.id(level), infirmary.getX(), infirmary.getY(), infirmary.getZ()),
                SiteKinds.INFIRMARY, kingdom.id());
        at(level, 6_000);
        Citizen patient = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
        CitizenEntity body = body(helper, patient, new BlockPos(1, 0, 1));
        LifeWork.fallIll(server, patient.id());
        helper.assertTrue(body.needsCare() && LifeWork.mood(server, patient) == -LifeSettings.rules().sickMood(), "The illness changed nothing");
        helper.succeedWhen(() -> {
            helper.assertTrue(CitizenCare.isInInfirmary(body), "The patient is not in the infirmary");
            // A day's worth of care in the infirmary makes it well.
            LifeWork.step(server, patient, body, (int) Math.ceil(LifeSettings.rules().infirmaryDays() * LifeRules.DAY), false);
            helper.assertTrue(!LifeWork.isSick(patient.id()) && !LifeWork.state(server).isSick(patient.id()), "The patient is still ill");
            helper.assertTrue(!body.needsCare(), "The patient still needs care");
            end(helper, kingdom, area, patient);
        });
    }

    // ---------------------------------------------------------------- festivals

    @GameTest(template = EMPTY, batch = "kingdomworld_life_festival", timeoutTicks = 200)
    public static void aFestivalTakesItsTreatAndRaisesTheMood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper);
        rules();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Festival", UUID.randomUUID());
        StorehouseInventory store = GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(0, 0, 8)));
        store.insertItem(0, new ItemStack(Items.BREAD, 30), false);
        Citizen[] people = {CitizenGameTests.newcomer(helper, kingdom, 1, 1), CitizenGameTests.newcomer(helper, kingdom, 3, 1),
                CitizenGameTests.newcomer(helper, kingdom, 5, 1)};
        try {
            fund(server, kingdom, 1_000);
            long treasury = funds(server, kingdom);
            int moodBefore = people[0].mood();
            KingdomActions.Feedback called = LifeWork.callFestival(server, null, kingdom.id());
            helper.assertTrue(called.success(), "The festival was refused: " + called.message().getString());
            LifeRules rules = LifeSettings.rules();
            helper.assertTrue(funds(server, kingdom) == treasury - 3 * rules.festivalPerCitizen(), "The treasury paid the wrong amount");
            int eaten = 30 - CitizenGameTests.count(store, Items.BREAD);
            helper.assertTrue(eaten * 5 >= 3 * rules.festivalFood() && eaten > 0, "The treat was not taken: " + eaten + " bread");
            LifeState.Festival festival = LifeWork.state(server).festival(kingdom.id());
            helper.assertTrue(festival.treated() && festival.festivalDay() == LifeWork.today(server), "The festival is not today");
            helper.assertTrue(LifeWork.mood(server, people[0]) == rules.festivalMood(), "The festival gives no mood");
            helper.assertTrue(LifeWork.state(server).events(kingdom.id()).get(0).kind().equals("festival"), "The chronicle missed the festival");
            CitizenSimulation.step(server, kingdom.id());
            helper.assertTrue(people[0].mood() > moodBefore, "The mood did not rise: " + moodBefore + " -> " + people[0].mood());
            helper.assertTrue(!LifeWork.callFestival(server, null, kingdom.id()).success(), "A second festival the same day");
        } finally {
            end(helper, kingdom, area, people);
        }
        helper.succeed();
    }
}
