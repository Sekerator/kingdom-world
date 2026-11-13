package io.github.sekerator.kingdomworld.common.npc;

import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.core.land.Tier;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
import io.github.sekerator.kingdomworld.core.npc.Settlement;
import io.github.sekerator.kingdomworld.core.npc.SettlementLife;
import net.minecraft.server.MinecraftServer;

/**
 * The days of the settlements that are data: once a game day each lives its day in numbers, {@link #PER_TICK} of them a tick.
 * Only their own numbers change: no treasury, store or item is touched.
 */
public final class SettlementWork {
    /** Settlements living their day in a tick at most. */
    static final int PER_TICK = 16;
    /** Days a settlement catches up at most when the game day jumped. */
    static final int MOST_DAYS = 30;

    private static long day = Long.MIN_VALUE;
    private static int[] queue = new int[0];
    private static int at;

    private SettlementWork() {
    }

    static void tick(MinecraftServer server) {
        NpcState state = NpcWork.state(server);
        if (at >= queue.length) {
            long today = TreasuryActions.day(server);
            if (today == day || state.settlements().isEmpty()) {
                return;
            }
            day = today;
            queue = state.settlements().stream().mapToInt(Settlement::id).toArray();
            at = 0;
        }
        int done = 0;
        while (at < queue.length && done < PER_TICK) {
            Settlement settlement = state.settlement(queue[at++]).orElse(null);
            if (settlement != null && live(settlement, day)) {
                done++;
            }
        }
    }

    /**
     * Lives the days of a settlement up to a day; false for one that does not live as data.
     */
    public static boolean live(Settlement settlement, long today) {
        if (settlement.tier() == Tier.CAPITAL
                || (settlement.status() != Settlement.Status.DATA && settlement.status() != Settlement.Status.BLOCKED)) {
            return false;
        }
        long last = settlement.lastDay();
        if (last >= today) {
            return false;
        }
        if (last < 0) {
            // Its first day is the day it is first seen: it starts to live from then.
            settlement.live(new SettlementLife.Day(settlement.population(), settlement.food(), settlement.guards(), settlement.militia(),
                    settlement.prosperity()), today);
            return true;
        }
        long days = Math.min(MOST_DAYS, today - last);
        SettlementLife.Day lived = null;
        int population = settlement.population();
        int food = settlement.food();
        int prosperity = settlement.prosperity();
        for (long i = 0; i < days; i++) {
            lived = SettlementLife.day(settlement.tier(), population, settlement.housing(), food, prosperity, settlement.fertility());
            population = lived.population();
            food = lived.food();
            prosperity = lived.prosperity();
        }
        settlement.live(lived, today);
        return true;
    }

    static void reset() {
        day = Long.MIN_VALUE;
        queue = new int[0];
        at = 0;
    }
}
