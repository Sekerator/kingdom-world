package io.github.sekerator.kingdomworld.common.npc;

import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.core.land.Tier;

import java.util.List;
import java.util.Locale;

/**
 * The settings of the settlements of the states from the config, with their defaults where the config is not loaded (tests).
 */
public final class SettlementSettings {
    private static final int[] TRIGGER = {16, 14, 12, 10};

    private SettlementSettings() {
    }

    /**
     * How close a player makes a settlement of a rank rise at once, in chunks.
     */
    public static int triggerChunks(Tier tier) {
        int index = switch (tier) {
            case CITY -> 0;
            case TOWN -> 1;
            case VILLAGE -> 2;
            case HAMLET -> 3;
            case CAPITAL -> -1;
        };
        if (index < 0) {
            return 0;
        }
        try {
            List<? extends Integer> list = KingdomWorldConfig.SERVER.npcKingdoms.settlementTriggerChunks.get();
            return index < list.size() ? list.get(index) : TRIGGER[index];
        } catch (IllegalStateException e) {
            return TRIGGER[index];
        }
    }

    /**
     * Whether settlements of a rank are raised in the background near players.
     */
    public static boolean background(Tier tier) {
        try {
            return KingdomWorldConfig.SERVER.npcKingdoms.backgroundTiers.get().contains(tier.name().toLowerCase(Locale.ROOT));
        } catch (IllegalStateException e) {
            return tier == Tier.CITY || tier == Tier.TOWN;
        }
    }

    public static int backgroundBlocks() {
        try {
            return KingdomWorldConfig.SERVER.npcKingdoms.backgroundSettlementBlocks.get();
        } catch (IllegalStateException e) {
            return 2000;
        }
    }

    public static int maxNamedCitizens() {
        try {
            return KingdomWorldConfig.SERVER.npcKingdoms.maxNamedCitizens.get();
        } catch (IllegalStateException e) {
            return 5000;
        }
    }

    public static int keepDays() {
        try {
            return KingdomWorldConfig.SERVER.npcKingdoms.settlerKeepDays.get();
        } catch (IllegalStateException e) {
            return 2;
        }
    }
}
