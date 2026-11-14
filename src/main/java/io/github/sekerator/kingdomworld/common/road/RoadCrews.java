package io.github.sekerator.kingdomworld.common.road;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.work.Equipment;
import io.github.sekerator.kingdomworld.common.citizen.work.MinerBehavior;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.economy.EconomySettings;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.road.EarthRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Miners help builders with the rock of the roads of their kingdom: they stay at their posts, and only a share of their time
 * goes to the roads, so that the mines bring in less for it. A miner whose post has met its goal gives all its time,
 * {@code roadMinerShare} otherwise; the king turns the help off in the roads tab. The share the roads really took, smoothed over
 * a minute, slows the mines down; every block of rock the miners pay for wears the pickaxe of one of them.
 */
public final class RoadCrews {
    /** Seconds over which the share taken is smoothed. */
    private static final double SMOOTH = 60.0;

    /**
     * A miner helping this second.
     *
     * @param share the share of its time it gives to the roads
     */
    private record Helper(UUID citizenId, double speed, double share) {
    }

    private static final Map<UUID, List<Helper>> HELPERS = new HashMap<>();
    /** The share of what the miners gave that the roads took, smoothed, by kingdom. */
    private static final Map<UUID, Double> USED = new HashMap<>();
    /** The share each miner gives, by citizen, as last counted. */
    private static final Map<UUID, Double> SHARES = new HashMap<>();
    /** The kingdom of each miner counted. */
    private static final Map<UUID, UUID> KINGDOMS = new HashMap<>();
    /** Seconds of rock paid by miners not yet worn into a pickaxe, by kingdom. */
    private static final Map<UUID, Double> WEAR = new HashMap<>();
    /** Which helper wears next, by kingdom. */
    private static final Map<UUID, Integer> NEXT = new HashMap<>();

    private RoadCrews() {
    }

    static void reset() {
        HELPERS.clear();
        USED.clear();
        SHARES.clear();
        KINGDOMS.clear();
        WEAR.clear();
        NEXT.clear();
    }

    /**
     * Seconds of miners a kingdom gives to the rock of its roads this second: the speed of every miner alive, unhurt, at a post on
     * its own land and with a pickaxe, times the share it gives.
     */
    static double offer(MinecraftServer server, UUID kingdomId, EarthRules earth) {
        List<Helper> helpers = new ArrayList<>();
        if (RoadWork.state(server).minersHelp(kingdomId)) {
            for (Citizen citizen : KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdomId)) {
                if (!citizen.isAlive() || citizen.state() == CitizenState.WOUNDED || !Professions.MINER.id().equals(citizen.professionId())) {
                    continue;
                }
                Optional<WorkPost> post = PostWork.postOf(server, citizen);
                if (post.isEmpty() || !PostWork.onOwnLand(server, post.get())) {
                    continue;
                }
                if (!Equipment.equipTool(server, kingdomId, hands(server, citizen.id()), EquipmentSlot.MAINHAND, Equipment.ToolNeed.PICKAXE, 0,
                        post.get().pos())) {
                    continue;
                }
                ServerLevel level = ConstructionWork.level(server, post.get().pos().dimension());
                // The storehouses are counted once a second for every post, not for every column.
                boolean met = level != null && post.get().mineGoal().metBy(MinerBehavior.stock(level, post.get()));
                double share = met ? 1.0 : earth.roadMinerShare();
                if (share <= 0) {
                    continue;
                }
                helpers.add(new Helper(citizen.id(), EconomySettings.workSpeed(citizen.mood()), share));
            }
        }
        forget(kingdomId);
        HELPERS.put(kingdomId, helpers);
        double sum = 0;
        for (Helper helper : helpers) {
            SHARES.put(helper.citizenId(), helper.share());
            KINGDOMS.put(helper.citizenId(), kingdomId);
            sum += helper.speed() * helper.share();
        }
        return sum;
    }

    /**
     * Forgets the shares of the miners of a kingdom.
     */
    private static void forget(UUID kingdomId) {
        KINGDOMS.entrySet().removeIf(entry -> {
            if (entry.getValue().equals(kingdomId)) {
                SHARES.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Nothing of the rock of the roads of a kingdom was paid for this second: its miners gave nothing.
     */
    static void idle(UUID kingdomId) {
        HELPERS.remove(kingdomId);
        settle(kingdomId, 0, 0);
        // While the share the roads took fades, the mines still go slower by it.
        if (used(kingdomId) <= 0) {
            forget(kingdomId);
        }
    }

    /**
     * The kingdoms whose roads were not paid for this second: the share their roads took fades.
     */
    static void idleExcept(java.util.Set<UUID> paying) {
        for (UUID kingdom : new ArrayList<>(USED.keySet())) {
            if (!paying.contains(kingdom)) {
                idle(kingdom);
            }
        }
    }

    /**
     * The second is over: of what the miners offered, so much was spent.
     */
    static void settle(UUID kingdomId, double offered, double spent) {
        double taken = offered <= 0 ? 0 : Math.min(1.0, spent / offered);
        double before = USED.getOrDefault(kingdomId, 0.0);
        double after = before + (taken - before) / SMOOTH;
        if (after < 1e-4) {
            USED.remove(kingdomId);
        } else {
            USED.put(kingdomId, after);
        }
    }

    /**
     * The share of what the miners of a kingdom gave that the roads took, smoothed over a minute.
     */
    public static double used(UUID kingdomId) {
        return USED.getOrDefault(kingdomId, 0.0);
    }

    /**
     * Miners of a kingdom helping with its roads now.
     */
    public static int helpers(UUID kingdomId) {
        return HELPERS.getOrDefault(kingdomId, List.of()).size();
    }

    /**
     * Seconds of miners a kingdom gives a second now.
     */
    public static double speed(UUID kingdomId) {
        double sum = 0;
        for (Helper helper : HELPERS.getOrDefault(kingdomId, List.of())) {
            sum += helper.speed() * helper.share();
        }
        return sum;
    }

    /**
     * How much of its work a miner still does at its post: 1 less the share it gives to the roads times the share they took.
     */
    public static double mineFactor(Citizen citizen) {
        return mineFactor(citizen.id());
    }

    /**
     * The same for a miner by its id.
     */
    public static double mineFactor(UUID citizenId) {
        Double share = SHARES.get(citizenId);
        UUID kingdom = KINGDOMS.get(citizenId);
        if (share == null || kingdom == null) {
            return 1.0;
        }
        return Math.max(0.0, 1.0 - share * used(kingdom));
    }

    /**
     * Seconds of rock paid by the miners: a block of it wears the pickaxe of one of them by one use.
     */
    static void wear(MinecraftServer server, UUID kingdomId, double seconds, EarthRules earth) {
        List<Helper> helpers = HELPERS.getOrDefault(kingdomId, List.of());
        if (helpers.isEmpty() || seconds <= 0) {
            return;
        }
        double carry = WEAR.getOrDefault(kingdomId, 0.0) + seconds;
        int blocks = (int) Math.floor(carry / earth.digRockSeconds());
        WEAR.put(kingdomId, carry - blocks * earth.digRockSeconds());
        int next = NEXT.getOrDefault(kingdomId, 0);
        for (int i = 0; i < blocks; i++) {
            Helper helper = helpers.get(next % helpers.size());
            next++;
            Equipment.wear(hands(server, helper.citizenId()), EquipmentSlot.MAINHAND, 1, server.overworld().getRandom());
        }
        NEXT.put(kingdomId, next % helpers.size());
    }

    /**
     * The hands of a miner: of its body where it has one, else the gear it keeps.
     */
    private static Equipment.Hands hands(MinecraftServer server, UUID citizenId) {
        CitizenEntity body = CitizenBodies.body(citizenId);
        return body != null ? Equipment.of(body) : Equipment.of(server, citizenId);
    }

    /**
     * Gives experience to the miners who helped, a share of the blocks paid for.
     */
    static void experience(UUID kingdomId, MinecraftServer server, int share) {
        if (share <= 0) {
            return;
        }
        for (Helper helper : HELPERS.getOrDefault(kingdomId, List.of())) {
            KingdomWorldApi.state(server).citizens().registry().get(helper.citizenId()).ifPresent(citizen -> citizen.addExperience(share));
        }
    }
}
