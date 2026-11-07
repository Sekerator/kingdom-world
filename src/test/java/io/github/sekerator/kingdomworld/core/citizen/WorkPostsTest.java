package io.github.sekerator.kingdomworld.core.citizen;

import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.state.KingdomRules;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Work posts: seats, professions, rights, hiring, the record of production, the carriers' plans and saving.
 */
class WorkPostsTest {
    private static final String OVERWORLD = "minecraft:overworld";

    private KingdomWorldState state;
    private Citizens citizens;
    private UUID king;
    private UUID realm;

    private static WorldPos pos(int x, int z) {
        return new WorldPos(OVERWORLD, x, 64, z);
    }

    @BeforeEach
    void setUp() {
        state = new KingdomWorldState();
        citizens = state.citizens();
        king = UUID.randomUUID();
        CreateKingdomResult result = state.found(UUID.randomUUID(), "Postland", 0xFFFFFF, king,
                new Capital(OVERWORLD, 8, 64, 8), 0L, KingdomRules.DEFAULT);
        realm = ((CreateKingdomResult.Created) result).kingdom().id();
    }

    private Citizen citizen(String profession) {
        Citizen citizen = new Citizen(UUID.randomUUID(), realm, 1, Gender.FEMALE, 0, 0L, pos(1, 1));
        citizens.add(citizen);
        citizens.setProfession(citizen, profession);
        return citizen;
    }

    @Test
    void postsTakeWorkersOfTheirProfessionUpToTheSeats() {
        assertTrue(citizens.addSite(pos(5, 5), SiteKinds.WORK_POST, realm));
        WorkPost post = citizens.posts().get(pos(5, 5)).orElseThrow();
        assertEquals(WorkStatus.NO_PROFESSION, post.status());
        assertTrue(state.setPostProfession(null, pos(5, 5), "miner").ok());
        assertTrue(state.setPostSeats(null, pos(5, 5), 2, 4).ok());

        Citizen a = citizen("miner");
        Citizen b = citizen("miner");
        Citizen c = citizen("miner");
        Citizen farmer = citizen("farmer");
        assertEquals(Optional.of(pos(5, 5)), citizens.nearestFreeWorkplace(a, pos(0, 0)));
        assertTrue(citizens.assignWorkplace(a, pos(5, 5)));
        assertTrue(citizens.assignWorkplace(b, pos(5, 5)));
        assertFalse(citizens.assignWorkplace(c, pos(5, 5)), "the post is full");
        assertTrue(citizens.nearestFreeWorkplace(c, pos(0, 0)).isEmpty());
        assertFalse(citizens.assignWorkplace(farmer, pos(5, 5)), "a farmer does not work at a miner's post");
        assertEquals(2, post.workers().size());
        assertEquals(pos(5, 5), a.workplace());

        // Fewer seats send away those who came last.
        assertTrue(state.setPostSeats(null, pos(5, 5), 1, 4).ok());
        assertEquals(List.copyOf(post.workers()), List.of(a.id()));
        assertNull(b.workplace());

        // Another profession sends away everybody of the old one.
        assertTrue(state.setPostProfession(null, pos(5, 5), "fisher").ok());
        assertTrue(post.workers().isEmpty());
        assertNull(a.workplace());
        assertEquals(KingdomError.UNCHANGED, state.setPostProfession(null, pos(5, 5), "fisher").error());
        assertEquals(KingdomError.UNKNOWN_PROFESSION, state.setPostProfession(null, pos(5, 5), "nobody").error());
    }

    @Test
    void farmersWorkAtPostsAndComposters() {
        citizens.addSite(pos(3, 3), SiteKinds.COMPOSTER, realm);
        citizens.addSite(pos(9, 9), SiteKinds.WORK_POST, realm);
        state.setPostProfession(null, pos(9, 9), "farmer");
        Citizen near = citizen("farmer");
        assertEquals(Optional.of(pos(3, 3)), citizens.nearestFreeWorkplace(near, pos(2, 2)));
        assertEquals(Optional.of(pos(9, 9)), citizens.nearestFreeWorkplace(near, pos(10, 10)));
        assertTrue(citizens.assignWorkplace(near, pos(9, 9)));
        assertTrue(citizens.assignWorkplace(near, pos(3, 3)), "a farmer may move to a composter");
        assertTrue(citizens.posts().get(pos(9, 9)).orElseThrow().workers().isEmpty());
        assertEquals(near.id(), citizens.sites().get(pos(3, 3)).orElseThrow().occupant());
    }

    @Test
    void settingsNeedTheRightAndStayInRange() {
        citizens.addSite(pos(8, 8), SiteKinds.WORK_POST, realm);
        UUID stranger = UUID.randomUUID();
        assertEquals(KingdomError.NO_PERMISSION, state.setPostProfession(stranger, pos(8, 8), "miner").error());
        assertTrue(state.setPostProfession(king, pos(8, 8), "herder").ok());
        assertEquals(KingdomError.VALUE_OUT_OF_RANGE, state.setPostSeats(king, pos(8, 8), 5, 4).error());
        assertEquals(KingdomError.VALUE_OUT_OF_RANGE, state.setPostRadius(king, pos(8, 8), 0, 16).error());
        assertEquals(KingdomError.VALUE_OUT_OF_RANGE, state.setPostHerdSize(king, pos(8, 8), 1, 12).error());
        assertTrue(state.setPostHerdSize(king, pos(8, 8), 7, 12).ok());
        assertTrue(state.setPostAnimal(king, pos(8, 8), "minecraft:cow").ok());
        assertEquals("minecraft:cow", citizens.posts().get(pos(8, 8)).orElseThrow().animal());
        // The land is 3x3 chunks around chunk 0: x and z from -16 to 31; the post at 8 reaches 24 blocks west at most.
        assertTrue(state.setPostRadius(king, pos(8, 8), 16, 32).ok());
        assertEquals(KingdomError.ZONE_OUTSIDE_LAND, state.setPostRadius(king, pos(8, 8), 25, 32).error());
        assertTrue(state.setPostRadius(king, pos(8, 8), 2, 32).ok(), "shrinking is always allowed");
        assertEquals(KingdomError.UNKNOWN_POST, state.setPostSeats(king, pos(100, 100), 2, 4).error());
    }

    @Test
    void hiringTakesTheNearestCitizenWithoutWork() {
        citizens.addSite(pos(8, 8), SiteKinds.WORK_POST, realm);
        assertEquals(KingdomError.NOT_A_POST_PROFESSION, state.hire(king, pos(8, 8)).outcome().error());
        state.setPostProfession(king, pos(8, 8), "lumberjack");
        assertEquals(KingdomError.NO_UNEMPLOYED, state.hire(king, pos(8, 8)).outcome().error());
        Citizen idle = citizen("unemployed");
        KingdomWorldState.Hiring hiring = state.hire(king, pos(8, 8));
        assertTrue(hiring.outcome().ok());
        assertEquals(idle, hiring.citizen().orElseThrow());
        assertEquals("lumberjack", idle.professionId());
        assertEquals(pos(8, 8), idle.workplace());
        citizen("unemployed");
        assertEquals(KingdomError.NO_FREE_SEAT, state.hire(king, pos(8, 8)).outcome().error());
        assertEquals(KingdomError.NO_PERMISSION, state.dismiss(UUID.randomUUID(), pos(8, 8), idle.id()).error());
        assertTrue(state.dismiss(king, pos(8, 8), idle.id()).ok());
        assertEquals("unemployed", idle.professionId());
        assertNull(idle.workplace());
        assertEquals(KingdomError.NOT_A_WORKER, state.dismiss(king, pos(8, 8), idle.id()).error());
    }

    @Test
    void lostPostsAndDeathsFreeTheSeats() {
        citizens.addSite(pos(8, 8), SiteKinds.WORK_POST, realm);
        state.setPostProfession(null, pos(8, 8), "carrier");
        Citizen carrier = citizen("carrier");
        assertTrue(citizens.assignWorkplace(carrier, pos(8, 8)));
        citizens.die(carrier.id(), CauseOfDeath.MINE, "", 0L, pos(8, 8));
        assertTrue(citizens.posts().get(pos(8, 8)).orElseThrow().workers().isEmpty());

        Citizen other = citizen("carrier");
        assertTrue(citizens.assignWorkplace(other, pos(8, 8)));
        citizens.removeSite(pos(8, 8));
        assertNull(other.workplace());
        assertTrue(citizens.posts().get(pos(8, 8)).isEmpty());

        // A post replaced by another kind of place disappears with its workers.
        citizens.addSite(pos(4, 4), SiteKinds.WORK_POST, realm);
        state.setPostProfession(null, pos(4, 4), "carrier");
        assertTrue(citizens.assignWorkplace(other, pos(4, 4)));
        citizens.addSite(pos(4, 4), SiteKinds.BED, realm);
        assertNull(other.workplace());
        assertTrue(citizens.posts().get(pos(4, 4)).isEmpty());
    }

    @Test
    void changingTheProfessionLeavesThePost() {
        citizens.addSite(pos(8, 8), SiteKinds.WORK_POST, realm);
        state.setPostProfession(null, pos(8, 8), "fisher");
        Citizen fisher = citizen("fisher");
        citizens.assignWorkplace(fisher, pos(8, 8));
        fisher.setWorkStatus(WorkStatus.NO_WATER);
        citizens.setProfession(fisher, "unemployed");
        assertNull(fisher.workplace());
        assertEquals(WorkStatus.NONE, fisher.workStatus());
        assertTrue(citizens.posts().get(pos(8, 8)).orElseThrow().workers().isEmpty());
    }

    @Test
    void postsAndWorkersAreRestored() {
        WorkPost post = new WorkPost(pos(8, 8), realm, 5, 6);
        post.restore("miner", 2, "", WorkStatus.NO_TOOL, new PostYield(Map.of("minecraft:cobblestone", 100.0), Map.of(), 100.0, true),
                Map.of("minecraft:cobblestone", 0.5), 3L, 17L, true);
        Citizen worker = new Citizen(UUID.randomUUID(), realm, 1, Gender.MALE, 0, 0L, pos(1, 1));
        worker.restore(20.0f, CitizenState.HEALTHY, "miner", null, pos(8, 8), 20.0f, 50, 0, 0, "", 0.0, WorkStatus.NO_TOOL);
        Citizen wrong = new Citizen(UUID.randomUUID(), realm, 2, Gender.MALE, 0, 0L, pos(1, 1));
        wrong.restore(20.0f, CitizenState.HEALTHY, "fisher", null, pos(8, 8), 20.0f, 50, 0, 0, "", 0.0, WorkStatus.NONE);
        Citizens restored = new Citizens();
        restored.restore(realm, List.of(new Site(pos(8, 8), SiteKinds.WORK_POST, realm, null)), List.of(post),
                List.of(worker, wrong), 0L, List.of(), List.of(new ProductionLedger.Day(3L, Map.of("miner", Map.of("minecraft:cobblestone", 40L)))));
        WorkPost back = restored.posts().get(pos(8, 8)).orElseThrow();
        assertEquals(List.of(worker.id()), List.copyOf(back.workers()));
        assertNull(wrong.workplace(), "a worker of another profession loses the seat");
        assertEquals(17L, back.producedOn(3L));
        assertTrue(back.hasContainer());
        assertEquals(0L, back.producedOn(4L));
        assertEquals(40L, restored.ledger().today(realm, 3L).total("miner"));
        assertEquals(40L, restored.ledger().yesterday(realm, 4L).total("miner"));

        // A post place saved without settings gets fresh ones; a post without a place is refused.
        Citizens bare = new Citizens();
        bare.restore(realm, List.of(new Site(pos(2, 2), SiteKinds.WORK_POST, realm, null)), List.of(), List.of(), 0L, List.of(), List.of());
        assertEquals(WorkStatus.NO_PROFESSION, bare.posts().get(pos(2, 2)).orElseThrow().status());
        assertThrows(IllegalArgumentException.class, () -> new Citizens().restore(realm, List.of(), List.of(new WorkPost(pos(1, 1), realm, 3, 4)),
                List.of(), 0L, List.of(), List.of()));
    }

    // ---------------------------------------------------------------- production

    @Test
    void theLedgerKeepsTodayAndYesterday() {
        ProductionLedger ledger = new ProductionLedger();
        ledger.record(realm, 10L, "miner", "minecraft:cobblestone", 30);
        ledger.record(realm, 10L, "miner", "minecraft:coal", 2);
        ledger.record(realm, 10L, "fisher", "minecraft:cod", 4);
        assertEquals(32L, ledger.today(realm, 10L).total("miner"));
        assertEquals(36L, ledger.today(realm, 10L).total());
        ledger.record(realm, 11L, "miner", "minecraft:cobblestone", 5);
        assertEquals(5L, ledger.today(realm, 11L).total());
        assertEquals(36L, ledger.yesterday(realm, 11L).total());
        ledger.record(realm, 10L, "fisher", "minecraft:cod", 1);
        assertEquals(37L, ledger.yesterday(realm, 11L).total(), "a late entry for yesterday still counts");
        ledger.record(realm, 14L, "miner", "minecraft:stone", 1);
        assertEquals(0L, ledger.yesterday(realm, 14L).total(), "days without work leave nothing behind");
        assertEquals(0L, ledger.today(UUID.randomUUID(), 14L).total());
        for (int i = 0; i < ProductionLedger.MAX_ITEMS + 5; i++) {
            ledger.record(realm, 14L, "fisher", "mod:fish_" + i, 1);
        }
        Map<String, Long> fish = ledger.today(realm, 14L).counts().get("fisher");
        assertEquals(ProductionLedger.MAX_ITEMS + 1, fish.size());
        assertEquals(5L, fish.get(ProductionLedger.OTHER));
    }

    // ---------------------------------------------------------------- carriers

    @Test
    void carriersEmptyTheFullestContainerIntoTheNearestStorehouse() {
        WorldPos home = pos(0, 0);
        CarrierPlanner.Stock smallPile = new CarrierPlanner.Stock(pos(5, 0), 10, 20, 27);
        CarrierPlanner.Stock bigPile = new CarrierPlanner.Stock(pos(-5, 0), 300, 2, 27);
        CarrierPlanner.Stock farPile = new CarrierPlanner.Stock(pos(500, 0), 1000, 0, 27);
        CarrierPlanner.Stock near = new CarrierPlanner.Stock(pos(-8, 0), 0, 54, 54);
        CarrierPlanner.Stock far = new CarrierPlanner.Stock(pos(30, 0), 0, 54, 54);
        CarrierPlanner.Task task = CarrierPlanner.next(home, List.of(smallPile, bigPile, farPile), List.of(far, near), List.of(), 576, 48)
                .orElseThrow();
        assertEquals(CarrierPlanner.Kind.COLLECT, task.kind());
        assertEquals(bigPile.pos(), task.from());
        assertEquals(near.pos(), task.to());
        assertEquals(300, task.amount());

        CarrierPlanner.Stock full = new CarrierPlanner.Stock(pos(-8, 0), 3000, 0, 54);
        assertTrue(CarrierPlanner.next(home, List.of(bigPile), List.of(full), List.of(), 576, 48)
                .map(t -> t.kind() != CarrierPlanner.Kind.COLLECT).orElse(true), "nothing is collected into a full store");
    }

    @Test
    void carriersSupplyPostsAndBalanceStorehouses() {
        WorldPos home = pos(0, 0);
        CarrierPlanner.Request torches = new CarrierPlanner.Request(pos(3, 3), "minecraft:torch", 16, pos(-3, -3));
        CarrierPlanner.Task supply = CarrierPlanner.next(home, List.of(), List.of(), List.of(torches), 576, 48).orElseThrow();
        assertEquals(CarrierPlanner.Kind.SUPPLY, supply.kind());
        assertEquals("minecraft:torch", supply.item());
        assertEquals(pos(-3, -3), supply.from());

        CarrierPlanner.Stock crowded = new CarrierPlanner.Stock(pos(-20, 0), 3000, 2, 54);
        CarrierPlanner.Stock empty = new CarrierPlanner.Stock(pos(20, 0), 0, 54, 54);
        CarrierPlanner.Stock close = new CarrierPlanner.Stock(pos(-18, 0), 0, 54, 54);
        CarrierPlanner.Task balance = CarrierPlanner.next(home, List.of(), List.of(crowded, close, empty), List.of(), 576, 48).orElseThrow();
        assertEquals(CarrierPlanner.Kind.BALANCE, balance.kind());
        assertEquals(crowded.pos(), balance.from());
        assertEquals(empty.pos(), balance.to(), "storehouses next to each other are not balanced");
        assertTrue(CarrierPlanner.next(home, List.of(), List.of(close, empty), List.of(), 576, 48).isEmpty());
    }

    @Test
    void carriersFarFromPlayersShareTheirCapacity() {
        CarrierPlanner.Stock a = new CarrierPlanner.Stock(pos(1, 0), 100, 0, 27);
        CarrierPlanner.Stock b = new CarrierPlanner.Stock(pos(2, 0), 40, 0, 27);
        CarrierPlanner.Stock store = new CarrierPlanner.Stock(pos(3, 0), 0, 54, 54);
        List<CarrierPlanner.Task> tasks = CarrierPlanner.plan(List.of(b, a), List.of(store), 120);
        assertEquals(2, tasks.size());
        assertEquals(a.pos(), tasks.get(0).from());
        assertEquals(100, tasks.get(0).amount());
        assertEquals(20, tasks.get(1).amount());
        assertTrue(CarrierPlanner.plan(List.of(a), List.of(), 120).isEmpty());
        assertTrue(CarrierPlanner.plan(List.of(a), List.of(store), 0).isEmpty());
    }
}
