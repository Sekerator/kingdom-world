package io.github.sekerator.kingdomworld.core.construction;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A blueprint a kingdom saved from a building that stands in the world: its blocks as block states in text (the data of
 * block entities is never kept), its name, category and role, where its anchor is and which side its entrance faces. It
 * belongs to the kingdom, lives in the kingdom data and shows in the "own" part of the catalog.
 *
 * @param palette the block states of the blueprint as text
 * @param blocks  for every position of the box ({@code (y * sizeZ + z) * sizeX + x}) the index into the palette, -1 for
 *                a position the blueprint leaves alone
 */
public record CustomBlueprint(long id, UUID kingdomId, String name, String category, String role, int sizeX, int sizeY, int sizeZ,
                              List<String> palette, int[] blocks, int anchorX, int anchorY, int anchorZ, Facing entrance, long created,
                              UUID author) {
    /** Longest name. */
    public static final int MAX_NAME = 32;
    /** Most states in a palette. */
    public static final int MAX_PALETTE = 4_096;
    /** The namespace of the ids of saved blueprints. */
    public static final String ID_PREFIX = "kingdomworld:custom/";

    public CustomBlueprint {
        Objects.requireNonNull(kingdomId, "kingdomId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(entrance, "entrance");
        Objects.requireNonNull(author, "author");
        palette = List.copyOf(palette);
        blocks = blocks.clone();
        if (id <= 0 || sizeX < 1 || sizeY < 1 || sizeZ < 1 || sizeX > 255 || sizeY > 255 || sizeZ > 255) {
            throw new IllegalArgumentException("Bad blueprint " + id + " of size " + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        if ((long) sizeX * sizeY * sizeZ != blocks.length) {
            throw new IllegalArgumentException("The blocks of blueprint " + id + " do not fill its box");
        }
        if (palette.size() > MAX_PALETTE) {
            throw new IllegalArgumentException("The palette of blueprint " + id + " is too large");
        }
        for (int block : blocks) {
            if (block < -1 || block >= palette.size()) {
                throw new IllegalArgumentException("Blueprint " + id + " names a state it does not have");
            }
        }
        if (anchorX < 0 || anchorY < 0 || anchorZ < 0 || anchorX >= sizeX || anchorY >= sizeY || anchorZ >= sizeZ) {
            throw new IllegalArgumentException("The anchor of blueprint " + id + " is outside it");
        }
    }

    @Override
    public int[] blocks() {
        return blocks.clone();
    }

    /**
     * The palette index at a position of the box, -1 for none.
     */
    public int at(int x, int y, int z) {
        return blocks[(y * sizeZ + z) * sizeX + x];
    }

    /**
     * The id of the blueprint among the templates.
     */
    public String templateId() {
        return ID_PREFIX + id;
    }

    /**
     * The number of a blueprint from its template id, or empty if the id is not one of a saved blueprint.
     */
    public static Optional<Long> number(String templateId) {
        if (templateId == null || !templateId.startsWith(ID_PREFIX)) {
            return Optional.empty();
        }
        try {
            long number = Long.parseLong(templateId.substring(ID_PREFIX.length()));
            return number > 0 ? Optional.of(number) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public CustomBlueprint renamed(String value) {
        return new CustomBlueprint(id, kingdomId, value, category, role, sizeX, sizeY, sizeZ, palette, blocks, anchorX, anchorY, anchorZ,
                entrance, created, author);
    }

    /**
     * Why a blueprint cannot be saved.
     */
    public enum Problem {
        /** The name is empty or too long. */
        BAD_NAME,
        /** The box is larger than templates may be. */
        TOO_BIG,
        /** Nothing in the box is built. */
        EMPTY,
        /** A block no item makes, or one of the blocks of the mod that are never built. */
        UNBUILDABLE,
        /** The role needs a bed and the box holds none. */
        NEEDS_BED,
        /** The role needs a container and the box holds none. */
        NEEDS_CONTAINER,
        /** No such role. */
        UNKNOWN_ROLE,
        /** The kingdom keeps as many blueprints as it may. */
        TOO_MANY;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Checks what a player wants to save, from what the server found in the box.
     *
     * @param role         the role chosen, empty for none
     * @param built        blocks the builders would place
     * @param unbuildable  blocks of the box no item makes or never built
     * @param beds         beds in the box
     * @param containers   containers in the box
     * @param kept         blueprints the kingdom has already
     * @param maxKept      most blueprints a kingdom may keep
     */
    public static Optional<Problem> check(String name, String role, int sizeX, int sizeY, int sizeZ, ConstructionRules rules, int built,
                                          int unbuildable, int beds, int containers, int kept, int maxKept) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME) {
            return Optional.of(Problem.BAD_NAME);
        }
        if (kept >= maxKept) {
            return Optional.of(Problem.TOO_MANY);
        }
        if (!rules.fits(sizeX, sizeY, sizeZ)) {
            return Optional.of(Problem.TOO_BIG);
        }
        if (unbuildable > 0) {
            return Optional.of(Problem.UNBUILDABLE);
        }
        if (built == 0) {
            return Optional.of(Problem.EMPTY);
        }
        if (!role.isEmpty()) {
            Optional<BuildingRole> found = BuildingRoles.get(role);
            if (found.isEmpty()) {
                return Optional.of(Problem.UNKNOWN_ROLE);
            }
            if (found.get().requirement() == BuildingRole.Requirement.BED && beds == 0) {
                return Optional.of(Problem.NEEDS_BED);
            }
            if (found.get().requirement() == BuildingRole.Requirement.CONTAINER && containers == 0) {
                return Optional.of(Problem.NEEDS_CONTAINER);
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CustomBlueprint that && id == that.id && kingdomId.equals(that.kingdomId) && name.equals(that.name)
                && category.equals(that.category) && role.equals(that.role) && Arrays.equals(blocks, that.blocks)
                && palette.equals(that.palette);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, kingdomId, name);
    }
}
