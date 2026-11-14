package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The container of a work post: any block next to the post that offers an item handler (a chest, a barrel, a crate
 * or drawer of another mod, a storehouse). Workers put what they gather there; carriers take it on to the
 * storehouses.
 */
public final class PostContainers {

    /**
     * A container next to a post.
     */
    public record Container(BlockPos pos, IItemHandler handler) {
    }

    private PostContainers() {
    }

    /**
     * The container next to the post, if the post's surroundings are loaded and there is one.
     */
    @Nullable
    public static Container find(ServerLevel level, BlockPos post) {
        if (!level.isLoaded(post)) {
            return null;
        }
        for (Direction direction : Direction.values()) {
            BlockPos pos = post.relative(direction);
            if (!level.isLoaded(pos) || level.getBlockState(pos).is(ModRegistries.WORK_POST.get())) {
                continue;
            }
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity == null) {
                continue;
            }
            IItemHandler handler = entity.getCapability(ForgeCapabilities.ITEM_HANDLER, direction.getOpposite()).orElse(null);
            if (handler != null && handler.getSlots() > 0) {
                return new Container(pos.immutable(), handler);
            }
        }
        return null;
    }

    @Nullable
    public static Container find(ServerLevel level, WorldPos post) {
        return find(level, new BlockPos(post.x(), post.y(), post.z()));
    }

    /**
     * Puts a stack into a container.
     *
     * @return what did not fit
     */
    public static ItemStack insert(IItemHandler handler, ItemStack stack) {
        return ItemHandlerHelper.insertItemStacked(handler, stack, false);
    }

    /**
     * What of a stack a container would not take, without putting anything in.
     */
    public static ItemStack insertSimulated(IItemHandler handler, ItemStack stack) {
        return ItemHandlerHelper.insertItemStacked(handler, stack, true);
    }

    public static int items(IItemHandler handler) {
        int count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            count += handler.getStackInSlot(slot).getCount();
        }
        return count;
    }

    public static int freeSlots(IItemHandler handler) {
        int free = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (handler.getStackInSlot(slot).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    /**
     * What a container holds, by item id.
     */
    public static Map<String, Integer> contents(IItemHandler handler) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                result.merge(itemId(stack), stack.getCount(), Integer::sum);
            }
        }
        return result;
    }

    public static String itemId(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }
}
