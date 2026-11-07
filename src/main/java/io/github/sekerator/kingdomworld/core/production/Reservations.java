package io.github.sekerator.kingdomworld.core.production;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Ingredients set aside in the storehouses for work that has begun, so that two workers never count on the same
 * items: a worker that starts making something reserves what it needs, takes the items one after another (each
 * taken item leaves the reservation) and the rest is released when the work ends, is given up, the worker dies or
 * its body leaves the world. A reservation also says what the work will give, so that orders do not ask for it
 * twice.
 *
 * <p>Reservations live only while the server runs; after a restart nothing is reserved. Not thread safe.
 */
public final class Reservations {

    /**
     * What one worker set aside.
     *
     * @param order       the order the work is for, 0 for work without an order
     * @param items       items still to be taken from the storehouses, by item id
     * @param output      item id the work gives
     * @param outputCount how many of it
     */
    public record Hold(UUID kingdomId, long order, Map<String, Integer> items, String output, int outputCount) {
        public Hold {
            Objects.requireNonNull(kingdomId, "kingdomId");
            Objects.requireNonNull(output, "output");
            Map<String, Integer> copy = new LinkedHashMap<>();
            items.forEach((item, count) -> {
                if (count != null && count > 0) {
                    copy.put(item, count);
                }
            });
            items = Collections.unmodifiableMap(copy);
            outputCount = Math.max(0, outputCount);
        }
    }

    private final Map<UUID, Hold> holds = new LinkedHashMap<>();

    /**
     * Sets aside items for a worker; an earlier reservation of the worker is replaced.
     */
    public void reserve(UUID worker, Hold hold) {
        holds.put(Objects.requireNonNull(worker, "worker"), Objects.requireNonNull(hold, "hold"));
    }

    /**
     * Ends the reservation of a worker.
     *
     * @return whether it had one
     */
    public boolean release(UUID worker) {
        return holds.remove(worker) != null;
    }

    /**
     * The worker took items it had reserved out of the storehouses: they are no longer set aside there.
     */
    public void taken(UUID worker, String item, int count) {
        Hold hold = holds.get(worker);
        if (hold == null || count <= 0 || !hold.items().containsKey(item)) {
            return;
        }
        Map<String, Integer> left = new LinkedHashMap<>(hold.items());
        left.merge(item, -count, Integer::sum);
        holds.put(worker, new Hold(hold.kingdomId(), hold.order(), left, hold.output(), hold.outputCount()));
    }

    public Hold hold(UUID worker) {
        return holds.get(worker);
    }

    public boolean has(UUID worker) {
        return holds.containsKey(worker);
    }

    /**
     * Items of the kind a kingdom set aside, not counting the reservation of {@code except} (may be {@code null}).
     */
    public int reserved(UUID kingdomId, String item, UUID except) {
        int total = 0;
        for (Map.Entry<UUID, Hold> entry : holds.entrySet()) {
            Hold hold = entry.getValue();
            if (hold.kingdomId().equals(kingdomId) && !entry.getKey().equals(except)) {
                total += hold.items().getOrDefault(item, 0);
            }
        }
        return total;
    }

    /**
     * Everything a kingdom set aside, by item id.
     */
    public Map<String, Integer> reserved(UUID kingdomId) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Hold hold : holds.values()) {
            if (hold.kingdomId().equals(kingdomId)) {
                hold.items().forEach((item, count) -> result.merge(item, count, Integer::sum));
            }
        }
        return result;
    }

    /**
     * Items that work under way will give for an order.
     */
    public int pending(UUID kingdomId, long order) {
        int total = 0;
        for (Hold hold : holds.values()) {
            if (hold.kingdomId().equals(kingdomId) && hold.order() == order && order != 0) {
                total += hold.outputCount();
            }
        }
        return total;
    }

    /**
     * Workers of a kingdom with a reservation.
     */
    public List<UUID> workers(UUID kingdomId) {
        List<UUID> result = new ArrayList<>();
        holds.forEach((worker, hold) -> {
            if (hold.kingdomId().equals(kingdomId)) {
                result.add(worker);
            }
        });
        return result;
    }

    public void removeKingdom(UUID kingdomId) {
        holds.values().removeIf(hold -> hold.kingdomId().equals(kingdomId));
    }

    public void clear() {
        holds.clear();
    }

    public int size() {
        return holds.size();
    }
}
