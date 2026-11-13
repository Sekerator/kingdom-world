package io.github.sekerator.kingdomworld.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * A button that goes through a row of choices: the left mouse button takes the next one, the right one the one
 * before, so that a choice passed by mistake is one click back and not a whole round away. It may show an item in
 * front of its text. Its tooltip says that the right button goes back, under whatever else it has to say.
 */
public class CycleButton extends Button {
    /** Room the item takes in front of the text. */
    public static final int ICON_SPACE = 18;

    /** The line of a tooltip that tells about the way back. */
    public static final String HINT_KEY = "kingdomworld.screen.cycle_back";

    /** Told which way to go: 1 for the next choice, -1 for the one before. */
    @FunctionalInterface
    public interface Step {
        void by(int direction);
    }

    private final ItemStack icon;
    private int pressed;

    public CycleButton(int x, int y, int width, int height, Component message, ItemStack icon, Step step) {
        super(x, y, width, height, message, button -> step.by(((CycleButton) button).pressed == 1 ? -1 : 1), DEFAULT_NARRATION);
        this.icon = icon;
        setTooltip(Tooltip.create(Component.translatable(HINT_KEY)));
    }

    public CycleButton(int x, int y, int width, int height, Component message, Step step) {
        this(x, y, width, height, message, ItemStack.EMPTY, step);
    }

    /**
     * The tooltip of a button that goes through choices: {@code text} and under it the line about the way back.
     */
    public static Tooltip hinted(Component text) {
        return Tooltip.create(Component.empty().append(text).append("\n").append(Component.translatable(HINT_KEY)));
    }

    @Override
    protected boolean isValidClickButton(int button) {
        return button == 0 || button == 1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        pressed = button;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        pressed = 0;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderString(GuiGraphics graphics, Font font, int color) {
        if (icon.isEmpty()) {
            super.renderString(graphics, font, color);
            return;
        }
        graphics.renderItem(icon, getX() + 2, getY() + (getHeight() - 16) / 2);
        renderScrollingString(graphics, font, getMessage(), getX() + ICON_SPACE + 2, getY(), getX() + getWidth() - 2,
                getY() + getHeight(), color);
    }
}
