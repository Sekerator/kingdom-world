package io.github.sekerator.kingdomworld.common.diplomacy;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.DiplomacyViews;
import io.github.sekerator.kingdomworld.common.network.packet.DiplomacyPackets;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.diplomacy.Bond;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyRules;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyState;
import io.github.sekerator.kingdomworld.core.diplomacy.PeaceTerms;
import io.github.sekerator.kingdomworld.core.diplomacy.Proposal;
import io.github.sekerator.kingdomworld.core.diplomacy.Relations;
import io.github.sekerator.kingdomworld.core.diplomacy.Treaty;
import io.github.sekerator.kingdomworld.core.diplomacy.War;
import io.github.sekerator.kingdomworld.core.diplomacy.WarGoal;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.life.DaySchedule;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Diplomacy for the clients: the "Diplomacy" tab of the throne screen with its actions, and the marks of the atlas, sent
 * to every player of a kingdom when they change.
 */
public final class DiplomacySync {
    private static final String KEY = WarWork.KEY;
    /** What each player was last sent for the atlas. */
    private static final Map<UUID, DiplomacyViews.MapView> SENT = new HashMap<>();

    private DiplomacySync() {
    }

    static void reset() {
        SENT.clear();
    }

    static void forget(UUID player) {
        SENT.remove(player);
    }

    // ---------------------------------------------------------------- the tab

    public static void sendTab(ServerPlayer player, UUID kingdomId) {
        if (KingdomWorldApi.kingdom(player.server, kingdomId).isEmpty()
                || !KingdomWorldApi.state(player.server).membership().isMember(player.getUUID(), kingdomId)) {
            return;
        }
        KingdomWorldNetwork.sendToPlayer(player, new DiplomacyPackets.TabResponse(tabView(player.server, player.getUUID(), kingdomId)));
    }

    /**
     * The diplomacy tab of a kingdom as one of its members sees it.
     */
    public static DiplomacyViews.TabView tabView(MinecraftServer server, UUID viewer, UUID kingdomId) {
        DiplomacyState state = WarWork.state(server);
        DiplomacyRules rules = DiplomacySettings.rules();
        long now = KingdomWorldApi.gameTime(server);
        var membership = KingdomWorldApi.state(server).membership();
        boolean canManage = membership.hasPermission(viewer, kingdomId, Permission.MANAGE_DIPLOMACY);
        boolean canDeclare = membership.hasPermission(viewer, kingdomId, Permission.DECLARE_WAR);
        List<DiplomacyViews.KingdomRow> kingdoms = new ArrayList<>();
        Map<UUID, List<DiplomacyViews.CedeOption>> cede = Cessions.options(server, kingdomId);
        for (Kingdom other : KingdomWorldApi.kingdoms(server)) {
            if (other.id().equals(kingdomId)) {
                continue;
            }
            Bond bond = state.bond(kingdomId, other.id(), now);
            Optional<Treaty> treaty = state.treaty(kingdomId, other.id()).filter(t -> !t.expired(now));
            long minutes = treaty.filter(t -> t.until() != Long.MAX_VALUE).map(t -> (t.until() - now) / 1_200).orElse(-1L);
            String reason = state.relations().get(kingdomId, other.id()).flatMap(Relations.Relation::main).map(Relations.Reason::id).orElse("");
            boolean vassalOf = treaty.map(t -> t.kind() == Bond.VASSAL && other.id().equals(t.overlord())).orElse(false);
            boolean lordOf = treaty.map(t -> t.kind() == Bond.VASSAL && kingdomId.equals(t.overlord())).orElse(false);
            kingdoms.add(new DiplomacyViews.KingdomRow(other.id(), KingdomActions.kingdomName(other), other.color(), WarWork.isNpc(server, other.id()),
                    bond, WarWork.relation(server, kingdomId, other.id()), reason, vassalOf, lordOf, minutes, WarWork.playable(server, other.id()),
                    WarWork.shielded(server, other.id()), cede.getOrDefault(other.id(), List.of())));
        }
        kingdoms.sort((a, b) -> {
            int order = Integer.compare(rank(a.bond()), rank(b.bond()));
            return order != 0 ? order : a.name().getString().compareToIgnoreCase(b.name().getString());
        });
        List<DiplomacyViews.OfferRow> offers = new ArrayList<>();
        for (Proposal proposal : state.openProposals(kingdomId)) {
            boolean incoming = proposal.to().equals(kingdomId);
            UUID other = incoming ? proposal.from() : proposal.to();
            offers.add(new DiplomacyViews.OfferRow(proposal.id(), incoming, other, WarWork.name(server, other), proposal.kind(),
                    WarTexts.offerText(server, proposal), (proposal.expires() - now) / 1_200));
        }
        List<DiplomacyViews.CallRow> calls = new ArrayList<>();
        List<DiplomacyViews.WarRow> wars = new ArrayList<>();
        for (War war : state.wars()) {
            if (war.over()) {
                continue;
            }
            War.Side called = war.calls().get(kingdomId);
            if (called != null) {
                calls.add(new DiplomacyViews.CallRow(war.id(), WarWork.name(server, war.leader(called)), WarWork.name(server, war.leader(called.enemy()))));
            }
            Optional<War.Side> side = war.sideOf(kingdomId);
            if (side.isEmpty()) {
                continue;
            }
            wars.add(warRow(server, war, side.get(), kingdomId, rules, now));
        }
        List<Component> chronicle = new ArrayList<>();
        for (DiplomacyState.Event event : state.chronicle()) {
            if (event.involves(kingdomId)) {
                chronicle.add(chronicleLine(server, event));
            }
        }
        return new DiplomacyViews.TabView(kingdomId, canManage, canDeclare, WarWork.shielded(server, kingdomId), kingdoms, offers, calls, wars,
                io.github.sekerator.kingdomworld.common.siege.SiegeTab.lines(server, kingdomId),
                chronicle);
    }

    private static int rank(Bond bond) {
        return switch (bond) {
            case WAR -> 0;
            case VASSAL -> 1;
            case ALLIANCE -> 2;
            case PACT -> 3;
            case TRUCE -> 4;
            case PEACE -> 5;
        };
    }

    private static DiplomacyViews.WarRow warRow(MinecraftServer server, War war, War.Side side, UUID kingdomId, DiplomacyRules rules, long now) {
        UUID enemy = war.leader(side.enemy());
        boolean leads = war.leader(side).equals(kingdomId);
        long minutes = war.phase() == War.Phase.PREPARING ? (war.starts() - now) / 1_200
                : (war.starts() + (long) rules.maxWarDays() * CitizenNeeds.TICKS_PER_DAY - now) / 1_200;
        MutableComponent allies = Component.empty();
        for (War.Side each : War.Side.values()) {
            for (UUID ally : war.allies(each)) {
                if (!allies.getSiblings().isEmpty()) {
                    allies.append(", ");
                }
                allies.append(Component.translatable(KEY + (each == side ? "tab.ally_ours" : "tab.ally_theirs"), WarWork.name(server, ally)));
            }
        }
        if (allies.getSiblings().isEmpty()) {
            allies.append(Component.translatable(KEY + "tab.no_allies"));
        }
        DiplomacyState state = WarWork.state(server);
        PeaceTerms demand = state.feasible(war, DiplomacyState.terms(war, side, false, WarWork.funds(server, enemy), rules), rules);
        PeaceTerms concede = state.feasible(war, DiplomacyState.terms(war, side.enemy(), false, WarWork.funds(server, war.leader(side)), rules), rules);
        return new DiplomacyViews.WarRow(war.id(), WarWork.name(server, enemy), leads, side == War.Side.ATTACKER, war.goal(), war.phase(), minutes,
                (int) Math.round(war.score(side)), (int) Math.round(war.score(side.enemy())), (int) Math.round(war.exhaustion(side)),
                (int) Math.round(war.exhaustion(side.enemy())), WarWork.shielded(server, enemy), WarWork.shielded(server, war.leader(side)), allies,
                WarTexts.terms(server, war, demand), WarTexts.terms(server, war, concede), !demand.isWhite());
    }

    /**
     * An entry of the chronicle in words.
     */
    public static Component chronicleLine(MinecraftServer server, DiplomacyState.Event event) {
        Component first = WarWork.name(server, event.first());
        Component second = event.second() == null ? Component.empty() : WarWork.name(server, event.second());
        Component detail = switch (event.kind()) {
            case "declared", "started" -> WarGoal.byId(event.detail()).<Component>map(WarTexts::goal).orElse(Component.literal(event.detail()));
            case "broken" -> Bond.byId(event.detail()).<Component>map(WarTexts::bond).orElse(Component.literal(event.detail()));
            case "raid" -> WarTexts.items(parse(event.detail()));
            case "peace" -> Component.translatable(KEY + "chronicle.how." + event.detail());
            case "province_taken", "ceded" ->
                    io.github.sekerator.kingdomworld.common.siege.SiegeTexts.provinceName(server, (int) parse(event.detail()));
            default -> Component.literal(event.detail());
        };
        return Component.translatable(KEY + "chronicle." + event.kind(), DaySchedule.day(event.time()), first, second, detail);
    }

    private static long parse(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- actions of the tab

    public static void handleTab(ServerPlayer player, DiplomacyPackets.TabChange change) {
        KingdomActions.reply(player, apply(player, change));
        sendTab(player, change.kingdomId());
    }

    static KingdomActions.Feedback apply(ServerPlayer player, DiplomacyPackets.TabChange change) {
        MinecraftServer server = player.server;
        UUID actor = player.getUUID();
        UUID kingdomId = change.kingdomId();
        UUID target = change.target();
        if (!KingdomWorldApi.state(server).membership().isMember(actor, kingdomId)) {
            return KingdomActions.Feedback.error(KingdomError.NO_PERMISSION);
        }
        Outcome outcome = switch (change.action()) {
            case PROPOSE_PACT -> DiplomacyActions.propose(server, actor, kingdomId, target, Proposal.Kind.PACT, null, 0, PeaceTerms.WHITE);
            case PROPOSE_ALLIANCE -> DiplomacyActions.propose(server, actor, kingdomId, target, Proposal.Kind.ALLIANCE, null, 0, PeaceTerms.WHITE);
            case OFFER_VASSALAGE -> DiplomacyActions.propose(server, actor, kingdomId, target, Proposal.Kind.VASSALAGE, kingdomId, 0, PeaceTerms.WHITE);
            case DEMAND_VASSALAGE -> DiplomacyActions.propose(server, actor, kingdomId, target, Proposal.Kind.VASSALAGE, target, 0, PeaceTerms.WHITE);
            case ASK_RELEASE -> DiplomacyActions.propose(server, actor, kingdomId, target, Proposal.Kind.RELEASE, null, 0, PeaceTerms.WHITE);
            case BREAK -> DiplomacyActions.breakTreaty(server, actor, kingdomId, target);
            case DECLARE -> WarGoal.byId(change.word()).map(goal -> DiplomacyActions.declare(server, actor, kingdomId, target, goal, change.value() == 1))
                    .orElse(Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE));
            case ACCEPT -> DiplomacyActions.answer(server, actor, kingdomId, change.value(), true);
            case DECLINE -> DiplomacyActions.answer(server, actor, kingdomId, change.value(), false);
            case CANCEL -> DiplomacyActions.cancel(server, actor, kingdomId, change.value());
            case JOIN -> DiplomacyActions.answerCall(server, actor, kingdomId, change.value(), true);
            case REFUSE -> DiplomacyActions.answerCall(server, actor, kingdomId, change.value(), false);
            case PEACE_WHITE, PEACE_DEMAND, PEACE_CONCEDE -> peace(server, actor, kingdomId, change.value(), change.action());
            case ASK_CEDE -> change.value() >= 1 && change.value() <= io.github.sekerator.kingdomworld.core.territory.ProvinceMap.MAX_ID
                    ? DiplomacyActions.askCede(server, actor, kingdomId, target, (int) change.value())
                    : Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        };
        if (!outcome.ok()) {
            // A province asked for, or a cession accepted, that the land refuses is told why in words of land.
            boolean cession = change.action() == DiplomacyPackets.TabChange.Action.ASK_CEDE
                    || change.action() == DiplomacyPackets.TabChange.Action.ACCEPT;
            Optional<Component> why = cession ? Cessions.refusal(outcome.error()) : Optional.empty();
            return why.map(KingdomActions.Feedback::fail).orElseGet(() -> KingdomActions.Feedback.error(outcome.error()));
        }
        return KingdomActions.Feedback.ok(KEY + "tab.done." + change.action().name().toLowerCase(java.util.Locale.ROOT));
    }

    private static Outcome peace(MinecraftServer server, UUID actor, UUID kingdomId, long warId, DiplomacyPackets.TabChange.Action action) {
        Optional<War> found = WarWork.state(server).war(warId);
        if (found.isEmpty() || found.get().over()) {
            return Outcome.fail(KingdomError.UNKNOWN_WAR);
        }
        War war = found.get();
        Optional<War.Side> side = war.sideOf(kingdomId).filter(s -> war.leader(s).equals(kingdomId));
        if (side.isEmpty()) {
            return Outcome.fail(KingdomError.NOT_AT_WAR);
        }
        DiplomacyRules rules = DiplomacySettings.rules();
        UUID enemy = war.leader(side.get().enemy());
        DiplomacyState state = WarWork.state(server);
        PeaceTerms terms = switch (action) {
            case PEACE_DEMAND -> state.feasible(war, DiplomacyState.terms(war, side.get(), false, WarWork.funds(server, enemy), rules), rules);
            case PEACE_CONCEDE -> state.feasible(war, DiplomacyState.terms(war, side.get().enemy(), false, WarWork.funds(server, kingdomId), rules), rules);
            default -> PeaceTerms.WHITE;
        };
        return DiplomacyActions.propose(server, actor, kingdomId, enemy, Proposal.Kind.PEACE, null, warId, terms);
    }

    // ---------------------------------------------------------------- the atlas

    /**
     * The marks of the atlas for a player: how the player's kingdom stands with every other kingdom.
     */
    public static DiplomacyViews.MapView mapView(MinecraftServer server, UUID player) {
        Optional<UUID> own = KingdomWorldApi.state(server).membership().kingdomOf(player);
        if (own.isEmpty()) {
            return DiplomacyViews.MapView.EMPTY;
        }
        DiplomacyState state = WarWork.state(server);
        long now = KingdomWorldApi.gameTime(server);
        List<DiplomacyViews.MapMark> marks = new ArrayList<>();
        for (Kingdom other : KingdomWorldApi.kingdoms(server)) {
            if (other.id().equals(own.get())) {
                continue;
            }
            Optional<Capital> capital = KingdomWorldApi.state(server).territory().territory(other.id()).map(Territory::capital);
            if (capital.isEmpty()) {
                continue;
            }
            marks.add(DiplomacyViews.MapMark.of(other.id(), KingdomActions.kingdomName(other), capital.get(), state.bond(own.get(), other.id(), now),
                    WarWork.relation(server, own.get(), other.id()), WarWork.shielded(server, other.id())));
        }
        return new DiplomacyViews.MapView(marks, io.github.sekerator.kingdomworld.common.siege.SiegeTab.spots(server, own.get()));
    }

    /**
     * Sends every player whose marks changed the new ones. Called every few seconds.
     */
    static void syncMaps(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            DiplomacyViews.MapView view = mapView(server, player.getUUID());
            DiplomacyViews.MapView before = SENT.get(player.getUUID());
            if (view.equals(before)) {
                continue;
            }
            SENT.put(player.getUUID(), view);
            KingdomWorldNetwork.sendToPlayer(player, new DiplomacyPackets.MapInfo(view));
        }
    }
}
