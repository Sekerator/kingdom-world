package io.github.sekerator.kingdomworld.core.production;

import java.util.Locale;
import java.util.Optional;

/**
 * Kinds of recipes citizens make, each at its own kind of workstation.
 */
public enum RecipeKind {
    /** A crafting table recipe, shaped or shapeless. */
    CRAFTING("crafting_table", false),
    STONECUTTING("stonecutter", false),
    /** An upgrade at the smithing table (netherite gear and the like). */
    SMITHING("smithing_table", false),
    SMELTING("furnace", true),
    BLASTING("blast_furnace", true),
    SMOKING("smoker", true),
    /** Cooking on a campfire, which burns without fuel. */
    CAMPFIRE("campfire", false);

    private final String station;
    private final boolean fuel;

    RecipeKind(String station, boolean fuel) {
        this.station = station;
        this.fuel = fuel;
    }

    /**
     * The kind of workstation the recipe is made at, as {@link Stations} names it.
     */
    public String station() {
        return station;
    }

    /**
     * Whether the workstation burns fuel.
     */
    public boolean needsFuel() {
        return fuel;
    }

    /**
     * Whether the recipe cooks in a furnace-like block over time, rather than being put together by hand.
     */
    public boolean cooks() {
        return this == SMELTING || this == BLASTING || this == SMOKING || this == CAMPFIRE;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<RecipeKind> byId(String id) {
        for (RecipeKind kind : values()) {
            if (kind.id().equals(id)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
