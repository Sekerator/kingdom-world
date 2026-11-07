package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Choosing an item in a tab of the throne screen: a search over every item of the game and of the mods, by the name in
 * the player's language or by the id, with icons. Only the items a list allows are offered (for example those that come
 * from abroad). The server checks the choice again.
 */
final class ItemPicker {
    static final int ROW = 18;
    private static final int MAX_MATCHES = 512;
    private static final String KEY = "kingdomworld.screen.picker.";

    private record Entry(ItemStack stack, String name, String id) {
    }

    private final ThroneScreen screen;
    private List<Entry> all;
    private Set<String> allowed;
    private Consumer<ItemStack> onPick = stack -> {
    };
    private String search = "";
    private List<Entry> matches = List.of();
    private int scroll;
    private boolean open;
    private int chooseWidth;
    private int backWidth;

    ItemPicker(ThroneScreen screen) {
        this.screen = screen;
    }

    boolean isOpen() {
        return open;
    }

    /**
     * Opens the search.
     *
     * @param allowed the item ids that may be chosen, or {@code null} for every item
     */
    void open(Set<String> allowed, Consumer<ItemStack> onPick) {
        this.allowed = allowed;
        this.onPick = onPick;
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
    void showForCheck(String text, Set<String> allowedIds) {
        allowed = allowedIds;
        open = true;
        search = text;
        refresh();
    }

    private List<Entry> all() {
        if (all == null) {
            List<Entry> entries = new ArrayList<>();
            for (Item item : ForgeRegistries.ITEMS) {
                if (item == Items.AIR || item == ModRegistries.COIN.get()) {
                    continue;
                }
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                ItemStack stack = new ItemStack(item);
                entries.add(new Entry(stack, stack.getHoverName().getString().toLowerCase(Locale.ROOT), id == null ? "" : id.toString()));
            }
            all = entries;
        }
        return all;
    }

    private void refresh() {
        String wanted = search.strip().toLowerCase(Locale.ROOT);
        List<Entry> found = new ArrayList<>();
        for (Entry entry : all()) {
            if (found.size() >= MAX_MATCHES) {
                break;
            }
            if ((allowed == null || allowed.contains(entry.id())) && (wanted.isEmpty() || entry.name().contains(wanted) || entry.id().contains(wanted))) {
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
                onPick.accept(entry.stack().copy());
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
            graphics.renderItem(entry.stack(), x, y);
            screen.name(graphics, entry.stack().getHoverName().getString(), x + 20, y + 4, 0xFFFFFFFF, nameRight - x - 20);
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
