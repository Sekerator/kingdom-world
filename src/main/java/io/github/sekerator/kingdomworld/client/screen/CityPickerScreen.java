package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.network.data.ConstructionView;
import io.github.sekerator.kingdomworld.common.road.RoadTexts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongConsumer;

/**
 * The choice of a city among many: a kingdom has no limit of cities, so where cycling through them one by one would take long,
 * a list of them with a search by name, the capital first. The first row stands for the choice without a city (by hand, or
 * all cities). Choosing goes back to the screen the list was opened from.
 */
public final class CityPickerScreen extends KingdomScreen {
    private static final int ROW = 18;
    private static final String KEY = "kingdomworld.screen.city_picker.";

    private final Screen parent;
    private final List<ConstructionView.Place> cities;
    private final Component none;
    private final LongConsumer chosen;
    private final long noneId;
    private String search = "";
    private int scroll;
    private EditBox box;

    /**
     * @param none   the label of the choice without a city
     * @param noneId the id passed for it
     */
    public CityPickerScreen(Screen parent, List<ConstructionView.Place> cities, Component none, long noneId, LongConsumer chosen) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
        this.cities = List.copyOf(cities);
        this.none = none;
        this.noneId = noneId;
        this.chosen = chosen;
    }

    @Override
    public String checkName() {
        return "city-picker";
    }

    /**
     * Shows a search, for the layout check.
     */
    public void searchForCheck(String text) {
        search = text;
    }

    private List<ConstructionView.Place> matches() {
        String wanted = search.strip().toLowerCase(Locale.ROOT);
        List<ConstructionView.Place> found = new ArrayList<>();
        for (ConstructionView.Place place : cities) {
            if (wanted.isEmpty() || place.name().toLowerCase(Locale.ROOT).contains(wanted)) {
                found.add(place);
            }
        }
        return found;
    }

    private int listTop() {
        return 46;
    }

    private int rows() {
        return Math.max(1, (height - listTop() - 30) / ROW);
    }

    private int listWidth() {
        return Math.min(320, width - 2 * PAD);
    }

    @Override
    protected void init() {
        int left = (width - listWidth()) / 2;
        box = new EditBox(font, left, 24, listWidth(), 16, Component.translatable(KEY + "search"));
        box.setHint(Component.translatable(KEY + "search").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        box.setMaxLength(io.github.sekerator.kingdomworld.core.road.City.MAX_NAME);
        box.setValue(search);
        box.setResponder(value -> {
            if (!value.equals(search)) {
                search = value;
                scroll = 0;
                rebuildWidgets();
                setFocused(box);
                box.setFocused(true);
            }
        });
        addRenderableWidget(box);
        List<ConstructionView.Place> found = matches();
        int total = found.size() + 1;
        int rows = rows();
        scroll = Math.max(0, Math.min(scroll, total - rows));
        for (int i = 0; i < rows && scroll + i < total; i++) {
            int index = scroll + i - 1;
            int y = listTop() + i * ROW;
            if (index < 0) {
                addRenderableWidget(Button.builder(none, b -> choose(noneId)).bounds(left, y, listWidth(), ROW - 2).build());
                continue;
            }
            ConstructionView.Place place = found.get(index);
            Component level = RoadTexts.level(place.level());
            Component full = Component.translatable(KEY + "row", place.name(), level, place.builders());
            String shown = fit(full.getString(), listWidth() - 8);
            Button button = addRenderableWidget(Button.builder(Component.literal(shown), b -> choose(place.id()))
                    .bounds(left, y, listWidth(), ROW - 2).build());
            button.setTooltip(Tooltip.create(Component.translatable(KEY + "row.hint", place.name(), level, place.builders(), place.helpers(),
                    place.x(), place.z())));
        }
        Component back = Component.translatable("gui.back");
        int backWidth = Math.max(60, buttonWidth(back));
        addRenderableWidget(Button.builder(back, b -> onClose()).bounds((width - backWidth) / 2, height - 24, backWidth, 18).build());
    }

    private void choose(long id) {
        chosen.accept(id);
        onClose();
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int total = matches().size() + 1;
        int next = Math.max(0, Math.min(Math.max(0, total - rows()), scroll - (int) Math.signum(delta)));
        if (next != scroll) {
            scroll = next;
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        centeredLabel(graphics, title, width / 2, 8, 0xFFF0D070, PAD, width - PAD);
        int total = matches().size() + 1;
        int rows = rows();
        if (total > rows) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(total, scroll + rows), total);
            int left = (width - listWidth()) / 2;
            label(graphics, page, left + listWidth() - font.width(page), listTop() + rows * ROW, 0xFF9A9A9A, width - PAD);
        }
    }
}
