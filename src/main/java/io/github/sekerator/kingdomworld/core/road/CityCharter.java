package io.github.sekerator.kingdomworld.core.road;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * What a city of a kingdom (its capital is city 0) keeps of its own beyond the town hall: its townsfolk, the people who live
 * there as a number, and when its last newcomer came; its mayor, whether the king stopped its governor, what the governor
 * decided last, what it spent today and the buildings the king gave up that it does not order again for a while. Kept by
 * {@link RoadState} per kingdom and city; a city that changes hands takes its townsfolk with it, its mayor and the rest stay
 * behind.
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
    private UUID mayor;
    private long mayorSince = NEVER;
    private boolean paused;
    private String note = "";
    private long decidedDay = NEVER;
    private long spentDay = NEVER;
    private long spent;
    /** The radius its governor widened the square of the city to, in blocks; 0 while it never did. */
    private int extent;
    /** Templates of buildings the king gave up, by the last game day they are not ordered again. */
    private final Map<String, Long> refused = new LinkedHashMap<>();

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

    // ---------------------------------------------------------------- the governor

    /**
     * The citizen who is the mayor of the city, or {@code null}.
     */
    public UUID mayor() {
        return mayor;
    }

    /**
     * The game time the mayor took office, {@link #NEVER} without one.
     */
    public long mayorSince() {
        return mayorSince;
    }

    /**
     * Makes a citizen the mayor from a game time on; {@code null} ends the office.
     */
    public void setMayor(UUID citizen, long since) {
        mayor = citizen;
        mayorSince = citizen == null ? NEVER : since;
    }

    /**
     * Whether the king stopped the governor of the city: it decides nothing until it is let go on.
     */
    public boolean paused() {
        return paused;
    }

    public void setPaused(boolean value) {
        paused = value;
    }

    /**
     * What the governor decided last, as {@link GovernorNote#encode()} writes it; empty before the first decision.
     */
    public String note() {
        return note;
    }

    public void setNote(String value) {
        note = value == null ? "" : value.length() > GovernorNote.MAX_TEXT ? value.substring(0, GovernorNote.MAX_TEXT) : value;
    }

    /**
     * The last game day the governor decided on, {@link #NEVER} before the first.
     */
    public long decidedDay() {
        return decidedDay;
    }

    public void setDecidedDay(long value) {
        decidedDay = value;
    }

    /**
     * What the governor spent on a game day: what it spent on the day it spent last, 0 on any other.
     */
    public long spent(long day) {
        return day == spentDay ? spent : 0;
    }

    /**
     * The game day the governor spent on last, {@link #NEVER} before the first spending.
     */
    public long spentDay() {
        return spentDay;
    }

    /**
     * What was spent on {@link #spentDay()}.
     */
    public long spentOnDay() {
        return spent;
    }

    /**
     * Counts money the governor spent on a game day.
     */
    public void spend(long day, long amount) {
        if (day != spentDay) {
            spentDay = day;
            spent = 0;
        }
        spent = Math.max(0, Math.min(Long.MAX_VALUE / 4, spent + Math.max(0, amount)));
    }

    public void restoreSpent(long day, long amount) {
        spentDay = day;
        spent = Math.max(0, amount);
    }

    /**
     * The radius a governor widened the square of the city to, in blocks, on the land of its kingdom; 0 while none did. The
     * square is never narrower than it, as it is never narrower than its people need.
     */
    public int extent() {
        return extent;
    }

    public void setExtent(int value) {
        extent = Math.max(0, value);
    }

    /**
     * Templates the king gave up, by the last game day they are not ordered again.
     */
    public Map<String, Long> refused() {
        return Collections.unmodifiableMap(refused);
    }

    /**
     * Remembers that the king gave up a building of a template: the governor does not order it again until the day passed.
     */
    public void refuse(String templateId, long untilDay) {
        if (templateId != null && !templateId.isEmpty()) {
            refused.merge(templateId, untilDay, Math::max);
        }
    }

    /**
     * Whether the governor does not order a template on a game day, the king having given one up lately.
     */
    public boolean refuses(String templateId, long day) {
        Long until = refused.get(templateId);
        return until != null && day <= until;
    }

    /**
     * Forgets what the king gave up before a game day.
     *
     * @return whether anything was forgotten
     */
    public boolean forgetRefused(long day) {
        return refused.values().removeIf(until -> until < day);
    }

    /**
     * The governor of the city is no more: its mayor, its pause, what it decided and remembered go; its townsfolk and what was
     * spent today stay.
     */
    public void clearGovernor() {
        mayor = null;
        mayorSince = NEVER;
        paused = false;
        note = "";
        decidedDay = NEVER;
        refused.clear();
    }

    /**
     * The same city under another kingdom and id: its townsfolk and its square stay with it, its mayor and pause do not, nor what
     * its governor decided, spent or remembered.
     */
    public CityCharter movedTo(UUID kingdom, long city) {
        CityCharter moved = new CityCharter(kingdom, city);
        moved.extent = extent;
        moved.folk = folk;
        moved.folkCarry = folkCarry;
        moved.lastDay = lastDay;
        moved.lastArrival = lastArrival;
        return moved;
    }

    /**
     * Whether it holds nothing worth keeping: no townsfolk, no day or newcomer counted yet and nothing of a governor.
     */
    public boolean empty() {
        return folk == 0 && folkCarry == 0.0 && lastDay == NEVER && lastArrival == NEVER && mayor == null && !paused && note.isEmpty()
                && decidedDay == NEVER && spentDay == NEVER && refused.isEmpty() && extent == 0;
    }
}
