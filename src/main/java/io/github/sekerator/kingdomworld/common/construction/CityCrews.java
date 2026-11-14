package io.github.sekerator.kingdomworld.common.construction;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who builds in each city of a kingdom, for the windows: the builders at its posts, the helpers of other cities working on its
 * constructions and its constructions, by the id of the city (0 for the capital).
 */
public final class CityCrews {
    private CityCrews() {
    }

    /**
     * What builds in a city.
     */
    public record Crew(int builders, int helpers, int sites) {
        public static final Crew NONE = new Crew(0, 0, 0);
    }

    /**
     * The crews of the cities of a kingdom; a city without builders or constructions is missing.
     */
    public static Map<Long, Crew> of(MinecraftServer server, UUID kingdomId) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        Map<Long, int[]> counts = new HashMap<>();
        for (WorkPost post : state.citizens().posts().postsOf(kingdomId, Professions.BUILDER.id())) {
            long city = ConstructionWork.cityOf(server, kingdomId, post.pos());
            counts.computeIfAbsent(city, key -> new int[3])[0] += post.workers().size();
        }
        for (Construction construction : state.construction().of(kingdomId)) {
            int[] count = counts.computeIfAbsent(ConstructionWork.cityOf(server, construction), key -> new int[3]);
            count[1] += RemoteCrews.helping(construction.id())[0];
            count[2]++;
        }
        Map<Long, Crew> result = new HashMap<>();
        counts.forEach((city, count) -> result.put(city, new Crew(count[0], count[1], count[2])));
        return result;
    }
}
