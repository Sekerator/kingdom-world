package io.github.sekerator.kingdomworld.common.construction;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.Site;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.BuildingIndex;
import io.github.sekerator.kingdomworld.core.construction.BuildingRole;
import io.github.sekerator.kingdomworld.core.construction.BuildingRoles;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What the roles of the buildings of the register do in the world while they work: houses give homes and comfort and
 * bring newcomers sooner, warehouses make the containers in them storehouses of the kingdom, workshops speed up the posts
 * in them, markets take more merchants and lots, towers let guards see farther, walls count for the wars to come, wells
 * cheer the citizens around them. A role works while its building stands on its kingdom's land, is not being taken down
 * and is whole enough (see {@link BuildingRoles#status}); below the threshold it rests until a repair.
 *
 * <p>The state of every building is worked out every few seconds and whenever the register changes. Server thread only.
 */
public final class BuildingEffects {
    /** Containers that count as storehouses inside a warehouse. Data packs add their own. */
    public static final TagKey<Block> WAREHOUSE_CONTAINERS = TagKey.create(Registries.BLOCK, KingdomWorld.id("warehouse_containers"));
    /** How far a tower lends its sight, in blocks. */
    public static final int TOWER_REACH = 64;

    /**
     * A building of the register with the state and the numbers of its role.
     *
     * @param beds      beds inside, for a house
     * @param residents citizens who live in those beds
     */
    public record Effect(Building building, BuildingRoles.Status status, Map<String, Double> params, int beds, int residents) {
        public boolean works() {
            return status.works();
        }

        public double param(String name) {
            return BuildingRole.param(params, name, 0.0);
        }
    }

    /** Buildings whose state is worked out again in a tick, besides those marked for it. */
    static final int REFRESH_PER_TICK = 64;

    private static Map<UUID, List<Effect>> effects = Map.of();
    /** Where the buildings of each kingdom stand, in the order of their effects. */
    private static Map<UUID, BuildingIndex> indexes = Map.of();
    /** Working houses of each kingdom. */
    private static Map<UUID, Integer> workingHouses = new HashMap<>();
    /** Every building in turn, for the states worked out again a few a tick. */
    private static List<Slot> slots = List.of();
    /** The place of every building among the effects. */
    private static Map<Long, Slot> slotById = Map.of();
    private static int cursor;
    private static final java.util.Set<String> DIRTY_ROLES = new java.util.HashSet<>();
    private static boolean dirty = true;
    private static long madeAt = -1;
    private static long refreshedAt = -1;
    private static long madeVersion = -1;
    private static MinecraftServer madeFor;
    /** Positions of the containers of each warehouse, as last seen. */
    private static final Map<Long, List<BlockPos>> CONTAINERS = new HashMap<>();
    /** The dimension of every warehouse whose containers are known. */
    private static final Map<Long, String> CONTAINER_DIMENSIONS = new HashMap<>();

    /**
     * A building among the effects: its kingdom and its place in the list of the kingdom.
     */
    private record Slot(UUID kingdomId, int index) {
    }

    private BuildingEffects() {
    }

    public static void reset() {
        effects = Map.of();
        indexes = Map.of();
        workingHouses = new HashMap<>();
        slots = List.of();
        slotById = Map.of();
        cursor = 0;
        DIRTY_ROLES.clear();
        dirty = true;
        madeAt = -1;
        refreshedAt = -1;
        madeVersion = -1;
        madeFor = null;
        CONTAINERS.clear();
        CONTAINER_DIMENSIONS.clear();
        WarehouseStock.reset();
    }

    /**
     * The register changed: the states are worked out again on next use.
     */
    public static void changed() {
        dirty = true;
    }

    /**
     * One building changed but stands as it stood (its integrity or its damage): its state alone is worked out again at once.
     */
    public static void changed(long buildingId) {
        MinecraftServer server = madeFor;
        Slot slot = slotById.get(buildingId);
        if (dirty || server == null || slot == null) {
            dirty = true;
            return;
        }
        redo(server, KingdomWorldApi.state(server), ConstructionSettings.buildings().roleThreshold(), effects, slot);
    }

    /**
     * Every tick the states of a few buildings are worked out again, in turn, so that every building is looked at again
     * within a few ticks without any tick doing them all; every few seconds the containers of warehouses are looked for, and
     * the warehouses are worked out again at once.
     */
    public static void tick(MinecraftServer server, long tick) {
        if (tick % 200 == 0) {
            surveyWarehouses(server);
            DIRTY_ROLES.add(BuildingRoles.WAREHOUSE);
        }
        refresh(server, REFRESH_PER_TICK);
    }

    /**
     * Works out again the state of the buildings of the roles marked first, then of {@code count} buildings in turn.
     */
    static void refresh(MinecraftServer server, int count) {
        Map<UUID, List<Effect>> current = all(server);
        refreshedAt = server.getTickCount();
        if (slots.isEmpty()) {
            DIRTY_ROLES.clear();
            return;
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        int threshold = ConstructionSettings.buildings().roleThreshold();
        if (!DIRTY_ROLES.isEmpty()) {
            for (Slot slot : slots) {
                Effect old = current.get(slot.kingdomId()).get(slot.index());
                if (DIRTY_ROLES.contains(old.building().role())) {
                    redo(server, state, threshold, current, slot);
                }
            }
            DIRTY_ROLES.clear();
        }
        for (int i = 0; i < Math.min(count, slots.size()); i++) {
            cursor = cursor % slots.size();
            redo(server, state, threshold, current, slots.get(cursor));
            cursor++;
        }
    }

    private static void redo(MinecraftServer server, KingdomWorldState state, int threshold, Map<UUID, List<Effect>> current, Slot slot) {
        List<Effect> list = current.get(slot.kingdomId());
        Effect old = list.get(slot.index());
        // The building as the register holds it now: its integrity and damage change without making the effects anew.
        Optional<Building> now = state.construction().building(old.building().id());
        if (now.isEmpty()) {
            dirty = true;
            return;
        }
        Effect fresh = effect(server, state, now.get(), threshold);
        list.set(slot.index(), fresh);
        boolean was = old.works() && BuildingRoles.HOUSE.equals(old.building().role());
        boolean is = fresh.works() && BuildingRoles.HOUSE.equals(fresh.building().role());
        if (was != is) {
            workingHouses.merge(slot.kingdomId(), is ? 1 : -1, Integer::sum);
        }
    }

    // ---------------------------------------------------------------- states

    /**
     * The buildings of a kingdom with their roles.
     */
    public static List<Effect> of(MinecraftServer server, UUID kingdomId) {
        return all(server).getOrDefault(kingdomId, List.of());
    }

    /**
     * The working buildings of a kingdom with a role.
     */
    public static List<Effect> working(MinecraftServer server, UUID kingdomId, String role) {
        List<Effect> result = new ArrayList<>();
        for (Effect effect : of(server, kingdomId)) {
            if (effect.works() && effect.building().role().equals(role)) {
                result.add(effect);
            }
        }
        return result;
    }

    public static Optional<Effect> of(MinecraftServer server, Building building) {
        for (Effect effect : of(server, building.kingdomId())) {
            if (effect.building().id() == building.id()) {
                return Optional.of(effect);
            }
        }
        return Optional.empty();
    }

    /**
     * The effects of all kingdoms. They are made anew when the register of buildings changed, and also when the server went a
     * long time without working any of them out again; otherwise they are worked out again a few a tick.
     */
    private static Map<UUID, List<Effect>> all(MinecraftServer server) {
        long now = server.getTickCount();
        long version = KingdomWorldApi.state(server).construction().version();
        if (dirty || server != madeFor || now < madeAt || version != madeVersion || now - Math.max(madeAt, refreshedAt) > 200) {
            make(server);
            dirty = false;
            madeAt = now;
            madeVersion = version;
            madeFor = server;
        }
        return effects;
    }

    private static void make(MinecraftServer server) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        int threshold = ConstructionSettings.buildings().roleThreshold();
        Map<UUID, List<Effect>> result = new HashMap<>();
        Map<UUID, Integer> houses = new HashMap<>();
        List<Slot> order = new ArrayList<>();
        Map<Long, Slot> byId = new HashMap<>();
        for (Building building : state.construction().buildings()) {
            Effect effect = effect(server, state, building, threshold);
            List<Effect> list = result.computeIfAbsent(building.kingdomId(), id -> new ArrayList<>());
            Slot slot = new Slot(building.kingdomId(), list.size());
            order.add(slot);
            byId.put(building.id(), slot);
            list.add(effect);
            if (effect.works() && BuildingRoles.HOUSE.equals(building.role())) {
                houses.merge(building.kingdomId(), 1, Integer::sum);
            }
        }
        Map<UUID, BuildingIndex> made = new HashMap<>();
        for (Map.Entry<UUID, List<Effect>> kingdom : result.entrySet()) {
            List<BuildingIndex.Entry> entries = new ArrayList<>();
            for (Effect effect : kingdom.getValue()) {
                Building building = effect.building();
                double radius = BuildingRoles.WELL.equals(building.role()) ? effect.param(BuildingRoles.RADIUS) : -1;
                entries.add(new BuildingIndex.Entry(building.dimension(), building.box(), radius));
            }
            made.put(kingdom.getKey(), new BuildingIndex(entries));
        }
        effects = result;
        indexes = made;
        workingHouses = houses;
        slots = order;
        slotById = byId;
        cursor = cursor >= order.size() ? 0 : cursor;
    }

    /**
     * The state of a building as it is now: its role met or lacking, the beds inside a house and those taken.
     */
    private static Effect effect(MinecraftServer server, KingdomWorldState state, Building building, int threshold) {
        boolean demolishing = state.construction().forBuilding(building.id()).map(c -> c.kind() == Construction.Kind.DEMOLISH).orElse(false);
        boolean onLand = onLand(state, building);
        BuildingRoles.Status status = BuildingRoles.status(building, threshold, demolishing, onLand);
        int bedCount = 0;
        int residents = 0;
        if (BuildingRoles.HOUSE.equals(building.role())) {
            for (long chunk : building.box().chunks()) {
                for (Site bed : state.citizens().sites().sitesInChunk(building.dimension(), chunk)) {
                    if (SiteKinds.BED.equals(bed.kind()) && building.kingdomId().equals(bed.kingdomId()) && inside(building, bed.pos())) {
                        bedCount++;
                        if (!bed.isFree()) {
                            residents++;
                        }
                    }
                }
            }
            if (bedCount == 0 && status == BuildingRoles.Status.ACTIVE) {
                status = BuildingRoles.Status.LACKING;
            }
        }
        if (BuildingRoles.BARRACKS.equals(building.role()) && status == BuildingRoles.Status.ACTIVE && !holdsBarracks(server, building)) {
            status = BuildingRoles.Status.LACKING;
        }
        if (BuildingRoles.MAGE_TOWER.equals(building.role()) && status == BuildingRoles.Status.ACTIVE && !holdsFocus(server, building)) {
            status = BuildingRoles.Status.LACKING;
        }
        if (BuildingRoles.WAREHOUSE.equals(building.role()) && status == BuildingRoles.Status.ACTIVE) {
            List<BlockPos> seen = CONTAINERS.get(building.id());
            if (seen != null && seen.isEmpty()) {
                status = BuildingRoles.Status.LACKING;
            }
        }
        return new Effect(building, status, params(building), bedCount, residents);
    }

    /**
     * The effects of a kingdom whose buildings hold a position, in their order.
     */
    private static List<Effect> holding(MinecraftServer server, UUID kingdomId, WorldPos pos) {
        List<Effect> list = all(server).get(kingdomId);
        BuildingIndex index = indexes.get(kingdomId);
        if (list == null || index == null || pos == null) {
            return List.of();
        }
        int[] found = index.containing(pos.dimension(), pos.x(), pos.y(), pos.z());
        if (found.length == 0) {
            return List.of();
        }
        List<Effect> result = new ArrayList<>(found.length);
        for (int i : found) {
            result.add(list.get(i));
        }
        return result;
    }

    private static boolean onLand(KingdomWorldState state, Building building) {
        for (long chunk : building.box().chunks()) {
            if (!building.kingdomId().equals(state.territory().ownerOf(building.dimension(), chunk))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The numbers of the role of a building: those of its template at its level, else the role's own for the level.
     */
    public static Map<String, Double> params(Building building) {
        Optional<BuildingTemplate> template = BuildingTemplates.get(building.templateId())
                .filter(found -> found.fingerprint() == building.fingerprint() && found.role().equals(building.role()));
        if (template.isPresent()) {
            return template.get().params();
        }
        return BuildingRoles.get(building.role()).map(role -> role.params(building.level(), Map.of())).orElse(Map.of());
    }

    static boolean inside(Building building, WorldPos pos) {
        return building.dimension().equals(pos.dimension()) && building.box().contains(pos.x(), pos.y(), pos.z());
    }

    // ---------------------------------------------------------------- houses and wells

    /**
     * The mood the buildings around a citizen's home give: the comfort of the working house it lives in and the best well
     * within reach.
     */
    public static int comfort(MinecraftServer server, UUID kingdomId, WorldPos home) {
        if (home == null) {
            return 0;
        }
        double house = 0;
        double well = 0;
        for (Effect effect : holding(server, kingdomId, home)) {
            if (effect.works() && BuildingRoles.HOUSE.equals(effect.building().role())) {
                house = Math.max(house, effect.param(BuildingRoles.COMFORT));
            }
        }
        List<Effect> list = all(server).get(kingdomId);
        BuildingIndex index = indexes.get(kingdomId);
        if (list != null && index != null) {
            for (int i : index.wellsNear(home.dimension(), home.x(), home.z())) {
                Effect effect = list.get(i);
                if (effect.works() && BuildingRoles.WELL.equals(effect.building().role()) && effect.building().dimension().equals(home.dimension())) {
                    double radius = effect.param(BuildingRoles.RADIUS);
                    if (ConstructionWork.distanceSqr(effect.building().box(), home) <= radius * radius) {
                        well = Math.max(well, effect.param(BuildingRoles.MOOD));
                    }
                }
            }
        }
        return (int) Math.round(Math.max(0, house) + Math.max(0, well));
    }

    /**
     * Whether a bed may become the home of a citizen. Never a bed of a tavern (the rooms of guests) or a bed a player sleeps
     * in as their point of return. A bed inside a building of the register may, unless the role of the building rests (a
     * house, a barracks or a tower of mages that works, or a building without a role). A bed outside every building may only
     * while the kingdom has no working house yet: a new kingdom gets its first citizens, and so its builders, from such beds.
     * Citizens who already live in a bed outside a building keep it.
     */
    public static boolean bedUsable(MinecraftServer server, UUID kingdomId, WorldPos bed) {
        if (io.github.sekerator.kingdomworld.common.life.LifeWork.playerBed(server, bed)) {
            return false;
        }
        List<Effect> holders = holding(server, kingdomId, bed);
        Effect holder = holders.isEmpty() ? null : holders.get(holders.size() - 1);
        boolean anyHouse = workingHouses.getOrDefault(kingdomId, 0) > 0;
        if (holder != null) {
            if (BuildingRoles.TAVERN.equals(holder.building().role())) {
                return false;
            }
            return holder.works() || holder.status() == BuildingRoles.Status.NONE;
        }
        return !anyHouse;
    }

    /**
     * Whether a citizen who lives in a bed should leave it: it became a room of a tavern or the bed of a player.
     */
    public static boolean homeLost(MinecraftServer server, UUID kingdomId, WorldPos bed) {
        if (io.github.sekerator.kingdomworld.common.life.LifeWork.playerBed(server, bed)) {
            return true;
        }
        for (Effect effect : holding(server, kingdomId, bed)) {
            if (BuildingRoles.TAVERN.equals(effect.building().role())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a bed lies inside a working house.
     */
    public static boolean inHouse(MinecraftServer server, UUID kingdomId, WorldPos bed) {
        for (Effect effect : holding(server, kingdomId, bed)) {
            if (effect.works() && BuildingRoles.HOUSE.equals(effect.building().role())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a bed is a bunk of a working barracks: such beds are kept for the soldiers of the kingdom.
     */
    public static boolean inBarracks(MinecraftServer server, UUID kingdomId, WorldPos bed) {
        for (Effect effect : holding(server, kingdomId, bed)) {
            if (effect.works() && BuildingRoles.BARRACKS.equals(effect.building().role())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the bunks of the barracks of a kingdom are kept for its soldiers: in a kingdom of the world, whose ruler keeps
     * a garrison. In a kingdom of players a bunk is a bed like any other, and the king decides who sleeps where.
     */
    public static boolean keepsBunks(MinecraftServer server, UUID kingdomId) {
        return io.github.sekerator.kingdomworld.common.npc.NpcWork.isNpc(server, kingdomId);
    }

    /**
     * The free bed a homeless citizen who is no soldier gets: the nearest one in a working house, else the nearest one
     * outside a house whose role rests; in a kingdom of the world a bunk of a barracks only when nothing else is left.
     */
    public static Optional<Site> freeBed(MinecraftServer server, UUID kingdomId, WorldPos near) {
        return freeBed(server, kingdomId, near, false);
    }

    /**
     * The free bed a homeless citizen gets. In a kingdom of the world a soldier takes a bunk of a barracks first, then a bed as
     * anybody; the bunks are kept for the garrison: they are not counted among the beds newcomers come for, and a citizen who
     * is no soldier takes one only as the last bed of the kingdom. In a kingdom of players a bunk is a bed like any other.
     */
    public static Optional<Site> freeBed(MinecraftServer server, UUID kingdomId, WorldPos near, boolean soldier) {
        Site best = null;
        double bestDistance = Double.MAX_VALUE;
        int bestRank = -1;
        boolean keeps = keepsBunks(server, kingdomId);
        for (Site bed : KingdomWorldApi.state(server).citizens().sites().sitesOf(kingdomId, SiteKinds.BED)) {
            if (!bed.isFree() || !bedUsable(server, kingdomId, bed.pos())) {
                continue;
            }
            boolean bunk = keeps && inBarracks(server, kingdomId, bed.pos());
            // A bunk is the first choice of a soldier and the last of anybody else: a citizen sleeps there only when the
            // kingdom has no other bed left, and newcomers are never invited for the sake of free bunks.
            int rank = bunk ? (soldier ? 3 : 0) : inHouse(server, kingdomId, bed.pos()) ? 2 : 1;
            double distance = near == null || !near.dimension().equals(bed.pos().dimension()) ? Double.MAX_VALUE / 2 : near.distanceSqr(bed.pos());
            if (best == null || rank > bestRank || (rank == bestRank && distance < bestDistance)) {
                best = bed;
                bestDistance = distance;
                bestRank = rank;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * The nearest free bunk of a working barracks, for a soldier of a kingdom of the world who sleeps elsewhere; none in a
     * kingdom of players.
     */
    public static Optional<Site> freeBunk(MinecraftServer server, UUID kingdomId, WorldPos near) {
        if (!keepsBunks(server, kingdomId)) {
            return Optional.empty();
        }
        return freeBed(server, kingdomId, near, true).filter(bed -> inBarracks(server, kingdomId, bed.pos()));
    }

    /**
     * Free beds for newcomers: {free beds that may be offered, of those the beds in working houses}. In a kingdom of the world
     * the bunks of barracks are not counted: they wait for soldiers.
     */
    public static int[] freeBeds(MinecraftServer server, UUID kingdomId) {
        int free = 0;
        int inHouses = 0;
        boolean keeps = keepsBunks(server, kingdomId);
        for (Site bed : KingdomWorldApi.state(server).citizens().sites().sitesOf(kingdomId, SiteKinds.BED)) {
            if (!bed.isFree() || !bedUsable(server, kingdomId, bed.pos()) || (keeps && inBarracks(server, kingdomId, bed.pos()))) {
                continue;
            }
            free++;
            if (inHouse(server, kingdomId, bed.pos())) {
                inHouses++;
            }
        }
        return new int[]{free, inHouses};
    }

    // ---------------------------------------------------------------- workshops and markets

    /**
     * How much faster a post works: the speed of the best working workshop around it, 1 elsewhere.
     */
    public static double workshopSpeed(MinecraftServer server, WorldPos post) {
        double speed = 1.0;
        for (Effect effect : holding(server, ownerOf(server, post), post)) {
            if (effect.works() && BuildingRoles.WORKSHOP.equals(effect.building().role())) {
                speed = Math.max(speed, effect.param(BuildingRoles.SPEED));
            }
        }
        return Math.max(1.0, Math.min(4.0, speed));
    }

    /**
     * Seats a working market adds to a merchant post inside it.
     */
    public static int extraSeats(MinecraftServer server, WorldPos post) {
        int seats = 0;
        for (Effect effect : holding(server, ownerOf(server, post), post)) {
            if (effect.works() && BuildingRoles.MARKET.equals(effect.building().role())) {
                seats = Math.max(seats, (int) Math.round(effect.param(BuildingRoles.MERCHANTS)));
            }
        }
        seats += io.github.sekerator.kingdomworld.common.life.LifeFairs.extraSeats(server, post);
        return Math.max(0, Math.min(16, seats));
    }

    /**
     * Lots the working markets of a kingdom add for every player.
     */
    public static int extraLots(MinecraftServer server, UUID kingdomId) {
        long lots = 0;
        for (Effect effect : working(server, kingdomId, BuildingRoles.MARKET)) {
            lots += Math.max(0, Math.round(effect.param(BuildingRoles.LOTS)));
        }
        return (int) Math.min(64, lots);
    }

    private static UUID ownerOf(MinecraftServer server, WorldPos pos) {
        UUID owner = KingdomWorldApi.state(server).territory().ownerOf(pos.dimension(), pos.chunk());
        return owner == null ? new UUID(0, 0) : owner;
    }

    // ---------------------------------------------------------------- the army and the mages

    /**
     * Whether a barracks block of the army stands inside a building.
     */
    static boolean holdsBarracks(MinecraftServer server, Building building) {
        for (WorldPos pos : io.github.sekerator.kingdomworld.common.army.ArmyWork.state(server).barracksOf(building.kingdomId())) {
            if (inside(building, pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a magic focus of the kingdom stands inside a building.
     */
    static boolean holdsFocus(MinecraftServer server, Building building) {
        for (var device : io.github.sekerator.kingdomworld.common.magic.MagicWork.state(server).devices()) {
            if (device.kind() == io.github.sekerator.kingdomworld.core.magic.MagicDevice.Kind.FOCUS
                    && building.kingdomId().equals(device.kingdomId()) && inside(building, device.pos())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Soldiers more the working barracks buildings of a kingdom let train at the same time, on top of what its barracks
     * blocks hold.
     */
    public static int bunks(MinecraftServer server, UUID kingdomId) {
        long bunks = 0;
        for (Effect effect : working(server, kingdomId, BuildingRoles.BARRACKS)) {
            bunks += Math.max(0, Math.round(effect.param(BuildingRoles.BUNKS)));
        }
        return (int) Math.min(1_024, bunks);
    }

    /**
     * How much faster a mage at a post works: the speed of the best working mage tower around the post, 1 elsewhere. It
     * speeds up dissolving crystals and the research of the tower alike.
     */
    public static double mageSpeed(MinecraftServer server, WorldPos post) {
        if (post == null) {
            return 1.0;
        }
        double speed = 1.0;
        for (Effect effect : holding(server, ownerOf(server, post), post)) {
            if (effect.works() && BuildingRoles.MAGE_TOWER.equals(effect.building().role())) {
                speed = Math.max(speed, effect.param(BuildingRoles.SPEED));
            }
        }
        return Math.max(1.0, Math.min(4.0, speed));
    }

    // ---------------------------------------------------------------- towers and walls

    /**
     * How much farther guards and garrisons near a position see: the best working tower of the kingdom within
     * {@link #TOWER_REACH} blocks. The army reads it.
     */
    public static double sight(MinecraftServer server, UUID kingdomId, WorldPos pos) {
        double sight = 0;
        for (Effect effect : working(server, kingdomId, BuildingRoles.TOWER)) {
            if (effect.building().dimension().equals(pos.dimension())
                    && ConstructionWork.distanceSqr(effect.building().box(), pos) <= (double) TOWER_REACH * TOWER_REACH) {
                sight = Math.max(sight, effect.param(BuildingRoles.SIGHT));
            }
        }
        return sight;
    }

    /**
     * The strength of the working walls of a kingdom, for the wars to come.
     */
    public static long fortification(MinecraftServer server, UUID kingdomId) {
        long total = 0;
        for (Effect effect : working(server, kingdomId, BuildingRoles.WALL)) {
            total += Math.max(0, Math.round(effect.param(BuildingRoles.FORTIFICATION)));
        }
        return total;
    }

    // ---------------------------------------------------------------- warehouses

    /**
     * Whether a block counts as a container of a warehouse: tagged so, or named like a chest, barrel or crate, with an
     * inventory, and not a block of the mod (a storehouse counts anyway).
     */
    public static boolean isContainer(ServerLevel level, BlockPos pos, BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null || KingdomWorld.MOD_ID.equals(id.getNamespace())) {
            return false;
        }
        String path = id.getPath();
        boolean named = path.endsWith("chest") || path.endsWith("barrel") || path.endsWith("crate") || path.endsWith("shulker_box");
        if (!state.is(WAREHOUSE_CONTAINERS) && !named) {
            return false;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        return entity != null && entity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent();
    }

    /**
     * Looks for the containers of the working warehouses whose place is loaded.
     */
    static void surveyWarehouses(MinecraftServer server) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        java.util.Set<Long> live = new java.util.HashSet<>();
        for (Building building : state.construction().buildings()) {
            if (!BuildingRoles.WAREHOUSE.equals(building.role())) {
                continue;
            }
            live.add(building.id());
            ServerLevel level = ConstructionWork.level(server, building.dimension());
            if (level == null || !ConstructionWork.loaded(level, building.box())) {
                continue;
            }
            List<BlockPos> found = scan(level, building);
            CONTAINERS.put(building.id(), found);
            CONTAINER_DIMENSIONS.put(building.id(), building.dimension());
            for (BlockPos pos : found) {
                IItemHandler handler = handler(level, pos);
                if (handler != null) {
                    WarehouseStock.remember(ConstructionWork.worldPos(level, pos), handler);
                }
            }
        }
        CONTAINERS.keySet().retainAll(live);
        CONTAINER_DIMENSIONS.keySet().retainAll(live);
        List<WorldPos> known = new ArrayList<>();
        CONTAINERS.forEach((id, positions) -> {
            String dimension = CONTAINER_DIMENSIONS.get(id);
            for (BlockPos pos : positions) {
                known.add(new WorldPos(dimension, pos.getX(), pos.getY(), pos.getZ()));
            }
        });
        WarehouseStock.keepOnly(known);
    }

    @org.jetbrains.annotations.Nullable
    private static IItemHandler handler(ServerLevel level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        return entity == null ? null : entity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
    }

    /**
     * A chunk unloads: what the containers of warehouses in it hold is remembered, so that the kingdom counts it while nobody is
     * near.
     */
    static void onChunkUnload(ServerLevel level, net.minecraft.world.level.chunk.LevelChunk chunk) {
        if (CONTAINERS.isEmpty()) {
            return;
        }
        String dimension = io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(level);
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        CONTAINERS.forEach((id, positions) -> {
            if (!dimension.equals(CONTAINER_DIMENSIONS.get(id))) {
                return;
            }
            for (BlockPos pos : positions) {
                if ((pos.getX() >> 4) == chunkX && (pos.getZ() >> 4) == chunkZ) {
                    BlockEntity entity = chunk.getBlockEntity(pos);
                    IItemHandler handler = entity == null ? null : entity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
                    if (handler != null) {
                        WarehouseStock.remember(new WorldPos(dimension, pos.getX(), pos.getY(), pos.getZ()), handler);
                    }
                }
            }
        });
    }

    static List<BlockPos> scan(ServerLevel level, Building building) {
        List<BlockPos> found = new ArrayList<>();
        var box = building.box();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.hasBlockEntity() && isContainer(level, pos, state) && !otherHalf(state, pos, box)) {
                        found.add(pos);
                    }
                }
            }
        }
        return found;
    }

    /**
     * Whether a container is the second half of a large chest inside the warehouse: either half of a large chest shows both, so
     * only one of them counts, or everything in it would count twice.
     */
    private static boolean otherHalf(BlockState state, BlockPos pos, io.github.sekerator.kingdomworld.core.construction.Box box) {
        if (!(state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock)
                || !state.hasProperty(net.minecraft.world.level.block.ChestBlock.TYPE)
                || state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE) != net.minecraft.world.level.block.state.properties.ChestType.LEFT) {
            return false;
        }
        BlockPos partner = pos.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state));
        return box.contains(partner.getX(), partner.getY(), partner.getZ());
    }

    /**
     * The containers of the working warehouses of a kingdom whose place is loaded, as storehouses: at most as many per
     * warehouse as its role allows.
     */
    public static List<StorehouseInventory> containers(MinecraftServer server, UUID kingdomId) {
        List<StorehouseInventory> result = new ArrayList<>();
        if (CONTAINERS.isEmpty()) {
            return result;
        }
        for (Effect effect : working(server, kingdomId, BuildingRoles.WAREHOUSE)) {
            List<BlockPos> positions = CONTAINERS.get(effect.building().id());
            ServerLevel level = ConstructionWork.level(server, effect.building().dimension());
            if (positions == null || level == null) {
                continue;
            }
            int limit = (int) Math.max(0, Math.round(effect.param(BuildingRoles.CONTAINERS)));
            for (BlockPos pos : positions) {
                if (result.size() >= 4_096 || limit-- <= 0) {
                    break;
                }
                if (!level.isLoaded(pos)) {
                    // Far from players a container counts with what it held when its place was last loaded.
                    WorldPos at = ConstructionWork.worldPos(level, pos);
                    if (WarehouseStock.seen(at) != null) {
                        result.add(new WarehouseStock.Remembered(at, level, pos));
                    }
                    continue;
                }
                BlockEntity entity = level.getBlockEntity(pos);
                IItemHandler handler = entity == null ? null : entity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
                if (handler != null && handler.getSlots() > 0) {
                    result.add(new ContainerStorehouse(ConstructionWork.worldPos(level, pos), level, pos, entity, handler));
                }
            }
        }
        return result;
    }

    /**
     * A container of a warehouse seen as a storehouse: every action goes to the container itself, and nothing goes
     * anywhere once the container is gone or its place unloaded.
     */
    static final class ContainerStorehouse extends StorehouseInventory {
        private final ServerLevel level;
        private final BlockPos at;
        private final BlockEntity entity;
        private final IItemHandler handler;

        ContainerStorehouse(WorldPos pos, ServerLevel level, BlockPos at, BlockEntity entity, IItemHandler handler) {
            super(pos);
            this.level = level;
            this.at = at;
            this.entity = entity;
            this.handler = handler;
        }

        private boolean live() {
            return !entity.isRemoved() && level.isLoaded(at) && level.getBlockEntity(at) == entity;
        }

        @Override
        public boolean takesIn() {
            return live();
        }

        @Override
        public int getSlots() {
            return live() ? handler.getSlots() : 0;
        }

        @Override
        public net.minecraft.world.item.ItemStack getStackInSlot(int slot) {
            return live() && slot >= 0 && slot < handler.getSlots() ? handler.getStackInSlot(slot) : net.minecraft.world.item.ItemStack.EMPTY;
        }

        @Override
        public net.minecraft.world.item.ItemStack insertItem(int slot, net.minecraft.world.item.ItemStack stack, boolean simulate) {
            return live() && slot >= 0 && slot < handler.getSlots() ? handler.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public net.minecraft.world.item.ItemStack extractItem(int slot, int amount, boolean simulate) {
            return live() && slot >= 0 && slot < handler.getSlots() ? handler.extractItem(slot, amount, simulate)
                    : net.minecraft.world.item.ItemStack.EMPTY;
        }

        @Override
        public void setStackInSlot(int slot, net.minecraft.world.item.ItemStack stack) {
            if (live() && handler instanceof net.minecraftforge.items.IItemHandlerModifiable modifiable && slot >= 0 && slot < handler.getSlots()) {
                modifiable.setStackInSlot(slot, stack);
            }
        }

        @Override
        public int getSlotLimit(int slot) {
            return live() && slot >= 0 && slot < handler.getSlots() ? handler.getSlotLimit(slot) : 0;
        }

        @Override
        public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
            return live() && slot >= 0 && slot < handler.getSlots() && handler.isItemValid(slot, stack);
        }
    }
}
