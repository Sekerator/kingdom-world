package io.github.sekerator.kingdomworld.common.nobility;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.kingdom.PlayerNames;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.NobilityViews;
import io.github.sekerator.kingdomworld.common.network.packet.NobilityPackets;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Member;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.nobility.Court;
import io.github.sekerator.kingdomworld.core.nobility.Demand;
import io.github.sekerator.kingdomworld.core.nobility.Faction;
import io.github.sekerator.kingdomworld.core.nobility.Fief;
import io.github.sekerator.kingdomworld.core.nobility.Grant;
import io.github.sekerator.kingdomworld.core.nobility.NobilityRules;
import io.github.sekerator.kingdomworld.core.nobility.NobilityState;
import io.github.sekerator.kingdomworld.core.nobility.NobleHouse;
import io.github.sekerator.kingdomworld.core.nobility.Note;
import io.github.sekerator.kingdomworld.core.nobility.Plot;
import io.github.sekerator.kingdomworld.core.nobility.Spy;
import io.github.sekerator.kingdomworld.core.nobility.Stability;
import io.github.sekerator.kingdomworld.core.nobility.Title;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The nobility for the clients: the "Nobility" tab of the throne screen with its actions.
 */
public final class NobilitySync {
    private static final String KEY = NobilityTexts.KEY;

    private NobilitySync() {
    }

    public static void sendTab(ServerPlayer player, UUID kingdomId) {
        if (KingdomWorldApi.kingdom(player.server, kingdomId).isEmpty()
                || !KingdomWorldApi.state(player.server).membership().isMember(player.getUUID(), kingdomId)) {
            return;
        }
        KingdomWorldNetwork.sendToPlayer(player, new NobilityPackets.TabResponse(tabView(player.server, player.getUUID(), kingdomId)));
    }

    /**
     * The tab of a kingdom as one of its members sees it.
     */
    public static NobilityViews.TabView tabView(MinecraftServer server, UUID viewer, UUID kingdomId) {
        NobilityState state = NobilityWork.state(server);
        NobilityRules rules = NobilitySettings.rules();
        var membership = KingdomWorldApi.state(server).membership();
        boolean npc = WarWork.isNpc(server, kingdomId);
        boolean canManage = !npc && membership.hasPermission(viewer, kingdomId, Permission.MANAGE_NOBILITY);
        boolean canSpies = !npc && membership.hasPermission(viewer, kingdomId, Permission.MANAGE_SPIES);
        long day = NobilityWork.day(server);
        Stability.Result stability = state.stability(kingdomId, NobilityWork.inputs(server, kingdomId, day));
        List<NobilityViews.CauseRow> causes = new ArrayList<>();
        for (Stability.Cause cause : stability.causes()) {
            causes.add(new NobilityViews.CauseRow(cause.id(), cause.amount()));
        }
        Court court = state.court(kingdomId);
        Component warning = Component.empty();
        if (court.rebelDays() > 0) {
            warning = Component.translatable(KEY + "tab.rebels", court.rebelDays());
        } else if (court.warned()) {
            warning = Component.translatable(KEY + "tab.warning");
        }
        List<NobilityViews.HouseRow> houses = new ArrayList<>();
        for (NobleHouse house : state.houses(kingdomId)) {
            MutableComponent titles = Component.empty();
            for (Grant grant : state.grantsOf(house)) {
                if (!titles.getString().isEmpty()) {
                    titles.append(", ");
                }
                titles.append(Component.translatable(KEY + "tab.title_of", NobilityTexts.title(grant.title()), NobilityTexts.fief(server, grant.fief())));
            }
            houses.add(new NobilityViews.HouseRow(house.id(), NobilityTexts.house(house), NobilityTexts.head(house), house.faction(), house.loyalty(),
                    house.wealth(), titles.getString().isEmpty() ? Component.translatable(KEY + "tab.no_title") : titles));
        }
        List<NobilityViews.GrantRow> grants = new ArrayList<>();
        for (Grant grant : state.grants(kingdomId)) {
            Component holder = grant.heldByPlayer() ? PlayerNames.display(server, grant.player())
                    : state.house(grant.house()).<Component>map(NobilityTexts::house).orElse(Component.literal("?"));
            grants.add(new NobilityViews.GrantRow(grant.id(), grant.title(), NobilityTexts.fief(server, grant.fief()), holder, grant.heldByPlayer()));
        }
        List<NobilityViews.Choice> fiefs = new ArrayList<>();
        for (Fief fief : NobilityWork.freeFiefs(server, kingdomId)) {
            fiefs.add(new NobilityViews.Choice(fief.code(), NobilityTexts.fief(server, fief)));
        }
        List<NobilityViews.Choice> subjects = new ArrayList<>();
        if (canManage) {
            membership.kingdom(kingdomId).ifPresent(members -> {
                for (Member member : members.members().values()) {
                    if (!state.isNoble(kingdomId, member.playerId())) {
                        subjects.add(new NobilityViews.Choice(member.playerId().toString(), PlayerNames.display(server, member.playerId())));
                    }
                }
            });
        }
        List<NobilityViews.FactionRow> factions = new ArrayList<>();
        Map<Faction, Integer> influence = state.influence(kingdomId);
        for (Faction faction : Faction.values()) {
            factions.add(new NobilityViews.FactionRow(faction, influence.get(faction), court.content(faction)));
        }
        NobilityViews.DemandRow demand = state.openDemand(kingdomId).map(d -> demandRow(server, state, court, d)).orElse(NobilityViews.DemandRow.NONE);
        List<NobilityViews.PlotRow> plots = new ArrayList<>();
        for (Plot plot : state.plots()) {
            if (plot.kingdomId().equals(kingdomId) && plot.status() == Plot.Status.DISCOVERED) {
                plots.add(new NobilityViews.PlotRow(plot.id(), state.house(plot.house()).<Component>map(NobilityTexts::house).orElse(Component.literal("?")),
                        plot.sponsor().<Component>map(sponsor -> WarWork.name(server, sponsor)).orElse(Component.empty())));
            }
        }
        List<Component> chronicle = new ArrayList<>();
        for (Note note : court.chronicle()) {
            chronicle.add(Component.translatable(KEY + "tab.dated", note.day(), NobilityTexts.note(server, note)));
        }
        List<NobilityViews.SpyRow> spies = new ArrayList<>();
        List<Component> reports = new ArrayList<>();
        List<NobilityViews.Choice> targets = new ArrayList<>();
        if (canSpies) {
            for (Spy spy : state.spiesOf(kingdomId)) {
                spies.add(new NobilityViews.SpyRow(spy.id(), NobilityTexts.spy(spy), spy.skill(), spy.status(), doing(server, spy, day)));
            }
            for (Note note : court.reports()) {
                reports.add(Component.translatable(KEY + "tab.dated", note.day(), NobilityTexts.note(server, note)));
            }
            for (Kingdom other : KingdomWorldApi.kingdoms(server)) {
                if (!other.id().equals(kingdomId)) {
                    targets.add(new NobilityViews.Choice(other.id().toString(), KingdomActions.kingdomName(other)));
                }
            }
        }
        Component hire = Component.translatable(KEY + "tab.hire_cost", NobilityTexts.money(server, kingdomId, rules.spyHire()),
                NobilityTexts.money(server, kingdomId, rules.spyWage()));
        return new NobilityViews.TabView(kingdomId, canManage, canSpies, npc, stability.value(), causes, warning, houses, grants, fiefs, subjects, factions,
                demand, plots, chronicle, spies, reports, targets, state.counter(kingdomId, NobilityWork.spymaster(server, kingdomId), rules),
                hire, rules.maxSpies());
    }

    private static NobilityViews.DemandRow demandRow(MinecraftServer server, NobilityState state, Court court, Demand demand) {
        Component house = state.house(demand.house()).<Component>map(NobilityTexts::house).orElse(Component.literal("?"));
        Component text = switch (demand.kind()) {
            case LOWER_TAX -> Component.translatable(KEY + "demand.lower_tax", demand.value());
            case BUILD, SHARE_LOOT -> Component.translatable(KEY + "demand." + demand.kind().id(), NobilityTexts.money(server, demand.kingdomId(), demand.value()));
            case DECLARE_WAR, MAKE_PEACE -> Component.translatable(KEY + "demand." + demand.kind().id(),
                    demand.target() == null ? Component.literal("?") : WarWork.name(server, demand.target()));
            case GRANT_TITLE -> Component.translatable(KEY + "demand.grant_title", house);
        };
        long days = Math.max(0, demand.due() - court.playedDays());
        return new NobilityViews.DemandRow(demand.id(), house, demand.faction(), demand.kind(), text, days);
    }

    private static Component doing(MinecraftServer server, Spy spy, long day) {
        return switch (spy.status()) {
            case IDLE -> Component.translatable(KEY + "spy.idle");
            case COUNTER -> Component.translatable(KEY + "spy.counter");
            case MISSION -> spy.mission().<Component>map(m -> Component.translatable(KEY + "spy.mission", NobilityTexts.task(m.task()),
                    WarWork.name(server, m.target()), Math.max(0, m.ends() - day))).orElse(Component.empty());
            case CAPTURED -> Component.translatable(KEY + "spy.captured", spy.captor().<Component>map(c -> WarWork.name(server, c)).orElse(Component.literal("?")),
                    NobilityTexts.money(server, spy.owner(), spy.ransom()), Math.max(0, spy.releaseBy() - day));
        };
    }

    // ---------------------------------------------------------------- actions of the tab

    public static void handleTab(ServerPlayer player, NobilityPackets.TabChange change) {
        KingdomActions.reply(player, apply(player, change));
        sendTab(player, change.kingdomId());
    }

    static KingdomActions.Feedback apply(ServerPlayer player, NobilityPackets.TabChange change) {
        MinecraftServer server = player.server;
        UUID actor = player.getUUID();
        UUID kingdomId = change.kingdomId();
        if (!KingdomWorldApi.state(server).membership().isMember(actor, kingdomId) || WarWork.isNpc(server, kingdomId)) {
            return KingdomActions.Feedback.error(KingdomError.NO_PERMISSION);
        }
        Outcome outcome = switch (change.action()) {
            case GRANT -> {
                Optional<Fief> fief = Fief.parse(change.word());
                Title[] titles = Title.values();
                if (fief.isEmpty() || change.second() < 0 || change.second() >= titles.length) {
                    yield Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
                }
                UUID subject = change.targetOrNull();
                yield NobilityActions.grant(server, actor, kingdomId, titles[(int) change.second()], fief.get(), subject != null ? 0 : change.first(), subject);
            }
            case REVOKE -> NobilityActions.revoke(server, actor, kingdomId, change.first());
            case TRANSFER -> NobilityActions.transfer(server, actor, kingdomId, change.first(), change.targetOrNull() != null ? 0 : change.second(),
                    change.targetOrNull());
            case ANSWER -> Demand.Answer.byId(change.word()).map(answer -> NobilityActions.answer(server, actor, kingdomId, change.first(), answer))
                    .orElse(Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE));
            case VERDICT -> Plot.Verdict.byId(change.word()).map(verdict -> NobilityActions.verdict(server, actor, kingdomId, change.first(), verdict))
                    .orElse(Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE));
            case HIRE -> NobilityActions.hire(server, actor, kingdomId);
            case SEND -> Spy.Task.byId(change.word()).map(task -> NobilityActions.send(server, actor, kingdomId, change.first(), task, change.targetOrNull(), 0))
                    .orElse(Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE));
            case STATION -> NobilityActions.station(server, actor, kingdomId, change.first(), change.second() == 1);
            case RANSOM -> NobilityActions.ransom(server, actor, kingdomId, change.first());
            case DISMISS -> NobilityActions.dismiss(server, actor, kingdomId, change.first());
        };
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        return KingdomActions.Feedback.ok(KEY + "tab.done." + change.action().name().toLowerCase(java.util.Locale.ROOT));
    }
}
