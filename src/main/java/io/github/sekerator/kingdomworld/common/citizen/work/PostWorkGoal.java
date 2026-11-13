package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.goal.PathBudget;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.citizen.WorkZone;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

/**
 * The work of a body at a work post near players, common to the gathering professions: it gets ready (a tool
 * from the storehouses, armor for dangerous work), works in the zone of its post (the profession's part), and
 * brings what it gathered to the post's container, or to the nearest storehouse if the post has none. Watched
 * work is measured for the abstract simulation. Works by day only and only on the kingdom's own land.
 */
public abstract class PostWorkGoal extends Goal {
    protected static final double REACH_SQR = 3.0 * 3.0;
    private static final double DELIVERY_REACH_SQR = 3.0 * 3.0;
    /** Farthest storehouse a worker without a container walks to, in blocks. */
    private static final int STOREHOUSE_RANGE = 64;
    /** A body farther than this from its post does not work. */
    private static final int MAX_DISTANCE = 96;
    private static final int GIVE_UP_TICKS = 300;

    protected enum Phase {
        PREPARE,
        WORK,
        DELIVER,
        DONE
    }

    protected final CitizenEntity body;
    protected final ProfessionBehavior behavior;
    protected WorkPost post;
    protected BlockPos postPos;
    protected WorkZone zone;
    protected LandCheck land;
    private Phase phase = Phase.DONE;
    private int cooldown;
    private int pathCooldown;
    @Nullable
    private BlockPos deliverTo;
    private boolean deliverToContainer;
    private int deliverTicks;
    private WorkStatus status = WorkStatus.NONE;

    protected PostWorkGoal(CitizenEntity body, ProfessionBehavior behavior) {
        this.body = body;
        this.behavior = behavior;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    // ---------------------------------------------------------------- goal

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        return canWork();
    }

    private boolean canWork() {
        MinecraftServer server = body.getServer();
        BlockPos workplace = body.workplace();
        if (server == null || workplace == null || body.isWounded() || !isWorkTime()) {
            return false;
        }
        Optional<WorkPost> found = PostWork.post(server, new WorldPos(Dimensions.id(body.level()), workplace.getX(),
                workplace.getY(), workplace.getZ()));
        return found.isPresent() && found.get().professionId().equals(body.professionId())
                && body.blockPosition().distSqr(workplace) < (double) maxDistance() * maxDistance();
    }

    /**
     * How far from its post a body still works, in blocks.
     */
    protected int maxDistance() {
        return MAX_DISTANCE;
    }

    /**
     * Ticks a worker walks with its harvest before it stops for a while and tries again.
     */
    protected int deliveryPatience() {
        return GIVE_UP_TICKS;
    }

    /**
     * Whether it is time to work; by day.
     */
    protected boolean isWorkTime() {
        return body.level().isDay();
    }

    @Override
    public boolean canContinueToUse() {
        return phase != Phase.DONE && canWork();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        MinecraftServer server = body.getServer();
        BlockPos workplace = body.workplace();
        if (server == null || workplace == null) {
            phase = Phase.DONE;
            return;
        }
        ServerLevel level = (ServerLevel) body.level();
        post = PostWork.post(server, new WorldPos(Dimensions.id(level), workplace.getX(), workplace.getY(), workplace.getZ()))
                .orElse(null);
        if (post == null) {
            phase = Phase.DONE;
            return;
        }
        postPos = workplace;
        zone = WorkZone.of(post.pos(), post.professionId(), post.radius(), GatheringSettings.rules().mineLevels());
        land = new LandCheck(level, post.kingdomId());
        deliverTo = null;
        phase = isCarryingFull() ? Phase.DELIVER : Phase.PREPARE;
        onStart();
    }

    @Override
    public void stop() {
        body.getNavigation().stop();
        if (phase != Phase.DONE) {
            cooldown = 40;
        }
        phase = Phase.DONE;
        onStop();
    }

    @Override
    public void tick() {
        if (pathCooldown > 0) {
            pathCooldown--;
        }
        switch (phase) {
            case PREPARE -> {
                if (prepare()) {
                    phase = Phase.WORK;
                } else {
                    finish(200);
                }
            }
            case WORK -> {
                if (status == WorkStatus.WORKING || status == WorkStatus.WAITING || status == WorkStatus.NONE) {
                    PostWork.worked(post);
                }
                WorkStatus result = work((ServerLevel) body.level());
                if (result != null) {
                    report(result);
                    if (hasCarried()) {
                        phase = Phase.DELIVER;
                        deliverTo = null;
                    } else {
                        finish(result == WorkStatus.WORKING ? 20 : 200);
                    }
                }
            }
            case DELIVER -> {
                PostWork.worked(post);
                deliver((ServerLevel) body.level());
            }
            default -> {
            }
        }
    }

    protected void finish(int wait) {
        phase = Phase.DONE;
        cooldown = wait;
    }

    // ---------------------------------------------------------------- the profession's part

    /**
     * Called when the work starts, after the post and zone are known.
     */
    protected void onStart() {
    }

    protected void onStop() {
    }

    /**
     * One tick of work in the zone.
     *
     * @return {@code null} to go on, otherwise how the round of work ended: {@link WorkStatus#WORKING} when the
     * worker should bring its harvest and come back, or why there is nothing to do
     */
    @Nullable
    protected abstract WorkStatus work(ServerLevel level);

    /**
     * Whether the harvest goes into the container of the post; otherwise straight to a storehouse.
     */
    protected boolean deliversToPost() {
        return true;
    }

    /**
     * Strength of tool the work needs now, 0 if any will do.
     */
    protected int neededTier() {
        return 0;
    }

    // ---------------------------------------------------------------- getting ready

    private boolean prepare() {
        MinecraftServer server = body.getServer();
        UUID kingdom = post.kingdomId();
        Equipment.Hands hands = Equipment.of(body);
        if (behavior.tool() != Equipment.ToolNeed.NONE
                && !Equipment.equipTool(server, kingdom, hands, EquipmentSlot.MAINHAND, behavior.tool(), neededTier(), post.pos())
                && behavior.tool().required()) {
            report(WorkStatus.NO_TOOL);
            return false;
        }
        if (behavior.secondTool() != Equipment.ToolNeed.NONE) {
            Equipment.equipTool(server, kingdom, hands, EquipmentSlot.OFFHAND, behavior.secondTool(), 0, post.pos());
        }
        if (behavior.risky()) {
            Equipment.equipArmor(server, kingdom, hands, post.pos());
        }
        return true;
    }

    // ---------------------------------------------------------------- helpers for the work

    protected void report(WorkStatus value) {
        status = value;
        citizen().ifPresent(citizen -> PostWork.setStatus(body.getServer(), citizen, value));
    }

    protected Optional<Citizen> citizen() {
        UUID id = body.citizenId();
        return id == null || body.getServer() == null ? Optional.empty()
                : KingdomWorldApi.state(body.getServer()).citizens().get(id);
    }

    /**
     * Walks towards a block; paths come from the {@link PathBudget}.
     */
    protected void moveTo(BlockPos pos) {
        if (pathCooldown > 0 || (!body.getNavigation().isDone() && body.getNavigation().getTargetPos() != null
                && body.getNavigation().getTargetPos().closerThan(pos, 1.5))) {
            return;
        }
        if (PathBudget.tryAcquire()) {
            body.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.6);
            pathCooldown = 20;
        }
    }

    protected boolean near(BlockPos pos, double reachSqr) {
        return body.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= reachSqr;
    }

    protected void lookAt(BlockPos pos) {
        body.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    /**
     * Counts a gathered stack and puts it into the worker's bag; what does not fit falls to the ground.
     */
    protected void gather(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        PostWork.gathered(body.getServer(), post, stack);
        carry(stack);
    }

    protected void carry(ItemStack stack) {
        ItemStack rest = body.carried().addItem(stack);
        if (!rest.isEmpty()) {
            Block.popResource(body.level(), body.blockPosition(), rest);
        }
    }

    /**
     * Wears down the tool in the main hand and counts the use.
     */
    protected void useTool(int uses) {
        Equipment.wear(Equipment.of(body), EquipmentSlot.MAINHAND, uses, body.getRandom());
        PostWork.acted(post, uses);
        body.swing(InteractionHand.MAIN_HAND);
        citizen().ifPresent(citizen -> citizen.addExperience(uses));
    }

    protected ItemStack tool() {
        return body.getMainHandItem();
    }

    protected boolean hasCarried() {
        return !body.carried().isEmpty();
    }

    /**
     * Whether the bag has no room left for a full stack.
     */
    protected boolean isCarryingFull() {
        for (int slot = 0; slot < body.carried().getContainerSize(); slot++) {
            if (body.carried().getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- delivering

    private void deliver(ServerLevel level) {
        if (!hasCarried()) {
            finish(20);
            return;
        }
        if (deliverTo == null) {
            PostContainers.Container container = deliversToPost() ? PostContainers.find(level, postPos) : null;
            if (deliversToPost()) {
                post.setHasContainer(container != null);
            }
            deliverToContainer = container != null;
            deliverTo = container != null ? postPos : nearestStorehouse(level);
            deliverTicks = 0;
            if (deliverTo == null) {
                report(WorkStatus.NO_STOREHOUSE);
                finish(400);
                return;
            }
        }
        if (++deliverTicks > deliveryPatience()) {
            deliverTo = null;
            finish(100);
            return;
        }
        if (!near(deliverTo, DELIVERY_REACH_SQR)) {
            moveTo(deliverTo);
            return;
        }
        IItemHandler target = null;
        if (deliverToContainer) {
            PostContainers.Container container = PostContainers.find(level, postPos);
            target = container == null ? null : container.handler();
        } else if (level.getBlockState(deliverTo).is(ModRegistries.STOREHOUSE.get()) && !KingdomWorldApi.isReadOnly(level.getServer())) {
            target = KingdomSavedData.get(level.getServer()).storehouses()
                    .get(new WorldPos(Dimensions.id(level), deliverTo.getX(), deliverTo.getY(), deliverTo.getZ())).orElse(null);
        }
        boolean moved = false;
        if (target != null) {
            for (int slot = 0; slot < body.carried().getContainerSize(); slot++) {
                ItemStack stack = body.carried().getItem(slot);
                if (!stack.isEmpty()) {
                    ItemStack rest = ItemHandlerHelper.insertItemStacked(target, stack, false);
                    moved |= rest.getCount() != stack.getCount();
                    body.carried().setItem(slot, rest);
                }
            }
        }
        if (!moved && deliverToContainer && target != null) {
            // The container is full: the worker takes its harvest to a storehouse instead.
            deliverToContainer = false;
            deliverTo = nearestStorehouse(level);
            if (deliverTo != null) {
                return;
            }
        }
        deliverTo = null;
        if (hasCarried()) {
            report(WorkStatus.STORAGE_FULL);
            finish(400);
        } else {
            finish(20);
        }
    }

    /**
     * The nearest loaded storehouse of the kingdom within reach, or {@code null}.
     */
    @Nullable
    protected BlockPos nearestStorehouse(ServerLevel level) {
        BlockPos here = body.blockPosition();
        WorldPos near = new WorldPos(Dimensions.id(level), here.getX(), here.getY(), here.getZ());
        for (StorehouseInventory inventory : KingdomStorage.storehouses(level.getServer(), post.kingdomId(), near)) {
            if (inventory.pos().distanceSqr(near) > (double) STOREHOUSE_RANGE * STOREHOUSE_RANGE) {
                break;
            }
            BlockPos pos = new BlockPos(inventory.pos().x(), inventory.pos().y(), inventory.pos().z());
            if (level.isLoaded(pos) && level.getBlockState(pos).is(ModRegistries.STOREHOUSE.get())) {
                return pos;
            }
        }
        return null;
    }
}
