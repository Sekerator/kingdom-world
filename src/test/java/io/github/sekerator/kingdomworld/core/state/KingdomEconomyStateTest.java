package io.github.sekerator.kingdomworld.core.state;

import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.EconomyRules;
import io.github.sekerator.kingdomworld.core.economy.Payroll;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.membership.RolePermissions;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KingdomEconomyStateTest {
    private static final String OVERWORLD = "minecraft:overworld";

    private KingdomWorldState state;
    private UUID king;
    private UUID treasurer;
    private UUID subject;
    private Kingdom kingdom;

    @BeforeEach
    void setUp() {
        state = new KingdomWorldState();
        king = UUID.randomUUID();
        treasurer = UUID.randomUUID();
        subject = UUID.randomUUID();
        CreateKingdomResult result = state.found(UUID.randomUUID(), "Coin Realm", 0x336699, king, new Capital(OVERWORLD, 8, 64, 8),
                5 * CitizenNeeds.TICKS_PER_DAY, KingdomRules.DEFAULT);
        kingdom = assertInstanceOf(CreateKingdomResult.Created.class, result).kingdom();
        for (UUID player : new UUID[]{treasurer, subject}) {
            state.membership().invite(king, player, 1L);
            state.membership().acceptInvitation(player, kingdom.id(), 1L);
        }
        state.membership().setRole(king, treasurer, Role.TREASURER);
    }

    private Treasury treasury() {
        return state.economy().treasury(kingdom.id()).orElseThrow();
    }

    @Test
    void aFoundedKingdomOpensItsTreasury() {
        assertEquals(5, treasury().lastPayday(), "the first payday is the next day");
        assertEquals(0, treasury().funds());
        assertTrue(RolePermissions.defaults().has(Role.TREASURER, Permission.MANAGE_TREASURY));
        assertFalse(RolePermissions.defaults().has(Role.STEWARD, Permission.MANAGE_TREASURY));
    }

    @Test
    void theServerChecksTheRightToManageTheTreasury() {
        assertTrue(state.economy().adminTreasury(kingdom.id(), 100, 5).ok());
        assertEquals(Optional.of(KingdomError.NO_PERMISSION), state.payFromTreasury(subject, kingdom.id(), subject, 10, 5).failure());
        assertEquals(Optional.of(KingdomError.NO_PERMISSION), state.payFromTreasury(UUID.randomUUID(), kingdom.id(), subject, 10, 5).failure());
        assertEquals(Optional.of(KingdomError.NO_PERMISSION), state.setPollTax(subject, kingdom.id(), 50, 1_000).failure());
        assertEquals(Optional.of(KingdomError.NO_PERMISSION),
                state.setWage(subject, kingdom.id(), "smith", 50, EconomyRules.DEFAULT).failure());
        assertEquals(100, treasury().funds(), "refused payments take nothing");

        assertTrue(state.payFromTreasury(treasurer, kingdom.id(), subject, 10, 5).ok());
        assertTrue(state.payFromTreasury(king, kingdom.id(), subject, 10, 5).ok());
        assertEquals(20, state.economy().accounts().balance(subject, kingdom.id()));
        assertTrue(state.setPollTax(treasurer, kingdom.id(), 50, 1_000).ok());
        assertEquals(Optional.of(KingdomError.UNKNOWN_PROFESSION),
                state.setWage(treasurer, kingdom.id(), "dragon_tamer", 5, EconomyRules.DEFAULT).failure());

        // Only the king renames the currency.
        assertEquals(Optional.of(KingdomError.NO_PERMISSION), state.renameCurrency(treasurer, kingdom.id(), "Gold Crown").failure());
        assertTrue(state.renameCurrency(king, kingdom.id(), "Gold Crown").ok());
        state.membership().setRolePermission(king, Role.TREASURER, Permission.MANAGE_TREASURY, false);
        assertEquals(Optional.of(KingdomError.NO_PERMISSION), state.payFromTreasury(treasurer, kingdom.id(), subject, 10, 5).failure());
    }

    @Test
    void paydayTaxesSubjectsButNotTheKingAndPaysCitizens() {
        Citizen citizen = new Citizen(UUID.randomUUID(), kingdom.id(), 5, Gender.MALE, 0, 0L, new WorldPos(OVERWORLD, 8, 64, 8));
        state.citizens().add(citizen);
        state.setProfession(null, citizen.id(), "smith");
        state.economy().adminAccount(king, kingdom.id(), 100);
        state.economy().adminAccount(subject, kingdom.id(), 100);

        Economy.Payday payday = state.payday(kingdom.id(), 6, EconomyRules.DEFAULT);
        assertEquals(2, payday.taxes().size(), "the treasurer and the subject pay, the king does not");
        assertEquals(100, state.economy().accounts().balance(king, kingdom.id()));
        assertEquals(90, state.economy().accounts().balance(subject, kingdom.id()));
        assertEquals(10, treasury().debt(treasurer));
        // 10 of taxes came in, the smith got its 10.
        assertEquals(100, citizen.payPercent());
        assertEquals(0, treasury().funds());

        state.payday(kingdom.id(), 7, EconomyRules.DEFAULT);
        assertEquals(100, citizen.payPercent(), "the subject's tax paid the smith again");
        state.economy().adminAccount(subject, kingdom.id(), -80);
        state.payday(kingdom.id(), 8, EconomyRules.DEFAULT);
        assertEquals(0, citizen.payPercent(), "an empty treasury pays nothing");
        int unpaid = CitizenNeeds.targetMood(new CitizenNeeds.MoodInputs(20.0f, true, false, 0, citizen.payPercent()));
        int content = CitizenNeeds.targetMood(new CitizenNeeds.MoodInputs(20.0f, true, false, 0, 100));
        assertEquals(content + Payroll.UNPAID_MOOD, unpaid);
    }

    @Test
    void dissolvingAKingdomMakesItsCurrencyOld() {
        state.economy().adminAccount(subject, kingdom.id(), 30);
        state.dissolve(kingdom.id(), 1_000L);
        assertTrue(state.economy().treasury(kingdom.id()).isEmpty());
        assertEquals("Coin Realm", state.economy().retired().get(kingdom.id()).kingdomName());
        assertEquals(30, state.economy().accounts().balance(subject, kingdom.id()));
    }
}
