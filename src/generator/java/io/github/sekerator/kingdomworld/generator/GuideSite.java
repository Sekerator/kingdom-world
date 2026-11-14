package io.github.sekerator.kingdomworld.generator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sekerator.kingdomworld.common.guide.GuideNumbers;
import io.github.sekerator.kingdomworld.core.guide.GuideBlock;
import io.github.sekerator.kingdomworld.core.guide.GuideBook;
import io.github.sekerator.kingdomworld.core.guide.GuideInline;
import io.github.sekerator.kingdomworld.core.guide.GuideNumbersFormat;
import io.github.sekerator.kingdomworld.core.guide.GuidePage;
import io.github.sekerator.kingdomworld.core.guide.GuideParser;
import io.github.sekerator.kingdomworld.core.guide.GuideRecipe;
import io.github.sekerator.kingdomworld.core.guide.GuideSection;
import io.github.sekerator.kingdomworld.core.guide.GuideText;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Writes the guide as one HTML page that opens in any browser: the same sections, pages, tables and crafting grids as in the
 * game, in Russian and English, with the tree of pages, the search (whatever the case and the "ё", the words found
 * highlighted) and the icons of the items. Numbers of the configuration show their defaults. Run by
 * {@code gradlew guideSite}; the page is written to {@code build/guide/guide.html} and never committed.
 *
 * <p>Arguments: the folder of the resources of the mod, the file to write, and optionally the folder of the assets of the game
 * (with {@code indexes/} and {@code objects/}) for the Russian names of the items of the game.
 */
public final class GuideSite {
    private static final List<String> LANGUAGES = List.of("ru_ru", "en_us");
    private static final Pattern NUMERIC = Pattern.compile("[\\d\\s.,%+\\-x×/:]+");
    private static final int MAX_MODEL_DEPTH = 6;

    private final Path resources;
    private final Path gameAssets;
    private final Map<String, String> defaults = GuideNumbers.defaults();
    private final Map<String, JsonObject> names = new TreeMap<>();
    /** The CSS class of the icon of every item used, by its id; empty where no texture was found. */
    private final Map<String, String> icons = new LinkedHashMap<>();
    private final StringBuilder iconStyles = new StringBuilder();

    private GuideSite(Path resources, Path gameAssets) {
        this.resources = resources;
        this.gameAssets = gameAssets;
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            throw new IllegalArgumentException("Arguments: <resources of the mod> <file to write> [assets of the game]");
        }
        Path out = Path.of(args[1]);
        GuideSite site = new GuideSite(Path.of(args[0]), args.length > 2 && !args[2].isEmpty() ? Path.of(args[2]) : null);
        String html = site.html();
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, html, StandardCharsets.UTF_8);
        System.out.println("The guide was written to " + out.toAbsolutePath() + " (" + html.length() / 1024 + " KB)");
    }

    // ------------------------------------------------------------------ the page

    private String html() throws IOException {
        Map<String, GuideBook> books = new LinkedHashMap<>();
        for (String language : LANGUAGES) {
            books.put(language, GuideParser.book(files(language)));
        }
        StringBuilder body = new StringBuilder();
        StringBuilder index = new StringBuilder("{");
        for (String language : LANGUAGES) {
            GuideBook book = books.get(language);
            String lang = language.substring(0, 2);
            body.append("<div class=\"book\" data-lang=\"").append(lang).append("\" hidden>\n<nav class=\"tree\">\n");
            for (GuideSection section : book.sections()) {
                body.append("<details open><summary>").append(icon(section.icon())).append(escape(section.title())).append("</summary>\n");
                for (GuidePage page : section.pages()) {
                    body.append("<a href=\"#").append(page.id()).append("\" data-page=\"").append(page.id()).append("\">")
                            .append(escape(page.title())).append("</a>\n");
                }
                body.append("</details>\n");
            }
            body.append("</nav>\n<main>\n<section class=\"results\" hidden></section>\n");
            for (GuideSection section : book.sections()) {
                for (GuidePage page : section.pages()) {
                    body.append(page(page, section, language));
                }
            }
            body.append("</main>\n</div>\n");
            index.append(index.length() > 1 ? "," : "").append('"').append(lang).append("\":").append(searchIndex(book, language));
        }
        index.append('}');
        String template = resource("guide-site.html");
        return template.replace("/*ICONS*/", iconStyles.toString())
                .replace("<!--BOOKS-->", body.toString())
                .replace("/*INDEX*/{}", index.toString());
    }

    private String page(GuidePage page, GuideSection section, String language) {
        StringBuilder html = new StringBuilder();
        html.append("<article id=\"").append(language, 0, 2).append('-').append(page.id()).append("\" data-page=\"").append(page.id())
                .append("\" hidden>\n<p class=\"crumb\">").append(escape(section.title())).append("</p>\n<h1>");
        if (!page.icon().isEmpty()) {
            html.append(icon(page.icon(), "big"));
        }
        html.append(escape(page.title())).append("</h1>\n");
        for (GuideBlock block : page.blocks()) {
            if (block instanceof GuideBlock.Heading heading) {
                html.append("<h2>").append(inline(heading.text(), language)).append("</h2>\n");
            } else if (block instanceof GuideBlock.Paragraph paragraph) {
                html.append("<p>").append(inline(paragraph.text(), language)).append("</p>\n");
            } else if (block instanceof GuideBlock.Bullets bullets) {
                String tag = bullets.numbered() ? "ol" : "ul";
                html.append('<').append(tag).append('>');
                for (List<GuideInline> item : bullets.items()) {
                    html.append("<li>").append(inline(item, language)).append("</li>");
                }
                html.append("</").append(tag).append(">\n");
            } else if (block instanceof GuideBlock.Note note) {
                html.append("<aside>").append(inline(note.text(), language)).append("</aside>\n");
            } else if (block instanceof GuideBlock.Table table) {
                html.append("<div class=\"table\"><table>");
                for (int r = 0; r < table.rows().size(); r++) {
                    boolean head = table.header() && r == 0;
                    html.append("<tr>");
                    List<List<GuideInline>> row = table.rows().get(r);
                    for (int c = 0; c < table.columns(); c++) {
                        List<GuideInline> cell = c < row.size() ? row.get(c) : List.of();
                        String text = inline(cell, language);
                        String plain = GuideText.plain(cell, resolver(language));
                        boolean number = !head && c > 0 && !plain.isBlank() && NUMERIC.matcher(plain.strip()).matches();
                        html.append(head ? "<th>" : number ? "<td class=\"num\">" : "<td>").append(text).append(head ? "</th>" : "</td>");
                    }
                    html.append("</tr>");
                }
                html.append("</table></div>\n");
            } else if (block instanceof GuideBlock.Recipes recipes) {
                html.append("<div class=\"grids\">");
                for (String id : recipes.ids()) {
                    html.append(grid(id, language));
                }
                html.append("</div>\n");
            }
        }
        return html.append("</article>\n").toString();
    }

    private String grid(String id, String language) {
        Optional<GuideRecipe> recipe = data(GuideRecipe.dataPath("recipes", id))
                .flatMap(json -> GuideRecipe.fromJson(json, tag -> GuideRecipe.tagItems(tag, t -> data(GuideRecipe.dataPath("tags/items", t)))));
        if (recipe.isEmpty()) {
            return "<p class=\"missing\">" + escape(id) + "</p>";
        }
        StringBuilder html = new StringBuilder("<figure class=\"grid\"><figcaption>").append(escape(itemName(recipe.get().result(), language)))
                .append("</figcaption><div class=\"cells\">");
        for (List<String> slot : recipe.get().slots()) {
            html.append("<span class=\"slot\"");
            if (!slot.isEmpty()) {
                StringBuilder title = new StringBuilder();
                for (String item : slot) {
                    title.append(title.isEmpty() ? "" : " / ").append(itemName(item, language));
                }
                html.append(" title=\"").append(escape(title.toString())).append("\">").append(icon(slot.get(0), "big"));
            } else {
                html.append('>');
            }
            html.append("</span>");
        }
        html.append("</div><span class=\"arrow\">&#10132;</span><span class=\"slot result\" title=\"")
                .append(escape(itemName(recipe.get().result(), language))).append("\">").append(icon(recipe.get().result(), "big"));
        if (recipe.get().count() > 1) {
            html.append("<b>").append(recipe.get().count()).append("</b>");
        }
        return html.append("</span></figure>").toString();
    }

    private String inline(List<GuideInline> text, String language) {
        StringBuilder html = new StringBuilder();
        for (GuideInline inline : text) {
            if (inline instanceof GuideInline.Text words) {
                html.append(words.bold() ? "<strong>" + escape(words.text()) + "</strong>" : escape(words.text()));
            } else if (inline instanceof GuideInline.Link link) {
                html.append("<a href=\"#").append(link.target()).append("\">").append(escape(link.text())).append("</a>");
            } else if (inline instanceof GuideInline.Item item) {
                html.append("<span title=\"").append(escape(itemName(item.id(), language))).append("\">").append(icon(item.id()))
                        .append("</span>");
            } else if (inline instanceof GuideInline.Number number) {
                html.append("<span class=\"cfg\" title=\"").append(escape(number.path())).append("\">").append(escape(number(number, language)))
                        .append("</span>");
            }
        }
        return html.toString();
    }

    private String number(GuideInline.Number number, String language) {
        return GuideNumbersFormat.format(defaults.get(number.path()), number, language.equals("ru_ru"));
    }

    /**
     * The plain text of the pages: an icon stands beside the name of its item, so it adds nothing.
     */
    private GuideText.Resolver resolver(String language) {
        return new GuideText.Resolver(id -> "", number -> number(number, language));
    }

    /**
     * The search index of a book: for every page its id, title, section and plain text.
     */
    private String searchIndex(GuideBook book, String language) {
        StringBuilder json = new StringBuilder("[");
        GuideText.Resolver resolver = resolver(language);
        for (GuideSection section : book.sections()) {
            for (GuidePage page : section.pages()) {
                JsonArray entry = new JsonArray();
                entry.add(page.id());
                entry.add(page.title());
                entry.add(section.title());
                entry.add(GuideText.plain(page, resolver));
                json.append(json.length() > 1 ? "," : "").append(entry);
            }
        }
        // The text of a page may hold "</script>": it must not end the script it stands in.
        return json.append(']').toString().replace("</", "<\\/");
    }

    // ------------------------------------------------------------------ items

    private String itemName(String id, String language) {
        String[] parts = id.split(":", 2);
        if (parts.length < 2) {
            return id;
        }
        JsonObject lang = names.computeIfAbsent(parts[0] + "/" + language, key -> lang(parts[0], language));
        for (String kind : List.of("item.", "block.")) {
            JsonElement name = lang.get(kind + parts[0] + "." + parts[1].replace('/', '.'));
            if (name != null) {
                return name.getAsString();
            }
        }
        return id;
    }

    private JsonObject lang(String namespace, String language) {
        if (namespace.equals("kingdomworld")) {
            return own("assets/kingdomworld/lang/" + language + ".json").orElseGet(JsonObject::new);
        }
        if (namespace.equals("minecraft") && language.equals("en_us")) {
            return classpath("assets/minecraft/lang/en_us.json").orElseGet(JsonObject::new);
        }
        if (namespace.equals("minecraft") && gameAssets != null) {
            return gameAsset("minecraft/lang/" + language + ".json").orElseGet(JsonObject::new);
        }
        return new JsonObject();
    }

    private String icon(String id) {
        return icon(id, "");
    }

    /**
     * An icon of an item: a span drawn by a CSS class that holds the first frame of its texture.
     */
    private String icon(String id, String size) {
        if (id.isEmpty()) {
            return "";
        }
        String css = icons.computeIfAbsent(id, key -> {
            Optional<byte[]> png = texture(key);
            if (png.isEmpty()) {
                return "";
            }
            String name = "i" + icons.size();
            iconStyles.append('.').append(name).append("{background-image:url(data:image/png;base64,")
                    .append(Base64.getEncoder().encodeToString(png.get())).append(")}\n");
            return name;
        });
        return "<span class=\"icon " + size + " " + css + "\"></span>";
    }

    /**
     * The texture that stands for an item: the first layer of its model, or a face of the block it places.
     */
    private Optional<byte[]> texture(String id) {
        String[] parts = id.split(":", 2);
        if (parts.length < 2) {
            return Optional.empty();
        }
        Map<String, String> textures = new LinkedHashMap<>();
        String model = parts[0] + ":item/" + parts[1];
        for (int depth = 0; depth < MAX_MODEL_DEPTH && model != null; depth++) {
            String[] location = model.contains(":") ? model.split(":", 2) : new String[]{"minecraft", model};
            Optional<JsonObject> json = asset(location[0], "models/" + location[1] + ".json");
            if (json.isEmpty()) {
                break;
            }
            if (json.get().has("textures")) {
                for (Map.Entry<String, JsonElement> texture : json.get().getAsJsonObject("textures").entrySet()) {
                    textures.putIfAbsent(texture.getKey(), texture.getValue().getAsString());
                }
            }
            model = json.get().has("parent") ? json.get().get("parent").getAsString() : null;
        }
        String chosen = null;
        for (String key : List.of("layer0", "front", "top", "side", "all", "texture", "particle")) {
            chosen = resolve(textures, textures.get(key));
            if (chosen != null) {
                break;
            }
        }
        if (chosen == null) {
            chosen = textures.values().stream().map(value -> resolve(textures, value)).filter(value -> value != null).findFirst()
                    .orElse(parts[0] + ":item/" + parts[1]);
        }
        String[] location = chosen.contains(":") ? chosen.split(":", 2) : new String[]{"minecraft", chosen};
        return bytes(location[0], "textures/" + location[1] + ".png");
    }

    private static String resolve(Map<String, String> textures, String value) {
        for (int i = 0; i < 8 && value != null && value.startsWith("#"); i++) {
            value = textures.get(value.substring(1));
        }
        return value;
    }

    // ------------------------------------------------------------------ files

    private Map<String, String> files(String language) throws IOException {
        Map<String, String> files = new TreeMap<>();
        try (Stream<Path> list = Files.list(resources.resolve("assets/kingdomworld/guide").resolve(language))) {
            for (Path file : list.filter(path -> path.toString().endsWith(".md")).toList()) {
                files.put(file.getFileName().toString(), Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    private Optional<JsonObject> data(String path) {
        return own(path).or(() -> classpath(path));
    }

    private Optional<JsonObject> asset(String namespace, String path) {
        String full = "assets/" + namespace + "/" + path;
        return own(full).or(() -> classpath(full));
    }

    private Optional<byte[]> bytes(String namespace, String path) {
        String full = "assets/" + namespace + "/" + path;
        Path own = resources.resolve(full);
        try {
            if (Files.isRegularFile(own)) {
                return Optional.of(Files.readAllBytes(own));
            }
            try (InputStream stream = GuideSite.class.getClassLoader().getResourceAsStream(full)) {
                return stream == null ? Optional.empty() : Optional.of(stream.readAllBytes());
            }
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Optional<JsonObject> own(String path) {
        Path file = resources.resolve(path);
        try {
            return Files.isRegularFile(file) ? Optional.of(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject())
                    : Optional.empty();
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Optional<JsonObject> classpath(String path) {
        try (InputStream stream = GuideSite.class.getClassLoader().getResourceAsStream(path)) {
            return stream == null ? Optional.empty()
                    : Optional.of(JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * A file of the assets of the game downloaded by the build, found through the newest index of the assets.
     */
    private Optional<JsonObject> gameAsset(String name) {
        try (Stream<Path> indexes = Files.list(gameAssets.resolve("indexes"))) {
            for (Path index : indexes.sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString())).toList()) {
                JsonObject objects = JsonParser.parseString(Files.readString(index, StandardCharsets.UTF_8)).getAsJsonObject()
                        .getAsJsonObject("objects");
                if (objects != null && objects.has(name)) {
                    String hash = objects.getAsJsonObject(name).get("hash").getAsString();
                    Path file = gameAssets.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
                    return Optional.of(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
                }
            }
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static String resource(String name) throws IOException {
        try (InputStream stream = GuideSite.class.getResourceAsStream(name)) {
            if (stream == null) {
                throw new IOException("The template " + name + " is missing");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
