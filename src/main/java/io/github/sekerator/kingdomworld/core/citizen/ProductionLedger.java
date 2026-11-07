package io.github.sekerator.kingdomworld.core.citizen;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * What the citizens of each kingdom produced, by profession and item, for the current game day and the day
 * before. Work near players and work by formula count alike. The economy of later stages reads it.
 *
 * <p>Not thread safe.
 */
public final class ProductionLedger {
    /** Items kept per profession and day at most; rarer ones are summed into {@link #OTHER}. */
    public static final int MAX_ITEMS = 32;
    /** Item id under which items beyond {@link #MAX_ITEMS} are counted. */
    public static final String OTHER = "*";

    /**
     * One day of production: profession id to item id to count.
     */
    public record Day(long day, Map<String, Map<String, Long>> counts) {
        public static Day empty(long day) {
            return new Day(day, Map.of());
        }

        public Day {
            Map<String, Map<String, Long>> copy = new TreeMap<>();
            counts.forEach((profession, items) -> copy.put(profession, Collections.unmodifiableMap(new TreeMap<>(items))));
            counts = Collections.unmodifiableMap(copy);
        }

        /**
         * All items a profession produced that day.
         */
        public long total(String professionId) {
            long sum = 0;
            for (long count : counts.getOrDefault(professionId, Map.of()).values()) {
                sum += count;
            }
            return sum;
        }

        public long total() {
            long sum = 0;
            for (String profession : counts.keySet()) {
                sum += total(profession);
            }
            return sum;
        }
    }

    private static final class Book {
        long day;
        Map<String, Map<String, Long>> today = new LinkedHashMap<>();
        Map<String, Map<String, Long>> yesterday = new LinkedHashMap<>();
        long yesterdayDay = Long.MIN_VALUE;
    }

    private final Map<UUID, Book> books = new LinkedHashMap<>();
    private Runnable changeListener = () -> {
    };

    public void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
    }

    /**
     * Counts items a profession produced on a game day.
     */
    public void record(UUID kingdomId, long gameDay, String professionId, String itemId, long count) {
        if (count <= 0 || itemId == null || itemId.isEmpty()) {
            return;
        }
        Book book = books.computeIfAbsent(kingdomId, k -> {
            Book created = new Book();
            created.day = gameDay;
            return created;
        });
        roll(book, gameDay);
        if (gameDay < book.day) {
            // A late entry for a past day is counted for the day it belongs to only if that is yesterday.
            if (gameDay == book.yesterdayDay) {
                add(book.yesterday, professionId, itemId, count);
                changeListener.run();
            }
            return;
        }
        add(book.today, professionId, itemId, count);
        changeListener.run();
    }

    private static void roll(Book book, long gameDay) {
        if (gameDay > book.day) {
            if (gameDay == book.day + 1) {
                book.yesterday = book.today;
                book.yesterdayDay = book.day;
            } else {
                book.yesterday = new LinkedHashMap<>();
                book.yesterdayDay = gameDay - 1;
            }
            book.today = new LinkedHashMap<>();
            book.day = gameDay;
        }
    }

    private static void add(Map<String, Map<String, Long>> counts, String professionId, String itemId, long count) {
        Map<String, Long> items = counts.computeIfAbsent(professionId, p -> new LinkedHashMap<>());
        if (!items.containsKey(itemId) && items.size() >= MAX_ITEMS) {
            items.merge(OTHER, count, Long::sum);
        } else {
            items.merge(itemId, count, Long::sum);
        }
    }

    /**
     * Production of the given game day so far.
     */
    public Day today(UUID kingdomId, long gameDay) {
        Book book = books.get(kingdomId);
        if (book == null) {
            return Day.empty(gameDay);
        }
        if (book.day == gameDay) {
            return new Day(gameDay, book.today);
        }
        if (book.yesterdayDay == gameDay) {
            return new Day(gameDay, book.yesterday);
        }
        return Day.empty(gameDay);
    }

    /**
     * Production of the day before the given game day.
     */
    public Day yesterday(UUID kingdomId, long gameDay) {
        return today(kingdomId, gameDay - 1);
    }

    public void removeKingdom(UUID kingdomId) {
        if (books.remove(kingdomId) != null) {
            changeListener.run();
        }
    }

    /**
     * The saved form of a kingdom's book: the current day and the day before, or empty.
     */
    public java.util.List<Day> days(UUID kingdomId) {
        Book book = books.get(kingdomId);
        if (book == null) {
            return java.util.List.of();
        }
        java.util.List<Day> days = new java.util.ArrayList<>();
        days.add(new Day(book.day, book.today));
        if (book.yesterdayDay != Long.MIN_VALUE) {
            days.add(new Day(book.yesterdayDay, book.yesterday));
        }
        return days;
    }

    /**
     * Restores a kingdom's book from saved days (the newest is the current one). Does not notify.
     */
    public void restore(UUID kingdomId, java.util.List<Day> days) {
        if (days.isEmpty()) {
            return;
        }
        Day newest = days.get(0);
        for (Day day : days) {
            if (day.day() > newest.day()) {
                newest = day;
            }
        }
        Book book = new Book();
        book.day = newest.day();
        newest.counts().forEach((profession, items) -> book.today.put(profession, new LinkedHashMap<>(items)));
        for (Day day : days) {
            if (day.day() == newest.day() - 1) {
                book.yesterdayDay = day.day();
                day.counts().forEach((profession, items) -> book.yesterday.put(profession, new LinkedHashMap<>(items)));
            }
        }
        books.put(kingdomId, book);
    }
}
