package io.github.sekerator.kingdomworld.core.law;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Damaged property and whose goods a theft took: fines, how acts make one crime, and the texts a record keeps of a victim.
 */
class PropertyCrimesTest {
    private final LawRules rules = new LawRules.Builder().build();
    private final UUID kingdom = UUID.randomUUID();
    private final UUID player = UUID.randomUUID();

    @Test
    void theFineForDamagedPropertyFollowsTheWorthAndTheCourt() {
        LawState law = new LawState();
        Wanted wanted = law.commit(kingdom, player, "Vandal", CrimeKind.VANDALISM, 40, "", 100, rules).wanted();
        assertEquals(80, wanted.fine(rules, 1000), "Twice the worth, as for a theft");
        assertEquals(160, wanted.fine(rules, 2000), "The multiplier of the kingdom counts");
        assertEquals(rules.vandalismReputation, law.reputation(kingdom, player));
        assertTrue(CrimeKind.VANDALISM.petty());
        assertEquals(Optional.of(CrimeKind.VANDALISM), CrimeKind.byId("vandalism"));
        LawRules.Builder builder = new LawRules.Builder();
        builder.vandalismFinePercent = 50;
        LawRules mild = builder.build();
        assertEquals(20, wanted.fine(mild, 1000));
        assertEquals(1, mild.vandalismFine(1), "A broken block costs a coin at least");
        assertEquals(0, mild.vandalismFine(0));
    }

    @Test
    void blocksBrokenOneAfterAnotherMakeOneCrime() {
        LawState law = new LawState();
        for (int i = 0; i < 100; i++) {
            law.commit(kingdom, player, "Vandal", CrimeKind.VANDALISM, 3, "", 100 + i, rules);
        }
        Wanted wanted = law.wanted(kingdom, player).orElseThrow();
        assertEquals(1, wanted.crimes().size(), "A hundred blocks are not a hundred crimes");
        assertEquals(300, wanted.crimes().get(0).worth());
        assertEquals(100, wanted.crimes().get(0).count());
        assertEquals(600, wanted.fine(rules, 1000));
        assertEquals(rules.vandalismReputation, law.reputation(kingdom, player), "One crime costs reputation once");
        // After a long quiet time the next block begins a new crime.
        law.commit(kingdom, player, "Vandal", CrimeKind.VANDALISM, 5, "", 200 + rules.mergeTicks + 1, rules);
        assertEquals(2, wanted.crimes().size());
        // A theft in between is a crime of its own and does not swallow the damage.
        law.commit(kingdom, player, "Vandal", CrimeKind.THEFT, 7, "", 200 + rules.mergeTicks + 2, rules);
        assertEquals(3, wanted.crimes().size());
        assertEquals(7, wanted.stolen(), "Broken blocks are not stolen goods");
    }

    @Test
    void aTheftFromTheTreasuryCostsMoreThanACommonOne() {
        LawState law = new LawState();
        Wanted common = law.commit(kingdom, player, "Thief", CrimeKind.THEFT, 100, "", 100, rules).wanted();
        assertEquals(200, common.fine(rules, 1000));
        UUID other = UUID.randomUUID();
        Wanted treasury = law.commit(kingdom, other, "Thief", CrimeKind.THEFT, 100, Victims.TREASURY, 100, rules).wanted();
        assertEquals(300, treasury.fine(rules, 1000), "One and a half times the common fine");
        assertEquals(100, treasury.stolen());
        LawRules.Builder builder = new LawRules.Builder();
        builder.treasuryFinePercent = 400;
        assertEquals(800, treasury.fine(builder.build(), 1000));
        builder.treasuryFinePercent = 10;
        assertEquals(200, builder.build().treasuryFine(100), "The treasury is never cheaper to rob than anybody else");
    }

    @Test
    void theftsFromDifferentVictimsAreDifferentCrimes() {
        LawState law = new LawState();
        String radomir = Victims.citizen(1234, false);
        String milena = Victims.citizen(-77, true);
        law.commit(kingdom, player, "Thief", CrimeKind.THEFT, 10, radomir, 100, rules);
        law.commit(kingdom, player, "Thief", CrimeKind.THEFT, 5, radomir, 110, rules);
        law.commit(kingdom, player, "Thief", CrimeKind.THEFT, 20, milena, 120, rules);
        law.commit(kingdom, player, "Thief", CrimeKind.THEFT, 40, Victims.TREASURY, 130, rules);
        law.commit(kingdom, player, "Thief", CrimeKind.THEFT, 1, "", 140, rules);
        Wanted wanted = law.wanted(kingdom, player).orElseThrow();
        assertEquals(4, wanted.crimes().size());
        assertEquals(15, wanted.crimes().get(0).worth());
        assertEquals(radomir, wanted.crimes().get(0).victim());
        assertEquals(76, wanted.stolen());
        // 2 x (15 + 20 + 1) for the common thefts, 2 x 40 x 1.5 for the treasury.
        assertEquals(72 + 120, wanted.fine(rules, 1000));
    }

    @Test
    void aRecordNamesACitizenByTheSeedOfItsName() {
        String text = Victims.citizen(-42, true);
        Victims.CitizenName name = Victims.citizen(text).orElseThrow();
        assertEquals(-42, name.nameSeed());
        assertTrue(name.female());
        assertFalse(Victims.citizen(Victims.citizen(7, false)).orElseThrow().female());
        assertTrue(Victims.citizen("Steve").isEmpty());
        assertTrue(Victims.citizen("").isEmpty());
        assertTrue(Victims.citizen("@citizen:x:m").isEmpty());
        assertTrue(Victims.citizen(Victims.TREASURY).isEmpty());
        assertTrue(Victims.isTreasury(Victims.TREASURY));
        assertFalse(Victims.isTreasury(""));
        assertTrue(Victims.isName("Steve"));
        assertFalse(Victims.isName(text));
        assertFalse(Victims.isName(""));
        assertTrue(text.length() <= Crime.MAX_VICTIM);
    }

    @Test
    void aRightToBuildExcusesDamageAsARightToContainersExcusesTaking() {
        LawPolicy policy = new LawPolicy();
        LawPolicy.Offender builder = new LawPolicy.Offender(player, Optional.empty(), true, false);
        LawPolicy.Offender stranger = new LawPolicy.Offender(player, Optional.empty(), false, false);
        assertFalse(policy.isCrime(kingdom, builder, CrimeKind.VANDALISM));
        assertTrue(policy.isCrime(kingdom, stranger, CrimeKind.VANDALISM));
        assertTrue(policy.isCrime(kingdom, builder, CrimeKind.ASSAULT), "A right to build excuses no blows");
    }
}
