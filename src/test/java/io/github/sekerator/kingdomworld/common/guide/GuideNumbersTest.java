package io.github.sekerator.kingdomworld.common.guide;

import io.github.sekerator.kingdomworld.common.network.packet.GuidePackets;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideNumbersTest {

    @Test
    void knowsThePlainValuesOfTheConfigurationOfTheServer() {
        Map<String, String> defaults = GuideNumbers.defaults();
        assertEquals("25", defaults.get("territory.baseMaxChunks"));
        assertEquals("4", defaults.get("territory.chunksPerMember"));
        assertEquals("2", defaults.get("territory.chunksPerCitizen"));
        assertEquals("0.05, 0.08, 0.12, 0.2, 0.35, 0.5, 1, 1, 1", defaults.get("guild.crystalChance"));
        // Lists of ids are no numbers to show.
        assertFalse(defaults.containsKey("kingdom.allowedDimensions"));
        assertTrue(GuideNumbers.value("kingdom.allowedDimensions").isEmpty());
        assertTrue(GuideNumbers.value("territory").isEmpty());
        assertTrue(GuideNumbers.value("no.such.value").isEmpty());
        assertTrue(GuideNumbers.value("x".repeat(GuideNumbers.MAX_PATH + 1)).isEmpty());
        assertEquals("25", GuideNumbers.defaultValue("territory.baseMaxChunks").orElseThrow());
    }

    @Test
    void answersWithTheValuesOfTheServerOrTheDefaults() {
        // No server runs in a unit test: the values are the defaults.
        Set<String> allowed = Set.of("territory.baseMaxChunks", "no.such.value", "kingdom.allowedDimensions");
        Map<String, String> values = GuideNumbers.current(List.of("territory.baseMaxChunks", "no.such.value", "kingdom.allowedDimensions",
                "territory.chunksPerMember"), allowed);
        assertEquals(Map.of("territory.baseMaxChunks", "25"), values);
    }

    @Test
    void tellsOnlyWhatTheGuideShows() {
        Path guide = Path.of(System.getProperty("kingdomworld.mainSourceDir")).getParent().resolve("resources/assets/kingdomworld/guide");
        Set<String> shown = GuideNumbers.shownBy(guide);
        assertTrue(shown.contains("territory.baseMaxChunks"), shown.toString());
        assertTrue(shown.contains("construction.earthworkMaxSize"), shown.toString());
        assertFalse(shown.contains("trade.caravanLossChance"), "a value no page shows");
        assertEquals(Map.of(), GuideNumbers.current(List.of("trade.caravanLossChance"), shown));
        assertTrue(GuideNumbers.shownBy(guide.resolve("missing")).isEmpty());
    }

    @Test
    void packetsTravelWhole() {
        GuidePackets.NumbersRequest request = new GuidePackets.NumbersRequest(List.of("territory.baseMaxChunks", "guild.crystalChance"));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        request.encode(buf);
        assertEquals(request, GuidePackets.NumbersRequest.decode(buf));

        GuidePackets.Numbers numbers = new GuidePackets.Numbers(Map.of("territory.baseMaxChunks", "40", "guild.crystalChance", "0.1, 0.2"));
        buf = new FriendlyByteBuf(Unpooled.buffer());
        numbers.encode(buf);
        assertEquals(numbers, GuidePackets.Numbers.decode(buf));

        buf = new FriendlyByteBuf(Unpooled.buffer());
        new GuidePackets.Open().encode(buf);
        assertEquals(new GuidePackets.Open(), GuidePackets.Open.decode(buf));
    }

    @Test
    void refusesTooManyPaths() {
        List<String> paths = new ArrayList<>();
        for (int i = 0; i <= GuideNumbers.MAX_PATHS; i++) {
            paths.add("p" + i);
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new GuidePackets.NumbersRequest(paths).encode(buf);
        assertThrows(IllegalArgumentException.class, () -> GuidePackets.NumbersRequest.decode(buf));
    }
}
