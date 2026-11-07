package io.github.sekerator.kingdomworld.core.road;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The wall of a city: a ring of blocks around it on the lay of the land, built in sections like a road (paid block by block
 * from the storehouses by builders, placed when its place is loaded), with gates where roads cross it and where the king
 * wants one. A finished wall and its gates enter the register of buildings as fortifications.
 *
 * <p>Not thread safe; the server uses it from the server thread only.
 */
public final class Wall {
    /** Blocks of the ring in one section. */
    public static final int SECTION_CELLS = 16;
    /** Blocks of a gate on each side of its middle. */
    public static final int GATE_HALF = 1;

    /**
     * What the wall is made of and how high it is.
     */
    public enum WallClass {
        /** A palisade of logs. */
        PALISADE(4),
        /** A stone wall with battlements. */
        STONE(5);

        private final int height;

        WallClass(int height) {
            this.height = height;
        }

        public int height() {
            return height;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<WallClass> byId(String id) {
            for (WallClass value : values()) {
                if (value.id().equals(id)) {
                    return Optional.of(value);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * A block of the wall: a column of {@code height} blocks standing on the ground at {@code y}.
     *
     * @param cell the index of the block of the ring
     */
    public record Block(int cell, int x, int y, int z, int height) {
    }

    private final long id;
    private final long cityId;
    private final UUID kingdomId;
    private final WallClass wallClass;
    private final String dimension;
    private final int centerX;
    private final int centerZ;
    private final List<RouteCell> cells;
    private final TreeSet<Integer> gates = new TreeSet<>();
    private final List<Road.Section> sections = new ArrayList<>();
    private final List<Block> blocks = new ArrayList<>();
    private final long created;
    private long buildingId;
    private final java.util.Set<Long> placedBlocks = new java.util.HashSet<>();

    public Wall(long id, long cityId, UUID kingdomId, WallClass wallClass, String dimension, int centerX, int centerZ, List<RouteCell> cells,
                Collection<Integer> gates, long created) {
        this.id = id;
        this.cityId = cityId;
        this.kingdomId = Objects.requireNonNull(kingdomId, "kingdomId");
        this.wallClass = Objects.requireNonNull(wallClass, "wallClass");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.centerX = centerX;
        this.centerZ = centerZ;
        if (cells.size() < 4 || cells.size() > Road.MAX_CELLS) {
            throw new IllegalArgumentException("A wall needs between 4 and " + Road.MAX_CELLS + " blocks, not " + cells.size());
        }
        List<RouteCell> plain = new ArrayList<>();
        for (RouteCell cell : cells) {
            plain.add(cell.withoutOwner());
        }
        this.cells = List.copyOf(plain);
        this.created = created;
        for (int gate : gates) {
            if (gate >= 0 && gate < this.cells.size()) {
                this.gates.add(gate);
            }
        }
        layout();
    }

    /**
     * The blocks and the sections, again after the gates changed: no wall stands in a gate.
     */
    private void layout() {
        blocks.clear();
        for (int i = 0; i < cells.size(); i++) {
            if (inGate(i)) {
                continue;
            }
            RouteCell cell = cells.get(i);
            // A stone wall has battlements: every other block one higher.
            int height = wallClass.height() + (wallClass == WallClass.STONE && i % 2 == 0 ? 1 : 0);
            blocks.add(new Block(i, cell.x(), cell.y(), cell.z(), height));
        }
        List<Road.Section> old = new ArrayList<>(sections);
        sections.clear();
        int block = 0;
        for (int s = 0; s * SECTION_CELLS < cells.size(); s++) {
            int first = s * SECTION_CELLS;
            int end = Math.min(cells.size(), first + SECTION_CELLS);
            int firstBlock = block;
            while (block < blocks.size() && blocks.get(block).cell() < end) {
                block++;
            }
            Road.Section section = new Road.Section(s, first, end, firstBlock, block, kingdomId);
            if (s < old.size()) {
                Road.Section was = old.get(s);
                // A gate cut into a built section keeps what was paid, up to the blocks left.
                section.restore(Math.min(was.paid(), section.units()), Math.min(was.placed(), section.units()), was.condition(), false, 0,
                        was.pool());
            }
            sections.add(section);
        }
    }

    public boolean inGate(int cell) {
        for (int gate : gates) {
            int distance = Math.abs(gate - cell);
            distance = Math.min(distance, cells.size() - distance);
            if (distance <= GATE_HALF) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cuts a gate at a block of the ring.
     *
     * @return whether it is a new gate
     */
    public boolean addGate(int cell) {
        if (cell < 0 || cell >= cells.size() || inGate(cell)) {
            return false;
        }
        gates.add(cell);
        layout();
        return true;
    }

    /**
     * The block of the ring nearest to a place.
     */
    public int nearestCell(int x, int z) {
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < cells.size(); i++) {
            RouteCell cell = cells.get(i);
            long dx = cell.x() - x;
            long dz = cell.z() - z;
            if (dx * dx + dz * dz < bestDistance) {
                best = i;
                bestDistance = dx * dx + dz * dz;
            }
        }
        return best;
    }

    public long id() {
        return id;
    }

    public long cityId() {
        return cityId;
    }

    public UUID kingdomId() {
        return kingdomId;
    }

    public WallClass wallClass() {
        return wallClass;
    }

    public String dimension() {
        return dimension;
    }

    public int centerX() {
        return centerX;
    }

    public int centerZ() {
        return centerZ;
    }

    public List<RouteCell> cells() {
        return cells;
    }

    public List<Integer> gates() {
        return List.copyOf(gates);
    }

    /**
     * Where each gate is: the middle block of its opening.
     */
    public List<RouteCell> gateCells() {
        List<RouteCell> result = new ArrayList<>();
        for (int gate : gates) {
            result.add(cells.get(gate));
        }
        return result;
    }

    public List<Road.Section> sections() {
        return Collections.unmodifiableList(sections);
    }

    public List<Block> blocks() {
        return Collections.unmodifiableList(blocks);
    }

    public List<Block> blocksOf(Road.Section section) {
        return blocks.subList(section.firstBlock(), section.endBlock());
    }

    public long created() {
        return created;
    }

    /**
     * The entry of the wall in the register of buildings, 0 until it is finished.
     */
    public long buildingId() {
        return buildingId;
    }

    public void setBuildingId(long value) {
        buildingId = value;
    }

    /**
     * Notes a block the wall put into the world, by its packed position.
     */
    public void markPlaced(long position) {
        placedBlocks.add(position);
    }

    /**
     * Whether the wall put the block at a packed position into the world itself; only such blocks are taken away for a gate.
     */
    public boolean placedHere(long position) {
        return placedBlocks.contains(position);
    }

    public void forgetPlaced(long position) {
        placedBlocks.remove(position);
    }

    /**
     * The packed positions of the blocks the wall put into the world.
     */
    public long[] placedBlocks() {
        return placedBlocks.stream().mapToLong(Long::longValue).sorted().toArray();
    }

    public void restorePlaced(long[] positions) {
        for (long position : positions) {
            placedBlocks.add(position);
        }
    }

    /**
     * Items the wall takes: one for every block of every column.
     */
    public int items() {
        int sum = 0;
        for (Block block : blocks) {
            sum += block.height();
        }
        return sum;
    }

    /**
     * Whether every column stands in the world.
     */
    public boolean built() {
        for (Road.Section section : sections) {
            if (!section.placedAll()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether every column is paid for.
     */
    public boolean allPaid() {
        for (Road.Section section : sections) {
            if (!section.done()) {
                return false;
            }
        }
        return true;
    }

    public int paid() {
        int sum = 0;
        for (Road.Section section : sections) {
            sum += section.paid();
        }
        return sum;
    }

    /**
     * Whether a place is inside the ring of the wall (its square).
     */
    public boolean inside(int x, int z) {
        int radius = radius();
        return Math.abs(x - centerX) < radius && Math.abs(z - centerZ) < radius;
    }

    public int radius() {
        int radius = 0;
        for (RouteCell cell : cells) {
            radius = Math.max(radius, Math.max(Math.abs(cell.x() - centerX), Math.abs(cell.z() - centerZ)));
        }
        return radius;
    }
}
