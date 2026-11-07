package io.github.sekerator.kingdomworld.common.road;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * The gear of robbers, from the tables of the config: items and tags of any mod with weights. The leader takes the best of
 * a few picks and always wears armor. Robbers never drop their gear, so none of it comes into the world.
 */
public final class BanditGear {
    private static final Logger LOGGER = LogUtils.getLogger();

    private record Entry(List<Item> items, int weight) {
    }

    private static List<? extends String> weaponsKey;
    private static List<Entry> weapons = List.of();
    private static List<? extends String> armorKey;
    private static List<Entry> armor = List.of();

    private BanditGear() {
    }

    /**
     * Tags may have changed: the tables are read again when next needed.
     */
    public static void invalidate() {
        weaponsKey = null;
        armorKey = null;
    }

    /**
     * Reads a table: {@code item=weight} or {@code #tag=weight}; entries that name nothing known are skipped.
     */
    static List<Entry> parse(List<? extends String> lines) {
        List<Entry> result = new ArrayList<>();
        for (String line : lines) {
            String text = line.strip();
            int weight = 1;
            int eq = text.lastIndexOf('=');
            if (eq > 0) {
                try {
                    weight = Math.max(0, Integer.parseInt(text.substring(eq + 1).strip()));
                } catch (NumberFormatException e) {
                    LOGGER.warn("KingdomWorld: the weight of '{}' in the gear of robbers is not a number", line);
                    continue;
                }
                text = text.substring(0, eq).strip();
            }
            List<Item> items = new ArrayList<>();
            if (text.startsWith("#")) {
                ResourceLocation id = ResourceLocation.tryParse(text.substring(1));
                if (id != null) {
                    TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
                    var tags = ForgeRegistries.ITEMS.tags();
                    if (tags != null) {
                        tags.getTag(tag).forEach(items::add);
                    }
                }
            } else {
                ResourceLocation id = ResourceLocation.tryParse(text);
                Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
                if (item != null && item != Items.AIR) {
                    items.add(item);
                }
            }
            if (!items.isEmpty() && weight > 0) {
                result.add(new Entry(List.copyOf(items), weight));
            }
        }
        return result;
    }

    private static List<Entry> weapons() {
        List<? extends String> lines = RoadSettings.weapons();
        if (!lines.equals(weaponsKey)) {
            weapons = parse(lines);
            weaponsKey = List.copyOf(lines);
        }
        return weapons;
    }

    private static List<Entry> armor() {
        List<? extends String> lines = RoadSettings.armor();
        if (!lines.equals(armorKey)) {
            armor = parse(lines);
            armorKey = List.copyOf(lines);
        }
        return armor;
    }

    static ItemStack pick(List<Entry> table, RandomSource random) {
        int total = 0;
        for (Entry entry : table) {
            total += entry.weight();
        }
        if (total <= 0) {
            return ItemStack.EMPTY;
        }
        int roll = random.nextInt(total);
        for (Entry entry : table) {
            roll -= entry.weight();
            if (roll < 0) {
                return new ItemStack(entry.items().get(random.nextInt(entry.items().size())));
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * How good a weapon is: its attack damage, or a bow's fixed worth.
     */
    static double score(ItemStack stack) {
        if (stack.getItem() instanceof BowItem) {
            return 6.0;
        }
        double damage = 0;
        for (AttributeModifier modifier : stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE)) {
            damage += modifier.getAmount();
        }
        return damage;
    }

    /**
     * Arms a robber.
     */
    public static void equip(BanditEntity bandit, RandomSource random) {
        boolean leader = bandit.isLeader();
        ItemStack weapon = pick(weapons(), random);
        for (int i = 0; leader && i < 3; i++) {
            ItemStack other = pick(weapons(), random);
            if (score(other) > score(weapon)) {
                weapon = other;
            }
        }
        if (!weapon.isEmpty()) {
            bandit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        }
        List<Entry> table = armor();
        for (int i = 0; i < 6 && !table.isEmpty(); i++) {
            if (!leader && random.nextFloat() < 0.5f) {
                continue;
            }
            ItemStack piece = pick(table, random);
            if (piece.getItem() instanceof ArmorItem armorItem) {
                EquipmentSlot slot = armorItem.getEquipmentSlot();
                if (bandit.getItemBySlot(slot).isEmpty()) {
                    bandit.setItemSlot(slot, piece);
                }
            }
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            bandit.setDropChance(slot, 0.0f);
        }
    }
}
