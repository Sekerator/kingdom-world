package io.github.sekerator.kingdomworld.common.companion;

import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.core.companion.Companion;
import io.github.sekerator.kingdomworld.core.companion.CompanionPower;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The body of a companion: a person who walks with its owner, fights monsters and carries a pack. Like the body of a
 * citizen it is never saved with the chunk; the {@link Companion} record and the pack kept next to it are the truth, and
 * {@link CompanionBodies} creates and removes bodies and copies their health and place back. What the body holds and wears
 * are copies of the best gear in the pack, which never fall when it dies: the pack itself falls.
 */
public class CompanionEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FEMALE = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SLAVE = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);

    /** The record this body shows; {@code null} on the client and for bodies nobody admitted. */
    private UUID companionId;
    private UUID owner;
    /** Set by {@link CompanionBodies} right before the body is added to the world; other bodies are refused. */
    private boolean admitted;
    private int regenerationTimer;
    private long healInterval;
    /** Where in the pack the gear the body uses lies, by slot of the body; -1 for none. */
    final int[] gearIndex = new int[EquipmentSlot.values().length];

    public CompanionEntity(EntityType<? extends CompanionEntity> type, Level level) {
        super(type, level);
        if (getNavigation() instanceof GroundPathNavigation ground) {
            ground.setCanOpenDoors(true);
            ground.setCanPassDoors(true);
        }
        getNavigation().setCanFloat(true);
        setCanPickUpLoot(false);
        java.util.Arrays.fill(gearIndex, -1);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0f);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, Companion.BASE_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.2);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SKIN, 0);
        entityData.define(FEMALE, false);
        entityData.define(SLAVE, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new OpenDoorGoal(this, true));
        goalSelector.addGoal(3, new CompanionGoal(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0f));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    // ---------------------------------------------------------------- link to the record

    /**
     * Prepares a new body for a companion; called before the body is added to the world.
     */
    void bind(Companion companion) {
        this.companionId = companion.id();
        this.owner = companion.owner();
        this.admitted = true;
        entityData.set(SKIN, companion.skin());
        entityData.set(FEMALE, companion.gender() == Gender.FEMALE);
        setCustomName(CitizenTexts.name(companion.nameSeed(), companion.gender()));
        setCustomNameVisible(false);
        refresh(companion);
        setHealth(Math.max(1.0f, Math.min(companion.health(), companion.maxHealth())));
    }

    /**
     * Copies what the body acts on from the record: health, strength, whether it is a slave.
     */
    void refresh(Companion companion) {
        entityData.set(SLAVE, companion.isSlave());
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(companion.maxHealth());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(CompanionPower.attackDamage(companion.skills()));
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.5 + companion.skills().stamina() * 0.005);
        if (getHealth() > getMaxHealth()) {
            setHealth(getMaxHealth());
        }
    }

    @Nullable
    public UUID companionId() {
        return companionId;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    boolean isAdmitted() {
        return admitted;
    }

    public int skin() {
        return entityData.get(SKIN);
    }

    public boolean isFemale() {
        return entityData.get(FEMALE);
    }

    public boolean isSlave() {
        return entityData.get(SLAVE);
    }

    // ---------------------------------------------------------------- life

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && isAlive() && getHealth() < getMaxHealth() && getServer() != null && companionId != null) {
            if (healInterval <= 0 || tickCount % 40 == 0) {
                // Slow natural healing; much faster at an infirmary of the owner's kingdom.
                healInterval = CompanionBodies.healInterval(this);
            }
            if (++regenerationTimer >= healInterval) {
                regenerationTimer = 0;
                heal(1.0f);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        float before = getHealth();
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && companionId != null) {
            CompanionBodies.onHurt(this, source, before);
        }
        return hurt;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && !level().isClientSide && companionId != null) {
            CompanionGear.wearWeapon(this);
        }
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (dead && !level().isClientSide && companionId != null) {
            CompanionBodies.onBodyDied(this, source);
        }
    }

    @Override
    public void onRemovedFromWorld() {
        if (!level().isClientSide && companionId != null) {
            CompanionBodies.onBodyRemoved(this);
        }
        super.onRemovedFromWorld();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide && player instanceof ServerPlayer serverPlayer && companionId != null) {
            CompanionActions.interact(serverPlayer, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        // The gear on the body is a copy: the pack falls instead, from CompanionBodies.
    }

    @Override
    protected void dropEquipment() {
        // See dropCustomDeathLoot.
    }

    // ---------------------------------------------------------------- never saved, never moved away

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public void checkDespawn() {
        // Bodies are created and removed by CompanionBodies only.
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (companionId != null) {
            tag.putUUID("KingdomWorldCompanion", companionId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("KingdomWorldCompanion")) {
            companionId = tag.getUUID("KingdomWorldCompanion");
        }
    }

    @Nullable
    public ServerLevel serverLevel() {
        return level() instanceof ServerLevel server ? server : null;
    }
}
