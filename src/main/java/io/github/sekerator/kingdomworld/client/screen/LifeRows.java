package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.life.LifeTexts;
import io.github.sekerator.kingdomworld.common.network.data.LifeViews;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The life of a kingdom in the citizens tab, as rows: how the citizens spend their day now, their day by the clock, the
 * taverns, festivals and fairs, who is ill and where, and the chronicle of festivals and fairs.
 */
final class LifeRows {
    private static final String KEY = "kingdomworld.screen.citizens.life.";
    private static final int NAME_COLUMN = 86;

    /**
     * A row: a line of text, or an ill citizen.
     */
    record Row(Component text, int color, LifeViews.Sick sick) {
    }

    private LifeRows() {
    }

    static Component festivalHint(LifeViews.TabView life) {
        if (life.festival() == 0) {
            return Component.translatable(KEY + "today");
        }
        if (life.nextIn() > 0) {
            return Component.translatable(KEY + "wait", LifeTexts.days(life.nextIn()));
        }
        return Component.translatable(KEY + "cost", MoneyTexts.amount(life.cost(), life.currency().units()), life.treat(), life.mood());
    }

    static List<Row> rows(LifeViews.TabView life) {
        List<Row> rows = new ArrayList<>();
        if (life == null) {
            return rows;
        }
        int[] c = life.counts();
        rows.add(new Row(Component.translatable(KEY + "now", c[0], c[1], c[2], c[3]), 0xFFE8E8E8, null));
        int[] h = life.hours();
        rows.add(new Row(Component.translatable(KEY + "day", LifeTexts.hours(h[0]), LifeTexts.hours(h[1]), LifeTexts.hours(h[2]),
                LifeTexts.hours(h[3])), 0xFFD0D0D0, null));
        int[] t = life.taverns();
        rows.add(new Row(t[0] == 0 ? Component.translatable(KEY + "no_tavern")
                : Component.translatable(KEY + "taverns", t[0], t[1], LifeTexts.guests(life.guests())), t[0] == 0 ? 0xFFB8B8B8 : 0xFFE8DCC0, null));
        Component festival = life.festival() == 0 ? Component.translatable(KEY + "today")
                : life.festival() < 0 ? Component.translatable(KEY + "never") : Component.translatable(KEY + "last", LifeTexts.days(life.festival()));
        rows.add(new Row(festival, life.festival() == 0 ? 0xFFA0E0A0 : 0xFFE8E8E8, null));
        if (life.festival() != 0) {
            rows.add(new Row(festivalHint(life), life.nextIn() > 0 ? 0xFFB8B8B8 : 0xFFF0D070, null));
        }
        if (life.fairs().isEmpty()) {
            rows.add(new Row(Component.translatable(KEY + "no_fairs"), 0xFFB8B8B8, null));
        }
        for (LifeViews.Fair fair : life.fairs()) {
            rows.add(new Row(fair.days() == 0 ? Component.translatable(KEY + "fair_today", fair.city())
                    : Component.translatable(KEY + "fair_in", fair.city(), LifeTexts.days(fair.days())), fair.days() == 0 ? 0xFFA0E0A0 : 0xFFD0D0D0, null));
        }
        rows.add(new Row(life.sick().isEmpty() ? Component.translatable(KEY + "no_sick")
                : Component.translatable(KEY + "sick", LifeTexts.citizens(life.sick().size())), life.sick().isEmpty() ? 0xFFA0E0A0 : 0xFFFF9070, null));
        for (LifeViews.Sick sick : life.sick()) {
            rows.add(new Row(Component.empty(), 0, sick));
        }
        if (!life.events().isEmpty()) {
            rows.add(new Row(Component.translatable(KEY + "chronicle"), 0xFFF0D070, null));
        }
        for (LifeViews.Event event : life.events()) {
            rows.add(new Row(event.place().isEmpty() ? Component.translatable(KEY + "event." + event.kind(), event.day())
                    : Component.translatable(KEY + "event." + event.kind() + "_in", event.day(), event.place()), 0xFFD0D0D0, null));
        }
        return rows;
    }

    static void render(ThroneScreen screen, GuiGraphics graphics, LifeViews.TabView life, int x, int right, int listTop, int visible, int scroll,
                       int rowHeight) {
        if (life == null) {
            screen.label(graphics, Component.translatable("kingdomworld.screen.citizens.loading"), x, listTop + 5, 0xFFB8B8B8, right);
            return;
        }
        List<Row> rows = rows(life);
        for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            int y = listTop + i * rowHeight + 4;
            if (row.sick() == null) {
                screen.name(graphics, row.text().getString(), x, y, row.color(), right - x);
                continue;
            }
            LifeViews.Sick sick = row.sick();
            Gender gender = sick.female() ? Gender.FEMALE : Gender.MALE;
            screen.name(graphics, CitizenTexts.name(sick.seed(), gender).getString(), x + 6, y, 0xFFFFFFFF, NAME_COLUMN - 10);
            Component percent = Component.translatable(KEY + "better", sick.percent());
            int percentX = right - screen.font().width(percent);
            screen.label(graphics, percent, percentX, y, 0xFFA0E0A0, right);
            Component where = sick.where() == null ? Component.translatable(KEY + "at_home", CitizenTexts.profession(sick.profession(), gender))
                    : Component.translatable(KEY + "in_infirmary", CitizenTexts.profession(sick.profession(), gender), sick.where().getX(),
                    sick.where().getY(), sick.where().getZ());
            screen.name(graphics, where.getString(), x + NAME_COLUMN, y, 0xFFD0D0D0, percentX - 6 - x - NAME_COLUMN);
        }
    }
}
