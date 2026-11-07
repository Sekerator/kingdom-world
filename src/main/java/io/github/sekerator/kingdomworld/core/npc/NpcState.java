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
    private int nextSiteId = 1;
    private int planCursor;
    private int planShift;
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
     * Adds a place to the plan with the next number.
     */
    public NpcSite addSite(int x, int z, NpcLevel level, long seed, List<NpcSite.Town> towns) {
        NpcSite site = new NpcSite(nextSiteId++, x, z, level, seed, towns);
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
     * Whether the plan was made to its end.
     */
    public boolean planned() {
        return planned;
    }

    public void setPlanProgress(int cursor, int shift, boolean done) {
        planCursor = Math.max(0, cursor);
        planShift = Math.max(0, shift);
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
        NpcKingdom removed = kingdoms.remove(kingdomId);
        if (removed != null) {
            changeListener.run();
        }
        return Optional.ofNullable(removed);
    }

    // ---------------------------------------------------------------- persistence

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
        planCursor = Math.max(0, cursor);
        planShift = Math.max(0, shift);
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
            if (site.status() == NpcSite.Status.SPAWNED && (site.kingdomId().isEmpty() || !kingdoms.containsKey(site.kingdomId().get()))) {
                problems.add(site + " appeared without a kingdom");
            }
        }
        return problems;
    }
}
