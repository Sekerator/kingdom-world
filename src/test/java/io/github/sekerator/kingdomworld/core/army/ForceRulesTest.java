package io.github.sekerator.kingdomworld.core.army;

import io.github.sekerator.kingdomworld.core.combat.CombatPower;
import io.github.sekerator.kingdomworld.core.combat.Duel;
import io.github.sekerator.kingdomworld.core.combat.Fighter;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How strong the guard, the army and their officers are against a player, by the game's own formulas of armor and
 * Protection: who must lose to an officer and who may win.
 */
class ForceRulesTest {
    private static final ForceRules RULES = ForceRules.DEFAULT;

    // Players, as the model of a fight of one against one sees them. Enchanted gear carries Protection IV on every piece
    // and Sharpness V on the sword.
    private static final Duel.Combatant IRON = Duel.player(6.0, 15, 0, 0);
    private static final Duel.Combatant IRON_ENCHANTED = Duel.player(9.0, 15, 0, 16);
    private static final Duel.Combatant DIAMOND = Duel.player(7.0, 20, 8, 0);
    private static final Duel.Combatant DIAMOND_ENCHANTED = Duel.player(10.0, 20, 8, 16);
    private static final Duel.Combatant NETHERITE = Duel.player(8.0, 20, 12, 0);
    private static final Duel.Combatant NETHERITE_ENCHANTED = Duel.player(11.0, 20, 12, 16);

    private static boolean wins(Duel.Combatant player, Branch branch, Post post) {
        return Duel.fight(player, RULES.combatant(branch, post)).playerWins();
    }

    @Test
    void theFormulasAreThoseOfTheGame() {
        // Ten damage against full diamond armor: 20 armor, 8 toughness.
        assertEquals(3.0, Duel.afterArmor(10.0, 20, 8), 1e-9);
        // Six damage against full iron armor: the armor is pierced down to 12 points.
        assertEquals(6.0 * (1 - 12.0 / 25.0), Duel.afterArmor(6.0, 15, 0), 1e-9);
        // Armor never counts for less than a fifth of its points, nor for more than twenty.
        assertEquals(100.0 * (1 - 3.0 / 25.0), Duel.afterArmor(100.0, 15, 0), 1e-9);
        assertEquals(1.0 * (1 - 20.0 / 25.0), Duel.afterArmor(1.0, 30, 20), 1e-9);
        // Protection IV on four pieces takes 64 percent, and no more than 80 percent however much there is.
        assertEquals(0.36, Duel.afterProtection(1.0, 16), 1e-9);
        assertEquals(0.2, Duel.afterProtection(1.0, 40), 1e-9);
    }

    @Test
    void aPlayerInPlainIronLosesToTheViceCaptainOfTheGuard() {
        assertFalse(wins(IRON, Branch.GUARD, Post.VICE_CAPTAIN));
        // Enchanted iron and plain diamond are not enough either: it takes full enchanted diamond at the least.
        assertFalse(wins(IRON_ENCHANTED, Branch.GUARD, Post.VICE_CAPTAIN));
        assertFalse(wins(DIAMOND, Branch.GUARD, Post.VICE_CAPTAIN));
    }

    @Test
    void aPlayerInEnchantedDiamondMayBeatTheViceCaptainButLosesToTheCaptain() {
        assertTrue(wins(DIAMOND_ENCHANTED, Branch.GUARD, Post.VICE_CAPTAIN));
        assertFalse(wins(DIAMOND_ENCHANTED, Branch.GUARD, Post.CAPTAIN));
        assertFalse(wins(NETHERITE, Branch.GUARD, Post.CAPTAIN), "plain netherite is not enough for the captain");
    }

    @Test
    void aPlayerInEnchantedNetheriteMayBeatTheCaptainOfTheGuard() {
        assertTrue(wins(NETHERITE_ENCHANTED, Branch.GUARD, Post.CAPTAIN));
        assertTrue(wins(NETHERITE_ENCHANTED, Branch.GUARD, Post.VICE_CAPTAIN));
    }

    @Test
    void theOfficersOfTheArmyAreStrongerThanThoseOfTheGuard() {
        for (Post post : new Post[]{Post.VICE_CAPTAIN, Post.CAPTAIN}) {
            Duel.Combatant guard = RULES.combatant(Branch.GUARD, post);
            Duel.Combatant army = RULES.combatant(Branch.ARMY, post);
            assertTrue(army.health() > guard.health(), post + " health");
            assertTrue(army.damage() > guard.damage(), post + " damage");
            assertTrue(army.protection() >= guard.protection(), post + " protection");
            // The same player needs longer for the officer of the army and falls sooner.
            Duel.Result againstGuard = Duel.fight(NETHERITE_ENCHANTED, guard);
            Duel.Result againstArmy = Duel.fight(NETHERITE_ENCHANTED, army);
            assertTrue(againstArmy.killSeconds() > againstGuard.killSeconds(), post + " stands longer");
            assertTrue(againstArmy.dieSeconds() < againstGuard.dieSeconds(), post + " kills sooner");
        }
        // The vice-captain of the army strikes no weaker than the captain of the guard, and no gear of the game is enough
        // to beat an officer of the army alone.
        assertTrue(RULES.combatant(Branch.ARMY, Post.VICE_CAPTAIN).damage() >= RULES.combatant(Branch.GUARD, Post.CAPTAIN).damage());
        assertFalse(wins(NETHERITE_ENCHANTED, Branch.ARMY, Post.VICE_CAPTAIN));
        assertFalse(wins(NETHERITE_ENCHANTED, Branch.ARMY, Post.CAPTAIN));
    }

    @Test
    void fightersAreStrongerThanCitizensAndTheArmyThanTheGuard() {
        assertTrue(RULES.health(Branch.GUARD, Post.FIGHTER) > ForceRules.CITIZEN_HEALTH);
        assertTrue(RULES.health(Branch.ARMY, Post.FIGHTER) > RULES.health(Branch.GUARD, Post.FIGHTER));
        assertTrue(RULES.combatant(Branch.ARMY, Post.FIGHTER).damage() > RULES.combatant(Branch.GUARD, Post.FIGHTER).damage());
        // A lone guard is no match for a player in iron, a lone fighter of the army is.
        assertTrue(wins(IRON, Branch.GUARD, Post.FIGHTER));
        assertFalse(wins(IRON, Branch.ARMY, Post.FIGHTER));
        for (Branch branch : Branch.values()) {
            assertTrue(RULES.health(branch, Post.VICE_CAPTAIN) > RULES.health(branch, Post.FIGHTER));
            assertTrue(RULES.health(branch, Post.CAPTAIN) > RULES.health(branch, Post.VICE_CAPTAIN));
            assertTrue(RULES.stats(branch, Post.VICE_CAPTAIN).knockback() >= 0.5, "an officer is not thrown about by every blow");
        }
    }

    @Test
    void theMultipliersOfTheConfigScaleHealthAndDamage() {
        ForceRules.Builder builder = new ForceRules.Builder();
        builder.guardHealth = 2.0;
        builder.officerHealth = 1.5;
        builder.armyDamage = 3.0;
        ForceRules scaled = builder.build();
        assertEquals(60.0, scaled.health(Branch.GUARD, Post.FIGHTER), 1e-9);
        assertEquals(240.0, scaled.health(Branch.GUARD, Post.VICE_CAPTAIN), 1e-9);
        assertEquals(RULES.health(Branch.ARMY, Post.FIGHTER), scaled.health(Branch.ARMY, Post.FIGHTER), 1e-9);
        assertEquals(3.0 * RULES.combatant(Branch.ARMY, Post.FIGHTER).damage(), scaled.combatant(Branch.ARMY, Post.FIGHTER).damage(), 1e-9);
    }

    @Test
    void theModelOfBattlesCountsBranchAndPost() {
        Fighter plain = new Fighter(UUID.randomUUID(), 1, 6.0, 1.6, 15.0f, 0.0f, 0, 1.0, false);
        double citizen = CombatPower.of(plain);
        double guard = CombatPower.of(plain.withMight(RULES.might(Branch.GUARD, Post.FIGHTER)));
        double army = CombatPower.of(plain.withMight(RULES.might(Branch.ARMY, Post.FIGHTER)));
        double captain = CombatPower.of(plain.withMight(RULES.might(Branch.GUARD, Post.CAPTAIN)));
        double armyCaptain = CombatPower.of(plain.withMight(RULES.might(Branch.ARMY, Post.CAPTAIN)));
        assertTrue(citizen < guard && guard < army && army < captain && captain < armyCaptain);
        assertEquals(citizen * Math.sqrt(30.0 / 20.0), guard, 1e-9);
    }

    @Test
    void anIssuedKitCostsTheWorthOfItsPiecesAndMoreForEveryLevelOfEnchantment() {
        Map<String, Long> worth = Map.of("minecraft:iron_helmet", 50L, "minecraft:iron_chestplate", 80L, "minecraft:iron_leggings", 70L,
                "minecraft:iron_boots", 40L, "minecraft:iron_sword", 20L, "minecraft:shield", 10L, "minecraft:bow", 6L);
        Kit guard = RULES.kit(Branch.GUARD, Post.FIGHTER);
        // Protection I and Sharpness I: every enchanted piece costs a fifth more, the shield its plain worth.
        assertEquals(60 + 96 + 84 + 48 + 24 + 10, guard.price(false, id -> worth.getOrDefault(id, 0L), 20));
        // An archer gets a bow and no shield; the bow of a guard is plain.
        assertEquals(60 + 96 + 84 + 48 + 6, guard.price(true, id -> worth.getOrDefault(id, 0L), 20));
        // A piece nobody knows the worth of still costs a coin, an enchanted one two: nothing is issued for nothing.
        assertEquals(5 * 2 + 1, guard.price(false, id -> 0L, 20));
        Kit.Piece sword = RULES.kit(Branch.ARMY, Post.CAPTAIN).pieces(false).get(4);
        assertEquals("minecraft:netherite_sword", sword.item());
        assertEquals(5, sword.level());
        assertEquals(2_000, Kit.price(sword, id -> 1_000L, 20));
        assertEquals(1_000, Kit.price(sword, id -> 1_000L, 0));
    }

    @Test
    void kitsAreReadFromTheConfig() {
        for (Branch branch : Branch.values()) {
            for (Post post : Post.values()) {
                Kit kit = RULES.kit(branch, post);
                assertEquals(Optional.of(kit), Kit.parse(kit.text()));
            }
        }
        assertEquals(Optional.of(new Kit(Kit.Material.IRON, Kit.Material.DIAMOND, 2, Kit.Material.IRON, 3, 2, true)),
                Kit.parse(" iron, diamond ,2,iron,3,2,true"));
        assertTrue(Kit.parse("gold,iron,1,iron,1,0,true").isEmpty());
        assertTrue(Kit.parse("iron,iron,1,iron,1,0").isEmpty());
        assertTrue(Kit.parse("iron,iron,99,iron,1,0,true").isEmpty());
        // The numbers of the materials are those of the game.
        Kit netherite = RULES.kit(Branch.ARMY, Post.CAPTAIN);
        assertEquals(20, netherite.armorPoints());
        assertEquals(12.0, netherite.toughness(), 1e-9);
        assertEquals(0.4, netherite.knockback(), 1e-9);
        assertEquals(1 + 7 + 3.0, netherite.swordDamage(), 1e-9);
        assertEquals(15, RULES.kit(Branch.GUARD, Post.FIGHTER).armorPoints());
    }
}
