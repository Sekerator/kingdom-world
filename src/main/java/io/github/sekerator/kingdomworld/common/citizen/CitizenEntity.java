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
    /** How far from its home a calm body goes, in blocks. */
    public static final int HOME_RANGE = 32;

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
    /** Game time until which the body is frightened and runs; 0 while it is calm. Never saved: bodies are not. */
    private long fearUntil;
    @Nullable
    private UUID threatId;
    @Nullable
    private net.minecraft.world.phys.Vec3 threatPos;
    /** Whether this fright still has to frighten the bodies around, which it does once. */
    private boolean spreadsFear;
    /** A guard the body runs to rather than home: a witness that tells the guards. */
    @Nullable
    private UUID guardId;

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
        goalSelector.addGoal(1, new io.github.sekerator.kingdomworld.common.citizen.goal.FearGoal(this));
        goalSelector.addGoal(1, CitizenGoals.fleeMonsters(this));
        goalSelector.addGoal(2, new CitizenGoals.SeekCareGoal(this));
        goalSelector.addGoal(3, new OpenDoorGoal(this, true));
        goalSelector.addGoal(4, new io.github.sekerator.kingdomworld.common.life.DayGoal(this));
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
        setCustomName(io.github.sekerator.kingdomworld.common.npc.SettlementHeads.title(level(), citizen, CitizenTexts.name(citizen)));
        setCustomNameVisible(false);
        // A soldier has more health than other citizens, by its branch and post.
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(citizen.maxHealth());
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
        restrictToHome();
    }

    /**
     * Keeps the body near its home, except while it is frightened: a body that runs for its life runs as far as it must.
     */
    private void restrictToHome() {
        if (home != null && !isFrightened()) {
            restrictTo(home, HOME_RANGE);
        } else {
            clearRestriction();
        }
    }

    // ---------------------------------------------------------------- fright

    /**
     * Frightens the body: it drops what it does and runs from the threat until {@code ticks} after the last cause. A body
     * asleep wakes up. A new cause while it runs only makes the fright last longer.
     *
     * @param threat  who frightened it
     * @param spreads whether the body frightens those around it once, as one that saw the blow does
     */
    public void frighten(net.minecraft.world.entity.Entity threat, int ticks, boolean spreads) {
        if (level().isClientSide || ticks <= 0 || !isAlive()) {
            return;
        }
        boolean calm = !isFrightened();
        fearUntil = Math.max(fearUntil, level().getGameTime() + ticks);
        threatId = threat.getUUID();
        threatPos = threat.position();
        if (calm) {
            spreadsFear = spreads;
            guardId = null;
            clearRestriction();
        }
        if (isSleeping()) {
            stopSleeping();
        }
    }

    public boolean isFrightened() {
        return fearUntil > 0 && level().getGameTime() < fearUntil;
    }

    /**
     * The fright is over: the body is kept near its home again.
     */
    public void calmDown() {
        fearUntil = 0;
        threatId = null;
        threatPos = null;
        spreadsFear = false;
        guardId = null;
        restrictToHome();
    }

    /**
     * Where the threat is now, as far as the body knows: the place of whoever frightened it while that one is near, else
     * where it was last.
     */
    @Nullable
    public net.minecraft.world.phys.Vec3 threatPosition() {
        if (threatId != null && level() instanceof ServerLevel server) {
            net.minecraft.world.entity.Entity threat = server.getEntity(threatId);
            if (threat != null && threat.isAlive() && threat.distanceToSqr(this) < 64.0 * 64.0) {
                threatPos = threat.position();
            }
        }
        return threatPos;
    }

    @Nullable
    public UUID threatId() {
        return threatId;
    }

    /**
     * Whether the body still has to frighten those around it; asking uses the one time up.
     */
    public boolean takeFearSpread() {
        boolean spreads = spreadsFear;
        spreadsFear = false;
        return spreads;
    }

    /**
     * The guard this body runs to, if it is a witness that tells the guards.
     */
    @Nullable
    public UUID fearGuard() {
        return guardId;
    }

    public void setFearGuard(@Nullable UUID guard) {
        guardId = guard;
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

    /**
     * What the body is busy with by the goals it follows now, for the window of the workers; {@code null} when none of them
     * tells (the body strolls or waits between two steps of its work).
     */
    @Nullable
    public io.github.sekerator.kingdomworld.core.citizen.Workforce.Doing doing() {
        if (isSleeping()) {
            return io.github.sekerator.kingdomworld.core.citizen.Workforce.Doing.SLEEPING;
        }
        for (net.minecraft.world.entity.ai.goal.WrappedGoal running : goalSelector.getRunningGoals().toList()) {
            net.minecraft.world.entity.ai.goal.Goal goal = running.getGoal();
            io.github.sekerator.kingdomworld.core.citizen.Workforce.Doing doing = null;
            if (goal instanceof io.github.sekerator.kingdomworld.common.life.DayGoal day) {
                doing = day.doing();
            } else if (goal instanceof WorkGoal work) {
                doing = work.doing();
            } else if (goal instanceof CitizenGoals.SeekCareGoal) {
                doing = io.github.sekerator.kingdomworld.core.citizen.Workforce.Doing.HEALING;
            }
            if (doing != null) {
                return doing;
            }
        }
        return null;
    }

    /**
     * Whether the citizen should go to an infirmary: wounded or ill.
     */
    public boolean needsCare() {
        return isWounded() || io.github.sekerator.kingdomworld.common.life.LifeWork.isSick(citizenId);
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

    /** The tick until which a fall does the body no harm: a builder on its scaffolds. */
    private int safeFromFallsUntil;

    /**
     * A fall does the body no harm for a while: it works on scaffolds, comes down them one block at a time and is set on the
     * ground when they are taken down under it.
     */
    public void keepSafeFromFalls(int ticks) {
        safeFromFallsUntil = Math.max(safeFromFallsUntil, tickCount + Math.max(0, ticks));
        fallDistance = 0.0f;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        if (tickCount <= safeFromFallsUntil) {
            return false;
        }
        return super.causeFallDamage(distance, multiplier, source);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && onGround() && level().getBlockState(blockPosition().below())
                .is(io.github.sekerator.kingdomworld.common.registry.ModRegistries.SCAFFOLDING.get())) {
            // Whoever stands on scaffolds (a body come back on top of a column, one that walks off its edge) comes down unharmed.
            keepSafeFromFalls(100);
        }
        if (!level().isClientSide && isSleeping() && tickCount % 20 == 0
                && io.github.sekerator.kingdomworld.common.life.DayGoal.shouldWake(this)) {
            stopSleeping();
        }
        if (!level().isClientSide && fearUntil > 0 && !isFrightened()) {
            // The fright ran out, whether the body ever ran or not (asleep, held still): it is kept near its home again.
            calmDown();
        }
        if (!level().isClientSide && isAlive() && getHealth() < getMaxHealth()) {
            // Slow natural healing; faster asleep, much faster next to an infirmary with room.
            int interval = CitizenCare.isInInfirmary(this) ? 40 : isSleeping() ? 100 : 200;
            if (++regenerationTimer >= interval) {
                regenerationTimer = 0;
                // A point for a citizen; a soldier with more health heals by the same share of it.
                heal(Math.max(1.0f, getMaxHealth() / Citizen.MAX_HEALTH));
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
    protected boolean isImmobile() {
        // A sleeping citizen lies still until it wakes up.
        return super.isImmobile() || isSleeping();
    }

    @Override
    public void onRemovedFromWorld() {
        if (!level().isClientSide && isSleeping() && getSleepingPos().map(pos -> level().isLoaded(pos)).orElse(false)) {
            stopSleeping();
        }
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
            if (io.github.sekerator.kingdomworld.common.life.TavernActions.open(serverPlayer, this)) {
                return InteractionResult.SUCCESS;
            }
            // One who manages the citizens sees the post of the citizen, or the window of the workers; with Shift, who it is.
            if (!serverPlayer.isSecondaryUseActive() && WorkforceActions.openFor(serverPlayer, this)) {
                return InteractionResult.SUCCESS;
            }
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
        // Bodies are created and removed by CitizenBodies only. The game counts here how long a mob did nothing and stops
        // its wandering after five seconds; a body lives while players are near, so the count starts anew every tick.
        setNoActionTime(0);
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
