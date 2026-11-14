package io.github.sekerator.kingdomworld.common.construction;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomStyles;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.npc.BuildingLooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * The look of the style of a kingdom for a building laid out at an anchor, for any kingdom: the style of the kingdom, its own
 * wood, the dye of its colour, the quarter of the kind of the template, drawn from the kingdom and the anchor so that its houses
 * differ. A kingdom without a known style gets the template's own materials.
 */
public final class KingdomLooks {
    private KingdomLooks() {
    }

    public static Materials materials(MinecraftServer server, UUID kingdomId, String templateId, BlockPos anchor) {
        String style = KingdomStyles.of(server, kingdomId);
        if (style.isEmpty() || !BuildingLooks.styles().contains(style)) {
            return Materials.NONE;
        }
        BuildingLooks.Quarter quarter = io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.get(templateId)
                .map(entry -> io.github.sekerator.kingdomworld.core.npc.RulerTemplates.quarter(entry.kind())).orElse(BuildingLooks.Quarter.PUBLIC);
        String cloth = KingdomWorldApi.kingdom(server, kingdomId)
                .map(kingdom -> io.github.sekerator.kingdomworld.core.kingdom.KingdomColors.dye(kingdom.color())).orElse("");
        long seed = kingdomId.getMostSignificantBits() ^ kingdomId.getLeastSignificantBits() ^ anchor.asLong();
        return known(BuildingLooks.of(style, BuildingLooks.styleWood(style), cloth, quarter, seed));
    }

    /**
     * Materials the game knows: a wood, a stone or a look it does not know is left out, the rest kept.
     */
    public static Materials known(Materials wanted) {
        if (TemplateMaterials.known(wanted)) {
            return wanted;
        }
        io.github.sekerator.kingdomworld.core.construction.MaterialTable table = TemplateMaterials.table();
        String wood = wanted.wood().isEmpty() || Materials.AUTO.equals(wanted.wood()) || table.hasWood(wanted.wood()) ? wanted.wood() : "";
        String stone = wanted.stone().isEmpty() || table.hasStone(wanted.stone()) ? wanted.stone() : "";
        String look = table.knows(wanted.appearance()) ? wanted.look() : "";
        return new Materials(wood, stone, look);
    }
}
