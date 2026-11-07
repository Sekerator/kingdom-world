package io.github.sekerator.kingdomworld.common.storage;

import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
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

    private KingdomStorage() {
    }

    /**
     * Storehouses on the kingdom's land; nearest to {@code near} first if it is given.
     */
    public static List<StorehouseInventory> storehouses(MinecraftServer server, UUID kingdomId, @Nullable WorldPos near) {
        KingdomSavedData data = KingdomSavedData.get(server);
        TerritoryIndex territory = data.state().territory();
        List<StorehouseInventory> result = new ArrayList<>();
        for (StorehouseInventory inventory : data.storehouses().all()) {
            WorldPos pos = inventory.pos();
            if (kingdomId.equals(territory.ownerOf(pos.dimension(), pos.chunk()))) {
                result.add(inventory);
            }
        }
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
     * Puts a stack into the kingdom's storehouses, nearest to {@code near} first.
     *
     * @return what did not fit; the whole stack if the kingdom has no storehouse or the data is read-only
     */
    public static ItemStack insert(MinecraftServer server, UUID kingdomId, ItemStack stack, @Nullable WorldPos near) {
        if (stack.isEmpty() || KingdomSavedData.get(server).isReadOnly()) {
            return stack;
        }
        ItemStack rest = stack.copy();
        for (StorehouseInventory inventory : storehouses(server, kingdomId, near)) {
            rest = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(inventory, rest, false);
            if (rest.isEmpty()) {
                break;
            }
        }
        return rest;
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
        for (Take take : takes) {
            ItemStack expected = take.found().stack();
            ItemStack inSlot = take.found().inventory().getStackInSlot(take.found().slot());
            ItemStack stack = inSlot.getCount() >= take.count() && ItemStack.isSameItemSameTags(inSlot, expected)
                    ? take.found().inventory().extractItem(take.found().slot(), take.count(), false) : ItemStack.EMPTY;
            if (stack.getCount() != take.count()) {
                // Something changed: everything goes back where it came from, or anywhere else.
                if (!stack.isEmpty()) {
                    taken.add(stack);
                }
                for (ItemStack back : taken) {
                    ItemStack rest = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(take.found().inventory(), back, false);
                    if (!rest.isEmpty()) {
                        insert(server, take.found().inventory().pos(), rest);
                    }
                }
                return null;
            }
            taken.add(stack);
        }
        return taken;
    }

    private static void insert(MinecraftServer server, WorldPos at, ItemStack stack) {
        UUID owner = KingdomSavedData.get(server).state().territory().ownerOf(at.dimension(), at.chunk());
        if (owner != null) {
            insert(server, owner, stack, at);
        }
    }

    /**
     * Whether all the stacks would fit into the kingdom's storehouses together.
     */
    public static boolean fits(MinecraftServer server, UUID kingdomId, List<ItemStack> stacks) {
        List<StorehouseInventory> inventories = storehouses(server, kingdomId, null);
        if (inventories.isEmpty()) {
            return stacks.stream().allMatch(ItemStack::isEmpty);
        }
        // Copies stand in for the storehouses, so that the stacks are tried one after another.
        List<net.minecraftforge.items.ItemStackHandler> copies = new ArrayList<>();
        for (StorehouseInventory inventory : inventories) {
            net.minecraftforge.items.ItemStackHandler copy = new net.minecraftforge.items.ItemStackHandler(inventory.getSlots());
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                copy.setStackInSlot(slot, inventory.getStackInSlot(slot).copy());
            }
            copies.add(copy);
        }
        for (ItemStack stack : stacks) {
            ItemStack rest = stack.copy();
            for (net.minecraftforge.items.ItemStackHandler copy : copies) {
                rest = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(copy, rest, false);
                if (rest.isEmpty()) {
                    break;
                }
            }
            if (!rest.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static String itemId(ItemStack stack) {
        net.minecraft.resources.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    /**
     * Empty slots of a storehouse.
     */
    public static int freeSlots(StorehouseInventory inventory) {
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
