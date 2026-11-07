package io.github.sekerator.kingdomworld.common.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.work.GatheringSettings;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.construction.BuilderBehavior;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplate;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplates;
import io.github.sekerator.kingdomworld.common.construction.ConstructionActions;
import io.github.sekerator.kingdomworld.common.construction.ConstructionSettings;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.common.network.data.ConstructionView;
import io.github.sekerator.kingdomworld.common.network.packet.ConstructionPackets;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.BuildPlan;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.ConstructionRules;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.production.Order;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Game tests of construction: a builder near players builds a small template from the storehouse; clearing sends drops to
 * the storehouse and stops at a container with items; what is missing is shown and ordered; cancelling gives the materials
 * back without duplicates; blocks of the mod in a template register like blocks a player placed; the server refuses places
 * off the kingdom's land, overlapping places and players without the right; a template with a block of a missing mod
 * cannot be built; work far from players places its blocks once the place is loaded; constructions survive saving and the
 * upgrade from format 7.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConstructionGameTests {
    private static final String EMPTY = "empty";
    /** Fast rules for tests: a block in two ticks, no falls. */
    private static final ConstructionRules FAST = new ConstructionRules(4, 64, 4.5, 12.0, 400, 2, 0.0, 48, 32, 48);

    private ConstructionGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    private static ListTag ints(int... values) {
        ListTag list = new ListTag();
        for (int value : values) {
            list.add(IntTag.valueOf(value));
        }
        return list;
    }

    /**
     * A structure tag as the structure block writes it: blocks by their palette state, one per position given.
     *
     * @param blocks rows of {x, y, z, palette index}
     */
    static CompoundTag structure(int sizeX, int sizeY, int sizeZ, List<String> palette, int[][] blocks) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", 3465);
        tag.put("size", ints(sizeX, sizeY, sizeZ));
        ListTag paletteTag = new ListTag();
        for (String name : palette) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Name", name);
            paletteTag.add(entry);
        }
        tag.put("palette", paletteTag);
        ListTag list = new ListTag();
        for (int[] block : blocks) {
            CompoundTag entry = new CompoundTag();
            entry.put("pos", ints(block[0], block[1], block[2]));
            entry.putInt("state", block[3]);
            list.add(entry);
        }
        tag.put("blocks", list);
        tag.put("entities", new ListTag());
        return tag;
    }

    private static JsonObject description(String name) {
        return JsonParser.parseString("{\"name\": \"kingdomworld.building.small_house\", \"fallback_name\": \"" + name
                + "\", \"category\": \"housing\", \"type\": \"test\", \"icon\": \"minecraft:cobblestone\", \"anchor\": [1, 0, 2], "
                + "\"entrance\": \"south\"}").getAsJsonObject();
    }

    /**
     * A tiny house: a floor of cobblestone, two pillars of planks, a torch on the floor and air above.
     */
    static BuildingTemplate hut(String name) {
        List<String> palette = List.of("minecraft:cobblestone", "minecraft:oak_planks", "minecraft:torch", "minecraft:air");
        int[][] blocks = new int[18][];
        int i = 0;
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                blocks[i++] = new int[]{x, 0, z, 0};
            }
        }
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                int state = (x == 0 || x == 2) && z == 0 ? 1 : x == 1 && z == 1 ? 2 : 3;
                blocks[i++] = new int[]{x, 1, z, state};
            }
        }
        return BuildingTemplates.addForTest(KingdomWorld.id("test_" + name), description(name), structure(3, 2, 3, palette, blocks));
    }

    private static long stored(MinecraftServer server, Kingdom kingdom, Item item) {
        return KingdomStorage.count(server, kingdom.id(), stack -> stack.is(item));
    }

    private static void fill(StorehouseInventory store, ItemStack... stacks) {
        int slot = 0;
        for (ItemStack stack : stacks) {
            while (!store.getStackInSlot(slot).isEmpty()) {
                slot++;
            }
            store.insertItem(slot, stack, false);
        }
    }

    private static Construction lay(GameTestHelper helper, Kingdom kingdom, BuildingTemplate template, BlockPos relative) {
        KingdomWorldState.ConstructionChange change = ConstructionWork.lay(helper.getLevel(), null, kingdom.id(), template,
                helper.absolutePos(relative), Placement.NONE);
        if (change.construction().isEmpty()) {
            throw new IllegalStateException("The construction was refused: " + change.outcome());
        }
        return change.construction().get();
    }

    /**
     * A builder without a body at a builder's post.
     */
    private static Citizen farBuilder(GameTestHelper helper, Kingdom kingdom, WorldPos post) {
        MinecraftServer server = helper.getLevel().getServer();
        Citizen citizen = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
        KingdomWorldApi.change(server, "test", state -> state.setProfession(null, citizen.id(), "builder"));
        helper.assertTrue(KingdomWorldApi.state(server).citizens().assignWorkplace(citizen, post), "The builder got no seat at the post");
        return citizen;
    }

    /**
     * Steps of work far from players for a builder, then a second of construction: what was paid goes into the world.
     */
    private static void workFar(MinecraftServer server, Citizen builder, int steps) {
        GatheringSettings.override(GatheringGameTests.safeRules(0.0));
        try {
            for (int i = 0; i < steps; i++) {
                new BuilderBehavior().workAbstractly(server, builder, 1_200);
            }
        } finally {
            GatheringSettings.override(null);
        }
    }

    private static BlockState at(GameTestHelper helper, int x, int y, int z) {
        return helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, y, z)));
    }

    private static void end(GameTestHelper helper, Kingdom kingdom, BlockPos from, BlockPos to, ResourceLocation template, Citizen... citizens) {
        ConstructionSettings.override(null);
        CitizenGameTests.finish(helper, kingdom, citizens);
        GatheringGameTests.clear(helper.getLevel(), from, to);
        BuildingTemplates.removeForTest(template);
    }

    // ---------------------------------------------------------------- near players

    @GameTest(template = EMPTY, batch = "kingdomworld_build_near", timeoutTicks = 2400)
    public static void aBuilderBuildsASmallTemplateFromTheStorehouse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        level.setDayTime(1_000L);
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        GatheringGameTests.clear(level, from, to);
        ConstructionSettings.override(FAST);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Masons", UUID.randomUUID());
        BuildingTemplate template = hut("near");
        StorehouseInventory store = GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(0, 0, 4)));
        fill(store, new ItemStack(Items.COBBLESTONE, 12), new ItemStack(Items.OAK_PLANKS, 5), new ItemStack(Items.TORCH, 3));
        WorldPos post = GatheringGameTests.post(helper, new BlockPos(0, 0, 0), "builder");
        Citizen builder = GatheringGameTests.worker(helper, kingdom, "builder", post, helper.absolutePos(new BlockPos(1, 0, 1)));
        Construction construction = lay(helper, kingdom, template, new BlockPos(6, 0, 6));

        helper.succeedWhen(() -> {
            List<Building> buildings = KingdomWorldApi.state(server).construction().buildingsOf(kingdom.id());
            helper.assertTrue(!buildings.isEmpty(), "Not built yet: status " + construction.status() + ", done " + construction.doneCount() + "/"
                    + construction.steps() + ", worker " + builder.workStatus() + ", site " + construction.delivered());
            helper.assertTrue(at(helper, 5, 0, 4).is(Blocks.COBBLESTONE) && at(helper, 7, 0, 6).is(Blocks.COBBLESTONE), "The floor is missing");
            helper.assertTrue(at(helper, 5, 1, 4).is(Blocks.OAK_PLANKS) && at(helper, 7, 1, 4).is(Blocks.OAK_PLANKS), "The pillars are missing");
            helper.assertTrue(at(helper, 6, 1, 5).is(Blocks.TORCH), "The torch is missing");
            helper.assertTrue(stored(server, kingdom, Items.COBBLESTONE) == 3 && stored(server, kingdom, Items.OAK_PLANKS) == 3
                    && stored(server, kingdom, Items.TORCH) == 2, "The materials do not add up: cobblestone " + stored(server, kingdom, Items.COBBLESTONE)
                    + ", planks " + stored(server, kingdom, Items.OAK_PLANKS) + ", torches " + stored(server, kingdom, Items.TORCH));
            helper.assertTrue(buildings.get(0).integrity() == 100 && !buildings.get(0).admin(), "The register entry is wrong");
            end(helper, kingdom, from, to, template.id(), builder);
        });
    }

    // ---------------------------------------------------------------- clearing

    @GameTest(template = EMPTY, batch = "kingdomworld_build_clear", timeoutTicks = 400)
    public static void clearingSendsDropsToTheStorehouseAndStopsAtAFullContainer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        GatheringGameTests.clear(level, from, to);
        ConstructionSettings.override(FAST);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Clearers", UUID.randomUUID());
        BuildingTemplate template = hut("clear");
        StorehouseInventory store = GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(0, 0, 4)));
        fill(store, new ItemStack(Items.COBBLESTONE, 9), new ItemStack(Items.OAK_PLANKS, 2), new ItemStack(Items.TORCH, 1));
        // A log stands where the template wants air, a chest with an apple where it wants a pillar.
        helper.setBlock(new BlockPos(6, 1, 6), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(5, 1, 4), Blocks.CHEST);
        Container chest = (Container) level.getBlockEntity(helper.absolutePos(new BlockPos(5, 1, 4)));
        chest.setItem(0, new ItemStack(Items.APPLE));
        WorldPos post = GatheringGameTests.post(helper, new BlockPos(0, 0, 0), "builder");
        Citizen builder = farBuilder(helper, kingdom, post);
        Construction construction = lay(helper, kingdom, template, new BlockPos(6, 0, 6));
        try {
            BuildPlan plan = ConstructionWork.plan(construction).orElseThrow();
            helper.assertTrue(plan.clears() == 2, "Two blocks are in the way, the plan clears " + plan.clears());
            workFar(server, builder, 3);
            ConstructionWork.tick(server, 1);
            helper.assertTrue(stored(server, kingdom, Items.OAK_LOG) == 1, "The log did not go to the storehouse");
            helper.assertTrue(at(helper, 6, 1, 6).isAir(), "The log is still there");
            helper.assertTrue(at(helper, 5, 1, 4).is(Blocks.CHEST) && chest.getItem(0).is(Items.APPLE), "The chest with the apple was broken");
            helper.assertTrue(construction.status() == Construction.Status.BLOCKED, "The construction does not wait: " + construction.status());
            helper.assertTrue(construction.statusPos() != null && construction.statusPos().equals(CitizenGameTests.at(helper, 5, 1, 4)),
                    "The construction does not say where the chest is: " + construction.statusPos());
            helper.assertTrue(at(helper, 7, 1, 4).isAir(), "Blocks were placed before the clearing was done");
            // Emptied, the chest goes as well (as an item to the storehouse) and the building goes on.
            chest.setItem(0, ItemStack.EMPTY);
            ConstructionWork.tick(server, 1);
            helper.assertTrue(stored(server, kingdom, Items.CHEST) == 1, "The empty chest did not go to the storehouse");
            helper.assertTrue(at(helper, 5, 1, 4).is(Blocks.OAK_PLANKS), "The pillar was not built where the chest stood");
        } finally {
            end(helper, kingdom, from, to, template.id(), builder);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- materials

    @GameTest(template = EMPTY, batch = "kingdomworld_build_missing")
    public static void whatIsMissingIsShownAndOrdered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Planners", UUID.randomUUID());
        BuildingTemplate template = hut("missing");
        GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(0, 0, 4))).insertItem(0, new ItemStack(Items.COBBLESTONE, 9), false);
        Construction construction = lay(helper, kingdom, template, new BlockPos(6, 0, 6));
        try {
            ConstructionView view = ConstructionActions.view(server, kingdom.id(), kingdom.rulerId()).orElseThrow();
            ConstructionView.Site site = view.sites().get(0);
            Optional<ConstructionView.Missing> planks = site.missing().stream().filter(m -> m.item().is(Items.OAK_PLANKS)).findFirst();
            helper.assertTrue(planks.isPresent() && planks.get().count() == 2 && planks.get().orderable() && !planks.get().ordered(),
                    "The missing planks are not shown right: " + site.missing());
            helper.assertTrue(site.missing().stream().noneMatch(m -> m.item().is(Items.COBBLESTONE)), "Cobblestone is shown missing");
            var result = ConstructionActions.apply(server, kingdom.rulerId(), new ConstructionPackets.Change(kingdom.id(),
                    ConstructionPackets.Change.Action.ORDER_MISSING, construction.id(), 0));
            helper.assertTrue(result.success(), "Ordering was refused: " + result.message().getString());
            List<Order> orders = KingdomWorldApi.state(server).orders().ordersOf(kingdom.id());
            helper.assertTrue(orders.stream().anyMatch(order -> order.item().equals("minecraft:oak_planks") && order.kind() == Order.Kind.MAKE
                    && order.amount() == 2), "No order for the planks: " + orders);
            ConstructionView again = ConstructionActions.view(server, kingdom.id(), kingdom.rulerId()).orElseThrow();
            helper.assertTrue(again.sites().get(0).missing().stream().anyMatch(m -> m.item().is(Items.OAK_PLANKS) && m.ordered()),
                    "The view does not say the planks are ordered");
            int before = orders.size();
            ConstructionWork.tick(server, 0);
            helper.assertTrue(KingdomWorldApi.state(server).orders().ordersOf(kingdom.id()).size() == before, "The planks were ordered twice");
        } finally {
            end(helper, kingdom, from, to, template.id());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_build_cancel")
    public static void cancellingGivesTheMaterialsBackWithoutDuplicates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Quitters", UUID.randomUUID());
        BuildingTemplate template = hut("cancel");
        StorehouseInventory store = GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(0, 0, 4)));
        fill(store, new ItemStack(Items.COBBLESTONE, 20), new ItemStack(Items.OAK_PLANKS, 20), new ItemStack(Items.TORCH, 20));
        WorldPos post = GatheringGameTests.post(helper, new BlockPos(0, 0, 0), "builder");
        Citizen builder = farBuilder(helper, kingdom, post);
        Construction construction = lay(helper, kingdom, template, new BlockPos(6, 0, 6));
        try {
            // Some blocks are placed, some paid and waiting, some brought to the site.
            ConstructionSettings.override(new ConstructionRules(4, 64, 4.5, 12.0, 400, 400, 0.0, 48, 32, 48));
            workFar(server, builder, 1);
            ConstructionWork.applyPending(level, construction, ConstructionWork.plan(construction).orElseThrow(), 2);
            workFar(server, builder, 1);
            ConstructionWork.fetch(server, construction, "minecraft:torch", 1);
            long placedCobble = 0;
            for (int x = 5; x <= 7; x++) {
                for (int z = 4; z <= 6; z++) {
                    if (at(helper, x, 0, z).is(Blocks.COBBLESTONE)) {
                        placedCobble++;
                    }
                }
            }
            helper.assertTrue(placedCobble > 0 && !construction.pending().isEmpty() && !construction.delivered().isEmpty(),
                    "The test did not get the construction half way: placed " + placedCobble + ", pending " + construction.pending().size());
            var result = ConstructionActions.apply(server, kingdom.rulerId(), new ConstructionPackets.Change(kingdom.id(),
                    ConstructionPackets.Change.Action.CANCEL, construction.id(), 0));
            helper.assertTrue(result.success(), "Cancelling was refused");
            helper.assertTrue(stored(server, kingdom, Items.COBBLESTONE) + placedCobble == 20, "Cobblestone was made or lost: "
                    + stored(server, kingdom, Items.COBBLESTONE) + " stored, " + placedCobble + " placed");
            helper.assertTrue(stored(server, kingdom, Items.OAK_PLANKS) == 20 && stored(server, kingdom, Items.TORCH) == 20,
                    "Materials not placed did not all come back");
            helper.assertTrue(KingdomWorldApi.state(server).construction().all().isEmpty(), "The construction is still there");
            helper.assertTrue(at(helper, 5, 0, 4).is(Blocks.COBBLESTONE) || placedCobble == 0, "Placed blocks were taken away");
        } finally {
            end(helper, kingdom, from, to, template.id(), builder);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- blocks of the mod

    @GameTest(template = EMPTY, batch = "kingdomworld_build_mod_blocks")
    public static void aStorehouseAndAPostOfATemplateRegisterLikePlacedByAPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Registrars", UUID.randomUUID());
        List<String> palette = List.of("minecraft:cobblestone", "kingdomworld:storehouse", "kingdomworld:work_post");
        BuildingTemplate template = BuildingTemplates.addForTest(KingdomWorld.id("test_mod_blocks"), description("mod blocks"),
                structure(3, 2, 3, palette, new int[][]{{0, 0, 0, 0}, {1, 0, 0, 0}, {2, 0, 0, 0}, {0, 1, 0, 1}, {2, 1, 0, 2}}));
        Construction construction = lay(helper, kingdom, template, new BlockPos(6, 0, 6));
        try {
            Building building = ConstructionWork.finishNow(server, construction);
            helper.assertTrue(building.admin(), "The register does not remember the operator");
            WorldPos storehouse = CitizenGameTests.at(helper, 5, 1, 4);
            WorldPos post = CitizenGameTests.at(helper, 7, 1, 4);
            helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(5, 1, 4))).is(ModRegistries.STOREHOUSE.get()), "No storehouse");
            helper.assertTrue(KingdomSavedData.get(server).storehouses().get(storehouse).isPresent(), "The storehouse has no inventory");
            helper.assertTrue(KingdomStorage.storehouses(server, kingdom.id(), null).stream().anyMatch(s -> s.pos().equals(storehouse)),
                    "The kingdom does not count the storehouse");
            helper.assertTrue(PostWork.post(server, post).isPresent(), "The work post was not registered");
        } finally {
            end(helper, kingdom, from, to, template.id());
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- the server decides

    @GameTest(template = EMPTY, batch = "kingdomworld_build_refused")
    public static void theServerRefusesForeignLandOverlapsAndPlayersWithoutTheRight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Gatekeepers", UUID.randomUUID());
        BuildingTemplate template = hut("refused");
        UUID subject = UUID.randomUUID();
        KingdomWorldState state = KingdomWorldApi.state(server);
        state.membership().invite(kingdom.rulerId(), subject, 1L);
        state.membership().acceptInvitation(subject, kingdom.id(), 1L);
        try {
            KingdomWorldState.ConstructionChange far = ConstructionWork.lay(level, null, kingdom.id(), template,
                    helper.absolutePos(new BlockPos(2 + 48, 0, 2)), Placement.NONE);
            helper.assertTrue(far.outcome().error() == KingdomError.CONSTRUCTION_OUTSIDE_LAND, "Land of nobody was accepted: " + far.outcome());
            KingdomWorldState.ConstructionChange throne = ConstructionWork.lay(level, null, kingdom.id(), template,
                    helper.absolutePos(new BlockPos(2, 0, 3)), Placement.NONE);
            helper.assertTrue(throne.outcome().error() == KingdomError.PROTECTED_BLOCK, "The throne may be built over: " + throne.outcome());
            KingdomWorldState.ConstructionChange bySubject = ConstructionWork.lay(level, subject, kingdom.id(), template,
                    helper.absolutePos(new BlockPos(6, 0, 6)), Placement.NONE);
            helper.assertTrue(bySubject.outcome().error() == KingdomError.NO_PERMISSION, "A subject laid out a construction");
            Construction construction = lay(helper, kingdom, template, new BlockPos(6, 0, 6));
            KingdomWorldState.ConstructionChange overlap = ConstructionWork.lay(level, null, kingdom.id(), template,
                    helper.absolutePos(new BlockPos(7, 0, 6)), new Placement(1, false));
            helper.assertTrue(overlap.outcome().error() == KingdomError.CONSTRUCTION_OVERLAPS, "Two constructions on one place");
            var change = ConstructionActions.apply(server, subject, new ConstructionPackets.Change(kingdom.id(),
                    ConstructionPackets.Change.Action.CANCEL, construction.id(), 0));
            helper.assertTrue(!change.success() && state.construction().get(construction.id()).isPresent(), "A subject cancelled a construction");
            helper.assertTrue(state.membership().setRole(kingdom.rulerId(), subject, Role.STEWARD).ok(), "No steward");
            helper.assertTrue(state.membership().hasPermission(subject, kingdom.id(), Permission.MANAGE_CONSTRUCTION), "The steward has no right");
            var paused = ConstructionActions.apply(server, subject, new ConstructionPackets.Change(kingdom.id(),
                    ConstructionPackets.Change.Action.PAUSE, construction.id(), 0));
            helper.assertTrue(paused.success() && construction.paused(), "The steward could not pause");
        } finally {
            end(helper, kingdom, from, to, template.id());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_build_missing_mod")
    public static void aTemplateWithABlockOfAMissingModCannotBeBuilt(GameTestHelper helper) {
        ResourceLocation id = KingdomWorld.id("test_missing_mod");
        BuildingTemplate template = BuildingTemplates.addForTest(id, description("missing mod"), structure(1, 1, 2,
                List.of("minecraft:cobblestone", "kingdomworldtestmissing:magic_brick"), new int[][]{{0, 0, 0, 0}, {0, 0, 1, 1}}));
        try {
            helper.assertTrue(!template.available(), "A template with a block of a missing mod is available");
            String problem = template.problem().getString();
            helper.assertTrue(problem.contains("kingdomworldtestmissing"), "The reason does not name the mod: " + problem);
            helper.assertTrue(BuildingTemplates.problem(template, ConstructionSettings.rules()) != null, "The rules let it be built");
            BuildingTemplate bedrock = BuildingTemplates.addForTest(KingdomWorld.id("test_bedrock"), description("bedrock"),
                    structure(1, 1, 1, List.of("minecraft:bedrock"), new int[][]{{0, 0, 0, 0}}));
            helper.assertTrue(!bedrock.available(), "Bedrock can be built");
            BuildingTemplate big = BuildingTemplates.addForTest(KingdomWorld.id("test_big"), description("big"),
                    structure(60, 1, 1, List.of("minecraft:cobblestone"), new int[][]{{0, 0, 0, 0}}));
            helper.assertTrue(BuildingTemplates.problem(big, ConstructionSettings.rules()) != null, "A template wider than allowed can be built");
            for (BuildingTemplate builtIn : BuildingTemplates.all()) {
                if (builtIn.id().getNamespace().equals(KingdomWorld.MOD_ID) && !builtIn.id().getPath().startsWith("test_")) {
                    helper.assertTrue(BuildingTemplates.problem(builtIn, ConstructionSettings.rules()) == null, "The built-in template "
                            + builtIn.id() + " cannot be built: " + builtIn.problem());
                }
            }
            helper.assertTrue(BuildingTemplates.all().stream().filter(t -> !t.id().getPath().startsWith("test_")).count() >= 7,
                    "Fewer than seven built-in templates");
        } finally {
            BuildingTemplates.removeForTest(id);
            BuildingTemplates.removeForTest(KingdomWorld.id("test_bedrock"));
            BuildingTemplates.removeForTest(KingdomWorld.id("test_big"));
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- far from players

    @GameTest(template = EMPTY, batch = "kingdomworld_build_far")
    public static void workFarFromPlayersPlacesItsBlocksWhenThePlaceIsLoadedAndSurvivesSaving(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Far Builders", UUID.randomUUID());
        BuildingTemplate template = hut("far");
        StorehouseInventory store = GatheringGameTests.storehouse(level, helper.absolutePos(new BlockPos(0, 0, 4)));
        fill(store, new ItemStack(Items.COBBLESTONE, 9), new ItemStack(Items.OAK_PLANKS, 2), new ItemStack(Items.TORCH, 1));
        WorldPos post = GatheringGameTests.post(helper, new BlockPos(0, 0, 0), "builder");
        Citizen builder = farBuilder(helper, kingdom, post);
        Construction construction = lay(helper, kingdom, template, new BlockPos(6, 0, 6));
        try {
            workFar(server, builder, 2);
            helper.assertTrue(construction.pending().size() == 12, "Not every block was paid: " + construction.pending().size());
            helper.assertTrue(stored(server, kingdom, Items.COBBLESTONE) == 0 && stored(server, kingdom, Items.OAK_PLANKS) == 0
                    && stored(server, kingdom, Items.TORCH) == 0, "The materials were not taken");
            helper.assertTrue(at(helper, 5, 0, 4).isAir(), "Blocks were placed before their place was looked at");

            // A restart now loses nothing: paid blocks still wait, nothing is paid twice.
            KingdomSavedData loaded = KingdomSavedData.load(KingdomSavedData.get(server).save(new CompoundTag()));
            helper.assertTrue(!loaded.isReadOnly(), "Saved construction became read-only: " + loaded.readOnlyReason());
            Construction saved = loaded.state().construction().get(construction.id()).orElseThrow();
            helper.assertTrue(saved.pending().equals(construction.pending()) && saved.doneSet().equals(construction.doneSet())
                    && saved.delivered().equals(construction.delivered()) && saved.fingerprint() == construction.fingerprint(),
                    "The construction changed in saving");

            ConstructionWork.tick(server, 1);
            helper.assertTrue(KingdomWorldApi.state(server).construction().all().isEmpty(), "The finished construction stayed");
            helper.assertTrue(at(helper, 5, 0, 4).is(Blocks.COBBLESTONE) && at(helper, 5, 1, 4).is(Blocks.OAK_PLANKS)
                    && at(helper, 6, 1, 5).is(Blocks.TORCH), "The paid blocks were not placed once loaded");
            helper.assertTrue(KingdomWorldApi.state(server).construction().buildingsOf(kingdom.id()).size() == 1, "No building in the register");
            helper.assertTrue(stored(server, kingdom, Items.COBBLESTONE) == 0, "Blocks came back as items");
        } finally {
            end(helper, kingdom, from, to, template.id(), builder);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_build_upgrade")
    public static void formatSevenIsUpgradedAndTheStewardGetsTheRight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Old Builders", UUID.randomUUID());
        try {
            CompoundTag old = KingdomSavedData.get(server).save(new CompoundTag());
            old.putInt("formatVersion", 7);
            old.remove("construction");
            ListTag kingdoms = old.getList("kingdoms", 10);
            for (int i = 0; i < kingdoms.size(); i++) {
                ListTag steward = new ListTag();
                steward.add(StringTag.valueOf("build"));
                kingdoms.getCompound(i).getCompound("permissions").put("steward", steward);
            }
            KingdomSavedData upgraded = KingdomSavedData.load(old);
            helper.assertTrue(!upgraded.isReadOnly(), "Format 7 data became read-only: " + upgraded.readOnlyReason());
            helper.assertTrue(upgraded.state().construction().all().isEmpty(), "An upgraded world came with constructions");
            helper.assertTrue(upgraded.state().membership().kingdom(kingdom.id()).orElseThrow().permissions()
                    .has(Role.STEWARD, Permission.MANAGE_CONSTRUCTION), "The steward did not get the right to manage construction");
            helper.assertTrue(upgraded.save(new CompoundTag()).getInt("formatVersion") == KingdomSavedData.FORMAT_VERSION, "Saved in the old format");
            CompoundTag broken = KingdomSavedData.get(server).save(new CompoundTag());
            CompoundTag bad = new CompoundTag();
            bad.putLong("id", 5);
            broken.getCompound("construction").getList("constructions", 10).add(bad);
            helper.assertTrue(KingdomSavedData.load(broken).isReadOnly(), "An unreadable construction did not make the data read-only");
        } finally {
            CitizenGameTests.finish(helper, kingdom);
            GatheringGameTests.clear(level, from, to);
        }
        helper.succeed();
    }

    /**
     * The game turns stairs the way the core turns directions.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_build_turns")
    public static void blockStatesTurnWithTheTemplate(GameTestHelper helper) {
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING,
                net.minecraft.core.Direction.NORTH);
        for (int rotation = 0; rotation < 4; rotation++) {
            for (boolean mirror : new boolean[]{false, true}) {
                Placement placement = new Placement(rotation, mirror);
                BlockState turned = io.github.sekerator.kingdomworld.common.construction.TemplateBlocks.transform(stairs, placement);
                var expected = io.github.sekerator.kingdomworld.common.construction.TemplateBlocks.direction(
                        placement.apply(io.github.sekerator.kingdomworld.core.construction.Facing.NORTH));
                helper.assertTrue(turned.getValue(net.minecraft.world.level.block.StairBlock.FACING) == expected,
                        "Stairs turned " + placement + " face " + turned.getValue(net.minecraft.world.level.block.StairBlock.FACING) + ", not " + expected);
            }
        }
        helper.succeed();
    }
}
