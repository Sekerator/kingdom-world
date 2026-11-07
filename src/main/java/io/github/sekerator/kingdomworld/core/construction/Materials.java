package io.github.sekerator.kingdomworld.core.construction;

import java.util.Objects;

/**
 * The wood and the stone a building is built of instead of those its template names: a wood species such as
 * {@code minecraft:spruce} and a stone such as {@code minecraft:deepslate_bricks}. Empty means the template's own;
 * {@link #AUTO} for wood means the species that grows where the building stands, decided when it is laid out.
 */
public record Materials(String wood, String stone) {
    public static final Materials NONE = new Materials("", "");
    /** The wood of the place, chosen by the biome when the construction is laid out. */
    public static final String AUTO = "auto";
    /** Longest id of a wood or a stone. */
    public static final int MAX_ID = 128;

    public Materials {
        Objects.requireNonNull(wood, "wood");
        Objects.requireNonNull(stone, "stone");
        wood = wood.length() > MAX_ID ? "" : wood;
        stone = stone.length() > MAX_ID ? "" : stone;
    }

    public boolean none() {
        return wood.isEmpty() && stone.isEmpty();
    }

    public Materials withWood(String value) {
        return new Materials(value, stone);
    }

    /**
     * A key for caches of templates built of these materials.
     */
    public String key() {
        return wood + "|" + stone;
    }
}
