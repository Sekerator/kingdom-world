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
        DONE,
        /** The places of the city ran out: it widens its square by a ring on the land of its kingdom and builds on. */
        EXPAND,
        /** The places of the city ran out and no land of its kingdom lies around it. */
        NEEDS_LAND
    }

    /** The least ring a governor widens its city by, in blocks. */
    public static final int LEAST_RING = 8;
    /** The widest ring a governor widens its city by at once, in blocks. */
    public static final int WIDEST_RING = 16;

    /**
     * What a governor decided.
     *
     * @param role     the role of the building, else empty
     * @param template the template of the building, else empty
     * @param missing  what the materials it lacks are worth
     * @param wall     the class of the wall ordered, else {@code null}
     * @param sites    the constructions and orders it waits for
     * @param extent   the radius it widens the square of the city to, in blocks, else 0
     */
    public record Decision(Kind kind, String role, String template, long missing, Wall.WallClass wall, int sites, int extent) {
        public Decision {
            Objects.requireNonNull(kind, "kind");
            role = role == null ? "" : role;
            template = template == null ? "" : template;
        }

        static Decision of(Kind kind) {
            return new Decision(kind, "", "", 0, null, 0, 0);
        }

        static Decision building(Kind kind, RulerView.Option option) {
            return new Decision(kind, option.role(), option.template(), option.missing(), null, 0, 0);
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
                case EXPAND -> new GovernorNote(GovernorNote.Kind.EXPANDS, template, extent);
                case NEEDS_LAND -> new GovernorNote(GovernorNote.Kind.NEEDS_LAND, template, 0);
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
     * What of its share of the allowance of a day goes into the treasury of a city whose governor pays out of it: the share, never
     * so much that the city holds more granted and unspent money than the shares of some days, nor that the treasury of the
     * kingdom falls below its reserve.
     *
     * @param share     the share of the city of the allowance ({@link #share})
     * @param unspent   what the crown granted the city and it has not spent
     * @param keepDays  how many shares the city holds granted and unspent at most
     * @param balance   the money of the treasury of the kingdom
     * @param reserve   what the treasury keeps for hard times ({@link RulerMind#reserve})
     */
    public static long grant(long share, long unspent, int keepDays, long balance, long reserve) {
        if (share <= 0) {
            return 0;
        }
        long room = io.github.sekerator.kingdomworld.core.economy.Money.multiplyCapped(share, Math.max(1, keepDays)) - Math.max(0, unspent);
        return Math.max(0, Math.min(Math.min(share, room), balance - reserve));
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
    /**
     * The wish of a head of a city for a house of a merchant or a craftsman, a house that holds a shop; its templates are houses.
     */
    public static final String SHOP_HOUSE = "shop_house";

    public static List<String> wantedRoles(RulerView view, RulerRules rules, RulerCharacter character) {
        List<String> roles = new ArrayList<>();
        if (view.bedsTotal - view.bedsUsed <= rules.freeBeds() && view.planned(RulerMind.HOUSE) == 0) {
            roles.add(RulerMind.HOUSE);
        }
        if (view.storageSlots > 0 && view.storageUsed * 100L >= (long) view.storageSlots * rules.storageFullPercent()
                && view.planned(RulerMind.WAREHOUSE) == 0) {
            roles.add(RulerMind.WAREHOUSE);
        }
        // Townspeople who find too little to buy, no free place for a shop and somebody to keep one: a house of a merchant or a
        // craftsman, after the house for tenants and the storehouse.
        if (view.wantsShopHouse && view.planned(RulerMind.HOUSE) == 0 && view.planned(SHOP_HOUSE) == 0) {
            roles.add(SHOP_HOUSE);
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
     * How wide a ring a governor widens its city by for a building: room for the building and the street around it, from
     * {@value #LEAST_RING} to {@value #WIDEST_RING} blocks.
     *
     * @param size the longer side of the building
     */
    public static int ring(int size) {
        return Math.max(LEAST_RING, Math.min(WIDEST_RING, Math.max(0, size) + 4));
    }

    /**
     * What a governor does when the places of its city ran out for what it wants (no place by the data, or an order of its that
     * found none in the world): it widens the square of the city by a ring when land of its kingdom lies in that ring, without an
     * upper limit, and builds on; else it says the city needs land around it. A wall is never laid out anew for it.
     *
     * @param wanted     what it decided before it found no place
     * @param landAround whether land of the kingdom lies in the ring around the square
     * @param radius     the radius of the square of the city now
     * @param ring       how wide a ring it would widen the city by ({@link #ring})
     */
    public static Decision outOfRoom(Decision wanted, boolean landAround, int radius, int ring) {
        if (!landAround) {
            return new Decision(Kind.NEEDS_LAND, wanted.role(), wanted.template(), wanted.missing(), null, 0, 0);
        }
        long wider = (long) Math.max(0, radius) + Math.max(LEAST_RING, ring);
        return new Decision(Kind.EXPAND, wanted.role(), wanted.template(), wanted.missing(), null, 0, (int) Math.min(Integer.MAX_VALUE / 2, wider));
    }

    /**
     * The chunks of the ring a city of a radius would widen by: those that hold a block farther than the radius from the middle
     * and no farther than the radius and the ring, as chunk x and z.
     */
    public static java.util.List<int[]> ringChunks(int x, int z, int radius, int ring) {
        java.util.List<int[]> chunks = new java.util.ArrayList<>();
        int outer = radius + ring;
        for (int cx = (x - outer) >> 4; cx <= (x + outer) >> 4; cx++) {
            for (int cz = (z - outer) >> 4; cz <= (z + outer) >> 4; cz++) {
                // The farthest block of the chunk from the middle, along x or z.
                int far = Math.max(Math.max(Math.abs((cx << 4) - x), Math.abs((cx << 4) + 15 - x)),
                        Math.max(Math.abs((cz << 4) - z), Math.abs((cz << 4) + 15 - z)));
                if (far > radius) {
                    chunks.add(new int[]{cx, cz});
                }
            }
        }
        return chunks;
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
            return new Decision(Kind.WAIT, "", "", 0, null, situation.sites(), 0);
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
            return new Decision(Kind.WALL, "", "", 0, wall, 0, 0);
        }
        return Decision.of(Kind.DONE);
    }
}
