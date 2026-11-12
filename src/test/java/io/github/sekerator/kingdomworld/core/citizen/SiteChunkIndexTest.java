package io.github.sekerator.kingdomworld.core.citizen;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SiteChunkIndexTest {
    private static final String[] DIMENSIONS = {"minecraft:overworld", "minecraft:the_nether"};
    private static final String[] KINDS = {SiteKinds.BED, SiteKinds.INFIRMARY, "work_post"};

    @Test
    void thePlacesOfAChunkAreFoundAsByLookingAtEveryPlace() {
        SplittableRandom random = new SplittableRandom(23);
        SiteRegistry registry = new SiteRegistry();
        List<UUID> kingdoms = List.of(new UUID(1, 1), new UUID(1, 2), new UUID(1, 3));
        List<WorldPos> known = new ArrayList<>();
        for (int step = 0; step < 50_000; step++) {
            WorldPos pos = new WorldPos(DIMENSIONS[random.nextInt(DIMENSIONS.length)], random.nextInt(-40, 40), random.nextInt(60, 64),
                    random.nextInt(-40, 40));
            int op = random.nextInt(20);
            if (op < 8) {
                registry.put(pos, KINDS[random.nextInt(KINDS.length)], kingdoms.get(random.nextInt(kingdoms.size())));
                known.add(pos);
            } else if (op < 12 && !known.isEmpty()) {
                registry.remove(known.get(random.nextInt(known.size())));
            } else if (op < 16 && !known.isEmpty()) {
                registry.setOccupant(known.get(random.nextInt(known.size())), random.nextBoolean() ? null : UUID.randomUUID());
            } else if (op == 16) {
                registry.removeKingdom(kingdoms.get(random.nextInt(kingdoms.size())));
            } else if (op == 17 && registry.get(pos).isEmpty()) {
                registry.restore(new Site(pos, KINDS[random.nextInt(KINDS.length)], kingdoms.get(random.nextInt(kingdoms.size())), null));
                known.add(pos);
            }
            WorldPos probe = random.nextBoolean() || known.isEmpty() ? pos : known.get(random.nextInt(known.size()));
            assertEquals(registry.sitesInChunkByLooking(probe.dimension(), probe.chunk()), registry.sitesInChunk(probe.dimension(), probe.chunk()),
                    "the places of the chunk of " + probe + " at step " + step);
        }
    }
}
