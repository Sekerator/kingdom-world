package io.github.sekerator.kingdomworld.common.commerce;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplate;
import io.github.sekerator.kingdomworld.common.npc.RulerPlaces;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.economy.CityStatement;
import io.github.sekerator.kingdomworld.core.economy.CityTreasury;
import io.github.sekerator.kingdomworld.core.npc.RulerMind;
import io.github.sekerator.kingdomworld.core.npc.TemplateCatalog;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * When the head of a city wants a house of a merchant or a craftsman: its townspeople did not find more than a share of their
 * basket at its last day, no place of a shop is free and a townsperson would keep one.
 */
public final class CityShopHouses {
    private CityShopHouses() {
    }

    /**
     * Whether the head of a city wants a house for a shop now.
     */
    public static boolean wanted(MinecraftServer server, UUID kingdom, long city) {
        if (!CityOf.active(server, kingdom)) {
            return false;
        }
        CommerceSavedData.CityTrade trade = CommerceSavedData.get(server).cityIfKnown(kingdom, city).orElse(null);
        if (trade == null || !trade.noPlaces) {
            return false;
        }
        Optional<CityTreasury> treasury = KingdomWorldApi.state(server).economy().cityTreasury(kingdom, city);
        long wanted = treasury.map(found -> found.latest().get(CityStatement.Line.BASKET_WANTED)).orElse(0L);
        if (wanted <= 0 || trade.unspent() * 100 <= wanted * CraftSettings.rules().houseWhenUnmetPercent()) {
            return false;
        }
        Map<UUID, Long> cities = CityOf.known(kingdom);
        for (Citizen citizen : KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdom)) {
            Long known = cities.get(citizen.id());
            if (known != null && known == city && citizen.business() == 0 && citizen.isAlive()
                    && Professions.UNEMPLOYED.id().equals(citizen.professionId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The houses of merchants and craftsmen of the style of a kingdom a head may build.
     */
    public static List<BuildingTemplate> templates(String style) {
        List<BuildingTemplate> found = new ArrayList<>();
        for (BuildingTemplate template : RulerPlaces.templates(RulerMind.HOUSE, style)) {
            Optional<TemplateCatalog.Kind> kind = TemplateCatalog.get(template.id().toString()).map(TemplateCatalog.Entry::kind);
            if (kind.isPresent() && (kind.get() == TemplateCatalog.Kind.MERCHANT_HOUSE || kind.get() == TemplateCatalog.Kind.CRAFT_HOUSE)) {
                found.add(template);
            }
        }
        return found;
    }
}
