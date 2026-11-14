package io.github.sekerator.kingdomworld.client.guide;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.core.guide.GuideBook;
import io.github.sekerator.kingdomworld.core.guide.GuideFormatException;
import io.github.sekerator.kingdomworld.core.guide.GuideParser;
import io.github.sekerator.kingdomworld.core.guide.GuideSection;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The texts of the guide on the client, read from {@code assets/kingdomworld/guide/<language>/} of the resources, so that a
 * resource pack may change or translate them. The guide is shown in the language of the game, in English where that language
 * has none. A file with a mistake is left out and named in the log; the rest of the guide still opens.
 */
public final class GuideLibrary {
    /** The language the guide is shown in when the language of the game has no guide. */
    public static final String FALLBACK = "en_us";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FOLDER = "guide/";
    private static final Map<String, GuideBook> BOOKS = new HashMap<>();

    /**
     * The guide in a language.
     */
    public record Loaded(String language, GuideBook book) {
        /**
         * Whether the language writes decimals with a comma.
         */
        public boolean decimalComma() {
            return !language.startsWith("en_");
        }
    }

    private GuideLibrary() {
    }

    /**
     * The guide in the language of the game, or in English.
     */
    public static Loaded current() {
        Minecraft minecraft = Minecraft.getInstance();
        String language = minecraft.getLanguageManager().getSelected();
        GuideBook book = book(minecraft.getResourceManager(), language);
        if (book.isEmpty() && !language.equals(FALLBACK)) {
            return new Loaded(FALLBACK, book(minecraft.getResourceManager(), FALLBACK));
        }
        return new Loaded(language, book);
    }

    /**
     * Forgets the guides read, after the resources were loaded again.
     */
    public static void clear() {
        BOOKS.clear();
    }

    private static GuideBook book(ResourceManager manager, String language) {
        return BOOKS.computeIfAbsent(language, key -> read(manager, key));
    }

    private static GuideBook read(ResourceManager manager, String language) {
        Map<String, String> files = new TreeMap<>();
        String folder = FOLDER + language;
        Map<ResourceLocation, Resource> found = manager.listResources(folder, location -> location.getPath().endsWith(".md"));
        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            ResourceLocation location = entry.getKey();
            if (!location.getNamespace().equals(KingdomWorld.MOD_ID)) {
                continue;
            }
            String name = location.getPath().substring(folder.length() + 1);
            if (name.contains("/")) {
                continue;
            }
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                files.put(name, reader.lines().collect(Collectors.joining("\n")));
            } catch (IOException e) {
                LOGGER.error("Could not read the page file {} of the guide", location, e);
            }
        }
        return parse(language, files);
    }

    /**
     * Reads the files of one language, leaving out those with a mistake.
     */
    static GuideBook parse(String language, Map<String, String> files) {
        try {
            return GuideParser.book(files);
        } catch (GuideFormatException e) {
            LOGGER.error("The guide in {} has a mistake: {}", language, e.getMessage());
        }
        List<GuideSection> sections = new ArrayList<>();
        for (Map.Entry<String, String> file : files.entrySet()) {
            String id = GuideParser.sectionId(file.getKey());
            if (id == null) {
                continue;
            }
            try {
                sections.add(GuideParser.section(id, file.getValue()));
            } catch (GuideFormatException e) {
                LOGGER.error("The section {} of the guide in {} is left out: {}", file.getKey(), language, e.getMessage());
            }
        }
        return new GuideBook(sections);
    }
}
