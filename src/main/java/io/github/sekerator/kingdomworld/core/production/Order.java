package io.github.sekerator.kingdomworld.core.production;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * An order of the king: keep a number of an item in the storehouses, or make a number of it once. Orders the
 * planner adds for missing ingredients ("sub-orders") have a parent and keep in stock what the parent still needs;
 * they go away with it.
 *
 * <p>The planner keeps the status, the chosen recipe and what is missing up to date; players change kind,
 * amount, priority and pause through {@link OrderBook}.
 */
public final class Order {
    /** Most items one order may ask for. */
    public static final int MAX_AMOUNT = 100_000;

    public enum Kind {
        /** Keep at least the amount in the storehouses; made again whenever stock falls below. */
        KEEP,
        /** Make the amount once; the order ends when it is done. */
        MAKE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Kind> byId(String id) {
            for (Kind kind : values()) {
                if (kind.id().equals(id)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
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

        public static Optional<Priority> byId(String id) {
            for (Priority priority : values()) {
                if (priority.id().equals(id)) {
                    return Optional.of(priority);
                }
            }
            return Optional.empty();
        }
    }

    public enum Status {
        /** Not looked at by the planner yet. */
        NEW(false),
        /** Everything for the next item is there; waits for a worker. */
        READY(false),
        /** A worker is making it. */
        WORKING(false),
        /** Ingredients are being made by sub-orders. */
        WAITING_FOR_PARTS(false),
        /** Ingredients are missing that nobody can make; they must be gathered or brought. */
        MISSING(true),
        /** A recipe exists, but the kingdom has no post of the profession with the workstation it needs. */
        NO_WORKSHOP(true),
        /** Nothing can make the item. */
        NO_RECIPE(true),
        /** A keep order with enough in stock. */
        SATISFIED(false),
        PAUSED(false),
        /** A make order that is finished; it leaves the book. */
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
            return NEW;
        }
    }

    /**
     * An ingredient nobody makes that is missing, and the gathering profession that brings it ({@code ""} if none
     * does).
     */
    public record Missing(String item, int count, String gatherer) {
        public Missing {
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(gatherer, "gatherer");
        }
    }

    private final long id;
    private final String item;
    private final long parent;
    private final long created;
    private Kind kind;
    private int amount;
    private int made;
    private Priority priority = Priority.NORMAL;
    private boolean paused;
    private Status status = Status.NEW;
    private String recipe = "";
    private List<Missing> missing = List.of();
    private String workshop = "";
    private String workshopStation = "";

    /**
     * @param parent  id of the order this one supplies, 0 for an order of the king
     * @param created game time of the order
     */
    public Order(long id, String item, Kind kind, int amount, long parent, long created) {
        if (id <= 0) {
            throw new IllegalArgumentException("Order ids start at 1");
        }
        this.id = id;
        this.item = Objects.requireNonNull(item, "item");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.amount = clampAmount(amount);
        this.parent = Math.max(0, parent);
        this.created = created;
    }

    static int clampAmount(int amount) {
        return Math.max(1, Math.min(MAX_AMOUNT, amount));
    }

    public long id() {
        return id;
    }

    public String item() {
        return item;
    }

    public Kind kind() {
        return kind;
    }

    void setKind(Kind kind) {
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public int amount() {
        return amount;
    }

    void setAmount(int amount) {
        this.amount = clampAmount(amount);
    }

    /**
     * Items of a make order made so far.
     */
    public int made() {
        return made;
    }

    void addMade(int count) {
        made = (int) Math.min(MAX_AMOUNT, (long) made + Math.max(0, count));
    }

    public Priority priority() {
        return priority;
    }

    void setPriority(Priority priority) {
        this.priority = Objects.requireNonNull(priority, "priority");
    }

    public boolean paused() {
        return paused;
    }

    void setPaused(boolean paused) {
        this.paused = paused;
    }

    /**
     * Id of the order this sub-order supplies, 0 for an order of the king.
     */
    public long parent() {
        return parent;
    }

    public boolean isSubOrder() {
        return parent != 0;
    }

    public long created() {
        return created;
    }

    public Status status() {
        return status;
    }

    /**
     * The recipe the planner chose, empty if none.
     */
    public String recipe() {
        return recipe;
    }

    /**
     * What is missing for the order and its sub-orders.
     */
    public List<Missing> missing() {
        return missing;
    }

    /**
     * For {@link Status#NO_WORKSHOP}: the profession whose post is needed.
     */
    public String workshop() {
        return workshop;
    }

    /**
     * For {@link Status#NO_WORKSHOP}: the workstation the post needs.
     */
    public String workshopStation() {
        return workshopStation;
    }

    void plan(Status status, String recipe, List<Missing> missing, String workshop, String workshopStation) {
        this.status = Objects.requireNonNull(status, "status");
        this.recipe = recipe == null ? "" : recipe;
        this.missing = List.copyOf(missing);
        this.workshop = workshop == null ? "" : workshop;
        this.workshopStation = workshopStation == null ? "" : workshopStation;
    }

    /**
     * Restores the state from saved data.
     */
    public void restore(int made, Priority priority, boolean paused) {
        this.made = Math.max(0, Math.min(MAX_AMOUNT, made));
        this.priority = Objects.requireNonNull(priority, "priority");
        this.paused = paused;
    }

    @Override
    public String toString() {
        return "Order#" + id + "[" + kind.id() + " " + amount + " " + item + (parent != 0 ? " for #" + parent : "") + ", " + status.id() + "]";
    }
}
