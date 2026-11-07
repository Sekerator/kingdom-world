package io.github.sekerator.kingdomworld.core.construction;

import io.github.sekerator.kingdomworld.core.citizen.WorldPos;

import java.util.BitSet;
import java.util.Objects;
import java.util.UUID;

/**
 * A finished building in the register of a kingdom: what template it was built from, where and how it was laid, what kind
 * of building it is, what role it plays and at what level, which wood and stone replaced those of the template, and how
 * much of it still stands. The register is what the roles of buildings, repairs, upgrades and demolitions work on, and
 * later the damage of wars.
 *
 * @param type       kind of building of the template ({@code house}, {@code warehouse}, ...)
 * @param category   category of the template
 * @param role       role of the building (see {@link BuildingRoles}), empty for none
 * @param level      level of the building, from 1
 * @param materials  wood and stone that replaced those of the template
 * @param builtTime  game time the last block was placed
 * @param admin      whether an operator built it at once
 * @param integrity  share of the template's blocks in place, from 0 to 100; -1 until it was first looked at
 * @param checked    game time the integrity was last worked out, -1 never
 * @param autoRepair whether a repair is ordered by itself when the building falls below the threshold of the server
 * @param damaged    indices of the template blocks found missing or changed at the last look; a copy is kept
 */
public record Building(long id, UUID kingdomId, String templateId, String type, String category, String role, int level,
                       Materials materials, WorldPos anchor, Placement placement, Box box, long fingerprint, long builtTime,
                       boolean admin, int integrity, long checked, boolean autoRepair, BitSet damaged) {
    /** Longest type, category or role name. */
    public static final int MAX_NAME = 64;

    public Building {
        Objects.requireNonNull(kingdomId, "kingdomId");
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(materials, "materials");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(box, "box");
        level = Math.max(1, Math.min(BuildingRole.MAX_LEVEL, level));
        integrity = Math.max(-1, Math.min(100, integrity));
        damaged = damaged == null ? new BitSet() : (BitSet) damaged.clone();
    }

    /**
     * A building as the register of format 8 knew it: no role, level 1, the template's own materials.
     */
    public Building(long id, UUID kingdomId, String templateId, String type, String category, WorldPos anchor, Placement placement,
                    Box box, long fingerprint, long builtTime, boolean admin, int integrity, long checked) {
        this(id, kingdomId, templateId, type, category, "", 1, Materials.NONE, anchor, placement, box, fingerprint, builtTime, admin,
                integrity, checked, false, new BitSet());
    }

    public String dimension() {
        return anchor.dimension();
    }

    @Override
    public BitSet damaged() {
        return (BitSet) damaged.clone();
    }

    public int damagedCount() {
        return damaged.cardinality();
    }

    /**
     * The building after a look at its blocks.
     */
    public Building withIntegrity(int percent, long time, BitSet missing) {
        return new Building(id, kingdomId, templateId, type, category, role, level, materials, anchor, placement, box, fingerprint, builtTime,
                admin, percent, time, autoRepair, missing);
    }

    public Building withIntegrity(int percent, long time) {
        return withIntegrity(percent, time, damaged);
    }

    public Building withAutoRepair(boolean value) {
        return new Building(id, kingdomId, templateId, type, category, role, level, materials, anchor, placement, box, fingerprint, builtTime,
                admin, integrity, checked, value, damaged);
    }

    /**
     * The building after a repair: whole until it is looked at again.
     */
    public Building repaired(long time) {
        return withIntegrity(100, time, new BitSet());
    }

    /**
     * The building after an upgrade to another template.
     */
    public Building upgraded(String template, String newType, String newCategory, String newRole, int newLevel, Box newBox,
                             long newFingerprint, long time) {
        return new Building(id, kingdomId, template, newType, newCategory, newRole, newLevel, materials, anchor, placement, newBox,
                newFingerprint, builtTime, admin, 100, time, autoRepair, new BitSet());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Building that)) {
            return false;
        }
        return id == that.id && level == that.level && fingerprint == that.fingerprint && builtTime == that.builtTime && admin == that.admin
                && integrity == that.integrity && checked == that.checked && autoRepair == that.autoRepair && kingdomId.equals(that.kingdomId)
                && templateId.equals(that.templateId) && type.equals(that.type) && category.equals(that.category) && role.equals(that.role)
                && materials.equals(that.materials) && anchor.equals(that.anchor) && placement.equals(that.placement) && box.equals(that.box)
                && damaged.equals(that.damaged);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, kingdomId, templateId, level, integrity, checked);
    }
}
