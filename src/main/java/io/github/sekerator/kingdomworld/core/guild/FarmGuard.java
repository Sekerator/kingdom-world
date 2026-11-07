package io.github.sekerator.kingdomworld.core.guild;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Tells monster farms from hunting. Kills are remembered by place (squares of {@value #CELL_SIZE} blocks) for a while; the
 * more monsters died in one place lately, the smaller the chance of a crystal and the fewer the points. A monster from a
 * spawner gives less from the first kill on. Nothing is saved: after a restart every place starts afresh.
 *
 * <p>Not thread safe; the server uses it from the server thread only.
 */
public final class FarmGuard {
    /** Side of the square that counts as one place, in blocks. */
    public static final int CELL_SIZE = 32;
    private static final int CELL_SHIFT = 5;
    /** Places kept at most; the oldest are forgotten first. */
    private static final int MAX_CELLS = 8_192;

    private record Cell(String dimension, int x, int z) {
    }

    private final Map<Cell, ArrayDeque<Long>> kills = new HashMap<>();

    /**
     * The share of the chance and the points a kill gives, from 0 to 1.
     *
     * @param earlierKills monsters that died in the same place within the window before this one
     * @param spawner      whether the monster came from a spawner
     */
    public static double factor(int earlierKills, boolean spawner, GuildRules rules) {
        int free = spawner ? 0 : rules.farmFreeKills;
        int over = Math.max(0, earlierKills - free);
        double factor = Math.pow(0.5, over / (double) rules.farmHalfKills);
        return spawner ? factor * rules.spawnerFactor : factor;
    }

    /**
     * Records a kill and says what it is worth.
     *
     * @param now game time in ticks
     * @return the share of the chance and the points this kill gives
     */
    public double record(String dimension, int x, int z, long now, boolean spawner, GuildRules rules) {
        Cell cell = new Cell(dimension, x >> CELL_SHIFT, z >> CELL_SHIFT);
        ArrayDeque<Long> times = kills.computeIfAbsent(cell, c -> new ArrayDeque<>());
        prune(times, now, rules);
        int earlier = times.size();
        times.addLast(now);
        if (kills.size() > MAX_CELLS) {
            cleanup(now, rules);
        }
        return factor(earlier, spawner, rules);
    }

    /**
     * Kills remembered in the place of a block.
     */
    public int recent(String dimension, int x, int z, long now, GuildRules rules) {
        ArrayDeque<Long> times = kills.get(new Cell(dimension, x >> CELL_SHIFT, z >> CELL_SHIFT));
        if (times == null) {
            return 0;
        }
        prune(times, now, rules);
        return times.size();
    }

    /**
     * Forgets places without recent kills.
     */
    public void cleanup(long now, GuildRules rules) {
        Iterator<Map.Entry<Cell, ArrayDeque<Long>>> it = kills.entrySet().iterator();
        while (it.hasNext()) {
            ArrayDeque<Long> times = it.next().getValue();
            prune(times, now, rules);
            if (times.isEmpty()) {
                it.remove();
            }
        }
        if (kills.size() > MAX_CELLS) {
            kills.clear();
        }
    }

    public void clear() {
        kills.clear();
    }

    private static void prune(ArrayDeque<Long> times, long now, GuildRules rules) {
        while (!times.isEmpty() && (now - times.peekFirst() >= rules.farmWindowTicks || times.peekFirst() > now)) {
            times.pollFirst();
        }
    }
}
