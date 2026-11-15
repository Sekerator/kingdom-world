package io.github.sekerator.kingdomworld.core.road;

import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.Citizens;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.commerce.Business;
import io.github.sekerator.kingdomworld.core.economy.CityPurses;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.EconomyRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The townsfolk at work: they fill the posts of the crown before the workshops and stay idle last, named workers come first, the
 * seats wait while the treasury holds too little, the townsfolk of a post are one claim paid after the named, the hands of a
 * workshop share by the work they give, and the townsfolk grow and leave by their work, pay and taxes.
 */
class CityFolkEconomyTest {
    private static final FolkWorkRules RULES = FolkWorkRules.DEFAULT;
    private static final UUID KINGDOM = new UUID(3, 3);

    @Test
    void theTownsfolkTakeTheFreeSeatsInOrder() {
        assertArrayEquals(new int[]{60, 20, 20}, FolkJobs.fill(100, new int[]{60, 20, 40}, new long[]{6, 5, 7}, 1_000_000, 500, 7),
                "the posts in order, the rest stay idle for the workshops");
        assertArrayEquals(new int[]{0, 0}, FolkJobs.fill(0, new int[]{3, 3}, new long[]{6, 6}, 1_000_000, 0, 7));
        assertTrue(FolkJobs.takes("farmer") && FolkJobs.takes("carrier") && FolkJobs.takes("smith"));
        assertTrue(!FolkJobs.takes("merchant") && !FolkJobs.takes("builder") && !FolkJobs.takes("minter") && !FolkJobs.takes("warrior")
                && !FolkJobs.takes("innkeeper") && !FolkJobs.takes("registrar") && !FolkJobs.takes("mage"));
    }

    @Test
    void theSeatsWaitWhileTheTreasuryHoldsLessThanTheDaysOfWages() {
        // 7 days of 500 a day and ten farmers of 6 more: 3920.
        assertArrayEquals(new int[]{10, 0}, FolkJobs.fill(50, new int[]{60, 20}, new long[]{6, 5}, 3_920, 500, 7));
        assertArrayEquals(new int[]{0}, FolkJobs.fill(50, new int[]{60}, new long[]{6}, 3_000, 500, 7),
                "a treasury of less than a week of wages takes nobody");
        assertArrayEquals(new int[]{60}, FolkJobs.fill(60, new int[]{60}, new long[]{6}, 0, 500, 0), "no days asked, no wait");
    }

    @Test
    void theIdleLeaveFirstThenTheHandsThenThePosts() {
        assertArrayEquals(new int[]{0, 0}, FolkJobs.shrink(100, 30, 50));
        assertArrayEquals(new int[]{10, 0}, FolkJobs.shrink(70, 30, 50));
        assertArrayEquals(new int[]{30, 10}, FolkJobs.shrink(40, 30, 50));
        assertArrayEquals(new int[]{30, 50}, FolkJobs.shrink(0, 30, 50));
    }

    @Test
    void aNamedWorkerTakesTheSeatOfOneOfTheTownsfolk() {
        Citizens citizens = new Citizens();
        WorldPos at = new WorldPos("minecraft:overworld", 0, 64, 0);
        citizens.addSite(at, SiteKinds.WORK_POST, KINGDOM);
        WorkPost post = citizens.posts().get(at).orElseThrow();
        citizens.setPostProfession(post, "farmer");
        citizens.setPostSeats(post, 3);
        post.setFolkWorkers(5);
        assertEquals(3, post.folkWorkers(), "never beyond the seats");
        Citizen anna = new Citizen(UUID.randomUUID(), KINGDOM, 7, Gender.FEMALE, 0, 0L, at);
        citizens.add(anna);
        assertTrue(citizens.assignToPost(anna, post), "a named worker is hired on a post full of townsfolk");
        assertEquals(2, post.folkWorkers(), "one of the townsfolk made way and is idle again");
        assertEquals(0, post.openSeats());
        citizens.setPostSeats(post, 1);
        assertEquals(0, post.folkWorkers(), "fewer seats send the townsfolk away first");
        assertEquals(post.folkId(), WorkPost.folkId(at), "the townsfolk of a post are known by its place");
    }

    @Test
    void theTownsfolkOfAPostArePaidAsOneClaimAfterTheNamedAndTheMoneyAddsUp() {
        Economy economy = new Economy();
        economy.open(KINGDOM, 0);
        long wage = economy.treasury(KINGDOM).orElseThrow().wage("farmer", EconomyRules.DEFAULT);
        UUID named = UUID.randomUUID();
        UUID folk = WorkPost.folkId(new WorldPos("minecraft:overworld", 5, 64, 5));
        List<Economy.WageClaim> claims = List.of(new Economy.WageClaim(named, "farmer"), Economy.WageClaim.folk(folk, "farmer", 20));
        // A treasury with the wage of the named farmer and five of the townsfolk.
        economy.adminTreasury(KINGDOM, wage * 6 - economy.treasury(KINGDOM).orElseThrow().funds(), 0);
        long before = economy.supply(KINGDOM);
        Economy.Payday payday = Economy.Payday.NONE;
        for (long day = 1; day <= 3 && payday == Economy.Payday.NONE; day++) {
            payday = economy.payday(KINGDOM, day, List.of(), claims, EconomyRules.DEFAULT, id -> id.equals(folk) ? 2 : 0);
        }
        assertEquals(wage, payday.pay().get(named), "the named farmer is paid in full first");
        assertEquals(wage * 5, payday.pay().get(folk), "the townsfolk get what is left");
        assertEquals(wage * 5, economy.purse(CityPurses.PurseKey.cityPurse(KINGDOM, 2)), "into the purse of the city of their post");
        assertEquals(before, economy.supply(KINGDOM), "the money only moved");
        assertEquals(1, payday.summary().underpaid(), "only the townsfolk were paid short");
    }

    @Test
    void theHandsOfAWorkshopShareByTheWorkTheyGive() {
        int hand = RULES.handShare(Business.KEEPER_SHARE);
        assertEquals(800, hand, "two fifths of the share of a master");
        assertArrayEquals(new long[]{25, 25, 50}, Business.split(100, new int[]{Business.KEEPER_SHARE, Business.KEEPER_SHARE, 5 * hand}));
        // Two masters shared 20 and sold out: a hand would earn 4, a master 10 of its usual 7.
        assertEquals(FolkJobs.HandChange.HIRE, FolkJobs.handChange(true, 20, 2, 0, 7, Business.KEEPER_SHARE, RULES));
        assertEquals(FolkJobs.HandChange.KEEP, FolkJobs.handChange(false, 20, 2, 0, 7, Business.KEEPER_SHARE, RULES),
                "a place that did not sell out takes nobody");
        // With the hand they make two fifths of a master more: shared 24, a master still earns 10.
        assertEquals(FolkJobs.HandChange.KEEP, FolkJobs.handChange(false, 24, 2, 1, 7, Business.KEEPER_SHARE, RULES),
                "the hand does not take the masters below their share of the usual income");
        // Shared 12: a master would earn 5 of 7, below three quarters.
        assertEquals(FolkJobs.HandChange.RELEASE, FolkJobs.handChange(true, 12, 2, 1, 7, Business.KEEPER_SHARE, RULES));
        assertEquals(FolkJobs.HandChange.KEEP, FolkJobs.handChange(true, 10, 2, 0, 7, Business.KEEPER_SHARE, RULES),
                "a hand is not taken where the masters would earn too little");
        assertEquals(FolkJobs.HandChange.RELEASE, FolkJobs.handChange(true, 100, 0, 2, 7, Business.KEEPER_SHARE, RULES),
                "a place without masters lets its hands go");
    }

    @Test
    void theTownsfolkGrowOnlyWithWorkAndPayAndLeaveForAllThreeReasons() {
        assertEquals(CityFolk.Why.NONE, CityFolk.why(100, new CityFolk.Work(10, 300, 0, RULES)));
        assertEquals(CityFolk.Why.IDLE, CityFolk.why(100, new CityFolk.Work(11, 300, 0, RULES)), "more than a tenth idle");
        assertEquals(CityFolk.Why.POOR, CityFolk.why(100, new CityFolk.Work(0, 149, 0, RULES)), "less than half the usual income");
        assertEquals(CityFolk.Why.LEAVE_POOR, CityFolk.why(100, new CityFolk.Work(26, 300, 0, RULES)), "more than a quarter idle");
        assertEquals(CityFolk.Why.LEAVE_POOR, CityFolk.why(100, new CityFolk.Work(0, 74, 0, RULES)), "less than a quarter of the income");
        assertEquals(CityFolk.Why.LEAVE_TAXES, CityFolk.why(100, new CityFolk.Work(0, 300, -10, RULES)), "the means of the city at -10");
        long plenty = 1_000_000_000L;
        CityFolk.Day idle = new CityFolk.Day(100, 0.0, 100, 6, 600, plenty, 64, 3, 64, CitizenNeeds.NEUTRAL_MOOD, new CityFolk.Work(30, 300, 0, RULES));
        CityFolk.Result left = CityFolk.day(idle, FolkRules.DEFAULT);
        assertEquals(3, left.leftPoor(), "three in a hundred leave");
        assertEquals(0, left.grown(), "and nobody comes");
        assertEquals(97, left.folk());
        CityFolk.Day few = new CityFolk.Day(10, 0.0, 100, 6, 60, plenty, 64, 3, 64, CitizenNeeds.NEUTRAL_MOOD, new CityFolk.Work(0, 0, 0, RULES));
        assertEquals(1, CityFolk.day(few, FolkRules.DEFAULT).leftPoor(), "at least one leaves");
        CityFolk.Day working = new CityFolk.Day(100, 0.0, 100, 6, 600, plenty, 64, 3, 64, CitizenNeeds.NEUTRAL_MOOD, new CityFolk.Work(5, 350, 0, RULES));
        assertEquals(3, CityFolk.day(working, FolkRules.DEFAULT).grown(), "townsfolk at work and paid grow as before");
    }
}
