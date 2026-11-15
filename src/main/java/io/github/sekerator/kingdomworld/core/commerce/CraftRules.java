package io.github.sekerator.kingdomworld.core.commerce;

import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.road.CityLevel;

/**
 * The rules of the workshops and trading houses of citizens and of what the crown buys of them, from the section
 * {@code [cityEconomy]} of the server config.
 *
 * @param crownBuysFromCitizens   whether a kingdom starts buying of its citizens what its workshops cannot make
 * @param crownBuyCapPercent      the most the crown pays a kingdom starts with, in percent of the worth (50-150)
 * @param crownBuyPerDay          the most the crown spends a day on goods of citizens a kingdom starts with
 * @param crownBuyKeepWageDays    days of wages the purchases never take the treasury below
 * @param playerSellPercent       what a shop pays a player for an item the crown sells, in percent of the lower of the price of
 *                                the crown and the worth
 * @param playerSellOwnPercent    what a shop pays a player for an item the crown does not sell, in percent of its own price
 * @param outsideDemand           the demand from abroad of a village, a town and a large city, worth a day
 * @param outsideDemandPerRootPerson demand from abroad for each square root of the people of a city
 * @param outsideDemandFullCities cities of a kingdom whose demand from abroad counts in full; the next count less and less
 * @param traderBuyPercent        the most a trading house pays a master, in percent of what it gets abroad
 * @param marketDemandFactor      how much a working market raises the demand from abroad
 * @param roadLinkPercent         how much each road to another city raises the demand from abroad, in percent, up to twice
 * @param craftExport             what a trading house gets abroad in a village, a town and a large city, in percent of the worth
 * @param regionalMarket          whether townspeople also buy in the shops of the nearest larger city on a road
 * @param houseWhenUnmetPercent   above this share of their basket unbought, a head of a city wants a house of a merchant or craftsman
 */
public record CraftRules(boolean crownBuysFromCitizens, int crownBuyCapPercent, long crownBuyPerDay, int crownBuyKeepWageDays,
                         int playerSellPercent, int playerSellOwnPercent, long[] outsideDemand, int outsideDemandPerRootPerson,
                         int outsideDemandFullCities, int traderBuyPercent, double marketDemandFactor, int roadLinkPercent, int[] craftExport,
                         boolean regionalMarket, int houseWhenUnmetPercent) {
    public static final int MIN_BUY_CAP_PERCENT = 50;
    public static final int MAX_BUY_CAP_PERCENT = 150;
    /** Roads raise the demand from abroad up to this many times. */
    public static final double MAX_ROAD_FACTOR = 2.0;
    /** The share of a kind of goods a new workshop of building materials stocks, in percent of its strength. */
    public static final int MATERIALS_SHARE_PERCENT = 10;
    /** A shop sells goods to the buy list of the crown when it holds more than this many days of its sales. */
    public static final int CROWN_LIST_DAYS = 3;
    /** Days a master has to deliver work the crown paid for in advance. */
    public static final int COMMISSION_DAYS = 3;
    /** The least a shop sells above what an item cost it, in thousandths. */
    public static final int LEAST_MARGIN = ShopPricing.LEAST_MARGIN;

    public static final CraftRules DEFAULT = new CraftRules(true, 150, 500, 7, 90, 70, new long[]{0, 60, 150}, 2, 5, 80, 1.5, 25,
            new int[]{60, 70, 80}, true, 30);

    public CraftRules {
        crownBuyCapPercent = clamp(crownBuyCapPercent, MIN_BUY_CAP_PERCENT, MAX_BUY_CAP_PERCENT);
        crownBuyPerDay = Math.max(0, Math.min(Money.MAX, crownBuyPerDay));
        crownBuyKeepWageDays = clamp(crownBuyKeepWageDays, 0, 365);
        playerSellPercent = clamp(playerSellPercent, 0, 100);
        playerSellOwnPercent = clamp(playerSellOwnPercent, 0, 100);
        long[] demand = new long[3];
        for (int i = 0; i < demand.length; i++) {
            demand[i] = outsideDemand == null || i >= outsideDemand.length ? 0 : Math.max(0, Math.min(1_000_000, outsideDemand[i]));
        }
        outsideDemand = demand;
        outsideDemandPerRootPerson = clamp(outsideDemandPerRootPerson, 0, 10_000);
        outsideDemandFullCities = clamp(outsideDemandFullCities, 1, 10_000);
        traderBuyPercent = clamp(traderBuyPercent, 0, 100);
        marketDemandFactor = Double.isFinite(marketDemandFactor) ? Math.max(1.0, Math.min(10.0, marketDemandFactor)) : 1.0;
        roadLinkPercent = clamp(roadLinkPercent, 0, 100);
        int[] export = new int[3];
        for (int i = 0; i < export.length; i++) {
            export[i] = craftExport == null || i >= craftExport.length ? 0 : clamp(craftExport[i], 0, 100);
        }
        craftExport = export;
        houseWhenUnmetPercent = clamp(houseWhenUnmetPercent, 0, 100);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public long[] outsideDemand() {
        return outsideDemand.clone();
    }

    @Override
    public int[] craftExport() {
        return craftExport.clone();
    }

    private static int index(CityLevel level) {
        return level == CityLevel.CITY ? 2 : level == CityLevel.TOWN ? 1 : 0;
    }

    /**
     * The demand from abroad of a city of a level, before its people, roads and market.
     */
    public long outsideDemand(CityLevel level) {
        return outsideDemand[index(level)];
    }

    /**
     * What a trading house gets abroad for goods of a worth, in percent, by the level of its city.
     */
    public int craftExportPercent(CityLevel level) {
        return craftExport[index(level)];
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CraftRules that && toString().equals(that.toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public String toString() {
        return "CraftRules[" + crownBuysFromCitizens + "," + crownBuyCapPercent + "," + crownBuyPerDay + "," + crownBuyKeepWageDays + ","
                + playerSellPercent + "," + playerSellOwnPercent + "," + java.util.Arrays.toString(outsideDemand) + "," + outsideDemandPerRootPerson
                + "," + outsideDemandFullCities + "," + traderBuyPercent + "," + marketDemandFactor + "," + roadLinkPercent + ","
                + java.util.Arrays.toString(craftExport) + "," + regionalMarket + "," + houseWhenUnmetPercent + "]";
    }
}
