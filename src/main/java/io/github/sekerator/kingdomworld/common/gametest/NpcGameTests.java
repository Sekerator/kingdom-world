package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.npc.NpcSavedData;
import io.github.sekerator.kingdomworld.common.npc.NpcSettings;
import io.github.sekerator.kingdomworld.common.npc.NpcSpawner;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.road.CityWalls;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.trade.StallActions;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.economy.TreasuryJournal;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.npc.NpcComposition;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.SpawnStage;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Game tests of the kingdoms of the world. A small kingdom appears on a flat platform high above the test place (at the
 * height {@value #PLATFORM}, above any ground of the world, so that the land under it does not matter): its throne, land,
 * buildings with roles, citizens at work, soldiers, a treasury that agrees with its founding; the appearance leaves the land
 * and the buildings of others alone; a restart in the middle and a second appearance of the same place make nothing twice;
 * its market sells to a player; a player becomes its subject. Every test runs in a batch of its own and takes down what it
 * built.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcGameTests {
    private static final String EMPTY = "empty";
    /** The height of the top of the platform. */
    private static final int PLATFORM = 290;
    /** Half the side of the platform, in blocks: the wall, the fields and the grove of the lumberjack fit on it. */
    private static final int HALF = 60;
    /** Steps the appearance of a small kingdom takes at most. */
    private static final int STEPS = 4_000;

    private NpcGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Flat grass at {@value #PLATFORM} around the test place with air above, after kingdoms left near it by earlier tests are
     * dissolved.
     */
    private static BlockPos platform(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos center = new BlockPos(origin.getX(), PLATFORM, origin.getZ());
        ChunkPos chunk = new ChunkPos(center);
        List<UUID> stale = new ArrayList<>();
        for (Territory territory : KingdomWorldApi.state(server).territory().territories()) {
            if (territory.chunks().stream().anyMatch(key -> Math.max(Math.abs(new ChunkPos(key).x - chunk.x), Math.abs(new ChunkPos(key).z - chunk.z))
                    <= 16)) {
                stale.add(territory.kingdomId());
            }
        }
        stale.forEach(id -> KingdomWorldApi.dissolveKingdom(server, id));
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                BlockPos column = center.offset(x, 0, z);
                set(level, column.below(), dirt);
                set(level, column, grass);
                for (int y = 1; y <= 18; y++) {
                    set(level, column.above(y), air);
                }
            }
        }
        return center;
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos) != state) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    /**
     * Takes the kingdom of a place down and clears the platform.
     */
    private static void cleanUp(GameTestHelper helper, NpcSite site, BlockPos center) {
        MinecraftServer server = helper.getLevel().getServer();
        if (site != null) {
            NpcWork.remove(server, site.id(), true);
        }
        GatheringGameTests.clear(helper.getLevel(), center.offset(-HALF, -1, -HALF), center.offset(HALF, 18, HALF));
    }

    /**
     * A small kingdom of the world appearing on the platform, without towns.
     */
    private static NpcSite plant(GameTestHelper helper, BlockPos center) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(NpcWork.state(server).spawning().isEmpty(), "Another kingdom of the world is appearing");
        NpcSite site = NpcWork.plant(server, center.getX(), center.getZ(), NpcLevel.SMALL, false).orElse(null);
        helper.assertTrue(site != null, "The data of the kingdoms of the world is read-only");
        return site;
    }

    private static Kingdom kingdomOf(GameTestHelper helper, NpcSite site) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID id = site.kingdomId().orElse(null);
        Kingdom kingdom = id == null ? null : KingdomWorldApi.kingdom(server, id).orElse(null);
        helper.assertTrue(kingdom != null, "The kingdom of the world did not appear: " + site + " " + site.reason());
        return kingdom;
    }

    private static long founding(MinecraftServer server, UUID kingdom) {
        Treasury treasury = KingdomWorldApi.treasury(server, kingdom).orElseThrow();
        return treasury.journal().totals(TreasuryActions.day(server), TreasuryJournal.Period.WEEK)
                .getOrDefault(LedgerCategory.FOUNDING, TreasuryJournal.Totals.ZERO).income();
    }

    private static List<Building> buildings(MinecraftServer server, UUID kingdom) {
        List<Building> list = new ArrayList<>();
        for (Building building : KingdomWorldApi.state(server).construction().buildingsOf(kingdom)) {
            if (!CityWalls.isFortification(building)) {
                list.add(building);
            }
        }
        return list;
    }

    private static ServerPlayer player(GameTestHelper helper, String name, BlockPos at) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name));
        player.setPos(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5);
        return player;
    }

    // ---------------------------------------------------------------- appearance

    @GameTest(template = EMPTY, batch = "kingdomworld_npc_appear", timeoutTicks = 200)
    public static void aSmallKingdomAppearsWithEverythingItNeeds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = platform(helper);
        NpcSite site = null;
        try {
            site = plant(helper, center);
            NpcSpawner.runNow(server, site, STEPS);
            helper.assertTrue(site.status() == NpcSite.Status.SPAWNED, "The appearance did not end: " + site + " at " + site.stage().id()
                    + " " + site.reason());
            Kingdom kingdom = kingdomOf(helper, site);
            KingdomWorldState state = KingdomWorldApi.state(server);
            helper.assertTrue(NpcWork.isNpc(server, kingdom.id()) && kingdom.rulerId() == null, "Not a kingdom of the world without a player king");
            helper.assertTrue(level.getBlockState(NpcSpawner.thronePos(site)).is(ModRegistries.THRONE.get()), "No throne in the middle");
            int chunks = state.territory().territory(kingdom.id()).map(Territory::size).orElse(0);
            int radius = NpcSettings.rules().landRadius(NpcLevel.SMALL);
            helper.assertTrue(chunks == (2 * radius + 1) * (2 * radius + 1), "Land of " + chunks + " chunks");
            NpcComposition composition = NpcSpawner.composition(site);
            List<Building> built = buildings(server, kingdom.id());
            helper.assertTrue(built.size() == composition.buildings().size(), "Buildings " + built.size() + " of " + composition.buildings().size());
            Set<String> roles = new HashSet<>();
            built.forEach(building -> roles.add(building.role()));
            for (String role : List.of("house", "warehouse", "workshop", "market", "well", "barracks", "tower")) {
                helper.assertTrue(roles.contains(role), "No building with the role " + role + ": " + roles);
            }
            for (Building building : built) {
                helper.assertTrue(building.integrity() == 100 || building.integrity() < 0 || building.checked() == 0,
                        building.templateId() + " is not whole: " + building.integrity());
                // Nothing hangs in the air: the ground under the floor of every building is solid.
                BlockPos under = new BlockPos(building.anchor().x(), building.anchor().y() - 1, building.anchor().z());
                helper.assertTrue(!level.getBlockState(under).isAir(), building.templateId() + " stands on air at " + under.toShortString());
            }
            List<Citizen> citizens = state.citizens().registry().citizensOf(kingdom.id());
            int beds = 0;
            for (NpcComposition.Plot plot : composition.buildings()) {
                beds += NpcComposition.beds(plot.template());
            }
            helper.assertTrue(citizens.size() == beds, "Citizens " + citizens.size() + " for " + beds + " beds");
            Set<String> professions = new HashSet<>();
            int homeless = 0;
            for (Citizen citizen : citizens) {
                professions.add(citizen.professionId());
                homeless += citizen.home() == null ? 1 : 0;
            }
            helper.assertTrue(homeless == 0, homeless + " citizens without a home");
            for (String profession : List.of("merchant", "crafter", "farmer", "warrior")) {
                helper.assertTrue(professions.contains(profession), "Nobody is a " + profession + ": " + professions);
            }
            int soldiers = ArmyWork.state(server).soldiersOf(kingdom.id()).size();
            helper.assertTrue(soldiers == NpcSettings.rules().soldiers(NpcLevel.SMALL), "Soldiers: " + soldiers);
            helper.assertTrue(!ArmyWork.state(server).squadsOf(kingdom.id()).isEmpty()
                    && ArmyWork.state(server).squadsOf(kingdom.id()).get(0).order().anchor().isPresent(), "The soldiers have no order");
            long money = NpcSettings.rules().money(NpcLevel.SMALL);
            Treasury treasury = KingdomWorldApi.treasury(server, kingdom.id()).orElseThrow();
            helper.assertTrue(treasury.balance() == money && founding(server, kingdom.id()) == money,
                    "The treasury " + treasury.balance() + " does not agree with its founding " + founding(server, kingdom.id()));
            helper.assertTrue(KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.BREAD)) > 0, "No bread in the storehouses");
            Wall wall = RoadWork.state(server).wallOf(0, kingdom.id()).orElse(null);
            helper.assertTrue(wall != null && wall.wallClass() == Wall.WallClass.PALISADE && wall.built(), "No palisade around the village");
            helper.succeed();
        } finally {
            cleanUp(helper, site, center);
        }
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_npc_others", timeoutTicks = 200)
    public static void anAppearanceLeavesTheLandAndBuildingsOfOthersAlone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = platform(helper);
        NpcSite site = null;
        UUID neighbours = null;
        ChunkPos middle = new ChunkPos(center);
        try {
            // A kingdom of players with a house of planks and a chest east of the place.
            BlockPos throne = center.offset(48, 1, 0);
            level.setBlockAndUpdate(throne, ModRegistries.THRONE.get().defaultBlockState());
            CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, throne, "GameTest Neighbours", 0x2E86C1, null);
            helper.assertTrue(result instanceof CreateKingdomResult.Created, "The neighbours were not founded: " + result);
            neighbours = ((CreateKingdomResult.Created) result).kingdom().id();
            BlockPos house = center.offset(44, 1, 4);
            level.setBlockAndUpdate(house, Blocks.OAK_PLANKS.defaultBlockState());
            level.setBlockAndUpdate(house.above(), Blocks.CHEST.defaultBlockState());
            site = plant(helper, center);
            NpcSpawner.runNow(server, site, STEPS);
            helper.assertTrue(site.status() == NpcSite.Status.BLOCKED, "The kingdom appeared next to the land of others: " + site);
            helper.assertTrue(site.kingdomId().isEmpty(), "A kingdom was founded");
            helper.assertTrue(level.getBlockState(house).is(Blocks.OAK_PLANKS) && level.getBlockState(house.above()).is(Blocks.CHEST),
                    "The house of the neighbours was touched");
            helper.assertTrue(level.getBlockState(NpcSpawner.thronePos(site)).isAir() || !level.getBlockState(NpcSpawner.thronePos(site))
                    .is(ModRegistries.THRONE.get()), "A throne stands at the place");
            KingdomWorldApi.dissolveKingdom(server, neighbours);
            neighbours = null;
            level.setBlockAndUpdate(house.above(), Blocks.AIR.defaultBlockState());
            // Players spent hours here: the place is left alone as well.
            level.getChunk(middle.x, middle.z).setInhabitedTime(10L * 60 * 60 * 20);
            NpcSite second = plant(helper, center.offset(0, 0, 1));
            NpcSpawner.runNow(server, second, STEPS);
            helper.assertTrue(second.status() == NpcSite.Status.BLOCKED && second.reason().contains("players"),
                    "The kingdom appeared where players spent hours: " + second + " " + second.reason());
            helper.succeed();
        } finally {
            level.getChunk(middle.x, middle.z).setInhabitedTime(0);
            if (neighbours != null) {
                KingdomWorldApi.dissolveKingdom(server, neighbours);
            }
            cleanUp(helper, site, center);
        }
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_npc_again", timeoutTicks = 200)
    public static void aRestartAndASecondAppearanceMakeNothingTwice(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = platform(helper);
        NpcSite site = null;
        try {
            site = plant(helper, center);
            for (int i = 0; i < STEPS && !(site.stage() == SpawnStage.BUILD && site.step() >= 3); i++) {
                NpcSpawner.runNow(server, site, 1);
            }
            helper.assertTrue(site.stage() == SpawnStage.BUILD, "The appearance did not reach the buildings: " + site.stage().id());
            // A restart: what is kept in memory goes, the saved data is read again.
            CompoundTag saved = NpcSavedData.get(server).save(new CompoundTag());
            NpcSavedData reread = NpcSavedData.load(saved);
            NpcSite copy = reread.state().site(site.id()).orElse(null);
            helper.assertTrue(!reread.isReadOnly() && copy != null && copy.stage() == SpawnStage.BUILD && copy.step() == site.step()
                    && copy.kingdomId().equals(site.kingdomId()) && reread.state().isNpc(site.kingdomId().orElseThrow()),
                    "The progress did not survive saving: " + copy);
            NpcSpawner.resetForTests();
            NpcSpawner.runNow(server, site, STEPS);
            helper.assertTrue(site.status() == NpcSite.Status.SPAWNED, "The appearance did not end after the restart: " + site.stage().id());
            Kingdom kingdom = kingdomOf(helper, site);
            NpcComposition composition = NpcSpawner.composition(site);
            int buildings = buildings(server, kingdom.id()).size();
            helper.assertTrue(buildings == composition.buildings().size(), "Buildings after the restart: " + buildings);
            int citizens = KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdom.id()).size();
            int soldiers = ArmyWork.state(server).soldiersOf(kingdom.id()).size();
            long balance = KingdomWorldApi.treasury(server, kingdom.id()).orElseThrow().balance();
            long bread = KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.BREAD));
            // The same place appears once more from its founding on.
            NpcSpawner.start(server, site);
            site.advance(SpawnStage.FOUND);
            NpcSpawner.runNow(server, site, STEPS);
            helper.assertTrue(site.status() == NpcSite.Status.SPAWNED, "The second appearance did not end");
            int place = site.id();
            long kingdomsOfThePlace = NpcWork.state(server).kingdoms().stream().filter(k -> k.siteId() == place).count();
            helper.assertTrue(kingdomsOfThePlace == 1, "Kingdoms of the place: " + kingdomsOfThePlace);
            helper.assertTrue(buildings(server, kingdom.id()).size() == buildings, "Buildings twice");
            helper.assertTrue(KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdom.id()).size() == citizens, "Citizens twice");
            helper.assertTrue(ArmyWork.state(server).soldiersOf(kingdom.id()).size() == soldiers, "Soldiers twice");
            helper.assertTrue(KingdomWorldApi.treasury(server, kingdom.id()).orElseThrow().balance() == balance
                    && founding(server, kingdom.id()) == NpcSettings.rules().money(NpcLevel.SMALL), "The founding money came twice");
            helper.assertTrue(KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.BREAD)) == bread, "The stores came twice");
            long thrones = 0;
            BlockPos throne = NpcSpawner.thronePos(site);
            for (BlockPos pos : BlockPos.betweenClosed(throne.offset(-8, -1, -8), throne.offset(8, 2, 8))) {
                thrones += level.getBlockState(pos).is(ModRegistries.THRONE.get()) ? 1 : 0;
            }
            helper.assertTrue(thrones == 1, "Thrones: " + thrones);
            helper.succeed();
        } finally {
            cleanUp(helper, site, center);
        }
    }

    // ---------------------------------------------------------------- players

    @GameTest(template = EMPTY, batch = "kingdomworld_npc_market", timeoutTicks = 200)
    public static void theMarketOfAKingdomOfTheWorldSellsToAPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = platform(helper);
        NpcSite site = null;
        try {
            site = plant(helper, center);
            NpcSpawner.runNow(server, site, STEPS);
            Kingdom kingdom = kingdomOf(helper, site);
            ServerPlayer buyer = player(helper, "GameTest_Buyer", center);
            KingdomWorldApi.change(server, "fund a buyer", state -> state.economy().adminAccount(buyer.getUUID(), kingdom.id(), 5_000));
            long before = KingdomWorldApi.treasury(server, kingdom.id()).orElseThrow().balance();
            long stock = KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.BREAD));
            KingdomActions.Feedback bought = StallActions.buy(buyer, kingdom, "minecraft:bread", 4, true, kingdom.id());
            helper.assertTrue(bought.success(), "The market did not sell: " + bought.message().getString());
            helper.assertTrue(buyer.getInventory().countItem(Items.BREAD) == 4, "The buyer has no bread");
            helper.assertTrue(KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.BREAD)) == stock - 4,
                    "The bread did not leave the storehouses");
            helper.assertTrue(KingdomWorldApi.treasury(server, kingdom.id()).orElseThrow().balance() > before
                    || KingdomWorldApi.balance(server, buyer.getUUID(), kingdom.id()) < 5_000, "Nobody paid for the bread");
            buyer.getInventory().clearContent();
            helper.succeed();
        } finally {
            cleanUp(helper, site, center);
        }
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_npc_subject", timeoutTicks = 200)
    public static void aPlayerBecomesASubjectOfAKingdomOfTheWorld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = platform(helper);
        NpcSite site = null;
        ServerPlayer player = null;
        try {
            site = plant(helper, center);
            NpcSpawner.runNow(server, site, STEPS);
            Kingdom kingdom = kingdomOf(helper, site);
            player = player(helper, "GameTest_Subject", center);
            UUID id = player.getUUID();
            KingdomWorldApi.kingdomOf(server, id).ifPresent(old -> KingdomWorldApi.change(server, "leave", s -> s.membership().leave(id)));
            KingdomActions.Feedback applied = KingdomActions.apply(player, kingdom);
            helper.assertTrue(applied.success(), "The application failed: " + applied.message().getString());
            KingdomWorldState state = KingdomWorldApi.state(server);
            helper.assertTrue(state.membership().kingdomOf(id).filter(kingdom.id()::equals).isPresent(), "The ruler did not take the player");
            helper.assertTrue(state.membership().memberOf(id).map(member -> member.role() == Role.SUBJECT).orElse(false), "Not a subject");
            helper.assertTrue(KingdomWorldApi.kingdom(server, kingdom.id()).orElseThrow().rulerId() == null, "A player took the throne");
            KingdomWorldApi.change(server, "leave", s -> s.membership().leave(id));
            helper.succeed();
        } finally {
            cleanUp(helper, site, center);
        }
    }
}
