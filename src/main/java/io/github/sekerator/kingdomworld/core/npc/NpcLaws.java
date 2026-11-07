package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.law.KingdomLaw;

import java.util.List;

/**
 * The laws a ruler of a kingdom of the world sets when its kingdom appears, by its leaning and traits: a merchant asks more at
 * the gates of large cities and a just ruler less; a warrior fines harder and pays more for the heads of the wanted; a just
 * ruler bans slavery on its land.
 *
 * @param fineMultiplierPermille how hard fines are, in thousandths of the usual
 * @param entryFee               what entering a large city costs, in the smallest unit of the currency
 * @param slaveryBanned          whether slaves are freed on its land
 * @param bounty                 the reward the guild posts for the head of a wanted player
 */
public record NpcLaws(int fineMultiplierPermille, long entryFee, boolean slaveryBanned, long bounty) {
    /** What entering a large city costs under a ruler of no particular leaning. */
    public static final long BASE_ENTRY_FEE = 20;

    /**
     * The laws of a ruler.
     *
     * @param maxEntryFee the highest fee the rules allow
     * @param maxBounty   the highest reward the rules allow
     */
    public static NpcLaws of(RulerCharacter character, List<RulerCharacter.Trait> traits, long maxEntryFee, long maxBounty) {
        int multiplier = switch (character) {
            case WARRIOR -> 1500;
            case MAGE -> 1250;
            case MERCHANT, BUILDER -> 1000;
        };
        double fee = BASE_ENTRY_FEE * (character == RulerCharacter.MERCHANT ? 1.5 : 1.0);
        long bounty = switch (character) {
            case WARRIOR -> 400;
            case MAGE -> 300;
            case MERCHANT -> 250;
            case BUILDER -> 200;
        };
        boolean slavery = false;
        for (RulerCharacter.Trait trait : traits) {
            switch (trait) {
                case JUST -> {
                    multiplier -= 250;
                    fee *= 0.5;
                    slavery = true;
                }
                case GENEROUS -> {
                    multiplier -= 150;
                    bounty = bounty * 3 / 2;
                }
                case THRIFTY -> bounty /= 2;
                case PROUD -> multiplier += 250;
                case AMBITIOUS -> fee *= 1.25;
                default -> {
                }
            }
        }
        multiplier = Math.max(KingdomLaw.MIN_MULTIPLIER, Math.min(KingdomLaw.MAX_MULTIPLIER, multiplier));
        long entry = Math.max(0, Math.min(maxEntryFee, Math.round(fee)));
        return new NpcLaws(multiplier, entry, slavery, Math.max(0, Math.min(maxBounty, bounty)));
    }
}
