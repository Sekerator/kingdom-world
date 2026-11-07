package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.goal.ScanBudget;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.PostYield;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.citizen.WorkZone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The fisher: stands on the bank of water in the zone of its post and fishes with a rod. The catch comes from the
 * game's fishing loot table, so mods that add fish to it add them to the fisher's catch too; the rod wears with
 * every catch.
 */
public final class FisherBehavior implements ProfessionBehavior {
    /** Catches of one fisher in a working day, on average. */
    static final double CATCHES_PER_DAY = 24.0;
    /** Fewest ticks one catch takes. */
    private static final int MIN_WAIT = 100;

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
        PostWork.workAbstractly(server, citizen, this, intervalTicks);
    }

    @Override
    public void survey(ServerLevel level, Citizen citizen) {
    }

    @Override
    public Equipment.ToolNeed tool() {
        return Equipment.ToolNeed.FISHING_ROD;
    }

    @Override
    public boolean risky() {
        return true;
    }

    @Override
    public CauseOfDeath accidentCause() {
        return CauseOfDeath.FISHING;
    }

    /**
     * Whether a block is still water open to the sky above it, where a hook can be cast.
     */
    static boolean isFishingWater(ServerLevel level, BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        return fluid.is(FluidTags.WATER) && fluid.isSource() && level.getBlockState(pos.above()).isAir();
    }

    /**
     * A place on the bank next to the water where a body can stand, or {@code null}.
     */
    @Nullable
    static BlockPos bank(ServerLevel level, BlockPos water, LandCheck land) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos ground = water.relative(direction);
            BlockPos stand = ground.above();
            if (!land.owns(stand) || !level.isLoaded(stand)) {
                continue;
            }
            BlockState below = level.getBlockState(ground);
            if (!below.getCollisionShape(level, ground).isEmpty() && below.getFluidState().isEmpty()
                    && level.getBlockState(stand).getCollisionShape(level, stand).isEmpty()
                    && level.getFluidState(stand).isEmpty()
                    && level.getBlockState(stand.above()).getCollisionShape(level, stand.above()).isEmpty()) {
                return stand;
            }
        }
        return null;
    }

    /**
     * One catch from the fishing loot table.
     */
    static List<ItemStack> fish(ServerLevel level, BlockPos water, ItemStack rod, @Nullable Entity fisher) {
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(water))
                .withParameter(LootContextParams.TOOL, rod)
                .withOptionalParameter(LootContextParams.THIS_ENTITY, fisher)
                .withLuck(EnchantmentHelper.getFishingLuckBonus(rod))
                .create(LootContextParamSets.FISHING);
        LootTable table = level.getServer().getLootData().getLootTable(BuiltInLootTables.FISHING);
        return table.getRandomItems(params);
    }

    @Override
    public ZoneSurvey surveyZone(ServerLevel level, WorkPost post) {
        return new Survey(level, post);
    }

    /**
     * Looks for water with a bank, and samples the fishing loot table.
     */
    private static final class Survey extends ZoneSurvey {
        private final LandCheck land;
        private BlockPos water;

        Survey(ServerLevel level, WorkPost post) {
            super(level, post, WorkZone.of(post.pos(), post.professionId(), post.radius(), 1));
            this.land = new LandCheck(level, post.kingdomId());
        }

        @Override
        protected void visit(BlockPos pos, BlockState state) {
            if (water == null && isFishingWater(level, pos) && bank(level, pos, land) != null) {
                water = pos.immutable();
            }
        }

        @Override
        public PostYield result() {
            if (water == null) {
                return PostYield.NONE;
            }
            Map<String, Integer> catches = new HashMap<>();
            int rolls = 40;
            for (int i = 0; i < rolls; i++) {
                for (ItemStack stack : fish(level, water, new ItemStack(Items.FISHING_ROD), null)) {
                    catches.merge(PostWork.itemId(stack), stack.getCount(), Integer::sum);
                }
            }
            Map<String, Double> rates = new HashMap<>();
            catches.forEach((item, count) -> rates.put(item, CATCHES_PER_DAY * count / rolls));
            return new PostYield(rates, Map.of(), CATCHES_PER_DAY, false);
        }

        @Override
        public WorkStatus status() {
            return water != null ? WorkStatus.WORKING : WorkStatus.NO_WATER;
        }
    }

    /**
     * The fisher at work: finds water with a bank, walks there and fishes until its bag is full.
     */
    static final class WorkGoal extends PostWorkGoal {
        private static final int GIVE_UP_TICKS = 300;
        /** Catches before the fisher takes them to the container. */
        private static final int CATCHES_PER_ROUND = 8;
        @Nullable
        private BlockPos water;
        @Nullable
        private BlockPos stand;
        private long scanIndex;
        private int timer;
        private int walking;
        private int catches;

        WorkGoal(CitizenEntity body, ProfessionBehavior behavior) {
            super(body, behavior);
        }

        @Override
        protected void onStart() {
            if (water != null && (zone == null || !zone.contains(water.getX(), water.getY(), water.getZ()))) {
                water = null;
            }
            scanIndex = 0;
            walking = 0;
            catches = 0;
            timer = 0;
        }

        @Override
        protected WorkStatus work(ServerLevel level) {
            if (water == null || stand == null || !isFishingWater(level, water)) {
                water = null;
                if (!scan(level)) {
                    return null;
                }
                if (water == null) {
                    return hasCarried() ? WorkStatus.WORKING : WorkStatus.NO_WATER;
                }
                report(WorkStatus.WORKING);
            }
            if (!near(stand, 1.2 * 1.2)) {
                moveTo(stand);
                if (++walking > GIVE_UP_TICKS) {
                    water = null;
                    return hasCarried() ? WorkStatus.WORKING : null;
                }
                return null;
            }
            lookAt(water);
            if (timer <= 0) {
                int lure = EnchantmentHelper.getFishingSpeedBonus(tool());
                timer = Math.max(20, MIN_WAIT + body.getRandom().nextInt(300) - lure * 100);
                return null;
            }
            if (--timer % 40 == 0) {
                level.sendParticles(ParticleTypes.FISHING, water.getX() + 0.5, water.getY() + 0.9, water.getZ() + 0.5, 2, 0.1, 0.0, 0.1, 0.0);
            }
            if (timer > 0) {
                return null;
            }
            for (ItemStack catchItem : fish(level, water, tool(), body)) {
                gather(catchItem);
            }
            level.sendParticles(ParticleTypes.SPLASH, water.getX() + 0.5, water.getY() + 1.0, water.getZ() + 0.5, 6, 0.2, 0.0, 0.2, 0.0);
            useTool(1);
            catches++;
            return catches >= CATCHES_PER_ROUND || isCarryingFull() ? WorkStatus.WORKING : null;
        }

        /**
         * Looks for water with a bank a little at a time.
         *
         * @return whether the look is over (water found or the zone ran out)
         */
        private boolean scan(ServerLevel level) {
            int budget = ScanBudget.take(ScanBudget.PER_WORKER);
            long total = zone.volume();
            int width = zone.width();
            int height = zone.height();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            while (budget-- > 0 && scanIndex < total) {
                long column = scanIndex / height;
                pos.set(zone.minX() + (int) (column % width), zone.maxY() - (int) (scanIndex % height), zone.minZ() + (int) (column / width));
                scanIndex++;
                if (!land.owns(pos) || !level.isLoaded(pos) || !isFishingWater(level, pos)) {
                    continue;
                }
                BlockPos bank = bank(level, pos, land);
                if (bank != null) {
                    water = pos.immutable();
                    stand = bank;
                    walking = 0;
                    return true;
                }
            }
            return scanIndex >= total;
        }
    }
}
