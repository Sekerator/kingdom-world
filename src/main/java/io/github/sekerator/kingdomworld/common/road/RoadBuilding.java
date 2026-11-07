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
import io.github.sekerator.kingdomworld.core.road.Crossing;
import io.github.sekerator.kingdomworld.core.road.Road;
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
    /** Blocks of road placed in the world a second at most, in all. */
    private static final int PLACE_BUDGET = 256;
    /** Work carried over between seconds, by kingdom, in seconds of a builder. */
    private static final Map<UUID, Double> CARRY = new HashMap<>();
    /** Builders of each kingdom seen working on roads in the last second. */
    private static final Map<UUID, Integer> CREWS = new HashMap<>();

    private RoadBuilding() {
    }

    static void reset() {
        CARRY.clear();
        CREWS.clear();
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
    static Set<UUID> consented(io.github.sekerator.kingdomworld.core.road.RoadState roads, Road road) {
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
        for (Citizen citizen : KingdomWorldApi.state(server).citizens().registry().all()) {
            if (!citizen.kingdomId().equals(kingdomId) || !citizen.isAlive() || citizen.state() == CitizenState.WOUNDED
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
        for (UUID kingdom : kingdoms) {
            List<Citizen> builders = freeBuilders(server, kingdom);
            if (builders.isEmpty()) {
                continue;
            }
            CREWS.put(kingdom, builders.size());
            double seconds = CARRY.getOrDefault(kingdom, 0.0);
            for (Citizen builder : builders) {
                seconds += EconomySettings.workSpeed(builder.mood());
            }
            int blocks = 0;
            for (Object[] job : work(roads, kingdom)) {
                Road road = (Road) job[0];
                Road.Section section = (Road.Section) job[1];
                double per = rules.secondsPerBlock(road.roadClass());
                while (seconds >= per) {
                    boolean paid = section.repairing() && section.done() ? payRepair(server, road, section, rules) : payBlock(server, road, section);
                    if (!paid) {
                        break;
                    }
                    seconds -= per;
                    blocks++;
                    if (section.done() && !section.repairing()) {
                        break;
                    }
                }
                if (seconds < per) {
                    break;
                }
            }
            // A crew without materials does not save up work for later.
            CARRY.put(kingdom, Math.min(seconds, rules.secondsPerBlock(RoadClass.PAVED) * builders.size()));
            if (blocks > 0) {
                int share = Math.max(1, blocks / builders.size());
                for (Citizen builder : builders) {
                    builder.addExperience(share);
                }
                roads.changed();
            }
        }
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
     * Pays for the next block of a section: every item it takes, all or none.
     */
    public static boolean payNow(MinecraftServer server, Road road, Road.Section section) {
        return payBlock(server, road, section);
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
        if (!take(server, road, section.builder(), items, block)) {
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
        for (Road road : roads.roads()) {
            if (budget <= 0) {
                break;
            }
            ServerLevel level = ConstructionWork.level(server, road.dimension());
            if (level == null) {
                continue;
            }
            Set<UUID> consented = consented(roads, road);
            for (Road.Section section : road.sections()) {
                if (section.placed() >= section.paid()) {
                    continue;
                }
                budget -= placeSection(level, road, section, consented, budget);
                if (budget <= 0) {
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
        List<RoadLayout.Block> blocks = road.blocksOf(section);
        int done = 0;
        while (section.placed() < section.paid() && done < budget) {
            RoadLayout.Block block = blocks.get(section.placed());
            BlockPos pos = new BlockPos(block.x(), block.y(), block.z());
            if (!level.isLoaded(pos) || !level.isLoaded(pos.offset(2, 0, 2)) || !level.isLoaded(pos.offset(-2, 0, -2))) {
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
     * Gives the paid items of a road that is given up back to the storehouses of their builders.
     */
    static void giveBackPools(MinecraftServer server, Road road) {
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
        for (Road road : roads.roads()) {
            double wear = rules.wearPerDay(road.roadClass()) * (1.0 + Math.min(4.0, road.traffic() / 50.0));
            for (Road.Section section : road.sections()) {
                if (section.done() && !section.repairing() && wear > 0) {
                    section.wear(wear);
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
                level.getChunk(new BlockPos(block.x(), block.y(), block.z()));
            }
            placed += placeSection(level, road, section, consented, Integer.MAX_VALUE);
        }
        return placed;
    }

    /**
     * Whether an item stack could be used for some material of a road.
     */
    static boolean isMaterial(ItemStack stack) {
        return stack.is(RoadBlocks.GRAVEL) || stack.is(RoadBlocks.PAVING) || stack.is(RoadBlocks.BRIDGE) || stack.is(RoadBlocks.FILL);
    }

    static String dimension(ServerLevel level) {
        return Dimensions.id(level);
    }
}
