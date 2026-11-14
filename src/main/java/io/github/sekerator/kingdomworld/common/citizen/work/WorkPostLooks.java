package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.block.WorkPostBlock;
import io.github.sekerator.kingdomworld.common.block.WorkPostLook;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Keeps the board of every work post showing the profession of the post ({@link WorkPostBlock#PROFESSION}). The core tells
 * the position of every post that came, went or got another profession; the scan of a chunk tells of every post whose board
 * shows something else. Both only queue the position: the change may come from inside the placing of a block or from a
 * change of the data, where the world must not be touched. The server tick then sets the look, from the data as it is at
 * that moment, on the posts whose chunk is loaded; a chunk is never loaded for it, and the scan puts the post right when its
 * chunk loads.
 *
 * <p>The look is set without updating the neighbours, as the law sets back what vandals broke: no observer notices it.
 *
 * <p>Chunks without an owner are not scanned. When one loads holding a post that shows a profession (its kingdom gave the land
 * up or was dissolved while the chunk was unloaded), its posts are looked at in the tick too.
 */
public final class WorkPostLooks {
    /** Posts looked at per tick at most. */
    private static final int POSTS_PER_TICK = 256;
    /** Chunks without an owner gone through per tick at most. */
    private static final int CHUNKS_PER_TICK = 2;
    /** Chunks without an owner waiting at most; more are left for the next time they load. */
    private static final int MAX_CHUNKS = 4096;
    /** Only the clients hear of it: no neighbour is updated and no observer fires. */
    private static final int SILENT = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private record ChunkRef(String dimension, long chunk) {
    }

    private static final Set<WorldPos> POSTS = new LinkedHashSet<>();
    private static final Set<ChunkRef> CHUNKS = new LinkedHashSet<>();

    private WorkPostLooks() {
    }

    /**
     * Looks at the post at the position in the next ticks. Only queues it, so it may be called from anywhere.
     */
    public static void queue(WorldPos pos) {
        POSTS.add(pos);
    }

    /**
     * Whether the block is a work post showing a profession, for the check of the palette of a chunk without an owner.
     */
    public static boolean showsProfession(BlockState state) {
        return state.getBlock() instanceof WorkPostBlock && state.getValue(WorkPostBlock.PROFESSION) != WorkPostLook.NONE;
    }

    /**
     * Looks at the posts of a chunk without an owner in the next ticks, if one of its sections may hold a post showing a
     * profession. Cheap: only the palettes of the sections are looked at. The blocks are not touched here.
     */
    public static void checkUnowned(ServerLevel level, LevelChunk chunk) {
        if (CHUNKS.size() >= MAX_CHUNKS) {
            return;
        }
        for (LevelChunkSection section : chunk.getSections()) {
            if (section != null && !section.hasOnlyAir() && section.maybeHas(WorkPostLooks::showsProfession)) {
                CHUNKS.add(new ChunkRef(Dimensions.id(level), ChunkKey.of(chunk.getPos().x, chunk.getPos().z)));
                return;
            }
        }
    }

    /**
     * The look a post at the position should have by the data: its profession, or none if there is no post.
     */
    public static WorkPostLook lookOf(KingdomWorldState state, WorldPos pos) {
        return state.citizens().posts().get(pos).map(WorkPost::professionId).map(WorkPostLook::of).orElse(WorkPostLook.NONE);
    }

    /**
     * Sets the look of some queued posts; called every server tick after the scan for places.
     */
    public static void tick(MinecraftServer server) {
        if (KingdomWorldApi.isReadOnly(server)) {
            return;
        }
        apply(server, POSTS_PER_TICK, CHUNKS_PER_TICK);
    }

    /**
     * Sets the look of every queued post at once, for the tests.
     */
    public static void flush(MinecraftServer server) {
        if (KingdomWorldApi.isReadOnly(server)) {
            return;
        }
        apply(server, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    private static void apply(MinecraftServer server, int posts, int chunks) {
        int gone = 0;
        Iterator<ChunkRef> pending = CHUNKS.iterator();
        while (gone < chunks && pending.hasNext()) {
            ChunkRef ref = pending.next();
            pending.remove();
            gone++;
            queueShowing(server, ref);
        }
        if (POSTS.isEmpty()) {
            return;
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        int done = 0;
        Iterator<WorldPos> queued = POSTS.iterator();
        while (done < posts && queued.hasNext()) {
            WorldPos pos = queued.next();
            queued.remove();
            done++;
            set(server, state, pos);
        }
    }

    private static void set(MinecraftServer server, KingdomWorldState state, WorldPos at) {
        Optional<ServerLevel> level = Dimensions.key(at.dimension()).map(server::getLevel);
        if (level.isEmpty()) {
            return;
        }
        LevelChunk chunk = level.get().getChunkSource().getChunkNow(at.x() >> 4, at.z() >> 4);
        if (chunk == null) {
            // The scan sets it when the chunk loads.
            return;
        }
        BlockPos pos = new BlockPos(at.x(), at.y(), at.z());
        BlockState there = chunk.getBlockState(pos);
        if (!(there.getBlock() instanceof WorkPostBlock)) {
            // A post only in the data: there is no block to show it.
            return;
        }
        WorkPostLook look = lookOf(state, at);
        if (there.getValue(WorkPostBlock.PROFESSION) != look) {
            level.get().setBlock(pos, there.setValue(WorkPostBlock.PROFESSION, look), SILENT);
        }
    }

    /**
     * Queues the posts of a loaded chunk that show a profession.
     */
    private static void queueShowing(MinecraftServer server, ChunkRef ref) {
        Optional<ServerLevel> level = Dimensions.key(ref.dimension()).map(server::getLevel);
        if (level.isEmpty()) {
            return;
        }
        LevelChunk chunk = level.get().getChunkSource().getChunkNow(ChunkKey.x(ref.chunk()), ChunkKey.z(ref.chunk()));
        if (chunk == null) {
            return;
        }
        LevelChunkSection[] sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(WorkPostLooks::showsProfession)) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (showsProfession(section.getBlockState(x, y, z))) {
                            queue(new WorldPos(ref.dimension(), chunk.getPos().getMinBlockX() + x, baseY + y,
                                    chunk.getPos().getMinBlockZ() + z));
                        }
                    }
                }
            }
        }
    }

    /**
     * Posts and chunks waiting, for diagnostics and tests.
     */
    public static int pending() {
        return POSTS.size() + CHUNKS.size();
    }

    /**
     * Forgets what waits, when the server stops.
     */
    public static void reset() {
        POSTS.clear();
        CHUNKS.clear();
    }
}
