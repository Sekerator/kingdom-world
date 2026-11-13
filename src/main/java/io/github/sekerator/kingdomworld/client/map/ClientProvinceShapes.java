package io.github.sekerator.kingdomworld.client.map;

import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.ChunkOutlines;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The outlines of the provinces known on the client, made on a thread of their own from a {@link ClientProvinceMap.View} and
 * kept by the version of the chunks of each province: a province is outlined anew only when its chunks changed, never when it
 * changes hands. Used on the client main thread; the thread of its own reads only the view.
 */
final class ClientProvinceShapes {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "KingdomWorld province outlines");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    private record Outline(int version, List<ChunkOutlines.Area> areas) {
    }

    private final Map<Integer, Outline> outlines = new HashMap<>();
    private CompletableFuture<Map<Integer, Outline>> pending;
    private long seen = -1;
    private String dimension;
    private volatile long lastMillis;
    private volatile int lastCount;

    /**
     * Takes in outlines made since the last call and asks for those of provinces whose chunks changed.
     *
     * @return the provinces outlined anew or gone since the last call
     */
    Set<Integer> update() {
        Set<Integer> changed = new HashSet<>();
        if (pending != null && pending.isDone()) {
            try {
                Map<Integer, Outline> made = pending.join();
                outlines.putAll(made);
                changed.addAll(made.keySet());
            } catch (RuntimeException e) {
                com.mojang.logging.LogUtils.getLogger().warn("The provinces could not be outlined: {}", e.toString());
            }
            pending = null;
        }
        ClientProvinceMap.View view = ClientProvinceMap.view();
        if (pending == null && view.version() != seen) {
            seen = view.version();
            if (!java.util.Objects.equals(dimension, view.dimension())) {
                changed.addAll(outlines.keySet());
                outlines.clear();
                dimension = view.dimension();
            }
            Map<Integer, Integer> wanted = new HashMap<>();
            for (int id = 1; id < view.provinces().length; id++) {
                if (view.provinces()[id] != null) {
                    int version = ClientProvinceMap.shapeVersion(id);
                    Outline known = outlines.get(id);
                    if (known == null || known.version() != version) {
                        wanted.put(id, version);
                    }
                }
            }
            for (Integer id : List.copyOf(outlines.keySet())) {
                if (id >= view.provinces().length || view.provinces()[id] == null) {
                    outlines.remove(id);
                    changed.add(id);
                }
            }
            if (!wanted.isEmpty()) {
                pending = CompletableFuture.supplyAsync(() -> outline(view, wanted), WORKER);
            }
        }
        return changed;
    }

    /**
     * The outlines of some provinces of a view: every tile is gone through once. Any thread.
     */
    private Map<Integer, Outline> outline(ClientProvinceMap.View view, Map<Integer, Integer> wanted) {
        long start = System.nanoTime();
        Map<Integer, List<Long>> chunks = new HashMap<>();
        for (Long2ObjectMap.Entry<char[]> entry : view.tiles().long2ObjectEntrySet()) {
            char[] tile = entry.getValue();
            int baseX = ProvinceMap.tileX(entry.getLongKey()) * ProvinceMap.TILE;
            int baseZ = ProvinceMap.tileZ(entry.getLongKey()) * ProvinceMap.TILE;
            for (int i = 0; i < tile.length; i++) {
                if (tile[i] != 0 && wanted.containsKey((int) tile[i])) {
                    chunks.computeIfAbsent((int) tile[i], id -> new ArrayList<>()).add(ChunkKey.of(baseX + (i & 31), baseZ + (i >> 5)));
                }
            }
        }
        Map<Integer, Outline> made = new HashMap<>();
        wanted.forEach((id, version) -> {
            List<Long> list = chunks.get(id);
            ClientProvinceMap.View current = view;
            var entry = current.province(id);
            Long anchor = entry == null ? null : ChunkKey.of(entry.centerX() >> 4, entry.centerZ() >> 4);
            made.put(id, new Outline(version, list == null ? List.of() : ChunkOutlines.of(list, list.contains(anchor) ? anchor : null)));
        });
        lastMillis = (System.nanoTime() - start) / 1_000_000L;
        lastCount = wanted.size();
        return made;
    }

    List<ChunkOutlines.Area> areas(int id) {
        Outline outline = outlines.get(id);
        return outline == null ? List.of() : outline.areas();
    }

    /**
     * How long the last outlining took and how many provinces it outlined, for the client self-check.
     */
    String stats() {
        return lastCount + " province(s) outlined in " + lastMillis + " ms";
    }

    void clear() {
        outlines.clear();
        pending = null;
        seen = -1;
        dimension = null;
    }
}
