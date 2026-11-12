package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.npc.NpcComposition.Lot;
import io.github.sekerator.kingdomworld.core.npc.NpcComposition.Plot;
import io.github.sekerator.kingdomworld.core.npc.NpcComposition.Post;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The capitals of districts of old worlds must stay as they were planned: a raising goes on from the plan made anew from the
 * seed on every call, so a capital half raised would change under its builders. The fingerprints were taken from the plans
 * before the styled layout came; every change of them is a change of old capitals.
 */
class CityPlanGoldenTest {
    private static final NpcRules RULES = NpcRules.DEFAULT;
    private static final long[] SEEDS = {1L, 5L, 21L};
    /** By level (small, medium, large), then by seed as in {@link #SEEDS}. */
    private static final long[][] FINGERPRINTS = {
            {0xF517281E31B3AA01L, 0xD3EF7026868D1826L, 0x57692EB137DEDFFAL},
            {0xF56EC5D752CB2878L, 0x89B7B4A392C82138L, 0xFD25EB1F9D9C5294L},
            {0xA7248E146BCED66AL, 0x3FA7611E5E5EFFB6L, 0x6981D9A46D389811L}};

    static String describe(NpcComposition composition) {
        StringBuilder text = new StringBuilder();
        text.append(composition.layout()).append(' ').append(composition.level()).append(' ').append(composition.seed()).append('\n');
        for (Plot plot : composition.buildings()) {
            text.append(plot).append('\n');
        }
        for (Post post : composition.posts()) {
            text.append(post.name()).append(points(post.points())).append('\n');
        }
        for (Lot lot : composition.lots()) {
            text.append(lot).append('\n');
        }
        text.append(composition.jobs()).append('\n');
        text.append(composition.gates()).append('\n');
        text.append(points(composition.fieldSpots())).append('\n');
        text.append(composition.citizens()).append(' ').append(composition.soldiers()).append(' ').append(composition.fields()).append(' ')
                .append(composition.bunks()).append(' ').append(composition.wallRadius()).append(' ').append(composition.towns()).append(' ')
                .append(composition.townHouses()).append(' ').append(composition.rings()).append(' ').append(composition.stoneWall())
                .append(' ').append(composition.roadClass()).append('\n');
        text.append(composition.reach()).append(' ').append(composition.outerReach()).append(' ').append(composition.precinct()).append(' ')
                .append(composition.townWall()).append(' ').append(composition.townReach()).append(' ')
                .append(points(composition.townFields())).append('\n');
        for (int t = 0; t < composition.towns(); t++) {
            for (Plot plot : composition.town(t)) {
                text.append(t).append(' ').append(plot).append('\n');
            }
        }
        int reach = composition.outerReach();
        for (int x = -reach; x <= reach; x += 7) {
            for (int z = -reach; z <= reach; z += 7) {
                text.append(composition.ground(x, z));
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static String points(List<int[]> points) {
        List<String> list = new ArrayList<>();
        for (int[] point : points) {
            list.add(Arrays.toString(point));
        }
        return list.toString();
    }

    /**
     * FNV-1a over the bytes of the text: the same on every machine.
     */
    static long hash(String text) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & 0xff;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    @Test
    void theCapitalsOfDistrictsAreAsTheyWere() {
        List<String> changed = new ArrayList<>();
        for (NpcLevel level : NpcLevel.values()) {
            for (int i = 0; i < SEEDS.length; i++) {
                NpcComposition composition = NpcComposition.districts(level, SEEDS[i], RULES, RULES.towns(level));
                long actual = hash(describe(composition));
                if (actual != FINGERPRINTS[level.ordinal()][i]) {
                    changed.add(level + " seed " + SEEDS[i] + ": 0x" + Long.toHexString(actual).toUpperCase(Locale.ROOT) + "L");
                }
            }
        }
        assertTrue(changed.isEmpty(), "the plans of old capitals changed: " + changed);
    }
}
