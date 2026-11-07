package io.github.sekerator.kingdomworld.core.construction;

import io.github.sekerator.kingdomworld.core.citizen.WorldPos;

import java.util.Objects;
import java.util.UUID;

/**
 * A finished building in the register of a kingdom: what template it was built from, where and how it was laid, what kind
 * of building it is and how much of it still stands. The register is what later mechanics build on: repairs and the roles
 * of buildings, and the damage of wars.
 *
 * @param type      kind of building of the template ({@code house}, {@code warehouse}, ...), for the roles of buildings
 * @param category  category of the template
 * @param builtTime game time the last block was placed
 * @param admin     whether an operator built it at once
 * @param integrity share of the template's blocks in place, from 0 to 100; -1 until it was first looked at
 * @param checked   game time the integrity was last worked out, -1 never
 */
public record Building(long id, UUID kingdomId, String templateId, String type, String category, WorldPos anchor, Placement placement,
                       Box box, long fingerprint, long builtTime, boolean admin, int integrity, long checked) {
    /** Longest type or category name. */
    public static final int MAX_NAME = 64;

    public Building {
        Objects.requireNonNull(kingdomId, "kingdomId");
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(box, "box");
        integrity = Math.max(-1, Math.min(100, integrity));
    }

    public String dimension() {
        return anchor.dimension();
    }

    public Building withIntegrity(int percent, long time) {
        return new Building(id, kingdomId, templateId, type, category, anchor, placement, box, fingerprint, builtTime, admin, percent, time);
    }
}
