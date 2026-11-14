package io.github.sekerator.kingdomworld.common.road;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadRules;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Cities and roads at work on the server: access to their saved data, the founding of cities by town halls, and once a
 * second the step of everything that moves on the roads (requests that run out, building, wear, caravans, robbers). Every
 * change goes through {@link #change}, which refuses while the roads or the kingdoms are read-only.
 */
public final class RoadWork {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final String KEY = "kingdomworld.road.";
    private static long lastDay = Long.MIN_VALUE;

    private RoadWork() {
    }

    // ---------------------------------------------------------------- access

    public static RoadState state(MinecraftServer server) {
        return RoadSavedData.get(server).state();
    }

    public static boolean isReadOnly(MinecraftServer server) {
        return RoadSavedData.get(server).isReadOnly() || KingdomWorldApi.isReadOnly(server);
    }

    /**
     * Runs a change of the roads, refusing it while the roads or the kingdoms are read-only: roads are paid from the
     * storehouses of the kingdoms.
     */
    public static Outcome change(MinecraftServer server, String what, Function<RoadState, Outcome> change) {
        if (isReadOnly(server)) {
            LOGGER.debug("Refused to {}: road or kingdom data is read-only", what);
            return Outcome.fail(KingdomError.READ_ONLY);
        }
        return change.apply(state(server));
    }

    public static void reset() {
        lastDay = Long.MIN_VALUE;
        RoadPlanning.reset();
        PlannedRoads.reset();
        RoadBuilding.reset();
        RoadCaravans.reset();
        BanditWork.reset();
        RoadSync.reset();
        RoadSpeed.reset();
        CityWalls.reset();
    }

    public static long day(MinecraftServer server) {
        return Math.floorDiv(KingdomWorldApi.gameTime(server), CitizenNeeds.TICKS_PER_DAY);
    }

    static Component kingdomName(MinecraftServer server, UUID id) {
        return KingdomWorldApi.kingdom(server, id).<Component>map(KingdomActions::kingdomName).orElseGet(() -> Component.literal("?"));
    }

    /**
     * Tells the members of a kingdom who run its construction.
     */
    static void notify(MinecraftServer server, UUID kingdomId, Component message) {
        KingdomWorldApi.kingdom(server, kingdomId).ifPresent(kingdom -> KingdomActions.notifyMembers(server, kingdom,
                Permission.MANAGE_CONSTRUCTION, message));
    }

    static boolean mayManage(MinecraftServer server, @Nullable UUID actor, UUID kingdomId) {
        return actor == null || KingdomWorldApi.state(server).membership().hasPermission(actor, kingdomId, Permission.MANAGE_CONSTRUCTION);
    }

    /**
     * The capitals of all kingdoms in a dimension, as x and z.
     */
    static List<int[]> capitals(MinecraftServer server, String dimension) {
        List<int[]> result = new ArrayList<>();
        for (Territory territory : KingdomWorldApi.state(server).territory().territories()) {
            Capital capital = territory.capital();
            if (capital != null && capital.dimension().equals(dimension)) {
                result.add(new int[]{capital.x(), capital.z()});
            }
        }
        return result;
    }

    public static Optional<Capital> capital(MinecraftServer server, UUID kingdomId) {
        return KingdomWorldApi.state(server).territory().territory(kingdomId).map(Territory::capital);
    }

    // ---------------------------------------------------------------- cities

    /**
     * Whether a city of a kingdom may be founded at a place by the data: the right of construction, the land of the kingdom and
     * the distance to the other cities and capitals, as when a town hall is placed there.
     */
    public static Outcome checkFounding(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, String dimension, int x, int z) {
        if (KingdomWorldApi.kingdom(server, kingdomId).isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (!mayManage(server, actor, kingdomId)) {
            return Outcome.fail(KingdomError.NO_PERMISSION);
        }
        if (!kingdomId.equals(KingdomWorldApi.state(server).territory().ownerOf(dimension,
                io.github.sekerator.kingdomworld.core.territory.ChunkKey.of(x >> 4, z >> 4)))) {
            return Outcome.fail(KingdomError.CONSTRUCTION_OUTSIDE_LAND);
        }
        return state(server).checkCity(kingdomId, dimension, x, z, capitals(server, dimension), RoadSettings.rulesFor(server, kingdomId));
    }

    /**
     * Founds a city.
     *
     * @param actor the player founding it, who needs the right to run construction; {@code null} for an operator
     * @param name  its name, or {@code null} for one after the kingdom
     */
    public static Outcome foundCity(ServerLevel level, @Nullable UUID actor, UUID kingdomId, BlockPos pos, @Nullable String name) {
        MinecraftServer server = level.getServer();
        String dimension = Dimensions.id(level);
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdom(server, kingdomId);
        if (kingdom.isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (!mayManage(server, actor, kingdomId)) {
            return Outcome.fail(KingdomError.NO_PERMISSION);
        }
        if (!kingdomId.equals(KingdomWorldApi.ownerAt(level, pos))) {
            return Outcome.fail(KingdomError.CONSTRUCTION_OUTSIDE_LAND);
        }
        RoadRules rules = RoadSettings.rulesFor(server, kingdomId);
        // A city grown by its people beyond the square of its level holds the land around it: no other city is founded there.
        for (CityAreas.Area area : CityAreas.of(server, kingdomId)) {
            if (area.cityId() >= 0 && area.contains(dimension, pos.getX(), pos.getZ()) && area.grown(pos.getX(), pos.getZ())) {
                return Outcome.fail(KingdomError.CITY_TOO_CLOSE);
            }
        }
        City[] made = new City[1];
        Outcome outcome = change(server, "found a city", roads -> {
            Outcome check = roads.checkCity(kingdomId, dimension, pos.getX(), pos.getZ(), capitals(server, dimension), rules);
            if (!check.ok()) {
                return check;
            }
            String chosen = name == null || name.isBlank() ? kingdom.get().name() + " " + (roads.citiesOf(kingdomId).size() + 2) : name.strip();
            if (chosen.length() > City.MAX_NAME) {
                chosen = chosen.substring(0, City.MAX_NAME).strip();
            }
            made[0] = roads.addCity(kingdomId, chosen, dimension, pos.getX(), pos.getY(), pos.getZ(), KingdomWorldApi.gameTime(server));
            return Outcome.DONE;
        });
        if (outcome.ok()) {
            LOGGER.info("City {} of {} founded at {}", made[0].name(), kingdom.get().name(), pos.toShortString());
            notify(server, kingdomId, Component.translatable(KEY + "city_founded", made[0].name(), pos.getX(), pos.getY(), pos.getZ())
                    .withStyle(ChatFormatting.GREEN));
            RoadSync.changed();
        }
        return outcome;
    }

    /**
     * Founds the town of a settlement of a state of the world at its town hall: a state holds many more towns than a kingdom
     * founds, so only the distance between towns is kept, not their number.
     *
     * @return the town, or empty if it stands too close to another town or off the land of its kingdom
     */
    public static Optional<City> foundSettlementCity(ServerLevel level, UUID kingdomId, BlockPos pos, String name) {
        MinecraftServer server = level.getServer();
        String dimension = Dimensions.id(level);
        Optional<City> there = state(server).cityAt(dimension, pos.getX(), pos.getY(), pos.getZ());
        if (there.isPresent()) {
            return there;
        }
        if (KingdomWorldApi.kingdom(server, kingdomId).isEmpty() || !kingdomId.equals(KingdomWorldApi.ownerAt(level, pos))) {
            return Optional.empty();
        }
        RoadRules rules = RoadSettings.rules();
        City[] made = new City[1];
        Outcome outcome = change(server, "found the town of a settlement", roads -> {
            for (City city : roads.cities()) {
                if (city.dimension().equals(dimension) && rules.tooClose((long) city.x() - pos.getX(), (long) city.z() - pos.getZ())) {
                    return Outcome.fail(KingdomError.CITY_TOO_CLOSE);
                }
            }
            for (int[] capital : capitals(server, dimension)) {
                if (rules.tooClose((long) capital[0] - pos.getX(), (long) capital[1] - pos.getZ())) {
                    return Outcome.fail(KingdomError.CITY_TOO_CLOSE);
                }
            }
            String chosen = name.length() > City.MAX_NAME ? name.substring(0, City.MAX_NAME).strip() : name;
            made[0] = roads.addCity(kingdomId, chosen, dimension, pos.getX(), pos.getY(), pos.getZ(), KingdomWorldApi.gameTime(server));
            return Outcome.DONE;
        });
        if (!outcome.ok()) {
            LOGGER.warn("The town of the settlement {} was not founded at {}: {}", name, pos.toShortString(), outcome.error());
            return Optional.empty();
        }
        RoadSync.changed();
        return Optional.of(made[0]);
    }

    static void hallPlaced(ServerLevel level, ServerPlayer player, BlockPos pos, @Nullable String name) {
        if (player instanceof FakePlayer) {
            return;
        }
        MinecraftServer server = level.getServer();
        UUID owner = KingdomWorldApi.ownerAt(level, pos);
        Outcome outcome = owner == null ? Outcome.fail(KingdomError.CONSTRUCTION_OUTSIDE_LAND)
                : foundCity(level, player.getUUID(), owner, pos, name);
        if (!outcome.ok()) {
            // No city: the town hall goes back to the player, so that nothing is lost.
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            ItemStack back = new ItemStack(RoadRegistries.TOWN_HALL_ITEM.get());
            if (name != null) {
                back.setHoverName(Component.literal(name));
            }
            if (!player.getInventory().add(back)) {
                player.drop(back, false);
            }
            player.sendSystemMessage(Component.translatable(KEY + "city_refused", KingdomActions.errorMessage(outcome.error()))
                    .withStyle(ChatFormatting.RED));
        }
    }

    static void hallRemoved(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        if (RoadSavedData.get(server).isReadOnly()) {
            return;
        }
        state(server).cityAt(Dimensions.id(level), pos.getX(), pos.getY(), pos.getZ()).ifPresent(city -> {
            state(server).removeCity(city.id());
            LOGGER.info("City {} given up: its town hall at {} is gone", city.name(), pos.toShortString());
            notify(server, city.kingdomId(), Component.translatable(KEY + "city_lost",
                    io.github.sekerator.kingdomworld.common.npc.NpcTexts.city(city.name())).withStyle(ChatFormatting.GOLD));
            RoadSync.changed();
        });
    }

    static void describeHall(ServerPlayer player, BlockPos pos) {
        Optional<City> city = state(player.server).cityAt(Dimensions.id(player.level()), pos.getX(), pos.getY(), pos.getZ());
        if (city.isEmpty()) {
            player.sendSystemMessage(Component.translatable(KEY + "hall_empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        player.sendSystemMessage(Component.translatable(KEY + "hall_city", city.get().name(), kingdomName(player.server, city.get().kingdomId())));
    }

    // ---------------------------------------------------------------- every second

    /**
     * Moves the roads on. Called once a second.
     */
    public static void tick(MinecraftServer server) {
        if (isReadOnly(server)) {
            RoadSync.tick(server);
            return;
        }
        RoadState roads = state(server);
        KingdomWorldState world = KingdomWorldApi.state(server);
        keepInStep(server, roads, world);
        long now = KingdomWorldApi.gameTime(server);
        for (Road dropped : roads.expire(now)) {
            RoadPlanning.dropped(server, dropped);
        }
        roads.prune(now - 7L * CitizenNeeds.TICKS_PER_DAY);
        RoadBuilding.tick(server);
        CityWalls.tick(server);
        CityWalls.watchEntries(server);
        long day = day(server);
        if (day != lastDay) {
            boolean first = lastDay == Long.MIN_VALUE;
            lastDay = day;
            if (!first) {
                RoadBuilding.daily(server, day);
            }
            BanditWork.daily(server, day);
        }
        RoadCaravans.tick(server);
        BanditWork.tick(server);
        RoadSync.tick(server);
    }

    /**
     * Forgets kingdoms that are gone.
     */
    static void keepInStep(MinecraftServer server, RoadState roads, KingdomWorldState world) {
        Set<UUID> kingdoms = new HashSet<>();
        roads.cities().forEach(city -> kingdoms.add(city.kingdomId()));
        // A kingdom with no city but its capital may still keep townsfolk there.
        roads.charters().forEach(charter -> kingdoms.add(charter.kingdomId()));
        roads.roads().forEach(road -> kingdoms.addAll(road.builders()));
        roads.roads().forEach(road -> kingdoms.add(road.kingdomId()));
        kingdoms.removeIf(id -> world.kingdoms().get(id).isPresent());
        for (UUID gone : kingdoms) {
            for (Road road : roads.forgetKingdom(gone)) {
                RoadBuilding.giveBackPools(server, road);
            }
            RoadSync.changed();
        }
    }
}
