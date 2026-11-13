package io.github.sekerator.kingdomworld.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Base of the mod's screens. Sizes come from the width of the translated texts, so layouts hold in every language:
 * buttons are as wide as their text plus a margin, translated labels are checked against the space they get, and
 * names of kingdoms and players (which can be long) are shortened with an ellipsis and shown in full on hover.
 */
public abstract class KingdomScreen extends Screen {
    protected static final int PAD = 8;
    protected static final int LINE = 10;
    private static final String ELLIPSIS = "…";

    /**
     * A text shown on hover; {@code full} marks the full form of a shortened text, listed first.
     */
    private record Hover(int x, int y, int width, Component text, boolean full) {
    }

    private final List<Hover> hovers = new ArrayList<>();

    protected KingdomScreen(Component title) {
        super(title);
    }

    /**
     * Name of the screen in layout check messages.
     */
    public abstract String checkName();

    /**
     * Draws the screen's own content; widgets and hover texts are drawn afterwards.
     */
    protected abstract void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    @Override
    public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hovers.clear();
        renderContent(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        LayoutCheck.widgets(this, font);
        List<Component> shown = hoverLines(mouseX, mouseY);
        if (!shown.isEmpty()) {
            graphics.renderComponentTooltip(font, shown, mouseX, mouseY);
        }
    }

    /**
     * Every hover text under the mouse in one list: the full forms of shortened texts first, then the details, each once.
     */
    protected List<Component> hoverLines(int mouseX, int mouseY) {
        List<Component> full = new ArrayList<>();
        List<Component> details = new ArrayList<>();
        for (Hover hover : hovers) {
            if (mouseX >= hover.x() && mouseX < hover.x() + hover.width() && mouseY >= hover.y() - 1 && mouseY < hover.y() + LINE) {
                List<Component> target = hover.full() ? full : details;
                if (target.stream().noneMatch(line -> line.getString().equals(hover.text().getString()))) {
                    target.add(hover.text());
                }
            }
        }
        full.addAll(details);
        return full;
    }

    /**
     * Width of a button that shows the widest of the texts without scrolling.
     */
    protected int buttonWidth(Component... texts) {
        int width = 0;
        for (Component text : texts) {
            width = Math.max(width, font.width(text));
        }
        return width + LayoutCheck.BUTTON_TEXT_MARGIN + 8;
    }

    /**
     * A translated text that must fit between {@code x} and {@code right}; a misfit is reported by the layout check.
     */
    protected void label(GuiGraphics graphics, Component text, int x, int y, int color, int right) {
        graphics.drawString(font, text, x, y, color);
        LayoutCheck.text(this, text, x, y, font.width(text), x, right);
        LayoutCheck.drawn(this, text, x, y, font.width(text));
    }

    /**
     * A translated text centered on {@code centerX} that must stay between {@code left} and {@code right}.
     */
    protected void centeredLabel(GuiGraphics graphics, Component text, int centerX, int y, int color, int left, int right) {
        int width = font.width(text);
        int x = centerX - width / 2;
        graphics.drawString(font, text, x, y, color);
        LayoutCheck.text(this, text, x, y, width, left, right);
        LayoutCheck.drawn(this, text, x, y, width);
    }

    /**
     * Draws a translated line that must end before {@code right}; a longer one is shortened with an ellipsis and shown in full
     * on hover.
     */
    protected void fittedLabel(GuiGraphics graphics, Component text, int x, int y, int color, int right) {
        int room = Math.max(0, right - x);
        if (font.width(text) <= room) {
            label(graphics, text, x, y, color, right);
            return;
        }
        String shown = fit(text.getString(), room);
        graphics.drawString(font, shown, x, y, color);
        int width = font.width(shown);
        LayoutCheck.drawn(this, Component.literal(shown), x, y, width);
        hoverFull(x, y, width, text);
    }

    /**
     * The name of a kingdom in the alphabet of the language of the game: kingdoms of the world have one in each.
     */
    protected static String kingdomName(String name, @org.jetbrains.annotations.Nullable io.github.sekerator.kingdomworld.common.network.data.NpcKingdomInfo npc) {
        if (npc == null) {
            return name;
        }
        String language = net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected();
        return io.github.sekerator.kingdomworld.core.citizen.CitizenNames.alphabetFor(language)
                == io.github.sekerator.kingdomworld.core.citizen.CitizenNames.Alphabet.CYRILLIC ? npc.nameCyrillic() : npc.nameLatin();
    }

    /**
     * The lines about a kingdom of the world: its ruler, its size, the character of its ruler, its people and soldiers, and the
     * ranks of its guilds.
     */
    protected static List<Component> npcLines(io.github.sekerator.kingdomworld.common.network.data.NpcKingdomInfo npc) {
        List<Component> lines = new java.util.ArrayList<>(npcBasics(npc));
        if (npc.ranked()) {
            lines.add(npcGuilds(npc.adventurers(), npc.mages()));
        }
        return lines;
    }

    /**
     * The ranks of the two guilds of a kingdom of the world: its branch of the adventurers' guild and its guild of mages.
     */
    protected static Component npcGuilds(io.github.sekerator.kingdomworld.core.guild.Rank adventurers,
                                         io.github.sekerator.kingdomworld.core.guild.Rank mages) {
        return Component.translatable("kingdomworld.screen.throne.npc.guilds", io.github.sekerator.kingdomworld.common.guild.GuildTexts.rank(adventurers),
                io.github.sekerator.kingdomworld.common.guild.GuildTexts.rank(mages));
    }

    private static List<Component> npcBasics(io.github.sekerator.kingdomworld.common.network.data.NpcKingdomInfo npc) {
        Component ruler = io.github.sekerator.kingdomworld.common.npc.NpcTexts.ruler(npc.rulerLatin(), npc.rulerCyrillic(), npc.rulerGender());
        List<Component> lines = new java.util.ArrayList<>(List.of(Component.translatable("kingdomworld.screen.throne.npc.ruler", ruler),
                Component.translatable(npc.raising() ? "kingdomworld.screen.throne.npc.size.raising" : "kingdomworld.screen.throne.npc.size",
                        io.github.sekerator.kingdomworld.common.npc.NpcTexts.level(npc.level())),
                Component.translatable("kingdomworld.screen.throne.npc.character",
                        io.github.sekerator.kingdomworld.common.npc.NpcTexts.character(npc.character()),
                        io.github.sekerator.kingdomworld.common.npc.NpcTexts.traits(npc.traits())),
                npcPeople(npc.raising(), npc.people(), npc.soldiers())));
        if (npc.settlements() > 0) {
            // The settlements of the state besides its capital: as numbers until they are raised.
            lines.add(Component.translatable("kingdomworld.screen.throne.npc.settlements", npc.settlements(),
                    io.github.sekerator.kingdomworld.common.life.LifeTexts.citizens(npc.settlementPeople())));
        }
        return List.copyOf(lines);
    }

    /**
     * The people and soldiers of a kingdom of the world: those it is expected to have while its capital is being built, the real
     * ones once it stands.
     */
    protected static Component npcPeople(boolean raising, int people, int soldiers) {
        return Component.translatable(raising ? "kingdomworld.screen.throne.npc.people.raising" : "kingdomworld.screen.throne.npc.people",
                io.github.sekerator.kingdomworld.common.life.LifeTexts.citizens(people),
                io.github.sekerator.kingdomworld.common.army.ArmyTexts.soldiers(soldiers));
    }

    /**
     * The hint over the line of the size of a kingdom of the world.
     */
    protected static Component npcHint() {
        return Component.translatable("kingdomworld.screen.throne.npc.size.hint");
    }

    /**
     * Shortens a name with an ellipsis so that it is at most {@code maxWidth} wide.
     */
    protected String fit(String name, int maxWidth) {
        if (font.width(name) <= maxWidth) {
            return name;
        }
        return font.plainSubstrByWidth(name, Math.max(0, maxWidth - font.width(ELLIPSIS))) + ELLIPSIS;
    }

    /**
     * Draws a name, shortened to {@code maxWidth}; when shortened, hovering shows it in full.
     *
     * @return the drawn width
     */
    protected int name(GuiGraphics graphics, String name, int x, int y, int color, int maxWidth) {
        String shown = fit(name, maxWidth);
        graphics.drawString(font, shown, x, y, color);
        int width = font.width(shown);
        LayoutCheck.drawn(this, Component.literal(shown), x, y, width);
        if (!shown.equals(name)) {
            hovers.add(new Hover(x, y, width, Component.literal(name), true));
        }
        return width;
    }

    /**
     * Shows the full form of a text shortened with an ellipsis while the mouse is over it; it comes first among the hover
     * texts of the place.
     */
    protected void hoverFull(int x, int y, int width, Component text) {
        hovers.add(new Hover(x, y, width, text, true));
    }

    /**
     * Shows a text in a tooltip while the mouse is over a line drawn at {@code x}, {@code y}.
     */
    protected void hoverText(int x, int y, int width, Component text) {
        hovers.add(new Hover(x, y, width, text, false));
    }

    /**
     * Draws a name centered on {@code centerX}, shortened to {@code maxWidth}.
     */
    protected void centeredName(GuiGraphics graphics, String name, int centerX, int y, int color, int maxWidth) {
        int width = font.width(fit(name, maxWidth));
        name(graphics, name, centerX - width / 2, y, color, maxWidth);
    }

    /**
     * Draws a text wrapped to {@code width}; a text that needs more than {@code maxLines} is reported as cut off.
     *
     * @return the number of lines drawn
     */
    protected int paragraph(GuiGraphics graphics, Component text, int x, int y, int width, int maxLines, int color) {
        if (text.getString().isEmpty()) {
            return 0;
        }
        List<FormattedCharSequence> lines = font.split(text, width);
        LayoutCheck.lines(this, text, lines.size(), maxLines);
        int drawn = 0;
        for (FormattedCharSequence line : lines) {
            if (drawn == maxLines) {
                break;
            }
            graphics.drawString(font, line, x, y + drawn * LINE, color);
            LayoutCheck.drawn(this, text, x, y + drawn * LINE, font.width(line));
            drawn++;
        }
        return drawn;
    }
}
