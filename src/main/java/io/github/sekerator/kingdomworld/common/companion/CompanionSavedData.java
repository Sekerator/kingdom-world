package io.github.sekerator.kingdomworld.common.companion;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.core.companion.CompanionState;
import io.github.sekerator.kingdomworld.core.companion.Skills;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The companions and parties of a world, stored with the overworld in {@code <world>/data/kingdomworld_companions.dat} apart
 * from the kingdoms: the records of companions with their packs, the markets of the branches, the closed markets, the
 * parties and their invitations.
 *
 * <p>The root tag carries {@value #TAG_FORMAT_VERSION}. Loading never throws: data of a newer format, a damaged version or an
 * entry that cannot be read (an item of a mod that is gone included) keeps the data read-only (what could be read is shown,
 * changes are refused, the file is not overwritten) until an operator fixes the file or accepts the loss with
 * {@code /kingdomworld admin companions unlock}.
 */
public final class CompanionSavedData extends SavedData {
    public static final String DATA_NAME = KingdomWorld.MOD_ID + "_companions";
    /** Current layout of the saved data. Increase it together with a migration step. */
    public static final int FORMAT_VERSION = 1;
    static final String TAG_FORMAT_VERSION = "formatVersion";

    private static final Logger LOGGER = LogUtils.getLogger();

    private final CompanionState state = new CompanionState();
    private final Map<UUID, SimpleContainer> packs = new HashMap<>();
    private CompoundTag preserved;
    private String readOnlyReason;

    public CompanionSavedData() {
        state.setChangeListener(this::setDirty);
    }

    /**
     * The companions of a server. Must be called on the server thread.
     */
    public static CompanionSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(CompanionSavedData::load, () -> createAbsent(server), DATA_NAME);
    }

    private static CompanionSavedData createAbsent(MinecraftServer server) {
        CompanionSavedData data = new CompanionSavedData();
        Path file = dataFile(server);
        if (Files.exists(file)) {
            data.lock(null, "the file " + file + " exists but the game could not read it");
        }
        return data;
    }

    public CompanionState state() {
        return state;
    }

    /**
     * The pack of a companion; an empty one is made when first needed. Changes to it mark the data to be saved.
     */
    public SimpleContainer pack(UUID companionId) {
        return packs.computeIfAbsent(companionId, id -> listen(new SimpleContainer(Skills.MAX_SLOTS)));
    }

    private SimpleContainer listen(SimpleContainer pack) {
        pack.addListener(container -> setDirty());
        return pack;
    }

    /**
     * Takes a pack away with everything in it, for a companion that is gone; the caller drops or hands out its items.
     */
    public Optional<SimpleContainer> takePack(UUID companionId) {
        SimpleContainer pack = packs.remove(companionId);
        if (pack != null) {
            setDirty();
        }
        return Optional.ofNullable(pack);
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

    public static CompanionSavedData load(CompoundTag tag) {
        CompanionSavedData result = new CompanionSavedData();
        try {
            result.read(tag);
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected problem while loading the companions", e);
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
        Map<UUID, SimpleContainer> read = new HashMap<>();
        List<String> problems = CompanionNbt.read(tag, state, read);
        read.forEach((id, pack) -> packs.put(id, listen(pack)));
        if (!problems.isEmpty()) {
            lock(tag, problems.size() + " unreadable part(s): " + String.join("; ", problems));
            return;
        }
        setDirty(false);
    }

    private void bestEffort(CompoundTag tag) {
        try {
            Map<UUID, SimpleContainer> read = new HashMap<>();
            List<String> problems = CompanionNbt.read(tag, state, read);
            read.forEach((id, pack) -> packs.put(id, listen(pack)));
            if (!problems.isEmpty()) {
                LOGGER.warn("Companion data parts not understood: {}", String.join("; ", problems));
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Companion data could not be read even partially", e);
        }
    }

    private void lock(CompoundTag original, String reason) {
        readOnlyReason = reason;
        preserved = original == null ? null : original.copy();
        super.setDirty(false);
        LOGGER.error("Companion data is read-only: {}. Changes to companions and parties are refused and the file will not be "
                + "overwritten. Fix or restore the file, or run '/kingdomworld admin companions unlock' to keep only what could be read "
                + "(a backup of the original is made first).", reason);
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
        LOGGER.warn("Companion data unlocked by an operator; the original was backed up to {} (reason it was read-only: {})", backup,
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
        CompanionNbt.write(state, packs, tag);
        return tag;
    }
}
