package io.github.sekerator.kingdomworld.core.commerce;

import io.github.sekerator.kingdomworld.core.economy.Money;

/**
 * How much of the taxes of a city its people evade. The burden is the sales tax and the tithe of the city together; above a
 * start, every point of it makes them evade two points more, up to a most. What is not paid stays with the payer: the sales
 * tax in the till of the shop, the tithe with the shopkeeper.
 *
 * <p>The most is high on purpose: with a lower one the collected tax {@code burden x (1 - evasion)} would grow again with the
 * heaviest taxes. At 90 percent it has a single top near 37.5 percent of burden, and at 75 percent less is collected than at 25.
 */
public final class Evasion {
    private Evasion() {
    }

    /**
     * The share of the taxes the people evade, in thousandths.
     *
     * @param burdenPermille the sales tax and the tithe of the city together, in thousandths
     */
    public static int permille(int burdenPermille, int startPercent, int maxPercent) {
        long over = Math.max(0L, (long) burdenPermille - startPercent * 10L);
        return (int) Math.min(maxPercent * 10L, over * 2);
    }

    /**
     * The share of the taxes the people evade, rounded to whole percent, for views.
     */
    public static int percent(int burdenPermille, int startPercent, int maxPercent) {
        return (permille(burdenPermille, startPercent, maxPercent) + 5) / 10;
    }

    /**
     * What of a tax is paid when the people evade a share of it.
     */
    public static long collected(long tax, int evasionPermille) {
        if (tax <= 0) {
            return 0;
        }
        return Money.share(tax, 1000 - Math.max(0, Math.min(1000, evasionPermille)), 1000);
    }
}
