package io.github.sekerator.kingdomworld.common.construction;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.road.RoadFootprint;
import io.github.sekerator.kingdomworld.common.road.RoadRegistries;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.Commission;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A city founded from the map: a town hall of the storehouses (or one the workshops make for it) is put on a free column of
 * the chunk the king chose, the nearest to its middle, the chunk held loaded meanwhile; the city is founded first and its town
 * hall set in the same tick, so that it is never founded twice, and a town hall that does not stand takes the city back with it
 * and goes back to the storehouses. With a post, a post of builders is ordered for the new city at once.
 */
public final class RemoteHalls {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The item of a town hall. */
    public static final String HALL_ITEM = "kingdomworld:town_hall";

    private RemoteHalls() {
    }

    /**
     * Orders a city founded in a chunk of the land of the kingdom, in the dimension given.
     *
     * @param name the name of the city, or empty for one after the kingdom
     */
    public static Outcome order(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, String dimension, int chunkX, int chunkZ, String name,
                                boolean withPost) {
        Outcome check = RemoteWorks.checkKingdom(server, actor, kingdomId, false);
        if (!check.ok()) {
            return check;
        }
        String chosen = name == null ? "" : name.strip();
        if (chosen.length() > City.MAX_NAME) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        int x = (chunkX << 4) + 8;
        int z = (chunkZ << 4) + 8;
        Outcome place = RoadWork.checkFounding(server, actor, kingdomId, dimension, x, z);
        if (!place.ok()) {
            return place;
        }
        return RemoteWorks.add(server, new Commission(0, kingdomId, Commission.Kind.HALL, "", Materials.NONE, Commission.OUTSIDE, dimension, x, z, 0,
                Construction.Priority.HIGH, actor, Commission.Origin.KING, KingdomWorldApi.gameTime(server), Commission.State.WAITING, 0, "", chosen,
                withPost, 0, 0));
    }

    static RemoteWorks.Job job(Commission commission, ServerLevel level) {
        return new HallJob(commission, level);
    }

    /**
     * A town hall set up in a chunk.
     */
    private static final class HallJob extends RemoteWorks.Job {
        private int ticks;

        HallJob(Commission commission, ServerLevel level) {
            super(commission, level);
        }

        @Override
        void tick(MinecraftServer server) {
            long now = KingdomWorldApi.gameTime(server);
            if (hold == null) {
                if (!RemotePosts.inStore(server, commission.kingdomId(), HALL_ITEM)) {
                    RemotePosts.lacks(server, commission, HALL_ITEM, now);
                    return;
                }
                int minX = commission.x() & ~15;
                int minZ = commission.z() & ~15;
                hold = new ChunkHold(level, ChunkHold.ringed(minX, minZ, minX + 15, minZ + 15));
                ticks = 0;
                return;
            }
            hold.hold();
            if (!hold.ready()) {
                if (++ticks > RemoteWorks.WAIT_TICKS) {
                    RemoteWorks.keep(server, commission.withState(Commission.State.WAITING, "", commission.retryAt()));
                }
                return;
            }
            Outcome again = RoadWork.checkFounding(server, commission.actor(), commission.kingdomId(), commission.dimension(), commission.x(),
                    commission.z());
            if (!again.ok()) {
                refuse(server, again.error());
                return;
            }
            BlockPos spot = spot(server);
            if (spot == null) {
                Commission waiting = commission.withState(Commission.State.NO_PLACE, "no_hall_place", now + RemoteWorks.PLACE_RETRY);
                if (commission.retryAt() == 0) {
                    RemoteWorks.tell(server, waiting, Component.translatable(RemoteWorks.KEY + "no_place", RemoteWorks.what(waiting),
                            RemoteWorks.reasonText(waiting)).withStyle(ChatFormatting.YELLOW), false);
                }
                RemoteWorks.keep(server, waiting);
                return;
            }
            WorldPos near = ConstructionWork.worldPos(level, spot);
            if (!RemoteWorks.takeOne(server, commission.kingdomId(), HALL_ITEM, near)) {
                RemotePosts.lacks(server, commission, HALL_ITEM, now);
                return;
            }
            Outcome founded = RoadWork.foundCity(level, commission.actor(), commission.kingdomId(), spot,
                    commission.name().isEmpty() ? null : commission.name());
            if (!founded.ok()) {
                RemoteWorks.giveBack(server, commission.kingdomId(), RoadRegistries.TOWN_HALL_ITEM.get(), near);
                refuse(server, founded.error());
                return;
            }
            Optional<City> city = RoadWork.state(server).cityAt(commission.dimension(), spot.getX(), spot.getY(), spot.getZ());
            // The city stands before its town hall does, so that setting the block never founds it a second time.
            level.setBlock(spot, RoadRegistries.TOWN_HALL.get().defaultBlockState(), 3);
            if (!level.getBlockState(spot).is(RoadRegistries.TOWN_HALL.get()) || city.isEmpty()) {
                city.ifPresent(found -> RoadWork.state(server).removeCity(found.id()));
                RemoteWorks.giveBack(server, commission.kingdomId(), RoadRegistries.TOWN_HALL_ITEM.get(), near);
                RemoteWorks.keep(server, commission.withState(Commission.State.NO_PLACE, "no_hall_place", now + RemoteWorks.ITEM_RETRY));
                LOGGER.warn("The town hall of {} at {} did not stand: the city is given up again", commission.name(), spot.toShortString());
                return;
            }
            RemoteWorks.drop(server, commission.id());
            LOGGER.info("Kingdom {} founded {} at {} from the map", commission.kingdomId(), city.get().name(), spot.toShortString());
            if (commission.withPost()) {
                RemoteWorks.orderPost(server, commission.actor(), commission.kingdomId(), city.get().id(), Commission.Origin.KING);
            }
        }

        private void refuse(MinecraftServer server, KingdomError error) {
            RemoteWorks.drop(server, commission.id());
            RemoteWorks.tell(server, commission, Component.translatable(RemoteWorks.KEY + "hall_refused", commission.name().isEmpty()
                    ? Component.translatable(RemoteWorks.KEY + "hall_unnamed") : Component.literal(commission.name()),
                    KingdomActions.errorMessage(error)).withStyle(ChatFormatting.YELLOW), false);
        }

        /**
         * A free column of the chunk for the town hall, the nearest to its middle first: its own land, the ground as it grew with
         * room for three blocks over it, no road or wall on it and no construction or building over it.
         */
        @Nullable
        private BlockPos spot(MinecraftServer server) {
            KingdomWorldState state = KingdomWorldApi.state(server);
            int minX = commission.x() & ~15;
            int minZ = commission.z() & ~15;
            Set<Long> footprint = new HashSet<>();
            for (int[] cell : CityPlots.within(RoadFootprint.roadsAndWalls(server, commission.kingdomId()), commission.x(), commission.z(), 9)) {
                footprint.add(BlockPos.asLong(cell[0], 0, cell[1]));
            }
            List<int[]> columns = new ArrayList<>();
            for (int x = minX; x < minX + 16; x++) {
                for (int z = minZ; z < minZ + 16; z++) {
                    columns.add(new int[]{x, z});
                }
            }
            int mx = commission.x();
            int mz = commission.z();
            columns.sort(Comparator.comparingInt((int[] c) -> (c[0] - mx) * (c[0] - mx) + (c[1] - mz) * (c[1] - mz)).thenComparingInt(c -> c[0])
                    .thenComparingInt(c -> c[1]));
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int[] column : columns) {
                int x = column[0];
                int z = column[1];
                if (footprint.contains(BlockPos.asLong(x, 0, z))) {
                    continue;
                }
                int ground = NaturalGround.groundAt(level, x, z);
                BlockPos hall = new BlockPos(x, ground + 1, z);
                if (!CityPlots.ownLand(server, commission.kingdomId(), commission.dimension(),
                        io.github.sekerator.kingdomworld.core.territory.ChunkKey.of(x >> 4, z >> 4))
                        || state.construction().overlapping(commission.dimension(), new Box(x, ground, z, x, ground + 3, z), -1).isPresent()) {
                    continue;
                }
                if (!NaturalGround.column(level, pos, x, z, ground, ground, ground + 3, Blocks.AIR.defaultBlockState(), Long.MIN_VALUE)
                        || !RemotePosts.open(level.getBlockState(hall)) || !RemotePosts.open(level.getBlockState(hall.above()))
                        || !RemotePosts.open(level.getBlockState(hall.above(2)))) {
                    continue;
                }
                return hall;
            }
            return null;
        }
    }
}
