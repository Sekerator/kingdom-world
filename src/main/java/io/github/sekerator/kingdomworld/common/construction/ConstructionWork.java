package io.github.sekerator.kingdomworld.common.construction;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.production.Gatherers;
import io.github.sekerator.kingdomworld.common.production.Production;
import io.github.sekerator.kingdomworld.common.production.ProductionSettings;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.BlockCost;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.BuildPlan;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.ConstructionRules;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.construction.PlacementOrder;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.production.Order;
import io.github.sekerator.kingdomworld.core.production.Reservations;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Construction in the world: laying out a construction after looking at what stands on the place, doing its steps (breaking
 * what is in the way, its drops to the storehouses; placing blocks with the hooks of a placement by a player), paying for
 * blocks with the materials of the site and the storehouses, placing the blocks paid for far from players once their place
 * is loaded, finishing it into the register of buildings, and keeping its materials set aside, ordered and counted.
 *
 * <p>Nothing is ever made from nothing: every block placed was paid for once, every block broken gives exactly its drops,
 * and the contents of containers never move. Server thread only.
 */
public final class ConstructionWork {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "kingdomworld.construction.";
    /** Blocks paid for that are placed per second once their place is loaded. */
    private static final int APPLY_PER_SECOND = 512;
    /** Template positions whose state is compared per second for the integrity of buildings. */
    private static final int INTEGRITY_PER_SECOND = 4_096;
    /** Ticks without a builder after which a construction says it has none. */
    private static final long BUILDER_TIMEOUT = 2_400;
    private static final GameProfile PLACER = new GameProfile(UUID.nameUUIDFromBytes("kingdomworld:builder".getBytes(StandardCharsets.UTF_8)),
            "[KingdomWorld]");

    /** Plans made for constructions, by number. */
    private static final Map<Long, BuildPlan> PLANS = new HashMap<>();
    /** Steps builders near players are working on, by construction. */
    private static final Map<Long, Map<Integer, UUID>> CLAIMS = new HashMap<>();
    /** Steps put off until a tick, by construction: out of reach, or a container in the way. */
    private static final Map<Long, Map<Integer, Long>> DEFERRED = new HashMap<>();
    /** When each builder last worked at a construction. */
    private static final Map<Long, Map<UUID, Long>> WORKERS = new HashMap<>();
    /** Progress of the integrity check of a building: next block, blocks in place, blocks counted. */
    private static final Map<Long, int[]> INTEGRITY = new HashMap<>();
    /** Blocks found missing or changed so far by the integrity check of a building. */
    private static final Map<Long, java.util.BitSet> MISSING = new HashMap<>();
    private static long integrityTurn;

    private ConstructionWork() {
    }

    public static void reset() {
        PLANS.clear();
        CITY_OF.clear();
        Carts.reset();
        RemoteCrews.reset();
        Earthworks.reset();
        CLAIMS.clear();
        DEFERRED.clear();
        WORKERS.clear();
        INTEGRITY.clear();
        MISSING.clear();
        KEYS.clear();
        BuildingWork.reset();
        BuildingEffects.reset();
        CustomTemplates.reset();
        Scaffolds.reset();
        integrityTurn = 0;
    }

    // ---------------------------------------------------------------- lookups

    /**
     * The template of a construction, built of the construction's materials.
     */
    public static Optional<BuildingTemplate> template(Construction construction) {
        if (construction.kind() == Construction.Kind.EARTHWORK) {
            // The blocks of earthworks come from the land they were laid out on, kept with the construction.
            return Earthworks.template(construction);
        }
        return BuildingTemplates.get(construction.templateId()).map(template -> TemplateMaterials.apply(template, construction.materials()));
    }

    /**
     * The template of a building of the register, built of the building's materials.
     */
    public static Optional<BuildingTemplate> template(Building building) {
        return BuildingTemplates.get(building.templateId()).map(template -> TemplateMaterials.apply(template, building.materials()));
    }

    @Nullable
    public static ServerLevel level(MinecraftServer server, String dimension) {
        return Dimensions.key(dimension).map(server::getLevel).orElse(null);
    }

    public static BlockPos blockPos(WorldPos pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }

    public static WorldPos worldPos(ServerLevel level, BlockPos pos) {
        return new WorldPos(Dimensions.id(level), pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * The key under which a construction sets materials aside in the storehouses.
     */
    public static UUID holdKey(Construction construction) {
        return UUID.nameUUIDFromBytes(("kingdomworld:construction:" + construction.id()).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The plan of a construction, or empty if its template is gone or changed (the status says so).
     */
    public static Optional<BuildPlan> plan(Construction construction) {
        if (construction.kind() == Construction.Kind.EARTHWORK) {
            return earthworkPlan(construction);
        }
        BuildPlan cached = PLANS.get(construction.id());
        Optional<BuildingTemplate> template = template(construction);
        if (template.isEmpty()) {
            construction.setStatus(Construction.Status.TEMPLATE_MISSING);
            return Optional.empty();
        }
        if (template.get().fingerprint() != construction.fingerprint()) {
            construction.setStatus(Construction.Status.TEMPLATE_CHANGED);
            return Optional.empty();
        }
        if (construction.building() > 0) {
            MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            Optional<Building> building = server == null ? Optional.empty()
                    : KingdomWorldApi.state(server).construction().building(construction.building());
            if (building.isEmpty()) {
                construction.setStatus(Construction.Status.BUILDING_GONE);
                return Optional.empty();
            }
        }
        if (cached != null && cached.size() == construction.steps()) {
            return Optional.of(cached);
        }
        BuildPlan plan = plan(template.get(), blockPos(construction.anchor()), construction.placement(), construction.clear(),
                construction.skip(), construction.kind());
        if (plan.size() != construction.steps()) {
            construction.setStatus(Construction.Status.TEMPLATE_CHANGED);
            return Optional.empty();
        }
        PLANS.put(construction.id(), plan);
        return Optional.of(plan);
    }

    /**
     * The plan of earthworks, made from the land kept with them; empty while it is being made after a restart.
     */
    private static Optional<BuildPlan> earthworkPlan(Construction construction) {
        if (construction.earthwork() == null) {
            construction.setStatus(Construction.Status.TEMPLATE_MISSING);
            return Optional.empty();
        }
        Optional<BuildPlan> plan = Earthworks.plan(construction);
        if (plan.isPresent() && plan.get().size() != construction.steps()) {
            construction.setStatus(Construction.Status.TEMPLATE_CHANGED);
            return Optional.empty();
        }
        return plan;
    }

    /**
     * What a construction still needs brought; earthworks count it from their tally rather than from every step.
     */
    public static Map<String, Integer> needed(Construction construction, BuildPlan plan) {
        return construction.kind() == Construction.Kind.EARTHWORK ? Earthworks.needed(construction, plan) : plan.needed(construction);
    }

    /**
     * Whether every step of a construction is done in the world.
     */
    public static boolean finished(Construction construction, BuildPlan plan) {
        return construction.kind() == Construction.Kind.EARTHWORK ? Earthworks.finished(construction, plan) : plan.finished(construction);
    }

    /**
     * A step is done: earthworks count a block of their fill in their tally.
     */
    private static void done(Construction construction, int index) {
        construction.markDone(index);
        if (construction.kind() == Construction.Kind.EARTHWORK) {
            Earthworks.placed(construction, index);
        }
    }

    /**
     * The plan of a template laid at a place, with the positions to clear.
     */
    public static BuildPlan plan(BuildingTemplate template, BlockPos anchor, Placement placement, List<int[]> clear) {
        return plan(template, anchor, placement, clear, new java.util.BitSet(), Construction.Kind.BUILD);
    }

    /**
     * The plan of any kind of construction: a demolition takes down the positions given, the others clear them and place
     * the template blocks not skipped.
     */
    public static BuildPlan plan(BuildingTemplate template, BlockPos anchor, Placement placement, List<int[]> clear, java.util.BitSet skip,
                                 Construction.Kind kind) {
        if (kind == Construction.Kind.DEMOLISH) {
            return BuildingWork.demolitionPlan(template, anchor, placement, clear);
        }
        List<PlacementOrder.Block> blocks = new ArrayList<>();
        List<Map<String, Integer>> costs = new ArrayList<>(template.blocks().size());
        for (int i = 0; i < template.blocks().size(); i++) {
            BuildingTemplate.Block block = template.blocks().get(i);
            BlockCost cost = template.costs().get(i);
            costs.add(cost.asMap());
            if (cost.placed() && !skip.get(i)) {
                BlockPos at = template.place(block.pos(), anchor, placement);
                blocks.add(new PlacementOrder.Block(i, at.getX(), at.getY(), at.getZ(), TemplateBlocks.phase(block.state())));
            }
        }
        return BuildPlan.of(clear, blocks, costs);
    }

    /**
     * The state a template block takes in the world.
     */
    public static BlockState target(BuildingTemplate template, Construction construction, int block) {
        return TemplateBlocks.transform(template.blocks().get(block).state(), construction.placement());
    }

    // ---------------------------------------------------------------- laying out

    /**
     * What stands on a place where a template would be laid.
     *
     * @param error     why it cannot be laid there, or {@code null}
     * @param conflicts positions that keep it from being laid (protected blocks), at most a few hundred
     * @param clear     positions holding blocks that are in the way and would be cleared
     */
    public record Survey(@Nullable KingdomError error, List<BlockPos> conflicts, List<int[]> clear) {
    }

    /**
     * Looks at the place of a template: inside the world, loaded, and free of protected blocks ({@link #isProtected})
     * that the building would replace; finds the blocks the builders have to clear.
     */
    public static Survey survey(ServerLevel level, BuildingTemplate template, BlockPos anchor, Placement placement) {
        Box box = template.box(anchor, placement);
        if (box.minY() < level.getMinBuildHeight() || box.maxY() >= level.getMaxBuildHeight()) {
            return new Survey(KingdomError.OUTSIDE_WORLD, List.of(), List.of());
        }
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                if (!ChunkHold.loaded(level, cx, cz)) {
                    return new Survey(KingdomError.AREA_NOT_LOADED, List.of(), List.of());
                }
            }
        }
        List<BlockPos> conflicts = new ArrayList<>();
        List<int[]> clear = new ArrayList<>();
        for (BuildingTemplate.Block block : template.blocks()) {
            BlockPos at = template.place(block.pos(), anchor, placement);
            BlockState there = level.getBlockState(at);
            BlockState wanted = TemplateBlocks.transform(block.state(), placement);
            if (TemplateBlocks.sameBuilt(there, wanted) || there.isAir() || Scaffolds.is(there)) {
                // Scaffolds left standing are taken down by themselves, or give way to the block.
                continue;
            }
            if (blocksWork(level, at, there)) {
                if (conflicts.size() < 256) {
                    conflicts.add(at.immutable());
                }
                continue;
            }
            // What stands where the template wants air (or a liquid, which is not built) goes; where it wants a block,
            // grass, snow and water simply give way to it, anything else goes first.
            boolean builds = !TemplateBlocks.isNothing(wanted) && !TemplateBlocks.isLiquid(wanted);
            if (!builds || !there.canBeReplaced()) {
                clear.add(new int[]{at.getX(), at.getY(), at.getZ()});
            }
        }
        return new Survey(conflicts.isEmpty() ? null : KingdomError.PROTECTED_BLOCK, conflicts, clear);
    }

    /**
     * Blocks a construction never breaks: those no player can break (bedrock, an end portal frame, a barrier, a command
     * block), those of the tag {@link TemplateBlocks#UNCLEARABLE} (a spawner, reinforced deepslate and what data packs
     * add) and the blocks of the mod (thrones, banks, storehouses, posts and the like).
     */
    public static boolean isProtected(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getDestroySpeed(level, pos) < 0 || state.is(TemplateBlocks.UNCLEARABLE)) {
            return true;
        }
        if (Scaffolds.is(state)) {
            // The scaffolding of the builders is nothing to keep: it goes when the work is done.
            return false;
        }
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id != null && KingdomWorld.MOD_ID.equals(id.getNamespace());
    }

    /**
     * Whether a block keeps the builders from their work: a protected block ({@link #isProtected}) other than a flag of a
     * kingdom, which they take down without a drop and which comes back where the new template wants it.
     */
    public static boolean blocksWork(BlockGetter level, BlockPos pos, BlockState state) {
        return isProtected(level, pos, state) && !io.github.sekerator.kingdomworld.common.block.FlagBlocks.is(state);
    }

    /**
     * Lays out a construction for a kingdom: the template must be buildable, the place free and on the kingdom's land.
     *
     * @param actor the player laying it, who needs the right to manage construction; {@code null} for an operator
     */
    public static KingdomWorldState.ConstructionChange lay(ServerLevel level, @Nullable UUID actor, UUID kingdomId, BuildingTemplate template,
                                                           BlockPos anchor, Placement placement) {
        return lay(level, actor, kingdomId, template, anchor, placement, io.github.sekerator.kingdomworld.core.construction.Materials.NONE);
    }

    /**
     * Lays out a construction of a template built of other wood and stone; the wood of the place is chosen by the biome.
     */
    public static KingdomWorldState.ConstructionChange lay(ServerLevel level, @Nullable UUID actor, UUID kingdomId, BuildingTemplate base,
                                                           BlockPos anchor, Placement placement,
                                                           io.github.sekerator.kingdomworld.core.construction.Materials chosen) {
        io.github.sekerator.kingdomworld.core.construction.Materials materials = TemplateMaterials.resolve(chosen, level, anchor);
        BuildingTemplate template = TemplateMaterials.apply(base, materials);
        KingdomWorldState.ConstructionChange refused = refusal(level, actor, kingdomId, template, anchor, placement);
        if (refused != null) {
            return refused;
        }
        Survey survey = survey(level, template, anchor, placement);
        if (survey.error() != null) {
            return new KingdomWorldState.ConstructionChange(Outcome.fail(survey.error()), Optional.empty());
        }
        BuildPlan plan = plan(template, anchor, placement, survey.clear());
        return record(level, actor, kingdomId, template, anchor, placement, materials, survey, plan);
    }

    /**
     * Why a construction of a template may not be laid out at a place before its blocks are looked at, or {@code null} if it may.
     */
    @Nullable
    private static KingdomWorldState.ConstructionChange refusal(ServerLevel level, @Nullable UUID actor, UUID kingdomId, BuildingTemplate template,
                                                              BlockPos anchor, Placement placement) {
        MinecraftServer server = level.getServer();
        if (template.owner() != null && !template.owner().equals(kingdomId)) {
            return new KingdomWorldState.ConstructionChange(Outcome.fail(KingdomError.UNKNOWN_TEMPLATE), Optional.empty());
        }
        if (KingdomWorldApi.isReadOnly(server)) {
            return new KingdomWorldState.ConstructionChange(Outcome.fail(KingdomError.READ_ONLY), Optional.empty());
        }
        ConstructionRules rules = ConstructionSettings.rules();
        if (BuildingTemplates.problem(template, rules) != null) {
            return new KingdomWorldState.ConstructionChange(Outcome.fail(KingdomError.TEMPLATE_UNAVAILABLE), Optional.empty());
        }
        // The kingdom, the right and the land first: a place outside the kingdom is refused as such, whatever stands there.
        Outcome allowed = KingdomWorldApi.state(server).checkLay(actor, kingdomId, worldPos(level, anchor).dimension(),
                template.box(anchor, placement), rules);
        return allowed.ok() ? null : new KingdomWorldState.ConstructionChange(allowed, Optional.empty());
    }

    /**
     * Enters a construction looked at and planned into the state of the kingdoms.
     */
    private static KingdomWorldState.ConstructionChange record(ServerLevel level, @Nullable UUID actor, UUID kingdomId, BuildingTemplate template,
                                                             BlockPos anchor, Placement placement,
                                                             io.github.sekerator.kingdomworld.core.construction.Materials materials, Survey survey,
                                                             BuildPlan plan) {
        MinecraftServer server = level.getServer();
        KingdomWorldState.ConstructionChange change = KingdomWorldApi.state(server).layConstruction(actor, kingdomId, template.id().toString(),
                template.fingerprint(), worldPos(level, anchor), placement, template.box(anchor, placement), survey.clear(), plan.size(),
                KingdomWorldApi.gameTime(server), ConstructionSettings.rules(), Construction.Kind.BUILD, 0, materials, new java.util.BitSet());
        change.construction().ifPresent(construction -> {
            PLANS.put(construction.id(), plan);
            refreshReservations(server);
            LOGGER.info("Construction {} of {} laid at {} ({} steps, {} to clear)", construction.id(), template.id(), anchor.toShortString(),
                    plan.size(), plan.clears());
        });
        return change;
    }

    /**
     * A construction laid out over ticks, as {@link #lay} lays it out at once: the parts that cost most for a large building (the
     * plans of the constructions that stand, which the reservations look at; the wood and stone of the place; the blocks of the
     * place; the order of the steps) go on while the time a call is given lasts, and the rest in the next call. A small
     * building is laid out in one call.
     */
    public static final class Laying {
        private final ServerLevel level;
        private final UUID kingdomId;
        private final BuildingTemplate base;
        private final BlockPos anchor;
        private final Placement placement;
        private final io.github.sekerator.kingdomworld.core.construction.Materials chosen;
        private int stage;
        private int warmIndex;
        private io.github.sekerator.kingdomworld.core.construction.Materials materials;
        private BuildingTemplate template;
        private Survey survey;
        private BuildPlan plan;

        public Laying(ServerLevel level, UUID kingdomId, BuildingTemplate base, BlockPos anchor, Placement placement,
                      io.github.sekerator.kingdomworld.core.construction.Materials chosen) {
            this.level = level;
            this.kingdomId = kingdomId;
            this.base = base;
            this.anchor = anchor;
            this.placement = placement;
            this.chosen = chosen;
        }

        /**
         * Goes on with the laying out while the time lasts.
         *
         * @param nanos the time of this call; at least one part is done
         * @return what came of it once the construction is laid out or refused, or empty while parts remain
         */
        public Optional<KingdomWorldState.ConstructionChange> step(long nanos) {
            long started = System.nanoTime();
            do {
                switch (stage) {
                    case 0 -> {
                        List<Construction> all = List.copyOf(KingdomWorldApi.state(level.getServer()).construction().all());
                        while (warmIndex < all.size() && System.nanoTime() - started < nanos) {
                            plan(all.get(warmIndex++));
                        }
                        if (warmIndex >= all.size()) {
                            stage = 1;
                        }
                    }
                    case 1 -> {
                        materials = TemplateMaterials.resolve(chosen, level, anchor);
                        template = TemplateMaterials.apply(base, materials);
                        KingdomWorldState.ConstructionChange refused = refusal(level, null, kingdomId, template, anchor, placement);
                        if (refused != null) {
                            return Optional.of(refused);
                        }
                        stage = 2;
                    }
                    case 2 -> {
                        survey = survey(level, template, anchor, placement);
                        if (survey.error() != null) {
                            return Optional.of(new KingdomWorldState.ConstructionChange(Outcome.fail(survey.error()), Optional.empty()));
                        }
                        stage = 3;
                    }
                    case 3 -> {
                        plan = plan(template, anchor, placement, survey.clear());
                        stage = 4;
                    }
                    default -> {
                        return Optional.of(record(level, null, kingdomId, template, anchor, placement, materials, survey, plan));
                    }
                }
            } while (System.nanoTime() - started < nanos);
            return Optional.empty();
        }
    }

    // ---------------------------------------------------------------- materials

    /**
     * Takes items from the storehouses for a construction, all or none; what other work set aside stays.
     */
    static boolean takeFromStorehouses(MinecraftServer server, Construction construction, Map<String, Integer> items) {
        if (items.isEmpty()) {
            return true;
        }
        UUID key = holdKey(construction);
        Reservations reservations = Production.reservations();
        UUID kingdom = construction.kingdomId();
        List<KingdomStorage.Take> takes = KingdomStorage.plan(server, kingdom, items, item -> reservations.reserved(kingdom, item, key),
                construction.anchor());
        if (takes == null || KingdomStorage.takePlanned(server, takes) == null) {
            return false;
        }
        items.forEach((item, count) -> reservations.taken(key, item, count));
        return true;
    }

    /**
     * Pays for one block far from players: the site first, the rest from the storehouses, all or none.
     */
    public static boolean pay(MinecraftServer server, Construction construction, Map<String, Integer> cost) {
        Map<String, Integer> fromSite = new LinkedHashMap<>();
        Map<String, Integer> fromStore = new LinkedHashMap<>();
        cost.forEach((item, count) -> {
            int site = Math.min(count, construction.delivered().getOrDefault(item, 0));
            if (site > 0) {
                fromSite.put(item, site);
            }
            if (count > site) {
                fromStore.put(item, count - site);
            }
        });
        if (!takeFromStorehouses(server, construction, fromStore)) {
            return false;
        }
        fromSite.forEach(construction::takeDelivered);
        return true;
    }

    /**
     * Items of a kind in the storehouses that a construction may take: what other work set aside stays.
     */
    public static long available(MinecraftServer server, Construction construction, String item) {
        Item found = PostWork.item(item);
        if (found == null) {
            return 0;
        }
        long stock = KingdomStorage.count(server, construction.kingdomId(), stack -> stack.is(found));
        return Math.max(0, stock - Production.reservations().reserved(construction.kingdomId(), item, holdKey(construction)));
    }

    /**
     * A builder brings materials from the storehouses to the site itself.
     *
     * @return items moved
     */
    public static int fetch(MinecraftServer server, Construction construction, String item, int amount) {
        int count = (int) Math.min(amount, available(server, construction, item));
        if (count <= 0 || !takeFromStorehouses(server, construction, Map.of(item, count))) {
            return 0;
        }
        int taken = construction.deliver(item, count);
        if (taken < count) {
            // The site keeps too many kinds already: the rest goes back.
            giveBack(server, construction, Map.of(item, count - taken));
        }
        return taken;
    }

    /**
     * Puts materials back into the storehouses of a construction's kingdom; what does not fit is dropped at the site.
     */
    static void giveBack(MinecraftServer server, Construction construction, Map<String, Integer> items) {
        ServerLevel level = level(server, construction.dimension());
        BlockPos at = blockPos(construction.anchor());
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            Item item = PostWork.item(entry.getKey());
            if (item == null) {
                continue;
            }
            int left = entry.getValue();
            while (left > 0) {
                int count = Math.min(left, new ItemStack(item).getMaxStackSize());
                left -= count;
                ItemStack rest = KingdomStorage.insert(server, construction.kingdomId(), new ItemStack(item, count), construction.anchor());
                if (rest.isEmpty() || level == null) {
                    continue;
                }
                if (ChunkHold.loaded(level, at)) {
                    Containers.dropItemStack(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, rest);
                } else {
                    // Nothing is loaded there: the items wait in the book and are dropped once the place is loaded.
                    KingdomWorldApi.state(server).construction().addDrop(construction.dimension(), at.getX(), at.getY(), at.getZ(),
                            entry.getKey(), rest.getCount(), construction.kingdomId(), KingdomWorldApi.gameTime(server));
                }
            }
        }
    }

    /**
     * Drops of cleared blocks of a construction: earthworks keep the ground their fill wants and send the rest of it to the
     * spoil heap when the storehouses are full; other constructions put all of it into the storehouses.
     */
    private static void storeOrRoute(MinecraftServer server, Construction construction, ServerLevel level, List<ItemStack> drops) {
        if (construction.kind() == Construction.Kind.EARTHWORK) {
            plan(construction).ifPresent(plan -> Earthworks.route(server, construction, plan, level, drops, false));
        } else {
            store(server, construction.kingdomId(), level, blockPos(construction.anchor()), drops);
        }
    }

    /**
     * Drops of cleared blocks into the storehouses; what does not fit falls at the place.
     */
    static void store(MinecraftServer server, UUID kingdomId, ServerLevel level, BlockPos at, List<ItemStack> drops) {
        for (ItemStack drop : drops) {
            // The drops of the builders go to the storehouses of builders, then to the common ones, then to any with room.
            ItemStack rest = KingdomStorage.insertFor(server, kingdomId, io.github.sekerator.kingdomworld.core.citizen.Professions.BUILDER.id(), drop, worldPos(level, at));
            rest = KingdomStorage.insert(server, kingdomId, rest, worldPos(level, at));
            if (!rest.isEmpty()) {
                Containers.dropItemStack(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, rest);
            }
        }
    }

    /**
     * The items a carrier brings are put into the site of the construction whose anchor is at the position.
     */
    @Nullable
    public static IItemHandler siteHandler(MinecraftServer server, WorldPos pos) {
        for (Construction construction : KingdomWorldApi.state(server).construction().all()) {
            if (construction.anchor().equals(pos)) {
                return new SiteHandler(construction);
            }
        }
        return null;
    }

    /**
     * The site as a container that only takes items.
     */
    private record SiteHandler(Construction construction) implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || stack.hasTag()) {
                return stack;
            }
            String id = KingdomStorage.itemId(stack);
            if (simulate) {
                return construction.delivered().containsKey(id) || construction.delivered().size() < Construction.MAX_DELIVERED_KINDS
                        ? ItemStack.EMPTY : stack;
            }
            int taken = construction.deliver(id, stack.getCount());
            return taken >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - taken);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !stack.hasTag();
        }
    }

    // ---------------------------------------------------------------- doing a step in the world

    public enum StepResult {
        DONE,
        /** A container with items or a protected block stands in the way. */
        BLOCKED,
        /** The place is no longer all the kingdom's land: nothing was touched. */
        OUTSIDE_LAND,
        /** The materials of the block are not at the site. */
        NO_MATERIALS
    }

    /**
     * Whether a block keeps items it would lose when broken: a container that is not empty, or one whose loot is not
     * rolled yet.
     */
    public static boolean holdsItems(ServerLevel level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null) {
            return false;
        }
        if (entity instanceof Container container && !container.isEmpty()) {
            return true;
        }
        // A half of a double storehouse holds only what is in its own half.
        IItemHandler handler = entity instanceof io.github.sekerator.kingdomworld.common.storage.StorehouseBlockEntity storehouse
                ? storehouse.ownHandler() : entity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
        if (handler != null) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (!handler.getStackInSlot(slot).isEmpty()) {
                    return true;
                }
            }
        }
        return entity.saveWithoutMetadata().contains("LootTable");
    }

    /**
     * Breaks a block in the way as a player would: its drops go into {@code drops}, liquids just leave. A container with
     * items and protected blocks are left standing.
     */
    public static StepResult clear(ServerLevel level, BlockPos pos, @Nullable LivingEntity breaker, ItemStack tool, List<ItemStack> drops) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || Scaffolds.is(state)) {
            // A scaffold is not in the way: it drops nothing and its builders take it down when they no longer need it, and a
            // block placed there replaces it.
            return StepResult.DONE;
        }
        if (blocksWork(level, pos, state) || holdsItems(level, pos)) {
            return StepResult.BLOCKED;
        }
        if (TemplateBlocks.isLiquid(state)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return StepResult.DONE;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        drops.addAll(Block.getDrops(state, level, pos, entity, breaker, tool));
        level.destroyBlock(pos, false, breaker);
        return StepResult.DONE;
    }

    /**
     * Places a block the way a player does: the state, then the placement hooks of its block (a storehouse, a post or a
     * block of another mod registers itself; a door gets its upper half).
     */
    public static void place(ServerLevel level, BlockPos pos, BlockState state, Item item) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
        BlockState placed = level.getBlockState(pos);
        if (io.github.sekerator.kingdomworld.common.block.FlagBlocks.is(placed)) {
            // A flag shows its kingdom at once, not white for a moment.
            io.github.sekerator.kingdomworld.common.flag.FlagWork.placed(level, pos);
        }
        if (placed.getBlock() == state.getBlock()) {
            placed.getBlock().setPlacedBy(level, pos, placed, FakePlayerFactory.get(level, PLACER), new ItemStack(item));
            // Beds and other places of citizens count at once, as when a player places them.
            io.github.sekerator.kingdomworld.common.citizen.CitizenSites.onPlaced(level, pos, placed);
            if (placed.getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
                BlockPos head = pos.relative(placed.getValue(net.minecraft.world.level.block.BedBlock.FACING));
                io.github.sekerator.kingdomworld.common.citizen.CitizenSites.onPlaced(level, head, level.getBlockState(head));
            }
        }
    }

    /**
     * Does one step of a construction in the world.
     *
     * @param pay whether the block is paid from the site now (near players); steps paid for far from players are not
     */
    public static StepResult doStep(ServerLevel level, Construction construction, BuildPlan plan, BuildingTemplate template, int index,
                                    @Nullable LivingEntity builder, ItemStack tool, List<ItemStack> drops, boolean pay) {
        if (!onOwnLand(level.getServer(), construction)) {
            construction.setStatus(Construction.Status.OUTSIDE_LAND);
            return StepResult.OUTSIDE_LAND;
        }
        BuildPlan.Step step = plan.step(index);
        BlockPos at = new BlockPos(step.x(), step.y(), step.z());
        if (construction.kind() == Construction.Kind.DEMOLISH) {
            StepResult result = BuildingWork.takeDown(level, construction, template, at);
            if (result == StepResult.DONE) {
                done(construction, index);
            }
            return result;
        }
        boolean earthwork = construction.kind() == Construction.Kind.EARTHWORK;
        if (step.kind() == BuildPlan.Kind.CLEAR && earthwork) {
            // Earthworks dig only the land as it grew; lava is closed off with cobblestone first.
            StepResult earth = Earthworks.dig(level, construction, at, builder, drops, false);
            if (earth == StepResult.DONE) {
                done(construction, index);
            }
            return earth;
        }
        if (step.kind() == BuildPlan.Kind.CLEAR) {
            StepResult result = clear(level, at, builder, tool, drops);
            if (result == StepResult.DONE) {
                done(construction, index);
            }
            return result;
        }
        BlockState wanted = target(template, construction, step.block());
        BlockState there = level.getBlockState(at);
        if (TemplateBlocks.sameBuilt(there, wanted)) {
            // Already standing (the second half of a door placed with the first, a post showing its profession): nothing to pay.
            if (construction.isPending(index)) {
                giveBack(level.getServer(), construction, step.cost());
            }
            done(construction, index);
            return StepResult.DONE;
        }
        if (earthwork) {
            // The place of a block of the fill is looked at anew: what somebody placed there since stays, and the block is left out.
            StepResult before = Earthworks.beforeFill(level, construction, at, builder, drops, false);
            if (before == StepResult.DONE) {
                if (construction.isPending(index)) {
                    giveBack(level.getServer(), construction, step.cost());
                }
                done(construction, index);
                return StepResult.DONE;
            }
            if (before != null) {
                return before;
            }
        } else if (!there.isAir() && !there.canBeReplaced() && !Scaffolds.is(there)) {
            StepResult cleared = clear(level, at, builder, tool, drops);
            if (cleared != StepResult.DONE) {
                return cleared;
            }
        }
        if (pay && !construction.isPending(index)) {
            if (!construction.hasDelivered(step.cost())) {
                return StepResult.NO_MATERIALS;
            }
            step.cost().forEach(construction::takeDelivered);
        }
        Item item = template.blocks().get(step.block()).state().getBlock().asItem();
        place(level, at, wanted, item);
        placeParts(level, construction, template, step.block());
        done(construction, index);
        return StepResult.DONE;
    }

    /**
     * Second parts of a block the placement hooks did not make (a door of a mod without them): placed with it, they cost
     * nothing more.
     */
    private static void placeParts(ServerLevel level, Construction construction, BuildingTemplate template, int main) {
        BlockPos mainPos = template.blocks().get(main).pos();
        for (int i : parts(template)) {
            BuildingTemplate.Block block = template.blocks().get(i);
            if (!block.pos().offset(TemplateBlocks.mainOf(block.state())).equals(mainPos)) {
                continue;
            }
            BlockPos at = template.place(block.pos(), blockPos(construction.anchor()), construction.placement());
            BlockState wanted = target(template, construction, i);
            BlockState there = level.getBlockState(at);
            if (!there.equals(wanted) && (there.isAir() || there.canBeReplaced())) {
                level.setBlock(at, wanted, Block.UPDATE_ALL);
                io.github.sekerator.kingdomworld.common.citizen.CitizenSites.onPlaced(level, at, wanted);
            }
        }
    }

    /**
     * The blocks of a template that are second parts of others, by the template itself (not by what it holds): a large template
     * is not gone through for every block placed. Forgotten when too many gather.
     */
    private static final Map<BuildingTemplate, int[]> PARTS = new java.util.IdentityHashMap<>();

    private static synchronized int[] parts(BuildingTemplate template) {
        int[] found = PARTS.get(template);
        if (found == null) {
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < template.costs().size(); i++) {
                if (template.costs().get(i).kind() == BlockCost.Kind.PART) {
                    list.add(i);
                }
            }
            found = list.stream().mapToInt(Integer::intValue).toArray();
            if (PARTS.size() >= 512) {
                PARTS.clear();
            }
            PARTS.put(template, found);
        }
        return found;
    }

    // ---------------------------------------------------------------- the work of the builders

    /**
     * A builder reports that it works at a construction.
     */
    public static void worked(Construction construction, UUID builder, long now) {
        WORKERS.computeIfAbsent(construction.id(), id -> new HashMap<>()).put(builder, now);
    }

    /**
     * Builders that worked at a construction lately.
     */
    public static int builders(Construction construction, long now) {
        Map<UUID, Long> workers = WORKERS.get(construction.id());
        if (workers == null) {
            return 0;
        }
        int count = 0;
        for (long last : workers.values()) {
            if (now - last <= BUILDER_TIMEOUT) {
                count++;
            }
        }
        return count;
    }

    /**
     * Whether another builder works on a step, or it is put off for now.
     */
    public static boolean busy(Construction construction, int step, UUID builder, long now) {
        UUID claimer = CLAIMS.getOrDefault(construction.id(), Map.of()).get(step);
        if (claimer != null && !claimer.equals(builder)) {
            return true;
        }
        Long until = DEFERRED.getOrDefault(construction.id(), Map.of()).get(step);
        return until != null && until > now;
    }

    public static void claim(Construction construction, int step, UUID builder) {
        CLAIMS.computeIfAbsent(construction.id(), id -> new HashMap<>()).put(step, builder);
    }

    public static void release(Construction construction, int step, UUID builder) {
        Map<Integer, UUID> claims = CLAIMS.get(construction.id());
        if (claims != null && builder.equals(claims.get(step))) {
            claims.remove(step);
        }
    }

    /**
     * Forgets everything a builder claimed, when its work ends.
     */
    public static void releaseAll(UUID builder) {
        for (Map<Integer, UUID> claims : CLAIMS.values()) {
            claims.values().removeIf(builder::equals);
        }
    }

    /**
     * Puts a step off for a while.
     */
    public static void defer(Construction construction, int step, long until) {
        DEFERRED.computeIfAbsent(construction.id(), id -> new HashMap<>()).put(step, until);
    }

    /**
     * The constructions a builder at a post may take: of its kingdom, within reach of the post, neither paused nor stuck on
     * a problem only a player can fix, the most urgent first, then the nearest.
     */
    public static List<Construction> candidates(MinecraftServer server, WorkPost post) {
        ConstructionRules rules = ConstructionSettings.rules();
        long postCity = cityOf(server, post.kingdomId(), post.pos());
        List<Construction> result = new ArrayList<>();
        for (Construction construction : KingdomWorldApi.state(server).construction().of(post.kingdomId())) {
            Construction.Status status = construction.status();
            if (construction.paused() || status == Construction.Status.TEMPLATE_MISSING || status == Construction.Status.TEMPLATE_CHANGED
                    || status == Construction.Status.OUTSIDE_LAND || status == Construction.Status.DONE
                    || status == Construction.Status.BUILDING_GONE
                    || !construction.dimension().equals(post.pos().dimension())) {
                continue;
            }
            if (io.github.sekerator.kingdomworld.core.construction.BuilderReach.reaches(postCity, cityOf(server, construction),
                    distanceSqr(construction.box(), post.pos()), rules.builderRadius())) {
                result.add(construction);
            }
        }
        result.sort((a, b) -> {
            int priority = Integer.compare(b.priority().ordinal(), a.priority().ordinal());
            return priority != 0 ? priority : Double.compare(distanceSqr(a.box(), post.pos()), distanceSqr(b.box(), post.pos()));
        });
        return result;
    }

    /**
     * Whether the builders of a post take a construction: in their radius, or in the city of the post (see
     * {@link io.github.sekerator.kingdomworld.core.construction.BuilderReach}).
     */
    public static boolean reaches(MinecraftServer server, WorkPost post, Construction construction) {
        return construction.kingdomId().equals(post.kingdomId()) && construction.dimension().equals(post.pos().dimension())
                && io.github.sekerator.kingdomworld.core.construction.BuilderReach.reaches(cityOf(server, post.kingdomId(), post.pos()),
                cityOf(server, construction), distanceSqr(construction.box(), post.pos()), ConstructionSettings.rules().builderRadius());
    }

    private record CityOf(long generation, long city) {
    }

    /** The city the middle of each construction lies in, worked out again when the squares of the cities are. */
    private static final Map<Long, CityOf> CITY_OF = new java.util.HashMap<>();

    /**
     * The city of its kingdom the middle of a construction lies in, or {@link io.github.sekerator.kingdomworld.core.construction.BuilderReach#NO_CITY}.
     */
    public static long cityOf(MinecraftServer server, Construction construction) {
        long generation = io.github.sekerator.kingdomworld.common.road.CityAreas.generation(server);
        CityOf known = CITY_OF.get(construction.id());
        if (known != null && known.generation() == generation) {
            return known.city();
        }
        Box box = construction.box();
        long city = cityOf(server, construction.kingdomId(), new WorldPos(construction.dimension(), (box.minX() + box.maxX()) / 2, box.minY(),
                (box.minZ() + box.maxZ()) / 2));
        if (CITY_OF.size() > 4 * Math.max(64, KingdomWorldApi.state(server).construction().all().size())) {
            CITY_OF.clear();
        }
        CITY_OF.put(construction.id(), new CityOf(generation, city));
        return city;
    }

    /**
     * The city of a kingdom a place lies in (0 for the capital), or {@link io.github.sekerator.kingdomworld.core.construction.BuilderReach#NO_CITY}.
     */
    public static long cityOf(MinecraftServer server, UUID kingdomId, WorldPos pos) {
        return io.github.sekerator.kingdomworld.common.road.CityAreas.at(server, kingdomId, pos.dimension(), pos.x(), pos.z())
                .map(io.github.sekerator.kingdomworld.common.road.CityAreas.Area::cityId)
                .orElse(io.github.sekerator.kingdomworld.core.construction.BuilderReach.NO_CITY);
    }

    /**
     * Squared distance from a position to the nearest block of a box.
     */
    public static double distanceSqr(Box box, WorldPos pos) {
        double dx = Math.max(0, Math.max(box.minX() - pos.x(), pos.x() - box.maxX()));
        double dy = Math.max(0, Math.max(box.minY() - pos.y(), pos.y() - box.maxY()));
        double dz = Math.max(0, Math.max(box.minZ() - pos.z(), pos.z() - box.maxZ()));
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Whether every chunk of a construction still belongs to its kingdom. Land given up or taken by another kingdom stops
     * the work: nothing is broken or placed there, and what was paid for stays the construction's.
     */
    public static boolean onOwnLand(MinecraftServer server, Construction construction) {
        if (construction.kind() == Construction.Kind.EARTHWORK) {
            // Earthworks leave the columns of others as they are: only the chunks their steps touch count.
            return Earthworks.onOwnLand(server, construction);
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        for (long chunk : construction.box().chunks()) {
            if (!construction.kingdomId().equals(state.territory().ownerOf(construction.dimension(), chunk))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether every chunk of a construction is loaded; for earthworks, every chunk their steps touch.
     */
    public static boolean loaded(ServerLevel level, Construction construction) {
        return construction.kind() == Construction.Kind.EARTHWORK ? Earthworks.loaded(level, construction) : loaded(level, construction.box());
    }

    /**
     * Whether every chunk of a box is loaded.
     */
    public static boolean loaded(ServerLevel level, Box box) {
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                if (!ChunkHold.loaded(level, cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- every second

    /**
     * Moves construction on: blocks paid for are placed where their place is loaded, finished constructions go into the
     * register, statuses, reservations and orders are kept current, and the integrity of buildings is looked at. Called
     * once a second.
     */
    public static void tick(MinecraftServer server, long tick) {
        if (KingdomWorldApi.isReadOnly(server)) {
            return;
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        long now = KingdomWorldApi.gameTime(server);
        int budget = APPLY_PER_SECOND;
        for (Construction construction : new ArrayList<>(state.construction().all())) {
            Optional<BuildPlan> plan = plan(construction);
            if (plan.isEmpty()) {
                continue;
            }
            ServerLevel level = level(server, construction.dimension());
            if (level == null) {
                continue;
            }
            // The place the loader of places serves gets its steps there, a few a tick, not hundreds in this one.
            if (!construction.pending().isEmpty() && budget > 0 && !SiteLoader.serving(construction.id())) {
                budget -= applyPending(level, construction, plan.get(), budget);
            }
            if (finished(construction, plan.get())) {
                // The scaffolds go before the construction is finished: a builder still on one comes down first, and those
                // where nothing is loaded wait for their place to be loaded.
                if (!Scaffolds.takeDownAll(level, construction, false, now)) {
                    construction.setStatus(builders(construction, now) > 0 ? Construction.Status.WORKING : Construction.Status.WAITING_FOR_LOAD);
                    continue;
                }
                complete(server, construction, false);
                continue;
            }
            if (tick % 100 == 0) {
                refreshStatus(server, construction, plan.get(), level, now);
            }
        }
        if (tick % 100 == 0) {
            refreshReservations(server);
        }
        Scaffolds.tick(server, now);
        Carts.tick(server, now);
        if (tick % 200 == 0) {
            orderMissing(server);
            BuildingWork.autoRepair(server);
        }
        checkIntegrity(server, now);
        BuildingEffects.tick(server, tick);
    }

    /**
     * Places blocks paid for far from players, in their order, once every chunk of the construction is loaded, and only
     * while the place is still the kingdom's land; until then they stay paid for and waiting.
     *
     * @return steps done
     */
    public static int applyPending(ServerLevel level, Construction construction, BuildPlan plan, int budget) {
        if (!onOwnLand(level.getServer(), construction)) {
            construction.setStatus(Construction.Status.OUTSIDE_LAND);
            return 0;
        }
        if (!loaded(level, construction)) {
            if (!construction.paused()) {
                construction.setStatus(Construction.Status.WAITING_FOR_LOAD);
            }
            return 0;
        }
        Optional<BuildingTemplate> template = template(construction);
        if (template.isEmpty()) {
            return 0;
        }
        int done = 0;
        List<Integer> pending = new ArrayList<>(construction.pending());
        pending.sort(Integer::compare);
        List<ItemStack> drops = new ArrayList<>();
        boolean blocked = false;
        long now = KingdomWorldApi.gameTime(level.getServer());
        for (int step : pending) {
            // A container in the way stops the building, not the rest of the clearing.
            if (done >= budget || (blocked && plan.step(step).kind() == BuildPlan.Kind.PLACE)) {
                break;
            }
            BuildPlan.Step at = plan.step(step);
            if (Scaffolds.inUse(construction.dimension(), at.x(), at.y(), at.z(), now)) {
                // A builder stands on a scaffold there: the block waits until the column is taken down.
                continue;
            }
            StepResult result = doStep(level, construction, plan, template.get(), step, null, ItemStack.EMPTY, drops, false);
            if (result == StepResult.OUTSIDE_LAND) {
                break;
            }
            if (result == StepResult.NO_MATERIALS) {
                // Earthworks wait for cobblestone to close off lava: the step stays paid for and waiting.
                continue;
            }
            if (result == StepResult.BLOCKED) {
                construction.setStatus(Construction.Status.BLOCKED, new WorldPos(construction.dimension(), at.x(), at.y(), at.z()));
                blocked = true;
                continue;
            }
            done++;
        }
        if (!drops.isEmpty()) {
            if (construction.kind() == Construction.Kind.EARTHWORK) {
                Earthworks.route(level.getServer(), construction, plan, level, drops, false);
            } else {
                store(level.getServer(), construction.kingdomId(), level, blockPos(construction.anchor()), drops);
            }
        }
        if (!blocked && done > 0 && (construction.status() == Construction.Status.WAITING_FOR_LOAD
                || construction.status() == Construction.Status.BLOCKED || construction.status() == Construction.Status.OUTSIDE_LAND)) {
            construction.setStatus(Construction.Status.WORKING);
        }
        return done;
    }

    private static void refreshStatus(MinecraftServer server, Construction construction, BuildPlan plan, ServerLevel level, long now) {
        if (!onOwnLand(server, construction)) {
            construction.setStatus(Construction.Status.OUTSIDE_LAND);
            return;
        }
        if (construction.status() == Construction.Status.OUTSIDE_LAND) {
            construction.setStatus(Construction.Status.NO_BUILDER);
        }
        if (construction.paused()) {
            construction.setStatus(Construction.Status.PAUSED);
            return;
        }
        if (construction.status() == Construction.Status.PAUSED) {
            construction.setStatus(Construction.Status.NO_BUILDER);
        }
        if (builders(construction, now) == 0 && construction.status() != Construction.Status.WAITING_FOR_LOAD
                && construction.status() != Construction.Status.BLOCKED) {
            construction.setStatus(Construction.Status.NO_BUILDER);
        }
    }

    /**
     * Sets aside again what the constructions of one kingdom need, as {@link #refreshReservations(MinecraftServer)} does for
     * every kingdom: the holds of constructions that are gone are let go, the holds of its constructions taken out of its
     * storehouses anew. The holds of other kingdoms stay as they are, so doing it for every kingdom in turn gives the same holds.
     */
    public static void refreshReservations(MinecraftServer server, UUID kingdomId) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        Reservations reservations = Production.reservations();
        List<Construction> mine = state.construction().of(kingdomId);
        Set<UUID> live = new HashSet<>();
        for (Construction construction : mine) {
            live.add(holdKey(construction));
        }
        for (UUID worker : reservations.workers(kingdomId)) {
            if (isConstructionKey(worker) && !live.contains(worker)) {
                reservations.release(worker);
            }
        }
        if (mine.isEmpty()) {
            return;
        }
        for (Construction construction : mine) {
            reservations.release(holdKey(construction));
        }
        Map<String, Long> stock = KingdomStorage.countsById(server, kingdomId);
        for (Construction construction : mine) {
            Optional<BuildPlan> plan = plan(construction);
            if (construction.paused() || plan.isEmpty()) {
                continue;
            }
            Map<String, Integer> hold = new LinkedHashMap<>();
            UUID key = holdKey(construction);
            needed(construction, plan.get()).forEach((item, count) -> {
                long free = stock.getOrDefault(item, 0L) - reservations.reserved(kingdomId, item, key);
                int take = (int) Math.max(0, Math.min(count, free));
                if (take > 0) {
                    hold.put(item, take);
                }
            });
            if (!hold.isEmpty()) {
                KEYS.add(key);
                reservations.reserve(key, new Reservations.Hold(kingdomId, 0, hold, "", 0));
            }
        }
    }

    /**
     * Sets aside in the storehouses what every construction still needs, the most urgent first, so that workshops and
     * the market do not take it. Paused constructions set nothing aside.
     */
    public static void refreshReservations(MinecraftServer server) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        Reservations reservations = Production.reservations();
        Set<UUID> live = new HashSet<>();
        Map<UUID, List<Construction>> byKingdom = new LinkedHashMap<>();
        for (Construction construction : state.construction().all()) {
            live.add(holdKey(construction));
            byKingdom.computeIfAbsent(construction.kingdomId(), id -> state.construction().of(id));
        }
        for (Kingdom kingdom : state.kingdoms().all()) {
            for (UUID worker : reservations.workers(kingdom.id())) {
                if (isConstructionKey(worker) && !live.contains(worker)) {
                    reservations.release(worker);
                }
            }
        }
        for (Map.Entry<UUID, List<Construction>> entry : byKingdom.entrySet()) {
            UUID kingdom = entry.getKey();
            for (Construction construction : entry.getValue()) {
                reservations.release(holdKey(construction));
            }
            Map<String, Long> stock = KingdomStorage.countsById(server, kingdom);
            for (Construction construction : entry.getValue()) {
                Optional<BuildPlan> plan = plan(construction);
                if (construction.paused() || plan.isEmpty()) {
                    continue;
                }
                Map<String, Integer> hold = new LinkedHashMap<>();
                UUID key = holdKey(construction);
                needed(construction, plan.get()).forEach((item, count) -> {
                    long free = stock.getOrDefault(item, 0L) - reservations.reserved(kingdom, item, key);
                    int take = (int) Math.max(0, Math.min(count, free));
                    if (take > 0) {
                        hold.put(item, take);
                    }
                });
                if (!hold.isEmpty()) {
                    KEYS.add(key);
                    reservations.reserve(key, new Reservations.Hold(kingdom, 0, hold, "", 0));
                }
            }
        }
    }

    private static boolean isConstructionKey(UUID key) {
        return KEYS.contains(key);
    }

    /** Keys of every construction that ever set something aside since the server started. */
    private static final Set<UUID> KEYS = new HashSet<>();

    /**
     * Gives the workshops orders for missing materials of constructions that ask for it, once per item while the order is
     * open; tells what nobody makes.
     */
    private static void orderMissing(MinecraftServer server) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        for (Construction construction : state.construction().all()) {
            if (!construction.orderMissing() || construction.paused()) {
                continue;
            }
            Optional<BuildPlan> plan = plan(construction);
            if (plan.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, Integer> missing : missing(server, construction, plan.get()).entrySet()) {
                orderItem(server, construction, missing.getKey(), missing.getValue());
            }
        }
    }

    /**
     * Gives one order for a missing material, unless the construction has an open order for it.
     *
     * @return whether an order is open for it now
     */
    public static boolean orderItem(MinecraftServer server, Construction construction, String item, int count) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        Long existing = construction.orders().get(item);
        if (existing != null && state.orders().get(construction.kingdomId(), existing).isPresent()) {
            return true;
        }
        if (existing != null) {
            construction.removeOrder(item);
        }
        if (!Production.orderable(server, item)) {
            return false;
        }
        KingdomWorldState.Ordering ordering = state.order(null, construction.kingdomId(), item, Order.Kind.MAKE,
                Math.max(1, Math.min(Order.MAX_AMOUNT, count)), Order.Priority.NORMAL, KingdomWorldApi.gameTime(server),
                ProductionSettings.rules().maxOrders(), id -> Production.orderable(server, id));
        ordering.order().ifPresent(order -> {
            construction.putOrder(item, order.id());
            LOGGER.info("Construction {} ordered {} x {} from the workshops", construction.id(), order.amount(), item);
        });
        return ordering.order().isPresent();
    }

    /**
     * What a construction lacks: the materials still needed beyond the site that the storehouses do not hold for it.
     */
    public static Map<String, Integer> missing(MinecraftServer server, Construction construction, BuildPlan plan) {
        Map<String, Integer> result = new LinkedHashMap<>();
        needed(construction, plan).forEach((item, count) -> {
            long there = available(server, construction, item);
            if (there < count) {
                result.put(item, (int) (count - there));
            }
        });
        return result;
    }

    /**
     * Who gathers an item in the kingdom, by profession id, or empty.
     */
    public static String gatherer(MinecraftServer server, UUID kingdomId, String item) {
        return Gatherers.of(server, kingdomId, item);
    }

    // ---------------------------------------------------------------- the end of a construction

    /**
     * A construction is finished: it goes into the register of the kingdom, what is left at the site goes back to the
     * storehouses and the members who manage construction hear of it.
     */
    public static Building complete(MinecraftServer server, Construction construction, boolean admin) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        BuildingTemplate template = template(construction).orElse(null);
        long now = KingdomWorldApi.gameTime(server);
        Building building = switch (construction.kind()) {
            case BUILD -> {
                String type = template == null ? "" : template.type();
                String category = template == null ? BuildingTemplate.Category.DECORATION.id() : template.category().id();
                yield state.construction().register(construction, type, category, template == null ? "" : template.role(),
                        template == null ? 1 : template.level(), now, admin);
            }
            case UPGRADE, REPAIR, DEMOLISH -> BuildingWork.finish(server, construction, template, now);
            // Earthworks leave the land changed and no building to enter into the register.
            case EARTHWORK -> null;
        };
        forget(server, construction);
        state.construction().remove(construction.id());
        giveBack(server, construction, construction.takeAllDelivered());
        BuildingEffects.changed();
        if (building != null) {
            io.github.sekerator.kingdomworld.common.flag.FlagWork.completed(server, building, construction.kind());
        }
        LOGGER.info("Construction {} ({}) of {} finished at {}{}", construction.id(), construction.kind().id(), construction.templateId(),
                construction.anchor().coordinates(), admin ? " by an operator" : "");
        Component name = template == null ? Component.literal(construction.templateId()) : template.name();
        String key = construction.kind() == Construction.Kind.BUILD ? "finished" : "finished." + construction.kind().id();
        state.kingdoms().get(construction.kingdomId()).ifPresent(kingdom -> KingdomActions.notifyMembers(server, kingdom,
                Permission.MANAGE_CONSTRUCTION, Component.translatable(KEY + key, name, construction.anchor().coordinates())
                        .withStyle(net.minecraft.ChatFormatting.GREEN)));
        return building;
    }

    /**
     * Cancels a construction: blocks placed stay; materials at the site and blocks paid for and not placed go back to the
     * storehouses; what was set aside is free again.
     */
    public static KingdomWorldState.ConstructionChange cancel(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, long id) {
        KingdomWorldState.ConstructionChange change = KingdomWorldApi.state(server).cancelConstruction(actor, kingdomId, id);
        change.construction().ifPresent(construction -> {
            Map<String, Integer> back = new LinkedHashMap<>(construction.takeAllDelivered());
            plan(construction).ifPresent(plan -> {
                for (int step : construction.pending()) {
                    plan.step(step).cost().forEach((item, count) -> back.merge(item, count, Integer::sum));
                }
            });
            forget(server, construction);
            giveBack(server, construction, back);
            LOGGER.info("Construction {} of {} cancelled; {} kind(s) of materials went back", construction.id(), construction.templateId(),
                    back.size());
        });
        return change;
    }

    /**
     * Finishes a construction at once, for operators: every step is done without materials, containers and protected
     * blocks in the way stay. A construction on land its kingdom no longer holds is left as it is.
     *
     * @return the building in the register, or empty if the place is not all the kingdom's land
     */
    public static Optional<Building> finishNow(MinecraftServer server, Construction construction) {
        return finishSome(server, construction, Integer.MAX_VALUE);
    }

    /**
     * Does up to {@code budget} steps of a construction at once, as {@link #finishNow} does all of them, so that a large
     * building can be put up over several ticks; the construction is finished with its last step.
     *
     * @return the building in the register once the construction is finished, empty while steps are left or if the place is not
     *         all the kingdom's land
     */
    public static Optional<Building> finishSome(MinecraftServer server, Construction construction, int budget) {
        if (!onOwnLand(server, construction)) {
            construction.setStatus(Construction.Status.OUTSIDE_LAND);
            return Optional.empty();
        }
        ServerLevel level = level(server, construction.dimension());
        Optional<BuildPlan> plan = plan(construction);
        Optional<BuildingTemplate> template = template(construction);
        if (level != null && plan.isPresent() && template.isPresent()) {
            // Scaffolds nobody stands on go first; those of builders on them now go when they come down.
            Scaffolds.takeDownAll(level, construction, true, KingdomWorldApi.gameTime(server));
            List<ItemStack> drops = new ArrayList<>();
            int done = 0;
            for (BuildPlan.Step step : plan.get().steps()) {
                if (construction.isDone(step.index())) {
                    continue;
                }
                if (done++ >= budget) {
                    storeOrRoute(server, construction, level, drops);
                    return Optional.empty();
                }
                BlockPos at = new BlockPos(step.x(), step.y(), step.z());
                level.getChunk(at);
                boolean earthwork = construction.kind() == Construction.Kind.EARTHWORK;
                if (earthwork && step.kind() == BuildPlan.Kind.CLEAR) {
                    // Only the land as it grew is dug, lava next to it closed off; what somebody placed since stays.
                    Earthworks.dig(level, construction, at, null, drops, true);
                } else if (earthwork && !construction.isPending(step.index())) {
                    BlockState wanted = target(template.get(), construction, step.block());
                    if (!level.getBlockState(at).equals(wanted) && Earthworks.beforeFill(level, construction, at, null, drops, true) == null) {
                        place(level, at, wanted, template.get().blocks().get(step.block()).state().getBlock().asItem());
                    }
                } else if (step.kind() == BuildPlan.Kind.PLACE) {
                    if (construction.isPending(step.index())) {
                        StepResult result = doStep(level, construction, plan.get(), template.get(), step.index(), null, ItemStack.EMPTY,
                                drops, false);
                        if (result != StepResult.DONE && construction.isPending(step.index())) {
                            // A protected block or a container keeps the place: what was paid for it goes back.
                            giveBack(server, construction, step.cost());
                        }
                    } else {
                        BlockState there = level.getBlockState(at);
                        if (!blocksWork(level, at, there) && !holdsItems(level, at)) {
                            if (!there.isAir() && !there.canBeReplaced()) {
                                clear(level, at, null, ItemStack.EMPTY, drops);
                            }
                            place(level, at, target(template.get(), construction, step.block()),
                                    template.get().blocks().get(step.block()).state().getBlock().asItem());
                            placeParts(level, construction, template.get(), step.block());
                        }
                    }
                } else if (construction.kind() == Construction.Kind.DEMOLISH) {
                    BuildingWork.takeDown(level, construction, template.get(), at);
                } else {
                    clear(level, at, null, ItemStack.EMPTY, drops);
                }
                done(construction, step.index());
            }
            storeOrRoute(server, construction, level, drops);
        }
        return Optional.ofNullable(complete(server, construction, true));
    }

    private static void forget(MinecraftServer server, Construction construction) {
        Production.reservations().release(holdKey(construction));
        PLANS.remove(construction.id());
        Earthworks.forget(construction.id());
        CLAIMS.remove(construction.id());
        DEFERRED.remove(construction.id());
        WORKERS.remove(construction.id());
    }

    // ---------------------------------------------------------------- integrity of buildings

    /**
     * Looks at a whole building at once, for operators and tests.
     *
     * @return the share of its blocks in place, or -1 if its place is not loaded or its template is gone
     */
    public static int checkNow(MinecraftServer server, long buildingId) {
        Optional<Building> found = KingdomWorldApi.state(server).construction().building(buildingId);
        if (found.isEmpty()) {
            return -1;
        }
        Building building = found.get();
        Optional<BuildingTemplate> template = template(building);
        ServerLevel level = level(server, building.dimension());
        if (template.isEmpty() || template.get().fingerprint() != building.fingerprint() || level == null || !loaded(level, building.box())) {
            return -1;
        }
        java.util.BitSet missing = new java.util.BitSet();
        int counted = 0;
        int standing = 0;
        BlockPos anchor = blockPos(building.anchor());
        for (int index = 0; index < template.get().blocks().size(); index++) {
            BlockCost cost = template.get().costs().get(index);
            // A flag gone is never damage: its kingdom puts it back, or a member took it down.
            if (cost.kind() == BlockCost.Kind.NOTHING || cost.kind() == BlockCost.Kind.LIQUID || cost.kind() == BlockCost.Kind.FREE) {
                continue;
            }
            BuildingTemplate.Block block = template.get().blocks().get(index);
            counted++;
            if (level.getBlockState(template.get().place(block.pos(), anchor, building.placement())).getBlock() == block.state().getBlock()) {
                standing++;
            } else {
                missing.set(index);
            }
        }
        int percent = counted == 0 ? 100 : (int) (standing * 100L / counted);
        KingdomWorldApi.state(server).construction().putBuilding(building.withIntegrity(percent, KingdomWorldApi.gameTime(server), missing));
        INTEGRITY.remove(building.id());
        MISSING.remove(building.id());
        BuildingEffects.changed(building.id());
        return percent;
    }

    /**
     * Looks at part of a building whose place is loaded and counts the blocks of its template still standing there; a
     * building is looked at in parts over several seconds.
     */
    private static void checkIntegrity(MinecraftServer server, long now) {
        List<Building> buildings = new ArrayList<>(KingdomWorldApi.state(server).construction().buildings());
        if (buildings.isEmpty()) {
            return;
        }
        Building building = buildings.get((int) Math.floorMod(integrityTurn, buildings.size()));
        Optional<BuildingTemplate> template = template(building);
        ServerLevel level = level(server, building.dimension());
        if (template.isEmpty() || template.get().fingerprint() != building.fingerprint() || level == null || !loaded(level, building.box())) {
            INTEGRITY.remove(building.id());
            MISSING.remove(building.id());
            integrityTurn++;
            return;
        }
        int[] progress = INTEGRITY.computeIfAbsent(building.id(), id -> new int[3]);
        java.util.BitSet missing = MISSING.computeIfAbsent(building.id(), id -> new java.util.BitSet());
        List<BuildingTemplate.Block> blocks = template.get().blocks();
        BlockPos anchor = blockPos(building.anchor());
        int budget = INTEGRITY_PER_SECOND;
        while (progress[0] < blocks.size() && budget-- > 0) {
            int index = progress[0]++;
            BlockCost cost = template.get().costs().get(index);
            if (cost.kind() == BlockCost.Kind.NOTHING || cost.kind() == BlockCost.Kind.LIQUID || cost.kind() == BlockCost.Kind.FREE) {
                continue;
            }
            BuildingTemplate.Block block = blocks.get(index);
            BlockState there = level.getBlockState(template.get().place(block.pos(), anchor, building.placement()));
            progress[2]++;
            if (there.getBlock() == block.state().getBlock()) {
                progress[1]++;
            } else {
                missing.set(index);
            }
        }
        if (progress[0] >= blocks.size()) {
            int percent = progress[2] == 0 ? 100 : (int) (progress[1] * 100L / progress[2]);
            Building current = KingdomWorldApi.state(server).construction().building(building.id()).orElse(building);
            KingdomWorldApi.state(server).construction().putBuilding(current.withIntegrity(percent, now, missing));
            INTEGRITY.remove(building.id());
            MISSING.remove(building.id());
            integrityTurn++;
            BuildingEffects.changed(building.id());
        }
    }
}
