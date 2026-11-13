package io.github.sekerator.kingdomworld.common.siege;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.npc.NpcTexts;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.economy.Plurals;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.land.ProvinceNames;
import io.github.sekerator.kingdomworld.core.npc.NpcNames;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.territory.Province;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.Optional;
import java.util.UUID;

/**
 * Words of the war in the world. Names of cities and kingdoms go in quotes, never into the cases of a Russian phrase.
 */
public final class SiegeTexts {
    private SiegeTexts() {
    }

    /**
     * The name of a city of a kingdom; the capital goes by the name of the kingdom.
     */
    public static String cityName(MinecraftServer server, UUID kingdomId, long cityId) {
        if (cityId == 0) {
            return KingdomWorldApi.kingdom(server, kingdomId).map(Kingdom::name).orElse("?");
        }
        return RoadWork.state(server).city(cityId).map(City::name).orElse("?");
    }

    /**
     * A city in words: «Name» for a city, the capital of «Name» for a capital.
     */
    public static Component cityLabel(MinecraftServer server, UUID kingdomId, long cityId) {
        return Component.translatable(SiegeWork.KEY + (cityId == 0 ? "capital_label" : "city_label"), cityName(server, kingdomId, cityId));
    }

    /**
     * The name of a province in both alphabets, its number for a province the map does not know.
     */
    public static Component provinceName(MinecraftServer server, int province) {
        Optional<Province> found = KingdomWorldApi.state(server).territory().provinceMap().flatMap(map -> map.province(province));
        if (found.isEmpty()) {
            return Component.literal("#" + province);
        }
        NpcNames.Name name = ProvinceNames.of(found.get().nameSeed());
        return NpcTexts.name(name.latin(), name.cyrillic());
    }

    /**
     * The word "minutes" in the form for a number.
     */
    public static Component minutes(long count) {
        return Component.translatable(SiegeWork.KEY + "minutes." + Plurals.form(count).suffix());
    }

    /**
     * A count of blocks, in the form for the number.
     */
    public static Component blocks(long count) {
        return Component.translatable(SiegeWork.KEY + "count.blocks." + Plurals.form(count).suffix(), count);
    }

    public static Component chunks(long count) {
        return Component.translatable(SiegeWork.KEY + "count.chunks." + Plurals.form(count).suffix(), count);
    }
}
