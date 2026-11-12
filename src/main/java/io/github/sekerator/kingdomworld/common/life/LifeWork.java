package io.github.sekerator.kingdomworld.common.life;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSettings;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehaviors;
import io.github.sekerator.kingdomworld.common.construction.BuildingEffects;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.road.CityWalls;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.BuildingRoles;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.life.DaySchedule;
import io.github.sekerator.kingdomworld.core.life.Festivals;
import io.github.sekerator.kingdomworld.core.life.LifeRules;
import io.github.sekerator.kingdomworld.core.life.LifeState;
import io.github.sekerator.kingdomworld.core.life.Sickness;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The life of cities on the server: the day of each citizen, evenings in the taverns, light illnesses, festivals and the
 * mood they give. The simulation of the citizens calls in once per step for every citizen ({@link #step}); bodies near players
 * follow the same day through {@link DayGoal}.
 */
public final class LifeWork {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final TagKey<Item> TAVERN_FOOD = TagKey.create(Registries.ITEM, KingdomWorld.id("tavern_food"));
    public static final TagKey<Item> TAVERN_DRINK = TagKey.create(Registries.ITEM, KingdomWorld.id("tavern_drink"));
    /** How far from home a tavern of the citizen's city may be. */
    public static final int TAVERN_REACH = 96;

    /** Ill citizens, mirrored from the saved data so that bodies can ask every tick. */
    private static final Set<UUID> SICK = new HashSet<>();
    /** Guests each tavern served on its last evening: building id to {day, guests}. */
    private static final Map<Long, long[]> GUESTS = new HashMap<>();
    private static java.util.function.DoubleSupplier random = Math::random;

    private LifeWork() {
    }

    public static LifeState state(MinecraftServer server) {
        return LifeSavedData.get(server).state();
    }

    public static boolean isReadOnly(MinecraftServer server) {
        return LifeSavedData.get(server).isReadOnly() || KingdomWorldApi.isReadOnly(server);
    }

    static void reset() {
        SICK.clear();
        GUESTS.clear();
        random = Math::random;
    }

    /**
     * Fills the mirror of the ill citizens after loading.
     */
    static void loaded(MinecraftServer server) {
        SICK.clear();
        SICK.addAll(state(server).allSick().keySet());
    }

    /**
     * Replaces the dice of illnesses, for game tests; {@code null} restores chance.
     */
    public static void setRandomForCheck(@Nullable java.util.function.DoubleSupplier supplier) {
        random = supplier == null ? Math::random : supplier;
    }

    public static boolean isSick(@Nullable UUID citizen) {
        return citizen != null && SICK.contains(citizen);
    }

    public static long today(MinecraftServer server) {
        return DaySchedule.day(KingdomWorldApi.gameTime(server));
    }

    /**
     * The time of the day the citizens live by: the day of the overworld.
     */
    public static long dayTime(MinecraftServer server) {
        return server.overworld().getDayTime();
    }

    // ---------------------------------------------------------------- the day of a citizen

    /**
     * The part of the day a citizen is in now.
     */
    public static DaySchedule.Phase phase(MinecraftServer server, UUID citizen) {
        LifeRules rules = LifeSettings.rules();
        return DaySchedule.phase(dayTime(server), DaySchedule.shift(citizen, rules.spreadTicks()), rules);
    }

    /**
     * Whether a citizen of the profession keeps watch instead of sleeping: soldiers and adventurers.
     */
    public static boolean keepsWatch(String professionId) {
        return ProfessionBehaviors.get(professionId).map(behavior -> behavior.fights()).orElse(false);
    }

    /**
     * Whether a citizen rests from work for a festival now.
     */
    public static boolean festivalRest(MinecraftServer server, UUID kingdomId) {
        return Festivals.resting(state(server).festival(kingdomId).festivalDay(), KingdomWorldApi.gameTime(server));
    }

    /**
     * Whether a body works now: in the morning and by day, unless a festival lets the city rest; soldiers and adventurers
     * are always on duty.
     */
    public static boolean works(CitizenEntity body) {
        MinecraftServer server = body.getServer();
        UUID id = body.citizenId();
        if (server == null || id == null || keepsWatch(body.professionId())) {
            return true;
        }
        if (body.kingdomId() != null && festivalRest(server, body.kingdomId())) {
            return false;
        }
        DaySchedule.Phase phase = phase(server, id);
        // The innkeeper keeps the tavern open through the evening.
        return phase == DaySchedule.Phase.MORNING || phase == DaySchedule.Phase.DAY
                || phase == DaySchedule.Phase.EVENING && InnkeeperBehavior.INNKEEPER.id().equals(body.professionId());
    }

    // ---------------------------------------------------------------- beds of players

    /**
     * Whether a bed is the point of return of a player: nobody's home, whoever placed it.
     */
    public static boolean playerBed(MinecraftServer server, WorldPos bed) {
        return state(server).isPlayerBed(bed);
    }

    /**
     * A player's point of return changed: a bed becomes theirs (a citizen living in it moves out at the next step), and the
     * bed they had before is free for citizens again.
     */
    public static void spawnSet(MinecraftServer server, UUID player, @Nullable net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                                @Nullable net.minecraft.core.BlockPos pos) {
        if (isReadOnly(server)) {
            return;
        }
        WorldPos bed = null;
        net.minecraft.server.level.ServerLevel level = dimension == null ? null : server.getLevel(dimension);
        if (level != null && pos != null && level.isLoaded(pos)
                && level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            net.minecraft.core.BlockPos head = state.getValue(net.minecraft.world.level.block.BedBlock.PART)
                    == net.minecraft.world.level.block.state.properties.BedPart.HEAD ? pos
                    : pos.relative(state.getValue(net.minecraft.world.level.block.BedBlock.FACING));
            bed = new WorldPos(io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(level), head.getX(), head.getY(), head.getZ());
        }
        state(server).setPlayerBed(player, bed);
    }

    // ---------------------------------------------------------------- taverns

    /**
     * A working tavern of a kingdom nearest to a place, within {@link #TAVERN_REACH}.
     */
    public static Optional<BuildingEffects.Effect> tavernNear(MinecraftServer server, UUID kingdomId, @Nullable WorldPos near) {
        if (near == null) {
            return Optional.empty();
        }
        BuildingEffects.Effect best = null;
        double bestDistance = (double) TAVERN_REACH * TAVERN_REACH;
        for (BuildingEffects.Effect effect : BuildingEffects.working(server, kingdomId, BuildingRoles.TAVERN)) {
            WorldPos center = center(effect.building());
            if (!center.dimension().equals(near.dimension())) {
                continue;
            }
            double distance = center.distanceSqr(near);
            if (distance <= bestDistance) {
                best = effect;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    public static Optional<BuildingEffects.Effect> tavern(MinecraftServer server, UUID kingdomId, long buildingId) {
        for (BuildingEffects.Effect effect : BuildingEffects.working(server, kingdomId, BuildingRoles.TAVERN)) {
            if (effect.building().id() == buildingId) {
                return Optional.of(effect);
            }
        }
        return Optional.empty();
    }

    /**
     * The middle of the floor of a building.
     */
    public static WorldPos center(Building building) {
        var box = building.box();
        return new WorldPos(building.dimension(), (box.minX() + box.maxX()) / 2, box.minY() + 1, (box.minZ() + box.maxZ()) / 2);
    }

    /**
     * Whether a tavern is open: a healthy innkeeper works at a post inside it.
     */
    public static boolean open(MinecraftServer server, Building tavern) {
        return InnkeeperBehavior.keeps(server, tavern);
    }

    /**
     * Whether an item may be served in a tavern.
     */
    public static boolean served(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(TAVERN_FOOD) || stack.is(TAVERN_DRINK));
    }

    /**
     * A citizen spends the evening in a tavern: if the tavern is open, has room for one more guest tonight and the storehouses
     * hold something to serve, the citizen eats or drinks it (its food is what it eats tonight) and is in a better mood for
     * some days.
     *
     * @return whether the evening was a good one
     */
    public static boolean enjoyTavern(MinecraftServer server, Citizen citizen, BuildingEffects.Effect tavern) {
        if (isReadOnly(server) || !open(server, tavern.building())) {
            return false;
        }
        LifeState life = state(server);
        long today = today(server);
        if (life.tavernDay(citizen.id()) == today) {
            return true;
        }
        long[] served = GUESTS.computeIfAbsent(tavern.building().id(), id -> new long[]{today, 0});
        if (served[0] != today) {
            served[0] = today;
            served[1] = 0;
        }
        int guests = (int) Math.round(tavern.param(BuildingRoles.GUESTS));
        if (served[1] >= Math.max(1, guests)) {
            return false;
        }
        // A drink first, food if there is none.
        // Plain things only: nothing named or enchanted is eaten.
        List<ItemStack> taken = KingdomStorage.take(server, citizen.kingdomId(), stack -> stack.is(TAVERN_DRINK)
                && io.github.sekerator.kingdomworld.common.trade.GoodsStacks.plain(stack), 1, center(tavern.building()));
        if (taken.isEmpty()) {
            taken = KingdomStorage.take(server, citizen.kingdomId(), stack -> stack.is(TAVERN_FOOD)
                    && io.github.sekerator.kingdomworld.common.trade.GoodsStacks.plain(stack), 1, center(tavern.building()));
        }
        if (taken.isEmpty()) {
            return false;
        }
        int nutrition = 0;
        for (ItemStack stack : taken) {
            nutrition += KingdomStorage.nutrition(stack) * stack.getCount();
        }
        citizen.setFood(citizen.food() + nutrition);
        served[1]++;
        life.visitedTavern(citizen.id(), today);
        return true;
    }

    /**
     * Guests a tavern served tonight, for the citizens tab.
     */
    public static int guestsTonight(long buildingId, long today) {
        long[] served = GUESTS.get(buildingId);
        return served == null || served[0] != today ? 0 : (int) served[1];
    }

    // ---------------------------------------------------------------- one step of the simulation

    /**
     * One step of the life of a citizen, before its mood: it may fall ill or get better, spend the evening in the tavern when
     * it has no body, and sleep at night.
     *
     * @param infirmaryFree whether an infirmary of the kingdom has room for one more patient
     * @return whether the citizen took a place in an infirmary
     */
    public static boolean step(MinecraftServer server, Citizen citizen, @Nullable CitizenEntity body, int interval, boolean infirmaryFree) {
        if (isReadOnly(server)) {
            return false;
        }
        LifeRules rules = LifeSettings.rules();
        LifeState life = state(server);
        boolean usedInfirmary = false;
        if (citizen.home() != null && io.github.sekerator.kingdomworld.common.construction.BuildingEffects.homeLost(server, citizen.kingdomId(),
                citizen.home())) {
            // A room of a tavern or the bed of a player is nobody's home: the citizen looks for another bed.
            KingdomWorldApi.state(server).citizens().releaseHome(citizen);
        }
        if (life.isSick(citizen.id())) {
            boolean infirmary = body != null ? io.github.sekerator.kingdomworld.common.citizen.CitizenCare.isInInfirmary(body) : infirmaryFree;
            usedInfirmary = infirmary && body == null;
            if (life.recover(citizen.id(), Sickness.recovery(rules, infirmary, interval))) {
                SICK.remove(citizen.id());
            }
        } else {
            double chance = Sickness.stepChance(Sickness.dailyChance(rules, citizen.food() < CitizenNeeds.HUNGRY_BELOW, citizen.home() == null,
                    citizen.state() == CitizenState.WOUNDED), interval);
            if (chance > 0 && random.getAsDouble() < chance) {
                fallIll(server, citizen.id());
            }
        }
        if (body == null) {
            DaySchedule.Phase phase = phase(server, citizen.id());
            long today = today(server);
            if (phase == DaySchedule.Phase.EVENING && life.tavernDay(citizen.id()) != today && DaySchedule.wantsTavern(citizen.id(), today, rules)) {
                WorldPos home = citizen.home() != null ? citizen.home() : citizen.lastPos();
                tavernNear(server, citizen.kingdomId(), home).ifPresent(tavern -> enjoyTavern(server, citizen, tavern));
            }
            if (phase == DaySchedule.Phase.NIGHT && citizen.home() != null && !keepsWatch(citizen.professionId())
                    && citizen.food() >= CitizenNeeds.HUNGRY_BELOW && citizen.health() < citizen.maxHealth()) {
                // Sleep heals a little more than the day, by the same share of the full health for everybody.
                citizen.setHealth(citizen.health() + 0.5f * interval / CitizenNeeds.REFERENCE_STEP_TICKS * citizen.maxHealth() / Citizen.MAX_HEALTH,
                        CitizenSettings.rules().woundedFraction());
            }
        }
        return usedInfirmary;
    }

    public static void fallIll(MinecraftServer server, UUID citizen) {
        state(server).fallIll(citizen, KingdomWorldApi.gameTime(server));
        SICK.add(citizen);
    }

    public static void heal(MinecraftServer server, UUID citizen) {
        state(server).heal(citizen);
        SICK.remove(citizen);
    }

    /**
     * How much slower an ill citizen works.
     */
    public static double workFactor(UUID citizen) {
        return Sickness.workFactor(LifeSettings.rules(), isSick(citizen));
    }

    /**
     * Mood the life of the city gives a citizen today: an evening in the tavern and a festival add, an illness takes.
     */
    public static int mood(MinecraftServer server, Citizen citizen) {
        LifeRules rules = LifeSettings.rules();
        LifeState life = state(server);
        long today = today(server);
        LifeState.Festival festival = life.festival(citizen.kingdomId());
        int mood = life.tavernMood(citizen.id(), today, rules) + Festivals.mood(festival.festivalDay(), festival.treated(), today, rules);
        if (life.isSick(citizen.id())) {
            mood -= rules.sickMood();
        }
        return mood;
    }

    /**
     * A citizen died or left the kingdom.
     */
    public static void forget(MinecraftServer server, UUID citizen) {
        if (!isReadOnly(server)) {
            state(server).forgetCitizen(citizen);
        }
        SICK.remove(citizen);
    }

    // ---------------------------------------------------------------- festivals

    /**
     * The king (or a member with the right to manage citizens) calls a festival for today: the treasury pays, the storehouses
     * give the treat, the citizens rest in the afternoon and are glad for some days.
     *
     * @param actor the player, or {@code null} for an operator
     */
    public static KingdomActions.Feedback callFestival(MinecraftServer server, @Nullable UUID actor, UUID kingdomId) {
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdom(server, kingdomId);
        if (kingdom.isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_KINGDOM);
        }
        if (isReadOnly(server)) {
            return KingdomActions.Feedback.error(KingdomError.READ_ONLY);
        }
        if (actor != null && !KingdomWorldApi.state(server).membership().hasPermission(actor, kingdomId, Permission.MANAGE_CITIZENS)) {
            return KingdomActions.Feedback.error(KingdomError.NO_PERMISSION);
        }
        LifeRules rules = LifeSettings.rules();
        LifeState life = state(server);
        long today = today(server);
        LifeState.Festival festival = life.festival(kingdomId);
        if (Festivals.refusal(festival.called(), today, rules) != null || festival.festivalDay() == today) {
            return KingdomActions.Feedback.fail(LifeTexts.KEY + "festival.too_soon", LifeTexts.days(Math.max(1,
                    Festivals.daysUntilNext(festival.called(), today, rules))));
        }
        // The royal army of a kingdom of the world keeps to its camps and takes no part in the feast of the town.
        int people = io.github.sekerator.kingdomworld.common.npc.NpcCamps.townsfolk(server, kingdomId);
        long cost = Festivals.cost(people, rules);
        var economy = KingdomWorldApi.state(server).economy();
        long day = TreasuryActions.day(server);
        if (cost > 0) {
            Outcome check = economy.checkKingdomPays(kingdomId, cost, null);
            if (!check.ok()) {
                return KingdomActions.Feedback.error(check.error());
            }
        }
        if (cost > 0 && !economy.kingdomPays(kingdomId, cost, null, LedgerCategory.LIFE, day).ok()) {
            return KingdomActions.Feedback.error(KingdomError.NOT_ENOUGH_MONEY);
        }
        int treat = Festivals.treat(people, rules);
        int served = treat > 0 ? KingdomStorage.eat(server, kingdomId, treat, null) : 0;
        boolean treated = served >= treat;
        hold(server, kingdomId, new LifeState.Festival(today, today, treated, festival.foundingYear()), "festival");
        LOGGER.info("{} holds a festival: {} paid, {} of {} food points of treat", kingdom.get().name(), cost, served, treat);
        return KingdomActions.Feedback.ok(LifeTexts.KEY + (treated ? "festival.called" : "festival.called_poor"),
                io.github.sekerator.kingdomworld.common.economy.MoneyTexts.amount(cost,
                        io.github.sekerator.kingdomworld.common.trade.Purses.units(server, kingdomId)));
    }

    private static void hold(MinecraftServer server, UUID kingdomId, LifeState.Festival festival, String kind) {
        LifeState life = state(server);
        life.setFestival(kingdomId, festival);
        life.addEvent(kingdomId, new LifeState.Event(kind, festival.festivalDay(), ""));
        Component message = Component.translatable(LifeTexts.KEY + "festival.announce." + kind,
                KingdomWorldApi.kingdom(server, kingdomId).map(KingdomActions::kingdomName).orElse(Component.literal("?")));
        tellMembers(server, kingdomId, message);
    }

    /**
     * Tells every member of a kingdom online, and every player on its land.
     */
    static void tellMembers(MinecraftServer server, UUID kingdomId, Component message) {
        var membership = KingdomWorldApi.state(server).membership();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean member = membership.isMember(player.getUUID(), kingdomId);
            boolean here = kingdomId.equals(KingdomWorldApi.ownerAt(player.level(), player.blockPosition()));
            if (member || here) {
                player.sendSystemMessage(message);
            }
        }
    }

    /**
     * Once a year the day a kingdom was founded is a festival by itself: free, with whatever treat the storehouses give.
     */
    static void foundingDays(MinecraftServer server) {
        LifeRules rules = LifeSettings.rules();
        LifeState life = state(server);
        long today = today(server);
        for (Kingdom kingdom : KingdomWorldApi.kingdoms(server)) {
            if (!Festivals.foundingDay(kingdom.createdGameTime(), today, rules)) {
                continue;
            }
            long year = Festivals.year(kingdom.createdGameTime(), KingdomWorldApi.gameTime(server), rules);
            LifeState.Festival festival = life.festival(kingdom.id());
            if (festival.foundingYear() >= year) {
                continue;
            }
            int people = KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdom.id()).size();
            int treat = Festivals.treat(people, rules);
            int served = treat > 0 ? KingdomStorage.eat(server, kingdom.id(), treat, null) : 0;
            hold(server, kingdom.id(), new LifeState.Festival(festival.called(), today, served >= treat, year), "founding");
            LOGGER.info("{} celebrates the day it was founded, year {}", kingdom.name(), year);
        }
    }

    /**
     * Forgets the life of kingdoms that are gone and of citizens who are gone.
     */
    static void cleanUp(MinecraftServer server) {
        if (isReadOnly(server)) {
            return;
        }
        LifeState life = state(server);
        var citizens = KingdomWorldApi.state(server).citizens();
        for (UUID id : new ArrayList<>(life.knownCitizens())) {
            if (citizens.get(id).isEmpty()) {
                life.forgetCitizen(id);
                SICK.remove(id);
            }
        }
        for (UUID kingdom : new ArrayList<>(life.kingdoms())) {
            if (KingdomWorldApi.kingdom(server, kingdom).isEmpty()) {
                life.forgetKingdom(kingdom);
            }
        }
        var camps = io.github.sekerator.kingdomworld.common.road.RoadWork.state(server);
        life.keepMarks(id -> camps.camp(id).filter(camp -> !camp.cleared()).isPresent());
    }

    /**
     * The city a place belongs to: the capital or a city within the area of its wall.
     */
    public static Optional<CityWalls.Place> cityOf(MinecraftServer server, UUID kingdomId, @Nullable WorldPos pos) {
        if (pos == null) {
            return Optional.empty();
        }
        CityWalls.Place best = null;
        double bestDistance = Double.MAX_VALUE;
        for (CityWalls.Place place : CityWalls.places(server)) {
            if (!place.kingdomId().equals(kingdomId) || !place.dimension().equals(pos.dimension())) {
                continue;
            }
            int area = CityWalls.area(server, place);
            if (Math.abs(place.x() - pos.x()) > area || Math.abs(place.z() - pos.z()) > area) {
                continue;
            }
            double distance = Math.pow(place.x() - pos.x(), 2) + Math.pow(place.z() - pos.z(), 2);
            if (distance < bestDistance) {
                best = place;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }
}
