package io.github.sekerator.kingdomworld.core.npc;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The look of a kingdom of the world: the stone and the wood all its buildings and its wall are built of, so that two
 * kingdoms do not look alike. The style of a place is chosen once, when the place is planned, by the climate of its land and
 * its number in the plan, and kept with the place.
 */
public enum NpcStyle {
    /** Light stone bricks on a cobblestone footing with the wood that grows at the place. */
    STONE("", "", "minecraft:stone_bricks"),
    /** Red brick with spruce. */
    BRICK("minecraft:spruce", "minecraft:bricks", "minecraft:bricks"),
    /** Sandstone with acacia. */
    SANDSTONE("minecraft:acacia", "minecraft:sandstone", "minecraft:sandstone"),
    /** Deepslate bricks with dark oak. */
    DEEPSLATE("minecraft:dark_oak", "minecraft:deepslate_bricks", "minecraft:deepslate_bricks"),
    /** Mud bricks with jungle wood. */
    MUD("minecraft:jungle", "minecraft:mud_bricks", "minecraft:mud_bricks");

    /**
     * What the land of a place is like, as far as the look of a kingdom goes.
     */
    public enum Climate {
        TEMPERATE,
        COLD,
        DRY,
        LUSH;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final String wood;
    private final String stone;
    private final String wall;

    NpcStyle(String wood, String stone, String wall) {
        this.wood = wood;
        this.stone = stone;
        this.wall = wall;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The wood species of the style, or empty for the wood that grows at the place.
     */
    public String wood() {
        return wood;
    }

    /**
     * The stone every stone of a template is replaced by, or empty for the stones the templates are drawn with.
     */
    public String stone() {
        return stone;
    }

    /**
     * The block the stone wall of the capital is built of.
     */
    public String wallBlock() {
        return wall;
    }

    public static Optional<NpcStyle> byId(String id) {
        for (NpcStyle style : values()) {
            if (style.id().equals(id)) {
                return Optional.of(style);
            }
        }
        return Optional.empty();
    }

    /**
     * The styles that suit a climate, the most usual first.
     */
    public static List<NpcStyle> of(Climate climate) {
        return switch (climate) {
            case COLD -> List.of(BRICK, DEEPSLATE, STONE);
            case DRY -> List.of(SANDSTONE, MUD, STONE);
            case LUSH -> List.of(MUD, STONE, SANDSTONE);
            case TEMPERATE -> List.of(STONE, BRICK, DEEPSLATE, SANDSTONE);
        };
    }

    /**
     * The style of a place: one that suits its climate, the next one for every place planned so far, so that neighbours in the
     * plan differ; the seed decides where the turn starts.
     *
     * @param ordinal how many places the plan had before this one
     */
    public static NpcStyle pick(Climate climate, long seed, int ordinal) {
        List<NpcStyle> suited = of(climate);
        int start = (int) Math.floorMod(seed ^ 0x5354594CL, (long) suited.size());
        return suited.get(Math.floorMod(start + ordinal, suited.size()));
    }

    /**
     * The climate a biome stands for, by its id and tags.
     */
    public static Climate climate(String biome, Set<String> tags) {
        String path = biome.contains(":") ? biome.substring(biome.indexOf(':') + 1) : biome;
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.contains("desert") || lower.contains("badlands") || lower.contains("savanna") || lower.contains("mesa")
                || tags.contains("minecraft:is_badlands") || tags.contains("minecraft:is_savanna") || tags.contains("forge:is_desert")
                || tags.contains("forge:is_dry/overworld")) {
            return Climate.DRY;
        }
        if (lower.contains("jungle") || lower.contains("mangrove") || lower.contains("bamboo") || lower.contains("swamp")
                || tags.contains("minecraft:is_jungle")) {
            return Climate.LUSH;
        }
        if (lower.contains("snowy") || lower.contains("frozen") || lower.contains("taiga") || lower.contains("grove") || lower.contains("ice")
                || tags.contains("minecraft:is_taiga") || tags.contains("forge:is_snowy") || tags.contains("forge:is_cold/overworld")) {
            return Climate.COLD;
        }
        return Climate.TEMPERATE;
    }
}
