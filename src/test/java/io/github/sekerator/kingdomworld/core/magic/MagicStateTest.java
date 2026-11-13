package io.github.sekerator.kingdomworld.core.magic;

import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.membership.RolePermissions;
import io.github.sekerator.kingdomworld.core.state.KingdomRules;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicStateTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final MagicRules RULES = MagicRules.DEFAULT;

    private KingdomWorldState world;
    private MagicState magic;
    private Economy economy;
    private Kingdom kingdom;
    private UUID king;
    private UUID mage;
    private UUID subject;
    private int changes;

    @BeforeEach
    void setUp() {
        world = new KingdomWorldState();
        magic = new MagicState();
        magic.setChangeListener(() -> changes++);
        economy = world.economy();
        king = UUID.randomUUID();
        mage = UUID.randomUUID();
        subject = UUID.randomUUID();
        CreateKingdomResult result = world.found(UUID.randomUUID(), "Arcane Realm", 0x6633AA, king, new Capital(OVERWORLD, 8, 64, 8), 0,
                KingdomRules.DEFAULT);
        kingdom = assertInstanceOf(CreateKingdomResult.Created.class, result).kingdom();
        for (UUID player : new UUID[]{mage, subject}) {
            world.membership().invite(king, player, 1L);
            world.membership().acceptInvitation(player, kingdom.id(), 1L);
        }
        world.membership().setRole(king, mage, Role.COURT_MAGE);
    }

    private void fill(long energy) {
        magic.adminSet(kingdom.id(), energy, 0);
    }

    // ---------------------------------------------------------------- energy and the journal

    @Test
    void crystalsAddEnergyAndTheJournalWritesIt() {
        assertTrue(magic.add(kingdom.id(), 300, EnergyCategory.CRYSTALS, 5, RULES).ok());
        assertTrue(magic.add(kingdom.id(), 30, EnergyCategory.CRYSTALS, 5, RULES).ok());
        assertEquals(330, magic.energy(kingdom.id()));
        assertTrue(magic.spend(kingdom.id(), 100, EnergyCategory.SPELLS, 6).ok());
        assertEquals(230, magic.energy(kingdom.id()));
        EnergyJournal journal = magic.find(kingdom.id()).orElseThrow().journal();
        assertEquals(new EnergyJournal.Totals(330, 0), journal.totals(5, EnergyJournal.Period.TODAY).get(EnergyCategory.CRYSTALS));
        assertEquals(new EnergyJournal.Totals(0, 100), journal.totals(6, EnergyJournal.Period.TODAY).get(EnergyCategory.SPELLS));
        assertEquals(new EnergyJournal.Totals(330, 100), journal.sum(6, EnergyJournal.Period.WEEK));
        assertEquals(new EnergyJournal.Totals(330, 0), journal.sum(6, EnergyJournal.Period.YESTERDAY));
        assertTrue(changes > 0);
    }

    @Test
    void energyNeverGoesBelowZeroAndARefusedSpendChangesNothing() {
        fill(50);
        Outcome refused = magic.spend(kingdom.id(), 51, EnergyCategory.SPELLS, 1);
        assertEquals(Optional.of(KingdomError.NOT_ENOUGH_ENERGY), refused.failure());
        assertEquals(50, magic.energy(kingdom.id()));
        assertFalse(magic.spend(kingdom.id(), -1, EnergyCategory.SPELLS, 1).ok());
        assertFalse(magic.add(kingdom.id(), 0, EnergyCategory.CRYSTALS, 1, RULES).ok());
        assertFalse(magic.add(kingdom.id(), -5, EnergyCategory.CRYSTALS, 1, RULES).ok());
        assertEquals(50, magic.energy(kingdom.id()));
    }

    @Test
    void theJournalForgetsOldDays() {
        EnergyJournal journal = new EnergyJournal();
        journal.record(1, EnergyCategory.CRYSTALS, 10, 0);
        journal.record(1 + EnergyJournal.DAYS_KEPT, EnergyCategory.CRYSTALS, 5, 0);
        assertFalse(journal.days().containsKey(1L));
        assertEquals(5, journal.sum(1 + EnergyJournal.DAYS_KEPT, EnergyJournal.Period.WEEK).income());
    }

    // ---------------------------------------------------------------- capacity

    @Test
    void theStoreHoldsNoMoreThanItsCapacityAndAccumulatorsAddRoom() {
        long base = RULES.baseCapacity;
        assertEquals(base, magic.capacity(kingdom.id(), RULES));
        assertTrue(magic.add(kingdom.id(), base, EnergyCategory.CRYSTALS, 1, RULES).ok());
        assertEquals(Optional.of(KingdomError.ENERGY_FULL), magic.add(kingdom.id(), 1, EnergyCategory.CRYSTALS, 1, RULES).failure());
        WorldPos at = new WorldPos(OVERWORLD, 1, 64, 1);
        magic.putDevice(MagicDevice.Kind.ACCUMULATOR, kingdom.id(), at);
        assertEquals(base + RULES.accumulatorCapacity, magic.capacity(kingdom.id(), RULES));
        assertTrue(magic.add(kingdom.id(), RULES.accumulatorCapacity, EnergyCategory.CRYSTALS, 1, RULES).ok());
        // Taking the accumulator away keeps the energy, but no more comes in.
        magic.removeDevice(at);
        assertEquals(base + RULES.accumulatorCapacity, magic.energy(kingdom.id()));
        assertEquals(0, magic.room(kingdom.id(), RULES));
        assertFalse(magic.add(kingdom.id(), 1, EnergyCategory.CRYSTALS, 1, RULES).ok());
        assertTrue(magic.spend(kingdom.id(), 10, EnergyCategory.SPELLS, 1).ok());
    }

    @Test
    void devicesKeepTheirNumbersAndGatesHaveALimit() {
        MagicRules.Builder builder = new MagicRules.Builder();
        builder.maxGates = 1;
        MagicRules rules = builder.build();
        WorldPos first = new WorldPos(OVERWORLD, 1, 64, 1);
        MagicDevice gate = magic.putDevice(MagicDevice.Kind.GATE, kingdom.id(), first);
        assertEquals(Optional.of(KingdomError.LIMIT_REACHED), magic.checkDevice(MagicDevice.Kind.GATE, kingdom.id(), rules).failure());
        assertEquals(gate.id(), magic.putDevice(MagicDevice.Kind.GATE, kingdom.id(), first).id());
        assertEquals(Optional.of(gate), magic.deviceById(gate.id()));
        magic.forgetKingdom(kingdom.id());
        assertTrue(magic.devices().isEmpty());
    }

    // ---------------------------------------------------------------- research

    @Test
    void researchCostsEnergyAndTakesTheWorkOfMages() {
        fill(10_000);
        assertTrue(magic.startResearch(mage, kingdom.id(), Research.WARD, 1, RULES, world).ok());
        assertEquals(10_000 - RULES.researchCost(Research.WARD), magic.energy(kingdom.id()));
        assertEquals(Optional.of(KingdomError.RESEARCH_UNDER_WAY),
                magic.checkResearch(mage, kingdom.id(), Research.HEALING, RULES, world).failure());
        long ticks = RULES.researchTicks(Research.WARD);
        assertTrue(magic.advanceResearch(kingdom.id(), ticks - 1, RULES).isEmpty());
        assertEquals(Optional.of(Research.WARD), magic.advanceResearch(kingdom.id(), 1, RULES));
        assertTrue(magic.researched(kingdom.id(), Research.WARD));
        assertEquals(1, magic.towerLevel(kingdom.id()));
        assertEquals(Optional.of(KingdomError.ALREADY_RESEARCHED),
                magic.checkResearch(mage, kingdom.id(), Research.WARD, RULES, world).failure());
    }

    @Test
    void researchNeedsWhatComesBeforeItAndEnergyForItsCost() {
        fill(100_000);
        assertEquals(Optional.of(KingdomError.RESEARCH_NEEDED),
                magic.checkResearch(mage, kingdom.id(), Research.ARCANA_2, RULES, world).failure());
        assertEquals(Optional.of(KingdomError.RESEARCH_NEEDED), magic.checkResearch(mage, kingdom.id(), Research.GATES, RULES, world).failure());
        fill(RULES.researchCost(Research.ARCANA_1) - 1);
        assertEquals(Optional.of(KingdomError.NOT_ENOUGH_ENERGY),
                magic.startResearch(mage, kingdom.id(), Research.ARCANA_1, 1, RULES, world).failure());
        assertEquals(RULES.researchCost(Research.ARCANA_1) - 1, magic.energy(kingdom.id()));
        assertTrue(magic.find(kingdom.id()).orElseThrow().current().isEmpty());
    }

    @Test
    void onlyTheKingAndTheCourtMageManageMagicByDefault() {
        fill(10_000);
        assertTrue(RolePermissions.defaults().has(Role.COURT_MAGE, Permission.MANAGE_MAGIC));
        assertTrue(RolePermissions.defaults().has(Role.KING, Permission.MANAGE_MAGIC));
        assertFalse(RolePermissions.defaults().has(Role.MARSHAL, Permission.MANAGE_MAGIC));
        assertFalse(RolePermissions.defaults().has(Role.SUBJECT, Permission.MANAGE_MAGIC));
        assertEquals(Optional.of(KingdomError.NO_PERMISSION),
                magic.startResearch(subject, kingdom.id(), Research.WARD, 1, RULES, world).failure());
        assertEquals(Optional.of(KingdomError.NO_PERMISSION), magic.setSpell(subject, kingdom.id(), KingdomSpell.WARD, true, 1, 1, RULES,
                world).failure());
        assertEquals(10_000, magic.energy(kingdom.id()));
        assertTrue(magic.startResearch(king, kingdom.id(), Research.WARD, 1, RULES, world).ok());
    }

    // ---------------------------------------------------------------- spells of the kingdom

    @Test
    void aSpellPaidByTheDayPaysWhenSwitchedOnAndEveryNewDay() {
        magic.adminResearch(kingdom.id(), Research.WARD, true);
        long price = RULES.spellPrice(KingdomSpell.WARD);
        fill(price * 3 + price * 3 + 1);
        assertTrue(magic.setSpell(mage, kingdom.id(), KingdomSpell.WARD, true, 3, 10, RULES, world).ok());
        assertEquals(price * 3 + 1, magic.energy(kingdom.id()));
        assertTrue(magic.chargeDay(kingdom.id(), 10, RULES, spell -> 3).isEmpty());
        assertEquals(price * 3 + 1, magic.energy(kingdom.id()), "The same day was paid twice");
        assertTrue(magic.chargeDay(kingdom.id(), 11, RULES, spell -> 3).isEmpty());
        assertEquals(1, magic.energy(kingdom.id()));
        // Without energy the spell switches off on the next day, and nothing is owed.
        assertEquals(List.of(KingdomSpell.WARD), magic.chargeDay(kingdom.id(), 12, RULES, spell -> 3));
        assertFalse(magic.isOn(kingdom.id(), KingdomSpell.WARD));
        assertEquals(1, magic.energy(kingdom.id()));
    }

    @Test
    void aSpellCannotBeSwitchedOnWithoutItsResearchOrTheEnergyForToday() {
        fill(1_000);
        assertEquals(Optional.of(KingdomError.RESEARCH_NEEDED), magic.setSpell(mage, kingdom.id(), KingdomSpell.FERTILITY, true, 2, 1, RULES,
                world).failure());
        magic.adminResearch(kingdom.id(), Research.FERTILITY, true);
        fill(RULES.spellPrice(KingdomSpell.FERTILITY) * 4 - 1);
        assertEquals(Optional.of(KingdomError.NOT_ENOUGH_ENERGY), magic.setSpell(mage, kingdom.id(), KingdomSpell.FERTILITY, true, 4, 1, RULES,
                world).failure());
        assertFalse(magic.isOn(kingdom.id(), KingdomSpell.FERTILITY));
        assertEquals(RULES.spellPrice(KingdomSpell.FERTILITY) * 4 - 1, magic.energy(kingdom.id()));
    }

    @Test
    void healingPaysOncePerWoundedCitizenAndSwitchesOffWithoutEnergy() {
        magic.adminResearch(kingdom.id(), Research.HEALING, true);
        long price = RULES.spellPrice(KingdomSpell.HEALING);
        fill(price + 1);
        assertTrue(magic.setSpell(mage, kingdom.id(), KingdomSpell.HEALING, true, 0, 1, RULES, world).ok());
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertTrue(magic.healWounded(kingdom.id(), first, 1, RULES));
        assertTrue(magic.healWounded(kingdom.id(), first, 1, RULES));
        assertEquals(1, magic.energy(kingdom.id()));
        assertFalse(magic.healWounded(kingdom.id(), second, 1, RULES));
        assertFalse(magic.isOn(kingdom.id(), KingdomSpell.HEALING));
        assertEquals(1, magic.energy(kingdom.id()));
        magic.keepHealed(kingdom.id(), citizen -> false);
        assertTrue(magic.find(kingdom.id()).orElseThrow().healed().isEmpty());
    }

    @Test
    void theFairWindPaysOncePerCaravan() {
        magic.adminResearch(kingdom.id(), Research.TAILWIND, true);
        long price = RULES.spellPrice(KingdomSpell.TAILWIND);
        fill(price * 2);
        assertTrue(magic.setSpell(mage, kingdom.id(), KingdomSpell.TAILWIND, true, 0, 1, RULES, world).ok());
        assertTrue(magic.speedCaravan(kingdom.id(), 7, 1, RULES));
        assertFalse(magic.speedCaravan(kingdom.id(), 7, 1, RULES));
        assertTrue(magic.caravanSpedUp(7));
        assertEquals(price, magic.energy(kingdom.id()));
        magic.keepCaravans(kingdom.id(), id -> false);
        assertFalse(magic.caravanSpedUp(7));
    }

    @Test
    void gatesRefuseATripWithoutEnergyButStayOn() {
        magic.adminResearch(kingdom.id(), Research.WARD, true);
        magic.adminResearch(kingdom.id(), Research.GATES, true);
        assertTrue(magic.setSpell(mage, kingdom.id(), KingdomSpell.GATES, true, 0, 1, RULES, world).ok());
        assertEquals(Optional.of(KingdomError.NOT_ENOUGH_ENERGY), magic.payPerUse(kingdom.id(), KingdomSpell.GATES, 1, 1, RULES).failure());
        assertTrue(magic.isOn(kingdom.id(), KingdomSpell.GATES));
        fill(RULES.spellPrice(KingdomSpell.GATES));
        assertTrue(magic.payPerUse(kingdom.id(), KingdomSpell.GATES, 1, 1, RULES).ok());
        assertEquals(0, magic.energy(kingdom.id()));
        assertEquals(RULES.spellPrice(KingdomSpell.GATES),
                magic.find(kingdom.id()).orElseThrow().journal().totals(1, EnergyJournal.Period.TODAY).get(EnergyCategory.GATES).expense());
    }

    @Test
    void theWardTakesOnlyOwnLandWithinItsLimit() {
        MagicRules.Builder builder = new MagicRules.Builder();
        builder.wardMaxChunks = 1;
        MagicRules rules = builder.build();
        assertEquals(Optional.of(KingdomError.NOT_OWNED), magic.toggleWard(mage, kingdom.id(), OVERWORLD, 5L, false, rules, world).failure());
        assertTrue(magic.toggleWard(mage, kingdom.id(), OVERWORLD, 5L, true, rules, world).ok());
        assertEquals(Optional.of(KingdomError.LIMIT_REACHED), magic.toggleWard(mage, kingdom.id(), OVERWORLD, 6L, true, rules, world).failure());
        assertFalse(magic.warded(OVERWORLD, 5L), "A ward that is switched off protects");
        magic.adminResearch(kingdom.id(), Research.WARD, true);
        fill(100);
        assertTrue(magic.setSpell(mage, kingdom.id(), KingdomSpell.WARD, true, 1, 1, rules, world).ok());
        assertTrue(magic.warded(OVERWORLD, 5L));
        assertTrue(magic.toggleWard(mage, kingdom.id(), OVERWORLD, 5L, true, rules, world).ok());
        assertFalse(magic.warded(OVERWORLD, 5L));
    }

    // ---------------------------------------------------------------- spells of players

    @Test
    void subjectsLearnSpellsForMoneyWhenTheTowerAndTheirRankAllow() {
        economy.adminAccount(subject, kingdom.id(), 1_000);
        long treasury = economy.treasury(kingdom.id()).orElseThrow().funds();
        PlayerSpell fireball = PlayerSpell.FIREBALL;
        assertTrue(magic.learn(subject, kingdom.id(), fireball, Rank.F, 1, RULES, world).ok());
        assertTrue(magic.knows(subject, fireball));
        assertEquals(1_000 - RULES.lessonPrice(fireball), economy.accounts().balance(subject, kingdom.id()));
        assertEquals(treasury + RULES.lessonPrice(fireball), economy.treasury(kingdom.id()).orElseThrow().funds());
        // The next lesson is level II, which a guild of mages without a rank does not teach.
        assertEquals(Optional.of(KingdomError.LESSON_TOO_HIGH), magic.checkLearn(subject, kingdom.id(), fireball, Rank.F, RULES, world).failure());
        assertEquals(Optional.of(KingdomError.TOWER_TOO_LOW), magic.checkLearn(subject, kingdom.id(), PlayerSpell.BLINK, Rank.SSS, RULES, world)
                .failure());
        magic.adminResearch(kingdom.id(), Research.ARCANA_1, true);
        magic.adminResearch(kingdom.id(), Research.ARCANA_2, true);
        magic.adminResearch(kingdom.id(), Research.ARCANA_3, true);
        assertEquals(Optional.of(KingdomError.RANK_TOO_LOW), magic.checkLearn(subject, kingdom.id(), PlayerSpell.BLINK, Rank.D, RULES, world)
                .failure());
        assertTrue(magic.checkLearn(subject, kingdom.id(), PlayerSpell.BLINK, Rank.C, RULES, world).ok());
        assertEquals(Optional.of(KingdomError.NOT_A_MEMBER), magic.checkLearn(UUID.randomUUID(), kingdom.id(), fireball, Rank.F, RULES, world)
                .failure());
    }

    @Test
    void aLessonWithoutMoneyTeachesNothing() {
        assertEquals(Optional.of(KingdomError.NOT_ENOUGH_MONEY), magic.learn(subject, kingdom.id(), PlayerSpell.HEAL, Rank.F, 1, RULES, world)
                .failure());
        assertFalse(magic.knows(subject, PlayerSpell.HEAL));
        assertEquals(Set.of(), magic.learned(subject));
    }

    @Test
    void restoredDataComesBackTheSame() {
        MagicState copy = new MagicState();
        copy.restoreKingdom(kingdom.id(), 77, Set.of(Research.WARD), Research.HEALING, 120.5, Set.of(KingdomSpell.WARD),
                Map.of(KingdomSpell.WARD, 4L), List.of(new KingdomMagic.WardChunk(OVERWORLD, 9L)), List.of(), List.of(3L));
        copy.restoreDevice(new MagicDevice(5, MagicDevice.Kind.GENERATOR, kingdom.id(), new WorldPos(OVERWORLD, 2, 70, 2)));
        assertEquals(77, copy.energy(kingdom.id()));
        assertEquals(6, copy.nextDeviceId());
        assertTrue(copy.warded(OVERWORLD, 9L));
        assertTrue(copy.caravanSpedUp(3L));
        assertEquals(120.5, copy.find(kingdom.id()).orElseThrow().progress());
    }

    // ---------------------------------------------------------------- the guild of mages

    @Test
    void theGuildOfMagesTeachesLevelsUpToItsRankAndEachCostsMore() {
        economy.adminAccount(subject, kingdom.id(), 10_000);
        PlayerSpell fireball = PlayerSpell.FIREBALL;
        magic.setGuildRank(kingdom.id(), Rank.A);
        long[] paid = new long[MagicRules.MAX_LEVEL];
        for (int level = 1; level <= 2; level++) {
            long before = economy.accounts().balance(subject, kingdom.id());
            assertTrue(magic.learn(subject, kingdom.id(), fireball, Rank.F, 1, RULES, world).ok(), "level " + level);
            assertEquals(level, magic.level(subject, fireball));
            paid[level - 1] = before - economy.accounts().balance(subject, kingdom.id());
        }
        // A guild of rank A teaches up to level II, one of rank SS up to IV.
        assertEquals(Optional.of(KingdomError.LESSON_TOO_HIGH), magic.checkLearn(subject, kingdom.id(), fireball, Rank.F, RULES, world).failure());
        magic.setGuildRank(kingdom.id(), Rank.SS);
        for (int level = 3; level <= 4; level++) {
            long before = economy.accounts().balance(subject, kingdom.id());
            assertTrue(magic.learn(subject, kingdom.id(), fireball, Rank.F, 1, RULES, world).ok(), "level " + level);
            paid[level - 1] = before - economy.accounts().balance(subject, kingdom.id());
        }
        assertEquals(RULES.lessonPrice(fireball), paid[0]);
        assertTrue(paid[0] < paid[1] && paid[1] < paid[2] && paid[2] < paid[3], java.util.Arrays.toString(paid));
        assertEquals(Optional.of(KingdomError.ALREADY_LEARNED), magic.checkLearn(subject, kingdom.id(), fireball, Rank.F, RULES, world).failure());
        assertTrue(RULES.levelPower(4) > RULES.levelPower(3) && RULES.levelPower(2) > RULES.levelPower(1), "a higher level is stronger");
        assertEquals(1, RULES.lessonLevel(Rank.B));
        assertEquals(2, RULES.lessonLevel(Rank.A));
        assertEquals(3, RULES.lessonLevel(Rank.S));
        assertEquals(4, RULES.lessonLevel(Rank.SS));
    }

    @Test
    void aGuestPaysMoreAndOneWantedOrAtWarIsNotTaught() {
        UUID guest = UUID.randomUUID();
        economy.adminAccount(guest, kingdom.id(), 1_000);
        PlayerSpell fireball = PlayerSpell.FIREBALL;
        assertEquals(Optional.of(KingdomError.NOT_WELCOME), magic.checkLearn(guest, kingdom.id(), fireball, Rank.F, MagicState.Access.BARRED, RULES,
                world).failure());
        long price = magic.lessonPrice(guest, fireball, MagicState.Access.GUEST, RULES);
        assertEquals(Math.round(RULES.lessonPrice(fireball) * RULES.foreignerPrice), price);
        assertTrue(price > magic.lessonPrice(subject, fireball, MagicState.Access.SUBJECT, RULES));
        assertTrue(magic.learn(guest, kingdom.id(), fireball, Rank.F, MagicState.Access.GUEST, 1, RULES, world).ok());
        assertEquals(1_000 - price, economy.accounts().balance(guest, kingdom.id()));
        assertTrue(magic.knows(guest, fireball));
    }

    @Test
    void theRankOfAGuildOfMagesOfPlayersComesFromItsResearchesMagesAndTowers() {
        assertEquals(Rank.F, RULES.guildRankOf(0, 0, 0));
        assertEquals(Rank.E, RULES.guildRankOf(0, 2, 0));
        assertEquals(Rank.B, RULES.guildRankOf(3, 2, 0));
        assertEquals(Rank.SS, RULES.guildRankOf(Research.COUNT, 10, 2));
        assertEquals(Rank.F, magic.guildRank(kingdom.id()), "no rank until it is given one");
        assertTrue(magic.setGuildRank(kingdom.id(), Rank.B));
        assertEquals(Rank.B, magic.guildRank(kingdom.id()));
        assertFalse(magic.setGuildRank(kingdom.id(), Rank.B));
    }

    @Test
    void aKingdomOfTheWorldFoundsItsGuildOfMagesWithResearchesAndEnergy() {
        magic.found(kingdom.id(), Rank.S, java.util.List.of(Research.WARD, Research.HEALING), 3_000, 1, RULES);
        assertEquals(Rank.S, magic.guildRank(kingdom.id()));
        assertEquals(2, magic.towerLevel(kingdom.id()));
        assertEquals(3_000, magic.energy(kingdom.id()));
    }

    @Test
    void spellsLearnedBeforeLevelsAreOfLevelOne() {
        UUID old = UUID.randomUUID();
        magic.restoreLearned(old, java.util.EnumSet.of(PlayerSpell.HEAL, PlayerSpell.LIGHT));
        assertEquals(1, magic.level(old, PlayerSpell.HEAL));
        assertEquals(0, magic.level(old, PlayerSpell.BLINK));
        magic.restoreLevels(old, java.util.Map.of(PlayerSpell.BLINK, 9));
        assertEquals(MagicRules.MAX_LEVEL, magic.level(old, PlayerSpell.BLINK), "a level out of range is kept within it");
    }
}
