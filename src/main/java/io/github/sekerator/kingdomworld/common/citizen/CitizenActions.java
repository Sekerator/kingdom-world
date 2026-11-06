package io.github.sekerator.kingdomworld.common.citizen;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.CitizenView;
import io.github.sekerator.kingdomworld.common.network.packet.CitizenViewPacket;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.Citizens;
import io.github.sekerator.kingdomworld.core.citizen.DeathRecord;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What players do with citizens and what they are shown, shared by the throne screen and the commands.
 */
public final class CitizenActions {

    /**
     * The numbers of a kingdom's population.
     *
     * @param foodDays whole days the storehouses feed everybody, {@link CitizenView#UNLIMITED_FOOD} if citizens
     *                 need no food
     */
    public record Summary(int population, int bedsUsed, int bedsTotal, int foodDays, int mood, int storehouses,
                          CitizenNeeds.GrowthCheck growth) {
    }

    private CitizenActions() {
    }

    public static Summary summary(MinecraftServer server, UUID kingdomId) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        Citizens citizens = state.citizens();
        List<Citizen> people = citizens.registry().citizensOf(kingdomId);
        double days = CitizenNeeds.foodDays(KingdomStorage.totalNutrition(server, kingdomId), people.size(),
                CitizenSettings.rules().dailyNutrition());
        int foodDays = Double.isInfinite(days) ? CitizenView.UNLIMITED_FOOD : (int) Math.min(CitizenView.MAX_FOOD_DAYS, Math.floor(days));
        return new Summary(people.size(), citizens.sites().occupied(kingdomId, SiteKinds.BED),
                citizens.sites().count(kingdomId, SiteKinds.BED), foodDays, CitizenSimulation.averageMood(people),
                KingdomStorage.storehouses(server, kingdomId, null).size(), CitizenSimulation.growthCheck(server, kingdomId));
    }

    /**
     * The "Citizens" tab for a member of the kingdom, or empty for anybody else.
     */
    public static Optional<CitizenView> view(MinecraftServer server, UUID kingdomId, UUID viewer) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        if (!state.membership().isMember(viewer, kingdomId)) {
            return Optional.empty();
        }
        Summary summary = summary(server, kingdomId);
        List<CitizenView.Entry> entries = new ArrayList<>();
        for (Citizen citizen : state.citizens().registry().citizensOf(kingdomId)) {
            if (entries.size() >= CitizenView.MAX_CITIZENS) {
                break;
            }
            BlockPos home = citizen.home() == null ? null : new BlockPos(citizen.home().x(), citizen.home().y(), citizen.home().z());
            entries.add(new CitizenView.Entry(citizen.id(), citizen.nameSeed(), citizen.gender() == Gender.FEMALE,
                    citizen.professionId(), citizen.state(), home, Math.round(citizen.health()), citizen.mood()));
        }
        List<CitizenView.Death> chronicle = new ArrayList<>();
        for (DeathRecord record : state.citizens().registry().chronicle(kingdomId)) {
            if (chronicle.size() >= CitizenView.MAX_CHRONICLE) {
                break;
            }
            chronicle.add(new CitizenView.Death(record.nameSeed(), record.gender() == Gender.FEMALE, record.professionId(),
                    record.cause(), record.killer(), record.day(),
                    new BlockPos(record.place().x(), record.place().y(), record.place().z())));
        }
        return Optional.of(new CitizenView(kingdomId, state.membership().hasPermission(viewer, kingdomId, Permission.MANAGE_CITIZENS),
                summary.population(), summary.bedsUsed(), summary.bedsTotal(), summary.foodDays(), summary.mood(),
                summary.storehouses(), summary.growth(), entries, chronicle));
    }

    public static void sendView(ServerPlayer player, UUID kingdomId) {
        view(player.server, kingdomId, player.getUUID())
                .ifPresent(view -> KingdomWorldNetwork.sendToPlayer(player, new CitizenViewPacket(view)));
    }

    /**
     * Gives a citizen a profession; needs the right to manage citizens. A citizen that now needs a workplace gets
     * the nearest free one right away.
     */
    public static KingdomActions.Feedback setProfession(ServerPlayer actor, UUID citizenId, String professionId) {
        MinecraftServer server = actor.server;
        Outcome outcome = KingdomWorldApi.change(server, "give a citizen a profession",
                state -> state.setProfession(actor.getUUID(), citizenId, professionId));
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        Optional<Citizen> citizen = KingdomWorldApi.state(server).citizens().get(citizenId);
        if (citizen.isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_CITIZEN);
        }
        CitizenSimulation.findPlaces(server, KingdomWorldApi.state(server).citizens(), citizen.get());
        CitizenEntity body = CitizenBodies.body(citizenId);
        if (body != null) {
            CitizenBodies.syncBack(server, body, citizen.get());
        }
        return KingdomActions.Feedback.ok("kingdomworld.citizen.profession_set", CitizenTexts.name(citizen.get()),
                CitizenTexts.profession(professionId, citizen.get().gender()));
    }

    /**
     * The citizen's kingdom, if any.
     */
    public static Optional<Kingdom> kingdomOf(MinecraftServer server, Citizen citizen) {
        return KingdomWorldApi.kingdom(server, citizen.kingdomId());
    }
}
