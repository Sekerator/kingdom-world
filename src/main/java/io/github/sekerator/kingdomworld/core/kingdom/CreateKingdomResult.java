package io.github.sekerator.kingdomworld.core.kingdom;

import java.util.Locale;
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
        ID_TAKEN,
        /** The founder already belongs to a kingdom. */
        ALREADY_IN_KINGDOM,
        /** Kingdoms may not be founded in this dimension. */
        DIMENSION_NOT_ALLOWED,
        /** The throne stands on land of another kingdom. */
        INSIDE_OTHER_KINGDOM,
        /** Land of another kingdom is too close to the throne. */
        TOO_CLOSE_TO_OTHER_KINGDOM,
        /** The saved data is read-only. */
        READ_ONLY;

        public static Error of(NameProblem problem) {
            return switch (problem) {
                case EMPTY -> NAME_EMPTY;
                case TOO_SHORT -> NAME_TOO_SHORT;
                case TOO_LONG -> NAME_TOO_LONG;
                case INVALID_CHARACTERS -> NAME_INVALID_CHARACTERS;
            };
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
