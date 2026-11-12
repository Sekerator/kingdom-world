package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.construction.Look;
import io.github.sekerator.kingdomworld.core.construction.Materials;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * The looks of the buildings of a kingdom of the world, so that a street is no row of the same house: the castle and the
 * public buildings of a capital wear the look of its style, every house its own one, drawn from the seed of its plot out of
 * the choices of the style and of its quarter. A noble house gets roofs of stone and keeps its shutters and chimneys, a poor
 * one gets plaster of clay, roofs of shingles and often neither shutters nor a chimney.
 *
 * <p>Pure data: the same style, quarter and seed give the same look on every server and after every restart.
 */
public final class BuildingLooks {
    /**
     * Where a building stands, as far as its look goes, and for the great buildings of a capital what they are.
     */
    public enum Quarter {
        /** The buildings of the institutions, towers and monuments: the look of the style. */
        PUBLIC,
        /** The castle: the look of the style with the roofs and spires the kingdom chose for it. */
        CASTLE,
        /** The house of the adventurers' guild: its own roofs and spires. */
        GUILD,
        /** The tower of the mages' guild: its own roofs and spires. */
        MAGES,
        /** The houses of the nobles by the castle. */
        NOBLE,
        /** The houses of the merchants by the market. */
        MERCHANT,
        /** The houses of the craftsmen. */
        CRAFT,
        /** The huts of the poor by the wall. */
        POOR
    }

    /**
     * The choices of a style: for every part of a look the public one first, then those of houses; an empty value keeps the
     * block of the template, {@link Look#REMOVED} leaves the part out.
     *
     * @param walls  stones of the walls of houses (the public buildings take the stone of the style); the fourth for the poor
     *               and the fifth for the nobles in the first place
     * @param woods  woods of houses besides the wood of the place
     * @param parts  the choices of every part, the public one first; of the roofs the second and third are of wood (the poor
     *               take them), the first, fourth and sixth of stone or metal (the nobles take them)
     * @param clay   plaster of the poor
     */
    private record Palette(List<String> walls, List<String> woods, Map<Look.Part, List<String>> parts, List<String> clay,
                           Map<Quarter, List<String>> roofs, Map<Quarter, List<String>> spires) {
        /**
         * This palette with the roofs and spires the great buildings choose from by their seed.
         */
        Palette great(List<String> castleRoofs, List<String> castleSpires, List<String> guildRoofs, List<String> guildSpires,
                      List<String> mageRoofs, List<String> mageSpires) {
            Map<Quarter, List<String>> roofs = new EnumMap<>(Quarter.class);
            Map<Quarter, List<String>> spires = new EnumMap<>(Quarter.class);
            roofs.put(Quarter.CASTLE, castleRoofs);
            spires.put(Quarter.CASTLE, castleSpires);
            roofs.put(Quarter.GUILD, guildRoofs);
            spires.put(Quarter.GUILD, guildSpires);
            roofs.put(Quarter.MAGES, mageRoofs);
            spires.put(Quarter.MAGES, mageSpires);
            return new Palette(walls, woods, parts, clay, roofs, spires);
        }
    }

    private static final Map<NpcStyle, Palette> PALETTES = new EnumMap<>(NpcStyle.class);
    /** The palettes of the architectural styles, by id; their wood and stone where the style has its own. */
    private static final Map<String, Palette> STYLE_PALETTES = new java.util.LinkedHashMap<>();
    private static final Map<String, String[]> STYLE_MATERIALS = new java.util.LinkedHashMap<>();

    static {
        PALETTES.put(NpcStyle.STONE, palette(
                List.of("", "", "minecraft:cobblestone", "minecraft:andesite", "minecraft:mossy_stone_bricks"),
                List.of("minecraft:spruce", "minecraft:oak", "minecraft:birch"),
                List.of("minecraft:deepslate_tiles", "minecraft:spruce", "minecraft:dark_oak", "minecraft:bricks", "minecraft:mangrove",
                        "minecraft:dark_prismarine"),
                List.of("minecraft:dark_prismarine", "minecraft:deepslate_tiles", "minecraft:waxed_oxidized_cut_copper"),
                List.of("", "minecraft:mossy_cobblestone", "minecraft:stone_bricks", "minecraft:cobbled_deepslate"),
                List.of("", "minecraft:stone", "minecraft:polished_diorite"),
                List.of("", "minecraft:chiseled_stone_bricks"),
                List.of("", "minecraft:calcite", "minecraft:yellow_terracotta", "minecraft:light_gray_terracotta", "minecraft:white_concrete"),
                List.of("minecraft:spruce", "minecraft:dark_oak", "minecraft:birch", Look.REMOVED),
                List.of("", "minecraft:stone_bricks", "minecraft:cobblestone", Look.REMOVED),
                List.of("minecraft:yellow_terracotta", "minecraft:white_terracotta", "minecraft:brown_terracotta")));
        PALETTES.put(NpcStyle.BRICK, palette(
                List.of("", "", "minecraft:stone_bricks", "minecraft:granite", "minecraft:bricks"),
                List.of("minecraft:dark_oak", "minecraft:oak", "minecraft:spruce"),
                List.of("minecraft:deepslate_tiles", "minecraft:spruce", "minecraft:dark_oak", "minecraft:dark_prismarine", "minecraft:mangrove",
                        "minecraft:waxed_oxidized_cut_copper"),
                List.of("minecraft:dark_prismarine", "minecraft:deepslate_tiles", "minecraft:waxed_oxidized_cut_copper"),
                List.of("minecraft:stone_bricks", "minecraft:cobblestone", "minecraft:stone_bricks", "minecraft:andesite"),
                List.of("minecraft:stone", "minecraft:polished_andesite", "minecraft:smooth_sandstone"),
                List.of("", "minecraft:chiseled_stone_bricks"),
                List.of("", "minecraft:yellow_terracotta", "minecraft:calcite", "minecraft:light_gray_terracotta", "minecraft:smooth_sandstone"),
                List.of("minecraft:dark_oak", "minecraft:spruce", "minecraft:oak", Look.REMOVED),
                List.of("minecraft:stone_bricks", "", "minecraft:cobblestone", Look.REMOVED),
                List.of("minecraft:yellow_terracotta", "minecraft:white_terracotta", "minecraft:terracotta")));
        PALETTES.put(NpcStyle.SANDSTONE, palette(
                List.of("", "", "minecraft:smooth_sandstone", "minecraft:red_sandstone", "minecraft:sandstone"),
                List.of("minecraft:acacia", "minecraft:oak", "minecraft:jungle"),
                List.of("minecraft:bricks", "minecraft:acacia", "minecraft:dark_oak", "minecraft:red_sandstone", "minecraft:granite",
                        "minecraft:mud_bricks"),
                List.of("minecraft:prismarine_bricks", "minecraft:bricks", "minecraft:waxed_cut_copper"),
                List.of("minecraft:red_sandstone", "minecraft:sandstone", "minecraft:smooth_red_sandstone", "minecraft:granite"),
                List.of("minecraft:smooth_sandstone", "minecraft:smooth_red_sandstone", "minecraft:polished_granite"),
                List.of("minecraft:chiseled_sandstone", "minecraft:chiseled_red_sandstone"),
                List.of("", "minecraft:terracotta", "minecraft:yellow_terracotta", "minecraft:orange_terracotta", "minecraft:smooth_sandstone"),
                List.of("minecraft:acacia", "minecraft:dark_oak", "minecraft:jungle", Look.REMOVED),
                List.of("minecraft:sandstone", "", "minecraft:red_sandstone", Look.REMOVED),
                List.of("minecraft:terracotta", "minecraft:white_terracotta", "minecraft:orange_terracotta")));
        PALETTES.put(NpcStyle.DEEPSLATE, palette(
                List.of("", "", "minecraft:stone_bricks", "minecraft:polished_blackstone_bricks", "minecraft:cobbled_deepslate"),
                List.of("minecraft:dark_oak", "minecraft:spruce", "minecraft:oak"),
                List.of("minecraft:waxed_oxidized_cut_copper", "minecraft:dark_oak", "minecraft:spruce", "minecraft:deepslate_tiles",
                        "minecraft:bricks", "minecraft:dark_prismarine"),
                List.of("minecraft:waxed_oxidized_cut_copper", "minecraft:dark_prismarine", "minecraft:bricks"),
                List.of("minecraft:cobbled_deepslate", "minecraft:cobblestone", "minecraft:blackstone", "minecraft:polished_blackstone"),
                List.of("minecraft:polished_deepslate", "minecraft:polished_blackstone", "minecraft:stone"),
                List.of("minecraft:chiseled_deepslate", "minecraft:chiseled_polished_blackstone"),
                List.of("", "minecraft:light_gray_terracotta", "minecraft:calcite", "minecraft:cyan_terracotta", "minecraft:white_concrete"),
                List.of("minecraft:spruce", "minecraft:dark_oak", "minecraft:crimson", Look.REMOVED),
                List.of("minecraft:cobbled_deepslate", "", "minecraft:stone_bricks", Look.REMOVED),
                List.of("minecraft:light_gray_terracotta", "minecraft:white_terracotta", "minecraft:gray_terracotta")));
        PALETTES.put(NpcStyle.MUD, palette(
                List.of("", "", "minecraft:mossy_stone_bricks", "minecraft:bricks", "minecraft:sandstone"),
                List.of("minecraft:jungle", "minecraft:mangrove", "minecraft:oak"),
                List.of("minecraft:dark_oak", "minecraft:jungle", "minecraft:bamboo", "minecraft:mangrove", "minecraft:spruce",
                        "minecraft:bricks"),
                List.of("minecraft:mangrove", "minecraft:dark_oak", "minecraft:waxed_oxidized_cut_copper"),
                List.of("minecraft:mossy_cobblestone", "minecraft:cobblestone", "minecraft:mossy_stone_bricks", "minecraft:stone_bricks"),
                List.of("minecraft:stone_bricks", "minecraft:smooth_sandstone", "minecraft:polished_granite"),
                List.of("minecraft:chiseled_stone_bricks", "minecraft:chiseled_sandstone"),
                List.of("", "minecraft:terracotta", "minecraft:yellow_terracotta", "minecraft:brown_terracotta", "minecraft:lime_terracotta"),
                List.of("minecraft:jungle", "minecraft:dark_oak", "minecraft:bamboo", Look.REMOVED),
                List.of("minecraft:mud_bricks", "", "minecraft:cobblestone", Look.REMOVED),
                List.of("minecraft:terracotta", "minecraft:brown_terracotta", "minecraft:white_terracotta")));
    }

    static {
        // Stone with half-timbering: slate roofs and spires on the public buildings, houses of every roof.
        STYLE_PALETTES.put("classic", PALETTES.get(NpcStyle.STONE).great(
                List.of("minecraft:deepslate_tiles", "minecraft:dark_oak", "minecraft:deepslate_tiles"),
                List.of("minecraft:dark_prismarine", "minecraft:deepslate_tiles", "minecraft:waxed_oxidized_cut_copper",
                        "minecraft:polished_blackstone_bricks", "minecraft:waxed_weathered_cut_copper"),
                List.of("minecraft:mangrove", "minecraft:dark_oak", "minecraft:deepslate_tiles"),
                List.of("minecraft:bricks", "minecraft:waxed_cut_copper", "minecraft:deepslate_tiles", "minecraft:mangrove"),
                List.of("minecraft:deepslate_tiles", "minecraft:dark_oak", "minecraft:spruce"),
                List.of("minecraft:crimson", "minecraft:nether_bricks", "minecraft:warped", "minecraft:dark_prismarine")));
        STYLE_MATERIALS.put("classic", new String[]{"", ""});
        // The wooden terem: white stone, light shingles on roofs and tents, onion domes of copper, gold, blue or green.
        STYLE_PALETTES.put("terem", palette(
                List.of("", "", "minecraft:diorite", "minecraft:cobblestone", "minecraft:stone_bricks"),
                List.of("minecraft:oak", "minecraft:dark_oak", "minecraft:spruce"),
                List.of("minecraft:oak", "minecraft:spruce", "minecraft:dark_oak", "minecraft:oak", "minecraft:mangrove",
                        "minecraft:spruce"),
                List.of("minecraft:waxed_cut_copper", "minecraft:bamboo", "minecraft:dark_prismarine", "minecraft:waxed_weathered_cut_copper",
                        "minecraft:waxed_oxidized_cut_copper"),
                List.of("minecraft:diorite", "minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:stone_bricks"),
                List.of("minecraft:smooth_quartz", "minecraft:polished_diorite", "minecraft:stone"),
                List.of("", "minecraft:chiseled_stone_bricks"),
                List.of("", "minecraft:calcite", "minecraft:white_concrete", "minecraft:light_gray_terracotta"),
                List.of("minecraft:spruce", "minecraft:dark_oak", "minecraft:birch", Look.REMOVED),
                List.of("minecraft:cobblestone", "minecraft:stone_bricks", "minecraft:mud_bricks", Look.REMOVED),
                List.of("", "minecraft:calcite", "minecraft:white_terracotta")).great(
                List.of("minecraft:oak", "minecraft:spruce"),
                List.of("minecraft:waxed_cut_copper", "minecraft:bamboo", "minecraft:dark_prismarine", "minecraft:waxed_weathered_cut_copper",
                        "minecraft:waxed_oxidized_cut_copper"),
                List.of("minecraft:oak", "minecraft:dark_oak"),
                List.of("minecraft:dark_prismarine", "minecraft:waxed_oxidized_cut_copper", "minecraft:waxed_cut_copper"),
                List.of("minecraft:spruce", "minecraft:oak"),
                List.of("minecraft:dark_prismarine", "minecraft:bamboo", "minecraft:waxed_weathered_cut_copper")));
        STYLE_MATERIALS.put("terem", new String[]{"minecraft:spruce", "minecraft:polished_diorite"});
    }

    /** Colours of the cloth of houses: awnings, curtains, beds. */
    private static final List<String> CLOTHS = List.of("red", "blue", "green", "yellow", "orange", "cyan", "purple", "brown", "white",
            "light_blue", "lime", "magenta");

    private BuildingLooks() {
    }

    private static Palette palette(List<String> walls, List<String> woods, List<String> roofs, List<String> spires, List<String> rough,
                                   List<String> trim, List<String> carved, List<String> plaster, List<String> shutters,
                                   List<String> chimneys, List<String> clay) {
        Map<Look.Part, List<String>> parts = new EnumMap<>(Look.Part.class);
        parts.put(Look.Part.ROOF, roofs);
        parts.put(Look.Part.SPIRE, spires);
        parts.put(Look.Part.ROUGH, rough);
        parts.put(Look.Part.TRIM, trim);
        parts.put(Look.Part.CARVED, carved);
        parts.put(Look.Part.PLASTER, plaster);
        parts.put(Look.Part.SHUTTER, shutters);
        parts.put(Look.Part.CHIMNEY, chimneys);
        return new Palette(walls, woods, parts, clay, Map.of(), Map.of());
    }

    /**
     * The materials of a building of a kingdom of the world.
     *
     * @param style   the style of the kingdom
     * @param wood    the wood of the place, used where the style has none of its own and by most houses
     * @param cloth   the colour of dye of the kingdom, for the banners and carpets of the public buildings
     * @param quarter where the building stands
     * @param seed    the seed of its plot
     */
    public static Materials of(NpcStyle style, String wood, String cloth, Quarter quarter, long seed) {
        return of(PALETTES.get(style), style.wood(), style.stone(), wood, cloth, quarter, seed);
    }

    /**
     * The materials of a building of an architectural style (see {@link TemplateCatalog#STYLES}).
     *
     * @throws IllegalArgumentException for a style without a palette
     */
    public static Materials of(String style, String wood, String cloth, Quarter quarter, long seed) {
        Palette palette = STYLE_PALETTES.get(style);
        if (palette == null) {
            throw new IllegalArgumentException("No palette of the style " + style);
        }
        String[] materials = STYLE_MATERIALS.get(style);
        return of(palette, materials[0], materials[1], wood, cloth, quarter, seed);
    }

    /**
     * The architectural styles that have a palette.
     */
    public static List<String> styles() {
        return List.copyOf(STYLE_PALETTES.keySet());
    }

    private static Materials of(Palette palette, String styleWood, String styleStone, String wood, String cloth, Quarter quarter, long seed) {
        String placeWood = styleWood.isEmpty() ? wood : styleWood;
        Map<Look.Part, String> parts = new EnumMap<>(Look.Part.class);
        if (quarter.ordinal() <= Quarter.MAGES.ordinal()) {
            palette.parts().forEach((part, choices) -> parts.put(part, choices.get(0)));
            parts.put(Look.Part.CLOTH, cloth);
            // The castle, the guild and the tower of the mages choose their roofs and spires by their seed, so that they are
            // not of one colour with each other nor with the castles of other kingdoms.
            SplittableRandom great = new SplittableRandom(seed ^ 0x47524541L * (quarter.ordinal() + 1));
            List<String> roofs = palette.roofs().get(quarter);
            List<String> spires = palette.spires().get(quarter);
            if (roofs != null) {
                parts.put(Look.Part.ROOF, pick(great, roofs));
            }
            if (spires != null) {
                parts.put(Look.Part.SPIRE, pick(great, spires));
            }
            return new Materials(placeWood, styleStone, Look.of(parts));
        }
        SplittableRandom random = new SplittableRandom(seed ^ 0x4C4F4F4BL * (quarter.ordinal() + 1));
        String walls = pick(random, palette.walls());
        String houseWood = random.nextInt(3) == 0 ? pick(random, palette.woods()) : placeWood;
        palette.parts().forEach((part, choices) -> parts.put(part, pick(random, choices)));
        parts.put(Look.Part.CLOTH, pick(random, CLOTHS));
        switch (quarter) {
            case NOBLE -> {
                // Stone roofs, the carved accents of the style, shutters and chimneys kept.
                parts.put(Look.Part.ROOF, palette.parts().get(Look.Part.ROOF).get(List.of(0, 3, 5).get(random.nextInt(3))));
                parts.put(Look.Part.CARVED, palette.parts().get(Look.Part.CARVED).get(0));
                parts.put(Look.Part.SHUTTER, keep(palette, Look.Part.SHUTTER, random));
                parts.put(Look.Part.CHIMNEY, keep(palette, Look.Part.CHIMNEY, random));
                walls = random.nextInt(3) == 0 ? palette.walls().get(4) : "";
            }
            case MERCHANT -> {
                // Bright plaster and awnings, no bare clay.
                parts.put(Look.Part.SHUTTER, keep(palette, Look.Part.SHUTTER, random));
            }
            case CRAFT -> {
                // Plain: no carvings.
                parts.put(Look.Part.CARVED, "");
            }
            case POOR -> {
                // Clay plaster, wooden roofs, rough footings; often no shutters and no chimney.
                parts.put(Look.Part.PLASTER, pick(random, palette.clay()));
                parts.put(Look.Part.ROOF, palette.parts().get(Look.Part.ROOF).get(1 + random.nextInt(2)));
                parts.put(Look.Part.CARVED, "");
                parts.put(Look.Part.SHUTTER, random.nextInt(2) == 0 ? Look.REMOVED : pick(random, palette.parts().get(Look.Part.SHUTTER)));
                parts.put(Look.Part.CHIMNEY, random.nextInt(2) == 0 ? Look.REMOVED : pick(random, palette.parts().get(Look.Part.CHIMNEY)));
                walls = random.nextInt(2) == 0 ? palette.walls().get(2) : "";
            }
            default -> {
            }
        }
        String stone = walls.isEmpty() ? styleStone : walls;
        return new Materials(houseWood, stone, Look.of(parts));
    }

    /**
     * Every look a style may give, for checks: the public one and a sample of houses of every quarter.
     */
    public static List<Materials> samples(NpcStyle style, String wood, int perQuarter) {
        List<Materials> list = new java.util.ArrayList<>();
        list.add(of(style, wood, "red", Quarter.PUBLIC, 0));
        for (Quarter quarter : Quarter.values()) {
            if (quarter != Quarter.PUBLIC) {
                for (int seed = 0; seed < perQuarter; seed++) {
                    list.add(of(style, wood, "red", quarter, seed * 7919L + 13));
                }
            }
        }
        return list;
    }

    private static String pick(SplittableRandom random, List<String> choices) {
        return choices.get(random.nextInt(choices.size()));
    }

    /**
     * A choice of a part that is never left out.
     */
    private static String keep(Palette palette, Look.Part part, SplittableRandom random) {
        List<String> choices = palette.parts().get(part).stream().filter(value -> !Look.REMOVED.equals(value)).toList();
        return pick(random, choices);
    }
}
