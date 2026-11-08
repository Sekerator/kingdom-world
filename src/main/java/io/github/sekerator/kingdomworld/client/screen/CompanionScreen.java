package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.companion.CompanionMenu;
import io.github.sekerator.kingdomworld.common.companion.CompanionTexts;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.CompanionViews;
import io.github.sekerator.kingdomworld.common.network.packet.CompanionPackets;
import io.github.sekerator.kingdomworld.core.companion.CompanionOrder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The window of a companion: on the right its pack next to the owner's inventory, on the left who it is and how it fares
 * (level, strength, health, food, loyalty with the reasons it changed and a warning when it is about to leave), the gear it
 * took from the pack, the orders and, for a slave, freedom and sale. Sizes come from the translated texts.
 */
public class CompanionScreen extends AbstractContainerScreen<CompanionMenu> implements ResultListener {
    private static final String KEY = "kingdomworld.screen.companion.";
    private static final int PAD = 6;
    private static final int LINE = 10;
    private static final int ROW = 20;
    private static final int GAP = 2;
    private static final int MIN_SIDE = 150;
    private static final int MAX_SIDE = 220;
    private static final String ELLIPSIS = "…";

    private record Hover(int x, int y, int width, Component text) {
    }

    private final List<Hover> hovers = new ArrayList<>();
    private int side;
    private int sideX;
    private int sideHeight;
    private int orderWidth;
    private int actionRows;
    /** The answer to the last action, under the pack; empty until there is one. */
    private Component status = Component.empty();
    private boolean statusError;

    public CompanionScreen(CompanionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = CompanionMenu.inventoryTop(menu.rows()) + 82;
        this.inventoryLabelY = CompanionMenu.inventoryTop(menu.rows()) - 11;
    }

    public String checkName() {
        return "companion-" + (menu.view().slave() ? "slave" : "free");
    }

    @Override
    public void onResult(boolean success, Component message) {
        if (statusLines() < 1) {
            // No room under the pack on this screen: the answer goes to the chat, as before.
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(message.copy().withStyle(success ? net.minecraft.ChatFormatting.WHITE
                        : net.minecraft.ChatFormatting.RED), false);
            }
            return;
        }
        status = message;
        statusError = !success;
    }

    private int statusY() {
        return topPos + imageHeight + 4;
    }

    private int statusLines() {
        return Math.min(3, (height - statusY()) / LINE);
    }

    public void update(CompanionViews.CompanionView view) {
        menu.setView(view);
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    private CompanionViews.CompanionView view() {
        return menu.view();
    }

    private int buttonWidth(Component... texts) {
        int width = 0;
        for (Component text : texts) {
            width = Math.max(width, font.width(text));
        }
        return width + LayoutCheck.BUTTON_TEXT_MARGIN + 8;
    }

    private static Component orderLabel(CompanionOrder order) {
        return Component.translatable(KEY + "order." + order.id());
    }

    private List<CompanionPackets.CompanionAction.Action> actions() {
        List<CompanionPackets.CompanionAction.Action> actions = new ArrayList<>();
        if (view().slave()) {
            actions.add(CompanionPackets.CompanionAction.Action.FREE);
            actions.add(CompanionPackets.CompanionAction.Action.SELL);
            if (view().subject()) {
                actions.add(CompanionPackets.CompanionAction.Action.FREE_TO_KINGDOM);
            }
        } else {
            actions.add(CompanionPackets.CompanionAction.Action.DISMISS);
        }
        return actions;
    }

    private static Component actionLabel(CompanionPackets.CompanionAction.Action action) {
        return Component.translatable(KEY + "action." + action.name().toLowerCase(java.util.Locale.ROOT));
    }

    // ---------------------------------------------------------------- layout

    /** Height of the lines above the gear: the name, eight lines and the notes of loyalty in two. */
    private static final int TEXT_HEIGHT = LINE + 2 + 8 * LINE + 2 * LINE;

    private int linesHeight() {
        return TEXT_HEIGHT + 2 + 18 + 4;
    }

    private int gearY() {
        return topPos + PAD + TEXT_HEIGHT + 2;
    }

    @Override
    protected void init() {
        orderWidth = 0;
        for (CompanionOrder order : CompanionOrder.values()) {
            orderWidth = Math.max(orderWidth, buttonWidth(orderLabel(order)));
        }
        int actionsWidth = 0;
        for (CompanionPackets.CompanionAction.Action action : actions()) {
            actionsWidth = Math.max(actionsWidth, buttonWidth(actionLabel(action)));
        }
        side = Math.max(MIN_SIDE, Math.min(MAX_SIDE, Math.max(2 * orderWidth + GAP, 2 * actionsWidth + GAP) + 2 * PAD));
        side = Math.min(side, Math.max(MIN_SIDE, width - imageWidth - 12));
        int columns = Math.max(1, (side - 2 * PAD + GAP) / (actionsWidth + GAP));
        actionRows = (actions().size() + columns - 1) / columns;
        sideHeight = PAD + linesHeight() + 2 * ROW + 4 + actionRows * ROW + PAD;
        int total = side + 4 + imageWidth;
        int height = Math.max(imageHeight, sideHeight);
        super.init();
        leftPos = Math.max(side + 4, (this.width - total) / 2 + side + 4);
        topPos = Math.max(0, (this.height - height) / 2);
        sideX = leftPos - side - 4;
        int x = sideX + PAD;
        int y = topPos + PAD + linesHeight();
        CompanionOrder[] orders = CompanionOrder.values();
        for (int i = 0; i < orders.length; i++) {
            CompanionOrder order = orders[i];
            Button button = addRenderableWidget(Button.builder(orderLabel(order), b -> send(action(order)))
                    .bounds(x + (i % 2) * (orderWidth + GAP), y + (i / 2) * ROW, orderWidth, ROW - 2)
                    .tooltip(Tooltip.create(Component.translatable(KEY + "order." + order.id() + ".hint"))).build());
            button.active = view().order() != order;
        }
        y += 2 * ROW + 4;
        List<CompanionPackets.CompanionAction.Action> actions = actions();
        int per = Math.max(1, (side - 2 * PAD + GAP) / (actionsWidth + GAP));
        for (int i = 0; i < actions.size(); i++) {
            CompanionPackets.CompanionAction.Action action = actions.get(i);
            Button button = addRenderableWidget(Button.builder(actionLabel(action), b -> send(action))
                    .bounds(x + (i % per) * (actionsWidth + GAP), y + (i / per) * ROW, actionsWidth, ROW - 2)
                    .tooltip(Tooltip.create(actionHint(action))).build());
            if (action == CompanionPackets.CompanionAction.Action.SELL) {
                button.active = view().canSell();
            }
        }
    }

    private static CompanionPackets.CompanionAction.Action action(CompanionOrder order) {
        return CompanionPackets.CompanionAction.Action.valueOf(order.name());
    }

    private Component actionHint(CompanionPackets.CompanionAction.Action action) {
        return switch (action) {
            case FREE -> Component.translatable(KEY + "action.free.hint", money(view().wage()));
            case SELL -> Component.translatable(view().canSell() ? KEY + "action.sell.hint" : KEY + "action.sell.no_money", money(view().sellValue()));
            default -> Component.translatable(KEY + "action." + action.name().toLowerCase(java.util.Locale.ROOT) + ".hint");
        };
    }

    private void send(CompanionPackets.CompanionAction.Action action) {
        if (Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new CompanionPackets.CompanionAction(view().id(), action));
        }
    }

    private Component money(long amount) {
        return MoneyTexts.amount(amount, view().currency().units());
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hovers.clear();
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderSide(graphics);
        if (!status.getString().isEmpty()) {
            paragraph(graphics, status, leftPos, statusY(), imageWidth, statusLines(), statusError ? 0xFFFF8080 : 0xFFB0E0B0);
        }
        LayoutCheck.widgets(this, font);
        renderTooltip(graphics, mouseX, mouseY);
        for (Hover hover : hovers) {
            if (mouseX >= hover.x() && mouseX < hover.x() + hover.width() && mouseY >= hover.y() - 1 && mouseY < hover.y() + LINE) {
                graphics.renderTooltip(font, hover.text(), mouseX, mouseY);
                break;
            }
        }
        renderGearTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(sideX, topPos, sideX + side, topPos + sideHeight, 0xF0201810);
        graphics.renderOutline(sideX, topPos, side, sideHeight, view().slave() ? 0xFF8A6A4A : 0xFF2E8B57);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFC6C6C6);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF000000);
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
            graphics.fill(x, y, x + 17, y + 17, 0xFFFFFFFF);
            graphics.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Component pack = Component.translatable(KEY + "pack", menu.usable());
        graphics.drawString(font, pack, 8, 6, 0xFF404040, false);
        LayoutCheck.text(this, pack, leftPos + 8, topPos + 6, font.width(pack), leftPos + 8, leftPos + imageWidth - 8);
        LayoutCheck.drawn(this, pack, leftPos + 8, topPos + 6, font.width(pack));
        graphics.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0xFF404040, false);
        LayoutCheck.text(this, playerInventoryTitle, leftPos + 8, topPos + inventoryLabelY, font.width(playerInventoryTitle), leftPos + 8,
                leftPos + imageWidth - 8);
        LayoutCheck.drawn(this, playerInventoryTitle, leftPos + 8, topPos + inventoryLabelY, font.width(playerInventoryTitle));
    }

    private String fit(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ELLIPSIS))) + ELLIPSIS;
    }

    /**
     * A line shortened with an ellipsis; hovering shows it in full.
     */
    private void line(GuiGraphics graphics, Component text, int x, int y, int color, int maxWidth) {
        String full = text.getString();
        String shown = fit(full, maxWidth);
        graphics.drawString(font, shown, x, y, color);
        LayoutCheck.drawn(this, Component.literal(shown), x, y, font.width(shown));
        if (!shown.equals(full)) {
            hovers.add(new Hover(x, y, font.width(shown), text));
        }
    }

    private void paragraph(GuiGraphics graphics, Component text, int x, int y, int width, int maxLines, int color) {
        List<FormattedCharSequence> lines = font.split(text, width);
        int drawn = 0;
        for (FormattedCharSequence line : lines) {
            if (drawn == maxLines) {
                break;
            }
            graphics.drawString(font, line, x, y + drawn * LINE, color);
            LayoutCheck.drawn(this, text, x, y + drawn * LINE, font.width(line));
            drawn++;
        }
        if (lines.size() > maxLines) {
            hovers.add(new Hover(x, y, width, text));
        }
    }

    private void renderSide(GuiGraphics graphics) {
        CompanionViews.CompanionView view = view();
        int x = sideX + PAD;
        int width = side - 2 * PAD;
        int y = topPos + PAD;
        line(graphics, CitizenTexts.name(view.seed(), view.gender()), x, y, 0xFFFFFFFF, width - (view.warned() ? 10 : 0));
        if (view.warned()) {
            Component mark = Component.literal("!");
            graphics.drawString(font, mark, sideX + side - PAD - font.width(mark), y, 0xFFFF5050);
            LayoutCheck.drawn(this, mark, sideX + side - PAD - font.width(mark), y, font.width(mark));
            hovers.add(new Hover(sideX + side - PAD - 6, y, 8, Component.translatable(view.slave() ? KEY + "warned_slave" : KEY + "warned_free")));
        }
        y += LINE + 2;
        line(graphics, Component.translatable(KEY + "who", CompanionTexts.status(view.slave(), view.gender()),
                CompanionTexts.origin(view.origin(), view.gender())), x, y, 0xFFD0D0D0, width);
        y += LINE;
        MutableComponent level = view.nextLevel() > 0 ? Component.translatable(KEY + "level", view.level(), view.experience(), view.nextLevel())
                : Component.translatable(KEY + "level_top", view.level());
        line(graphics, level, x, y, 0xFFF0D070, width);
        y += LINE;
        line(graphics, Component.translatable(KEY + "power", view.power(), view.kills()), x, y, 0xFFB0E0B0, width);
        y += LINE;
        line(graphics, Component.translatable(KEY + "health", (int) Math.ceil(view.health()), (int) Math.ceil(view.maxHealth())), x, y,
                view.health() < view.maxHealth() * 0.4f ? 0xFFFF8080 : 0xFFE8E8E8, width);
        y += LINE;
        line(graphics, view.starving() ? Component.translatable(KEY + "starving", view.food())
                : Component.translatable(KEY + "food", view.food()), x, y, view.starving() ? 0xFFFF8080 : 0xFFE8E8E8, width);
        y += LINE;
        line(graphics, Component.translatable(KEY + "loyalty", view.loyalty()), x, y, view.warned() ? 0xFFFF8080 : 0xFFE8E8E8, width);
        y += LINE;
        paragraph(graphics, notes(view), x, y, width, 2, 0xFFB8B8B8);
        y += 2 * LINE;
        line(graphics, Component.translatable(KEY + "skills", view.combat(), view.carrying(), view.stamina()), x, y, 0xFFD0D0D0, width);
        y += LINE;
        line(graphics, view.slave() ? Component.translatable(KEY + "worth", money(view.sellValue()))
                : Component.translatable(KEY + "wage", money(view.wage())), x, y, 0xFFF0D070, width);
        y = gearY();
        for (int i = 0; i < view.gear().size(); i++) {
            ItemStack stack = view.gear().get(i);
            int gx = x + i * 18;
            graphics.fill(gx - 1, y - 1, gx + 17, y + 17, 0xFF505050);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, gx, y);
            }
        }
    }

    private void renderGearTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = sideX + PAD;
        int y = gearY();
        if (mouseY < y || mouseY >= y + 16) {
            return;
        }
        for (int i = 0; i < view().gear().size(); i++) {
            int gx = x + i * 18;
            if (mouseX >= gx && mouseX < gx + 16) {
                ItemStack stack = view().gear().get(i);
                if (!stack.isEmpty()) {
                    graphics.renderTooltip(font, stack, mouseX, mouseY);
                } else {
                    graphics.renderTooltip(font, Component.translatable(KEY + "gear." + i), mouseX, mouseY);
                }
            }
        }
    }

    private Component notes(CompanionViews.CompanionView view) {
        if (view.notes().isEmpty()) {
            return Component.translatable(KEY + "notes_none");
        }
        MutableComponent text = Component.empty();
        for (int i = 0; i < view.notes().size(); i++) {
            CompanionViews.Note note = view.notes().get(i);
            if (i > 0) {
                text.append(", ");
            }
            text.append(Component.translatable(KEY + "note", (note.change() > 0 ? "+" : "") + note.change(), CompanionTexts.reason(note.reason())));
        }
        return text;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
