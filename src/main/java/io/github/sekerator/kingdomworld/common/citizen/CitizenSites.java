package io.github.sekerator.kingdomworld.common.citizen;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehaviors;
import io.github.sekerator.kingdomworld.common.citizen.work.WorkPostActions;
import io.github.sekerator.kingdomworld.common.citizen.work.ZoneSurveys;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.Citizens;
import io.github.sekerator.kingdomworld.core.citizen.Site;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Finds the places citizens use in kingdom land: beds, infirmaries and the workplaces of professions. Loaded
 * chunks of all kingdoms are scanned one after another, a couple per tick; chunk sections that cannot hold any
 * such block are skipped by their palette, so a scan is cheap. Placing or breaking such a block, and loading a
 * claimed chunk, schedules its chunk right away. Places that no longer match their block, or that now lie on
 * another kingdom's land, are forgotten, and their occupants look for new ones.
 *
 * <p>Stale storehouse contents (the block vanished without being broken normally, for example by a world editor)
 * are dropped into the world when their chunk is scanned.
 */
public final class CitizenSites {
    /** Chunks scanned per tick at most. */
    private static final int CHUNKS_PER_TICK = 2;
    /** Claimed chunks checked for being loaded per tick at most. */
    private static final int LOOKUPS_PER_TICK = 64;

    private record ChunkRef(String dimension, long chunk) {
    }

    private static final Set<ChunkRef> URGENT = new LinkedHashSet<>();
    private static final Deque<ChunkRef> ROUND = new ArrayDeque<>();

    private CitizenSites() {
    }

    /**
     * The kind of place the block is, or {@code null}.
     */
    @Nullable
    public static String kindOf(BlockState state) {
        if (state.getBlock() instanceof BedBlock) {
            return state.getValue(BedBlock.PART) == BedPart.HEAD ? SiteKinds.BED : null;
        }
        if (state.is(ModRegistries.INFIRMARY.get())) {
            return SiteKinds.INFIRMARY;
        }
        if (state.is(ModRegistries.WORK_POST.get())) {
            return SiteKinds.WORK_POST;
        }
        return ProfessionBehaviors.workplaceKind(state);
    }

    private static boolean mayBePlace(BlockState state) {
        return state.getBlock() instanceof BedBlock || state.is(ModRegistries.INFIRMARY.get())
                || state.is(ModRegistries.STOREHOUSE.get()) || state.is(ModRegistries.WORK_POST.get())
                || ProfessionBehaviors.isAnyWorkplace(state);
    }

    /**
     * Scans the chunk of the position soon, for example after a block was placed or broken there.
     */
    public static void schedule(ServerLevel level, BlockPos pos) {
        URGENT.add(new ChunkRef(Dimensions.id(level), ChunkKey.of(pos.getX() >> 4, pos.getZ() >> 4)));
    }

    public static void scheduleChunk(String dimension, long chunk) {
        URGENT.add(new ChunkRef(dimension, chunk));
    }

    /**
     * Scans a few chunks; called every server tick.
     */
    static void tick(MinecraftServer server) {
        if (KingdomWorldApi.isReadOnly(server)) {
            return;
        }
        int scanned = 0;
        while (!URGENT.isEmpty() && scanned < CHUNKS_PER_TICK) {
            ChunkRef next = URGENT.iterator().next();
            URGENT.remove(next);
            if (scan(server, next)) {
                scanned++;
            }
        }
        if (ROUND.isEmpty()) {
            refill(server);
        }
        int lookups = 0;
        while (!ROUND.isEmpty() && scanned < CHUNKS_PER_TICK && lookups < LOOKUPS_PER_TICK) {
            lookups++;
            if (scan(server, ROUND.poll())) {
                scanned++;
            }
        }
    }

    private static void refill(MinecraftServer server) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        for (String dimension : state.territory().dimensions()) {
            for (Long chunk : state.territory().claimsIn(dimension).keySet()) {
                ROUND.add(new ChunkRef(dimension, chunk));
            }
        }
        // Places recorded in chunks that are no longer claimed are checked as well.
        for (Site site : allSites(state)) {
            ChunkRef ref = new ChunkRef(site.pos().dimension(), site.pos().chunk());
            if (io.github.sekerator.kingdomworld.common.land.Wilderness.placeOwner(server, ref.dimension(), ref.chunk()) == null) {
                ROUND.add(ref);
            }
        }
    }

    private static List<Site> allSites(KingdomWorldState state) {
        List<Site> sites = new ArrayList<>();
        for (UUID kingdom : state.kingdoms().all().stream().map(k -> k.id()).toList()) {
            sites.addAll(state.citizens().sites().sitesOf(kingdom));
        }
        return sites;
    }

    /**
     * Scans one chunk if it is loaded.
     *
     * @return whether the chunk was loaded and scanned
     */
    static boolean scan(MinecraftServer server, ChunkRef ref) {
        Optional<ServerLevel> level = Dimensions.key(ref.dimension()).map(server::getLevel);
        if (level.isEmpty()) {
            return false;
        }
        int chunkX = ChunkKey.x(ref.chunk());
        int chunkZ = ChunkKey.z(ref.chunk());
        LevelChunk chunk = level.get().getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            return false;
        }
        scanChunk(level.get(), chunk);
        return true;
    }

    /**
     * Brings the recorded places of a loaded chunk in line with its blocks.
     */
    public static void scanChunk(ServerLevel level, LevelChunk chunk) {
        MinecraftServer server = level.getServer();
        KingdomSavedData data = KingdomSavedData.get(server);
        if (data.isReadOnly()) {
            return;
        }
        KingdomWorldState state = data.state();
        Citizens citizens = state.citizens();
        String dimension = Dimensions.id(level);
        long key = ChunkKey.of(chunk.getPos().x, chunk.getPos().z);
        // The wilds of a kingdom of the world hold no places of it: a bed or a workbench of a player there is nobody's.
        UUID owner = io.github.sekerator.kingdomworld.common.land.Wilderness.placeOwner(server, dimension, key);

        // Forget what is no longer there or no longer on the owner's land.
        for (Site site : citizens.sites().sitesInChunk(dimension, key)) {
            BlockPos pos = new BlockPos(site.pos().x(), site.pos().y(), site.pos().z());
            if (!site.kingdomId().equals(owner) || !site.kind().equals(kindOf(level.getBlockState(pos)))) {
                citizens.removeSite(site.pos());
            }
        }
        // Storehouse contents whose block vanished go into the world.
        for (StorehouseInventory inventory : List.copyOf(data.storehouses().inChunk(dimension, key))) {
            WorldPos pos = inventory.pos();
            if (pos.chunk() == key && pos.dimension().equals(dimension)) {
                BlockPos blockPos = new BlockPos(pos.x(), pos.y(), pos.z());
                if (!level.getBlockState(blockPos).is(ModRegistries.STOREHOUSE.get())) {
                    for (var stack : inventory.takeAll()) {
                        Containers.dropItemStack(level, pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5, stack);
                    }
                    data.storehouses().remove(pos);
                }
            }
        }
        if (owner == null) {
            return;
        }
        LevelChunkSection[] sections = chunk.getSections();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(CitizenSites::mayBePlace)) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state1 = section.getBlockState(x, y, z);
                        String kind = kindOf(state1);
                        if (kind != null) {
                            pos.set(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z);
                            WorldPos at = new WorldPos(dimension, pos.getX(), pos.getY(), pos.getZ());
                            if (citizens.addSite(at, kind, owner) && kind.equals(SiteKinds.WORK_POST)) {
                                WorkPostActions.onPostFound(level, at);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Records a place right after its block was placed, if it stands on kingdom land.
     */
    public static void onPlaced(ServerLevel level, BlockPos pos, BlockState state) {
        String kind = kindOf(state);
        if (kind == null || KingdomWorldApi.isReadOnly(level.getServer())) {
            return;
        }
        UUID owner = io.github.sekerator.kingdomworld.common.land.Wilderness.placeOwner(level.getServer(), Dimensions.id(level), ChunkKey.of(pos.getX() >> 4, pos.getZ() >> 4));
        if (owner != null) {
            WorldPos at = new WorldPos(Dimensions.id(level), pos.getX(), pos.getY(), pos.getZ());
            if (KingdomWorldApi.state(level.getServer()).citizens().addSite(at, kind, owner) && kind.equals(SiteKinds.WORK_POST)) {
                WorkPostActions.onPostFound(level, at);
            }
        }
    }

    /**
     * Forgets a place whose block is being removed.
     */
    public static void onRemoved(ServerLevel level, BlockPos pos) {
        if (KingdomWorldApi.isReadOnly(level.getServer())) {
            return;
        }
        WorldPos at = new WorldPos(Dimensions.id(level), pos.getX(), pos.getY(), pos.getZ());
        Citizens citizens = KingdomWorldApi.state(level.getServer()).citizens();
        if (citizens.sites().get(at).isPresent()) {
            citizens.removeSite(at);
            ZoneSurveys.forget(at);
        }
    }

    /**
     * Number of chunks waiting for a scan, for diagnostics.
     */
    public static int pending() {
        return URGENT.size() + ROUND.size();
    }

    public static void reset() {
        URGENT.clear();
        ROUND.clear();
    }

    /**
     * Places of kingdoms by kind, for tests and diagnostics.
     */
    public static Map<String, Integer> count(MinecraftServer server, UUID kingdomId) {
        Citizens citizens = KingdomWorldApi.state(server).citizens();
        return Map.of(SiteKinds.BED, citizens.sites().count(kingdomId, SiteKinds.BED),
                SiteKinds.INFIRMARY, citizens.sites().count(kingdomId, SiteKinds.INFIRMARY),
                SiteKinds.COMPOSTER, citizens.sites().count(kingdomId, SiteKinds.COMPOSTER),
                SiteKinds.WORK_POST, citizens.sites().count(kingdomId, SiteKinds.WORK_POST));
    }
}
