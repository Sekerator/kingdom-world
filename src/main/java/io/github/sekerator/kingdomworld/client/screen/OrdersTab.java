package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.core.Cycle;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.CatalogView;
import io.github.sekerator.kingdomworld.common.network.data.OrdersView;
import io.github.sekerator.kingdomworld.common.network.packet.OrderPackets;
import io.github.sekerator.kingdomworld.core.production.Order;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The "Orders" tab of the throne screen: the king's orders with their sub-orders under them, how far each is and
 * what is missing, and for members with the right to manage production a way to give orders (an item found by
 * name, the amount, keep or make, the priority), change, pause and cancel them. Every change is an intent the
 * server checks; it answers with a fresh list.
 */
final class OrdersTab {
    static final int ROW = 22;
    static final int ITEM_ROW = 18;
    private static final int LINE = 10;
    private static final int GAP = 2;
    private static final int SEARCH_WIDTH = 120;
    private static final int AMOUNT_WIDTH = 46;
    private static final int SMALL = 18;
    private static final int INDENT = 8;
    /** Ticks without typing before a search is sent. */
    private static final int SEARCH_DELAY = 6;
    private static final String KEY = "kingdomworld.screen.orders.";
    private static final String ORDER = "kingdomworld.order.";

    enum Mode {
        LIST,
        CREATE,
        EDIT
    }

    private final ThroneScreen screen;
    private OrdersView view;
    private CatalogView catalog;
    private Mode mode = Mode.LIST;
    private int scroll;
    private String search = "";
    private int searchTimer = -1;
    private int page;
    @Nullable
    private ItemStack selected;
    @Nullable
    private String selectedProfession;
    private long editing;
    private String amount = "1";
    private Order.Kind kind = Order.Kind.MAKE;
    private Order.Priority priority = Order.Priority.NORMAL;
    @Nullable
    private Set<Item> craftable;
    @Nullable
    private EditBox amountBox;

    private int priorityWidth;
    private int pauseWidth;
    private int cancelWidth;
    private int newWidth;
    private int kindWidth;
    private int createWidth;
    private int backWidth;
    private int arrowWidth;

    OrdersTab(ThroneScreen screen) {
        this.screen = screen;
    }

    OrdersView view() {
        return view;
    }

    void setView(OrdersView view) {
        this.view = view;
    }

    void setCatalog(CatalogView catalog) {
        if (view == null || catalog.kingdomId().equals(view.kingdomId())) {
            this.catalog = catalog;
            this.page = catalog.page();
        }
    }

    String checkName() {
        return "orders-" + mode.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Shows a mode of the tab, for the layout check: the list, the creation of an order with a search, or the
     * change of an order.
     */
    void showForCheck(Mode value, String searchText, @Nullable ItemStack choice, @Nullable String profession) {
        mode = value;
        search = searchText;
        selected = choice;
        selectedProfession = profession;
        if (value == Mode.EDIT) {
            editing = 1;
            if (view != null) {
                for (OrdersView.Entry entry : view.orders()) {
                    if (!entry.isSubOrder()) {
                        editing = entry.id();
                        selected = entry.sample();
                        break;
                    }
                }
            }
        }
        amount = "100000";
    }

    // ---------------------------------------------------------------- texts

    private static Component itemName(ItemStack stack) {
        return stack.getHoverName();
    }

    private Component summary() {
        return Component.translatable(KEY + "summary", view.kingOrders(), view.maxOrders());
    }

    private static Component kindButton(Order.Kind kind) {
        return Component.translatable(KEY + "kind_button." + kind.id());
    }

    private static Component priorityName(Order.Priority priority) {
        return Component.translatable(ORDER + "priority." + priority.id());
    }

    private static Component pauseLabel(boolean paused) {
        return Component.translatable(paused ? KEY + "resume" : KEY + "pause");
    }

    static Component progress(OrdersView.Entry entry) {
        String key = entry.isSubOrder() ? "progress.sub" : "progress." + entry.kind().id();
        return Component.translatable(KEY + key, entry.progress(), entry.amount());
    }

    /**
     * The status of an order with what it lacks: "Not enough: raw iron x2 (gathered by: Miner)".
     */
    static Component status(OrdersView.Entry entry) {
        MutableComponent text = Component.translatable(ORDER + "status." + entry.status().id());
        if (entry.status() == Order.Status.NO_WORKSHOP && !entry.workshop().isEmpty()) {
            return Component.translatable(KEY + "needs_workshop", inSentence(CitizenTexts.profession(entry.workshop())),
                    Component.translatable("kingdomworld.station." + entry.workshopStation()));
        }
        if (!entry.missing().isEmpty() && (entry.status() == Order.Status.MISSING || entry.status() == Order.Status.WAITING_FOR_PARTS)) {
            MutableComponent list = Component.empty();
            for (int i = 0; i < entry.missing().size(); i++) {
                OrdersView.Missing missing = entry.missing().get(i);
                if (i > 0) {
                    list.append(Component.translatable(KEY + "separator"));
                }
                list.append(missing.gatherer().isEmpty()
                        ? Component.translatable(KEY + "missing", itemName(missing.sample()), missing.count())
                        : Component.translatable(KEY + "missing_gathered", itemName(missing.sample()), missing.count(),
                        inSentence(CitizenTexts.profession(missing.gatherer()))));
            }
            return Component.translatable(KEY + "status_missing", text, list);
        }
        return text;
    }

    /**
     * A profession named inside a sentence starts with a small letter.
     */
    private static Component inSentence(Component name) {
        String text = name.getString();
        return text.isEmpty() ? name : Component.literal(text.substring(0, 1).toLowerCase(Locale.ROOT) + text.substring(1));
    }

    static int statusColor(Order.Status status) {
        if (status == Order.Status.WORKING || status == Order.Status.SATISFIED) {
            return 0xFFA0E0A0;
        }
        return status.isProblem() ? 0xFFFF9070 : 0xFFE0D090;
    }

    private static Component professionName(String profession) {
        return CitizenTexts.profession(profession);
    }

    // ---------------------------------------------------------------- layout

    int measure(Font font) {
        priorityWidth = 0;
        for (Order.Priority value : Order.Priority.values()) {
            priorityWidth = Math.max(priorityWidth, screen.buttonWidth(priorityName(value)));
        }
        pauseWidth = Math.max(screen.buttonWidth(pauseLabel(true)), screen.buttonWidth(pauseLabel(false)));
        cancelWidth = screen.buttonWidth(Component.translatable(KEY + "cancel"));
        newWidth = screen.buttonWidth(Component.translatable(KEY + "new"));
        kindWidth = Math.max(screen.buttonWidth(kindButton(Order.Kind.KEEP)), screen.buttonWidth(kindButton(Order.Kind.MAKE)));
        createWidth = Math.max(screen.buttonWidth(Component.translatable(KEY + "create")), screen.buttonWidth(Component.translatable(KEY + "save")));
        backWidth = screen.buttonWidth(Component.translatable(KEY + "back"));
        arrowWidth = screen.buttonWidth(Component.literal("<"));
        int buttons = priorityWidth + pauseWidth + cancelWidth + 2 * GAP;
        int list = 18 + 90 + 8 + font.width(Component.translatable(KEY + "progress.make", 100_000, 100_000)) + 6 + buttons;
        int top = newWidth + 8 + font.width(Component.translatable(KEY + "summary", 256, 256)) + 8
                + font.width(Component.translatable("kingdomworld.screen.throne.page", 500, 500, 500));
        int controls = SMALL + GAP + AMOUNT_WIDTH + GAP + SMALL + 6 + kindWidth + GAP + priorityWidth + 6 + createWidth + GAP + backWidth;
        int searchRow = SEARCH_WIDTH + 6 + 2 * arrowWidth + GAP + 8 + font.width(Component.translatable(KEY + "catalog_page", 9_999, 9_999));
        int messages = 0;
        for (String key : List.of("loading", "empty", "catalog_loading", "catalog_empty", "choose")) {
            messages = Math.max(messages, Math.min(300, font.width(Component.translatable(KEY + key))));
        }
        return Math.max(Math.max(list, top), Math.max(Math.max(controls, searchRow), messages));
    }

    private static int listTop(int top) {
        return top + ThroneScreen.CONTENT_TOP + 20;
    }

    private static int visibleRows(int top, int panelHeight) {
        return Math.max(1, (top + panelHeight - ThroneScreen.BOTTOM_AREA - listTop(top)) / ROW);
    }

    private static int controlsY(int top, int panelHeight) {
        return top + panelHeight - ThroneScreen.BOTTOM_AREA - ROW;
    }

    /**
     * Where the controls of the form that changes an order stand: right under the order, with its progress and status.
     */
    private static int editControlsY(int top) {
        return listTop(top) + ITEM_ROW + 2 * (LINE + 2) + 6;
    }

    private int formControlsY(int top, int panelHeight) {
        return mode == Mode.EDIT ? Math.min(editControlsY(top), controlsY(top, panelHeight)) : controlsY(top, panelHeight);
    }

    /**
     * The order the form changes, if the list still has it.
     */
    @Nullable
    private OrdersView.Entry edited() {
        if (view == null) {
            return null;
        }
        for (OrdersView.Entry entry : view.orders()) {
            if (entry.id() == editing) {
                return entry;
            }
        }
        return null;
    }

    private static int chosenY(int top, int panelHeight) {
        return controlsY(top, panelHeight) - ITEM_ROW;
    }

    /**
     * Catalog rows that fit between the search and the chosen item.
     */
    private static int catalogRows(int top, int panelHeight) {
        return Math.max(1, (chosenY(top, panelHeight) - 2 - listTop(top)) / ITEM_ROW);
    }

    // ---------------------------------------------------------------- widgets

    void init(int left, int top, int panelWidth, int panelHeight) {
        if (view == null) {
            return;
        }
        int x = left + KingdomScreen.PAD;
        int right = left + panelWidth - KingdomScreen.PAD;
        int y = top + ThroneScreen.CONTENT_TOP;
        if (!view.canManage() && mode != Mode.LIST) {
            mode = Mode.LIST;
        }
        if (mode == Mode.LIST) {
            if (view.canManage()) {
                Button create = screen.add(Button.builder(Component.translatable(KEY + "new"), b -> openCreate())
                        .bounds(x, y, newWidth, ITEM_ROW - 2).build());
                create.active = view.kingOrders() < view.maxOrders();
            }
            initList(left, top, panelWidth, panelHeight, right);
            return;
        }
        if (mode == Mode.CREATE) {
            EditBox box = new EditBox(screen.font(), x + 1, y + 1, SEARCH_WIDTH - 2, ITEM_ROW - 4, Component.translatable(KEY + "search"));
            box.setMaxLength(CatalogView.MAX_SEARCH);
            box.setValue(search);
            box.setHint(Component.translatable(KEY + "search"));
            box.setResponder(value -> {
                if (!value.equals(search)) {
                    search = value;
                    page = 0;
                    searchTimer = SEARCH_DELAY;
                }
            });
            screen.add(box);
            int pages = catalog == null ? 1 : catalog.pages();
            Button previous = screen.add(Button.builder(Component.literal("<"), b -> requestCatalog(page - 1))
                    .bounds(x + SEARCH_WIDTH + 6, y, arrowWidth, ITEM_ROW - 2).build());
            previous.active = page > 0;
            Button next = screen.add(Button.builder(Component.literal(">"), b -> requestCatalog(page + 1))
                    .bounds(x + SEARCH_WIDTH + 6 + arrowWidth + GAP, y, arrowWidth, ITEM_ROW - 2).build());
            next.active = page + 1 < pages;
        }
        initControls(left, top, panelWidth, panelHeight);
    }

    private void initList(int left, int top, int panelWidth, int panelHeight, int right) {
        List<OrdersView.Entry> orders = view.orders();
        int rows = visibleRows(top, panelHeight);
        scroll = Math.max(0, Math.min(scroll, orders.size() - rows));
        if (!view.canManage()) {
            return;
        }
        for (int i = 0; i < rows && scroll + i < orders.size(); i++) {
            OrdersView.Entry entry = orders.get(scroll + i);
            if (entry.isSubOrder()) {
                continue;
            }
            int y = listTop(top) + i * ROW + 1;
            int cancelX = right - cancelWidth;
            int pauseX = cancelX - GAP - pauseWidth;
            int priorityX = pauseX - GAP - priorityWidth;
            long id = entry.id();
            Button change = screen.add(new CycleButton(priorityX, y, priorityWidth, ROW - 4, priorityName(entry.priority()),
                    direction -> send(OrderPackets.Change.Action.PRIORITY, id, entry.kind(), 0, Cycle.step(entry.priority(), direction))));
            change.setTooltip(CycleButton.hinted(Component.translatable(KEY + "priority_hint")));
            screen.add(Button.builder(pauseLabel(entry.paused()), b -> send(entry.paused() ? OrderPackets.Change.Action.RESUME
                            : OrderPackets.Change.Action.PAUSE, id, entry.kind(), 0, entry.priority()))
                    .bounds(pauseX, y, pauseWidth, ROW - 4).build());
            screen.add(Button.builder(Component.translatable(KEY + "cancel"), b -> send(OrderPackets.Change.Action.CANCEL, id, entry.kind(), 0,
                    entry.priority())).bounds(cancelX, y, cancelWidth, ROW - 4).build());
        }
    }

    /**
     * Amount, keep or make, priority, and the buttons to give the order (or save the change) and to go back.
     */
    private void initControls(int left, int top, int panelWidth, int panelHeight) {
        int x = left + KingdomScreen.PAD;
        int y = formControlsY(top, panelHeight);
        screen.add(Button.builder(Component.literal("-"), b -> stepAmount(-1)).bounds(x, y, SMALL, ITEM_ROW - 2).build());
        EditBox box = new EditBox(screen.font(), x + SMALL + GAP + 1, y + 1, AMOUNT_WIDTH - 2, ITEM_ROW - 4,
                Component.translatable(KEY + "amount"));
        box.setMaxLength(6);
        box.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        box.setValue(amount);
        box.setResponder(value -> amount = value);
        amountBox = screen.add(box);
        screen.add(Button.builder(Component.literal("+"), b -> stepAmount(1))
                .bounds(x + SMALL + GAP + AMOUNT_WIDTH + GAP, y, SMALL, ITEM_ROW - 2).build());
        int kindX = x + 2 * SMALL + 2 * GAP + AMOUNT_WIDTH + 6;
        screen.add(Button.builder(kindButton(kind), b -> {
            kind = kind == Order.Kind.MAKE ? Order.Kind.KEEP : Order.Kind.MAKE;
            screen.rebuild();
        }).bounds(kindX, y, kindWidth, ITEM_ROW - 2).build());
        int priorityX = kindX + kindWidth + GAP;
        Button rank = screen.add(new CycleButton(priorityX, y, priorityWidth, ITEM_ROW - 2, priorityName(priority), direction -> {
            priority = Cycle.step(priority, direction);
            screen.rebuild();
        }));
        rank.setTooltip(CycleButton.hinted(Component.translatable(KEY + "priority_hint")));
        int right = left + panelWidth - KingdomScreen.PAD;
        Button back = screen.add(Button.builder(Component.translatable(KEY + "back"), b -> {
            mode = Mode.LIST;
            screen.rebuild();
        }).bounds(right - backWidth, y, backWidth, ITEM_ROW - 2).build());
        Component action = Component.translatable(mode == Mode.EDIT ? KEY + "save" : KEY + "create");
        Button create = screen.add(Button.builder(action, b -> submit())
                .bounds(right - backWidth - GAP - createWidth, y, createWidth, ITEM_ROW - 2).build());
        create.active = selected != null && parsedAmount() > 0;
        back.active = true;
    }

    private int parsedAmount() {
        try {
            int value = Integer.parseInt(amount);
            return value >= 1 && value <= Order.MAX_AMOUNT ? value : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void stepAmount(int delta) {
        int value = Math.max(1, Math.min(Order.MAX_AMOUNT, (parsedAmount() == 0 ? 1 : parsedAmount()) + delta));
        amount = String.valueOf(value);
        screen.rebuild();
    }

    private void openCreate() {
        mode = Mode.CREATE;
        selected = null;
        selectedProfession = null;
        amount = "1";
        kind = Order.Kind.MAKE;
        priority = Order.Priority.NORMAL;
        page = 0;
        requestCatalog(0);
        screen.rebuild();
    }

    private void openEdit(OrdersView.Entry entry) {
        mode = Mode.EDIT;
        editing = entry.id();
        selected = entry.sample();
        selectedProfession = null;
        amount = String.valueOf(entry.amount());
        kind = entry.kind();
        priority = entry.priority();
        screen.rebuild();
    }

    private void submit() {
        int value = parsedAmount();
        if (selected == null || value <= 0 || view == null) {
            return;
        }
        if (mode == Mode.EDIT) {
            send(OrderPackets.Change.Action.UPDATE, editing, kind, value, priority);
        } else {
            String item = String.valueOf(ForgeRegistries.ITEMS.getKey(selected.getItem()));
            KingdomWorldNetwork.sendToServer(new OrderPackets.Change(view.kingdomId(), OrderPackets.Change.Action.CREATE, 0, item, kind,
                    value, priority));
        }
        mode = Mode.LIST;
        screen.rebuild();
    }

    private void send(OrderPackets.Change.Action action, long id, Order.Kind kind, int amount, Order.Priority priority) {
        if (view != null && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new OrderPackets.Change(view.kingdomId(), action, id, "", kind, amount, priority));
        }
    }

    // ---------------------------------------------------------------- the catalog

    /**
     * Asks the server for a page of what can be ordered. The server cannot read the names of items in the player's
     * language, so the client sends the items whose names match.
     */
    private void requestCatalog(int wanted) {
        if (view == null || Minecraft.getInstance().getConnection() == null) {
            return;
        }
        page = Math.max(0, wanted);
        int size = Math.min(CatalogView.MAX_PAGE_SIZE, catalogRows(screen.panelTop(), screen.panelHeight()));
        KingdomWorldNetwork.sendToServer(new OrderPackets.CatalogRequest(view.kingdomId(), search, page, size, hints(search)));
    }

    private List<Integer> hints(String text) {
        String wanted = text.trim().toLowerCase(Locale.ROOT);
        List<Integer> result = new ArrayList<>();
        if (wanted.isEmpty()) {
            return result;
        }
        for (Item item : craftable()) {
            if (result.size() >= OrderPackets.MAX_HINTS) {
                break;
            }
            if (new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT).contains(wanted)) {
                result.add(Item.getId(item));
            }
        }
        return result;
    }

    /**
     * Items some recipe the client knows makes: the search only needs to look at those.
     */
    private Set<Item> craftable() {
        if (craftable == null) {
            craftable = new HashSet<>();
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                for (Recipe<?> recipe : minecraft.level.getRecipeManager().getRecipes()) {
                    ItemStack result = recipe.getResultItem(minecraft.level.registryAccess());
                    if (result != null && !result.isEmpty()) {
                        craftable.add(result.getItem());
                    }
                }
            }
        }
        return craftable;
    }

    /**
     * Sends a search once the player stopped typing; called every tick.
     */
    void tick() {
        if (searchTimer > 0 && --searchTimer == 0) {
            searchTimer = -1;
            requestCatalog(0);
        }
    }

    boolean click(int left, int top, int panelWidth, int panelHeight, double mouseX, double mouseY) {
        int x = left + KingdomScreen.PAD;
        int right = left + panelWidth - KingdomScreen.PAD;
        if (view == null || mouseX < x || mouseX > right) {
            return false;
        }
        int listTop = listTop(top);
        if (mode == Mode.CREATE && catalog != null) {
            int rows = catalogRows(top, panelHeight);
            int row = (int) Math.floor((mouseY - listTop) / ITEM_ROW);
            if (mouseY >= listTop && row >= 0 && row < rows && row < catalog.entries().size()) {
                CatalogView.Entry entry = catalog.entries().get(row);
                selected = entry.sample();
                selectedProfession = entry.profession();
                screen.rebuild();
                return true;
            }
            return false;
        }
        if (mode == Mode.LIST && view.canManage()) {
            int rows = visibleRows(top, panelHeight);
            int row = (int) Math.floor((mouseY - listTop) / ROW);
            int buttonsX = right - cancelWidth - pauseWidth - priorityWidth - 2 * GAP;
            if (mouseY >= listTop && row >= 0 && row < rows && scroll + row < view.orders().size() && mouseX < buttonsX - 4) {
                OrdersView.Entry entry = view.orders().get(scroll + row);
                if (!entry.isSubOrder()) {
                    openEdit(entry);
                    return true;
                }
            }
        }
        return false;
    }

    boolean scroll(int top, int panelHeight, double delta) {
        if (mode == Mode.CREATE) {
            int pages = catalog == null ? 1 : catalog.pages();
            int next = Math.max(0, Math.min(pages - 1, page - (int) Math.signum(delta)));
            if (next != page) {
                requestCatalog(next);
                return true;
            }
            return false;
        }
        if (mode != Mode.LIST || view == null) {
            return false;
        }
        int max = Math.max(0, view.orders().size() - visibleRows(top, panelHeight));
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
        if (mode == Mode.LIST) {
            renderList(graphics, font, left, top, panelWidth, panelHeight, x, right, y);
        } else {
            renderCreate(graphics, font, top, panelHeight, x, right, y);
        }
    }

    private void renderList(GuiGraphics graphics, Font font, int left, int top, int panelWidth, int panelHeight, int x, int right, int y) {
        int summaryX = view.canManage() ? x + newWidth + 8 : x;
        List<OrdersView.Entry> orders = view.orders();
        int rows = visibleRows(top, panelHeight);
        int summaryRight = right;
        if (orders.size() > rows) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(orders.size(), scroll + rows),
                    orders.size());
            int pageX = right - font.width(page);
            screen.label(graphics, page, pageX, y + 4, 0xFF9A9A9A, right);
            summaryRight = pageX - 4;
        }
        screen.label(graphics, summary(), summaryX, y + 4, 0xFFE8E8E8, summaryRight);
        int listTop = listTop(top);
        if (orders.isEmpty()) {
            screen.paragraph(graphics, Component.translatable(KEY + "empty"), x, listTop + 5, right - x, 3, 0xFFB8B8B8);
            return;
        }
        int buttons = view.canManage() ? priorityWidth + pauseWidth + cancelWidth + 2 * GAP + 4 : 0;
        for (int i = 0; i < rows && scroll + i < orders.size(); i++) {
            OrdersView.Entry entry = orders.get(scroll + i);
            int rowY = listTop + i * ROW;
            int indent = Math.min(4, entry.depth()) * INDENT;
            int itemX = x + indent;
            if (entry.isSubOrder()) {
                graphics.fill(itemX - INDENT + 3, rowY + 2, itemX - INDENT + 4, rowY + 10, 0xFF706050);
                graphics.fill(itemX - INDENT + 3, rowY + 9, itemX - 1, rowY + 10, 0xFF706050);
            }
            graphics.renderItem(entry.sample(), itemX, rowY + 2);
            int textX = itemX + 20;
            int rowRight = entry.isSubOrder() ? right : right - buttons;
            Component amount = progress(entry);
            int amountX = rowRight - font.width(amount);
            screen.label(graphics, amount, amountX, rowY + 2, entry.paused() ? 0xFF9A9A9A : 0xFFFFFFFF, rowRight);
            screen.name(graphics, itemName(entry.sample()).getString(), textX, rowY + 2, entry.isSubOrder() ? 0xFFC8C8C8 : 0xFFFFFFFF,
                    amountX - 6 - textX);
            screen.name(graphics, status(entry).getString(), textX, rowY + 2 + LINE + 1, statusColor(entry.status()), rowRight - textX);
        }
    }

    /**
     * The form that changes an order: the order with its progress and status on top, the controls right under it
     * and what keep and make mean below.
     */
    private void renderEdit(GuiGraphics graphics, Font font, int top, int panelHeight, int x, int right, int y) {
        screen.label(graphics, Component.translatable(KEY + "editing"), x, y + 4, 0xFFF0D070, right);
        int itemY = listTop(top);
        OrdersView.Entry entry = edited();
        if (selected != null) {
            graphics.renderItem(selected, x, itemY);
            int nameRight = right;
            if (entry != null) {
                Component amountText = progress(entry);
                int amountX = right - font.width(amountText);
                screen.label(graphics, amountText, amountX, itemY + 5, entry.paused() ? 0xFF9A9A9A : 0xFFFFFFFF, right);
                nameRight = amountX - 6;
            }
            screen.name(graphics, itemName(selected).getString(), x + 20, itemY + 5, 0xFFF0D070, nameRight - x - 20);
        }
        if (entry != null) {
            screen.name(graphics, status(entry).getString(), x + 20, itemY + ITEM_ROW + 2, statusColor(entry.status()), right - x - 20);
        }
        int hintY = formControlsY(top, panelHeight) + ROW + 4;
        int lines = Math.max(0, (top + panelHeight - ThroneScreen.BOTTOM_AREA - hintY) / LINE);
        Component hint = Component.translatable(KEY + "edit_hint");
        // The hint only helps; where the panel is too low for all of it, it is left out rather than cut off.
        if (lines > 0 && font.split(hint, right - x).size() <= lines) {
            screen.paragraph(graphics, hint, x, hintY, right - x, lines, 0xFFB8B8B8);
        }
    }

    private void renderCreate(GuiGraphics graphics, Font font, int top, int panelHeight, int x, int right, int y) {
        int listTop = listTop(top);
        if (mode == Mode.CREATE) {
            if (catalog != null) {
                Component pageText = Component.translatable(KEY + "catalog_page", catalog.page() + 1, catalog.pages());
                int pageX = x + SEARCH_WIDTH + 6 + 2 * arrowWidth + GAP + 8;
                screen.label(graphics, pageText, pageX, y + 4, 0xFF9A9A9A, right);
            }
            if (catalog == null) {
                screen.label(graphics, Component.translatable(KEY + "catalog_loading"), x, listTop + 5, 0xFFB8B8B8, right);
            } else if (catalog.entries().isEmpty()) {
                screen.paragraph(graphics, Component.translatable(KEY + "catalog_empty"), x, listTop + 5, right - x, 2, 0xFFB8B8B8);
            } else {
                int rows = catalogRows(top, panelHeight);
                for (int i = 0; i < rows && i < catalog.entries().size(); i++) {
                    CatalogView.Entry entry = catalog.entries().get(i);
                    int rowY = listTop + i * ITEM_ROW;
                    boolean chosen = selected != null && ItemStack.isSameItem(selected, entry.sample());
                    if (chosen) {
                        graphics.fill(x - 2, rowY, right + 2, rowY + ITEM_ROW - 1, 0x40FFFFFF);
                    }
                    graphics.renderItem(entry.sample(), x, rowY);
                    Component profession = professionName(entry.profession());
                    int professionX = right - Math.min(font.width(profession), 110);
                    screen.name(graphics, profession.getString(), professionX, rowY + 5, 0xFF9AB0C8, right - professionX);
                    screen.name(graphics, itemName(entry.sample()).getString(), x + 20, rowY + 5, chosen ? 0xFFF0D070 : 0xFFE0E0E0,
                            professionX - 6 - (x + 20));
                }
            }
        } else {
            renderEdit(graphics, font, top, panelHeight, x, right, y);
            return;
        }
        int chosenY = chosenY(top, panelHeight);
        if (selected == null) {
            screen.label(graphics, Component.translatable(KEY + "choose"), x, chosenY + 5, 0xFFB8B8B8, right);
        } else {
            graphics.renderItem(selected, x, chosenY);
            Component name = selectedProfession == null ? itemName(selected)
                    : Component.translatable(KEY + "chosen", itemName(selected), professionName(selectedProfession));
            screen.name(graphics, name.getString(), x + 20, chosenY + 5, 0xFFF0D070, right - x - 20);
        }
    }
}
