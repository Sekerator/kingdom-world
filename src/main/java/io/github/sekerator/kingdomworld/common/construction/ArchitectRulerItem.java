package io.github.sekerator.kingdomworld.common.construction;

import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The architect's ruler: a member with the right to manage construction marks two corners of a building that stands in the
 * world (use on a block) and saves it as a blueprint of the kingdom (use while sneaking): a screen asks for its name,
 * category and role, and the server reads the blocks itself. Sneaking with no corners marked forgets them.
 */
public class ArchitectRulerItem extends Item {
    private static final String FIRST = "first";
    private static final String SECOND = "second";
    private static final String DIMENSION = "dimension";

    public ArchitectRulerItem(Properties properties) {
        super(properties);
    }

    /**
     * The corners marked on a ruler: none, the first, or both.
     */
    public record Corners(@Nullable BlockPos first, @Nullable BlockPos second, String dimension) {
        public boolean complete() {
            return first != null && second != null;
        }

        /**
         * The size of the box between the corners, {x, y, z}, or zeros.
         */
        public int[] size() {
            if (!complete()) {
                return new int[3];
            }
            return new int[]{Math.abs(first.getX() - second.getX()) + 1, Math.abs(first.getY() - second.getY()) + 1,
                    Math.abs(first.getZ() - second.getZ()) + 1};
        }
    }

    public static Corners corners(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return new Corners(null, null, "");
        }
        return new Corners(read(tag, FIRST), read(tag, SECOND), tag.getString(DIMENSION));
    }

    @Nullable
    private static BlockPos read(CompoundTag tag, String key) {
        int[] pos = tag.getIntArray(key);
        return pos.length == 3 ? new BlockPos(pos[0], pos[1], pos[2]) : null;
    }

    public static void clear(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(FIRST);
            tag.remove(SECOND);
            tag.remove(DIMENSION);
        }
    }

    /**
     * Marks a corner: the first one, the second one, then the first one again.
     */
    public static Corners mark(ItemStack stack, BlockPos pos, String dimension) {
        Corners corners = corners(stack);
        CompoundTag tag = stack.getOrCreateTag();
        boolean again = corners.first() == null || corners.complete() || !dimension.equals(corners.dimension());
        if (again) {
            tag.putIntArray(FIRST, new int[]{pos.getX(), pos.getY(), pos.getZ()});
            tag.remove(SECOND);
            tag.putString(DIMENSION, dimension);
        } else {
            tag.putIntArray(SECOND, new int[]{pos.getX(), pos.getY(), pos.getZ()});
        }
        return corners(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            return save(context.getLevel(), player, context.getItemInHand()).getResult();
        }
        if (!context.getLevel().isClientSide) {
            Corners corners = mark(context.getItemInHand(), context.getClickedPos(), Dimensions.id(context.getLevel()));
            BlockPos pos = context.getClickedPos();
            String at = pos.getX() + " " + pos.getY() + " " + pos.getZ();
            if (corners.complete()) {
                int[] size = corners.size();
                player.displayClientMessage(Component.translatable("kingdomworld.ruler.second", at, size[0], size[1], size[2])
                        .withStyle(ChatFormatting.GREEN), true);
            } else {
                player.displayClientMessage(Component.translatable("kingdomworld.ruler.first", at).withStyle(ChatFormatting.YELLOW), true);
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        return save(level, player, stack);
    }

    /**
     * Sneaking with both corners marked opens the screen that saves the blueprint; without them the ruler forgets what it
     * had.
     */
    private static InteractionResultHolder<ItemStack> save(Level level, Player player, ItemStack stack) {
        Corners corners = corners(stack);
        if (!corners.complete()) {
            if (!level.isClientSide) {
                clear(stack);
                player.displayClientMessage(Component.translatable("kingdomworld.ruler.cleared"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> io.github.sekerator.kingdomworld.client.ClientHooks.openCapture(corners));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.kingdomworld.architect_ruler.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
