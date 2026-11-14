package io.github.sekerator.kingdomworld.common.construction;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenActions;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSites;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.citizen.work.GatheringSettings;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.road.RoadFootprint;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.Commission;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
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
 * A post of builders set up in a city from afar: a work post of the storehouses (or one the workshops make for it) is put on a
 * free column near the town hall, the place held loaded meanwhile, and a builder is hired for it: a builder without a post or a
 * citizen without work first, the nearest first, and on an order of the king a builder whose post has nothing to build. A
 * worker of another profession is never taken from his work.
 */
public final class RemotePosts {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The item of a work post. */
    public static final String POST_ITEM = "kingdomworld:work_post";
    /** Blocks around the town hall a post is set up within. */
    static final int REACH = 12;

    private RemotePosts() {
    }

    static RemoteWorks.Job job(Commission commission, ServerLevel level) {
        return new PostJob(commission, level);
    }

    /**
     * Sends one builder more to a city: a free seat of a post of builders of the city takes him; a post of the city with all its
     * seats taken gets one more, up to the most a post has; a city without such a post gets one set up from afar.
     *
     * @return what came of it, and the citizen hired if one was
     */
    public static Sending sendBuilder(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, long cityId) {
        Outcome check = RemoteWorks.checkKingdom(server, actor, kingdomId, true);
        if (!check.ok()) {
            return new Sending(check, Optional.empty(), false);
        }
        if (CityPlots.middle(server, kingdomId, cityId).isEmpty()) {
            return new Sending(Outcome.fail(KingdomError.UNKNOWN_CITY), Optional.empty(), false);
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        int maxSeats = GatheringSettings.rules().maxSeats();
        List<WorkPost> posts = new ArrayList<>();
        for (WorkPost post : state.citizens().posts().postsOf(kingdomId, Professions.BUILDER.id())) {
            if (ConstructionWork.cityOf(server, kingdomId, post.pos()) == cityId) {
                posts.add(post);
            }
        }
        posts.sort(Comparator.comparingInt(WorkPost::freeSeats).reversed().thenComparingInt(WorkPost::seats));
        for (WorkPost post : posts) {
            if (post.freeSeats() <= 0 && post.seats() >= maxSeats) {
                continue;
            }
            WorldPos at = post.pos();
            if (post.freeSeats() <= 0) {
                Outcome seats = KingdomWorldApi.change(server, "add a seat to a post of builders",
                        s -> s.setPostSeats(actor, at, post.seats() + 1, maxSeats));
                if (!seats.ok()) {
                    continue;
                }
            }
            Optional<Citizen> hired = hireOne(server, actor, Commission.Origin.KING, at);
            return new Sending(hired.isPresent() ? Outcome.DONE : Outcome.fail(KingdomError.NO_UNEMPLOYED), hired, false);
        }
        Outcome ordered = RemoteWorks.orderPost(server, actor, kingdomId, cityId, Commission.Origin.KING);
        return new Sending(ordered, Optional.empty(), ordered.ok());
    }

    /**
     * What sending a builder came to.
     *
     * @param ordered whether a post is set up from afar for him
     */
    public record Sending(Outcome outcome, Optional<Citizen> hired, boolean ordered) {
    }

    /**
     * Hires one builder for a post: {@link KingdomWorldState#hire} as a player would, then, on an order of the king, a builder
     * whose post has nothing to build.
     */
    static Optional<Citizen> hireOne(MinecraftServer server, @Nullable UUID actor, Commission.Origin origin, WorldPos at) {
        KingdomWorldState.Hiring[] hiring = new KingdomWorldState.Hiring[1];
        Outcome hired = KingdomWorldApi.change(server, "hire a builder for a post set up from afar", s -> {
            hiring[0] = s.hire(actor, at);
            return hiring[0].outcome();
        });
        if (hired.ok() && hiring[0] != null && hiring[0].citizen().isPresent()) {
            Citizen citizen = hiring[0].citizen().get();
            CitizenActions.afterProfessionChange(server, citizen);
            return Optional.of(citizen);
        }
        if (origin != Commission.Origin.KING) {
            // A governor never takes a builder from another city.
            return Optional.empty();
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        Optional<WorkPost> target = state.citizens().posts().get(at);
        if (target.isEmpty()) {
            return Optional.empty();
        }
        for (WorkPost post : state.citizens().posts().postsOf(target.get().kingdomId(), Professions.BUILDER.id())) {
            if (post.pos().equals(at) || post.workers().isEmpty() || !ConstructionWork.candidates(server, post).isEmpty()) {
                continue;
            }
            for (UUID worker : List.copyOf(post.workers())) {
                Outcome moved = KingdomWorldApi.change(server, "send an idle builder to a post set up from afar", s -> s.assign(actor, at, worker));
                if (moved.ok()) {
                    Optional<Citizen> citizen = state.citizens().get(worker);
                    citizen.ifPresent(found -> CitizenActions.afterProfessionChange(server, found));
                    return citizen;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * A post of builders set up near the town hall of a city.
     */
    private static final class PostJob extends RemoteWorks.Job {
        private BlockPos hall;
        private int ticks;

        PostJob(Commission commission, ServerLevel level) {
            super(commission, level);
        }

        @Override
        void tick(MinecraftServer server) {
            if (hold == null) {
                Optional<BlockPos> middle = CityPlots.middle(server, commission.kingdomId(), commission.cityId());
                if (middle.isEmpty()) {
                    RemoteWorks.drop(server, commission.id());
                    return;
                }
                hall = middle.get();
                // The three by three chunks around the town hall.
                List<Long> chunks = new ArrayList<>();
                for (int cx = (hall.getX() >> 4) - 1; cx <= (hall.getX() >> 4) + 1; cx++) {
                    for (int cz = (hall.getZ() >> 4) - 1; cz <= (hall.getZ() >> 4) + 1; cz++) {
                        chunks.add(io.github.sekerator.kingdomworld.core.territory.ChunkKey.of(cx, cz));
                    }
                }
                hold = new ChunkHold(level, chunks);
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
            long now = KingdomWorldApi.gameTime(server);
            WorldPos near = ConstructionWork.worldPos(level, hall);
            if (!inStore(server, commission.kingdomId(), POST_ITEM)) {
                lacks(server, commission, POST_ITEM, now);
                return;
            }
            BlockPos spot = spot(server);
            if (spot == null) {
                Commission waiting = commission.withState(Commission.State.NO_PLACE, "no_post_place", now + RemoteWorks.PLACE_RETRY);
                if (commission.retryAt() == 0) {
                    RemoteWorks.tell(server, waiting, Component.translatable(RemoteWorks.KEY + "no_place", RemoteWorks.what(waiting),
                            RemoteWorks.reasonText(waiting)).withStyle(ChatFormatting.YELLOW), false);
                }
                RemoteWorks.keep(server, waiting);
                return;
            }
            if (!RemoteWorks.takeOne(server, commission.kingdomId(), POST_ITEM, near)) {
                lacks(server, commission, POST_ITEM, now);
                return;
            }
            level.setBlockAndUpdate(spot, ModRegistries.WORK_POST.get().defaultBlockState());
            LevelChunk chunk = level.getChunkSource().getChunkNow(spot.getX() >> 4, spot.getZ() >> 4);
            if (chunk != null) {
                CitizenSites.scanChunk(level, chunk);
            }
            WorldPos at = ConstructionWork.worldPos(level, spot);
            KingdomWorldState state = KingdomWorldApi.state(server);
            if (state.citizens().posts().get(at).isEmpty()) {
                // The post did not become known: it goes back to the storehouses and the order tries again later.
                level.setBlockAndUpdate(spot, Blocks.AIR.defaultBlockState());
                RemoteWorks.giveBack(server, commission.kingdomId(), ModRegistries.WORK_POST_ITEM.get(), near);
                RemoteWorks.keep(server, commission.withState(Commission.State.NO_PLACE, "no_post_place", now + RemoteWorks.ITEM_RETRY));
                return;
            }
            UUID actor = commission.actor();
            int maxSeats = GatheringSettings.rules().maxSeats();
            KingdomWorldApi.change(server, "set up a post of builders from afar", s -> {
                Outcome profession = s.setPostProfession(actor, at, Professions.BUILDER.id());
                if (!profession.ok() && profession.error() != KingdomError.UNCHANGED) {
                    return profession;
                }
                return s.setPostSeats(actor, at, 1, maxSeats);
            });
            Optional<Citizen> hired = hireOne(server, actor, commission.origin(), at);
            RemoteWorks.drop(server, commission.id());
            String where = spot.getX() + " " + spot.getY() + " " + spot.getZ();
            Component city = RemoteWorks.cityName(server, commission);
            RemoteWorks.tell(server, commission, hired.isPresent()
                    ? Component.translatable(RemoteWorks.KEY + "post_hired", city, where, CitizenTexts.name(hired.get())).withStyle(ChatFormatting.GREEN)
                    : Component.translatable(RemoteWorks.KEY + "post_set", city, where).withStyle(ChatFormatting.GREEN), true);
            LOGGER.info("Kingdom {} set up a post of builders at {} from afar{}", commission.kingdomId(), spot.toShortString(),
                    hired.isPresent() ? "" : " with nobody to hire yet");
        }

        /**
         * A free column for the post near the town hall, the nearest first: its own land, the ground as it grew with room for a
         * citizen over it, no road or wall on it and no construction or building over it.
         */
        @Nullable
        private BlockPos spot(MinecraftServer server) {
            KingdomWorldState state = KingdomWorldApi.state(server);
            Set<Long> footprint = new HashSet<>();
            for (int[] cell : CityPlots.within(RoadFootprint.roadsAndWalls(server, commission.kingdomId()), hall.getX(), hall.getZ(), REACH + 1)) {
                footprint.add(BlockPos.asLong(cell[0], 0, cell[1]));
            }
            List<int[]> columns = new ArrayList<>();
            for (int dx = -REACH; dx <= REACH; dx++) {
                for (int dz = -REACH; dz <= REACH; dz++) {
                    if (dx * dx + dz * dz <= REACH * REACH && Math.max(Math.abs(dx), Math.abs(dz)) >= 2) {
                        columns.add(new int[]{dx, dz});
                    }
                }
            }
            columns.sort(Comparator.comparingInt((int[] c) -> c[0] * c[0] + c[1] * c[1]).thenComparingInt(c -> c[0]).thenComparingInt(c -> c[1]));
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int[] column : columns) {
                int x = hall.getX() + column[0];
                int z = hall.getZ() + column[1];
                if (footprint.contains(BlockPos.asLong(x, 0, z)) || !ChunkHold.loaded(level, x >> 4, z >> 4)) {
                    continue;
                }
                int ground = NaturalGround.groundAt(level, x, z);
                BlockPos post = new BlockPos(x, ground + 1, z);
                if (!CityPlots.ownLand(server, commission.kingdomId(), commission.dimension(),
                        io.github.sekerator.kingdomworld.core.territory.ChunkKey.of(x >> 4, z >> 4))
                        || state.construction().overlapping(commission.dimension(), new Box(x, ground, z, x, ground + 2, z), -1).isPresent()) {
                    continue;
                }
                if (!NaturalGround.column(level, pos, x, z, ground, ground, ground + 2, Blocks.AIR.defaultBlockState(), Long.MIN_VALUE)
                        || !open(level.getBlockState(post)) || !open(level.getBlockState(post.above()))) {
                    continue;
                }
                return post;
            }
            return null;
        }
    }

    /**
     * Whether a block leaves room for a post or a citizen: air, or a plant that gives way.
     */
    static boolean open(BlockState state) {
        return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    /**
     * Whether the storehouses of a kingdom hold an item others did not set aside.
     */
    static boolean inStore(MinecraftServer server, UUID kingdomId, String item) {
        return io.github.sekerator.kingdomworld.common.storage.KingdomStorage.plan(server, kingdomId, java.util.Map.of(item, 1),
                id -> io.github.sekerator.kingdomworld.common.production.Production.reservations().reserved(kingdomId, id, null), null) != null;
    }

    /**
     * An order that lacks an item waits for it: the workshops are asked for one, or, if they do not make it, the kingdom is told
     * to put one into the storehouses.
     */
    static void lacks(MinecraftServer server, Commission commission, String item, long now) {
        long order = RemoteWorks.orderItem(server, commission, item);
        String why = (order > 0 ? "item|" : "store|") + item;
        Commission lacking = commission.withState(Commission.State.LACKS_ITEM, why, now + RemoteWorks.ITEM_RETRY).withOrder(order);
        if (commission.order() == 0 && commission.retryAt() == 0) {
            RemoteWorks.tell(server, lacking, Component.translatable(RemoteWorks.KEY + "lacks", RemoteWorks.what(lacking),
                    RemoteWorks.reasonText(lacking)).withStyle(ChatFormatting.YELLOW), false);
        }
        RemoteWorks.keep(server, lacking);
    }
}
