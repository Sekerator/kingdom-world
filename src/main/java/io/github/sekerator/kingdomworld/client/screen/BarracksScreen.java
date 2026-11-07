package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.army.ArmyTexts;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.ArmyViews;
import io.github.sekerator.kingdomworld.common.network.packet.ArmyPackets;
import io.github.sekerator.kingdomworld.core.army.SoldierRank;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The barracks: how many soldiers the kingdom has and how many its barracks train, how far training takes them, the
 * soldiers who train now, and recruiting the nearest unemployed citizen as a warrior or an archer (with the right to run the
 * army). The server checks everything.
 */
public class BarracksScreen extends KingdomScreen implements ResultListener {
    private static final String KEY = "kingdomworld.screen.barracks.";
    private static final int MIN_WIDTH = 260;
    private static final int MAX_HEIGHT = 240;
    private static final int ROW = 12;
    private static final int TOP = 22;
    private static final int STATUS_LINES = 2;
    private static final int BOTTOM_AREA = 26 + 4 + STATUS_LINES * LINE;

    private ArmyViews.BarracksView view;
    private Component status = Component.empty();
    private boolean statusIsError;
    private int scroll;
    private int panelWidth;
    private int panelHeight;
    private int warriorWidth;
    private int archerWidth;
    private int doneWidth;

    public BarracksScreen(ArmyViews.BarracksView view) {
        super(Component.literal(view.kingdomName()));
        this.view = view;
    }

    @Override
    public String checkName() {
        return "barracks" + (view.canManage() ? "" : "-member") + (view.training().isEmpty() ? "-empty" : "");
    }

    public BlockPos pos() {
        return view.pos();
    }

    public void update(ArmyViews.BarracksView value) {
        view = value;
        rebuildWidgets();
    }

    private List<Component> lines() {
        SoldierRank cap = SoldierRank.of(view.trainingCap());
        return List.of(
                Component.translatable(KEY + "soldiers", ArmyTexts.soldiers(view.soldiers()), view.maxSoldiers()),
                Component.translatable(KEY + "room", view.barracks(), (long) view.barracks() * view.capacity()),
                Component.translatable(KEY + "training", ArmyTexts.rank(cap), view.trainingPerMinute()),
                view.nearest().getString().isEmpty() ? Component.translatable(KEY + "no_unemployed")
                        : Component.translatable(KEY + "nearest", view.nearest()));
    }

    private int left() {
        return (width - panelWidth) / 2;
    }

    private int top() {
        return (height - panelHeight) / 2;
    }

    private int listTop() {
        return top() + TOP + lines().size() * ROW + 16;
    }

    /**
     * Rows that fit the list; one less when a page counter is needed below them.
     */
    private int visibleRows() {
        int fit = Math.max(1, (top() + panelHeight - BOTTOM_AREA - 2 - listTop()) / ROW);
        return view.training().size() > fit ? Math.max(1, fit - 1) : fit;
    }

    @Override
    protected void init() {
        warriorWidth = buttonWidth(Component.translatable(KEY + "recruit_warrior"));
        archerWidth = buttonWidth(Component.translatable(KEY + "recruit_archer"));
        doneWidth = Math.max(50, buttonWidth(Component.translatable("gui.done")));
        int inner = warriorWidth + archerWidth + doneWidth + 8;
        for (Component line : lines()) {
            inner = Math.max(inner, Math.min(360, font.width(line)));
        }
        panelWidth = Math.min(Math.max(MIN_WIDTH, inner + 2 * PAD), width - 8);
        panelHeight = Math.min(MAX_HEIGHT, height - 8);
        scroll = Math.max(0, Math.min(scroll, view.training().size() - visibleRows()));
        int y = top() + panelHeight - 26;
        int x = left() + PAD;
        Button warrior = addRenderableWidget(Button.builder(Component.translatable(KEY + "recruit_warrior"),
                b -> send(ArmyPackets.BarracksAction.Action.RECRUIT_WARRIOR)).bounds(x, y, warriorWidth, 20).build());
        Button archer = addRenderableWidget(Button.builder(Component.translatable(KEY + "recruit_archer"),
                b -> send(ArmyPackets.BarracksAction.Action.RECRUIT_ARCHER)).bounds(x + warriorWidth + 4, y, archerWidth, 20).build());
        boolean can = view.canManage() && !view.nearest().getString().isEmpty() && view.soldiers() < view.maxSoldiers();
        warrior.active = can;
        archer.active = can;
        if (!view.canManage()) {
            warrior.setTooltip(Tooltip.create(Component.translatable(KEY + "no_right")));
            archer.setTooltip(Tooltip.create(Component.translatable(KEY + "no_right")));
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left() + panelWidth - PAD - doneWidth, y, doneWidth, 20).build());
    }

    private void send(ArmyPackets.BarracksAction.Action action) {
        if (minecraft != null && minecraft.getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new ArmyPackets.BarracksAction(view.pos(), action));
        }
    }

    @Override
    public void onResult(boolean success, Component message) {
        status = message;
        statusIsError = !success;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, view.training().size() - visibleRows());
        int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        if (next != scroll) {
            scroll = next;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = left();
        int top = top();
        int right = left + panelWidth - PAD;
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF0201810);
        graphics.renderOutline(left, top, panelWidth, panelHeight, 0xFF000000 | view.color());
        centeredName(graphics, Component.translatable(KEY + "title", view.kingdomName()).getString(), width / 2, top + 7,
                0xFF000000 | FoundKingdomScreen.readable(view.color()), panelWidth - 2 * PAD);
        int y = top + TOP;
        for (Component line : lines()) {
            name(graphics, line.getString(), left + PAD, y, 0xFFE8E8E8, right - left - PAD);
            y += ROW;
        }
        label(graphics, Component.translatable(KEY + "list", view.training().size()), left + PAD, listTop() - 12, 0xFFF0D070, right);
        if (view.training().isEmpty()) {
            name(graphics, Component.translatable(KEY + "nobody").getString(), left + PAD, listTop(), 0xFFB8B8B8, right - left - PAD);
        }
        for (int i = 0; i < visibleRows() && scroll + i < view.training().size(); i++) {
            ArmyViews.SoldierRow row = view.training().get(scroll + i);
            int rowY = listTop() + i * ROW;
            Component rank = ArmyTexts.rank(row.rank());
            int rankWidth = Math.min(font.width(rank), 70);
            name(graphics, rank.getString(), left + PAD, rowY, 0xFFF0D070, rankWidth);
            Component experience = Component.translatable(KEY + "experience", row.experience());
            int experienceWidth = Math.min(font.width(experience), 120);
            int nameX = left + PAD + rankWidth + 6;
            name(graphics, row.name().getString(), nameX, rowY, 0xFFFFFFFF, right - experienceWidth - 6 - nameX);
            name(graphics, experience.getString(), right - experienceWidth, rowY, 0xFFB8B8B8, experienceWidth);
        }
        if (view.training().size() > visibleRows()) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1,
                    Math.min(view.training().size(), scroll + visibleRows()), view.training().size());
            label(graphics, page, right - font.width(page), listTop() + visibleRows() * ROW, 0xFF9A9A9A, right);
        }
        Component bottom = status;
        int color = statusIsError ? 0xFFFF8080 : 0xFFA0E0A0;
        if (bottom.getString().isEmpty()) {
            bottom = Component.translatable(view.canManage() ? KEY + "hint" : KEY + "hint_member");
            color = 0xFFB8B8B8;
        }
        paragraph(graphics, bottom, left + PAD, top + panelHeight - BOTTOM_AREA + 2, panelWidth - 2 * PAD, STATUS_LINES, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
