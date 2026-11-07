package io.github.sekerator.kingdomworld.core.construction;

/**
 * How a building template is laid into the world: optionally mirrored front to back (x becomes -x), then turned clockwise
 * by quarter turns around its anchor. This is the order the game uses for its own structures (mirror, then rotation),
 * so block states turned by the game agree with the positions turned here.
 *
 * @param rotation quarter turns clockwise, 0 to 3
 * @param mirror   whether the template is mirrored before turning
 */
public record Placement(int rotation, boolean mirror) {
    public static final Placement NONE = new Placement(0, false);

    public Placement {
        rotation = Math.floorMod(rotation, 4);
    }

    /**
     * The placement turned a further quarter clockwise.
     */
    public Placement turned() {
        return new Placement(rotation + 1, mirror);
    }

    /**
     * The placement with the mirror switched.
     */
    public Placement flipped() {
        return new Placement(rotation, !mirror);
    }

    /**
     * Where a block lies relative to the anchor after placing, given where it lies relative to the anchor in the template.
     *
     * @return {x, y, z}
     */
    public int[] apply(int x, int y, int z) {
        int mx = mirror ? -x : x;
        return switch (rotation) {
            case 1 -> new int[]{-z, y, mx};
            case 2 -> new int[]{-mx, y, -z};
            case 3 -> new int[]{z, y, -mx};
            default -> new int[]{mx, y, z};
        };
    }

    /**
     * The template position (relative to the anchor) that ends up at the given placed position.
     *
     * @return {x, y, z}
     */
    public int[] invert(int x, int y, int z) {
        int tx;
        int tz;
        switch (rotation) {
            case 1 -> {
                tx = z;
                tz = -x;
            }
            case 2 -> {
                tx = -x;
                tz = -z;
            }
            case 3 -> {
                tx = -z;
                tz = x;
            }
            default -> {
                tx = x;
                tz = z;
            }
        }
        return new int[]{mirror ? -tx : tx, y, tz};
    }

    /**
     * Where a direction of the template points after placing.
     */
    public Facing apply(Facing facing) {
        Facing result = mirror && (facing == Facing.EAST || facing == Facing.WEST) ? facing.opposite() : facing;
        for (int i = 0; i < rotation; i++) {
            result = result.clockwise();
        }
        return result;
    }

    /**
     * The box a template of the given size takes in the world when its anchor (a position inside the template) is laid at
     * {@code ax, ay, az}.
     */
    public Box box(int sizeX, int sizeY, int sizeZ, int anchorX, int anchorY, int anchorZ, int ax, int ay, int az) {
        int[] a = apply(-anchorX, -anchorY, -anchorZ);
        int[] b = apply(sizeX - 1 - anchorX, sizeY - 1 - anchorY, sizeZ - 1 - anchorZ);
        return new Box(ax + Math.min(a[0], b[0]), ay + Math.min(a[1], b[1]), az + Math.min(a[2], b[2]),
                ax + Math.max(a[0], b[0]), ay + Math.max(a[1], b[1]), az + Math.max(a[2], b[2]));
    }
}
