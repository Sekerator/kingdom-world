package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.law.LawTexts;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.LawViews;
import io.github.sekerator.kingdomworld.common.network.packet.LawPackets;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A window that asks the player to pay. At a guard: the crimes on record and the fine, «Give up and pay» (the fine is taken
 * from coins, then the account, the rest becomes a debt), or for a murderer the death the law asks. At the gate of a large
 * city: the entry fee, the pass for a game day in every city of the kingdom, and paying with coins or from the account.
 */
public class LawDialogScreen extends KingdomScreen implements ResultListener {
    private static final int MIN_PANEL_WIDTH = 260;
    private static final int MAX_PANEL_HEIGHT = 200;
    private static final int GAP = 4;
    private static final int STATUS_LINES = 2;
    private static final int CRIME_LINES = 4;
    private static final String KEY = "kingdomworld.screen.law_dialog.";

    private final LawViews.DialogView view;
    private Component status = Component.empty();
    private boolean statusIsError;

    private int panelWidth;
    private int panelHeight;
    private int cancelWidth;
    private int firstWidth;
    private int secondWidth;

    public LawDialogScreen(LawViews.DialogView view) {
        super(Component.translatable(KEY + view.dialog().name().toLowerCase(java.util.Locale.ROOT)));
        this.view = view;
    }

    @Override
    public String checkName() {
        return "law-" + view.dialog().name().toLowerCase(java.util.Locale.ROOT) + (view.execution() ? "-murder" : "");
    }

    private CurrencyUnits units() {
        return view.currency().units();
    }

    private Component firstLabel() {
        if (view.dialog() == LawViews.Dialog.SURRENDER) {
            return view.execution() ? Component.translatable(KEY + "accept_death")
                    : Component.translatable(KEY + "give_up", LawTexts.shortAmount(view.amount(), units()));
        }
        return Component.translatable(KEY + "pay_coins");
    }

    private List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        if (view.dialog() == LawViews.Dialog.SURRENDER) {
            for (int i = 0; i < Math.min(CRIME_LINES, view.crimes().size()); i++) {
                lines.add(Component.translatable("kingdomworld.screen.law_tab.record_line", LawTexts.crime(view.crimes().get(i).crime(), units())));
            }
            if (view.crimes().size() > CRIME_LINES) {
                lines.add(Component.translatable(KEY + "more", view.crimes().size() - CRIME_LINES));
            }
            lines.add(view.execution() ? Component.translatable(KEY + "death") : Component.translatable(KEY + "fine",
                    MoneyTexts.amount(view.amount(), units())));
        } else {
            lines.add(Component.translatable(KEY + "fee", MoneyTexts.amount(view.amount(), units())));
            lines.add(Component.translatable(KEY + "pass"));
        }
        lines.add(Component.translatable(KEY + "money", MoneyTexts.amount(view.coins(), units()), MoneyTexts.amount(view.account(), units())));
        return lines;
    }

    private void measure() {
        cancelWidth = buttonWidth(Component.translatable(KEY + "cancel"));
        firstWidth = buttonWidth(firstLabel());
        secondWidth = view.dialog() == LawViews.Dialog.ENTRY ? buttonWidth(Component.translatable(KEY + "pay_account")) : 0;
        int buttons = firstWidth + cancelWidth + GAP + (secondWidth > 0 ? secondWidth + GAP : 0);
        int inner = buttons;
        for (Component line : lines()) {
            inner = Math.max(inner, Math.min(360, font.width(line)));
        }
        panelWidth = Math.min(Math.max(MIN_PANEL_WIDTH, inner + 2 * PAD), width - 8);
        panelHeight = Math.min(MAX_PANEL_HEIGHT, height - 8);
        // The first button gives way so that the buttons never cover each other on a narrow screen.
        int room = panelWidth - 2 * PAD - cancelWidth - GAP - (secondWidth > 0 ? secondWidth + GAP : 0);
        firstWidth = Math.max(20, Math.min(firstWidth, room));
    }

    private int left() {
        return (width - panelWidth) / 2;
    }

    private int top() {
        return (height - panelHeight) / 2;
    }

    private int bottomArea() {
        return 26 + 4 + STATUS_LINES * LINE;
    }

    private void act(boolean account) {
        if (Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new LawPackets.DialogAction(view.dialog(), view.kingdomId(), account));
        }
        if (view.dialog() == LawViews.Dialog.SURRENDER) {
            onClose();
        }
    }

    @Override
    protected void init() {
        measure();
        int y = top() + panelHeight - 26;
        int x = left() + PAD;
        addRenderableWidget(Button.builder(firstLabel(), b -> act(false)).bounds(x, y, firstWidth, 20).build());
        x += firstWidth + GAP;
        if (secondWidth > 0) {
            Button account = addRenderableWidget(Button.builder(Component.translatable(KEY + "pay_account"), b -> act(true))
                    .bounds(x, y, secondWidth, 20).build());
            account.active = view.account() >= view.amount();
        }
        addRenderableWidget(Button.builder(Component.translatable(KEY + "cancel"), b -> onClose())
                .bounds(left() + panelWidth - PAD - cancelWidth, y, cancelWidth, 20).build());
    }

    @Override
    public void onResult(boolean success, Component message) {
        status = message;
        statusIsError = !success;
        if (success && view.dialog() == LawViews.Dialog.ENTRY) {
            onClose();
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = left();
        int top = top();
        int x = left + PAD;
        int right = left + panelWidth - PAD;
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF0201818);
        graphics.renderOutline(left, top, panelWidth, panelHeight, 0xFF000000 | FoundKingdomScreen.readable(view.currency().color()));
        centeredLabel(graphics, title, width / 2, top + 7, 0xFFFFFFFF, x, right);
        String place = view.dialog() == LawViews.Dialog.ENTRY && !view.city().isEmpty()
                ? Component.translatable(KEY + "city", view.city(), view.currency().kingdomName()).getString() : view.currency().kingdomName();
        centeredName(graphics, place, width / 2, top + 19, 0xFF000000 | FoundKingdomScreen.readable(view.currency().color()), panelWidth - 2 * PAD);
        int y = top + 34;
        int limit = top + panelHeight - bottomArea() - LINE;
        for (Component line : lines()) {
            if (y > limit) {
                break;
            }
            boolean warn = line.getString().equals(Component.translatable(KEY + "death").getString());
            name(graphics, line.getString(), x, y, warn ? 0xFFFF7070 : 0xFFE8E8E8, right - x);
            y += LINE + 2;
        }
        Component bottom = status;
        int color = statusIsError ? 0xFFFF8080 : 0xFFA0E0A0;
        if (bottom.getString().isEmpty()) {
            bottom = Component.translatable(KEY + "hint." + view.dialog().name().toLowerCase(java.util.Locale.ROOT));
            color = 0xFFB8B8B8;
        }
        paragraph(graphics, bottom, x, top + panelHeight - bottomArea() + 2, panelWidth - 2 * PAD, STATUS_LINES, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
