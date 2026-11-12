package io.github.sekerator.kingdomworld.common.npc;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.npc.ArmyCamp;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.RulerDecision;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Raises a camp of the royal army: the chunks of the way and of the camp are looked at (nobody may have lived there) and
 * claimed from the land of the kingdom outwards, the camp is levelled row by row and its buildings go up a part at a time,
 * as at the founding of the kingdom. A camp left half raised by a restart goes on from where it stood: claimed chunks stay
 * claimed, levelled ground stays level and buildings already standing are not raised again.
 */
final class CampJob extends RulerActions.Job {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Ticks the job waits for its chunks before it gives up. */
    private static final int LOAD_TICKS = 1200;
    /** Chunks a camp claims in a tick at most. */
    private static final int CLAIMS_PER_TICK = 4;
    private final NpcKingdom npc;
    private final int x;
    private final int z;
    private List<Long> chunks;
    private List<Long> square;
    private final Map<Long, CompletableFuture<Optional<CompoundTag>>> reads = new LinkedHashMap<>();
    private int stage;
    private int row;
    private int column;
    private int y;
    private int claimIndex;
    private boolean claimedInPass;
    private int building;
    private int field;

    CampJob(NpcKingdom npc, RulerDecision decision, int x, int z) {
        super(npc.kingdomId(), decision);
        this.npc = npc;
        this.x = x;
        this.z = z;
    }

    @Override
    Outcome step(MinecraftServer server, ServerLevel level) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        Optional<Territory> territory = state.territory().territory(kingdomId);
        if (territory.isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        String dimension = territory.get().dimension();
        part = "camp, stage " + stage;
        switch (stage) {
            case 0 -> {
                chunks = NpcCamps.chunks(territory.get().capital().x(), territory.get().capital().z(), x, z);
                square = new ArrayList<>();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        square.add(ChunkKey.of((x >> 4) + dx, (z >> 4) + dz));
                    }
                }
                // Known as being raised from now on, so that a restart goes on with it and no second camp is begun meanwhile.
                ArmyCamp known = NpcWork.state(server).camps(kingdomId).stream().filter(camp -> camp.x() == x && camp.z() == z)
                        .findFirst().orElse(null);
                if (known == null) {
                    NpcWork.state(server).putCamp(kingdomId, new ArmyCamp(x, z, 0, ArmyCamp.Status.BUILDING));
                } else if (known.y() > 0) {
                    // A camp levelled before a restart: the buildings go on at its height.
                    y = known.y();
                }
                stage = 1;
                return null;
            }
            case 1 -> {
                // Whether players lived on the way or in the camp: loaded chunks say it at once, the others are read from their
                // region files off the main thread.
                long limit = NpcSettings.rules().maxInhabitedMinutes() * 1200L;
                for (long chunk : chunks) {
                    if (reads.containsKey(chunk) || state.territory().ownerOf(dimension, chunk) != null) {
                        continue;
                    }
                    LevelChunk now = level.getChunkSource().getChunkNow(ChunkKey.x(chunk), ChunkKey.z(chunk));
                    reads.put(chunk, now != null ? CompletableFuture.completedFuture(Optional.of(RulerActions.inhabited(now)))
                            : level.getChunkSource().chunkMap.read(new ChunkPos(ChunkKey.x(chunk), ChunkKey.z(chunk))));
                }
                boolean squareLoaded = loaded(level, square);
                if (!squareLoaded || reads.values().stream().anyMatch(read -> !read.isDone())) {
                    return ++waited > LOAD_TICKS ? give(server, KingdomError.AREA_NOT_LOADED) : null;
                }
                for (CompletableFuture<Optional<CompoundTag>> read : reads.values()) {
                    Optional<CompoundTag> tag = read.getNow(Optional.empty());
                    if (tag.isPresent() && tag.get().getLong("InhabitedTime") > limit) {
                        return give(server, KingdomError.CONSTRUCTION_OUTSIDE_LAND);
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
                for (long chunk : square) {
                    if (!kingdomId.equals(state.territory().ownerOf(dimension, chunk))) {
                        return give(server, KingdomError.CONSTRUCTION_OUTSIDE_LAND);
                    }
                }
                if (y <= 0) {
                    Integer height = RulerActions.heightNow(level, x, z);
                    if (height == null) {
                        return ++waited > LOAD_TICKS ? give(server, KingdomError.AREA_NOT_LOADED) : null;
                    }
                    y = height;
                    NpcWork.state(server).putCamp(kingdomId, new ArmyCamp(x, z, y, ArmyCamp.Status.BUILDING));
                    stage = 3;
                } else {
                    // Levelled before a restart.
                    stage = 4;
                }
                return null;
            }
            case 3 -> {
                int reach = ArmyCamp.REACH;
                int side = 2 * reach + 1;
                NpcGround.Soil soil = NpcGround.soilAt(level, x, z);
                // Column by column: a hill or a forest may hold many blocks in one column, and the step ends as soon as the budget
                // of the tick is spent.
                int budget = Math.max(64, NpcSettings.rules().blocksPerTick() / 16);
                while (row < side && budget > 0) {
                    int rz = z - reach + row;
                    int rx = x - reach + column;
                    // The drill yard is beaten earth.
                    boolean yard = Math.abs(rx - x) <= 8 && Math.abs(rz - z) <= 7;
                    BlockState top = yard ? Blocks.COARSE_DIRT.defaultBlockState() : soil.top();
                    budget -= Math.max(1, NpcGround.level(level, kingdomId, rx, rz, y, top, soil.fill()));
                    if (++column >= side) {
                        column = 0;
                        row++;
                    }
                }
                if (row >= side) {
                    stage = 4;
                }
                return null;
            }
            case 4 -> {
                List<ArmyCamp.Building> buildings = ArmyCamp.buildings();
                int budget = NpcSpawner.blockBudget();
                Materials materials = NpcWork.state(server).site(npc.siteId()).map(NpcCity::materials).orElse(Materials.NONE);
                while (building < buildings.size() && budget > 0) {
                    ArmyCamp.Building plan = buildings.get(building);
                    int[] used = new int[1];
                    boolean standing = NpcSpawner.buildAt(server, level, kingdomId, plan.template(), x + plan.dx(), y + 1, z + plan.dz(),
                            new Placement(plan.facing(), false), materials, budget, used);
                    budget -= Math.max(1, used[0]);
                    if (standing) {
                        building++;
                    }
                }
                part = "camp, building " + building;
                if (building >= buildings.size()) {
                    stage = 5;
                }
                return null;
            }
            case 5 -> {
                // The fields, one a step.
                List<int[]> fields = ArmyCamp.fields();
                if (field < fields.size()) {
                    field(level, x + fields.get(field)[0], z + fields.get(field)[1]);
                    field++;
                    return null;
                }
                stage = 6;
                return null;
            }
            default -> {
                NpcWork.state(server).putCamp(kingdomId, new ArmyCamp(x, z, y, ArmyCamp.Status.BUILT));
                LOGGER.info("The ruler of {} raised a camp of its army at {} {} {}", npc.name().latin(), x, y, z);
                NpcGarrison.station(server, kingdomId);
                decision = new RulerDecision(RulerDecision.Kind.CAMP, "", 1, 0, x + "," + z, "");
                return Outcome.DONE;
            }
        }
    }

    /**
     * A field of the camp: nine by nine of carrots on farmland round the water in its middle, with a fence and a lantern at two
     * corners. The soldiers of the camp work it; it has no composters, so no farmer of the capital takes it.
     */
    private void field(ServerLevel level, int cx, int cz) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                NpcGround.level(level, kingdomId, cx + dx, cz + dz, y, Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState());
            }
        }
        net.minecraft.world.level.block.CropBlock crop = (net.minecraft.world.level.block.CropBlock) Blocks.CARROTS;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                BlockPos at = new BlockPos(cx + dx, y, cz + dz);
                if (dx == 0 && dz == 0) {
                    NpcGround.set(level, kingdomId, at, Blocks.WATER.defaultBlockState());
                    continue;
                }
                if (NpcGround.set(level, kingdomId, at, Blocks.FARMLAND.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, net.minecraft.world.level.block.FarmBlock.MAX_MOISTURE))) {
                    int age = Math.floorMod(cx * 31 + cz * 17 + dx * 7 + dz * 13, crop.getMaxAge() + 1);
                    NpcGround.set(level, kingdomId, at.above(), crop.getStateForAge(age));
                }
            }
        }
        for (int[] corner : new int[][]{{-5, 5}, {5, -5}}) {
            BlockPos base = new BlockPos(cx + corner[0], y + 1, cz + corner[1]);
            NpcGround.set(level, kingdomId, base, Blocks.OAK_FENCE.defaultBlockState());
            NpcGround.set(level, kingdomId, base.above(), Blocks.LANTERN.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LanternBlock.HANGING, false));
        }
    }

    /**
     * Gives up this place: it is marked unfit and forgotten as a camp, so that the ruler looks for another one.
     */
    private Outcome give(MinecraftServer server, KingdomError error) {
        RulerPlaces.unfit(kingdomId, new BlockPos(x, 0, z));
        NpcWork.state(server).removeCamp(kingdomId, x, z);
        LOGGER.info("The camp of the army of {} at {} {} was given up: {}", npc.name().latin(), x, z, error);
        return Outcome.fail(error);
    }
}
