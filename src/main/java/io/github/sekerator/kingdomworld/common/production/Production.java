package io.github.sekerator.kingdomworld.common.production;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSettings;
import io.github.sekerator.kingdomworld.common.citizen.work.PostContainers;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.production.Order;
import io.github.sekerator.kingdomworld.core.production.OrderBook;
import io.github.sekerator.kingdomworld.core.production.ProductionPlanner;
import io.github.sekerator.kingdomworld.core.production.ProductionRules;
import io.github.sekerator.kingdomworld.core.production.RecipeKind;
import io.github.sekerator.kingdomworld.core.production.RecipeOption;
import io.github.sekerator.kingdomworld.core.production.Reservations;
import io.github.sekerator.kingdomworld.core.production.Stations;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraftforge.common.ForgeHooks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Production of the kingdoms: plans the king's orders, hands out work to the workshops, keeps what workers set
 * aside, and makes things far from players in one go: the ingredients and fuel are taken and the result put
 * into the storehouses in the same tick, or nothing happens at all, so nothing is ever made twice or from nothing.
 *
 * <p>Server thread only.
 */
public final class Production {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Ticks after which orders are planned again when somebody asks for work. */
    private static final long PLAN_TICKS = 100;
    /** Key of a post's progress that holds burn time bought and not used yet, in furnace ticks. */
    static final String FUEL_BANK = "#fuel";
    /** Key of a post's progress that holds work time not used yet, in ticks. */
    static final String WORK_BANK = "#work";

    private static final Reservations RESERVATIONS = new Reservations();
    private static final Map<UUID, Long> LAST_PLAN = new HashMap<>();

    private Production() {
    }

    public static Reservations reservations() {
        return RESERVATIONS;
    }

    /**
     * Ends what a worker set aside: its work ended, it died, its body left the world.
     */
    public static void release(UUID citizenId) {
        RESERVATIONS.release(citizenId);
    }

    public static void reset() {
        RESERVATIONS.clear();
        LAST_PLAN.clear();
    }

    // ---------------------------------------------------------------- the state of a kingdom

    /**
     * Which workstations serve the work of a profession.
     */
    public static List<String> stationsOf(String profession) {
        if (Professions.SMELTER.id().equals(profession)) {
            return List.of(Stations.FURNACE, Stations.BLAST_FURNACE);
        }
        if (Professions.COOK.id().equals(profession)) {
            return List.of(Stations.SMOKER, Stations.CAMPFIRE, Stations.FURNACE, Stations.CRAFTING_TABLE);
        }
        if (Professions.CRAFTER.id().equals(profession)) {
            return List.of(Stations.CRAFTING_TABLE, Stations.STONECUTTER);
        }
        if (Professions.SMITH.id().equals(profession)) {
            return List.of(Stations.CRAFTING_TABLE, Stations.SMITHING_TABLE);
        }
        return List.of();
    }

    /**
     * Whether the kingdom has a staffed post of the recipe's profession with the workstation it needs.
     */
    static boolean workable(MinecraftServer server, UUID kingdomId, RecipeOption recipe) {
        for (WorkPost post : KingdomWorldApi.state(server).citizens().posts().postsOf(kingdomId, recipe.profession())) {
            if (!post.workers().isEmpty() && post.hasStation(recipe.kind().station())) {
                return true;
            }
        }
        return false;
    }

    /**
     * What the planner needs to know about a kingdom now.
     */
    public static ProductionPlanner.World world(MinecraftServer server, UUID kingdomId) {
        Map<String, Long> stock = KingdomStorage.countsById(server, kingdomId);
        Map<String, Boolean> workable = new HashMap<>();
        Map<String, String> gatherers = new HashMap<>();
        return new ProductionPlanner.World(kingdomId, Recipes.book(server), item -> stock.getOrDefault(item, 0L), RESERVATIONS,
                recipe -> workable.computeIfAbsent(recipe.profession() + "@" + recipe.kind().station(),
                        key -> workable(server, kingdomId, recipe)),
                item -> gatherers.computeIfAbsent(item, id -> Gatherers.of(server, kingdomId, id)),
                ProductionSettings.rules().chainDepth(), KingdomWorldApi.gameTime(server));
    }

    public static OrderBook orders(MinecraftServer server) {
        return KingdomWorldApi.state(server).orders();
    }

    /**
     * Whether citizens can make an item: a recipe in the book makes it.
     */
    public static boolean orderable(MinecraftServer server, String item) {
        return Recipes.book(server).canMake(item);
    }

    // ---------------------------------------------------------------- planning

    /**
     * Plans the orders of a kingdom again: statuses, recipes, sub-orders; finished orders leave with a message to
     * the members who manage production.
     */
    public static void plan(MinecraftServer server, UUID kingdomId) {
        if (KingdomWorldApi.isReadOnly(server)) {
            return;
        }
        LAST_PLAN.put(kingdomId, KingdomWorldApi.gameTime(server));
        OrderBook orders = orders(server);
        if (orders.ordersOf(kingdomId).isEmpty()) {
            return;
        }
        List<Order> done = ProductionPlanner.refresh(orders, world(server, kingdomId));
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdom(server, kingdomId);
        for (Order order : done) {
            Item item = Recipes.item(order.item());
            if (kingdom.isPresent() && item != null) {
                KingdomActions.notifyMembers(server, kingdom.get(), Permission.MANAGE_PRODUCTION,
                        Component.translatable("kingdomworld.order.done", new ItemStack(item).getHoverName(), order.amount()));
            }
            LOGGER.info("Order {} of kingdom {} is done: {} x {}", order.id(), kingdomId, order.amount(), order.item());
        }
    }

    /**
     * Plans a kingdom again if it was not planned lately.
     */
    public static void planIfStale(MinecraftServer server, UUID kingdomId) {
        Long last = LAST_PLAN.get(kingdomId);
        long now = KingdomWorldApi.gameTime(server);
        if (last == null || now - last >= PLAN_TICKS || now < last) {
            plan(server, kingdomId);
        }
    }

    // ---------------------------------------------------------------- handing out work

    /**
     * What a worker makes next, or why it makes nothing.
     *
     * @param fuel for work that burns fuel: the fuel to burn and how many of it, {@code null} otherwise
     */
    public record Search(@Nullable ProductionPlanner.Job job, @Nullable Fuel fuel, WorkStatus reason) {
    }

    /**
     * Fuel in the storehouses.
     *
     * @param burn ticks one item burns in the furnace the work needs
     */
    public record Fuel(String item, int burn) {
    }

    /**
     * Finds the next making for a worker at a workshop with the given workstations: the king's orders first, then
     * the post's own work in auto mode (smelting ore, cooking while food runs short).
     */
    public static Search findJob(MinecraftServer server, WorkPost post, Citizen citizen, Set<String> stations) {
        UUID kingdom = post.kingdomId();
        String profession = post.professionId();
        boolean anyStation = false;
        for (String station : stationsOf(profession)) {
            anyStation |= stations.contains(station);
        }
        if (!anyStation) {
            return new Search(null, null, WorkStatus.NO_WORKSTATION);
        }
        planIfStale(server, kingdom);
        ProductionPlanner.World world = world(server, kingdom);
        OrderBook orders = orders(server);
        ProductionPlanner.Job job = ProductionPlanner.nextJob(orders, world, profession, stations, citizen.id());
        if (job == null && post.auto() && Professions.hasAutoMode(profession) && autoWanted(server, kingdom, profession)) {
            job = ProductionPlanner.autoJob(orders, world, Recipes.autoRecipes(server, profession), stations, citizen.id());
        }
        if (job == null) {
            // The post says what the window of orders says: ingredients missing, a workstation lacking, or nothing to do.
            WorkStatus reason = switch (ProductionPlanner.waitFor(orders, world, profession, stations)) {
                case INGREDIENTS -> WorkStatus.NO_INGREDIENTS;
                case WORKSTATION -> WorkStatus.NO_WORKSTATION;
                case NOTHING -> WorkStatus.NO_ORDERS;
            };
            return new Search(null, null, reason);
        }
        if (job.recipe().kind().needsFuel()) {
            Fuel fuel = chooseFuel(server, kingdom, job.recipe().kind(), job.picks().keySet(), citizen.id());
            if (fuel == null) {
                return new Search(null, null, WorkStatus.NO_FUEL);
            }
            return new Search(job, fuel, WorkStatus.WORKING);
        }
        return new Search(job, null, WorkStatus.WORKING);
    }

    /**
     * Whether a workshop in auto mode has work of its own: a smelter always, a cook while the food in the
     * storehouses lasts fewer days than the server asks.
     */
    static boolean autoWanted(MinecraftServer server, UUID kingdomId, String profession) {
        if (!Professions.COOK.id().equals(profession)) {
            return true;
        }
        int citizens = KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdomId).size();
        double days = ProductionRules.foodDays(KingdomStorage.totalNutrition(server, kingdomId), citizens,
                CitizenSettings.rules().dailyNutrition());
        return days < ProductionSettings.rules().cookFoodDays();
    }

    /**
     * Sets aside the ingredients (and fuel) of a making for a worker.
     */
    public static void reserve(UUID worker, UUID kingdomId, ProductionPlanner.Job job, Map<String, Integer> fuel) {
        Map<String, Integer> items = new LinkedHashMap<>(job.picks());
        fuel.forEach((item, count) -> items.merge(item, count, Integer::sum));
        RESERVATIONS.reserve(worker, new Reservations.Hold(kingdomId, job.order(), items, job.recipe().output(), job.recipe().outputCount()));
    }

    /**
     * Items a kingdom set aside for everybody but one worker.
     */
    public static int reservedFor(UUID kingdomId, String item, @Nullable UUID except) {
        return RESERVATIONS.reserved(kingdomId, item, except);
    }

    // ---------------------------------------------------------------- fuel

    /**
     * Ticks one item burns in the furnace of the kind; blast furnaces and smokers burn twice as fast.
     */
    static int burnTicks(ItemStack fuel, RecipeKind kind) {
        RecipeType<?> type = kind == RecipeKind.BLASTING ? RecipeType.BLASTING : kind == RecipeKind.SMOKING ? RecipeType.SMOKING
                : RecipeType.SMELTING;
        int burn = ForgeHooks.getBurnTime(fuel, type);
        return kind == RecipeKind.SMELTING ? burn : burn / 2;
    }

    /**
     * Burn time one making needs, in ticks of the furnace.
     */
    static int fuelTicks(RecipeOption recipe) {
        return Math.max(ProductionRules.MIN_TICKS, recipe.ticks());
    }

    /**
     * Fuel to burn: anything that burns (of every mod) except tools, food and the ingredients of the work, coal and
     * charcoal first, else what the storehouses hold most of.
     */
    @Nullable
    public static Fuel chooseFuel(MinecraftServer server, UUID kingdomId, RecipeKind kind, Set<String> exclude, @Nullable UUID worker) {
        Fuel best = null;
        boolean bestCoal = false;
        long bestCount = 0;
        Map<String, Long> counts = new HashMap<>();
        Map<String, ItemStack> samples = new HashMap<>();
        for (KingdomStorage.Found found : KingdomStorage.find(server, kingdomId, stack -> !stack.isDamageableItem() && !Recipes.isFood(stack),
                null)) {
            String id = KingdomStorage.itemId(found.stack());
            counts.merge(id, (long) found.stack().getCount(), Long::sum);
            samples.putIfAbsent(id, found.stack());
        }
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            String id = entry.getKey();
            if (exclude.contains(id)) {
                continue;
            }
            long free = entry.getValue() - RESERVATIONS.reserved(kingdomId, id, worker);
            int burn = burnTicks(samples.get(id), kind);
            if (free <= 0 || burn <= 0) {
                continue;
            }
            boolean coal = samples.get(id).is(ItemTags.COALS);
            if (best == null || coal && !bestCoal || coal == bestCoal && free > bestCount) {
                best = new Fuel(id, burn);
                bestCoal = coal;
                bestCount = free;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- making things

    /**
     * What a making gives from the ingredients taken for it: the result first, then what the ingredients leave
     * behind (buckets of milk give back the bucket). An upgrade at the smithing table keeps the enchantments and
     * the name of the base.
     */
    public static List<ItemStack> assemble(MinecraftServer server, RecipeOption recipe, List<ItemStack> ingredients) {
        List<ItemStack> result = new ArrayList<>();
        Recipe<?> game = Recipes.recipe(server, recipe.id()).orElse(null);
        if (game == null) {
            return result;
        }
        ItemStack made;
        if (recipe.kind() == RecipeKind.SMITHING && game instanceof SmithingTransformRecipe smithing) {
            SimpleContainer table = new SimpleContainer(3);
            List<ItemStack> left = new ArrayList<>(ingredients);
            for (int part = 0; part < 3; part++) {
                for (ItemStack stack : left) {
                    if (Recipes.accepts(server, recipe, part, stack)) {
                        table.setItem(part, stack.copyWithCount(1));
                        left.remove(stack);
                        break;
                    }
                }
            }
            made = smithing.assemble(table, server.registryAccess());
        } else {
            made = game.getResultItem(server.registryAccess()).copy();
        }
        if (made.isEmpty()) {
            return result;
        }
        result.add(made);
        if (!recipe.kind().cooks()) {
            for (ItemStack stack : ingredients) {
                if (stack.hasCraftingRemainingItem()) {
                    for (int i = 0; i < stack.getCount(); i++) {
                        ItemStack remainder = stack.getCraftingRemainingItem();
                        if (!remainder.isEmpty()) {
                            result.add(remainder.copy());
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * Counts a making: in the kingdom's record of production and on the post, and for its order.
     *
     * @param watched whether it was made near players, so that the work of the post is measured
     */
    public static void made(MinecraftServer server, WorkPost post, long orderId, ItemStack result, boolean watched) {
        if (watched) {
            PostWork.gathered(server, post, result);
            PostWork.acted(post, 1);
        } else {
            PostWork.record(server, post, PostWork.itemId(result), result.getCount());
        }
        if (orderId != 0) {
            OrderBook orders = orders(server);
            orders.get(post.kingdomId(), orderId).filter(order -> order.kind() == Order.Kind.MAKE)
                    .ifPresent(order -> orders.addMade(order, result.getCount()));
        }
    }

    /**
     * How one making far from players ended.
     */
    public enum Made {
        DONE,
        NO_INGREDIENTS,
        NO_FUEL,
        STORAGE_FULL,
        /** Every storehouse is given to other work. */
        NO_STOREHOUSE
    }

    /**
     * One making far from players, in one go: the ingredients and the fuel the furnace still needs are taken from
     * the storehouses and the result is put there in the same tick, or nothing changes.
     */
    public static Made makeAbstractly(MinecraftServer server, WorkPost post, Citizen citizen, ProductionPlanner.Job job, @Nullable Fuel fuel) {
        UUID kingdom = post.kingdomId();
        WorldPos near = post.pos();
        if (!KingdomStorage.hasStorehouseFor(server, kingdom, post.professionId())) {
            // Nothing is taken for a making whose result could go nowhere.
            return Made.NO_STOREHOUSE;
        }
        Map<String, Integer> wanted = new LinkedHashMap<>(job.picks());
        double bank = post.progress().getOrDefault(FUEL_BANK, 0.0);
        int fuelCount = 0;
        if (job.recipe().kind().needsFuel()) {
            if (fuel == null) {
                return Made.NO_FUEL;
            }
            fuelCount = ProductionRules.fuelItems(fuelTicks(job.recipe()) - bank, fuel.burn());
            if (fuelCount > 0) {
                wanted.merge(fuel.item(), fuelCount, Integer::sum);
            }
        }
        List<KingdomStorage.Take> plan = KingdomStorage.plan(server, kingdom, wanted, item -> RESERVATIONS.reserved(kingdom, item, citizen.id()), near);
        if (plan == null) {
            boolean ingredients = KingdomStorage.plan(server, kingdom, job.picks(), item -> RESERVATIONS.reserved(kingdom, item, citizen.id()),
                    near) != null;
            return ingredients ? Made.NO_FUEL : Made.NO_INGREDIENTS;
        }
        List<ItemStack> ingredients = new ArrayList<>();
        Map<String, Integer> fuelLeft = new HashMap<>();
        if (fuelCount > 0) {
            fuelLeft.put(fuel.item(), fuelCount);
        }
        for (KingdomStorage.Take take : plan) {
            ingredients.add(take.found().stack().copyWithCount(take.count()));
        }
        List<ItemStack> outputs = assemble(server, job.recipe(), withoutFuel(ingredients, fuelLeft));
        if (outputs.isEmpty()) {
            return Made.NO_INGREDIENTS;
        }
        // What the burned fuel leaves (the bucket of a lava bucket) goes back too.
        for (ItemStack stack : ingredients) {
            String id = KingdomStorage.itemId(stack);
            if (fuel != null && id.equals(fuel.item()) && stack.hasCraftingRemainingItem()) {
                for (int i = 0; i < Math.min(stack.getCount(), fuelCount); i++) {
                    outputs.add(stack.getCraftingRemainingItem().copy());
                }
            }
        }
        if (!KingdomStorage.fitsFor(server, kingdom, post.professionId(), outputs)) {
            return Made.STORAGE_FULL;
        }
        List<ItemStack> taken = KingdomStorage.takePlanned(server, plan);
        if (taken == null) {
            return Made.NO_INGREDIENTS;
        }
        if (job.recipe().kind().needsFuel()) {
            double next = bank + (double) fuelCount * fuel.burn() - fuelTicks(job.recipe());
            Map<String, Double> progress = new LinkedHashMap<>(post.progress());
            progress.put(FUEL_BANK, Math.max(0.0, next));
            post.setProgress(progress);
        }
        for (ItemStack output : outputs) {
            ItemStack rest = KingdomStorage.insertFor(server, kingdom, post.professionId(), output, near);
            if (!rest.isEmpty()) {
                rest = PostWork.deliver(server, post, rest);
            }
            if (!rest.isEmpty()) {
                drop(server, post, rest);
            }
        }
        made(server, post, job.order(), outputs.get(0), false);
        return Made.DONE;
    }

    /**
     * The ingredients without the fuel taken along with them.
     */
    private static List<ItemStack> withoutFuel(List<ItemStack> taken, Map<String, Integer> fuel) {
        Map<String, Integer> left = new HashMap<>(fuel);
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : taken) {
            String id = KingdomStorage.itemId(stack);
            int skip = Math.min(stack.getCount(), left.getOrDefault(id, 0));
            if (skip > 0) {
                left.put(id, left.get(id) - skip);
            }
            if (stack.getCount() > skip) {
                result.add(stack.copyWithCount(stack.getCount() - skip));
            }
        }
        return result;
    }

    /**
     * Last resort for a result with no room anywhere: it lies at the post (the storehouses were checked before, so
     * this hardly ever happens).
     */
    private static void drop(MinecraftServer server, WorkPost post, ItemStack stack) {
        ServerLevel level = PostWork.level(server, post.pos());
        BlockPos pos = new BlockPos(post.pos().x(), post.pos().y(), post.pos().z());
        LOGGER.warn("No room for {} made at the work post at {}; it is left at the post", stack, post.pos().coordinates());
        if (level != null && level.isLoaded(pos)) {
            level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack));
        }
    }

    // ---------------------------------------------------------------- carriers

    /**
     * Items the workers of a post set aside and still have to fetch: carriers bring them into the post's
     * container, by item id with the count wanted there.
     */
    public static Map<String, Integer> wanted(WorkPost post, Map<String, Integer> container) {
        Map<String, Integer> wanted = new LinkedHashMap<>();
        for (UUID worker : post.workers()) {
            Reservations.Hold hold = RESERVATIONS.hold(worker);
            if (hold != null) {
                hold.items().forEach((item, count) -> wanted.merge(item, count, Integer::sum));
            }
        }
        wanted.replaceAll((item, count) -> Math.max(0, count - container.getOrDefault(item, 0)));
        wanted.values().removeIf(count -> count <= 0);
        return wanted;
    }

    /**
     * Whether an item in a post's container is something its workers set aside, so that carriers leave it there.
     */
    public static boolean isInput(WorkPost post, ItemStack stack) {
        String id = KingdomStorage.itemId(stack);
        for (UUID worker : post.workers()) {
            Reservations.Hold hold = RESERVATIONS.hold(worker);
            if (hold != null && hold.items().containsKey(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a post has a container next to it and the kingdom a carrier to fill it, so that its workers need not
     * fetch their ingredients themselves.
     */
    public static boolean suppliedByCarriers(MinecraftServer server, WorkPost post) {
        if (!post.hasContainer()) {
            return false;
        }
        for (WorkPost carrier : KingdomWorldApi.state(server).citizens().posts().postsOf(post.kingdomId(), Professions.CARRIER.id())) {
            if (!carrier.workers().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * What a post's container holds, by item id, if it is loaded.
     */
    static Map<String, Integer> containerContents(ServerLevel level, WorkPost post) {
        PostContainers.Container container = PostContainers.find(level, post.pos());
        return container == null ? Map.of() : PostContainers.contents(container.handler());
    }
}
