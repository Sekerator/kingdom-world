package io.github.sekerator.kingdomworld.core.request;

/**
 * Why a client request was refused.
 */
public enum Rejection {
    /** The request did not come from a player. */
    NO_SENDER,
    /** The player has already disconnected. */
    NOT_CONNECTED,
    /** The player sent too many requests of this kind recently. */
    RATE_LIMITED,
    /** The player lacks the required permission level. */
    INSUFFICIENT_PERMISSION,
    /** A numeric field is outside its allowed range. */
    VALUE_OUT_OF_RANGE,
    /** A text field is missing or longer than allowed. */
    TEXT_TOO_LONG,
    /** A collection field is missing or has too many elements. */
    COLLECTION_TOO_LARGE
}
