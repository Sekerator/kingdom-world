package io.github.sekerator.kingdomworld.core.citizen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The citizens of all kingdoms together with the places they use, the work posts and the record of production.
 * Changes that touch more than one (giving a citizen a bed, losing a workplace, a death) go through this class
 * so that a citizen's home or workplace is always a place of its kingdom occupied by that citizen: a bed or a
 * composter by exactly that citizen, a work post with the citizen among its workers.
 *
 * <p>Not thread safe; the server uses it from the server thread only.
 */
public final class Citizens {
    private final CitizenRegistry registry = new CitizenRegistry();
    private final SiteRegistry sites = new SiteRegistry();
    private final WorkPosts posts = new WorkPosts();
    private final ProductionLedger ledger = new ProductionLedger();
    private int newPostRadius = GatheringRules.DEFAULT.defaultRadius();
    private int newHerdSize = GatheringRules.DEFAULT.maxHerd() / 2;
    private GatheringRules postRules = GatheringRules.DEFAULT;

    private java.util.function.BiPredicate<UUID, WorldPos> homeRule = (kingdom, bed) -> true;
    private java.util.function.Consumer<Citizen> freedListener = citizen -> {
    };

    /**
     * Hears every worker who lost its work because its post went away, once the post is gone.
     */
    public void setFreedListener(java.util.function.Consumer<Citizen> listener) {
        freedListener = listener == null ? citizen -> {
        } : listener;
    }

    public void setChangeListener(Runnable listener) {
        registry.setChangeListener(listener);
        sites.setChangeListener(listener);
        posts.setChangeListener(listener);
        ledger.setChangeListener(listener);
    }

    public CitizenRegistry registry() {
        return registry;
    }

    public SiteRegistry sites() {
        return sites;
    }

    public WorkPosts posts() {
        return posts;
    }

    public ProductionLedger ledger() {
        return ledger;
    }

    public Optional<Citizen> get(UUID id) {
        return registry.get(id);
    }

    /**
     * Settings new work posts start with.
     */
    public void setPostDefaults(int radius, int herdSize) {
        this.newPostRadius = Math.max(1, radius);
        this.newHerdSize = Math.max(2, herdSize);
    }

    /**
     * The rules of work posts of the server: the radii each profession allows.
     */
    public GatheringRules postRules() {
        return postRules;
    }

    public void setPostRules(GatheringRules rules) {
        this.postRules = Objects.requireNonNull(rules, "rules");
    }

    // ---------------------------------------------------------------- citizens

    public void add(Citizen citizen) {
        registry.add(citizen);
    }

    /**
     * Gives a citizen a profession. A workplace that does not suit the new profession is released; with an
     * unknown profession id the citizen keeps the id but does not work.
     */
    public void setProfession(Citizen citizen, String professionId) {
        Objects.requireNonNull(professionId, "professionId");
        String kind = Professions.REGISTRY.byId(professionId).map(Profession::workplaceKind).orElse(null);
        if (citizen.workplace() != null) {
            Optional<WorkPost> post = posts.get(citizen.workplace());
            Optional<Site> site = sites.get(citizen.workplace());
            boolean keeps = post.isPresent() ? post.get().professionId().equals(professionId)
                    : kind != null && site.isPresent() && site.get().kind().equals(kind);
            if (!keeps) {
                releaseWorkplace(citizen);
            }
        }
        citizen.setProfessionId(professionId);
        if (!Professions.worksAtPosts(professionId)) {
            citizen.setWorkStatus(WorkStatus.NONE);
        }
    }

    /**
     * A citizen dies: the record leaves the population, its places become free and the death is written to the
     * kingdom's chronicle.
     */
    public Optional<DeathRecord> die(UUID id, CauseOfDeath cause, String killer, long gameTime, WorldPos place) {
        return die(id, DeathCause.of(cause, killer), gameTime, place);
    }

    /**
     * A citizen dies with the whole cause of its death (who killed, the kind of damage).
     */
    public Optional<DeathRecord> die(UUID id, DeathCause cause, long gameTime, WorldPos place) {
        Optional<Citizen> citizen = registry.get(id);
        if (citizen.isEmpty()) {
            return Optional.empty();
        }
        releaseHome(citizen.get());
        releaseWorkplace(citizen.get());
        return registry.die(id, cause, gameTime, place);
    }

    /**
     * Forgets the citizens, places, posts and production of a kingdom.
     *
     * @return the citizens that were removed
     */
    public List<Citizen> removeKingdom(UUID kingdomId) {
        List<Citizen> removed = registry.removeKingdom(kingdomId);
        sites.removeKingdom(kingdomId);
        posts.removeKingdom(kingdomId);
        ledger.removeKingdom(kingdomId);
        return removed;
    }

    // ---------------------------------------------------------------- places

    /**
     * Records a place of a kingdom. If another place was known at the position, its occupants lose it. A place of
     * kind {@link SiteKinds#WORK_POST} gets a work post with the default settings.
     *
     * @return whether anything changed
     */
    public boolean addSite(WorldPos pos, String kind, UUID kingdomId) {
        Optional<Site> current = sites.get(pos);
        if (current.isPresent() && current.get().kind().equals(kind) && current.get().kingdomId().equals(kingdomId)) {
            return false;
        }
        List<Citizen> freed = current.map(this::evict).orElse(List.of());
        posts.remove(pos);
        sites.put(pos, kind, kingdomId);
        if (kind.equals(SiteKinds.WORK_POST)) {
            posts.put(pos, kingdomId, newPostRadius, newHerdSize);
        }
        freed.forEach(freedListener);
        return true;
    }

    /**
     * Forgets a place, for example a broken bed; its occupants lose it. The workers of a work post that goes away are
     * without work again, keeping what they learned, so that a new post in its place may take anybody at once.
     */
    public Optional<Site> removeSite(WorldPos pos) {
        Optional<Site> site = sites.get(pos);
        List<Citizen> freed = site.map(this::evict).orElse(List.of());
        posts.remove(pos);
        Optional<Site> removed = sites.remove(pos);
        freed.forEach(freedListener);
        return removed;
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
        if (!homeRule.test(citizen.kingdomId(), bed)) {
            return false;
        }
        return assign(citizen, bed, SiteKinds.BED, true);
    }

    /**
     * Sets which beds may become homes of the citizens of a kingdom (the game decides it from the buildings and the beds of
     * players); every bed may when none is set.
     */
    public void setHomeRule(java.util.function.BiPredicate<UUID, WorldPos> rule) {
        homeRule = rule == null ? (kingdom, bed) -> true : rule;
    }

    /**
     * Gives a citizen a workplace that suits its profession: a work post of the profession with a free seat or a
     * free place of the profession's own kind. The old workplace is left.
     *
     * @return whether the citizen now works there
     */
    public boolean assignWorkplace(Citizen citizen, WorldPos workplace) {
        Optional<Profession> profession = Professions.REGISTRY.byId(citizen.professionId());
        if (profession.isEmpty() || !profession.get().needsWorkplace()) {
            return false;
        }
        Optional<WorkPost> post = posts.get(workplace);
        if (post.isPresent()) {
            return assignPost(citizen, post.get());
        }
        if (profession.get().workplaceKind().equals(SiteKinds.WORK_POST)) {
            return false;
        }
        return assign(citizen, workplace, profession.get().workplaceKind(), false);
    }

    /**
     * Puts a citizen on a post in one step: it takes the post's profession and leaves whatever work it had, a seat at another
     * post as well, so that moving a worker from post to post needs no dismissal in between. What it learned stays.
     *
     * @return whether the citizen now works there
     */
    public boolean assignToPost(Citizen citizen, WorkPost post) {
        if (!post.hasProfession() || !post.kingdomId().equals(citizen.kingdomId()) || !citizen.isAlive()
                || registry.get(citizen.id()).isEmpty() || posts.get(post.pos()).orElse(null) != post) {
            return false;
        }
        if (post.workers().contains(citizen.id())) {
            return true;
        }
        if (post.freeSeats() <= 0) {
            return false;
        }
        setProfession(citizen, post.professionId());
        return assignPost(citizen, post);
    }

    private boolean assignPost(Citizen citizen, WorkPost post) {
        if (!citizen.isAlive() || registry.get(citizen.id()).isEmpty() || !post.kingdomId().equals(citizen.kingdomId())
                || !post.professionId().equals(citizen.professionId())) {
            return false;
        }
        if (post.workers().contains(citizen.id())) {
            return true;
        }
        if (post.freeSeats() <= 0) {
            return false;
        }
        releaseWorkplace(citizen);
        citizen.setWorkplace(post.pos());
        post.addWorker(citizen.id());
        return true;
    }

    /**
     * The free workplace for a citizen's profession nearest to {@code near}: a work post of the profession with a
     * free seat, or a free place of the profession's own kind (a composter for a farmer).
     */
    public Optional<WorldPos> nearestFreeWorkplace(Citizen citizen, WorldPos near) {
        Optional<Profession> profession = Professions.REGISTRY.byId(citizen.professionId());
        if (profession.isEmpty() || !profession.get().needsWorkplace()) {
            return Optional.empty();
        }
        Optional<WorldPos> post = posts.nearestWithSeat(citizen.kingdomId(), citizen.professionId(), near).map(WorkPost::pos);
        Optional<WorldPos> site = profession.get().workplaceKind().equals(SiteKinds.WORK_POST) ? Optional.empty()
                : sites.nearestFree(citizen.kingdomId(), profession.get().workplaceKind(), near).map(Site::pos);
        if (post.isEmpty()) {
            return site;
        }
        if (site.isEmpty() || near == null) {
            return post;
        }
        return near.distanceSqr(post.get()) <= near.distanceSqr(site.get()) ? post : site;
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
            posts.get(workplace).ifPresent(post -> post.removeWorker(citizen.id()));
            sites.get(workplace).filter(site -> citizen.id().equals(site.occupant()))
                    .ifPresent(site -> sites.setOccupant(workplace, null));
        }
    }

    /**
     * The occupants of a place that goes away lose it; the workers of a work post lose their profession with it.
     *
     * @return the workers who are without work now
     */
    private List<Citizen> evict(Site site) {
        List<Citizen> freed = new ArrayList<>();
        Optional<WorkPost> post = posts.get(site.pos());
        if (post.isPresent()) {
            for (UUID worker : List.copyOf(post.get().workers())) {
                registry.get(worker).ifPresent(citizen -> {
                    releaseWorkplace(citizen);
                    if (!Professions.UNEMPLOYED.id().equals(citizen.professionId()) && !Professions.isMilitary(citizen.professionId())) {
                        setProfession(citizen, Professions.UNEMPLOYED.id());
                        freed.add(citizen);
                    }
                });
            }
        }
        if (site.occupant() == null) {
            return freed;
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
        return freed;
    }

    // ---------------------------------------------------------------- work posts

    /**
     * Sets the profession of a post; workers of another profession leave it.
     */
    public void setPostProfession(WorkPost post, String professionId) {
        for (UUID worker : List.copyOf(post.workers())) {
            registry.get(worker).filter(citizen -> !citizen.professionId().equals(professionId)).ifPresent(this::releaseWorkplace);
        }
        post.setProfessionId(professionId);
        post.setStatus(!post.hasProfession() ? WorkStatus.NO_PROFESSION
                : post.workers().isEmpty() ? WorkStatus.NO_WORKERS : WorkStatus.WORKING);
    }

    /**
     * Sets the seats of a post; the workers who came last leave it if there are too many.
     */
    public void setPostSeats(WorkPost post, int seats) {
        post.setSeats(seats);
        List<UUID> workers = new ArrayList<>(post.workers());
        for (int i = workers.size() - 1; i >= post.seats(); i--) {
            registry.get(workers.get(i)).ifPresent(this::releaseWorkplace);
        }
    }

    public void setPostRadius(WorkPost post, int radius) {
        post.setRadius(radius);
    }

    public void setPostAnimal(WorkPost post, String animal) {
        post.setAnimal(animal);
    }

    public void setPostHerdSize(WorkPost post, int herdSize) {
        post.setHerdSize(herdSize);
    }

    public void setPostMineGoal(WorkPost post, MineGoal goal) {
        post.setMineGoal(goal);
    }

    public void setPostName(WorkPost post, String name) {
        post.setName(name);
    }

    /**
     * The number of a post among the posts of its kingdom with the same profession, from 1, in the order they were found; it
     * names a post nobody gave a name ("Lumberjack 3").
     */
    public int postNumber(WorkPost post) {
        int number = 0;
        for (WorkPost other : posts.postsOf(post.kingdomId(), post.professionId())) {
            number++;
            if (other == post) {
                return number;
            }
        }
        return Math.max(1, number);
    }

    // ---------------------------------------------------------------- persistence

    /**
     * Restores the places, work posts, population and production of one kingdom from saved data; all or nothing.
     * Homes and workplaces of the citizens that do not match a place are dropped (the citizen finds a new one).
     *
     * @param workPosts posts without workers; each must stand on a place of kind {@link SiteKinds#WORK_POST}
     * @throws IllegalArgumentException if the data conflicts with what is already known
     */
    public void restore(UUID kingdomId, List<Site> places, List<WorkPost> workPosts, List<Citizen> citizens, long lastArrival,
                        List<DeathRecord> chronicle, List<ProductionLedger.Day> production) {
        Set<WorldPos> seen = new HashSet<>();
        Set<WorldPos> postPlaces = new HashSet<>();
        for (Site site : places) {
            if (!site.kingdomId().equals(kingdomId)) {
                throw new IllegalArgumentException("Place " + site.pos() + " belongs to another kingdom");
            }
            if (sites.get(site.pos()).isPresent() || !seen.add(site.pos())) {
                throw new IllegalArgumentException("Two places at " + site.pos());
            }
            if (site.kind().equals(SiteKinds.WORK_POST)) {
                postPlaces.add(site.pos());
            }
        }
        Map<WorldPos, WorkPost> postsByPos = new LinkedHashMap<>();
        for (WorkPost post : workPosts) {
            if (!post.kingdomId().equals(kingdomId)) {
                throw new IllegalArgumentException("Work post " + post.pos() + " belongs to another kingdom");
            }
            if (!postPlaces.contains(post.pos()) || posts.get(post.pos()).isPresent() || postsByPos.put(post.pos(), post) != null) {
                throw new IllegalArgumentException("Work post " + post.pos() + " has no place of its own or is listed twice");
            }
            if (!post.workers().isEmpty()) {
                throw new IllegalArgumentException("Work post " + post.pos() + " comes with workers");
            }
        }
        registry.restore(kingdomId, citizens, lastArrival, chronicle);
        Map<WorldPos, Site> byPos = new HashMap<>();
        for (Site site : places) {
            byPos.put(site.pos(), new Site(site.pos(), site.kind(), kingdomId, null));
        }
        // A post place saved without its settings gets fresh settings.
        for (WorldPos pos : postPlaces) {
            postsByPos.computeIfAbsent(pos, p -> new WorkPost(p, kingdomId, newPostRadius, newHerdSize));
        }
        for (Citizen citizen : citizens) {
            claim(byPos, citizen, citizen.home(), SiteKinds.BED, true);
            WorkPost post = citizen.workplace() == null ? null : postsByPos.get(citizen.workplace());
            if (post != null) {
                if (!post.professionId().equals(citizen.professionId()) || !post.addWorker(citizen.id())) {
                    citizen.setWorkplace(null);
                }
                continue;
            }
            String kind = Professions.REGISTRY.byId(citizen.professionId()).map(Profession::workplaceKind).orElse(null);
            claim(byPos, citizen, citizen.workplace(), kind, false);
        }
        for (Site site : byPos.values()) {
            sites.restore(site);
        }
        for (WorkPost post : postsByPos.values()) {
            posts.restore(post);
        }
        ledger.restore(kingdomId, production);
    }

    private static void claim(Map<WorldPos, Site> byPos, Citizen citizen, WorldPos pos, String kind, boolean home) {
        if (pos == null) {
            return;
        }
        Site site = byPos.get(pos);
        if (site != null && site.kind().equals(kind) && site.isFree() && !SiteKinds.WORK_POST.equals(kind)) {
            byPos.put(pos, site.withOccupant(citizen.id()));
        } else if (home) {
            citizen.setHome(null);
        } else {
            citizen.setWorkplace(null);
        }
    }
}
