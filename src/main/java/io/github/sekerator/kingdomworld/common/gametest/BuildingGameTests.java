package io.github.sekerator.kingdomworld.common.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSimulation;
import io.github.sekerator.kingdomworld.common.construction.BlueprintCapture;
import io.github.sekerator.kingdomworld.common.construction.BuilderBehavior;
import io.github.sekerator.kingdomworld.common.construction.BuildingEffects;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplate;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplates;
import io.github.sekerator.kingdomworld.common.construction.BuildingWork;
import io.github.sekerator.kingdomworld.common.construction.ConstructionActions;
import io.github.sekerator.kingdomworld.common.construction.ConstructionSettings;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.construction.CustomTemplates;
import io.github.sekerator.kingdomworld.common.citizen.work.GatheringSettings;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.packet.BuildingPackets;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.BuildingRules;
import io.github.sekerator.kingdomworld.core.construction.BuildingRoles;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.ConstructionRules;
import io.github.sekerator.kingdomworld.core.construction.CustomBlueprint;
import io.github.sekerator.kingdomworld.core.construction.Facing;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Game tests of the buildings of the register: roles that work and rest, upgrades by the difference, repairs, demolitions,
 * blueprints saved from the world, other wood, rights and land.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BuildingGameTests {
    private static final String EMPTY = "empty";
    /** Fast rules for tests: a block in two ticks, no falls. */
    private static final ConstructionRules FAST = new ConstructionRules(4, 64, 4.5, 12.0, 400, 2, 0.0, 48, 32, 48);

    private BuildingGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    private static JsonObject description(String name, String role, int level, String upgradesTo) {
        return JsonParser.parseString("{\"name\": \"kingdomworld.building.small_house\", \"fallback_name\": \"" + name
                + "\", \"category\": \"housing\", \"type\": \"" + (role.isEmpty() ? "test" : role) + "\", \"role\": \"" + (role.isEmpty() ? "none" : role)
                + "\", \"level\": " + level + ", \"upgrades_to\": \"" + upgradesTo + "\", \"icon\": \"minecraft:cobblestone\", \"anchor\": [1, 0, 2], "
                + "\"entrance\": \"south\"}").getAsJsonObject();
    }

    /**
     * A 3 x 2 x 3 template: a floor of cobblestone and on it the given blocks of the second layer, by {x, z, palette index}.
     */
    private static BuildingTemplate template(String id, String role, int level, String upgradesTo, List<String> palette, int[][] upper) {
        List<String> states = new java.util.ArrayList<>(palette);
        states.add(0, "minecraft:cobblestone");
        int[][] blocks = new int[9 + upper.length][];
        int i = 0;
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                blocks[i++] = new int[]{x, 0, z, 0};
            }
        }
        for (int[] block : upper) {
            blocks[i++] = new int[]{block[0], 1, block[1], block[2] + 1};
        }
        CompoundTag structure = ConstructionGameTests.structure(3, 2, 3, states, blocks);
        // States with properties: the name and the properties go apart, as the structure block writes them.
        ListTag paletteTag = structure.getList("palette", 10);
        for (int p = 0; p < paletteTag.size(); p++) {
            CompoundTag entry = paletteTag.getCompound(p);
            String name = entry.getString("Name");
            int open = name.indexOf('[');
            if (open > 0) {
                CompoundTag properties = new CompoundTag();
                for (String pair : name.substring(open + 1, name.length() - 1).split(",")) {
                    String[] parts = pair.split("=");
                    properties.putString(parts[0], parts[1]);
                }
                entry.putString("Name", name.substring(0, open));
                entry.put("Properties", properties);
            }
        }
        return BuildingTemplates.addForTest(KingdomWorld.id(id), description(id, role, level, upgradesTo), structure);
    }

    private static BlockState at(GameTestHelper helper, int x, int y, int z) {
        return helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, y, z)));
    }

    private static long stored(MinecraftServer server, Kingdom kingdom, Item item) {
        return KingdomStorage.count(server, kingdom.id(), stack -> stack.is(item));
    }

    private static Construction lay(GameTestHelper helper, Kingdom kingdom, BuildingTemplate template, BlockPos relative, Materials materials) {
        KingdomWorldState.ConstructionChange change = ConstructionWork.lay(helper.getLevel(), null, kingdom.id(), template,
                helper.absolutePos(relative), Placement.NONE, materials);
        if (change.construction().isEmpty()) {
            throw new IllegalStateException("The construction was refused: " + change.outcome());
        }
        return change.construction().get();
    }

    /**
     * Builds a template at once as an operator would and returns its building.
     */
    private static Building build(GameTestHelper helper, Kingdom kingdom, BuildingTemplate template, BlockPos relative) {
        Construction construction = lay(helper, kingdom, template, relative, Materials.NONE);
        return ConstructionWork.finishNow(helper.getLevel().getServer(), construction).orElseThrow();
    }

    private static Building current(MinecraftServer server, Building building) {
        return KingdomWorldApi.state(server).construction().building(building.id()).orElseThrow();
    }

    private static void end(GameTestHelper helper, Kingdom kingdom, BlockPos from, BlockPos to, ResourceLocation... templates) {
        ConstructionSettings.override(null);
        ConstructionSettings.overrideBuildings(null);
        CitizenGameTests.finish(helper, kingdom);
        GatheringGameTests.clear(helper.getLevel(), from, to);
        for (ResourceLocation template : templates) {
            BuildingTemplates.removeForTest(template);
        }
        BuildingEffects.changed();
    }

    private static BlockPos[] area(GameTestHelper helper) {
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(12, 4, 12));
        GatheringGameTests.clear(helper.getLevel(), from, to);
        return new BlockPos[]{from, to};
    }

    // ---------------------------------------------------------------- roles

    @GameTest(template = EMPTY, batch = "kingdomworld_roles_house")
    public static void aHouseGivesHomesComfortAndGrowth(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Householders", UUID.randomUUID());
        BuildingTemplate house = template("test_role_house", BuildingRoles.HOUSE, 1, "",
                List.of("minecraft:red_bed[facing=north,part=foot,occupied=false]", "minecraft:red_bed[facing=north,part=head,occupied=false]"),
                new int[][]{{0, 2, 0}, {0, 1, 1}});
        Citizen citizen = null;
        try {
            Building building = build(helper, kingdom, house, new BlockPos(6, 0, 6));
            helper.assertTrue(BuildingRoles.HOUSE.equals(building.role()) && building.level() == 1, "The house has no role: " + building.role());
            BuildingEffects.Effect effect = BuildingEffects.of(server, building).orElseThrow();
            helper.assertTrue(effect.works() && effect.beds() == 1, "The house does not work: " + effect.status() + ", beds " + effect.beds());
            helper.assertTrue(BuildingEffects.freeBeds(server, kingdom.id())[1] == 1, "The bed of the house is not offered to newcomers");
            helper.assertTrue(CitizenSimulation.growthCheck(server, kingdom.id()) != CitizenNeeds.GrowthCheck.NO_FREE_BED, "No room for newcomers");
            citizen = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
            CitizenSimulation.step(server, kingdom.id());
            WorldPos home = KingdomWorldApi.state(server).citizens().get(citizen.id()).orElseThrow().home();
            helper.assertTrue(home != null && building.box().contains(home.x(), home.y(), home.z()), "The citizen does not live in the house: " + home);
            int comfort = BuildingEffects.comfort(server, kingdom.id(), home);
            helper.assertTrue(comfort == (int) Math.round(effect.param(BuildingRoles.COMFORT)) && comfort > 0, "No comfort at home: " + comfort);
            helper.assertTrue(BuildingEffects.freeBeds(server, kingdom.id())[0] == 0, "The bed is still free");
        } finally {
            if (citizen != null) {
                CitizenGameTests.finish(helper, kingdom, citizen);
            }
            end(helper, kingdom, area[0], area[1], house.id());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_roles_warehouse")
    public static void theContainersOfAWarehouseAreStorehouses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Warehousers", UUID.randomUUID());
        BuildingTemplate warehouse = template("test_role_warehouse", BuildingRoles.WAREHOUSE, 1, "",
                List.of("minecraft:chest[facing=south,type=single,waterlogged=false]", "minecraft:barrel[facing=up,open=false]"),
                new int[][]{{0, 0, 0}, {2, 0, 1}});
        try {
            Building building = build(helper, kingdom, warehouse, new BlockPos(6, 0, 6));
            BuildingEffects.tick(server, 200);
            List<StorehouseInventory> stores = KingdomStorage.storehouses(server, kingdom.id(), null);
            helper.assertTrue(stores.size() == 2, "The containers of the warehouse are not storehouses: " + stores.size());
            ItemStack rest = KingdomStorage.insert(server, kingdom.id(), new ItemStack(Items.WHEAT, 40), null);
            helper.assertTrue(rest.isEmpty() && stored(server, kingdom, Items.WHEAT) == 40, "The warehouse took nothing");
            ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(5, 1, 4)));
            BlockPos barrel = helper.absolutePos(new BlockPos(7, 1, 4));
            int inChest = 0;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                inChest += chest.getItem(slot).getCount();
            }
            helper.assertTrue(inChest > 0 || level.getBlockEntity(barrel) != null, "The wheat is in no container");
            helper.assertTrue(KingdomStorage.take(server, kingdom.id(), stack -> stack.is(Items.WHEAT), 40, null).stream()
                    .mapToInt(ItemStack::getCount).sum() == 40, "The wheat could not be taken back");
            // Taken down far enough, the warehouse rests and its containers no longer count.
            BuildingWork.damage(server, current(server, building), 8);
            helper.assertTrue(ConstructionWork.checkNow(server, building.id()) < 70, "The damage did not count");
            helper.assertTrue(KingdomStorage.storehouses(server, kingdom.id(), null).isEmpty(), "A damaged warehouse still serves");
        } finally {
            end(helper, kingdom, area[0], area[1], warehouse.id());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_roles_repair", timeoutTicks = 400)
    public static void aRoleRestsWhenTheBuildingIsDamagedAndComesBackAfterARepair(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper);
        ConstructionSettings.override(FAST);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Repairers", UUID.randomUUID());
        BuildingTemplate workshop = template("test_role_workshop", BuildingRoles.WORKSHOP, 1, "",
                List.of("minecraft:oak_planks"), new int[][]{{0, 0, 0}, {1, 0, 0}, {2, 0, 0}, {0, 2, 0}, {2, 2, 0}});
        StorehouseInventory store = GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(0, 0, 4)));
        WorldPos post = GatheringGameTests.post(helper, new BlockPos(0, 0, 0), "builder");
        Citizen builder = null;
        try {
            Building building = build(helper, kingdom, workshop, new BlockPos(6, 0, 6));
            helper.assertTrue(BuildingEffects.of(server, building).orElseThrow().works(), "The workshop does not work");
            WorldPos inside = CitizenGameTests.at(helper, 6, 1, 5);
            helper.assertTrue(BuildingEffects.workshopSpeed(server, inside) > 1.0, "The workshop does not speed up its posts");
            int removed = BuildingWork.damage(server, building, 9);
            helper.assertTrue(removed == 9, "The damage removed " + removed);
            int integrity = ConstructionWork.checkNow(server, building.id());
            helper.assertTrue(integrity < 70, "The integrity is " + integrity);
            BuildingEffects.Effect damaged = BuildingEffects.of(server, current(server, building)).orElseThrow();
            helper.assertTrue(damaged.status() == BuildingRoles.Status.DAMAGED, "The role still works: " + damaged.status());
            helper.assertTrue(BuildingEffects.workshopSpeed(server, inside) == 1.0, "A damaged workshop still speeds up");

            // The repair costs exactly the blocks that went: five planks and four cobblestone.
            java.util.Map<String, Integer> cost = BuildingWork.repairCost(current(server, building));
            helper.assertTrue(cost.getOrDefault("minecraft:oak_planks", 0) == 5 && cost.getOrDefault("minecraft:cobblestone", 0) == 4,
                    "The repair costs " + cost);
            store.insertItem(0, new ItemStack(Items.OAK_PLANKS, 5), false);
            store.insertItem(1, new ItemStack(Items.COBBLESTONE, 4), false);
            KingdomWorldState.ConstructionChange repair = BuildingWork.orderRepair(server, null, kingdom.id(), building.id());
            helper.assertTrue(repair.outcome().ok(), "The repair was refused: " + repair.outcome());
            builder = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
            Citizen worker = builder;
            KingdomWorldApi.change(server, "test", state -> state.setProfession(null, worker.id(), "builder"));
            helper.assertTrue(KingdomWorldApi.state(server).citizens().assignWorkplace(worker, post), "The builder got no seat");
            GatheringSettings.override(GatheringGameTests.safeRules(0.0));
            try {
                for (int i = 0; i < 3; i++) {
                    new BuilderBehavior().workAbstractly(server, worker, 1_200);
                }
            } finally {
                GatheringSettings.override(null);
            }
            ConstructionWork.tick(server, 1);
            helper.assertTrue(KingdomWorldApi.state(server).construction().forBuilding(building.id()).isEmpty(), "The repair did not finish");
            helper.assertTrue(stored(server, kingdom, Items.OAK_PLANKS) == 0 && stored(server, kingdom, Items.COBBLESTONE) == 0,
                    "The repair did not take its materials");
            helper.assertTrue(ConstructionWork.checkNow(server, building.id()) == 100, "The building is not whole after the repair");
            helper.assertTrue(BuildingEffects.of(server, current(server, building)).orElseThrow().works(), "The role did not come back");
        } finally {
            if (builder != null) {
                CitizenGameTests.finish(helper, kingdom, builder);
            }
            end(helper, kingdom, area[0], area[1], workshop.id());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_roles_bunks")
    public static void theBunksOfAKingdomOfPlayersAreBedsLikeAnyOther(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Bunk Sleepers", UUID.randomUUID());
        String foot = "minecraft:red_bed[facing=north,part=foot,occupied=false]";
        String head = "minecraft:red_bed[facing=north,part=head,occupied=false]";
        BuildingTemplate house = template("test_role_bunk_house", BuildingRoles.HOUSE, 1, "", List.of(foot, head), new int[][]{{0, 2, 0}, {0, 1, 1}});
        BuildingTemplate barracks = template("test_role_bunk_barracks", BuildingRoles.BARRACKS, 1, "", List.of("kingdomworld:barracks", foot, head),
                new int[][]{{2, 0, 0}, {0, 2, 1}, {0, 1, 2}});
        Citizen citizen = null;
        try {
            Building home = build(helper, kingdom, house, new BlockPos(6, 0, 6));
            Building bunks = build(helper, kingdom, barracks, new BlockPos(2, 0, 9));
            BuildingEffects.changed();
            helper.assertTrue(BuildingEffects.of(server, bunks).orElseThrow().works(), "The barracks does not work: "
                    + BuildingEffects.of(server, bunks).orElseThrow().status());
            // The king decides who sleeps where: the bunk is a bed for newcomers like the bed of the house.
            helper.assertTrue(BuildingEffects.freeBeds(server, kingdom.id())[0] == 2, "The bunk of the barracks is not offered to newcomers: "
                    + BuildingEffects.freeBeds(server, kingdom.id())[0] + " free bed(s)");
            citizen = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
            CitizenSimulation.step(server, kingdom.id());
            Citizen record = KingdomWorldApi.state(server).citizens().get(citizen.id()).orElseThrow();
            WorldPos bed = record.home();
            helper.assertTrue(bed != null && home.box().contains(bed.x(), bed.y(), bed.z()), "The newcomer does not live in the house: " + bed);
            helper.assertTrue(BuildingEffects.freeBeds(server, kingdom.id())[0] == 1, "The free bunk is not counted for newcomers");
            helper.assertTrue(CitizenSimulation.growthCheck(server, kingdom.id()) != CitizenNeeds.GrowthCheck.NO_FREE_BED,
                    "The free bunk brings no newcomers");
            // A soldier the king recruits stays where it lives.
            helper.assertTrue(io.github.sekerator.kingdomworld.common.army.ArmyWork.recruit(server, null, kingdom.id(), citizen.id(), "warrior").ok(),
                    "The citizen was not recruited");
            WorldPos after = record.home();
            helper.assertTrue(after != null && home.box().contains(after.x(), after.y(), after.z()), "The recruit moved out of its house: " + after);
        } finally {
            if (citizen != null) {
                CitizenGameTests.finish(helper, kingdom, citizen);
            }
            end(helper, kingdom, area[0], area[1], house.id(), barracks.id());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_roles_army_magic")
    public static void theRolesOfTheArmyAndTheMagesNeedTheirBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Garrison Builders", UUID.randomUUID());
        BuildingTemplate barracks = template("test_role_barracks", BuildingRoles.BARRACKS, 1, "", List.of("kingdomworld:barracks"),
                new int[][]{{1, 1, 0}});
        BuildingTemplate empty = template("test_role_empty_barracks", BuildingRoles.BARRACKS, 1, "", List.of("minecraft:oak_planks"),
                new int[][]{{1, 1, 0}});
        BuildingTemplate tower = template("test_role_mage_tower", BuildingRoles.MAGE_TOWER, 1, "", List.of("kingdomworld:magic_focus"),
                new int[][]{{1, 1, 0}});
        try {
            Building withBlock = build(helper, kingdom, barracks, new BlockPos(2, 0, 9));
            Building without = build(helper, kingdom, empty, new BlockPos(6, 0, 9));
            Building mages = build(helper, kingdom, tower, new BlockPos(10, 0, 9));
            BuildingEffects.changed();
            helper.assertTrue(BuildingEffects.of(server, withBlock).orElseThrow().works(), "The barracks with a barracks block does not work: "
                    + BuildingEffects.of(server, withBlock).orElseThrow().status());
            helper.assertTrue(BuildingEffects.of(server, without).orElseThrow().status() == BuildingRoles.Status.LACKING,
                    "A barracks without the block of the army plays its role");
            helper.assertTrue(BuildingEffects.bunks(server, kingdom.id()) > 0, "The barracks gives no more room for training");
            helper.assertTrue(BuildingEffects.of(server, mages).orElseThrow().works(), "The mage tower with a focus does not work: "
                    + BuildingEffects.of(server, mages).orElseThrow().status());
            WorldPos post = CitizenGameTests.at(helper, 10, 1, 8);
            helper.assertTrue(BuildingEffects.mageSpeed(server, post) > 1.0, "Mages inside the tower are not faster");
            helper.assertTrue(BuildingEffects.mageSpeed(server, CitizenGameTests.at(helper, 0, 1, 0)) == 1.0, "Mages outside the tower are faster");
        } finally {
            end(helper, kingdom, area[0], area[1], barracks.id(), empty.id(), tower.id());
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- upgrades and demolitions

    @GameTest(template = EMPTY, batch = "kingdomworld_roles_upgrade")
    public static void anUpgradeChangesOnlyTheDifference(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Upgraders", UUID.randomUUID());
        String chest = "minecraft:chest[facing=south,type=single,waterlogged=false]";
        BuildingTemplate second = template("test_upgrade_2", BuildingRoles.HOUSE, 2, "",
                List.of(chest, "minecraft:stone_bricks", "minecraft:red_bed[facing=north,part=foot,occupied=false]",
                        "minecraft:red_bed[facing=north,part=head,occupied=false]"),
                new int[][]{{0, 0, 0}, {2, 0, 1}, {0, 2, 2}, {0, 1, 3}});
        BuildingTemplate first = template("test_upgrade_1", BuildingRoles.HOUSE, 1, second.id().toString(),
                List.of(chest, "minecraft:oak_planks", "minecraft:red_bed[facing=north,part=foot,occupied=false]",
                        "minecraft:red_bed[facing=north,part=head,occupied=false]"),
                new int[][]{{0, 0, 0}, {2, 0, 1}, {0, 2, 2}, {0, 1, 3}});
        try {
            Building building = build(helper, kingdom, first, new BlockPos(6, 0, 6));
            ChestBlockEntity box = (ChestBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(5, 1, 4)));
            box.setItem(0, new ItemStack(Items.DIAMOND, 3));
            StorehouseInventory store = GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(0, 0, 4)));
            store.insertItem(0, new ItemStack(Items.STONE_BRICKS, 1), false);
            var diff = BuildingWork.upgradeDiff(building).orElseThrow();
            helper.assertTrue(diff.cost().equals(java.util.Map.of("minecraft:stone_bricks", 1)), "The upgrade costs " + diff.cost());
            KingdomWorldState.ConstructionChange change = BuildingWork.orderUpgrade(server, null, kingdom.id(), building.id());
            helper.assertTrue(change.outcome().ok(), "The upgrade was refused: " + change.outcome());
            Construction upgrade = change.construction().orElseThrow();
            helper.assertTrue(ConstructionWork.plan(upgrade).orElseThrow().places() == 1, "The upgrade places more than the difference");
            helper.assertTrue(ConstructionWork.pay(server, upgrade, java.util.Map.of("minecraft:stone_bricks", 1)), "The difference could not be paid");
            ConstructionWork.finishNow(server, upgrade);
            helper.assertTrue(at(helper, 7, 1, 4).is(Blocks.STONE_BRICKS), "The planks did not become stone bricks");
            helper.assertTrue(stored(server, kingdom, Items.OAK_PLANKS) == 1, "The planks taken out did not go to the storehouse");
            ChestBlockEntity after = (ChestBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(5, 1, 4)));
            helper.assertTrue(after != null && after.getItem(0).is(Items.DIAMOND) && after.getItem(0).getCount() == 3,
                    "The chest that stays the same was touched");
            Building upgraded = current(server, building);
            helper.assertTrue(upgraded.level() == 2 && upgraded.templateId().equals(second.id().toString()) && upgraded.id() == building.id(),
                    "The register did not learn of the upgrade: " + upgraded.templateId() + " level " + upgraded.level());
        } finally {
            end(helper, kingdom, area[0], area[1], first.id(), second.id());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_roles_demolish")
    public static void aDemolitionGivesBackMaterialsWithALossAndKeepsWhatContainersHold(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper);
        ConstructionSettings.overrideBuildings(new BuildingRules(70, 80, 25, 16));
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Wreckers", UUID.randomUUID());
        BuildingTemplate shed = template("test_demolish", "", 1, "",
                List.of("minecraft:chest[facing=south,type=single,waterlogged=false]", "minecraft:oak_planks", "minecraft:torch"),
                new int[][]{{0, 0, 0}, {2, 1, 1}, {1, 2, 2}});
        try {
            StorehouseInventory store = GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(0, 0, 4)));
            Building building = build(helper, kingdom, shed, new BlockPos(6, 0, 6));
            ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(5, 1, 4)));
            chest.setItem(3, new ItemStack(Items.GOLD_INGOT, 17));
            // What the storehouses held before: neighbouring tests may leave things on this land.
            long gold = stored(server, kingdom, Items.GOLD_INGOT);
            long cobblestone = stored(server, kingdom, Items.COBBLESTONE);
            long chests = stored(server, kingdom, Items.CHEST);
            long torches = stored(server, kingdom, Items.TORCH);
            long planks = stored(server, kingdom, Items.OAK_PLANKS);
            KingdomWorldState.ConstructionChange change = BuildingWork.orderDemolition(server, null, kingdom.id(), building.id());
            helper.assertTrue(change.outcome().ok(), "The demolition was refused: " + change.outcome());
            helper.assertTrue(BuildingEffects.of(server, building).map(effect -> effect.status()).orElse(null) != BuildingRoles.Status.ACTIVE,
                    "The building plays its role while it is taken down");
            ConstructionWork.finishNow(server, change.construction().orElseThrow());
            helper.assertTrue(at(helper, 6, 0, 5).isAir() && at(helper, 7, 1, 5).isAir() && at(helper, 5, 1, 4).isAir(), "Blocks stayed");
            helper.assertTrue(KingdomWorldApi.state(server).construction().building(building.id()).isEmpty(), "The building stayed in the register");
            helper.assertTrue(stored(server, kingdom, Items.GOLD_INGOT) - gold == 17, "The gold in the chest was lost: "
                    + (stored(server, kingdom, Items.GOLD_INGOT) - gold));
            // Nine cobblestone less a quarter, rounded down; one chest, one plank and one torch each round down to nothing.
            helper.assertTrue(stored(server, kingdom, Items.COBBLESTONE) - cobblestone == 6, "Cobblestone came back: "
                    + (stored(server, kingdom, Items.COBBLESTONE) - cobblestone));
            long moreChests = stored(server, kingdom, Items.CHEST) - chests;
            long moreTorches = stored(server, kingdom, Items.TORCH) - torches;
            long morePlanks = stored(server, kingdom, Items.OAK_PLANKS) - planks;
            helper.assertTrue(moreChests == 0 && moreTorches == 0 && morePlanks == 0, "More came back than the loss allows: chests " + moreChests
                    + ", torches " + moreTorches + ", planks " + morePlanks + "; taken down " + change.construction().orElseThrow().removed()
                    + "; storehouses " + KingdomStorage.countsById(server, kingdom.id()));
            helper.assertTrue(store.getSlots() > 0, "No storehouse");
        } finally {
            end(helper, kingdom, area[0], area[1], shed.id());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_roles_cancel_demolition")
    public static void aDemolitionCancelledHalfWayLeavesTheBuildingDamaged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Half Wreckers", UUID.randomUUID());
        BuildingTemplate shed = template("test_half_demolish", BuildingRoles.WELL, 1, "", List.of("minecraft:oak_planks"),
                new int[][]{{0, 0, 0}, {1, 0, 0}, {2, 0, 0}});
        try {
            GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(0, 0, 4)));
            Building building = build(helper, kingdom, shed, new BlockPos(6, 0, 6));
            Construction demolition = BuildingWork.orderDemolition(server, null, kingdom.id(), building.id()).construction().orElseThrow();
            var plan = ConstructionWork.plan(demolition).orElseThrow();
            for (int step = 0; step < 6; step++) {
                ConstructionWork.doStep(level, demolition, plan, ConstructionWork.template(demolition).orElseThrow(), step, null, ItemStack.EMPTY,
                        new java.util.ArrayList<>(), false);
            }
            ConstructionWork.cancel(server, null, kingdom.id(), demolition.id());
            helper.assertTrue(KingdomWorldApi.state(server).construction().building(building.id()).isPresent(), "The building left the register");
            helper.assertTrue(ConstructionWork.checkNow(server, building.id()) < 100, "The building is whole after half a demolition");
        } finally {
            end(helper, kingdom, area[0], area[1], shed.id());
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- blueprints of the kingdom

    @GameTest(template = EMPTY, batch = "kingdomworld_roles_capture")
    public static void aSavedBlueprintIsCheckedAndBuilt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Draftsmen", UUID.randomUUID());
        try {
            // A hut stands in the world: a floor, a bed, a wall of planks.
            for (int x = 4; x <= 6; x++) {
                for (int z = 4; z <= 6; z++) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.COBBLESTONE);
                }
            }
            helper.setBlock(new BlockPos(4, 1, 4), Blocks.OAK_PLANKS);
            helper.setBlock(new BlockPos(6, 1, 4), Blocks.OAK_PLANKS);
            BlockPos a = helper.absolutePos(new BlockPos(4, 0, 4));
            BlockPos b = helper.absolutePos(new BlockPos(6, 2, 6));
            KingdomActions.Feedback noBed = BlueprintCapture.capture(level, null, kingdom.id(), a, b, "Hut", "housing", BuildingRoles.HOUSE, Facing.SOUTH);
            helper.assertTrue(!noBed.success(), "A house without a bed was saved");
            helper.setBlock(new BlockPos(5, 1, 6), Blocks.BEDROCK);
            KingdomActions.Feedback bedrock = BlueprintCapture.capture(level, null, kingdom.id(), a, b, "Hut", "housing", "", Facing.SOUTH);
            helper.assertTrue(!bedrock.success(), "A blueprint with bedrock was saved");
            helper.setBlock(new BlockPos(5, 1, 6), Blocks.AIR);
            helper.setBlock(new BlockPos(5, 1, 6), Blocks.RED_BED.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.NORTH));
            helper.setBlock(new BlockPos(5, 1, 5), Blocks.RED_BED.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.NORTH)
                    .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
            KingdomActions.Feedback saved = BlueprintCapture.capture(level, null, kingdom.id(), a, b, "Hut", "housing", BuildingRoles.HOUSE, Facing.SOUTH);
            helper.assertTrue(saved.success(), "The hut was not saved: " + saved.message().getString());
            CustomBlueprint blueprint = KingdomWorldApi.state(server).construction().blueprintsOf(kingdom.id()).get(0);
            BuildingTemplate template = CustomTemplates.get(blueprint.templateId()).orElseThrow();
            helper.assertTrue(template.available() && kingdom.id().equals(template.owner()) && BuildingRoles.HOUSE.equals(template.role()),
                    "The saved blueprint is no template of the kingdom: " + template.problem());
            helper.assertTrue(template.items().getOrDefault("minecraft:red_bed", 0) == 1 && template.items().getOrDefault("minecraft:cobblestone", 0) == 9,
                    "The blueprint costs " + template.items());
            // The saved blueprint survives saving the data.
            KingdomSavedData loaded = KingdomSavedData.load(KingdomSavedData.get(server).save(new CompoundTag()));
            helper.assertTrue(!loaded.isReadOnly() && loaded.state().construction().blueprint(blueprint.id()).map(found -> found.equals(blueprint))
                    .orElse(false), "The blueprint changed in saving");
            // It is built like any template, two blocks further.
            GatheringGameTests.clear(level, helper.absolutePos(new BlockPos(3, 0, 3)), helper.absolutePos(new BlockPos(7, 3, 7)));
            Building built = build(helper, kingdom, template, new BlockPos(9, 0, 9));
            helper.assertTrue(BuildingRoles.HOUSE.equals(built.role()), "The house of the blueprint has no role");
            helper.assertTrue(BuildingEffects.of(server, built).orElseThrow().beds() == 1, "The bed of the blueprint is not a home");
        } finally {
            end(helper, kingdom, area[0], area[1]);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_roles_wood")
    public static void aTemplateIsBuiltOfSpruce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Spruce Builders", UUID.randomUUID());
        BuildingTemplate hut = template("test_spruce", "", 1, "", List.of("minecraft:oak_planks",
                        "minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]", "minecraft:oak_door[facing=north,half=lower,hinge=left,open=false,powered=false]",
                        "minecraft:oak_door[facing=north,half=upper,hinge=left,open=false,powered=false]", "minecraft:crafting_table"),
                new int[][]{{0, 0, 0}, {2, 0, 1}, {1, 2, 2}, {2, 2, 4}});
        try {
            Construction construction = lay(helper, kingdom, hut, new BlockPos(6, 0, 6), new Materials("minecraft:spruce", ""));
            java.util.Map<String, Integer> cost = ConstructionWork.plan(construction).orElseThrow().remaining(construction);
            helper.assertTrue(cost.containsKey("minecraft:spruce_planks") && cost.containsKey("minecraft:spruce_stairs")
                    && cost.containsKey("minecraft:spruce_door") && !cost.containsKey("minecraft:oak_planks"), "The cost is not of spruce: " + cost);
            Building building = ConstructionWork.finishNow(server, construction).orElseThrow();
            helper.assertTrue(at(helper, 5, 1, 4).is(Blocks.SPRUCE_PLANKS), "The planks are not spruce");
            BlockState stairs = at(helper, 7, 1, 4);
            helper.assertTrue(stairs.is(Blocks.SPRUCE_STAIRS) && stairs.getValue(net.minecraft.world.level.block.StairBlock.FACING)
                    == net.minecraft.core.Direction.EAST, "The stairs are not spruce or lost their facing: " + stairs);
            helper.assertTrue(at(helper, 7, 1, 6).is(Blocks.CRAFTING_TABLE), "What is not wood changed");
            helper.assertTrue("minecraft:spruce".equals(building.materials().wood()), "The register does not know the wood");
            helper.assertTrue(ConstructionWork.checkNow(server, building.id()) == 100, "A spruce building is not whole by its own blocks");
        } finally {
            end(helper, kingdom, area[0], area[1], hut.id());
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- rights and land

    @GameTest(template = EMPTY, batch = "kingdomworld_roles_rights")
    public static void theServerChecksRightsAndLandForBuildings(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Overseers", UUID.randomUUID());
        BuildingTemplate hut = template("test_rights", BuildingRoles.WALL, 1, "", List.of("minecraft:oak_planks"), new int[][]{{1, 1, 0}});
        UUID subject = UUID.randomUUID();
        KingdomWorldState state = KingdomWorldApi.state(server);
        state.membership().invite(kingdom.rulerId(), subject, 1L);
        state.membership().acceptInvitation(subject, kingdom.id(), 1L);
        try {
            Building building = build(helper, kingdom, hut, new BlockPos(6, 0, 6));
            for (BuildingPackets.Change.Action action : BuildingPackets.Change.Action.values()) {
                KingdomActions.Feedback feedback = ConstructionActions.applyBuilding(server, subject, kingdom.id(), action, building.id());
                helper.assertTrue(!feedback.success(), "A subject could " + action);
            }
            helper.assertTrue(state.construction().forBuilding(building.id()).isEmpty() && !current(server, building).autoRepair(),
                    "A subject changed the building");
            helper.assertTrue(BlueprintCapture.capture(level, subject, kingdom.id(), helper.absolutePos(new BlockPos(5, 0, 4)),
                    helper.absolutePos(new BlockPos(7, 1, 6)), "Copy", "housing", "", Facing.SOUTH).message().getString().length() > 0
                    && state.construction().blueprintsOf(kingdom.id()).isEmpty(), "A subject saved a blueprint");
            helper.assertTrue(state.membership().setRole(kingdom.rulerId(), subject, Role.STEWARD).ok(), "No steward");
            KingdomActions.Feedback auto = ConstructionActions.applyBuilding(server, subject, kingdom.id(), BuildingPackets.Change.Action.AUTO_REPAIR_ON,
                    building.id());
            helper.assertTrue(auto.success() && current(server, building).autoRepair(), "The steward could not switch the repairs");

            // The land under the building is lost: a demolition takes nothing down.
            KingdomWorldState.ConstructionChange demolition = BuildingWork.orderDemolition(server, null, kingdom.id(), building.id());
            helper.assertTrue(demolition.outcome().ok(), "The demolition was refused: " + demolition.outcome());
            var capital = state.territory().territory(kingdom.id()).orElseThrow().capital();
            java.util.Set<Long> land = state.territory().remove(kingdom.id());
            try {
                Construction lost = demolition.construction().orElseThrow();
                ConstructionWork.finishNow(server, lost);
                helper.assertTrue(at(helper, 6, 1, 5).is(Blocks.OAK_PLANKS) && at(helper, 6, 0, 5).is(Blocks.COBBLESTONE),
                        "A demolition broke blocks on land the kingdom lost");
                helper.assertTrue(state.construction().building(building.id()).isPresent(), "The building left the register off our land");
            } finally {
                state.territory().establish(kingdom.id(), capital, land);
            }
        } finally {
            end(helper, kingdom, area[0], area[1], hut.id());
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- data

    @GameTest(template = EMPTY, batch = "kingdomworld_roles_format")
    public static void formatEightIsUpgradedAndFormatNineKeepsEverything(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Archivists", UUID.randomUUID());
        BuildingTemplate hut = template("test_format", BuildingRoles.HOUSE, 1, "", List.of("minecraft:oak_planks"), new int[][]{{1, 1, 0}});
        try {
            Building building = build(helper, kingdom, hut, new BlockPos(6, 0, 6));
            KingdomWorldApi.change(server, "test", state -> state.setAutoRepair(null, kingdom.id(), building.id(), true));
            BuildingWork.damage(server, current(server, building), 2);
            ConstructionWork.checkNow(server, building.id());
            Construction repair = BuildingWork.orderRepair(server, null, kingdom.id(), building.id()).construction().orElseThrow();
            CompoundTag saved = KingdomSavedData.get(server).save(new CompoundTag());
            KingdomSavedData loaded = KingdomSavedData.load(saved.copy());
            helper.assertTrue(!loaded.isReadOnly(), "Format 9 became read-only: " + loaded.readOnlyReason());
            Building back = loaded.state().construction().building(building.id()).orElseThrow();
            helper.assertTrue(back.equals(current(server, building)), "The building changed in saving");
            Construction repairBack = loaded.state().construction().get(repair.id()).orElseThrow();
            helper.assertTrue(repairBack.kind() == Construction.Kind.REPAIR && repairBack.building() == building.id()
                    && repairBack.skip().equals(repair.skip()), "The repair changed in saving");

            // The same world as format 8 wrote it: no roles, kinds or blueprints.
            CompoundTag old = saved.copy();
            old.putInt("formatVersion", 8);
            CompoundTag construction = old.getCompound("construction");
            construction.remove("blueprints");
            construction.remove("nextBlueprint");
            ListTag buildings = construction.getList("buildings", 10);
            for (int i = 0; i < buildings.size(); i++) {
                for (String key : List.of("role", "level", "wood", "stone", "autoRepair", "damaged")) {
                    buildings.getCompound(i).remove(key);
                }
            }
            ListTag constructions = construction.getList("constructions", 10);
            for (int i = 0; i < constructions.size(); i++) {
                for (String key : List.of("kind", "building", "wood", "stone", "skip", "removed")) {
                    constructions.getCompound(i).remove(key);
                }
            }
            KingdomSavedData upgraded = KingdomSavedData.load(old);
            helper.assertTrue(!upgraded.isReadOnly(), "Format 8 became read-only: " + upgraded.readOnlyReason());
            Building plain = upgraded.state().construction().building(building.id()).orElseThrow();
            helper.assertTrue(BuildingRoles.HOUSE.equals(plain.role()) && plain.level() == 1 && plain.materials().none(),
                    "The building of format 8 got no role from its type: " + plain.role());
            helper.assertTrue(upgraded.state().construction().get(repair.id()).orElseThrow().kind() == Construction.Kind.BUILD,
                    "A construction of format 8 is not a new building");
            helper.assertTrue(upgraded.save(new CompoundTag()).getInt("formatVersion") == KingdomSavedData.FORMAT_VERSION, "Saved in the old format");
        } finally {
            end(helper, kingdom, area[0], area[1], hut.id());
        }
        helper.succeed();
    }
}
