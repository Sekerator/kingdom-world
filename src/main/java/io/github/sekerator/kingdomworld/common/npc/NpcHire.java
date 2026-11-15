package io.github.sekerator.kingdomworld.common.npc;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmySettings;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.core.army.ArmyRules;
import io.github.sekerator.kingdomworld.core.army.ArmyState;
import io.github.sekerator.kingdomworld.core.army.MercenaryBand;
import io.github.sekerator.kingdomworld.core.npc.RulerMind;
import io.github.sekerator.kingdomworld.core.npc.RulerRules;
import io.github.sekerator.kingdomworld.core.npc.RulerView;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A rich ruler of the world turns money into swords: in danger or at war it hires a band of mercenaries of the offers of the
 * day, the most strength for its price, while its treasury spares the advance and a month of their pay; once the danger is
 * gone it lets them go.
 */
public final class NpcHire {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Bands a ruler keeps at most. */
    public static final int MAX_BANDS = 2;
    /** Days of the pay of a band a ruler wants to spare besides its advance before it hires it. */
    public static final int PAY_DAYS = 30;
    /** The danger from which a ruler hires swords in peace. */
    public static final int THREAT = 2;

    private NpcHire() {
    }

    /**
     * Once a turn: hires a band or lets the bands go.
     *
     * @return the band hired, if one was
     */
    public static Optional<MercenaryBand> daily(MinecraftServer server, UUID kingdomId, RulerView view, RulerRules rules) {
        if (ArmyWork.isReadOnly(server) || KingdomWorldApi.isReadOnly(server)) {
            return Optional.empty();
        }
        ArmyState army = ArmyWork.state(server);
        List<MercenaryBand> hired = army.hiredOf(kingdomId);
        boolean needed = view.threat >= THREAT || view.wars > 0;
        if (!needed) {
            for (MercenaryBand band : hired) {
                ArmyWork.change(server, "let the mercenaries of a ruler of the world go",
                        state -> state.release(null, band.id(), KingdomWorldApi.state(server)));
                LOGGER.info("The ruler of {} let band #{} of mercenaries go: the danger is past", NpcRulers.kingdomName(server, kingdomId), band.id());
            }
            return Optional.empty();
        }
        if (hired.size() >= MAX_BANDS || RulerMind.poor(view, rules)) {
            return Optional.empty();
        }
        long spare = RulerMind.spare(view, rules);
        ArmyRules armyRules = ArmySettings.rulesFor(server, kingdomId);
        Optional<MercenaryBand> best = army.offersOf(kingdomId).stream()
                .filter(band -> ArmyState.advance(band, armyRules) + band.dailyPrice() * PAY_DAYS <= spare)
                .max(Comparator.comparingDouble(band -> band.power() / Math.max(1, band.dailyPrice())));
        if (best.isEmpty()) {
            return Optional.empty();
        }
        MercenaryBand band = best.get();
        long day = ArmyWork.day(server);
        io.github.sekerator.kingdomworld.core.kingdom.Outcome outcome = ArmyWork.change(server, "hire mercenaries for a ruler of the world",
                state -> state.hire(null, kingdomId, band.id(), day, armyRules, KingdomWorldApi.state(server)).outcome());
        if (!outcome.ok()) {
            LOGGER.debug("The ruler of {} could not hire band #{}: {}", NpcRulers.kingdomName(server, kingdomId), band.id(), outcome.error());
            return Optional.empty();
        }
        LOGGER.info("The ruler of {} hired band #{} of {} mercenaries for {} a day", NpcRulers.kingdomName(server, kingdomId), band.id(),
                band.fighters().size(), band.dailyPrice());
        return Optional.of(band);
    }
}
