package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.land.TerrainClass;
import io.github.sekerator.kingdomworld.core.land.Tier;

import java.util.SplittableRandom;

/**
 * The life of a settlement in numbers while it is data, a day at a time: its people grow toward its houses while they have
 * food, its food is what its land yields less what its people eat, hunger takes people and contentment, and its guard and
 * militia follow its rank and people. Only numbers: nothing here becomes money or goods.
 */
public final class SettlementLife {
    /** How much the people grow in a day while fed and below the houses: a fiftieth. */
    public static final double GROWTH = 0.02;
    /** Days of food a settlement starts with. */
    public static final int START_FOOD_DAYS = 10;
    /** Days of food a settlement keeps at most. */
    public static final int MAX_FOOD_DAYS = 30;
    public static final int START_PROSPERITY = 50;
    public static final int MAX_PROSPERITY = 100;

    /**
     * What a day leaves of a settlement.
     */
    public record Day(int population, int food, int guards, int militia, int prosperity) {
    }

    private SettlementLife() {
    }

    /**
     * The people of a rank, least and most; the seed picks between them. A capital is counted by its own people.
     */
    public static int[] people(Tier tier) {
        return switch (tier) {
            case CITY -> new int[]{80, 150};
            case TOWN -> new int[]{25, 45};
            case VILLAGE -> new int[]{10, 25};
            case HAMLET -> new int[]{4, 8};
            case CAPITAL -> new int[]{0, 0};
        };
    }

    /**
     * The houses of a settlement of a rank and seed: how many people it may hold.
     */
    public static int housing(Tier tier, long seed) {
        int[] range = people(tier);
        return range[1] == 0 ? 0 : new SplittableRandom(seed ^ 0x484F555345L).nextInt(range[0], range[1] + 1);
    }

    /**
     * The people a settlement starts with: three quarters of its houses.
     */
    public static int startingPopulation(int housing) {
        return (int) Math.round(housing * 0.75);
    }

    /**
     * The guard of a rank: a city keeps 8, a town 4, a village and a hamlet none.
     */
    public static int guards(Tier tier) {
        return switch (tier) {
            case CITY -> 8;
            case TOWN -> 4;
            default -> 0;
        };
    }

    /**
     * The soldiers who serve a standing settlement of a rank in person, come from abroad: the guard of a large city (8) and of a
     * small town (4), the watchmen of a village (2) and of a hamlet (1). The watchmen of a village stand beside its militia,
     * which stays a number.
     */
    public static int watch(Tier tier) {
        return switch (tier) {
            case CITY -> 8;
            case TOWN -> 4;
            case VILLAGE -> 2;
            case HAMLET -> 1;
            case CAPITAL -> 0;
        };
    }

    /**
     * The squads of the guard of a standing settlement of a rank: a large city its gate, its round and its town hall, a small town
     * its gate and its round, a village and a hamlet the round of their watch.
     */
    public static int squads(Tier tier) {
        return switch (tier) {
            case CITY -> 3;
            case TOWN -> 2;
            case VILLAGE, HAMLET -> 1;
            case CAPITAL -> 0;
        };
    }

    /**
     * The soldiers and squads a kingdom of the world keeps at most with all its settlements standing: the guard of the capital,
     * of every large city and small town, the watch of every village and hamlet, and the royal army; a squad for every post of
     * the capital, the squads of the settlements and one for every squad of the army.
     *
     * @param capitalPosts the posts of the capital, those of its army in reserve among them
     * @param villages     villages and hamlets, all counted as villages
     * @param inCapital    soldiers of the army who stay in the capital, in its reserve
     * @param squad        soldiers of a squad of the army beyond the capital
     * @return {soldiers, squads}
     */
    public static int[] garrison(int capitalGuards, int capitalPosts, int cities, int towns, int villages, int army, int inCapital, int squad) {
        int soldiers = capitalGuards + cities * watch(Tier.CITY) + towns * watch(Tier.TOWN) + villages * watch(Tier.VILLAGE) + army;
        int squads = capitalPosts + cities * squads(Tier.CITY) + towns * squads(Tier.TOWN) + villages * squads(Tier.VILLAGE)
                + (Math.max(0, army - inCapital) + squad - 1) / squad;
        return new int[]{soldiers, squads};
    }

    /**
     * The militia: in a village a tenth of its people, elsewhere none.
     */
    public static int militia(Tier tier, int population) {
        return tier == Tier.VILLAGE ? population / 10 : 0;
    }

    /**
     * What a person of the land of a class grows in a day, in hundredths of what a person eats.
     */
    public static int yield(TerrainClass terrain) {
        return switch (terrain) {
            case LAND -> 130;
            case RIVER -> 125;
            case HILLS -> 110;
            case BEACH, LAKE -> 100;
            case OCEAN -> 90;
            case SWAMP -> 80;
            case STEEP -> 60;
            case DESERT -> 50;
            case UNKNOWN -> 100;
        };
    }

    /**
     * A day of a settlement.
     *
     * @param fertility what a person grows in a day, in hundredths of what a person eats
     */
    public static Day day(Tier tier, int population, int housing, int food, int prosperity, int fertility) {
        int people = Math.max(0, population);
        long harvest = (long) people * Math.max(0, fertility) / 100;
        long stock = (long) food + harvest - people;
        int prosper = prosperity;
        if (stock < 0) {
            // Hunger: some go away, the rest grow discontent; there is nothing to eat left.
            people = Math.max(0, people - Math.max(1, (int) Math.round(people * GROWTH)));
            prosper -= 3;
            stock = 0;
        } else if (people < housing) {
            people = Math.min(housing, people + Math.max(1, (int) Math.round(people * GROWTH)));
            prosper += 1;
        }
        stock = Math.min(stock, (long) Math.max(people, housing) * MAX_FOOD_DAYS);
        prosper = Math.max(0, Math.min(MAX_PROSPERITY, prosper));
        return new Day(people, (int) stock, guards(tier), militia(tier, people), prosper);
    }
}
