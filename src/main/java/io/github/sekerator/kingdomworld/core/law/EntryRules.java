package io.github.sekerator.kingdomworld.core.law;

/**
 * Who pays to enter a large city and what happens at the gate. Subjects and officials of the kingdom and players of high
 * reputation enter for free; others pay the fee the king set (a little less or more by their reputation) once, and the pass
 * holds a game day in every city of the kingdom. A player who switched on paying by itself pays at the gate without a word;
 * without the money, or without that switch, the guards politely name the price and do not let them in.
 */
public final class EntryRules {

    /**
     * What happens at the gate.
     */
    public enum Decision {
        /** No fee for this player, or none at all. */
        FREE,
        /** A pass bought earlier still holds. */
        PASS,
        /** The fee is paid at once by the player's own choice. */
        PAY,
        /** The guards ask for the fee and keep the player out. */
        REFUSE
    }

    private EntryRules() {
    }

    /**
     * The fee this player pays: 0 for a member of the kingdom or a player of high reputation, else the fee of the king
     * moved by reputation.
     */
    public static long fee(KingdomLaw law, boolean member, int reputation, LawRules rules) {
        if (member || law.entryFee() <= 0 || reputation >= rules.highReputation) {
            return 0;
        }
        return Reputation.price(law.entryFee(), reputation, rules.priceEffectPercent);
    }

    /**
     * Whether the way a player went between two looks passed a gate: some point of the line between the two positions lies
     * within {@code reach} blocks of a gate cell. A runner covers more than a gate is wide between two looks, so the position
     * after crossing alone does not tell how the player came in.
     *
     * @param gates cells of gates as {x, z}
     */
    public static boolean passesGate(java.util.List<int[]> gates, int reach, double fromX, double fromZ, double toX, double toZ) {
        double length = Math.hypot(toX - fromX, toZ - fromZ);
        int steps = Math.max(1, (int) Math.ceil(length / 0.25));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.floor(fromX + (toX - fromX) * t);
            int z = (int) Math.floor(fromZ + (toZ - fromZ) * t);
            for (int[] gate : gates) {
                if (Math.abs(gate[0] - x) <= reach && Math.abs(gate[1] - z) <= reach) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Decision decide(long fee, boolean hasPass, boolean autoPay, boolean canPay) {
        if (fee <= 0) {
            return Decision.FREE;
        }
        if (hasPass) {
            return Decision.PASS;
        }
        return autoPay && canPay ? Decision.PAY : Decision.REFUSE;
    }
}
