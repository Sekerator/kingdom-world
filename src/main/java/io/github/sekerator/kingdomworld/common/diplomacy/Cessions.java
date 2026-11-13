package io.github.sekerator.kingdomworld.common.diplomacy;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.land.LandEvents;
import io.github.sekerator.kingdomworld.common.land.ProvinceSavedData;
import io.github.sekerator.kingdomworld.common.land.ProvinceTrade;
import io.github.sekerator.kingdomworld.common.network.data.DiplomacyViews;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.siege.SiegeTexts;
import io.github.sekerator.kingdomworld.common.siege.SiegeWork;
import io.github.sekerator.kingdomworld.common.trade.Purses;
import io.github.sekerator.kingdomworld.core.diplomacy.CedeLand;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyMind;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyRules;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyState;
import io.github.sekerator.kingdomworld.core.diplomacy.Proposal;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
import io.github.sekerator.kingdomworld.core.npc.Settlement;
import io.github.sekerator.kingdomworld.core.siege.ProvinceHolds;
import io.github.sekerator.kingdomworld.core.territory.Province;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Provinces ceded by treaty outside war, as the world sees them: what the land says of a province asked for, what a ruler of
 * the world asks for it (the price it asks for a province on sale, twice that where people live, nothing when it would give
 * it away), which provinces a kingdom may ask each of its neighbours for, and the acceptance: the price paid by the books on
 * the line "Land", then the journal of conquests hands the province over whole, as a peace does.
 */
final class Cessions {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Provinces of one neighbour offered in the tab at most. */
    static final int MAX_OFFERED = DiplomacyViews.MAX_CEDE;

    private Cessions() {
    }

    /**
     * What the land says of a province one kingdom asks for: who holds it, whether the capital of its holder stands in it and
     * whether it touches the land of the asker; {@code null} when there is no such province.
     */
    @Nullable
    static CedeLand land(MinecraftServer server, UUID asker, int id) {
        TerritoryIndex territory = LandEvents.attach(server);
        ProvinceMap map = territory.provinceMap().orElse(null);
        if (map == null || !map.exists(id)) {
            return null;
        }
        UUID holder = map.owner(id);
        boolean heart = holder != null && SiegeWork.capitalProvince(server, holder) == id;
        return new CedeLand(holder, heart, bordering(territory, map, asker).contains(id));
    }

    /**
     * The provinces of others that touch the land of a kingdom.
     */
    private static Set<Integer> bordering(TerritoryIndex territory, ProvinceMap map, UUID kingdomId) {
        Optional<Territory> land = territory.territory(kingdomId).filter(own -> own.dimension().equals(map.dimension()));
        Iterable<Long> claims = land.isPresent() ? land.get().chunks() : List.of();
        return CedeLand.bordering(map.provincesOf(kingdomId), id -> map.province(id).map(Province::neighbours).orElse(List.of()), claims,
                map::provinceAt);
    }

    /**
     * Whether a province is held now for the bearer of a charter: it is promised and cannot be asked for.
     */
    static boolean chartered(MinecraftServer server, int id) {
        ProvinceMap map = LandEvents.attach(server).provinceMap().orElse(null);
        return map != null && chartered(map, id, TreasuryActions.day(server));
    }

    private static boolean chartered(ProvinceMap map, int id, long day) {
        return map.hold(id).filter(hold -> hold.untilDay() > day).isPresent();
    }

    /**
     * Whether the main settlement of a province stands: people live there.
     */
    private static boolean settled(NpcState npc, ProvinceMap map, int id) {
        Optional<Province> province = map.province(id);
        if (province.isEmpty()) {
            return false;
        }
        Settlement seat = province.get().seat() == 0 ? null : npc.settlement((int) province.get().seat()).orElse(null);
        return ProvinceHolds.settled(province.get(), seat);
    }

    /**
     * A province asked of a ruler of the world, as the land and the treaties tell it.
     */
    private static DiplomacyMind.CedeAsk ask(MinecraftServer server, UUID ruler, UUID asker, int id, long price) {
        ProvinceMap map = LandEvents.attach(server).provinceMap().orElse(null);
        if (map == null || !map.exists(id)) {
            return new DiplomacyMind.CedeAsk(false, false, 0, price, false);
        }
        boolean heart = SiegeWork.capitalProvince(server, ruler) == id;
        boolean overlord = WarWork.state(server).overlordOf(ruler).map(asker::equals).orElse(false);
        return new DiplomacyMind.CedeAsk(settled(NpcWork.state(server), map, id), heart, map.chunks(id), price, overlord);
    }

    /**
     * The ruler of a kingdom of the world as it weighs a cession: its character and traits are all that count.
     */
    private static Optional<DiplomacyMind.Self> ruler(MinecraftServer server, UUID kingdomId) {
        Optional<NpcKingdom> npc = NpcWork.kingdom(server, kingdomId);
        return npc.map(kingdom -> new DiplomacyMind.Self(kingdomId, kingdom.character(), kingdom.traits(), 0, false, null));
    }

    /**
     * The asker as a ruler of the world sees it in a cession: its relation is all that counts.
     */
    private static DiplomacyMind.Neighbor asker(MinecraftServer server, UUID ruler, UUID asker) {
        return new DiplomacyMind.Neighbor(asker, WarWork.isNpc(server, asker), 0, 0, WarWork.relation(server, ruler, asker),
                WarWork.state(server).bond(ruler, asker, KingdomWorldApi.gameTime(server)), true, false, null);
    }

    /**
     * What a kingdom is asked to pay for a province of another: what the ruler of the world that holds it asks (nothing when
     * it would give it away), nothing from a kingdom of players, which answers the offer itself.
     */
    static long price(MinecraftServer server, UUID holder, UUID asker, int id) {
        Optional<DiplomacyMind.Self> ruler = ruler(server, holder);
        if (ruler.isEmpty()) {
            return 0;
        }
        DiplomacyMind.Neighbor neighbor = asker(server, holder, asker);
        DiplomacyMind.CedeAsk free = ask(server, holder, asker, id, 0);
        if (DiplomacyMind.cede(ruler.get(), neighbor, free, ProvinceTrade.rules()).accept()) {
            return 0;
        }
        return DiplomacyMind.cedeValue(ruler.get(), neighbor, free, ProvinceTrade.rules());
    }

    /**
     * How a ruler of the world answers an offer to cede a province to the one who made it.
     */
    static DiplomacyMind.Verdict verdict(MinecraftServer server, DiplomacyMind.Self self, DiplomacyMind.Neighbor from, Proposal proposal) {
        DiplomacyMind.CedeAsk ask = ask(server, proposal.to(), proposal.from(), proposal.province(), proposal.price());
        return DiplomacyMind.cede(self, from, ask, ProvinceTrade.rules());
    }

    /**
     * The provinces a kingdom may ask each other kingdom to cede, with their prices, for the tab: those that touch its land,
     * never the heart of their holder nor one promised by a charter.
     */
    static Map<UUID, List<DiplomacyViews.CedeOption>> options(MinecraftServer server, UUID asker) {
        TerritoryIndex territory = LandEvents.attach(server);
        ProvinceMap map = territory.provinceMap().orElse(null);
        if (map == null) {
            return Map.of();
        }
        Set<Integer> bordering = bordering(territory, map, asker);
        if (bordering.isEmpty()) {
            return Map.of();
        }
        long day = TreasuryActions.day(server);
        Map<UUID, List<Integer>> candidates = CedeLand.candidates(bordering, map::owner, asker, holder -> SiegeWork.capitalProvince(server, holder),
                id -> chartered(map, id, day), MAX_OFFERED);
        Map<UUID, List<DiplomacyViews.CedeOption>> options = new HashMap<>();
        candidates.forEach((holder, provinces) -> {
            List<DiplomacyViews.CedeOption> list = new ArrayList<>(provinces.size());
            for (int id : provinces) {
                long price = price(server, holder, asker, id);
                list.add(new DiplomacyViews.CedeOption(id, price, cost(server, holder, price)));
            }
            options.put(holder, list);
        });
        return options;
    }

    /**
     * What is paid for a province in words: "for" a sum in the currency of the kingdom that cedes it, or "for nothing".
     */
    static Component cost(MinecraftServer server, UUID holder, long price) {
        return price > 0 ? Component.translatable(WarWork.KEY + "cede.price", MoneyTexts.amount(price, Purses.units(server, holder)))
                : Component.translatable(WarWork.KEY + "cede.free");
    }

    /**
     * What a refusal of the land means in words, for one who asks for a province or accepts to cede it; empty for others.
     */
    static Optional<Component> refusal(KingdomError error) {
        String key = switch (error) {
            case NOT_OWNED -> "not_theirs";
            case CAPITAL_CHUNK -> "heart";
            case NOT_ADJACENT -> "not_bordering";
            case NOT_FOR_SALE -> "chartered";
            default -> null;
        };
        return key == null ? Optional.empty() : Optional.of(Component.translatable(WarWork.KEY + "cede.refused." + key));
    }

    /**
     * Accepts an offer to cede a province: the land and the money of the asker are looked at again, the asker pays the price
     * in its currency by the rates of the day into the reserves of the one who cedes, on the line "Land" of both (in the books
     * of both or not at all), and the journal of conquests hands the province over whole with its claims, buildings, citizens,
     * cities and settlements.
     */
    static Outcome accept(MinecraftServer server, Proposal proposal, UUID kingdomId) {
        if (SiegeWork.isReadOnly(server) || ProvinceSavedData.get(server).isReadOnly() || KingdomWorldApi.isReadOnly(server)) {
            return Outcome.fail(KingdomError.READ_ONLY);
        }
        DiplomacyState state = WarWork.state(server);
        DiplomacyRules rules = DiplomacySettings.rules();
        long now = KingdomWorldApi.gameTime(server);
        long day = TreasuryActions.day(server);
        int id = proposal.province();
        CedeLand land = land(server, proposal.from(), id);
        Outcome check = state.checkAccept(proposal.id(), kingdomId, now, Long.MAX_VALUE, rules, land);
        if (!check.ok()) {
            return check;
        }
        if (chartered(server, id)) {
            return Outcome.fail(KingdomError.NOT_FOR_SALE);
        }
        Economy economy = KingdomWorldApi.state(server).economy();
        if (proposal.price() > 0) {
            Economy.Exchanged paid = economy.landPayment(proposal.from(), proposal.to(), proposal.price(), day);
            if (!paid.outcome().ok()) {
                return paid.outcome();
            }
        }
        DiplomacyState.Accepted[] accepted = new DiplomacyState.Accepted[1];
        Outcome outcome = WarWork.change(server, "cede a province", diplomacy -> {
            accepted[0] = diplomacy.acceptOffer(proposal.id(), kingdomId, now, day, rules, land);
            return accepted[0].outcome();
        });
        if (!outcome.ok() || accepted[0].cession().isEmpty()) {
            // Cannot happen after the checks of this same moment; the log keeps the payment that went without its province.
            LOGGER.error("The cession #{} of the province {} was paid ({}) but could not be accepted: {}", proposal.id(), id,
                    proposal.price(), outcome.ok() ? "?" : outcome.error());
            return outcome.ok() ? Outcome.fail(KingdomError.UNKNOWN_PROPOSAL) : outcome;
        }
        DiplomacyState.Cession cession = accepted[0].cession().get();
        String dimension = LandEvents.attach(server).provinceMap().map(ProvinceMap::dimension).orElse("");
        SiegeWork.cede(server, cession.winner(), cession.loser(), cession.province(), dimension, now);
        state.record(now, "ceded", cession.winner(), cession.loser(), 0, Integer.toString(cession.province()));
        LOGGER.info("{} ceded the province {} to {} for {}", WarWork.name(server, cession.loser()).getString(), id,
                WarWork.name(server, cession.winner()).getString(), cession.price());
        return Outcome.DONE;
    }

    /**
     * The province of an offer in words: its name and what is paid for it.
     */
    static Component offerText(MinecraftServer server, Proposal proposal) {
        Component name = SiegeTexts.provinceName(server, proposal.province());
        return Component.translatable(WarWork.KEY + "offer_text.cede", name, cost(server, proposal.to(), proposal.price()));
    }
}
