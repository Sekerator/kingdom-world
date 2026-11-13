package io.github.sekerator.kingdomworld.common.npc;

import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.land.Tier;
import io.github.sekerator.kingdomworld.core.npc.Settlement;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * The head of a settlement of a state: the first of its settlers is the governor of a large city, who sits in its residence,
 * the mayor of a small town, the elder of a village or a hamlet. The name over the head of his body says so.
 */
public final class SettlementHeads {
    /** The key of the name of a head, followed by the rank of the settlement. */
    public static final String KEY = "kingdomworld.settlement.head.";

    private SettlementHeads() {
    }

    /**
     * Whether a citizen is the head of its settlement.
     */
    public static boolean isHead(MinecraftServer server, Citizen citizen) {
        return citizen.settlement() != 0 && citizen.id().equals(CitizenLife.settlerId(server.overworld().getSeed(), citizen.settlement(), 0));
    }

    /**
     * The name a body shows: that of the citizen, with the office of the head of a settlement.
     */
    public static Component title(Level level, Citizen citizen, Component name) {
        if (citizen.settlement() == 0 || !(level instanceof ServerLevel serverLevel)) {
            return name;
        }
        MinecraftServer server = serverLevel.getServer();
        if (!isHead(server, citizen)) {
            return name;
        }
        Optional<Settlement> settlement = NpcWork.state(server).settlement(citizen.settlement());
        if (settlement.isEmpty() || settlement.get().tier() == Tier.CAPITAL) {
            return name;
        }
        return Component.translatable(KEY + settlement.get().tier().id(), name);
    }
}
