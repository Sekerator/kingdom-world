package io.github.sekerator.kingdomworld.core.land;

import java.util.Arrays;

/**
 * A binary heap of longs, the least first. The searches of the planners pack the cost, the source and the cell into one long,
 * so that ties are broken the same way every time.
 */
final class LongHeap {
    private long[] items;
    private int size;

    LongHeap(int capacity) {
        items = new long[Math.max(16, capacity)];
    }

    boolean isEmpty() {
        return size == 0;
    }

    int size() {
        return size;
    }

    void clear() {
        size = 0;
    }

    void add(long item) {
        if (size == items.length) {
            items = Arrays.copyOf(items, size * 2);
        }
        int at = size++;
        while (at > 0) {
            int parent = (at - 1) >>> 1;
            long above = items[parent];
            if (above <= item) {
                break;
            }
            items[at] = above;
            at = parent;
        }
        items[at] = item;
    }

    long poll() {
        long first = items[0];
        long last = items[--size];
        int at = 0;
        int half = size >>> 1;
        while (at < half) {
            int child = 2 * at + 1;
            long least = items[child];
            int right = child + 1;
            if (right < size && items[right] < least) {
                child = right;
                least = items[right];
            }
            if (last <= least) {
                break;
            }
            items[at] = least;
            at = child;
        }
        if (size > 0) {
            items[at] = last;
        }
        return first;
    }
}
