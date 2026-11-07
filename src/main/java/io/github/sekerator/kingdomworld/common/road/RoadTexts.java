package io.github.sekerator.kingdomworld.common.road;

import io.github.sekerator.kingdomworld.core.economy.Plurals;
import io.github.sekerator.kingdomworld.core.road.CityLevel;
import io.github.sekerator.kingdomworld.core.road.Crossing;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadClass;
import io.github.sekerator.kingdomworld.core.road.RoadLayout;
import io.github.sekerator.kingdomworld.core.road.Wall;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Texts of cities, roads and robbers: classes, states, and counts in every plural form.
 */
public final class RoadTexts {
    private static final String KEY = "kingdomworld.road.";
    private static final String COUNT = "kingdomworld.count.";

    private RoadTexts() {
    }

    public static Component className(RoadClass roadClass) {
        return Component.translatable(KEY + "class." + roadClass.id());
    }

    public static Component mode(Road.Mode mode) {
        return Component.translatable(KEY + "mode." + mode.id());
    }

    public static Component status(Road.Status status) {
        return Component.translatable(KEY + "status." + status.id());
    }

    public static Component crossingStatus(Crossing.Status status) {
        return Component.translatable(KEY + "crossing." + status.id());
    }

    public static Component material(RoadLayout.Material material) {
        return Component.translatable(KEY + "material." + material.id());
    }

    private static MutableComponent count(String what, long count) {
        return Component.translatable(COUNT + what + "." + Plurals.form(count).suffix(), count);
    }

    public static MutableComponent blocks(long count) {
        return count("blocks", count);
    }

    public static MutableComponent sections(long count) {
        return count("sections", count);
    }

    public static MutableComponent bandits(long count) {
        return count("bandits", count);
    }

    public static MutableComponent cities(long count) {
        return count("cities", count);
    }

    public static MutableComponent roads(long count) {
        return count("roads", count);
    }

    public static MutableComponent builders(long count) {
        return count("builders", count);
    }

    public static MutableComponent items(long count) {
        return count("items", count);
    }

    public static MutableComponent minutes(long count) {
        return count("minutes", count);
    }

    public static MutableComponent gates(long count) {
        return count("gates", count);
    }

    public static Component level(CityLevel level) {
        return Component.translatable(KEY + "level." + level.id());
    }

    public static Component wallClass(Wall.WallClass wallClass) {
        return Component.translatable(KEY + "wall." + wallClass.id());
    }
}
