package io.github.sekerator.kingdomworld.core.law;

/**
 * What the court of one kingdom decided for its land.
 *
 * @param fineMultiplierPermille fines are multiplied by this, in permille (1000 as in the config)
 * @param entryFee               what a large city asks for entry; 0 is free
 * @param slaveryBanned          whether slaves are set free on the land of the kingdom
 * @param bountyReward           reward for the head of a wanted murderer; -1 takes the config
 */
public record KingdomLaw(int fineMultiplierPermille, long entryFee, boolean slaveryBanned, long bountyReward) {
    public static final KingdomLaw DEFAULT = new KingdomLaw(1000, 0, false, -1);
    public static final int MIN_MULTIPLIER = 100;
    public static final int MAX_MULTIPLIER = 5_000;

    public KingdomLaw {
        fineMultiplierPermille = Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, fineMultiplierPermille));
        entryFee = Math.max(0, entryFee);
        bountyReward = Math.max(-1, bountyReward);
    }

    public long bounty(LawRules rules) {
        return bountyReward < 0 ? rules.bountyReward : bountyReward;
    }

    public boolean isDefault() {
        return equals(DEFAULT);
    }

    public KingdomLaw withMultiplier(int value) {
        return new KingdomLaw(value, entryFee, slaveryBanned, bountyReward);
    }

    public KingdomLaw withEntryFee(long value) {
        return new KingdomLaw(fineMultiplierPermille, value, slaveryBanned, bountyReward);
    }

    public KingdomLaw withSlaveryBanned(boolean value) {
        return new KingdomLaw(fineMultiplierPermille, entryFee, value, bountyReward);
    }

    public KingdomLaw withBounty(long value) {
        return new KingdomLaw(fineMultiplierPermille, entryFee, slaveryBanned, value);
    }
}
