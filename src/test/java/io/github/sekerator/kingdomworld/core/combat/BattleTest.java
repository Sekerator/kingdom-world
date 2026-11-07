package io.github.sekerator.kingdomworld.core.combat;

import io.github.sekerator.kingdomworld.core.citizen.TaskRisks;
import io.github.sekerator.kingdomworld.core.guild.Expeditions;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.DoubleSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleTest {
    private static final BattleRules RULES = new BattleRules(0.15, 0.03, 1.0);

    private static List<Fighter> fighters(int count, int level, double weapon, float armor) {
        List<Fighter> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new Fighter(new UUID(level, i), level, weapon, 1.6, armor, 0.0f, 0, 1.0, false));
        }
        return list;
    }

    private static DoubleSupplier constant(double value) {
        return () -> value;
    }

    @Test
    void theStrengthOfAnAdventurerIsWhatTheGuildCountedBefore() {
        // The guild's own formula before the army: 10 x (rank + 1) x (1 + weapon / 8) x (1 + armor / 20) x skill.
        for (Rank rank : Rank.values()) {
            for (double weapon : new double[]{0, 3, 7.5}) {
                for (float armor : new float[]{0, 15, 20}) {
                    for (int experience : new int[]{0, 500, 5_000}) {
                        double before = 10.0 * (rank.ordinal() + 1) * (1 + weapon / 8.0) * (1 + armor / 20.0)
                                * (1 + Math.min(1.0, experience / 1_000.0) * 0.5);
                        double now = Expeditions.power(new Expeditions.Member(UUID.randomUUID(), rank, weapon, armor, experience));
                        assertEquals(before, now, 1e-9, "the strength of an adventurer changed");
                    }
                }
            }
        }
        assertEquals(0.5, Expeditions.successChance(10, 10), 1e-12, "an even expedition is a toss of a coin, as before");
        assertEquals(Battle.winChance(30, 10), Expeditions.successChance(30, 10), 1e-12);
    }

    @Test
    void strengthGrowsWithGearRankAndHealthAndFallsWithWounds() {
        Fighter bare = new Fighter(UUID.randomUUID(), 0, 0, 0, 0, 0, 0, 1.0, false);
        assertEquals(CombatPower.BASE, CombatPower.of(bare), 1e-9);
        Fighter armed = new Fighter(bare.id(), 0, 6, 1.6, 15, 2, 0, 1.0, false);
        Fighter veteran = new Fighter(bare.id(), 4, 6, 1.6, 15, 2, 1_000, 1.0, false);
        Fighter hurt = new Fighter(bare.id(), 4, 6, 1.6, 15, 2, 1_000, 0.4, true);
        assertTrue(CombatPower.of(armed) > CombatPower.of(bare));
        assertTrue(CombatPower.of(veteran) > CombatPower.of(armed));
        assertTrue(CombatPower.of(hurt) < CombatPower.of(veteran) * 0.5, "a wounded fighter is much weaker");
    }

    @Test
    void anEmptySideLosesWithoutABlowAndTwoEmptySidesDoNotFight() {
        Side soldiers = Side.of(Side.Kind.SOLDIERS, fighters(3, 1, 5, 10));
        Battle.Result none = Battle.resolve(Side.of(Side.Kind.SOLDIERS, List.of()), Side.NOBODY, RULES, constant(0.0));
        assertEquals(Battle.Winner.NONE, none.winner());
        assertTrue(none.fates().isEmpty());
        Battle.Result walkover = Battle.resolve(soldiers, Side.crowd(Side.Kind.MONSTERS, 0, 0), RULES, constant(0.0));
        assertEquals(Battle.Winner.FIRST, walkover.winner());
        assertTrue(walkover.fates().isEmpty(), "nobody is hurt without a fight");
        Battle.Result lost = Battle.resolve(Side.NOBODY, Side.crowd(Side.Kind.MONSTERS, 50, 2), RULES, constant(0.0));
        assertEquals(Battle.Winner.SECOND, lost.winner());
        assertEquals(0, lost.secondLost());
    }

    @Test
    void anEvenFightIsATossOfACoin() {
        Side first = Side.of(Side.Kind.SOLDIERS, fighters(3, 1, 5, 10));
        Side second = Side.crowd(Side.Kind.MONSTERS, first.power(), 3);
        assertEquals(0.5, Battle.winChance(first.power(), second.power()), 1e-12);
        int won = 0;
        Random random = new Random(42);
        for (int i = 0; i < 4_000; i++) {
            if (Battle.resolve(first, second, RULES, random::nextDouble).winner() == Battle.Winner.FIRST) {
                won++;
            }
        }
        assertTrue(won > 1_800 && won < 2_200, "an even fight should be won about half the time, not " + won + " of 4000");
    }

    @Test
    void anOverwhelmingSideWinsAndHardlySuffers() {
        Side army = Side.of(Side.Kind.SOLDIERS, fighters(20, 5, 7, 20));
        Side rabble = Side.crowd(Side.Kind.BANDITS, 15, 3);
        assertTrue(Battle.winChance(army.power(), rabble.power()) > 0.9999);
        Random random = new Random(7);
        int deaths = 0;
        for (int i = 0; i < 200; i++) {
            Battle.Result result = Battle.resolve(army, rabble, RULES, random::nextDouble);
            assertEquals(Battle.Winner.FIRST, result.winner());
            assertEquals(3, result.secondLost(), "a rabble against an army is wiped out");
            assertEquals(1.0, result.lootShare(), 1e-12, "nobody is left to keep the spoils");
            assertFalse(result.secondFled());
            deaths += result.fallen(army);
        }
        assertTrue(deaths <= 2, "an overwhelming army hardly loses fighters to a rabble: " + deaths);
    }

    @Test
    void theLoserSuffersMoreAndRetreatsWithItsSurvivors() {
        Side first = Side.of(Side.Kind.SOLDIERS, fighters(4, 0, 0, 0));
        Side second = Side.crowd(Side.Kind.MONSTERS, first.power(), 8);
        // 0.9 for the result: the first side loses; then every death and wound roll as low as can be.
        Battle.Result result = Battle.resolve(first, second, RULES, new DoubleSupplier() {
            private int calls;

            @Override
            public double getAsDouble() {
                return calls++ == 0 ? 0.9 : 0.0;
            }
        });
        assertEquals(Battle.Winner.SECOND, result.winner());
        assertEquals(4, result.fallen(first), "with the lowest rolls everybody of the loser falls");
        assertTrue(result.secondLost() <= 2, "the winner lost few: " + result.secondLost());
        assertTrue(Battle.casualtyRisk(1.0, 2.0, 0.15, 0.03).death() > Battle.casualtyRisk(1.0, 1.0, 0.15, 0.03).death());
        assertTrue(Battle.casualtyRisk(0.5, 1.0, 0.15, 0.03).injury() > Battle.casualtyRisk(2.0, 1.0, 0.15, 0.03).injury());
    }

    @Test
    void theSameRandomNumbersGiveTheSameBattle() {
        Side first = Side.of(Side.Kind.SOLDIERS, fighters(5, 2, 6, 12));
        Side second = Side.of(Side.Kind.MERCENARIES, fighters(6, 1, 5, 7)).with(Side.crowd(Side.Kind.MONSTERS, 40, 2));
        for (long seed = 0; seed < 20; seed++) {
            Random a = new Random(seed);
            Random b = new Random(seed);
            assertEquals(Battle.resolve(first, second, RULES, a::nextDouble), Battle.resolve(first, second, RULES, b::nextDouble),
                    "seed " + seed);
        }
    }

    @Test
    void battlesAreHarmlessWithoutRisk() {
        Side first = Side.of(Side.Kind.SOLDIERS, fighters(5, 0, 0, 0));
        Side second = Side.crowd(Side.Kind.MONSTERS, 500, 5);
        Battle.Result result = Battle.resolve(first, second, new BattleRules(0.9, 0.5, 0.0), constant(0.0));
        for (TaskRisks.Accident accident : result.fates().values()) {
            assertEquals(TaskRisks.Accident.NONE, accident, "taskRiskMultiplier 0 turns the dangers off");
        }
    }
}
