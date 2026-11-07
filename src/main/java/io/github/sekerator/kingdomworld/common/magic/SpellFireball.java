package io.github.sekerator.kingdomworld.common.magic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The fireball of players and mages. Unlike the fireballs of the game it never sets blocks on fire or lights explosives,
 * and it hurts only whom the rules let its caster hurt: in peacetime no players and no citizens (see {@link MagicPvp});
 * the fireball of a mage hurts monsters only.
 */
public class SpellFireball extends Fireball {
    private static final String DAMAGE = "Damage";
    private float damage = 6.0f;

    public SpellFireball(EntityType<? extends SpellFireball> type, Level level) {
        super(type, level);
    }

    public SpellFireball(Level level, LivingEntity owner, double dx, double dy, double dz, float damage) {
        super(MagicRegistries.SPELL_FIREBALL.get(), owner, dx, dy, dz, level);
        this.damage = damage;
    }

    /**
     * Hurts a creature the fireball reached, if the rules allow it.
     *
     * @return whether the creature was hurt
     */
    public boolean strike(Entity target) {
        Entity owner = getOwner();
        if (!(target instanceof LivingEntity living) || !MagicPvp.mayHurt(owner, living)) {
            return false;
        }
        DamageSource source = damageSources().fireball(this, owner);
        boolean hurt = living.hurt(source, damage);
        if (hurt) {
            living.setSecondsOnFire(3);
            if (owner instanceof LivingEntity caster) {
                doEnchantDamageEffects(caster, living);
            }
        }
        return hurt;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!level().isClientSide) {
            strike(result.getEntity());
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        // No fire and no lit explosives.
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            discard();
        }
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat(DAMAGE, damage);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = Math.max(0.0f, Math.min(100.0f, tag.getFloat(DAMAGE)));
    }
}
