package io.github.sekerator.kingdomworld.core;

/**
 * Steps through a row of choices that closes into a ring: one step forward from the last choice is the first one and
 * one step back from the first is the last. The buttons that go through choices take the next one on a left click and
 * the one before on a right click with it.
 */
public final class Cycle {
    private Cycle() {
    }

    /**
     * The place {@code direction} steps away from {@code index} in a ring of {@code size} places; a negative direction
     * goes back. An index outside the ring is taken around it first, and a ring without places gives 0.
     */
    public static int step(int index, int direction, int size) {
        if (size <= 0) {
            return 0;
        }
        return Math.floorMod(Math.floorMod(index, size) + Math.floorMod(direction, size), size);
    }

    /**
     * The value {@code direction} steps away from {@code value} among the values of its enum, in the order they are
     * declared.
     */
    public static <E extends Enum<E>> E step(E value, int direction) {
        E[] values = value.getDeclaringClass().getEnumConstants();
        return values[step(value.ordinal(), direction, values.length)];
    }
}
