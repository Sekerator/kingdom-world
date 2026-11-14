package io.github.sekerator.kingdomworld.core.road;

import java.util.Objects;
import java.util.UUID;

/**
 * What a city of a kingdom (its capital is city 0) keeps of its own beyond the town hall: its townsfolk, the people who live
 * there as a number, and when its last newcomer came. Kept by {@link RoadState} per kingdom and city; a city that changes
 * hands takes its townsfolk with it.
 *
 * <p>Not thread safe; the server uses it from the server thread only.
 */
public final class CityCharter {
    /** Never happened. */
    public static final long NEVER = Long.MIN_VALUE;
    /** The most townsfolk a city holds, far beyond any city that can be built; it keeps every sum of them in range. */
    public static final int MAX_FOLK = 1_000_000_000;

    private final UUID kingdomId;
    private final long cityId;
    private int folk;
    private double folkCarry;
    private long lastDay = NEVER;
    private long lastArrival = NEVER;

    /**
     * @param cityId 0 for the capital
     */
    public CityCharter(UUID kingdomId, long cityId) {
        this.kingdomId = Objects.requireNonNull(kingdomId, "kingdomId");
        if (cityId < 0) {
            throw new IllegalArgumentException("City out of range: " + cityId);
        }
        this.cityId = cityId;
    }

    public UUID kingdomId() {
        return kingdomId;
    }

    /**
     * The city, 0 for the capital.
     */
    public long cityId() {
        return cityId;
    }

    /**
     * The townsfolk: people of the city who live there as a number, without a body or a record.
     */
    public int folk() {
        return folk;
    }

    public void setFolk(int value) {
        folk = Math.max(0, Math.min(MAX_FOLK, value));
    }

    /**
     * The part of a newcomer the townsfolk grew by and that did not make a whole one yet, from 0 up to 1.
     */
    public double folkCarry() {
        return folkCarry;
    }

    public void setFolkCarry(double value) {
        folkCarry = Double.isFinite(value) ? Math.max(0.0, Math.min(0.999_999, value)) : 0.0;
    }

    /**
     * The last day the townsfolk lived through, {@link #NEVER} before the first.
     */
    public long lastDay() {
        return lastDay;
    }

    public void setLastDay(long value) {
        lastDay = value;
    }

    /**
     * Game time the last newcomer came to the city, {@link #NEVER} before the first.
     */
    public long lastArrival() {
        return lastArrival;
    }

    public void setLastArrival(long value) {
        lastArrival = value;
    }

    /**
     * The same city under another kingdom and id, as it is: its townsfolk stay with it.
     */
    public CityCharter movedTo(UUID kingdom, long city) {
        CityCharter moved = new CityCharter(kingdom, city);
        moved.folk = folk;
        moved.folkCarry = folkCarry;
        moved.lastDay = lastDay;
        moved.lastArrival = lastArrival;
        return moved;
    }

    /**
     * Whether it holds nothing worth keeping: no townsfolk and no day or newcomer counted yet.
     */
    public boolean empty() {
        return folk == 0 && folkCarry == 0.0 && lastDay == NEVER && lastArrival == NEVER;
    }
}
