package io.github.sekerator.kingdomworld.common.construction;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.BuildPlan;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.Scaffolding;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The scaffolds of the builders in the world: where one may go, putting one up for a construction, the columns builders
 * stand on now, and taking down every scaffold nobody stands on any longer once its place is loaded: after a restart, after
 * a builder was called away, and after its construction was finished, cancelled or taken down. Server thread only.
 */
public final class Scaffolds {
    /** Ticks a column counts as in use after its builder last said so: a builder that is ill works only in part of the ticks. */
    static final long HOLD_TICKS = 200;
    /** Scaffolds taken down per second where nobody needs them any longer. */
    private static final int TAKE_DOWN_PER_SECOND = 256;
    /** Ticks the roads and buildings around a construction are looked at again. */
    private static final long WAYS_TICKS = 1_200;
    /** Ticks a fall does a citizen no harm after the scaffolds under it were taken down. */
    private static final int SAFE_TICKS = 200;

    /**
     * A column a builder stands on or climbs: its scaffolds from {@code ground} up to below {@code top}.
     */
    private record Hold(String dimension, int x, int z, int ground, int top, long seen) {
        boolean covers(String dimension, int x, int y, int z) {
            return this.x == x && this.z == z && y >= ground && y <= top && this.dimension.equals(dimension);
        }
    }

    /**
     * What lies around a construction that scaffolds keep off: the columns of roads and walls, and the boxes of the buildings and
     * constructions of others.
     */
    private record Around(Set<Long> ways, List<Box> others, long when) {
    }

    private static final Map<UUID, Hold> HOLDS = new HashMap<>();
    private static final Map<Long, Around> AROUND = new HashMap<>();

    private Scaffolds() {
    }

    public static void reset() {
        HOLDS.clear();
        AROUND.clear();
    }

    public static boolean is(BlockState state) {
        return state.is(ModRegistries.SCAFFOLDING.get());
    }

    // ---------------------------------------------------------------- columns in use

    /**
     * A builder stands on a column or climbs it: its scaffolds are not taken down under it and no step of a construction
     * replaces them.
     */
    static void hold(UUID builder, String dimension, int x, int z, int ground, int top, long now) {
        HOLDS.put(builder, new Hold(dimension, x, z, ground, top, now));
    }

    static void release(UUID builder) {
        HOLDS.remove(builder);
    }

    /**
     * Whether a builder stands on a column that holds the position, or climbs it.
     */
    public static boolean inUse(String dimension, int x, int y, int z, long now) {
        for (Hold hold : HOLDS.values()) {
            if (now - hold.seen() <= HOLD_TICKS && hold.covers(dimension, x, y, z)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- where scaffolds may go

    /**
     * Whether a body may be in a cell: nothing to bump into and no liquid.
     */
    static boolean open(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    static boolean firm(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.isFaceSturdy(level, pos, Direction.UP) && state.getFluidState().isEmpty();
    }

    /**
     * Whether a scaffold of a construction may go into a cell: air or what gives way (grass, a layer of snow), holding
     * nothing; on the site or in the band around it; on the kingdom's land; not on a road or a wall and not in a building or
     * on the site of a construction of others.
     */
    public static boolean placeable(ServerLevel level, Construction construction, BlockPos pos) {
        if (!Scaffolding.inBand(construction.box(), pos.getX(), pos.getY(), pos.getZ()) || !level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty()) || state.hasBlockEntity() || is(state)) {
            return false;
        }
        if (!construction.kingdomId().equals(KingdomWorldApi.ownerAt(level, pos))) {
            return false;
        }
        Around around = around(level.getServer(), construction);
        if (around.ways().contains(BlockPos.asLong(pos.getX(), 0, pos.getZ()))) {
            return false;
        }
        for (Box other : around.others()) {
            if (other.contains(pos.getX(), pos.getY(), pos.getZ())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a block may carry a column: firm, holding nothing, and no block of the mod (a post, a storehouse).
     */
    static boolean carries(ServerLevel level, BlockPos pos) {
        if (!firm(level, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.hasBlockEntity() || is(state)) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id == null || !KingdomWorld.MOD_ID.equals(id.getNamespace());
    }

    private static Around around(MinecraftServer server, Construction construction) {
        long now = KingdomWorldApi.gameTime(server);
        Around known = AROUND.get(construction.id());
        if (known != null && now - known.when() < WAYS_TICKS && now >= known.when()) {
            return known;
        }
        Box box = construction.box();
        Box band = new Box(box.minX() - Scaffolding.BAND, box.minY() - Scaffolding.BAND, box.minZ() - Scaffolding.BAND,
                box.maxX() + Scaffolding.BAND, box.maxY() + 2, box.maxZ() + Scaffolding.BAND);
        Set<Long> ways = Earthworks.ways(server, band);
        List<Box> others = new ArrayList<>();
        KingdomWorldState state = KingdomWorldApi.state(server);
        for (Building building : state.construction().buildings()) {
            if (building.id() != construction.building() && building.dimension().equals(construction.dimension()) && building.box().intersects(band)) {
                others.add(building.box());
            }
        }
        for (Construction other : state.construction().all()) {
            if (other.id() != construction.id() && other.dimension().equals(construction.dimension()) && other.box().intersects(band)
                    && !(construction.building() > 0 && other.building() == construction.building())) {
                others.add(other.box());
            }
        }
        Around made = new Around(ways, others, now);
        if (AROUND.size() > 256) {
            AROUND.clear();
        }
        AROUND.put(construction.id(), made);
        return made;
    }

    /**
     * The place around a block as the choice of a column sees it.
     *
     * @param plan the plan of the construction: cells its blocks still go into are kept clear when there is a choice
     */
    static Scaffolding.World world(ServerLevel level, Construction construction, @Nullable BuildPlan plan, BlockPos target) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        return new Scaffolding.World() {
            /** The cells blocks of the plan still go into near the block, looked at when first asked. */
            @Nullable
            private Set<Long> planned;

            @Override
            public boolean open(int x, int y, int z) {
                return Scaffolds.open(level, cursor.set(x, y, z));
            }

            @Override
            public boolean firm(int x, int y, int z) {
                return Scaffolds.firm(level, cursor.set(x, y, z));
            }

            @Override
            public boolean placeable(int x, int y, int z) {
                return Scaffolds.placeable(level, construction, cursor.set(x, y, z));
            }

            @Override
            public boolean carries(int x, int y, int z) {
                return Scaffolds.carries(level, cursor.set(x, y, z));
            }

            @Override
            public boolean planned(int x, int y, int z) {
                if (planned == null) {
                    planned = new HashSet<>();
                    if (plan != null) {
                        int radius = Scaffolding.BAND + 8;
                        for (BuildPlan.Step step : plan.steps()) {
                            if (step.kind() == BuildPlan.Kind.PLACE && !construction.isDone(step.index())
                                    && Math.abs(step.x() - target.getX()) <= radius && Math.abs(step.z() - target.getZ()) <= radius) {
                                planned.add(Scaffolding.pack(step.x(), step.y(), step.z()));
                            }
                        }
                    }
                }
                return planned.contains(Scaffolding.pack(x, y, z));
            }
        };
    }

    // ---------------------------------------------------------------- putting up and taking down

    /**
     * Puts up a scaffold for a construction where one may go; it is kept with the construction until it is taken down.
     *
     * @return whether it stands
     */
    public static boolean put(ServerLevel level, Construction construction, BlockPos pos) {
        if (!placeable(level, construction, pos) || !construction.addScaffold(Scaffolding.pack(pos.getX(), pos.getY(), pos.getZ()))) {
            return false;
        }
        level.setBlock(pos, ModRegistries.SCAFFOLDING.get().defaultBlockState(), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.SCAFFOLDING_PLACE, SoundSource.BLOCKS, 0.8f, 1.0f);
        return true;
    }

    /**
     * Takes a scaffold down, if one stands there, and forgets it wherever it is kept.
     */
    static void takeDown(ServerLevel level, BlockPos pos) {
        if (is(level.getBlockState(pos))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.SCAFFOLDING_BREAK, SoundSource.BLOCKS, 0.6f, 1.0f);
        }
        forget(level.getServer(), Dimensions.id(level), Scaffolding.pack(pos.getX(), pos.getY(), pos.getZ()));
    }

    private static void forget(MinecraftServer server, String dimension, long cell) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        for (Construction construction : state.construction().all()) {
            if (construction.hasScaffolds() && construction.dimension().equals(dimension)) {
                construction.removeScaffold(cell);
            }
        }
        state.construction().forgetLeftScaffold(dimension, cell);
    }

    /**
     * Before a scaffold nobody holds is taken down: citizens on its column over it (a body come back where it last stood, on
     * top of a column, after a restart) are set on the ground beside its foot and kept from harm by a fall, so that no citizen is
     * ever hurt by scaffolds; a scaffold a player stands on stays for now.
     *
     * @return whether the scaffold may be taken down now
     */
    static boolean makeWayDown(ServerLevel level, BlockPos pos) {
        AABB column = new AABB(pos.getX(), pos.getY() + 1, pos.getZ(), pos.getX() + 1, pos.getY() + Scaffolding.MAX_HEIGHT + 4, pos.getZ() + 1);
        List<LivingEntity> over = level.getEntitiesOfClass(LivingEntity.class, column, entity -> entity.isAlive()
                && Mth.floor(entity.getX()) == pos.getX() && Mth.floor(entity.getZ()) == pos.getZ());
        if (over.isEmpty()) {
            return true;
        }
        for (LivingEntity entity : over) {
            if (entity instanceof Player) {
                return false;
            }
        }
        BlockPos foot = foot(level, pos);
        for (LivingEntity entity : over) {
            if (entity instanceof io.github.sekerator.kingdomworld.common.citizen.CitizenEntity citizen) {
                citizen.keepSafeFromFalls(SAFE_TICKS);
                citizen.getNavigation().stop();
                if (foot != null) {
                    citizen.moveTo(foot.getX() + 0.5, foot.getY(), foot.getZ() + 0.5, citizen.getYRot(), citizen.getXRot());
                    citizen.setDeltaMovement(Vec3.ZERO);
                }
            }
        }
        return true;
    }

    /**
     * Where a body stands on the ground at the foot of a column: beside it where there is room, else on the ground under it.
     */
    @Nullable
    private static BlockPos foot(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int i = 0; i <= Scaffolding.MAX_HEIGHT + 4 && cursor.getY() > level.getMinBuildHeight(); i++) {
            BlockPos below = cursor.below();
            if (!is(level.getBlockState(below)) && firm(level, below)) {
                BlockPos ground = cursor.immutable();
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    BlockPos beside = ground.relative(side);
                    if (open(level, beside) && open(level, beside.above()) && firm(level, beside.below()) && !is(level.getBlockState(beside.below()))) {
                        return beside;
                    }
                }
                return ground;
            }
            cursor.move(Direction.DOWN);
        }
        return null;
    }

    /**
     * Takes down the scaffolds of a construction that nobody stands on, where their place is loaded, from the top down.
     *
     * @param load whether places not loaded are loaded for it (an operator finishing the construction at once)
     * @return whether none is left
     */
    static boolean takeDownAll(ServerLevel level, Construction construction, boolean load, long now) {
        if (!construction.hasScaffolds()) {
            return true;
        }
        String dimension = construction.dimension();
        for (long cell : Scaffolding.takeDownOrder(construction.scaffolds())) {
            BlockPos pos = BlockPos.of(cell);
            if (inUse(dimension, pos.getX(), pos.getY(), pos.getZ(), now) || !load && !level.isLoaded(pos)) {
                continue;
            }
            if (load) {
                level.getChunk(pos);
            }
            if (is(level.getBlockState(pos))) {
                if (!makeWayDown(level, pos)) {
                    continue;
                }
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            construction.removeScaffold(cell);
        }
        return !construction.hasScaffolds();
    }

    /**
     * Once a second: columns nobody said for a while that it stands on are let go, and scaffolds nobody stands on are taken
     * down where their place is loaded: those of constructions under way that no builder needs now, and those left behind by
     * constructions that went away.
     */
    static void tick(MinecraftServer server, long now) {
        HOLDS.values().removeIf(hold -> now - hold.seen() > HOLD_TICKS || now < hold.seen());
        KingdomWorldState state = KingdomWorldApi.state(server);
        int budget = TAKE_DOWN_PER_SECOND;
        for (Construction construction : state.construction().all()) {
            if (budget <= 0) {
                return;
            }
            if (!construction.hasScaffolds()) {
                continue;
            }
            ServerLevel level = ConstructionWork.level(server, construction.dimension());
            if (level == null) {
                continue;
            }
            for (long cell : Scaffolding.takeDownOrder(construction.scaffolds())) {
                if (budget <= 0) {
                    break;
                }
                BlockPos pos = BlockPos.of(cell);
                if (!level.isLoaded(pos) || inUse(construction.dimension(), pos.getX(), pos.getY(), pos.getZ(), now)) {
                    continue;
                }
                if (is(level.getBlockState(pos))) {
                    if (!makeWayDown(level, pos)) {
                        continue;
                    }
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    budget--;
                }
                construction.removeScaffold(cell);
            }
        }
        for (Map.Entry<String, Set<Long>> entry : state.construction().leftScaffolds().entrySet()) {
            ServerLevel level = ConstructionWork.level(server, entry.getKey());
            Iterator<Long> cells = Scaffolding.takeDownOrder(entry.getValue()).iterator();
            while (cells.hasNext() && budget > 0) {
                long cell = cells.next();
                BlockPos pos = BlockPos.of(cell);
                if (level == null) {
                    // The dimension is gone: nothing is left to take down there.
                    state.construction().forgetLeftScaffold(entry.getKey(), cell);
                    continue;
                }
                if (!level.isLoaded(pos) || inUse(entry.getKey(), pos.getX(), pos.getY(), pos.getZ(), now)) {
                    continue;
                }
                if (is(level.getBlockState(pos))) {
                    if (!makeWayDown(level, pos)) {
                        continue;
                    }
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    budget--;
                }
                state.construction().forgetLeftScaffold(entry.getKey(), cell);
            }
        }
    }
}
