package io.github.sekerator.kingdomworld.common.construction;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Materials brought to a construction near players from storehouses the builder cannot get to: a village without a storehouse
 * of its own, a storehouse in another city. When the kingdom holds what the builder needs but no storehouse in its reach does,
 * a cart is sent, one at a time for each construction, and after {@value #CART_TICKS} ticks the materials lie at the site, as
 * if a carrier brought them. It lives in memory: a cart on its way at a restart is sent again when the builder looks next.
 */
public final class Carts {
    /** Ticks a cart takes. */
    public static final int CART_TICKS = 200;
    /** Items a cart brings at most. */
    static final int LOAD = 64;

    private record Cart(String item, int amount, long ready) {
    }

    private static final Map<Long, Cart> CARTS = new HashMap<>();

    private Carts() {
    }

    /**
     * Sends a cart with an item to a construction unless one is on its way to it already.
     */
    static void order(Construction construction, String item, int amount, long now) {
        CARTS.putIfAbsent(construction.id(), new Cart(item, Math.max(1, Math.min(LOAD, amount)), now + CART_TICKS));
    }

    /**
     * Whether a cart is on its way to the construction.
     */
    public static boolean coming(long constructionId) {
        return CARTS.containsKey(constructionId);
    }

    /**
     * Unloads the carts that arrived; called once a second.
     */
    static void tick(MinecraftServer server, long now) {
        Iterator<Map.Entry<Long, Cart>> all = CARTS.entrySet().iterator();
        while (all.hasNext()) {
            Map.Entry<Long, Cart> entry = all.next();
            if (entry.getValue().ready() > now) {
                continue;
            }
            all.remove();
            Optional<Construction> construction = KingdomWorldApi.state(server).construction().get(entry.getKey());
            if (construction.isPresent() && !KingdomWorldApi.isReadOnly(server)) {
                ConstructionWork.fetch(server, construction.get(), entry.getValue().item(), entry.getValue().amount());
            }
        }
    }

    static void reset() {
        CARTS.clear();
    }
}
