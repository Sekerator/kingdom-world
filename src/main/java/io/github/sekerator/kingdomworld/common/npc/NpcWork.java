package io.github.sekerator.kingdomworld.common.npc;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcNames;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The kingdoms of the world on the server: their data, the plan of their places, their founding as data, the raising of their
 * buildings in the world and the turns of their rulers. Other parts of the mod ask here whether a kingdom is one of the world.
 */
public final class NpcWork {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Blocks between two places the search for land of an operator's kingdom looks at. */
    private static final int SEARCH_STEP = 64;
    /** Ticks between two looks for players near planned places. */
    private static final int TRIGGER_TICKS = 20;
    /** Names of the kingdoms of the world in both alphabets, for texts made without the server at hand. */
    private static final Map<UUID, NpcNames.Name> NAMES = new ConcurrentHashMap<>();
    /** Names of the towns of the kingdoms of the world in both alphabets, by the Latin form (the name of the city). */
    private static final Map<String, NpcNames.Name> TOWNS = new ConcurrentHashMap<>();
    /** Grows whenever the names of the kingdoms of the world or of their towns change. */
    private static final java.util.concurrent.atomic.AtomicLong NAMES_VERSION = new java.util.concurrent.atomic.AtomicLong();
    private static long lastDay = Long.MIN_VALUE;

    private NpcWork() {
    }

    public static NpcState state(MinecraftServer server) {
        return NpcSavedData.get(server).state();
    }

    public static boolean isReadOnly(MinecraftServer server) {
        return NpcSavedData.get(server).isReadOnly();
    }

    /**
     * Runs a change of the data of the kingdoms of the world, refusing it while the data is read-only.
     */
    public static Outcome change(MinecraftServer server, String what, Function<NpcState, Outcome> change) {
        NpcSavedData data = NpcSavedData.get(server);
        if (data.isReadOnly()) {
            LOGGER.error("Refused to {}: the data of the kingdoms of the world is read-only ({})", what, data.readOnlyReason().orElse("?"));
            return Outcome.fail(KingdomError.READ_ONLY);
        }
        return change.apply(data.state());
    }

    /**
     * Whether a kingdom is one of the world, ruled by no player.
     */
    public static boolean isNpc(MinecraftServer server, UUID kingdomId) {
        return kingdomId != null && state(server).isNpc(kingdomId);
    }

    public static Optional<NpcKingdom> kingdom(MinecraftServer server, UUID kingdomId) {
        return kingdomId == null ? Optional.empty() : state(server).kingdom(kingdomId);
    }

    /**
     * The name of a kingdom of the world in both alphabets, or empty for kingdoms of players; works without the server, from
     * what the server last knew.
     */
    public static Optional<NpcNames.Name> name(UUID kingdomId) {
        return kingdomId == null ? Optional.empty() : Optional.ofNullable(NAMES.get(kingdomId));
    }

    public static void remember(NpcKingdom kingdom) {
        NAMES.put(kingdom.kingdomId(), kingdom.name());
        kingdom.towns().forEach(town -> TOWNS.put(town.latin(), town));
        NAMES_VERSION.incrementAndGet();
    }

    static void forgetName(UUID kingdomId) {
        NAMES.remove(kingdomId);
        NAMES_VERSION.incrementAndGet();
    }

    /**
     * Grows whenever the names {@link #localCity} and {@link #localKingdom} give may change, so that what was named with them
     * is named again only then.
     */
    public static long namesVersion() {
        return NAMES_VERSION.get();
    }

    /**
     * The name of a city in the language of a player: a town of a kingdom of the world in its Cyrillic form for Russian, any other
     * name as it is.
     */
    public static String localCity(String name, String language) {
        NpcNames.Name town = TOWNS.get(name);
        if (town == null) {
            return name;
        }
        return io.github.sekerator.kingdomworld.core.citizen.CitizenNames.alphabetFor(language)
                == io.github.sekerator.kingdomworld.core.citizen.CitizenNames.Alphabet.CYRILLIC ? town.cyrillic() : town.latin();
    }

    /**
     * The name of a kingdom in the language of a player: a kingdom of the world in its Cyrillic form for Russian.
     */
    public static String localKingdom(UUID kingdomId, String name, String language) {
        NpcNames.Name both = kingdomId == null ? null : NAMES.get(kingdomId);
        if (both == null) {
            return name;
        }
        return io.github.sekerator.kingdomworld.core.citizen.CitizenNames.alphabetFor(language)
                == io.github.sekerator.kingdomworld.core.citizen.CitizenNames.Alphabet.CYRILLIC ? both.cyrillic() : both.latin();
    }

    /**
     * The name of a city as a text in both alphabets when it is a town of a kingdom of the world.
     */
    public static Optional<NpcNames.Name> town(String name) {
        return Optional.ofNullable(TOWNS.get(name));
    }

    static void reset() {
        NAMES.clear();
        TOWNS.clear();
        NAMES_VERSION.incrementAndGet();
        lastDay = Long.MIN_VALUE;
        NpcPlanning.reset();
        NpcFounding.reset();
        NpcSpawner.reset();
        NpcRulers.reset();
        NpcCamps.reset();
    }

    static void loaded(MinecraftServer server) {
        NAMES.clear();
        TOWNS.clear();
        NAMES_VERSION.incrementAndGet();
        state(server).kingdoms().forEach(NpcWork::remember);
    }

    // ---------------------------------------------------------------- every tick

    static void tick(MinecraftServer server) {
        if (isReadOnly(server) || KingdomWorldApi.isReadOnly(server)) {
            return;
        }
        // The appearance of the kingdoms of the world, its plan and its founding are measured as one work.
        long started = System.nanoTime();
        NpcSpawner.tick(server);
        NpcPlanning.tick(server);
        NpcFounding.tick(server);
        io.github.sekerator.kingdomworld.common.command.PerfCommands.countWork(System.nanoTime() - started);
        if (server.getTickCount() % TRIGGER_TICKS == 0) {
            NpcBackground.tick(server);
            keepInStep(server);
            long day = TreasuryActions.day(server);
            if (day != lastDay) {
                lastDay = day;
                NpcRulers.due(server, day);
            }
        }
        NpcRulers.tick(server);
    }

    /**
     * Whether the buildings of a kingdom of the world stand in the world; a kingdom founded as data whose place is not raised
     * yet lives on the map only.
     */
    public static boolean built(MinecraftServer server, UUID kingdomId) {
        NpcState state = state(server);
        return state.kingdom(kingdomId).flatMap(kingdom -> state.site(kingdom.siteId()))
                .map(site -> site.status() == NpcSite.Status.SPAWNED).orElse(true);
    }

    /**
     * The citizens a kingdom of the world founded as data will have once its buildings stand, or empty for a kingdom whose
     * buildings stand (or of players).
     */
    public static Optional<Integer> plannedPeople(MinecraftServer server, UUID kingdomId) {
        NpcState state = state(server);
        return state.kingdom(kingdomId).flatMap(kingdom -> state.site(kingdom.siteId()))
                .filter(site -> site.status() == NpcSite.Status.FOUNDED || site.status() == NpcSite.Status.SPAWNING)
                .map(site -> NpcSpawner.composition(site).citizens());
    }

    /**
     * The radius the plan of a kingdom of the world gives the wall of its capital or of its towns, in blocks: that of a capital
     * laid out in districts once its buildings are raised or being raised, whether its wall stands yet or not; 0 for a kingdom of
     * players, a kingdom of the first layout, one that waits for its buildings, and towns without a palisade.
     *
     * @param capital whether the capital is asked for, or a town
     */
    public static int planRadius(MinecraftServer server, UUID kingdomId, boolean capital) {
        NpcState state = state(server);
        return state.kingdom(kingdomId).flatMap(kingdom -> state.site(kingdom.siteId()))
                .filter(site -> site.districts() && (site.status() == NpcSite.Status.SPAWNED || site.status() == NpcSite.Status.SPAWNING))
                .map(site -> capital ? io.github.sekerator.kingdomworld.core.npc.NpcComposition.districtWall(site.level())
                        : io.github.sekerator.kingdomworld.core.npc.NpcComposition.districtTownWall(site.level()))
                .orElse(0);
    }

    /**
     * Founds a planned place as data at once, for operators and game tests.
     *
     * @return whether the kingdom stands as data
     */
    public static boolean foundNow(MinecraftServer server, NpcSite site) {
        return !isReadOnly(server) && site.status() == NpcSite.Status.PLANNED && NpcFounding.foundNow(server, site);
    }

    /**
     * Forgets the kingdoms of the world that are gone (an operator deleted the kingdom, for example).
     */
    private static void keepInStep(MinecraftServer server) {
        NpcState state = state(server);
        List<UUID> gone = new ArrayList<>();
        for (NpcKingdom kingdom : state.kingdoms()) {
            if (KingdomWorldApi.kingdom(server, kingdom.kingdomId()).isEmpty()) {
                gone.add(kingdom.kingdomId());
            }
        }
        for (UUID id : gone) {
            NpcKingdom kingdom = state.removeKingdom(id).orElseThrow();
            forgetName(id);
            state.site(kingdom.siteId()).ifPresent(site -> {
                if (site.status() == NpcSite.Status.SPAWNING) {
                    NpcSpawner.abandon(server, site);
                }
                if (site.status() == NpcSite.Status.SPAWNED || site.status() == NpcSite.Status.FOUNDED
                        || site.status() == NpcSite.Status.SPAWNING) {
                    site.setStatus(NpcSite.Status.REMOVED);
                }
            });
            LOGGER.info("The kingdom of the world {} is gone; its record is forgotten", kingdom.name().latin());
        }
    }

    // ---------------------------------------------------------------- operators

    /**
     * Adds a place to the plan where an operator wants it and starts its appearance at once (when no other one is appearing).
     *
     * @param towns whether the kingdom gets the towns of its size, on land that suits them
     * @return the place, or empty if the data is read-only
     */
    public static Optional<NpcSite> plant(MinecraftServer server, int x, int z, io.github.sekerator.kingdomworld.core.npc.NpcLevel level,
                                          boolean towns) {
        if (isReadOnly(server)) {
            return Optional.empty();
        }
        NpcState state = state(server);
        long seed = server.overworld().getSeed() * 31 + x * 341_873_128_712L + z * 132_897_987_541L;
        List<int[]> chosen = new ArrayList<>();
        if (towns) {
            int wanted = NpcSettings.rules().towns(level);
            int minCity = Math.max(64, io.github.sekerator.kingdomworld.common.road.RoadSettings.rules().minCityDistance());
            List<int[]> candidates = new ArrayList<>();
            for (int[] town : io.github.sekerator.kingdomworld.core.npc.SitePlanner.townCandidates(x, z, seed,
                    NpcCity.townDistance(level, NpcSite.LAYOUT_DISTRICTS))) {
                candidates.add(NpcPlanning.centred(town));
            }
            chosen = io.github.sekerator.kingdomworld.core.npc.SitePlanner.chooseTowns(candidates,
                    point -> !NpcPlanning.nearKingdom(server, point[0], point[1], 64)
                            && NpcPlanning.suitable(server.overworld(), point[0], point[1], 28, false), wanted, Math.max(minCity, 112));
        }
        return plantAt(server, x, z, level, chosen);
    }

    /**
     * Adds a place to the plan with the towns given without raising it: it is founded as data like a place of the plan; for game
     * tests.
     */
    public static Optional<NpcSite> planAt(MinecraftServer server, int x, int z, io.github.sekerator.kingdomworld.core.npc.NpcLevel level,
                                           List<int[]> towns) {
        if (isReadOnly(server)) {
            return Optional.empty();
        }
        long seed = server.overworld().getSeed() * 31 + x * 341_873_128_712L + z * 132_897_987_541L;
        return Optional.of(NpcPlanning.addSite(server, x, z, level, seed, towns));
    }

    /**
     * Adds a place of the first layout (a grid of plots around the square of the throne) to the plan without raising it, as
     * worlds made before the districts have them; for game tests.
     */
    public static Optional<NpcSite> planGridAt(MinecraftServer server, int x, int z, io.github.sekerator.kingdomworld.core.npc.NpcLevel level,
                                               List<int[]> towns) {
        if (isReadOnly(server)) {
            return Optional.empty();
        }
        long seed = server.overworld().getSeed() * 31 + x * 341_873_128_712L + z * 132_897_987_541L;
        return Optional.of(state(server).addSite(x, z, level, seed, NpcSite.towns(towns)));
    }

    /**
     * Adds a place to the plan with the towns given and starts its appearance at once (when no other one is appearing); for
     * operators and game tests.
     */
    public static Optional<NpcSite> plantAt(MinecraftServer server, int x, int z, io.github.sekerator.kingdomworld.core.npc.NpcLevel level,
                                            List<int[]> towns) {
        if (isReadOnly(server)) {
            return Optional.empty();
        }
        NpcState state = state(server);
        long seed = server.overworld().getSeed() * 31 + x * 341_873_128_712L + z * 132_897_987_541L;
        NpcSite site = NpcPlanning.addSite(server, x, z, level, seed, towns);
        if (state.spawning().isEmpty()) {
            NpcSpawner.start(server, site);
        }
        LOGGER.info("An operator planted a kingdom of the world: {}", site);
        return Optional.of(site);
    }

    /**
     * Removes a kingdom of the world: its kingdom with land, citizens and treasury goes and its place is marked removed.
     *
     * @param buildings whether its buildings, throne, town halls and wall are taken down as well
     */
    public static Outcome remove(MinecraftServer server, int siteId, boolean buildings) {
        if (isReadOnly(server)) {
            return Outcome.fail(KingdomError.READ_ONLY);
        }
        NpcState state = state(server);
        Optional<NpcSite> found = state.site(siteId);
        if (found.isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        NpcSite site = found.get();
        NpcSpawner.abandon(server, site);
        Optional<UUID> kingdomId = site.kingdomId();
        if (kingdomId.isPresent()) {
            if (buildings) {
                NpcRemoval.takeDown(server, site, kingdomId.get());
            }
            if (KingdomWorldApi.kingdom(server, kingdomId.get()).isPresent()) {
                KingdomWorldApi.dissolveKingdom(server, kingdomId.get());
            }
            state.removeKingdom(kingdomId.get());
            forgetName(kingdomId.get());
        }
        site.setStatus(NpcSite.Status.REMOVED);
        LOGGER.info("An operator removed the kingdom of the world of {}{}", site, buildings ? " with its buildings" : "");
        return Outcome.DONE;
    }

    /**
     * The nearest point to a place whose land suits a capital, looking in rings up to a distance; the place itself if none does.
     */
    public static int[] nearestSuitable(MinecraftServer server, int x, int z, int maxDistance) {
        return nearestSuitable(server, x, z, maxDistance, io.github.sekerator.kingdomworld.core.npc.NpcLevel.SMALL);
    }

    /**
     * The nearest point to a place whose land suits a capital of a level; the place itself if none does within the distance.
     */
    public static int[] nearestSuitable(MinecraftServer server, int x, int z, int maxDistance,
                                        io.github.sekerator.kingdomworld.core.npc.NpcLevel kingdom) {
        ServerLevel level = server.overworld();
        int land = NpcCity.landRadius(kingdom, NpcSite.LAYOUT_DISTRICTS) * 16 + 16;
        // The plan asks less of a place on its later passes; an operator takes the best place near where it stands.
        for (int relax = 0; relax <= 2; relax++) {
            if (NpcPlanning.suitableCapital(level, x, z, kingdom, relax)) {
                return new int[]{x, z};
            }
            for (int ring = 1; ring * SEARCH_STEP <= maxDistance; ring++) {
                for (int i = 0; i < 8 * ring; i++) {
                    double angle = i * Math.PI * 2 / (8 * ring);
                    int cx = x + (int) Math.round(Math.cos(angle) * ring * SEARCH_STEP);
                    int cz = z + (int) Math.round(Math.sin(angle) * ring * SEARCH_STEP);
                    if (!NpcPlanning.nearKingdom(server, cx, cz, land) && NpcPlanning.suitableCapital(level, cx, cz, kingdom, relax)) {
                        return new int[]{cx, cz};
                    }
                }
            }
        }
        return new int[]{x, z};
    }

    /**
     * Whether the land around a point suits a capital, judged as the plan judges it; for the showcase of the client.
     */
    public static boolean suitableLand(ServerLevel level, int x, int z) {
        return suitableLand(level, x, z, io.github.sekerator.kingdomworld.core.npc.NpcLevel.SMALL);
    }

    /**
     * Whether the land around a point suits a capital of a level, judged as the plan judges it on its second pass.
     */
    public static boolean suitableLand(ServerLevel level, int x, int z, io.github.sekerator.kingdomworld.core.npc.NpcLevel kingdom) {
        return NpcPlanning.suitableCapital(level, x, z, kingdom, 1);
    }

    // ---------------------------------------------------------------- texts
}
