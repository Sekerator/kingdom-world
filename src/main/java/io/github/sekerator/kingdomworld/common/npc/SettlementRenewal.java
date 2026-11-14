package io.github.sekerator.kingdomworld.common.npc;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplate;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplates;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.npc.NpcComposition;
import io.github.sekerator.kingdomworld.core.npc.Settlement;
import io.github.sekerator.kingdomworld.core.npc.SettlementLayouts;
import io.github.sekerator.kingdomworld.core.npc.TemplateCatalog;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Completing a settlement that stands already: a building of its plan goes up only on free ground, where nothing but the land
 * as it grew (or as the kingdom levelled it) lies, no building, wall or block of the mod of anybody stands and no container
 * holds anything; what players built or placed is never touched. A building of the plan its place has no room for (a market,
 * a tavern, a storehouse, a workshop, a well) looks for free ground elsewhere on the land of the settlement; a house waits for
 * its own place.
 */
final class SettlementRenewal {
    /** Blocks around a plot that must be free as well: the way round a building. */
    static final int MARGIN = 1;
    /** Layers above the street looked at for what stands in the way, at most. */
    private static final int HEIGHT = 24;
    /** Layers under the street looked at: a cellar or a chest dug in. */
    private static final int BELOW = 3;
    /** Blocks between the places looked at for a building that moves. */
    private static final int STEP = 4;

    private SettlementRenewal() {
    }

    /**
     * The top of the ground the kingdom laid at a place of a settlement when it rose, {dx, dz} from its middle: its paving,
     * paths and soil may lie under a building of its plan.
     */
    interface Ground {
        BlockState top(int dx, int dz);
    }

    /**
     * Whether a settlement of the plan stands but has not all its parts yet: raised before the settlements lived as their rank
     * asks, it is completed once when a player comes near.
     */
    static boolean incomplete(Settlement settlement) {
        return settlement.status() == Settlement.Status.STANDING && !settlement.given(Settlement.GIVEN_LIVING);
    }

    /**
     * Whether a building of a kind stands, or is being built, anywhere on the land of a settlement: a settlement needs one
     * market, one storehouse and so on, wherever it is.
     */
    static boolean kindStands(MinecraftServer server, Settlement settlement, TemplateCatalog.Kind kind) {
        int reach = SettlementLayouts.land(settlement.tier()) * 8;
        KingdomWorldState state = KingdomWorldApi.state(server);
        for (Building building : state.construction().buildingsOf(settlement.kingdomId())) {
            if (near(settlement, building.anchor().x(), building.anchor().z(), reach)
                    && TemplateCatalog.get(building.templateId()).map(entry -> entry.kind() == kind).orElse(false)) {
                return true;
            }
        }
        for (Construction construction : state.construction().of(settlement.kingdomId())) {
            if (near(settlement, construction.anchor().x(), construction.anchor().z(), reach)
                    && TemplateCatalog.get(construction.templateId()).map(entry -> entry.kind() == kind).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    private static boolean near(Settlement settlement, int x, int z, int reach) {
        return Math.abs(x - settlement.x()) <= reach && Math.abs(z - settlement.z()) <= reach;
    }

    /**
     * Whether a building of the plan moves to free ground elsewhere when its own place has no room: the buildings a settlement
     * works and lives by, not its houses, towers and gates.
     */
    static boolean movable(NpcComposition.Plot plot) {
        TemplateCatalog.Kind kind = TemplateCatalog.of(plot.template()).kind();
        return switch (kind) {
            case MARKET, TAVERN, STOREHOUSE, STORAGE_YARD, WORKSHOP, WELL, BARRACKS -> true;
            default -> false;
        };
    }

    /**
     * Whether a building of the plan is being built, or stands, at its own place.
     */
    static boolean started(MinecraftServer server, UUID kingdomId, NpcComposition.Plot plot, int x, int y, int z) {
        Optional<BuildingTemplate> template = BuildingTemplates.get(plot.template());
        if (template.isEmpty()) {
            return true;
        }
        BlockPos anchor = NpcSpawner.anchor(template.get(), x, y, z, new Placement(plot.facing(), plot.mirror()));
        KingdomWorldState state = KingdomWorldApi.state(server);
        for (Building building : state.construction().buildingsOf(kingdomId)) {
            if (building.templateId().equals(plot.template()) && building.anchor().x() == anchor.getX() && building.anchor().z() == anchor.getZ()) {
                return true;
            }
        }
        for (Construction construction : state.construction().of(kingdomId)) {
            if (construction.templateId().equals(plot.template()) && construction.anchor().x() == anchor.getX()
                    && construction.anchor().z() == anchor.getZ()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a plot of a settlement may be built on as the ground lies now: within its land, its footprint and a block around
     * it clear of every building, construction and wall, and of every block that is not of the land.
     *
     * @param x the middle of the plot in the world
     * @param z the middle of the plot in the world
     */
    static boolean free(ServerLevel level, Settlement settlement, NpcComposition.Plot plot, int x, int z, Ground ground,
                        @org.jetbrains.annotations.Nullable java.util.Set<Long> roads) {
        int[] box = NpcComposition.footprint(new NpcComposition.Plot(plot.template(), 0, 0, plot.facing(), plot.mirror(), plot.quarter()));
        int minX = x + box[0] - MARGIN;
        int minZ = z + box[1] - MARGIN;
        int maxX = x + box[2] + MARGIN;
        int maxZ = z + box[3] + MARGIN;
        int reach = SettlementLayouts.land(settlement.tier()) * 8;
        if (minX < settlement.x() - reach || maxX > settlement.x() + reach || minZ < settlement.z() - reach || maxZ > settlement.z() + reach) {
            return false;
        }
        int y = settlement.groundY();
        MinecraftServer server = level.getServer();
        Box area = new Box(minX, y - BELOW, minZ, maxX, y + HEIGHT, maxZ);
        KingdomWorldState state = KingdomWorldApi.state(server);
        for (Building building : state.construction().buildingsOf(settlement.kingdomId())) {
            if (building.box().intersects(area)) {
                return false;
            }
        }
        for (Construction construction : state.construction().of(settlement.kingdomId())) {
            if (construction.box().intersects(area)) {
                return false;
            }
        }
        for (Wall wall : RoadWork.state(server).walls()) {
            if (!wall.kingdomId().equals(settlement.kingdomId()) || Math.abs(wall.centerX() - settlement.x()) > 4 * reach
                    || Math.abs(wall.centerZ() - settlement.z()) > 4 * reach) {
                continue;
            }
            for (RouteCell cell : wall.cells()) {
                if (cell.x() >= minX && cell.x() <= maxX && cell.z() >= minZ && cell.z() <= maxZ) {
                    return false;
                }
            }
        }
        UUID owner = settlement.kingdomId();
        // The residence of a large city stands round its town hall, as a castle round a throne.
        long hall = SettlementLayouts.RESIDENCE.equals(plot.quarter()) ? BlockPos.asLong(settlement.x(), y + 1, settlement.z()) : Long.MIN_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                if (roads != null && roads.contains(BlockPos.asLong(bx, 0, bz))) {
                    // A road of the kingdom runs there.
                    return false;
                }
                if (!level.hasChunk(bx >> 4, bz >> 4) || !owner.equals(KingdomWorldApi.ownerAt(level, pos.set(bx, y, bz)))) {
                    return false;
                }
                // A hollow deeper than the blocks looked at would be filled over what lies under it.
                if (NpcGround.groundAt(level, bx, bz) < y - BELOW) {
                    return false;
                }
                if (!column(level, pos, bx, bz, y, y - BELOW, y + HEIGHT, ground.top(bx - settlement.x(), bz - settlement.z()), hall)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * See {@link io.github.sekerator.kingdomworld.common.construction.NaturalGround#column}.
     */
    static boolean column(ServerLevel level, BlockPos.MutableBlockPos pos, int x, int z, int street, int from, int to, BlockState laid,
                          long hall) {
        return io.github.sekerator.kingdomworld.common.construction.NaturalGround.column(level, pos, x, z, street, from, to, laid, hall);
    }

    static boolean placedLeaves(BlockState state) {
        return io.github.sekerator.kingdomworld.common.construction.NaturalGround.placedLeaves(state);
    }

    static boolean ground(BlockState state) {
        return io.github.sekerator.kingdomworld.common.construction.NaturalGround.ground(state);
    }

    static boolean wild(BlockState state) {
        return io.github.sekerator.kingdomworld.common.construction.NaturalGround.wild(state);
    }

    /**
     * Whether the ground of a field of eleven by eleven blocks around a middle is free: the land of the kingdom, nothing on it
     * but what grew there.
     */
    static boolean fieldFree(ServerLevel level, Settlement settlement, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Box area = new Box(x - 6, settlement.groundY() - BELOW, z - 6, x + 6, settlement.groundY() + HEIGHT, z + 6);
        for (Building building : KingdomWorldApi.state(level.getServer()).construction().buildingsOf(settlement.kingdomId())) {
            if (building.box().intersects(area)) {
                return false;
            }
        }
        for (int bx = x - 6; bx <= x + 6; bx++) {
            for (int bz = z - 6; bz <= z + 6; bz++) {
                if (!level.hasChunk(bx >> 4, bz >> 4) || !settlement.kingdomId().equals(KingdomWorldApi.ownerAt(level, pos.set(bx, settlement.groundY(), bz)))) {
                    return false;
                }
                // A field lies on the land as it is: its own ground, and nothing but what grew over it.
                int top = NpcGround.groundAt(level, bx, bz);
                if (!column(level, pos, bx, bz, top, top - 1, top + 3, null, Long.MIN_VALUE)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Free ground on the land of a settlement for a building that moves, the nearest to its middle: looked at a few places a
     * call, from where the last call stopped.
     *
     * @param cursor the place to look at next, kept between calls; set to -1 when every place was looked at
     * @param deadline when the time of the step is over: no more places are looked at then
     * @return the middle of the free ground in the world, {x, z, facing}, if found
     */
    static Optional<int[]> elsewhere(ServerLevel level, Settlement settlement, NpcComposition.Plot plot, int[] cursor, long deadline, Ground ground,
                                     java.util.Set<Long> roads) {
        List<int[]> spots = spots(settlement);
        while (cursor[0] >= 0 && cursor[0] < spots.size() && System.nanoTime() - deadline < 0) {
            int[] spot = spots.get(cursor[0]++);
            // Its front looks at the middle of the settlement: north for a place south of it, and so on.
            int facing = Math.abs(spot[1]) >= Math.abs(spot[0]) ? (spot[1] > 0 ? 2 : 0) : (spot[0] > 0 ? 3 : 1);
            NpcComposition.Plot moved = new NpcComposition.Plot(plot.template(), spot[0], spot[1], facing, plot.mirror(), plot.quarter());
            int x = settlement.x() + spot[0];
            int z = settlement.z() + spot[1];
            if (free(level, settlement, moved, x, z, ground, roads)) {
                return Optional.of(new int[]{x, z, facing});
            }
        }
        if (cursor[0] >= spots.size()) {
            cursor[0] = -1;
        }
        return Optional.empty();
    }

    /**
     * The places a building that moves may take, {dx, dz} from the middle of a settlement, the nearest first; not on its
     * square or its street.
     */
    static List<int[]> spots(Settlement settlement) {
        int reach = SettlementLayouts.land(settlement.tier()) * 8 - 6;
        List<int[]> spots = new ArrayList<>();
        for (int dx = -reach; dx <= reach; dx += STEP) {
            for (int dz = -reach; dz <= reach; dz += STEP) {
                if (Math.abs(dz) <= 8 || Math.abs(dx) <= 6 && Math.abs(dz) <= 12) {
                    continue;
                }
                spots.add(new int[]{dx, dz});
            }
        }
        spots.sort(java.util.Comparator.comparingInt(spot -> spot[0] * spot[0] + spot[1] * spot[1]));
        return spots;
    }

    /**
     * Levels the ground under a plot and a block around it to the street of the settlement, column by column within a budget;
     * only ground that {@link #free} found free is ever levelled.
     *
     * @param from the column to begin with, kept between calls
     * @return the column to go on with, or -1 when the ground is level
     */
    static int level(ServerLevel level, Settlement settlement, NpcComposition.Plot plot, int x, int z, int from, int budget) {
        int[] box = NpcComposition.footprint(new NpcComposition.Plot(plot.template(), 0, 0, plot.facing(), plot.mirror(), plot.quarter()));
        int minX = x + box[0] - MARGIN;
        int minZ = z + box[1] - MARGIN;
        int sizeX = box[2] - box[0] + 1 + 2 * MARGIN;
        int sizeZ = box[3] - box[1] + 1 + 2 * MARGIN;
        NpcGround.Soil soil = NpcGround.soilAt(level, x, z);
        int column = from;
        while (column < sizeX * sizeZ && budget > 0) {
            int bx = minX + column % sizeX;
            int bz = minZ + column / sizeX;
            budget -= Math.max(1, NpcGround.level(level, settlement.kingdomId(), bx, bz, settlement.groundY(), soil.top(), soil.fill()));
            column++;
        }
        return column >= sizeX * sizeZ ? -1 : column;
    }
}
