package io.github.sekerator.kingdomworld.core.protection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The wilds of a kingdom of the world: the land of its provinces where nobody lives. A player is as free there as on land of
 * nobody; the law of the kingdom holds where it lives: its claimed chunks (the land of its capital and towns) and the chunks
 * its buildings, walls and roads stand in. The land of kingdoms of players is not wild, whoever holds it.
 */
public final class Wilds {
    /** Who owns a chunk. */
    public enum Owner {
        NOBODY, PLAYERS, WORLD
    }

    private Wilds() {
    }

    /**
     * Whether a player may do anything in a chunk as on land of nobody, the law not asking: the chunk is land of a kingdom of
     * the world that nobody lives in, and the wilds are open. Striking a citizen stays a matter of the law everywhere.
     *
     * @param claimed   whether the chunk is claimed land (a capital or a town)
     * @param inhabited whether a building, a wall or a road of the kingdom stands in the chunk
     */
    public static boolean free(boolean open, Owner owner, boolean claimed, boolean inhabited, AccessSubject.Kind subject,
                               ProtectedAction action) {
        return open && owner == Owner.WORLD && !claimed && !inhabited && subject == AccessSubject.Kind.PLAYER
                && action != ProtectedAction.ATTACK_CITIZEN;
    }

    /**
     * The chunks a kingdom of the world lives in beyond its claims, by dimension: those its buildings, walls and roads stand in.
     */
    public static final class Inhabited {
        private final Map<String, Set<Long>> chunks = new HashMap<>();

        /** Adds the chunks a box of blocks lies in. */
        public void addBox(String dimension, int minX, int minZ, int maxX, int maxZ) {
            Set<Long> set = chunks.computeIfAbsent(dimension, d -> new HashSet<>());
            for (int cx = Math.min(minX, maxX) >> 4; cx <= Math.max(minX, maxX) >> 4; cx++) {
                for (int cz = Math.min(minZ, maxZ) >> 4; cz <= Math.max(minZ, maxZ) >> 4; cz++) {
                    set.add(key(cx, cz));
                }
            }
        }

        /** Adds the chunk a block lies in. */
        public void addBlock(String dimension, int x, int z) {
            chunks.computeIfAbsent(dimension, d -> new HashSet<>()).add(key(x >> 4, z >> 4));
        }

        public boolean contains(String dimension, long chunk) {
            Set<Long> set = chunks.get(dimension);
            return set != null && set.contains(chunk);
        }

        public int size() {
            return chunks.values().stream().mapToInt(Set::size).sum();
        }

        /** The key of a chunk, as the territory keys it: x in the low half, z in the high half. */
        public static long key(int chunkX, int chunkZ) {
            return io.github.sekerator.kingdomworld.core.territory.ChunkKey.of(chunkX, chunkZ);
        }
    }
}
