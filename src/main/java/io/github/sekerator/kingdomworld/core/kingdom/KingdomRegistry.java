package io.github.sekerator.kingdomworld.core.kingdom;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * All kingdoms of one world, indexed by id and by case-insensitive name.
 *
 * <p>Iteration order is insertion order. Every successful mutation notifies the change listener, which the
 * persistence layer uses to mark data for saving. Not thread safe.
 */
public final class KingdomRegistry {
    private final Map<UUID, Kingdom> byId = new LinkedHashMap<>();
    private final Map<String, UUID> idsByNameKey = new HashMap<>();
    private Runnable changeListener = () -> {
    };

    public enum AddResult {
        ADDED,
        DUPLICATE_ID,
        DUPLICATE_NAME
    }

    public void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
    }

    /**
     * Founds a new kingdom after validating the name and uniqueness.
     *
     * @param id              identifier for the new kingdom
     * @param rawName         name as entered; it is normalized before validation
     * @param color           map color as {@code 0xRRGGBB}
     * @param rulerId         ruler, or {@code null}
     * @param createdGameTime current game time in ticks
     */
    public CreateKingdomResult create(UUID id, String rawName, int color, UUID rulerId, long createdGameTime) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(rawName, "rawName");
        String name = KingdomNames.normalize(rawName);
        Optional<NameProblem> problem = KingdomNames.validate(name);
        if (problem.isPresent()) {
            return new CreateKingdomResult.Rejected(CreateKingdomResult.Error.of(problem.get()));
        }
        if (byId.containsKey(id)) {
            return new CreateKingdomResult.Rejected(CreateKingdomResult.Error.ID_TAKEN);
        }
        if (isNameTaken(name)) {
            return new CreateKingdomResult.Rejected(CreateKingdomResult.Error.NAME_TAKEN);
        }
        Kingdom kingdom = new Kingdom(id, name, color, rulerId, createdGameTime);
        put(kingdom);
        changeListener.run();
        return new CreateKingdomResult.Created(kingdom);
    }

    /**
     * Adds an already constructed kingdom, for example one read from saved data.
     */
    public AddResult add(Kingdom kingdom) {
        Objects.requireNonNull(kingdom, "kingdom");
        if (byId.containsKey(kingdom.id())) {
            return AddResult.DUPLICATE_ID;
        }
        if (isNameTaken(kingdom.name())) {
            return AddResult.DUPLICATE_NAME;
        }
        put(kingdom);
        changeListener.run();
        return AddResult.ADDED;
    }

    /**
     * Replaces a kingdom with a changed version of itself, for example after a change of ruler or color.
     * A changed name must still be unique.
     *
     * @return {@code false} if no kingdom has the id of {@code updated} or its new name is taken
     */
    public boolean update(Kingdom updated) {
        Objects.requireNonNull(updated, "updated");
        Kingdom current = byId.get(updated.id());
        if (current == null) {
            return false;
        }
        if (current.equals(updated)) {
            return true;
        }
        String oldKey = KingdomNames.key(current.name());
        String newKey = KingdomNames.key(updated.name());
        if (!oldKey.equals(newKey) && idsByNameKey.containsKey(newKey)) {
            return false;
        }
        idsByNameKey.remove(oldKey);
        put(updated);
        changeListener.run();
        return true;
    }

    /**
     * Whether {@link #add(Kingdom)} would accept the kingdom.
     */
    public AddResult canAdd(Kingdom kingdom) {
        if (byId.containsKey(kingdom.id())) {
            return AddResult.DUPLICATE_ID;
        }
        return isNameTaken(kingdom.name()) ? AddResult.DUPLICATE_NAME : AddResult.ADDED;
    }

    public Optional<Kingdom> remove(UUID id) {
        Kingdom removed = byId.remove(id);
        if (removed == null) {
            return Optional.empty();
        }
        idsByNameKey.remove(KingdomNames.key(removed.name()));
        changeListener.run();
        return Optional.of(removed);
    }

    public Optional<Kingdom> get(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<Kingdom> findByName(String name) {
        UUID id = idsByNameKey.get(KingdomNames.key(name));
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    public boolean isNameTaken(String name) {
        return idsByNameKey.containsKey(KingdomNames.key(name));
    }

    /**
     * Snapshot of all kingdoms in insertion order.
     */
    public List<Kingdom> all() {
        return List.copyOf(byId.values());
    }

    public int size() {
        return byId.size();
    }

    public boolean isEmpty() {
        return byId.isEmpty();
    }

    private void put(Kingdom kingdom) {
        byId.put(kingdom.id(), kingdom);
        idsByNameKey.put(KingdomNames.key(kingdom.name()), kingdom.id());
    }
}
