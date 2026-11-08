package io.github.sekerator.kingdomworld.core.diplomacy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * When the players of each kingdom were last there to defend it. A kingdom of players with nobody online at the keyboard is
 * shielded while away: no battles on its land, no robbed caravans, no score against it, no exhaustion for it. The shield
 * comes only some minutes after the last player left, and not while there was a fight near its players a moment ago, so that
 * leaving is no escape from a battle.
 */
public final class Presence {
    /** Never seen: as long ago as can be. */
    private static final long NEVER = Long.MIN_VALUE / 4;

    private final Map<UUID, long[]> seen = new LinkedHashMap<>();

    /**
     * A player of the kingdom is online and at the keyboard now.
     */
    public void present(UUID kingdom, long now) {
        seen.computeIfAbsent(kingdom, k -> new long[]{NEVER, NEVER})[0] = now;
    }

    /**
     * Players of the kingdom fought an enemy near them now.
     */
    public void fought(UUID kingdom, long now) {
        seen.computeIfAbsent(kingdom, k -> new long[]{NEVER, NEVER})[1] = now;
    }

    public long lastPresent(UUID kingdom) {
        long[] times = seen.get(kingdom);
        return times == null ? NEVER : times[0];
    }

    public long lastFight(UUID kingdom) {
        long[] times = seen.get(kingdom);
        return times == null ? NEVER : times[1];
    }

    /**
     * Whether a kingdom of players is shielded now.
     *
     * @param presentNow whether one of its players is online and at the keyboard now
     */
    public boolean shielded(UUID kingdom, long now, boolean presentNow, DiplomacyRules rules) {
        if (presentNow) {
            return false;
        }
        return now - lastPresent(kingdom) >= rules.absenceTicks() && now - lastFight(kingdom) >= rules.fightGraceTicks();
    }

    /**
     * When the shield of an absent kingdom comes, or {@code now} if it is there already.
     */
    public long shieldFrom(UUID kingdom, long now, DiplomacyRules rules) {
        return Math.max(now, Math.max(lastPresent(kingdom) + rules.absenceTicks(), lastFight(kingdom) + rules.fightGraceTicks()));
    }

    public Map<UUID, long[]> all() {
        return Collections.unmodifiableMap(seen);
    }

    public void restore(UUID kingdom, long present, long fight) {
        seen.put(kingdom, new long[]{present, fight});
    }

    void forget(UUID kingdom) {
        seen.remove(kingdom);
    }

    void clear() {
        seen.clear();
    }
}
