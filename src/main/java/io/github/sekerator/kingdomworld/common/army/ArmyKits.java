package io.github.sekerator.kingdomworld.common.army;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.work.Equipment;
import io.github.sekerator.kingdomworld.common.economy.ItemPrices;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.army.ForceRules;
import io.github.sekerator.kingdomworld.core.army.Kit;
import io.github.sekerator.kingdomworld.core.army.Soldier;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.Iterator;

/**
 * The gear a kingdom of the world issues to its soldiers: a kit by branch and post, paid from the treasury at its price.
 * The money leaves the world as the pay of mercenaries does, so nothing comes from nothing. An issued piece carries a
 * mark: a soldier does not swap it for something from the storehouses, and the gear of an officer never reaches anybody
 * else. When an officer dies, is dismissed or loses its post, its issued gear is gone; the issued gear of a fighter falls
 * and goes back to the storehouses as plain gear, by the rules of all soldiers.
 */
public final class ArmyKits {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The mark of an issued piece: 1 for the kit of a fighter, 2 for the kit of an officer. */
    static final String MARK = "KingdomWorldIssued";
    private static final byte FIGHTER = 1;
    private static final byte OFFICER = 2;

    /**
     * What issuing did.
     *
     * @param pieces pieces handed out
     * @param paid   what the treasury paid for them
     * @param whole  whether the soldier has its whole kit now
     */
    public record Issued(int pieces, long paid, boolean whole) {
        static final Issued NOTHING = new Issued(0, 0, true);
    }

    private ArmyKits() {
    }

    private static byte mark(ItemStack stack) {
        CompoundTag tag = stack.isEmpty() ? null : stack.getTag();
        return tag == null ? 0 : tag.getByte(MARK);
    }

    /**
     * Whether a kingdom issued this piece to a soldier.
     */
    public static boolean isIssued(ItemStack stack) {
        return mark(stack) != 0;
    }

    /**
     * Whether this piece belongs to the issued kit of an officer.
     */
    public static boolean isOfficerIssue(ItemStack stack) {
        return mark(stack) == OFFICER;
    }

    /**
     * What an issued piece is once it leaves its soldier: nothing if it is the gear of an officer, a plain piece without
     * the mark otherwise. Anything else stays what it is.
     */
    public static ItemStack released(ItemStack stack) {
        byte mark = mark(stack);
        if (mark == 0) {
            return stack;
        }
        if (mark == OFFICER) {
            return ItemStack.EMPTY;
        }
        ItemStack plain = stack.copy();
        plain.removeTagKey(MARK);
        return plain;
    }

    /**
     * The gear of a body that died: issued gear of an officer does not fall, issued gear of a fighter falls as plain gear.
     */
    static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof CitizenEntity body) || body.level().isClientSide()) {
            return;
        }
        for (Iterator<ItemEntity> drops = event.getDrops().iterator(); drops.hasNext(); ) {
            ItemEntity drop = drops.next();
            ItemStack stack = drop.getItem();
            if (!isIssued(stack)) {
                continue;
            }
            ItemStack plain = released(stack);
            if (plain.isEmpty()) {
                drops.remove();
            } else {
                drop.setItem(plain);
            }
        }
    }

    // ---------------------------------------------------------------- prices

    private static long worth(MinecraftServer server, String itemId) {
        return ItemPrices.table(server).value(itemId).units();
    }

    /**
     * What a piece of a kit costs a treasury.
     */
    public static long price(MinecraftServer server, Kit.Piece piece) {
        return Kit.price(piece, id -> worth(server, id), ArmySettings.force().enchantPercent);
    }

    /**
     * What the whole kit of a soldier costs.
     */
    public static long price(MinecraftServer server, Soldier soldier, boolean archer) {
        ForceRules rules = ArmySettings.force();
        return rules.kit(soldier.branch(), soldier.post()).price(archer, id -> worth(server, id), rules.enchantPercent);
    }

    // ---------------------------------------------------------------- issuing

    private static ItemStack make(Kit.Piece piece, boolean officer) {
        ResourceLocation id = ResourceLocation.tryParse(piece.item());
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        if (piece.enchanted()) {
            ResourceLocation enchantmentId = ResourceLocation.tryParse(piece.enchantment());
            Enchantment enchantment = enchantmentId == null ? null : ForgeRegistries.ENCHANTMENTS.getValue(enchantmentId);
            if (enchantment != null) {
                stack.enchant(enchantment, piece.level());
            }
        }
        stack.getOrCreateTag().putByte(MARK, officer ? OFFICER : FIGHTER);
        return stack;
    }

    /**
     * Whether a slot holds the piece the kit asks for: the same item, issued for the same kind of post.
     */
    private static boolean holds(ItemStack current, Kit.Piece piece, boolean officer) {
        if (mark(current) != (officer ? OFFICER : FIGHTER)) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(current.getItem());
        return id != null && id.toString().equals(piece.item());
    }

    /**
     * What the pieces a soldier still lacks of its kit cost.
     */
    public static long missingPrice(MinecraftServer server, Citizen citizen, Soldier soldier) {
        ForceRules rules = ArmySettings.force();
        Equipment.Hands hands = ArmyWork.hands(server, citizen.id());
        boolean officer = soldier.post().officer();
        long sum = 0;
        for (Kit.Piece piece : rules.kit(soldier.branch(), soldier.post()).pieces(Professions.ARCHER.id().equals(citizen.professionId()))) {
            EquipmentSlot slot = EquipmentSlot.byName(piece.slot());
            if (!holds(hands.get(slot), piece, officer)) {
                sum += price(server, piece);
            }
        }
        return sum;
    }

    /**
     * Issues a soldier the pieces of its kit it lacks, each paid from the treasury of its kingdom at its price; a piece the
     * treasury cannot pay is not issued. What the soldier held in the slot goes back to the storehouses, and issued gear
     * of another post is given up as issued gear is.
     *
     * @param free whether the kingdom has the gear already: at its founding the gear is a part of its wealth
     */
    public static Issued issue(MinecraftServer server, Citizen citizen, Soldier soldier, boolean free) {
        if (ArmyWork.isReadOnly(server)) {
            return Issued.NOTHING;
        }
        ForceRules rules = ArmySettings.force();
        Equipment.Hands hands = ArmyWork.hands(server, citizen.id());
        boolean officer = soldier.post().officer();
        long day = ArmyWork.day(server);
        int pieces = 0;
        long paid = 0;
        boolean whole = true;
        for (Kit.Piece piece : rules.kit(soldier.branch(), soldier.post()).pieces(Professions.ARCHER.id().equals(citizen.professionId()))) {
            EquipmentSlot slot = EquipmentSlot.byName(piece.slot());
            ItemStack current = hands.get(slot);
            if (holds(current, piece, officer)) {
                continue;
            }
            ItemStack made = make(piece, officer);
            if (made.isEmpty()) {
                // A pack without the item: the soldier keeps what it has.
                whole = false;
                continue;
            }
            long price = free ? 0 : price(server, piece);
            if (price > 0 && KingdomWorldApi.state(server).economy().treasury(citizen.kingdomId()).map(treasury -> treasury.funds() < price)
                    .orElse(true)) {
                whole = false;
                continue;
            }
            ItemStack old = released(current);
            if (!old.isEmpty()) {
                ItemStack rest = KingdomStorage.insert(server, citizen.kingdomId(), old, citizen.lastPos());
                if (!rest.isEmpty() && !isIssued(current)) {
                    // No room for a piece of the soldier's own: it stays, and nothing is paid.
                    whole = false;
                    continue;
                }
                // An issued piece of another post that finds no room in the storehouses is given up.
            }
            if (price > 0 && !KingdomWorldApi.state(server).economy().armyPayment(citizen.kingdomId(), price, day).ok()) {
                hands.set(slot, ItemStack.EMPTY);
                whole = false;
                continue;
            }
            hands.set(slot, made);
            pieces++;
            paid += price;
        }
        if (pieces > 0) {
            LOGGER.debug("Soldier {} was issued {} piece(s) of the kit of {} {} for {}", citizen.shortId(), pieces, soldier.branch().id(),
                    soldier.post().id(), paid);
        }
        return new Issued(pieces, paid, whole);
    }

    /**
     * Takes the issued gear of a soldier who leaves the army or its post: the gear of an officer is gone, the gear of a
     * fighter goes back to the storehouses as plain gear.
     *
     * @param officerOnly whether only the gear of an officer is taken
     */
    public static void giveUp(MinecraftServer server, Citizen citizen, boolean officerOnly) {
        Equipment.Hands hands = ArmyWork.hands(server, citizen.id());
        for (EquipmentSlot slot : io.github.sekerator.kingdomworld.common.citizen.GearRegistry.SLOTS) {
            ItemStack current = hands.get(slot);
            if (!isIssued(current) || (officerOnly && !isOfficerIssue(current))) {
                continue;
            }
            ItemStack plain = released(current);
            hands.set(slot, plain.isEmpty() ? ItemStack.EMPTY : KingdomStorage.insert(server, citizen.kingdomId(), plain, citizen.lastPos()));
        }
    }
}
