package io.github.sekerator.kingdomworld.common.citizen;

import io.github.sekerator.kingdomworld.common.citizen.goal.CitizenGoals;
import io.github.sekerator.kingdomworld.common.citizen.goal.WorkGoal;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The body of a citizen: a humanoid in the world that exists only while players are near. It is never saved with
 * the chunk; the authoritative state is the {@link Citizen} record, and {@link CitizenBodies} creates and removes
 * bodies and copies their state back. Deliberately not a villager, so that trading and other mods leave it alone.
 *
 * <p>The client only knows the look (skin, gender, kingdom color) and the name, which is a translated component
 * the client shows in its own alphabet.
 */
public class CitizenEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FEMALE = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);
    /** Slots for what a worker carries to the storehouse. */
    public static final int CARRY_SLOTS = 9;

    /** The record this body shows; {@code null} on the client and for bodies nobody admitted. */
    private UUID citizenId;
    private UUID kingdomId;
    /** Set by {@link CitizenBodies} right before the body is added to the world; other bodies are refused. */
    private boolean admitted;
    private String professionId = "";
    @Nullable
    private BlockPos home;
    @Nullable
    private BlockPos workplace;
    private CitizenState healthState = CitizenState.HEALTHY;
    private final SimpleContainer carried = new SimpleContainer(CARRY_SLOTS);
    private int regenerationTimer;

    public CitizenEntity(EntityType<? extends CitizenEntity> type, Level level) {
        super(type, level);
        if (getNavigation() instanceof GroundPathNavigation ground) {
            ground.setCanOpenDoors(true);
            ground.setCanPassDoors(true);
        }
        getNavigation().setCanFloat(true);
        setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, Citizen.MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ATTACK_DAMAGE, 1.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SKIN, 0);
        entityData.define(FEMALE, false);
        entityData.define(COLOR, 0xFFFFFF);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, CitizenGoals.fleeMonsters(this));
        goalSelector.addGoal(2, new CitizenGoals.SeekCareGoal(this));
        goalSelector.addGoal(3, new OpenDoorGoal(this, true));
        goalSelector.addGoal(4, new CitizenGoals.GoHomeAtNightGoal(this));
        goalSelector.addGoal(5, new WorkGoal(this));
        goalSelector.addGoal(7, new CitizenGoals.StrollNearHomeGoal(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0f));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    // ---------------------------------------------------------------- link to the record

    /**
     * Prepares a new body for a citizen; called before the body is added to the world.
     */
    void bind(Citizen citizen, int kingdomColor) {
        this.citizenId = citizen.id();
        this.kingdomId = citizen.kingdomId();
        this.admitted = true;
        entityData.set(SKIN, citizen.skin());
        entityData.set(FEMALE, citizen.gender() == Gender.FEMALE);
        entityData.set(COLOR, kingdomColor & 0xFFFFFF);
        setCustomName(CitizenTexts.name(citizen));
        setCustomNameVisible(false);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(Citizen.MAX_HEALTH);
        setHealth(Math.max(1.0f, citizen.health()));
        refresh(citizen, kingdomColor);
    }

    /**
     * Puts the citizen's tools and armor on the body; what the body carries falls where it dies.
     */
    void equip(GearRegistry gear) {
        for (net.minecraft.world.entity.EquipmentSlot slot : GearRegistry.SLOTS) {
            setItemSlot(slot, gear.get(citizenId, slot).copy());
            setDropChance(slot, 2.0f);
        }
    }

    /**
     * Writes what the body holds and wears back into the citizen's gear.
     */
    void storeGear(GearRegistry gear) {
        if (citizenId == null) {
            return;
        }
        for (net.minecraft.world.entity.EquipmentSlot slot : GearRegistry.SLOTS) {
            ItemStack stack = getItemBySlot(slot);
            if (!ItemStack.matches(stack, gear.get(citizenId, slot))) {
                gear.set(citizenId, slot, stack.copy());
            }
        }
    }

    /**
     * Copies what the body acts on from the record: profession, home, workplace.
     */
    void refresh(Citizen citizen, int kingdomColor) {
        this.professionId = citizen.professionId();
        this.home = toBlockPos(citizen.home());
        this.workplace = toBlockPos(citizen.workplace());
        this.healthState = citizen.state();
        entityData.set(COLOR, kingdomColor & 0xFFFFFF);
        if (home != null) {
            restrictTo(home, 32);
        } else {
            clearRestriction();
        }
    }

    @Nullable
    private BlockPos toBlockPos(@Nullable WorldPos pos) {
        if (pos == null || !pos.dimension().equals(io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(level()))) {
            return null;
        }
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }

    @Nullable
    public UUID citizenId() {
        return citizenId;
    }

    @Nullable
    public UUID kingdomId() {
        return kingdomId;
    }

    boolean isAdmitted() {
        return admitted;
    }

    public String professionId() {
        return professionId;
    }

    @Nullable
    public BlockPos home() {
        return home;
    }

    @Nullable
    public BlockPos workplace() {
        return workplace;
    }

    public boolean isWounded() {
        return healthState == CitizenState.WOUNDED || getHealth() < getMaxHealth() * CitizenSettings.rules().woundedFraction();
    }

    public SimpleContainer carried() {
        return carried;
    }

    public int skin() {
        return entityData.get(SKIN);
    }

    public boolean isFemale() {
        return entityData.get(FEMALE);
    }

    public int kingdomColor() {
        return entityData.get(COLOR);
    }

    // ---------------------------------------------------------------- life

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && isAlive() && getHealth() < getMaxHealth()) {
            // Slow natural healing; much faster next to an infirmary with room.
            int interval = CitizenCare.isInInfirmary(this) ? 40 : 200;
            if (++regenerationTimer >= interval) {
                regenerationTimer = 0;
                heal(1.0f);
            }
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (dead && !level().isClientSide && citizenId != null) {
            CitizenBodies.onBodyDied(this, source);
        }
    }

    @Override
    public void onRemovedFromWorld() {
        if (!level().isClientSide && citizenId != null) {
            CitizenBodies.onBodyRemoved(this);
        }
        super.onRemovedFromWorld();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide && player instanceof ServerPlayer serverPlayer && citizenId != null) {
            CitizenTexts.describeTo(serverPlayer, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
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
        // Bodies are created and removed by CitizenBodies only.
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
        // Bodies are not saved with chunks; if another mod stores one anyway, the citizen id lets the server
        // recognise and refuse the copy when it comes back.
        if (citizenId != null) {
            tag.putUUID("KingdomWorldCitizen", citizenId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("KingdomWorldCitizen")) {
            citizenId = tag.getUUID("KingdomWorldCitizen");
        }
    }

    @Nullable
    public ServerLevel serverLevel() {
        return level() instanceof ServerLevel server ? server : null;
    }
}
