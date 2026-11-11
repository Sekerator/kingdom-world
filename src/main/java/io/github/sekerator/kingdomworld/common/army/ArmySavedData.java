package io.github.sekerator.kingdomworld.common.army;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.core.army.ArmyState;
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
import java.util.List;
import java.util.Optional;

/**
 * The armies of a world, stored with the overworld in {@code <world>/data/kingdomworld_army.dat} apart from the kingdoms:
 * soldiers, squads and their orders, mercenaries on offer and hired, barracks.
 *
 * <p>The root tag carries {@value #TAG_FORMAT_VERSION}. Loading never throws: data of a newer format, a damaged version or an
 * entry that cannot be read keeps the army read-only (what could be read is shown, changes are refused, the file is not
 * overwritten) until an operator fixes the file or accepts the loss with {@code /kingdomworld admin army unlock}.
 */
public final class ArmySavedData extends SavedData {
    public static final String DATA_NAME = KingdomWorld.MOD_ID + "_army";
    /** Current layout of the saved data. Increase it together with a migration step. */
    public static final int FORMAT_VERSION = 3;
    /** The format that first knows the branches and posts of soldiers. */
    static final int BRANCHES_VERSION = 3;
    static final String TAG_FORMAT_VERSION = "formatVersion";

    private static final Logger LOGGER = LogUtils.getLogger();

    private final ArmyState state = new ArmyState();
    private CompoundTag preserved;
    private String readOnlyReason;

    public ArmySavedData() {
        state.setChangeListener(this::setDirty);
    }

    /**
     * The armies of a server. Must be called on the server thread.
     */
    public static ArmySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ArmySavedData::load, () -> createAbsent(server), DATA_NAME);
    }

    private static ArmySavedData createAbsent(MinecraftServer server) {
        ArmySavedData data = new ArmySavedData();
        Path file = dataFile(server);
        if (Files.exists(file)) {
            data.lock(null, "the file " + file + " exists but the game could not read it");
        }
        return data;
    }

    public ArmyState state() {
        return state;
    }

    public boolean isReadOnly() {
        return readOnlyReason != null;
    }

    public Optional<String> readOnlyReason() {
        return Optional.ofNullable(readOnlyReason);
    }

    @Override
    public void setDirty(boolean dirty) {
        super.setDirty(dirty && readOnlyReason == null);
    }

    public static ArmySavedData load(CompoundTag tag) {
        ArmySavedData result = new ArmySavedData();
        try {
            result.read(tag);
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected problem while loading the army", e);
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
        List<String> problems = ArmyNbt.read(tag, state);
        problems.addAll(state.verify());
        if (!problems.isEmpty()) {
            lock(tag, problems.size() + " unreadable part(s): " + String.join("; ", problems));
            return;
        }
        setDirty(false);
        if (version < BRANCHES_VERSION) {
            // Data older than the guard and the army: soldiers whose squad holds a point, guards an area or walks a patrol
            // are the guard, the others the army, and all of them are fighters.
            int guards = state.assignBranchesByOrders();
            LOGGER.info("Army data of format {} brought to format {}: {} soldier(s) serve in the guard, {} in the army", version,
                    FORMAT_VERSION, guards, state.soldiers().size() - guards);
            setDirty(true);
        }
    }

    private void bestEffort(CompoundTag tag) {
        try {
            List<String> problems = ArmyNbt.read(tag, state);
            if (!problems.isEmpty()) {
                LOGGER.warn("Army data parts not understood: {}", String.join("; ", problems));
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Army data could not be read even partially", e);
        }
    }

    private void lock(CompoundTag original, String reason) {
        readOnlyReason = reason;
        preserved = original == null ? null : original.copy();
        super.setDirty(false);
        LOGGER.error("Army data is read-only: {}. Changes to the army are refused and the file will not be overwritten. Fix or "
                + "restore the file, or run '/kingdomworld admin army unlock' to keep only what could be read (a backup of the "
                + "original is made first).", reason);
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
        LOGGER.warn("Army data unlocked by an operator; the original was backed up to {} (reason it was read-only: {})", backup,
                readOnlyReason);
        readOnlyReason = null;
        preserved = null;
        // Squads of soldiers that could not be read are dropped; the rest stays.
        for (String problem : state.verify()) {
            LOGGER.warn("Army data after unlocking: {}", problem);
        }
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
        ArmyNbt.write(state, tag);
        return tag;
    }
}
