package io.github.sekerator.kingdomworld.core.magic;

import io.github.sekerator.kingdomworld.core.citizen.WorldPos;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A block of magic standing on the land of a kingdom: a magic focus, a crystal accumulator (more room for energy), a crystal
 * generator (Forge Energy for machines) or a gate (travel between the gates of the kingdom).
 *
 * @param id the number of the device, shown for gates and generators
 */
public record MagicDevice(long id, Kind kind, UUID kingdomId, WorldPos pos) {
    public enum Kind {
        /** A magic focus, the heart of a mage tower; its well serves the magic of other mods. */
        FOCUS,
        ACCUMULATOR,
        GENERATOR,
        GATE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Kind> byId(String id) {
            for (Kind kind : values()) {
                if (kind.id().equals(id)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }

    public MagicDevice {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(kingdomId, "kingdomId");
        Objects.requireNonNull(pos, "pos");
        if (id < 1) {
            throw new IllegalArgumentException("A device needs a positive number: " + id);
        }
    }
}
