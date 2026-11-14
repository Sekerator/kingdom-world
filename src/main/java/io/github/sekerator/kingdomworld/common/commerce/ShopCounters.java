package io.github.sekerator.kingdomworld.common.commerce;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The counters of the kingdoms: put up by players, found when their chunks are looked at, and gone with their blocks.
 */
public final class ShopCounters {
    private static final String KEY = "kingdomworld.shop.counter.";

    private ShopCounters() {
    }

    /**
     * Why a player may not put up a counter at a place, or empty when it may.
     */
    static Optional<Component> refusal(MinecraftServer server, ServerPlayer player, String dimension, BlockPos pos) {
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdomOf(server, player.getUUID());
        if (kingdom.isEmpty()) {
            return Optional.of(Component.translatable(KEY + "no_kingdom"));
        }
        if (!CityOf.active(server, kingdom.get().id())) {
            return Optional.of(Component.translatable(KEY + "off"));
        }
        if (!KingdomWorldApi.state(server).membership().hasPermission(player.getUUID(), kingdom.get().id(), Permission.MANAGE_CONSTRUCTION)) {
            return Optional.of(Component.translatable(KEY + "no_right"));
        }
        if (BusinessPlaces.counterCity(server, kingdom.get().id(), dimension, pos.getX(), pos.getZ()).isEmpty()) {
            return Optional.of(Component.translatable(KEY + "outside"));
        }
        return Optional.empty();
    }

    /**
     * A player put up a counter: kept where the player may, else taken down and given back.
     */
    static void placed(ServerLevel level, ServerPlayer player, BlockPos pos) {
        MinecraftServer server = level.getServer();
        String dimension = Dimensions.id(level);
        Optional<Component> refused = KingdomWorldApi.isReadOnly(server) ? Optional.of(Component.translatable(KEY + "read_only"))
                : refusal(server, player, dimension, pos);
        if (refused.isPresent()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            if (!player.isCreative()) {
                ItemStack back = new ItemStack(ModRegistries.SHOP_COUNTER_ITEM.get());
                if (!player.getInventory().add(back)) {
                    player.drop(back, false);
                }
            }
            player.sendSystemMessage(refused.get().copy().withStyle(ChatFormatting.RED));
            return;
        }
        UUID kingdom = KingdomWorldApi.kingdomOf(server, player.getUUID()).orElseThrow().id();
        CommerceSavedData.get(server).putCounter(new CommerceSavedData.Counter(kingdom, dimension, pos.getX(), pos.getY(), pos.getZ()));
        BusinessPlaces.changed(kingdom);
        player.sendSystemMessage(Component.translatable(KEY + "placed").withStyle(ChatFormatting.GREEN));
    }

    /**
     * A counter is gone: its shop closes.
     */
    static void removed(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        if (KingdomWorldApi.isReadOnly(server)) {
            return;
        }
        String key = CommerceSavedData.counterKey(Dimensions.id(level), pos.getX(), pos.getY(), pos.getZ());
        CommerceSavedData.get(server).removeCounter(key).ifPresent(counter -> {
            BusinessPlaces.changed(counter.kingdom());
            Shops.placeGone(server, "counter/" + key);
        });
    }

    /**
     * A chunk was looked at: counters of the kingdom that owns it are noted, and noted counters whose block is gone or whose land
     * changed hands are forgotten, closing their shops.
     */
    public static void scanned(ServerLevel level, long chunk, UUID owner, List<BlockPos> found) {
        MinecraftServer server = level.getServer();
        if (KingdomWorldApi.isReadOnly(server)) {
            return;
        }
        CommerceSavedData data = CommerceSavedData.get(server);
        String dimension = Dimensions.id(level);
        List<CommerceSavedData.Counter> gone = new ArrayList<>();
        for (CommerceSavedData.Counter counter : data.counters().values()) {
            if (!counter.dimension().equals(dimension) || ChunkKey.of(counter.x() >> 4, counter.z() >> 4) != chunk) {
                continue;
            }
            BlockPos pos = new BlockPos(counter.x(), counter.y(), counter.z());
            if (!level.getBlockState(pos).is(ModRegistries.SHOP_COUNTER.get()) || !counter.kingdom().equals(owner)) {
                gone.add(counter);
            }
        }
        for (CommerceSavedData.Counter counter : gone) {
            data.removeCounter(counter.key());
            BusinessPlaces.changed(counter.kingdom());
            Shops.placeGone(server, "counter/" + counter.key());
        }
        if (owner == null || !CityOf.active(server, owner)) {
            return;
        }
        for (BlockPos pos : found) {
            String key = CommerceSavedData.counterKey(dimension, pos.getX(), pos.getY(), pos.getZ());
            if (!data.counters().containsKey(key)) {
                data.putCounter(new CommerceSavedData.Counter(owner, dimension, pos.getX(), pos.getY(), pos.getZ()));
                BusinessPlaces.changed(owner);
            }
        }
    }
}
