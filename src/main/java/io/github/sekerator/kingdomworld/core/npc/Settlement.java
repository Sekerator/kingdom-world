package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.land.Tier;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A settlement of a state of the world as data: a city, a town, a village or a hamlet (or the record of a capital), with its
 * place, its names and its life in numbers until its buildings are raised. The numbers never become money or goods: money
 * and goods come only from named citizens.
 */
public final class Settlement {
    /**
     * How far a settlement is.
     */
    public enum Status {
        /** Planned, not founded yet. */
        PLANNED,
        /** It lives as data: numbers, a place on the map. */
        DATA,
        /** Its buildings are being raised. */
        RAISING,
        /** It stands in the world. */
        STANDING,
        /** The world turned it down: the land became someone else's or players built there. */
        BLOCKED,
        /** Destroyed. */
        RUINED;

        private static final Status[] VALUES = values();

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Status> byId(String id) {
            for (Status status : VALUES) {
                if (status.id().equals(id)) {
                    return Optional.of(status);
                }
            }
            return Optional.empty();
        }
    }

    private final int id;
    private final UUID kingdomId;
    private final int province;
    private final int parent;
    private final Tier tier;
    private int x;
    private int z;
    private final long seed;
    private final NpcNames.Name name;
    private Status status = Status.DATA;
    private String stage = "";
    private int step;
    private int groundY;
    private int given;
    private long cityId;
    private int population;
    private int housing;
    private int food;
    private int guards;
    private int militia;
    private int prosperity;
    /** Food the land around feeds a person with in a day, in hundredths: 100 feeds exactly. */
    private int fertility = 100;
    private long lastDay = -1;
    private long lastPlayerTick;
    /** Citizens folded away while nobody was near, 8 bytes each; filled by the named citizens of the settlements. */
    private long[] folded = new long[0];
    private Runnable changeListener = () -> {
    };

    /**
     * @param parent the settlement it belongs to (a village to its town), or 0 for a capital
     */
    public Settlement(int id, UUID kingdomId, int province, int parent, Tier tier, int x, int z, long seed, NpcNames.Name name) {
        if (id <= 0) {
            throw new IllegalArgumentException("Settlement id must be positive: " + id);
        }
        this.id = id;
        this.kingdomId = Objects.requireNonNull(kingdomId, "kingdom");
        this.province = province;
        this.parent = parent;
        this.tier = Objects.requireNonNull(tier, "tier");
        this.x = x;
        this.z = z;
        this.seed = seed;
        this.name = Objects.requireNonNull(name, "name");
    }

    /** The flag of the stores it was founded with. */
    public static final int GIVEN_STORES = 4;
    /** The flag of a place moved once because its own was turned down. */
    public static final int GIVEN_MOVED = 8;
    /** The flag of its laws. */
    public static final int GIVEN_LAWS = 2;
    /** The flag of its guard come from abroad. */
    public static final int GIVEN_GUARDS = 16;

    /**
     * Moves the settlement to another place, when its own was turned down by the world.
     */
    public void moveTo(int newX, int newZ) {
        x = newX;
        z = newZ;
        changed();
    }

    /**
     * Its people as a number: the citizens of a settlement that no longer stands go to another one.
     */
    public void setPopulation(int value) {
        population = Math.max(0, value);
        changed();
    }

        public void setChangeListener(Runnable listener) {
        changeListener = Objects.requireNonNull(listener, "listener");
    }

    private void changed() {
        changeListener.run();
    }

    public int id() {
        return id;
    }

    public UUID kingdomId() {
        return kingdomId;
    }

    public int province() {
        return province;
    }

    public int parent() {
        return parent;
    }

    public Tier tier() {
        return tier;
    }

    public int x() {
        return x;
    }

    public int z() {
        return z;
    }

    public long seed() {
        return seed;
    }

    public NpcNames.Name name() {
        return name;
    }

    public Status status() {
        return status;
    }

    public String stage() {
        return stage;
    }

    public int step() {
        return step;
    }

    public int groundY() {
        return groundY;
    }

    public int givenFlags() {
        return given;
    }

    public boolean given(int flag) {
        return (given & flag) != 0;
    }

    public long cityId() {
        return cityId;
    }

    public int population() {
        return population;
    }

    public int housing() {
        return housing;
    }

    public int food() {
        return food;
    }

    public int guards() {
        return guards;
    }

    public int militia() {
        return militia;
    }

    public int prosperity() {
        return prosperity;
    }

    public int fertility() {
        return fertility;
    }

    public long lastDay() {
        return lastDay;
    }

    public long lastPlayerTick() {
        return lastPlayerTick;
    }

    public long[] folded() {
        return folded.clone();
    }

    public void setStatus(Status value) {
        status = Objects.requireNonNull(value, "status");
        changed();
    }

    public void setStage(String value, int newStep) {
        stage = Objects.requireNonNull(value, "stage");
        step = newStep;
        changed();
    }

    public void setGroundY(int value) {
        groundY = value;
        changed();
    }

    public void give(int flag) {
        given |= flag;
        changed();
    }

    public void setCityId(long value) {
        cityId = value;
        changed();
    }

    public void setLastPlayerTick(long value) {
        lastPlayerTick = value;
        changed();
    }

    public void setFolded(long[] value) {
        folded = value.clone();
        changed();
    }

    /**
     * Its numbers when it is founded: its houses and people by its rank and seed, the land it farms.
     */
    public void found(int newHousing, int newPopulation, int newFertility) {
        housing = Math.max(0, newHousing);
        population = Math.max(0, Math.min(newHousing, newPopulation));
        fertility = Math.max(0, newFertility);
        food = population * SettlementLife.START_FOOD_DAYS;
        prosperity = SettlementLife.START_PROSPERITY;
        guards = SettlementLife.guards(tier);
        militia = SettlementLife.militia(tier, population);
        changed();
    }

    /**
     * A day of its life in numbers.
     */
    public void live(SettlementLife.Day day, long dayNumber) {
        population = day.population();
        food = day.food();
        guards = day.guards();
        militia = day.militia();
        prosperity = day.prosperity();
        lastDay = dayNumber;
        changed();
    }

    /**
     * Puts back what was saved.
     */
    public void restore(Status savedStatus, String savedStage, int savedStep, int savedGround, int savedGiven, long savedCity, int savedPopulation,
                        int savedHousing, int savedFood, int savedGuards, int savedMilitia, int savedProsperity, int savedFertility, long savedDay,
                        long savedPlayerTick, long[] savedFolded) {
        status = Objects.requireNonNull(savedStatus, "status");
        stage = Objects.requireNonNull(savedStage, "stage");
        step = savedStep;
        groundY = savedGround;
        given = savedGiven;
        cityId = savedCity;
        population = savedPopulation;
        housing = savedHousing;
        food = savedFood;
        guards = savedGuards;
        militia = savedMilitia;
        prosperity = savedProsperity;
        fertility = savedFertility;
        lastDay = savedDay;
        lastPlayerTick = savedPlayerTick;
        folded = savedFolded.clone();
    }

    @Override
    public String toString() {
        return "settlement " + id + " " + tier.name().toLowerCase(Locale.ROOT) + " " + name.latin() + " at " + x + ", " + z + " (" + status.id()
                + ", " + population + " people)" + (folded.length == 0 ? "" : " " + Arrays.toString(folded));
    }
}
