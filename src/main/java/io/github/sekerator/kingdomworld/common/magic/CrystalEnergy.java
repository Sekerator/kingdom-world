package io.github.sekerator.kingdomworld.common.magic;

import io.github.sekerator.kingdomworld.common.guild.GuildSettings;
import io.github.sekerator.kingdomworld.common.guild.MonsterCrystalItem;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import io.github.sekerator.kingdomworld.core.magic.CrystalCharges;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The energy of the crystals a player carries, which spells of players spend. A crystal that gives part of its energy
 * stays in the inventory with what is left; one that gives everything is gone. Either the whole cost is taken or nothing.
 */
public final class CrystalEnergy {
    private CrystalEnergy() {
    }

    /**
     * The crystals of an inventory as charges.
     */
    public static List<CrystalCharges.Charge> charges(Inventory inventory) {
        List<CrystalCharges.Charge> charges = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (MonsterCrystalItem.isCrystal(stack)) {
                charges.add(new CrystalCharges.Charge(slot, stack.getCount(), MonsterCrystalItem.energy(stack), MonsterCrystalItem.isStarted(stack)));
            }
        }
        return charges;
    }

    /**
     * Energy all the crystals a player carries hold.
     */
    public static long carried(Player player) {
        return CrystalCharges.total(charges(player.getInventory()));
    }

    /**
     * Whether the crystals of a player hold {@code cost}.
     */
    public static boolean has(Player player, long cost) {
        return CrystalCharges.plan(charges(player.getInventory()), cost).isPresent();
    }

    /**
     * Takes {@code cost} energy from the crystals a player carries.
     *
     * @return whether it was taken; nothing changes when the crystals hold too little
     */
    public static boolean spend(Player player, long cost) {
        Inventory inventory = player.getInventory();
        Optional<List<CrystalCharges.Use>> plan = CrystalCharges.plan(charges(inventory), cost);
        if (plan.isEmpty()) {
            return false;
        }
        List<ItemStack> leftovers = new ArrayList<>();
        for (CrystalCharges.Use use : plan.get()) {
            ItemStack stack = inventory.getItem(use.slot());
            Rank rank = MonsterCrystalItem.rank(stack).orElse(Rank.F);
            stack.shrink(use.used());
            if (stack.isEmpty()) {
                inventory.setItem(use.slot(), ItemStack.EMPTY);
            }
            if (use.left() > 0) {
                leftovers.add(MonsterCrystalItem.started(rank, use.left()));
            }
        }
        for (ItemStack leftover : leftovers) {
            if (!inventory.add(leftover)) {
                player.drop(leftover, false);
            }
        }
        inventory.setChanged();
        return true;
    }

    /**
     * The energy of a fresh crystal of a rank, for texts.
     */
    public static long fresh(Rank rank) {
        return GuildSettings.rules().crystalEnergy(rank);
    }
}
