package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.network.data.RoadViews;
import io.github.sekerator.kingdomworld.common.road.RoadTexts;
import io.github.sekerator.kingdomworld.core.road.RoadLayout;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The card of a plan of a road on the atlas: where it goes, or where the planner is with it, or why it found nothing; its length
 * and the time of a caravan, its earthworks, its materials with those missing in red, the days of builders and miners and the time
 * the road takes with the workers there are, what it is worth, the foreign land it touches and the gates it cuts, and how it differs
 * from the last plan shown. Client only.
 */
public final class RoadPlanCard {
    static final String KEY = "kingdomworld.screen.map.road.card.";
    /** The colour of each kind of block of a way: ground, bridge, embankment, cutting, tunnel. */
    static final int[] KIND_COLORS = {0xFFFF8C1A, 0xFF4A90E2, 0xFFC9A227, 0xFF8B5A2B, 0xFF555555};

    private RoadPlanCard() {
    }

    /**
     * The colour of a kind of block of a way.
     */
    static int color(int kind) {
        return kind >= 0 && kind < KIND_COLORS.length ? KIND_COLORS[kind] : KIND_COLORS[0];
    }

    /**
     * Days of a number of seconds of work, to a tenth.
     */
    static String days(double seconds) {
        return String.format(java.util.Locale.ROOT, "%.1f", seconds / 1_200.0);
    }

    static String minutes(int seconds) {
        return seconds < 60 ? seconds + "s" : String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    /**
     * The lines of the card.
     *
     * @param previous the plan shown before, for the difference
     */
    static List<Component> lines(RoadViews.PlanView view, @Nullable RoadViews.PlanView previous) {
        List<Component> lines = new ArrayList<>();
        if (!view.error().isEmpty()) {
            lines.add(Component.translatable("kingdomworld.error." + view.error()).withStyle(ChatFormatting.RED));
            return lines;
        }
        if (view.searching() || view.planId() == 0 && view.stage() != RoadViews.PlanView.Stage.FAILED) {
            if (view.queue() > 0) {
                lines.add(Component.translatable(KEY + "queue", view.queue()));
            } else {
                lines.add(Component.translatable(KEY + "stage", Component.translatable(KEY + "stage." + view.stage().id()), view.progress()));
            }
            return lines;
        }
        if (view.stage() == RoadViews.PlanView.Stage.FAILED) {
            for (int i = 0; i < view.legs().size(); i++) {
                RoadViews.PlanView.LegView leg = view.legs().get(i);
                if (!leg.reason().isEmpty()) {
                    lines.add(Component.translatable(KEY + "failed", i + 1, Component.translatable(KEY + "reason." + leg.reason()), leg.stuckX(),
                            leg.stuckZ()).withStyle(ChatFormatting.RED));
                }
            }
            if (lines.isEmpty()) {
                lines.add(Component.translatable("kingdomworld.error.no_route").withStyle(ChatFormatting.RED));
            }
            return lines;
        }
        int between = Math.max(0, view.points().length / 2 - 2);
        lines.add(between > 0 ? Component.translatable(KEY + "way_through", view.from(), view.to(), between)
                : Component.translatable(KEY + "way", view.from(), view.to()));
        lines.add(Component.translatable(KEY + "length", RoadTexts.blocks(view.length()), RoadTexts.blocks(view.straight()),
                minutes(view.caravan()), minutes(view.across())));
        if (view.levelled()) {
            int[] w = view.works();
            lines.add(Component.translatable(KEY + "works", w[4], w[2], w[3], w[5], w[7], w[8]));
        } else {
            lines.add(Component.translatable(KEY + "works_old", view.works()[7], view.works()[6]));
        }
        MutableComponent materials = Component.translatable(KEY + "materials");
        boolean first = true;
        for (RoadLayout.Material material : RoadLayout.Material.values()) {
            int count = material.ordinal() < view.materials().length ? view.materials()[material.ordinal()] : 0;
            if (count <= 0) {
                continue;
            }
            int missing = material.ordinal() < view.missing().length ? view.missing()[material.ordinal()] : 0;
            MutableComponent part = Component.translatable(KEY + "material", RoadTexts.material(material), count);
            if (missing > 0) {
                part = part.append(Component.translatable(KEY + "missing", missing)).withStyle(ChatFormatting.RED);
            }
            materials.append(first ? Component.literal(" ") : Component.literal(", ")).append(part);
            first = false;
        }
        lines.add(materials);
        if (view.term() < 0) {
            lines.add(Component.translatable(KEY + "labour_no_builders", days(view.builderSeconds()), days(view.minerSeconds())));
        } else {
            lines.add(Component.translatable(KEY + "labour", days(view.builderSeconds()), days(view.minerSeconds()),
                    String.format(java.util.Locale.ROOT, "%.1f", view.term() / 10.0)));
        }
        if (view.coins() > 0) {
            lines.add(Component.translatable(KEY + "coins", view.coins(), view.currency()));
        }
        if (!view.foreign().isEmpty() || view.gates() > 0) {
            lines.add(Component.translatable(KEY + "foreign", view.foreign().isEmpty() ? Component.translatable(KEY + "none")
                    : Component.literal(String.join(", ", view.foreign())), view.gates()));
        }
        if (view.guessed()) {
            lines.add(Component.translatable(KEY + "guessed").withStyle(ChatFormatting.GOLD));
        }
        if (previous != null && previous.planId() != 0 && previous.planId() != view.planId() && previous.levelled() == view.levelled()) {
            lines.add(difference(view, previous));
        }
        return lines;
    }

    /**
     * How a plan differs from the one shown before: length, work, days and coins, green where it is better.
     */
    static Component difference(RoadViews.PlanView view, RoadViews.PlanView previous) {
        int[] now = view.works();
        int[] before = previous.works();
        int work = now[1] + now[2] + now[3] - (before[1] + before[2] + before[3]);
        double days = (view.builderSeconds() + view.minerSeconds() - previous.builderSeconds() - previous.minerSeconds()) / 1_200.0;
        return Component.translatable(KEY + "difference", signed(view.length() - previous.length()), signed(work),
                signed(days), signed(view.coins() - previous.coins()));
    }

    private static Component signed(double value) {
        // Shown to a tenth: what rounds to nothing is nothing, without a sign.
        value = Math.round(value * 10) / 10.0;
        String text = value == 0 ? "0" : value == Math.rint(value) ? String.format(java.util.Locale.ROOT, "%+d", (long) value)
                : String.format(java.util.Locale.ROOT, "%+.1f", value);
        return Component.literal(text).withStyle(value > 0 ? ChatFormatting.RED : value < 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    /**
     * The name of a kind of block of a way.
     */
    static Component kind(int kind) {
        RouteCell.Kind[] kinds = RouteCell.Kind.values();
        return Component.translatable(KEY + "kind." + (kind >= 0 && kind < kinds.length ? kinds[kind] : RouteCell.Kind.GROUND).id());
    }
}
