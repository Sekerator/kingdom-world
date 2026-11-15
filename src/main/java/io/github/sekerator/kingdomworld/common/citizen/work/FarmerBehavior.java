package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSettings;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The farmer: works around a composter in the kingdom's land. Near players it harvests ripe crops, replants them
 * and carries the harvest to a storehouse ({@link FarmerWorkGoal}). Far from players it brings in a harvest by
 * formula from the plots counted the last time its farm was seen.
 */
public final class FarmerBehavior implements ProfessionBehavior {
    /** Rows above and below the composter that belong to the farm. */
    static final int FARM_DEPTH = 2;

    @Override
    public boolean isWorkplace(BlockState state) {
        return state.is(Blocks.COMPOSTER);
    }

    @Override
    public Goal createWork(CitizenEntity body) {
        return new FarmerWorkGoal(body);
    }

    @Override
    public void workAbstractly(MinecraftServer server, Citizen citizen, int intervalTicks) {
        if (citizen.workplace() == null) {
            citizen.setWorkStatus(io.github.sekerator.kingdomworld.core.citizen.WorkStatus.NO_WORKPLACE);
            return;
        }
        if (citizen.workCapacity() <= 0 || citizen.workProduct().isEmpty()) {
            PostWork.setStatus(server, citizen, io.github.sekerator.kingdomworld.core.citizen.WorkStatus.NO_CROPS);
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(citizen.workProduct());
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) {
            return;
        }
        var farm = PostWork.postOf(server, citizen);
        double progress = citizen.workProgress() + CitizenNeeds.farmOutput(citizen.workCapacity(), intervalTicks)
                * io.github.sekerator.kingdomworld.common.economy.EconomySettings.workSpeed(citizen.mood());
        int whole = (int) Math.min(Math.floor(progress), 64.0 * 9);
        List<ItemStack> harvest = PostWork.stacks(Map.of(citizen.workProduct(), whole));
        if (farm.isPresent() ? !PostWork.fits(server, farm.get(), harvest)
                : !KingdomStorage.fitsFor(server, citizen.kingdomId(), citizen.professionId(), harvest, citizen.workplace())) {
            // Nowhere to bring all of the harvest: it waits in the fields rather than rot there, and the farmer says why.
            PostWork.setStatus(server, citizen, KingdomStorage.hasStorehouseFor(server, citizen.kingdomId(), citizen.professionId())
                    ? io.github.sekerator.kingdomworld.core.citizen.WorkStatus.STORAGE_FULL
                    : io.github.sekerator.kingdomworld.core.citizen.WorkStatus.NO_STOREHOUSE);
            return;
        }
        citizen.setWorkProgress(progress - whole);
        int left = whole;
        var post = farm;
        while (left > 0) {
            int count = Math.min(left, item.getMaxStackSize(new ItemStack(item)));
            // The room was checked above: everything goes in.
            if (post.isPresent()) {
                PostWork.deliver(server, post.get(), new ItemStack(item, count));
            } else {
                KingdomStorage.insertFor(server, citizen.kingdomId(), citizen.professionId(), new ItemStack(item, count), citizen.workplace());
            }
            left -= count;
        }
        if (whole > 0) {
            if (post.isPresent()) {
                PostWork.record(server, post.get(), citizen.workProduct(), whole);
            } else {
                io.github.sekerator.kingdomworld.common.api.KingdomWorldApi.state(server).citizens().ledger()
                        .record(citizen.kingdomId(), PostWork.gameDay(server), citizen.professionId(), citizen.workProduct(), whole);
            }
        }
        citizen.addExperience(whole);
        PostWork.setStatus(server, citizen, io.github.sekerator.kingdomworld.core.citizen.WorkStatus.WORKING);
    }

    @Override
    public void survey(ServerLevel level, Citizen citizen) {
        WorldPos workplace = citizen.workplace();
        if (workplace == null || !workplace.dimension().equals(Dimensions.id(level))) {
            return;
        }
        Survey survey = survey(level, citizen.kingdomId(), new BlockPos(workplace.x(), workplace.y(), workplace.z()), radiusAt(level, workplace));
        if (survey.plots() > 0) {
            citizen.rememberWork(survey.plots(), survey.product());
            // The townsfolk of the post farm by what its named farmer saw.
            PostWork.post(level.getServer(), workplace).ifPresent(post -> post.rememberFarm(survey.plots(), survey.product()));
        }
    }

    /**
     * The farmer looks at its new farm over the next ticks, a few columns a tick, rather than all of it in the tick it got the post.
     */
    @Override
    public void surveySoon(ServerLevel level, Citizen citizen) {
        WorldPos workplace = citizen.workplace();
        if (workplace == null || !workplace.dimension().equals(Dimensions.id(level))) {
            return;
        }
        Looks.request(level, citizen.kingdomId(), workplace, radiusAt(level, workplace), false).citizens.add(citizen.id());
    }

    private static int radiusAt(ServerLevel level, WorldPos workplace) {
        return PostWork.post(level.getServer(), workplace).map(post -> post.radius()).orElse(CitizenSettings.rules().farmRadius());
    }

    /**
     * Looks at the fields of a farm for its townsfolk over the next ticks, a few columns a tick, while its place is loaded; nothing
     * happens while it is not.
     */
    public static void surveyPost(net.minecraft.server.MinecraftServer server, io.github.sekerator.kingdomworld.core.citizen.WorkPost post) {
        WorldPos at = post.pos();
        ServerLevel level = PostWork.level(server, at);
        if (level == null || !level.isLoaded(new BlockPos(at.x(), at.y(), at.z()))) {
            return;
        }
        Looks.request(level, post.kingdomId(), at, post.radius(), true);
    }

    /**
     * Looks at the fields of a farm for its townsfolk at once, while its place is loaded; nothing happens while it is not.
     */
    public static void surveyPostNow(net.minecraft.server.MinecraftServer server, io.github.sekerator.kingdomworld.core.citizen.WorkPost post) {
        WorldPos at = post.pos();
        ServerLevel level = PostWork.level(server, at);
        BlockPos center = new BlockPos(at.x(), at.y(), at.z());
        if (level == null || !level.isLoaded(center)) {
            return;
        }
        Survey survey = survey(level, post.kingdomId(), center, post.radius());
        post.rememberFarm(survey.plots(), survey.product());
    }

    /**
     * What a farm looks like: how many crops grow there and what they mostly give.
     *
     * @param product item id of the main product, empty if unknown
     */
    record Survey(int plots, String product) {
    }

    /**
     * Counts the crops around a composter on the kingdom's land and finds out what they give, at once.
     */
    static Survey survey(ServerLevel level, UUID kingdomId, BlockPos center, int radius) {
        Look look = new Look(level, kingdomId, center, radius);
        look.step(Integer.MAX_VALUE);
        return look.result();
    }

    /**
     * A look over the crops around a composter on the kingdom's land, column after column in the same order whether it is made
     * at once or a few columns at a time.
     */
    static final class Look {
        private final ServerLevel level;
        private final UUID kingdomId;
        private final BlockPos center;
        private final int radius;
        private final int side;
        private final String dimension;
        private long column;
        private int plots;
        private final Map<Item, Integer> products = new HashMap<>();

        Look(ServerLevel level, UUID kingdomId, BlockPos center, int radius) {
            this.level = level;
            this.kingdomId = kingdomId;
            this.center = center.immutable();
            this.radius = Math.max(0, radius);
            this.side = 2 * this.radius + 1;
            this.dimension = Dimensions.id(level);
        }

        boolean done() {
            return column >= (long) side * side;
        }

        /**
         * Looks at up to {@code columns} more columns; the land and the chunks are looked up anew in every call.
         *
         * @return the columns looked at
         */
        int step(int columns) {
            var territory = KingdomWorldApi.state(level.getServer()).territory();
            // The owner, the chunk and whether the sections of the rows of the farm may hold crops at all are looked up once a
            // chunk, not once a column or a block.
            Map<Long, Boolean> owned = new HashMap<>();
            Map<Long, net.minecraft.world.level.chunk.LevelChunk> chunks = new HashMap<>();
            Map<Long, boolean[]> rows = new HashMap<>();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            long end = Math.min((long) side * side, column + Math.max(0, columns));
            int looked = 0;
            while (column < end) {
                int dx = (int) (column / side) - radius;
                int dz = (int) (column % side) - radius;
                column++;
                looked++;
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                long key = ChunkKey.of(x >> 4, z >> 4);
                if (!owned.computeIfAbsent(key, chunk -> kingdomId.equals(territory.ownerOf(dimension, chunk)))) {
                    continue;
                }
                net.minecraft.world.level.chunk.LevelChunk chunk = chunks.computeIfAbsent(key,
                        k -> level.getChunkSource().getChunkNow(x >> 4, z >> 4));
                boolean[] mayHold = chunk == null ? null : rows.computeIfAbsent(key, k -> cropRows(chunk));
                for (int dy = -FARM_DEPTH; dy <= FARM_DEPTH; dy++) {
                    pos.set(x, center.getY() + dy, z);
                    BlockState state;
                    if (chunk != null && !level.isOutsideBuildHeight(pos)) {
                        if (!mayHold[dy + FARM_DEPTH]) {
                            continue;
                        }
                        state = chunk.getBlockState(pos);
                    } else {
                        // A chunk the game holds a ticket for but does not have whole yet, or a block beyond the height of the
                        // world: as the level answers it.
                        if (!level.isLoaded(pos)) {
                            continue;
                        }
                        state = level.getBlockState(pos);
                    }
                    if (state.isAir() || !FarmCrops.isCrop(state)) {
                        continue;
                    }
                    if (state.getBlock() instanceof net.minecraft.world.level.block.SugarCaneBlock
                            && level.getBlockState(pos.below()).is(state.getBlock())) {
                        continue;
                    }
                    plots++;
                    BlockState fruit = FarmCrops.fruitOf(state);
                    BlockState ripe = fruit != null ? fruit
                            : state.getBlock() instanceof CropBlock crop ? crop.getStateForAge(crop.getMaxAge()) : state;
                    // A sample of the crops is enough to know what the farm gives.
                    if (plots <= 16) {
                        List<ItemStack> drops = FarmCrops.drops(level, pos.immutable(), ripe, null);
                        Item product = FarmCrops.product(drops, ripe, level, pos.immutable());
                        if (product != null) {
                            products.merge(product, 1, Integer::sum);
                        }
                    }
                }
            }
            return looked;
        }

        /**
         * Whether each row of the farm may hold crops in a chunk: a section whose palette holds no crop has none anywhere, and
         * its blocks are not read one by one.
         */
        private boolean[] cropRows(net.minecraft.world.level.chunk.LevelChunk chunk) {
            boolean[] result = new boolean[2 * FARM_DEPTH + 1];
            for (int dy = -FARM_DEPTH; dy <= FARM_DEPTH; dy++) {
                int y = center.getY() + dy;
                if (level.isOutsideBuildHeight(y)) {
                    continue;
                }
                net.minecraft.world.level.chunk.LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                result[dy + FARM_DEPTH] = !section.hasOnlyAir() && section.maybeHas(FarmCrops::isCrop);
            }
            return result;
        }

        Survey result() {
            Item best = null;
            int bestCount = 0;
            for (Map.Entry<Item, Integer> entry : products.entrySet()) {
                if (entry.getValue() > bestCount) {
                    best = entry.getKey();
                    bestCount = entry.getValue();
                }
            }
            ResourceLocation id = best == null ? null : ForgeRegistries.ITEMS.getKey(best);
            return new Survey(plots, id == null ? "" : id.toString());
        }
    }

    /**
     * The looks over farms under way, a few columns a tick for all of them: the first one asked for goes first.
     */
    public static final class Looks {
        /** Columns of farms looked at in a tick at most, in all: five blocks each. */
        static final int COLUMNS_PER_TICK = 512;
        /** The looks for farmers who just got their farm, which go first, and those for the townsfolk of farms. */
        private static final Map<WorldPos, Pending> FARMERS = new java.util.LinkedHashMap<>();
        private static final Map<WorldPos, Pending> POSTS = new java.util.LinkedHashMap<>();

        private Looks() {
        }

        private static final class Pending {
            final Look look;
            /** Whether the post remembers what was seen, crops or none, as for its townsfolk. */
            boolean forPost;
            /** The farmers who remember what was seen, if they saw crops. */
            final java.util.Set<UUID> citizens = new java.util.LinkedHashSet<>();

            Pending(Look look) {
                this.look = look;
            }
        }

        static Pending request(ServerLevel level, UUID kingdomId, WorldPos at, int radius, boolean forPost) {
            Pending pending = FARMERS.get(at);
            if (pending == null) {
                pending = POSTS.get(at);
                if (pending == null) {
                    pending = new Pending(new Look(level, kingdomId, new BlockPos(at.x(), at.y(), at.z()), radius));
                    (forPost ? POSTS : FARMERS).put(at, pending);
                } else if (!forPost) {
                    // A farmer waits for its farm: the look goes on among those of the farmers.
                    POSTS.remove(at);
                    FARMERS.put(at, pending);
                }
            }
            pending.forPost |= forPost;
            return pending;
        }

        /**
         * Whether a look over the farm of a post is under way.
         */
        public static boolean pending(WorldPos at) {
            return FARMERS.containsKey(at) || POSTS.containsKey(at);
        }

        /**
         * Takes the looks under way further, {@value #COLUMNS_PER_TICK} columns a tick for all of them.
         */
        public static void tick(net.minecraft.server.MinecraftServer server) {
            int budget = advance(server, FARMERS, COLUMNS_PER_TICK);
            advance(server, POSTS, budget);
        }

        private static int advance(net.minecraft.server.MinecraftServer server, Map<WorldPos, Pending> looks, int budget) {
            while (budget > 0 && !looks.isEmpty()) {
                Map.Entry<WorldPos, Pending> entry = looks.entrySet().iterator().next();
                Pending pending = entry.getValue();
                budget -= pending.look.step(budget);
                if (!pending.look.done()) {
                    break;
                }
                looks.remove(entry.getKey());
                finish(server, entry.getKey(), pending);
            }
            return budget;
        }

        /**
         * Runs every look under way to its end at once; for game tests.
         */
        public static void finishNow(net.minecraft.server.MinecraftServer server) {
            while (!FARMERS.isEmpty() || !POSTS.isEmpty()) {
                tick(server);
            }
        }

        private static void finish(net.minecraft.server.MinecraftServer server, WorldPos at, Pending pending) {
            Survey survey = pending.look.result();
            var post = PostWork.post(server, at);
            if (pending.forPost) {
                post.ifPresent(found -> found.rememberFarm(survey.plots(), survey.product()));
            }
            if (survey.plots() <= 0 || pending.citizens.isEmpty()) {
                return;
            }
            boolean remembered = false;
            for (UUID id : pending.citizens) {
                Citizen citizen = KingdomWorldApi.state(server).citizens().get(id).orElse(null);
                if (citizen != null && at.equals(citizen.workplace())) {
                    citizen.rememberWork(survey.plots(), survey.product());
                    remembered = true;
                }
            }
            if (remembered && !pending.forPost) {
                // The townsfolk of the post farm by what its named farmer saw.
                post.ifPresent(found -> found.rememberFarm(survey.plots(), survey.product()));
            }
        }

        public static void reset() {
            FARMERS.clear();
            POSTS.clear();
        }
    }
}
