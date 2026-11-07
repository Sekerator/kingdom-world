package io.github.sekerator.kingdomworld.common.production;

import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.work.PostContainers;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWorkGoal;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehavior;
import io.github.sekerator.kingdomworld.common.citizen.work.ZoneSurveys;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.production.ProductionPlanner;
import io.github.sekerator.kingdomworld.core.production.RecipeKind;
import io.github.sekerator.kingdomworld.core.production.Reservations;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A workshop worker near players. It picks the next making (an order of the king, or the post's own work), sets
 * the ingredients aside, fetches them (from the post's container if carriers brought them, else from a
 * storehouse), goes to the workstation and makes the thing: by hand at a table, on a campfire, or with a real
 * furnace that it fills with the ingredient and fuel from above and from the side and empties from below, so that
 * furnaces of other mods built the same way serve too. The result goes to the storehouse.
 */
final class WorkshopGoal extends PostWorkGoal {
    private static final double STATION_REACH_SQR = 2.5 * 2.5;
    private static final int GIVE_UP_TICKS = 600;
    /** How long a worker waits at its post for carriers to bring its ingredients. */
    private static final int CARRIER_WAIT_TICKS = 200;

    private enum Step {
        CHOOSE,
        FETCH,
        GO,
        WORK,
        FURNACE
    }

    private Step step = Step.CHOOSE;
    @Nullable
    private ProductionPlanner.Job job;
    private Map<String, Integer> fuel = Map.of();
    @Nullable
    private BlockPos station;
    @Nullable
    private BlockPos storehouse;
    private int timer;
    private int total;
    private int walking;
    private int waited;
    private int extracted;

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
        job = null;
        step = Step.CHOOSE;
    }

    @Override
    protected void onStop() {
        if (job != null) {
            giveUp();
        }
    }

    @Override
    protected WorkStatus work(ServerLevel level) {
        return switch (step) {
            case CHOOSE -> choose(level);
            case FETCH -> fetch(level);
            case GO -> go(level);
            case WORK -> handWork();
            case FURNACE -> furnace(level);
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
        BlockPos at = pickStation(level, known.getOrDefault(found.recipe().kind().station(), List.of()), found);
        if (at == null) {
            ZoneSurveys.request(post.pos());
            return WorkStatus.NO_WORKSTATION;
        }
        Map<String, Integer> fuelNeeded = new LinkedHashMap<>();
        if (found.recipe().kind().needsFuel() && search.fuel() != null && needsFuel(level, at)) {
            fuelNeeded.put(search.fuel().item(), 1);
        }
        job = found;
        fuel = fuelNeeded;
        station = at;
        storehouse = null;
        walking = 0;
        waited = 0;
        extracted = 0;
        Production.reserve(citizen.get().id(), post.kingdomId(), found, fuelNeeded);
        post.setCurrent(found.recipe().output(), 0.0);
        step = Step.FETCH;
        report(WorkStatus.WORKING);
        return null;
    }

    /**
     * The nearest workstation of the kind that is still there; for a furnace one that is free for the ingredient.
     */
    @Nullable
    private BlockPos pickStation(ServerLevel level, List<BlockPos> candidates, ProductionPlanner.Job found) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        String kind = found.recipe().kind().station();
        for (BlockPos pos : candidates) {
            if (!level.isLoaded(pos) || !land.owns(pos) || !kind.equals(Workstations.kindOf(level, pos, level.getBlockState(pos)))) {
                continue;
            }
            if (found.recipe().kind().needsFuel() && !furnaceFree(level, pos, found.picks().keySet())) {
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

    private static boolean furnaceFree(ServerLevel level, BlockPos pos, Set<String> inputs) {
        BlockEntity entity = level.getBlockEntity(pos);
        Workstations.Furnace furnace = entity == null ? null : Workstations.furnace(entity);
        if (furnace == null) {
            return false;
        }
        ItemStack input = furnace.input().getStackInSlot(0);
        return input.isEmpty() || inputs.contains(KingdomStorage.itemId(input)) && input.getCount() < input.getMaxStackSize();
    }

    /**
     * Whether a furnace needs fuel to cook: it is not burning and has nothing to burn.
     */
    private static boolean needsFuel(ServerLevel level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        Workstations.Furnace furnace = entity == null ? null : Workstations.furnace(entity);
        return furnace != null && furnace.fuel().getStackInSlot(0).isEmpty();
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
            step = Step.GO;
            walking = 0;
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
        // Fetched from the storehouse by the worker itself.
        if (storehouse == null) {
            storehouse = nearestStorehouse(level);
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
        step = Step.GO;
        walking = 0;
        return null;
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

    // ---------------------------------------------------------------- making

    @Nullable
    private WorkStatus go(ServerLevel level) {
        if (station == null || !level.isLoaded(station)
                || !job.recipe().kind().station().equals(Workstations.kindOf(level, station, level.getBlockState(station)))) {
            ZoneSurveys.request(post.pos());
            return giveUp(WorkStatus.NO_WORKSTATION);
        }
        if (!near(station, STATION_REACH_SQR)) {
            moveTo(station);
            if (++walking > GIVE_UP_TICKS) {
                return giveUp(WorkStatus.NO_WORKSTATION);
            }
            return null;
        }
        timer = 0;
        if (job.recipe().kind().needsFuel()) {
            return load(level);
        }
        int experience = citizen().map(Citizen::experience).orElse(0);
        total = ProductionSettings.rules().ticks(job.recipe(), experience);
        step = Step.WORK;
        return null;
    }

    /**
     * Work by hand at a table, or cooking on a campfire: it takes its time, then the ingredients become the result.
     */
    @Nullable
    private WorkStatus handWork() {
        lookAt(station);
        timer++;
        post.setCurrent(job.recipe().output(), timer / (double) Math.max(1, total));
        if (timer % 20 == 0) {
            body.swing(InteractionHand.MAIN_HAND);
        }
        if (timer < total) {
            return null;
        }
        List<ItemStack> ingredients = takeCarried(job.picks());
        if (ingredients == null) {
            return giveUp(WorkStatus.NO_INGREDIENTS);
        }
        MinecraftServer server = body.getServer();
        List<ItemStack> outputs = Production.assemble(server, job.recipe(), ingredients);
        if (outputs.isEmpty()) {
            ingredients.forEach(this::carry);
            return giveUp(WorkStatus.NO_INGREDIENTS);
        }
        outputs.forEach(this::carry);
        return finished(outputs.get(0));
    }

    /**
     * Puts the ingredient into the furnace from above and fuel from the side.
     */
    @Nullable
    private WorkStatus load(ServerLevel level) {
        BlockEntity entity = level.getBlockEntity(station);
        Workstations.Furnace furnace = entity == null ? null : Workstations.furnace(entity);
        if (furnace == null) {
            return giveUp(WorkStatus.NO_WORKSTATION);
        }
        List<ItemStack> ingredients = takeCarried(job.picks());
        if (ingredients == null) {
            return giveUp(WorkStatus.NO_INGREDIENTS);
        }
        for (ItemStack ingredient : ingredients) {
            ItemStack rest = furnace.input().insertItem(0, ingredient, false);
            if (!rest.isEmpty()) {
                // A player filled the furnace meanwhile.
                carry(rest);
                return giveUp(WorkStatus.WAITING);
            }
        }
        List<ItemStack> burnable = takeCarried(fuel);
        if (burnable != null) {
            for (ItemStack stack : burnable) {
                ItemStack rest = furnace.fuel().insertItem(0, stack, false);
                if (!rest.isEmpty()) {
                    carry(rest);
                }
            }
        }
        body.swing(InteractionHand.MAIN_HAND);
        extracted = 0;
        total = Math.max(20, job.recipe().ticks());
        step = Step.FURNACE;
        return null;
    }

    /**
     * Waits by the furnace and takes the result out from below.
     */
    @Nullable
    private WorkStatus furnace(ServerLevel level) {
        lookAt(station);
        timer++;
        post.setCurrent(job.recipe().output(), Math.min(1.0, timer / (double) (total + 10)));
        if (timer % 10 != 0) {
            return null;
        }
        BlockEntity entity = level.isLoaded(station) ? level.getBlockEntity(station) : null;
        Workstations.Furnace furnace = entity == null ? null : Workstations.furnace(entity);
        if (furnace == null) {
            return giveUp(WorkStatus.NO_WORKSTATION);
        }
        Item output = Recipes.item(job.recipe().output());
        int expected = job.recipe().outputCount();
        for (int slot = 0; slot < furnace.output().getSlots() && output != null && extracted < expected; slot++) {
            ItemStack stack = furnace.output().getStackInSlot(slot);
            if (stack.is(output)) {
                ItemStack taken = furnace.output().extractItem(slot, expected - extracted, false);
                extracted += taken.getCount();
                carry(taken);
            }
        }
        if (extracted >= expected && output != null) {
            return finished(new ItemStack(output, expected));
        }
        if (timer > total * 3 + 400) {
            // The furnace went out or a player took the ingredient: what is left of it comes back.
            ItemStack input = furnace.input().getStackInSlot(0);
            if (!input.isEmpty() && job.picks().containsKey(KingdomStorage.itemId(input))) {
                carry(furnace.input().extractItem(0, Math.min(input.getCount(), job.picks().get(KingdomStorage.itemId(input))), false));
            }
            return giveUp(Workstations.isLit(level.getBlockState(station)) ? WorkStatus.WAITING : WorkStatus.NO_FUEL);
        }
        return null;
    }

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
     * A making is done: it is counted, the reservation ends and the result goes to the storehouse.
     */
    private WorkStatus finished(ItemStack result) {
        MinecraftServer server = body.getServer();
        Production.made(server, post, job.order(), result, true);
        citizen().ifPresent(citizen -> {
            citizen.addExperience(1);
            Production.release(citizen.id());
        });
        if (job.order() != 0) {
            Production.plan(server, post.kingdomId());
        }
        clear();
        report(WorkStatus.WORKING);
        return WorkStatus.WORKING;
    }

    private void giveUp() {
        citizen().ifPresent(citizen -> Production.release(citizen.id()));
        clear();
    }

    /**
     * Gives up the making: the reservation ends; what the worker carries goes back to the storehouse.
     */
    private WorkStatus giveUp(WorkStatus why) {
        giveUp();
        return why;
    }

    private void clear() {
        job = null;
        fuel = Map.of();
        station = null;
        storehouse = null;
        step = Step.CHOOSE;
        post.setCurrent("", 0.0);
    }

    /**
     * The kind of recipe the worker is busy with, for tests.
     */
    @Nullable
    RecipeKind busyWith() {
        return job == null ? null : job.recipe().kind();
    }
}
