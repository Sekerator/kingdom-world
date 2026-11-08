package io.github.sekerator.kingdomworld.core.siege;

import java.util.Objects;
import java.util.UUID;

/**
 * The leaves of a gate in a city wall: hung or not, closed or open, and how much strength they have left. A gate whose
 * strength is gone is breached until the builders hang new leaves.
 */
public final class Gate {
    private final long wallId;
    private final int cell;
    private final UUID kingdomId;
    private GateMaterial material;
    private double strength;
    private boolean hung;
    private boolean closed;

    public Gate(long wallId, int cell, UUID kingdomId, GateMaterial material, double strength, boolean hung, boolean closed) {
        this.wallId = wallId;
        this.cell = cell;
        this.kingdomId = Objects.requireNonNull(kingdomId, "kingdomId");
        this.material = Objects.requireNonNull(material, "material");
        this.strength = Math.max(0, strength);
        this.hung = hung;
        this.closed = closed && hung;
    }

    public static String key(long wallId, int cell) {
        return wallId + ":" + cell;
    }

    public String key() {
        return key(wallId, cell);
    }

    public long wallId() {
        return wallId;
    }

    public int cell() {
        return cell;
    }

    public UUID kingdomId() {
        return kingdomId;
    }

    public GateMaterial material() {
        return material;
    }

    public double strength() {
        return strength;
    }

    public boolean hung() {
        return hung;
    }

    public boolean closed() {
        return closed;
    }

    /**
     * Whether the leaves are gone (never hung, or broken).
     */
    public boolean breached() {
        return !hung || strength <= 0;
    }

    /**
     * The share of strength left, 0 to 100.
     */
    public int percent(SiegeRules rules) {
        if (!hung) {
            return 0;
        }
        return (int) Math.max(0, Math.min(100, Math.round(strength * 100.0 / rules.strength(material))));
    }

    /**
     * The share of the gate broken so far, 0 to 100, for the line above the hotbar.
     */
    public int brokenPercent(SiegeRules rules) {
        return 100 - percent(rules);
    }

    /**
     * Hangs new leaves at full strength (the builders paid the materials).
     */
    public void hang(GateMaterial value, SiegeRules rules) {
        material = Objects.requireNonNull(value, "material");
        strength = rules.strength(value);
        hung = true;
    }

    /**
     * Strikes the gate.
     *
     * @return whether this blow breached it
     */
    public boolean strike(double points) {
        if (breached() || points <= 0) {
            return false;
        }
        strength = Math.max(0, strength - points);
        if (strength <= 0) {
            hung = false;
            closed = false;
            return true;
        }
        return false;
    }

    /**
     * Opens or closes hung leaves.
     *
     * @return whether anything changed
     */
    public boolean setClosed(boolean value) {
        boolean wanted = value && hung;
        if (closed == wanted) {
            return false;
        }
        closed = wanted;
        return true;
    }

    /**
     * Leaves taken down by hand or broken to the last block: the gate is breached.
     */
    public void lose() {
        hung = false;
        closed = false;
        strength = 0;
    }

    /**
     * The strength left after a share of the leaf blocks is gone, for leaves broken block by block by players.
     */
    public void wear(int blocksLeft, int blocksAll, SiegeRules rules) {
        if (!hung || blocksAll <= 0) {
            return;
        }
        double cap = rules.strength(material) * Math.max(0, blocksLeft) / (double) blocksAll;
        strength = Math.min(strength, cap);
        if (blocksLeft * 2 < blocksAll) {
            lose();
        }
    }
}
