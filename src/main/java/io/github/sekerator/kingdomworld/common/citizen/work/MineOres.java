package io.github.sekerator.kingdomworld.common.citizen.work;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.tags.ITagManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The kinds of ore a miner can be asked for: the tags {@code forge:ores/<name>} that hold at least one block in
 * the running game, so the ores of other mods are kinds of their own without anything written for them. A kind
 * shows with the first block of its tag, and counts on the storehouses as what that block drops under an iron
 * pickaxe without enchantments.
 */
public final class MineOres {
    private static final String NAMESPACE = "forge";
    private static final String PREFIX = "ores/";
    /** Most kinds a post offers; they travel over the network. */
    public static final int MAX_KINDS = 128;

    private static final Map<String, Optional<Item>> DROPS = new HashMap<>();

    private MineOres() {
    }

    /**
     * The ids of the kinds of ore of the running game, sorted.
     */
    public static List<String> kinds() {
        ITagManager<Block> tags = ForgeRegistries.BLOCKS.tags();
        if (tags == null) {
            return List.of();
        }
        List<String> kinds = new ArrayList<>();
        tags.getTagNames().forEach(key -> {
            ResourceLocation id = key.location();
            if (id.getNamespace().equals(NAMESPACE) && id.getPath().startsWith(PREFIX) && id.getPath().length() > PREFIX.length()
                    && !tags.getTag(key).isEmpty()) {
                kinds.add(id.toString());
            }
        });
        Collections.sort(kinds);
        return kinds.size() > MAX_KINDS ? List.copyOf(kinds.subList(0, MAX_KINDS)) : kinds;
    }

    @Nullable
    public static TagKey<Block> tag(String id) {
        ITagManager<Block> tags = ForgeRegistries.BLOCKS.tags();
        ResourceLocation key = id == null || id.isEmpty() ? null : ResourceLocation.tryParse(id);
        return tags == null || key == null ? null : tags.createTagKey(key);
    }

    /**
     * Whether an id names a kind of ore of the running game.
     */
    public static boolean exists(String id) {
        return kinds().contains(id);
    }

    /**
     * The block a kind of ore shows with: the first of its tag. Before the tags of a world are known the vanilla
     * ore of that name stands in.
     */
    @Nullable
    public static Block block(String id) {
        ITagManager<Block> tags = ForgeRegistries.BLOCKS.tags();
        TagKey<Block> key = tag(id);
        if (tags != null && key != null) {
            Optional<Block> first = tags.getTag(key).stream().findFirst();
            if (first.isPresent()) {
                return first.get();
            }
        }
        ResourceLocation location = id == null ? null : ResourceLocation.tryParse(id);
        if (location != null && location.getPath().startsWith(PREFIX)) {
            Block vanilla = ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryBuild("minecraft",
                    location.getPath().substring(PREFIX.length()) + "_ore"));
            if (vanilla != null && vanilla != Blocks.AIR) {
                return vanilla;
            }
        }
        return null;
    }

    /**
     * The name of a kind of ore in the language of the game: the name of its block.
     */
    public static Component name(String id) {
        Block block = block(id);
        return block == null ? Component.literal(id) : block.getName();
    }

    public static ItemStack icon(String id) {
        Block block = block(id);
        return block == null ? ItemStack.EMPTY : new ItemStack(block);
    }

    /**
     * The item a kind of ore counts as on the storehouses: what its block drops under an iron pickaxe without
     * enchantments (raw iron, a diamond, coal), or the block itself if it drops nothing.
     */
    @Nullable
    public static Item drop(ServerLevel level, String id) {
        Optional<Item> known = DROPS.get(id);
        if (known != null) {
            return known.orElse(null);
        }
        Block block = block(id);
        Item found = null;
        if (block != null) {
            BlockState state = block.defaultBlockState();
            ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
            for (int roll = 0; roll < 8 && found == null; roll++) {
                for (ItemStack stack : Block.getDrops(state, level, BlockPos.ZERO, null, null, pickaxe)) {
                    if (!stack.isEmpty()) {
                        found = stack.getItem();
                        break;
                    }
                }
            }
            if (found == null && block.asItem() != Items.AIR) {
                found = block.asItem();
            }
        }
        DROPS.put(id, Optional.ofNullable(found));
        return found;
    }

    public static void reset() {
        DROPS.clear();
    }
}
