package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.client.construction.BlueprintRenderer;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.construction.TemplateMaterials;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.ConstructionView;
import io.github.sekerator.kingdomworld.common.network.packet.BuildingPackets;
import io.github.sekerator.kingdomworld.common.network.packet.ConstructionPackets;
import io.github.sekerator.kingdomworld.core.construction.BuildingRoles;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.npc.ArchitecturalStyles;
import io.github.sekerator.kingdomworld.core.npc.TemplateCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The "Construction" tab of the throne screen, in four views: the catalog of building templates (with the cost of one, the
 * wood and stone to build it of and a button to take its blueprint), the blueprints the kingdom saved itself (renamed and
 * deleted here), the constructions under way (their kind, progress, state and what they lack, with priority, pause, orders
 * for missing materials and cancelling for members with the right) and the register of buildings (their roles and whether
 * they work, level and integrity, with upgrades, repairs and demolitions, each shown with its cost before it is confirmed).
 * Every change is an intent the server checks; it answers with a fresh view.
 */
final class ConstructionTab {
    static final int ROW = 20;
    private static final int LINE = 11;
    private static final int GAP = 2;
    private static final int CELL = 16;
    private static final String KEY = "kingdomworld.screen.construction.";
    private static final int ALL_STYLES = -2;
    private static final int OWN_STYLE = -1;
    /** The bar at the left edge of a row of the catalog in the style of the kingdom. */
    private static final int OWN_STYLE_COLOR = 0xFFE0B040;

    enum Mode {
        CATALOG,
        OWN,
        SITES,
        BUILDINGS;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final ThroneScreen screen;
    private ConstructionView view;
    private Mode mode = Mode.CATALOG;
    /** The template shown in detail, or empty for the list. */
    private String template = "";
    /** The construction shown in detail, or 0 for the list. */
    private long site;
    /** The building shown in detail, or 0 for the list. */
    private long building;
    /** The work on the building in detail waiting for confirmation, or {@code null}. */
    @Nullable
    private BuildingPackets.Change.Action action;
    private int scroll;
    /** The wood and the stone chosen for blueprints, as indices into the choices. */
    private int wood;
    private int stone;
    /**
     * The style the catalog shows: {@link #ALL_STYLES}, {@link #OWN_STYLE} or the index of an architectural style.
     */
    private int filter = ALL_STYLES;

    private int modeWidth;
    private int filterWidth;
    private int takeWidth;
    private int backWidth;
    private int priorityWidth;
    private int pauseWidth;
    private int orderWidth;
    private int cancelWidth;
    private int confirmWidth;

    ConstructionTab(ThroneScreen screen) {
        this.screen = screen;
    }

    ConstructionView view() {
        return view;
    }

    void setView(ConstructionView view) {
        this.view = view;
    }

    String checkName() {
        String detail = "";
        if ((mode == Mode.CATALOG || mode == Mode.OWN) && !template.isEmpty()) {
            detail = "-template";
        } else if (mode == Mode.SITES && site != 0) {
            detail = "-site";
        } else if (mode == Mode.BUILDINGS && building != 0) {
            detail = action == null ? "-building" : "-building-" + action.name().toLowerCase(Locale.ROOT);
        }
        return "construction-" + mode.id() + detail;
    }

    /**
     * Shows a view of the tab, for the layout check and the self-check: a template or a construction in detail when
     * given.
     */
    void showForCheck(Mode value, String templateId, long siteId) {
        showForCheck(value, templateId, siteId, 0, null);
    }

    /**
     * Shows a view of the tab with a building in detail and the work on it waiting for confirmation, when given.
     */
    void showForCheck(Mode value, String templateId, long siteId, long buildingId, @Nullable BuildingPackets.Change.Action work) {
        mode = value;
        template = templateId;
        site = siteId;
        building = buildingId;
        action = work;
        scroll = 0;
    }

    /**
     * Chooses the wood and stone by position in their lists, for the layout check.
     */
    void chooseForCheck(int woodIndex, int stoneIndex) {
        wood = Math.max(0, Math.min(woodChoices().size() - 1, woodIndex));
        stone = Math.max(0, Math.min(stoneChoices().size() - 1, stoneIndex));
    }

    /**
     * Filters the catalog by style, for the layout check: 0 shows all, 1 the style of the kingdom, 2 and on each architectural
     * style in turn.
     */
    void filterForCheck(int index) {
        filter = index <= 0 ? ALL_STYLES : index == 1 ? OWN_STYLE : Math.min(ArchitecturalStyles.IDS.size() - 1, index - 2);
        scroll = 0;
    }

    // ---------------------------------------------------------------- styles

    /**
     * The architectural style of the kingdom, or empty while the server sent none.
     */
    private String kingdomStyle() {
        return screen.view() == null ? "" : screen.view().style();
    }

    /**
     * The architectural style of a template, or empty for one that suits every style.
     */
    private static String styleOf(String templateId) {
        return TemplateCatalog.get(templateId).map(TemplateCatalog.Entry::style).orElse("");
    }

    /**
     * The filter that follows {@code value}: all, the kingdom's own style when it has one, then every style.
     */
    private int nextFilter(int value) {
        if (value == ALL_STYLES) {
            return kingdomStyle().isEmpty() ? 0 : OWN_STYLE;
        }
        if (value == OWN_STYLE) {
            return 0;
        }
        return value + 1 < ArchitecturalStyles.IDS.size() ? value + 1 : ALL_STYLES;
    }

    private static Component filterLabel(int value) {
        if (value == ALL_STYLES) {
            return Component.translatable(KEY + "filter.all");
        }
        if (value == OWN_STYLE) {
            return Component.translatable(KEY + "filter.own");
        }
        return Component.translatable(KEY + "filter.style", StylePicker.name(ArchitecturalStyles.id(value)));
    }

    private boolean shows(String style) {
        if (filter == ALL_STYLES) {
            return true;
        }
        if (filter == OWN_STYLE) {
            return !kingdomStyle().isEmpty() && style.equals(kingdomStyle());
        }
        return style.equals(ArchitecturalStyles.id(filter));
    }

    /**
     * The order of the groups of the catalog: the style of the kingdom first, then the templates of every style, then the other
     * styles in their order.
     */
    private int group(String style) {
        if (!style.isEmpty() && style.equals(kingdomStyle())) {
            return 0;
        }
        return style.isEmpty() ? 1 : 2 + Math.max(0, ArchitecturalStyles.index(style));
    }

    // ---------------------------------------------------------------- texts

    private static Component modeLabel(Mode value) {
        return Component.translatable(KEY + "mode", Component.translatable(KEY + "mode." + value.id()));
    }

    private static Component category(String id) {
        return Component.translatable("kingdomworld.building.category." + id);
    }

    private static Component priorityLabel(Construction.Priority priority) {
        return Component.translatable(KEY + "priority", Component.translatable("kingdomworld.order.priority." + priority.id()));
    }

    private static Component pauseLabel(boolean paused) {
        return Component.translatable(paused ? KEY + "resume" : KEY + "pause");
    }

    private static Component orderLabel(boolean ordering) {
        return Component.translatable(ordering ? KEY + "order_on" : KEY + "order_off");
    }

    private static Component autoLabel(boolean on) {
        return Component.translatable(on ? KEY + "auto_repair_on" : KEY + "auto_repair_off");
    }

    private static Component workLabel(BuildingPackets.Change.Action work) {
        return Component.translatable(KEY + "work." + work.name().toLowerCase(Locale.ROOT));
    }

    private static String coordinates(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    static Component status(ConstructionView.Site entry) {
        Component name = Component.translatable("kingdomworld.construction.status." + entry.status().id());
        if (entry.statusPos() != null) {
            return Component.translatable(KEY + "status_at", name, coordinates(entry.statusPos()));
        }
        return name;
    }

    private static int statusColor(Construction.Status status) {
        if (status == Construction.Status.WORKING || status == Construction.Status.DONE) {
            return 0xFFA0E0A0;
        }
        return status.isProblem() ? 0xFFFF9070 : 0xFFE0D090;
    }

    private static int roleColor(BuildingRoles.Status status) {
        return switch (status) {
            case ACTIVE -> 0xFFA0E0A0;
            case NONE -> 0xFFB0B0B0;
            default -> 0xFFFF9070;
        };
    }

    private static Component size(ConstructionView.Template entry) {
        return Component.translatable(KEY + "size", entry.sizeX(), entry.sizeY(), entry.sizeZ());
    }

    /**
     * The name of a construction with what the builders do: «Repair: Small house».
     */
    private static Component siteName(ConstructionView.Site entry) {
        Component name = entry.kind() == Construction.Kind.BUILD ? entry.name()
                : Component.translatable(KEY + "kind." + entry.kind().id(), entry.name());
        return entry.paused() ? Component.translatable(KEY + "paused_name", name) : name;
    }

    private static Component buildingName(ConstructionView.Building entry) {
        Component name = entry.level() > 1 ? Component.translatable(KEY + "leveled_name", entry.name(), entry.level()) : entry.name();
        return entry.admin() ? Component.translatable(KEY + "admin_name", name) : name;
    }

    // ---------------------------------------------------------------- wood and stone

    /**
     * The woods to choose from: that of the place, the template's own, then every wood the game knows.
     */
    static List<String> woodChoices() {
        List<String> choices = new ArrayList<>();
        choices.add(Materials.AUTO);
        choices.add("");
        choices.addAll(TemplateMaterials.table().woods());
        return choices;
    }

    static List<String> stoneChoices() {
        List<String> choices = new ArrayList<>();
        choices.add("");
        choices.addAll(TemplateMaterials.table().stones());
        return choices;
    }

    private Materials chosen() {
        List<String> woods = woodChoices();
        List<String> stones = stoneChoices();
        return new Materials(woods.get(Math.floorMod(wood, woods.size())), stones.get(Math.floorMod(stone, stones.size())));
    }

    private static Component woodLabel(String value) {
        Component name = Materials.AUTO.equals(value) ? Component.translatable(KEY + "wood.auto")
                : value.isEmpty() ? Component.translatable(KEY + "wood.own") : TemplateMaterials.woodName(value);
        return Component.translatable(KEY + "wood", name);
    }

    private static Component stoneLabel(String value) {
        return Component.translatable(KEY + "stone", value.isEmpty() ? Component.translatable(KEY + "stone.own") : TemplateMaterials.stoneName(value));
    }

    /**
     * The cost of a template built of the chosen materials: every block item becomes the one that stands for it.
     */
    static List<ConstructionView.Cost> cost(List<ConstructionView.Cost> cost, Materials materials) {
        if (materials.stone().isEmpty() && (materials.wood().isEmpty() || Materials.AUTO.equals(materials.wood()))) {
            return cost;
        }
        Map<net.minecraft.world.item.Item, Integer> counts = new LinkedHashMap<>();
        for (ConstructionView.Cost entry : cost) {
            net.minecraft.world.item.Item item = entry.item().getItem();
            if (item instanceof BlockItem block) {
                item = TemplateMaterials.substitute(block.getBlock().defaultBlockState(), materials).getBlock().asItem();
                if (item == net.minecraft.world.item.Items.AIR) {
                    item = entry.item().getItem();
                }
            }
            counts.merge(item, entry.count(), Integer::sum);
        }
        List<ConstructionView.Cost> result = new ArrayList<>();
        counts.forEach((item, count) -> result.add(new ConstructionView.Cost(new ItemStack(item), count)));
        return result;
    }

    // ---------------------------------------------------------------- the entries shown

    /**
     * The templates of the view in the mode: the catalog of the data packs, those of the style of the kingdom first and
     * filtered by style, or the kingdom's own blueprints in the order the server sent them.
     */
    private List<ConstructionView.Template> templates() {
        List<ConstructionView.Template> result = new ArrayList<>();
        if (view == null) {
            return result;
        }
        for (ConstructionView.Template entry : view.templates()) {
            if (entry.own() == (mode == Mode.OWN) && (mode != Mode.CATALOG || shows(styleOf(entry.id())))) {
                result.add(entry);
            }
        }
        if (mode == Mode.CATALOG) {
            // The sort keeps the order of the server inside a group.
            result.sort(java.util.Comparator.comparingInt(entry -> group(styleOf(entry.id()))));
        }
        return result;
    }

    @Nullable
    private ConstructionView.Template selectedTemplate() {
        for (ConstructionView.Template entry : templates()) {
            if (entry.id().equals(template)) {
                return entry;
            }
        }
        return null;
    }

    @Nullable
    private ConstructionView.Site selectedSite() {
        if (view == null) {
            return null;
        }
        for (ConstructionView.Site entry : view.sites()) {
            if (entry.id() == site) {
                return entry;
            }
        }
        return null;
    }

    @Nullable
    private ConstructionView.Building selectedBuilding() {
        if (view == null) {
            return null;
        }
        for (ConstructionView.Building entry : view.buildings()) {
            if (entry.id() == building) {
                return entry;
            }
        }
        return null;
    }

    private Component summary() {
        return switch (mode) {
            case CATALOG -> Component.translatable(KEY + "summary.catalog", templates().size());
            case OWN -> Component.translatable(KEY + "summary.own", templates().size(), view.maxBlueprints());
            case SITES -> Component.translatable(KEY + "summary.sites", view.sites().size(), view.maxConstructions());
            case BUILDINGS -> Component.translatable(KEY + "summary.buildings", view.buildings().size(), view.fortification());
        };
    }

    // ---------------------------------------------------------------- layout

    int measure(Font font) {
        modeWidth = 0;
        for (Mode value : Mode.values()) {
            modeWidth = Math.max(modeWidth, screen.buttonWidth(modeLabel(value)));
        }
        takeWidth = screen.buttonWidth(Component.translatable(KEY + "take"));
        backWidth = screen.buttonWidth(Component.translatable(KEY + "back"));
        priorityWidth = 0;
        for (Construction.Priority priority : Construction.Priority.values()) {
            priorityWidth = Math.max(priorityWidth, screen.buttonWidth(priorityLabel(priority)));
        }
        pauseWidth = Math.max(screen.buttonWidth(pauseLabel(true)), screen.buttonWidth(pauseLabel(false)));
        orderWidth = Math.max(screen.buttonWidth(orderLabel(true)), screen.buttonWidth(orderLabel(false)));
        cancelWidth = screen.buttonWidth(Component.translatable(KEY + "cancel"));
        confirmWidth = screen.buttonWidth(Component.translatable(KEY + "confirm"));
        filterWidth = Math.max(screen.buttonWidth(filterLabel(ALL_STYLES)), screen.buttonWidth(filterLabel(OWN_STYLE)));
        for (int i = 0; i < ArchitecturalStyles.IDS.size(); i++) {
            filterWidth = Math.max(filterWidth, screen.buttonWidth(filterLabel(i)));
        }
        int top = modeWidth + 8 + font.width(Component.translatable(KEY + "summary.buildings", 512, 99_999));
        // The catalog has the filter of styles beside the button of the view.
        top = Math.max(top, modeWidth + GAP + filterWidth + 8 + font.width(Component.translatable(KEY + "summary.catalog", 512)));
        int detail = takeWidth + GAP + backWidth;
        int controls = Math.max(priorityWidth + GAP + pauseWidth, orderWidth + GAP + cancelWidth);
        int messages = 0;
        for (String key : List.of("loading", "no_templates", "no_sites", "no_buildings", "no_own", "read_only", "hint.catalog")) {
            messages = Math.max(messages, Math.min(300, font.width(Component.translatable(KEY + key))));
        }
        return Math.max(Math.max(top, detail), Math.max(controls + backWidth + GAP, messages));
    }

    private static int buttonsY(int top) {
        return top + ThroneScreen.CONTENT_TOP;
    }

    private static int listTop(int top) {
        return buttonsY(top) + ROW + 2;
    }

    private static int bottom(int top, int panelHeight) {
        return top + panelHeight - ThroneScreen.BOTTOM_AREA;
    }

    private static int visibleRows(int top, int panelHeight) {
        return Math.max(1, (bottom(top, panelHeight) - listTop(top)) / ROW);
    }

    private int rowCount() {
        if (view == null) {
            return 0;
        }
        return switch (mode) {
            case CATALOG, OWN -> templates().size();
            case SITES -> view.sites().size();
            case BUILDINGS -> view.buildings().size();
        };
    }

    /**
     * A button laid out in rows from the left; a row that is full goes on below.
     */
    private record Placed(Component label, int x, int y, int width) {
    }

    private List<Placed> rows(List<Component> labels, int x, int right, int y) {
        List<Placed> placed = new ArrayList<>();
        int cursor = x;
        int row = y;
        for (Component label : labels) {
            int width = Math.min(right - x, screen.buttonWidth(label));
            if (cursor + width > right && cursor > x) {
                cursor = x;
                row += ROW;
            }
            placed.add(new Placed(label, cursor, row, width));
            cursor += width + GAP;
        }
        return placed;
    }

    private static int rowsOf(List<Placed> placed) {
        return placed.isEmpty() ? 0 : (placed.get(placed.size() - 1).y() - placed.get(0).y()) / ROW + 1;
    }

    private List<Component> siteLabels(ConstructionView.Site entry) {
        return List.of(priorityLabel(entry.priority()), pauseLabel(entry.paused()), orderLabel(entry.orderMissing()),
                Component.translatable(KEY + "cancel"));
    }

    private List<Component> materialLabels() {
        Materials materials = chosen();
        return List.of(woodLabel(materials.wood()), stoneLabel(materials.stone()));
    }

    private List<Component> buildingLabels(ConstructionView.Building entry) {
        return List.of(workLabel(BuildingPackets.Change.Action.UPGRADE), workLabel(BuildingPackets.Change.Action.REPAIR),
                workLabel(BuildingPackets.Change.Action.DEMOLISH), autoLabel(entry.autoRepair()));
    }

    private List<Component> ownLabels() {
        return List.of(Component.translatable(KEY + "rename"), Component.translatable(KEY + "delete"));
    }

    // ---------------------------------------------------------------- widgets

    void init(int left, int top, int panelWidth, int panelHeight) {
        if (view == null) {
            return;
        }
        int x = left + KingdomScreen.PAD;
        int right = left + panelWidth - KingdomScreen.PAD;
        ConstructionView.Template shownTemplate = mode == Mode.CATALOG || mode == Mode.OWN ? selectedTemplate() : null;
        ConstructionView.Site shownSite = mode == Mode.SITES ? selectedSite() : null;
        ConstructionView.Building shownBuilding = mode == Mode.BUILDINGS ? selectedBuilding() : null;
        if (shownTemplate == null && shownSite == null && shownBuilding == null) {
            template = "";
            site = 0;
            building = 0;
            action = null;
            Mode next = Mode.values()[(mode.ordinal() + 1) % Mode.values().length];
            screen.add(Button.builder(modeLabel(mode), b -> {
                mode = next;
                scroll = 0;
                screen.rebuild();
            }).bounds(x, buttonsY(top), modeWidth, ROW - 2).build());
            if (mode == Mode.CATALOG) {
                if (filter == OWN_STYLE && kingdomStyle().isEmpty()) {
                    filter = ALL_STYLES;
                }
                Button filterButton = screen.add(Button.builder(filterLabel(filter), b -> {
                    filter = nextFilter(filter);
                    scroll = 0;
                    screen.rebuild();
                }).bounds(x + modeWidth + GAP, buttonsY(top), filterWidth, ROW - 2).build());
                filterButton.setTooltip(Tooltip.create(Component.translatable(KEY + "filter.hint")));
            }
            int rows = visibleRows(top, panelHeight);
            scroll = Math.max(0, Math.min(scroll, rowCount() - rows));
            return;
        }
        screen.add(Button.builder(Component.translatable(KEY + "back"), b -> {
            if (action != null) {
                action = null;
            } else {
                template = "";
                site = 0;
                building = 0;
            }
            scroll = 0;
            screen.rebuild();
        }).bounds(right - backWidth, buttonsY(top), backWidth, ROW - 2).build());
        if (shownTemplate != null) {
            initTemplate(shownTemplate, x, right, top);
            return;
        }
        if (!view.canManage()) {
            return;
        }
        if (shownBuilding != null) {
            initBuilding(shownBuilding, x, right, top, panelHeight);
            return;
        }
        int controlsTop = siteControlsTop(top, panelHeight, shownSite, x, right);
        long id = shownSite.id();
        List<Placed> placed = rows(siteLabels(shownSite), x, right, controlsTop);
        for (int i = 0; i < placed.size(); i++) {
            Placed button = placed.get(i);
            Button widget;
            switch (i) {
                case 0 -> {
                    widget = Button.builder(button.label(), b -> change(ConstructionPackets.Change.Action.PRIORITY, id,
                            shownSite.priority().next().ordinal())).bounds(button.x(), button.y(), button.width(), ROW - 2).build();
                    widget.setTooltip(Tooltip.create(Component.translatable(KEY + "priority_hint")));
                }
                case 1 -> widget = Button.builder(button.label(), b -> change(shownSite.paused() ? ConstructionPackets.Change.Action.RESUME
                        : ConstructionPackets.Change.Action.PAUSE, id, 0)).bounds(button.x(), button.y(), button.width(), ROW - 2).build();
                case 2 -> {
                    widget = Button.builder(button.label(), b -> change(shownSite.orderMissing() ? ConstructionPackets.Change.Action.NO_ORDERS
                            : ConstructionPackets.Change.Action.ORDER_MISSING, id, 0)).bounds(button.x(), button.y(), button.width(), ROW - 2).build();
                    widget.setTooltip(Tooltip.create(Component.translatable(KEY + "order_hint")));
                }
                default -> {
                    widget = Button.builder(button.label(), b -> {
                        change(ConstructionPackets.Change.Action.CANCEL, id, 0);
                        site = 0;
                    }).bounds(button.x(), button.y(), button.width(), ROW - 2).build();
                    widget.setTooltip(Tooltip.create(Component.translatable(KEY + "cancel_hint")));
                }
            }
            screen.add(widget);
        }
    }

    private void initTemplate(ConstructionView.Template shown, int x, int right, int top) {
        int buttonsRight = right - backWidth - GAP;
        Button take = screen.add(Button.builder(Component.translatable(KEY + "take"), b -> {
            Materials materials = chosen();
            send(new ConstructionPackets.TakeBlueprint(view.kingdomId(), shown.id(), materials.wood(), materials.stone()));
        }).bounds(buttonsRight - takeWidth, buttonsY(top), takeWidth, ROW - 2).build());
        take.active = view.canManage() && shown.available();
        take.setTooltip(Tooltip.create(Component.translatable(view.canManage() ? KEY + "take_hint" : KEY + "read_only")));
        int y = materialsTop(top, shown);
        List<Placed> placed = rows(materialLabels(), x, right, y);
        Button woodButton = screen.add(Button.builder(placed.get(0).label(), b -> {
            wood = (wood + 1) % woodChoices().size();
            screen.rebuild();
        }).bounds(placed.get(0).x(), placed.get(0).y(), placed.get(0).width(), ROW - 2).build());
        woodButton.setTooltip(Tooltip.create(Component.translatable(KEY + "wood_hint")));
        Button stoneButton = screen.add(Button.builder(placed.get(1).label(), b -> {
            stone = (stone + 1) % stoneChoices().size();
            screen.rebuild();
        }).bounds(placed.get(1).x(), placed.get(1).y(), placed.get(1).width(), ROW - 2).build());
        stoneButton.setTooltip(Tooltip.create(Component.translatable(KEY + "stone_hint")));
        if (!shown.own() || !view.canManage()) {
            return;
        }
        // The kingdom's own blueprint is renamed and deleted from the top row, left of the blueprint button.
        int deleteWidth = screen.buttonWidth(ownLabels().get(1));
        int renameWidth = screen.buttonWidth(ownLabels().get(0));
        int deleteX = buttonsRight - takeWidth - GAP - deleteWidth;
        int renameX = deleteX - GAP - renameWidth;
        screen.add(Button.builder(ownLabels().get(0), b -> Minecraft.getInstance().setScreen(new RenameBlueprintScreen(screen, view.kingdomId(),
                shown.blueprint(), shown.name().getString()))).bounds(renameX, buttonsY(top), renameWidth, ROW - 2).build());
        screen.add(Button.builder(ownLabels().get(1), b -> confirm(Component.translatable(KEY + "delete_question", shown.name()), () -> {
            send(new BuildingPackets.BlueprintChange(view.kingdomId(), BuildingPackets.BlueprintChange.Action.DELETE, shown.blueprint(), ""));
            template = "";
        })).bounds(deleteX, buttonsY(top), deleteWidth, ROW - 2).build());
    }

    /**
     * Where the name of a template in detail must end: before the buttons of the top row.
     */
    private int templateNameRight(ConstructionView.Template entry, int right) {
        int buttons = backWidth + GAP + takeWidth;
        if (entry.own() && view != null && view.canManage()) {
            buttons += 2 * GAP + screen.buttonWidth(ownLabels().get(0)) + screen.buttonWidth(ownLabels().get(1));
        }
        return right - buttons - 6;
    }

    private void initBuilding(ConstructionView.Building shown, int x, int right, int top, int panelHeight) {
        if (action != null) {
            Button confirm = screen.add(Button.builder(Component.translatable(KEY + "confirm"), b -> {
                send(new BuildingPackets.Change(view.kingdomId(), action, shown.id()));
                action = null;
            }).bounds(right - backWidth - GAP - confirmWidth, buttonsY(top), confirmWidth, ROW - 2).build());
            confirm.active = shown.busy() == null;
            return;
        }
        List<Placed> placed = rows(buildingLabels(shown), x, right, buildingControlsTop(top, panelHeight, shown, x, right));
        BuildingPackets.Change.Action[] works = {BuildingPackets.Change.Action.UPGRADE, BuildingPackets.Change.Action.REPAIR,
                BuildingPackets.Change.Action.DEMOLISH};
        for (int i = 0; i < works.length; i++) {
            BuildingPackets.Change.Action work = works[i];
            Placed button = placed.get(i);
            Button widget = screen.add(Button.builder(button.label(), b -> {
                action = work;
                scroll = 0;
                screen.rebuild();
            }).bounds(button.x(), button.y(), button.width(), ROW - 2).build());
            // City walls and gates are kept in the roads tab.
            boolean byRoads = io.github.sekerator.kingdomworld.common.road.CityWalls.CATEGORY.equals(shown.category());
            widget.active = shown.busy() == null && !byRoads && switch (work) {
                case UPGRADE -> shown.upgradable();
                case REPAIR -> shown.damaged() > 0 && shown.integrity() >= 0;
                default -> true;
            };
            widget.setTooltip(Tooltip.create(byRoads ? Component.translatable(KEY + "work_hint.by_roads")
                    : Component.translatable(KEY + "work_hint." + work.name().toLowerCase(Locale.ROOT))));
        }
        Placed auto = placed.get(3);
        Button toggle = screen.add(Button.builder(auto.label(), b -> send(new BuildingPackets.Change(view.kingdomId(),
                shown.autoRepair() ? BuildingPackets.Change.Action.AUTO_REPAIR_OFF : BuildingPackets.Change.Action.AUTO_REPAIR_ON, shown.id())))
                .bounds(auto.x(), auto.y(), auto.width(), ROW - 2).build());
        boolean byRoads = io.github.sekerator.kingdomworld.common.road.CityWalls.CATEGORY.equals(shown.category());
        toggle.active = !byRoads;
        toggle.setTooltip(Tooltip.create(Component.translatable(byRoads ? KEY + "work_hint.by_roads" : KEY + "auto_repair_hint")));
    }

    private void confirm(Component question, Runnable onYes) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                onYes.run();
            }
            minecraft.setScreen(screen);
        }, Component.translatable("kingdomworld.screen.throne.confirm_title"), question));
    }

    /**
     * Where the buttons of a construction in detail start: at the bottom of the list area, as many rows as they need.
     */
    private int siteControlsTop(int top, int panelHeight, ConstructionView.Site entry, int x, int right) {
        return bottom(top, panelHeight) - rowsOf(rows(siteLabels(entry), x, right, 0)) * ROW;
    }

    private int buildingControlsTop(int top, int panelHeight, ConstructionView.Building entry, int x, int right) {
        if (view == null || !view.canManage()) {
            return bottom(top, panelHeight);
        }
        return bottom(top, panelHeight) - rowsOf(rows(buildingLabels(entry), x, right, 0)) * ROW;
    }

    /**
     * Where the buttons of the wood and stone of a template in detail stand: below its lines of text.
     */
    private int materialsTop(int top, ConstructionView.Template entry) {
        return listTop(top) + 2 * LINE + (entry.liquids() > 0 ? LINE : 0) + (entry.available() ? 0 : LINE) + 2;
    }

    private void send(Object packet) {
        if (Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(packet);
        }
    }

    private void change(ConstructionPackets.Change.Action change, long id, int value) {
        if (view != null) {
            send(new ConstructionPackets.Change(view.kingdomId(), change, id, value));
        }
    }

    boolean scroll(int top, int panelHeight, double delta) {
        int max;
        if ((mode == Mode.CATALOG || mode == Mode.OWN) && selectedTemplate() != null) {
            int[] rows = costRows(selectedTemplate(), top, panelHeight);
            max = Math.max(0, rows[1] - rows[0]);
        } else if (mode == Mode.SITES && selectedSite() != null) {
            max = Math.max(0, selectedSite().missing().size() - missingRows(top, panelHeight));
        } else if (mode == Mode.BUILDINGS && selectedBuilding() != null) {
            if (action == null) {
                return false;
            }
            int[] rows = workRows(workCost(selectedBuilding()), top, panelHeight);
            max = Math.max(0, rows[1] - rows[0]);
        } else {
            max = Math.max(0, rowCount() - visibleRows(top, panelHeight));
        }
        int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        if (next == scroll) {
            return false;
        }
        scroll = next;
        return true;
    }

    /**
     * A click on a row of the catalog, the constructions or the buildings shows it in detail.
     */
    boolean click(int left, int top, int panelWidth, int panelHeight, double mouseX, double mouseY) {
        if (view == null || mouseX < left + KingdomScreen.PAD || mouseX > left + panelWidth - KingdomScreen.PAD) {
            return false;
        }
        boolean list = ((mode == Mode.CATALOG || mode == Mode.OWN) && selectedTemplate() == null) || (mode == Mode.SITES && selectedSite() == null)
                || (mode == Mode.BUILDINGS && selectedBuilding() == null);
        if (!list) {
            return false;
        }
        int row = (int) Math.floor((mouseY - listTop(top)) / ROW);
        if (mouseY < listTop(top) || row < 0 || row >= visibleRows(top, panelHeight) || scroll + row >= rowCount()) {
            return false;
        }
        switch (mode) {
            case CATALOG, OWN -> template = templates().get(scroll + row).id();
            case SITES -> site = view.sites().get(scroll + row).id();
            case BUILDINGS -> {
                building = view.buildings().get(scroll + row).id();
                action = null;
            }
        }
        scroll = 0;
        screen.rebuild();
        return true;
    }

    // ---------------------------------------------------------------- drawing

    void render(GuiGraphics graphics, Font font, int left, int top, int panelWidth, int panelHeight) {
        int x = left + KingdomScreen.PAD;
        int right = left + panelWidth - KingdomScreen.PAD;
        lastPanelWidth = right - x + 2 * KingdomScreen.PAD;
        if (view == null) {
            screen.label(graphics, Component.translatable(KEY + "loading"), x, buttonsY(top) + 4, 0xFFB8B8B8, right);
            return;
        }
        ConstructionView.Template shownTemplate = mode == Mode.CATALOG || mode == Mode.OWN ? selectedTemplate() : null;
        ConstructionView.Site shownSite = mode == Mode.SITES ? selectedSite() : null;
        ConstructionView.Building shownBuilding = mode == Mode.BUILDINGS ? selectedBuilding() : null;
        if (shownTemplate != null) {
            renderTemplate(graphics, font, shownTemplate, x, right, top, panelHeight);
            return;
        }
        if (shownSite != null) {
            renderSite(graphics, font, shownSite, x, right, top, panelHeight);
            return;
        }
        if (shownBuilding != null) {
            if (action != null) {
                renderWork(graphics, font, shownBuilding, x, right, top, panelHeight);
            } else {
                renderBuilding(graphics, font, shownBuilding, x, right, top, panelHeight);
            }
            return;
        }
        int summaryX = x + modeWidth + 8 + (mode == Mode.CATALOG ? GAP + filterWidth : 0);
        screen.name(graphics, summary().getString(), summaryX, buttonsY(top) + 5, 0xFFD0D0D0, right - summaryX);
        int rows = visibleRows(top, panelHeight);
        int count = rowCount();
        int listTop = listTop(top);
        if (count == 0) {
            String key = switch (mode) {
                case CATALOG -> "no_templates";
                case OWN -> "no_own";
                case SITES -> "no_sites";
                case BUILDINGS -> "no_buildings";
            };
            screen.paragraph(graphics, Component.translatable(KEY + key), x, listTop + 5, right - x, 3, 0xFFB8B8B8);
            return;
        }
        List<ConstructionView.Template> templates = templates();
        for (int i = 0; i < rows && scroll + i < count; i++) {
            int y = listTop + i * ROW;
            switch (mode) {
                case CATALOG, OWN -> renderTemplateRow(graphics, font, templates.get(scroll + i), x, right, y);
                case SITES -> renderSiteRow(graphics, font, view.sites().get(scroll + i), x, right, y);
                case BUILDINGS -> renderBuildingRow(graphics, font, view.buildings().get(scroll + i), x, right, y);
            }
        }
        if (count > rows) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(count, scroll + rows), count);
            screen.label(graphics, page, right - font.width(page), listTop + rows * ROW, 0xFF9A9A9A, right);
        }
    }

    private void renderTemplateRow(GuiGraphics graphics, Font font, ConstructionView.Template entry, int x, int right, int y) {
        String style = entry.own() ? "" : styleOf(entry.id());
        if (!style.isEmpty() && style.equals(kingdomStyle())) {
            graphics.fill(x - 5, y + 2, x - 3, y + ROW - 3, OWN_STYLE_COLOR);
        }
        graphics.renderItem(entry.icon(), x, y + 1);
        Component plain = Component.translatable(KEY + "row.template", category(entry.category()), size(entry));
        Component info = style.isEmpty() ? plain
                : Component.translatable(KEY + "row.template", Component.translatable(KEY + "row.style", StylePicker.name(style)), plain);
        int nameX = x + 20;
        String name = entry.name().getString();
        // The details take half the row, or what a short name leaves; where the style does not fit with the category and the
        // size, it is left to the hover so that the size is never cut.
        int room = Math.max((right - x) / 2, right - (nameX + font.width(name) + 6));
        boolean styleShown = font.width(info) <= room || style.isEmpty();
        Component shown = styleShown ? info : plain;
        int infoWidth = Math.min(font.width(shown), room);
        int infoX = right - infoWidth;
        int drawnInfo = screen.name(graphics, shown.getString(), infoX, y + 5, 0xFFB0B0B0, infoWidth);
        if (!styleShown) {
            screen.hoverText(infoX, y + 5, drawnInfo, info);
        }
        int drawn = screen.name(graphics, name, nameX, y + 5, entry.available() ? 0xFFFFFFFF : 0xFF909090, infoX - 6 - nameX);
        if (!entry.available()) {
            screen.hoverText(nameX, y + 5, drawn, entry.problem());
        }
    }

    private void renderSiteRow(GuiGraphics graphics, Font font, ConstructionView.Site entry, int x, int right, int y) {
        graphics.renderItem(entry.icon(), x, y + 1);
        Component percent = Component.translatable(KEY + "percent", entry.percent());
        int percentX = right - font.width(percent);
        screen.label(graphics, percent, percentX, y + 5, 0xFFF0D070, right);
        int statusWidth = Math.min(font.width(status(entry)), (percentX - x) / 2);
        int statusX = percentX - 6 - statusWidth;
        int drawnStatus = screen.name(graphics, status(entry).getString(), statusX, y + 5, statusColor(entry.status()), statusWidth);
        screen.hoverText(statusX, y + 5, drawnStatus, status(entry));
        int nameX = x + 20;
        screen.name(graphics, siteName(entry).getString(), nameX, y + 5, 0xFFFFFFFF, statusX - 6 - nameX);
    }

    private static Component integrity(ConstructionView.Building entry) {
        return entry.integrity() < 0 ? Component.translatable(KEY + "integrity_unknown") : Component.translatable(KEY + "integrity", entry.integrity());
    }

    private static int integrityColor(int integrity) {
        return integrity < 0 ? 0xFFB0B0B0 : integrity >= 90 ? 0xFFA0E0A0 : integrity >= 50 ? 0xFFE0D090 : 0xFFFF9070;
    }

    private static Component roleShort(ConstructionView.Building entry) {
        if (entry.roleStatus() == BuildingRoles.Status.NONE) {
            return Component.translatable(KEY + "role_none");
        }
        return entry.roleStatus().works() ? entry.role() : Component.translatable(KEY + "role_resting", entry.role());
    }

    private void renderBuildingRow(GuiGraphics graphics, Font font, ConstructionView.Building entry, int x, int right, int y) {
        Component integrity = integrity(entry);
        int integrityX = right - font.width(integrity);
        screen.label(graphics, integrity, integrityX, y + 5, integrityColor(entry.integrity()), right);
        Component role = roleShort(entry);
        int roleWidth = Math.min(font.width(role), (integrityX - x) / 3);
        int roleX = integrityX - 6 - roleWidth;
        int drawnRole = screen.name(graphics, role.getString(), roleX, y + 5, roleColor(entry.roleStatus()), roleWidth);
        screen.hoverText(roleX, y + 5, drawnRole, entry.roleReason());
        Component place = Component.translatable(KEY + "row.building", category(entry.category()), coordinates(entry.pos()));
        int placeWidth = Math.min(font.width(place), (roleX - x) / 2);
        int placeX = roleX - 6 - placeWidth;
        screen.name(graphics, place.getString(), placeX, y + 5, 0xFFB0B0B0, placeWidth);
        screen.name(graphics, buildingName(entry).getString(), x, y + 5, 0xFFFFFFFF, placeX - 6 - x);
    }

    /**
     * Cells of a cost per row and rows of it in all, with the rows that fit: {fit, total, perRow, firstLine, cellWidth}.
     */
    private int[] grid(int count, int firstLine, int bottom) {
        Font font = screen.font();
        int cellWidth = CELL + 4 + font.width(Component.translatable(KEY + "count", 999_999)) + 6;
        int perRow = Math.max(1, Math.min(8, (lastPanelWidth - 2 * KingdomScreen.PAD) / cellWidth));
        int total = (count + perRow - 1) / perRow;
        int fit = Math.max(1, (bottom - firstLine) / (CELL + 2));
        return new int[]{fit, total, perRow, firstLine, cellWidth};
    }

    private int[] costRows(ConstructionView.Template entry, int top, int panelHeight) {
        int x = 0;
        int right = Math.max(1, lastPanelWidth - 2 * KingdomScreen.PAD);
        int buttonRows = rowsOf(rows(materialLabels(), x, right, 0));
        int firstLine = materialsTop(top, entry) + buttonRows * ROW + LINE + 2;
        return grid(cost(entry.cost(), chosen()).size(), firstLine, bottom(top, panelHeight));
    }

    private int lastPanelWidth;

    private void drawGrid(GuiGraphics graphics, Font font, List<ConstructionView.Cost> cost, int[] rows, int x) {
        int perRow = rows[2];
        int first = rows[3];
        int cellWidth = rows[4];
        for (int row = 0; row < rows[0] && scroll + row < rows[1]; row++) {
            for (int column = 0; column < perRow; column++) {
                int index = (scroll + row) * perRow + column;
                if (index >= cost.size()) {
                    break;
                }
                ConstructionView.Cost entry = cost.get(index);
                int cellX = x + column * cellWidth;
                int cellY = first + row * (CELL + 2);
                graphics.renderItem(entry.item(), cellX, cellY);
                Component count = Component.translatable(KEY + "count", entry.count());
                screen.label(graphics, count, cellX + CELL + 4, cellY + 4, 0xFFE8E8E8, cellX + cellWidth);
                screen.hoverText(cellX, cellY + 4, CELL, entry.item().getHoverName());
            }
        }
    }

    private void renderTemplate(GuiGraphics graphics, Font font, ConstructionView.Template entry, int x, int right, int top, int panelHeight) {
        int y = buttonsY(top);
        graphics.renderItem(entry.icon(), x, y + 1);
        int nameRight = templateNameRight(entry, right);
        screen.name(graphics, entry.name().getString(), x + 20, y + 5, 0xFFF0D070, Math.max(0, nameRight - x - 20));
        int line = listTop(top);
        Component role = entry.role().getString().isEmpty() ? Component.translatable(KEY + "template_role_none", entry.level())
                : entry.next().getString().isEmpty() ? Component.translatable(KEY + "template_role", entry.role(), entry.level())
                : Component.translatable(KEY + "template_role_next", entry.role(), entry.level(), entry.next());
        screen.name(graphics, Component.translatable(KEY + "category", category(entry.category())).getString() + "; "
                + role.getString(), x, line, 0xFFD0D0D0, right - x);
        line += LINE;
        screen.name(graphics, Component.translatable(KEY + "dimensions", size(entry), BlueprintRenderer.blocks(entry.blocks())).getString(), x, line,
                0xFFD0D0D0, right - x);
        line += LINE;
        Materials materials = chosen();
        Component note = Materials.AUTO.equals(materials.wood()) ? Component.translatable(KEY + "cost_auto") : Component.translatable(KEY + "cost");
        if (entry.liquids() > 0) {
            screen.name(graphics, Component.translatable(KEY + "liquids", BlueprintRenderer.blocks(entry.liquids())).getString(), x, line,
                    0xFFB8B8B8, right - x);
            line += LINE;
        }
        if (!entry.available()) {
            // One line; the whole reason shows on hover.
            int drawn = screen.name(graphics, entry.problem().getString(), x, line, 0xFFFF9070, right - x);
            screen.hoverText(x, line, drawn, entry.problem());
        }
        List<ConstructionView.Cost> cost = cost(entry.cost(), materials);
        int[] rows = costRows(entry, top, panelHeight);
        scroll = Math.max(0, Math.min(scroll, rows[1] - rows[0]));
        int noteRight = right;
        if (rows[1] > rows[0]) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(rows[1], scroll + rows[0]), rows[1]);
            screen.label(graphics, page, right - font.width(page), rows[3] - LINE - 1, 0xFF9A9A9A, right);
            noteRight = right - font.width(page) - 6;
        }
        screen.name(graphics, note.getString(), x, rows[3] - LINE - 1, 0xFFF0D070, noteRight - x);
        drawGrid(graphics, font, cost, rows, x);
    }

    private int missingRows(int top, int panelHeight) {
        ConstructionView.Site entry = selectedSite();
        if (entry == null) {
            return 1;
        }
        int x = 0;
        int right = Math.max(1, lastPanelWidth - 2 * KingdomScreen.PAD);
        int controls = view != null && view.canManage() ? siteControlsTop(top, panelHeight, entry, x, right) : bottom(top, panelHeight);
        return Math.max(1, (controls - 2 - (listTop(top) + 3 * LINE + 2)) / (CELL + 2));
    }

    private void renderSite(GuiGraphics graphics, Font font, ConstructionView.Site entry, int x, int right, int top, int panelHeight) {
        int y = buttonsY(top);
        graphics.renderItem(entry.icon(), x, y + 1);
        screen.name(graphics, siteName(entry).getString(), x + 20, y + 5, 0xFFF0D070, right - backWidth - 6 - x - 20);
        int line = listTop(top);
        screen.name(graphics, Component.translatable(KEY + "site_place", coordinates(entry.pos()), entry.builders()).getString(), x, line,
                0xFFD0D0D0, right - x);
        line += LINE;
        Component progress = entry.kind() == Construction.Kind.DEMOLISH
                ? Component.translatable(KEY + "progress_demolition", entry.percent())
                : Component.translatable(KEY + "progress", entry.percent(), entry.placed(), BlueprintRenderer.blocks(entry.total()));
        screen.name(graphics, progress.getString(), x, line, 0xFFD0D0D0, right - x);
        line += LINE;
        int stateLine = line;
        line += LINE + 2;
        int controls = view.canManage() ? siteControlsTop(top, panelHeight, entry, x, right) : bottom(top, panelHeight);
        int rows = Math.max(1, (controls - 2 - line) / (CELL + 2));
        scroll = Math.max(0, Math.min(scroll, entry.missing().size() - rows));
        // When the missing materials take more than one page, the counter stands at the end of the state line.
        int stateRight = right;
        if (entry.missing().size() > rows) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(entry.missing().size(), scroll + rows),
                    entry.missing().size());
            screen.label(graphics, page, right - font.width(page), stateLine, 0xFF9A9A9A, right);
            stateRight = right - font.width(page) - 6;
        }
        int drawn = screen.name(graphics, Component.translatable(KEY + "state", status(entry)).getString(), x, stateLine,
                statusColor(entry.status()), stateRight - x);
        screen.hoverText(x, stateLine, drawn, status(entry));
        if (entry.missing().isEmpty()) {
            screen.name(graphics, Component.translatable(KEY + "nothing_missing").getString(), x, line + 4, 0xFFA0E0A0, right - x);
            return;
        }
        for (int i = 0; i < rows && scroll + i < entry.missing().size(); i++) {
            ConstructionView.Missing missing = entry.missing().get(scroll + i);
            int rowY = line + i * (CELL + 2);
            graphics.renderItem(missing.item(), x, rowY);
            Component note;
            if (missing.ordered()) {
                note = Component.translatable(KEY + "missing.ordered", missing.count());
            } else if (!missing.gatherer().isEmpty()) {
                note = Component.translatable(KEY + "missing.gathered", missing.count(), inSentence(CitizenTexts.profession(missing.gatherer())));
            } else if (missing.orderable()) {
                note = Component.translatable(KEY + "missing.orderable", missing.count());
            } else {
                note = Component.translatable(KEY + "missing.nobody", missing.count());
            }
            Component text = Component.translatable(KEY + "missing.line", missing.item().getHoverName(), note);
            int shown = screen.name(graphics, text.getString(), x + CELL + 4, rowY + 4, 0xFFFF9070, right - x - CELL - 4);
            screen.hoverText(x + CELL + 4, rowY + 4, shown, text);
        }
    }

    /**
     * The lines of a building in detail, the most important first: its role and state, level, integrity, materials, what
     * the builders do there and what its role gives.
     */
    private List<Component> buildingLines(ConstructionView.Building entry) {
        List<Component> lines = new ArrayList<>();
        lines.add(entry.roleStatus() == BuildingRoles.Status.NONE ? Component.translatable(KEY + "role_line_none")
                : Component.translatable(KEY + "role_line", entry.role(), entry.roleReason()));
        lines.add(entry.upgradable() ? Component.translatable(KEY + "level_line", entry.level(), entry.next())
                : Component.translatable(KEY + "level_top", entry.level()));
        lines.add(entry.integrity() < 0 ? Component.translatable(KEY + "integrity_line_unknown")
                : Component.translatable(KEY + "integrity_line", entry.integrity(), BlueprintRenderer.blocks(entry.damaged())));
        if (entry.busy() != null) {
            lines.add(Component.translatable(KEY + "busy", Component.translatable(KEY + "busy." + entry.busy().id())));
        }
        if (!entry.materials().getString().isEmpty()) {
            lines.add(entry.materials());
        }
        lines.addAll(entry.effects());
        lines.add(Component.translatable(KEY + "building_place", coordinates(entry.pos())));
        return lines;
    }

    private void renderBuilding(GuiGraphics graphics, Font font, ConstructionView.Building entry, int x, int right, int top, int panelHeight) {
        int y = buttonsY(top);
        screen.name(graphics, buildingName(entry).getString(), x, y + 5, 0xFFF0D070, right - backWidth - 6 - x);
        int line = listTop(top);
        int limit = buildingControlsTop(top, panelHeight, entry, x, right) - 2;
        List<Component> lines = buildingLines(entry);
        int fit = Math.max(1, (limit - line) / LINE);
        for (int i = 0; i < lines.size() && i < fit; i++) {
            Component text = lines.get(i);
            int color = i == 0 ? roleColor(entry.roleStatus()) : i == 2 ? integrityColor(entry.integrity()) : 0xFFD0D0D0;
            if (i == fit - 1 && lines.size() > fit) {
                // The lines that do not fit show on hover over the last one.
                Component more = Component.translatable(KEY + "more_lines", lines.size() - fit + 1);
                int drawn = screen.name(graphics, more.getString(), x, line, 0xFF9A9A9A, right - x);
                for (int j = i; j < lines.size(); j++) {
                    screen.hoverText(x, line, drawn, lines.get(j));
                }
                break;
            }
            int drawn = screen.name(graphics, text.getString(), x, line, color, right - x);
            if (i == 0) {
                screen.hoverText(x, line, drawn, entry.roleReason());
            }
            line += LINE;
        }
    }

    private List<ConstructionView.Cost> workCost(ConstructionView.Building entry) {
        if (action == null) {
            return List.of();
        }
        return switch (action) {
            case UPGRADE -> entry.upgrade();
            case REPAIR -> entry.repair();
            default -> entry.demolish();
        };
    }

    private int[] workRows(List<ConstructionView.Cost> cost, int top, int panelHeight) {
        return grid(cost.size(), listTop(top) + 3 * LINE + 2, bottom(top, panelHeight));
    }

    /**
     * The work on a building waiting for confirmation: what it costs (or, for a demolition, gives back) and the button that
     * orders it.
     */
    private void renderWork(GuiGraphics graphics, Font font, ConstructionView.Building entry, int x, int right, int top, int panelHeight) {
        int y = buttonsY(top);
        screen.name(graphics, buildingName(entry).getString(), x, y + 5, 0xFFF0D070, right - backWidth - GAP - confirmWidth - 6 - x);
        int line = listTop(top);
        String work = action.name().toLowerCase(Locale.ROOT);
        Component question = action == BuildingPackets.Change.Action.UPGRADE
                ? Component.translatable(KEY + "question.upgrade", entry.name(), entry.next())
                : Component.translatable(KEY + "question." + work, entry.name());
        screen.paragraph(graphics, question, x, line, right - x, 2, 0xFFE8E8E8);
        line += 2 * LINE;
        List<ConstructionView.Cost> cost = workCost(entry);
        Component heading = Component.translatable(KEY + (action == BuildingPackets.Change.Action.DEMOLISH ? "returns" : "work_cost"));
        int[] rows = workRows(cost, top, panelHeight);
        scroll = Math.max(0, Math.min(scroll, rows[1] - rows[0]));
        int headingRight = right;
        if (rows[1] > rows[0]) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(rows[1], scroll + rows[0]), rows[1]);
            screen.label(graphics, page, right - font.width(page), line, 0xFF9A9A9A, right);
            headingRight = right - font.width(page) - 6;
        }
        screen.name(graphics, heading.getString(), x, line, 0xFFF0D070, headingRight - x);
        if (cost.isEmpty()) {
            screen.name(graphics, Component.translatable(KEY + "no_cost").getString(), x, rows[3] + 4, 0xFFA0E0A0, right - x);
            return;
        }
        drawGrid(graphics, font, cost, rows, x);
    }

    /**
     * A profession named inside a sentence starts with a small letter.
     */
    private static Component inSentence(Component name) {
        String text = name.getString();
        return text.isEmpty() ? name : Component.literal(text.substring(0, 1).toLowerCase(Locale.ROOT) + text.substring(1));
    }

    /**
     * A status line for the bottom while there is no other message.
     */
    Component hint() {
        if (view == null) {
            return Component.empty();
        }
        if (!view.canManage()) {
            return Component.translatable(KEY + "read_only");
        }
        if ((mode == Mode.CATALOG || mode == Mode.OWN) && selectedTemplate() == null) {
            return Component.translatable(mode == Mode.OWN ? KEY + "hint.own" : KEY + "hint.catalog");
        }
        if (mode == Mode.CATALOG || mode == Mode.OWN) {
            return Component.translatable(KEY + "hint.template");
        }
        if (mode == Mode.SITES && selectedSite() == null) {
            return Component.translatable(KEY + "hint.sites");
        }
        if (mode == Mode.BUILDINGS && selectedBuilding() == null) {
            return Component.translatable(KEY + "hint.buildings");
        }
        return Component.empty();
    }
}
