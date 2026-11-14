package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.packet.StorehousePackets;
import io.github.sekerator.kingdomworld.core.citizen.Profession;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.StorehouseRules;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Whom a storehouse takes in from, opened from its window: every citizen, or the workers of the professions chosen (a storehouse of
 * wood for the lumberjacks, of food for the farmers and herders). Taking from it stays open to every worker; players, hoppers and
 * pipes put things in as before. The server checks the right and opens the window of the storehouse again.
 */
public class StorehouseAcceptsScreen extends KingdomScreen {
    private static final String KEY = "kingdomworld.storehouse.accepts.";
    private static final int MIN_PANEL_WIDTH = 220;
    private static final int ROW = 18;
    private static final int TOGGLE = 16;
    private static final int GAP = 4;

    private final BlockPos pos;
    private final Set<String> chosen;
    private final List<String> professions;
    private int panelWidth;
    private int panelHeight;
    private boolean sent;

    public StorehouseAcceptsScreen(BlockPos pos, List<String> accepts) {
        super(Component.translatable(KEY + "title"));
        this.pos = pos.immutable();
        this.chosen = new LinkedHashSet<>(accepts);
        this.professions = choices(accepts);
    }

    /**
     * The professions a storehouse may be given: those whose workers bring things to the storehouses (not the unemployed, the
     * soldiers, the adventurers or the carriers, who carry the goods of other work), with those it was given already.
     */
    public static List<String> choices(List<String> accepts) {
        List<String> result = new ArrayList<>();
        for (Profession profession : Professions.REGISTRY.all()) {
            String id = profession.id();
            if (!id.equals(Professions.UNEMPLOYED.id()) && !id.equals(Professions.ADVENTURER.id()) && !id.equals(Professions.CARRIER.id())
                    && !Professions.isMilitary(id)) {
                result.add(id);
            }
        }
        for (String id : accepts) {
            if (!result.contains(id)) {
                result.add(id);
            }
        }
        return result.subList(0, Math.min(result.size(), StorehouseRules.MAX_PROFESSIONS));
    }

    @Override
    public String checkName() {
        return "storehouse-accepts";
    }

    private Component toggleLabel(Component name, boolean on) {
        return on ? Component.literal("✔ ").withStyle(ChatFormatting.GREEN).append(name.copy().withStyle(ChatFormatting.WHITE))
                : Component.literal("   ").append(name);
    }

    private int cellWidth() {
        int widest = 0;
        for (String profession : professions) {
            widest = Math.max(widest, font.width(toggleLabel(CitizenTexts.profession(profession), true)));
        }
        return widest + 10;
    }

    private int columns(int inner) {
        return Math.max(1, Math.min(4, (inner + GAP) / (cellWidth() + GAP)));
    }

    private int left() {
        return (width - panelWidth) / 2;
    }

    private int top() {
        return (height - panelHeight) / 2;
    }

    @Override
    protected void init() {
        Component done = Component.translatable("gui.done");
        Component cancel = Component.translatable("gui.cancel");
        int buttons = buttonWidth(done, cancel);
        int allWidth = font.width(toggleLabel(Component.translatable(KEY + "everybody"), true)) + 10;
        int cell = cellWidth();
        int wanted = Math.max(Math.max(font.width(title), allWidth), Math.max(2 * buttons + PAD, 3 * cell + 2 * GAP));
        panelWidth = Math.min(Math.max(MIN_PANEL_WIDTH, wanted + 2 * PAD), width - 8);
        int inner = panelWidth - 2 * PAD;
        int columns = columns(inner);
        int rows = (professions.size() + columns - 1) / columns;
        panelHeight = 22 + ROW + 6 + rows * ROW + 8 + 20 + PAD;
        int left = left() + PAD;
        int top = top();
        int y = top + 22;
        addRenderableWidget(Button.builder(toggleLabel(Component.translatable(KEY + "everybody"), chosen.isEmpty()), button -> {
            chosen.clear();
            rebuildWidgets();
        }).bounds(left, y, inner, TOGGLE).build());
        y += ROW + 6;
        int cellWidth = (inner - (columns - 1) * GAP) / columns;
        for (int i = 0; i < professions.size(); i++) {
            String profession = professions.get(i);
            int x = left + (i % columns) * (cellWidth + GAP);
            int rowY = y + (i / columns) * ROW;
            addRenderableWidget(Button.builder(toggleLabel(CitizenTexts.profession(profession), chosen.contains(profession)), button -> {
                if (!chosen.remove(profession)) {
                    chosen.add(profession);
                }
                rebuildWidgets();
            }).bounds(x, rowY, cellWidth, TOGGLE).build());
        }
        int bottom = top + panelHeight - PAD - 20;
        addRenderableWidget(Button.builder(done, button -> send(true)).bounds(left, bottom, buttons, 20).build());
        addRenderableWidget(Button.builder(cancel, button -> send(false)).bounds(left() + panelWidth - PAD - buttons, bottom, buttons, 20).build());
    }

    /**
     * Sends the setting, or only asks for the window of the storehouse again.
     */
    private void send(boolean apply) {
        if (!sent) {
            sent = true;
            if (Minecraft.getInstance().getConnection() != null) {
                KingdomWorldNetwork.sendToServer(new StorehousePackets.Accepts(pos, apply, apply ? List.copyOf(chosen) : List.of()));
            }
        }
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public void onClose() {
        send(false);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = left();
        int top = top();
        int right = left + panelWidth;
        graphics.fill(left, top, right, top + panelHeight, 0xF0201810);
        graphics.renderOutline(left, top, panelWidth, panelHeight, 0xFFC8A040);
        centeredLabel(graphics, title, width / 2, top + 8, 0xFFE8C860, left + PAD, right - PAD);
        hoverText(left + PAD, top + 22 + 4, panelWidth - 2 * PAD, Component.translatable(KEY + "everybody_hint"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
