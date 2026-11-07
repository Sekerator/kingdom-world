package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.guild.GuildTexts;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.guild.Contract;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * Choosing the target of a contract at the desk: a monster (every hostile monster of the game and of the mods, with its
 * spawn egg as the icon) or an item to gather, by the name in the player's language or by the id. The server checks the
 * choice again.
 */
final class GuildPicker {
    static final int ROW = 18;
    private static final int MAX_MATCHES = 512;
    private static final String KEY = "kingdomworld.screen.picker.";

    /**
     * Something to choose: its icon, its name and its id.
     */
    record Entry(ItemStack icon, Component name, String search, String id) {
    }

    private final GuildDeskScreen screen;
    private List<Entry> monsters;
    private List<Entry> items;
    private boolean monstersShown;
    private BiConsumer<String, Entry> onPick = (id, entry) -> {
    };
    private String search = "";
    private List<Entry> matches = List.of();
    private int scroll;
    private boolean open;
    private int chooseWidth;
    private int backWidth;

    GuildPicker(GuildDeskScreen screen) {
        this.screen = screen;
    }

    boolean isOpen() {
        return open;
    }

    /**
     * Opens the search over monsters or over items.
     */
    void open(boolean monsters, BiConsumer<String, Entry> pick) {
        this.monstersShown = monsters;
        this.onPick = pick;
        this.open = true;
        this.scroll = 0;
        refresh();
    }

    void close() {
        open = false;
    }

    /**
     * Opens the search with a text, for the layout check.
     */
    void showForCheck(boolean monsters, String text) {
        monstersShown = monsters;
        open = true;
        search = text;
        refresh();
    }

    /**
     * Every hostile monster of the game and of the mods.
     */
    static List<Entry> monsterEntries() {
        List<Entry> entries = new ArrayList<>();
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
            if (type.getCategory() != MobCategory.MONSTER) {
                continue;
            }
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (id == null) {
                continue;
            }
            Component name = type.getDescription();
            entries.add(new Entry(GuildTexts.icon(Contract.Kind.HUNT, id.toString(), Rank.F), name,
                    name.getString().toLowerCase(Locale.ROOT), id.toString()));
        }
        return entries;
    }

    private List<Entry> entries() {
        if (monstersShown) {
            if (monsters == null) {
                monsters = monsterEntries();
            }
            return monsters;
        }
        if (items == null) {
            List<Entry> entries = new ArrayList<>();
            for (Item item : ForgeRegistries.ITEMS) {
                if (item == Items.AIR || item == ModRegistries.COIN.get()) {
                    continue;
                }
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                ItemStack stack = new ItemStack(item);
                entries.add(new Entry(stack, stack.getHoverName(), stack.getHoverName().getString().toLowerCase(Locale.ROOT),
                        id == null ? "" : id.toString()));
            }
            items = entries;
        }
        return items;
    }

    private void refresh() {
        String wanted = search.strip().toLowerCase(Locale.ROOT);
        List<Entry> found = new ArrayList<>();
        for (Entry entry : entries()) {
            if (found.size() >= MAX_MATCHES) {
                break;
            }
            if (wanted.isEmpty() || entry.search().contains(wanted) || entry.id().contains(wanted)) {
                found.add(entry);
            }
        }
        matches = found;
        scroll = Math.max(0, Math.min(scroll, matches.size() - 1));
    }

    int measure(Font font) {
        chooseWidth = screen.buttonWidth(Component.translatable(KEY + "choose"));
        backWidth = screen.buttonWidth(Component.translatable(KEY + "back"));
        int text = 0;
        for (String key : List.of("hint", "none")) {
            text = Math.max(text, Math.min(300, font.width(Component.translatable(KEY + key))));
        }
        return Math.max(text, 120 + 2 + chooseWidth + 2 + backWidth);
    }

    private int visibleRows(int listTop, int bottom) {
        return Math.max(1, (bottom - listTop - 11) / ROW);
    }

    void init(int x, int right, int top, int bottom) {
        EditBox box = new EditBox(screen.font(), x + 1, top + 1, right - x - backWidth - 4, ROW - 4, Component.translatable(KEY + "search"));
        box.setMaxLength(64);
        box.setValue(search);
        box.setHint(Component.translatable(KEY + "hint"));
        box.setResponder(value -> {
            if (!value.equals(search)) {
                search = value;
                scroll = 0;
                refresh();
                screen.rebuild();
            }
        });
        screen.add(box);
        screen.focus(box);
        screen.add(Button.builder(Component.translatable(KEY + "back"), b -> {
            close();
            screen.rebuild();
        }).bounds(right - backWidth, top, backWidth, ROW - 2).build());
        int listTop = top + ROW + 2;
        int rows = visibleRows(listTop, bottom);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, matches.size() - rows)));
        for (int i = 0; i < rows && scroll + i < matches.size(); i++) {
            Entry entry = matches.get(scroll + i);
            screen.add(Button.builder(Component.translatable(KEY + "choose"), b -> {
                close();
                onPick.accept(entry.id(), entry);
                screen.rebuild();
            }).bounds(right - chooseWidth, listTop + i * ROW, chooseWidth, ROW - 2).build());
        }
    }

    void render(GuiGraphics graphics, Font font, int x, int right, int top, int bottom) {
        int listTop = top + ROW + 2;
        int rows = visibleRows(listTop, bottom);
        if (matches.isEmpty()) {
            screen.label(graphics, Component.translatable(KEY + "none"), x, listTop + 5, 0xFFB8B8B8, right);
            return;
        }
        int nameRight = right - chooseWidth - 4;
        for (int i = 0; i < rows && scroll + i < matches.size(); i++) {
            Entry entry = matches.get(scroll + i);
            int y = listTop + i * ROW;
            graphics.renderItem(entry.icon(), x, y);
            screen.name(graphics, entry.name().getString(), x + 20, y + 4, 0xFFFFFFFF, nameRight - x - 20);
        }
        if (matches.size() > rows) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(matches.size(), scroll + rows),
                    matches.size());
            screen.label(graphics, page, right - font.width(page), listTop + rows * ROW + 1, 0xFF9A9A9A, right);
        }
    }

    boolean scroll(int top, int bottom, double delta) {
        int rows = visibleRows(top + ROW + 2, bottom);
        int max = Math.max(0, matches.size() - rows);
        int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta) * Math.max(1, rows / 2)));
        if (next == scroll) {
            return false;
        }
        scroll = next;
        return true;
    }
}
