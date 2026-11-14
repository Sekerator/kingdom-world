package io.github.sekerator.kingdomworld.common.network;

import io.github.sekerator.kingdomworld.common.network.data.ConstructionView;
import io.github.sekerator.kingdomworld.common.network.data.RoadViews;
import io.github.sekerator.kingdomworld.common.network.data.WorkforceView;
import io.github.sekerator.kingdomworld.common.network.packet.ConstructionPackets;
import io.github.sekerator.kingdomworld.common.network.packet.RoadPackets;
import io.github.sekerator.kingdomworld.common.network.packet.WorkforcePackets;
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

    @Test
    void theGovernorsOfCitiesGoThereAndBack() {
        UUID kingdom = UUID.randomUUID();
        RoadViews.CityRow row = new RoadViews.CityRow(4, "Ольховка", 100, 64, -200, false, 2, io.github.sekerator.kingdomworld.core.road.CityLevel.TOWN,
                30, 12, 0, null, 0, 0, 936, 40, 7, 2, 1, 3).withGovernor(RoadViews.CityRow.HOUSE, "Стрелостенград", "baron", true,
                net.minecraft.network.chat.Component.translatable("kingdomworld.governor.note.builds", "Малый дом"));
        RoadViews.RoadTabView view = new RoadViews.RoadTabView(kingdom, true, false, 3, 0, 0, List.of(row), List.of(), List.of(), List.of(),
                List.of(), List.of(), 112, 12, 6, 40, 20, true, 300, 120, 2,
                new io.github.sekerator.kingdomworld.common.network.data.TradeViews.Currency(kingdom, "крона", "Северная Корона", 0x2E6E9E, 10, 100, 1),
                true);
        FriendlyByteBuf buf = buffer();
        view.encode(buf);
        RoadViews.RoadTabView read = RoadViews.RoadTabView.decode(buf);
        assertEquals(0, buf.readableBytes());
        assertEquals(300, read.allowance());
        assertEquals(120, read.spentToday());
        assertEquals(2, read.governed());
        assertEquals("крона", read.currency().name());
        assertEquals(true, read.king());
        assertEquals(true, read.treasurer());
        RoadViews.CityRow back = read.cities().get(0);
        assertEquals(RoadViews.CityRow.HOUSE, back.governor());
        assertEquals("Стрелостенград", back.governorName());
        assertEquals("baron", back.governorTitle());
        assertEquals(true, back.paused());
        assertEquals(row.note(), back.note());
        assertEquals(936, back.folk());
        RoadViews.RoadTabView plain = new RoadViews.RoadTabView(kingdom, true, false, 3, 0, 0, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());
        buf = buffer();
        plain.encode(buf);
        RoadViews.RoadTabView none = RoadViews.RoadTabView.decode(buf);
        assertEquals(null, none.currency(), "a kingdom without a treasury sends no currency");
        assertEquals(0, none.allowance());
    }

    @Test
    void anOfficeIsAskedForWithItsCity() {
        WorkforcePackets.Action action = new WorkforcePackets.Action(UUID.randomUUID(), WorkforcePackets.Action.Kind.BARON, UUID.randomUUID(), null,
                "c12", WorkforceView.Query.of(WorkforceView.Mode.CITIZENS, 8));
        FriendlyByteBuf buf = buffer();
        action.encode(buf);
        assertEquals(action, WorkforcePackets.Action.decode(buf));
    }
}
