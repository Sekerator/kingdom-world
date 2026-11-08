package io.github.sekerator.kingdomworld.common.nobility;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.nobility.Court;
import io.github.sekerator.kingdomworld.core.nobility.Demand;
import io.github.sekerator.kingdomworld.core.nobility.Fief;
import io.github.sekerator.kingdomworld.core.nobility.NobilityMind;
import io.github.sekerator.kingdomworld.core.nobility.NobilityRules;
import io.github.sekerator.kingdomworld.core.nobility.NobilityState;
import io.github.sekerator.kingdomworld.core.nobility.NobleHouse;
import io.github.sekerator.kingdomworld.core.nobility.Spy;
import io.github.sekerator.kingdomworld.core.nobility.Title;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What rulers of the world do with their courts: answer demands and judge plotters by their traits, make barons of players
 * they trust, and keep a few spies.
 */
final class NobilityNpc {
    /** Game days between two titles a ruler of the world gives to players. */
    private static final long GRANT_EVERY_DAYS = 7;

    private NobilityNpc() {
    }

    static void day(MinecraftServer server, UUID kingdomId, long day) {
        Optional<NpcKingdom> npc = NpcWork.kingdom(server, kingdomId);
        if (npc.isEmpty()) {
            return;
        }
        grantPlayer(server, npc.get(), day);
        spies(server, npc.get(), day);
    }

    static void answer(MinecraftServer server, Demand demand, long day) {
        Optional<NpcKingdom> npc = NpcWork.kingdom(server, demand.kingdomId());
        if (npc.isEmpty()) {
            return;
        }
        long funds = WarWork.funds(server, demand.kingdomId());
        boolean canPay = funds >= Math.max(demand.value(), NobilitySettings.rules().demandValue()) * 3;
        Demand.Answer answer = NobilityMind.answer(demand, npc.get().character(), npc.get().traits(), canPay);
        if (!NobilityActions.answer(server, null, demand.kingdomId(), demand.id(), answer).ok()) {
            NobilityActions.answer(server, null, demand.kingdomId(), demand.id(), Demand.Answer.REFUSE);
        }
    }

    /**
     * A ruler of the world makes a player it knows well and who has a good name in its land a baron of a free march, now and
     * then.
     */
    private static void grantPlayer(MinecraftServer server, NpcKingdom npc, long day) {
        NobilityState state = NobilityWork.state(server);
        Court court = state.court(npc.kingdomId());
        if (day - court.lastPlayerGrant() < GRANT_EVERY_DAYS) {
            return;
        }
        NobilityRules rules = NobilitySettings.rules();
        Optional<Fief> march = NobilityWork.freeFiefs(server, npc.kingdomId()).stream().filter(f -> f.kind() == Fief.Kind.MARCH).findFirst();
        if (march.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Integer> known : NobilityWork.known(server, npc.kingdomId()).entrySet()) {
            UUID player = known.getKey();
            if (known.getValue() < rules.grantDealings() || state.isNoble(npc.kingdomId(), player)
                    || NobilityWork.reputation(server, npc.kingdomId(), player) < rules.grantReputation()) {
                continue;
            }
            if (state.grant(npc.kingdomId(), Title.BARON, march.get(), 0, player, day).ok()) {
                court.grantedPlayer(day);
                state.changed();
                ServerPlayer online = server.getPlayerList().getPlayer(player);
                if (online != null) {
                    online.sendSystemMessage(Component.translatable(NobilityTexts.KEY + "msg.made_baron", WarWork.name(server, npc.kingdomId()),
                            NobilityTexts.fief(server, march.get())).withStyle(ChatFormatting.GOLD));
                }
                NobilityWork.LOGGER.info("The ruler of {} made the player {} a baron", WarWork.name(server, npc.kingdomId()).getString(), player);
                return;
            }
        }
    }

    /**
     * A ruler of the world keeps as many spies as its character wants and sends an idle one against an enemy or a kingdom it
     * dislikes now and then.
     */
    private static void spies(MinecraftServer server, NpcKingdom npc, long day) {
        NobilityState state = NobilityWork.state(server);
        NobilityRules rules = NobilitySettings.rules();
        List<Spy> own = state.spiesOf(npc.kingdomId());
        int wanted = NobilityMind.spies(npc.character(), npc.traits(), rules);
        if (own.size() < wanted && WarWork.funds(server, npc.kingdomId()) >= rules.spyHire() * 4
                && (rules.spyHire() == 0 || KingdomWorldApi.state(server).economy().nobilityPayment(npc.kingdomId(), rules.spyHire(), day).ok())) {
            double roll = NobilityWork.rolls().getAsDouble();
            state.hire(npc.kingdomId(), (int) (roll * Integer.MAX_VALUE), NobilityWork.gender(roll), 35 + (int) (roll * 40), day);
            return;
        }
        Optional<Spy> idle = own.stream().filter(spy -> spy.status() == Spy.Status.IDLE || spy.status() == Spy.Status.COUNTER).findFirst();
        if (idle.isEmpty()) {
            return;
        }
        List<NobilityMind.Mark> marks = new ArrayList<>();
        for (Kingdom other : KingdomWorldApi.kingdoms(server)) {
            if (other.id().equals(npc.kingdomId())) {
                continue;
            }
            boolean otherNpc = WarWork.isNpc(server, other.id());
            long house = otherNpc ? state.houses(other.id()).stream().mapToLong(NobleHouse::id).findFirst().orElse(0) : 0;
            marks.add(new NobilityMind.Mark(other.id(), otherNpc, WarWork.relation(server, npc.kingdomId(), other.id()),
                    WarWork.fighting(server, npc.kingdomId(), other.id()), house));
        }
        NobilityMind.mission(npc.traits(), marks, day, NobilityWork.rolls()).ifPresent(mission ->
                state.send(idle.get().id(), mission.task(), mission.target(), mission.house(), day));
    }
}
