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
    UNKNOWN_ANIMAL;

    /**
     * Lower case identifier, used for translation keys.
     */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
