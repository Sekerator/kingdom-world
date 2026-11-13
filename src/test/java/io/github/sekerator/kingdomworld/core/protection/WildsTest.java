package io.github.sekerator.kingdomworld.core.protection;

import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WildsTest {
    @Test
    void onlyTheUninhabitedLandOfAKingdomOfTheWorldIsFreeToPlayers() {
        AccessSubject.Kind player = AccessSubject.Kind.PLAYER;
        assertTrue(Wilds.free(true, Wilds.Owner.WORLD, false, false, player, ProtectedAction.BREAK));
        assertTrue(Wilds.free(true, Wilds.Owner.WORLD, false, false, player, ProtectedAction.CONTAINER));
        assertTrue(Wilds.free(true, Wilds.Owner.WORLD, false, false, player, ProtectedAction.USE_ITEM));
        // Where it lives the law holds: its claims and the chunks of its buildings, walls and roads.
        assertFalse(Wilds.free(true, Wilds.Owner.WORLD, true, false, player, ProtectedAction.BREAK));
        assertFalse(Wilds.free(true, Wilds.Owner.WORLD, false, true, player, ProtectedAction.BREAK));
        // The land of kingdoms of players is never wild, nor is the land of nobody a matter of the wilds.
        assertFalse(Wilds.free(true, Wilds.Owner.PLAYERS, false, false, player, ProtectedAction.BREAK));
        assertFalse(Wilds.free(true, Wilds.Owner.NOBODY, false, false, player, ProtectedAction.BREAK));
        // Only players; striking a citizen stays a matter of the law; closed wilds are not free.
        assertFalse(Wilds.free(true, Wilds.Owner.WORLD, false, false, AccessSubject.Kind.OTHER, ProtectedAction.BREAK));
        assertFalse(Wilds.free(true, Wilds.Owner.WORLD, false, false, player, ProtectedAction.ATTACK_CITIZEN));
        assertFalse(Wilds.free(false, Wilds.Owner.WORLD, false, false, player, ProtectedAction.BREAK));
    }

    @Test
    void theInhabitedChunksAreThoseOfTheBuildingsWallsAndRoads() {
        Random random = new Random(15);
        List<int[]> boxes = new ArrayList<>();
        List<int[]> blocks = new ArrayList<>();
        Wilds.Inhabited inhabited = new Wilds.Inhabited();
        for (int i = 0; i < 40; i++) {
            int x = random.nextInt(2_000) - 1_000;
            int z = random.nextInt(2_000) - 1_000;
            int[] box = {x, z, x + random.nextInt(40), z + random.nextInt(40)};
            boxes.add(box);
            inhabited.addBox("minecraft:overworld", box[0], box[1], box[2], box[3]);
        }
        for (int i = 0; i < 300; i++) {
            int[] block = {random.nextInt(4_000) - 2_000, random.nextInt(4_000) - 2_000};
            blocks.add(block);
            inhabited.addBlock("minecraft:overworld", block[0], block[1]);
        }
        // The set says what going through every box and block says, for every chunk around.
        for (int cx = -130; cx <= 130; cx += 3) {
            for (int cz = -130; cz <= 130; cz += 3) {
                boolean expected = false;
                for (int[] box : boxes) {
                    expected |= cx >= box[0] >> 4 && cx <= box[2] >> 4 && cz >= box[1] >> 4 && cz <= box[3] >> 4;
                }
                for (int[] block : blocks) {
                    expected |= block[0] >> 4 == cx && block[1] >> 4 == cz;
                }
                assertEquals(expected, inhabited.contains("minecraft:overworld", ChunkKey.of(cx, cz)), cx + " " + cz);
            }
        }
        assertFalse(inhabited.contains("minecraft:the_nether", ChunkKey.of(boxes.get(0)[0] >> 4, boxes.get(0)[1] >> 4)));
        assertTrue(inhabited.size() > 0);
    }
}
