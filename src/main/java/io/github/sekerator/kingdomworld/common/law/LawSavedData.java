package io.github.sekerator.kingdomworld.common.law;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.core.law.LawState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The law of a world, stored with the overworld in {@code <world>/data/kingdomworld_law.dat} apart from the kingdoms: who is
 * wanted where, confiscated goods with the goods themselves, reputations, passes into cities, the decisions of each court and
 * who pays for entry by themselves. Debts live in the treasuries.
 *
 * <p>The root tag carries {@value #TAG_FORMAT_VERSION}. Loading never throws: data of a newer format, a damaged version, an
 * entry that cannot be read or goods of a mod that is gone keep the law read-only (what could be read is shown, changes are
 * refused, the file is not overwritten) until an operator fixes the file or accepts the loss with
 * {@code /kingdomworld admin law unlock}.
 */
public final class LawSavedData extends SavedData {
    public static final String DATA_NAME = KingdomWorld.MOD_ID + "_law";
    /** Current layout of the saved data. Increase it together with a migration step. */
    public static final int FORMAT_VERSION = 1;
    static final String TAG_FORMAT_VERSION = "formatVersion";

    private static final Logger LOGGER = LogUtils.getLogger();

    private final LawState state = new LawState();
    /** The confiscated goods, by the id of their record. */
    private final Map<Long, List<ItemStack>> goods = new LinkedHashMap<>();
    private CompoundTag preserved;
    private String readOnlyReason;

    public LawSavedData() {
        state.setChangeListener(this::setDirty);
    }

    /**
     * The law of a server. Must be called on the server thread.
     */
    public static LawSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(LawSavedData::load, () -> createAbsent(server), DATA_NAME);
    }

    private static LawSavedData createAbsent(MinecraftServer server) {
        LawSavedData data = new LawSavedData();
        Path file = dataFile(server);
        if (Files.exists(file)) {
            data.lock(null, "the file " + file + " exists but the game could not read it");
        }
        return data;
    }

    public LawState state() {
        return state;
    }

    /**
     * The goods of a confiscation, as they lie in the record; empty for an unknown one.
     */
    public List<ItemStack> goods(long id) {
        return goods.getOrDefault(id, List.of());
    }

    /**
     * Keeps goods under the id of their record.
     */
    void putGoods(long id, List<ItemStack> stacks) {
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                copy.add(stack.copy());
            }
        }
        goods.put(id, copy);
        setDirty();
    }

    /**
     * Takes the goods of a confiscation out of the record; the caller hands them on in the same moment.
     */
    List<ItemStack> takeGoods(long id) {
        List<ItemStack> taken = goods.remove(id);
        setDirty();
        return taken == null ? List.of() : taken;
    }

    /**
     * Makes the law read-only or writable again without touching the file, for game tests.
     */
    public void setReadOnlyForCheck(boolean readOnly) {
        readOnlyReason = readOnly ? "read-only for a game test" : null;
        preserved = null;
        super.setDirty(false);
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

    public static LawSavedData load(CompoundTag tag) {
        LawSavedData result = new LawSavedData();
        try {
            result.read(tag);
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected problem while loading the law of kingdoms", e);
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
        List<String> problems = LawNbt.read(tag, state, goods);
        if (!problems.isEmpty()) {
            lock(tag, problems.size() + " unreadable part(s): " + String.join("; ", problems));
            return;
        }
        setDirty(false);
    }

    private void bestEffort(CompoundTag tag) {
        try {
            List<String> problems = LawNbt.read(tag, state, goods);
            if (!problems.isEmpty()) {
                LOGGER.warn("Law data parts not understood: {}", String.join("; ", problems));
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Law data could not be read even partially", e);
        }
    }

    private void lock(CompoundTag original, String reason) {
        readOnlyReason = reason;
        preserved = original == null ? null : original.copy();
        super.setDirty(false);
        LOGGER.error("Law data is read-only: {}. Changes to the law are refused and the file will not be overwritten. Fix or restore the "
                + "file, or run '/kingdomworld admin law unlock' to keep only what could be read (a backup of the original is made first).",
                reason);
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
        LOGGER.warn("Law data unlocked by an operator; the original was backed up to {} (reason it was read-only: {})", backup, readOnlyReason);
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
        LawNbt.write(state, goods, tag);
        return tag;
    }
}
