package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.GearRegistry;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.ToolChoice;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Tools and armor of workers. Tools are recognised by what they can do (Forge tool actions), so axes, pickaxes,
 * rods and shears of every mod serve as well as vanilla ones; their tier counts where the work needs strength.
 * A worker takes a tool from the storehouses when it has none, it wears out with use, and a broken one is
 * replaced by the next. Armor is taken from the storehouses too and lowers the risk of the work.
 *
 * <p>The same code works on a body in the world and on the gear of a citizen without a body ({@link Hands}).
 */
public final class Equipment {
    /** Armor slots, head first. */
    public static final List<EquipmentSlot> ARMOR = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET);

    /**
     * What tool a profession needs.
     */
    public enum ToolNeed {
        NONE(null, false),
        AXE(ToolActions.AXE_DIG, true),
        PICKAXE(ToolActions.PICKAXE_DIG, true),
        FISHING_ROD(ToolActions.FISHING_ROD_CAST, true),
        SHEARS(ToolActions.SHEARS_HARVEST, false),
        /** Anything to strike with; a herder culls bare handed if there is none. */
        WEAPON(ToolActions.SWORD_SWEEP, false);

        @Nullable
        private final ToolAction action;
        private final boolean required;

        ToolNeed(@Nullable ToolAction action, boolean required) {
            this.action = action;
            this.required = required;
        }

        /**
         * Whether the work cannot be done without such a tool.
         */
        public boolean required() {
            return required;
        }

        public boolean matches(ItemStack stack) {
            if (stack.isEmpty() || action == null) {
                return false;
            }
            if (this == WEAPON) {
                return stack.canPerformAction(ToolActions.SWORD_SWEEP) || stack.canPerformAction(ToolActions.SWORD_DIG)
                        || stack.canPerformAction(ToolActions.AXE_DIG) || stack.getItem() instanceof SwordItem;
            }
            return stack.canPerformAction(action);
        }
    }

    /**
     * Where a worker's things are: the slots of its body, or its stored gear while it has no body.
     */
    public interface Hands {
        ItemStack get(EquipmentSlot slot);

        void set(EquipmentSlot slot, ItemStack stack);
    }

    private Equipment() {
    }

    public static Hands of(CitizenEntity body) {
        return new Hands() {
            @Override
            public ItemStack get(EquipmentSlot slot) {
                return body.getItemBySlot(slot);
            }

            @Override
            public void set(EquipmentSlot slot, ItemStack stack) {
                body.setItemSlot(slot, stack);
                // What a body carries falls where it dies.
                body.setDropChance(slot, 2.0f);
            }
        };
    }

    public static Hands of(MinecraftServer server, UUID citizenId) {
        GearRegistry gear = KingdomSavedData.get(server).gear();
        return new Hands() {
            @Override
            public ItemStack get(EquipmentSlot slot) {
                return gear.get(citizenId, slot);
            }

            @Override
            public void set(EquipmentSlot slot, ItemStack stack) {
                gear.set(citizenId, slot, stack);
                gear.changed();
            }
        };
    }

    /**
     * Strength of a tool by its tier (Forge's order of tiers, which mods extend), 0 for tools without one.
     */
    public static int tier(ItemStack stack) {
        if (stack.getItem() instanceof TieredItem tiered) {
            Tier tier = tiered.getTier();
            int index = TierSortingRegistry.getSortedTiers().indexOf(tier);
            return index < 0 ? 1 : index + 1;
        }
        return 0;
    }

    public static int durabilityLeft(ItemStack stack) {
        return stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : Integer.MAX_VALUE;
    }

    /**
     * Makes sure the slot holds a tool for the work, taking one from the storehouses if needed. A tool that does
     * not suit is put back into the storehouses.
     *
     * @param neededTier strength the work needs, 0 if any will do
     * @return whether the slot now holds a suitable tool
     */
    public static boolean equipTool(MinecraftServer server, UUID kingdomId, Hands hands, EquipmentSlot slot, ToolNeed need,
                                    int neededTier, @Nullable WorldPos near) {
        if (need == ToolNeed.NONE) {
            return true;
        }
        ItemStack current = hands.get(slot);
        if (need.matches(current) && (neededTier <= 0 || tier(current) >= neededTier)) {
            return true;
        }
        List<KingdomStorage.Found> found = KingdomStorage.find(server, kingdomId, need::matches, near);
        if (found.isEmpty()) {
            return need.matches(current);
        }
        List<ToolChoice.Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) {
            ItemStack stack = found.get(i).stack();
            candidates.add(new ToolChoice.Candidate(i, tier(stack), durabilityLeft(stack)));
        }
        if (need.matches(current)) {
            // A tool in hand that is too weak is swapped only for a stronger one.
            candidates.removeIf(candidate -> candidate.tier() <= tier(current));
            if (candidates.isEmpty()) {
                return true;
            }
        }
        OptionalInt choice = ToolChoice.choose(candidates, neededTier);
        if (choice.isEmpty()) {
            return need.matches(current);
        }
        if (!current.isEmpty()) {
            ItemStack rest = KingdomStorage.insert(server, kingdomId, current, near);
            if (!rest.isEmpty()) {
                // No room for the old tool: keep working with what is in hand.
                return need.matches(current);
            }
            hands.set(slot, ItemStack.EMPTY);
        }
        ItemStack tool = KingdomStorage.extract(server, found.get(choice.getAsInt()), 1);
        if (tool.isEmpty()) {
            return false;
        }
        hands.set(slot, tool);
        return true;
    }

    /**
     * Puts on armor from the storehouses for every empty armor slot, the strongest piece there is.
     */
    public static void equipArmor(MinecraftServer server, UUID kingdomId, Hands hands, @Nullable WorldPos near) {
        for (EquipmentSlot slot : ARMOR) {
            if (!hands.get(slot).isEmpty()) {
                continue;
            }
            List<KingdomStorage.Found> found = KingdomStorage.find(server, kingdomId,
                    stack -> stack.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == slot, near);
            KingdomStorage.Found best = null;
            for (KingdomStorage.Found candidate : found) {
                if (best == null || defense(candidate.stack()) > defense(best.stack())) {
                    best = candidate;
                }
            }
            if (best != null) {
                ItemStack piece = KingdomStorage.extract(server, best, 1);
                if (!piece.isEmpty()) {
                    hands.set(slot, piece);
                }
            }
        }
    }

    private static int defense(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem armor ? armor.getDefense() : 0;
    }

    /**
     * Armor points of what a worker wears.
     */
    public static float armorPoints(Hands hands) {
        float points = 0.0f;
        for (EquipmentSlot slot : ARMOR) {
            points += defense(hands.get(slot));
        }
        return points;
    }

    /**
     * Wears down the tool in a slot; a broken tool is gone.
     *
     * @return whether the tool broke
     */
    public static boolean wear(Hands hands, EquipmentSlot slot, int uses, RandomSource random) {
        ItemStack stack = hands.get(slot);
        if (stack.isEmpty() || uses <= 0 || !stack.isDamageableItem()) {
            return false;
        }
        boolean broken = stack.hurt(uses, random, null);
        if (broken) {
            stack.shrink(1);
            hands.set(slot, ItemStack.EMPTY);
            return true;
        }
        hands.set(slot, stack);
        return false;
    }

    /**
     * Puts a tool that does not suit the work (any more) back into the storehouses.
     */
    public static void stowUnsuitable(MinecraftServer server, UUID kingdomId, Hands hands, EquipmentSlot slot, ToolNeed need,
                                      @Nullable WorldPos near) {
        ItemStack current = hands.get(slot);
        if (current.isEmpty() || need.matches(current)) {
            return;
        }
        ItemStack rest = KingdomStorage.insert(server, kingdomId, current, near);
        hands.set(slot, rest);
    }
}
