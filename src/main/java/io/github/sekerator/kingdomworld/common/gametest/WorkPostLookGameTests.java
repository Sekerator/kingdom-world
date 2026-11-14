package io.github.sekerator.kingdomworld.common.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.block.WorkPostBlock;
import io.github.sekerator.kingdomworld.common.block.WorkPostLook;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSites;
import io.github.sekerator.kingdomworld.common.citizen.work.WorkPostLooks;
import io.github.sekerator.kingdomworld.common.construction.BlueprintCapture;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplate;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplates;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.construction.TemplateBlocks;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.citizen.Profession;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.BuildPlan;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.CustomBlueprint;
import io.github.sekerator.kingdomworld.core.construction.Facing;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
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
 * Game tests of the board of a work post, which shows the profession of the post: it follows every change of the profession,
 * the land given up and the kingdom dissolved, is put right by the scan of a chunk and on land without an owner, comes with a
 * post placed where the data knows one, and neither the hashes of templates nor building over a post nor a blueprint of a
 * player sees it. Each test founds its kingdom in a batch of its own.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorkPostLookGameTests {
    private static final String EMPTY = "empty";
    /** Built-in templates with a post and their hashes before the post had a property (by their files, at 9fd40b4). */
    private static final String[] TEMPLATES = {"kingdomworld:workshop", "kingdomworld:market_square", "kingdomworld:mage_tower"};
    private static final long[] GOLDEN = {1301802044017965731L, 897146897576760734L, 1463876964105840601L};
    /** Changing the look of a post: no neighbour hears of it, as the tick of the looks does it. */
    private static final int SILENT = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private WorkPostLookGameTests() {
    }

    private static WorkPostLook look(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof WorkPostBlock ? state.getValue(WorkPostBlock.PROFESSION) : null;
    }

    private static WorkPostLook look(GameTestHelper helper, BlockPos relative) {
        return look(helper.getLevel(), helper.absolutePos(relative));
    }

    private static boolean profession(MinecraftServer server, WorldPos pos, String profession) {
        return KingdomWorldApi.change(server, "test", state -> state.setPostProfession(null, pos, profession)).ok();
    }

    private static void silently(GameTestHelper helper, BlockPos relative, WorkPostLook look) {
        BlockPos pos = helper.absolutePos(relative);
        helper.getLevel().setBlock(pos, helper.getLevel().getBlockState(pos).setValue(WorkPostBlock.PROFESSION, look), SILENT);
    }

    private static BlockPos[] area(GameTestHelper helper) {
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(16, 3, 16));
        GatheringGameTests.clear(helper.getLevel(), from, to);
        return new BlockPos[]{from, to};
    }

    private static void end(GameTestHelper helper, Kingdom kingdom, BlockPos[] area) {
        CitizenGameTests.finish(helper, kingdom);
        WorkPostLooks.flush(helper.getLevel().getServer());
        GatheringGameTests.clear(helper.getLevel(), area[0], area[1]);
    }

    // ---------------------------------------------------------------- the look follows the post

    @GameTest(template = EMPTY, batch = "kingdomworld_post_look_change")
    public static void theBoardFollowsTheProfessionOfThePost(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos[] area = area(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Boards", UUID.randomUUID());
        try {
            BlockPos relative = new BlockPos(5, 1, 5);
            WorldPos post = GatheringGameTests.post(helper, relative, "lumberjack");
            WorkPostLooks.flush(server);
            helper.assertTrue(look(helper, relative) == WorkPostLook.LUMBERJACK, "The post shows " + look(helper, relative));
            helper.assertTrue(profession(server, post, "miner"), "The miner was refused");
            WorkPostLooks.flush(server);
            helper.assertTrue(look(helper, relative) == WorkPostLook.MINER, "The post shows " + look(helper, relative));
            helper.assertTrue(profession(server, post, Professions.UNEMPLOYED.id()), "Unemployed was refused");
            WorkPostLooks.flush(server);
            helper.assertTrue(look(helper, relative) == WorkPostLook.NONE, "The post without a profession shows " + look(helper, relative));
            helper.assertTrue(KingdomWorldApi.state(server).citizens().posts().get(post).isPresent(),
                    "Changing the look took the post from the data");
        } finally {
            end(helper, kingdom, area);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_post_look_every")
    public static void everyProfessionOfAPostHasItsOwnLook(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos[] area = area(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Every Board", UUID.randomUUID());
        try {
            List<Profession> professions = Professions.postProfessions();
            helper.assertTrue(professions.stream().anyMatch(p -> p.id().equals("innkeeper")),
                    "The innkeeper is not among the professions of posts: " + professions);
            List<String> wrong = new ArrayList<>();
            for (int i = 0; i < professions.size(); i++) {
                String id = professions.get(i).id();
                BlockPos relative = new BlockPos(i % 8 * 2, 1, 6 + i / 8 * 2);
                GatheringGameTests.post(helper, relative, id);
                WorkPostLooks.flush(server);
                WorkPostLook shown = look(helper, relative);
                if (shown == null || shown == WorkPostLook.NONE || shown == WorkPostLook.OTHER || !shown.getSerializedName().equals(id)) {
                    wrong.add(id + "=" + shown);
                }
            }
            helper.assertTrue(wrong.isEmpty(), "Posts without a look of their own: " + wrong);
        } finally {
            end(helper, kingdom, area);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_post_look_land")
    public static void landGivenUpAndAKingdomDissolvedLeaveTheBoardsEmpty(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Lost Boards", UUID.randomUUID());
        BlockPos far = null;
        try {
            // A post in a corner chunk of the land, which the kingdom may give up, and one on the land it keeps.
            KingdomWorldState state = KingdomWorldApi.state(server);
            BlockPos throne = helper.absolutePos(new BlockPos(2, 0, 2));
            BlockPos relative = new BlockPos(5, 1, 5);
            BlockPos kept = helper.absolutePos(relative);
            int cx = throne.getX() >> 4;
            int cz = throne.getZ() >> 4;
            long keptChunk = ChunkKey.of(kept.getX() >> 4, kept.getZ() >> 4);
            long corner = state.territory().territory(kingdom.id()).orElseThrow().chunks().stream()
                    .filter(chunk -> chunk != keptChunk && chunk != ChunkKey.of(cx, cz))
                    .max(java.util.Comparator.comparingInt(chunk -> Math.abs(ChunkKey.x(chunk) - cx) + Math.abs(ChunkKey.z(chunk) - cz)))
                    .orElse(ChunkKey.of(cx, cz));
            helper.assertTrue(corner != ChunkKey.of(cx, cz), "The kingdom has no chunk to give up");
            far = new BlockPos((ChunkKey.x(corner) << 4) + 8, throne.getY() + 1, (ChunkKey.z(corner) << 4) + 8);
            level.getChunk(far);
            WorldPos farPost = GatheringGameTests.post(level, far, "lumberjack");
            GatheringGameTests.post(helper, relative, "miner");
            WorkPostLooks.flush(server);
            helper.assertTrue(look(level, far) == WorkPostLook.LUMBERJACK && look(helper, relative) == WorkPostLook.MINER,
                    "The posts show " + look(level, far) + " and " + look(helper, relative));

            helper.assertTrue(KingdomWorldApi.unclaimChunk(level, new ChunkPos(ChunkKey.x(corner), ChunkKey.z(corner)), kingdom.id(), null).ok(),
                    "The corner chunk was not given up");
            helper.assertTrue(state.citizens().posts().get(farPost).isEmpty(), "The post of the land given up is still known");
            WorkPostLooks.flush(server);
            helper.assertTrue(look(level, far) == WorkPostLook.NONE, "The post on the land given up shows " + look(level, far));
            helper.assertTrue(look(helper, relative) == WorkPostLook.MINER, "The post on the land kept shows " + look(helper, relative));

            helper.assertTrue(KingdomWorldApi.dissolveKingdom(server, kingdom.id()).isPresent(), "The kingdom was not dissolved");
            WorkPostLooks.flush(server);
            helper.assertTrue(look(helper, relative) == WorkPostLook.NONE, "The post of the kingdom dissolved shows " + look(helper, relative));
        } finally {
            if (far != null) {
                level.setBlock(far, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            end(helper, kingdom, area);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- the scan puts it right

    @GameTest(template = EMPTY, batch = "kingdomworld_post_look_scan")
    public static void theScanOfAChunkPutsTheBoardRight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Scanned Boards", UUID.randomUUID());
        try {
            BlockPos wrong = new BlockPos(4, 1, 4);
            BlockPos old = new BlockPos(6, 1, 4);
            GatheringGameTests.post(helper, wrong, "lumberjack");
            GatheringGameTests.post(helper, old, "miner");
            WorkPostLooks.flush(server);
            // One shows another profession, set past the mod as by a command; the other shows none, as a post of a world from
            // before the boards (its profession is in the data, its block has no look yet).
            silently(helper, wrong, WorkPostLook.FISHER);
            silently(helper, old, WorkPostLook.NONE);
            helper.assertTrue(WorkPostLooks.pending() == 0, "Something waits before the scan: " + WorkPostLooks.pending());
            Set<ChunkPos> chunks = new HashSet<>(List.of(new ChunkPos(helper.absolutePos(wrong)), new ChunkPos(helper.absolutePos(old))));
            for (ChunkPos chunk : chunks) {
                CitizenSites.scanChunk(level, level.getChunk(chunk.x, chunk.z));
            }
            helper.assertTrue(WorkPostLooks.pending() == 2, "The scan queued " + WorkPostLooks.pending() + " posts");
            WorkPostLooks.flush(server);
            helper.assertTrue(look(helper, wrong) == WorkPostLook.LUMBERJACK, "The post set wrong shows " + look(helper, wrong));
            helper.assertTrue(look(helper, old) == WorkPostLook.MINER, "The post of the old world shows " + look(helper, old));
            for (ChunkPos chunk : chunks) {
                CitizenSites.scanChunk(level, level.getChunk(chunk.x, chunk.z));
            }
            helper.assertTrue(WorkPostLooks.pending() == 0, "Posts that show their profession were queued again");
        } finally {
            end(helper, kingdom, area);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_post_look_nobody")
    public static void aBoardOnLandWithoutAnOwnerIsEmptied(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Ownerless Boards", UUID.randomUUID());
        try {
            // The kingdom goes; a post of it shows a profession still, as if the chunk had been unloaded meanwhile.
            KingdomWorldApi.dissolveKingdom(server, kingdom.id());
            BlockPos relative = new BlockPos(5, 1, 5);
            helper.setBlock(relative, ModRegistries.WORK_POST.get());
            WorkPostLooks.flush(server);
            silently(helper, relative, WorkPostLook.SMITH);
            BlockPos pos = helper.absolutePos(relative);
            String dimension = Dimensions.id(level);
            helper.assertTrue(io.github.sekerator.kingdomworld.common.land.Wilderness.placeOwner(server, dimension,
                    ChunkKey.of(pos.getX() >> 4, pos.getZ() >> 4)) == null, "The land has an owner");
            // The chunk loads: its palette tells of a post showing a profession, the tick puts it right.
            WorkPostLooks.checkUnowned(level, level.getChunkAt(pos));
            helper.assertTrue(WorkPostLooks.pending() > 0, "The chunk without an owner was not queued");
            WorkPostLooks.flush(server);
            helper.assertTrue(look(helper, relative) == WorkPostLook.NONE, "The post on land without an owner shows " + look(helper, relative));
        } finally {
            end(helper, kingdom, area);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_post_look_raised")
    public static void aPostPlacedWhereTheDataKnowsOneTakesItsLook(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Raised Boards", UUID.randomUUID());
        try {
            // A settlement raised from its data: the post and its profession are known before the block stands.
            BlockPos relative = new BlockPos(5, 1, 5);
            WorldPos pos = CitizenGameTests.at(helper, 5, 1, 5);
            KingdomWorldState state = KingdomWorldApi.state(server);
            helper.assertTrue(state.citizens().addSite(pos, SiteKinds.WORK_POST, kingdom.id()), "The post was not recorded");
            helper.assertTrue(profession(server, pos, "smith"), "The smith was refused");
            WorkPostLooks.flush(server);
            helper.setBlock(relative, ModRegistries.WORK_POST.get());
            WorkPostLooks.flush(server);
            helper.assertTrue(look(helper, relative) == WorkPostLook.SMITH, "The post raised from the data shows " + look(helper, relative));
            helper.assertTrue(state.citizens().posts().get(pos).map(WorkPost::professionId).orElse("").equals("smith"),
                    "The post lost its profession when its block came");
        } finally {
            end(helper, kingdom, area);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- what does not see the look

    @GameTest(template = EMPTY, batch = "kingdomworld_post_look_build")
    public static void aTemplateIsLaidOverAPostShowingItsProfession(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Built Boards", UUID.randomUUID());
        ResourceLocation id = KingdomWorld.id("test_post_look");
        // A floor of cobblestone with a post in the middle.
        List<String> palette = List.of("minecraft:cobblestone", "kingdomworld:work_post", "minecraft:air");
        List<int[]> blocks = new ArrayList<>();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                blocks.add(new int[]{x, 0, z, 0});
                blocks.add(new int[]{x, 1, z, x == 1 && z == 1 ? 1 : 2});
            }
        }
        JsonObject description = JsonParser.parseString("{\"name\": \"kingdomworld.building.small_house\", \"fallback_name\": \"post look\", "
                + "\"category\": \"housing\", \"type\": \"test\", \"icon\": \"minecraft:cobblestone\", \"anchor\": [1, 0, 2], "
                + "\"entrance\": \"south\"}").getAsJsonObject();
        BuildingTemplate template = BuildingTemplates.addForTest(id, description,
                ConstructionGameTests.structure(3, 2, 3, palette, blocks.toArray(new int[0][])));
        try {
            BlockPos anchor = helper.absolutePos(new BlockPos(8, 1, 8));
            BlockPos postAt = template.place(new BlockPos(1, 1, 1), anchor, Placement.NONE);
            WorldPos post = GatheringGameTests.post(level, postAt, "lumberjack");
            WorkPostLooks.flush(server);
            helper.assertTrue(look(level, postAt) == WorkPostLook.LUMBERJACK, "The post shows " + look(level, postAt));

            KingdomWorldState.ConstructionChange change = ConstructionWork.lay(level, null, kingdom.id(), template, anchor, Placement.NONE);
            helper.assertTrue(change.construction().isPresent(), "The template was refused over the post: " + change.outcome());
            Construction construction = change.construction().get();
            BuildPlan plan = ConstructionWork.plan(construction).orElseThrow();
            helper.assertTrue(plan.steps().stream().noneMatch(step -> step.kind() == BuildPlan.Kind.CLEAR
                    && step.x() == postAt.getX() && step.y() == postAt.getY() && step.z() == postAt.getZ()), "The post is to be cleared");
            int index = -1;
            for (BuildPlan.Step step : plan.steps()) {
                if (step.kind() == BuildPlan.Kind.PLACE && template.blocks().get(step.block()).state().getBlock() instanceof WorkPostBlock) {
                    index = step.index();
                }
            }
            helper.assertTrue(index >= 0, "The plan has no step of the post");
            List<ItemStack> drops = new ArrayList<>();
            ConstructionWork.StepResult result = ConstructionWork.doStep(level, construction, plan, template, index, null, ItemStack.EMPTY, drops, true);
            helper.assertTrue(result == ConstructionWork.StepResult.DONE && drops.isEmpty(), "The step of the post ended " + result + ", " + drops);
            helper.assertTrue(look(level, postAt) == WorkPostLook.LUMBERJACK, "The post standing there became " + look(level, postAt));
            helper.assertTrue(KingdomWorldApi.state(server).citizens().posts().get(post).map(WorkPost::professionId).orElse("").equals("lumberjack"),
                    "The post lost its profession");
        } finally {
            end(helper, kingdom, area);
            BuildingTemplates.removeForTest(id);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_post_look_capture")
    public static void aBlueprintOfAPostShowingItsProfessionIsThePlainPost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Drawn Boards", UUID.randomUUID());
        try {
            for (int x = 6; x <= 8; x++) {
                for (int z = 6; z <= 8; z++) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.COBBLESTONE);
                }
            }
            BlockPos relative = new BlockPos(7, 1, 7);
            GatheringGameTests.post(helper, relative, "lumberjack");
            WorkPostLooks.flush(server);
            helper.assertTrue(look(helper, relative) == WorkPostLook.LUMBERJACK, "The post shows " + look(helper, relative));
            BlockPos a = helper.absolutePos(new BlockPos(6, 0, 6));
            BlockPos b = helper.absolutePos(new BlockPos(8, 1, 8));
            KingdomActions.Feedback first = BlueprintCapture.capture(level, null, kingdom.id(), a, b, "Board A", "housing", "", Facing.SOUTH);
            helper.assertTrue(first.success(), "The blueprint was not saved: " + first.message().getString());
            silently(helper, relative, WorkPostLook.NONE);
            KingdomActions.Feedback second = BlueprintCapture.capture(level, null, kingdom.id(), a, b, "Board B", "housing", "", Facing.SOUTH);
            helper.assertTrue(second.success(), "The second blueprint was not saved: " + second.message().getString());
            List<CustomBlueprint> saved = KingdomWorldApi.state(server).construction().blueprintsOf(kingdom.id());
            helper.assertTrue(saved.size() == 2, "Blueprints saved: " + saved.size());
            CustomBlueprint withLook = saved.stream().filter(blueprint -> blueprint.name().equals("Board A")).findFirst().orElseThrow();
            CustomBlueprint plain = saved.stream().filter(blueprint -> blueprint.name().equals("Board B")).findFirst().orElseThrow();
            helper.assertTrue(withLook.palette().contains("kingdomworld:work_post")
                    && withLook.palette().stream().noneMatch(text -> text.startsWith("kingdomworld:work_post[")),
                    "The blueprint writes the post as " + withLook.palette());
            helper.assertTrue(withLook.palette().equals(plain.palette()) && java.util.Arrays.equals(withLook.blocks(), plain.blocks()),
                    "A post showing a profession is drawn otherwise than a post without one");
        } finally {
            end(helper, kingdom, area);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_post_look_hashes")
    public static void theHashesOfTemplatesWithAPostStayAsTheyWere(GameTestHelper helper) {
        List<String> changed = new ArrayList<>();
        for (int i = 0; i < TEMPLATES.length; i++) {
            long now = BuildingTemplates.get(TEMPLATES[i]).map(BuildingTemplate::fingerprint).orElse(0L);
            if (now != GOLDEN[i]) {
                changed.add(TEMPLATES[i] + " " + now + " instead of " + GOLDEN[i]);
            }
        }
        helper.assertTrue(changed.isEmpty(), "Hashes of templates with a post changed: " + changed);
        BlockState plain = ModRegistries.WORK_POST.get().defaultBlockState();
        BlockState lumberjack = plain.setValue(WorkPostBlock.PROFESSION, WorkPostLook.LUMBERJACK);
        helper.assertTrue(TemplateBlocks.sameBuilt(lumberjack, plain), "A post of a lumberjack is not the post of a template");
        helper.assertTrue(!TemplateBlocks.sameBuilt(Blocks.STONE.defaultBlockState(), Blocks.GRANITE.defaultBlockState()), "Stone is granite");
        helper.assertTrue(!TemplateBlocks.sameBuilt(Blocks.OAK_STAIRS.defaultBlockState(),
                        Blocks.OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.EAST)),
                "Stairs turned another way are the stairs of the template");
        helper.assertTrue(TemplateBlocks.hashText(lumberjack).equals("Block{kingdomworld:work_post}")
                        && TemplateBlocks.hashText(plain).equals("Block{kingdomworld:work_post}"),
                "The post is hashed as " + TemplateBlocks.hashText(lumberjack));
        helper.assertTrue(TemplateBlocks.blueprintText(lumberjack).equals("kingdomworld:work_post"),
                "A blueprint writes the post as " + TemplateBlocks.blueprintText(lumberjack));
        helper.succeed();
    }
}
