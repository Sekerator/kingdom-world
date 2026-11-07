package io.github.sekerator.kingdomworld.common.npc;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

/**
 * The rulers of the kingdoms of the world. A ruler takes a turn once a game day; what it decides by its character and traits
 * (taxes, building, hiring, trade, land, roads to its neighbours, laws, answers to robbers) is added here later. For now the
 * turn only keeps the day.
 */
public final class NpcRulers {
    private static final Logger LOGGER = LogUtils.getLogger();

    private NpcRulers() {
    }

    /**
     * The daily turn of a ruler.
     */
    static void turn(MinecraftServer server, NpcKingdom kingdom, long day) {
        LOGGER.debug("Day {}: the ruler of {} ({}) keeps its kingdom as it is", day, kingdom.name().latin(), kingdom.character().id());
    }
}
