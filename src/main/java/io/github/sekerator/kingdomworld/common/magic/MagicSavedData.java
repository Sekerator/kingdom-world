package io.github.sekerator.kingdomworld.common.magic;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.core.magic.MagicState;
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
 * The magic of a world, stored with the overworld in {@code <world>/data/kingdomworld_magic.dat} apart from the kingdoms:
 * the energy of every kingdom with its journal, the research of the towers, the spells of the kingdoms, the blocks of magic
 * on their land and the spells players learned. The energy left in crystals travels with the crystals themselves.
 *
 * <p>The root tag carries {@value #TAG_FORMAT_VERSION}. Loading never throws: data of a newer format, a damaged version or an
 * entry that cannot be read keeps the magic read-only (what could be read is shown, changes are refused, the file is not
 * overwritten) until an operator fixes the file or accepts the loss with {@code /kingdomworld admin magic unlock}.
 */
public final class MagicSavedData extends SavedData {
    public static final String DATA_NAME = KingdomWorld.MOD_ID + "_magic";
    /** Current layout of the saved data. Increase it together with a migration step. */
    public static final int FORMAT_VERSION = 1;
    static final String TAG_FORMAT_VERSION = "formatVersion";

    private static final Logger LOGGER = LogUtils.getLogger();

    private final MagicState state = new MagicState();
    private CompoundTag preserved;
    private String readOnlyReason;

    public MagicSavedData() {
        state.setChangeListener(this::setDirty);
    }

    /**
     * The magic of a server. Must be called on the server thread.
     */
    public static MagicSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(MagicSavedData::load, () -> createAbsent(server), DATA_NAME);
    }

    private static MagicSavedData createAbsent(MinecraftServer server) {
        MagicSavedData data = new MagicSavedData();
        Path file = dataFile(server);
        if (Files.exists(file)) {
            data.lock(null, "the file " + file + " exists but the game could not read it");
        }
        return data;
    }

    public MagicState state() {
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

    public static MagicSavedData load(CompoundTag tag) {
        MagicSavedData result = new MagicSavedData();
        try {
            result.read(tag);
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected problem while loading the magic of kingdoms", e);
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
        List<String> problems = MagicNbt.read(tag, state);
        if (!problems.isEmpty()) {
            lock(tag, problems.size() + " unreadable part(s): " + String.join("; ", problems));
            return;
        }
        setDirty(false);
    }

    private void bestEffort(CompoundTag tag) {
        try {
            List<String> problems = MagicNbt.read(tag, state);
            if (!problems.isEmpty()) {
                LOGGER.warn("Magic data parts not understood: {}", String.join("; ", problems));
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Magic data could not be read even partially", e);
        }
    }

    private void lock(CompoundTag original, String reason) {
        readOnlyReason = reason;
        preserved = original == null ? null : original.copy();
        super.setDirty(false);
        LOGGER.error("Magic data is read-only: {}. Changes to energy and magic are refused and the file will not be overwritten. Fix or "
                + "restore the file, or run '/kingdomworld admin magic unlock' to keep only what could be read (a backup of the "
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
        LOGGER.warn("Magic data unlocked by an operator; the original was backed up to {} (reason it was read-only: {})", backup,
                readOnlyReason);
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
        MagicNbt.write(state, tag);
        return tag;
    }
}
