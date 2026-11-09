package io.github.sekerator.kingdomworld.common.magic;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.magic.MagicRules;
import io.github.sekerator.kingdomworld.core.magic.PlayerSpell;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Casting the spells of players. A cast needs the spell learned, the spell recharged and crystals that hold its cost; the
 * cost is taken from the crystals only when the spell can work, so a blink into a wall costs nothing. The line above the
 * hotbar tells what happened.
 */
public final class PlayerSpells {
    private static final String KEY = "kingdomworld.magic.cast.";
    /** When each player may cast each spell again, by game time. */
    private static final Map<UUID, Map<PlayerSpell, Long>> READY = new HashMap<>();
    private static final double HEAL_REACH = 8.0;
    /** How far a player may drift while preparing a blink, in blocks; a step farther breaks it. */
    private static final double PREPARE_DRIFT = 0.75;
    /** Ticks between two lines of the progress of a preparation. */
    private static final int PROGRESS_TICKS = 5;
    /** Blinks into the protected land of another kingdom being prepared, by player. */
    private static final Map<UUID, Preparation> PREPARING = new HashMap<>();

    /**
     * A blink being prepared: who, since when, until when, and where the player stood.
     */
    private record Preparation(ServerPlayer player, long started, long ready, Vec3 from) {
    }

    private PlayerSpells() {
    }

    public static void reset() {
        READY.clear();
        PREPARING.clear();
    }

    public static void forget(UUID player) {
        READY.remove(player);
        PREPARING.remove(player);
    }

    /**
     * Whether a player is preparing a blink into the land of another kingdom.
     */
    public static boolean preparing(ServerPlayer player) {
        return PREPARING.containsKey(player.getUUID());
    }

    /**
     * Whether a blink to a place lands in the protected land of a kingdom the player is not a member of.
     */
    public static boolean intoForeignLand(ServerPlayer player, Vec3 target) {
        UUID owner = KingdomWorldApi.ownerAt(player.serverLevel(), BlockPos.containing(target));
        return owner != null && !KingdomWorldApi.state(player.server).membership().isMember(player.getUUID(), owner);
    }

    /**
     * A blow breaks the preparation of a blink.
     */
    public static void interrupt(ServerPlayer player, String reason) {
        if (PREPARING.remove(player.getUUID()) != null) {
            player.displayClientMessage(Component.translatable(KEY + reason).withStyle(ChatFormatting.RED), true);
        }
    }

    /**
     * Every tick: the preparations go on, show their progress, break when the player moves away, and end in the blink.
     */
    static void tickPreparations() {
        if (PREPARING.isEmpty()) {
            return;
        }
        for (Preparation preparation : List.copyOf(PREPARING.values())) {
            ServerPlayer player = preparation.player();
            if (player.isRemoved() || !player.isAlive()) {
                PREPARING.remove(player.getUUID());
                continue;
            }
            if (player.position().distanceTo(preparation.from()) > PREPARE_DRIFT) {
                interrupt(player, "blink_broken_move");
                continue;
            }
            long now = player.serverLevel().getGameTime();
            if (now >= preparation.ready()) {
                PREPARING.remove(player.getUUID());
                KingdomActions.Feedback feedback = cast(player, PlayerSpell.BLINK, true);
                player.displayClientMessage(feedback.message().copy().withStyle(feedback.success() ? ChatFormatting.AQUA : ChatFormatting.RED), true);
                continue;
            }
            if ((now - preparation.started()) % PROGRESS_TICKS == 0) {
                player.displayClientMessage(progress(now - preparation.started(), preparation.ready() - preparation.started()), true);
            }
        }
    }

    /**
     * The line above the hotbar while a blink is prepared: a bar of ten cells and the percent.
     */
    public static Component progress(long done, long total) {
        int percent = total <= 0 ? 100 : (int) Math.max(0, Math.min(100, done * 100 / total));
        int cells = percent / 10;
        Component bar = Component.literal("■".repeat(cells)).withStyle(ChatFormatting.AQUA)
                .append(Component.literal("□".repeat(10 - cells)).withStyle(ChatFormatting.DARK_GRAY));
        return Component.translatable(KEY + "blink_preparing", bar, percent).withStyle(ChatFormatting.AQUA);
    }

    /**
     * Ticks until a player may cast a spell again; 0 when it is ready.
     */
    public static long cooldownLeft(ServerPlayer player, PlayerSpell spell) {
        Long ready = READY.getOrDefault(player.getUUID(), Map.of()).get(spell);
        return ready == null ? 0 : Math.max(0, ready - player.serverLevel().getGameTime());
    }

    /**
     * Sneaking with a staff chooses its next spell.
     */
    static void select(ServerPlayer player, ItemStack staff) {
        int count = MagicSettings.rules().staffSlots;
        int slot = StaffItem.selectNext(staff, count);
        if (slot < 0) {
            player.displayClientMessage(Component.translatable("kingdomworld.magic.staff.no_spells").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        PlayerSpell spell = StaffItem.selected(staff, count).orElseThrow();
        player.displayClientMessage(Component.translatable("kingdomworld.magic.staff.selected", MagicTexts.spell(spell), slot + 1, count)
                .withStyle(ChatFormatting.AQUA), true);
    }

    /**
     * Using a staff casts its chosen spell.
     */
    static void castWithStaff(ServerPlayer player, ItemStack staff) {
        Optional<PlayerSpell> spell = StaffItem.selected(staff, MagicSettings.rules().staffSlots);
        if (spell.isEmpty()) {
            player.displayClientMessage(Component.translatable("kingdomworld.magic.staff.no_spells").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        KingdomActions.Feedback feedback = cast(player, spell.get());
        player.displayClientMessage(feedback.message().copy().withStyle(feedback.success() ? ChatFormatting.AQUA : ChatFormatting.RED), true);
    }

    /**
     * Casts a spell of a player; the API the staff, the commands and the game tests use.
     */
    public static KingdomActions.Feedback cast(ServerPlayer player, PlayerSpell spell) {
        return cast(player, spell, false);
    }

    /**
     * @param prepared whether a blink was prepared and may land in the protected land of another kingdom
     */
    private static KingdomActions.Feedback cast(ServerPlayer player, PlayerSpell spell, boolean prepared) {
        MagicRules rules = MagicSettings.rules();
        if (spell == PlayerSpell.BLINK && preparing(player)) {
            return KingdomActions.Feedback.fail(KEY + "blink_already_preparing");
        }
        if (!MagicWork.state(player.server).knows(player.getUUID(), spell)) {
            return KingdomActions.Feedback.error(KingdomError.NOT_LEARNED);
        }
        long left = cooldownLeft(player, spell);
        if (left > 0) {
            return KingdomActions.Feedback.fail(KEY + "cooldown", MagicTexts.seconds(left));
        }
        long cost = rules.cost(spell);
        if (!CrystalEnergy.has(player, cost)) {
            return KingdomActions.Feedback.fail(KEY + "no_crystals", MagicTexts.energy(cost));
        }
        Vec3 blink = null;
        if (spell == PlayerSpell.BLINK) {
            blink = blinkTarget(player, rules.blinkDistance);
            if (blink == null) {
                return KingdomActions.Feedback.fail(KEY + "blink_blocked");
            }
            if (!prepared && intoForeignLand(player, blink)) {
                // Into the protected land of another kingdom only after a long preparation, which a blow or a step breaks.
                long now = player.serverLevel().getGameTime();
                PREPARING.put(player.getUUID(), new Preparation(player, now, now + rules.blinkPrepareSeconds * 20L, player.position()));
                return KingdomActions.Feedback.ok(KEY + "blink_prepare_started", rules.blinkPrepareSeconds);
            }
        }
        if (!CrystalEnergy.spend(player, cost)) {
            return KingdomActions.Feedback.fail(KEY + "no_crystals", MagicTexts.energy(cost));
        }
        ServerLevel level = player.serverLevel();
        switch (spell) {
            case FIREBALL -> {
                Vec3 look = player.getLookAngle();
                SpellFireball fireball = new SpellFireball(level, player, look.x, look.y, look.z, rules.fireballDamage);
                fireball.setPos(player.getX() + look.x, player.getEyeY() - 0.1 + look.y, player.getZ() + look.z);
                level.addFreshEntity(fireball);
                level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.6f, 1.2f);
            }
            case HEAL -> {
                LivingEntity target = healTarget(player);
                target.heal(rules.healAmount);
                level.sendParticles(ParticleTypes.HEART, target.getX(), target.getEyeY(), target.getZ(), 4, 0.3, 0.3, 0.3, 0.0);
                level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.4f);
            }
            case LIGHT -> {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, rules.lightSeconds * 20, 0, false, true));
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 1.6f);
            }
            case DETECT -> {
                int found = 0;
                for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(rules.detectRadius),
                        mob -> mob.isAlive() && mob instanceof Enemy && !(mob instanceof CitizenEntity))) {
                    mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, rules.detectSeconds * 20, 0, false, false));
                    found++;
                }
                level.playSound(null, player.blockPosition(), SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 0.8f, 1.2f);
                setCooldown(player, spell, rules);
                return KingdomActions.Feedback.ok(KEY + "detected", found, MagicTexts.energy(cost));
            }
            case SHIELD -> {
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, rules.shieldSeconds * 20, 1, false, true));
                level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_DIAMOND, SoundSource.PLAYERS, 1.0f, 0.8f);
            }
            case BLINK -> {
                level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 20, 0.3, 0.6, 0.3, 0.2);
                MagicActions.move(player, level, blink.x, blink.y, blink.z);
                level.playSound(null, BlockPos.containing(blink), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.4f);
            }
        }
        setCooldown(player, spell, rules);
        return KingdomActions.Feedback.ok(KEY + "done", MagicTexts.spell(spell), MagicTexts.energy(cost));
    }

    private static void setCooldown(ServerPlayer player, PlayerSpell spell, MagicRules rules) {
        READY.computeIfAbsent(player.getUUID(), id -> new HashMap<>()).put(spell, player.serverLevel().getGameTime() + rules.cooldown(spell));
    }

    /**
     * Whom the healing spell heals: a citizen or a player of the caster's kingdom it looks at, else the caster.
     */
    private static LivingEntity healTarget(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(HEAL_REACH));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, player.getBoundingBox().inflate(HEAL_REACH),
                entity -> entity instanceof LivingEntity && entity.isAlive(), HEAL_REACH * HEAL_REACH);
        if (hit != null && hit.getEntity() instanceof LivingEntity target && friendly(player, target)) {
            return target;
        }
        return player;
    }

    private static boolean friendly(ServerPlayer player, LivingEntity target) {
        UUID kingdom = KingdomWorldApi.kingdomOf(player.server, player.getUUID()).map(k -> k.id()).orElse(null);
        if (kingdom == null) {
            return false;
        }
        if (target instanceof CitizenEntity citizen) {
            return kingdom.equals(citizen.kingdomId());
        }
        return target instanceof Player other && KingdomWorldApi.state(player.server).membership().isMember(other.getUUID(), kingdom);
    }

    /**
     * Where a blink along the player's look ends: the farthest free spot before a wall, at most {@code distance} away;
     * {@code null} when there is no such spot a block or more away.
     */
    @Nullable
    static Vec3 blinkTarget(ServerPlayer player, int distance) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, Math.max(-0.5, Math.min(0.5, look.y)), look.z).normalize();
        Vec3 from = player.position().add(0, 0.1, 0);
        BlockHitResult wall = level.clip(new ClipContext(player.getEyePosition(), player.getEyePosition().add(flat.scale(distance)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double reach = wall.getType() == HitResult.Type.MISS ? distance : Math.max(0, wall.getLocation().distanceTo(player.getEyePosition()) - 0.6);
        for (double step = reach; step >= 1.0; step -= 0.5) {
            Vec3 spot = from.add(flat.scale(step));
            AABB box = player.getBoundingBox().move(spot.subtract(player.position()));
            if (level.noCollision(player, box) && level.isLoaded(BlockPos.containing(spot))) {
                return spot;
            }
        }
        return null;
    }
}
