package io.github.sekerator.kingdomworld.client.map;

import io.github.sekerator.kingdomworld.common.network.data.RoadViews;

/**
 * Cities, roads and known camps of robbers the server sent for the maps, and the last plan of a road it found. Client
 * only.
 */
public final class ClientRoadCache {
    private static volatile RoadViews.MapData data = RoadViews.MapData.EMPTY;
    private static volatile RoadViews.PlanView plan;

    private ClientRoadCache() {
    }

    public static void replace(RoadViews.MapData value) {
        data = value == null ? RoadViews.MapData.EMPTY : value;
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
        data = RoadViews.MapData.EMPTY;
        plan = null;
    }
}
