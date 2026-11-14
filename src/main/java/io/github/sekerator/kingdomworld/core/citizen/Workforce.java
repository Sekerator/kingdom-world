package io.github.sekerator.kingdomworld.core.citizen;

import io.github.sekerator.kingdomworld.core.life.DaySchedule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * What the window of the workers of a kingdom tells about its citizens: what each of them lacks, what it is doing far from
 * players, who may take a seat at a post and in which order, and the numbers of the kingdom's work. Pure rules; the game fills
 * in what only the world knows (the tool a profession needs, whether a body sleeps or sits in the tavern).
 */
public final class Workforce {
    /** Below this satiety a citizen counts as hungry. */
    public static final float HUNGRY_BELOW = CitizenNeeds.MAX_FOOD * 0.25f;

    private Workforce() {
    }

    // ---------------------------------------------------------------- what a citizen lacks

    /**
     * Something a citizen lacks to work or to live well.
     */
    public enum Need {
        /** The tool of its work, named by the game. */
        TOOL,
        /** A tool strong enough for what is left in the zone. */
        BETTER_TOOL,
        SAPLINGS,
        FEED,
        FUEL,
        MATERIALS,
        WORKSTATION,
        STOREHOUSE,
        STORAGE_SPACE,
        /** A post of its profession. */
        WORKPLACE,
        CARE,
        FOOD,
        HOME;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /**
         * What the state of the work says is lacking, or {@code null} when the work lacks nothing it could be given.
         */
        public static Need of(WorkStatus status) {
            return switch (status) {
                case NO_TOOL -> TOOL;
                case TOOL_TOO_WEAK -> BETTER_TOOL;
                case NO_SAPLINGS -> SAPLINGS;
                case NO_FEED -> FEED;
                case NO_FUEL -> FUEL;
                case NO_INGREDIENTS, NO_MATERIALS -> MATERIALS;
                case NO_WORKSTATION -> WORKSTATION;
                case NO_STOREHOUSE -> STOREHOUSE;
                case STORAGE_FULL -> STORAGE_SPACE;
                case NO_WORKPLACE -> WORKPLACE;
                case WOUNDED -> CARE;
                default -> null;
            };
        }
    }

    /**
     * What a citizen lacks: what its work stands still for, care when wounded, food when hungry and a bed when homeless.
     */
    public static List<Need> needs(Citizen citizen) {
        List<Need> needs = new ArrayList<>();
        Need work = withoutPost(citizen) ? Need.WORKPLACE : Need.of(citizen.workStatus());
        if (work != null) {
            needs.add(work);
        }
        if (citizen.state() == CitizenState.WOUNDED && !needs.contains(Need.CARE)) {
            needs.add(Need.CARE);
        }
        if (citizen.food() < HUNGRY_BELOW) {
            needs.add(Need.FOOD);
        }
        if (citizen.home() == null) {
            needs.add(Need.HOME);
        }
        return needs;
    }

    /**
     * Whether a citizen's profession needs a workplace it does not have.
     */
    public static boolean withoutPost(Citizen citizen) {
        return Professions.worksAtPosts(citizen.professionId()) && citizen.workplace() == null;
    }

    /**
     * Whether a citizen has something the king could see to: its work stands still for a reason, or it lacks anything.
     */
    public static boolean hasProblem(Citizen citizen) {
        return citizen.workStatus().isProblem() || !needs(citizen).isEmpty();
    }

    /**
     * The order of the list of the window: citizens with problems first, then by profession in the order of the registry,
     * then in the order they came.
     */
    public static Comparator<Citizen> problemsFirst() {
        List<Profession> order = Professions.REGISTRY.all();
        return Comparator.comparing((Citizen citizen) -> !hasProblem(citizen))
                .thenComparingInt(citizen -> {
                    for (int i = 0; i < order.size(); i++) {
                        if (order.get(i).id().equals(citizen.professionId())) {
                            return i;
                        }
                    }
                    return order.size();
                });
    }

    // ---------------------------------------------------------------- who may take a seat

    /**
     * How a citizen stands to a post that may take it.
     */
    public enum Candidate {
        /** Of the post's profession and without a workplace: given the profession at the throne, or its post went away. */
        IDLE_SAME(0),
        /** Without work. */
        UNEMPLOYED(1),
        /** Works at another post of the same profession; moves over. */
        SAME_ELSEWHERE(-1),
        /** Has other work, which it leaves for this post's profession. */
        OTHER_WORK(-1),
        /** Works here already. */
        HERE(-1),
        /** A soldier: the army gives and takes that work. */
        SOLDIER(-1);

        private final int hireRank;

        Candidate(int hireRank) {
            this.hireRank = hireRank;
        }

        /**
         * Rank among those the post takes on its own (the hire button): lower first, negative for those it never takes so.
         */
        public int hireRank() {
            return hireRank;
        }

        /**
         * Whether putting the citizen on the post takes it from other work, which the window asks to confirm.
         */
        public boolean transfer() {
            return this == SAME_ELSEWHERE || this == OTHER_WORK;
        }

        public boolean allowed() {
            return this != HERE && this != SOLDIER;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static Candidate candidate(Citizen citizen, WorkPost post) {
        String profession = citizen.professionId();
        if (Professions.isMilitary(profession)) {
            return Candidate.SOLDIER;
        }
        if (post.workers().contains(citizen.id()) || post.pos().equals(citizen.workplace())) {
            return Candidate.HERE;
        }
        if (profession.equals(post.professionId()) && post.hasProfession()) {
            return citizen.workplace() == null ? Candidate.IDLE_SAME : Candidate.SAME_ELSEWHERE;
        }
        if (Professions.UNEMPLOYED.id().equals(profession)) {
            return Candidate.UNEMPLOYED;
        }
        return Candidate.OTHER_WORK;
    }

    /**
     * Citizens a post may take, in the order the window offers them: those of its profession without a post, those without
     * work, then the others who would move over; each group nearest first. Soldiers and its own workers are left out.
     */
    public static List<Citizen> candidates(List<Citizen> citizens, WorkPost post) {
        List<Citizen> result = new ArrayList<>();
        for (Citizen citizen : citizens) {
            if (citizen.isAlive() && candidate(citizen, post).allowed()) {
                result.add(citizen);
            }
        }
        result.sort(Comparator.comparingInt((Citizen citizen) -> candidate(citizen, post).ordinal())
                .thenComparingDouble(citizen -> citizen.lastPos().dimension().equals(post.pos().dimension())
                        ? citizen.lastPos().distanceSqr(post.pos()) : Double.MAX_VALUE));
        return result;
    }

    /**
     * Professions a citizen may get without a post: those that need no workplace and the farmer, who finds a composter of its
     * own. Neither the soldiers' nor being without work, which the dismissal gives.
     */
    public static List<Profession> professionsWithoutPost() {
        List<Profession> result = new ArrayList<>();
        for (Profession profession : Professions.REGISTRY.all()) {
            if (Professions.isMilitary(profession.id()) || Professions.UNEMPLOYED.id().equals(profession.id())) {
                continue;
            }
            if (!SiteKinds.WORK_POST.equals(profession.workplaceKind())) {
                result.add(profession);
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- what a citizen is doing

    /**
     * What a citizen is doing now, told in the window.
     */
    public enum Doing {
        /** At its work; the window names the work of the profession. */
        WORKING,
        /** Carries what it gathered to a container or a storehouse. */
        CARRYING,
        /** Gets ready: fetches a tool, walks to the zone. */
        PREPARING,
        /** On the way to work in the morning. */
        GOING_TO_WORK,
        SLEEPING,
        TAVERN,
        FESTIVAL,
        /** The evening at home or a walk. */
        RESTING,
        /** Wounded and being looked after. */
        HEALING,
        SICK,
        /** Without work. */
        IDLE,
        /** A soldier in the army. */
        SERVING,
        /** The work stands still; the window says why. */
        STANDING,
        /** Goes back home or to its post. */
        RETURNING;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * What a citizen far from players is doing, from its day and its work.
     *
     * @param phase    the part of the citizen's own day
     * @param sick     whether it is ill
     * @param festival whether its kingdom rests for a festival
     */
    public static Doing doing(Citizen citizen, DaySchedule.Phase phase, boolean sick, boolean festival) {
        if (citizen.state() == CitizenState.WOUNDED) {
            return Doing.HEALING;
        }
        if (sick) {
            return Doing.SICK;
        }
        if (Professions.isMilitary(citizen.professionId())) {
            return Doing.SERVING;
        }
        if (festival) {
            return Doing.FESTIVAL;
        }
        if (phase == DaySchedule.Phase.NIGHT) {
            return Doing.SLEEPING;
        }
        if (phase == DaySchedule.Phase.EVENING) {
            return Doing.RESTING;
        }
        if (Professions.UNEMPLOYED.id().equals(citizen.professionId())) {
            return Doing.IDLE;
        }
        if (withoutPost(citizen) || citizen.workStatus().isProblem()) {
            return Doing.STANDING;
        }
        return phase == DaySchedule.Phase.MORNING ? Doing.GOING_TO_WORK : Doing.WORKING;
    }

    // ---------------------------------------------------------------- the numbers of a kingdom

    /**
     * How many citizens work, are without work, have a profession but no post, and serve in the army.
     */
    public record Counts(int working, int unemployed, int withoutPost, int soldiers) {
    }

    public static Counts counts(List<Citizen> citizens) {
        int working = 0;
        int unemployed = 0;
        int withoutPost = 0;
        int soldiers = 0;
        for (Citizen citizen : citizens) {
            String profession = citizen.professionId();
            if (Professions.isMilitary(profession)) {
                soldiers++;
            } else if (Professions.UNEMPLOYED.id().equals(profession)) {
                unemployed++;
            } else if (withoutPost(citizen)) {
                withoutPost++;
            } else {
                working++;
            }
        }
        return new Counts(working, unemployed, withoutPost, soldiers);
    }
}
