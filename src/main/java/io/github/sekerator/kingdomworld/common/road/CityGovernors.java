package io.github.sekerator.kingdomworld.common.road;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.citizen.FolkSettings;
import io.github.sekerator.kingdomworld.common.construction.BuildingEffects;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplate;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplates;
import io.github.sekerator.kingdomworld.common.construction.CityCrews;
import io.github.sekerator.kingdomworld.common.construction.CityPlots;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.construction.KingdomLooks;
import io.github.sekerator.kingdomworld.common.construction.RemoteWorks;
import io.github.sekerator.kingdomworld.common.construction.TemplateMaterials;
import io.github.sekerator.kingdomworld.common.economy.MaterialWorth;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomStyles;
import io.github.sekerator.kingdomworld.common.nobility.NobilityActions;
import io.github.sekerator.kingdomworld.common.nobility.NobilityTexts;
import io.github.sekerator.kingdomworld.common.nobility.NobilityWork;
import io.github.sekerator.kingdomworld.common.npc.NpcSettings;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.npc.RulerPlaces;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNames;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.Site;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.Commission;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.economy.TreasuryJournal;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.nobility.Fief;
import io.github.sekerator.kingdomworld.core.nobility.Grant;
import io.github.sekerator.kingdomworld.core.nobility.NobilityState;
import io.github.sekerator.kingdomworld.core.nobility.NobleHouse;
import io.github.sekerator.kingdomworld.core.nobility.Note;
import io.github.sekerator.kingdomworld.core.nobility.Title;
import io.github.sekerator.kingdomworld.core.npc.RulerCharacter;
import io.github.sekerator.kingdomworld.core.npc.RulerMind;
import io.github.sekerator.kingdomworld.core.npc.RulerRules;
import io.github.sekerator.kingdomworld.core.npc.RulerView;
import io.github.sekerator.kingdomworld.core.road.CityCharter;
import io.github.sekerator.kingdomworld.core.road.CityFolk;
import io.github.sekerator.kingdomworld.core.road.CityGrowth;
import io.github.sekerator.kingdomworld.core.road.CityLevel;
import io.github.sekerator.kingdomworld.core.road.CityOffice;
import io.github.sekerator.kingdomworld.core.road.GovernorNote;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.ToLongFunction;

/**
 * The governors of the cities of kingdoms of players: a house of the nobility that holds a city, else the mayor the king made of
 * one of its citizens. A city with a governor builds by itself (see {@link CityGrowth}): one city a second in turn, at most once
 * a game day on which somebody of the kingdom played, by orders of the king from afar with its origin the governor, in the
 * style of the kingdom and on the land as it grew, so that no block of a player is ever taken. It pays from the storehouses
 * and, with what the king gives the governors a day, buys what its buildings lack; it never lets the treasury fall below its
 * reserve. A governor who dies, leaves or is dismissed stops: its orders not laid out go, its constructions stay ordinary
 * constructions. Whether a mayor may still govern is looked at whenever its city is.
 */
public final class CityGovernors {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String KEY = "kingdomworld.governor.";
    /** Game days the governor of a city does not order again a building the king gave up. */
    public static final int REFUSED_DAYS = 3;
    /** Seconds between two looks for orders and constructions of governors who govern no more. */
    private static final int SWEEP_SECONDS = 20;

    /** The next city of the round, by its place in the list of governed cities. */
    private static int cursor;
    private static int seconds;
    /** The game day each governor who is a citizen last looked for a bed in its city. */
    private static final Map<UUID, Long> MOVED = new HashMap<>();
    /** How far each construction of a governor stood blocked at the last look, so that one that stands still is given up. */
    private static final Map<Long, Integer> BLOCKED = new HashMap<>();
    /** What the governors of each kingdom did in this round, told once at its end. */
    private static final Map<UUID, List<Component>> SUMMARY = new LinkedHashMap<>();

    private CityGovernors() {
    }

    /**
     * Who governs a city.
     */
    public enum Kind {
        NONE,
        /** A citizen the king made mayor. */
        MAYOR,
        /** A house of the nobility that holds the city. */
        HOUSE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * The governor of a city.
     *
     * @param citizen the mayor, or the citizen who heads the house; {@code null} for none
     * @param house   the house that holds the city, {@code null} for a mayor or none
     * @param grant   the title of the house on the city
     */
    public record Governor(Kind kind, UUID kingdomId, long cityId, @Nullable UUID citizen, @Nullable NobleHouse house, @Nullable Grant grant) {
        public boolean present() {
            return kind != Kind.NONE;
        }

        /**
         * The character the city is built by: that of the house; a mayor has none.
         */
        @Nullable
        public RulerCharacter character() {
            return house == null ? null : house.character();
        }
    }

    /**
     * A city governed: its kingdom and its id (0 for the capital).
     */
    private record Seat(UUID kingdomId, long cityId) {
    }

    // ---------------------------------------------------------------- who governs

    /**
     * Whether the cities of a kingdom may have governors: those of a kingdom of players.
     */
    public static boolean governs(MinecraftServer server, UUID kingdomId) {
        return KingdomWorldApi.kingdom(server, kingdomId).isPresent() && !NpcWork.isNpc(server, kingdomId);
    }

    /**
     * The house of the nobility that holds a city of a kingdom, if one does (a title held by a player gives no governor).
     */
    static Optional<Governor> house(MinecraftServer server, UUID kingdomId, long cityId) {
        if (cityId <= 0 || NobilityWork.isReadOnly(server)) {
            return Optional.empty();
        }
        NobilityState nobility = NobilityWork.state(server);
        Optional<Grant> grant = nobility.holderOf(kingdomId, Fief.city(cityId)).filter(found -> !found.heldByPlayer());
        if (grant.isEmpty()) {
            return Optional.empty();
        }
        return nobility.house(grant.get().house()).map(house -> new Governor(Kind.HOUSE, kingdomId, cityId, house.citizen(), house, grant.get()));
    }

    /**
     * The governor of a city of a kingdom of players (0 for its capital): the house of the nobility that holds it, else its
     * mayor, else nobody. A mayor who may govern no more (see {@link CityOffice#keep}) loses the office now, and one whose city
     * went to a house too.
     */
    public static Governor governor(MinecraftServer server, UUID kingdomId, long cityId) {
        Governor none = new Governor(Kind.NONE, kingdomId, cityId, null, null, null);
        if (!governs(server, kingdomId) || CityWalls.place(server, kingdomId, cityId).isEmpty()) {
            return none;
        }
        Optional<CityCharter> charter = RoadWork.state(server).charter(kingdomId, cityId);
        UUID mayor = charter.map(CityCharter::mayor).orElse(null);
        Optional<Governor> house = house(server, kingdomId, cityId);
        if (house.isPresent()) {
            if (mayor != null) {
                endMayor(server, charter.get(), "house");
            }
            return house.get();
        }
        if (mayor == null) {
            return none;
        }
        CityOffice.Unfit unfit = CityOffice.keep(candidate(server, kingdomId, mayor), cityId);
        if (unfit == CityOffice.Unfit.FIT) {
            return new Governor(Kind.MAYOR, kingdomId, cityId, mayor, null, null);
        }
        endMayor(server, charter.get(), unfit.id());
        return none;
    }

    /**
     * The city of a kingdom whose square holds a place (0 for the capital), the nearer one where squares meet; -1 for none.
     */
    static long cityAt(MinecraftServer server, UUID kingdomId, WorldPos pos) {
        if (pos == null) {
            return -1;
        }
        return CityAreas.nearestAt(server, kingdomId, pos.dimension(), pos.x(), pos.z()).map(CityAreas.Area::cityId).orElse(-1L);
    }

    /**
     * The key of the city of a kingdom whose square holds a place, as the window of the workers names it ({@code c<id>}); empty
     * for none.
     */
    public static String cityKey(MinecraftServer server, UUID kingdomId, @Nullable WorldPos pos) {
        long city = cityAt(server, kingdomId, pos);
        return city < 0 ? "" : "c" + city;
    }

    /**
     * The office of a citizen, for the window of the workers.
     *
     * @param kind  0 none, 1 the mayor, 2 the baron (as {@code WorkforceView.CitizenRow} counts them)
     * @param place the key of the city it governs ({@code c<id>}), empty if none
     */
    public record Office(int kind, String place) {
        public static final Office NONE = new Office(0, "");
    }

    /**
     * What a citizen governs: the city it is the mayor of, or the city of the house it heads.
     */
    public static Office office(MinecraftServer server, Citizen citizen) {
        if (RoadWork.isReadOnly(server) || NobilityWork.isReadOnly(server)) {
            return Office.NONE;
        }
        Optional<CityCharter> mayor = RoadWork.state(server).mayoralty(citizen.id());
        if (mayor.isPresent()) {
            return new Office(1, "c" + mayor.get().cityId());
        }
        NobilityState nobility = NobilityWork.state(server);
        Optional<NobleHouse> house = nobility.houseOf(citizen.id());
        if (house.isEmpty()) {
            return Office.NONE;
        }
        return new Office(2, nobility.grantsOf(house.get()).stream().filter(grant -> grant.fief().kind() == Fief.Kind.CITY)
                .map(grant -> "c" + grant.fief().city()).findFirst().orElse(""));
    }

    /**
     * What is known of a citizen for an office in a city of a kingdom.
     */
    public static CityOffice.Candidate candidate(MinecraftServer server, UUID kingdomId, UUID citizenId) {
        Optional<Citizen> citizen = KingdomWorldApi.state(server).citizens().get(citizenId).filter(Citizen::isAlive);
        boolean own = citizen.map(found -> found.kingdomId().equals(kingdomId)).orElse(false);
        boolean soldier = citizen.map(found -> Professions.isMilitary(found.professionId())).orElse(false);
        WorldPos work = citizen.map(Citizen::workplace).orElse(null);
        long postCity = work == null ? -1 : cityAt(server, kingdomId, work);
        long mayorOf = RoadWork.state(server).mayoralty(citizenId).filter(found -> found.kingdomId().equals(kingdomId))
                .map(CityCharter::cityId).orElse(-1L);
        long houseCity = -1;
        if (!NobilityWork.isReadOnly(server)) {
            NobilityState nobility = NobilityWork.state(server);
            Optional<NobleHouse> house = nobility.houseOf(citizenId);
            if (house.isPresent()) {
                houseCity = nobility.grantsOf(house.get()).stream().filter(grant -> grant.fief().kind() == Fief.Kind.CITY)
                        .mapToLong(grant -> grant.fief().city()).findFirst().orElse(-2L);
            }
        }
        return new CityOffice.Candidate(citizen.isPresent(), own, soldier, work != null, postCity, mayorOf, houseCity);
    }

    /**
     * The city a citizen would govern when none is given: that of its post, else that of its home; -1 for none.
     */
    public static long cityFor(MinecraftServer server, UUID kingdomId, Citizen citizen) {
        WorldPos work = citizen.workplace();
        return CityOffice.cityFor(work != null, work == null ? -1 : cityAt(server, kingdomId, work), cityAt(server, kingdomId, citizen.home()));
    }

    /**
     * Why a citizen may not become the baron of a city, the head of a new house: as for a mayor.
     */
    public static CityOffice.Unfit baronUnfit(MinecraftServer server, UUID kingdomId, UUID citizenId, long cityId) {
        return CityOffice.baron(candidate(server, kingdomId, citizenId), cityId);
    }

    // ---------------------------------------------------------------- the decisions of the king

    private static Outcome rights(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, Permission... rights) {
        if (!governs(server, kingdomId)) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (RoadWork.isReadOnly(server) || NobilityWork.isReadOnly(server)) {
            return Outcome.fail(KingdomError.READ_ONLY);
        }
        if (actor != null) {
            for (Permission right : rights) {
                if (!KingdomWorldApi.state(server).membership().hasPermission(actor, kingdomId, right)) {
                    return Outcome.fail(KingdomError.NO_PERMISSION);
                }
            }
        }
        return Outcome.DONE;
    }

    /**
     * Makes a citizen the mayor of a city (0 for the capital): one who runs the citizens and the construction of the kingdom
     * decides. A city a house holds has its governor; the mayor there before loses the office.
     */
    public static KingdomActions.Feedback appoint(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, UUID citizenId, long cityId) {
        Outcome right = rights(server, actor, kingdomId, Permission.MANAGE_CITIZENS, Permission.MANAGE_CONSTRUCTION);
        if (!right.ok()) {
            return KingdomActions.Feedback.error(right.error());
        }
        if (CityWalls.place(server, kingdomId, cityId).isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_CITY);
        }
        if (house(server, kingdomId, cityId).isPresent()) {
            return KingdomActions.Feedback.fail(KEY + "held_by_house", cityName(server, kingdomId, cityId));
        }
        CityOffice.Unfit unfit = CityOffice.mayor(candidate(server, kingdomId, citizenId), cityId);
        if (unfit != CityOffice.Unfit.FIT) {
            return KingdomActions.Feedback.fail(KEY + "unfit." + unfit.id());
        }
        Citizen citizen = KingdomWorldApi.state(server).citizens().get(citizenId).orElseThrow();
        CityCharter charter = CityPeople.charterOf(server, kingdomId, cityId);
        if (citizenId.equals(charter.mayor())) {
            return KingdomActions.Feedback.error(KingdomError.UNCHANGED);
        }
        if (charter.mayor() != null) {
            endMayor(server, charter, "replaced");
        }
        charter.setMayor(citizenId, KingdomWorldApi.gameTime(server));
        charter.setNote("");
        charter.setDecidedDay(CityCharter.NEVER);
        RoadWork.state(server).charterChanged();
        write(server, kingdomId, "mayor_appointed", Note.person(citizen.nameSeed(), citizen.gender()), cityArg(kingdomId, cityId));
        MOVED.remove(citizenId);
        moveIn(server, kingdomId, citizenId, cityId);
        retitle(server, citizenId);
        Component name = CitizenTexts.name(citizen);
        tell(server, kingdomId, Component.translatable(KEY + "appointed", name, cityName(server, kingdomId, cityId)).withStyle(ChatFormatting.GOLD));
        LOGGER.info("Citizen {} became the mayor of city {} of {}", citizenId, cityId, kingdomId);
        return KingdomActions.Feedback.ok(KEY + "appointed", name, cityName(server, kingdomId, cityId));
    }

    /**
     * Makes a citizen the baron of a city: the king raises a new house headed by the citizen, with the city as its fief. It has no
     * wealth and does not count against the most houses the rules allow. A mayor of the city loses the office to it.
     */
    public static KingdomActions.Feedback makeBaron(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, UUID citizenId, long cityId) {
        Outcome right = rights(server, actor, kingdomId, Permission.MANAGE_NOBILITY);
        if (!right.ok()) {
            return KingdomActions.Feedback.error(right.error());
        }
        if (cityId <= 0) {
            return KingdomActions.Feedback.fail(KEY + "capital_no_fief");
        }
        if (CityWalls.place(server, kingdomId, cityId).isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_CITY);
        }
        if (NobilityWork.state(server).holderOf(kingdomId, Fief.city(cityId)).isPresent()) {
            return KingdomActions.Feedback.error(KingdomError.FIEF_TAKEN);
        }
        CityOffice.Unfit unfit = baronUnfit(server, kingdomId, citizenId, cityId);
        if (unfit != CityOffice.Unfit.FIT) {
            return KingdomActions.Feedback.fail(KEY + "unfit." + unfit.id());
        }
        Outcome granted = NobilityActions.grant(server, actor, kingdomId, Title.BARON, Fief.city(cityId), -1, null, citizenId);
        if (!granted.ok()) {
            return KingdomActions.Feedback.error(granted.error());
        }
        Citizen citizen = KingdomWorldApi.state(server).citizens().get(citizenId).orElseThrow();
        RoadWork.state(server).charter(kingdomId, cityId).filter(charter -> charter.mayor() != null)
                .ifPresent(charter -> endMayor(server, charter, "house"));
        CityCharter charter = CityPeople.charterOf(server, kingdomId, cityId);
        charter.setNote("");
        charter.setDecidedDay(CityCharter.NEVER);
        RoadWork.state(server).charterChanged();
        MOVED.remove(citizenId);
        moveIn(server, kingdomId, citizenId, cityId);
        retitle(server, citizenId);
        Component name = CitizenTexts.name(citizen);
        tell(server, kingdomId, Component.translatable(KEY + "baron", name, cityName(server, kingdomId, cityId)).withStyle(ChatFormatting.GOLD));
        LOGGER.info("Citizen {} became the baron of city {} of {}", citizenId, cityId, kingdomId);
        return KingdomActions.Feedback.ok(KEY + "baron", name, cityName(server, kingdomId, cityId));
    }

    /**
     * Dismisses the governor of a city: a mayor loses the office, a house its title on the city (as the court takes a title back).
     * Its orders not laid out go; its constructions stay as ordinary ones.
     */
    public static KingdomActions.Feedback dismiss(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, long cityId) {
        Governor governor = governor(server, kingdomId, cityId);
        switch (governor.kind()) {
            case MAYOR -> {
                Outcome right = rights(server, actor, kingdomId, Permission.MANAGE_CITIZENS, Permission.MANAGE_CONSTRUCTION);
                if (!right.ok()) {
                    return KingdomActions.Feedback.error(right.error());
                }
                endMayor(server, RoadWork.state(server).charter(kingdomId, cityId).orElseThrow(), "dismissed");
                return KingdomActions.Feedback.ok(KEY + "dismissed", cityName(server, kingdomId, cityId));
            }
            case HOUSE -> {
                Outcome right = rights(server, actor, kingdomId, Permission.MANAGE_NOBILITY);
                if (!right.ok()) {
                    return KingdomActions.Feedback.error(right.error());
                }
                Outcome revoked = NobilityActions.revoke(server, actor, kingdomId, governor.grant().id());
                if (!revoked.ok()) {
                    return KingdomActions.Feedback.error(revoked.error());
                }
                ended(server, kingdomId, cityId);
                if (governor.citizen() != null) {
                    retitle(server, governor.citizen());
                }
                return KingdomActions.Feedback.ok(KEY + "dismissed", cityName(server, kingdomId, cityId));
            }
            default -> {
                return KingdomActions.Feedback.fail(KEY + "no_governor", cityName(server, kingdomId, cityId));
            }
        }
    }

    /**
     * Stops the governor of a city or lets it go on: one who runs the construction of the kingdom decides.
     */
    public static KingdomActions.Feedback pause(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, long cityId, boolean paused) {
        Outcome right = rights(server, actor, kingdomId, Permission.MANAGE_CONSTRUCTION);
        if (!right.ok()) {
            return KingdomActions.Feedback.error(right.error());
        }
        if (CityWalls.place(server, kingdomId, cityId).isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_CITY);
        }
        CityCharter charter = CityPeople.charterOf(server, kingdomId, cityId);
        if (charter.paused() == paused) {
            return KingdomActions.Feedback.error(KingdomError.UNCHANGED);
        }
        charter.setPaused(paused);
        charter.setNote(paused ? GovernorNote.of(GovernorNote.Kind.PAUSED).encode() : "");
        // Let go on, the governor looks at its city again today.
        charter.setDecidedDay(paused ? charter.decidedDay() : CityCharter.NEVER);
        RoadWork.state(server).charterChanged();
        return KingdomActions.Feedback.ok(KEY + (paused ? "paused" : "resumed"), cityName(server, kingdomId, cityId));
    }

    /**
     * Sets what the king gives the governors of all cities a day: one who runs the treasury decides.
     */
    public static KingdomActions.Feedback setAllowance(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, long amount) {
        Outcome right = rights(server, actor, kingdomId, Permission.MANAGE_TREASURY);
        if (!right.ok()) {
            return KingdomActions.Feedback.error(right.error());
        }
        if (amount < 0 || amount > io.github.sekerator.kingdomworld.core.economy.Money.MAX) {
            return KingdomActions.Feedback.error(KingdomError.VALUE_OUT_OF_RANGE);
        }
        NobilityState nobility = NobilityWork.state(server);
        nobility.court(kingdomId).setGovernorAllowance(amount);
        nobility.changed();
        LOGGER.info("Kingdom {} gives its governors {} a day", kingdomId, amount);
        return KingdomActions.Feedback.ok(KEY + "allowance_set", money(server, kingdomId, amount));
    }

    // ---------------------------------------------------------------- the end of an office

    /**
     * A mayor loses the office: the court writes it down, the kingdom hears, and the governor of the city stops.
     *
     * @param why gone, other_kingdom, soldier, post_elsewhere, dismissed, replaced or house
     */
    static void endMayor(MinecraftServer server, CityCharter charter, String why) {
        UUID mayor = charter.mayor();
        if (mayor == null || RoadWork.isReadOnly(server)) {
            return;
        }
        UUID kingdomId = charter.kingdomId();
        long cityId = charter.cityId();
        charter.setMayor(null, CityCharter.NEVER);
        charter.setNote("");
        RoadWork.state(server).charterChanged();
        MOVED.remove(mayor);
        Optional<Citizen> citizen = KingdomWorldApi.state(server).citizens().get(mayor);
        String person = citizen.map(found -> Note.person(found.nameSeed(), found.gender())).orElse(Note.text("?"));
        write(server, kingdomId, "mayor_ended." + why, person, cityArg(kingdomId, cityId));
        if (!why.equals("replaced")) {
            ended(server, kingdomId, cityId);
        }
        Component name = citizen.<Component>map(CitizenTexts::name).orElse(Component.literal("?"));
        tell(server, kingdomId, Component.translatable(KEY + "ended." + why, name, cityName(server, kingdomId, cityId)).withStyle(ChatFormatting.YELLOW));
        retitle(server, mayor);
        LOGGER.info("The mayor {} of city {} of {} lost the office: {}", mayor, cityId, kingdomId, why);
    }

    /**
     * The governor of a city is no more: its orders not laid out go, its constructions stay as ordinary constructions.
     */
    static void ended(MinecraftServer server, UUID kingdomId, long cityId) {
        RemoteWorks.dropGovernorOrders(server, kingdomId, cityId);
        for (Construction construction : KingdomWorldApi.state(server).construction().of(kingdomId)) {
            if (construction.governor() && ConstructionWork.cityOf(server, construction) == cityId) {
                construction.setGovernor(false);
            }
        }
        RoadWork.state(server).charter(kingdomId, cityId).ifPresent(charter -> {
            charter.setNote("");
            RoadWork.state(server).charterChanged();
        });
    }

    /**
     * The king gave up a building a governor ordered or laid out: the governor of the city does not order it again for
     * {@value #REFUSED_DAYS} game days.
     */
    public static void refused(MinecraftServer server, UUID kingdomId, long cityId, String templateId) {
        if (cityId < 0 || RoadWork.isReadOnly(server)) {
            return;
        }
        RoadWork.state(server).charter(kingdomId, cityId).ifPresent(charter -> {
            charter.refuse(templateId, NobilityWork.day(server) + REFUSED_DAYS);
            RoadWork.state(server).charterChanged();
        });
    }

    /**
     * A construction a governor laid out is finished: the court writes it down.
     */
    public static void built(MinecraftServer server, Construction construction, Building building) {
        long cityId = ConstructionWork.cityOf(server, construction);
        if (cityId < 0) {
            return;
        }
        write(server, construction.kingdomId(), "governor_built", cityArg(construction.kingdomId(), cityId), Note.template(building.templateId()));
    }

    // ---------------------------------------------------------------- the round

    /**
     * Called once a second: the next city with a governor in turn, and now and then the orders and constructions of governors
     * who govern no more.
     */
    public static void tick(MinecraftServer server) {
        if (RoadWork.isReadOnly(server) || NobilityWork.isReadOnly(server)) {
            return;
        }
        if (++seconds % SWEEP_SECONDS == 0) {
            sweep(server);
        }
        List<Seat> seats = seats(server);
        if (cursor >= seats.size()) {
            cursor = 0;
            flush(server);
        }
        if (seats.isEmpty()) {
            return;
        }
        Seat seat = seats.get(cursor++);
        int governed = (int) seats.stream().filter(other -> other.kingdomId().equals(seat.kingdomId())).count();
        try {
            visit(server, seat, governed);
        } catch (RuntimeException e) {
            LOGGER.error("The governor of city {} of {} failed", seat.cityId(), seat.kingdomId(), e);
        }
    }

    /**
     * The cities that may have a governor now, in a fixed order: those with a mayor and those a house of the nobility holds, of
     * kingdoms of players.
     */
    private static List<Seat> seats(MinecraftServer server) {
        TreeSet<Seat> seats = new TreeSet<>(Comparator.comparing(Seat::kingdomId).thenComparingLong(Seat::cityId));
        for (CityCharter charter : RoadWork.state(server).charters()) {
            if (charter.mayor() != null && governs(server, charter.kingdomId())) {
                seats.add(new Seat(charter.kingdomId(), charter.cityId()));
            }
        }
        for (Grant grant : NobilityWork.state(server).grants()) {
            if (grant.fief().kind() == Fief.Kind.CITY && !grant.heldByPlayer() && governs(server, grant.kingdomId())) {
                seats.add(new Seat(grant.kingdomId(), grant.fief().city()));
            }
        }
        return new ArrayList<>(seats);
    }

    /**
     * How many cities of a kingdom have a governor now.
     */
    public static int governed(MinecraftServer server, UUID kingdomId) {
        return (int) seats(server).stream().filter(seat -> seat.kingdomId().equals(kingdomId)).count();
    }

    /**
     * Lets the governor of a city look at it at once, as on a new game day: for operators and game tests.
     */
    public static void decideNow(MinecraftServer server, UUID kingdomId, long cityId) {
        CityCharter charter = CityPeople.charterOf(server, kingdomId, cityId);
        if (charter.decidedDay() != CityCharter.NEVER) {
            // Any day but today, and not the first one.
            charter.setDecidedDay(CityCharter.NEVER + 1);
        }
        visit(server, new Seat(kingdomId, cityId), Math.max(1, governed(server, kingdomId)));
    }

    private static void visit(MinecraftServer server, Seat seat, int governed) {
        UUID kingdomId = seat.kingdomId();
        long cityId = seat.cityId();
        Governor governor = governor(server, kingdomId, cityId);
        if (!governor.present()) {
            return;
        }
        if (governor.citizen() != null) {
            moveIn(server, kingdomId, governor.citizen(), cityId);
        }
        RoadState roads = RoadWork.state(server);
        CityCharter charter = CityPeople.charterOf(server, kingdomId, cityId);
        long day = NobilityWork.day(server);
        if (charter.forgetRefused(day)) {
            roads.charterChanged();
        }
        if (charter.paused()) {
            String paused = GovernorNote.of(GovernorNote.Kind.PAUSED).encode();
            if (!paused.equals(charter.note())) {
                charter.setNote(paused);
                roads.charterChanged();
            }
            return;
        }
        if (!NobilityWork.played(server, kingdomId) || charter.decidedDay() == day) {
            // Nobody of the kingdom played today: nothing is spent while they are away.
            return;
        }
        boolean first = charter.decidedDay() == CityCharter.NEVER;
        charter.setDecidedDay(day);
        giveUpBlocked(server, kingdomId, cityId);
        decide(server, governor, charter, day, governed, first);
        roads.charterChanged();
    }

    /**
     * Looks once more at the orders of governors and the constructions they laid out: those of a city without a governor now go,
     * or stay as ordinary constructions.
     */
    static void sweep(MinecraftServer server) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        Map<String, Boolean> governed = new HashMap<>();
        for (Commission commission : List.copyOf(state.construction().commissions())) {
            if (commission.origin() != Commission.Origin.GOVERNOR || commission.actor() != null) {
                continue;
            }
            String key = commission.kingdomId() + "/" + commission.cityId();
            boolean has = governed.computeIfAbsent(key, k -> governor(server, commission.kingdomId(), commission.cityId()).present());
            if (!has) {
                RemoteWorks.dropGovernorOrders(server, commission.kingdomId(), commission.cityId());
            }
        }
        for (Construction construction : state.construction().all()) {
            if (!construction.governor()) {
                continue;
            }
            long cityId = ConstructionWork.cityOf(server, construction);
            String key = construction.kingdomId() + "/" + cityId;
            boolean has = cityId >= 0 && governed.computeIfAbsent(key, k -> governor(server, construction.kingdomId(), cityId).present());
            if (!has) {
                construction.setGovernor(false);
            }
        }
        BLOCKED.keySet().removeIf(id -> state.construction().get(id).isEmpty());
    }

    /**
     * A governor who is a citizen moves into its city once a game day while it lives elsewhere: into the free bed of the city
     * nearest its town hall, a bed in a house first. Without one it keeps its home and tries again the next day.
     */
    static void moveIn(MinecraftServer server, UUID kingdomId, UUID citizenId, long cityId) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        Optional<Citizen> found = state.citizens().get(citizenId).filter(Citizen::isAlive);
        if (found.isEmpty()) {
            return;
        }
        Citizen citizen = found.get();
        if (citizen.home() != null && cityAt(server, kingdomId, citizen.home()) == cityId) {
            MOVED.remove(citizenId);
            return;
        }
        long day = NobilityWork.day(server);
        Long tried = MOVED.get(citizenId);
        if (tried != null && tried == day) {
            return;
        }
        MOVED.put(citizenId, day);
        Optional<BlockPos> middle = CityPlots.middle(server, kingdomId, cityId);
        Optional<String> dimension = CityPlots.dimension(server, kingdomId, cityId);
        if (middle.isEmpty() || dimension.isEmpty()) {
            return;
        }
        WorldPos hall = new WorldPos(dimension.get(), middle.get().getX(), middle.get().getY(), middle.get().getZ());
        Optional<Site> bed = BuildingEffects.freeBed(server, kingdomId, hall, false, pos -> cityAt(server, kingdomId, pos) == cityId);
        if (bed.isEmpty()) {
            return;
        }
        WorldPos before = citizen.home();
        Outcome moved = KingdomWorldApi.change(server, "move a governor into its city",
                s -> s.citizens().assignHome(citizen, bed.get().pos()) ? Outcome.DONE : Outcome.fail(KingdomError.UNCHANGED));
        if (moved.ok()) {
            MOVED.remove(citizenId);
            LOGGER.info("The governor {} of city {} of {} moved from {} to {}", citizenId, cityId, kingdomId, before, bed.get().pos());
        }
    }

    // ---------------------------------------------------------------- the day of a governor

    /**
     * What a governor does today: looks at its city, decides by {@link CityGrowth#decide} and orders what it decided.
     */
    private static void decide(MinecraftServer server, Governor governor, CityCharter charter, long day, int governed, boolean first) {
        UUID kingdomId = governor.kingdomId();
        long cityId = governor.cityId();
        RulerRules rules = NpcSettings.rulers();
        RulerCharacter character = governor.character();
        RulerView view = view(server, kingdomId, cityId, day, governor);
        // Where the governor pays out of the treasury of its city, its share of the money of the king goes there first.
        GovernorFunds.grantShare(server, kingdomId, cityId, governed, view, rules);
        price(server, kingdomId, cityId, view, rules, character, charter, day);
        CityGrowth.Situation situation = situation(server, kingdomId, cityId, charter, day, governed, view, rules, character);
        CityGrowth.Decision decision = CityGrowth.decide(view, situation, rules);
        decision = room(server, kingdomId, cityId, decision);
        GovernorNote before = GovernorNote.parse(charter.note());
        Component city = cityName(server, kingdomId, cityId);
        Component line = null;
        switch (decision.kind()) {
            case ORDER_POST -> {
                Outcome ordered = RemoteWorks.orderPost(server, null, kingdomId, cityId, Commission.Origin.GOVERNOR);
                if (ordered.ok()) {
                    line = Component.translatable(KEY + "summary.post", city);
                }
            }
            case BUILD -> {
                Outcome ordered = RemoteWorks.orderForGovernor(server, kingdomId, decision.template(), cityId);
                if (ordered.ok()) {
                    line = Component.translatable(KEY + "summary.build", city, templateName(decision.template()));
                }
            }
            case EXPAND -> {
                // The square of the city is wider from now on; what found no place looks again, and what was wanted is ordered.
                boolean waiting = stuck(server, kingdomId, cityId);
                charter.setExtent(decision.extent());
                RoadWork.state(server).charterChanged();
                CityAreas.invalidate();
                RemoteWorks.retryGovernorOrders(server, kingdomId, cityId);
                line = Component.translatable(KEY + "summary.expand", city, decision.extent());
                if (!decision.template().isEmpty() && !waiting
                        && RemoteWorks.orderForGovernor(server, kingdomId, decision.template(), cityId).ok()) {
                    line = Component.translatable(KEY + "summary.expand_build", city, decision.extent(), templateName(decision.template()));
                }
                LOGGER.info("The governor of city {} of {} widened it to {} blocks", cityId, kingdomId, decision.extent());
            }
            case WALL -> {
                Outcome ordered = CityWalls.order(server, null, kingdomId, cityId, decision.wall());
                if (ordered.ok()) {
                    line = Component.translatable(KEY + "summary.wall." + decision.wall().name().toLowerCase(Locale.ROOT), city);
                }
            }
            default -> {
            }
        }
        // What the constructions of the city lack is bought abroad with what is left of the money of the day.
        long spent = GovernorFunds.pay(server, kingdomId, cityId, charter, day, GovernorFunds.budget(server, kingdomId, charter, day, governed, view,
                rules));
        if (spent > 0) {
            summary(kingdomId, Component.translatable(KEY + "summary.bought", city, money(server, kingdomId, spent)));
        }
        GovernorNote note = decision.note();
        charter.setNote(note.encode());
        if (first) {
            // A city governed before the governors built (by a house of the old days) starts now: the king hears it once.
            summary(kingdomId, Component.translatable(KEY + "summary.started", city, governorName(server, governor)));
        }
        if (line == null && !note.equals(before)) {
            line = switch (note.kind()) {
                case SAVES -> Component.translatable(KEY + "summary.saves", city, templateName(note.subject()), money(server, kingdomId, note.amount()));
                case LACKS -> Component.translatable(KEY + "summary.lacks", city, templateName(note.subject()));
                case NO_BUILDER -> Component.translatable(KEY + "summary.no_builder", city);
                case NEEDS_LAND -> Component.translatable(KEY + "summary.needs_land", city);
                default -> null;
            };
        }
        if (line != null) {
            summary(kingdomId, line);
        }
        LOGGER.debug("The governor of city {} of {} decided {} {}", cityId, kingdomId, decision.kind(), decision.template());
    }

    /**
     * Whether an order of the governor of a city found no place in it.
     */
    static boolean stuck(MinecraftServer server, UUID kingdomId, long cityId) {
        for (Commission commission : KingdomWorldApi.state(server).construction().commissionsOf(kingdomId)) {
            if (commission.origin() == Commission.Origin.GOVERNOR && commission.cityId() == cityId
                    && commission.state() == Commission.State.NO_PLACE) {
                return true;
            }
        }
        return false;
    }

    /**
     * What a governor does when the places of its city ran out: an order of its found no place, or the building it wants has no
     * place by the data. It widens the square of the city by a ring when land of its kingdom lies there, else it says the city
     * needs land around it. Otherwise its decision stands.
     */
    static CityGrowth.Decision room(MinecraftServer server, UUID kingdomId, long cityId, CityGrowth.Decision decision) {
        String template = decision.kind() == CityGrowth.Kind.BUILD ? decision.template() : "";
        boolean stuck = false;
        for (Commission commission : KingdomWorldApi.state(server).construction().commissionsOf(kingdomId)) {
            if (commission.origin() == Commission.Origin.GOVERNOR && commission.cityId() == cityId
                    && commission.state() == Commission.State.NO_PLACE) {
                stuck = true;
                template = template.isEmpty() ? commission.templateId() : template;
            }
        }
        if (!stuck && (decision.kind() != CityGrowth.Kind.BUILD || hasPlace(server, kingdomId, cityId, template))) {
            return decision;
        }
        Optional<CityAreas.Area> area = CityAreas.of(server, kingdomId).stream().filter(found -> found.cityId() == cityId).findFirst();
        if (area.isEmpty()) {
            return decision;
        }
        int size = BuildingTemplates.get(template).map(found -> Math.max(found.sizeX(), found.sizeZ())).orElse(CityGrowth.LEAST_RING);
        int ring = CityGrowth.ring(size);
        boolean land = false;
        for (int[] chunk : CityGrowth.ringChunks(area.get().x(), area.get().z(), area.get().radius(), ring)) {
            if (CityPlots.ownLand(server, kingdomId, area.get().dimension(),
                    io.github.sekerator.kingdomworld.core.territory.ChunkKey.of(chunk[0], chunk[1]))) {
                land = true;
                break;
            }
        }
        CityGrowth.Decision wanted = decision.kind() == CityGrowth.Kind.BUILD ? decision
                : new CityGrowth.Decision(CityGrowth.Kind.BUILD, "", template, 0, null, 0, 0);
        return CityGrowth.outOfRoom(wanted, land, area.get().radius(), ring);
    }

    /**
     * Whether a template has a place in the square of a city by the data: its land, nothing laid out there, clear of roads.
     */
    static boolean hasPlace(MinecraftServer server, UUID kingdomId, long cityId, String templateId) {
        Optional<BuildingTemplate> template = BuildingTemplates.get(templateId);
        return template.isEmpty() || !CityPlots.places(server, kingdomId, cityId, template.get(), null, java.util.Set.of(), 1).isEmpty();
    }

    /**
     * What a governor sees of its city, as a ruler of the world sees its kingdom: its level, its beds and townsfolk, the
     * storehouses in its square, the buildings standing there, laid out and ordered, and the money of the treasury.
     */
    static RulerView view(MinecraftServer server, UUID kingdomId, long cityId, long day, Governor governor) {
        RulerView view = new RulerView();
        view.day = day;
        view.cityLevel = CityWalls.level(server, kingdomId, cityId).ordinal();
        if (governor.house() != null) {
            view.character = governor.house().character();
            view.traits = List.of(governor.house().trait());
        } else {
            // A mayor has no leanings.
            view.traits = List.of();
        }
        int folk = RoadWork.state(server).folk(kingdomId, cityId);
        for (CityPeople.Census census : CityPeople.census(server, kingdomId)) {
            if (census.cityId() == cityId) {
                view.bedsTotal = census.beds();
                view.bedsUsed = census.beds() - CityFolk.bedsLeft(census.freeBeds(), folk, FolkSettings.rules());
                view.population = census.named() + folk;
                view.mood = census.mood();
            }
        }
        int slots = 0;
        int used = 0;
        for (StorehouseInventory inventory : KingdomStorage.storehouses(server, kingdomId, null)) {
            if (cityAt(server, kingdomId, inventory.pos()) == cityId) {
                slots += inventory.getSlots();
                used += inventory.getSlots() - KingdomStorage.freeSlots(inventory);
            }
        }
        view.storageSlots = slots;
        view.storageUsed = used;
        view.knownRoles = new ArrayList<>();
        io.github.sekerator.kingdomworld.core.construction.BuildingRoles.all().forEach(role -> view.knownRoles.add(role.id()));
        KingdomWorldState state = KingdomWorldApi.state(server);
        for (Building building : state.construction().buildingsOf(kingdomId)) {
            if (ConstructionWork.cityOf(server, kingdomId, building.anchor()) == cityId) {
                view.roles.merge(building.role(), 1, Integer::sum);
            }
        }
        for (Construction construction : state.construction().of(kingdomId)) {
            if (construction.kind() == Construction.Kind.BUILD && ConstructionWork.cityOf(server, construction) == cityId) {
                BuildingTemplates.get(construction.templateId()).ifPresent(template -> view.planned.merge(template.role(), 1, Integer::sum));
            }
        }
        for (Commission commission : state.construction().commissionsOf(kingdomId)) {
            if (commission.cityId() == cityId && (commission.kind() == Commission.Kind.BUILD || commission.kind() == Commission.Kind.AT)) {
                BuildingTemplates.get(commission.templateId()).ifPresent(template -> view.planned.merge(template.role(), 1, Integer::sum));
            }
        }
        treasury(server, kingdomId, day, view);
        return view;
    }

    /**
     * The money of the treasury and what it spends on an average day of the last week, wages at least.
     */
    static void treasury(MinecraftServer server, UUID kingdomId, long day, RulerView view) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        Optional<Treasury> found = state.economy().treasury(kingdomId);
        if (found.isEmpty()) {
            return;
        }
        Treasury treasury = found.get();
        view.balance = treasury.funds();
        long founded = KingdomWorldApi.kingdom(server, kingdomId).map(Kingdom::createdGameTime).orElse(0L)
                / io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds.TICKS_PER_DAY;
        long age = Math.max(1, Math.min(7, day - founded));
        long income = 0;
        long expense = 0;
        for (Map.Entry<LedgerCategory, TreasuryJournal.Totals> entry : treasury.journal().totals(day, TreasuryJournal.Period.WEEK).entrySet()) {
            if (entry.getKey() == LedgerCategory.FOUNDING || entry.getKey() == LedgerCategory.ADMIN) {
                continue;
            }
            income += entry.getValue().income();
            expense += entry.getValue().expense();
        }
        view.income = income / age;
        view.expenses = expense / age;
        long payroll = 0;
        var economy = io.github.sekerator.kingdomworld.common.economy.EconomySettings.rules();
        for (Citizen citizen : state.citizens().registry().citizensOf(kingdomId)) {
            payroll += new io.github.sekerator.kingdomworld.core.economy.Economy.WageClaim(citizen.id(), citizen.professionId(),
                    io.github.sekerator.kingdomworld.common.army.ArmyStaff.wagePermille(server, citizen.id()))
                    .scaled(treasury.wage(citizen.professionId(), economy));
        }
        view.expenses = Math.max(view.expenses, payroll);
    }

    /**
     * The cheapest template of every role the governor wants, with what its materials lack worth, as the governor counts them:
     * only a share of every good of the storehouses, in the style of the kingdom; templates the king gave up lately left out.
     */
    static void price(MinecraftServer server, UUID kingdomId, long cityId, RulerView view, RulerRules rules, @Nullable RulerCharacter character,
                      CityCharter charter, long day) {
        String style = KingdomStyles.of(server, kingdomId);
        Map<String, Long> stock = KingdomStorage.countsById(server, kingdomId);
        ToLongFunction<String> worth = MaterialWorth.worth(server);
        int percent = RoadSettings.governorStockPercent();
        BlockPos hall = CityPlots.middle(server, kingdomId, cityId).orElse(BlockPos.ZERO);
        view.wantsShopHouse = io.github.sekerator.kingdomworld.common.commerce.CityShopHouses.wanted(server, kingdomId, cityId);
        for (String role : CityGrowth.wantedRoles(view, rules, character)) {
            List<BuildingTemplate> candidates = new ArrayList<>(CityGrowth.SHOP_HOUSE.equals(role)
                    ? io.github.sekerator.kingdomworld.common.commerce.CityShopHouses.templates(style) : RulerPlaces.templates(role, style));
            candidates.removeIf(template -> charter.refuses(template.id().toString(), day));
            if (candidates.isEmpty()) {
                continue;
            }
            long[] missing = new long[candidates.size()];
            long least = Long.MAX_VALUE;
            for (int i = 0; i < candidates.size(); i++) {
                BuildingTemplate template = candidates.get(i);
                Materials materials = KingdomLooks.materials(server, kingdomId, template.id().toString(), hall);
                Map<String, Integer> cost = TemplateMaterials.apply(template, materials).items();
                missing[i] = CityGrowth.missing(cost, stock, worth, percent);
                least = Math.min(least, missing[i]);
            }
            // Of those that cost about the least, the next in turn for every building of the role there, so that a city does not
            // put up the same house every time.
            int turn = view.built(role) + view.planned(role);
            BuildingTemplate best = null;
            for (int i = 0; i < candidates.size() && best == null; i++) {
                int index = Math.floorMod(turn + i, candidates.size());
                if (missing[index] <= least + Math.max(40, least / 4)) {
                    best = candidates.get(index);
                    least = missing[index];
                }
            }
            if (best != null) {
                view.builds.add(new RulerView.Option(role, best.id().toString(), "", least, true));
            }
        }
    }

    /**
     * What a governor knows of its city beyond its view: its builders and those of the other cities, the citizens without work,
     * the constructions and orders it waits for, its wall and what it may spend today.
     */
    static CityGrowth.Situation situation(MinecraftServer server, UUID kingdomId, long cityId, CityCharter charter, long day, int governed,
                                          RulerView view, RulerRules rules, @Nullable RulerCharacter character) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        int builders = 0;
        int others = 0;
        int sites = 0;
        for (Map.Entry<Long, CityCrews.Crew> crew : CityCrews.of(server, kingdomId).entrySet()) {
            if (crew.getKey() == cityId) {
                builders = crew.getValue().builders();
                sites = crew.getValue().sites();
            } else {
                others += crew.getValue().builders();
            }
        }
        boolean postOrdered = false;
        for (Commission commission : state.construction().commissionsOf(kingdomId)) {
            if (commission.cityId() == cityId && commission.kind() != Commission.Kind.HALL) {
                sites++;
                postOrdered |= commission.kind() == Commission.Kind.POST;
            }
        }
        boolean laying = CityWalls.layingOut(kingdomId, cityId);
        if (laying) {
            sites++;
        }
        int unemployed = 0;
        for (Citizen citizen : state.citizens().registry().citizensOf(kingdomId)) {
            if (Professions.UNEMPLOYED.id().equals(citizen.professionId())) {
                unemployed++;
            }
        }
        boolean wall = laying || RoadWork.state(server).wallOf(cityId, kingdomId).isPresent();
        // Without money a governor builds from the storehouses and the workshops alone: without money from the king, or, where it
        // pays out of the treasury of its city, without money there either.
        long allowance = GovernorFunds.allowance(server, kingdomId);
        long budget = GovernorFunds.budget(server, kingdomId, charter, day, governed, view, rules);
        if (GovernorFunds.fromCity(server, kingdomId)) {
            allowance = Math.max(allowance, budget);
        }
        return new CityGrowth.Situation(builders, others, unemployed, sites, postOrdered, wall, allowance, budget, character);
    }

    /**
     * Gives up a construction of the governor of a city that a block of a player keeps from being finished: it stood blocked at
     * the last look already and got no further since. What was paid goes back to the storehouses.
     */
    static void giveUpBlocked(MinecraftServer server, UUID kingdomId, long cityId) {
        for (Construction construction : List.copyOf(KingdomWorldApi.state(server).construction().of(kingdomId))) {
            if (!construction.governor() || ConstructionWork.cityOf(server, construction) != cityId) {
                continue;
            }
            if (construction.status() != Construction.Status.BLOCKED) {
                BLOCKED.remove(construction.id());
                continue;
            }
            Integer seen = BLOCKED.put(construction.id(), construction.doneCount());
            if (seen == null || seen != construction.doneCount()) {
                continue;
            }
            BLOCKED.remove(construction.id());
            if (ConstructionWork.cancel(server, null, kingdomId, construction.id()).outcome().ok()) {
                summary(kingdomId, Component.translatable(KEY + "summary.given_up", cityName(server, kingdomId, cityId),
                        templateName(construction.templateId())));
                LOGGER.info("The governor of city {} of {} gave up construction {}: a block of a player keeps it from being finished",
                        cityId, kingdomId, construction.id());
            }
        }
    }

    // ---------------------------------------------------------------- texts

    /**
     * The name of a city: the kingdom for its capital.
     */
    public static Component cityName(MinecraftServer server, UUID kingdomId, long cityId) {
        if (cityId == 0) {
            return KingdomWorldApi.kingdom(server, kingdomId).<Component>map(KingdomActions::kingdomName).orElse(Component.literal("?"));
        }
        return RoadWork.state(server).city(cityId).<Component>map(city -> Component.literal(city.name())).orElse(Component.literal("?"));
    }

    /**
     * What a governor decided last, in words, for the window of the king.
     */
    public static Component noteText(MinecraftServer server, UUID kingdomId, GovernorNote note) {
        return switch (note.kind()) {
            case NONE, PAUSED, NO_BUILDER, POST, DONE -> Component.translatable(KEY + "note." + note.kind().id());
            case WAITING -> Component.translatable(KEY + "note.waiting", note.amount());
            case BUILDS, LACKS -> Component.translatable(KEY + "note." + note.kind().id(), templateName(note.subject()));
            case SAVES -> Component.translatable(KEY + "note.saves", templateName(note.subject()), money(server, kingdomId, note.amount()));
            case WALL -> Component.translatable(KEY + "note.wall." + (note.subject().equals("stone") ? "stone" : "palisade"));
            case EXPANDS -> note.subject().isEmpty() ? Component.translatable(KEY + "note.expands", note.amount())
                    : Component.translatable(KEY + "note.expands_build", note.amount(), templateName(note.subject()));
            case NEEDS_LAND -> Component.translatable(KEY + "note.needs_land");
        };
    }

    /**
     * What a governor decided last and, while the king gives the governors money, what it spent of its share today.
     */
    public static Component noteWithSpending(MinecraftServer server, UUID kingdomId, long cityId, GovernorNote note) {
        Component text = noteText(server, kingdomId, note);
        long allowance = GovernorFunds.allowance(server, kingdomId);
        if (allowance <= 0) {
            return text;
        }
        long day = NobilityWork.day(server);
        long spent = RoadWork.state(server).charter(kingdomId, cityId).map(charter -> charter.spent(day)).orElse(0L);
        long share = CityGrowth.share(allowance, Math.max(1, governed(server, kingdomId)));
        return Component.translatable(KEY + "note_spent", text, money(server, kingdomId, spent), money(server, kingdomId, share));
    }

    static Component templateName(String templateId) {
        return BuildingTemplates.get(templateId).map(BuildingTemplate::name).orElse(Component.literal(templateId));
    }

    static Component money(MinecraftServer server, UUID kingdomId, long amount) {
        return io.github.sekerator.kingdomworld.common.economy.MoneyTexts.amount(amount,
                io.github.sekerator.kingdomworld.common.trade.Purses.units(server, kingdomId));
    }

    /**
     * Who governs, for messages: the mayor by name, or the house.
     */
    static Component governorName(MinecraftServer server, Governor governor) {
        if (governor.house() != null) {
            return Component.translatable(KEY + "house", NobilityTexts.house(governor.house()),
                    Component.translatable(KEY + "title." + governor.grant().title().id()));
        }
        return governor.citizen() == null ? Component.empty() : KingdomWorldApi.state(server).citizens().get(governor.citizen())
                .<Component>map(CitizenTexts::name).orElse(Component.literal("?"));
    }

    /**
     * The name of the governor of a city in an alphabet, for the window: the citizen, or the family of the house.
     */
    public static String governorName(MinecraftServer server, Governor governor, CitizenNames.Alphabet alphabet) {
        if (governor.house() != null) {
            return governor.house().family(alphabet);
        }
        if (governor.citizen() == null) {
            return "";
        }
        return KingdomWorldApi.state(server).citizens().get(governor.citizen()).map(citizen -> citizen.name(alphabet)).orElse("");
    }

    /**
     * The name a body of a citizen shows when it governs a city of a kingdom of players: with its office by the level of the
     * city (an elder, a mayor, a governor), or as a baron; empty when it governs nothing.
     */
    public static Optional<Component> title(MinecraftServer server, Citizen citizen, Component name) {
        if (RoadWork.isReadOnly(server) || NobilityWork.isReadOnly(server)) {
            return Optional.empty();
        }
        if (NobilityWork.state(server).houseOf(citizen.id()).isPresent()) {
            return Optional.of(Component.translatable(io.github.sekerator.kingdomworld.common.npc.SettlementHeads.KEY + "baron", name));
        }
        Optional<CityCharter> charter = RoadWork.state(server).mayoralty(citizen.id());
        if (charter.isEmpty()) {
            return Optional.empty();
        }
        CityLevel level = CityWalls.level(server, charter.get().kingdomId(), charter.get().cityId());
        return Optional.of(Component.translatable(io.github.sekerator.kingdomworld.common.npc.SettlementHeads.KEY + level.id(), name));
    }

    /**
     * Shows the office of a citizen over the head of its body, if it has one in the world now.
     */
    static void retitle(MinecraftServer server, UUID citizenId) {
        CitizenEntity body = CitizenBodies.body(citizenId);
        if (body == null) {
            return;
        }
        KingdomWorldApi.state(server).citizens().get(citizenId).ifPresent(citizen -> {
            Level level = body.level();
            body.setCustomName(io.github.sekerator.kingdomworld.common.npc.SettlementHeads.title(level, citizen, CitizenTexts.name(citizen)));
        });
    }

    private static String cityArg(UUID kingdomId, long cityId) {
        return cityId > 0 ? Note.fief(Fief.city(cityId)) : Note.kingdom(kingdomId);
    }

    private static void write(MinecraftServer server, UUID kingdomId, String key, String... args) {
        if (NobilityWork.isReadOnly(server)) {
            return;
        }
        NobilityWork.state(server).write(kingdomId, new Note(NobilityWork.day(server), key, List.of(args)));
    }

    private static void tell(MinecraftServer server, UUID kingdomId, Component message) {
        KingdomWorldApi.kingdom(server, kingdomId).ifPresent(kingdom -> KingdomActions.notifyMembers(server, kingdom,
                Permission.MANAGE_CONSTRUCTION, message));
    }

    static void summary(UUID kingdomId, Component line) {
        SUMMARY.computeIfAbsent(kingdomId, key -> new ArrayList<>()).add(line);
    }

    /**
     * Tells every kingdom what its governors did in the round, in one message to those who run its construction.
     */
    static void flush(MinecraftServer server) {
        for (Map.Entry<UUID, List<Component>> entry : SUMMARY.entrySet()) {
            MutableComponent lines = Component.empty();
            for (int i = 0; i < entry.getValue().size(); i++) {
                if (i > 0) {
                    lines.append("; ");
                }
                lines.append(entry.getValue().get(i));
            }
            tell(server, entry.getKey(), Component.translatable(KEY + "summary", lines).withStyle(ChatFormatting.GOLD));
        }
        SUMMARY.clear();
    }

    /**
     * Forgets everything kept in memory, when the server stops.
     */
    public static void reset() {
        cursor = 0;
        seconds = 0;
        MOVED.clear();
        BLOCKED.clear();
        SUMMARY.clear();
    }
}
