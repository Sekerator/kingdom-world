package io.github.sekerator.kingdomworld.common.network.data;

import io.github.sekerator.kingdomworld.core.road.RoadClass;
import io.github.sekerator.kingdomworld.core.road.RouteStyle;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plan of a levelled road crosses the network whole: its stage, points, legs, earthworks, materials, work, way and profile.
 */
class PlanViewPacketTest {

    @Test
    void aPlanComesBackAsItWasSent() {
        UUID kingdom = new UUID(4, 2);
        RoadViews.PlanView.LegView leg = new RoadViews.PlanView.LegView(RoadViews.PlanView.Stage.FAILED, "blocked", -120, 45, 300, 900, 1800,
                new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11});
        int[] xs = new int[4_096];
        int[] zs = new int[4_096];
        byte[] kinds = new byte[4_096];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = i - 2_000;
            zs[i] = -i;
            kinds[i] = (byte) (i % 5);
        }
        int[] profile = new int[512 * 3];
        for (int i = 0; i < profile.length; i++) {
            profile[i] = 60 + i % 40;
        }
        RoadViews.PlanView view = new RoadViews.PlanView(77, kingdom, 12, "", RoadViews.PlanView.Stage.DONE, 100, 0, RoadClass.PAVED,
                RouteStyle.MODERATE, new int[]{0, 0, 50, 60, 120, -40}, new int[]{1, 0, 50, 61, 121, -40}, List.of(leg, leg), "Ostbrook",
                "Capital", 2_100, 1_800, 400, 900, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, new int[]{7, 6, 5, 4, 3, 2, 1},
                new int[]{0, 1, 0, 1, 0, 1, 0}, 54_000, 120_000, 6, 250, 83, 12_345, "Crowns", List.of("Northmarch"), 2, true, true, xs, zs,
                kinds, profile);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        view.encode(buf);
        RoadViews.PlanView back = RoadViews.PlanView.decode(buf);
        assertEquals(0, buf.readableBytes());
        assertEquals(view.planId(), back.planId());
        assertEquals(view.seq(), back.seq());
        assertEquals(view.stage(), back.stage());
        assertEquals(view.style(), back.style());
        assertEquals(view.roadClass(), back.roadClass());
        assertArrayEquals(view.snapped(), back.snapped());
        assertEquals(2, back.legs().size());
        assertEquals("blocked", back.legs().get(0).reason());
        assertEquals(-120, back.legs().get(0).stuckX());
        assertArrayEquals(view.works(), back.works());
        assertArrayEquals(view.missing(), back.missing());
        assertEquals(view.term(), back.term());
        assertEquals(view.coins(), back.coins());
        assertEquals(view.foreign(), back.foreign());
        assertArrayEquals(view.kinds(), back.kinds());
        assertArrayEquals(view.profile(), back.profile());
        assertTrue(back.guessed() && back.levelled() && !back.searching());
        RoadViews.PlanView waiting = RoadViews.PlanView.of(kingdom, 3, "", RoadViews.PlanView.Stage.READING, 40, 2, RoadClass.PATH,
                RouteStyle.TERRAIN);
        FriendlyByteBuf small = new FriendlyByteBuf(Unpooled.buffer());
        waiting.encode(small);
        RoadViews.PlanView again = RoadViews.PlanView.decode(small);
        assertTrue(again.searching());
        assertEquals(2, again.queue());
        assertEquals(-1, again.term());
    }
}
