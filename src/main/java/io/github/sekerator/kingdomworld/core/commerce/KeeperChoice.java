package io.github.sekerator.kingdomworld.core.commerce;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Who keeps a shop. Keepers are townspeople: citizens without a post of the crown (unemployed by profession), no soldiers, and
 * without a shop of their own. For a house the townspeople living in it come first, then the other townspeople of the city,
 * the nearest first. When a house has no townspeople to keep its shop but people with posts live in it, their family keeps it
 * in the evening.
 */
public final class KeeperChoice {
    private KeeperChoice() {
    }

    /**
     * A citizen who might keep a shop.
     *
     * @param townsperson whether the citizen has no post of the crown, is no soldier and has no shop
     * @param residentHere whether the citizen lives in the house of the shop
     * @param distanceSqr how far the citizen lives from the shop, squared
     */
    public record Candidate(UUID id, boolean townsperson, boolean residentHere, double distanceSqr) {
    }

    /**
     * Who keeps a place.
     *
     * @param keepers the townspeople chosen, at most the room of the place
     * @param family  whether the family of the house keeps it in the evening instead
     */
    public record Choice(List<UUID> keepers, boolean family) {
        public static final Choice NONE = new Choice(List.of(), false);

        public boolean any() {
            return family || !keepers.isEmpty();
        }
    }

    /**
     * Chooses the keepers of a place.
     *
     * @param room      how many keepers the place takes
     * @param house     whether the place is a house, whose family may keep it
     */
    public static Choice choose(List<Candidate> candidates, int room, boolean house) {
        List<Candidate> townspeople = new ArrayList<>();
        boolean residents = false;
        for (Candidate candidate : candidates) {
            if (candidate.townsperson()) {
                townspeople.add(candidate);
            } else if (candidate.residentHere()) {
                residents = true;
            }
        }
        // The people of the house first, for a house only; then the nearest.
        townspeople.sort(Comparator.comparing((Candidate candidate) -> house && !candidate.residentHere())
                .thenComparingDouble(Candidate::distanceSqr).thenComparing(Candidate::id));
        List<UUID> chosen = new ArrayList<>();
        for (Candidate candidate : townspeople) {
            if (chosen.size() >= Math.max(0, room)) {
                break;
            }
            chosen.add(candidate.id());
        }
        if (!chosen.isEmpty()) {
            return new Choice(List.copyOf(chosen), false);
        }
        return house && residents ? new Choice(List.of(), true) : Choice.NONE;
    }
}
