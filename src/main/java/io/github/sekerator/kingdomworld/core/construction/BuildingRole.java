package io.github.sekerator.kingdomworld.core.construction;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * What a kind of building does for its kingdom while it stands: a house gives homes, a warehouse makes the containers in it
 * storehouses, a workshop speeds up the posts in it, and so on. The role of a building comes from its template; it works
 * while the building is whole enough (see {@link BuildingRoles#status}).
 *
 * <p>A role has numbers (comfort, speed, places) that grow with the level of the building: a template may give its own,
 * otherwise every level adds {@code perLevel} to {@code defaults}.
 *
 * @param id          id of the role, as templates name it
 * @param requirement what a building of the role must hold, checked when players save their own blueprints
 * @param defaults    numbers of the role at the first level
 * @param perLevel    what every further level adds
 */
public record BuildingRole(String id, Requirement requirement, Map<String, Double> defaults, Map<String, Double> perLevel) {
    /** Longest role id. */
    public static final int MAX_ID = 64;
    /** Highest level a building may have. */
    public static final int MAX_LEVEL = 16;

    /**
     * What a building of the role must hold.
     */
    public enum Requirement {
        NONE,
        /** At least one bed. */
        BED,
        /** At least one container. */
        CONTAINER;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Requirement byId(String id) {
            for (Requirement requirement : values()) {
                if (requirement.id().equals(id)) {
                    return requirement;
                }
            }
            return NONE;
        }
    }

    public BuildingRole {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requirement, "requirement");
        if (id.isEmpty() || id.length() > MAX_ID) {
            throw new IllegalArgumentException("Bad role id '" + id + "'");
        }
        defaults = Collections.unmodifiableMap(new LinkedHashMap<>(defaults));
        perLevel = Collections.unmodifiableMap(new LinkedHashMap<>(perLevel));
    }

    public BuildingRole(String id, Requirement requirement, Map<String, Double> defaults) {
        this(id, requirement, defaults, Map.of());
    }

    /**
     * The numbers of the role for a building of a level: the template's own where it gives them, otherwise the defaults
     * grown by the level. Numbers that are not finite are left out.
     */
    public Map<String, Double> params(int level, Map<String, Double> overrides) {
        int steps = Math.max(0, Math.min(MAX_LEVEL, level) - 1);
        Map<String, Double> result = new LinkedHashMap<>();
        defaults.forEach((name, value) -> result.put(name, value + perLevel.getOrDefault(name, 0.0) * steps));
        overrides.forEach((name, value) -> {
            if (value != null && Double.isFinite(value)) {
                result.put(name, value);
            }
        });
        return result;
    }

    /**
     * A number of the role, or {@code fallback} when the role has no such number.
     */
    public static double param(Map<String, Double> params, String name, double fallback) {
        Double value = params.get(name);
        return value == null || !Double.isFinite(value) ? fallback : value;
    }
}
