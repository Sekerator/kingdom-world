package io.github.sekerator.kingdomworld.core.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The rules of the rulers of the kingdoms of the world. Values by level are given for small, medium and large kingdoms in this
 * order; wanted buildings by the level of the capital as a city (village, town, large city).
 *
 * @param turnEveryDays      a ruler takes a turn every this many game days
 * @param decisionsPerTurn   decisions a ruler makes in a turn at most, so that its kingdom changes step by step
 * @param decreesPerDay      decrees of one kingdom the chronicle takes in a day at most
 * @param maxChunks          the land a kingdom of the world may hold at most, in chunks, by its size
 * @param chunksPerClaim     chunks claimed by one decision at most
 * @param freeBeds           a house is laid out once no more than this many beds are free
 * @param storageFullPercent a storehouse is laid out once the storehouses are this full
 * @param roles              the buildings a kingdom wants by the level of its capital, in the order they are built
 * @param townPopulation     a kingdom with at least this many citizens founds a new town
 * @param maxTowns           towns a kingdom of the world has at most
 * @param reserveDays        days of expenses a ruler keeps in its treasury; below that it counts itself poor
 * @param soldiersPermille   soldiers for every thousand citizens a ruler of no particular leaning keeps
 * @param slackPermille      how far below the worth of goods a ruler still sells them (or above, buys them)
 * @param roadDistance       roads are laid to neighbours this close at most, in blocks
 * @param festivalMood       a feast is held when the mood of the citizens falls below this
 */
public record RulerRules(int turnEveryDays, int decisionsPerTurn, int decreesPerDay, int[] maxChunks, int chunksPerClaim, int freeBeds,
                         int storageFullPercent, List<RoleWant> roles, int townPopulation, int maxTowns, int reserveDays,
                         int soldiersPermille, int slackPermille, int roadDistance, int festivalMood) {
    public static final RulerRules DEFAULT = new RulerRules(1, 3, 2, new int[]{96, 160, 256}, 4, 1, 85,
            RoleWant.parse(List.of("warehouse=1,1,2", "market=1,1,1", "well=1,2,3", "workshop=1,2,3", "barracks=1,1,2", "tower=1,2,4",
                    "tavern=0,1,1", "mage_tower=0,0,1")), 40, 4, 5, 100, 100, 1200, 35);

    /**
     * How many buildings of a role a kingdom wants at each level of its capital.
     */
    public record RoleWant(String role, int[] counts) {
        public RoleWant {
            if (role == null || role.isBlank() || counts == null || counts.length != 3) {
                throw new IllegalArgumentException("A role and three counts are needed");
            }
            for (int count : counts) {
                if (count < 0 || count > 16) {
                    throw new IllegalArgumentException("Count out of range: " + count);
                }
            }
            role = role.trim().toLowerCase(Locale.ROOT);
            counts = counts.clone();
        }

        @Override
        public int[] counts() {
            return counts.clone();
        }

        public int count(int cityLevel) {
            return counts[Math.max(0, Math.min(2, cityLevel))];
        }

        /**
         * Reads {@code role=village,town,city}, for example {@code market=1,1,2}.
         */
        public static Optional<RoleWant> parse(String text) {
            if (text == null) {
                return Optional.empty();
            }
            int equals = text.indexOf('=');
            if (equals <= 0) {
                return Optional.empty();
            }
            String[] parts = text.substring(equals + 1).split(",");
            if (parts.length != 3) {
                return Optional.empty();
            }
            try {
                int[] counts = new int[3];
                for (int i = 0; i < 3; i++) {
                    counts[i] = Integer.parseInt(parts[i].trim());
                }
                return Optional.of(new RoleWant(text.substring(0, equals), counts));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        public static List<RoleWant> parse(List<? extends String> texts) {
            List<RoleWant> list = new ArrayList<>();
            for (String text : texts) {
                parse(text).ifPresent(list::add);
            }
            return List.copyOf(list);
        }

        public String text() {
            return role + "=" + counts[0] + "," + counts[1] + "," + counts[2];
        }
    }

    public RulerRules {
        if (maxChunks == null || maxChunks.length != 3) {
            throw new IllegalArgumentException("Three sizes of land are needed");
        }
        for (int value : maxChunks) {
            if (value < 9 || value > 4096) {
                throw new IllegalArgumentException("Land out of range: " + value);
            }
        }
        maxChunks = maxChunks.clone();
        roles = List.copyOf(roles);
        if (turnEveryDays < 1 || decisionsPerTurn < 0 || decreesPerDay < 0 || chunksPerClaim < 1 || freeBeds < 0 || storageFullPercent < 10
                || storageFullPercent > 100 || townPopulation < 1 || maxTowns < 0 || reserveDays < 0 || soldiersPermille < 0
                || soldiersPermille > 1000 || slackPermille < 0 || slackPermille > 900 || roadDistance < 0 || festivalMood < 0) {
            throw new IllegalArgumentException("Rules of the rulers out of range");
        }
    }

    @Override
    public int[] maxChunks() {
        return maxChunks.clone();
    }

    public int maxChunks(NpcLevel level) {
        return maxChunks[level.ordinal()];
    }

    public RulerRules withDecisionsPerTurn(int value) {
        return new RulerRules(turnEveryDays, value, decreesPerDay, maxChunks, chunksPerClaim, freeBeds, storageFullPercent, roles,
                townPopulation, maxTowns, reserveDays, soldiersPermille, slackPermille, roadDistance, festivalMood);
    }

    public RulerRules withMaxChunks(int[] value) {
        return new RulerRules(turnEveryDays, decisionsPerTurn, decreesPerDay, value, chunksPerClaim, freeBeds, storageFullPercent, roles,
                townPopulation, maxTowns, reserveDays, soldiersPermille, slackPermille, roadDistance, festivalMood);
    }
}
