package io.github.sekerator.kingdomworld.common.npc;

import io.github.sekerator.kingdomworld.core.npc.Settlement;
import net.minecraft.server.MinecraftServer;

/**
 * The named citizens of the settlements of the states: those nobody needs are folded into the numbers of their settlement
 * while no player is near and come back as themselves when one comes.
 */
public final class SettlementResidents {
    private SettlementResidents() {
    }

    public static int foldNow(MinecraftServer server, Settlement settlement) {
        return 0;
    }

    public static int unfoldNow(MinecraftServer server, Settlement settlement) {
        return 0;
    }
}
