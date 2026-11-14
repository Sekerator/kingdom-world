package io.github.sekerator.kingdomworld.core.commerce;

/**
 * How well the people of a city live by their money, a part of the mood of every citizen of the city from
 * {@value #MIN} to {@value #MAX}: the burden of its taxes, the shops of its own where they found what they wanted, a treasury
 * the crown took from them, and a city with a head that sends nearly everything to the crown. Shops only add, so that old worlds
 * lose no mood; with the default taxes and no shops it is 0.
 *
 * @param burden the part of the burden of the taxes
 * @param shops  the part of their own shops
 * @param recall the part of a treasury taken
 * @param share  the part of a share of the crown that leaves the city nothing
 * @param value  all together, within the bounds
 */
public record CityMeans(int burden, int shops, int recall, int share, int value) {
    public static final int MIN = -15;
    public static final int MAX = 8;
    public static final CityMeans NONE = new CityMeans(0, 0, 0, 0, 0);

    /**
     * The part of the burden of the taxes: +3 at 15 percent and less, 0 up to 25, -5 up to 35, -10 up to 45, -15 above.
     */
    public static int fromBurden(int burdenPermille) {
        if (burdenPermille <= 150) {
            return 3;
        }
        if (burdenPermille <= 250) {
            return 0;
        }
        if (burdenPermille <= 350) {
            return -5;
        }
        if (burdenPermille <= 450) {
            return -10;
        }
        return -15;
    }

    /**
     * The part of their own shops: +5 when yesterday the townspeople bought 90 percent of their basket in the shops of their
     * city, +2 at 60 percent.
     */
    public static int fromShops(int basketPercent) {
        return basketPercent >= 90 ? 5 : basketPercent >= 60 ? 2 : 0;
    }

    /**
     * The means of the people of a city.
     *
     * @param recalled          whether the crown took its treasury in the last days
     * @param hasHead           whether the city has a head
     * @param crownSharePermille the share of the crown of its taxes
     */
    public static CityMeans of(int burdenPermille, int basketPercent, boolean recalled, boolean hasHead, int crownSharePermille,
                               int fairPercent) {
        int burden = fromBurden(burdenPermille);
        int shops = fromShops(basketPercent);
        int recall = recalled ? -5 : 0;
        int share = hasHead && crownSharePermille > fairPercent * 10 ? -3 : 0;
        int value = Math.max(MIN, Math.min(MAX, burden + shops + recall + share));
        return new CityMeans(burden, shops, recall, share, value);
    }
}
