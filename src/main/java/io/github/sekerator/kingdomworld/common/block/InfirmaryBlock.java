package io.github.sekerator.kingdomworld.common.block;

import io.github.sekerator.kingdomworld.common.citizen.CitizenSites;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The infirmary: wounded citizens of the kingdom whose land it stands on come here and heal much faster. Each
 * infirmary treats a few citizens at a time ({@code infirmaryCapacity}).
 */
public class InfirmaryBlock extends Block {

    public InfirmaryBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (level instanceof ServerLevel server && !oldState.is(this)) {
            CitizenSites.onPlaced(server, pos, state);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (level instanceof ServerLevel server && !newState.is(this)) {
            CitizenSites.onRemoved(server, pos);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.kingdomworld.infirmary.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
