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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    /** A turn whose snapshot is half made: the rest is made and the decisions taken in the next tick. */
    private static Turning turning;

    private record Turning(UUID kingdomId, RulerView view, long day, int part) {
    }
    private static int cursor;
    /** The kingdom the next round of answers to deals starts with. */
    private static int answerCursor;
    /** Deals a ruler could neither take nor decline; they are not looked at again until they run out. */
    private static final Set<Long> UNANSWERABLE = new LinkedHashSet<>();
    private static long loadingConstruction = -1;
    private static final List<Long> loadingChunks = new ArrayList<>();
    private static int loadingTicks;
    private static long turns;
    private static long busyTicks;
    private static long sumNanos;
    private static long maxNanos;
    /** The worst tick without the pauses of the garbage collector that fell into it. */
    private static long maxOwnNanos;
    /** The worst tick in which a ruler looked at its kingdom, decided or answered, without the garbage collector. */
    private static long maxTurnNanos;
    /** Whether the work of the tick was a part of a turn (a look, the decisions, answers) rather than carrying one out. */
    private static boolean turnTick;
    /** What the rulers did in the tick, for the log of slow ticks. */
    private static String doing = "";
    private static String lastPart = "";
    /** Ticks of rulers longer than this are written to the log with what they did. */
    private static final long SLOW_TICK_NANOS = 50_000_000L;

    /**
     * How long the ticks were in which rulers worked: turns taken, ticks with work, the average and the worst in milliseconds.
     */
    public record Timing(long turns, long ticks, double averageMillis, double worstMillis, double worstOwnMillis, double worstTurnMillis) {
    }

    private NpcRulers() {
    }

    static void reset() {
        RulerActions.resetTools();
        DUE.clear();
        LAST_VIEWS.clear();
        job = null;
        turning = null;
        cursor = 0;
        answerCursor = 0;
        UNANSWERABLE.clear();
        loadingConstruction = -1;
        loadingChunks.clear();
        loadingTicks = 0;
        turns = 0;
        busyTicks = 0;
        sumNanos = 0;
        maxNanos = 0;
        maxOwnNanos = 0;
        maxTurnNanos = 0;
        RulerPlaces.reset();
        RulerSnapshots.reset();
    }

    public static Timing timing() {
        return new Timing(turns, busyTicks, busyTicks == 0 ? 0 : sumNanos / 1e6 / busyTicks, maxNanos / 1e6, maxOwnNanos / 1e6,
                maxTurnNanos / 1e6);
    }

    public static void resetTiming() {
        turns = 0;
        busyTicks = 0;
        sumNanos = 0;
        maxNanos = 0;
        maxOwnNanos = 0;
        maxTurnNanos = 0;
    }

    /**
     * The time the garbage collectors of the server took so far, in milliseconds.
     */
    private static long collected() {
        long total = 0;
        for (java.lang.management.GarbageCollectorMXBean bean : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
            total += Math.max(0, bean.getCollectionTime());
        }
        return total;
    }

    /**
     * Whether a job of a ruler (a building, land or a town) is going on.
     */
    public static boolean working() {
        return job != null;
    }

    /**
     * What a ruler saw of the buildings it could lay out in its last turn, for operators and game tests.
     */
    public static String lastBuilds(UUID kingdomId) {
        return lastView(kingdomId).map(view -> view.builds + ", constructions " + view.constructions + ", beds " + view.bedsUsed + "/"
                + view.bedsTotal + ", spare " + RulerMind.spare(view, NpcSettings.rulers())).orElse("-");
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

    /**
     * Whether a ruler takes turns: its kingdom stands, as data or in the world.
     */
    private static boolean ready(NpcState state, NpcKingdom kingdom) {
        return state.site(kingdom.siteId()).map(NpcSite::founded).orElse(false);
    }

    /**
     * Whether the buildings of a kingdom stand in the world, so that its ruler looks at all of it; before that it only grows its
     * land.
     */
    private static boolean standing(NpcState state, NpcKingdom kingdom) {
        return state.site(kingdom.siteId()).map(site -> site.status() == NpcSite.Status.SPAWNED).orElse(false);
    }

    /**
     * The turn of a ruler whose buildings are not raised yet: its land grows a little by the data, no chunk is loaded.
     */
    private static void growOnly(MinecraftServer server, NpcKingdom kingdom, long day) {
        NpcState state = NpcWork.state(server);
        UUID id = kingdom.kingdomId();
        RulerMemory memory = state.ruler(id);
        int growth = NpcSettings.rulers().growthChunks();
        int held = KingdomWorldApi.state(server).territory().territory(id).map(t -> t.size()).orElse(0);
        int limit = NpcSettings.rulers().maxChunks(kingdom.level());
        boolean waiting = memory.pending().stream().anyMatch(p -> p.kind() == RulerDecision.Kind.CLAIM)
                || job != null && job.kingdomId.equals(id);
        if (growth > 0 && held < limit && !waiting) {
            memory.addPending(List.of(RulerDecision.of(RulerDecision.Kind.CLAIM, "", Math.min(growth, limit - held))));
        }
        memory.countTurn();
        state.putKingdom(state.kingdom(id).orElse(kingdom).withLastTurnDay(day));
        state.rulerChanged();
        turns++;
    }

    // ---------------------------------------------------------------- every tick

    static void tick(MinecraftServer server) {
        long start = System.nanoTime();
        long gcBefore = collected();
        boolean worked = work(server);
        if (server.getTickCount() % ANSWER_TICKS == 0) {
            worked |= answerAll(server);
        }
        if (server.getTickCount() % LOAD_TICKS == 0 || loadingConstruction >= 0) {
            keepPlacesLoaded(server);
        }
        if (worked) {
            long spent = System.nanoTime() - start;
            long gc = Math.max(0, collected() - gcBefore) * 1_000_000L;
            busyTicks++;
            sumNanos += spent;
            maxNanos = Math.max(maxNanos, spent);
            maxOwnNanos = Math.max(maxOwnNanos, Math.max(0, spent - gc));
            if (turnTick) {
                maxTurnNanos = Math.max(maxTurnNanos, Math.max(0, spent - gc));
            }
            if (spent > SLOW_TICK_NANOS) {
                LOGGER.warn("A tick of the rulers of the world took {} ms ({} ms of it the garbage collector): {}{}",
                        String.format(java.util.Locale.ROOT, "%.1f", spent / 1e6), gc / 1_000_000L, doing,
                        lastPart.isEmpty() ? "" : " (" + lastPart + ")");
            }
        }
        doing = "";
        lastPart = "";
        turnTick = false;
    }

    /**
     * A turn, a step of a job or a decision: one of them a tick.
     */
    private static boolean work(MinecraftServer server) {
        NpcState state = NpcWork.state(server);
        long day = TreasuryActions.day(server);
        if (turning != null) {
            Turning current = turning;
            turning = null;
            state.kingdom(current.kingdomId()).ifPresent(kingdom -> {
                turnTick = true;
                RulerMemory memory = state.ruler(kingdom.kingdomId());
                if (current.part() < RulerSnapshots.PARTS) {
                    doing = "part " + current.part() + " of the look of " + kingdom.name().latin();
                    RulerSnapshots.next(server, kingdom, memory, current.view(), current.part());
                    turning = new Turning(current.kingdomId(), current.view(), current.day(), current.part() + 1);
                    return;
                }
                if (current.part() == RulerSnapshots.PARTS) {
                    doing = "the second look of " + kingdom.name().latin();
                    RulerSnapshots.more(server, kingdom, memory, current.view());
                    turning = new Turning(current.kingdomId(), current.view(), current.day(), current.part() + 1);
                    return;
                }
                doing = "the decisions of " + kingdom.name().latin();
                RulerSnapshots.finish(server, kingdom, NpcSettings.rulers(), current.view());
                decide(server, kingdom, current.view(), current.day(), false);
            });
            return true;
        }
        UUID next = DUE.poll();
        if (next != null) {
            state.kingdom(next).ifPresent(kingdom -> {
                doing = "the look of " + kingdom.name().latin();
                turnTick = true;
                if (KingdomWorldApi.kingdom(server, kingdom.kingdomId()).isPresent() && !standing(state, kingdom)) {
                    growOnly(server, kingdom, day);
                } else if (KingdomWorldApi.kingdom(server, kingdom.kingdomId()).isPresent()) {
                    RulerMemory memory = state.ruler(kingdom.kingdomId());
                    countDeals(server, kingdom.kingdomId(), memory);
                    memory.fade();
                    turning = new Turning(kingdom.kingdomId(), RulerSnapshots.begin(server, kingdom, memory, NpcSettings.rulers(), day), day, 1);
                }
            });
            return true;
        }
        if (job != null) {
            doing = "a step of " + job.decision;
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
            doing = kingdom.name().latin() + ": " + decision;
            state.rulerChanged();
            carryOut(server, kingdom, decision, day);
            return true;
        }
        return false;
    }

    /**
     * Carries out one decision of a ruler at once, for operators and game tests; a decision that needs the world starts a job
     * that goes on over the next ticks (or with {@link #runJobs}).
     */
    public static void carryOutNow(MinecraftServer server, NpcKingdom kingdom, RulerDecision decision) {
        carryOut(server, kingdom, decision, TreasuryActions.day(server));
    }

    /**
     * Steps the job of a ruler until it ends or the steps run out, for game tests whose chunks are loaded.
     *
     * @return whether no job is left
     */
    public static boolean runJobs(MinecraftServer server, int steps) {
        long day = TreasuryActions.day(server);
        for (int i = 0; i < steps && job != null; i++) {
            stepJob(server, day);
        }
        return job == null;
    }

    private static void carryOut(MinecraftServer server, NpcKingdom kingdom, RulerDecision decision, long day) {
        RulerActions.Result result;
        try {
            result = RulerActions.start(server, kingdom, decision);
        } catch (RuntimeException e) {
            LOGGER.error("The ruler of {} could not carry out {}", kingdom.name().latin(), decision, e);
            return;
        }
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
                lastPart = current.part;
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
            LOGGER.info("Chronicle of {}: the ruler {} decreed {}", kingdom.get().name().latin(),
                    kingdom.get().rulerName(CitizenNames.Alphabet.LATIN), decision);
            tellSubjects(server, kingdomId, language -> Component.translatable("kingdomworld.ruler.chronicle.message",
                    NpcTexts.name(kingdom.get().name().latin(), kingdom.get().name().cyrillic()),
                    RulerTexts.chronicle(NpcTexts.ruler(kingdom.get()), RulerTexts.localized(decision, language), kingdom.get().rulerGender())));
        }
        state.rulerChanged();
    }

    /**
     * Tells every subject of a kingdom online a message in their language.
     */
    private static void tellSubjects(MinecraftServer server, UUID kingdomId, java.util.function.Function<String, Component> message) {
        KingdomWorldApi.state(server).membership().kingdom(kingdomId).ifPresent(members -> {
            for (Member member : members.members().values()) {
                ServerPlayer player = server.getPlayerList().getPlayer(member.playerId());
                if (player != null) {
                    player.sendSystemMessage(message.apply(player.getLanguage()));
                }
            }
        });
    }

    /**
     * The language of a player online, or English for one who is not.
     */
    static String language(MinecraftServer server, UUID player) {
        ServerPlayer online = player == null ? null : server.getPlayerList().getPlayer(player);
        return online == null ? "en_us" : online.getLanguage();
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
        RulerMemory memory = state.ruler(id);
        countDeals(server, id, memory);
        memory.fade();
        RulerView view = RulerSnapshots.of(server, kingdom, memory, NpcSettings.rulers(), day);
        return Optional.of(decide(server, kingdom, view, day, now));
    }

    /**
     * The decisions of a turn from the snapshot of the kingdom: the answers to deals at once, the supply, the needs, and the
     * decisions in the queue (or carried out at once, for operators).
     */
    private static RulerMind.Plan decide(MinecraftServer server, NpcKingdom kingdom, RulerView view, long day, boolean now) {
        NpcState state = NpcWork.state(server);
        UUID id = kingdom.kingdomId();
        RulerRules rules = NpcSettings.rulers();
        RulerMemory memory = state.ruler(id);
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
        if (!poor) {
            for (RulerDecision bought : RulerActions.supply(server, id, RulerMind.spare(view, rules))) {
                finish(server, id, bought, Outcome.DONE, day);
            }
        }
        // Workers who stand idle for want of a tool get one bought, poor or not: without tools a kingdom earns nothing.
        for (RulerDecision bought : RulerActions.tools(server, id)) {
            finish(server, id, bought, Outcome.DONE, day);
        }
        // The garrison: the fallen are replaced, the posts of officers filled and the soldiers put on their posts.
        NpcGarrison.daily(server, id);
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
        return plan;
    }

    private static boolean needsJob(RulerDecision decision) {
        return decision.kind() == RulerDecision.Kind.BUILD || decision.kind() == RulerDecision.Kind.CLAIM
                || decision.kind() == RulerDecision.Kind.TOWN || decision.kind() == RulerDecision.Kind.UPGRADE;
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
        List<NpcKingdom> kingdoms = List.copyOf(state.kingdoms());
        int count = kingdoms.size();
        // The kingdoms take their turns to answer, so that one with a deal it cannot settle does not hold up the others.
        for (int step = 0; step < count; step++) {
            int index = Math.floorMod(answerCursor + step, count);
            NpcKingdom kingdom = kingdoms.get(index);
            if (!ready(state, kingdom)) {
                continue;
            }
            UUID id = kingdom.kingdomId();
            RulerActions.answerCrossings(server, kingdom);
            boolean offered = world.trade().dealsOf(id).stream().anyMatch(deal -> deal.status() == Deal.Status.PROPOSED && id.equals(deal.answerer())
                    && !UNANSWERABLE.contains(deal.id()));
            if (!offered) {
                continue;
            }
            answerCursor = index + 1;
            any = true;
            doing = doing + " answers of " + kingdom.name().latin();
            turnTick = true;
            RulerMemory memory = state.ruler(id);
            RulerView view = look(server, kingdom, day);
            for (RulerView.Offer offer : view.offers) {
                if (UNANSWERABLE.contains(offer.deal())) {
                    continue;
                }
                RulerMind.Verdict verdict = RulerMind.judge(offer, view, memory, NpcSettings.rulers());
                carryOut(server, kingdom, new RulerDecision(verdict.accept() ? RulerDecision.Kind.ACCEPT : RulerDecision.Kind.DECLINE,
                        verdict.reason(), offer.count(), offer.deal(), offer.partner().toString(), offer.partnerName()), day);
            }
            // The deals of one kingdom a round: a look at a kingdom is not cheap.
            break;
        }
        return any;
    }

    /**
     * A look of a ruler at its kingdom without the places for buildings and land: enough to judge deals and subjects.
     */
    public static RulerView look(MinecraftServer server, NpcKingdom kingdom, long day) {
        RulerMemory memory = NpcWork.state(server).ruler(kingdom.kingdomId());
        RulerView view = RulerSnapshots.begin(server, kingdom, memory, NpcSettings.rulers(), day);
        for (int part = 1; part < RulerSnapshots.PARTS; part++) {
            RulerSnapshots.next(server, kingdom, memory, view, part);
        }
        RulerSnapshots.more(server, kingdom, memory, view);
        return view;
    }

    /**
     * Remembers a deal that a ruler could neither take nor decline.
     */
    static void unanswerable(long dealId) {
        UNANSWERABLE.add(dealId);
        while (UNANSWERABLE.size() > 256) {
            UNANSWERABLE.remove(UNANSWERABLE.iterator().next());
        }
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
     * The decrees of the chronicle of a kingdom of the world, the newest first; empty for a kingdom of players.
     */
    public static List<io.github.sekerator.kingdomworld.common.network.data.RulerInfo.Decree> chronicle(MinecraftServer server, UUID kingdomId,
                                                                                                    UUID viewer) {
        String language = language(server, viewer);
        List<io.github.sekerator.kingdomworld.common.network.data.RulerInfo.Decree> decrees = new ArrayList<>();
        NpcWork.state(server).existingRuler(kingdomId).ifPresent(memory -> {
            for (RulerMemory.Decree decree : memory.chronicle()) {
                if (decrees.size() < io.github.sekerator.kingdomworld.common.network.data.CitizenView.MAX_DECREES) {
                    decrees.add(new io.github.sekerator.kingdomworld.common.network.data.RulerInfo.Decree(decree.day(),
                            RulerTexts.localized(decree.decision(), language)));
                }
            }
        });
        return decrees;
    }

    /**
     * What the overview of a kingdom of the world shows a player of its ruler.
     */
    public static io.github.sekerator.kingdomworld.common.network.data.RulerInfo info(MinecraftServer server, NpcKingdom kingdom, UUID viewer) {
        RulerMemory memory = NpcWork.state(server).existingRuler(kingdom.kingdomId()).orElseGet(RulerMemory::new);
        String language = language(server, viewer);
        List<io.github.sekerator.kingdomworld.common.network.data.RulerInfo.Decree> decrees = new ArrayList<>();
        for (RulerMemory.Decree decree : memory.decrees()) {
            if (decrees.size() >= io.github.sekerator.kingdomworld.common.network.data.RulerInfo.MAX_DECREES) {
                break;
            }
            decrees.add(new io.github.sekerator.kingdomworld.common.network.data.RulerInfo.Decree(decree.day(),
                    RulerTexts.localized(decree.decision(), language)));
        }
        int reputation = io.github.sekerator.kingdomworld.common.law.LawWork.isReadOnly(server) ? 0
                : io.github.sekerator.kingdomworld.common.law.LawWork.reputation(server, kingdom.kingdomId(), viewer);
        int dealings = memory.dealings(viewer);
        UUID councillor = memory.councillor();
        String councillorName = councillor != null && KingdomWorldApi.state(server).membership().isMember(councillor, kingdom.kingdomId())
                ? io.github.sekerator.kingdomworld.common.kingdom.PlayerNames.name(server, councillor) : "";
        return new io.github.sekerator.kingdomworld.common.network.data.RulerInfo(decrees, memory.needs(),
                RulerMind.attitude(reputation, dealings, kingdom.traits()), reputation, dealings, councillorName,
                !councillorName.isEmpty() && councillor.equals(viewer));
    }

    /**
     * The name of the kingdom of a player's king for the decrees, or the name as it is.
     */
    static String kingdomName(MinecraftServer server, UUID kingdomId) {
        return KingdomWorldApi.kingdom(server, kingdomId).map(Kingdom::name).orElse("?");
    }
}
