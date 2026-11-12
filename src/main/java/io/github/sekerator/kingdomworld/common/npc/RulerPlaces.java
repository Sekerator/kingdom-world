package io.github.sekerator.kingdomworld.common.npc;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplate;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplates;
import io.github.sekerator.kingdomworld.common.construction.ConstructionSettings;
import io.github.sekerator.kingdomworld.common.construction.TemplateMaterials;
import io.github.sekerator.kingdomworld.common.road.RoadSettings;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.npc.NpcComposition;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.RulerMind;
import io.github.sekerator.kingdomworld.core.npc.RulerView;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadRules;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where a ruler of the world puts what it decides on, from the data of its kingdom: free ground inside the wall of its capital
 * (the gardens of the grid or the free lots of the districts, levelled when the kingdom appeared), plots beyond its wall and
 * fields, chunks of free land next to its own and places for new towns. Places that turned out unfit when the world was looked
 * at are remembered until a restart. Which template a ruler builds follows from the role it wants: every template of the role
 * may come up, by turns, so that the houses a ruler adds differ like those the kingdom appeared with.
 */
final class RulerPlaces {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    /** Kinds of the templates of the plans a ruler does not put up by itself: they belong to the wall and the palace. */
    private static final Set<io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Kind> NOT_BY_RULERS = Set.of(
            io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Kind.GATEHOUSE,
            io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Kind.CORNER_TOWER,
            io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Kind.HALL,
            io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Kind.CASTLE,
            io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Kind.DRILL_YARD,
            io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Kind.STORAGE_YARD);
    /** Blocks a plot beyond the wall keeps from fields, roads and the wall. */
    private static final int KEEP_CLEAR = 3;
    /** What a template costs with a wood; templates do not change while the server runs. */
    private static final Map<String, Map<String, Integer>> COSTS = new ConcurrentHashMap<>();
    /** Anchors that turned out unfit, by kingdom; tried again after a restart. */
    private static final Map<UUID, Set<Long>> UNFIT = new ConcurrentHashMap<>();

    /**
     * A place for a building.
     *
     * @param levelled whether the ground is flat already (a plot of the grid inside the wall)
     */
    record Spot(BuildingTemplate template, BlockPos anchor, Placement placement, Box box, boolean levelled) {
    }

    private RulerPlaces() {
    }

    static void reset() {
        UNFIT.clear();
        COSTS.clear();
    }

    /**
     * What a template costs with the wood of a kingdom.
     */
    static Map<String, Integer> cost(BuildingTemplate template, String wood) {
        return cost(template, new Materials(wood, ""));
    }

    /**
     * What a template costs built of the wood and stone of a kingdom.
     */
    static Map<String, Integer> cost(BuildingTemplate template, Materials materials) {
        return COSTS.computeIfAbsent(template.id() + "|" + template.fingerprint() + "|" + materials.key(),
                key -> Map.copyOf(TemplateMaterials.apply(template, materials).items()));
    }

    /**
     * The wood and stone a ruler builds with: the style of its kingdom where it has one, else the wood of the buildings that
     * stand.
     */
    static Materials materials(MinecraftServer server, UUID kingdomId) {
        Optional<NpcSite> site = NpcWork.kingdom(server, kingdomId).flatMap(npc -> NpcWork.state(server).site(npc.siteId()));
        if (site.isPresent() && site.get().districts()) {
            Materials own = NpcCity.materials(site.get());
            // Costs are counted for a wood that is named: the templates' own woods count as they are.
            return own;
        }
        return new Materials(wood(server, kingdomId), "");
    }

    static void unfit(UUID kingdomId, BlockPos anchor) {
        UNFIT.computeIfAbsent(kingdomId, id -> ConcurrentHashMap.newKeySet()).add(anchor.asLong());
    }

    /**
     * The wood of the buildings of a kingdom, as a species ({@code minecraft:spruce}); oak if none is known.
     */
    static String wood(MinecraftServer server, UUID kingdomId) {
        for (Building building : KingdomWorldApi.state(server).construction().buildingsOf(kingdomId)) {
            String wood = building.materials().wood();
            if (!wood.isEmpty() && !wood.equals(Materials.AUTO)) {
                return wood;
            }
        }
        return "minecraft:oak";
    }

    /**
     * The template a ruler builds for a role: its own choice for the roles it knows, else the first template of the role at the
     * lowest level.
     */
    static Optional<BuildingTemplate> template(String role) {
        return BuildingTemplates.all().stream().filter(t -> role.equals(t.role()) && usable(t))
                .min(Comparator.comparingInt(BuildingTemplate::level).thenComparing(t -> t.id().toString()));
    }

    /**
     * The templates of a role at its first level, by id.
     */
    static List<BuildingTemplate> templates(String role) {
        List<BuildingTemplate> list = new ArrayList<>();
        BuildingTemplates.all().stream().filter(t -> role.equals(t.role()) && t.level() <= 1 && usable(t))
                .sorted(Comparator.comparing(t -> t.id().toString())).forEach(list::add);
        return list;
    }

    private static boolean usable(BuildingTemplate template) {
        if (io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.get(template.id().toString())
                .map(entry -> NOT_BY_RULERS.contains(entry.kind())).orElse(false)) {
            return false;
        }
        return template.owner() == null && template.available() && BuildingTemplates.problem(template, ConstructionSettings.rules()) == null;
    }

    /**
     * The buildings a ruler could lay out this turn: a house, a storehouse and every role of the table, each with what its missing
     * materials are worth and whether a place was found.
     */
    static void options(MinecraftServer server, NpcKingdom npc, RulerView view, Map<String, Long> counts) {
        UUID id = npc.kingdomId();
        Materials materials = materials(server, id);
        for (String role : RulerMind.wantedRoles(view, NpcSettings.rulers())) {
            if (!view.knownRoles.contains(role)) {
                continue;
            }
            // Of the first-level templates of the role, those whose missing materials cost about the least; among them the next
            // one in turn for every building of the role that stands, so that a ruler does not build the same house every time.
            List<BuildingTemplate> candidates = templates(role);
            long[] missing = new long[candidates.size()];
            long least = Long.MAX_VALUE;
            for (int i = 0; i < candidates.size(); i++) {
                missing[i] = RulerSnapshots.missingWorth(server, counts, cost(candidates.get(i), materials));
                least = Math.min(least, missing[i]);
            }
            BuildingTemplate best = null;
            int turn = view.roles.getOrDefault(role, 0);
            for (int i = 0; i < candidates.size() && best == null; i++) {
                int index = Math.floorMod(turn + i, candidates.size());
                if (missing[index] <= least + Math.max(40, least / 4)) {
                    best = candidates.get(index);
                    least = missing[index];
                }
            }
            if (best == null) {
                continue;
            }
            boolean place = find(server, npc, best).isPresent();
            view.builds.add(new RulerView.Option(role, best.id().toString(), "", least, place));
        }
    }

    /**
     * A free place for a building on the land of a kingdom within reach of its builders: a garden of the grid of the capital
     * first, then plots beyond its wall.
     */
    static Optional<Spot> find(MinecraftServer server, NpcKingdom npc, BuildingTemplate template) {
        Optional<NpcSite> found = NpcWork.state(server).site(npc.siteId());
        if (found.isEmpty() || found.get().groundY() == 0) {
            return Optional.empty();
        }
        NpcSite site = found.get();
        UUID id = npc.kingdomId();
        KingdomWorldState state = KingdomWorldApi.state(server);
        Optional<Territory> territory = state.territory().territory(id);
        if (territory.isEmpty()) {
            return Optional.empty();
        }
        String dimension = territory.get().dimension();
        NpcComposition composition = NpcSpawner.composition(site);
        int y = site.groundY() + 1;
        Set<Long> unfit = UNFIT.getOrDefault(id, Set.of());
        List<io.github.sekerator.kingdomworld.core.citizen.WorldPos> builders = new ArrayList<>();
        state.citizens().posts().postsOf(id, Professions.BUILDER.id()).forEach(post -> builders.add(post.pos()));
        if (builders.isEmpty()) {
            // A kingdom without a builder gets one at the square of its throne when it lays out a building.
            BlockPos post = builderPost(site);
            builders.add(new io.github.sekerator.kingdomworld.core.citizen.WorldPos(dimension, post.getX(), post.getY(), post.getZ()));
        }
        int reach = Math.max(8, ConstructionSettings.rules().builderRadius() - 4);
        int first;
        List<int[]> outside;
        if (composition.districts()) {
            for (NpcComposition.Lot lot : composition.lots()) {
                Optional<Spot> spot = spot(state, id, dimension, template, site.x() + lot.x(), y, site.z() + lot.z(), lot.facing(), true, unfit,
                        builders, reach);
                if (spot.isPresent()) {
                    return spot;
                }
            }
            // Beyond the wall, on the grid of the plots of old, between the fields and the outer posts and clear of the ways out of
            // the gates: beyond the fields all would lie off the land and out of reach of the builders.
            first = (composition.wallRadius() + NpcComposition.PLOT / 2 + 1 + KEEP_CLEAR + NpcComposition.SPACING - 1) / NpcComposition.SPACING;
            outside = new ArrayList<>();
            for (NpcComposition.Gate gate : composition.gates()) {
                int out = composition.wallRadius() + NpcComposition.SPACING;
                outside.add(new int[]{site.x() + Integer.signum(gate.x()) * out, site.z() + Integer.signum(gate.z()) * out});
            }
            for (int[] spot : composition.fieldSpots()) {
                outside.add(new int[]{site.x() + spot[0], site.z() + spot[1]});
            }
            for (int[] spot : NpcCity.outerPosts(composition)) {
                outside.add(new int[]{site.x() + spot[0], site.z() + spot[1]});
            }
        } else {
            for (int[] cell : NpcSpawner.gardenPlots(composition)) {
                Optional<Spot> spot = spot(state, id, dimension, template, site.x() + cell[0] * NpcComposition.SPACING, y,
                        site.z() + cell[1] * NpcComposition.SPACING, NpcComposition.facing(cell[0], cell[1]), true, unfit, builders, reach);
                if (spot.isPresent()) {
                    return spot;
                }
            }
            RoadRules roads = RoadSettings.rules();
            int wall = switch (site.level()) {
                case SMALL -> roads.wallRadius();
                case MEDIUM -> roads.wallRadius() * 3 / 2;
                case LARGE -> roads.wallRadius() * 2;
            };
            first = Math.max(composition.rings() + 1, (wall + NpcComposition.PLOT / 2 + 1 + KEEP_CLEAR + NpcComposition.SPACING - 1)
                    / NpcComposition.SPACING);
            outside = NpcSpawner.outsideSpots(site, composition);
        }
        List<int[]> cells = roadCells(server, id);
        for (int ring = first; ring <= first + 1; ring++) {
            for (int gx = -ring; gx <= ring; gx++) {
                for (int gz = -ring; gz <= ring; gz++) {
                    if (Math.max(Math.abs(gx), Math.abs(gz)) != ring) {
                        continue;
                    }
                    int cx = site.x() + gx * NpcComposition.SPACING;
                    int cz = site.z() + gz * NpcComposition.SPACING;
                    if (near(outside, cx, cz, NpcComposition.PLOT / 2 + 6 + KEEP_CLEAR) || near(cells, cx, cz, NpcComposition.PLOT / 2 + KEEP_CLEAR)) {
                        LOGGER.debug("No place for {} at {} {}: a field or a road", template.id(), cx, cz);
                        continue;
                    }
                    Optional<Spot> spot = spot(state, id, dimension, template, cx, y, cz, NpcComposition.facing(gx, gz), false, unfit, builders,
                            reach);
                    if (spot.isPresent()) {
                        return spot;
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Where the post of the builder of a kingdom of the world stands on the square of its throne.
     */
    static BlockPos builderPost(NpcSite site) {
        return new BlockPos(site.x() - 5, site.groundY() + 1, site.z() - 5);
    }

    private static Optional<Spot> spot(KingdomWorldState state, UUID id, String dimension, BuildingTemplate template, int cx, int y, int cz,
                                       int facing, boolean levelled, Set<Long> unfit, List<io.github.sekerator.kingdomworld.core.citizen.WorldPos> builders, int reach) {
        Placement placement = new Placement(facing, false);
        BlockPos anchor = NpcSpawner.anchor(template, cx, y, cz, placement);
        if (unfit.contains(anchor.asLong())) {
            return Optional.empty();
        }
        Box box = template.box(anchor, placement);
        io.github.sekerator.kingdomworld.core.kingdom.Outcome lay = state.checkLay(null, id, dimension, box, ConstructionSettings.rules());
        if (!lay.ok()) {
            LOGGER.debug("No place for {} at {} {}: {}", template.id(), cx, cz, lay.error());
            return Optional.empty();
        }
        boolean reached = false;
        for (io.github.sekerator.kingdomworld.core.citizen.WorldPos post : builders) {
            int dx = Math.max(0, Math.max(box.minX() - post.x(), post.x() - box.maxX()));
            int dz = Math.max(0, Math.max(box.minZ() - post.z(), post.z() - box.maxZ()));
            if (post.dimension().equals(dimension) && dx * dx + dz * dz <= reach * reach) {
                reached = true;
                break;
            }
        }
        if (!reached) {
            LOGGER.debug("No place for {} at {} {}: no builder within reach", template.id(), cx, cz);
        }
        return reached ? Optional.of(new Spot(template, anchor, placement, box, levelled)) : Optional.empty();
    }

    private static boolean near(List<int[]> points, int x, int z, int distance) {
        for (int[] point : points) {
            if (Math.abs(point[0] - x) <= distance && Math.abs(point[1] - z) <= distance) {
                return true;
            }
        }
        return false;
    }

    private static List<int[]> roadCells(MinecraftServer server, UUID id) {
        List<int[]> cells = new ArrayList<>();
        for (Road road : RoadWork.state(server).roadsOf(id)) {
            for (RouteCell cell : road.cells()) {
                cells.add(new int[]{cell.x(), cell.z()});
            }
        }
        return cells;
    }

    /**
     * Free chunks next to the land of a kingdom it could claim by the rules of land (no other kingdom near), nearest to its
     * capital first. Whether players lived there is looked at when the chunk is claimed.
     */
    static List<Long> claimable(MinecraftServer server, UUID id, int max) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        Optional<Territory> territory = state.territory().territory(id);
        if (territory.isEmpty()) {
            return List.of();
        }
        String dimension = territory.get().dimension();
        int buffer = KingdomWorldApi.rules().claimBufferChunks();
        int cx = territory.get().capital().x() >> 4;
        int cz = territory.get().capital().z() >> 4;
        Set<Long> seen = new HashSet<>();
        List<Long> result = new ArrayList<>();
        Set<Long> unfit = UNFIT.getOrDefault(id, Set.of());
        for (long chunk : territory.get().chunks()) {
            int x = ChunkKey.x(chunk);
            int z = ChunkKey.z(chunk);
            for (int[] side : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                long next = ChunkKey.of(x + side[0], z + side[1]);
                if (!seen.add(next) || state.territory().ownerOf(dimension, next) != null || unfit.contains(next)
                        || state.territory().hasForeignLandWithin(dimension, next, buffer, id)) {
                    continue;
                }
                result.add(next);
            }
        }
        result.sort(Comparator.comparingDouble(chunk -> Math.hypot(ChunkKey.x(chunk) - cx, ChunkKey.z(chunk) - cz)));
        return result.size() > max ? List.copyOf(result.subList(0, max)) : result;
    }

    static void unfitChunk(UUID kingdomId, long chunk) {
        UNFIT.computeIfAbsent(kingdomId, id -> ConcurrentHashMap.newKeySet()).add(chunk);
    }

    /**
     * A place for a new town at the distance of towns from the capital, where a city may stand by the rules of roads and no other
     * kingdom holds or touches the land of its square or of the way to it; empty if there is none.
     */
    static Optional<int[]> townSpot(MinecraftServer server, NpcKingdom npc) {
        UUID id = npc.kingdomId();
        KingdomWorldState state = KingdomWorldApi.state(server);
        Optional<Territory> territory = state.territory().territory(id);
        if (territory.isEmpty()) {
            return Optional.empty();
        }
        String dimension = territory.get().dimension();
        int capitalX = territory.get().capital().x();
        int capitalZ = territory.get().capital().z();
        int distance = NpcWork.state(server).site(npc.siteId()).map(site -> NpcCity.townDistance(site.level(), site.layout()))
                .orElse(NpcSettings.rules().townDistance());
        int buffer = KingdomWorldApi.rules().claimBufferChunks() + 1;
        List<int[]> capitals = new ArrayList<>();
        for (Territory other : state.territory().territories()) {
            if (other.capital().dimension().equals(dimension)) {
                capitals.add(new int[]{other.capital().x(), other.capital().z()});
            }
        }
        Set<Long> unfit = UNFIT.getOrDefault(id, Set.of());
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4 + Math.PI / 8;
            int x = capitalX + (int) Math.round(Math.cos(angle) * distance);
            int z = capitalZ + (int) Math.round(Math.sin(angle) * distance);
            if (unfit.contains(BlockPos.asLong(x, 0, z))) {
                continue;
            }
            if (!RoadWork.state(server).checkCity(id, dimension, x, z, capitals, RoadSettings.rules()).ok()) {
                continue;
            }
            boolean free = true;
            for (long chunk : townChunks(capitalX, capitalZ, x, z)) {
                UUID owner = state.territory().ownerOf(dimension, chunk);
                if ((owner != null && !owner.equals(id)) || state.territory().hasForeignLandWithin(dimension, chunk, buffer, id)) {
                    free = false;
                    break;
                }
            }
            if (free) {
                return Optional.of(new int[]{x, z});
            }
        }
        return Optional.empty();
    }

    /**
     * The chunks of a new town: the way from the capital and the square around the town hall, in the order they are claimed.
     */
    static List<Long> townChunks(int capitalX, int capitalZ, int x, int z) {
        Set<Long> keys = new LinkedHashSet<>();
        for (long chunk : NpcSpawner.corridor(capitalX >> 4, capitalZ >> 4, x >> 4, z >> 4)) {
            keys.add(ChunkKey.of(ChunkPos.getX(chunk), ChunkPos.getZ(chunk)));
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                keys.add(ChunkKey.of((x >> 4) + dx, (z >> 4) + dz));
            }
        }
        return new ArrayList<>(keys);
    }
}
