package io.github.sekerator.kingdomworld.core.npc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The kingdoms of the world of a server: the plan of their places (with where the planner stopped), the progress of every
 * appearance and the record of every kingdom that appeared.
 */
public final class NpcState {
    private final Map<Integer, NpcSite> sites = new LinkedHashMap<>();
    private final Map<UUID, NpcKingdom> kingdoms = new LinkedHashMap<>();
    private final Map<UUID, RulerMemory> rulers = new LinkedHashMap<>();
    private int nextSiteId = 1;
    private int planCursor;
    private int planShift;
    private int planPass;
    private boolean planned;
    private Runnable changeListener = () -> { };

    public void setChangeListener(Runnable listener) {
        changeListener = Objects.requireNonNull(listener, "listener");
        sites.values().forEach(site -> site.setChangeListener(listener));
    }

    // ---------------------------------------------------------------- the plan

    public Collection<NpcSite> sites() {
        return Collections.unmodifiableCollection(sites.values());
    }

    public Optional<NpcSite> site(int id) {
        return Optional.ofNullable(sites.get(id));
    }

    /**
     * Adds a place of the first layout to the plan with the next number.
     */
    public NpcSite addSite(int x, int z, NpcLevel level, long seed, List<NpcSite.Town> towns) {
        return addSite(x, z, level, seed, towns, NpcSite.LAYOUT_GRID, "", "");
    }

    /**
     * Adds a place to the plan with the next number.
     *
     * @param layout how its capital is laid out
     * @param style  the id of its look, or empty
     * @param wood   the wood that grows there, or empty
     */
    public NpcSite addSite(int x, int z, NpcLevel level, long seed, List<NpcSite.Town> towns, int layout, String style, String wood) {
        NpcSite site = new NpcSite(nextSiteId++, x, z, level, seed, towns, layout, style, wood);
        site.setChangeListener(changeListener);
        sites.put(site.id(), site);
        changeListener.run();
        return site;
    }

    public int planCursor() {
        return planCursor;
    }

    public int planShift() {
        return planShift;
    }

    /**
     * The pass over the candidates the plan is on: 0 at first, more once it asks less of a place.
     */
    public int planPass() {
        return planPass;
    }

    /**
     * Whether the plan was made to its end.
     */
    public boolean planned() {
        return planned;
    }

    public void setPlanProgress(int cursor, int shift, boolean done) {
        setPlanProgress(cursor, shift, planPass, done);
    }

    public void setPlanProgress(int cursor, int shift, int pass, boolean done) {
        planCursor = Math.max(0, cursor);
        planShift = Math.max(0, shift);
        planPass = Math.max(0, pass);
        planned = done;
        changeListener.run();
    }

    /**
     * Forgets the places that are only planned and starts the plan again; places that appeared or are appearing stay.
     */
    public int resetPlan() {
        List<Integer> gone = new ArrayList<>();
        for (NpcSite site : sites.values()) {
            if (site.status() == NpcSite.Status.PLANNED || site.status() == NpcSite.Status.BLOCKED) {
                gone.add(site.id());
            }
        }
        gone.forEach(sites::remove);
        planCursor = 0;
        planShift = 0;
        planPass = 0;
        planned = false;
        changeListener.run();
        return gone.size();
    }

    /**
     * The place that is appearing now, if any: one appears at a time.
     */
    public Optional<NpcSite> spawning() {
        for (NpcSite site : sites.values()) {
            if (site.status() == NpcSite.Status.SPAWNING) {
                return Optional.of(site);
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- kingdoms

    public Collection<NpcKingdom> kingdoms() {
        return Collections.unmodifiableCollection(kingdoms.values());
    }

    public Optional<NpcKingdom> kingdom(UUID kingdomId) {
        return Optional.ofNullable(kingdoms.get(kingdomId));
    }

    public boolean isNpc(UUID kingdomId) {
        return kingdomId != null && kingdoms.containsKey(kingdomId);
    }

    public void putKingdom(NpcKingdom kingdom) {
        kingdoms.put(kingdom.kingdomId(), kingdom);
        changeListener.run();
    }

    public Optional<NpcKingdom> removeKingdom(UUID kingdomId) {
        rulers.remove(kingdomId);
        rulers.values().forEach(memory -> memory.forgetRelation(kingdomId));
        NpcKingdom removed = kingdoms.remove(kingdomId);
        if (removed != null) {
            changeListener.run();
        }
        return Optional.ofNullable(removed);
    }

    // ---------------------------------------------------------------- rulers

    /**
     * What the ruler of a kingdom of the world remembers; a ruler that never took a turn starts with an empty memory. Changes
     * to the memory are saved with the next {@link #rulerChanged()}.
     */
    public RulerMemory ruler(UUID kingdomId) {
        return rulers.computeIfAbsent(kingdomId, id -> new RulerMemory());
    }

    public Optional<RulerMemory> existingRuler(UUID kingdomId) {
        return Optional.ofNullable(rulers.get(kingdomId));
    }

    public Map<UUID, RulerMemory> rulers() {
        return Collections.unmodifiableMap(rulers);
    }

    /**
     * Marks the memories of the rulers as changed, so that they are saved.
     */
    public void rulerChanged() {
        changeListener.run();
    }

    // ---------------------------------------------------------------- persistence

    public void restoreRuler(UUID kingdomId, RulerMemory memory) {
        rulers.put(kingdomId, memory);
    }

    /**
     * Restores a place from saved data.
     *
     * @throws IllegalArgumentException if its number is taken
     */
    public NpcSite restoreSite(NpcSite site) {
        if (sites.containsKey(site.id())) {
            throw new IllegalArgumentException("Duplicate site " + site.id());
        }
        site.setChangeListener(changeListener);
        sites.put(site.id(), site);
        nextSiteId = Math.max(nextSiteId, site.id() + 1);
        return site;
    }

    public void restorePlan(int cursor, int shift, boolean done, int nextId) {
        restorePlan(cursor, shift, 0, done, nextId);
    }

    public void restorePlan(int cursor, int shift, int pass, boolean done, int nextId) {
        planCursor = Math.max(0, cursor);
        planShift = Math.max(0, shift);
        planPass = Math.max(0, pass);
        planned = done;
        nextSiteId = Math.max(nextSiteId, nextId);
    }

    public void restoreKingdom(NpcKingdom kingdom) {
        if (kingdoms.containsKey(kingdom.kingdomId())) {
            throw new IllegalArgumentException("Duplicate kingdom of the world " + kingdom.kingdomId());
        }
        kingdoms.put(kingdom.kingdomId(), kingdom);
    }

    public int nextSiteId() {
        return nextSiteId;
    }

    /**
     * Problems of consistency, for the log: kingdoms whose place is missing, places that point at no kingdom.
     */
    public List<String> verify() {
        List<String> problems = new ArrayList<>();
        for (NpcKingdom kingdom : kingdoms.values()) {
            if (!sites.containsKey(kingdom.siteId())) {
                problems.add("kingdom " + kingdom.kingdomId() + " names the missing site " + kingdom.siteId());
            }
        }
        for (NpcSite site : sites.values()) {
            boolean standing = site.status() == NpcSite.Status.SPAWNED || site.status() == NpcSite.Status.FOUNDED;
            if (standing && (site.kingdomId().isEmpty() || !kingdoms.containsKey(site.kingdomId().get()))) {
                problems.add(site + " appeared without a kingdom");
            }
        }
        for (UUID ruler : rulers.keySet()) {
            if (!kingdoms.containsKey(ruler)) {
                problems.add("the memory of a ruler names the missing kingdom " + ruler);
            }
        }
        return problems;
    }
}
