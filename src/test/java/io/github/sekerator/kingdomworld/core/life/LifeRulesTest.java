package io.github.sekerator.kingdomworld.core.life;

import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifeRulesTest {
    private static final LifeRules RULES = LifeRules.defaults();
    private static final int DAY = LifeRules.DAY;

    // ---------------------------------------------------------------- the day

    @Test
    void thePartsOfTheDayFollowTheClockAndFillIt() {
        assertEquals(DaySchedule.Phase.MORNING, DaySchedule.phase(RULES.wakeUp(), 0, RULES));
        assertEquals(DaySchedule.Phase.DAY, DaySchedule.phase(6_000, 0, RULES));
        assertEquals(DaySchedule.Phase.EVENING, DaySchedule.phase(RULES.eveningStart(), 0, RULES));
        assertEquals(DaySchedule.Phase.NIGHT, DaySchedule.phase(18_000, 0, RULES));
        // Any number of days later the same.
        assertEquals(DaySchedule.Phase.NIGHT, DaySchedule.phase(18_000 + 37L * DAY, 0, RULES));
        int total = 0;
        for (DaySchedule.Phase phase : DaySchedule.Phase.values()) {
            total += DaySchedule.length(phase, RULES);
        }
        assertEquals(DAY, total);
    }

    @Test
    void citizensKeepTheirDayShiftedWithinTheSpreadSoThatACityDoesNotMoveInStep() {
        Set<Integer> shifts = new HashSet<>();
        int evening = 0;
        for (int i = 0; i < 200; i++) {
            UUID id = UUID.nameUUIDFromBytes(("citizen" + i).getBytes());
            int shift = DaySchedule.shift(id, RULES.spreadTicks());
            assertTrue(Math.abs(shift) <= RULES.spreadTicks(), "shift " + shift);
            assertEquals(shift, DaySchedule.shift(id, RULES.spreadTicks()), "a citizen keeps its shift");
            shifts.add(shift);
            if (DaySchedule.phase(RULES.eveningStart(), shift, RULES) == DaySchedule.Phase.EVENING) {
                evening++;
            }
        }
        assertTrue(shifts.size() > 50, "shifts differ: " + shifts.size());
        // At the very start of the evening some citizens are still at work.
        assertTrue(evening > 40 && evening < 160, "in the evening already: " + evening);
        // In the middle of a part every citizen is in it.
        for (int i = 0; i < 50; i++) {
            UUID id = UUID.nameUUIDFromBytes(("other" + i).getBytes());
            assertEquals(DaySchedule.Phase.NIGHT, DaySchedule.phase(18_500, DaySchedule.shift(id, RULES.spreadTicks()), RULES));
        }
        assertEquals(0, DaySchedule.shift(UUID.randomUUID(), 0));
    }

    @Test
    void aboutTheShareOfCitizensGoesToTheTavernADifferentChoiceEveryDay() {
        int going = 0;
        int changed = 0;
        for (int i = 0; i < 1_000; i++) {
            UUID id = UUID.nameUUIDFromBytes(("guest" + i).getBytes());
            boolean today = DaySchedule.wantsTavern(id, 10, RULES);
            going += today ? 1 : 0;
            changed += today != DaySchedule.wantsTavern(id, 11, RULES) ? 1 : 0;
        }
        assertTrue(going > 400 && going < 600, "going: " + going);
        assertTrue(changed > 300, "changed: " + changed);
        LifeRules.Builder none = new LifeRules.Builder();
        none.tavernShare = 0;
        assertFalse(DaySchedule.wantsTavern(UUID.randomUUID(), 3, none.build()));
    }

    @Test
    void timesOutOfOrderAreRefused() {
        LifeRules.Builder bad = new LifeRules.Builder();
        bad.eveningStart = 500;
        assertThrows(IllegalArgumentException.class, bad::build);
        LifeRules.Builder late = new LifeRules.Builder();
        late.sleepStart = 10_000;
        assertThrows(IllegalArgumentException.class, late::build);
    }

    // ---------------------------------------------------------------- rumors

    private static Rumor rumor(String source, int i) {
        return new Rumor(source, "key." + source + i, List.of(Rumor.Arg.plain(i)));
    }

    @Test
    void anInnkeeperTellsTwoToFourRumorsOfDifferentKindsFirstTheSameAllDay() {
        List<Rumor> candidates = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            candidates.add(rumor(Rumors.CAMP, i));
            candidates.add(rumor(Rumors.CONTRACT, i));
        }
        candidates.add(rumor(Rumors.WANTED, 0));
        Set<Integer> counts = new HashSet<>();
        for (long day = 0; day < 40; day++) {
            List<Rumor> told = Rumors.pick(candidates, day, 77, RULES);
            assertTrue(told.size() >= 2 && told.size() <= 4, "told " + told.size());
            assertEquals(told, Rumors.pick(candidates, day, 77, RULES), "the talk of a day does not change");
            Set<String> sources = new HashSet<>();
            for (Rumor rumor : told.subList(0, Math.min(3, told.size()))) {
                sources.add(rumor.source());
            }
            assertEquals(Math.min(3, told.size()), sources.size(), "one of each kind first: " + told);
            assertTrue(candidates.containsAll(told));
            counts.add(told.size());
        }
        assertTrue(counts.size() > 1, "the number of rumors changes from day to day");
        assertTrue(Rumors.pick(List.of(), 3, 1, RULES).isEmpty());
        assertEquals(1, Rumors.pick(List.of(rumor(Rumors.NEWS, 1)), 3, 1, RULES).size());
    }

    @Test
    void aCampSoundsRoughlyWhereItIs() {
        assertEquals("kingdomworld.life.direction.north", Rumors.direction(0, 0, 10, -300));
        assertEquals("kingdomworld.life.direction.east", Rumors.direction(0, 0, 300, 20));
        assertEquals("kingdomworld.life.direction.south_west", Rumors.direction(100, 100, -200, 400));
        assertEquals("kingdomworld.life.direction.here", Rumors.direction(5, 5, 5, 5));
        assertEquals(300, Rumors.roughDistance(0, 0, 0, 312));
        assertEquals(Rumors.DISTANCE_STEP, Rumors.roughDistance(0, 0, 3, 4));
        assertEquals("kingdomworld.life.camp.few", Rumors.strength(2));
        assertEquals("kingdomworld.life.camp.band", Rumors.strength(6));
        assertEquals("kingdomworld.life.camp.large", Rumors.strength(12));
        assertThrows(IllegalArgumentException.class, () -> new Rumor("x", "k", List.of(Rumor.Arg.plain(1), Rumor.Arg.plain(2),
                Rumor.Arg.plain(3), Rumor.Arg.plain(4), Rumor.Arg.plain(5), Rumor.Arg.plain(6), Rumor.Arg.plain(7))));
    }

    // ---------------------------------------------------------------- illness

    @Test
    void hungerHomelessnessAndWoundsMakeIllnessLikelierButNeverCertain() {
        double healthy = Sickness.dailyChance(RULES, false, false, false);
        assertEquals(RULES.sickChance() / 1000.0, healthy, 1e-9);
        assertEquals(healthy * RULES.sickFactor(), Sickness.dailyChance(RULES, true, false, false), 1e-9);
        assertTrue(Sickness.dailyChance(RULES, true, true, true) > Sickness.dailyChance(RULES, true, true, false));
        LifeRules.Builder often = new LifeRules.Builder();
        often.sickChance = 400;
        often.sickFactor = 10;
        assertEquals(Sickness.MAX_DAILY, Sickness.dailyChance(often.build(), true, true, true), 1e-9);
        // The chance per day stays the same whatever the length of a step.
        double perStep = Sickness.stepChance(0.1, 1_200);
        assertEquals(0.1, 1 - Math.pow(1 - perStep, DAY / 1_200.0), 1e-9);
        assertEquals(0.0, Sickness.stepChance(0.0, 1_200));
    }

    @Test
    void anIllCitizenGetsWellFasterInAnInfirmaryAndByItselfAtHome() {
        double infirmary = Sickness.recovery(RULES, true, 1_200);
        double home = Sickness.recovery(RULES, false, 1_200);
        assertTrue(infirmary > home && home > 0);
        assertEquals(1.0, Sickness.recovery(RULES, true, (int) (RULES.infirmaryDays() * DAY)), 1e-9);
        LifeState state = new LifeState();
        UUID id = UUID.randomUUID();
        state.fallIll(id, 100);
        int steps = 0;
        while (!state.recover(id, home)) {
            steps++;
        }
        assertEquals(Math.round(RULES.homeDays() * DAY / 1_200.0) - 1, steps, 1);
        assertFalse(state.isSick(id));
        assertEquals(0.5, Sickness.workFactor(RULES, true), 1e-9);
        assertEquals(1.0, Sickness.workFactor(RULES, false), 1e-9);
    }

    @Test
    void theLifeOfTheCityMovesTheMoodWithinItsBounds() {
        CitizenNeeds.MoodInputs base = new CitizenNeeds.MoodInputs(20, true, false, 0, 100, 0, 0);
        int plain = CitizenNeeds.targetMood(base);
        assertEquals(plain + 15, CitizenNeeds.targetMood(new CitizenNeeds.MoodInputs(20, true, false, 0, 100, 0, 15)));
        assertEquals(plain - 10, CitizenNeeds.targetMood(new CitizenNeeds.MoodInputs(20, true, false, 0, 100, 0, -10)));
        assertEquals(Math.min(100, plain + CitizenNeeds.MAX_LIFE), CitizenNeeds.targetMood(new CitizenNeeds.MoodInputs(20, true, false, 0, 100, 0, 90)));
        assertEquals(plain, CitizenNeeds.targetMood(new CitizenNeeds.MoodInputs(20, true, false, 0, 100, 0)));
    }

    // ---------------------------------------------------------------- festivals and fairs

    @Test
    void aFestivalCostsMoneyAndTreatForEveryCitizenAndGladdensForSomeDays() {
        assertEquals(40 * RULES.festivalPerCitizen(), Festivals.cost(40, RULES));
        assertEquals(40 * RULES.festivalFood(), Festivals.treat(40, RULES));
        assertEquals(0, Festivals.cost(0, RULES));
        assertEquals(RULES.festivalMood(), Festivals.mood(10, true, 10, RULES));
        assertEquals(RULES.festivalMood(), Festivals.mood(10, true, 10 + RULES.festivalMoodDays() - 1, RULES));
        assertEquals(0, Festivals.mood(10, true, 10 + RULES.festivalMoodDays(), RULES));
        assertEquals(0, Festivals.mood(10, true, 9, RULES));
        assertEquals(RULES.festivalMood() / 2, Festivals.mood(10, false, 11, RULES));
        assertEquals(0, Festivals.mood(Long.MIN_VALUE, true, 11, RULES));
        assertEquals(null, Festivals.refusal(Long.MIN_VALUE, 3, RULES));
        assertEquals("too_soon", Festivals.refusal(10, 10 + RULES.festivalEveryDays() - 1, RULES));
        assertEquals(null, Festivals.refusal(10, 10 + RULES.festivalEveryDays(), RULES));
        assertEquals(2, Festivals.daysUntilNext(10, 10 + RULES.festivalEveryDays() - 2, RULES));
        // The citizens rest from noon of the festival day.
        long festivalDay = 5;
        assertFalse(Festivals.resting(festivalDay, festivalDay * DAY + 5_999));
        assertTrue(Festivals.resting(festivalDay, festivalDay * DAY + 6_000));
        assertFalse(Festivals.resting(festivalDay, (festivalDay + 1) * DAY + 7_000));
    }

    @Test
    void theDayOfTheFoundingComesBackOnceAYear() {
        long founded = 3L * DAY + 500;
        assertFalse(Festivals.foundingDay(founded, 3, RULES), "not the founding itself");
        assertTrue(Festivals.foundingDay(founded, 3 + RULES.daysPerYear(), RULES));
        assertFalse(Festivals.foundingDay(founded, 4 + RULES.daysPerYear(), RULES));
        assertTrue(Festivals.foundingDay(founded, 3 + 2L * RULES.daysPerYear(), RULES));
        assertEquals(0, Festivals.year(founded, founded + 10L * DAY, RULES));
        assertEquals(2, Festivals.year(founded, (3 + 2L * RULES.daysPerYear()) * DAY, RULES));
    }

    @Test
    void aCityHoldsAFairEveryFewDaysOnADayOfItsOwnWithLowerPrices() {
        int[] fairs = new int[3];
        Set<Long> first = new HashSet<>();
        for (int city = 0; city < 3; city++) {
            for (long day = 0; day < 10L * RULES.fairEveryDays(); day++) {
                if (Festivals.fairDay(city * 1_000_003L, day, RULES)) {
                    fairs[city]++;
                    if (fairs[city] == 1) {
                        first.add(day);
                    }
                }
            }
            assertEquals(10, fairs[city]);
        }
        long days = Festivals.daysToFair(42, 100, RULES);
        assertTrue(days >= 0 && days < RULES.fairEveryDays());
        assertTrue(Festivals.fairDay(42, 100 + days, RULES));
        assertEquals(900, Festivals.fairPrice(1_000, RULES));
        assertEquals(1, Festivals.fairPrice(1, RULES));
        LifeRules.Builder none = new LifeRules.Builder();
        none.fairEveryDays = 0;
        assertFalse(Festivals.fairDay(42, 100, none.build()));
        assertEquals(-1, Festivals.daysToFair(42, 100, none.build()));
    }

    // ---------------------------------------------------------------- the state

    @Test
    void theStateRemembersTavernsFestivalsRoomsAndMarks() {
        LifeState state = new LifeState();
        int[] changes = {0};
        state.setChangeListener(() -> changes[0]++);
        UUID citizen = UUID.randomUUID();
        state.visitedTavern(citizen, 7);
        assertEquals(RULES.tavernMood(), state.tavernMood(citizen, 7 + RULES.tavernMoodDays() - 1, RULES));
        assertEquals(0, state.tavernMood(citizen, 7 + RULES.tavernMoodDays(), RULES));
        UUID kingdom = UUID.randomUUID();
        state.setFestival(kingdom, new LifeState.Festival(3, 3, true, 0));
        state.addEvent(kingdom, new LifeState.Event("festival", 3, ""));
        assertEquals(3, state.festival(kingdom).festivalDay());
        UUID player = UUID.randomUUID();
        assertTrue(state.mark(player, 9));
        assertFalse(state.mark(player, 9));
        for (long i = 0; i < LifeState.MAX_MARKS + 5; i++) {
            state.mark(player, 100 + i);
        }
        assertEquals(LifeState.MAX_MARKS, state.marks(player).size());
        state.keepMarks(id -> id % 2 == 0);
        assertTrue(state.marks(player).stream().allMatch(id -> id % 2 == 0));
        state.rent(player, new LifeState.Room(kingdom, new WorldPos("minecraft:overworld", 1, 2, 3), 100, null, 0, false));
        assertTrue(state.room(player).isPresent());
        assertTrue(state.leave(player).isPresent());
        state.forgetCitizen(citizen);
        state.forgetKingdom(kingdom);
        assertEquals(Long.MIN_VALUE, state.tavernDay(citizen));
        assertTrue(state.events(kingdom).isEmpty());
        assertTrue(changes[0] > 5);
    }
}
