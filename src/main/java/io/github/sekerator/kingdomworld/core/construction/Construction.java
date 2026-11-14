package io.github.sekerator.kingdomworld.core.construction;

import io.github.sekerator.kingdomworld.core.citizen.WorldPos;

import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A building going up on the land of a kingdom: which template, where and how it is laid, how urgent it is, which steps of
 * its {@link BuildPlan} are done, which are paid for and wait to be placed in the world (work far from players), and the
 * materials brought to the site and not used yet. The plan itself is made again from the template whenever it is needed;
 * the fingerprint of the template tells whether the steps still mean the same.
 *
 * <p>Settings change through {@link io.github.sekerator.kingdomworld.core.state.KingdomWorldState}, which checks rights;
 * the work in the world updates progress, status and the site directly. Not thread safe.
 */
public final class Construction {
    /** Most kinds of materials kept at one site. */
    public static final int MAX_DELIVERED_KINDS = 512;

    /**
     * What the builders do: put up a new building, change a building to the next level of its template, put back what
     * is missing or changed in it, or take it down.
     */
    public enum Kind {
        BUILD,
        UPGRADE,
        REPAIR,
        DEMOLISH,
        /** Earthworks: the land of a rectangle levelled, cut down or filled up ({@link Earthwork}). */
        EARTHWORK;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Kind byId(String id) {
            for (Kind kind : values()) {
                if (kind.id().equals(id)) {
                    return kind;
                }
            }
            return BUILD;
        }
    }

    public enum Priority {
        LOW,
        NORMAL,
        HIGH;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Priority next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static Priority byId(String id) {
            for (Priority priority : values()) {
                if (priority.id().equals(id)) {
                    return priority;
                }
            }
            return NORMAL;
        }
    }

    /**
     * What the construction is doing, or why not.
     */
    public enum Status {
        /** Builders are at work. */
        WORKING(false),
        /** Waits for a builder: no builder post of the kingdom has it within reach, or nobody works there. */
        NO_BUILDER(false),
        /** The builders have nothing to place: the materials are missing. */
        NO_MATERIALS(true),
        /** A container with items stands in the way; it is not broken. */
        BLOCKED(true),
        /** A place the builders could not reach; they went on with the rest. */
        UNREACHABLE(true),
        /** Blocks are paid for and wait to be placed when the place is loaded again. */
        WAITING_FOR_LOAD(false),
        /** Stopped by the king. */
        PAUSED(false),
        /** The template is gone (its data pack was removed). */
        TEMPLATE_MISSING(true),
        /** The template changed since the construction was laid. */
        TEMPLATE_CHANGED(true),
        /** Part of the site is no longer land of the kingdom. */
        OUTSIDE_LAND(true),
        /** The building an upgrade, repair or demolition works on is no longer in the register. */
        BUILDING_GONE(true),
        /** Every block is in place. */
        DONE(false);

        private final boolean problem;

        Status(boolean problem) {
            this.problem = problem;
        }

        public boolean isProblem() {
            return problem;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Status byId(String id) {
            for (Status status : values()) {
                if (status.id().equals(id)) {
                    return status;
                }
            }
            return NO_BUILDER;
        }
    }

    private final long id;
    private final UUID kingdomId;
    private final String templateId;
    private final long fingerprint;
    private final WorldPos anchor;
    private final Placement placement;
    private final Box box;
    private final long created;
    private final List<int[]> clear;
    private final int steps;
    private final Kind kind;
    private final long building;
    private final Materials materials;
    private final BitSet skip;
    private final Map<String, Integer> removed = new LinkedHashMap<>();
    private Priority priority = Priority.NORMAL;
    private boolean paused;
    private boolean orderMissing;
    private final BitSet done = new BitSet();
    private final Set<Integer> pending = new LinkedHashSet<>();
    private final Map<String, Integer> delivered = new LinkedHashMap<>();
    private final Map<String, Long> orders = new LinkedHashMap<>();
    private Status status = Status.NO_BUILDER;
    private WorldPos statusPos;
    private double carry;

    private Runnable changeListener = () -> {
    };

    /**
     * @param fingerprint what the template looked like when the construction was laid
     * @param clear       positions in the world that held blocks in the way when it was laid
     * @param steps       steps of the plan, to check that the plan made later is the same
     */
    public Construction(long id, UUID kingdomId, String templateId, long fingerprint, WorldPos anchor, Placement placement, Box box,
                        long created, List<int[]> clear, int steps) {
        this(id, kingdomId, templateId, fingerprint, anchor, placement, box, created, clear, steps, Kind.BUILD, 0, Materials.NONE,
                new BitSet());
    }

    /**
     * @param kind      what the builders do
     * @param building  the building of the register an upgrade, repair or demolition works on, 0 for a new building
     * @param materials wood and stone instead of the template's own
     * @param skip      indices of template blocks the builders leave as they are: those an upgrade does not change, those a
     *                  repair finds in place
     */
    public Construction(long id, UUID kingdomId, String templateId, long fingerprint, WorldPos anchor, Placement placement, Box box,
                        long created, List<int[]> clear, int steps, Kind kind, long building, Materials materials, BitSet skip) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.building = building;
        this.materials = Objects.requireNonNull(materials, "materials");
        this.skip = (BitSet) skip.clone();
        this.id = id;
        this.kingdomId = Objects.requireNonNull(kingdomId, "kingdomId");
        this.templateId = Objects.requireNonNull(templateId, "templateId");
        this.fingerprint = fingerprint;
        this.anchor = Objects.requireNonNull(anchor, "anchor");
        this.placement = Objects.requireNonNull(placement, "placement");
        this.box = Objects.requireNonNull(box, "box");
        this.created = created;
        this.clear = clear.stream().map(int[]::clone).toList();
        this.steps = Math.max(0, steps);
    }

    void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
    }

    private void changed() {
        changeListener.run();
    }

    public long id() {
        return id;
    }

    public UUID kingdomId() {
        return kingdomId;
    }

    public String templateId() {
        return templateId;
    }

    public long fingerprint() {
        return fingerprint;
    }

    public WorldPos anchor() {
        return anchor;
    }

    public String dimension() {
        return anchor.dimension();
    }

    public Placement placement() {
        return placement;
    }

    public Box box() {
        return box;
    }

    public long created() {
        return created;
    }

    /**
     * Positions that held blocks in the way when the construction was laid, as {x, y, z}; copies.
     */
    public List<int[]> clear() {
        return clear.stream().map(int[]::clone).toList();
    }

    public int steps() {
        return steps;
    }

    public Kind kind() {
        return kind;
    }

    /**
     * The building of the register the construction works on, 0 for a new building.
     */
    /** The land and height of earthworks, {@code null} for every other kind. */
    @org.jetbrains.annotations.Nullable
    private Earthwork earthwork;

    @org.jetbrains.annotations.Nullable
    public Earthwork earthwork() {
        return earthwork;
    }

    public void setEarthwork(@org.jetbrains.annotations.Nullable Earthwork value) {
        earthwork = value;
    }

    public long building() {
        return building;
    }

    public Materials materials() {
        return materials;
    }

    /**
     * Indices of the template blocks the builders leave as they are; a copy.
     */
    public BitSet skip() {
        return (BitSet) skip.clone();
    }

    public boolean skips(int block) {
        return block >= 0 && skip.get(block);
    }

    // ---------------------------------------------------------------- what a demolition took down

    /**
     * Items of the blocks a demolition took down so far, before the loss: the share that goes back to the storehouses is
     * worked out from these (see {@link Salvage}).
     */
    public Map<String, Integer> removed() {
        return Collections.unmodifiableMap(removed);
    }

    /**
     * A demolition took down blocks worth these items.
     *
     * @return the items that go back to the storehouses for them, after the loss
     */
    public Map<String, Integer> salvage(Map<String, Integer> items, int lossPercent) {
        Map<String, Integer> back = new LinkedHashMap<>();
        items.forEach((item, count) -> {
            if (count == null || count <= 0 || item == null || item.isEmpty()) {
                return;
            }
            int before = removed.getOrDefault(item, 0);
            if (!removed.containsKey(item) && removed.size() >= MAX_DELIVERED_KINDS) {
                return;
            }
            int after = (int) Math.min(Integer.MAX_VALUE, (long) before + count);
            removed.put(item, after);
            int returned = Salvage.returned(before, after, lossPercent);
            if (returned > 0) {
                back.put(item, returned);
            }
        });
        if (!back.isEmpty() || !items.isEmpty()) {
            changed();
        }
        return back;
    }

    // ---------------------------------------------------------------- settings

    public Priority priority() {
        return priority;
    }

    public void setPriority(Priority value) {
        Objects.requireNonNull(value, "value");
        if (value != priority) {
            priority = value;
            changed();
        }
    }

    public boolean paused() {
        return paused;
    }

    public void setPaused(boolean value) {
        if (value != paused) {
            paused = value;
            changed();
        }
    }

    /**
     * Whether missing materials that the workshops can make are ordered from them.
     */
    public boolean orderMissing() {
        return orderMissing;
    }

    public void setOrderMissing(boolean value) {
        if (value != orderMissing) {
            orderMissing = value;
            changed();
        }
    }

    /**
     * Orders the construction gave for missing materials, by item id with the order number.
     */
    public Map<String, Long> orders() {
        return Collections.unmodifiableMap(orders);
    }

    public void putOrder(String item, long order) {
        Long old = orders.put(item, order);
        if (old == null || old != order) {
            changed();
        }
    }

    public void removeOrder(String item) {
        if (orders.remove(item) != null) {
            changed();
        }
    }

    // ---------------------------------------------------------------- progress

    public boolean isDone(int step) {
        return step >= 0 && done.get(step);
    }

    public boolean isPending(int step) {
        return pending.contains(step);
    }

    /**
     * Steps paid for that wait to be done in the world, in the order they were paid for.
     */
    public List<Integer> pending() {
        return List.copyOf(pending);
    }

    /**
     * A step is done in the world.
     */
    public void markDone(int step) {
        if (step < 0 || step >= steps) {
            return;
        }
        boolean wasPending = pending.remove(step);
        if (!done.get(step) || wasPending) {
            done.set(step);
            changed();
        }
    }

    /**
     * A step is paid for far from players and waits until its place is loaded.
     */
    public void markPending(int step) {
        if (step >= 0 && step < steps && !done.get(step) && pending.add(step)) {
            changed();
        }
    }

    /**
     * Steps done, as a bit set; a copy.
     */
    public BitSet doneSet() {
        return (BitSet) done.clone();
    }

    public int doneCount() {
        return done.cardinality();
    }

    /**
     * Work far from players carried over to the next simulation step, in blocks.
     */
    public double carry() {
        return carry;
    }

    public void setCarry(double value) {
        double next = Double.isFinite(value) ? Math.max(0.0, Math.min(1_000.0, value)) : 0.0;
        if (next != carry) {
            carry = next;
            changed();
        }
    }

    // ---------------------------------------------------------------- the site

    /**
     * Materials brought to the site and not used yet, by item id.
     */
    public Map<String, Integer> delivered() {
        return Collections.unmodifiableMap(delivered);
    }

    /**
     * Materials arrive at the site.
     *
     * @return how many the site took (none when it already holds too many kinds)
     */
    public int deliver(String item, int count) {
        if (count <= 0 || item == null || item.isEmpty()) {
            return 0;
        }
        if (!delivered.containsKey(item) && delivered.size() >= MAX_DELIVERED_KINDS) {
            return 0;
        }
        delivered.merge(item, count, Integer::sum);
        changed();
        return count;
    }

    /**
     * Takes materials from the site.
     *
     * @return how many were there to take, at most {@code count}
     */
    public int takeDelivered(String item, int count) {
        int there = delivered.getOrDefault(item, 0);
        int taken = Math.max(0, Math.min(there, count));
        if (taken > 0) {
            if (taken == there) {
                delivered.remove(item);
            } else {
                delivered.put(item, there - taken);
            }
            changed();
        }
        return taken;
    }

    /**
     * Whether the site holds all the items.
     */
    public boolean hasDelivered(Map<String, Integer> items) {
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            if (delivered.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Empties the site, for a cancelled construction.
     *
     * @return what lay there
     */
    public Map<String, Integer> takeAllDelivered() {
        Map<String, Integer> all = new LinkedHashMap<>(delivered);
        if (!all.isEmpty()) {
            delivered.clear();
            changed();
        }
        return all;
    }

    // ---------------------------------------------------------------- status

    public Status status() {
        return status;
    }

    /**
     * Where the problem of the status is (a container in the way, a place out of reach), or {@code null}.
     */
    public WorldPos statusPos() {
        return statusPos;
    }

    public void setStatus(Status value, WorldPos pos) {
        Objects.requireNonNull(value, "value");
        if (value != status || !Objects.equals(pos, statusPos)) {
            status = value;
            statusPos = pos;
            changed();
        }
    }

    public void setStatus(Status value) {
        setStatus(value, null);
    }

    /**
     * Restores the state of the construction from saved data. Does not notify.
     */
    public void restore(Priority priority, boolean paused, boolean orderMissing, BitSet done, List<Integer> pending,
                        Map<String, Integer> delivered, Map<String, Long> orders, Status status, WorldPos statusPos, double carry,
                        Map<String, Integer> removed) {
        restore(priority, paused, orderMissing, done, pending, delivered, orders, status, statusPos, carry);
        this.removed.clear();
        removed.forEach((item, count) -> {
            if (item != null && !item.isEmpty() && count != null && count > 0 && this.removed.size() < MAX_DELIVERED_KINDS) {
                this.removed.put(item, count);
            }
        });
    }

    public void restore(Priority priority, boolean paused, boolean orderMissing, BitSet done, List<Integer> pending,
                        Map<String, Integer> delivered, Map<String, Long> orders, Status status, WorldPos statusPos, double carry) {
        this.priority = Objects.requireNonNull(priority, "priority");
        this.paused = paused;
        this.orderMissing = orderMissing;
        this.done.clear();
        this.done.or(done);
        if (this.done.length() > steps) {
            this.done.clear(steps, this.done.length());
        }
        this.pending.clear();
        for (int step : pending) {
            if (step >= 0 && step < steps && !this.done.get(step)) {
                this.pending.add(step);
            }
        }
        this.delivered.clear();
        delivered.forEach((item, count) -> {
            if (item != null && !item.isEmpty() && count != null && count > 0 && this.delivered.size() < MAX_DELIVERED_KINDS) {
                this.delivered.put(item, count);
            }
        });
        this.orders.clear();
        this.orders.putAll(orders);
        this.status = Objects.requireNonNull(status, "status");
        this.statusPos = statusPos;
        this.carry = Double.isFinite(carry) ? Math.max(0.0, carry) : 0.0;
    }

    @Override
    public String toString() {
        return "Construction[" + id + " " + kind.id() + " " + templateId + " at " + anchor.coordinates() + ", " + done.cardinality() + "/" + steps + "]";
    }
}
