package io.github.sekerator.kingdomworld.core.trade;

import io.github.sekerator.kingdomworld.core.economy.Money;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * A deal between two kingdoms: the seller sends {@code count} of an item to the buyer for a price, once or every
 * {@code periodDays} game days. One of them proposes it, the other accepts or declines; a proposal nobody answers
 * expires.
 *
 * @param proposer     the kingdom that proposed it, the seller or the buyer
 * @param currency     the currency of the price: the seller's or the buyer's
 * @param periodDays   game days between deliveries, or 0 for one delivery
 * @param proposedTime game time of the proposal
 * @param expiresTime  game time the proposal expires at
 * @param nextTime     game time of the next delivery of a regular deal
 * @param deliveries   caravans the deal has sent
 * @param failures     deliveries in a row that could not start
 */
public record Deal(long id, UUID seller, UUID buyer, UUID proposer, String item, int count, long price, UUID currency, int periodDays,
                   Status status, long proposedTime, long expiresTime, long nextTime, int deliveries, int failures) {
    /** Most items one delivery carries. */
    public static final int MAX_COUNT = 4_096;
    /** Deliveries in a row that may fail before a regular deal is called off. */
    public static final int MAX_FAILURES = 3;

    public enum Status {
        /** Waits for the answer of the other kingdom. */
        PROPOSED,
        /** Accepted and regular: waits for the next delivery. */
        ACTIVE,
        /** Accepted and delivered once, or a regular deal that ended. */
        DONE,
        DECLINED,
        EXPIRED,
        CANCELLED;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /**
         * Whether the deal is over.
         */
        public boolean closed() {
            return this != PROPOSED && this != ACTIVE;
        }
    }

    public Deal {
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(buyer, "buyer");
        Objects.requireNonNull(proposer, "proposer");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(status, "status");
        if (seller.equals(buyer) || !(proposer.equals(seller) || proposer.equals(buyer)) || !(currency.equals(seller) || currency.equals(buyer))) {
            throw new IllegalArgumentException("A deal is between two kingdoms in the currency of one of them");
        }
        if (item.isEmpty() || item.length() > Goods.MAX_ITEM || count < 1 || count > MAX_COUNT || !Money.isPayment(price) || periodDays < 0
                || deliveries < 0 || failures < 0) {
            throw new IllegalArgumentException("Deal out of range: " + item + " x" + count + " for " + price);
        }
    }

    /**
     * The kingdom that answers the proposal.
     */
    public UUID partnerOf(UUID kingdom) {
        return kingdom.equals(seller) ? buyer : seller;
    }

    public UUID answerer() {
        return partnerOf(proposer);
    }

    public boolean involves(UUID kingdom) {
        return seller.equals(kingdom) || buyer.equals(kingdom);
    }

    public boolean regular() {
        return periodDays > 0;
    }

    public Deal withStatus(Status value) {
        return new Deal(id, seller, buyer, proposer, item, count, price, currency, periodDays, value, proposedTime, expiresTime, nextTime,
                deliveries, failures);
    }

    public Deal withNext(long time, int deliveriesDone, int failuresInRow) {
        return new Deal(id, seller, buyer, proposer, item, count, price, currency, periodDays, status, proposedTime, expiresTime, time,
                deliveriesDone, failuresInRow);
    }
}
