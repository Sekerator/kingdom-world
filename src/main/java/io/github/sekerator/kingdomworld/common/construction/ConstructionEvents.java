package io.github.sekerator.kingdomworld.common.construction;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;

/**
 * Wires construction into the game: the templates load with the data packs, their costs are worked out again when tags
 * change, and once a second the constructions move on.
 */
public final class ConstructionEvents {

    private ConstructionEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(ConstructionEvents::onReload);
        MinecraftForge.EVENT_BUS.addListener(ConstructionEvents::onTags);
        MinecraftForge.EVENT_BUS.addListener(ConstructionEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(ConstructionEvents::onChunkUnload);
    }

    private static void onReload(AddReloadListenerEvent event) {
        event.addListener(new BuildingRoleLoader());
        event.addListener(new BuildingTemplates.Loader());
    }

    private static void onTags(TagsUpdatedEvent event) {
        // Which blocks are a wood or a stone depends on tags, on the server and on the client.
        TemplateMaterials.reset();
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            BuildingTemplates.tagsChanged();
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        long tick = event.getServer().getTickCount();
        if (tick % 20 == 0) {
            ConstructionSettings.refresh();
            ConstructionWork.tick(event.getServer(), tick);
        }
    }

    private static void onChunkUnload(net.minecraftforge.event.level.ChunkEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level && level.getServer().isSameThread()
                && event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk) {
            BuildingEffects.onChunkUnload(level, chunk);
        }
    }

    /**
     * Forgets everything kept in memory, when the server stops.
     */
    public static void reset() {
        ConstructionWork.reset();
        ConstructionSettings.reset();
    }
}
