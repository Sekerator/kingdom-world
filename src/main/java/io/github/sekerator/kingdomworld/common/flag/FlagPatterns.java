package io.github.sekerator.kingdomworld.common.flag;

import io.github.sekerator.kingdomworld.core.kingdom.KingdomColors;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomFlag;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BannerPattern;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The patterns a flag may carry, on both sides: every banner pattern the game knows except the field itself, in the order the
 * editor shows them. First the patterns a loom draws without an item, in the order of the loom; then the six that need a
 * banner pattern item, free on a flag; then those of other mods by their ids. The order does not depend on the tags of the
 * patterns having reached the client.
 */
public final class FlagPatterns {
    /** The patterns of the loom that need no item, in its order. */
    public static final List<String> WITHOUT_ITEM = ids("square_bottom_left", "square_bottom_right", "square_top_left",
            "square_top_right", "stripe_bottom", "stripe_top", "stripe_left", "stripe_right", "stripe_center", "stripe_middle",
            "stripe_downright", "stripe_downleft", "small_stripes", "cross", "straight_cross", "triangle_bottom", "triangle_top",
            "triangles_bottom", "triangles_top", "diagonal_left", "diagonal_up_right", "diagonal_up_left", "diagonal_right", "circle",
            "rhombus", "half_vertical", "half_horizontal", "half_vertical_right", "half_horizontal_bottom", "border", "curly_border",
            "gradient", "gradient_up", "bricks");
    /** The patterns a loom draws only with a banner pattern item. */
    public static final List<String> WITH_ITEM = ids("globe", "creeper", "skull", "flower", "mojang", "piglin");

    private static List<String> cached;

    private FlagPatterns() {
    }

    private static List<String> ids(String... paths) {
        List<String> ids = new ArrayList<>(paths.length);
        for (String path : paths) {
            ids.add("minecraft:" + path);
        }
        return List.copyOf(ids);
    }

    /**
     * Whether a flag may carry a pattern: the game knows it and it is not the field.
     */
    public static boolean valid(String id) {
        return KingdomFlag.validPattern(id) && key(id).map(BuiltInRegistries.BANNER_PATTERN::containsKey).orElse(false);
    }

    /**
     * Every pattern a flag may carry, in the order of the editor.
     */
    public static List<String> all() {
        if (cached == null || cached.isEmpty()) {
            Set<String> ordered = new LinkedHashSet<>();
            for (String id : WITHOUT_ITEM) {
                if (valid(id)) {
                    ordered.add(id);
                }
            }
            for (String id : WITH_ITEM) {
                if (valid(id)) {
                    ordered.add(id);
                }
            }
            List<String> others = new ArrayList<>();
            for (ResourceLocation location : BuiltInRegistries.BANNER_PATTERN.keySet()) {
                String id = location.toString();
                if (!ordered.contains(id) && valid(id)) {
                    others.add(id);
                }
            }
            others.sort(String::compareTo);
            ordered.addAll(others);
            cached = List.copyOf(ordered);
        }
        return cached;
    }

    /**
     * The pattern of the game for an id, or {@code null} for one it does not know.
     */
    @Nullable
    public static Holder<BannerPattern> holder(String id) {
        Optional<ResourceLocation> key = key(id);
        if (key.isEmpty()) {
            return null;
        }
        return BuiltInRegistries.BANNER_PATTERN.getHolder(ResourceKey.create(Registries.BANNER_PATTERN, key.get())).orElse(null);
    }

    /**
     * The field of a banner, the pattern every banner starts with.
     */
    public static Holder<BannerPattern> base() {
        return holder(KingdomFlag.BASE_PATTERN);
    }

    /**
     * The name the game gives a layer of a banner, for example "White Cross".
     */
    public static Component name(String id, int dye) {
        Optional<ResourceLocation> key = key(id);
        String shortKey = key.map(ResourceLocation::toShortLanguageKey).orElse(id);
        return Component.translatable("block.minecraft.banner." + shortKey + "." + KingdomColors.dyeName(dye));
    }

    /**
     * The name the game gives a dye, for example "Light Blue".
     */
    public static Component colorName(int dye) {
        return Component.translatable("color.minecraft." + KingdomColors.dyeName(dye));
    }

    private static Optional<ResourceLocation> key(String id) {
        return Optional.ofNullable(id == null ? null : ResourceLocation.tryParse(id));
    }
}
