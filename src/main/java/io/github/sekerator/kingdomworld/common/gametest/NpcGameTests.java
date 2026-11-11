package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.construction.BuildingEffects;
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
import io.github.sekerator.kingdomworld.core.army.Soldier;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.economy.TreasuryJournal;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.npc.NpcComposition;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcRules;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.SpawnStage;
import io.github.sekerator.kingdomworld.core.npc.TemplateCatalog;
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
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Game tests of the kingdoms of the world. A kingdom appears on a flat platform high above the test place (at the height
 * {@value #PLATFORM}, above any ground of the world, so that the land under it does not matter). A small capital of districts
 * rises whole: every building of its plan, its palace around the throne, its wall with the gates of the plan, citizens in their
 * houses and at work, soldiers, a treasury that agrees with its founding; every variant of a building works as its role. A
 * capital of the first layout, as old worlds have them, still rises as before. The appearance leaves the land and the buildings
 * of others alone; a restart in the middle and a second appearance of the same place make nothing twice; its market sells to a
 * player; a player becomes its subject. Every test runs in a batch of its own and takes down what it built.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcGameTests {
    static final String EMPTY = "empty";
    /** The height of the top of the platform. */
    static final int PLATFORM = 290;
    /**
     * Half the side of the platform for a small kingdom, in blocks: the fields and the posts of woodcutters and miners of a small
     * capital of districts with the groves around them, and the ramp around its levelled ground.
     */
    static final int HALF = NpcComposition.districtReach(NpcLevel.SMALL) + 10;
    /** Half the side of the platform for a large kingdom. */
    private static final int LARGE_HALF = NpcComposition.districtReach(NpcLevel.LARGE) + 10;
    /** Blocks over the platform that are cleared before and after a test: the castle of a large capital is the highest building. */
    private static final int HEIGHT = 30;
    /** Steps the appearance of a small kingdom takes at most. */
    static final int STEPS = 4_000;
    /** Steps the appearance of a large kingdom takes at most. */
    private static final int LARGE_STEPS = 12_000;
    /** Ticks of the server the appearance of a small kingdom takes at most, with the wait for its wall. */
    private static final int TICKS = STEPS + 3_000;
    private static final String SMALL_HOUSE = NpcComposition.SMALL_HOUSE;
    private static final String LARGE_HOUSE = NpcComposition.LARGE_HOUSE;

    private NpcGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Flat grass at {@value #PLATFORM} around the test place with air above, after kingdoms left near it by earlier tests are
     * dissolved; wide enough for a small kingdom.
     */
    static BlockPos platform(GameTestHelper helper) {
        return platform(helper, HALF);
    }

    /**
     * Flat grass of a size around the test place.
     *
     * @param half half the side of the platform, in blocks
     */
    static BlockPos platform(GameTestHelper helper, int half) {
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
        int top = Math.min(HEIGHT, level.getMaxBuildHeight() - 1 - PLATFORM);
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                BlockPos column = center.offset(x, 0, z);
                set(level, column.below(), dirt);
                set(level, column, grass);
                for (int y = 1; y <= top; y++) {
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
     * Takes the kingdom of a place down and clears the platform of a small kingdom.
     */
    static void cleanUp(GameTestHelper helper, NpcSite site, BlockPos center) {
        cleanUp(helper, site, center, HALF);
    }

    /**
     * Takes the kingdom of a place down and clears a platform of a size.
     */
    static void cleanUp(GameTestHelper helper, NpcSite site, BlockPos center, int half) {
        MinecraftServer server = helper.getLevel().getServer();
        if (site != null) {
            NpcWork.remove(server, site.id(), true);
        }
        int top = Math.min(HEIGHT, helper.getLevel().getMaxBuildHeight() - 1 - PLATFORM);
        GatheringGameTests.clear(helper.getLevel(), center.offset(-half, -1, -half), center.offset(half, top, half));
    }

    /**
     * A small kingdom of the world appearing on the platform, without towns.
     */
    static NpcSite plant(GameTestHelper helper, BlockPos center) {
        return plant(helper, center, NpcLevel.SMALL);
    }

    private static NpcSite plant(GameTestHelper helper, BlockPos center, NpcLevel size) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(NpcWork.state(server).spawning().isEmpty(), "Another kingdom of the world is appearing");
        NpcSite site = NpcWork.plant(server, center.getX(), center.getZ(), size, false).orElse(null);
        helper.assertTrue(site != null, "The data of the kingdoms of the world is read-only");
        helper.assertTrue(site.districts(), "A new place is not laid out in districts: " + site.layout());
        return site;
    }

    static Kingdom kingdomOf(GameTestHelper helper, NpcSite site) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID id = site.kingdomId().orElse(null);
        Kingdom kingdom = id == null ? null : KingdomWorldApi.kingdom(server, id).orElse(null);
        helper.assertTrue(kingdom != null, "The kingdom of the world did not appear: " + site + " " + site.reason());
        return kingdom;
    }

    /**
     * Raises a place to its end at once and gives its kingdom.
     */
    private static Kingdom raise(GameTestHelper helper, NpcSite site, int steps) {
        NpcSpawner.runNow(helper.getLevel().getServer(), site, steps);
        helper.assertTrue(site.status() == NpcSite.Status.SPAWNED, "The appearance did not end: " + site + " at " + site.stage().id()
                + " step " + site.step() + " " + site.reason());
        return kingdomOf(helper, site);
    }

    /**
     * The land around the capital of a place, in chunks from the chunk of the throne: what the rules give, and for a capital of
     * districts at least what its fields and outer posts take.
     */
    static int landRadius(NpcSite site) {
        int configured = NpcSettings.rules().landRadius(site.level());
        if (!site.districts()) {
            return configured;
        }
        return Math.min(NpcRules.MAX_LAND_RADIUS, Math.max(configured, (NpcSpawner.composition(site).outerReach() + 15) / 16));
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

    private static Map<String, Integer> byTemplate(List<String> templates) {
        Map<String, Integer> counts = new TreeMap<>();
        templates.forEach(template -> counts.merge(template, 1, Integer::sum));
        return counts;
    }

    /**
     * What is wrong with the buildings of a capital against its plan: every plot has exactly one building of its template whose
     * box holds the middle of the plot, and there is no other; an empty text if nothing is.
     */
    private static String plotProblem(MinecraftServer server, NpcSite site, NpcComposition composition, UUID kingdom) {
        List<Building> built = buildings(server, kingdom);
        List<String> planned = new ArrayList<>();
        composition.buildings().forEach(plot -> planned.add(plot.template()));
        List<String> standing = new ArrayList<>();
        built.forEach(building -> standing.add(building.templateId()));
        if (!byTemplate(planned).equals(byTemplate(standing))) {
            return "The buildings " + byTemplate(standing) + " are not those of the plan " + byTemplate(planned);
        }
        int y = site.groundY() + 1;
        for (NpcComposition.Plot plot : composition.buildings()) {
            int x = site.x() + plot.x();
            int z = site.z() + plot.z();
            long found = built.stream().filter(b -> b.templateId().equals(plot.template()) && b.box().contains(x, y, z)).count();
            if (found != 1) {
                return found + " building(s) of " + plot.template() + " stand on the plot at " + plot.x() + " " + plot.z();
            }
        }
        return "";
    }

    private static List<WorkPost> postsIn(MinecraftServer server, UUID kingdom, Box box) {
        List<WorkPost> list = new ArrayList<>();
        for (WorkPost post : KingdomWorldApi.state(server).citizens().posts().all()) {
            WorldPos at = post.pos();
            if (post.kingdomId().equals(kingdom) && box.contains(at.x(), at.y(), at.z())) {
                list.add(post);
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

    /**
     * Asserts that a capital of districts stands whole: every building of its plan on its plot, its wall of the radius of the
     * plan with a gate at every gate of the plan, its people in their beds and at work.
     */
    private static void assertWhole(GameTestHelper helper, NpcSite site, Kingdom kingdom, NpcComposition composition) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        String problem = plotProblem(server, site, composition, kingdom.id());
        helper.assertTrue(problem.isEmpty(), problem);
        for (Building building : buildings(server, kingdom.id())) {
            helper.assertTrue(building.integrity() == 100 || building.integrity() < 0 || building.checked() == 0,
                    building.templateId() + " is not whole: " + building.integrity());
            // Nothing hangs in the air: the ground under the floor of every building is solid.
            BlockPos under = new BlockPos(building.anchor().x(), building.box().minY() - 1, building.anchor().z());
            helper.assertTrue(!level.getBlockState(under).isAir(), building.templateId() + " stands on air at " + under.toShortString());
        }
        Wall wall = RoadWork.state(server).wallOf(0, kingdom.id()).orElse(null);
        helper.assertTrue(wall != null && wall.built(), "The wall of the capital does not stand: " + wall);
        helper.assertTrue(wall.radius() == composition.wallRadius(), "The wall has the radius " + wall.radius() + " instead of "
                + composition.wallRadius());
        helper.assertTrue(wall.wallClass() == (composition.stoneWall() ? Wall.WallClass.STONE : Wall.WallClass.PALISADE),
                "The wall is of " + wall.wallClass());
        List<WorldPos> gates = CityWalls.gates(server, kingdom.id(), 0);
        for (NpcComposition.Gate gate : composition.gates()) {
            int x = site.x() + gate.x();
            int z = site.z() + gate.z();
            WorldPos opening = gates.stream().filter(g -> Math.abs(g.x() - x) <= 2 && Math.abs(g.z() - z) <= 2).findFirst().orElse(null);
            helper.assertTrue(opening != null, "No gate at " + gate.x() + " " + gate.z() + " of the plan: " + gates);
            BlockPos passage = new BlockPos(opening.x(), opening.y(), opening.z());
            helper.assertTrue(level.getBlockState(passage).getCollisionShape(level, passage).isEmpty()
                    && level.getBlockState(passage.above()).getCollisionShape(level, passage.above()).isEmpty(),
                    "The gate at " + passage.toShortString() + " is closed by " + level.getBlockState(passage));
        }
        List<Citizen> citizens = KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdom.id());
        helper.assertTrue(citizens.size() == composition.citizens(), "Citizens " + citizens.size() + " instead of " + composition.citizens());
        Set<UUID> soldiers = new HashSet<>();
        for (Soldier soldier : ArmyWork.state(server).soldiersOf(kingdom.id())) {
            soldiers.add(soldier.citizenId());
        }
        helper.assertTrue(soldiers.size() == composition.soldiers(), "Soldiers " + soldiers.size() + " instead of " + composition.soldiers());
        int homeless = 0;
        int idle = 0;
        for (Citizen citizen : citizens) {
            if (!soldiers.contains(citizen.id()) && citizen.home() == null) {
                homeless++;
            }
            if (citizen.professionId().equals(Professions.UNEMPLOYED.id())) {
                idle++;
            }
        }
        helper.assertTrue(homeless == 0, homeless + " citizens who are not soldiers have no home");
        helper.assertTrue(idle * 2 < citizens.size(), idle + " of " + citizens.size() + " citizens are without work");
    }

    // ---------------------------------------------------------------- appearance

    @GameTest(template = EMPTY, batch = "kingdomworld_npc_appear", timeoutTicks = 200)
    public static void aSmallCapitalOfDistrictsRisesWhole(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = platform(helper);
        NpcSite site = null;
        try {
            site = plant(helper, center);
            Kingdom kingdom = raise(helper, site, STEPS);
            KingdomWorldState state = KingdomWorldApi.state(server);
            helper.assertTrue(NpcWork.isNpc(server, kingdom.id()) && kingdom.rulerId() == null, "Not a kingdom of the world without a player king");
            helper.assertTrue(level.getBlockState(NpcSpawner.thronePos(site)).is(ModRegistries.THRONE.get()), "No throne in the middle");
            int chunks = state.territory().territory(kingdom.id()).map(Territory::size).orElse(0);
            int radius = landRadius(site);
            helper.assertTrue(chunks == (2 * radius + 1) * (2 * radius + 1), "Land of " + chunks + " chunks for the radius " + radius);
            NpcComposition composition = NpcSpawner.composition(site);
            assertWhole(helper, site, kingdom, composition);
            Set<String> roles = new HashSet<>();
            buildings(server, kingdom.id()).forEach(building -> roles.add(building.role()));
            for (String role : List.of("house", "warehouse", "workshop", "market", "well", "barracks", "tower", "tavern")) {
                helper.assertTrue(roles.contains(role), "No building with the role " + role + ": " + roles);
            }
            Set<String> professions = new HashSet<>();
            state.citizens().registry().citizensOf(kingdom.id()).forEach(citizen -> professions.add(citizen.professionId()));
            for (String profession : List.of("merchant", "farmer", "warrior", "innkeeper", "lumberjack", "miner")) {
                helper.assertTrue(professions.contains(profession), "Nobody is a " + profession + ": " + professions);
            }
            // The workshops of a capital of districts are smithies, bakeries or the workshops of crafters by what stands in them.
            helper.assertTrue(professions.contains("crafter") || professions.contains("smith") || professions.contains("cook"),
                    "Nobody works in a workshop: " + professions);
            helper.assertTrue(!ArmyWork.state(server).squadsOf(kingdom.id()).isEmpty()
                    && ArmyWork.state(server).squadsOf(kingdom.id()).get(0).order().anchor().isPresent(), "The soldiers have no order");
            long money = NpcSettings.rules().money(NpcLevel.SMALL);
            Treasury treasury = KingdomWorldApi.treasury(server, kingdom.id()).orElseThrow();
            helper.assertTrue(treasury.balance() == money && founding(server, kingdom.id()) == money,
                    "The treasury " + treasury.balance() + " does not agree with its founding " + founding(server, kingdom.id()));
            helper.assertTrue(KingdomStorage.count(server, kingdom.id(), stack -> stack.is(Items.BREAD)) > 0, "No bread in the storehouses");
            helper.succeed();
        } finally {
            cleanUp(helper, site, center);
        }
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_npc_palace", timeoutTicks = 200)
    public static void thePalaceOfASmallCapitalStandsAroundTheThrone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = platform(helper);
        NpcSite site = null;
        try {
            site = plant(helper, center);
            Kingdom kingdom = raise(helper, site, STEPS);
            NpcComposition composition = NpcSpawner.composition(site);
            BlockPos throne = NpcSpawner.thronePos(site);
            helper.assertTrue(throne.getX() == site.x() && throne.getZ() == site.z() && level.getBlockState(throne).is(ModRegistries.THRONE.get()),
                    "No throne at the place " + site.x() + " " + site.z());
            String palace = composition.buildings().get(0).template();
            helper.assertTrue(palace.equals("kingdomworld:great_hall"), "The palace of a small capital is " + palace);
            Building hall = buildings(server, kingdom.id()).stream().filter(b -> b.templateId().equals(palace)).findFirst().orElse(null);
            helper.assertTrue(hall != null, "The great hall does not stand");
            helper.assertTrue(hall.box().contains(throne.getX(), throne.getY(), throne.getZ()), "The throne " + throne.toShortString()
                    + " is not inside the great hall " + hall.box());
            helper.assertTrue(level.getBlockState(throne.above()).isAir(), "Something stands on the throne: " + level.getBlockState(throne.above()));
            boolean roof = false;
            for (int y = throne.getY() + 2; y <= hall.box().maxY() && !roof; y++) {
                roof = !level.getBlockState(new BlockPos(throne.getX(), y, throne.getZ())).isAir();
            }
            helper.assertTrue(roof, "No roof of the palace over the throne");
            NpcComposition.Post guards = composition.post("palace_hall").orElse(null);
            helper.assertTrue(guards != null && guards.points().size() == 2, "The hall of the palace has no posts of guards: " + guards);
            for (int[] point : guards.points()) {
                BlockPos feet = new BlockPos(site.x() + point[0], throne.getY(), site.z() + point[1]);
                helper.assertTrue(hall.box().contains(feet.getX(), feet.getY(), feet.getZ()), "The guard of the hall stands outside it at "
                        + feet.toShortString());
                helper.assertTrue(level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir(),
                        "The guard of the hall at " + feet.toShortString() + " has no room: " + level.getBlockState(feet) + " "
                                + level.getBlockState(feet.above()));
                helper.assertTrue(level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP),
                        "The guard of the hall at " + feet.toShortString() + " has no floor: " + level.getBlockState(feet.below()));
            }
            helper.succeed();
        } finally {
            cleanUp(helper, site, center);
        }
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_npc_variants", timeoutTicks = 200)
    public static void everyVariantOfABuildingWorksAsItsRole(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = platform(helper);
        NpcSite site = null;
        try {
            site = plant(helper, center);
            Kingdom kingdom = raise(helper, site, STEPS);
            UUID id = kingdom.id();
            BuildingEffects.changed();
            Set<String> working = Set.of("house", "workshop", "warehouse", "barracks", "tavern", "market");
            Set<String> seen = new HashSet<>();
            for (Building building : buildings(server, id)) {
                String role = building.role();
                seen.add(role);
                if (working.contains(role)) {
                    BuildingEffects.Effect effect = BuildingEffects.of(server, building).orElse(null);
                    helper.assertTrue(effect != null && effect.works(), building.templateId() + " at " + building.anchor() + " does not work as a "
                            + role + ": " + (effect == null ? "-" : effect.status()));
                }
                String expected = switch (role) {
                    case "workshop" -> building.templateId().equals("kingdomworld:smithy") ? "smith"
                            : building.templateId().equals("kingdomworld:bakery") ? "cook" : "crafter";
                    case "market" -> "merchant";
                    case "tavern" -> "innkeeper";
                    default -> "";
                };
                if (expected.isEmpty()) {
                    continue;
                }
                List<WorkPost> posts = postsIn(server, id, building.box());
                helper.assertTrue(!posts.isEmpty(), building.templateId() + " has no post of work");
                for (WorkPost post : posts) {
                    helper.assertTrue(post.professionId().equals(expected), "The post in " + building.templateId() + " is for "
                            + post.professionId() + " instead of " + expected);
                }
            }
            helper.assertTrue(seen.containsAll(working), "Roles of the capital: " + seen);
            // Citizens live in the houses of other kinds as well as in the first two.
            Set<String> homes = new HashSet<>();
            for (Citizen citizen : KingdomWorldApi.state(server).citizens().registry().citizensOf(id)) {
                WorldPos home = citizen.home();
                if (home == null) {
                    continue;
                }
                for (Building building : buildings(server, id)) {
                    if (building.role().equals("house") && building.box().contains(home.x(), home.y(), home.z())) {
                        homes.add(building.templateId());
                    }
                }
            }
            boolean variants = NpcSpawner.composition(site).buildings().stream().anyMatch(plot -> TemplateCatalog.get(plot.template())
                    .map(entry -> entry.role().equals("house")).orElse(false)
                    && !plot.template().equals(SMALL_HOUSE) && !plot.template().equals(LARGE_HOUSE));
            helper.assertTrue(variants, "The plan has no houses of other kinds");
            helper.assertTrue(homes.stream().anyMatch(home -> !home.equals(SMALL_HOUSE) && !home.equals(LARGE_HOUSE)),
                    "Nobody lives in a house of another kind: " + homes);
            helper.succeed();
        } finally {
            cleanUp(helper, site, center);
        }
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_npc_grid", timeoutTicks = 200)
    public static void aKingdomOfTheFirstLayoutStillAppearsAsBefore(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = platform(helper);
        NpcSite site = null;
        try {
            helper.assertTrue(NpcWork.state(server).spawning().isEmpty(), "Another kingdom of the world is appearing");
            site = NpcWork.planGridAt(server, center.getX(), center.getZ(), NpcLevel.SMALL, List.of()).orElse(null);
            helper.assertTrue(site != null, "The data of the kingdoms of the world is read-only");
            helper.assertTrue(site.layout() == NpcSite.LAYOUT_GRID && !site.districts(), "Not a place of the first layout: " + site.layout());
            Kingdom kingdom = raise(helper, site, STEPS);
            KingdomWorldState state = KingdomWorldApi.state(server);
            helper.assertTrue(level.getBlockState(NpcSpawner.thronePos(site)).is(ModRegistries.THRONE.get()), "No throne in the middle");
            int chunks = state.territory().territory(kingdom.id()).map(Territory::size).orElse(0);
            int radius = NpcSettings.rules().landRadius(NpcLevel.SMALL);
            helper.assertTrue(chunks == (2 * radius + 1) * (2 * radius + 1), "Land of " + chunks + " chunks");
            NpcComposition composition = NpcSpawner.composition(site);
            helper.assertTrue(!composition.districts(), "The composition is not that of the first layout");
            List<Building> built = buildings(server, kingdom.id());
            helper.assertTrue(built.size() == composition.buildings().size(), "Buildings " + built.size() + " of " + composition.buildings().size());
            Set<String> roles = new HashSet<>();
            built.forEach(building -> roles.add(building.role()));
            for (String role : List.of("house", "warehouse", "workshop", "market", "well", "barracks", "tower")) {
                helper.assertTrue(roles.contains(role), "No building with the role " + role + ": " + roles);
            }
            for (Building building : built) {
                helper.assertTrue(TemplateCatalog.get(building.templateId()).map(entry -> !entry.upgrade()).orElse(false)
                        && composition.buildingsOf(building.templateId()) > 0, building.templateId() + " is not of the first layout");
                BlockPos under = new BlockPos(building.anchor().x(), building.anchor().y() - 1, building.anchor().z());
                helper.assertTrue(!level.getBlockState(under).isAir(), building.templateId() + " stands on air at " + under.toShortString());
            }
            List<Citizen> citizens = state.citizens().registry().citizensOf(kingdom.id());
            int beds = 0;
            for (NpcComposition.Plot plot : composition.buildings()) {
                beds += NpcComposition.beds(plot.template());
            }
            helper.assertTrue(citizens.size() == beds, "Citizens " + citizens.size() + " for " + beds + " beds");
            int homeless = 0;
            for (Citizen citizen : citizens) {
                homeless += citizen.home() == null ? 1 : 0;
            }
            helper.assertTrue(homeless == 0, homeless + " citizens without a home");
            int soldiers = ArmyWork.state(server).soldiersOf(kingdom.id()).size();
            helper.assertTrue(soldiers == NpcSettings.rules().soldiers(NpcLevel.SMALL), "Soldiers: " + soldiers);
            long money = NpcSettings.rules().money(NpcLevel.SMALL);
            helper.assertTrue(KingdomWorldApi.treasury(server, kingdom.id()).orElseThrow().balance() == money
                    && founding(server, kingdom.id()) == money, "The treasury does not agree with its founding");
            Wall wall = RoadWork.state(server).wallOf(0, kingdom.id()).orElse(null);
            helper.assertTrue(wall != null && wall.wallClass() == Wall.WallClass.PALISADE && wall.built(), "No palisade around the village");
            helper.succeed();
        } finally {
            cleanUp(helper, site, center);
        }
    }

    /**
     * A large capital rises whole with its castle around the throne and a gate on every side.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_npc_large", timeoutTicks = 200)
    public static void aLargeCapitalRisesWholeWithItsCastle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = platform(helper, LARGE_HALF);
        NpcSite site = null;
        try {
            site = plant(helper, center, NpcLevel.LARGE);
            long started = System.nanoTime();
            Kingdom kingdom = raise(helper, site, LARGE_STEPS);
            long millis = (System.nanoTime() - started) / 1_000_000;
            NpcComposition composition = NpcSpawner.composition(site);
            helper.assertTrue(composition.buildings().size() >= 60, "A large capital of " + composition.buildings().size() + " buildings");
            helper.assertTrue(composition.gates().size() == 4, "Gates of the plan: " + composition.gates().size());
            assertWhole(helper, site, kingdom, composition);
            BlockPos throne = NpcSpawner.thronePos(site);
            helper.assertTrue(level.getBlockState(throne).is(ModRegistries.THRONE.get()), "No throne in the castle");
            Building castle = buildings(server, kingdom.id()).stream().filter(b -> b.templateId().equals("kingdomworld:castle")).findFirst()
                    .orElse(null);
            helper.assertTrue(castle != null && castle.box().contains(throne.getX(), throne.getY(), throne.getZ()),
                    "The throne is not inside the castle: " + (castle == null ? "no castle" : castle.box()));
            helper.assertTrue(level.getBlockState(throne.above()).isAir(), "Something stands on the throne");
            com.mojang.logging.LogUtils.getLogger().info("A large capital of {} buildings rose in {} ms", composition.buildings().size(), millis);
            helper.succeed();
        } finally {
            cleanUp(helper, site, center, LARGE_HALF);
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

    @GameTest(template = EMPTY, batch = "kingdomworld_npc_town", timeoutTicks = 200)
    public static void aTownWherePlayersSpentHoursIsLeftOutAndTheirBlocksStay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = platform(helper);
        // A place for a town east of the capital, where a player spent hours and left a chest on planks.
        BlockPos town = new BlockPos(center.getX() + 130, PLATFORM, center.getZ());
        ChunkPos townChunk = new ChunkPos(town);
        BlockPos planks = town.offset(3, 1, 2);
        NpcSite site = null;
        try {
            level.setBlockAndUpdate(planks, Blocks.OAK_PLANKS.defaultBlockState());
            level.setBlockAndUpdate(planks.above(), Blocks.CHEST.defaultBlockState());
            level.getChunk(townChunk.x, townChunk.z).setInhabitedTime(10L * 60 * 60 * 20);
            helper.assertTrue(NpcWork.state(server).spawning().isEmpty(), "Another kingdom of the world is appearing");
            site = NpcWork.plantAt(server, center.getX(), center.getZ(), NpcLevel.SMALL, List.of(new int[]{town.getX(), town.getZ()}))
                    .orElse(null);
            helper.assertTrue(site != null && site.towns().size() == 1, "The place has no town to check");
            NpcSpawner.runNow(server, site, STEPS);
            helper.assertTrue(site.status() == NpcSite.Status.SPAWNED, "The capital did not appear: " + site + " " + site.reason());
            helper.assertTrue(site.towns().isEmpty(), "The town where players spent hours was kept");
            Kingdom kingdom = kingdomOf(helper, site);
            helper.assertTrue(!kingdom.id().equals(KingdomWorldApi.ownerOfChunk(level, townChunk.x, townChunk.z)), "The land of the town was claimed");
            helper.assertTrue(RoadWork.state(server).citiesOf(kingdom.id()).isEmpty(), "A town hall was founded");
            helper.assertTrue(level.getBlockState(planks).is(Blocks.OAK_PLANKS) && level.getBlockState(planks.above()).is(Blocks.CHEST),
                    "The blocks of the player were touched");
            helper.succeed();
        } finally {
            level.getChunk(townChunk.x, townChunk.z).setInhabitedTime(0);
            level.setBlock(planks.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            level.setBlock(planks, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
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
            NpcComposition planned = NpcSpawner.composition(site);
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
            helper.assertTrue(copy.layout() == site.layout() && copy.style().equals(site.style()) && copy.wood().equals(site.wood())
                            && copy.groundY() == site.groundY(),
                    "The look of the place did not survive saving: " + copy.layout() + " " + copy.style() + " " + copy.wood());
            helper.assertTrue(NpcSpawner.composition(copy).buildings().equals(planned.buildings()), "The plan changed after saving");
            NpcSpawner.resetForTests();
            Kingdom kingdom = raise(helper, site, STEPS);
            NpcComposition composition = NpcSpawner.composition(site);
            helper.assertTrue(composition.buildings().equals(planned.buildings()), "The plan changed after the restart");
            String problem = plotProblem(server, site, composition, kingdom.id());
            helper.assertTrue(problem.isEmpty(), "After the restart: " + problem);
            int buildings = buildings(server, kingdom.id()).size();
            int citizens = KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdom.id()).size();
            helper.assertTrue(citizens == composition.citizens(), "Citizens after the restart: " + citizens);
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

    /**
     * A kingdom an operator calls for appears by the ticks of the server while nobody is online: it keeps the full budget (the
     * small one of the background waits for players) and comes to its end.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_npc_ticks", timeoutTicks = TICKS + 100)
    public static void aKingdomCalledByAnOperatorAppearsOnAnEmptyServer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = platform(helper);
        NpcSite site = plant(helper, center);
        long started = server.getTickCount();
        helper.onEachTick(() -> {
            String problem = null;
            if (site.status() == NpcSite.Status.SPAWNING) {
                if (NpcSpawner.inBackground()) {
                    problem = "The appearance went on with the small budget of the background with nobody online, at " + site.stage().id();
                } else if (server.getTickCount() - started > TICKS) {
                    problem = "The appearance did not end in " + TICKS + " ticks: " + site.stage().id() + " step " + site.step();
                } else {
                    return;
                }
            } else if (site.status() != NpcSite.Status.SPAWNED) {
                problem = "The appearance did not end: " + site + " at " + site.stage().id() + " " + site.reason();
            } else if (!level.getBlockState(NpcSpawner.thronePos(site)).is(ModRegistries.THRONE.get())) {
                problem = "No throne once the kingdom appeared";
            }
            cleanUp(helper, site, center);
            if (problem != null) {
                helper.fail(problem);
            }
            helper.succeed();
        });
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
