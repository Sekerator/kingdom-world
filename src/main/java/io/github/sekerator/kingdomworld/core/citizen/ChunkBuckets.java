package io.github.sekerator.kingdomworld.core.citizen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Things kept by the chunk they lie in, so that what lies in one chunk is found without looking at all of them. Each chunk
 * keeps its things in the order they were added. Not thread safe.
 *
 * @param <T> what is kept
 */
public final class ChunkBuckets<T> {
    private final Map<String, Map<Long, List<T>>> buckets = new HashMap<>();

    public void add(String dimension, long chunk, T value) {
        buckets.computeIfAbsent(dimension, d -> new HashMap<>()).computeIfAbsent(chunk, c -> new ArrayList<>()).add(value);
    }

    /**
     * Takes a thing out of its chunk.
     *
     * @return whether it was there
     */
    public boolean remove(String dimension, long chunk, T value) {
        Map<Long, List<T>> inDimension = buckets.get(dimension);
        if (inDimension == null) {
            return false;
        }
        List<T> list = inDimension.get(chunk);
        if (list == null || !list.remove(value)) {
            return false;
        }
        if (list.isEmpty()) {
            inDimension.remove(chunk);
            if (inDimension.isEmpty()) {
                buckets.remove(dimension);
            }
        }
        return true;
    }

    /**
     * What lies in a chunk, in the order it was added; a view that must not outlive a change.
     */
    public List<T> get(String dimension, long chunk) {
        Map<Long, List<T>> inDimension = buckets.get(dimension);
        List<T> list = inDimension == null ? null : inDimension.get(chunk);
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }

    public void clear() {
        buckets.clear();
    }
}
