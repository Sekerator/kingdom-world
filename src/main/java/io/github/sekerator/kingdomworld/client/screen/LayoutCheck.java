package io.github.sekerator.kingdomworld.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects layout problems while a check is running: text wider than the place given to it, text that needs
 * more lines than fit, widgets whose text does not fit, anything leaving the screen, and lines of text drawn over
 * each other or over widgets. Screens report what they draw through {@link KingdomScreen}; outside a check every
 * call returns at once.
 */
public final class LayoutCheck {
    /** Space a button needs around its text so that the text neither touches the border nor scrolls. */
    public static final int BUTTON_TEXT_MARGIN = 8;
    private static final int LINE_HEIGHT = 9;

    private static List<String> problems;
    private static String context = "";
    /** Lines of text drawn in the current frame, by screen. */
    private static final Map<Screen, List<Area>> DRAWN = new IdentityHashMap<>();

    /**
     * Where a line of text was drawn.
     */
    private record Area(Component text, int x, int y, int width) {
        boolean overlaps(int otherX, int otherY, int otherWidth, int otherHeight) {
            return width > 0 && otherWidth > 0 && x < otherX + otherWidth && otherX < x + width && y < otherY + otherHeight
                    && otherY < y + LINE_HEIGHT;
        }
    }

    private LayoutCheck() {
    }

    public static void begin(String newContext) {
        problems = new ArrayList<>();
        context = newContext;
        DRAWN.clear();
    }

    public static List<String> end() {
        List<String> result = problems == null ? List.of() : problems;
        problems = null;
        DRAWN.clear();
        return result;
    }

    /**
     * A line of text was drawn at {@code x}, {@code y}; lines that cover each other are reported when the frame ends.
     */
    static void drawn(Screen screen, Component text, int x, int y, int width) {
        if (active() && width > 0) {
            DRAWN.computeIfAbsent(screen, s -> new ArrayList<>()).add(new Area(text, x, y, width));
        }
    }

    public static boolean active() {
        return problems != null;
    }

    /**
     * A single line of text drawn at {@code x}, which must stay between {@code left} and {@code right}.
     */
    static void text(Screen screen, Component text, int x, int y, int width, int left, int right) {
        if (!active()) {
            return;
        }
        if (x < left || x + width > right) {
            add(screen, text, "text is " + width + " px wide but only " + (right - left) + " px are free (from " + left + " to " + right
                    + ", drawn from " + x + ")");
        }
        if (x < 0 || x + width > screen.width || y < 0 || y + LINE_HEIGHT > screen.height) {
            add(screen, text, "text leaves the screen at " + x + "," + y + " (" + width + " px wide, screen " + screen.width + "x"
                    + screen.height + ")");
        }
    }

    /**
     * A wrapped text that needs {@code needed} lines where {@code allowed} fit.
     */
    static void lines(Screen screen, Component text, int needed, int allowed) {
        if (active() && needed > allowed) {
            add(screen, text, "text needs " + needed + " lines but only " + allowed + " fit, the rest is cut off");
        }
    }

    /**
     * Checks every widget of the screen: inside the screen, and the text of buttons fits with a margin.
     */
    public static void widgets(Screen screen, Font font) {
        if (!active()) {
            return;
        }
        overlaps(screen);
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractWidget widget) || !widget.visible) {
                continue;
            }
            if (widget.getX() < 0 || widget.getY() < 0 || widget.getX() + widget.getWidth() > screen.width
                    || widget.getY() + widget.getHeight() > screen.height) {
                add(screen, widget.getMessage(), "widget at " + widget.getX() + "," + widget.getY() + " size " + widget.getWidth()
                        + "x" + widget.getHeight() + " leaves the screen " + screen.width + "x" + screen.height);
            }
            if (widget instanceof AbstractButton) {
                int textWidth = font.width(widget.getMessage());
                if (textWidth + BUTTON_TEXT_MARGIN > widget.getWidth()) {
                    add(screen, widget.getMessage(), "button text is " + textWidth + " px wide, the button only " + widget.getWidth()
                            + " px");
                }
            }
        }
    }

    /**
     * Reports lines of text of the frame that are drawn over each other or over a widget, then forgets them.
     */
    private static void overlaps(Screen screen) {
        List<Area> areas = DRAWN.remove(screen);
        if (areas == null) {
            return;
        }
        for (int i = 0; i < areas.size(); i++) {
            Area area = areas.get(i);
            for (int j = i + 1; j < areas.size(); j++) {
                Area other = areas.get(j);
                if (area.overlaps(other.x(), other.y(), other.width(), LINE_HEIGHT)) {
                    add(screen, area.text(), "text at " + area.x() + "," + area.y() + " is drawn over the text '" + other.text().getString()
                            + "' (key " + key(other.text()) + ") at " + other.x() + "," + other.y());
                }
            }
            for (GuiEventListener child : screen.children()) {
                if (child instanceof AbstractWidget widget && widget.visible
                        && area.overlaps(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight())) {
                    add(screen, area.text(), "text at " + area.x() + "," + area.y() + " is drawn over the widget '"
                            + widget.getMessage().getString() + "' at " + widget.getX() + "," + widget.getY() + " size "
                            + widget.getWidth() + "x" + widget.getHeight());
                }
            }
        }
    }

    /**
     * A message shown above the hotbar, which is never wrapped: it must fit the narrowest screen.
     */
    public static void actionbar(Font font, Component message, int screenWidth) {
        if (!active()) {
            return;
        }
        int width = font.width(message);
        if (width > screenWidth - 2 * BUTTON_TEXT_MARGIN) {
            problems.add(context + " | actionbar | key " + key(message) + " | message is " + width + " px wide, the screen only "
                    + screenWidth + " px");
        }
    }

    private static void add(Screen screen, Component text, String problem) {
        String name = screen instanceof KingdomScreen kingdomScreen ? kingdomScreen.checkName() : screen.getClass().getSimpleName();
        problems.add(context + " | " + name + " | key " + key(text) + " | " + problem);
    }

    static String key(Component text) {
        if (text.getContents() instanceof TranslatableContents translatable) {
            return translatable.getKey();
        }
        return "literal '" + text.getString() + "'";
    }
}
