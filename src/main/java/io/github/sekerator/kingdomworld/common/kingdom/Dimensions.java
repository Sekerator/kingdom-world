package io.github.sekerator.kingdomworld.common.kingdom;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dimension ids as used by the domain core. The strings are cached because protection looks them up on every
 * block change.
 */
public final class Dimensions {
    private static final Map<ResourceKey<Level>, String> IDS = new ConcurrentHashMap<>();

    private Dimensions() {
    }

    public static String id(Level level) {
        return id(level.dimension());
    }

    public static String id(ResourceKey<Level> key) {
        return IDS.computeIfAbsent(key, k -> k.location().toString());
    }

    public static Optional<ResourceKey<Level>> key(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? Optional.empty() : Optional.of(ResourceKey.create(Registries.DIMENSION, location));
    }
}
