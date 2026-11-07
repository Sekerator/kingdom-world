package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.core.citizen.Profession;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Behaviour of each profession in the world, by profession id. Registration happens at startup; lookups are
 * lock free because chunk scans ask for every block.
 */
public final class ProfessionBehaviors {
    private static volatile Map<String, ProfessionBehavior> byId = Map.of();

    static {
        register(Professions.FARMER, new FarmerBehavior());
        register(Professions.LUMBERJACK, new LumberjackBehavior());
        register(Professions.MINER, new MinerBehavior());
        register(Professions.FISHER, new FisherBehavior());
        register(Professions.HERDER, new HerderBehavior());
        register(Professions.CARRIER, new CarrierBehavior());
    }

    private ProfessionBehaviors() {
    }

    /**
     * Registers a profession together with what it does in the world. The core profession is registered too if it
     * is not known yet.
     */
    public static synchronized void register(Profession profession, ProfessionBehavior behavior) {
        Objects.requireNonNull(behavior, "behavior");
        if (byId.containsKey(profession.id())) {
            throw new IllegalArgumentException("Behaviour of profession '" + profession.id() + "' is registered already");
        }
        if (!Professions.REGISTRY.isKnown(profession.id())) {
            Professions.REGISTRY.register(profession);
        }
        Map<String, ProfessionBehavior> next = new LinkedHashMap<>(byId);
        next.put(profession.id(), behavior);
        byId = Collections.unmodifiableMap(next);
    }

    public static Optional<ProfessionBehavior> get(String professionId) {
        return Optional.ofNullable(byId.get(professionId));
    }

    /**
     * The kind of workplace the block is for, or {@code null}.
     */
    public static String workplaceKind(BlockState state) {
        for (Map.Entry<String, ProfessionBehavior> entry : byId.entrySet()) {
            if (entry.getValue().isWorkplace(state)) {
                return Professions.REGISTRY.byId(entry.getKey()).map(Profession::workplaceKind).orElse(null);
            }
        }
        return null;
    }

    /**
     * Whether the block may be a workplace of any profession; a quick test for scanning chunk sections.
     */
    public static boolean isAnyWorkplace(BlockState state) {
        for (ProfessionBehavior behavior : byId.values()) {
            if (behavior.isWorkplace(state)) {
                return true;
            }
        }
        return false;
    }
}
