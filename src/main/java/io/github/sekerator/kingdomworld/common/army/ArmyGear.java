package io.github.sekerator.kingdomworld.common.army;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.citizen.work.Equipment;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.army.GearChoice;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.ToolActions;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * The gear of soldiers: weapons, shields and armor of any mod, judged by what they do. A melee weapon by the attack damage
 * and attack speed it gives (so swords of Simply Swords and the weapons of Better Combat count by their attributes), a bow or
 * a crossbow by its kind and enchantments, armor by the defense and toughness it gives in its slot, a shield by what it can do
 * (Forge's shield block action). Guns of mods that are no bows count only for the melee damage they give, so they are left
 * alone unless they work as a club. The config overrides scores of items and tags or forbids them.
 *
 * <p>Gear comes from the kingdom's storehouses and goes back there; a piece that wears out is replaced from them.
 */
public final class ArmyGear {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Attacks per second of a hand that holds nothing that slows it. */
    private static final double BASE_SPEED = 4.0;

    /**
     * What a soldier took, for logs and checks.
     */
    public record Choice(EquipmentSlot slot, String item, double score) {
    }

    private ArmyGear() {
    }

    // ---------------------------------------------------------------- judging gear

    private static double sum(ItemStack stack, EquipmentSlot slot, Attribute attribute) {
        double sum = 0.0;
        for (AttributeModifier modifier : stack.getAttributeModifiers(slot).get(attribute)) {
            if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
                sum += modifier.getAmount();
            }
        }
        return sum;
    }

    /**
     * The attack damage a weapon adds in the main hand.
     */
    public static double meleeDamage(ItemStack stack) {
        return stack.isEmpty() ? 0.0 : Math.max(0.0, sum(stack, EquipmentSlot.MAINHAND, Attributes.ATTACK_DAMAGE));
    }

    /**
     * Attacks per second with the item in the main hand.
     */
    public static double attackSpeed(ItemStack stack) {
        return stack.isEmpty() ? BASE_SPEED : Math.max(0.1, BASE_SPEED + sum(stack, EquipmentSlot.MAINHAND, Attributes.ATTACK_SPEED));
    }

    private static String id(Item item) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        return key == null ? "" : key.toString();
    }

    private static List<String> tags(ItemStack stack) {
        List<String> result = new ArrayList<>();
        stack.getTags().forEach(tag -> result.add(tag.location().toString()));
        return result;
    }

    private static boolean listed(ItemStack stack, List<String> entries) {
        String id = id(stack.getItem());
        for (String entry : entries) {
            if (entry.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(entry.substring(1));
                if (tag != null && stack.is(TagKey.create(net.minecraft.core.registries.Registries.ITEM, tag))) {
                    return true;
                }
            } else if (entry.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether archers can shoot with the item: a bow or a crossbow of any mod, or a weapon the config lists that shoots
     * arrows like a bow. Guns that do not are not ranged weapons for soldiers.
     */
    public static boolean isRanged(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        if (item instanceof BowItem || item instanceof CrossbowItem) {
            return true;
        }
        return item instanceof ProjectileWeaponItem weapon && weapon.getAllSupportedProjectiles().test(new ItemStack(Items.ARROW))
                && listed(stack, ArmySettings.rangedWeapons());
    }

    public static boolean isCrossbow(ItemStack stack) {
        return stack.getItem() instanceof CrossbowItem;
    }

    public static boolean isShield(ItemStack stack) {
        return !stack.isEmpty() && stack.canPerformAction(ToolActions.SHIELD_BLOCK);
    }

    /**
     * The armor slot of a piece of armor, or {@code null} for anything else.
     */
    @Nullable
    public static EquipmentSlot armorSlot(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        EquipmentSlot slot = LivingEntity.getEquipmentSlotForItem(stack);
        return slot.getType() == EquipmentSlot.Type.ARMOR ? slot : null;
    }

    public static double armorDefense(ItemStack stack, EquipmentSlot slot) {
        return Math.max(0.0, sum(stack, slot, Attributes.ARMOR));
    }

    public static double armorToughness(ItemStack stack, EquipmentSlot slot) {
        return Math.max(0.0, sum(stack, slot, Attributes.ARMOR_TOUGHNESS));
    }

    /**
     * What arrows from a ranged weapon hit for, roughly: a bow 6, a crossbow 9, more with Power.
     */
    public static double rangedDamage(ItemStack stack) {
        if (!isRanged(stack)) {
            return 0.0;
        }
        double base = isCrossbow(stack) ? 9.0 : 6.0;
        int power = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, stack);
        int piercing = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PIERCING, stack);
        int multishot = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MULTISHOT, stack);
        return base + 1.5 * power + 0.5 * piercing + (multishot > 0 ? 2.0 : 0.0);
    }

    /**
     * How good a piece is for a slot, with the overrides of the config; 0 when it does not fit or is forbidden.
     */
    public static double score(ItemStack stack, GearChoice.Slot slot) {
        if (stack.isEmpty()) {
            return 0.0;
        }
        double own = switch (slot) {
            case MELEE -> isRanged(stack) || isShield(stack) ? 0.0 : GearChoice.meleeScore(meleeDamage(stack), attackSpeed(stack));
            case RANGED -> rangedDamage(stack);
            case SHIELD -> isShield(stack) ? 1.0 + Math.min(10_000, stack.getMaxDamage()) / 100.0 : 0.0;
            default -> {
                EquipmentSlot equipment = equipmentSlot(slot);
                yield armorSlot(stack) == equipment ? GearChoice.armorScore(armorDefense(stack, equipment), armorToughness(stack, equipment)) : 0.0;
            }
        };
        if (own <= 0.0) {
            return 0.0;
        }
        OptionalDouble override = ArmySettings.gear().of(id(stack.getItem()), tags(stack));
        if (override.isPresent()) {
            return override.getAsDouble() == GearChoice.DENIED ? 0.0 : override.getAsDouble();
        }
        return own;
    }

    static EquipmentSlot equipmentSlot(GearChoice.Slot slot) {
        return switch (slot) {
            case MELEE, RANGED -> EquipmentSlot.MAINHAND;
            case SHIELD -> EquipmentSlot.OFFHAND;
            case HEAD -> EquipmentSlot.HEAD;
            case CHEST -> EquipmentSlot.CHEST;
            case LEGS -> EquipmentSlot.LEGS;
            case FEET -> EquipmentSlot.FEET;
        };
    }

    private static GearChoice.Slot armorChoiceSlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> GearChoice.Slot.HEAD;
            case CHEST -> GearChoice.Slot.CHEST;
            case LEGS -> GearChoice.Slot.LEGS;
            default -> GearChoice.Slot.FEET;
        };
    }

    // ---------------------------------------------------------------- taking gear

    /**
     * Makes sure a soldier holds the best weapon (a bow or crossbow for an archer, if there is any) and wears the best
     * armor and, for a warrior, a shield the storehouses have; better pieces replace worse ones, which go back.
     *
     * @return what the soldier took this time
     */
    public static List<Choice> equip(MinecraftServer server, UUID kingdomId, Equipment.Hands hands, boolean archer, @Nullable WorldPos near) {
        List<Choice> taken = new ArrayList<>();
        if (archer) {
            if (!take(server, kingdomId, hands, GearChoice.Slot.RANGED, near, taken) && score(hands.get(EquipmentSlot.MAINHAND),
                    GearChoice.Slot.RANGED) <= 0) {
                // No bow anywhere: an archer fights with what it can.
                take(server, kingdomId, hands, GearChoice.Slot.MELEE, near, taken);
            }
        } else {
            take(server, kingdomId, hands, GearChoice.Slot.MELEE, near, taken);
            take(server, kingdomId, hands, GearChoice.Slot.SHIELD, near, taken);
        }
        for (EquipmentSlot slot : Equipment.ARMOR) {
            take(server, kingdomId, hands, armorChoiceSlot(slot), near, taken);
        }
        return taken;
    }

    /**
     * Takes the best piece for a slot if it is better than the one worn.
     *
     * @return whether the slot holds a piece for this kind of slot now
     */
    private static boolean take(MinecraftServer server, UUID kingdomId, Equipment.Hands hands, GearChoice.Slot slot, @Nullable WorldPos near,
                                List<Choice> taken) {
        EquipmentSlot equipment = equipmentSlot(slot);
        ItemStack current = hands.get(equipment);
        double currentScore = score(current, slot);
        boolean wrongKind = slot == GearChoice.Slot.RANGED && !current.isEmpty() && currentScore <= 0
                && score(current, GearChoice.Slot.MELEE) > 0;
        List<KingdomStorage.Found> found = KingdomStorage.find(server, kingdomId, stack -> score(stack, slot) > 0, near);
        List<GearChoice.Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) {
            ItemStack stack = found.get(i).stack();
            candidates.add(new GearChoice.Candidate(i, slot, score(stack, slot), Equipment.durabilityLeft(stack)));
        }
        OptionalInt best = GearChoice.best(candidates, slot);
        if (best.isEmpty() || !(wrongKind || GearChoice.better(candidates.get(best.getAsInt()).score(), currentScore))) {
            return currentScore > 0;
        }
        if (!current.isEmpty()) {
            ItemStack rest = KingdomStorage.insert(server, kingdomId, current, near);
            if (!rest.isEmpty()) {
                // No room for the old piece: it stays.
                return currentScore > 0;
            }
            hands.set(equipment, ItemStack.EMPTY);
        }
        ItemStack piece = KingdomStorage.extract(server, found.get(best.getAsInt()), 1);
        if (piece.isEmpty()) {
            return false;
        }
        hands.set(equipment, piece);
        taken.add(new Choice(equipment, id(piece.getItem()), score(piece, slot)));
        return true;
    }

    /**
     * Brings all the gear of a soldier back to the storehouses; what finds no room stays with the citizen.
     *
     * @return items brought back
     */
    public static int stowAll(MinecraftServer server, UUID kingdomId, Equipment.Hands hands, @Nullable WorldPos near) {
        int count = 0;
        for (EquipmentSlot slot : io.github.sekerator.kingdomworld.common.citizen.GearRegistry.SLOTS) {
            ItemStack stack = hands.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int before = stack.getCount();
            ItemStack rest = KingdomStorage.insert(server, kingdomId, stack, near);
            count += before - rest.getCount();
            hands.set(slot, rest);
        }
        return count;
    }

    /**
     * Logs what a soldier took, so that the gear of mods can be checked on a server.
     */
    public static void log(String who, List<Choice> taken) {
        for (Choice choice : taken) {
            LOGGER.info("Soldier {} took {} for {} (score {})", who, choice.item(), choice.slot().getName(),
                    String.format(java.util.Locale.ROOT, "%.1f", choice.score()));
        }
    }

    /**
     * An arrow shot from a weapon, as a skeleton shoots: the arrow a bow of a mod makes of it, never picked up again.
     */
    public static net.minecraft.world.entity.projectile.AbstractArrow arrow(LivingEntity shooter, ItemStack weapon, float power) {
        ItemStack ammo = new ItemStack(Items.ARROW);
        net.minecraft.world.entity.projectile.AbstractArrow arrow = ((ArrowItem) Items.ARROW).createArrow(shooter.level(), ammo, shooter);
        arrow.setEnchantmentEffectsFromEntity(shooter, power);
        if (weapon.getItem() instanceof BowItem bow) {
            arrow = bow.customArrow(arrow);
        }
        if (isCrossbow(weapon)) {
            arrow.setBaseDamage(arrow.getBaseDamage() + 1.0);
            arrow.setShotFromCrossbow(true);
        }
        arrow.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
        if (arrow instanceof Arrow plain) {
            plain.setEffectsFromItem(ammo);
        }
        return arrow;
    }
}
