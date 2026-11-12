package io.github.sekerator.kingdomworld.core.npc;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The architectural styles of kingdoms: which one suits a climate, which one a new kingdom of the world takes so that no two of
 * them share one, and the stone of their walls and streets.
 */
public final class ArchitecturalStyles {
    /** The ids of the styles, in the order of their indices. */
    public static final List<String> IDS = TemplateCatalog.STYLES;

    /** The style of the mountains, which a place among mountains prefers before all others. */
    private static final String MOUNTAIN = "mountain";

    /** The styles in the order every climate prefers them, each list holding every style once. */
    private static final Map<NpcStyle.Climate, List<String>> PREFERENCES = new EnumMap<>(NpcStyle.Climate.class);

    /** By style: the block of its stone walls, the paving of its squares and avenues, the rougher paving of its streets. */
    private static final Map<String, String[]> STONES = new LinkedHashMap<>();

    static {
        PREFERENCES.put(NpcStyle.Climate.COLD, List.of("nordic", "terem", "mountain", "gothic", "classic", "eastern", "southern", "desert"));
        PREFERENCES.put(NpcStyle.Climate.DRY, List.of("desert", "southern", "mountain", "eastern", "classic", "gothic", "terem", "nordic"));
        PREFERENCES.put(NpcStyle.Climate.LUSH, List.of("eastern", "southern", "classic", "terem", "gothic", "desert", "mountain", "nordic"));
        PREFERENCES.put(NpcStyle.Climate.TEMPERATE,
                List.of("classic", "gothic", "terem", "southern", "mountain", "nordic", "eastern", "desert"));

        // Stone with half-timbering: dressed grey stone everywhere.
        STONES.put("classic", new String[]{"minecraft:stone_bricks", "minecraft:stone_bricks", "minecraft:cobblestone"});
        // The wooden terem: a white stone kremlin, squares laid with planks, streets of trodden earth.
        STONES.put("terem", new String[]{"minecraft:polished_diorite", "minecraft:spruce_planks", "minecraft:packed_mud"});
        // The timber north: rough stone, cobbles and mossy cobbles.
        STONES.put("nordic", new String[]{"minecraft:cobblestone", "minecraft:cobblestone", "minecraft:mossy_cobblestone"});
        // The south: pale dressed stone, squares of brick, streets of sandstone.
        STONES.put("southern", new String[]{"minecraft:smooth_sandstone", "minecraft:bricks", "minecraft:smooth_sandstone"});
        // The desert: walls of mud brick, cut sandstone on the squares.
        STONES.put("desert", new String[]{"minecraft:mud_bricks", "minecraft:cut_sandstone", "minecraft:sandstone"});
        // The east: smooth stone walls on their platforms, flags of smooth stone and andesite.
        STONES.put("eastern", new String[]{"minecraft:stone", "minecraft:smooth_stone", "minecraft:andesite"});
        // The gothic: deepslate.
        STONES.put("gothic", new String[]{"minecraft:deepslate_bricks", "minecraft:deepslate_tiles", "minecraft:cobbled_deepslate"});
        // The mountains: heavy dark stone, squares of polished andesite.
        STONES.put("mountain", new String[]{"minecraft:cobbled_deepslate", "minecraft:polished_andesite", "minecraft:cobblestone"});
    }

    private ArchitecturalStyles() {
    }

    /**
     * Whether a style of this id exists.
     */
    public static boolean known(String id) {
        return id != null && IDS.contains(id);
    }

    /**
     * The index of a style, or -1 for an unknown id.
     */
    public static int index(String id) {
        return id == null ? -1 : IDS.indexOf(id);
    }

    /**
     * The id of a style by its index, or empty for an index out of range.
     */
    public static String id(int index) {
        return index >= 0 && index < IDS.size() ? IDS.get(index) : "";
    }

    /**
     * The styles in the order a climate prefers them, every style once; among mountains the style of the mountains comes first.
     *
     * @param mountains whether the place lies in mountains
     */
    public static List<String> preferences(NpcStyle.Climate climate, boolean mountains) {
        List<String> list = PREFERENCES.get(climate == null ? NpcStyle.Climate.TEMPERATE : climate);
        if (!mountains) {
            return list;
        }
        List<String> moved = new ArrayList<>(list.size());
        moved.add(MOUNTAIN);
        for (String id : list) {
            if (!id.equals(MOUNTAIN)) {
                moved.add(id);
            }
        }
        return List.copyOf(moved);
    }

    /**
     * The style a climate prefers most.
     */
    public static String byClimate(NpcStyle.Climate climate, boolean mountains) {
        return preferences(climate, mountains).get(0);
    }

    /**
     * The style of a new kingdom of the world: the first the climate prefers that no other kingdom holds. When every style is
     * held, one of them by the seed, so that the kingdoms beyond the eighth repeat styles.
     *
     * @param taken the styles other kingdoms of the world hold
     */
    public static String pick(NpcStyle.Climate climate, boolean mountains, long seed, Set<String> taken) {
        List<String> list = preferences(climate, mountains);
        for (String id : list) {
            if (taken == null || !taken.contains(id)) {
                return id;
            }
        }
        return list.get((int) Math.floorMod(GuildHouses.mix(seed ^ 0x5354594C45L), (long) list.size()));
    }

    /**
     * The block of the stone walls of a style, or empty for an unknown style.
     */
    public static String wallBlock(String style) {
        return stone(style, 0);
    }

    /**
     * The paving of the squares and avenues of a style, or empty for an unknown style.
     */
    public static String paving(String style) {
        return stone(style, 1);
    }

    /**
     * The rougher paving of the streets of a style, or empty for an unknown style.
     */
    public static String cobbles(String style) {
        return stone(style, 2);
    }

    private static String stone(String style, int which) {
        String[] stones = style == null ? null : STONES.get(style);
        return stones == null ? "" : stones[which];
    }
}
