package io.github.sekerator.kingdomworld.core.road;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The way of a road packed for the file: the first block, then a byte for every step to the next block (a step along x and
 * z of at most one, up to 8 down or 7 up), a step any longer written out whole in a list of its own; what each block is
 * (ground, bridge, embankment and its depth) in runs of the same. A road of a thousand blocks takes about a thousand bytes
 * instead of sixteen thousand.
 *
 * <p>A step byte holds {@code dx + 1} in bits 0-1, {@code dz + 1} in bits 2-3 and {@code dy + 8} in bits 4-7; {@code dx + 1}
 * of 3 marks a block written out whole, the next three numbers of the jumps.
 */
public final class RoadCells {
    private static final int JUMP = 3;

    /**
     * A way packed.
     *
     * @param start the first block: x, y, z
     * @param steps a byte for every block after the first
     * @param jumps x, y, z of every block a step could not reach, in order
     * @param kinds what the blocks are, in runs: the kind times 256 plus the depth, then how many blocks
     */
    public record Packed(int[] start, byte[] steps, int[] jumps, int[] kinds) {
    }

    private RoadCells() {
    }

    public static Packed pack(List<RouteCell> cells) {
        if (cells.isEmpty()) {
            return new Packed(new int[0], new byte[0], new int[0], new int[0]);
        }
        RouteCell first = cells.get(0);
        ByteArrayOutputStream steps = new ByteArrayOutputStream(cells.size());
        List<Integer> jumps = new ArrayList<>();
        List<Integer> kinds = new ArrayList<>();
        int run = 0;
        int value = kindOf(first);
        for (int i = 1; i < cells.size(); i++) {
            RouteCell before = cells.get(i - 1);
            RouteCell cell = cells.get(i);
            int dx = cell.x() - before.x();
            int dy = cell.y() - before.y();
            int dz = cell.z() - before.z();
            if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && dy >= -8 && dy <= 7) {
                steps.write((dx + 1) | (dz + 1) << 2 | (dy + 8) << 4);
            } else {
                steps.write(JUMP);
                jumps.add(cell.x());
                jumps.add(cell.y());
                jumps.add(cell.z());
            }
        }
        for (RouteCell cell : cells) {
            int kind = kindOf(cell);
            if (kind == value) {
                run++;
            } else {
                kinds.add(value);
                kinds.add(run);
                value = kind;
                run = 1;
            }
        }
        kinds.add(value);
        kinds.add(run);
        return new Packed(new int[]{first.x(), first.y(), first.z()}, steps.toByteArray(),
                jumps.stream().mapToInt(Integer::intValue).toArray(), kinds.stream().mapToInt(Integer::intValue).toArray());
    }

    private static int kindOf(RouteCell cell) {
        return cell.kind().ordinal() * 256 + Math.min(255, cell.depth());
    }

    /**
     * The way again, as it was packed; the owners of the land are not kept.
     *
     * @throws IllegalArgumentException when the arrays do not fit one another
     */
    public static List<RouteCell> unpack(Packed packed) {
        if (packed.start().length == 0) {
            if (packed.steps().length != 0) {
                throw new IllegalArgumentException("steps of a road without a start");
            }
            return List.of();
        }
        if (packed.start().length != 3 || packed.jumps().length % 3 != 0 || packed.kinds().length % 2 != 0) {
            throw new IllegalArgumentException("the way of a road is damaged");
        }
        int count = packed.steps().length + 1;
        int[] kinds = new int[count];
        int at = 0;
        for (int i = 0; i < packed.kinds().length; i += 2) {
            int value = packed.kinds()[i];
            int run = packed.kinds()[i + 1];
            if (run < 0 || at + run > count || value < 0 || value / 256 >= RouteCell.Kind.values().length) {
                throw new IllegalArgumentException("the kinds of the blocks of a road are damaged");
            }
            for (int k = 0; k < run; k++) {
                kinds[at++] = value;
            }
        }
        if (at != count) {
            throw new IllegalArgumentException("the kinds of the blocks of a road do not match its way");
        }
        List<RouteCell> cells = new ArrayList<>(count);
        int x = packed.start()[0];
        int y = packed.start()[1];
        int z = packed.start()[2];
        cells.add(cell(x, y, z, kinds[0]));
        int jump = 0;
        for (int i = 0; i < packed.steps().length; i++) {
            int step = packed.steps()[i] & 0xFF;
            if ((step & 3) == JUMP) {
                if (jump + 3 > packed.jumps().length) {
                    throw new IllegalArgumentException("a jump of a road is missing");
                }
                x = packed.jumps()[jump];
                y = packed.jumps()[jump + 1];
                z = packed.jumps()[jump + 2];
                jump += 3;
            } else {
                x += (step & 3) - 1;
                z += ((step >> 2) & 3) - 1;
                y += ((step >> 4) & 15) - 8;
            }
            cells.add(cell(x, y, z, kinds[i + 1]));
        }
        if (jump != packed.jumps().length) {
            throw new IllegalArgumentException("jumps of a road are left over");
        }
        return cells;
    }

    private static RouteCell cell(int x, int y, int z, int kind) {
        return new RouteCell(x, y, z, RouteCell.Kind.values()[kind / 256], kind % 256, null);
    }
}
