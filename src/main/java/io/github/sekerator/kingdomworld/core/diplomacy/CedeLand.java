package io.github.sekerator.kingdomworld.core.diplomacy;

import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;

import java.util.UUID;

/**
 * What the land says of a province asked for in a cession, for the rules of diplomacy, which know no land of their own: who
 * holds it, whether the capital of its holder stands in it, and whether it touches the land of the kingdom that asks for
 * it. The server reads it from the territory of the world when the offer is made and again when it is accepted; {@code null}
 * stands for a province that does not exist.
 *
 * @param holder  the kingdom that holds the province, {@code null} for nobody
 * @param heart   whether the capital of its holder stands in it: the heart of a state is ceded only by an annexation
 * @param borders whether a neighbour province or a claimed chunk of the asking kingdom touches it
 */
public record CedeLand(UUID holder, boolean heart, boolean borders) {
    /**
     * Whether a kingdom may be asked for a province, as far as the land goes: the province exists, the kingdom holds it, it
     * is not its heart and it touches the land of the one who asks.
     *
     * @param to   the kingdom asked to cede it
     * @param land what the land says of the province, {@code null} when there is no such province
     */
    public static Outcome check(UUID to, int province, CedeLand land) {
        if (province < 1 || land == null) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (!to.equals(land.holder())) {
            return Outcome.fail(KingdomError.NOT_OWNED);
        }
        if (land.heart()) {
            return Outcome.fail(KingdomError.CAPITAL_CHUNK);
        }
        if (!land.borders()) {
            return Outcome.fail(KingdomError.NOT_ADJACENT);
        }
        return Outcome.DONE;
    }
}
