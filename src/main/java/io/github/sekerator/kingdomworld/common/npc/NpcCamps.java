package io.github.sekerator.kingdomworld.common.npc;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.ArmyState;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.npc.ArmyCamp;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcRules;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.RoyalArmy;
import io.github.sekerator.kingdomworld.core.npc.RulerDecision;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The camps of the royal armies of the kingdoms of the world.
 *
 * <p>The capital keeps a reserve of the army; the rest of it lives in camps of {@link NpcRules#campSoldiers} soldiers on the land
 * of the kingdom. A camp lies just beyond the land the kingdom appeared with, between the ways to its towns: the ruler claims a
 * square of three chunks there and the way to it, where nobody lived and no other kingdom is near, levels it and raises a drill
 * yard, two barracks, a barn and two watchtowers (the founding of a kingdom of the world, so nothing is paid). A kingdom that
 * finds no such place keeps its army abstractly at the capital and says so in the log.
 *
 * <p>Soldiers of a camp have no beds: they live in it as the army does, and only a part of them has a body when a player comes
 * near ({@link NpcRules#campBodies}).
 */
public final class NpcCamps {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Blocks the middle of a camp lies beyond the land a kingdom appeared with. */
    private static final int BEYOND_LAND = 40;
    /** The least distance from a camp to a town, a capital or another camp, in blocks. */
    private static final int KEEP_AWAY = 96;
    /** The prefix of the posts of the garrison in a camp. */
    static final String DUTY = "camp";

    private NpcCamps() {
    }

    /**
     * The camps a kingdom should have for the army it should have.
     */
    static int wanted(MinecraftServer server, UUID kingdomId) {
        Optional<NpcKingdom> npc = NpcWork.state(server).kingdom(kingdomId);
        if (npc.isEmpty()) {
            return 0;
        }
        return RoyalArmy.camps(NpcSettings.rules(), npc.get().level(), NpcGarrison.royalArmy(server, kingdomId));
    }

    /**
     * The camps of a kingdom that stand.
     */
    public static List<ArmyCamp> built(MinecraftServer server, UUID kingdomId) {
        return NpcWork.state(server).camps(kingdomId).stream().filter(ArmyCamp::built).toList();
    }

    /**
     * The next camp the ruler of a kingdom should raise, as a decision: a camp left half raised by a restart first, then a new
     * one; {@code null} when the kingdom has the camps it needs. A kingdom with no land for a camp is told so once, and its
     * army lives at the capital.
     */
    @Nullable
    public static RulerDecision decision(MinecraftServer server, NpcKingdom npc) {
        UUID id = npc.kingdomId();
        List<ArmyCamp> camps = NpcWork.state(server).camps(id);
        for (ArmyCamp camp : camps) {
            if (camp.status() == ArmyCamp.Status.BUILDING) {
                return of(camp.x(), camp.z());
            }
        }
        if (camps.size() >= wanted(server, id) || !NpcWork.built(server, id)) {
            return null;
        }
        Optional<int[]> spot = spot(server, npc);
        if (spot.isEmpty()) {
            WorldPos throne = ArmyWork.capitalOf(server, id);
            if (throne != null) {
                NpcWork.state(server).putCamp(id, new ArmyCamp(throne.x(), throne.z(), throne.y(), ArmyCamp.Status.NO_LAND));
            }
            LOGGER.info("{} found no land for a camp of its army: {} soldier(s) live abstractly at the capital", npc.name().latin(),
                    Math.max(0, NpcGarrison.royalArmy(server, id) - NpcSettings.rules().army(npc.level())));
            return null;
        }
        return of(spot.get()[0], spot.get()[1]);
    }

    private static RulerDecision of(int x, int z) {
        return new RulerDecision(RulerDecision.Kind.CAMP, "", 1, 0, x + "," + z, "");
    }

    /**
     * The place of a camp a decision names.
     */
    static Optional<int[]> place(RulerDecision decision) {
        String[] parts = decision.target().split(",");
        try {
            return parts.length == 2 ? Optional.of(new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])}) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * A place for a new camp: in one of eight ways from the capital, between the ways to its towns, just beyond the land the
     * kingdom appeared with; far from towns, capitals and other camps, and where no other kingdom holds or touches the land of
     * the camp or of the way to it. Empty if there is none.
     */
    static Optional<int[]> spot(MinecraftServer server, NpcKingdom npc) {
        UUID id = npc.kingdomId();
        KingdomWorldState state = KingdomWorldApi.state(server);
        Optional<Territory> territory = state.territory().territory(id);
        Optional<NpcSite> site = NpcWork.state(server).site(npc.siteId());
        if (territory.isEmpty() || site.isEmpty()) {
            return Optional.empty();
        }
        String dimension = territory.get().dimension();
        int capitalX = territory.get().capital().x();
        int capitalZ = territory.get().capital().z();
        // The land the kingdom appeared with is a square of chunks about the throne: the camp lies beyond its edge in every way.
        int distance = NpcCity.landRadius(site.get()) * 16 + 8 + BEYOND_LAND;
        int buffer = KingdomWorldApi.rules().claimBufferChunks() + 1;
        List<int[]> away = new ArrayList<>();
        for (Territory other : state.territory().territories()) {
            if (other.capital().dimension().equals(dimension)) {
                away.add(new int[]{other.capital().x(), other.capital().z()});
            }
        }
        for (City city : RoadWork.state(server).cities()) {
            if (city.dimension().equals(dimension)) {
                away.add(new int[]{city.x(), city.z()});
            }
        }
        for (List<ArmyCamp> camps : NpcWork.state(server).allCamps().values()) {
            for (ArmyCamp camp : camps) {
                if (camp.status() != ArmyCamp.Status.NO_LAND) {
                    away.add(new int[]{camp.x(), camp.z()});
                }
            }
        }
        // The way out of the site, then round from there: a camp of a seed lies on the same side every time.
        int first = (int) Math.floorMod(site.get().seed(), 8L);
        for (int ring = 0; ring < 2; ring++) {
            for (int i = 0; i < 8; i++) {
                double angle = ((first + i) % 8) * Math.PI / 4;
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                double reach = (distance + ring * 48) / Math.max(Math.abs(cos), Math.abs(sin));
                // The middle of a chunk, so that the square of three chunks holds the whole camp.
                int x = (((capitalX + (int) Math.round(cos * reach)) >> 4) << 4) + 8;
                int z = (((capitalZ + (int) Math.round(sin * reach)) >> 4) << 4) + 8;
                if (RulerPlaces.isUnfit(id, new BlockPos(x, 0, z)) || near(away, x, z) || onRoad(server, x, z)) {
                    continue;
                }
                boolean free = true;
                for (long chunk : chunks(capitalX, capitalZ, x, z)) {
                    UUID owner = state.territory().ownerOf(dimension, chunk);
                    if ((owner != null && !owner.equals(id)) || state.territory().hasForeignLandWithin(dimension, chunk, buffer, id)) {
                        free = false;
                        break;
                    }
                }
                if (free) {
                    return Optional.of(new int[]{x, z});
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a road of any kingdom runs through the square of a camp.
     */
    private static boolean onRoad(MinecraftServer server, int x, int z) {
        int reach = ArmyCamp.REACH + 4;
        for (io.github.sekerator.kingdomworld.core.road.Road road : RoadWork.state(server).roads()) {
            for (io.github.sekerator.kingdomworld.core.road.RouteCell cell : road.cells()) {
                if (Math.abs(cell.x() - x) <= reach && Math.abs(cell.z() - z) <= reach) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean near(List<int[]> places, int x, int z) {
        for (int[] place : places) {
            long dx = place[0] - x;
            long dz = place[1] - z;
            if (dx * dx + dz * dz < (long) KEEP_AWAY * KEEP_AWAY) {
                return true;
            }
        }
        return false;
    }

    /**
     * The chunks of a camp: the way from the capital and the square of three chunks around its middle, in the order they are
     * claimed.
     */
    static List<Long> chunks(int capitalX, int capitalZ, int x, int z) {
        Set<Long> keys = new LinkedHashSet<>(RulerPlaces.townChunks(capitalX, capitalZ, x, z));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                keys.add(ChunkKey.of((x >> 4) + dx, (z >> 4) + dz));
            }
        }
        return new ArrayList<>(keys);
    }

    // ---------------------------------------------------------------- the fields

    /** What the fields of every camp grew and has not gone to the barn yet, by kingdom and camp. */
    private static final java.util.Map<String, Double> GROWN = new java.util.HashMap<>();
    /** What the fields of every camp grew beyond what its soldiers eat, waiting for the sutlers, by kingdom and camp. */
    private static final java.util.Map<String, Double> SPARE = new java.util.HashMap<>();
    /** Carrots the sutlers of a camp take at a time. */
    private static final int SUTLERS_LOT = 64;

    /**
     * The soldiers of a camp work its fields in peace, two to a field, at the pace of farmers ({@link
     * io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds#farmOutput}). What its soldiers eat goes to the storehouses
     * nearest the camp; the rest the sutlers of the camp buy at the price of an export (the carrots leave the world and the
     * money comes from abroad, so nothing is made), and the camp pays a good part of the pay of its army. A camp whose squads
     * are away (a war, a siege) works no field.
     */
    public static void farm(MinecraftServer server, UUID kingdomId, int intervalTicks) {
        if (!NpcWork.isNpc(server, kingdomId) || KingdomWorldApi.isReadOnly(server)) {
            return;
        }
        ArmyState army = ArmyWork.state(server);
        List<ArmyCamp> camps = built(server, kingdomId);
        for (int c = 0; c < camps.size(); c++) {
            ArmyCamp camp = camps.get(c);
            int hands = 0;
            for (ArmySquad squad : army.squadsOf(kingdomId)) {
                Optional<String> duty = army.duty(squad.id());
                if (duty.isPresent() && duty.get().startsWith(DUTY + c + "_") && squad.order().kind() == io.github.sekerator.kingdomworld.core.army.Order.Kind.GUARD) {
                    hands += squad.members().size();
                }
            }
            hands = Math.min(hands, ArmyCamp.FIELDS * ArmyCamp.FIELD_HANDS);
            if (hands <= 0) {
                continue;
            }
            String key = kingdomId + ":" + c;
            double crop = hands * io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds.farmOutput(ArmyCamp.FIELD_PLOTS, intervalTicks);
            // The soldiers of the camp eat carrots, three points of food each.
            double eaten = soldiersOf(army, kingdomId, c) * io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds.hunger(
                    io.github.sekerator.kingdomworld.common.citizen.CitizenSettings.rules().dailyNutrition(), intervalTicks) / CARROT_NUTRITION;
            double kept = Math.min(crop, eaten);
            sell(server, kingdomId, key, crop - kept);
            double grown = GROWN.getOrDefault(key, 0.0) + kept;
            int whole = (int) Math.floor(grown);
            GROWN.put(key, grown - whole);
            if (whole <= 0) {
                continue;
            }
            WorldPos at = ArmyWork.capitalOf(server, kingdomId);
            WorldPos near = at == null ? null : new WorldPos(at.dimension(), camp.x(), camp.y() + 1, camp.z());
            int left = whole;
            while (left > 0) {
                int count = Math.min(left, 64);
                // What the storehouses cannot take rots in the fields.
                io.github.sekerator.kingdomworld.common.storage.KingdomStorage.insert(server, kingdomId,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CARROT, count), near);
                left -= count;
            }
            KingdomWorldApi.state(server).citizens().ledger().record(kingdomId,
                    io.github.sekerator.kingdomworld.common.citizen.work.PostWork.gameDay(server),
                    io.github.sekerator.kingdomworld.core.citizen.Professions.WARRIOR.id(), "minecraft:carrot", whole);
        }
    }

    /** Food points a carrot gives. */
    private static final double CARROT_NUTRITION = 3.0;

    private static int soldiersOf(ArmyState army, UUID kingdomId, int camp) {
        int count = 0;
        for (ArmySquad squad : army.squadsOf(kingdomId)) {
            if (army.duty(squad.id()).map(duty -> duty.startsWith(DUTY + camp + "_")).orElse(false)) {
                count += squad.members().size();
            }
        }
        return count;
    }

    /**
     * The sutlers of a camp buy what its fields grew beyond what its soldiers eat, a lot at a time.
     */
    private static void sell(MinecraftServer server, UUID kingdomId, String key, double spare) {
        double waiting = SPARE.getOrDefault(key, 0.0) + Math.max(0.0, spare);
        if (waiting < SUTLERS_LOT) {
            SPARE.put(key, waiting);
            return;
        }
        int lot = (int) Math.floor(waiting);
        double worth = io.github.sekerator.kingdomworld.common.trade.MarketStock.worth(server, net.minecraft.world.item.Items.CARROT).amount();
        long money = io.github.sekerator.kingdomworld.core.trade.Prices.exportIncome(worth, lot,
                io.github.sekerator.kingdomworld.common.trade.TradeSettings.rules());
        if (money > 0 && KingdomWorldApi.state(server).economy().exportIncome(kingdomId, money,
                io.github.sekerator.kingdomworld.common.economy.TreasuryActions.day(server)).ok()) {
            SPARE.put(key, waiting - lot);
            LOGGER.debug("The sutlers of a camp of {} bought {} carrot(s) for {}", kingdomId, lot, money);
        } else {
            // Nobody pays for them: they rot.
            SPARE.put(key, 0.0);
        }
    }

    /**
     * Whether a squad stands in a camp or with the army waiting at the capital for one: watchtowers and fires keep the beasts
     * of the night away from it.
     */
    public static boolean sheltered(MinecraftServer server, ArmySquad squad) {
        return ArmyWork.state(server).duty(squad.id()).map(duty -> duty.startsWith(DUTY) || duty.startsWith(NpcGarrison.FIELD))
                .orElse(false) && squad.order().kind() == io.github.sekerator.kingdomworld.core.army.Order.Kind.GUARD;
    }

    static void reset() {
        GROWN.clear();
        SPARE.clear();
    }

    // ---------------------------------------------------------------- who lives there

    /**
     * Whether a soldier serves in the royal army of a kingdom of the world beyond its capital: in a camp, waiting at the
     * capital for one, marching, or just come from abroad. Such a soldier sleeps in the tents of the army, needs no bed and
     * lives as well housed as one who has. The reserve of the army at the capital sleeps in the bunks of its barracks while
     * they hold it.
     */
    public static boolean quartered(MinecraftServer server, UUID citizenId) {
        ArmyState army = ArmyWork.state(server);
        Optional<io.github.sekerator.kingdomworld.core.army.Soldier> soldier = army.soldier(citizenId);
        if (soldier.isEmpty() || soldier.get().branch() != io.github.sekerator.kingdomworld.core.army.Branch.ARMY
                || !NpcWork.isNpc(server, soldier.get().kingdomId())) {
            return false;
        }
        boolean reserve = army.squadOf(citizenId).flatMap(squad -> army.duty(squad.id())).map(duty -> duty.startsWith("reserve")).orElse(false);
        // A reserve the bunks of the capital cannot hold (a capital of the first layout has few) lives in tents as well.
        return !reserve || KingdomWorldApi.state(server).citizens().get(citizenId).map(citizen -> citizen.home() == null).orElse(false);
    }

    /**
     * The group of the budget of bodies a citizen belongs to: a soldier of a royal army of a kingdom of the world beyond its
     * capital (in a camp, waiting at the capital for one, or marching) belongs to its camp, or to the army of its kingdom
     * away from the camps; everybody else, the guard, the reserve at the capital and soldiers in a siege (which has a budget
     * of its own) to none.
     */
    public static String bodyGroup(MinecraftServer server, io.github.sekerator.kingdomworld.core.citizen.Citizen citizen) {
        if (!io.github.sekerator.kingdomworld.core.citizen.Professions.isMilitary(citizen.professionId())) {
            return "";
        }
        ArmyState army = ArmyWork.state(server);
        Optional<io.github.sekerator.kingdomworld.core.army.Soldier> soldier = army.soldier(citizen.id());
        if (soldier.isEmpty() || soldier.get().branch() != io.github.sekerator.kingdomworld.core.army.Branch.ARMY
                || !NpcWork.isNpc(server, citizen.kingdomId())) {
            return "";
        }
        Optional<ArmySquad> squad = army.squadOf(citizen.id());
        if (squad.isPresent()) {
            io.github.sekerator.kingdomworld.core.army.Order.Kind kind = squad.get().order().kind();
            if (kind == io.github.sekerator.kingdomworld.core.army.Order.Kind.SIEGE
                    || kind == io.github.sekerator.kingdomworld.core.army.Order.Kind.DEFEND) {
                return "";
            }
            Optional<String> duty = army.duty(squad.get().id());
            if (duty.isPresent() && duty.get().startsWith("reserve")) {
                return "";
            }
            Optional<String> camp = campOf(server, squad.get());
            if (camp.isPresent()) {
                return citizen.kingdomId() + ":" + camp.get();
            }
        }
        return citizen.kingdomId() + ":away";
    }

    /**
     * The citizens of a kingdom without the royal army beyond its capital: the people of its capital and towns.
     */
    public static int townsfolk(MinecraftServer server, UUID kingdomId) {
        int count = 0;
        for (io.github.sekerator.kingdomworld.core.citizen.Citizen citizen : KingdomWorldApi.state(server).citizens().registry()
                .citizensOf(kingdomId)) {
            count += quartered(server, citizen.id()) ? 0 : 1;
        }
        return count;
    }

    /**
     * The camp a squad stands in, if it is one of a camp.
     */
    public static Optional<String> campOf(MinecraftServer server, ArmySquad squad) {
        return ArmyWork.state(server).duty(squad.id()).filter(duty -> duty.startsWith(DUTY)).map(duty -> {
            int split = duty.indexOf('_');
            return split > 0 ? duty.substring(0, split) : duty;
        });
    }

    /**
     * Where the squads of the camps of a kingdom stand, in the order of the camps: for every camp that stands, the points of
     * its squads.
     */
    static List<List<WorldPos>> squadPlaces(MinecraftServer server, UUID kingdomId, String dimension, int squadsPerCamp) {
        List<List<WorldPos>> places = new ArrayList<>();
        for (ArmyCamp camp : built(server, kingdomId)) {
            List<WorldPos> points = new ArrayList<>();
            for (int[] point : ArmyCamp.squadPoints(squadsPerCamp)) {
                points.add(new WorldPos(dimension, camp.x() + point[0], camp.y() + 1, camp.z() + point[1]));
            }
            places.add(points);
        }
        return places;
    }

    static NpcLevel level(MinecraftServer server, UUID kingdomId) {
        return NpcWork.state(server).kingdom(kingdomId).map(NpcKingdom::level).orElse(NpcLevel.SMALL);
    }
}
