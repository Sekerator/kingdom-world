package io.github.sekerator.kingdomworld.core.army;

import io.github.sekerator.kingdomworld.core.economy.Money;

/**
 * Trophies of war: the gear soldiers take from the fallen of the enemy. After peace, the kingdom the gear belonged to may buy
 * it back for a share of its worth within some days; what it does not buy back stays with the kingdom that took it.
 *
 * @param percent the share of the worth the buyback costs, in percent
 * @param days    game days after peace within which the gear may be bought back
 */
public record TrophyRules(int percent, int days) {
    public static final TrophyRules DEFAULT = new TrophyRules(50, 7);

    public TrophyRules {
        if (percent < 0 || percent > 1_000 || days < 0 || days > 3_650) {
            throw new IllegalArgumentException("Trophy rules out of range");
        }
    }

    /**
     * What buying back gear of some worth costs.
     */
    public long price(long worth) {
        return worth <= 0 ? 0 : Money.share(worth, percent, 100);
    }

    /**
     * Whether gear may be bought back today.
     *
     * @param peaceDay the day the war between the two kingdoms ended, 0 while it goes on
     */
    public boolean open(long peaceDay, long today) {
        return peaceDay > 0 && today >= peaceDay && today <= peaceDay + days;
    }

    /**
     * Whether the time to buy back is over, and the gear stays with the kingdom that took it.
     */
    public boolean expired(long peaceDay, long today) {
        return peaceDay > 0 && today > peaceDay + days;
    }
}
