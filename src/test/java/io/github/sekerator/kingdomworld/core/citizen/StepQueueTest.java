package io.github.sekerator.kingdomworld.core.citizen;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepQueueTest {
    /**
     * A made-up world: kingdoms with citizens holding numbers; a step takes a snapshot of the ids of its kingdom, adds to every
     * citizen still there what a counter carried through the step gives, and at its end writes the counter down.
     */
    private static final class World {
        final Map<Integer, List<Integer>> citizens = new HashMap<>();
        final Map<Integer, Long> values = new HashMap<>();
        final Set<Integer> gone = new HashSet<>();
        final List<String> log = new ArrayList<>();

        StepQueue.Step begin(int kingdom) {
            List<Integer> ids = List.copyOf(citizens.get(kingdom));
            log.add("begin " + kingdom);
            return new StepQueue.Step() {
                int index;
                long carried = kingdom;

                @Override
                public boolean alive() {
                    return true;
                }

                @Override
                public boolean hasNext() {
                    return index < ids.size();
                }

                @Override
                public void next() {
                    int id = ids.get(index++);
                    if (gone.contains(id)) {
                        return;
                    }
                    carried = carried * 31 + values.get(id);
                    values.put(id, values.get(id) + carried % 7);
                    log.add(kingdom + ":" + id);
                }

                @Override
                public void finish() {
                    log.add("end " + kingdom + " " + carried);
                }
            };
        }

        static World make(long seed) {
            SplittableRandom random = new SplittableRandom(seed);
            World world = new World();
            int id = 0;
            for (int kingdom = 0; kingdom < 3; kingdom++) {
                List<Integer> list = new ArrayList<>();
                for (int i = 0; i < random.nextInt(0, 400); i++) {
                    list.add(id);
                    world.values.put(id, random.nextLong(1000));
                    id++;
                }
                world.citizens.put(kingdom, list);
            }
            return world;
        }
    }

    @Test
    void aStepInSlicesDoesWhatAWholeStepDoesOnThreeSeeds() {
        for (long seed = 1; seed <= 3; seed++) {
            World whole = World.make(seed);
            for (int kingdom = 0; kingdom < 3; kingdom++) {
                StepQueue.Step step = whole.begin(kingdom);
                while (step.hasNext()) {
                    step.next();
                }
                step.finish();
            }
            World sliced = World.make(seed);
            StepQueue<Integer> queue = new StepQueue<>();
            for (int kingdom = 0; kingdom < 3; kingdom++) {
                int k = kingdom;
                queue.due(k, () -> sliced.begin(k));
            }
            SplittableRandom budgets = new SplittableRandom(seed * 7);
            int guard = 0;
            while (!queue.keys().isEmpty() && guard++ < 100_000) {
                int done = queue.advance(budgets.nextInt(1, 40), Long.MAX_VALUE / 2, () -> 0L);
                assertTrue(done <= 40);
            }
            assertEquals(whole.values, sliced.values, "the citizens after the step, seed " + seed);
            // Every step begins when it comes due; the work on the citizens and the ends come in the same order.
            assertEquals(whole.log.stream().filter(line -> !line.startsWith("begin")).toList(),
                    sliced.log.stream().filter(line -> !line.startsWith("begin")).toList(), "the order of the work, seed " + seed);
        }
    }

    @Test
    void aCitizenGoneMeanwhileIsPassedOverAndANewOneWaits() {
        World world = World.make(5);
        List<Integer> thirty = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            thirty.add(5_000 + i);
            world.values.put(5_000 + i, (long) i);
        }
        world.citizens.put(0, thirty);
        StepQueue<Integer> queue = new StepQueue<>();
        queue.due(0, () -> world.begin(0));
        queue.advance(10, Long.MAX_VALUE / 2, () -> 0L);
        List<Integer> ids = world.citizens.get(0);
        int gone = ids.get(ids.size() - 1);
        world.gone.add(gone);
        world.citizens.put(0, new ArrayList<>(ids));
        world.citizens.get(0).add(9_999);
        world.values.put(9_999, 1L);
        while (!queue.keys().isEmpty()) {
            queue.advance(10, Long.MAX_VALUE / 2, () -> 0L);
        }
        assertFalse(world.log.contains("0:" + gone), "a citizen gone meanwhile is passed over");
        assertFalse(world.log.contains("0:9999"), "a newcomer waits for the next step");
    }

    @Test
    void theBudgetOfTimeAndItemsHolds() {
        World world = World.make(9);
        StepQueue<Integer> queue = new StepQueue<>();
        for (int kingdom = 0; kingdom < 3; kingdom++) {
            int k = kingdom;
            queue.due(k, () -> world.begin(k));
        }
        long[] clock = {0};
        // Every item takes a microsecond: a budget of 25 microseconds lets 25 items through at most.
        int done = queue.advance(1000, 25_000, () -> clock[0] += 1_000);
        assertTrue(done <= 25, "items done in the time: " + done);
        assertEquals(17, queue.advance(17, Long.MAX_VALUE / 2, () -> 0L), "the items of the budget");
    }

    @Test
    void aStepDueWhileTheLastIsUnderWayWaitsForIt() {
        World world = World.make(11);
        world.citizens.put(0, new ArrayList<>(List.of(0, 1, 2, 3, 4, 5)));
        StepQueue<Integer> queue = new StepQueue<>();
        queue.due(0, () -> world.begin(0));
        queue.advance(2, Long.MAX_VALUE / 2, () -> 0L);
        queue.due(0, () -> world.begin(0));
        assertTrue(queue.waiting(0));
        assertEquals(1, world.log.stream().filter(line -> line.equals("begin 0")).count(), "the second step has not begun");
        queue.startWaiting();
        assertEquals(1, world.log.stream().filter(line -> line.equals("begin 0")).count(), "it still waits");
        while (queue.running(0)) {
            queue.advance(2, Long.MAX_VALUE / 2, () -> 0L);
        }
        queue.startWaiting();
        assertTrue(queue.running(0) && !queue.waiting(0), "it begins once the first has ended");
        assertEquals(2, world.log.stream().filter(line -> line.equals("begin 0")).count());
    }
}
