package io.github.sekerator.kingdomworld.core.construction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Which blocks of a wood species or of a stone stand for which, made from the ids of all blocks of the game: a species is
 * known by its planks ({@code ns:spruce_planks}), its other blocks by the usual names (logs or stems, wood, stripped ones,
 * stairs, slabs, fences, gates, doors, trapdoors, buttons, pressure plates, signs); a stone by a block with stairs and a
 * slab of its name ({@code ns:deepslate_bricks}, {@code ns:deepslate_brick_stairs}, ...). So the woods and stones of mods
 * work as long as their blocks are named the way the game names its own. A block the chosen species or stone lacks stays
 * as the template has it.
 */
public final class MaterialTable {
    /** The kinds of blocks of a wood species and how they are named, the species name standing for {@code %s}. */
    private static final Map<String, List<String>> WOOD = new LinkedHashMap<>();
    /** The kinds of blocks of a stone, by suffix after the stem of its name. */
    private static final List<String> STONE = List.of("", "_stairs", "_slab", "_wall");

    static {
        WOOD.put("planks", List.of("%s_planks"));
        WOOD.put("log", List.of("%s_log", "%s_stem", "%s_block"));
        WOOD.put("wood", List.of("%s_wood", "%s_hyphae"));
        WOOD.put("stripped_log", List.of("stripped_%s_log", "stripped_%s_stem", "stripped_%s_block"));
        WOOD.put("stripped_wood", List.of("stripped_%s_wood", "stripped_%s_hyphae"));
        WOOD.put("stairs", List.of("%s_stairs"));
        WOOD.put("slab", List.of("%s_slab"));
        WOOD.put("fence", List.of("%s_fence"));
        WOOD.put("fence_gate", List.of("%s_fence_gate"));
        WOOD.put("door", List.of("%s_door"));
        WOOD.put("trapdoor", List.of("%s_trapdoor"));
        WOOD.put("button", List.of("%s_button"));
        WOOD.put("pressure_plate", List.of("%s_pressure_plate"));
        WOOD.put("sign", List.of("%s_sign"));
        WOOD.put("wall_sign", List.of("%s_wall_sign"));
        WOOD.put("hanging_sign", List.of("%s_hanging_sign"));
        WOOD.put("wall_hanging_sign", List.of("%s_wall_hanging_sign"));
    }

    /** Species to its blocks by kind. */
    private final Map<String, Map<String, String>> woods = new LinkedHashMap<>();
    /** Stone to its blocks by suffix. */
    private final Map<String, Map<String, String>> stones = new LinkedHashMap<>();
    /** Block to {species or stone, kind}. */
    private final Map<String, String[]> woodOf = new HashMap<>();
    private final Map<String, String[]> stoneOf = new HashMap<>();

    private MaterialTable() {
    }

    /**
     * @param blocks    ids of every block of the game
     * @param stoneBase whether a block may be the main block of a stone (by its tags: mined with a pickaxe, not metal)
     */
    public static MaterialTable of(Collection<String> blocks, Predicate<String> stoneBase) {
        MaterialTable table = new MaterialTable();
        Set<String> all = new HashSet<>(blocks);
        List<String> sorted = new ArrayList<>(all);
        sorted.sort(MaterialTable::order);
        for (String id : sorted) {
            if (!id.endsWith("_planks")) {
                continue;
            }
            String species = id.substring(0, id.length() - "_planks".length());
            Map<String, String> kinds = new LinkedHashMap<>();
            WOOD.forEach((kind, patterns) -> {
                for (String pattern : patterns) {
                    String candidate = named(species, pattern);
                    if (all.contains(candidate)) {
                        kinds.put(kind, candidate);
                        break;
                    }
                }
            });
            if (kinds.size() < 2) {
                continue;
            }
            table.woods.put(species, kinds);
            kinds.forEach((kind, block) -> table.woodOf.putIfAbsent(block, new String[]{species, kind}));
        }
        for (String id : sorted) {
            if (table.woodOf.containsKey(id) || !stoneBase.test(id)) {
                continue;
            }
            String stem = stem(id);
            if (!all.contains(stem + "_stairs") || !all.contains(stem + "_slab")) {
                continue;
            }
            Map<String, String> kinds = new LinkedHashMap<>();
            for (String suffix : STONE) {
                String candidate = suffix.isEmpty() ? id : stem + suffix;
                if (all.contains(candidate) && !table.woodOf.containsKey(candidate) && !table.stoneOf.containsKey(candidate)) {
                    kinds.put(suffix, candidate);
                }
            }
            if (kinds.size() < 3) {
                continue;
            }
            table.stones.put(id, kinds);
            kinds.forEach((suffix, block) -> table.stoneOf.put(block, new String[]{id, suffix}));
        }
        return table;
    }

    private static String named(String species, String pattern) {
        int colon = species.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : species.substring(0, colon);
        String name = colon < 0 ? species : species.substring(colon + 1);
        return namespace + ":" + pattern.replace("%s", name);
    }

    /**
     * The stem a stone's stairs and slabs are named after: bricks and tiles lose their plural.
     */
    static String stem(String id) {
        if (id.endsWith("bricks") || id.endsWith("tiles")) {
            return id.substring(0, id.length() - 1);
        }
        return id;
    }

    /**
     * The game's own blocks first, then by id.
     */
    private static int order(String a, String b) {
        boolean va = a.startsWith("minecraft:");
        boolean vb = b.startsWith("minecraft:");
        if (va != vb) {
            return va ? -1 : 1;
        }
        return a.compareTo(b);
    }

    /**
     * The wood species, the game's first.
     */
    public List<String> woods() {
        return List.copyOf(woods.keySet());
    }

    /**
     * The stones, the game's first.
     */
    public List<String> stones() {
        return List.copyOf(stones.keySet());
    }

    public boolean hasWood(String species) {
        return woods.containsKey(species);
    }

    public boolean hasStone(String stone) {
        return stones.containsKey(stone);
    }

    /**
     * The species of a wooden block, or empty.
     */
    public String woodOf(String block) {
        String[] found = woodOf.get(block);
        return found == null ? "" : found[0];
    }

    /**
     * The stone of a block, or empty.
     */
    public String stoneOf(String block) {
        String[] found = stoneOf.get(block);
        return found == null ? "" : found[0];
    }

    /**
     * The block that stands for a block of a template when it is built of other materials, or the block itself.
     */
    public String substitute(String block, Materials materials) {
        if (!materials.wood().isEmpty() && !Materials.AUTO.equals(materials.wood())) {
            String[] wood = woodOf.get(block);
            if (wood != null) {
                Map<String, String> target = woods.get(materials.wood());
                String replacement = target == null ? null : target.get(wood[1]);
                return replacement == null ? block : replacement;
            }
        }
        if (!materials.stone().isEmpty()) {
            String[] stone = stoneOf.get(block);
            if (stone != null) {
                Map<String, String> target = stones.get(materials.stone());
                String replacement = target == null ? null : target.get(stone[1]);
                return replacement == null ? block : replacement;
            }
        }
        return block;
    }

    /**
     * The wood that grows where a building is laid out: a species named in the biome's id (the one of the biome's own mod
     * first), else by what the biome is (taiga and snow spruce, jungle, savanna acacia), else empty: the template's own.
     *
     * @param biome id of the biome
     * @param tags  ids of the tags of the biome
     */
    public String woodForBiome(String biome, Set<String> tags) {
        String path = biome.contains(":") ? biome.substring(biome.indexOf(':') + 1) : biome;
        String namespace = biome.contains(":") ? biome.substring(0, biome.indexOf(':')) : "minecraft";
        if (path.contains("dark_forest") && woods.containsKey("minecraft:dark_oak")) {
            return "minecraft:dark_oak";
        }
        String best = "";
        List<String> candidates = new ArrayList<>(woods.keySet());
        candidates.sort(Comparator.comparing((String species) -> !species.startsWith(namespace + ":"))
                .thenComparing(species -> -species.length()));
        for (String species : candidates) {
            String name = species.substring(species.indexOf(':') + 1);
            if (!name.isEmpty() && contains(path, name)) {
                best = species;
                break;
            }
        }
        if (!best.isEmpty()) {
            return best;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (tags.contains("minecraft:is_taiga") || lower.contains("snowy") || lower.contains("frozen") || lower.contains("taiga")
                || lower.contains("grove")) {
            return woods.containsKey("minecraft:spruce") ? "minecraft:spruce" : "";
        }
        if (tags.contains("minecraft:is_jungle")) {
            return woods.containsKey("minecraft:jungle") ? "minecraft:jungle" : "";
        }
        if (tags.contains("minecraft:is_savanna")) {
            return woods.containsKey("minecraft:acacia") ? "minecraft:acacia" : "";
        }
        return "";
    }

    /**
     * Whether a biome path names a species as a whole word: {@code birch_forest} names birch, {@code oak_forest} does not
     * name dark oak.
     */
    private static boolean contains(String path, String name) {
        int from = 0;
        while (true) {
            int at = path.indexOf(name, from);
            if (at < 0) {
                return false;
            }
            boolean start = at == 0 || path.charAt(at - 1) == '_' || path.charAt(at - 1) == '/';
            int end = at + name.length();
            boolean finish = end == path.length() || path.charAt(end) == '_' || path.charAt(end) == '/';
            if (start && finish) {
                return true;
            }
            from = at + 1;
        }
    }
}
