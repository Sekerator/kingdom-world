package io.github.sekerator.kingdomworld.client.map;

import io.github.sekerator.kingdomworld.common.network.data.RoadViews;

/**
 * Cities, roads and known camps of robbers the server sent for the maps, and the last plan of a road it found. Client
 * only.
 */
public final class ClientRoadCache {
    private static volatile RoadViews.MapData data = RoadViews.MapData.EMPTY;
    private static volatile RoadViews.PlanView plan;
    /** The parts of the kingdoms the server sent, in the order they came; changed on the client main thread only. */
    private static final java.util.Map<java.util.UUID, RoadViews.MapPart> PARTS = new java.util.LinkedHashMap<>();

    private ClientRoadCache() {
    }

    public static void replace(RoadViews.MapData value) {
        PARTS.clear();
        data = value == null ? RoadViews.MapData.EMPTY : value;
    }

    /**
     * Takes in a change the server sent: the parts of kingdoms that changed replace those kept, and the maps get all of them as one.
     */
    public static void apply(RoadViews.MapChange change) {
        if (change.reset()) {
            PARTS.clear();
        }
        change.dropped().forEach(PARTS::remove);
        for (RoadViews.MapPart part : change.parts()) {
            PARTS.put(part.kingdomId(), part);
        }
        java.util.List<RoadViews.MapCity> cities = new java.util.ArrayList<>();
        java.util.List<RoadViews.MapRoad> roads = new java.util.ArrayList<>();
        for (RoadViews.MapPart part : PARTS.values()) {
            cities.addAll(part.cities());
            roads.addAll(part.roads());
        }
        data = new RoadViews.MapData(cities, roads, change.camps(), change.walls());
    }

    /**
     * The settlement of the highest rank of a province among those the server sent, the first on a tie; null for none.
     */
    public static RoadViews.MapCity seatOf(int province) {
        RoadViews.MapCity best = null;
        for (RoadViews.MapCity city : data.cities()) {
            RoadViews.MapSettlement settlement = city.settlement();
            if (settlement != null && settlement.province() == province
                    && (best == null || settlement.tier().ordinal() < best.settlement().tier().ordinal())) {
                best = city;
            }
        }
        return best;
    }

    public static RoadViews.MapData data() {
        return data;
    }

    public static void setPlan(RoadViews.PlanView value) {
        plan = value;
    }

    public static RoadViews.PlanView plan() {
        return plan;
    }

    public static void clear() {
        PARTS.clear();
        data = RoadViews.MapData.EMPTY;
        plan = null;
    }
}
