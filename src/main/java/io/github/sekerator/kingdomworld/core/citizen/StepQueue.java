package io.github.sekerator.kingdomworld.core.citizen;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Steps that go a few items at a time, so that no tick does a whole step: a step begins when it is due, every tick the steps
 * under way take the next items within a budget of items and of time, the oldest step first, and a step whose items are done
 * ends. A step that comes due while the last step of the same key is still under way waits until that one has ended. Not
 * thread safe.
 *
 * @param <K> what a step belongs to, e.g. a kingdom
 */
public final class StepQueue<K> {
    /**
     * A step under way.
     */
    public interface Step {
        /**
         * Whether the step may go on; a step that may not is dropped without its end.
         */
        boolean alive();

        boolean hasNext();

        /**
         * Does the next item.
         */
        void next();

        /**
         * Ends the step once its items are done.
         */
        void finish();
    }

    private final Map<K, Step> running = new LinkedHashMap<>();
    private final Map<K, Supplier<Step>> waiting = new LinkedHashMap<>();

    /**
     * A step of a key is due: it begins now, or once the step of the key under way has ended.
     *
     * @param begin begins the step (its first part happens now) and gives it, or null for no step
     */
    public void due(K key, Supplier<Step> begin) {
        if (running.containsKey(key)) {
            waiting.put(key, begin);
        } else {
            start(key, begin);
        }
    }

    /**
     * Begins the steps that waited for the step of their key to end.
     */
    public void startWaiting() {
        for (K key : List.copyOf(waiting.keySet())) {
            if (!running.containsKey(key)) {
                start(key, waiting.remove(key));
            }
        }
    }

    private void start(K key, Supplier<Step> begin) {
        Step step = begin.get();
        if (step != null) {
            running.put(key, step);
        }
    }

    /**
     * Takes the steps under way further: at most {@code items} items, and no new item once {@code nanos} have gone by on the
     * clock, the oldest step first; a step whose items are done ends in the same call.
     *
     * @return the items done
     */
    public int advance(int items, long nanos, LongSupplier clock) {
        long deadline = clock.getAsLong() + nanos;
        int done = 0;
        Iterator<Map.Entry<K, Step>> steps = running.entrySet().iterator();
        while (steps.hasNext()) {
            Step step = steps.next().getValue();
            if (!step.alive()) {
                steps.remove();
                continue;
            }
            while (done < items && step.hasNext() && clock.getAsLong() < deadline) {
                step.next();
                done++;
            }
            if (step.hasNext()) {
                break;
            }
            steps.remove();
            step.finish();
        }
        return done;
    }

    public boolean running(K key) {
        return running.containsKey(key);
    }

    public boolean waiting(K key) {
        return waiting.containsKey(key);
    }

    /**
     * The keys of the steps under way, oldest first.
     */
    public List<K> keys() {
        return new ArrayList<>(running.keySet());
    }

    public Set<K> waitingKeys() {
        return new LinkedHashSet<>(waiting.keySet());
    }

    public void clear() {
        running.clear();
        waiting.clear();
    }
}
