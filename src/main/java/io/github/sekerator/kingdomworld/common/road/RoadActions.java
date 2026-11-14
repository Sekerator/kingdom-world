package io.github.sekerator.kingdomworld.common.road;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.RoadViews;
import io.github.sekerator.kingdomworld.common.network.packet.RoadPackets;
import io.github.sekerator.kingdomworld.common.trade.GoodsStacks;
import io.github.sekerator.kingdomworld.common.trade.TradeSettings;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.road.BanditCamp;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.CityLevel;
import io.github.sekerator.kingdomworld.core.road.Crossing;
import io.github.sekerator.kingdomworld.core.road.Endpoint;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadRules;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.road.TravelPlan;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.trade.Caravan;
import io.github.sekerator.kingdomworld.core.trade.Deal;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The "Roads" tab of the throne screen and planning roads on the atlas: views for members of a kingdom, and the decisions
 * of those who run its construction (repair, pause or give up a road, answer requests to cross the land, rename a city) or
 * its army (send a squad to patrol a road). The server checks every right.
 */
public final class RoadActions {
    private static final String KEY = "kingdomworld.screen.roads.";
    private static final String ROAD = "kingdomworld.road.";

    private RoadActions() {
    }

    // ---------------------------------------------------------------- views

    public static void sendTab(ServerPlayer player, UUID kingdomId) {
        tabView(player.server, kingdomId, player.getUUID())
                .ifPresent(view -> KingdomWorldNetwork.sendToPlayer(player, new RoadPackets.TabResponse(view)));
    }

    private static RoadViews.CityRow cityRow(MinecraftServer server, RoadState roads, UUID kingdomId, long cityId, String name, int x, int y, int z,
                                             int roadCount, java.util.Map<Long, CityPeople.Census> census,
                                             java.util.Map<Long, io.github.sekerator.kingdomworld.common.construction.CityCrews.Crew> crews) {
        CityWalls.Place place = new CityWalls.Place(kingdomId, cityId, "", x, y, z, name);
        int[] counts = CityWalls.place(server, kingdomId, cityId).map(found -> CityWalls.counts(server, found)).orElse(new int[2]);
        CityLevel level = CityLevel.of(counts[0], counts[1], RoadSettings.rules());
        Optional<Wall> wall = roads.wallOf(cityId, kingdomId);
        int built = wall.map(w -> {
            int units = 0;
            int placed = 0;
            for (Road.Section section : w.sections()) {
                units += section.units();
                placed += section.placed();
            }
            return units == 0 ? 1000 : (int) Math.round(1000.0 * placed / units);
        }).orElse(0);
        CityPeople.Census people = census.get(cityId);
        int folk = roads.folk(kingdomId, cityId);
        int freeBeds = people == null ? 0 : io.github.sekerator.kingdomworld.core.road.CityFolk.bedsLeft(people.freeBeds(), folk,
                io.github.sekerator.kingdomworld.common.citizen.FolkSettings.rules());
        io.github.sekerator.kingdomworld.common.construction.CityCrews.Crew crew = crews.getOrDefault(cityId,
                io.github.sekerator.kingdomworld.common.construction.CityCrews.Crew.NONE);
        return new RoadViews.CityRow(cityId, place.name(), x, y, z, cityId == 0, roadCount, level, people == null ? 0 : people.named(), counts[1],
                wall.map(Wall::id).orElse(0L), wall.map(Wall::wallClass).orElse(null), built, wall.map(w -> w.gates().size()).orElse(0), folk,
                people == null ? 0 : people.beds(), freeBeds, crew.builders(), crew.helpers(), crew.sites());
    }

    static String endpointName(MinecraftServer server, Endpoint endpoint) {
        return RoadPlanning.name(server, endpoint).getString();
    }

    /**
     * The tab of a kingdom for a member.
     *
     * @param viewer the member, or {@code null} for an operator
     */
    public static Optional<RoadViews.RoadTabView> tabView(MinecraftServer server, UUID kingdomId, @Nullable UUID viewer) {
        KingdomWorldState world = KingdomWorldApi.state(server);
        Optional<Kingdom> kingdom = world.kingdoms().get(kingdomId);
        if (kingdom.isEmpty() || (viewer != null && !world.membership().isMember(viewer, kingdomId))) {
            return Optional.empty();
        }
        RoadState roads = RoadWork.state(server);
        RoadRules rules = RoadSettings.rules();
        boolean canManage = viewer == null || world.membership().hasPermission(viewer, kingdomId, Permission.MANAGE_CONSTRUCTION);
        boolean canOrder = viewer == null || world.membership().hasPermission(viewer, kingdomId, Permission.MANAGE_ARMY);
        Optional<Capital> capital = RoadWork.capital(server, kingdomId);
        List<RoadViews.CityRow> cities = new ArrayList<>();
        // Towns of kingdoms of the world are named in the alphabet of the viewer.
        net.minecraft.server.level.ServerPlayer reader = viewer == null ? null : server.getPlayerList().getPlayer(viewer);
        String language = reader == null ? "en_us" : reader.getLanguage();
        // Who lives in each city and its beds, and who builds there, counted once for all of them.
        java.util.Map<Long, CityPeople.Census> census = new java.util.HashMap<>();
        for (CityPeople.Census city : CityPeople.census(server, kingdomId)) {
            census.put(city.cityId(), city);
        }
        java.util.Map<Long, io.github.sekerator.kingdomworld.common.construction.CityCrews.Crew> crews =
                io.github.sekerator.kingdomworld.common.construction.CityCrews.of(server, kingdomId);
        capital.ifPresent(c -> cities.add(cityRow(server, roads, kingdomId, 0,
                io.github.sekerator.kingdomworld.common.npc.NpcWork.localKingdom(kingdomId, kingdom.get().name(), language), c.x(), c.y(), c.z(),
                countRoads(roads, Endpoint.Kind.CAPITAL, 0, kingdomId), census, crews)));
        for (City city : roads.citiesOf(kingdomId)) {
            cities.add(cityRow(server, roads, kingdomId, city.id(), io.github.sekerator.kingdomworld.common.npc.NpcWork.localCity(city.name(),
                    language), city.x(), city.y(), city.z(),
                    countRoads(roads, Endpoint.Kind.CITY, city.id(), null), census, crews));
        }
        List<RoadViews.RoadRow> roadRows = new ArrayList<>();
        for (Road road : roads.roadsOf(kingdomId)) {
            if (roadRows.size() >= RoadViews.MAX_ROWS) {
                break;
            }
            roadRows.add(roadRow(server, road, kingdomId));
        }
        List<RoadViews.CrossingRow> crossings = new ArrayList<>();
        long now = KingdomWorldApi.gameTime(server);
        for (Crossing crossing : roads.crossingsOf(kingdomId)) {
            if (crossings.size() >= RoadViews.MAX_ROWS) {
                break;
            }
            boolean incoming = crossing.owner().equals(kingdomId);
            UUID other = incoming ? crossing.requester() : crossing.owner();
            crossings.add(new RoadViews.CrossingRow(crossing.id(), crossing.roadId(), incoming, RoadWork.kingdomName(server, other).getString(),
                    crossing.mode(), crossing.status(), crossing.sections(), crossing.status().open() ? crossing.expires() - now : 0));
        }
        List<RoadViews.CampRow> camps = new ArrayList<>();
        for (BanditCamp camp : roads.camps()) {
            if (!camp.knownTo(kingdomId) || camp.cleared() || camps.size() >= RoadViews.MAX_ROWS) {
                continue;
            }
            int distance = capital.map(c -> (int) Math.round(Math.sqrt(camp.distanceSqr(c.x(), c.z())))).orElse(0);
            camps.add(new RoadViews.CampRow(camp.id(), camp.x(), camp.z(), distance, camp.bandits(), camp.leader(), camp.lootItems(), camp.raids()));
        }
        List<RoadViews.RiskRow> risks = new ArrayList<>();
        for (Deal deal : world.trade().dealsOf(kingdomId)) {
            boolean onRoad = world.trade().caravans().stream()
                    .anyMatch(caravan -> caravan.dealId() == deal.id() && caravan.state() == Caravan.State.TRAVELING);
            if ((deal.status() != Deal.Status.ACTIVE && !onRoad) || risks.size() >= RoadViews.MAX_ROWS) {
                continue;
            }
            risks.add(riskRow(server, deal, kingdomId));
        }
        List<RoadViews.SquadRow> squads = new ArrayList<>();
        if (!ArmyWork.isReadOnly(server)) {
            for (ArmySquad squad : ArmyWork.state(server).squadsOf(kingdomId)) {
                if (!ArmyWork.onTheRoad(server, squad) && squads.size() < RoadViews.MAX_ROWS) {
                    squads.add(new RoadViews.SquadRow(squad.id(), "#" + squad.id(), squad.members().size()));
                }
            }
        }
        return Optional.of(new RoadViews.RoadTabView(kingdomId, canManage, canOrder, RoadBuilding.freeBuilders(server, kingdomId).size(),
                rules.maxCities(), rules.maxRoads(), cities, roadRows, crossings, camps, risks, squads, rules.minCityDistance(), rules.townPeople(),
                rules.townBuildings(), rules.cityPeople(), rules.cityBuildings()));
    }

    private static int countRoads(RoadState roads, Endpoint.Kind kind, long city, @Nullable UUID kingdom) {
        int count = 0;
        for (Road road : roads.roads()) {
            for (Endpoint end : List.of(road.from(), road.to())) {
                if (end.kind() == kind && (kind == Endpoint.Kind.CITY ? end.city() == city : kingdom != null && kingdom.equals(end.kingdom()))) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    static RoadViews.RoadRow roadRow(MinecraftServer server, Road road, UUID kingdomId) {
        List<String> partners = new ArrayList<>();
        for (UUID builder : road.builders()) {
            if (!builder.equals(kingdomId)) {
                partners.add(RoadWork.kingdomName(server, builder).getString());
            }
        }
        if (!road.kingdomId().equals(kingdomId)) {
            String planner = RoadWork.kingdomName(server, road.kingdomId()).getString();
            if (!partners.contains(planner)) {
                partners.add(0, planner);
            }
        }
        int worn = 0;
        int repairing = 0;
        for (Road.Section section : road.sections()) {
            if (section.repairing()) {
                repairing++;
            } else if (section.done() && section.condition() < 0.999) {
                worn++;
            }
        }
        int[] missing = RoadBuilding.remaining(road).getOrDefault(kingdomId, new int[3]);
        int built = road.units() == 0 ? 1000 : (int) Math.round(1000.0 * road.paid() / road.units());
        return new RoadViews.RoadRow(road.id(), endpointName(server, road.from()), endpointName(server, road.to()), road.roadClass(), road.length(),
                built, (int) Math.round(road.condition() * 1000), road.status(), road.paused(), road.kingdomId().equals(kingdomId),
                String.join(", ", partners), road.mode(), worn, repairing, missing);
    }

    static RoadViews.RiskRow riskRow(MinecraftServer server, Deal deal, UUID kingdomId) {
        double guards = 0;
        boolean guarded = false;
        if (!ArmyWork.isReadOnly(server)) {
            for (ArmySquad squad : ArmyWork.state(server).escortsOf(deal.id())) {
                guards += ArmyWork.power(server, squad);
                guarded = true;
            }
        }
        double chance = RoadCaravans.robberyChance(server, deal, guards);
        int onRoads = 0;
        int minutes = 0;
        Optional<Capital> from = RoadWork.capital(server, deal.seller());
        Optional<Capital> to = RoadWork.capital(server, deal.buyer());
        if (from.isPresent() && to.isPresent() && from.get().dimension().equals(to.get().dimension())) {
            TravelPlan plan = RoadCaravans.plan(server, from.get().dimension(), from.get().x(), from.get().z(), to.get().x(), to.get().z());
            onRoads = plan.length() <= 0 ? 0 : (int) Math.round(1000.0 * plan.onRoads() / plan.length());
            minutes = (int) Math.max(1, Math.round(plan.ticks() / 1200.0));
        }
        UUID partner = deal.seller().equals(kingdomId) ? deal.buyer() : deal.seller();
        Item item = GoodsStacks.item(deal.item());
        return new RoadViews.RiskRow(deal.id(), RoadWork.kingdomName(server, partner).getString(), item == null ? ItemStack.EMPTY : new ItemStack(item),
                deal.seller().equals(kingdomId), (int) Math.round(chance * 1000), onRoads, minutes, guarded);
    }

    // ---------------------------------------------------------------- decisions

    public static void handleTab(ServerPlayer player, RoadPackets.TabChange change) {
        if (!KingdomWorldApi.state(player.server).membership().isMember(player.getUUID(), change.kingdomId())) {
            KingdomActions.reply(player, KingdomActions.Feedback.error(KingdomError.NOT_A_MEMBER));
            return;
        }
        if (change.action() == RoadPackets.TabChange.Action.GATE_HERE) {
            // The gate goes where the player stands, whatever the client says.
            change = new RoadPackets.TabChange(change.kingdomId(), change.action(), change.first(), player.blockPosition().asLong(), "");
        }
        KingdomActions.reply(player, applyTab(player.server, player.getUUID(), change));
        sendTab(player, change.kingdomId());
    }

    private static KingdomActions.Feedback result(Outcome outcome, String key, Object... args) {
        return outcome.ok() ? KingdomActions.Feedback.ok(KEY + key, args) : KingdomActions.Feedback.error(outcome.error());
    }

    /**
     * Applies a decision of the tab.
     *
     * @param actor the player deciding, or {@code null} for an operator
     */
    public static KingdomActions.Feedback applyTab(MinecraftServer server, @Nullable UUID actor, RoadPackets.TabChange change) {
        UUID kingdom = change.kingdomId();
        if (change.action() == RoadPackets.TabChange.Action.PATROL) {
            return patrol(server, actor, kingdom, change.first(), change.second());
        }
        if (!RoadWork.mayManage(server, actor, kingdom)) {
            return KingdomActions.Feedback.error(KingdomError.NO_PERMISSION);
        }
        RoadState roads = RoadWork.state(server);
        return switch (change.action()) {
            case REPAIR -> {
                Optional<Road> road = roads.road(change.first());
                if (road.isEmpty() || !road.get().builders().contains(kingdom)) {
                    yield KingdomActions.Feedback.error(KingdomError.UNKNOWN_ROAD);
                }
                int[] count = new int[1];
                Outcome outcome = RoadWork.change(server, "repair a road", state -> {
                    for (Road.Section section : road.get().sections()) {
                        if (section.builder().equals(kingdom) && (change.second() < 0 || section.index() == change.second())
                                && section.done() && !section.repairing() && section.condition() < 0.999) {
                            section.startRepair();
                            count[0]++;
                        }
                    }
                    state.changed();
                    return count[0] == 0 ? Outcome.fail(KingdomError.NOTHING_TO_REPAIR) : Outcome.DONE;
                });
                yield result(outcome, "repair_ordered", RoadTexts.sections(count[0]));
            }
            case PAUSE -> {
                Optional<Road> road = roads.road(change.first());
                if (road.isEmpty() || !road.get().kingdomId().equals(kingdom)) {
                    yield KingdomActions.Feedback.error(KingdomError.UNKNOWN_ROAD);
                }
                Outcome outcome = RoadWork.change(server, "pause a road", state -> {
                    road.get().setPaused(!road.get().paused());
                    state.changed();
                    return Outcome.DONE;
                });
                yield result(outcome, road.get().paused() ? "paused" : "resumed", road.get().id());
            }
            case CANCEL -> {
                Optional<Road> road = roads.road(change.first());
                if (road.isEmpty() || !road.get().kingdomId().equals(kingdom)) {
                    yield KingdomActions.Feedback.error(KingdomError.UNKNOWN_ROAD);
                }
                Outcome outcome = RoadWork.change(server, "give up a road", state -> state.removeRoad(road.get().id()).isPresent() ? Outcome.DONE
                        : Outcome.fail(KingdomError.UNKNOWN_ROAD));
                if (outcome.ok()) {
                    RoadBuilding.giveBackPools(server, road.get());
                    RoadSync.changed();
                    RoadSpeed.invalidate();
                }
                yield result(outcome, "cancelled", road.get().id());
            }
            case ACCEPT, DECLINE -> answer(server, kingdom, change.first(), change.action() == RoadPackets.TabChange.Action.ACCEPT);
            case RENAME_CITY -> {
                Optional<City> city = roads.city(change.first());
                String name = change.text().strip();
                if (city.isEmpty() || !city.get().kingdomId().equals(kingdom)) {
                    yield KingdomActions.Feedback.error(KingdomError.UNKNOWN_CITY);
                }
                if (name.isEmpty() || name.length() > City.MAX_NAME) {
                    yield KingdomActions.Feedback.error(KingdomError.VALUE_OUT_OF_RANGE);
                }
                Outcome outcome = RoadWork.change(server, "rename a city", state -> {
                    state.putCity(city.get().withName(name));
                    return Outcome.DONE;
                });
                if (outcome.ok()) {
                    RoadSync.changed();
                }
                yield result(outcome, "renamed", name);
            }
            case WALL_PALISADE, WALL_STONE -> {
                // The ring is read over the next ticks; the kingdom hears when the wall is laid out.
                Outcome outcome = CityWalls.order(server, actor, kingdom, change.first(),
                        change.action() == RoadPackets.TabChange.Action.WALL_STONE ? Wall.WallClass.STONE : Wall.WallClass.PALISADE);
                yield outcome.ok() ? KingdomActions.Feedback.ok(KEY + "wall_ordered", CityWalls.place(server, kingdom, change.first())
                        .map(CityWalls.Place::name).orElse("?")) : KingdomActions.Feedback.error(outcome.error());
            }
            case SEND_BUILDER -> {
                io.github.sekerator.kingdomworld.common.construction.RemotePosts.Sending sending =
                        io.github.sekerator.kingdomworld.common.construction.RemotePosts.sendBuilder(server, actor, kingdom, change.first());
                if (!sending.outcome().ok()) {
                    yield KingdomActions.Feedback.error(sending.outcome().error());
                }
                yield sending.ordered() ? KingdomActions.Feedback.ok(KEY + "builder_post_ordered")
                        : KingdomActions.Feedback.ok(KEY + "builder_sent", sending.hired().map(io.github.sekerator.kingdomworld.common.citizen.CitizenTexts::name)
                        .orElse(Component.empty()));
            }
            case GATE_HERE -> {
                Optional<Wall> wall = roads.wallOf(change.first(), kingdom);
                if (wall.isEmpty()) {
                    yield KingdomActions.Feedback.error(KingdomError.NO_WALL);
                }
                net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.of(change.second());
                yield result(CityWalls.gateAt(server, actor, wall.get().id(), at.getX(), at.getZ()), "gate_cut", at.getX(), at.getZ());
            }
            default -> KingdomActions.Feedback.error(KingdomError.VALUE_OUT_OF_RANGE);
        };
    }

    /**
     * The owner of the land answers a request to cross it.
     */
    public static KingdomActions.Feedback answer(MinecraftServer server, UUID kingdom, long crossingId, boolean accept) {
        RoadState.Answer[] answer = new RoadState.Answer[1];
        Outcome outcome = RoadWork.change(server, "answer a request to cross land", state -> {
            answer[0] = state.answer(crossingId, kingdom, accept);
            return answer[0].outcome();
        });
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        Crossing crossing = RoadWork.state(server).crossing(crossingId).orElseThrow();
        RoadWork.notify(server, crossing.requester(), Component.translatable(accept ? ROAD + "crossing_accepted" : ROAD + "crossing_declined",
                RoadWork.kingdomName(server, kingdom), crossing.roadId()).withStyle(accept ? ChatFormatting.GREEN : ChatFormatting.GOLD));
        answer[0].started().ifPresent(road -> RoadWork.notify(server, road.kingdomId(),
                Component.translatable(ROAD + "road_starts", road.id()).withStyle(ChatFormatting.GREEN)));
        answer[0].dropped().ifPresent(road -> RoadPlanning.dropped(server, road));
        RoadSync.changed();
        return KingdomActions.Feedback.ok(KEY + (accept ? "accepted" : "declined"), crossing.roadId());
    }

    /**
     * A squad patrols a road: it walks up and down its way and keeps robbers off it.
     */
    static KingdomActions.Feedback patrol(MinecraftServer server, @Nullable UUID actor, UUID kingdom, long roadId, long squadId) {
        Optional<Road> road = RoadWork.state(server).road(roadId);
        if (road.isEmpty() || !road.get().builders().contains(kingdom) && !road.get().kingdomId().equals(kingdom)) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_ROAD);
        }
        Outcome outcome = ArmyWork.order(server, actor, squadId, Order.patrol(patrolPoints(road.get())));
        return result(outcome, "patrol_ordered", squadId, roadId);
    }

    /**
     * Points a patrol walks along a road: out to the far end and back.
     */
    public static List<WorldPos> patrolPoints(Road road) {
        List<RouteCell> cells = road.cells();
        int forward = Math.min(5, Order.MAX_POINTS);
        List<WorldPos> points = new ArrayList<>();
        for (int i = 0; i < forward; i++) {
            RouteCell cell = cells.get((int) Math.round((cells.size() - 1) * i / (double) (forward - 1)));
            points.add(new WorldPos(road.dimension(), cell.x(), cell.y() + 1, cell.z()));
        }
        for (int i = forward - 2; i >= 1 && points.size() < Order.MAX_POINTS; i--) {
            points.add(points.get(i));
        }
        return points;
    }

    // ---------------------------------------------------------------- planning on the atlas

    public static void handlePlan(ServerPlayer player, RoadPackets.PlanRequest request) {
        if (!KingdomWorldApi.state(player.server).membership().isMember(player.getUUID(), request.kingdomId())) {
            KingdomWorldNetwork.sendToPlayer(player, new RoadPackets.PlanResponse(RoadViews.PlanView.failed(request.kingdomId(),
                    KingdomError.NOT_A_MEMBER.id())));
            return;
        }
        Outcome outcome = RoadPlanning.start(player.serverLevel(), player.getUUID(), request.kingdomId(), request.fromX(), request.fromZ(),
                request.toX(), request.toZ());
        KingdomWorldNetwork.sendToPlayer(player, new RoadPackets.PlanResponse(outcome.ok() ? RoadViews.PlanView.searching(request.kingdomId())
                : RoadViews.PlanView.failed(request.kingdomId(), outcome.error().id())));
    }

    public static void handleConfirm(ServerPlayer player, RoadPackets.PlanConfirm confirm) {
        if (!KingdomWorldApi.state(player.server).membership().isMember(player.getUUID(), confirm.kingdomId())) {
            KingdomActions.reply(player, KingdomActions.Feedback.error(KingdomError.NOT_A_MEMBER));
            return;
        }
        Road[] made = new Road[1];
        Outcome outcome = RoadPlanning.build(player.server, player.getUUID(), confirm.kingdomId(), confirm.planId(), confirm.roadClass(),
                confirm.mode(), made);
        KingdomActions.reply(player, outcome.ok() ? KingdomActions.Feedback.ok(made[0].status() == Road.Status.WAITING_CONSENT
                ? KEY + "planned_waiting" : KEY + "planned", made[0].id()) : KingdomActions.Feedback.error(outcome.error()));
    }

    /**
     * A city founded from the map: the order is taken at once, the town hall set up when its turn comes.
     */
    public static void handleFound(ServerPlayer player, RoadPackets.Found found) {
        Outcome outcome = io.github.sekerator.kingdomworld.common.construction.RemoteHalls.order(player.server, player.getUUID(), found.kingdomId(),
                io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(player.level()), found.chunkX(), found.chunkZ(), found.name(),
                found.withPost());
        KingdomActions.reply(player, outcome.ok() ? KingdomActions.Feedback.ok("kingdomworld.commission.founding")
                : KingdomActions.Feedback.error(outcome.error()));
        if (outcome.ok()) {
            sendTab(player, found.kingdomId());
        }
    }

    /**
     * The pace of a caravan on a road of a class against across country, for texts.
     */
    static double pace() {
        return TradeSettings.rules().caravanBlocksPerMinute();
    }
}
