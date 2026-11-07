package io.github.sekerator.kingdomworld.core.trade;

import io.github.sekerator.kingdomworld.core.economy.Money;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * A caravan on its way from the capital of the seller to the capital of the buyer. It carries the goods of a delivery;
 * the payment waits in escrow until it arrives. Travel is abstract: the caravan is where the time says it is.
 *
 * @param escrowId   the payment in escrow
 * @param departTime game time it left
 * @param arriveTime game time it arrives
 */
public record Caravan(long id, long dealId, UUID seller, UUID buyer, List<Goods> cargo, UUID currency, long payment, long escrowId,
                      String dimension, int fromX, int fromZ, int toX, int toZ, long departTime, long arriveTime, State state) {
    /** Most entries of goods one caravan carries. */
    public static final int MAX_CARGO = 256;

    public enum State {
        /** On the way. */
        TRAVELING,
        /** Arrived, waits for room in the storehouses of the buyer. */
        WAITING,
        /** Called off on the way: the goods go back to the seller. */
        RETURNING;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public Caravan {
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(buyer, "buyer");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(state, "state");
        cargo = List.copyOf(cargo);
        if (cargo.isEmpty() || cargo.size() > MAX_CARGO || !Money.isAmount(payment) || arriveTime < departTime) {
            throw new IllegalArgumentException("Caravan out of range");
        }
    }

    /**
     * Items the caravan carries.
     */
    public long items() {
        long sum = 0;
        for (Goods goods : cargo) {
            sum += goods.count();
        }
        return sum;
    }

    /**
     * How far along the way the caravan is at a game time, from 0 to 1.
     */
    public double progress(long gameTime) {
        if (arriveTime <= departTime) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, (gameTime - departTime) / (double) (arriveTime - departTime)));
    }

    public Caravan withState(State value) {
        return new Caravan(id, dealId, seller, buyer, cargo, currency, payment, escrowId, dimension, fromX, fromZ, toX, toZ, departTime,
                arriveTime, value);
    }

    /**
     * Game ticks a trip takes between two points.
     */
    public static long travelTicks(int fromX, int fromZ, int toX, int toZ, double blocksPerMinute) {
        double distance = Math.sqrt(Math.pow((double) toX - fromX, 2) + Math.pow((double) toZ - fromZ, 2));
        double minutes = distance / Math.max(0.001, blocksPerMinute);
        return Math.max(1200L, Math.min(24_000L * 30, Math.round(minutes * 1200.0)));
    }
}
