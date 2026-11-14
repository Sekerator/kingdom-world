package io.github.sekerator.kingdomworld.common.road;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.economy.EconomySettings;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.production.Production;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.production.Reservations;
import io.github.sekerator.kingdomworld.core.road.ColumnWork;
import io.github.sekerator.kingdomworld.core.road.Crossing;
import io.github.sekerator.kingdomworld.core.road.EarthRules;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadPayment;
import io.github.sekerator.kingdomworld.core.road.RoadClass;
import io.github.sekerator.kingdomworld.core.road.RoadLayout;
import io.github.sekerator.kingdomworld.core.road.RoadRules;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Roads being built and kept up. Builders of a kingdom who have no construction within reach of their post work on its
 * roads: every second each pays for blocks of the first section waiting for it, at the pace of its class, from the
 * storehouses (what other work set aside stays there). The paid items wait in the section; a block is placed in the world
 * as soon as its place is loaded, so far from players a section counts as built once it is paid for. Once a game day the
 * built sections wear; a repair ordered in the tab is paid like building, with a share of the materials.
 */
public final class RoadBuilding {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Blocks of the world roads change a second at most, in all. */
    private static final int PLACE_BUDGET = 512;
    /** Time the roads take to change the world in a second at most. */
    private static final long PLACE_NANOS = 3_000_000L;
    /** Work carried over between seconds, by kingdom, in seconds of a builder. */
    private static final Map<UUID, Double> CARRY = new HashMap<>();
    /** Builders of each kingdom seen working on roads in the last second. */
    private static final Map<UUID, Integer> CREWS = new HashMap<>();
    /** Seconds of builders a second each kingdom had for its roads in the last second. */
    private static final Map<UUID, Double> RATES = new HashMap<>();
    /** Sections of levelled roads whose next column waits for materials, by road and section. */
    private static final Set<Long> WAITING = new HashSet<>();

    private RoadBuilding() {
    }

    static void reset() {
        CARRY.clear();
        CREWS.clear();
        RATES.clear();
        WAITING.clear();
        RoadCrews.reset();
        RoadEarthworks.reset();
    }

    /**
     * Seconds of builders a second a kingdom had for its roads in the last second, before the share of far constructions.
     */
    public static double builderRate(UUID kingdomId) {
        return RATES.getOrDefault(kingdomId, 0.0);
    }

    /**
     * Whether the next column of a section of a levelled road waits for materials.
     */
    public static boolean waitingForMaterials(Road road, Road.Section section) {
        return WAITING.contains(waitKey(road, section));
    }

    private static long waitKey(Road road, Road.Section section) {
        return road.id() * 65_536L + section.index();
    }

    /**
     * Builders of a kingdom at work on its roads in the last second.
     */
    public static int crew(UUID kingdomId) {
        return CREWS.getOrDefault(kingdomId, 0);
    }

    static UUID holdKey(Road road) {
        return UUID.nameUUIDFromBytes(("kingdomworld:road:" + road.id()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Kingdoms that agreed to the road crossing their land.
     */
    public static Set<UUID> consented(io.github.sekerator.kingdomworld.core.road.RoadState roads, Road road) {
        Set<UUID> result = new HashSet<>();
        for (Crossing crossing : roads.crossingsOfRoad(road.id())) {
            if (crossing.status() == Crossing.Status.ACCEPTED) {
                result.add(crossing.owner());
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- builders

    /**
     * Builders of a kingdom free for roads: alive, not wounded, at a post on its own land with no construction in reach.
     */
    public static List<Citizen> freeBuilders(MinecraftServer server, UUID kingdomId) {
        List<Citizen> result = new ArrayList<>();
        for (Citizen citizen : KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdomId)) {
            if (!citizen.isAlive() || citizen.state() == CitizenState.WOUNDED
                    || !Professions.BUILDER.id().equals(citizen.professionId())) {
                continue;
            }
            Optional<WorkPost> post = PostWork.postOf(server, citizen);
            if (post.isEmpty() || !PostWork.onOwnLand(server, post.get()) || !ConstructionWork.candidates(server, post.get()).isEmpty()) {
                continue;
            }
            result.add(citizen);
        }
        return result;
    }

    /**
     * Sections a kingdom builds or repairs now, the oldest road first.
     */
    static List<Object[]> work(io.github.sekerator.kingdomworld.core.road.RoadState roads, UUID kingdomId) {
        List<Object[]> result = new ArrayList<>();
        for (Road road : roads.roads()) {
            if (road.status() == Road.Status.WAITING_CONSENT || road.paused()) {
                continue;
            }
            for (Road.Section section : road.sections()) {
                if (section.builder().equals(kingdomId) && (!section.done() || section.repairing())) {
                    result.add(new Object[]{road, section});
                }
            }
        }
        return result;
    }

    public static void tick(MinecraftServer server) {
        io.github.sekerator.kingdomworld.core.road.RoadState roads = RoadWork.state(server);
        RoadRules rules = RoadSettings.rules();
        CREWS.clear();
        Set<UUID> kingdoms = new HashSet<>();
        for (Road road : roads.roads()) {
            if (road.status() != Road.Status.WAITING_CONSENT && !road.paused()) {
                for (Road.Section section : road.sections()) {
                    if (!section.done() || section.repairing()) {
                        kingdoms.add(section.builder());
                    }
                }
            }
        }
        Set<UUID> walling = new HashSet<>();
        for (io.github.sekerator.kingdomworld.core.road.Wall wall : roads.walls()) {
            if (!wall.allPaid()) {
                kingdoms.add(wall.kingdomId());
                walling.add(wall.kingdomId());
            }
        }
        // Kingdoms with constructions no post may reach: their free builders help there.
        Set<UUID> building = new HashSet<>();
        for (io.github.sekerator.kingdomworld.core.construction.Construction construction : KingdomWorldApi.state(server).construction().all()) {
            if (!construction.paused()) {
                building.add(construction.kingdomId());
            }
        }
        kingdoms.addAll(building);
        io.github.sekerator.kingdomworld.common.construction.RemoteCrews.startSecond();
        long now = KingdomWorldApi.gameTime(server);
        Set<UUID> paying = new HashSet<>();
        for (UUID kingdom : kingdoms) {
            // Counted once a second for the helpers of far constructions and for the roads alike.
            List<Citizen> builders = freeBuilders(server, kingdom);
            if (builders.isEmpty()) {
                continue;
            }
            List<Object[]> jobs = work(roads, kingdom);
            boolean roadsWait = !jobs.isEmpty() || walling.contains(kingdom);
            double left = building.contains(kingdom)
                    ? io.github.sekerator.kingdomworld.common.construction.RemoteCrews.work(server, kingdom, builders, roadsWait, now) : 1.0;
            if (!roadsWait) {
                continue;
            }
            CREWS.put(kingdom, builders.size());
            double seconds = CARRY.getOrDefault(kingdom, 0.0);
            double rate = 0;
            for (Citizen builder : builders) {
                rate += EconomySettings.workSpeed(builder.mood()) * left;
            }
            RATES.put(kingdom, rate);
            seconds += rate;
            EarthRules earth = RoadSettings.earth();
            paying.add(kingdom);
            // The miners are counted only while a levelled road of the kingdom has rock to pay for.
            double offered = rockWaiting(roads, jobs) ? RoadCrews.offer(server, kingdom, earth) : 0.0;
            RoadPayment.Budget budget = new RoadPayment.Budget(seconds, offered);
            int blocks = 0;
            int levelled = 0;
            for (Object[] job : jobs) {
                Road road = (Road) job[0];
                Road.Section section = (Road.Section) job[1];
                if (road.levelled() && !(section.repairing() && section.done())) {
                    int paid = payLevelled(server, road, section, budget, rules, earth);
                    levelled += paid;
                    blocks += paid;
                    if (budget.builders <= 1e-6 && budget.miners <= 1e-6) {
                        break;
                    }
                    continue;
                }
                double per = rules.secondsPerBlock(road.roadClass());
                while (budget.builders >= per) {
                    boolean paid = section.repairing() && section.done() ? payRepair(server, road, section, rules) : payBlock(server, road, section);
                    if (!paid) {
                        break;
                    }
                    budget.builders -= per;
                    budget.buildersSpent += per;
                    blocks++;
                    if (section.done() && !section.repairing()) {
                        break;
                    }
                }
                if (budget.builders < per) {
                    break;
                }
            }
            seconds = budget.builders;
            if (offered > 0) {
                RoadCrews.settle(kingdom, offered, budget.minersSpent);
                RoadCrews.wear(server, kingdom, budget.minersSpent, earth);
            } else {
                RoadCrews.idle(kingdom);
            }
            double beforeWalls = seconds;
            seconds = CityWalls.work(server, kingdom, seconds);
            blocks += (int) Math.round((beforeWalls - seconds) / rules.secondsPerBlock(RoadClass.PAVED));
            // A crew without materials does not save up work for later.
            CARRY.put(kingdom, Math.min(seconds, rules.secondsPerBlock(RoadClass.PAVED) * builders.size()));
            if (blocks > 0) {
                // The experience is shared between builders and miners by the seconds each gave.
                double all = budget.buildersSpent + budget.minersSpent;
                double minerPart = levelled > 0 && all > 0 ? budget.minersSpent / all : 0.0;
                int builderShare = Math.max(1, (int) Math.round(blocks * (1.0 - minerPart) / builders.size()));
                for (Citizen builder : builders) {
                    builder.addExperience(builderShare);
                }
                int helpers = RoadCrews.helpers(kingdom);
                if (minerPart > 0 && helpers > 0) {
                    RoadCrews.experience(kingdom, server, Math.max(1, (int) Math.round(blocks * minerPart / helpers)));
                }
                roads.changed();
            }
        }
        RoadCrews.idleExcept(paying);
        for (Road road : roads.roads()) {
            boolean wasBuilt = road.status() == Road.Status.DONE;
            if (road.status() == Road.Status.BUILDING && road.built()) {
                road.setStatus(Road.Status.DONE);
                roads.changed();
                LOGGER.info("Road {} of {} is built ({} blocks)", road.id(), RoadWork.kingdomName(server, road.kingdomId()).getString(),
                        road.length());
                RoadWork.notify(server, road.kingdomId(), Component.translatable(RoadWork.KEY + "road_built", road.id(),
                        RoadTexts.blocks(road.length())).withStyle(ChatFormatting.GREEN));
                RoadSync.changed();
                RoadSpeed.invalidate();
            } else if (wasBuilt && !road.built()) {
                road.setStatus(Road.Status.BUILDING);
            }
        }
        placeLoaded(server);
    }

    /**
     * Pays for the next block of a section: every item it takes, all or none; for a levelled road its work too, at once.
     */
    public static boolean payNow(MinecraftServer server, Road road, Road.Section section) {
        if (road.levelled() && !section.done()) {
            RoadLayout.Block block = road.blocksOf(section).get(section.paid());
            Choice items = chooseLevelled(server, road, section, block.needs(road.roadClass()));
            return items != null && completeLevelled(server, road, section, block, items, RoadSettings.rules(), RoadSettings.earth());
        }
        return payBlock(server, road, section);
    }

    // ---------------------------------------------------------------- levelled roads

    /**
     * Whether a section of a levelled road among the work of a kingdom has rock left to pay for.
     */
    static boolean rockWaiting(io.github.sekerator.kingdomworld.core.road.RoadState roads, List<Object[]> jobs) {
        for (Object[] job : jobs) {
            Road road = (Road) job[0];
            Road.Section section = (Road.Section) job[1];
            if (!road.levelled() || section.done()) {
                continue;
            }
            List<RoadLayout.Block> blocks = road.blocksOf(section);
            for (int i = section.paid(); i < blocks.size(); i++) {
                RoadLayout.ColumnSurvey survey = blocks.get(i).survey();
                if (survey != null && !survey.onRoad() && survey.rock() > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The items for a column of a levelled road: embankments and lining from what the road digs where it can, the rest from the
     * storehouses.
     *
     * @param credit  items of the credit of the road, by item id
     * @param storage items of the storehouses, by item id
     */
    record Choice(Map<String, Integer> credit, Map<String, Integer> storage) {
    }

    /**
     * Picks the items for the next column of a levelled road; {@code null} if one is missing.
     */
    static Choice chooseLevelled(MinecraftServer server, Road road, Road.Section section, List<RoadLayout.Material> needs) {
        Map<String, Integer> fromCredit = new LinkedHashMap<>();
        List<RoadLayout.Material> rest = new ArrayList<>();
        // Only the road's own kingdom digs into its credit: a kingdom building its part of a joint road pays its own way.
        Map<String, Integer> left = section.builder().equals(road.kingdomId()) ? new LinkedHashMap<>(road.credit()) : Map.of();
        for (RoadLayout.Material material : needs) {
            String found = null;
            if (material == RoadLayout.Material.FILL || material == RoadLayout.Material.LINING) {
                for (Map.Entry<String, Integer> entry : left.entrySet()) {
                    Item item = PostWork.item(entry.getKey());
                    if (entry.getValue() > 0 && item != null && RoadBlocks.fits(item, material, road.roadClass())) {
                        found = entry.getKey();
                        break;
                    }
                }
            }
            if (found != null) {
                left.merge(found, -1, Integer::sum);
                fromCredit.merge(found, 1, Integer::sum);
            } else {
                rest.add(material);
            }
        }
        Map<String, Integer> fromStorage = choose(server, road, section.builder(), rest);
        return fromStorage == null ? null : new Choice(fromCredit, fromStorage);
    }

    /**
     * Pays what the seconds of the kingdom allow for the next columns of a section of a levelled road.
     *
     * @return columns paid for
     */
    static int payLevelled(MinecraftServer server, Road road, Road.Section section, RoadPayment.Budget budget, RoadRules rules,
                           EarthRules earth) {
        int paid = 0;
        while (!section.done() && (budget.builders > 1e-6 || budget.miners > 1e-6)) {
            RoadLayout.Block block = road.blocksOf(section).get(section.paid());
            ColumnWork work = ColumnWork.of(block, road.roadClass(), rules, earth);
            Choice items = chooseLevelled(server, road, section, block.needs(road.roadClass()));
            if (items == null) {
                // Its items cannot all be had now: the workers go on to the next section.
                WAITING.add(waitKey(road, section));
                return paid;
            }
            WAITING.remove(waitKey(road, section));
            if (!RoadPayment.pay(section, work, budget, earth.rockByBuilders())) {
                return paid;
            }
            if (!completeLevelled(server, road, section, block, items, rules, earth)) {
                WAITING.add(waitKey(road, section));
                return paid;
            }
            paid++;
        }
        return paid;
    }

    /**
     * Takes the items of a column whose work is given, all or none, and marks it paid: what it will dig out is counted for the
     * embankments of the road, its earthworks done.
     */
    static boolean completeLevelled(MinecraftServer server, Road road, Road.Section section, RoadLayout.Block block, Choice items,
                                    RoadRules rules, EarthRules earth) {
        if (!items.storage().isEmpty() && !take(server, road, section.builder(), items.storage(), block)) {
            return false;
        }
        Map<String, Integer> all = new LinkedHashMap<>(items.storage());
        items.credit().forEach((item, count) -> {
            int used = road.useCredit(item, count);
            if (used > 0) {
                all.merge(item, used, Integer::sum);
            }
        });
        section.markPaid(all);
        RoadLayout.ColumnSurvey survey = block.survey();
        if (survey != null && !survey.onRoad() && section.builder().equals(road.kingdomId())) {
            road.addCredit(SOIL_ITEM, survey.soil());
            road.addCredit(ROCK_ITEM, survey.rock());
        }
        road.addEarthDone(ColumnWork.earthwork(block, road.roadClass(), rules, earth));
        return true;
    }

    /** What a block of soil and of rock dug out of a road gives for its embankments. */
    static final String SOIL_ITEM = io.github.sekerator.kingdomworld.common.construction.Earthworks.fillItem(
            net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
    static final String ROCK_ITEM = io.github.sekerator.kingdomworld.common.construction.Earthworks.fillItem(
            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());

    /**
     * Settles the credit of a levelled road laid in full or given up: what was dug and not used goes to the storehouses, what was
     * used and never dug is taken from them as far as they have it; the rest is written in the log.
     */
    static void settleCredit(MinecraftServer server, Road road) {
        if (!road.levelled() || road.credit().isEmpty() && road.owed().isEmpty()) {
            return;
        }
        RoadPayment.Settlement settlement = RoadPayment.settle(road.takeCredit(), road.takeOwed());
        ServerLevel level = ConstructionWork.level(server, road.dimension());
        RouteCell first = road.cells().get(0);
        BlockPos at = new BlockPos(first.x(), first.y(), first.z());
        if (level != null && KingdomWorldApi.kingdom(server, road.kingdomId()).isPresent()) {
            RoadBlocks.giveBack(level, road.kingdomId(), at, settlement.toStorage());
        }
        boolean alive = KingdomWorldApi.kingdom(server, road.kingdomId()).isPresent();
        settlement.shortfall().forEach((item, count) -> {
            int left = count;
            int have = alive ? (int) Math.min(count, KingdomStorage.countsById(server, road.kingdomId()).getOrDefault(item, 0L)) : 0;
            if (have > 0) {
                List<KingdomStorage.Take> takes = KingdomStorage.plan(server, road.kingdomId(), Map.of(item, have), id -> 0, null);
                if (takes != null && KingdomStorage.takePlanned(server, takes) != null) {
                    left -= have;
                }
            }
            if (left > 0) {
                LOGGER.info("Road {} of {} used {} {} counted from what it would dig and never dug it; the storehouses had none left",
                        road.id(), RoadWork.kingdomName(server, road.kingdomId()).getString(), left, item);
            }
        });
    }

    static boolean payBlock(MinecraftServer server, Road road, Road.Section section) {
        if (section.done()) {
            return false;
        }
        RoadLayout.Block block = road.blocksOf(section).get(section.paid());
        List<RoadLayout.Material> needs = block.needs(road.roadClass());
        Map<String, Integer> items = choose(server, road, section.builder(), needs);
        if (items == null) {
            return false;
        }
        if (!items.isEmpty() && !take(server, road, section.builder(), items, block)) {
            return false;
        }
        section.markPaid(items);
        return true;
    }

    /**
     * Pays for a block of a repair: one item of the road itself, or for a path nothing but the work.
     */
    static boolean payRepair(MinecraftServer server, Road road, Road.Section section, RoadRules rules) {
        int needed = Math.max(1, (int) Math.ceil(section.units() * rules.repairShare()));
        Map<String, Integer> items = road.roadClass() == RoadClass.PATH ? Map.of()
                : choose(server, road, section.builder(), List.of(RoadLayout.Material.SURFACE));
        if (items == null) {
            return false;
        }
        RoadLayout.Block block = road.blocksOf(section).isEmpty() ? null : road.blocksOf(section).get(0);
        if (!items.isEmpty() && !take(server, road, section.builder(), items, block)) {
            return false;
        }
        // The items of a repair go into the road itself.
        if (section.repairStep(needed)) {
            RoadWork.notify(server, road.kingdomId(), Component.translatable(RoadWork.KEY + "repaired", road.id(), section.index() + 1));
        }
        return true;
    }

    /**
     * Picks items of the storehouses for materials, the most plentiful first; {@code null} if one is missing.
     */
    static Map<String, Integer> choose(MinecraftServer server, Road road, UUID kingdomId, List<RoadLayout.Material> needs) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (needs.isEmpty()) {
            return result;
        }
        Map<String, Long> stock = KingdomStorage.countsById(server, kingdomId);
        Reservations reservations = Production.reservations();
        UUID key = holdKey(road);
        for (RoadLayout.Material material : needs) {
            String best = null;
            long bestCount = 0;
            for (Map.Entry<String, Long> entry : stock.entrySet()) {
                Item item = PostWork.item(entry.getKey());
                if (item == null || !RoadBlocks.fits(item, material, road.roadClass())) {
                    continue;
                }
                long free = entry.getValue() - reservations.reserved(kingdomId, entry.getKey(), key) - result.getOrDefault(entry.getKey(), 0);
                if (free > bestCount) {
                    best = entry.getKey();
                    bestCount = free;
                }
            }
            if (best == null) {
                return null;
            }
            result.merge(best, 1, Integer::sum);
        }
        return result;
    }

    private static boolean take(MinecraftServer server, Road road, UUID kingdomId, Map<String, Integer> items, RoadLayout.Block near) {
        WorldPos at = near == null ? null : new WorldPos(road.dimension(), near.x(), near.y(), near.z());
        Reservations reservations = Production.reservations();
        UUID key = holdKey(road);
        List<KingdomStorage.Take> takes = KingdomStorage.plan(server, kingdomId, items, item -> reservations.reserved(kingdomId, item, key), at);
        return takes != null && KingdomStorage.takePlanned(server, takes) != null;
    }

    // ---------------------------------------------------------------- the world

    /**
     * Places paid blocks whose place is loaded.
     */
    static void placeLoaded(MinecraftServer server) {
        io.github.sekerator.kingdomworld.core.road.RoadState roads = RoadWork.state(server);
        int budget = PLACE_BUDGET;
        long deadline = System.nanoTime() + PLACE_NANOS;
        for (Road road : roads.roads()) {
            if (budget <= 0 || System.nanoTime() > deadline) {
                break;
            }
            ServerLevel level = ConstructionWork.level(server, road.dimension());
            if (level == null || io.github.sekerator.kingdomworld.common.construction.SiteLoader.servingRoad(road.id())) {
                // A road being laid far from players is the loader's while it is held.
                continue;
            }
            RoadEarthworks.tell(server, road);
            Set<UUID> consented = consented(roads, road);
            for (Road.Section section : road.sections()) {
                if (section.placed() >= section.paid()) {
                    continue;
                }
                budget -= placeSection(level, road, section, consented, budget, deadline);
                if (budget <= 0 || System.nanoTime() > deadline) {
                    break;
                }
            }
        }
    }

    /**
     * Places paid blocks of a section in order while their place is loaded and its owner lets the road be there.
     *
     * @return blocks placed
     */
    static int placeSection(ServerLevel level, Road road, Road.Section section, Set<UUID> consented, int budget) {
        return placeSection(level, road, section, consented, budget, Long.MAX_VALUE);
    }

    /**
     * Places paid blocks of a section in order until the budget of changes or the time runs out.
     *
     * @param deadline the time from {@link System#nanoTime()} after which no further column is started
     * @return blocks of the world changed
     */
    public static int placeSection(ServerLevel level, Road road, Road.Section section, Set<UUID> consented, int budget, long deadline) {
        if (road.levelled()) {
            return placeLevelled(level, road, section, consented, budget, deadline);
        }
        List<RoadLayout.Block> blocks = road.blocksOf(section);
        int done = 0;
        while (section.placed() < section.paid() && done < budget) {
            RoadLayout.Block block = blocks.get(section.placed());
            BlockPos pos = new BlockPos(block.x(), block.y(), block.z());
            if (!io.github.sekerator.kingdomworld.common.construction.ChunkHold.loaded(level, pos)
                    || !io.github.sekerator.kingdomworld.common.construction.ChunkHold.loaded(level, pos.offset(2, 0, 2))
                    || !io.github.sekerator.kingdomworld.common.construction.ChunkHold.loaded(level, pos.offset(-2, 0, -2))) {
                break;
            }
            if (!RoadBlocks.allowedAt(level, road, pos, consented)) {
                // Not the land of whoever builds it, and its owner did not agree: it waits.
                break;
            }
            RoadBlocks.place(level, road, section, block);
            section.markPlaced();
            done++;
        }
        if (section.placedAll() && !section.pool().isEmpty()) {
            RouteCell first = road.cells().get(section.firstCell());
            RoadBlocks.giveBack(level, section.builder(), new BlockPos(first.x(), first.y(), first.z()), section.takePool());
        }
        if (done > 0) {
            RoadWork.state(level.getServer()).changed();
            RoadSpeed.invalidate();
        }
        return done;
    }

    /**
     * Lays paid columns of a section of a levelled road in order, each with its earthworks, while its place is loaded, its owner
     * lets the road be there and nothing holds it up.
     *
     * @return blocks of the world changed
     */
    static int placeLevelled(ServerLevel level, Road road, Road.Section section, Set<UUID> consented, int budget, long deadline) {
        List<RoadLayout.Block> blocks = road.blocksOf(section);
        int changes = 0;
        int done = 0;
        while (section.placed() < section.paid() && changes < budget && System.nanoTime() <= deadline) {
            RoadLayout.Block block = blocks.get(section.placed());
            RoadEarthworks.Result result = RoadEarthworks.place(level, road, section, block, consented);
            if (result.hold() != RoadEarthworks.Hold.NONE) {
                // It waits: for its place to load, for consent, or for what holds it up to be seen to.
                break;
            }
            section.markPlaced();
            done++;
            changes += Math.max(1, result.changes());
        }
        if (section.placedAll() && !section.pool().isEmpty()) {
            RouteCell first = road.cells().get(section.firstCell());
            RoadBlocks.giveBack(level, section.builder(), new BlockPos(first.x(), first.y(), first.z()), section.takePool());
        }
        if (done > 0) {
            RoadWork.state(level.getServer()).changed();
            RoadSpeed.invalidate();
            if (placedAll(road)) {
                settleCredit(level.getServer(), road);
                RoadEarthworks.forget(road.id());
            }
        }
        return changes;
    }

    /**
     * Whether every block of a road is in the world.
     */
    static boolean placedAll(Road road) {
        for (Road.Section section : road.sections()) {
            if (!section.done() || !section.placedAll()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gives the paid items of a road that is given up back to the storehouses of their builders.
     */
    static void giveBackPools(MinecraftServer server, Road road) {
        settleCredit(server, road);
        RoadEarthworks.forget(road.id());
        ServerLevel level = ConstructionWork.level(server, road.dimension());
        if (level == null) {
            return;
        }
        for (Road.Section section : road.sections()) {
            if (section.pool().isEmpty() || KingdomWorldApi.kingdom(server, section.builder()).isEmpty()) {
                continue;
            }
            RouteCell first = road.cells().get(section.firstCell());
            RoadBlocks.giveBack(level, section.builder(), new BlockPos(first.x(), first.y(), first.z()), section.takePool());
        }
    }

    // ---------------------------------------------------------------- wear

    /**
     * A game day: built sections wear, more on busy roads.
     */
    static void daily(MinecraftServer server, long day) {
        io.github.sekerator.kingdomworld.core.road.RoadState roads = RoadWork.state(server);
        RoadRules rules = RoadSettings.rules();
        boolean any = false;
        roads.setRememberConsent(rules.rememberConsent());
        for (Road road : roads.roads()) {
            double wear = rules.wearPerDay(road.roadClass()) * (1.0 + Math.min(4.0, road.traffic() / 50.0));
            for (Road.Section section : road.sections()) {
                if (section.done() && !section.repairing() && wear > 0) {
                    section.wear(wear);
                    any = true;
                }
                // Builders keep their roads up by themselves unless that is turned off.
                if (rules.autoRepair() && section.done() && !section.repairing() && section.condition() < rules.repairBelow()) {
                    section.startRepair();
                    any = true;
                }
            }
        }
        if (any) {
            roads.changed();
            RoadSync.changed();
        }
    }

    /**
     * Orders the repair of the worn sections of a road (or of one section).
     *
     * @param section the section, or -1 for every worn one
     * @return sections put up for repair
     */
    public static int orderRepair(Road road, int section) {
        int count = 0;
        for (Road.Section candidate : road.sections()) {
            if ((section < 0 || candidate.index() == section) && candidate.done() && !candidate.repairing() && candidate.condition() < 0.999) {
                candidate.startRepair();
                count++;
            }
        }
        return count;
    }

    /**
     * Items of each material the unpaid blocks of a road still take, by the kingdom that builds them.
     */
    public static Map<UUID, int[]> remaining(Road road) {
        Map<UUID, int[]> result = new LinkedHashMap<>();
        for (Road.Section section : road.sections()) {
            List<RoadLayout.Block> blocks = road.blocksOf(section);
            int[] counts = RoadLayout.materials(blocks.subList(Math.min(section.paid(), blocks.size()), blocks.size()), road.roadClass());
            int[] sum = result.computeIfAbsent(section.builder(), id -> new int[RoadLayout.Material.values().length]);
            for (int i = 0; i < counts.length; i++) {
                sum[i] += counts[i];
            }
        }
        return result;
    }

    /**
     * Places every paid block of a road at once, loading its chunks; for operators and game tests.
     *
     * @return blocks placed
     */
    public static int placeNow(MinecraftServer server, Road road) {
        ServerLevel level = ConstructionWork.level(server, road.dimension());
        if (level == null) {
            return 0;
        }
        Set<UUID> consented = consented(RoadWork.state(server), road);
        int placed = 0;
        for (Road.Section section : road.sections()) {
            for (RoadLayout.Block block : road.blocksOf(section)) {
                for (int dx = -2; dx <= 2; dx += 2) {
                    for (int dz = -2; dz <= 2; dz += 2) {
                        level.getChunk(new BlockPos(block.x() + dx, block.y(), block.z() + dz));
                    }
                }
            }
            placed += placeSection(level, road, section, consented, Integer.MAX_VALUE);
        }
        return placed;
    }

    /**
     * Whether an item stack could be used for some material of a road.
     */
    static boolean isMaterial(ItemStack stack) {
        return stack.is(RoadBlocks.GRAVEL) || stack.is(RoadBlocks.PAVING) || stack.is(RoadBlocks.BRIDGE) || stack.is(RoadBlocks.FILL)
                || stack.is(RoadBlocks.PIER) || stack.is(RoadBlocks.RAIL) || stack.is(RoadBlocks.LINING) || stack.is(RoadBlocks.LIGHT);
    }

    static String dimension(ServerLevel level) {
        return Dimensions.id(level);
    }
}
