package io.github.sekerator.kingdomworld.common.land;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.data.AsyncSave;
import io.github.sekerator.kingdomworld.core.land.SurveyClassifier;
import io.github.sekerator.kingdomworld.core.land.SurveyRaster;
import io.github.sekerator.kingdomworld.core.land.TerrainClass;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The survey of the land of a world, stored with the overworld in {@code <world>/data/kingdomworld_survey.dat}: the cells of
 * {@value SurveyRaster#CELL} blocks over the circle of {@code radius + }{@value #MARGIN} blocks around the spawn, each with its
 * class and the height of its ground, and how far the survey has got. The cells are surveyed row by row from cell 0; the file
 * is written as the rows are done and once more at the end, and a survey interrupted by a restart goes on from the start of the
 * row it was in. A complete survey is not written again.
 *
 * <p>The root tag carries {@value #TAG_FORMAT_VERSION}. Loading never throws: data of a newer format, a damaged version or a part
 * that cannot be read keep the survey read-only (what could be read stays, changes are not written, the file is not
 * overwritten) until an operator fixes the file or accepts the loss through {@link #unlock(MinecraftServer)}.
 */
public final class SurveySavedData extends SavedData {
    public static final String DATA_NAME = KingdomWorld.MOD_ID + "_survey";
    /** Current layout of the saved data. */
    public static final int FORMAT_VERSION = 1;
    /** Blocks the survey reaches beyond the radius of the plan. */
    public static final int MARGIN = 512;

    static final String TAG_FORMAT_VERSION = "formatVersion";
    static final String TAG_SPAWN_X = "spawnX";
    static final String TAG_SPAWN_Z = "spawnZ";
    static final String TAG_RADIUS = "radius";
    static final String TAG_ORIGIN_X = "originX";
    static final String TAG_ORIGIN_Z = "originZ";
    static final String TAG_WIDTH = "width";
    static final String TAG_HEIGHT = "height";
    static final String TAG_SEA_LEVEL = "seaLevel";
    static final String TAG_CLASSES = "classes";
    static final String TAG_HEIGHTS = "heights";
    static final String TAG_NEXT = "next";
    static final String TAG_COMPLETE = "complete";

    private static final Logger LOGGER = LogUtils.getLogger();

    private int spawnX;
    private int spawnZ;
    private int radius;
    private int originX;
    private int originZ;
    private int width;
    private int height;
    private int seaLevel;
    private byte[] classes;
    private short[] heights;
    private int nextCell;
    private boolean complete;
    private CompoundTag preserved;
    private String readOnlyReason;

    /**
     * The survey of a server. Must be called on the server thread.
     */
    public static SurveySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SurveySavedData::load, () -> createAbsent(server), DATA_NAME);
    }

    private static SurveySavedData createAbsent(MinecraftServer server) {
        SurveySavedData data = new SurveySavedData();
        Path file = dataFile(server);
        if (Files.exists(file)) {
            data.lock(null, "the file " + file + " exists but the game could not read it");
        }
        return data;
    }

    /**
     * Whether a survey was begun: until then the survey has no cells.
     */
    public boolean started() {
        return classes != null;
    }

    public boolean complete() {
        return complete;
    }

    /**
     * The cells done, row by row from cell 0: the next cell to survey.
     */
    public int nextCell() {
        return nextCell;
    }

    public int cells() {
        return width * height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int originX() {
        return originX;
    }

    public int originZ() {
        return originZ;
    }

    public int spawnX() {
        return spawnX;
    }

    public int spawnZ() {
        return spawnZ;
    }

    /**
     * The radius of the plan the survey was made for, in blocks; the survey reaches {@value #MARGIN} blocks beyond it.
     */
    public int radius() {
        return radius;
    }

    /**
     * The level of the sea of the world when the survey began.
     */
    public int seaLevel() {
        return seaLevel;
    }

    /**
     * Begins a new survey of the circle of {@code radius + }{@value #MARGIN} blocks around the spawn, forgetting the one before:
     * a square of cells from a corner of a cell at least one cell beyond the circle on every side, every cell unknown.
     */
    public void start(int spawnX, int spawnZ, int radius, int seaLevel) {
        if (radius < 0) {
            throw new IllegalArgumentException("A survey of radius " + radius);
        }
        int reach = radius + MARGIN + SurveyRaster.CELL;
        int size = (int) Math.ceil(2.0 * reach / SurveyRaster.CELL) + 1;
        if ((long) size * size > 1 << 24) {
            throw new IllegalArgumentException("A survey of radius " + radius + " takes " + size + " by " + size + " cells");
        }
        this.spawnX = spawnX;
        this.spawnZ = spawnZ;
        this.radius = radius;
        this.seaLevel = seaLevel;
        this.originX = Math.floorDiv(spawnX - reach, SurveyRaster.CELL) * SurveyRaster.CELL;
        this.originZ = Math.floorDiv(spawnZ - reach, SurveyRaster.CELL) * SurveyRaster.CELL;
        this.width = size;
        this.height = size;
        this.classes = new byte[size * size];
        Arrays.fill(classes, TerrainClass.UNKNOWN.id());
        this.heights = new short[size * size];
        this.nextCell = 0;
        this.complete = false;
        setDirty();
    }

    /**
     * Sets the class and the height of the ground of a cell; written with the row it is in.
     */
    void put(int cell, TerrainClass terrain, int groundHeight) {
        if (!started() || complete) {
            return;
        }
        classes[cell] = terrain.id();
        heights[cell] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, groundHeight));
    }

    /**
     * Records how far the survey has got; the file is written whenever a row more is done.
     */
    void advance(int nextCell) {
        if (!started() || complete) {
            return;
        }
        int next = Math.max(0, Math.min(cells(), nextCell));
        boolean rowDone = next / width > this.nextCell / width;
        this.nextCell = next;
        if (rowDone) {
            setDirty();
        }
    }

    /**
     * Ends the survey: cells far higher or lower than a neighbour become steep ({@link SurveyClassifier#steepen}) and the survey
     * is complete, written once more and not again.
     */
    void finish() {
        if (!started() || complete) {
            return;
        }
        SurveyClassifier.steepen(classes, heights, width, height, SurveyClassifier.STEEP_STEP);
        nextCell = cells();
        complete = true;
        setDirty();
    }

    /**
     * The survey as the plans of the land read it, with the given marks of the cells ({@link SurveyRaster#GENERATED},
     * {@link SurveyRaster#PLAYER}), or null for none. This is how the parts of the plan made after the survey (the roads, the
     * robbers) read the land; a copy, safe to read from any thread. Cells not surveyed yet are {@link TerrainClass#UNKNOWN}.
     *
     * @throws IllegalStateException when no survey was begun
     */
    public SurveyRaster raster(byte[] marks) {
        if (!started()) {
            throw new IllegalStateException("No survey of the land was begun");
        }
        return new SurveyRaster(originX, originZ, width, height, classes, heights, marks);
    }

    public boolean isReadOnly() {
        return readOnlyReason != null;
    }

    public Optional<String> readOnlyReason() {
        return Optional.ofNullable(readOnlyReason);
    }

    @Override
    public void setDirty(boolean dirty) {
        // Read-only data is never written back.
        super.setDirty(dirty && readOnlyReason == null);
    }

    public static SurveySavedData load(CompoundTag tag) {
        SurveySavedData result = new SurveySavedData();
        try {
            result.read(tag);
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected problem while loading the survey of the land", e);
            result.forget();
            result.lock(tag, "loading failed: " + e);
        }
        return result;
    }

    private void read(CompoundTag tag) {
        if (tag.contains(TAG_FORMAT_VERSION) && !tag.contains(TAG_FORMAT_VERSION, Tag.TAG_INT)) {
            lock(tag, "the format version is damaged");
            bestEffort(tag);
            return;
        }
        int version = tag.contains(TAG_FORMAT_VERSION) ? tag.getInt(TAG_FORMAT_VERSION) : FORMAT_VERSION;
        if (version > FORMAT_VERSION) {
            lock(tag, "format " + version + " comes from a newer KingdomWorld, this version understands " + FORMAT_VERSION);
            bestEffort(tag);
            return;
        }
        if (version < 1) {
            lock(tag, "the format version " + version + " is invalid");
            bestEffort(tag);
            return;
        }
        List<String> problems = readAll(tag);
        if (!problems.isEmpty()) {
            lock(tag, problems.size() + " unreadable part(s): " + String.join("; ", problems));
            return;
        }
        super.setDirty(false);
    }

    private void bestEffort(CompoundTag tag) {
        try {
            List<String> problems = readAll(tag);
            if (!problems.isEmpty()) {
                LOGGER.warn("Survey data parts not understood: {}", String.join("; ", problems));
            }
        } catch (RuntimeException e) {
            forget();
            LOGGER.warn("Survey data could not be read even partially", e);
        }
    }

    /**
     * Reads the survey if the tag holds a whole one and describes what is wrong otherwise; a survey that cannot be read is not
     * begun.
     */
    private List<String> readAll(CompoundTag tag) {
        List<String> problems = new ArrayList<>();
        forget();
        if (!tag.contains(TAG_CLASSES)) {
            return problems;
        }
        int readWidth = tag.getInt(TAG_WIDTH);
        int readHeight = tag.getInt(TAG_HEIGHT);
        int readOriginX = tag.getInt(TAG_ORIGIN_X);
        int readOriginZ = tag.getInt(TAG_ORIGIN_Z);
        if (readWidth < 1 || readHeight < 1 || (long) readWidth * readHeight > 1 << 24) {
            problems.add("a survey of " + readWidth + " by " + readHeight + " cells");
            return problems;
        }
        if ((readOriginX & 15) != 0 || (readOriginZ & 15) != 0) {
            problems.add("the survey begins at " + readOriginX + ", " + readOriginZ + ", not at the corner of a chunk");
            return problems;
        }
        int cells = readWidth * readHeight;
        if (!tag.contains(TAG_CLASSES, Tag.TAG_BYTE_ARRAY) || tag.getByteArray(TAG_CLASSES).length != cells) {
            problems.add("'" + TAG_CLASSES + "' is not a class for each of the " + cells + " cells");
            return problems;
        }
        if (!tag.contains(TAG_HEIGHTS, Tag.TAG_INT_ARRAY) || tag.getIntArray(TAG_HEIGHTS).length != cells) {
            problems.add("'" + TAG_HEIGHTS + "' is not a height for each of the " + cells + " cells");
            return problems;
        }
        byte[] readClasses = tag.getByteArray(TAG_CLASSES).clone();
        int[] readHeights = tag.getIntArray(TAG_HEIGHTS);
        short[] packed = new short[cells];
        for (int i = 0; i < cells; i++) {
            packed[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, readHeights[i]));
        }
        boolean readComplete = tag.getBoolean(TAG_COMPLETE);
        int next = Math.max(0, Math.min(cells, tag.getInt(TAG_NEXT)));
        if (readComplete) {
            next = cells;
        } else {
            // Only whole rows count: the survey goes on from the start of the row it was in.
            next -= next % readWidth;
        }
        spawnX = tag.getInt(TAG_SPAWN_X);
        spawnZ = tag.getInt(TAG_SPAWN_Z);
        radius = Math.max(0, tag.getInt(TAG_RADIUS));
        seaLevel = tag.getInt(TAG_SEA_LEVEL);
        originX = readOriginX;
        originZ = readOriginZ;
        width = readWidth;
        height = readHeight;
        classes = readClasses;
        heights = packed;
        nextCell = next;
        complete = readComplete;
        return problems;
    }

    /**
     * Back to no survey at all.
     */
    private void forget() {
        spawnX = 0;
        spawnZ = 0;
        radius = 0;
        originX = 0;
        originZ = 0;
        width = 0;
        height = 0;
        seaLevel = 0;
        classes = null;
        heights = null;
        nextCell = 0;
        complete = false;
    }

    private void lock(CompoundTag original, String reason) {
        readOnlyReason = reason;
        preserved = original == null ? null : original.copy();
        super.setDirty(false);
        LOGGER.error("Survey data is read-only: {}. What could be read stays, changes are not written and the file will not be "
                + "overwritten. Fix or restore the file, or unlock the survey to keep only what could be read (a backup of the original "
                + "is made first).", reason);
    }

    /**
     * Leaves read-only mode after backing up the original data next to it.
     *
     * @return the backup file, or empty if the data was not read-only
     */
    public Optional<Path> unlock(MinecraftServer server) throws IOException {
        if (readOnlyReason == null) {
            return Optional.empty();
        }
        Path source = dataFile(server);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path backup = source.resolveSibling(DATA_NAME + "-readonly-backup-" + stamp + ".dat");
        if (preserved != null) {
            CompoundTag root = new CompoundTag();
            root.put("data", preserved.copy());
            NbtUtils.addCurrentDataVersion(root);
            NbtIo.writeCompressed(root, backup.toFile());
        } else if (Files.exists(source)) {
            Files.copy(source, backup);
        }
        LOGGER.warn("Survey data unlocked by an operator; the original was backed up to {} (reason it was read-only: {})", backup,
                readOnlyReason);
        readOnlyReason = null;
        preserved = null;
        setDirty(true);
        return Optional.of(backup);
    }

    static Path dataFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(DATA_NAME + ".dat");
    }

    /**
     * Saves the survey as the game does, only the file is written by {@link AsyncSave} off the server thread.
     */
    @Override
    public void save(java.io.File file) {
        AsyncSave.save(this, file, DATA_NAME);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (preserved != null) {
            for (String key : preserved.getAllKeys()) {
                Tag value = preserved.get(key);
                if (value != null) {
                    tag.put(key, value.copy());
                }
            }
            return tag;
        }
        if (readOnlyReason != null) {
            return tag;
        }
        return write(tag);
    }

    /**
     * The survey written into a tag as the file holds it. The arrays are copies: the writer has them alone while the survey goes
     * on.
     */
    public CompoundTag write(CompoundTag tag) {
        tag.putInt(TAG_FORMAT_VERSION, FORMAT_VERSION);
        if (!started()) {
            return tag;
        }
        tag.putInt(TAG_SPAWN_X, spawnX);
        tag.putInt(TAG_SPAWN_Z, spawnZ);
        tag.putInt(TAG_RADIUS, radius);
        tag.putInt(TAG_ORIGIN_X, originX);
        tag.putInt(TAG_ORIGIN_Z, originZ);
        tag.putInt(TAG_WIDTH, width);
        tag.putInt(TAG_HEIGHT, height);
        tag.putInt(TAG_SEA_LEVEL, seaLevel);
        tag.putByteArray(TAG_CLASSES, classes.clone());
        int[] wide = new int[heights.length];
        for (int i = 0; i < heights.length; i++) {
            wide[i] = heights[i];
        }
        tag.putIntArray(TAG_HEIGHTS, wide);
        tag.putInt(TAG_NEXT, nextCell);
        tag.putBoolean(TAG_COMPLETE, complete);
        return tag;
    }
}
