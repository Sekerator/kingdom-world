package io.github.sekerator.kingdomworld.common.army;

import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.work.Equipment;
import io.github.sekerator.kingdomworld.core.army.ArmyState;
import io.github.sekerator.kingdomworld.core.army.ForceRules;
import io.github.sekerator.kingdomworld.core.army.Post;
import io.github.sekerator.kingdomworld.core.army.Soldier;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/**
 * The strength of the bodies of soldiers by branch and post: full health, damage, armor and knockback resistance, and the
 * shield that wards off a share of the blows. A fighter has the strength of its branch. An officer has the strength of its
 * post only while it wears armor of the post, every piece: diamond or better for a vice-captain, netherite or better for
 * a captain, enchanted. Without it an officer is as strong as a fighter, so a post alone gives nothing.
 */
public final class ArmyForce {
    private static final UUID DAMAGE = UUID.fromString("6f0c2e4a-51a7-4f0e-9b1d-2a4c8e1f7a01");
    private static final UUID DAMAGE_FACTOR = UUID.fromString("6f0c2e4a-51a7-4f0e-9b1d-2a4c8e1f7a02");
    private static final UUID ARMOR = UUID.fromString("6f0c2e4a-51a7-4f0e-9b1d-2a4c8e1f7a03");
    private static final UUID KNOCKBACK = UUID.fromString("6f0c2e4a-51a7-4f0e-9b1d-2a4c8e1f7a04");
    /** Ticks an axe keeps the shield of a soldier down, as it does the shield of a player. */
    private static final int SHIELD_DOWN_TICKS = 100;
    private static final String SHIELD_DOWN = "KingdomWorldShieldDown";

    private ArmyForce() {
    }

    // ---------------------------------------------------------------- the post that counts

    /**
     * Whether what a soldier wears is armor of a post: four pieces, each as tough as the post asks and enchanted.
     */
    public static boolean wearsArmorOf(Equipment.Hands hands, Post post, ForceRules rules) {
        if (!post.officer()) {
            return true;
        }
        double tough = ForceRules.pieceToughness(post);
        for (EquipmentSlot slot : Equipment.ARMOR) {
            ItemStack piece = hands.get(slot);
            if (piece.isEmpty() || ArmyGear.armorSlot(piece) != slot || ArmyGear.armorToughness(piece, slot) < tough
                    || (rules.enchantedArmor && !piece.isEnchanted())) {
                return false;
            }
        }
        return true;
    }

    /**
     * The post a soldier has the strength of: its own while it wears the armor of the post, that of a fighter otherwise.
     */
    public static Post standing(MinecraftServer server, Soldier soldier) {
        return standing(ArmyWork.hands(server, soldier.citizenId()), soldier);
    }

    private static Post standing(Equipment.Hands hands, Soldier soldier) {
        return wearsArmorOf(hands, soldier.post(), ArmySettings.force()) ? soldier.post() : Post.FIGHTER;
    }

    /**
     * The full health of a soldier now.
     */
    public static float maxHealth(MinecraftServer server, Soldier soldier) {
        return (float) ArmySettings.force().health(soldier.branch(), standing(server, soldier));
    }

    /**
     * How much stronger than a plain citizen with the same gear a soldier is in the battles far from players.
     */
    public static double might(MinecraftServer server, Soldier soldier) {
        return ArmySettings.force().might(soldier.branch(), standing(server, soldier));
    }

    // ---------------------------------------------------------------- records

    /**
     * Keeps the full health of every citizen in step with the army: that of its branch and post for a soldier, that of a
     * citizen for everybody else.
     */
    static void keepHealth(MinecraftServer server, ArmyState army, KingdomWorldState world) {
        for (Citizen citizen : world.citizens().registry().all()) {
            Optional<Soldier> soldier = Professions.isMilitary(citizen.professionId()) ? army.soldier(citizen.id()) : Optional.empty();
            float full = soldier.map(record -> maxHealth(server, record)).orElse(Citizen.MAX_HEALTH);
            if (citizen.maxHealth() != full) {
                citizen.setMaxHealth(full);
            }
        }
    }

    // ---------------------------------------------------------------- bodies

    private static void set(CitizenEntity body, Attribute attribute, UUID id, String name, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = body.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (current != null && current.getAmount() == amount) {
            return;
        }
        if (current != null) {
            instance.removeModifier(id);
        }
        if (amount != 0.0) {
            instance.addTransientModifier(new AttributeModifier(id, name, amount, operation));
        }
    }

    /**
     * Gives a body the strength of its branch and post, or takes it away from a body that is no soldier any more. The
     * health of the body keeps its share of the full health.
     */
    public static void apply(MinecraftServer server, CitizenEntity body, Citizen citizen) {
        Optional<Soldier> soldier = Professions.isMilitary(citizen.professionId()) && !ArmyWork.isReadOnly(server)
                ? ArmyWork.state(server).soldier(citizen.id()) : Optional.empty();
        ForceRules rules = ArmySettings.force();
        double full = Citizen.MAX_HEALTH;
        double damage = 0.0;
        double factor = 1.0;
        double armor = 0.0;
        double knockback = 0.0;
        if (soldier.isPresent()) {
            Post post = standing(Equipment.of(body), soldier.get());
            ForceRules.Stats stats = rules.stats(soldier.get().branch(), post);
            full = rules.health(soldier.get().branch(), post);
            damage = stats.damage();
            factor = rules.damageFactor(soldier.get().branch(), post);
            armor = stats.armor();
            knockback = stats.knockback();
        }
        AttributeInstance health = body.getAttribute(Attributes.MAX_HEALTH);
        if (health != null && health.getBaseValue() != full) {
            float before = body.getMaxHealth();
            float share = before > 0 ? body.getHealth() / before : 1.0f;
            health.setBaseValue(full);
            if (body.isAlive()) {
                body.setHealth(Math.max(1.0f, share * body.getMaxHealth()));
            }
        }
        if (citizen.maxHealth() != (float) full) {
            citizen.setMaxHealth((float) full);
        }
        set(body, Attributes.ATTACK_DAMAGE, DAMAGE, "Post of a soldier", damage, AttributeModifier.Operation.ADDITION);
        set(body, Attributes.ATTACK_DAMAGE, DAMAGE_FACTOR, "Branch of a soldier", factor - 1.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
        set(body, Attributes.ARMOR, ARMOR, "Post of a soldier", armor, AttributeModifier.Operation.ADDITION);
        set(body, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK, "Post of a soldier", knockback, AttributeModifier.Operation.ADDITION);
    }

    /**
     * Makes an arrow of a soldier as strong as its branch and post make its blows.
     *
     * @param velocity how fast the arrow flies: its damage is its base damage times that
     */
    static void arrow(MinecraftServer server, CitizenEntity body, AbstractArrow arrow, float velocity) {
        if (body.citizenId() == null || ArmyWork.isReadOnly(server)) {
            return;
        }
        ArmyWork.state(server).soldier(body.citizenId()).ifPresent(soldier -> {
            ForceRules rules = ArmySettings.force();
            Post post = standing(Equipment.of(body), soldier);
            double added = rules.stats(soldier.branch(), post).damage() / Math.max(0.1f, velocity);
            arrow.setBaseDamage((arrow.getBaseDamage() + added) * rules.damageFactor(soldier.branch(), post));
        });
    }

    // ---------------------------------------------------------------- shields

    /**
     * Whether a soldier wards a blow off with its shield: a blow or an arrow from the front that a shield can stop, by the
     * chance of the config. An axe gets through and keeps the shield down for a while, as it does with players.
     */
    static boolean blocks(CitizenEntity body, DamageSource source) {
        double chance = ArmySettings.force().blockChance;
        if (chance <= 0.0 || !ArmyGear.isShield(body.getItemBySlot(EquipmentSlot.OFFHAND)) || source.is(DamageTypeTags.BYPASSES_SHIELD)
                || !Professions.isMilitary(body.professionId())) {
            return false;
        }
        Vec3 from = source.getSourcePosition();
        if (from == null) {
            return false;
        }
        Vec3 look = body.getViewVector(1.0f);
        Vec3 toward = from.subtract(body.position());
        toward = new Vec3(toward.x, 0.0, toward.z);
        if (toward.lengthSqr() < 1.0e-6 || toward.normalize().dot(new Vec3(look.x, 0.0, look.z)) <= 0.0) {
            return false;
        }
        long now = body.level().getGameTime();
        if (body.getPersistentData().getLong(SHIELD_DOWN) > now) {
            return false;
        }
        if (source.getDirectEntity() instanceof LivingEntity attacker
                && attacker.getMainHandItem().canDisableShield(body.getItemBySlot(EquipmentSlot.OFFHAND), body, attacker)) {
            body.getPersistentData().putLong(SHIELD_DOWN, now + SHIELD_DOWN_TICKS);
            body.playSound(SoundEvents.SHIELD_BREAK, 0.8f, 0.8f + body.getRandom().nextFloat() * 0.4f);
            return false;
        }
        if (body.getRandom().nextDouble() >= chance) {
            return false;
        }
        body.playSound(SoundEvents.SHIELD_BLOCK, 1.0f, 0.8f + body.getRandom().nextFloat() * 0.4f);
        Equipment.wear(Equipment.of(body), EquipmentSlot.OFFHAND, 1, body.getRandom());
        return true;
    }
}
