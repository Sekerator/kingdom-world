package io.github.sekerator.kingdomworld.common.network;

import io.github.sekerator.kingdomworld.common.network.data.RoadViews;
import io.github.sekerator.kingdomworld.common.network.packet.RoadPackets;
import io.github.sekerator.kingdomworld.common.road.RoadSync;
import io.github.sekerator.kingdomworld.core.land.Tier;
import io.github.sekerator.kingdomworld.core.road.CityLevel;
import io.github.sekerator.kingdomworld.core.road.RoadClass;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapSyncPacketsTest {
    private static final UUID KINGDOM = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private static final String OVERWORLD = "minecraft:overworld";

    private static RoadViews.MapPart part(long version, int settlements) {
        List<RoadViews.MapCity> cities = new java.util.ArrayList<>();
        cities.add(new RoadViews.MapCity(0, KINGDOM, "Ozerovia", OVERWORLD, 0, 0, 0x3366AA, CityLevel.CITY));
        List<RoadViews.MapRoad> roads = new java.util.ArrayList<>();
        for (int i = 1; i <= settlements; i++) {
            Tier tier = Tier.values()[1 + i % 4];
            cities.add(new RoadViews.MapCity(RoadSync.settlementId(i), KINGDOM, "Town " + i, OVERWORLD, i * 300, -i * 200, 0x3366AA,
                    CityLevel.VILLAGE, null, new RoadViews.MapSettlement(tier, 10 + i, i % 3, i % 2, 1 + i % 5, i % 2 == 0)));
            roads.add(new RoadViews.MapRoad(RoadSync.plannedRoadId(i), KINGDOM, OVERWORLD, RoadClass.PATH, 0x3366AA, 0, new int[]{0, 150, i * 300},
                    new int[]{0, -100, -i * 200}));
        }
        return new RoadViews.MapPart(KINGDOM, version, cities, roads);
    }

    @Test
    void aChangeOfTheMapsTravelsWhole() {
        RoadViews.MapChange change = new RoadViews.MapChange(true, List.of(part(7, 40)), List.of(UUID.randomUUID()),
                List.of(new RoadViews.MapCamp(1, OVERWORLD, 20, 40, 64)), List.of());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new RoadPackets.MapSync(change).encode(buf);
        int bytes = buf.readableBytes();
        RoadPackets.MapSync read = RoadPackets.MapSync.decode(buf);
        assertEquals(change, read.change());
        assertEquals(0, buf.readableBytes());
        // The weight a part is reckoned at keeps a packet of parts below what is sent.
        assertTrue(change.parts().get(0).weight() >= bytes / 2, change.parts().get(0).weight() + " for " + bytes + " bytes");
        assertTrue(read.change().parts().get(0).cities().get(3).capitalOfPlan() == (read.change().parts().get(0).cities().get(3).settlement().tier()
                == Tier.CAPITAL));
    }

    @Test
    void aPartIsTheSameOnlyWithTheSameRoads() {
        assertEquals(part(1, 5), part(1, 5));
        assertNotEquals(part(1, 5), part(1, 6));
        RoadViews.MapRoad road = part(1, 1).roads().get(0);
        RoadViews.MapRoad moved = new RoadViews.MapRoad(road.id(), road.kingdomId(), road.dimension(), road.roadClass(), road.color(), road.built(),
                new int[]{0, 151, 300}, road.zs());
        assertNotEquals(road, moved);
    }

    @Test
    void theIdsOfThePlanNeverMeetOtherIds() {
        Set<Long> ids = new HashSet<>();
        // Real cities and roads count up from 1; the towns of the old plan of a place are -(place * 16 + town + 1).
        for (long id = 1; id <= 10_000; id++) {
            ids.add(id);
        }
        for (int site = 1; site <= 5_000; site++) {
            for (int town = 0; town < 16; town++) {
                assertTrue(ids.add(-((long) site * 16 + town + 1)));
            }
        }
        for (int i = 1; i <= 100_000; i += 7) {
            assertTrue(ids.add(RoadSync.settlementId(i)), "settlement " + i);
            assertTrue(ids.add(RoadSync.plannedRoadId(i)), "road " + i);
        }
        assertTrue(RoadSync.settlementId(Integer.MAX_VALUE) != RoadSync.plannedRoadId(Integer.MAX_VALUE));
        assertTrue(RoadSync.settlementId(1) < -((long) Integer.MAX_VALUE * 16 + 16), "beyond any town of the old plan");
    }
}
