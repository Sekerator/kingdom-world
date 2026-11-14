package io.github.sekerator.kingdomworld.common.network;

import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomActionPacket;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The throne screen learns whether the throne is being moved and how many days pass between moves, and asks to move it or to
 * call the move off.
 */
class ThroneMovePacketsTest {

    private static KingdomView view() {
        return new KingdomView(UUID.randomUUID(), "Valoria", 0x336699, "kw-king", 2, 10, 29, 14, new BlockPos(8, 64, 8),
                KingdomView.Relation.MEMBER, Role.KING, EnumSet.allOf(Permission.class), List.of(), List.of(), Map.of(), null, null,
                "gothic", 2_000L);
    }

    private static KingdomView roundTrip(KingdomView view) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        view.encode(buf);
        KingdomView read = KingdomView.decode(buf);
        assertEquals(0, buf.readableBytes());
        return read;
    }

    @Test
    void theViewTellsOfTheMoveOfTheThrone() {
        KingdomView plain = view();
        assertFalse(plain.throneMoving());
        assertEquals(0, plain.throneMoveDays());
        assertEquals(plain, roundTrip(plain));

        KingdomView moving = plain.withThroneMove(true, 3);
        KingdomView read = roundTrip(moving);
        assertEquals(moving, read);
        assertTrue(read.throneMoving());
        assertEquals(3, read.throneMoveDays());
        assertEquals("gothic", read.style());
        // Moving the throne survives the other changes of the view.
        assertTrue(moving.withNpc(null).withRuler(null).throneMoving());
        assertEquals(KingdomView.MAX_THRONE_MOVE_DAYS, plain.withThroneMove(false, Integer.MAX_VALUE).throneMoveDays());
        assertEquals(0, plain.withThroneMove(false, -5).throneMoveDays());
    }

    @Test
    void theScreenAsksToMoveTheThroneAndToCallItOff() {
        for (KingdomActionPacket.Action action : List.of(KingdomActionPacket.Action.MOVE_THRONE, KingdomActionPacket.Action.CANCEL_THRONE_MOVE)) {
            KingdomActionPacket packet = new KingdomActionPacket(action, null, 0);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            packet.encode(buf);
            assertEquals(packet, KingdomActionPacket.decode(buf));
        }
    }
}
