package io.github.sekerator.kingdomworld.common.diplomacy;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyState;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.life.Rumor;
import io.github.sekerator.kingdomworld.core.life.Rumors;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What taverns tell of wars and treaties: declarations, battles, raids and peace of the last days, from the chronicle of
 * diplomacy. Registered as a source of rumors of the life of cities.
 */
final class WarRumors {
    static final String SOURCE = "war";
    /** Game days news of war stays worth telling. */
    private static final long NEWS_DAYS = 3;
    private static final Set<String> TOLD = Set.of("declared", "battle", "raid", "peace", "alliance", "vassal", "broken", "siege",
            "city_taken", "province_taken", "annexed", "ceded");

    private WarRumors() {
    }

    static List<Rumor> rumors(MinecraftServer server, UUID kingdomId, WorldPos tavern) {
        List<Rumor> result = new ArrayList<>();
        if (WarWork.isReadOnly(server)) {
            return result;
        }
        long now = KingdomWorldApi.gameTime(server);
        Set<String> seen = new HashSet<>();
        for (DiplomacyState.Event event : WarWork.state(server).chronicle()) {
            if (now - event.time() > NEWS_DAYS * CitizenNeeds.TICKS_PER_DAY) {
                break;
            }
            if (!TOLD.contains(event.kind()) || event.second() == null || !seen.add(event.kind() + event.war())) {
                continue;
            }
            String first = KingdomWorldApi.kingdom(server, event.first()).map(Kingdom::name).orElse(null);
            String second = KingdomWorldApi.kingdom(server, event.second()).map(Kingdom::name).orElse(null);
            if (first == null || second == null) {
                continue;
            }
            result.add(new Rumor(Rumors.NEWS, WarWork.KEY + "rumor." + event.kind(), List.of(Rumor.Arg.plain(first), Rumor.Arg.plain(second))));
            if (result.size() >= 3) {
                break;
            }
        }
        return result;
    }
}
