package io.github.sekerator.kingdomworld.core.citizen;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A work post: a block on a kingdom's land where citizens of one profession work, with its settings (profession,
 * radius of the zone, seats, the animals of a herd, the goal of a mine), the workers, the last status and what is known about its
 * yield. Workers change only through {@link Citizens}, which keeps them consistent with the citizens'
 * workplaces; settings change through {@link io.github.sekerator.kingdomworld.core.state.KingdomWorldState},
 * which checks the rights. The simulation updates status, yield and progress directly.
 */
public final class WorkPost {
    /** Animal id of a herd that is not chosen yet. */
    public static final String NO_ANIMAL = "";
    /** Longest animal id; ids travel over the network and into saved data. */
    public static final int MAX_ANIMAL_LENGTH = 128;
    /** Longest name a player may give a post. */
    public static final int MAX_NAME_LENGTH = 32;

    private final WorldPos pos;
    private final UUID kingdomId;
    private String name = "";
    private String professionId = Professions.UNEMPLOYED.id();
    private int radius;
    private int seats = 1;
    private String animal = NO_ANIMAL;
    private int herdSize;
    private MineGoal mineGoal = MineGoal.ANYTHING;
    private final Set<UUID> workers = new LinkedHashSet<>();
    private WorkStatus status = WorkStatus.NO_PROFESSION;
    private PostYield yield = PostYield.NONE;
    private final Map<String, Double> progress = new LinkedHashMap<>();
    private long day = -1;
    private long producedToday;
    private boolean container;
    private boolean auto = true;
    private final Map<String, Integer> stations = new LinkedHashMap<>();
    private String current = "";
    private double currentProgress;

    private Runnable changeListener = () -> {
    };

    public WorkPost(WorldPos pos, UUID kingdomId, int radius, int herdSize) {
        this.pos = Objects.requireNonNull(pos, "pos");
        this.kingdomId = Objects.requireNonNull(kingdomId, "kingdomId");
        this.radius = Math.max(1, radius);
        this.herdSize = Math.max(2, herdSize);
    }

    void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
    }

    private void changed() {
        changeListener.run();
    }

    public WorldPos pos() {
        return pos;
    }

    public UUID kingdomId() {
        return kingdomId;
    }

    // ---------------------------------------------------------------- settings

    /**
     * The name a player gave the post, empty for the default one (its profession and its number).
     */
    public String name() {
        return name;
    }

    void setName(String value) {
        String next = value == null ? "" : value.strip();
        if (!next.equals(name)) {
            name = next;
            changed();
        }
    }

    /**
     * Restores the name from saved data. Does not notify.
     */
    public void restoreName(String value) {
        name = value == null ? "" : value.strip();
    }

    /**
     * Whether a player may give a post this name: at most {@link #MAX_NAME_LENGTH} characters without control characters or
     * the formatting sign of the game; empty gives the post its default name back.
     */
    public static boolean isValidName(String value) {
        if (value == null || value.length() > MAX_NAME_LENGTH) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c) || c == '§') {
                return false;
            }
        }
        return true;
    }

    /**
     * The profession of the post; {@code unemployed} while none is chosen.
     */
    public String professionId() {
        return professionId;
    }

    public boolean hasProfession() {
        return !Professions.UNEMPLOYED.id().equals(professionId);
    }

    void setProfessionId(String value) {
        Objects.requireNonNull(value, "value");
        if (!value.equals(professionId)) {
            professionId = value;
            yield = PostYield.NONE;
            progress.clear();
            current = "";
            currentProgress = 0.0;
            changed();
        }
    }

    public int radius() {
        return radius;
    }

    void setRadius(int value) {
        int clamped = Math.max(1, value);
        if (clamped != radius) {
            radius = clamped;
            if (!yield.measured()) {
                yield = PostYield.NONE;
            }
            changed();
        }
    }

    public int seats() {
        return seats;
    }

    void setSeats(int value) {
        int clamped = Math.max(1, value);
        if (clamped != seats) {
            seats = clamped;
            changed();
        }
    }

    /**
     * Entity type id of the animals a herder keeps, or {@link #NO_ANIMAL}.
     */
    public String animal() {
        return animal;
    }

    void setAnimal(String value) {
        String id = value == null ? NO_ANIMAL : value;
        if (!id.equals(animal)) {
            animal = id;
            yield = PostYield.NONE;
            progress.clear();
            changed();
        }
    }

    /**
     * Head of animals a herder keeps: it breeds up to this number and culls adults beyond it.
     */
    public int herdSize() {
        return herdSize;
    }

    void setHerdSize(int value) {
        int clamped = Math.max(2, value);
        if (clamped != herdSize) {
            herdSize = clamped;
            changed();
        }
    }

    /**
     * What a miner's post is asked for; {@link MineGoal#ANYTHING} until somebody sets a goal.
     */
    public MineGoal mineGoal() {
        return mineGoal;
    }

    void setMineGoal(MineGoal value) {
        Objects.requireNonNull(value, "value");
        if (!value.equals(mineGoal)) {
            if (!value.sameMine(mineGoal)) {
                // Another kind of ore or another depth is another mine: what the old one gave says nothing about it.
                yield = PostYield.NONE;
                progress.clear();
            }
            mineGoal = value;
            changed();
        }
    }

    /**
     * Restores the goal of a miner's post from saved data. Does not notify.
     */
    public void restoreMineGoal(MineGoal value) {
        this.mineGoal = Objects.requireNonNull(value, "value");
    }

    // ---------------------------------------------------------------- workers

    public Set<UUID> workers() {
        return Collections.unmodifiableSet(workers);
    }

    public int freeSeats() {
        return Math.max(0, seats - workers.size());
    }

    boolean addWorker(UUID citizen) {
        if (workers.contains(citizen) || workers.size() >= seats) {
            return false;
        }
        workers.add(citizen);
        changed();
        return true;
    }

    boolean removeWorker(UUID citizen) {
        if (workers.remove(citizen)) {
            changed();
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- state of the work

    public WorkStatus status() {
        return status;
    }

    public void setStatus(WorkStatus value) {
        Objects.requireNonNull(value, "value");
        if (value != status) {
            status = value;
            changed();
        }
    }

    public PostYield yield() {
        return yield;
    }

    public void setYield(PostYield value) {
        Objects.requireNonNull(value, "value");
        if (!value.equals(yield)) {
            yield = value;
            changed();
        }
    }

    /**
     * Fractions of items carried between steps of the abstract work, by item id; {@code ""} carries tool uses.
     */
    public Map<String, Double> progress() {
        return Collections.unmodifiableMap(progress);
    }

    public void setProgress(Map<String, Double> value) {
        Map<String, Double> next = new LinkedHashMap<>();
        value.forEach((item, amount) -> {
            if (item != null && amount != null && Double.isFinite(amount) && amount > 0.0) {
                next.put(item, amount);
            }
        });
        if (!next.equals(progress)) {
            progress.clear();
            progress.putAll(next);
            changed();
        }
    }

    /**
     * Whether a container stood next to the post the last time it was looked at; without one the workers carry
     * what they gather to a storehouse themselves.
     */
    public boolean hasContainer() {
        return container;
    }

    public void setHasContainer(boolean value) {
        if (value != container) {
            container = value;
            changed();
        }
    }

    // ---------------------------------------------------------------- production

    /**
     * Whether a workshop works on its own when no order needs it (a smelter smelts raw ore, a cook keeps food in
     * stock); otherwise it makes only what is ordered.
     */
    public boolean auto() {
        return auto;
    }

    public void setAuto(boolean value) {
        if (value != auto) {
            auto = value;
            changed();
        }
    }

    /**
     * Workstations in the zone the last time it was looked at, by kind with their number.
     */
    public Map<String, Integer> stations() {
        return Collections.unmodifiableMap(stations);
    }

    public boolean hasStation(String kind) {
        return stations.getOrDefault(kind, 0) > 0;
    }

    public void setStations(Map<String, Integer> value) {
        Map<String, Integer> next = new LinkedHashMap<>();
        value.forEach((kind, count) -> {
            if (kind != null && count != null && count > 0) {
                next.put(kind, count);
            }
        });
        if (!next.equals(stations)) {
            stations.clear();
            stations.putAll(next);
            changed();
        }
    }

    /**
     * Item id of what the workshop makes now, empty if nothing; shown on the post's screen, not saved.
     */
    public String current() {
        return current;
    }

    /**
     * How far the current item is, from 0 to 1.
     */
    public double currentProgress() {
        return currentProgress;
    }

    public void setCurrent(String item, double progress) {
        current = item == null ? "" : item;
        currentProgress = Double.isFinite(progress) ? Math.max(0.0, Math.min(1.0, progress)) : 0.0;
    }

    /**
     * Restores the settings of a workshop from saved data. Does not notify.
     */
    public void restoreProduction(boolean auto, Map<String, Integer> stations) {
        this.auto = auto;
        this.stations.clear();
        stations.forEach((kind, count) -> {
            if (kind != null && count != null && count > 0) {
                this.stations.put(kind, count);
            }
        });
    }

    /**
     * Items gathered at this post on the given game day.
     */
    public long producedOn(long gameDay) {
        return gameDay == day ? producedToday : 0L;
    }

    public void addProduced(long gameDay, long count) {
        if (count <= 0) {
            return;
        }
        if (gameDay != day) {
            day = gameDay;
            producedToday = 0;
        }
        producedToday += count;
        changed();
    }

    /**
     * Restores the settings and the state of the work from saved data; workers are restored by {@link Citizens}.
     * Does not notify.
     */
    public void restore(String professionId, int seats, String animal, WorkStatus status, PostYield yield,
                        Map<String, Double> progress, long day, long producedToday, boolean container) {
        this.professionId = Objects.requireNonNull(professionId, "professionId");
        this.seats = Math.max(1, seats);
        this.animal = animal == null ? NO_ANIMAL : animal;
        this.status = Objects.requireNonNull(status, "status");
        this.yield = Objects.requireNonNull(yield, "yield");
        this.progress.clear();
        progress.forEach((item, amount) -> {
            if (item != null && amount != null && Double.isFinite(amount) && amount > 0.0) {
                this.progress.put(item, amount);
            }
        });
        this.day = day;
        this.producedToday = Math.max(0L, producedToday);
        this.container = container;
    }

    /**
     * The day the production counter belongs to, for saved data.
     */
    public long counterDay() {
        return day;
    }

    /**
     * The production counter as saved, regardless of the day.
     */
    public long counter() {
        return producedToday;
    }

    @Override
    public String toString() {
        return "WorkPost[" + pos.coordinates() + " " + professionId + ", " + workers.size() + "/" + seats + "]";
    }
}
