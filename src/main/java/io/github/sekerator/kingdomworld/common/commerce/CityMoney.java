package io.github.sekerator.kingdomworld.common.commerce;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.core.economy.CityLedger;
import io.github.sekerator.kingdomworld.core.economy.CityPurses;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;

/**
 * What becomes of the money of a city that is gone: given up by its kingdom, or handed to another in a war. Nothing is lost:
 * the money goes to the crown, to the townspeople of the capital or to the winner.
 */
public final class CityMoney {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CityMoney() {
    }

    /**
     * A city was given up (its town hall is gone, or an operator removed it): its shops close and the crown buys their goods,
     * its treasury goes to the crown and the purse of its townspeople to those of the capital.
     */
    public static void cityRemoved(MinecraftServer server, UUID kingdom, long city) {
        if (city == 0 || KingdomWorldApi.isReadOnly(server)) {
            return;
        }
        Shops.closeCity(server, kingdom, city, true);
        Economy economy = KingdomWorldApi.state(server).economy();
        long day = TreasuryActions.day(server);
        CityPurses.PurseKey treasury = CityPurses.PurseKey.cityTreasury(kingdom, city);
        long money = economy.purse(treasury);
        if (money > 0 && !economy.purseToTreasury(treasury, money, LedgerCategory.CITIES, CityLedger.OTHER, day).ok()) {
            // The treasury of the kingdom is full: the money goes to the townspeople of the capital instead.
            economy.purseMove(treasury, CityPurses.PurseKey.cityPurse(kingdom, 0), money, CityLedger.OTHER, day);
        }
        CityPurses.PurseKey purse = CityPurses.PurseKey.cityPurse(kingdom, city);
        economy.purseMove(purse, CityPurses.PurseKey.cityPurse(kingdom, 0), economy.purse(purse), CityLedger.OTHER, day);
        if (economy.purse(treasury) == 0 && economy.purse(purse) == 0) {
            economy.dropCityTreasury(kingdom, city);
        }
        CommerceWork.meansChanged(kingdom);
        LOGGER.info("The money of city {} of {} went to the crown and the capital: the city is given up", city, kingdom);
    }

    /**
     * A city went to another kingdom: its shops close at once without being bought out (their goods are loot of the winner),
     * then its treasury and the purse of its townspeople go to the winner, into its reserves of the currency of the loser.
     */
    public static void handOver(MinecraftServer server, long city, UUID from, UUID to) {
        if (KingdomWorldApi.isReadOnly(server) || from.equals(to)) {
            return;
        }
        Shops.handOver(server, from, city, to);
        Economy economy = KingdomWorldApi.state(server).economy();
        long day = TreasuryActions.day(server);
        long seized = economy.seizePurses(List.of(CityPurses.PurseKey.cityTreasury(from, city), CityPurses.PurseKey.cityPurse(from, city)), to,
                day);
        economy.dropCityTreasury(from, city);
        CommerceWork.meansChanged(from);
        if (seized > 0) {
            LOGGER.info("The treasury and the purse of city {} of {} went to {}: {}", city, from, to, seized);
        }
    }
}
