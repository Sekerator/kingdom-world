package io.github.sekerator.kingdomworld.core.construction;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The look of one building beyond its wood and stone: what its roof, the spires of its towers, its footing, its trim, its
 * plaster, its cloth, its shutters and its chimney are made of. A template is drawn with a fixed block for every part (the
 * stand-ins below), and a look puts other blocks in their place when the building is laid, so that one template looks
 * different from plot to plot. A part the look does not name stays as the template has it; {@link #REMOVED} leaves the part
 * out of the building (a house without shutters or without a chimney).
 *
 * <p>Written as text, {@code roof=minecraft:deepslate_tiles,cloth=blue,chimney=-}, so that it travels inside
 * {@link Materials} and is kept with a construction and a building.
 */
public final class Look {
    /** The value of a part that is left out of the building. */
    public static final String REMOVED = "-";
    /** Longest text of a look. */
    public static final int MAX_TEXT = 512;
    public static final Look NONE = new Look(new EnumMap<>(Part.class));

    /**
     * A part of a look, the blocks of the template that stand for it and what may take their place.
     */
    public enum Part {
        /** Roofs of houses and halls: the planks, stairs and slabs of dark oak, mangrove and warped wood. A wood or a stone. */
        ROOF,
        /** Spires and cones of towers: dark prismarine, its stairs and slab. A wood or a stone. */
        SPIRE,
        /** Footings, quoins and rubble: cobblestone, its stairs, slab and wall. A stone. */
        ROUGH,
        /** Cornices, sills and steps: polished andesite, its stairs and slab. A stone. */
        TRIM,
        /** Carved accents: chiseled stone bricks. Any block. */
        CARVED,
        /** Plaster between timbers: white terracotta. Any block. */
        PLASTER,
        /** Cloth of the colour of the house: red wool, carpets, banners and beds. A colour of dye. */
        CLOTH,
        /** Shutters: spruce trapdoors. A wood, or removed. */
        SHUTTER,
        /** The chimney: bricks, their stairs, slab and wall. A stone, or removed. */
        CHIMNEY;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Part> byId(String id) {
            for (Part part : values()) {
                if (part.id().equals(id)) {
                    return Optional.of(part);
                }
            }
            return Optional.empty();
        }

        /**
         * Whether the part may be left out of a building.
         */
        public boolean removable() {
            return this == SHUTTER || this == CHIMNEY;
        }
    }

    private final EnumMap<Part, String> parts;

    private Look(EnumMap<Part, String> parts) {
        this.parts = parts;
    }

    /**
     * A look of the given parts; empty values are left out.
     */
    public static Look of(Map<Part, String> parts) {
        EnumMap<Part, String> copy = new EnumMap<>(Part.class);
        parts.forEach((part, value) -> {
            if (value != null && !value.isEmpty() && !value.contains(",") && !value.contains("=")) {
                copy.put(part, value);
            }
        });
        return copy.isEmpty() ? NONE : new Look(copy);
    }

    /**
     * A look from its text; unknown parts and malformed pairs are left out, so that a look written by a newer version still
     * reads.
     */
    public static Look parse(String text) {
        if (text == null || text.isEmpty() || text.length() > MAX_TEXT) {
            return NONE;
        }
        EnumMap<Part, String> parts = new EnumMap<>(Part.class);
        for (String pair : text.split(",")) {
            int equals = pair.indexOf('=');
            if (equals <= 0 || equals == pair.length() - 1) {
                continue;
            }
            String value = pair.substring(equals + 1);
            Part.byId(pair.substring(0, equals)).ifPresent(part -> parts.put(part, value));
        }
        return parts.isEmpty() ? NONE : new Look(parts);
    }

    /**
     * The look as text, its parts in a fixed order.
     */
    public String text() {
        StringBuilder text = new StringBuilder();
        parts.forEach((part, value) -> {
            if (text.length() > 0) {
                text.append(',');
            }
            text.append(part.id()).append('=').append(value);
        });
        return text.toString();
    }

    /**
     * What takes the place of a part, or empty when the template keeps its own.
     */
    public String get(Part part) {
        return parts.getOrDefault(part, "");
    }

    public boolean isEmpty() {
        return parts.isEmpty();
    }

    /**
     * This look with one part set, or kept as the template has it for an empty value.
     */
    public Look with(Part part, String value) {
        EnumMap<Part, String> copy = new EnumMap<>(parts);
        if (value == null || value.isEmpty()) {
            copy.remove(part);
        } else {
            copy.put(part, value);
        }
        return of(copy);
    }

    public Map<Part, String> parts() {
        return Map.copyOf(parts);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Look look && look.parts.equals(parts);
    }

    @Override
    public int hashCode() {
        return parts.hashCode();
    }

    @Override
    public String toString() {
        return text();
    }
}
