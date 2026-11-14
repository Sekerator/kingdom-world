package io.github.sekerator.kingdomworld.common.guide;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.guide.GuideFormatException;
import io.github.sekerator.kingdomworld.core.guide.GuideParser;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The numbers of the configuration of the server the guide shows ({@code {cfg:territory.baseMaxChunks}}): the values the server
 * runs with, and the defaults where no server is at hand. Only plain values are shown, never lists of ids, and a server tells a
 * client only the values the pages of its own guide show, so that no other key of its configuration ever leaves it.
 */
public final class GuideNumbers {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The longest path a client may ask about. */
    public static final int MAX_PATH = 128;
    /** The most paths a client may ask about at once. */
    public static final int MAX_PATHS = 512;
    /** The longest value sent to a client. */
    public static final int MAX_VALUE = 256;

    /** The paths the pages of the guide in the jar of the mod show, read once. */
    private static volatile Set<String> shown;

    private GuideNumbers() {
    }

    /**
     * The value of the configuration at {@code path}, when it is a plain value (a number, a word, a table of numbers written in
     * one line).
     */
    public static Optional<ForgeConfigSpec.ConfigValue<?>> value(String path) {
        if (path == null || path.isEmpty() || path.length() > MAX_PATH) {
            return Optional.empty();
        }
        Object found;
        try {
            found = KingdomWorldConfig.SERVER_SPEC.getValues().get(path);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        if (found instanceof ForgeConfigSpec.ConfigValue<?> value && !(value.getDefault() instanceof Collection<?>)) {
            return Optional.of(value);
        }
        return Optional.empty();
    }

    /**
     * The default value at {@code path} written as text, or empty for a path that holds no plain value.
     */
    public static Optional<String> defaultValue(String path) {
        return value(path).map(value -> io.github.sekerator.kingdomworld.core.Limits.effectiveText(path, text(value.getDefault())));
    }

    /**
     * The value the running server has at {@code path}, or the default where the configuration of the server is not loaded. A
     * limit is the one the server runs with: the old default of a configuration written before counts as none
     * ({@link io.github.sekerator.kingdomworld.core.Limits#effective}).
     */
    public static Optional<String> current(String path) {
        return value(path).map(value -> {
            try {
                return text(value.get());
            } catch (IllegalStateException e) {
                return text(value.getDefault());
            }
        }).map(text -> {
            if (io.github.sekerator.kingdomworld.core.Limits.CITY_DISTANCE.equals(path)) {
                // The distance the server keeps, from its walls as well as from the number written.
                try {
                    return Integer.toString(io.github.sekerator.kingdomworld.common.road.RoadSettings.rules().minCityDistance());
                } catch (RuntimeException e) {
                    return io.github.sekerator.kingdomworld.core.Limits.effectiveText(path, text);
                }
            }
            return io.github.sekerator.kingdomworld.core.Limits.effectiveText(path, text);
        });
    }

    /**
     * The values of the running server for the paths asked about, leaving out paths that hold no plain value and those the
     * guide does not show.
     *
     * @param allowed the paths a client may learn, those of {@link #shownByTheGuide()}
     */
    public static Map<String, String> current(Collection<String> paths, Set<String> allowed) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String path : paths) {
            if (allowed.contains(path)) {
                current(path).filter(text -> text.length() <= MAX_VALUE).ifPresent(text -> values.put(path, text));
            }
        }
        return values;
    }

    /**
     * The paths of the configuration the pages of the guide in the jar of the mod show, in any language.
     */
    public static Set<String> shownByTheGuide() {
        Set<String> paths = shown;
        if (paths == null) {
            paths = Set.of();
            try {
                IModFileInfo file = ModList.get().getModFileById(KingdomWorld.MOD_ID);
                paths = shownBy(file.getFile().findResource("assets", KingdomWorld.MOD_ID, "guide"));
            } catch (RuntimeException e) {
                LOGGER.warn("The pages of the guide could not be read; the guide gets the default numbers only", e);
            }
            shown = paths;
        }
        return paths;
    }

    /**
     * The paths of the configuration the pages in a folder of the guide show: one folder per language, one file per section.
     */
    public static Set<String> shownBy(Path guide) {
        Set<String> paths = new HashSet<>();
        if (!Files.isDirectory(guide)) {
            return Set.of();
        }
        try (Stream<Path> languages = Files.list(guide)) {
            for (Path language : languages.filter(Files::isDirectory).toList()) {
                Map<String, String> files = new TreeMap<>();
                try (Stream<Path> list = Files.list(language)) {
                    for (Path file : list.filter(path -> path.getFileName().toString().endsWith(".md")).toList()) {
                        files.put(file.getFileName().toString(), Files.readString(file, StandardCharsets.UTF_8));
                    }
                }
                try {
                    paths.addAll(GuideParser.book(files).numberPaths());
                } catch (GuideFormatException e) {
                    LOGGER.error("The guide in {} has a mistake: {}", language.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.warn("The pages of the guide could not be read from {}", guide, e);
        }
        return Set.copyOf(paths);
    }

    /**
     * Every plain value of the configuration of the server with its default, by path, in the order of the configuration.
     */
    public static Map<String, String> defaults() {
        Map<String, String> values = new LinkedHashMap<>();
        collect(KingdomWorldConfig.SERVER_SPEC.getValues(), "", values);
        return values;
    }

    private static void collect(UnmodifiableConfig config, String prefix, Map<String, String> values) {
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
            String path = prefix + entry.getKey();
            Object child = entry.getValue();
            if (child instanceof UnmodifiableConfig inner) {
                collect(inner, path + ".", values);
            } else if (child instanceof ForgeConfigSpec.ConfigValue<?> value && !(value.getDefault() instanceof Collection<?>)) {
                values.put(path, io.github.sekerator.kingdomworld.core.Limits.effectiveText(path, text(value.getDefault())));
            }
        }
    }

    private static String text(Object value) {
        if (value instanceof List<?> list) {
            return String.join(", ", list.stream().map(String::valueOf).toList());
        }
        return String.valueOf(value);
    }
}
