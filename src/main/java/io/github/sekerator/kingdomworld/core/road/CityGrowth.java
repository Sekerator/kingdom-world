package io.github.sekerator.kingdomworld.core.road;

import io.github.sekerator.kingdomworld.core.npc.RulerCharacter;
import io.github.sekerator.kingdomworld.core.npc.RulerMind;
import io.github.sekerator.kingdomworld.core.npc.RulerRules;
import io.github.sekerator.kingdomworld.core.npc.RulerView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToLongFunction;

/**
 * What the governor of a city of a kingdom of players builds next, once a game day: a post of builders while the city has none
 * and somebody is without work, nothing while its builders have enough to do, else the buildings its level and its people
 * want as the rulers of the world want them ({@link RulerMind#wantedRoles}): a house when the beds are nearly taken, a
 * storehouse when the stores are full, the first building of the table the city lacks, a house of the nobility leaning by its
 * character; and once nothing is wanted, a wall round a city without one. It never repairs, raises or takes down a building
 * and never lays a road. A building is ordered only when what its materials lack is worth no more than the governor may spend
 * today; without money from the king it waits for the storehouses to hold them. Pure, so that the server and the tests share
 * it.
 */
public final class CityGrowth {
    private CityGrowth() {
    }

    /**
     * What a governor decided.
     */
    public enum Kind {
        /** No builder of the city, nobody to hire and no builder of another city to help: it waits. */
        NO_BUILDER,
        /** It orders a post of builders for the city, a citizen without work hired for it. */
        ORDER_POST,
        /** It waits for the post of builders ordered. */
        WAIT_POST,
        /** The city builds as much as its builders manage. */
        WAIT,
        /** It orders a building. */
        BUILD,
        /** It saves for a building whose missing materials cost more than it may spend today. */
        SAVE,
        /** It waits for the storehouses to hold the materials of a building, without money to buy them. */
        LACK,
        /** It orders a wall round the city. */
        WALL,
        /** Nothing is wanted. */
        DONE
    }

    /**
     * What a governor decided.
     *
     * @param role     the role of the building, else empty
     * @param template the template of the building, else empty
     * @param missing  what the materials it lacks are worth
     * @param wall     the class of the wall ordered, else {@code null}
     * @param sites    the constructions and orders it waits for
     */
    public record Decision(Kind kind, String role, String template, long missing, Wall.WallClass wall, int sites) {
        public Decision {
            Objects.requireNonNull(kind, "kind");
            role = role == null ? "" : role;
            template = template == null ? "" : template;
        }

        static Decision of(Kind kind) {
            return new Decision(kind, "", "", 0, null, 0);
        }

        static Decision building(Kind kind, RulerView.Option option) {
            return new Decision(kind, option.role(), option.template(), option.missing(), null, 0);
        }

        /**
         * The note the window of the king shows for it.
         */
        public GovernorNote note() {
            return switch (kind) {
                case NO_BUILDER -> GovernorNote.of(GovernorNote.Kind.NO_BUILDER);
                case ORDER_POST, WAIT_POST -> GovernorNote.of(GovernorNote.Kind.POST);
                case WAIT -> new GovernorNote(GovernorNote.Kind.WAITING, "", sites);
                case BUILD -> new GovernorNote(GovernorNote.Kind.BUILDS, template, missing);
                case SAVE -> new GovernorNote(GovernorNote.Kind.SAVES, template, missing);
                case LACK -> new GovernorNote(GovernorNote.Kind.LACKS, template, missing);
                case WALL -> new GovernorNote(GovernorNote.Kind.WALL, wall == null ? "" : wall.name().toLowerCase(java.util.Locale.ROOT), 0);
                case DONE -> GovernorNote.of(GovernorNote.Kind.DONE);
            };
        }
    }

    /**
     * What a governor knows of its city beyond its view.
     *
     * @param builders      citizens at the posts of builders of the city
     * @param otherBuilders citizens at the posts of builders of the other cities of the kingdom, who help where none build
     * @param unemployed    citizens of the kingdom without work
     * @param sites         constructions of the city and orders for it not laid out yet
     * @param postOrdered   whether a post of builders is ordered for the city and not set up yet
     * @param wall          whether the city has a wall or one is being laid out
     * @param allowance     what the king gives all governors a day; 0 for nothing, the governors then buy nothing
     * @param budget        what the governor of this city may still spend today ({@link #budget})
     * @param character     the character of the house of the nobility that governs the city, {@code null} for a mayor
     */
    public record Situation(int builders, int otherBuilders, int unemployed, int sites, boolean postOrdered, boolean wall, long allowance,
                            long budget, RulerCharacter character) {
    }

    /**
     * The fair share of the allowance of the king for the city of one governor: the allowance shared by the cities with a
     * governor, rounded up.
     */
    public static long share(long allowance, int governed) {
        if (allowance <= 0) {
            return 0;
        }
        int cities = Math.max(1, governed);
        return allowance / cities + (allowance % cities == 0 ? 0 : 1);
    }

    /**
     * What the governor of a city may still spend today: what is left of its share, and never so much that the treasury falls
     * below its reserve. 0 without an allowance.
     *
     * @param spent   what it spent today already
     * @param balance the money of the treasury
     * @param reserve what the treasury keeps for hard times ({@link RulerMind#reserve})
     */
    public static long budget(long allowance, int governed, long spent, long balance, long reserve) {
        if (allowance <= 0) {
            return 0;
        }
        long left = share(allowance, governed) - Math.max(0, spent);
        return Math.max(0, Math.min(left, balance - reserve));
    }

    /**
     * How much of a good of the storehouses a governor plans with: the given percent of it, so that the buildings of the king
     * find the rest.
     */
    public static long usable(long stock, int stockPercent) {
        if (stock <= 0) {
            return 0;
        }
        int percent = Math.max(0, Math.min(100, stockPercent));
        return stock / 100 * percent + stock % 100 * percent / 100;
    }

    /**
     * What the materials a building lacks are worth for a governor, who plans with only a share of every good of the
     * storehouses.
     *
     * @param cost         the materials of the building, by item
     * @param stock        what the storehouses hold, by item
     * @param worth        what one of an item is worth
     * @param stockPercent the share of every good the governor plans with
     */
    public static long missing(Map<String, Integer> cost, Map<String, Long> stock, ToLongFunction<String> worth, int stockPercent) {
        long total = 0;
        for (Map.Entry<String, Integer> entry : cost.entrySet()) {
            long lack = entry.getValue() - usable(stock.getOrDefault(entry.getKey(), 0L), stockPercent);
            if (lack > 0) {
                total += lack * Math.max(1, worth.applyAsLong(entry.getKey()));
            }
        }
        return total;
    }

    /**
     * The roles a governor would order a building of, the most wanted first: a house when the beds are nearly taken, a
     * storehouse when the stores are full, then the roles of the table its level wants and it lacks, those its house of the
     * nobility leans to first, else in the order of the table. Only for these are templates priced.
     */
    public static List<String> wantedRoles(RulerView view, RulerRules rules, RulerCharacter character) {
        List<String> roles = new ArrayList<>();
        if (view.bedsTotal - view.bedsUsed <= rules.freeBeds() && view.planned(RulerMind.HOUSE) == 0) {
            roles.add(RulerMind.HOUSE);
        }
        if (view.storageSlots > 0 && view.storageUsed * 100L >= (long) view.storageSlots * rules.storageFullPercent()
                && view.planned(RulerMind.WAREHOUSE) == 0) {
            roles.add(RulerMind.WAREHOUSE);
        }
        List<RulerRules.RoleWant> missing = new ArrayList<>();
        for (RulerRules.RoleWant want : rules.roles()) {
            if (view.knownRoles.contains(want.role()) && view.built(want.role()) + view.planned(want.role()) < want.count(view.cityLevel)
                    && !roles.contains(want.role())) {
                missing.add(want);
            }
        }
        if (character != null) {
            // A stable sort: the table keeps its order among roles the house leans to alike.
            missing.sort(Comparator.comparingInt((RulerRules.RoleWant want) -> -RulerMind.roleBonus(character, want.role())));
        }
        for (RulerRules.RoleWant want : missing) {
            roles.add(want.role());
        }
        return roles;
    }

    /**
     * What the governor of a city does today.
     *
     * @param view what it sees of its city: its level, beds, storehouses, the buildings standing and planned, and in
     *             {@link RulerView#builds} the cheapest template of every role it wants with what its materials lack
     */
    public static Decision decide(RulerView view, Situation situation, RulerRules rules) {
        if (situation.builders() <= 0) {
            if (situation.postOrdered()) {
                return Decision.of(Kind.WAIT_POST);
            }
            if (situation.unemployed() > 0) {
                return Decision.of(Kind.ORDER_POST);
            }
            if (situation.otherBuilders() <= 0) {
                return Decision.of(Kind.NO_BUILDER);
            }
            // Builders of other cities help where nobody builds.
        }
        if (situation.sites() >= Math.max(1, situation.builders())) {
            return new Decision(Kind.WAIT, "", "", 0, null, situation.sites());
        }
        for (String role : wantedRoles(view, rules, situation.character())) {
            RulerView.Option option = null;
            for (RulerView.Option candidate : view.builds) {
                if (candidate.role().equals(role) && candidate.place()) {
                    option = candidate;
                    break;
                }
            }
            if (option == null) {
                continue;
            }
            if (situation.allowance() <= 0) {
                return Decision.building(option.missing() > 0 ? Kind.LACK : Kind.BUILD, option);
            }
            return Decision.building(option.missing() > situation.budget() ? Kind.SAVE : Kind.BUILD, option);
        }
        if (!situation.wall()) {
            Wall.WallClass wall = view.cityLevel >= CityLevel.CITY.ordinal() ? Wall.WallClass.STONE : Wall.WallClass.PALISADE;
            return new Decision(Kind.WALL, "", "", 0, wall, 0);
        }
        return Decision.of(Kind.DONE);
    }
}
