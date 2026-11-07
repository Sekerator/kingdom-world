package io.github.sekerator.kingdomworld.common.npc;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNames;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.economy.TreasuryJournal;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Member;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
import io.github.sekerator.kingdomworld.core.npc.RulerDecision;
import io.github.sekerator.kingdomworld.core.npc.RulerMemory;
import io.github.sekerator.kingdomworld.core.npc.RulerMind;
import io.github.sekerator.kingdomworld.core.npc.RulerRules;
import io.github.sekerator.kingdomworld.core.npc.RulerView;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.trade.Deal;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The rulers of the kingdoms of the world. Every few game days (by the rules) a ruler looks at a snapshot of its kingdom and
 * decides a few things by its character and traits; the turns of the kingdoms are spread over ticks, one a tick, and their
 * decisions are carried out one a tick, so that no tick carries much. Answers to deals and to roads over its land come at once,
 * not with the turn. Important decrees go to the chronicle of the kingdom and to its subjects.
 */
public final class NpcRulers {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Ticks between two looks for deals and roads waiting for an answer of a ruler. */
    private static final int ANSWER_TICKS = 100;
    /** Ticks between two looks for buildings of rulers that wait for their place to be loaded. */
    private static final int LOAD_TICKS = 200;
    /** Ticks the place of a building is kept loaded so that its paid blocks are put in. */
    private static final int KEEP_LOADED_TICKS = 400;
    private static final Deque<UUID> DUE = new ArrayDeque<>();
    private static final Map<UUID, RulerView> LAST_VIEWS = new ConcurrentHashMap<>();
    private static RulerActions.Job job;
    private static int cursor;
    private static long loadingConstruction = -1;
    private static final List<Long> loadingChunks = new ArrayList<>();
    private static int loadingTicks;
    private static long turns;
    private static long busyTicks;
    private static long sumNanos;
    private static long maxNanos;

    /**
     * How long the ticks were in which rulers worked: turns taken, ticks with work, the average and the worst in milliseconds.
     */
    public record Timing(long turns, long ticks, double averageMillis, double worstMillis) {
    }

    private NpcRulers() {
    }

    static void reset() {
        DUE.clear();
        LAST_VIEWS.clear();
        job = null;
        cursor = 0;
        loadingConstruction = -1;
        loadingChunks.clear();
        loadingTicks = 0;
        turns = 0;
        busyTicks = 0;
        sumNanos = 0;
        maxNanos = 0;
        RulerPlaces.reset();
    }

    public static Timing timing() {
        return new Timing(turns, busyTicks, busyTicks == 0 ? 0 : sumNanos / 1e6 / busyTicks, maxNanos / 1e6);
    }

    public static void resetTiming() {
        turns = 0;
        busyTicks = 0;
        sumNanos = 0;
        maxNanos = 0;
    }

    static Optional<RulerView> lastView(UUID kingdomId) {
        return Optional.ofNullable(LAST_VIEWS.get(kingdomId));
    }

    /**
     * Puts the kingdoms whose rulers are due a turn in the queue; called once a game day.
     */
    static void due(MinecraftServer server, long day) {
        int every = NpcSettings.rulers().turnEveryDays();
        NpcState state = NpcWork.state(server);
        for (NpcKingdom kingdom : state.kingdoms()) {
            if (day - kingdom.lastTurnDay() >= every && ready(state, kingdom) && !DUE.contains(kingdom.kingdomId())) {
                DUE.add(kingdom.kingdomId());
            }
        }
    }

    /**
     * Puts every kingdom of the world in the queue for a turn now, for operators measuring the turns.
     *
     * @return how many kingdoms were queued
     */
    public static int queueAll(MinecraftServer server) {
        NpcState state = NpcWork.state(server);
        int count = 0;
        for (NpcKingdom kingdom : state.kingdoms()) {
            if (ready(state, kingdom) && !DUE.contains(kingdom.kingdomId())) {
                DUE.add(kingdom.kingdomId());
                count++;
            }
        }
        return count;
    }

    private static boolean ready(NpcState state, NpcKingdom kingdom) {
        return state.site(kingdom.siteId()).map(site -> site.status() == NpcSite.Status.SPAWNED).orElse(false);
    }

    // ---------------------------------------------------------------- every tick

    static void tick(MinecraftServer server) {
        long start = System.nanoTime();
        boolean worked = work(server);
        if (server.getTickCount() % ANSWER_TICKS == 0) {
            worked |= answerAll(server);
        }
        if (server.getTickCount() % LOAD_TICKS == 0 || loadingConstruction >= 0) {
            keepPlacesLoaded(server);
        }
        if (worked) {
            long spent = System.nanoTime() - start;
            busyTicks++;
            sumNanos += spent;
            maxNanos = Math.max(maxNanos, spent);
        }
    }

    /**
     * A turn, a step of a job or a decision: one of them a tick.
     */
    private static boolean work(MinecraftServer server) {
        NpcState state = NpcWork.state(server);
        long day = TreasuryActions.day(server);
        UUID next = DUE.poll();
        if (next != null) {
            state.kingdom(next).ifPresent(kingdom -> turn(server, kingdom, day, false));
            return true;
        }
        if (job != null) {
            stepJob(server, day);
            return true;
        }
        List<NpcKingdom> kingdoms = List.copyOf(state.kingdoms());
        for (int i = 0; i < kingdoms.size(); i++) {
            NpcKingdom kingdom = kingdoms.get((cursor + i) % kingdoms.size());
            Optional<RulerMemory> memory = state.existingRuler(kingdom.kingdomId());
            if (memory.isEmpty() || memory.get().pending().isEmpty()) {
                continue;
            }
            cursor = (cursor + i + 1) % Math.max(1, kingdoms.size());
            RulerDecision decision = memory.get().nextPending().orElseThrow();
            state.rulerChanged();
            carryOut(server, kingdom, decision, day);
            return true;
        }
        return false;
    }

    private static void carryOut(MinecraftServer server, NpcKingdom kingdom, RulerDecision decision, long day) {
        RulerActions.Result result = RulerActions.start(server, kingdom, decision);
        if (result.job() != null) {
            job = result.job();
            stepJob(server, day);
            return;
        }
        finish(server, kingdom.kingdomId(), result.decision(), result.outcome(), day);
    }

    private static void stepJob(MinecraftServer server, long day) {
        RulerActions.Job current = job;
        ServerLevel level = server.overworld();
        Optional<NpcKingdom> kingdom = NpcWork.kingdom(server, current.kingdomId);
        Optional<io.github.sekerator.kingdomworld.core.territory.Territory> territory = KingdomWorldApi.state(server).territory()
                .territory(current.kingdomId);
        if (territory.isPresent()) {
            ServerLevel own = Dimensions.key(territory.get().dimension()).map(server::getLevel).orElse(null);
            level = own == null ? level : own;
        }
        Outcome outcome;
        if (kingdom.isEmpty() || territory.isEmpty()) {
            outcome = Outcome.fail(io.github.sekerator.kingdomworld.core.kingdom.KingdomError.UNKNOWN_KINGDOM);
        } else {
            try {
                outcome = current.step(server, level);
            } catch (RuntimeException e) {
                LOGGER.error("A job of a ruler of the world failed: {}", current.decision, e);
                outcome = Outcome.fail(io.github.sekerator.kingdomworld.core.kingdom.KingdomError.VALUE_OUT_OF_RANGE);
            }
        }
        if (outcome == null) {
            return;
        }
        current.release(level);
        job = null;
        finish(server, current.kingdomId, current.decision, outcome, day);
    }

    /**
     * Remembers a decision that was carried out as a decree, writes the important ones into the chronicle and tells the subjects.
     */
    private static void finish(MinecraftServer server, UUID kingdomId, RulerDecision decision, Outcome outcome, long day) {
        Optional<NpcKingdom> kingdom = NpcWork.kingdom(server, kingdomId);
        if (kingdom.isEmpty()) {
            return;
        }
        if (!outcome.ok()) {
            LOGGER.debug("The ruler of {} could not {}: {}", kingdom.get().name().latin(), decision, outcome.error());
            return;
        }
        NpcState state = NpcWork.state(server);
        RulerMemory memory = state.ruler(kingdomId);
        memory.decree(day, decision);
        if (memory.writeChronicle(day, decision, NpcSettings.rulers().decreesPerDay())) {
            MutableComponent line = RulerTexts.chronicle(NpcTexts.ruler(kingdom.get()), decision, kingdom.get().rulerGender());
            LOGGER.info("Chronicle of {}: the ruler {} decreed {}", kingdom.get().name().latin(),
                    kingdom.get().rulerName(CitizenNames.Alphabet.LATIN), decision);
            tellSubjects(server, kingdomId, Component.translatable("kingdomworld.ruler.chronicle.message", NpcTexts.name(kingdom.get().name().latin(),
                    kingdom.get().name().cyrillic()), line));
        }
        state.rulerChanged();
    }

    private static void tellSubjects(MinecraftServer server, UUID kingdomId, Component message) {
        KingdomWorldApi.state(server).membership().kingdom(kingdomId).ifPresent(members -> {
            for (Member member : members.members().values()) {
                ServerPlayer player = server.getPlayerList().getPlayer(member.playerId());
                if (player != null) {
                    player.sendSystemMessage(message);
                }
            }
        });
    }

    /**
     * Tells the traders of a kingdom that a ruler of the world declined their deal, and why.
     */
    static void tellDeclined(MinecraftServer server, UUID ruler, UUID partner, RulerDecision decision) {
        Optional<NpcKingdom> kingdom = NpcWork.kingdom(server, ruler);
        if (kingdom.isEmpty()) {
            return;
        }
        Component message = Component.translatable("kingdomworld.ruler.declined", NpcTexts.name(kingdom.get().name().latin(),
                kingdom.get().name().cyrillic()), RulerTexts.reason(decision.subject()));
        KingdomWorldState state = KingdomWorldApi.state(server);
        state.membership().kingdom(partner).ifPresent(members -> {
            for (Member member : members.members().values()) {
                ServerPlayer player = server.getPlayerList().getPlayer(member.playerId());
                if (player != null && state.membership().hasPermission(member.playerId(), partner, Permission.MANAGE_TRADE)) {
                    player.sendSystemMessage(message);
                }
            }
        });
    }

    // ---------------------------------------------------------------- the turn

    /**
     * The turn of a ruler: it looks at its kingdom, answers the deals offered to it at once, remembers what its kingdom needs and
     * puts its decisions in the queue (or carries them out at once, for operators).
     *
     * @param now whether the decisions are carried out at once; jobs that need the world still wait for their ticks
     * @return the plan of the turn, or empty if the ruler could not look at its kingdom
     */
    public static Optional<RulerMind.Plan> turn(MinecraftServer server, NpcKingdom kingdom, long day, boolean now) {
        NpcState state = NpcWork.state(server);
        UUID id = kingdom.kingdomId();
        if (KingdomWorldApi.kingdom(server, id).isEmpty()) {
            return Optional.empty();
        }
        RulerRules rules = NpcSettings.rulers();
        RulerMemory memory = state.ruler(id);
        countDeals(server, id, memory);
        memory.fade();
        RulerView view = RulerSnapshots.of(server, kingdom, memory, rules, day);
        boolean poor = RulerMind.poor(view, rules);
        if (!poor) {
            RulerActions.resume(server, id);
        }
        memory.setPoorSince(poor ? (memory.poorSince() < 0 ? day : memory.poorSince()) : -1);
        RulerMind.Plan plan = RulerMind.decide(view, memory, rules);
        LAST_VIEWS.put(id, view);
        memory.setNeeds(plan.needs());
        List<String> spares = new ArrayList<>();
        view.goods.values().forEach(good -> {
            if (good.surplus() > 0) {
                spares.add(good.item());
            }
        });
        memory.setSpares(spares);
        memory.countTurn();
        for (RulerDecision answer : plan.answers()) {
            carryOut(server, kingdom, answer, day);
        }
        List<RulerDecision> fresh = new ArrayList<>();
        for (RulerDecision decision : plan.decisions()) {
            boolean waiting = memory.pending().stream().anyMatch(p -> p.kind() == decision.kind() && p.subject().equals(decision.subject())
                    && p.target().equals(decision.target()));
            boolean working = job != null && job.kingdomId.equals(id) && job.decision.kind() == decision.kind()
                    && job.decision.subject().equals(decision.subject());
            if (!waiting && !working) {
                fresh.add(decision);
            }
        }
        if (view.landToClaim || !needsLand(plan)) {
            disputes(server, id, memory, false);
        } else {
            disputes(server, id, memory, true);
        }
        memory.addPending(fresh);
        state.putKingdom(state.kingdom(id).orElse(kingdom).withLastTurnDay(day));
        state.rulerChanged();
        turns++;
        LOGGER.debug("Day {}: the ruler of {} ({}) decided {} and answered {} deal(s)", day, kingdom.name().latin(), kingdom.character().id(),
                plan.decisions(), plan.answers().size());
        if (now) {
            while (!memory.pending().isEmpty()) {
                RulerDecision decision = memory.nextPending().orElseThrow();
                if (job != null && needsJob(decision)) {
                    memory.addPending(List.of(decision));
                    break;
                }
                carryOut(server, kingdom, decision, day);
            }
        }
        return Optional.of(plan);
    }

    private static boolean needsJob(RulerDecision decision) {
        return decision.kind() == RulerDecision.Kind.BUILD || decision.kind() == RulerDecision.Kind.CLAIM
                || decision.kind() == RulerDecision.Kind.TOWN;
    }

    private static boolean needsLand(RulerMind.Plan plan) {
        return plan.needs().stream().anyMatch(need -> need.kind() == RulerMemory.NeedKind.LAND);
    }

    /**
     * Counts the closed deals of a kingdom once each for its relations: a deal made brings its partner closer, a refused offer of
     * the ruler puts it off.
     */
    private static void countDeals(MinecraftServer server, UUID id, RulerMemory memory) {
        for (Deal deal : KingdomWorldApi.state(server).trade().dealsOf(id)) {
            boolean traded = deal.status() == Deal.Status.DONE || (deal.status() == Deal.Status.ACTIVE && deal.deliveries() > 0);
            boolean refused = (deal.status() == Deal.Status.DECLINED || deal.status() == Deal.Status.EXPIRED) && id.equals(deal.proposer());
            if ((traded || refused) && memory.seeDeal(deal.id())) {
                memory.relate(deal.partnerOf(id), traded ? RulerMemory.Reason.TRADE : RulerMemory.Reason.REFUSAL);
            }
        }
    }

    /**
     * A ruler that wants land and finds none free remembers the neighbours whose land stands in its way, once in a few turns.
     */
    private static void disputes(MinecraftServer server, UUID id, RulerMemory memory, boolean blocked) {
        if (!blocked || memory.turns() % 5 != 0) {
            return;
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        state.territory().territory(id).ifPresent(own -> {
            int buffer = KingdomWorldApi.rules().claimBufferChunks() + 2;
            java.util.Set<UUID> near = new java.util.HashSet<>();
            for (long chunk : own.chunks()) {
                for (int dx = -buffer; dx <= buffer; dx++) {
                    for (int dz = -buffer; dz <= buffer; dz++) {
                        UUID owner = state.territory().ownerOf(own.dimension(), ChunkKey.offset(chunk, dx, dz));
                        if (owner != null && !owner.equals(id)) {
                            near.add(owner);
                        }
                    }
                }
                if (near.size() > 4) {
                    break;
                }
            }
            near.forEach(other -> memory.relate(other, RulerMemory.Reason.LAND));
        });
    }

    /**
     * Runs turns of a ruler one after another at once, for operators checking a kingdom: every turn's decisions that need no
     * world are carried out at once; buildings, land and towns go on over the next ticks.
     *
     * @return the money of the treasury before and after, and what the journal says came in and went out meanwhile
     */
    public static long[] runDays(MinecraftServer server, NpcKingdom kingdom, int days) {
        UUID id = kingdom.kingdomId();
        long day = TreasuryActions.day(server);
        long[] before = money(server, id, day);
        for (int i = 0; i < days; i++) {
            NpcKingdom current = NpcWork.kingdom(server, id).orElse(kingdom);
            turn(server, current, day, true);
        }
        long[] after = money(server, id, day);
        return new long[]{before[0], after[0], after[1] - before[1], after[2] - before[2]};
    }

    /**
     * The money of a treasury, and what came in and went out today by its journal.
     */
    static long[] money(MinecraftServer server, UUID id, long day) {
        Optional<Treasury> treasury = KingdomWorldApi.state(server).economy().treasury(id);
        if (treasury.isEmpty()) {
            return new long[3];
        }
        TreasuryJournal.Totals totals = treasury.get().journal().sum(day, TreasuryJournal.Period.TODAY);
        return new long[]{treasury.get().funds(), totals.income(), totals.expense()};
    }

    // ---------------------------------------------------------------- answers

    /**
     * Answers the deals offered to rulers of the world and the roads asked over their land.
     *
     * @return whether anything was answered
     */
    private static boolean answerAll(MinecraftServer server) {
        NpcState state = NpcWork.state(server);
        KingdomWorldState world = KingdomWorldApi.state(server);
        long day = TreasuryActions.day(server);
        boolean any = false;
        for (NpcKingdom kingdom : List.copyOf(state.kingdoms())) {
            if (!ready(state, kingdom)) {
                continue;
            }
            UUID id = kingdom.kingdomId();
            RulerActions.answerCrossings(server, kingdom);
            boolean offered = world.trade().dealsOf(id).stream().anyMatch(deal -> deal.status() == Deal.Status.PROPOSED && id.equals(deal.answerer()));
            if (!offered) {
                continue;
            }
            any = true;
            RulerMemory memory = state.ruler(id);
            RulerView view = RulerSnapshots.of(server, kingdom, memory, NpcSettings.rulers(), day);
            LAST_VIEWS.put(id, view);
            for (RulerView.Offer offer : view.offers) {
                RulerMind.Verdict verdict = RulerMind.judge(offer, view, memory, NpcSettings.rulers());
                carryOut(server, kingdom, new RulerDecision(verdict.accept() ? RulerDecision.Kind.ACCEPT : RulerDecision.Kind.DECLINE,
                        verdict.reason(), offer.count(), offer.deal(), offer.partner().toString(), offer.partnerName()), day);
            }
        }
        return any;
    }

    // ---------------------------------------------------------------- buildings far from players

    /**
     * Keeps the place of a construction of a ruler loaded for a while when its builders paid for blocks that wait for the place
     * to be loaded, one construction at a time, so that buildings of kingdoms nobody visits are finished too.
     */
    private static void keepPlacesLoaded(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (loadingConstruction >= 0) {
            loadingTicks++;
            Optional<Construction> construction = KingdomWorldApi.state(server).construction().get(loadingConstruction);
            if (loadingTicks < KEEP_LOADED_TICKS && construction.isPresent() && construction.get().status() == Construction.Status.WAITING_FOR_LOAD) {
                return;
            }
            for (long key : loadingChunks) {
                ChunkPos pos = new ChunkPos(ChunkKey.x(key), ChunkKey.z(key));
                level.getChunkSource().removeRegionTicket(RulerActions.TICKET, pos, 0, pos);
            }
            loadingChunks.clear();
            loadingConstruction = -1;
            loadingTicks = 0;
            return;
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        for (NpcKingdom kingdom : NpcWork.state(server).kingdoms()) {
            for (Construction construction : state.construction().of(kingdom.kingdomId())) {
                if (construction.status() != Construction.Status.WAITING_FOR_LOAD
                        || !construction.dimension().equals(Dimensions.id(level))) {
                    continue;
                }
                loadingConstruction = construction.id();
                loadingTicks = 0;
                for (long key : construction.box().chunks()) {
                    loadingChunks.add(key);
                    ChunkPos pos = new ChunkPos(ChunkKey.x(key), ChunkKey.z(key));
                    level.getChunkSource().addRegionTicket(RulerActions.TICKET, pos, 0, pos);
                }
                return;
            }
        }
    }

    /**
     * The attitude of a ruler to a player: from the player's name under its law and their dealings.
     */
    public static int attitude(MinecraftServer server, NpcKingdom kingdom, UUID player) {
        int reputation = io.github.sekerator.kingdomworld.common.law.LawWork.isReadOnly(server) ? 0
                : io.github.sekerator.kingdomworld.common.law.LawWork.reputation(server, kingdom.kingdomId(), player);
        int dealings = NpcWork.state(server).existingRuler(kingdom.kingdomId()).map(memory -> memory.dealings(player)).orElse(0);
        return RulerMind.attitude(reputation, dealings, kingdom.traits());
    }

    /**
     * The name of the kingdom of a player's king for the decrees, or the name as it is.
     */
    static String kingdomName(MinecraftServer server, UUID kingdomId) {
        return KingdomWorldApi.kingdom(server, kingdomId).map(Kingdom::name).orElse("?");
    }
}
