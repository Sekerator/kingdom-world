package io.github.sekerator.kingdomworld.core.law;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The cities of the world with their gates, as the law needs them: which city a place belongs to and whether the city is
 * large enough to ask for entry. Cities and walls come with the roads; until then there are none and nobody is checked at
 * any gate. The server installs the real cities through the law's entry point.
 */
@FunctionalInterface
public interface CityGates {
    /** No cities: nobody passes a gate. */
    CityGates NONE = (dimension, x, y, z) -> Optional.empty();

    /**
     * A city of a kingdom.
     *
     * @param large whether it is a large city, which asks for entry
     */
    record City(UUID id, UUID kingdomId, String name, boolean large) {
        public City {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(kingdomId, "kingdomId");
            name = name == null ? "" : name;
        }
    }

    /**
     * The city whose walls enclose a place, if any.
     */
    Optional<City> cityAt(String dimension, int x, int y, int z);
}
