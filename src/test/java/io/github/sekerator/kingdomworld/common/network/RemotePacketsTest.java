package io.github.sekerator.kingdomworld.common.network;

import io.github.sekerator.kingdomworld.common.network.data.ConstructionView;
import io.github.sekerator.kingdomworld.common.network.data.RoadViews;
import io.github.sekerator.kingdomworld.common.network.packet.ConstructionPackets;
import io.github.sekerator.kingdomworld.common.network.packet.RoadPackets;
import io.github.sekerator.kingdomworld.core.road.CityLevel;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The packets of building from the capital go there and back: an order of a building, at a point or of a post, giving it up,
 * founding a city from the map, the cities and orders in the view of construction with its filter by city, and the distance
 * between cities with the thresholds of the levels in the view of the roads.
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
    void theViewOfConstructionTellsTheCitiesAndTheOrders() {
        UUID kingdom = UUID.randomUUID();
        List<ConstructionView.Place> cities = List.of(new ConstructionView.Place(0, "Валория", 8, 8, 24, CityLevel.TOWN, 3, 0),
                new ConstructionView.Place(7, "Новый Верхнеполянский Городок", -29_999_999, 29_999_999, 48, CityLevel.CITY, 99_999, 12));
        List<ConstructionView.CommissionRow> orders = List.of(new ConstructionView.CommissionRow(4, "build", Component.literal("Дом"), 7, "no_place",
                Component.translatable("kingdomworld.commission.reason.blocked", "1 64 -3")),
                new ConstructionView.CommissionRow(5, "hall", Component.literal("Заречье"), ConstructionView.NO_CITY, "waiting", Component.empty()));
        ConstructionView view = new ConstructionView(kingdom, true, 0, List.of(), List.of(), List.of(), 32, 0, cities, orders, 7);
        FriendlyByteBuf buf = buffer();
        view.encode(buf);
        ConstructionView read = ConstructionView.decode(buf);
        assertEquals(0, buf.readableBytes());
        assertEquals(cities, read.cities());
        assertEquals(orders, read.commissions());
        assertEquals(7, read.cityFilter());
        assertEquals("Новый Верхнеполянский Городок", read.cityName(7));
        assertEquals("", read.cityName(3));

        ConstructionPackets.Request request = new ConstructionPackets.Request(kingdom, 7);
        buf = buffer();
        request.encode(buf);
        assertEquals(request, ConstructionPackets.Request.decode(buf));
        assertEquals(ConstructionView.ALL_CITIES, new ConstructionPackets.Request(kingdom).city());
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
