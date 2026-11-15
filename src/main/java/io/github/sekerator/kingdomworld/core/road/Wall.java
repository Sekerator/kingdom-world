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
    /** The longest ring a wall keeps, in blocks. */
    public static final int MAX_CELLS = 8_192;
    /** Blocks of a gate on each side of its middle. */
    public static final int GATE_HALF = 1;
    /** The highest column of any wall: a battlement of a stone wall. */
    public static final int TALLEST = 6;
    /** The base of a wall that stands on the ground of each of its columns, as laid out, rather than at one height. */
    public static final int FOLLOW_GROUND = Integer.MIN_VALUE;

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
    private final int radius;
    private long buildingId;
    private final java.util.Set<Long> placedBlocks = new java.util.HashSet<>();
    /** Blocks of an older wall that still stand where this one goes up, by packed position: swapped or taken down. */
    private final java.util.Set<Long> replaced = new java.util.HashSet<>();
    /** The height the lowest block of every column stands at, or {@link #FOLLOW_GROUND}. */
    private final int baseY;
    /** The natural ground of each block of the ring as it was laid out: the top block under the foot of the wall. */
    private final int[] ground;
    /** Blocks of the ring by packed x and z. */
    private final java.util.Map<Long, Integer> byColumn = new java.util.HashMap<>();
    /** Soil the wall dug that waits to fill under its columns, by item. */
    private final java.util.Map<String, Integer> spoil = new java.util.TreeMap<>();

    public Wall(long id, long cityId, UUID kingdomId, WallClass wallClass, String dimension, int centerX, int centerZ, List<RouteCell> cells,
                Collection<Integer> gates, long created) {
        this(id, cityId, kingdomId, wallClass, dimension, centerX, centerZ, cells, gates, created, FOLLOW_GROUND, null);
    }

    /**
     * @param baseY  the height of the lowest block of every column, or {@link #FOLLOW_GROUND}; the blocks of the ring stand one lower
     * @param ground the natural ground of each block of the ring, or {@code null} for the height of the block itself
     */
    public Wall(long id, long cityId, UUID kingdomId, WallClass wallClass, String dimension, int centerX, int centerZ, List<RouteCell> cells,
                Collection<Integer> gates, long created, int baseY, int[] ground) {
        this.id = id;
        this.cityId = cityId;
        this.kingdomId = Objects.requireNonNull(kingdomId, "kingdomId");
        this.wallClass = Objects.requireNonNull(wallClass, "wallClass");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.centerX = centerX;
        this.centerZ = centerZ;
        if (cells.size() < 4 || cells.size() > MAX_CELLS) {
            throw new IllegalArgumentException("A wall needs between 4 and " + MAX_CELLS + " blocks, not " + cells.size());
        }
        List<RouteCell> plain = new ArrayList<>();
        for (RouteCell cell : cells) {
            plain.add(cell.withoutOwner());
        }
        this.cells = List.copyOf(plain);
        this.created = created;
        this.baseY = baseY;
        this.ground = new int[this.cells.size()];
        for (int i = 0; i < this.cells.size(); i++) {
            this.ground[i] = ground != null && i < ground.length ? ground[i] : this.cells.get(i).y();
            byColumn.put(column(this.cells.get(i).x(), this.cells.get(i).z()), i);
        }
        int reach = 0;
        for (RouteCell cell : this.cells) {
            reach = Math.max(reach, Math.max(Math.abs(cell.x() - centerX), Math.abs(cell.z() - centerZ)));
        }
        this.radius = reach;
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
        List<Block> before = new ArrayList<>(blocks);
        blocks.clear();
        for (int i = 0; i < cells.size(); i++) {
            if (inGate(i)) {
                continue;
            }
            RouteCell cell = cells.get(i);
            // A stone wall has battlements: every other block one higher.
            int height = heightAt(i);
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
                // A gate cut into a section under way: the columns placed and paid for stay so, those the gate takes are no longer
                // counted, so that the columns after them are still built. What was paid for the columns of the gate stays in the
                // pool of the section and goes back to the storehouses once the section stands.
                int placed = kept(before, was.firstBlock(), was.placed());
                int paid = Math.max(placed, kept(before, was.firstBlock(), was.paid()));
                section.restore(Math.min(paid, section.units()), Math.min(placed, section.units()), was.condition(), false, 0,
                        was.pool());
            }
            sections.add(section);
        }
    }

    /**
     * Of the first {@code count} columns of a section as they were laid out before, those no gate stands in now.
     */
    private int kept(List<Block> before, int firstBlock, int count) {
        int kept = 0;
        for (int i = firstBlock; i < Math.min(before.size(), firstBlock + count); i++) {
            if (!inGate(before.get(i).cell())) {
                kept++;
            }
        }
        return kept;
    }

    private static long column(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFF_FFFFL);
    }

    /**
     * A position packed as the game packs block positions.
     */
    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (long) y & 0xFFFL;
    }

    public static int packedX(long packed) {
        return (int) (packed >> 38);
    }

    public static int packedY(long packed) {
        return (int) (packed << 52 >> 52);
    }

    public static int packedZ(long packed) {
        return (int) (packed << 26 >> 38);
    }

    /**
     * The block of the ring at a place, or -1 if the ring does not pass there.
     */
    public int cellAt(int x, int z) {
        Integer cell = byColumn.get(column(x, z));
        return cell == null ? -1 : cell;
    }

    /**
     * The height of the block of the ring at a place, the foot its column stands on; empty off the ring.
     */
    public java.util.OptionalInt groundAt(int x, int z) {
        int cell = cellAt(x, z);
        return cell < 0 ? java.util.OptionalInt.empty() : java.util.OptionalInt.of(cells.get(cell).y());
    }

    /**
     * The height of the column of a block of the ring: a stone wall has battlements, every other block one higher.
     */
    public int heightAt(int cell) {
        return wallClass.height() + (wallClass == WallClass.STONE && cell % 2 == 0 ? 1 : 0);
    }

    /**
     * Whether a position is a block of a column of the wall: on a block of the ring outside the gates, from one above its foot to
     * the top of the column.
     */
    public boolean inColumn(long position) {
        int cell = cellAt(packedX(position), packedZ(position));
        if (cell < 0 || inGate(cell)) {
            return false;
        }
        int y = packedY(position);
        int foot = cells.get(cell).y();
        return y > foot && y <= foot + heightAt(cell);
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

    /**
     * How far a place is from the ring, in blocks along x or z, whichever is more: to its nearest block.
     */
    public int reach(int x, int z) {
        RouteCell cell = cells.get(nearestCell(x, z));
        return Math.max(Math.abs(cell.x() - x), Math.abs(cell.z() - z));
    }

    /**
     * The wall a gate is cut into for a player standing at a place: the wall of the chosen city if it is in the dimension of
     * the player and no further than {@code reach}, else the nearest wall of the kingdom in that dimension, however far.
     *
     * @param cityId the city chosen in the window, 0 for the capital
     * @return empty when the kingdom has no wall in that dimension
     */
    public static Optional<Wall> forGate(Collection<Wall> walls, UUID kingdomId, long cityId, String dimension, int x, int z, int reach) {
        Wall nearest = null;
        int nearestReach = Integer.MAX_VALUE;
        for (Wall wall : walls) {
            if (!wall.kingdomId().equals(kingdomId) || !wall.dimension().equals(dimension) || wall.cells().isEmpty()) {
                continue;
            }
            int distance = wall.reach(x, z);
            if (wall.cityId() == cityId && distance <= reach) {
                return Optional.of(wall);
            }
            if (distance < nearestReach || distance == nearestReach && nearest != null && wall.id() < nearest.id()) {
                nearest = wall;
                nearestReach = distance;
            }
        }
        return Optional.ofNullable(nearest);
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
     * Takes over the blocks of an older wall that still stand: those it placed, those it took over itself and some more (the
     * leaves of its gates). Each is swapped for a block of this wall when its column goes up, or taken down.
     */
    public void inherit(Wall old, Collection<Long> extra) {
        replaced.addAll(old.placedBlocks);
        replaced.addAll(old.replaced);
        replaced.addAll(extra);
        replaced.removeAll(placedBlocks);
    }

    /**
     * Whether the block at a packed position belongs to an older wall and waits to be swapped or taken down.
     */
    public boolean replaces(long position) {
        return replaced.contains(position);
    }

    public void forgetReplaced(long position) {
        replaced.remove(position);
    }

    public boolean hasReplaced() {
        return !replaced.isEmpty();
    }

    /**
     * The packed positions of the blocks of an older wall still to swap or take down.
     */
    public long[] replacedBlocks() {
        return replaced.stream().mapToLong(Long::longValue).sorted().toArray();
    }

    public void restoreReplaced(long[] positions) {
        for (long position : positions) {
            replaced.add(position);
        }
    }

    /**
     * The height the lowest block of every column stands at, or {@link #FOLLOW_GROUND} for a wall on the ground of each column.
     */
    public int baseY() {
        return baseY;
    }

    public boolean hasBase() {
        return baseY != FOLLOW_GROUND;
    }

    /**
     * The natural ground of a block of the ring as it was laid out.
     */
    public int ground(int cell) {
        return ground[cell];
    }

    public int[] grounds() {
        return ground.clone();
    }

    /**
     * Blocks of earth to fill under a column, from its natural ground up to its foot, as laid out.
     */
    public int fill(Block block) {
        return Math.max(0, block.y() - ground[block.cell()]);
    }

    /**
     * Blocks of natural ground to cut over the foot of a column, as laid out.
     */
    public int cut(Block block) {
        return Math.max(0, ground[block.cell()] - block.y());
    }

    /**
     * Blocks of earth to fill under all the columns, as laid out.
     */
    public int earth() {
        int sum = 0;
        for (Block block : blocks) {
            sum += fill(block);
        }
        return sum;
    }

    /**
     * Blocks of natural ground to cut over the feet of all the columns, as laid out.
     */
    public int cutTotal() {
        int sum = 0;
        for (Block block : blocks) {
            sum += cut(block);
        }
        return sum;
    }

    /**
     * Soil the wall dug and keeps to fill under its columns, by item.
     */
    public java.util.Map<String, Integer> spoil() {
        return java.util.Collections.unmodifiableMap(spoil);
    }

    public void addSpoil(String item, int count) {
        if (count > 0) {
            spoil.merge(item, count, Integer::sum);
        }
    }

    /**
     * Takes one of an item from the spoil.
     *
     * @return whether there was one
     */
    public boolean useSpoil(String item) {
        Integer count = spoil.get(item);
        if (count == null || count <= 0) {
            return false;
        }
        if (count == 1) {
            spoil.remove(item);
        } else {
            spoil.put(item, count - 1);
        }
        return true;
    }

    /**
     * Empties the spoil, for the storehouses.
     */
    public java.util.Map<String, Integer> takeSpoil() {
        java.util.Map<String, Integer> all = new java.util.TreeMap<>(spoil);
        spoil.clear();
        return all;
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

    /**
     * The radius of the square of the wall: how far its furthest block lies from its middle.
     */
    public int radius() {
        return radius;
    }
}
