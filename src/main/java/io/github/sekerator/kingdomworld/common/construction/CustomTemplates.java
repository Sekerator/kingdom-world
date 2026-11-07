package io.github.sekerator.kingdomworld.common.construction;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.core.construction.BlockCost;
import io.github.sekerator.kingdomworld.core.construction.ConstructionBook;
import io.github.sekerator.kingdomworld.core.construction.CustomBlueprint;
import io.github.sekerator.kingdomworld.core.construction.TemplateCost;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The blueprints kingdoms saved, as building templates: made from the kingdom data of the running server whenever the
 * blueprints change, with the same checks as the templates of data packs (blocks of mods that are gone, blocks no item
 * makes). Server thread only.
 */
public final class CustomTemplates {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "kingdomworld.template.problem.";

    private static Map<String, BuildingTemplate> templates = Map.of();
    private static int revision = -1;
    private static MinecraftServer madeFor;

    private CustomTemplates() {
    }

    public static void reset() {
        templates = Map.of();
        revision = -1;
        madeFor = null;
    }

    /**
     * The template of a saved blueprint by its id, or empty.
     */
    public static Optional<BuildingTemplate> get(String id) {
        if (CustomBlueprint.number(id).isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(current().get(id));
    }

    /**
     * The templates of the blueprints a kingdom saved, oldest first.
     */
    public static List<BuildingTemplate> of(UUID kingdomId) {
        List<BuildingTemplate> result = new ArrayList<>();
        for (BuildingTemplate template : current().values()) {
            if (kingdomId.equals(template.owner())) {
                result.add(template);
            }
        }
        return result;
    }

    private static Map<String, BuildingTemplate> current() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return Map.of();
        }
        ConstructionBook book = KingdomWorldApi.state(server).construction();
        if (server != madeFor || book.blueprintRevision() != revision) {
            Map<String, BuildingTemplate> next = new java.util.LinkedHashMap<>();
            for (CustomBlueprint blueprint : book.blueprints()) {
                try {
                    next.put(blueprint.templateId(), template(blueprint));
                } catch (RuntimeException e) {
                    LOGGER.warn("Saved blueprint {} cannot be used: {}", blueprint.id(), e.toString());
                }
            }
            templates = next;
            revision = book.blueprintRevision();
            madeFor = server;
        }
        return templates;
    }

    /**
     * A template from a saved blueprint.
     */
    public static BuildingTemplate template(CustomBlueprint blueprint) {
        Set<String> missing = new LinkedHashSet<>();
        List<BlockState> palette = new ArrayList<>(blueprint.palette().size());
        for (String text : blueprint.palette()) {
            palette.add(parse(text, missing));
        }
        List<BuildingTemplate.Block> blocks = new ArrayList<>();
        for (int y = 0; y < blueprint.sizeY(); y++) {
            for (int z = 0; z < blueprint.sizeZ(); z++) {
                for (int x = 0; x < blueprint.sizeX(); x++) {
                    int index = blueprint.at(x, y, z);
                    if (index >= 0) {
                        blocks.add(new BuildingTemplate.Block(new BlockPos(x, y, z), palette.get(index)));
                    }
                }
            }
        }
        List<BlockCost> costs = new ArrayList<>(blocks.size());
        for (BuildingTemplate.Block block : blocks) {
            costs.add(TemplateBlocks.cost(block.state()));
        }
        TemplateCost cost = TemplateCost.of(costs);
        Component problem = null;
        if (!missing.isEmpty()) {
            problem = Component.translatable(KEY + "missing_block", String.join(", ", missing));
        } else if (!cost.buildable()) {
            problem = Component.translatable(KEY + "unobtainable", String.join(", ", cost.unobtainable()));
        } else if (cost.blocks() == 0) {
            problem = Component.translatable(KEY + "empty");
        }
        ResourceLocation id = ResourceLocation.tryParse(blueprint.templateId());
        if (id == null) {
            throw new IllegalArgumentException("Bad id " + blueprint.templateId());
        }
        ItemStack icon = new ItemStack(Items.PAPER);
        return new BuildingTemplate(id, "", blueprint.name(), BuildingTemplate.Category.byId(blueprint.category()),
                blueprint.role().isEmpty() ? "custom" : blueprint.role(), blueprint.role(), Map.of(), 1, "", blueprint.kingdomId(), icon, blueprint.sizeX(), blueprint.sizeY(), blueprint.sizeZ(),
                new BlockPos(blueprint.anchorX(), blueprint.anchorY(), blueprint.anchorZ()), blueprint.entrance(), blocks, costs, cost,
                fingerprint(blueprint), problem);
    }

    private static BlockState parse(String text, Set<String> missing) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), text, false).blockState();
        } catch (Exception e) {
            missing.add(text.contains("[") ? text.substring(0, text.indexOf('[')) : text);
            return Blocks.AIR.defaultBlockState();
        }
    }

    /**
     * A hash of the blocks of a blueprint; its name does not count, so a renamed blueprint builds on.
     */
    static long fingerprint(CustomBlueprint blueprint) {
        long hash = 0xcbf29ce484222325L;
        StringBuilder text = new StringBuilder();
        text.append(blueprint.sizeX()).append(',').append(blueprint.sizeY()).append(',').append(blueprint.sizeZ()).append(';');
        text.append(blueprint.anchorX()).append(',').append(blueprint.anchorY()).append(',').append(blueprint.anchorZ()).append(';');
        for (String state : blueprint.palette()) {
            text.append(state).append(';');
        }
        for (byte b : text.toString().getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & 0xFF;
            hash *= 0x100000001b3L;
        }
        for (int block : blueprint.blocks()) {
            hash ^= block;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    /**
     * How many blocks of each palette entry, to count beds and containers; for tests.
     */
    static Map<String, Integer> counts(CustomBlueprint blueprint) {
        Map<String, Integer> result = new HashMap<>();
        for (int block : blueprint.blocks()) {
            if (block >= 0) {
                result.merge(blueprint.palette().get(block), 1, Integer::sum);
            }
        }
        return result;
    }
}
