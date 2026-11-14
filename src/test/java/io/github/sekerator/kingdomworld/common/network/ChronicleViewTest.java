package io.github.sekerator.kingdomworld.common.network;

import io.github.sekerator.kingdomworld.common.network.data.CitizenView;
import io.github.sekerator.kingdomworld.common.network.data.WorkforceView;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.DeathCause;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The chronicle travels to the client with who killed and the kind of damage; the window of the workers opened at a citizen
 * travels with the citizen.
 */
class ChronicleViewTest {

    @Test
    void theChronicleTravelsWithTheWholeCause() {
        List<CitizenView.Death> deaths = List.of(
                new CitizenView.Death(7, true, "farmer", CauseOfDeath.ANIMAL, "Rex", 12, new BlockPos(1, 64, -3), "minecraft:wolf", "mob"),
                new CitizenView.Death(8, false, "miner", CauseOfDeath.OTHER, "", 13, new BlockPos(2, 30, 4), "", "inWall"),
                new CitizenView.Death(9, false, "unemployed", CauseOfDeath.MINE, "", 14, BlockPos.ZERO));
        CitizenView view = new CitizenView(UUID.randomUUID(), true, 3, 2, 4, 5, 60, 2, CitizenNeeds.GrowthCheck.READY, List.of(), deaths,
                List.of());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        view.encode(buf);

        List<CitizenView.Death> read = CitizenView.decode(buf).chronicle();

        assertEquals(deaths, read);
        assertEquals(new DeathCause(CauseOfDeath.ANIMAL, "Rex", "minecraft:wolf", "mob"), read.get(0).deathCause());
        assertEquals(DeathCause.Telling.WORD, read.get(1).deathCause().telling());
    }

    @Test
    void theWindowOfTheWorkersTravelsWithTheCitizenItWasOpenedAt() {
        UUID citizen = UUID.randomUUID();
        WorkforceView.Query focus = new WorkforceView.Query(WorkforceView.Mode.CITIZENS, "", "", false, 2, 8, citizen, null);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        focus.encode(buf);

        WorkforceView.Query read = WorkforceView.Query.decode(buf);

        assertEquals(focus, read);
        assertNull(read.withoutFocus().citizen(), "the chosen citizen leaves the pages asked for later");
        assertEquals(2, read.withoutFocus().page());
        WorkforceView.Query choice = WorkforceView.Query.choosePost(citizen, 8);
        assertEquals(choice, choice.withoutFocus(), "the choices of a citizen keep it");
    }
}
