package io.github.sekerator.kingdomworld.common.commerce;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.core.commerce.Business;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import net.minecraft.server.MinecraftServer;

import java.util.List;

/**
 * What the crown buys of the workshops of its citizens. Work it paid a master for in advance is held in escrow among the books
 * of the shops of citizens until it is delivered or goes back to the crown.
 */
public final class CrownPurchases {
    private CrownPurchases() {
    }

    /**
     * A shop closes: the work the crown paid it for in advance and that it did not deliver goes back to the crown.
     */
    static void cancelCommissions(MinecraftServer server, Business shop, long day) {
        if (shop.commissions().isEmpty()) {
            return;
        }
        Economy economy = KingdomWorldApi.state(server).economy();
        for (Business.Commission commission : List.copyOf(shop.commissions())) {
            // The escrow leaves the books of the shops in the same moment it goes back to the treasury.
            shop.removeCommission(commission.id());
            if (commission.paid() > 0 && !economy.releaseToTreasury(shop.kingdomId(), shop.kingdomId(), commission.paid(), LedgerCategory.COMMERCE,
                    day).ok()) {
                shop.addCommission(commission);
            }
        }
        CommerceSavedData.get(server).setDirty();
    }
}
