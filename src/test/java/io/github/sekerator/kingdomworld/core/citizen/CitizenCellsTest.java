package io.github.sekerator.kingdomworld.core.citizen;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitizenCellsTest {
    private static final String[] DIMENSIONS = {"minecraft:overworld", "minecraft:the_nether"};
    private static final List<UUID> KINGDOMS = List.of(new UUID(3, 1), new UUID(3, 2));

    @Test
    void aCitizenIsFoundByEachOfItsPlacesAndOnlyWhereItIsNow() {
        CitizenCells cells = new CitizenCells();
        UUID id = new UUID(1, 1);
        String dim = DIMENSIONS[0];
        // Block -1 is in chunk -1 and in the square west of block 0.
        cells.place(id, new WorldPos(dim, -1, 64, 0), null, new WorldPos(dim, 1000, 64, 1000));
        assertTrue(found(cells, dim, 0, 0, 1).contains(id), "one chunk away across the edge of a square");
        assertTrue(found(cells, dim, 62, 62, 0).contains(id), "by the last position");
        assertFalse(found(cells, dim, 30, 30, 2).contains(id), "far from every place");
        assertFalse(found(cells, DIMENSIONS[1], 0, 0, 1).contains(id), "another dimension");

        cells.place(id, null, new WorldPos(dim, 500, 64, 500), new WorldPos(dim, 1000, 64, 1000));
        assertFalse(found(cells, dim, 0, 0, 1).contains(id), "the old home is forgotten");
        assertTrue(found(cells, dim, 31, 31, 0).contains(id), "the new workplace");

        UUID other = new UUID(1, 2);
        cells.place(other, null, null, new WorldPos(dim, 0, 64, 0));
        assertTrue(cells.order(id) < cells.order(other));
        assertTrue(cells.remove(id));
        assertFalse(cells.remove(id));
        assertTrue(found(cells, dim, 62, 62, 3).isEmpty(), "a removed citizen is nowhere");
        cells.place(id, null, null, new WorldPos(dim, 0, 64, 0));
        assertTrue(cells.order(other) < cells.order(id), "put again, it comes after the others");
        assertEquals(Set.of(id, other), found(cells, dim, 0, 0, 0));
    }

    @Test
    void theCitizensNearPlayersAreThoseAFullLookFindsInTheSameOrder() {
        SplittableRandom random = new SplittableRandom(41);
        CitizenRegistry registry = new CitizenRegistry();
        List<Citizen> removed = new ArrayList<>();
        Set<UUID> withBody = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            registry.add(newCitizen(random, KINGDOMS.get(random.nextInt(KINGDOMS.size()))));
        }
        int checked = 0;
        for (int round = 0; round < 1000; round++) {
            change(random, registry, removed, withBody);

            // Now and then a wide reach, where fewer squares hold citizens than the reach covers.
            int activation = random.nextInt(10) == 0 ? random.nextInt(20, 41) : random.nextInt(0, 9);
            List<ActiveSelection.PlayerSpot> players = new ArrayList<>();
            for (int i = random.nextInt(0, 5); i > 0; i--) {
                players.add(new ActiveSelection.PlayerSpot(DIMENSIONS[random.nextInt(DIMENSIONS.length)], random.nextInt(-36, 36),
                        random.nextInt(-36, 36)));
            }

            List<UUID> byLooking = new ArrayList<>();
            for (Citizen citizen : registry.all()) {
                if (selected(citizen, withBody, players, activation)) {
                    byLooking.add(citizen.id());
                }
            }

            int reach = ActiveSelection.reach(activation);
            Set<UUID> near = new HashSet<>();
            for (ActiveSelection.PlayerSpot player : players) {
                registry.near(player.dimension(), player.chunkX(), player.chunkZ(), reach, near);
            }
            assertTrue(near.containsAll(byLooking), "every citizen the full look selects is near at round " + round);
            for (Citizen citizen : registry.all()) {
                if (anyPlaceWithin(citizen, players, reach)) {
                    assertTrue(near.contains(citizen.id()), citizen.id() + " has a place within reach at round " + round);
                }
            }

            List<UUID> byIndex = new ArrayList<>();
            for (Citizen citizen : registry.inOrder(near)) {
                if (selected(citizen, withBody, players, activation)) {
                    byIndex.add(citizen.id());
                }
            }
            assertEquals(byLooking, byIndex, "the selection at round " + round);
            checked += byLooking.size();
        }
        assertTrue(checked > 1000, "the rounds selected somebody: " + checked);
    }

    @Test
    void inOrderFollowsThePopulationAndLeavesOutTheDeadAndUnknown() {
        SplittableRandom random = new SplittableRandom(5);
        CitizenRegistry registry = new CitizenRegistry();
        List<Citizen> citizens = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Citizen citizen = newCitizen(random, KINGDOMS.get(0));
            registry.add(citizen);
            citizens.add(citizen);
        }
        registry.remove(citizens.get(1).id());
        registry.add(citizens.get(1));
        registry.die(citizens.get(3).id(), CauseOfDeath.FALL, "", 0L, null);
        List<UUID> ids = new ArrayList<>();
        for (int i = citizens.size() - 1; i >= 0; i--) {
            ids.add(citizens.get(i).id());
            ids.add(citizens.get(i).id());
        }
        ids.add(UUID.randomUUID());
        assertEquals(List.of(citizens.get(0), citizens.get(2), citizens.get(4), citizens.get(1)), registry.inOrder(ids));
        assertEquals(List.copyOf(registry.all()), registry.inOrder(ids));
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Some citizens move, walk off or come back, take or lose a home or a workplace, die, arrive or get or lose a body.
     */
    private static void change(SplittableRandom random, CitizenRegistry registry, List<Citizen> removed, Set<UUID> withBody) {
        List<Citizen> living = new ArrayList<>(registry.all());
        for (int i = random.nextInt(0, 40); i > 0 && !living.isEmpty(); i--) {
            Citizen citizen = living.get(random.nextInt(living.size()));
            int op = random.nextInt(100);
            if (op < 40) {
                WorldPos at = citizen.lastPos();
                citizen.setLastPos(random.nextInt(4) == 0 ? randomPos(random)
                        : new WorldPos(at.dimension(), at.x() + random.nextInt(-20, 21), at.y(), at.z() + random.nextInt(-20, 21)));
            } else if (op < 52) {
                citizen.setHome(random.nextInt(3) == 0 ? null : randomPos(random));
            } else if (op < 64) {
                citizen.setWorkplace(random.nextInt(3) == 0 ? null : randomPos(random));
            } else if (op < 72) {
                if (!withBody.remove(citizen.id())) {
                    withBody.add(citizen.id());
                }
            } else if (op < 78) {
                registry.remove(citizen.id());
                removed.add(citizen);
                living.remove(citizen);
            } else if (op < 82) {
                registry.die(citizen.id(), CauseOfDeath.FALL, "", 0L, null);
                living.remove(citizen);
            } else if (op < 88 && !removed.isEmpty()) {
                // A citizen that walked off comes back, and moves while it is away.
                Citizen back = removed.remove(random.nextInt(removed.size()));
                back.setLastPos(randomPos(random));
                registry.add(back);
                living.add(back);
            } else if (op < 94) {
                Citizen newcomer = newCitizen(random, KINGDOMS.get(random.nextInt(KINGDOMS.size())));
                registry.add(newcomer);
                living.add(newcomer);
            } else if (op < 97) {
                // Restored from saved data, with places set before the citizens join.
                UUID kingdom = KINGDOMS.get(random.nextInt(KINGDOMS.size()));
                List<Citizen> restored = new ArrayList<>();
                for (int n = random.nextInt(1, 4); n > 0; n--) {
                    Citizen arrived = newCitizen(random, kingdom);
                    arrived.restore(Citizen.MAX_HEALTH, CitizenState.HEALTHY, Professions.UNEMPLOYED.id(), randomPos(random),
                            random.nextBoolean() ? null : randomPos(random), CitizenNeeds.MAX_FOOD, CitizenNeeds.NEUTRAL_MOOD, 0, 0, "",
                            0.0, WorkStatus.NONE);
                    restored.add(arrived);
                }
                registry.restore(kingdom, restored, CitizenRegistry.NEVER, List.of());
                living.addAll(restored);
            } else if (random.nextInt(50) == 0) {
                List<Citizen> gone = registry.removeKingdom(KINGDOMS.get(random.nextInt(KINGDOMS.size())));
                removed.addAll(gone);
                living.removeAll(gone);
            }
        }
        if (registry.size() < 100) {
            for (int i = 0; i < 100; i++) {
                registry.add(newCitizen(random, KINGDOMS.get(random.nextInt(KINGDOMS.size()))));
            }
        }
    }

    /**
     * Whether the selection would give a citizen a body by the places of its record, as {@code CitizenBodies} does.
     */
    private static boolean selected(Citizen citizen, Set<UUID> withBody, List<ActiveSelection.PlayerSpot> players, int activation) {
        List<WorldPos> anchors = new ArrayList<>(ActiveSelection.anchors(citizen));
        boolean active = withBody.contains(citizen.id());
        if (active) {
            anchors.add(citizen.lastPos());
        }
        int distance = ActiveSelection.distance(anchors, players);
        if (active && distance != Integer.MAX_VALUE) {
            distance = Math.max(0, distance - ActiveSelection.KEEP_SLACK);
        }
        return distance <= activation;
    }

    private static boolean anyPlaceWithin(Citizen citizen, List<ActiveSelection.PlayerSpot> players, int reach) {
        List<WorldPos> places = new ArrayList<>();
        places.add(citizen.home());
        places.add(citizen.workplace());
        places.add(citizen.lastPos());
        return ActiveSelection.distance(places, players) <= reach;
    }

    private static Set<UUID> found(CitizenCells cells, String dimension, int chunkX, int chunkZ, int radius) {
        Set<UUID> ids = new HashSet<>();
        cells.near(dimension, chunkX, chunkZ, radius, ids);
        return ids;
    }

    private static Citizen newCitizen(SplittableRandom random, UUID kingdom) {
        Citizen citizen = new Citizen(new UUID(random.nextLong(), random.nextLong()), kingdom, random.nextInt(), Gender.MALE, 0, 0L,
                randomPos(random));
        if (random.nextBoolean()) {
            citizen.setHome(randomPos(random));
        }
        if (random.nextBoolean()) {
            citizen.setWorkplace(randomPos(random));
        }
        return citizen;
    }

    private static WorldPos randomPos(SplittableRandom random) {
        return new WorldPos(DIMENSIONS[random.nextInt(DIMENSIONS.length)], random.nextInt(-500, 500), 64, random.nextInt(-500, 500));
    }
}
