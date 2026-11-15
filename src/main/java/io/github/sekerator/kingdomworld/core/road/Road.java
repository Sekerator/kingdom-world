package io.github.sekerator.kingdomworld.core.road;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A road: its way as a chain of sections of {@value #SECTION_CELLS} blocks, its class, and how far each section is built
 * and how worn it is. A section is built by the kingdom that builds it (the road's own kingdom, or on the land of another
 * kingdom in a joint road that one), from its storehouses: every block is paid for once, its items wait in the section
 * until the block is placed in the world, which happens when its place is loaded. Far from players a section counts as
 * built once it is paid for.
 *
 * <p>Not thread safe; the server uses it from the server thread only.
 */
public final class Road {
    /** Blocks of the middle of the road in one section. */
    public static final int SECTION_CELLS = 16;
    /** The longest way a road keeps, in blocks. */
    public static final int MAX_CELLS = 32_768;

    public enum Status {
        /** Waits for other kingdoms to let it cross their land. */
        WAITING_CONSENT,
        BUILDING,
        DONE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Who builds the parts of the road on the land of other kingdoms.
     */
    public enum Mode {
        /** The road crosses no other kingdom. */
        OWN,
        /** Every kingdom builds the part on its land; the planner builds the part on wild land. */
        JOINT,
        /** The planner builds and pays for all of it. */
        PAYER;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * A section of the road.
     */
    public static final class Section {
        private final int index;
        private final int firstCell;
        private final int endCell;
        private final int firstBlock;
        private final int endBlock;
        private UUID builder;
        private int paid;
        private int placed;
        private double condition = 1.0;
        private boolean repairing;
        private int repairPaid;
        private final Map<String, Integer> pool = new LinkedHashMap<>();
        /** Seconds of builders and of miners already given to the next block of a levelled road. */
        private double builderWork;
        private double minerWork;

        Section(int index, int firstCell, int endCell, int firstBlock, int endBlock, UUID builder) {
            this.index = index;
            this.firstCell = firstCell;
            this.endCell = endCell;
            this.firstBlock = firstBlock;
            this.endBlock = endBlock;
            this.builder = Objects.requireNonNull(builder, "builder");
        }

        public int index() {
            return index;
        }

        public int firstCell() {
            return firstCell;
        }

        public int endCell() {
            return endCell;
        }

        public int firstBlock() {
            return firstBlock;
        }

        public int endBlock() {
            return endBlock;
        }

        /**
         * Blocks of road in the section.
         */
        public int units() {
            return endBlock - firstBlock;
        }

        public UUID builder() {
            return builder;
        }

        public void setBuilder(UUID value) {
            builder = Objects.requireNonNull(value, "builder");
        }

        /**
         * Blocks paid for.
         */
        public int paid() {
            return paid;
        }

        /**
         * Blocks placed in the world (or found unneeded there).
         */
        public int placed() {
            return placed;
        }

        public boolean done() {
            return paid >= units();
        }

        public boolean placedAll() {
            return placed >= units();
        }

        public double condition() {
            return condition;
        }

        public boolean repairing() {
            return repairing;
        }

        public int repairPaid() {
            return repairPaid;
        }

        /**
         * Items paid for and not placed yet, by item id.
         */
        public Map<String, Integer> pool() {
            return Collections.unmodifiableMap(pool);
        }

        public void markPaid(Map<String, Integer> items) {
            if (paid >= units()) {
                throw new IllegalStateException("Section already paid for");
            }
            items.forEach((item, count) -> pool.merge(item, count, Integer::sum));
            paid++;
            builderWork = 0;
            minerWork = 0;
        }

        /**
         * Seconds of builders given to the next block of a levelled road.
         */
        public double builderWork() {
            return builderWork;
        }

        /**
         * Seconds of miners given to the next block of a levelled road.
         */
        public double minerWork() {
            return minerWork;
        }

        /**
         * Gives seconds of work to the next block.
         */
        public void addWork(double builders, double miners) {
            builderWork += Math.max(0, builders);
            minerWork += Math.max(0, miners);
        }

        /**
         * Puts back the work given to the next block, read from saved data.
         */
        public void restoreWork(double builders, double miners) {
            builderWork = Double.isFinite(builders) ? Math.max(0, builders) : 0;
            minerWork = Double.isFinite(miners) ? Math.max(0, miners) : 0;
        }

        /**
         * Takes an item of the pool for a block being placed.
         */
        public boolean use(String item) {
            Integer count = pool.get(item);
            if (count == null || count <= 0) {
                return false;
            }
            if (count == 1) {
                pool.remove(item);
            } else {
                pool.put(item, count - 1);
            }
            return true;
        }

        public void markPlaced() {
            if (placed < paid) {
                placed++;
            }
        }

        /**
         * Takes everything left in the pool, to give back.
         */
        public Map<String, Integer> takePool() {
            Map<String, Integer> all = new LinkedHashMap<>(pool);
            pool.clear();
            return all;
        }

        public void wear(double amount) {
            if (done()) {
                condition = Math.max(0.0, condition - Math.max(0.0, amount));
            }
        }

        public void setCondition(double value) {
            condition = Math.max(0.0, Math.min(1.0, value));
        }

        public void startRepair() {
            repairing = true;
            repairPaid = 0;
        }

        /**
         * A block of the repair was paid for; the repair ends when all of them are.
         *
         * @return whether the repair is over
         */
        public boolean repairStep(int needed) {
            repairPaid++;
            if (repairPaid >= needed) {
                repairing = false;
                repairPaid = 0;
                condition = 1.0;
                return true;
            }
            return false;
        }

        public void cancelRepair() {
            repairing = false;
            repairPaid = 0;
        }

        /**
         * Puts back the state of a section read from saved data.
         */
        public void restore(int paidValue, int placedValue, double conditionValue, boolean repairingValue, int repairPaidValue,
                            Map<String, Integer> poolValue) {
            paid = Math.max(0, Math.min(units(), paidValue));
            placed = Math.max(0, Math.min(paid, placedValue));
            condition = Math.max(0.0, Math.min(1.0, conditionValue));
            repairing = repairingValue;
            repairPaid = Math.max(0, repairPaidValue);
            pool.clear();
            poolValue.forEach((item, count) -> {
                if (count > 0) {
                    pool.put(item, count);
                }
            });
        }
    }

    private final long id;
    private UUID kingdomId;
    private final RoadClass roadClass;
    private final String dimension;
    private Endpoint from;
    private Endpoint to;
    private final List<RouteCell> cells;
    private final Mode mode;
    private final long created;
    private final List<Section> sections = new ArrayList<>();
    private final List<RoadLayout.Block> blocks;
    private final int layout;
    /** Items dug out of the road and counted for its embankments and lining before they are dug, by item id. */
    private final Map<String, Integer> credit = new LinkedHashMap<>();
    /** Items counted that way and not dug out yet, by item id. */
    private final Map<String, Integer> owed = new LinkedHashMap<>();
    /** Seconds of the earthworks of a levelled road in all, and of those paid for. */
    private double earthTotal;
    private double earthDone;
    private Status status;
    private boolean paused;
    private int traffic;

    /**
     * @param builders who builds each section, in order; a missing entry means the road's own kingdom
     */
    public Road(long id, UUID kingdomId, RoadClass roadClass, String dimension, Endpoint from, Endpoint to, List<RouteCell> cells, Mode mode,
                long created, Status status, List<UUID> builders) {
        this(id, kingdomId, roadClass, dimension, from, to, cells, mode, created, status, builders, RoadLayout.LAID, null);
    }

    /**
     * @param builders who builds each section, in order; a missing entry means the road's own kingdom
     * @param layout   {@link RoadLayout#LAID} or {@link RoadLayout#LEVELLED}
     * @param surveys  the earthworks of every block of a levelled road, in the order of its blocks; empty ones when they do not
     *                 fit its blocks
     */
    public Road(long id, UUID kingdomId, RoadClass roadClass, String dimension, Endpoint from, Endpoint to, List<RouteCell> cells, Mode mode,
                long created, Status status, List<UUID> builders, int layout, List<RoadLayout.ColumnSurvey> surveys) {
        this.id = id;
        this.kingdomId = Objects.requireNonNull(kingdomId, "kingdomId");
        this.roadClass = Objects.requireNonNull(roadClass, "roadClass");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.from = Objects.requireNonNull(from, "from");
        this.to = Objects.requireNonNull(to, "to");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.status = Objects.requireNonNull(status, "status");
        this.created = created;
        if (cells.size() < 2 || cells.size() > MAX_CELLS) {
            throw new IllegalArgumentException("A road needs between 2 and " + MAX_CELLS + " blocks, not " + cells.size());
        }
        List<RouteCell> plain = new ArrayList<>(cells.size());
        for (RouteCell cell : cells) {
            plain.add(cell.withoutOwner());
        }
        this.cells = List.copyOf(plain);
        this.layout = layout == RoadLayout.LEVELLED ? RoadLayout.LEVELLED : RoadLayout.LAID;
        this.blocks = List.copyOf(RoadLayout.blocks(this.cells, roadClass, this.layout, surveys));
        int count = sectionCount(this.cells.size());
        int block = 0;
        for (int i = 0; i < count; i++) {
            int first = i * SECTION_CELLS;
            int end = Math.min(this.cells.size(), first + SECTION_CELLS);
            int firstBlock = block;
            while (block < blocks.size() && blocks.get(block).cell() < end) {
                block++;
            }
            UUID builder = builders != null && i < builders.size() && builders.get(i) != null ? builders.get(i) : kingdomId;
            sections.add(new Section(i, first, end, firstBlock, block, builder));
        }
    }

    public static int sectionCount(int cells) {
        return (cells + SECTION_CELLS - 1) / SECTION_CELLS;
    }

    public long id() {
        return id;
    }

    public UUID kingdomId() {
        return kingdomId;
    }

    /**
     * The road goes to another kingdom with the land it lies on: its sections the old one built become the new one's, and its
     * ends are those given.
     */
    void handOver(UUID from, UUID to, Endpoint start, Endpoint end) {
        if (kingdomId.equals(from)) {
            kingdomId = Objects.requireNonNull(to, "to");
        }
        for (Section section : sections) {
            if (section.builder().equals(from)) {
                section.setBuilder(to);
            }
        }
        this.from = Objects.requireNonNull(start, "start");
        this.to = Objects.requireNonNull(end, "end");
    }

    public RoadClass roadClass() {
        return roadClass;
    }

    public String dimension() {
        return dimension;
    }

    public Endpoint from() {
        return from;
    }

    public Endpoint to() {
        return to;
    }

    public List<RouteCell> cells() {
        return cells;
    }

    public List<RoadLayout.Block> blocks() {
        return blocks;
    }

    /**
     * {@link RoadLayout#LAID} for a road laid on the land as it is, {@link RoadLayout#LEVELLED} for a levelled one.
     */
    public int layout() {
        return layout;
    }

    public boolean levelled() {
        return layout == RoadLayout.LEVELLED;
    }

    /**
     * Seconds of the earthworks of a levelled road in all.
     */
    public double earthTotal() {
        return earthTotal;
    }

    /**
     * Seconds of its earthworks paid for.
     */
    public double earthDone() {
        return earthDone;
    }

    public void setEarthTotal(double value) {
        earthTotal = Double.isFinite(value) ? Math.max(0, value) : 0;
    }

    public void addEarthDone(double value) {
        if (Double.isFinite(value) && value > 0) {
            earthDone = Math.min(earthTotal, earthDone + value);
        }
    }

    /**
     * Puts back the earthworks read from saved data.
     */
    public void restoreEarth(double total, double done) {
        setEarthTotal(total);
        earthDone = Double.isFinite(done) ? Math.max(0, Math.min(earthTotal, done)) : 0;
    }

    /**
     * The share of the earthworks paid for, 1 for a road without any.
     */
    public double levelledShare() {
        return earthTotal <= 0 ? 1.0 : Math.min(1.0, earthDone / earthTotal);
    }

    /**
     * Items counted for the embankments of the road from what it digs, by item id.
     */
    public Map<String, Integer> credit() {
        return Collections.unmodifiableMap(credit);
    }

    /**
     * Items counted that way and not dug out yet, by item id.
     */
    public Map<String, Integer> owed() {
        return Collections.unmodifiableMap(owed);
    }

    /**
     * Counts items a column paid for will dig out: they may go into the embankments before they are dug.
     */
    public void addCredit(String item, int count) {
        if (count > 0) {
            credit.merge(item, count, Integer::sum);
            owed.merge(item, count, Integer::sum);
        }
    }

    /**
     * Takes up to so many items of the credit.
     *
     * @return how many were taken
     */
    public int useCredit(String item, int count) {
        return take(credit, item, count);
    }

    /**
     * Something dug out of the road: what of it was owed is settled.
     *
     * @return how many were owed and are now settled
     */
    public int settleOwed(String item, int count) {
        return take(owed, item, count);
    }

    /**
     * Takes the credit out, to settle it when the road is done or given up.
     */
    public Map<String, Integer> takeCredit() {
        Map<String, Integer> all = new LinkedHashMap<>(credit);
        credit.clear();
        return all;
    }

    /**
     * Takes the debt out, to settle it when the road is done or given up.
     */
    public Map<String, Integer> takeOwed() {
        Map<String, Integer> all = new LinkedHashMap<>(owed);
        owed.clear();
        return all;
    }

    /**
     * Puts back the credit and the debt read from saved data.
     */
    public void restoreCredit(Map<String, Integer> creditValue, Map<String, Integer> owedValue) {
        credit.clear();
        owed.clear();
        creditValue.forEach((item, count) -> {
            if (count != null && count > 0) {
                credit.put(item, count);
            }
        });
        owedValue.forEach((item, count) -> {
            if (count != null && count > 0) {
                owed.put(item, count);
            }
        });
    }

    private static int take(Map<String, Integer> map, String item, int count) {
        Integer have = map.get(item);
        if (have == null || count <= 0) {
            return 0;
        }
        int taken = Math.min(have, count);
        if (taken >= have) {
            map.remove(item);
        } else {
            map.put(item, have - taken);
        }
        return taken;
    }

    public Mode mode() {
        return mode;
    }

    public long created() {
        return created;
    }

    public List<Section> sections() {
        return Collections.unmodifiableList(sections);
    }

    public Section section(int index) {
        return sections.get(index);
    }

    public Status status() {
        return status;
    }

    public void setStatus(Status value) {
        status = Objects.requireNonNull(value, "status");
    }

    public boolean paused() {
        return paused;
    }

    public void setPaused(boolean value) {
        paused = value;
    }

    /**
     * Caravans that went along the road.
     */
    public int traffic() {
        return traffic;
    }

    public void addTraffic(int count) {
        traffic = (int) Math.min(Integer.MAX_VALUE, (long) traffic + Math.max(0, count));
    }

    public void restoreTraffic(int value) {
        traffic = Math.max(0, value);
    }

    /**
     * Length in blocks along the middle.
     */
    public int length() {
        return cells.size();
    }

    public int units() {
        return blocks.size();
    }

    public int paid() {
        int sum = 0;
        for (Section section : sections) {
            sum += section.paid();
        }
        return sum;
    }

    public boolean built() {
        for (Section section : sections) {
            if (!section.done()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The mean condition of the built sections, 1 for a new road.
     */
    public double condition() {
        double sum = 0;
        int count = 0;
        for (Section section : sections) {
            if (section.done()) {
                sum += section.condition();
                count++;
            }
        }
        return count == 0 ? 1.0 : sum / count;
    }

    /**
     * The blocks of a section.
     */
    public List<RoadLayout.Block> blocksOf(Section section) {
        return blocks.subList(section.firstBlock(), section.endBlock());
    }

    /**
     * The middle block of the road where a section starts, and where the last one ends.
     */
    public RouteCell boundary(int index) {
        if (index >= sections.size()) {
            return cells.get(cells.size() - 1);
        }
        return cells.get(sections.get(index).firstCell());
    }

    /**
     * Blocks along the middle of a section, counting from its first block to the first block of the next one.
     */
    public double sectionLength(Section section) {
        int end = Math.min(cells.size() - 1, section.endCell());
        double length = 0;
        for (int i = section.firstCell(); i < end; i++) {
            RouteCell a = cells.get(i);
            RouteCell b = cells.get(i + 1);
            length += Math.sqrt(Math.pow(b.x() - a.x(), 2) + Math.pow(b.z() - a.z(), 2));
        }
        return Math.max(1.0, length);
    }

    /**
     * Kingdoms that build some of the road.
     */
    public List<UUID> builders() {
        List<UUID> result = new ArrayList<>();
        for (Section section : sections) {
            if (!result.contains(section.builder())) {
                result.add(section.builder());
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return "Road#" + id + "(" + roadClass.id() + ", " + cells.size() + " blocks, " + status.id() + ")";
    }
}
