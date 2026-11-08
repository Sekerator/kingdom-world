package io.github.sekerator.kingdomworld.core.nobility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The court of one kingdom: the content of its parties, the days it was played, how long its stability has been low, what
 * weighs on its stability lately, the rebels that keep its income, and its chronicle and the reports of its spies.
 */
public final class Court {
    public static final int MAX_CHRONICLE = 32;
    public static final int MAX_REPORTS = 24;
    public static final int MAX_MODIFIERS = 12;

    private final UUID kingdomId;
    private final EnumMap<Faction, Integer> content = new EnumMap<>(Faction.class);
    /** Days of the court: game days on which the kingdom was played. */
    private long playedDays;
    /** The game day last counted as played. */
    private long lastPlayed = Long.MIN_VALUE;
    private int lowDays;
    private boolean warned;
    private long lastDemand;
    private long lastPlayerGrant = Long.MIN_VALUE / 4;
    private int rebelDays;
    private long rebelHouse;
    private final List<Stability.Modifier> modifiers = new ArrayList<>();
    private final List<Note> chronicle = new ArrayList<>();
    private final List<Note> reports = new ArrayList<>();
    private Stability.Result stability = Stability.Result.NEUTRAL;

    public Court(UUID kingdomId) {
        this.kingdomId = Objects.requireNonNull(kingdomId, "kingdomId");
        for (Faction faction : Faction.values()) {
            content.put(faction, 50);
        }
    }

    public UUID kingdomId() {
        return kingdomId;
    }

    public int content(Faction faction) {
        return content.getOrDefault(faction, 50);
    }

    public Map<Faction, Integer> contents() {
        return Collections.unmodifiableMap(content);
    }

    void changeContent(Faction faction, int amount) {
        content.put(faction, Math.max(0, Math.min(100, content(faction) + amount)));
    }

    public long playedDays() {
        return playedDays;
    }

    public long lastPlayed() {
        return lastPlayed;
    }

    void played(long day) {
        lastPlayed = day;
        playedDays++;
    }

    public int lowDays() {
        return lowDays;
    }

    public boolean warned() {
        return warned;
    }

    void setLow(int days, boolean warning) {
        lowDays = Math.max(0, days);
        warned = warning;
    }

    public long lastDemand() {
        return lastDemand;
    }

    void demanded(long day) {
        lastDemand = day;
    }

    public long lastPlayerGrant() {
        return lastPlayerGrant;
    }

    public void grantedPlayer(long day) {
        lastPlayerGrant = day;
    }

    public int rebelDays() {
        return rebelDays;
    }

    public long rebelHouse() {
        return rebelHouse;
    }

    void rebels(int days, long house) {
        rebelDays = Math.max(0, days);
        rebelHouse = days > 0 ? house : 0;
    }

    public List<Stability.Modifier> modifiers() {
        return Collections.unmodifiableList(modifiers);
    }

    void modify(String cause, int amount, long until) {
        modifiers.removeIf(modifier -> modifier.cause().equals(cause));
        modifiers.add(new Stability.Modifier(cause, amount, until));
        while (modifiers.size() > MAX_MODIFIERS) {
            modifiers.remove(0);
        }
    }

    void expire(long day) {
        modifiers.removeIf(modifier -> modifier.until() <= day);
    }

    public List<Note> chronicle() {
        return Collections.unmodifiableList(chronicle);
    }

    void write(Note note) {
        chronicle.add(0, note);
        while (chronicle.size() > MAX_CHRONICLE) {
            chronicle.remove(chronicle.size() - 1);
        }
    }

    public List<Note> reports() {
        return Collections.unmodifiableList(reports);
    }

    void report(Note note) {
        reports.add(0, note);
        while (reports.size() > MAX_REPORTS) {
            reports.remove(reports.size() - 1);
        }
    }

    public Stability.Result stability() {
        return stability;
    }

    void setStability(Stability.Result value) {
        stability = Objects.requireNonNull(value, "value");
    }

    /**
     * Puts back what was saved.
     */
    public void restore(Map<Faction, Integer> savedContent, long savedPlayed, long savedLastPlayed, int savedLow, boolean savedWarned,
                        long savedLastDemand, long savedLastGrant, int savedRebelDays, long savedRebelHouse,
                        List<Stability.Modifier> savedModifiers, List<Note> savedChronicle, List<Note> savedReports, int savedStability) {
        savedContent.forEach((faction, value) -> content.put(faction, Math.max(0, Math.min(100, value))));
        playedDays = Math.max(0, savedPlayed);
        lastPlayed = savedLastPlayed;
        lowDays = Math.max(0, savedLow);
        warned = savedWarned;
        lastDemand = savedLastDemand;
        lastPlayerGrant = savedLastGrant;
        rebels(savedRebelDays, savedRebelHouse);
        modifiers.clear();
        modifiers.addAll(savedModifiers.subList(0, Math.min(MAX_MODIFIERS, savedModifiers.size())));
        chronicle.clear();
        chronicle.addAll(savedChronicle.subList(0, Math.min(MAX_CHRONICLE, savedChronicle.size())));
        reports.clear();
        reports.addAll(savedReports.subList(0, Math.min(MAX_REPORTS, savedReports.size())));
        stability = new Stability.Result(Math.max(0, Math.min(Stability.MAX, savedStability)), List.of());
    }
}
