package io.github.sekerator.kingdomworld.core.army;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.ToLongFunction;

/**
 * The gear a kingdom of the world issues to a soldier of a branch and a post: armor of a material with Protection, a sword
 * with Sharpness (a bow with Power for an archer) and a shield. The numbers of the materials are those of the game, so that
 * the strength of a soldier in its kit can be reckoned without the game.
 *
 * @param armor      the material of helmet, leggings and boots
 * @param chest      the material of the chestplate
 * @param protection level of Protection on every piece of armor, 0 for none
 * @param sword      the material of the sword
 * @param sharpness  level of Sharpness on the sword, 0 for none
 * @param power      level of Power on the bow of an archer, 0 for none
 * @param shield     whether a warrior carries a shield
 */
public record Kit(Material armor, Material chest, int protection, Material sword, int sharpness, int power, boolean shield) {
    /** Slots of a kit, by the names the game gives them. */
    public static final String HEAD = "head";
    public static final String CHEST = "chest";
    public static final String LEGS = "legs";
    public static final String FEET = "feet";
    public static final String MAINHAND = "mainhand";
    public static final String OFFHAND = "offhand";

    /**
     * A material of armor and swords, with the numbers of the game: armor points of helmet, chestplate, leggings and boots,
     * toughness and knockback resistance of each piece, and what a sword adds to the damage of a hand.
     */
    public enum Material {
        IRON(2, 6, 5, 2, 0.0, 0.0, 5.0),
        DIAMOND(3, 8, 6, 3, 2.0, 0.0, 6.0),
        NETHERITE(3, 8, 6, 3, 3.0, 0.1, 7.0);

        private final int head;
        private final int chest;
        private final int legs;
        private final int feet;
        private final double toughness;
        private final double knockback;
        private final double sword;

        Material(int head, int chest, int legs, int feet, double toughness, double knockback, double sword) {
            this.head = head;
            this.chest = chest;
            this.legs = legs;
            this.feet = feet;
            this.toughness = toughness;
            this.knockback = knockback;
            this.sword = sword;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public int chestArmor() {
            return chest;
        }

        public int otherArmor() {
            return head + legs + feet;
        }

        /** Toughness one piece of this material gives. */
        public double toughness() {
            return toughness;
        }

        /** Knockback resistance one piece of this material gives. */
        public double knockback() {
            return knockback;
        }

        /** What a sword of this material adds to the damage of a hand. */
        public double swordDamage() {
            return sword;
        }

        public static Optional<Material> byId(String id) {
            for (Material material : values()) {
                if (material.id().equals(id)) {
                    return Optional.of(material);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * One piece of a kit: an item in a slot, with one enchantment or none.
     *
     * @param enchantment the id of the enchantment, empty for none
     */
    public record Piece(String slot, String item, String enchantment, int level) {
        public boolean enchanted() {
            return !enchantment.isEmpty() && level > 0;
        }
    }

    public Kit {
        if (armor == null || chest == null || sword == null || protection < 0 || protection > 10 || sharpness < 0 || sharpness > 10
                || power < 0 || power > 10) {
            throw new IllegalArgumentException("Kit out of range");
        }
    }

    /** Armor points of the four pieces. */
    public int armorPoints() {
        return armor.otherArmor() + chest.chestArmor();
    }

    public double toughness() {
        return 3 * armor.toughness() + chest.toughness();
    }

    public double knockback() {
        return 3 * armor.knockback() + chest.knockback();
    }

    /** What Protection on the four pieces counts for against a blow: its level on each. */
    public int protectionFactor() {
        return 4 * protection;
    }

    /**
     * The damage of a blow with the sword of the kit: the hand, the sword and its Sharpness.
     */
    public double swordDamage() {
        return 1.0 + sword.swordDamage() + (sharpness > 0 ? 0.5 * sharpness + 0.5 : 0.0);
    }

    /**
     * The pieces of the kit for a warrior or an archer.
     */
    public List<Piece> pieces(boolean archer) {
        List<Piece> pieces = new ArrayList<>();
        String shielding = protection > 0 ? "minecraft:protection" : "";
        pieces.add(new Piece(HEAD, "minecraft:" + armor.id() + "_helmet", shielding, protection));
        pieces.add(new Piece(CHEST, "minecraft:" + chest.id() + "_chestplate", shielding, protection));
        pieces.add(new Piece(LEGS, "minecraft:" + armor.id() + "_leggings", shielding, protection));
        pieces.add(new Piece(FEET, "minecraft:" + armor.id() + "_boots", shielding, protection));
        if (archer) {
            pieces.add(new Piece(MAINHAND, "minecraft:bow", power > 0 ? "minecraft:power" : "", power));
        } else {
            pieces.add(new Piece(MAINHAND, "minecraft:" + sword.id() + "_sword", sharpness > 0 ? "minecraft:sharpness" : "", sharpness));
            if (shield) {
                pieces.add(new Piece(OFFHAND, "minecraft:shield", "", 0));
            }
        }
        return pieces;
    }

    /**
     * What a piece costs a treasury: the worth of the item, more by a share for every level of its enchantment, and never
     * nothing.
     *
     * @param worth          the worth of an item by its id
     * @param enchantPercent percent of the worth every level of an enchantment adds
     */
    public static long price(Piece piece, ToLongFunction<String> worth, int enchantPercent) {
        long base = Math.max(1L, worth.applyAsLong(piece.item()));
        long premium = piece.enchanted() ? (long) Math.max(0, enchantPercent) * piece.level() : 0L;
        double price = base * (100.0 + premium) / 100.0;
        return price >= Long.MAX_VALUE / 4 ? Long.MAX_VALUE / 4 : Math.max(1L, (long) Math.ceil(price));
    }

    /**
     * What a whole kit costs.
     */
    public long price(boolean archer, ToLongFunction<String> worth, int enchantPercent) {
        long sum = 0;
        for (Piece piece : pieces(archer)) {
            sum = Math.min(Long.MAX_VALUE / 4, sum + price(piece, worth, enchantPercent));
        }
        return sum;
    }

    /**
     * Reads a kit from the config: {@code armor,chest,protection,sword,sharpness,power,shield}, for example
     * {@code iron,diamond,2,iron,3,2,true}.
     */
    public static Optional<Kit> parse(String text) {
        String[] parts = text == null ? new String[0] : text.trim().split("\\s*,\\s*");
        if (parts.length != 7) {
            return Optional.empty();
        }
        try {
            Optional<Material> armor = Material.byId(parts[0]);
            Optional<Material> chest = Material.byId(parts[1]);
            Optional<Material> sword = Material.byId(parts[3]);
            if (armor.isEmpty() || chest.isEmpty() || sword.isEmpty() || !(parts[6].equals("true") || parts[6].equals("false"))) {
                return Optional.empty();
            }
            return Optional.of(new Kit(armor.get(), chest.get(), Integer.parseInt(parts[2]), sword.get(), Integer.parseInt(parts[4]),
                    Integer.parseInt(parts[5]), Boolean.parseBoolean(parts[6])));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** The kit as the config writes it. */
    public String text() {
        return armor.id() + "," + chest.id() + "," + protection + "," + sword.id() + "," + sharpness + "," + power + "," + shield;
    }
}
