package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.magic.MagicRegistries;
import io.github.sekerator.kingdomworld.common.magic.MagicTexts;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.MagicViews;
import io.github.sekerator.kingdomworld.common.network.packet.MagicPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The screen of a gate: the other gates of its kingdom with their place and distance, and what a trip costs. A trip is paid
 * from the kingdom's energy; the server checks everything and moves the player.
 */
public class GateScreen extends KingdomScreen implements ResultListener {
    private static final int MIN_PANEL_WIDTH = 260;
    private static final int MAX_PANEL_HEIGHT = 220;
    private static final int ROW = 20;
    private static final int STATUS_LINES = 2;
    private static final String KEY = "kingdomworld.screen.gate.";

    private MagicViews.GateView view;
    private int scroll;
    private Component status = Component.empty();
    private boolean statusIsError;

    private int panelWidth;
    private int panelHeight;
    private int doneWidth;
    private int goWidth;

    public GateScreen(MagicViews.GateView view) {
        super(Component.translatable("block.kingdomworld.magic_gate"));
        this.view = view;
    }

    @Override
    public String checkName() {
        return "magic-gate";
    }

    public BlockPos pos() {
        return view.pos();
    }

    public void update(MagicViews.GateView newView) {
        view = newView;
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    private void measure() {
        doneWidth = Math.max(50, buttonWidth(Component.translatable("gui.done")));
        goWidth = buttonWidth(Component.translatable(KEY + "go"));
        int inner = 16 + 4 + 230 + goWidth;
        for (String key : List.of("no_gates", "off")) {
            inner = Math.max(inner, Math.min(300, font.width(Component.translatable(KEY + key))));
        }
        panelWidth = Math.min(Math.max(MIN_PANEL_WIDTH, inner + 2 * PAD), width - 8);
        panelHeight = Math.min(MAX_PANEL_HEIGHT, height - 8);
    }

    private int left() {
        return (width - panelWidth) / 2;
    }

    private int top() {
        return (height - panelHeight) / 2;
    }

    private int listTop() {
        return top() + 20 + 2 * LINE + 4;
    }

    private int bottomArea() {
        return 26 + 4 + STATUS_LINES * LINE;
    }

    private int visibleRows() {
        return Math.max(1, (top() + panelHeight - bottomArea() - listTop() - 11) / ROW);
    }

    @Override
    protected void init() {
        measure();
        int right = left() + panelWidth - PAD;
        int rows = visibleRows();
        scroll = Math.max(0, Math.min(scroll, view.gates().size() - rows));
        for (int i = 0; i < rows && scroll + i < view.gates().size(); i++) {
            MagicViews.GateRow row = view.gates().get(scroll + i);
            Button go = addRenderableWidget(Button.builder(Component.translatable(KEY + "go"), b -> {
                if (Minecraft.getInstance().getConnection() != null) {
                    KingdomWorldNetwork.sendToServer(new MagicPackets.GateTravel(view.pos(), row.id()));
                }
                onClose();
            }).bounds(right - goWidth, listTop() + i * ROW, goWidth, ROW - 2).build());
            go.active = view.on() && view.energy() >= view.price();
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left() + panelWidth - PAD - doneWidth, top() + panelHeight - 26, doneWidth, 20).build());
    }

    @Override
    public void onResult(boolean success, Component message) {
        status = message;
        statusIsError = !success;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, view.gates().size() - visibleRows());
        int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        if (next != scroll) {
            scroll = next;
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = left();
        int top = top();
        int x = left + PAD;
        int right = left + panelWidth - PAD;
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF0201810);
        graphics.renderOutline(left, top, panelWidth, panelHeight, 0xFF000000 | view.kingdomColor());
        centeredName(graphics, Component.translatable(KEY + "title", view.gateId(), view.kingdomName()).getString(), width / 2, top + 7,
                0xFF000000 | FoundKingdomScreen.readable(view.kingdomColor()), panelWidth - 2 * PAD);
        Component price = view.on() ? Component.translatable(KEY + "price", MagicTexts.energy(view.price()), MagicTexts.energy(view.energy()))
                : Component.translatable(KEY + "off");
        name(graphics, price.getString(), x, top + 20, view.on() ? 0xFFF0D070 : 0xFFFF9070, right - x);
        int rows = visibleRows();
        if (view.gates().isEmpty()) {
            name(graphics, Component.translatable(KEY + "no_gates").getString(), x, listTop() + 5, 0xFFB8B8B8, right - x);
        }
        for (int i = 0; i < rows && scroll + i < view.gates().size(); i++) {
            MagicViews.GateRow row = view.gates().get(scroll + i);
            int y = listTop() + i * ROW;
            graphics.renderItem(new ItemStack(MagicRegistries.MAGIC_GATE_ITEM.get()), x, y);
            Component where = row.distance() >= 0 ? Component.translatable(KEY + "where", row.pos().getX(), row.pos().getY(), row.pos().getZ(),
                    row.distance()) : Component.translatable(KEY + "where_far", row.pos().getX(), row.pos().getY(), row.pos().getZ(), row.dimension());
            int textRight = right - goWidth - 4;
            Component name = Component.translatable(KEY + "gate", row.id());
            int nameWidth = Math.min(font.width(name), (textRight - x - 20) / 3);
            name(graphics, name.getString(), x + 20, y + 5, 0xFFFFFFFF, nameWidth);
            name(graphics, where.getString(), x + 20 + nameWidth + 6, y + 5, 0xFFB8B8B8, textRight - x - 26 - nameWidth);
        }
        int count = view.gates().size();
        if (count > rows) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(count, scroll + rows), count);
            label(graphics, page, right - font.width(page), listTop() + rows * ROW + 1, 0xFF9A9A9A, right);
        }
        Component bottom = status;
        int color = statusIsError ? 0xFFFF8080 : 0xFFA0E0A0;
        if (bottom.getString().isEmpty()) {
            bottom = Component.translatable(KEY + "hint");
            color = 0xFFB8B8B8;
        }
        paragraph(graphics, bottom, x, top + panelHeight - bottomArea() + 2, panelWidth - 2 * PAD, STATUS_LINES, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
