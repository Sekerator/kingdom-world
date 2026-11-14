package io.github.sekerator.kingdomworld.client;

import io.github.sekerator.kingdomworld.client.screen.FlagScreen;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.common.flag.FlagPatterns;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomColors;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomFlag;
import io.github.sekerator.kingdomworld.core.membership.Role;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Layout cases of the editor of the flag for the client self-check: the king drawing a flag of no, three and six layers with the
 * longest names of layers in the language of the game, with the field or a layer picked, with a note and an answer of the
 * server; the flag as others see it, of a kingdom of players and of the world; and the question before leaving unsaved.
 */
final class FlagLayoutCases {

    private FlagLayoutCases() {
    }

    static void cases(BiConsumer<String, Supplier<Screen>> add, BiFunction<KingdomView.Relation, Role, KingdomView> view,
                      UnaryOperator<KingdomView> world) {
        add.accept("flag-king-plain", () -> editor(view.apply(KingdomView.Relation.MEMBER, Role.KING), KingdomFlag.plain(11), 0,
                Component.empty()));
        add.accept("flag-king-three-field", () -> editor(view.apply(KingdomView.Relation.MEMBER, Role.KING), longest(3), 0,
                Component.empty()));
        add.accept("flag-king-three-layer", () -> editor(view.apply(KingdomView.Relation.MEMBER, Role.KING), longest(3), 2,
                Component.empty()));
        add.accept("flag-king-six", () -> editor(view.apply(KingdomView.Relation.MEMBER, Role.KING), longest(6), 6,
                Component.translatable("kingdomworld.screen.flag.max", KingdomFlag.MAX_LAYERS)));
        add.accept("flag-king-six-field", () -> editor(view.apply(KingdomView.Relation.MEMBER, Role.KING), longest(6), 0,
                Component.empty()));
        add.accept("flag-king-error", () -> editor(view.apply(KingdomView.Relation.MEMBER, Role.KING), longest(3), 1,
                KingdomActions.errorMessage(KingdomError.READ_ONLY)));
        add.accept("flag-king-missing", () -> editor(view.apply(KingdomView.Relation.MEMBER, Role.KING),
                new KingdomFlag(4, List.of(new KingdomFlag.Layer("removedmod:very_long_pattern_of_a_mod_taken_out", 15),
                        new KingdomFlag.Layer("minecraft:cross", 14))), 1, Component.empty()));
        add.accept("flag-subject", () -> viewer(view.apply(KingdomView.Relation.MEMBER, Role.SUBJECT), longest(6)));
        add.accept("flag-outsider-plain", () -> viewer(view.apply(KingdomView.Relation.OUTSIDER, null), KingdomFlag.plain(10)));
        add.accept("flag-world", () -> viewer(world.apply(view.apply(KingdomView.Relation.OUTSIDER, null)), longest(3)));
        add.accept("confirm-flag-discard", () -> new ConfirmScreen(yes -> {
        }, Component.translatable("kingdomworld.screen.throne.confirm_title"), Component.translatable("kingdomworld.screen.flag.discard")));
    }

    private static FlagScreen editor(KingdomView kingdom, KingdomFlag flag, int row, Component status) {
        FlagScreen screen = new FlagScreen(new ThroneScreen(kingdom), kingdom.id(), kingdom.name(), kingdom.color(), kingdom.style(), true);
        screen.showForCheck(flag, row);
        if (!status.getString().isEmpty()) {
            screen.onResult(false, status);
        }
        return screen;
    }

    private static FlagScreen viewer(KingdomView kingdom, KingdomFlag flag) {
        FlagScreen screen = new FlagScreen(new ThroneScreen(kingdom), kingdom.id(), kingdom.name(), kingdom.color(), kingdom.style(), false);
        screen.showForCheck(flag, 0);
        return screen;
    }

    /**
     * A flag of the layers with the widest names in the language of the game, each pattern once.
     */
    private static KingdomFlag longest(int layers) {
        record Named(String pattern, int dye, int width) {
        }
        List<Named> names = new ArrayList<>();
        for (String pattern : FlagPatterns.all()) {
            Named widest = null;
            for (int dye = 0; dye < KingdomColors.DYES; dye++) {
                int width = Minecraft.getInstance().font.width(FlagPatterns.name(pattern, dye));
                if (widest == null || width > widest.width()) {
                    widest = new Named(pattern, dye, width);
                }
            }
            names.add(widest);
        }
        names.sort(Comparator.comparingInt(Named::width).reversed());
        List<KingdomFlag.Layer> chosen = new ArrayList<>();
        for (int i = 0; i < layers && i < names.size(); i++) {
            chosen.add(new KingdomFlag.Layer(names.get(i).pattern(), names.get(i).dye()));
        }
        return new KingdomFlag(14, chosen);
    }
}
