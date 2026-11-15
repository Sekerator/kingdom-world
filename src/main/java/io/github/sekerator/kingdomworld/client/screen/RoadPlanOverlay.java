package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.client.map.ClientRoadCache;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.common.network.data.RoadViews;
import io.github.sekerator.kingdomworld.common.network.packet.RoadPackets;
import io.github.sekerator.kingdomworld.core.Cycle;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadClass;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.road.RouteStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Planning a road on the atlas: the start, the end and points between set with the mouse, a point dragged, removed with a right
 * click, the changes undone; the plan asked of the server once the points settle and once a second at most, the last points
 * always sent; the way drawn with the kind of every block, a failed leg in red with a cross where it got stuck, legs being
 * planned dashed; the card, and the profile of the road in a strip. The atlas only hands the mouse, the keys and the drawing to it.
 * Client only.
 */
final class RoadPlanOverlay {
    /** Pixels round a point that pick it up. */
    static final int GRAB = 4;
    /** Changes of the points kept to undo. */
    static final int HISTORY = 32;
    /** Ticks the points rest before the plan is asked for. */
    static final int SETTLE = 10;
    /** Ticks between two requests at least. */
    static final int EVERY = 20;
    /** Height of the strip of the profile. */
    static final int PROFILE = 36;
    /** Width of a button shown as an icon. */
    static final int ICON = 20;

    private final KingdomMapScreen screen;
    private final KingdomView kingdom;
    private final List<int[]> points = new ArrayList<>();
    private final Deque<List<int[]>> history = new ArrayDeque<>();
    private RoadClass roadClass = RoadClass.GRAVEL;
    private RouteStyle route = RouteStyle.TERRAIN;
    private Road.Mode mode = Road.Mode.JOINT;
    private RoadViews.PlanView plan;
    private RoadViews.PlanView previous;
    /** The last plan found, kept while the next one is searched for. */
    private RoadViews.PlanView found;
    private boolean profile;
    private int seq;
    private int dragged = -1;
    private boolean moved;
    /** Ticks since the points last changed, -1 when nothing waits to be sent. */
    private int dirty = -1;
    private int sinceSent = EVERY;
    private int ticks;

    RoadPlanOverlay(KingdomMapScreen screen, KingdomView kingdom) {
        this.screen = screen;
        this.kingdom = kingdom;
        ClientRoadCache.Session kept = ClientRoadCache.session();
        if (kept != null && kept.kingdomId().equals(kingdom.id())) {
            // The plan of the atlas closed before comes back as it was; the server kept planning it.
            for (int[] point : kept.points()) {
                points.add(point.clone());
            }
            roadClass = kept.roadClass();
            route = kept.route();
            seq = kept.seq();
            plan = ClientRoadCache.plan();
        }
    }

    // ---------------------------------------------------------------- the state

    List<int[]> points() {
        return points;
    }

    RoadViews.PlanView plan() {
        return plan;
    }

    /**
     * Shows points and a plan without asking the server, for the self-check of the client.
     */
    void showForCheck(List<int[]> shown, RoadViews.PlanView view, boolean withProfile) {
        points.clear();
        for (int[] point : shown) {
            points.add(point.clone());
        }
        plan = view;
        profile = withProfile;
        dirty = -1;
    }

    /**
     * Sets points and the way and asks the server for their plan as clicks would, for the self-check of the client.
     */
    void planForCheck(List<int[]> shown, RouteStyle way, boolean withProfile) {
        keep();
        points.clear();
        for (int[] point : shown) {
            points.add(point.clone());
        }
        route = way;
        profile = withProfile;
        send();
        screen.rebuild();
    }

    /**
     * Whether the server has answered the last request with a plan found or failed.
     */
    boolean answered() {
        return plan != null && plan.seq() == seq && !plan.searching() && plan.stage() != RoadViews.PlanView.Stage.IDLE;
    }

    /**
     * The server answered a request: a plan, its progress, or why there is none.
     */
    void planUpdated(RoadViews.PlanView view) {
        if (!view.kingdomId().equals(kingdom.id()) || view.seq() != 0 && view.seq() < seq) {
            return;
        }
        if (view.planId() != 0) {
            if (found != null && found.planId() != view.planId()) {
                previous = found;
            }
            found = view;
        }
        plan = view;
        remember();
        screen.rebuild();
    }

    private void remember() {
        ClientRoadCache.setSession(new ClientRoadCache.Session(kingdom.id(), List.copyOf(points), roadClass, route, seq));
        ClientRoadCache.setPlan(plan);
    }

    private void changed() {
        dirty = 0;
        plan = plan == null ? null : RoadViews.PlanView.of(kingdom.id(), plan.seq(), "", RoadViews.PlanView.Stage.QUEUED, 0, 0, roadClass,
                route);
        remember();
    }

    private void keep() {
        List<int[]> copy = new ArrayList<>();
        for (int[] point : points) {
            copy.add(point.clone());
        }
        history.push(copy);
        while (history.size() > HISTORY) {
            history.removeLast();
        }
    }

    void undo() {
        if (history.isEmpty()) {
            return;
        }
        points.clear();
        points.addAll(history.pop());
        changed();
        screen.rebuild();
    }

    void reset() {
        if (!points.isEmpty()) {
            keep();
        }
        points.clear();
        plan = null;
        previous = null;
        found = null;
        dirty = -1;
        send();
        ClientRoadCache.setSession(null);
        ClientRoadCache.setPlan(null);
        screen.rebuild();
    }

    /**
     * Sends the points if they have rested long enough and the last request was a second ago or more.
     */
    void tick() {
        ticks++;
        sinceSent++;
        if (dirty >= 0) {
            dirty++;
            if (dirty >= SETTLE && sinceSent >= EVERY && dragged < 0) {
                send();
            }
        }
    }

    private void send() {
        if (screen.minecraftConnected()) {
            int[] xs = new int[points.size() < 2 ? 0 : points.size()];
            int[] zs = new int[xs.length];
            for (int i = 0; i < xs.length; i++) {
                xs[i] = points.get(i)[0];
                zs[i] = points.get(i)[1];
            }
            KingdomWorldNetwork.sendToServer(new RoadPackets.PlanRequest(kingdom.id(), ++seq, roadClass, route, xs, zs));
            sinceSent = 0;
        }
        dirty = -1;
        remember();
    }

    // ---------------------------------------------------------------- the mouse

    private int pointAt(double mouseX, double mouseY) {
        for (int i = points.size() - 1; i >= 0; i--) {
            int[] point = points.get(i);
            int x = screen.blockScreenX(point[0] + 0.5);
            int y = screen.blockScreenZ(point[1] + 0.5);
            if (Math.abs(x - mouseX) <= GRAB && Math.abs(y - mouseY) <= GRAB) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The mouse went down on the map: a point within reach is picked up, otherwise the map moves as before.
     *
     * @return whether the overlay took the mouse
     */
    boolean mousePressed(double mouseX, double mouseY, int button) {
        if (button != 0 || plan != null && plan.stage() == RoadViews.PlanView.Stage.SURVEYING) {
            return false;
        }
        int at = pointAt(mouseX, mouseY);
        if (at < 0) {
            return false;
        }
        keep();
        dragged = at;
        moved = false;
        return true;
    }

    boolean mouseDragged(double mouseX, double mouseY) {
        if (dragged < 0) {
            return false;
        }
        int x = screen.blockAtX(mouseX);
        int z = screen.blockAtZ(mouseY);
        int[] point = points.get(dragged);
        if (point[0] != x || point[1] != z) {
            point[0] = x;
            point[1] = z;
            moved = true;
            changed();
        }
        return true;
    }

    /**
     * The mouse went up: the end of a drag, or a click on the map that was not a drag of the map.
     *
     * @param click whether the map was clicked rather than dragged
     */
    boolean mouseReleased(double mouseX, double mouseY, int button, boolean click) {
        if (dragged >= 0) {
            dragged = -1;
            if (moved) {
                send();
            } else {
                history.pop();
            }
            return true;
        }
        if (!click) {
            return false;
        }
        if (button == 1) {
            int at = pointAt(mouseX, mouseY);
            if (at > 0 && at < points.size() - 1) {
                keep();
                points.remove(at);
                changed();
                send();
                screen.rebuild();
            }
            return true;
        }
        if (button != 0 || points.size() >= RoadViews.PlanView.MAX_POINTS) {
            return true;
        }
        int[] point = {screen.blockAtX(mouseX), screen.blockAtZ(mouseY)};
        keep();
        if (points.size() < 2) {
            points.add(point);
        } else {
            points.add(leg(point) + 1, point);
        }
        changed();
        if (points.size() >= 2) {
            send();
        }
        screen.rebuild();
        return true;
    }

    /**
     * The leg nearest a point: by the way found where there is one, otherwise by the straight line between the points.
     */
    int leg(int[] point) {
        int best = 0;
        double nearest = Double.POSITIVE_INFINITY;
        RoadViews.PlanView view = plan;
        if (view != null && view.xs().length >= 2 && view.snapped().length == points.size() * 2) {
            // Where along the way the point is, against where the points between lie on it.
            int at = 0;
            for (int i = 0; i < view.xs().length; i++) {
                double d = Math.hypot(view.xs()[i] - point[0], view.zs()[i] - point[1]);
                if (d < nearest) {
                    nearest = d;
                    at = i;
                }
            }
            int leg = 0;
            for (int p = 1; p < points.size() - 1; p++) {
                int index = nearestIndex(view, view.snapped()[p * 2], view.snapped()[p * 2 + 1]);
                if (at > index) {
                    leg = p;
                }
            }
            return leg;
        }
        for (int i = 0; i + 1 < points.size(); i++) {
            double d = toSegment(point, points.get(i), points.get(i + 1));
            if (d < nearest) {
                nearest = d;
                best = i;
            }
        }
        return best;
    }

    private static int nearestIndex(RoadViews.PlanView view, int x, int z) {
        int best = 0;
        double nearest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < view.xs().length; i++) {
            double d = Math.hypot(view.xs()[i] - x, view.zs()[i] - z);
            if (d < nearest) {
                nearest = d;
                best = i;
            }
        }
        return best;
    }

    static double toSegment(int[] p, int[] a, int[] b) {
        double dx = b[0] - a[0];
        double dz = b[1] - a[1];
        double length = dx * dx + dz * dz;
        double t = length <= 0 ? 0 : Math.max(0, Math.min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dz) / length));
        return Math.hypot(a[0] + t * dx - p[0], a[1] + t * dz - p[1]);
    }

    boolean keyPressed(int keyCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_Z && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            undo();
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- the buttons

    /**
     * The buttons of planning along the bottom of the map: class, way, payment, build, and undo, reset and profile as words, or as
     * icons with hints when the row does not fit.
     */
    void init() {
        int classWidth = 0;
        for (RoadClass value : RoadClass.values()) {
            classWidth = Math.max(classWidth, screen.buttonWidth(KingdomMapScreen.classLabel(value)));
        }
        int routeWidth = 0;
        for (RouteStyle value : RouteStyle.values()) {
            routeWidth = Math.max(routeWidth, screen.buttonWidth(routeLabel(value)));
        }
        int modeWidth = Math.max(screen.buttonWidth(KingdomMapScreen.modeLabel(Road.Mode.JOINT)),
                screen.buttonWidth(KingdomMapScreen.modeLabel(Road.Mode.PAYER)));
        Component build = Component.translatable("kingdomworld.screen.map.road.build");
        Component undo = Component.translatable("kingdomworld.screen.map.road.undo");
        Component reset = Component.translatable("kingdomworld.screen.map.road.reset");
        Component strip = Component.translatable("kingdomworld.screen.map.road.profile");
        int words = screen.buttonWidth(undo) + screen.buttonWidth(reset) + screen.buttonWidth(strip) + 6;
        int fixed = classWidth + routeWidth + modeWidth + screen.buttonWidth(build) + 8;
        int room = screen.mapRight() - screen.mapLeft() - 8;
        boolean icons = fixed + words > room;
        int over = fixed + (icons ? 3 * (ICON + 2) : words) - room;
        if (over > 0) {
            // Even with icons the row is too wide: the class, the way and the payment give up width, their labels scrolling.
            int shared = classWidth + routeWidth + modeWidth;
            classWidth -= (int) Math.ceil((double) over * classWidth / shared);
            routeWidth -= (int) Math.ceil((double) over * routeWidth / shared);
            modeWidth -= (int) Math.ceil((double) over * modeWidth / shared);
        }
        int y = screen.mapBottom() - 20;
        int x = screen.mapLeft() + 4;
        screen.widget(new CycleButton(x, y, classWidth, 16, KingdomMapScreen.classLabel(roadClass), direction -> {
            roadClass = Cycle.step(roadClass, direction);
            changed();
            send();
            screen.rebuild();
        }));
        x += classWidth + 2;
        screen.widget(new CycleButton(x, y, routeWidth, 16, routeLabel(route), direction -> {
            route = Cycle.step(route, direction);
            changed();
            send();
            screen.rebuild();
        }));
        x += routeWidth + 2;
        Button pay = screen.widget(Button.builder(KingdomMapScreen.modeLabel(mode), button -> {
            mode = mode == Road.Mode.JOINT ? Road.Mode.PAYER : Road.Mode.JOINT;
            screen.rebuild();
        }).bounds(x, y, modeWidth, 16).build());
        pay.active = plan != null && !plan.foreign().isEmpty();
        pay.setTooltip(Tooltip.create(Component.translatable("kingdomworld.screen.map.road.mode." + mode.id() + ".hint")));
        x += modeWidth + 2;
        Button go = screen.widget(Button.builder(build, button -> {
            if (plan != null && plan.planId() != 0 && screen.minecraftConnected()) {
                KingdomWorldNetwork.sendToServer(new RoadPackets.PlanConfirm(kingdom.id(), plan.planId(), roadClass, mode));
                if (!plan.guessed()) {
                    points.clear();
                    plan = null;
                    previous = null;
                    found = null;
                    ClientRoadCache.setSession(null);
                    ClientRoadCache.setPlan(null);
                    screen.rebuild();
                }
            }
        }).bounds(x, y, screen.buttonWidth(build), 16).build());
        go.active = plan != null && plan.planId() != 0 && plan.stage() == RoadViews.PlanView.Stage.DONE && kingdom.can(Permission.MANAGE_CONSTRUCTION);
        x += screen.buttonWidth(build) + 2;
        x = button(x, y, icons, undo, "<", b -> undo(), !history.isEmpty());
        x = button(x, y, icons, reset, "x", b -> reset(), !points.isEmpty());
        button(x, y, icons, strip, "~", b -> {
            profile = !profile;
            screen.rebuild();
        }, plan != null && plan.profile().length > 0);
    }

    private int button(int x, int y, boolean icon, Component text, String mark, Button.OnPress press, boolean active) {
        int width = icon ? ICON : screen.buttonWidth(text);
        Button button = screen.widget(Button.builder(icon ? Component.literal(mark) : text, press).bounds(x, y, width, 16).build());
        button.active = active;
        if (icon) {
            button.setTooltip(Tooltip.create(text));
        }
        return x + width + 2;
    }

    static Component routeLabel(RouteStyle value) {
        return Component.translatable("kingdomworld.screen.map.road.route", Component.translatable("kingdomworld.screen.map.road.route."
                + value.id()));
    }

    // ---------------------------------------------------------------- drawing

    void render(GuiGraphics graphics, int mouseX, int mouseY) {
        RoadViews.PlanView view = plan;
        boolean fresh = view != null && view.xs().length >= 2 && view.snapped().length == points.size() * 2 && dirty < 0
                && !view.searching();
        if (fresh) {
            for (int i = 1; i < view.xs().length; i++) {
                screen.line(graphics, view.xs()[i - 1] + 0.5, view.zs()[i - 1] + 0.5, view.xs()[i] + 0.5, view.zs()[i] + 0.5, 3, 0xFF2B1D0E, false);
            }
            for (int i = 1; i < view.xs().length; i++) {
                int kind = i < view.kinds().length ? view.kinds()[i] : 0;
                screen.line(graphics, view.xs()[i - 1] + 0.5, view.zs()[i - 1] + 0.5, view.xs()[i] + 0.5, view.zs()[i] + 0.5, 1,
                        RoadPlanCard.color(kind), kind == RouteCell.Kind.TUNNEL.ordinal());
            }
        } else {
            // Legs being planned: dashed straight lines between the points.
            for (int i = 0; i + 1 < points.size(); i++) {
                int[] a = points.get(i);
                int[] b = points.get(i + 1);
                screen.line(graphics, a[0] + 0.5, a[1] + 0.5, b[0] + 0.5, b[1] + 0.5, 1, 0xFFFF8C1A, true);
            }
        }
        if (view != null && view.stage() == RoadViews.PlanView.Stage.FAILED) {
            for (int i = 0; i < view.legs().size() && i + 1 < points.size(); i++) {
                RoadViews.PlanView.LegView leg = view.legs().get(i);
                if (leg.reason().isEmpty()) {
                    continue;
                }
                int[] a = points.get(i);
                int[] b = points.get(i + 1);
                screen.line(graphics, a[0] + 0.5, a[1] + 0.5, b[0] + 0.5, b[1] + 0.5, 1, 0xFFE03030, true);
                int x = screen.blockScreenX(leg.stuckX() + 0.5);
                int y = screen.blockScreenZ(leg.stuckZ() + 0.5);
                for (int d = -3; d <= 3; d++) {
                    screen.mapFill(graphics, x + d, y + d, x + d + 1, y + d + 1, 0xFFE03030);
                    screen.mapFill(graphics, x + d, y - d, x + d + 1, y - d + 1, 0xFFE03030);
                }
            }
        }
        for (int i = 0; i < points.size(); i++) {
            int[] point = points.get(i);
            int x = screen.blockScreenX(point[0] + 0.5);
            int y = screen.blockScreenZ(point[1] + 0.5);
            boolean end = i == 0 || i == points.size() - 1;
            int half = end ? 3 : 2;
            screen.mapFill(graphics, x - half - 1, y - half - 1, x + half + 2, y + half + 2, 0xFF2B1D0E);
            screen.mapFill(graphics, x - half, y - half, x + half + 1, y + half + 1, end ? 0xFFFF8C1A : 0xFFFFE0A0);
            if (!end) {
                graphics.drawString(screen.font(), String.valueOf(i), x + 4, y - 4, 0xFFFFE0A0, true);
            }
        }
        if (fresh) {
            hoverLeg(graphics, view, mouseX, mouseY);
        }
        if (view != null) {
            drawCard(graphics, view);
            if (profile && view.profile().length >= 6) {
                drawProfile(graphics, view, mouseX, mouseY);
            }
        }
    }

    /**
     * The length, works and work of the leg under the mouse.
     */
    private void hoverLeg(GuiGraphics graphics, RoadViews.PlanView view, int mouseX, int mouseY) {
        if (!screen.insideMap(mouseX, mouseY) || view.legs().isEmpty()) {
            return;
        }
        int[] at = {screen.blockAtX(mouseX), screen.blockAtZ(mouseY)};
        int index = nearestIndex(view, at[0], at[1]);
        double distance = Math.hypot(view.xs()[index] - at[0], view.zs()[index] - at[1]);
        if (distance * screen.cellPixels() / 16.0 > 4) {
            return;
        }
        int leg = Math.min(view.legs().size() - 1, leg(at));
        RoadViews.PlanView.LegView shown = view.legs().get(leg);
        int[] w = shown.works();
        Component text = Component.translatable("kingdomworld.screen.map.road.card.leg", leg + 1, shown.length(), w.length > 5 ? w[4] : 0,
                w.length > 5 ? w[5] : 0, w.length > 7 ? w[7] : 0, RoadPlanCard.days(shown.builderSeconds()), RoadPlanCard.days(shown.minerSeconds()));
        graphics.renderTooltip(screen.font(), text, mouseX, mouseY);
    }

    private void drawCard(GuiGraphics graphics, RoadViews.PlanView view) {
        List<Component> lines = RoadPlanCard.lines(view, previous);
        int maxWidth = screen.mapRight() - screen.mapLeft() - 12;
        int inner = 0;
        for (Component line : lines) {
            inner = Math.max(inner, screen.font().width(line));
        }
        int cardWidth = Math.min(inner + 12, maxWidth);
        int x = screen.mapLeft() + 6;
        int y = screen.mapTop() + 6;
        int cardHeight = 6 + lines.size() * 11;
        graphics.fill(x, y, x + cardWidth, y + cardHeight, 0xF0201810);
        graphics.renderOutline(x, y, cardWidth, cardHeight, 0xFFFF8C1A);
        int lineY = y + 4;
        for (Component line : lines) {
            fitted(graphics, line, x + 6, lineY, cardWidth - 12);
            lineY += 11;
        }
    }

    /**
     * A line cut to a width, shown whole on hover.
     */
    private void fitted(GuiGraphics graphics, Component line, int x, int y, int width) {
        if (screen.font().width(line) <= width) {
            graphics.drawString(screen.font(), line, x, y, 0xFFE8E8E8, false);
            return;
        }
        String cut = screen.font().plainSubstrByWidth(line.getString(), Math.max(0, width - screen.font().width("..."))) + "...";
        graphics.drawString(screen.font(), Component.literal(cut).withStyle(line.getStyle()), x, y, 0xFFE8E8E8, false);
        screen.hoverText(x, y - 1, width, line);
    }

    /**
     * The profile of the road in a strip above the buttons: the ground in grey, the deck in the colour of its kind.
     */
    private void drawProfile(GuiGraphics graphics, RoadViews.PlanView view, int mouseX, int mouseY) {
        int[] probes = view.profile();
        int count = probes.length / 3;
        int left = screen.mapLeft() + 4;
        int right = screen.mapRight() - 4;
        int bottom = screen.mapBottom() - 24;
        int top = bottom - PROFILE;
        graphics.fill(left, top, right, bottom, 0xE0101010);
        graphics.renderOutline(left, top, right - left, bottom - top, 0xFF806040);
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            low = Math.min(low, Math.min(probes[i * 3], probes[i * 3 + 1]));
            high = Math.max(high, Math.max(probes[i * 3], probes[i * 3 + 1]));
        }
        int span = Math.max(8, high - low);
        int width = right - left - 2;
        for (int i = 0; i < count; i++) {
            int x = left + 1 + (int) ((long) i * (width - 1) / Math.max(1, count - 1));
            int ground = bottom - 2 - (probes[i * 3] - low) * (PROFILE - 4) / span;
            int deck = bottom - 2 - (probes[i * 3 + 1] - low) * (PROFILE - 4) / span;
            graphics.fill(x, ground, x + 1, bottom - 1, 0xFF505050);
            graphics.fill(x, deck - 1, x + 1, deck + 1, RoadPlanCard.color(probes[i * 3 + 2]));
        }
        if (mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom) {
            int i = Math.max(0, Math.min(count - 1, (int) Math.round((mouseX - left - 1) * (count - 1) / (double) Math.max(1, width - 1))));
            graphics.renderTooltip(screen.font(), Component.translatable("kingdomworld.screen.map.road.probe", probes[i * 3], probes[i * 3 + 1],
                    RoadPlanCard.kind(probes[i * 3 + 2])), mouseX, mouseY);
        }
    }

    int ticks() {
        return ticks;
    }
}
