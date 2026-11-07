package io.github.sekerator.kingdomworld.core.companion;

import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.guild.Contract;
import io.github.sekerator.kingdomworld.core.guild.GuildRules;
import io.github.sekerator.kingdomworld.core.guild.GuildState;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.state.KingdomRules;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyStateTest {
    private static final long DAY = CitizenNeeds.TICKS_PER_DAY;
    private static final CompanionRules RULES = CompanionRules.DEFAULT;
    private static final GuildRules GUILD = GuildRules.DEFAULT;

    private KingdomWorldState world;
    private Economy economy;
    private GuildState guild;
    private PartyState parties;
    private Kingdom kingdom;
    private UUID leader;
    private UUID second;
    private UUID third;
    private long now;

    @BeforeEach
    void setUp() {
        world = new KingdomWorldState();
        economy = world.economy();
        guild = new GuildState();
        economy.setHeldElsewhere(guild::held);
        parties = new PartyState();
        CreateKingdomResult result = world.found(UUID.randomUUID(), "Party Realm", 0x336699, UUID.randomUUID(),
                new Capital("minecraft:overworld", 8, 64, 8), 0, KingdomRules.DEFAULT);
        kingdom = assertInstanceOf(CreateKingdomResult.Created.class, result).kingdom();
        economy.adminTreasury(kingdom.id(), 10_000, 0);
        leader = UUID.randomUUID();
        second = UUID.randomUUID();
        third = UUID.randomUUID();
        now = 2 * DAY;
        for (UUID player : List.of(leader, second, third)) {
            guild.join(player, kingdom.id(), now, 2, GUILD, world);
        }
    }

    private long money() {
        return economy.supply(kingdom.id());
    }

    private void form(UUID... members) {
        for (UUID member : members) {
            assertTrue(parties.invite(leader, member, true, true, now, RULES).ok());
            long party = parties.partyOf(leader).orElseThrow().id();
            assertTrue(parties.accept(member, party, now, RULES).ok());
        }
    }

    private Contract post(Contract.Kind kind, String target, int count, long reward) {
        GuildState.Made made = guild.post(null, new GuildState.Posting(kingdom.id(), Contract.Poster.KINGDOM, kingdom.id(), kind, target, Rank.F,
                count, reward, Rank.F, 2, true), now, 2, GUILD, world);
        assertTrue(made.outcome().ok(), "posting failed: " + made.outcome());
        return made.contract().orElseThrow();
    }

    @Test
    void invitationsJoiningLeavingAndTheLead() {
        assertEquals(Optional.of(KingdomError.NOT_IN_GUILD), parties.invite(leader, second, true, false, now, RULES).failure());
        assertEquals(Optional.of(KingdomError.TARGET_IS_SELF), parties.invite(leader, leader, true, true, now, RULES).failure());
        assertTrue(parties.invite(leader, second, true, true, now, RULES).ok(), "inviting founds the party");
        Party party = parties.partyOf(leader).orElseThrow();
        assertTrue(party.isLeader(leader));
        assertEquals(Optional.of(KingdomError.NOT_INVITED), parties.accept(third, party.id(), now, RULES).failure());
        assertEquals(Optional.of(KingdomError.NOT_INVITED), parties.accept(second, party.id(), now + RULES.inviteTicks, RULES).failure(),
                "an invitation runs out");
        assertTrue(parties.accept(second, party.id(), now, RULES).ok());
        assertEquals(Optional.of(KingdomError.NOT_PARTY_LEADER), parties.invite(second, third, true, true, now, RULES).failure());
        assertEquals(Optional.of(KingdomError.ALREADY_IN_PARTY), parties.invite(leader, second, true, true, now, RULES).failure());
        assertTrue(parties.transfer(leader, second).ok());
        assertTrue(party.isLeader(second));
        assertEquals(Optional.of(KingdomError.NOT_PARTY_LEADER), parties.kick(leader, second).failure());
        assertTrue(parties.kick(second, leader).ok());
        assertFalse(parties.together(leader, second));
        assertTrue(parties.leave(second).isEmpty(), "a party left empty is gone");
        assertTrue(parties.parties().isEmpty());
    }

    @Test
    void aPartyHasALimitAndALeaderWhoLeavesHandsTheLeadOn() {
        CompanionRules.Builder builder = new CompanionRules.Builder();
        builder.maxParty = 2;
        CompanionRules small = builder.build();
        assertTrue(parties.invite(leader, second, true, true, now, small).ok());
        assertTrue(parties.invite(leader, third, true, true, now, small).ok());
        long id = parties.partyOf(leader).orElseThrow().id();
        assertTrue(parties.accept(second, id, now, small).ok());
        assertEquals(Optional.of(KingdomError.PARTY_FULL), parties.accept(third, id, now, small).failure());
        assertTrue(parties.leave(leader).isPresent());
        assertTrue(parties.party(id).orElseThrow().isLeader(second));
    }

    @Test
    void killsOfEveryMemberCountAndTheRewardIsSplitWithoutLosingACoin() {
        form(second, third);
        Contract contract = post(Contract.Kind.HUNT, "minecraft:zombie", 3, 100);
        assertTrue(guild.take(leader, contract.id(), now, GUILD).ok());
        assertTrue(parties.adoptContract(leader, contract.id()));
        assertFalse(parties.adoptContract(second, contract.id()), "only the leader takes contracts for the party");
        long before = money();
        GuildState.Sharing sharing = parties.sharing();
        assertEquals(1, guild.recordKill(second, "minecraft:zombie", Rank.F, false, now, 2, world, sharing).size(), "a member's kill counts");
        assertEquals(1, guild.recordKill(third, "minecraft:zombie", Rank.F, false, now, 2, world, sharing).size());
        assertTrue(guild.recordKill(UUID.randomUUID(), "minecraft:zombie", Rank.F, false, now, 2, world, sharing).isEmpty(),
                "an outsider's kill does not");
        List<GuildState.Progress> done = guild.recordKill(leader, "minecraft:zombie", Rank.F, false, now, 2, world, sharing);
        assertTrue(done.get(0).finished());
        // 100 less 10% commission is 90, split in three; the commission goes to the branch.
        assertEquals(30, economy.accounts().balance(leader, kingdom.id()));
        assertEquals(30, economy.accounts().balance(second, kingdom.id()));
        assertEquals(30, economy.accounts().balance(third, kingdom.id()));
        assertEquals(3, done.get(0).payments().size());
        assertEquals(before, money(), "no money is made or lost");
        long points = contract.points();
        long given = guild.members().stream().filter(m -> List.of(leader, second, third).contains(m.player())).mapToLong(m -> m.points()).sum();
        // Kill points are given by the server apart; here only the contract's points, split.
        assertEquals(points, given);
    }

    @Test
    void sharesSetByTheLeaderAndTheOddCoinGoesToTheBiggestRemainder() {
        form(second);
        assertTrue(parties.setEvenSplit(leader, false).ok());
        assertTrue(parties.setShare(leader, leader, 2).ok());
        assertTrue(parties.setShare(leader, second, 1).ok());
        assertEquals(Optional.of(KingdomError.NOT_PARTY_LEADER), parties.setShare(second, second, 100).failure());
        Contract contract = post(Contract.Kind.HUNT, "minecraft:zombie", 1, 101);
        guild.take(leader, contract.id(), now, GUILD);
        parties.adoptContract(leader, contract.id());
        long before = money();
        guild.recordKill(second, "minecraft:zombie", Rank.F, false, now, 2, world, parties.sharing());
        // 101 less 10 (10%, rounded down) = 91: 2/3 is 60.67, 1/3 is 30.33; the odd coin goes to the leader.
        assertEquals(61, economy.accounts().balance(leader, kingdom.id()));
        assertEquals(30, economy.accounts().balance(second, kingdom.id()));
        assertEquals(before, money());
    }

    @Test
    void anyMemberHandsInAPartOfAGathering() {
        form(second);
        Contract contract = post(Contract.Kind.GATHER, "minecraft:oak_log", 10, 50);
        guild.take(leader, contract.id(), now, GUILD);
        parties.adoptContract(leader, contract.id());
        GuildState.Sharing sharing = parties.sharing();
        assertEquals(Optional.of(KingdomError.NOT_YOUR_CONTRACT), guild.checkHandIn(third, contract.id(), 10, now, sharing).failure());
        assertTrue(guild.checkHandIn(second, contract.id(), 4, now, sharing).ok(), "a member may hand in a part");
        GuildState.Progress part = guild.handIn(second, contract.id(), 4, now, 2, world, sharing).orElseThrow();
        assertFalse(part.finished());
        assertEquals(4, part.contract().progress());
        GuildState.Progress rest = guild.handIn(leader, contract.id(), 6, now, 2, world, sharing).orElseThrow();
        assertTrue(rest.finished());
        assertEquals(45, economy.accounts().balance(leader, kingdom.id()) + economy.accounts().balance(second, kingdom.id()));
    }

    @Test
    void contractsLeaveThePartyWithTheirHolder() {
        form(second);
        Contract contract = post(Contract.Kind.HUNT, "minecraft:zombie", 2, 10);
        guild.take(leader, contract.id(), now, GUILD);
        parties.adoptContract(leader, contract.id());
        parties.leave(leader);
        assertTrue(parties.sharing().sharedWith(second).isEmpty());
        assertTrue(guild.recordKill(second, "minecraft:zombie", Rank.F, false, now, 2, world, parties.sharing()).isEmpty());
        assertEquals(1, guild.recordKill(leader, "minecraft:zombie", Rank.F, false, now, 2, world, parties.sharing()).size(),
                "the contract stays with whoever took it");
    }
}
