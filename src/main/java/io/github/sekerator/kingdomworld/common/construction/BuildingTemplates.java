package io.github.sekerator.kingdomworld.common.construction;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.core.construction.BlockCost;
import io.github.sekerator.kingdomworld.core.construction.ConstructionRules;
import io.github.sekerator.kingdomworld.core.construction.Facing;
import io.github.sekerator.kingdomworld.core.construction.TemplateCost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * The building templates of the running server, read from {@code data/<namespace>/kingdomworld_buildings/} of every data
 * pack: a JSON description and a structure file (the format of the structure block) with the same name, or the one the
 * description names. Packs and mod packs add their own buildings this way. Read again on {@code /reload}.
 *
 * <p>A template that needs a mod that is not installed, holds a block no item makes, cannot be read or is larger than the
 * server allows is kept, but cannot be built; the reason shows in the catalog and in the log. Nothing here throws.
 */
public final class BuildingTemplates {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String FOLDER = "kingdomworld_buildings";
    private static final String KEY = "kingdomworld.template.problem.";

    /** The raw templates, before their costs are worked out. */
    private static volatile Map<ResourceLocation, Raw> raw = Map.of();
    /** The finished templates; worked out on first use after tags are known, since costs depend on block tags. */
    private static volatile Map<ResourceLocation, BuildingTemplate> templates;
    /** Counts reloads, so that clients know when their copies are old. */
    private static volatile int revision;

    /**
     * A template as read: its description, its blocks and why it cannot be built if that is known already.
     */
    record Raw(ResourceLocation id, JsonObject json, List<BuildingTemplate.Block> blocks, int sizeX, int sizeY, int sizeZ,
               @Nullable Component problem) {
    }

    private BuildingTemplates() {
    }

    // ---------------------------------------------------------------- lookups

    public static Collection<BuildingTemplate> all() {
        return finished().values();
    }

    /**
     * Templates sorted by category, then id.
     */
    public static List<BuildingTemplate> sorted() {
        List<BuildingTemplate> list = new ArrayList<>(all());
        list.sort(Comparator.comparing((BuildingTemplate t) -> t.category().ordinal()).thenComparing(t -> t.id().toString()));
        return list;
    }

    public static Optional<BuildingTemplate> get(ResourceLocation id) {
        BuildingTemplate found = finished().get(id);
        return found != null ? Optional.of(found) : CustomTemplates.get(id.toString());
    }

    public static Optional<BuildingTemplate> get(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? Optional.empty() : get(key);
    }

    public static int revision() {
        return revision;
    }

    /**
     * The size up to which the mod's own templates can always be built, whatever the limits of the server say: the limits
     * are there for the templates of data packs and the blueprints of players, and a castle of the mod must not vanish from
     * a capital because a server kept the limits of an older version.
     */
    public static final int BUILT_IN_SIZE = 48;

    /**
     * Why a template cannot be built under the rules of the server, or {@code null} if it can.
     */
    @Nullable
    public static Component problem(BuildingTemplate template, ConstructionRules rules) {
        if (template.problem() != null) {
            return template.problem();
        }
        boolean builtIn = template.id().getNamespace().equals(KingdomWorld.MOD_ID) && !template.id().getPath().startsWith("test_")
                && template.sizeX() <= BUILT_IN_SIZE && template.sizeY() <= BUILT_IN_SIZE && template.sizeZ() <= BUILT_IN_SIZE;
        if (!builtIn && !rules.fits(template.sizeX(), template.sizeY(), template.sizeZ())) {
            return Component.translatable(KEY + "too_big", template.sizeX(), template.sizeY(), template.sizeZ(), rules.maxWidth(),
                    rules.maxHeight(), rules.maxLength());
        }
        return null;
    }

    private static Map<ResourceLocation, BuildingTemplate> finished() {
        Map<ResourceLocation, BuildingTemplate> current = templates;
        if (current == null) {
            synchronized (BuildingTemplates.class) {
                current = templates;
                if (current == null) {
                    current = finish(raw);
                    templates = current;
                }
            }
        }
        return current;
    }

    /**
     * Block tags changed: costs and what cannot be built are worked out again on next use.
     */
    public static void tagsChanged() {
        templates = null;
    }

    /**
     * Replaces the templates, for game tests; {@code null} reloads nothing and keeps them.
     */
    static void replaceForTest(Map<ResourceLocation, Raw> next) {
        raw = Map.copyOf(next);
        templates = null;
        revision++;
    }

    static Map<ResourceLocation, Raw> rawTemplates() {
        return raw;
    }

    /**
     * Reads a template from its description and its structure tag and adds it to the templates, for game tests.
     */
    public static BuildingTemplate addForTest(ResourceLocation id, JsonObject json, CompoundTag structure) {
        Map<ResourceLocation, Raw> next = new LinkedHashMap<>(raw);
        next.put(id, parse(id, json, structure));
        replaceForTest(next);
        return get(id).orElseThrow();
    }

    /**
     * Removes a template added by a game test.
     */
    public static void removeForTest(ResourceLocation id) {
        Map<ResourceLocation, Raw> next = new LinkedHashMap<>(raw);
        if (next.remove(id) != null) {
            replaceForTest(next);
        }
    }

    // ---------------------------------------------------------------- the reload listener

    /**
     * Reads all templates of the data packs; added on every reload of the server's data.
     */
    public static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, Raw>> {
        @Override
        protected Map<ResourceLocation, Raw> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, Raw> result = new TreeMap<>();
            for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources(FOLDER, id -> id.getPath().endsWith(".json")).entrySet()) {
                ResourceLocation file = entry.getKey();
                String path = file.getPath().substring(FOLDER.length() + 1, file.getPath().length() - ".json".length());
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path);
                result.put(id, read(manager, id, entry.getValue()));
            }
            return result;
        }

        @Override
        protected void apply(Map<ResourceLocation, Raw> prepared, ResourceManager manager, ProfilerFiller profiler) {
            raw = Map.copyOf(prepared);
            templates = null;
            revision++;
            long broken = prepared.values().stream().filter(template -> template.problem() != null).count();
            LOGGER.info("KingdomWorld loaded {} building template(s), {} of them cannot be built", prepared.size(), broken);
        }
    }

    private static Raw read(ResourceManager manager, ResourceLocation id, Resource description) {
        JsonObject json;
        try (Reader reader = description.openAsReader()) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Building template {}: the description cannot be read: {}", id, e.toString());
            return broken(id, new JsonObject(), Component.translatable(KEY + "unreadable", id.toString()));
        }
        ResourceLocation structure = ResourceLocation.tryParse(string(json, "structure", id.toString()));
        if (structure == null) {
            return broken(id, json, Component.translatable(KEY + "unreadable", id.toString()));
        }
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(structure.getNamespace(), FOLDER + "/" + structure.getPath() + ".nbt");
        Optional<Resource> resource = manager.getResource(file);
        if (resource.isEmpty()) {
            LOGGER.warn("Building template {}: no structure file {}", id, file);
            return broken(id, json, Component.translatable(KEY + "no_structure", file.toString()));
        }
        try (InputStream in = resource.get().open()) {
            return parse(id, json, NbtIo.readCompressed(in));
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Building template {}: the structure file cannot be read: {}", id, e.toString());
            return broken(id, json, Component.translatable(KEY + "unreadable", id.toString()));
        }
    }

    private static Raw broken(ResourceLocation id, JsonObject json, Component problem) {
        return new Raw(id, json, List.of(), 1, 1, 1, problem);
    }

    /**
     * Reads a template from its description and the tag of its structure file. Blocks of mods that are not installed make
     * it unbuildable; the data of block entities is never taken over.
     */
    static Raw parse(ResourceLocation id, JsonObject json, CompoundTag structure) {
        CompoundTag tag = DataFixTypes.STRUCTURE.updateToCurrentVersion(DataFixers.getDataFixer(), structure,
                NbtUtils.getDataVersion(structure, 500));
        ListTag sizeTag = tag.getList("size", Tag.TAG_INT);
        if (sizeTag.size() != 3) {
            return broken(id, json, Component.translatable(KEY + "unreadable", id.toString()));
        }
        int sizeX = sizeTag.getInt(0);
        int sizeY = sizeTag.getInt(1);
        int sizeZ = sizeTag.getInt(2);
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1 || sizeX > 1_024 || sizeY > 1_024 || sizeZ > 1_024) {
            return broken(id, json, Component.translatable(KEY + "unreadable", id.toString()));
        }
        ListTag palette = tag.contains("palettes", Tag.TAG_LIST) ? tag.getList("palettes", Tag.TAG_LIST).getList(0)
                : tag.getList("palette", Tag.TAG_COMPOUND);
        Set<String> missingMods = new LinkedHashSet<>();
        Set<String> missingBlocks = new LinkedHashSet<>();
        List<BlockState> states = new ArrayList<>(palette.size());
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag entry = palette.getCompound(i);
            ResourceLocation name = ResourceLocation.tryParse(entry.getString("Name"));
            if (name == null || !ForgeRegistries.BLOCKS.containsKey(name)) {
                String namespace = name == null ? "?" : name.getNamespace();
                if (name != null && !net.minecraftforge.fml.ModList.get().isLoaded(namespace) && !"minecraft".equals(namespace)) {
                    missingMods.add(namespace);
                } else {
                    missingBlocks.add(name == null ? entry.getString("Name") : name.toString());
                }
                states.add(Blocks.AIR.defaultBlockState());
                continue;
            }
            states.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), entry));
        }
        if (!missingMods.isEmpty()) {
            LOGGER.warn("Building template {} needs mods that are not installed: {}", id, missingMods);
            return broken(id, json, Component.translatable(KEY + "missing_mod", String.join(", ", missingMods)));
        }
        if (!missingBlocks.isEmpty()) {
            LOGGER.warn("Building template {} has unknown blocks: {}", id, missingBlocks);
            return broken(id, json, Component.translatable(KEY + "missing_block", String.join(", ", missingBlocks)));
        }
        ListTag blockList = tag.getList("blocks", Tag.TAG_COMPOUND);
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (int i = 0; i < blockList.size(); i++) {
            CompoundTag block = blockList.getCompound(i);
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            int state = block.getInt("state");
            if (pos.size() != 3 || state < 0 || state >= states.size()) {
                return broken(id, json, Component.translatable(KEY + "unreadable", id.toString()));
            }
            BlockPos at = new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
            if (at.getX() < 0 || at.getY() < 0 || at.getZ() < 0 || at.getX() >= sizeX || at.getY() >= sizeY || at.getZ() >= sizeZ) {
                return broken(id, json, Component.translatable(KEY + "unreadable", id.toString()));
            }
            blocks.put(at, states.get(state));
        }
        List<BuildingTemplate.Block> list = new ArrayList<>();
        blocks.forEach((pos, state) -> list.add(new BuildingTemplate.Block(pos, state)));
        list.sort(Comparator.comparingInt((BuildingTemplate.Block b) -> b.pos().getY()).thenComparingInt(b -> b.pos().getZ())
                .thenComparingInt(b -> b.pos().getX()));
        return new Raw(id, json, list, sizeX, sizeY, sizeZ, null);
    }

    // ---------------------------------------------------------------- finishing

    private static Map<ResourceLocation, BuildingTemplate> finish(Map<ResourceLocation, Raw> source) {
        Map<ResourceLocation, BuildingTemplate> result = new LinkedHashMap<>();
        for (Raw template : source.values()) {
            try {
                result.put(template.id(), finish(template));
            } catch (RuntimeException e) {
                LOGGER.warn("Building template {} cannot be used: {}", template.id(), e.toString());
                result.put(template.id(), finish(broken(template.id(), template.json(),
                        Component.translatable(KEY + "unreadable", template.id().toString()))));
            }
        }
        return Map.copyOf(result);
    }

    static BuildingTemplate finish(Raw template) {
        JsonObject json = template.json();
        String nameKey = cut(string(json, "name", "kingdomworld.building." + template.id().getPath().replace('/', '.')));
        String fallback = cut(string(json, "fallback_name", ""));
        BuildingTemplate.Category category = BuildingTemplate.Category.byId(string(json, "category", "decoration"));
        String type = cut(string(json, "type", template.id().getPath()));
        String role = role(json, type, template.id());
        Map<String, Double> params = numbers(json, "role_params", template.id());
        int level = Math.max(1, Math.min(io.github.sekerator.kingdomworld.core.construction.BuildingRole.MAX_LEVEL, integer(json, "level", 1)));
        String upgradesTo = cut(string(json, "upgrades_to", ""));
        ResourceLocation iconId = ResourceLocation.tryParse(string(json, "icon", "minecraft:bricks"));
        ItemStack icon = iconId == null || !ForgeRegistries.ITEMS.containsKey(iconId) ? new ItemStack(Items.BRICKS)
                : new ItemStack(ForgeRegistries.ITEMS.getValue(iconId));
        BlockPos anchor = anchor(json, template);
        Facing entrance = Facing.byId(string(json, "entrance", "south")).orElse(Facing.SOUTH);
        List<BlockCost> costs = new ArrayList<>(template.blocks().size());
        for (BuildingTemplate.Block block : template.blocks()) {
            costs.add(TemplateBlocks.cost(block.state()));
        }
        TemplateCost cost = TemplateCost.of(costs);
        Component problem = template.problem();
        if (problem == null && !cost.buildable()) {
            LOGGER.warn("Building template {} holds blocks no item makes: {}", template.id(), cost.unobtainable());
            problem = Component.translatable(KEY + "unobtainable", String.join(", ", cost.unobtainable()));
        }
        if (problem == null && cost.blocks() == 0) {
            problem = Component.translatable(KEY + "empty");
        }
        return new BuildingTemplate(template.id(), nameKey, fallback, category, type, role, params, level, upgradesTo, null, icon,
                template.sizeX(), template.sizeY(), template.sizeZ(), anchor, entrance, template.blocks(), costs, cost, fingerprint(template),
                problem);
    }

    /**
     * The role a description gives: {@code role} if it names one ({@code none} for no role), else the type when a role of
     * that name exists. A role nobody registered is kept, so that a role of a data pack that loads later still counts.
     */
    static String role(JsonObject json, String type, ResourceLocation id) {
        JsonElement element = json.get("role");
        if (element != null && element.isJsonPrimitive()) {
            String role = element.getAsString().toLowerCase(java.util.Locale.ROOT);
            if (role.isEmpty() || "none".equals(role)) {
                return "";
            }
            if (!io.github.sekerator.kingdomworld.core.construction.BuildingRoles.exists(role)) {
                LOGGER.warn("Building template {} names the role {}, which is not registered", id, role);
            }
            return role.length() > io.github.sekerator.kingdomworld.core.construction.BuildingRole.MAX_ID ? "" : role;
        }
        return io.github.sekerator.kingdomworld.core.construction.BuildingRoles.exists(type) ? type : "";
    }

    /**
     * The numbers of an object of the description, by name; what is not a finite number is left out.
     */
    static Map<String, Double> numbers(JsonObject json, String name, ResourceLocation id) {
        Map<String, Double> result = new LinkedHashMap<>();
        JsonElement element = json.get(name);
        if (element == null || !element.isJsonObject()) {
            return result;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            try {
                double value = entry.getValue().getAsDouble();
                if (Double.isFinite(value) && result.size() < 32) {
                    result.put(entry.getKey(), value);
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Building template {}: {} of {} is not a number", id, entry.getKey(), name);
            }
        }
        return result;
    }

    private static int integer(JsonObject json, String name, int fallback) {
        JsonElement element = json.get(name);
        try {
            return element != null && element.isJsonPrimitive() ? element.getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static BlockPos anchor(JsonObject json, Raw template) {
        int x = template.sizeX() / 2;
        int y = 0;
        int z = template.sizeZ() - 1;
        JsonElement element = json.get("anchor");
        if (element != null && element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (array.size() == 3) {
                try {
                    x = array.get(0).getAsInt();
                    y = array.get(1).getAsInt();
                    z = array.get(2).getAsInt();
                } catch (RuntimeException e) {
                    LOGGER.warn("Building template {}: the anchor is not three numbers", template.id());
                }
            }
        }
        return new BlockPos(Math.max(0, Math.min(template.sizeX() - 1, x)), Math.max(0, Math.min(template.sizeY() - 1, y)),
                Math.max(0, Math.min(template.sizeZ() - 1, z)));
    }

    private static String string(JsonObject json, String name, String fallback) {
        JsonElement element = json.get(name);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static String cut(String text) {
        return text.length() > BuildingTemplate.MAX_TEXT ? text.substring(0, BuildingTemplate.MAX_TEXT) : text;
    }

    /**
     * A hash of the size and the blocks of a template.
     */
    static long fingerprint(Raw template) {
        long hash = 0xcbf29ce484222325L;
        StringBuilder text = new StringBuilder();
        text.append(template.sizeX()).append(',').append(template.sizeY()).append(',').append(template.sizeZ()).append(';');
        for (BuildingTemplate.Block block : template.blocks()) {
            text.append(block.pos().getX()).append(',').append(block.pos().getY()).append(',').append(block.pos().getZ()).append('=')
                    .append(TemplateBlocks.hashText(block.state())).append(';');
        }
        for (byte b : text.toString().getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & 0xFF;
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
