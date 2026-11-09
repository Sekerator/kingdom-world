package io.github.sekerator.kingdomworld.common.army;

import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.Mercenary;
import io.github.sekerator.kingdomworld.core.army.MercenaryBand;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.army.SoldierRank;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.economy.Plurals;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Texts of the army: ranks, orders, names of squads and mercenaries, and counts in every plural form.
 */
public final class ArmyTexts {
    private static final String KEY = "kingdomworld.army.";
    private static final String COUNT = "kingdomworld.count.";

    private ArmyTexts() {
    }

    public static Component rank(SoldierRank rank) {
        return Component.translatable(KEY + "rank." + rank.id());
    }

    public static MutableComponent soldiers(long count) {
        return Component.translatable(COUNT + "soldiers." + Plurals.form(count).suffix(), count);
    }

    public static MutableComponent mercenaries(long count) {
        return Component.translatable(COUNT + "mercenaries." + Plurals.form(count).suffix(), count);
    }

    public static MutableComponent fighters(long count) {
        return Component.translatable(COUNT + "fighters." + Plurals.form(count).suffix(), count);
    }

    public static MutableComponent enemies(long count) {
        return Component.translatable(COUNT + "enemies." + Plurals.form(count).suffix(), count);
    }

    public static MutableComponent squads(long count) {
        return Component.translatable(COUNT + "squads." + Plurals.form(count).suffix(), count);
    }

    /**
     * The short name of an order.
     */
    public static Component orderName(Order.Kind kind) {
        return Component.translatable(KEY + "order." + kind.id());
    }

    private static Component place(WorldPos pos) {
        return Component.translatable(KEY + "place", pos.x(), pos.y(), pos.z());
    }

    /**
     * What a squad does: hold a point at x y z, patrol a route of n points from x y z, and so on.
     *
     * @param playerName the name of the player followed, if known
     * @param deal       what the escorted deal carries, if known
     */
    public static Component order(Order order, Component playerName, Component deal) {
        return switch (order.kind()) {
            case NONE -> Component.translatable(KEY + "order_text.none");
            case HOLD -> Component.translatable(KEY + "order_text.hold", place(order.points().get(0)));
            case PATROL -> Component.translatable(KEY + "order_text.patrol", order.points().size(), place(order.points().get(0)));
            case GUARD -> Component.translatable(KEY + "order_text.guard", 2 * order.radius() + 1, 2 * order.radius() + 1,
                    place(order.points().get(0)));
            case FOLLOW -> Component.translatable(KEY + "order_text.follow", playerName);
            case ESCORT -> Component.translatable(KEY + "order_text.escort", order.deal(), deal);
            case SIEGE -> Component.translatable(KEY + "order_text.siege", place(order.points().get(0)));
            case DEFEND -> Component.translatable(KEY + "order_text.defend", place(order.points().get(0)));
            case MOVE -> Component.translatable(KEY + "order_text.move", place(order.points().get(0)));
            case ATTACK -> order.target() != 0 ? Component.translatable(KEY + "order_text.attack_squad", order.target())
                    : Component.translatable(KEY + "order_text.attack", place(order.points().get(0)));
            case RETREAT -> Component.translatable(KEY + "order_text.retreat");
            case SCOUT -> Component.translatable(KEY + "order_text.scout", 2 * order.radius() + 1, 2 * order.radius() + 1,
                    place(order.points().get(0)));
        };
    }

    public static Component formationName(io.github.sekerator.kingdomworld.core.combat.Formation formation) {
        return Component.translatable(KEY + "formation." + formation.id());
    }

    public static Component squadName(ArmySquad squad) {
        return Component.translatable(KEY + (squad.mercenaries() ? "band_squad" : "squad"), squad.id());
    }

    public static MutableComponent mercenaryName(Mercenary fighter) {
        return CitizenTexts.name(fighter.nameSeed(), fighter.female() ? Gender.FEMALE : Gender.MALE);
    }

    public static Component bandName(MercenaryBand band) {
        return Component.translatable(KEY + "band", mercenaryName(band.captain()));
    }
}
