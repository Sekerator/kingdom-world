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
    UNKNOWN_PROFESSION;

    /**
     * Lower case identifier, used for translation keys.
     */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
