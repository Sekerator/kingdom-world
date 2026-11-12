package io.github.sekerator.kingdomworld.common.land;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.core.land.BorderKind;
import io.github.sekerator.kingdomworld.core.land.ProvinceKind;
import io.github.sekerator.kingdomworld.core.territory.Province;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
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
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The provinces of a world, stored with the overworld in {@code <world>/data/kingdomworld_provinces.dat} apart from the
 * kingdoms: the tiles of provinces as runs of numbers ({@code (id << 16) | length}), the table of provinces with their holders,
 * the days they were acquired and the holds for buyers, the stage of the plan of the world and the log of the adoption of an
 * old world. Without the file there are no provinces and the land is as it always was.
 *
 * <p>The root tag carries {@value #TAG_FORMAT_VERSION}. Loading never throws: data of a newer format, a damaged version or a part
 * that cannot be read keep the provinces read-only (what could be read still guards the land, changes are not written, the file
 * is not overwritten) until an operator fixes the file or accepts the loss with {@code /kingdomworld admin land unlock}. The file
 * is written only when something changed.
 */
public final class ProvinceSavedData extends SavedData {
    public static final String DATA_NAME = KingdomWorld.MOD_ID + "_provinces";
    /** Current layout of the saved data. */
    public static final int FORMAT_VERSION = 1;
    /** The dimension of provinces of a new file. */
    public static final String DEFAULT_DIMENSION = "minecraft:overworld";

    static final String TAG_FORMAT_VERSION = "formatVersion";
    static final String TAG_DIMENSION = "dimension";
    static final String TAG_PROVINCES = "provinces";
    static final String TAG_TILES = "tiles";
    static final String TAG_PLAN = "plan";
    static final String TAG_ADOPTION = "adoption";

    private static final Logger LOGGER = LogUtils.getLogger();

    private ProvinceMap map = new ProvinceMap(DEFAULT_DIMENSION);
    private CompoundTag plan = new CompoundTag();
    private ListTag adoption = new ListTag();
    private CompoundTag preserved;
    private String readOnlyReason;

    public ProvinceSavedData() {
        map.setChangeListener(this::setDirty);
    }

    /**
     * The provinces of a server. Must be called on the server thread.
     */
    public static ProvinceSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ProvinceSavedData::load, () -> createAbsent(server), DATA_NAME);
    }

    private static ProvinceSavedData createAbsent(MinecraftServer server) {
        ProvinceSavedData data = new ProvinceSavedData();
        Path file = dataFile(server);
        if (Files.exists(file)) {
            data.lock(null, "the file " + file + " exists but the game could not read it");
        }
        return data;
    }

    public ProvinceMap map() {
        return map;
    }

    /**
     * Where the plan of the world has got to; empty until the plan of states begins.
     */
    public CompoundTag plan() {
        return plan.copy();
    }

    public void setPlan(CompoundTag stage) {
        plan = stage.copy();
        setDirty();
    }

    /**
     * The log of the adoption of an old world.
     */
    public ListTag adoption() {
        return adoption.copy();
    }

    public void addAdoptionEntry(Tag entry) {
        adoption.add(entry.copy());
        setDirty();
    }

    public boolean isReadOnly() {
        return readOnlyReason != null;
    }

    public Optional<String> readOnlyReason() {
        return Optional.ofNullable(readOnlyReason);
    }

    /**
     * Makes the provinces read-only or writable again without touching the file, for game tests.
     */
    public void setReadOnlyForCheck(boolean readOnly) {
        readOnlyReason = readOnly ? "read-only for a game test" : null;
        preserved = null;
        super.setDirty(false);
    }

    @Override
    public void setDirty(boolean dirty) {
        // Read-only data is never written back.
        super.setDirty(dirty && readOnlyReason == null);
    }

    public static ProvinceSavedData load(CompoundTag tag) {
        ProvinceSavedData result = new ProvinceSavedData();
        try {
            result.read(tag);
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected problem while loading the provinces", e);
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
                LOGGER.warn("Province data parts not understood: {}", String.join("; ", problems));
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Province data could not be read even partially", e);
        }
    }

    /**
     * Reads what can be read into a new map and describes the rest.
     */
    private List<String> readAll(CompoundTag tag) {
        List<String> problems = new ArrayList<>();
        String dimension = tag.contains(TAG_DIMENSION, Tag.TAG_STRING) ? tag.getString(TAG_DIMENSION) : DEFAULT_DIMENSION;
        ProvinceMap read = new ProvinceMap(dimension.isEmpty() ? DEFAULT_DIMENSION : dimension);
        if (tag.contains(TAG_PROVINCES)) {
            if (!tag.contains(TAG_PROVINCES, Tag.TAG_LIST)) {
                problems.add("'" + TAG_PROVINCES + "' is not a list");
            } else {
                ListTag list = tag.getList(TAG_PROVINCES, Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    try {
                        readProvince(read, list.getCompound(i));
                    } catch (RuntimeException e) {
                        problems.add("province #" + i + ": " + e.getMessage());
                    }
                }
            }
        }
        if (tag.contains(TAG_TILES)) {
            if (!tag.contains(TAG_TILES, Tag.TAG_LIST)) {
                problems.add("'" + TAG_TILES + "' is not a list");
            } else {
                ListTag list = tag.getList(TAG_TILES, Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag tile = list.getCompound(i);
                    try {
                        if (!tile.contains("runs", Tag.TAG_INT_ARRAY)) {
                            throw new IllegalArgumentException("no runs");
                        }
                        read.putTile(tile.getInt("x"), tile.getInt("z"), ProvinceMap.decode(tile.getIntArray("runs")));
                    } catch (RuntimeException e) {
                        problems.add("tile #" + i + ": " + e.getMessage());
                    }
                }
            }
        }
        if (tag.contains(TAG_PLAN, Tag.TAG_COMPOUND)) {
            plan = tag.getCompound(TAG_PLAN).copy();
        }
        if (tag.contains(TAG_ADOPTION, Tag.TAG_LIST)) {
            adoption = tag.getList(TAG_ADOPTION, Tag.TAG_COMPOUND).copy();
        }
        read.setChangeListener(this::setDirty);
        map = read;
        return problems;
    }

    private static void readProvince(ProvinceMap read, CompoundTag tag) {
        int id = tag.getInt("id");
        ProvinceKind kind = ProvinceKind.valueOf(tag.getString("kind").toUpperCase(Locale.ROOT));
        int[] neighbours = tag.getIntArray("near");
        ListTag borders = tag.getList("borders", Tag.TAG_STRING);
        if (borders.size() != neighbours.length) {
            throw new IllegalArgumentException("a kind of border for every neighbour");
        }
        List<Integer> near = new ArrayList<>();
        List<BorderKind> kinds = new ArrayList<>();
        for (int i = 0; i < neighbours.length; i++) {
            near.add(neighbours[i]);
            kinds.add(BorderKind.valueOf(borders.getString(i).toUpperCase(Locale.ROOT)));
        }
        if (read.exists(id)) {
            throw new IllegalArgumentException("province " + id + " twice");
        }
        read.define(new Province(id, tag.getInt("x"), tag.getInt("z"), near, kinds, kind, tag.getLong("seed"), tag.getLong("seat")));
        UUID owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ProvinceMap.SaleHold hold = null;
        if (tag.contains("hold", Tag.TAG_COMPOUND)) {
            CompoundTag held = tag.getCompound("hold");
            hold = new ProvinceMap.SaleHold(held.getUUID("buyer"), held.getLong("until"));
        }
        read.restore(id, owner, tag.getLong("day"), hold);
    }

    private void lock(CompoundTag original, String reason) {
        readOnlyReason = reason;
        preserved = original == null ? null : original.copy();
        super.setDirty(false);
        LOGGER.error("Province data is read-only: {}. The provinces read still guard the land, changes are not written and the file will "
                + "not be overwritten. Fix or restore the file, or run '/kingdomworld admin land unlock' to keep only what could be read "
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
        LOGGER.warn("Province data unlocked by an operator; the original was backed up to {} (reason it was read-only: {})", backup,
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
        return write(tag);
    }

    /**
     * The provinces written into a tag as the file holds them.
     */
    public CompoundTag write(CompoundTag tag) {
        tag.putInt(TAG_FORMAT_VERSION, FORMAT_VERSION);
        tag.putString(TAG_DIMENSION, map.dimension());
        ListTag provinces = new ListTag();
        for (Province province : map.provinces()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("id", province.id());
            entry.putInt("x", province.centerX());
            entry.putInt("z", province.centerZ());
            entry.putString("kind", province.kind().id());
            entry.putLong("seed", province.nameSeed());
            entry.putLong("seat", province.seat());
            int[] near = new int[province.neighbours().size()];
            ListTag borders = new ListTag();
            for (int i = 0; i < near.length; i++) {
                near[i] = province.neighbours().get(i);
                borders.add(StringTag.valueOf(province.borderKinds().get(i).id()));
            }
            entry.putIntArray("near", near);
            entry.put("borders", borders);
            UUID owner = map.owner(province.id());
            if (owner != null) {
                entry.putUUID("owner", owner);
            }
            entry.putLong("day", map.acquiredDay(province.id()));
            map.hold(province.id()).ifPresent(hold -> {
                CompoundTag held = new CompoundTag();
                held.putUUID("buyer", hold.buyer());
                held.putLong("until", hold.untilDay());
                entry.put("hold", held);
            });
            provinces.add(entry);
        }
        tag.put(TAG_PROVINCES, provinces);
        ListTag tiles = new ListTag();
        for (long key : map.tileKeys()) {
            CompoundTag tile = new CompoundTag();
            tile.putInt("x", ProvinceMap.tileX(key));
            tile.putInt("z", ProvinceMap.tileZ(key));
            tile.putIntArray("runs", ProvinceMap.encode(map.tile(key)));
            tiles.add(tile);
        }
        tag.put(TAG_TILES, tiles);
        tag.put(TAG_PLAN, plan.copy());
        tag.put(TAG_ADOPTION, adoption.copy());
        return tag;
    }
}
