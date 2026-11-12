package io.github.sekerator.kingdomworld.core.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A place of the plan where a kingdom of the world appears, with the towns around it and how far its appearance went. The
 * plan only writes records; the kingdom is founded as data soon after and its buildings are raised in the world later.
 */
public final class NpcSite {
    /**
     * How far a place is.
     */
    public enum Status {
        /** Planned; the kingdom is founded as data soon after (or appears at once when a player comes near first). */
        PLANNED,
        /**
         * The kingdom stands as data: its throne place, land, ruler, laws and treasury exist and the maps show it, while its
         * buildings wait to be raised in the world.
         */
        FOUNDED,
        /** The kingdom is appearing in the world, in steps over ticks. */
        SPAWNING,
        /** The kingdom stands in the world. */
        SPAWNED,
        /** Left alone: the land became someone else's or players built there before the kingdom appeared. */
        BLOCKED,
        /** Removed by an operator. */
        REMOVED;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Status> byId(String id) {
            for (Status status : values()) {
                if (status.id().equals(id)) {
                    return Optional.of(status);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * A place for a town, in blocks.
     */
    public record Town(int x, int z) {
    }

    /** The first layout: a grid of plots around the throne on its square. */
    public static final int LAYOUT_GRID = 1;
    /** The layout of districts: a palace around the throne, avenues to the gates, streets with buildings of many kinds. */
    public static final int LAYOUT_DISTRICTS = 2;
    /**
     * The styled layout: a capital of districts in one of the architectural styles ({@link TemplateCatalog#STYLES}), with its
     * castle, institutions and quarters.
     */
    public static final int LAYOUT_STYLED = 3;

    private final int id;
    private final int x;
    private final int z;
    private final NpcLevel level;
    private final long seed;
    private final int layout;
    private final String style;
    private final String wood;
    private final String guildRank;
    private final List<Town> towns = new ArrayList<>();
    private Status status = Status.PLANNED;
    private UUID kingdomId;
    private SpawnStage stage = SpawnStage.LOAD;
    private int step;
    private int groundY;
    private String reason = "";
    private int given;
    private Runnable changeListener = () -> { };

    /**
     * A place of the first layout, as worlds made before the districts have them.
     */
    public NpcSite(int id, int x, int z, NpcLevel level, long seed, List<Town> towns) {
        this(id, x, z, level, seed, towns, LAYOUT_GRID, "", "");
    }

    /**
     * @param layout how the capital is laid out: {@link #LAYOUT_GRID} or {@link #LAYOUT_DISTRICTS}
     * @param style  the id of the look of the kingdom ({@link NpcStyle}), or empty for the look of the templates
     * @param wood   the wood species that grows at the place, or empty if none is known
     */
    public NpcSite(int id, int x, int z, NpcLevel level, long seed, List<Town> towns, int layout, String style, String wood) {
        this(id, x, z, level, seed, towns, layout, style, wood, "");
    }

    /**
     * @param layout    how the capital is laid out
     * @param style     the id of the look of the kingdom, or empty
     * @param wood      the wood species that grows at the place, or empty if none is known
     * @param guildRank the rank of the guild house and the mage tower of a styled capital, or empty
     */
    public NpcSite(int id, int x, int z, NpcLevel level, long seed, List<Town> towns, int layout, String style, String wood,
                   String guildRank) {
        if (id < 1) {
            throw new IllegalArgumentException("Site id must be positive");
        }
        if (layout != LAYOUT_GRID && layout != LAYOUT_DISTRICTS) {
            throw new IllegalArgumentException("Unknown layout " + layout);
        }
        this.id = id;
        this.x = x;
        this.z = z;
        this.level = Objects.requireNonNull(level, "level");
        this.seed = seed;
        this.layout = layout;
        this.style = style == null ? "" : style;
        this.wood = wood == null ? "" : wood;
        this.guildRank = guildRank == null ? "" : guildRank;
        this.towns.addAll(towns);
    }

    void setChangeListener(Runnable listener) {
        changeListener = Objects.requireNonNull(listener, "listener");
    }

    public int id() {
        return id;
    }

    public int x() {
        return x;
    }

    public int z() {
        return z;
    }

    public NpcLevel level() {
        return level;
    }

    public long seed() {
        return seed;
    }

    /**
     * How the capital is laid out; a place keeps the layout it was planned with, so that a kingdom half raised goes on as it began.
     */
    public int layout() {
        return layout;
    }

    public boolean districts() {
        return layout == LAYOUT_DISTRICTS;
    }

    /**
     * Whether the capital is laid out in an architectural style ({@link #LAYOUT_STYLED}).
     */
    public boolean styled() {
        return layout == LAYOUT_STYLED;
    }

    /**
     * The rank of the guild house and the mage tower of a styled capital ({@code b}, {@code a}, {@code s}, {@code ss}), or empty.
     */
    public String guildRank() {
        return guildRank;
    }

    /**
     * The id of the look of the kingdom, or empty.
     */
    public String style() {
        return style;
    }

    /**
     * The look of the kingdom, if it has one of its own.
     */
    public Optional<NpcStyle> look() {
        return NpcStyle.byId(style);
    }

    /**
     * The wood species that grows at the place (for example {@code minecraft:birch}), or empty.
     */
    public String wood() {
        return wood;
    }

    public List<Town> towns() {
        return List.copyOf(towns);
    }

    /**
     * Keeps only some of the towns: the others are left out, their land never claimed.
     */
    public void retainTowns(List<Town> kept) {
        towns.retainAll(kept);
        changeListener.run();
    }

    public Status status() {
        return status;
    }

    public Optional<UUID> kingdomId() {
        return Optional.ofNullable(kingdomId);
    }

    public SpawnStage stage() {
        return stage;
    }

    /**
     * The step within the stage: which building, town or chunk comes next.
     */
    public int step() {
        return step;
    }

    /**
     * The height of the square of the throne once the place was looked at, or 0 before.
     */
    public int groundY() {
        return groundY;
    }

    /**
     * Why the place was left alone, for operators; empty otherwise.
     */
    public String reason() {
        return reason;
    }

    /** The founding money went to the treasury. */
    public static final int MONEY = 1;
    /** The laws of the ruler were set. */
    public static final int LAWS = 2;
    /** The stores and the market were filled. */
    public static final int STORES = 4;

    /**
     * Whether something of the founding was given already ({@link #MONEY}, {@link #LAWS}, {@link #STORES}), so that raising the
     * buildings later gives it no second time.
     */
    public boolean given(int what) {
        return (given & what) == what;
    }

    public void markGiven(int what) {
        given |= what;
        changeListener.run();
    }

    /**
     * What of the founding was given, as flags, for saving.
     */
    public int givenFlags() {
        return given;
    }

    /**
     * Whether the kingdom of the place stands as data (founded, being raised or standing in the world).
     */
    public boolean founded() {
        return kingdomId != null && (status == Status.FOUNDED || status == Status.SPAWNING || status == Status.SPAWNED);
    }

    public void setStatus(Status value) {
        status = Objects.requireNonNull(value, "status");
        changeListener.run();
    }

    public void block(String why) {
        status = Status.BLOCKED;
        reason = why == null ? "" : why;
        changeListener.run();
    }

    public void setKingdomId(UUID value) {
        kingdomId = value;
        changeListener.run();
    }

    /**
     * Moves on to the next stage, at its first step.
     */
    public void advance(SpawnStage next) {
        stage = Objects.requireNonNull(next, "stage");
        step = 0;
        changeListener.run();
    }

    public void setStep(int value) {
        step = Math.max(0, value);
        changeListener.run();
    }

    public void setGroundY(int value) {
        groundY = value;
        changeListener.run();
    }

    /**
     * Starts the appearance again from the beginning, for a place that was removed and is spawned again.
     */
    public void restart() {
        status = Status.PLANNED;
        stage = SpawnStage.LOAD;
        step = 0;
        kingdomId = null;
        reason = "";
        given = 0;
        changeListener.run();
    }

    /**
     * Restores the progress of a place from saved data.
     */
    public void restore(Status statusValue, UUID kingdom, SpawnStage stageValue, int stepValue, int ground, String why, int givenFlags) {
        given = givenFlags;
        status = Objects.requireNonNull(statusValue, "status");
        kingdomId = kingdom;
        stage = Objects.requireNonNull(stageValue, "stage");
        step = Math.max(0, stepValue);
        groundY = ground;
        reason = why == null ? "" : why;
    }

    public static List<Town> towns(List<int[]> points) {
        List<Town> list = new ArrayList<>();
        for (int[] point : points) {
            list.add(new Town(point[0], point[1]));
        }
        return list;
    }

    @Override
    public String toString() {
        return "site " + id + " " + level.id() + " at " + x + ", " + z + " (" + status.id() + ")";
    }
}
