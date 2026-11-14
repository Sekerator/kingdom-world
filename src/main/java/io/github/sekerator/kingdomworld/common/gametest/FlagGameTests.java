package io.github.sekerator.kingdomworld.common.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.block.FlagBlocks;
import io.github.sekerator.kingdomworld.common.block.KingdomFlagBlockEntity;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplate;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplates;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.construction.TemplateBlocks;
import io.github.sekerator.kingdomworld.common.flag.FlagActions;
import io.github.sekerator.kingdomworld.common.flag.FlagDirectory;
import io.github.sekerator.kingdomworld.common.flag.FlagPatterns;
import io.github.sekerator.kingdomworld.common.flag.FlagSpotCache;
import io.github.sekerator.kingdomworld.common.flag.FlagWork;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.law.LawStandIn;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.siege.SiegeWork;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.construction.BlockCost;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.FlagSpots;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomFlag;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcNames;
import io.github.sekerator.kingdomworld.core.npc.RulerCharacter;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Game tests of the flags of kingdoms in the world: the flags of templates and those added to buildings, what drops and what
 * comes back, the land and the law, old banners, a new flag on the throne, conquest and dissolving, constructions over flags,
 * the integrity of buildings, blueprints, thrones, wars, the refusals of the server, the built-in templates and the flag of a
 * kingdom that broke away.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FlagGameTests {
    private static final String EMPTY = "empty";
    /** Where the sites of the tests stand, away from the throne at 2, 0, 2. */
    private static final BlockPos SITE = new BlockPos(6, 0, 4);

    private FlagGameTests() {
    }

    // ---------------------------------------------------------------- templates

    private static JsonObject description(String name, String category) {
        return JsonParser.parseString("{\"name\": \"kingdomworld.building.small_house\", \"fallback_name\": \"" + name
                + "\", \"category\": \"" + category + "\", \"type\": \"test\", \"icon\": \"minecraft:oak_planks\", \"anchor\": [4, 0, 9], "
                + "\"entrance\": \"south\"}").getAsJsonObject();
    }

    /**
     * A hall of 9 by 9 with a margin of air in front: a floor of cobblestone, walls of planks three high, a door in the middle
     * of the south wall and a flat roof; optionally a red banner standing on the roof and one hanging on the front wall.
     */
    private static BuildingTemplate hall(String name, String category, boolean banners) {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 10; z++) {
                for (int y = 0; y < 7; y++) {
                    BlockState state = Blocks.AIR.defaultBlockState();
                    if (z < 9) {
                        boolean wall = x == 0 || x == 8 || z == 0 || z == 8;
                        if (y == 0 || y == 4) {
                            state = y == 0 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState();
                        } else if (y <= 3 && wall) {
                            state = Blocks.OAK_PLANKS.defaultBlockState();
                        }
                    }
                    blocks.put(new BlockPos(x, y, z), state);
                }
            }
        }
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        blocks.put(new BlockPos(4, 1, 8), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        blocks.put(new BlockPos(4, 2, 8), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        if (banners) {
            blocks.put(new BlockPos(2, 5, 2), Blocks.RED_BANNER.defaultBlockState().setValue(BannerBlock.ROTATION, 0));
            blocks.put(new BlockPos(6, 3, 9), Blocks.RED_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH));
        }
        return BuildingTemplates.addForTest(KingdomWorld.id("test_flag_" + name), description(name, category), structure(9, 7, 10, blocks));
    }

    /**
     * A structure tag as the structure block writes it, with the states of its blocks in full.
     */
    private static CompoundTag structure(int sizeX, int sizeY, int sizeZ, Map<BlockPos, BlockState> blocks) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", 3465);
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(sizeX));
        size.add(IntTag.valueOf(sizeY));
        size.add(IntTag.valueOf(sizeZ));
        tag.put("size", size);
        List<BlockState> states = new ArrayList<>();
        ListTag palette = new ListTag();
        ListTag list = new ListTag();
        blocks.forEach((pos, state) -> {
            int index = states.indexOf(state);
            if (index < 0) {
                index = states.size();
                states.add(state);
                palette.add(NbtUtils.writeBlockState(state));
            }
            CompoundTag entry = new CompoundTag();
            ListTag at = new ListTag();
            at.add(IntTag.valueOf(pos.getX()));
            at.add(IntTag.valueOf(pos.getY()));
            at.add(IntTag.valueOf(pos.getZ()));
            entry.put("pos", at);
            entry.putInt("state", index);
            list.add(entry);
        });
        tag.put("palette", palette);
        tag.put("blocks", list);
        tag.put("entities", new ListTag());
        return tag;
    }

    /**
     * Lays a template out and finishes it at once, as an operator does.
     */
    private static Building build(GameTestHelper helper, Kingdom kingdom, BuildingTemplate template) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos anchor = helper.absolutePos(SITE.offset(4, 0, 9));
        KingdomWorldState.ConstructionChange change = ConstructionWork.lay(helper.getLevel(), null, kingdom.id(), template, anchor, Placement.NONE);
        Construction construction = change.construction().orElseThrow(() -> new IllegalStateException("Refused: " + change.outcome()));
        return ConstructionWork.finishNow(server, construction).orElseThrow(() -> new IllegalStateException("Not finished"));
    }

    /**
     * The place of the world of a position of a template laid at the site.
     */
    private static BlockPos at(GameTestHelper helper, BuildingTemplate template, int x, int y, int z) {
        return template.place(new BlockPos(x, y, z), helper.absolutePos(SITE.offset(4, 0, 9)), Placement.NONE);
    }

    private static List<BlockPos> flagsIn(ServerLevel level, BlockPos from, BlockPos to) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (FlagBlocks.is(level.getBlockState(pos))) {
                found.add(pos.immutable());
            }
        }
        return found;
    }

    @org.jetbrains.annotations.Nullable
    private static KingdomFlagBlockEntity flag(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof KingdomFlagBlockEntity flag ? flag : null;
    }

    private static BlockPos from(GameTestHelper helper) {
        return helper.absolutePos(SITE.offset(-2, -1, -2));
    }

    private static BlockPos to(GameTestHelper helper) {
        return helper.absolutePos(SITE.offset(11, 9, 12));
    }

    private static void prepare(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GatheringGameTests.clear(level, from(helper), to(helper));
        for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(SITE.offset(-2, -1, -2)), helper.absolutePos(SITE.offset(11, -1, 12)))) {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2 | 16);
        }
    }

    private static void end(GameTestHelper helper, Kingdom kingdom, BuildingTemplate... templates) {
        CitizenGameTests.finish(helper, kingdom);
        GatheringGameTests.clear(helper.getLevel(), from(helper), to(helper));
        for (BuildingTemplate template : templates) {
            BuildingTemplates.removeForTest(template.id());
        }
    }

    private static ServerPlayer member(GameTestHelper helper, Kingdom kingdom, String name) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = LawStandIn.create(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        KingdomWorldState state = KingdomWorldApi.state(server);
        state.membership().invite(kingdom.rulerId(), player.getUUID(), 1L);
        state.membership().acceptInvitation(player.getUUID(), kingdom.id(), 1L);
        state.membership().setRole(kingdom.rulerId(), player.getUUID(), Role.STEWARD);
        return player;
    }

    private static int items(ServerLevel level, BlockPos around) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(around).inflate(3)).size();
    }

    // ---------------------------------------------------------------- 1. flags of templates and added flags

    @GameTest(template = EMPTY, batch = "kingdomworld_flag_buildings", timeoutTicks = 200)
    public static void aHallFliesTheFlagOfItsKingdomAndAHouseKeepsItsBanners(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        prepare(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Flag Halls", UUID.randomUUID());
        BuildingTemplate hall = hall("trade_hall", "trade", true);
        BuildingTemplate house = hall("house", "housing", true);
        try {
            helper.assertTrue(FlagBlocks.is(hall.blocks().get(hall.indexAt(new BlockPos(2, 5, 2))).state()), "The red banner of a hall is no flag");
            helper.assertTrue(house.blocks().get(house.indexAt(new BlockPos(2, 5, 2))).state().is(Blocks.RED_BANNER), "The banner of a house became a flag");
            build(helper, kingdom, hall);
            BlockPos roof = at(helper, hall, 2, 5, 2);
            BlockPos front = at(helper, hall, 6, 3, 9);
            for (BlockPos pos : List.of(roof, front)) {
                KingdomFlagBlockEntity flag = flag(level, pos);
                helper.assertTrue(flag != null && kingdom.id().equals(flag.kingdom()), "No flag of the kingdom at " + GatheringGameTests.local(helper, pos));
            }
            helper.assertTrue(level.getBlockState(front).getValue(io.github.sekerator.kingdomworld.common.block.KingdomWallFlagBlock.FACING)
                    == Direction.SOUTH, "The flag on the wall does not face out");
            helper.assertTrue(flagsIn(level, from(helper), to(helper)).size() == 2, "A hall with two flags of its template got more");
            helper.succeed();
        } finally {
            end(helper, kingdom, hall, house);
        }
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_flag_house", timeoutTicks = 200)
    public static void aHouseKeepsItsRedBannerAndGetsOneFlagBesideTheDoor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        prepare(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Flag House", UUID.randomUUID());
        BuildingTemplate house = hall("house_banner", "housing", true);
        try {
            build(helper, kingdom, house);
            helper.assertTrue(level.getBlockState(at(helper, house, 2, 5, 2)).is(Blocks.RED_BANNER), "The banner of the house is gone");
            List<BlockPos> flags = flagsIn(level, from(helper), to(helper));
            helper.assertTrue(flags.size() == 1, "A house flies " + flags.size() + " flags");
            KingdomFlagBlockEntity added = flag(level, flags.get(0));
            helper.assertTrue(added != null && added.origin() == KingdomFlagBlockEntity.Origin.ADDED && kingdom.id().equals(added.kingdom()),
                    "The flag of the house is not an added flag of the kingdom");
            helper.succeed();
        } finally {
            end(helper, kingdom, house);
        }
    }

    // ---------------------------------------------------------------- 2. nothing drops, members take down, the rest comes back

    @GameTest(template = EMPTY, batch = "kingdomworld_flag_breaking", timeoutTicks = 200)
    public static void aFlagDropsNothingStaysOffForAMemberAndComesBackOtherwise(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        prepare(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Flag Breaking", UUID.randomUUID());
        BuildingTemplate hall = hall("breaking", "trade", true);
        try {
            Building building = build(helper, kingdom, hall);
            BlockPos roof = at(helper, hall, 2, 5, 2);
            BlockPos front = at(helper, hall, 6, 3, 9);
            level.destroyBlock(roof, true);
            helper.assertTrue(items(level, roof) == 0, "A broken flag dropped something");
            helper.assertTrue(FlagWork.lookAtNow(server, building.id()), "The building was not looked at");
            helper.assertTrue(FlagBlocks.is(level.getBlockState(roof)), "A flag knocked down did not come back");
            ServerPlayer member = member(helper, kingdom, "kw-flag-member");
            member.setPos(front.getX() + 0.5, front.getY(), front.getZ() + 1.5);
            helper.assertTrue(member.gameMode.destroyBlock(front), "A member could not take a flag down");
            helper.assertTrue(items(level, front) == 0, "A flag taken down dropped something");
            helper.assertTrue(FlagWork.lookAtNow(server, building.id()), "The building was not looked at");
            helper.assertTrue(level.getBlockState(front).isAir(), "A flag a member took down came back");
            helper.succeed();
        } finally {
            end(helper, kingdom, hall);
        }
    }

    // ---------------------------------------------------------------- 3. strangers in peace

    @GameTest(template = EMPTY, batch = "kingdomworld_flag_stranger", timeoutTicks = 200)
    public static void aStrangerCannotBreakAFlagInPeace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        prepare(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Flag Strangers", UUID.randomUUID());
        BuildingTemplate hall = hall("stranger", "trade", true);
        try {
            build(helper, kingdom, hall);
            BlockPos front = at(helper, hall, 6, 3, 9);
            ServerPlayer stranger = LawStandIn.create(level.getServer(), level, new GameProfile(UUID.randomUUID(), "kw-flag-stranger"));
            stranger.setPos(front.getX() + 0.5, front.getY(), front.getZ() + 1.5);
            stranger.gameMode.destroyBlock(front);
            helper.assertTrue(FlagBlocks.is(level.getBlockState(front)), "A stranger broke a flag in peace");
            helper.succeed();
        } finally {
            end(helper, kingdom, hall);
        }
    }

    // ---------------------------------------------------------------- 4. a block placed by a player

    @GameTest(template = EMPTY, batch = "kingdomworld_flag_player_block", timeoutTicks = 200)
    public static void aBlockOfAPlayerKeepsAnAddedFlagAway(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        prepare(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Flag Player Block", UUID.randomUUID());
        BuildingTemplate shed = hall("player_block", "production", false);
        try {
            List<FlagSpots.Spot> spots = FlagSpotCache.of(shed).added();
            helper.assertTrue(spots.size() == 1, "The shed should get one flag: " + spots);
            FlagSpots.Spot spot = spots.get(0);
            BlockPos place = at(helper, shed, spot.x(), spot.y(), spot.z());
            Building building = build(helper, kingdom, shed);
            helper.assertTrue(FlagBlocks.is(level.getBlockState(place)), "The shed got no flag beside its door");
            level.setBlock(place, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(place, Blocks.LANTERN.defaultBlockState(), 3);
            FlagWork.lookAtNow(level.getServer(), building.id());
            helper.assertTrue(level.getBlockState(place).is(Blocks.LANTERN), "The flag replaced the block of a player");
            helper.succeed();
        } finally {
            end(helper, kingdom, shed);
        }
    }

    // ---------------------------------------------------------------- 5. old banners

    @GameTest(template = EMPTY, batch = "kingdomworld_flag_old_banners", timeoutTicks = 200)
    public static void aPlainOldBannerBecomesAFlagAndOneWithPatternsStays(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        prepare(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Flag Old Banners", UUID.randomUUID());
        BuildingTemplate hall = hall("old_banners", "trade", true);
        try {
            Building building = build(helper, kingdom, hall);
            BlockPos roof = at(helper, hall, 2, 5, 2);
            BlockPos front = at(helper, hall, 6, 3, 9);
            // As an older version left them: plain red banners where the template now has flags, one of them painted by a player.
            level.setBlock(roof, Blocks.RED_BANNER.defaultBlockState().setValue(BannerBlock.ROTATION, 4), 3);
            level.setBlock(front, Blocks.RED_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH), 3);
            if (level.getBlockEntity(front) instanceof BannerBlockEntity banner) {
                CompoundTag tag = banner.saveWithoutMetadata();
                ListTag patterns = new ListTag();
                CompoundTag cross = new CompoundTag();
                cross.putString("Pattern", "cr");
                cross.putInt("Color", 0);
                patterns.add(cross);
                tag.put("Patterns", patterns);
                banner.load(tag);
            }
            FlagWork.lookAtNow(level.getServer(), building.id());
            helper.assertTrue(FlagBlocks.is(level.getBlockState(roof)), "The plain banner did not become a flag");
            helper.assertTrue(level.getBlockState(roof).getValue(io.github.sekerator.kingdomworld.common.block.KingdomFlagBlock.ROTATION) == 4,
                    "The flag is not turned as the banner was");
            helper.assertTrue(level.getBlockState(front).is(Blocks.RED_WALL_BANNER), "The banner with a pattern was replaced");
            helper.assertTrue(items(level, roof) == 0, "The banner made a flag dropped something");
            helper.succeed();
        } finally {
            end(helper, kingdom, hall);
        }
    }

    // ---------------------------------------------------------------- 6. a new flag changes no block

    @GameTest(template = EMPTY, batch = "kingdomworld_flag_save", timeoutTicks = 200)
    public static void savingAFlagChangesNoBlockAndTellsTheClientsOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        prepare(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Flag Saving", UUID.randomUUID());
        BuildingTemplate hall = hall("saving", "trade", true);
        try {
            build(helper, kingdom, hall);
            Map<BlockPos, BlockState> before = new HashMap<>();
            for (BlockPos pos : BlockPos.betweenClosed(from(helper), to(helper))) {
                before.put(pos.immutable(), level.getBlockState(pos));
            }
            FlagDirectory.pushNow(server);
            int told = FlagDirectory.told();
            KingdomFlag flag = KingdomFlag.plain(11).added(new KingdomFlag.Layer("minecraft:cross", 0));
            KingdomActions.Feedback feedback = FlagActions.change(server, kingdom.rulerId(), kingdom.id(), flag);
            helper.assertTrue(feedback.success(), "The king could not save a flag: " + feedback.message().getString());
            helper.assertTrue(flag.equals(KingdomWorldApi.kingdom(server, kingdom.id()).orElseThrow().flag()), "The flag is not kept");
            helper.assertTrue(FlagDirectory.told() == told + 1, "The clients were told " + (FlagDirectory.told() - told) + " times");
            before.forEach((pos, state) -> helper.assertTrue(level.getBlockState(pos).equals(state), "A block changed at " + pos));
            helper.succeed();
        } finally {
            end(helper, kingdom, hall);
        }
    }

    // ---------------------------------------------------------------- 7. conquest and dissolving

    @GameTest(template = EMPTY, batch = "kingdomworld_flag_conquest", timeoutTicks = 400)
    public static void aConqueredBuildingFliesTheFlagOfTheWinnerAndDissolvingTakesThemDown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        prepare(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Flag Losers", UUID.randomUUID());
        Kingdom winner = ((CreateKingdomResult.Created) KingdomWorldApi.createKingdom(server, "GameTest Flag Winners", UUID.randomUUID())).kingdom();
        BuildingTemplate shed = hall("conquest", "production", true);
        Building built = build(helper, kingdom, shed);
        BlockPos roof = at(helper, shed, 2, 5, 2);
        BlockPos front = at(helper, shed, 6, 3, 9);
        // A member of the old kingdom took one down: for the winner it flies again.
        level.setBlock(front, Blocks.AIR.defaultBlockState(), 3);
        Building taken = new Building(built.id(), winner.id(), built.templateId(), built.type(), built.category(), built.role(), built.level(),
                built.materials(), built.anchor(), built.placement(), built.box(), built.fingerprint(), built.builtTime(), built.admin(),
                built.integrity(), built.checked(), built.autoRepair(), built.damaged());
        KingdomWorldApi.state(server).construction().putBuilding(taken);
        helper.assertTrue(FlagWork.lookAtNow(server, built.id()), "The building was not looked at");
        KingdomFlagBlockEntity roofFlag = flag(level, roof);
        helper.assertTrue(roofFlag != null && winner.id().equals(roofFlag.kingdom()), "The flag on the roof is not the winner's");
        helper.assertTrue(FlagBlocks.is(level.getBlockState(front)), "The missing flag was not put up for the winner");
        KingdomWorldApi.dissolveKingdom(server, winner.id());
        helper.succeedWhen(() -> {
            helper.assertTrue(flagsIn(level, from(helper), to(helper)).isEmpty(), "Flags of a kingdom dissolved still stand");
            end(helper, kingdom, shed);
        });
    }

    // ---------------------------------------------------------------- 8. constructions over flags

    @GameTest(template = EMPTY, batch = "kingdomworld_flag_construction", timeoutTicks = 200)
    public static void flagsKeepNoConstructionAway(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        prepare(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Flag Builders", UUID.randomUUID());
        BuildingTemplate shed = hall("over_flags", "production", false);
        try {
            BlockPos anchor = helper.absolutePos(SITE.offset(4, 0, 9));
            BlockPos inside = at(helper, shed, 3, 1, 3);
            level.setBlock(inside.below(), Blocks.COBBLESTONE.defaultBlockState(), 3);
            level.setBlock(inside, FlagBlocks.standing(0), 3);
            ConstructionWork.Survey survey = ConstructionWork.survey(level, shed, anchor, Placement.NONE);
            helper.assertTrue(survey.error() != KingdomError.PROTECTED_BLOCK, "A flag kept a construction from being laid out");
            helper.assertTrue(!ConstructionWork.blocksWork(level, inside, level.getBlockState(inside)), "A flag blocks the builders");
            List<net.minecraft.world.item.ItemStack> drops = new ArrayList<>();
            helper.assertTrue(ConstructionWork.clear(level, inside, null, net.minecraft.world.item.ItemStack.EMPTY, drops)
                    == ConstructionWork.StepResult.DONE, "The builders could not clear a flag");
            helper.assertTrue(drops.isEmpty() && level.getBlockState(inside).isAir(), "Clearing a flag gave something or left it");
            helper.succeed();
        } finally {
            end(helper, kingdom, shed);
        }
    }

    // ---------------------------------------------------------------- 9. integrity

    @GameTest(template = EMPTY, batch = "kingdomworld_flag_integrity", timeoutTicks = 200)
    public static void aBuildingWithoutItsFlagsIsWhole(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        prepare(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Flag Integrity", UUID.randomUUID());
        BuildingTemplate hall = hall("integrity", "trade", true);
        try {
            Building building = build(helper, kingdom, hall);
            for (BlockPos pos : flagsIn(level, from(helper), to(helper))) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            int integrity = ConstructionWork.checkNow(level.getServer(), building.id());
            helper.assertTrue(integrity == 100, "A building without its flags is whole by " + integrity + "%");
            helper.succeed();
        } finally {
            end(helper, kingdom, hall);
        }
    }

    // ---------------------------------------------------------------- 10. blueprints

    @GameTest(template = EMPTY, batch = "kingdomworld_flag_blueprint")
    public static void flagsOfABlueprintCostNothing(GameTestHelper helper) {
        helper.assertTrue(TemplateBlocks.cost(FlagBlocks.standing(3)).kind() == BlockCost.Kind.FREE, "A standing flag is not free");
        helper.assertTrue(TemplateBlocks.cost(FlagBlocks.wall(Direction.EAST)).kind() == BlockCost.Kind.FREE, "A flag on a wall is not free");
        helper.assertTrue(TemplateBlocks.cost(Blocks.RED_BANNER.defaultBlockState()).kind() == BlockCost.Kind.ONE,
                "A red banner of a player is no longer a banner");
        helper.assertTrue(TemplateBlocks.cost(FlagBlocks.standing(3)).placed(), "A flag of a blueprint is not built");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 11. thrones

    @GameTest(template = EMPTY, batch = "kingdomworld_flag_throne", timeoutTicks = 400)
    public static void theThroneGetsFlagsOnlyInAir(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Flag Throne", UUID.randomUUID());
        BlockPos throne = helper.absolutePos(new BlockPos(2, 0, 2));
        Direction facing = level.getBlockState(throne).getValue(io.github.sekerator.kingdomworld.common.block.ThroneBlock.FACING);
        BlockPos left = throne.relative(facing.getCounterClockWise());
        BlockPos right = throne.relative(facing.getClockWise());
        level.setBlock(left.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(right.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(left, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(left.above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(right, Blocks.OAK_PLANKS.defaultBlockState(), 3);
        helper.succeedWhen(() -> {
            helper.assertTrue(FlagBlocks.is(level.getBlockState(left)), "No flag beside the throne");
            KingdomFlagBlockEntity flag = flag(level, left);
            helper.assertTrue(flag != null && flag.origin() == KingdomFlagBlockEntity.Origin.THRONE && kingdom.id().equals(flag.kingdom()),
                    "The flag beside the throne is not the throne's");
            helper.assertTrue(level.getBlockState(right).is(Blocks.OAK_PLANKS), "A flag replaced a block beside the throne");
            CitizenGameTests.finish(helper, kingdom);
        });
    }

    // ---------------------------------------------------------------- 12. war

    @GameTest(template = EMPTY, batch = "kingdomworld_flag_war", timeoutTicks = 200)
    public static void flagsComeBackOnlyOnceTheCityIsCalm(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        prepare(helper);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Flag War", UUID.randomUUID());
        Kingdom enemy = ((CreateKingdomResult.Created) KingdomWorldApi.createKingdom(server, "GameTest Flag Raiders", UUID.randomUUID())).kingdom();
        BuildingTemplate hall = hall("war", "trade", true);
        try {
            Building building = build(helper, kingdom, hall);
            BlockPos roof = at(helper, hall, 2, 5, 2);
            io.github.sekerator.kingdomworld.common.diplomacy.WarWork.markPresent(enemy.id(), true);
            io.github.sekerator.kingdomworld.common.diplomacy.WarWork.markPresent(kingdom.id(), true);
            io.github.sekerator.kingdomworld.common.diplomacy.DiplomacyActions.declareByOperator(server, enemy.id(), kingdom.id(),
                    io.github.sekerator.kingdomworld.core.diplomacy.WarGoal.RAID, false);
            io.github.sekerator.kingdomworld.common.diplomacy.WarWork.state(server).warBetween(enemy.id(), kingdom.id())
                    .ifPresent(war -> io.github.sekerator.kingdomworld.common.diplomacy.WarWork.startNow(server, war));
            helper.assertTrue(io.github.sekerator.kingdomworld.common.diplomacy.WarWork.state(server).fightsAnyWar(kingdom.id()),
                    "The war did not start");
            SiegeWork.state(server).enemyNear(kingdom.id(), KingdomWorldApi.gameTime(server));
            level.setBlock(roof, Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(!FlagWork.lookAtNow(server, building.id()) && level.getBlockState(roof).isAir(),
                    "A flag came back during the fight");
            // The enemy left the city long ago.
            SiegeWork.state(server).enemyNear(kingdom.id(), KingdomWorldApi.gameTime(server) - 1_000_000L);
            helper.assertTrue(FlagWork.lookAtNow(server, building.id()), "The calm city was not looked at");
            helper.assertTrue(FlagBlocks.is(level.getBlockState(roof)), "The flag did not come back once the city was calm");
            helper.succeed();
        } finally {
            KingdomWorldApi.dissolveKingdom(server, enemy.id());
            end(helper, kingdom, hall);
        }
    }

    // ---------------------------------------------------------------- 13. the server refuses

    @GameTest(template = EMPTY, batch = "kingdomworld_flag_refusals", timeoutTicks = 100)
    public static void theServerRefusesWhatNoKingMayDo(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Flag Refusals", UUID.randomUUID());
        CreateKingdomResult created = KingdomWorldApi.createKingdom(server, "GameTest Flag World", null);
        Kingdom world = ((CreateKingdomResult.Created) created).kingdom();
        NpcWork.state(server).putKingdom(new NpcKingdom(world.id(), -1, NpcLevel.SMALL, new NpcNames.Name("Flagland", "Флагландия"),
                RulerCharacter.WARRIOR, List.of(RulerCharacter.Trait.PATIENT), 1234, Gender.MALE, List.of(), KingdomWorldApi.gameTime(server), 0L));
        KingdomFlag good = KingdomFlag.plain(4).added(new KingdomFlag.Layer("minecraft:cross", 15));
        try {
            ServerPlayer subject = member(helper, kingdom, "kw-flag-subject");
            helper.assertTrue(FlagActions.change(server, subject.getUUID(), kingdom.id(), good).message().getString()
                    .equals(KingdomActions.errorMessage(KingdomError.NO_PERMISSION).getString()), "A subject changed the flag");
            helper.assertTrue(!FlagActions.change(server, null, world.id(), good).success(), "The flag of a kingdom of the world changed");
            helper.assertTrue(!FlagActions.change(server, kingdom.rulerId(), kingdom.id(),
                    KingdomFlag.plain(4).added(new KingdomFlag.Layer("removedmod:eagle", 0))).success(), "An unknown pattern was saved");
            helper.assertTrue(!FlagPatterns.valid(KingdomFlag.BASE_PATTERN), "The field is a pattern of a layer");
            boolean sevenRefused;
            try {
                net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                buf.writeUUID(kingdom.id());
                buf.writeByte(4);
                buf.writeByte(7);
                for (int i = 0; i < 7; i++) {
                    buf.writeUtf("minecraft:cross");
                    buf.writeByte(0);
                }
                io.github.sekerator.kingdomworld.common.network.packet.FlagPackets.Save.decode(buf);
                sevenRefused = false;
            } catch (RuntimeException e) {
                sevenRefused = true;
            }
            helper.assertTrue(sevenRefused, "A flag of seven layers was read");
            helper.assertTrue(KingdomWorldApi.kingdom(server, kingdom.id()).orElseThrow().flag() == null, "A refused flag was kept");
            helper.assertTrue(FlagActions.change(server, kingdom.rulerId(), kingdom.id(), good).success(), "The king could not save a flag");
            helper.succeed();
        } finally {
            KingdomWorldApi.dissolveKingdom(server, world.id());
            CitizenGameTests.finish(helper, kingdom);
        }
    }

    // ---------------------------------------------------------------- 14. the built-in templates

    /** Fingerprints of built-in templates with red banners, taken from their files before flags existed. */
    static final Map<String, Long> GOLDEN = Map.of(
            "kingdomworld:classic_castle_small", 4459064522805080320L,
            "kingdomworld:classic_wall_gate", 2354646779713036405L,
            "kingdomworld:classic_camp_tower", 8401321818852998775L);

    @GameTest(template = EMPTY, batch = "kingdomworld_flag_templates")
    public static void builtInTemplatesFlyFlagsWhereTheyHadRedBannersAndKeepTheirHashes(GameTestHelper helper) {
        int flagged = 0;
        int houses = 0;
        for (BuildingTemplate template : BuildingTemplates.all()) {
            if (!KingdomWorld.MOD_ID.equals(template.id().getNamespace())) {
                continue;
            }
            boolean flags = template.blocks().stream().anyMatch(block -> FlagBlocks.is(block.state()));
            boolean redBanners = template.blocks().stream().anyMatch(block -> block.state().is(Blocks.RED_BANNER)
                    || block.state().is(Blocks.RED_WALL_BANNER));
            if (template.category() == BuildingTemplate.Category.HOUSING) {
                helper.assertTrue(!flags, template.id() + ": a house got flags in its template");
                houses += redBanners ? 1 : 0;
            } else {
                helper.assertTrue(!redBanners, template.id() + ": a red banner was left");
                flagged += flags ? 1 : 0;
            }
        }
        helper.assertTrue(flagged >= 100, "Only " + flagged + " templates fly flags");
        helper.assertTrue(houses > 0, "No house keeps its red banners");
        GOLDEN.forEach((id, golden) -> {
            BuildingTemplate template = BuildingTemplates.get(ResourceLocation.tryParse(id)).orElseThrow();
            helper.assertTrue(template.fingerprint() == golden, id + ": the hash is " + template.fingerprint() + ", not " + golden);
        });
        helper.succeed();
    }

    // ---------------------------------------------------------------- 15. a kingdom that broke away

    @GameTest(template = EMPTY, batch = "kingdomworld_flag_secession", timeoutTicks = 200)
    public static void aKingdomThatBrokeAwayGetsItsFlagWithoutARestart(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CreateKingdomResult created = KingdomWorldApi.createKingdom(server, "GameTest Flag Seceders", null);
        Kingdom seceded = ((CreateKingdomResult.Created) created).kingdom();
        // As a secession founds it: the kingdom first, its record of the world a moment later.
        NpcWork.state(server).putKingdom(new NpcKingdom(seceded.id(), -1, NpcLevel.SMALL, new NpcNames.Name("Seceda", "Сецеда"),
                RulerCharacter.MERCHANT, List.of(RulerCharacter.Trait.PATIENT), 99, Gender.FEMALE, List.of(), KingdomWorldApi.gameTime(server), 0L));
        helper.succeedWhen(() -> {
            Optional<Kingdom> kingdom = KingdomWorldApi.kingdom(server, seceded.id());
            helper.assertTrue(kingdom.isPresent() && kingdom.get().flag() != null, "The kingdom that broke away has no flag of its own");
            helper.assertTrue(!kingdom.get().flag().layers().isEmpty(), "Its flag is plain");
            KingdomWorldApi.dissolveKingdom(server, seceded.id());
        });
    }
}
