package io.github.sekerator.kingdomworld.common.road;

import io.github.sekerator.kingdomworld.common.citizen.work.Trees;
import io.github.sekerator.kingdomworld.common.construction.NaturalGround;
import io.github.sekerator.kingdomworld.core.road.LandColumn;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.IntFunction;

/**
 * The land as a levelled road sees it, one place for the plan and for the laying: which blocks are natural and may be dug, which
 * are soil for the builders and which rock for the miners, and what a column of the land is.
 *
 * <p>Natural for a road: the ground as {@link NaturalGround#ground} and {@link NaturalGround#wild} have it, trunks and leaves of
 * trees as they grew, plants and snow, paths (a road goes along a path and takes it up), and what the mountains and caves are made
 * of besides: calcite, dripstone, smooth basalt, amethyst, magma, the stone of the Nether and of the End and every ore. Polished
 * and carved stone, smooth and carved sandstone, cobblestone, bricks, planks, farmland, crops, pumpkins and melons, everything
 * with a block entity and every protected block are not.
 */
public final class RoadLand {
    /** Blocks under the ground a column is looked at for its caves and how far down it is natural. */
    public static final int DEPTH = 96;

    private RoadLand() {
    }

    /**
     * Whether a block is natural for a road: what it may dig and clear.
     */
    public static boolean natural(BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (state.hasBlockEntity() || protectedState(state)) {
            return false;
        }
        if (!state.getFluidState().isEmpty()) {
            // Water and lava are no blocks to dig; plants in water grew there.
            return state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT) || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS);
        }
        if (state.is(BlockTags.LEAVES)) {
            return Trees.isNaturalLeaves(state) && !NaturalGround.placedLeaves(state);
        }
        if (Trees.isLog(state)) {
            return !Trees.isWorkedWood(state);
        }
        if (state.is(Blocks.FARMLAND)) {
            return false;
        }
        return NaturalGround.ground(state) || NaturalGround.wild(state) || cover(state) || state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.CALCITE) || state.is(Blocks.DRIPSTONE_BLOCK) || state.is(Blocks.POINTED_DRIPSTONE)
                || state.is(Blocks.SMOOTH_BASALT) || state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.BUDDING_AMETHYST)
                || state.is(Blocks.AMETHYST_CLUSTER) || state.is(Blocks.LARGE_AMETHYST_BUD) || state.is(Blocks.MEDIUM_AMETHYST_BUD)
                || state.is(Blocks.SMALL_AMETHYST_BUD) || state.is(Blocks.MAGMA_BLOCK) || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(Tags.Blocks.END_STONES) || state.is(Tags.Blocks.ORES) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.MUD);
    }

    /**
     * Whether a block lies over the ground without being it: a plant, snow, a vine, moss or lichen.
     */
    public static boolean cover(BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (!state.getFluidState().isEmpty() || state.hasBlockEntity()) {
            return false;
        }
        return NaturalGround.wild(state) || state.is(Blocks.SNOW) || state.is(Blocks.VINE) || state.is(Blocks.MOSS_CARPET)
                || state.is(Blocks.GLOW_LICHEN) || state.is(Blocks.HANGING_ROOTS) || state.is(Blocks.SPORE_BLOSSOM)
                || state.is(Blocks.AZALEA) || state.is(Blocks.FLOWERING_AZALEA) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(BlockTags.REPLACEABLE_BY_TREES) && !state.is(BlockTags.LEAVES);
    }

    /**
     * Whether a block is soil, dug by builders: earth with its grass, moss and mud, a path, sand, gravel, clay, snow and ice.
     */
    public static boolean soil(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(Blocks.DIRT_PATH) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL)
                || state.is(Blocks.CLAY) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW) || state.is(BlockTags.SNOW)
                || state.is(BlockTags.ICE);
    }

    /**
     * Whether a block is protected by its state alone: one that cannot be broken, one kept from clearing, a block of this mod.
     */
    public static boolean protectedState(BlockState state) {
        if (state.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) < 0
                || state.is(io.github.sekerator.kingdomworld.common.construction.TemplateBlocks.UNCLEARABLE)) {
            return true;
        }
        if (io.github.sekerator.kingdomworld.common.construction.Scaffolds.is(state)) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id != null && io.github.sekerator.kingdomworld.KingdomWorld.MOD_ID.equals(id.getNamespace());
    }

    /**
     * Whether a block is water as a road sees it: water, or ice over water.
     */
    static boolean water(BlockState state) {
        return state.getFluidState().is(FluidTags.WATER) || state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE);
    }

    /**
     * A column read from its blocks.
     *
     * @param states the block at a height
     * @param top    the highest block that is not air
     * @param minY   the bottom of the world
     */
    public static LandColumn scan(IntFunction<BlockState> states, int top, int minY) {
        int y = top;
        int logs = 0;
        boolean built = false;
        for (; y >= minY; y--) {
            BlockState state = states.apply(y);
            if (state.isAir()) {
                continue;
            }
            if (!state.getFluidState().isEmpty() && !natural(state) || water(state)) {
                break;
            }
            if (state.hasBlockEntity() || protectedState(state)) {
                built = true;
                break;
            }
            if (Trees.isLog(state) && !Trees.isWorkedWood(state)) {
                logs++;
                continue;
            }
            if (state.is(BlockTags.LEAVES) && natural(state) || cover(state) || state.is(Blocks.KELP) || state.is(Blocks.SEAGRASS)) {
                continue;
            }
            break;
        }
        if (y < minY) {
            return new LandColumn(minY - 1, LandColumn.NONE, 0, 0, LandColumn.VOID, LandColumn.NONE, minY, LandColumn.NONE,
                    LandColumn.NONE, false);
        }
        int waterTop = LandColumn.NONE;
        int flags = built ? LandColumn.BUILT : 0;
        BlockState state = states.apply(y);
        if (!built && state.getFluidState().is(FluidTags.LAVA)) {
            flags |= LandColumn.LAVA;
        } else if (!built && water(state)) {
            waterTop = y;
            while (y > minY) {
                BlockState below = states.apply(y);
                if (!water(below) && !below.isAir()) {
                    break;
                }
                y--;
            }
            state = states.apply(y);
        }
        int ground = y;
        if (!built && (flags & LandColumn.LAVA) == 0 && !natural(state)) {
            flags |= LandColumn.BUILT;
        }
        int soil = 0;
        while (soil < LandColumn.DEEP_SOIL && ground - soil >= minY && soil(states.apply(ground - soil))) {
            soil++;
        }
        int naturalTo = ground;
        int caveTop = LandColumn.NONE;
        int caveBottom = LandColumn.NONE;
        boolean liquid = false;
        boolean inCave = false;
        boolean caveDone = false;
        for (int at = ground - 1; at >= Math.max(minY, ground - DEPTH); at--) {
            BlockState below = states.apply(at);
            boolean hollow = below.isAir() || !below.getFluidState().isEmpty();
            if (hollow) {
                if (!caveDone) {
                    if (!inCave) {
                        caveTop = at;
                        inCave = true;
                    }
                    caveBottom = at;
                    liquid |= !below.getFluidState().isEmpty();
                }
                naturalTo = at;
                continue;
            }
            if (inCave) {
                caveDone = true;
                inCave = false;
            }
            if (!natural(below)) {
                break;
            }
            naturalTo = at;
        }
        return new LandColumn(ground, waterTop, soil, logs, flags, LandColumn.NONE, naturalTo, caveTop, caveBottom, liquid);
    }

    /**
     * A column of a loaded level as it is now; for the roads laid by operators and game tests.
     */
    public static LandColumn column(ServerLevel level, int x, int z) {
        if (!level.getWorldBorder().isWithinBounds(x, z)) {
            return new LandColumn(level.getMinBuildHeight(), LandColumn.NONE, 0, 0, LandColumn.BORDER, LandColumn.NONE,
                    level.getMinBuildHeight(), LandColumn.NONE, LandColumn.NONE, false);
        }
        int top = io.github.sekerator.kingdomworld.common.construction.Earthworks.surface(level, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, 0, z);
        return scan(y -> level.getBlockState(pos.setY(y)), top, level.getMinBuildHeight());
    }
}
