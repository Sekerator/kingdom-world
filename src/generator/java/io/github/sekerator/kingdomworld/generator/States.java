package io.github.sekerator.kingdomworld.generator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Block states as text ({@code minecraft:oak_stairs[facing=north,half=bottom]}): their parts, what kind of block they are
 * as far as joining neighbours goes, and how a state turns and mirrors with the building it belongs to.
 */
final class States {
    /** Blocks that are no full cube although their names do not say so. */
    private static final Set<String> NOT_FULL = Set.of(
            "minecraft:air", "minecraft:water", "minecraft:lava", "minecraft:torch", "minecraft:soul_torch", "minecraft:lantern",
            "minecraft:soul_lantern", "minecraft:chain", "minecraft:ladder", "minecraft:iron_bars", "minecraft:bell",
            "minecraft:chest", "minecraft:trapped_chest", "minecraft:ender_chest", "minecraft:cauldron", "minecraft:water_cauldron",
            "minecraft:composter", "minecraft:hopper", "minecraft:lectern", "minecraft:anvil", "minecraft:grindstone",
            "minecraft:stonecutter", "minecraft:enchanting_table", "minecraft:brewing_stand", "minecraft:campfire",
            "minecraft:soul_campfire", "minecraft:end_rod", "minecraft:lightning_rod", "minecraft:scaffolding", "minecraft:cobweb",
            "minecraft:vine", "minecraft:snow", "minecraft:flower_pot", "minecraft:dirt_path", "minecraft:farmland",
            "minecraft:carved_pumpkin", "minecraft:jack_o_lantern", "minecraft:pumpkin", "minecraft:melon", "minecraft:lever",
            "minecraft:tripwire_hook", "minecraft:amethyst_cluster", "minecraft:pointed_dripstone", "minecraft:grass",
            "minecraft:tall_grass", "minecraft:fern", "minecraft:large_fern", "minecraft:dead_bush", "minecraft:sweet_berry_bush",
            "minecraft:sugar_cane", "minecraft:bamboo", "minecraft:lily_pad", "minecraft:moss_carpet", "minecraft:pink_petals",
            "minecraft:azalea", "minecraft:flowering_azalea", "minecraft:wheat", "minecraft:carrots", "minecraft:potatoes",
            "minecraft:beetroots", "minecraft:poppy", "minecraft:dandelion", "minecraft:blue_orchid", "minecraft:allium",
            "minecraft:azure_bluet", "minecraft:red_tulip", "minecraft:orange_tulip", "minecraft:white_tulip",
            "minecraft:pink_tulip", "minecraft:oxeye_daisy", "minecraft:cornflower", "minecraft:lily_of_the_valley",
            "minecraft:rose_bush", "minecraft:peony", "minecraft:lilac", "minecraft:sunflower", "minecraft:torchflower",
            "minecraft:red_mushroom", "minecraft:brown_mushroom", "minecraft:skeleton_skull", "minecraft:candle");
    private static final String[] NOT_FULL_ENDINGS = {
            "_stairs", "_slab", "_wall", "_fence", "_fence_gate", "_pane", "_door", "_trapdoor", "_torch", "_carpet", "_bed",
            "_banner", "_button", "_pressure_plate", "_sign", "_leaves", "_sapling", "_candle", "_head", "_skull", "_rail",
            "_cluster", "_bud"};

    private States() {
    }

    static String id(String state) {
        int open = state.indexOf('[');
        return open < 0 ? state : state.substring(0, open);
    }

    static Map<String, String> properties(String state) {
        Map<String, String> properties = new LinkedHashMap<>();
        int open = state.indexOf('[');
        if (open >= 0) {
            for (String pair : state.substring(open + 1, state.length() - 1).split(",")) {
                String[] parts = pair.split("=");
                properties.put(parts[0], parts[1]);
            }
        }
        return properties;
    }

    static String of(String id, Map<String, String> properties) {
        if (properties.isEmpty()) {
            return id;
        }
        StringBuilder text = new StringBuilder(id).append('[');
        boolean first = true;
        for (Map.Entry<String, String> property : properties.entrySet()) {
            if (!first) {
                text.append(',');
            }
            text.append(property.getKey()).append('=').append(property.getValue());
            first = false;
        }
        return text.append(']').toString();
    }

    /**
     * A state with some of its properties set: name, value, name, value...
     */
    static String with(String state, String... pairs) {
        Map<String, String> properties = properties(state);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            properties.put(pairs[i], pairs[i + 1]);
        }
        return of(id(state), properties);
    }

    static boolean isFence(String id) {
        return id.endsWith("_fence");
    }

    static boolean isPane(String id) {
        return id.endsWith("glass_pane") || id.equals("minecraft:iron_bars");
    }

    static boolean isWall(String id) {
        return id.endsWith("_wall");
    }

    /**
     * Whether a state fills its whole cube, so that fences, panes and walls beside it join it.
     */
    static boolean isFull(String state) {
        String id = id(state);
        if (NOT_FULL.contains(id) || !id.startsWith("minecraft:")) {
            return false;
        }
        if (id.endsWith("_slab")) {
            return state.contains("type=double");
        }
        for (String ending : NOT_FULL_ENDINGS) {
            if (id.endsWith(ending)) {
                return false;
            }
        }
        return !id.contains("potted_");
    }

    // ---------------------------------------------------------------- turning and mirroring

    private static String turnedSide(String side) {
        return switch (side) {
            case "north" -> "east";
            case "east" -> "south";
            case "south" -> "west";
            case "west" -> "north";
            default -> side;
        };
    }

    private static String mirroredSide(String side) {
        return switch (side) {
            case "east" -> "west";
            case "west" -> "east";
            default -> side;
        };
    }

    /**
     * A state after a clockwise quarter turn of its building.
     */
    static String turned(String state) {
        Map<String, String> from = properties(state);
        if (from.isEmpty()) {
            return state;
        }
        Map<String, String> to = new LinkedHashMap<>();
        for (Map.Entry<String, String> property : from.entrySet()) {
            String name = property.getKey();
            String value = property.getValue();
            switch (name) {
                case "facing" -> to.put(name, turnedSide(value));
                case "axis" -> to.put(name, value.equals("x") ? "z" : value.equals("z") ? "x" : value);
                case "north", "east", "south", "west" -> to.put(turnedSide(name), value);
                case "rotation" -> to.put(name, String.valueOf((Integer.parseInt(value) + 4) % 16));
                default -> to.put(name, value);
            }
        }
        return of(id(state), to);
    }

    /**
     * A state after its building is mirrored along its front.
     */
    static String mirrored(String state) {
        Map<String, String> from = properties(state);
        if (from.isEmpty()) {
            return state;
        }
        Map<String, String> to = new LinkedHashMap<>();
        for (Map.Entry<String, String> property : from.entrySet()) {
            String name = property.getKey();
            String value = property.getValue();
            switch (name) {
                case "facing" -> to.put(name, mirroredSide(value));
                case "north", "east", "south", "west" -> to.put(mirroredSide(name), value);
                case "hinge" -> to.put(name, value.equals("left") ? "right" : "left");
                case "shape" -> to.put(name, value.endsWith("_left") ? value.replace("_left", "_right")
                        : value.endsWith("_right") ? value.replace("_right", "_left") : value);
                case "rotation" -> to.put(name, String.valueOf((16 - Integer.parseInt(value)) % 16));
                default -> to.put(name, value);
            }
        }
        return of(id(state), to);
    }
}
