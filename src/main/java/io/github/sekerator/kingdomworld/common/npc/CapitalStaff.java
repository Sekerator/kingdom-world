package io.github.sekerator.kingdomworld.common.npc;

import io.github.sekerator.kingdomworld.core.npc.NpcComposition;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * The work in the buildings of a styled capital once they stand: the registrar in the guild house, the minter in the treasury,
 * the builders and carriers in their yards, the bank in the treasury and the front gardens of the houses. Safe to repeat.
 */
final class CapitalStaff {
    private CapitalStaff() {
    }

    /**
     * Sets the posts of work in the buildings of a styled capital, places its bank and lays its front gardens.
     *
     * @param id the kingdom of the capital
     */
    static void staff(MinecraftServer server, ServerLevel level, NpcSite site, NpcComposition composition, UUID id) {
    }
}
