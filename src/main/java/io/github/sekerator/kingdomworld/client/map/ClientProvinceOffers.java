package io.github.sekerator.kingdomworld.client.map;

import io.github.sekerator.kingdomworld.common.network.packet.ProvinceSalePackets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * What the provinces of the states of the world cost the player, as the server last said: for the map of the atlas.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientProvinceOffers {
    private static Map<Integer, ProvinceSalePackets.Offer> offers = Map.of();
    private static boolean charter;

    private ClientProvinceOffers() {
    }

    public static void replace(ProvinceSalePackets.Offers message) {
        Map<Integer, ProvinceSalePackets.Offer> map = new HashMap<>();
        for (ProvinceSalePackets.Offer offer : message.offers()) {
            map.put(offer.id(), offer);
        }
        offers = map;
        charter = message.charter();
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof io.github.sekerator.kingdomworld.client.screen.KingdomMapScreen screen) {
            screen.onProvinceOffers();
        }
    }

    @Nullable
    public static ProvinceSalePackets.Offer offer(int id) {
        return offers.get(id);
    }

    /**
     * Whether the player buys a charter, having no kingdom.
     */
    public static boolean charter() {
        return charter;
    }

    public static void clear() {
        offers = Map.of();
        charter = false;
    }
}
