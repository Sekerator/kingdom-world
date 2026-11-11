package io.github.sekerator.kingdomworld.core.army;

import java.util.Locale;
import java.util.Optional;

/**
 * The post of a soldier in its branch. A kingdom has at most one vice-captain and one captain of the guard, and one of each
 * of the army. An officer is stronger than a fighter only while it wears armor of its post.
 */
public enum Post {
    FIGHTER,
    VICE_CAPTAIN,
    CAPTAIN;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean officer() {
        return this != FIGHTER;
    }

    public static Optional<Post> byId(String id) {
        for (Post post : values()) {
            if (post.id().equals(id)) {
                return Optional.of(post);
            }
        }
        return Optional.empty();
    }
}
