package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.CitizenView;
import io.github.sekerator.kingdomworld.common.network.packet.CitizenActionPacket;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.Profession;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The "Citizens" tab of the throne screen: the numbers of the population, the list of citizens with their
 * professions (which members with the right can change), a filter, and the chronicle of deaths. Widths come from
 * the translated texts like everywhere in the throne screen.
 */
final class CitizensTab {
    static final int ROW = 18;
    private static final int LINE = 11;
    private static final int GAP = 2;
    /** Space for a name; the longest generated names fit. */
    private static final int NAME_COLUMN = 86;
    private static final int MIN_PLACE_COLUMN = 64;
    private static final String KEY = "kingdomworld.screen.citizens.";

    private final ThroneScreen screen;
    private CitizenView view;
    private int filter;
    private boolean chronicle;
    private int scroll;

    private int professionWidth;
    private int stateWidth;
    private int filterWidth;
    private int toggleWidth;
    private int pageWidth;
    private int dayWidth;

    CitizensTab(ThroneScreen screen) {
        this.screen = screen;
    }

    CitizenView view() {
        return view;
    }

    void setView(CitizenView view) {
        this.view = view;
    }

    boolean isChronicle() {
        return chronicle;
    }

    void setChronicle(boolean chronicle) {
        this.chronicle = chronicle;
        this.scroll = 0;
    }

    void setFilter(int filter) {
        this.filter = Math.floorMod(filter, filterCount());
        this.scroll = 0;
    }

    // ---------------------------------------------------------------- texts

    private static List<Profession> professions() {
        return Professions.REGISTRY.all();
    }

    private int filterCount() {
        return professions().size() + 3;
    }

    private Component filterOption(int index) {
        List<Profession> professions = professions();
        if (index == 0) {
            return Component.translatable(KEY + "filter.all");
        }
        if (index <= professions.size()) {
            return CitizenTexts.profession(professions.get(index - 1).id());
        }
        return Component.translatable(index == professions.size() + 1 ? KEY + "filter.wounded" : KEY + "filter.homeless");
    }

    private boolean matches(CitizenView.Entry entry) {
        List<Profession> professions = professions();
        if (filter == 0) {
            return true;
        }
        if (filter <= professions.size()) {
            return entry.profession().equals(professions.get(filter - 1).id());
        }
        return filter == professions.size() + 1 ? entry.state() == CitizenState.WOUNDED : entry.home() == null;
    }

    private Component filterLabel(int index) {
        return Component.translatable(KEY + "filter", filterOption(index));
    }

    private Component toggleLabel(boolean showingChronicle) {
        return Component.translatable(showingChronicle ? KEY + "show_list" : KEY + "show_chronicle");
    }

    private List<Component> summaryLines() {
        List<Component> lines = new ArrayList<>();
        if (view == null) {
            return lines;
        }
        lines.add(Component.translatable(KEY + "summary", view.population(), view.bedsUsed(), view.bedsTotal()));
        lines.add(view.foodDays() == CitizenView.UNLIMITED_FOOD
                ? Component.translatable(KEY + "supplies_unlimited", view.storehouses(), view.mood())
                : Component.translatable(KEY + "supplies", view.foodDays(), view.storehouses(), view.mood()));
        return lines;
    }

    /**
     * Why newcomers do or do not arrive, shown in the status line while there is no other message.
     */
    Component hint() {
        if (view == null) {
            return Component.empty();
        }
        return Component.translatable(KEY + "growth." + view.growth().name().toLowerCase(Locale.ROOT));
    }

    private static Component state(CitizenState state, boolean female) {
        return CitizenTexts.state(state, female ? Gender.FEMALE : Gender.MALE);
    }

    private static Gender gender(boolean female) {
        return female ? Gender.FEMALE : Gender.MALE;
    }

    private static String name(int seed, boolean female) {
        return CitizenTexts.name(seed, female ? Gender.FEMALE : Gender.MALE).getString();
    }

    // ---------------------------------------------------------------- layout

    /**
     * Inner width this tab needs.
     */
    int measure(Font font) {
        professionWidth = 0;
        stateWidth = 0;
        for (Gender gender : Gender.values()) {
            for (Profession profession : professions()) {
                professionWidth = Math.max(professionWidth, screen.buttonWidth(CitizenTexts.profession(profession.id(), gender)));
            }
            professionWidth = Math.max(professionWidth, screen.buttonWidth(CitizenTexts.profession("unknown_profession", gender)));
            for (CitizenState state : CitizenState.values()) {
                stateWidth = Math.max(stateWidth, font.width(CitizenTexts.state(state, gender)));
            }
        }
        filterWidth = 0;
        for (int i = 0; i < filterCount(); i++) {
            filterWidth = Math.max(filterWidth, screen.buttonWidth(filterLabel(i)));
        }
        toggleWidth = Math.max(screen.buttonWidth(toggleLabel(true)), screen.buttonWidth(toggleLabel(false)));
        pageWidth = font.width(Component.translatable("kingdomworld.screen.throne.page", 500, 500, 500));
        dayWidth = font.width(Component.translatable(KEY + "day", 99_999));

        int buttons = filterWidth + GAP + toggleWidth + 8 + pageWidth;
        int list = NAME_COLUMN + professionWidth + 6 + stateWidth + 6 + MIN_PLACE_COLUMN;
        int deaths = NAME_COLUMN + 120 + 6 + dayWidth;
        int summary = 0;
        CitizenView saved = view;
        // The longest numbers a summary can show.
        view = new CitizenView(new java.util.UUID(0, 0), false, 10_000, 10_000, 10_000, CitizenView.MAX_FOOD_DAYS, 100, 999,
                io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds.GrowthCheck.READY, List.of(), List.of());
        for (Component line : summaryLines()) {
            summary = Math.max(summary, font.width(line));
        }
        view = saved;
        int messages = Math.max(font.width(Component.translatable(KEY + "loading")),
                font.width(Component.translatable(KEY + "chronicle_empty")));
        messages = Math.max(messages, font.width(Component.translatable(KEY + "none_match")));
        return Math.max(Math.max(buttons, list), Math.max(Math.max(deaths, summary), messages));
    }

    private int listTop(int top) {
        return top + ThroneScreen.CONTENT_TOP + 2 * LINE + 2 + ROW + 4;
    }

    private int visibleRows(int top, int panelHeight) {
        return Math.max(1, (top + panelHeight - ThroneScreen.BOTTOM_AREA - listTop(top)) / ROW);
    }

    private List<CitizenView.Entry> rows() {
        List<CitizenView.Entry> rows = new ArrayList<>();
        if (view != null) {
            for (CitizenView.Entry entry : view.citizens()) {
                if (matches(entry)) {
                    rows.add(entry);
                }
            }
        }
        return rows;
    }

    private int rowCount() {
        if (view == null) {
            return 0;
        }
        return chronicle ? view.chronicle().size() : rows().size();
    }

    void init(int left, int top, int panelWidth, int panelHeight) {
        int buttonsY = top + ThroneScreen.CONTENT_TOP + 2 * LINE + 2;
        if (view == null) {
            return;
        }
        Button filterButton = screen.add(Button.builder(filterLabel(filter), b -> {
            setFilter(filter + 1);
            screen.rebuild();
        }).bounds(left + KingdomScreen.PAD, buttonsY, filterWidth, ROW - 2).build());
        filterButton.active = !chronicle;
        screen.add(Button.builder(toggleLabel(chronicle), b -> {
            setChronicle(!chronicle);
            screen.rebuild();
        }).bounds(left + KingdomScreen.PAD + filterWidth + GAP, buttonsY, toggleWidth, ROW - 2).build());

        int rows = visibleRows(top, panelHeight);
        scroll = Math.max(0, Math.min(scroll, rowCount() - rows));
        if (chronicle || !view.canManage()) {
            return;
        }
        List<CitizenView.Entry> entries = rows();
        int professionX = left + KingdomScreen.PAD + NAME_COLUMN;
        for (int i = 0; i < rows && scroll + i < entries.size(); i++) {
            CitizenView.Entry entry = entries.get(scroll + i);
            int y = listTop(top) + i * ROW;
            String next = Professions.REGISTRY.next(entry.profession()).id();
            screen.add(Button.builder(CitizenTexts.profession(entry.profession(), gender(entry.female())),
                            b -> KingdomWorldNetwork.sendToServer(new CitizenActionPacket(entry.id(), next)))
                    .bounds(professionX, y, professionWidth, ROW - 2).build());
        }
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
        int y = top + ThroneScreen.CONTENT_TOP;
        if (view == null) {
            screen.label(graphics, Component.translatable(KEY + "loading"), x, y + 4, 0xFFB8B8B8, right);
            return;
        }
        for (Component line : summaryLines()) {
            screen.label(graphics, line, x, y, 0xFFE8E8E8, right);
            y += LINE;
        }
        int rows = visibleRows(top, panelHeight);
        int count = rowCount();
        if (count > rows) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(count, scroll + rows), count);
            int pageX = right - font.width(page);
            screen.label(graphics, page, pageX, top + ThroneScreen.CONTENT_TOP + 2 * LINE + 6, 0xFF9A9A9A, right);
        }
        int listTop = listTop(top);
        if (chronicle) {
            renderChronicle(graphics, font, x, right, listTop, rows);
        } else {
            renderList(graphics, x, right, listTop, rows);
        }
    }

    private void renderList(GuiGraphics graphics, int x, int right, int listTop, int rows) {
        List<CitizenView.Entry> entries = rows();
        if (entries.isEmpty()) {
            screen.paragraph(graphics, Component.translatable(view.citizens().isEmpty() ? KEY + "empty" : KEY + "none_match"),
                    x, listTop + 5, right - x, 3, 0xFFB8B8B8);
            return;
        }
        int professionX = x + NAME_COLUMN;
        int stateX = professionX + professionWidth + 6;
        int placeX = stateX + stateWidth + 6;
        for (int i = 0; i < rows && scroll + i < entries.size(); i++) {
            CitizenView.Entry entry = entries.get(scroll + i);
            int y = listTop + i * ROW + 4;
            screen.name(graphics, name(entry.seed(), entry.female()), x, y, 0xFFFFFFFF, NAME_COLUMN - 4);
            if (!view.canManage()) {
                screen.label(graphics, CitizenTexts.profession(entry.profession(), gender(entry.female())), professionX + 4, y,
                        0xFFD0D0D0, stateX - 2);
            }
            int stateColor = entry.state() == CitizenState.WOUNDED ? 0xFFFF8080 : 0xFFA0E0A0;
            screen.label(graphics, state(entry.state(), entry.female()), stateX, y, stateColor, placeX - 2);
            BlockPos home = entry.home();
            if (home == null) {
                screen.label(graphics, Component.translatable(KEY + "no_home"), placeX, y, 0xFFB8B8B8, right);
            } else {
                screen.name(graphics, home.getX() + " " + home.getY() + " " + home.getZ(), placeX, y, 0xFFD0D0D0, right - placeX);
            }
        }
    }

    private void renderChronicle(GuiGraphics graphics, Font font, int x, int right, int listTop, int rows) {
        List<CitizenView.Death> deaths = view.chronicle();
        if (deaths.isEmpty()) {
            screen.label(graphics, Component.translatable(KEY + "chronicle_empty"), x, listTop + 5, 0xFFB8B8B8, right);
            return;
        }
        int causeX = x + NAME_COLUMN;
        for (int i = 0; i < rows && scroll + i < deaths.size(); i++) {
            CitizenView.Death death = deaths.get(scroll + i);
            int y = listTop + i * ROW + 4;
            screen.name(graphics, name(death.seed(), death.female()), x, y, 0xFFE8E8E8, NAME_COLUMN - 4);
            Component day = Component.translatable(KEY + "day", death.day());
            int dayX = right - font.width(day);
            screen.label(graphics, day, dayX, y, 0xFF9A9A9A, right);
            Component cause = Component.translatable(KEY + "death", CitizenTexts.profession(death.profession(), gender(death.female())),
                    CitizenTexts.cause(death.cause(), death.killer(), gender(death.female())), death.place().getX(),
                    death.place().getY(), death.place().getZ());
            screen.name(graphics, cause.getString(), causeX, y, 0xFFD0B0B0, dayX - 6 - causeX);
        }
    }

    /**
     * Name of the current state for layout check messages.
     */
    String checkName() {
        return chronicle ? "chronicle" : "citizens";
    }
}
