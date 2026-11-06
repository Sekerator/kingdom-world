package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSettings;
import io.github.sekerator.kingdomworld.common.citizen.goal.PathBudget;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A farmer at work near players: looks over the farm around its composter a little every tick, walks to ripe
 * crops, harvests and replants them, and carries the harvest to the nearest storehouse of the kingdom. Works by
 * day only and only on the kingdom's own land.
 */
final class FarmerWorkGoal extends Goal {
    /** Positions of the farm looked at per tick. */
    private static final int SCAN_PER_TICK = 160;
    /** Ripe crops remembered from one look over the farm. */
    private static final int MAX_TARGETS = 24;
    private static final double REACH_SQR = 2.4 * 2.4;
    private static final double STOREHOUSE_REACH_SQR = 3.0 * 3.0;
    /** Farthest storehouse a farmer walks to, in blocks. */
    private static final int STOREHOUSE_RANGE = 64;
    private static final int GIVE_UP_TICKS = 240;

    private enum Phase {
        SCAN,
        HARVEST,
        DELIVER,
        DONE
    }

    private final CitizenEntity body;
    private Phase phase = Phase.DONE;
    private int cooldown;
    private BlockPos center;
    private int radius;
    private int scanIndex;
    private int plots;
    private final List<BlockPos> targets = new ArrayList<>();
    private final Map<Item, Integer> harvested = new HashMap<>();
    @Nullable
    private BlockPos target;
    @Nullable
    private BlockPos storehouse;
    private int ticksOnTarget;
    private int pathCooldown;

    FarmerWorkGoal(CitizenEntity body) {
        this.body = body;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        return canWork();
    }

    private boolean canWork() {
        BlockPos workplace = body.workplace();
        return workplace != null && body.level().isDay() && !body.isWounded() && body.getServer() != null
                && body.blockPosition().distSqr(workplace) < 64 * 64;
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
        center = body.workplace();
        radius = CitizenSettings.rules().farmRadius();
        scanIndex = 0;
        plots = 0;
        targets.clear();
        target = null;
        storehouse = null;
        phase = isCarryingFull() ? Phase.DELIVER : Phase.SCAN;
    }

    @Override
    public void stop() {
        body.getNavigation().stop();
        if (phase != Phase.DONE) {
            cooldown = 40;
        }
        phase = Phase.DONE;
    }

    @Override
    public void tick() {
        if (pathCooldown > 0) {
            pathCooldown--;
        }
        switch (phase) {
            case SCAN -> scan();
            case HARVEST -> harvest();
            case DELIVER -> deliver();
            default -> {
            }
        }
    }

    private void finish(int wait) {
        phase = Phase.DONE;
        cooldown = wait;
    }

    // ---------------------------------------------------------------- looking over the farm

    private void scan() {
        ServerLevel level = (ServerLevel) body.level();
        int side = 2 * radius + 1;
        int layers = 2 * FarmerBehavior.FARM_DEPTH + 1;
        int total = side * side * layers;
        UUID kingdom = body.kingdomId();
        String dimension = Dimensions.id(level);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int end = Math.min(total, scanIndex + SCAN_PER_TICK);
        for (; scanIndex < end; scanIndex++) {
            int layer = scanIndex % layers;
            int column = scanIndex / layers;
            int x = center.getX() - radius + column % side;
            int z = center.getZ() - radius + column / side;
            pos.set(x, center.getY() - FarmerBehavior.FARM_DEPTH + layer, z);
            if (!level.isLoaded(pos) || kingdom == null
                    || !kingdom.equals(KingdomWorldApi.state(level.getServer()).territory().ownerOf(dimension, ChunkKey.of(x >> 4, z >> 4)))) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (FarmCrops.isCrop(state)) {
                plots++;
                if (FarmCrops.isMature(state) && targets.size() < MAX_TARGETS) {
                    targets.add(pos.immutable());
                }
            }
        }
        if (scanIndex < total) {
            return;
        }
        if (plots > 0) {
            record(plots, null);
        }
        if (!targets.isEmpty() && !isCarryingFull()) {
            phase = Phase.HARVEST;
        } else if (!body.carried().isEmpty()) {
            phase = Phase.DELIVER;
        } else {
            finish(200);
        }
    }

    // ---------------------------------------------------------------- harvesting

    private void harvest() {
        ServerLevel level = (ServerLevel) body.level();
        if (target == null) {
            target = nearestTarget();
            ticksOnTarget = 0;
            if (target == null) {
                phase = body.carried().isEmpty() ? Phase.DONE : Phase.DELIVER;
                if (phase == Phase.DONE) {
                    cooldown = 100;
                }
                return;
            }
        }
        BlockState state = level.getBlockState(target);
        if (!FarmCrops.isMature(state)) {
            target = null;
            return;
        }
        if (++ticksOnTarget > GIVE_UP_TICKS) {
            target = null;
            return;
        }
        body.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (body.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) > REACH_SQR) {
            moveTo(target);
            return;
        }
        harvestAt(level, target, state);
        target = null;
        if (isCarryingFull()) {
            phase = Phase.DELIVER;
        }
    }

    @Nullable
    private BlockPos nearestTarget() {
        BlockPos best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (BlockPos candidate : targets) {
            double distance = body.distanceToSqr(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        if (best != null) {
            targets.remove(best);
        }
        return best;
    }

    /**
     * Harvests a ripe crop: its drops go into the farmer's bag, one seed goes back into the ground.
     */
    static void harvestAt(ServerLevel level, BlockPos pos, BlockState state, @Nullable CitizenEntity farmer,
                          Map<Item, Integer> harvested) {
        List<ItemStack> drops = FarmCrops.drops(level, pos, state, farmer);
        Item product = FarmCrops.product(drops, state, level, pos);
        boolean seed = FarmCrops.takeSeed(drops, state, level, pos)
                || (farmer != null && FarmCrops.takeSeed(farmer.carried(), state, level, pos));
        level.destroyBlock(pos, false, farmer);
        BlockState planted = FarmCrops.planted(state);
        if (seed && planted.canSurvive(level, pos)) {
            level.setBlockAndUpdate(pos, planted);
        } else if (seed) {
            drops.add(new ItemStack(planted.getBlock().asItem()));
        }
        for (ItemStack drop : drops) {
            ItemStack rest = farmer == null ? drop : farmer.carried().addItem(drop);
            if (!rest.isEmpty()) {
                Block.popResource(level, pos, rest);
            }
        }
        if (product != null) {
            harvested.merge(product, 1, Integer::sum);
        }
    }

    private void harvestAt(ServerLevel level, BlockPos pos, BlockState state) {
        harvestAt(level, pos, state, body, harvested);
        citizen().ifPresent(citizen -> citizen.addExperience(1));
    }

    // ---------------------------------------------------------------- delivering

    private void deliver() {
        if (body.carried().isEmpty()) {
            finish(20);
            return;
        }
        ServerLevel level = (ServerLevel) body.level();
        if (storehouse == null) {
            storehouse = nearestStorehouse(level);
            ticksOnTarget = 0;
            if (storehouse == null) {
                finish(400);
                return;
            }
        }
        if (!level.getBlockState(storehouse).is(ModRegistries.STOREHOUSE.get()) || ++ticksOnTarget > GIVE_UP_TICKS * 2) {
            storehouse = null;
            finish(100);
            return;
        }
        if (body.distanceToSqr(storehouse.getX() + 0.5, storehouse.getY(), storehouse.getZ() + 0.5) > STOREHOUSE_REACH_SQR) {
            moveTo(storehouse);
            return;
        }
        WorldPos at = new WorldPos(Dimensions.id(level), storehouse.getX(), storehouse.getY(), storehouse.getZ());
        Optional<StorehouseInventory> inventory = io.github.sekerator.kingdomworld.common.data.KingdomSavedData
                .get(level.getServer()).storehouses().get(at);
        if (inventory.isPresent() && !KingdomWorldApi.isReadOnly(level.getServer())) {
            for (int slot = 0; slot < body.carried().getContainerSize(); slot++) {
                ItemStack stack = body.carried().getItem(slot);
                if (!stack.isEmpty()) {
                    body.carried().setItem(slot, ItemHandlerHelper.insertItemStacked(inventory.get(), stack, false));
                }
            }
        }
        storehouse = null;
        finish(body.carried().isEmpty() ? 20 : 400);
    }

    @Nullable
    private BlockPos nearestStorehouse(ServerLevel level) {
        UUID kingdom = body.kingdomId();
        if (kingdom == null) {
            return null;
        }
        BlockPos here = body.blockPosition();
        WorldPos near = new WorldPos(Dimensions.id(level), here.getX(), here.getY(), here.getZ());
        for (StorehouseInventory inventory : KingdomStorage.storehouses(level.getServer(), kingdom, near)) {
            if (inventory.pos().distanceSqr(near) > STOREHOUSE_RANGE * STOREHOUSE_RANGE) {
                break;
            }
            BlockPos pos = new BlockPos(inventory.pos().x(), inventory.pos().y(), inventory.pos().z());
            if (level.isLoaded(pos) && level.getBlockState(pos).is(ModRegistries.STOREHOUSE.get())) {
                return pos;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- helpers

    private void moveTo(BlockPos pos) {
        if (pathCooldown > 0 || (!body.getNavigation().isDone() && body.getNavigation().getTargetPos() != null
                && body.getNavigation().getTargetPos().closerThan(pos, 1.5))) {
            return;
        }
        if (PathBudget.tryAcquire()) {
            body.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.6);
            pathCooldown = 20;
        }
    }

    private boolean isCarryingFull() {
        for (int slot = 0; slot < body.carried().getContainerSize(); slot++) {
            ItemStack stack = body.carried().getItem(slot);
            if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }

    private Optional<Citizen> citizen() {
        UUID id = body.citizenId();
        return id == null || body.getServer() == null ? Optional.empty()
                : KingdomWorldApi.state(body.getServer()).citizens().get(id);
    }

    /**
     * Remembers the size of the farm and its main product for the abstract simulation.
     */
    private void record(int plotCount, @Nullable Item product) {
        Item best = product;
        int bestCount = 0;
        for (Map.Entry<Item, Integer> entry : harvested.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        Item chosen = best;
        citizen().ifPresent(citizen -> {
            String id = citizen.workProduct();
            if (chosen != null) {
                ResourceLocation key = ForgeRegistries.ITEMS.getKey(chosen);
                id = key == null ? id : key.toString();
            }
            citizen.rememberWork(plotCount, id);
        });
    }
}
