package io.github.sekerator.kingdomworld.core.citizen;

import java.util.Objects;
import java.util.UUID;

/**
 * A citizen of a kingdom: the authoritative record. A citizen near players also has a body in the world, which
 * is only a view of this record; when the body goes away the record lives on and is simulated by formulas.
 *
 * <p>Identity, name and looks never change. Home and workplace change only through
 * {@link io.github.sekerator.kingdomworld.core.state.KingdomWorldState}, which keeps them consistent with the
 * places of the kingdom. The other fields are changed by the simulation directly; every change notifies the
 * registry so that the data is saved.
 */
public final class Citizen {
    public static final float MAX_HEALTH = 20.0f;

    private final UUID id;
    private final UUID kingdomId;
    private final int nameSeed;
    private final Gender gender;
    private final int skin;
    private final long birthTick;

    private float health = MAX_HEALTH;
    private CitizenState state = CitizenState.HEALTHY;
    private String professionId = Professions.UNEMPLOYED.id();
    private WorldPos home;
    private WorldPos workplace;
    private WorldPos lastPos;
    private float food = CitizenNeeds.MAX_FOOD;
    private int mood = CitizenNeeds.NEUTRAL_MOOD;
    private int experience;
    private int workCapacity;
    private String workProduct = "";
    private double workProgress;
    private WorkStatus workStatus = WorkStatus.NONE;

    private Runnable changeListener = () -> {
    };

    /**
     * @param skin      index of the look, interpreted by the client
     * @param birthTick game time at which the citizen was born; may be negative for citizens who arrive as adults
     * @param lastPos   where the citizen is
     */
    public Citizen(UUID id, UUID kingdomId, int nameSeed, Gender gender, int skin, long birthTick, WorldPos lastPos) {
        this.id = Objects.requireNonNull(id, "id");
        this.kingdomId = Objects.requireNonNull(kingdomId, "kingdomId");
        this.nameSeed = nameSeed;
        this.gender = Objects.requireNonNull(gender, "gender");
        this.skin = Math.max(0, skin);
        this.birthTick = birthTick;
        this.lastPos = Objects.requireNonNull(lastPos, "lastPos");
    }

    void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
    }

    private void changed() {
        changeListener.run();
    }

    // ---------------------------------------------------------------- identity

    public UUID id() {
        return id;
    }

    /**
     * The first eight characters of the id, used by operator commands.
     */
    public String shortId() {
        return id.toString().substring(0, 8);
    }

    public UUID kingdomId() {
        return kingdomId;
    }

    public int nameSeed() {
        return nameSeed;
    }

    public Gender gender() {
        return gender;
    }

    public int skin() {
        return skin;
    }

    public long birthTick() {
        return birthTick;
    }

    /**
     * Age in game days at the given game time.
     */
    public long ageDays(long gameTime) {
        return Math.max(0L, Math.floorDiv(gameTime - birthTick, CitizenNeeds.TICKS_PER_DAY));
    }

    public String name(CitizenNames.Alphabet alphabet) {
        return CitizenNames.render(nameSeed, gender, alphabet);
    }

    // ---------------------------------------------------------------- health

    public float health() {
        return health;
    }

    public CitizenState state() {
        return state;
    }

    public boolean isAlive() {
        return state != CitizenState.DEAD;
    }

    /**
     * Sets the health and derives the state from it. Has no effect on a dead citizen.
     *
     * @param woundedFraction fraction of the maximum health below which a citizen counts as wounded
     */
    public void setHealth(float value, float woundedFraction) {
        if (state == CitizenState.DEAD) {
            return;
        }
        float clamped = Math.max(0.0f, Math.min(MAX_HEALTH, value));
        CitizenState next = clamped < MAX_HEALTH * woundedFraction ? CitizenState.WOUNDED : CitizenState.HEALTHY;
        if (clamped != health || next != state) {
            health = clamped;
            state = next;
            changed();
        }
    }

    void markDead() {
        if (state != CitizenState.DEAD) {
            state = CitizenState.DEAD;
            health = 0.0f;
            changed();
        }
    }

    // ---------------------------------------------------------------- work and places

    public String professionId() {
        return professionId;
    }

    void setProfessionId(String value) {
        Objects.requireNonNull(value, "value");
        if (!value.equals(professionId)) {
            professionId = value;
            workCapacity = 0;
            workProduct = "";
            workProgress = 0.0;
            changed();
        }
    }

    public WorldPos home() {
        return home;
    }

    void setHome(WorldPos value) {
        if (!Objects.equals(home, value)) {
            home = value;
            changed();
        }
    }

    public WorldPos workplace() {
        return workplace;
    }

    void setWorkplace(WorldPos value) {
        if (!Objects.equals(workplace, value)) {
            workplace = value;
            changed();
        }
    }

    public WorldPos lastPos() {
        return lastPos;
    }

    public void setLastPos(WorldPos value) {
        Objects.requireNonNull(value, "value");
        if (!value.equals(lastPos)) {
            lastPos = value;
            changed();
        }
    }

    // ---------------------------------------------------------------- needs

    /**
     * Satiety from 0 (starving) to {@link CitizenNeeds#MAX_FOOD}.
     */
    public float food() {
        return food;
    }

    public void setFood(float value) {
        float clamped = Math.max(0.0f, Math.min(CitizenNeeds.MAX_FOOD, value));
        if (clamped != food) {
            food = clamped;
            changed();
        }
    }

    /**
     * Mood from 0 to {@link CitizenNeeds#MAX_MOOD}.
     */
    public int mood() {
        return mood;
    }

    public void setMood(int value) {
        int clamped = Math.max(0, Math.min(CitizenNeeds.MAX_MOOD, value));
        if (clamped != mood) {
            mood = clamped;
            changed();
        }
    }

    public int experience() {
        return experience;
    }

    public void addExperience(int amount) {
        if (amount > 0) {
            experience = (int) Math.min(Integer.MAX_VALUE, (long) experience + amount);
            changed();
        }
    }

    // ---------------------------------------------------------------- remembered work for the abstract simulation

    /**
     * Size of the work last seen in the world, for example the number of farm plots of a farmer.
     */
    public int workCapacity() {
        return workCapacity;
    }

    /**
     * Item id of what the work produces, for example {@code minecraft:wheat}; empty if unknown.
     */
    public String workProduct() {
        return workProduct;
    }

    /**
     * Fraction of the next unit of output, carried between simulation steps.
     */
    public double workProgress() {
        return workProgress;
    }

    public void rememberWork(int capacity, String product) {
        int clamped = Math.max(0, capacity);
        String value = product == null ? "" : product;
        if (clamped != workCapacity || !value.equals(workProduct)) {
            workCapacity = clamped;
            workProduct = value;
            changed();
        }
    }

    /**
     * What the citizen's work looks like right now, and if it stands still, why.
     */
    public WorkStatus workStatus() {
        return workStatus;
    }

    public void setWorkStatus(WorkStatus value) {
        Objects.requireNonNull(value, "value");
        if (value != workStatus) {
            workStatus = value;
            changed();
        }
    }

    public void setWorkProgress(double value) {
        double clamped = Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
        if (clamped != workProgress) {
            workProgress = clamped;
            changed();
        }
    }

    /**
     * Restores the fields that are not part of the constructor, for saved data. Does not notify.
     */
    public void restore(float health, CitizenState state, String professionId, WorldPos home, WorldPos workplace,
                        float food, int mood, int experience, int workCapacity, String workProduct, double workProgress,
                        WorkStatus workStatus) {
        if (state == CitizenState.DEAD) {
            throw new IllegalArgumentException("A dead citizen cannot be restored into the population");
        }
        this.health = Math.max(0.0f, Math.min(MAX_HEALTH, health));
        this.state = Objects.requireNonNull(state, "state");
        this.professionId = Objects.requireNonNull(professionId, "professionId");
        this.home = home;
        this.workplace = workplace;
        this.food = Math.max(0.0f, Math.min(CitizenNeeds.MAX_FOOD, food));
        this.mood = Math.max(0, Math.min(CitizenNeeds.MAX_MOOD, mood));
        this.experience = Math.max(0, experience);
        this.workCapacity = Math.max(0, workCapacity);
        this.workProduct = workProduct == null ? "" : workProduct;
        this.workProgress = Double.isFinite(workProgress) ? Math.max(0.0, workProgress) : 0.0;
        this.workStatus = workStatus == null ? WorkStatus.NONE : workStatus;
    }

    @Override
    public String toString() {
        return "Citizen[" + shortId() + " " + name(CitizenNames.Alphabet.LATIN) + ", " + professionId + ", " + state + "]";
    }
}
