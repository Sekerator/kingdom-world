package io.github.sekerator.kingdomworld.core.kingdom;

import java.util.Objects;

/**
 * Outcome of an attempt to found a kingdom.
 */
public sealed interface CreateKingdomResult {

    record Created(Kingdom kingdom) implements CreateKingdomResult {
        public Created {
            Objects.requireNonNull(kingdom, "kingdom");
        }
    }

    record Rejected(Error error) implements CreateKingdomResult {
        public Rejected {
            Objects.requireNonNull(error, "error");
        }
    }

    enum Error {
        NAME_EMPTY,
        NAME_TOO_SHORT,
        NAME_TOO_LONG,
        NAME_INVALID_CHARACTERS,
        NAME_TAKEN,
        ID_TAKEN;

        public static Error of(NameProblem problem) {
            return switch (problem) {
                case EMPTY -> NAME_EMPTY;
                case TOO_SHORT -> NAME_TOO_SHORT;
                case TOO_LONG -> NAME_TOO_LONG;
                case INVALID_CHARACTERS -> NAME_INVALID_CHARACTERS;
            };
        }
    }
}
