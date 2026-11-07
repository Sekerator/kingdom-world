package io.github.sekerator.kingdomworld.core.kingdom;

import java.util.Locale;

/**
 * Why an operation on a kingdom, its members or its territory was refused. Naming problems of new kingdoms
 * are reported separately by {@link CreateKingdomResult.Error}.
 */
public enum KingdomError {
    UNKNOWN_KINGDOM,
    /** The saved data could not be fully understood, so changes are refused to protect it. */
    READ_ONLY,

    ALREADY_IN_KINGDOM,
    NOT_IN_KINGDOM,
    ALREADY_APPLIED,
    ALREADY_INVITED,
    NO_APPLICATION,
    NO_INVITATION,
    NO_PERMISSION,
    NOT_A_MEMBER,
    TARGET_IS_KING,
    TARGET_IS_SELF,
    KING_CANNOT_LEAVE,
    TOO_MANY_PENDING,
    ROLE_NOT_ASSIGNABLE,
    PERMISSION_NOT_EDITABLE,
    OUTRANKED,
    UNCHANGED,

    NO_CAPITAL,
    WRONG_DIMENSION,
    ALREADY_OWNED,
    OWNED_BY_OTHER,
    NOT_ADJACENT,
    TOO_CLOSE_TO_OTHER_KINGDOM,
    LIMIT_REACHED,
    NOT_OWNED,
    CAPITAL_CHUNK,
    WOULD_DISCONNECT,

    UNKNOWN_CITIZEN,
    UNKNOWN_PROFESSION,

    UNKNOWN_POST,
    /** The profession does not work at work posts. */
    NOT_A_POST_PROFESSION,
    /** A setting of a work post outside the range the server allows. */
    VALUE_OUT_OF_RANGE,
    /** The zone of a work post would reach beyond the kingdom's land. */
    ZONE_OUTSIDE_LAND,
    /** The post has no free seat. */
    NO_FREE_SEAT,
    /** Nobody without work is there to hire. */
    NO_UNEMPLOYED,
    /** The citizen does not work at that post. */
    NOT_A_WORKER,
    /** The animal is not one that lives in the zone of the post. */
    UNKNOWN_ANIMAL,

    UNKNOWN_ORDER,
    /** The kingdom has as many orders as it may. */
    TOO_MANY_ORDERS,
    /** No citizen can make the item. */
    NOT_ORDERABLE,
    /** Orders the planner added for ingredients follow their parent and are not changed by hand. */
    SUB_ORDER,

    /** The account or the treasury holds less than the amount. */
    NOT_ENOUGH_MONEY,
    /** The amount would make a balance larger than the largest amount of money. */
    TOO_MUCH_MONEY,
    /** No current or old currency has that id or name. */
    UNKNOWN_CURRENCY,
    /** The currency belongs to a dissolved kingdom; its coins and accounts wait for an exchange. */
    OLD_CURRENCY,
    /** The name of a currency follows the rules of kingdom names. */
    INVALID_CURRENCY_NAME,
    CURRENCY_NAME_TAKEN,
    /** There are no coins of the currency to hand in. */
    NO_COINS,
    /** The inventory has no room for coins. */
    NO_ROOM,
    /** A bank works only on the land of a kingdom. */
    BANK_OUTSIDE,
    /** The amount needs confirmation first. */
    NOT_CONFIRMED,

    /** The treasury has too little money for this. */
    TREASURY_EMPTY,
    /** A currency cannot be exchanged for itself. */
    SAME_CURRENCY,
    /** The amount is too small to give anything. */
    TOO_LITTLE,
    /** A list of the market is full. */
    LIST_FULL,
    /** The market has no such entry. */
    UNKNOWN_ENTRY,
    /** No merchant works at this stall. */
    MARKET_CLOSED,
    /** The market is closed to outsiders. */
    OUTSIDERS_REFUSED,
    /** The kingdom does not sell this item. */
    NOT_FOR_SALE,
    /** The kingdom does not buy this item. */
    NOT_BOUGHT,
    /** The storehouses hold too few of the item. */
    OUT_OF_STOCK,
    /** The money for buying this item today is spent. */
    BUDGET_SPENT,
    /** The kingdom holds as many of the item as it wants. */
    ENOUGH_STOCK,
    /** The player has as many lots on this market as allowed. */
    TOO_MANY_LOTS,
    /** The lot is no longer on the market. */
    UNKNOWN_LOT,
    /** The market takes only the kingdom's own currency. */
    FOREIGN_CURRENCY_REFUSED,
    /** Only items with a worth in the config, and not the rare ones, come from abroad. */
    NOT_IMPORTABLE,
    /** The kingdom ordered as much from abroad today as allowed. */
    IMPORT_LIMIT,
    UNKNOWN_DEAL,
    /** The kingdom has as many open deals as allowed. */
    TOO_MANY_DEALS,
    /** The deal is over or was answered already. */
    DEAL_CLOSED,
    /** The deal is not the kingdom's to answer or call off. */
    NOT_PARTNER,
    /** A kingdom cannot trade with itself. */
    SAME_KINGDOM,
    /** The storehouses of the seller hold too few of the goods. */
    NOT_ENOUGH_GOODS,
    /** The storehouses have no room for the goods. */
    STOREHOUSES_FULL,
    /** The player carries no such items. */
    NO_ITEMS,
    /** Only subjects of the kingdom put up lots. */
    NOT_SUBJECT,
    /** The item cannot be traded: unknown, coins, or with data the market does not take. */
    BAD_ITEM;

    /**
     * Lower case identifier, used for translation keys.
     */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
