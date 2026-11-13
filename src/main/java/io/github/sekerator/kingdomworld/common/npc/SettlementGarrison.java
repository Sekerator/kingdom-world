package io.github.sekerator.kingdomworld.common.npc;

import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.Settlement;
import io.github.sekerator.kingdomworld.core.npc.SettlementLayouts;
import io.github.sekerator.kingdomworld.core.npc.SettlementStage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * The guard, the wall and the laws of a settlement of a state as it is raised.
 */
final class SettlementGarrison {
    private SettlementGarrison() {
    }

    static void guards(MinecraftServer server, Settlement settlement, SettlementLayouts.Layout layout, long deadline) {
        settlement.setStage(SettlementStage.ROADS.id(), 0);
    }

    static void wall(MinecraftServer server, ServerLevel level, Settlement settlement, NpcSite site, SettlementLayouts.Layout layout) {
        settlement.setStage(SettlementStage.LAW.id(), 0);
    }

    static void law(MinecraftServer server, Settlement settlement, NpcSite site, SettlementLayouts.Layout layout) {
        settlement.setStage(SettlementStage.DONE.id(), 0);
    }
}
