package io.github.sekerator.kingdomworld.common.item;

import io.github.sekerator.kingdomworld.client.ClientHooks;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The guide book "Kingdoms: a Guide": opens the guide with its tree of pages and its search on the client. Every player gets one
 * the first time they join (once, remembered in the data of the player that survives death), and {@code /kingdom guide} opens the
 * guide and gives the book again to a player who has none; the book is worth nothing to markets, so giving it away again makes
 * nothing from nothing.
 */
public class GuideBookItem extends Item {
    /** The mark in the persistent data of a player that the book was given on the first join. */
    static final String GIVEN = "KingdomWorldGuideGiven";

    public GuideBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientHooks::openGuide);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.kingdomworld.guide_book.tooltip").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Gives the book to a player who joins for the first time, once; the config may turn it off.
     *
     * @return whether the book was given now
     */
    public static boolean giveOnFirstJoin(ServerPlayer player) {
        CompoundTag kept = kept(player);
        if (kept.getBoolean(GIVEN) || !enabled()) {
            return false;
        }
        kept.putBoolean(GIVEN, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, kept);
        hand(player);
        if (player.connection != null) {
            player.sendSystemMessage(Component.translatable("kingdomworld.guide.given").withStyle(ChatFormatting.GOLD));
        }
        return true;
    }

    /**
     * Gives the book again on request, when the player carries none.
     *
     * @return whether the book was given
     */
    public static boolean giveAgain(ServerPlayer player) {
        if (player.getInventory().contains(new ItemStack(ModRegistries.GUIDE_BOOK.get()))) {
            return false;
        }
        hand(player);
        return true;
    }

    private static void hand(ServerPlayer player) {
        ItemStack book = new ItemStack(ModRegistries.GUIDE_BOOK.get());
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
    }

    private static CompoundTag kept(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static boolean enabled() {
        try {
            return KingdomWorldConfig.SERVER.giveGuideOnFirstJoin.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }
}
