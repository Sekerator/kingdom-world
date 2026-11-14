package io.github.sekerator.kingdomworld.architecture;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sekerator.kingdomworld.common.block.WorkPostLook;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The models of the work post: a variant of the block state for every look, every model there, every child of the parent
 * with its icon, the textures of the mod in the mod and those of the game from a short list (they follow the resource pack of
 * the player and the mod carries no picture of the game), and the item that still looks as it always did.
 */
class WorkPostModelsTest {
    private static final String PARENT = "kingdomworld:block/work_post_profession";
    /** Textures of the game the models may use: items with a flat picture that reads on a small board. */
    private static final Set<String> GAME_TEXTURES = Set.of("minecraft:item/iron_hoe", "minecraft:item/iron_axe",
            "minecraft:item/iron_pickaxe", "minecraft:item/fishing_rod", "minecraft:item/lead", "minecraft:item/shears",
            "minecraft:item/bundle_filled", "minecraft:item/iron_ingot", "minecraft:item/bread",
            "minecraft:block/crafting_table_front", "minecraft:item/iron_sword", "minecraft:item/iron_chestplate",
            "minecraft:item/emerald", "minecraft:item/writable_book", "minecraft:item/amethyst_shard", "minecraft:item/honey_bottle",
            "minecraft:item/paper", "minecraft:block/spruce_planks");

    private static Path assets() {
        return SourceTree.mainSourceRoot().getParent().resolve("resources").resolve("assets");
    }

    private static JsonObject read(Path file) throws IOException {
        assertTrue(Files.isRegularFile(file), file + " is missing");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static Path model(String id) {
        String[] parts = id.split(":", 2);
        return assets().resolve(parts[0]).resolve("models").resolve(parts[1] + ".json");
    }

    @Test
    void everyLookHasExactlyOneVariant() throws IOException {
        JsonObject variants = read(assets().resolve("kingdomworld/blockstates/work_post.json")).getAsJsonObject("variants");
        Set<String> expected = Arrays.stream(WorkPostLook.values()).map(look -> "profession=" + look.getSerializedName())
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(expected, new TreeSet<>(variants.keySet()));
        assertEquals("kingdomworld:block/work_post", variants.getAsJsonObject("profession=none").get("model").getAsString(),
                "a post without a profession looks as it always did");
        for (WorkPostLook look : WorkPostLook.values()) {
            if (look != WorkPostLook.NONE) {
                assertEquals("kingdomworld:block/work_post_" + look.getSerializedName(),
                        variants.getAsJsonObject("profession=" + look.getSerializedName()).get("model").getAsString());
            }
        }
    }

    @Test
    void everyModelIsThereAndEveryChildHasAnIcon() throws IOException {
        JsonObject parent = read(model(PARENT));
        assertEquals("minecraft:cutout_mipped", parent.get("render_type").getAsString(), "the icons need a cut out layer");
        assertTrue(!parent.getAsJsonObject("textures").has("icon"), "every child names its own icon");
        // The cube, then a board and an icon with the same corners on each of the four sides.
        assertEquals(9, parent.getAsJsonArray("elements").size());
        for (WorkPostLook look : WorkPostLook.values()) {
            if (look == WorkPostLook.NONE) {
                continue;
            }
            JsonObject child = read(model("kingdomworld:block/work_post_" + look.getSerializedName()));
            assertEquals(PARENT, child.get("parent").getAsString(), look.getSerializedName());
            JsonElement icon = child.getAsJsonObject("textures").get("icon");
            assertTrue(icon != null && !icon.getAsString().isEmpty(), look.getSerializedName() + " has no icon");
        }
    }

    @Test
    void texturesOfTheModAreThereAndThoseOfTheGameAreFromTheList() throws IOException {
        Set<String> textures = new TreeSet<>();
        for (Map.Entry<String, JsonElement> texture : read(model(PARENT)).getAsJsonObject("textures").entrySet()) {
            textures.add(texture.getValue().getAsString());
        }
        for (WorkPostLook look : WorkPostLook.values()) {
            if (look != WorkPostLook.NONE) {
                textures.add(read(model("kingdomworld:block/work_post_" + look.getSerializedName())).getAsJsonObject("textures")
                        .get("icon").getAsString());
            }
        }
        for (String texture : textures) {
            String[] parts = texture.split(":", 2);
            if (parts[0].equals("kingdomworld")) {
                Path file = assets().resolve("kingdomworld/textures").resolve(parts[1] + ".png");
                assertTrue(Files.isRegularFile(file), file + " is missing");
                byte[] head = Arrays.copyOf(Files.readAllBytes(file), 8);
                assertArrayEquals(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}, head, file + " is no PNG");
            } else {
                assertEquals("minecraft", parts[0], texture);
                assertTrue(GAME_TEXTURES.contains(texture), texture + " is not among the textures of the game a post may use");
            }
        }
        assertTrue(textures.contains("kingdomworld:block/work_post_board") && textures.contains("kingdomworld:block/work_post_coin"));
    }

    @Test
    void theItemLooksAsItAlwaysDid() throws IOException {
        assertEquals("kingdomworld:block/work_post", read(model("kingdomworld:item/work_post")).get("parent").getAsString());
        JsonObject post = read(model("kingdomworld:block/work_post"));
        assertEquals("minecraft:block/cube_bottom_top", post.get("parent").getAsString());
        assertEquals("kingdomworld:block/work_post_side", post.getAsJsonObject("textures").get("side").getAsString());
    }
}
