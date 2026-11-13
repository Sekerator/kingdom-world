package io.github.sekerator.kingdomworld.common.land;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.npc.NpcSettings;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.land.ProvinceKind;
import io.github.sekerator.kingdomworld.core.land.Tier;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcNames;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
import io.github.sekerator.kingdomworld.core.npc.ProvinceGrowth;
import io.github.sekerator.kingdomworld.core.npc.Settlement;
import io.github.sekerator.kingdomworld.core.npc.SettlementLife;
import io.github.sekerator.kingdomworld.core.npc.StateLand;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.Province;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * How the states of the world grow: one wild province next to their land now and then instead of claims chunk by chunk, and
 * a village in an empty province of their core when their people grow. One state is looked at at a time: the wild provinces
 * around it in the order of {@link ProvinceGrowth#ordered}, and for the first that may be taken the time players spent in it,
 * from the headers of the region files first (a chunk never saved saw nobody) and then at most {@value #READS} chunks read off
 * the server thread, {@value #READS_PER_TICK} a tick.
 */
public final class StateGrowth {
    /** Chunks of a province read for the time players spent there, at most. */
    public static final int READS = 64;
    /** Reads started in a tick at most. */
    public static final int READS_PER_TICK = 4;
    /** Ticks between two looks for a state that is due. */
    private static final int LOOK_TICKS = 200;
    private static final Logger LOGGER = LogUtils.getLogger();

    private static Check check;
    private static int cursor;
    /** The zone around the spawn left alone, set by game tests; null for the one of the config. */
    private static volatile Integer spawnZoneOverride;

    /**
     * Sets the zone around the spawn no state grows into, for game tests; null gives the one of the config back.
     */
    public static void overrideSpawnZone(@org.jetbrains.annotations.Nullable Integer blocks) {
        spawnZoneOverride = blocks;
    }

    /**
     * The look at the wild provinces around one state.
     */
    private static final class Check {
        final UUID kingdom;
        final List<ProvinceGrowth.Candidate> candidates;
        int index;
        /** The regions of the province looked at, and the chunks of it each holds. */
        Map<Long, CompletableFuture<BitSet>> regions;
        List<Long> toRead;
        final Map<Long, CompletableFuture<Optional<CompoundTag>>> reads = new LinkedHashMap<>();
        long inhabited;

        Check(UUID kingdom, List<ProvinceGrowth.Candidate> candidates) {
            this.kingdom = kingdom;
            this.candidates = candidates;
        }
    }

    private StateGrowth() {
    }

    public static void reset() {
        check = null;
        cursor = 0;
    }

    static void tick(MinecraftServer server) {
        if (check == null && server.getTickCount() % LOOK_TICKS != 13) {
            return;
        }
        if (ProvinceSavedData.get(server).isReadOnly() || NpcWork.isReadOnly(server)) {
            check = null;
            return;
        }
        TerritoryIndex territory = LandEvents.attach(server);
        ProvinceMap map = territory.provinceMap().orElse(null);
        if (map == null) {
            return;
        }
        if (check == null) {
            check = begin(server, territory, map);
            return;
        }
        step(server, territory, map);
    }

    /**
     * The next state that is due to grow, with the wild provinces around it that may be taken but for the lives of players.
     */
    private static Check begin(MinecraftServer server, TerritoryIndex territory, ProvinceMap map) {
        List<NpcKingdom> kingdoms = new ArrayList<>(NpcWork.state(server).kingdoms());
        if (kingdoms.isEmpty()) {
            return null;
        }
        StateLand states = ProvinceSavedData.get(server).states();
        long day = TreasuryActions.day(server);
        ProvinceGrowth.Rules rules = ProvinceGrowth.Rules.DEFAULT;
        for (int n = 0; n < kingdoms.size(); n++) {
            NpcKingdom npc = kingdoms.get(Math.floorMod(cursor++, kingdoms.size()));
            UUID id = npc.kingdomId();
            Optional<StateLand.Entry> entry = states.entry(id);
            List<Integer> held = map.provincesOf(id);
            if (entry.isEmpty() || held.isEmpty()
                    || !ProvinceGrowth.due(day, entry.get().lastGrowthDay(), territory.landSize(id), entry.get().plannedChunks(), rules)) {
                continue;
            }
            List<ProvinceGrowth.Candidate> candidates = candidates(server, territory, map, id, held, rules);
            if (candidates.isEmpty()) {
                // Nothing around to take: it looks again when the days are over.
                states.grew(id, day);
                continue;
            }
            return new Check(id, candidates);
        }
        return null;
    }

    private static List<ProvinceGrowth.Candidate> candidates(MinecraftServer server, TerritoryIndex territory, ProvinceMap map, UUID id,
                                                             List<Integer> held, ProvinceGrowth.Rules rules) {
        Set<Integer> around = new HashSet<>();
        for (int province : held) {
            map.province(province).ifPresent(p -> around.addAll(p.neighbours()));
        }
        around.removeAll(held);
        if (around.isEmpty()) {
            return List.of();
        }
        // The provinces with a claim of a player in them or near them.
        Set<Integer> nearPlayers = new HashSet<>();
        for (Map.Entry<Long, UUID> claim : territory.claimsIn(map.dimension()).entrySet()) {
            if (NpcWork.isNpc(server, claim.getValue())) {
                continue;
            }
            int cx = ChunkKey.x(claim.getKey());
            int cz = ChunkKey.z(claim.getKey());
            for (int dx = -rules.playerChunks(); dx <= rules.playerChunks(); dx++) {
                for (int dz = -rules.playerChunks(); dz <= rules.playerChunks(); dz++) {
                    int at = map.provinceAt(ChunkKey.of(cx + dx, cz + dz));
                    if (at != 0) {
                        nearPlayers.add(at);
                    }
                }
            }
        }
        long neutral = 0;
        long all = 0;
        for (Province province : map.provinces()) {
            int chunks = map.chunks(province.id());
            all += chunks;
            if (map.owner(province.id()) == null) {
                neutral += chunks;
            }
        }
        BlockPos spawn = server.overworld().getSharedSpawnPos();
        Integer forced = spawnZoneOverride;
        int zone = forced != null ? forced : NpcSettings.stateRules(spawn.getX(), spawn.getZ()).spawnZone();
        Optional<Territory> land = territory.territory(id);
        int capitalX = land.map(t -> t.capital().x()).orElse(0);
        int capitalZ = land.map(t -> t.capital().z()).orElse(0);
        List<ProvinceGrowth.Candidate> list = new ArrayList<>();
        for (int near : around) {
            Optional<Province> province = map.province(near);
            if (province.isEmpty()) {
                continue;
            }
            Province p = province.get();
            int chunks = map.chunks(near);
            boolean wild = p.kind() == ProvinceKind.WILD && map.owner(near) == null && map.hold(near).isEmpty();
            boolean inSpawn = Math.hypot(p.centerX() - spawn.getX(), p.centerZ() - spawn.getZ()) < zone;
            long distance = Math.round(Math.hypot(p.centerX() - capitalX, p.centerZ() - capitalZ));
            ProvinceGrowth.Candidate candidate = new ProvinceGrowth.Candidate(near, chunks, wild, inSpawn, nearPlayers.contains(near), false,
                    ProvinceGrowth.neutralAfter(neutral, all, chunks), distance);
            if (ProvinceGrowth.refusal(candidate, rules).isEmpty()) {
                list.add(candidate);
            }
        }
        return ProvinceGrowth.ordered(list);
    }

    /**
     * The wild provinces around a state it may take but for the lives of players, in the order it looks at them; for game tests
     * and operators.
     */
    public static List<Integer> candidates(MinecraftServer server, UUID kingdomId) {
        TerritoryIndex territory = LandEvents.attach(server);
        ProvinceMap map = territory.provinceMap().orElse(null);
        if (map == null) {
            return List.of();
        }
        return candidates(server, territory, map, kingdomId, map.provincesOf(kingdomId), ProvinceGrowth.Rules.DEFAULT).stream()
                .map(ProvinceGrowth.Candidate::province).toList();
    }

    /**
     * A step of the look at the lives of players in the province at hand: the headers of its regions, then the reads.
     */
    private static void step(MinecraftServer server, TerritoryIndex territory, ProvinceMap map) {
        Check at = check;
        if (at.index >= at.candidates.size()) {
            ProvinceSavedData.get(server).states().grew(at.kingdom, TreasuryActions.day(server));
            LOGGER.info("The state of the world {} found no wild province around it to take", at.kingdom);
            check = null;
            return;
        }
        ProvinceGrowth.Candidate candidate = at.candidates.get(at.index);
        if (!map.exists(candidate.province()) || NpcWork.state(server).kingdom(at.kingdom).isEmpty()) {
            // The province or the state went while the look lasted (a plan made anew, a state taken down): the look ends, and
            // the state looks again when it is due.
            LOGGER.info("The look of the state of the world {} at the province {} ends: one of them is gone", at.kingdom, candidate.province());
            check = null;
            return;
        }
        ServerLevel level = server.overworld();
        if (at.regions == null) {
            at.regions = new LinkedHashMap<>();
            for (long key : map.tileKeysOf(candidate.province())) {
                at.regions.put(key, RegionHeaders.generated(server, ProvinceMap.tileX(key), ProvinceMap.tileZ(key)));
            }
            return;
        }
        if (at.toRead == null) {
            for (CompletableFuture<BitSet> region : at.regions.values()) {
                if (!region.isDone()) {
                    return;
                }
            }
            List<Long> generated = new ArrayList<>();
            for (Map.Entry<Long, CompletableFuture<BitSet>> region : at.regions.entrySet()) {
                BitSet bits = region.getValue().getNow(RegionHeaders.all());
                int baseX = ProvinceMap.tileX(region.getKey()) * ProvinceMap.TILE;
                int baseZ = ProvinceMap.tileZ(region.getKey()) * ProvinceMap.TILE;
                for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
                    long chunk = ChunkKey.of(baseX + (i & 31), baseZ + (i >> 5));
                    if (map.provinceAt(chunk) == candidate.province()) {
                        generated.add(chunk);
                    }
                }
            }
            // At most so many chunks, spread over all the province has seen generated.
            List<Long> sample = new ArrayList<>();
            int count = Math.min(READS, generated.size());
            for (int i = 0; i < count; i++) {
                sample.add(generated.get((int) ((long) i * generated.size() / count)));
            }
            at.toRead = sample;
            return;
        }
        long limit = NpcSettings.rules().maxInhabitedMinutes() * 1200L;
        int started = 0;
        while (at.reads.size() < at.toRead.size() && started < READS_PER_TICK) {
            long chunk = at.toRead.get(at.reads.size());
            LevelChunk loaded = level.getChunkSource().getChunkNow(ChunkKey.x(chunk), ChunkKey.z(chunk));
            if (loaded != null) {
                CompoundTag tag = new CompoundTag();
                tag.putLong("InhabitedTime", loaded.getInhabitedTime());
                at.reads.put(chunk, CompletableFuture.completedFuture(Optional.of(tag)));
            } else {
                at.reads.put(chunk, level.getChunkSource().chunkMap.read(new ChunkPos(ChunkKey.x(chunk), ChunkKey.z(chunk))));
            }
            started++;
        }
        boolean lived = false;
        for (CompletableFuture<Optional<CompoundTag>> read : at.reads.values()) {
            if (!read.isDone()) {
                return;
            }
            if (!read.isCompletedExceptionally() && read.getNow(Optional.empty()).map(tag -> tag.getLong("InhabitedTime")).orElse(0L) > limit) {
                lived = true;
            }
        }
        if (!lived && at.reads.size() < at.toRead.size()) {
            return;
        }
        if (lived) {
            LOGGER.info("The state of the world {} leaves the wild province {} alone: players lived there", at.kingdom, candidate.province());
            next(at);
            return;
        }
        long day = TreasuryActions.day(server);
        if (map.owner(candidate.province()) == null && map.hold(candidate.province()).isEmpty()) {
            territory.transferProvince(candidate.province(), at.kingdom, day);
            LOGGER.info("The state of the world {} took the wild province {} ({} chunks)", at.kingdom, candidate.province(), candidate.chunks());
        }
        ProvinceSavedData.get(server).states().grew(at.kingdom, day);
        check = null;
    }

    private static void next(Check at) {
        at.index++;
        at.regions = null;
        at.toRead = null;
        at.reads.clear();
    }

    // ---------------------------------------------------------------- villages

    /**
     * An empty province of the core of a state where a village may be: the nearest to its capital with no settlement in it.
     */
    public static Optional<Province> villageProvince(MinecraftServer server, UUID kingdomId) {
        TerritoryIndex territory = LandEvents.attach(server);
        ProvinceMap map = territory.provinceMap().orElse(null);
        if (map == null) {
            return Optional.empty();
        }
        Set<Integer> settled = new HashSet<>();
        for (Settlement settlement : NpcWork.state(server).settlementsOf(kingdomId)) {
            if (settlement.status() != Settlement.Status.RUINED && settlement.status() != Settlement.Status.BLOCKED) {
                settled.add(settlement.province());
            }
        }
        Optional<Territory> land = territory.territory(kingdomId);
        int capitalX = land.map(t -> t.capital().x()).orElse(0);
        int capitalZ = land.map(t -> t.capital().z()).orElse(0);
        Province best = null;
        double nearest = Double.MAX_VALUE;
        for (int id : map.provincesOf(kingdomId)) {
            Optional<Province> province = map.province(id);
            if (province.isEmpty() || province.get().kind() != ProvinceKind.CORE || settled.contains(id)) {
                continue;
            }
            double distance = Math.hypot(province.get().centerX() - capitalX, province.get().centerZ() - capitalZ);
            if (distance < nearest) {
                nearest = distance;
                best = province.get();
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * A state founds a village in an empty province of its core: it lives as data, as the settlements of the plan do, until
     * players come near and it is raised.
     */
    public static Outcome village(MinecraftServer server, NpcKingdom npc) {
        Optional<Province> found = villageProvince(server, npc.kingdomId());
        if (found.isEmpty()) {
            // No empty province of the core is left: a village that is not raised yet becomes a small town.
            return town(server, npc);
        }
        Province province = found.get();
        return NpcWork.change(server, "found a village of a state of the world", state -> {
            int parent = 0;
            for (Settlement settlement : state.settlementsOf(npc.kingdomId())) {
                if (settlement.tier() == Tier.CAPITAL) {
                    parent = settlement.id();
                    break;
                }
            }
            long seed = province.nameSeed() * 31 + KingdomWorldApi.gameTime(server);
            Set<String> names = state.settlementNames();
            NpcNames.Name name = NpcNames.town(seed, names::contains);
            Settlement settlement = state.addSettlement(npc.kingdomId(), province.id(), parent, Tier.VILLAGE, province.centerX(), province.centerZ(),
                    seed, name);
            int housing = SettlementLife.housing(Tier.VILLAGE, seed);
            settlement.found(housing, SettlementLife.startingPopulation(housing), 100);
            LOGGER.info("The state of the world {} founded the village {} in its province {}", npc.name().latin(), name.latin(), province.id());
            return Outcome.DONE;
        });
    }

    /**
     * A village of a state that lives as data becomes a small town: the one with the most people far enough from every town and
     * city of the world.
     */
    public static Outcome town(MinecraftServer server, NpcKingdom npc) {
        NpcState state = NpcWork.state(server);
        io.github.sekerator.kingdomworld.core.land.SettlementRules rules = io.github.sekerator.kingdomworld.core.land.SettlementRules.DEFAULT;
        Settlement best = null;
        for (Settlement village : state.settlementsOf(npc.kingdomId())) {
            if (village.tier() != Tier.VILLAGE || village.status() != Settlement.Status.DATA || !village.stage().isEmpty()) {
                continue;
            }
            boolean apart = true;
            for (Settlement other : state.settlements()) {
                if (other.id() == village.id() || other.tier().ordinal() > Tier.TOWN.ordinal() || other.status() == Settlement.Status.RUINED) {
                    continue;
                }
                long least = rules.apart(Tier.TOWN, other.tier());
                long dx = other.x() - village.x();
                long dz = other.z() - village.z();
                apart &= dx * dx + dz * dz >= least * least;
            }
            if (apart && (best == null || village.population() > best.population())) {
                best = village;
            }
        }
        if (best == null) {
            return Outcome.fail(KingdomError.CITY_TOO_CLOSE);
        }
        Settlement chosen = best;
        return NpcWork.change(server, "make a village of a state a small town", npcState -> {
            chosen.promote(Tier.TOWN);
            LOGGER.info("The state of the world {} made its village {} a small town", npc.name().latin(), chosen.name().latin());
            return Outcome.DONE;
        });
    }

    /**
     * Whether a kingdom of the world is a state of the plan: it holds provinces, and grows by them rather than by claims.
     */
    public static boolean isState(MinecraftServer server, UUID kingdomId) {
        return !LandEvents.attach(server).provincesOf(kingdomId).isEmpty();
    }
}
