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

    /**
     * Called every server tick; steps the kingdoms whose turn it is.
     */
    static void tick(MinecraftServer server, long tick) {
        if (KingdomWorldApi.isReadOnly(server)) {
            return;
        }
        int interval = CitizenSettings.rules().simulationInterval();
        if (tick % GEAR_CLEANUP_TICKS == 0) {
            forgetLostGear(server);
        }
        for (Kingdom kingdom : KingdomWorldApi.kingdoms(server)) {
            if (Math.floorMod(tick + kingdom.id().hashCode(), interval) == 0) {
                step(server, kingdom.id());
            }
        }
    }

    /**
     * Does one simulation step for a kingdom.
     */
    public static void step(MinecraftServer server, UUID kingdomId) {
        if (KingdomWorldApi.isReadOnly(server)) {
            return;
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        if (state.kingdoms().get(kingdomId).isEmpty()) {
            return;
        }
        CitizenRules rules = CitizenSettings.rules();
        Citizens citizens = state.citizens();
        int interval = rules.simulationInterval();
        long now = KingdomWorldApi.gameTime(server);
        int recentDeaths = citizens.registry().deathsSince(kingdomId, now - CitizenNeeds.RECENT_DEATH_DAYS * CitizenNeeds.TICKS_PER_DAY);
        int infirmaryRoom = citizens.sites().count(kingdomId, SiteKinds.INFIRMARY) * rules.infirmaryCapacity();
        int treated = 0;
        float hunger = CitizenNeeds.hunger(rules.dailyNutrition(), interval);

        Production.plan(server, kingdomId);
        // Materials of constructions are set aside before workshops look for ingredients and fuel.
        io.github.sekerator.kingdomworld.common.construction.ConstructionWork.refreshReservations(server);
        for (Citizen citizen : citizens.registry().citizensOf(kingdomId)) {
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
            findPlaces(server, citizens, citizen);

            boolean inInfirmary = citizen.state() == CitizenState.WOUNDED && treated < infirmaryRoom;
            if (inInfirmary) {
                treated++;
            }
            if (!changeHealth(server, citizen, body, inInfirmary, interval, rules)) {
                continue;
            }
            int target = CitizenNeeds.targetMood(new CitizenNeeds.MoodInputs(citizen.food(), citizen.home() != null,
                    citizen.state() == CitizenState.WOUNDED, recentDeaths, citizen.payPercent()));
            citizen.setMood(CitizenNeeds.nextMood(citizen.mood(), target));
            if (body == null) {
                ProfessionBehaviors.get(citizen.professionId())
                        .ifPresent(behavior -> behavior.workAbstractly(server, citizen, interval));
            } else if (citizen.state() == CitizenState.WOUNDED && Professions.worksAtPosts(citizen.professionId())) {
                PostWork.setStatus(server, citizen, WorkStatus.WOUNDED);
            }
        }
        CarrierBehavior.stepAbstractly(server, kingdomId, interval);
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
            citizens.sites().nearestFree(citizen.kingdomId(), SiteKinds.BED, citizen.lastPos())
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
        int freeBeds = state.citizens().sites().count(kingdomId, SiteKinds.BED)
                - state.citizens().sites().occupied(kingdomId, SiteKinds.BED);
        long last = registry.lastArrival(kingdomId);
        long since = last == CitizenRegistry.NEVER ? Long.MAX_VALUE : now - last;
        CitizenNeeds.GrowthCheck check = CitizenNeeds.checkGrowth(people.size(), freeBeds,
                KingdomStorage.totalNutrition(server, kingdomId), since, averageMood(people), rules);
        if (check == CitizenNeeds.GrowthCheck.READY) {
            Capital capital = territory.get().capital();
            CitizenLife.arrive(server, kingdomId, new WorldPos(capital.dimension(), capital.x(), capital.y(), capital.z()), true);
        }
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
        int freeBeds = state.citizens().sites().count(kingdomId, SiteKinds.BED)
                - state.citizens().sites().occupied(kingdomId, SiteKinds.BED);
        long last = registry.lastArrival(kingdomId);
        long since = last == CitizenRegistry.NEVER ? Long.MAX_VALUE : KingdomWorldApi.gameTime(server) - last;
        return CitizenNeeds.checkGrowth(people.size(), freeBeds, KingdomStorage.totalNutrition(server, kingdomId), since,
                averageMood(people), CitizenSettings.rules());
    }
}
