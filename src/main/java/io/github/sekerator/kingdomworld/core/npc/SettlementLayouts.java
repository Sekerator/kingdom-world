package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.land.Tier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * How the settlements of a state are laid out, in the style of their kingdom: a large city as the districts of a medium capital
 * with the residence of its governor (the small castle of the style) where the palace would stand and its town hall in it, a
 * small town as a town with its market, tavern, workshops, storehouse and houses inside a palisade ({@link SettlementTowns}), a
 * village as two rows of houses with its barn, well and fields, and a hamlet as two or three houses by a well with a small
 * storehouse and a field. Positions are in blocks from the middle of the settlement.
 */
public final class SettlementLayouts {
    /** Blocks the levelled ground of a village reaches from its middle. */
    static final int VILLAGE_REACH = SettlementTowns.VILLAGE_ROW + 2;
    /** Blocks the levelled ground of a hamlet reaches from its middle. */
    static final int HAMLET_REACH = SettlementTowns.HAMLET_ROW + 2;
    /** The name of the round of the guard or the watch of a settlement among its posts. */
    public static final String ROUND = "round";
    /** The prefix of the posts of the guard at the gates of a settlement. */
    public static final String GATE = "gate_";
    /** The quarter of the residence of the governor of a large city. */
    public static final String RESIDENCE = "residence";
    /** Blocks from the middle of a large city at which its round passes the residence on its way from gate to gate. */
    static final int CITY_SQUARE = 19;

    private SettlementLayouts() {
    }

    /**
     * The layout of a settlement.
     *
     * @param plots  its buildings in the order they go up
     * @param reach  how far its levelled ground reaches from its middle, in blocks
     * @param fields the middles of its fields
     * @param hall   whether a town hall stands in its middle (a town, a large city)
     * @param wall   the radius of its wall, 0 for none
     * @param city   the districts of a large city, null for the others
     * @param gates  where its wall has its gates, {x, z}
     * @param posts  the posts of its guard or watch: a place to stand inside each gate (named {@value #GATE} and the side) and
     *               the round ({@value #ROUND}) to walk
     * @param works  the middles of the places of woodcutters and miners outside a large city, by turns; empty where the land
     *               around decides
     */
    public record Layout(Tier tier, List<NpcComposition.Plot> plots, int reach, List<int[]> fields, boolean hall, int wall, NpcComposition city,
                         List<int[]> gates, List<NpcComposition.Post> posts, List<int[]> works) {
        public Layout {
            plots = List.copyOf(plots);
            fields = List.copyOf(fields);
            gates = List.copyOf(gates);
            posts = List.copyOf(posts);
            works = List.copyOf(works);
        }

        /**
         * The beds of its houses, for the citizens who live there; the bunks of soldiers do not count.
         */
        public int homes() {
            int beds = 0;
            for (NpcComposition.Plot plot : plots) {
                Optional<TemplateCatalog.Entry> entry = TemplateCatalog.get(plot.template());
                if (entry.isPresent() && !TemplateCatalog.bunks(entry.get())) {
                    beds += entry.get().beds();
                }
            }
            return beds;
        }

        /**
         * A post of its guard by name, if it has one.
         */
        public Optional<NpcComposition.Post> post(String name) {
            for (NpcComposition.Post post : posts) {
                if (post.name().equals(name) && !post.points().isEmpty()) {
                    return Optional.of(post);
                }
            }
            return Optional.empty();
        }

        /**
         * The posts of its guard inside its gates.
         */
        public List<NpcComposition.Post> gatePosts() {
            List<NpcComposition.Post> list = new ArrayList<>();
            for (NpcComposition.Post post : posts) {
                if (post.name().startsWith(GATE) && !post.points().isEmpty()) {
                    list.add(post);
                }
            }
            return list;
        }
    }

    /**
     * The side of the square of land of a settlement of a rank, in chunks.
     */
    public static int land(Tier tier) {
        return switch (tier) {
            case CAPITAL -> 0;
            case CITY -> 11;
            case TOWN -> 5;
            case VILLAGE -> 3;
            case HAMLET -> 2;
        };
    }

    /**
     * The side of the square of chunks held loaded while a settlement of a rank is raised: its land and a ring of a chunk.
     */
    public static int ticketSide(Tier tier) {
        return tier == Tier.CAPITAL ? 0 : land(tier) + 2;
    }

    /**
     * Chunks held loaded while a settlement of a rank is raised.
     */
    public static int tickets(Tier tier) {
        int side = ticketSide(tier);
        return side * side;
    }

    /**
     * The chunks of a square of a side around the chunk of a point, the west and north first; an even side leans west and north.
     */
    public static List<long[]> square(int x, int z, int side) {
        List<long[]> chunks = new ArrayList<>(side * side);
        int cx = x >> 4;
        int cz = z >> 4;
        int from = -(side / 2);
        for (int dz = 0; dz < side; dz++) {
            for (int dx = 0; dx < side; dx++) {
                chunks.add(new long[]{cx + from + dx, cz + from + dz});
            }
        }
        return chunks;
    }

    /**
     * The layout of a settlement of a rank of a kingdom.
     *
     * @param kingdom the level of the kingdom: the towns of a large one have a barracks and more fields
     * @param styled  whether the kingdom is of the styled layout; otherwise its settlements take the plain buildings
     * @param style   its architectural style
     * @param rank    the rank of the guild house of a large city
     */
    public static Layout of(Tier tier, NpcLevel kingdom, boolean styled, String style, long seed, TemplateCatalog.Rank rank, NpcRules rules) {
        return switch (tier) {
            case CITY -> city(styled, style, seed, rank, rules);
            case TOWN -> new Layout(tier, SettlementTowns.town(kingdom, styled, style, seed), SettlementTowns.TOWN_WALL,
                    SettlementTowns.townFields(kingdom), true, SettlementTowns.TOWN_WALL, null, SettlementTowns.townGates(), townPosts(),
                    List.of());
            case VILLAGE -> new Layout(tier, SettlementTowns.village(styled, style, seed), VILLAGE_REACH, SettlementTowns.villageFields(seed),
                    false, 0, null, List.of(), List.of(new NpcComposition.Post(ROUND, SettlementTowns.street(SettlementTowns.VILLAGE_ROW - 2))),
                    List.of());
            case HAMLET -> new Layout(tier, SettlementTowns.hamlet(styled, style, seed), HAMLET_REACH, SettlementTowns.hamletFields(), false,
                    0, null, List.of(), List.of(new NpcComposition.Post(ROUND, SettlementTowns.street(SettlementTowns.HAMLET_ROW - 2))),
                    List.of());
            case CAPITAL -> throw new IllegalArgumentException("A capital is laid out as a capital");
        };
    }

    /**
     * The posts of the guard of a small town: a place inside each of its gates and its round.
     */
    private static List<NpcComposition.Post> townPosts() {
        int inside = SettlementTowns.TOWN_WALL - 3;
        return List.of(new NpcComposition.Post(GATE + "west", List.of(new int[]{-inside, 1})),
                new NpcComposition.Post(GATE + "east", List.of(new int[]{inside, -1})),
                new NpcComposition.Post(ROUND, SettlementTowns.townRound()));
    }

    /**
     * A large city: the districts of a medium capital of the style of its kingdom with the residence of its governor, the small
     * castle of the style, where the palace would stand; its town hall stands in the residence as the throne stands in a castle.
     */
    private static Layout city(boolean styled, String style, long seed, TemplateCatalog.Rank rank, NpcRules rules) {
        TemplateCatalog.Rank fitting = rank == TemplateCatalog.Rank.SS ? TemplateCatalog.Rank.S : rank;
        NpcComposition city = styled ? StyledPlan.make(NpcLevel.MEDIUM, seed, style, fitting, rules, 0) : CityPlan.make(NpcLevel.MEDIUM, seed, rules, 0);
        List<NpcComposition.Plot> plots = new ArrayList<>();
        residence(styled, style).ifPresent(plots::add);
        for (NpcComposition.Plot plot : city.buildings()) {
            if (!palace(plot)) {
                plots.add(plot);
            }
        }
        // A city has 40 buildings at most (its gates, towers, decorations and wells aside): the houses last laid out stay lots.
        int buildings = 0;
        for (NpcComposition.Plot plot : plots) {
            buildings += building(plot) ? 1 : 0;
        }
        for (int i = plots.size() - 1; i >= 0 && buildings > MAX_CITY_BUILDINGS; i--) {
            if (house(plots.get(i))) {
                plots.remove(i);
                buildings--;
            }
        }
        List<int[]> gates = new ArrayList<>();
        for (NpcComposition.Gate gate : city.gates()) {
            gates.add(new int[]{gate.x(), gate.z()});
        }
        List<NpcComposition.Post> posts = new ArrayList<>();
        for (NpcComposition.Post post : city.posts()) {
            if (post.name().startsWith(GATE) && !post.points().isEmpty()) {
                posts.add(post);
            }
        }
        posts.add(new NpcComposition.Post(ROUND, cityRound(gates)));
        int fields = Math.max(0, Math.min(city.fields(), city.fieldSpots().size()));
        return new Layout(Tier.CITY, plots, city.reach(), city.fieldSpots().subList(0, fields), true, city.wallRadius(), city, gates, posts,
                city.fieldSpots().subList(fields, city.fieldSpots().size()));
    }

    /**
     * The round of the guard of a large city: inside each gate in turn, by the residence between them.
     */
    static List<int[]> cityRound(List<int[]> gates) {
        List<int[]> sorted = new ArrayList<>(gates);
        sorted.sort(Comparator.comparingDouble(gate -> Math.atan2(gate[1], gate[0])));
        List<int[]> route = new ArrayList<>();
        for (int[] gate : sorted) {
            boolean alongX = Math.abs(gate[0]) >= Math.abs(gate[1]);
            int sx = Integer.signum(gate[0]);
            int sz = Integer.signum(gate[1]);
            int[] square = alongX ? new int[]{sx * CITY_SQUARE, 0} : new int[]{0, sz * CITY_SQUARE};
            int[] inside = alongX ? new int[]{gate[0] - sx * 8, gate[1]} : new int[]{gate[0], gate[1] - sz * 8};
            route.add(square);
            route.add(inside);
        }
        return route;
    }

    /**
     * The residence of the governor of a large city: the small castle of the style of its kingdom, in the middle.
     */
    static Optional<NpcComposition.Plot> residence(boolean styled, String style) {
        if (!styled) {
            return Optional.empty();
        }
        List<TemplateCatalog.Entry> castles = TemplateCatalog.ofStyle(style, TemplateCatalog.Kind.CASTLE);
        if (castles.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new NpcComposition.Plot(castles.get(0).id(), 0, 0, 0, false, RESIDENCE));
    }

    /** Buildings of a large city at most, its gates, towers, decorations and wells aside. */
    static final int MAX_CITY_BUILDINGS = 40;

    /**
     * Whether a plot is a building of a city that counts: not a gate or tower of its wall, a decoration or a well.
     */
    public static boolean building(NpcComposition.Plot plot) {
        TemplateCatalog.Kind kind = TemplateCatalog.of(plot.template()).kind();
        return kind != TemplateCatalog.Kind.WALL_GATE && kind != TemplateCatalog.Kind.TOWER && kind != TemplateCatalog.Kind.DECORATION
                && kind != TemplateCatalog.Kind.WELL;
    }

    private static boolean house(NpcComposition.Plot plot) {
        TemplateCatalog.Kind kind = TemplateCatalog.of(plot.template()).kind();
        return kind == TemplateCatalog.Kind.POOR_HOUSE || kind == TemplateCatalog.Kind.CRAFT_HOUSE || kind == TemplateCatalog.Kind.MERCHANT_HOUSE
                || kind == TemplateCatalog.Kind.NOBLE_HOUSE;
    }

    /**
     * Whether a plot is the palace (or castle) of a capital, which a large city has not.
     */
    static boolean palace(NpcComposition.Plot plot) {
        return "palace".equals(plot.quarter()) || StyledPlan.CASTLE.equals(plot.quarter());
    }

    /**
     * The footprint of a plot around the middle of a settlement, {minX, minZ, maxX, maxZ} in blocks from it.
     */
    public static int[] footprint(NpcComposition.Plot plot) {
        return NpcComposition.footprint(plot);
    }
}
