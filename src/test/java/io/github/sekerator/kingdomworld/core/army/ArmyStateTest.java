package io.github.sekerator.kingdomworld.core.army;

import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.state.KingdomRules;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmyStateTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final long DAY = 5;

    private KingdomWorldState world;
    private ArmyState army;
    private Kingdom kingdom;
    private UUID king;
    private UUID marshal;
    private UUID subject;
    private ArmyRules rules;

    @BeforeEach
    void setUp() {
        world = new KingdomWorldState();
        army = new ArmyState();
        king = UUID.randomUUID();
        marshal = UUID.randomUUID();
        subject = UUID.randomUUID();
        CreateKingdomResult result = world.found(UUID.randomUUID(), "Iron Realm", 0x993333, king, new Capital(OVERWORLD, 8, 64, 8),
                DAY * CitizenNeeds.TICKS_PER_DAY, KingdomRules.DEFAULT);
        kingdom = assertInstanceOf(CreateKingdomResult.Created.class, result).kingdom();
        for (UUID player : new UUID[]{marshal, subject}) {
            world.membership().invite(king, player, 1L);
            world.membership().acceptInvitation(player, kingdom.id(), 1L);
        }
        world.membership().setRole(king, marshal, Role.MARSHAL);
        ArmyRules.Builder builder = new ArmyRules.Builder();
        builder.maxSoldiers = 3;
        builder.maxMercenaries = 6;
        rules = builder.build();
    }

    private Citizen citizen() {
        Citizen citizen = new Citizen(UUID.randomUUID(), kingdom.id(), 5, Gender.MALE, 0, 0L, new WorldPos(OVERWORLD, 8, 64, 8));
        world.citizens().add(citizen);
        return citizen;
    }

    private long funds() {
        return world.economy().treasury(kingdom.id()).orElseThrow().funds();
    }

    @Test
    void onlyMembersWithTheRightRecruitAndDismiss() {
        Citizen citizen = citizen();
        assertEquals(Optional.of(KingdomError.NO_PERMISSION),
                army.recruit(subject, kingdom.id(), citizen.id(), Professions.WARRIOR.id(), 0, rules, world).failure());
        assertEquals(Optional.of(KingdomError.NOT_A_MEMBER),
                army.recruit(UUID.randomUUID(), kingdom.id(), citizen.id(), Professions.WARRIOR.id(), 0, rules, world).failure());
        assertEquals(Professions.UNEMPLOYED.id(), citizen.professionId(), "a refused recruitment changes nothing");
        assertTrue(army.recruit(marshal, kingdom.id(), citizen.id(), Professions.WARRIOR.id(), 0, rules, world).ok());
        assertEquals(Professions.WARRIOR.id(), citizen.professionId());
        assertEquals(SoldierRank.RECRUIT, army.soldier(citizen.id()).orElseThrow().rank());
        assertEquals(Optional.of(KingdomError.ALREADY_A_SOLDIER),
                army.recruit(king, kingdom.id(), citizen.id(), Professions.WARRIOR.id(), 0, rules, world).failure());
        assertTrue(army.recruit(king, kingdom.id(), citizen.id(), Professions.ARCHER.id(), 0, rules, world).ok(), "a warrior can become an archer");
        assertEquals(Optional.of(KingdomError.NO_PERMISSION), army.dismiss(subject, citizen.id(), world).failure());
        assertTrue(army.dismiss(marshal, citizen.id(), world).ok());
        assertEquals(Professions.UNEMPLOYED.id(), citizen.professionId(), "a dismissed soldier is a citizen again");
        assertTrue(army.soldier(citizen.id()).isEmpty());
        assertEquals(Optional.of(KingdomError.ARMY_PROFESSION), world.setProfession(king, citizen.id(), Professions.WARRIOR.id()).failure(),
                "soldiers do not come from the citizens tab");
    }

    @Test
    void theArmyKeepsItsLimits() {
        for (int i = 0; i < 3; i++) {
            assertTrue(army.recruit(null, kingdom.id(), citizen().id(), Professions.WARRIOR.id(), 0, rules, world).ok());
        }
        assertEquals(Optional.of(KingdomError.TOO_MANY_SOLDIERS),
                army.recruit(null, kingdom.id(), citizen().id(), Professions.WARRIOR.id(), 0, rules, world).failure());
    }

    @Test
    void squadsHaveACommanderAndOrders() {
        Citizen first = citizen();
        Citizen second = citizen();
        army.recruit(null, kingdom.id(), first.id(), Professions.WARRIOR.id(), 0, rules, world);
        army.recruit(null, kingdom.id(), second.id(), Professions.ARCHER.id(), 0, rules, world);
        assertEquals(Optional.of(KingdomError.NO_PERMISSION),
                army.formSquad(subject, kingdom.id(), List.of(first.id()), rules, world).outcome().failure());
        ArmySquad squad = army.formSquad(marshal, kingdom.id(), List.of(first.id(), second.id()), rules, world).squad().orElseThrow();
        assertEquals(first.id(), squad.commander());
        assertEquals(Optional.of(KingdomError.IN_ANOTHER_SQUAD),
                army.formSquad(marshal, kingdom.id(), List.of(second.id()), rules, world).outcome().failure());
        WorldPos point = new WorldPos(OVERWORLD, 30, 64, 30);
        assertTrue(army.setOrder(marshal, squad.id(), Order.hold(point), OVERWORLD, world).ok());
        assertEquals(Optional.of(KingdomError.BAD_ORDER),
                army.setOrder(marshal, squad.id(), Order.hold(new WorldPos("minecraft:the_nether", 0, 64, 0)), OVERWORLD, world).failure());
        assertEquals(Optional.of(KingdomError.BAD_ORDER), army.setOrder(marshal, squad.id(), Order.follow(UUID.randomUUID()), OVERWORLD, world)
                .failure(), "only members of the kingdom are followed");
        assertTrue(army.setOrder(marshal, squad.id(), Order.follow(subject), OVERWORLD, world).ok());
        assertEquals(Optional.of(KingdomError.BAD_ORDER), army.setOrder(marshal, squad.id(), Order.escort(99), OVERWORLD, world).failure());
        Order patrol = Order.NONE.withPatrolPoint(point).withPatrolPoint(new WorldPos(OVERWORLD, 40, 64, 30));
        assertEquals(2, patrol.points().size());
        assertTrue(Order.guard(point, 1).covers(new WorldPos(OVERWORLD, 45, 70, 15)));
        assertFalse(Order.guard(point, 1).covers(new WorldPos(OVERWORLD, 70, 70, 30)));
        army.dismiss(null, first.id(), world);
        assertEquals(second.id(), army.squad(squad.id()).orElseThrow().commander(), "the next soldier takes command");
        army.dismiss(null, second.id(), world);
        assertTrue(army.squad(squad.id()).isEmpty(), "an empty squad is gone");
    }

    @Test
    void mercenariesArePaidFromTheTreasuryAndLeaveWithoutPay() {
        army.refreshOffers(kingdom.id(), DAY, rules, new Random(1)::nextDouble);
        MercenaryBand offer = army.offersOf(kingdom.id()).get(0);
        long advance = ArmyState.advance(offer, rules);
        assertEquals(offer.dailyPrice() * rules.advanceDays(), advance);
        assertEquals(Optional.of(KingdomError.TREASURY_EMPTY),
                army.hire(king, kingdom.id(), offer.id(), DAY, rules, world).outcome().failure(), "nothing is hired on credit");
        assertTrue(world.economy().adminTreasury(kingdom.id(), advance + offer.dailyPrice(), DAY).ok());
        long before = funds();
        assertEquals(Optional.of(KingdomError.NO_PERMISSION), army.hire(subject, kingdom.id(), offer.id(), DAY, rules, world).outcome().failure());
        ArmySquad squad = army.hire(marshal, kingdom.id(), offer.id(), DAY, rules, world).squad().orElseThrow();
        assertTrue(squad.mercenaries());
        assertEquals(before - advance, funds(), "the advance left the treasury, exactly");
        MercenaryBand band = army.band(offer.id()).orElseThrow();
        assertEquals(DAY + rules.advanceDays() - 1, band.paidUntil());
        assertEquals(rules.advanceDays(), band.daysLeft(DAY));

        long day = DAY + rules.advanceDays();
        assertTrue(army.payday(kingdom.id(), day - 1, world).isEmpty(), "paid days need no pay");
        assertEquals(before - advance, funds());
        assertTrue(army.payday(kingdom.id(), day, world).isEmpty(), "the daily pay is paid while there is money");
        assertEquals(before - advance - offer.dailyPrice(), funds());
        assertEquals(0, funds(), "the treasury is empty now");
        List<MercenaryBand> left = army.payday(kingdom.id(), day + 1, world);
        assertEquals(1, left.size(), "an unpaid band leaves");
        assertTrue(army.band(offer.id()).isEmpty() && army.squad(squad.id()).isEmpty(), "the band and its squad are gone");
        assertEquals(0, funds(), "no money appears when mercenaries leave");
    }

    @Test
    void theNumberOfMercenariesIsLimited() {
        assertTrue(world.economy().adminTreasury(kingdom.id(), 1_000_000, DAY).ok());
        ArmyRules.Builder builder = new ArmyRules.Builder();
        builder.minBand = 4;
        builder.maxBand = 4;
        builder.maxMercenaries = 6;
        ArmyRules tight = builder.build();
        army.refreshOffers(kingdom.id(), DAY, tight, new Random(2)::nextDouble);
        List<MercenaryBand> offers = army.offersOf(kingdom.id());
        assertTrue(army.hire(king, kingdom.id(), offers.get(0).id(), DAY, tight, world).outcome().ok());
        Outcome second = army.hire(king, kingdom.id(), offers.get(1).id(), DAY, tight, world).outcome();
        assertEquals(Optional.of(KingdomError.TOO_MANY_MERCENARIES), second.failure());
        assertEquals(4, army.mercenaryCount(kingdom.id()));
    }

    @Test
    void offersAreMadeAnewEachDayAndPricedByStrength() {
        army.refreshOffers(kingdom.id(), DAY, rules, new Random(5)::nextDouble);
        List<MercenaryBand> first = army.offersOf(kingdom.id());
        assertEquals(rules.offersPerDay(), first.size());
        for (MercenaryBand band : first) {
            assertTrue(band.fighters().size() >= rules.minBand() && band.fighters().size() <= rules.maxBand());
            assertEquals(rules.price(band.power()), band.dailyPrice());
        }
        army.refreshOffers(kingdom.id(), DAY + 1, rules, new Random(6)::nextDouble);
        assertTrue(army.offersOf(kingdom.id()).stream().noneMatch(first::contains), "yesterday's offers are gone");
        assertEquals(DAY + 1, army.offersDay(kingdom.id()));
    }

    @Test
    void ranksFollowExperienceAndTrainingStopsAtTheCap() {
        Citizen citizen = citizen();
        army.recruit(null, kingdom.id(), citizen.id(), Professions.WARRIOR.id(), 0, rules, world);
        for (int i = 0; i < 1_000; i++) {
            army.train(citizen.id(), 10, rules);
        }
        assertEquals(rules.trainingCap(), army.soldier(citizen.id()).orElseThrow().experience());
        assertEquals(SoldierRank.SERGEANT, army.soldier(citizen.id()).orElseThrow().rank());
        army.reward(citizen.id(), 5_000, 3, 1);
        assertEquals(SoldierRank.CAPTAIN, army.soldier(citizen.id()).orElseThrow().rank(), "battles take soldiers beyond training");
        assertEquals(SoldierRank.PRIVATE, SoldierRank.of(100));
        assertEquals(SoldierRank.RECRUIT, SoldierRank.of(99));
    }

    @Test
    void aDissolvedKingdomTakesItsArmyAlong() {
        Citizen citizen = citizen();
        army.recruit(null, kingdom.id(), citizen.id(), Professions.WARRIOR.id(), 0, rules, world);
        army.addBarracks(kingdom.id(), new WorldPos(OVERWORLD, 1, 64, 1));
        army.refreshOffers(kingdom.id(), DAY, rules, new Random(3)::nextDouble);
        army.forgetKingdom(kingdom.id());
        assertTrue(army.soldiers().isEmpty() && army.bands().isEmpty() && army.barracks().isEmpty());
        assertTrue(army.verify().isEmpty());
    }
}
