package io.github.sekerator.kingdomworld.core.economy;

import java.util.Locale;
import java.util.Optional;

/**
 * What the money of a treasury came in for or went out for.
 */
public enum LedgerCategory {
    /** Taxes of the subjects, and their debts paid back. */
    TAXES,
    /** Wages of the citizens. */
    WAGES,
    /** Coins struck by the mint from metal of the storehouses. */
    MINTING,
    /** Payments between the treasury and players. */
    TRANSFERS,
    /** Changes made by an operator. */
    ADMIN,
    /** Buying and selling on the market, abroad and between kingdoms. */
    TRADE,
    /** Currencies exchanged at the bank of the kingdom. */
    EXCHANGE,
    /** Rewards of guild contracts, the guild's commission and its fees. */
    GUILD,
    /** Pay of mercenaries. */
    ARMY,
    MAGIC,
    /** Slaves bought and sold back on the market of the guild, wages of free companions. */
    COMPANIONS,
    /** Fines, debts paid back from income, ransoms of confiscated goods, entry fees of cities, coins of the executed. */
    LAW,
    /** The money a kingdom of the world has when it appears, like the chests of villages. */
    FOUNDING,
    /** Taverns, rooms and rumors, festivals and the goods of fairs. */
    LIFE,
    /** Tributes, indemnities and the loot of wars. */
    WAR;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<LedgerCategory> byId(String id) {
        for (LedgerCategory category : values()) {
            if (category.id().equals(id)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }
}
