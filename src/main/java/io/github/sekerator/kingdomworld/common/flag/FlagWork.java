package io.github.sekerator.kingdomworld.common.flag;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomStyles;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomFlag;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomFlags;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The flags of kingdoms on the server: the flag a kingdom flies, and the flags the kingdoms of the world are given.
 *
 * <p>A kingdom flies the flag it keeps; a kingdom of the world without one yet the flag drawn for it, and any other kingdom the
 * flag of its colour. The flags of the kingdoms of the world are drawn once, the first time the server sees such a kingdom
 * without a flag, and kept from then on: they never change, not even after a coup.
 */
public final class FlagWork {
    private static final Logger LOGGER = LogUtils.getLogger();

    private FlagWork() {
    }

    /**
     * The flag a kingdom flies.
     */
    public static KingdomFlag effective(MinecraftServer server, Kingdom kingdom) {
        if (kingdom.flag() != null) {
            return kingdom.flag();
        }
        if (NpcWork.isNpc(server, kingdom.id())) {
            // Drawn on the fly only while the data is read-only or before the flags of the world are given out.
            return generated(server, kingdom, kept(server, kingdom.id()));
        }
        return KingdomFlags.standard(kingdom.color());
    }

    /**
     * The flag drawn for a kingdom of the world.
     */
    static KingdomFlag generated(MinecraftServer server, Kingdom kingdom, Set<KingdomFlag> taken) {
        UUID id = kingdom.id();
        long seed = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
        String character = NpcWork.kingdom(server, id).map(NpcKingdom::character).map(Enum::name).orElse(null);
        return KingdomFlags.generated(seed, kingdom.color(), KingdomStyles.of(server, id), character, taken);
    }

    /**
     * The flags the other kingdoms fly without drawing any: those they keep and the flags of the colours of kingdoms of players.
     */
    private static Set<KingdomFlag> kept(MinecraftServer server, UUID except) {
        Set<KingdomFlag> taken = new HashSet<>();
        for (Kingdom other : KingdomWorldApi.state(server).kingdoms().all()) {
            if (other.id().equals(except)) {
                continue;
            }
            if (other.flag() != null) {
                taken.add(other.flag());
            } else if (!NpcWork.isNpc(server, other.id())) {
                taken.add(KingdomFlags.standard(other.color()));
            }
        }
        return taken;
    }

    /**
     * Gives every kingdom of the world without a flag the one drawn for it, all in one change, each flag unlike those already
     * flown. Nothing happens while the data is read-only.
     *
     * @return how many kingdoms got a flag
     */
    public static int fillWorldFlags(MinecraftServer server) {
        if (KingdomWorldApi.isReadOnly(server)) {
            return 0;
        }
        List<Kingdom> missing = new ArrayList<>();
        Set<KingdomFlag> taken = new HashSet<>();
        for (Kingdom kingdom : KingdomWorldApi.state(server).kingdoms().all()) {
            boolean npc = NpcWork.isNpc(server, kingdom.id());
            if (kingdom.flag() == null && npc) {
                missing.add(kingdom);
            } else {
                taken.add(kingdom.flag() != null ? kingdom.flag() : KingdomFlags.standard(kingdom.color()));
            }
        }
        if (missing.isEmpty()) {
            return 0;
        }
        Map<UUID, KingdomFlag> drawn = new LinkedHashMap<>();
        for (Kingdom kingdom : missing) {
            KingdomFlag flag = generated(server, kingdom, taken);
            drawn.put(kingdom.id(), flag);
            taken.add(flag);
        }
        KingdomWorldApi.change(server, "give the kingdoms of the world their flags", state -> {
            drawn.forEach(state::setKingdomFlag);
            return Outcome.DONE;
        });
        LOGGER.info("KingdomWorld gave {} kingdom(s) of the world their flags", drawn.size());
        return drawn.size();
    }
}
