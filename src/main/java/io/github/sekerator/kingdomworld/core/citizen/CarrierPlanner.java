package io.github.sekerator.kingdomworld.core.citizen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Plans the work of carriers. Three kinds of trips:
 * <ul>
 *   <li>collect: from the container of a work post to a storehouse, the main work;</li>
 *   <li>supply: from a storehouse to the container of a post that asked for an item (the way production chains
 *       get their inputs);</li>
 *   <li>balance: from a storehouse that is nearly full to one far from it with room.</li>
 * </ul>
 * A carrier near players makes one trip at a time ({@link #next}); far from players all carriers of a kingdom
 * together move a number of items per step ({@link #plan}).
 */
public final class CarrierPlanner {
    /** A storehouse fuller than this share of its slots gives goods to an emptier one. */
    public static final double FULL_SHARE = 0.9;
    /** A storehouse emptier than this share of its slots takes goods from a full one. */
    public static final double EMPTY_SHARE = 0.5;
    /** Storehouses closer than this (blocks) are not balanced: the trip is not worth it. */
    public static final int BALANCE_DISTANCE = 24;

    /**
     * A container that holds goods: the container of a post or a storehouse.
     *
     * @param items     items in it
     * @param freeSlots empty slots
     */
    public record Stock(WorldPos pos, int items, int freeSlots, int slots) {
        public Stock {
            Objects.requireNonNull(pos, "pos");
        }

        double fill() {
            return slots <= 0 ? 1.0 : (slots - freeSlots) / (double) slots;
        }
    }

    /**
     * A post that wants an item brought into its container.
     *
     * @param source a storehouse that holds the item
     */
    public record Request(WorldPos post, String item, int amount, WorldPos source) {
        public Request {
            Objects.requireNonNull(post, "post");
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(source, "source");
        }
    }

    public enum Kind {
        COLLECT,
        SUPPLY,
        BALANCE
    }

    /**
     * One trip.
     *
     * @param item   item id to carry, or empty for anything
     * @param amount items to carry at most
     */
    public record Task(Kind kind, WorldPos from, WorldPos to, String item, int amount) {
    }

    private CarrierPlanner() {
    }

    /**
     * The next trip of a carrier near players: collecting from the fullest post container in reach first, then
     * supplying posts, then balancing storehouses.
     *
     * @param range how far from {@code home} (the carrier's post) a trip may lead, in blocks
     */
    public static Optional<Task> next(WorldPos home, List<Stock> postContainers, List<Stock> storehouses,
                                      List<Request> requests, int capacity, int range) {
        double reach = (double) range * range;
        List<Request> near = new ArrayList<>();
        for (Request request : requests) {
            if (home.distanceSqr(request.source()) <= reach) {
                near.add(request);
            }
        }
        List<Stock> inReach = new ArrayList<>();
        for (Stock stock : storehouses) {
            if (home.distanceSqr(stock.pos()) <= reach) {
                inReach.add(stock);
            }
        }
        return next(home, postContainers, source -> roomiest(storehouses, source.pos(), reach, home).map(Stock::pos), inReach, near,
                capacity, range);
    }

    /**
     * The next trip of a carrier near players, the storehouses chosen by the caller: collecting from the fullest post container
     * within {@code range} of {@code home} into the storehouse {@code collectTarget} gives for it (the storehouses of the work of
     * that post first), then supplying posts within the range from the storehouses of the requests, then balancing the storehouses
     * given (the common ones in reach).
     */
    public static Optional<Task> next(WorldPos home, List<Stock> postContainers, java.util.function.Function<Stock, Optional<WorldPos>> collectTarget,
                                      List<Stock> balanced, List<Request> requests, int capacity, int range) {
        double reach = (double) range * range;
        Stock source = null;
        for (Stock stock : postContainers) {
            if (stock.items() > 0 && home.distanceSqr(stock.pos()) <= reach
                    && (source == null || stock.items() > source.items())) {
                source = stock;
            }
        }
        if (source != null) {
            Optional<WorldPos> target = collectTarget.apply(source);
            if (target.isPresent()) {
                return Optional.of(new Task(Kind.COLLECT, source.pos(), target.get(), "", Math.min(capacity, source.items())));
            }
        }
        for (Request request : requests) {
            if (request.amount() > 0 && home.distanceSqr(request.post()) <= reach) {
                return Optional.of(new Task(Kind.SUPPLY, request.source(), request.post(), request.item(),
                        Math.min(capacity, request.amount())));
            }
        }
        return balance(home, balanced, capacity, Double.MAX_VALUE);
    }

    private static Optional<Stock> roomiest(List<Stock> storehouses, WorldPos near, double reach, WorldPos home) {
        return storehouses.stream()
                .filter(stock -> stock.freeSlots() > 0 && home.distanceSqr(stock.pos()) <= reach)
                .min(Comparator.comparingDouble((Stock stock) -> near.distanceSqr(stock.pos())));
    }

    private static Optional<Task> balance(WorldPos home, List<Stock> storehouses, int capacity, double reach) {
        List<Stock> near = new ArrayList<>();
        for (Stock stock : storehouses) {
            if (home.distanceSqr(stock.pos()) <= reach) {
                near.add(stock);
            }
        }
        Stock full = null;
        for (Stock stock : near) {
            if (stock.fill() >= FULL_SHARE && stock.items() > 0 && (full == null || stock.fill() > full.fill())) {
                full = stock;
            }
        }
        if (full == null) {
            return Optional.empty();
        }
        Stock empty = null;
        double min = (double) BALANCE_DISTANCE * BALANCE_DISTANCE;
        for (Stock stock : near) {
            if (stock != full && stock.fill() <= EMPTY_SHARE && full.pos().distanceSqr(stock.pos()) >= min
                    && (empty == null || stock.fill() < empty.fill())) {
                empty = stock;
            }
        }
        return empty == null ? Optional.empty()
                : Optional.of(new Task(Kind.BALANCE, full.pos(), empty.pos(), "", Math.min(capacity, full.items())));
    }

    /**
     * Trips of all carriers far from players in one step: the fullest post containers are emptied first into
     * the storehouses with the most room, until the carriers' capacity is used up.
     */
    public static List<Task> plan(List<Stock> postContainers, List<Stock> storehouses, int capacity) {
        List<Task> tasks = new ArrayList<>();
        int left = capacity;
        List<Stock> sources = new ArrayList<>(postContainers);
        sources.sort(Comparator.comparingInt(Stock::items).reversed());
        List<int[]> room = new ArrayList<>();
        for (Stock storehouse : storehouses) {
            room.add(new int[]{storehouse.freeSlots()});
        }
        for (Stock source : sources) {
            if (left <= 0 || source.items() <= 0) {
                break;
            }
            int best = -1;
            for (int i = 0; i < storehouses.size(); i++) {
                if (room.get(i)[0] > 0 && (best < 0 || room.get(i)[0] > room.get(best)[0])) {
                    best = i;
                }
            }
            if (best < 0) {
                break;
            }
            int amount = Math.min(left, source.items());
            tasks.add(new Task(Kind.COLLECT, source.pos(), storehouses.get(best).pos(), "", amount));
            left -= amount;
            // A stack takes a slot; the estimate keeps several sources from aiming at a storehouse that fills up.
            room.get(best)[0] -= Math.max(1, (amount + 63) / 64);
        }
        return tasks;
    }
}
