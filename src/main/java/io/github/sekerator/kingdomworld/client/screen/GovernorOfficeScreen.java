package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.network.data.WorkforceView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * The office of a citizen chosen in the window of the workers: make it the mayor or the baron of a city. The city is the one
 * the window shows when it was opened from the actions of a city, else the city of the post of the citizen, else the city of
 * its home. A button the citizen or the player cannot use is inactive and says why on hover; the server checks everything
 * again.
 */
public final class GovernorOfficeScreen extends KingdomScreen {
    private static final String KEY = "kingdomworld.screen.workforce.office.";
    private static final String UNFIT = "kingdomworld.governor.unfit.";
    private static final int WIDTH = 300;

    private final WorkforceScreen parent;
    private final WorkforceView.CitizenRow citizen;
    private final String city;
    private final Component cityName;
    private final boolean mayors;
    private final boolean barons;
    private final Consumer<Boolean> chosen;

    /**
     * @param city     the key of the city ({@code c<id>}), empty when none could be told
     * @param cityName its name
     * @param mayors   whether the player may make a mayor
     * @param barons   whether the player may make a baron (the king)
     * @param chosen   called with true for a mayor, false for a baron
     */
    public GovernorOfficeScreen(WorkforceScreen parent, WorkforceView.CitizenRow citizen, Component name, String city, Component cityName,
                                boolean mayors, boolean barons, Consumer<Boolean> chosen) {
        super(Component.translatable(KEY + "title", name));
        this.parent = parent;
        this.citizen = citizen;
        this.city = city;
        this.cityName = cityName;
        this.mayors = mayors;
        this.barons = barons;
        this.chosen = chosen;
    }

    @Override
    public String checkName() {
        return "workforce-citizen-mayor";
    }

    /**
     * The city a citizen would govern: that of the filter of the window when it shows a city, else that of its post, else that of
     * its home; empty for none.
     */
    public static String cityFor(WorkforceView.Query query, WorkforceView.CitizenRow citizen) {
        if (query.place().startsWith("c")) {
            return query.place();
        }
        if (citizen.workplace() != null) {
            return citizen.postPlace();
        }
        return citizen.place().startsWith("c") ? citizen.place() : "";
    }

    /**
     * Why a citizen cannot become the mayor (or, with {@code baron}, the baron) of a city, as a translation key; {@code null} if it
     * can as far as the window knows.
     */
    @Nullable
    public static String unfit(WorkforceView.CitizenRow citizen, String city, boolean baron) {
        if (city.isEmpty()) {
            return UNFIT + "no_city";
        }
        if (baron && city.equals("c0")) {
            return "kingdomworld.governor.capital_no_fief";
        }
        if (citizen.soldier()) {
            return UNFIT + "soldier";
        }
        if (citizen.workplace() != null && !citizen.postPlace().equals(city)) {
            return UNFIT + "post_elsewhere";
        }
        if (citizen.office() == WorkforceView.CitizenRow.OFFICE_BARON) {
            return UNFIT + "head_of_house";
        }
        if (citizen.office() == WorkforceView.CitizenRow.OFFICE_MAYOR) {
            if (!citizen.officePlace().equals(city)) {
                return UNFIT + "mayor_elsewhere";
            }
            if (!baron) {
                return KEY + "already";
            }
        }
        return null;
    }

    private int left() {
        return (width - Math.min(WIDTH, width - 2 * PAD)) / 2;
    }

    /**
     * The top of the window: the title, the city and a line on what governors do over the buttons.
     */
    private int top() {
        return Math.max(PAD, height / 2 - 60);
    }

    private int panelWidth() {
        return Math.min(WIDTH, width - 2 * PAD);
    }

    @Override
    protected void init() {
        int left = left();
        int panel = panelWidth();
        int y = top() + 42;
        addOffice(left, y, panel, false);
        addOffice(left, y + 22, panel, true);
        Component cancel = Component.translatable("gui.cancel");
        int cancelWidth = Math.max(60, buttonWidth(cancel));
        addRenderableWidget(Button.builder(cancel, b -> onClose()).bounds((width - cancelWidth) / 2, y + 50, cancelWidth, 20).build());
    }

    private void addOffice(int left, int y, int panel, boolean baron) {
        Component label = Component.translatable(KEY + (baron ? "baron" : "mayor"), city.isEmpty() ? Component.translatable(KEY + "no_city")
                : cityName);
        String shown = fit(label.getString(), panel - 8);
        Button button = addRenderableWidget(Button.builder(Component.literal(shown), b -> {
            onClose();
            chosen.accept(!baron);
        }).bounds(left, y, panel, 20).build());
        String why = baron && !barons ? KEY + "only_king" : !baron && !mayors ? KEY + "no_right" : unfit(citizen, city, baron);
        button.active = why == null;
        Component hint = Component.translatable(KEY + (baron ? "baron" : "mayor") + ".hint");
        button.setTooltip(Tooltip.create(why == null ? Component.translatable(KEY + "hint", label, hint)
                : Component.translatable(KEY + "hint", label, Component.translatable(why))));
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = left();
        int panel = panelWidth();
        int top = top();
        graphics.fill(left - 6, top - 6, left + panel + 6, top + 120, 0xF0201810);
        graphics.renderOutline(left - 6, top - 6, panel + 12, 126, 0xFFB08850);
        centeredName(graphics, title.getString(), width / 2, top, 0xFFF0D070, panel);
        Component where = city.isEmpty() ? Component.translatable(KEY + "city_unknown") : Component.translatable(KEY + "city", cityName);
        fittedLabel(graphics, where, left, top + 14, 0xFFE8E8E8, left + panel);
        fittedLabel(graphics, Component.translatable(KEY + "intro"), left, top + 26, 0xFFB8B8B8, left + panel);
    }
}
