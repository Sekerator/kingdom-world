package io.github.sekerator.kingdomworld.common.road;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyCaravans;
import io.github.sekerator.kingdomworld.common.army.ArmySettings;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.trade.TradeSettings;
import io.github.sekerator.kingdomworld.common.trade.TradeTexts;
import io.github.sekerator.kingdomworld.common.trade.TradeWork;
import io.github.sekerator.kingdomworld.core.army.ArmyRules;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.combat.Battle;
import io.github.sekerator.kingdomworld.core.combat.Side;
import io.github.sekerator.kingdomworld.core.road.Ambushes;
import io.github.sekerator.kingdomworld.core.road.BanditCamp;
import io.github.sekerator.kingdomworld.core.road.BanditRules;
import io.github.sekerator.kingdomworld.core.road.CaravanRoute;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadNetwork;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import io.github.sekerator.kingdomworld.core.road.TravelPlan;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.trade.Caravan;
import io.github.sekerator.kingdomworld.core.trade.Deal;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.level.levelgen.Heightmap;
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
import java.util.function.DoubleSupplier;

/**
 * Caravans on the roads. A caravan takes the fastest way through the network of roads when it leaves; far from players it
 * is where the time says it is along that way, and on every piece of it near a camp robbers may fall upon it: the guards
 * fight them by the numbers of a battle, and what the robbers take goes into the camp, while the payment for it goes back
 * to the buyer. Near players the caravan walks as a merchant with pack llamas and its guards beside it, and an ambush is
 * a real fight.
 */
public final class RoadCaravans {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = RoadWork.KEY;
    /** Tag of the pack llamas of caravans, so that they drop nothing and cannot be taken. */
    public static final String PACK_TAG = "kingdomworld_caravan";
    /** Blocks around a player within which a caravan walks in the world. */
    private static final int NEAR = 96;
    /** Ticks robbers of an ambush near players stay before they go back to their camp. */
    private static final long AMBUSH_TICKS = 20L * 90;

    private record Body(UUID merchant, List<UUID> llamas, String dimension) {
    }

    private record Fight(long campId, long until) {
    }

    private static final Map<Long, Body> BODIES = new HashMap<>();
    private static final Map<Long, Fight> FIGHTS = new HashMap<>();
    private static final Set<UUID> SPAWNED = new HashSet<>();

    private RoadCaravans() {
    }

    static void reset() {
        BODIES.clear();
        FIGHTS.clear();
        SPAWNED.clear();
    }

    /**
     * Whether an entity of caravans was made in this run of the server; others are left over and go.
     */
    public static boolean ours(UUID entity) {
        return SPAWNED.contains(entity);
    }

    // ---------------------------------------------------------------- the way

    /**
     * The way of a caravan between two points through the roads.
     */
    public static TravelPlan plan(MinecraftServer server, String dimension, int fromX, int fromZ, int toX, int toZ) {
        double pace = TradeSettings.rules().caravanBlocksPerMinute();
        return RoadNetwork.plan(RoadWork.state(server).roads(), dimension, fromX, fromZ, toX, toZ, pace, RoadSettings.rules(), 1_200L,
                24_000L * 30);
    }

    /**
     * A caravan left on its way: the way is kept, and the roads it takes count it.
     */
    public static void departed(MinecraftServer server, Caravan caravan, TravelPlan plan) {
        if (RoadSavedData.get(server).isReadOnly()) {
            return;
        }
        RoadState roads = RoadWork.state(server);
        roads.putRoute(new CaravanRoute(caravan.id(), caravan.dimension(), caravan.departTime(), plan, 0));
        Set<Long> used = new HashSet<>();
        for (TravelPlan.Piece piece : plan.pieces()) {
            if (piece.roadId() != 0 && used.add(piece.roadId())) {
                roads.road(piece.roadId()).ifPresent(road -> road.addTraffic(1));
            }
        }
    }

    /**
     * The way of a caravan; a caravan that left before there were roads goes straight.
     */
    public static CaravanRoute route(MinecraftServer server, Caravan caravan) {
        RoadState roads = RoadWork.state(server);
        Optional<CaravanRoute> known = roads.route(caravan.id());
        if (known.isPresent()) {
            return known.get();
        }
        CaravanRoute straight = new CaravanRoute(caravan.id(), caravan.dimension(), caravan.departTime(),
                TravelPlan.straight(caravan.fromX(), caravan.fromZ(), caravan.toX(), caravan.toZ(), caravan.arriveTime() - caravan.departTime()), 0);
        if (!RoadSavedData.get(server).isReadOnly()) {
            roads.putRoute(straight);
        }
        return straight;
    }

    // ---------------------------------------------------------------- risks

    /**
     * The chance of an ambush on a piece of a way, and the camp whose robbers would lie in wait.
     */
    record Risk(double chance, @Nullable BanditCamp camp) {
    }

    static Risk risk(MinecraftServer server, String dimension, int x, int z, TravelPlan.Piece piece) {
        BanditRules rules = RoadSettings.bandits();
        if (!rules.enabled()) {
            return new Risk(0.0, null);
        }
        Optional<BanditCamp> camp = RoadWork.state(server).nearestCamp(dimension, x, z, rules.campReach());
        if (camp.isEmpty()) {
            return new Risk(0.0, null);
        }
        double factor = Ambushes.campFactor(camp.get().size(), Math.sqrt(camp.get().distanceSqr(x, z)), rules);
        double protection = Ambushes.protection(squadsNear(server, dimension, x, z, false), squadsNear(server, dimension, x, z, true),
                KingdomWorldApi.state(server).territory().ownerOf(dimension, ChunkKey.of(x >> 4, z >> 4)) != null, rules);
        return new Risk(Ambushes.chance(piece.length(), piece.roadClass(), factor, protection, rules), camp.get());
    }

    /**
     * Squads that hold or guard (or patrol) near a place.
     */
    static int squadsNear(MinecraftServer server, String dimension, int x, int z, boolean patrols) {
        if (ArmyWork.isReadOnly(server)) {
            return 0;
        }
        int radius = RoadSettings.bandits().protectRadius();
        WorldPos here = new WorldPos(dimension, x, 64, z);
        int count = 0;
        for (ArmySquad squad : ArmyWork.state(server).squads()) {
            Order order = squad.order();
            boolean wanted = patrols ? order.kind() == Order.Kind.PATROL : order.kind() == Order.Kind.HOLD || order.kind() == Order.Kind.GUARD;
            if (!wanted) {
                continue;
            }
            for (WorldPos point : order.points()) {
                double dx = point.x() - here.x();
                double dz = point.z() - here.z();
                if (point.dimension().equals(dimension) && dx * dx + dz * dz <= (double) radius * radius) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    /**
     * The chance that a caravan between two capitals is robbed on its way, with the guards it would have.
     */
    public static double robberyChance(MinecraftServer server, String dimension, int fromX, int fromZ, int toX, int toZ, double guardPower) {
        TravelPlan plan = plan(server, dimension, fromX, fromZ, toX, toZ);
        double safe = 1.0;
        for (int i = 0; i < plan.pieces().size(); i++) {
            TravelPlan.Point a = plan.points().get(i);
            TravelPlan.Point b = plan.points().get(i + 1);
            Risk risk = risk(server, dimension, (a.x() + b.x()) / 2, (a.z() + b.z()) / 2, plan.pieces().get(i));
            if (risk.camp() == null || risk.chance() <= 0) {
                continue;
            }
            double robbed = Ambushes.robbery(risk.chance(), guardPower, risk.camp().side(RoadSettings.bandits(), RoadSettings.bandits().raidParty())
                    .power());
            safe *= 1.0 - robbed;
        }
        return 1.0 - safe;
    }

    /**
     * The chance a caravan of a deal is robbed on its way.
     */
    public static double robberyChance(MinecraftServer server, Deal deal, double guardPower) {
        Optional<io.github.sekerator.kingdomworld.core.territory.Capital> from = RoadWork.capital(server, deal.seller());
        Optional<io.github.sekerator.kingdomworld.core.territory.Capital> to = RoadWork.capital(server, deal.buyer());
        if (from.isEmpty() || to.isEmpty() || from.get() == null || to.get() == null || !from.get().dimension().equals(to.get().dimension())) {
            return 0.0;
        }
        return robberyChance(server, from.get().dimension(), from.get().x(), from.get().z(), to.get().x(), to.get().z(), guardPower);
    }

    // ---------------------------------------------------------------- on the way

    /**
     * Moves caravans on: robbers on the pieces of way passed, merchants near players. Called once a second.
     */
    static void tick(MinecraftServer server) {
        KingdomWorldState world = KingdomWorldApi.state(server);
        long now = KingdomWorldApi.gameTime(server);
        RoadState roads = RoadWork.state(server);
        Set<Long> traveling = new HashSet<>();
        for (Caravan caravan : new ArrayList<>(world.trade().caravans())) {
            if (caravan.state() != Caravan.State.TRAVELING) {
                continue;
            }
            traveling.add(caravan.id());
            Optional<Caravan> left = reckon(server, caravan, now, TradeWork.dice());
            if (left.isPresent()) {
                walk(server, left.get(), route(server, left.get()), now);
            }
        }
        for (CaravanRoute route : new ArrayList<>(roads.routes())) {
            if (!traveling.contains(route.caravanId()) && world.trade().caravan(route.caravanId()).isEmpty()) {
                roads.removeRoute(route.caravanId());
            }
        }
        for (Long id : new ArrayList<>(BODIES.keySet())) {
            if (!traveling.contains(id)) {
                removeBody(server, id);
            }
        }
        FIGHTS.entrySet().removeIf(entry -> {
            if (now >= entry.getValue().until()) {
                BanditWork.retreat(server, entry.getValue().campId(), entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Reckons with the robbers of the pieces of way a caravan passed by a time.
     *
     * @return the caravan as it goes on, or empty if robbers took everything
     */
    static Optional<Caravan> reckon(MinecraftServer server, Caravan caravan, long until, DoubleSupplier roll) {
        CaravanRoute route = route(server, caravan);
        int passed = route.passed(until);
        Caravan current = caravan;
        int checked = route.checked();
        while (checked < passed) {
            int piece = checked;
            checked++;
            if (!RoadSavedData.get(server).isReadOnly()) {
                RoadWork.state(server).putRoute(route.withChecked(checked));
            }
            Optional<Caravan> after = ambush(server, current, route, piece, roll);
            if (after.isEmpty()) {
                return Optional.empty();
            }
            current = after.get();
        }
        return Optional.of(current);
    }

    /**
     * Before a caravan arrives, every piece of its way is reckoned with.
     *
     * @return the caravan with what is left, or empty if robbers took everything
     */
    public static Optional<Caravan> beforeArrival(MinecraftServer server, Caravan caravan, DoubleSupplier roll) {
        if (RoadWork.isReadOnly(server)) {
            return Optional.of(caravan);
        }
        CaravanRoute route = route(server, caravan);
        Optional<Caravan> left = reckon(server, caravan, route.departTime() + route.plan().ticks() + 1, roll);
        left.ifPresent(c -> RoadWork.state(server).removeRoute(c.id()));
        removeBody(server, caravan.id());
        return left;
    }

    /**
     * Robbers may fall upon a caravan on a piece of its way.
     */
    static Optional<Caravan> ambush(MinecraftServer server, Caravan caravan, CaravanRoute route, int pieceIndex, DoubleSupplier roll) {
        if (RoadWork.isReadOnly(server)) {
            return Optional.of(caravan);
        }
        int[] middle = route.middle(pieceIndex);
        TravelPlan.Piece piece = route.plan().pieces().get(pieceIndex);
        Risk risk = risk(server, caravan.dimension(), middle[0], middle[1], piece);
        if (risk.camp() == null || risk.chance() <= 0) {
            return Optional.of(caravan);
        }
        BanditCamp camp = risk.camp();
        BanditRules rules = RoadSettings.bandits();
        Body body = BODIES.get(caravan.id());
        if (body != null) {
            // Near players the ambush is a real fight.
            if (roll.getAsDouble() < risk.chance() && !FIGHTS.containsKey(caravan.id())) {
                Entity merchant = entity(server, body.dimension(), body.merchant());
                if (merchant instanceof CaravanMerchantEntity found && BanditWork.ambushInWorld(server, camp, found)) {
                    FIGHTS.put(caravan.id(), new Fight(camp.id(), KingdomWorldApi.gameTime(server) + AMBUSH_TICKS));
                    learn(server, camp, caravan);
                    camp.raided(KingdomWorldApi.gameTime(server));
                    RoadWork.state(server).changed();
                }
            }
            return Optional.of(caravan);
        }
        return strike(server, caravan, camp, risk.chance(), middle[0], middle[1], roll);
    }

    /**
     * Robbers of a camp fall upon a caravan far from players, whatever the chance; for operators.
     */
    public static Optional<Caravan> forceAmbush(MinecraftServer server, Caravan caravan, BanditCamp camp, DoubleSupplier roll) {
        double[] at = route(server, caravan).position(KingdomWorldApi.gameTime(server));
        return strike(server, caravan, camp, 1.0, (int) at[0], (int) at[1], roll);
    }

    /**
     * Robbers of a camp may fall upon a caravan far from players: the guards fight them, and if they lose, the robbers
     * take their share.
     */
    static Optional<Caravan> strike(MinecraftServer server, Caravan caravan, BanditCamp camp, double chance, int x, int z, DoubleSupplier roll) {
        BanditRules rules = RoadSettings.bandits();
        int[] middle = {x, z};
        List<ArmySquad> guards = ArmyCaravans.guards(server, caravan);
        List<Side> sides = new ArrayList<>();
        Side all = Side.of(Side.Kind.SOLDIERS, List.of());
        for (ArmySquad squad : guards) {
            Side side = ArmyWork.side(server, squad);
            sides.add(side);
            all = all.with(side);
        }
        Side bandits = camp.side(rules, rules.raidParty());
        ArmyRules armyRules = ArmySettings.rules();
        Ambushes.Ambush ambush = Ambushes.resolve(chance, all, bandits, rules, armyRules.battle(), roll);
        if (!ambush.happened()) {
            return Optional.of(caravan);
        }
        long now = KingdomWorldApi.gameTime(server);
        camp.raided(now);
        learn(server, camp, caravan);
        WorldPos where = new WorldPos(caravan.dimension(), middle[0], 64, middle[1]);
        if (ambush.battle().winner() != Battle.Winner.NONE) {
            Component what = Component.translatable(KEY + "ambush", caravan.dealId());
            for (int i = 0; i < guards.size(); i++) {
                ArmyWork.Fought fought = ArmyWork.apply(server, guards.get(i), sides.get(i), ambush.battle(), where, armyRules);
                ArmyWork.announce(server, guards.get(i), fought, what);
            }
            int lost = ambush.battle().secondLost();
            if (lost >= bandits.units()) {
                camp.lose(camp.bandits());
                camp.killLeader();
            } else {
                camp.lose(lost);
            }
        }
        RoadWork.state(server).changed();
        RoadSync.changed();
        LOGGER.info("Robbers of camp {} fell upon a caravan of deal #{}: {}", camp.id(), caravan.dealId(),
                ambush.robbed() ? "robbed of a share of " + String.format(java.util.Locale.ROOT, "%.2f", ambush.share()) : "beaten off");
        if (!ambush.robbed()) {
            for (UUID kingdom : List.of(caravan.seller(), caravan.buyer())) {
                RoadWork.notify(server, kingdom, Component.translatable(KEY + "beaten_off", caravan.dealId()).withStyle(ChatFormatting.GREEN));
            }
            return Optional.of(caravan);
        }
        return rob(server, caravan, camp, ambush.share(), roll);
    }

    /**
     * Robbers take a share of the goods of a caravan into their camp; the payment for them goes back to the buyer, and a
     * merchant of the seller may die.
     *
     * @return the caravan with the rest, or empty if nothing is left
     */
    public static Optional<Caravan> rob(MinecraftServer server, Caravan caravan, BanditCamp camp, double share, DoubleSupplier roll) {
        KingdomWorldState world = KingdomWorldApi.state(server);
        Ambushes.Split split = Ambushes.split(caravan.cargo(), share);
        long day = RoadWork.day(server);
        BanditWork.addLoot(server, camp, split.taken());
        long refund = Ambushes.refund(caravan.payment(), split.takenItems(), caravan.items());
        Component goods = Component.translatable(KEY + "goods_taken", TradeTexts.items(split.takenItems()));
        Optional<Caravan> left;
        if (split.kept().isEmpty()) {
            world.economy().refundEscrow(caravan.escrowId(), day);
            world.trade().removeCaravan(caravan.id());
            RoadWork.state(server).removeRoute(caravan.id());
            removeBody(server, caravan.id());
            left = Optional.empty();
        } else {
            if (refund > 0) {
                world.economy().refundEscrowPart(caravan.escrowId(), refund, day);
            }
            Caravan rest = new Caravan(caravan.id(), caravan.dealId(), caravan.seller(), caravan.buyer(), split.kept(), caravan.currency(),
                    caravan.payment() - refund, caravan.escrowId(), caravan.dimension(), caravan.fromX(), caravan.fromZ(), caravan.toX(),
                    caravan.toZ(), caravan.departTime(), caravan.arriveTime(), caravan.state());
            world.trade().putCaravan(rest);
            left = Optional.of(rest);
        }
        if (roll.getAsDouble() < RoadSettings.bandits().merchantDeath()) {
            killMerchant(server, caravan.seller(), new WorldPos(caravan.dimension(), camp.x(), camp.y(), camp.z()));
        }
        for (UUID kingdom : List.of(caravan.seller(), caravan.buyer())) {
            RoadWork.notify(server, kingdom, Component.translatable(left.isEmpty() ? KEY + "robbed_all" : KEY + "robbed", caravan.dealId(), goods)
                    .withStyle(ChatFormatting.RED));
        }
        LOGGER.info("Robbers took {} item(s) of a caravan of deal #{} into camp {}; {} of the payment went back to the buyer",
                split.takenItems(), caravan.dealId(), camp.id(), refund);
        return left;
    }

    private static void killMerchant(MinecraftServer server, UUID kingdomId, WorldPos where) {
        for (Citizen citizen : KingdomWorldApi.state(server).citizens().registry().all()) {
            if (citizen.kingdomId().equals(kingdomId) && citizen.isAlive() && Professions.MERCHANT.id().equals(citizen.professionId())) {
                CitizenLife.die(server, citizen.id(), CauseOfDeath.ROBBERS, "", where);
                return;
            }
        }
    }

    private static void learn(MinecraftServer server, BanditCamp camp, Caravan caravan) {
        boolean news = camp.learn(caravan.seller());
        news |= camp.learn(caravan.buyer());
        if (news) {
            RoadSync.changed();
        }
    }

    // ---------------------------------------------------------------- near players

    static Entity entity(MinecraftServer server, String dimension, UUID id) {
        ServerLevel level = ConstructionWork.level(server, dimension);
        return level == null ? null : level.getEntity(id);
    }

    private static boolean playerNear(ServerLevel level, double x, double z) {
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.distanceToSqr(x, player.getY(), z) < (double) NEAR * NEAR) {
                return true;
            }
        }
        return false;
    }

    private static void walk(MinecraftServer server, Caravan caravan, CaravanRoute route, long now) {
        ServerLevel level = ConstructionWork.level(server, caravan.dimension());
        Body body = BODIES.get(caravan.id());
        double[] at = route.position(now);
        boolean near = level != null && RoadSettings.physicalCaravans() && playerNear(level, at[0], at[1])
                && level.hasChunkAt(BlockPos.containing(at[0], 64, at[1]));
        if (!near) {
            if (body != null) {
                removeBody(server, caravan.id());
            }
            return;
        }
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at[0], 64, at[1]));
        if (body == null || !(level.getEntity(body.merchant()) instanceof CaravanMerchantEntity)) {
            if (body != null) {
                removeBody(server, caravan.id());
            }
            spawn(level, caravan, ground);
            return;
        }
        CaravanMerchantEntity merchant = (CaravanMerchantEntity) level.getEntity(body.merchant());
        double[] next = route.position(now + 60);
        BlockPos target = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(next[0], 64, next[1]));
        if (merchant.distanceToSqr(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5) > 24.0 * 24.0) {
            merchant.moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
            merchant.getNavigation().stop();
        } else if (!FIGHTS.containsKey(caravan.id())) {
            merchant.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.7);
        }
        for (UUID id : body.llamas()) {
            if (level.getEntity(id) instanceof Llama llama) {
                double distance = llama.distanceToSqr(merchant);
                if (distance > 20.0 * 20.0) {
                    llama.moveTo(merchant.getX(), merchant.getY(), merchant.getZ());
                } else if (distance > 4.0 * 4.0) {
                    llama.getNavigation().moveTo(merchant, 1.1);
                }
            }
        }
    }

    private static void spawn(ServerLevel level, Caravan caravan, BlockPos ground) {
        CaravanMerchantEntity merchant = RoadRegistries.CARAVAN_MERCHANT.get().create(level);
        if (merchant == null) {
            return;
        }
        merchant.setCaravanId(caravan.id());
        merchant.moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, 0.0f, 0.0f);
        merchant.setPersistenceRequired();
        merchant.setCustomName(Component.translatable(KEY + "merchant_name", RoadWork.kingdomName(level.getServer(), caravan.seller())));
        SPAWNED.add(merchant.getUUID());
        level.addFreshEntity(merchant);
        List<UUID> llamas = new ArrayList<>();
        int count = caravan.items() > 256 ? 2 : 1;
        for (int i = 0; i < count; i++) {
            Llama llama = EntityType.LLAMA.create(level);
            if (llama == null) {
                continue;
            }
            llama.moveTo(ground.getX() + 0.5 - 1.5 * (i + 1), ground.getY(), ground.getZ() + 0.5, 0.0f, 0.0f);
            llama.finalizeSpawn(level, level.getCurrentDifficultyAt(ground), MobSpawnType.EVENT, null, null);
            llama.setTamed(true);
            llama.addTag(PACK_TAG);
            llama.setPersistenceRequired();
            SPAWNED.add(llama.getUUID());
            level.addFreshEntity(llama);
            llamas.add(llama.getUUID());
        }
        BODIES.put(caravan.id(), new Body(merchant.getUUID(), llamas, caravan.dimension()));
    }

    static void removeBody(MinecraftServer server, long caravanId) {
        Body body = BODIES.remove(caravanId);
        if (body == null) {
            return;
        }
        ServerLevel level = ConstructionWork.level(server, body.dimension());
        if (level == null) {
            return;
        }
        Entity merchant = level.getEntity(body.merchant());
        if (merchant != null) {
            merchant.discard();
        }
        for (UUID id : body.llamas()) {
            Entity llama = level.getEntity(id);
            if (llama != null) {
                llama.discard();
            }
        }
    }

    /**
     * The merchant of the caravan of a deal walking near players, if there is one.
     */
    public static Optional<CaravanMerchantEntity> merchantOfDeal(MinecraftServer server, long dealId) {
        for (Caravan caravan : KingdomWorldApi.state(server).trade().caravans()) {
            if (caravan.dealId() != dealId || caravan.state() != Caravan.State.TRAVELING) {
                continue;
            }
            Body body = BODIES.get(caravan.id());
            if (body != null && entity(server, body.dimension(), body.merchant()) instanceof CaravanMerchantEntity merchant && merchant.isAlive()) {
                return Optional.of(merchant);
            }
        }
        return Optional.empty();
    }

    /**
     * The merchant of a caravan died near players. If robbers of an ambush killed it, they take their share.
     */
    static void merchantDied(MinecraftServer server, CaravanMerchantEntity merchant, @Nullable Entity killer) {
        long caravanId = merchant.caravanId();
        Fight fight = FIGHTS.remove(caravanId);
        BODIES.remove(caravanId);
        if (RoadWork.isReadOnly(server)) {
            return;
        }
        Optional<Caravan> caravan = KingdomWorldApi.state(server).trade().caravan(caravanId);
        if (caravan.isEmpty() || caravan.get().state() != Caravan.State.TRAVELING || !(killer instanceof BanditEntity bandit)) {
            return;
        }
        long campId = fight != null ? fight.campId() : bandit.campId();
        Optional<BanditCamp> camp = RoadWork.state(server).camp(campId);
        if (camp.isEmpty()) {
            return;
        }
        BanditWork.retreat(server, campId, caravanId);
        rob(server, caravan.get(), camp.get(), RoadSettings.bandits().unguardedLoot(), TradeWork.dice());
    }

    /**
     * Ways of caravans on roads for the maps: the points of the way of every caravan.
     */
    public static List<TravelPlan.Point> points(MinecraftServer server, Caravan caravan) {
        return RoadWork.state(server).route(caravan.id()).map(route -> {
            List<TravelPlan.Point> absolute = new ArrayList<>();
            for (TravelPlan.Point point : route.plan().points()) {
                absolute.add(new TravelPlan.Point(point.x(), point.z(), route.departTime() + point.tick()));
            }
            return absolute;
        }).orElse(List.of());
    }

    /**
     * Roads a caravan takes, for operators.
     */
    public static Set<Long> roadsOf(MinecraftServer server, Caravan caravan) {
        Set<Long> result = new HashSet<>();
        RoadWork.state(server).route(caravan.id()).ifPresent(route -> route.plan().pieces().forEach(piece -> {
            if (piece.roadId() != 0) {
                result.add(piece.roadId());
            }
        }));
        return result;
    }

    static Optional<Road> road(MinecraftServer server, long id) {
        return RoadWork.state(server).road(id);
    }
}
