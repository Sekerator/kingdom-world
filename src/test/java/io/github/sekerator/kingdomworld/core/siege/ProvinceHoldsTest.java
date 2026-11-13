package io.github.sekerator.kingdomworld.core.siege;

import io.github.sekerator.kingdomworld.core.combat.Side;
import io.github.sekerator.kingdomworld.core.diplomacy.WarGoal;
import io.github.sekerator.kingdomworld.core.land.ProvinceKind;
import io.github.sekerator.kingdomworld.core.land.Tier;
import io.github.sekerator.kingdomworld.core.npc.NpcNames;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
import io.github.sekerator.kingdomworld.core.npc.Settlement;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.Province;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Where a province is held and how its garrison fights far from players.
 */
class ProvinceHoldsTest {
    private static final String WORLD = "minecraft:overworld";
    private static final UUID STATE = new UUID(0, 1);

    private static Province province(int id, int centerX, int centerZ, long seat) {
        return new Province(id, centerX, centerZ, List.of(), List.of(), ProvinceKind.CORE, id, seat);
    }

    private static ProvinceMap filled(ProvinceMap map, int id, int centerX, int centerZ, int x1, int z1, int x2, int z2) {
        map.define(province(id, centerX, centerZ, 0));
        map.fill(id, x1, z1, x2, z2);
        return map;
    }

    /**
     * A search through the tiles that must not be needed.
     */
    private static long never(String why) {
        fail(why);
        return 0;
    }

    @Test
    void aProvinceWherePeopleLiveIsHeldAtTheSquareOfItsMainSettlement() {
        NpcState npc = new NpcState();
        Settlement town = npc.addSettlement(STATE, 5, 0, Tier.TOWN, 100, -200, 7, new NpcNames.Name("Ostbrook", "Остбрук"));
        Province settled = province(5, 0, 0, town.id());

        ProvinceHolds.Target target = ProvinceHolds.target(settled, town, chunk -> 5, () -> never("a settled province has no stone"));
        assertTrue(target.settled());
        assertEquals(town.id(), target.seat());
        assertEquals(100, target.x());
        assertEquals(-200, target.z());
        assertEquals(ChunkKey.of(6, -13), target.chunk());
        assertTrue(target.contains(112, -188, 12), "the edge of the square counts");
        assertFalse(target.contains(113, -200, 12), "a step beyond it does not");
        assertArrayEquals(new int[]{88, -212, 113, -187}, target.bounds(12));
        assertTrue(target.reaches(124, -176, ProvinceHolds.REACH), "a squad within 24 blocks stands at the square");
        assertFalse(target.reaches(125, -200, ProvinceHolds.REACH));

        town.setStatus(Settlement.Status.RUINED);
        assertFalse(ProvinceHolds.settled(settled, town), "a ruined settlement leaves the province empty");
        assertFalse(ProvinceHolds.settled(province(5, 0, 0, 0), null), "a province without a seat is empty");
        assertFalse(ProvinceHolds.settled(province(5, 0, 0, town.id() + 1), town), "the seat must be the settlement it names");
        ProvinceHolds.Target stone = ProvinceHolds.target(settled, town, chunk -> 5, () -> never("the middle lies in it"));
        assertFalse(stone.settled());
        assertEquals(0, stone.seat());
        assertEquals(8, stone.x(), "the stone stands in the middle of the chunk of the middle of the province");
        assertEquals(8, stone.z());
    }

    @Test
    void anEmptyProvinceIsHeldAtItsBoundaryStone() {
        ProvinceMap map = new ProvinceMap(WORLD);
        filled(map, 7, 40, 40, 0, 0, 5, 5);
        Province middle = map.province(7).orElseThrow();
        ProvinceHolds.Target stone = ProvinceHolds.target(middle, null, map::provinceAt, () -> never("the middle lies in it"));
        assertEquals(ChunkKey.of(2, 2), stone.chunk());
        assertEquals(40, stone.x());
        assertEquals(40, stone.z());
        assertTrue(stone.contains(32, 47, 12), "the whole chunk of the stone counts");
        assertFalse(stone.contains(48, 40, 12), "the next chunk does not");
        assertArrayEquals(new int[]{32, 32, 48, 48}, stone.bounds(12));

        // The middle of a crooked province lies outside it: the stone goes to the chunk of it nearest to the middle.
        filled(map, 8, 40, 40, 5, 0, 6, 1);
        long nearest = ProvinceHolds.stoneChunk(map.province(8).orElseThrow(), map::provinceAt, () -> never("a chunk of it is near"));
        assertEquals(ChunkKey.of(5, 1), nearest);

        // Nothing of it near the middle: the first chunk found in its tiles.
        filled(map, 9, 0, 0, 100, 100, 100, 100);
        long far = ProvinceHolds.stoneChunk(map.province(9).orElseThrow(), map::provinceAt, () -> ChunkKey.of(100, 100));
        assertEquals(ChunkKey.of(100, 100), far);
    }

    @Test
    void theProvincesNearABlockAreFoundOnceEach() {
        ProvinceMap map = new ProvinceMap(WORLD);
        filled(map, 1, 0, 0, 0, 0, 3, 3);
        filled(map, 2, 80, 0, 4, 0, 7, 3);
        int[] near = ProvinceHolds.near(63, 8, 1, map::provinceAt);
        Arrays.sort(near);
        assertArrayEquals(new int[]{1, 2}, near, "the chunks around touch both provinces; land of no province is left out");
        assertArrayEquals(new int[]{1}, ProvinceHolds.near(8, 8, 0, map::provinceAt));
        assertEquals(0, ProvinceHolds.near(-500, -500, 2, map::provinceAt).length);
    }

    @Test
    void aWarForLandTakesAnyProvinceButTheHeartAndAWarForACityOneWherePeopleLive() {
        int capital = 2;
        assertTrue(ProvinceHolds.eligible(WarGoal.LAND, 3, false, capital));
        assertTrue(ProvinceHolds.eligible(WarGoal.LAND, 3, true, capital));
        assertFalse(ProvinceHolds.eligible(WarGoal.LAND, capital, true, capital), "the province of the capital only by annexation");
        assertTrue(ProvinceHolds.eligible(WarGoal.CITY, 3, true, capital));
        assertFalse(ProvinceHolds.eligible(WarGoal.CITY, 3, false, capital), "a war for a city takes no empty province");
        assertFalse(ProvinceHolds.eligible(WarGoal.CITY, capital, true, capital));
        assertFalse(ProvinceHolds.eligible(WarGoal.THRONE, 3, true, capital), "a war for the throne takes the throne hall alone");
        assertFalse(ProvinceHolds.eligible(WarGoal.RAID, 3, true, capital));
        assertFalse(ProvinceHolds.eligible(WarGoal.LAND, 0, true, capital));
    }

    @Test
    void theGarrisonFightsByItsNumbersAndLosesItsGuardsFirst() {
        Side garrison = ProvinceHolds.garrison(8, 3);
        assertEquals(11, garrison.size());
        assertEquals(8 * ProvinceHolds.GUARD_STRENGTH + 3 * ProvinceHolds.MILITIA_STRENGTH, garrison.power(), 1e-9);
        assertEquals(Side.Kind.ARMY, garrison.kind());
        assertTrue(ProvinceHolds.garrison(0, 0).isEmpty());
        assertTrue(ProvinceHolds.garrison(-1, -4).isEmpty());

        assertTrue(ProvinceHolds.battleDue(null, 0), "the first battle at a place is fought at once");
        assertFalse(ProvinceHolds.battleDue(1_000L, 2_199), "then a minute goes before the next");
        assertTrue(ProvinceHolds.battleDue(1_000L, 2_200));
        assertTrue(ProvinceHolds.battleDue(5_000L, 100), "a clock set back does not stop the battles");

        NpcState npc = new NpcState();
        Settlement town = npc.addSettlement(STATE, 5, 0, Tier.TOWN, 0, 0, 7, new NpcNames.Name("Ostbrook", "Остбрук"));
        town.restore(Settlement.Status.DATA, "", 0, 0, 0, 0, 30, 40, 100, 4, 3, 50, 100, 0, 0, new long[0]);
        long version = npc.settlementsVersion();
        assertEquals(5, town.loseDefenders(5));
        assertEquals(0, town.guards(), "the guards fall first");
        assertEquals(2, town.militia(), "then the militia");
        assertTrue(npc.settlementsVersion() > version, "the loss is saved and seen");
        assertEquals(2, town.loseDefenders(10), "no more fall than there are");
        assertEquals(0, town.militia());
        version = npc.settlementsVersion();
        assertEquals(0, town.loseDefenders(3));
        assertEquals(0, town.loseDefenders(-2));
        assertEquals(version, npc.settlementsVersion(), "nothing lost, nothing changed");
    }
}
