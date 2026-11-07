package io.github.sekerator.kingdomworld.common.companion;

import io.github.sekerator.kingdomworld.common.army.ArmyGear;
import io.github.sekerator.kingdomworld.core.army.GearChoice;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BowlFoodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SuspiciousStewItem;
import org.jetbrains.annotations.Nullable;

/**
 * The gear and the food of a companion, all in its pack. The body holds and wears copies of the best pieces of the pack,
 * judged by what they do like the gear of soldiers (so weapons and armor of any mod count), and the wear of a blow or a hit
 * goes to the piece in the pack. A companion eats on its own: from the pack first, then from its owner's inventory.
 */
public final class CompanionGear {
    /** Slots of the body that show gear, the hands first. */
    static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private CompanionGear() {
    }

    /**
     * Where the best piece for a slot lies in the pack, or -1.
     */
    static int best(Container pack, int usable, GearChoice.Slot slot) {
        int best = -1;
        double bestScore = 0.0;
        for (int i = 0; i < Math.min(usable, pack.getContainerSize()); i++) {
            ItemStack stack = pack.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            double score = ArmyGear.score(stack, slot);
            if (score > bestScore) {
                best = i;
                bestScore = score;
            }
        }
        return best;
    }

    static boolean hasArrows(Container pack, int usable) {
        return arrowIndex(pack, usable) >= 0;
    }

    private static int arrowIndex(Container pack, int usable) {
        for (int i = 0; i < Math.min(usable, pack.getContainerSize()); i++) {
            if (pack.getItem(i).is(ItemTags.ARROWS)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Takes one arrow from the pack for a shot.
     */
    static boolean takeArrow(Container pack, int usable) {
        int index = arrowIndex(pack, usable);
        if (index < 0) {
            return false;
        }
        pack.removeItem(index, 1);
        pack.setChanged();
        return true;
    }

    /**
     * The gear the body should use: a melee weapon if the pack has one, otherwise a bow or crossbow if there are arrows,
     * a shield beside a melee weapon or bare hands, and the best armor for each slot.
     *
     * @return the index in the pack for each of {@link #SLOTS}, -1 for nothing
     */
    static int[] choose(Container pack, int usable) {
        int[] chosen = new int[SLOTS.length];
        int melee = best(pack, usable, GearChoice.Slot.MELEE);
        int ranged = best(pack, usable, GearChoice.Slot.RANGED);
        chosen[0] = melee >= 0 ? melee : ranged >= 0 && hasArrows(pack, usable) ? ranged : -1;
        boolean shooting = chosen[0] >= 0 && chosen[0] == ranged && melee < 0;
        chosen[1] = shooting ? -1 : best(pack, usable, GearChoice.Slot.SHIELD);
        chosen[2] = best(pack, usable, GearChoice.Slot.HEAD);
        chosen[3] = best(pack, usable, GearChoice.Slot.CHEST);
        chosen[4] = best(pack, usable, GearChoice.Slot.LEGS);
        chosen[5] = best(pack, usable, GearChoice.Slot.FEET);
        return chosen;
    }

    /**
     * Puts copies of the chosen gear of the pack on the body.
     */
    static void equip(CompanionEntity body, Container pack, int usable) {
        int[] chosen = choose(pack, usable);
        for (int i = 0; i < SLOTS.length; i++) {
            EquipmentSlot slot = SLOTS[i];
            ItemStack wanted = chosen[i] < 0 ? ItemStack.EMPTY : pack.getItem(chosen[i]);
            body.gearIndex[slot.ordinal()] = chosen[i];
            if (!ItemStack.matches(body.getItemBySlot(slot), wanted)) {
                body.setItemSlot(slot, wanted.copy());
            }
        }
    }

    /**
     * The weapon in hand wore by one use: the piece in the pack takes it, and the body gets the copy again.
     */
    static void wearWeapon(CompanionEntity body) {
        wear(body, EquipmentSlot.MAINHAND);
    }

    /**
     * A blow wore the armor and the shield.
     */
    static void wearArmor(CompanionEntity body) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                EquipmentSlot.OFFHAND}) {
            wear(body, slot);
        }
    }

    private static void wear(CompanionEntity body, EquipmentSlot slot) {
        CompanionBodies.Pack pack = CompanionBodies.pack(body);
        int index = body.gearIndex[slot.ordinal()];
        if (pack == null || index < 0 || index >= pack.usable()) {
            return;
        }
        ItemStack stack = pack.container().getItem(index);
        if (stack.isEmpty() || !stack.isDamageableItem()) {
            return;
        }
        if (stack.hurt(1, body.getRandom(), null)) {
            stack.shrink(1);
            stack.setDamageValue(0);
        }
        pack.container().setChanged();
        equip(body, pack.container(), pack.usable());
    }

    // ---------------------------------------------------------------- food

    /**
     * Food that a companion eats without being told: nourishing, with no effect at all (so neither rotten flesh nor a
     * golden apple), unless it has nothing else and the food does it no harm.
     */
    static int food(Container container, int from, int to, int room) {
        int best = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int i = from; i < Math.min(to, container.getContainerSize()); i++) {
            ItemStack stack = container.getItem(i);
            FoodProperties properties = properties(stack);
            if (properties == null || properties.getNutrition() <= 0 || harmful(properties)) {
                continue;
            }
            int nutrition = properties.getNutrition();
            // Prefer plain food, then food that fills the room without waste.
            int score = (properties.getEffects().isEmpty() ? 1_000 : 0) - Math.abs(room - nutrition);
            if (score > bestScore) {
                best = i;
                bestScore = score;
            }
        }
        return best;
    }

    @Nullable
    static FoodProperties properties(ItemStack stack) {
        if (stack.isEmpty() || !stack.isEdible()) {
            return null;
        }
        try {
            return stack.getFoodProperties(null);
        } catch (RuntimeException e) {
            return stack.getItem().getFoodProperties();
        }
    }

    private static boolean harmful(FoodProperties properties) {
        for (var effect : properties.getEffects()) {
            if (effect.getFirst() != null && effect.getFirst().getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                return true;
            }
        }
        return false;
    }

    /**
     * Eats one of the food at an index: gives its nutrition and what is left of it (a bowl, a bottle), or empty.
     */
    static Eaten eat(Container container, int index) {
        ItemStack stack = container.getItem(index);
        FoodProperties properties = properties(stack);
        if (properties == null) {
            return new Eaten(0, ItemStack.EMPTY);
        }
        ItemStack left = stack.hasCraftingRemainingItem() ? stack.getCraftingRemainingItem()
                : stack.getItem() instanceof BowlFoodItem || stack.getItem() instanceof SuspiciousStewItem ? new ItemStack(Items.BOWL)
                : ItemStack.EMPTY;
        container.removeItem(index, 1);
        container.setChanged();
        return new Eaten(properties.getNutrition(), left);
    }

    /**
     * What eating gave.
     */
    record Eaten(int nutrition, ItemStack left) {
    }
}
