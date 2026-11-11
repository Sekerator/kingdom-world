package io.github.sekerator.kingdomworld.common.law;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehaviors;
import io.github.sekerator.kingdomworld.common.construction.TemplateBlocks;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.road.CityAreas;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.siege.SiegeGates;
import io.github.sekerator.kingdomworld.common.siege.SiegeRepair;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.law.CrimeKind;
import io.github.sekerator.kingdomworld.core.law.LawRules;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadLayout;
import io.github.sekerator.kingdomworld.core.road.Wall;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Blocks on the land of another kingdom in peace. Breaking and placing them is possible rather than refused, and what it
 * means depends on the place.
 *
 * <ul>
 * <li>Inside a city of the kingdom, and at a registered building, a wall or a road of the kingdom anywhere on its land, a
 * block is property. Breaking it is a crime, damaged property: the block drops nothing, is written down as it stood and is
 * put back for free once the offender is gone or kept still (see {@link SiegeRepair}); if somebody saw it, the fine
 * follows the worth of the blocks, and blocks broken one after another make one crime. Whatever cannot stay without the
 * block (a torch, a rail, a door, a crop on it, sand over it) goes with it, drops nothing either, is written down after
 * it and comes back after it: damaged property gives no item and loses no block. A block a stranger places there is
 * written down too and taken out again.</li>
 * <li>On the rest of the land blocks are broken and placed freely: no crime, nothing put back. Only a block a frame or a
 * painting hangs on stays whole there too, so that what a frame holds never falls to a breaker.</li>
 * </ul>
 *
 * <p>Still refused in peace: the land of a kingdom of players none of whom is there, the throne, the working blocks of the
 * mod, workplaces, containers, beds and every other block that keeps data, the leaves of city gates; a block that a frame
 * or a painting hangs on, that holds up a block with data or more than {@link #CASCADE} blocks; any block while the book
 * of damage of the kingdom is full or cannot be written, since nothing is broken that cannot be written down. A stranger
 * places plain blocks only: nothing that holds goods or keeps data, nothing of redstone, no rails, pistons, explosives or
 * fire, and no entities (boats, minecarts, armor stands, frames). Nothing here asks about war: land open by war never comes
 * this far.
 */
public final class LawVandalism {
    /** Ticks between two hints that a broken block was property. */
    private static final long HINT_TICKS = 600;
    /** Ticks after its last damage an offender who left the land of a kingdom is taken to be gone. */
    private static final long GONE_TICKS = 100;
    /** Most blocks one broken block may take out of the world, itself among them; a block that would take more does not break. */
    static final int CASCADE = 64;
    /** A change of a block the clients see and the neighbours do not hear of. */
    private static final int SILENT = net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE;

    /**
     * What the law makes of a block a player breaks.
     */
    public enum Verdict {
        /** Refused, as land protection refuses it. */
        REFUSED,
        /** Allowed as on nobody's land: the block breaks and drops as usual. */
        FREE,
        /** Damaged property: the block is gone without drops and written down, and nothing else is to be done with it. */
        DAMAGED
    }

    /**
     * Damage a player did that nobody was asked about yet: the witnesses are looked for once a second, not for every block.
     */
    private static final class Pending {
        final UUID kingdom;
        long worth;
        BlockPos last;

        Pending(UUID kingdom, BlockPos last) {
            this.kingdom = kingdom;
            this.last = last;
        }
    }

    /**
     * The last damage done to a kingdom in peace.
     */
    private record Damage(long at, UUID offender) {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Damage> LAST = new HashMap<>();
    private static final Map<UUID, Long> HINTS = new HashMap<>();
    /**
     * The columns of a road with the height of the road there, and the blocks of the road they were worked out from.
     */
    private record RoadColumns(List<RoadLayout.Block> source, Long2IntOpenHashMap heights) {
    }

    /** The columns of each road, by the id of the road. */
    private static final Map<Long, RoadColumns> ROADS = new HashMap<>();

    private LawVandalism() {
    }

    static void reset() {
        PENDING.clear();
        LAST.clear();
        HINTS.clear();
        ROADS.clear();
    }

    // ---------------------------------------------------------------- what may be done

    /**
     * Whether the law judges what this player does to the blocks of a kingdom in peace instead of land protection refusing
     * it: the config says so, the law is writable, and somebody of the kingdom is there to answer (a kingdom of the world
     * always is, a kingdom of players while one of them is online at the keyboard).
     */
    private static boolean judged(Player player, Level level, UUID kingdom) {
        return player instanceof ServerPlayer actor && LawEvents.judges(player, level) && LawSettings.rules().breakingIsCrime
                && WarWork.playable(actor.server, kingdom);
    }

    /**
     * Whether a block stays whole in peace whatever the law says: blocks that keep data (containers, beds, signs, the
     * working blocks of the mod), blocks of the mod, workplaces of citizens, blocks the builders never clear and the
     * leaves of city gates.
     */
    static boolean keptWhole(BlockPos pos, BlockState state) {
        if (state.hasBlockEntity() || state.is(TemplateBlocks.UNCLEARABLE) || SiegeGates.isLeafPlace(pos)
                || ProfessionBehaviors.workplaceKind(state) != null) {
            return true;
        }
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id != null && KingdomWorld.MOD_ID.equals(id.getNamespace());
    }

    /**
     * Whether a block is plain enough for a stranger to place: it holds nothing and keeps no data (no chest, barrel, hopper,
     * dropper, dispenser, furnace or sign: a hopper under a chest of the kingdom would empty it with no theft on any
     * record, a dispenser would pour what a bucket may not), gives and carries no signal (no torch of redstone, lever,
     * button, plate, observer, repeater, dust or rail: they would work the mechanisms of the kingdom), and neither moves,
     * blows up, burns nor flows.
     */
    static boolean plain(BlockState state) {
        net.minecraft.world.level.block.Block block = state.getBlock();
        if (state.hasBlockEntity() || state.isSignalSource()) {
            return false;
        }
        if (block instanceof PistonBaseBlock || block instanceof TntBlock || block instanceof BaseFireBlock
                || block instanceof net.minecraft.world.level.block.LiquidBlock) {
            return false;
        }
        return !(block instanceof net.minecraft.world.level.block.BaseRailBlock || block instanceof net.minecraft.world.level.block.RedStoneWireBlock
                || block instanceof net.minecraft.world.level.block.DiodeBlock || block instanceof net.minecraft.world.level.block.ButtonBlock
                || block instanceof net.minecraft.world.level.block.LeverBlock
                || block instanceof net.minecraft.world.level.block.BasePressurePlateBlock
                || block instanceof net.minecraft.world.level.block.TripWireBlock
                || block instanceof net.minecraft.world.level.block.TripWireHookBlock);
    }

    /**
     * Whether an item puts an entity into the world where the player looks, with no block clicked: a boat, a spawn egg.
     * Such a click passes the rules of clicked blocks, so land protection asks about it apart. Minecarts, armor stands,
     * frames, paintings and crystals are put on a clicked block and are refused there with every other item that is no
     * plain block.
     */
    public static boolean placesEntityInSight(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.world.item.BoatItem || stack.getItem() instanceof net.minecraft.world.item.SpawnEggItem;
    }

    /**
     * Whether a place belongs to what a kingdom built: it lies in one of its cities, in the box of one of its registered
     * buildings, in one of its walls or on one of its roads.
     */
    public static boolean isProperty(MinecraftServer server, UUID kingdom, ServerLevel level, BlockPos pos) {
        String dimension = Dimensions.id(level);
        if (CityAreas.inCity(server, kingdom, dimension, pos.getX(), pos.getZ())) {
            return true;
        }
        for (Building building : KingdomWorldApi.state(server).construction().buildingsOf(kingdom)) {
            if (building.box() != null && building.anchor().dimension().equals(dimension)
                    && building.box().contains(pos.getX(), pos.getY(), pos.getZ())) {
                return true;
            }
        }
        long at = pos.asLong();
        for (Wall wall : RoadWork.state(server).walls()) {
            if (wall.kingdomId().equals(kingdom) && wall.dimension().equals(dimension) && wall.placedHere(at)) {
                return true;
            }
        }
        long column = column(pos.getX(), pos.getZ());
        for (Road road : RoadWork.state(server).roadsOf(kingdom)) {
            if (!road.dimension().equals(dimension)) {
                continue;
            }
            // Worked out anew when the road got other blocks: a road laid again or widened is property as it lies now.
            RoadColumns known = ROADS.get(road.id());
            if (known == null || known.source() != road.blocks()) {
                known = new RoadColumns(road.blocks(), columns(road));
                ROADS.put(road.id(), known);
            }
            int height = known.heights().get(column);
            if (height != Integer.MIN_VALUE && Math.abs(pos.getY() - height) <= 1) {
                return true;
            }
        }
        return false;
    }

    private static long column(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFF_FFFFL);
    }

    private static Long2IntOpenHashMap columns(Road road) {
        Long2IntOpenHashMap columns = new Long2IntOpenHashMap();
        columns.defaultReturnValue(Integer.MIN_VALUE);
        for (RoadLayout.Block block : road.blocks()) {
            columns.put(column(block.x(), block.z()), block.y());
        }
        return columns;
    }

    // ---------------------------------------------------------------- breaking

    /**
     * A player without the right to build breaks a block on the land of a kingdom in peace. Called by land protection
     * before it refuses.
     */
    public static Verdict breaks(Player player, Level level, BlockPos pos, BlockState state) {
        UUID kingdom = KingdomWorldApi.ownerAt(level, pos);
        if (kingdom == null || !(level instanceof ServerLevel serverLevel) || !judged(player, level, kingdom) || keptWhole(pos, state)) {
            return Verdict.REFUSED;
        }
        ServerPlayer actor = (ServerPlayer) player;
        MinecraftServer server = actor.server;
        if (SiegeRepair.isPlacedByStranger(server, kingdom, serverLevel, pos, state) || !isProperty(server, kingdom, serverLevel, pos)) {
            // Free to break, as on nobody's land, but a frame or a painting stays protected wherever it hangs: the block
            // that holds one does not break, nor one that sand with such a thing on it rests on, or the goods in the frame
            // would fall to the breaker with no theft on any record.
            return holdsEntityAbove(serverLevel, pos) ? Verdict.REFUSED : Verdict.FREE;
        }
        if (!LawWork.isCrime(server, actor, kingdom, CrimeKind.VANDALISM, Permission.BUILD, false)) {
            // Somebody the law of this kingdom does not judge and who holds no right to build: as before.
            return Verdict.REFUSED;
        }
        int room = SiegeRepair.room(server, kingdom);
        if (room <= 0 && !SiegeRepair.isRecorded(server, kingdom, serverLevel, pos)) {
            // The book of damage is full or cannot be written: what cannot be written down is not broken.
            return Verdict.REFUSED;
        }
        long worth = 0;
        // Nothing drops while the block and what stood on it leave the world: the game keeps drops back while blocks are
        // being restored, and that is what this is, the other way round. What comes back for free must not be carried
        // away, or every block would be endless.
        boolean restoring = serverLevel.restoringBlockSnapshots;
        serverLevel.restoringBlockSnapshots = true;
        try {
            List<Gone> gone = takeOut(serverLevel, pos, state);
            if (gone == null) {
                return Verdict.REFUSED;
            }
            int fresh = 0;
            for (Gone block : gone) {
                fresh += SiegeRepair.isRecorded(server, kingdom, serverLevel, block.pos()) ? 0 : 1;
            }
            if (fresh > room) {
                putBack(serverLevel, gone);
                return Verdict.REFUSED;
            }
            // Written down in the order they come back: the block first, then what stood on it.
            for (Gone block : gone) {
                SiegeRepair.damaged(server, kingdom, serverLevel, block.pos(), block.state());
                worth = io.github.sekerator.kingdomworld.core.economy.Money.addCapped(worth,
                        Math.max(1, LawPunish.worth(server, kingdom, new ItemStack(block.state().getBlock().asItem()))));
            }
            // Only now the neighbours hear of it: fences let go, water flows in, signals change.
            for (Gone block : gone) {
                serverLevel.markAndNotifyBlock(block.pos(), serverLevel.getChunkAt(block.pos()), block.state(),
                        serverLevel.getBlockState(block.pos()), net.minecraft.world.level.block.Block.UPDATE_ALL, 512);
            }
        } finally {
            serverLevel.restoringBlockSnapshots = restoring;
        }
        serverLevel.levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(state));
        serverLevel.gameEvent(net.minecraft.world.level.gameevent.GameEvent.BLOCK_DESTROY, pos,
                net.minecraft.world.level.gameevent.GameEvent.Context.of(actor, state));
        long now = LawWork.now(server);
        LAST.put(kingdom, new Damage(now, actor.getUUID()));
        Pending pending = PENDING.get(actor.getUUID());
        if (pending != null && !pending.kingdom.equals(kingdom)) {
            report(actor, pending);
            pending = null;
        }
        if (pending == null) {
            pending = new Pending(kingdom, pos.immutable());
            PENDING.put(actor.getUUID(), pending);
        }
        pending.worth = io.github.sekerator.kingdomworld.core.economy.Money.addCapped(pending.worth, worth);
        pending.last = pos.immutable();
        hint(actor, kingdom, now);
        return Verdict.DAMAGED;
    }

    /**
     * A block taken out of the world, as it stood.
     */
    private record Gone(BlockPos pos, BlockState state) {
    }

    /**
     * Takes a block out of the world with everything that cannot stay without it: what stands, hangs or leans on it (a
     * torch, a rail, a carpet, a door, a crop), the other half of a block of two, what would fall (sand, gravel), and so
     * on along the chain. Each is set to air (or its water) without a word to its neighbours, so nothing pops off and
     * nothing falls; the caller tells the neighbours once all is out.
     *
     * @return the blocks taken out, the broken one first and each support before what stood on it; {@code null}, with
     *         everything put back, when the block may not break: a frame or a painting hangs on one of them, one of them
     *         keeps data or never breaks, or there are more than {@link #CASCADE}
     */
    @org.jetbrains.annotations.Nullable
    private static List<Gone> takeOut(ServerLevel level, BlockPos pos, BlockState state) {
        if (holdsEntity(level, pos)) {
            return null;
        }
        List<Gone> gone = new java.util.ArrayList<>();
        remove(level, pos, state, gone);
        for (int i = 0; i < gone.size(); i++) {
            Gone from = gone.get(i);
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                BlockPos next = from.pos().relative(direction);
                BlockState there = level.getBlockState(next);
                if (there.isAir() || !goesWith(level, from, direction, next, there)) {
                    continue;
                }
                if (gone.size() >= CASCADE || keptWhole(next, there) || holdsEntity(level, next)) {
                    putBack(level, gone);
                    return null;
                }
                remove(level, next, there, gone);
            }
        }
        return gone;
    }

    /**
     * Whether a block cannot stay once its neighbour is out: it is the other half of it, it would fall into the hole, or
     * the game says it does not survive there.
     */
    private static boolean goesWith(ServerLevel level, Gone from, net.minecraft.core.Direction direction, BlockPos next, BlockState there) {
        var half = net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF;
        if (from.state().hasProperty(half) && there.getBlock() == from.state().getBlock() && there.hasProperty(half)
                && direction == (from.state().getValue(half) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                ? net.minecraft.core.Direction.UP : net.minecraft.core.Direction.DOWN)) {
            return true;
        }
        if (direction == net.minecraft.core.Direction.UP && there.getBlock() instanceof net.minecraft.world.level.block.FallingBlock) {
            return true;
        }
        return !there.canSurvive(level, next);
    }

    private static void remove(ServerLevel level, BlockPos pos, BlockState state, List<Gone> gone) {
        gone.add(new Gone(pos.immutable(), state));
        level.setBlock(pos, state.getFluidState().createLegacyBlock(), SILENT);
    }

    private static void putBack(ServerLevel level, List<Gone> gone) {
        for (int i = gone.size() - 1; i >= 0; i--) {
            level.setBlock(gone.get(i).pos(), gone.get(i).state(), SILENT);
        }
    }

    /**
     * Whether a frame, a painting or a knot of a lead holds on to a block: such a block does not break, or the thing would
     * fall with what is in it.
     */
    private static boolean holdsEntity(ServerLevel level, BlockPos pos) {
        net.minecraft.world.phys.AABB block = new net.minecraft.world.phys.AABB(pos);
        return !level.getEntitiesOfClass(net.minecraft.world.entity.decoration.HangingEntity.class, block.inflate(1.0), hanging -> {
            if (hanging.getPos().equals(pos)) {
                return true;
            }
            // Pushed half a block into the wall it hangs on, the thing reaches every block that holds it.
            net.minecraft.core.Vec3i into = hanging.getDirection().getOpposite().getNormal();
            return hanging.getBoundingBox().move(into.getX() * 0.5, into.getY() * 0.5, into.getZ() * 0.5).intersects(block.deflate(0.01));
        }).isEmpty();
    }

    /**
     * Whether a frame or a painting holds on to a block, or to the sand and gravel that lie on it and would fall with it.
     */
    private static boolean holdsEntityAbove(ServerLevel level, BlockPos pos) {
        if (holdsEntity(level, pos)) {
            return true;
        }
        BlockPos.MutableBlockPos above = pos.mutable().move(net.minecraft.core.Direction.UP);
        for (int i = 0; i < CASCADE && level.getBlockState(above).getBlock() instanceof net.minecraft.world.level.block.FallingBlock; i++) {
            if (holdsEntity(level, above)) {
                return true;
            }
            above.move(net.minecraft.core.Direction.UP);
        }
        return false;
    }

    private static void hint(ServerPlayer actor, UUID kingdom, long now) {
        Long last = HINTS.get(actor.getUUID());
        if (last != null && now - last < HINT_TICKS && now >= last || actor.connection == null) {
            return;
        }
        HINTS.put(actor.getUUID(), now);
        actor.displayClientMessage(Component.translatable(LawWork.KEY + "vandalism_hint", LawWork.kingdomName(actor.server, kingdom, ""))
                .withStyle(ChatFormatting.GOLD), true);
    }

    private static void report(ServerPlayer actor, Pending pending) {
        if (pending.worth > 0) {
            LawWork.deed(actor, pending.last, pending.kingdom, CrimeKind.VANDALISM, pending.worth, "", Permission.BUILD, false, null);
        }
    }

    /**
     * Tells the law now of the damage a player did a moment ago, for the player who leaves and for checks.
     */
    public static void reportNow(ServerPlayer player) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending != null) {
            report(player, pending);
        }
    }

    // ---------------------------------------------------------------- placing

    /**
     * Whether a player without the right to build may place this block on the land of a kingdom in peace.
     */
    public static boolean mayPlace(Player player, Level level, BlockPos pos, BlockState placed) {
        UUID kingdom = KingdomWorldApi.ownerAt(level, pos);
        return kingdom != null && level instanceof ServerLevel && judged(player, level, kingdom) && plain(placed);
    }

    /**
     * Whether a player without the right to build may use the item in hand to place a block at a place: only items that
     * are plain blocks. Containers and machines, everything of redstone, buckets, fire, tools, and items that become
     * entities (minecarts, boats, armor stands, frames, crystals) stay refused.
     */
    public static boolean mayPlace(Player player, Level level, BlockPos pos, ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && mayPlace(player, level, pos, item.getBlock().defaultBlockState());
    }

    /**
     * A player without the right to build placed a block on the land of a kingdom in peace. Called by land protection
     * before it refuses.
     *
     * @param before what stood there
     * @return whether the block may stay for now
     */
    public static boolean places(Player player, Level level, BlockPos pos, BlockState before, BlockState placed) {
        if (!mayPlace(player, level, pos, placed)) {
            return false;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        ServerPlayer actor = (ServerPlayer) player;
        MinecraftServer server = actor.server;
        UUID kingdom = KingdomWorldApi.ownerAt(level, pos);
        if (isProperty(server, kingdom, serverLevel, pos)) {
            // In a city the block of a stranger does not stay: it is taken out when the place is put in order. A block
            // that cannot be written down (the book of damage is full or cannot be written) is not placed at all.
            if (!SiegeRepair.placed(server, kingdom, serverLevel, pos, before, placed)) {
                return false;
            }
            LAST.put(kingdom, new Damage(LawWork.now(server), actor.getUUID()));
        }
        return true;
    }

    // ---------------------------------------------------------------- putting back

    /**
     * Whether what was damaged in a kingdom in peace may be put back now: nothing new for
     * {@link LawRules#restoreQuietTicks}, or whoever did the last damage has left the land of the kingdom.
     */
    public static boolean settled(MinecraftServer server, UUID kingdom, long now) {
        Damage last = LAST.get(kingdom);
        if (last == null || now < last.at() || now - last.at() >= LawSettings.rules().restoreQuietTicks) {
            return true;
        }
        if (now - last.at() < GONE_TICKS) {
            return false;
        }
        ServerPlayer offender = server.getPlayerList().getPlayer(last.offender());
        return offender == null || !offender.isAlive() || !kingdom.equals(KingdomWorldApi.ownerAt(offender.level(), offender.blockPosition()));
    }

    // ---------------------------------------------------------------- once a second

    static void tick(MinecraftServer server, long now) {
        if (!PENDING.isEmpty()) {
            for (Map.Entry<UUID, Pending> entry : List.copyOf(PENDING.entrySet())) {
                PENDING.remove(entry.getKey());
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    report(player, entry.getValue());
                }
            }
        }
        if (!ROADS.isEmpty() && now % 1_200 < 20) {
            ROADS.keySet().removeIf(id -> RoadWork.state(server).roads().stream().noneMatch(road -> road.id() == id));
        }
        if (now % 1_200 < 20) {
            HINTS.values().removeIf(at -> now - at > HINT_TICKS || now < at);
            LAST.entrySet().removeIf(entry -> now - entry.getValue().at() > 24_000 || now < entry.getValue().at());
        }
    }
}
