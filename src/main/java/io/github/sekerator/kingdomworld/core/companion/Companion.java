package io.github.sekerator.kingdomworld.core.companion;

import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.economy.Money;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A companion of a player: a slave bought on the market or a free companion hired for a daily wage. The record is the truth
 * (like the record of a citizen): the body in the world shows it while the owner is online and near, and the server copies
 * the body's health and place back into it. What the companion carries is kept by the server next to the record.
 *
 * <p>Changed only through {@link CompanionState}, which tells the saved data.
 */
public final class Companion {
    /** Highest level. */
    public static final int MAX_LEVEL = 30;
    /** Health of a companion of level 0. */
    public static final float BASE_HEALTH = 20.0f;
    /** Health a level adds. */
    public static final float HEALTH_PER_LEVEL = 2.0f;
    /** Most health. */
    public static final float MAX_HEALTH = 60.0f;
    /** Below this share of its health a companion counts as wounded. */
    public static final double WOUNDED_SHARE = 0.4;
    /** Notes of loyalty a companion keeps. */
    public static final int NOTES = 5;

    /**
     * Whether a companion is owned or serves for pay.
     */
    public enum Status {
        SLAVE,
        FREE;

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

    private final UUID id;
    private final UUID owner;
    private Status status;
    private final int nameSeed;
    private final Gender gender;
    private final int skin;
    private Origin origin;
    private int level;
    private long experience;
    private Skills skills;
    private float health;
    private int food = CompanionRules.MAX_FOOD;
    /** Ticks of hunger gathered towards the next point of food used. */
    private long hungerProgress;
    /** Game time the food ran out, or -1. */
    private long starvingSince = -1;
    private int loyalty;
    private final List<LoyaltyNote> notes = new ArrayList<>();
    /** Ticks of good keeping gathered towards the next point of loyalty. */
    private long keptProgress;
    /** Ticks of starving gathered towards the next loss of loyalty. */
    private long starveProgress;
    /** Game time the companion warned that it will leave, or -1. */
    private long warnedAt = -1;
    /** The kingdom of the branch where it was bought or hired: the currency of its wage and where it is sold back. */
    private final UUID branch;
    private final long price;
    private long wage;
    /** Game day the wage was last paid. */
    private long paidDay;
    private int unpaidDays;
    private CompanionOrder order = CompanionOrder.FOLLOW;
    private WorldPos orderPos;
    private WorldPos lastPos;
    private final long acquired;
    private int kills;

    /**
     * A new companion.
     *
     * @param price what the owner paid, or the worth at hiring
     */
    public Companion(UUID id, UUID owner, Status status, int nameSeed, Gender gender, int skin, Origin origin, int level, Skills skills,
                     UUID branch, long price, long wage, long acquired, long day, int loyalty) {
        this.id = Objects.requireNonNull(id, "id");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.status = Objects.requireNonNull(status, "status");
        this.nameSeed = nameSeed;
        this.gender = Objects.requireNonNull(gender, "gender");
        this.skin = skin;
        this.origin = Objects.requireNonNull(origin, "origin");
        this.skills = Objects.requireNonNull(skills, "skills");
        this.branch = Objects.requireNonNull(branch, "branch");
        if (level < 0 || level > MAX_LEVEL || !Money.isAmount(price) || !Money.isAmount(wage)) {
            throw new IllegalArgumentException("Companion out of range: " + id);
        }
        this.level = level;
        this.price = price;
        this.wage = wage;
        this.acquired = acquired;
        this.paidDay = day;
        this.health = maxHealth();
        this.loyalty = Math.max(0, Math.min(CompanionRules.MAX_LOYALTY, loyalty));
    }

    /**
     * The health of a companion of a level.
     */
    public static float maxHealth(int level) {
        return Math.min(MAX_HEALTH, BASE_HEALTH + HEALTH_PER_LEVEL * Math.max(0, level));
    }

    // ---------------------------------------------------------------- reading

    public UUID id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public Status status() {
        return status;
    }

    public boolean isSlave() {
        return status == Status.SLAVE;
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

    public Origin origin() {
        return origin;
    }

    public int level() {
        return level;
    }

    public long experience() {
        return experience;
    }

    public Skills skills() {
        return skills;
    }

    public float maxHealth() {
        return maxHealth(level);
    }

    public float health() {
        return health;
    }

    public boolean isWounded() {
        return health < maxHealth() * WOUNDED_SHARE;
    }

    public int food() {
        return food;
    }

    public long hungerProgress() {
        return hungerProgress;
    }

    public long starvingSince() {
        return starvingSince;
    }

    /**
     * Whether the companion went without food long enough to lose strength.
     */
    public boolean isStarving(long now, CompanionRules rules) {
        return starvingSince >= 0 && now - starvingSince >= rules.starveTicks;
    }

    public int loyalty() {
        return loyalty;
    }

    /**
     * The last changes of loyalty, newest first.
     */
    public List<LoyaltyNote> notes() {
        return Collections.unmodifiableList(notes);
    }

    public long keptProgress() {
        return keptProgress;
    }

    public long starveProgress() {
        return starveProgress;
    }

    public long warnedAt() {
        return warnedAt;
    }

    public boolean warned() {
        return warnedAt >= 0;
    }

    public UUID branch() {
        return branch;
    }

    public long price() {
        return price;
    }

    public long wage() {
        return wage;
    }

    public long paidDay() {
        return paidDay;
    }

    public int unpaidDays() {
        return unpaidDays;
    }

    public CompanionOrder order() {
        return order;
    }

    public Optional<WorldPos> orderPos() {
        return Optional.ofNullable(orderPos);
    }

    public Optional<WorldPos> lastPos() {
        return Optional.ofNullable(lastPos);
    }

    public long acquired() {
        return acquired;
    }

    public int kills() {
        return kills;
    }

    // ---------------------------------------------------------------- changes, through CompanionState

    void setStatus(Status value) {
        status = value;
    }

    void setOrigin(Origin value) {
        origin = value;
    }

    void setLevel(int value) {
        level = Math.max(0, Math.min(MAX_LEVEL, value));
        health = Math.min(health, maxHealth());
    }

    void setExperience(long value) {
        experience = Math.max(0, value);
    }

    void setSkills(Skills value) {
        skills = Objects.requireNonNull(value, "skills");
    }

    void setHealth(float value) {
        health = Float.isFinite(value) ? Math.max(0.0f, Math.min(maxHealth(), value)) : maxHealth();
    }

    void setFood(int value) {
        food = Math.max(0, Math.min(CompanionRules.MAX_FOOD, value));
    }

    void setHungerProgress(long value) {
        hungerProgress = Math.max(0, value);
    }

    void setStarvingSince(long value) {
        starvingSince = value;
    }

    void setLoyalty(int value) {
        loyalty = Math.max(0, Math.min(CompanionRules.MAX_LOYALTY, value));
    }

    void note(LoyaltyReason reason, int change, long time) {
        if (!notes.isEmpty() && notes.get(0).reason() == reason && Integer.signum(notes.get(0).change()) == Integer.signum(change)) {
            notes.set(0, new LoyaltyNote(reason, notes.get(0).change() + change, time));
            return;
        }
        notes.add(0, new LoyaltyNote(reason, change, time));
        while (notes.size() > NOTES) {
            notes.remove(notes.size() - 1);
        }
    }

    void setKeptProgress(long value) {
        keptProgress = Math.max(0, value);
    }

    void setStarveProgress(long value) {
        starveProgress = Math.max(0, value);
    }

    void setWarnedAt(long value) {
        warnedAt = value;
    }

    void setWage(long value) {
        wage = Math.max(0, value);
    }

    void setPaidDay(long value) {
        paidDay = value;
    }

    void setUnpaidDays(int value) {
        unpaidDays = Math.max(0, value);
    }

    void setOrder(CompanionOrder value, WorldPos pos) {
        order = Objects.requireNonNull(value, "order");
        orderPos = value.atPlace() ? pos : null;
    }

    void setLastPos(WorldPos value) {
        lastPos = value;
    }

    void setKills(int value) {
        kills = Math.max(0, value);
    }

    // ---------------------------------------------------------------- saved data

    /**
     * Restores what changes over a companion's life; for saved data.
     */
    public void restore(long experience, float health, int food, long hungerProgress, long starvingSince, int loyalty, List<LoyaltyNote> notes,
                        long keptProgress, long starveProgress, long warnedAt, long paidDay, int unpaidDays, CompanionOrder order, WorldPos orderPos,
                        WorldPos lastPos, int kills) {
        this.experience = Math.max(0, experience);
        setHealth(health);
        setFood(food);
        this.hungerProgress = Math.max(0, hungerProgress);
        this.starvingSince = starvingSince;
        setLoyalty(loyalty);
        this.notes.clear();
        for (LoyaltyNote note : notes) {
            if (this.notes.size() < NOTES) {
                this.notes.add(note);
            }
        }
        this.keptProgress = Math.max(0, keptProgress);
        this.starveProgress = Math.max(0, starveProgress);
        this.warnedAt = warnedAt;
        this.paidDay = paidDay;
        this.unpaidDays = Math.max(0, unpaidDays);
        this.order = Objects.requireNonNull(order, "order");
        this.orderPos = order.atPlace() ? orderPos : null;
        this.lastPos = lastPos;
        this.kills = Math.max(0, kills);
    }
}
