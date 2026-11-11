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
 * follows the worth of the blocks, and blocks broken one after another make one crime. A block a stranger places there is
 * written down too and taken out again.</li>
 * <li>On the rest of the land blocks are broken and placed freely: no crime, nothing put back.</li>
 * </ul>
 *
 * <p>Still refused in peace: the land of a kingdom of players none of whom is there, the throne, the working blocks of the
 * mod, workplaces, containers, beds and every other block that keeps data, the leaves of city gates, and placing pistons,
 * explosives and fire. Nothing here asks about war: land open by war never comes this far.
 */
public final class LawVandalism {
    /** Ticks between two hints that a broken block was property. */
    private static final long HINT_TICKS = 600;
    /** Ticks after its last damage an offender who left the land of a kingdom is taken to be gone. */
    private static final long GONE_TICKS = 100;

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
    /** The columns of each road with the height of the road there, by the id of the road. */
    private static final Map<Long, Long2IntOpenHashMap> ROADS = new HashMap<>();

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
     * Whether a block may not be placed by a stranger: it moves, blows up or burns what is not its own.
     */
    static boolean dangerous(BlockState state) {
        return state.getBlock() instanceof PistonBaseBlock || state.getBlock() instanceof TntBlock || state.getBlock() instanceof BaseFireBlock
                || !state.getFluidState().isEmpty() && state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock;
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
            Long2IntOpenHashMap columns = ROADS.computeIfAbsent(road.id(), id -> columns(road));
            int height = columns.get(column);
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
            return Verdict.FREE;
        }
        if (!LawWork.isCrime(server, actor, kingdom, CrimeKind.VANDALISM, Permission.BUILD, false)) {
            // Somebody the law of this kingdom does not judge and who holds no right to build: as before.
            return Verdict.REFUSED;
        }
        long worth = Math.max(1, LawPunish.worth(server, kingdom, new ItemStack(state.getBlock().asItem())));
        SiegeRepair.damaged(server, kingdom, serverLevel, pos, state);
        // Gone without drops: what comes back for free must not be carried away, or every block would be endless.
        serverLevel.destroyBlock(pos, false, actor);
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
        return kingdom != null && level instanceof ServerLevel && judged(player, level, kingdom) && !dangerous(placed);
    }

    /**
     * Whether a player without the right to build may use the item in hand to place a block at a place: only items that
     * are blocks, and no dangerous ones. Buckets, fire, tools and everything else stay refused.
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
            // In a city the block of a stranger does not stay: it is taken out when the place is put in order.
            SiegeRepair.placed(server, kingdom, serverLevel, pos, before, placed);
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
