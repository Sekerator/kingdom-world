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
    NOT_CONFIRMED;

    /**
     * Lower case identifier, used for translation keys.
     */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
