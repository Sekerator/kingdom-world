package io.github.sekerator.kingdomworld.common.construction;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.road.CityAreas;
import io.github.sekerator.kingdomworld.common.road.RoadFootprint;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.Commission;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.construction.PlotRings;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The places a building ordered for a city may stand, by the data alone: on rings around its town hall (around the throne for
 * the capital), every one wholly inside the square of the city (so that the builders of the city reach it), with its entrance
 * towards the town hall, where the kingdom may lay it out (its land, no other construction or building, the limit of the
 * server) and two blocks clear of every road and wall. What stands there is looked at in the world afterwards.
 */
public final class CityPlots {
    /** Blocks a building keeps clear of roads and walls. */
    static final int ROAD_MARGIN = 2;
    /** The heights the data looks at for a place whose ground is not known yet: every construction above or below counts. */
    private static final int LOWEST = -4096;
    private static final int HIGHEST = 4096;

    private CityPlots() {
    }

    /**
     * A place for a building: where its anchor goes (its height is found in the world), how it is turned and the box it takes.
     */
    public record Plot(BlockPos anchor, Placement placement, Box box) {
    }

    /**
     * The middle of a city, its town hall or the throne of the capital, if the city is one of the kingdom.
     */
    public static Optional<BlockPos> middle(MinecraftServer server, UUID kingdomId, long cityId) {
        if (cityId == Commission.CAPITAL) {
            return KingdomWorldApi.state(server).territory().territory(kingdomId).map(Territory::capital)
                    .map(capital -> new BlockPos(capital.x(), capital.y(), capital.z()));
        }
        return RoadWork.state(server).city(cityId).filter(city -> city.kingdomId().equals(kingdomId))
                .map(city -> new BlockPos(city.x(), city.y(), city.z()));
    }

    /**
     * The dimension of a city.
     */
    public static Optional<String> dimension(MinecraftServer server, UUID kingdomId, long cityId) {
        if (cityId == Commission.CAPITAL) {
            return KingdomWorldApi.state(server).territory().territory(kingdomId).map(territory -> territory.capital().dimension());
        }
        return RoadWork.state(server).city(cityId).filter(city -> city.kingdomId().equals(kingdomId)).map(City::dimension);
    }

    /**
     * The places of a city for a template, the nearest to its middle first, as many as asked at most.
     *
     * @param unfit anchors found unfit in the world before, as {@link BlockPos#asLong()}
     */
    public static List<Plot> places(MinecraftServer server, UUID kingdomId, long cityId, BuildingTemplate template, @Nullable UUID actor,
                                    Set<Long> unfit, int most) {
        Optional<BlockPos> middle = middle(server, kingdomId, cityId);
        Optional<String> dimension = dimension(server, kingdomId, cityId);
        Optional<CityAreas.Area> area = CityAreas.of(server, kingdomId).stream().filter(found -> found.cityId() == cityId).findFirst();
        if (middle.isEmpty() || dimension.isEmpty() || area.isEmpty()) {
            return List.of();
        }
        BlockPos hall = middle.get();
        int cx = area.get().x();
        int cz = area.get().z();
        int radius = area.get().radius();
        List<int[]> footprint = within(RoadFootprint.roadsAndWalls(server, kingdomId), cx, cz, radius + ROAD_MARGIN + 1);
        List<Plot> plots = new ArrayList<>();
        for (int[] point : PlotRings.around(cx, cz, radius)) {
            Placement placement = Placement.toward(template.entrance(), hall.getX() - point[0], hall.getZ() - point[1]);
            BlockPos anchor = ConstructionWork.anchorAt(template, point[0], hall.getY(), point[1], placement);
            if (unfit.contains(anchor.asLong())) {
                continue;
            }
            Box box = template.box(anchor, placement);
            if (!PlotRings.inside(box, cx, cz, radius) || box.minX() <= hall.getX() && hall.getX() <= box.maxX()
                    && box.minZ() <= hall.getZ() && hall.getZ() <= box.maxZ()) {
                continue;
            }
            if (fits(server, kingdomId, dimension.get(), box, actor, footprint)) {
                plots.add(new Plot(anchor, placement, box));
                if (plots.size() >= most) {
                    break;
                }
            }
        }
        return plots;
    }

    /**
     * The columns of a list within a distance of a middle along x and z, so that the places of a city look at the roads near it
     * only.
     */
    static List<int[]> within(List<int[]> cells, int cx, int cz, int distance) {
        List<int[]> near = new ArrayList<>();
        for (int[] cell : cells) {
            if (Math.abs(cell[0] - cx) <= distance && Math.abs(cell[1] - cz) <= distance) {
                near.add(cell);
            }
        }
        return near;
    }

    /**
     * Whether the kingdom may lay a building out over a box by the data: its land, nothing laid out or registered there (at any
     * height, since the ground is not known yet), the limit of the server, and two blocks clear of roads and walls; the box is
     * widened by a block for all but the roads.
     */
    static boolean fits(MinecraftServer server, UUID kingdomId, String dimension, Box box, @Nullable UUID actor, List<int[]> footprint) {
        Box column = new Box(box.minX() - 1, LOWEST, box.minZ() - 1, box.maxX() + 1, HIGHEST, box.maxZ() + 1);
        KingdomWorldState state = KingdomWorldApi.state(server);
        if (!state.checkLay(actor, kingdomId, dimension, column, ConstructionSettings.rules()).ok()) {
            return false;
        }
        return !RoadFootprint.near(footprint, box, ROAD_MARGIN);
    }
}
