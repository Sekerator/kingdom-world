package io.github.sekerator.kingdomworld.common.construction;

import io.github.sekerator.kingdomworld.core.construction.BlockCost;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.construction.MaterialTable;
import io.github.sekerator.kingdomworld.core.construction.TemplateCost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Templates built of other wood and stone: the table of which block stands for which comes from all blocks of the game
 * (see {@link MaterialTable}), so the woods and stones of mods work too; a block the chosen material lacks stays as the
 * template has it, and every block keeps the properties it shares with the one it replaces (facing, half, shape). Used by
 * the server for plans and costs and by the client for the outline of a blueprint. Made again when tags change.
 */
public final class TemplateMaterials {
    private static volatile MaterialTable table;
    private static final Map<String, BuildingTemplate> CACHE = new ConcurrentHashMap<>();
    /** Most templates of other materials kept. */
    private static final int CACHE_SIZE = 256;

    private TemplateMaterials() {
    }

    public static MaterialTable table() {
        MaterialTable current = table;
        if (current == null) {
            List<String> ids = new ArrayList<>();
            for (ResourceLocation id : ForgeRegistries.BLOCKS.getKeys()) {
                ids.add(id.toString());
            }
            current = MaterialTable.of(ids, TemplateMaterials::stoneBase);
            table = current;
        }
        return current;
    }

    /**
     * Whether a block may be the main block of a stone: mined with a pickaxe and not metal, glass or ice.
     */
    private static boolean stoneBase(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        Block block = key == null ? null : ForgeRegistries.BLOCKS.getValue(key);
        if (block == null) {
            return false;
        }
        String path = key.getPath();
        if (path.contains("copper") || path.contains("iron") || path.contains("gold") || path.contains("glass") || path.contains("ice")
                || path.contains("purpur") || path.contains("quartz") || path.contains("prismarine")) {
            return false;
        }
        try {
            return block.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Tags or blocks changed: the table and the templates of other materials are made again on next use.
     */
    public static void reset() {
        table = null;
        CACHE.clear();
    }

    /**
     * The state that stands for a state of a template when it is built of other materials.
     */
    public static BlockState substitute(BlockState state, Materials materials) {
        if (materials.none()) {
            return state;
        }
        String id = TemplateBlocks.blockId(state);
        String target = table().substitute(id, materials);
        if (target.equals(id)) {
            return state;
        }
        ResourceLocation key = ResourceLocation.tryParse(target);
        Block block = key == null ? null : ForgeRegistries.BLOCKS.getValue(key);
        if (block == null) {
            return state;
        }
        return copyProperties(state, block.defaultBlockState());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static BlockState copyProperties(BlockState from, BlockState to) {
        BlockState result = to;
        for (Property<?> property : from.getProperties()) {
            Property target = result.getBlock().getStateDefinition().getProperty(property.getName());
            if (target == null) {
                continue;
            }
            String value = ((Property) property).getName(from.getValue(property));
            Optional<?> parsed = target.getValue(value);
            if (parsed.isPresent()) {
                result = result.setValue(target, (Comparable) parsed.get());
            }
        }
        return result;
    }

    /**
     * A template built of other materials: the same id, fingerprint and description, other blocks and costs.
     */
    public static BuildingTemplate apply(BuildingTemplate template, Materials materials) {
        if (materials.none() || (materials.stone().isEmpty() && Materials.AUTO.equals(materials.wood()))) {
            return template;
        }
        String key = template.id() + "#" + template.fingerprint() + "#" + materials.key();
        BuildingTemplate cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        List<BuildingTemplate.Block> blocks = new ArrayList<>(template.blocks().size());
        List<BlockCost> costs = new ArrayList<>(template.blocks().size());
        for (BuildingTemplate.Block block : template.blocks()) {
            BlockState state = substitute(block.state(), materials);
            blocks.add(new BuildingTemplate.Block(block.pos(), state));
            costs.add(TemplateBlocks.cost(state));
        }
        BuildingTemplate result = template.withBlocks(blocks, costs, TemplateCost.of(costs));
        if (CACHE.size() >= CACHE_SIZE) {
            CACHE.clear();
        }
        CACHE.put(key, result);
        return result;
    }

    /**
     * The wood that grows where a building is laid out, or empty for the template's own.
     */
    public static String woodAt(Level level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        String id = biome.unwrapKey().map(found -> found.location().toString()).orElse("");
        Set<String> tags = new HashSet<>();
        biome.tags().forEach(tag -> tags.add(tag.location().toString()));
        return table().woodForBiome(id, tags);
    }

    /**
     * The materials a construction is built of: the wood of the place for {@link Materials#AUTO}, and only woods and stones
     * the game knows.
     */
    public static Materials resolve(Materials materials, Level level, BlockPos pos) {
        String wood = Materials.AUTO.equals(materials.wood()) ? woodAt(level, pos) : materials.wood();
        if (!wood.isEmpty() && !table().hasWood(wood)) {
            wood = "";
        }
        String stone = materials.stone().isEmpty() || table().hasStone(materials.stone()) ? materials.stone() : "";
        return new Materials(wood, stone);
    }

    /**
     * Whether a choice of a player names materials the game knows: empty, the wood of the place, a known wood or stone.
     */
    public static boolean known(Materials materials) {
        boolean wood = materials.wood().isEmpty() || Materials.AUTO.equals(materials.wood()) || table().hasWood(materials.wood());
        boolean stone = materials.stone().isEmpty() || table().hasStone(materials.stone());
        return wood && stone;
    }

    /**
     * What stands for a wood in screens: its planks.
     */
    public static ItemStack woodIcon(String species) {
        Block planks = ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse(species + "_planks"));
        return planks == null ? ItemStack.EMPTY : new ItemStack(planks);
    }

    /**
     * What stands for a stone in screens: its main block.
     */
    public static ItemStack stoneIcon(String stone) {
        ResourceLocation key = ResourceLocation.tryParse(stone);
        Block block = key == null ? null : ForgeRegistries.BLOCKS.getValue(key);
        return block == null ? ItemStack.EMPTY : new ItemStack(block);
    }

    /**
     * The name of a wood in the player's language: the name of its planks.
     */
    public static Component woodName(String species) {
        ItemStack icon = woodIcon(species);
        return icon.isEmpty() ? Component.literal(species) : icon.getHoverName();
    }

    public static Component stoneName(String stone) {
        ItemStack icon = stoneIcon(stone);
        return icon.isEmpty() ? Component.literal(stone) : icon.getHoverName();
    }
}
