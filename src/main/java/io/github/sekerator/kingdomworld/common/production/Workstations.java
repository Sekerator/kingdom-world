package io.github.sekerator.kingdomworld.common.production;

import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.production.RecipeKind;
import io.github.sekerator.kingdomworld.core.production.Stations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.FletchingTableBlock;
import net.minecraft.world.level.block.SmithingTableBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Workstations of the workshops, recognised by the classes of vanilla blocks so that those of other mods count
 * too: crafting tables (and the tables of mods built on them), stonecutters, smithing tables, lit campfires, and
 * every furnace, blast furnace and smoker. Furnaces of mods that are not built on the vanilla furnace count when
 * they offer item handlers the vanilla way: input from above, fuel from the side, the result from below.
 */
public final class Workstations {

    /**
     * The three item handlers of a furnace, as hoppers see them.
     */
    public record Furnace(IItemHandler input, IItemHandler fuel, IItemHandler output) {
    }

    private Workstations() {
    }

    /**
     * The kind of workstation at a block (see {@link Stations}), or {@code null}.
     */
    @Nullable
    public static String kindOf(ServerLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof SmithingTableBlock) {
            return Stations.SMITHING_TABLE;
        }
        if (block instanceof FletchingTableBlock) {
            return null;
        }
        if (block instanceof CraftingTableBlock) {
            return Stations.CRAFTING_TABLE;
        }
        if (block instanceof StonecutterBlock) {
            return Stations.STONECUTTER;
        }
        if (block instanceof CampfireBlock) {
            return state.hasProperty(CampfireBlock.LIT) && state.getValue(CampfireBlock.LIT) ? Stations.CAMPFIRE : null;
        }
        if (!state.hasBlockEntity() || state.is(ModRegistries.STOREHOUSE.get()) || state.is(ModRegistries.WORK_POST.get())) {
            return null;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof BlastFurnaceBlockEntity) {
            return Stations.BLAST_FURNACE;
        }
        if (entity instanceof SmokerBlockEntity) {
            return Stations.SMOKER;
        }
        if (entity instanceof AbstractFurnaceBlockEntity) {
            return Stations.FURNACE;
        }
        return entity != null && furnace(entity) != null ? Stations.FURNACE : null;
    }

    /**
     * Whether a block may be a workstation at all; a quick test for scanning.
     */
    public static boolean mayBeStation(BlockState state) {
        Block block = state.getBlock();
        return block instanceof CraftingTableBlock || block instanceof StonecutterBlock || block instanceof CampfireBlock
                || state.hasBlockEntity();
    }

    /**
     * The handlers of a furnace: a block with one slot from above, one slot for fuel from the side (which takes
     * coal and refuses stone) and one or two from below; {@code null} for anything else (chests, hoppers, machines).
     */
    @Nullable
    public static Furnace furnace(BlockEntity entity) {
        IItemHandler input = entity.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
        IItemHandler output = entity.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElse(null);
        IItemHandler fuel = null;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            fuel = entity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
            if (fuel != null) {
                break;
            }
        }
        if (input == null || output == null || fuel == null || input.getSlots() != 1 || fuel.getSlots() != 1
                || output.getSlots() < 1 || output.getSlots() > 2) {
            return null;
        }
        if (!fuel.isItemValid(0, new ItemStack(Items.COAL)) || fuel.isItemValid(0, new ItemStack(Items.COBBLESTONE))) {
            return null;
        }
        return new Furnace(input, fuel, output);
    }

    /**
     * Whether a furnace burns now.
     */
    public static boolean isLit(BlockState state) {
        return state.hasProperty(AbstractFurnaceBlock.LIT) && state.getValue(AbstractFurnaceBlock.LIT);
    }

    /**
     * Whether a kind of workstation makes the kind of recipe.
     */
    public static boolean serves(String station, RecipeKind kind) {
        return kind.station().equals(station);
    }
}
