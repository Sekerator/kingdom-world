package io.github.sekerator.kingdomworld.core.citizen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The citizens of all kingdoms together with the places they use. Changes that touch both (giving a citizen a
 * bed, losing a workplace, a death) go through this class so that a citizen's home or workplace is always a
 * place of its kingdom occupied by exactly that citizen.
 *
 * <p>Not thread safe; the server uses it from the server thread only.
 */
public final class Citizens {
    private final CitizenRegistry registry = new CitizenRegistry();
    private final SiteRegistry sites = new SiteRegistry();

    public void setChangeListener(Runnable listener) {
        registry.setChangeListener(listener);
        sites.setChangeListener(listener);
    }

    public CitizenRegistry registry() {
        return registry;
    }

    public SiteRegistry sites() {
        return sites;
    }

    public Optional<Citizen> get(UUID id) {
        return registry.get(id);
    }

    // ---------------------------------------------------------------- citizens

    public void add(Citizen citizen) {
        registry.add(citizen);
    }

    /**
     * Gives a citizen a profession. A workplace of another kind is released; with an unknown profession id the
     * citizen keeps the id but does not work.
     */
    public void setProfession(Citizen citizen, String professionId) {
        Objects.requireNonNull(professionId, "professionId");
        String kind = Professions.REGISTRY.byId(professionId).map(Profession::workplaceKind).orElse(null);
        if (citizen.workplace() != null) {
            Optional<Site> site = sites.get(citizen.workplace());
            if (kind == null || site.isEmpty() || !site.get().kind().equals(kind)) {
                releaseWorkplace(citizen);
            }
        }
        citizen.setProfessionId(professionId);
    }

    /**
     * A citizen dies: the record leaves the population, its places become free and the death is written to the
     * kingdom's chronicle.
     */
    public Optional<DeathRecord> die(UUID id, CauseOfDeath cause, String killer, long gameTime, WorldPos place) {
        Optional<Citizen> citizen = registry.get(id);
        if (citizen.isEmpty()) {
            return Optional.empty();
        }
        releaseHome(citizen.get());
        releaseWorkplace(citizen.get());
        return registry.die(id, cause, killer, gameTime, place);
    }

    /**
     * Forgets the citizens and places of a kingdom.
     *
     * @return the citizens that were removed
     */
    public List<Citizen> removeKingdom(UUID kingdomId) {
        List<Citizen> removed = registry.removeKingdom(kingdomId);
        sites.removeKingdom(kingdomId);
        return removed;
    }

    // ---------------------------------------------------------------- places

    /**
     * Records a place of a kingdom. If another place was known at the position, its occupant loses it.
     *
     * @return whether anything changed
     */
    public boolean addSite(WorldPos pos, String kind, UUID kingdomId) {
        Optional<Site> current = sites.get(pos);
        if (current.isPresent() && current.get().kind().equals(kind) && current.get().kingdomId().equals(kingdomId)) {
            return false;
        }
        current.ifPresent(this::evict);
        sites.put(pos, kind, kingdomId);
        return true;
    }

    /**
     * Forgets a place, for example a broken bed; its occupant loses it.
     */
    public Optional<Site> removeSite(WorldPos pos) {
        Optional<Site> site = sites.get(pos);
        site.ifPresent(this::evict);
        return sites.remove(pos);
    }

    /**
     * Forgets the places of a kingdom in one chunk, for example when the kingdom gives the chunk up.
     *
     * @return the places that were removed
     */
    public List<Site> removeSitesInChunk(UUID kingdomId, String dimension, long chunk) {
        List<Site> removed = new ArrayList<>();
        for (Site site : sites.sitesInChunk(dimension, chunk)) {
            if (site.kingdomId().equals(kingdomId)) {
                removeSite(site.pos()).ifPresent(removed::add);
            }
        }
        return removed;
    }

    /**
     * Moves a citizen into a free bed of its kingdom, leaving its old bed.
     *
     * @return whether the citizen now lives there
     */
    public boolean assignHome(Citizen citizen, WorldPos bed) {
        return assign(citizen, bed, SiteKinds.BED, true);
    }

    /**
     * Gives a citizen a free workplace of the kind its profession needs, leaving its old workplace.
     *
     * @return whether the citizen now works there
     */
    public boolean assignWorkplace(Citizen citizen, WorldPos workplace) {
        Optional<Profession> profession = Professions.REGISTRY.byId(citizen.professionId());
        if (profession.isEmpty() || !profession.get().needsWorkplace()) {
            return false;
        }
        return assign(citizen, workplace, profession.get().workplaceKind(), false);
    }

    private boolean assign(Citizen citizen, WorldPos pos, String kind, boolean home) {
        if (!citizen.isAlive() || registry.get(citizen.id()).isEmpty()) {
            return false;
        }
        Optional<Site> site = sites.get(pos);
        if (site.isEmpty() || !site.get().kind().equals(kind) || !site.get().kingdomId().equals(citizen.kingdomId())) {
            return false;
        }
        if (citizen.id().equals(site.get().occupant())) {
            return true;
        }
        if (!site.get().isFree()) {
            return false;
        }
        if (home) {
            releaseHome(citizen);
            citizen.setHome(pos);
        } else {
            releaseWorkplace(citizen);
            citizen.setWorkplace(pos);
        }
        sites.setOccupant(pos, citizen.id());
        return true;
    }

    public void releaseHome(Citizen citizen) {
        WorldPos home = citizen.home();
        if (home != null) {
            citizen.setHome(null);
            sites.get(home).filter(site -> citizen.id().equals(site.occupant()))
                    .ifPresent(site -> sites.setOccupant(home, null));
        }
    }

    public void releaseWorkplace(Citizen citizen) {
        WorldPos workplace = citizen.workplace();
        if (workplace != null) {
            citizen.setWorkplace(null);
            sites.get(workplace).filter(site -> citizen.id().equals(site.occupant()))
                    .ifPresent(site -> sites.setOccupant(workplace, null));
        }
    }

    private void evict(Site site) {
        if (site.occupant() == null) {
            return;
        }
        registry.get(site.occupant()).ifPresent(citizen -> {
            if (site.pos().equals(citizen.home())) {
                citizen.setHome(null);
            }
            if (site.pos().equals(citizen.workplace())) {
                citizen.setWorkplace(null);
            }
        });
        sites.setOccupant(site.pos(), null);
    }

    // ---------------------------------------------------------------- persistence

    /**
     * Restores the places and the population of one kingdom from saved data; all or nothing. Homes and
     * workplaces of the citizens that do not match a place are dropped (the citizen finds a new one).
     *
     * @throws IllegalArgumentException if the data conflicts with what is already known
     */
    public void restore(UUID kingdomId, List<Site> places, List<Citizen> citizens, long lastArrival,
                        List<DeathRecord> chronicle) {
        for (Site site : places) {
            if (!site.kingdomId().equals(kingdomId)) {
                throw new IllegalArgumentException("Place " + site.pos() + " belongs to another kingdom");
            }
            if (sites.get(site.pos()).isPresent()) {
                throw new IllegalArgumentException("Two places at " + site.pos());
            }
        }
        java.util.Set<WorldPos> seen = new java.util.HashSet<>();
        for (Site site : places) {
            if (!seen.add(site.pos())) {
                throw new IllegalArgumentException("Two places at " + site.pos());
            }
        }
        registry.restore(kingdomId, citizens, lastArrival, chronicle);
        java.util.Map<WorldPos, Site> byPos = new java.util.HashMap<>();
        for (Site site : places) {
            byPos.put(site.pos(), new Site(site.pos(), site.kind(), kingdomId, null));
        }
        for (Citizen citizen : citizens) {
            claim(byPos, citizen, citizen.home(), SiteKinds.BED, true);
            String kind = Professions.REGISTRY.byId(citizen.professionId()).map(Profession::workplaceKind).orElse(null);
            claim(byPos, citizen, citizen.workplace(), kind, false);
        }
        for (Site site : byPos.values()) {
            sites.restore(site);
        }
    }

    private static void claim(java.util.Map<WorldPos, Site> byPos, Citizen citizen, WorldPos pos, String kind, boolean home) {
        if (pos == null) {
            return;
        }
        Site site = byPos.get(pos);
        if (site != null && site.kind().equals(kind) && site.isFree()) {
            byPos.put(pos, site.withOccupant(citizen.id()));
        } else if (home) {
            citizen.setHome(null);
        } else {
            citizen.setWorkplace(null);
        }
    }
}
