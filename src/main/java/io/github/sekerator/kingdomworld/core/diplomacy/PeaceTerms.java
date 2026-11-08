package io.github.sekerator.kingdomworld.core.diplomacy;

import java.util.UUID;

/**
 * The terms a war ends on. The loser pays: a sum from its treasury at once, a share of its daily income for some days, or
 * becomes the vassal of the winner; a vassal that wins its war for independence goes free. Land, a city and the throne are
 * recorded as claims and handed to whoever carries them out in the world. A white peace has no winner and no terms.
 *
 * @param winner       the side that gains, {@code null} for a white peace
 * @param indemnity    a sum the loser pays at once, in its own currency
 * @param tributeShare the share of its daily income the loser pays, in permille
 * @param tributeDays  for how many game days the loser pays tribute
 * @param vassalage    whether the loser becomes the vassal of the winner
 * @param independence whether the vassal goes free
 * @param landChunks   chunks of the loser's land the winner claims
 * @param city         the name of a city the winner claims, empty for none
 * @param throne       whether the winner claims the throne of the loser
 */
public record PeaceTerms(UUID winner, long indemnity, int tributeShare, int tributeDays, boolean vassalage, boolean independence,
                         int landChunks, String city, boolean throne) {
    public static final PeaceTerms WHITE = new PeaceTerms(null, 0, 0, 0, false, false, 0, "", false);
    /** Largest share of income a tribute takes, in permille. */
    public static final int MAX_SHARE = 500;
    /** Most days of tribute. */
    public static final int MAX_DAYS = 30;
    /** Most chunks one peace may claim. */
    public static final int MAX_CHUNKS = 64;

    public PeaceTerms {
        city = city == null ? "" : city;
        if (indemnity < 0 || tributeShare < 0 || tributeShare > MAX_SHARE || tributeDays < 0 || tributeDays > MAX_DAYS || landChunks < 0
                || landChunks > MAX_CHUNKS || city.length() > 64) {
            throw new IllegalArgumentException("Terms of peace out of range");
        }
        if ((tributeShare == 0) != (tributeDays == 0)) {
            tributeShare = 0;
            tributeDays = 0;
        }
        if (winner == null && (indemnity > 0 || tributeShare > 0 || vassalage || independence || landChunks > 0 || !city.isEmpty() || throne)) {
            throw new IllegalArgumentException("A white peace has no terms");
        }
    }

    public boolean isWhite() {
        return winner == null;
    }

    /**
     * Whether the terms claim something in the world: land, a city or the throne.
     */
    public boolean claims() {
        return landChunks > 0 || !city.isEmpty() || throne;
    }

    public PeaceTerms withIndemnity(long value) {
        return new PeaceTerms(winner, Math.max(0, value), tributeShare, tributeDays, vassalage, independence, landChunks, city, throne);
    }

    /**
     * How heavy the terms are for the loser, from 0 (white) to about 100 (the goal in full), to weigh them against the
     * score of a war.
     *
     * @param loserFunds what the loser's treasury holds
     */
    public int weight(long loserFunds) {
        if (isWhite()) {
            return 0;
        }
        double weight = 10;
        if (indemnity > 0) {
            weight += 60.0 * Math.min(1.0, indemnity / (double) Math.max(1, loserFunds));
        }
        weight += tributeShare / 1_000.0 * tributeDays * 8.0;
        if (vassalage) {
            weight += 70;
        }
        if (independence) {
            weight += 50;
        }
        weight += landChunks * 3.0;
        if (!city.isEmpty()) {
            weight += 50;
        }
        if (throne) {
            weight += 90;
        }
        return (int) Math.min(100, Math.round(weight));
    }

    /**
     * The terms a winner asks for its goal in full.
     *
     * @param loserFunds what the loser's treasury holds now
     */
    public static PeaceTerms forGoal(WarGoal goal, UUID winner, boolean winnerAttacked, long loserFunds, DiplomacyRules rules) {
        if (!winnerAttacked) {
            // A defender that wins asks for its costs back.
            return new PeaceTerms(winner, share(loserFunds, rules.indemnityShare() / 2), 0, 0, false, false, 0, "", false);
        }
        return switch (goal) {
            case TRIBUTE -> new PeaceTerms(winner, 0, 0, 0, true, false, 0, "", false);
            case INDEMNITY -> new PeaceTerms(winner, share(loserFunds, rules.indemnityShare()), 0, 0, false, false, 0, "", false);
            case RAID -> new PeaceTerms(winner, share(loserFunds, rules.indemnityShare() / 3), 0, 0, false, false, 0, "", false);
            case LAND -> new PeaceTerms(winner, 0, 0, 0, false, false, rules.landChunks(), "", false);
            case CITY -> new PeaceTerms(winner, 0, 0, 0, false, false, 0, "?", false);
            case THRONE -> new PeaceTerms(winner, 0, 0, 0, false, false, 0, "", true);
            case INDEPENDENCE -> new PeaceTerms(winner, 0, 0, 0, false, true, 0, "", false);
        };
    }

    /**
     * Smaller terms than the goal: what a side may ask without a clear lead in the war. Vassalage becomes a tribute for some
     * days, the sums and the land are halved, a city and the throne are not asked.
     */
    public PeaceTerms halved(DiplomacyRules rules) {
        if (isWhite()) {
            return this;
        }
        int share = vassalage ? rules.tributeShare() / 2 : tributeShare / 2;
        int days = share > 0 ? (vassalage ? rules.tributeDays() : tributeDays) : 0;
        return new PeaceTerms(winner, indemnity / 2, share, days, false, independence, landChunks / 2, "", false);
    }

    /**
     * The same terms with a tribute for some days instead of vassalage, where vassalage cannot be.
     */
    public PeaceTerms withoutVassalage(DiplomacyRules rules) {
        if (!vassalage) {
            return this;
        }
        return new PeaceTerms(winner, indemnity, Math.max(tributeShare, rules.tributeShare()), Math.max(tributeDays, rules.tributeDays()), false,
                independence, landChunks, city, throne);
    }

    static long share(long funds, int permille) {
        if (funds <= 0 || permille <= 0) {
            return 0;
        }
        return Math.max(0, (long) Math.floor(funds / 1_000.0 * permille));
    }
}
