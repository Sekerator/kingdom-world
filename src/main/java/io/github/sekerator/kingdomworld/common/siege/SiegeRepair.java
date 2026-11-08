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
 */
public final class SiegeRepair {
    /** Blocks put back for one kingdom in a second. */
    static final int PER_SECOND = 4;
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
     * What a block at a place of a kingdom belongs to: a building of the register (its id), a city wall (0), or nothing.
     */
    static Optional<Long> registered(MinecraftServer server, UUID kingdom, String dimension, BlockPos pos) {
        for (Building building : KingdomWorldApi.state(server).construction().buildingsOf(kingdom)) {
            if (building.anchor().dimension().equals(dimension) && building.box() != null
                    && building.box().contains(pos.getX(), pos.getY(), pos.getZ())) {
                return Optional.of(building.id());
            }
        }
        for (Wall wall : RoadWork.state(server).walls()) {
            if (wall.kingdomId().equals(kingdom) && wall.dimension().equals(dimension) && wall.placedHere(pos.asLong())) {
                return Optional.of(0L);
            }
        }
        return Optional.empty();
    }

    /**
     * Writes down a block broken in a war, if it belongs to a building or a wall of the kingdom.
     *
     * @return whether it was written down
     */
    public static boolean broken(MinecraftServer server, UUID kingdom, ServerLevel level, BlockPos pos, BlockState state, long war) {
        if (state.isAir() || SiegeGates.isLeafPlace(pos)) {
            return false;
        }
        String dimension = Dimensions.id(level);
        Optional<Long> building = registered(server, kingdom, dimension, pos);
        if (building.isEmpty()) {
            return false;
        }
        Item item = state.getBlock().asItem();
        String itemId = item == Items.AIR ? "" : BuiltInRegistries.ITEM.getKey(item).toString();
        SiegeState siege = SiegeWork.state(server);
        boolean added = siege.damage().record(new WarDamage.Entry(war, kingdom, dimension, pos.asLong(), NbtUtils.writeBlockState(state).toString(),
                itemId, building.get(), KingdomWorldApi.gameTime(server)));
        // The enemy is at the city: the builders wait until it has been gone a while.
        siege.enemyNear(kingdom, KingdomWorldApi.gameTime(server));
        siege.changed();
        return added;
    }

    // ---------------------------------------------------------------- putting back

    static void tick(MinecraftServer server, SiegeRules rules, long now) {
        SiegeState state = SiegeWork.state(server);
        WarDamage damage = state.damage();
        for (UUID kingdom : List.copyOf(damage.all().keySet())) {
            if (KingdomWorldApi.kingdom(server, kingdom).isEmpty()) {
                damage.forget(kingdom);
                state.changed();
                continue;
            }
            boolean atWar = WarWork.state(server).fightsAnyWar(kingdom);
            boolean quiet = rules.repairQuietTicks() > 0 && now - state.lastEnemyNear(kingdom) >= rules.repairQuietTicks();
            if (atWar && !quiet) {
                continue;
            }
            mend(server, kingdom, PER_SECOND, now);
        }
    }

    /**
     * Puts back up to some broken blocks of a kingdom, paid from its storehouses.
     *
     * @return the blocks put back
     */
    static int mend(MinecraftServer server, UUID kingdom, int most, long now) {
        SiegeState state = SiegeWork.state(server);
        WarDamage damage = state.damage();
        Map<String, Integer> missing = new LinkedHashMap<>();
        int done = 0;
        boolean changed = false;
        for (WarDamage.Entry entry : damage.of(kingdom)) {
            if (done >= most) {
                break;
            }
            ServerLevel level = SiegeWork.level(server, entry.dimension());
            BlockPos pos = BlockPos.of(entry.pos());
            if (level == null || !level.isLoaded(pos)) {
                continue;
            }
            BlockState wanted = parse(level, entry.state());
            BlockState there = level.getBlockState(pos);
            if (wanted == null || there.equals(wanted)) {
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
