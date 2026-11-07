package io.github.sekerator.kingdomworld.common.magic;

import io.github.sekerator.kingdomworld.core.magic.MagicDevice;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The crystal generator: gives the energy of the kingdom whose land it stands on to the machines next to it as Forge
 * Energy. Only a member with the right to run the mage tower may place it.
 */
public class CrystalGeneratorBlock extends MagicDeviceBlock implements EntityBlock {

    public CrystalGeneratorBlock(Properties properties) {
        super(MagicDevice.Kind.GENERATOR, properties);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrystalGeneratorBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != MagicRegistries.GENERATOR_ENTITY.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> ((CrystalGeneratorBlockEntity) entity).serverTick();
    }
}
