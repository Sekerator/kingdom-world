package io.github.sekerator.kingdomworld.core.citizen;

import io.github.sekerator.kingdomworld.core.life.DaySchedule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules of the window of the workers: what a citizen lacks, what it is doing far from players, who may take a seat at a
 * post and in which order, and the numbers of a kingdom.
 */
class WorkforceTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final UUID REALM = UUID.randomUUID();

    private static WorldPos pos(int x, int z) {
        return new WorldPos(OVERWORLD, x, 64, z);
    }

    private final Citizens citizens = new Citizens();

    private Citizen citizen(String profession, int x) {
        Citizen citizen = new Citizen(UUID.randomUUID(), REALM, 1, Gender.MALE, 0, 0L, pos(x, 0));
        citizens.add(citizen);
        citizens.setProfession(citizen, profession);
        return citizen;
    }

    private WorkPost post(int x, String profession, int seats) {
        citizens.addSite(pos(x, 0), SiteKinds.WORK_POST, REALM);
        WorkPost post = citizens.posts().get(pos(x, 0)).orElseThrow();
        citizens.setPostProfession(post, profession);
        citizens.setPostSeats(post, seats);
        return post;
    }

    @Test
    void needsComeFromTheWorkTheHungerTheWoundsAndTheBed() {
        WorkPost mine = post(10, "miner", 2);
        Citizen miner = citizen("miner", 0);
        citizens.addSite(pos(1, 1), SiteKinds.BED, REALM);
        citizens.assignHome(miner, pos(1, 1));
        assertTrue(citizens.assignWorkplace(miner, mine.pos()));
        assertTrue(Workforce.needs(miner).isEmpty());
        assertFalse(Workforce.hasProblem(miner));

        miner.setWorkStatus(WorkStatus.NO_TOOL);
        assertEquals(List.of(Workforce.Need.TOOL), Workforce.needs(miner));
        miner.setWorkStatus(WorkStatus.STORAGE_FULL);
        assertEquals(List.of(Workforce.Need.STORAGE_SPACE), Workforce.needs(miner));
        miner.setWorkStatus(WorkStatus.NO_ROCK);
        assertTrue(Workforce.needs(miner).isEmpty(), "rock is no supply: the window tells it as the state of the work");
        assertTrue(Workforce.hasProblem(miner));

        miner.setWorkStatus(WorkStatus.WORKING);
        miner.setFood(1.0f);
        miner.setHealth(2.0f, 0.5f);
        citizens.releaseHome(miner);
        assertEquals(List.of(Workforce.Need.CARE, Workforce.Need.FOOD, Workforce.Need.HOME), Workforce.needs(miner));

        // A lumberjack given the profession at the throne lacks a post, nothing else of its work.
        Citizen lumberjack = citizen("lumberjack", 3);
        lumberjack.setWorkStatus(WorkStatus.NO_TOOL);
        assertEquals(Workforce.Need.WORKPLACE, Workforce.needs(lumberjack).get(0));
        assertEquals(Workforce.Need.WORKPLACE, Workforce.Need.of(WorkStatus.NO_WORKPLACE));
        assertEquals(Workforce.Need.MATERIALS, Workforce.Need.of(WorkStatus.NO_INGREDIENTS));
        assertEquals(Workforce.Need.MATERIALS, Workforce.Need.of(WorkStatus.NO_MATERIALS));
    }

    @Test
    void candidatesOfThePostsProfessionWithoutAPostComeFirstThenThoseWithoutWork() {
        WorkPost grove = post(50, "lumberjack", 4);
        WorkPost other = post(80, "lumberjack", 4);
        Citizen nearIdle = citizen("unemployed", 49);
        Citizen farIdle = citizen("unemployed", 0);
        Citizen farLumberjack = citizen("lumberjack", -200);
        Citizen elsewhere = citizen("lumberjack", 79);
        assertTrue(citizens.assignWorkplace(elsewhere, other.pos()));
        Citizen fisher = citizen("fisher", 51);
        Citizen soldier = citizen("archer", 50);
        Citizen here = citizen("lumberjack", 50);
        assertTrue(citizens.assignWorkplace(here, grove.pos()));

        List<Citizen> order = Workforce.candidates(citizens.registry().citizensOf(REALM), grove);
        assertEquals(List.of(farLumberjack, nearIdle, farIdle, elsewhere, fisher), order);
        assertEquals(Workforce.Candidate.SAME_ELSEWHERE, Workforce.candidate(elsewhere, grove));
        assertTrue(Workforce.candidate(fisher, grove).transfer());
        assertFalse(Workforce.candidate(nearIdle, grove).transfer());
        assertFalse(Workforce.Candidate.SOLDIER.allowed());
        assertEquals(-1, Workforce.Candidate.SAME_ELSEWHERE.hireRank(), "the hire button takes nobody from other work");
        assertFalse(order.contains(soldier));
        assertFalse(order.contains(here));
    }

    @Test
    void farFromPlayersTheDayAndTheWorkTellWhatACitizenDoes() {
        WorkPost pond = post(10, "fisher", 1);
        Citizen fisher = citizen("fisher", 0);
        assertTrue(citizens.assignWorkplace(fisher, pond.pos()));
        fisher.setWorkStatus(WorkStatus.WORKING);
        assertEquals(Workforce.Doing.WORKING, Workforce.doing(fisher, DaySchedule.Phase.DAY, false, false));
        assertEquals(Workforce.Doing.GOING_TO_WORK, Workforce.doing(fisher, DaySchedule.Phase.MORNING, false, false));
        assertEquals(Workforce.Doing.RESTING, Workforce.doing(fisher, DaySchedule.Phase.EVENING, false, false));
        assertEquals(Workforce.Doing.SLEEPING, Workforce.doing(fisher, DaySchedule.Phase.NIGHT, false, false));
        assertEquals(Workforce.Doing.FESTIVAL, Workforce.doing(fisher, DaySchedule.Phase.DAY, false, true));
        assertEquals(Workforce.Doing.SICK, Workforce.doing(fisher, DaySchedule.Phase.DAY, true, false));
        fisher.setWorkStatus(WorkStatus.NO_WATER);
        assertEquals(Workforce.Doing.STANDING, Workforce.doing(fisher, DaySchedule.Phase.DAY, false, false));
        fisher.setHealth(1.0f, 0.5f);
        assertEquals(Workforce.Doing.HEALING, Workforce.doing(fisher, DaySchedule.Phase.NIGHT, false, false));

        assertEquals(Workforce.Doing.IDLE, Workforce.doing(citizen("unemployed", 1), DaySchedule.Phase.DAY, false, false));
        assertEquals(Workforce.Doing.SERVING, Workforce.doing(citizen("warrior", 1), DaySchedule.Phase.NIGHT, false, false));
        assertEquals(Workforce.Doing.STANDING, Workforce.doing(citizen("miner", 1), DaySchedule.Phase.DAY, false, false),
                "a miner without a post stands still");
        assertEquals(Workforce.Doing.WORKING, Workforce.doing(citizen("adventurer", 1), DaySchedule.Phase.DAY, false, false));
    }

    @Test
    void theNumbersOfTheKingdomAndTheProfessionsWithoutAPost() {
        WorkPost pond = post(10, "fisher", 1);
        Citizen fisher = citizen("fisher", 0);
        citizens.assignWorkplace(fisher, pond.pos());
        citizen("fisher", 0);
        citizen("unemployed", 0);
        citizen("unemployed", 0);
        citizen("adventurer", 0);
        citizen("warrior", 0);
        assertEquals(new Workforce.Counts(2, 2, 1, 1), Workforce.counts(citizens.registry().citizensOf(REALM)));

        List<String> free = Workforce.professionsWithoutPost().stream().map(Profession::id).toList();
        assertTrue(free.contains("adventurer"));
        assertTrue(free.contains("farmer"), "a farmer finds a composter of its own");
        assertFalse(free.contains("lumberjack"));
        assertFalse(free.contains("unemployed"));
        assertFalse(free.contains("warrior"));

        // Problems first, then the professions in the order of the registry.
        citizens.addSite(pos(2, 2), SiteKinds.BED, REALM);
        citizens.assignHome(fisher, pos(2, 2));
        fisher.setWorkStatus(WorkStatus.WORKING);
        assertFalse(Workforce.hasProblem(fisher));
        List<Citizen> sorted = new java.util.ArrayList<>(citizens.registry().citizensOf(REALM));
        sorted.sort(Workforce.problemsFirst());
        assertEquals(fisher, sorted.get(sorted.size() - 1), "the only citizen without a problem comes last");
        for (int i = 0; i < sorted.size() - 1; i++) {
            assertTrue(Workforce.hasProblem(sorted.get(i)));
        }
    }
}
