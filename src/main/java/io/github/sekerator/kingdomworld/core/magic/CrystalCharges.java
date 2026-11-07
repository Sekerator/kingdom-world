package io.github.sekerator.kingdomworld.core.magic;

import io.github.sekerator.kingdomworld.core.economy.Money;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * How a spell takes its energy from the crystals a player carries. A crystal holds the energy of its rank until it is
 * used; a crystal that gave part of its energy remembers what is left, and one that gave everything is gone. Crystals
 * already started are used first, then the weakest, so that strong crystals stay whole as long as possible. Either the
 * crystals hold enough for the whole cost and it is taken, or nothing is taken.
 */
public final class CrystalCharges {
    private CrystalCharges() {
    }

    /**
     * Crystals of one slot of an inventory: all of a stack hold the same energy.
     *
     * @param slot    the slot
     * @param count   crystals in the stack
     * @param energy  energy left in each of them
     * @param started whether they were used before (they hold less than a fresh crystal of their rank)
     */
    public record Charge(int slot, int count, long energy, boolean started) {
        public Charge {
            if (count < 0 || energy < 0) {
                throw new IllegalArgumentException("A charge out of range: " + count + " x " + energy);
            }
        }

        long total() {
            return Money.multiplyCapped(count, energy);
        }
    }

    /**
     * What happens to one slot.
     *
     * @param used crystals taken from the stack: used up, or (the last one, when {@code left > 0}) split off with
     *             {@code left} energy in it
     * @param left energy left in the last crystal taken; 0 if every crystal taken was used up
     */
    public record Use(int slot, int used, long left) {
    }

    /**
     * Energy all the crystals hold together.
     */
    public static long total(List<Charge> charges) {
        long sum = 0;
        for (Charge charge : charges) {
            sum = Money.addCapped(sum, charge.total());
        }
        return sum;
    }

    /**
     * Plans to take {@code cost} energy from the crystals; empty if they hold less.
     */
    public static Optional<List<Use>> plan(List<Charge> charges, long cost) {
        if (cost < 0) {
            return Optional.empty();
        }
        if (cost == 0) {
            return Optional.of(List.of());
        }
        if (total(charges) < cost) {
            return Optional.empty();
        }
        List<Charge> order = new ArrayList<>(charges);
        order.removeIf(charge -> charge.count() <= 0 || charge.energy() <= 0);
        order.sort(Comparator.comparing((Charge charge) -> !charge.started()).thenComparingLong(Charge::energy)
                .thenComparingInt(Charge::slot));
        List<Use> uses = new ArrayList<>();
        long left = cost;
        for (Charge charge : order) {
            if (left <= 0) {
                break;
            }
            long whole = Math.min(charge.count(), left / charge.energy());
            left -= whole * charge.energy();
            if (whole < charge.count() && left > 0) {
                uses.add(new Use(charge.slot(), (int) whole + 1, charge.energy() - left));
                left = 0;
            } else if (whole > 0) {
                uses.add(new Use(charge.slot(), (int) whole, 0));
            }
        }
        return left == 0 ? Optional.of(List.copyOf(uses)) : Optional.empty();
    }
}
