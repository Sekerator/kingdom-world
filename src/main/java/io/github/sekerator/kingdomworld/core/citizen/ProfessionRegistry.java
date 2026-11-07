package io.github.sekerator.kingdomworld.core.citizen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Known professions by id, in registration order (the order the throne screen cycles through). New professions
 * are added with {@link #register(Profession)} without touching the rest of the citizen code.
 *
 * <p>Citizens keep the id of their profession even if it is not registered (for example after an addon was
 * removed); such a citizen simply does not work until it gets a known profession.
 */
public final class ProfessionRegistry {
    private final Map<String, Profession> byId = new LinkedHashMap<>();

    /**
     * A registry with the professions of the base game: unemployed, farmer, the gathering professions
     * (lumberjack, miner, fisher, herder) with the carrier, and the workshops (smelter, cook, crafter, smith).
     */
    public static ProfessionRegistry withDefaults() {
        ProfessionRegistry registry = new ProfessionRegistry();
        registry.register(Professions.UNEMPLOYED);
        registry.register(Professions.FARMER);
        registry.register(Professions.LUMBERJACK);
        registry.register(Professions.MINER);
        registry.register(Professions.FISHER);
        registry.register(Professions.HERDER);
        registry.register(Professions.CARRIER);
        registry.register(Professions.SMELTER);
        registry.register(Professions.COOK);
        registry.register(Professions.CRAFTER);
        registry.register(Professions.SMITH);
        registry.register(Professions.MINTER);
        return registry;
    }

    /**
     * @throws IllegalArgumentException if a profession with the same id is registered already
     */
    public synchronized void register(Profession profession) {
        Objects.requireNonNull(profession, "profession");
        if (byId.containsKey(profession.id())) {
            throw new IllegalArgumentException("Profession '" + profession.id() + "' is registered already");
        }
        byId.put(profession.id(), profession);
    }

    public synchronized Optional<Profession> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public synchronized boolean isKnown(String id) {
        return byId.containsKey(id);
    }

    public synchronized List<Profession> all() {
        return List.copyOf(byId.values());
    }

    /**
     * The profession after {@code id} in registration order, wrapping around; the first one for an unknown id.
     */
    public synchronized Profession next(String id) {
        List<Profession> list = new ArrayList<>(byId.values());
        if (list.isEmpty()) {
            throw new IllegalStateException("No professions registered");
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(id)) {
                return list.get((i + 1) % list.size());
            }
        }
        return list.get(0);
    }
}
