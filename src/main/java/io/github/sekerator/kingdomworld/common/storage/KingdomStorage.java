package io.github.sekerator.kingdomworld.common.storage;

import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.core.citizen.StorehouseRules;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * The storehouses of a kingdom as one store: every storehouse standing on the kingdom's land counts, wherever it
 * is and whether its chunk is loaded or not. Citizens eat from here and farmers bring their harvest here; later
 * mechanics (carriers, production chains, the king's orders) search, take and put items through the same
 * methods.
 *
 * <p>Server thread only. While the kingdom data is read-only nothing is taken or put, because the change could
 * not be saved.
 */
public final class KingdomStorage {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** The storehouses of every kingdom, as sorted out in {@link #byKingdom}. */
    private static Map<UUID, List<StorehouseInventory>> sorted = Map.of();
    private static StorehouseRegistry sortedFrom;
    private static long sortedVersion = -1;
    private static long sortedTick = -1;
    private static long sortedLand = Long.MIN_VALUE;

    private KingdomStorage() {
    }

    /**
     * The kingdom a storehouse belongs to: the owner of the land it stands on, but nobody in the wilds of a kingdom of the
     * world (a storehouse of a player there is not one of the kingdom). The one place where this is decided.
     */
    @Nullable
    public static UUID owner(TerritoryIndex territory, WorldPos pos) {
        UUID owner = territory.ownerOf(pos.dimension(), pos.chunk());
        if (owner == null || territory.claimOwner(pos.dimension(), pos.chunk()) != null) {
            return owner;
        }
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        return server != null && io.github.sekerator.kingdomworld.common.land.Wilderness.isWild(server, pos.dimension(), pos.chunk()) ? null : owner;
    }

    /**
     * The storehouses of every kingdom, in the order of the register: sorted out once a tick, and again when a storehouse
     * is made or taken away.
     */
    private static Map<UUID, List<StorehouseInventory>> byKingdom(MinecraftServer server, KingdomSavedData data) {
        StorehouseRegistry registry = data.storehouses();
        long tick = server.getTickCount();
        TerritoryIndex territory = data.state().territory();
        long land = landPrint(territory);
        if (registry != sortedFrom || registry.version() != sortedVersion || tick != sortedTick || land != sortedLand) {
            Map<UUID, List<StorehouseInventory>> result = new HashMap<>();
            for (StorehouseInventory inventory : registry.all()) {
                UUID owner = owner(territory, inventory.pos());
                if (owner != null) {
                    result.computeIfAbsent(owner, id -> new ArrayList<>()).add(inventory);
                }
            }
            sorted = result;
            sortedFrom = registry;
            sortedVersion = registry.version();
            sortedTick = tick;
            sortedLand = land;
        }
        return sorted;
    }

    /**
     * A mark of the land of all kingdoms: every claim made, given up or moved and every province changing hands changes it, so
     * that a storehouse whose land changed in the middle of a tick is sorted out anew.
     */
    private static long landPrint(TerritoryIndex territory) {
        long provinces = territory.provinceMap().map(map -> map.version()).orElse(0L);
        return territory.version() * 31 + provinces;
    }

    /**
     * Storehouses on the kingdom's land; nearest to {@code near} first if it is given.
     */
    public static List<StorehouseInventory> storehouses(MinecraftServer server, UUID kingdomId, @Nullable WorldPos near) {
        KingdomSavedData data = KingdomSavedData.get(server);
        List<StorehouseInventory> result = new ArrayList<>(byKingdom(server, data).getOrDefault(kingdomId, List.of()));
        // The containers of the warehouses of the kingdom count as storehouses while their place is loaded.
        result.addAll(io.github.sekerator.kingdomworld.common.construction.BuildingEffects.containers(server, kingdomId));
        if (near != null) {
            result.sort(Comparator.comparingDouble(inventory -> near.distanceSqr(inventory.pos())));
        }
        return result;
    }

    /**
     * How many items matching the filter the kingdom has.
     */
    public static long count(MinecraftServer server, UUID kingdomId, Predicate<ItemStack> filter) {
        long total = 0;
        for (StorehouseInventory inventory : storehouses(server, kingdomId, null)) {
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (!stack.isEmpty() && filter.test(stack)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    /**
     * Whether the kingdom has an item matching the filter.
     */
    public static boolean contains(MinecraftServer server, UUID kingdomId, Predicate<ItemStack> filter) {
        for (StorehouseInventory inventory : storehouses(server, kingdomId, null)) {
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (!stack.isEmpty() && filter.test(stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Takes up to {@code amount} items matching the filter, from the storehouses nearest to {@code near} first.
     *
     * @return the stacks taken; empty if nothing matched or the data is read-only
     */
    public static List<ItemStack> take(MinecraftServer server, UUID kingdomId, Predicate<ItemStack> filter, int amount,
                                       @Nullable WorldPos near) {
        List<ItemStack> taken = new ArrayList<>();
        if (amount <= 0 || KingdomSavedData.get(server).isReadOnly()) {
            return taken;
        }
        int left = amount;
        for (StorehouseInventory inventory : storehouses(server, kingdomId, near)) {
            for (int slot = 0; slot < inventory.getSlots() && left > 0; slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack.isEmpty() || !filter.test(stack)) {
                    continue;
                }
                ItemStack extracted = inventory.extractItem(slot, Math.min(left, stack.getCount()), false);
                left -= extracted.getCount();
                merge(taken, extracted);
            }
            if (left == 0) {
                break;
            }
        }
        return taken;
    }

    /**
     * Puts a stack that comes in otherwise than from the work of a citizen (the market, caravans, trophies, taxes, the gear of the
     * dead) into the kingdom's storehouses: the common ones first, nearest to {@code near} first, then any other with room, so that
     * nothing is lost.
     *
     * @return what did not fit; the whole stack if the kingdom has no storehouse or the data is read-only
     */
    public static ItemStack insert(MinecraftServer server, UUID kingdomId, ItemStack stack, @Nullable WorldPos near) {
        if (stack.isEmpty() || KingdomSavedData.get(server).isReadOnly()) {
            return stack;
        }
        return insertInto(StorehouseRules.income(candidates(storehouses(server, kingdomId, null)), x(near), y(near), z(near)), stack);
    }

    /**
     * Puts what a worker of a profession gathered or made into the kingdom's storehouses: those of its profession first, then the
     * common ones, never those given to other professions; nearest to {@code near} first.
     *
     * @return what did not fit; the whole stack if no storehouse takes it in or the data is read-only
     */
    public static ItemStack insertFor(MinecraftServer server, UUID kingdomId, String profession, ItemStack stack, @Nullable WorldPos near) {
        if (stack.isEmpty() || KingdomSavedData.get(server).isReadOnly()) {
            return stack;
        }
        return insertInto(StorehouseRules.delivery(candidates(storehouses(server, kingdomId, null)), profession, null, x(near), y(near),
                z(near)), stack);
    }

    private static ItemStack insertInto(List<StorehouseInventory> order, ItemStack stack) {
        ItemStack rest = stack.copy();
        for (StorehouseInventory inventory : order) {
            rest = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(inventory, rest, false);
            if (rest.isEmpty()) {
                break;
            }
        }
        return rest;
    }

    /**
     * The storehouses as the rules of {@link StorehouseRules} see them; those that take nothing in (containers of warehouses whose
     * place is not loaded) have no room.
     */
    static List<StorehouseRules.Candidate<StorehouseInventory>> candidates(List<StorehouseInventory> inventories) {
        List<StorehouseRules.Candidate<StorehouseInventory>> result = new ArrayList<>(inventories.size());
        for (StorehouseInventory inventory : inventories) {
            WorldPos pos = inventory.pos();
            result.add(new StorehouseRules.Candidate<>(inventory, pos.dimension(), pos.x(), pos.y(), pos.z(), acceptsOf(inventory),
                    inventory.takesIn()));
        }
        return result;
    }

    /**
     * Whom a storehouse takes in from, of the professions the game knows now: a profession of a mod that was taken away does not
     * shut the storehouse, and one given only such professions takes in from everybody.
     */
    public static StorehouseRules.Accepts acceptsOf(StorehouseInventory inventory) {
        StorehouseRules.Accepts accepts = inventory.accepts();
        if (accepts.all()) {
            return accepts;
        }
        List<String> known = new ArrayList<>();
        for (String profession : accepts.professions()) {
            if (io.github.sekerator.kingdomworld.core.citizen.Professions.REGISTRY.isKnown(profession)) {
                known.add(profession);
            }
        }
        return known.size() == accepts.professions().size() ? accepts : StorehouseRules.Accepts.of(known);
    }

    /**
     * Whether a storehouse of the profession or a common one has room for some of the stacks (any empty slot when no stacks are
     * given).
     */
    public static boolean hasRoomFor(MinecraftServer server, UUID kingdomId, String profession, List<ItemStack> stacks) {
        for (StorehouseInventory inventory : storehouses(server, kingdomId, null)) {
            if (inventory.takesIn() && acceptsOf(inventory).kindFor(profession) != StorehouseRules.Kind.OTHERS
                    && hasRoomFor(inventory, stacks)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a worker of a profession has a storehouse to bring things to at all: one of its profession or a common one, wherever
     * it stands. Work far from players that has none stands still rather than gather what would be lost.
     */
    public static boolean hasStorehouseFor(MinecraftServer server, UUID kingdomId, String profession) {
        for (StorehouseInventory inventory : storehouses(server, kingdomId, null)) {
            if (inventory.takesIn() && acceptsOf(inventory).kindFor(profession) != StorehouseRules.Kind.OTHERS) {
                return true;
            }
        }
        return false;
    }

    private static int x(@Nullable WorldPos pos) {
        return pos == null ? 0 : pos.x();
    }

    private static int y(@Nullable WorldPos pos) {
        return pos == null ? 0 : pos.y();
    }

    private static int z(@Nullable WorldPos pos) {
        return pos == null ? 0 : pos.z();
    }

    // ---------------------------------------------------------------- where workers near players walk

    /**
     * How far a worker of a post walks to a storehouse near players: inside the city the post stands in, or anywhere on the land of
     * the kingdom for a post outside the cities.
     */
    public static StorehouseRules.Reach reach(MinecraftServer server, UUID kingdomId, WorldPos post) {
        return new StorehouseRules.Reach(post.dimension(), io.github.sekerator.kingdomworld.common.road.CityAreas
                .at(server, kingdomId, post.dimension(), post.x(), post.z())
                .map(area -> new StorehouseRules.Square(area.x(), area.z(), area.radius())).orElse(null));
    }

    /**
     * The storehouses (blocks of the mod whose place is loaded) a worker of a profession near players brings things to, in the
     * order it tries them: those of its profession nearest first, then the common ones, within the reach of its post; never those of
     * other professions, nor those without room for any of the stacks.
     *
     * @param stacks what the worker brings, {@code null} to leave room out of the choice
     */
    public static List<StorehouseInventory> deliveryTargets(net.minecraft.server.level.ServerLevel level, UUID kingdomId, String profession,
                                                            WorldPos post, net.minecraft.core.BlockPos from, List<ItemStack> stacks) {
        List<StorehouseRules.Candidate<StorehouseInventory>> candidates = new ArrayList<>();
        for (StorehouseInventory inventory : storehouses(level.getServer(), kingdomId, null)) {
            WorldPos pos = inventory.pos();
            net.minecraft.core.BlockPos at = new net.minecraft.core.BlockPos(pos.x(), pos.y(), pos.z());
            if (!inventory.takesIn() || !level.isLoaded(at) || !level.getBlockState(at).is(io.github.sekerator.kingdomworld.common.registry.ModRegistries.STOREHOUSE.get())) {
                continue;
            }
            candidates.add(new StorehouseRules.Candidate<>(inventory, pos.dimension(), pos.x(), pos.y(), pos.z(), acceptsOf(inventory),
                    stacks == null || hasRoomFor(inventory, stacks)));
        }
        return StorehouseRules.delivery(candidates, profession, reach(level.getServer(), kingdomId, post), from.getX(), from.getY(),
                from.getZ());
    }

    /**
     * The storehouses whose place is loaded a worker near players may take from, within the reach of its post, nearest first: any
     * storehouse, either half of a double one, the containers of warehouses.
     */
    public static List<StorehouseInventory> takingSources(net.minecraft.server.level.ServerLevel level, UUID kingdomId, WorldPos post,
                                                          net.minecraft.core.BlockPos from) {
        List<StorehouseRules.Candidate<StorehouseInventory>> candidates = new ArrayList<>();
        for (StorehouseInventory inventory : storehouses(level.getServer(), kingdomId, null)) {
            WorldPos pos = inventory.pos();
            if (inventory.takesIn() && level.isLoaded(new net.minecraft.core.BlockPos(pos.x(), pos.y(), pos.z()))) {
                candidates.add(new StorehouseRules.Candidate<>(inventory, pos.dimension(), pos.x(), pos.y(), pos.z(), acceptsOf(inventory),
                        true));
            }
        }
        return StorehouseRules.taking(candidates, reach(level.getServer(), kingdomId, post), from.getX(), from.getY(), from.getZ());
    }

    /**
     * Whether a storehouse has room for some of the stacks: an empty slot, or a slot with the same item that is not full.
     */
    public static boolean hasRoomFor(StorehouseInventory inventory, List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            return freeSlots(inventory) > 0;
        }
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(inventory, stack.copyWithCount(1), true).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Takes up to {@code amount} items matching the filter out of one storehouse.
     *
     * @return the stacks taken; empty if nothing matched or the data is read-only
     */
    public static List<ItemStack> takeFrom(MinecraftServer server, StorehouseInventory inventory, Predicate<ItemStack> filter, int amount) {
        List<ItemStack> taken = new ArrayList<>();
        if (amount <= 0 || KingdomSavedData.get(server).isReadOnly()) {
            return taken;
        }
        int left = amount;
        for (int slot = 0; slot < inventory.getSlots() && left > 0; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty() || !filter.test(stack)) {
                continue;
            }
            ItemStack extracted = inventory.extractItem(slot, Math.min(left, stack.getCount()), false);
            left -= extracted.getCount();
            merge(taken, extracted);
        }
        return taken;
    }

    /**
     * A stack in a storehouse.
     */
    public record Found(StorehouseInventory inventory, int slot, ItemStack stack) {
    }

    /**
     * Every stack matching the filter, from the storehouses nearest to {@code near} first. The stacks are the
     * stored ones; take items with {@link #extract}.
     */
    public static List<Found> find(MinecraftServer server, UUID kingdomId, Predicate<ItemStack> filter, @Nullable WorldPos near) {
        List<Found> result = new ArrayList<>();
        for (StorehouseInventory inventory : storehouses(server, kingdomId, near)) {
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (!stack.isEmpty() && filter.test(stack)) {
                    result.add(new Found(inventory, slot, stack));
                }
            }
        }
        return result;
    }

    /**
     * Takes up to {@code amount} items of a found stack.
     *
     * @return what was taken; empty if the data is read-only
     */
    public static ItemStack extract(MinecraftServer server, Found found, int amount) {
        if (amount <= 0 || KingdomSavedData.get(server).isReadOnly()) {
            return ItemStack.EMPTY;
        }
        return found.inventory().extractItem(found.slot(), amount, false);
    }

    // ---------------------------------------------------------------- taking several things at once

    /**
     * Part of a stack to take.
     */
    public record Take(Found found, int count) {
    }

    /**
     * Counts of every kind of item in the kingdom's storehouses, by item id, in one pass.
     */
    public static Map<String, Long> countsById(MinecraftServer server, UUID kingdomId) {
        Map<String, Long> counts = new HashMap<>();
        for (StorehouseInventory inventory : storehouses(server, kingdomId, null)) {
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    counts.merge(itemId(stack), (long) stack.getCount(), Long::sum);
                }
            }
        }
        return counts;
    }

    /**
     * Plans to take items of several kinds, by item id, without taking anything yet: the storehouses nearest to
     * {@code near} first. Items others set aside stay: of every kind at least {@code reserved} must be left.
     *
     * @return what to take, or {@code null} if not all of it is there (nothing is changed either way)
     */
    @Nullable
    public static List<Take> plan(MinecraftServer server, UUID kingdomId, Map<String, Integer> wanted, ToIntFunction<String> reserved,
                                  @Nullable WorldPos near) {
        Map<String, Integer> left = new HashMap<>();
        wanted.forEach((item, count) -> {
            if (count > 0) {
                left.merge(item, count, Integer::sum);
            }
        });
        Map<String, Long> counts = countsById(server, kingdomId);
        for (Map.Entry<String, Integer> entry : left.entrySet()) {
            if (counts.getOrDefault(entry.getKey(), 0L) - Math.max(0, reserved.applyAsInt(entry.getKey())) < entry.getValue()) {
                return null;
            }
        }
        List<Take> takes = new ArrayList<>();
        for (Found found : find(server, kingdomId, stack -> left.getOrDefault(itemId(stack), 0) > 0, near)) {
            String id = itemId(found.stack());
            int still = left.getOrDefault(id, 0);
            if (still <= 0) {
                continue;
            }
            int count = Math.min(still, found.stack().getCount());
            takes.add(new Take(found, count));
            left.put(id, still - count);
        }
        for (int still : left.values()) {
            if (still > 0) {
                return null;
            }
        }
        return takes;
    }

    /**
     * Takes what a plan says, right after it was made (in the same tick), so that nothing changed in between.
     *
     * @return the stacks taken, or {@code null} if the storehouses no longer hold what was planned; then everything
     * taken so far went back
     */
    @Nullable
    public static List<ItemStack> takePlanned(MinecraftServer server, List<Take> takes) {
        if (KingdomSavedData.get(server).isReadOnly()) {
            return null;
        }
        List<ItemStack> taken = new ArrayList<>();
        for (int i = 0; i < takes.size(); i++) {
            Take take = takes.get(i);
            ItemStack expected = take.found().stack();
            ItemStack inSlot = take.found().inventory().getStackInSlot(take.found().slot());
            ItemStack stack = inSlot.getCount() >= take.count() && ItemStack.isSameItemSameTags(inSlot, expected)
                    ? take.found().inventory().extractItem(take.found().slot(), take.count(), false) : ItemStack.EMPTY;
            if (stack.getCount() != take.count()) {
                // Something changed: everything taken goes back into the very slot it came from, in the same tick.
                if (!stack.isEmpty()) {
                    putBack(server, take, stack);
                }
                for (int j = i - 1; j >= 0; j--) {
                    putBack(server, takes.get(j), taken.get(j));
                }
                return null;
            }
            taken.add(stack);
        }
        return taken;
    }

    /**
     * Puts a stack taken by a plan back where it was taken from: into its slot, which was emptied of it in the same tick, else into
     * the same storehouse, and only if neither can take it into the other storehouses of the kingdom.
     */
    private static void putBack(MinecraftServer server, Take take, ItemStack stack) {
        StorehouseInventory inventory = take.found().inventory();
        ItemStack rest = inventory.insertItem(take.found().slot(), stack, false);
        if (!rest.isEmpty()) {
            rest = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(inventory, rest, false);
        }
        if (!rest.isEmpty()) {
            WorldPos at = inventory.pos();
            UUID owner = KingdomSavedData.get(server).state().territory().ownerOf(at.dimension(), at.chunk());
            if (owner != null) {
                rest = insert(server, owner, rest, at);
            }
        }
        if (!rest.isEmpty()) {
            // No storehouse has room for it: it lies at the storehouse it came from rather than be lost.
            WorldPos at = inventory.pos();
            net.minecraft.server.level.ServerLevel level = io.github.sekerator.kingdomworld.common.citizen.work.PostWork.level(server, at);
            net.minecraft.core.BlockPos place = new net.minecraft.core.BlockPos(at.x(), at.y(), at.z());
            if (level != null && level.isLoaded(place)) {
                net.minecraft.world.Containers.dropItemStack(level, at.x() + 0.5, at.y() + 1.0, at.z() + 0.5, rest);
            } else {
                LOGGER.warn("{} taken by a plan that could not be carried out found no room to go back to at {}; it is lost", rest,
                        at.coordinates());
            }
        }
    }

    /**
     * Whether all the stacks would fit into the kingdom's storehouses together.
     */
    public static boolean fits(MinecraftServer server, UUID kingdomId, List<ItemStack> stacks) {
        List<StorehouseInventory> inventories = new ArrayList<>(storehouses(server, kingdomId, null));
        inventories.removeIf(inventory -> !inventory.takesIn());
        return fitsInto(inventories, stacks);
    }

    /**
     * Whether all the stacks a worker of a profession made would fit together into the storehouses it brings things to: those of
     * its profession and the common ones.
     */
    public static boolean fitsFor(MinecraftServer server, UUID kingdomId, String profession, List<ItemStack> stacks) {
        return fitsFor(server, kingdomId, profession, stacks, null);
    }

    /**
     * Whether all the stacks a worker of a profession made would fit together into a container of its own first and then the
     * storehouses it brings things to.
     *
     * @param first the container the stacks go into before the storehouses, {@code null} for none
     */
    public static boolean fitsFor(MinecraftServer server, UUID kingdomId, String profession, List<ItemStack> stacks,
                                  @Nullable net.minecraftforge.items.IItemHandler first) {
        List<net.minecraftforge.items.IItemHandler> order = new ArrayList<>();
        if (first != null) {
            order.add(first);
        }
        order.addAll(StorehouseRules.delivery(candidates(storehouses(server, kingdomId, null)), profession, null, 0, 0, 0));
        return fitsInto(order, stacks);
    }

    /**
     * Whether all the stacks fit into the storehouses together, counted without copying them: what is left of each kind of item
     * goes into the stacks of the same item that are not full, and the rest needs empty slots. The storehouses are looked through
     * one after another, and the count stops as soon as everything has room.
     */
    static boolean fitsInto(List<? extends net.minecraftforge.items.IItemHandler> inventories, List<ItemStack> stacks) {
        // What is to be put away, one entry for each kind of item, and how many of it are left.
        List<ItemStack> kinds = new ArrayList<>();
        List<Integer> left = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            int same = -1;
            for (int i = 0; i < kinds.size() && same < 0; i++) {
                if (ItemStack.isSameItemSameTags(kinds.get(i), stack)) {
                    same = i;
                }
            }
            if (same < 0) {
                kinds.add(stack);
                left.add(stack.getCount());
            } else {
                left.set(same, left.get(same) + stack.getCount());
            }
        }
        if (kinds.isEmpty()) {
            return true;
        }
        int empty = 0;
        for (net.minecraftforge.items.IItemHandler inventory : inventories) {
            if (inventory instanceof StorehouseInventory storehouse && !storehouse.takesIn()) {
                continue;
            }
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack there = inventory.getStackInSlot(slot);
                if (there.isEmpty()) {
                    empty++;
                    continue;
                }
                for (int i = 0; i < kinds.size(); i++) {
                    if (left.get(i) > 0 && ItemStack.isSameItemSameTags(kinds.get(i), there)) {
                        int room = Math.min(inventory.getSlotLimit(slot), there.getMaxStackSize()) - there.getCount();
                        if (room > 0) {
                            left.set(i, Math.max(0, left.get(i) - room));
                        }
                        break;
                    }
                }
            }
            if (slotsNeeded(kinds, left) <= empty) {
                return true;
            }
        }
        return false;
    }

    /**
     * Empty slots the items left need: full stacks of each kind of item, and one more for the rest.
     */
    private static int slotsNeeded(List<ItemStack> kinds, List<Integer> left) {
        int slots = 0;
        for (int i = 0; i < kinds.size(); i++) {
            int size = Math.max(1, Math.min(64, kinds.get(i).getMaxStackSize()));
            slots += (left.get(i) + size - 1) / size;
        }
        return slots;
    }

    public static String itemId(ItemStack stack) {
        net.minecraft.resources.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    /**
     * Empty slots of a storehouse.
     */
    public static int freeSlots(StorehouseInventory inventory) {
        if (!inventory.takesIn()) {
            return 0;
        }
        int free = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (inventory.getStackInSlot(slot).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    /**
     * Items in a storehouse.
     */
    public static int itemCount(StorehouseInventory inventory) {
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            count += inventory.getStackInSlot(slot).getCount();
        }
        return count;
    }

    /**
     * One kind of item and how many of it all storehouses of a kingdom hold.
     *
     * @param sample one item of the kind, with its tag
     */
    public record Total(ItemStack sample, long count) {
    }

    /**
     * What all storehouses of a kingdom hold, by kind of item (items with different tags are different kinds), most
     * first.
     */
    public static List<Total> totals(MinecraftServer server, UUID kingdomId) {
        List<ItemStack> samples = new ArrayList<>();
        List<long[]> counts = new ArrayList<>();
        java.util.Map<net.minecraft.world.item.Item, List<Integer>> byItem = new java.util.HashMap<>();
        for (StorehouseInventory inventory : storehouses(server, kingdomId, null)) {
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                List<Integer> candidates = byItem.computeIfAbsent(stack.getItem(), item -> new ArrayList<>());
                int index = -1;
                for (int candidate : candidates) {
                    if (ItemStack.isSameItemSameTags(samples.get(candidate), stack)) {
                        index = candidate;
                        break;
                    }
                }
                if (index < 0) {
                    index = samples.size();
                    samples.add(stack.copyWithCount(1));
                    counts.add(new long[1]);
                    candidates.add(index);
                }
                counts.get(index)[0] += stack.getCount();
            }
        }
        List<Total> totals = new ArrayList<>(samples.size());
        for (int i = 0; i < samples.size(); i++) {
            totals.add(new Total(samples.get(i), counts.get(i)[0]));
        }
        totals.sort(Comparator.comparingLong(Total::count).reversed());
        return totals;
    }

    // ---------------------------------------------------------------- food

    /**
     * Food points one item gives a citizen, or 0 if citizens do not eat it: anything with food properties
     * counts (so food of other mods is eaten too) unless it has a harmful effect or leaves a container behind.
     */
    public static int nutrition(ItemStack stack) {
        if (stack.isEmpty() || stack.hasCraftingRemainingItem()) {
            return 0;
        }
        FoodProperties food = stack.getFoodProperties(null);
        if (food == null || food.getNutrition() <= 0) {
            return 0;
        }
        for (var effect : food.getEffects()) {
            if (effect.getFirst() != null && effect.getFirst().getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                return 0;
            }
        }
        return food.getNutrition();
    }

    public static boolean isFood(ItemStack stack) {
        return nutrition(stack) > 0;
    }

    /**
     * Food points in all storehouses of the kingdom.
     */
    public static long totalNutrition(MinecraftServer server, UUID kingdomId) {
        long total = 0;
        for (StorehouseInventory inventory : storehouses(server, kingdomId, null)) {
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                int each = nutrition(stack);
                if (each > 0) {
                    total += (long) each * stack.getCount();
                }
            }
        }
        return total;
    }

    /**
     * Takes food worth at least {@code wanted} points (or as much as there is), preferring the storehouses
     * nearest to {@code near}.
     *
     * @return the food points taken
     */
    public static int eat(MinecraftServer server, UUID kingdomId, int wanted, @Nullable WorldPos near) {
        if (wanted <= 0 || KingdomSavedData.get(server).isReadOnly()) {
            return 0;
        }
        int eaten = 0;
        // Food a workshop set aside for cooking is not eaten raw.
        Map<String, Integer> reserved = new HashMap<>(
                io.github.sekerator.kingdomworld.common.production.Production.reservations().reserved(kingdomId));
        Map<String, Long> spare = new HashMap<>();
        if (!reserved.isEmpty()) {
            countsById(server, kingdomId).forEach((item, count) -> spare.put(item, count - reserved.getOrDefault(item, 0)));
        }
        for (StorehouseInventory inventory : storehouses(server, kingdomId, near)) {
            for (int slot = 0; slot < inventory.getSlots() && eaten < wanted; slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                int each = nutrition(stack);
                if (each <= 0) {
                    continue;
                }
                int items = Math.min(stack.getCount(), Math.max(1, (wanted - eaten + each - 1) / each));
                if (!reserved.isEmpty()) {
                    String id = itemId(stack);
                    long free = spare.getOrDefault(id, (long) stack.getCount());
                    items = (int) Math.min(items, Math.max(0, free));
                    if (items <= 0) {
                        continue;
                    }
                    spare.put(id, free - items);
                }
                ItemStack extracted = inventory.extractItem(slot, items, false);
                eaten += each * extracted.getCount();
            }
            if (eaten >= wanted) {
                break;
            }
        }
        return eaten;
    }

    private static void merge(List<ItemStack> stacks, ItemStack added) {
        if (added.isEmpty()) {
            return;
        }
        for (ItemStack stack : stacks) {
            if (ItemStack.isSameItemSameTags(stack, added) && stack.getCount() < stack.getMaxStackSize()) {
                int moved = Math.min(added.getCount(), stack.getMaxStackSize() - stack.getCount());
                stack.grow(moved);
                added.shrink(moved);
                if (added.isEmpty()) {
                    return;
                }
            }
        }
        stacks.add(added);
    }
}
