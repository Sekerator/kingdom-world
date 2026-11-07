package io.github.sekerator.kingdomworld.core.companion;

import io.github.sekerator.kingdomworld.core.citizen.Gender;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * The people on the market of one branch on one day. The same branch and day always give the same people, which keeps
 * worlds fair and tests repeatable. Most are of low level, a few are seasoned; the skills grow with the level, the price
 * follows the strength.
 */
public final class MarketOffers {
    private MarketOffers() {
    }

    /**
     * The period of offers a game day belongs to.
     */
    public static long period(long day, CompanionRules rules) {
        return Math.floorDiv(day, rules.refreshDays);
    }

    /**
     * The offers of a branch for a period.
     *
     * @param worldSeed a number of the world, so that worlds differ
     * @param ids       the ids for the offers
     * @param skins     looks a person may have
     */
    public static List<Offer> generate(UUID branch, long period, long worldSeed, CompanionRules rules, int skins, LongSupplier ids) {
        SplittableRandom random = new SplittableRandom(branch.getMostSignificantBits() * 31 + branch.getLeastSignificantBits()
                ^ period * 0x9E3779B97F4A7C15L ^ worldSeed);
        List<Offer> offers = new ArrayList<>();
        for (int i = 0; i < rules.slaveOffers + rules.freeOffers; i++) {
            boolean slave = i < rules.slaveOffers;
            double roll = random.nextDouble();
            int level = (int) Math.floor(roll * roll * (rules.maxOfferLevel + 1));
            level = Math.max(0, Math.min(rules.maxOfferLevel, level));
            Skills skills = new Skills(1 + random.nextInt(4), 1 + random.nextInt(4), 1 + random.nextInt(4));
            for (int raise = 0; raise < level / 2; raise++) {
                skills = skills.raised();
            }
            Origin[] origins = Origin.offered(slave);
            Origin origin = origins[random.nextInt(origins.length)];
            Gender gender = random.nextBoolean() ? Gender.MALE : Gender.FEMALE;
            long price = slave ? CompanionPower.price(level, skills, rules) : CompanionPower.wage(level, skills, rules);
            offers.add(new Offer(ids.getAsLong(), branch, slave, random.nextInt(), gender, random.nextInt(Math.max(1, skins)), origin, level,
                    skills, price));
        }
        return offers;
    }
}
