package io.github.sekerator.kingdomworld.common.npc;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.economy.TreasuryJournal;
import net.minecraft.server.MinecraftServer;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The money of a crown of the world as its ruler counts it: what goes out every day whatever it decides, and for how many days
 * its treasury pays that. Thresholds of spending are days of upkeep, never shares of the money a kingdom appeared with, so that
 * a rich crown and a poor one weigh a purchase alike.
 */
public final class RulerPurse {
    /** What goes out every day whatever the ruler decides: the wages of its people and the pay of hired swords. */
    public static final Set<LedgerCategory> UPKEEP = EnumSet.of(LedgerCategory.WAGES, LedgerCategory.ARMY, LedgerCategory.COMPANIONS);
    /** Days of upkeep a crown keeps before it equips new soldiers with bought kits. */
    public static final int KIT_DAYS = 30;
    /** Days of upkeep a crown keeps before its guilds may rise. */
    public static final int GUILD_DAYS = 60;

    private RulerPurse() {
    }

    /**
     * The upkeep of a kingdom on an average day of the last week (of the days it stood), at least one copper.
     */
    public static long upkeep(MinecraftServer server, UUID kingdomId) {
        Optional<Treasury> treasury = KingdomWorldApi.state(server).economy().treasury(kingdomId);
        if (treasury.isEmpty()) {
            return 1;
        }
        long day = TreasuryActions.day(server);
        long age = NpcWork.kingdom(server, kingdomId)
                .map(npc -> Math.max(1, Math.min(7, day - npc.founded() / io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds.TICKS_PER_DAY)))
                .orElse(7L);
        return Math.max(1, upkeep(treasury.get().journal().totals(day, TreasuryJournal.Period.WEEK)) / age);
    }

    /**
     * What came into the treasury of a kingdom on an average day of the last week, its founding money and the gifts of
     * operators left out.
     */
    public static long income(MinecraftServer server, UUID kingdomId) {
        Optional<Treasury> treasury = KingdomWorldApi.state(server).economy().treasury(kingdomId);
        if (treasury.isEmpty()) {
            return 0;
        }
        long day = TreasuryActions.day(server);
        long age = NpcWork.kingdom(server, kingdomId)
                .map(npc -> Math.max(1, Math.min(7, day - npc.founded() / io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds.TICKS_PER_DAY)))
                .orElse(7L);
        long sum = 0;
        for (Map.Entry<LedgerCategory, TreasuryJournal.Totals> entry : treasury.get().journal().totals(day, TreasuryJournal.Period.WEEK).entrySet()) {
            if (entry.getKey() != LedgerCategory.FOUNDING && entry.getKey() != LedgerCategory.ADMIN) {
                sum += entry.getValue().income();
            }
        }
        return sum / age;
    }

    /**
     * The upkeep in totals of a journal.
     */
    public static long upkeep(Map<LedgerCategory, TreasuryJournal.Totals> totals) {
        long sum = 0;
        for (Map.Entry<LedgerCategory, TreasuryJournal.Totals> entry : totals.entrySet()) {
            if (UPKEEP.contains(entry.getKey())) {
                sum += entry.getValue().expense();
            }
        }
        return sum;
    }

    /**
     * The money of a treasury, coins of its vault included.
     */
    public static long funds(MinecraftServer server, UUID kingdomId) {
        return KingdomWorldApi.state(server).economy().treasury(kingdomId).map(Treasury::funds).orElse(0L);
    }

    /**
     * For how many days of its upkeep the treasury of a kingdom pays.
     */
    public static long keepDays(MinecraftServer server, UUID kingdomId) {
        return funds(server, kingdomId) / upkeep(server, kingdomId);
    }
}
