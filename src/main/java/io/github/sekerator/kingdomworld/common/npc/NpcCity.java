package io.github.sekerator.kingdomworld.common.npc;

import io.github.sekerator.kingdomworld.common.construction.TemplateMaterials;
import io.github.sekerator.kingdomworld.common.road.CityWalls;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.npc.NpcComposition;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcRules;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.NpcStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What the appearance of a kingdom needs to know about a capital laid out in districts ({@link NpcSite#LAYOUT_DISTRICTS}): the
 * land it takes, the stone and wood of its style, the ground of its streets and squares, where its posts of work, lamps and
 * trees stand, and where its citizens arrive. The stages themselves run in {@link NpcSpawner}.
 */
final class NpcCity {
    /** Blocks between two lamps of an avenue. */
    private static final int LAMP_STEP = 10;

    private NpcCity() {
    }

    // ---------------------------------------------------------------- land

    /**
     * The land around the capital of a place, in chunks from the chunk of the throne: what the rules give, and for a capital of
     * districts at least what its wall and fields take.
     */
    static int landRadius(NpcSite site) {
        return landRadius(site.level(), site.layout());
    }

    static int landRadius(NpcLevel level, int layout) {
        int configured = NpcSettings.rules().landRadius(level);
        if (layout != NpcSite.LAYOUT_DISTRICTS) {
            return configured;
        }
        return Math.min(NpcRules.MAX_LAND_RADIUS, Math.max(configured, (NpcComposition.districtReach(level) + 15) / 16));
    }

    /**
     * The land around a town hall of a place, in chunks from its chunk.
     */
    static int townRadius(NpcSite site) {
        return townRadius(site.layout());
    }

    static int townRadius(int layout) {
        return layout == NpcSite.LAYOUT_DISTRICTS ? 2 : 1;
    }

    /**
     * How far the towns of a kingdom lie from its capital, in blocks: what the rules say, and for a kingdom of districts never
     * so near that the ground of a town would reach the fields and the wall of the capital, whatever the config says.
     */
    static int townDistance(NpcLevel level, int layout) {
        int configured = NpcSettings.rules().townDistance();
        if (layout != NpcSite.LAYOUT_DISTRICTS) {
            return configured;
        }
        return Math.max(configured, NpcComposition.districtReach(level) + NpcComposition.districtTownReach() + NpcSpawner.RAMP + 16);
    }

    // ---------------------------------------------------------------- the look

    /**
     * The wood and stone the buildings of a place are built of: those of its style, the wood of its land where the style names
     * none; a place of the first layout takes the wood that grows where each building stands.
     */
    static Materials materials(NpcSite site) {
        if (!site.districts()) {
            return new Materials(Materials.AUTO, "");
        }
        Optional<NpcStyle> look = site.look();
        String wood = look.map(NpcStyle::wood).orElse("");
        if (wood.isEmpty()) {
            wood = site.wood();
        }
        String stone = look.map(NpcStyle::stone).orElse("");
        Materials wanted = new Materials(wood, stone);
        return TemplateMaterials.known(wanted) ? wanted : new Materials(TemplateMaterials.table().hasWood(wood) ? wood : "", "");
    }

    /**
     * The materials of a building on a plot of the capital of a place, with the look of its style and quarter for a styled
     * capital.
     */
    static Materials materials(MinecraftServer server, NpcSite site, NpcComposition.Plot plot) {
        return materials(site);
    }

    /**
     * The materials of a building on a plot of a town of a place.
     *
     * @param town the number of the town, from 0
     */
    static Materials townMaterials(MinecraftServer server, NpcSite site, int town, NpcComposition.Plot plot) {
        return materials(site);
    }

    /**
     * The materials of the public buildings of a place (its camps of the army among them).
     */
    static Materials publicMaterials(MinecraftServer server, NpcSite site) {
        return materials(site);
    }

    /**
     * The wood species of a place for palisades, groves and stores: that of its style or land, oak if neither is known.
     */
    static String wood(NpcSite site) {
        String wood = materials(site).wood();
        if (wood.isEmpty() || Materials.AUTO.equals(wood) || !ForgeRegistries.ITEMS.containsKey(ResourceLocation.tryParse(wood + "_log"))
                || !ForgeRegistries.ITEMS.containsKey(ResourceLocation.tryParse(wood + "_planks"))) {
            return "minecraft:oak";
        }
        return wood;
    }

    /**
     * The item the stone wall of the capital of a place is built of.
     */
    static String wallStone(NpcSite site) {
        return site.look().map(NpcStyle::wallBlock).orElse("minecraft:stone_bricks");
    }

    private static BlockState block(String id, Block fallback) {
        Block block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse(id));
        return block == null || block == Blocks.AIR ? fallback.defaultBlockState() : block.defaultBlockState();
    }

    /**
     * The paving of squares and avenues in the style of a place.
     */
    private static BlockState paving(NpcSite site) {
        return switch (site.look().orElse(NpcStyle.STONE)) {
            case BRICK -> Blocks.BRICKS.defaultBlockState();
            case SANDSTONE -> Blocks.CUT_SANDSTONE.defaultBlockState();
            case DEEPSLATE -> Blocks.DEEPSLATE_TILES.defaultBlockState();
            case MUD -> Blocks.MUD_BRICKS.defaultBlockState();
            default -> Blocks.STONE_BRICKS.defaultBlockState();
        };
    }

    /**
     * The rougher paving of streets in the style of a place.
     */
    private static BlockState cobbles(NpcSite site) {
        return switch (site.look().orElse(NpcStyle.STONE)) {
            case SANDSTONE -> Blocks.SANDSTONE.defaultBlockState();
            case DEEPSLATE -> Blocks.COBBLED_DEEPSLATE.defaultBlockState();
            case MUD -> Blocks.PACKED_MUD.defaultBlockState();
            default -> Blocks.COBBLESTONE.defaultBlockState();
        };
    }

    /**
     * The top block of the ground of a capital of districts at a column: paving by the class of its roads (a small capital has
     * trodden paths and a cobbled square, a large one paved avenues), the soil of the place in yards and on greens.
     */
    static BlockState surface(NpcSite site, NpcComposition composition, int dx, int dz, NpcGround.Soil soil) {
        int ground = composition.ground(dx, dz);
        // The road of a gate goes on through the wall and a little beyond it.
        int reach = Math.max(Math.abs(dx), Math.abs(dz));
        if (reach >= composition.wallRadius()) {
            for (NpcComposition.Gate gate : composition.gates()) {
                boolean across = gate.x() == 0 ? Math.abs(dx) <= 1 : Math.abs(dz) <= 1;
                boolean along = gate.x() == 0 ? Integer.signum(dz) == Integer.signum(gate.z()) : Integer.signum(dx) == Integer.signum(gate.x());
                if (across && along && reach <= composition.wallRadius() + 2) {
                    ground = NpcComposition.GROUND_AVENUE;
                }
            }
        }
        BlockState path = soil == NpcGround.Soil.GRASS ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
        String roads = composition.roadClass();
        return switch (ground) {
            case NpcComposition.GROUND_SQUARE -> "path".equals(roads) ? cobbles(site) : paving(site);
            case NpcComposition.GROUND_AVENUE -> "paved".equals(roads) ? paving(site) : "gravel".equals(roads) ? cobbles(site)
                    : Blocks.GRAVEL.defaultBlockState();
            case NpcComposition.GROUND_STREET -> "paved".equals(roads) ? cobbles(site) : "gravel".equals(roads) ? Blocks.GRAVEL.defaultBlockState()
                    : path;
            case NpcComposition.GROUND_WALK -> "path".equals(roads) ? path : Blocks.GRAVEL.defaultBlockState();
            default -> soil.top();
        };
    }

    // ---------------------------------------------------------------- places on the square, lamps, trees

    /**
     * A place of work or of the treasury on the square of the palace.
     *
     * @param what  a profession for a post of work, or {@code guild_desk}, {@code mint} or {@code bank}
     * @param seats the workers a post of work takes
     */
    record Place(String what, int x, int z, int seats) {
        Place(String what, int x, int z) {
            this(what, x, z, 0);
        }
    }

    /**
     * Where the posts of the builders, carriers, registrar and minter, the desk of the guild, the mint and the bank stand: in the
     * corners of the square around the palace, and for a larger capital more posts of builders and carriers by its gates, so that
     * the builders reach the land outside the wall.
     */
    static List<Place> places(NpcComposition composition) {
        int q = composition.precinct() + 2;
        List<Place> list = new ArrayList<>();
        list.add(new Place("builder", -q, q));
        list.add(new Place("carrier", q, q));
        list.add(new Place("guild_desk", -q, -q));
        list.add(new Place("registrar", -q + 1, -q));
        list.add(new Place("mint", q, -q));
        list.add(new Place("minter", q - 1, -q));
        list.add(new Place("bank", q, -q + 1));
        if (composition.level() != NpcLevel.SMALL) {
            int by = composition.wallRadius() - 8;
            list.add(new Place("builder", 5, by));
            list.add(new Place("carrier", -5, by));
            if (composition.level() == NpcLevel.LARGE) {
                list.add(new Place("builder", 5, -by));
                list.add(new Place("builder", by, 5));
                list.add(new Place("builder", -by, 5));
            }
        }
        return list;
    }

    /**
     * Where the posts of woodcutters and miners stand: outside the corners of the wall, {x, z} from the throne.
     */
    static List<int[]> outerPosts(NpcComposition composition) {
        int out = composition.wallRadius() + 10;
        return List.of(new int[]{out, -out}, new int[]{-out, -out}, new int[]{out, out}, new int[]{-out, out});
    }

    /**
     * Lamp posts along the avenues, on their edges by turns, and at the corners of the square of the palace: {x, z} from the
     * throne.
     */
    static List<int[]> lamps(NpcComposition composition) {
        List<int[]> list = new ArrayList<>();
        int square = composition.precinct() + 1;
        for (int[] corner : new int[][]{{-square, -square}, {square, -square}, {-square, square}, {square, square}}) {
            list.add(corner);
        }
        int first = square + 6;
        int last = composition.wallRadius() - 9;
        int edge = 2;
        int turn = 0;
        for (int at = first; at <= last; at += LAMP_STEP, turn++) {
            int side = turn % 2 == 0 ? edge : -edge;
            for (int sign : new int[]{-1, 1}) {
                addLamp(composition, list, side, sign * at);
                addLamp(composition, list, sign * at, side);
            }
        }
        return list;
    }

    private static void addLamp(NpcComposition composition, List<int[]> list, int x, int z) {
        if (composition.ground(x, z) == NpcComposition.GROUND_AVENUE) {
            list.add(new int[]{x, z});
        }
    }

    /**
     * The middles of the greens of a capital, where a tree stands: {x, z} from the throne.
     */
    static List<int[]> greens(NpcComposition composition) {
        List<int[]> list = new ArrayList<>();
        int reach = composition.wallRadius();
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                // The middle of a green is the cell whose neighbours five blocks away in all four directions are greens too.
                if (composition.ground(x, z) == NpcComposition.GROUND_GREEN && composition.ground(x - 5, z - 5) == NpcComposition.GROUND_GREEN
                        && composition.ground(x + 5, z + 5) == NpcComposition.GROUND_GREEN
                        && composition.ground(x - 6, z) != NpcComposition.GROUND_GREEN && composition.ground(x, z - 6) != NpcComposition.GROUND_GREEN) {
                    list.add(new int[]{x, z});
                }
            }
        }
        return list;
    }

    /**
     * A lamp post: two fence posts with a lantern on top.
     */
    static void lamp(ServerLevel level, UUID id, NpcSite site, BlockPos base) {
        BlockState fence = block(wood(site) + "_fence", Blocks.OAK_FENCE);
        NpcGround.set(level, id, base, fence);
        NpcGround.set(level, id, base.above(), fence);
        NpcGround.set(level, id, base.above(2), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
    }

    /**
     * A tree of the wood of the place on a green: a trunk and a crown that does not decay.
     */
    static void tree(ServerLevel level, UUID id, NpcSite site, BlockPos base) {
        String wood = wood(site);
        BlockState log = block(wood + "_log", Blocks.OAK_LOG);
        BlockState leaves = block(wood + "_leaves", Blocks.OAK_LEAVES);
        if (leaves.hasProperty(LeavesBlock.PERSISTENT)) {
            leaves = leaves.setValue(LeavesBlock.PERSISTENT, true);
        }
        if (log.hasProperty(RotatedPillarBlock.AXIS)) {
            log = log.setValue(RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.Y);
        }
        int height = 4;
        for (int dy = 0; dy < height; dy++) {
            NpcGround.set(level, id, base.above(dy), log);
        }
        for (int dy = height - 2; dy <= height + 1; dy++) {
            int radius = dy >= height ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    boolean corner = Math.abs(dx) == radius && Math.abs(dz) == radius;
                    BlockPos at = base.offset(dx, dy, dz);
                    if ((dx != 0 || dz != 0 || dy >= height) && !(corner && radius == 2) && level.getBlockState(at).isAir()) {
                        NpcGround.set(level, id, at, leaves);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- people

    /**
     * Where the citizens of a kingdom arrive, one place for each: before every bed of the houses of the capital, then before
     * the bunks of its barracks for its soldiers, then before the beds of its towns. A citizen takes the free bed nearest to
     * where it arrives and the work nearest to that, so that people live and work in the same part of the town.
     *
     * @return {x, z} in blocks of the world, with the index of the town or -1 for the capital as the third number
     */
    static List<int[]> arrivals(NpcSite site, NpcComposition composition) {
        List<int[]> list = new ArrayList<>();
        for (NpcComposition.Plot plot : composition.buildings()) {
            if (!NpcComposition.bunks(plot.template())) {
                for (int i = 0; i < NpcComposition.beds(plot.template()); i++) {
                    list.add(new int[]{site.x() + plot.x(), site.z() + plot.z(), -1});
                }
            }
        }
        int soldiers = composition.soldiers();
        for (NpcComposition.Plot plot : composition.buildings()) {
            if (NpcComposition.bunks(plot.template())) {
                for (int i = 0; i < NpcComposition.beds(plot.template()) && soldiers > 0; i++, soldiers--) {
                    list.add(new int[]{site.x() + plot.x(), site.z() + plot.z(), -1});
                }
            }
        }
        for (int t = 0; t < Math.min(site.towns().size(), composition.towns()); t++) {
            NpcSite.Town town = site.towns().get(t);
            for (NpcComposition.Plot plot : composition.town(t)) {
                if (!NpcComposition.bunks(plot.template())) {
                    for (int i = 0; i < NpcComposition.beds(plot.template()); i++) {
                        list.add(new int[]{town.x() + plot.x(), town.z() + plot.z(), t});
                    }
                }
            }
        }
        // Soldiers the barracks of the capital have no bunk for sleep in those of the towns.
        for (int t = 0; t < Math.min(site.towns().size(), composition.towns()) && soldiers > 0; t++) {
            NpcSite.Town town = site.towns().get(t);
            for (NpcComposition.Plot plot : composition.town(t)) {
                if (NpcComposition.bunks(plot.template())) {
                    for (int i = 0; i < NpcComposition.beds(plot.template()) && soldiers > 0; i++, soldiers--) {
                        list.add(new int[]{town.x() + plot.x(), town.z() + plot.z(), t});
                    }
                }
            }
        }
        while (list.size() < composition.citizens()) {
            list.add(new int[]{site.x(), site.z() + composition.precinct() + 2, -1});
        }
        return list;
    }

    /**
     * The profession of the post of work inside a building, by what the building is for and what stands in it: a workshop with
     * an anvil or a blast furnace is a smithy, one with a smoker a bakery, any other a crafter's; markets have merchants,
     * taverns an innkeeper, a tower of mages a mage.
     */
    static String profession(String role, java.util.function.Predicate<Block> stands) {
        return switch (role) {
            case "workshop" -> stands.test(Blocks.ANVIL) || stands.test(Blocks.BLAST_FURNACE) || stands.test(Blocks.SMITHING_TABLE) ? "smith"
                    : stands.test(Blocks.SMOKER) ? "cook" : "crafter";
            case "market" -> "merchant";
            case "tavern" -> "innkeeper";
            case "mage_tower" -> "mage";
            default -> "";
        };
    }

    /**
     * The gate of a capital a road to a place leaves by: the one whose side looks most towards it.
     */
    static Optional<NpcComposition.Gate> gateTowards(NpcComposition composition, int dx, int dz) {
        NpcComposition.Gate best = null;
        double bestScore = -Double.MAX_VALUE;
        double length = Math.max(1.0, Math.hypot(dx, dz));
        for (NpcComposition.Gate gate : composition.gates()) {
            double score = (gate.x() * dx + gate.z() * dz) / (length * Math.max(1.0, Math.hypot(gate.x(), gate.z())));
            if (score > bestScore) {
                bestScore = score;
                best = gate;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Cuts the gates of the plan into the wall of a capital that was just laid out.
     */
    static void cutGates(net.minecraft.server.MinecraftServer server, NpcSite site, NpcComposition composition, UUID id) {
        for (NpcComposition.Gate gate : composition.gates()) {
            CityWalls.gateOfPlan(server, id, 0, site.x() + gate.x(), site.z() + gate.z());
        }
    }
}
