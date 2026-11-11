package io.github.sekerator.kingdomworld.core.army;

import io.github.sekerator.kingdomworld.core.combat.Duel;

/**
 * How strong the city guard, the royal army and their officers are, what a kingdom of the world issues them, when the
 * army comes to the help of the guard, and how the fallen are replaced.
 *
 * <p>A soldier has the strength of a fighter of its branch. An officer has the strength of its post only while it wears
 * armor of the post: diamond or better for a vice-captain, netherite or better for a captain, every piece enchanted.
 */
public final class ForceRules {
    /** Health of a citizen who is no soldier, the measure of the strength of soldiers. */
    public static final double CITIZEN_HEALTH = 20.0;
    public static final ForceRules DEFAULT = new Builder().build();

    /**
     * The body of a soldier of a branch and a post.
     *
     * @param health    its full health
     * @param damage    what it adds to the damage of every blow and arrow
     * @param armor     armor points it has besides what it wears
     * @param knockback knockback resistance it has besides what it wears, from 0 to 1
     */
    public record Stats(double health, double damage, double armor, double knockback) {
        public Stats {
            if (!(health >= 1 && health <= 1024) || !(damage >= 0 && damage <= 1024) || !(armor >= 0 && armor <= 30)
                    || !(knockback >= 0 && knockback <= 1)) {
                throw new IllegalArgumentException("Stats of a soldier out of range");
            }
        }
    }

    private final Stats[] stats;
    private final Kit[] kits;
    /** What multiplies the health and the damage of the guard, of the army and, on top of that, of officers. */
    public final double guardHealth;
    public final double guardDamage;
    public final double armyHealth;
    public final double armyDamage;
    public final double officerHealth;
    public final double officerDamage;
    /** Share of the blows from the front a soldier wards off with its shield. */
    public final double blockChance;
    /** Whether the armor of an officer must be enchanted to count for its post. */
    public final boolean enchantedArmor;
    /** Soldiers out of the fight, or ticks of fighting, that call the vice-captain of the guard. */
    public final int viceLosses;
    public final long viceTicks;
    /** The same for the captain of the guard, who also comes when the vice-captain is out. */
    public final int captainLosses;
    public final long captainTicks;
    /** The same for the army, which also comes when the captain of the guard is out. */
    public final int armyLosses;
    public final long armyTicks;
    /** Soldiers out of the fight that call the captain of the army with the rest; it also comes when its vice-captain is out. */
    public final int allLosses;
    /** Share of the fighters of the army that come out first, with its vice-captain. */
    public final double armyShare;
    /** Share of its health below which an officer counts as out of the fight. */
    public final double officerOut;
    /** Fallen soldiers a kingdom of the world replaces a day. */
    public final int replacePerDay;
    /** Days after which the best fighter of a branch takes the post of a fallen officer. */
    public final int officerReplaceDays;
    /** Soldiers a branch needs to have a vice-captain, and a captain. */
    public final int viceMinBranch;
    public final int captainMinBranch;
    /** The wage of an officer, in thousandths of the wage of a soldier. */
    public final int viceWagePermille;
    public final int captainWagePermille;
    /** Percent of its worth every level of an enchantment adds to the price of an issued piece. */
    public final int enchantPercent;

    private ForceRules(Builder b) {
        stats = b.stats.clone();
        kits = b.kits.clone();
        for (int i = 0; i < stats.length; i++) {
            if (stats[i] == null || kits[i] == null) {
                throw new IllegalArgumentException("Force rules lack a post");
            }
        }
        guardHealth = factor(b.guardHealth);
        guardDamage = factor(b.guardDamage);
        armyHealth = factor(b.armyHealth);
        armyDamage = factor(b.armyDamage);
        officerHealth = factor(b.officerHealth);
        officerDamage = factor(b.officerDamage);
        if (!(b.blockChance >= 0 && b.blockChance <= 0.9) || b.viceLosses < 1 || b.captainLosses < 1 || b.armyLosses < 1 || b.allLosses < 1
                || b.viceTicks < 1 || b.captainTicks < 1 || b.armyTicks < 1 || !(b.armyShare >= 0 && b.armyShare <= 1)
                || !(b.officerOut >= 0 && b.officerOut <= 1) || b.replacePerDay < 0 || b.officerReplaceDays < 0 || b.viceMinBranch < 1
                || b.captainMinBranch < 1 || b.viceWagePermille < 1_000 || b.captainWagePermille < 1_000 || b.enchantPercent < 0) {
            throw new IllegalArgumentException("Force rules out of range");
        }
        blockChance = b.blockChance;
        enchantedArmor = b.enchantedArmor;
        viceLosses = b.viceLosses;
        viceTicks = b.viceTicks;
        captainLosses = b.captainLosses;
        captainTicks = b.captainTicks;
        armyLosses = b.armyLosses;
        armyTicks = b.armyTicks;
        allLosses = b.allLosses;
        armyShare = b.armyShare;
        officerOut = b.officerOut;
        replacePerDay = b.replacePerDay;
        officerReplaceDays = b.officerReplaceDays;
        viceMinBranch = b.viceMinBranch;
        captainMinBranch = b.captainMinBranch;
        viceWagePermille = b.viceWagePermille;
        captainWagePermille = b.captainWagePermille;
        enchantPercent = b.enchantPercent;
    }

    private static double factor(double value) {
        if (!(value >= 0.1 && value <= 100.0)) {
            throw new IllegalArgumentException("Multiplier of soldiers out of range: " + value);
        }
        return value;
    }

    static int index(Branch branch, Post post) {
        return branch.ordinal() * Post.values().length + post.ordinal();
    }

    public Stats stats(Branch branch, Post post) {
        return stats[index(branch, post)];
    }

    public Kit kit(Branch branch, Post post) {
        return kits[index(branch, post)];
    }

    private double healthFactor(Branch branch, Post post) {
        return (branch == Branch.GUARD ? guardHealth : armyHealth) * (post.officer() ? officerHealth : 1.0);
    }

    /**
     * What multiplies the damage of a soldier of a branch and a post.
     */
    public double damageFactor(Branch branch, Post post) {
        return (branch == Branch.GUARD ? guardDamage : armyDamage) * (post.officer() ? officerDamage : 1.0);
    }

    /**
     * The full health of a soldier of a branch and a post.
     */
    public double health(Branch branch, Post post) {
        return Math.max(1.0, Math.min(1024.0, stats(branch, post).health() * healthFactor(branch, post)));
    }

    /**
     * How much stronger than a plain citizen with the same gear a soldier is in the model of battles far from players: more
     * health lets it stand longer, more damage lets it end the fight sooner.
     */
    public double might(Branch branch, Post post) {
        return Math.sqrt(health(branch, post) / CITIZEN_HEALTH) * (1.0 + stats(branch, post).damage() / 8.0) * damageFactor(branch, post);
    }

    /**
     * The wage of a soldier with a post, in thousandths of the wage of its profession.
     */
    public int wagePermille(Post post) {
        return switch (post) {
            case FIGHTER -> 1_000;
            case VICE_CAPTAIN -> viceWagePermille;
            case CAPTAIN -> captainWagePermille;
        };
    }

    /**
     * Soldiers a branch needs for a post to be filled.
     */
    public int minBranch(Post post) {
        return post == Post.CAPTAIN ? captainMinBranch : post == Post.VICE_CAPTAIN ? viceMinBranch : 1;
    }

    /**
     * Armor toughness every piece an officer wears needs for its post to count: that of diamond for a vice-captain, of
     * netherite for a captain; 0 for a fighter.
     */
    public static double pieceToughness(Post post) {
        return post == Post.CAPTAIN ? Kit.Material.NETHERITE.toughness() : post == Post.VICE_CAPTAIN ? Kit.Material.DIAMOND.toughness() : 0.0;
    }

    /**
     * A warrior of a branch and a post in its issued kit, as a fight of one against one sees it.
     */
    public Duel.Combatant combatant(Branch branch, Post post) {
        Stats body = stats(branch, post);
        Kit kit = kit(branch, post);
        return new Duel.Combatant(health(branch, post), (kit.swordDamage() + body.damage()) * damageFactor(branch, post),
                Math.min(30.0, kit.armorPoints() + body.armor()), kit.toughness(), kit.protectionFactor(), kit.shield() ? blockChance : 0.0);
    }

    /**
     * Rules built step by step, from the defaults.
     */
    public static final class Builder {
        private final Stats[] stats = new Stats[Branch.values().length * Post.values().length];
        private final Kit[] kits = new Kit[stats.length];
        public double guardHealth = 1.0;
        public double guardDamage = 1.0;
        public double armyHealth = 1.0;
        public double armyDamage = 1.0;
        public double officerHealth = 1.0;
        public double officerDamage = 1.0;
        public double blockChance = 0.1;
        public boolean enchantedArmor = true;
        public int viceLosses = 2;
        public long viceTicks = 400;
        public int captainLosses = 4;
        public long captainTicks = 800;
        public int armyLosses = 6;
        public long armyTicks = 1_200;
        public int allLosses = 10;
        public double armyShare = 0.5;
        public double officerOut = 0.4;
        public int replacePerDay = 2;
        public int officerReplaceDays = 3;
        public int viceMinBranch = 3;
        public int captainMinBranch = 5;
        public int viceWagePermille = 3_000;
        public int captainWagePermille = 5_000;
        public int enchantPercent = 20;

        public Builder() {
            set(Branch.GUARD, Post.FIGHTER, new Stats(30, 0, 0, 0.0));
            set(Branch.GUARD, Post.VICE_CAPTAIN, new Stats(80, 1, 0, 0.5));
            set(Branch.GUARD, Post.CAPTAIN, new Stats(120, 0, 0, 0.6));
            set(Branch.ARMY, Post.FIGHTER, new Stats(40, 1, 2, 0.2));
            set(Branch.ARMY, Post.VICE_CAPTAIN, new Stats(110, 1, 0, 0.7));
            set(Branch.ARMY, Post.CAPTAIN, new Stats(160, 2, 0, 0.6));
            set(Branch.GUARD, Post.FIGHTER, new Kit(Kit.Material.IRON, Kit.Material.IRON, 1, Kit.Material.IRON, 1, 0, true));
            set(Branch.GUARD, Post.VICE_CAPTAIN, new Kit(Kit.Material.DIAMOND, Kit.Material.DIAMOND, 3, Kit.Material.DIAMOND, 3, 1, true));
            set(Branch.GUARD, Post.CAPTAIN, new Kit(Kit.Material.NETHERITE, Kit.Material.NETHERITE, 3, Kit.Material.NETHERITE, 4, 2, true));
            set(Branch.ARMY, Post.FIGHTER, new Kit(Kit.Material.IRON, Kit.Material.DIAMOND, 2, Kit.Material.IRON, 3, 2, true));
            set(Branch.ARMY, Post.VICE_CAPTAIN, new Kit(Kit.Material.DIAMOND, Kit.Material.DIAMOND, 4, Kit.Material.DIAMOND, 5, 3, true));
            set(Branch.ARMY, Post.CAPTAIN, new Kit(Kit.Material.NETHERITE, Kit.Material.NETHERITE, 4, Kit.Material.NETHERITE, 5, 4, true));
        }

        public Builder set(Branch branch, Post post, Stats value) {
            stats[index(branch, post)] = value;
            return this;
        }

        public Builder set(Branch branch, Post post, Kit value) {
            kits[index(branch, post)] = value;
            return this;
        }

        public Stats stats(Branch branch, Post post) {
            return stats[index(branch, post)];
        }

        public Kit kit(Branch branch, Post post) {
            return kits[index(branch, post)];
        }

        public ForceRules build() {
            return new ForceRules(this);
        }
    }
}
