package io.github.sekerator.kingdomworld.core.construction;

import java.util.Objects;

/**
 * The wood and the stone a building is built of instead of those its template names: a wood species such as
 * {@code minecraft:spruce} and a stone such as {@code minecraft:deepslate_bricks}. Empty means the template's own;
 * {@link #AUTO} for wood means the species that grows where the building stands, decided when it is laid out. A
 * {@link Look} may name more: the roof, the footing, the plaster, the colour of the cloth, shutters and chimney; with a look
 * the stone stands only for the walls of the template, its other stones follow the look.
 *
 * @param look the text of a look, empty for none
 */
public record Materials(String wood, String stone, String look) {
    public static final Materials NONE = new Materials("", "", "");
    /** The wood of the place, chosen by the biome when the construction is laid out. */
    public static final String AUTO = "auto";
    /** Longest id of a wood or a stone. */
    public static final int MAX_ID = 128;

    public Materials {
        Objects.requireNonNull(wood, "wood");
        Objects.requireNonNull(stone, "stone");
        look = look == null ? "" : look;
        wood = wood.length() > MAX_ID ? "" : wood;
        stone = stone.length() > MAX_ID ? "" : stone;
        look = look.length() > Look.MAX_TEXT ? "" : look;
    }

    public Materials(String wood, String stone) {
        this(wood, stone, "");
    }

    public Materials(String wood, String stone, Look look) {
        this(wood, stone, look.text());
    }

    public boolean none() {
        return wood.isEmpty() && stone.isEmpty() && look.isEmpty();
    }

    public Materials withWood(String value) {
        return new Materials(value, stone, look);
    }

    public Materials withLook(Look value) {
        return new Materials(wood, stone, value.text());
    }

    /**
     * The look, read from its text.
     */
    public Look appearance() {
        return Look.parse(look);
    }

    /**
     * A key for caches of templates built of these materials.
     */
    public String key() {
        return look.isEmpty() ? wood + "|" + stone : wood + "|" + stone + "|" + look;
    }
}
