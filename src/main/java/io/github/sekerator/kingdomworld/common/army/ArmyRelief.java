package io.github.sekerator.kingdomworld.common.army;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.law.LawAlarms;
import io.github.sekerator.kingdomworld.core.army.ArmyState;
import io.github.sekerator.kingdomworld.core.army.Branch;
import io.github.sekerator.kingdomworld.core.army.ForceRules;
import io.github.sekerator.kingdomworld.core.army.Post;
import io.github.sekerator.kingdomworld.core.army.Relief;
import io.github.sekerator.kingdomworld.core.army.Soldier;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.law.Alarm;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The help the army gives the guard. An alarm of a city ({@link LawAlarms}) is answered step by step ({@link Relief}): the
 * fighters of the guard at once, then its vice-captain, its captain, the royal army with its vice-captain, and at last the
 * captain of the army with everybody left. In peace the army stays where it stands; a soldier comes out only at its step
 * and goes back to its place when the alarm is over. The offender hears one line when the army comes out.
 *
 * <p>This is about alarms of the law only. In a war and a siege soldiers fight as they did before, officers with them.
 */
public final class ArmyRelief {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "kingdomworld.army.relief.";

    private record Key(UUID kingdom, UUID offender) {
    }

    /**
     * How the answer to one alarm stands.
     */
    private static final class Call {
        private Relief.Step step = Relief.Step.GUARDS;
        /** The officers the kingdom had when the alarm was raised: one who is gone since is out of the fight. */
        @Nullable
        private final UUID vice;
        @Nullable
        private final UUID captain;
        @Nullable
        private final UUID armyVice;
        /** The fighters of the army that come out with its vice-captain. */
        @Nullable
        private Set<UUID> wave;
        private boolean told;

        private Call(@Nullable UUID vice, @Nullable UUID captain, @Nullable UUID armyVice) {
            this.vice = vice;
            this.captain = captain;
            this.armyVice = armyVice;
        }
    }

    private static final Map<Key, Call> CALLS = new HashMap<>();
    /** Soldiers an alarm calls now, whose bodies come before those of other citizens. */
    private static final Set<UUID> CALLED = new HashSet<>();

    private ArmyRelief() {
    }

    static void reset() {
        CALLS.clear();
        CALLED.clear();
    }

    // ---------------------------------------------------------------- reading

    /**
     * How far the answer to the alarm of a kingdom for an offender has gone; the guards alone without such an alarm.
     */
    public static Relief.Step step(UUID kingdomId, UUID offender) {
        Call call = CALLS.get(new Key(kingdomId, offender));
        return call == null ? Relief.Step.GUARDS : call.step;
    }

    /**
     * Whether an alarm calls a soldier now: its body comes before those of other citizens while bodies are scarce.
     */
    public static boolean isCalled(UUID citizenId) {
        return CALLED.contains(citizenId);
    }

    /**
     * The soldiers an alarm calls now; a view that changes once a second.
     */
    public static Set<UUID> called() {
        return java.util.Collections.unmodifiableSet(CALLED);
    }

    /**
     * Whether a soldier answers an alarm of its kingdom at the step the alarm has come to.
     */
    public static boolean answers(MinecraftServer server, CitizenEntity soldier, Alarm alarm) {
        if (soldier.citizenId() == null || ArmyWork.isReadOnly(server)) {
            return true;
        }
        ArmyState army = ArmyWork.state(server);
        Optional<Soldier> record = army.soldier(soldier.citizenId());
        if (record.isEmpty()) {
            return true;
        }
        Call call = CALLS.get(new Key(alarm.kingdomId(), alarm.offender()));
        if (call == null) {
            // Raised this very second: nobody has taken stock yet.
            Relief.Step first = Relief.step(new Relief.Facts(army.branchOf(alarm.kingdomId(), Branch.GUARD).size(), 0, 0, false, false, false),
                    ArmySettings.force());
            return Relief.answers(first, record.get().branch(), record.get().post(), true);
        }
        return Relief.answers(call.step, record.get().branch(), record.get().post(), call.wave != null && call.wave.contains(soldier.citizenId()));
    }

    // ---------------------------------------------------------------- once a second

    static void tick(MinecraftServer server) {
        CALLED.clear();
        List<Alarm> alarms = LawAlarms.all(server);
        if (alarms.isEmpty()) {
            CALLS.clear();
            return;
        }
        Set<Key> lasting = new HashSet<>();
        ArmyState army = ArmyWork.state(server);
        KingdomWorldState world = KingdomWorldApi.state(server);
        ForceRules rules = ArmySettings.force();
        long now = KingdomWorldApi.gameTime(server);
        for (Alarm alarm : alarms) {
            Key key = new Key(alarm.kingdomId(), alarm.offender());
            lasting.add(key);
            UUID kingdom = alarm.kingdomId();
            Call call = CALLS.computeIfAbsent(key, k -> new Call(officer(army, kingdom, Branch.GUARD, Post.VICE_CAPTAIN),
                    officer(army, kingdom, Branch.GUARD, Post.CAPTAIN), officer(army, kingdom, Branch.ARMY, Post.VICE_CAPTAIN)));
            Relief.Facts facts = new Relief.Facts(army.branchOf(kingdom, Branch.GUARD).size(), alarm.guardsKilled() + alarm.guardsWounded(),
                    alarm.fightTicks(now), out(army, world, call.vice, rules), out(army, world, call.captain, rules),
                    out(army, world, call.armyVice, rules));
            Relief.Step step = call.step.max(Relief.step(facts, rules));
            if (step != call.step) {
                LOGGER.info("Alarm of {} for {}: {} ({} out, {} ticks of fight)", kingdom, alarm.name(), step, facts.losses(), facts.fightTicks());
                call.step = step;
            }
            if (step.reached(Relief.Step.ARMY)) {
                if (call.wave == null) {
                    call.wave = firstWave(army, world, alarm, rules);
                }
                if (!call.told && !army.branchOf(kingdom, Branch.ARMY).isEmpty()) {
                    call.told = true;
                    tell(server, alarm);
                }
            }
            for (Soldier soldier : army.soldiersOf(kingdom)) {
                if (!Relief.answers(step, soldier.branch(), soldier.post(), call.wave != null && call.wave.contains(soldier.citizenId()))) {
                    continue;
                }
                WorldPos at = place(world, soldier.citizenId());
                if (at != null && LawAlarms.reaches(server, alarm, at.dimension(), at.x(), at.z())) {
                    CALLED.add(soldier.citizenId());
                }
            }
        }
        CALLS.keySet().retainAll(lasting);
    }

    @Nullable
    private static UUID officer(ArmyState army, UUID kingdom, Branch branch, Post post) {
        return army.officer(kingdom, branch, post).map(Soldier::citizenId).orElse(null);
    }

    /**
     * Whether an officer is out of the fight: dead, or below the share of its health the rules name.
     */
    private static boolean out(ArmyState army, KingdomWorldState world, @Nullable UUID officer, ForceRules rules) {
        if (officer == null) {
            return false;
        }
        Optional<Citizen> citizen = world.citizens().get(officer);
        if (citizen.isEmpty() || army.soldier(officer).isEmpty()) {
            return true;
        }
        CitizenEntity body = CitizenBodies.body(officer);
        float health = body != null ? body.getHealth() : citizen.get().health();
        float full = body != null ? body.getMaxHealth() : citizen.get().maxHealth();
        return health < full * rules.officerOut;
    }

    @Nullable
    private static WorldPos place(KingdomWorldState world, UUID citizenId) {
        CitizenEntity body = CitizenBodies.body(citizenId);
        if (body != null) {
            return new WorldPos(io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(body.level()), body.getBlockX(), body.getBlockY(),
                    body.getBlockZ());
        }
        return world.citizens().get(citizenId).map(Citizen::lastPos).orElse(null);
    }

    /**
     * The fighters of the army that come out first: the nearest to the place of the alarm.
     */
    private static Set<UUID> firstWave(ArmyState army, KingdomWorldState world, Alarm alarm, ForceRules rules) {
        List<UUID> fighters = new ArrayList<>();
        Map<UUID, Double> distance = new HashMap<>();
        for (Soldier soldier : army.branchOf(alarm.kingdomId(), Branch.ARMY)) {
            if (soldier.post() != Post.FIGHTER) {
                continue;
            }
            WorldPos at = place(world, soldier.citizenId());
            double far = at == null || !at.dimension().equals(alarm.dimension()) ? Double.MAX_VALUE
                    : Math.pow(at.x() - alarm.x(), 2) + Math.pow(at.z() - alarm.z(), 2);
            fighters.add(soldier.citizenId());
            distance.put(soldier.citizenId(), far);
        }
        fighters.sort(Comparator.<UUID>comparingDouble(distance::get).thenComparing(Comparator.naturalOrder()));
        return new HashSet<>(fighters.subList(0, Math.min(fighters.size(), Relief.firstWave(fighters.size(), rules))));
    }

    /**
     * One line for the offender: the royal army comes out.
     */
    private static void tell(MinecraftServer server, Alarm alarm) {
        ServerPlayer offender = LawAlarms.offender(server, alarm);
        if (offender == null) {
            return;
        }
        Component kingdom = KingdomWorldApi.kingdom(server, alarm.kingdomId()).<Component>map(KingdomActions::kingdomName)
                .orElse(Component.literal("?"));
        offender.sendSystemMessage(Component.translatable(KEY + "army_comes", kingdom).withStyle(ChatFormatting.RED));
    }
}
