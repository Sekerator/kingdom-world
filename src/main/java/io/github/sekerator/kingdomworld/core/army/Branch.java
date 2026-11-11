package io.github.sekerator.kingdomworld.core.army;

import java.util.Locale;
import java.util.Optional;

/**
 * The branch a soldier serves in. The city guard keeps the peace of the cities and answers an alarm at once; the royal army
 * is stronger, stands in reserve in peace and comes out when the guard is hard pressed. Both march and fight in a war.
 */
public enum Branch {
    GUARD,
    ARMY;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Branch other() {
        return this == GUARD ? ARMY : GUARD;
    }

    public static Optional<Branch> byId(String id) {
        for (Branch branch : values()) {
            if (branch.id().equals(id)) {
                return Optional.of(branch);
            }
        }
        return Optional.empty();
    }

    /**
     * The branch of a soldier of data older than the branches: the guard if its squad holds a point, guards an area or
     * walks a patrol, the army otherwise.
     */
    public static Branch ofOrder(Order.Kind kind) {
        return kind == Order.Kind.HOLD || kind == Order.Kind.GUARD || kind == Order.Kind.PATROL ? GUARD : ARMY;
    }
}
