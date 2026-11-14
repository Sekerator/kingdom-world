package io.github.sekerator.kingdomworld.core.economy;

/**
 * The rules of the money of the cities of the kingdoms of players and of the shops of their citizens, from the section
 * {@code [cityEconomy]} of the server config.
 *
 * @param enabled               whether wages stay in the cities, cities keep treasuries and citizens keep shops; without it
 *                              wages leave the world as before
 * @param salesTaxPercent       the tax on the sales of shops a kingdom starts with, in percent (0-25)
 * @param tithePercent          the tithe of the earnings of shopkeepers a kingdom starts with, in percent (0-50)
 * @param crownSharePercent     the share of the taxes of a city its treasury sends to the crown a kingdom starts with (0-100)
 * @param idleReserveDays       days of its own share a city without a head keeps
 * @param headReserveDays       days of income the head of a city never spends
 * @param cityReserveDays       days of its own share a city with a head keeps besides what it saves for
 * @param crownShareFairPercent above this share of the crown the people of a city with a head feel it keeps nothing
 * @param purseKeepDays         days of what comes into the purse of the townspeople it keeps; more leaves the world
 * @param afarPercent           the share of the purse the townspeople spend on goods from afar
 * @param basket                the shares of the purse they spend on food, clothing, tools, household goods and luxury
 * @param businessUpkeepPercent the share of the takings of a shop that its upkeep takes out of the world
 * @param turnoverPerWorker     the worth one shopkeeper sells a day
 * @param familyShopPercent     the strength of a shop the family of a house keeps in the evening
 * @param fairIncomePercent     the income a shopkeeper aims at, in percent of its usual income
 * @param minMarginPercent      the smallest margin of a shop
 * @param maxMarginPercent      the largest margin of a shop
 * @param marginStepPercent     points the margin moves a day at most
 * @param keeperUsualIncome     what a shopkeeper usually earns a day
 * @param closeAfterDays        days of poor earnings after which a shop closes, and a place where one closed waits
 * @param shopCredit            the worth of the goods the crown gives a new shop on credit
 * @param takeFromCrownPercent  the share of the free stock of an item all shops of a kingdom take a day
 * @param stockDays             days of sales a shop keeps in stock
 * @param tavernEveningPrice    what an evening at a tavern of the crown costs a citizen
 * @param evasionStartPercent   the burden of the taxes of a city above which its people start to evade them
 * @param evasionMaxPercent     the most of the taxes they evade
 */
public record CityEconomyRules(boolean enabled, int salesTaxPercent, int tithePercent, int crownSharePercent, int idleReserveDays,
                               int headReserveDays, int cityReserveDays, int crownShareFairPercent, int purseKeepDays, int afarPercent,
                               int[] basket, int businessUpkeepPercent, long turnoverPerWorker, int familyShopPercent,
                               int fairIncomePercent, int minMarginPercent, int maxMarginPercent, int marginStepPercent,
                               long keeperUsualIncome, int closeAfterDays, long shopCredit, int takeFromCrownPercent, int stockDays,
                               long tavernEveningPrice, int evasionStartPercent, int evasionMaxPercent) {
    public static final int MAX_SALES_TAX_PERCENT = 25;
    public static final int MAX_TITHE_PERCENT = 50;
    public static final int MAX_CROWN_SHARE_PERCENT = 100;

    public static final CityEconomyRules DEFAULT = new CityEconomyRules(true, 10, 10, 50, 7, 3, 30, 75, 3, 10,
            new int[]{35, 15, 10, 20, 10}, 5, 150, 50, 120, 5, 50, 2, 5, 7, 100, 25, 3, 1, 25, 90);

    public CityEconomyRules {
        salesTaxPercent = clamp(salesTaxPercent, 0, MAX_SALES_TAX_PERCENT);
        tithePercent = clamp(tithePercent, 0, MAX_TITHE_PERCENT);
        crownSharePercent = clamp(crownSharePercent, 0, MAX_CROWN_SHARE_PERCENT);
        idleReserveDays = clamp(idleReserveDays, 0, 365);
        headReserveDays = clamp(headReserveDays, 0, 365);
        cityReserveDays = clamp(cityReserveDays, 0, 365);
        crownShareFairPercent = clamp(crownShareFairPercent, 0, 100);
        purseKeepDays = clamp(purseKeepDays, 0, 365);
        afarPercent = clamp(afarPercent, 0, 100);
        int[] shares = new int[5];
        for (int i = 0; i < shares.length; i++) {
            shares[i] = basket == null || i >= basket.length ? 0 : clamp(basket[i], 0, 100);
        }
        basket = shares;
        businessUpkeepPercent = clamp(businessUpkeepPercent, 0, 100);
        turnoverPerWorker = Math.max(0, Math.min(Money.MAX, turnoverPerWorker));
        familyShopPercent = clamp(familyShopPercent, 0, 100);
        fairIncomePercent = clamp(fairIncomePercent, 0, 1_000);
        minMarginPercent = clamp(minMarginPercent, 0, 1_000);
        maxMarginPercent = clamp(maxMarginPercent, minMarginPercent, 1_000);
        marginStepPercent = clamp(marginStepPercent, 0, 1_000);
        keeperUsualIncome = Math.max(0, Math.min(Money.MAX, keeperUsualIncome));
        closeAfterDays = clamp(closeAfterDays, 1, 365);
        shopCredit = Math.max(0, Math.min(Money.MAX, shopCredit));
        takeFromCrownPercent = clamp(takeFromCrownPercent, 0, 100);
        stockDays = clamp(stockDays, 0, 365);
        tavernEveningPrice = Math.max(0, Math.min(Money.MAX, tavernEveningPrice));
        evasionStartPercent = clamp(evasionStartPercent, 0, 100);
        evasionMaxPercent = clamp(evasionMaxPercent, 0, 100);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public int[] basket() {
        return basket.clone();
    }

    /**
     * The share of the basket of a kind of goods, in percent.
     */
    public int basketShare(int category) {
        return category < 0 || category >= basket.length ? 0 : basket[category];
    }

    /**
     * The income a shopkeeper aims at.
     */
    public long fairIncome() {
        return Money.share(keeperUsualIncome, fairIncomePercent, 100);
    }

    public CityEconomyRules withEnabled(boolean value) {
        return new CityEconomyRules(value, salesTaxPercent, tithePercent, crownSharePercent, idleReserveDays, headReserveDays,
                cityReserveDays, crownShareFairPercent, purseKeepDays, afarPercent, basket, businessUpkeepPercent, turnoverPerWorker,
                familyShopPercent, fairIncomePercent, minMarginPercent, maxMarginPercent, marginStepPercent, keeperUsualIncome,
                closeAfterDays, shopCredit, takeFromCrownPercent, stockDays, tavernEveningPrice, evasionStartPercent, evasionMaxPercent);
    }

    public CityEconomyRules withTaxes(int salesTax, int tithe, int crownShare) {
        return new CityEconomyRules(enabled, salesTax, tithe, crownShare, idleReserveDays, headReserveDays, cityReserveDays,
                crownShareFairPercent, purseKeepDays, afarPercent, basket, businessUpkeepPercent, turnoverPerWorker, familyShopPercent,
                fairIncomePercent, minMarginPercent, maxMarginPercent, marginStepPercent, keeperUsualIncome, closeAfterDays, shopCredit,
                takeFromCrownPercent, stockDays, tavernEveningPrice, evasionStartPercent, evasionMaxPercent);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CityEconomyRules that && toString().equals(that.toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public String toString() {
        return "CityEconomyRules[" + enabled + "," + salesTaxPercent + "," + tithePercent + "," + crownSharePercent + "," + idleReserveDays
                + "," + headReserveDays + "," + cityReserveDays + "," + crownShareFairPercent + "," + purseKeepDays + "," + afarPercent + ","
                + java.util.Arrays.toString(basket) + "," + businessUpkeepPercent + "," + turnoverPerWorker + "," + familyShopPercent + ","
                + fairIncomePercent + "," + minMarginPercent + "," + maxMarginPercent + "," + marginStepPercent + "," + keeperUsualIncome
                + "," + closeAfterDays + "," + shopCredit + "," + takeFromCrownPercent + "," + stockDays + "," + tavernEveningPrice + ","
                + evasionStartPercent + "," + evasionMaxPercent + "]";
    }
}
