package io.github.sekerator.kingdomworld.common.gametest;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmySettings;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.diplomacy.DiplomacyActions;
import io.github.sekerator.kingdomworld.common.diplomacy.DiplomacySettings;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.land.LandEvents;
import io.github.sekerator.kingdomworld.common.npc.NpcRulers;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.siege.SiegeNbt;
import io.github.sekerator.kingdomworld.common.siege.SiegeSavedData;
import io.github.sekerator.kingdomworld.common.siege.SiegeSettings;
import io.github.sekerator.kingdomworld.common.siege.SiegeWork;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenRegistry;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.ConstructionBook;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyState;
import io.github.sekerator.kingdomworld.core.diplomacy.PeaceTerms;
import io.github.sekerator.kingdomworld.core.diplomacy.Proposal;
import io.github.sekerator.kingdomworld.core.diplomacy.Relations;
import io.github.sekerator.kingdomworld.core.diplomacy.War;
import io.github.sekerator.kingdomworld.core.diplomacy.WarGoal;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.TreasuryJournal;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.land.BorderKind;
import io.github.sekerator.kingdomworld.core.land.ProvinceKind;
import io.github.sekerator.kingdomworld.core.land.Tier;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcNames;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
import io.github.sekerator.kingdomworld.core.npc.RulerCharacter;
import io.github.sekerator.kingdomworld.core.npc.RulerMemory;
import io.github.sekerator.kingdomworld.core.npc.Settlement;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.siege.Conquest;
import io.github.sekerator.kingdomworld.core.siege.Hold;
import io.github.sekerator.kingdomworld.core.siege.ProvinceHolds;
import io.github.sekerator.kingdomworld.core.siege.SiegeRules;
import io.github.sekerator.kingdomworld.core.siege.SiegeState;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.Province;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Game tests of the war by provinces against the states of the world: an annexation of a state of twenty-five provinces with
 * its settlements, buildings and citizens in short seconds; a restart in the middle of the journal of conquests that neither
 * loses nor doubles anything; a province held far from players at the square of its settlement after a battle with its
 * garrison and an empty one at its boundary stone, both handed over by the peace; the cession of a province by a ruler of the
 * world for its price and its refusal to one it distrusts; and the disputes of a ruler with the holders of provinces next to
 * its own. The states lie as data some way from the place of the test, on provinces whose numbers are far above any a plan of
 * the world gives; every test gives them up.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProvinceWarGameTests {
    private static final String EMPTY = "empty";
    /** The worst second of the journal of conquests allowed, in nanoseconds. */
    private static final long WORST_NANOS = 15_000_000L;
    /** Seconds of the journal a test runs at most. */
    private static final int MOST_SECONDS = 1_000;

    /** The annexed state: five by five provinces of six chunks across, its heart in the middle. */
    private static final int ANNEX_SIDE = 5;
    private static final int ANNEX_WIDE = 6;
    private static final int ANNEX_FIRST = 65_401;
    private static final int ANNEX_HEART = ANNEX_FIRST + ANNEX_SIDE * ANNEX_SIDE / 2;

    /** The state of the restart: its heart and three provinces it hands over, eight chunks across. */
    private static final int RESTART_WIDE = 8;
    private static final int RESTART_HEART = 65_431;
    private static final int[] RESTART_GIVEN = {65_432, 65_433, 65_434};

    /** The state of the holds: its heart, a province where people live and an empty one, eight chunks across. */
    private static final int HOLD_WIDE = 8;
    private static final int HOLD_HEART = 65_441;
    private static final int HOLD_SETTLED = 65_442;
    private static final int HOLD_EMPTY = 65_443;

    /** The state of the cession, west of the asker: its heart, its core and its edge next to the claims of the asker. */
    private static final int CEDE_WIDE = 12;
    private static final int CEDE_HEART = 65_451;
    private static final int CEDE_CORE = 65_452;
    private static final int CEDE_EDGE = 65_453;

    /** The states of the disputes: the heart and the edge of one, a province of its neighbour, and wild land. */
    private static final int DISPUTE_WIDE = 12;
    private static final int DISPUTE_HEART = 65_461;
    private static final int DISPUTE_EDGE = 65_462;
    private static final int DISPUTE_THEIRS = 65_463;
    private static final int DISPUTE_WILD = 65_464;

    private ProvinceWarGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    /**
     * The test's own kingdom of players on its place.
     */
    private static Kingdom home(GameTestHelper helper, String name) {
        GatheringGameTests.clear(helper.getLevel(), WarGameTests.from(helper), WarGameTests.to(helper));
        return CitizenGameTests.found(helper, name, UUID.randomUUID());
    }

    /**
     * A kingdom without land and without a ruler of the world.
     */
    private static Kingdom landless(MinecraftServer server, String name) {
        for (Kingdom stale : List.copyOf(KingdomWorldApi.kingdoms(server))) {
            if (stale.name().equals(name)) {
                forget(server, stale.id());
            }
        }
        CreateKingdomResult created = KingdomWorldApi.createKingdom(server, name, UUID.randomUUID());
        if (created instanceof CreateKingdomResult.Created result) {
            return result.kingdom();
        }
        throw new IllegalStateException("Could not create '" + name + "': " + created);
    }

    /**
     * A state of the world: a kingdom led by a ruler of the world of a character, its land still to be given.
     */
    private static Kingdom state(MinecraftServer server, String name, RulerCharacter character) {
        for (Kingdom stale : List.copyOf(KingdomWorldApi.kingdoms(server))) {
            if (stale.name().equals(name)) {
                forget(server, stale.id());
            }
        }
        CreateKingdomResult created = KingdomWorldApi.createKingdom(server, name, null);
        if (!(created instanceof CreateKingdomResult.Created result)) {
            throw new IllegalStateException("The state was not created: " + created);
        }
        Kingdom kingdom = result.kingdom();
        NpcWork.state(server).putKingdom(new NpcKingdom(kingdom.id(), -1, NpcLevel.SMALL, new NpcNames.Name(name, name), character,
                List.of(RulerCharacter.Trait.PATIENT), 4321, Gender.FEMALE, List.of(), KingdomWorldApi.gameTime(server), TreasuryActions.day(server)));
        return kingdom;
    }

    /**
     * Forgets a kingdom of the tests: its diplomacy, sieges, settlements and ruler, then the kingdom itself.
     */
    private static void forget(MinecraftServer server, UUID kingdomId) {
        WarWork.state(server).forget(kingdomId, KingdomWorldApi.gameTime(server));
        SiegeWork.state(server).forget(kingdomId);
        NpcWork.state(server).removeSettlementsOf(kingdomId);
        NpcWork.state(server).removeKingdom(kingdomId);
        KingdomWorldApi.dissolveKingdom(server, kingdomId);
    }

    /**
     * A province of the tests, {@code wide} chunks across from a chunk, its middle the middle of that square.
     *
     * @param seat its main settlement, 0 for none
     */
    private static void province(TerritoryIndex territory, int id, int chunkX, int chunkZ, int wide, List<Integer> near, ProvinceKind kind,
                                 long seat) {
        ProvinceMap map = territory.provinceMap().orElseThrow();
        if (map.exists(id)) {
            map.setHold(id, null);
            territory.clearProvince(id);
        }
        List<BorderKind> borders = new ArrayList<>();
        for (int i = 0; i < near.size(); i++) {
            borders.add(BorderKind.values()[0]);
        }
        map.define(new Province(id, middle(chunkX, wide), middle(chunkZ, wide), near, borders, kind, id, seat));
        territory.fillProvince(id, chunkX, chunkZ, chunkX + wide - 1, chunkZ + wide - 1);
    }

    /**
     * The block in the middle of a row of chunks.
     */
    private static int middle(int chunk, int wide) {
        return chunk * 16 + wide * 8;
    }

    /**
     * A village of ten people with one militiaman, founded as the plan founds it.
     */
    private static Settlement village(MinecraftServer server, UUID kingdomId, int province, int x, int z, String latin, String cyrillic) {
        Settlement settlement = NpcWork.state(server).addSettlement(kingdomId, province, 0, Tier.VILLAGE, x, z, province,
                new NpcNames.Name(latin, cyrillic));
        settlement.found(12, 10, 50);
        return settlement;
    }

    /**
     * A building of a kingdom as data, a small house at a place.
     */
    private static long building(ConstructionBook book, UUID kingdomId, String dimension, int x, int z, long now) {
        long id = book.nextBuildingId();
        book.putBuilding(new Building(id, kingdomId, "kingdomworld:test_province_war", "house", "test", "", 1, Materials.NONE,
                new WorldPos(dimension, x, 64, z), new Placement(0, false), new Box(x, 64, z, x + 6, 70, z + 6), 0L, now, true, 100, now, false,
                new BitSet()));
        book.restoreCounters(0, id + 1);
        return id;
    }

    /**
     * A citizen of a kingdom living at a place, without a bed.
     */
    private static UUID citizen(MinecraftServer server, UUID kingdomId, String dimension, int x, int z) {
        return CitizenLife.arrive(server, kingdomId, new WorldPos(dimension, x, 65, z), false, false).map(Citizen::id)
                .orElseThrow(() -> new IllegalStateException("No citizen arrived"));
    }

    private static void giveUp(MinecraftServer server, int... ids) {
        TerritoryIndex territory = LandEvents.attach(server);
        ProvinceMap map = territory.provinceMap().orElseThrow();
        for (int id : ids) {
            if (map.exists(id)) {
                map.setHold(id, null);
                territory.clearProvince(id);
            }
        }
    }

    private static ArmySquad squad(MinecraftServer server, Kingdom kingdom, List<Citizen> soldiers) {
        List<UUID> members = new ArrayList<>();
        for (Citizen soldier : soldiers) {
            members.add(soldier.id());
        }
        ArmyWork.change(server, "form a squad for a check", state -> state.formSquad(null, kingdom.id(), members, ArmySettings.rules(),
                KingdomWorldApi.state(server)).outcome());
        return ArmyWork.state(server).squadsOf(kingdom.id()).stream().filter(s -> s.members().contains(members.get(0))).findFirst()
                .orElseThrow(() -> new IllegalStateException("No squad was formed"));
    }

    private static boolean pending(MinecraftServer server, long conquestId) {
        return SiegeWork.state(server).conquests().stream().anyMatch(conquest -> conquest.id() == conquestId);
    }

    private static Conquest conquest(MinecraftServer server, long conquestId) {
        return SiegeWork.state(server).conquests().stream().filter(conquest -> conquest.id() == conquestId).findFirst()
                .orElseThrow(() -> new IllegalStateException("The conquest #" + conquestId + " is not in the journal"));
    }

    private static int heldBy(NpcState npc, List<Integer> settlements, UUID kingdomId) {
        int count = 0;
        for (int id : settlements) {
            if (npc.settlement(id).map(settlement -> settlement.kingdomId().equals(kingdomId)).orElse(false)) {
                count++;
            }
        }
        return count;
    }

    private static boolean ofKingdom(CitizenRegistry registry, UUID citizenId, UUID kingdomId) {
        return registry.get(citizenId).map(citizen -> citizen.kingdomId().equals(kingdomId)).orElse(false);
    }

    private static boolean ofKingdom(ConstructionBook book, long buildingId, UUID kingdomId) {
        return book.building(buildingId).map(building -> building.kingdomId().equals(kingdomId)).orElse(false);
    }

    /**
     * The open offer of one kingdom to another to cede a province.
     */
    private static Proposal cession(DiplomacyState diplomacy, UUID from, UUID to, int province) {
        return diplomacy.proposals().stream().filter(p -> p.open() && p.kind() == Proposal.Kind.CEDE && p.from().equals(from) && p.to().equals(to)
                && p.province() == province).findFirst().orElseThrow(() -> new IllegalStateException("No offer to cede " + province));
    }

    /**
     * The rulers of the world answer until the offer is answered (two offers a second, older ones of the world first).
     */
    private static void answered(MinecraftServer server, long proposalId) {
        for (int i = 0; i < 20 && WarWork.state(server).proposal(proposalId).map(Proposal::open).orElse(false); i++) {
            WarWork.forceAnswers(server);
        }
    }

    private static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.2f", nanos / 1_000_000.0);
    }

    // ---------------------------------------------------------------- annexation

    /**
     * A state of twenty-five provinces with a village, two buildings and two citizens in each and its heart claimed is annexed:
     * every second of the journal is measured, and every province, settlement, building and citizen ends with the winner.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_province_war_annex", timeoutTicks = 200)
    public static void anAnnexationOfAStateOfTwentyFiveProvincesTakesShortSeconds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        String dimension = Dimensions.id(level);
        Kingdom winner = home(helper, "GameTest Province Annexer");
        Kingdom loser = state(server, "GameTest Annexed State", RulerCharacter.BUILDER);
        TerritoryIndex territory = LandEvents.attach(server);
        ProvinceMap map = territory.provinceMap().orElseThrow();
        NpcState npc = NpcWork.state(server);
        KingdomWorldState world = KingdomWorldApi.state(server);
        ConstructionBook book = world.construction();
        CitizenRegistry registry = world.citizens().registry();
        int[] ids = new int[ANNEX_SIDE * ANNEX_SIDE];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = ANNEX_FIRST + i;
        }
        List<Integer> settlements = new ArrayList<>();
        List<Long> buildings = new ArrayList<>();
        List<UUID> citizens = new ArrayList<>();
        try {
            ChunkPos origin = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
            int baseX = origin.x + 40;
            int baseZ = origin.z + 40;
            long now = KingdomWorldApi.gameTime(server);
            List<Long> heart = new ArrayList<>();
            for (int i = 0; i < ids.length; i++) {
                int column = i % ANNEX_SIDE;
                int row = i / ANNEX_SIDE;
                int chunkX = baseX + column * ANNEX_WIDE;
                int chunkZ = baseZ + row * ANNEX_WIDE;
                int x = middle(chunkX, ANNEX_WIDE);
                int z = middle(chunkZ, ANNEX_WIDE);
                List<Integer> near = new ArrayList<>();
                if (column > 0) {
                    near.add(ids[i - 1]);
                }
                if (column < ANNEX_SIDE - 1) {
                    near.add(ids[i + 1]);
                }
                if (row > 0) {
                    near.add(ids[i - ANNEX_SIDE]);
                }
                if (row < ANNEX_SIDE - 1) {
                    near.add(ids[i + ANNEX_SIDE]);
                }
                Settlement seat = village(server, loser.id(), ids[i], x, z, "Annexholm " + i, "Аннексхольм " + i);
                settlements.add(seat.id());
                province(territory, ids[i], chunkX, chunkZ, ANNEX_WIDE, near, ids[i] == ANNEX_HEART ? ProvinceKind.HEART : ProvinceKind.CORE,
                        seat.id());
                territory.transferProvince(ids[i], loser.id(), 0);
                for (int b = 0; b < 2; b++) {
                    buildings.add(building(book, loser.id(), dimension, x + 16 + b * 12, z + 16, now));
                    citizens.add(citizen(server, loser.id(), dimension, x - 16 - b * 4, z - 16));
                }
                if (ids[i] == ANNEX_HEART) {
                    for (int dx = 0; dx < ANNEX_WIDE; dx++) {
                        for (int dz = 0; dz < ANNEX_WIDE; dz++) {
                            heart.add(ChunkKey.of(chunkX + dx, chunkZ + dz));
                        }
                    }
                }
            }
            // The capital stands in the middle of the heart, whose chunks are all claimed: the claims go after the provinces.
            int heartColumn = (ANNEX_HEART - ANNEX_FIRST) % ANNEX_SIDE;
            int heartRow = (ANNEX_HEART - ANNEX_FIRST) / ANNEX_SIDE;
            Capital capital = new Capital(dimension, middle(baseX + heartColumn * ANNEX_WIDE, ANNEX_WIDE), 64,
                    middle(baseZ + heartRow * ANNEX_WIDE, ANNEX_WIDE));
            territory.establish(loser.id(), capital, heart);
            int registered = registry.size();
            int known = npc.settlements().size();

            Conquest annex = SiegeWork.state(server).addConquest(Conquest.Kind.ANNEX, 0, winner.id(), loser.id(), dimension, List.of(), 0, now);
            long worst = 0;
            long total = 0;
            int seconds = 0;
            while (seconds < MOST_SECONDS && pending(server, annex.id())) {
                long start = System.nanoTime();
                SiegeWork.forceConquestSecond(server);
                long took = System.nanoTime() - start;
                worst = Math.max(worst, took);
                total += took;
                seconds++;
            }
            SiegeWork.LOGGER.info("Annexation of a state of {} provinces, {} settlements, {} buildings and {} citizens: {} second(s) of the "
                    + "journal, average {} ms, worst {} ms", ids.length, settlements.size(), buildings.size(), citizens.size(), seconds,
                    millis(seconds == 0 ? 0 : total / seconds), millis(worst));
            helper.assertTrue(!pending(server, annex.id()), "The annexation was not over in " + seconds + " seconds");
            helper.assertTrue(worst <= WORST_NANOS, "A second of the annexation took " + millis(worst) + " ms");
            helper.assertTrue(KingdomWorldApi.kingdom(server, loser.id()).isEmpty(), "The annexed state is still there");
            for (int id : ids) {
                helper.assertTrue(winner.id().equals(map.owner(id)), "The province " + id + " did not go to the winner");
            }
            helper.assertTrue(heldBy(npc, settlements, winner.id()) == settlements.size() && npc.settlements().size() == known,
                    "The settlements did not all go to the winner once: " + heldBy(npc, settlements, winner.id()) + " of " + settlements.size()
                            + ", " + known + " settlements before, " + npc.settlements().size() + " after");
            for (long id : buildings) {
                helper.assertTrue(ofKingdom(book, id, winner.id()), "The building " + id + " did not go to the winner");
            }
            for (UUID id : citizens) {
                helper.assertTrue(ofKingdom(registry, id, winner.id()), "A citizen did not become a citizen of the winner");
            }
            helper.assertTrue(registry.size() == registered, "Citizens were made or lost: " + registered + " before, " + registry.size() + " after");
            helper.assertTrue(territory.territory(loser.id()).isEmpty() && winner.id().equals(territory.claimOwner(dimension, capital.chunk())),
                    "The claims of the annexed state did not go to the winner");
        } finally {
            for (long id : buildings) {
                book.removeBuilding(id);
            }
            for (UUID id : citizens) {
                registry.remove(id);
            }
            giveUp(server, ids);
            for (City city : List.copyOf(RoadWork.state(server).citiesOf(winner.id()))) {
                RoadWork.state(server).removeCity(city.id());
            }
            npc.removeSettlementsOf(winner.id());
            forget(server, loser.id());
            WarGameTests.end(helper, winner, List.of(), List.of());
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- a restart in the middle

    /**
     * Three provinces of a state with two villages, three buildings and two citizens each, and a city, go over by the journal;
     * the server stops while the villages go one a second, reads the journal back from its file and goes on: in the end
     * everything of the three provinces is the winner's once, and the heart keeps what is its own.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_province_war_restart", timeoutTicks = 200)
    public static void aRestartInTheMiddleOfTheJournalNeitherLosesNorDoubles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        String dimension = Dimensions.id(level);
        Kingdom loser = state(server, "GameTest Restarted State", RulerCharacter.MERCHANT);
        Kingdom winner = landless(server, "GameTest Province Heir");
        TerritoryIndex territory = LandEvents.attach(server);
        ProvinceMap map = territory.provinceMap().orElseThrow();
        NpcState npc = NpcWork.state(server);
        KingdomWorldState world = KingdomWorldApi.state(server);
        ConstructionBook book = world.construction();
        CitizenRegistry registry = world.citizens().registry();
        List<Integer> given = new ArrayList<>();
        List<Long> givenBuildings = new ArrayList<>();
        List<UUID> givenCitizens = new ArrayList<>();
        long keptBuilding = 0;
        UUID keptCitizen = null;
        int keptSettlement = 0;
        City city = null;
        try {
            ChunkPos origin = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
            int baseX = origin.x + 40;
            int baseZ = origin.z - 60;
            long now = KingdomWorldApi.gameTime(server);
            int heartX = middle(baseX, RESTART_WIDE);
            int heartZ = middle(baseZ, RESTART_WIDE);
            Settlement capitalSeat = village(server, loser.id(), RESTART_HEART, heartX, heartZ, "Keepholm", "Кипхольм");
            keptSettlement = capitalSeat.id();
            province(territory, RESTART_HEART, baseX, baseZ, RESTART_WIDE, List.of(RESTART_GIVEN[0]), ProvinceKind.HEART, capitalSeat.id());
            territory.transferProvince(RESTART_HEART, loser.id(), 0);
            keptBuilding = building(book, loser.id(), dimension, heartX + 20, heartZ + 20, now);
            keptCitizen = citizen(server, loser.id(), dimension, heartX - 20, heartZ - 20);
            for (int i = 0; i < RESTART_GIVEN.length; i++) {
                int chunkX = baseX + (i + 1) * RESTART_WIDE;
                int x = middle(chunkX, RESTART_WIDE);
                int z = heartZ;
                List<Integer> near = new ArrayList<>();
                near.add(i == 0 ? RESTART_HEART : RESTART_GIVEN[i - 1]);
                if (i + 1 < RESTART_GIVEN.length) {
                    near.add(RESTART_GIVEN[i + 1]);
                }
                Settlement seat = village(server, loser.id(), RESTART_GIVEN[i], x, z, "Restholm " + i, "Рестхольм " + i);
                Settlement hamlet = village(server, loser.id(), RESTART_GIVEN[i], x + 40, z + 40, "Restby " + i, "Рестбю " + i);
                given.add(seat.id());
                given.add(hamlet.id());
                province(territory, RESTART_GIVEN[i], chunkX, baseZ, RESTART_WIDE, near, ProvinceKind.CORE, seat.id());
                territory.transferProvince(RESTART_GIVEN[i], loser.id(), 0);
                for (int b = 0; b < 3; b++) {
                    givenBuildings.add(building(book, loser.id(), dimension, x - 40 + b * 12, z + 20, now));
                }
                for (int c = 0; c < 2; c++) {
                    givenCitizens.add(citizen(server, loser.id(), dimension, x - 20 - c * 4, z - 20));
                }
                if (i == 0) {
                    city = RoadWork.state(server).addCity(loser.id(), "GameTest Rest Town", dimension, x, 64, z, now);
                    seat.setCityId(city.id());
                }
            }
            territory.establish(loser.id(), new Capital(dimension, heartX, 64, heartZ), List.of());
            int registered = registry.size();
            int known = npc.settlements().size();
            List<Long> provinces = new ArrayList<>();
            for (int id : RESTART_GIVEN) {
                provinces.add((long) id);
            }

            Conquest handed = SiegeWork.state(server).addConquest(Conquest.Kind.PROVINCE, 0, winner.id(), loser.id(), map.dimension(), provinces, 0,
                    now);
            // The journal runs until the first village went over: the step of the settlements is in its middle.
            int seconds = 0;
            while (seconds < MOST_SECONDS && pending(server, handed.id()) && heldBy(npc, given, winner.id()) == 0) {
                SiegeWork.forceConquestSecond(server);
                seconds++;
            }
            Conquest middle = conquest(server, handed.id());
            int before = heldBy(npc, given, winner.id());
            helper.assertTrue(middle.step() == Conquest.Step.SETTLEMENTS && before >= 1 && before < given.size(),
                    "The journal is not in the middle of the settlements: " + middle.step() + ", " + before + " of " + given.size());
            for (int id : RESTART_GIVEN) {
                helper.assertTrue(winner.id().equals(map.owner(id)), "The province " + id + " did not go over first");
            }

            // The server stops: the file read back holds the conquest as it stands, in this format.
            SiegeSavedData reread = SiegeSavedData.copyOf(SiegeSavedData.get(server));
            helper.assertTrue(!reread.isReadOnly(), "The sieges read back are read-only: " + reread.readOnlyReason().orElse(""));
            Conquest saved = reread.state().conquests().stream().filter(c -> c.id() == handed.id()).findFirst()
                    .orElseThrow(() -> new IllegalStateException("The conquest was lost in saving"));
            helper.assertTrue(saved.equals(middle), "The conquest changed in saving: " + saved + " instead of " + middle);
            // It starts again: the journal is read from the file and the memory of the second is gone.
            SiegeState live = SiegeWork.state(server);
            CompoundTag file = SiegeNbt.write(live);
            live.clear();
            List<String> problems = SiegeNbt.read(file, live);
            helper.assertTrue(problems.isEmpty(), "The sieges could not be read back: " + problems);
            SiegeWork.reset();
            helper.assertTrue(heldBy(npc, given, winner.id()) == before, "A settlement went back or over again at the restart");

            int after = 0;
            while (after < MOST_SECONDS && pending(server, handed.id())) {
                SiegeWork.forceConquestSecond(server);
                after++;
            }
            SiegeWork.LOGGER.info("Restart in the middle of the journal: {} second(s) before it, {} after, {} of {} village(s) over at the stop",
                    seconds, after, before, given.size());
            helper.assertTrue(!pending(server, handed.id()), "The conquest was not over after the restart");
            helper.assertTrue(heldBy(npc, given, winner.id()) == given.size() && npc.settlements().size() == known,
                    "The settlements of the provinces did not go over once: " + heldBy(npc, given, winner.id()) + " of " + given.size()
                            + ", " + known + " settlements before, " + npc.settlements().size() + " after");
            for (long id : givenBuildings) {
                helper.assertTrue(ofKingdom(book, id, winner.id()), "The building " + id + " did not go over");
            }
            for (UUID id : givenCitizens) {
                helper.assertTrue(ofKingdom(registry, id, winner.id()), "A citizen of the provinces did not go over");
            }
            helper.assertTrue(registry.size() == registered, "Citizens were made or lost: " + registered + " before, " + registry.size() + " after");
            long cityId = city.id();
            helper.assertTrue(RoadWork.state(server).citiesOf(winner.id()).stream().filter(c -> c.id() == cityId).count() == 1
                    && RoadWork.state(server).citiesOf(loser.id()).stream().noneMatch(c -> c.id() == cityId), "The city did not go over once");
            // The heart is not in the conquest: its province, village, building and citizen stay.
            helper.assertTrue(loser.id().equals(map.owner(RESTART_HEART)) && heldBy(npc, List.of(keptSettlement), loser.id()) == 1
                    && ofKingdom(book, keptBuilding, loser.id()) && ofKingdom(registry, keptCitizen, loser.id()),
                    "Something of the heart went with the provinces");
        } finally {
            for (long id : givenBuildings) {
                book.removeBuilding(id);
            }
            if (keptBuilding != 0) {
                book.removeBuilding(keptBuilding);
            }
            for (UUID id : givenCitizens) {
                registry.remove(id);
            }
            if (keptCitizen != null) {
                registry.remove(keptCitizen);
            }
            if (city != null) {
                RoadWork.state(server).removeCity(city.id());
            }
            giveUp(server, RESTART_HEART, RESTART_GIVEN[0], RESTART_GIVEN[1], RESTART_GIVEN[2]);
            forget(server, winner.id());
            forget(server, loser.id());
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- holds far from players

    /**
     * Far from players a squad at the square of a village first fights its garrison by numbers, holds the square while nobody
     * defends it and is interrupted while a militia stands there again; another squad takes an empty province at its boundary
     * stone in its time. The peace for land hands both provinces over, the village with them, never the heart.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_province_war_hold", timeoutTicks = 200)
    public static void farFromPlayersASquadBeatsTheGarrisonAndHoldsTheSquareAndAnotherTheStone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        String dimension = Dimensions.id(level);
        Kingdom home = home(helper, "GameTest Province Besieger");
        Kingdom enemy = state(server, "GameTest Held State", RulerCharacter.BUILDER);
        TerritoryIndex territory = LandEvents.attach(server);
        ProvinceMap map = territory.provinceMap().orElseThrow();
        NpcState npc = NpcWork.state(server);
        List<Citizen> soldiers = new ArrayList<>();
        try {
            ChunkPos origin = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
            int baseX = origin.x + 80;
            int baseZ = origin.z;
            int z = middle(baseZ, HOLD_WIDE);
            Settlement seat = village(server, enemy.id(), HOLD_SETTLED, middle(baseX + HOLD_WIDE, HOLD_WIDE), z, "Holdbury", "Холдбери");
            province(territory, HOLD_HEART, baseX, baseZ, HOLD_WIDE, List.of(HOLD_SETTLED), ProvinceKind.HEART, 0);
            province(territory, HOLD_SETTLED, baseX + HOLD_WIDE, baseZ, HOLD_WIDE, List.of(HOLD_HEART, HOLD_EMPTY), ProvinceKind.CORE, seat.id());
            province(territory, HOLD_EMPTY, baseX + 2 * HOLD_WIDE, baseZ, HOLD_WIDE, List.of(HOLD_SETTLED), ProvinceKind.CORE, 0);
            for (int id : new int[]{HOLD_HEART, HOLD_SETTLED, HOLD_EMPTY}) {
                territory.transferProvince(id, enemy.id(), 0);
            }
            territory.establish(enemy.id(), new Capital(dimension, middle(baseX, HOLD_WIDE), 64, z), List.of());
            helper.assertTrue(seat.militia() == 1 && seat.guards() == 0, "The village has no militiaman: " + seat.militia());

            // A square and a stone are held as long as a chunk is occupied here; the dice of battles fall in the middle.
            SiegeSettings.override(SiegeRules.DEFAULT.withTimes(1_200, 1_200, 1_200));
            ArmyWork.overrideRolls(() -> 0.5);
            for (int i = 0; i < 5; i++) {
                soldiers.add(WarGameTests.soldier(helper, home, i));
            }
            War war = WarGameTests.war(helper, home, enemy, WarGoal.LAND);
            ProvinceHolds.Target stone = ProvinceHolds.stone(HOLD_EMPTY, ProvinceHolds.stoneChunk(map.province(HOLD_EMPTY).orElseThrow(),
                    map::provinceAt, () -> 0L));
            helper.assertTrue(map.provinceAt(stone.chunk()) == HOLD_EMPTY, "The boundary stone stands outside its province");
            ArmySquad besiegers = squad(server, home, soldiers.subList(0, 4));
            ArmySquad watch = squad(server, home, soldiers.subList(4, 5));
            helper.assertTrue(ArmyWork.order(server, null, besiegers.id(), Order.hold(new WorldPos(dimension, seat.x(), 64, seat.z()))).ok(),
                    "The squad was not sent to the square");
            helper.assertTrue(ArmyWork.order(server, null, watch.id(), Order.hold(new WorldPos(dimension, stone.x(), 64, stone.z()))).ok(),
                    "The squad was not sent to the stone");
            String square = Hold.key(Hold.Kind.PROVINCE, war.id(), enemy.id(), map.dimension(), HOLD_SETTLED);
            String boundary = Hold.key(Hold.Kind.PROVINCE, war.id(), enemy.id(), map.dimension(), HOLD_EMPTY);

            // Far from players the four soldiers fight the militiaman first: he falls and the square is held.
            SiegeWork.forceHolds(server, 600);
            Hold plaza = SiegeWork.state(server).hold(square).orElseThrow(() -> new IllegalStateException("The square is not held"));
            Hold held = SiegeWork.state(server).hold(boundary).orElseThrow(() -> new IllegalStateException("The stone is not held"));
            helper.assertTrue(seat.militia() == 0 && seat.guards() == 0, "The garrison did not fall: " + seat.militia() + " militia left");
            helper.assertTrue(plaza.progress() == 600 && !plaza.taken(), "The square was not held after the battle: " + plaza.progress());
            helper.assertTrue(held.progress() == 600 && !held.taken(), "The stone was not held: " + held.progress());

            // A militia stands in the square again: no battle within a minute of the last one, the hold is interrupted. The empty
            // province is taken in its time meanwhile.
            seat.found(12, 10, 50);
            SiegeWork.forceHolds(server, 600);
            helper.assertTrue(plaza.progress() == 0 && seat.militia() == 1, "The militia did not interrupt the hold or was fought again: "
                    + plaza.progress() + ", " + seat.militia() + " militia");
            helper.assertTrue(held.taken(), "The boundary stone was not taken in its time");

            // A minute later (a restart forgets when the last battle was) the squad beats the militia again and holds the square.
            SiegeWork.reset();
            SiegeWork.forceHolds(server, 600);
            helper.assertTrue(seat.militia() == 0 && plaza.progress() == 600, "The second battle did not clear the square");
            SiegeWork.forceHolds(server, 600);
            helper.assertTrue(plaza.taken(), "Holding the square for its time did not take the province");
            helper.assertTrue(!SiegeWork.state(server).scoreOnce(war.id(), square) && !SiegeWork.state(server).scoreOnce(war.id(), boundary),
                    "A province taken gave no score");
            for (int id : new int[]{HOLD_SETTLED, HOLD_EMPTY}) {
                String detail = Integer.toString(id);
                helper.assertTrue(WarWork.state(server).chronicle().stream().anyMatch(event -> event.kind().equals("province_taken")
                        && event.war() == war.id() && event.detail().equals(detail)), "The chronicle does not know the province " + id + " taken");
            }

            // The peace for land hands over the two provinces taken, the village with its province, and never the heart.
            Outcome ended = WarWork.end(server, war, War.Result.ADMIN, PeaceTerms.forGoal(WarGoal.LAND, home.id(), true, 0,
                    DiplomacySettings.rules()));
            helper.assertTrue(ended.ok(), "The peace failed: " + ended);
            SiegeWork.forceConquests(server);
            helper.assertTrue(home.id().equals(map.owner(HOLD_SETTLED)) && home.id().equals(map.owner(HOLD_EMPTY)),
                    "The provinces taken did not go to the winner");
            helper.assertTrue(enemy.id().equals(map.owner(HOLD_HEART)), "The heart went with a peace for land");
            helper.assertTrue(heldBy(npc, List.of(seat.id()), home.id()) == 1, "The village did not go with its province");
        } finally {
            ArmyWork.overrideRolls(null);
            SiegeSettings.override(null);
            giveUp(server, HOLD_HEART, HOLD_SETTLED, HOLD_EMPTY);
            npc.removeSettlementsOf(home.id());
            forget(server, enemy.id());
            SiegeWork.state(server).forget(home.id());
            WarGameTests.end(helper, home, List.of(), List.of(), soldiers.toArray(Citizen[]::new));
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- cession

    /**
     * A kingdom of players asks a merchant ruler for the province next to its claims, where a village stands: the ruler asks
     * twice the price of an empty province, cedes it when it is offered and the money moves by the books; the heart is never
     * asked for, and a ruler that distrusts the asker keeps even the province it would sell.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_province_war_cede", timeoutTicks = 200)
    public static void aRulerOfTheWorldCedesABorderingProvinceForItsPriceAndRefusesOneItDistrusts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        String dimension = Dimensions.id(level);
        Kingdom asker = home(helper, "GameTest Province Asker");
        Kingdom holder = state(server, "GameTest Ceding Ruler", RulerCharacter.MERCHANT);
        TerritoryIndex territory = LandEvents.attach(server);
        ProvinceMap map = territory.provinceMap().orElseThrow();
        NpcState npc = NpcWork.state(server);
        DiplomacyState diplomacy = WarWork.state(server);
        try {
            Capital capital = RoadWork.capital(server, asker.id()).orElseThrow();
            int atX = capital.x() >> 4;
            int atZ = capital.z() >> 4;
            // From west to east the heart, the core and the edge, which ends one chunk west of the claims around the throne.
            int edgeX = atX - 1 - CEDE_WIDE;
            int coreX = edgeX - CEDE_WIDE;
            int heartX = coreX - CEDE_WIDE;
            int z = middle(atZ, CEDE_WIDE);
            Settlement seat = village(server, holder.id(), CEDE_EDGE, middle(edgeX, CEDE_WIDE), z, "Cedeford", "Сидфорд");
            province(territory, CEDE_HEART, heartX, atZ, CEDE_WIDE, List.of(CEDE_CORE), ProvinceKind.HEART, 0);
            province(territory, CEDE_CORE, coreX, atZ, CEDE_WIDE, List.of(CEDE_HEART, CEDE_EDGE), ProvinceKind.CORE, 0);
            province(territory, CEDE_EDGE, edgeX, atZ, CEDE_WIDE, List.of(CEDE_CORE), ProvinceKind.FRONTIER, seat.id());
            for (int id : new int[]{CEDE_HEART, CEDE_CORE, CEDE_EDGE}) {
                territory.transferProvince(id, holder.id(), 0);
            }
            territory.establish(holder.id(), new Capital(dimension, middle(heartX, CEDE_WIDE), 64, z), List.of());
            Economy economy = KingdomWorldApi.state(server).economy();
            long day = TreasuryActions.day(server);
            economy.adminTreasury(asker.id(), 50_000, day);

            // The heart of a state goes only with the whole state.
            Outcome heart = DiplomacyActions.askCede(server, null, asker.id(), holder.id(), CEDE_HEART);
            helper.assertTrue(!heart.ok() && heart.error() == KingdomError.CAPITAL_CHUNK, "The heart could be asked for: " + heart);

            // The edge touches the claims of the asker: the ruler asks its price, twice that of an empty province, for people live there.
            long funds = economy.treasury(asker.id()).orElseThrow().funds();
            long reserve = economy.treasury(holder.id()).orElseThrow().reserve(asker.id());
            long supply = economy.supply(asker.id());
            Outcome asked = DiplomacyActions.askCede(server, null, asker.id(), holder.id(), CEDE_EDGE);
            helper.assertTrue(asked.ok(), "The edge could not be asked for: " + asked);
            Proposal offer = cession(diplomacy, asker.id(), holder.id(), CEDE_EDGE);
            helper.assertTrue(offer.price() >= 3_000, "A province where people live is asked for " + offer.price());
            helper.assertTrue(economy.treasury(asker.id()).orElseThrow().funds() == funds, "The money moved before the answer");
            answered(server, offer.id());
            helper.assertTrue(diplomacy.proposal(offer.id()).map(p -> p.status() == Proposal.Status.ACCEPTED).orElse(false),
                    "The ruler did not cede the edge for its own price");
            helper.assertTrue(asker.id().equals(map.owner(CEDE_EDGE)), "The edge paid for is not the asker's before the journal");
            SiegeWork.forceConquests(server);
            helper.assertTrue(asker.id().equals(map.owner(CEDE_EDGE)), "The edge did not go to the asker");
            helper.assertTrue(heldBy(npc, List.of(seat.id()), asker.id()) == 1, "The village of the edge stayed with the ruler");
            long paid = funds - economy.treasury(asker.id()).orElseThrow().funds();
            long got = economy.treasury(holder.id()).orElseThrow().reserve(asker.id()) - reserve;
            helper.assertTrue(paid > 0 && paid == got && paid == economy.landCost(asker.id(), holder.id(), offer.price()),
                    "The money did not move whole: paid " + paid + ", got " + got);
            helper.assertTrue(economy.supply(asker.id()) == supply, "Money was made or lost: " + supply + " -> " + economy.supply(asker.id()));
            TreasuryJournal.Totals spent = economy.treasury(asker.id()).orElseThrow().journal().totals(day, TreasuryJournal.Period.TODAY)
                    .getOrDefault(LedgerCategory.LAND, TreasuryJournal.Totals.ZERO);
            helper.assertTrue(spent.expense() == paid, "No line \"Land\" in the books of the asker: " + spent);
            String detail = Integer.toString(CEDE_EDGE);
            helper.assertTrue(diplomacy.chronicle().stream().anyMatch(event -> event.kind().equals("ceded") && event.detail().equals(detail)),
                    "The chronicle does not know the cession");

            // The core touches the edge the asker holds now, but the ruler distrusts it after a war: it keeps the core.
            diplomacy.relate(holder.id(), asker.id(), Relations.Reason.WAR);
            long left = economy.treasury(asker.id()).orElseThrow().funds();
            Outcome again = DiplomacyActions.askCede(server, null, asker.id(), holder.id(), CEDE_CORE);
            helper.assertTrue(again.ok(), "The core could not be asked for: " + again);
            Proposal refused = cession(diplomacy, asker.id(), holder.id(), CEDE_CORE);
            answered(server, refused.id());
            helper.assertTrue(diplomacy.proposal(refused.id()).map(p -> p.status() == Proposal.Status.DECLINED).orElse(false),
                    "A ruler ceded a province to a kingdom it distrusts");
            SiegeWork.forceConquests(server);
            helper.assertTrue(holder.id().equals(map.owner(CEDE_CORE)) && economy.treasury(asker.id()).orElseThrow().funds() == left,
                    "A refused cession moved land or money");
        } finally {
            giveUp(server, CEDE_HEART, CEDE_CORE, CEDE_EDGE);
            npc.removeSettlementsOf(asker.id());
            forget(server, holder.id());
            SiegeWork.state(server).forget(asker.id());
            WarGameTests.end(helper, asker, List.of(), List.of());
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- disputes

    /**
     * A ruler of a state that wants land disputes with nobody while wild land next to its provinces is free, and with the
     * holder of the provinces next to its own once none is: the kingdom whose province borders its edge, however far from its
     * capital.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_province_war_disputes", timeoutTicks = 100)
    public static void aStateDisputesItsLandWithItsNeighbourOnceNoWildLandIsFree(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        String dimension = Dimensions.id(helper.getLevel());
        Kingdom crowded = state(server, "GameTest Crowded State", RulerCharacter.WARRIOR);
        Kingdom neighbour = state(server, "GameTest Neighbour State", RulerCharacter.BUILDER);
        TerritoryIndex territory = LandEvents.attach(server);
        try {
            ChunkPos origin = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
            int baseX = origin.x - 80;
            int baseZ = origin.z + 40;
            province(territory, DISPUTE_HEART, baseX, baseZ, DISPUTE_WIDE, List.of(DISPUTE_EDGE), ProvinceKind.HEART, 0);
            province(territory, DISPUTE_EDGE, baseX + DISPUTE_WIDE, baseZ, DISPUTE_WIDE, List.of(DISPUTE_HEART, DISPUTE_THEIRS, DISPUTE_WILD),
                    ProvinceKind.FRONTIER, 0);
            province(territory, DISPUTE_THEIRS, baseX + 2 * DISPUTE_WIDE, baseZ, DISPUTE_WIDE, List.of(DISPUTE_EDGE), ProvinceKind.FRONTIER, 0);
            province(territory, DISPUTE_WILD, baseX + DISPUTE_WIDE, baseZ - DISPUTE_WIDE, DISPUTE_WIDE, List.of(DISPUTE_EDGE), ProvinceKind.WILD, 0);
            territory.transferProvince(DISPUTE_HEART, crowded.id(), 0);
            territory.transferProvince(DISPUTE_EDGE, crowded.id(), 0);
            territory.transferProvince(DISPUTE_THEIRS, neighbour.id(), 0);
            // The capital in the middle of the heart: no land of another lies near its claims.
            territory.establish(crowded.id(), new Capital(dimension, middle(baseX, DISPUTE_WIDE), 64, middle(baseZ, DISPUTE_WIDE)), List.of());
            RulerMemory memory = NpcWork.state(server).ruler(crowded.id());

            NpcRulers.forceDisputes(server, crowded.id());
            helper.assertTrue(memory.relation(neighbour.id()) == 0, "A state disputed its land while wild land next to it was free: "
                    + memory.relation(neighbour.id()));

            territory.transferProvince(DISPUTE_WILD, neighbour.id(), 0);
            NpcRulers.forceDisputes(server, crowded.id());
            int relation = memory.relation(neighbour.id());
            helper.assertTrue(relation < 0, "A state did not dispute its land with the holder of the provinces next to it: " + relation);
            helper.assertTrue(memory.relations().keySet().stream().allMatch(other -> other.equals(neighbour.id())),
                    "A state disputed its land with a kingdom that does not border it: " + memory.relations().keySet());
        } finally {
            giveUp(server, DISPUTE_HEART, DISPUTE_EDGE, DISPUTE_THEIRS, DISPUTE_WILD);
            forget(server, crowded.id());
            forget(server, neighbour.id());
        }
        helper.succeed();
    }
}
