package io.github.sekerator.kingdomworld.core.nobility;

import io.github.sekerator.kingdomworld.core.citizen.CitizenNames;
import io.github.sekerator.kingdomworld.core.citizen.Gender;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A spy of a kingdom, hired from the agents of the guild. Spies have no bodies: they stay at home and watch for foreign
 * agents and plots, or go on a mission abroad for some days. A caught spy is held for ransom or put to death.
 */
public final class Spy {
    public static final int MAX_SKILL = 95;

    public enum Status {
        /** At home, doing nothing in particular. */
        IDLE,
        /** At home, hunting foreign spies and plots. */
        COUNTER,
        /** Away on a mission. */
        MISSION,
        /** Held by another kingdom. */
        CAPTURED;

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
     * What a spy can be sent to do.
     */
    public enum Task {
        /** Learn the treasury, the army, the wars, the plans of the ruler and the plots of a kingdom. */
        SCOUT(2, 70),
        /** Stir up unrest: less stability, and a plot ripens faster with our backing. */
        INCITE(3, 55),
        /** Spoil part of the stores of a kingdom. */
        SABOTAGE(3, 50),
        /** Take a small part of a treasury into ours. */
        THEFT(3, 40),
        /** Kill the head of a noble house of a kingdom of the world. */
        ASSASSINATE(4, 30);

        private final int days;
        private final int chance;

        Task(int days, int chance) {
            this.days = days;
            this.chance = chance;
        }

        public int days() {
            return days;
        }

        /**
         * The chance of success of a spy of skill 50 against a kingdom without counter-intelligence, in percent.
         */
        public int chance() {
            return chance;
        }

        public boolean hostile() {
            return this != SCOUT;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Task> byId(String id) {
            for (Task task : values()) {
                if (task.id().equals(id)) {
                    return Optional.of(task);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * A mission abroad.
     *
     * @param house the house whose head is the target ({@link Task#ASSASSINATE}), 0 otherwise
     * @param ends  the game day the mission is done on, at the earliest
     */
    public record Mission(Task task, UUID target, long house, long started, long ends) {
        public Mission {
            Objects.requireNonNull(task, "task");
            Objects.requireNonNull(target, "target");
        }
    }

    private final long id;
    private final UUID owner;
    private final int seed;
    private final Gender gender;
    private int skill;
    private Status status;
    private Mission mission;
    private UUID captor;
    private long ransom;
    private long releaseBy;
    private final long hired;

    public Spy(long id, UUID owner, int seed, Gender gender, int skill, Status status, Mission mission, UUID captor, long ransom,
               long releaseBy, long hired) {
        this.id = id;
        this.owner = Objects.requireNonNull(owner, "owner");
        this.seed = seed;
        this.gender = Objects.requireNonNull(gender, "gender");
        this.skill = Math.max(1, Math.min(MAX_SKILL, skill));
        this.status = Objects.requireNonNull(status, "status");
        this.mission = status == Status.MISSION ? Objects.requireNonNull(mission, "mission") : null;
        this.captor = status == Status.CAPTURED ? Objects.requireNonNull(captor, "captor") : null;
        this.ransom = status == Status.CAPTURED ? Math.max(0, ransom) : 0;
        this.releaseBy = status == Status.CAPTURED ? releaseBy : 0;
        this.hired = hired;
    }

    public long id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public int seed() {
        return seed;
    }

    public Gender gender() {
        return gender;
    }

    public String name(CitizenNames.Alphabet alphabet) {
        return CitizenNames.render(seed, gender, alphabet);
    }

    public int skill() {
        return skill;
    }

    void learn(int amount) {
        skill = Math.max(1, Math.min(MAX_SKILL, skill + amount));
    }

    public Status status() {
        return status;
    }

    public Optional<Mission> mission() {
        return Optional.ofNullable(mission);
    }

    public Optional<UUID> captor() {
        return Optional.ofNullable(captor);
    }

    public long ransom() {
        return ransom;
    }

    public long releaseBy() {
        return releaseBy;
    }

    public long hired() {
        return hired;
    }

    void send(Mission value) {
        status = Status.MISSION;
        mission = Objects.requireNonNull(value, "value");
        captor = null;
    }

    void home(boolean counter) {
        status = counter ? Status.COUNTER : Status.IDLE;
        mission = null;
        captor = null;
        ransom = 0;
        releaseBy = 0;
    }

    void capture(UUID by, long price, long until) {
        status = Status.CAPTURED;
        mission = null;
        captor = Objects.requireNonNull(by, "by");
        ransom = Math.max(0, price);
        releaseBy = until;
    }
}
