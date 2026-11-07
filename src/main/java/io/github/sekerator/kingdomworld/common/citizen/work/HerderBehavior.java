package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.PostYield;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.citizen.WorkZone;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.IForgeShearable;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The herder: keeps a herd of one kind of animal (any animal, also those of other mods) in the zone of its post.
 * It breeds the herd with the food the animals eat up to the size set at the post, culls grown animals beyond
 * it (never young ones, never animals with a name) and shears those that can be shorn when it has shears.
 */
public final class HerderBehavior implements ProfessionBehavior {
    /** Days a herd needs to grow back by half its size. */
    static final double HERD_DAYS = 2.0;
    /** Feed one animal born takes. */
    static final double FEED_PER_BIRTH = 2.0;
    /** Animals of a kind shown as choices in the post's settings at most. */
    public static final int MAX_CHOICES = 16;

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
        return Equipment.ToolNeed.WEAPON;
    }

    @Override
    public Equipment.ToolNeed secondTool() {
        return Equipment.ToolNeed.SHEARS;
    }

    @Override
    public boolean risky() {
        return true;
    }

    @Override
    public CauseOfDeath accidentCause() {
        return CauseOfDeath.HERD;
    }

    // ---------------------------------------------------------------- animals of a zone

    public static AABB box(WorkZone zone) {
        return new AABB(zone.minX(), zone.minY(), zone.minZ(), zone.maxX() + 1, zone.maxY() + 1, zone.maxZ() + 1);
    }

    public static String typeId(Entity entity) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id == null ? "" : id.toString();
    }

    /**
     * Animals of the kind in the zone, on the kingdom's land.
     */
    static List<Animal> herd(ServerLevel level, WorkZone zone, LandCheck land, String type) {
        return level.getEntitiesOfClass(Animal.class, box(zone),
                animal -> animal.isAlive() && typeId(animal).equals(type) && land.owns(animal.blockPosition()));
    }

    /**
     * Kinds of animals living in the zone of a post, most numerous first, for the choice at the post.
     */
    public static List<String> kindsIn(ServerLevel level, WorkPost post) {
        WorkZone zone = WorkZone.of(post.pos(), post.professionId(), post.radius(), 1);
        LandCheck land = new LandCheck(level, post.kingdomId());
        Map<String, Integer> counts = new HashMap<>();
        for (Animal animal : level.getEntitiesOfClass(Animal.class, box(zone), a -> a.isAlive() && land.owns(a.blockPosition()))) {
            counts.merge(typeId(animal), 1, Integer::sum);
        }
        List<String> kinds = new ArrayList<>(counts.keySet());
        kinds.sort(Comparator.comparing((String id) -> -counts.get(id)).thenComparing(id -> id));
        return kinds.size() > MAX_CHOICES ? kinds.subList(0, MAX_CHOICES) : kinds;
    }

    /**
     * Whether an animal may be culled: grown, without a name, not on a lead.
     */
    static boolean mayCull(Animal animal) {
        return !animal.isBaby() && !animal.hasCustomName() && !animal.isLeashed() && !animal.isVehicle();
    }

    /**
     * Whether an animal can be shorn now with the shears.
     */
    static boolean canShear(Animal animal, ItemStack shears) {
        return !shears.isEmpty() && animal instanceof IForgeShearable shearable
                && shearable.isShearable(shears, animal.level(), animal.blockPosition());
    }

    @Override
    public ZoneSurvey surveyZone(ServerLevel level, WorkPost post) {
        return new Survey(level, post);
    }

    /**
     * Counts the herd at the end and samples what a culled animal gives.
     */
    private static final class Survey extends ZoneSurvey {
        private int animals;
        private int shearable;
        private PostYield yield = PostYield.NONE;

        Survey(ServerLevel level, WorkPost post) {
            // Only the animals matter: a survey of a single block finishes at once.
            super(level, post, WorkZone.around(post.pos(), 0, 0, 0));
        }

        @Override
        protected void visit(BlockPos pos, BlockState state) {
        }

        @Override
        protected void finish() {
            if (post.animal().isEmpty()) {
                return;
            }
            WorkZone zone = WorkZone.of(post.pos(), post.professionId(), post.radius(), 1);
            List<Animal> herd = herd(level, zone, new LandCheck(level, post.kingdomId()), post.animal());
            animals = herd.size();
            if (herd.isEmpty()) {
                return;
            }
            Animal sample = herd.get(0);
            for (Animal animal : herd) {
                if (animal instanceof IForgeShearable) {
                    shearable++;
                }
            }
            double culls = Math.max(1.0, Math.min(animals, post.herdSize()) / 2.0) / HERD_DAYS;
            Map<String, Double> outputs = new LinkedHashMap<>();
            int rolls = 6;
            for (int i = 0; i < rolls; i++) {
                for (ItemStack drop : loot(level, sample)) {
                    outputs.merge(PostWork.itemId(drop), culls * drop.getCount() / rolls, Double::sum);
                }
            }
            if (shearable > 0) {
                // Wool and the like grow back within about a day.
                for (ItemStack drop : shearDrops(sample)) {
                    outputs.merge(PostWork.itemId(drop), (double) Math.min(shearable, post.herdSize()) * drop.getCount(), Double::sum);
                }
            }
            Map<String, Double> inputs = new LinkedHashMap<>();
            String feed = feedOf(level, post, sample);
            if (feed != null) {
                inputs.put(feed, culls * FEED_PER_BIRTH);
            }
            yield = new PostYield(outputs, inputs, culls * 3, false);
        }

        @Override
        public PostYield result() {
            return yield;
        }

        @Override
        public WorkStatus status() {
            return post.animal().isEmpty() || animals == 0 ? WorkStatus.NO_ANIMALS : WorkStatus.WORKING;
        }
    }

    /**
     * What an animal of the kind drops when culled, by its loot table, without killing it.
     */
    static List<ItemStack> loot(ServerLevel level, Animal animal) {
        LootTable table = level.getServer().getLootData().getLootTable(animal.getLootTable());
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, animal)
                .withParameter(LootContextParams.ORIGIN, animal.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
                .create(LootContextParamSets.ENTITY);
        return table.getRandomItems(params);
    }

    /**
     * What shearing gives, estimated without touching the animal: wool for sheep; other animals are measured
     * once their herder was watched at work.
     */
    private static List<ItemStack> shearDrops(Animal animal) {
        if (animal instanceof net.minecraft.world.entity.animal.Sheep) {
            return List.of(new ItemStack(net.minecraft.world.level.block.Blocks.WHITE_WOOL, 2));
        }
        return List.of();
    }

    /**
     * The id of an item in the kingdom's storehouses the animal eats, or {@code null} if there is none.
     */
    @Nullable
    private static String feedOf(ServerLevel level, WorkPost post, Animal animal) {
        List<KingdomStorage.Found> found = KingdomStorage.find(level.getServer(), post.kingdomId(), animal::isFood, post.pos());
        return found.isEmpty() ? null : PostWork.itemId(found.get(0).stack());
    }

    // ---------------------------------------------------------------- the herder at work

    /**
     * The herder at work: counts its herd, breeds it up to its size, culls grown animals beyond it and shears.
     */
    static final class WorkGoal extends PostWorkGoal {
        private static final int GIVE_UP_TICKS = 300;
        private static final int ROUND_TICKS = 100;
        @Nullable
        private Animal target;
        private Action action;
        @Nullable
        private Animal partner;
        private int walking;
        private int attackCooldown;
        private int idle;
        @Nullable
        private BlockPos lastKill;
        private int collectTicks;

        private enum Action {
            BREED,
            CULL,
            SHEAR
        }

        WorkGoal(CitizenEntity body, ProfessionBehavior behavior) {
            super(body, behavior);
        }

        @Override
        protected void onStart() {
            target = null;
            idle = 0;
        }

        @Override
        protected WorkStatus work(ServerLevel level) {
            if (attackCooldown > 0) {
                attackCooldown--;
            }
            if (lastKill != null && collectTicks-- > 0) {
                collectDrops(level, lastKill);
                if (collectTicks <= 0) {
                    lastKill = null;
                }
            }
            if (target == null || !target.isAlive()) {
                if (target != null && action == Action.CULL) {
                    lastKill = target.blockPosition();
                    collectTicks = 20;
                }
                target = null;
                if (lastKill != null) {
                    return null;
                }
                if (post.animal().isEmpty()) {
                    chooseAnimal(level);
                    if (post.animal().isEmpty()) {
                        return WorkStatus.NO_ANIMALS;
                    }
                }
                WorkStatus plan = plan(level);
                if (plan != null) {
                    return plan;
                }
                walking = 0;
                report(WorkStatus.WORKING);
            }
            lookAt(target.blockPosition());
            if (body.distanceToSqr(target) > 2.6 * 2.6) {
                if (body.getNavigation().isDone() || walking % 20 == 0) {
                    body.getNavigation().moveTo(target, 0.7);
                }
                if (++walking > GIVE_UP_TICKS) {
                    target = null;
                }
                return null;
            }
            switch (action) {
                case BREED -> breed(level);
                case SHEAR -> shear(level);
                case CULL -> cull(level);
            }
            return isCarryingFull() ? WorkStatus.WORKING : null;
        }

        /**
         * Picks the kind of animal most numerous in the zone when the post has none set.
         */
        private void chooseAnimal(ServerLevel level) {
            List<String> kinds = kindsIn(level, post);
            if (!kinds.isEmpty()) {
                String kind = kinds.get(0);
                KingdomWorldApi.change(level.getServer(), "choose the animals of a herd",
                        state -> state.setPostAnimal(null, post.pos(), kind));
            }
        }

        /**
         * Decides what to do next with the herd.
         *
         * @return {@code null} if there is something to do, otherwise how the round ends
         */
        @Nullable
        private WorkStatus plan(ServerLevel level) {
            List<Animal> herd = herd(level, zone, land, post.animal());
            if (herd.isEmpty()) {
                return hasCarried() ? WorkStatus.WORKING : WorkStatus.NO_ANIMALS;
            }
            herd.sort(Comparator.comparingDouble(body::distanceToSqr));
            ItemStack shears = body.getItemBySlot(EquipmentSlot.OFFHAND);
            for (Animal animal : herd) {
                if (canShear(animal, shears)) {
                    target = animal;
                    action = Action.SHEAR;
                    return null;
                }
            }
            int size = post.herdSize();
            if (herd.size() > size) {
                for (Animal animal : herd) {
                    if (mayCull(animal)) {
                        target = animal;
                        action = Action.CULL;
                        return null;
                    }
                }
            }
            if (herd.size() < size) {
                List<Animal> ready = new ArrayList<>();
                for (Animal animal : herd) {
                    if (animal.getAge() == 0 && animal.canFallInLove() && !animal.isInLove()) {
                        ready.add(animal);
                    }
                }
                if (ready.size() >= 2) {
                    if (!hasFeed(level, ready.get(0), 2)) {
                        return WorkStatus.NO_FEED;
                    }
                    target = ready.get(0);
                    partner = ready.get(1);
                    action = Action.BREED;
                    return null;
                }
            }
            if (++idle > 2 || hasCarried()) {
                return hasCarried() ? WorkStatus.WORKING : WorkStatus.WAITING;
            }
            return WorkStatus.WAITING;
        }

        private boolean hasFeed(ServerLevel level, Animal animal, int count) {
            int carried = 0;
            for (int slot = 0; slot < body.carried().getContainerSize(); slot++) {
                ItemStack stack = body.carried().getItem(slot);
                if (!stack.isEmpty() && animal.isFood(stack)) {
                    carried += stack.getCount();
                }
            }
            if (carried >= count) {
                return true;
            }
            for (ItemStack taken : KingdomStorage.take(level.getServer(), post.kingdomId(), animal::isFood, count - carried, post.pos())) {
                carry(taken);
                carried += taken.getCount();
            }
            return carried >= count;
        }

        private ItemStack takeFeed(Animal animal) {
            for (int slot = 0; slot < body.carried().getContainerSize(); slot++) {
                ItemStack stack = body.carried().getItem(slot);
                if (!stack.isEmpty() && animal.isFood(stack)) {
                    return body.carried().removeItem(slot, 1);
                }
            }
            return ItemStack.EMPTY;
        }

        private void breed(ServerLevel level) {
            if (!target.canFallInLove() || target.isInLove()) {
                next();
                return;
            }
            ItemStack feed = takeFeed(target);
            if (feed.isEmpty()) {
                target = null;
                return;
            }
            target.setInLove(null);
            PostWork.used(post, feed, 1);
            body.swing(InteractionHand.MAIN_HAND);
            next();
        }

        /**
         * After the first animal of a pair the herder goes to the second.
         */
        private void next() {
            if (partner != null && partner.isAlive() && partner != target) {
                target = partner;
                partner = null;
                walking = 0;
            } else {
                target = null;
                partner = null;
            }
        }

        private void shear(ServerLevel level) {
            ItemStack shears = body.getItemBySlot(EquipmentSlot.OFFHAND);
            if (!canShear(target, shears)) {
                target = null;
                return;
            }
            IForgeShearable shearable = (IForgeShearable) target;
            int fortune = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, shears);
            for (ItemStack drop : shearable.onSheared(null, shears, level, target.blockPosition(), fortune)) {
                gather(drop);
            }
            Equipment.wear(Equipment.of(body), EquipmentSlot.OFFHAND, 1, body.getRandom());
            PostWork.acted(post, 1);
            body.swing(InteractionHand.OFF_HAND);
            target = null;
        }

        private void cull(ServerLevel level) {
            if (!mayCull(target)) {
                target = null;
                return;
            }
            if (attackCooldown > 0) {
                return;
            }
            attackCooldown = 15;
            body.swing(InteractionHand.MAIN_HAND);
            if (body.doHurtTarget(target)) {
                if (!body.getMainHandItem().isEmpty()) {
                    useTool(1);
                } else {
                    PostWork.acted(post, 1);
                }
            } else {
                // Some animals shrug off a blow; finish them so that culling does not go on forever.
                DamageSource source = body.damageSources().mobAttack(body);
                target.hurt(source, 4.0f);
            }
        }

        /**
         * Picks up what a culled animal dropped.
         */
        private void collectDrops(ServerLevel level, BlockPos where) {
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(where).inflate(3.0),
                    entity -> entity.isAlive() && entity.getAge() < 200)) {
                ItemStack stack = item.getItem().copy();
                item.discard();
                gather(stack);
            }
        }
    }

    /**
     * The entity type with the id, or {@code null}.
     */
    @Nullable
    public static EntityType<?> type(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null || !ForgeRegistries.ENTITY_TYPES.containsKey(key) ? null : ForgeRegistries.ENTITY_TYPES.getValue(key);
    }
}
