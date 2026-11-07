package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.network.data.StorageView;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The "Storehouses" tab of the throne screen: everything the kingdom's storehouses hold, with a search by name and
 * the count of each kind, and the list of storehouses with how full they are. Only for looking; the storehouses
 * themselves are opened in the world.
 */
final class StorageTab {
    static final int ROW = 18;
    private static final int LINE = 11;
    private static final int GAP = 2;
    private static final int SEARCH_WIDTH = 120;
    private static final String KEY = "kingdomworld.screen.storage.";

    private final ThroneScreen screen;
    private StorageView view;
    private boolean showStorehouses;
    private String search = "";
    private int scroll;
    private int toggleWidth;
    private int countWidth;

    StorageTab(ThroneScreen screen) {
        this.screen = screen;
    }

    StorageView view() {
        return view;
    }

    void setView(StorageView view) {
        this.view = view;
    }

    void showStorehouses(boolean value) {
        this.showStorehouses = value;
        this.scroll = 0;
    }

    void setSearch(String value) {
        this.search = value == null ? "" : value;
        this.scroll = 0;
    }

    String checkName() {
        return showStorehouses ? "storehouses" : "storage";
    }

    // ---------------------------------------------------------------- texts

    private Component toggleLabel(boolean storehouses) {
        return Component.translatable(storehouses ? KEY + "show_items" : KEY + "show_storehouses");
    }

    private Component summary() {
        return view == null ? Component.empty()
                : Component.translatable(KEY + "summary", view.kinds(), view.total(), view.storehouses().size());
    }

    private static String name(ItemStack stack) {
        return stack.getHoverName().getString();
    }

    private List<StorageView.Entry> rows() {
        List<StorageView.Entry> rows = new ArrayList<>();
        if (view == null) {
            return rows;
        }
        String wanted = search.trim().toLowerCase(Locale.ROOT);
        for (StorageView.Entry entry : view.items()) {
            if (wanted.isEmpty() || name(entry.sample()).toLowerCase(Locale.ROOT).contains(wanted)
                    || String.valueOf(ForgeRegistries.ITEMS.getKey(entry.sample().getItem())).contains(wanted)) {
                rows.add(entry);
            }
        }
        return rows;
    }

    private int rowCount() {
        if (view == null) {
            return 0;
        }
        return showStorehouses ? view.storehouses().size() : rows().size();
    }

    // ---------------------------------------------------------------- layout

    int measure(Font font) {
        toggleWidth = Math.max(screen.buttonWidth(toggleLabel(true)), screen.buttonWidth(toggleLabel(false)));
        countWidth = font.width(Component.translatable(KEY + "count", 999_999_999L));
        int top = SEARCH_WIDTH + GAP + toggleWidth + 8 + font.width(Component.translatable("kingdomworld.screen.throne.page", 500, 500, 500));
        int list = 18 + 100 + 8 + countWidth;
        int storehouses = font.width(Component.translatable(KEY + "storehouse", -30_000_000, 319, 29_999_999, 54, 54));
        int summary = font.width(Component.translatable(KEY + "summary", 9_999, 999_999_999L, 999));
        int messages = Math.max(font.width(Component.translatable(KEY + "loading")), font.width(Component.translatable(KEY + "none_match")));
        messages = Math.max(messages, font.width(Component.translatable(KEY + "no_storehouses")));
        return Math.max(Math.max(top, list), Math.max(Math.max(storehouses, summary), messages));
    }

    private int listTop(int top) {
        return top + ThroneScreen.CONTENT_TOP + ROW + LINE + 4;
    }

    private int visibleRows(int top, int panelHeight) {
        return Math.max(1, (top + panelHeight - ThroneScreen.BOTTOM_AREA - listTop(top)) / ROW);
    }

    void init(int left, int top, int panelWidth, int panelHeight) {
        if (view == null) {
            return;
        }
        int y = top + ThroneScreen.CONTENT_TOP;
        int x = left + KingdomScreen.PAD;
        if (!showStorehouses) {
            EditBox box = new EditBox(screen.font(), x + 1, y + 1, SEARCH_WIDTH - 2, ROW - 4, Component.translatable(KEY + "search"));
            box.setMaxLength(64);
            box.setValue(search);
            box.setHint(Component.translatable(KEY + "search"));
            box.setResponder(value -> {
                if (!value.equals(search)) {
                    setSearch(value);
                }
            });
            screen.add(box);
        }
        screen.add(Button.builder(toggleLabel(showStorehouses), b -> {
            showStorehouses(!showStorehouses);
            screen.rebuild();
        }).bounds(x + SEARCH_WIDTH + GAP, y, toggleWidth, ROW - 2).build());
        int rows = visibleRows(top, panelHeight);
        scroll = Math.max(0, Math.min(scroll, rowCount() - rows));
    }

    boolean scroll(int top, int panelHeight, double delta) {
        int max = Math.max(0, rowCount() - visibleRows(top, panelHeight));
        int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        if (next == scroll) {
            return false;
        }
        scroll = next;
        return true;
    }

    // ---------------------------------------------------------------- drawing

    void render(GuiGraphics graphics, Font font, int left, int top, int panelWidth, int panelHeight) {
        int x = left + KingdomScreen.PAD;
        int right = left + panelWidth - KingdomScreen.PAD;
        if (view == null) {
            screen.label(graphics, Component.translatable(KEY + "loading"), x, top + ThroneScreen.CONTENT_TOP + 4, 0xFFB8B8B8, right);
            return;
        }
        screen.label(graphics, summary(), x, top + ThroneScreen.CONTENT_TOP + ROW + 1, 0xFFE8E8E8, right);
        int rows = visibleRows(top, panelHeight);
        int count = rowCount();
        if (count > rows) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(count, scroll + rows), count);
            screen.label(graphics, page, right - font.width(page), top + ThroneScreen.CONTENT_TOP + 4, 0xFF9A9A9A, right);
        }
        int listTop = listTop(top);
        if (showStorehouses) {
            List<StorageView.Storehouse> storehouses = view.storehouses();
            if (storehouses.isEmpty()) {
                screen.paragraph(graphics, Component.translatable(KEY + "no_storehouses"), x, listTop + 5, right - x, 3, 0xFFB8B8B8);
            }
            for (int i = 0; i < rows && scroll + i < storehouses.size(); i++) {
                StorageView.Storehouse storehouse = storehouses.get(scroll + i);
                Component line = Component.translatable(KEY + "storehouse", storehouse.pos().getX(), storehouse.pos().getY(),
                        storehouse.pos().getZ(), storehouse.used(), storehouse.slots());
                screen.name(graphics, line.getString(), x, listTop + i * ROW + 5,
                        storehouse.used() >= storehouse.slots() ? 0xFFFF9070 : 0xFFD0D0D0, right - x);
            }
            return;
        }
        List<StorageView.Entry> entries = rows();
        if (entries.isEmpty()) {
            screen.paragraph(graphics, Component.translatable(view.items().isEmpty() ? KEY + "empty" : KEY + "none_match"),
                    x, listTop + 5, right - x, 3, 0xFFB8B8B8);
            return;
        }
        for (int i = 0; i < rows && scroll + i < entries.size(); i++) {
            StorageView.Entry entry = entries.get(scroll + i);
            int y = listTop + i * ROW;
            graphics.renderItem(entry.sample(), x, y);
            Component amount = Component.translatable(KEY + "count", entry.count());
            int amountX = right - font.width(amount);
            screen.label(graphics, amount, amountX, y + 5, 0xFFFFFFFF, right);
            screen.name(graphics, name(entry.sample()), x + 20, y + 5, 0xFFE0E0E0, amountX - 6 - (x + 20));
        }
    }
}
