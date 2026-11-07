package io.github.sekerator.kingdomworld.compat.maps;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.locating.IModFile;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Texts of the web maps in the language set by {@code webMapLanguage}. A dedicated server has no language of its
 * own, so the texts are read from the mod's language files in its jar; keys missing there fall back to English, then
 * to the key itself.
 */
public final class MapTexts {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern ARGUMENT = Pattern.compile("%(?:(\\d+)\\$)?([sd%])");
    private static final String FALLBACK = "en_us";

    private final String language;
    private final Map<String, String> texts;
    private final Map<String, String> fallback;

    private MapTexts(String language, Map<String, String> texts, Map<String, String> fallback) {
        this.language = language;
        this.texts = texts;
        this.fallback = fallback;
    }

    public static MapTexts load(String language) {
        Map<String, String> fallback = read(FALLBACK);
        Map<String, String> texts = FALLBACK.equals(language) ? fallback : read(language);
        if (texts.isEmpty() && !FALLBACK.equals(language)) {
            LOGGER.warn("KingdomWorld web maps: no language file for '{}', using {}", language, FALLBACK);
        }
        return new MapTexts(language, texts, fallback);
    }

    public String language() {
        return language;
    }

    private static Map<String, String> read(String language) {
        Map<String, String> result = new HashMap<>();
        try {
            IModFile file = ModList.get().getModFileById(KingdomWorld.MOD_ID).getFile();
            Path path = file.findResource("assets", KingdomWorld.MOD_ID, "lang", language + ".json");
            if (!Files.exists(path)) {
                return result;
            }
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        result.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("KingdomWorld web maps: could not read the language file '{}': {}", language, e.toString());
        }
        return result;
    }

    public String text(String key, Object... args) {
        String pattern = texts.getOrDefault(key, fallback.getOrDefault(key, key));
        return format(pattern, args);
    }

    /**
     * A chat component as plain text in this language.
     */
    public String resolve(Component component) {
        StringBuilder text = new StringBuilder();
        if (component.getContents() instanceof TranslatableContents translatable) {
            Object[] args = translatable.getArgs();
            Object[] resolved = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                resolved[i] = args[i] instanceof Component part ? resolve(part) : String.valueOf(args[i]);
            }
            text.append(text(translatable.getKey(), resolved));
        } else if (component.getContents() instanceof LiteralContents literal) {
            text.append(literal.text());
        }
        for (Component sibling : component.getSiblings()) {
            text.append(resolve(sibling));
        }
        return text.toString();
    }

    /**
     * Fills {@code %s}, {@code %d}, {@code %1$s} and {@code %%} the way the game does.
     */
    public static String format(String pattern, Object... args) {
        Matcher matcher = ARGUMENT.matcher(pattern);
        StringBuilder result = new StringBuilder();
        int next = 0;
        while (matcher.find()) {
            String replacement;
            if ("%".equals(matcher.group(2))) {
                replacement = "%";
            } else {
                int index = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) - 1 : next++;
                replacement = index >= 0 && index < args.length ? String.valueOf(args[index]) : "";
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Text made safe for HTML.
     */
    public static String html(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            switch (c) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                case '\n' -> out.append("<br>");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
