package io.github.sekerator.kingdomworld.common.citizen;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.work.CarrierBehavior;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehavior;
import io.github.sekerator.kingdomworld.common.citizen.work.ZoneSurveys;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehaviors;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.production.Production;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.CitizenRegistry;
import io.github.sekerator.kingdomworld.core.citizen.CitizenRules;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.Citizens;
import io.github.sekerator.kingdomworld.core.citizen.Profession;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The needs of the citizens, one kingdom at a time: every {@code simulationIntervalTicks} each kingdom gets one
 * step, spread over the interval so that kingdoms do not all step in the same tick. A step lets every citizen
 * get hungry and eat from the storehouses, find a bed and a workplace, heal or starve, change its mood and, if it
 * has no body, work by formula; then a newcomer may arrive.
 *
 * <p>Citizens with a body heal and get hurt in the world; their record follows the body. Nothing is simulated
 * while the kingdom data is read-only, because nothing could be saved.
 */
public final class CitizenSimulation {

    /** How often the gear of citizens who are gone (a kingdom was dissolved) is forgotten. */
    private static final int GEAR_CLEANUP_TICKS = 6_000;

    private CitizenSimulation() {
    }

    /**
     * Forgets the tools and armor of citizens that no longer exist.
     */
    static void forgetLostGear(MinecraftServer server) {
        var gear = io.github.sekerator.kingdomworld.common.data.KingdomSavedData.get(server).gear();
        Citizens citizens = KingdomWorldApi.state(server).citizens();
        for (UUID id : gear.citizens()) {
            if (citizens.get(id).isEmpty()) {
                gear.takeAll(id);
            }
        }
    }

    /** Citizens all steps under way go through in a tick at most. */
    static final int CITIZENS_PER_TICK = 128;
    /** Time all steps under way take in a tick at most, besides the beginning and end of a step. */
    static final long NANOS_PER_TICK = 2_000_000L;

    /** The steps under way, the oldest first; a step that comes due while the last one is still under way waits for it. */
    private static final io.github.sekerator.kingdomworld.core.citizen.StepQueue<UUID> STEPS = new io.github.sekerator.kingdomworld.core.citizen.StepQueue<>();

    /**
     * Forgets the steps under way, when the server stops.
     */
    static void reset() {
        STEPS.clear();
    }

    /**
     * Called every server tick: begins the steps of the kingdoms whose turn it is and takes every step under way a little
     * further, at most {@value #CITIZENS_PER_TICK} citizens and {@value #NANOS_PER_TICK} nanoseconds a tick for all of them.
     */
    static void tick(MinecraftServer server, long tick) {
        if (KingdomWorldApi.isReadOnly(server)) {
            return;
        }
        int interval = CitizenSettings.rules().simulationInterval();
        if (tick % GEAR_CLEANUP_TICKS == 0) {
            forgetLostGear(server);
        }
        STEPS.startWaiting();
        for (Kingdom kingdom : KingdomWorldApi.kingdoms(server)) {
            if (Math.floorMod(tick + kingdom.id().hashCode(), interval) == 0) {
                UUID id = kingdom.id();
                STEPS.due(id, () -> KingdomStep.begin(server, id));
            }
        }
        STEPS.advance(CITIZENS_PER_TICK, NANOS_PER_TICK, System::nanoTime);
    }

    /**
     * Takes the steps under way further within a budget of citizens and time, as a tick does; for game tests.
     */
    public static int advance(int citizens, long nanos) {
        return STEPS.advance(citizens, nanos, System::nanoTime);
    }

    /**
     * Begins the step of a kingdom, or lets it wait for the one under way, as when it comes due; for game tests.
     */
    public static void beginStep(MinecraftServer server, UUID kingdomId) {
        STEPS.due(kingdomId, () -> KingdomStep.begin(server, kingdomId));
    }

    /**
     * Whether the step of a kingdom is under way.
     */
    public static boolean stepping(UUID kingdomId) {
        return STEPS.running(kingdomId);
    }

    /**
     * Does one whole simulation step for a kingdom at once: its beginning, every citizen and its end.
     */
    public static void step(MinecraftServer server, UUID kingdomId) {
        KingdomStep step = KingdomStep.begin(server, kingdomId);
        if (step == null) {
            return;
        }
        while (step.hasNext()) {
            step.next();
        }
        step.finish();
    }

    /**
     * One step of the citizens of a kingdom, done a few citizens at a time. The beginning plans the work of the workshops and
     * sets aside what the constructions of the kingdom need; then every citizen of the list taken at the beginning gets
     * hungry and eats, finds a bed and a workplace, heals or starves, changes its mood and, without a body, works by formula
     * (a citizen gone meanwhile is passed over, one come meanwhile waits for the next step); the end moves goods, farms the
     * fields of the camps, exports, looks at the posts and lets newcomers come. Done all at once it is the step as it always
     * was.
     */
    static final class KingdomStep implements io.github.sekerator.kingdomworld.core.citizen.StepQueue.Step {
        private final MinecraftServer server;
        private final UUID kingdomId;
        private final List<UUID> ids;
        private final CitizenRules rules;
        private final int interval;
        private final long now;
        private final int recentDeaths;
        private final int unrest;
        private final int infirmaryRoom;
        private final float hunger;
        private int treated;
        private int index;

        private KingdomStep(MinecraftServer server, UUID kingdomId, List<UUID> ids, CitizenRules rules, int interval, long now,
                            int recentDeaths, int unrest, int infirmaryRoom, float hunger) {
            this.server = server;
            this.kingdomId = kingdomId;
            this.ids = ids;
            this.rules = rules;
            this.interval = interval;
            this.now = now;
            this.recentDeaths = recentDeaths;
            this.unrest = unrest;
            this.infirmaryRoom = infirmaryRoom;
            this.hunger = hunger;
        }

        /**
         * Begins a step: the numbers of the step, the plan of the workshops and what the constructions set aside.
         *
         * @return the step, or null when the data is read-only or the kingdom is gone
         */
        static KingdomStep begin(MinecraftServer server, UUID kingdomId) {
            if (KingdomWorldApi.isReadOnly(server)) {
                return null;
            }
            KingdomWorldState state = KingdomWorldApi.state(server);
            if (state.kingdoms().get(kingdomId).isEmpty()) {
                return null;
            }
            CitizenRules rules = CitizenSettings.rules();
            Citizens citizens = state.citizens();
            int interval = rules.simulationInterval();
            long now = KingdomWorldApi.gameTime(server);
            int recentDeaths = citizens.registry().deathsSince(kingdomId, now - CitizenNeeds.RECENT_DEATH_DAYS * CitizenNeeds.TICKS_PER_DAY);
            int unrest = io.github.sekerator.kingdomworld.common.nobility.NobilityWork.moodShift(server, kingdomId);
            int infirmaryRoom = citizens.sites().count(kingdomId, SiteKinds.INFIRMARY) * rules.infirmaryCapacity();
            float hunger = CitizenNeeds.hunger(rules.dailyNutrition(), interval);
            Production.plan(server, kingdomId);
            // Materials of constructions are set aside before workshops look for ingredients and fuel.
            io.github.sekerator.kingdomworld.common.construction.ConstructionWork.refreshReservations(server, kingdomId);
            List<UUID> ids = new ArrayList<>();
            for (Citizen citizen : citizens.registry().citizensOf(kingdomId)) {
                ids.add(citizen.id());
            }
            return new KingdomStep(server, kingdomId, ids, rules, interval, now, recentDeaths, unrest, infirmaryRoom, hunger);
        }

        @Override
        public boolean alive() {
            return !KingdomWorldApi.isReadOnly(server) && KingdomWorldApi.state(server).kingdoms().get(kingdomId).isPresent();
        }

        @Override
        public boolean hasNext() {
            return index < ids.size();
        }

        /**
         * The next citizen of the list, if it is still a citizen of the kingdom.
         */
        @Override
        public void next() {
            UUID id = ids.get(index++);
            KingdomWorldState state = KingdomWorldApi.state(server);
            Citizens citizens = state.citizens();
            Optional<Citizen> found = citizens.get(id);
            if (found.isEmpty() || !kingdomId.equals(found.get().kingdomId())) {
                return;
            }
            Citizen citizen = found.get();
            CitizenEntity body = CitizenBodies.body(citizen.id());
            if (body != null) {
                CitizenBodies.syncBack(server, body, citizen);
            } else {
                // Only a body sets things aside; whatever a citizen without one held is free again.
                Production.release(citizen.id());
            }
            citizen.setFood(citizen.food() - hunger);
            int wanted = CitizenNeeds.nutritionWanted(citizen.food());
            if (wanted > 0) {
                citizen.setFood(citizen.food() + KingdomStorage.eat(server, kingdomId, wanted, citizen.lastPos()));
            }
            // A soldier of a royal army beyond the capital sleeps in the tents of its camp and looks for no bed.
            boolean quartered = citizen.home() == null && Professions.isMilitary(citizen.professionId())
                    && io.github.sekerator.kingdomworld.common.npc.NpcCamps.quartered(server, citizen.id());
            if (!quartered) {
                findPlaces(server, citizens, citizen);
            }

            boolean inInfirmary = citizen.state() == CitizenState.WOUNDED && treated < infirmaryRoom;
            if (inInfirmary) {
                treated++;
            }
            if (!changeHealth(server, citizen, body, inInfirmary, interval, rules)) {
                return;
            }
            // The life of the city: illnesses, the evening in the tavern and sleep; an ill citizen takes a place in an infirmary too.
            if (io.github.sekerator.kingdomworld.common.life.LifeWork.step(server, citizen, body, interval, treated < infirmaryRoom)) {
                treated++;
            }
            int comfort = io.github.sekerator.kingdomworld.common.construction.BuildingEffects.comfort(server, kingdomId, citizen.home());
            int target = CitizenNeeds.targetMood(new CitizenNeeds.MoodInputs(citizen.food(), citizen.home() != null || quartered,
                    citizen.state() == CitizenState.WOUNDED, recentDeaths, citizen.payPercent(), comfort,
                    io.github.sekerator.kingdomworld.common.life.LifeWork.mood(server, citizen), unrest));
            citizen.setMood(CitizenNeeds.nextMood(citizen.mood(), target));
            if (body == null) {
                // An ill citizen gets less done.
                int worked = (int) Math.round(interval * io.github.sekerator.kingdomworld.common.life.LifeWork.workFactor(citizen.id()));
                ProfessionBehaviors.get(citizen.professionId())
                        .ifPresent(behavior -> behavior.workAbstractly(server, citizen, worked));
            } else if (citizen.state() == CitizenState.WOUNDED && Professions.worksAtPosts(citizen.professionId())) {
                PostWork.setStatus(server, citizen, WorkStatus.WOUNDED);
            }
        }

        /**
         * Ends the step: goods carried, the fields of the camps, exports, the posts and newcomers.
         */
        @Override
        public void finish() {
            if (!alive()) {
                return;
            }
            KingdomWorldState state = KingdomWorldApi.state(server);
            Citizens citizens = state.citizens();
            CarrierBehavior.stepAbstractly(server, kingdomId, interval);
            // The soldiers of the camps of a royal army work their fields.
            io.github.sekerator.kingdomworld.common.npc.NpcCamps.farm(server, kingdomId, interval);
            io.github.sekerator.kingdomworld.common.trade.TradeWork.exportStep(server, kingdomId, interval);
            for (WorkPost post : citizens.posts().postsOf(kingdomId)) {
                PostWork.refreshStatus(server, post);
                boolean workshop = Professions.usesStations(post.professionId());
                // Workshops are looked at every step: workstations come and go.
                if (post.hasProfession() && (post.yield().isEmpty() || workshop) && !ZoneSurveys.pending(post.pos())) {
                    ZoneSurveys.request(post.pos());
                }
            }
            grow(server, state, kingdomId, rules, now);
        }
    }

    /**
     * Applies hunger or healing to the health of a citizen.
     *
     * @return whether the citizen is still alive
     */
    private static boolean changeHealth(MinecraftServer server, Citizen citizen, CitizenEntity body, boolean inInfirmary,
                                        int interval, CitizenRules rules) {
        float change = CitizenNeeds.healthChange(citizen.food(), inInfirmary, interval);
        if (body != null) {
            // A body heals in the world; only starvation hurts it from here.
            if (change < 0.0f) {
                float damage = rules.starvationCanKill() ? -change : Math.min(-change, body.getHealth() - 1.0f);
                if (damage > 0.0f) {
                    body.hurt(body.damageSources().starve(), damage);
                }
            }
            return body.isAlive();
        }
        if (change > 0.0f) {
            // A soldier with more health heals by the same share of it as anybody.
            change *= citizen.maxHealth() / Citizen.MAX_HEALTH;
        }
        float health = citizen.health() + change;
        if (health <= 0.0f && rules.starvationCanKill()) {
            CitizenLife.die(server, citizen.id(), CauseOfDeath.STARVATION, "", citizen.lastPos());
            return false;
        }
        if (change < 0.0f && !rules.starvationCanKill()) {
            // Hunger stops at one point of health.
            health = Math.max(health, Math.min(citizen.health(), 1.0f));
        }
        citizen.setHealth(health, rules.woundedFraction());
        return true;
    }

    /**
     * Gives a homeless citizen the nearest free bed and a citizen without workplace the nearest free workplace of
     * its profession.
     */
    static void findPlaces(MinecraftServer server, Citizens citizens, Citizen citizen) {
        if (citizen.home() == null) {
            // A bed in a house comes first; beds in houses whose role rests are not offered. In a kingdom of the world a
            // soldier takes a bunk of a barracks first, and anybody else only as the last bed.
            io.github.sekerator.kingdomworld.common.construction.BuildingEffects.freeBed(server, citizen.kingdomId(), citizen.lastPos(),
                            Professions.isMilitary(citizen.professionId()))
                    .ifPresent(bed -> citizens.assignHome(citizen, bed.pos()));
        }
        Optional<Profession> profession = Professions.REGISTRY.byId(citizen.professionId());
        if (profession.isPresent() && profession.get().needsWorkplace() && citizen.workplace() == null) {
            WorldPos near = citizen.home() != null ? citizen.home() : citizen.lastPos();
            citizens.nearestFreeWorkplace(citizen, near).ifPresent(workplace -> {
                if (citizens.assignWorkplace(citizen, workplace)) {
                    survey(server, citizen);
                }
            });
            if (citizen.workplace() == null) {
                citizen.setWorkStatus(WorkStatus.NO_WORKPLACE);
            }
        }
    }

    /**
     * Gives every citizen of a kingdom its bed and workplace now and lets each look at its workplace while the land is
     * loaded, instead of waiting for the next step: a kingdom that just appeared starts work at once, far from players too.
     */
    public static void settle(MinecraftServer server, UUID kingdomId) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        Citizens citizens = state.citizens();
        for (Citizen citizen : List.copyOf(citizens.registry().citizensOf(kingdomId))) {
            findPlaces(server, citizens, citizen);
            survey(server, citizen);
        }
        for (WorkPost post : citizens.posts().postsOf(kingdomId)) {
            if (post.hasProfession() && post.yield().isEmpty() && !ZoneSurveys.pending(post.pos())) {
                ZoneSurveys.request(post.pos());
            }
        }
    }

    /**
     * Lets the profession look at a loaded workplace, so that the abstract work has something to go on.
     */
    static void survey(MinecraftServer server, Citizen citizen) {
        WorldPos workplace = citizen.workplace();
        Optional<ProfessionBehavior> behavior = ProfessionBehaviors.get(citizen.professionId());
        if (workplace == null || behavior.isEmpty()) {
            return;
        }
        Optional<ServerLevel> level = Dimensions.key(workplace.dimension()).map(server::getLevel);
        if (level.isPresent() && level.get().isLoaded(new BlockPos(workplace.x(), workplace.y(), workplace.z()))) {
            behavior.get().survey(level.get(), citizen);
        }
    }

    /**
     * A newcomer arrives at the throne if there is a free bed, enough food and the last arrival is long enough ago.
     */
    private static void grow(MinecraftServer server, KingdomWorldState state, UUID kingdomId, CitizenRules rules, long now) {
        Optional<Territory> territory = state.territory().territory(kingdomId);
        if (territory.isEmpty()) {
            return;
        }
        CitizenRegistry registry = state.citizens().registry();
        List<Citizen> people = registry.citizensOf(kingdomId);
        int[] freeBeds = io.github.sekerator.kingdomworld.common.construction.BuildingEffects.freeBeds(server, kingdomId);
        long last = registry.lastArrival(kingdomId);
        long since = last == CitizenRegistry.NEVER ? Long.MAX_VALUE : now - last;
        CitizenNeeds.GrowthCheck check = CitizenNeeds.checkGrowth(people.size() - royalArmy(server, kingdomId), people.size(),
                freeBeds[0], freeBeds[1],
                KingdomStorage.totalNutrition(server, kingdomId), since, averageMood(people), rules);
        if (check == CitizenNeeds.GrowthCheck.READY) {
            Capital capital = territory.get().capital();
            CitizenLife.arrive(server, kingdomId, new WorldPos(capital.dimension(), capital.x(), capital.y(), capital.z()), true);
        }
    }

    /**
     * The soldiers of the royal army of a kingdom of the world: they come from abroad and do not count against the most
     * citizens a kingdom has.
     */
    private static int royalArmy(MinecraftServer server, UUID kingdomId) {
        if (!io.github.sekerator.kingdomworld.common.npc.NpcWork.isNpc(server, kingdomId)) {
            return 0;
        }
        return io.github.sekerator.kingdomworld.common.army.ArmyWork.state(server)
                .branchOf(kingdomId, io.github.sekerator.kingdomworld.core.army.Branch.ARMY).size();
    }

    /**
     * Average mood of the citizens, neutral for an empty kingdom.
     */
    public static int averageMood(List<Citizen> people) {
        if (people.isEmpty()) {
            return CitizenNeeds.NEUTRAL_MOOD;
        }
        long sum = 0;
        for (Citizen citizen : people) {
            sum += citizen.mood();
        }
        return (int) Math.round(sum / (double) people.size());
    }

    /**
     * Why newcomers do or do not arrive right now, for the throne screen and commands.
     */
    public static CitizenNeeds.GrowthCheck growthCheck(MinecraftServer server, UUID kingdomId) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        CitizenRegistry registry = state.citizens().registry();
        List<Citizen> people = registry.citizensOf(kingdomId);
        int[] freeBeds = io.github.sekerator.kingdomworld.common.construction.BuildingEffects.freeBeds(server, kingdomId);
        long last = registry.lastArrival(kingdomId);
        long since = last == CitizenRegistry.NEVER ? Long.MAX_VALUE : KingdomWorldApi.gameTime(server) - last;
        return CitizenNeeds.checkGrowth(people.size() - royalArmy(server, kingdomId), people.size(), freeBeds[0], freeBeds[1],
                KingdomStorage.totalNutrition(server, kingdomId), since,
                averageMood(people), CitizenSettings.rules());
    }
}
