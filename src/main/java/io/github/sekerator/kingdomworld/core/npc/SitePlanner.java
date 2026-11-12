package io.github.sekerator.kingdomworld.core.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.function.Predicate;

/**
 * The plan of the places of the kingdoms of a world, the same for the same seed. Candidates come one after another from the
 * seed; the server judges each by the land (biome and lay of the ground) and by what stands there already, over as many ticks as
 * it needs, and the planner remembers only where it is ({@link #cursor()}, {@link #shift()} and {@link #pass()}). A candidate
 * whose land is taken or was already generated is shifted around its place before the planner gives it up; one too close to the
 * spawn of the world or to a capital of the plan is skipped without being judged.
 *
 * <p>How large the kingdoms are is a quota, not chance: the plan makes so many of each level, and a candidate is judged for the
 * largest level still wanted first, then for the smaller ones. When the candidates of a pass run out before the plan is full,
 * the planner looks at as many new ones with less asked of a place ({@link Candidate#relax()}); on the last pass a place meant
 * for a larger kingdom may go to a smaller one, so that the world still gets all its kingdoms. Kingdoms beyond the quota draw
 * their level by the weights.
 */
public final class SitePlanner {
    /** Places tried around a candidate whose land is taken, in 8 directions. */
    public static final int SHIFTS = 8;
    /** How far a shifted candidate lies from the original one, in blocks. */
    public static final int SHIFT_STEP = 320;
    /** Passes over the candidates: the first asks everything of a place, each later one less. */
    public static final int PASSES = 4;
    /** The pass from which capitals may stand closer to each other. */
    public static final int CLOSER_FROM = 2;

    /**
     * What the server found at a candidate.
     */
    public enum Verdict {
        /** A kingdom may appear there. */
        GOOD,
        /** Water, mountains, a swamp or ground too rough for a kingdom of this level. */
        UNSUITABLE,
        /** Land of a kingdom nearby, or chunks generated already: the planner looks around it. */
        OCCUPIED
    }

    /**
     * A place to judge.
     *
     * @param index the number of the candidate
     * @param shift 0 for the candidate itself, 1 to {@value #SHIFTS} for the places around it
     * @param level the level the place is judged for
     * @param relax how much less is asked of the place: 0 on the first pass over the candidates, more on later ones
     */
    public record Candidate(int index, int shift, int x, int z, NpcLevel level, long seed, int relax) {
    }

    private final long worldSeed;
    private final NpcRules rules;
    private final int spawnX;
    private final int spawnZ;
    private final List<int[]> taken = new ArrayList<>();
    private final int[] made = new int[NpcLevel.values().length];
    private int planned;
    private int cursor;
    private int shift;
    private int pass;
    /** Which of the levels still wanted the current candidate is judged for: 0 is the largest. */
    private int tier;

    /**
     * @param sites  the places planned already (their capitals keep others away)
     * @param cursor the candidate to go on from
     * @param shift  the shift of that candidate to go on from
     */
    public SitePlanner(long worldSeed, NpcRules rules, int spawnX, int spawnZ, List<NpcSite> sites, int cursor, int shift) {
        this(worldSeed, rules, spawnX, spawnZ, sites, cursor, shift, 0);
    }

    /**
     * @param pass the pass over the candidates to go on from
     */
    public SitePlanner(long worldSeed, NpcRules rules, int spawnX, int spawnZ, List<NpcSite> sites, int cursor, int shift, int pass) {
        this.worldSeed = worldSeed;
        this.rules = rules;
        this.spawnX = spawnX;
        this.spawnZ = spawnZ;
        for (NpcSite site : sites) {
            taken.add(new int[]{site.x(), site.z()});
            made[site.level().ordinal()]++;
        }
        this.planned = sites.size();
        this.cursor = Math.max(0, cursor);
        this.shift = Math.max(0, Math.min(SHIFTS, shift));
        this.pass = Math.max(0, Math.min(PASSES - 1, pass));
    }

    public int cursor() {
        return cursor;
    }

    public int shift() {
        return shift;
    }

    /**
     * The pass over the candidates the planner is on, from 0.
     */
    public int pass() {
        return pass;
    }

    /**
     * How many places of a level the plan has.
     */
    public int made(NpcLevel level) {
        return made[level.ordinal()];
    }

    /**
     * Whether the plan has all its places, or looked at every candidate it may on every pass.
     */
    public boolean done() {
        return planned >= rules.count() || (cursor >= limit() && pass >= PASSES - 1);
    }

    private int limit() {
        return (int) Math.min(Integer.MAX_VALUE, (long) Math.max(1, rules.count()) * rules.attemptsPerSite());
    }

    /**
     * The levels a candidate is judged for now, the largest first: those whose quota is not full, and on the last pass the
     * smaller ones too, which take the places the larger ones did not find. Empty once the quota is full.
     */
    private List<NpcLevel> wanted() {
        List<NpcLevel> list = new ArrayList<>();
        NpcLevel[] levels = NpcLevel.values();
        boolean open = false;
        for (int i = levels.length - 1; i >= 0; i--) {
            boolean lacking = made[i] < rules.quota(levels[i]);
            if (lacking || (open && pass >= PASSES - 1)) {
                list.add(levels[i]);
            }
            open |= lacking;
        }
        return list;
    }

    /**
     * The candidate to judge now; candidates too close to the spawn or to a capital, or too far away, are passed over.
     */
    public Optional<Candidate> current() {
        for (int guard = 0; guard < 100_000 && !done(); guard++) {
            if (cursor >= limit()) {
                // The candidates ran out before the plan was full: as many new ones, of which less is asked.
                pass++;
                cursor = 0;
                shift = 0;
                tier = 0;
                continue;
            }
            // Every pass has candidates of its own: land that did not suit is not looked at twice.
            Candidate drawn = candidate(pass * limit() + cursor, shift);
            if (!fits(drawn.x(), drawn.z())) {
                moveOn(shift > 0);
                continue;
            }
            List<NpcLevel> wanted = wanted();
            NpcLevel level = wanted.isEmpty() ? drawn.level() : wanted.get(Math.min(tier, wanted.size() - 1));
            return Optional.of(new Candidate(drawn.index(), drawn.shift(), drawn.x(), drawn.z(), level, drawn.seed(), pass));
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
                made[current.get().level().ordinal()]++;
                planned++;
                cursor++;
                shift = 0;
                tier = 0;
                return current;
            }
            case OCCUPIED -> moveOn(true);
            default -> {
                if (tier + 1 < wanted().size()) {
                    // Too rough for a kingdom this large: a smaller one is still wanted and asks less of the place.
                    tier++;
                } else {
                    moveOn(shift > 0);
                }
            }
        }
        return Optional.empty();
    }

    private void moveOn(boolean shifting) {
        tier = 0;
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
        double apart = minDistance(rules, pass);
        for (int[] other : taken) {
            if (Math.hypot(x - other[0], z - other[1]) < apart) {
                return false;
            }
        }
        return true;
    }

    /**
     * The least distance between two capitals on a pass: three quarters of the rule from the pass {@value #CLOSER_FROM} on.
     */
    public static double minDistance(NpcRules rules, int pass) {
        return pass >= CLOSER_FROM ? rules.minDistance() * 0.75 : rules.minDistance();
    }

    /**
     * A candidate of the plan as it is drawn: the same for the same seed of the world, index and shift. Its level is the one
     * the weights give it; the plan judges it for the levels of its quota first.
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
        return new Candidate(index, shiftIndex, x, z, level, seed, 0);
    }

    /**
     * Places for towns around a capital, in the order they are tried: opposite sides first, so that towns stand apart.
     */
    public static List<int[]> townCandidates(int x, int z, long seed, int distance) {
        SplittableRandom random = new SplittableRandom(seed ^ 0x544F574E53L);
        double base = random.nextDouble() * Math.PI * 2;
        int[] order = {0, 4, 2, 6, 1, 5, 3, 7};
        List<int[]> list = new ArrayList<>();
        // Two rings: the distance of the rules first, then a third further out where the nearer land does not suit.
        for (double ring : new double[]{1.0, 1.35}) {
            for (int step : order) {
                double angle = base + step * Math.PI / 4 + (ring > 1 ? Math.PI / 8 : 0);
                int reach = (int) Math.round(distance * ring) + random.nextInt(Math.max(1, distance / 5));
                list.add(new int[]{x + (int) Math.round(Math.cos(angle) * reach), z + (int) Math.round(Math.sin(angle) * reach)});
            }
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
