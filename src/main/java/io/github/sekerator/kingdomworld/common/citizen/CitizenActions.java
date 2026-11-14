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
            BlockPos work = citizen.workplace() == null ? null
                    : new BlockPos(citizen.workplace().x(), citizen.workplace().y(), citizen.workplace().z());
            entries.add(new CitizenView.Entry(citizen.id(), citizen.nameSeed(), citizen.gender() == Gender.FEMALE,
                    citizen.professionId(), citizen.state(), home, Math.round(citizen.health()), citizen.mood(), work,
                    citizen.workStatus()));
        }
        List<CitizenView.Death> chronicle = new ArrayList<>();
        for (DeathRecord record : state.citizens().registry().chronicle(kingdomId)) {
            if (chronicle.size() >= CitizenView.MAX_CHRONICLE) {
                break;
            }
            chronicle.add(new CitizenView.Death(record.nameSeed(), record.gender() == Gender.FEMALE, record.professionId(),
                    record.cause(), record.killer(), record.day(),
                    new BlockPos(record.place().x(), record.place().y(), record.place().z()), record.killerType(), record.damage()));
        }
        return Optional.of(new CitizenView(kingdomId, state.membership().hasPermission(viewer, kingdomId, Permission.MANAGE_CITIZENS),
                summary.population(), summary.bedsUsed(), summary.bedsTotal(), summary.foodDays(), summary.mood(),
                summary.storehouses(), summary.growth(), entries, chronicle, production(server, kingdomId),
                io.github.sekerator.kingdomworld.common.npc.NpcRulers.chronicle(server, kingdomId, viewer), settlements(server, kingdomId)));
    }

    /**
     * The settlements of a state of the world besides its capital, as the citizens tab lists them: the largest first.
     */
    static List<CitizenView.SettlementRow> settlements(MinecraftServer server, UUID kingdomId) {
        List<CitizenView.SettlementRow> rows = new ArrayList<>();
        List<io.github.sekerator.kingdomworld.core.npc.Settlement> own = new ArrayList<>(
                io.github.sekerator.kingdomworld.common.npc.NpcWork.state(server).settlementsOf(kingdomId));
        own.sort(java.util.Comparator.comparingInt((io.github.sekerator.kingdomworld.core.npc.Settlement settlement) -> settlement.tier().ordinal())
                .thenComparing(settlement -> -settlement.population()));
        java.util.Map<Integer, Integer> named = io.github.sekerator.kingdomworld.common.npc.SettlementResidents.named(server, kingdomId);
        for (io.github.sekerator.kingdomworld.core.npc.Settlement settlement : own) {
            if (settlement.tier() == io.github.sekerator.kingdomworld.core.land.Tier.CAPITAL
                    || settlement.status() == io.github.sekerator.kingdomworld.core.npc.Settlement.Status.RUINED) {
                continue;
            }
            if (rows.size() >= CitizenView.MAX_SETTLEMENTS) {
                break;
            }
            int people = Math.max(1, settlement.population());
            rows.add(new CitizenView.SettlementRow(settlement.name().latin(), settlement.name().cyrillic(), settlement.tier(),
                    settlement.population(), settlement.housing(), Math.min(CitizenView.MAX_FOOD_DAYS, settlement.food() / people),
                    settlement.guards(), settlement.militia(), settlement.status() != io.github.sekerator.kingdomworld.core.npc.Settlement.Status.STANDING,
                    named.getOrDefault(settlement.id(), 0)));
        }
        return rows;
    }

    /**
     * What each profession produced today and yesterday, from the kingdom's record of production.
     */
    public static List<CitizenView.Production> production(MinecraftServer server, UUID kingdomId) {
        long day = io.github.sekerator.kingdomworld.common.citizen.work.PostWork.gameDay(server);
        var ledger = KingdomWorldApi.state(server).citizens().ledger();
        var today = ledger.today(kingdomId, day);
        var yesterday = ledger.yesterday(kingdomId, day);
        java.util.Set<String> professions = new java.util.TreeSet<>(today.counts().keySet());
        professions.addAll(yesterday.counts().keySet());
        List<CitizenView.Production> result = new ArrayList<>();
        for (String profession : professions) {
            if (result.size() >= CitizenView.MAX_PRODUCTION) {
                break;
            }
            var items = today.counts().getOrDefault(profession, yesterday.counts().getOrDefault(profession, java.util.Map.of()));
            String top = items.entrySet().stream().filter(entry -> !entry.getKey().equals(
                            io.github.sekerator.kingdomworld.core.citizen.ProductionLedger.OTHER))
                    .max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse("");
            result.add(new CitizenView.Production(profession, today.total(profession), yesterday.total(profession), top));
        }
        return result;
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
        afterProfessionChange(server, citizen.get());
        return KingdomActions.Feedback.ok("kingdomworld.citizen.profession_set", CitizenTexts.name(citizen.get()),
                CitizenTexts.profession(professionId, citizen.get().gender()));
    }

    /**
     * After a citizen got another profession: it looks for a workplace of the new one right away, puts tools that
     * do not suit the new work back into the storehouses, and its body follows.
     */
    public static void afterProfessionChange(MinecraftServer server, Citizen citizen) {
        CitizenSimulation.findPlaces(server, KingdomWorldApi.state(server).citizens(), citizen);
        CitizenEntity body = CitizenBodies.body(citizen.id());
        io.github.sekerator.kingdomworld.common.citizen.work.Equipment.Hands hands = body != null
                ? io.github.sekerator.kingdomworld.common.citizen.work.Equipment.of(body)
                : io.github.sekerator.kingdomworld.common.citizen.work.Equipment.of(server, citizen.id());
        io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehavior behavior =
                io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehaviors.get(citizen.professionId()).orElse(null);
        var need = behavior == null ? io.github.sekerator.kingdomworld.common.citizen.work.Equipment.ToolNeed.NONE : behavior.tool();
        var second = behavior == null ? io.github.sekerator.kingdomworld.common.citizen.work.Equipment.ToolNeed.NONE : behavior.secondTool();
        io.github.sekerator.kingdomworld.common.citizen.work.Equipment.stowUnsuitable(server, citizen.kingdomId(), hands,
                net.minecraft.world.entity.EquipmentSlot.MAINHAND, need, citizen.lastPos());
        io.github.sekerator.kingdomworld.common.citizen.work.Equipment.stowUnsuitable(server, citizen.kingdomId(), hands,
                net.minecraft.world.entity.EquipmentSlot.OFFHAND, second, citizen.lastPos());
        if (body != null) {
            CitizenBodies.syncBack(server, body, citizen);
        }
    }

    /**
     * The citizen's kingdom, if any.
     */
    public static Optional<Kingdom> kingdomOf(MinecraftServer server, Citizen citizen) {
        return KingdomWorldApi.kingdom(server, citizen.kingdomId());
    }
}
