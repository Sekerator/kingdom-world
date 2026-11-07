package io.github.sekerator.kingdomworld.common.npc;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.npc.NpcRules;

import java.util.List;

/**
 * The rules of the kingdoms of the world of the running server, built from the server config and refreshed once a second like
 * the other rules; game tests may replace them. Before the config is loaded the defaults count.
 */
public final class NpcSettings {
    private static NpcRules override;
    private static NpcRules rules;

    private NpcSettings() {
    }

    public static NpcRules rules() {
        if (override != null) {
            return override;
        }
        NpcRules value = rules;
        if (value == null) {
            value = fromConfig();
            rules = value;
        }
        return value;
    }

    public static void refresh() {
        rules = fromConfig();
    }

    public static void reset() {
        rules = null;
        override = null;
    }

    /**
     * Replaces the configured rules, for game tests; {@code null} restores the config.
     */
    public static void override(NpcRules value) {
        override = value;
    }

    private static NpcRules fromConfig() {
        NpcRules d = NpcRules.DEFAULT;
        try {
            KingdomWorldConfig.NpcKingdoms c = KingdomWorldConfig.SERVER.npcKingdoms;
            int[] money = ints(c.startingMoney.get(), new int[]{(int) d.money()[0], (int) d.money()[1], (int) d.money()[2]});
            return new NpcRules(c.generate.get(), c.count.get(), c.radius.get(), c.minDistance.get(), c.minSpawnDistance.get(),
                    c.triggerChunks.get(), ints(c.sizeWeights.get(), d.sizeWeights()), ints(c.landRadius.get(), d.landRadius()),
                    ints(c.towns.get(), d.towns()), c.townDistance.get(), new long[]{money[0], money[1], money[2]}, c.startingFoodDays.get(),
                    ints(c.startingMaterials.get(), d.materials()), ints(c.soldiers.get(), d.soldiers()), c.blocksPerTick.get(),
                    c.chunksPerTick.get(), c.maxInhabitedMinutes.get(), d.attemptsPerSite());
        } catch (IllegalStateException | IllegalArgumentException e) {
            return d;
        }
    }

    private static int[] ints(List<? extends Integer> values, int[] fallback) {
        if (values == null || values.size() != 3) {
            return fallback;
        }
        return new int[]{values.get(0), values.get(1), values.get(2)};
    }
}
