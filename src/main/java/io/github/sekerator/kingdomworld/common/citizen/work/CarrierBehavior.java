package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.CarrierPlanner;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The carrier: takes what workers left in the containers of their posts to the storehouses, brings posts the
 * inputs they ask for (torches for a mine; the production chains of later professions take their inputs this
 * way) and moves goods from a storehouse that is nearly full to an emptier one further away. Far from players
 * the carriers of a kingdom move a number of items per step from loaded post containers to the storehouses.
 */
public final class CarrierBehavior implements ProfessionBehavior {

    @Override
    public boolean isWorkplace(BlockState state) {
        return false;
    }

    @Override
    public Goal createWork(CitizenEntity body) {
        return new WorkGoal(body, this);
    }

    @Override
    public void workAbstractly(MinecraftServer server, Citizen citizen, int intervalTicks) {
        // The carriers of a kingdom work together, see stepAbstractly.
    }

    @Override
    public void survey(ServerLevel level, Citizen citizen) {
    }

    // ---------------------------------------------------------------- what there is to carry

    /**
     * The item handler at a position: a storehouse's inventory, or the handler of a block entity.
     */
    @Nullable
    static IItemHandler handlerAt(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        if (level.getBlockState(pos).is(ModRegistries.STOREHOUSE.get())) {
            return KingdomSavedData.get(level.getServer()).storehouses()
                    .get(new WorldPos(Dimensions.id(level), pos.getX(), pos.getY(), pos.getZ())).orElse(null);
        }
        BlockEntity entity = level.getBlockEntity(pos);
        return entity == null ? null : entity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
    }

    private static boolean isStorehouse(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(ModRegistries.STOREHOUSE.get());
    }

    /**
     * A container of a post with goods to take away.
     */
    record Source(WorkPost post, PostContainers.Container container, Predicate<ItemStack> keep) {
    }

    /**
     * The containers of the kingdom's posts that hold goods to take away (not the carriers' own posts, not posts
     * whose container is a storehouse).
     */
    static List<Source> sources(ServerLevel level, UUID kingdomId) {
        List<Source> sources = new ArrayList<>();
        String dimension = Dimensions.id(level);
        for (WorkPost post : KingdomWorldApi.state(level.getServer()).citizens().posts().postsOf(kingdomId)) {
            if (!post.pos().dimension().equals(dimension) || post.professionId().equals(Professions.CARRIER.id())) {
                continue;
            }
            PostContainers.Container container = PostContainers.find(level, post.pos());
            if (container == null || isStorehouse(level, container.pos())) {
                continue;
            }
            ProfessionBehavior behavior = ProfessionBehaviors.get(post.professionId()).orElse(null);
            Predicate<ItemStack> keep = behavior == null ? stack -> false : behavior::isInput;
            sources.add(new Source(post, container, keep));
        }
        return sources;
    }

    private static int takeable(Source source) {
        int count = 0;
        IItemHandler handler = source.container().handler();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && !source.keep().test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static WorldPos at(ServerLevel level, BlockPos pos) {
        return new WorldPos(Dimensions.id(level), pos.getX(), pos.getY(), pos.getZ());
    }

    // ---------------------------------------------------------------- far from players

    /**
     * One step of the carriers of a kingdom without a body: they empty loaded post containers into the
     * storehouses, the fullest first, up to what their number can move in the time of a step.
     */
    public static void stepAbstractly(MinecraftServer server, UUID kingdomId, int intervalTicks) {
        List<Citizen> carriers = new ArrayList<>();
        for (Citizen citizen : KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdomId)) {
            if (citizen.professionId().equals(Professions.CARRIER.id()) && CitizenBodies.body(citizen.id()) == null) {
                if (citizen.workplace() == null) {
                    citizen.setWorkStatus(WorkStatus.NO_WORKPLACE);
                } else if (citizen.state() == CitizenState.WOUNDED) {
                    PostWork.setStatus(server, citizen, WorkStatus.WOUNDED);
                } else {
                    carriers.add(citizen);
                }
            }
        }
        if (carriers.isEmpty()) {
            return;
        }
        int capacity = GatheringSettings.rules().carried(carriers.size(), intervalTicks);
        int moved = 0;
        for (ServerLevel level : server.getAllLevels()) {
            List<Source> sources = sources(level, kingdomId);
            if (sources.isEmpty()) {
                continue;
            }
            List<CarrierPlanner.Stock> stocks = new ArrayList<>();
            for (Source source : sources) {
                stocks.add(new CarrierPlanner.Stock(at(level, source.container().pos()), takeable(source),
                        PostContainers.freeSlots(source.container().handler()), source.container().handler().getSlots()));
            }
            List<CarrierPlanner.Stock> storehouses = new ArrayList<>();
            for (StorehouseInventory inventory : KingdomStorage.storehouses(server, kingdomId, null)) {
                storehouses.add(new CarrierPlanner.Stock(inventory.pos(), KingdomStorage.itemCount(inventory),
                        KingdomStorage.freeSlots(inventory), inventory.getSlots()));
            }
            for (CarrierPlanner.Task task : CarrierPlanner.plan(stocks, storehouses, capacity - moved)) {
                Source source = sources.stream().filter(s -> at(level, s.container().pos()).equals(task.from())).findFirst().orElse(null);
                if (source != null) {
                    moved += move(server, source, task.to(), task.amount(), kingdomId);
                }
            }
            if (moved >= capacity) {
                break;
            }
        }
        for (Citizen carrier : carriers) {
            PostWork.setStatus(server, carrier, moved > 0 ? WorkStatus.WORKING : WorkStatus.NOTHING_TO_CARRY);
            if (moved > 0) {
                carrier.addExperience(Math.max(1, moved / carriers.size() / 16));
            }
        }
    }

    /**
     * Moves up to {@code amount} items from a post container into a storehouse (or any storehouse if that one is
     * full).
     *
     * @return items moved
     */
    private static int move(MinecraftServer server, Source source, WorldPos target, int amount, UUID kingdomId) {
        IItemHandler from = source.container().handler();
        StorehouseInventory to = KingdomSavedData.get(server).storehouses().get(target).orElse(null);
        int moved = 0;
        for (int slot = 0; slot < from.getSlots() && moved < amount; slot++) {
            ItemStack stack = from.getStackInSlot(slot);
            if (stack.isEmpty() || source.keep().test(stack)) {
                continue;
            }
            ItemStack taken = from.extractItem(slot, Math.min(amount - moved, stack.getCount()), false);
            ItemStack rest = to == null ? taken : ItemHandlerHelper.insertItemStacked(to, taken, false);
            if (!rest.isEmpty()) {
                rest = KingdomStorage.insert(server, kingdomId, rest, target);
            }
            moved += taken.getCount() - rest.getCount();
            if (!rest.isEmpty()) {
                // No room anywhere: what is left goes back.
                ItemStack back = from.insertItem(slot, rest, false);
                if (!back.isEmpty()) {
                    ItemHandlerHelper.insertItemStacked(from, back, false);
                }
                break;
            }
        }
        return moved;
    }

    // ---------------------------------------------------------------- near players

    /**
     * The carrier at work: plans a trip, walks to the source, takes the goods, walks to the target, puts them
     * there.
     */
    static final class WorkGoal extends PostWorkGoal {
        private static final int GIVE_UP_TICKS = 400;
        @Nullable
        private CarrierPlanner.Task task;
        private boolean loaded;
        private int walking;

        WorkGoal(CitizenEntity body, ProfessionBehavior behavior) {
            super(body, behavior);
        }

        @Override
        protected void onStart() {
            task = null;
        }

        @Override
        protected boolean deliversToPost() {
            return false;
        }

        @Override
        protected WorkStatus work(ServerLevel level) {
            if (task == null) {
                if (hasCarried()) {
                    return WorkStatus.WORKING;
                }
                task = plan(level).orElse(null);
                loaded = false;
                walking = 0;
                if (task == null) {
                    return WorkStatus.NOTHING_TO_CARRY;
                }
                report(WorkStatus.WORKING);
            }
            BlockPos goal = blockPos(loaded ? task.to() : task.from());
            if (!near(goal, REACH_SQR)) {
                moveTo(goal);
                if (++walking > GIVE_UP_TICKS) {
                    task = null;
                    return hasCarried() ? WorkStatus.WORKING : null;
                }
                return null;
            }
            IItemHandler handler = handlerAt(level, goal);
            if (handler == null) {
                task = null;
                return hasCarried() ? WorkStatus.WORKING : null;
            }
            if (!loaded) {
                pick(handler);
                loaded = true;
                walking = 0;
                if (!hasCarried()) {
                    task = null;
                }
                return null;
            }
            drop(handler);
            task = null;
            return hasCarried() ? WorkStatus.WORKING : null;
        }

        private static BlockPos blockPos(WorldPos pos) {
            return new BlockPos(pos.x(), pos.y(), pos.z());
        }

        private Optional<CarrierPlanner.Task> plan(ServerLevel level) {
            int range = GatheringSettings.rules().carrierRange();
            WorldPos home = post.pos();
            List<Source> sources = sources(level, post.kingdomId());
            List<CarrierPlanner.Stock> stocks = new ArrayList<>();
            List<CarrierPlanner.Request> requests = new ArrayList<>();
            List<CarrierPlanner.Stock> storehouses = new ArrayList<>();
            for (StorehouseInventory inventory : KingdomStorage.storehouses(level.getServer(), post.kingdomId(), home)) {
                BlockPos pos = blockPos(inventory.pos());
                if (inventory.pos().dimension().equals(home.dimension()) && level.isLoaded(pos) && isStorehouse(level, pos)) {
                    storehouses.add(new CarrierPlanner.Stock(inventory.pos(), KingdomStorage.itemCount(inventory),
                            KingdomStorage.freeSlots(inventory), inventory.getSlots()));
                }
            }
            for (Source source : sources) {
                stocks.add(new CarrierPlanner.Stock(at(level, source.container().pos()), takeable(source),
                        PostContainers.freeSlots(source.container().handler()), source.container().handler().getSlots()));
                ProfessionBehavior behavior = ProfessionBehaviors.get(source.post().professionId()).orElse(null);
                if (behavior == null) {
                    continue;
                }
                Map<String, Integer> wanted = behavior.wanted(source.post(), PostContainers.contents(source.container().handler()));
                for (Map.Entry<String, Integer> entry : wanted.entrySet()) {
                    WorldPos holder = storehouseWith(level, entry.getKey(), storehouses);
                    if (holder != null) {
                        requests.add(new CarrierPlanner.Request(at(level, source.container().pos()), entry.getKey(),
                                entry.getValue(), holder));
                    }
                }
            }
            return CarrierPlanner.next(home, stocks, storehouses, requests, body.carried().getContainerSize() * 64, range);
        }

        @Nullable
        private WorldPos storehouseWith(ServerLevel level, String item, List<CarrierPlanner.Stock> storehouses) {
            for (CarrierPlanner.Stock stock : storehouses) {
                IItemHandler handler = handlerAt(level, blockPos(stock.pos()));
                if (handler != null) {
                    for (int slot = 0; slot < handler.getSlots(); slot++) {
                        if (PostWork.itemId(handler.getStackInSlot(slot)).equals(item)) {
                            return stock.pos();
                        }
                    }
                }
            }
            return null;
        }

        private void pick(IItemHandler handler) {
            int left = task.amount();
            Predicate<ItemStack> keep = keepAtSource();
            for (int slot = 0; slot < handler.getSlots() && left > 0; slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty() || keep.test(stack)
                        || (!task.item().isEmpty() && !PostWork.itemId(stack).equals(task.item()))) {
                    continue;
                }
                ItemStack simulated = handler.extractItem(slot, Math.min(left, stack.getCount()), true);
                ItemStack fits = body.carried().addItem(simulated.copy());
                int taken = simulated.getCount() - fits.getCount();
                if (taken <= 0) {
                    break;
                }
                handler.extractItem(slot, taken, false);
                left -= taken;
            }
        }

        /**
         * What a collecting carrier leaves in a post's container: the inputs of the post's work.
         */
        private Predicate<ItemStack> keepAtSource() {
            if (task.kind() != CarrierPlanner.Kind.COLLECT) {
                return stack -> false;
            }
            for (Source source : sources((ServerLevel) body.level(), post.kingdomId())) {
                if (at((ServerLevel) body.level(), source.container().pos()).equals(task.from())) {
                    return source.keep();
                }
            }
            return stack -> false;
        }

        private void drop(IItemHandler handler) {
            for (int slot = 0; slot < body.carried().getContainerSize(); slot++) {
                ItemStack stack = body.carried().getItem(slot);
                if (!stack.isEmpty()) {
                    body.carried().setItem(slot, ItemHandlerHelper.insertItemStacked(handler, stack, false));
                }
            }
            citizen().ifPresent(citizen -> citizen.addExperience(1));
        }
    }
}
