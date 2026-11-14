package io.github.sekerator.kingdomworld.common.network;

import io.github.sekerator.kingdomworld.common.network.data.WorkforceView;
import io.github.sekerator.kingdomworld.common.network.packet.CitizenMapPackets;
import io.github.sekerator.kingdomworld.common.network.packet.WorkforcePackets;
import io.github.sekerator.kingdomworld.core.citizen.Workforce;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The packets of where citizens are: the list asked for by where they are, the answer to "Find", and the citizens on the atlas.
 */
class WhereaboutsPacketsTest {

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    @Test
    void theListTravelsWithWhereTheCitizensAre() {
        WorkforceView.Query query = WorkforceView.Query.of(WorkforceView.Mode.CITIZENS, 8).withWhere(WorkforceView.Where.AWAY).withPage(2);
        FriendlyByteBuf buf = buffer();
        query.encode(buf);
        WorkforceView.Query read = WorkforceView.Query.decode(buf);
        assertEquals(query, read);
        assertEquals(WorkforceView.Where.AWAY, read.where());
        assertEquals(0, WorkforceView.Query.of(WorkforceView.Mode.CITIZENS, 8).withPage(3).withWhere(WorkforceView.Where.NEAREST).page(),
                "another order starts from the first page");
        assertEquals(WorkforceView.Where.NEAREST, read.withWhere(WorkforceView.Where.NEAREST).withFilters("", "", true).where(),
                "filters keep the order");
    }

    @Test
    void theAnswerToFindTravels() {
        WorkforcePackets.Found found = new WorkforcePackets.Found(UUID.randomUUID(), 42, true, 1234, "minecraft:overworld",
                new BlockPos(312, 70, -40), true);
        FriendlyByteBuf buf = buffer();
        found.encode(buf);
        assertEquals(found, WorkforcePackets.Found.decode(buf));
        WorkforcePackets.Found far = new WorkforcePackets.Found(UUID.randomUUID(), 7, false, -1, "minecraft:the_nether", BlockPos.ZERO, false);
        buf = buffer();
        far.encode(buf);
        assertEquals(far, WorkforcePackets.Found.decode(buf), "a citizen without a body has no entity");
    }

    @Test
    void theCitizensOfTheAtlasTravel() {
        CitizenMapPackets.View view = new CitizenMapPackets.View(true, UUID.randomUUID(), "minecraft:overworld", List.of(
                new CitizenMapPackets.Dot(UUID.randomUUID(), 1, false, "farmer", Workforce.Doing.WORKING, 10, -20, true, false),
                new CitizenMapPackets.Dot(UUID.randomUUID(), 2, true, "lumberjack", Workforce.Doing.RETURNING, -500, 900, false, true)));
        FriendlyByteBuf buf = buffer();
        view.encode(buf);
        CitizenMapPackets.View read = CitizenMapPackets.View.decode(buf);
        assertEquals(view, read);
        buf = buffer();
        CitizenMapPackets.View.NONE.encode(buf);
        assertTrue(!CitizenMapPackets.View.decode(buf).allowed(), "one who does not manage the citizens sees none");
    }
}
