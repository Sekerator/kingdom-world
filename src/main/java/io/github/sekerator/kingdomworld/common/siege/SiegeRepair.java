package io.github.sekerator.kingdomworld.common.siege;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.production.Production;
import io.github.sekerator.kingdomworld.common.production.ProductionSettings;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.production.Order;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.siege.SiegeRules;
import io.github.sekerator.kingdomworld.core.siege.SiegeState;
import io.github.sekerator.kingdomworld.core.siege.WarDamage;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * War damage: blocks of registered buildings and city walls broken in a war are written down when they break, and the
 * builders of the kingdom put them back from the storehouses after the peace (or earlier, when no enemy was near for a while).
 * Every block put back is paid with its item. A ruler of the world orders what is missing from its workshops; players see
 * in the diplomacy tab what is missing. Blocks built outside the register are a risk of war and are not mended.
 *
 * <p>Damage done in peace is kept in the same record and mended by the same hand, by other rules: a block of a city, a
 * building, a wall or a road that a player broke as damaged property is written down as it stood, whatever it was, and
 * comes back for free once the offender is gone or kept still for a while; a block a stranger placed in a city is written
 * down with what it covered, and is taken out again and dropped as an item. The law decides what counts
 * ({@link io.github.sekerator.kingdomworld.common.law.LawVandalism}).
 */
public final class SiegeRepair {
    /** Blocks put back for one kingdom in a second. */
    static final int PER_SECOND = 4;
    /** Game ticks a block damaged in peace waits for what it stood on to come back before it is given up: a game day. */
    static final long WAIT_FOR_SUPPORT = 24_000;
    /** Game ticks between two orders of the same item by a ruler of the world. */
    private static final long ORDER_EVERY = 12_000;

    private static final Map<String, Long> ORDERED = new HashMap<>();
    /** What was missing at the last try, for the tab. */
    private static final Map<UUID, Map<String, Integer>> MISSING = new HashMap<>();

    private SiegeRepair() {
    }

    static void reset() {
        ORDERED.clear();
        MISSING.clear();
    }

    /**
     * Items missing for the mending of a kingdom at the last try.
     */
    public static Map<String, Integer> missing(UUID kingdom) {
        return MISSING.getOrDefault(kingdom, Map.of());
    }

    // ---------------------------------------------------------------- writing down

    /**
     * What a broken block of a kingdom was: a block of the template of a registered building (with the state the template
     * wants there) or a block a city wall put there; nothing for a block the template does not have, or one of another kind
     * than the template wants (placed by somebody else), so that the kingdom never pays for putting back what was not its.
     *
     * @return the building (0 for a wall) and the state to put back
     */
    static Optional<Owned> owned(MinecraftServer server, UUID kingdom, String dimension, BlockPos pos, BlockState broken) {
        for (Building building : KingdomWorldApi.state(server).construction().buildingsOf(kingdom)) {
            if (!building.anchor().dimension().equals(dimension) || building.box() == null
                    || !building.box().contains(pos.getX(), pos.getY(), pos.getZ())) {
                continue;
            }
            Optional<BlockState> wanted = io.github.sekerator.kingdomworld.common.construction.BuildingWork.templateBlockAt(building, pos);
            if (wanted.isPresent() && !wanted.get().isAir() && wanted.get().getBlock() == broken.getBlock()) {
                return Optional.of(new Owned(building.id(), wanted.get()));
            }
            return Optional.empty();
        }
        for (Wall wall : RoadWork.state(server).walls()) {
            if (wall.kingdomId().equals(kingdom) && wall.dimension().equals(dimension) && wall.placedHere(pos.asLong())) {
                return Optional.of(new Owned(0L, broken));
            }
        }
        return Optional.empty();
    }

    record Owned(long building, BlockState state) {
    }

    /**
     * Writes down a block broken in a war, if it belongs to a building or a wall of the kingdom.
     *
     * @return whether it was written down
     */
    public static boolean broken(MinecraftServer server, UUID kingdom, ServerLevel level, BlockPos pos, BlockState state, long war) {
        if (state.isAir() || SiegeGates.isLeafPlace(pos) || io.github.sekerator.kingdomworld.common.block.FlagBlocks.is(state)) {
            // A flag has no item to order: the keeper of the flags puts it back once the city is calm.
            return false;
        }
        String dimension = Dimensions.id(level);
        Optional<Owned> owned = owned(server, kingdom, dimension, pos, state);
        if (owned.isEmpty()) {
            return false;
        }
        BlockState wanted = owned.get().state();
        Item item = wanted.getBlock().asItem();
        String itemId = item == Items.AIR ? "" : BuiltInRegistries.ITEM.getKey(item).toString();
        SiegeState siege = SiegeWork.state(server);
        boolean added = siege.damage().record(new WarDamage.Entry(war, kingdom, dimension, pos.asLong(), NbtUtils.writeBlockState(wanted).toString(),
                itemId, owned.get().building(), KingdomWorldApi.gameTime(server)));
        // The enemy is at the city: the builders wait until it has been gone a while.
        siege.enemyNear(kingdom, KingdomWorldApi.gameTime(server));
        siege.changed();
        return added;
    }

    // ---------------------------------------------------------------- damage in peace

    /**
     * How many more blocks damaged in peace the book of a kingdom takes: none while the data cannot be written. Whoever
     * breaks a block of a city asks first, so that nothing is broken that cannot be written down and put back.
     */
    public static int room(MinecraftServer server, UUID kingdom) {
        return SiegeWork.isReadOnly(server) ? 0 : SiegeWork.state(server).damage().room(kingdom);
    }

    /**
     * Whether a place of a kingdom is on the book already: what is broken there now needs no room, the first state is kept.
     */
    public static boolean isRecorded(MinecraftServer server, UUID kingdom, ServerLevel level, BlockPos pos) {
        return SiegeWork.state(server).damage().contains(kingdom, Dimensions.id(level), pos.asLong());
    }

    /**
     * Writes down a block of a kingdom a player broke in peace, as it stood, to put it back for free. Blocks that go with
     * it are written down by the caller after it, in the order they are to come back.
     *
     * @return whether the place is on the book now: written down, or recorded before; not if the book is full or cannot
     *         be written
     */
    public static boolean damaged(MinecraftServer server, UUID kingdom, ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || SiegeWork.isReadOnly(server)) {
            return false;
        }
        String dimension = Dimensions.id(level);
        SiegeState siege = SiegeWork.state(server);
        if (siege.damage().contains(kingdom, dimension, pos.asLong())) {
            return true;
        }
        boolean added = siege.damage().record(WarDamage.Entry.inPeace(kingdom, dimension, pos.asLong(), NbtUtils.writeBlockState(state).toString(),
                "", KingdomWorldApi.gameTime(server)));
        if (added) {
            siege.changed();
        }
        return added;
    }

    /**
     * Writes down a block a stranger placed in a city in peace, with what it covered: it is taken out again when the place
     * is mended, and falls there as an item.
     *
     * @return whether it was written down; not if the book is full or cannot be written, and then the block may not stay
     */
    public static boolean placed(MinecraftServer server, UUID kingdom, ServerLevel level, BlockPos pos, BlockState before, BlockState placed) {
        if (SiegeWork.isReadOnly(server)) {
            return false;
        }
        String dimension = Dimensions.id(level);
        SiegeState siege = SiegeWork.state(server);
        String now = NbtUtils.writeBlockState(placed).toString();
        boolean written = siege.damage().placedOver(kingdom, dimension, pos.asLong(), now)
                || siege.damage().record(WarDamage.Entry.inPeace(kingdom, dimension, pos.asLong(), NbtUtils.writeBlockState(before).toString(), now,
                KingdomWorldApi.gameTime(server)));
        if (written) {
            siege.changed();
        }
        return written;
    }

    /**
     * Whether the block at a place is one a stranger placed there in peace, which nobody answers for breaking.
     */
    public static boolean isPlacedByStranger(MinecraftServer server, UUID kingdom, ServerLevel level, BlockPos pos, BlockState state) {
        Optional<WarDamage.Entry> entry = SiegeWork.state(server).damage().at(kingdom, Dimensions.id(level), pos.asLong());
        if (entry.isEmpty() || entry.get().placed().isEmpty()) {
            return false;
        }
        BlockState placed = parse(level, entry.get().placed());
        return placed != null && placed.getBlock() == state.getBlock();
    }

    /**
     * How many blocks of a kingdom wait to be put back for free, for checks.
     */
    public static int waiting(MinecraftServer server, UUID kingdom) {
        return SiegeWork.state(server).damage().countFree(kingdom);
    }

    // ---------------------------------------------------------------- putting back

    /**
     * Whether a kingdom is calm enough to put things back: at peace, or at war with no enemy near its cities for a while.
     */
    public static boolean calm(MinecraftServer server, UUID kingdom, long now) {
        return calm(server, SiegeSettings.rules(), kingdom, now);
    }

    static boolean calm(MinecraftServer server, SiegeRules rules, UUID kingdom, long now) {
        boolean atWar = WarWork.state(server).fightsAnyWar(kingdom);
        boolean quiet = rules.repairQuietTicks() > 0 && now - SiegeWork.state(server).lastEnemyNear(kingdom) >= rules.repairQuietTicks();
        return !atWar || quiet;
    }

    static void tick(MinecraftServer server, SiegeRules rules, long now) {
        SiegeState state = SiegeWork.state(server);
        WarDamage damage = state.damage();
        for (UUID kingdom : List.copyOf(damage.all().keySet())) {
            if (KingdomWorldApi.kingdom(server, kingdom).isEmpty()) {
                damage.forget(kingdom);
                state.changed();
                continue;
            }
            if (!calm(server, rules, kingdom, now)) {
                continue;
            }
            // What was damaged in peace waits until the offender is gone or kept still for a while.
            boolean settled = io.github.sekerator.kingdomworld.common.law.LawVandalism.settled(server, kingdom, now);
            mend(server, kingdom, PER_SECOND, now, settled);
        }
    }

    /**
     * Puts back up to some damaged blocks of a kingdom: those broken in a war paid from its storehouses, those damaged in
     * peace for free.
     *
     * @return the blocks put back
     */
    static int mend(MinecraftServer server, UUID kingdom, int most, long now) {
        return mend(server, kingdom, most, now, true);
    }

    /**
     * @param peace whether what was damaged in peace is put back now too
     */
    static int mend(MinecraftServer server, UUID kingdom, int most, long now, boolean peace) {
        SiegeState state = SiegeWork.state(server);
        WarDamage damage = state.damage();
        Map<String, Integer> missing = new LinkedHashMap<>();
        int done = 0;
        boolean changed = false;
        for (WarDamage.Entry entry : damage.of(kingdom)) {
            if (done >= most) {
                break;
            }
            if (entry.free() && !peace) {
                continue;
            }
            ServerLevel level = SiegeWork.level(server, entry.dimension());
            BlockPos pos = BlockPos.of(entry.pos());
            if (level == null || !level.isLoaded(pos)) {
                continue;
            }
            BlockState wanted = parse(level, entry.state());
            BlockState there = level.getBlockState(pos);
            if (entry.free() && !entry.placed().isEmpty()) {
                BlockState foreign = parse(level, entry.placed());
                if (foreign != null && foreign.getBlock() == there.getBlock() && !there.isAir()) {
                    // What a stranger placed comes out and lies there as an item: it was never the city's.
                    level.destroyBlock(pos, true);
                    there = level.getBlockState(pos);
                    done++;
                }
            }
            if (wanted == null || there.equals(wanted)) {
                changed |= damage.mended(kingdom, entry.dimension(), entry.pos());
                continue;
            }
            if (entry.free()) {
                if (there.isAir() || there.canBeReplaced()) {
                    if (!wanted.getCollisionShape(level, pos).isEmpty()
                            && !level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, new net.minecraft.world.phys.AABB(pos),
                            player -> !player.isSpectator()).isEmpty()) {
                        // Somebody stands in the place: the block waits rather than wall a player in.
                        continue;
                    }
                    if (!wanted.canSurvive(level, pos) || wanted.getBlock() instanceof net.minecraft.world.level.block.FallingBlock
                            && net.minecraft.world.level.block.FallingBlock.isFree(level.getBlockState(pos.below()))) {
                        // What it stood on is not back yet: a torch waits for its wall, sand for the block under it. The
                        // support was written down first and comes first; a thing whose support never comes is given up.
                        if (now - entry.time() > WAIT_FOR_SUPPORT) {
                            changed |= damage.mended(kingdom, entry.dimension(), entry.pos());
                        }
                        continue;
                    }
                    // Without telling the neighbours to look at their shape: the other half of a door comes in its own turn.
                    level.setBlock(pos, wanted, net.minecraft.world.level.block.Block.UPDATE_CLIENTS
                            | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE);
                    done++;
                }
                // Else something else stands there now, built over by whoever may: it is not torn down.
                changed |= damage.mended(kingdom, entry.dimension(), entry.pos());
                continue;
            }
            if (!there.isAir() && !there.canBeReplaced()) {
                // Something else stands there now: the kingdom built it over, or an enemy did; it is not torn down.
                changed |= damage.mended(kingdom, entry.dimension(), entry.pos());
                continue;
            }
            Item item = entry.item().isEmpty() ? Items.AIR : BuiltInRegistries.ITEM.get(new ResourceLocation(entry.item()));
            if (item == Items.AIR) {
                changed |= damage.mended(kingdom, entry.dimension(), entry.pos());
                continue;
            }
            WorldPos near = new WorldPos(entry.dimension(), pos.getX(), pos.getY(), pos.getZ());
            if (missing.containsKey(entry.item()) || KingdomStorage.take(server, kingdom, stack -> stack.is(item), 1, near).isEmpty()) {
                missing.merge(entry.item(), 1, Integer::sum);
                continue;
            }
            level.setBlock(pos, wanted, 3);
            changed |= damage.mended(kingdom, entry.dimension(), entry.pos());
            done++;
        }
        if (missing.isEmpty()) {
            MISSING.remove(kingdom);
        } else {
            MISSING.put(kingdom, missing);
            if (WarWork.isNpc(server, kingdom)) {
                order(server, kingdom, missing, now);
            }
        }
        if (changed) {
            state.changed();
        }
        return done;
    }

    /**
     * A ruler of the world orders what its builders miss from its workshops.
     */
    private static void order(MinecraftServer server, UUID kingdom, Map<String, Integer> missing, long now) {
        KingdomWorldState world = KingdomWorldApi.state(server);
        for (Map.Entry<String, Integer> want : missing.entrySet()) {
            String key = kingdom + "/" + want.getKey();
            if (now - ORDERED.getOrDefault(key, Long.MIN_VALUE / 4) < ORDER_EVERY || !Production.orderable(server, want.getKey())) {
                continue;
            }
            ORDERED.put(key, now);
            int count = SiegeWork.state(server).damage().cost(kingdom).getOrDefault(want.getKey(), want.getValue());
            world.order(null, kingdom, want.getKey(), Order.Kind.MAKE, Math.max(1, Math.min(Order.MAX_AMOUNT, count)), Order.Priority.NORMAL, now,
                    ProductionSettings.rules().maxOrders(), id -> Production.orderable(server, id));
        }
    }

    @Nullable
    static BlockState parse(ServerLevel level, String text) {
        try {
            CompoundTag tag = TagParser.parseTag(text);
            return NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), tag);
        } catch (Exception e) {
            return null;
        }
    }
}
