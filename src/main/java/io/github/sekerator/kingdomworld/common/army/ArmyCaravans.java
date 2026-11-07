package io.github.sekerator.kingdomworld.common.army;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.trade.TradeWork;
import io.github.sekerator.kingdomworld.core.army.ArmyRules;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.combat.CaravanRisk;
import io.github.sekerator.kingdomworld.core.combat.Side;
import io.github.sekerator.kingdomworld.core.trade.Caravan;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * Guards of caravans. A squad ordered to escort a deal goes with every caravan of it (abstractly, without bodies) and comes
 * back when the caravan arrives. On the road something may lie in wait ({@code caravanLossChance}): an unguarded caravan is
 * lost to it as before, a guarded one only if its guards lose the fight, whose wounded and fallen count like in any battle.
 *
 * <p>The threat on the road is abstract for now; the robbers of a later stage replace {@link #threat} with a side of their own.
 */
public final class ArmyCaravans {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "kingdomworld.army.";

    private ArmyCaravans() {
    }

    /**
     * What lies in wait for a caravan.
     */
    public static Side threat(ArmyRules rules) {
        return CaravanRisk.roadThreat(rules.caravanThreat(), rules.caravanThreatUnits());
    }

    /**
     * The guards of a caravan: every squad that escorts its deal.
     */
    public static List<ArmySquad> guards(MinecraftServer server, Caravan caravan) {
        if (ArmyWork.isReadOnly(server)) {
            return List.of();
        }
        List<ArmySquad> result = new ArrayList<>();
        for (ArmySquad squad : ArmyWork.state(server).escortsOf(caravan.dealId())) {
            if (squad.kingdomId().equals(caravan.seller()) || squad.kingdomId().equals(caravan.buyer())) {
                result.add(squad);
            }
        }
        return result;
    }

    /**
     * The chance a caravan is lost with the guards it has now.
     */
    public static double lossChance(MinecraftServer server, Caravan caravan, double ambushChance) {
        double guards = 0.0;
        for (ArmySquad squad : guards(server, caravan)) {
            guards += ArmyWork.power(server, squad);
        }
        return CaravanRisk.lossChance(ambushChance, guards, threat(ArmySettings.rules()).power());
    }

    /**
     * Decides on the arrival of a caravan whether it was lost on the way. Without guards one random number decides, exactly
     * as before there were guards; with them, the guards fight what lies in wait.
     *
     * @param roll the dice of trade, which game tests replace
     */
    public static boolean lost(MinecraftServer server, Caravan caravan, double ambushChance, DoubleSupplier roll) {
        List<ArmySquad> guards = guards(server, caravan);
        if (guards.isEmpty()) {
            return roll.getAsDouble() < ambushChance;
        }
        ArmyRules rules = ArmySettings.rules();
        List<Side> sides = new ArrayList<>();
        Side all = Side.of(Side.Kind.SOLDIERS, List.of());
        for (ArmySquad squad : guards) {
            Side side = ArmyWork.side(server, squad);
            sides.add(side);
            all = all.with(side);
        }
        CaravanRisk.Journey journey = CaravanRisk.journey(ambushChance, all, threat(rules), rules.battle(), roll);
        if (!journey.ambushed()) {
            return false;
        }
        WorldPos where = new WorldPos(caravan.dimension(), (caravan.fromX() + caravan.toX()) / 2, 64, (caravan.fromZ() + caravan.toZ()) / 2);
        Component what = Component.translatable(KEY + "ambush", caravan.dealId());
        for (int i = 0; i < guards.size(); i++) {
            ArmyWork.Fought fought = ArmyWork.apply(server, guards.get(i), sides.get(i), journey.battle(), where, rules);
            ArmyWork.announce(server, guards.get(i), fought, what);
        }
        LOGGER.info("A caravan of deal #{} was ambushed on the way: its guards {} (chance {})", caravan.dealId(),
                journey.lost() ? "lost" : "won", String.format(java.util.Locale.ROOT, "%.2f", journey.battle().chance()));
        return journey.lost();
    }

    /**
     * Rolls of trade, for game tests that fight with the guards of caravans.
     */
    public static void overrideRolls(DoubleSupplier value) {
        TradeWork.overrideRoll(value);
    }
}
