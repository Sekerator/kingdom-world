package io.github.sekerator.kingdomworld.core.siege;

import io.github.sekerator.kingdomworld.core.combat.Side;
import io.github.sekerator.kingdomworld.core.diplomacy.WarGoal;
import io.github.sekerator.kingdomworld.core.npc.Settlement;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.Province;

import java.util.Arrays;
import java.util.function.LongSupplier;
import java.util.function.LongToIntFunction;

/**
 * Where and how a province of a state is held in a war: the square of its main settlement while people live there, else the
 * boundary stone in its middle (a place in the data, no block). Far from players a squad of the attackers standing there fights
 * the garrison of the settlement and the squads of the defenders by their numbers; only when nobody of them is left does the
 * hold go on.
 */
public final class ProvinceHolds {
    /** Blocks from the place of a province within which a squad holds or defends it far from players. */
    public static final int REACH = 24;
    /** Game ticks between two battles at the same place far from players. */
    public static final long BATTLE_TICKS = 1_200;
    /** The strength of a guard of a settlement in a battle far from players (a recruit with bare hands has 10). */
    public static final double GUARD_STRENGTH = 25.0;
    /** The strength of a man of the militia of a village: a tool in his hands. */
    public static final double MILITIA_STRENGTH = 12.0;
    /** Rings of chunks around the middle of a province searched for a chunk of it when the middle lies outside it. */
    static final int STONE_SEARCH = 8;

    private ProvinceHolds() {
    }

    /**
     * The place where a province is held.
     *
     * @param province the province
     * @param settled  whether its main settlement stands: then the square of the settlement is held, else the boundary stone
     * @param seat     the main settlement, 0 for an empty province
     * @param x        the block in the middle of the place: the square of the settlement, or the middle of the chunk of the stone
     * @param y        the ground of the settlement, 0 when it is not known (the whole height counts then)
     * @param z        the block in the middle of the place
     */
    public record Target(int province, boolean settled, int seat, int x, int y, int z) {
        /**
         * The chunk of the place.
         */
        public long chunk() {
            return ChunkKey.of(x >> 4, z >> 4);
        }

        /**
         * Whether a block stands on the place: within the square of the settlement, or in the chunk of the stone.
         *
         * @param plazaRadius blocks from the middle of a square to its edge
         */
        public boolean contains(int blockX, int blockZ, int plazaRadius) {
            if (settled) {
                return Math.abs(blockX - x) <= plazaRadius && Math.abs(blockZ - z) <= plazaRadius;
            }
            return blockX >> 4 == x >> 4 && blockZ >> 4 == z >> 4;
        }

        /**
         * Whether a squad standing at a block is at the place far from players: within {@code reach} blocks each way.
         */
        public boolean reaches(int blockX, int blockZ, int reach) {
            return Math.abs(blockX - x) <= reach && Math.abs(blockZ - z) <= reach;
        }

        /**
         * The western and northern edge and the eastern and southern edge (not included) of the place, in blocks:
         * {@code {minX, minZ, maxX, maxZ}}.
         */
        public int[] bounds(int plazaRadius) {
            if (settled) {
                return new int[]{x - plazaRadius, z - plazaRadius, x + plazaRadius + 1, z + plazaRadius + 1};
            }
            int minX = (x >> 4) << 4;
            int minZ = (z >> 4) << 4;
            return new int[]{minX, minZ, minX + 16, minZ + 16};
        }
    }

    /**
     * Where a province is held: the square of its main settlement when that stands (exists and is not ruined), else its
     * boundary stone.
     *
     * @param seat       the main settlement of the province, {@code null} when there is none
     * @param provinceAt the province of a chunk
     * @param fromTiles  the first chunk of the province found in its tiles, asked only when no chunk of it lies near its
     *                   middle
     */
    public static Target target(Province province, Settlement seat, LongToIntFunction provinceAt, LongSupplier fromTiles) {
        if (settled(province, seat)) {
            return new Target(province.id(), true, seat.id(), seat.x(), seat.groundY(), seat.z());
        }
        return stone(province.id(), stoneChunk(province, provinceAt, fromTiles));
    }

    /**
     * The boundary stone of an empty province standing in a chunk: the place is the middle of the chunk, the whole height of it.
     */
    public static Target stone(int province, long chunk) {
        return new Target(province, false, 0, ChunkKey.x(chunk) * 16 + 8, 0, ChunkKey.z(chunk) * 16 + 8);
    }

    /**
     * Whether people live in a province: it has a main settlement, and that stands.
     *
     * @param seat the settlement its {@link Province#seat()} names, {@code null} when there is none
     */
    public static boolean settled(Province province, Settlement seat) {
        return province.seat() != 0 && seat != null && seat.id() == province.seat() && seat.status() != Settlement.Status.RUINED;
    }

    /**
     * The chunk of the boundary stone of a province: the chunk of its middle when that lies in it, else the chunk of it
     * nearest to the middle within {@value #STONE_SEARCH} chunks, else the first chunk of it in its tiles.
     */
    public static long stoneChunk(Province province, LongToIntFunction provinceAt, LongSupplier fromTiles) {
        int cx = province.centerX() >> 4;
        int cz = province.centerZ() >> 4;
        long middle = ChunkKey.of(cx, cz);
        if (provinceAt.applyAsInt(middle) == province.id()) {
            return middle;
        }
        for (int ring = 1; ring <= STONE_SEARCH; ring++) {
            long best = 0;
            long bestDistance = Long.MAX_VALUE;
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    long chunk = ChunkKey.of(cx + dx, cz + dz);
                    long distance = (long) dx * dx + (long) dz * dz;
                    if (distance < bestDistance && provinceAt.applyAsInt(chunk) == province.id()) {
                        best = chunk;
                        bestDistance = distance;
                    }
                }
            }
            if (bestDistance != Long.MAX_VALUE) {
                return best;
            }
        }
        return fromTiles.getAsLong();
    }

    /**
     * The provinces that a square of chunks around a block touches, each once, 0 left out: a place of any of them may lie
     * near the block.
     *
     * @param chunks chunks from the chunk of the block to the edge of the square
     */
    public static int[] near(int blockX, int blockZ, int chunks, LongToIntFunction provinceAt) {
        int cx = blockX >> 4;
        int cz = blockZ >> 4;
        int radius = Math.max(0, chunks);
        int[] found = new int[(2 * radius + 1) * (2 * radius + 1)];
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int id = provinceAt.applyAsInt(ChunkKey.of(cx + dx, cz + dz));
                if (id == 0) {
                    continue;
                }
                boolean seen = false;
                for (int i = 0; i < count; i++) {
                    if (found[i] == id) {
                        seen = true;
                        break;
                    }
                }
                if (!seen) {
                    found[count++] = id;
                }
            }
        }
        return Arrays.copyOf(found, count);
    }

    /**
     * Whether a war takes a province: a war for land any province of the defender but the one of its capital, a war for a
     * city one where people live, never the one of the capital; a war for the throne takes the throne hall alone.
     *
     * @param capital the province of the capital of the defender, 0 for none
     */
    public static boolean eligible(WarGoal goal, int province, boolean settled, int capital) {
        if (province <= 0 || province == capital) {
            return false;
        }
        return switch (goal) {
            case LAND -> true;
            case CITY -> settled;
            default -> false;
        };
    }

    /**
     * The garrison of a settlement as a side of a battle: its guards and its militia by their numbers.
     */
    public static Side garrison(int guards, int militia) {
        int g = Math.max(0, guards);
        int m = Math.max(0, militia);
        return Side.crowd(Side.Kind.ARMY, g * GUARD_STRENGTH + m * MILITIA_STRENGTH, g + m);
    }

    /**
     * Whether the attackers at a place fight its defenders now: never before {@value #BATTLE_TICKS} ticks have gone since the
     * last battle there.
     *
     * @param last the tick of the last battle there, {@code null} for none
     */
    public static boolean battleDue(Long last, long now) {
        return last == null || now < last || now - last >= BATTLE_TICKS;
    }
}
