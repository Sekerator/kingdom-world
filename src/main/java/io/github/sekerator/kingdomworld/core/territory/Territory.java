package io.github.sekerator.kingdomworld.core.territory;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The land of one kingdom: its capital and the chunks it owns, all in the capital's dimension. Read-only view;
 * changes go through {@link TerritoryIndex}.
 */
public final class Territory {
    private final UUID kingdomId;
    private final Capital capital;
    final Set<Long> chunks = new LinkedHashSet<>();

    Territory(UUID kingdomId, Capital capital) {
        this.kingdomId = Objects.requireNonNull(kingdomId, "kingdomId");
        this.capital = Objects.requireNonNull(capital, "capital");
    }

    public UUID kingdomId() {
        return kingdomId;
    }

    public Capital capital() {
        return capital;
    }

    public String dimension() {
        return capital.dimension();
    }

    public int size() {
        return chunks.size();
    }

    public boolean contains(long chunk) {
        return chunks.contains(chunk);
    }

    /**
     * Owned chunks as {@link ChunkKey} values, in the order they were claimed.
     */
    public Set<Long> chunks() {
        return Collections.unmodifiableSet(chunks);
    }
}
