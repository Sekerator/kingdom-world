package io.github.sekerator.kingdomworld.client;

import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.network.data.CompanionViews;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The list in the top left corner of the screen: the members of the player's party with their health (the leader marked,
 * those offline greyed), then the player's companions with theirs. It is as narrow as its longest line and as short as
 * the screen allows; lines that do not fit are left out with a note of how many.
 */
public final class PartyHud {
    private static final int MARGIN = 3;
    private static final int LINE = 10;
    private static final int BAR = 30;
    private static List<CompanionViews.HudLine> lines = List.of();

    /**
     * Something that wants to know where text was drawn; the layout check listens.
     */
    @FunctionalInterface
    public interface Listener {
        Listener NONE = (text, x, y, width) -> {
        };

        void drawn(Component text, int x, int y, int width);
    }

    private PartyHud() {
    }

    public static void setLines(List<CompanionViews.HudLine> value) {
        lines = List.copyOf(value);
    }

    public static List<CompanionViews.HudLine> lines() {
        return lines;
    }

    public static void clear() {
        lines = List.of();
    }

    /**
     * Draws the list from the game's overlay.
     */
    static void draw(GuiGraphics graphics, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (lines.isEmpty() || minecraft.options.hideGui || minecraft.options.renderDebug || !KingdomWorldConfig.CLIENT.showPartyList.get()) {
            return;
        }
        render(graphics, minecraft.font, lines, width, height, Listener.NONE);
    }

    private static Component name(CompanionViews.HudLine line) {
        return line.companion() ? CitizenTexts.name(line.seed(), line.gender()) : Component.literal(line.name());
    }

    private static Component health(CompanionViews.HudLine line) {
        if (!line.online()) {
            return Component.translatable(line.companion() ? "kingdomworld.party.hud.away" : "kingdomworld.party.hud.offline");
        }
        return Component.translatable("kingdomworld.party.hud.health", (int) Math.ceil(line.health()), (int) Math.ceil(line.maxHealth()));
    }

    /**
     * Draws the list for a screen of a size.
     *
     * @return the height drawn
     */
    public static int render(GuiGraphics graphics, Font font, List<CompanionViews.HudLine> shown, int width, int height, Listener listener) {
        int fit = Math.max(1, (height / 2 - 2 * MARGIN) / LINE);
        int count = Math.min(shown.size(), fit);
        boolean cut = count < shown.size();
        if (cut) {
            count = Math.max(0, fit - 1);
        }
        int nameWidth = 0;
        int healthWidth = 0;
        for (int i = 0; i < count; i++) {
            CompanionViews.HudLine line = shown.get(i);
            nameWidth = Math.max(nameWidth, font.width(name(line)) + (line.companion() ? 6 : 0));
            healthWidth = Math.max(healthWidth, font.width(health(line)));
        }
        Component more = cut ? Component.translatable("kingdomworld.party.hud.more", shown.size() - count) : Component.empty();
        int markWidth = font.width("! ");
        int boxWidth = Math.max(markWidth + nameWidth + 4 + Math.max(BAR, healthWidth), font.width(more)) + 2 * MARGIN;
        int boxHeight = (count + (cut ? 1 : 0)) * LINE + 2 * MARGIN;
        int x = 2;
        int y = 2;
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0x80000000);
        for (int i = 0; i < count; i++) {
            CompanionViews.HudLine line = shown.get(i);
            int lineY = y + MARGIN + i * LINE;
            int textX = x + MARGIN;
            if (line.leader() || line.warned()) {
                Component mark = Component.literal(line.warned() ? "!" : "*");
                graphics.drawString(font, mark, textX, lineY, line.warned() ? 0xFFFF6060 : 0xFFF0D070);
                listener.drawn(mark, textX, lineY, font.width(mark));
            }
            int nameX = textX + markWidth + (line.companion() ? 6 : 0);
            Component name = name(line);
            int color = !line.online() ? 0xFF909090 : line.companion() ? 0xFFB0E0B0 : 0xFFFFFFFF;
            graphics.drawString(font, name, nameX, lineY, color);
            listener.drawn(name, nameX, lineY, font.width(name));
            int barX = textX + markWidth + nameWidth + 4;
            if (line.online() && line.maxHealth() > 0) {
                float share = Math.max(0.0f, Math.min(1.0f, line.health() / line.maxHealth()));
                int barWidth = Math.max(BAR, healthWidth);
                graphics.fill(barX, lineY + 8, barX + barWidth, lineY + 9, 0xFF402020);
                graphics.fill(barX, lineY + 8, barX + Math.round(barWidth * share), lineY + 9, share > 0.4f ? 0xFF40C040 : 0xFFE04040);
            }
            Component health = health(line);
            graphics.drawString(font, health, barX, lineY - 1, line.online() ? 0xFFE0E0E0 : 0xFF909090);
            listener.drawn(health, barX, lineY - 1, font.width(health));
        }
        if (cut) {
            int lineY = y + MARGIN + count * LINE;
            graphics.drawString(font, more, x + MARGIN, lineY, 0xFFB0B0B0);
            listener.drawn(more, x + MARGIN, lineY, font.width(more));
        }
        return boxHeight;
    }
}
