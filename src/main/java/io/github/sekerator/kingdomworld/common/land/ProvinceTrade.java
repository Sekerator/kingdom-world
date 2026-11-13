package io.github.sekerator.kingdomworld.common.land;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.trade.Purses;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.land.ProvinceKind;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.ProvinceSale;
import io.github.sekerator.kingdomworld.core.npc.Settlement;
import io.github.sekerator.kingdomworld.core.npc.StateLand;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.Province;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import io.github.sekerator.kingdomworld.core.trade.ExchangeRates;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongPredicate;

/**
 * The sale of provinces of the states of the world to players: a kingdom buys a province next to its land from its treasury,
 * and takes it at once; a player without a kingdom buys a charter, its money held in escrow and the province held for it for
 * a few days, and founds a kingdom by setting its throne there, or gets its money back. Money moves in the books and nothing
 * is made: the price, set in the currency of the seller, is paid by the rates of the day.
 */
public final class ProvinceTrade {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "kingdomworld.province.sale.";

    /**
     * What a province costs a buyer and why it is not for sale, if it is not.
     *
     * @param price   the price in the currency of the seller, 0 when there is none
     * @param refusal why it is not for sale to this buyer, empty when it is (the ruler may still not want to today)
     */
    public record Quote(int province, @Nullable UUID seller, int chunks, long price, Optional<ProvinceSale.Refusal> refusal) {
        public boolean forSale() {
            return refusal.isEmpty();
        }
    }

    /** Whether rulers are willing to sell, set by game tests; null for their characters. */
    private static volatile Boolean willingOverride;

    private ProvinceTrade() {
    }

    /**
     * Makes every ruler willing to sell, or unwilling, whatever its character, for game tests; null gives the rulers their
     * characters back.
     */
    public static void overrideWilling(@Nullable Boolean willing) {
        willingOverride = willing;
    }

    public static ProvinceSale.Rules rules() {
        ProvinceSale.Rules base = ProvinceSale.Rules.DEFAULT;
        double perChunk;
        try {
            perChunk = KingdomWorldConfig.SERVER.land.provincePricePerChunk.get();
        } catch (IllegalStateException e) {
            perChunk = base.pricePerChunk();
        }
        return new ProvinceSale.Rules(perChunk, base.minPrice(), base.cooldownDays(), base.keepShare(), base.connectedShare(), base.soldShare(),
                base.minRelation(), base.holdDays());
    }

    // ---------------------------------------------------------------- the quote

    /**
     * What a province costs a buyer.
     *
     * @param buyer  the kingdom that buys it, or null for a player buying a charter
     * @param player the player who asks
     */
    public static Quote quote(MinecraftServer server, int id, @Nullable UUID buyer, @Nullable UUID player) {
        TerritoryIndex territory = LandEvents.attach(server);
        ProvinceMap map = territory.provinceMap().orElse(null);
        Optional<Province> found = map == null ? Optional.empty() : map.province(id);
        if (found.isEmpty()) {
            return new Quote(id, null, 0, 0, Optional.of(ProvinceSale.Refusal.NOT_OF_THE_WORLD));
        }
        Province province = found.get();
        UUID seller = map.owner(id);
        int chunks = map.chunks(id);
        Optional<NpcKingdom> npc = seller == null ? Optional.empty() : NpcWork.kingdom(server, seller);
        if (npc.isEmpty() || seller.equals(buyer)) {
            return new Quote(id, seller, chunks, 0, Optional.of(ProvinceSale.Refusal.NOT_OF_THE_WORLD));
        }
        ProvinceSale.Rules rules = rules();
        int relation = buyer == null ? 0 : WarWork.relation(server, seller, buyer);
        long price = ProvinceSale.price(chunks, npc.get().character(), npc.get().traits(), relation, rules);
        return new Quote(id, seller, chunks, price, ProvinceSale.refusal(facts(server, territory, map, province, seller, buyer, player, relation), rules));
    }

    private static ProvinceSale.Facts facts(MinecraftServer server, TerritoryIndex territory, ProvinceMap map, Province province, UUID seller,
                                            @Nullable UUID buyer, @Nullable UUID player, int relation) {
        int id = province.id();
        boolean nextToHeart = false;
        for (int near : province.neighbours()) {
            Optional<Province> neighbour = map.province(near);
            if (neighbour.isPresent() && neighbour.get().kind() == ProvinceKind.HEART && seller.equals(map.owner(near))) {
                nextToHeart = true;
                break;
            }
        }
        List<Integer> held = map.provincesOf(seller);
        Map<Integer, List<Integer>> neighbours = new HashMap<>();
        Map<Integer, Integer> sizes = new HashMap<>();
        long heldChunks = 0;
        for (int each : held) {
            map.province(each).ifPresent(p -> neighbours.put(each, p.neighbours()));
            sizes.put(each, map.chunks(each));
            heldChunks += map.chunks(each);
        }
        Optional<Territory> land = territory.territory(seller);
        int capital = land.map(t -> map.provinceAt(t.capital().chunk())).orElse(0);
        double connected = ProvinceSale.connectedShare(new HashSet<>(held), neighbours, sizes, capital, id);
        StateLand.Entry entry = ProvinceSavedData.get(server).states().entry(seller).orElse(StateLand.Entry.planned(held.size(), heldChunks));
        long day = TreasuryActions.day(server);
        boolean atWar = buyer != null && WarWork.fighting(server, seller, buyer);
        Optional<ProvinceMap.SaleHold> hold = map.hold(id).filter(h -> h.untilDay() > day);
        boolean heldForOther = hold.isPresent() && !hold.get().buyer().equals(player);
        return new ProvinceSale.Facts(true, province.kind(), nextToHeart, settled(server, territory, map, seller, id), entry.plannedProvinces(),
                held.size() - 1, connected, entry.lastSaleDay(), day, entry.sold(), atWar, relation, buyer == null || borders(territory, map, id, buyer),
                heldForOther);
    }

    /**
     * Whether a settlement of the seller stands or is planned in a province, or its capital.
     */
    private static boolean settled(MinecraftServer server, TerritoryIndex territory, ProvinceMap map, UUID seller, int id) {
        for (Settlement settlement : NpcWork.state(server).settlementsOf(seller)) {
            if (settlement.province() == id && settlement.status() != Settlement.Status.RUINED) {
                return true;
            }
        }
        return territory.territory(seller).map(t -> map.provinceAt(t.capital().chunk()) == id).orElse(false);
    }

    /**
     * Whether a province borders the land of a kingdom: a province of it beside, or a claim of it sharing a side with a chunk of
     * the province.
     */
    static boolean borders(TerritoryIndex territory, ProvinceMap map, int id, UUID kingdomId) {
        Optional<Province> province = map.province(id);
        if (province.isEmpty()) {
            return false;
        }
        for (int near : province.get().neighbours()) {
            if (kingdomId.equals(map.owner(near))) {
                return true;
            }
        }
        Optional<Territory> land = territory.territory(kingdomId);
        if (land.isEmpty() || !land.get().dimension().equals(map.dimension())) {
            return false;
        }
        for (long chunk : land.get().chunks()) {
            for (long side : ChunkKey.sides(chunk)) {
                if (map.provinceAt(side) == id) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- buying

    /**
     * A player buys a province: for its kingdom from the treasury when it has one (and the right to spend it and to change the
     * land), else a charter from its own account.
     */
    public static KingdomActions.Feedback buy(ServerPlayer player, int id) {
        MinecraftServer server = player.server;
        if (ProvinceSavedData.get(server).isReadOnly()) {
            return KingdomActions.Feedback.error(KingdomError.READ_ONLY);
        }
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdomOf(server, player.getUUID());
        return kingdom.isPresent() ? buyForKingdom(player, kingdom.get(), id) : buyCharter(player, id);
    }

    private static KingdomActions.Feedback buyForKingdom(ServerPlayer player, Kingdom kingdom, int id) {
        MinecraftServer server = player.server;
        var membership = KingdomWorldApi.state(server).membership();
        if (!membership.hasPermission(player.getUUID(), kingdom.id(), Permission.MANAGE_TREASURY)
                || !membership.hasPermission(player.getUUID(), kingdom.id(), Permission.TERRITORY)) {
            return KingdomActions.Feedback.error(KingdomError.NO_PERMISSION);
        }
        Quote quote = quote(server, id, kingdom.id(), player.getUUID());
        Optional<KingdomActions.Feedback> refused = refused(server, quote, kingdom.id());
        if (refused.isPresent()) {
            return refused.get();
        }
        long day = TreasuryActions.day(server);
        Economy economy = KingdomWorldApi.state(server).economy();
        Economy.Exchanged paid = economy.landPayment(kingdom.id(), quote.seller(), quote.price(), day);
        if (!paid.outcome().ok()) {
            return KingdomActions.Feedback.error(paid.outcome().error());
        }
        TerritoryIndex territory = LandEvents.attach(server);
        territory.transferProvince(id, kingdom.id(), day);
        ProvinceMap map = territory.provinceMap().orElseThrow();
        map.hold(id).ifPresent(hold -> release(server, map, id, hold, false));
        ProvinceSavedData.get(server).states().sold(quote.seller(), day);
        Component name = name(server, id);
        Component cost = MoneyTexts.amount(paid.got(), Purses.units(server, kingdom.id()));
        KingdomActions.notifyMembers(server, kingdom, Permission.TERRITORY,
                Component.translatable(KEY + "bought_news", name, WarWork.name(server, quote.seller()), cost).withStyle(ChatFormatting.GOLD));
        LOGGER.info("Province {} ({} chunks) sold by {} to the kingdom {} for {} of its own currency ({} of the seller's)", id, quote.chunks(),
                quote.seller(), kingdom.id(), paid.got(), quote.price());
        return KingdomActions.Feedback.ok(KEY + "bought", name, cost);
    }

    private static KingdomActions.Feedback buyCharter(ServerPlayer player, int id) {
        MinecraftServer server = player.server;
        UUID me = player.getUUID();
        TerritoryIndex territory = LandEvents.attach(server);
        ProvinceMap map = territory.provinceMap().orElse(null);
        long day = TreasuryActions.day(server);
        if (map != null && charterOf(map, me, day).isPresent()) {
            return KingdomActions.Feedback.fail(KEY + "charter_already");
        }
        Quote quote = quote(server, id, null, me);
        Optional<KingdomActions.Feedback> refused = refused(server, quote, null);
        if (refused.isPresent()) {
            return refused.get();
        }
        Economy economy = KingdomWorldApi.state(server).economy();
        Optional<Map.Entry<UUID, Long>> pay = charterPayment(economy, me, quote.seller(), quote.price());
        if (pay.isEmpty()) {
            return KingdomActions.Feedback.fail(KEY + "charter_poor", MoneyTexts.amount(quote.price(), Purses.units(server, quote.seller())),
                    WarWork.name(server, quote.seller()));
        }
        UUID currency = pay.get().getKey();
        long amount = pay.get().getValue();
        Outcome held = economy.holdFromAccount(me, currency, amount);
        if (!held.ok()) {
            return KingdomActions.Feedback.error(held.error());
        }
        long until = day + rules().holdDays();
        map.setHold(id, new ProvinceMap.SaleHold(me, until, quote.seller(), currency, amount));
        LOGGER.info("{} bought a charter for the province {} of {}: {} held until day {}", player.getGameProfile().getName(), id, quote.seller(),
                amount, until);
        return KingdomActions.Feedback.ok(KEY + "charter", name(server, id), MoneyTexts.amount(amount, Purses.units(server, currency)),
                rules().holdDays());
    }

    /**
     * Why a buyer cannot have a province now, as words to it; the ruler answers as it answers a deal, the same all day.
     */
    private static Optional<KingdomActions.Feedback> refused(MinecraftServer server, Quote quote, @Nullable UUID buyer) {
        if (quote.refusal().isPresent()) {
            return Optional.of(KingdomActions.Feedback.fail(refusalText(server, quote)));
        }
        NpcKingdom npc = NpcWork.kingdom(server, quote.seller()).orElseThrow();
        int relation = buyer == null ? 0 : WarWork.relation(server, quote.seller(), buyer);
        long seed = TreasuryActions.day(server) * 0x9E3779B97F4A7C15L + quote.province() * 31L + quote.seller().hashCode()
                + (buyer == null ? 0 : buyer.hashCode());
        Boolean forced = willingOverride;
        if (forced != null ? !forced : !ProvinceSale.willing(npc.character(), relation, seed)) {
            return Optional.of(KingdomActions.Feedback.fail(Component.translatable(KEY + "refusal.unwilling", WarWork.name(server, quote.seller()))));
        }
        return Optional.empty();
    }

    /**
     * The name of a province in the alphabet of the reader, with its number.
     */
    public static Component name(MinecraftServer server, int id) {
        ProvinceMap map = LandEvents.attach(server).provinceMap().orElse(null);
        Optional<Province> province = map == null ? Optional.empty() : map.province(id);
        if (province.isEmpty()) {
            return Component.literal("#" + id);
        }
        io.github.sekerator.kingdomworld.core.npc.NpcNames.Name both = io.github.sekerator.kingdomworld.core.land.ProvinceNames.of(province.get().nameSeed());
        return Component.translatable(KEY + "name", Component.translatable("kingdomworld.npc.name", both.latin(), both.cyrillic()), id);
    }

    /**
     * Why a province is not for sale, in words.
     */
    public static Component refusalText(MinecraftServer server, Quote quote) {
        ProvinceSale.Refusal refusal = quote.refusal().orElse(ProvinceSale.Refusal.UNWILLING);
        Component seller = quote.seller() == null ? Component.translatable(KEY + "nobody") : WarWork.name(server, quote.seller());
        return Component.translatable(KEY + "refusal." + refusal.name().toLowerCase(java.util.Locale.ROOT), seller, rules().cooldownDays());
    }

    /**
     * What a player without a kingdom pays for a charter: in the currency of the seller if the account holds enough of it,
     * else in the current currency of which it holds the most worth, by the rates of the day.
     */
    private static Optional<Map.Entry<UUID, Long>> charterPayment(Economy economy, UUID player, UUID seller, long price) {
        if (economy.checkHoldFromAccount(player, seller, price).ok()) {
            return Optional.of(Map.entry(seller, price));
        }
        Map.Entry<UUID, Long> best = null;
        for (Map.Entry<UUID, Long> held : economy.accounts().of(player).entrySet()) {
            UUID currency = held.getKey();
            if (currency.equals(seller) || !economy.isActive(currency)) {
                continue;
            }
            long cost = ExchangeRates.priceIn(price, economy.rate(seller), economy.rate(currency), 0);
            if (cost > 0 && economy.checkHoldFromAccount(player, currency, cost).ok() && (best == null || cost < best.getValue())) {
                best = Map.entry(currency, cost);
            }
        }
        return Optional.ofNullable(best);
    }

    // ---------------------------------------------------------------- charters

    /**
     * The province held for a player by its charter, if one is.
     */
    public static Optional<Integer> charterOf(ProvinceMap map, UUID player, long day) {
        for (Map.Entry<Integer, ProvinceMap.SaleHold> hold : map.holds().entrySet()) {
            if (hold.getValue().buyer().equals(player) && hold.getValue().untilDay() > day && hold.getValue().amount() > 0) {
                return Optional.of(hold.getKey());
            }
        }
        return Optional.empty();
    }

    /**
     * The chunks a founder may set its throne in and found its kingdom on although they are not land of nobody: those of the
     * province its charter holds, its claims aside; nothing for a founder without a charter.
     */
    public static LongPredicate granted(MinecraftServer server, @Nullable UUID founder, String dimension) {
        if (founder == null || ProvinceSavedData.get(server).isReadOnly()) {
            return chunk -> false;
        }
        TerritoryIndex territory = LandEvents.attach(server);
        ProvinceMap map = territory.provinceMap().orElse(null);
        if (map == null || !map.dimension().equals(dimension)) {
            return chunk -> false;
        }
        Optional<Integer> charter = charterOf(map, founder, TreasuryActions.day(server));
        if (charter.isEmpty()) {
            return chunk -> false;
        }
        int id = charter.get();
        UUID seller = map.hold(id).map(ProvinceMap.SaleHold::seller).orElse(null);
        if (seller == null || !seller.equals(map.owner(id))) {
            return chunk -> false;
        }
        return chunk -> map.provinceAt(chunk) == id && territory.claimOwner(dimension, chunk) == null;
    }

    /**
     * A kingdom was founded: if its throne stands in the province its founder's charter holds, the province is its own at once
     * and the money in escrow goes to the seller.
     */
    public static void founded(MinecraftServer server, @Nullable UUID founder, Kingdom kingdom) {
        if (founder == null) {
            return;
        }
        TerritoryIndex territory = LandEvents.attach(server);
        ProvinceMap map = territory.provinceMap().orElse(null);
        Optional<Territory> land = territory.territory(kingdom.id());
        if (map == null || land.isEmpty() || !land.get().dimension().equals(map.dimension())) {
            return;
        }
        long day = TreasuryActions.day(server);
        Optional<Integer> charter = charterOf(map, founder, day);
        if (charter.isEmpty() || map.provinceAt(land.get().capital().chunk()) != charter.get()) {
            return;
        }
        int id = charter.get();
        ProvinceMap.SaleHold hold = map.hold(id).orElseThrow();
        territory.transferProvince(id, kingdom.id(), day);
        release(server, map, id, hold, true);
        if (hold.seller() != null) {
            ProvinceSavedData.get(server).states().sold(hold.seller(), day);
        }
        LOGGER.info("The kingdom {} was founded on the province {} its founder bought with a charter", kingdom.id(), id);
    }

    /**
     * Lets a hold go: the money in escrow goes to the seller if it was paid for, else back to the buyer.
     */
    private static void release(MinecraftServer server, ProvinceMap map, int id, ProvinceMap.SaleHold hold, boolean paid) {
        Economy economy = KingdomWorldApi.state(server).economy();
        long day = TreasuryActions.day(server);
        if (hold.amount() > 0 && hold.currency() != null) {
            boolean toSeller = paid && hold.seller() != null && economy.canReleaseToTreasury(hold.seller(), hold.currency(), hold.amount());
            if (toSeller) {
                economy.releaseToTreasury(hold.seller(), hold.currency(), hold.amount(), LedgerCategory.LAND, day);
                economy.noteLandIncome(hold.seller(), hold.currency(), hold.amount(), day);
            } else if (!economy.releaseToAccount(hold.buyer(), hold.currency(), hold.amount()).ok()) {
                LOGGER.warn("The money of the charter for the province {} could not go back to {}: it stays held", id, hold.buyer());
                return;
            }
        }
        map.setHold(id, null);
        if (!paid && hold.amount() > 0) {
            ServerPlayer buyer = server.getPlayerList().getPlayer(hold.buyer());
            if (buyer != null) {
                buyer.sendSystemMessage(Component.translatable(KEY + "charter_returned", name(server, id),
                        MoneyTexts.amount(hold.amount(), Purses.units(server, hold.currency()))).withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    /**
     * Charters whose days are over, or whose province changed hands, give the money back. Once a second is enough.
     */
    public static void expire(MinecraftServer server, ProvinceMap map) {
        if (ProvinceSavedData.get(server).isReadOnly()) {
            return;
        }
        long day = TreasuryActions.day(server);
        for (Map.Entry<Integer, ProvinceMap.SaleHold> entry : map.holds().entrySet()) {
            ProvinceMap.SaleHold hold = entry.getValue();
            boolean over = hold.untilDay() <= day;
            boolean lost = hold.seller() != null && !hold.seller().equals(map.owner(entry.getKey()));
            if (over || lost) {
                release(server, map, entry.getKey(), hold, false);
                LOGGER.info("The charter for the province {} ended ({}): the money went back to {}", entry.getKey(), over ? "time" : "changed hands",
                        hold.buyer());
            }
        }
    }

    /**
     * Sends a player what the provinces on the edge and in the core of the states of the world cost it, and why those not for
     * sale are not.
     */
    public static void sendOffers(ServerPlayer player) {
        MinecraftServer server = player.server;
        TerritoryIndex territory = LandEvents.attach(server);
        ProvinceMap map = territory.provinceMap().orElse(null);
        UUID buyer = KingdomWorldApi.kingdomOf(server, player.getUUID()).map(Kingdom::id).orElse(null);
        List<io.github.sekerator.kingdomworld.common.network.packet.ProvinceSalePackets.Offer> offers = new java.util.ArrayList<>();
        if (map != null) {
            for (Province province : map.provinces()) {
                UUID seller = map.owner(province.id());
                if (seller == null || seller.equals(buyer) || (province.kind() != ProvinceKind.FRONTIER && province.kind() != ProvinceKind.CORE)
                        || !NpcWork.isNpc(server, seller)
                        || offers.size() >= io.github.sekerator.kingdomworld.common.network.packet.ProvinceSalePackets.MAX_OFFERS) {
                    continue;
                }
                Quote quote = quote(server, province.id(), buyer, player.getUUID());
                Component price = quote.price() > 0 ? MoneyTexts.amount(quote.price(), Purses.units(server, seller)) : Component.empty();
                offers.add(new io.github.sekerator.kingdomworld.common.network.packet.ProvinceSalePackets.Offer(province.id(),
                        quote.refusal().map(Enum::ordinal).orElse(-1), price, quote.forSale() ? Component.empty() : refusalText(server, quote)));
            }
        }
        io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork.sendToPlayer(player,
                new io.github.sekerator.kingdomworld.common.network.packet.ProvinceSalePackets.Offers(buyer == null, offers));
    }
}
