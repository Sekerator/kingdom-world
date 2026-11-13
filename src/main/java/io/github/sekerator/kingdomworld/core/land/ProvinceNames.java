package io.github.sekerator.kingdomworld.core.land;

import io.github.sekerator.kingdomworld.core.npc.NpcNames;

/**
 * The names of the provinces, in both alphabets, from the seed of their names: the same on the server and on every client.
 */
public final class ProvinceNames {
    /** Mixed into the seed of a province, so that a province is not named as a town of the same seed. */
    private static final long SALT = 0x50524F56494E4345L;

    private ProvinceNames() {
    }

    public static NpcNames.Name of(long nameSeed) {
        return NpcNames.town(nameSeed ^ SALT, name -> false);
    }
}
