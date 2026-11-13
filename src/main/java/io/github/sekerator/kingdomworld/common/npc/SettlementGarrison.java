package io.github.sekerator.kingdomworld.common.npc;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmySettings;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.road.CityWalls;
import io.github.sekerator.kingdomworld.common.road.RoadSync;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.army.ArmyRules;
import io.github.sekerator.kingdomworld.core.army.ArmyState;
import io.github.sekerator.kingdomworld.core.army.Branch;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.land.Tier;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
import io.github.sekerator.kingdomworld.core.npc.Settlement;
import io.github.sekerator.kingdomworld.core.npc.SettlementLayouts;
import io.github.sekerator.kingdomworld.core.npc.SettlementLife;
import io.github.sekerator.kingdomworld.core.npc.SettlementStage;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The guard, the wall and the posts of a settlement of a state as it is raised: its guard (a number while it is data) comes from
 * abroad as soldiers of the guard with the kits of their posts paid by the treasury, without taking a citizen from his work; a
 * large city gets its wall of its style and a small town of a large kingdom its palisade, paid as data and placed when their
 * chunks load; then the guard takes its post at the town hall.
 */
public final class SettlementGarrison {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Soldiers coming from abroad in a tick at most. */
    private static final int GUARDS_PER_TICK = 2;

    private SettlementGarrison() {
    }

    /**
     * The guard a settlement keeps once it stands: a large city 8, a small town 4, villages and hamlets none (their militia is
     * a number only).
     */
    public static int guards(Tier tier) {
        return SettlementLife.guards(tier);
    }

    /**
     * The soldiers of the guard of a kingdom that serve a standing settlement, by settlement.
     */
    public static Map<Integer, Integer> guardsBySettlement(MinecraftServer server, UUID kingdomId) {
        Map<Integer, Integer> counts = new HashMap<>();
        ArmyState army = ArmyWork.state(server);
        KingdomWorldState world = KingdomWorldApi.state(server);
        army.branchOf(kingdomId, Branch.GUARD).forEach(soldier -> world.citizens().get(soldier.citizenId())
                .filter(citizen -> citizen.settlement() != 0)
                .ifPresent(citizen -> counts.merge(citizen.settlement(), 1, Integer::sum)));
        return counts;
    }

    /**
     * The guard of the standing settlements of a kingdom, to add to the guard of its capital.
     */
    public static int standingGuards(MinecraftServer server, UUID kingdomId) {
        int guards = 0;
        for (Settlement settlement : NpcWork.state(server).settlementsOf(kingdomId)) {
            if (settlement.status() == Settlement.Status.STANDING || settlement.status() == Settlement.Status.RAISING
                    && SettlementStage.byId(settlement.stage()).ordinal() > SettlementStage.GUARDS.ordinal()) {
                guards += guards(settlement.tier());
            }
        }
        return guards;
    }

    /**
     * The settlement whose town a city of the roads is, if any.
     */
    public static Optional<Settlement> ofCity(MinecraftServer server, UUID kingdomId, long cityId) {
        for (Settlement settlement : NpcWork.state(server).settlementsOf(kingdomId)) {
            if (settlement.cityId() == cityId && cityId != 0) {
                return Optional.of(settlement);
            }
        }
        return Optional.empty();
    }

    /**
     * The guard of a settlement comes from abroad, a part a tick: each soldier lives in the settlement, serves in the guard and
     * gets the kit of its post from the treasury, by the books.
     */
    static void guards(MinecraftServer server, Settlement settlement, SettlementLayouts.Layout layout, long deadline) {
        int wanted = guards(settlement.tier());
        if (wanted <= 0 || settlement.given(Settlement.GIVEN_GUARDS) || ArmyWork.isReadOnly(server) || KingdomWorldApi.isReadOnly(server)) {
            settlement.give(Settlement.GIVEN_GUARDS);
            settlement.setStage(SettlementStage.ROADS.id(), 0);
            return;
        }
        UUID kingdomId = settlement.kingdomId();
        int have = guardsBySettlement(server, kingdomId).getOrDefault(settlement.id(), 0);
        ArmyState army = ArmyWork.state(server);
        KingdomWorldState world = KingdomWorldApi.state(server);
        ArmyRules rules = ArmySettings.rulesFor(server, kingdomId);
        WorldPos hall = new WorldPos(Dimensions.id(server.overworld()), settlement.x(), settlement.groundY() + 1, settlement.z());
        int joined = 0;
        while (have < wanted && joined < GUARDS_PER_TICK && System.nanoTime() - deadline < 0) {
            Optional<Citizen> recruit = CitizenLife.arrive(server, kingdomId, hall, false, false);
            if (recruit.isEmpty()) {
                break;
            }
            UUID id = recruit.get().id();
            String profession = have % 2 == 0 ? Professions.WARRIOR.id() : Professions.ARCHER.id();
            Outcome outcome = ArmyWork.change(server, "enlist a guard of a settlement",
                    state -> state.recruit(null, kingdomId, id, profession, KingdomWorldApi.gameTime(server), rules, world));
            if (!outcome.ok()) {
                LOGGER.info("The guard of {} was not taken into the army: {}", settlement, outcome.error());
                KingdomWorldApi.change(server, "send a recruit back", state -> {
                    state.citizens().registry().remove(id);
                    return Outcome.DONE;
                });
                // No room in the army of the kingdom: the settlement keeps its guard as a number.
                settlement.give(Settlement.GIVEN_GUARDS);
                settlement.setStage(SettlementStage.ROADS.id(), 0);
                return;
            }
            KingdomWorldApi.change(server, "settle a guard", state -> {
                recruit.get().setSettlement(settlement.id());
                return Outcome.DONE;
            });
            army.setBranch(null, id, Branch.GUARD, world);
            army.soldier(id).ifPresent(soldier -> io.github.sekerator.kingdomworld.common.army.ArmyKits.issue(server, recruit.get(), soldier, false));
            // A guard sleeps in a bunk of the barracks of the settlement if it has one, otherwise in a free bed of its houses.
            ArmyWork.billet(server, recruit.get());
            have++;
            joined++;
        }
        if (have >= wanted) {
            settlement.give(Settlement.GIVEN_GUARDS);
            LOGGER.info("The guard of {} came: {} soldier(s)", settlement, have);
            settlement.setStage(SettlementStage.ROADS.id(), 0);
        }
    }

    /**
     * The wall of a large city in the style of its kingdom (stone or a palisade) and the palisade of a small town of a large
     * kingdom, around its town hall: laid out as data, paid a part a tick, placed as their chunks load.
     */
    static void wall(MinecraftServer server, ServerLevel level, Settlement settlement, NpcSite site, SettlementLayouts.Layout layout) {
        if (layout.wall() <= 0 || settlement.cityId() == 0 || RoadWork.isReadOnly(server)) {
            settlement.setStage(SettlementStage.LAW.id(), 0);
            return;
        }
        UUID kingdomId = settlement.kingdomId();
        Optional<City> city = RoadWork.state(server).city(settlement.cityId());
        if (city.isEmpty()) {
            settlement.setStage(SettlementStage.LAW.id(), 0);
            return;
        }
        Optional<Wall> existing = RoadWork.state(server).wallOf(city.get().id(), kingdomId);
        if (existing.isEmpty()) {
            Wall[] made = new Wall[1];
            boolean stone = layout.city() != null && layout.city().stoneWall();
            Outcome built = CityWalls.build(server, null, kingdomId, city.get().id(), stone ? Wall.WallClass.STONE : Wall.WallClass.PALISADE, made,
                    layout.wall());
            if (!built.ok() || made[0] == null) {
                LOGGER.warn("The wall of {} was refused: {}", settlement, built.error());
                settlement.setStage(SettlementStage.LAW.id(), 0);
                return;
            }
            existing = Optional.of(made[0]);
        }
        if (NpcSpawner.payWall(server, level, site, existing.get())) {
            RoadSync.changed();
            settlement.setStage(SettlementStage.LAW.id(), 0);
        }
    }

    /**
     * The town of a settlement takes the laws of its ruler (set for the kingdom at its founding) and its guard its post at the
     * town hall.
     */
    static void law(MinecraftServer server, Settlement settlement, NpcSite site, SettlementLayouts.Layout layout) {
        if (!ArmyWork.isReadOnly(server) && guards(settlement.tier()) > 0) {
            NpcGarrison.station(server, settlement.kingdomId());
        }
        settlement.setStage(SettlementStage.DONE.id(), 0);
    }

    static int standing(NpcState state, UUID kingdomId) {
        int count = 0;
        for (Settlement settlement : state.settlementsOf(kingdomId)) {
            if (settlement.status() == Settlement.Status.STANDING) {
                count++;
            }
        }
        return count;
    }
}
