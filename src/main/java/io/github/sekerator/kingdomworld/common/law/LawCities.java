package io.github.sekerator.kingdomworld.common.law;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.road.CityEntryEvent;
import io.github.sekerator.kingdomworld.common.road.CityWalls;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.law.CityGates;
import io.github.sekerator.kingdomworld.core.law.LawState;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.CityLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The gates of the cities of the roads in the law. Every time a player comes into a city with a wall, the guards of the gate
 * look at them: through a gate the entry is checked ({@link LawEntry#onEnter}); a wanted player is called out and kept out,
 * a large city asks its fee and puts back outside the gate whoever does not pay. Getting in past the gates (over the wall,
 * through a gap) is a trespass when it avoided a check and somebody saw it. Soldiers of the kingdom guarding near a gate
 * call out themselves; where none stand, the check is made all the same (the window of the gate opens).
 */
public final class LawCities {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** How near a gate a guard stands, in blocks. */
    private static final double GATE_REACH = 16.0;
    /** How far outside its gate a player who may not come in is put, in blocks. */
    private static final int PUSH_OUT = 3;

    private LawCities() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(LawCities::onEntry);
    }

    /**
     * The cities of the roads as the law sees them, for a server.
     */
    static CityGates gates(MinecraftServer server) {
        return (dimension, x, y, z) -> {
            Optional<City> city = RoadWork.state(server).cityAt(dimension, x, y, z);
            return city.map(found -> city(server, found.kingdomId(), found.id(), found.name()));
        };
    }

    static CityGates.City city(MinecraftServer server, UUID kingdomId, long cityId, String name) {
        boolean large = CityWalls.level(server, kingdomId, cityId) == CityLevel.CITY;
        UUID id = UUID.nameUUIDFromBytes((kingdomId + "/" + cityId).getBytes(StandardCharsets.UTF_8));
        return new CityGates.City(id, kingdomId, name, large);
    }

    private static void onEntry(CityEntryEvent event) {
        ServerPlayer player = event.getPlayer();
        MinecraftServer server = player.server;
        if (player instanceof FakePlayer || player.isCreative() || player.isSpectator() || LawWork.isReadOnly(server)) {
            return;
        }
        try {
            enter(player, event.getKingdomId(), event.getCityId(), event.throughGate());
        } catch (RuntimeException e) {
            LOGGER.error("KingdomWorld law could not check the entry of {} into a city", player.getGameProfile().getName(), e);
        }
    }

    /**
     * A player came into a city of a kingdom (0 is its capital), through a gate or past the gates.
     *
     * @return whether the player may stay inside
     */
    public static boolean enter(ServerPlayer player, UUID kingdomId, long cityId, boolean throughGate) {
        MinecraftServer server = player.server;
        List<WorldPos> gates = CityWalls.gates(server, kingdomId, cityId);
        if (gates.isEmpty()) {
            // No wall: nobody stands at a gate, the guards inside do their usual work.
            return true;
        }
        Optional<CityWalls.Place> place = CityWalls.place(server, kingdomId, cityId);
        CityGates.City city = city(server, kingdomId, cityId, place.map(CityWalls.Place::name).orElse(""));
        WorldPos center = place.map(found -> new WorldPos(found.dimension(), found.x(), found.y(), found.z())).orElse(null);
        return enter(player, city, throughGate, gates, center);
    }

    /**
     * A player came into a city with gates, through one of them or past them.
     *
     * @param center the middle of the city, to know which way is outside
     * @return whether the player may stay inside
     */
    public static boolean enter(ServerPlayer player, CityGates.City city, boolean throughGate, List<WorldPos> gates, @Nullable WorldPos center) {
        MinecraftServer server = player.server;
        UUID kingdomId = city.kingdomId();
        if (throughGate) {
            WorldPos gate = nearest(gates, player);
            CitizenEntity guard = gate == null ? null : guardAt(server, kingdomId, gate);
            if (guard != null) {
                guard.getLookControl().setLookAt(player, 30.0f, 30.0f);
            }
            boolean in = LawEntry.onEnter(player, city, gate);
            if (!in) {
                pushOut(player, center, gate);
            }
            return in;
        }
        LawState state = LawWork.state(server);
        if (KingdomWorldApi.state(server).membership().isMember(player.getUUID(), kingdomId)) {
            return true;
        }
        long fee = LawEntry.fee(server, player, city);
        boolean avoided = state.wanted(kingdomId, player.getUUID()).isPresent()
                || fee > 0 && !state.hasPass(kingdomId, player.getUUID(), LawWork.now(server));
        if (avoided) {
            LawEntry.trespass(player, player.blockPosition(), kingdomId);
        }
        return true;
    }

    @Nullable
    private static WorldPos nearest(List<WorldPos> gates, ServerPlayer player) {
        String dimension = Dimensions.id(player.level());
        WorldPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (WorldPos gate : gates) {
            if (!gate.dimension().equals(dimension)) {
                continue;
            }
            double distance = player.distanceToSqr(gate.x() + 0.5, gate.y(), gate.z() + 0.5);
            if (distance < bestDistance) {
                best = gate;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * A soldier of the kingdom guarding near a gate, if one stands there.
     */
    @Nullable
    static CitizenEntity guardAt(MinecraftServer server, UUID kingdomId, WorldPos gate) {
        ServerLevel level = Dimensions.key(gate.dimension()).map(server::getLevel).orElse(null);
        if (level == null) {
            return null;
        }
        for (CitizenEntity body : level.getEntitiesOfClass(CitizenEntity.class, new AABB(gate.x(), gate.y(), gate.z(), gate.x() + 1,
                gate.y() + 1, gate.z() + 1).inflate(GATE_REACH), body -> body.isAlive() && kingdomId.equals(body.kingdomId())
                && LawWork.isSoldier(body))) {
            if (body.citizenId() != null && io.github.sekerator.kingdomworld.common.army.ArmyWork.orderOf(server, body.citizenId()).kind()
                    == Order.Kind.GUARD) {
                return body;
            }
        }
        return null;
    }

    /**
     * Puts a player who may not come in back outside the gate, on the ground.
     */
    static void pushOut(ServerPlayer player, @Nullable WorldPos place, @Nullable WorldPos gate) {
        if (gate == null) {
            // No gate in this world: back to where the player came from, on safe ground.
            Optional<double[]> from = CityWalls.enteredFrom(player.getUUID());
            if (from.isPresent()) {
                BlockPos spot = safeNear(player.serverLevel(), (int) Math.floor(from.get()[0]), (int) Math.floor(from.get()[1]), null, 0);
                if (spot != null) {
                    player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                }
            }
            return;
        }
        double dx = place == null ? player.getX() - (gate.x() + 0.5) : gate.x() - place.x();
        double dz = place == null ? player.getZ() - (gate.z() + 0.5) : gate.z() - place.z();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-3) {
            dx = 1.0;
            dz = 0.0;
            length = 1.0;
        }
        int x = gate.x() + (int) Math.round(dx / length * PUSH_OUT);
        int z = gate.z() + (int) Math.round(dz / length * PUSH_OUT);
        // Outside means farther from the middle than the gate; the nearest safe ground there, else the safe ground nearest the gate.
        double gateDistance = place == null ? -1 : Math.hypot(gate.x() - place.x(), gate.z() - place.z());
        BlockPos spot = safeNear(player.serverLevel(), x, z, place, gateDistance);
        if (spot == null) {
            spot = safeNear(player.serverLevel(), gate.x(), gate.z(), null, 0);
        }
        if (spot != null) {
            player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        }
    }

    /**
     * Safe ground near a column: a solid block under two blocks of air without liquid, above the bottom of the world; the
     * nearest within a few blocks, optionally only farther from the middle than {@code minDistance}.
     */
    @Nullable
    static BlockPos safeNear(ServerLevel level, int x, int z, @Nullable WorldPos middle, double minDistance) {
        for (int radius = 0; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int cx = x + dx;
                    int cz = z + dz;
                    if (middle != null && minDistance > 0 && Math.hypot(cx - middle.x(), cz - middle.z()) <= minDistance) {
                        continue;
                    }
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
                    BlockPos feet = new BlockPos(cx, y, cz);
                    BlockPos ground = feet.below();
                    if (y <= level.getMinBuildHeight() || !level.getFluidState(ground).isEmpty()
                            || !level.getBlockState(ground).isFaceSturdy(level, ground, net.minecraft.core.Direction.UP)
                            || !level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()
                            || !level.getFluidState(feet).isEmpty()) {
                        continue;
                    }
                    return feet;
                }
            }
        }
        return null;
    }
}
