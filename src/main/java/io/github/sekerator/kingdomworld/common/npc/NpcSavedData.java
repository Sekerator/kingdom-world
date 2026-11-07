package io.github.sekerator.kingdomworld.common.npc;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
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
 * The plan of the kingdoms of the world, the progress of their appearance and their records (the mark of a kingdom of the world
 * and the character of its ruler), stored with the overworld in {@code <world>/data/kingdomworld_npc.dat} apart from the
 * kingdoms.
 *
 * <p>The root tag carries {@value #TAG_FORMAT_VERSION}. Loading never throws: data of a newer format, a damaged version or an
 * entry that cannot be read keeps the kingdoms of the world read-only (what could be read is shown, changes are refused, the file is not
 * overwritten) until an operator fixes the file or accepts the loss with {@code /kingdomworld admin npc unlock}.
 */
public final class NpcSavedData extends SavedData {
    public static final String DATA_NAME = KingdomWorld.MOD_ID + "_npc";
    /**
     * Current layout of the saved data. Increase it together with a migration step. Format 2 added the memories of the rulers;
     * data of format 1 has none and is read as it is, every ruler starting with an empty memory.
     */
    public static final int FORMAT_VERSION = 2;
    static final String TAG_FORMAT_VERSION = "formatVersion";

    private static final Logger LOGGER = LogUtils.getLogger();

    private final NpcState state = new NpcState();
    private CompoundTag preserved;
    private String readOnlyReason;

    public NpcSavedData() {
        state.setChangeListener(this::setDirty);
    }

    /**
     * The kingdoms of the world of a server. Must be called on the server thread.
     */
    public static NpcSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(NpcSavedData::load, () -> createAbsent(server), DATA_NAME);
    }

    private static NpcSavedData createAbsent(MinecraftServer server) {
        NpcSavedData data = new NpcSavedData();
        Path file = dataFile(server);
        if (Files.exists(file)) {
            data.lock(null, "the file " + file + " exists but the game could not read it");
        }
        return data;
    }

    public NpcState state() {
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

    public static NpcSavedData load(CompoundTag tag) {
        NpcSavedData result = new NpcSavedData();
        try {
            result.read(tag);
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected problem while loading the kingdoms of the world", e);
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
        List<String> problems = NpcNbt.read(tag, state);
        for (String problem : state.verify()) {
            LOGGER.warn("Kingdoms of the world data: {}", problem);
        }
        if (!problems.isEmpty()) {
            lock(tag, problems.size() + " unreadable part(s): " + String.join("; ", problems));
            return;
        }
        setDirty(false);
    }

    private void bestEffort(CompoundTag tag) {
        try {
            List<String> problems = NpcNbt.read(tag, state);
            if (!problems.isEmpty()) {
                LOGGER.warn("Kingdoms of the world data parts not understood: {}", String.join("; ", problems));
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Kingdoms of the world data could not be read even partially", e);
        }
    }

    private void lock(CompoundTag original, String reason) {
        readOnlyReason = reason;
        preserved = original == null ? null : original.copy();
        super.setDirty(false);
        LOGGER.error("Kingdoms of the world data is read-only: {}. Changes to the plan and the appearance of kingdoms are refused and the file will not be "
                + "overwritten. Fix or restore the file, or run '/kingdomworld admin npc unlock' to keep only what could be read (a backup of the "
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
        LOGGER.warn("Kingdoms of the world data unlocked by an operator; the original was backed up to {} (reason it was read-only: {})", backup,
                readOnlyReason);
        readOnlyReason = null;
        preserved = null;
        for (String problem : state.verify()) {
            LOGGER.warn("Kingdoms of the world data after unlocking: {}", problem);
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
        NpcNbt.write(state, tag);
        return tag;
    }
}
