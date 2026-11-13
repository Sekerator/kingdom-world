package io.github.sekerator.kingdomworld.common.road;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.trade.MarketStock;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.combat.Battle;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.road.Ambushes;
import io.github.sekerator.kingdomworld.core.road.BanditCamp;
import io.github.sekerator.kingdomworld.core.road.BanditRules;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import io.github.sekerator.kingdomworld.core.trade.Goods;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.util.FakePlayer;
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
 * Camps of robbers at work: once a game day a camp may appear in the wild far from kingdoms, more likely near roads with
 * many caravans, and camps nobody disturbs grow; squads that hold, guard or patrol next to a camp fight it. Near players a
 * camp stands in the world (a fire, tents and a chest with the robbed goods, built only where nothing of value stands) and
 * its robbers walk about it; killed robbers are gone from the camp for good.
 *
 * <p>The blocks of a camp drop nothing when broken (the goods in its chest do), and its robbers drop nothing either, so a
 * camp brings nothing into the world that was not robbed.
 */
public final class BanditWork {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = RoadWork.KEY;
    /** Blocks around a player within which a camp stands in the world and its robbers walk. */
    private static final int NEAR = 64;
    /** Blocks around a camp within which squads fight it. */
    private static final int FIGHT_RADIUS = 48;
    private static final RandomSource RANDOM = RandomSource.create();
    /** Robbers walking about their camp, by camp. */
    private static final Map<Long, Set<UUID>> BODIES = new HashMap<>();
    /** Robbers of an ambush near players, with the caravan they fell upon. */
    private static final Map<UUID, Long> AMBUSHERS = new HashMap<>();
    private static final Set<UUID> SPAWNED = new HashSet<>();

    private BanditWork() {
    }

    static void reset() {
        BODIES.clear();
        AMBUSHERS.clear();
        SPAWNED.clear();
    }

    /**
     * Whether a robber was made in this run of the server; others are left over and go.
     */
    public static boolean ours(UUID entity) {
        return SPAWNED.contains(entity);
    }

    /**
     * Whether robbers attack a player: not one away from the game, in creative mode or a spectator.
     */
    public static boolean mayAttack(LivingEntity target) {
        return target instanceof ServerPlayer player && !(player instanceof FakePlayer) && !player.isCreative() && !player.isSpectator()
                && !ArmyWork.isAway(player);
    }

    // ---------------------------------------------------------------- camps

    /**
     * Makes a camp, for the daily chance and for operators.
     */
    public static BanditCamp found(MinecraftServer server, String dimension, int x, int z, int bandits) {
        RoadState roads = RoadWork.state(server);
        BanditCamp camp = roads.addCamp(dimension, x, 64, z, bandits, true, RoadWork.day(server));
        LOGGER.info("A camp of robbers appeared at {} {} in {} ({} robbers and a leader)", x, z, dimension, bandits);
        RoadSync.changed();
        return camp;
    }

    /**
     * A game day: a new camp may appear, camps grow, squads near camps fight them, empty camps go.
     */
    static void daily(MinecraftServer server, long day) {
        BanditRules rules = RoadSettings.bandits();
        RoadState roads = RoadWork.state(server);
        // The world of game tests gets no camps by itself: they would fall upon the caravans of other tests.
        boolean testWorld = server instanceof net.minecraft.gametest.framework.GameTestServer;
        if (rules.enabled() && !testWorld && roads.camps().size() < rules.maxCamps() && RANDOM.nextDouble() < rules.spawnChance()) {
            findPlace(server, rules).ifPresent(place -> found(server, place.dimension(), place.x(), place.z(), rules.startBandits()));
        }
        for (BanditCamp camp : new ArrayList<>(roads.camps())) {
            fightSquads(server, camp, day);
            if (Ambushes.grow(camp, day, rules, RANDOM::nextDouble)) {
                roads.changed();
                RoadSync.changed();
            }
            if (camp.cleared() && camp.loot().isEmpty() && (camp.chest().isEmpty() || !chestHolds(server, camp))) {
                roads.removeCamp(camp.id());
                removeBodies(server, camp.id());
                RoadSync.changed();
            }
        }
    }

    private static boolean chestHolds(MinecraftServer server, BanditCamp camp) {
        ServerLevel level = ConstructionWork.level(server, camp.dimension());
        if (level == null || camp.chest().isEmpty()) {
            return false;
        }
        BlockPos pos = BlockPos.of(camp.chest().getAsLong());
        if (!level.hasChunkAt(pos)) {
            return true;
        }
        return level.getBlockEntity(pos) instanceof Container container && !container.isEmpty();
    }

    /**
     * A place for a new camp: beside a road with caravans, or in the wild around a kingdom; never closer to the land of any
     * kingdom than the rules allow.
     */
    static Optional<WorldPos> findPlace(MinecraftServer server, BanditRules rules) {
        RoadState roads = RoadWork.state(server);
        List<Road> busy = new ArrayList<>();
        int weight = 0;
        for (Road road : roads.roads()) {
            if (road.paid() > 0) {
                busy.add(road);
                weight += 1 + Math.min(100, road.traffic());
            }
        }
        List<Capital> capitals = new ArrayList<>();
        for (Territory territory : KingdomWorldApi.state(server).territory().territories()) {
            if (territory.capital() != null) {
                capitals.add(territory.capital());
            }
        }
        for (int attempt = 0; attempt < 16; attempt++) {
            String dimension;
            int x;
            int z;
            if (!busy.isEmpty() && attempt % 4 != 3) {
                int roll = RANDOM.nextInt(weight);
                Road road = busy.get(0);
                for (Road candidate : busy) {
                    roll -= 1 + Math.min(100, candidate.traffic());
                    if (roll < 0) {
                        road = candidate;
                        break;
                    }
                }
                RouteCell cell = road.cells().get(RANDOM.nextInt(road.cells().size()));
                double angle = RANDOM.nextDouble() * Math.PI * 2;
                // In the wilds off the road: far enough not to be seen from it, near enough to fall upon it.
                double distance = ROAD_DISTANCE + 8 + RANDOM.nextInt(96);
                dimension = road.dimension();
                x = cell.x() + (int) Math.round(Math.cos(angle) * distance);
                z = cell.z() + (int) Math.round(Math.sin(angle) * distance);
            } else if (!capitals.isEmpty()) {
                Capital capital = capitals.get(RANDOM.nextInt(capitals.size()));
                double angle = RANDOM.nextDouble() * Math.PI * 2;
                double distance = Math.max(rules.minDistance(), SETTLEMENT_DISTANCE) + 32 + RANDOM.nextInt(256);
                dimension = capital.dimension();
                x = capital.x() + (int) Math.round(Math.cos(angle) * distance);
                z = capital.z() + (int) Math.round(Math.sin(angle) * distance);
            } else {
                return Optional.empty();
            }
            if (wild(server, roads, dimension, x, z, rules)) {
                return Optional.of(new WorldPos(dimension, x, 64, z));
            }
        }
        return Optional.empty();
    }

    /** Blocks a camp keeps from the claimed land of the settlements of kingdoms of the world. */
    static final int SETTLEMENT_DISTANCE = 400;
    /** Blocks a camp keeps from every road. */
    static final int ROAD_DISTANCE = 128;

    /**
     * Whether a place is in the wilds for a camp: at least {@link #SETTLEMENT_DISTANCE} blocks from the claimed land of the
     * settlements of kingdoms of the world, at least the distance of the rules from the claims of kingdoms of players, at least
     * {@link #ROAD_DISTANCE} from roads and 128 from other camps. The provinces of the states of the world keep no distance:
     * their wilds are where robbers live.
     */
    static boolean wild(MinecraftServer server, RoadState roads, String dimension, int x, int z, BanditRules rules) {
        int playerChunks = (rules.minDistance() + 15) / 16;
        int settlementChunks = (SETTLEMENT_DISTANCE + 15) / 16;
        Map<Long, UUID> claims = KingdomWorldApi.claims(server, dimension);
        int cx = x >> 4;
        int cz = z >> 4;
        Map<UUID, Boolean> world = new java.util.HashMap<>();
        for (Map.Entry<Long, UUID> claim : claims.entrySet()) {
            int dx = ChunkKey.x(claim.getKey()) - cx;
            int dz = ChunkKey.z(claim.getKey()) - cz;
            int distance = dx * dx + dz * dz;
            if (distance > settlementChunks * settlementChunks && distance > playerChunks * playerChunks) {
                continue;
            }
            boolean ofWorld = world.computeIfAbsent(claim.getValue(), id -> io.github.sekerator.kingdomworld.common.npc.NpcWork.isNpc(server, id));
            int limit = ofWorld ? settlementChunks : playerChunks;
            if (distance <= limit * limit) {
                return false;
            }
        }
        for (Road road : roads.roads()) {
            if (!road.dimension().equals(dimension)) {
                continue;
            }
            List<RouteCell> cells = road.cells();
            // Every eighth block of a road is close enough to tell its distance.
            for (int i = 0; i < cells.size(); i += 8) {
                RouteCell cell = cells.get(i);
                double dx = cell.x() - x;
                double dz = cell.z() - z;
                if (dx * dx + dz * dz < (double) ROAD_DISTANCE * ROAD_DISTANCE) {
                    return false;
                }
            }
        }
        for (BanditCamp camp : roads.camps()) {
            if (camp.dimension().equals(dimension) && camp.distanceSqr(x, z) < 128.0 * 128.0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Squads that hold, guard or patrol next to a camp fight it once a day. If they win and no robber is left, the goods of
     * the camp go to the storehouses of their kingdom.
     */
    static void fightSquads(MinecraftServer server, BanditCamp camp, long day) {
        if (camp.cleared() || ArmyWork.isReadOnly(server)) {
            return;
        }
        BanditRules rules = RoadSettings.bandits();
        for (ArmySquad squad : List.copyOf(ArmyWork.state(server).squads())) {
            Order order = squad.order();
            if (!(order.kind() == Order.Kind.HOLD || order.kind() == Order.Kind.GUARD || order.kind() == Order.Kind.PATROL)
                    || ArmyWork.onTheRoad(server, squad)) {
                continue;
            }
            boolean near = false;
            for (WorldPos point : order.points()) {
                if (point.dimension().equals(camp.dimension()) && camp.distanceSqr(point.x(), point.z()) <= (double) FIGHT_RADIUS * FIGHT_RADIUS) {
                    near = true;
                    break;
                }
            }
            if (!near) {
                continue;
            }
            if (fightSquad(server, camp, squad)) {
                return;
            }
        }
    }

    /**
     * A squad fights a camp. If it wins and no robber is left, the goods of the camp go to the storehouses of its kingdom.
     *
     * @return whether the camp is cleared
     */
    public static boolean fightSquad(MinecraftServer server, BanditCamp camp, ArmySquad squad) {
        if (camp.cleared() || ArmyWork.isReadOnly(server)) {
            return camp.cleared();
        }
        BanditRules rules = RoadSettings.bandits();
        long day = io.github.sekerator.kingdomworld.common.economy.TreasuryActions.day(server);
        {
            var enemy = camp.side(rules, camp.bandits());
            WorldPos where = new WorldPos(camp.dimension(), camp.x(), camp.y(), camp.z());
            ArmyWork.Fought fought = ArmyWork.fight(server, squad, enemy, where);
            ArmyWork.announce(server, squad, fought, Component.translatable(KEY + "camp_fight", camp.id()));
            camp.setLastGrowth(day);
            camp.learn(squad.kingdomId());
            int lost = fought.result().secondLost();
            if (lost >= enemy.units()) {
                camp.lose(camp.bandits());
                camp.killLeader();
            } else {
                camp.lose(lost);
            }
            RoadWork.state(server).changed();
            RoadSync.changed();
            if (camp.cleared()) {
                cleared(server, camp, squad.kingdomId());
                return true;
            }
        }
        return false;
    }

    /**
     * Nobody of a camp is left: whoever cleared it takes the goods it kept outside its chest.
     *
     * @param kingdomId the kingdom whose squads cleared it, or {@code null} for players
     */
    static void cleared(MinecraftServer server, BanditCamp camp, @Nullable UUID kingdomId) {
        LOGGER.info("The camp of robbers {} was cleared", camp.id());
        for (UUID known : camp.known()) {
            RoadWork.notify(server, known, Component.translatable(KEY + "camp_cleared", camp.x(), camp.z()).withStyle(ChatFormatting.GREEN));
        }
        if (kingdomId == null || camp.loot().isEmpty() || KingdomWorldApi.kingdom(server, kingdomId).isEmpty()) {
            return;
        }
        List<ItemStack> stacks = MarketStock.stacks(camp.takeLoot());
        List<Goods> rest = new ArrayList<>();
        WorldPos near = new WorldPos(camp.dimension(), camp.x(), camp.y(), camp.z());
        for (ItemStack stack : stacks) {
            ItemStack left = KingdomStorage.insert(server, kingdomId, stack, near);
            if (!left.isEmpty()) {
                rest.addAll(MarketStock.goods(List.of(left)));
            }
        }
        camp.addLoot(rest);
        RoadWork.notify(server, kingdomId, Component.translatable(KEY + "camp_loot", RoadTexts.items(Ambushes.items(MarketStock.goods(stacks))
                - Ambushes.items(rest))).withStyle(ChatFormatting.GREEN));
        RoadWork.state(server).changed();
    }

    /**
     * A slave who escaped its owner runs to the nearest camp of robbers in its dimension and becomes one of them; what it
     * carried goes into the loot of the camp. With no camp to run to it is gone with its pack.
     *
     * @param who   for the log
     * @param where where it ran from, {@code null} if not known
     * @return the camp it joined
     */
    public static Optional<BanditCamp> takeRunaway(MinecraftServer server, String who, List<ItemStack> pack, @Nullable WorldPos where) {
        if (where == null || RoadWork.isReadOnly(server) || !RoadSettings.bandits().enabled()) {
            return Optional.empty();
        }
        RoadState roads = RoadWork.state(server);
        Optional<BanditCamp> camp = roads.nearestCamp(where.dimension(), where.x(), where.z(), Double.MAX_VALUE / 4)
                .filter(found -> !found.cleared());
        if (camp.isEmpty()) {
            return Optional.empty();
        }
        Outcome outcome = RoadWork.change(server, "take in a runaway", state -> {
            // A runaway joins even a full camp.
            camp.get().grow(Math.max(RoadSettings.bandits().maxBandits(), camp.get().bandits() + 1));
            state.changed();
            return Outcome.DONE;
        });
        if (!outcome.ok()) {
            return Optional.empty();
        }
        List<Goods> goods = new ArrayList<>();
        for (ItemStack stack : pack) {
            if (!stack.isEmpty()) {
                goods.add(io.github.sekerator.kingdomworld.common.trade.GoodsStacks.goods(stack));
            }
        }
        if (!goods.isEmpty()) {
            addLoot(server, camp.get(), goods);
        }
        LOGGER.info("{} ran away to the camp of robbers {} at {} {} with {} stack(s)", who, camp.get().id(), camp.get().x(), camp.get().z(),
                goods.size());
        return camp;
    }

    /**
     * Robbed goods go into a camp: into its chest when it stands in a loaded place, otherwise they wait in the camp.
     */
    static void addLoot(MinecraftServer server, BanditCamp camp, List<Goods> goods) {
        List<Goods> lost = camp.addLoot(goods);
        if (!lost.isEmpty()) {
            LOGGER.info("The camp of robbers {} is full: {} entries of goods are lost to the wild", camp.id(), lost.size());
        }
        moveLootToChest(server, camp);
        RoadWork.state(server).changed();
    }

    public static void moveLootToChest(MinecraftServer server, BanditCamp camp) {
        if (camp.loot().isEmpty() || camp.chest().isEmpty()) {
            return;
        }
        ServerLevel level = ConstructionWork.level(server, camp.dimension());
        BlockPos pos = BlockPos.of(camp.chest().getAsLong());
        if (level == null || !level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof Container chest)) {
            return;
        }
        List<Goods> rest = new ArrayList<>();
        for (ItemStack stack : MarketStock.stacks(camp.takeLoot())) {
            ItemStack left = stack;
            for (int slot = 0; slot < chest.getContainerSize() && !left.isEmpty(); slot++) {
                ItemStack held = chest.getItem(slot);
                if (held.isEmpty()) {
                    chest.setItem(slot, left);
                    left = ItemStack.EMPTY;
                } else if (ItemStack.isSameItemSameTags(held, left) && held.getCount() < held.getMaxStackSize()) {
                    int move = Math.min(left.getCount(), held.getMaxStackSize() - held.getCount());
                    held.grow(move);
                    left.shrink(move);
                    chest.setItem(slot, held);
                }
            }
            if (!left.isEmpty()) {
                rest.addAll(MarketStock.goods(List.of(left)));
            }
        }
        camp.addLoot(rest);
        chest.setChanged();
    }

    // ---------------------------------------------------------------- near players

    private static boolean playerNear(ServerLevel level, BanditCamp camp, int radius, List<ServerPlayer> found) {
        boolean near = false;
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && camp.distanceSqr(player.getBlockX(), player.getBlockZ()) < (double) radius * radius) {
                near = true;
                found.add(player);
            }
        }
        return near;
    }

    /**
     * Camps near players stand in the world and their robbers walk; others are abstract. Called once a second.
     */
    static void tick(MinecraftServer server) {
        RoadState roads = RoadWork.state(server);
        BanditRules rules = RoadSettings.bandits();
        for (BanditCamp camp : new ArrayList<>(roads.camps())) {
            ServerLevel level = ConstructionWork.level(server, camp.dimension());
            if (level == null) {
                continue;
            }
            List<ServerPlayer> players = new ArrayList<>();
            boolean near = playerNear(level, camp, NEAR, players) && level.hasChunkAt(new BlockPos(camp.x(), 64, camp.z()));
            if (!near) {
                removeBodies(server, camp.id());
                continue;
            }
            for (ServerPlayer player : players) {
                KingdomWorldApi.state(server).membership().kingdomOf(player.getUUID()).ifPresent(kingdom -> {
                    if (camp.learn(kingdom)) {
                        RoadWork.notify(server, kingdom, Component.translatable(KEY + "camp_found", camp.x(), camp.z()).withStyle(ChatFormatting.GOLD));
                        roads.changed();
                        RoadSync.changed();
                    }
                });
            }
            if (!camp.built()) {
                build(level, camp);
                roads.changed();
            }
            moveLootToChest(server, camp);
            Set<UUID> bodies = BODIES.computeIfAbsent(camp.id(), id -> new HashSet<>());
            bodies.removeIf(id -> !(level.getEntity(id) instanceof BanditEntity bandit) || !bandit.isAlive());
            int want = Math.min(camp.size(), rules.maxPhysical());
            boolean leaderShown = bodies.stream().anyMatch(id -> level.getEntity(id) instanceof BanditEntity bandit && bandit.isLeader());
            int spawned = 0;
            while (bodies.size() < want && spawned < 2) {
                boolean leader = camp.leader() && !leaderShown;
                BanditEntity bandit = spawn(level, camp, leader, new BlockPos(camp.x(), camp.y(), camp.z()), 6);
                if (bandit == null) {
                    break;
                }
                bandit.restrictTo(new BlockPos(camp.x(), camp.y(), camp.z()), 16);
                bodies.add(bandit.getUUID());
                leaderShown |= leader;
                spawned++;
            }
        }
    }

    @Nullable
    private static BanditEntity spawn(ServerLevel level, BanditCamp camp, boolean leader, BlockPos around, int spread) {
        BanditEntity bandit = (leader ? RoadRegistries.BANDIT_LEADER : RoadRegistries.BANDIT).get().create(level);
        if (bandit == null) {
            return null;
        }
        BlockPos at = null;
        for (int tries = 0; tries < 8 && at == null; tries++) {
            int x = around.getX() + RANDOM.nextInt(spread * 2 + 1) - spread;
            int z = around.getZ() + RANDOM.nextInt(spread * 2 + 1) - spread;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (level.getBlockState(top.below()).getFluidState().isEmpty() && level.noCollision(bandit.getType().getAABB(x + 0.5, top.getY(), z + 0.5))) {
                at = top;
            }
        }
        if (at == null) {
            return null;
        }
        bandit.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, RANDOM.nextFloat() * 360.0f, 0.0f);
        bandit.setCampId(camp.id());
        bandit.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
        BanditGear.equip(bandit, level.getRandom());
        bandit.setPersistenceRequired();
        SPAWNED.add(bandit.getUUID());
        level.addFreshEntity(bandit);
        return bandit;
    }

    private static void removeBodies(MinecraftServer server, long campId) {
        Set<UUID> bodies = BODIES.remove(campId);
        if (bodies == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (UUID id : bodies) {
                Entity entity = level.getEntity(id);
                if (entity != null) {
                    entity.discard();
                }
            }
        }
    }

    /**
     * A robber an operator or a check spawned joins the bodies of a camp, and fights with them.
     */
    public static void adopt(long campId, BanditEntity bandit) {
        bandit.setCampId(campId);
        BODIES.computeIfAbsent(campId, id -> new HashSet<>()).add(bandit.getUUID());
    }

    /**
     * A robber leaves the bodies of its camp without dying: an operator or a check took it away.
     */
    public static void release(BanditEntity bandit) {
        Set<UUID> bodies = BODIES.get(bandit.campId());
        if (bodies != null) {
            bodies.remove(bandit.getUUID());
            if (bodies.isEmpty()) {
                BODIES.remove(bandit.campId());
            }
        }
        AMBUSHERS.remove(bandit.getUUID());
    }

    /**
     * The robbers who have bodies now, of the camps and of the ambushes: their ids.
     */
    public static Set<UUID> living() {
        Set<UUID> all = new HashSet<>(AMBUSHERS.keySet());
        BODIES.values().forEach(all::addAll);
        return all;
    }

    /**
     * Robbers of a camp fall upon a caravan walking near players: up to a party of them come at its merchant.
     *
     * @return whether any came
     */
    static boolean ambushInWorld(MinecraftServer server, BanditCamp camp, CaravanMerchantEntity merchant) {
        if (!(merchant.level() instanceof ServerLevel level) || camp.cleared()) {
            return false;
        }
        BanditRules rules = RoadSettings.bandits();
        int party = Math.min(Math.min(rules.raidParty(), camp.bandits()), Math.max(1, rules.maxPhysical()));
        int count = 0;
        BlockPos around = merchant.blockPosition();
        for (int i = 0; i < Math.max(1, party); i++) {
            boolean leader = i == 0 && camp.bandits() == 0 && camp.leader();
            if (!leader && camp.bandits() == 0) {
                break;
            }
            BanditEntity bandit = spawn(level, camp, leader, around.offset(RANDOM.nextBoolean() ? 12 : -12, 0, RANDOM.nextBoolean() ? 10 : -10), 4);
            if (bandit == null) {
                continue;
            }
            bandit.setTarget(merchant);
            bandit.restrictTo(around, 40);
            AMBUSHERS.put(bandit.getUUID(), merchant.caravanId());
            count++;
        }
        if (count > 0) {
            LOGGER.info("{} robber(s) of camp {} fell upon a caravan near players", count, camp.id());
        }
        return count > 0;
    }

    /**
     * Robbers of an ambush go back to their camp: their bodies go, the camp keeps them.
     */
    static void retreat(MinecraftServer server, long campId, long caravanId) {
        for (Map.Entry<UUID, Long> entry : new ArrayList<>(AMBUSHERS.entrySet())) {
            if (entry.getValue() != caravanId) {
                continue;
            }
            AMBUSHERS.remove(entry.getKey());
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(entry.getKey());
                if (entity != null) {
                    entity.discard();
                }
            }
        }
    }

    /**
     * A robber died: the camp loses it for good.
     */
    static void died(MinecraftServer server, BanditEntity bandit, @Nullable Entity killer) {
        AMBUSHERS.remove(bandit.getUUID());
        Set<UUID> bodies = BODIES.get(bandit.campId());
        if (bodies != null) {
            bodies.remove(bandit.getUUID());
        }
        if (RoadWork.isReadOnly(server)) {
            return;
        }
        Optional<BanditCamp> found = RoadWork.state(server).camp(bandit.campId());
        if (found.isEmpty()) {
            return;
        }
        BanditCamp camp = found.get();
        boolean wasCleared = camp.cleared();
        camp.lose(bandit.isLeader());
        camp.setLastGrowth(RoadWork.day(server));
        if (killer instanceof ServerPlayer player) {
            KingdomWorldApi.state(server).membership().kingdomOf(player.getUUID()).ifPresent(camp::learn);
        }
        RoadWork.state(server).changed();
        RoadSync.changed();
        if (bandit.isLeader()) {
            for (UUID known : camp.known()) {
                RoadWork.notify(server, known, Component.translatable(KEY + "leader_dead", camp.x(), camp.z()).withStyle(ChatFormatting.GREEN));
            }
        }
        if (!wasCleared && camp.cleared()) {
            cleared(server, camp, null);
        }
    }

    // ---------------------------------------------------------------- the camp in the world

    /**
     * Builds the camp where it stands: a fire, two tents and a chest, only on places where nothing of value stands.
     */
    public static void build(ServerLevel level, BanditCamp camp) {
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(camp.x(), 0, camp.z()));
        camp.setY(top.getY());
        camp.setBuilt(true);
        List<Long> placed = new ArrayList<>();
        BlockPos fire = top;
        if (canStand(level, fire)) {
            put(level, fire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), placed);
        }
        for (int[] tent : new int[][]{{4, 0}, {-4, 2}}) {
            BlockPos base = groundAt(level, camp.x() + tent[0], camp.z() + tent[1]);
            if (base == null) {
                continue;
            }
            for (int dz = 0; dz <= 1; dz++) {
                BlockPos row = base.offset(0, 0, dz);
                if (canStand(level, row.west()) && canStand(level, row.east()) && RoadBlocks.free(level.getBlockState(row.above()))) {
                    put(level, row.west(), Blocks.BROWN_WOOL.defaultBlockState(), placed);
                    put(level, row.east(), Blocks.BROWN_WOOL.defaultBlockState(), placed);
                    put(level, row.above(), Blocks.BROWN_WOOL.defaultBlockState(), placed);
                }
            }
        }
        for (int[] spot : new int[][]{{1, 3}, {-1, 3}, {2, -2}, {-2, -2}, {0, 4}, {3, 1}}) {
            BlockPos ground = groundAt(level, camp.x() + spot[0], camp.z() + spot[1]);
            if (ground != null && canStand(level, ground)) {
                level.setBlock(ground, Blocks.CHEST.defaultBlockState(), 3);
                camp.setChest(ground.asLong());
                break;
            }
        }
        camp.setStructure(placed);
        LOGGER.info("The camp of robbers {} stands in the world at {} ({} blocks{})", camp.id(), top.toShortString(), placed.size(),
                camp.chest().isPresent() ? " and a chest" : ", no place for a chest");
    }

    @Nullable
    private static BlockPos groundAt(ServerLevel level, int x, int z) {
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        return level.hasChunkAt(top) ? top : null;
    }

    /**
     * Whether a block can be put at a place: nothing of value there and solid ground under it.
     */
    private static boolean canStand(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockState below = level.getBlockState(pos.below());
        return RoadBlocks.free(state) && state.getFluidState().isEmpty() && below.isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP);
    }

    private static void put(ServerLevel level, BlockPos pos, BlockState state, List<Long> placed) {
        if (!RoadBlocks.free(level.getBlockState(pos)) || !level.getBlockState(pos).getFluidState().isEmpty()) {
            return;
        }
        level.setBlock(pos, state, 3);
        placed.add(pos.asLong());
    }

    /**
     * Whether a block belongs to a camp; such blocks drop nothing.
     *
     * @return the camp, if it does
     */
    public static Optional<BanditCamp> campBlock(Level level, BlockPos pos) {
        if (level.getServer() == null) {
            return Optional.empty();
        }
        String dimension = Dimensions.id(level);
        long packed = pos.asLong();
        for (BanditCamp camp : RoadWork.state(level.getServer()).camps()) {
            if (camp.dimension().equals(dimension) && camp.built() && camp.distanceSqr(pos.getX(), pos.getZ()) < 32 * 32
                    && (camp.structure().contains(packed) || camp.chest().orElse(Long.MIN_VALUE) == packed)) {
                return Optional.of(camp);
            }
        }
        return Optional.empty();
    }

    /**
     * A block of a camp is broken: it goes without its own drops; a chest leaves its goods.
     */
    public static void breakCampBlock(ServerLevel level, BlockPos pos, BanditCamp camp) {
        // Removing the block drops what a container holds, once, as breaking it would; the block itself drops nothing.
        level.removeBlock(pos, false);
        long packed = pos.asLong();
        camp.removeStructure(packed);
        if (camp.chest().orElse(Long.MIN_VALUE) == packed) {
            camp.setChest(null);
        }
        RoadWork.state(level.getServer()).changed();
    }

    /**
     * The robbers walking about a camp, for game tests.
     */
    public static Set<UUID> bodies(long campId) {
        return Set.copyOf(BODIES.getOrDefault(campId, Set.of()));
    }
}
