package io.github.sekerator.kingdomworld.core.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.function.Predicate;

/**
 * The plan of the places of the kingdoms of a world, the same for the same seed. Candidates come one after another from the
 * seed; the server judges each by the land (biome and lay of the ground) and by what stands there already, over as many ticks as
 * it needs, and the planner remembers only where it is ({@link #cursor()} and {@link #shift()}). A candidate whose land is taken
 * or was already generated is shifted around its place before the planner gives it up; one too close to the spawn of the world
 * or to a capital of the plan is skipped without being judged.
 */
public final class SitePlanner {
    /** Places tried around a candidate whose land is taken, in 8 directions. */
    public static final int SHIFTS = 8;
    /** How far a shifted candidate lies from the original one, in blocks. */
    public static final int SHIFT_STEP = 320;

    /**
     * What the server found at a candidate.
     */
    public enum Verdict {
        /** A kingdom may appear there. */
        GOOD,
        /** Water, mountains, a swamp or rough ground. */
        UNSUITABLE,
        /** Land of a kingdom nearby, or chunks generated already: the planner looks around it. */
        OCCUPIED
    }

    /**
     * A place to judge.
     *
     * @param index the number of the candidate
     * @param shift 0 for the candidate itself, 1 to {@value #SHIFTS} for the places around it
     */
    public record Candidate(int index, int shift, int x, int z, NpcLevel level, long seed) {
    }

    private final long worldSeed;
    private final NpcRules rules;
    private final int spawnX;
    private final int spawnZ;
    private final List<int[]> taken = new ArrayList<>();
    private int planned;
    private int cursor;
    private int shift;

    /**
     * @param sites  the places planned already (their capitals keep others away)
     * @param cursor the candidate to go on from
     * @param shift  the shift of that candidate to go on from
     */
    public SitePlanner(long worldSeed, NpcRules rules, int spawnX, int spawnZ, List<NpcSite> sites, int cursor, int shift) {
        this.worldSeed = worldSeed;
        this.rules = rules;
        this.spawnX = spawnX;
        this.spawnZ = spawnZ;
        for (NpcSite site : sites) {
            taken.add(new int[]{site.x(), site.z()});
        }
        this.planned = sites.size();
        this.cursor = Math.max(0, cursor);
        this.shift = Math.max(0, Math.min(SHIFTS, shift));
    }

    public int cursor() {
        return cursor;
    }

    public int shift() {
        return shift;
    }

    /**
     * Whether the plan has all its places, or looked at every candidate it may.
     */
    public boolean done() {
        return planned >= rules.count() || cursor >= limit();
    }

    private int limit() {
        return (int) Math.min(Integer.MAX_VALUE, (long) Math.max(1, rules.count()) * rules.attemptsPerSite());
    }

    /**
     * The candidate to judge now; candidates too close to the spawn or to a capital, or too far away, are passed over.
     */
    public Optional<Candidate> current() {
        for (int guard = 0; guard < 10_000 && !done(); guard++) {
            Candidate candidate = candidate(cursor, shift);
            if (fits(candidate.x(), candidate.z())) {
                return Optional.of(candidate);
            }
            moveOn(shift > 0);
        }
        return Optional.empty();
    }

    /**
     * Takes the verdict on the current candidate.
     *
     * @return the candidate if it became a place of the plan
     */
    public Optional<Candidate> judge(Verdict verdict) {
        Optional<Candidate> current = current();
        if (current.isEmpty()) {
            return Optional.empty();
        }
        switch (verdict) {
            case GOOD -> {
                taken.add(new int[]{current.get().x(), current.get().z()});
                planned++;
                cursor++;
                shift = 0;
                return current;
            }
            case OCCUPIED -> moveOn(true);
            default -> moveOn(shift > 0);
        }
        return Optional.empty();
    }

    private void moveOn(boolean shifting) {
        if (shifting && shift < SHIFTS) {
            shift++;
        } else {
            cursor++;
            shift = 0;
        }
    }

    private boolean fits(int x, int z) {
        double fromSpawn = Math.hypot(x - spawnX, z - spawnZ);
        if (fromSpawn < rules.minSpawnDistance() || fromSpawn > rules.radius()) {
            return false;
        }
        for (int[] other : taken) {
            if (Math.hypot(x - other[0], z - other[1]) < rules.minDistance()) {
                return false;
            }
        }
        return true;
    }

    /**
     * A candidate of the plan: the same for the same seed of the world, index and shift.
     */
    public Candidate candidate(int index, int shiftIndex) {
        SplittableRandom random = new SplittableRandom(worldSeed * 0x9E3779B97F4A7C15L + index * 0xBF58476D1CE4E5B9L + 0x4E5043L);
        double angle = random.nextDouble() * Math.PI * 2;
        double inner = Math.min(rules.minSpawnDistance(), rules.radius());
        // Uniform over the ring between the least distance from the spawn and the radius.
        double u = random.nextDouble();
        double distance = Math.sqrt(inner * inner + u * ((double) rules.radius() * rules.radius() - inner * inner));
        NpcLevel level = NpcLevel.pick(rules.sizeWeights(), random.nextDouble());
        long seed = random.nextLong();
        int x = spawnX + (int) Math.round(Math.cos(angle) * distance);
        int z = spawnZ + (int) Math.round(Math.sin(angle) * distance);
        if (shiftIndex > 0) {
            double around = (shiftIndex - 1) * Math.PI / 4;
            x += (int) Math.round(Math.cos(around) * SHIFT_STEP);
            z += (int) Math.round(Math.sin(around) * SHIFT_STEP);
            seed ^= shiftIndex * 0x9E3779B97F4A7C15L;
        }
        return new Candidate(index, shiftIndex, x, z, level, seed);
    }

    /**
     * Places for towns around a capital, in the order they are tried: opposite sides first, so that towns stand apart.
     */
    public static List<int[]> townCandidates(int x, int z, long seed, int distance) {
        SplittableRandom random = new SplittableRandom(seed ^ 0x544F574E53L);
        double base = random.nextDouble() * Math.PI * 2;
        int[] order = {0, 4, 2, 6, 1, 5, 3, 7};
        List<int[]> list = new ArrayList<>();
        for (int step : order) {
            double angle = base + step * Math.PI / 4;
            int reach = distance + random.nextInt(Math.max(1, distance / 5));
            list.add(new int[]{x + (int) Math.round(Math.cos(angle) * reach), z + (int) Math.round(Math.sin(angle) * reach)});
        }
        return list;
    }

    /**
     * Chooses up to {@code count} places for towns among candidates: those the land allows, at least {@code minDistance} apart.
     */
    public static List<int[]> chooseTowns(List<int[]> candidates, Predicate<int[]> suitable, int count, int minDistance) {
        List<int[]> chosen = new ArrayList<>();
        for (int[] candidate : candidates) {
            if (chosen.size() >= count) {
                break;
            }
            boolean apart = true;
            for (int[] other : chosen) {
                if (Math.hypot(candidate[0] - other[0], candidate[1] - other[1]) < minDistance) {
                    apart = false;
                    break;
                }
            }
            if (apart && suitable.test(candidate)) {
                chosen.add(candidate);
            }
        }
        return chosen;
    }
}
