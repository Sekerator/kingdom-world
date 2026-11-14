package io.github.sekerator.kingdomworld.common.network;

import io.github.sekerator.kingdomworld.common.network.data.RoadViews;
import io.github.sekerator.kingdomworld.common.network.packet.ConstructionPackets;
import io.github.sekerator.kingdomworld.common.network.packet.RoadPackets;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The packets of building from the capital go there and back: an order of a building, at a point or of a post, giving it up,
 * founding a city from the map, and the distance between cities with the thresholds of the levels in the view of the roads.
 */
class RemotePacketsTest {
    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    @Test
    void anOrderGoesThereAndBack() {
        UUID kingdom = UUID.randomUUID();
        ConstructionPackets.Commission order = new ConstructionPackets.Commission(kingdom, "at", "kingdomworld:house_small", "kingdom", "",
                0, -29_999_000, 29_999_000, 3, 2, false);
        FriendlyByteBuf buf = buffer();
        order.encode(buf);
        assertEquals(order, ConstructionPackets.Commission.decode(buf));
        assertEquals(0, buf.readableBytes());

        ConstructionPackets.CommissionCancel cancel = new ConstructionPackets.CommissionCancel(kingdom, Long.MAX_VALUE);
        buf = buffer();
        cancel.encode(buf);
        assertEquals(cancel, ConstructionPackets.CommissionCancel.decode(buf));
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void aCityFoundedFromTheMapGoesThereAndBack() {
        RoadPackets.Found found = new RoadPackets.Found(UUID.randomUUID(), -1_875_000, 1_875_000, "Новый Верхнеполянский Городок", true);
        FriendlyByteBuf buf = buffer();
        found.encode(buf);
        assertEquals(found, RoadPackets.Found.decode(buf));
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void theViewOfRoadsTellsTheDistanceAndTheLevels() {
        RoadViews.RoadTabView view = new RoadViews.RoadTabView(UUID.randomUUID(), true, false, 3, 0, 0, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), 208, 15, 7, 50, 25);
        FriendlyByteBuf buf = buffer();
        view.encode(buf);
        RoadViews.RoadTabView read = RoadViews.RoadTabView.decode(buf);
        assertEquals(0, buf.readableBytes());
        assertEquals(208, read.cityDistance());
        assertEquals(15, read.townPeople());
        assertEquals(7, read.townBuildings());
        assertEquals(50, read.cityPeople());
        assertEquals(25, read.cityBuildings());
    }
}
