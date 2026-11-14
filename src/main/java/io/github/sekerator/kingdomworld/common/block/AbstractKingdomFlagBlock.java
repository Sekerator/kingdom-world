package io.github.sekerator.kingdomworld.common.block;

import io.github.sekerator.kingdomworld.common.flag.FlagWork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The flag of a kingdom in the world: a banner that always shows the flag its kingdom flies now, so that a new flag drawn on
 * the throne shows on every building at once and no block changes. It has no item, drops nothing and is drawn by its block
 * entity, as a banner is.
 *
 * <p>It does not extend the banner of the game: a banner registers itself as the banner of its dye.
 */
public abstract class AbstractKingdomFlagBlock extends BaseEntityBlock {

    protected AbstractKingdomFlagBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isPossibleToRespawnInThis(BlockState state) {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KingdomFlagBlockEntity(pos, state);
    }

    /**
     * No item: a flag is no banner to pick, and weaving a banner of a flag is a matter of its own.
     */
    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof KingdomFlagBlockEntity flag) {
            FlagWork.removed(serverLevel, pos, flag);
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
