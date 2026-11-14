package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.citizen.work.MineOres;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.WorkPostView;
import io.github.sekerator.kingdomworld.common.network.packet.WorkPostActionPacket;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.MineGoal;
import io.github.sekerator.kingdomworld.core.citizen.Profession;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The screen of a work post: its profession, the radius of its zone, its seats, the animals of a herd, the goal of
 * a mine (the kind of ore, how much of it is needed and the depth), the metals of the mint of a minter (whether it strikes
 * from each and how many of each stay in the storehouses), why it stands still if it does, what it yields and who works
 * there. The buttons that go through choices (profession, animals, ore) take the next one on a left
 * click and the one before on a right click. Members with the right to manage citizens change
 * the settings, hire citizens (those without work and those of the profession without a post first, any other worker
 * with a confirmation that it moves here) and dismiss workers; everybody else of the kingdom only looks. Opened from the
 * window of the workers the screen manages the post from afar and goes back there. Every button sends an intent; the
 * server decides and answers with the result and a fresh screen. Widths come from the translated texts.
 */
public class WorkPostScreen extends KingdomScreen implements ResultListener {
    private static final String KEY = "kingdomworld.screen.post.";
    private static final int MIN_PANEL_WIDTH = 280;
    private static final int MAX_PANEL_HEIGHT = 300;
    private static final int ROW = 20;
    private static final int WORKER_ROW = 18;
    private static final int GAP = 2;
    private static final int SMALL = 20;
    private static final int TOP = 22;
    private static final int STATUS_LINES = 2;
    private static final int BOTTOM_AREA = 26 + 4 + STATUS_LINES * LINE;
    private static final int NAME_COLUMN = 86;
    /** Width the value between minus and plus takes at most ("16 / 16"). */
    private static final int VALUE_WIDTH = 44;
    /** Widest button of a kind of ore; longer names are shortened and shown in full on hover. */
    private static final int ORE_WIDTH = 118;
    private static final int AMOUNT_STEP = 16;
    private static final int AMOUNT_BIG_STEP = 64;
    private static final int DEPTH_BIG_STEP = 8;

    private WorkPostView view;
    private Component status = Component.empty();
    private boolean statusIsError;
    private int scroll;

    private int panelWidth;
    private int panelHeight;
    private int labelWidth;
    private int secondLabelWidth;
    private int professionWidth;
    private int animalWidth;
    private int hireWidth;
    private int modeWidth;
    private int dismissWidth;
    private int stateWidth;
    private int depthValueWidth;
    private int amountValueWidth;
    private int oreWidth;
    private int metalWidth;
    private int strikeWidth;
    private int keepValueWidth;
    /** The metal of the mint the row of a minter shows; empty for the first one. */
    private String mintMetal = "";
    /** Step of the ingots to keep; with Shift the big one. */
    private static final int KEEP_STEP = 16;
    private static final int KEEP_BIG_STEP = 64;

    /** The window of the workers this screen was opened from, {@code null} when opened at the post. */
    @org.jetbrains.annotations.Nullable
    private final net.minecraft.client.gui.screens.Screen parent;

    public WorkPostScreen(WorkPostView view) {
        this(view, null);
    }

    public WorkPostScreen(WorkPostView view, @org.jetbrains.annotations.Nullable net.minecraft.client.gui.screens.Screen parent) {
        super(Component.translatable("block.kingdomworld.work_post"));
        this.view = view;
        this.parent = parent;
    }

    /**
     * Whether a view is of the post this screen shows.
     */
    public boolean shows(WorkPostView other) {
        return view.pos().equals(other.pos()) && (view.place().dimension().isEmpty() || other.place().dimension().isEmpty()
                || view.place().dimension().equals(other.place().dimension()));
    }

    /**
     * Asks the server for the post again, for example after a worker was chosen in the window of the workers.
     */
    public void refresh() {
        if (net.minecraft.client.Minecraft.getInstance().getConnection() != null) {
            send(WorkPostActionPacket.Action.REFRESH, 0, "", null);
        }
    }

    /**
     * The post as the window of the workers knows it: its dimension and place.
     */
    private io.github.sekerator.kingdomworld.common.network.data.WorkforceView.PostRef ref() {
        String dimension = view.place().dimension();
        if (dimension.isEmpty() && minecraft != null && minecraft.level != null) {
            dimension = io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(minecraft.level);
        }
        return new io.github.sekerator.kingdomworld.common.network.data.WorkforceView.PostRef(dimension, view.pos());
    }

    @Override
    public void onClose() {
        if (parent instanceof WorkforceScreen workforce) {
            workforce.refresh();
        }
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public String checkName() {
        return "post/" + view.profession() + "/" + view.status().id() + (view.canManage() ? "" : "/read-only");
    }

    public BlockPos pos() {
        return view.pos();
    }

    public WorkPostView view() {
        return view;
    }

    public void update(WorkPostView next) {
        this.view = next;
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    // ---------------------------------------------------------------- texts

    private boolean isHerder() {
        return view.profession().equals(Professions.HERDER.id());
    }

    private boolean isWorkshop() {
        return Professions.usesStations(view.profession());
    }

    private boolean isMiner() {
        return view.profession().equals(Professions.MINER.id());
    }

    private boolean isMinter() {
        return view.profession().equals(Professions.MINTER.id());
    }

    /**
     * The metal of the mint the row shows, or {@code null} if the mint has none.
     */
    @org.jetbrains.annotations.Nullable
    private io.github.sekerator.kingdomworld.common.network.data.TreasuryView.MintMetal metal() {
        List<io.github.sekerator.kingdomworld.common.network.data.TreasuryView.MintMetal> metals = view.mint().metals();
        if (metals.isEmpty()) {
            return null;
        }
        for (io.github.sekerator.kingdomworld.common.network.data.TreasuryView.MintMetal metal : metals) {
            if (metal.metal().equals(mintMetal)) {
                return metal;
            }
        }
        return metals.get(0);
    }

    private static Component strikeLabel(boolean strike) {
        return Component.translatable(strike ? KEY + "mint.strike_on" : KEY + "mint.strike_off");
    }

    /**
     * For a minter: how many of the metal shown the storehouses hold, in the place of the container, which a minter does
     * not use.
     */
    private Component metalStockLine() {
        var metal = metal();
        if (metal == null) {
            return Component.translatable(KEY + "mint.no_metals");
        }
        return Component.translatable(KEY + "mint.stock", metal.item().getHoverName(), metal.stock());
    }

    private static Component oreName(String id) {
        return id.isEmpty() ? Component.translatable(KEY + "goal.anything") : MineOres.name(id);
    }

    private static Component depthText(boolean auto, int depth) {
        return Component.translatable(auto ? KEY + "goal.depth_auto" : KEY + "goal.depth_manual", depth);
    }

    private Component amountText() {
        int amount = view.goal().amount();
        return amount == 0 ? Component.translatable(KEY + "goal.no_limit") : Component.literal(Integer.toString(amount));
    }

    /**
     * The first line of a mine: the goal when it is met, otherwise the state of the post.
     */
    private Component statusLine() {
        WorkPostView.Goal goal = view.goal();
        if (isMiner() && goal.met() && !view.status().isProblem()) {
            return Component.translatable(KEY + "goal.met", oreName(goal.ore()), goal.stock(), goal.amount());
        }
        return Component.translatable(KEY + "status", statusText(view.status()));
    }

    /**
     * How much of what the mine is asked for the storehouses hold.
     */
    private Component stockLine() {
        WorkPostView.Goal goal = view.goal();
        if (goal.ore().isEmpty()) {
            return Component.translatable(KEY + "goal.none");
        }
        return goal.amount() > 0 ? Component.translatable(KEY + "goal.stock", goal.stock(), goal.amount())
                : Component.translatable(KEY + "goal.stock_any", goal.stock());
    }

    private boolean isBuilder() {
        return view.profession().equals(Professions.BUILDER.id());
    }

    private Component siteLine() {
        return view.site().getString().isEmpty() ? Component.translatable(KEY + "site_none") : view.site();
    }

    private Component placingLine() {
        return view.current().isEmpty() ? Component.translatable(KEY + "placing_none")
                : Component.translatable(KEY + "placing", view.current().getHoverName());
    }

    private boolean hasAuto() {
        return Professions.hasAutoMode(view.profession());
    }

    private static Component modeName(boolean auto) {
        return Component.translatable(auto ? KEY + "mode.auto" : KEY + "mode.orders");
    }

    /**
     * The workstations in the zone: "Workstations: furnace x2, crafting table".
     */
    private Component stationsLine() {
        if (view.stations().isEmpty()) {
            return Component.translatable(KEY + "no_stations");
        }
        MutableComponent list = Component.empty();
        for (int i = 0; i < view.stations().size(); i++) {
            WorkPostView.Station station = view.stations().get(i);
            if (i > 0) {
                list.append(Component.translatable("kingdomworld.screen.orders.separator"));
            }
            Component name = Component.translatable("kingdomworld.station." + station.kind());
            list.append(station.count() > 1 ? Component.translatable(KEY + "station_count", name, station.count()) : name);
        }
        return Component.translatable(KEY + "stations", list);
    }

    private Component currentLine() {
        if (view.current().isEmpty()) {
            return Component.translatable(KEY + "current_none");
        }
        return Component.translatable(KEY + "current", view.current().getHoverName(), view.progress());
    }

    private Component workshopYieldLine() {
        if (view.perDay() <= 0) {
            return Component.translatable(KEY + "made_today", view.producedToday());
        }
        return Component.translatable(view.measured() ? KEY + "workshop_measured" : KEY + "workshop_estimated", view.perDay(),
                view.producedToday());
    }

    private static List<Profession> choices() {
        List<Profession> choices = new ArrayList<>();
        choices.add(Professions.UNEMPLOYED);
        choices.addAll(Professions.postProfessions());
        return choices;
    }

    private static Component professionName(String id) {
        return id.equals(Professions.UNEMPLOYED.id()) ? Component.translatable(KEY + "no_profession") : CitizenTexts.profession(id);
    }

    private static Component animalName(String id) {
        if (id.isEmpty()) {
            return Component.translatable(KEY + "no_animal");
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        EntityType<?> type = key == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(key);
        return type == null ? Component.literal(id) : type.getDescription();
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
     * Labels of the first column: profession, radius, herd size.
     */
    private List<Component> labels() {
        return List.of(Component.translatable(KEY + "profession"), Component.translatable(isMiner() ? KEY + "goal.depth" : KEY + "radius"),
                Component.translatable(hasAuto() ? KEY + "mode" : isMiner() ? KEY + "goal.ore" : isMinter() ? KEY + "mint.metal" : KEY + "herd"));
    }

    /**
     * Labels of the second column: seats, animals.
     */
    private List<Component> secondLabels() {
        return List.of(Component.translatable(KEY + "seats"), Component.translatable(isMiner() ? KEY + "goal.amount"
                : isMinter() ? KEY + "mint.keep" : KEY + "animal"));
    }

    private static int stepperWidth() {
        return 2 * SMALL + 2 * GAP + VALUE_WIDTH;
    }

    /**
     * Width of the first column of controls: a stepper, or for a mine the wider of its depth and its ore.
     */
    private int firstColumnWidth() {
        if (isMinter()) {
            return Math.max(stepperWidth(), metalWidth + GAP + strikeWidth);
        }
        return isMiner() ? Math.max(2 * SMALL + 2 * GAP + depthValueWidth, oreWidth) : stepperWidth();
    }

    private int keepStepperWidth() {
        return 2 * SMALL + 2 * GAP + keepValueWidth;
    }

    private int amountStepperWidth() {
        return 2 * SMALL + 2 * GAP + amountValueWidth;
    }

    private Component containerLine() {
        return Component.translatable(view.container() ? KEY + "container" : KEY + "no_container");
    }

    private Component yieldLine() {
        if (view.perDay() <= 0) {
            return Component.translatable(KEY + "yield_unknown");
        }
        return Component.translatable(view.measured() ? KEY + "yield_measured" : KEY + "yield_estimated", view.perDay());
    }

    private Component todayLine() {
        return Component.translatable(KEY + "today", view.producedToday());
    }

    private Component workersHeader() {
        return Component.translatable(KEY + "workers", view.workers().size(), view.seats());
    }

    private Component hireLabel() {
        return Component.translatable(KEY + "hire", view.unemployed());
    }

    private List<Component> infoLines() {
        if (isBuilder()) {
            return List.of(Component.translatable(KEY + "status", statusText(view.status())), siteLine(), placingLine(),
                    Component.translatable(KEY + "placed_today", view.producedToday()));
        }
        if (isMinter()) {
            return List.of(Component.translatable(KEY + "status", statusText(view.status())), stationsLine(), currentLine(), metalStockLine(),
                    workshopYieldLine());
        }
        if (isWorkshop()) {
            return List.of(Component.translatable(KEY + "status", statusText(view.status())), stationsLine(), currentLine(), containerLine(),
                    workshopYieldLine());
        }
        if (isMiner()) {
            return List.of(statusLine(), containerLine(), yieldLine(), todayLine(), stockLine());
        }
        return List.of(Component.translatable(KEY + "status", statusText(view.status())), containerLine(), yieldLine(), todayLine());
    }

    /**
     * Lines of the info block that name things from the world (workstations, items) and are shortened to fit
     * rather than sizing the panel.
     */
    private boolean isNameLine(int index) {
        return (isWorkshop() || isBuilder()) && (index == 1 || index == 2) || isMinter() && index == 3;
    }

    private Component title() {
        WorkPostView.Place place = view.place();
        if (place.name().isEmpty() && place.number() <= 0) {
            return Component.translatable(KEY + "title", view.pos().getX(), view.pos().getY(), view.pos().getZ());
        }
        return Component.translatable(KEY + "title_named", WorkforceScreen.postName(place.name(), view.profession(), place.number()),
                view.pos().getX(), view.pos().getY(), view.pos().getZ());
    }

    // ---------------------------------------------------------------- layout

    private void measure() {
        labelWidth = 0;
        for (Component label : labels()) {
            labelWidth = Math.max(labelWidth, font.width(label));
        }
        secondLabelWidth = 0;
        for (Component label : secondLabels()) {
            secondLabelWidth = Math.max(secondLabelWidth, font.width(label));
        }
        professionWidth = 0;
        for (Profession profession : choices()) {
            professionWidth = Math.max(professionWidth, buttonWidth(professionName(profession.id())));
        }
        animalWidth = buttonWidth(animalName(""));
        for (String animal : view.animals()) {
            animalWidth = Math.max(animalWidth, buttonWidth(animalName(animal)));
        }
        hireWidth = buttonWidth(Component.translatable(KEY + "hire", 9_999));
        modeWidth = Math.max(buttonWidth(modeName(true)), buttonWidth(modeName(false)));
        dismissWidth = buttonWidth(Component.translatable(KEY + "dismiss"));
        stateWidth = 0;
        for (WorkStatus value : WorkStatus.values()) {
            stateWidth = Math.max(stateWidth, font.width(statusText(value)));
        }
        WorkPostView.Goal goal = view.goal();
        depthValueWidth = VALUE_WIDTH;
        for (int depth : new int[]{goal.depth(), goal.minDepth(), goal.maxDepth()}) {
            depthValueWidth = Math.max(depthValueWidth, Math.max(buttonWidth(depthText(true, depth)), buttonWidth(depthText(false, depth))));
        }
        amountValueWidth = Math.max(VALUE_WIDTH, Math.max(font.width(Integer.toString(MineGoal.MAX_AMOUNT)),
                font.width(Component.translatable(KEY + "goal.no_limit"))) + 4);
        // The ore button shortens a long name; hovering shows it in full.
        oreWidth = font.width(oreName("")) + CycleButton.ICON_SPACE + LayoutCheck.BUTTON_TEXT_MARGIN + 8;
        for (String ore : goal.ores()) {
            oreWidth = Math.max(oreWidth, font.width(oreName(ore)) + CycleButton.ICON_SPACE + LayoutCheck.BUTTON_TEXT_MARGIN + 8);
        }
        oreWidth = Math.min(oreWidth, ORE_WIDTH);
        // The metal button shortens a long name too; hovering shows it in full.
        metalWidth = font.width(Component.translatable(KEY + "mint.no_metals")) / 2 + CycleButton.ICON_SPACE + LayoutCheck.BUTTON_TEXT_MARGIN + 8;
        for (var metal : view.mint().metals()) {
            metalWidth = Math.max(metalWidth, font.width(metal.item().getHoverName()) + CycleButton.ICON_SPACE + LayoutCheck.BUTTON_TEXT_MARGIN + 8);
        }
        metalWidth = Math.min(metalWidth, ORE_WIDTH - 20);
        strikeWidth = Math.max(buttonWidth(strikeLabel(true)), buttonWidth(strikeLabel(false)));
        keepValueWidth = Math.max(VALUE_WIDTH, font.width(Integer.toString(io.github.sekerator.kingdomworld.core.economy.MintMetals.MAX_KEEP)) + 4);
        // The animal button shortens its text when it has to; a few letters must show.
        int controls = labelWidth + 6 + Math.max(Math.max(professionWidth, modeWidth), firstColumnWidth() + 12 + secondLabelWidth + 6
                + Math.max(stepperWidth(), isMiner() ? amountStepperWidth() : isMinter() ? keepStepperWidth() : Math.min(animalWidth, 80)));
        int info = font.width(title()) + 2 * PAD;
        WorkPostView saved = view;
        // The longest numbers the lines can show.
        view = new WorkPostView(new BlockPos(-30_000_000, 319, 29_999_999), saved.kingdomId(), saved.canManage(), saved.profession(),
                64, 64, 16, 16, saved.animal(), saved.animals(), 64, 64, WorkStatus.ZONE_PARTLY_OUTSIDE, false, true,
                999_999, 9_999_999L, List.of(), 9_999, saved.auto(), saved.stations(), saved.current(), 100, saved.site())
                .withPlace(saved.place());
        info = Math.max(info, font.width(title()));
        for (WorkStatus value : WorkStatus.values()) {
            info = Math.max(info, font.width(Component.translatable(KEY + "status", statusText(value))));
        }
        for (boolean container : new boolean[]{true, false}) {
            info = Math.max(info, font.width(Component.translatable(container ? KEY + "container" : KEY + "no_container")));
        }
        info = Math.max(info, Math.max(font.width(Component.translatable(KEY + "yield_measured", 999_999)),
                font.width(Component.translatable(KEY + "yield_estimated", 999_999))));
        info = Math.max(info, Math.max(font.width(Component.translatable(KEY + "yield_unknown")), font.width(todayLine())));
        if (isWorkshop()) {
            info = Math.max(info, Math.max(font.width(Component.translatable(KEY + "workshop_measured", 999_999, 9_999_999L)),
                    font.width(Component.translatable(KEY + "workshop_estimated", 999_999, 9_999_999L))));
            info = Math.max(info, Math.max(font.width(Component.translatable(KEY + "made_today", 9_999_999L)),
                    font.width(Component.translatable(KEY + "no_stations"))));
            info = Math.max(info, font.width(Component.translatable(KEY + "current_none")));
        }
        if (isMinter()) {
            info = Math.max(info, font.width(Component.translatable(KEY + "mint.no_metals")));
        }
        if (isMiner()) {
            info = Math.max(info, Math.max(font.width(Component.translatable(KEY + "goal.stock", 9_999_999L, MineGoal.MAX_AMOUNT)),
                    font.width(Component.translatable(KEY + "goal.stock_any", 9_999_999L))));
            info = Math.max(info, font.width(Component.translatable(KEY + "goal.none")));
        }
        if (isBuilder()) {
            info = Math.max(info, Math.max(font.width(Component.translatable(KEY + "site_none")),
                    font.width(Component.translatable(KEY + "placed_today", 9_999_999L))));
            info = Math.max(info, font.width(Component.translatable(KEY + "placing_none")));
        }
        int header = font.width(Component.translatable(KEY + "workers", 16, 16)) + 8 + hireWidth + 4
                + font.width(Component.translatable("kingdomworld.screen.throne.page", 16, 16, 16));
        view = saved;
        int workers = NAME_COLUMN + stateWidth + 8 + dismissWidth;
        int inner = Math.max(Math.max(controls, info), Math.max(header, workers));
        inner = Math.max(inner, font.width(Component.translatable(KEY + "no_workers")));
        panelWidth = Math.min(Math.max(MIN_PANEL_WIDTH, inner + 2 * PAD), width - 8);
        panelHeight = Math.min(MAX_PANEL_HEIGHT, height - 8);
    }

    private int left() {
        return (width - panelWidth) / 2;
    }

    private int top() {
        return (height - panelHeight) / 2;
    }

    private int settingRows() {
        return isHerder() || hasAuto() || isMiner() || isMinter() ? 3 : 2;
    }

    private int secondColumnX() {
        return controlsX() + firstColumnWidth() + 12;
    }

    private int secondControlsX() {
        return secondColumnX() + secondLabelWidth + 6;
    }

    private int infoTop() {
        return top() + TOP + settingRows() * ROW + 2;
    }

    private int workersTop() {
        return infoTop() + infoLines().size() * (LINE + 1) + 4;
    }

    private int listTop() {
        return workersTop() + ROW;
    }

    private int visibleWorkers() {
        return Math.max(1, (top() + panelHeight - BOTTOM_AREA - listTop()) / WORKER_ROW);
    }

    private int controlsX() {
        return left() + PAD + labelWidth + 6;
    }

    // ---------------------------------------------------------------- widgets

    @Override
    protected void init() {
        measure();
        int x = controlsX();
        int y = top() + TOP;
        boolean manage = view.canManage();
        if (manage) {
            List<Profession> choices = choices();
            int index = 0;
            for (int i = 0; i < choices.size(); i++) {
                if (choices.get(i).id().equals(view.profession())) {
                    index = i;
                }
            }
            int chosen = index;
            addRenderableWidget(new CycleButton(x, y, professionWidth, ROW - 2, professionName(view.profession()),
                    direction -> send(WorkPostActionPacket.Action.PROFESSION, 0,
                            choices.get(Math.floorMod(chosen + direction, choices.size())).id(), null)));
            if (isMiner()) {
                goalWidgets(x, y);
            } else {
                stepper(x, y + ROW, view.radius(), 1, view.maxRadius(), WorkPostActionPacket.Action.RADIUS);
            }
            stepper(secondControlsX(), y + ROW, view.seats(), 1, view.maxSeats(), WorkPostActionPacket.Action.SEATS);
            if (isHerder()) {
                stepper(x, y + 2 * ROW, view.herdSize(), 2, view.maxHerd(), WorkPostActionPacket.Action.HERD);
                List<String> animals = view.animals();
                if (!animals.isEmpty()) {
                    int current = animals.indexOf(view.animal());
                    int available = left() + panelWidth - PAD - secondControlsX();
                    int buttonWidth = Math.min(animalWidth, available);
                    Component label = animalName(view.animal());
                    // Before a kind is chosen the first click takes the first one, or the last one going back.
                    CycleButton button = addRenderableWidget(new CycleButton(secondControlsX(), y + 2 * ROW, buttonWidth, ROW - 2,
                            Component.literal(fit(label.getString(), buttonWidth - LayoutCheck.BUTTON_TEXT_MARGIN - 8)),
                            direction -> send(WorkPostActionPacket.Action.ANIMAL, 0, animals.get(Math.floorMod(
                                    current < 0 ? (direction > 0 ? 0 : -1) : current + direction, animals.size())), null)));
                    button.setTooltip(CycleButton.hinted(label));
                }
            }
            if (hasAuto()) {
                Button mode = addRenderableWidget(Button.builder(modeName(view.auto()),
                        b -> send(WorkPostActionPacket.Action.AUTO, view.auto() ? 0 : 1, "", null))
                        .bounds(x, y + 2 * ROW, modeWidth, ROW - 2).build());
                mode.setTooltip(Tooltip.create(Component.translatable(KEY + "mode_hint")));
            }
            if (isMinter()) {
                mintWidgets(x, y);
            }
            int right = left() + panelWidth - PAD;
            // The candidates: those without work and those of the profession without a post first, then any other worker.
            Button hire = addRenderableWidget(Button.builder(hireLabel(), b -> {
                        if (minecraft != null) {
                            minecraft.setScreen(WorkforceScreen.chooseCitizen(this, view.kingdomId(), ref()));
                        }
                    })
                    .bounds(right - hireWidth, workersTop(), hireWidth, ROW - 2).build());
            hire.active = view.workers().size() < view.seats() && !view.profession().equals(Professions.UNEMPLOYED.id());
            hire.setTooltip(Tooltip.create(Component.translatable(view.profession().equals(Professions.UNEMPLOYED.id()) ? KEY + "hire_no_profession"
                    : view.workers().size() >= view.seats() ? KEY + "hire_full" : KEY + "hire_hint", view.unemployed())));
            int rows = visibleWorkers();
            scroll = Math.max(0, Math.min(scroll, view.workers().size() - rows));
            for (int i = 0; i < rows && scroll + i < view.workers().size(); i++) {
                WorkPostView.Worker worker = view.workers().get(scroll + i);
                UUID id = worker.id();
                addRenderableWidget(Button.builder(Component.translatable(KEY + "dismiss"),
                                b -> send(WorkPostActionPacket.Action.DISMISS, 0, "", id))
                        .bounds(right - dismissWidth, listTop() + i * WORKER_ROW, dismissWidth, WORKER_ROW - 2).build());
            }
        }
        if (!manage && isMinter()) {
            // Without the right to manage citizens the metals are still there to look at, and to change with the right to the
            // treasury.
            mintWidgets(x, y);
        }
        Component doneLabel = Component.translatable(parent != null ? "gui.back" : "gui.done");
        int doneWidth = Math.max(50, buttonWidth(doneLabel));
        addRenderableWidget(Button.builder(doneLabel, b -> onClose())
                .bounds(left() + panelWidth - PAD - doneWidth, top() + panelHeight - 26, doneWidth, 20).build());
    }

    /**
     * The goal of a mine: its depth in the place of the radius, which a mine does not have, the kind of ore below
     * it and how much of it is needed beside that.
     */
    private void goalWidgets(int x, int y) {
        WorkPostView.Goal goal = view.goal();
        int depthY = y + ROW;
        Button lower = addRenderableWidget(Button.builder(Component.literal("-"),
                        b -> sendDepth(goal.depth() - (hasShiftDown() ? DEPTH_BIG_STEP : 1)))
                .bounds(x, depthY, SMALL, ROW - 2).build());
        lower.active = goal.depth() > goal.minDepth();
        Button mode = addRenderableWidget(Button.builder(depthText(goal.autoDepth(), goal.depth()),
                        b -> send(WorkPostActionPacket.Action.GOAL_DEPTH,
                                goal.autoDepth() ? goal.depth() + WorkPostActionPacket.DEPTH_OFFSET : 0, "", null))
                .bounds(x + SMALL + GAP, depthY, depthValueWidth, ROW - 2).build());
        mode.setTooltip(Tooltip.create(Component.translatable(goal.autoDepth() ? KEY + "goal.depth_auto_hint" : KEY + "goal.depth_manual_hint")));
        Button higher = addRenderableWidget(Button.builder(Component.literal("+"),
                        b -> sendDepth(goal.depth() + (hasShiftDown() ? DEPTH_BIG_STEP : 1)))
                .bounds(x + SMALL + GAP + depthValueWidth + GAP, depthY, SMALL, ROW - 2).build());
        higher.active = goal.depth() < goal.maxDepth();

        List<String> ores = new ArrayList<>();
        ores.add("");
        ores.addAll(goal.ores());
        int chosen = Math.max(0, ores.indexOf(goal.ore()));
        Component name = oreName(goal.ore());
        int width = Math.min(oreWidth, firstColumnWidth());
        CycleButton ore = addRenderableWidget(new CycleButton(x, y + 2 * ROW, width, ROW - 2,
                Component.literal(fit(name.getString(), width - CycleButton.ICON_SPACE - LayoutCheck.BUTTON_TEXT_MARGIN - 4)),
                MineOres.icon(goal.ore()),
                direction -> send(WorkPostActionPacket.Action.GOAL_ORE, 0, ores.get(Math.floorMod(chosen + direction, ores.size())), null)));
        ore.setTooltip(CycleButton.hinted(name));

        int amountX = secondControlsX();
        Button less = addRenderableWidget(Button.builder(Component.literal("-"),
                        b -> send(WorkPostActionPacket.Action.GOAL_AMOUNT, goal.amount() - amountStep(), "", null))
                .bounds(amountX, y + 2 * ROW, SMALL, ROW - 2).build());
        // Anything has no stock to count: the amount belongs to a kind of ore.
        less.active = !goal.ore().isEmpty() && goal.amount() > 0;
        less.setTooltip(Tooltip.create(Component.translatable(KEY + "goal.amount_hint")));
        Button more = addRenderableWidget(Button.builder(Component.literal("+"),
                        b -> send(WorkPostActionPacket.Action.GOAL_AMOUNT, Math.min(MineGoal.MAX_AMOUNT, goal.amount() + amountStep()), "", null))
                .bounds(amountX + SMALL + GAP + amountValueWidth + GAP, y + 2 * ROW, SMALL, ROW - 2).build());
        more.active = !goal.ore().isEmpty() && goal.amount() < MineGoal.MAX_AMOUNT;
        more.setTooltip(Tooltip.create(Component.translatable(KEY + "goal.amount_hint")));
    }

    /**
     * The metals of the mint of a minter's kingdom: which one the row shows (a choice of the window only), whether the mint
     * strikes from it and how many of it stay in the storehouses. The two settings belong to the treasury and need its right.
     */
    private void mintWidgets(int x, int y) {
        var metal = metal();
        if (metal == null) {
            return;
        }
        List<io.github.sekerator.kingdomworld.common.network.data.TreasuryView.MintMetal> metals = view.mint().metals();
        int chosen = Math.max(0, metals.indexOf(metal));
        Component name = metal.item().getHoverName();
        int rowY = y + 2 * ROW;
        CycleButton choice = addRenderableWidget(new CycleButton(x, rowY, metalWidth, ROW - 2,
                Component.literal(fit(name.getString(), metalWidth - CycleButton.ICON_SPACE - LayoutCheck.BUTTON_TEXT_MARGIN - 4)), metal.item(),
                direction -> {
                    mintMetal = metals.get(Math.floorMod(chosen + direction, metals.size())).metal();
                    rebuildWidgets();
                }));
        choice.setTooltip(CycleButton.hinted(Component.translatable(KEY + "mint.metal_hint", name,
                io.github.sekerator.kingdomworld.common.economy.MoneyTexts.coins(metal.coin(), metal.count()))));
        if (!view.mint().canManage()) {
            return;
        }
        Button strike = addRenderableWidget(Button.builder(strikeLabel(metal.strike()),
                        b -> send(WorkPostActionPacket.Action.MINT_STRIKE, metal.strike() ? 0 : 1, metal.metal(), null))
                .bounds(x + metalWidth + GAP, rowY, strikeWidth, ROW - 2).build());
        strike.setTooltip(Tooltip.create(Component.translatable(metal.strike() ? KEY + "mint.strike_on_hint" : KEY + "mint.strike_off_hint", name)));
        int keepX = secondControlsX();
        Button less = addRenderableWidget(Button.builder(Component.literal("-"),
                        b -> send(WorkPostActionPacket.Action.MINT_KEEP, Math.max(0, metal.keep() - keepStep()), metal.metal(), null))
                .bounds(keepX, rowY, SMALL, ROW - 2).build());
        less.active = metal.keep() > 0;
        less.setTooltip(Tooltip.create(Component.translatable(KEY + "mint.keep_hint")));
        Button more = addRenderableWidget(Button.builder(Component.literal("+"),
                        b -> send(WorkPostActionPacket.Action.MINT_KEEP,
                                Math.min(io.github.sekerator.kingdomworld.core.economy.MintMetals.MAX_KEEP, metal.keep() + keepStep()), metal.metal(), null))
                .bounds(keepX + SMALL + GAP + keepValueWidth + GAP, rowY, SMALL, ROW - 2).build());
        more.active = metal.keep() < io.github.sekerator.kingdomworld.core.economy.MintMetals.MAX_KEEP;
        more.setTooltip(Tooltip.create(Component.translatable(KEY + "mint.keep_hint")));
    }

    private static int keepStep() {
        return hasShiftDown() ? KEEP_BIG_STEP : KEEP_STEP;
    }

    private static int amountStep() {
        return hasShiftDown() ? AMOUNT_BIG_STEP : AMOUNT_STEP;
    }

    private void sendDepth(int depth) {
        WorkPostView.Goal goal = view.goal();
        int clamped = Math.max(goal.minDepth(), Math.min(goal.maxDepth(), depth));
        send(WorkPostActionPacket.Action.GOAL_DEPTH, clamped + WorkPostActionPacket.DEPTH_OFFSET, "", null);
    }

    /**
     * A minus button, the value and a plus button.
     */
    private void stepper(int x, int y, int value, int min, int max, WorkPostActionPacket.Action action) {
        Button minus = addRenderableWidget(Button.builder(Component.literal("-"), b -> send(action, value - 1, "", null))
                .bounds(x, y, SMALL, ROW - 2).build());
        minus.active = value > min;
        Button plus = addRenderableWidget(Button.builder(Component.literal("+"), b -> send(action, value + 1, "", null))
                .bounds(x + SMALL + GAP + VALUE_WIDTH + GAP, y, SMALL, ROW - 2).build());
        plus.active = value < max;
    }

    private void send(WorkPostActionPacket.Action action, int value, String text, UUID target) {
        KingdomWorldNetwork.sendToServer(new WorkPostActionPacket(view.pos(), action, Math.max(0, value), text, target, view.place().dimension()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, view.workers().size() - visibleWorkers());
        int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        if (next != scroll) {
            scroll = next;
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public void onResult(boolean success, Component message) {
        status = message;
        statusIsError = !success;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = left();
        int top = top();
        int right = left + panelWidth - PAD;
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF0201810);
        graphics.renderOutline(left, top, panelWidth, panelHeight, 0xFFB08850);
        // A long name of the post is shortened and shown in full on hover.
        centeredName(graphics, title().getString(), width / 2, top + 7, 0xFFF0D070, right - left - PAD);

        int x = left + PAD;
        int y = top + TOP;
        List<Component> labels = labels();
        List<Component> second = secondLabels();
        int controls = controlsX();
        int secondX = secondColumnX();
        int secondControls = secondControlsX();
        for (int row = 0; row < settingRows(); row++) {
            label(graphics, labels.get(row), x, y + row * ROW + 5, 0xFFE8E8E8, controls - 2);
            if (row == 1 || row == 2 && (isHerder() || isMiner() || isMinter())) {
                label(graphics, second.get(row - 1), secondX, y + row * ROW + 5, 0xFFE8E8E8, secondControls - 2);
            }
        }
        boolean manage = view.canManage();
        if (!manage) {
            label(graphics, professionName(view.profession()), controls, y + 5, 0xFFFFFFFF, right);
        }
        if (isMiner()) {
            WorkPostView.Goal goal = view.goal();
            if (manage) {
                int amountLeft = secondControls + SMALL + GAP;
                centeredLabel(graphics, amountText(), amountLeft + amountValueWidth / 2, y + 2 * ROW + 5, 0xFFFFFFFF, amountLeft,
                        amountLeft + amountValueWidth);
            } else {
                label(graphics, depthText(goal.autoDepth(), goal.depth()), controls, y + ROW + 5, 0xFFFFFFFF, secondX - 4);
                name(graphics, oreName(goal.ore()).getString(), controls, y + 2 * ROW + 5, 0xFFFFFFFF, secondX - 4 - controls);
                label(graphics, amountText(), secondControls, y + 2 * ROW + 5, 0xFFFFFFFF, right);
            }
        } else {
            value(graphics, view.radius(), view.maxRadius(), controls, y + ROW, manage, secondX - 4);
        }
        value(graphics, view.seats(), view.maxSeats(), secondControls, y + ROW, manage, right);
        if (hasAuto() && !manage) {
            label(graphics, modeName(view.auto()), controls, y + 2 * ROW + 5, 0xFFFFFFFF, right);
        }
        if (isMinter()) {
            renderMint(graphics, controls, secondControls, y + 2 * ROW, right);
        }
        if (isHerder()) {
            value(graphics, view.herdSize(), view.maxHerd(), controls, y + 2 * ROW, manage, secondX - 4);
            if (!manage || view.animals().isEmpty()) {
                name(graphics, animalName(view.animal()).getString(), secondControls, y + 2 * ROW + 5, 0xFFFFFFFF, right - secondControls);
            }
        }

        int infoY = infoTop();
        List<Component> info = infoLines();
        for (int i = 0; i < info.size(); i++) {
            int color = i == 0 ? statusColor(view.status()) : 0xFFD0D0D0;
            if (i == 0 && isMiner()) {
                // The goal that is met names its ore, as long as the game names it.
                boolean met = view.goal().met() && !view.status().isProblem();
                fittedLabel(graphics, info.get(i), x, infoY, met ? 0xFFA0E0A0 : color, right);
            } else if (isNameLine(i)) {
                name(graphics, info.get(i).getString(), x, infoY + i * (LINE + 1), color, right - x);
            } else {
                label(graphics, info.get(i), x, infoY + i * (LINE + 1), color, right);
            }
        }

        int rows = visibleWorkers();
        List<WorkPostView.Worker> workers = view.workers();
        int headerRight = manage ? right - hireWidth - 4 : right;
        if (workers.size() > rows) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(workers.size(), scroll + rows),
                    workers.size());
            int pageX = headerRight - font.width(page);
            label(graphics, page, pageX, workersTop() + 5, 0xFF9A9A9A, headerRight);
            headerRight = pageX - 4;
        }
        label(graphics, workersHeader(), x, workersTop() + 5, 0xFFF0D070, headerRight);
        if (workers.isEmpty()) {
            label(graphics, Component.translatable(KEY + "no_workers"), x, listTop() + 5, 0xFFB8B8B8, right);
        }
        int stateX = x + NAME_COLUMN;
        int stateRight = manage ? right - dismissWidth - 4 : right;
        for (int i = 0; i < rows && scroll + i < workers.size(); i++) {
            WorkPostView.Worker worker = workers.get(scroll + i);
            int rowY = listTop() + i * WORKER_ROW + 4;
            Gender gender = worker.female() ? Gender.FEMALE : Gender.MALE;
            name(graphics, CitizenTexts.name(worker.seed(), gender).getString(), x, rowY, 0xFFFFFFFF, NAME_COLUMN - 4);
            label(graphics, statusText(worker.status()), stateX, rowY, statusColor(worker.status()), stateRight);
        }

        Component bottom = status;
        int color = statusIsError ? 0xFFFF8080 : 0xFFA0E0A0;
        if (bottom.getString().isEmpty() && !manage) {
            bottom = Component.translatable(KEY + "read_only_hint");
            color = 0xFFB8B8B8;
        }
        paragraph(graphics, bottom, x, top + panelHeight - BOTTOM_AREA + 2, panelWidth - 2 * PAD, STATUS_LINES, color);
    }

    /**
     * The settings of the metal the row of a minter shows: as text where the viewer may not change them.
     */
    private void renderMint(GuiGraphics graphics, int controls, int secondControls, int rowY, int right) {
        var metal = metal();
        if (metal == null) {
            label(graphics, Component.translatable(KEY + "mint.no_metals"), controls, rowY + 5, 0xFFB8B8B8, right);
            return;
        }
        Component keep = Component.literal(Integer.toString(metal.keep()));
        if (view.mint().canManage()) {
            int keepLeft = secondControls + SMALL + GAP;
            centeredLabel(graphics, keep, keepLeft + keepValueWidth / 2, rowY + 5, 0xFFFFFFFF, keepLeft, keepLeft + keepValueWidth);
        } else {
            int strikeX = controls + metalWidth + GAP + 4;
            label(graphics, strikeLabel(metal.strike()), strikeX, rowY + 5, metal.strike() ? 0xFFA0E0A0 : 0xFFFF9070, secondColumnX() - 4);
            label(graphics, keep, secondControls, rowY + 5, 0xFFFFFFFF, right);
        }
    }

    private void value(GuiGraphics graphics, int value, int max, int x, int y, boolean manage, int right) {
        Component text = Component.translatable(KEY + "value", value, max);
        if (manage) {
            int center = x + SMALL + GAP + VALUE_WIDTH / 2;
            centeredLabel(graphics, text, center, y + 5, 0xFFFFFFFF, x + SMALL + GAP, x + SMALL + GAP + VALUE_WIDTH);
        } else {
            label(graphics, text, x, y + 5, 0xFFFFFFFF, right);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
