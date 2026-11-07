package io.github.sekerator.kingdomworld.common.road;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;

/**
 * A robber: a person with a weapon and armor of any mod, who lives in a camp in the wild, falls upon caravans and attacks
 * players who come near, except those who are away from the game. It fights with the goals of vanilla mobs, shoots if it
 * holds a bow, never breaks blocks and never drops its gear. The leader of a camp is stronger.
 */
public class BanditEntity extends Monster implements RangedAttackMob {
    private final RangedBowAttackGoal<BanditEntity> bowGoal = new RangedBowAttackGoal<>(this, 1.0, 25, 15.0f);
    private final MeleeAttackGoal meleeGoal = new MeleeAttackGoal(this, 1.15, false);
    private long campId;

    public BanditEntity(EntityType<? extends BanditEntity> type, Level level) {
        super(type, level);
        setCanPickUpLoot(false);
        xpReward = isLeader() ? 15 : 6;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0f);
        }
        reassessWeaponGoal();
    }

    public static AttributeSupplier.Builder createAttributes(boolean leader) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, leader ? 50.0 : 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, leader ? 5.0 : 3.0)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ARMOR, leader ? 4.0 : 0.0);
    }

    public boolean isLeader() {
        return getType() == RoadRegistries.BANDIT_LEADER.get();
    }

    /**
     * The camp the robber belongs to.
     */
    public long campId() {
        return campId;
    }

    public void setCampId(long value) {
        campId = value;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 1.0));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, BanditEntity.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, BanditWork::mayAttack));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, CaravanMerchantEntity.class, true));
    }

    /**
     * Shoots with a bow, fights hand to hand with anything else.
     */
    public void reassessWeaponGoal() {
        if (bowGoal == null || meleeGoal == null || level() == null || level().isClientSide) {
            return;
        }
        goalSelector.removeGoal(bowGoal);
        goalSelector.removeGoal(meleeGoal);
        if (getMainHandItem().getItem() instanceof BowItem) {
            bowGoal.setMinAttackInterval(25);
            goalSelector.addGoal(4, bowGoal);
        } else {
            goalSelector.addGoal(4, meleeGoal);
        }
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        super.setItemSlot(slot, stack);
        if (!level().isClientSide && slot == EquipmentSlot.MAINHAND) {
            reassessWeaponGoal();
        }
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        ItemStack weapon = getMainHandItem();
        ItemStack ammo = getProjectile(weapon);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, ammo, power);
        if (weapon.getItem() instanceof BowItem bow) {
            arrow = bow.customArrow(arrow);
        }
        double dx = target.getX() - getX();
        double dy = target.getY(0.3333333333333333) - arrow.getY();
        double dz = target.getZ() - getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + flat * 0.2, dz, 1.6f, (float) (14 - level().getDifficulty().getId() * 4));
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        playSound(SoundEvents.SKELETON_SHOOT, 1.0f, 1.0f / (getRandom().nextFloat() * 0.4f + 0.8f));
        level().addFreshEntity(arrow);
    }

    @Override
    public boolean canFireProjectileWeapon(ProjectileWeaponItem weapon) {
        return weapon instanceof BowItem;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void checkDespawn() {
        // The camp decides when its robbers come and go.
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        // Robbers drop nothing: their gear came from nowhere.
    }
}
