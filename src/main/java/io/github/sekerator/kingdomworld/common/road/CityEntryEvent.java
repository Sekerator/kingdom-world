package io.github.sekerator.kingdomworld.common.road;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

/**
 * Posted on the Forge event bus when a player comes into a city from outside: through a gate of its wall, or past the
 * gates (over the wall, through a gap, or into a city without a wall). Law and guards of a later stage listen to it.
 *
 * @see CityWalls#gates
 * @see CityWalls#level
 */
public class CityEntryEvent extends Event {
    private final ServerPlayer player;
    private final UUID kingdomId;
    private final long cityId;
    private final boolean throughGate;

    /**
     * @param cityId the city, 0 for the capital of the kingdom
     */
    public CityEntryEvent(ServerPlayer player, UUID kingdomId, long cityId, boolean throughGate) {
        this.player = player;
        this.kingdomId = kingdomId;
        this.cityId = cityId;
        this.throughGate = throughGate;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public UUID getKingdomId() {
        return kingdomId;
    }

    /**
     * The city, 0 for the capital.
     */
    public long getCityId() {
        return cityId;
    }

    /**
     * Whether the player came in through a gate of the wall.
     */
    public boolean throughGate() {
        return throughGate;
    }
}
