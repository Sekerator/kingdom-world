package io.github.sekerator.kingdomworld.client.construction;

import io.github.sekerator.kingdomworld.client.screen.EarthworkScreen;
import io.github.sekerator.kingdomworld.common.network.packet.EarthworkPackets;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * What the server last said of the earthworks marked by the survey stakes in hand: the window shows it, the outline over the
 * land draws its height and the places it leaves as they are.
 */
public final class ClientEarthwork {
    @Nullable
    private static EarthworkPackets.Result last;

    private ClientEarthwork() {
    }

    public static void accept(EarthworkPackets.Result result) {
        last = result.laid() ? null : result;
        if (Minecraft.getInstance().screen instanceof EarthworkScreen screen) {
            screen.accept(result);
        }
    }

    /**
     * The last answer for the corners of the stakes, if it is about them.
     */
    @Nullable
    public static EarthworkPackets.Result last(net.minecraft.core.BlockPos first, net.minecraft.core.BlockPos second) {
        return last != null && last.first().equals(first) && last.second().equals(second) ? last : null;
    }

    public static void reset() {
        last = null;
    }
}
