package io.github.sekerator.kingdomworld.common.network.data;

import io.github.sekerator.kingdomworld.core.road.CityLevel;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The row of a city in the roads tab travels with its townsfolk and beds.
 */
class CityRowPacketTest {

    @Test
    void theRowOfACityTravelsWithItsTownsfolkAndBeds() {
        RoadViews.CityRow row = new RoadViews.CityRow(3, "Olkhovka", -120, 70, 4_000, false, 2, CityLevel.CITY, 64, 40, 9,
                Wall.WallClass.STONE, 1000, 4, 1_250_000, 400, 12);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        row.encode(buf);
        RoadViews.CityRow read = RoadViews.CityRow.decode(buf);
        assertEquals(row, read);
        assertEquals(1_250_000, read.folk());
        assertEquals(400, read.beds());
        assertEquals(12, read.freeBeds());
        assertEquals(0, buf.readableBytes());
    }
}
