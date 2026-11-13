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
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.economy.TreasuryJournal;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.npc.ArchitecturalStyles;
import io.github.sekerator.kingdomworld.core.npc.GuildHouses;
import io.github.sekerator.kingdomworld.core.npc.NpcComposition;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcRules;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.SpawnStage;
import io.github.sekerator.kingdomworld.core.npc.StyledPlan;
import io.github.sekerator.kingdomworld.core.npc.TemplateCatalog;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * player; a player becomes its subject. A capital in an architectural style, as new worlds have them, rises whole with the
 * castle of its style around the throne, the institutions on its square and their workers at their places, the posts of its
 * castle, and goes on after a restart in the middle of its castle with the same plan and looks; no two kingdoms of a world
 * share a style while one is free. Every test runs in a batch of its own and takes down what it built.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcGameTests {
    static final String EMPTY = "empty";
    /** The height of the top of the platform: the highest castle and guild house of a style (48 layers) fit above it. */
    static final int PLATFORM = 230;
    /**
     * Half the side of the platform for a small kingdom, in blocks: the fields and the posts of woodcutters and miners of a small
     * capital of districts with the groves around them, and the ramp around its levelled ground.
     */
    static final int HALF = NpcComposition.districtReach(NpcLevel.SMALL) + 10;
    /** Half the side of the platform for a large kingdom. */
    static final int LARGE_HALF = NpcComposition.districtReach(NpcLevel.LARGE) + 10;
    /** Half the side of the platform for a small styled kingdom. */
    static final int HALF3 = NpcComposition.capitalReach(NpcLevel.SMALL, NpcSite.LAYOUT_STYLED) + 10;
    /** Half the side of the platform for a large styled kingdom. */
    static final int LARGE_HALF3 = NpcComposition.capitalReach(NpcLevel.LARGE, NpcSite.LAYOUT_STYLED) + 10;
    /**
     * Blocks over the platform that are cleared before and after a test: the castles and the great guild houses of the styles
     * are the highest buildings, 48 layers from the one under the street.
     */
    private static final int HEIGHT = 56;
    /** Steps the appearance of a small kingdom takes at most. */
    static final int STEPS = 4_000;
    /** Steps the appearance of a large kingdom takes at most. */
    static final int LARGE_STEPS = 12_000;
    /** Steps the appearance of a small styled kingdom takes at most: its castle and its square are larger. */
    static final int STYLED_STEPS = 8_000;
    /** Steps the appearance of a large styled kingdom takes at most. */
    static final int LARGE_STYLED_STEPS = 24_000;
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
     * A small kingdom of the world of districts appearing on the platform, without towns.
     */
    static NpcSite plant(GameTestHelper helper, BlockPos center) {
        return plant(helper, center, NpcLevel.SMALL);
    }

    /**
     * A kingdom of the world of districts ({@link NpcSite#LAYOUT_DISTRICTS}) of a size appearing on the platform, without towns:
     * the layout of the worlds made before the architectural styles, whose numbers the tests of the platform of {@link #HALF}
     * keep.
     */
    static NpcSite plant(GameTestHelper helper, BlockPos center, NpcLevel size) {
        return plant(helper, center, size, NpcSite.LAYOUT_DISTRICTS, "");
    }

    /**
     * A styled kingdom of the world ({@link NpcSite#LAYOUT_STYLED}) of a size appearing on the platform of {@link #HALF3} or
     * {@link #LARGE_HALF3}, without towns.
     *
     * @param style its architectural style, or empty for the one the plan would choose; the styles held by the kingdoms other
     *              tests left in the world change that choice, so a test that needs a style names it
     */
    static NpcSite plantStyled(GameTestHelper helper, BlockPos center, NpcLevel size, String style) {
        return plant(helper, center, size, NpcSite.LAYOUT_STYLED, style);
    }

    private static NpcSite plant(GameTestHelper helper, BlockPos center, NpcLevel size, int layout, String style) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(NpcWork.state(server).spawning().isEmpty(), "Another kingdom of the world is appearing");
        NpcSite site = NpcWork.plantAt(server, center.getX(), center.getZ(), size, List.of(), layout, style).orElse(null);
        helper.assertTrue(site != null, "The data of the kingdoms of the world is read-only");
        helper.assertTrue(site.layout() == layout && site.districts(), "The place is not of the layout " + layout + ": " + site.layout());
        if (layout == NpcSite.LAYOUT_STYLED) {
            helper.assertTrue(ArchitecturalStyles.known(site.style()) && (style.isEmpty() || site.style().equals(style)),
                    "The styled place has the style '" + site.style() + "'");
            helper.assertTrue(GuildHouses.parse(site.guildRank()).isPresent(), "The styled place keeps no rank of its guild: '"
                    + site.guildRank() + "'");
        }
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
    static Kingdom raise(GameTestHelper helper, NpcSite site, int steps) {
        NpcSpawner.runNow(helper.getLevel().getServer(), site, steps);
        helper.assertTrue(site.status() == NpcSite.Status.SPAWNED, "The appearance did not end: " + site + " at " + site.stage().id()
                + " step " + site.step() + " " + site.reason());
        return kingdomOf(helper, site);
    }

    /**
     * The land around the capital of a place, in chunks from the chunk of the throne: what the rules give, and for a capital of
     * districts at least what its fields and outer posts take.
     */
    /**
     * The soldiers of the royal army of a kingdom who came from abroad and live beyond its capital: they are not of the plan of
     * the capital.
     */
    static int fromAbroad(MinecraftServer server, UUID kingdomId) {
        int army = ArmyWork.state(server).branchOf(kingdomId, io.github.sekerator.kingdomworld.core.army.Branch.ARMY).size();
        // A capital of districts plans its reserve of the army; a capital of the first layout plans none.
        int planned = NpcWork.state(server).kingdom(kingdomId).flatMap(npc -> NpcWork.state(server).site(npc.siteId()))
                .filter(NpcSite::districts).map(site -> NpcSettings.rules().army(site.level())).orElse(0);
        return Math.max(0, army - planned);
    }

    /**
     * The adventurers and mages of a kingdom of the world who came from abroad, at least and at most: every adventurer came, and
     * of the mages those the capital did not hire among its own people.
     */
    static int[] guildsFromAbroad(MinecraftServer server, UUID kingdomId) {
        int adventurers = 0;
        int mages = 0;
        for (Citizen citizen : KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdomId)) {
            adventurers += citizen.professionId().equals(Professions.ADVENTURER.id()) ? 1 : 0;
            mages += citizen.professionId().equals(Professions.MAGE.id()) ? 1 : 0;
        }
        return new int[]{adventurers, adventurers + mages};
    }

    /**
     * Whether a kingdom of the world has the citizens it was planned for: those it has without the royal army and the people of
     * the guilds from abroad.
     */
    static boolean plannedPeople(MinecraftServer server, UUID kingdomId, int citizens, int planned) {
        int[] guilds = guildsFromAbroad(server, kingdomId);
        int rest = citizens - fromAbroad(server, kingdomId);
        return rest - guilds[1] <= planned && planned <= rest - guilds[0];
    }

    static String guildsText(MinecraftServer server, UUID kingdomId) {
        int[] guilds = guildsFromAbroad(server, kingdomId);
        return guilds[0] + " adventurers and " + (guilds[1] - guilds[0]) + " mages";
    }

    /**
     * Asserts that the guilds of a raised kingdom of the world have their ranks and people: the adventurers of the rank of the
     * branch with a head of the guild of that rank, the mages and the researches of the rank of the guild of mages.
     */
    static void assertGuilds(GameTestHelper helper, Kingdom kingdom, io.github.sekerator.kingdomworld.core.guild.Rank low,
                             io.github.sekerator.kingdomworld.core.guild.Rank high) {
        MinecraftServer server = helper.getLevel().getServer();
        io.github.sekerator.kingdomworld.core.npc.GuildRanks.Ranks ranks = io.github.sekerator.kingdomworld.common.npc.NpcGuilds.ranks(server, kingdom.id());
        for (io.github.sekerator.kingdomworld.core.guild.Rank rank : List.of(ranks.adventurers(), ranks.mages())) {
            helper.assertTrue(rank.atLeast(low) && high.atLeast(rank), "A guild of rank " + rank.label() + " outside " + low.label() + "-" + high.label());
        }
        io.github.sekerator.kingdomworld.core.guild.GuildRules guildRules = io.github.sekerator.kingdomworld.common.guild.GuildSettings.rules();
        io.github.sekerator.kingdomworld.core.magic.MagicRules magicRules = io.github.sekerator.kingdomworld.common.magic.MagicSettings.rules();
        int adventurers = 0;
        int mages = 0;
        for (Citizen citizen : KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdom.id())) {
            adventurers += citizen.professionId().equals(Professions.ADVENTURER.id()) ? 1 : 0;
            mages += citizen.professionId().equals(Professions.MAGE.id()) ? 1 : 0;
        }
        helper.assertTrue(adventurers == guildRules.worldAdventurers(ranks.adventurers()), adventurers + " adventurers in a branch of rank "
                + ranks.adventurers().label());
        Optional<Citizen> head = io.github.sekerator.kingdomworld.common.guild.GuildWork.head(server, kingdom.id());
        helper.assertTrue(head.isPresent(), "The branch of rank " + ranks.adventurers().label() + " has no head");
        io.github.sekerator.kingdomworld.core.guild.Rank headRank = io.github.sekerator.kingdomworld.common.guild.GuildWork.state(server)
                .hireling(head.get().id()).orElseThrow().rank();
        helper.assertTrue(headRank == ranks.adventurers(), "The head of the guild is of rank " + headRank.label());
        helper.assertTrue(mages >= magicRules.worldMages(ranks.mages()), mages + " mages in a guild of rank " + ranks.mages().label());
        int researches = io.github.sekerator.kingdomworld.common.magic.MagicWork.state(server).towerLevel(kingdom.id());
        helper.assertTrue(researches >= magicRules.worldResearch(ranks.mages()), researches + " researches in a guild of rank " + ranks.mages().label());
    }

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
        int abroad = fromAbroad(server, kingdom.id());
        helper.assertTrue(plannedPeople(server, kingdom.id(), citizens.size(), composition.citizens()), "Citizens " + citizens.size() + " ("
                + abroad + " of the royal army from abroad, " + guildsText(server, kingdom.id()) + ") instead of " + composition.citizens());
        Set<UUID> soldiers = new HashSet<>();
        for (Soldier soldier : ArmyWork.state(server).soldiersOf(kingdom.id())) {
            soldiers.add(soldier.citizenId());
        }
        helper.assertTrue(soldiers.size() - abroad == composition.soldiers(), "Soldiers " + soldiers.size() + " (" + abroad
                + " of the royal army from abroad) instead of " + composition.soldiers());
        int homeless = 0;
        int idle = 0;
        for (Citizen citizen : citizens) {
            if (!soldiers.contains(citizen.id()) && citizen.home() == null && !io.github.sekerator.kingdomworld.common.npc.NpcGuilds.lodged(server, citizen)) {
                homeless++;
            }
            if (citizen.professionId().equals(Professions.UNEMPLOYED.id())) {
                idle++;
            }
        }
        helper.assertTrue(homeless == 0, homeless + " citizens who are not soldiers have no home");
        helper.assertTrue(idle * 2 < citizens.size(), idle + " of " + citizens.size() + " citizens are without work");
    }

    /**
     * Asserts that every building of a kingdom with a role that works does work as its role, and that the posts in its
     * workshops, markets and taverns are for the profession their blocks call for: a smithy of any style for a smith, a bakery
     * for a cook, other workshops for crafters.
     */
    private static void assertRolesWork(GameTestHelper helper, MinecraftServer server, UUID id) {
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
                // The smithies of the styles have the anvil and the furnace of the plain one, the bakery its oven.
                case "workshop" -> building.templateId().endsWith("smithy") ? "smith"
                        : building.templateId().endsWith("bakery") ? "cook" : "crafter";
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
    }

    // ---------------------------------------------------------------- styled capitals

    private static Optional<TemplateCatalog.Kind> kindOf(String template) {
        return TemplateCatalog.get(template).map(TemplateCatalog.Entry::kind);
    }

    /**
     * The buildings of a kind that stand in a kingdom.
     */
    private static List<Building> ofKind(MinecraftServer server, UUID kingdom, TemplateCatalog.Kind kind) {
        List<Building> list = new ArrayList<>();
        for (Building building : buildings(server, kingdom)) {
            if (kindOf(building.templateId()).filter(kind::equals).isPresent()) {
                list.add(building);
            }
        }
        return list;
    }

    /**
     * Where a post of the plan of a capital puts the feet of who stands there: {x, z} on the street, {x, z, y} y layers above it.
     */
    static BlockPos feet(NpcSite site, int[] point) {
        return new BlockPos(site.x() + point[0], site.groundY() + 1 + (point.length > 2 ? point[2] : 0), site.z() + point[1]);
    }

    /**
     * Whether one can stand at a place: nothing to bump into at the feet but a carpet and nothing at the head, a floor under
     * the feet.
     */
    private static boolean standable(ServerLevel level, BlockPos feet) {
        VoxelShape at = level.getBlockState(feet).getCollisionShape(level, feet);
        boolean low = at.isEmpty() || at.max(Direction.Axis.Y) <= 0.125;
        boolean head = level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
        boolean floor = !at.isEmpty() || level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP);
        return low && head && floor;
    }

    /**
     * Asserts that the castle of the style and size of a capital is its first plot, at the throne, and stands around the
     * throne with a roof over it; gives the castle.
     */
    private static Building assertCastle(GameTestHelper helper, NpcSite site, Kingdom kingdom, NpcComposition composition) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        String castle = TemplateCatalog.ofStyle(site.style(), TemplateCatalog.Kind.CASTLE).get(site.level().ordinal()).id();
        NpcComposition.Plot first = composition.buildings().get(0);
        helper.assertTrue(first.template().equals(castle) && first.x() == 0 && first.z() == 0 && first.facing() == 0,
                "The first plot of the plan is " + first + " instead of " + castle + " at the throne");
        BlockPos throne = NpcSpawner.thronePos(site);
        helper.assertTrue(level.getBlockState(throne).is(ModRegistries.THRONE.get()), "No throne in the castle: " + level.getBlockState(throne));
        Building building = buildings(server, kingdom.id()).stream().filter(b -> b.templateId().equals(castle)
                && b.box().contains(throne.getX(), throne.getY(), throne.getZ())).findFirst().orElse(null);
        helper.assertTrue(building != null, "The castle " + castle + " does not stand around the throne " + throne.toShortString());
        helper.assertTrue(level.getBlockState(throne.above()).isAir(), "Something stands on the throne: " + level.getBlockState(throne.above()));
        boolean roof = false;
        for (int y = throne.getY() + 2; y <= building.box().maxY() && !roof; y++) {
            roof = !level.getBlockState(new BlockPos(throne.getX(), y, throne.getZ())).isAir();
        }
        helper.assertTrue(roof, "No roof of the castle over the throne");
        return building;
    }

    /**
     * Asserts that a post of the castle is in the plan with at least so many points, every one inside the ground of the castle.
     */
    private static NpcComposition.Post assertCastlePost(GameTestHelper helper, NpcComposition composition, Building castle, NpcSite site,
                                                        String name, int least) {
        NpcComposition.Post post = composition.post(name).orElse(null);
        helper.assertTrue(post != null && post.points().size() >= least, "The castle has no post " + name + " of " + least
                + " point(s) or more: " + post);
        for (int[] point : post.points()) {
            BlockPos at = feet(site, point);
            helper.assertTrue(castle.box().minX() <= at.getX() && at.getX() <= castle.box().maxX() && castle.box().minZ() <= at.getZ()
                    && at.getZ() <= castle.box().maxZ(), "The post " + name + " at " + at.toShortString() + " lies outside the castle "
                    + castle.box());
        }
        return post;
    }

    /**
     * Asserts that the posts of work of a profession inside the buildings of a kind (and of a template ending so, if given) have
     * that profession and a worker at them.
     */
    private static void assertWorkersIn(GameTestHelper helper, MinecraftServer server, UUID kingdom, TemplateCatalog.Kind kind, String ending,
                                        String profession) {
        List<Building> standing = ofKind(server, kingdom, kind);
        standing.removeIf(building -> !building.templateId().endsWith(ending));
        helper.assertTrue(!standing.isEmpty(), "No building of the kind " + kind + " ending with '" + ending + "' stands");
        for (Building building : standing) {
            List<WorkPost> posts = postsIn(server, kingdom, building.box());
            boolean staffed = posts.stream().anyMatch(post -> post.professionId().equals(profession) && !post.workers().isEmpty());
            StringBuilder found = new StringBuilder();
            posts.forEach(post -> found.append(' ').append(post.professionId()).append('/').append(post.workers().size()).append(" of ")
                    .append(post.seats()));
            helper.assertTrue(staffed, building.templateId() + " has no " + profession + " at work; its posts:" + found);
        }
    }

    /**
     * Whether two lists of points hold the same points in the same order.
     */
    private static boolean samePoints(List<int[]> a, List<int[]> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!Arrays.equals(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
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
            // The guilds of a small kingdom are of rank B or A, with their adventurers, mages and researches.
            assertGuilds(helper, kingdom, io.github.sekerator.kingdomworld.core.guild.Rank.B, io.github.sekerator.kingdomworld.core.guild.Rank.A);
            assertMagicWithoutPlayers(helper, kingdom);
            helper.succeed();
        } finally {
            cleanUp(helper, site, center);
        }
    }

    /**
     * A day of the guilds of a kingdom of the world without any player: its adventurers bring the crystals of their hunts to
     * its guild of mages, which puts the ward over the throne and heals, paid by that energy.
     */
    private static void assertMagicWithoutPlayers(GameTestHelper helper, Kingdom kingdom) {
        MinecraftServer server = helper.getLevel().getServer();
        io.github.sekerator.kingdomworld.core.magic.MagicState magic = io.github.sekerator.kingdomworld.common.magic.MagicWork.state(server);
        long day = TreasuryActions.day(server);
        io.github.sekerator.kingdomworld.common.npc.NpcGuilds.day(server, day);
        UUID id = kingdom.id();
        helper.assertTrue(magic.isOn(id, io.github.sekerator.kingdomworld.core.magic.KingdomSpell.WARD)
                && magic.find(id).map(found -> found.ward().size()).orElse(0) > 0, "The guild of mages keeps no ward over the throne");
        helper.assertTrue(magic.isOn(id, io.github.sekerator.kingdomworld.core.magic.KingdomSpell.HEALING), "The guild of mages does not heal");
        Map<io.github.sekerator.kingdomworld.core.magic.EnergyCategory, io.github.sekerator.kingdomworld.core.magic.EnergyJournal.Totals> today =
                magic.find(id).orElseThrow().journal().totals(day, io.github.sekerator.kingdomworld.core.magic.EnergyJournal.Period.TODAY);
        long spells = today.getOrDefault(io.github.sekerator.kingdomworld.core.magic.EnergyCategory.SPELLS,
                io.github.sekerator.kingdomworld.core.magic.EnergyJournal.Totals.ZERO).expense();
        long crystals = today.getOrDefault(io.github.sekerator.kingdomworld.core.magic.EnergyCategory.CRYSTALS,
                io.github.sekerator.kingdomworld.core.magic.EnergyJournal.Totals.ZERO).income();
        helper.assertTrue(spells > 0, "The ward was not paid with energy: " + today);
        helper.assertTrue(crystals > 0, "The adventurers brought no crystals home: " + today);
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
            assertRolesWork(helper, server, id);
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
            helper.assertTrue(plannedPeople(server, kingdom.id(), citizens.size(), beds), "Citizens " + citizens.size() + " ("
                    + guildsText(server, kingdom.id()) + ") for " + beds + " beds");
            int homeless = 0;
            for (Citizen citizen : citizens) {
                // The royal army from abroad lives in the tents of its camps.
                homeless += citizen.home() == null && !io.github.sekerator.kingdomworld.common.npc.NpcCamps.quartered(server, citizen.id())
                        && !io.github.sekerator.kingdomworld.common.npc.NpcGuilds.lodged(server, citizen) ? 1 : 0;
            }
            helper.assertTrue(homeless == 0, homeless + " citizens without a home");
            int soldiers = ArmyWork.state(server).soldiersOf(kingdom.id()).size();
            helper.assertTrue(soldiers - fromAbroad(server, kingdom.id()) == NpcSettings.rules().soldiers(NpcLevel.SMALL), "Soldiers: "
                    + soldiers + " (" + fromAbroad(server, kingdom.id()) + " of the royal army from abroad)");
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
            // The guilds of a large kingdom are of rank S, or SS for the strongest kingdom of the world.
            assertGuilds(helper, kingdom, io.github.sekerator.kingdomworld.core.guild.Rank.S, io.github.sekerator.kingdomworld.core.guild.Rank.SS);
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
            site = NpcWork.plantAt(server, center.getX(), center.getZ(), NpcLevel.SMALL, List.of(new int[]{town.getX(), town.getZ()}),
                    NpcSite.LAYOUT_DISTRICTS, "").orElse(null);
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
                            && copy.groundY() == site.groundY() && copy.guildRank().equals(site.guildRank()),
                    "The look of the place did not survive saving: " + copy.layout() + " " + copy.style() + " " + copy.wood() + " '"
                            + copy.guildRank() + "'");
            helper.assertTrue(NpcSpawner.composition(copy).buildings().equals(planned.buildings()), "The plan changed after saving");
            NpcSpawner.resetForTests();
            Kingdom kingdom = raise(helper, site, STEPS);
            NpcComposition composition = NpcSpawner.composition(site);
            helper.assertTrue(composition.buildings().equals(planned.buildings()), "The plan changed after the restart");
            String problem = plotProblem(server, site, composition, kingdom.id());
            helper.assertTrue(problem.isEmpty(), "After the restart: " + problem);
            int buildings = buildings(server, kingdom.id()).size();
            int citizens = KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdom.id()).size();
            helper.assertTrue(plannedPeople(server, kingdom.id(), citizens, composition.citizens()), "Citizens after the restart: " + citizens
                    + " (" + guildsText(server, kingdom.id()) + ")");
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

    // ---------------------------------------------------------------- the capitals of the styles

    /**
     * A small styled capital, as new worlds have them, rises whole: the castle of its style and size around the throne, the
     * guild house and the mage tower of its rank, the treasury, the court, the infirmary, the yards and the decorations of the
     * square, their workers at their places, the bank in the treasury and the posts of the castle.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_npc_styled", timeoutTicks = 200)
    public static void aStyledSmallCapitalRisesWhole(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = platform(helper, HALF3);
        NpcSite site = null;
        try {
            site = plantStyled(helper, center, NpcLevel.SMALL, "");
            Kingdom kingdom = raise(helper, site, STYLED_STEPS);
            UUID id = kingdom.id();
            KingdomWorldState state = KingdomWorldApi.state(server);
            NpcComposition composition = NpcSpawner.composition(site);
            helper.assertTrue(composition.styled() && composition.style().equals(site.style()), "The plan is not of the style "
                    + site.style() + ": layout " + composition.layout() + " " + composition.style());
            helper.assertTrue(composition.wallRadius() == NpcComposition.capitalWall(NpcLevel.SMALL, NpcSite.LAYOUT_STYLED),
                    "The wall of the plan has the radius " + composition.wallRadius());
            int chunks = state.territory().territory(id).map(Territory::size).orElse(0);
            int radius = landRadius(site);
            helper.assertTrue(chunks == (2 * radius + 1) * (2 * radius + 1), "Land of " + chunks + " chunks for the radius " + radius);
            assertWhole(helper, site, kingdom, composition);
            Building castle = assertCastle(helper, site, kingdom, composition);
            // The institutions of the square, each once, the guild house and the mage tower of the rank of the place.
            TemplateCatalog.Rank rank = GuildHouses.fitting(site.level(), GuildHouses.of(site));
            String style = site.style();
            for (TemplateCatalog.Kind kind : List.of(TemplateCatalog.Kind.GUILD, TemplateCatalog.Kind.MAGE_TOWER, TemplateCatalog.Kind.TREASURY,
                    TemplateCatalog.Kind.COURT, TemplateCatalog.Kind.INFIRMARY)) {
                List<Building> standing = ofKind(server, id, kind);
                helper.assertTrue(standing.size() == 1, standing.size() + " building(s) of the kind " + kind + " stand");
                if (kind == TemplateCatalog.Kind.GUILD || kind == TemplateCatalog.Kind.MAGE_TOWER) {
                    TemplateCatalog.Rank shown = TemplateCatalog.get(standing.get(0).templateId()).flatMap(TemplateCatalog.Entry::guildRank)
                            .orElse(null);
                    helper.assertTrue(shown == rank, standing.get(0).templateId() + " is of the rank " + shown + " instead of " + rank);
                    helper.assertTrue(TemplateCatalog.get(standing.get(0).templateId()).map(entry -> entry.style().equals(style))
                            .orElse(false), standing.get(0).templateId() + " is not of the style " + style);
                }
            }
            helper.assertTrue(!ofKind(server, id, TemplateCatalog.Kind.TAVERN).isEmpty(), "No tavern stands");
            List<Building> yards = ofKind(server, id, TemplateCatalog.Kind.YARD);
            helper.assertTrue(yards.stream().anyMatch(b -> b.templateId().endsWith("_builders_yard"))
                    && yards.stream().anyMatch(b -> b.templateId().endsWith("_carters_yard")), "The yards stand not: " + yards.size());
            Set<String> decorations = new HashSet<>();
            ofKind(server, id, TemplateCatalog.Kind.DECORATION).forEach(building -> decorations.add(building.templateId()));
            helper.assertTrue(decorations.size() >= 3, "The square has " + decorations + " for decorations");
            helper.assertTrue(ofKind(server, id, TemplateCatalog.Kind.WALL_GATE).size() == composition.gates().size(),
                    "Gates of the wall standing: " + ofKind(server, id, TemplateCatalog.Kind.WALL_GATE).size() + " of " + composition.gates().size());
            Set<String> roles = new HashSet<>();
            buildings(server, id).forEach(building -> roles.add(building.role()));
            for (String role : List.of("house", "warehouse", "workshop", "market", "well", "barracks", "tower", "tavern")) {
                helper.assertTrue(roles.contains(role), "No building with the role " + role + ": " + roles);
            }
            assertRolesWork(helper, server, id);
            // The workers at their places: in the guild house, the treasury, the yards, the mage tower.
            assertWorkersIn(helper, server, id, TemplateCatalog.Kind.GUILD, "", "registrar");
            assertWorkersIn(helper, server, id, TemplateCatalog.Kind.TREASURY, "", "minter");
            assertWorkersIn(helper, server, id, TemplateCatalog.Kind.YARD, "_builders_yard", "builder");
            assertWorkersIn(helper, server, id, TemplateCatalog.Kind.YARD, "_carters_yard", "carrier");
            assertWorkersIn(helper, server, id, TemplateCatalog.Kind.MAGE_TOWER, "", "mage");
            Set<String> professions = new HashSet<>();
            state.citizens().registry().citizensOf(id).forEach(citizen -> professions.add(citizen.professionId()));
            for (String profession : List.of("registrar", "minter", "mage", "builder", "carrier", "merchant", "innkeeper", "smith", "farmer",
                    "warrior")) {
                helper.assertTrue(professions.contains(profession), "Nobody is a " + profession + ": " + professions);
            }
            // The bank in the treasury.
            int[] bank = StyledPlan.bank(composition).orElse(null);
            helper.assertTrue(bank != null, "The plan has no place for the bank");
            BlockPos bankPos = new BlockPos(site.x() + bank[0], site.groundY() + 1 + bank[2], site.z() + bank[1]);
            Building treasury = ofKind(server, id, TemplateCatalog.Kind.TREASURY).get(0);
            helper.assertTrue(level.getBlockState(bankPos).is(ModRegistries.BANK.get()) && treasury.box().contains(bankPos.getX(), bankPos.getY(),
                    bankPos.getZ()), "No bank in the treasury at " + bankPos.toShortString() + ": " + level.getBlockState(bankPos));
            // The guards of the hall have room by the throne; the gates of the wall and the castle have their posts.
            NpcComposition.Post hall = assertCastlePost(helper, composition, castle, site, "palace_hall", 4);
            helper.assertTrue(hall.points().size() == 4 && hall.points().stream().anyMatch(p -> p[0] == -2 && p[1] == 3)
                    && hall.points().stream().anyMatch(p -> p[0] == 2 && p[1] == 3), "The hall of the castle has not its four places");
            for (int[] point : hall.points()) {
                BlockPos at = feet(site, point);
                helper.assertTrue(standable(level, at), "The guard of the hall at " + at.toShortString() + " has no room: "
                        + level.getBlockState(at.below()) + " " + level.getBlockState(at) + " " + level.getBlockState(at.above()));
            }
            assertCastlePost(helper, composition, castle, site, "palace_door", 1);
            assertCastlePost(helper, composition, castle, site, "castle_gate", 2);
            assertCastlePost(helper, composition, castle, site, "castle_wall", 4);
            assertCastlePost(helper, composition, castle, site, "treasury", 1);
            long gates = composition.posts().stream().filter(post -> post.name().startsWith("gate_")).count();
            helper.assertTrue(gates == composition.gates().size(), gates + " posts of gates for " + composition.gates().size() + " gates");
            helper.succeed();
        } finally {
            cleanUp(helper, site, center, HALF3);
        }
    }

    /**
     * A large styled capital rises with the large castle of its style, a gate on every side, the barracks of the royal guard and
     * the posts of its castle, which its guard holds while it has soldiers left for them. One test a run: the heaviest.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_npc_styled_large", timeoutTicks = 200)
    public static void aStyledLargeCapitalRises(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = platform(helper, LARGE_HALF3);
        // The garrison of the rules of a new world, whatever the config of the test world keeps from older versions.
        NpcSettings.override(NpcRules.DEFAULT);
        NpcSite site = null;
        try {
            // The style with the roomiest houses: the fewest people for the work of a large capital.
            site = plantStyled(helper, center, NpcLevel.LARGE, "nordic");
            long started = System.nanoTime();
            Kingdom kingdom = raise(helper, site, LARGE_STYLED_STEPS);
            long millis = (System.nanoTime() - started) / 1_000_000;
            NpcComposition composition = NpcSpawner.composition(site);
            helper.assertTrue(composition.styled(), "The plan is not styled");
            helper.assertTrue(composition.buildings().size() >= 55, "A large styled capital of " + composition.buildings().size() + " buildings");
            helper.assertTrue(composition.gates().size() == 4, "Gates of the plan: " + composition.gates().size());
            assertWhole(helper, site, kingdom, composition);
            Building castle = assertCastle(helper, site, kingdom, composition);
            helper.assertTrue(ofKind(server, kingdom.id(), TemplateCatalog.Kind.WALL_GATE).size() == 4, "Gates of the wall standing: "
                    + ofKind(server, kingdom.id(), TemplateCatalog.Kind.WALL_GATE).size());
            helper.assertTrue(!ofKind(server, kingdom.id(), TemplateCatalog.Kind.ROYAL_BARRACKS).isEmpty(), "No barracks of the royal guard");
            helper.assertTrue(ofKind(server, kingdom.id(), TemplateCatalog.Kind.GUILD).size() == 1
                    && ofKind(server, kingdom.id(), TemplateCatalog.Kind.MAGE_TOWER).size() == 1, "The guild house or the mage tower is missing");
            assertCastlePost(helper, composition, castle, site, "castle_gate", 2);
            assertCastlePost(helper, composition, castle, site, "castle_wall", 4);
            assertCastlePost(helper, composition, castle, site, "treasury", 1);
            // Every trade of the plan has its people, the last ones (miners, carriers) too, however few the houses hold.
            Set<String> professions = new HashSet<>();
            KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdom.id())
                    .forEach(citizen -> professions.add(citizen.professionId()));
            for (String profession : List.of("farmer", "merchant", "innkeeper", "crafter", "registrar", "minter", "mage", "builder",
                    "lumberjack", "miner", "carrier")) {
                helper.assertTrue(composition.jobs().getOrDefault(profession, 0) > 0, "The plan has no work for a " + profession + ": "
                        + composition.jobs());
            }
            for (String profession : composition.jobs().keySet()) {
                helper.assertTrue(professions.contains(profession), "Nobody is a " + profession + ": " + professions);
            }
            assertWorkersIn(helper, server, kingdom.id(), TemplateCatalog.Kind.YARD, "_carters_yard", "carrier");
            assertWorkersIn(helper, server, kingdom.id(), TemplateCatalog.Kind.YARD, "_builders_yard", "builder");
            // A large kingdom without towns has guards left after the posts of the capital: the castle gets its own.
            GuardArmyGameTests.assertCastleDuties(helper, site, kingdom.id());
            com.mojang.logging.LogUtils.getLogger().info("A large styled capital ({}, guild rank {}) of {} buildings rose in {} ms", site.style(),
                    site.guildRank(), composition.buildings().size(), millis);
            helper.succeed();
        } finally {
            NpcSettings.override(null);
            cleanUp(helper, site, center, LARGE_HALF3);
        }
    }

    /**
     * A styled capital stopped by a restart in the middle of its castle goes on with the same plan: its layout, style, rank and
     * wood are saved, the half-built castle is finished as it was laid out, with its look, and nothing stands twice.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_npc_styled_again", timeoutTicks = 200)
    public static void aStyledCapitalGoesOnAfterARestart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = platform(helper, HALF3);
        NpcSite site = null;
        try {
            site = plantStyled(helper, center, NpcLevel.SMALL, "");
            NpcComposition planned = NpcSpawner.composition(site);
            String castle = planned.buildings().get(0).template();
            // Tick by tick up to the castle half done: laid out, some of its blocks placed.
            Construction half = null;
            for (int i = 0; i < STYLED_STEPS && half == null && site.status() == NpcSite.Status.SPAWNING; i++) {
                NpcSpawner.runNow(server, site, 1);
                if (site.stage() == SpawnStage.BUILD && site.kingdomId().isPresent()) {
                    half = KingdomWorldApi.state(server).construction().of(site.kingdomId().get()).stream()
                            .filter(construction -> construction.templateId().equals(castle)).findFirst().orElse(null);
                }
            }
            helper.assertTrue(half != null && site.stage() == SpawnStage.BUILD && site.step() == 0, "The castle is not half built: "
                    + site.stage().id() + " step " + site.step() + " " + half);
            Materials look = half.materials();
            helper.assertTrue(!look.look().isEmpty(), "The castle was laid out without the look of its style: " + look);
            // A restart: what is kept in memory goes, the saved data is read again.
            CompoundTag saved = NpcSavedData.get(server).save(new CompoundTag());
            NpcSavedData reread = NpcSavedData.load(saved);
            NpcSite copy = reread.state().site(site.id()).orElse(null);
            helper.assertTrue(!reread.isReadOnly() && copy != null && copy.stage() == SpawnStage.BUILD && copy.step() == site.step()
                    && copy.kingdomId().equals(site.kingdomId()), "The progress did not survive saving: " + copy);
            helper.assertTrue(copy.layout() == NpcSite.LAYOUT_STYLED && copy.style().equals(site.style()) && copy.wood().equals(site.wood())
                            && copy.guildRank().equals(site.guildRank()) && copy.groundY() == site.groundY(),
                    "The place did not survive saving: " + copy.layout() + " " + copy.style() + " " + copy.wood() + " '" + copy.guildRank() + "'");
            NpcComposition again = NpcSpawner.composition(copy);
            helper.assertTrue(again.buildings().equals(planned.buildings()) && samePoints(again.trees(), planned.trees())
                    && samePoints(again.gardens(), planned.gardens()), "The plan changed after saving");
            NpcSpawner.resetForTests();
            Kingdom kingdom = raise(helper, site, STYLED_STEPS);
            NpcComposition composition = NpcSpawner.composition(site);
            helper.assertTrue(composition.buildings().equals(planned.buildings()), "The plan changed after the restart");
            String problem = plotProblem(server, site, composition, kingdom.id());
            helper.assertTrue(problem.isEmpty(), "After the restart: " + problem);
            helper.assertTrue(KingdomWorldApi.state(server).construction().of(kingdom.id()).isEmpty(), "Constructions were left: "
                    + KingdomWorldApi.state(server).construction().of(kingdom.id()).size());
            WorldPos anchor = half.anchor();
            Building built = buildings(server, kingdom.id()).stream().filter(b -> b.templateId().equals(castle) && b.anchor().x() == anchor.x()
                    && b.anchor().z() == anchor.z()).findFirst().orElse(null);
            helper.assertTrue(built != null, "The castle was not finished where it was laid out at " + anchor);
            helper.assertTrue(built.materials().equals(look), "The castle changed its look with the restart: " + look + " -> " + built.materials());
            for (Building building : buildings(server, kingdom.id())) {
                boolean styled = TemplateCatalog.get(building.templateId()).map(entry -> !entry.style().isEmpty()).orElse(false);
                helper.assertTrue(!styled || !building.materials().look().isEmpty(), building.templateId() + " stands without a look");
            }
            int citizens = KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdom.id()).size();
            helper.assertTrue(citizens - fromAbroad(server, kingdom.id()) == composition.citizens(), "Citizens after the restart: " + citizens
                    + " of " + composition.citizens());
            helper.assertTrue(level.getBlockState(NpcSpawner.thronePos(site)).is(ModRegistries.THRONE.get()), "No throne after the restart");
            helper.succeed();
        } finally {
            cleanUp(helper, site, center, HALF3);
        }
    }

    /**
     * The kingdoms of a world take the styles no other kingdom holds while there are such: planted one after another, they get
     * every free style once; when all are held a ninth one takes one of them again, and a style comes free with its kingdom
     * gone. The places are only planned, far from everything, and removed at the end.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_npc_styles", timeoutTicks = 100)
    public static void stylesDoNotRepeatInAWorld(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        List<NpcSite> planted = new ArrayList<>();
        try {
            // Other tests may have left kingdoms in the world: what they hold is not free.
            Set<String> taken = new HashSet<>(NpcWork.state(server).takenStyles());
            List<String> free = new ArrayList<>(ArchitecturalStyles.IDS);
            free.removeAll(taken);
            Set<String> given = new java.util.LinkedHashSet<>();
            int[] next = {0};
            java.util.function.Supplier<NpcSite> plan = () -> {
                int n = next[0]++;
                NpcSite site = NpcWork.planAt(server, origin.getX() + 60_000 + n * 4_000, origin.getZ() - 60_000, NpcLevel.SMALL, List.of(),
                        NpcSite.LAYOUT_STYLED, "").orElse(null);
                helper.assertTrue(site != null && site.styled() && ArchitecturalStyles.known(site.style()), "No styled place was planned: " + site);
                planted.add(site);
                return site;
            };
            for (int i = 0; i < free.size(); i++) {
                NpcSite site = plan.get();
                helper.assertTrue(!taken.contains(site.style()) && given.add(site.style()), "A kingdom took the style " + site.style()
                        + " another one holds: taken " + taken + ", given " + given);
            }
            helper.assertTrue(given.size() == free.size() && NpcWork.state(server).takenStyles().containsAll(ArchitecturalStyles.IDS),
                    "Not every style is held: " + NpcWork.state(server).takenStyles());
            if (!planted.isEmpty()) {
                // The style of a kingdom that is gone is free again, and the next kingdom takes it.
                NpcSite first = planted.get(0);
                helper.assertTrue(NpcWork.remove(server, first.id(), false).ok(), "The first place was not removed");
                NpcSite after = plan.get();
                helper.assertTrue(after.style().equals(first.style()), "The free style " + first.style() + " went to nobody: " + after.style());
            }
            // Every style held: one of them again.
            NpcSite ninth = plan.get();
            helper.assertTrue(ArchitecturalStyles.known(ninth.style()), "A kingdom beyond the eighth has no style: " + ninth.style());
            helper.succeed();
        } finally {
            for (NpcSite site : planted) {
                if (site.status() != NpcSite.Status.REMOVED) {
                    NpcWork.remove(server, site.id(), false);
                }
            }
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
