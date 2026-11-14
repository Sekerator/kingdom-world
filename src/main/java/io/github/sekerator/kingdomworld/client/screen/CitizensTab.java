package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.core.Cycle;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.CitizenView;
import io.github.sekerator.kingdomworld.common.network.packet.CitizenActionPacket;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.Profession;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The "Citizens" tab of the throne screen: the numbers of the population and four views: the citizens with their
 * professions (which members with the right can change) and homes, the same citizens with their work (post and
 * why the work stands still), what each profession produced today and yesterday, and the chronicle of deaths.
 * Widths come from the translated texts like everywhere in the throne screen; long texts are shortened and shown
 * in full on hover.
 */
final class CitizensTab {
    static final int ROW = 18;
    private static final int LINE = 11;
    private static final int GAP = 2;
    /** Space for a name; the longest generated names fit. */
    private static final int NAME_COLUMN = 86;
    private static final int MIN_PLACE_COLUMN = 64;
    private static final String KEY = "kingdomworld.screen.citizens.";

    /**
     * What the tab shows.
     */
    enum Mode {
        CITIZENS,
        WORK,
        PRODUCTION,
        CHRONICLE,
        LIFE;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final ThroneScreen screen;
    private CitizenView view;
    private io.github.sekerator.kingdomworld.common.network.data.LifeViews.TabView life;
    private int festivalWidth;
    private int workersWidth;
    private int filter;
    private Mode mode = Mode.CITIZENS;
    private int scroll;

    private int professionWidth;
    private int stateWidth;
    private int filterWidth;
    private int modeWidth;
    private int pageWidth;
    private int dayWidth;
    private int countWidth;
    private int professionLabelWidth;

    CitizensTab(ThroneScreen screen) {
        this.screen = screen;
    }

    CitizenView view() {
        return view;
    }

    void setView(CitizenView view) {
        this.view = view;
    }

    void setLife(io.github.sekerator.kingdomworld.common.network.data.LifeViews.TabView life) {
        this.life = life;
    }

    io.github.sekerator.kingdomworld.common.network.data.LifeViews.TabView life() {
        return life;
    }

    boolean isChronicle() {
        return mode == Mode.CHRONICLE;
    }

    void setChronicle(boolean chronicle) {
        setMode(chronicle ? Mode.CHRONICLE : Mode.CITIZENS);
    }

    void setMode(Mode value) {
        this.mode = value;
        this.scroll = 0;
    }

    void setFilter(int filter) {
        this.filter = Math.floorMod(filter, filterCount());
        this.scroll = 0;
    }

    private boolean listsCitizens() {
        return mode == Mode.CITIZENS || mode == Mode.WORK;
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

    private static Component modeLabel(Mode value) {
        return Component.translatable(KEY + "mode", Component.translatable(KEY + "mode." + value.id()));
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

    private static Component work(WorkStatus status) {
        return Component.translatable("kingdomworld.work.status." + status.id());
    }

    private static int workColor(WorkStatus status) {
        if (status == WorkStatus.WORKING) {
            return 0xFFA0E0A0;
        }
        return status.isProblem() ? 0xFFFF9070 : 0xFFD0D0D0;
    }

    private static ItemStack item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        Item item = key == null ? null : ForgeRegistries.ITEMS.getValue(key);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
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
        modeWidth = 0;
        for (Mode value : Mode.values()) {
            modeWidth = Math.max(modeWidth, screen.buttonWidth(modeLabel(value)));
        }
        pageWidth = font.width(Component.translatable("kingdomworld.screen.throne.page", 500, 500, 500));
        festivalWidth = screen.buttonWidth(Component.translatable(KEY + "life.call"));
        workersWidth = screen.buttonWidth(Component.translatable(KEY + "workers"));
        dayWidth = font.width(Component.translatable(KEY + "day", 99_999));
        professionLabelWidth = 0;
        for (Profession profession : professions()) {
            professionLabelWidth = Math.max(professionLabelWidth, font.width(CitizenTexts.profession(profession.id())) + 8);
        }
        professionLabelWidth = Math.max(professionLabelWidth, font.width(CitizenTexts.profession("unknown_profession")) + 8);
        countWidth = Math.max(font.width(Component.translatable(KEY + "production.today", 9_999_999L)),
                font.width(Component.translatable(KEY + "production.yesterday", 9_999_999L)));

        int buttons = filterWidth + GAP + modeWidth + GAP + Math.max(festivalWidth, workersWidth) + 8 + pageWidth;
        int list = NAME_COLUMN + professionWidth + 6 + stateWidth + 6 + MIN_PLACE_COLUMN;
        int deaths = NAME_COLUMN + 120 + 6 + dayWidth;
        int production = professionLabelWidth + 2 * (countWidth + 6) + 60;
        int summary = 0;
        CitizenView saved = view;
        // The longest numbers a summary can show.
        view = new CitizenView(new java.util.UUID(0, 0), false, 10_000, 10_000, 10_000, CitizenView.MAX_FOOD_DAYS, 100, 999,
                io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds.GrowthCheck.READY, List.of(), List.of(), List.of());
        for (Component line : summaryLines()) {
            summary = Math.max(summary, font.width(line));
        }
        view = saved;
        int messages = Math.max(font.width(Component.translatable(KEY + "loading")),
                font.width(Component.translatable(KEY + "chronicle_empty")));
        messages = Math.max(messages, font.width(Component.translatable(KEY + "none_match")));
        messages = Math.max(messages, font.width(Component.translatable(KEY + "production.none")));
        return Math.max(Math.max(Math.max(buttons, list), production), Math.max(Math.max(deaths, summary), messages));
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
        return switch (mode) {
            case CHRONICLE -> view.chronicle().size() + view.decrees().size();
            case PRODUCTION -> view.production().size();
            case LIFE -> LifeRows.rows(life).size();
            default -> rows().size() + settlementRows().size();
        };
    }

    /**
     * The settlements of a state of the world, listed after its citizens while no filter is chosen.
     */
    private List<CitizenView.SettlementRow> settlementRows() {
        return view == null || filter != 0 ? List.of() : view.settlements();
    }

    void init(int left, int top, int panelWidth, int panelHeight) {
        int buttonsY = top + ThroneScreen.CONTENT_TOP + 2 * LINE + 2;
        if (view == null) {
            return;
        }
        Button filterButton = screen.add(new CycleButton(left + KingdomScreen.PAD, buttonsY, filterWidth, ROW - 2, filterLabel(filter), direction -> {
            setFilter(filter + direction);
            screen.rebuild();
        }));
        filterButton.active = listsCitizens();
        screen.add(new CycleButton(left + KingdomScreen.PAD + filterWidth + GAP, buttonsY, modeWidth, ROW - 2, modeLabel(mode), direction -> {
            setMode(Cycle.step(mode, direction));
            screen.rebuild();
        }));
        if (mode == Mode.LIFE && life != null && life.canManage()) {
            // The king calls a festival; the server says why not when it cannot be.
            net.minecraft.client.gui.components.Button festival = screen.add(Button.builder(Component.translatable(KEY + "life.call"),
                    b -> KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.LifePackets.CallFestival(life.kingdomId())))
                    .bounds(left + KingdomScreen.PAD + filterWidth + GAP + modeWidth + GAP, buttonsY, festivalWidth, ROW - 2).build());
            festival.active = life.nextIn() == 0 && life.festival() != 0;
            festival.setTooltip(net.minecraft.client.gui.components.Tooltip.create(LifeRows.festivalHint(life)));
        }

        if (listsCitizens() && view.canManage()) {
            // The window of the workers: who does what, what is lacking, the posts, all without walking to them.
            Button workers = screen.add(Button.builder(Component.translatable(KEY + "workers"),
                    b -> KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.WorkforcePackets.Request(
                            view.kingdomId(), true, io.github.sekerator.kingdomworld.common.network.data.WorkforceView.Query.of(
                                    io.github.sekerator.kingdomworld.common.network.data.WorkforceView.Mode.CITIZENS,
                                    io.github.sekerator.kingdomworld.common.citizen.WorkforceActions.DEFAULT_PAGE))))
                    .bounds(left + KingdomScreen.PAD + filterWidth + GAP + modeWidth + GAP, buttonsY, workersWidth, ROW - 2).build());
            workers.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(KEY + "workers_hint")));
        }

        int rows = visibleRows(top, panelHeight);
        scroll = Math.max(0, Math.min(scroll, rowCount() - rows));
        if (!listsCitizens() || !view.canManage()) {
            return;
        }
        List<CitizenView.Entry> entries = rows();
        int professionX = left + KingdomScreen.PAD + NAME_COLUMN;
        for (int i = 0; i < rows && scroll + i < entries.size(); i++) {
            CitizenView.Entry entry = entries.get(scroll + i);
            int y = listTop(top) + i * ROW;
            // Soldiers are recruited and dismissed in the army tab.
            Button profession = screen.add(new CycleButton(professionX, y, professionWidth, ROW - 2,
                    CitizenTexts.profession(entry.profession(), gender(entry.female())), direction -> KingdomWorldNetwork.sendToServer(new CitizenActionPacket(entry.id(), Professions.stepCivilian(entry.profession(), direction).id()))));
            if (Professions.isMilitary(entry.profession())) {
                profession.active = false;
                profession.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(KEY + "soldier_hint")));
            }
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
        // The button of the festival may leave no room for the page on a narrow screen; the rows still scroll.
        int buttonsRight = x + filterWidth + GAP + modeWidth + (mode == Mode.LIFE && life != null && life.canManage() ? GAP + festivalWidth
                : listsCitizens() && view.canManage() ? GAP + workersWidth : 0);
        if (count > rows) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(count, scroll + rows), count);
            int pageX = right - font.width(page);
            if (pageX >= buttonsRight + 4) {
                screen.label(graphics, page, pageX, top + ThroneScreen.CONTENT_TOP + 2 * LINE + 6, 0xFF9A9A9A, right);
            }
        }
        int listTop = listTop(top);
        switch (mode) {
            case LIFE -> LifeRows.render(screen, graphics, life, x, right, listTop, rows, scroll, ROW);
            case CHRONICLE -> renderChronicle(graphics, font, x, right, listTop, rows);
            case PRODUCTION -> renderProduction(graphics, font, x, right, listTop, rows);
            default -> renderList(graphics, x, right, listTop, rows);
        }
    }

    private void renderList(GuiGraphics graphics, int x, int right, int listTop, int rows) {
        List<CitizenView.Entry> entries = rows();
        List<CitizenView.SettlementRow> settlements = settlementRows();
        if (entries.isEmpty() && settlements.isEmpty()) {
            screen.paragraph(graphics, Component.translatable(view.citizens().isEmpty() ? KEY + "empty" : KEY + "none_match"),
                    x, listTop + 5, right - x, 3, 0xFFB8B8B8);
            return;
        }
        int professionX = x + NAME_COLUMN;
        int stateX = professionX + professionWidth + 6;
        int placeX = mode == Mode.WORK ? right - MIN_PLACE_COLUMN : stateX + stateWidth + 6;
        boolean cyrillic = io.github.sekerator.kingdomworld.core.citizen.CitizenNames.alphabetFor(
                net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected())
                == io.github.sekerator.kingdomworld.core.citizen.CitizenNames.Alphabet.CYRILLIC;
        for (int i = Math.max(0, entries.size() - scroll); i < rows && scroll + i < entries.size() + settlements.size(); i++) {
            CitizenView.SettlementRow settlement = settlements.get(scroll + i - entries.size());
            int y = listTop + i * ROW + 4;
            screen.name(graphics, cyrillic ? settlement.cyrillic() : settlement.latin(), x, y, 0xFFF0D070, NAME_COLUMN - 4);
            Component tier = Component.translatable("kingdomworld.settlement.tier." + settlement.tier().name().toLowerCase(Locale.ROOT));
            screen.label(graphics, settlement.data() ? Component.translatable(KEY + "settlement.planned", tier) : tier, professionX + 4, y,
                    0xFFD0D0D0, stateX - 2);
            screen.label(graphics, Component.translatable(KEY + "settlement.numbers", settlement.population(), settlement.named(),
                    settlement.housing(), settlement.food(), settlement.guards() + settlement.militia()), stateX, y,
                    settlement.food() == 0 ? 0xFFFF8080 : 0xFFA0E0A0, right);
        }
        for (int i = 0; i < rows && scroll + i < entries.size(); i++) {
            CitizenView.Entry entry = entries.get(scroll + i);
            int y = listTop + i * ROW + 4;
            screen.name(graphics, name(entry.seed(), entry.female()), x, y, 0xFFFFFFFF, NAME_COLUMN - 4);
            if (!view.canManage()) {
                screen.label(graphics, CitizenTexts.profession(entry.profession(), gender(entry.female())), professionX + 4, y,
                        0xFFD0D0D0, stateX - 2);
            }
            if (mode == Mode.WORK) {
                WorkStatus work = entry.work();
                screen.name(graphics, work(work).getString(), stateX, y, workColor(work), placeX - 6 - stateX);
                BlockPos place = entry.workplace();
                if (place == null) {
                    screen.name(graphics, Component.translatable(KEY + "no_workplace").getString(), placeX, y, 0xFFB8B8B8, right - placeX);
                } else {
                    screen.name(graphics, place.getX() + " " + place.getY() + " " + place.getZ(), placeX, y, 0xFFD0D0D0, right - placeX);
                }
                continue;
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

    private void renderProduction(GuiGraphics graphics, Font font, int x, int right, int listTop, int rows) {
        List<CitizenView.Production> production = view.production();
        if (production.isEmpty()) {
            screen.paragraph(graphics, Component.translatable(KEY + "production.none"), x, listTop + 5, right - x, 3, 0xFFB8B8B8);
            return;
        }
        int todayX = x + professionLabelWidth;
        int yesterdayX = todayX + countWidth + 6;
        int itemX = yesterdayX + countWidth + 6;
        for (int i = 0; i < rows && scroll + i < production.size(); i++) {
            CitizenView.Production entry = production.get(scroll + i);
            int y = listTop + i * ROW;
            screen.label(graphics, CitizenTexts.profession(entry.profession()), x, y + 5, 0xFFFFFFFF, todayX - 4);
            screen.label(graphics, Component.translatable(KEY + "production.today", entry.today()), todayX, y + 5, 0xFFA0E0A0,
                    yesterdayX - 4);
            screen.label(graphics, Component.translatable(KEY + "production.yesterday", entry.yesterday()), yesterdayX, y + 5,
                    0xFFD0D0D0, itemX - 4);
            ItemStack top = item(entry.top());
            if (!top.isEmpty() && itemX + 18 < right) {
                graphics.renderItem(top, itemX, y);
                screen.name(graphics, top.getHoverName().getString(), itemX + 18, y + 5, 0xFFD0D0D0, right - itemX - 18);
            }
        }
    }

    /**
     * The deaths and, in a kingdom of the world, the decrees of its ruler, the newest first.
     */
    private List<Object> chronicleRows() {
        List<Object> rows = new ArrayList<>();
        List<CitizenView.Death> deaths = view.chronicle();
        List<io.github.sekerator.kingdomworld.common.network.data.RulerInfo.Decree> decrees = view.decrees();
        int d = 0;
        int r = 0;
        while (d < deaths.size() || r < decrees.size()) {
            if (r < decrees.size() && (d >= deaths.size() || decrees.get(r).day() >= deaths.get(d).day())) {
                rows.add(decrees.get(r++));
            } else {
                rows.add(deaths.get(d++));
            }
        }
        return rows;
    }

    private void renderChronicle(GuiGraphics graphics, Font font, int x, int right, int listTop, int rows) {
        List<Object> entries = chronicleRows();
        if (entries.isEmpty()) {
            screen.label(graphics, Component.translatable(KEY + "chronicle_empty"), x, listTop + 5, 0xFFB8B8B8, right);
            return;
        }
        int causeX = x + NAME_COLUMN;
        for (int i = 0; i < rows && scroll + i < entries.size(); i++) {
            int y = listTop + i * ROW + 4;
            if (entries.get(scroll + i) instanceof io.github.sekerator.kingdomworld.common.network.data.RulerInfo.Decree decree) {
                Component day = Component.translatable(KEY + "day", decree.day());
                int dayX = right - font.width(day);
                screen.label(graphics, day, dayX, y, 0xFF9A9A9A, right);
                screen.name(graphics, decreeLine(decree).getString(), x, y, 0xFFE8D8A0, dayX - 6 - x);
                continue;
            }
            CitizenView.Death death = (CitizenView.Death) entries.get(scroll + i);
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
     * A decree as the chronicle writes it, with the name of the ruler when the screen knows it.
     */
    private Component decreeLine(io.github.sekerator.kingdomworld.common.network.data.RulerInfo.Decree decree) {
        io.github.sekerator.kingdomworld.common.network.data.NpcKingdomInfo npc = screen.view().npc();
        if (npc == null) {
            return Component.translatable(KEY + "decree", io.github.sekerator.kingdomworld.common.npc.RulerTexts.decree(decree.decision(),
                    io.github.sekerator.kingdomworld.core.citizen.Gender.MALE));
        }
        return io.github.sekerator.kingdomworld.common.npc.RulerTexts.chronicle(io.github.sekerator.kingdomworld.common.npc.NpcTexts.ruler(
                npc.rulerLatin(), npc.rulerCyrillic(), npc.rulerGender()), decree.decision(), npc.rulerGender());
    }

    /**
     * Name of the current state for layout check messages.
     */
    String checkName() {
        return mode == Mode.CITIZENS ? "citizens" : mode.id();
    }
}
