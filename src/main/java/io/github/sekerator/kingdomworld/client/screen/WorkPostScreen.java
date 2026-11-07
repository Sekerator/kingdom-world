package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.WorkPostView;
import io.github.sekerator.kingdomworld.common.network.packet.WorkPostActionPacket;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
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
 * The screen of a work post: its profession, the radius of its zone, its seats, the animals of a herd, why it
 * stands still if it does, what it yields and who works there. Members with the right to manage citizens change
 * the settings, hire citizens without work and dismiss workers; everybody else of the kingdom only looks. Every
 * button sends an intent; the server decides and answers with the result and a fresh screen. Widths come from the
 * translated texts.
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

    public WorkPostScreen(WorkPostView view) {
        super(Component.translatable("block.kingdomworld.work_post"));
        this.view = view;
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
        return List.of(Component.translatable(KEY + "profession"), Component.translatable(KEY + "radius"),
                Component.translatable(hasAuto() ? KEY + "mode" : KEY + "herd"));
    }

    /**
     * Labels of the second column: seats, animals.
     */
    private List<Component> secondLabels() {
        return List.of(Component.translatable(KEY + "seats"), Component.translatable(KEY + "animal"));
    }

    private static int stepperWidth() {
        return 2 * SMALL + 2 * GAP + VALUE_WIDTH;
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
        if (isWorkshop()) {
            return List.of(Component.translatable(KEY + "status", statusText(view.status())), stationsLine(), currentLine(), containerLine(),
                    workshopYieldLine());
        }
        return List.of(Component.translatable(KEY + "status", statusText(view.status())), containerLine(), yieldLine(), todayLine());
    }

    /**
     * Lines of the info block that name things from the world (workstations, items) and are shortened to fit
     * rather than sizing the panel.
     */
    private boolean isNameLine(int index) {
        return isWorkshop() && (index == 1 || index == 2);
    }

    private Component title() {
        return Component.translatable(KEY + "title", view.pos().getX(), view.pos().getY(), view.pos().getZ());
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
        // The animal button shortens its text when it has to; a few letters must show.
        int controls = labelWidth + 6 + Math.max(Math.max(professionWidth, modeWidth), stepperWidth() + 12 + secondLabelWidth + 6
                + Math.max(stepperWidth(), Math.min(animalWidth, 80)));
        int info = font.width(title()) + 2 * PAD;
        WorkPostView saved = view;
        // The longest numbers the lines can show.
        view = new WorkPostView(new BlockPos(-30_000_000, 319, 29_999_999), saved.kingdomId(), saved.canManage(), saved.profession(),
                64, 64, 16, 16, saved.animal(), saved.animals(), 64, 64, WorkStatus.ZONE_PARTLY_OUTSIDE, false, true,
                999_999, 9_999_999L, List.of(), 9_999, saved.auto(), saved.stations(), saved.current(), 100);
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
        return isHerder() || hasAuto() ? 3 : 2;
    }

    private int secondColumnX() {
        return controlsX() + stepperWidth() + 12;
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
            String next = choices.get((index + 1) % choices.size()).id();
            addRenderableWidget(Button.builder(professionName(view.profession()),
                    b -> send(WorkPostActionPacket.Action.PROFESSION, 0, next, null)).bounds(x, y, professionWidth, ROW - 2).build());
            stepper(x, y + ROW, view.radius(), 1, view.maxRadius(), WorkPostActionPacket.Action.RADIUS);
            stepper(secondControlsX(), y + ROW, view.seats(), 1, view.maxSeats(), WorkPostActionPacket.Action.SEATS);
            if (isHerder()) {
                stepper(x, y + 2 * ROW, view.herdSize(), 2, view.maxHerd(), WorkPostActionPacket.Action.HERD);
                List<String> animals = view.animals();
                if (!animals.isEmpty()) {
                    int current = animals.indexOf(view.animal());
                    String nextAnimal = animals.get((current + 1) % animals.size());
                    int available = left() + panelWidth - PAD - secondControlsX();
                    int buttonWidth = Math.min(animalWidth, available);
                    Component label = animalName(view.animal());
                    Button button = addRenderableWidget(Button.builder(Component.literal(fit(label.getString(),
                                    buttonWidth - LayoutCheck.BUTTON_TEXT_MARGIN - 8)),
                            b -> send(WorkPostActionPacket.Action.ANIMAL, 0, nextAnimal, null))
                            .bounds(secondControlsX(), y + 2 * ROW, buttonWidth, ROW - 2).build());
                    button.setTooltip(Tooltip.create(label));
                }
            }
            if (hasAuto()) {
                Button mode = addRenderableWidget(Button.builder(modeName(view.auto()),
                        b -> send(WorkPostActionPacket.Action.AUTO, view.auto() ? 0 : 1, "", null))
                        .bounds(x, y + 2 * ROW, modeWidth, ROW - 2).build());
                mode.setTooltip(Tooltip.create(Component.translatable(KEY + "mode_hint")));
            }
            int right = left() + panelWidth - PAD;
            Button hire = addRenderableWidget(Button.builder(hireLabel(), b -> send(WorkPostActionPacket.Action.HIRE, 0, "", null))
                    .bounds(right - hireWidth, workersTop(), hireWidth, ROW - 2).build());
            hire.active = view.unemployed() > 0 && view.workers().size() < view.seats() && !view.profession().equals(Professions.UNEMPLOYED.id());
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
        int doneWidth = Math.max(50, buttonWidth(Component.translatable("gui.done")));
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left() + panelWidth - PAD - doneWidth, top() + panelHeight - 26, doneWidth, 20).build());
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
        KingdomWorldNetwork.sendToServer(new WorkPostActionPacket(view.pos(), action, Math.max(0, value), text, target));
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
        Component title = title();
        centeredLabel(graphics, title, width / 2, top + 7, 0xFFF0D070, left + PAD, right);

        int x = left + PAD;
        int y = top + TOP;
        List<Component> labels = labels();
        List<Component> second = secondLabels();
        int controls = controlsX();
        int secondX = secondColumnX();
        int secondControls = secondControlsX();
        for (int row = 0; row < settingRows(); row++) {
            label(graphics, labels.get(row), x, y + row * ROW + 5, 0xFFE8E8E8, controls - 2);
            if (row == 1 || row == 2 && isHerder()) {
                label(graphics, second.get(row - 1), secondX, y + row * ROW + 5, 0xFFE8E8E8, secondControls - 2);
            }
        }
        boolean manage = view.canManage();
        if (!manage) {
            label(graphics, professionName(view.profession()), controls, y + 5, 0xFFFFFFFF, right);
        }
        value(graphics, view.radius(), view.maxRadius(), controls, y + ROW, manage, secondX - 4);
        value(graphics, view.seats(), view.maxSeats(), secondControls, y + ROW, manage, right);
        if (hasAuto() && !manage) {
            label(graphics, modeName(view.auto()), controls, y + 2 * ROW + 5, 0xFFFFFFFF, right);
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
            if (isNameLine(i)) {
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
