package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.CitizenView;
import io.github.sekerator.kingdomworld.common.network.data.WorkforceView;
import io.github.sekerator.kingdomworld.common.network.packet.WorkPostActionPacket;
import io.github.sekerator.kingdomworld.common.network.packet.WorkforcePackets;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNames;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.Profession;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.citizen.Workforce;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The window of the workers of a kingdom: what every citizen is doing, where it works and what it lacks, and every work
 * post with its workers, state and container, so that a king moves people between posts and professions without walking to
 * the posts. A row is chosen with a click; the buttons under the list act on it (a double click does what the first one
 * does). Putting a citizen on a post goes through the list of posts with a free seat, putting somebody on a post through
 * the list of its candidates; a worker taken from other work moves in one step. The settings of a post open its own window
 * from afar. Every button sends an intent; the server answers with the result and the page. Widths come from the translated
 * texts; long texts are shortened with an ellipsis and shown in full on hover.
 */
public class WorkforceScreen extends KingdomScreen implements ResultListener {
    private static final String KEY = "kingdomworld.screen.workforce.";
    private static final int MIN_PANEL_WIDTH = 300;
    private static final int MAX_PANEL_WIDTH = 600;
    private static final int MAX_PANEL_HEIGHT = 330;
    /** A row holds two lines of text. */
    private static final int ROW = 21;
    private static final int SMALL = 14;
    private static final int GAP = 2;
    private static final int TOP_ROW = 4;
    private static final int SUMMARY = 22;
    private static final int FILTERS = 44;
    private static final int LIST = 64;
    private static final int BOTTOM_AREA = 36;
    private static final int NAME_COLUMN = 82;
    private static final int PROFESSION_COLUMN = 74;
    private static final int DOING_COLUMN = 150;
    private static final int ARROW = 16;
    /** A second click on the same row within this time does what the first button does. */
    private static final long DOUBLE_CLICK_MILLIS = 400L;

    @Nullable
    private final Screen parent;
    @Nullable
    private WorkforceView view;
    private WorkforceView.Query query;
    @Nullable
    private UUID kingdomId;
    /** The list to go back to from the choices, {@code null} when they were opened by the window of a post. */
    @Nullable
    private WorkforceView.Query back;
    /** The citizen or post chosen in the list: its id or its place. */
    @Nullable
    private Object selected;
    private long lastClick;
    @Nullable
    private Object lastClicked;
    private Component status = Component.empty();
    private boolean statusIsError;
    /** Whether a page was asked for and has not come yet. */
    private boolean waiting;

    private int panelWidth;
    private int panelHeight;
    private final List<Button> actions = new ArrayList<>();

    /**
     * The window as the server opened it.
     *
     * @param parent the screen to go back to, {@code null} to close
     */
    public WorkforceScreen(@Nullable Screen parent, WorkforceView view) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
        this.view = view;
        this.query = view.query();
        this.kingdomId = view.kingdomId();
        if (query.mode() == WorkforceView.Mode.CITIZENS && query.citizen() != null) {
            // Opened by a click on a citizen: it is chosen in the list.
            selected = query.citizen();
        }
    }

    /**
     * The citizen the list was opened at is chosen; the pages asked for from now on are the pages of the list itself.
     */
    private void settleFocus() {
        query = query.withoutFocus();
    }

    private WorkforceScreen(@Nullable Screen parent, UUID kingdomId, WorkforceView.Query query) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
        this.kingdomId = kingdomId;
        this.query = query;
    }

    /**
     * The candidates of a post, opened from the window of the post: the choice goes back to it.
     */
    public static WorkforceScreen chooseCitizen(Screen parent, UUID kingdomId, WorkforceView.PostRef post) {
        return new WorkforceScreen(parent, kingdomId, WorkforceView.Query.chooseCitizen(post, io.github.sekerator.kingdomworld.common.citizen
                .WorkforceActions.DEFAULT_PAGE));
    }

    @Override
    public String checkName() {
        return "workforce/" + query.mode().id() + (view == null ? "/loading" : "");
    }

    @Nullable
    public WorkforceView view() {
        return view;
    }

    /**
     * A page from the server; an answer to a question the window no longer asks is dropped.
     */
    public void update(WorkforceView next) {
        WorkforceView.Query asked = next.query();
        if (asked.mode() != query.mode() || asked.pageSize() != query.pageSize() || !java.util.Objects.equals(asked.citizen(), query.citizen())
                || !java.util.Objects.equals(asked.post(), query.post())) {
            // An answer for another list or another height; the one asked for last is on its way.
            return;
        }
        view = next;
        query = asked;
        kingdomId = next.kingdomId();
        waiting = false;
        settleFocus();
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    /**
     * Asks for the page again, for example after the window of a post was closed.
     */
    public void refresh() {
        request();
    }

    private void request() {
        Minecraft client = Minecraft.getInstance();
        waiting = true;
        if (client.getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new WorkforcePackets.Request(kingdomId, false, query));
        }
    }

    private void act(WorkforcePackets.Action.Kind kind, @Nullable UUID citizen, @Nullable WorkforceView.PostRef post, String text) {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() != null && kingdomId != null) {
            KingdomWorldNetwork.sendToServer(new WorkforcePackets.Action(kingdomId, kind, citizen, post, text, query));
        }
    }

    private void show(WorkforceView.Query next) {
        query = next;
        selected = null;
        view = view == null ? null : new WorkforceView(next, view.kingdomId(), view.kingdomName(), view.header(), 0, List.of(), List.of(),
                List.of(), null, null);
        request();
        rebuildWidgets();
    }

    // ---------------------------------------------------------------- texts

    private static Gender gender(boolean female) {
        return female ? Gender.FEMALE : Gender.MALE;
    }

    private static boolean cyrillic() {
        return CitizenNames.alphabetFor(Minecraft.getInstance().getLanguageManager().getSelected()) == CitizenNames.Alphabet.CYRILLIC;
    }

    /**
     * The name of a post: the one it was given, or its profession and number.
     */
    public static Component postName(String name, String profession, int number) {
        if (!name.isEmpty()) {
            return Component.literal(name);
        }
        if (Professions.UNEMPLOYED.id().equals(profession)) {
            return Component.translatable("kingdomworld.workforce.post_name.none", number);
        }
        return Component.translatable("kingdomworld.workforce.post_name", CitizenTexts.profession(profession), number);
    }

    private static Component distance(int blocks) {
        return blocks == WorkforceView.FAR ? Component.translatable(KEY + "far") : Component.translatable(KEY + "distance", blocks);
    }

    private Component placeName(String key) {
        if (key.isEmpty()) {
            return Component.translatable(KEY + "place.none");
        }
        if (view != null) {
            for (WorkforceView.Place place : view.header().places()) {
                if (place.key().equals(key)) {
                    String name = cyrillic() ? place.cyrillic() : place.latin();
                    if (key.equals("c0") && name.isEmpty()) {
                        return Component.translatable(KEY + "place.capital");
                    }
                    return Component.literal(name.isEmpty() ? key : name);
                }
            }
        }
        return Component.translatable(KEY + "place.none");
    }

    private static Component statusText(WorkStatus status) {
        return Component.translatable("kingdomworld.work.status." + status.id());
    }

    private static int statusColor(WorkStatus status) {
        if (status == WorkStatus.WORKING) {
            return 0xFFA0E0A0;
        }
        return status.isProblem() ? 0xFFFF9070 : 0xFFE0D090;
    }

    /**
     * What a citizen is doing: why the work stands still when it does, the work of its profession while it works.
     */
    static Component doingText(WorkforceView.CitizenRow row) {
        return switch (row.doing()) {
            case STANDING -> statusText(row.status() == WorkStatus.NONE ? WorkStatus.NO_WORKPLACE : row.status());
            case WORKING -> Component.translatable("kingdomworld.workforce.doing.work." + workKey(row.profession()));
            default -> Component.translatable("kingdomworld.workforce.doing." + row.doing().id());
        };
    }

    /**
     * The key of the work of a profession; professions of other mods work in general words.
     */
    private static String workKey(String profession) {
        for (Profession known : Professions.REGISTRY.all()) {
            if (known.id().equals(profession) && WORK_KEYS.contains(profession)) {
                return profession;
            }
        }
        return "other";
    }

    /** Professions whose work has words of its own. */
    static final List<String> WORK_KEYS = List.of("farmer", "lumberjack", "miner", "fisher", "herder", "carrier", "smelter", "cook", "crafter",
            "smith", "minter", "merchant", "registrar", "adventurer", "builder", "mage", "innkeeper");

    private static int doingColor(WorkforceView.CitizenRow row) {
        return switch (row.doing()) {
            case STANDING -> 0xFFFF9070;
            case WORKING, CARRYING, PREPARING, GOING_TO_WORK -> 0xFFA0E0A0;
            case HEALING, SICK -> 0xFFFFB070;
            default -> 0xFFD0D0D0;
        };
    }

    /**
     * Something lacking in a few words: "axe", "saplings".
     */
    static Component lackText(Workforce.Need need, String detail) {
        if ((need == Workforce.Need.TOOL || need == Workforce.Need.BETTER_TOOL) && !detail.isEmpty()) {
            return Component.translatable("kingdomworld.workforce.lack." + need.id() + "." + detail);
        }
        return Component.translatable("kingdomworld.workforce.lack." + need.id());
    }

    /**
     * Something the kingdom lacks, with how many of its citizens: "3 workers need an axe".
     */
    static Component shortageText(WorkforceView.Shortage shortage) {
        // Food, a bed and care are lacked by citizens, everything else by workers.
        Workforce.Need need = shortage.need();
        String who = need == Workforce.Need.FOOD || need == Workforce.Need.HOME || need == Workforce.Need.CARE ? "citizens" : "workers";
        Component count = Component.translatable("kingdomworld.workforce.count." + who + "." + io.github.sekerator.kingdomworld.core.economy.Plurals
                .form(shortage.count()).suffix(), shortage.count());
        String key = "kingdomworld.workforce.need." + shortage.need().id();
        if ((shortage.need() == Workforce.Need.TOOL || shortage.need() == Workforce.Need.BETTER_TOOL) && !shortage.detail().isEmpty()) {
            key += "." + shortage.detail();
        }
        return Component.translatable(key, count);
    }

    private Component foodText() {
        if (view == null || view.header().foodDays() == CitizenView.UNLIMITED_FOOD) {
            return Component.translatable(KEY + "food_unlimited");
        }
        return Component.translatable(KEY + "food", io.github.sekerator.kingdomworld.common.life.LifeTexts.days(view.header().foodDays()));
    }

    private Component countsLine() {
        if (view == null) {
            return Component.translatable(KEY + "loading");
        }
        WorkforceView.Header header = view.header();
        if (query.mode() == WorkforceView.Mode.POSTS) {
            return Component.translatable(KEY + "posts_summary", header.postCount(), header.emptyPosts(), header.freeSeats());
        }
        return Component.translatable(KEY + "summary", header.working(), header.unemployed(), header.withoutPost(), header.soldiers());
    }

    /**
     * Everything the kingdom lacks, one line each, the food first.
     */
    private List<Component> shortageLines() {
        List<Component> lines = new ArrayList<>();
        if (view == null) {
            return lines;
        }
        lines.add(foodText());
        for (WorkforceView.Shortage shortage : view.header().shortages()) {
            lines.add(shortageText(shortage));
        }
        return lines;
    }

    /**
     * What the kingdom lacks on one line, the food last; with nothing lacking, that and the food.
     */
    private Component shortageLine() {
        List<Component> lines = shortageLines();
        if (lines.isEmpty()) {
            return Component.empty();
        }
        if (lines.size() == 1) {
            return Component.translatable(KEY + "nothing_lacking", lines.get(0));
        }
        MutableComponent line = Component.empty();
        for (int i = 1; i < lines.size(); i++) {
            if (i > 1) {
                line.append(Component.translatable(KEY + "separator"));
            }
            line.append(lines.get(i));
        }
        line.append(Component.translatable(KEY + "separator")).append(lines.get(0));
        return line;
    }

    private static Component modeName(WorkforceView.Mode mode) {
        return Component.translatable(KEY + "tab." + mode.id());
    }

    private Component titleText() {
        return switch (query.mode()) {
            case CHOOSE_POST -> Component.translatable(KEY + "choose_post.title");
            case CHOOSE_CITIZEN -> Component.translatable(KEY + "choose_citizen.title");
            default -> {
                String kingdom = view == null ? "" : view.kingdomName();
                if (view != null) {
                    for (WorkforceView.Place place : view.header().places()) {
                        if (place.key().equals("c0") && !(cyrillic() ? place.cyrillic() : place.latin()).isEmpty()) {
                            kingdom = cyrillic() ? place.cyrillic() : place.latin();
                        }
                    }
                }
                yield Component.translatable(KEY + "title_of", kingdom);
            }
        };
    }

    /**
     * The first line of the choices: whom or what they are for.
     */
    private Component targetLine() {
        if (view == null) {
            return Component.translatable(KEY + "loading");
        }
        if (query.mode() == WorkforceView.Mode.CHOOSE_POST) {
            WorkforceView.CitizenRow who = view.forCitizen();
            if (who == null) {
                return Component.translatable(KEY + "gone");
            }
            return Component.translatable(KEY + "choose_post.for", CitizenTexts.name(who.seed(), gender(who.female())),
                    CitizenTexts.profession(who.profession(), gender(who.female())));
        }
        WorkforceView.PostRow post = view.forPost();
        if (post == null) {
            return Component.translatable(KEY + "gone");
        }
        return Component.translatable(KEY + "choose_citizen.for", postName(post.name(), post.profession(), post.number()), post.workers(),
                post.seats());
    }

    private Component hintLine() {
        return Component.translatable(query.mode() == WorkforceView.Mode.CHOOSE_POST ? KEY + "choose_post.hint" : KEY + "choose_citizen.hint");
    }

    // ---------------------------------------------------------------- layout

    private int left() {
        return (width - panelWidth) / 2;
    }

    private int top() {
        return (height - panelHeight) / 2;
    }

    private int listTop() {
        return top() + LIST;
    }

    private int listBottom() {
        return top() + panelHeight - BOTTOM_AREA - 2;
    }

    int visibleRows() {
        return Math.max(1, Math.min(WorkforceView.MAX_PAGE, (listBottom() - listTop()) / ROW));
    }

    private void measure() {
        int inner = 0;
        for (WorkforceView.Mode mode : List.of(WorkforceView.Mode.CITIZENS, WorkforceView.Mode.POSTS)) {
            inner = Math.max(inner, buttonWidth(modeName(mode)) * 2 + GAP + 120);
        }
        inner = Math.max(inner, NAME_COLUMN + PROFESSION_COLUMN + DOING_COLUMN + 80);
        inner = Math.max(inner, actionsWidth() + GAP + doneWidth() + 8);
        panelWidth = Math.min(Math.max(MIN_PANEL_WIDTH, Math.min(MAX_PANEL_WIDTH, inner + 2 * PAD)), width - 8);
        // A wide screen gives the texts of the rows more room.
        panelWidth = Math.min(Math.max(panelWidth, Math.min(MAX_PANEL_WIDTH, width * 3 / 4)), width - 8);
        panelHeight = Math.min(MAX_PANEL_HEIGHT, height - 8);
    }

    private int doneWidth() {
        return Math.max(50, buttonWidth(Component.translatable(query.mode().choice() ? "gui.back" : "gui.done")));
    }

    private List<Component> actionLabels() {
        return switch (query.mode()) {
            case CITIZENS -> List.of(Component.translatable(KEY + "dismiss"), Component.translatable(KEY + "assign"));
            case POSTS -> List.of(Component.translatable(KEY + "settings"), Component.translatable(KEY + "put"),
                    Component.translatable(KEY + "clear"), Component.translatable(KEY + "rename"));
            case CHOOSE_POST -> List.of(Component.translatable(KEY + "choose_post.choose"));
            case CHOOSE_CITIZEN -> List.of(Component.translatable(KEY + "choose_citizen.choose"));
        };
    }

    private int actionsWidth() {
        int total = 0;
        for (Component label : actionLabels()) {
            total += buttonWidth(label) + GAP;
        }
        return total;
    }

    // ---------------------------------------------------------------- widgets

    @Override
    protected void init() {
        measure();
        actions.clear();
        int left = left();
        int top = top();
        int right = left + panelWidth - PAD;
        int rows = visibleRows();
        if (rows != query.pageSize()) {
            // Another height shows another number of rows: the page keeps the first row in sight.
            int first = query.page() * query.pageSize();
            query = query.withPageSize(rows).withPage(first / rows);
            request();
        } else if (view == null && !waiting) {
            // Opened without a page, from the window of a post: the first one is asked for now.
            request();
        } else if (!waiting) {
            settleFocus();
        }
        if (!query.mode().choice()) {
            int x = left + PAD;
            for (WorkforceView.Mode mode : List.of(WorkforceView.Mode.CITIZENS, WorkforceView.Mode.POSTS)) {
                Component label = modeName(mode);
                int w = buttonWidth(label);
                Button tab = addRenderableWidget(Button.builder(label, b -> show(WorkforceView.Query.of(mode, visibleRows())))
                        .bounds(x, top + TOP_ROW, w, SMALL).build());
                tab.active = query.mode() != mode;
                x += w + GAP;
            }
        }
        filters(left + PAD, top + FILTERS, right);
        buttons(left, top, right);
    }

    private int tabsRight() {
        int x = left() + PAD;
        if (!query.mode().choice()) {
            for (WorkforceView.Mode mode : List.of(WorkforceView.Mode.CITIZENS, WorkforceView.Mode.POSTS)) {
                x += buttonWidth(modeName(mode)) + GAP;
            }
        }
        return x;
    }

    private Component pageLabel() {
        int pages = view == null ? 1 : view.pages();
        return Component.translatable(KEY + "page", Math.min(pages, query.page() + 1), pages);
    }

    private int pageLabelWidth() {
        return Math.max(font.width(pageLabel()), font.width(Component.translatable(KEY + "page", 99, 99)));
    }

    /**
     * The filters of the lists, the professions without a post of the choices of a citizen, and the pages.
     */
    private void filters(int x, int y, int right) {
        int pagesLeft = right - 2 * ARROW - GAP - 4 - pageLabelWidth() - 4;
        Button previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> page(-1))
                .bounds(right - 2 * ARROW - GAP, y, ARROW, SMALL + 2).build());
        previous.active = query.page() > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> page(1))
                .bounds(right - ARROW, y, ARROW, SMALL + 2).build());
        next.active = view != null && query.page() + 1 < view.pages();
        if (view == null) {
            return;
        }
        if (query.mode() == WorkforceView.Mode.CHOOSE_POST) {
            WorkforceView.CitizenRow who = view.forCitizen();
            Component label = Component.translatable(KEY + "choose_post.without_post");
            int cx = x + font.width(label) + 4;
            for (String profession : view.professions()) {
                Component name = CitizenTexts.profession(profession, who == null ? Gender.MALE : gender(who.female()));
                int w = buttonWidth(name);
                if (cx + w > pagesLeft) {
                    break;
                }
                Button choose = addRenderableWidget(Button.builder(name, b -> {
                    if (who != null) {
                        act(WorkforcePackets.Action.Kind.PROFESSION, who.id(), null, profession);
                    }
                }).bounds(cx, y, w, SMALL + 2).build());
                choose.active = who != null && !who.profession().equals(profession);
                choose.setTooltip(Tooltip.create(Component.translatable(KEY + "choose_post.profession_hint", name)));
                cx += w + GAP;
            }
            return;
        }
        if (query.mode() != WorkforceView.Mode.CITIZENS && query.mode() != WorkforceView.Mode.POSTS) {
            return;
        }
        // The place, the profession and the problems share what the pages leave; long names are shortened.
        Component problems = Component.translatable(query.problems() ? KEY + "filter.problems_on" : KEY + "filter.problems_off");
        int problemsWidth = Math.max(buttonWidth(Component.translatable(KEY + "filter.problems_on")),
                buttonWidth(Component.translatable(KEY + "filter.problems_off")));
        int room = pagesLeft - x - problemsWidth - 2 * GAP;
        List<String> places = placeKeys();
        List<String> professions = professionKeys();
        int placeWidth = 0;
        for (String place : places) {
            placeWidth = Math.max(placeWidth, buttonWidth(placeFilter(place)));
        }
        int professionWidth = 0;
        for (String profession : professions) {
            professionWidth = Math.max(professionWidth, buttonWidth(professionFilter(profession)));
        }
        if (placeWidth + professionWidth > room) {
            int half = Math.max(40, room / 2);
            placeWidth = Math.min(placeWidth, Math.max(half, room - professionWidth));
            professionWidth = Math.max(40, room - placeWidth);
        }
        int placeIndex = Math.max(0, places.indexOf(query.place()));
        Component placeLabel = placeFilter(query.place());
        CycleButton place = addRenderableWidget(new CycleButton(x, y, placeWidth, SMALL + 2,
                Component.literal(fit(placeLabel.getString(), placeWidth - LayoutCheck.BUTTON_TEXT_MARGIN - 8)), direction -> {
            String chosen = places.get(Math.floorMod(placeIndex + direction, places.size()));
            show(query.withFilters(chosen, query.profession(), query.problems()));
        }));
        place.setTooltip(CycleButton.hinted(placeLabel));
        int professionIndex = Math.max(0, professions.indexOf(query.profession()));
        Component professionLabel = professionFilter(query.profession());
        CycleButton profession = addRenderableWidget(new CycleButton(x + placeWidth + GAP, y, professionWidth, SMALL + 2,
                Component.literal(fit(professionLabel.getString(), professionWidth - LayoutCheck.BUTTON_TEXT_MARGIN - 8)), direction -> {
            String chosen = professions.get(Math.floorMod(professionIndex + direction, professions.size()));
            show(query.withFilters(query.place(), chosen, query.problems()));
        }));
        profession.setTooltip(CycleButton.hinted(professionLabel));
        Button problemsButton = addRenderableWidget(Button.builder(problems,
                        b -> show(query.withFilters(query.place(), query.profession(), !query.problems())))
                .bounds(x + placeWidth + GAP + professionWidth + GAP, y, problemsWidth, SMALL + 2).build());
        problemsButton.setTooltip(Tooltip.create(Component.translatable(query.mode() == WorkforceView.Mode.POSTS ? KEY + "filter.problems_posts_hint"
                : KEY + "filter.problems_hint")));
    }

    private List<String> placeKeys() {
        List<String> keys = new ArrayList<>();
        keys.add("");
        if (view != null) {
            for (WorkforceView.Place place : view.header().places()) {
                keys.add(place.key());
            }
        }
        keys.add("-");
        return keys;
    }

    private Component placeFilter(String key) {
        Component value = key.isEmpty() ? Component.translatable(KEY + "filter.all") : key.equals("-")
                ? Component.translatable(KEY + "filter.outside") : placeName(key);
        return Component.translatable(KEY + "filter.place", value);
    }

    private List<String> professionKeys() {
        List<String> keys = new ArrayList<>();
        keys.add("");
        for (Profession profession : Professions.REGISTRY.all()) {
            // A post has a profession that works at posts, or none.
            if (query.mode() == WorkforceView.Mode.POSTS && !profession.id().equals(Professions.UNEMPLOYED.id())
                    && !profession.needsWorkplace()) {
                continue;
            }
            keys.add(profession.id());
        }
        return keys;
    }

    private Component professionFilter(String key) {
        Component value;
        if (key.isEmpty()) {
            value = Component.translatable(KEY + "filter.all");
        } else if (query.mode() == WorkforceView.Mode.POSTS && key.equals(Professions.UNEMPLOYED.id())) {
            value = Component.translatable(KEY + "filter.no_profession");
        } else {
            value = CitizenTexts.profession(key);
        }
        return Component.translatable(KEY + "filter.profession", value);
    }

    private void page(int direction) {
        if (view == null) {
            return;
        }
        int next = Math.max(0, Math.min(view.pages() - 1, query.page() + direction));
        if (next != query.page()) {
            query = query.withPage(next);
            selected = null;
            request();
            rebuildWidgets();
        }
    }

    private void buttons(int left, int top, int right) {
        int y = top + panelHeight - 24;
        int x = left + PAD;
        List<Component> labels = actionLabels();
        for (int i = 0; i < labels.size(); i++) {
            int index = i;
            Component label = labels.get(i);
            int w = buttonWidth(label);
            Button button = addRenderableWidget(Button.builder(label, b -> action(index)).bounds(x, y, w, 20).build());
            actions.add(button);
            x += w + GAP;
        }
        updateActions();
        int done = doneWidth();
        addRenderableWidget(Button.builder(Component.translatable(query.mode().choice() ? "gui.back" : "gui.done"), b -> onClose())
                .bounds(right - done, y, done, 20).build());
    }

    @Nullable
    private WorkforceView.CitizenRow selectedCitizen() {
        if (view == null || !(selected instanceof UUID id)) {
            return null;
        }
        for (WorkforceView.CitizenRow row : view.citizens()) {
            if (row.id().equals(id)) {
                return row;
            }
        }
        return null;
    }

    @Nullable
    private WorkforceView.PostRow selectedPost() {
        if (view == null || !(selected instanceof WorkforceView.PostRef ref)) {
            return null;
        }
        for (WorkforceView.PostRow row : view.posts()) {
            if (row.ref().equals(ref)) {
                return row;
            }
        }
        return null;
    }

    /**
     * Which of the buttons under the list can act on the row chosen, and what they say on hover.
     */
    private void updateActions() {
        WorkforceView.CitizenRow citizen = selectedCitizen();
        WorkforceView.PostRow post = selectedPost();
        for (int i = 0; i < actions.size(); i++) {
            Button button = actions.get(i);
            button.setTooltip(null);
            switch (query.mode()) {
                case CITIZENS -> {
                    boolean soldier = citizen != null && citizen.soldier();
                    button.active = citizen != null && !soldier && (i != 0 || !citizen.profession().equals(Professions.UNEMPLOYED.id()));
                    if (soldier) {
                        button.setTooltip(Tooltip.create(Component.translatable(KEY + "soldier_hint")));
                    } else {
                        button.setTooltip(Tooltip.create(Component.translatable(i == 0 ? KEY + "dismiss_hint" : KEY + "assign_hint")));
                    }
                }
                case POSTS -> {
                    button.active = post != null && switch (i) {
                        case 1 -> !post.profession().equals(Professions.UNEMPLOYED.id()) && post.freeSeats() > 0;
                        case 2 -> post.workers() > 0;
                        default -> true;
                    };
                    String hint = switch (i) {
                        case 0 -> "settings_hint";
                        case 1 -> post != null && post.profession().equals(Professions.UNEMPLOYED.id()) ? "put_no_profession" : "put_hint";
                        case 2 -> "clear_hint";
                        default -> "rename_hint";
                    };
                    button.setTooltip(Tooltip.create(Component.translatable(KEY + hint)));
                }
                case CHOOSE_POST -> button.active = post != null && view != null && view.forCitizen() != null;
                case CHOOSE_CITIZEN -> button.active = citizen != null && view != null && view.forPost() != null;
            }
        }
    }

    private void action(int index) {
        switch (query.mode()) {
            case CITIZENS -> {
                WorkforceView.CitizenRow citizen = selectedCitizen();
                if (citizen == null) {
                    return;
                }
                if (index == 0) {
                    act(WorkforcePackets.Action.Kind.DISMISS, citizen.id(), null, "");
                } else {
                    back = query;
                    show(WorkforceView.Query.choosePost(citizen.id(), visibleRows()));
                }
            }
            case POSTS -> {
                WorkforceView.PostRow post = selectedPost();
                if (post == null) {
                    return;
                }
                switch (index) {
                    case 0 -> openPost(post.ref());
                    case 1 -> {
                        back = query;
                        show(WorkforceView.Query.chooseCitizen(post.ref(), visibleRows()));
                    }
                    case 2 -> act(WorkforcePackets.Action.Kind.CLEAR, null, post.ref(), "");
                    default -> {
                        if (minecraft != null && kingdomId != null) {
                            minecraft.setScreen(new RenamePostScreen(this, post.ref(), post.name(),
                                    postName("", post.profession(), post.number()).getString()));
                        }
                    }
                }
            }
            case CHOOSE_POST -> {
                WorkforceView.PostRow post = selectedPost();
                WorkforceView.CitizenRow who = view == null ? null : view.forCitizen();
                if (post != null && who != null) {
                    act(WorkforcePackets.Action.Kind.ASSIGN, who.id(), post.ref(), "");
                }
            }
            case CHOOSE_CITIZEN -> {
                WorkforceView.CitizenRow citizen = selectedCitizen();
                WorkforceView.PostRow post = view == null ? null : view.forPost();
                if (citizen == null || post == null) {
                    return;
                }
                if (citizen.candidate().transfer() && minecraft != null) {
                    // Taking a worker from other work is asked once more.
                    Component from = citizen.workplace() != null && citizen.workplace().post()
                            ? postName(citizen.workplace().name(), citizen.workplace().profession(), citizen.workplace().number())
                            : CitizenTexts.profession(citizen.profession(), gender(citizen.female()));
                    minecraft.setScreen(new ConfirmScreen(yes -> {
                        minecraft.setScreen(this);
                        if (yes) {
                            act(WorkforcePackets.Action.Kind.ASSIGN, citizen.id(), post.ref(), "");
                        }
                    }, Component.translatable(KEY + "transfer.title"), Component.translatable(KEY + "transfer.question",
                            CitizenTexts.name(citizen.seed(), gender(citizen.female())), from, postName(post.name(), post.profession(), post.number()),
                            CitizenTexts.profession(post.profession(), gender(citizen.female()))),
                            Component.translatable(KEY + "transfer.yes"), Component.translatable("gui.cancel")));
                    return;
                }
                act(WorkforcePackets.Action.Kind.ASSIGN, citizen.id(), post.ref(), "");
            }
        }
    }

    /**
     * Opens the window of a post from afar; the server answers with it.
     */
    private void openPost(WorkforceView.PostRef ref) {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new WorkPostActionPacket(ref.pos(), WorkPostActionPacket.Action.OPEN, 0, "", null, ref.dimension()));
        }
    }

    /**
     * Gives a post a new name from the window of renaming; empty gives its default name back.
     */
    void rename(WorkforceView.PostRef ref, String name) {
        act(WorkforcePackets.Action.Kind.RENAME, null, ref, name);
    }

    @Override
    public void onResult(boolean success, Component message) {
        status = message;
        statusIsError = !success;
        if (success && query.mode().choice()) {
            // The choice is made: back to the list or the window it came from.
            onClose();
        }
    }

    @Override
    public void onClose() {
        if (query.mode().choice() && back != null) {
            WorkforceView.Query list = back;
            back = null;
            show(list);
            return;
        }
        if (minecraft == null) {
            return;
        }
        if (parent instanceof WorkPostScreen post) {
            post.refresh();
        }
        minecraft.setScreen(parent);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int index = rowAt(mouseX, mouseY);
        if (button != 0 || index < 0) {
            return false;
        }
        Object key = rowKey(index);
        if (key == null) {
            return false;
        }
        long now = net.minecraft.Util.getMillis();
        boolean twice = key.equals(lastClicked) && now - lastClick < DOUBLE_CLICK_MILLIS;
        lastClick = now;
        lastClicked = key;
        selected = key;
        updateActions();
        if (twice && !actions.isEmpty()) {
            int first = query.mode() == WorkforceView.Mode.CITIZENS ? 1 : 0;
            if (actions.get(first).active) {
                action(first);
            }
        }
        return true;
    }

    private int rowAt(double mouseX, double mouseY) {
        int left = left() + PAD;
        int right = left() + panelWidth - PAD;
        if (mouseX < left || mouseX >= right || mouseY < listTop()) {
            return -1;
        }
        int index = (int) ((mouseY - listTop()) / ROW);
        return index < visibleRows() && index < rowCount() ? index : -1;
    }

    private int rowCount() {
        if (view == null) {
            return 0;
        }
        return usesCitizens() ? view.citizens().size() : view.posts().size();
    }

    private boolean usesCitizens() {
        return query.mode() == WorkforceView.Mode.CITIZENS || query.mode() == WorkforceView.Mode.CHOOSE_CITIZEN;
    }

    @Nullable
    private Object rowKey(int index) {
        if (view == null || index < 0 || index >= rowCount()) {
            return null;
        }
        return usesCitizens() ? view.citizens().get(index).id() : view.posts().get(index).ref();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        page(delta > 0 ? -1 : 1);
        return true;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = left();
        int top = top();
        int x = left + PAD;
        int right = left + panelWidth - PAD;
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF0201810);
        graphics.renderOutline(left, top, panelWidth, panelHeight, 0xFFB08850);

        Component title = titleText();
        if (query.mode().choice()) {
            centeredName(graphics, title.getString(), width / 2, top + TOP_ROW + 3, 0xFFF0D070, panelWidth - 2 * PAD);
        } else {
            int titleLeft = tabsRight() + 6;
            String shown = fit(title.getString(), right - titleLeft);
            name(graphics, title.getString(), right - font.width(shown), top + TOP_ROW + 3, 0xFFF0D070, right - titleLeft);
        }

        int summaryY = top + SUMMARY;
        if (query.mode().choice()) {
            fittedLabel(graphics, targetLine(), x, summaryY, 0xFFE8E8E8, right);
            fittedLabel(graphics, hintLine(), x, summaryY + LINE, 0xFFB8B8B8, right);
        } else {
            fittedLabel(graphics, countsLine(), x, summaryY, 0xFFE8E8E8, right);
            Component shortages = shortageLine();
            int color = view != null && !view.header().shortages().isEmpty() ? 0xFFFFC080 : 0xFFA0E0A0;
            fittedLabel(graphics, shortages, x, summaryY + LINE, color, right);
            // Every shortage on its own line on hover.
            for (Component line : shortageLines()) {
                hoverText(x, summaryY + LINE, right - x, line);
            }
        }

        if (query.mode() == WorkforceView.Mode.CHOOSE_POST && view != null) {
            label(graphics, Component.translatable(KEY + "choose_post.without_post"), x, top + FILTERS + 4, 0xFFE8E8E8, right);
        }
        Component page = pageLabel();
        int pageRight = right - 2 * ARROW - GAP - 4;
        label(graphics, page, pageRight - font.width(page), top + FILTERS + 4, 0xFF9A9A9A, pageRight);

        renderList(graphics, x, right, mouseX, mouseY);

        Component bottom = status;
        int color = statusIsError ? 0xFFFF8080 : 0xFFA0E0A0;
        if (bottom.getString().isEmpty()) {
            bottom = Component.translatable(usesCitizens() ? KEY + "select_citizen" : KEY + "select_post");
            color = 0xFFB8B8B8;
        }
        fittedLabel(graphics, bottom, x, top + panelHeight - BOTTOM_AREA + 2, color, right);
    }

    private void renderList(GuiGraphics graphics, int x, int right, int mouseX, int mouseY) {
        int top = listTop();
        if (view == null) {
            label(graphics, Component.translatable(KEY + "loading"), x, top + 6, 0xFFB8B8B8, right);
            return;
        }
        int rows = Math.min(visibleRows(), rowCount());
        if (rows == 0) {
            Component empty = waiting ? Component.translatable(KEY + "loading") : Component.translatable(KEY + "empty." + query.mode().id());
            paragraph(graphics, empty, x, top + 6, right - x, 2, 0xFFB8B8B8);
            return;
        }
        int hovered = rowAt(mouseX, mouseY);
        for (int i = 0; i < rows; i++) {
            int y = top + i * ROW;
            Object key = rowKey(i);
            if (key != null && key.equals(selected)) {
                graphics.fill(x - 2, y, right + 2, y + ROW - 1, 0x50F0D070);
            } else if (i == hovered) {
                graphics.fill(x - 2, y, right + 2, y + ROW - 1, 0x20FFFFFF);
            } else if (i % 2 == 1) {
                graphics.fill(x - 2, y, right + 2, y + ROW - 1, 0x10FFFFFF);
            }
            if (usesCitizens()) {
                renderCitizen(graphics, view.citizens().get(i), x, y, right);
            } else {
                renderPost(graphics, view.posts().get(i), x, y, right);
            }
        }
    }

    private void renderCitizen(GuiGraphics graphics, WorkforceView.CitizenRow row, int x, int y, int right) {
        Gender gender = gender(row.female());
        int first = y + 2;
        int second = y + 11;
        name(graphics, CitizenTexts.name(row.seed(), gender).getString(), x, first, 0xFFFFFFFF, NAME_COLUMN - 4);
        int professionX = x + NAME_COLUMN;
        int professionColor = row.soldier() ? 0xFFB0B0F0 : row.profession().equals(Professions.UNEMPLOYED.id()) ? 0xFFB8B8B8 : 0xFFF0D070;
        name(graphics, CitizenTexts.profession(row.profession(), gender).getString(), professionX, first, professionColor, PROFESSION_COLUMN - 4);
        int placeX = professionX + PROFESSION_COLUMN;
        name(graphics, workplaceText(row).getString(), placeX, first, row.workplace() == null ? 0xFFB8B8B8 : 0xFFD0D0D0, right - placeX);

        if (query.mode() == WorkforceView.Mode.CHOOSE_CITIZEN) {
            Component group = candidateText(row);
            Component far = distance(row.distance());
            int farX = right - font.width(far);
            label(graphics, far, farX, second, 0xFF9A9A9A, right);
            name(graphics, group.getString(), x, second, row.candidate().transfer() ? 0xFFFFC080 : 0xFFA0E0A0, farX - 6 - x);
            return;
        }
        Component vitals = Component.translatable(KEY + "vitals", row.health(), row.mood());
        int vitalsX = right - font.width(vitals);
        int healthColor = row.state() == CitizenState.WOUNDED ? 0xFFFF8080 : row.mood() < 30 ? 0xFFFFC080 : 0xFFA0E0A0;
        label(graphics, vitals, vitalsX, second, healthColor, right);
        hoverText(vitalsX, second, font.width(vitals), Component.translatable(KEY + "vitals_hint", row.health(), row.maxHealth(), row.mood()));
        int doingWidth = Math.min(DOING_COLUMN, (vitalsX - 6 - x) / 2 + 30);
        name(graphics, doingText(row).getString(), x, second, doingColor(row), doingWidth - 4);
        int lacksX = x + doingWidth;
        if (!row.lacks().isEmpty()) {
            MutableComponent list = Component.empty();
            for (int i = 0; i < row.lacks().size(); i++) {
                if (i > 0) {
                    list.append(Component.translatable(KEY + "list_separator"));
                }
                list.append(lackText(row.lacks().get(i).need(), row.lacks().get(i).detail()));
            }
            name(graphics, Component.translatable(KEY + "lacks", list).getString(), lacksX, second, 0xFFFFB070, vitalsX - 6 - lacksX);
        }
    }

    /**
     * Where a citizen works and how far it is: the post, the composter of a farmer, or that it has none.
     */
    private Component workplaceText(WorkforceView.CitizenRow row) {
        WorkforceView.Workplace workplace = row.workplace();
        if (workplace == null) {
            if (row.soldier()) {
                return Component.translatable(KEY + "in_army");
            }
            if (row.profession().equals(Professions.UNEMPLOYED.id())) {
                return Component.translatable(KEY + "no_work");
            }
            return Component.translatable(Professions.worksAtPosts(row.profession()) ? KEY + "no_post" : KEY + "no_post_needed");
        }
        Component where = workplace.post() ? postName(workplace.name(), workplace.profession(), workplace.number())
                : Component.translatable(KEY + "composter");
        return Component.translatable(KEY + "workplace", where, distance(workplace.distance()));
    }

    private Component candidateText(WorkforceView.CitizenRow row) {
        return switch (row.candidate()) {
            case IDLE_SAME -> Component.translatable(KEY + "candidate.idle_same",
                    CitizenTexts.profession(row.profession(), gender(row.female())));
            case UNEMPLOYED -> Component.translatable(KEY + "candidate.unemployed");
            case SAME_ELSEWHERE -> Component.translatable(KEY + "candidate.same_elsewhere");
            default -> Component.translatable(KEY + "candidate.other_work", CitizenTexts.profession(row.profession(), gender(row.female())));
        };
    }

    private void renderPost(GuiGraphics graphics, WorkforceView.PostRow row, int x, int y, int right) {
        int first = y + 2;
        int second = y + 11;
        int nameWidth = Math.max(NAME_COLUMN, Math.min(140, (right - x) / 3));
        name(graphics, postName(row.name(), row.profession(), row.number()).getString(), x, first, 0xFFF0D070, nameWidth - 4);
        int professionX = x + nameWidth;
        boolean none = row.profession().equals(Professions.UNEMPLOYED.id());
        Component profession = none ? Component.translatable(KEY + "no_profession") : CitizenTexts.profession(row.profession());
        name(graphics, profession.getString(), professionX, first, none ? 0xFFFF9070 : 0xFFE8E8E8, PROFESSION_COLUMN - 4);
        int seatsX = professionX + PROFESSION_COLUMN;
        Component seats = query.mode() == WorkforceView.Mode.CHOOSE_POST ? Component.translatable(KEY + "free_seats", row.freeSeats())
                : Component.translatable(KEY + "seats", row.workers(), row.seats());
        int seatsWidth = Math.max(font.width(seats), font.width(Component.translatable(KEY + "seats", 16, 16)));
        label(graphics, seats, seatsX, first, row.workers() == 0 && !none ? 0xFFFF9070 : 0xFFD0D0D0, seatsX + seatsWidth);
        int placeX = seatsX + seatsWidth + 6;
        Component where = Component.translatable(KEY + "post_place", placeName(row.place()), distance(row.distance()));
        name(graphics, where.getString(), placeX, first, 0xFFB8B8B8, right - placeX);
        hoverText(placeX, first, Math.max(0, right - placeX), Component.translatable(KEY + "coordinates", row.ref().pos().getX(),
                row.ref().pos().getY(), row.ref().pos().getZ()));

        int statusWidth = Math.min(DOING_COLUMN + 30, (right - x) / 2);
        name(graphics, statusText(row.status()).getString(), x, second, statusColor(row.status()), statusWidth - 4);
        int contentsX = x + statusWidth;
        Component contents = contentsText(row);
        name(graphics, contents.getString(), contentsX, second, 0xFFC8C8C8, right - contentsX);
        if (!row.contents().isEmpty()) {
            for (WorkforceView.Content content : row.contents()) {
                hoverText(contentsX, second, Math.max(0, right - contentsX), Component.translatable(KEY + "content",
                        content.item().getHoverName(), content.count()));
            }
        }
    }

    /**
     * What the container of a post holds, briefly: its two largest kinds and how many more.
     */
    private Component contentsText(WorkforceView.PostRow row) {
        if (!row.loaded()) {
            return Component.translatable(row.container() ? KEY + "container_far" : KEY + "no_container_far");
        }
        if (!row.container()) {
            return Component.translatable(KEY + "no_container");
        }
        if (row.contents().isEmpty()) {
            return Component.translatable(KEY + "container_empty");
        }
        MutableComponent list = Component.empty();
        int shown = 0;
        int counted = 0;
        for (WorkforceView.Content content : row.contents()) {
            if (shown == 2) {
                break;
            }
            if (shown > 0) {
                list.append(Component.translatable(KEY + "list_separator"));
            }
            list.append(Component.translatable(KEY + "content", content.item().getHoverName(), content.count()));
            counted += content.count();
            shown++;
        }
        int rest = row.items() - counted;
        return rest > 0 ? Component.translatable(KEY + "container_more", list, rest) : Component.translatable(KEY + "container", list);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
