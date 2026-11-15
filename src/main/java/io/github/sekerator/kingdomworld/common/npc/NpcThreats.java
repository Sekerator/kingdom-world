package io.github.sekerator.kingdomworld.common.npc;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.law.LawWork;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.law.Wanted;
import io.github.sekerator.kingdomworld.core.npc.MilitaryMind;
import io.github.sekerator.kingdomworld.core.road.BanditCamp;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The dangers a kingdom of the world sees around it, for its ruler and its garrison: murderers, robbers, wars, its people
 * killed in its cities, and neighbours far stronger than itself.
 */
public final class NpcThreats {
    /** Days the killings of its people weigh on a ruler. */
    public static final int KILLED_DAYS = 3;
    /** Kingdoms further than this from the capital are no danger to it, in blocks. */
    private static final int NEIGHBOUR_DISTANCE = 1600;
    /** The days its people were killed in its cities, one entry a death, for each kingdom. */
    private static final Map<UUID, Deque<Long>> KILLED = new ConcurrentHashMap<>();
    /** The level of danger of each kingdom, counted once a day: {day, level}. */
    private static final Map<UUID, long[]> LEVELS = new ConcurrentHashMap<>();

    private NpcThreats() {
    }

    static void reset() {
        KILLED.clear();
        LEVELS.clear();
    }

    /**
     * Somebody of a kingdom was killed by an enemy in its land.
     */
    public static void killed(MinecraftServer server, UUID kingdomId) {
        long day = TreasuryActions.day(server);
        Deque<Long> days = KILLED.computeIfAbsent(kingdomId, k -> new ArrayDeque<>());
        synchronized (days) {
            days.addLast(day);
            while (days.size() > 256) {
                days.removeFirst();
            }
        }
        LEVELS.remove(kingdomId);
    }

    /**
     * Its people killed in the last days.
     */
    public static int killedLately(MinecraftServer server, UUID kingdomId) {
        Deque<Long> days = KILLED.get(kingdomId);
        if (days == null) {
            return 0;
        }
        long today = TreasuryActions.day(server);
        synchronized (days) {
            days.removeIf(day -> today - day >= KILLED_DAYS);
            return days.size();
        }
    }

    /**
     * How endangered a kingdom is today, from 0 to {@link MilitaryMind#MAX_THREAT}.
     */
    public static int level(MinecraftServer server, UUID kingdomId) {
        long day = TreasuryActions.day(server);
        long[] known = LEVELS.get(kingdomId);
        if (known != null && known[0] == day) {
            return (int) known[1];
        }
        int murderers = 0;
        if (!LawWork.isReadOnly(server)) {
            for (Wanted wanted : LawWork.state(server).wantedIn(kingdomId)) {
                murderers += wanted.murder() ? 1 : 0;
            }
        }
        Optional<Capital> capital = KingdomWorldApi.state(server).territory().territory(kingdomId).map(Territory::capital);
        int camps = 0;
        double strongest = 0;
        if (capital.isPresent()) {
            for (BanditCamp camp : RoadWork.state(server).camps()) {
                if (!camp.cleared() && Math.hypot(camp.x() - capital.get().x(), camp.z() - capital.get().z())
                        <= io.github.sekerator.kingdomworld.core.npc.RulerMind.THREAT_DISTANCE) {
                    camps++;
                }
            }
            double own = io.github.sekerator.kingdomworld.common.diplomacy.WarWork.countedPower(kingdomId);
            for (Territory other : KingdomWorldApi.state(server).territory().territories()) {
                Capital at = other.capital();
                if (other.kingdomId().equals(kingdomId) || !at.dimension().equals(capital.get().dimension())
                        || Math.hypot(at.x() - capital.get().x(), at.z() - capital.get().z()) > NEIGHBOUR_DISTANCE) {
                    continue;
                }
                double theirs = io.github.sekerator.kingdomworld.common.diplomacy.WarWork.countedPower(other.kingdomId());
                if (own > 0 && theirs > 0) {
                    strongest = Math.max(strongest, theirs / own);
                }
            }
        }
        int wars = io.github.sekerator.kingdomworld.common.diplomacy.WarWork.isReadOnly(server) ? 0
                : io.github.sekerator.kingdomworld.common.diplomacy.WarWork.state(server).warsOf(kingdomId).size();
        int level = MilitaryMind.threat(murderers, camps, wars, killedLately(server, kingdomId), strongest);
        LEVELS.put(kingdomId, new long[]{day, level});
        return level;
    }
}
