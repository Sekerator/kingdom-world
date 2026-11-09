package io.github.sekerator.kingdomworld.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.sekerator.kingdomworld.client.KingdomWorldClient;
import io.github.sekerator.kingdomworld.client.map.ClientTerritoryCache;
import io.github.sekerator.kingdomworld.common.kingdom.PlayerNames;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.KingdomMapInfo;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.common.network.packet.ClaimChunkPacket;
import io.github.sekerator.kingdomworld.common.network.packet.MapAreaRequestPacket;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Political map of kingdoms around the player: land in the kingdom's color, borders between owners, capitals
 * with names, and the player's position and heading. Scroll to zoom, drag to move, click land for a card.
 *
 * <p>Opened from the throne screen with a kingdom view and the territory right, it edits land: left click
 * claims a chunk, right click gives it up. The server checks every request.
 *
 * <p>Everything is clipped to the map area by hand instead of with a scissor, so the screen draws the same at
 * any size and scale.
 */
public class KingdomMapScreen extends KingdomScreen implements ResultListener {
    private static final int HEADER = 22;
    private static final int FOOTER = 36;
    private static final double MAX_CELL = 32.0;
    private static final int MAX_VISIBLE = MapAreaRequestPacket.MAX_SIDE - 8;
    private static final long REQUEST_INTERVAL_MILLIS = 300L;
    private static final int PARCHMENT = 0xFFE9D9B0;
    private static final int GRID = 0x22000000;
    private static final int REGION_GRID = 0x44000000;
    private static final int BORDER = 0xE02B1D0E;
    private static final int MAX_CAPITAL_NAME = 120;
    private static final int MAX_CARD_NAME = 200;

    @Nullable
    private final KingdomView editing;
    /** The kingdom a road is planned for, or {@code null}. */
    @Nullable
    private final KingdomView planning;
    /** The points of the road being planned, in blocks, or {@code null}. */
    @Nullable
    private int[] planFrom;
    @Nullable
    private int[] planTo;
    @Nullable
    private io.github.sekerator.kingdomworld.common.network.data.RoadViews.PlanView plan;
    private io.github.sekerator.kingdomworld.core.road.RoadClass planClass = io.github.sekerator.kingdomworld.core.road.RoadClass.GRAVEL;
    private io.github.sekerator.kingdomworld.core.road.Road.Mode planMode = io.github.sekerator.kingdomworld.core.road.Road.Mode.JOINT;
    /** Places of the names drawn on the map this frame, so that later names do not cover earlier ones. */
    private final List<int[]> labels = new java.util.ArrayList<>();
    private double centerX;
    private double centerZ;
    private double cell = 12.0;
    private boolean centered;
    private boolean dragging;
    private double dragDistance;
    private long lastRequest;
    @Nullable
    private UUID selected;
    private Component status = Component.empty();
    private boolean statusIsError;
    private int buttonsLeft;
    /** Whether the map shows the army of the kingdom of the player. */
    private boolean army;
    /** The kind of order being given on the map, or {@code null}. */
    @Nullable
    private String armyOrder;
    /** The squads chosen. */
    private final List<Long> chosen = new java.util.ArrayList<>();
    /** The points on the way, or of a patrol, clicked so far, in blocks. */
    private final List<int[]> armyPoints = new java.util.ArrayList<>();
    private int armyTicks;
    /** The kinds of orders of the buttons of the army, in their order. */
    private static final List<String> ARMY_ORDERS = List.of("move", "hold", "patrol", "guard", "attack", "siege", "escort", "scout", "retreat");
    private static final List<String> FORMATIONS = List.of("line", "column", "wedge");

    /**
     * @param editing the kingdom whose land is edited, or {@code null} to only look
     */
    public KingdomMapScreen(@Nullable KingdomView editing) {
        this(editing, null);
    }

    private KingdomMapScreen(@Nullable KingdomView editing, @Nullable KingdomView planning) {
        super(Component.translatable("kingdomworld.screen.map.title"));
        this.editing = editing != null && editing.can(Permission.TERRITORY) ? editing : null;
        this.planning = planning;
    }

    /**
     * The atlas for planning a road of a kingdom: two points, the way the server finds, its cost, and building it.
     */
    public static KingdomMapScreen forRoads(KingdomView view) {
        return new KingdomMapScreen(null, view);
    }

    @Override
    public String checkName() {
        if (army) {
            return armyOrder != null ? "map/army-order" : "map/army";
        }
        if (planning != null) {
            return plan != null ? "map/roads-plan" : "map/roads";
        }
        return editing != null ? "map/edit" : "map/view";
    }

    /**
     * Shows a plan of a road between two points, used by the client self-check.
     */
    public void showPlanForCheck(int fromX, int fromZ, int toX, int toZ,
                                 @Nullable io.github.sekerator.kingdomworld.common.network.data.RoadViews.PlanView view) {
        planFrom = new int[]{fromX, fromZ};
        planTo = new int[]{toX, toZ};
        plan = view;
    }

    /**
     * The server found a way for the road being planned, or none.
     */
    public void planUpdated(io.github.sekerator.kingdomworld.common.network.data.RoadViews.PlanView view) {
        if (planning == null || !view.kingdomId().equals(planning.id())) {
            return;
        }
        plan = view;
        if (!view.error().isEmpty()) {
            status = Component.translatable("kingdomworld.error." + view.error());
            statusIsError = true;
        } else if (view.searching()) {
            status = Component.translatable("kingdomworld.screen.map.road.searching");
            statusIsError = false;
        } else {
            status = Component.empty();
        }
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    private int mapLeft() {
        return PAD;
    }

    private int mapTop() {
        return HEADER;
    }

    private int mapRight() {
        return width - PAD;
    }

    private int mapBottom() {
        return height - FOOTER;
    }

    private double minCell() {
        return Math.max(2.0, Math.max((mapRight() - mapLeft()) / (double) MAX_VISIBLE, (mapBottom() - mapTop()) / (double) MAX_VISIBLE));
    }

    @Override
    protected void init() {
        LocalPlayer player = minecraft == null ? null : minecraft.player;
        if (!centered && player != null) {
            centerX = player.getX() / 16.0;
            centerZ = player.getZ() / 16.0;
            centered = true;
        }
        cell = Mth.clamp(cell, minCell(), MAX_CELL);
        Component center = Component.translatable("kingdomworld.screen.map.center");
        Component done = Component.translatable("gui.done");
        int doneWidth = Math.max(50, buttonWidth(done));
        int centerWidth = buttonWidth(center);
        int doneX = width - PAD - doneWidth;
        int centerButtonX = doneX - 2 - centerWidth;
        buttonsLeft = centerButtonX;
        if (editing == null && planning == null) {
            Component armyLabel = Component.translatable("kingdomworld.screen.map.army." + (army ? "lands" : "button"));
            int armyWidth = buttonWidth(Component.translatable("kingdomworld.screen.map.army.button"),
                    Component.translatable("kingdomworld.screen.map.army.lands"));
            int armyX = centerButtonX - 2 - armyWidth;
            buttonsLeft = armyX;
            Button toggle = addRenderableWidget(Button.builder(armyLabel, button -> {
                army = !army;
                armyOrder = null;
                armyPoints.clear();
                chosen.clear();
                status = Component.empty();
                if (army) {
                    armyTicks = 0;
                    KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Request());
                }
                rebuildWidgets();
            }).bounds(armyX, 3, armyWidth, 16).build());
            toggle.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("kingdomworld.screen.map.army.button.hint")));
            if (army) {
                initArmy();
            }
        }
        addRenderableWidget(Button.builder(center, button -> {
            if (minecraft != null && minecraft.player != null) {
                centerX = minecraft.player.getX() / 16.0;
                centerZ = minecraft.player.getZ() / 16.0;
            }
        }).bounds(centerButtonX, 3, centerWidth, 16).build());
        addRenderableWidget(Button.builder(done, button -> onClose()).bounds(doneX, 3, doneWidth, 16).build());
        if (planning != null) {
            initPlanning();
        }
        requestVisibleArea(true);
    }

    private static Component classLabel(io.github.sekerator.kingdomworld.core.road.RoadClass value) {
        return Component.translatable("kingdomworld.screen.map.road.class",
                io.github.sekerator.kingdomworld.common.road.RoadTexts.className(value));
    }

    private static Component modeLabel(io.github.sekerator.kingdomworld.core.road.Road.Mode value) {
        return Component.translatable("kingdomworld.screen.map.road.mode." + value.id());
    }

    /**
     * The buttons of planning a road, along the bottom of the map.
     */
    private void initPlanning() {
        int classWidth = 0;
        for (io.github.sekerator.kingdomworld.core.road.RoadClass value : io.github.sekerator.kingdomworld.core.road.RoadClass.values()) {
            classWidth = Math.max(classWidth, buttonWidth(classLabel(value)));
        }
        int modeWidth = Math.max(buttonWidth(modeLabel(io.github.sekerator.kingdomworld.core.road.Road.Mode.JOINT)),
                buttonWidth(modeLabel(io.github.sekerator.kingdomworld.core.road.Road.Mode.PAYER)));
        Component build = Component.translatable("kingdomworld.screen.map.road.build");
        Component reset = Component.translatable("kingdomworld.screen.map.road.reset");
        int y = mapBottom() - 20;
        int x = mapLeft() + 4;
        addRenderableWidget(Button.builder(classLabel(planClass), button -> {
            planClass = planClass.next();
            rebuildWidgets();
        }).bounds(x, y, classWidth, 16).build());
        x += classWidth + 2;
        Button mode = addRenderableWidget(Button.builder(modeLabel(planMode), button -> {
            planMode = planMode == io.github.sekerator.kingdomworld.core.road.Road.Mode.JOINT
                    ? io.github.sekerator.kingdomworld.core.road.Road.Mode.PAYER : io.github.sekerator.kingdomworld.core.road.Road.Mode.JOINT;
            rebuildWidgets();
        }).bounds(x, y, modeWidth, 16).build());
        mode.active = plan != null && !plan.crossed().isEmpty();
        mode.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("kingdomworld.screen.map.road.mode." + planMode.id() + ".hint")));
        x += modeWidth + 2;
        Button go = addRenderableWidget(Button.builder(build, button -> {
            if (plan != null && plan.planId() != 0 && minecraft != null && minecraft.getConnection() != null) {
                KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.RoadPackets.PlanConfirm(planning.id(),
                        plan.planId(), planClass, planMode));
                resetPlan();
            }
        }).bounds(x, y, buttonWidth(build), 16).build());
        go.active = plan != null && plan.planId() != 0 && planning.can(Permission.MANAGE_CONSTRUCTION);
        x += buttonWidth(build) + 2;
        addRenderableWidget(Button.builder(reset, button -> resetPlan()).bounds(x, y, buttonWidth(reset), 16).build());
    }

    private void resetPlan() {
        planFrom = null;
        planTo = null;
        plan = null;
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    /**
     * Shows a given place, used by the client self-check.
     */
    public void showForCheck(double chunkX, double chunkZ, double cellSize, @Nullable UUID card, Component message, boolean error) {
        centerX = chunkX;
        centerZ = chunkZ;
        centered = true;
        cell = cellSize;
        selected = card;
        status = message;
        statusIsError = error;
    }

    @Nullable
    private String dimension() {
        return minecraft == null || minecraft.level == null ? null : minecraft.level.dimension().location().toString();
    }

    // ---------------------------------------------------------------- data

    private int visibleMinX() {
        return Mth.floor(centerX - (mapRight() - mapLeft()) / 2.0 / cell) - 1;
    }

    private int visibleMaxX() {
        return Mth.ceil(centerX + (mapRight() - mapLeft()) / 2.0 / cell) + 1;
    }

    private int visibleMinZ() {
        return Mth.floor(centerZ - (mapBottom() - mapTop()) / 2.0 / cell) - 1;
    }

    private int visibleMaxZ() {
        return Mth.ceil(centerZ + (mapBottom() - mapTop()) / 2.0 / cell) + 1;
    }

    private void requestVisibleArea(boolean force) {
        String dimension = dimension();
        if (dimension == null) {
            return;
        }
        int minX = visibleMinX();
        int maxX = visibleMaxX();
        int minZ = visibleMinZ();
        int maxZ = visibleMaxZ();
        if (!force && ClientTerritoryCache.covers(dimension, minX, minZ, maxX, maxZ)) {
            return;
        }
        long now = Util.getMillis();
        if (!force && now - lastRequest < REQUEST_INTERVAL_MILLIS) {
            return;
        }
        lastRequest = now;
        int side = MapAreaRequestPacket.MAX_SIDE;
        int areaWidth = Math.min(side, maxX - minX + 1 + 16);
        int areaHeight = Math.min(side, maxZ - minZ + 1 + 16);
        int startX = (minX + maxX) / 2 - areaWidth / 2;
        int startZ = (minZ + maxZ) / 2 - areaHeight / 2;
        int limit = ClaimChunkPacket.MAX_CHUNK_COORDINATE;
        startX = Mth.clamp(startX, -limit, limit - areaWidth);
        startZ = Mth.clamp(startZ, -limit, limit - areaHeight);
        KingdomWorldNetwork.sendToServer(new MapAreaRequestPacket(startX, startZ, areaWidth, areaHeight));
    }

    @Override
    public void tick() {
        requestVisibleArea(false);
        if (army && ++armyTicks % 40 == 0 && minecraft != null && minecraft.getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Request());
        }
    }

    // ---------------------------------------------------------------- the army

    /**
     * Shows the army of the kingdom on the map, as for the client self-check.
     */
    public void showArmyForCheck(@Nullable String order, List<Long> squads, List<int[]> points) {
        army = true;
        armyOrder = order;
        chosen.clear();
        chosen.addAll(squads);
        armyPoints.clear();
        armyPoints.addAll(points);
    }

    private static Component armyLabel(String kind, boolean full) {
        return Component.translatable("kingdomworld.screen.map.army." + (full ? "order." : "short.") + kind);
    }

    /**
     * The buttons of the orders along the bottom of the map: a full name where it fits, else a letter; the full name and
     * what to click show on hover.
     */
    private void initArmy() {
        int count = ARMY_ORDERS.size() + 1;
        int available = width - 2 * PAD;
        int buttonWidth = Math.max(12, Math.min(64, (available - 2 * (count - 1)) / count));
        int x = PAD;
        int y = height - FOOTER + 2;
        boolean full = true;
        for (String kind : ARMY_ORDERS) {
            full &= font.width(armyLabel(kind, true)) + io.github.sekerator.kingdomworld.client.screen.LayoutCheck.BUTTON_TEXT_MARGIN <= buttonWidth;
        }
        String formation = currentFormation();
        Component formationFull = Component.translatable("kingdomworld.screen.map.army.formation." + formation);
        full &= font.width(formationFull) + io.github.sekerator.kingdomworld.client.screen.LayoutCheck.BUTTON_TEXT_MARGIN <= buttonWidth;
        for (String kind : ARMY_ORDERS) {
            Button button = addRenderableWidget(Button.builder(armyLabel(kind, full), pressed -> armyButton(kind)).bounds(x, y, buttonWidth, 14).build());
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("kingdomworld.screen.map.army.order." + kind)
                    .append("\n").append(Component.translatable("kingdomworld.screen.map.army.hint." + kind))));
            button.active = !chosen.isEmpty();
            x += buttonWidth + 2;
        }
        Button shape = addRenderableWidget(Button.builder(full ? formationFull : Component.translatable("kingdomworld.screen.map.army.short.formation"),
                pressed -> nextFormation()).bounds(x, y, buttonWidth, 14).build());
        shape.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("kingdomworld.screen.map.army.formation",
                Component.translatable("kingdomworld.screen.map.army.formation." + formation))
                .append("\n").append(Component.translatable("kingdomworld.screen.map.army.formation.hint"))));
        shape.active = !chosen.isEmpty();
    }

    private String currentFormation() {
        for (io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Own squad
                : io.github.sekerator.kingdomworld.client.map.ClientArmyMap.view().own()) {
            if (chosen.contains(squad.id())) {
                return FORMATIONS.contains(squad.formation()) ? squad.formation() : "line";
            }
        }
        return "line";
    }

    private void nextFormation() {
        if (chosen.isEmpty()) {
            return;
        }
        String next = FORMATIONS.get((FORMATIONS.indexOf(currentFormation()) + 1) % FORMATIONS.size());
        KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Command(
                io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Command.Action.FORMATION, List.copyOf(chosen), next,
                new int[0], new int[0], 0, 0));
    }

    private void armyButton(String kind) {
        if (chosen.isEmpty()) {
            return;
        }
        armyPoints.clear();
        if (kind.equals("retreat")) {
            sendOrder(kind, new int[0], 0);
            return;
        }
        armyOrder = kind;
        status = Component.empty();
    }

    private void sendOrder(String kind, int[] points, long target) {
        int[] via = new int[0];
        if (!kind.equals("patrol")) {
            via = new int[armyPoints.size() * 2];
            for (int i = 0; i < armyPoints.size(); i++) {
                via[2 * i] = armyPoints.get(i)[0];
                via[2 * i + 1] = armyPoints.get(i)[1];
            }
        }
        int radius = kind.equals("guard") || kind.equals("scout") ? 1 : 0;
        KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Command(
                io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Command.Action.ORDER, List.copyOf(chosen), kind, points, via,
                target, radius));
        armyOrder = null;
        armyPoints.clear();
    }

    private int blockAtX(double mouseX) {
        return Mth.floor((centerX + (mouseX - (mapLeft() + mapRight()) / 2.0) / cell) * 16.0);
    }

    private int blockAtZ(double mouseY) {
        return Mth.floor((centerZ + (mouseY - (mapTop() + mapBottom()) / 2.0) / cell) * 16.0);
    }

    private static boolean near(int x, int y, double mouseX, double mouseY) {
        return Math.abs(mouseX - x) <= 5 && Math.abs(mouseY - y) <= 5;
    }

    /**
     * A click on the map of the army: choosing squads, the points of an order, its target.
     */
    private void armyClick(double mouseX, double mouseY, int button) {
        io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.View view = io.github.sekerator.kingdomworld.client.map.ClientArmyMap.view();
        int bx = blockAtX(mouseX);
        int bz = blockAtZ(mouseY);
        if (armyOrder == null) {
            if (button != 0) {
                return;
            }
            boolean add = hasShiftDown();
            for (io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Own squad : view.own()) {
                if (near(blockScreenX(squad.x()), blockScreenZ(squad.z()), mouseX, mouseY)) {
                    if (!add) {
                        chosen.clear();
                        chosen.add(squad.id());
                    } else if (!chosen.remove(squad.id()) && chosen.size() < io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.MAX_SQUADS) {
                        chosen.add(squad.id());
                    }
                    rebuildWidgets();
                    return;
                }
            }
            if (!add && !chosen.isEmpty()) {
                chosen.clear();
                rebuildWidgets();
            }
            return;
        }
        int limit = io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.MAX_POINTS;
        if (armyOrder.equals("patrol")) {
            if (button == 0 && armyPoints.size() < limit) {
                armyPoints.add(new int[]{bx, bz});
            } else if (button == 1 && !armyPoints.isEmpty()) {
                int[] route = new int[armyPoints.size() * 2];
                for (int i = 0; i < armyPoints.size(); i++) {
                    route[2 * i] = armyPoints.get(i)[0];
                    route[2 * i + 1] = armyPoints.get(i)[1];
                }
                sendOrder("patrol", route, 0);
            }
            return;
        }
        if (button == 1) {
            // A point on the way: around the enemy, to its flank or rear.
            if (!armyOrder.equals("escort") && armyPoints.size() < limit) {
                armyPoints.add(new int[]{bx, bz});
            }
            return;
        }
        if (button != 0) {
            return;
        }
        if (armyOrder.equals("escort")) {
            long now = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0;
            for (io.github.sekerator.kingdomworld.common.network.data.TradeViews.CaravanInfo caravan
                    : io.github.sekerator.kingdomworld.client.map.ClientTradeCache.caravans()) {
                double[] at = caravan.position(now);
                if (near(blockScreenX(at[0]), blockScreenZ(at[1]), mouseX, mouseY)) {
                    sendOrder("escort", new int[0], caravan.id());
                    return;
                }
            }
            return;
        }
        if (armyOrder.equals("attack")) {
            List<io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Other> targets = new java.util.ArrayList<>(view.others());
            targets.addAll(view.marks());
            for (io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Other other : targets) {
                if (!other.ally() && near(blockScreenX(other.x()), blockScreenZ(other.z()), mouseX, mouseY)) {
                    sendOrder("attack", new int[]{other.x(), other.z()}, other.id());
                    return;
                }
            }
        }
        sendOrder(armyOrder, new int[]{bx, bz}, 0);
    }

    private static String clock(int seconds) {
        return seconds >= 3_600 ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", seconds / 3_600, seconds / 60 % 60, seconds % 60)
                : String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private void marker(GuiGraphics graphics, int x, int y, int fill, int border, boolean pale) {
        if (x - 4 < mapLeft() || x + 4 > mapRight() || y - 4 < mapTop() || y + 4 > mapBottom()) {
            return;
        }
        int alpha = pale ? 0x60000000 : 0xFF000000;
        graphics.fill(x - 4, y - 4, x + 4, y + 4, (border & 0x00FFFFFF) | alpha);
        graphics.fill(x - 3, y - 3, x + 3, y + 3, (fill & 0x00FFFFFF) | alpha);
    }

    /**
     * The squads of the kingdom, the squads of others in sight, the pale marks of those seen last, the battles, and the
     * order being given.
     */
    private void drawArmy(GuiGraphics graphics) {
        io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.View view = io.github.sekerator.kingdomworld.client.map.ClientArmyMap.view();
        if (!view.allowed() || !view.dimension().equals(dimension())) {
            return;
        }
        for (io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Other mark : view.marks()) {
            int x = blockScreenX(mark.x());
            int y = blockScreenZ(mark.z());
            marker(graphics, x, y, mark.color(), mark.ally() ? 0xFF2E8B3A : 0xFFB02020, true);
            hoverText(x - 4, y - 4, 8, Component.translatable("kingdomworld.screen.map.army.last_seen", mark.kingdom(), mark.size(),
                    clock(mark.seconds())));
        }
        for (io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Other other : view.others()) {
            int x = blockScreenX(other.x());
            int y = blockScreenZ(other.z());
            marker(graphics, x, y, other.color(), other.ally() ? 0xFF2E8B3A : 0xFFB02020, false);
            hoverText(x - 4, y - 4, 8, Component.translatable("kingdomworld.screen.map.army." + (other.ally() ? "ally" : "enemy"), other.kingdom(),
                    other.size()));
        }
        for (io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Own squad : view.own()) {
            int x = blockScreenX(squad.x());
            int y = blockScreenZ(squad.z());
            boolean picked = chosen.contains(squad.id());
            int[] path = squad.path();
            double fromX = squad.x();
            double fromZ = squad.z();
            for (int i = 0; i + 1 < path.length; i += 2) {
                line(graphics, fromX, fromZ, path[i], path[i + 1], 1, picked ? 0xFFFFE040 : 0xFF3A2A14, true);
                fromX = path[i];
                fromZ = path[i + 1];
            }
            marker(graphics, x, y, view.color(), picked ? 0xFFFFE040 : 0xFF101010, false);
            Component state = squad.state() == 2 ? Component.translatable("kingdomworld.screen.map.army.state.battle")
                    : squad.state() == 1 ? Component.translatable("kingdomworld.screen.map.army.state.marching", clock(squad.seconds()))
                    : Component.translatable("kingdomworld.screen.map.army.state.standing");
            hoverText(x - 4, y - 4, 8, Component.translatable("kingdomworld.screen.map.army.own",
                    Component.translatable("kingdomworld.army." + (squad.band() ? "band_squad" : "squad"), squad.id()), squad.size(),
                    Component.translatable("kingdomworld.army.order." + squad.order()), state));
        }
        int[] battles = view.battles();
        for (int i = 0; i + 1 < battles.length; i += 2) {
            int x = blockScreenX(battles[i]);
            int y = blockScreenZ(battles[i + 1]);
            if (x - 5 < mapLeft() || x + 5 > mapRight() || y - 5 < mapTop() || y + 5 > mapBottom()) {
                continue;
            }
            for (int d = -4; d <= 4; d++) {
                graphics.fill(x + d, y + d, x + d + 2, y + d + 2, 0xFFD02020);
                graphics.fill(x + d, y - d, x + d + 2, y - d + 2, 0xFFD02020);
            }
            hoverText(x - 5, y - 5, 10, Component.translatable("kingdomworld.screen.map.army.battle"));
        }
        if (armyOrder != null && !armyPoints.isEmpty()) {
            int[] previous = null;
            for (int[] point : armyPoints) {
                if (previous != null) {
                    line(graphics, previous[0], previous[1], point[0], point[1], 1, 0xFFFFFFFF, true);
                }
                int x = blockScreenX(point[0]);
                int y = blockScreenZ(point[1]);
                mapFill(graphics, x - 2, y - 2, x + 2, y + 2, 0xFFFFFFFF);
                previous = point;
            }
        }
    }

    /**
     * The line under the buttons of the army: the answer of the server, else what to do now.
     */
    private void drawArmyFooter(GuiGraphics graphics) {
        io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.View view = io.github.sekerator.kingdomworld.client.map.ClientArmyMap.view();
        Component line;
        int color = 0xFFA8A8A8;
        if (!status.getString().isEmpty()) {
            line = status;
            color = statusIsError ? 0xFFFF8080 : 0xFFA0E0A0;
        } else if (io.github.sekerator.kingdomworld.client.map.ClientArmyMap.answered() && !view.allowed()) {
            line = Component.translatable("kingdomworld.screen.map.army.no_rights");
            color = 0xFFFF8080;
        } else if (armyOrder != null) {
            line = Component.translatable("kingdomworld.screen.map.army.hint." + armyOrder);
        } else if (chosen.isEmpty()) {
            line = Component.translatable("kingdomworld.screen.map.army.hint.choose");
        } else {
            line = Component.translatable("kingdomworld.screen.map.army.hint.chosen", chosen.size());
        }
        paragraph(graphics, line, PAD, height - FOOTER + 20, width - 2 * PAD, 1, color);
    }

    // ---------------------------------------------------------------- input

    private int chunkAtX(double mouseX) {
        return Mth.floor(centerX + (mouseX - (mapLeft() + mapRight()) / 2.0) / cell);
    }

    private int chunkAtZ(double mouseY) {
        return Mth.floor(centerZ + (mouseY - (mapTop() + mapBottom()) / 2.0) / cell);
    }

    private boolean insideMap(double mouseX, double mouseY) {
        return mouseX >= mapLeft() && mouseX < mapRight() && mouseY >= mapTop() && mouseY < mapBottom();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!insideMap(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        double beforeX = centerX + (mouseX - (mapLeft() + mapRight()) / 2.0) / cell;
        double beforeZ = centerZ + (mouseY - (mapTop() + mapBottom()) / 2.0) / cell;
        cell = Mth.clamp(cell * (delta > 0 ? 1.25 : 0.8), minCell(), MAX_CELL);
        // Keep the point under the cursor where it was.
        centerX = beforeX - (mouseX - (mapLeft() + mapRight()) / 2.0) / cell;
        centerZ = beforeZ - (mouseY - (mapTop() + mapBottom()) / 2.0) / cell;
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (insideMap(mouseX, mouseY)) {
            dragging = false;
            dragDistance = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && insideMap(mouseX, mouseY)) {
            dragDistance += Math.abs(dragX) + Math.abs(dragY);
            if (dragDistance > 3) {
                dragging = true;
            }
            centerX -= dragX / cell;
            centerZ -= dragY / cell;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (insideMap(mouseX, mouseY) && !dragging && getChildAt(mouseX, mouseY).isEmpty()) {
            if (army) {
                armyClick(mouseX, mouseY, button);
            } else {
                onMapClick(chunkAtX(mouseX), chunkAtZ(mouseY), button);
            }
        }
        dragging = false;
        dragDistance = 0;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void onMapClick(int chunkX, int chunkZ, int button) {
        UUID owner = ClientTerritoryCache.ownerOf(chunkX, chunkZ);
        if (planning != null) {
            if (button == 1) {
                resetPlan();
                return;
            }
            int[] point = {chunkX * 16 + 8, chunkZ * 16 + 8};
            if (planFrom == null || planTo != null) {
                planFrom = point;
                planTo = null;
                plan = null;
                rebuildWidgets();
                return;
            }
            planTo = point;
            plan = null;
            if (minecraft != null && minecraft.getConnection() != null) {
                KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.RoadPackets.PlanRequest(planning.id(),
                        planFrom[0], planFrom[1], planTo[0], planTo[1]));
            }
            rebuildWidgets();
            return;
        }
        if (editing != null) {
            if (button == 0 && owner == null) {
                KingdomWorldNetwork.sendToServer(new ClaimChunkPacket(chunkX, chunkZ, true));
                return;
            }
            if (button == 1 && editing.id().equals(owner)) {
                KingdomWorldNetwork.sendToServer(new ClaimChunkPacket(chunkX, chunkZ, false));
                return;
            }
        }
        if (button == 0) {
            selected = owner;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (KingdomWorldClient.MAP_KEY.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (army && armyOrder != null && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            // Escape first drops the order being given, then closes the map.
            armyOrder = null;
            armyPoints.clear();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * The last answer of the server shown in the status line.
     */
    public String statusText() {
        return status.getString();
    }

    @Override
    public void onResult(boolean success, Component message) {
        status = message;
        statusIsError = !success;
    }

    // ---------------------------------------------------------------- drawing

    private int screenX(double chunkX) {
        return (int) Math.floor((mapLeft() + mapRight()) / 2.0 + (chunkX - centerX) * cell);
    }

    private int screenZ(double chunkZ) {
        return (int) Math.floor((mapTop() + mapBottom()) / 2.0 + (chunkZ - centerZ) * cell);
    }

    /**
     * A rectangle cut to the map area.
     */
    private void mapFill(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int left = Math.max(x0, mapLeft());
        int top = Math.max(y0, mapTop());
        int right = Math.min(x1, mapRight());
        int bottom = Math.min(y1, mapBottom());
        if (left < right && top < bottom) {
            graphics.fill(left, top, right, bottom, color);
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF1A140C);
        int left = mapLeft();
        int top = mapTop();
        int right = mapRight();
        int bottom = mapBottom();
        graphics.fill(left, top, right, bottom, PARCHMENT);
        int minX = visibleMinX();
        int maxX = visibleMaxX();
        int minZ = visibleMinZ();
        int maxZ = visibleMaxZ();
        drawGrid(graphics, minX, maxX, minZ, maxZ);
        drawLand(graphics, minX, maxX, minZ, maxZ);
        drawBorders(graphics, minX, maxX, minZ, maxZ);
        labels.clear();
        drawRoads(graphics);
        drawWalls(graphics);
        drawCapitals(graphics, minX, maxX, minZ, maxZ);
        drawDiplomacy(graphics);
        drawCities(graphics);
        drawCamps(graphics);
        drawLifeEvents(graphics);
        drawCaravans(graphics);
        if (army) {
            drawArmy(graphics);
        }
        if (planning != null) {
            drawPlan(graphics);
        }
        drawPlayer(graphics, partialTick);
        if (editing != null && insideMap(mouseX, mouseY)) {
            int x = screenX(chunkAtX(mouseX));
            int z = screenZ(chunkAtZ(mouseY));
            int size = Math.max(1, (int) Math.ceil(cell));
            mapFill(graphics, x, z, x + size, z + 1, 0xFFFFFFFF);
            mapFill(graphics, x, z + size - 1, x + size, z + size, 0xFFFFFFFF);
            mapFill(graphics, x, z, x + 1, z + size, 0xFFFFFFFF);
            mapFill(graphics, x + size - 1, z, x + size, z + size, 0xFFFFFFFF);
        }
        graphics.renderOutline(left - 1, top - 1, right - left + 2, bottom - top + 2, 0xFF6B4E2A);
        drawHeader(graphics);
        drawFooter(graphics, mouseX, mouseY);
        drawCard(graphics);
    }

    private void drawHeader(GuiGraphics graphics) {
        int available = buttonsLeft - 6 - PAD;
        Component heading;
        if (planning != null) {
            int prefix = font.width(Component.translatable("kingdomworld.screen.map.title_roads", ""));
            String shown = fit(planning.name(), available - prefix);
            heading = Component.translatable("kingdomworld.screen.map.title_roads", shown);
            if (!shown.equals(planning.name())) {
                hoverFull(PAD, 7, font.width(heading), Component.translatable("kingdomworld.screen.map.title_roads", planning.name()));
            }
        } else if (editing != null) {
            int prefix = font.width(Component.translatable("kingdomworld.screen.map.title_edit", ""));
            String shown = fit(editing.name(), available - prefix);
            heading = Component.translatable("kingdomworld.screen.map.title_edit", shown);
            if (!shown.equals(editing.name())) {
                hoverFull(PAD, 7, font.width(heading), Component.translatable("kingdomworld.screen.map.title_edit", editing.name()));
            }
        } else {
            heading = title;
        }
        label(graphics, heading, PAD, 7, 0xFFF0D070, buttonsLeft - 6);
    }

    private void drawGrid(GuiGraphics graphics, int minX, int maxX, int minZ, int maxZ) {
        for (int x = minX; x <= maxX + 1; x++) {
            boolean region = Math.floorMod(x, 32) == 0;
            if (cell >= 6 || region) {
                mapFill(graphics, screenX(x), mapTop(), screenX(x) + 1, mapBottom(), region ? REGION_GRID : GRID);
            }
        }
        for (int z = minZ; z <= maxZ + 1; z++) {
            boolean region = Math.floorMod(z, 32) == 0;
            if (cell >= 6 || region) {
                mapFill(graphics, mapLeft(), screenZ(z), mapRight(), screenZ(z) + 1, region ? REGION_GRID : GRID);
            }
        }
    }

    private void drawLand(GuiGraphics graphics, int minX, int maxX, int minZ, int maxZ) {
        for (int z = minZ; z <= maxZ; z++) {
            int y0 = screenZ(z);
            int y1 = screenZ(z + 1);
            int runStart = minX;
            UUID runOwner = ClientTerritoryCache.ownerOf(minX, z);
            for (int x = minX + 1; x <= maxX + 1; x++) {
                UUID owner = x <= maxX ? ClientTerritoryCache.ownerOf(x, z) : null;
                if (x > maxX || !Objects.equals(owner, runOwner)) {
                    if (runOwner != null) {
                        KingdomMapInfo info = ClientTerritoryCache.kingdom(runOwner);
                        int color = info == null ? 0x808080 : info.color();
                        mapFill(graphics, screenX(runStart), y0, screenX(x), y1, 0x99000000 | color);
                    }
                    runStart = x;
                    runOwner = owner;
                }
            }
        }
    }

    private void drawBorders(GuiGraphics graphics, int minX, int maxX, int minZ, int maxZ) {
        int thickness = cell >= 10 ? 2 : 1;
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                UUID owner = ClientTerritoryCache.ownerOf(x, z);
                UUID east = ClientTerritoryCache.ownerOf(x + 1, z);
                UUID south = ClientTerritoryCache.ownerOf(x, z + 1);
                if (!Objects.equals(owner, east) && (owner != null || east != null)) {
                    int sx = screenX(x + 1);
                    mapFill(graphics, sx - thickness / 2, screenZ(z), sx - thickness / 2 + thickness, screenZ(z + 1), BORDER);
                }
                if (!Objects.equals(owner, south) && (owner != null || south != null)) {
                    int sz = screenZ(z + 1);
                    mapFill(graphics, screenX(x), sz - thickness / 2, screenX(x + 1), sz - thickness / 2 + thickness, BORDER);
                }
            }
        }
    }

    private void drawCapitals(GuiGraphics graphics, int minX, int maxX, int minZ, int maxZ) {
        for (KingdomMapInfo info : ClientTerritoryCache.kingdoms().values()) {
            if (!info.hasCapital() || info.capitalChunkX() < minX - 4 || info.capitalChunkX() > maxX + 4
                    || info.capitalChunkZ() < minZ - 4 || info.capitalChunkZ() > maxZ + 4) {
                continue;
            }
            int x = screenX(info.capitalChunkX() + 0.5);
            int y = screenZ(info.capitalChunkZ() + 0.5);
            if (x - 6 < mapLeft() || x + 7 > mapRight() || y - 5 < mapTop() || y + 5 > mapBottom()) {
                continue;
            }
            // A small crown: dark outline, gold band and three points.
            graphics.fill(x - 5, y - 4, x + 6, y + 4, 0xFF2B1D0E);
            graphics.fill(x - 4, y, x + 5, y + 3, 0xFFF0C030);
            graphics.fill(x - 4, y - 3, x - 2, y, 0xFFF0C030);
            graphics.fill(x - 1, y - 3, x + 2, y, 0xFFF0C030);
            graphics.fill(x + 3, y - 3, x + 5, y, 0xFFF0C030);
            graphics.fill(x - 1, y + 1, x + 2, y + 2, 0xFFC03030);
            // The name stands on a dark plate so that it is readable on parchment and on land of any color.
            int maxName = Math.min(MAX_CAPITAL_NAME, mapRight() - mapLeft() - 8);
            int nameWidth = font.width(fit(info.name(), maxName));
            int nameX = Mth.clamp(x - nameWidth / 2, mapLeft() + 3, mapRight() - 3 - nameWidth);
            int nameY = y - 17;
            if (nameY - 2 < mapTop()) {
                nameY = y + 7;
            }
            graphics.fill(nameX - 2, nameY - 2, nameX + nameWidth + 2, nameY + 9, 0xC0100C08);
            name(graphics, info.name(), nameX, nameY, 0xFF000000 | FoundKingdomScreen.readable(info.color()), maxName);
            labels.add(new int[]{nameX - 2, nameY - 2, nameX + nameWidth + 2, nameY + 9});
        }
    }

    /**
     * Caravans on the way between capitals: a small cart in the color of the seller where the time says it is.
     */
    private void drawCaravans(GuiGraphics graphics) {
        String dimension = dimension();
        if (dimension == null || minecraft == null || minecraft.level == null) {
            return;
        }
        long now = minecraft.level.getGameTime();
        for (io.github.sekerator.kingdomworld.common.network.data.TradeViews.CaravanInfo caravan
                : io.github.sekerator.kingdomworld.client.map.ClientTradeCache.caravans()) {
            if (!caravan.dimension().equals(dimension)) {
                continue;
            }
            double progress = caravan.progress(now);
            // Along its way through the roads, where the time says it is.
            double[] at = caravan.position(now);
            double blockX = at[0];
            double blockZ = at[1];
            int x = screenX(blockX / 16.0);
            int y = screenZ(blockZ / 16.0);
            if (x - 4 < mapLeft() || x + 4 > mapRight() || y - 4 < mapTop() || y + 4 > mapBottom()) {
                continue;
            }
            graphics.fill(x - 4, y - 3, x + 4, y + 2, 0xFF2B1D0E);
            graphics.fill(x - 3, y - 2, x + 3, y + 1, 0xFF000000 | caravan.color());
            graphics.fill(x - 3, y + 2, x - 1, y + 4, 0xFF2B1D0E);
            graphics.fill(x + 1, y + 2, x + 3, y + 4, 0xFF2B1D0E);
            KingdomMapInfo from = ClientTerritoryCache.kingdom(caravan.seller());
            KingdomMapInfo to = ClientTerritoryCache.kingdom(caravan.buyer());
            hoverText(x - 4, y - 4, 8, Component.translatable("kingdomworld.screen.map.caravan", from == null ? "?" : from.name(),
                    to == null ? "?" : to.name(), (int) Math.round(progress * 100)));
        }
    }

    private void drawPlayer(GuiGraphics graphics, float partialTick) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        LocalPlayer player = minecraft.player;
        double px = (mapLeft() + mapRight()) / 2.0 + (Mth.lerp(partialTick, player.xo, player.getX()) / 16.0 - centerX) * cell;
        double pz = (mapTop() + mapBottom()) / 2.0 + (Mth.lerp(partialTick, player.zo, player.getZ()) / 16.0 - centerZ) * cell;
        if (px < mapLeft() + 6 || px > mapRight() - 6 || pz < mapTop() + 6 || pz > mapBottom() - 6) {
            return;
        }
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(px, pz, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(player.getYRot() + 180.0f));
        for (int row = 0; row < 8; row++) {
            int half = row / 2 + 1;
            graphics.fill(-half - 1, row - 5, half + 1, row - 4, 0xFF000000);
        }
        for (int row = 1; row < 7; row++) {
            int half = row / 2;
            graphics.fill(-half, row - 5, half + 1, row - 4, 0xFFFFFFFF);
        }
        pose.popPose();
    }

    private void drawFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        if (army) {
            drawArmyFooter(graphics);
            return;
        }
        int y = height - FOOTER + 4;
        int textWidth = width - 2 * PAD;
        if (insideMap(mouseX, mouseY)) {
            int cx = chunkAtX(mouseX);
            int cz = chunkAtZ(mouseY);
            KingdomMapInfo info = ClientTerritoryCache.kingdom(ClientTerritoryCache.ownerOf(cx, cz));
            Component where = Component.translatable("kingdomworld.screen.map.chunk", cx, cz, cx * 16, cz * 16);
            if (info != null) {
                int prefix = font.width(Component.translatable("kingdomworld.screen.map.chunk_owned", where, ""));
                where = Component.translatable("kingdomworld.screen.map.chunk_owned", where, fit(info.name(), textWidth - prefix));
            }
            label(graphics, where, PAD, y, 0xFFE8E8E8, width - PAD);
        }
        Component hint = planning != null
                ? Component.translatable("kingdomworld.screen.map.hint_roads")
                : editing != null
                ? Component.translatable("kingdomworld.screen.map.hint_edit")
                : Component.translatable("kingdomworld.screen.map.hint", KingdomWorldClient.MAP_KEY.getTranslatedKeyMessage());
        boolean showStatus = !status.getString().isEmpty();
        paragraph(graphics, showStatus ? status : hint, PAD, y + 11, textWidth, 2,
                showStatus ? (statusIsError ? 0xFFFF8080 : 0xFFA0E0A0) : 0xFFA8A8A8);
    }

    // ---------------------------------------------------------------- roads

    private int blockScreenX(double blockX) {
        return screenX(blockX / 16.0);
    }

    private int blockScreenZ(double blockZ) {
        return screenZ(blockZ / 16.0);
    }

    /**
     * A line on the map as a row of small squares, cut to the map; every other square for a dashed one.
     */
    private void line(GuiGraphics graphics, double x0, double z0, double x1, double z1, int size, int color, boolean dashed) {
        int sx0 = blockScreenX(x0);
        int sz0 = blockScreenZ(z0);
        int sx1 = blockScreenX(x1);
        int sz1 = blockScreenZ(z1);
        int steps = Math.max(Math.abs(sx1 - sx0), Math.abs(sz1 - sz0));
        if (steps > 4_000 || (Math.max(sx0, sx1) < mapLeft() || Math.min(sx0, sx1) > mapRight()
                || Math.max(sz0, sz1) < mapTop() || Math.min(sz0, sz1) > mapBottom())) {
            return;
        }
        int half = size / 2;
        for (int i = 0; i <= steps; i += Math.max(1, size - 1)) {
            if (dashed && (i / Math.max(2, size * 2)) % 2 == 1) {
                continue;
            }
            double t = steps == 0 ? 0 : i / (double) steps;
            int x = (int) Math.round(sx0 + (sx1 - sx0) * t);
            int z = (int) Math.round(sz0 + (sz1 - sz0) * t);
            mapFill(graphics, x - half, z - half, x - half + size, z - half + size, color);
        }
    }

    static int roadColor(io.github.sekerator.kingdomworld.core.road.RoadClass roadClass) {
        return switch (roadClass) {
            case PATH -> 0xFF8B6B3A;
            case GRAVEL -> 0xFF6E6E6E;
            case PAVED -> 0xFFE0DCCF;
        };
    }

    private void drawRoads(GuiGraphics graphics) {
        String dimension = dimension();
        if (dimension == null) {
            return;
        }
        int size = cell >= 12 ? 3 : 2;
        for (io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapRoad road
                : io.github.sekerator.kingdomworld.client.map.ClientRoadCache.data().roads()) {
            if (!road.dimension().equals(dimension) || road.xs().length < 2) {
                continue;
            }
            int builtPoints = (int) Math.round((road.xs().length - 1) * road.built() / 1000.0);
            for (int i = 1; i < road.xs().length; i++) {
                boolean built = i <= builtPoints;
                line(graphics, road.xs()[i - 1] + 0.5, road.zs()[i - 1] + 0.5, road.xs()[i] + 0.5, road.zs()[i] + 0.5, size + 1, 0xFF2B1D0E, !built);
                line(graphics, road.xs()[i - 1] + 0.5, road.zs()[i - 1] + 0.5, road.xs()[i] + 0.5, road.zs()[i] + 0.5, size - 1,
                        roadColor(road.roadClass()), !built);
            }
        }
    }

    /**
     * City walls: a square ring in the color of stone or wood, dashed while it is being built, with the gates in gold.
     */
    private void drawWalls(GuiGraphics graphics) {
        String dimension = dimension();
        if (dimension == null) {
            return;
        }
        for (io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapWall wall
                : io.github.sekerator.kingdomworld.client.map.ClientRoadCache.data().walls()) {
            if (!wall.dimension().equals(dimension) || wall.radius() <= 0) {
                continue;
            }
            double west = wall.x() - wall.radius() + 0.5;
            double east = wall.x() + wall.radius() + 0.5;
            double north = wall.z() - wall.radius() + 0.5;
            double south = wall.z() + wall.radius() + 0.5;
            int color = wall.wallClass() == io.github.sekerator.kingdomworld.core.road.Wall.WallClass.STONE ? 0xFF6E6E6E : 0xFF7A5230;
            boolean dashed = wall.built() < 1000;
            double[][] sides = {{west, north, east, north}, {east, north, east, south}, {east, south, west, south}, {west, south, west, north}};
            for (double[] side : sides) {
                line(graphics, side[0], side[1], side[2], side[3], 3, 0xFF2B1D0E, dashed);
                line(graphics, side[0], side[1], side[2], side[3], 1, color, dashed);
            }
            for (int i = 0; i < wall.gateXs().length && i < wall.gateZs().length; i++) {
                int x = blockScreenX(wall.gateXs()[i] + 0.5);
                int y = blockScreenZ(wall.gateZs()[i] + 0.5);
                if (x - 2 < mapLeft() || x + 3 > mapRight() || y - 2 < mapTop() || y + 3 > mapBottom()) {
                    continue;
                }
                graphics.fill(x - 2, y - 2, x + 3, y + 3, 0xFF2B1D0E);
                graphics.fill(x - 1, y - 1, x + 2, y + 2, 0xFFF0C030);
            }
        }
    }

    /**
     * The level of a city as one to three gold marks in a column beside its sign.
     */
    private void drawLevel(GuiGraphics graphics, int x, int y, io.github.sekerator.kingdomworld.core.road.CityLevel level, Component hover) {
        int marks = level.ordinal() + 1;
        graphics.fill(x - 1, y - 1, x + 3, y + marks * 3, 0xFF2B1D0E);
        for (int i = 0; i < marks; i++) {
            graphics.fill(x, y + i * 3, x + 2, y + i * 3 + 2, 0xFFF0C030);
        }
        hoverText(x - 1, y - 1, 4, hover);
    }

    private void drawCities(GuiGraphics graphics) {
        String dimension = dimension();
        if (dimension == null) {
            return;
        }
        for (io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapCity city
                : io.github.sekerator.kingdomworld.client.map.ClientRoadCache.data().cities()) {
            if (!city.dimension().equals(dimension)) {
                continue;
            }
            Component level = io.github.sekerator.kingdomworld.common.road.RoadTexts.level(city.level());
            if (city.id() == 0) {
                // A capital: the crown stands for it already, the level goes beside the crown.
                int x = screenX(Math.floorDiv(city.x(), 16) + 0.5);
                int y = screenZ(Math.floorDiv(city.z(), 16) + 0.5);
                if (x - 6 < mapLeft() || x + 11 > mapRight() || y - 5 < mapTop() || y + 5 > mapBottom()) {
                    continue;
                }
                drawLevel(graphics, x + 8, y - 3, city.level(), Component.translatable("kingdomworld.screen.map.capital_level", city.name(), level));
                continue;
            }
            int x = blockScreenX(city.x() + 0.5);
            int y = blockScreenZ(city.z() + 0.5);
            if (x - 5 < mapLeft() || x + 10 > mapRight() || y - 5 < mapTop() || y + 5 > mapBottom()) {
                continue;
            }
            // A small house: dark outline, a wall in the color of the kingdom and a red roof.
            graphics.fill(x - 4, y - 4, x + 5, y + 4, 0xFF2B1D0E);
            graphics.fill(x - 3, y - 1, x + 4, y + 3, 0xFF000000 | city.color());
            graphics.fill(x - 2, y - 3, x + 3, y - 1, 0xFFB03020);
            Component hover = Component.translatable("kingdomworld.screen.map.city_level", city.name(), level);
            hoverText(x - 4, y - 4, 9, hover);
            drawLevel(graphics, x + 7, y - 3, city.level(), hover);
            if (cell >= 6) {
                int maxName = Math.min(MAX_CAPITAL_NAME, mapRight() - mapLeft() - 8);
                int nameWidth = font.width(fit(city.name(), maxName));
                int nameX = Mth.clamp(x - nameWidth / 2, mapLeft() + 3, mapRight() - 3 - nameWidth);
                int nameY = y + 6;
                if (nameY + 10 > mapBottom()) {
                    nameY = y - 15;
                }
                int[] box = {nameX - 2, nameY - 1, nameX + nameWidth + 2, nameY + 9};
                // A name that would cover another one is left out; the city shows its name on hover.
                if (labels.stream().noneMatch(other -> box[0] < other[2] && other[0] < box[2] && box[1] < other[3] && other[1] < box[3])) {
                    graphics.fill(box[0], box[1], box[2], box[3], 0xB0100C08);
                    name(graphics, city.name(), nameX, nameY, 0xFFF0E0C0, maxName);
                    labels.add(box);
                }
            }
        }
    }

    private void drawCamps(GuiGraphics graphics) {
        String dimension = dimension();
        if (dimension == null) {
            return;
        }
        for (io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapCamp camp
                : io.github.sekerator.kingdomworld.client.map.ClientRoadCache.data().camps()) {
            if (!camp.dimension().equals(dimension)) {
                continue;
            }
            int x = blockScreenX(camp.x() + 0.5);
            int y = blockScreenZ(camp.z() + 0.5);
            if (x - 4 < mapLeft() || x + 4 > mapRight() || y - 4 < mapTop() || y + 4 > mapBottom()) {
                continue;
            }
            // Crossed swords in red.
            for (int i = -3; i <= 3; i++) {
                graphics.fill(x + i, y + i, x + i + 1, y + i + 1, 0xFFB01010);
                graphics.fill(x + i, y - i, x + i + 1, y - i + 1, 0xFFB01010);
            }
            hoverText(x - 4, y - 4, 8, Component.translatable("kingdomworld.screen.map.camp", camp.size()));
        }
    }

    /**
     * How the player's kingdom stands with the others, beside their capitals: a bar in the colour of the relation under the
     * crown, and a sign of the bond left of it (crossed swords for war, a shield for an alliance, a seal for a pact, a ring for
     * vassalage, a white flag for a truce), with a blue frame while a kingdom is shielded.
     */
    private void drawDiplomacy(GuiGraphics graphics) {
        String dimension = dimension();
        if (dimension == null) {
            return;
        }
        drawWarSpots(graphics, dimension);
        for (io.github.sekerator.kingdomworld.common.network.data.DiplomacyViews.MapMark mark
                : io.github.sekerator.kingdomworld.client.network.ClientDiplomacyHandlers.marks().marks()) {
            if (!mark.dimension().equals(dimension)) {
                continue;
            }
            int x = screenX(Math.floorDiv(mark.x(), 16) + 0.5) - 13;
            int y = screenZ(Math.floorDiv(mark.z(), 16) + 0.5);
            if (x - 5 < mapLeft() || x + 18 > mapRight() || y - 5 < mapTop() || y + 8 > mapBottom()) {
                continue;
            }
            int relation = DiplomacyTab.relationColor(mark.relation());
            graphics.fill(x + 8, y + 5, x + 18, y + 7, 0xFF2B1D0E);
            graphics.fill(x + 9, y + 5, x + 9 + Math.max(1, (mark.relation() + 100) * 8 / 200), y + 6, relation);
            int color = DiplomacyTab.bondColor(mark.bond());
            switch (mark.bond()) {
                case WAR -> {
                    for (int i = -3; i <= 3; i++) {
                        graphics.fill(x + i, y + i, x + i + 1, y + i + 1, 0xFF2B1D0E);
                        graphics.fill(x + i, y - i, x + i + 1, y - i + 1, 0xFF2B1D0E);
                        graphics.fill(x + i + 1, y + i, x + i + 2, y + i + 1, color);
                        graphics.fill(x + i + 1, y - i, x + i + 2, y - i + 1, color);
                    }
                }
                case ALLIANCE, PACT -> {
                    graphics.fill(x - 3, y - 4, x + 4, y + 2, 0xFF2B1D0E);
                    graphics.fill(x - 2, y - 3, x + 3, y + 1, color);
                    graphics.fill(x - 1, y + 2, x + 2, y + 4, 0xFF2B1D0E);
                }
                case VASSAL -> {
                    graphics.renderOutline(x - 3, y - 3, 7, 7, 0xFF2B1D0E);
                    graphics.renderOutline(x - 2, y - 2, 5, 5, color);
                }
                case TRUCE -> {
                    graphics.fill(x - 3, y - 4, x - 2, y + 4, 0xFF6B4A2B);
                    graphics.fill(x - 2, y - 4, x + 3, y, color);
                }
                case PEACE -> {
                }
            }
            if (mark.shielded()) {
                graphics.renderOutline(x - 5, y - 5, 11, 11, 0xFF80C0FF);
            }
            Component bond = Component.translatable("kingdomworld.war.bond." + mark.bond().id());
            hoverText(x - 5, y - 5, 23, Component.translatable(mark.shielded() ? "kingdomworld.screen.map.diplomacy_shielded"
                    : "kingdomworld.screen.map.diplomacy", mark.name(), bond, mark.relation()));
        }
    }

    /**
     * The war in the world: chunks occupied by an enemy are hatched (red when the enemy holds ours, green when we hold
     * theirs), a besieged city wears a ring of flames with the share of its gate broken, a taken city a banner. Hovering
     * tells how far the hold has gone.
     */
    private void drawWarSpots(GuiGraphics graphics, String dimension) {
        for (io.github.sekerator.kingdomworld.common.network.data.DiplomacyViews.WarSpot spot
                : io.github.sekerator.kingdomworld.client.network.ClientDiplomacyHandlers.marks().spots()) {
            if (!spot.dimension().equals(dimension)) {
                continue;
            }
            int color = spot.ours() ? 0xFFE04040 : 0xFF40C060;
            if ("occupied".equals(spot.kind())) {
                int x0 = screenX(spot.chunkX());
                int x1 = screenX(spot.chunkX() + 1);
                int y0 = screenZ(spot.chunkZ());
                int y1 = screenZ(spot.chunkZ() + 1);
                if (x1 < mapLeft() || x0 > mapRight() || y1 < mapTop() || y0 > mapBottom()) {
                    continue;
                }
                for (int i = 0; i < Math.max(1, x1 - x0); i += 3) {
                    mapFill(graphics, x0 + i, y0, Math.min(x1, x0 + i + 1), y1, 0x88000000 | (color & 0xFFFFFF));
                }
                hoverText(x0, y0, Math.max(4, x1 - x0), spot.label());
                continue;
            }
            int x = screenX(spot.chunkX() + 0.5);
            int y = screenZ(spot.chunkZ() + 0.5) - 12;
            if (x - 6 < mapLeft() || x + 6 > mapRight() || y - 6 < mapTop() || y + 8 > mapBottom()) {
                continue;
            }
            if ("siege".equals(spot.kind())) {
                graphics.renderOutline(x - 6, y - 6, 13, 13, 0xFF2B1D0E);
                graphics.renderOutline(x - 5, y - 5, 11, 11, 0xFFFF8020);
                graphics.fill(x - 4, y + 4, x - 4 + Math.max(1, spot.percent() * 9 / 100), y + 5, 0xFFFF4020);
            } else {
                graphics.fill(x - 1, y - 6, x, y + 6, 0xFF6B4A2B);
                graphics.fill(x, y - 6, x + 6, y - 1, color);
            }
            hoverText(x - 6, y - 6, 13, spot.label());
        }
    }

    /**
     * Festivals and fairs of today: a flag over the capital for a festival, an awning over the city for a fair.
     */
    private void drawLifeEvents(GuiGraphics graphics) {
        String dimension = dimension();
        if (dimension == null) {
            return;
        }
        for (io.github.sekerator.kingdomworld.common.network.data.LifeViews.MapEvent event
                : io.github.sekerator.kingdomworld.client.network.ClientLifeHandlers.events()) {
            if (!event.dimension().equals(dimension)) {
                continue;
            }
            int x = blockScreenX(event.x() + 0.5) + (event.fair() ? 7 : -7);
            int y = blockScreenZ(event.z() + 0.5) - 9;
            if (x - 4 < mapLeft() || x + 4 > mapRight() || y - 4 < mapTop() || y + 4 > mapBottom()) {
                continue;
            }
            int color = 0xFF000000 | event.color();
            if (event.fair()) {
                // A striped awning on two posts.
                for (int i = -4; i <= 3; i++) {
                    graphics.fill(x + i, y - 3, x + i + 1, y, (i & 1) == 0 ? 0xFFE04040 : 0xFFF0F0F0);
                }
                graphics.fill(x - 4, y, x - 3, y + 4, 0xFF6B4A2B);
                graphics.fill(x + 3, y, x + 4, y + 4, 0xFF6B4A2B);
            } else {
                // A flag in the colour of the kingdom on a pole.
                graphics.fill(x - 3, y - 4, x - 2, y + 4, 0xFF6B4A2B);
                graphics.fill(x - 2, y - 4, x + 4, y, color);
                graphics.renderOutline(x - 2, y - 4, 6, 4, 0xFFF0D070);
            }
            hoverText(x - 4, y - 4, 9, Component.translatable(event.fair() ? "kingdomworld.screen.map.fair" : "kingdomworld.screen.map.festival"));
        }
    }

    private void drawPlan(GuiGraphics graphics) {
        io.github.sekerator.kingdomworld.common.network.data.RoadViews.PlanView view = plan;
        if (view != null && view.xs().length >= 2) {
            for (int i = 1; i < view.xs().length; i++) {
                line(graphics, view.xs()[i - 1] + 0.5, view.zs()[i - 1] + 0.5, view.xs()[i] + 0.5, view.zs()[i] + 0.5, 3, 0xFF2B1D0E, false);
                line(graphics, view.xs()[i - 1] + 0.5, view.zs()[i - 1] + 0.5, view.xs()[i] + 0.5, view.zs()[i] + 0.5, 1, 0xFFFF8C1A, false);
            }
        } else if (planFrom != null && planTo != null) {
            line(graphics, planFrom[0], planFrom[1], planTo[0], planTo[1], 1, 0xFFFF8C1A, true);
        }
        for (int[] point : new int[][]{planFrom, planTo}) {
            if (point == null) {
                continue;
            }
            int x = blockScreenX(point[0]);
            int y = blockScreenZ(point[1]);
            mapFill(graphics, x - 3, y - 3, x + 4, y + 4, 0xFF2B1D0E);
            mapFill(graphics, x - 2, y - 2, x + 3, y + 3, 0xFFFF8C1A);
        }
        if (view == null || view.planId() == 0) {
            return;
        }
        io.github.sekerator.kingdomworld.core.road.RoadClass roadClass = planClass;
        List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.translatable("kingdomworld.screen.map.road.way", view.from(), view.to()));
        lines.add(Component.translatable("kingdomworld.screen.map.road.length", io.github.sekerator.kingdomworld.common.road.RoadTexts.blocks(view.length()),
                view.bridges(), view.fills()));
        lines.add(Component.translatable("kingdomworld.screen.map.road.cost." + roadClass.id(), view.material(roadClass, 0),
                view.material(roadClass, 1), view.material(roadClass, 2)));
        int builders = Math.max(1, view.builders());
        long minutes = Math.max(1, Math.round(view.seconds(roadClass) / 60.0 / builders));
        lines.add(Component.translatable("kingdomworld.screen.map.road.time", io.github.sekerator.kingdomworld.common.road.RoadTexts.minutes(minutes),
                io.github.sekerator.kingdomworld.common.road.RoadTexts.builders(view.builders())));
        if (!view.crossed().isEmpty()) {
            lines.add(Component.translatable("kingdomworld.screen.map.road.crossed", String.join(", ", view.crossed())));
        }
        int maxWidth = mapRight() - mapLeft() - 12;
        int inner = 0;
        for (Component line : lines) {
            inner = Math.max(inner, font.width(line));
        }
        int cardWidth = Math.min(inner + 12, maxWidth);
        int x = mapLeft() + 6;
        int y = mapTop() + 6;
        int cardHeight = 6 + lines.size() * 11;
        graphics.fill(x, y, x + cardWidth, y + cardHeight, 0xF0201810);
        graphics.renderOutline(x, y, cardWidth, cardHeight, 0xFFFF8C1A);
        int lineY = y + 4;
        for (Component line : lines) {
            // Long names make long lines: shortened, with the whole line on hover.
            name(graphics, line.getString(), x + 6, lineY, 0xFFE8E8E8, cardWidth - 12);
            lineY += 11;
        }
    }

    private void drawCard(GuiGraphics graphics) {
        if (planning != null) {
            return;
        }
        KingdomMapInfo info = ClientTerritoryCache.kingdom(selected);
        if (info == null) {
            return;
        }
        List<Component> lines = new java.util.ArrayList<>();
        if (info.npc() != null) {
            lines.addAll(npcLines(info.npc()));
        } else {
            lines.add(Component.translatable("kingdomworld.screen.map.card.ruler", PlayerNames.display(fit(info.rulerName(), MAX_CARD_NAME))));
        }
        lines.addAll(List.of(
                Component.translatable("kingdomworld.screen.map.card.members", info.members()),
                Component.translatable("kingdomworld.screen.map.card.population", info.population()),
                Component.translatable("kingdomworld.screen.map.card.chunks", info.chunks())));
        String currency = Component.translatable("kingdomworld.screen.map.card.currency", currencyName(info)).getString();
        long rate = io.github.sekerator.kingdomworld.common.economy.CurrencyDirectory.get(info.id())
                .map(io.github.sekerator.kingdomworld.common.network.data.CurrencyInfo::rate).orElse(0L);
        Component rateLine = rate > 0 ? Component.translatable("kingdomworld.screen.map.card.rate",
                io.github.sekerator.kingdomworld.common.trade.TradeTexts.rate(rate)) : null;
        int maxWidth = mapRight() - mapLeft() - 12;
        String shownName = kingdomName(info.name(), info.npc());
        int inner = font.width(fit(shownName, Math.min(MAX_CARD_NAME, maxWidth - 12)));
        for (Component line : lines) {
            inner = Math.max(inner, Math.min(font.width(line), MAX_CARD_NAME + 60));
        }
        inner = Math.max(inner, Math.min(font.width(currency), MAX_CARD_NAME + 60));
        if (rateLine != null) {
            inner = Math.max(inner, font.width(rateLine));
        }
        int cardWidth = Math.min(inner + 12, maxWidth);
        int x = mapLeft() + 6;
        int y = mapTop() + 6;
        int cardHeight = 16 + (lines.size() + (rateLine != null ? 2 : 1)) * 11;
        graphics.fill(x, y, x + cardWidth, y + cardHeight, 0xF0201810);
        graphics.renderOutline(x, y, cardWidth, cardHeight, 0xFF000000 | info.color());
        name(graphics, shownName, x + 6, y + 5, 0xFF000000 | FoundKingdomScreen.readable(info.color()),
                Math.min(MAX_CARD_NAME, cardWidth - 12));
        int lineY = y + 17;
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            fittedLabel(graphics, line, x + 6, lineY, 0xFFE8E8E8, x + cardWidth - 6);
            if (info.npc() != null && i == 1) {
                hoverText(x + 6, lineY, Math.min(font.width(line), cardWidth - 12), npcHint());
            }
            lineY += 11;
        }
        // The name of a currency is chosen by a king and may be long: it is shortened like the names of kingdoms.
        name(graphics, currency, x + 6, lineY, 0xFFE8E8E8, cardWidth - 12);
        if (rateLine != null) {
            label(graphics, rateLine, x + 6, lineY + 11, 0xFFE8E8E8, x + cardWidth - 6);
        }
    }

    /**
     * The currency of a kingdom on the card, as the server last named it.
     */
    private static Component currencyName(KingdomMapInfo info) {
        return io.github.sekerator.kingdomworld.common.economy.CurrencyDirectory.get(info.id())
                .<Component>map(currency -> io.github.sekerator.kingdomworld.common.economy.MoneyTexts.currency(currency.name(), info.name()))
                .orElseGet(() -> io.github.sekerator.kingdomworld.common.economy.MoneyTexts.currency("", info.name()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
