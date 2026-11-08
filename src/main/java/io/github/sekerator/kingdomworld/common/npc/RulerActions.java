package io.github.sekerator.kingdomworld.common.npc;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmySettings;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.construction.BuildingEffects;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplate;
import io.github.sekerator.kingdomworld.common.construction.BuildingWork;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.economy.EconomySettings;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.guild.GuildSettings;
import io.github.sekerator.kingdomworld.common.guild.GuildWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.law.LawSettings;
import io.github.sekerator.kingdomworld.common.law.LawWork;
import io.github.sekerator.kingdomworld.common.magic.MagicSettings;
import io.github.sekerator.kingdomworld.common.magic.MagicWork;
import io.github.sekerator.kingdomworld.common.network.packet.TradePackets;
import io.github.sekerator.kingdomworld.common.production.Production;
import io.github.sekerator.kingdomworld.common.production.ProductionSettings;
import io.github.sekerator.kingdomworld.common.road.RoadActions;
import io.github.sekerator.kingdomworld.common.road.RoadPlanning;
import io.github.sekerator.kingdomworld.common.road.RoadRegistries;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.trade.TradeActions;
import io.github.sekerator.kingdomworld.common.trade.TradeSettings;
import io.github.sekerator.kingdomworld.common.trade.TradeWork;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.ArmyState;
import io.github.sekerator.kingdomworld.core.army.Soldier;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.economy.EconomyRules;
import io.github.sekerator.kingdomworld.core.guild.Contract;
import io.github.sekerator.kingdomworld.core.guild.GuildState;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.magic.Research;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcNames;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.RulerDecision;
import io.github.sekerator.kingdomworld.core.npc.RulerMemory;
import io.github.sekerator.kingdomworld.core.npc.RulerView;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadClass;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import io.github.sekerator.kingdomworld.core.trade.Deal;
import io.github.sekerator.kingdomworld.core.trade.Market;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Carries out the decisions of the rulers of the world through the same paths a king of players uses, with the kingdom itself
 * as the actor. Most decisions take one tick; laying out a building, claiming land and founding a town are jobs that load the
 * chunks they need and go on a step a tick.
 */
final class RulerActions {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final TicketType<ChunkPos> TICKET = TicketType.create("kingdomworld_ruler", Comparator.comparingLong(ChunkPos::toLong));
    /** Ticks a job waits for its chunks before it gives up. */
    private static final int LOAD_TICKS = 1200;
    /** Blocks the ground of a building or a town may differ by and still be levelled. */
    private static final int MAX_STEP = 3;
    /** Half the side of the levelled square of a new town. */
    private static final int TOWN_SQUARE = 12;
    /** Chunks a new town claims in a tick at most. */
    private static final int CLAIMS_PER_TICK = 4;
    /** Days a contract on robbers stays on the board. */
    private static final int CONTRACT_DAYS = 7;

    /**
     * What came of a decision: done, refused with a reason, or a job that goes on.
     */
    record Result(Outcome outcome, RulerDecision decision, Job job) {
        static Result done(RulerDecision decision) {
            return new Result(Outcome.DONE, decision, null);
        }

        static Result failed(KingdomError error, RulerDecision decision) {
            return new Result(Outcome.fail(error), decision, null);
        }

        static Result of(Outcome outcome, RulerDecision decision) {
            return new Result(outcome, decision, null);
        }

        static Result working(Job job) {
            return new Result(Outcome.DONE, job.decision, job);
        }
    }

    /**
     * A decision that takes more than a tick. Its chunks are kept loaded with tickets of its own until it ends.
     */
    abstract static class Job {
        final UUID kingdomId;
        RulerDecision decision;
        final Set<Long> ticketed = new LinkedHashSet<>();
        int waited;
        /** What the last step did, for the log of slow ticks. */
        String part = "";

        Job(UUID kingdomId, RulerDecision decision) {
            this.kingdomId = kingdomId;
            this.decision = decision;
        }

        /**
         * One step.
         *
         * @return the outcome once the job is over, or {@code null} while it goes on
         */
        abstract Outcome step(MinecraftServer server, ServerLevel level);

        /**
         * Asks for chunks and tells whether all of them are loaded.
         */
        boolean loaded(ServerLevel level, List<Long> chunks) {
            boolean all = true;
            for (long key : chunks) {
                ChunkPos pos = new ChunkPos(ChunkKey.x(key), ChunkKey.z(key));
                if (ticketed.add(key)) {
                    level.getChunkSource().addRegionTicket(TICKET, pos, 0, pos);
                }
                if (!level.hasChunk(pos.x, pos.z)) {
                    all = false;
                }
            }
            return all;
        }

        void release(ServerLevel level) {
            for (long key : ticketed) {
                ChunkPos pos = new ChunkPos(ChunkKey.x(key), ChunkKey.z(key));
                level.getChunkSource().removeRegionTicket(TICKET, pos, 0, pos);
            }
            ticketed.clear();
        }
    }

    private RulerActions() {
    }

    /**
     * Starts a decision of the ruler of a kingdom.
     */
    static Result start(MinecraftServer server, NpcKingdom npc, RulerDecision decision) {
        UUID id = npc.kingdomId();
        if (KingdomWorldApi.kingdom(server, id).isEmpty()) {
            return Result.failed(KingdomError.UNKNOWN_KINGDOM, decision);
        }
        try {
            return switch (decision.kind()) {
                case BUILD -> build(server, npc, decision);
                case UPGRADE -> Result.of(BuildingWork.orderUpgrade(server, null, id, parse(decision.target())).outcome(), decision);
                case REPAIR -> repair(server, id, decision);
                case CLAIM -> Result.working(new ClaimJob(id, decision));
                case TOWN -> town(server, npc, decision);
                case ORDER -> order(server, id, decision);
                case TAX -> tax(server, id, decision);
                case WAGE -> wage(server, id, decision);
                case SELL -> sell(server, id, decision);
                case BUY -> buy(server, id, decision);
                case IMPORT -> io.github.sekerator.kingdomworld.common.trade.MarketActions.orderImport(server, null, id,
                        io.github.sekerator.kingdomworld.common.trade.GoodsStacks.item(decision.subject()), (int) Math.max(1, decision.amount()))
                        .success() ? Result.done(decision) : Result.failed(KingdomError.NOT_IMPORTABLE, decision);
                case MINT -> mint(server, id, decision);
                case OFFER -> offer(server, id, decision);
                case ACCEPT -> accept(server, id, decision);
                case DECLINE -> decline(server, id, decision);
                case HIRE -> hire(server, id, decision);
                case DISMISS -> dismiss(server, id, decision);
                case PATROL -> patrol(server, id, decision);
                case GUARD -> guard(server, id, decision);
                case CONTRACT -> contract(server, id, decision);
                case ROAD -> road(server, npc, decision);
                case ENTRY_FEE -> Result.of(LawWork.change(server, "set the entry fee of a kingdom of the world",
                        law -> law.setEntryFee(null, id, decision.amount(), LawSettings.rules(), KingdomWorldApi.state(server))), decision);
                case BOUNTY -> Result.of(LawWork.change(server, "set the bounty of a kingdom of the world",
                        law -> law.setBountyReward(null, id, decision.amount(), LawSettings.rules(), KingdomWorldApi.state(server))), decision);
                case FESTIVAL -> Result.of(RulerFestivals.hold(server, id, decision.amount()), decision);
                case RESEARCH -> research(server, id, decision);
                case APPOINT -> Result.of(KingdomWorldApi.change(server, "appoint a subject of a kingdom of the world",
                        state -> state.membership().appoint(id, UUID.fromString(decision.target()), Role.TREASURER)), decision);
                case PAUSE -> pause(server, id, decision);
            };
        } catch (IllegalArgumentException e) {
            LOGGER.debug("A decision of a ruler of the world could not be read: {}", decision, e);
            return Result.failed(KingdomError.VALUE_OUT_OF_RANGE, decision);
        }
    }

    private static long parse(String text) {
        return Long.parseLong(text);
    }

    // ---------------------------------------------------------------- buildings

    private static Result build(MinecraftServer server, NpcKingdom npc, RulerDecision decision) {
        Optional<BuildingTemplate> template = io.github.sekerator.kingdomworld.common.construction.BuildingTemplates.get(decision.target())
                .filter(t -> decision.subject().equals(t.role())).or(() -> RulerPlaces.template(decision.subject()));
        if (template.isEmpty()) {
            return Result.failed(KingdomError.UNKNOWN_TEMPLATE, decision);
        }
        Optional<RulerPlaces.Spot> spot = RulerPlaces.find(server, npc, template.get());
        if (spot.isEmpty()) {
            return Result.failed(KingdomError.CONSTRUCTION_OUTSIDE_LAND, decision);
        }
        return Result.working(new BuildJob(npc.kingdomId(), decision, npc, spot.get()));
    }

    /**
     * Lays out a building on a place found from the data: its chunks are loaded, the ground beyond the wall is levelled under it
     * when it is nearly flat, and the construction is laid out for the builders with the wood of the place; what the stores lack
     * is ordered from the workshops.
     */
    private static final class BuildJob extends Job {
        private final NpcKingdom npc;
        private RulerPlaces.Spot spot;
        private boolean levelled;
        private int levelRow;

        BuildJob(UUID kingdomId, RulerDecision decision, NpcKingdom npc, RulerPlaces.Spot spot) {
            super(kingdomId, decision);
            this.npc = npc;
            this.spot = spot;
            this.levelled = spot.levelled();
        }

        @Override
        Outcome step(MinecraftServer server, ServerLevel level) {
            List<Long> chunks = new ArrayList<>(spot.box().chunks());
            part = "load";
            if (!loaded(level, chunks)) {
                return ++waited > LOAD_TICKS ? Outcome.fail(KingdomError.AREA_NOT_LOADED) : null;
            }
            BlockPos anchor = spot.anchor();
            part = levelled ? "lay" : "level";
            if (!levelled) {
                int min = Integer.MAX_VALUE;
                int max = Integer.MIN_VALUE;
                var box = spot.box();
                for (int[] corner : new int[][]{{box.minX(), box.minZ()}, {box.maxX(), box.minZ()}, {box.minX(), box.maxZ()},
                        {box.maxX(), box.maxZ()}, {(box.minX() + box.maxX()) / 2, (box.minZ() + box.maxZ()) / 2}}) {
                    int ground = NpcGround.groundAt(level, corner[0], corner[1]);
                    min = Math.min(min, ground);
                    max = Math.max(max, ground);
                }
                if (max - min > MAX_STEP) {
                    RulerPlaces.unfit(kingdomId, anchor);
                    return Outcome.fail(KingdomError.CONSTRUCTION_OUTSIDE_LAND);
                }
                int y = max;
                NpcGround.Soil soil = NpcGround.soilAt(level, (box.minX() + box.maxX()) / 2, (box.minZ() + box.maxZ()) / 2);
                // One row of the ground a tick.
                if (levelRow < box.sizeX()) {
                    int x = box.minX() + levelRow++;
                    for (int z = box.minZ(); z <= box.maxZ(); z++) {
                        NpcGround.level(level, kingdomId, x, z, y, soil.top(), soil.fill());
                    }
                    return null;
                }
                anchor = new BlockPos(anchor.getX(), y + 1, anchor.getZ());
                spot = new RulerPlaces.Spot(spot.template(), anchor, spot.placement(), spot.template().box(anchor, spot.placement()), true);
                levelled = true;
                return null;
            }
            // The wood of the buildings that stand, so that the stores of the kingdom fit the new one.
            KingdomWorldState.ConstructionChange change = ConstructionWork.lay(level, null, kingdomId, spot.template(), anchor, spot.placement(),
                    new Materials(RulerPlaces.wood(server, kingdomId), ""));
            if (change.construction().isEmpty()) {
                if (change.outcome().error() == KingdomError.PROTECTED_BLOCK || change.outcome().error() == KingdomError.CONSTRUCTION_OVERLAPS) {
                    RulerPlaces.unfit(kingdomId, anchor);
                }
                return change.outcome();
            }
            Construction laid = change.construction().get();
            long id = laid.id();
            KingdomWorldApi.change(server, "let a construction of a kingdom of the world order what it lacks",
                    state -> state.setOrderMissing(null, kingdomId, id, true).outcome());
            java.util.Map<String, Integer> lacks = ConstructionWork.plan(laid).map(plan -> ConstructionWork.missing(server, laid, plan))
                    .orElse(java.util.Map.of());
            LOGGER.info("The ruler of {} laid out {} at {}; it lacks {}", npc.name().latin(), spot.template().id(), anchor.toShortString(),
                    lacks.isEmpty() ? "nothing" : lacks);
            return Outcome.DONE;
        }
    }

    private static Result repair(MinecraftServer server, UUID id, RulerDecision decision) {
        Outcome outcome = KingdomWorldApi.change(server, "let a building of a kingdom of the world repair itself",
                state -> state.setAutoRepair(null, id, parse(decision.target()), true));
        if (outcome.ok()) {
            BuildingEffects.changed();
        }
        return Result.of(outcome, decision);
    }

    /**
     * Stops the constructions that wait for materials and the buying of goods at the market, while the treasury is poor.
     */
    private static Result pause(MinecraftServer server, UUID id, RulerDecision decision) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        int paused = 0;
        for (Construction construction : List.copyOf(state.construction().of(id))) {
            if (!construction.paused() && state.pauseConstruction(null, id, construction.id(), true).outcome().ok()) {
                paused++;
            }
        }
        int dropped = 0;
        Optional<Market> market = state.trade().existing(id);
        if (market.isPresent()) {
            for (Market.BuyEntry entry : List.copyOf(market.get().buys())) {
                if (state.removeBuy(null, id, entry.item()).ok()) {
                    dropped++;
                }
            }
        }
        return paused + dropped > 0 ? Result.done(RulerDecision.of(RulerDecision.Kind.PAUSE, "", paused)) : Result.failed(KingdomError.UNCHANGED,
                decision);
    }

    /**
     * Takes up again the constructions a poor ruler stopped; for the turn of a ruler that is not poor any more.
     */
    static int resume(MinecraftServer server, UUID id) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        int resumed = 0;
        for (Construction construction : List.copyOf(state.construction().of(id))) {
            if (construction.paused() && state.pauseConstruction(null, id, construction.id(), false).outcome().ok()) {
                resumed++;
            }
        }
        return resumed;
    }

    /**
     * What the constructions of a kingdom and the orders of its workshops lack, ordered from abroad as far as the goods can be
     * bought there, the money it may spare reaches and the day's limit of imports allows. Goods on their way count as there.
     *
     * @return the goods ordered, as decrees
     */
    static List<RulerDecision> supply(MinecraftServer server, UUID id, long spare) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        java.util.Map<String, Integer> lacking = new java.util.LinkedHashMap<>();
        for (Construction construction : state.construction().of(id)) {
            if (construction.paused()) {
                continue;
            }
            ConstructionWork.plan(construction).ifPresent(plan -> ConstructionWork.missing(server, construction, plan)
                    .forEach((item, count) -> lacking.merge(item, count, Integer::sum)));
        }
        for (io.github.sekerator.kingdomworld.core.production.Order order : state.orders().ordersOf(id)) {
            for (io.github.sekerator.kingdomworld.core.production.Order.Missing missing : order.missing()) {
                lacking.merge(missing.item(), missing.count(), Integer::sum);
            }
        }
        if (lacking.isEmpty()) {
            return List.of();
        }
        io.github.sekerator.kingdomworld.core.trade.TradeRules rules = TradeSettings.rules();
        Market market = state.trade().market(id, rules);
        for (Market.ImportOrder order : market.imports()) {
            lacking.computeIfPresent(order.item(), (item, count) -> count - order.count() > 0 ? count - order.count() : null);
        }
        long day = TreasuryActions.day(server);
        int left = rules.importMaxPerDay() - market.stats(day).imported();
        long budget = spare / 2;
        List<RulerDecision> ordered = new ArrayList<>();
        for (java.util.Map.Entry<String, Integer> entry : lacking.entrySet()) {
            if (left <= 0 || budget <= 0) {
                break;
            }
            net.minecraft.world.item.Item item = io.github.sekerator.kingdomworld.common.trade.GoodsStacks.item(entry.getKey());
            if (item == net.minecraft.world.item.Items.AIR || !io.github.sekerator.kingdomworld.common.trade.MarketActions.isImportable(server, item)) {
                continue;
            }
            double worth = io.github.sekerator.kingdomworld.common.trade.MarketStock.worth(server, item).amount();
            int count = Math.min(entry.getValue(), left);
            while (count > 0 && io.github.sekerator.kingdomworld.core.trade.Prices.importCost(worth, count, rules) > budget) {
                count /= 2;
            }
            if (count <= 0) {
                continue;
            }
            long cost = io.github.sekerator.kingdomworld.core.trade.Prices.importCost(worth, count, rules);
            if (io.github.sekerator.kingdomworld.common.trade.MarketActions.orderImport(server, null, id, item, count).success()) {
                budget -= cost;
                left -= count;
                ordered.add(RulerDecision.of(RulerDecision.Kind.IMPORT, entry.getKey(), count).withExtra(cost));
            }
        }
        return ordered;
    }

    // ---------------------------------------------------------------- land and towns

    /**
     * Claims free chunks next to the land, one a tick, after a look at whether players lived there.
     */
    private static final class ClaimJob extends Job {
        private List<Long> candidates;
        private int claimed;
        private int index;
        private int room;

        ClaimJob(UUID kingdomId, RulerDecision decision) {
            super(kingdomId, decision);
        }

        @Override
        Outcome step(MinecraftServer server, ServerLevel level) {
            int wanted = (int) Math.max(1, decision.amount());
            if (candidates == null) {
                // Claims of a ruler pass no limit of the server: the limit of the rulers is kept here.
                int held = KingdomWorldApi.state(server).territory().territory(kingdomId).map(Territory::size).orElse(0);
                int limit = NpcWork.kingdom(server, kingdomId).map(npc -> NpcSettings.rulers().maxChunks(npc.level())).orElse(held);
                room = Math.max(0, limit - held);
                candidates = room == 0 ? List.of() : RulerPlaces.claimable(server, kingdomId, Math.min(wanted, room) * 4);
            }
            wanted = Math.min(wanted, room);
            if (claimed >= wanted || index >= candidates.size()) {
                if (claimed == 0) {
                    return Outcome.fail(KingdomError.LIMIT_REACHED);
                }
                decision = RulerDecision.of(RulerDecision.Kind.CLAIM, "", claimed);
                return Outcome.DONE;
            }
            long chunk = candidates.get(index);
            if (!loaded(level, List.of(chunk))) {
                if (++waited > LOAD_TICKS) {
                    index++;
                    waited = 0;
                }
                return null;
            }
            index++;
            waited = 0;
            LevelChunk loaded = level.getChunkSource().getChunkNow(ChunkKey.x(chunk), ChunkKey.z(chunk));
            if (loaded != null && loaded.getInhabitedTime() > NpcSettings.rules().maxInhabitedMinutes() * 1200L) {
                RulerPlaces.unfitChunk(kingdomId, chunk);
                return null;
            }
            if (KingdomWorldApi.claimChunk(level, new ChunkPos(ChunkKey.x(chunk), ChunkKey.z(chunk)), kingdomId, null).ok()) {
                claimed++;
            }
            return null;
        }
    }

    private static Result town(MinecraftServer server, NpcKingdom npc, RulerDecision decision) {
        Optional<int[]> spot = RulerPlaces.townSpot(server, npc);
        if (spot.isEmpty()) {
            return Result.failed(KingdomError.CITY_TOO_CLOSE, decision);
        }
        return Result.working(new TownJob(npc, decision, spot.get()));
    }

    /**
     * Founds a town: the chunks of the way and of the square are loaded and looked at, claimed from the land of the kingdom
     * outwards, the square is levelled row by row, the town hall founded and a road laid to it from the capital.
     */
    private static final class TownJob extends Job {
        private final NpcKingdom npc;
        private final int x;
        private final int z;
        private List<Long> chunks;
        private int stage;
        private int row;
        private int y;
        private int claimIndex;
        private boolean claimedInPass;

        TownJob(NpcKingdom npc, RulerDecision decision, int[] spot) {
            super(npc.kingdomId(), decision);
            this.npc = npc;
            this.x = spot[0];
            this.z = spot[1];
        }

        @Override
        Outcome step(MinecraftServer server, ServerLevel level) {
            KingdomWorldState state = KingdomWorldApi.state(server);
            Optional<Territory> territory = state.territory().territory(kingdomId);
            if (territory.isEmpty()) {
                return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
            }
            String dimension = territory.get().dimension();
            part = "stage " + stage;
            switch (stage) {
                case 0 -> {
                    chunks = RulerPlaces.townChunks(territory.get().capital().x(), territory.get().capital().z(), x, z);
                    long added = chunks.stream().filter(chunk -> state.territory().ownerOf(dimension, chunk) == null).count();
                    int limit = NpcSettings.rulers().maxChunks(npc.level());
                    if (territory.get().size() + added > limit) {
                        return Outcome.fail(KingdomError.LIMIT_REACHED);
                    }
                    stage = 1;
                    return null;
                }
                case 1 -> {
                    if (!loaded(level, chunks)) {
                        return ++waited > LOAD_TICKS ? Outcome.fail(KingdomError.AREA_NOT_LOADED) : null;
                    }
                    long limit = NpcSettings.rules().maxInhabitedMinutes() * 1200L;
                    for (long chunk : chunks) {
                        LevelChunk loaded = level.getChunkSource().getChunkNow(ChunkKey.x(chunk), ChunkKey.z(chunk));
                        UUID owner = state.territory().ownerOf(dimension, chunk);
                        if (loaded != null && owner == null && loaded.getInhabitedTime() > limit) {
                            RulerPlaces.unfit(kingdomId, new BlockPos(x, 0, z));
                            return Outcome.fail(KingdomError.CONSTRUCTION_OUTSIDE_LAND);
                        }
                    }
                    stage = 2;
                    return null;
                }
                case 2 -> {
                    // A few claims a tick; a pass that claims nothing more ends the claiming.
                    int budget = CLAIMS_PER_TICK;
                    while (budget > 0 && claimIndex < chunks.size()) {
                        long chunk = chunks.get(claimIndex++);
                        if (state.territory().ownerOf(dimension, chunk) == null
                                && KingdomWorldApi.claimChunk(level, new ChunkPos(ChunkKey.x(chunk), ChunkKey.z(chunk)), kingdomId, null).ok()) {
                            claimedInPass = true;
                            budget--;
                        }
                    }
                    if (claimIndex < chunks.size()) {
                        return null;
                    }
                    if (claimedInPass) {
                        claimIndex = 0;
                        claimedInPass = false;
                        return null;
                    }
                    for (long chunk : chunks) {
                        if (!kingdomId.equals(state.territory().ownerOf(dimension, chunk))) {
                            RulerPlaces.unfit(kingdomId, new BlockPos(x, 0, z));
                            return Outcome.fail(KingdomError.CONSTRUCTION_OUTSIDE_LAND);
                        }
                    }
                    y = NpcSpawner.plazaHeight(level, x, z);
                    stage = 3;
                    return null;
                }
                case 3 -> {
                    int side = 2 * TOWN_SQUARE + 1;
                    NpcGround.Soil soil = NpcGround.soilAt(level, x, z);
                    int budget = Math.max(64, NpcSettings.rules().blocksPerTick() / 8);
                    while (row < side && budget > 0) {
                        int rz = z - TOWN_SQUARE + row;
                        for (int rx = x - TOWN_SQUARE; rx <= x + TOWN_SQUARE; rx++) {
                            boolean square = Math.abs(rx - x) <= 3 && Math.abs(rz - z) <= 3;
                            BlockState top = square ? Blocks.COBBLESTONE.defaultBlockState() : soil.top();
                            budget -= Math.max(1, NpcGround.level(level, kingdomId, rx, rz, y, top, soil.fill()));
                        }
                        row++;
                    }
                    if (row >= side) {
                        stage = 4;
                    }
                    return null;
                }
                default -> {
                    BlockPos hall = new BlockPos(x, y + 1, z);
                    NpcNames.Name name = townName(server, npc);
                    Outcome founded = RoadWork.foundCity(level, null, kingdomId, hall, name.latin());
                    if (!founded.ok()) {
                        return founded;
                    }
                    level.setBlock(hall, RoadRegistries.TOWN_HALL.get().defaultBlockState(), Block.UPDATE_ALL);
                    NpcWork.state(server).kingdom(kingdomId).ifPresent(record -> {
                        List<NpcNames.Name> towns = new ArrayList<>(record.towns());
                        towns.add(name);
                        NpcKingdom renamed = record.withTowns(towns);
                        NpcWork.state(server).putKingdom(renamed);
                        NpcWork.remember(renamed);
                    });
                    RoadClass roadClass = switch (npc.level()) {
                        case SMALL -> RoadClass.PATH;
                        case MEDIUM -> RoadClass.GRAVEL;
                        case LARGE -> RoadClass.PAVED;
                    };
                    Territory own = territory.get();
                    Outcome road = RoadPlanning.startAndBuild(level, kingdomId, own.capital().x(), own.capital().z(), x, z, roadClass, Road.Mode.JOINT);
                    if (!road.ok()) {
                        LOGGER.info("The road to the new town {} of {} waits: {}", name.latin(), npc.name().latin(), road.error());
                    }
                    decision = new RulerDecision(RulerDecision.Kind.TOWN, "", 1, 0, "", name.latin());
                    LOGGER.info("The ruler of {} founded the town {} at {} {}", npc.name().latin(), name.latin(), x, z);
                    return Outcome.DONE;
                }
            }
        }
    }

    private static NpcNames.Name townName(MinecraftServer server, NpcKingdom npc) {
        Set<String> taken = new HashSet<>();
        RoadWork.state(server).cities().forEach(city -> taken.add(io.github.sekerator.kingdomworld.core.kingdom.KingdomNames.key(city.name())));
        NpcWork.state(server).kingdoms().forEach(other -> other.towns().forEach(town -> taken.add(
                io.github.sekerator.kingdomworld.core.kingdom.KingdomNames.key(town.latin()))));
        KingdomWorldApi.state(server).kingdoms().all().forEach(kingdom -> taken.add(
                io.github.sekerator.kingdomworld.core.kingdom.KingdomNames.key(kingdom.name())));
        long seed = npc.rulerSeed() * 31L + npc.towns().size() * 7919L + 104_729L;
        return NpcNames.town(seed, candidate -> taken.contains(io.github.sekerator.kingdomworld.core.kingdom.KingdomNames.key(candidate)));
    }

    private static Result road(MinecraftServer server, NpcKingdom npc, RulerDecision decision) {
        UUID id = npc.kingdomId();
        UUID target = UUID.fromString(decision.target());
        KingdomWorldState state = KingdomWorldApi.state(server);
        Optional<Territory> own = state.territory().territory(id);
        Optional<Territory> other = state.territory().territory(target);
        if (own.isEmpty() || other.isEmpty()) {
            return Result.failed(KingdomError.UNKNOWN_KINGDOM, decision);
        }
        ServerLevel level = Dimensions.key(own.get().dimension()).map(server::getLevel).orElse(null);
        if (level == null) {
            return Result.failed(KingdomError.UNKNOWN_KINGDOM, decision);
        }
        RoadClass roadClass = switch (npc.level()) {
            case SMALL -> RoadClass.PATH;
            case MEDIUM -> RoadClass.GRAVEL;
            case LARGE -> RoadClass.PAVED;
        };
        int[] from = {own.get().capital().x(), own.get().capital().z()};
        int[] to = {other.get().capital().x(), other.get().capital().z()};
        // From the town of the kingdom nearest to the neighbour, when it is nearer than the capital.
        for (City city : RoadWork.state(server).citiesOf(id)) {
            if (Math.hypot(city.x() - to[0], city.z() - to[1]) < Math.hypot(from[0] - to[0], from[1] - to[1])) {
                from = new int[]{city.x(), city.z()};
            }
        }
        return Result.of(RoadPlanning.startAndBuild(level, id, from[0], from[1], to[0], to[1], roadClass, Road.Mode.PAYER), decision);
    }

    // ---------------------------------------------------------------- the household

    private static Result order(MinecraftServer server, UUID id, RulerDecision decision) {
        long now = KingdomWorldApi.gameTime(server);
        Outcome outcome = KingdomWorldApi.change(server, "order goods for a kingdom of the world", state -> state.order(null, id,
                decision.subject(), io.github.sekerator.kingdomworld.core.production.Order.Kind.KEEP, (int) Math.min(100_000, decision.amount()),
                io.github.sekerator.kingdomworld.core.production.Order.Priority.NORMAL, now, ProductionSettings.rules().maxOrders(),
                item -> Production.orderable(server, item)).outcome());
        if (outcome.ok()) {
            Production.plan(server, id);
        }
        return Result.of(outcome, decision);
    }

    private static Result tax(MinecraftServer server, UUID id, RulerDecision decision) {
        EconomyRules rules = EconomySettings.rules();
        Outcome poll = KingdomWorldApi.change(server, "set the poll tax of a kingdom of the world",
                state -> state.setPollTax(null, id, decision.amount(), rules.maxPollTax()));
        Outcome sales = KingdomWorldApi.change(server, "set the sales tax of a kingdom of the world",
                state -> state.setSalesTax(null, id, (int) decision.extra(), TradeSettings.rules()));
        return Result.of(poll.ok() || sales.ok() ? Outcome.DONE : poll, decision);
    }

    private static Result wage(MinecraftServer server, UUID id, RulerDecision decision) {
        EconomyRules rules = EconomySettings.rules();
        Set<String> professions = new LinkedHashSet<>();
        KingdomWorldApi.state(server).citizens().registry().citizensOf(id).forEach(citizen -> professions.add(citizen.professionId()));
        professions.remove(Professions.UNEMPLOYED.id());
        int changed = 0;
        for (String profession : professions) {
            long wage = Math.min(rules.maxWage(), Math.round(rules.defaultWage(profession) * decision.amount() / 100.0));
            if (KingdomWorldApi.change(server, "set a wage of a kingdom of the world", state -> state.setWage(null, id, profession, wage, rules)).ok()) {
                changed++;
            }
        }
        return changed > 0 ? Result.done(decision) : Result.failed(KingdomError.UNCHANGED, decision);
    }

    private static Result sell(MinecraftServer server, UUID id, RulerDecision decision) {
        RulerView.Good good = good(server, id, decision.subject());
        int keep = (int) Math.min(Market.MAX_COUNT, good == null ? 64 : Math.max(16, good.want() * 2));
        Outcome sell = KingdomWorldApi.change(server, "sell surplus of a kingdom of the world",
                state -> state.putSell(null, id, new Market.SellEntry(decision.subject(), 0, keep), TradeSettings.rules()));
        KingdomWorldApi.change(server, "export surplus of a kingdom of the world",
                state -> state.putExport(null, id, new Market.ExportRule(decision.subject(), keep), TradeSettings.rules()));
        return Result.of(sell, decision);
    }

    private static Result buy(MinecraftServer server, UUID id, RulerDecision decision) {
        RulerView.Good good = good(server, id, decision.subject());
        int max = (int) Math.max(1, Math.min(Market.MAX_COUNT, good == null ? decision.amount() : good.want()));
        long budget = Math.max(1, (good == null ? 1 : good.worth()) * Math.max(1, decision.amount()) * 2);
        return Result.of(KingdomWorldApi.change(server, "buy goods for a kingdom of the world",
                state -> state.putBuy(null, id, new Market.BuyEntry(decision.subject(), 0, max, budget), TradeSettings.rules())), decision);
    }

    private static RulerView.Good good(MinecraftServer server, UUID id, String item) {
        return NpcRulers.lastView(id).map(view -> view.goods.get(item)).orElse(null);
    }

    private static Result mint(MinecraftServer server, UUID id, RulerDecision decision) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        for (WorkPost post : state.citizens().posts().postsOf(id, Professions.MINTER.id())) {
            if (post.freeSeats() > 0) {
                KingdomWorldState.Hiring hiring = state.hire(null, post.pos());
                return Result.of(hiring.outcome(), decision);
            }
        }
        return Result.failed(KingdomError.UNCHANGED, decision);
    }

    private static Result research(MinecraftServer server, UUID id, RulerDecision decision) {
        Research research = null;
        for (Research candidate : Research.values()) {
            if (candidate.id().equals(decision.subject())) {
                research = candidate;
            }
        }
        if (research == null) {
            return Result.failed(KingdomError.VALUE_OUT_OF_RANGE, decision);
        }
        Research chosen = research;
        long day = TreasuryActions.day(server);
        return Result.of(MagicWork.change(server, "research in the tower of a kingdom of the world",
                magic -> magic.startResearch(null, id, chosen, day, MagicSettings.rules(), KingdomWorldApi.state(server))), decision);
    }

    // ---------------------------------------------------------------- trade

    private static Result offer(MinecraftServer server, UUID id, RulerDecision decision) {
        UUID partner = UUID.fromString(decision.target());
        boolean sells = decision.amount() >= 0;
        int count = (int) Math.min(Deal.MAX_COUNT, Math.abs(decision.amount()));
        boolean proposed = TradeActions.apply(server, null, id, new TradePackets.TradeChange(id, TradePackets.TradeChange.Action.PROPOSE,
                partner, 0, decision.subject(), count, decision.extra(), sells, true, 0)).success();
        return proposed ? Result.done(decision) : Result.failed(KingdomError.VALUE_OUT_OF_RANGE, decision);
    }

    private static Result accept(MinecraftServer server, UUID id, RulerDecision decision) {
        long dealId = decision.extra();
        Optional<Deal> deal = KingdomWorldApi.state(server).trade().deal(dealId);
        Outcome outcome = TradeWork.accept(server, null, id, dealId);
        RulerMemory memory = NpcWork.state(server).ruler(id);
        if (outcome.ok()) {
            deal.ifPresent(found -> {
                UUID partner = found.partnerOf(id);
                memory.relate(partner, RulerMemory.Reason.TRADE);
                KingdomWorldApi.kingdom(server, partner).map(io.github.sekerator.kingdomworld.core.kingdom.Kingdom::rulerId)
                        .ifPresent(king -> memory.dealt(king, 5));
                memory.seeDeal(dealId);
            });
            NpcWork.state(server).rulerChanged();
            return Result.done(decision);
        }
        if (outcome.error() == KingdomError.NOT_ENOUGH_GOODS || outcome.error() == KingdomError.NOT_ENOUGH_MONEY) {
            String reason = outcome.error() == KingdomError.NOT_ENOUGH_GOODS ? io.github.sekerator.kingdomworld.core.npc.RulerMind.NO_GOODS
                    : io.github.sekerator.kingdomworld.core.npc.RulerMind.NO_MONEY;
            return decline(server, id, new RulerDecision(RulerDecision.Kind.DECLINE, reason, decision.amount(), dealId, decision.target(),
                    decision.targetName()));
        }
        return Result.of(outcome, decision);
    }

    private static Result decline(MinecraftServer server, UUID id, RulerDecision decision) {
        long dealId = decision.extra();
        Optional<Deal> deal = KingdomWorldApi.state(server).trade().deal(dealId);
        Outcome outcome = KingdomWorldApi.change(server, "decline a deal for a kingdom of the world", state -> state.declineDeal(null, id, dealId));
        if (outcome.ok() && deal.isPresent()) {
            UUID partner = deal.get().partnerOf(id);
            RulerMemory memory = NpcWork.state(server).ruler(id);
            memory.seeDeal(dealId);
            boolean robbing = decision.subject().equals(io.github.sekerator.kingdomworld.core.npc.RulerMind.PRICE_LOW)
                    || decision.subject().equals(io.github.sekerator.kingdomworld.core.npc.RulerMind.PRICE_HIGH);
            if (robbing) {
                KingdomWorldApi.kingdom(server, partner).map(io.github.sekerator.kingdomworld.core.kingdom.Kingdom::rulerId)
                        .ifPresent(king -> memory.dealt(king, -3));
            }
            NpcWork.state(server).existingRuler(partner).ifPresent(other -> other.relate(id, RulerMemory.Reason.REFUSAL));
            NpcWork.state(server).rulerChanged();
            NpcRulers.tellDeclined(server, id, partner, decision);
        }
        return Result.of(outcome, decision);
    }

    // ---------------------------------------------------------------- the army

    private static Result hire(MinecraftServer server, UUID id, RulerDecision decision) {
        if (ArmyWork.isReadOnly(server)) {
            return Result.failed(KingdomError.READ_ONLY, decision);
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        ArmyState army = ArmyWork.state(server);
        int warriors = 0;
        int archers = 0;
        for (Soldier soldier : army.soldiersOf(id)) {
            String profession = state.citizens().get(soldier.citizenId()).map(Citizen::professionId).orElse("");
            if (profession.equals(Professions.ARCHER.id())) {
                archers++;
            } else {
                warriors++;
            }
        }
        int hired = 0;
        for (Citizen citizen : List.copyOf(state.citizens().registry().citizensOf(id))) {
            if (hired >= decision.amount()) {
                break;
            }
            if (!citizen.professionId().equals(Professions.UNEMPLOYED.id())) {
                continue;
            }
            String profession = warriors <= archers ? Professions.WARRIOR.id() : Professions.ARCHER.id();
            if (ArmyWork.recruit(server, null, id, citizen.id(), profession).ok()) {
                hired++;
                if (profession.equals(Professions.WARRIOR.id())) {
                    warriors++;
                } else {
                    archers++;
                }
            }
        }
        return hired > 0 ? Result.done(RulerDecision.of(RulerDecision.Kind.HIRE, "", hired)) : Result.failed(KingdomError.UNCHANGED, decision);
    }

    private static Result dismiss(MinecraftServer server, UUID id, RulerDecision decision) {
        if (ArmyWork.isReadOnly(server)) {
            return Result.failed(KingdomError.READ_ONLY, decision);
        }
        ArmyState army = ArmyWork.state(server);
        List<Soldier> soldiers = new ArrayList<>(army.soldiersOf(id));
        soldiers.sort(Comparator.comparingLong(Soldier::experience));
        int dismissed = 0;
        for (Soldier soldier : soldiers) {
            if (dismissed >= decision.amount()) {
                break;
            }
            Optional<ArmySquad> squad = army.squadOf(soldier.citizenId());
            if (squad.isPresent() && ArmyWork.onTheRoad(server, squad.get())) {
                continue;
            }
            if (ArmyWork.dismiss(server, null, soldier.citizenId()).ok()) {
                dismissed++;
            }
        }
        return dismissed > 0 ? Result.done(RulerDecision.of(RulerDecision.Kind.DISMISS, "", dismissed))
                : Result.failed(KingdomError.UNCHANGED, decision);
    }

    /**
     * A squad for a new task: the soldiers outside any squad form one, else a squad that only trains.
     */
    private static Optional<ArmySquad> freeSquad(MinecraftServer server, UUID id) {
        ArmyState army = ArmyWork.state(server);
        List<UUID> free = new ArrayList<>();
        for (Soldier soldier : army.soldiersOf(id)) {
            if (army.squadOf(soldier.citizenId()).isEmpty() && free.size() < 4) {
                free.add(soldier.citizenId());
            }
        }
        if (free.size() >= 2) {
            ArmyState.Formed[] formed = new ArmyState.Formed[1];
            ArmyWork.change(server, "form a squad of a kingdom of the world", state -> {
                formed[0] = state.formSquad(null, id, free, ArmySettings.rules(), KingdomWorldApi.state(server));
                return formed[0].outcome();
            });
            if (formed[0] != null && formed[0].squad().isPresent()) {
                return formed[0].squad();
            }
        }
        return army.squadsOf(id).stream().filter(squad -> squad.order().kind() == io.github.sekerator.kingdomworld.core.army.Order.Kind.NONE)
                .findFirst();
    }

    private static Result patrol(MinecraftServer server, UUID id, RulerDecision decision) {
        if (ArmyWork.isReadOnly(server)) {
            return Result.failed(KingdomError.READ_ONLY, decision);
        }
        List<Road> roads = RoadWork.state(server).roadsOf(id).stream().filter(road -> road.kingdomId().equals(id)).toList();
        if (roads.isEmpty()) {
            return Result.failed(KingdomError.UNCHANGED, decision);
        }
        Road road = roads.get(0);
        double nearest = Double.MAX_VALUE;
        for (Road candidate : roads) {
            for (var camp : RoadWork.state(server).camps()) {
                if (camp.cleared() || candidate.cells().isEmpty()) {
                    continue;
                }
                var cell = candidate.cells().get(candidate.cells().size() / 2);
                double distance = Math.hypot(camp.x() - cell.x(), camp.z() - cell.z());
                if (distance < nearest) {
                    nearest = distance;
                    road = candidate;
                }
            }
        }
        Optional<ArmySquad> squad = freeSquad(server, id);
        if (squad.isEmpty()) {
            return Result.failed(KingdomError.UNCHANGED, decision);
        }
        return Result.of(ArmyWork.order(server, null, squad.get().id(),
                io.github.sekerator.kingdomworld.core.army.Order.patrol(RoadActions.patrolPoints(road))), decision);
    }

    private static Result guard(MinecraftServer server, UUID id, RulerDecision decision) {
        if (ArmyWork.isReadOnly(server)) {
            return Result.failed(KingdomError.READ_ONLY, decision);
        }
        Optional<Deal> deal = KingdomWorldApi.state(server).trade().dealsOf(id).stream()
                .filter(found -> found.status() == Deal.Status.ACTIVE && ArmyWork.state(server).escortsOf(found.id()).isEmpty()).findFirst();
        if (deal.isEmpty()) {
            return Result.failed(KingdomError.UNCHANGED, decision);
        }
        Optional<ArmySquad> squad = freeSquad(server, id);
        if (squad.isEmpty()) {
            return Result.failed(KingdomError.UNCHANGED, decision);
        }
        return Result.of(ArmyWork.order(server, null, squad.get().id(), io.github.sekerator.kingdomworld.core.army.Order.escort(deal.get().id())),
                decision);
    }

    private static Result contract(MinecraftServer server, UUID id, RulerDecision decision) {
        if (GuildWork.isReadOnly(server)) {
            return Result.failed(KingdomError.READ_ONLY, decision);
        }
        int count = (int) Math.max(1, Math.min(Contract.MAX_COUNT, decision.amount()));
        long reward = Math.max(1, decision.extra());
        int days = Math.min(CONTRACT_DAYS, GuildSettings.rules().maxContractDays);
        long now = KingdomWorldApi.gameTime(server);
        long day = TreasuryActions.day(server);
        GuildState.Posting posting = new GuildState.Posting(id, Contract.Poster.KINGDOM, id, Contract.Kind.HUNT, RulerSnapshots.BANDIT, Rank.D,
                count, reward, Rank.E, days, true);
        return Result.of(GuildWork.change(server, "post a contract on robbers for a kingdom of the world",
                guild -> guild.post(null, posting, now, day, GuildSettings.rules(), KingdomWorldApi.state(server)).outcome()), decision);
    }

    /**
     * Whether a ruler lets a road pass over its land, answered at once when a request comes.
     */
    static void answerCrossings(MinecraftServer server, NpcKingdom npc) {
        UUID id = npc.kingdomId();
        RulerMemory memory = NpcWork.state(server).ruler(id);
        for (var crossing : List.copyOf(RoadWork.state(server).crossingsOf(id))) {
            if (!crossing.owner().equals(id) || !crossing.status().open()) {
                continue;
            }
            UUID requester = crossing.requester();
            boolean accept = io.github.sekerator.kingdomworld.core.npc.RulerMind.letsPass(npc.character(), npc.traits(), memory.relation(requester));
            if (RoadActions.answer(server, id, crossing.id(), accept).success()) {
                NpcWork.state(server).existingRuler(requester)
                        .ifPresent(other -> other.relate(id, accept ? RulerMemory.Reason.PASSAGE : RulerMemory.Reason.BLOCKED));
                NpcWork.state(server).rulerChanged();
                LOGGER.info("The ruler of {} {} a road of another kingdom over its land", npc.name().latin(), accept ? "let" : "refused");
            }
        }
    }
}
