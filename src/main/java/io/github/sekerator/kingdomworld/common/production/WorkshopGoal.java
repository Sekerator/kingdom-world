package io.github.sekerator.kingdomworld.common.production;

import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.work.PostContainers;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWorkGoal;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehavior;
import io.github.sekerator.kingdomworld.common.citizen.work.ZoneSurveys;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.production.Batches;
import io.github.sekerator.kingdomworld.core.production.ProductionPlanner;
import io.github.sekerator.kingdomworld.core.production.RecipeKind;
import io.github.sekerator.kingdomworld.core.production.RecipeOption;
import io.github.sekerator.kingdomworld.core.production.Reservations;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A workshop worker near players. It picks what to make next (an order of the king, or the post's own work) and takes a batch of
 * makings of that recipe at once: as many as the order still asks for, the storehouses hold and its bag carries, and at furnaces as
 * many as the free furnaces of its zone take. It sets the ingredients and fuel aside, fetches them (from the post's container if
 * carriers brought them, else from the storehouses) and makes the things: one after another by hand at a table or on a campfire;
 * in real furnaces, all the free ones of its zone at once, which it fills with the ingredient from above and with as much fuel as
 * the cooking burns from the side and empties from below as the results come, so that furnaces of other mods built the same way
 * serve too. Every making counts as soon as its result is out; the results go to the storehouse when the batch is done.
 */
final class WorkshopGoal extends PostWorkGoal {
    private static final double STATION_REACH_SQR = 2.5 * 2.5;
    private static final int GIVE_UP_TICKS = 600;
    /** How long a worker waits at its post for carriers to bring its ingredients. */
    private static final int CARRIER_WAIT_TICKS = 200;
    /** Storehouses a worker walks to for one batch at most; what is still missing then comes from any storehouse. */
    private static final int MAX_STOREHOUSES = 4;
    /** Ticks between two looks of a worker at the furnaces of its batch. */
    private static final int CHECK_TICKS = 10;
    /** Items the bag holds at most: its slots of full stacks. */
    private static final int BAG_ITEMS = CitizenEntity.CARRY_SLOTS * 64;
    /** A batch put aside when the day of work ended is given up if its worker does not come back to it within a day. */
    private static final long PARKED_TICKS = 24_000L;

    /** Furnaces a worker tends, by place: no other worker puts anything into them or takes anything out. */
    private static final Map<WorldPos, WorkshopGoal> CLAIMS = new HashMap<>();
    /** How many times each worker took ingredients at a storehouse, for tests. */
    private static final Map<UUID, Integer> FETCHES = new HashMap<>();

    private enum Step {
        CHOOSE,
        FETCH,
        GO,
        WORK,
        TEND
    }

    /**
     * A furnace of a batch: what goes into it and what came out of it.
     */
    private static final class Load {
        private final BlockPos pos;
        /** Ingredients and fuel to put in. */
        private int items;
        private int fuel;
        private int loaded;
        private int fueled;
        /** Results of the recipe that lay in its output before the batch: they are taken out with those of the batch. */
        private final int before;
        /** Results taken out. */
        private int taken;
        /** Ticks since anything went in or came out. */
        private int idle;
        private boolean done;

        Load(BlockPos pos, int items, int fuel, int before) {
            this.pos = pos;
            this.items = items;
            this.fuel = fuel;
            this.before = before;
        }

        boolean filled() {
            return loaded >= items && fueled >= fuel;
        }
    }

    private Step step = Step.CHOOSE;
    @Nullable
    private ProductionPlanner.Job job;
    /** The ingredients of each making of the batch, in order. */
    private List<Map<String, Integer>> makings = List.of();
    /** Makings of the batch done and counted. */
    private int done;
    /** Results taken out of the furnaces that do not make up a whole making yet. */
    private int results;
    @Nullable
    private Production.Fuel fuel;
    private final List<Load> loads = new ArrayList<>();
    @Nullable
    private Load target;
    /** Why a furnace of the batch stopped, if one did. */
    @Nullable
    private WorkStatus trouble;
    @Nullable
    private BlockPos station;
    @Nullable
    private BlockPos storehouse;
    @Nullable
    private WorldPos batchPost;
    private int timer;
    private int total;
    private int walking;
    private int waited;
    /** Storehouses the worker took from for this batch. */
    private int visited;
    /** When the batch was put aside because the work stopped, -1 while it is worked at. */
    private long parked = -1;

    WorkshopGoal(CitizenEntity body, ProfessionBehavior behavior) {
        super(body, behavior);
    }

    @Override
    protected boolean deliversToPost() {
        // The post's container is the workshop's input; what is made goes to the storehouse.
        return false;
    }

    @Override
    protected void onStart() {
        if (job != null) {
            if (parked >= 0 && resumable()) {
                // Back from the night or from a meal: the furnaces burned on meanwhile, and the batch goes on where it stopped.
                parked = -1;
                loads.forEach(load -> load.idle = 0);
                target = null;
                walking = 0;
                timer = step == Step.TEND ? CHECK_TICKS - 1 : 0;
                if (step == Step.WORK) {
                    step = Step.GO;
                }
                return;
            }
            giveUp();
        }
        step = Step.CHOOSE;
    }

    @Override
    protected void onStop() {
        if (job == null) {
            return;
        }
        if ((step == Step.TEND || step == Step.GO || step == Step.WORK) && sameWork()) {
            // Everything is fetched: the batch waits for the worker, who comes back to it when it works again.
            parked = body.level().getGameTime();
            return;
        }
        giveUp();
    }

    @Override
    protected void onLeaveWork() {
        if (job != null) {
            giveUp();
        }
    }

    /**
     * Whether the body still works at the post of the batch in its profession.
     */
    private boolean sameWork() {
        return post != null && postPos != null && post.professionId().equals(body.professionId()) && postPos.equals(body.workplace());
    }

    /**
     * Whether a batch put aside may go on: the worker came back to the same post within a day and its reservation is still there.
     */
    private boolean resumable() {
        return post != null && post.pos().equals(batchPost) && body.level().getGameTime() - parked <= PARKED_TICKS
                && citizen().map(citizen -> Production.reservations().has(citizen.id())).orElse(false);
    }

    @Override
    protected WorkStatus work(ServerLevel level) {
        return switch (step) {
            case CHOOSE -> choose(level);
            case FETCH -> fetch(level);
            case GO -> go(level);
            case WORK -> handWork(level);
            case TEND -> tend(level);
        };
    }

    // ---------------------------------------------------------------- choosing

    @Nullable
    private WorkStatus choose(ServerLevel level) {
        if (hasCarried()) {
            // Leftovers of earlier work go to the storehouse first.
            return WorkStatus.WORKING;
        }
        MinecraftServer server = level.getServer();
        Optional<Citizen> citizen = citizen();
        if (citizen.isEmpty()) {
            return WorkStatus.UNKNOWN;
        }
        if (!WorkshopBehavior.surveyed(post.pos())) {
            ZoneSurveys.request(post.pos());
            return WorkStatus.WAITING;
        }
        Map<String, List<BlockPos>> known = WorkshopBehavior.knownStations(post.pos());
        Set<String> kinds = new LinkedHashSet<>();
        known.forEach((kind, list) -> {
            if (!list.isEmpty()) {
                kinds.add(kind);
            }
        });
        Production.Search search = Production.findJob(server, post, citizen.get(), kinds);
        if (search.job() == null) {
            return search.reason();
        }
        ProductionPlanner.Job found = search.job();
        List<BlockPos> candidates = known.getOrDefault(found.recipe().kind().station(), List.of());
        clear();
        WorkStatus refused = found.recipe().kind().needsFuel()
                ? planFurnaces(level, citizen.get(), found, search.fuel(), candidates)
                : planHandWork(level, citizen.get(), found, candidates);
        if (refused != null) {
            clear();
            return refused;
        }
        batchPost = post.pos();
        post.setCurrent(found.recipe().output(), 0.0);
        step = Step.FETCH;
        report(WorkStatus.WORKING);
        return null;
    }

    /**
     * A batch at a table or a campfire: the makings the order still asks for, the stock holds and the bag carries together with
     * their results, made one after another at the nearest workstation.
     */
    @Nullable
    private WorkStatus planHandWork(ServerLevel level, Citizen citizen, ProductionPlanner.Job found, List<BlockPos> candidates) {
        BlockPos at = nearestStation(level, candidates, found.recipe().kind().station());
        if (at == null) {
            ZoneSurveys.request(post.pos());
            return WorkStatus.NO_WORKSTATION;
        }
        MinecraftServer server = level.getServer();
        RecipeOption recipe = found.recipe();
        int most = Math.min(Production.makingsWanted(server, post.kingdomId(), found), BAG_ITEMS);
        List<Map<String, Integer>> batch = Production.batch(server, post.kingdomId(), found, citizen.id(), most, false);
        int fits = Batches.largest(batch.size(),
                count -> Batches.stacks(bagLoad(batch, count, recipe, Map.of()), WorkshopGoal::maxStack) <= CitizenEntity.CARRY_SLOTS);
        int count = Math.max(1, fits);
        begin(found, batch.subList(0, count));
        station = at;
        Production.reserve(citizen.id(), post.kingdomId(), found, count, sum(makings), Map.of());
        return null;
    }

    /**
     * A batch at furnaces: spread over every free furnace of the kind in the zone, nearest first, up to a stack of the ingredient
     * in each, as many makings as the order still asks for, the stock holds, the fuel there cooks and the bag carries together with
     * that fuel and the results. Each furnace gets the fuel its cooking burns, less what lies in it already.
     */
    @Nullable
    private WorkStatus planFurnaces(ServerLevel level, Citizen citizen, ProductionPlanner.Job found, @Nullable Production.Fuel burn,
                                    List<BlockPos> candidates) {
        RecipeOption recipe = found.recipe();
        if (burn == null) {
            return WorkStatus.NO_FUEL;
        }
        if (found.picks().size() != 1) {
            // A furnace holds one ingredient; every recipe that cooks has one.
            return WorkStatus.NO_INGREDIENTS;
        }
        String ingredient = found.picks().keySet().iterator().next();
        int perMaking = Math.max(1, found.picks().get(ingredient));
        Item output = Recipes.item(recipe.output());
        int cook = Production.fuelTicks(recipe);
        String kind = recipe.kind().station();
        List<BlockPos> free = new ArrayList<>();
        boolean busy = false;
        for (BlockPos pos : candidates) {
            if (!level.isLoaded(pos) || !land.owns(pos) || !kind.equals(Workstations.kindOf(level, pos, level.getBlockState(pos)))) {
                continue;
            }
            if (claimedByOther(pos) || !furnaceFree(level, pos, found.picks().keySet(), output, recipe.outputCount())) {
                busy = true;
                continue;
            }
            free.add(pos.immutable());
        }
        if (free.isEmpty()) {
            if (busy) {
                // The furnaces of the zone are all at work: the worker waits for one to come free.
                return WorkStatus.WAITING;
            }
            ZoneSurveys.request(post.pos());
            return WorkStatus.NO_WORKSTATION;
        }
        free.sort(Comparator.comparingDouble(pos -> body.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)));
        int size = free.size();
        int[] room = new int[size];
        int[] inside = new int[size];
        int[] before = new int[size];
        double[] banked = new double[size];
        boolean[] own = new boolean[size];
        long roomTotal = 0;
        for (int i = 0; i < size; i++) {
            Workstations.Furnace furnace = furnaceAt(level, free.get(i));
            if (furnace == null) {
                continue;
            }
            ItemStack input = furnace.input().getStackInSlot(0);
            inside[i] = input.isEmpty() ? 0 : input.getCount();
            int items = Math.min(maxStack(ingredient), furnace.input().getSlotLimit(0)) - inside[i];
            ItemStack lying = furnace.fuel().getStackInSlot(0);
            if (lying.isEmpty()) {
                own[i] = true;
            } else if (KingdomStorage.itemId(lying).equals(burn.item())) {
                own[i] = true;
                banked[i] = lying.getCount() * (double) burn.burn();
            } else {
                // Fuel of another kind lies there: no fuel can be added, and the furnace cooks what that fuel burns for.
                banked[i] = lying.getCount() * (double) Production.burnTicks(lying, recipe.kind());
                items = Math.min(items, Batches.cookable(banked[i], cook) - inside[i]);
            }
            for (int slot = 0; slot < furnace.output().getSlots() && output != null; slot++) {
                ItemStack made = furnace.output().getStackInSlot(slot);
                if (made.is(output)) {
                    before[i] += made.getCount();
                }
            }
            room[i] = Math.max(0, items / perMaking);
            roomTotal += room[i];
        }
        if (roomTotal == 0) {
            return WorkStatus.WAITING;
        }
        MinecraftServer server = level.getServer();
        int most = (int) Math.min(roomTotal, Production.makingsWanted(server, post.kingdomId(), found));
        List<Map<String, Integer>> batch = Production.batch(server, post.kingdomId(), found, citizen.id(), most, true);
        long fuelThere = Production.free(server, post.kingdomId(), burn.item(), citizen.id());
        int count = Batches.largest(batch.size(), makingsCount -> {
            int fuelNeeded = fuelFor(Batches.spread(makingsCount, room), inside, banked, own, perMaking, cook, burn.burn());
            return fuelNeeded <= fuelThere && Batches.stacks(bagLoad(batch, makingsCount, recipe, Map.of(burn.item(), fuelNeeded)),
                    WorkshopGoal::maxStack) <= CitizenEntity.CARRY_SLOTS;
        });
        if (count == 0) {
            return WorkStatus.NO_FUEL;
        }
        int[] spread = Batches.spread(count, room);
        begin(found, batch.subList(0, count));
        fuel = burn;
        int fuelTotal = 0;
        for (int i = 0; i < size; i++) {
            if (spread[i] <= 0) {
                continue;
            }
            int items = spread[i] * perMaking;
            int burnItems = own[i] ? Batches.fuel(inside[i] + items, cook, banked[i], burn.burn()) : 0;
            loads.add(new Load(free.get(i), items, burnItems, before[i]));
            fuelTotal += burnItems;
            CLAIMS.put(key(free.get(i)), this);
        }
        Production.reserve(citizen.id(), post.kingdomId(), found, count, sum(makings),
                fuelTotal > 0 ? Map.of(burn.item(), fuelTotal) : Map.of());
        return null;
    }

    /**
     * Fuel items furnaces need for makings spread over them so.
     */
    private static int fuelFor(int[] spread, int[] inside, double[] banked, boolean[] own, int perMaking, int cook, int burn) {
        long total = 0;
        for (int i = 0; i < spread.length; i++) {
            if (spread[i] > 0 && own[i]) {
                total += Batches.fuel(inside[i] + spread[i] * perMaking, cook, banked[i], burn);
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /**
     * What the bag holds at most during a batch of {@code count} makings: their ingredients, the fuel and their results.
     */
    private static Map<String, Integer> bagLoad(List<Map<String, Integer>> batch, int count, RecipeOption recipe, Map<String, Integer> fuel) {
        Map<String, Integer> load = sum(batch.subList(0, Math.min(count, batch.size())));
        fuel.forEach((item, amount) -> load.merge(item, amount, Integer::sum));
        load.merge(recipe.output(), (int) Math.min(Integer.MAX_VALUE, (long) count * recipe.outputCount()), Integer::sum);
        return load;
    }

    private static Map<String, Integer> sum(List<Map<String, Integer>> makings) {
        Map<String, Integer> total = new LinkedHashMap<>();
        for (Map<String, Integer> making : makings) {
            making.forEach((item, count) -> total.merge(item, count, Integer::sum));
        }
        return total;
    }

    /**
     * How many of an item go into one slot of the bag.
     */
    private static int maxStack(String item) {
        Item found = Recipes.item(item);
        return found == null ? 64 : Math.max(1, new ItemStack(found).getMaxStackSize());
    }

    private void begin(ProductionPlanner.Job found, List<Map<String, Integer>> batch) {
        job = found;
        makings = List.copyOf(batch);
        done = 0;
        results = 0;
        trouble = null;
        parked = -1;
        storehouse = null;
        walking = 0;
        waited = 0;
        visited = 0;
    }

    /**
     * The nearest workstation of the kind that is still there.
     */
    @Nullable
    private BlockPos nearestStation(ServerLevel level, List<BlockPos> candidates, String kind) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : candidates) {
            if (!level.isLoaded(pos) || !land.owns(pos) || !kind.equals(Workstations.kindOf(level, pos, level.getBlockState(pos)))) {
                continue;
            }
            double distance = body.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distance < bestDistance) {
                best = pos;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Whether a furnace can take the ingredient and give the result: its input is empty or holds the same ingredient with room,
     * and its output is empty or holds the same result with room for it. A furnace whose output holds something else (glass a
     * player left there, say) would burn the fuel and never give the result.
     */
    private static boolean furnaceFree(ServerLevel level, BlockPos pos, Set<String> inputs, @Nullable Item output, int outputCount) {
        Workstations.Furnace furnace = furnaceAt(level, pos);
        if (furnace == null) {
            return false;
        }
        ItemStack input = furnace.input().getStackInSlot(0);
        if (!input.isEmpty() && !(inputs.contains(KingdomStorage.itemId(input)) && input.getCount() < input.getMaxStackSize())) {
            return false;
        }
        for (int slot = 0; slot < furnace.output().getSlots(); slot++) {
            ItemStack made = furnace.output().getStackInSlot(slot);
            if (!made.isEmpty() && (output == null || !made.is(output) || made.getCount() + outputCount > made.getMaxStackSize())) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private static Workstations.Furnace furnaceAt(ServerLevel level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        return entity == null ? null : Workstations.furnace(entity);
    }

    // ---------------------------------------------------------------- furnaces in use

    private WorldPos key(BlockPos pos) {
        return new WorldPos(post.pos().dimension(), pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Whether another worker tends the furnace now; a worker gone, or done with it, holds it no more.
     */
    private boolean claimedByOther(BlockPos pos) {
        WorldPos key = key(pos);
        WorkshopGoal holder = CLAIMS.get(key);
        if (holder == null || holder == this) {
            return false;
        }
        if (!holder.tends(pos)) {
            CLAIMS.remove(key);
            return false;
        }
        return true;
    }

    /**
     * Whether this worker tends the furnace: it is in its batch and not done with, and the worker is there to come back to it.
     */
    private boolean tends(BlockPos pos) {
        if (job == null || body.isRemoved() || !body.isAlive() || !sameWork()
                || parked >= 0 && body.level().getGameTime() - parked > PARKED_TICKS) {
            return false;
        }
        for (Load load : loads) {
            if (!load.done && load.pos.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    private void unclaim(BlockPos pos) {
        if (post != null) {
            CLAIMS.remove(key(pos), this);
        }
    }

    static void reset() {
        CLAIMS.clear();
        FETCHES.clear();
    }

    static int fetches(UUID worker) {
        return FETCHES.getOrDefault(worker, 0);
    }

    // ---------------------------------------------------------------- fetching

    @Nullable
    private WorkStatus fetch(ServerLevel level) {
        Citizen citizen = citizen().orElse(null);
        Reservations.Hold hold = citizen == null ? null : Production.reservations().hold(citizen.id());
        if (citizen == null || hold == null) {
            return giveUp(WorkStatus.UNKNOWN);
        }
        Map<String, Integer> needed = hold.items();
        if (needed.isEmpty()) {
            fetched();
            return null;
        }
        if (++walking > GIVE_UP_TICKS) {
            return giveUp(WorkStatus.NO_INGREDIENTS);
        }
        // Brought by carriers into the post's container.
        PostContainers.Container container = PostContainers.find(level, postPos);
        if (container != null && holdsAny(container.handler(), needed)) {
            if (!near(postPos, REACH_SQR)) {
                moveTo(postPos);
                return null;
            }
            takeFrom(container.handler(), needed, citizen);
            return null;
        }
        if (container != null && waited < CARRIER_WAIT_TICKS && Production.suppliedByCarriers(level.getServer(), post)) {
            waited++;
            if (!near(postPos, REACH_SQR * 4)) {
                moveTo(postPos);
            }
            return null;
        }
        // Fetched by the worker itself: from the storehouse where the items lie, the nearest first; items in storehouses out of
        // reach come along through the nearest one, so that work never waits for a player to carry them over.
        if (storehouse == null) {
            StorehouseInventory holder = visited < MAX_STOREHOUSES ? storehouseWith(level, needed) : null;
            storehouse = holder != null ? blockPos(holder.pos()) : nearestStorehouse(level);
            if (storehouse == null) {
                return giveUp(WorkStatus.NO_STOREHOUSE);
            }
        }
        if (!near(storehouse, REACH_SQR)) {
            moveTo(storehouse);
            return null;
        }
        MinecraftServer server = level.getServer();
        WorldPos near = new WorldPos(post.pos().dimension(), storehouse.getX(), storehouse.getY(), storehouse.getZ());
        if (visited < MAX_STOREHOUSES && takeHere(level, near, needed, citizen)) {
            // Took what this storehouse holds; the rest from the next storehouse that holds it.
            FETCHES.merge(citizen.id(), 1, Integer::sum);
            visited++;
            walking = 0;
            storehouse = null;
            Reservations.Hold left = Production.reservations().hold(citizen.id());
            if (left != null && !left.items().isEmpty() && visited < MAX_STOREHOUSES && storehouseWith(level, left.items()) != null) {
                return null;
            }
            if (left == null || left.items().isEmpty()) {
                fetched();
                return null;
            }
            storehouse = new BlockPos(near.x(), near.y(), near.z());
            needed = left.items();
        }
        FETCHES.merge(citizen.id(), 1, Integer::sum);
        for (Map.Entry<String, Integer> entry : new LinkedHashMap<>(needed).entrySet()) {
            String item = entry.getKey();
            List<ItemStack> taken = KingdomStorage.take(server, post.kingdomId(), stack -> KingdomStorage.itemId(stack).equals(item),
                    entry.getValue(), near);
            int count = 0;
            for (ItemStack stack : taken) {
                count += stack.getCount();
                carry(stack);
            }
            Production.reservations().taken(citizen.id(), item, count);
            if (count < entry.getValue()) {
                // Somebody took them first.
                return giveUp(WorkStatus.NO_INGREDIENTS);
            }
        }
        fetched();
        return null;
    }

    /**
     * Everything is in the bag: to the furnaces, or to the table.
     */
    private void fetched() {
        walking = 0;
        target = null;
        if (job.recipe().kind().needsFuel()) {
            step = Step.TEND;
            timer = CHECK_TICKS - 1;
        } else {
            step = Step.GO;
        }
    }

    /**
     * The nearest storehouse whose place is loaded and that holds any of the items, in the city of the post (anywhere on the land
     * of the kingdom for a post outside the cities): a storehouse, either half of a double one, or a container of a warehouse.
     */
    @Nullable
    private StorehouseInventory storehouseWith(ServerLevel level, Map<String, Integer> needed) {
        for (StorehouseInventory inventory : KingdomStorage.takingSources(level, post.kingdomId(), post.pos(), body.blockPosition())) {
            if (holdsAny(inventory, needed)) {
                return inventory;
            }
        }
        return null;
    }

    private static BlockPos blockPos(WorldPos pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }

    /**
     * Takes what the storehouse at the place holds of the items the worker set aside.
     *
     * @return whether it held any of them
     */
    private boolean takeHere(ServerLevel level, WorldPos at, Map<String, Integer> needed, Citizen citizen) {
        MinecraftServer server = level.getServer();
        boolean took = false;
        for (StorehouseInventory inventory : KingdomStorage.storehouses(server, post.kingdomId(), at)) {
            if (!inventory.pos().equals(at)) {
                break;
            }
            for (Map.Entry<String, Integer> entry : new LinkedHashMap<>(needed).entrySet()) {
                String item = entry.getKey();
                int count = 0;
                for (ItemStack stack : KingdomStorage.takeFrom(server, inventory, value -> KingdomStorage.itemId(value).equals(item),
                        entry.getValue())) {
                    count += stack.getCount();
                    carry(stack);
                }
                if (count > 0) {
                    took = true;
                    Production.reservations().taken(citizen.id(), item, count);
                }
            }
        }
        return took;
    }

    private static boolean holdsAny(IItemHandler handler, Map<String, Integer> needed) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && needed.containsKey(KingdomStorage.itemId(stack))) {
                return true;
            }
        }
        return false;
    }

    private void takeFrom(IItemHandler handler, Map<String, Integer> needed, Citizen citizen) {
        Map<String, Integer> left = new LinkedHashMap<>(needed);
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            String id = KingdomStorage.itemId(stack);
            int want = left.getOrDefault(id, 0);
            if (stack.isEmpty() || want <= 0) {
                continue;
            }
            ItemStack taken = handler.extractItem(slot, Math.min(want, stack.getCount()), false);
            if (!taken.isEmpty()) {
                left.put(id, want - taken.getCount());
                Production.reservations().taken(citizen.id(), id, taken.getCount());
                carry(taken);
            }
        }
    }

    // ---------------------------------------------------------------- work by hand

    @Nullable
    private WorkStatus go(ServerLevel level) {
        if (station == null || !level.isLoaded(station) || !land.owns(station)
                || !job.recipe().kind().station().equals(Workstations.kindOf(level, station, level.getBlockState(station)))) {
            ZoneSurveys.request(post.pos());
            return endBatch(WorkStatus.NO_WORKSTATION);
        }
        if (!near(station, STATION_REACH_SQR)) {
            moveTo(station);
            if (++walking > GIVE_UP_TICKS) {
                return endBatch(WorkStatus.NO_WORKSTATION);
            }
            return null;
        }
        timer = 0;
        total = handTicks(level);
        step = Step.WORK;
        return null;
    }

    /**
     * Ticks the next making by hand takes: experience, mood and a workshop building make it faster.
     */
    private int handTicks(ServerLevel level) {
        int experience = citizen().map(Citizen::experience).orElse(0);
        return Math.max(1, (int) Math.round(ProductionSettings.rules().ticks(job.recipe(), experience)
                / io.github.sekerator.kingdomworld.common.economy.EconomySettings.workSpeed(citizen().map(c -> c.mood())
                .orElse(io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds.NEUTRAL_MOOD))
                / io.github.sekerator.kingdomworld.common.construction.BuildingEffects.workshopSpeed(level.getServer(),
                io.github.sekerator.kingdomworld.common.construction.ConstructionWork.worldPos(level, postPos))));
    }

    /**
     * Work by hand at a table, or cooking on a campfire: each making takes its time, then its ingredients become the result, and
     * the next making begins until the batch is done.
     */
    @Nullable
    private WorkStatus handWork(ServerLevel level) {
        lookAt(station);
        timer++;
        post.setCurrent(job.recipe().output(), Math.min(1.0, (done + timer / (double) Math.max(1, total)) / Math.max(1, makings.size())));
        if (timer % 20 == 0) {
            body.swing(InteractionHand.MAIN_HAND);
        }
        if (timer < total) {
            return null;
        }
        List<ItemStack> ingredients = takeCarried(makings.get(done));
        if (ingredients == null) {
            return endBatch(WorkStatus.NO_INGREDIENTS);
        }
        MinecraftServer server = level.getServer();
        List<ItemStack> outputs = Production.assemble(server, job.recipe(), ingredients);
        if (outputs.isEmpty()) {
            ingredients.forEach(this::carry);
            return endBatch(WorkStatus.NO_INGREDIENTS);
        }
        if (!fitsInBag(outputs)) {
            // No room for the result: the ingredients left go back with what was made.
            ingredients.forEach(this::carry);
            return endBatch(WorkStatus.WORKING);
        }
        outputs.forEach(this::carry);
        count(outputs.get(0));
        if (job.order() != 0) {
            Production.plan(server, post.kingdomId());
        }
        if (done >= makings.size()) {
            return endBatch(WorkStatus.WORKING);
        }
        timer = 0;
        total = handTicks(level);
        return null;
    }

    // ---------------------------------------------------------------- furnaces

    /**
     * Tends the furnaces of the batch: fills each from above with its share of the ingredient and from the side with its fuel,
     * then goes round them and takes the results out from below as they come; every making counts as soon as its result is out.
     * A furnace that gives nothing for long (it went out, or a player took the ingredient) gives back what is left of the
     * ingredient and is left. When nothing of the batch is left in the furnaces, the results go to the storehouse.
     */
    @Nullable
    private WorkStatus tend(ServerLevel level) {
        timer++;
        if (target != null) {
            lookAt(target.pos);
            if (!near(target.pos, STATION_REACH_SQR)) {
                comeTo(level, target.pos, STATION_REACH_SQR);
                if (++walking > GIVE_UP_TICKS) {
                    // No way to the furnace: what lies in it stays there, and the worker gives it up.
                    leave(target, WorkStatus.NO_WORKSTATION);
                    target = null;
                }
            } else {
                walking = 0;
            }
        }
        if (timer % CHECK_TICKS != 0) {
            return null;
        }
        int stall = Production.fuelTicks(job.recipe()) * 3 + 400;
        boolean bagFull = false;
        for (Load load : loads) {
            if (load.done) {
                continue;
            }
            load.idle += CHECK_TICKS;
            Workstations.Furnace furnace = level.isLoaded(load.pos) ? furnaceAt(level, load.pos) : null;
            if (furnace == null) {
                // The furnace was broken: whatever lay in it fell out where it stood.
                leave(load, WorkStatus.NO_WORKSTATION);
                continue;
            }
            if (near(load.pos, STATION_REACH_SQR)) {
                fill(load, furnace);
                bagFull |= !empty(load, furnace);
                if (load.idle > stall) {
                    takeBack(level, load, furnace);
                    continue;
                }
            } else if (load.idle > stall + GIVE_UP_TICKS) {
                leave(load, WorkStatus.WAITING);
                continue;
            }
            if (finished(load, furnace)) {
                load.done = true;
                unclaim(load.pos);
            }
        }
        countResults(level.getServer());
        post.setCurrent(job.recipe().output(), Math.min(1.0, done / (double) Math.max(1, makings.size())));
        if (bagFull) {
            // The bag is full of results: they go to the storehouse, and what the furnaces still give waits for the next batch.
            return endBatch(WorkStatus.WORKING);
        }
        boolean open = false;
        for (Load load : loads) {
            open |= !load.done;
        }
        if (!open) {
            return endBatch(trouble == null ? WorkStatus.WORKING : trouble);
        }
        Load next = nextTarget(level, stall);
        if (next != target) {
            walking = 0;
            target = next;
        }
        return null;
    }

    /**
     * Puts what is still to go into a furnace: its share of the ingredient from above, its fuel from the side. What the furnace
     * does not take (a player filled it meanwhile) stays in the bag and goes back to the storehouse.
     */
    private void fill(Load load, Workstations.Furnace furnace) {
        if (load.filled()) {
            return;
        }
        String ingredient = makings.get(0).keySet().iterator().next();
        int put = put(furnace.input(), ingredient, load.items - load.loaded);
        int burned = fuel == null ? 0 : put(furnace.fuel(), fuel.item(), load.fuel - load.fueled);
        load.loaded += put;
        load.fueled += burned;
        load.items = load.loaded;
        load.fuel = load.fueled;
        if (put > 0 || burned > 0) {
            load.idle = 0;
            body.swing(InteractionHand.MAIN_HAND);
        }
    }

    /**
     * Moves up to {@code count} of an item from the bag into a slot of a furnace.
     *
     * @return how many went in
     */
    private int put(IItemHandler handler, String item, int count) {
        int have = 0;
        for (int slot = 0; slot < body.carried().getContainerSize(); slot++) {
            ItemStack stack = body.carried().getItem(slot);
            if (!stack.isEmpty() && KingdomStorage.itemId(stack).equals(item)) {
                have += stack.getCount();
            }
        }
        int moving = Math.min(count, have);
        if (moving <= 0) {
            return 0;
        }
        List<ItemStack> taken = takeCarried(Map.of(item, moving));
        if (taken == null) {
            return 0;
        }
        int put = 0;
        for (ItemStack stack : taken) {
            ItemStack rest = handler.insertItem(0, stack, false);
            put += stack.getCount() - rest.getCount();
            if (!rest.isEmpty()) {
                carry(rest);
            }
        }
        return put;
    }

    /**
     * Takes the results out of a furnace from below, as many as the bag holds.
     *
     * @return whether the bag had room for all of them
     */
    private boolean empty(Load load, Workstations.Furnace furnace) {
        Item output = Recipes.item(job.recipe().output());
        if (output == null) {
            return true;
        }
        boolean room = true;
        for (int slot = 0; slot < furnace.output().getSlots(); slot++) {
            ItemStack stack = furnace.output().getStackInSlot(slot);
            if (!stack.is(output)) {
                continue;
            }
            int fits = Math.min(stack.getCount(), bagRoom(stack));
            if (fits < stack.getCount()) {
                room = false;
            }
            if (fits <= 0) {
                continue;
            }
            ItemStack out = furnace.output().extractItem(slot, fits, false);
            if (!out.isEmpty()) {
                load.taken += out.getCount();
                results += out.getCount();
                load.idle = 0;
                carry(out);
            }
        }
        return room;
    }

    /**
     * Whether a furnace is done with: everything went in and every result came out, or nothing of the batch is left in it.
     */
    private boolean finished(Load load, Workstations.Furnace furnace) {
        if (!load.filled()) {
            return false;
        }
        int perMaking = Math.max(1, makings.get(0).values().iterator().next());
        if (load.taken >= load.before + load.loaded / perMaking * job.recipe().outputCount()) {
            return true;
        }
        ItemStack input = furnace.input().getStackInSlot(0);
        if (!input.isEmpty() && makings.get(0).containsKey(KingdomStorage.itemId(input))) {
            return false;
        }
        Item output = Recipes.item(job.recipe().output());
        for (int slot = 0; slot < furnace.output().getSlots(); slot++) {
            if (output != null && furnace.output().getStackInSlot(slot).is(output)) {
                return false;
            }
        }
        return true;
    }

    /**
     * A furnace gave nothing for long: it went out, or a player took the ingredient. What is left of the ingredient of the batch
     * comes back, the furnace is left, and the post says why.
     */
    private void takeBack(ServerLevel level, Load load, Workstations.Furnace furnace) {
        ItemStack input = furnace.input().getStackInSlot(0);
        int perMaking = Math.max(1, makings.get(0).values().iterator().next());
        int cooked = load.taken / Math.max(1, job.recipe().outputCount()) * perMaking;
        int ours = Math.max(0, load.loaded - cooked);
        if (!input.isEmpty() && makings.get(0).containsKey(KingdomStorage.itemId(input)) && ours > 0) {
            carry(furnace.input().extractItem(0, Math.min(ours, input.getCount()), false));
        }
        leave(load, Workstations.isLit(level.getBlockState(load.pos)) ? WorkStatus.WAITING : WorkStatus.NO_FUEL);
    }

    private void leave(Load load, WorkStatus why) {
        load.done = true;
        trouble = why;
        unclaim(load.pos);
    }

    /**
     * The furnace to go to next: one still to fill, then one that gives nothing and is to be emptied of the ingredient, then the
     * nearest one with results waiting. While nothing waits the worker stays by its furnaces.
     */
    @Nullable
    private Load nextTarget(ServerLevel level, int stall) {
        Load best = null;
        int bestRank = Integer.MAX_VALUE;
        double bestDistance = Double.MAX_VALUE;
        boolean byOne = false;
        Item output = Recipes.item(job.recipe().output());
        for (Load load : loads) {
            if (load.done) {
                continue;
            }
            byOne |= near(load.pos, STATION_REACH_SQR);
            int rank;
            if (!load.filled()) {
                rank = 0;
            } else if (load.idle > stall) {
                rank = 1;
            } else if (hasResults(level, load.pos, output)) {
                rank = 2;
            } else {
                rank = 3;
            }
            double distance = body.distanceToSqr(load.pos.getX() + 0.5, load.pos.getY() + 0.5, load.pos.getZ() + 0.5);
            if (rank < bestRank || rank == bestRank && distance < bestDistance) {
                best = load;
                bestRank = rank;
                bestDistance = distance;
            }
        }
        if (bestRank == 3 && byOne) {
            // Nothing to do yet: the worker waits by its furnaces.
            if (best != null) {
                lookAt(best.pos);
            }
            return null;
        }
        return best;
    }

    private static boolean hasResults(ServerLevel level, BlockPos pos, @Nullable Item output) {
        Workstations.Furnace furnace = output == null ? null : furnaceAt(level, pos);
        if (furnace == null) {
            return false;
        }
        for (int slot = 0; slot < furnace.output().getSlots(); slot++) {
            if (furnace.output().getStackInSlot(slot).is(output)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Counts the makings whose results are out of the furnaces.
     */
    private void countResults(MinecraftServer server) {
        int per = Math.max(1, job.recipe().outputCount());
        Item output = Recipes.item(job.recipe().output());
        boolean counted = false;
        while (results >= per && output != null) {
            results -= per;
            count(new ItemStack(output, per));
            counted = true;
        }
        if (counted && job.order() != 0) {
            Production.plan(server, post.kingdomId());
        }
    }

    // ---------------------------------------------------------------- the bag

    /**
     * Takes the given items out of the bag.
     *
     * @return the stacks, or {@code null} if the bag does not hold them all (then nothing was taken)
     */
    @Nullable
    private List<ItemStack> takeCarried(Map<String, Integer> wanted) {
        Map<String, Integer> have = new LinkedHashMap<>();
        for (int slot = 0; slot < body.carried().getContainerSize(); slot++) {
            ItemStack stack = body.carried().getItem(slot);
            if (!stack.isEmpty()) {
                have.merge(KingdomStorage.itemId(stack), stack.getCount(), Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> entry : wanted.entrySet()) {
            if (have.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return null;
            }
        }
        List<ItemStack> taken = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : wanted.entrySet()) {
            int left = entry.getValue();
            for (int slot = 0; slot < body.carried().getContainerSize() && left > 0; slot++) {
                ItemStack stack = body.carried().getItem(slot);
                if (!stack.isEmpty() && KingdomStorage.itemId(stack).equals(entry.getKey())) {
                    ItemStack part = body.carried().removeItem(slot, Math.min(left, stack.getCount()));
                    left -= part.getCount();
                    taken.add(part);
                }
            }
        }
        return taken;
    }

    /**
     * How many of a stack the bag has room for.
     */
    private int bagRoom(ItemStack stack) {
        int room = 0;
        int most = Math.min(stack.getMaxStackSize(), body.carried().getMaxStackSize());
        for (int slot = 0; slot < body.carried().getContainerSize(); slot++) {
            ItemStack lying = body.carried().getItem(slot);
            if (lying.isEmpty()) {
                room += most;
            } else if (ItemStack.isSameItemSameTags(lying, stack)) {
                room += Math.max(0, most - lying.getCount());
            }
        }
        return room;
    }

    /**
     * Whether all the stacks fit into the bag together.
     */
    private boolean fitsInBag(List<ItemStack> stacks) {
        SimpleContainer copy = new SimpleContainer(body.carried().getContainerSize());
        for (int slot = 0; slot < body.carried().getContainerSize(); slot++) {
            copy.setItem(slot, body.carried().getItem(slot).copy());
        }
        for (ItemStack stack : stacks) {
            if (!copy.addItem(stack.copy()).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- the end of a batch

    /**
     * A making is done: it counts for the kingdom, the post and the order at once, the worker gains experience, and its result is
     * no longer on the way.
     */
    private void count(ItemStack result) {
        MinecraftServer server = body.getServer();
        Production.made(server, post, job.order(), result, true);
        citizen().ifPresent(citizen -> {
            citizen.addExperience(1);
            Production.reservations().made(citizen.id(), result.getCount());
        });
        done++;
    }

    /**
     * The batch is over: the reservation ends, the furnaces are free for others, and what the worker carries goes to the storehouse.
     */
    private WorkStatus endBatch(WorkStatus why) {
        giveUp();
        report(why);
        return why;
    }

    private void giveUp() {
        citizen().ifPresent(citizen -> Production.release(citizen.id()));
        clear();
    }

    /**
     * Gives up the batch: the reservation ends; what the worker carries goes back to the storehouse.
     */
    private WorkStatus giveUp(WorkStatus why) {
        giveUp();
        return why;
    }

    private void clear() {
        for (Load load : loads) {
            unclaim(load.pos);
        }
        loads.clear();
        job = null;
        makings = List.of();
        done = 0;
        results = 0;
        fuel = null;
        target = null;
        trouble = null;
        station = null;
        storehouse = null;
        batchPost = null;
        parked = -1;
        step = Step.CHOOSE;
        if (post != null) {
            post.setCurrent("", 0.0);
        }
    }

    /**
     * The kind of recipe the worker is busy with, for tests.
     */
    @Nullable
    RecipeKind busyWith() {
        return job == null ? null : job.recipe().kind();
    }
}
