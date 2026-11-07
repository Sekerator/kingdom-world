package io.github.sekerator.kingdomworld.common.life;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenCare;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.construction.BuildingEffects;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.LifeViews;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import io.github.sekerator.kingdomworld.common.network.packet.LifePackets;
import io.github.sekerator.kingdomworld.common.road.CityWalls;
import io.github.sekerator.kingdomworld.common.trade.Purses;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.construction.BuildingRoles;
import io.github.sekerator.kingdomworld.core.life.DaySchedule;
import io.github.sekerator.kingdomworld.core.life.Festivals;
import io.github.sekerator.kingdomworld.core.life.LifeRules;
import io.github.sekerator.kingdomworld.core.life.LifeState;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The life of a kingdom in the citizens tab: how the citizens spend their day now, who is ill and where, festivals and the
 * fairs of its cities with their chronicle; and the festival the king calls there.
 */
public final class LifeActions {
    private LifeActions() {
    }

    public static void sendTab(ServerPlayer player, UUID kingdomId) {
        MinecraftServer server = player.server;
        if (!KingdomWorldApi.state(server).membership().isMember(player.getUUID(), kingdomId)) {
            return;
        }
        view(server, kingdomId, player.getUUID()).ifPresent(view -> KingdomWorldNetwork.sendToPlayer(player, new LifePackets.TabResponse(view)));
    }

    public static Optional<LifeViews.TabView> view(MinecraftServer server, UUID kingdomId, UUID viewer) {
        Optional<TradeViews.Currency> currency = Purses.currency(server, kingdomId);
        if (currency.isEmpty()) {
            return Optional.empty();
        }
        LifeRules rules = LifeSettings.rules();
        LifeState life = LifeWork.state(server);
        long today = LifeWork.today(server);
        int[] counts = new int[4];
        List<LifeViews.Sick> sick = new ArrayList<>();
        List<Citizen> people = KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdomId);
        for (Citizen citizen : people) {
            DaySchedule.Phase phase = LifeWork.phase(server, citizen.id());
            boolean watch = LifeWork.keepsWatch(citizen.professionId());
            if (phase == DaySchedule.Phase.NIGHT && !watch) {
                counts[0]++;
            } else if (watch || (phase == DaySchedule.Phase.MORNING || phase == DaySchedule.Phase.DAY)
                    && !Professions.UNEMPLOYED.id().equals(citizen.professionId()) && !LifeWork.festivalRest(server, kingdomId)) {
                counts[1]++;
            } else if (phase == DaySchedule.Phase.EVENING && life.tavernDay(citizen.id()) == today) {
                counts[2]++;
            } else {
                counts[3]++;
            }
            life.sick(citizen.id()).ifPresent(state -> {
                CitizenEntity body = CitizenBodies.body(citizen.id());
                BlockPos where = null;
                if (body != null && CitizenCare.isInInfirmary(body)) {
                    var chosen = CitizenCare.chooseInfirmary(body);
                    where = chosen == null ? null : new BlockPos(chosen.x(), chosen.y(), chosen.z());
                }
                sick.add(new LifeViews.Sick(citizen.nameSeed(), citizen.gender() == Gender.FEMALE, citizen.professionId(), where,
                        (int) Math.round(state.progress() * 100)));
            });
        }
        int[] hours = new int[4];
        for (DaySchedule.Phase phase : DaySchedule.Phase.values()) {
            hours[phase.ordinal()] = (int) Math.round(DaySchedule.length(phase, rules) / 1000.0);
        }
        LifeState.Festival festival = life.festival(kingdomId);
        long since = festival.festivalDay() == Long.MIN_VALUE ? -1 : Math.max(0, today - festival.festivalDay());
        int[] taverns = new int[2];
        int guests = 0;
        for (BuildingEffects.Effect effect : BuildingEffects.working(server, kingdomId, BuildingRoles.TAVERN)) {
            taverns[0]++;
            if (LifeWork.open(server, effect.building())) {
                taverns[1]++;
            }
            guests += LifeWork.guestsTonight(effect.building().id(), today);
        }
        List<LifeViews.Fair> fairs = new ArrayList<>();
        for (CityWalls.Place place : CityWalls.places(server)) {
            if (place.kingdomId().equals(kingdomId)) {
                long days = LifeFairs.daysToFair(server, place);
                if (days >= 0) {
                    fairs.add(new LifeViews.Fair(place.name(), days));
                }
            }
        }
        List<LifeViews.Event> events = new ArrayList<>();
        for (LifeState.Event event : life.events(kingdomId)) {
            events.add(new LifeViews.Event(event.kind(), event.day() + 1, event.place()));
        }
        boolean canManage = KingdomWorldApi.state(server).membership().hasPermission(viewer, kingdomId, Permission.MANAGE_CITIZENS);
        return Optional.of(new LifeViews.TabView(kingdomId, canManage, counts, hours, sick, since,
                Festivals.daysUntilNext(festival.called(), today, rules), Festivals.cost(people.size(), rules), Festivals.treat(people.size(), rules),
                rules.festivalMood(), taverns, guests, fairs, events, currency.get()));
    }

    public static void callFestival(ServerPlayer player, UUID kingdomId) {
        KingdomActions.Feedback feedback = LifeWork.callFestival(player.server, player.getUUID(), kingdomId);
        KingdomActions.reply(player, feedback);
        sendTab(player, kingdomId);
    }
}
