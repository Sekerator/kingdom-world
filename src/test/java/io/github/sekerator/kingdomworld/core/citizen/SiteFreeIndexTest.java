package io.github.sekerator.kingdomworld.core.citizen;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SiteFreeIndexTest {
    private static final String[] DIMENSIONS = {"minecraft:overworld", "minecraft:the_nether"};
    private static final String[] KINDS = {SiteKinds.BED, SiteKinds.INFIRMARY, "work_post"};

    @Test
    void theFreePlacesAreTheFreeOnesOfAllPlacesInTheirOrder() {
        SplittableRandom random = new SplittableRandom(41);
        SiteRegistry registry = new SiteRegistry();
        List<UUID> kingdoms = List.of(new UUID(2, 1), new UUID(2, 2), new UUID(2, 3));
        List<WorldPos> known = new ArrayList<>();
        for (int step = 0; step < 50_000; step++) {
            WorldPos pos = new WorldPos(DIMENSIONS[random.nextInt(DIMENSIONS.length)], random.nextInt(-30, 30), random.nextInt(60, 64),
                    random.nextInt(-30, 30));
            int op = random.nextInt(20);
            if (op < 8) {
                registry.put(pos, KINDS[random.nextInt(KINDS.length)], kingdoms.get(random.nextInt(kingdoms.size())));
                known.add(pos);
            } else if (op < 11 && !known.isEmpty()) {
                registry.remove(known.get(random.nextInt(known.size())));
            } else if (op < 17 && !known.isEmpty()) {
                registry.setOccupant(known.get(random.nextInt(known.size())), random.nextBoolean() ? null : UUID.randomUUID());
            } else if (op == 17) {
                registry.removeKingdom(kingdoms.get(random.nextInt(kingdoms.size())));
            } else if (registry.get(pos).isEmpty()) {
                registry.restore(new Site(pos, KINDS[random.nextInt(KINDS.length)], kingdoms.get(random.nextInt(kingdoms.size())),
                        random.nextBoolean() ? null : UUID.randomUUID()));
                known.add(pos);
            }
            UUID kingdom = kingdoms.get(random.nextInt(kingdoms.size()));
            String kind = KINDS[random.nextInt(KINDS.length)];
            List<Site> free = new ArrayList<>();
            for (Site site : registry.sitesOf(kingdom, kind)) {
                if (site.isFree()) {
                    free.add(site);
                }
            }
            assertEquals(free, registry.freeSitesOf(kingdom, kind), "the free places of " + kind + " at step " + step);
        }
    }
}
