package io.github.sekerator.kingdomworld.core.siege;

import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyRules;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyState;
import io.github.sekerator.kingdomworld.core.diplomacy.PeaceTerms;
import io.github.sekerator.kingdomworld.core.diplomacy.War;
import io.github.sekerator.kingdomworld.core.diplomacy.WarGoal;
import io.github.sekerator.kingdomworld.core.economy.Plurals;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiegeCoreTest {
    private static final DiplomacyRules RULES = DiplomacyRules.DEFAULT;
    private static final SiegeRules SIEGE = SiegeRules.DEFAULT;

    private final UUID a = new UUID(0, 1);
    private final UUID b = new UUID(0, 2);
    private final UUID c = new UUID(0, 3);

    private War war(DiplomacyState state, UUID attacker, UUID defender, WarGoal goal, boolean start) {
        War war = state.declare(attacker, defender, goal, 0, 0, RULES, false, true).war().orElseThrow();
        if (start) {
            assertTrue(state.startNow(war.id()).ok());
        }
        return war;
    }

    @Test
    void landIsOpenOnlyInAStartedWarAgainstAnUnshieldedEnemy() {
        DiplomacyState state = new DiplomacyState();
        assertEquals(WarLand.Access.PEACE, WarLand.access(state, a, b, k -> false), "no war, no open land");
        assertEquals(WarLand.Access.WILD, WarLand.access(state, a, null, k -> false));
        assertEquals(WarLand.Access.OWN, WarLand.access(state, a, a, k -> false));
        War war = war(state, a, b, WarGoal.CITY, false);
        assertEquals(WarLand.Access.PREPARING, WarLand.access(state, a, b, k -> false), "the land opens when the war starts");
        assertTrue(state.startNow(war.id()).ok());
        assertTrue(WarLand.open(state, a, b, k -> false));
        assertTrue(WarLand.open(state, b, a, k -> false), "the defenders may strike back the same way");
        assertEquals(WarLand.Access.SHIELDED, WarLand.access(state, a, b, b::equals), "the land of an absent kingdom stays shut");
        assertEquals(WarLand.Access.PEACE, WarLand.access(state, a, c, k -> false), "third kingdoms are not touched");
        assertEquals(WarLand.Access.PEACE, WarLand.access(state, c, b, k -> false));
    }

    @Test
    void gatesHoldByTheirMaterialAndFallFasterToRams() {
        long wood = SIEGE.secondsToBreak(GateMaterial.WOOD, 6, 1);
        long iron = SIEGE.secondsToBreak(GateMaterial.IRON, 6, 1);
        assertTrue(wood < iron, "planks give way before iron");
        assertTrue(iron >= 60 && iron <= 10 * 60, "a good siege breaks a portcullis in some minutes, not an hour: " + iron);
        assertTrue(SIEGE.secondsToBreak(GateMaterial.IRON, 6, 0) > iron, "a ram speeds the siege up");
        assertEquals(SIEGE.secondsToBreak(GateMaterial.WOOD, 6, 0), SIEGE.secondsToBreak(GateMaterial.WOOD, 40, 0),
                "only so many soldiers reach one gate");
        assertEquals(Long.MAX_VALUE, SIEGE.secondsToBreak(GateMaterial.WOOD, 0, 2), "rams need hands");

        Gate gate = new Gate(1, 5, a, GateMaterial.WOOD, 0, false, false);
        assertTrue(gate.breached());
        assertFalse(gate.setClosed(true), "a gate without leaves does not close");
        gate.hang(GateMaterial.WOOD, SIEGE);
        assertTrue(gate.setClosed(true));
        assertEquals(100, gate.percent(SIEGE));
        double hit = SIEGE.hitPerSecond(6, 1);
        long seconds = 0;
        while (!gate.strike(hit)) {
            seconds++;
            assertTrue(seconds < 10_000);
        }
        assertEquals(wood, seconds + 1);
        assertTrue(gate.breached());
        assertFalse(gate.closed(), "a breached gate stands open");

        Gate worn = new Gate(1, 9, a, GateMaterial.IRON, SIEGE.ironGateHp(), true, true);
        worn.wear(4, 6, SIEGE);
        assertEquals(67, worn.percent(SIEGE));
        worn.wear(2, 6, SIEGE);
        assertTrue(worn.breached(), "more than half the leaf blocks broken breach the gate");
    }

    @Test
    void holdsGrowWithoutDefendersStopWhenShieldedAndStartOverWhenDefendersCome() {
        SiegeRules rules = SIEGE.withTimes(100, 200, 300);
        Hold hold = new Hold(Hold.Kind.PLAZA, 7, a, b, "minecraft:overworld", 3, 0, false, 0);
        assertEquals(Hold.Change.PROGRESS, hold.step(true, false, false, 60, 60, rules));
        assertEquals(30, hold.percent(rules));
        assertEquals(Hold.Change.NONE, hold.step(true, false, true, 60, 120, rules), "a shielded enemy is not taken from");
        assertEquals(60, hold.progress());
        assertEquals(Hold.Change.INTERRUPTED, hold.step(true, true, false, 20, 140, rules), "defenders interrupt the hold");
        assertEquals(0, hold.progress());
        assertEquals(Hold.Change.NONE, hold.step(false, false, false, 20, 160, rules));
        hold.step(true, false, false, 150, 310, rules);
        assertEquals(Hold.Change.TAKEN, hold.step(true, false, false, 60, 370, rules));
        assertTrue(hold.taken());
        assertEquals(Hold.Change.NONE, hold.step(false, true, false, 1_000, 1_370, rules), "a taken city stays taken until the peace");

        Hold chunk = new Hold(Hold.Kind.CHUNK, 7, a, b, "minecraft:overworld", 42, 0, false, 0);
        assertEquals(Hold.Change.TAKEN, chunk.step(true, false, false, 100, 100, rules));
        assertEquals(Hold.Change.RETAKING, chunk.step(false, true, false, 40, 140, rules));
        assertEquals(Hold.Change.NONE, chunk.step(true, true, false, 40, 180, rules), "both sides there: nothing moves back");
        assertEquals(Hold.Change.FREED, chunk.step(false, true, false, 100, 280, rules), "defenders free a chunk by holding it");
        assertFalse(chunk.taken());
        assertTrue(chunk.idle());
    }

    @Test
    void peaceHandsOverOnlyWhatWasTaken() {
        SiegeRules rules = SIEGE.withTimes(100, 100, 100);
        SiegeState state = new SiegeState();
        for (int i = 0; i < 5; i++) {
            Hold hold = state.holdAt(Hold.Kind.CHUNK, 9, a, b, "d", 100 + i, 0);
            if (i != 2) {
                hold.step(true, false, false, 100, 10 * (5 - i), rules);
            }
        }
        state.holdAt(Hold.Kind.CHUNK, 8, a, b, "d", 500, 0).take(0, rules);
        List<Long> land = Conquest.landToCede(List.copyOf(state.holds()), a, b, 9, 3);
        assertEquals(List.of(104L, 103L, 101L), land, "the first taken go first, no more than the terms ask, only of this war");
        assertEquals(4, state.occupied(b).size() - 1);

        state.holdAt(Hold.Kind.PLAZA, 9, a, b, "d", 7, 0).take(50, rules);
        state.holdAt(Hold.Kind.PLAZA, 9, a, b, "d", 8, 0).take(20, rules);
        Map<Long, String> names = Map.of(7L, "Ost", 8L, "West");
        assertEquals(Optional.of(7L), Conquest.cityToCede(List.copyOf(state.holds()), a, b, 9, "ost", names::get), "the named city first");
        assertEquals(Optional.of(8L), Conquest.cityToCede(List.copyOf(state.holds()), a, b, 9, "?", names::get), "else the first taken");
        assertEquals(Optional.empty(), Conquest.cityToCede(List.copyOf(state.holds()), b, a, 9, "?", names::get));
        assertFalse(Conquest.throneTaken(List.copyOf(state.holds()), a, b, 9));
        state.holdAt(Hold.Kind.THRONE, 9, a, b, "d", 0, 0).take(70, rules);
        assertTrue(Conquest.throneTaken(List.copyOf(state.holds()), a, b, 9));

        DiplomacyState diplomacy = new DiplomacyState();
        War war = war(diplomacy, a, b, WarGoal.THRONE, true);
        assertTrue(PeaceTerms.forGoal(WarGoal.THRONE, a, true, 0, RULES).throne());
        assertTrue(PeaceTerms.forGoal(WarGoal.LAND, a, true, 0, RULES).landChunks() > 0);
        assertFalse(PeaceTerms.forGoal(WarGoal.CITY, a, true, 0, RULES).city().isEmpty());
        assertTrue(diplomacy.endWar(war.id(), War.Result.SURRENDER, PeaceTerms.forGoal(WarGoal.THRONE, a, true, 0, RULES), 10, 0, RULES).ok());
        assertEquals(1, diplomacy.claims().size(), "the claim waits for the world");
        state.forgetWar(9);
        assertTrue(state.holdsOf(9).isEmpty());
        assertEquals(1, state.holdsOf(8).size());
    }

    @Test
    void conquestsGoStepByStepAndAnnexationTakesEverything() {
        SiegeState state = new SiegeState();
        Conquest annex = state.addConquest(Conquest.Kind.ANNEX, 3, a, b, "d", List.of(), 0, 0);
        assertEquals(Conquest.Step.TREASURY, annex.step());
        int steps = 0;
        Conquest at = annex;
        while (!at.done()) {
            at = state.advance(at.id()).orElseThrow();
            steps++;
        }
        assertEquals(Conquest.Step.values().length - 1, steps, "an annexation carries out every step");
        assertTrue(state.conquests().isEmpty(), "a finished conquest leaves the journal");

        Conquest land = state.addConquest(Conquest.Kind.LAND, 3, a, b, "d", List.of(1L, 2L), 0, 0);
        assertEquals(Conquest.Step.LAND, land.step());
        assertEquals(Conquest.Step.DAMAGE, land.advance().step());
        assertEquals(Conquest.Step.DONE, land.advance().advance().step());
        Conquest city = state.addConquest(Conquest.Kind.CITY, 3, a, b, "d", List.of(), 4, 0);
        assertEquals(List.of(Conquest.Step.LAND, Conquest.Step.BUILDINGS, Conquest.Step.CITIZENS, Conquest.Step.CITIES, Conquest.Step.DAMAGE),
                List.of(city.step(), city.advance().step(), city.advance().advance().step(), city.advance().advance().advance().step(),
                        city.advance().advance().advance().advance().step()));
        assertThrows(IllegalArgumentException.class, () -> new Conquest(1, Conquest.Kind.ANNEX, 1, a, a, "d", List.of(), 0,
                Conquest.Step.TREASURY, 0));
        assertTrue(state.nextConquestId() > city.id());
    }

    @Test
    void warDamageIsRecordedOnceAndPricedForMending() {
        WarDamage damage = new WarDamage();
        assertTrue(damage.record(new WarDamage.Entry(1, a, "d", 10, "minecraft:stone_bricks", "minecraft:stone_bricks", 5, 0)));
        assertFalse(damage.record(new WarDamage.Entry(1, a, "d", 10, "minecraft:dirt", "minecraft:dirt", 5, 1)),
                "a place keeps the block the kingdom built");
        damage.record(new WarDamage.Entry(1, a, "d", 11, "minecraft:stone_bricks", "minecraft:stone_bricks", 5, 2));
        damage.record(new WarDamage.Entry(1, a, "d", 12, "minecraft:oak_planks", "minecraft:oak_planks", 6, 3));
        assertEquals(Map.of("minecraft:stone_bricks", 2, "minecraft:oak_planks", 1), damage.cost(a));
        assertEquals("minecraft:stone_bricks", damage.cost(a).keySet().iterator().next(), "the most needed first");
        assertEquals(2 * 3 + 2, damage.value(a, item -> item.contains("stone") ? 3 : 2));
        assertTrue(damage.mended(a, "d", 11));
        assertEquals(2, damage.count(a));
        damage.move(a, b, entry -> entry.building() == 6);
        assertEquals(1, damage.count(a));
        assertEquals(1, damage.count(b), "the damage of a city that changed hands goes with it");
        for (int i = 0; i < WarDamage.MAX_PER_KINGDOM + 10; i++) {
            damage.record(new WarDamage.Entry(1, c, "d", 1_000 + i, "s", "i", 0, i));
        }
        assertEquals(WarDamage.MAX_PER_KINGDOM, damage.count(c));
    }

    @Test
    void siegesAreFewForRulersOfTheWorld() {
        SiegeState state = new SiegeState();
        assertTrue(state.startSiege(new Siege(1, a, b, 0, "d", 0, 64, 0, 0, 1, true)));
        assertFalse(state.startSiege(new Siege(1, a, b, 4, "d", 0, 64, 0, 0, 1, true)), "a ruler of the world holds one siege at a time");
        assertTrue(state.startSiege(new Siege(1, c, b, 4, "d", 0, 64, 0, 0, 0, false)), "players may besiege more");
        assertTrue(state.startSiege(new Siege(1, c, b, 5, "d", 0, 64, 0, 0, 9, false)));
        assertEquals(Siege.MAX_RAMS, state.siege(1, b, 5).orElseThrow().rams());
        assertEquals(Set.of(0L, 4L, 5L), Set.copyOf(state.siegesOf(b).stream().map(Siege::city).toList()));
        state.forget(c);
        assertEquals(1, state.siegesOf(b).size());
    }

    @Test
    void rulesRefuseNonsenseAndNumbersHaveTheirForms() {
        assertThrows(IllegalArgumentException.class, () -> SIEGE.withTimes(0, 100, 100));
        assertThrows(IllegalArgumentException.class, () -> new SiegeRules(0, 1, 1, 1, 1, 1, 0.5, 100, 100, 100, 5, 5, 10, 2, 0, true, 1, 1));
        assertEquals("минута", Plurals.russian(1, "минута", "минуты", "минут"));
        assertEquals("минуты", Plurals.russian(3, "минута", "минуты", "минут"));
        assertEquals("минут", Plurals.russian(5, "минута", "минуты", "минут"));
        assertEquals("минут", Plurals.russian(11, "минута", "минуты", "минут"));
        assertEquals("минута", Plurals.russian(21, "минута", "минуты", "минут"));
        assertEquals("minutes", Plurals.english(2, "minute", "minutes"));
        assertEquals(Plurals.Form.N1, Plurals.form(1));
        assertEquals(Plurals.Form.N21, Plurals.form(21));
        assertEquals(Plurals.Form.N5, Plurals.form(12));
    }
}
