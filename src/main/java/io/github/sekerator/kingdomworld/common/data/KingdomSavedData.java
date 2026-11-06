package io.github.sekerator.kingdomworld.common.data;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.kingdom.TerritorySync;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomRegistry;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
import java.util.List;
import java.util.Optional;

/**
 * World-wide kingdom state, stored with the overworld in {@code <world>/data/kingdomworld_kingdoms.dat}.
 *
 * <p>The root tag carries {@value #TAG_FORMAT_VERSION}; older formats are upgraded by
 * {@link KingdomDataMigrations} on load and written back in the current format on the next save.
 *
 * <p>Loading never throws. Data that cannot be fully understood (a newer format, a damaged format version, a
 * failed migration, an unreadable entry or an unreadable file) switches the data to read-only mode: whatever
 * could be read stays available, so protection keeps working, but changes are refused with an ERROR in the
 * log, the data is never marked for saving, and the original tag is what {@link #save(CompoundTag)} writes. The
 * file on disk therefore stays exactly as it was until an operator fixes it or explicitly accepts the loss with
 * {@code /kingdomworld admin data unlock}.
 */
public final class KingdomSavedData extends SavedData {
    public static final String DATA_NAME = KingdomWorld.MOD_ID + "_kingdoms";
    /** Current layout of the saved data. Increase it together with a migration step. */
    public static final int FORMAT_VERSION = 2;
    /** Format assumed for data without a version field. */
    static final int FIRST_FORMAT_VERSION = 1;

    static final String TAG_FORMAT_VERSION = "formatVersion";
    static final String TAG_KINGDOMS = "kingdoms";

    private static final Logger LOGGER = LogUtils.getLogger();

    private final KingdomWorldState state = new KingdomWorldState();
    /** The tag as it was loaded, kept while read-only; {@code null} if the file itself was unreadable. */
    private CompoundTag preserved;
    /** Why the data is read-only, or {@code null} if it may be changed. */
    private String readOnlyReason;
    /** Whether map updates of this instance are sent to players; true only for the live data of a server. */
    private boolean synced;

    public KingdomSavedData() {
        state.setChangeListener(this::setDirty);
    }

    /**
     * The kingdom data of the given server. Must be called on the server thread.
     */
    public static KingdomSavedData get(MinecraftServer server) {
        KingdomSavedData data = server.overworld().getDataStorage()
                .computeIfAbsent(KingdomSavedData::load, () -> createAbsent(server), DATA_NAME);
        if (!data.synced) {
            data.synced = true;
            data.state.territory().addListener(TerritorySync.LISTENER);
        }
        return data;
    }

    /**
     * Called by the game when no data could be loaded: either there is no file yet or reading it failed.
     */
    private static KingdomSavedData createAbsent(MinecraftServer server) {
        KingdomSavedData data = new KingdomSavedData();
        Path file = dataFile(server);
        if (Files.exists(file)) {
            data.lock(null, "the file " + file + " exists but the game could not read it");
        }
        return data;
    }

    public KingdomWorldState state() {
        return state;
    }

    public KingdomRegistry registry() {
        return state.kingdoms();
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

    public static KingdomSavedData load(CompoundTag tag) {
        KingdomSavedData result = new KingdomSavedData();
        try {
            result.read(tag);
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected problem while loading kingdom data", e);
            result.lock(tag, "loading failed: " + e);
        }
        return result;
    }

    private void read(CompoundTag tag) {
        if (tag.contains(TAG_FORMAT_VERSION) && !tag.contains(TAG_FORMAT_VERSION, Tag.TAG_INT)) {
            lock(tag, "the format version is damaged (tag type " + tag.getTagType(TAG_FORMAT_VERSION) + ")");
            readBestEffort(tag);
            return;
        }
        int version = tag.contains(TAG_FORMAT_VERSION) ? tag.getInt(TAG_FORMAT_VERSION) : FIRST_FORMAT_VERSION;
        if (version > FORMAT_VERSION) {
            lock(tag, "format " + version + " comes from a newer KingdomWorld, this version understands " + FORMAT_VERSION);
            readBestEffort(tag);
            return;
        }
        if (version < FIRST_FORMAT_VERSION) {
            lock(tag, "the format version " + version + " is invalid");
            readBestEffort(tag);
            return;
        }
        CompoundTag data = tag;
        if (version < FORMAT_VERSION) {
            LOGGER.info("Upgrading kingdom data from format {} to {}", version, FORMAT_VERSION);
            try {
                data = KingdomDataMigrations.upgrade(tag.copy(), version);
            } catch (RuntimeException e) {
                lock(tag, "the upgrade from format " + version + " failed: " + e.getMessage());
                return;
            }
        }
        List<String> problems = readKingdoms(data);
        if (!problems.isEmpty()) {
            lock(tag, problems.size() + " unreadable part(s): " + String.join("; ", problems));
            return;
        }
        // Freshly loaded data only needs saving if it was converted from another format.
        setDirty(version != FORMAT_VERSION);
    }

    /**
     * Reads every kingdom that can be read and describes the rest.
     */
    private List<String> readKingdoms(CompoundTag data) {
        List<String> problems = new ArrayList<>();
        if (!data.contains(TAG_KINGDOMS)) {
            return problems;
        }
        if (!data.contains(TAG_KINGDOMS, Tag.TAG_LIST)) {
            problems.add("'" + TAG_KINGDOMS + "' is not a list");
            return problems;
        }
        ListTag kingdoms = (ListTag) data.get(TAG_KINGDOMS);
        if (!kingdoms.isEmpty() && kingdoms.getElementType() != Tag.TAG_COMPOUND) {
            problems.add("'" + TAG_KINGDOMS + "' does not hold compounds");
            return problems;
        }
        for (int i = 0; i < kingdoms.size(); i++) {
            try {
                KingdomNbt.restore(state, kingdoms.getCompound(i));
            } catch (RuntimeException e) {
                problems.add("kingdom #" + i + ": " + e.getMessage());
            }
        }
        return problems;
    }

    private void readBestEffort(CompoundTag tag) {
        try {
            List<String> problems = readKingdoms(tag);
            if (!problems.isEmpty()) {
                LOGGER.warn("Kingdom data parts not understood: {}", String.join("; ", problems));
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Kingdom data could not be read even partially", e);
        }
    }

    private void lock(CompoundTag original, String reason) {
        readOnlyReason = reason;
        preserved = original == null ? null : original.copy();
        super.setDirty(false);
        LOGGER.error("Kingdom data is read-only: {}. Changes to kingdoms are refused and the file will not be "
                + "overwritten. Fix or restore the file, or run '/kingdomworld admin data unlock' to keep only what "
                + "could be read (a backup of the original is made first).", reason);
    }

    /**
     * Leaves read-only mode after backing up the original data next to it. What could be read becomes the
     * new data and is written on the next save; everything else is dropped.
     *
     * @return the backup file, or empty if the data was not read-only
     * @throws IOException if the backup cannot be written; the data then stays read-only
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
        LOGGER.warn("Kingdom data unlocked by an operator; the original was backed up to {} (reason it was read-only: {})",
                backup, readOnlyReason);
        readOnlyReason = null;
        preserved = null;
        setDirty(true);
        return Optional.of(backup);
    }

    static Path dataFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(DATA_NAME + ".dat");
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
        tag.putInt(TAG_FORMAT_VERSION, FORMAT_VERSION);
        ListTag kingdoms = new ListTag();
        for (Kingdom kingdom : state.kingdoms().all()) {
            kingdoms.add(KingdomNbt.write(state, kingdom));
        }
        tag.put(TAG_KINGDOMS, kingdoms);
        return tag;
    }
}
